package com.smnexstudio.memora.ui.screens

import com.smnexstudio.memora.data.Prefs
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.smnexstudio.memora.ui.ScriptFont
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.stampText
import com.smnexstudio.memora.logic.shortDate
import com.smnexstudio.memora.logic.milestone
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Channel
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.CardContent
import com.smnexstudio.memora.logic.CardDesign
import com.smnexstudio.memora.logic.CardShape
import com.smnexstudio.memora.logic.Cards
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.logic.Tone
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.fillMessage
import com.smnexstudio.memora.logic.nextDate
import com.smnexstudio.memora.logic.occurrenceDatedIn
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.logic.yearsOn
import com.smnexstudio.memora.platform.Send
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.IconTap
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.LinedField
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.ReadyWishesSheet
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.UnderlineField
import com.smnexstudio.memora.ui.WorkSans
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.widget.NextEventsWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val WHATSAPP_GREEN = Color(0xFF1E7A4C)

private fun firstName(e: MemoraEvent) = e.name.trim().split(Regex("\\s+|&")).firstOrNull()?.ifBlank { null } ?: e.name

/** Review and send: first how (WhatsApp, email, SMS), then as a postcard picture or plain text. */
@Composable
fun ApproveScreen(id: Long, initialChannel: String?, year: Int?, belated: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val e = remember(id) { Repo.getEvent(id) }
    if (e == null) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "This event no longer exists.", Toast.LENGTH_SHORT).show()
            onBack()
        }
        return
    }
    val date = if (year != null) occurrenceDatedIn(e, year) else nextDate(e)
    // A channel from the notification button or the person's saved choice skips the question.
    var channel by rememberSaveable { mutableStateOf(Channel.from(initialChannel)?.id ?: e.sendVia) }

    val ch = Channel.from(channel)
    if (ch == null) ChooseHow(e, date, onPick = { channel = it.id }, onClose = onBack)
    else Composer(e, ch, date, belated, onChange = { channel = null }, onBack = onBack)
}

// ---- 1 · How should it reach them? ----

/** An envelope addressed to the person, then the ways to send it as stamp tiles. */
@Composable
private fun ChooseHow(e: MemoraEvent, date: LocalDate, onPick: (Channel) -> Unit, onClose: () -> Unit) {
    val t = theme
    val c = t.c
    val first = firstName(e)
    val lastUsed = remember(e.id) { Repo.listHistory(e.id).firstOrNull { it.channel != "skipped" }?.let { h -> Channel.from(h.channel)?.let { it to h.year } } }
    var always by rememberSaveable { mutableStateOf(e.sendVia != null) }
    var picked by rememberSaveable {
        mutableStateOf(
            (Channel.from(e.sendVia) ?: lastUsed?.first ?: if (e.phone == null && e.email != null) Channel.EMAIL else Channel.WHATSAPP).id,
        )
    }
    val choice = Channel.from(picked) ?: Channel.WHATSAPP
    val n = daysUntil(date)
    val whenText = when {
        n == 0 -> "today"
        n == 1 -> "tomorrow"
        n < 0 -> if (n == -1) "yesterday" else "${-n} days ago"
        else -> "on ${shortDate(date)}"
    }
    val detail = "${eventLabel(e)} $whenText" + (milestone(e, date)?.let { ", $it" } ?: "")

    Column(Modifier.fillMaxSize().background(c.ground)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.CLOSE, "Close", onClose)
            Text(
                "Post it to $first", Modifier.padding(start = 4.dp),
                fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.ink,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Envelope(e, date, detail, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))

            Text(
                "Send it by", Modifier.padding(start = 4.dp, top = 28.dp, bottom = 12.dp),
                fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(Channel.WHATSAPP, Ic.CHAT, "Postcard or text"),
                    Triple(Channel.EMAIL, Ic.MAIL, "Postcard or text"),
                    Triple(Channel.SMS, Ic.SMS, "Text only"),
                ).forEach { (ch, icon, sub) ->
                    val (bg, fg) = when (ch) {
                        Channel.WHATSAPP -> WHATSAPP_GREEN to Color.White
                        Channel.EMAIL -> c.stamp to c.onStamp
                        Channel.SMS -> c.ink to c.onInk
                    }
                    ChannelTile(
                        ch.label, sub, icon, bg, fg, selected = choice == ch,
                        usedIn = lastUsed?.takeIf { it.first == ch }?.second,
                        modifier = Modifier.weight(1f),
                    ) { picked = ch.id }
                }
            }
            Text(
                when (choice) {
                    Channel.WHATSAPP -> e.phone?.let { "Goes to $it on WhatsApp" } ?: "No number saved: you’ll pick the chat in WhatsApp"
                    Channel.EMAIL -> e.email?.let { "Goes to $it by email" } ?: "No email saved: you’ll type the address next"
                    Channel.SMS -> e.phone?.let { "Goes to $it by SMS" } ?: "No number saved: you’ll type it next"
                },
                Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 16.dp), fontSize = 14.sp, color = c.muted,
            )
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { always = !always },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(always, null, colors = CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                Text("Always use this for $first", fontSize = 14.sp, color = c.ink)
            }
            PostButton("Continue", {
                Repo.setSendVia(e.id, if (always) choice.id else null)
                onPick(choice)
            }, Modifier.fillMaxWidth(), look = Look.STAMP, height = 54.dp, fontSize = 16)
        }
    }
}

