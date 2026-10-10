package com.smnexstudio.memora.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

private class Helper(context: Context) : SQLiteOpenHelper(context, "memora.db", null, 5) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                title TEXT,
                month INTEGER NOT NULL,
                day INTEGER NOT NULL,
                year INTEGER,
                phone TEXT,
                email TEXT,
                message TEXT NOT NULL,
                offsets TEXT NOT NULL,
                notes TEXT,
                contact_id TEXT,
                last_sent_year INTEGER,
                lunar INTEGER NOT NULL DEFAULT 0,
                send_via TEXT
            )"""
        )
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                year INTEGER NOT NULL,
                channel TEXT NOT NULL,
                message TEXT,
                sent_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX history_event ON history (event_id, year)")
        createFestivalDates(db)
        createPersonNotes(db)
    }

    private fun createPersonNotes(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE person_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                text TEXT NOT NULL,
                year INTEGER,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX person_notes_event ON person_notes (event_id)")
    }

    private fun createFestivalDates(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE festival_dates (
                festival_id TEXT NOT NULL,
                year INTEGER NOT NULL,
                date TEXT NOT NULL,
                name TEXT NOT NULL,
                approximate INTEGER NOT NULL,
                PRIMARY KEY (festival_id, year)
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 2's festival_dates had no names; the dates are downloaded again anyway.
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS festival_dates")
            createFestivalDates(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE events ADD COLUMN lunar INTEGER NOT NULL DEFAULT 0")
            createPersonNotes(db)
            // Kannada and Hindi wishes were removed; festivals now come from up to two calendars.
            db.execSQL("DELETE FROM settings WHERE key IN ('templateLang', 'country', 'festivalCountry')")
        }
        if (oldVersion < 5) db.execSQL("ALTER TABLE events ADD COLUMN send_via TEXT")
    }
}

/**
 * All of Memora's data: events, sent history and settings, in one SQLite database.
 * [version] ticks on every write so screens can re-read.
 */
object Repo {
    private lateinit var helper: Helper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Synchronized
    fun init(context: Context) {
        if (!::helper.isInitialized) helper = Helper(context.applicationContext)
    }

    private fun changed() {
        _version.update { it + 1 }
    }

    // ---- Events ----

    private fun parseOffsets(s: String?): List<Int> = try {
        val a = JSONArray(s ?: "[3,1]")
        List(a.length()) { a.getInt(it) }.ifEmpty { listOf(3, 1) }
    } catch (_: Exception) {
        listOf(3, 1)
    }

    private fun Cursor.str(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.int(col: String): Int? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }

    private fun Cursor.toEvent() = MemoraEvent(
        id = getLong(getColumnIndexOrThrow("id")),
        type = EventType.from(str("type")) ?: EventType.OTHER,
        name = str("name") ?: "",
        title = str("title"),
        month = int("month") ?: 1,
        day = int("day") ?: 1,
        year = int("year"),
        phone = str("phone"),
        email = str("email"),
        message = str("message") ?: "",
        offsets = parseOffsets(str("offsets")),
        notes = str("notes"),
        contactId = str("contact_id"),
        lastSentYear = int("last_sent_year"),
        lunar = int("lunar") == 1,
        sendVia = str("send_via"),
    )

    fun listEvents(): List<MemoraEvent> =
        db.rawQuery("SELECT * FROM events", null).use { c -> buildList { while (c.moveToNext()) add(c.toEvent()) } }

    fun getEvent(id: Long): MemoraEvent? =
        db.rawQuery("SELECT * FROM events WHERE id = ?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toEvent() else null }

    /** Contact keys already imported, so the import screen can skip them. */
    fun importedContactIds(): Set<String> =
        db.rawQuery("SELECT contact_id FROM events WHERE contact_id IS NOT NULL", null)
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    /** Inserts when [id] is null, otherwise updates. Returns the event id. */
    fun saveEvent(e: MemoraEvent, id: Long? = null): Long {
        val v = ContentValues().apply {
            put("type", e.type.id); put("name", e.name); put("title", e.title)
            put("month", e.month); put("day", e.day); put("year", e.year)
            put("phone", e.phone); put("email", e.email); put("message", e.message)
            put("offsets", JSONArray(e.offsets).toString()); put("notes", e.notes); put("contact_id", e.contactId)
            put("lunar", if (e.lunar && e.year != null) 1 else 0)
            put("send_via", e.sendVia)
        }
        val result = if (id != null) {
            db.update("events", v, "id = ?", arrayOf(id.toString())); id
        } else {
            db.insertOrThrow("events", null, v)
        }
        changed()
        return result
    }

    /** Saves many new events in one transaction, with one change tick (contact import). */
    fun saveEvents(list: List<MemoraEvent>) {
        if (list.isEmpty()) return
        db.beginTransaction()
        try {
            list.forEach { e ->
                db.insertOrThrow("events", null, ContentValues().apply {
                    put("type", e.type.id); put("name", e.name); put("title", e.title)
                    put("month", e.month); put("day", e.day); put("year", e.year)
                    put("phone", e.phone); put("email", e.email); put("message", e.message)
                    put("offsets", JSONArray(e.offsets).toString()); put("notes", e.notes); put("contact_id", e.contactId)
                    put("lunar", if (e.lunar && e.year != null) 1 else 0)
                    put("send_via", e.sendVia)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun deleteEvent(id: Long) {
        db.beginTransaction()
        try {
            db.delete("history", "event_id = ?", arrayOf(id.toString()))
            db.delete("person_notes", "event_id = ?", arrayOf(id.toString()))
            db.delete("events", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /** Marks the year as handled and keeps a record of what was sent ("skipped" for skips). */
    fun markSent(id: Long, year: Int, channel: String, message: String?) {
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE events SET last_sent_year = MAX(COALESCE(last_sent_year, 0), ?) WHERE id = ?",
                arrayOf<Any>(year, id),
            )
            db.insertOrThrow("history", null, ContentValues().apply {
                put("event_id", id); put("year", year); put("channel", channel)
                put("message", message); put("sent_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /** Newest first. */
    fun listHistory(eventId: Long): List<HistoryEntry> =
        db.rawQuery("SELECT * FROM history WHERE event_id = ? ORDER BY year DESC, sent_at DESC", arrayOf(eventId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    HistoryEntry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        eventId = c.getLong(c.getColumnIndexOrThrow("event_id")),
                        year = c.getInt(c.getColumnIndexOrThrow("year")),
                        channel = c.getString(c.getColumnIndexOrThrow("channel")),
                        message = c.str("message"),
                        sentAt = c.getLong(c.getColumnIndexOrThrow("sent_at")),
                    )
                )
            }
        }

    // ---- Person notes ----

    private fun Cursor.toNote() = PersonNote(
        id = getLong(getColumnIndexOrThrow("id")),
        eventId = getLong(getColumnIndexOrThrow("event_id")),
        kind = NoteKind.from(str("kind")),
        text = str("text") ?: "",
        year = int("year"),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
    )

    /** Newest first. */
    fun listNotes(eventId: Long): List<PersonNote> =
        db.rawQuery("SELECT * FROM person_notes WHERE event_id = ? ORDER BY COALESCE(year, 0) DESC, created_at DESC", arrayOf(eventId.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    /** Every person's notes, for search. */
    fun allNotes(): List<PersonNote> =
        db.rawQuery("SELECT * FROM person_notes", null).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    fun addNote(eventId: Long, kind: NoteKind, text: String, year: Int?) {
        db.insertOrThrow("person_notes", null, ContentValues().apply {
            put("event_id", eventId); put("kind", kind.id); put("text", text); put("year", year)
            put("created_at", System.currentTimeMillis())
        })
        changed()
    }

    fun deleteNote(id: Long) {
        db.delete("person_notes", "id = ?", arrayOf(id.toString()))
        changed()
    }

    // ---- Settings ----

    fun getSetting(key: String, fallback: String): String =
        db.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else fallback }

    fun setSetting(key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    fun getBool(key: String, fallback: Boolean) = getSetting(key, if (fallback) "1" else "0") == "1"
    fun setBool(key: String, value: Boolean) = setSetting(key, if (value) "1" else "0")

    fun reminderHour() = getSetting("reminderHour", "9").toIntOrNull()?.coerceIn(0, 23) ?: 9
    fun reminderMinute() = getSetting("reminderMinute", "0").toIntOrNull()?.coerceIn(0, 59) ?: 0
    fun defaultOffsets(): List<Int> = parseOffsets(getSetting("defaultOffsets", "[3,1]"))

    /** Ids used before festivals came from Google, mapped to the ids made from Google's names. */
    private val LEGACY_FESTIVAL_IDS = mapOf(
        "newyear" to "new-years-day", "sankranti" to "makar-sankranti", "eid" to "ramzan-id",
        "rakhi" to "raksha-bandhan", "ganesha" to "ganesh-chaturthi", "diwali" to "diwali-deepavali",
    )

    /** Festival ids the user wants reminders for (may include other countries' ids). */
    fun festivals(): List<String> = try {
        val a = JSONArray(getSetting("festivals", "[]"))
        List(a.length()) { a.getString(it).let { id -> LEGACY_FESTIVAL_IDS[id] ?: id } }.distinct()
    } catch (_: Exception) {
        emptyList()
    }

    /** Holiday calendars whose festivals are shown (1 or 2 codes, e.g. "IN", "HINDU"). */
    fun calendars(): List<String> =
        getSetting("calendars", "IN").split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2).ifEmpty { listOf("IN") }

    fun setCalendars(codes: List<String>) = setSetting("calendars", codes.take(2).joinToString(","))

    /** Remembers how to send to this person, or forgets it with null. */
    fun setSendVia(id: Long, channel: String?) {
        db.update("events", ContentValues().apply { put("send_via", channel) }, "id = ?", arrayOf(id.toString()))
        changed()
    }

    /** How cards are signed, e.g. "SM"; blank for no sign-off. */
    fun signature(): String = getSetting("signature", "")

    fun setFestivals(ids: List<String>) = setSetting("festivals", JSONArray(ids).toString())

    // ---- Festivals (downloaded from Google Calendar) ----

    fun festivalRows(): List<FestivalRow> =
        db.rawQuery("SELECT festival_id, date, name, approximate FROM festival_dates", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val d = runCatching { LocalDate.parse(c.getString(1)) }.getOrNull() ?: continue
                    add(FestivalRow(c.getString(0), c.getString(2), d, c.getInt(3) == 1))
                }
            }
        }

    /** Calendars the stored festivals came from ("IN,HINDU"), or "" if none are stored. */
    fun festivalCalendars(): String = getSetting("festivalCalendars", "")

    /** Replaces every stored festival, dropping years and calendars no longer downloaded. */
    fun replaceFestivalRows(calendars: String, rows: List<FestivalRow>) {
        db.beginTransaction()
        try {
            db.delete("festival_dates", null, null)
            for (r in rows) {
                db.insertOrThrow("festival_dates", null, ContentValues().apply {
                    put("festival_id", r.id); put("year", r.date.year); put("date", r.date.toString())
                    put("name", r.name); put("approximate", if (r.approximate) 1 else 0)
                })
            }
            db.insertWithOnConflict("settings", null, ContentValues().apply {
                put("key", "festivalCalendars"); put("value", calendars)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    // ---- Backup (same JSON format as Memora 1.x, so old backups restore) ----

    private val EVENT_COLS = listOf(
        "id", "type", "name", "title", "month", "day", "year", "phone", "email",
        "message", "offsets", "notes", "contact_id", "last_sent_year", "lunar", "send_via",
    )
    private val HISTORY_COLS = listOf("id", "event_id", "year", "channel", "message", "sent_at")
    private val NOTE_COLS = listOf("id", "event_id", "kind", "text", "year", "created_at")

    private fun Cursor.toJson(cols: List<String>) = JSONObject().apply {
        for (col in cols) {
            val i = getColumnIndexOrThrow(col)
            when {
                isNull(i) -> put(col, JSONObject.NULL)
                getType(i) == Cursor.FIELD_TYPE_INTEGER -> put(col, getLong(i))
                else -> put(col, getString(i))
            }
        }
    }

    fun exportJson(): String {
        val events = db.rawQuery("SELECT * FROM events", null).use { c -> JSONArray().apply { while (c.moveToNext()) put(c.toJson(EVENT_COLS)) } }
        val history = db.rawQuery("SELECT * FROM history", null).use { c -> JSONArray().apply { while (c.moveToNext()) put(c.toJson(HISTORY_COLS)) } }
        val notes = db.rawQuery("SELECT * FROM person_notes", null).use { c -> JSONArray().apply { while (c.moveToNext()) put(c.toJson(NOTE_COLS)) } }
        // App lock stays on the device it was turned on for. Update checks are per phone too.
        val settings = db.rawQuery("SELECT key, value FROM settings WHERE key NOT IN ('appLock', 'linkToken', 'appPinHash', 'appPinSalt', 'pinFails', 'pinLockUntil', 'festivalCalendars', 'festivalAttemptAt', 'festivalFetchedAt') AND (key NOT LIKE 'update%' OR key = 'updateAuto')", null).use { c ->
            JSONArray().apply { while (c.moveToNext()) put(JSONObject().put("key", c.getString(0)).put("value", c.getString(1))) }
        }
        return JSONObject()
            .put("app", "memora").put("version", 1).put("exportedAt", Instant.now().toString())
            .put("events", events).put("history", history).put("personNotes", notes).put("settings", settings)
            .toString(2)
    }

    /** Settings a backup may restore, each with a check on its value. */
    private val RESTORABLE: Map<String, (String) -> Boolean> = mapOf(
        "reminderHour" to { v -> v.toIntOrNull()?.let { it in 0..23 } == true },
        "reminderMinute" to { v -> v.toIntOrNull()?.let { it in 0..59 } == true },
        "defaultOffsets" to { v -> Regex("""^\[\s*\d+(\s*,\s*\d+)?\s*]$""").matches(v) },
        "weeklyDigest" to { v -> v == "0" || v == "1" },
        "festivals" to { v -> Regex("""^\[("[a-z0-9-]+",?)*]$""").matches(v.replace(Regex("\\s"), "")) },
        "calendars" to { v -> Regex("^[A-Z]+(,[A-Z]+)?$").matches(v) },
        "festivalEarly" to { v -> v == "0" || v == "1" },
        "signature" to { v -> v.length <= 40 },
        "themeMode" to { v -> v in listOf("system", "light", "dark") },
        "templateTone" to { v -> v in listOf("formal", "fun", "family") },
        "updateAuto" to { v -> v == "0" || v == "1" },
    )

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else (opt(key) as? Number)?.toInt()
    private fun JSONObject.optStr(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)

    /** Replaces all events, history and settings with the backup's. Returns the number of events restored. */
    fun importJson(text: String): Int {
        val root = try {
            JSONObject(text.removePrefix("\uFEFF"))
        } catch (_: Exception) {
            throw IllegalArgumentException("This file is not a Memora backup.")
        }
        if (root.optString("app") != "memora" || root.optJSONArray("events") == null) {
            throw IllegalArgumentException("This file is not a Memora backup.")
        }
        val events = root.getJSONArray("events").let { a -> List(a.length()) { a.optJSONObject(it) } }.filterNotNull().filter { o ->
            val month = o.optIntOrNull("month"); val day = o.optIntOrNull("day"); val year = o.optIntOrNull("year")
            o.has("id") && !o.optStr("name").isNullOrBlank() && o.optStr("message") != null &&
                EventType.from(o.optStr("type")) != null &&
                month != null && month in 1..12 && day != null && day in 1..31 &&
                (year == null || year in 1801..2999)
        }
        val ids = events.map { it.getLong("id") }.toSet()
        val history = root.optJSONArray("history")?.let { a -> List(a.length()) { a.optJSONObject(it) } }.orEmpty()
            .filterNotNull().filter { it.optLong("event_id", -1) in ids && it.optIntOrNull("year") != null }
        val notes = root.optJSONArray("personNotes")?.let { a -> List(a.length()) { a.optJSONObject(it) } }.orEmpty()
            .filterNotNull().filter { it.optLong("event_id", -1) in ids && !it.optStr("text").isNullOrBlank() }
        val settings = root.optJSONArray("settings")?.let { a -> List(a.length()) { a.optJSONObject(it) } }.orEmpty()
            .filterNotNull().filter { s -> RESTORABLE[s.optString("key")]?.invoke(s.optString("value")) == true }

        db.beginTransaction()
        try {
            db.delete("history", null, null)
            db.delete("person_notes", null, null)
            db.delete("events", null, null)
            for (o in events) {
                db.insertOrThrow("events", null, ContentValues().apply {
                    put("id", o.getLong("id")); put("type", o.getString("type")); put("name", o.getString("name"))
                    put("title", o.optStr("title")); put("month", o.optIntOrNull("month")); put("day", o.optIntOrNull("day"))
                    put("year", o.optIntOrNull("year")); put("phone", o.optStr("phone")); put("email", o.optStr("email"))
                    put("message", o.getString("message")); put("offsets", o.optStr("offsets") ?: "[3,1]")
                    put("notes", o.optStr("notes"))
                    // Contact ids differ between phones, so don't carry them over.
                    putNull("contact_id")
                    put("last_sent_year", o.optIntOrNull("last_sent_year"))
                    put("lunar", if (o.optIntOrNull("lunar") == 1 && o.optIntOrNull("year") != null) 1 else 0)
                    put("send_via", o.optStr("send_via")?.takeIf { Channel.from(it) != null })
                })
            }
            for (n in notes) {
                db.insertOrThrow("person_notes", null, ContentValues().apply {
                    put("event_id", n.getLong("event_id")); put("kind", NoteKind.from(n.optStr("kind")).id)
                    put("text", n.getString("text")); put("year", n.optIntOrNull("year"))
                    put("created_at", n.optLong("created_at", System.currentTimeMillis()))
                })
            }
            for (h in history) {
                db.insertOrThrow("history", null, ContentValues().apply {
                    put("event_id", h.getLong("event_id")); put("year", h.getInt("year"))
                    put("channel", h.optString("channel", "skipped")); put("message", h.optStr("message"))
                    put("sent_at", h.optLong("sent_at", System.currentTimeMillis()))
                })
            }
            for (s in settings) {
                db.insertWithOnConflict("settings", null, ContentValues().apply {
                    put("key", s.getString("key")); put("value", s.getString("value"))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return events.size
    }
}
