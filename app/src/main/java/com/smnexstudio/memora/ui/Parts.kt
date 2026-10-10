package com.smnexstudio.memora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Channel
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.CALENDARS
import com.smnexstudio.memora.logic.Festival
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.logic.MAX_CALENDARS
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.logic.Tone
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.festivalDate
import com.smnexstudio.memora.logic.isMilestone
import com.smnexstudio.memora.logic.occurrencesInMonth
import com.smnexstudio.memora.logic.ordinal
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.logic.yearsOn
import com.smnexstudio.memora.platform.ContactMatch
import com.smnexstudio.memora.platform.ContactsRepo
import com.smnexstudio.memora.platform.LabeledValue
import com.smnexstudio.memora.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.concurrent.thread

// ---- Words ----

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "Friday, 9 October" */
fun dayTitle(d: LocalDate): String = "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

fun weekday(d: LocalDate): String = WEEKDAYS[d.dayOfWeek.value - 1]

/** "today", "tomorrow", "3 days" */
fun daysLeft(n: Int) = when (n) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "$n days"
}

/** "Turns 30", "10th anniversary, a milestone", "Housewarming" for the occurrence on [on]. */
fun whatLine(e: MemoraEvent, on: LocalDate): String {
    val n = yearsOn(e, on)
    val base = when {
        e.type == EventType.BIRTHDAY && n != null -> "Turns $n"
        e.type == EventType.ANNIVERSARY && n != null -> "${ordinal(n)} anniversary"
        e.type == EventType.OTHER -> e.title?.ifBlank { null } ?: "Other event"
        else -> eventLabel(e)
    }
    return base + (if (isMilestone(e, on)) ", a milestone" else "") + (if (e.lunar) ", lunar date" else "")
}

/** Who the row is about: "Karthik’s Housewarming" for other events, else the name. */
fun rowTitle(e: MemoraEvent) = if (e.type == EventType.OTHER) "${e.name}’s ${e.title?.ifBlank { null } ?: "event"}" else e.name

/** What the person's stamp shows: their age or years married, else the day. */
fun stampText(e: MemoraEvent, on: LocalDate): String = yearsOn(e, on)?.toString() ?: on.dayOfMonth.toString()

// ---- Bottom navigation ----

enum class Tab(val route: String, val label: String, val icon: Ic) {
    HOME("home", "Home", Ic.HOME),
    CALENDAR("calendar", "Calendar", Ic.CALENDAR),
    PEOPLE("people", "People", Ic.PEOPLE),
    SETTINGS("settings", "Settings", Ic.SETTINGS),
}

/** Height of [BottomNav] above the system bar, for list padding. */
val BOTTOM_NAV_SPACE = 100.dp

@Composable
fun BottomNav(current: String?, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = theme.c
    Column(modifier.fillMaxWidth().background(c.paper)) {
        DashedDivider()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Tab.HOME, Tab.CALENDAR, null, Tab.PEOPLE, Tab.SETTINGS).forEach { tab ->
                if (tab == null) {
                    // The Add button: a red stamp lifted above the bar.
                    Column(
                        Modifier.weight(1f).clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add event" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.offset(y = (-16).dp).size(60.dp).rotate(-3f).background(c.ground)
                                .dashedBorder(c.stamp, 2.dp).padding(3.dp).background(c.stamp),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Ic.PLUS, c.onStamp, size = 26) }
                        Text("Add", Modifier.offset(y = (-12).dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.stamp)
                    }
                    return@forEach
                }
                val on = current == tab.route
                Column(
                    Modifier.weight(1f).height(62.dp).clickable(role = Role.Tab) { onTab(tab) }.semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.size(46.dp, 32.dp).then(if (on) Modifier.background(c.stampSoft).dashedBorder(c.stamp, 1.5.dp) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(tab.icon, if (on) c.stamp else c.muted, size = 22) }
                    Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) c.stamp else c.muted)
                }
            }
        }
    }
}

/** Spacer that keeps the end of a tab's list clear of [BottomNav]. */
@Composable
fun NavSpacer() = Box(Modifier.navigationBarsPadding().height(BOTTOM_NAV_SPACE).width(1.dp))

// ---- Rows ----

