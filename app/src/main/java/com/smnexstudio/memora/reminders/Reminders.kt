package com.smnexstudio.memora.reminders

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

/**
 * Schedules every reminder as a one-off alarm for its next date. When an alarm
 * fires, [AlarmReceiver] shows the notification and schedules the next one, so
 * reminders repeat every year. Everything is rebuilt by [syncAll] after any
 * change, at app start, after a reboot and when the clock or time zone changes.
 */
object Reminders {
    const val CHANNEL_ID = "reminders"
    const val SNOOZE_HOURS = 2

    const val EXTRA_KIND = "kind"
    const val EXTRA_EVENT_ID = "eventId"
    const val EXTRA_OFFSET = "offset"
    const val EXTRA_FESTIVAL = "festival"
    const val EXTRA_EARLY = "early"

    const val KIND_EVENT = "event"
    const val KIND_SNOOZE = "snooze"
    const val KIND_DIGEST = "digest"
    const val KIND_FESTIVAL = "festival"

    private val ALL_OFFSETS = listOf(7, 3, 2, 1, 0, MILESTONE_OFFSET)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

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
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun cancel(context: Context, key: String) = alarms(context).cancel(pending(context, key))

    private fun atReminderTime(date: LocalDate): LocalDateTime = date.atTime(Repo.reminderHour(), Repo.reminderMinute())

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

    /** The chosen early reminders, the on-the-day one, and a milestone reminder 2 weeks before a big one. */
    fun scheduleEvent(context: Context, e: MemoraEvent) {
        val wanted = (e.offsets + 0).toMutableSet()
        val next = nextDate(e)
        val milestoneFire = atReminderTime(next.minusDays(MILESTONE_OFFSET.toLong()))
        val milestone = isMilestone(e, next) && MILESTONE_OFFSET !in e.offsets && milestoneFire.isAfter(LocalDateTime.now())
        if (milestone) wanted += MILESTONE_OFFSET

        for (offset in ALL_OFFSETS) {
            val key = eventKey(e.id, offset)
            if (offset !in wanted) {
                cancel(context, key)
                continue
            }
            val at = if (offset == MILESTONE_OFFSET && milestone) milestoneFire else nextFire(e, offset)
            setAlarm(context, at, pending(context, key) {
                putExtra(EXTRA_KIND, KIND_EVENT); putExtra(EXTRA_EVENT_ID, e.id); putExtra(EXTRA_OFFSET, offset)
            })
        }
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
        if (!Repo.getBool("weeklyDigest", true)) return cancel(context, "digest")
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
        val ids = Repo.festivals()
        val early = Repo.getBool("festivalEarly", false)
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

    /** Rebuilds every reminder, digest and festival alarm and refreshes the widget. Pending snoozes are kept. */
    fun syncAll(context: Context) {
        Repo.init(context)
        Repo.listEvents().forEach { scheduleEvent(context, it) }
        scheduleDigest(context)
        scheduleFestivals(context)
        NextEventsWidget.updateAll(context)
    }
}
