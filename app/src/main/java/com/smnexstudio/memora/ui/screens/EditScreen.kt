package com.smnexstudio.memora.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.OFFSET_OPTIONS
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.Lunar
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.logic.fillMessage
import com.smnexstudio.memora.logic.hasPlaceholders
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.nextOccurrence
import com.smnexstudio.memora.logic.shortDate
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.BottomBar
import com.smnexstudio.memora.ui.Caveat
import com.smnexstudio.memora.ui.ContactSuggestions
import com.smnexstudio.memora.ui.Fraunces
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PaperSwitch
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.ReadyWishesSheet
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.UnderlineField
import com.smnexstudio.memora.ui.UnderlinePicker
import com.smnexstudio.memora.ui.clock
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.ruled
import com.smnexstudio.memora.ui.theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.concurrent.thread

private val PLACEHOLDER_CHIPS = listOf("{name}", "{first}", "{age}", "{ordinal}")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(editingId: Long?, initialDate: LocalDate?, onBack: () -> Unit, navigate: (String) -> Unit) {
    val t = theme
    val c = t.c
    val context = LocalContext.current
    val existing = remember(editingId) { editingId?.let { Repo.getEvent(it) } }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: EventType.BIRTHDAY) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var month by rememberSaveable { mutableStateOf(existing?.month ?: initialDate?.monthValue) }
    var day by rememberSaveable { mutableStateOf(existing?.day ?: initialDate?.dayOfMonth) }
    var yearText by rememberSaveable { mutableStateOf(existing?.year?.toString() ?: "") }
    var noYear by rememberSaveable { mutableStateOf(existing != null && existing.year == null) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var messageEdited by rememberSaveable { mutableStateOf(existing != null) }
    var message by remember { mutableStateOf(TextFieldValue(existing?.message ?: "")) }
    var offsets by rememberSaveable { mutableStateOf(existing?.offsets ?: Repo.defaultOffsets()) }
    var lunar by rememberSaveable { mutableStateOf(existing?.lunar ?: false) }
    var showPicker by remember { mutableStateOf(false) }
    var showWishes by remember { mutableStateOf(false) }
    var contactsVersion by remember { mutableIntStateOf(0) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsVersion++ }

    val year = yearText.trim().toIntOrNull()?.takeIf { it in 1801..today().year + 1 }
    val hasYear = !noYear && year != null
    // The suggested message follows the type until the user writes their own.
    val shown = if (messageEdited) message else TextFieldValue(Templates.default(type, hasYear))
    val nextOn = if (month != null && day != null) nextOccurrence(month!!, day!!) else today() // close enough for a preview
    val preview = { text: String -> fillMessage(text, name.trim().ifEmpty { "Ravi" }, if (noYear) null else year, nextOn) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun save() {
        when {
            name.isBlank() -> return toast("Enter who this event is for.")
            type == EventType.OTHER && title.isBlank() -> return toast("Add an event name, e.g. Housewarming.")
            month == null || day == null -> return toast("Choose the date of the event.")
            !noYear && yearText.isNotBlank() && year == null -> return toast("Check the year, or switch on “I don’t know the year”.")
            shown.text.isBlank() -> return toast("Write the wishes you want to send.")
        }
        val e = MemoraEvent(
            id = editingId ?: 0, type = type, name = name.trim(),
            title = if (type == EventType.OTHER) title.trim() else null,
            month = month!!, day = day!!, year = if (noYear) null else year,
            phone = phone.trim().ifEmpty { null }, email = email.trim().ifEmpty { null },
            message = shown.text.trim(), offsets = offsets, notes = existing?.notes,
            contactId = existing?.contactId, lastSentYear = existing?.lastSentYear,
            lunar = lunar && hasYear, sendVia = existing?.sendVia,
        )
        val id = Repo.saveEvent(e, editingId)
        val app = context.applicationContext
        thread { Reminders.syncAll(app) }
        onBack()
        if (editingId == null) navigate("event/$id")
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(if (editingId != null) "Edit event" else "New event", onBack, close = true) {
            if (editingId == null) PostButton("From contacts", { navigate("import") }, look = Look.OUTLINE, height = 40.dp, fontSize = 14)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What are we remembering?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(EventType.BIRTHDAY, "Birthday", Ic.CAKE), Triple(EventType.ANNIVERSARY, "Anniversary", Ic.RINGS), Triple(EventType.OTHER, "Other", Ic.HOUSE))
                        .forEach { (et, label, icon) ->
                            val on = type == et
                            Column(
                                Modifier.weight(1f).height(72.dp)
                                    .then(if (on) Modifier.background(c.paper).dashedBorder(c.stamp, 2.dp) else Modifier.background(c.paperAlt).border(1.5.dp, c.shadow))
                                    .clickable(role = Role.RadioButton) { type = et }.semantics { selected = on },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            ) {
                                LineIcon(icon, if (on) c.stamp else c.ink, size = 20)
                                Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp, color = if (on) c.stamp else c.ink)
                            }
                        }
                }
                if (type == EventType.OTHER) Text("Other asks for an event name too, like Housewarming.", fontSize = 13.sp, color = c.muted)
            }

            PaperCard(gap = 14.dp) {
                UnderlineField(
                    if (type == EventType.ANNIVERSARY) "To the couple" else "To", name, { name = it }, handwritten = true,
                    placeholder = if (type == EventType.ANNIVERSARY) "Priya & Arjun" else "Ravi Kumar",
                )
                ContactSuggestions(
                    name = name, phone = phone, accessVersion = contactsVersion,
                    onRequestAccess = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                    onPick = { m, number ->
                        number?.let { phone = it.value }
                        if (m.email != null && email.isBlank()) email = m.email.value
                    },
                )
                if (type == EventType.OTHER) UnderlineField("Event name", title, { title = it }, placeholder = "Housewarming, Graduation…", strong = true)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    UnderlinePicker(
                        "Date", if (month != null && day != null) "$day ${MONTHS[month!! - 1]}" else "Choose a date",
                        { showPicker = true }, Modifier.weight(1.4f), placeholder = month == null,
                    )
                    if (!noYear) UnderlineField("Year", yearText, { yearText = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), placeholder = "1990", keyboardType = KeyboardType.Number, strong = true)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { noYear = !noYear },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(noYear, null, colors = CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                    Text("I don’t know the year", fontSize = 15.sp, color = c.ink)
                }
                Column(
                    Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 1.5.dp).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Follow the Hindu lunar date", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.festivalInk)
                            Text(if (hasYear) "Repeats on the same tithi each year." else "Repeats on the same tithi each year. Needs the year.", fontSize = 13.sp, color = c.festivalInk)
                        }
                        PaperSwitch(lunar && hasYear, { lunar = it }, enabled = hasYear)
                    }
                    if (lunar && hasYear && month != null && day != null) {
                        val original = runCatching { LocalDate.of(year!!, month!!, day!!) }.getOrNull()
                        if (original != null) {
                            val probe = MemoraEvent(0, type, name, null, month!!, day!!, year, null, null, "", emptyList(), null, null, null, lunar = true)
                            Handwriting("${Lunar.describe(Lunar.dayOf(original))}, next on ${shortDate(nextDate(probe))}", size = 22, color = c.festivalInk)
                        }
                    }
                }
            }

            PaperCard(gap = 14.dp) {
                UnderlineField("Phone, optional", phone, { phone = it }, placeholder = "+91 98450 12345", keyboardType = KeyboardType.Phone)
                ContactSuggestions(
                    name = name, phone = phone, accessVersion = contactsVersion, byPhone = true,
                    onRequestAccess = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                    onPick = { m, number ->
                        number?.let { phone = it.value }
                        if (name.isBlank()) name = m.name
                        if (m.email != null && email.isBlank()) email = m.email.value
                    },
                )
                UnderlineField("Email, optional", email, { email = it }, placeholder = "name@example.com", keyboardType = KeyboardType.Email)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your message", fontFamily = Fraunces, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
                    BasicTextField(
                        value = shown,
                        onValueChange = { message = it; messageEdited = true },
                        textStyle = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, color = c.ink),
                        cursorBrush = SolidColor(c.stamp), minLines = 3,
                        modifier = Modifier.fillMaxWidth().ruled(c.rule, 30.dp).semantics { contentDescription = "Your message" },
                    )
                    if (hasPlaceholders(shown.text)) Text("Preview: ${preview(shown.text)}", fontSize = 13.sp, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PLACEHOLDER_CHIPS.forEach { token ->
                            Box(
                                Modifier.heightIn(min = 36.dp).dashedBorder(c.ink, 1.dp).clickable(role = Role.Button) {
                                    val v = shown
                                    val s = v.selection.start.coerceIn(0, v.text.length)
                                    val e = v.selection.end.coerceIn(s, v.text.length)
                                    val text = v.text.substring(0, s) + token + v.text.substring(e)
                                    message = TextFieldValue(text, TextRange(s + token.length)); messageEdited = true
                                }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(token, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink) }
                        }
                        Box(Modifier.heightIn(min = 36.dp).clickable(role = Role.Button) { showWishes = true }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text("Ready wishes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.stamp, style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remind me early, pick up to two", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OFFSET_OPTIONS.forEach { (days, label) ->
                        val on = days in offsets
                        ToggleChip(label, on, {
                            offsets = when {
                                on && offsets.size > 1 -> offsets - days
                                !on && offsets.size < 2 -> (offsets + days).sortedDescending()
                                else -> offsets
                            }
                        }, enabled = on || offsets.size < 2, height = 42.dp)
                    }
                }
                Text("You’ll always get a reminder on the day, at ${clock(Repo.reminderHour(), Repo.reminderMinute())}.", fontSize = 13.sp, color = c.muted)
            }
        }
        BottomBar { PostButton("Save event", ::save, Modifier.fillMaxWidth(), look = Look.STAMP, height = 52.dp, fontSize = 16) }
    }

    if (showWishes) ReadyWishesSheet(
        type, hasYear, onPick = { message = TextFieldValue(it, TextRange(it.length)); messageEdited = true },
        onDismiss = { showWishes = false }, preview = preview,
    )

    if (showPicker) {
        val initial = (if (month != null && day != null) LocalDate.of(year ?: 2000, month!!, day!!) else today())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val limitToPast = type != EventType.OTHER
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !limitToPast || Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        month = d.monthValue; day = d.dayOfMonth
                        if (!noYear) yearText = d.year.toString()
                    }
                    showPicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold, color = c.ink) }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Cancel", color = c.muted) } },
        ) { DatePicker(state) }
    }
}