/** A paper row with a date stamp: used for upcoming events, search results and the day lists. */
@Composable
fun StampRow(
    stamp: String,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tilt: Float = 0f,
    trailing: String? = null,
    note: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val c = theme.c
    Row(
        modifier.fillMaxWidth().rotate(tilt).paper(c.paper, c.shadow).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Stamp(stamp, color, filled = filled, fontSize = if (stamp.length > 2) 15 else 18)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            note?.let { Text(it, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Handwriting(it, size = 22, bold = true) }
        badge?.invoke()
    }
}

/** Tilts for a stack of paper rows, so they look hand-placed. */
fun tiltFor(i: Int) = listOf(0.6f, -0.5f, 0.4f, -0.3f, 0.5f, -0.4f)[i % 6]

// ---- Calendar pieces ----

data class DayMark(val color: Color, val festival: Boolean)

/** Dots for each day of [month]: one per event (type colour) and a diamond per enabled festival. */
fun monthMarks(
    t: MemoraTheme,
    events: List<MemoraEvent>,
    festivalIds: List<String>,
    month: YearMonth,
    people: Boolean = true,
    festivals: Boolean = true,
): Map<LocalDate, List<DayMark>> {
    val out = mutableMapOf<LocalDate, MutableList<DayMark>>()
    if (people) for (e in events) for (d in occurrencesInMonth(e, month)) out.getOrPut(d) { mutableListOf() } += DayMark(t.type(e.type), false)
    if (festivals) for (f in availableFestivals().filter { it.id in festivalIds }) {
        festivalDate(f, month.year)?.takeIf { YearMonth.from(it) == month }?.let { out.getOrPut(it) { mutableListOf() } += DayMark(t.c.festival, true) }
    }
    return out
}

@Composable
fun MarkDots(marks: List<DayMark>, onColor: Color? = null) {
    Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        marks.take(3).forEach { Dot(onColor ?: it.color, 6, diamond = it.festival) }
    }
}

private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Sunday-to-Saturday strip for the current week. */
@Composable
fun WeekStrip(marks: (LocalDate) -> List<DayMark>, onDay: (LocalDate) -> Unit) {
    val c = theme.c
    val now = today()
    val start = now.minusDays((now.dayOfWeek.value % 7).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val isToday = d == now
            val dm = marks(d)
            Column(
                Modifier.weight(1f).height(72.dp)
                    .then(if (isToday) Modifier.dashedBorder(c.stamp, 2.dp).padding(3.dp) else Modifier)
                    .paper(c.paper, c.shadow)
                    .clickable(role = Role.Button) { onDay(d) }
                    .semantics { contentDescription = "${dayTitle(d)}, ${dm.size} event${if (dm.size == 1) "" else "s"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                val color = if (isToday) c.stamp else c.ink
                Text(SHORT_DAYS[d.dayOfWeek.value - 1], fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                Text("${d.dayOfMonth}", fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
                MarkDots(dm)
            }
        }
    }
}

/** The month grid on paper, Sunday first, with the neighbouring months' days greyed. */
@Composable
fun MonthGrid(month: YearMonth, marks: Map<LocalDate, List<DayMark>>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val t = theme
    val c = t.c
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7
    val start = first.minusDays(lead.toLong())
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth().paper(c.paper, c.shadow).padding(horizontal = 8.dp, vertical = 10.dp)) {
        Row(Modifier.padding(bottom = 6.dp)) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        for (w in 0 until weeks) {
            Row(Modifier.padding(top = 2.dp)) {
                for (i in 0..6) {
                    val d = start.plusDays((w * 7 + i).toLong())
                    val inMonth = YearMonth.from(d) == month
                    val sel = d == selected
                    val isToday = d == today()
                    val dm = if (inMonth) marks[d].orEmpty() else emptyList()
                    Column(
                        Modifier.weight(1f).height(52.dp)
                            .then(if (sel) Modifier.background(c.stamp) else if (isToday) Modifier.dashedBorder(c.stamp, 1.5.dp) else Modifier)
                            .clickable(role = Role.Button) { onSelect(d) }
                            .semantics {
                                contentDescription = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" +
                                    if (dm.isNotEmpty()) ", ${dm.size} event${if (dm.size > 1) "s" else ""}" else ""
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            "${d.dayOfMonth}", fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) c.onStamp else if (inMonth) c.ink else c.faint,
                        )
                        MarkDots(dm, if (sel) c.onStamp else null)
                    }
                }
            }
        }
        DashedDivider(Modifier.padding(top = 6.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                Triple("Birthday", t.type(EventType.BIRTHDAY), false),
                Triple("Anniversary", t.type(EventType.ANNIVERSARY), false),
                Triple("Other", t.type(EventType.OTHER), false),
                Triple("Festival", c.festival, true),
            ).forEach { (label, color, diamond) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(color, 8, diamond)
                    Text(label, fontSize = 12.sp, color = c.muted)
                }
            }
        }
    }
}

// ---- Festival banner ----

