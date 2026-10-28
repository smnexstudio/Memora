package com.smnexstudio.memora.ui.screens

import androidx.core.app.NotificationManagerCompat
import com.smnexstudio.memora.data.Diagnostics
import com.smnexstudio.memora.platform.Send
import com.smnexstudio.memora.ui.Route
import com.smnexstudio.memora.ui.festivalVersion
import com.smnexstudio.memora.data.Prefs
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.reminders.AlertStyle
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.smnexstudio.memora.data.AppPin
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.smnexstudio.memora.data.BackupCrypto
import com.smnexstudio.memora.ui.SetPinDialog
import com.smnexstudio.memora.ui.VerifyPinDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.smnexstudio.memora.MainActivity
import com.smnexstudio.memora.data.OFFSET_OPTIONS
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.availableFestivals
import com.smnexstudio.memora.logic.calendarsLabel
import com.smnexstudio.memora.platform.CheckResult
import com.smnexstudio.memora.platform.Updates
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.CardRow
import com.smnexstudio.memora.ui.Chevron
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.NavSpacer
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.SectionTitle
import com.smnexstudio.memora.ui.Skin
import com.smnexstudio.memora.ui.SwitchRow
import com.smnexstudio.memora.ui.TabTitle
import com.smnexstudio.memora.ui.TimeSheet
import com.smnexstudio.memora.ui.UnderlineField
import com.smnexstudio.memora.ui.ToggleChip
import com.smnexstudio.memora.ui.WeightedChip
import com.smnexstudio.memora.ui.clock
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

