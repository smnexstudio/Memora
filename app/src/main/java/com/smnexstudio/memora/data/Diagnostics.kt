package com.smnexstudio.memora.data

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A small log kept on the phone for working out why a reminder didn't arrive. It never holds
 * names, dates of birth, phone numbers or anything else about the people in Memora, only what
 * the app did ("alarm refused: limit reached"), and the user decides whether to share it.
 * It keeps the last [MAX_BYTES] and is never sent anywhere.
 */
object Diagnostics {
    private const val MAX_BYTES = 48 * 1024
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun file(context: Context) = File(context.applicationContext.filesDir, "diagnostics.log")

    /** Adds a line. Never throws: a log that can't be written must not break the thing it's describing. */
    @Synchronized
    fun note(context: Context, what: String, detail: String? = null) {
        runCatching {
            val f = file(context)
            val line = "${LocalDateTime.now().format(stamp)}  $what" + (detail?.let { ": $it" } ?: "") + "\n"
            f.appendText(line)
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2).substringAfter('\n'))
        }
    }

    @Synchronized
    fun read(context: Context): String = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    /** The most recent line starting with [what], or null. */
    fun last(context: Context, what: String): String? =
        read(context).lineSequence().lastOrNull { it.drop(21).startsWith(what) }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
