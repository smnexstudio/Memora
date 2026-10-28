package com.smnexstudio.memora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.logic.MONTHS
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

// The Departures look's own pieces: the board header, the DATE · WHO · IN column heads and
// departure rows. Screens use them only when the theme is the board.

/** Small mono capitals: column heads, labels, meta lines. */
@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color? = null, size: Int = 10, bold: Boolean = false, spacing: Float = 0.6f, maxLines: Int = 1) {
    Text(
        text.uppercase(), modifier, fontFamily = MonoFont, fontSize = size.sp, letterSpacing = spacing.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = color ?: theme.c.muted,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** "WED 28 OCT" */
fun boardDate(d: LocalDate): String =
    "${d.dayOfWeek.name.take(3)} ${d.dayOfMonth.toString().padStart(2, '0')} ${MONTHS[d.monthValue - 1].take(3)}".uppercase()

/** Red for a missed wish: the board's "cancelled" colour. */
val boardRed: Color @Composable get() = if (theme.dark) Color(0xFFF2865E) else Color(0xFFB8401F)

/** "NOW", "1 DAY", "12 DAYS", "MISSED" */
fun boardIn(days: Int): String = when {
    days < 0 -> "MISSED"
    days == 0 -> "NOW"
    days == 1 -> "1 DAY"
    else -> "$days DAYS"
}

/** The head of the board: big capitals on the left, a live mono clock on the right. */
@Composable
fun BoardHeader(title: String, modifier: Modifier = Modifier, sub: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = theme.c
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            now = LocalTime.now()
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 0.5.sp, color = c.ink, lineHeight = 34.sp, maxLines = 1)
            sub?.let { Mono(it, Modifier.padding(top = 2.dp), color = c.muted, size = 11) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "%02d:%02d".format(now.hour, now.minute), fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ok,
            )
            Mono(boardDate(LocalDate.now()), color = c.ok, size = 10)
        }
        trailing?.invoke()
    }
}

/** Column heads between two amber rules. */
@Composable
fun BoardColumns(vararg heads: String, modifier: Modifier = Modifier) {
    val c = theme.c
    val rule = c.stamp.copy(alpha = 0.35f)
    Row(
        modifier.fillMaxWidth().drawBehind {
            drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        heads.forEachIndexed { i, h ->
            when (i) {
                0 -> Mono(h, Modifier.width(46.dp), size = 9, spacing = 1.2f)
                heads.lastIndex -> Mono(h, size = 9, spacing = 1.2f)
                else -> Mono(h, Modifier.weight(1f), size = 9, spacing = 1.2f)
            }
        }
    }
}

/** A split-flap date tile on its own, for rows: day number, or a symbol. */
@Composable
fun Flap(text: String, color: Color? = null, filled: Boolean = false, size: Dp = 46.dp, fontSize: Int = 18) {
    val t = theme
    val c = t.c
    val shape = RoundedCornerShape(6.dp)
    val seam = Color.Black.copy(alpha = if (t.dark) 0.5f else 0.12f)
    val bg = if (filled) c.stamp else c.paperAlt
    Box(
        Modifier.size(size, size * 0.84f).clip(shape).background(bg).border(1.dp, if (filled) c.stamp else c.line, shape)
            .drawBehind { drawLine(seam, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), 1.dp.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp,
            color = if (filled) c.onStamp else color ?: c.ink, maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

/**
 * One departure: date tile, the name as the destination, a mono meta line, and the time to go.
 * [now] tints the row amber; [action] adds a small amber chip under the meta line.
 */
@Composable
fun DepartureRow(
    flap: String,
    name: String,
    meta: String,
    whenText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flapColor: Color? = null,
    flapFilled: Boolean = false,
    whenColor: Color? = null,
    now: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = theme.c
    Row(
        modifier.fillMaxWidth()
            .then(if (now) Modifier.background(Brush.horizontalGradient(listOf(c.stamp.copy(alpha = 0.18f), Color.Transparent))) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Flap(flap, flapColor, filled = flapFilled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name.uppercase(), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Mono(meta, size = 10, maxLines = 2)
            if (action != null && onAction != null) Box(
                Modifier.padding(top = 4.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(6.dp)).background(c.stamp)
                    .clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Mono("▸ $action", color = c.onStamp, size = 11, bold = true) }
        }
        if (trailing != null) trailing() else Text(
            whenText, fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = whenColor ?: c.muted, textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

/** A group letter or label above board rows. */
@Composable
fun BoardLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) =
    Mono(text, modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), color = color ?: theme.c.stamp, size = 11, bold = true, spacing = 1.5f)

/** A mono label / value line for detail tables. */
@Composable
fun BoardDetail(label: String, value: String, faint: Boolean = false) {
    val c = theme.c
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Mono(label, size = 10)
        Text(
            value, Modifier.weight(1f), fontFamily = MonoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = if (faint) c.faint else c.ink, textAlign = TextAlign.End,
        )
    }
}

/** Thin full-width rule. */
@Composable
fun BoardRule(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(theme.c.line))
