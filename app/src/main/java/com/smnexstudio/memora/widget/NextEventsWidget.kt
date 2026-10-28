package com.smnexstudio.memora.widget

import com.smnexstudio.memora.logic.Agenda
import com.smnexstudio.memora.data.Prefs
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.smnexstudio.memora.MainActivity
import com.smnexstudio.memora.R
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.Festival
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.ordinal
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.logic.yearsOn
import com.smnexstudio.memora.platform.LinkGuard
import java.time.LocalDate

/** What the widget shows while Memora has app lock on. */
enum class LockedWidget(val id: String) {
    COUNT("count"),     // how many events today, and the next date
    NOTHING("nothing"), // just the Memora name
    FULL("full");       // names and events, as with the lock off

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: COUNT
    }
}

/** Widget preferences, from Settings → Widget. */
object WidgetPrefs {
    fun locked() = Prefs.widgetLocked.get()
    fun ages() = Prefs.widgetAges.get()
    fun festivals() = Prefs.widgetFestivals.get()
    fun rows() = Prefs.widgetRows.get()
}

/**
 * "Coming up" home screen widget. Large: today's events with a Wish button, then what's next.
 * Small (2 × 2): the first of those. With app lock on it shows only a count and the next date,
 * or nothing, as chosen in Settings → Widget.
 */
class NextEventsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, intArrayOf(id))
    }

    /** One line on the widget: a person's event or an enabled festival. */
    private sealed interface Entry {
        val date: LocalDate
        data class Person(val e: MemoraEvent, override val date: LocalDate) : Entry
        data class Fest(val f: Festival, override val date: LocalDate) : Entry
    }

    companion object {
        private val ROWS = listOf(
            intArrayOf(R.id.row0, R.id.div0, R.id.stamp0, R.id.name0, R.id.sub0, R.id.when0, R.id.wish0, R.id.sent0),
            intArrayOf(R.id.row1, R.id.div1, R.id.stamp1, R.id.name1, R.id.sub1, R.id.when1, R.id.wish1, R.id.sent1),
            intArrayOf(R.id.row2, R.id.div2, R.id.stamp2, R.id.name2, R.id.sub2, R.id.when2, R.id.wish2, R.id.sent2),
            intArrayOf(R.id.row3, R.id.div3, R.id.stamp3, R.id.name3, R.id.sub3, R.id.when3, R.id.wish3, R.id.sent3),
            intArrayOf(R.id.row4, R.id.div4, R.id.stamp4, R.id.name4, R.id.sub4, R.id.when4, R.id.wish4, R.id.sent4),
        )
        private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextEventsWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun open(context: Context, route: String, code: Int) = PendingIntent.getActivity(
            context, code,
            LinkGuard.stamp(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("memora://$route")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun dp(manager: AppWidgetManager, id: Int, key: String, fallback: Int) =
            manager.getAppWidgetOptions(id).getInt(key).takeIf { it > 0 } ?: fallback

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Repo.init(context)
            GoogleHolidays.load()
            val lockOn = Prefs.appLock.get()
            val mode = if (lockOn) WidgetPrefs.locked() else LockedWidget.FULL
            val now = today()
            val people = Agenda.current().upcoming.map { (e, d) -> Entry.Person(e, d) }
            val fests = if (WidgetPrefs.festivals()) {
                val on = Prefs.festivals.get()
                availableFestivals().filter { it.id in on }.mapNotNull { f -> nextFestivalDate(f)?.let { Entry.Fest(f, it) } }
            } else emptyList()
            val entries = (people + fests).sortedWith(compareBy({ it.date }, { (it as? Entry.Person)?.e?.name ?: "" }))
            val todayPeople = people.filter { it.date == now }
            val nextPerson = people.filter { it.date > now }.minByOrNull { it.date }

            for (id in ids) {
                // A 2 × 2 widget is about 110–180 dp wide.
                val small = dp(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) < 220
                val views = if (small) small(context, id, mode, lockOn, entries, todayPeople, nextPerson, people.isEmpty())
                else large(context, manager, id, mode, lockOn, entries, todayPeople, nextPerson, people.isEmpty() && fests.isEmpty())
                manager.updateAppWidget(id, views)
            }
        }

        // ---- Large ----

        private fun large(
            context: Context, manager: AppWidgetManager, id: Int, mode: LockedWidget, lockOn: Boolean,
            entries: List<Entry>, todayPeople: List<Entry.Person>, nextPerson: Entry.Person?, nothingSaved: Boolean,
        ): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_next_events)
            val now = today()
            v.setViewVisibility(R.id.header_lock, if (lockOn && mode != LockedWidget.FULL) View.VISIBLE else View.GONE)
            ROWS.forEach { v.setViewVisibility(it[0], View.GONE) }
            v.setViewVisibility(R.id.locked_block, View.GONE)
            v.setViewVisibility(R.id.open_empty, View.GONE)
            v.setViewVisibility(R.id.empty, View.GONE)

            if (mode != LockedWidget.FULL) {
                // Unlocking lands on today in the calendar.
                v.setOnClickPendingIntent(R.id.root, open(context, "calendar", id * 100))
                v.setTextViewText(R.id.header_sub, "Locked")
                if (mode == LockedWidget.NOTHING) return v
                v.setViewVisibility(R.id.locked_block, View.VISIBLE)
                val n = todayPeople.size
                if (n > 0) {
                    v.setTextViewText(R.id.lock_stamp, "$n")
                    v.setInt(R.id.lock_stamp, "setBackgroundResource", R.drawable.widget_stamp_today)
                    v.setTextColor(R.id.lock_stamp, context.getColor(R.color.w_on_stamp))
                    v.setTextViewText(R.id.lock_title, if (n == 1) "1 event today" else "$n events today")
                    v.setTextViewText(R.id.lock_sub, "Someone is waiting for a wish")
                    v.setViewVisibility(R.id.lock_button, View.VISIBLE)
                    v.setOnClickPendingIntent(R.id.lock_button, open(context, "calendar", id * 100 + 1))
                    v.setViewVisibility(R.id.lock_next, View.GONE)
                } else {
                    v.setTextViewText(R.id.lock_stamp, "–")
                    v.setInt(R.id.lock_stamp, "setBackgroundResource", R.drawable.widget_stamp_faint)
                    v.setTextColor(R.id.lock_stamp, context.getColor(R.color.w_faint))
                    v.setTextViewText(R.id.lock_title, "No events today")
                    v.setTextViewText(R.id.lock_sub, "Enjoy a quiet day")
                    v.setViewVisibility(R.id.lock_button, View.GONE)
                    if (nextPerson != null) {
                        val d = nextPerson.date
                        v.setViewVisibility(R.id.lock_next, View.VISIBLE)
                        v.setTextViewText(R.id.next_stamp, "${d.dayOfMonth}")
                        v.setTextViewText(R.id.next_date, "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}")
                        v.setTextViewText(R.id.next_when, inDays(daysUntil(d, now)))
                    } else v.setViewVisibility(R.id.lock_next, View.GONE)
                }
                return v
            }

            v.setOnClickPendingIntent(R.id.root, open(context, "home", id * 100))
            if (nothingSaved) {
                v.setTextViewText(R.id.header_sub, "Coming up")
                v.setViewVisibility(R.id.empty, View.VISIBLE)
                return v
            }
            val todayCount = entries.count { it.date == now }
            v.setTextViewText(R.id.header_sub, if (todayCount > 0) "$todayCount today" else "Today, ${short(now)}")
            if (todayCount == 0) v.setViewVisibility(R.id.open_empty, View.VISIBLE)

            // Rows that fit: header and padding take about 55 dp, the "No events today" line 26 dp.
            val h = dp(manager, id, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dp(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180))
            val fit = ((h - 55 - (if (todayCount == 0) 26 else 0)) / 54).coerceIn(1, ROWS.size)
            val shown = entries.take(minOf(fit, WidgetPrefs.rows()))
            val ages = WidgetPrefs.ages()
            shown.forEachIndexed { i, entry ->
                val (row, div, stamp, name, sub, whenView, wish, sent) = ROWS[i].toList().let { Row8(it) }
                v.setViewVisibility(row, View.VISIBLE)
                // A dashed line where today's events end and the later ones begin.
                v.setViewVisibility(div, if (i > 0 && shown[i - 1].date == now && entry.date != now) View.VISIBLE else View.GONE)
                v.setViewVisibility(whenView, View.GONE); v.setViewVisibility(wish, View.GONE); v.setViewVisibility(sent, View.GONE)
                val isToday = entry.date == now
                val days = daysUntil(entry.date, now)
                when (entry) {
                    is Entry.Person -> {
                        val e = entry.e
                        val years = yearsOn(e, entry.date)?.takeIf { ages }
                        stampFor(context, v, stamp, e.type, filled = isToday)
                        v.setTextViewText(stamp, if (isToday && years != null) "$years" else "${entry.date.dayOfMonth}")
                        v.setTextViewText(name, if (e.type == EventType.OTHER) "${e.name}’s ${e.title ?: "event"}" else e.name)
                        val what = label(e, years)
                        v.setTextViewText(sub, if (isToday) what else "$what, ${if (days <= 6) inDays(days) else short(entry.date)}")
                        v.setOnClickPendingIntent(row, open(context, "event/${e.id}", id * 100 + 10 + i))
                        if (isToday) {
                            if (e.lastSentYear == entry.date.year) v.setViewVisibility(sent, View.VISIBLE)
                            else {
                                v.setViewVisibility(wish, View.VISIBLE)
                                v.setOnClickPendingIntent(wish, open(context, "approve/${e.id}", id * 100 + 20 + i))
                            }
                        } else if (days > 6) {
                            v.setViewVisibility(whenView, View.VISIBLE)
                            v.setTextViewText(whenView, "$days days")
                        }
                    }
                    is Entry.Fest -> {
                        v.setInt(stamp, "setBackgroundResource", R.drawable.widget_stamp_festival)
                        v.setTextColor(stamp, context.getColor(R.color.w_festival))
                        v.setTextViewText(stamp, "${entry.date.dayOfMonth}")
                        v.setTextViewText(name, entry.f.name)
                        v.setTextViewText(sub, if (isToday) "Festival, today" else "Festival, ${short(entry.date)}")
                        if (!isToday) {
                            v.setViewVisibility(whenView, View.VISIBLE)
                            v.setTextViewText(whenView, if (days == 1) "tomorrow" else "$days days")
                        }
                    }
                }
            }
            return v
        }

        private class Row8(l: List<Int>) {
            val a = l
            operator fun component1() = a[0]
            operator fun component2() = a[1]
            operator fun component3() = a[2]
            operator fun component4() = a[3]
            operator fun component5() = a[4]
            operator fun component6() = a[5]
            operator fun component7() = a[6]
            operator fun component8() = a[7]
        }

        // ---- Small (2 × 2) ----

        private fun small(
            context: Context, id: Int, mode: LockedWidget, lockOn: Boolean,
            entries: List<Entry>, todayPeople: List<Entry.Person>, nextPerson: Entry.Person?, nothingSaved: Boolean,
        ): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_small)
            val now = today()
            val hidden = lockOn && mode != LockedWidget.FULL
            v.setViewVisibility(R.id.header_lock, View.GONE)
            v.setTextViewText(R.id.header_sub, "")
            v.setViewVisibility(R.id.small_lock, if (hidden) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.small_stamp, View.VISIBLE)
            v.setOnClickPendingIntent(R.id.root, open(context, if (hidden) "calendar" else "home", id * 100))

            fun stampText(text: String, bg: Int, color: Int) {
                v.setTextViewText(R.id.small_stamp, text)
                v.setInt(R.id.small_stamp, "setBackgroundResource", bg)
                v.setTextColor(R.id.small_stamp, context.getColor(color))
            }

            when {
                hidden && mode == LockedWidget.NOTHING -> {
                    v.setViewVisibility(R.id.small_stamp, View.GONE)
                    v.setTextViewText(R.id.small_title, "")
                    v.setTextViewText(R.id.small_sub, "Locked")
                }
                hidden && todayPeople.isNotEmpty() -> {
                    stampText("${todayPeople.size}", R.drawable.widget_stamp_today, R.color.w_on_stamp)
                    v.setTextViewText(R.id.small_title, if (todayPeople.size == 1) "event\ntoday" else "events\ntoday")
                    v.setTextViewText(R.id.small_sub, "Tap to view")
                }
                hidden -> {
                    stampText("–", R.drawable.widget_stamp_faint, R.color.w_faint)
                    v.setTextViewText(R.id.small_title, "Nothing today")
                    v.setTextViewText(R.id.small_sub, nextPerson?.let { "Next ${shortDay(it.date)}" } ?: "Nothing coming up")
                }
                nothingSaved -> {
                    v.setViewVisibility(R.id.small_stamp, View.GONE)
                    v.setTextViewText(R.id.small_title, "No events yet")
                    v.setTextViewText(R.id.small_sub, "Tap to add one")
                }
                else -> {
                    val first = entries.firstOrNull { it is Entry.Person && it.date == now } ?: entries.first()
                    val isToday = first.date == now
                    val todayCount = entries.count { it.date == now }
                    when (first) {
                        is Entry.Person -> {
                            val years = yearsOn(first.e, first.date)?.takeIf { WidgetPrefs.ages() }
                            stampText(
                                if (isToday && years != null) "$years" else "${first.date.dayOfMonth}",
                                if (isToday) R.drawable.widget_stamp_today else stampBg(first.e.type),
                                if (isToday) R.color.w_on_stamp else stampColor(first.e.type),
                            )
                            v.setTextViewText(R.id.small_title, first.e.name)
                            v.setOnClickPendingIntent(R.id.root, open(context, "event/${first.e.id}", id * 100 + 1))
                        }
                        is Entry.Fest -> {
                            stampText("${first.date.dayOfMonth}", R.drawable.widget_stamp_festival, R.color.w_festival)
                            v.setTextViewText(R.id.small_title, first.f.name)
                        }
                    }
                    v.setTextViewText(
                        R.id.small_sub,
                        if (isToday) (if (todayCount > 1) "Today, +${todayCount - 1} more" else "Today")
                        else inDays(daysUntil(first.date, now)).replaceFirstChar { it.uppercase() },
                    )
                }
            }
            return v
        }

        // ---- Words and stamps ----

        private fun label(e: MemoraEvent, years: Int?) = when (e.type) {
            EventType.BIRTHDAY -> if (years != null) "Birthday, turns $years" else "Birthday"
            EventType.ANNIVERSARY -> if (years != null) "${ordinal(years)} anniversary" else "Anniversary"
            EventType.OTHER -> e.title?.ifBlank { null } ?: "Other event"
        }

        private fun inDays(n: Int) = when (n) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $n days"
        }

        /** "Sat 10 Oct" */
        private fun short(d: LocalDate) = "${WEEKDAYS[d.dayOfWeek.value - 1].take(3)} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}"

        /** "Mon, 12 Oct" */
        private fun shortDay(d: LocalDate) = "${WEEKDAYS[d.dayOfWeek.value - 1].take(3)}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}"

        private fun stampBg(t: EventType) = when (t) {
            EventType.BIRTHDAY -> R.drawable.widget_stamp_birthday
            EventType.ANNIVERSARY -> R.drawable.widget_stamp_anniversary
            EventType.OTHER -> R.drawable.widget_stamp_other
        }

        private fun stampColor(t: EventType) = when (t) {
            EventType.BIRTHDAY -> R.color.w_birthday
            EventType.ANNIVERSARY -> R.color.w_anniversary
            EventType.OTHER -> R.color.w_other
        }

        private fun stampFor(context: Context, v: RemoteViews, stamp: Int, t: EventType, filled: Boolean) {
            v.setInt(stamp, "setBackgroundResource", if (filled) R.drawable.widget_stamp_today else stampBg(t))
            v.setTextColor(stamp, context.getColor(if (filled) R.color.w_on_stamp else stampColor(t)))
        }
    }
}
