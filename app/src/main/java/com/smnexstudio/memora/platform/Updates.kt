package com.smnexstudio.memora.platform

import com.smnexstudio.memora.data.Prefs
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smnexstudio.memora.R
import com.smnexstudio.memora.reminders.openAppIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/** A Memora release on GitHub. */
data class Release(
    val version: String,
    val notes: List<String>,
    val publishedAt: String, // "2026-11-02"
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val shaUrl: String,
    val pageUrl: String,
) {
    fun toJson(): String = JSONObject()
        .put("version", version).put("notes", JSONArray(notes)).put("publishedAt", publishedAt)
        .put("apkName", apkName).put("apkUrl", apkUrl).put("apkSize", apkSize).put("shaUrl", shaUrl).put("pageUrl", pageUrl)
        .toString()

    companion object {
        fun fromJson(s: String): Release? = runCatching {
            val o = JSONObject(s)
            val n = o.getJSONArray("notes")
            Release(
                o.getString("version"), List(n.length()) { n.getString(it) }, o.getString("publishedAt"),
                o.getString("apkName"), o.getString("apkUrl"), o.getLong("apkSize"), o.getString("shaUrl"), o.getString("pageUrl"),
            )
        }.getOrNull()
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val release: Release, val read: Long) : UpdateState
    data class Verifying(val release: Release) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val release: Release, val message: String, val notGenuine: Boolean) : UpdateState
}

enum class CheckResult { NEWER, LATEST, FAILED }

/**
 * In-app updates from GitHub releases. Memora asks the GitHub API for the latest release (nothing
 * about the user is sent), downloads the APK and its .sha256 file, and only installs it when the
 * fingerprint matches and the APK is signed with the same key as the Memora already installed.
 * Android then asks the user to confirm the install.
 */
object Updates {
    private const val REPO = "smnexstudio/Memora"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    // Downloads must come from this repository's releases; GitHub then redirects to its file host.
    private const val DOWNLOADS = "https://github.com/$REPO/releases/download/"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 9_000_001
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_API_BYTES = 512 * 1024
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val MAX_NOTES = 6

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile private var cancelled = false

    /** True while the update screen is on show, so "ready" doesn't also need a notification. */
    @Volatile var watching = false

    // ---- What's installed and what's out there ----

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    /** The last release seen on GitHub, from the most recent check. */
    fun latest(): Release? = Prefs.updateLatest.get().takeIf { it.isNotEmpty() }?.let(Release::fromJson)

    /** The release to offer: newer than this install and not skipped. */
    fun available(context: Context): Release? =
        latest()?.takeIf { isNewer(it.version, installedVersion(context)) && it.version != Prefs.updateSkip.get() }

    fun lastCheckedAt(): Long? = Prefs.updateCheckedAt.get()

    fun autoCheck() = Prefs.updateAuto.get()
    fun setAutoCheck(on: Boolean) = Prefs.updateAuto.set(on)

    fun skip(release: Release) = Prefs.updateSkip.set(release.version)

    /** Set right after an update installs, until the next check. */
    fun justInstalled(context: Context): String? =
        Prefs.updateJustInstalled.get().takeIf { it.isNotEmpty() && it == installedVersion(context) }

    /** Run off the main thread at app start. */
    fun onAppStart(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        // A fresh process has no download in progress, so anything left over is stale.
        updatesDir(context).deleteRecursively()
        val pending = Prefs.updateInstalling.get()
        if (pending.isNotEmpty()) {
            if (pending == installedVersion(context)) Prefs.updateJustInstalled.set(pending)
            Prefs.updateInstalling.set("")
        }
        val last = lastCheckedAt() ?: 0L
        val now = System.currentTimeMillis()
        if (autoCheck() && (now - last >= WEEK_MS || now < last)) check(context)
    }

