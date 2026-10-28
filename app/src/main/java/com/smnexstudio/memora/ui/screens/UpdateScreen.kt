package com.smnexstudio.memora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.platform.Release
import com.smnexstudio.memora.platform.UpdateState
import com.smnexstudio.memora.platform.Updates
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.theme
import java.time.LocalDate

/** What's new in a release, then its download, check and install. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val c = theme.c
    val context = LocalContext.current
    val state by Updates.state.collectAsState()
    val installed = remember { Updates.installedVersion(context) }
    val offered = remember { Updates.available(context) ?: Updates.latest()?.takeIf { Updates.isNewer(it.version, installed) } }
    val release: Release? = when (val s = state) {
        is UpdateState.Downloading -> s.release
        is UpdateState.Verifying -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        UpdateState.Idle -> offered
    }

    DisposableEffect(Unit) {
        Updates.watching = true
        Updates.clearNotification(context)
        onDispose { Updates.watching = false }
    }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(
            when (state) {
                is UpdateState.Downloading, is UpdateState.Verifying -> "Getting ${release?.version.orEmpty()}"
                is UpdateState.Ready, is UpdateState.Installing -> "Ready to install"
                else -> "A new Memora"
            },
            onBack,
        )
        if (release == null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LineIcon(Ic.CHECK, c.ok, size = 20)
                        Text("You have the latest version, $installed", Modifier.weight(1f), color = c.ink)
                        RubberStamp("Up to date", c.ok, tilt = 4f)
                    }
                }
            }
            return@Column
        }
        when (val s = state) {
            is UpdateState.Downloading, is UpdateState.Verifying -> Downloading(release, s)
            is UpdateState.Ready, is UpdateState.Installing -> Install(release, s)
            else -> WhatsNew(release, installed, s as? UpdateState.Failed, onBack)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.WhatsNew(release: Release, installed: String, failed: UpdateState.Failed?, onBack: () -> Unit) {
    val c = theme.c
    val context = LocalContext.current
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Box(Modifier.padding(top = 12.dp)) {
            PaperCard(tilt = -1f, padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stamp(release.version, c.festival, filled = true, width = 78.dp, height = 92.dp, fontSize = 22, caption = "new")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Memora ${release.version}", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 26.sp, color = c.ink)
                        Text("You have $installed" + (releasedOn(release)?.let { " · released $it" } ?: ""), fontSize = 14.sp, color = c.muted)
                        Text("${megabytes(release.apkSize)} download", fontSize = 14.sp, color = c.muted)
                    }
                }
            }
        }

        if (failed != null) PaperCard(Modifier.padding(top = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(4.dp).height(44.dp).background(c.stamp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (failed.notGenuine) failed.message else "The download stopped",
                        fontWeight = FontWeight.Bold, color = c.stampDeep,
                    )
                    Text(
                        if (failed.notGenuine) "It was deleted and nothing was installed. Try again on another network, or download Memora from the GitHub page."
                        else failed.message,
                        fontSize = 13.sp, color = c.muted,
                    )
                }
            }
        }

        if (release.notes.isNotEmpty()) PaperCard(Modifier.padding(top = 24.dp)) {
            SectionTitle("What’s new")
            release.notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(c.stamp))
                    Text(note, fontSize = 15.sp, lineHeight = 21.sp, color = c.ink)
                }
            }
            LinkText("Full release notes on GitHub", { openPage(context, release.pageUrl) })
        }

        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineIcon(Ic.LOCK, c.ok, size = 18)
            Text(
                "Your events, notes and settings stay as they are. Memora checks the download is the real release before installing.",
                fontSize = 13.sp, color = c.muted,
            )
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PostButton(
            if (failed != null) "Try again" else "Download & install",
            { Updates.reset(); Updates.download(context, release) },
            Modifier.fillMaxWidth(), look = Look.STAMP, height = 54.dp, fontSize = 16, icon = if (failed != null) Ic.REFRESH else null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PostButton("Later", onBack, Modifier.weight(1f), look = Look.OUTLINE, height = 46.dp)
            PostButton("Skip this version", { Updates.skip(release); Updates.reset(); onBack() }, Modifier.weight(1f), look = Look.GHOST, height = 46.dp)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Downloading(release: Release, state: UpdateState) {
    val c = theme.c
    val read = (state as? UpdateState.Downloading)?.read ?: release.apkSize
    val fraction = if (release.apkSize > 0) (read.toFloat() / release.apkSize).coerceIn(0f, 1f) else 0f
    val checking = state is UpdateState.Verifying
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PaperCard(Modifier.padding(top = 18.dp), padding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 20.dp), gap = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("${(fraction * 100).toInt()}%", Modifier.weight(1f), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp, color = c.ink)
                Text("${megabytes(read, unit = false)} of ${megabytes(release.apkSize)}", fontSize = 14.sp, color = c.muted)
            }
            Box(
                Modifier.fillMaxWidth().height(18.dp).dashedBorder(c.ink, 2.dp).padding(3.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
            ) {
                val red = c.stamp
                val navy = c.ink
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction).clipToBounds().drawBehind {
                        // Airmail stripes, like the envelope on the send screen.
                        val band = 10.dp.toPx()
                        var x = -size.height
                        var i = 0
                        while (x < size.width) {
                            drawLine(if (i % 2 == 0) red else navy, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = band * 0.72f)
                            x += band; i++
                        }
                    },
                )
            }
            Handwriting(if (checking) "Here. Checking it’s the real thing…" else "On its way from GitHub.", size = 22)
        }

        PaperCard(Modifier.padding(top = 22.dp), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp), gap = 0.dp) {
            Step(if (checking) StepMark.DONE else StepMark.NOW, "Download", release.apkName)
            DashedDivider()
            Step(if (checking) StepMark.NOW else StepMark.LATER, "Check it’s genuine", "Matches the release fingerprint and is signed like the Memora you have")
            DashedDivider()
            Step(StepMark.LATER, "Install", "Android asks you to confirm")
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("You can leave this screen. A notification shows when it’s ready.", Modifier.fillMaxWidth(), fontSize = 13.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (!checking) PostButton("Cancel download", { Updates.cancel() }, Modifier.fillMaxWidth(), look = Look.OUTLINE, height = 50.dp)
    }
}

private enum class StepMark { DONE, NOW, LATER }

@Composable
private fun Step(mark: StepMark, title: String, hint: String) {
    val c = theme.c
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when (mark) {
            StepMark.DONE -> Box(Modifier.size(30.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) { LineIcon(Ic.CHECK, c.onInk, size = 16) }
            StepMark.NOW -> Box(Modifier.size(30.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) { LineIcon(Ic.RIGHT, c.onInk, size = 16) }
            StepMark.LATER -> Box(Modifier.size(30.dp).dashedBorder(c.faint, 2.dp, radius = 15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = c.ink)
            Text(hint, fontSize = 13.sp, color = c.muted)
        }
        if (mark == StepMark.NOW) Text("Now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.stamp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Install(release: Release, state: UpdateState) {
    val c = theme.c
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumed++ } }
    val allowed = remember(resumed) { Updates.canInstall(context) }
    val installing = state is UpdateState.Installing
    // Show Android's prompt by itself once; after a Cancel there, it waits for the button.
    var autoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(allowed, state is UpdateState.Ready) {
        if (allowed && state is UpdateState.Ready && !autoStarted) {
            autoStarted = true
            Updates.install(context)
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PaperCard(Modifier.padding(top = 12.dp), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), gap = 0.dp) {
            Step(StepMark.DONE, "Download", release.apkName)
            DashedDivider()
            Step(StepMark.DONE, "Check it’s genuine", "Fingerprint and signature match")
            DashedDivider()
            Step(if (allowed) StepMark.NOW else StepMark.LATER, "Install", if (installing) "Waiting for Android…" else "Android asks you to confirm")
        }

        if (!allowed) Column(
            Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 2.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("FIRST TIME ONLY", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = c.festivalInk)
            Text("Allow Memora to install updates", fontWeight = FontWeight.Bold, color = c.festivalInk)
            Text("Android needs your OK once. Turn on “Allow from this source”, then come back.", fontSize = 13.sp, color = c.festivalInk)
            PostButton("Open Android settings", { context.startActivity(Updates.permissionIntent(context)) }, Modifier.fillMaxWidth(), height = 44.dp)
        } else Handwriting(
            if (installing) "Android is asking you to confirm. Tap Update." else "All set. Memora restarts on ${release.version} after the install.",
            Modifier.padding(horizontal = 4.dp), size = 22,
        )
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        PostButton(
            if (installing) "Installing…" else "Install now", { Updates.install(context) },
            Modifier.fillMaxWidth(), look = Look.STAMP, height = 54.dp, fontSize = 16, enabled = allowed && !installing,
        )
    }
}

private fun openPage(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

internal fun megabytes(bytes: Long, unit: Boolean = true): String =
    "%.1f".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0)) + if (unit) " MB" else ""

internal fun releasedOn(release: Release): String? =
    runCatching { LocalDate.parse(release.publishedAt) }.getOrNull()?.let { d ->
        "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" + if (d.year != LocalDate.now().year) " ${d.year}" else ""
    }
