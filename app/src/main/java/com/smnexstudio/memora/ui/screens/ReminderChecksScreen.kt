package com.smnexstudio.memora.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.theme

/** The three things that keep reminders on time. */
data class Checks(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean) {
    val failing get() = listOf(notifications, exactAlarms, battery).count { !it }
}

fun reminderChecks(context: Context) = Checks(
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    exactAlarms = Reminders.canScheduleExact(context),
    battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true,
)

/** Re-reads [reminderChecks] each time the screen comes back, as they change in system settings. */
@Composable
fun rememberChecks(): Pair<Checks, () -> Unit> {
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumed++ } }
    val checks = remember(resumed) { reminderChecks(context) }
    return checks to { resumed++ }
}

@Composable
fun ReminderChecksScreen(onBack: () -> Unit) {
    val c = theme.c
    val context = LocalContext.current
    val (checks, recheck) = rememberChecks()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck() }
    val appSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reminder checks", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Android can hold reminders back to save battery. Fix these so every card goes out on time.",
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 15.sp, lineHeight = 22.sp, color = c.muted,
            )
            Check("Notifications", if (checks.notifications) "Memora can show reminders" else "Turned off, so reminders can’t appear", checks.notifications, "Allow notifications") {
                // Once refused, Android won't ask again; the app's settings page is the way back.
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) appSettings()
                else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Check("Exact alarms", "Reminders fire at the time you set", checks.exactAlarms, "Allow exact alarms") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
            Check(
                if (checks.battery) "Battery use is Unrestricted" else "Battery use is Optimised",
                if (checks.battery) "Reminders won’t be held back" else "Set it to Unrestricted so reminders aren’t delayed",
                checks.battery, "Open battery settings", appSettings,
            )
            Handwriting("Reminders are rebuilt on their own after a restart, or when the time or time zone changes.", Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), size = 22)
        }
    }
}

@Composable
private fun Check(title: String, hint: String, ok: Boolean, fix: String, onFix: () -> Unit) {
    val c = theme.c
    Column(
        Modifier.fillMaxWidth().then(if (ok) Modifier.paper(c.paper, c.shadow) else Modifier.background(c.paper).dashedBorder(c.stamp, 2.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                Text(hint, fontSize = 13.sp, color = c.muted)
            }
            RubberStamp(if (ok) "Allowed" else "Fix", if (ok) c.ok else c.stamp, tilt = if (ok) -6f else -5f, fontSize = 13)
        }
        if (!ok) PostButton(fix, onFix, Modifier.fillMaxWidth(), look = Look.STAMP)
    }
}
