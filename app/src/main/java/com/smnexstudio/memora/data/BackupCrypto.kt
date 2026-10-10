package com.smnexstudio.memora.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files: AES-256-GCM with a key from PBKDF2-SHA256.
 * The file stays JSON ({"app":"memora","encrypted":1,…}) so it is still recognised as a Memora backup.
 */
object BackupCrypto {
    private const val ITERATIONS = 210_000

    fun isEncrypted(text: String): Boolean = try {
        JSONObject(text.removePrefix("\uFEFF")).optInt("encrypted") == 1
    } catch (_: Exception) {
        false
    }

    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "memora").put("encrypted", 1)
            .put("kdf", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS)
            .put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(data))
            .toString(2)
    }

    /** Throws [IllegalArgumentException] with a readable message for a wrong password or a damaged file. */
    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text.removePrefix("\uFEFF"))
        val iterations = o.optInt("iterations", ITERATIONS).coerceIn(10_000, 5_000_000)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, unb64(o.getString("salt")), iterations), GCMParameterSpec(128, unb64(o.getString("iv"))))
            String(cipher.doFinal(unb64(o.getString("data"))), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wrong password, or the file was changed.")
        } catch (_: Exception) {
            throw IllegalArgumentException("This backup file is damaged.")
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
