package com.smnexstudio.memora.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.smnexstudio.memora.reminders.AlertStyle
import com.smnexstudio.memora.widget.LockedWidget
import org.json.JSONArray

/**
 * One stored setting: its key, its value when unset, and how it's read and written.
 * [parse] returns null for a value it doesn't accept, which reads as [default] and keeps a
 * backup from restoring it.
 */
class Setting<T>(
    val key: String,
    val default: T,
    private val parse: (String) -> T?,
    private val format: (T) -> String = { it.toString() },
    /** Restored from a backup. Everything not [deviceOnly] is written into one. */
    val restore: Boolean = false,
    /** Never leaves this phone: not written into a backup. */
    val deviceOnly: Boolean = false,
) {
    fun get(): T = from(Repo.settings.value)

    fun from(all: Map<String, String>): T = all[key]?.let(parse) ?: default

    fun set(value: T) = Repo.writeSetting(key, format(value))

    /** Whether [raw], from a backup, is a value this setting takes. */
    fun accepts(raw: String) = parse(raw) != null

    /** The value, read again whenever settings change. */
    val current: T @Composable get() = from(Repo.settings.collectAsState().value)
}

private fun bool(key: String, default: Boolean, restore: Boolean = false, deviceOnly: Boolean = false) =
    Setting(key, default, { when (it) { "1" -> true; "0" -> false; else -> null } }, { if (it) "1" else "0" }, restore, deviceOnly)

private fun int(key: String, default: Int, range: IntRange, restore: Boolean = false) =
    Setting(key, default, { it.toIntOrNull()?.takeIf { v -> v in range } }, restore = restore)

/** A time in milliseconds, or null when it hasn't happened. */
private fun instant(key: String, deviceOnly: Boolean = false) =
    Setting<Long?>(key, null, { it.toLongOrNull() }, { it?.toString() ?: "" }, deviceOnly = deviceOnly)

private fun text(key: String, default: String = "", maxLength: Int = Int.MAX_VALUE, restore: Boolean = false, deviceOnly: Boolean = false) =
    Setting(key, default, { it.takeIf { v -> v.length <= maxLength } }, restore = restore, deviceOnly = deviceOnly)

private fun choice(key: String, default: String, options: List<String>, restore: Boolean = false) =
    Setting(key, default, { it.takeIf { v -> v in options } }, restore = restore)

private fun <E> enum(key: String, default: E, entries: List<E>, id: (E) -> String, restore: Boolean = false) =
    Setting(key, default, { v -> entries.firstOrNull { id(it) == v } }, id, restore = restore)

/** Ids used before festivals came from Google, mapped to the ids made from Google's names. */
private val LEGACY_FESTIVAL_IDS = mapOf(
    "newyear" to "new-years-day", "sankranti" to "makar-sankranti", "eid" to "ramzan-id",
    "rakhi" to "raksha-bandhan", "ganesha" to "ganesh-chaturthi", "diwali" to "diwali-deepavali",
)
private val FESTIVAL_ID = Regex("^[a-z0-9-]+$")
private val CALENDAR_CODES = Regex("^[A-Z]+(,[A-Z]+)?$")

/** Every setting Memora stores, in one place. */
object Prefs {
    // Reminders
    val reminderHour = int("reminderHour", 9, 0..23, restore = true)
    val reminderMinute = int("reminderMinute", 0, 0..59, restore = true)
    /** Early reminders for new events: one or two of [OFFSET_OPTIONS], in days. */
    val defaultOffsets: Setting<List<Int>> = Setting(
        "defaultOffsets", listOf(3, 1),
        { v -> runCatching { JSONArray(v).let { a -> List(a.length()) { a.getInt(it) } } }.getOrNull()?.takeIf { it.size in 1..2 && it.all { d -> d >= 0 } } },
        { JSONArray(it).toString() }, restore = true,
    )
    val alertStyle = enum("alertStyle", AlertStyle.NOTIFICATION, AlertStyle.entries, { it.id }, restore = true)
    val weeklyDigest = bool("weeklyDigest", true, restore = true)

