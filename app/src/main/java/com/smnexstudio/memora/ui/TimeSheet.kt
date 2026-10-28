package com.smnexstudio.memora.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A time picker drawn in the theme, as a sheet: a paper clock face with a red stamp on the
 * chosen number for the postcard, a dark face with an amber tile for the board. Hours first,
 * then minutes; tap the hour or minutes above the face to go back to either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(title: String, hint: String?, hour: Int, minute: Int, onDismiss: () -> Unit, onSet: (Int, Int) -> Unit) {
    val t = theme
    val c = t.c
    var h by remember { mutableIntStateOf(hour) }
    var m by remember { mutableIntStateOf(minute) }
    var minutes by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.paper, shape = sheetShape(),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().drawBehind { if (t.board) drawRect(c.stamp, size = Size(size.width, 2.dp.toPx())) else airmail(c.stamp, c.ink) }
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (t.board) Mono(title, size = 11, bold = true, spacing = 1.6f)
                else Text(title, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.ink)
                if (hint != null && !t.board) Text(hint, fontSize = 13.sp, color = c.muted)
            }
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val h12 = (h + 11) % 12 + 1
                Row(verticalAlignment = Alignment.Bottom) {
                    BigTime(if (t.board) h12.toString().padStart(2, '0') else h12.toString(), !minutes) { minutes = false }
                    BigTime(":" + m.toString().padStart(2, '0'), minutes) { minutes = true }
                }
                Meridiem(h < 12) { am -> if (am != h < 12) h = (h + 12) % 24 }
            }
            Dial(minutes, h, m, Modifier.align(Alignment.CenterHorizontally)) { value, done ->
                if (minutes) m = value else h = value % 12 + if (h < 12) 0 else 12
                if (done && !minutes) minutes = true
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, if (t.board) Alignment.Start else Alignment.End), modifier = Modifier.fillMaxWidth()) {
                val board = t.board
                PostButton("Cancel", onDismiss, if (board) Modifier.weight(1f) else Modifier, look = if (board) Look.OUTLINE else Look.GHOST)
                PostButton("Set time", { onSet(h, m) }, if (board) Modifier.weight(1f) else Modifier)
            }
        }
    }
}

/** The postcard sheet's top edge: red and navy airmail dashes. */
private fun DrawScope.airmail(red: Color, navy: Color) {
    val dash = 14.dp.toPx()
    val gap = 6.dp.toPx()
    val tall = 6.dp.toPx()
    var x = 0f
    var i = 0
    while (x < size.width) {
        drawRect(if (i % 2 == 0) red else navy, Offset(x, 0f), Size(dash, tall))
        x += dash + gap
        i++
    }
}

/** One half of the big time above the face; the half being picked is in ink, the other muted. */
@Composable
private fun BigTime(text: String, active: Boolean, onClick: () -> Unit) {
    val t = theme
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick),
        fontFamily = DisplayFont, fontStyle = if (t.board) FontStyle.Normal else FontStyle.Italic,
        fontWeight = if (t.board) FontWeight.Black else FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 60.sp,
        color = if (active) t.c.ink else t.c.muted.copy(alpha = 0.7f),
    )
}