/** An airmail envelope: striped edge, the person's stamp, a postmark and the address. */
@Composable
private fun Envelope(e: MemoraEvent, date: LocalDate, detail: String, modifier: Modifier = Modifier) {
    val t = theme
    val c = t.c
    val red = c.stamp
    val navy = c.ink
    val paperColor = c.paper
    Box(
        modifier.fillMaxWidth().height(200.dp).rotate(if (theme.board) 0f else -1.5f)
            .drawBehind {
                drawRect(c.shadow, topLeft = Offset(0f, 2.dp.toPx()), size = size)
                drawRect(paperColor)
                // Airmail stripes along the edge, under the inner paper.
                val band = 26.dp.toPx()
                var x = -size.height
                var i = 0
                clipRect {
                    while (x < size.width + size.height) {
                        val color = if (i % 2 == 0) red else navy
                        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 9.dp.toPx())
                        x += band; i++
                    }
                }
            }
            .padding(10.dp)
            .background(paperColor)
            .padding(16.dp),
    ) {
        Stamp(stampText(e, date), t.type(e.type), Modifier.align(Alignment.TopEnd), filled = true, width = 54.dp, height = 64.dp, fontSize = 22)
        val pm = today()
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 38.dp, end = 38.dp).size(56.dp).rotate(if (theme.board) 0f else -14f)
                .border(2.dp, navy.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${pm.dayOfMonth} ${MONTHS[pm.monthValue - 1].take(3).uppercase()}\n${pm.year}",
                fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = navy.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("To", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = c.muted)
            Handwriting(e.name, size = 32, bold = true, lineHeight = 34)
            Box(Modifier.fillMaxWidth().height(1.dp).background(navy))
            Text(detail, fontSize = 13.sp, color = c.muted, maxLines = 1)
        }
    }
}

/** One way to send, as a stamp-like tile. */
@Composable
private fun ChannelTile(
    title: String,
    sub: String,
    icon: Ic,
    bg: Color,
    fg: Color,
    selected: Boolean,
    usedIn: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = theme.c
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().height(132.dp)
                .then(if (selected) Modifier.background(c.stampSoft).dashedBorder(c.stamp, 2.5.dp) else Modifier.paper(c.paper, c.shadow).dashedBorder(c.faint, 1.5.dp))
                .clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { LineIcon(icon, fg, size = 22) }
            Text(title, fontWeight = FontWeight.Bold, color = c.ink)
            Text(sub, fontSize = 11.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (usedIn != null) RubberStamp(
            if (usedIn == today().year) "This year" else if (usedIn == today().year - 1) "Last year" else "$usedIn",
            c.ok, Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).background(c.paper), tilt = -4f, fontSize = 10,
        )
    }
}

// ---- 2 · Write it and send ----

