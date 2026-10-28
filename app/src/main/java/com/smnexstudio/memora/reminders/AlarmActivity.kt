package com.smnexstudio.memora.reminders

import com.smnexstudio.memora.data.Prefs
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.today
import com.smnexstudio.memora.ui.DisplayFont
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Ic
import com.smnexstudio.memora.ui.LineIcon
import com.smnexstudio.memora.ui.LocalMemora
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.MemoraTheme
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.Skin
import com.smnexstudio.memora.ui.clock
import java.lang.ref.WeakReference
import java.time.LocalTime

/**
 * The full-screen alarm, shown over the lock screen when reminders ring. The sound comes from the
 * alarm notification ([ringAlarm]); stopping here cancels it. With app lock on it shows no names.
 */
class AlarmActivity : ComponentActivity() {
    companion object {
        private var showing: WeakReference<AlarmActivity>? = null

        /** Closes the alarm screen if it's up, e.g. after Stop was tapped on the notification. */
        fun closeShowing() {
            showing?.get()?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        showing = WeakReference(this)
        Repo.init(this)

        val ids = intent.getLongArrayExtra(Reminders.EXTRA_EVENT_IDS) ?: LongArray(0)
        val offsets = intent.getIntArrayExtra(Reminders.EXTRA_OFFSETS) ?: IntArray(0)
        val due = ids.zip(offsets.toTypedArray()).mapNotNull { (id, o) -> Repo.getEvent(id)?.let { it to o } }
        if (due.isEmpty()) {
            stopAlarm(this, ids, offsets, snooze = false)
            return
        }
        // Over the lock screen, names show only if the user allows it (and never with app lock on).
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        val locked = Prefs.appLock.get() || (Prefs.hideOnLockScreen.get() && keyguard?.isKeyguardLocked == true)
        val mode = Prefs.themeMode.get()
        val nightNow = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = mode == "dark" || (mode == "system" && nightNow)
        val skin = Skin.from(Prefs.themeStyle.get())
        val now = LocalTime.now()
        val single = due.singleOrNull()
        val todayDue = due.filter { it.second == 0 && it.first.lastSentYear != today().year }

        setContent {
            MemoraTheme(dark, skin) {
                val t = LocalMemora.current
                val c = t.c
                fun caps(s: String) = if (t.board) s.uppercase() else s
                Column(
                    Modifier.fillMaxSize().background(c.ground).systemBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    LineIcon(Ic.BELL, c.stamp, size = 44)
                    Text(clock(now.hour, now.minute), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, color = c.ink)
                    Text(
                        caps(if (locked) "You have a reminder" else single?.let { alarmTitle(it.first, it.second) } ?: "${due.size} reminders"),
                        fontFamily = DisplayFont, fontStyle = if (t.board) FontStyle.Normal else FontStyle.Italic,
                        fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, color = c.ink, textAlign = TextAlign.Center,
                    )
                    if (!locked) {
                        if (single != null) Handwriting(
                            if (single.second == 0) "Your card is ready to review." else "Your card will be ready on the day.",
                            size = 22, color = c.muted,
                        ) else due.forEach { (e, o) ->
                            Text(alarmTitle(e, o), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.muted, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // One person due today: straight to their card. Otherwise open the app.
                    val open = when {
                        locked -> "home"
                        single != null && todayDue.size == 1 -> "approve/${single.first.id}"
                        single != null -> "event/${single.first.id}"
                        else -> "home"
                    }
                    PostButton(
                        when {
                            locked || open == "home" -> "Open Memora"
                            open.startsWith("approve") -> "Send card"
                            else -> "See details"
                        },
                        { stopAlarm(this@AlarmActivity, ids, offsets, snooze = false); openAppIntent(this@AlarmActivity, open, ALARM_NOTIFICATION_ID + 3).send() },
                        Modifier.fillMaxWidth(), look = Look.STAMP, height = 56.dp, icon = if (open.startsWith("approve")) Ic.MAIL else null,
                    )
                    if (due.any { it.second == 0 }) PostButton(
                        "Remind in ${Reminders.SNOOZE_HOURS}h", { stopAlarm(this@AlarmActivity, ids, offsets, snooze = true) },
                        Modifier.fillMaxWidth(), look = Look.OUTLINE, height = 52.dp,
                    )
                    PostButton("Stop", { stopAlarm(this@AlarmActivity, ids, offsets, snooze = false) }, Modifier.fillMaxWidth(), look = Look.GHOST, height = 52.dp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    override fun onDestroy() {
        if (showing?.get() === this) showing = null
        super.onDestroy()
    }
}
