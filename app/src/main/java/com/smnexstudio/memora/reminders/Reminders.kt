package com.smnexstudio.memora.reminders

import com.smnexstudio.memora.data.Diagnostics
import com.smnexstudio.memora.data.Prefs
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.MILESTONE_OFFSET
import com.smnexstudio.memora.logic.isMilestone
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.widget.NextEventsWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class AlertStyle(val id: String, val label: String, val hint: String) {
    NOTIFICATION("notification", "Notification", "A notification at the reminder time, with buttons to send the card."),
    ALARM("alarm", "Alarm", "Rings like an alarm and fills the screen until you stop it."),
    BOTH("both", "Both", "The notification, and an alarm that rings until you stop it.");

    val notifies get() = this != ALARM
    val rings get() = this != NOTIFICATION

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: NOTIFICATION
    }
}

/**
 * People reminders share one alarm, set for the earliest one due ([scheduleNext]). When it
 * fires, [AlarmReceiver] sends everything due by then and sets it again for the next, so
 * reminders repeat every year. One alarm per reminder would hit Android's limit of 500
 * alarms per app at a few hundred people, and the rest would silently never ring.
 *
 * Festivals, the weekly digest and snoozes keep their own alarms; there are few of them.
 * Everything is rebuilt by [syncAll] after any change, at app start, after a reboot and
 * when the clock or time zone changes.
 */
object Reminders {
    const val CHANNEL_ID = "reminders"

    /** The alarm's notification. Silent: [AlarmSound] plays the tone. A channel's sound can't change once made, hence the version. */
    const val ALARM_CHANNEL_ID = "alarms_v2"
    const val SNOOZE_HOURS = 2

    const val EXTRA_KIND = "kind"
    const val EXTRA_EVENT_ID = "eventId"
    const val EXTRA_EVENT_IDS = "memora.eventIds"
    const val EXTRA_OFFSETS = "memora.offsets"
    const val EXTRA_OFFSET = "offset"
    const val EXTRA_FESTIVAL = "festival"
    const val EXTRA_EARLY = "early"

    const val KIND_EVENT = "event"
    const val KIND_NEXT = "next"
    const val KIND_SNOOZE = "snooze"
    const val KIND_DIGEST = "digest"
    const val KIND_FESTIVAL = "festival"

    private val ALL_OFFSETS = listOf(7, 3, 2, 1, 0, MILESTONE_OFFSET)

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        manager.deleteNotificationChannel("alarms_v1")
        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders that ring like an alarm, if you chose Alarm in Settings"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** Whether reminders must keep names off the lock screen: the user asked to, or app lock is on. */
    fun privateOnLockScreen(): Boolean = Prefs.appLock.get() || Prefs.hideOnLockScreen.get()

    /** How people reminders reach you: Settings → Reminders → How to remind. Festivals and the digest are always notifications. */
    fun alertStyle(): AlertStyle = Prefs.alertStyle.get()

    /** Android 14+ lets the user stop apps from opening a full-screen alarm over the lock screen. */
    fun canShowFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    /** One PendingIntent per reminder, told apart by its data URI. */
    private fun pending(context: Context, key: String, fill: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = Uri.parse("memora://alarm/$key")
            fill()
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun canScheduleExact(context: Context): Boolean =
        alarms(context).canScheduleExactAlarms()

    private fun setAlarm(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = alarms(context)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: RuntimeException) {
            // Android refuses past 500 alarms per app, or if exact alarms were revoked mid-call.
            // Record it, so Reminder checks can show it, rather than stop scheduling the rest.
            Diagnostics.note(context, "alarm refused", e.javaClass.simpleName)
        }
    }

    private fun cancel(context: Context, key: String) = alarms(context).cancel(pending(context, key))

    private fun atReminderTime(date: LocalDate): LocalDateTime = date.atTime(Prefs.reminderHour.get(), Prefs.reminderMinute.get())

    private fun eventKey(id: Long, offset: Int) = "event/$id/$offset"
    private fun snoozeKey(id: Long) = "snooze/$id"

    /** When the reminder `offset` days before the event should next fire. */
    fun nextFire(e: MemoraEvent, offset: Int, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var occurrence = nextDate(e, now.toLocalDate())
        repeat(3) {
            val fire = atReminderTime(occurrence.minusDays(offset.toLong()))
            if (fire.isAfter(now)) return fire
            occurrence = nextDate(e, occurrence.plusDays(1))
        }
        return atReminderTime(occurrence.minusDays(offset.toLong()))
    }

    /** One people reminder: [offset] days before event [eventId] (0 on the day), at [at]. */
    data class Due(val at: LocalDateTime, val eventId: Long, val offset: Int)

    /**
     * When each of [e]'s reminders next fires after [after]: the chosen early ones, the
     * on-the-day one, and one 2 weeks before a milestone birthday or anniversary.
     */
    fun reminders(e: MemoraEvent, after: LocalDateTime): List<Due> {
        val next = nextDate(e, after.toLocalDate())
        val milestoneFire = atReminderTime(next.minusDays(MILESTONE_OFFSET.toLong()))
        val milestone = isMilestone(e, next) && MILESTONE_OFFSET !in e.offsets && milestoneFire.isAfter(after)
        return (e.offsets + 0).distinct().map { Due(nextFire(e, it, after), e.id, it) } +
            listOfNotNull(Due(milestoneFire, e.id, MILESTONE_OFFSET).takeIf { milestone })
    }