@Composable
private fun Composer(e: MemoraEvent, channel: Channel, date: LocalDate, belated: Boolean, onChange: () -> Unit, onBack: () -> Unit) {
    val t = theme
    val c = t.c
    val context = LocalContext.current
    val first = firstName(e)
    val signature = remember { Prefs.signature.get() }
    val canPostcard = channel != Channel.SMS
    var postcard by rememberSaveable { mutableStateOf(canPostcard && Prefs.sendMode(channel).get() == "postcard") }

    val mine = fillMessage(if (belated) Templates.belated(e.type) else e.message, e, date)
    var tone by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf(mine) }
    var showWishes by remember { mutableStateOf(false) }

    // Each kind of event offers its own designs and remembers the last one used.
    val designs = remember(e.type) { CardDesign.forType(e.type) }
    var design by rememberSaveable {
        mutableStateOf(
            (CardDesign.entries.firstOrNull { it.id == Prefs.cardDesign(e.type).get() }?.takeIf { it in designs } ?: designs.first()).id,
        )
    }
    var shape by rememberSaveable { mutableStateOf(if (channel == Channel.EMAIL) CardShape.POSTCARD.id else CardShape.from(Prefs.cardShape.get()).id) }
    var caption by rememberSaveable { mutableStateOf(captionFor(e, first, belated)) }
    // The greeting on the card, editable: "Happy Birthday" and "dear Ananya".
    val defaultHeadline = headlineFor(e, belated)
    val defaultToLine = if (e.type == EventType.ANNIVERSARY) e.name else "dear $first"
    var headline by rememberSaveable { mutableStateOf(defaultHeadline) }
    var toLine by rememberSaveable { mutableStateOf(defaultToLine) }
    var saveCopy by rememberSaveable { mutableStateOf(Prefs.saveCards.get()) }
    var showAge by rememberSaveable { mutableStateOf(true) }
    var shortPublic by rememberSaveable { mutableStateOf(true) }
    var emailTo by rememberSaveable { mutableStateOf(e.email ?: "") }
    var subject by rememberSaveable {
        mutableStateOf(if (belated) "Belated ${eventLabel(e).lowercase()} wishes, $first" else "A ${eventLabel(e).lowercase()} card for you, $first")
    }
    var phoneTo by rememberSaveable { mutableStateOf(e.phone ?: "") }
    val cardShape = CardShape.from(shape)
    val isStatus = channel == Channel.WHATSAPP && postcard && cardShape == CardShape.STATUS

    fun choose(t2: Tone?) {
        if (t2 == null) {
            tone = null; message = mine; return
        }
        val list = Templates.templatesFor(e.type, t2, e.year != null)
        // Tapping the same tone again shows its next wish.
        pick = if (tone == t2.id) (pick + 1) % list.size else 0
        tone = t2.id
        message = fillMessage(list[pick], e, date)
    }

    val content = CardContent(
        headline = headline.trim(),
        toLine = toLine.trim(),
        number = yearsOn(e, date)?.toString()?.takeIf { showAge || !isStatus },
        message = if (isStatus && shortPublic) publicLine(e, first) else message,
        signature = signature.ifBlank { null }?.takeIf { !isStatus },
        name = e.name,
        address = if (channel == Channel.EMAIL) emailTo.ifBlank { null } else phoneTo.ifBlank { null },
        postmark = today().let { "${it.dayOfMonth} ${MONTHS[it.monthValue - 1].take(3).uppercase()}\n${it.year}" },
        stampColor = stampArgb(e),
    )

    // The picture, redrawn shortly after anything on it changes.
    var card by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(postcard, design, shape, content) {
        if (!postcard) return@LaunchedEffect
        delay(250)
        card = withContext(Dispatchers.Default) { Cards.render(context, CardDesign.from(design), CardShape.from(shape), content) }
    }

    fun done(msg: String) {
        Repo.markSent(e.id, date.year, channel.id, msg)
        NextEventsWidget.updateAll(context)
        Prefs.sendMode(channel).set(if (postcard) "postcard" else "text")
        if (postcard) {
            Prefs.cardDesign(e.type).set(design)
            if (channel == Channel.WHATSAPP) Prefs.cardShape.set(shape)
            Prefs.saveCards.set(saveCopy)
        }
        onBack()
    }

    fun send() {
        val text = message.trim()
        if (text.isEmpty() && !isStatus) return Toast.makeText(context, "The message can’t be empty.", Toast.LENGTH_SHORT).show()
        try {
            if (postcard) {
                val bmp = card ?: return Toast.makeText(context, "The card is still being drawn.", Toast.LENGTH_SHORT).show()
                if (saveCopy) Send.saveToGallery(context, bmp, "memora-${first.lowercase()}-${today()}")
                val uri = Send.cardUri(context, bmp)
                when (channel) {
                    Channel.WHATSAPP -> Send.whatsAppImage(context, phoneTo.takeIf { !isStatus && it.isNotBlank() }, uri, if (isStatus) "" else caption.trim())
                    Channel.EMAIL -> {
                        if (emailTo.isBlank()) return Toast.makeText(context, "Add an email address.", Toast.LENGTH_SHORT).show()
                        Send.emailImage(context, emailTo.trim(), subject.trim(), text + (signature.ifBlank { null }?.let { "\n\n$it" } ?: ""), uri)
                    }
                    Channel.SMS -> Unit
                }
            } else when (channel) {
                Channel.WHATSAPP -> {
                    if (phoneTo.isBlank()) return Toast.makeText(context, "Add a phone number to send on WhatsApp.", Toast.LENGTH_SHORT).show()
                    Send.whatsApp(context, phoneTo, text)
                }
                Channel.SMS -> Send.sms(context, phoneTo, text)
                Channel.EMAIL -> {
                    if (emailTo.isBlank()) return Toast.makeText(context, "Add an email address.", Toast.LENGTH_SHORT).show()
                    Send.email(context, emailTo.trim(), subject.trim(), text)
                }
            }
            done(if (isStatus) "Status: ${content.message}" else text)
        } catch (err: Exception) {
            Toast.makeText(context, err.message ?: "Couldn’t open the app", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Header
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.BACK, "Back", onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (isStatus) "Status picture" else if (postcard) "A card for $first" else "A wish for $first",
                    fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (channel) { Channel.WHATSAPP -> "Sending on WhatsApp"; Channel.EMAIL -> "Sending by email"; Channel.SMS -> "Sending by SMS" } + " · ",
                        fontSize = 13.sp, color = c.muted,
                    )
                    Text(
                        "Change", Modifier.clickable(role = Role.Button) { Repo.setSendVia(e.id, null); onChange() },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink, style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (canPostcard) Segmented(
                left = (if (channel == Channel.EMAIL) "Email" else "WhatsApp") + " postcard", leftIcon = Ic.POSTCARD,
                right = (if (channel == Channel.EMAIL) "Email" else "WhatsApp") + " text only", rightIcon = Ic.LINES,
                leftOn = postcard, onChange = { postcard = it },
            )

            if (postcard) {
                if (channel == Channel.WHATSAPP) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Picture shape", italic = false, size = 17)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardShape.entries.forEach { s -> ShapeTile(s, shape == s.id, Modifier.weight(1f)) { shape = s.id } }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Design", italic = false, size = 17)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        designs.forEach { d -> DesignSwatch(d, design == d.id, yearsOn(e, date)) { design = d.id } }
                    }
                }
                // The picture itself
                Box(
                    // Narrow the tall shapes first, then size the box to the picture, so there's no empty band.
                    Modifier.fillMaxWidth()
                        .let { if (cardShape != CardShape.SQUARE && CardDesign.from(design).letter || cardShape == CardShape.STATUS) it.padding(horizontal = 40.dp) else it }
                        .aspectRatio(Cards.size(CardDesign.from(design), cardShape).let { (w, h) -> w.toFloat() / h })
                        .rotate(if (cardShape == CardShape.SQUARE && !theme.board) -0.8f else 0f).paper(c.paper, c.shadow),
                    contentAlignment = Alignment.Center,
                ) {
                    card?.let { Image(it.asImageBitmap(), "Card preview", Modifier.fillMaxSize()) } ?: CircularProgressIndicator(color = c.stamp)
                }
            } else TextPreview(channel, if (channel == Channel.EMAIL) emailTo else phoneTo, subject, message)

            // The greeting on the card
            if (postcard) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Greeting", Modifier.weight(1f), italic = false, size = 17)
                    if (headline != defaultHeadline || toLine != defaultToLine) LinkText("Reset", { headline = defaultHeadline; toLine = defaultToLine })
                }
                PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), gap = 12.dp) {
                    UnderlineField("Heading", headline, { headline = it.take(40) }, placeholder = defaultHeadline, strong = true)
                    UnderlineField("Under it", toLine, { toLine = it.take(40) }, placeholder = defaultToLine)
                }
            }

            // Message
            if (!isStatus || !shortPublic) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(
                        if (!postcard) "Message" else if (CardDesign.from(design).letter) "Message on the card" else "Message on the back",
                        Modifier.weight(1f), italic = false, size = 17,
                    )
                    LinkText("More wishes", { showWishes = true })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("Mine", tone == null, { choose(null) })
                    Tone.entries.forEach { t2 -> ToggleChip(t2.label, tone == t2.id, { choose(t2) }) }
                }
                if (postcard) PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    LinedField(message, { message = it }, label = "Message", fontSize = 24, lineHeight = 30, minLines = 3)
                } else TextArea(message, { message = it }, "Message")
                if (postcard && signature.isBlank()) Text("Add your name in Settings to sign your cards.", fontSize = 13.sp, color = c.muted)
            }

            if (isStatus) PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), gap = 0.dp) {
                com.smnexstudio.memora.ui.SwitchRow(
                    if (e.type == EventType.BIRTHDAY) "Show the age" else "Show the years", "Everyone who sees your Status will see it", showAge, { showAge = it },
                )
                com.smnexstudio.memora.ui.SwitchRow("Shorter public message", "Your private wish stays in the chat card", shortPublic, { shortPublic = it })
            }

            // Who it goes to
            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), gap = 12.dp) {
                when (channel) {
                    Channel.EMAIL -> {
                        SectionTitle("Email details", italic = false, size = 17)
                        UnderlineField("To", emailTo, { emailTo = it }, placeholder = "name@example.com", keyboardType = KeyboardType.Email)
                        UnderlineField("Subject", subject, { subject = it })
                        if (postcard) Text("The card goes as a picture attached to the email, with your wish typed below it.", fontSize = 13.sp, color = c.muted)
                    }
                    Channel.WHATSAPP -> {
                        if (!isStatus) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                UnderlineField("To", phoneTo, { phoneTo = it }, Modifier.weight(1f), placeholder = "Pick the chat in WhatsApp", keyboardType = KeyboardType.Phone)
                                val installed = remember { Send.whatsAppPackage(context) != null }
                                Text(
                                    if (installed) "WhatsApp ready" else "WhatsApp not installed", Modifier.padding(start = 8.dp, bottom = 12.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (installed) c.ok else c.stamp,
                                )
                            }
                            if (postcard) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Caption under the picture", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
                                TextArea(caption, { caption = it }, "Caption", minLines = 2)
                            }
                        } else Text("WhatsApp will ask where to share it. Pick My status at the top.", fontSize = 14.sp, color = c.muted)
                        if (postcard) Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { saveCopy = !saveCopy },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(saveCopy, null, colors = CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                            Column {
                                Text("Save the picture to my gallery", fontSize = 14.sp, color = c.ink)
                                Text("Apps with photo access can see pictures in your gallery.", fontSize = 12.sp, color = c.muted)
                            }
                        }
                    }
                    Channel.SMS -> UnderlineField("To", phoneTo, { phoneTo = it }, placeholder = "+91 98450 12345", keyboardType = KeyboardType.Phone)
                }
            }

            if (daysUntil(date) == 0 && !belated) LinkText("Remind me in ${Reminders.SNOOZE_HOURS} hours instead", {
                Reminders.snooze(context.applicationContext, e.id)
                Toast.makeText(context, "We’ll remind you again in ${Reminders.SNOOZE_HOURS} hours", Toast.LENGTH_SHORT).show()
                onBack()
            })
        }

        // Send
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    isStatus -> "Post to WhatsApp Status"
                    channel == Channel.WHATSAPP -> "Open WhatsApp to send"
                    channel == Channel.EMAIL && postcard -> "Send card by email"
                    channel == Channel.EMAIL -> "Open email app to send"
                    else -> "Open Messages to send"
                }
                val greenLook = channel == Channel.WHATSAPP
                Box(Modifier.weight(1f)) {
                    if (greenLook) Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(4.dp)).background(if (isStatus) Color(0xFFF2A93B) else WHATSAPP_GREEN)
                            .clickable(role = Role.Button, onClick = ::send),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        val fg = if (isStatus) Color(0xFF3A2600) else Color.White
                        LineIcon(Ic.CHAT, fg, size = 20)
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = fg)
                    } else PostButton(label, ::send, Modifier.fillMaxWidth(), look = Look.STAMP, height = 54.dp, fontSize = 16)
                }
                PostButton(if (belated) "Don’t send" else "Skip", {
                    Repo.markSent(e.id, date.year, "skipped", null)
                    NextEventsWidget.updateAll(context)
                    onBack()
                }, look = Look.GHOST, height = 54.dp)
            }
            Text(
                when {
                    isStatus -> "WhatsApp opens with the picture. Choose My status, then post."
                    channel == Channel.WHATSAPP && postcard -> if (phoneTo.isBlank()) "WhatsApp opens with the card. Pick the chat, then tap send." else "WhatsApp opens their chat with the card ready. You tap send."
                    channel == Channel.WHATSAPP -> "Their chat opens with the message typed in. You tap send."
                    channel == Channel.EMAIL -> "Your email app opens with everything filled in. You tap send."
                    else -> "Messages opens with the text typed in. You tap send."
                },
                Modifier.fillMaxWidth(), fontSize = 13.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showWishes) ReadyWishesSheet(
        e.type, e.year != null, onPick = { message = fillMessage(it, e, date); tone = null },
        onDismiss = { showWishes = false }, preview = { fillMessage(it, e, date) },
    )
}

