package com.smnexstudio.memora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Drawing helpers ----

/** A dashed outline, like a stamp's perforation or a cut-here line. The board draws it as a solid rule with rounded corners. */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.5.dp, dash: Dp = 5.dp, gap: Dp = 3.dp, radius: Dp = 0.dp) = composed {
    val board = LocalMemora.current.board
    drawBehind {
        val w = (if (board) minOf(width, 1.5.dp) else width).toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((if (board) maxOf(radius, 6.dp) else radius).toPx()),
            style = Stroke(width = w, pathEffect = if (board) null else PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
        )
    }
}

/** Paper on the ground: a hard 2dp shadow under the card. On the board, a flat rounded panel with a hairline edge. */
fun Modifier.paper(fill: Color, shadow: Color) = composed {
    val t = LocalMemora.current
    if (t.board) {
        val shape = RoundedCornerShape(10.dp)
        Modifier.clip(shape).background(fill).border(1.dp, t.c.line, shape)
    } else {
        Modifier.drawBehind {
            drawRect(shadow, topLeft = Offset(0f, 2.dp.toPx()), size = size)
            drawRect(fill)
        }
    }
}

/** Ruled lines every [lineHeight], for handwritten text. */
fun Modifier.ruled(color: Color, lineHeight: Dp, offset: Dp = 0.dp) = drawBehind {
    val step = lineHeight.toPx()
    var y = step + offset.toPx() - 1.dp.toPx()
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += step
    }
}

// ---- Cards & stamps ----

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    padding: PaddingValues = PaddingValues(16.dp),
    gap: Dp = 10.dp,
    fill: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = theme
    val c = t.c
    Column(
        modifier
            .fillMaxWidth()
            .rotate(if (t.board) 0f else tilt)
            .paper(fill ?: c.paper, c.shadow)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** A postage stamp: dashed edge, and when [filled], a solid block inside it. */
@Composable
fun Stamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    width: Dp = 42.dp,
    height: Dp = 50.dp,
    fontSize: Int = 18,
    caption: String? = null,
) {
    if (theme.board) return FlapTile(text, color, modifier, filled, width, height, fontSize, caption)
    val c = theme.c
    Box(
        modifier.size(width, height).dashedBorder(color, if (filled) 2.dp else 1.5.dp).padding(if (filled) 3.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().then(if (filled) Modifier.background(color) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, lineHeight = fontSize.sp,
                color = if (filled) c.onStamp else color, maxLines = 1, textAlign = TextAlign.Center,
            )
            if (caption != null) Text(caption, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (filled) c.onStamp else color, lineHeight = 12.sp)
        }
    }
}

/** The board's stamp: a split-flap tile with the seam across its middle. */
@Composable
private fun FlapTile(text: String, color: Color, modifier: Modifier, filled: Boolean, width: Dp, height: Dp, fontSize: Int, caption: String?) {
    val t = theme
    val c = t.c
    val shape = RoundedCornerShape(6.dp)
    val fg = if (filled) c.onStamp else color
    val seam = Color.Black.copy(alpha = if (t.dark) 0.45f else 0.12f)
    Box(
        modifier.size(width, height).clip(shape).background(if (filled) color else c.paperAlt).border(1.dp, if (filled) color else c.line, shape)
            .drawBehind { drawLine(seam, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text, fontFamily = t.display, fontWeight = FontWeight.ExtraBold, fontSize = (fontSize + 1).sp, lineHeight = (fontSize + 1).sp,
                color = fg, maxLines = 1, textAlign = TextAlign.Center,
            )
            if (caption != null) Text(caption.uppercase(), fontFamily = MonoFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = fg, lineHeight = 10.sp)
        }
    }
}

/** A tilted rubber-stamp mark: "Sent", "Allowed", "Fix", "May shift by a day". The board sets it level, in mono capitals. */
@Composable
fun RubberStamp(text: String, color: Color, modifier: Modifier = Modifier, tilt: Float = -6f, fontSize: Int = 12) {
    if (theme.board) Text(
        text.uppercase(),
        modifier.border(1.5.dp, color, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        fontFamily = MonoFont, fontSize = (fontSize - 1).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = color,
    ) else Text(
        text,
        modifier.rotate(tilt).border(2.dp, color).padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = color,
    )
}

/** "★ Milestone" for big birthdays and anniversaries. */
@Composable
fun MilestoneTag(label: String, modifier: Modifier = Modifier) {
    val c = theme.c
    Text(
        "★ $label",
        modifier.background(c.highlight).dashedBorder(c.festival, 1.dp).padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.festivalInk,
    )
}

// ---- Buttons ----

enum class Look { INK, STAMP, OUTLINE, GHOST }

@Composable
fun PostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: Look = Look.INK,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    icon: Ic? = null,
    stacked: Boolean = false,
    fontSize: Int = 15,
) {
    val t = theme
    val c = t.c
    val (bg, fg) = when (look) {
        // The board's main action is its amber signal.
        Look.INK -> if (t.board) c.stamp to c.onStamp else c.ink to c.onInk
        Look.STAMP -> c.stamp to c.onStamp
        Look.OUTLINE, Look.GHOST -> Color.Transparent to c.ink
    }
    val shape = RoundedCornerShape(if (t.board) 8.dp else 4.dp)
    val alpha = if (enabled) 1f else 0.45f
    val content: @Composable () -> Unit = {
        if (icon != null) LineIcon(icon, fg.copy(alpha = alpha), size = 20)
        Text(text, fontSize = fontSize.sp, fontWeight = if (look == Look.GHOST) FontWeight.SemiBold else FontWeight.Bold, color = fg.copy(alpha = alpha), maxLines = 1)
    }
    val base = modifier
        .heightIn(min = height)
        .clip(shape)
        .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.45f))
        .then(if (look == Look.OUTLINE) Modifier.border(1.5.dp, c.ink.copy(alpha = alpha), shape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp)
    if (stacked) Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) { content() }
    else Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) { content() }
}

