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
 *
 * Format 2 also authenticates the header (KDF, iterations, salt, IV) as GCM associated data, so
 * an edited header fails like a wrong password. Format 1 files (no "format" field) still open.
 */
object BackupCrypto {
    private const val ITERATIONS = 210_000
    private const val FORMAT = 2
    /** A file can't ask for more work than this, so a hostile one can't stall the phone for long. */
    private const val MAX_ITERATIONS = 1_000_000

    private fun header(kdf: String, iterations: Int, salt: String, iv: String) =
        "memora-backup|$FORMAT|$kdf|$iterations|$salt|$iv".toByteArray(Charsets.UTF_8)

    fun isEncrypted(text: String): Boolean = try {
        JSONObject(text.removePrefix("\uFEFF")).optInt("encrypted") == 1
    } catch (_: Exception) {
        false
    }

    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val kdf = "PBKDF2WithHmacSHA256"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(header(kdf, ITERATIONS, b64(salt), b64(iv)))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "memora").put("encrypted", 1).put("format", FORMAT)
            .put("kdf", kdf).put("iterations", ITERATIONS)
            .put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(data))
            .toString(2)
    }

    /** Throws [IllegalArgumentException] with a readable message for a wrong password or a damaged file. */
    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text.removePrefix("\uFEFF"))
        val iterations = o.optInt("iterations", ITERATIONS)
        if (iterations !in 10_000..MAX_ITERATIONS) throw IllegalArgumentException("This backup file is damaged.")
        return try {
            val salt = o.getString("salt")
            val iv = o.getString("iv")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, unb64(salt), iterations), GCMParameterSpec(128, unb64(iv)))
            if (o.optInt("format", 1) >= 2) cipher.updateAAD(header(o.getString("kdf"), iterations, salt, iv))
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
