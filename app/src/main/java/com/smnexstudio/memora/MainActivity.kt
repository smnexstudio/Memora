package com.smnexstudio.memora

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import android.view.WindowManager
import com.smnexstudio.memora.widget.NextEventsWidget
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.smnexstudio.memora.data.AppPin
import com.smnexstudio.memora.platform.LinkGuard
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.ui.PinColors
import com.smnexstudio.memora.ui.PinEntry
import com.smnexstudio.memora.ui.SetPinDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.MemoraTheme
import com.smnexstudio.memora.ui.BottomNav
import com.smnexstudio.memora.ui.Caveat
import com.smnexstudio.memora.ui.Fraunces
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.Tab
import com.smnexstudio.memora.ui.dashedBorder
import com.smnexstudio.memora.ui.screens.ApproveScreen
import com.smnexstudio.memora.ui.screens.CalendarScreen
import com.smnexstudio.memora.ui.screens.MissedScreen
import com.smnexstudio.memora.ui.screens.PeopleScreen
import com.smnexstudio.memora.ui.screens.ReminderChecksScreen
import com.smnexstudio.memora.ui.screens.SearchScreen
import com.smnexstudio.memora.ui.screens.UpdateScreen
import com.smnexstudio.memora.ui.screens.WidgetSettingsScreen
import com.smnexstudio.memora.ui.screens.EditScreen
import com.smnexstudio.memora.ui.screens.EventScreen
import com.smnexstudio.memora.ui.screens.FestivalsScreen
import com.smnexstudio.memora.ui.screens.HomeScreen
import com.smnexstudio.memora.ui.screens.ImportScreen
import com.smnexstudio.memora.ui.screens.SettingsScreen
import com.smnexstudio.memora.ui.theme as memoraTheme
import kotlin.concurrent.thread

/** Leaving for WhatsApp or the file picker and coming back within this window doesn't relock.
 *  Timed with the uptime clock, so changing the phone's date or time can't stretch it. */
private const val LOCK_GRACE_MS = 60_000L

class MainActivity : FragmentActivity() {
    private var themeMode by mutableStateOf("system")
    private var locked by mutableStateOf(false)
    private var pinAsked by mutableStateOf(false) // asked this time the app was opened
    private var leftAt: Long? = null
    private var authenticating = false
    private var nav: NavHostController? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LinkGuard.clean(intent)
        enableEdgeToEdge()
        themeMode = Repo.getSetting("themeMode", "system")
        locked = isLockEnabled()
        applyPrivacy()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Download festival dates if any are missing, then rebuild alarms
        // so one-off ones (milestones, digest, festivals) roll forward.
        thread {
            GoogleHolidays.ensure()
            Reminders.syncAll(applicationContext)
        }

