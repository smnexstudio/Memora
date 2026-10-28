package com.smnexstudio.memora.platform

import com.smnexstudio.memora.data.Prefs
import android.content.Intent
import java.security.SecureRandom

/**
 * MainActivity has to be open to other apps (the launcher starts it), so any app could start it
 * pointed at a screen such as "memora://approve/5". Memora's own notifications and widget stamp
 * their intents with a random token known only to this install; links without it are dropped.
 */
object LinkGuard {
    private const val EXTRA = "com.smnexstudio.memora.LINK_TOKEN"

    @Synchronized
    private fun token(): String {
        val saved = Prefs.linkToken.get()
        if (saved.isNotEmpty()) return saved
        val fresh = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        Prefs.linkToken.set(fresh)
        return fresh
    }

    /** Marks an intent Memora builds itself, before it goes into a PendingIntent. */
    fun stamp(intent: Intent): Intent = intent.putExtra(EXTRA, token())

    /**
     * Drops the screen link from an intent that didn't come from Memora. Returns the same intent.
     *
     * A kept link also loses FLAG_ACTIVITY_NEW_TASK. Navigation restarts the activity for a link with
     * that flag, and the restarted intent no longer carries the token removed here, so the link would
     * then be dropped and the app would open on Home instead of the screen the notification named.
     */
    fun clean(intent: Intent): Intent {
        if (intent.data != null && intent.getStringExtra(EXTRA) != token()) intent.data = null
        if (intent.data != null) intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
        intent.removeExtra(EXTRA)
        return intent
    }
}
