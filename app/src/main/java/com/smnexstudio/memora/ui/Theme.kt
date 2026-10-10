package com.smnexstudio.memora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.smnexstudio.memora.R
import com.smnexstudio.memora.data.EventType

// "Postcard" look: paper cards on a kraft ground, postage stamps, handwritten wishes.

val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_extrabold, FontWeight.ExtraBold),
    Font(R.font.fraunces_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.fraunces_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
)
val Caveat = FontFamily(
    Font(R.font.caveat_medium, FontWeight.Medium),
    Font(R.font.caveat_bold, FontWeight.Bold),
)
val WorkSans = FontFamily(
    Font(R.font.work_sans_regular, FontWeight.Normal),
    Font(R.font.work_sans_medium, FontWeight.Medium),
    Font(R.font.work_sans_semibold, FontWeight.SemiBold),
    Font(R.font.work_sans_bold, FontWeight.Bold),
)

data class Palette(
    val ground: Color,      // behind everything
    val paper: Color,       // cards
    val paperAlt: Color,    // sunken areas, sheets, bottom bars
    val ink: Color,         // text, primary buttons
    val onInk: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,        // dashed dividers
    val shadow: Color,      // hard paper shadow
    val rule: Color,        // ruled lines under handwriting
    val stamp: Color,       // red: accent, main actions
    val onStamp: Color,
    val stampSoft: Color,
    val stampDeep: Color,   // red text on paper
    val festival: Color,
    val festivalBg: Color,
    val festivalInk: Color,
    val ok: Color,
    val highlight: Color,
)

val LIGHT = Palette(
    ground = Color(0xFFE6DFD2), paper = Color(0xFFFFFDF8), paperAlt = Color(0xFFF3EEE4),
    ink = Color(0xFF23395B), onInk = Color(0xFFFFFDF8), muted = Color(0xFF4A5B78), faint = Color(0xFFA69C8A),
    line = Color(0xFFD8CFBF), shadow = Color(0xFFCFC6B5), rule = Color(0xFFE3DBCB),
    stamp = Color(0xFFB23A2E), onStamp = Color(0xFFFFFDF8), stampSoft = Color(0xFFF7E3DF), stampDeep = Color(0xFF7A1F16),
    festival = Color(0xFF9A6A00), festivalBg = Color(0xFFFBF3DF), festivalInk = Color(0xFF4A3300),
    ok = Color(0xFF2E7060), highlight = Color(0xFFF5E7C4),
)

val DARK = Palette(
    ground = Color(0xFF141C2B), paper = Color(0xFF1F2A3D), paperAlt = Color(0xFF182233),
    ink = Color(0xFFEDE6D8), onInk = Color(0xFF141C2B), muted = Color(0xFFA9B4C8), faint = Color(0xFF6E7A90),
    line = Color(0xFF33415A), shadow = Color(0xFF0B111C), rule = Color(0xFF2C3950),
    stamp = Color(0xFFE0705F), onStamp = Color(0xFF1A0E0C), stampSoft = Color(0xFF3A2226), stampDeep = Color(0xFFF2A397),
    festival = Color(0xFFE0B456), festivalBg = Color(0xFF2E2614), festivalInk = Color(0xFFF2D9A0),
    ok = Color(0xFF6FC2A9), highlight = Color(0xFF4A3D1C),
)

private val LIGHT_TYPES = mapOf(
    EventType.BIRTHDAY to Color(0xFFB23A2E),
    EventType.ANNIVERSARY to Color(0xFF5B4E9A),
    EventType.OTHER to Color(0xFF2E7060),
)
private val DARK_TYPES = mapOf(
    EventType.BIRTHDAY to Color(0xFFE0705F),
    EventType.ANNIVERSARY to Color(0xFFA99BE8),
    EventType.OTHER to Color(0xFF6FC2A9),
)

data class MemoraTheme(val dark: Boolean, val c: Palette, val types: Map<EventType, Color>) {
    /** Stamp and dot colour for an event type. */
    fun type(t: EventType) = types.getValue(t)
}

val LocalMemora = staticCompositionLocalOf { MemoraTheme(false, LIGHT, LIGHT_TYPES) }

/** Shorthand for the current theme inside composables. */
val theme: MemoraTheme @Composable get() = LocalMemora.current

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.sans() = copy(fontFamily = WorkSans)
    return Typography(
        displayLarge = base.displayLarge.sans(), displayMedium = base.displayMedium.sans(), displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans(), headlineMedium = base.headlineMedium.sans(), headlineSmall = base.headlineSmall.sans(),
        titleLarge = base.titleLarge.sans(), titleMedium = base.titleMedium.sans(), titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(), bodyMedium = base.bodyMedium.sans(), bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(), labelMedium = base.labelMedium.sans(), labelSmall = base.labelSmall.sans(),
    )
}

@Composable
fun MemoraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DARK else LIGHT
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.ink, onPrimary = c.onInk, secondary = c.stamp, background = c.ground, surface = c.paper,
            onSurface = c.ink, onBackground = c.ink, surfaceContainerHigh = c.paper, surfaceContainerLow = c.paperAlt, error = c.stamp,
        )
    } else {
        lightColorScheme(
            primary = c.ink, onPrimary = c.onInk, secondary = c.stamp, background = c.ground, surface = c.paper,
            onSurface = c.ink, onBackground = c.ink, surfaceContainerHigh = c.paper, surfaceContainerLow = c.paperAlt, error = c.stamp,
        )
    }
    CompositionLocalProvider(LocalMemora provides MemoraTheme(dark, c, if (dark) DARK_TYPES else LIGHT_TYPES)) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
