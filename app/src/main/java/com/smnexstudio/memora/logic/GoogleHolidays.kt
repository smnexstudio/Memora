package com.smnexstudio.memora.logic

import com.smnexstudio.memora.data.FestivalRow
import com.smnexstudio.memora.data.Repo
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Festivals from Google Calendar's public holiday calendars (up to two, picked in
 * Settings). The iCal feeds need no account or API key. This year's and next year's
 * festivals are stored in the festival_dates table and downloaded again when the
 * table is empty, a year is missing, or the calendars have changed. Failed or
 * incomplete downloads are retried at most once an hour unless forced.
 */
object GoogleHolidays {
    private const val RETRY_MS = 60 * 60 * 1000L
    private const val MAX_FEED_BYTES = 2 * 1024 * 1024

    private fun feed(c: HolidayCalendar) =
        "https://calendar.google.com/calendar/ical/en.${c.googleId}%23holiday%40group.v.calendar.google.com/public/basic.ics"

    /** Mirrors of the database, for screens, the calendar and alarms. */
    @Volatile
    var festivals: List<Festival> = emptyList()
        private set

    @Volatile
    private var dates: Map<String, Map<Int, LocalDate>> = emptyMap() // id -> year -> date

    private fun years() = today().year.let { listOf(it, it + 1) }

    fun load() {
        val rows = Repo.festivalRows().sortedBy { it.date }
        festivals = rows.groupBy { it.id }.map { (id, r) -> Festival(id, r.first().name, r.any { it.approximate }) }
            .sortedBy { f -> rows.first { it.id == f.id }.date.withYear(2000) }
        dates = rows.groupBy { it.id }.mapValues { (_, r) -> r.associate { it.date.year to it.date } }
    }

    fun date(id: String, year: Int): LocalDate? = dates[id]?.get(year)

    /** Years with stored festivals, e.g. [2026, 2027]. */
    fun storedYears(): List<Int> = dates.values.flatMap { it.keys }.distinct().sorted()

    private fun wanted() = chosenCalendars(Repo.calendars()).joinToString(",") { it.code }

    private fun complete(): Boolean {
        if (Repo.festivalCalendars() != wanted()) return false
        val stored = dates.values.flatMap { it.keys }.toSet()
        return years().all { it in stored }
    }

    /**
     * Downloads the chosen calendars' festivals if any are missing, empty or for other calendars.
     * Without [force], a failed or incomplete download isn't retried for an hour; [refresh]
     * downloads again even when nothing is missing. Blocking; call
     * off the main thread. Returns true if new festivals were stored, so the caller can
     * reschedule reminders.
     */
    @Synchronized
    fun ensure(force: Boolean = false, refresh: Boolean = false): Boolean {
        load()
        if (complete() && !refresh) return false
        val lastAttempt = Repo.getSetting("festivalAttemptAt", "0").toLongOrNull() ?: 0
        if (!force && System.currentTimeMillis() - lastAttempt in 0 until RETRY_MS) return false
        Repo.setSetting("festivalAttemptAt", System.currentTimeMillis().toString())

        val calendars = chosenCalendars(Repo.calendars())
        val rows = try {
            // The first calendar wins when both list the same festival (e.g. Christmas).
            calendars.flatMap { parse(download(feed(it))) }.distinctBy { it.id to it.date.year }
        } catch (_: Exception) {
            return false
        }
        if (rows.isEmpty()) return false
        Repo.replaceFestivalRows(calendars.joinToString(",") { it.code }, rows)
        Repo.setSetting("festivalFetchedAt", System.currentTimeMillis().toString())
        load()
        return true
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Memora/Android (+https://github.com/smnexstudio/Memora)")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP ${conn.responseCode}")
            // The feed is about 200 KB; stop well before a bad answer could exhaust memory.
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_FEED_BYTES) throw IllegalStateException("Feed too large")
                }
                out.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    /** "Ramzan Id (tentative)" -> "Ramzan Id"; substitute days and clock changes aren't festivals. */
    private fun cleanName(title: String): String? {
        if (title.startsWith("Substitute ") || title.contains("(substitute)") || title.startsWith("Daylight Saving")) return null
        return title.replace(Regex("""\s*\((tentative|regional holiday)\)"""), "").trim().ifEmpty { null }
    }

    /** "New Year's Day" -> "new-years-day" */
    private fun slug(name: String) = name.lowercase().replace("'", "").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Reads DTSTART, SUMMARY and DESCRIPTION from each VEVENT, keeping this year and next. */
    private fun parse(ics: String): List<FestivalRow> {
        val keep = years()
        val out = linkedMapOf<Pair<String, Int>, FestivalRow>()
        var start: LocalDate? = null
        var title: String? = null
        var description = ""
        // Unfold continuation lines (RFC 5545: a line starting with a space continues the previous one).
        for (line in ics.replace("\r\n", "\n").replace("\n ", "").lines()) {
            when {
                line == "BEGIN:VEVENT" -> { start = null; title = null; description = "" }
                line.startsWith("DTSTART") -> start = line.substringAfter(':').take(8).toIntOrNull()?.let {
                    LocalDate.of(it / 10000, it / 100 % 100, it % 100)
                }
                line.startsWith("SUMMARY:") -> title = line.substringAfter(':').trim()
                line.startsWith("DESCRIPTION:") -> description = line.substringAfter(':')
                line == "END:VEVENT" -> {
                    val d = start ?: continue
                    val t = title ?: continue
                    if (d.year !in keep) continue
                    val name = cleanName(t) ?: continue
                    val tentative = "tentative" in t.lowercase() || "tentative" in description.lowercase()
                    out.putIfAbsent(slug(name) to d.year, FestivalRow(slug(name), name, d, tentative))
                }
            }
        }
        return out.values.toList()
    }
}