@Composable
/** The next festival, for information only: it doesn't open anything. */
fun FestivalBanner(f: Festival, date: LocalDate) {
    val c = theme.c
    val n = daysUntil(date)
    Row(
        Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 1.5.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp, 46.dp).border(1.5.dp, c.festival), contentAlignment = Alignment.Center) {
            Text("${date.dayOfMonth}", fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c.festivalInk)
        }
        Column(Modifier.weight(1f)) {
            Text(if (n == 0) "${f.name} today" else "${f.name} in ${daysLeft(n)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.festivalInk)
            Text(weekday(date) + if (f.approximate) ", may shift by a day" else "", fontSize = 13.sp, color = c.festivalInk)
        }
    }
}

// ---- Day sheet ----

fun clock(h: Int, m: Int) = "${(h + 11) % 12 + 1}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"

/** Everything on one day, as a sheet over the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(date: LocalDate, onDismiss: () -> Unit, navigate: (String) -> Unit) {
    val t = theme
    val c = t.c
    val version by Repo.version.collectAsState()
    val events = remember(version, date) {
        Repo.listEvents().filter { e -> occurrencesInMonth(e, YearMonth.from(date)).any { it == date } }.sortedBy { it.name }
    }
    val fests = remember(version, date) {
        val ids = Repo.festivals()
        availableFestivals().filter { it.id in ids && festivalDate(it, date.year) == date }
    }
    val sentByEvent = remember(version, date) { events.associate { e -> e.id to Repo.listHistory(e.id).firstOrNull { it.year == date.year } } }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val n = daysUntil(date)
    val toSend = events.count { sentByEvent[it.id] == null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.paperAlt, shape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayTitle(date), fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = c.ink)
                    Text(
                        (when {
                            n == 0 -> "Today"
                            n == 1 -> "Tomorrow"
                            n < 0 -> "${-n} day${if (n == -1) "" else "s"} ago"
                            else -> "In $n days"
                        }) + when {
                            events.isEmpty() && fests.isEmpty() -> ", nothing yet"
                            toSend == 0 || n > 0 -> ""
                            toSend == 1 -> ", one card to send"
                            else -> ", $toSend cards to send"
                        },
                        fontSize = 14.sp, color = c.muted,
                    )
                }
                CircleIconButton(Ic.CLOSE, "Close", onDismiss)
            }
            events.forEachIndexed { i, e ->
                val sent = sentByEvent[e.id]
                PaperCard(tilt = if (i % 2 == 0) -0.6f else 0.5f, padding = PaddingValues(14.dp), gap = 12.dp) {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onDismiss(); navigate("event/${e.id}") },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Stamp(stampText(e, date), t.type(e.type), filled = n == 0 && sent == null, width = 44.dp, height = 52.dp)
                            Column(Modifier.weight(1f)) {
                                Text(rowTitle(e), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                                Text(
                                    eventLabel(e) + (e.year?.let { if (e.type == EventType.BIRTHDAY) ", born $it" else ", since $it" } ?: ""),
                                    fontSize = 13.sp, color = c.muted,
                                )
                            }
                        }
                        if (sent != null) RubberStamp(
                            if (sent.channel == "skipped") "Skipped" else "Sent",
                            if (sent.channel == "skipped") c.muted else c.ok,
                            Modifier.align(Alignment.TopEnd), tilt = -8f, fontSize = 13,
                        )
                    }
                    if (sent == null && n <= 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PostButton(
                            "Send card", { onDismiss(); navigate("approve/${e.id}?year=${date.year}" + if (n < 0) "&belated=true" else "") },
                            Modifier.weight(1f), height = 44.dp,
                        )
                        PostButton("Skip this year", { Repo.markSent(e.id, date.year, "skipped", null) }, look = Look.OUTLINE, height = 44.dp)
                    } else if (sent != null && sent.channel != "skipped") {
                        val at = Instant.ofEpochMilli(sent.sentAt).atZone(ZoneId.systemDefault())
                        Text("Posted via ${Channel.from(sent.channel)?.label ?: sent.channel} at ${clock(at.hour, at.minute)}", fontSize = 13.sp, color = c.ok)
                    }
                }
            }
            fests.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 1.5.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(c.festival, 8, diamond = true)
                    Text(f.name + if (f.approximate) ", may shift by a day" else "", fontWeight = FontWeight.Bold, color = c.festivalInk)
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).dashedBorder(c.ink, 1.5.dp, radius = 4.dp)
                    .clickable(role = Role.Button) { onDismiss(); navigate("edit?date=$date") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Ic.PLUS, c.ink, size = 18)
                Text("Add event on ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}", fontWeight = FontWeight.SemiBold, color = c.ink)
            }
        }
    }
}

// ---- Ready wishes ----

/** Ready wishes by tone, as a sheet. [preview] turns a template into what the person receives. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadyWishesSheet(type: EventType, hasYear: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit, preview: (String) -> String) {
    val c = theme.c
    var tone by remember { mutableStateOf(Templates.toneFrom(Repo.getSetting("templateTone", "formal"))) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.paperAlt, shape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Ready wishes", size = 22)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tone.entries.forEach { t -> WeightedChip(t.label, tone == t, { tone = t; Repo.setSetting("templateTone", t.id) }) }
            }
            Templates.templatesFor(type, tone, hasYear).forEachIndexed { i, text ->
                PaperCard(tilt = tiltFor(i) / 2, onClick = { onPick(text); onDismiss() }, padding = PaddingValues(14.dp)) {
                    Handwriting(preview(text), size = 22)
                }
            }
            Text("{name} and {age} fill in by themselves each year.", fontSize = 13.sp, color = c.muted)
        }
    }
}

// ---- Contact suggestions ----

/**
 * Phone contacts matching what's typed, so their details fill in with one tap.
 * Under the name field ([byPhone] false) it matches the name; under the phone field,
 * the digits typed so far.
 */
