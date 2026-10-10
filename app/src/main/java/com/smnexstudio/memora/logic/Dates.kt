package com.smnexstudio.memora.logic

import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** The date in a given year; 29 Feb falls on 28 Feb in non-leap years. */
fun dateInYear(year: Int, month: Int, day: Int): LocalDate {
    val ym = java.time.YearMonth.of(year, month)
    return ym.atDay(day.coerceAtMost(ym.lengthOfMonth()))
}

fun today(): LocalDate = LocalDate.now()

fun nextOccurrence(month: Int, day: Int, from: LocalDate = today()): LocalDate {
    val d = dateInYear(from.year, month, day)
    return if (d < from) dateInYear(from.year + 1, month, day) else d
}

/** Most recent occurrence strictly before [from]. */
fun previousOccurrence(month: Int, day: Int, from: LocalDate = today()): LocalDate {
    val d = dateInYear(from.year, month, day)
    return if (d < from) d else dateInYear(from.year - 1, month, day)
}

// ---- An event's dates; lunar events move each year ----

private fun MemoraEvent.originalDate(): LocalDate? = year?.takeIf { lunar }?.let { dateInYear(it, month, day) }

/** The event's occurrence for [year]'s anniversary. A lunar one may fall in the next or previous Gregorian year. */
fun occurrenceIn(e: MemoraEvent, year: Int): LocalDate =
    e.originalDate()?.let { Lunar.anniversary(it, year) } ?: dateInYear(year, e.month, e.day)

/** The occurrence whose date is in [year], for the history of that year. */
fun occurrenceDatedIn(e: MemoraEvent, year: Int): LocalDate =
    if (!e.lunar) dateInYear(year, e.month, e.day)
    else (year - 1..year + 1).map { occurrenceIn(e, it) }.firstOrNull { it.year == year } ?: occurrenceIn(e, year)

fun nextDate(e: MemoraEvent, from: LocalDate = today()): LocalDate =
    if (!e.lunar) nextOccurrence(e.month, e.day, from)
    else (from.year - 1..from.year + 2).map { occurrenceIn(e, it) }.filter { it >= from }.min()

/** Most recent occurrence strictly before [from]. */
fun previousDate(e: MemoraEvent, from: LocalDate = today()): LocalDate =
    if (!e.lunar) previousOccurrence(e.month, e.day, from)
    else (from.year - 2..from.year + 1).map { occurrenceIn(e, it) }.filter { it < from }.max()

/** Occurrences falling in the given month (at most one for normal events). */
fun occurrencesInMonth(e: MemoraEvent, month: java.time.YearMonth): List<LocalDate> =
    (if (!e.lunar) listOf(dateInYear(month.year, e.month, e.day)) else (month.year - 1..month.year + 1).map { occurrenceIn(e, it) })
        .filter { java.time.YearMonth.from(it) == month }

/** "Bhadrapada Shukla Tritiya" for lunar events, else null. */
fun lunarLabel(e: MemoraEvent): String? = e.originalDate()?.let { Lunar.describe(Lunar.dayOf(it)) }

fun daysUntil(date: LocalDate, from: LocalDate = today()): Int = ChronoUnit.DAYS.between(from, date).toInt()

/** "Thu, 8 Oct" */
fun shortDate(d: LocalDate): String = "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)}"

/** "8 October 1990" */
fun longDate(month: Int, day: Int, year: Int? = null): String = "$day ${MONTHS[month - 1]}${if (year != null) " $year" else ""}"

/** "8 Oct 2026": unambiguous whatever the phone's date format. */
fun dayMonthYear(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1].take(3)} ${d.year}"

fun leftLabel(n: Int) = when (n) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "$n days"
}

fun offsetLabel(n: Int) = when (n) {
    7 -> "1 week"
    14 -> "2 weeks"
    1 -> "1 day"
    else -> "$n days"
}

fun ordinal(n: Int): String {
    val v = n % 100
    val suffix = if (v in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

/** Age or years married on the given occurrence, or null without a year. */
fun yearsOn(e: MemoraEvent, on: LocalDate): Int? {
    val y = e.year ?: return null
    // A lunar date can cross into the next or previous Gregorian year, so count whole years elapsed.
    val n = if (e.lunar) Math.round(ChronoUnit.DAYS.between(dateInYear(y, e.month, e.day), on) / 365.2425).toInt() else on.year - y
    return if (n > 0) n else null
}

/** "turns 32" / "8th anniversary" for the given occurrence, or null without a year. */
fun milestone(e: MemoraEvent, on: LocalDate): String? {
    val n = yearsOn(e, on) ?: return null
    return when (e.type) {
        EventType.BIRTHDAY -> "turns $n"
        EventType.ANNIVERSARY -> "${ordinal(n)} anniversary"
        EventType.OTHER -> "${ordinal(n)} year"
    }
}

private val BIRTHDAY_MILESTONES = setOf(1, 18, 21, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)
private val ANNIVERSARY_MILESTONES = setOf(1, 5, 10, 25, 40, 50, 60)

/** Big birthdays (18th, 25th, 50th…) and anniversaries (10th, 25th…) get an extra early reminder. */
fun isMilestone(e: MemoraEvent, on: LocalDate): Boolean {
    val n = yearsOn(e, on) ?: return false
    return when (e.type) {
        EventType.BIRTHDAY -> n in BIRTHDAY_MILESTONES
        EventType.ANNIVERSARY -> n in ANNIVERSARY_MILESTONES
        EventType.OTHER -> false
    }
}

/** Days before a milestone that the extra reminder fires. */
const val MILESTONE_OFFSET = 14

/** What the event is called: "Birthday", "Anniversary" or the custom event name. */
fun eventLabel(e: MemoraEvent): String = if (e.type == EventType.OTHER) e.title?.ifBlank { null } ?: "Event" else e.type.label

/** "Ravi's birthday", "Deepa's Housewarming". */
fun possessiveTitle(e: MemoraEvent): String {
    val label = if (e.type == EventType.OTHER) e.title?.ifBlank { null } ?: "event" else e.type.label.lowercase()
    return "${e.name}'s $label"
}

private val PLACEHOLDERS = Regex("""\{(name|first|age|years|ordinal)\}""", RegexOption.IGNORE_CASE)

fun hasPlaceholders(message: String) = PLACEHOLDERS.containsMatchIn(message)

/** Fills {name}, {first}, {age} (or {years}) and {ordinal} ("25th") for the given occurrence. */
fun fillMessage(message: String, name: String, year: Int?, on: LocalDate): String =
    fill(message, name, year?.let { on.year - it }?.takeIf { it > 0 })

private fun fill(message: String, name: String, n: Int?): String {
    val trimmed = name.trim()
    val first = trimmed.split(Regex("\\s+")).firstOrNull() ?: trimmed
    return PLACEHOLDERS.replace(message) { m ->
        when (m.groupValues[1].lowercase()) {
            "name" -> trimmed
            "first" -> first
            "ordinal" -> n?.let { ordinal(it) } ?: ""
            else -> n?.toString() ?: ""
        }
    }.replace(Regex(" {2,}"), " ").replace(Regex(" ([,!.])"), "$1")
}

fun fillMessage(message: String, e: MemoraEvent, on: LocalDate) = fill(message, e.name, yearsOn(e, on))