private fun stampArgb(e: MemoraEvent) = when (e.type) {
    EventType.BIRTHDAY -> 0xFFB23A2E.toInt()
    EventType.ANNIVERSARY -> 0xFF5B4E9A.toInt()
    EventType.OTHER -> 0xFF2E7060.toInt()
}

private fun headlineFor(e: MemoraEvent, belated: Boolean) = when (e.type) {
    EventType.BIRTHDAY -> if (belated) "Belated Birthday" else "Happy Birthday"
    EventType.ANNIVERSARY -> if (belated) "Belated Anniversary" else "Happy Anniversary"
    EventType.OTHER -> e.title?.ifBlank { null } ?: "Best Wishes"
}

private fun captionFor(e: MemoraEvent, first: String, belated: Boolean) = when (e.type) {
    EventType.BIRTHDAY -> if (belated) "Happy belated birthday $first! Made you a little card" else "Happy birthday $first! Made you a little card"
    EventType.ANNIVERSARY -> "Happy anniversary! Made you a little card"
    EventType.OTHER -> "Made you a little card, $first"
}

/** What strangers on your Status see instead of the private wish. */
private fun publicLine(e: MemoraEvent, first: String) = when (e.type) {
    EventType.BIRTHDAY -> "Wishing you the happiest year, $first!"
    EventType.ANNIVERSARY -> "Here’s to many more happy years together!"
    EventType.OTHER -> "Congratulations and all the best, $first!"
}

