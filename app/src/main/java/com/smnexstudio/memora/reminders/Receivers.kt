package com.smnexstudio.memora.reminders

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smnexstudio.memora.MainActivity
import com.smnexstudio.memora.R
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.NoteKind
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.platform.LinkGuard
import com.smnexstudio.memora.logic.MILESTONE_OFFSET
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.festivalById
import com.smnexstudio.memora.logic.milestone
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.offsetLabel
import com.smnexstudio.memora.logic.possessiveTitle
import com.smnexstudio.memora.logic.shortDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.widget.NextEventsWidget
import kotlin.concurrent.thread

/** Opens the app at a route, e.g. "approve/5?channel=whatsapp". */
fun openAppIntent(context: Context, route: String, requestCode: Int): PendingIntent {
    val intent = LinkGuard.stamp(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("memora://$route")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

private fun post(context: Context, id: Int, n: Notification) {
    val manager = NotificationManagerCompat.from(context)
    // Android 13+ asks for the permission; the user can also turn Memora's notifications off.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    if (!manager.areNotificationsEnabled()) return
    try {
        manager.notify(id, n)
    } catch (_: SecurityException) {
        // Permission revoked between the check and the call.
    }
}

private fun base(context: Context): NotificationCompat.Builder {
    val b = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFFB23A2E.toInt())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    // With app lock on, names and wishes stay off the lock screen.
    if (Repo.getBool("appLock", false)) {
        b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
            NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFB23A2E.toInt())
                .setContentTitle("Memora")
                .setContentText("You have a reminder")
                .build(),
        )
    }
    return b
}

fun dayNotificationId(eventId: Long) = (eventId * 10).toInt()

/** "Today is Ravi's birthday" with WhatsApp / SMS / Remind in 2h buttons. */
fun showDayNotification(context: Context, e: MemoraEvent, snoozed: Boolean) {
    val id = dayNotificationId(e.id)
    val snooze = PendingIntent.getBroadcast(
        context, id,
        Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_SNOOZE
            putExtra(Reminders.EXTRA_EVENT_ID, e.id)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val n = base(context)
        .setContentTitle(if (snoozed) "Reminder: ${possessiveTitle(e)} is today" else "${possessiveTitle(e)} is today")
        .setContentText(
            (milestone(e, today())?.let { "${it.replaceFirstChar { c -> c.uppercase() }}. " } ?: "") + "Your card is ready to review.",
        )
        .setContentIntent(openAppIntent(context, "approve/${e.id}", id))
        .addAction(0, "WhatsApp", openAppIntent(context, "approve/${e.id}?channel=whatsapp", id + 1))
        .addAction(0, "SMS", openAppIntent(context, "approve/${e.id}?channel=sms", id + 2))
        .addAction(0, "Remind in ${Reminders.SNOOZE_HOURS}h", snooze)
        .build()
    post(context, id, n)
}

/** Shows the notification an alarm stands for, then schedules the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        when (intent.getStringExtra(Reminders.EXTRA_KIND)) {
            Reminders.KIND_EVENT -> {
                val e = Repo.getEvent(intent.getLongExtra(Reminders.EXTRA_EVENT_ID, -1)) ?: return
                val offset = intent.getIntExtra(Reminders.EXTRA_OFFSET, 0)
                if (offset == 0) showDayNotification(context, e, snoozed = false) else showEarly(context, e, offset)
                Reminders.scheduleEvent(context, e)
            }
            Reminders.KIND_SNOOZE -> {
                val e = Repo.getEvent(intent.getLongExtra(Reminders.EXTRA_EVENT_ID, -1)) ?: return
                showDayNotification(context, e, snoozed = true)
            }
            Reminders.KIND_DIGEST -> {
                showDigest(context)
                Reminders.scheduleDigest(context)
            }
            Reminders.KIND_FESTIVAL -> {
                val f = festivalById(intent.getStringExtra(Reminders.EXTRA_FESTIVAL) ?: "") ?: return
                val early = intent.getBooleanExtra(Reminders.EXTRA_EARLY, false)
                val b = base(context).setContentIntent(openAppIntent(context, "home", 3))
                if (early) {
                    b.setContentTitle("${f.name} in 1 week")
                    nextFestivalDate(f)?.let { b.setContentText(shortDate(it)) }
                } else {
                    b.setContentTitle("Today is ${f.name}")
                }
                post(context, 900_000 + Math.floorMod(f.id.hashCode(), 1000) + if (early) 1000 else 0, b.build())
                Reminders.scheduleFestivals(context)
            }
        }
        NextEventsWidget.updateAll(context)
    }

    private fun showEarly(context: Context, e: MemoraEvent, offset: Int) {
        val next = nextDate(e)
        val whenText = "${next.dayOfMonth} ${MONTHS[next.monthValue - 1]}"
        val b = base(context).setContentIntent(openAppIntent(context, "event/${e.id}", dayNotificationId(e.id) + offset))
        if (offset == MILESTONE_OFFSET) {
            b.setContentTitle("★ ${e.name} – ${milestone(e, next)} in 2 weeks")
                .setContentText("A big one on $whenText. Time to plan something special?")
        } else {
            val idea = Repo.listNotes(e.id).firstOrNull { it.kind == NoteKind.IDEA }?.text
            val text = "${whenText}${milestone(e, next)?.let { ", $it" } ?: ""}." + (idea?.let { " Gift idea saved: $it." } ?: " Your card will be ready on the day.")
            b.setContentTitle("${possessiveTitle(e)} in ${offsetLabel(offset)}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        post(context, dayNotificationId(e.id) + offset, b.build())
    }

    private fun showDigest(context: Context) {
        val start = today()
        val week = Repo.listEvents()
            .map { it to nextDate(it, start) }
            .filter { (_, d) -> daysUntil(d, start) < 7 }
            .sortedBy { it.second }
        if (week.isEmpty()) return
        val lines = week.take(5).map { (e, d) -> "${shortDate(d)} · ${possessiveTitle(e)}" }.toMutableList()
        if (week.size > 5) lines += "and ${week.size - 5} more"
        post(
            context, 800_000,
            base(context)
                .setContentTitle(if (week.size == 1) "This week: 1 event" else "This week: ${week.size} events")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(openAppIntent(context, "home", 2))
                .build(),
        )
    }
}

/** Notification buttons that work without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.smnexstudio.memora.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        val id = intent.getLongExtra(Reminders.EXTRA_EVENT_ID, -1)
        if (id <= 0) return
        NotificationManagerCompat.from(context).cancel(dayNotificationId(id))
        Reminders.snooze(context, id)
        Toast.makeText(context, "We’ll remind you again in ${Reminders.SNOOZE_HOURS} hours", Toast.LENGTH_SHORT).show()
    }
}

/** Alarms are cleared on reboot and drift with clock changes; rebuild them. */
class BootReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        // Rebuilding every alarm reads the whole database; keep it off the main thread.
        val pending = goAsync()
        thread {
            try {
                Reminders.syncAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
