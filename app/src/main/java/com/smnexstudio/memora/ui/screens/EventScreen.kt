package com.smnexstudio.memora.ui.screens

import com.smnexstudio.memora.ui.Route
import com.smnexstudio.memora.ui.historyVersion
import com.smnexstudio.memora.data.Prefs
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Channel
import com.smnexstudio.memora.data.NoteKind
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MILESTONE_OFFSET
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.isMilestone
import com.smnexstudio.memora.logic.longDate
import com.smnexstudio.memora.logic.lunarLabel
import com.smnexstudio.memora.logic.milestone
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.offsetLabel
import com.smnexstudio.memora.logic.shortDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.logic.ordinal
import com.smnexstudio.memora.logic.yearsOn
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.ui.BoardDetail
import com.smnexstudio.memora.ui.Mono
import com.smnexstudio.memora.ui.boardDate
import com.smnexstudio.memora.ui.BoxField
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.IconTap
import com.smnexstudio.memora.ui.LinedField
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.MilestoneTag
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.clock
import com.smnexstudio.memora.ui.daysLeft
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.stampText
import com.smnexstudio.memora.ui.theme
import kotlinx.coroutines.delay
import kotlin.concurrent.thread
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun EventScreen(id: Long, onBack: () -> Unit, navigate: (Route) -> Unit) {
    val t = theme
    val c = t.c
    val context = LocalContext.current
    val events by Repo.events.collectAsState()
    val e = remember(events, id) { Repo.getEvent(id) }
    val changes = historyVersion()
    val history = remember(changes, id) { Repo.listHistory(id) }
    val notes = remember(changes, id) { Repo.listNotes(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var noteKind by rememberSaveable { mutableStateOf(NoteKind.IDEA) }
    var noteText by rememberSaveable { mutableStateOf("") }
    var about by rememberSaveable(id) { mutableStateOf(e?.notes ?: "") }

    if (e == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Save "About" a moment after typing stops, and when leaving the screen.
    val saveAbout = {
        Repo.getEvent(id)?.let { cur ->
            val text = about.trim().ifEmpty { null }
            if (text != cur.notes) Repo.saveEvent(cur.copy(notes = text), id)
        }
    }
    LaunchedEffect(about) {
        delay(600)
        saveAbout()
    }
    DisposableEffect(Unit) { onDispose { saveAbout() } }

    val date = nextDate(e)
    val days = daysUntil(date)
    val big = isMilestone(e, date)
    val first = e.name.trim().split(Regex("\\s+|&")).firstOrNull()?.ifBlank { null } ?: e.name
    val offsets = if (big && MILESTONE_OFFSET !in e.offsets) listOf(MILESTONE_OFFSET) + e.offsets else e.offsets

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.BACK, "Back", onBack)
            if (t.board) Mono("Service detail", Modifier.weight(1f), size = 11, spacing = 1.5f) else Spacer(Modifier.weight(1f))
            Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { navigate(Route.Edit(id = e.id)) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                if (t.board) Mono("Edit", color = c.stamp, size = 12, bold = true)
                else Text("Edit", fontWeight = FontWeight.Bold, color = c.ink, textDecoration = TextDecoration.Underline)
            }
        }

        if (t.board) PaperCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp), padding = PaddingValues(16.dp), gap = 10.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Stamp(
                    "${date.dayOfMonth}", t.type(e.type), filled = days == 0, width = 70.dp, height = 62.dp, fontSize = 28,
                    caption = MONTHS[date.monthValue - 1].take(3),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.name.uppercase(), fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp, color = c.ink)
                    Mono("${eventLabel(e)} · ${longDate(e.month, e.day, e.year)}", size = 10, maxLines = 2)
                    yearsOn(e, date)?.let { y -> Mono(if (e.type == EventType.BIRTHDAY) "Turns $y" else "${ordinal(y)} anniversary", size = 10) }
                }
            }
            Mono(
                "● " + when (days) {
                    0 -> "On time — today"
                    1 -> "Due tomorrow"
                    else -> "Due in $days days · ${boardDate(date)}"
                } + if (big) " · milestone" else "",
                color = if (days == 0) c.ok else c.stamp, size = 11, bold = true, maxLines = 2,
            )
            lunarLabel(e)?.let { Mono("Tithi · $it", color = c.festival, size = 10, maxLines = 2) }
        }

        // The postcard header
        if (!t.board) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
            PaperCard(tilt = -0.8f, padding = PaddingValues(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stamp(
                        stampText(e, date), t.type(e.type), filled = true, width = 74.dp, height = 88.dp, fontSize = 30,
                        caption = if (days == 0) "today" else "in ${daysLeft(days)}",
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.name, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 30.sp, color = c.ink)
                        Text("${eventLabel(e)}, ${longDate(e.month, e.day, e.year)}", fontSize = 15.sp, color = c.muted)
                        lunarLabel(e)?.let { Text("Lunar: $it, this year ${shortDate(date)}", fontSize = 13.sp, color = c.festival) }
                        milestone(e, date)?.let { m -> if (big) MilestoneTag(m.replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            if (e.year != null) Box(
                Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = 14.dp).size(56.dp).rotate(if (theme.board) 0f else 12f).clip(CircleShape)
                    .background(c.paper).border(2.dp, c.stamp.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("SINCE\n${e.year}", fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = c.stamp.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            }
        }

        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = if (t.board) 14.dp else 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PostButton("Send card", { navigate(Route.Approve(e.id)) }, Modifier.weight(1f), look = Look.STAMP, height = 60.dp, icon = Ic.MAIL, stacked = true, fontSize = 14)
            PostButton(
                "Call", {
                    e.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(it)}"))) }
                        ?: Toast.makeText(context, "No phone saved for ${e.name}.", Toast.LENGTH_SHORT).show()
                },
                Modifier.weight(1f), look = Look.OUTLINE, height = 60.dp, icon = Ic.PHONE, stacked = true, fontSize = 14, enabled = e.phone != null,
            )
            PostButton("Reminders", { showReminders = true }, Modifier.weight(1f), look = Look.OUTLINE, height = 60.dp, icon = Ic.BELL, stacked = true, fontSize = 14)
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), gap = 0.dp) {
                InfoLine("Phone", e.phone ?: "Not saved", e.phone == null)
                DashedDivider()
                InfoLine("Email", e.email ?: "Not saved", e.email == null)
                DashedDivider()
                InfoLine("Reminders", (listOf("On the day") + offsets.sortedDescending().map { if (it == MILESTONE_OFFSET && big) "2 weeks (milestone)" else offsetLabel(it) }).joinToString(", "), false)
            }

            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 4.dp) {
                SectionTitle("About $first", size = 17)
                LinedField(about, { about = it }, label = "About $first", placeholder = "Where you met, what they love, allergies…", fontSize = 23, lineHeight = 30, minLines = 2)
            }

            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 12.dp) {
                SectionTitle("Notes", size = 17)
                Row(Modifier.fillMaxWidth().drawBehind { drawLine(c.line, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx()) }) {
                    listOf(NoteKind.IDEA to "Gift ideas", NoteKind.GIFT to "Gifts given", NoteKind.NOTE to "Other").forEach { (k, label) ->
                        val on = noteKind == k
                        Text(
                            label,
                            Modifier.clickable(role = Role.Tab) { noteKind = k }.semantics { selected = on }
                                .heightIn(min = 44.dp).padding(end = 16.dp)
                                .drawBehind {
                                    if (on) drawLine(c.stamp, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width - 16.dp.toPx(), size.height - 1.dp.toPx()), 2.dp.toPx())
                                }
                                .padding(vertical = 12.dp),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) c.ink else c.muted, maxLines = 1,
                        )
                    }
                }
                val list = notes.filter { it.kind == noteKind }
                if (list.isEmpty()) Text(
                    when (noteKind) {
                        NoteKind.IDEA -> "No gift ideas yet."
                        NoteKind.GIFT -> "Nothing logged yet. Note what you gave, and next year you won’t repeat it."
                        NoteKind.NOTE -> "No other notes yet."
                    },
                    fontSize = 14.sp, color = c.muted,
                )
                list.forEachIndexed { i, n ->
                    if (i > 0) DashedDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (n.kind == NoteKind.GIFT && n.year != null) Text("${n.year}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c.muted)
                        Handwriting(n.text, Modifier.weight(1f), size = 23)
                        IconTap(Ic.CLOSE, "Remove note", { Repo.deleteNote(n.id) }, color = c.muted, size = 16)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoxField(
                        noteText, { noteText = it }, "New note", Modifier.weight(1f),
                        placeholder = when (noteKind) {
                            NoteKind.IDEA -> "Jot a gift idea"
                            NoteKind.GIFT -> "What you gave in ${today().year}"
                            NoteKind.NOTE -> "Anything to remember"
                        },
                    )
                    PostButton("Add", {
                        if (noteText.isNotBlank()) {
                            Repo.addNote(e.id, noteKind, noteText.trim(), if (noteKind == NoteKind.GIFT) today().year else null)
                            noteText = ""
                        }
                    }, height = 44.dp, enabled = noteText.isNotBlank())
                }
            }

            if (history.isNotEmpty() && t.board) PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), gap = 0.dp) {
                Mono("Cards sent · log", Modifier.padding(bottom = 6.dp), size = 10, spacing = 1.5f)
                history.forEachIndexed { i, h ->
                    if (i > 0) DashedDivider()
                    val skipped = h.channel == "skipped"
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${h.year}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (skipped) c.muted else c.ink)
                        Mono(if (skipped) "Not sent" else Channel.from(h.channel)?.label ?: h.channel, Modifier.weight(1f), size = 11)
                        Mono(if (skipped) "Skipped" else "Sent", color = if (skipped) c.muted else c.ok, size = 11, bold = true)
                    }
                }
            } else if (history.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Cards sent over the years", Modifier.padding(horizontal = 4.dp), size = 17)
                history.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { i, h ->
                            val skipped = h.channel == "skipped"
                            Column(
                                Modifier.weight(1f).rotate(if (theme.board) 0f else listOf(-2f, 1.5f, -1f)[i]).paper(if (skipped) c.paperAlt else c.paper, c.shadow).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("${h.year}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (skipped) c.muted else c.ink)
                                Text(if (skipped) "Not sent" else Channel.from(h.channel)?.label ?: h.channel, fontSize = 12.sp, color = c.muted)
                                RubberStamp(if (skipped) "Skipped" else "Sent", if (skipped) c.muted else c.ok, fontSize = 11)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            PostButton("Delete event", { confirmDelete = true }, Modifier.fillMaxWidth().padding(top = 8.dp), look = Look.GHOST)
            Spacer(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
        }
    }

    if (showReminders) AlertDialog(
        onDismissRequest = { showReminders = false },
        containerColor = c.paper,
        title = { Text("Reminders at ${clock(Prefs.reminderHour.get(), Prefs.reminderMinute.get())}", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (offsets.sortedDescending().map { o -> (if (o == MILESTONE_OFFSET && big) "2 weeks before, milestone" else "${offsetLabel(o)} before") to date.minusDays(o.toLong()) } +
                    ("On the day, review and send" to date)).forEach { (label, d) ->
                    val done = daysUntil(d) < 0
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = if (done) c.muted else c.ink, textDecoration = if (done) TextDecoration.LineThrough else null)
                        Text(shortDate(d), fontSize = 13.sp, color = c.muted)
                    }
                }
                LinkText("Change early reminders", { showReminders = false; navigate(Route.Edit(id = e.id)) })
            }
        },
        confirmButton = { TextButton({ showReminders = false }) { Text("Done", fontWeight = FontWeight.Bold, color = c.ink) } },
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = c.paper,
        title = { Text("Delete event?", color = c.ink) },
        text = { Text("${e.name}’s reminders, notes and history will be removed.", color = c.muted) },
        confirmButton = {
            TextButton({
                confirmDelete = false
                val app = context.applicationContext
                Reminders.cancelEvent(app, e.id)
                Repo.deleteEvent(e.id)
                thread { Reminders.syncAll(app) }
                onBack()
            }) { Text("Delete", color = c.stamp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String, faint: Boolean) {
    val c = theme.c
    if (theme.board) return BoardDetail(label, value, faint)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.ink)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (faint) c.faint else c.ink, textAlign = TextAlign.End)
    }
}