@Composable
fun ContactSuggestions(
    name: String,
    phone: String,
    onRequestAccess: () -> Unit,
    accessVersion: Int,
    onPick: (ContactMatch, LabeledValue?) -> Unit,
    byPhone: Boolean = false,
) {
    val c = theme.c
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<ContactMatch>?>(null) }
    var dismissed by remember { mutableStateOf<String?>(null) }
    val hasAccess = ContactsRepo.hasAccess(context)

    LaunchedEffect(accessVersion, hasAccess) {
        if (hasAccess) all = withContext(Dispatchers.IO) { ContactsRepo.reachable(context) }
    }

    val typed = phone.filter(Char::isDigit)
    val key = if (byPhone) "phone|$typed" else "name|${name.trim().lowercase()}"
    if (dismissed == key) return
    if (byPhone && typed.length < 3) return
    // Under the name, stop once a full number is in the phone field.
    if (!byPhone && typed.length >= 10) return
    if (!hasAccess) {
        if (byPhone) LinkText("Search contacts for this number", onRequestAccess)
        else if (name.trim().length >= 2) LinkText("Find ${name.trim()}’s number in contacts", onRequestAccess)
        return
    }
    val matches = all?.let { if (byPhone) ContactsRepo.match(it, "", phone) else ContactsRepo.match(it, name, "") }.orEmpty()
    // Already filled from one of these contacts.
    if (typed.isNotEmpty() && matches.any { it.second?.value?.filter(Char::isDigit) == typed }) return
    if (matches.isEmpty()) return

    Column(Modifier.fillMaxWidth().border(1.dp, c.line).background(c.paper)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("From your contacts", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted)
            LinkText("Hide", { dismissed = key }, Modifier.padding(end = 12.dp))
        }
        matches.forEachIndexed { i, (contact, number) ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (i == 0) c.highlight else c.paper)
                    .clickable(role = Role.Button) { onPick(contact, number) }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(number?.value, contact.email?.value).joinToString(", ")
                if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---- Holiday calendars ----

/** Pick 1–2 holiday calendars; saving downloads their festivals. */
@Composable
fun CalendarsDialog(onDismiss: () -> Unit) {
    val c = theme.c
    val app = LocalContext.current.applicationContext
    val current = remember { Repo.calendars() }
    var sel by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.paper,
        shape = RoundedCornerShape(4.dp),
        title = { Text("Holiday calendars", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, color = c.ink) },
        text = {
            Column {
                Text("Pick up to $MAX_CALENDARS, e.g. your country and a religious calendar.", fontSize = 14.sp, color = c.muted)
                Column(Modifier.padding(top = 8.dp).heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    CALENDARS.forEach { k ->
                        val on = k.code in sel
                        val enabled = on || sel.size < MAX_CALENDARS
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Checkbox) {
                                sel = if (on) sel - k.code else sel + k.code
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(on, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                            Text(k.name, fontSize = 15.sp, color = if (enabled) c.ink else c.faint)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                onDismiss()
                if (sel != current) {
                    Repo.setCalendars(sel)
                    thread { GoogleHolidays.ensure(force = true); Reminders.syncAll(app) }
                }
            }, enabled = sel.isNotEmpty()) { Text("Save", fontWeight = FontWeight.Bold, color = c.ink) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.muted) } },
    )
}
