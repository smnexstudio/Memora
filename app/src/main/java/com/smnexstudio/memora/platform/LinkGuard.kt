package com.smnexstudio.memora.platform

import android.content.Intent
import com.smnexstudio.memora.data.Repo
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
        val saved = Repo.getSetting("linkToken", "")
        if (saved.isNotEmpty()) return saved
        val fresh = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        Repo.setSetting("linkToken", fresh)
        return fresh
    }

    /** Marks an intent Memora builds itself, before it goes into a PendingIntent. */
    fun stamp(intent: Intent): Intent = intent.putExtra(EXTRA, token())

    /** Drops the screen link from an intent that didn't come from Memora. Returns the same intent. */
    fun clean(intent: Intent): Intent {
        if (intent.data != null && intent.getStringExtra(EXTRA) != token()) intent.data = null
        intent.removeExtra(EXTRA)
        return intent
    }
}