    /** Asks GitHub for the latest release. Blocking; call it off the main thread. */
    fun check(context: Context): CheckResult {
        val release = try {
            parse(String(get(API, MAX_API_BYTES.toLong()), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        } ?: return CheckResult.FAILED
        Prefs.updateLatest.set(release.toJson())
        Prefs.updateCheckedAt.set(System.currentTimeMillis())
        Prefs.updateJustInstalled.set("")
        return if (isNewer(release.version, installedVersion(context))) CheckResult.NEWER else CheckResult.LATEST
    }

    private fun parse(json: String): Release? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.getString("tag_name").removePrefix("v")
        if (parts(version) == null) return null
        val assets = o.getJSONArray("assets")
        val list = List(assets.length()) { assets.getJSONObject(it) }
        val apk = list.firstOrNull { it.getString("name") == "Memora-$version.apk" } ?: return null
        val sha = list.firstOrNull { it.getString("name") == "Memora-$version.apk.sha256" } ?: return null
        return Release(
            version = version,
            notes = notes(o.optString("body")),
            publishedAt = o.optString("published_at").take(10),
            apkName = apk.getString("name"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.getLong("size"),
            shaUrl = sha.getString("browser_download_url"),
            pageUrl = o.optString("html_url").takeIf { it.startsWith("https://github.com/$REPO/") } ?: "https://github.com/$REPO/releases/latest",
        )
    }

    /** The bullet points of the release notes, without Markdown, the first few only. */
    internal fun notes(body: String): List<String> {
        val bullets = body.lines().map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.drop(2)
                    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                    .replace("**", "").replace("__", "").replace("`", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        return if (bullets.size <= MAX_NOTES) bullets else bullets.take(MAX_NOTES - 1) + "And ${bullets.size - MAX_NOTES + 1} more changes"
    }

    internal fun parts(v: String): List<Int>? =
        v.split('.').takeIf { it.size == 3 }?.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }

    internal fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(installed.substringBefore('-')) ?: return true
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    // ---- Download and check ----

    fun download(context: Context, release: Release) {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Verifying || busy is UpdateState.Installing) return
        val app = context.applicationContext
        cancelled = false
        _state.value = UpdateState.Downloading(release, 0)
        thread(name = "memora-update") {
            val dir = updatesDir(app).apply { deleteRecursively(); mkdirs() }
            val apk = File(dir, release.apkName)
            try {
                if (!release.apkUrl.startsWith(DOWNLOADS) || !release.shaUrl.startsWith(DOWNLOADS)) throw NotGenuine()
                if (release.apkSize !in 1..MAX_APK_BYTES) throw NotGenuine()
                val expected = Regex("^[0-9a-fA-F]{64}").find(String(get(release.shaUrl, 4096), Charsets.UTF_8).trim())?.value?.lowercase()
                    ?: throw NotGenuine()
                save(release.apkUrl, apk, release.apkSize) { read -> _state.value = UpdateState.Downloading(release, read) }
                if (cancelled) throw IOException("Cancelled")
                _state.value = UpdateState.Verifying(release)
                if (sha256(apk) != expected) throw NotGenuine()
                checkPackage(app, apk, release)
                _state.value = UpdateState.Ready(release, apk)
                if (!watching) notifyReady(app, release)
            } catch (e: NotGenuine) {
                dir.deleteRecursively()
                _state.value = UpdateState.Failed(release, e.message ?: "This file isn’t the real Memora ${release.version}", notGenuine = true)
            } catch (e: Exception) {
                dir.deleteRecursively()
                _state.value = if (cancelled) UpdateState.Idle
                else UpdateState.Failed(release, "Couldn’t download the update. Check the connection and try again.", notGenuine = false)
            }
        }
    }

    fun cancel() {
        cancelled = true
    }

    /** Back to the start after a failure. */
    fun reset() {
        if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    private class NotGenuine(message: String? = null) : Exception(message)

    /** Same package, newer, the version GitHub named, and signed by the key this install was signed with. */
    private fun checkPackage(context: Context, apk: File, release: Release) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw NotGenuine()
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName || archive.versionName != release.version) throw NotGenuine()
        if (archive.longVersionCode <= installed.longVersionCode) throw NotGenuine("This download isn’t newer than the Memora you have")
        val mine = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val info = archive.signingInfo ?: throw NotGenuine()
        // With key rotation the new APK lists the older keys in its history.
        val theirs = (if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory)
            ?.map { it.toCharsString() }?.toSet().orEmpty()
        if (mine.isEmpty() || !theirs.containsAll(mine)) throw NotGenuine()
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Memora/Android (+https://github.com/$REPO)")
        conn.setRequestProperty("Accept", if (url == API) "application/vnd.github+json" else "application/octet-stream")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP ${conn.responseCode}")
        }
        // Redirects may only stay on https.
        if (conn.url.protocol != "https") {
            conn.disconnect()
            throw IOException("Not https")
        }
        return conn
    }

