package com.smnexstudio.memora

import androidx.compose.runtime.LaunchedEffect
import com.smnexstudio.memora.data.Prefs
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.smnexstudio.memora.ui.Route
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.smnexstudio.memora.data.AppPin
import com.smnexstudio.memora.platform.LinkGuard
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.smnexstudio.memora.ui.HandFont
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.Skin
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
    private var themeStyle by mutableStateOf(Skin.POSTCARD.id)
    private var locked by mutableStateOf(false)
    private var pinAsked by mutableStateOf(false) // asked this time the app was opened
    private var leftAt: Long? = null
    private var authenticating = false
    private var nav: NavHostController? = null
    /** A link that arrived before navigation was set up (the process had been killed); opened once it is. */
    private var pendingLink: Intent? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LinkGuard.clean(intent)
        enableEdgeToEdge()
        themeMode = Prefs.themeMode.get()
        themeStyle = Prefs.themeStyle.get()
        locked = isLockEnabled()
        applyPrivacy()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Download festival dates if any are missing (only once festivals are switched on: the
        // download tells Google which holiday calendars were picked), then rebuild alarms so
        // one-off ones (milestones, digest, festivals) roll forward.
        thread {
            if (Prefs.festivals.get().isNotEmpty()) GoogleHolidays.ensure()
            Reminders.syncAll(applicationContext)
        }

        setContent {
            val dark = themeMode == "dark" || (themeMode == "system" && isSystemInDarkTheme())
            val skin = Skin.from(themeStyle)
            // Status and navigation bar icons follow Memora's theme, not the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MemoraTheme(dark, skin) {
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
                        MemoraTheme(dark, skin) { LockScreen(canUseLock(), onBiometric = { unlock() }, onUnlocked = { locked = false }) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LinkGuard.clean(intent)
        setIntent(intent)
        if (intent.data == null) return
        val n = nav
        if (n != null) n.handleDeepLink(intent) else pendingLink = intent
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
        Prefs.themeMode.set(mode)
        themeMode = mode
    }

    /** Postcard or Departures; see [Skin]. */
    fun setStyle(style: String) {
        Prefs.themeStyle.set(style)
        themeStyle = style
    }

    // ---- App lock ----

    // Class 3 (strong) biometrics only: weaker face unlock on some phones can be fooled by a photo.
    // Phones without one fall back to the screen lock or the Memora PIN.
    private fun authenticators() =
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun isLockEnabled() = Prefs.appLock.get()

    /** With the lock on, keep Memora out of screenshots, screen recordings and the Recents preview. */
    /** Hides Memora from screenshots and Recents while app lock is on, or when the user asks to. */
    fun applyPrivacy() {
        if (isLockEnabled() || Prefs.blockScreenshots.get()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
                Prefs.appLock.set(true)
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
        Prefs.appLock.set(false)
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
        // Runs after NavHost has set the graph, so the link can be matched against it.
        LaunchedEffect(navController) {
            pendingLink?.let { pendingLink = null; navController.handleDeepLink(it) }
        }
        val back: () -> Unit = { navController.popBackStack() }
        val go: (Route) -> Unit = { navController.navigate(it) }
        val entry by navController.currentBackStackEntryAsState()
        val tab = Tab.entries.firstOrNull { entry?.destination?.hasRoute(it.route::class) == true }

        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController, startDestination = Route.Home, Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(160)) }, exitTransition = { fadeOut(tween(120)) },
            ) {
                composable<Route.Home>(deepLinks = listOf(navDeepLink<Route.Home>(basePath = "memora://home"))) { HomeScreen(navigate = go) }
                // The locked widget's "Tap to view" lands here, on today.
                composable<Route.Calendar>(deepLinks = listOf(navDeepLink<Route.Calendar>(basePath = "memora://calendar"))) { CalendarScreen(navigate = go) }
                composable<Route.People> { PeopleScreen(navigate = go) }
                composable<Route.Settings> { SettingsScreen(activity = this@MainActivity, themeMode = themeMode, themeStyle = themeStyle, navigate = go) }
                composable<Route.Search> { SearchScreen(onBack = back, navigate = go) }
                composable<Route.Edit> { e ->
                    val r = e.toRoute<Route.Edit>()
                    EditScreen(
                        editingId = r.id.takeIf { it > 0 },
                        initialDate = r.date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                        onBack = back, navigate = go,
                    )
                }
                composable<Route.Event>(deepLinks = listOf(navDeepLink<Route.Event>(basePath = "memora://event"))) { e ->
                    EventScreen(id = e.toRoute<Route.Event>().id, onBack = back, navigate = go)
                }
                // Notifications open memora://approve/5, or memora://approve/5?channel=whatsapp.
                composable<Route.Approve>(deepLinks = listOf(navDeepLink<Route.Approve>(basePath = "memora://approve"))) { e ->
                    val r = e.toRoute<Route.Approve>()
                    ApproveScreen(id = r.id, initialChannel = r.channel, year = r.year.takeIf { it > 1900 }, belated = r.belated, onBack = back)
                }
                composable<Route.Import> { ImportScreen(onBack = back) }
                composable<Route.Festivals> { FestivalsScreen(onBack = back) }
                composable<Route.Missed> { MissedScreen(onBack = back, navigate = go) }
                composable<Route.Checks> { ReminderChecksScreen(onBack = back) }
                composable<Route.Widget> { WidgetSettingsScreen(onBack = back) }
                // The "ready to install" notification lands here.
                composable<Route.Update>(deepLinks = listOf(navDeepLink<Route.Update>(basePath = "memora://update"))) { UpdateScreen(onBack = back) }
            }
            if (tab != null) BottomNav(
                current = tab,
                onTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { go(Route.Edit()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * What the app hides behind until unlocked. Postcard: "Sealed", a postcard on navy.
 * Departures: "Locked", a panel on the night board. Both keep one look whatever the light or dark mode.
 */
@Composable
private fun LockScreen(canUseBiometric: Boolean, onBiometric: () -> Unit, onUnlocked: () -> Unit) {
    val board = memoraTheme.board
    val navy = Color(0xFF23395B)
    val paper = Color(0xFFFFFDF8)
    val red = Color(0xFFB23A2E)
    val ground = if (board) Color(0xFF121417) else navy
    val card = if (board) Color(0xFF1A1D22) else paper
    val cardInk = if (board) Color(0xFFF2EFE9) else navy
    val light = if (board) Color(0xFFF2EFE9) else paper
    val accent = if (board) Color(0xFFF2C14E) else red
    val onAccent = if (board) Color(0xFF17140A) else paper
    val soft = if (board) Color(0xFFA3A6AB) else Color(0xFFD3D8E6)
    Column(
        Modifier.fillMaxSize().background(ground)
            // Swallow touches so nothing behind the lock can be used.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .systemBarsPadding().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.width(260.dp).rotate(if (board) 0f else -2f)
                .then(if (board) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
                .background(card).drawBehind {
                    if (board) {
                        // An amber rule along the top, like the head of the board.
                        drawRect(accent, Offset.Zero, Size(size.width, 3.dp.toPx()))
                        return@drawBehind
                    }
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
                Modifier.size(48.dp, 58.dp).dashedBorder(accent, 2.dp).padding(3.dp)
                    .then(if (board) Modifier.clip(RoundedCornerShape(4.dp)) else Modifier).background(accent),
                contentAlignment = Alignment.Center,
            ) { Text("M", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = onAccent) }
            Column {
                Text(if (board) "LOCKED" else "Sealed", fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = cardInk)
                Text(
                    "Unlock to see your people, notes and wishes.", fontFamily = HandFont,
                    fontSize = (19 * memoraTheme.handScale).sp, lineHeight = (if (board) 18 else 21).sp, color = cardInk,
                )
            }
        }
        val fingerprint: @Composable () -> Unit = {
            Box(
                Modifier.size(64.dp).clip(CircleShape).dashedBorder(if (board) accent.copy(alpha = 0.6f) else Color(0xFFF2B8AE), 2.dp, radius = 32.dp)
                    .clickable(role = Role.Button, onClick = onBiometric).semantics { contentDescription = "Unlock with fingerprint or phone screen lock" },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Ic.FINGERPRINT, light, size = 30) }
        }
        if (AppPin.isSet()) {
            Text(
                if (canUseBiometric) "Enter your Memora PIN, or tap the fingerprint" else "Enter your Memora PIN",
                fontSize = 15.sp, color = soft, textAlign = TextAlign.Center,
            )
            PinEntry(
                if (board) PinColors(ink = light, muted = soft, key = Color(0x14FFFFFF), accent = accent, onAccent = onAccent)
                else PinColors(ink = paper, muted = soft, key = Color(0x1FFFFDF8), accent = Color(0xFFF2A93B), onAccent = Color(0xFF3A2600)),
                "Unlock", onUnlocked, extraKey = if (canUseBiometric) fingerprint else null,
            )
        } else {
            Spacer(Modifier.heightIn(min = 120.dp))
            fingerprint()
            Text("Touch to unlock with your fingerprint or phone PIN", fontSize = 15.sp, color = soft, textAlign = TextAlign.Center)
        }
    }
}