/** Round outlined icon button, 44dp. */
@Composable
fun CircleIconButton(icon: Ic, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = theme.c
    Box(
        modifier.size(44.dp).clip(CircleShape).border(1.5.dp, c.ink, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, c.ink, size = 20) }
}

/** Bare icon button, 44dp. */
@Composable
fun IconTap(icon: Ic, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null, size: Int = 22) {
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, color ?: theme.c.ink, size = size) }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    Box(modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(
            text, color = color ?: theme.c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        )
    }
}

/** On/off chip: ink-filled when selected, outlined otherwise. */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    enabled: Boolean = true,
    height: Dp = 38.dp,
) {
    val t = theme
    val c = t.c
    val shape = RoundedCornerShape(if (t.board) 8.dp else 4.dp)
    // Selected chips fill with ink on postcards, with the amber signal on the board.
    val (on, onFg) = if (t.board) c.stamp to c.onStamp else c.ink to c.onInk
    Row(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(if (selected) on else Color.Transparent)
            .border(1.5.dp, if (selected) on else if (enabled) c.ink else c.faint, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (dot != null) Dot(if (selected) onFg else dot, 8)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) onFg else if (enabled) c.ink else c.faint, maxLines = 1)
    }
}

@Composable
fun RowScope.WeightedChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) =
    ToggleChip(label, selected, onClick, Modifier.weight(1f), enabled = enabled, height = 42.dp)

// ---- Headers & text ----

/** Header of a pushed screen: back (or close) arrow, italic title, optional action. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, close: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val c = theme.c
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconTap(if (close) Ic.CLOSE else Ic.BACK, if (close) "Close" else "Back", onBack)
        val board = theme.board
        Text(
            if (board) title.uppercase() else title, Modifier.weight(1f).padding(start = if (onBack == null) 16.dp else 4.dp),
            fontFamily = DisplayFont, fontStyle = if (board) FontStyle.Normal else FontStyle.Italic,
            fontWeight = if (board) FontWeight.Black else FontWeight.ExtraBold, fontSize = if (board) 21.sp else 24.sp,
            color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        action?.invoke()
    }
}

/** Big italic title of a main tab, with an optional line above or below. */
@Composable
fun TabTitle(title: String, modifier: Modifier = Modifier, above: String? = null, below: String? = null) {
    val t = theme
    val c = t.c
    // The board's header reads like the top of a departures board: a mono dateline over capitals.
    val line: @Composable (String) -> Unit = {
        if (t.board) Text(it.uppercase(), fontFamily = MonoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = c.ok)
        else Text(it, fontSize = 14.sp, color = c.muted)
    }
    Column(modifier) {
        above?.let { line(it) }
        if (t.board) Text(title.uppercase(), fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 0.5.sp, color = c.ink, lineHeight = 36.sp)
        else Text(title, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = c.ink, lineHeight = 36.sp)
        below?.let { line(it) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, italic: Boolean = true, size: Int = 18) {
    // On the board, section names are small mono labels, like a timetable's column heads.
    if (theme.board) Text(
        text.uppercase(), modifier, fontFamily = MonoFont, fontWeight = FontWeight.Bold,
        fontSize = (size * 0.68f).sp, letterSpacing = 1.5.sp, color = theme.c.muted,
    ) else Text(
        text, modifier, fontFamily = DisplayFont, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight.SemiBold, fontSize = size.sp, color = theme.c.ink,
    )
}

@Composable
fun Handwriting(text: String, modifier: Modifier = Modifier, size: Int = 23, color: Color? = null, bold: Boolean = false, lineHeight: Int? = null) {
    val t = theme
    // Mono needs more leading than Caveat to stay readable over several lines.
    val lh = if (t.board) (lineHeight ?: size) * t.handScale * 1.3f else (lineHeight ?: (size * 1.15f).toInt()).toFloat()
    Text(
        text, modifier, fontFamily = HandFont, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = (size * t.handScale).sp, lineHeight = lh.sp, color = color ?: t.c.ink,
    )
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = theme.c.line
    val board = theme.board
    Box(
        modifier.fillMaxWidth().height(1.dp).drawBehind {
            drawLine(
                color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
                pathEffect = if (board) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        },
    )
}

@Composable
fun Dot(color: Color, size: Int = 6, diamond: Boolean = false) =
    Box(
        Modifier.size(size.dp).then(if (diamond) Modifier.rotate(45f) else Modifier)
            .clip(if (diamond) RoundedCornerShape(0.dp) else CircleShape).background(color),
    )

fun initials(name: String): String =
    name.trim().split(Regex("[\\s&]+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

// ---- Inputs ----

/** Label in italic serif, text on an underline. [handwritten] uses the handwriting face. */
@Composable
fun UnderlineField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    handwritten: Boolean = false,
    strong: Boolean = false,
) {
    val c = theme.c
    if (theme.board) {
        // The board's field: a rounded box, the label as a small amber mono head inside it.
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(10.dp)
        val style = TextStyle(fontFamily = if (handwritten) MonoFont else WorkSans, fontSize = 16.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, color = c.ink)
        Column(
            modifier.fillMaxWidth().clip(shape).background(c.paper).border(1.5.dp, if (focused) c.stamp else c.line, shape).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Mono(label, color = c.stamp, size = 10, bold = true, spacing = 1f)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true, textStyle = style, cursorBrush = SolidColor(c.stamp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.faint))
                        inner()
                    }
                },
            )
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
        val style = if (handwritten) TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Medium, fontSize = (28 * theme.handScale).sp, color = c.ink)
        else TextStyle(fontFamily = WorkSans, fontSize = 16.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, color = c.ink)
        val underline = if (strong || handwritten) c.ink else c.faint
        val thickness = if (strong || handwritten) 2.dp else 1.5.dp
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, textStyle = style,
            cursorBrush = SolidColor(c.stamp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .drawBehind { drawLine(underline, Offset(0f, size.height - thickness.toPx() / 2), Offset(size.width, size.height - thickness.toPx() / 2), thickness.toPx()) }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.faint))
                    inner()
                }
            },
        )
    }
}