// ---- Pieces ----

@Composable
private fun Segmented(left: String, leftIcon: Ic, right: String, rightIcon: Ic, leftOn: Boolean, onChange: (Boolean) -> Unit) {
    val c = theme.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.paperAlt).border(1.5.dp, c.ink, RoundedCornerShape(4.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(Triple(left, leftIcon, true), Triple(right, rightIcon, false)).forEach { (label, icon, isLeft) ->
            val on = leftOn == isLeft
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(3.dp)).background(if (on) c.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onChange(isLeft) }.semantics { selected = on },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(icon, if (on) c.onInk else c.ink, size = 18)
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) c.onInk else c.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShapeTile(s: CardShape, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = theme.c
    Column(
        modifier.height(70.dp).clip(RoundedCornerShape(4.dp))
            .then(if (on) Modifier.background(c.stampSoft).dashedBorder(c.stamp, 2.dp, radius = 4.dp) else Modifier.border(1.5.dp, c.shadow, RoundedCornerShape(4.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val color = if (on) c.stampDeep else c.ink
        val (w, h) = when (s) { CardShape.SQUARE -> 24 to 24; CardShape.POSTCARD -> 32 to 21; CardShape.STATUS -> 16 to 28 }
        Box(Modifier.size(w.dp, h.dp).border(2.dp, color))
        Text(s.label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}

/** A small sample of a card design. */
@Composable
private fun DesignSwatch(d: CardDesign, on: Boolean, number: Int?, onClick: () -> Unit) {
    val c = theme.c
    Box(
        Modifier.size(76.dp, 52.dp)
            .then(if (on) Modifier.drawBehind {
                val o = 4.dp.toPx()
                drawRect(c.stamp, Offset(-o, -o), androidx.compose.ui.geometry.Size(size.width + 2 * o, size.height + 2 * o), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            } else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on; contentDescription = d.label },
    ) {
        // The pattern stays inside its box; only the selection outline sits outside it.
        Box(Modifier.fillMaxSize().clipToBounds()) { Swatch(d, number) }
    }
}

@Composable
private fun BoxScope.Swatch(d: CardDesign, number: Int?) {
    val navy = Color(0xFF23395B)
    val red = Color(0xFFB23A2E)
    when (d) {
        CardDesign.MARIGOLD -> Box(Modifier.fillMaxSize().background(Color(0xFFF2A93B)).drawBehind {
            listOf(Offset(4f, 4f) to 28f, Offset(size.width - 4f, size.height - 4f) to 24f).forEach { (o, r) ->
                drawCircle(Color(0xFFF6C453), r * density / 2, o)
                drawCircle(Color(0xFFE07A1F), r * density / 3.2f, o)
            }
        })
        CardDesign.AIRMAIL -> Box(Modifier.fillMaxSize().background(Color(0xFFFFFDF8)).drawBehind {
            var x = -size.height
            var i = 0
            while (x < size.width) {
                drawLine(if (i % 2 == 0) red else navy, Offset(x, size.height), Offset(x + size.height, 0f), 5.dp.toPx())
                x += 9.dp.toPx(); i++
            }
            drawRect(Color(0xFFFFFDF8), Offset(6.dp.toPx(), 6.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()))
        })
        CardDesign.RANGOLI -> Box(Modifier.fillMaxSize().background(Color(0xFF7A1E2C)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).rotate(45f).border(2.dp, Color(0xFFE8B23A)), contentAlignment = Alignment.Center) { Box(Modifier.size(7.dp).background(Color(0xFFE8B23A))) }
        }
        CardDesign.BIG_NUMBER -> Box(Modifier.fillMaxSize().background(navy), contentAlignment = Alignment.Center) {
            Text(number?.toString() ?: "M", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFFFFFDF8))
        }
        CardDesign.CONFETTI -> Box(Modifier.fillMaxSize().background(Color(0xFFFFFDF8)).drawBehind {
            val colors = listOf(red, navy, Color(0xFFF2A93B), Color(0xFF2E7060), Color(0xFF5B4E9A))
            val rnd = java.util.Random(3)
            repeat(16) { i ->
                drawCircle(colors[i % colors.size], 2.5.dp.toPx(), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
            }
        })
        // Letter designs: their ground, a touch of their ornament and the script greeting.
        CardDesign.LOVE_LETTER -> LetterSwatch(Color(0xFFC99A98), Color(0xFF2E2828), Color(0xFFB3263A)) {
            drawRect(Color(0xFFFBF7F0), Offset(size.width * 0.12f, size.height * 0.08f), androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.84f))
        }
        CardDesign.BLUSH -> LetterSwatch(Color(0xFFFCF6F2), Color(0xFF3F2A26), Color(0xFFEBA7AE)) {
            drawLine(Color(0xFFF0BFC3), Offset(size.width * 0.15f, size.height * 0.88f), Offset(size.width * 0.85f, size.height * 0.88f), 3.dp.toPx())
        }
        CardDesign.RIBBONS -> LetterSwatch(Color(0xFFFDFEFF), Color(0xFF1E2A46), Color(0xFF8EAEE6)) {
            drawRect(Color(0xFF8EAEE6), Offset(4.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
        CardDesign.PARTY -> LetterSwatch(Color(0xFFF7F2EA), Color(0xFF2B2B2B), Color(0xFFE88AA6)) {
            val flags = listOf(Color(0xFFFBE08A), Color(0xFFF6B8CB), Color(0xFFA9D2F2), Color(0xFFCDB8F0), Color(0xFFBFE2B3))
            val w = size.width / flags.size
            flags.forEachIndexed { i, col ->
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(i * w + 2, 0f); lineTo((i + 1) * w - 2, 0f); lineTo(i * w + w / 2, 10.dp.toPx()); close() }, col)
            }
        }
        CardDesign.GOLDEN -> LetterSwatch(Color(0xFFFBF6EC), Color(0xFF3A2E22), Color(0xFFC29B4A)) {
            listOf(-1f, 1f).forEach { d -> drawCircle(Color(0xFFC29B4A), 7.dp.toPx(), Offset(size.width / 2 + d * 4.dp.toPx(), 11.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
        }
        CardDesign.PLAIN -> QuietSwatch(Color(0xFFFBFAF7), Color(0xFF2F2F2F), Color(0xFFB5B0A6))
        CardDesign.CLASSIC -> QuietSwatch(Color(0xFF1F2C44), Color(0xFFF4EBD8), Color(0xFFCFAE68))
        CardDesign.MEMORY -> QuietSwatch(Color(0xFFF2F0EB), Color(0xFF3B4048), Color(0xFFC9C5BC))
        CardDesign.WILDFLOWER -> LetterSwatch(Color(0xFFFBF3EF), Color(0xFF6E5444), Color(0xFFCF8A88)) {
            listOf(0.12f, 0.2f, 0.8f, 0.88f).forEach { x -> drawCircle(Color(0xFFCF8A88), 3.dp.toPx(), Offset(size.width * x, size.height * 0.78f)) }
        }
    }
}

/** A letter design's sample: its ground, an ornament, and "Aa" in its script. */
@Composable
private fun LetterSwatch(ground: Color, ink: Color, accent: Color, ornament: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(ground).drawBehind(ornament), contentAlignment = Alignment.Center) {
        Text("Aa", fontFamily = ScriptFont, fontSize = 26.sp, color = ink)
        Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(accent))
    }
}

/** A quiet design's sample: a thin frame and "Aa" in serif. */
@Composable
private fun QuietSwatch(ground: Color, ink: Color, rule: Color) {
    Box(
        Modifier.fillMaxSize().background(ground).drawBehind {
            val o = 5.dp.toPx()
            drawRect(rule, Offset(o, o), androidx.compose.ui.geometry.Size(size.width - 2 * o, size.height - 2 * o), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) { Text("Aa", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = ink) }
}

/** How the plain text will look once it arrives. */
@Composable
private fun TextPreview(channel: Channel, to: String, subject: String, message: String) {
    val c = theme.c
    if (channel == Channel.EMAIL) Column(
        Modifier.fillMaxWidth().paper(Color.White, c.shadow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("To  ${to.ifBlank { "…" }}", fontSize = 13.sp, color = Color(0xFF5F6368), fontFamily = FontFamily.SansSerif)
            Text(subject, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F1F1F), fontFamily = FontFamily.SansSerif)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E5E5)))
        Text(message, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF1F1F1F), fontFamily = FontFamily.SansSerif)
        Text("Plain text only. No card picture is attached.", fontSize = 12.sp, color = Color(0xFF5F6368))
    } else Column(
        Modifier.fillMaxWidth().paper(if (channel == Channel.WHATSAPP) Color(0xFFEEF0EC) else Color(0xFFF1F3F4), c.shadow).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Preview", Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 12.sp, color = Color(0xFF56605A),
        )
        Column(
            Modifier.align(Alignment.End).widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(if (channel == Channel.WHATSAPP) Color(0xFFD7EFD9) else Color(0xFFD3E3FD)).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        ) {
            Text(message.ifBlank { "…" }, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF1F2321), fontFamily = FontFamily.SansSerif)
            Text("now", Modifier.align(Alignment.End), fontSize = 11.sp, color = Color(0xFF56605A))
        }
    }
}

/** Plain outlined multi-line text box. */
@Composable
private fun TextArea(value: String, onChange: (String) -> Unit, label: String, minLines: Int = 4) {
    val c = theme.c
    BasicTextField(
        value, onChange, minLines = minLines,
        textStyle = TextStyle(fontFamily = WorkSans, fontSize = 15.sp, lineHeight = 22.sp, color = c.ink), cursorBrush = SolidColor(c.stamp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.paper).border(1.5.dp, c.faint, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp).semantics { contentDescription = label },
    )
}
