package com.smnexstudio.memora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.Fraunces
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.NavSpacer
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.TabTitle
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.initials
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.rowTitle
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.ui.whatLine

@Composable
fun PeopleScreen(navigate: (String) -> Unit) {
    val t = theme
    val c = t.c
    val version by Repo.version.collectAsState()
    val events = remember(version) { Repo.listEvents() }
    var soonest by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }

    val rows = remember(version, soonest, type) {
        events.filter { type == null || it.type.id == type }
            .map { it to nextDate(it) }
            .let { list -> if (soonest) list.sortedBy { it.second } else list.sortedBy { it.first.name.lowercase() } }
    }
    val groups = if (soonest) listOf("" to rows) else rows.groupBy { it.first.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#" }.toList()
    val people = events.map { it.name.trim().lowercase() }.distinct().size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TabTitle(
                    "People", Modifier.weight(1f),
                    below = "$people ${if (people == 1) "person" else "people"}, ${events.size} date${if (events.size == 1) "" else "s"} to remember",
                )
                PostButton("From contacts", { navigate("import") }, look = Look.OUTLINE, height = 40.dp, fontSize = 14)
            }
        }
        item {
            val shape = RoundedCornerShape(4.dp)
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(c.paper)
                    .border(1.5.dp, c.ink, shape).clickable(role = Role.Button) { navigate("search") }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LineIcon(Ic.SEARCH, c.muted, size = 18)
                Text("Search names, notes, phone, email", fontSize = 15.sp, color = c.muted)
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToggleChip("A to Z", !soonest, { soonest = false })
                ToggleChip("Soonest first", soonest, { soonest = true })
                ToggleChip("Birthdays", type == EventType.BIRTHDAY.id, { type = if (type == EventType.BIRTHDAY.id) null else EventType.BIRTHDAY.id }, dot = t.type(EventType.BIRTHDAY))
                ToggleChip("Anniversaries", type == EventType.ANNIVERSARY.id, { type = if (type == EventType.ANNIVERSARY.id) null else EventType.ANNIVERSARY.id }, dot = t.type(EventType.ANNIVERSARY))
                ToggleChip("Other", type == EventType.OTHER.id, { type = if (type == EventType.OTHER.id) null else EventType.OTHER.id }, dot = t.type(EventType.OTHER))
            }
        }
        if (events.isEmpty()) item {
            Box(Modifier.padding(16.dp)) {
                PaperCard(tilt = -0.8f, padding = PaddingValues(18.dp)) {
                    Handwriting("No one here yet. Add a birthday, or bring in the ones saved in your contacts.", size = 23)
                    PostButton("Add an event", { navigate("edit") }, Modifier.fillMaxWidth())
                }
            }
        }
        groups.forEach { (letter, list) ->
            item(key = "group-$letter") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (letter.isNotEmpty()) Text(
                        letter, Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
                        fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.stamp,
                    )
                    Column(Modifier.fillMaxWidth().paper(c.paper, c.shadow)) {
                        list.forEachIndexed { i, (e, d) ->
                            if (i > 0) DashedDivider()
                            val n = daysUntil(d)
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { navigate("event/${e.id}") }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Stamp(initials(e.name), t.type(e.type), width = 40.dp, height = 46.dp, fontSize = 15)
                                Column(Modifier.weight(1f)) {
                                    Text(rowTitle(e), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        whatLine(e, d) + if (e.year == null && e.type == EventType.BIRTHDAY) ", year unknown" else "",
                                        fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Handwriting(if (n == 0) "today" else "${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}", size = 21, bold = true)
                            }
                        }
                    }
                }
            }
        }
        item { NavSpacer() }
    }
}