/** A tap target styled like [UnderlineField], for pickers. */
@Composable
fun UnderlinePicker(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, placeholder: Boolean = false) {
    val c = theme.c
    if (theme.board) {
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier.fillMaxWidth().clip(shape).background(c.paper).border(1.5.dp, c.line, shape)
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Mono(label, color = c.stamp, size = 10, bold = true, spacing = 1f)
            Box(Modifier.heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                Text(value, fontFamily = DisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (placeholder) c.faint else c.ink)
            }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick)
                .drawBehind { drawLine(c.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (placeholder) c.faint else c.ink) }
    }
}

/** Handwritten text on ruled lines. */
@Composable
fun LinedField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    fontSize: Int = 24,
    lineHeight: Int = 30,
    minLines: Int = 3,
    color: Color? = null,
) {
    val c = theme.c
    val style = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Medium, fontSize = (fontSize * theme.handScale).sp, lineHeight = lineHeight.sp, color = color ?: c.ink)
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = style, cursorBrush = SolidColor(c.stamp), minLines = minLines,
        modifier = modifier.fillMaxWidth().ruled(c.rule, lineHeight.dp).semantics { contentDescription = label },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.faint))
                inner()
            }
        },
    )
}

/** Outlined single-line box, for search and quick notes. */
@Composable
fun BoxField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: Ic? = null,
    strong: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = theme.c
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier.heightIn(min = if (strong) 50.dp else 44.dp).clip(shape).background(c.paper)
            .border(if (strong) 2.dp else 1.5.dp, if (strong) c.ink else c.faint, shape).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) LineIcon(leading, c.muted, size = 18)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = WorkSans, fontSize = 16.sp, color = c.ink), cursorBrush = SolidColor(c.stamp),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}

@Composable
fun PaperSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = theme
    val c = t.c
    Switch(
        checked, onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = if (t.board) c.stamp else c.ink, checkedThumbColor = if (t.board) c.onStamp else c.onInk,
            uncheckedTrackColor = c.paperAlt,
            uncheckedBorderColor = c.faint, uncheckedThumbColor = c.faint,
        ),
    )
}

/** A row inside a paper card: title and hint on the left, anything on the right. */
@Composable
fun CardRow(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = theme.c
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            hint?.let { Text(it, fontSize = 13.sp, color = c.muted) }
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    CardRow(title, hint, Modifier.semantics { contentDescription = title }) { PaperSwitch(checked, onChange, enabled) }

@Composable
fun Chevron() = LineIcon(Ic.RIGHT, theme.c.muted, size = 18)

/** Fixed bar at the bottom of a form, e.g. "Save event". */
@Composable
fun BottomBar(content: @Composable () -> Unit) {
    val c = theme.c
    Column(Modifier.fillMaxWidth().background(c.paperAlt)) {
        DashedDivider()
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) { content() }
    }
}

/** Small spacer helper for rows. */
@Composable
fun Gap(w: Int) = Box(Modifier.width(w.dp))