    /** Every people reminder firing after [after], earliest first. */
    fun plan(events: List<MemoraEvent>, after: LocalDateTime): List<Due> =
        events.flatMap { reminders(it, after) }.sortedBy { it.at }

    /** Reminders from [events] due in ([after], [now]]: what the shared alarm sends when it fires. */
    fun due(events: List<MemoraEvent>, after: LocalDateTime, now: LocalDateTime): List<Due> =
        events.flatMap { reminders(it, after) }.filter { !it.at.isAfter(now) }.sortedBy { it.at }

    /** Up to when people reminders have been sent; the next ones fire after this. */
    fun handledUntil(): LocalDateTime =
        Prefs.remindersHandledAt.get()?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
            ?: LocalDateTime.now()

    fun markHandled(at: LocalDateTime) =
        Prefs.remindersHandledAt.set(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    /** Sets the shared alarm for the earliest people reminder, or clears it when there are none. */
    fun scheduleNext(context: Context) {
        val next = plan(Repo.listEvents(), handledUntil()).firstOrNull()
        if (next == null) return cancel(context, "next")
        setAlarm(context, next.at, pending(context, "next") { putExtra(EXTRA_KIND, KIND_NEXT) })
    }

    /** Before 1.1 each reminder had its own alarm; those are cleared once, so nothing rings twice. */
    private fun clearOldAlarms(context: Context) {
        if (Prefs.rollingAlarms.get()) return
        Repo.listEvents().forEach { e -> ALL_OFFSETS.forEach { cancel(context, eventKey(e.id, it)) } }
        Prefs.rollingAlarms.set(true)
    }

    fun cancelEvent(context: Context, id: Long) {
        ALL_OFFSETS.forEach { cancel(context, eventKey(id, it)) }
        cancel(context, snoozeKey(id))
    }

    /** Shows the on-the-day reminder again in a couple of hours. */
    fun snooze(context: Context, id: Long, hours: Int = SNOOZE_HOURS) {
        setAlarm(context, LocalDateTime.now().plusHours(hours.toLong()), pending(context, snoozeKey(id)) {
            putExtra(EXTRA_KIND, KIND_SNOOZE); putExtra(EXTRA_EVENT_ID, id)
        })
    }

    /** Next Sunday's digest; its content is worked out when it fires. */
    fun scheduleDigest(context: Context) {
        if (!Prefs.weeklyDigest.get()) return cancel(context, "digest")
        var day = today()
        while (true) {
            if (day.dayOfWeek == DayOfWeek.SUNDAY && atReminderTime(day).isAfter(LocalDateTime.now())) break
            day = day.plusDays(1)
        }
        setAlarm(context, atReminderTime(day), pending(context, "digest") { putExtra(EXTRA_KIND, KIND_DIGEST) })
    }

    /** Days before a festival that the optional early reminder fires. */
    const val FESTIVAL_EARLY_DAYS = 7L

    /** On-the-day reminders for the festivals the user picked, and optionally one a week before. */
    fun scheduleFestivals(context: Context) {
        val ids = Prefs.festivals.get()
        val early = Prefs.festivalEarly.get()
        val festivals = availableFestivals().associateBy { it.id }
        // Enabled ids may belong to another country; their alarms are cancelled too.
        for (id in festivals.keys + ids) {
            val key = "festival/$id"
            val f = festivals[id]
            val date = f?.let { nextFestivalDate(it) }?.let { d ->
                if (atReminderTime(d).isAfter(LocalDateTime.now())) d else nextFestivalDate(f, d.plusDays(1))
            }
            val earlyKey = "$key/early"
            val earlyDate = f?.takeIf { early }?.let { nextFestivalDate(it) }?.let { d ->
                if (atReminderTime(d.minusDays(FESTIVAL_EARLY_DAYS)).isAfter(LocalDateTime.now())) d else nextFestivalDate(f, d.plusDays(1))
            }
            if (f == null || id !in ids || earlyDate == null) cancel(context, earlyKey)
            else setAlarm(context, atReminderTime(earlyDate.minusDays(FESTIVAL_EARLY_DAYS)), pending(context, earlyKey) {
                putExtra(EXTRA_KIND, KIND_FESTIVAL); putExtra(EXTRA_FESTIVAL, f.id); putExtra(EXTRA_EARLY, true)
            })

            if (f == null || id !in ids || date == null) {
                cancel(context, key)
                continue
            }
            setAlarm(context, atReminderTime(date), pending(context, key) {
                putExtra(EXTRA_KIND, KIND_FESTIVAL); putExtra(EXTRA_FESTIVAL, f.id)
            })
        }
    }

    /**
     * Rebuilds every reminder, digest and festival alarm and refreshes the widget. Pending snoozes are kept.
     * Reminders whose time passed while this runs (or while the phone was off) are skipped, not sent late.
     */
    fun syncAll(context: Context) {
        Repo.init(context)
        clearOldAlarms(context)
        markHandled(LocalDateTime.now())
        scheduleNext(context)
        scheduleDigest(context)
        scheduleFestivals(context)
        NextEventsWidget.updateAll(context)
    }
}