    // Festivals
    /** Festival ids the user wants reminders for (may include other countries' ids). */
    val festivals: Setting<List<String>> = Setting(
        "festivals", emptyList<String>(),
        { v ->
            runCatching { JSONArray(v).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull()
                ?.takeIf { ids -> ids.all { FESTIVAL_ID.matches(it) } }
                ?.map { LEGACY_FESTIVAL_IDS[it] ?: it }?.distinct()
        },
        { JSONArray(it).toString() }, restore = true,
    )
    /** Holiday calendars whose festivals are shown: one or two codes, e.g. "IN", "HINDU". */
    val calendars: Setting<List<String>> = Setting(
        "calendars", listOf("IN"),
        { v -> v.takeIf { CALENDAR_CODES.matches(it) }?.split(",")?.distinct() },
        { it.take(2).joinToString(",") }, restore = true,
    )
    val festivalEarly = bool("festivalEarly", false, restore = true)
    /** Calendars the stored festivals came from ("IN,HINDU"), or "" if none are stored. */
    val festivalCalendars = text("festivalCalendars", deviceOnly = true)
    val festivalAttemptAt = instant("festivalAttemptAt", deviceOnly = true)
    val festivalFetchedAt = instant("festivalFetchedAt", deviceOnly = true)

    // Cards
    /** How cards are signed, e.g. "SM"; blank for no sign-off. */
    val signature = text("signature", maxLength = 40, restore = true)
    val templateTone = choice("templateTone", "formal", listOf("formal", "fun", "family"), restore = true)
    val cardShape = text("cardShape", "square")
    val saveCards = bool("saveCards", false)
    /** The card design last used for one kind of event. */
    fun cardDesign(type: EventType) = text("cardDesign/${type.id}")
    /** "postcard" or "text": how cards last went out through one channel. */
    fun sendMode(channel: Channel) = choice("sendMode/${channel.id}", "postcard", listOf("postcard", "text"))

    // Appearance
    val themeMode = choice("themeMode", "system", listOf("system", "light", "dark"), restore = true)
    val themeStyle = choice("themeStyle", "postcard", listOf("postcard", "board"), restore = true)

    // Widget
    val widgetLocked = enum("widgetLocked", LockedWidget.COUNT, LockedWidget.entries, { it.id })
    val widgetAges = bool("widgetAges", true)
    val widgetFestivals = bool("widgetFestivals", true)
    val widgetRows = int("widgetRows", 3, 1..5)

    // Privacy
    /** Reminders on the lock screen say only "You have a reminder" until the phone is unlocked. */
    val hideOnLockScreen = bool("hideOnLockScreen", true, restore = true)
    /** Keeps Memora out of screenshots, screen recordings and the Recents preview. App lock always does. */
    val blockScreenshots = bool("blockScreenshots", false, restore = true)
    // These stay on this phone.
    val appLock = bool("appLock", false, deviceOnly = true)
    val appPinHash = text("appPinHash", deviceOnly = true)
    val appPinSalt = text("appPinSalt", deviceOnly = true)
    val pinFails = Setting("pinFails", 0, { it.toIntOrNull() }, deviceOnly = true)
    val pinLockUntil = Setting("pinLockUntil", 0L, { it.toLongOrNull() }, deviceOnly = true)
    val linkToken = text("linkToken", deviceOnly = true)

    // Reminder bookkeeping: on this phone only.
    /** Up to when people reminders have been sent, in epoch milliseconds. */
    val remindersHandledAt = instant("remindersHandledAt", deviceOnly = true)
    /** Set once the per-reminder alarms of Memora 1.0 have been cleared. */
    val rollingAlarms = bool("rollingAlarms", false, deviceOnly = true)

    // Backups and updates
    val lastBackupAt = instant("lastBackupAt")
    val updateAuto = bool("updateAuto", true, restore = true)
    val updateLatest = text("updateLatest", deviceOnly = true)
    val updateSkip = text("updateSkip", deviceOnly = true)
    val updateCheckedAt = instant("updateCheckedAt", deviceOnly = true)
    val updateInstalling = text("updateInstalling", deviceOnly = true)
    val updateJustInstalled = text("updateJustInstalled", deviceOnly = true)

    private val all: List<Setting<*>> = listOf(
        reminderHour, reminderMinute, defaultOffsets, alertStyle, weeklyDigest,
        festivals, calendars, festivalEarly, festivalCalendars, festivalAttemptAt, festivalFetchedAt,
        signature, templateTone, cardShape, saveCards, themeMode, themeStyle,
        widgetLocked, widgetAges, widgetFestivals, widgetRows,
        hideOnLockScreen, blockScreenshots, appLock, appPinHash, appPinSalt, pinFails, pinLockUntil, linkToken,
        remindersHandledAt, rollingAlarms,
        lastBackupAt, updateAuto, updateLatest, updateSkip, updateCheckedAt, updateInstalling, updateJustInstalled,
    )

    /** Keys never written into a backup. */
    val deviceOnly: Set<String> = all.filter { it.deviceOnly }.mapTo(mutableSetOf()) { it.key }

    /** Settings a backup may restore, by key. */
    val restorable: Map<String, Setting<*>> = all.filter { it.restore }.associateBy { it.key }
}
