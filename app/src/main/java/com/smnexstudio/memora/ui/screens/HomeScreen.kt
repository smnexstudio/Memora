package com.smnexstudio.memora.ui.screens

import com.smnexstudio.memora.ui.Route
import com.smnexstudio.memora.ui.festivalVersion
import com.smnexstudio.memora.ui.rememberAgenda
import com.smnexstudio.memora.data.Prefs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.logic.Festival
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.fillMessage
import com.smnexstudio.memora.logic.isMilestone
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.ui.CircleIconButton
import com.smnexstudio.memora.ui.weekday
import com.smnexstudio.memora.ui.boardIn
import com.smnexstudio.memora.ui.boardRed
import com.smnexstudio.memora.ui.Mono
import com.smnexstudio.memora.ui.IconTap
import com.smnexstudio.memora.ui.Flap
import com.smnexstudio.memora.ui.DepartureRow
import com.smnexstudio.memora.ui.BoardHeader
import com.smnexstudio.memora.ui.BoardColumns
import com.smnexstudio.memora.ui.DaySheet
import com.smnexstudio.memora.ui.FestivalBanner
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.MilestoneTag
import com.smnexstudio.memora.ui.NavSpacer
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.StampRow
import com.smnexstudio.memora.ui.TabTitle
import com.smnexstudio.memora.ui.WeekStrip
import com.smnexstudio.memora.ui.dayTitle
import com.smnexstudio.memora.ui.daysLeft
import com.smnexstudio.memora.ui.monthMarks
import com.smnexstudio.memora.ui.rowTitle
import com.smnexstudio.memora.ui.stampText
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.ui.tiltFor
import com.smnexstudio.memora.ui.whatLine
import java.time.LocalDate
import java.time.YearMonth

/** A missed wish is offered for this many days after the event. */

