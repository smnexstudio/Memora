package com.smnexstudio.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.ui.CardRow
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.SwitchRow
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.theme
import com.smnexstudio.memora.widget.LockedWidget
import com.smnexstudio.memora.widget.NextEventsWidget
import com.smnexstudio.memora.widget.WidgetPrefs

/** Settings entry line for the widget. */
fun widgetSummary(lockOn: Boolean): String = when {
    !lockOn -> "Shows names and what’s coming up"
    WidgetPrefs.locked() == LockedWidget.FULL -> "Names show even while app lock is on"
    else -> "Details hidden while app lock is on"
}

@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val c = theme.c
    val context = LocalContext.current
    val version by Repo.version.collectAsState()
    val mode = remember(version) { WidgetPrefs.locked() }
    val ages = remember(version) { WidgetPrefs.ages() }
    val festivals = remember(version) { WidgetPrefs.festivals() }
    val rows = remember(version) { WidgetPrefs.rows() }

    fun changed(key: String, value: String) {
        Repo.setSetting(key, value)
        NextEventsWidget.updateAll(context)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Widget", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // What each state looks like
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Preview("App lock on", locked = true, Modifier.weight(1f)) {
                    when (mode) {
                        LockedWidget.NOTHING -> Text("Memora", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF23395B))
                        LockedWidget.FULL -> SampleNames()
                        LockedWidget.COUNT -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(26.dp, 32.dp).dashedBorder(Color(0xFFB23A2E), 1.5.dp).padding(2.dp).background(Color(0xFFB23A2E)), contentAlignment = Alignment.Center) {
                                Text("2", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFFFFFDF8))
                            }
                            Column {
                                Text("2 events today", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF23395B))
                                Text("Tap to view", fontSize = 12.sp, color = Color(0xFF4A5B78))
                            }
                        }
                    }
                }
                Preview("App lock off", locked = false, Modifier.weight(1f)) { SampleNames() }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("While Memora has a password, show", Modifier.padding(horizontal = 4.dp))
                Choice(
                    "How many events, and the next date", "“2 events today” or “Nothing today, next Mon 12 Oct”",
                    mode == LockedWidget.COUNT,
                ) { changed("widgetLocked", LockedWidget.COUNT.id) }
                Choice("Nothing at all", "Just the Memora name", mode == LockedWidget.NOTHING) { changed("widgetLocked", LockedWidget.NOTHING.id) }
                Choice(
                    "Names and events", "Same as with the lock off", mode == LockedWidget.FULL,
                    warning = "Anyone with your phone can read them without the password",
                ) { changed("widgetLocked", LockedWidget.FULL.id) }
            }

            PaperCard(padding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), gap = 0.dp) {
                SwitchRow("Show ages", "Only when names are shown", ages, { changed("widgetAges", if (it) "1" else "0") })
                DashedDivider()
                SwitchRow("Include festivals", "The festivals you switched on", festivals, { changed("widgetFestivals", if (it) "1" else "0") })
                DashedDivider()
                CardRow("Events to list", "On the large widget, when there’s room") {
                    Handwriting("next $rows", size = 24, bold = true, color = c.stamp)
                }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n -> ToggleChip("$n", rows == n, { changed("widgetRows", "$n") }, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A tiny home screen with the widget on it. */
@Composable
private fun Preview(label: String, locked: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val c = theme.c
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Grows with the text (large font sizes), never shorter than a widget.
        Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 108.dp).background(Color(0xFF2A3550)).padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().background(Color(0xFFFFFDF8)).padding(horizontal = 10.dp, vertical = 8.dp)) { content() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (locked) LineIcon(Ic.LOCK, c.ink, size = 14)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        }
    }
}

@Composable
private fun SampleNames() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Ananya, 27", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF23395B))
        Text("Rohan’s Farewell", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF23395B))
        Text("Raghav & Meera, 3d", fontSize = 12.sp, color = Color(0xFF4A5B78))
    }
}

@Composable
private fun Choice(title: String, hint: String, on: Boolean, warning: String? = null, onClick: () -> Unit) {
    val c = theme.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .then(if (on) Modifier.background(c.stampSoft).dashedBorder(c.stamp, 2.dp, radius = 4.dp) else Modifier.background(c.paper).border(1.5.dp, c.shadow, RoundedCornerShape(4.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) c.stamp else c.muted, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.stamp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = c.ink)
            Text(hint, fontSize = 13.sp, color = c.muted)
            warning?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.stampDeep) }
        }
    }
}
