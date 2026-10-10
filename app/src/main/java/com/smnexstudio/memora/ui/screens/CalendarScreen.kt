package com.smnexstudio.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.festivalDate
import com.smnexstudio.memora.logic.occurrencesInMonth
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.ui.CircleIconButton
import com.smnexstudio.memora.ui.Dot
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.MonthGrid
import com.smnexstudio.memora.ui.NavSpacer
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.StampRow
import com.smnexstudio.memora.ui.TabTitle
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.dayTitle
import com.smnexstudio.memora.ui.monthMarks
import com.smnexstudio.memora.ui.rowTitle
import com.smnexstudio.memora.ui.stampText
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.ui.tiltFor
import com.smnexstudio.memora.ui.whatLine
import java.time.LocalDate
import java.time.YearMonth

private enum class Show(val label: String) { ALL("Everything"), PEOPLE("People only"), FESTIVALS("Festivals only") }

@Composable
fun CalendarScreen(navigate: (String) -> Unit) {
    val t = theme
    val c = t.c
    val version by Repo.version.collectAsState()
    val events = remember(version) { Repo.listEvents() }
    val festivalIds = remember(version) { Repo.festivals() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(today()) }
    var show by rememberSaveable { mutableStateOf(Show.ALL) }

    val marks = remember(version, month, show) {
        monthMarks(t, events, festivalIds, month, people = show != Show.FESTIVALS, festivals = show != Show.PEOPLE)
    }
    val day = selected
    val dayEvents = remember(version, day, show) {
        if (day == null || show == Show.FESTIVALS) emptyList()
        else events.filter { e -> occurrencesInMonth(e, YearMonth.from(day)).any { it == day } }.sortedBy { it.name }
    }
    val dayFestivals = remember(version, day, show) {
        if (day == null || show == Show.PEOPLE) emptyList()
        else availableFestivals().filter { it.id in festivalIds && festivalDate(it, day.year) == day }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabTitle("${MONTHS[month.monthValue - 1]} ${month.year}", Modifier.weight(1f))
            CircleIconButton(Ic.LEFT, "Previous month", { month = month.minusMonths(1); selected = null })
            CircleIconButton(Ic.RIGHT, "Next month", { month = month.plusMonths(1); selected = null })
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Show.entries.forEach { s -> ToggleChip(s.label, show == s, { show = s }) }
            if (month != YearMonth.now()) ToggleChip("Back to today", false, { month = YearMonth.now(); selected = today() })
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            MonthGrid(month, marks, selected) { d ->
                if (YearMonth.from(d) != month) month = YearMonth.from(d)
                selected = d
            }
        }

        if (day != null) Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(dayTitle(day), Modifier.weight(1f), italic = false, size = 20)
                LinkText("Add on this day", { navigate("edit?date=$day") })
            }
            if (dayEvents.isEmpty() && dayFestivals.isEmpty()) Text("Nothing on this day.", Modifier.padding(horizontal = 4.dp), fontSize = 14.sp, color = c.muted)
            dayEvents.forEachIndexed { i, e ->
                val sent = e.lastSentYear == day.year && daysUntil(day) <= 0
                StampRow(
                    stamp = stampText(e, day), color = t.type(e.type), title = rowTitle(e),
                    subtitle = whatLine(e, day) + if (daysUntil(day) == 0 && !sent) ", card not sent yet" else "",
                    filled = daysUntil(day) == 0 && !sent, tilt = tiltFor(i), onClick = { navigate("event/${e.id}") },
                    badge = if (sent) ({ RubberStamp("Sent", c.ok) }) else null,
                )
            }
            dayFestivals.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 1.5.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(c.festival, 8, diamond = true)
                    Text(f.name + if (f.approximate) ", may shift by a day" else "", fontWeight = FontWeight.Bold, color = c.festivalInk)
                }
            }
        }
        NavSpacer()
    }
}