@Composable
fun HomeScreen(navigate: (Route) -> Unit) {
    val t = theme
    val c = t.c
    val agenda = rememberAgenda()
    val events = agenda.events
    val festivalIds = Prefs.festivals.current
    val festivalDates = festivalVersion()
    var sheet by remember { mutableStateOf<LocalDate?>(null) }
    val now = agenda.today

    val upcoming = agenda.upcoming
    val todays = upcoming.filter { it.second == now }
    val soon = upcoming.filter { it.second != now }.take(8)
    val missed = agenda.missed
    val marks = remember(agenda, festivalIds, festivalDates, t) {
        val m = YearMonth.from(now)
        monthMarks(t, agenda, festivalIds, m.minusMonths(1)) + monthMarks(t, agenda, festivalIds, m) + monthMarks(t, agenda, festivalIds, m.plusMonths(1))
    }
    val nextFestival = remember(festivalIds, festivalDates, now) {
        availableFestivals().filter { it.id in festivalIds }.mapNotNull { f -> nextFestivalDate(f)?.let { f to it } }
            .filter { daysUntil(it.second) <= 30 }.minByOrNull { it.second }
    }

    if (t.board) {
        BoardHome(events, upcoming, missed, festivalIds, navigate)
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabTitle("Memora", Modifier.weight(1f), above = dayTitle(now))
                CircleIconButton(Ic.SEARCH, "Search", { navigate(Route.Search) })
            }
        }

        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when {
                    events.isEmpty() -> PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp)) {
                        Text("Your first card", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
                        Handwriting("Add a birthday or anniversary, and Memora will remind you and have the wishes ready.", size = 24)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PostButton("Add an event", { navigate(Route.Edit()) }, Modifier.weight(1f))
                            PostButton("From contacts", { navigate(Route.Import) }, look = Look.OUTLINE)
                        }
                    }
                    todays.isNotEmpty() -> {
                        val (e, d) = todays.first()
                        TodayCard(e, d, onClick = { navigate(Route.Event(e.id)) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val sent = e.lastSentYear == d.year
                            PostButton(if (sent) "Send another card" else "Write & send this card", { navigate(Route.Approve(e.id)) }, Modifier.weight(1f))
                            if (todays.size > 1) PostButton("+${todays.size - 1} more", { sheet = now }, look = Look.OUTLINE)
                        }
                    }
                    else -> soon.firstOrNull()?.let { (e, d) ->
                        PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp), onClick = { navigate(Route.Event(e.id)) }) {
                            Text("Nothing to post today", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
                            Handwriting("Next up: ${rowTitle(e)}, ${if (daysUntil(d) == 1) "tomorrow" else "in ${daysLeft(daysUntil(d))}"}.", size = 24)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("This week", Modifier.weight(1f), italic = false, size = 20)
                    LinkText("Full calendar", { navigate(Route.Calendar) })
                }
                WeekStrip(marks = { marks[it].orEmpty() }, onDay = { sheet = it })
            }
        }

        item { BackupNudge(events.size, navigate) }
        nextFestival?.let { (f, d) ->
            item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) { FestivalBanner(f, d) } }
        }

        if (soon.isNotEmpty() || missed.isNotEmpty()) item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("In the post soon", Modifier.weight(1f), italic = false, size = 20)
                if (missed.isNotEmpty()) LinkText("${missed.size} missed", { navigate(Route.Missed) }, color = c.stamp)
            }
        }
        itemsIndexed(soon, key = { _, it -> "soon-${it.first.id}" }) { i, (e, d) ->
            StampRow(
                stamp = "${d.dayOfMonth}", color = t.type(e.type), title = rowTitle(e), subtitle = whatLine(e, d),
                trailing = daysLeft(daysUntil(d)), tilt = tiltFor(i), onClick = { navigate(Route.Event(e.id)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        item { NavSpacer() }
    }

    sheet?.let { DaySheet(it, onDismiss = { sheet = null }, navigate = navigate) }
}

/** One line of the board: a person's date or a festival. */
private sealed interface Departure {
    val date: LocalDate
    data class Person(val e: MemoraEvent, override val date: LocalDate) : Departure
    data class Fest(val f: Festival, override val date: LocalDate) : Departure
}

/** The Departures home: everyone due, as one timetable. Missed first, then today, then what's coming. */
/** A backup older than this is worth a reminder, once there's enough in Memora to lose. */
private const val BACKUP_STALE_DAYS = 90L
private const val BACKUP_NUDGE_EVENTS = 5

/**
 * Memora keeps everything on this phone only, so a lost phone loses it all. A quiet line on
 * Home when there's no backup, or none for [BACKUP_STALE_DAYS] days, pointing to Settings.
 */
@Composable
private fun BackupNudge(eventCount: Int, navigate: (Route) -> Unit) {
    val last = Prefs.lastBackupAt.current
    val days = last?.let { java.time.Duration.ofMillis(System.currentTimeMillis() - it).toDays() }
    if (eventCount < BACKUP_NUDGE_EVENTS || (days != null && days < BACKUP_STALE_DAYS)) return
    val c = theme.c
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (days == null) "Everything is on this phone only, with no backup yet."
            else "Your last backup was ${days / 30} months ago.",
            Modifier.weight(1f), fontSize = 13.sp, color = c.muted,
        )
        LinkText("Back up", { navigate(Route.Settings) }, color = c.stamp)
    }
}

