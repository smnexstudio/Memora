package com.smnexstudio.memora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.smnexstudio.memora.data.AppPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours for a [PinPad]: on the navy lock screen, or on paper in Settings. */
data class PinColors(val ink: Color, val muted: Color, val key: Color, val accent: Color, val onAccent: Color)

/**
 * Dots for the digits typed so far, a number keypad and a confirm button.
 * [extraKey] takes the empty bottom-left key, e.g. the fingerprint button.
 */
@Composable
fun PinPad(
    colors: PinColors,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    enabled: Boolean = true,
    clearSignal: Int = 0,
    extraKey: (@Composable () -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(clearSignal) { pin = "" }
    Column(modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.heightIn(min = 24.dp).semantics { contentDescription = "${pin.length} of ${AppPin.MAX_LENGTH} digits entered" },
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until AppPin.MAX_LENGTH) {
                val filled = i < pin.length
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .then(if (filled) Modifier.background(colors.ink) else Modifier.border(1.5.dp, if (i < AppPin.MIN_LENGTH) colors.ink else colors.muted, CircleShape)),
                )
            }
        }
        Text(
            message ?: " ", Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 14.sp, color = colors.muted, textAlign = TextAlign.Center, minLines = 2,
        )
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "extra", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { k ->
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (k) {
                            "extra" -> extraKey?.invoke()
                            "del" -> if (pin.isNotEmpty()) Box(
                                Modifier.size(64.dp).clip(CircleShape).clickable(role = Role.Button) { pin = pin.dropLast(1) }
                                    .semantics { contentDescription = "Delete" },
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Ic.BACK, colors.ink, size = 24) }
                            else -> Box(
                                Modifier.size(64.dp).clip(CircleShape).background(colors.key)
                                    .clickable(enabled = enabled, role = Role.Button) { if (pin.length < AppPin.MAX_LENGTH) pin += k },
                                contentAlignment = Alignment.Center,
                            ) { Text(k, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = colors.ink) }
                        }
                    }
                }
            }
        }
        val ready = enabled && pin.length >= AppPin.MIN_LENGTH
        Box(
            Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(4.dp))
                .background(if (ready) colors.accent else colors.accent.copy(alpha = 0.35f))
                .clickable(enabled = ready, role = Role.Button) { onConfirm(pin) },
            contentAlignment = Alignment.Center,
        ) { Text(confirmLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onAccent.copy(alpha = if (ready) 1f else 0.7f)) }
    }
}

@Composable
fun paperPinColors(): PinColors {
    val c = theme.c
    return PinColors(ink = c.ink, muted = c.muted, key = c.paperAlt, accent = c.stamp, onAccent = c.onStamp)
}

/** Full-screen dialog to choose a new PIN: type it, then type it again. */
@Composable
fun SetPinDialog(title: String, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    val c = theme.c
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableIntStateOf(0) }
    PinDialogFrame(title, onDismiss) {
        Text(
            if (first == null) "Choose a ${AppPin.MIN_LENGTH}–${AppPin.MAX_LENGTH} digit PIN. It always opens Memora, even if your phone’s fingerprint and screen lock are removed."
            else "Type the same PIN again.",
            fontSize = 15.sp, lineHeight = 21.sp, color = c.muted, textAlign = TextAlign.Center,
        )
        PinPad(
            paperPinColors(), if (first == null) "Next" else "Set PIN", clearSignal = clear, message = message,
            onConfirm = { pin ->
                when {
                    first == null && pin.toSet().size == 1 -> { message = "Avoid one digit repeated. Try another PIN."; clear++ }
                    first == null && "0123456789012345".contains(pin) -> { message = "Avoid a simple run like 1234. Try another PIN."; clear++ }
                    first == null -> { first = pin; message = null; clear++ }
                    pin == first -> onSet(pin)
                    else -> { first = null; message = "Those didn’t match. Start again."; clear++ }
                }
            },
        )
        Text("If you forget it and your phone has no fingerprint or screen lock, Memora can’t be opened.", fontSize = 13.sp, color = c.muted, textAlign = TextAlign.Center)
    }
}

/** Full-screen dialog that checks the current PIN, with the growing wait after wrong tries. */
@Composable
fun VerifyPinDialog(title: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    PinDialogFrame(title, onDismiss) {
        PinEntry(paperPinColors(), "Confirm", onOk)
    }
}

/** A [PinPad] that checks against the saved PIN and explains wrong tries and waits. */
@Composable
fun PinEntry(colors: PinColors, confirmLabel: String, onOk: () -> Unit, extraKey: (@Composable () -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableLongStateOf(AppPin.waitSeconds()) }
    var checking by remember { mutableStateOf(false) }
    var clear by remember { mutableIntStateOf(0) }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = AppPin.waitSeconds()
        }
    }
    PinPad(
        colors, if (checking) "Checking…" else confirmLabel, clearSignal = clear, enabled = wait == 0L && !checking, extraKey = extraKey,
        message = if (wait > 0) "Too many wrong tries. Try again in ${waitText(wait)}." else message,
        onConfirm = { pin ->
            checking = true
            scope.launch {
                val result = withContext(Dispatchers.Default) { AppPin.check(pin) }
                checking = false
                clear++
                when (result) {
                    AppPin.Check.Ok -> onOk()
                    is AppPin.Check.Wrong -> message = "Wrong PIN. ${result.triesBeforeWait} ${if (result.triesBeforeWait == 1) "try" else "tries"} left before a wait."
                    is AppPin.Check.Wait -> wait = result.seconds
                }
            }
        },
    )
}

private fun waitText(seconds: Long) = if (seconds < 60) "$seconds s" else "${(seconds + 59) / 60} min"

@Composable
private fun PinDialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = theme.c
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Column(
            Modifier.fillMaxSize().background(c.ground).systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Ic.CLOSE, "Cancel", onDismiss)
                Spacer(Modifier.weight(1f))
            }
            Text(title, fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = c.ink, textAlign = TextAlign.Center)
            content()
        }
    }
}
