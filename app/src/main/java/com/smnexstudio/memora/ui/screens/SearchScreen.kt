package com.smnexstudio.memora.ui.screens

import com.smnexstudio.memora.ui.Route
import com.smnexstudio.memora.ui.historyVersion
import com.smnexstudio.memora.ui.rememberAgenda
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.shortDate
import com.smnexstudio.memora.ui.BoxField
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.IconTap
import com.smnexstudio.memora.ui.StampRow
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.daysLeft
import com.smnexstudio.memora.ui.rowTitle
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.ui.tiltFor
import com.smnexstudio.memora.ui.whatLine

private val NUMBER_WORDS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

@Composable
fun SearchScreen(onBack: () -> Unit, navigate: (Route) -> Unit) {
    val t = theme
    val c = t.c
    val agenda = rememberAgenda()
    val events = agenda.events
    val history = historyVersion()
    val notes = remember(history) { Repo.allNotes().groupBy { it.eventId } }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim().lowercase()
    // Each hit carries where it matched, to show under the result.
    val hits = remember(events, notes, q) {
        if (q.isEmpty()) emptyList() else events.mapNotNull { e -> whyMatch(e, notes[e.id].orEmpty().map { it.text }, q)?.let { e to it } }
    }
    val shown = hits.filter { type == null || it.first.type.id == type }.map { (e, why) -> Triple(e, agenda.next(e), why) }.sortedBy { it.second }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.BACK, "Back", onBack)
            BoxField(
                query, { query = it }, "Search", Modifier.weight(1f).focusRequester(focus),
                placeholder = "Names, events, notes, phone, email", leading = Ic.SEARCH, strong = true,
                trailing = { if (query.isNotEmpty()) IconTap(Ic.CLOSE, "Clear search", { query = "" }, color = c.muted, size = 16) },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip(if (q.isEmpty()) "All" else "All ${hits.size}", type == null, { type = null })
            EventType.entries.forEach { et ->
                ToggleChip(et.label, type == et.id, { type = if (type == et.id) null else et.id }, dot = t.type(et))
            }
        }
        if (q.isNotEmpty()) Text(
            "${NUMBER_WORDS.getOrNull(shown.size) ?: shown.size.toString()} result${if (shown.size == 1) "" else "s"} for “${query.trim()}”",
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = c.ink,
        )
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(shown, key = { _, it -> it.first.id }) { i, (e, d, why) ->
                val n = daysUntil(d)
                StampRow(
                    stamp = "${d.dayOfMonth}", color = t.type(e.type), title = rowTitle(e),
                    subtitle = "${whatLine(e, d)}, ${shortDate(d)}${if (n in 1..60) ", in ${daysLeft(n)}" else if (n == 0) ", today" else ""}",
                    note = why, tilt = tiltFor(i) / 1.5f, onClick = { navigate(Route.Event(e.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** Where [q] appears in the event, or null if it doesn't. */
private fun whyMatch(e: MemoraEvent, noteTexts: List<String>, q: String): String? {
    fun has(s: String?) = s?.lowercase()?.contains(q) == true
    fun snippet(s: String): String {
        val i = s.lowercase().indexOf(q)
        val from = (i - 20).coerceAtLeast(0)
        val to = (i + q.length + 20).coerceAtMost(s.length)
        return (if (from > 0) "…" else "") + s.substring(from, to) + if (to < s.length) "…" else ""
    }
    return when {
        has(e.name) -> "Matched name"
        has(e.title) -> "Matched event name"
        has(e.notes) -> "In notes: “${snippet(e.notes!!)}”"
        noteTexts.any(::has) -> "In notes: “${snippet(noteTexts.first(::has))}”"
        has(e.phone) || (q.any(Char::isDigit) && e.phone?.filter(Char::isDigit)?.contains(q.filter(Char::isDigit)) == true) -> "Phone: ${e.phone}"
        has(e.email) -> "Email: ${e.email}"
        has(e.type.label) -> "Matched ${e.type.label.lowercase()}"
        else -> null
    }
}