@Composable
private fun BoardHome(
    events: List<MemoraEvent>,
    upcoming: List<Pair<MemoraEvent, LocalDate>>,
    missed: List<Pair<MemoraEvent, LocalDate>>,
    festivalIds: List<String>,
    navigate: (Route) -> Unit,
) {
    val t = theme
    val c = t.c
    val now = today()
    val rows = remember(upcoming, festivalIds, now) {
        val people = upcoming.take(12).map { (e, d) -> Departure.Person(e, d) }
        val horizon = people.lastOrNull()?.date?.coerceAtLeast(now.plusDays(30)) ?: now.plusDays(30)
        val fests = availableFestivals().filter { it.id in festivalIds }
            .mapNotNull { f -> nextFestivalDate(f)?.let { Departure.Fest(f, it) } }
            .filter { !it.date.isAfter(horizon) }
        (people + fests).sortedBy { it.date }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            BoardHeader(
                "Memora",
                Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 12.dp),
                trailing = { IconTap(Ic.SEARCH, "Search", { navigate(Route.Search) }) },
            )
        }
        if (events.isEmpty()) {
            item { BoardColumns("Date", "Who", "In") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { Flap("—", c.faint) } }
                    Text("NO CONTACTS YET", Modifier.padding(top = 8.dp), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.ink)
                    Mono("Add the people you don't want to miss.", size = 11, maxLines = 3)
                    PostButton("Add someone", { navigate(Route.Edit()) }, Modifier.padding(top = 10.dp).fillMaxWidth(), icon = Ic.PLUS)
                    PostButton("Import from contacts", { navigate(Route.Import) }, Modifier.fillMaxWidth(), look = Look.OUTLINE)
                }
            }
            item { NavSpacer() }
            return@LazyColumn
        }
        item { BackupNudge(events.size, navigate) }
        item { BoardColumns("Date", "Who", "In") }
        items(missed, key = { "missed-${it.first.id}" }) { (e, d) ->
            DepartureRow(
                flap = "${d.dayOfMonth}", name = rowTitle(e), meta = boardMeta(e, d), whenText = "MISSED", whenColor = boardRed,
                flapColor = boardRed, onClick = { navigate(Route.Event(e.id)) },
                action = "Wish late", onAction = { navigate(Route.Approve.late(e.id, d)) },
            )
        }
        items(rows, key = { r -> if (r is Departure.Person) "p-${r.e.id}" else "f-${(r as Departure.Fest).f.id}" }) { r ->
            val n = daysUntil(r.date)
            when (r) {
                is Departure.Person -> {
                    val e = r.e
                    val sent = n == 0 && e.lastSentYear == r.date.year
                    DepartureRow(
                        flap = "${r.date.dayOfMonth}", name = rowTitle(e), meta = boardMeta(e, r.date) + if (sent) " · sent" else "",
                        whenText = if (sent) "SENT" else boardIn(n), whenColor = if (n == 0) c.ok else null,
                        flapColor = if (n == 0) c.stamp else t.type(e.type), now = n == 0 && !sent,
                        action = if (n == 0 && !sent) "Wish now" else null, onAction = { navigate(Route.Approve(e.id)) },
                        onClick = { navigate(Route.Event(e.id)) },
                    )
                }
                is Departure.Fest -> DepartureRow(
                    flap = "${r.date.dayOfMonth}", name = r.f.name, meta = "Festival · " + weekday(r.date) + if (r.f.approximate) " · may shift a day" else "",
                    whenText = boardIn(n), flapColor = c.festival, onClick = { navigate(Route.Calendar) },
                )
            }
        }
        item { NavSpacer() }
    }
}

/** "Birthday · turns 27, a milestone" / "10th anniversary, a milestone" */
private fun boardMeta(e: MemoraEvent, d: LocalDate): String {
    val what = whatLine(e, d)
    return if (e.type == EventType.BIRTHDAY && what != eventLabel(e)) "Birthday · $what" else what
}

/** Today's postcard: the wish in handwriting, the stamp and the address lines. */
@Composable
private fun TodayCard(e: MemoraEvent, on: LocalDate, onClick: () -> Unit) {
    val t = theme
    val c = t.c
    PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Today", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
                Handwriting(fillMessage(e.message, e, on), size = 25, lineHeight = 28)
                if (isMilestone(e, on)) MilestoneTag("Milestone")
            }
            Box(Modifier.width(1.dp).height(120.dp).background(c.line))
            Column(Modifier.width(104.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stamp(stampText(e, on), t.type(e.type), filled = true, width = 60.dp, height = 72.dp, fontSize = 24)
                AddressLine(e.name, bold = true)
                AddressLine(eventLabel(e))
            }
        }
    }
}

@Composable
private fun AddressLine(text: String, bold: Boolean = false) {
    val c = theme.c
    Column(Modifier.fillMaxWidth()) {
        Text(
            text, fontSize = if (bold) 13.sp else 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (bold) c.ink else c.muted, maxLines = 2,
        )
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(c.ink))
    }
}
