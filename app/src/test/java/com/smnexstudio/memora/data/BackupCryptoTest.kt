package com.smnexstudio.memora.data

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCryptoTest {
    private val plain = """{"app":"memora","events":[{"name":"Ananya Rao"}]}"""

    @Test fun roundTrips() {
        val sealed = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertFalse(sealed.contains("Ananya"))
        assertEquals(plain, BackupCrypto.decrypt(sealed, "correct horse"))
    }

    @Test fun wrongPasswordFails() {
        val sealed = BackupCrypto.encrypt(plain, "correct horse")
        val e = assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(sealed, "wrong horse") }
        assertTrue(e.message!!.startsWith("Wrong password"))
    }

    @Test fun changedCiphertextFails() {
        val o = JSONObject(BackupCrypto.encrypt(plain, "correct horse"))
        val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        data[3] = (data[3].toInt() xor 1).toByte()
        o.put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(o.toString(), "correct horse") }
    }

    @Test fun changedHeaderFails() {
        // Format 2 authenticates the header: swapping the IV for another fails even with the right password.
        val o = JSONObject(BackupCrypto.encrypt(plain, "correct horse"))
        o.put("iv", Base64.encodeToString(ByteArray(12).also(SecureRandom()::nextBytes), Base64.NO_WRAP))
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(o.toString(), "correct horse") }
    }

    @Test fun refusesExcessiveIterations() {
        val o = JSONObject(BackupCrypto.encrypt(plain, "correct horse")).put("iterations", 50_000_000)
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(o.toString(), "correct horse") }
    }

    @Test fun opensFormatOneBackups() {
        // A backup from before the header was authenticated: no "format" field, no associated data.
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec("old pass".toCharArray(), salt, 210_000, 256)).encoded, "AES",
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val old = JSONObject()
            .put("app", "memora").put("encrypted", 1).put("kdf", "PBKDF2WithHmacSHA256").put("iterations", 210_000)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP))
        assertEquals(plain, BackupCrypto.decrypt(old.toString(), "old pass"))
    }
}
