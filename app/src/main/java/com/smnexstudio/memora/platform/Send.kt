package com.smnexstudio.memora.platform

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Formats a phone number for wa.me; 10 digits without a country code are treated as Indian (+91). */
private fun waNumber(phone: String): String {
    val trimmed = phone.trim()
    val digits = trimmed.replace(Regex("\\D"), "")
    if (trimmed.startsWith("+")) return digits
    return if (digits.length == 10) "91$digits" else digits
}

/** Each opens the app with the message filled in; the user taps Send there. */
object Send {
    fun whatsApp(context: Context, phone: String, message: String) {
        val number = waNumber(phone)
        require(number.length >= 7) { "Please enter a valid phone number with country code (e.g. +91 9876543210)." }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}"))
        start(context, intent, "WhatsApp could not be opened. Make sure WhatsApp is installed.")
    }

    fun sms(context: Context, phone: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).putExtra("sms_body", message)
        start(context, intent, "This phone can’t send SMS.")
    }

    fun email(context: Context, address: String, subject: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        start(context, intent, "No email app is set up on this phone. Add an account in Gmail and try again.")
    }

    // ---- Card pictures ----

    /** Installed WhatsApp, preferring the personal app over Business. */
    fun whatsAppPackage(context: Context): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
        try {
            context.packageManager.getPackageInfo(it, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Deletes card pictures older than a day; the app they went to has long since read them. */
    fun clearOldCards(context: Context) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        File(context.cacheDir, "cards").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    /** Writes the card to the app's cache so other apps can read it. */
    fun cardUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "cards").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "memora-card-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.cards", file)
    }

    /**
     * Opens WhatsApp with the picture ready. With [phone] it goes straight to that chat;
     * without, WhatsApp asks where to send it, with My status at the top.
     */
    fun whatsAppImage(context: Context, phone: String?, image: Uri, caption: String) {
        val pkg = whatsAppPackage(context) ?: throw IllegalStateException("WhatsApp isn’t installed on this phone.")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            setPackage(pkg)
            putExtra(Intent.EXTRA_STREAM, image)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            phone?.let {
                val number = waNumber(it)
                require(number.length >= 7) { "Please enter a valid phone number with country code (e.g. +91 9876543210)." }
                putExtra("jid", "$number@s.whatsapp.net")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, intent, "WhatsApp could not be opened.")
    }

    /** Opens an email app with the card attached and the wish typed below. */
    fun emailImage(context: Context, address: String, subject: String, message: String, image: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(intent, "Send the card with"), "No email app is set up on this phone. Add an account in Gmail and try again.")
    }

    /** Saves the card to Pictures/Memora. */
    fun saveToGallery(context: Context, bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Memora")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Deletes the cards Memora saved to Pictures/Memora. Returns how many went. Android lets an app delete its own pictures. */
    fun deleteSavedCards(context: Context): Int = runCatching {
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?",
            arrayOf("Pictures/Memora%", context.packageName),
        )
    }.getOrDefault(0)

    /** Deletes every card picture waiting in the cache. */
    fun clearAllCards(context: Context) {
        File(context.cacheDir, "cards").deleteRecursively()
    }

    private fun start(context: Context, intent: Intent, error: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            throw IllegalStateException(error)
        }
    }
}