/** AM and PM, stacked: small stamps on the postcard, a two-part switch on the board. */
@Composable
private fun Meridiem(am: Boolean, onPick: (Boolean) -> Unit) {
    val t = theme
    val c = t.c
    if (t.board) {
        Column(Modifier.clip(RoundedCornerShape(8.dp)).border(1.5.dp, c.line, RoundedCornerShape(8.dp))) {
            listOf(true to "AM", false to "PM").forEach { (isAm, label) ->
                val on = am == isAm
                Box(Modifier.background(if (on) c.stamp else Color.Transparent).clickable(role = Role.RadioButton) { onPick(isAm) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Mono(label, color = if (on) c.onStamp else c.muted, size = 12, bold = true)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(true to "am", false to "pm").forEach { (isAm, label) ->
                val on = am == isAm
                Box(
                    Modifier.background(if (on) c.stamp else Color.Transparent).dashedBorder(c.stamp, 1.5.dp)
                        .clickable(role = Role.RadioButton) { onPick(isAm) }.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(label, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (on) c.onStamp else c.stamp)
                }
            }
        }
    }
}

/**
 * The clock face. [onPick] gets the hour (1–12 as 0–11) or minute under the finger, and
 * done = true when the finger lifts.
 */
@Composable
private fun Dial(minutes: Boolean, h: Int, m: Int, modifier: Modifier, onPick: (Int, Boolean) -> Unit) {
    val t = theme
    val c = t.c
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val style = TextStyle(
        fontFamily = if (t.board) MonoFont else DisplayFont, fontStyle = if (t.board) FontStyle.Normal else FontStyle.Italic,
        fontWeight = if (t.board) FontWeight.Bold else FontWeight.ExtraBold, fontSize = 19.sp,
    )
    val chosen = if (minutes) m else h % 12
    val steps = if (minutes) 60 else 12
    val label = if (minutes) "${m.toString().padStart(2, '0')} minutes" else "${(h + 11) % 12 + 1} o’clock"
    Canvas(
        modifier.size(248.dp).semantics { contentDescription = "Clock face, $label" }.pointerInput(minutes) {
            fun at(p: Offset): Int {
                val deg = Math.toDegrees(atan2((p.x - size.width / 2f).toDouble(), (size.height / 2f - p.y).toDouble()))
                return ((deg + 360) % 360 / (360.0 / steps)).roundToInt() % steps
            }
            var last = -1
            fun pick(p: Offset, done: Boolean) {
                val v = at(p)
                if (v != last) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); last = v }
                onPick(v, done)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                last = chosen
                pick(down.position, false)
                var end = down.position
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    end = change.position
                    if (!change.pressed) break
                    pick(end, false)
                }
                pick(end, true)
            }
        },
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val ring = size.minDimension / 2 * 0.76f
        drawCircle(if (t.board) c.ground else c.paperAlt, size.minDimension / 2)
        drawCircle(c.line, size.minDimension / 2 - 1.dp.toPx(), style = Stroke((if (t.board) 2.0 else 1.5).dp.toPx()))
        val angle = Math.toRadians(chosen * 360.0 / steps)
        val tip = Offset(center.x + ring * sin(angle).toFloat(), center.y - ring * cos(angle).toFloat())
        drawLine(c.stamp, center, tip, 2.5.dp.toPx())
        drawCircle(c.stamp, 4.dp.toPx(), center)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val p = Offset(center.x + ring * sin(a).toFloat(), center.y - ring * cos(a).toFloat())
            numeral(measurer, face(i, minutes, t.board), style.copy(color = c.ink), p)
        }
        // The chosen number sits on its own tile, on top of whatever is under it.
        val tile = 44.dp.toPx()
        if (t.board) {
            drawRoundRect(c.stamp, Offset(tip.x - tile / 2, tip.y - tile / 2), Size(tile, tile), CornerRadius(9.dp.toPx()))
        } else rotate(-6f, tip) {
            val outer = tile + 10.dp.toPx()
            drawRect(c.paper, Offset(tip.x - outer / 2, tip.y - outer / 2), Size(outer, outer))
            drawRect(
                c.stamp, Offset(tip.x - outer / 2, tip.y - outer / 2), Size(outer, outer),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
            )
            drawRect(c.stamp, Offset(tip.x - tile / 2 + 2.dp.toPx(), tip.y - tile / 2 + 2.dp.toPx()), Size(tile - 4.dp.toPx(), tile - 4.dp.toPx()))
        }
        val shown = if (minutes) m.toString().padStart(2, '0') else face(h % 12, false, t.board)
        numeral(measurer, shown, style.copy(color = c.onStamp), tip)
    }
}

/** What the face shows at position [i] (0 is the top): 12, 1, 2… for hours, 00, 05, 10… for minutes. */
private fun face(i: Int, minutes: Boolean, board: Boolean): String =
    if (minutes) (i * 5).toString().padStart(2, '0')
    else (if (i == 0) 12 else i).let { if (board) it.toString().padStart(2, '0') else it.toString() }

private fun DrawScope.numeral(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
