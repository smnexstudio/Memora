package com.smnexstudio.memora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.smnexstudio.memora.R
import com.smnexstudio.memora.data.EventType

// Two looks share every screen, each in light and dark:
// "Postcard": paper cards on a kraft ground, postage stamps, handwritten wishes.
// "Departures": a station board: split-flap date tiles, condensed capitals, mono times.

/** The look chosen in Settings → Appearance → Theme. */
enum class Skin(val id: String, val label: String) {
    POSTCARD("postcard", "Postcard"),
    BOARD("board", "Departures");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: POSTCARD
    }
}

private val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_extrabold, FontWeight.ExtraBold),
    Font(R.font.fraunces_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.fraunces_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
)
private val CaveatFamily = FontFamily(
    Font(R.font.caveat_medium, FontWeight.Medium),
    Font(R.font.caveat_bold, FontWeight.Bold),
)
val WorkSans = FontFamily(
    Font(R.font.work_sans_regular, FontWeight.Normal),
    Font(R.font.work_sans_medium, FontWeight.Medium),
    Font(R.font.work_sans_semibold, FontWeight.SemiBold),
    Font(R.font.work_sans_bold, FontWeight.Bold),
)

/** Archivo, a little condensed. The board has no italics, so italic asks get the upright face, never a slanted fake. */
@OptIn(ExperimentalTextApi::class)
private val ArchivoFamily = FontFamily(
    listOf(FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black).flatMap { w ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { s ->
            Font(
                R.font.archivo_variable, w, s,
                variationSettings = FontVariation.Settings(FontVariation.weight(w.weight), FontVariation.width(84f)),
            )
        }
    },
)

/** Great Vibes: the script on the letter card designs. */
val ScriptFont = FontFamily(Font(R.font.great_vibes, FontWeight.Normal))

/** JetBrains Mono: the board's times, labels and messages. */
@OptIn(ExperimentalTextApi::class)
val MonoFont = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.jetbrains_mono_variable, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

data class Palette(
    val ground: Color,      // behind everything
    val paper: Color,       // cards
    val paperAlt: Color,    // sunken areas, sheets, bottom bars; the board's flap tiles
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

/**
 * The board by day: a printed timetable. Amber is the signal, darkened to read on the pale ground.
 * Festivals are violet on the board: apart from the type colours, and not red, which reads as an alert.
 */
val BOARD_LIGHT = Palette(
    ground = Color(0xFFECEAE1), paper = Color(0xFFFBFAF3), paperAlt = Color(0xFFE4E1D6),
    ink = Color(0xFF1A1C20), onInk = Color(0xFFFBFAF3), muted = Color(0xFF55585E), faint = Color(0xFF8A8C90),
    line = Color(0xFFD3CFC3), shadow = Color(0xFFCFCBBE), rule = Color(0xFFDDD9CD),
    stamp = Color(0xFF946309), onStamp = Color(0xFFFFF7E6), stampSoft = Color(0xFFF1E3C4), stampDeep = Color(0xFF7A5006),
    festival = Color(0xFF6A4FB0), festivalBg = Color(0xFFEDE7F8), festivalInk = Color(0xFF3A2770),
    ok = Color(0xFF216E44), highlight = Color(0xFFF2E3BE),
)

/** The board by night: the lit departures board. */
val BOARD_DARK = Palette(
    ground = Color(0xFF121417), paper = Color(0xFF1A1D22), paperAlt = Color(0xFF23272D),
    ink = Color(0xFFF2EFE9), onInk = Color(0xFF121417), muted = Color(0xFFA3A6AB), faint = Color(0xFF7C8086),
    line = Color(0xFF2E333A), shadow = Color(0xFF0B0C0E), rule = Color(0xFF2A2E35),
    stamp = Color(0xFFF2C14E), onStamp = Color(0xFF17140A), stampSoft = Color(0xFF332B14), stampDeep = Color(0xFFF5CD6A),
    festival = Color(0xFFB9A2F2), festivalBg = Color(0xFF231E33), festivalInk = Color(0xFFD9CCFA),
    ok = Color(0xFF5BD08A), highlight = Color(0xFF3A3218),
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
private val BOARD_LIGHT_TYPES = mapOf(
    EventType.BIRTHDAY to Color(0xFF946309),
    EventType.ANNIVERSARY to Color(0xFF216E44),
    EventType.OTHER to Color(0xFF2F6FA6),
)
private val BOARD_DARK_TYPES = mapOf(
    EventType.BIRTHDAY to Color(0xFFF2C14E),
    EventType.ANNIVERSARY to Color(0xFF5BD08A),
    EventType.OTHER to Color(0xFF78B4E4),
)

data class MemoraTheme(val dark: Boolean, val c: Palette, val types: Map<EventType, Color>, val skin: Skin = Skin.POSTCARD) {
    /** Stamp and dot colour for an event type. */
    fun type(t: EventType) = types.getValue(t)

    val board get() = skin == Skin.BOARD

    /** Titles, names and numerals: Fraunces on postcards, condensed Archivo on the board. */
    val display: FontFamily get() = if (board) ArchivoFamily else FrauncesFamily

    /** The personal voice: Caveat handwriting on postcards, a mono message line on the board. */
    val hand: FontFamily get() = if (board) MonoFont else CaveatFamily

    /** Caveat sets small for its size; mono at the same size would shout. */
    val handScale: Float get() = if (board) 0.7f else 1f
}

val LocalMemora = staticCompositionLocalOf { MemoraTheme(false, LIGHT, LIGHT_TYPES) }

/** Shorthand for the current theme inside composables. */
val theme: MemoraTheme @Composable get() = LocalMemora.current

/** The current look's display face. */
val DisplayFont: FontFamily @Composable get() = theme.display

/** The current look's handwriting face; scale sizes by [MemoraTheme.handScale]. */
val HandFont: FontFamily @Composable get() = theme.hand

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
fun MemoraTheme(dark: Boolean, skin: Skin = Skin.POSTCARD, content: @Composable () -> Unit) {
    val board = skin == Skin.BOARD
    val c = when {
        board -> if (dark) BOARD_DARK else BOARD_LIGHT
        else -> if (dark) DARK else LIGHT
    }
    val types = when {
        board -> if (dark) BOARD_DARK_TYPES else BOARD_LIGHT_TYPES
        else -> if (dark) DARK_TYPES else LIGHT_TYPES
    }
    // Material's own controls (pickers, dialogs) take the board's amber as their primary.
    val primary = if (board) c.stamp else c.ink
    val onPrimary = if (board) c.onStamp else c.onInk
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary, secondary = c.stamp, background = c.ground, surface = c.paper,
            onSurface = c.ink, onBackground = c.ink, surfaceContainerHigh = c.paper, surfaceContainerLow = c.paperAlt, error = c.stamp,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary, secondary = c.stamp, background = c.ground, surface = c.paper,
            onSurface = c.ink, onBackground = c.ink, surfaceContainerHigh = c.paper, surfaceContainerLow = c.paperAlt, error = c.stamp,
        )
    }
    CompositionLocalProvider(LocalMemora provides MemoraTheme(dark, c, types, skin)) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