@Composable
fun SettingsScreen(activity: MainActivity, themeMode: String, themeStyle: String, navigate: (Route) -> Unit) {
    val c = theme.c
    val context = LocalContext.current
    val app = context.applicationContext
    val settings by Repo.settings.collectAsState()
    val offsets = Prefs.defaultOffsets.from(settings)
    val hour = Prefs.reminderHour.from(settings)
    val minute = Prefs.reminderMinute.from(settings)
    val digest = Prefs.weeklyDigest.from(settings)
    val alertStyle = Prefs.alertStyle.from(settings)
    // Re-read when coming back from Android's settings page for full-screen alarms.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState().value
    val fullScreenOk = remember(resumed) { Reminders.canShowFullScreen(context) }
    val festivalIds = Prefs.festivals.from(settings)
    val festivalCount = remember(festivalIds, festivalVersion()) { availableFestivals().count { it.id in festivalIds } }
    val calendars = calendarsLabel(Prefs.calendars.from(settings))
    val lock = Prefs.appLock.from(settings)
    val lastBackup = Prefs.lastBackupAt.from(settings)
    val eventCount = Repo.events.collectAsState().value.size
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    var signature by remember { mutableStateOf(Prefs.signature.get()) }
    var pinDialog by remember { mutableStateOf<PinStep?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val (checks, _) = rememberChecks()
    val appVersion = remember { Updates.installedVersion(context) }
    val update = remember(settings) { Updates.available(context) }
    val updateChecked = Prefs.updateCheckedAt.from(settings)
    val justUpdated = remember(settings) { Updates.justInstalled(context) }
    val autoUpdate = Prefs.updateAuto.from(settings)
    var checking by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") } // held until the file is chosen
    var lockedBackup by remember { mutableStateOf<String?>(null) } // a protected file waiting for its password
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val password = backupPassword
        backupPassword = ""
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = Repo.exportJson()
                    val out = if (password.isNotEmpty()) BackupCrypto.encrypt(json, password) else json
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(out.toByteArray(Charsets.UTF_8)) }
                }
                Prefs.lastBackupAt.set(System.currentTimeMillis())
                Toast.makeText(context, if (password.isNotEmpty()) "Backup saved with a password" else "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            busy = false
        }
    }

    /** Empties Memora: alarms, database, card pictures, diagnostics and notifications; settings go back to their defaults. */
    fun eraseEverything() {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                Repo.listEvents().forEach { Reminders.cancelEvent(app, it.id) }
                Repo.eraseAll()
                Reminders.syncAll(app)
                Send.clearAllCards(app)
                Diagnostics.clear(app)
            }
            NotificationManagerCompat.from(app).cancelAll()
            activity.finishTurnOff()
            activity.setTheme(Prefs.themeMode.get())
            activity.setStyle(Prefs.themeStyle.get())
            signature = ""
            busy = false
            Toast.makeText(context, "Everything in Memora has been erased.", Toast.LENGTH_LONG).show()
        }
    }

    fun restore(text: String) {
        val n = Repo.importJson(text)
        activity.setTheme(Prefs.themeMode.get())
        activity.setStyle(Prefs.themeStyle.get())
        thread { GoogleHolidays.ensure(force = true); Reminders.syncAll(app) }
        Toast.makeText(context, "$n event${if (n == 1) "" else "s"} restored and reminders set up again.", Toast.LENGTH_LONG).show()
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            // Backups are small; refuse anything big enough to run the phone out of memory.
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_BACKUP_BYTES + 1) }
            require(bytes.size <= MAX_BACKUP_BYTES) { "This file is too big to be a Memora backup." }
            val text = bytes.toString(Charsets.UTF_8)
            if (BackupCrypto.isEncrypted(text)) lockedBackup = text else restore(text)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn’t restore", Toast.LENGTH_LONG).show()
        }
    }

    if (pickTime) TimeSheet("Reminder time", "For on-the-day and early reminders", hour, minute, { pickTime = false }) { h, m ->
        pickTime = false
        Prefs.reminderHour.set(h); Prefs.reminderMinute.set(m)
        thread { Reminders.syncAll(app) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabTitle("Settings", Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            update?.let { u ->
                Row(
                    Modifier.fillMaxWidth().background(c.festivalBg).dashedBorder(c.festival, 2.dp).clickable(role = Role.Button) { navigate(Route.Update) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Stamp(u.version.substringBeforeLast('.'), c.festival, filled = true, width = 40.dp, height = 48.dp, fontSize = 12)
                    Column(Modifier.weight(1f)) {
                        Text("Memora ${u.version} is ready", fontWeight = FontWeight.Bold, color = c.festivalInk)
                        Text("See what’s new. ${megabytes(u.apkSize)}", fontSize = 13.sp, color = c.festivalInk)
                    }
                    Chevron()
                }
            }

            if (checks.failing > 0) Row(
                Modifier.fillMaxWidth().background(c.paper).dashedBorder(c.stamp, 2.dp).clickable(role = Role.Button) { navigate(Route.Checks) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.stamp), contentAlignment = Alignment.Center) { LineIcon(Ic.ALERT, c.onStamp, size = 20) }
                Column(Modifier.weight(1f)) {
                    Text("Reminders may arrive late", fontWeight = FontWeight.Bold, color = c.stampDeep)
                    Text(
                        if (checks.failing == 1) "One of three checks needs fixing" else "${checks.failing} of three checks need fixing",
                        fontSize = 13.sp, color = c.muted,
                    )
                }
                Chevron()
            }

            Section("Reminders") {
                CardRow("Reminder time", "For on-the-day and early reminders", onClick = { pickTime = true }) { Handwriting(clock(hour, minute).lowercase(), size = 26, bold = true, color = c.stamp) }
                DashedDivider()
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How to remind", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertStyle.entries.forEach { s ->
                            WeightedChip(s.label, alertStyle == s, { Prefs.alertStyle.set(s) })
                        }
                    }
                    Text(alertStyle.hint + if (alertStyle.rings) " Festivals and the weekly digest stay as notifications." else "", fontSize = 13.sp, color = c.muted)
                    if (alertStyle.rings && !fullScreenOk) LinkText(
                        "Allow alarms to fill the screen",
                        {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                            )
                        },
                        color = c.stamp,
                    )
                }
                DashedDivider()
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Early reminders for new events", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OFFSET_OPTIONS.forEach { (days, label) ->
                            val on = days in offsets
                            ToggleChip(label, on, {
                                val next = when {
                                    on && offsets.size > 1 -> offsets - days
                                    !on && offsets.size < 2 -> (offsets + days).sortedDescending()
                                    else -> offsets
                                }
                                Prefs.defaultOffsets.set(next)
                            }, enabled = on || offsets.size < 2)
                        }
                    }
                    Text("Pick 1–2. Milestone birthdays and anniversaries also get one 2 weeks before.", fontSize = 13.sp, color = c.muted)
                }
                DashedDivider()
                SwitchRow("Weekly digest", "A Sunday note about the week ahead", digest, {
                    Prefs.weeklyDigest.set(it)
                    thread { Reminders.syncAll(app) }
                })
                DashedDivider()
                CardRow(
                    "Festivals", "$calendars, ${if (festivalCount == 0) "none switched on" else "$festivalCount switched on"}",
                    onClick = { navigate(Route.Festivals) },
                ) { Chevron() }
            }

            Section("Appearance", padded = true) {
                Text("Theme", fontWeight = FontWeight.SemiBold, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skin.entries.forEach { skin ->
                        WeightedChip(skin.label, themeStyle == skin.id, { activity.setStyle(skin.id) })
                    }
                }
                Text("Mode", Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                        WeightedChip(label, themeMode == mode, { activity.setTheme(mode) })
                    }
                }
            }

            Section("Home-screen widget", padded = true) {
                CardRow("Widget", widgetSummary(lock), onClick = { navigate(Route.Widget) }) { Chevron() }
            }

            Section("Privacy") {
                SwitchRow(
                    "Hide names on the lock screen",
                    if (lock) "Always, while app lock is on" else "Reminders say “You have a reminder” until you unlock",
                    lock || Prefs.hideOnLockScreen.from(settings), { Prefs.hideOnLockScreen.set(it) }, enabled = !lock,
                )
                DashedDivider()
                SwitchRow(
                    "Hide from screenshots",
                    if (lock) "Always, while app lock is on" else "Also keeps Memora's screen out of Recents and screen recordings",
                    lock || Prefs.blockScreenshots.from(settings), { Prefs.blockScreenshots.set(it); activity.applyPrivacy() }, enabled = !lock,
                )
                DashedDivider()
                SwitchRow("App lock", "Fingerprint, phone screen lock or your Memora PIN", lock, { on ->
                    when {
                        on -> pinDialog = PinStep.SET_FOR_LOCK
                        activity.canUseLock() -> activity.turnOffLock {}
                        AppPin.isSet() -> pinDialog = PinStep.VERIFY_FOR_OFF
                        else -> activity.finishTurnOff()
                    }
                })
                if (lock) {
                    DashedDivider()
                    CardRow(
                        "Memora PIN",
                        if (AppPin.isSet()) "Opens the app even without a fingerprint or screen lock" else "Not set. Add one so you can always get in.",
                        onClick = { pinDialog = if (AppPin.isSet()) PinStep.VERIFY_FOR_CHANGE else PinStep.SET_NEW },
                    ) { Text(if (AppPin.isSet()) "Change" else "Set", fontWeight = FontWeight.Bold, color = c.stamp) }
                }
                DashedDivider()
                Handwriting("Everything stays on this phone. Memora only goes online to fetch festival dates and check for updates.", Modifier.padding(vertical = 14.dp), size = 21)
            }

            Section("Cards", padded = true) {
                UnderlineField("Sign cards as", signature, { signature = it.take(40); Prefs.signature.set(signature.trim()) }, placeholder = "Your name or initials, e.g. SM")
                Text("Shown as “— ${signature.trim().ifBlank { "SM" }}” at the end of postcard wishes.", fontSize = 13.sp, color = c.muted)
                LinkText("Delete cards saved to the gallery", {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { Send.deleteSavedCards(app) }
                        Toast.makeText(context, if (n == 0) "No saved cards to delete" else "Deleted $n saved card${if (n == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            Section("Your people", padded = true) {
                CardRow("Import from contacts", "Birthdays and anniversaries saved on your phone", onClick = { navigate(Route.Import) }) { Chevron() }
            }

            Section("Backup and restore", padded = true) {
                Text(
                    lastBackup?.let {
                        val d: LocalDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        "Last backup ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}, $eventCount event${if (eventCount == 1) "" else "s"} now"
                    } ?: "No backup yet. $eventCount event${if (eventCount == 1) "" else "s"} on this phone.",
                    fontSize = 13.sp, color = c.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostButton(if (busy) "Working…" else "Back up now", { askBackupPassword = true }, Modifier.weight(1f), enabled = !busy)
                    PostButton("Restore", { confirmRestore = true }, Modifier.weight(1f), look = Look.OUTLINE)
                }
                Text("Save it to Drive or Downloads. Backups from Memora 1.x restore too.", fontSize = 13.sp, color = c.muted)
                LinkText("Erase everything", { confirmErase = true }, color = c.stamp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("About Memora", Modifier.padding(horizontal = 4.dp))
                PaperCard(padding = PaddingValues(horizontal = 16.dp), gap = 0.dp) {
                    CardRow("Version", "Birthday & anniversary reminders") { Handwriting(appVersion, size = 26, bold = true, color = c.stamp) }
                    DashedDivider()
                    justUpdated?.let { v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LineIcon(Ic.CHECK, c.ok, size = 20)
                            Text("Updated to $v. Everything came along.", Modifier.weight(1f), fontSize = 14.sp, color = c.ink)
                            RubberStamp("Delivered", c.ok, tilt = 5f, fontSize = 11)
                        }
                        DashedDivider()
                    }
                    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Updates", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                                Text(
                                    when {
                                        checking -> "Asking GitHub…"
                                        updateChecked == null -> "Not checked yet"
                                        else -> "Last checked ${checkedLabel(updateChecked)}"
                                    },
                                    fontSize = 13.sp, color = c.muted,
                                )
                            }
                            when {
                                update != null -> RubberStamp("${update.version} ready", c.festival, tilt = -5f)
                                updateChecked != null && !checking -> RubberStamp("Up to date", c.ok, tilt = 4f)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (update != null) PostButton("See what’s new", { navigate(Route.Update) }, Modifier.weight(1f), height = 46.dp)
                            PostButton(
                                if (checking) "Checking…" else if (update != null) "Check again" else "Check for updates",
                                {
                                    checking = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { Updates.check(app) }
                                        checking = false
                                        when (result) {
                                            CheckResult.FAILED -> Toast.makeText(context, "Couldn’t reach GitHub. Check the connection and try again.", Toast.LENGTH_LONG).show()
                                            CheckResult.LATEST -> Toast.makeText(context, "You have the latest version", Toast.LENGTH_SHORT).show()
                                            CheckResult.NEWER -> if (Updates.available(app) != null) navigate(Route.Update)
                                        }
                                    }
                                },
                                Modifier.weight(1f), look = Look.OUTLINE, height = 46.dp, enabled = !checking, icon = Ic.REFRESH,
                            )
                        }
                    }
                    DashedDivider()
                    SwitchRow("Check automatically", "Once a week, when you open Memora", autoUpdate, { Updates.setAutoCheck(it) })
                }
                Handwriting("Memora asks GitHub if there’s a newer release. Nothing about you or your people is sent.", Modifier.padding(horizontal = 4.dp), size = 21)
            }
        }
        NavSpacer()
    }

    when (pinDialog) {
        PinStep.SET_FOR_LOCK -> SetPinDialog("Set a Memora PIN", onDismiss = { pinDialog = null }) { pin ->
            pinDialog = null
            activity.turnOnLock(pin) { ok -> if (ok) Toast.makeText(context, "App lock is on", Toast.LENGTH_SHORT).show() }
        }
        PinStep.SET_NEW -> SetPinDialog("New Memora PIN", onDismiss = { pinDialog = null }) { pin ->
            pinDialog = null
            activity.changePin(pin) { Toast.makeText(context, "PIN saved", Toast.LENGTH_SHORT).show() }
        }
        PinStep.VERIFY_FOR_CHANGE -> VerifyPinDialog("Current PIN", onDismiss = { pinDialog = null }) { pinDialog = PinStep.SET_NEW }
        PinStep.VERIFY_FOR_OFF -> VerifyPinDialog("Enter your PIN to turn off the lock", onDismiss = { pinDialog = null }) {
            pinDialog = null
            activity.finishTurnOff()
        }
        null -> Unit
    }

    if (askBackupPassword) PasswordDialog(
        title = "Protect the backup?",
        text = "The backup has every name, number and note. With a password, nobody who finds the file can read it. You’ll need the password to restore.",
        confirm = "Back up",
        confirmTwice = true,
        allowEmpty = true,
        // Without a password is a separate, deliberate choice, not what an empty field quietly does.
        minLength = MIN_BACKUP_PASSWORD,
        onDismiss = { askBackupPassword = false },
    ) { pw ->
        askBackupPassword = false
        backupPassword = pw
        exportLauncher.launch("memora-backup-${LocalDate.now()}.json")
    }

    lockedBackup?.let { text ->
        PasswordDialog(
            title = "Backup password",
            text = "This backup is protected. Enter the password it was saved with.",
            confirm = "Restore",
            onDismiss = { lockedBackup = null },
        ) { pw ->
            busy = true
            scope.launch {
                try {
                    val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(text, pw) }
                    lockedBackup = null
                    restore(plain)
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Couldn’t restore", Toast.LENGTH_LONG).show()
                }
                busy = false
            }
        }
    }

    if (confirmErase) AlertDialog(
        onDismissRequest = { confirmErase = false },
        containerColor = c.paper,
        title = { Text("Erase everything?", color = c.ink) },
        text = {
            Text(
                "Every person, note, card history and setting in Memora on this phone, and the PIN, will be deleted. This can’t be undone. Back up first if you might want them again.",
                color = c.muted,
            )
        },
        confirmButton = {
            TextButton({ confirmErase = false; eraseEverything() }) { Text("Erase", fontWeight = FontWeight.Bold, color = c.stamp) }
        },
        dismissButton = { TextButton({ confirmErase = false }) { Text("Cancel", color = c.muted) } },
    )

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        containerColor = c.paper,
        title = { Text("Restore from backup?", color = c.ink) },
        text = { Text("Everything in Memora on this phone will be replaced with the backup.", color = c.muted) },
        confirmButton = {
            TextButton({ confirmRestore = false; restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Choose file", fontWeight = FontWeight.Bold, color = c.ink)
            }
        },
        dismissButton = { TextButton({ confirmRestore = false }) { Text("Cancel", color = c.muted) } },
    )
}

