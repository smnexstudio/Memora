package com.smnexstudio.memora.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Memora's own PIN for the app lock. It always unlocks the app, and it is the only way in
 * when the phone no longer has a fingerprint, face or screen lock.
 *
 * Only a salted PBKDF2 hash is stored, never the PIN, and it is left out of backups.
 * After [FREE_TRIES] wrong tries each further wrong one makes the next try wait longer.
 */
object AppPin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    private const val ITERATIONS = 120_000
    private const val FREE_TRIES = 5

    sealed interface Check {
        data object Ok : Check
        data class Wrong(val triesBeforeWait: Int) : Check
        data class Wait(val seconds: Long) : Check
    }

    fun isSet(): Boolean = Repo.getSetting("appPinHash", "").isNotEmpty()

    fun valid(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all(Char::isDigit)

    fun set(pin: String) {
        require(valid(pin))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        Repo.setSetting("appPinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
        Repo.setSetting("appPinHash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
        resetFailures()
    }

    fun clear() {
        Repo.setSetting("appPinHash", "")
        Repo.setSetting("appPinSalt", "")
        resetFailures()
    }

    /** Seconds until another try is allowed, or 0. */
    fun waitSeconds(): Long {
        val left = (Repo.getSetting("pinLockUntil", "0").toLongOrNull() ?: 0) - System.currentTimeMillis()
        return if (left <= 0) 0 else (left + 999) / 1000
    }

    /** Slow on purpose (PBKDF2); call off the main thread. */
    fun check(pin: String): Check {
        waitSeconds().takeIf { it > 0 }?.let { return Check.Wait(it) }
        val salt = Base64.decode(Repo.getSetting("appPinSalt", ""), Base64.NO_WRAP)
        val stored = Base64.decode(Repo.getSetting("appPinHash", ""), Base64.NO_WRAP)
        if (stored.isNotEmpty() && MessageDigest.isEqual(hash(pin, salt), stored)) {
            resetFailures()
            return Check.Ok
        }
        val fails = (Repo.getSetting("pinFails", "0").toIntOrNull() ?: 0) + 1
        Repo.setSetting("pinFails", fails.toString())
        if (fails < FREE_TRIES) return Check.Wrong(FREE_TRIES - fails)
        // 30 s after the 5th wrong try, doubling each time, up to an hour.
        val seconds = (30L shl (fails - FREE_TRIES).coerceAtMost(7)).coerceAtMost(3600)
        Repo.setSetting("pinLockUntil", (System.currentTimeMillis() + seconds * 1000).toString())
        return Check.Wait(seconds)
    }

    private fun resetFailures() {
        Repo.setSetting("pinFails", "0")
        Repo.setSetting("pinLockUntil", "0")
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
