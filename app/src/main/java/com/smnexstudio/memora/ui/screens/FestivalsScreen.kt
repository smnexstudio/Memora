package com.smnexstudio.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MAX_CALENDARS
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.chosenCalendars
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.nextFestivalDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.CalendarsDialog
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.Fraunces
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.IconTap
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PaperSwitch
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.SwitchRow
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.daysLeft
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.ui.tiltFor
import com.smnexstudio.memora.ui.weekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.concurrent.thread

@Composable
fun FestivalsScreen(onBack: () -> Unit) {
    val c = theme.c
    val app = LocalContext.current.applicationContext
    val version by Repo.version.collectAsState()
    val ids = remember(version) { Repo.festivals() }
    val calendars = remember(version) { chosenCalendars(Repo.calendars()) }
    val early = remember(version) { Repo.getBool("festivalEarly", false) }
    val festivals = remember(version) {
        availableFestivals().map { it to nextFestivalDate(it) }.sortedWith(compareBy(nullsLast()) { it.second })
    }
    val fetchedAt = remember(version) { Repo.getSetting("festivalFetchedAt", "").toLongOrNull() }
    var attempt by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var pickCalendars by remember { mutableStateOf(false) }

    // Download here too in case it failed at app start (e.g. offline). Tapping refresh skips the hourly retry limit.
    LaunchedEffect(attempt, calendars) {
        loading = true
        withContext(Dispatchers.IO) { if (GoogleHolidays.ensure(force = attempt > 0, refresh = refresh)) Reminders.syncAll(app) }
        refresh = false
        loading = false
    }

    fun setAll(on: Boolean) {
        val here = festivals.map { it.first.id }
        Repo.setFestivals(if (on) (ids + here).distinct() else ids - here.toSet())
        thread { Reminders.syncAll(app) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Festivals", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Holiday calendars, ${calendars.size} of $MAX_CALENDARS", Modifier.weight(1f), size = 17)
                    LinkText("Change", { pickCalendars = true })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    calendars.forEach { k ->
                        Box(
                            Modifier.heightIn(min = 44.dp).background(c.festivalBg).dashedBorder(c.festival, 1.5.dp).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(k.name, fontWeight = FontWeight.Bold, color = c.festivalInk) }
                    }
                }
                DashedDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val years = GoogleHolidays.storedYears()
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(18.dp), color = c.festival, strokeWidth = 2.dp)
                        festivals.isNotEmpty() -> LineIcon(Ic.CHECK, c.ok, size = 18)
                        else -> LineIcon(Ic.ALERT, c.stamp, size = 18)
                    }
                    Text(
                        when {
                            loading -> "Downloading from Google Calendar…"
                            festivals.isEmpty() -> "Couldn’t download festivals. Check your internet connection."
                            else -> "${years.joinToString(" and ")} dates downloaded from Google Calendar${fetchedAt?.let { ", ${whenText(it)}" } ?: ""}"
                        },
                        Modifier.weight(1f), fontSize = 13.sp, color = c.muted,
                    )
                    IconTap(Ic.REFRESH, "Download dates again", { refresh = true; attempt++ })
                }
            }

            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), gap = 0.dp) {
                SwitchRow("Also remind a week before", "On top of the morning-of reminder", early, { on ->
                    Repo.setBool("festivalEarly", on)
                    thread { Reminders.syncAll(app) }
                })
            }

            if (festivals.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Remind me on the day", Modifier.weight(1f), size = 17)
                    val allOn = festivals.all { it.first.id in ids }
                    LinkText(if (allOn) "All off" else "All on", { setAll(!allOn) })
                }
                festivals.forEachIndexed { i, (f, next) ->
                    Row(
                        Modifier.fillMaxWidth().rotate(tiltFor(i) * 0.7f).paper(c.paper, c.shadow).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(
                            Modifier.size(46.dp, 54.dp).dashedBorder(c.festival, 1.5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            if (next != null) {
                                Text(MONTHS[next.monthValue - 1].take(3).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.festival, lineHeight = 11.sp)
                                Text("${next.dayOfMonth}", fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.festival, lineHeight = 22.sp)
                            } else Text("?", fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.festival)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(f.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                            Text(
                                next?.let { d ->
                                    val n = daysUntil(d)
                                    weekday(d) + (if (d.year != today().year) ", ${d.year}" else "") + if (n in 0..30) ", ${if (n == 0) "today" else "in ${daysLeft(n)}"}" else ""
                                } ?: "Date not available yet",
                                fontSize = 13.sp, color = c.muted,
                            )
                            if (f.approximate) RubberStamp("May shift by a day", c.festival, tilt = -3f, fontSize = 11)
                        }
                        PaperSwitch(f.id in ids, { on ->
                            Repo.setFestivals(if (on) ids + f.id else ids - f.id)
                            thread { Reminders.syncAll(app) }
                        })
                    }
                }
            } else if (!loading) PostButton("Try again", { attempt++ }, Modifier.fillMaxWidth(), look = Look.OUTLINE)
        }
    }

    if (pickCalendars) CalendarsDialog { pickCalendars = false }
}

/** "today", "yesterday" or "on 3 October". */
private fun whenText(millis: Long): String {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (val n = -daysUntil(d)) {
        0 -> "today"
        1 -> "yesterday"
        else -> if (n < 7) "$n days ago" else "on ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }
}