        setContent {
            val dark = themeMode == "dark" || (themeMode == "system" && isSystemInDarkTheme())
            // Status and navigation bar icons follow Memora's theme, not the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MemoraTheme(dark) {
                Box(Modifier.fillMaxSize().background(memoraTheme.c.ground)) {
                    // Kept composed so nothing in progress is lost, but nothing under the lock can be read out.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) { AppNav() }
                    // Content scrolls under a solid strip, not under the clock and icons.
                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(memoraTheme.c.ground))
                    // Locks turned on before the Memora PIN existed have none; ask once unlocked.
                    if (!locked && !pinAsked && isLockEnabled() && !AppPin.isSet()) SetPinDialog(
                        "Set a Memora PIN",
                        onDismiss = { pinAsked = true },
                    ) { pin ->
                        pinAsked = true
                        changePin(pin) { Toast.makeText(this@MainActivity, "PIN saved", Toast.LENGTH_SHORT).show() }
                    }
                    if (locked) Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                            dismissOnBackPress = false, dismissOnClickOutside = false,
                        ),
                    ) {
                        // Back leaves the app instead of revealing it.
                        BackHandler { moveTaskToBack(true) }
                        MemoraTheme(dark) { LockScreen(canUseLock(), onBiometric = { unlock() }, onUnlocked = { locked = false }) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LinkGuard.clean(intent)
        setIntent(intent)
        if (intent.data != null) nav?.handleDeepLink(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!authenticating) leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = leftAt?.let { SystemClock.elapsedRealtime() - it }
        leftAt = null
        if (isLockEnabled() && (away == null && locked || away != null && away > LOCK_GRACE_MS)) {
            locked = true
            unlock()
        }
    }

    fun setTheme(mode: String) {
        Repo.setSetting("themeMode", mode)
        themeMode = mode
    }

    // ---- App lock ----

    private fun authenticators() =
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isLockEnabled() = Repo.getBool("appLock", false)

    /** With the lock on, keep Memora out of screenshots, screen recordings and the Recents preview. */
    private fun applyPrivacy() {
        if (isLockEnabled()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Can this phone lock the app with a fingerprint, face or screen lock PIN? */
    fun canUseLock(): Boolean {
        val bm = BiometricManager.from(this)
        if (bm.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS) return true
        val km = getSystemService(android.app.KeyguardManager::class.java)
        return km.isDeviceSecure
    }

    /** Asks for fingerprint, face or phone PIN; calls [onResult] with the outcome. */
    fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        if (authenticating) return
        authenticating = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false; onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false; onResult(false)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(authenticators()).build()
        prompt.authenticate(info)
    }

    private fun unlock() {
        when {
            canUseLock() -> authenticate("Unlock Memora") { ok -> if (ok) locked = false }
            // The lock screen asks for the Memora PIN.
            AppPin.isSet() -> Unit
            // Locks turned on before the Memora PIN existed, on a phone that has since lost its
            // fingerprint and screen lock: nothing could unlock it, so don't trap the data.
            else -> locked = false
        }
    }

    /** Turns the lock on with [pin] as Memora's own PIN, after the phone's lock confirms it's the owner. */
    fun turnOnLock(pin: String, done: (Boolean) -> Unit) {
        val finish = {
            thread {
                AppPin.set(pin)
                Repo.setBool("appLock", true)
                runOnUiThread {
                    applyPrivacy()
                    NextEventsWidget.updateAll(this)
                    done(true)
                }
            }
        }
        if (canUseLock()) authenticate("Turn on app lock") { ok -> if (ok) finish() else done(false) } else finish()
    }

    /** Asks for the phone's lock, then turns the app lock off. Without one, check the Memora PIN and call [finishTurnOff]. */
    fun turnOffLock(done: (Boolean) -> Unit) {
        authenticate("Turn off app lock") { ok ->
            if (ok) finishTurnOff()
            done(ok)
        }
    }

    fun finishTurnOff() {
        Repo.setBool("appLock", false)
        AppPin.clear()
        applyPrivacy()
        NextEventsWidget.updateAll(this)
    }

    fun changePin(pin: String, done: () -> Unit) {
        thread {
            AppPin.set(pin)
            runOnUiThread(done)
        }
    }

    // ---- Navigation ----

    @Composable
    private fun AppNav() {
        val navController = rememberNavController()
        nav = navController
        val back: () -> Unit = { navController.popBackStack() }
        val go: (String) -> Unit = { navController.navigate(it) }
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val tabRoutes = Tab.entries.map { it.route }

        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController, startDestination = "home", Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(160)) }, exitTransition = { fadeOut(tween(120)) },
            ) {
                composable("home", deepLinks = listOf(navDeepLink { uriPattern = "memora://home" })) { HomeScreen(navigate = go) }
                // The locked widget's "Tap to view" lands here, on today.
                composable("calendar", deepLinks = listOf(navDeepLink { uriPattern = "memora://calendar" })) { CalendarScreen(navigate = go) }
                composable("people") { PeopleScreen(navigate = go) }
                composable("settings") { SettingsScreen(activity = this@MainActivity, themeMode = themeMode, navigate = go) }
                composable("search") { SearchScreen(onBack = back, navigate = go) }
                composable(
                    "edit?id={id}&date={date}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { e ->
                    EditScreen(
                        editingId = e.arguments?.getLong("id")?.takeIf { it > 0 },
                        initialDate = e.arguments?.getString("date")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                        onBack = back, navigate = go,
                    )
                }
                composable(
                    "event/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "memora://event/{id}" }),
                ) { e ->
                    EventScreen(id = e.arguments?.getLong("id") ?: 0, onBack = back, navigate = go)
                }
                composable(
                    "approve/{id}?channel={channel}&year={year}&belated={belated}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("channel") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("year") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("belated") { type = NavType.BoolType; defaultValue = false },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = "memora://approve/{id}?channel={channel}" }),
                ) { e ->
                    val a = e.arguments
                    ApproveScreen(
                        id = a?.getLong("id") ?: 0,
                        initialChannel = a?.getString("channel"),
                        year = a?.getInt("year")?.takeIf { it > 1900 },
                        belated = a?.getBoolean("belated") ?: false,
                        onBack = back,
                    )
                }
                composable("import") { ImportScreen(onBack = back) }
                composable("festivals") { FestivalsScreen(onBack = back) }
                composable("missed") { MissedScreen(onBack = back, navigate = go) }
                composable("checks") { ReminderChecksScreen(onBack = back) }
                composable("widget") { WidgetSettingsScreen(onBack = back) }
                // The "ready to install" notification lands here.
                composable("update", deepLinks = listOf(navDeepLink { uriPattern = "memora://update" })) { UpdateScreen(onBack = back) }
            }
            if (route in tabRoutes) BottomNav(
                current = route,
                onTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { go("edit") },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** "Sealed": the postcard the app hides behind until unlocked. */
@Composable
private fun LockScreen(canUseBiometric: Boolean, onBiometric: () -> Unit, onUnlocked: () -> Unit) {
    val navy = Color(0xFF23395B)
    val paper = Color(0xFFFFFDF8)
    val red = Color(0xFFB23A2E)
    val soft = Color(0xFFD3D8E6)
    Column(
        Modifier.fillMaxSize().background(navy)
            // Swallow touches so nothing behind the lock can be used.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .systemBarsPadding().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.width(260.dp).rotate(-2f).background(paper).drawBehind {
                // Airmail stripe along the top edge.
                var x = 0f
                val h = 6.dp.toPx()
                val unit = 6.dp.toPx()
                while (x < size.width) {
                    drawRect(red, Offset(x, 0f), Size(2 * unit, h))
                    drawRect(navy, Offset(x + 3 * unit, 0f), Size(2 * unit, h))
                    x += 6 * unit
                }
            }.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp, 58.dp).dashedBorder(red, 2.dp).padding(3.dp).background(red),
                contentAlignment = Alignment.Center,
            ) { Text("M", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = paper) }
            Column {
                Text("Sealed", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = navy)
                Text("Unlock to see your people, notes and wishes.", fontFamily = Caveat, fontSize = 19.sp, lineHeight = 21.sp, color = navy)
            }
        }
        val fingerprint: @Composable () -> Unit = {
            Box(
                Modifier.size(64.dp).clip(CircleShape).dashedBorder(Color(0xFFF2B8AE), 2.dp, radius = 32.dp)
                    .clickable(role = Role.Button, onClick = onBiometric).semantics { contentDescription = "Unlock with fingerprint or phone screen lock" },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Ic.FINGERPRINT, paper, size = 30) }
        }
        if (AppPin.isSet()) {
            Text(
                if (canUseBiometric) "Enter your Memora PIN, or tap the fingerprint" else "Enter your Memora PIN",
                fontSize = 15.sp, color = soft, textAlign = TextAlign.Center,
            )
            PinEntry(
                PinColors(ink = paper, muted = soft, key = Color(0x1FFFFDF8), accent = Color(0xFFF2A93B), onAccent = Color(0xFF3A2600)),
                "Unlock", onUnlocked, extraKey = if (canUseBiometric) fingerprint else null,
            )
        } else {
            Spacer(Modifier.heightIn(min = 120.dp))
            fingerprint()
            Text("Touch to unlock with your fingerprint or phone PIN", fontSize = 15.sp, color = soft, textAlign = TextAlign.Center)
        }
    }
}
