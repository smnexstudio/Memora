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
 *
 * Events and settings are also kept in memory, so reading them never touches the database;
 * they are re-read only after a write. Each kind of data has its own stream, so a screen
 * showing events doesn't reload when a setting changes:
 * [events], [settings], [history] (sent cards and notes) and [festivalDates].
 */
object Repo {
    private lateinit var helper: Helper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private val _events = MutableStateFlow<List<MemoraEvent>>(emptyList())
    /** Every event, in no particular order. */
    val events: StateFlow<List<MemoraEvent>> = _events

    @Volatile
    private var byId: Map<Long, MemoraEvent> = emptyMap()

    private val _settings = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Every stored setting, raw. Read them through [Prefs]. */
    val settings: StateFlow<Map<String, String>> = _settings

    private val _history = MutableStateFlow(0)
    /** Ticks when sent history or person notes change. */
    val history: StateFlow<Int> = _history

    private val _festivalDates = MutableStateFlow(0)
    /** Ticks when the downloaded festival dates change. */
    val festivalDates: StateFlow<Int> = _festivalDates

    @Synchronized
    fun init(context: Context) {
        if (::helper.isInitialized) return
        helper = Helper(context.applicationContext)
        refreshEvents()
        refreshSettings()
    }

    /** Tests only: opens [context]'s database in place of the last one, so each test starts from its own. */
    @Synchronized
    internal fun resetForTests(context: Context) {
        helper = Helper(context.applicationContext)
        refreshEvents()
        refreshSettings()
    }

    /** Re-reads the events after a write. Synchronized, so an older read can't land after a newer one. */
    @Synchronized
    private fun refreshEvents() {
        val list = db.rawQuery("SELECT * FROM events", null).use { c -> buildList { while (c.moveToNext()) add(c.toEvent()) } }
        byId = list.associateBy { it.id }
        _events.value = list
    }

    @Synchronized
    private fun refreshSettings() {
        _settings.value = db.rawQuery("SELECT key, value FROM settings", null)
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }
    }

    private fun historyChanged() = _history.update { it + 1 }

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

    fun listEvents(): List<MemoraEvent> = _events.value

    fun getEvent(id: Long): MemoraEvent? = byId[id]

    /** Contact keys already imported, so the import screen can skip them. */
    fun importedContactIds(): Set<String> = _events.value.mapNotNullTo(mutableSetOf()) { it.contactId }

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
        refreshEvents()
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
        refreshEvents()
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
        refreshEvents()
        historyChanged()
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
        refreshEvents()
        historyChanged()
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
        historyChanged()
    }

    fun deleteNote(id: Long) {
        db.delete("person_notes", "id = ?", arrayOf(id.toString()))
        historyChanged()
    }

    // ---- Settings (typed in [Prefs]) ----

    fun setting(key: String): String? = _settings.value[key]

    fun writeSetting(key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        _settings.update { it + (key to value) }
    }

    /** Remembers how to send to this person, or forgets it with null. */
    fun setSendVia(id: Long, channel: String?) {
        db.update("events", ContentValues().apply { put("send_via", channel) }, "id = ?", arrayOf(id.toString()))
        refreshEvents()
    }

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
                put("key", Prefs.festivalCalendars.key); put("value", calendars)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshSettings()
        _festivalDates.update { it + 1 }
    }

    /** Deletes everything Memora stores in its database: events, history, notes, festivals and every setting. */
    fun eraseAll() {
        db.beginTransaction()
        try {
            for (table in listOf("history", "person_notes", "events", "festival_dates", "settings")) db.delete(table, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshEvents()
        refreshSettings()
        historyChanged()
        _festivalDates.update { it + 1 }
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
        // App lock, the PIN, update checks and downloaded festivals stay on this phone (see [Prefs]).
        val settings = JSONArray().apply {
            _settings.value.filterKeys { it !in Prefs.deviceOnly }.forEach { (k, v) -> put(JSONObject().put("key", k).put("value", v)) }
        }
        return JSONObject()
            .put("app", "memora").put("version", 1).put("exportedAt", Instant.now().toString())
            .put("events", events).put("history", history).put("personNotes", notes).put("settings", settings)
            .toString(2)
    }

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
            .filterNotNull().filter { s -> Prefs.restorable[s.optString("key")]?.accepts(s.optString("value")) == true }

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
        refreshEvents()
        refreshSettings()
        historyChanged()
        return events.size
    }
}
