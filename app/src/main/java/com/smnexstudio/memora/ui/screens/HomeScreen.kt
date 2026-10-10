package com.smnexstudio.memora.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.fillMessage
import com.smnexstudio.memora.logic.isMilestone
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.previousDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.ui.CircleIconButton
import com.smnexstudio.memora.ui.DaySheet
import com.smnexstudio.memora.ui.FestivalBanner
import com.smnexstudio.memora.ui.Fraunces
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
const val BELATED_DAYS = 2

/** Events from the last [BELATED_DAYS] days nobody sent or skipped, newest first. */
fun missedEvents(events: List<MemoraEvent>): List<Pair<MemoraEvent, LocalDate>> = events
    .map { it to previousDate(it) }
    .filter { (e, d) -> daysUntil(d) >= -BELATED_DAYS && (e.lastSentYear ?: 0) < d.year }
    .sortedByDescending { it.second }

@Composable
fun HomeScreen(navigate: (String) -> Unit) {
    val t = theme
    val c = t.c
    val version by Repo.version.collectAsState()
    val events = remember(version) { Repo.listEvents() }
    val festivalIds = remember(version) { Repo.festivals() }
    var sheet by remember { mutableStateOf<LocalDate?>(null) }
    val now = today()

    val upcoming = remember(version, now) {
        events.map { it to nextDate(it) }.sortedWith(compareBy({ it.second }, { it.first.name }))
    }
    val todays = upcoming.filter { it.second == now }
    val soon = upcoming.filter { it.second != now }.take(8)
    val missed = remember(version, now) { missedEvents(events) }
    val marks = remember(version, now) {
        val m = YearMonth.from(now)
        monthMarks(t, events, festivalIds, m.minusMonths(1)) + monthMarks(t, events, festivalIds, m) + monthMarks(t, events, festivalIds, m.plusMonths(1))
    }
    val nextFestival = remember(version, now) {
        availableFestivals().filter { it.id in festivalIds }.mapNotNull { f -> nextFestivalDate(f)?.let { f to it } }
            .filter { daysUntil(it.second) <= 30 }.minByOrNull { it.second }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabTitle("Memora", Modifier.weight(1f), above = dayTitle(now))
                CircleIconButton(Ic.SEARCH, "Search", { navigate("search") })
            }
        }

        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when {
                    events.isEmpty() -> PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp)) {
                        Text("Your first card", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
                        Handwriting("Add a birthday or anniversary, and Memora will remind you and have the wishes ready.", size = 24)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PostButton("Add an event", { navigate("edit") }, Modifier.weight(1f))
                            PostButton("From contacts", { navigate("import") }, look = Look.OUTLINE)
                        }
                    }
                    todays.isNotEmpty() -> {
                        val (e, d) = todays.first()
                        TodayCard(e, d, onClick = { navigate("event/${e.id}") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val sent = e.lastSentYear == d.year
                            PostButton(if (sent) "Send another card" else "Write & send this card", { navigate("approve/${e.id}") }, Modifier.weight(1f))
                            if (todays.size > 1) PostButton("+${todays.size - 1} more", { sheet = now }, look = Look.OUTLINE)
                        }
                    }
                    else -> soon.firstOrNull()?.let { (e, d) ->
                        PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp), onClick = { navigate("event/${e.id}") }) {
                            Text("Nothing to post today", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
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
                    LinkText("Full calendar", { navigate("calendar") })
                }
                WeekStrip(marks = { marks[it].orEmpty() }, onDay = { sheet = it })
            }
        }

        nextFestival?.let { (f, d) ->
            item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) { FestivalBanner(f, d) } }
        }

        if (soon.isNotEmpty() || missed.isNotEmpty()) item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("In the post soon", Modifier.weight(1f), italic = false, size = 20)
                if (missed.isNotEmpty()) LinkText("${missed.size} missed", { navigate("missed") }, color = c.stamp)
            }
        }
        itemsIndexed(soon, key = { _, it -> "soon-${it.first.id}" }) { i, (e, d) ->
            StampRow(
                stamp = "${d.dayOfMonth}", color = t.type(e.type), title = rowTitle(e), subtitle = whatLine(e, d),
                trailing = daysLeft(daysUntil(d)), tilt = tiltFor(i), onClick = { navigate("event/${e.id}") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        item { NavSpacer() }
    }

    sheet?.let { DaySheet(it, onDismiss = { sheet = null }, navigate = navigate) }
}

/** Today's postcard: the wish in handwriting, the stamp and the address lines. */
@Composable
private fun TodayCard(e: MemoraEvent, on: LocalDate, onClick: () -> Unit) {
    val t = theme
    val c = t.c
    PaperCard(tilt = -1.2f, padding = PaddingValues(18.dp), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Today", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.stamp)
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