    private fun get(url: String, limit: Long): ByteArray {
        val conn = open(url)
        try {
            val out = java.io.ByteArrayOutputStream()
            copy(conn, out, limit) {}
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun save(url: String, file: File, expectedSize: Long, progress: (Long) -> Unit) {
        val conn = open(url)
        try {
            file.outputStream().use { copy(conn, it, expectedSize, progress) }
        } finally {
            conn.disconnect()
        }
        if (file.length() != expectedSize) throw IOException("Incomplete download")
    }

    private fun copy(conn: HttpURLConnection, out: java.io.OutputStream, limit: Long, progress: (Long) -> Unit) {
        conn.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                if (cancelled) throw IOException("Cancelled")
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw IOException("Too large")
                out.write(buf, 0, n)
                progress(total)
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---- Install ----

    /** Android's one-time "Install unknown apps" permission for Memora. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Hands the checked APK to Android, which shows its own "update this app?" prompt. */
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        val app = context.applicationContext
        _state.value = UpdateState.Installing(ready.release)
        thread(name = "memora-install") {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(ready.file.length())
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    ready.file.inputStream().use { input ->
                        session.openWrite("memora.apk", 0, ready.file.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    Prefs.updateInstalling.set(ready.release.version)
                    // Android fills in the result, so the intent must be mutable; it's explicit and private to Memora.
                    val result = Intent(app, InstallReceiver::class.java).setPackage(app.packageName)
                    val pending = PendingIntent.getBroadcast(app, id, result, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(ready.release, "Android couldn’t start the install. Try again.", notGenuine = false)
            }
        }
    }

    internal fun installResult(context: Context, status: Int) {
        val release = (_state.value as? UpdateState.Installing)?.release ?: return
        val file = File(updatesDir(context), release.apkName)
        _state.value = when (status) {
            // Cancelled in Android's prompt: the checked file is still there to try again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> if (file.exists()) UpdateState.Ready(release, file) else UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed(release, "Not enough space on the phone to install the update.", notGenuine = false)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed(release, "Android couldn’t install this update over the Memora you have.", notGenuine = false)
            else -> UpdateState.Failed(release, "The update didn’t install. Try again.", notGenuine = false)
        }
        if (_state.value !is UpdateState.Ready) Prefs.updateInstalling.set("")
    }

    private fun notifyReady(context: Context, release: Release) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB23A2E.toInt())
            .setContentTitle("Memora ${release.version} is ready to install")
            .setContentText("Downloaded and checked. Tap to install.")
            .setContentIntent(openAppIntent(context, "update", NOTIFICATION_ID))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun clearNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

/** Gets the installer's answer: show Android's prompt, or report how it went. */
class InstallReceiver : BroadcastReceiver() {
    // Not exported: only the PendingIntent Memora gave the installer can reach it.
    @SuppressLint("UnsafeIntentLaunch")
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Updates.installResult(context, PackageInstaller.STATUS_FAILURE_ABORTED)
                }
            }
            // On success Android restarts Memora on the new version; onAppStart picks it up.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Updates.installResult(context, status)
        }
    }
}