@Composable
private fun Section(title: String, padded: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = 4.dp))
        PaperCard(padding = if (padded) PaddingValues(16.dp) else PaddingValues(horizontal = 16.dp), gap = if (padded) 10.dp else 0.dp) { content() }
    }
}

/** "today, 9:41 AM", "yesterday" or "3 October". */
private fun checkedLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val d = t.toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "today, ${clock(t.hour, t.minute)}"
        today.minusDays(1) -> "yesterday"
        else -> "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" + if (d.year != today.year) " ${d.year}" else ""
    }
}

private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
private const val MIN_BACKUP_PASSWORD = 8

/** Reads at most [limit] bytes. */
private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

@Composable
private fun PasswordDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    confirmTwice: Boolean = false,
    allowEmpty: Boolean = false,
    minLength: Int = 1,
    onConfirm: (String) -> Unit,
) {
    val c = theme.c
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = confirmTwice && pw.isNotEmpty() && again != pw
    val tooShort = pw.isNotEmpty() && pw.length < minLength
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = c.paper,
        title = { Text(title, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, fontSize = 14.sp, color = c.muted)
                PasswordField("Password", pw) { pw = it }
                if (confirmTwice && pw.isNotEmpty()) PasswordField("Type it again", again) { again = it }
                if (tooShort) Text("Use at least $minLength characters.", fontSize = 13.sp, color = c.stamp)
                if (mismatch && again.isNotEmpty()) Text("The passwords don’t match.", fontSize = 13.sp, color = c.stamp)
                if (allowEmpty) Text("Anyone who gets a backup without a password, on a shared drive or cloud folder say, can read all of it.", fontSize = 13.sp, color = c.muted)
            }
        },
        confirmButton = {
            TextButton({ onConfirm(pw) }, enabled = pw.isNotEmpty() && !tooShort && !(confirmTwice && again != pw)) {
                Text(confirm, fontWeight = FontWeight.Bold, color = c.ink)
            }
        },
        dismissButton = {
            Row {
                if (allowEmpty) TextButton({ onConfirm("") }) { Text("No password", color = c.muted) }
                TextButton(onDismiss) { Text("Cancel", color = c.muted) }
            }
        },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    val c = theme.c
    BasicTextField(
        value, onChange, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(fontSize = 16.sp, color = c.ink),
        cursorBrush = SolidColor(c.stamp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .border(1.5.dp, c.faint, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 13.dp)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(label, fontSize = 16.sp, color = c.faint)
                inner()
            }
        },
    )
}

private enum class PinStep { SET_FOR_LOCK, SET_NEW, VERIFY_FOR_CHANGE, VERIFY_FOR_OFF }
