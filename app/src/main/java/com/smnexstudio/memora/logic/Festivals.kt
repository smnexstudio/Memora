package com.smnexstudio.memora.logic

import java.time.LocalDate

data class Festival(
    val id: String,
    val name: String,
    /** Google marks the date tentative (usually moon sighting); it may move by a day. */
    val approximate: Boolean = false,
)

/** A holiday calendar Google publishes ("en.<id>#holiday@group.v.calendar.google.com"). */
data class HolidayCalendar(val code: String, val name: String, val googleId: String)

val CALENDARS = listOf(
    HolidayCalendar("IN", "India", "indian"),
    HolidayCalendar("US", "United States", "usa"),
    HolidayCalendar("GB", "United Kingdom", "uk"),
    HolidayCalendar("CA", "Canada", "canadian"),
    HolidayCalendar("AU", "Australia", "australian"),
    HolidayCalendar("NZ", "New Zealand", "new_zealand"),
    HolidayCalendar("IE", "Ireland", "irish"),
    HolidayCalendar("SG", "Singapore", "singapore"),
    HolidayCalendar("MY", "Malaysia", "malaysia"),
    HolidayCalendar("AE", "United Arab Emirates", "ae"),
    HolidayCalendar("SA", "Saudi Arabia", "saudiarabian"),
    HolidayCalendar("ZA", "South Africa", "sa"),
    HolidayCalendar("LK", "Sri Lanka", "lk"),
    HolidayCalendar("PK", "Pakistan", "pk"),
    HolidayCalendar("BD", "Bangladesh", "bd"),
    HolidayCalendar("PH", "Philippines", "philippines"),
    HolidayCalendar("DE", "Germany", "german"),
    HolidayCalendar("FR", "France", "french"),
    HolidayCalendar("JP", "Japan", "japanese"),
    HolidayCalendar("HINDU", "Hindu holidays", "hinduism"),
    HolidayCalendar("CHRISTIAN", "Christian holidays", "christian"),
    HolidayCalendar("ISLAMIC", "Islamic holidays", "islamic"),
)

/** At most this many calendars can be shown together. */
const val MAX_CALENDARS = 2

fun calendarByCode(code: String) = CALENDARS.firstOrNull { it.code == code }

/** The chosen calendars, e.g. [India, Hindu holidays]. */
fun chosenCalendars(codes: List<String>): List<HolidayCalendar> = codes.mapNotNull(::calendarByCode).ifEmpty { listOf(CALENDARS.first()) }

/** "India + Hindu holidays" */
fun calendarsLabel(codes: List<String>) = chosenCalendars(codes).joinToString(" + ") { it.name }

// No festivals or dates are kept in code: GoogleHolidays downloads the chosen
// calendars' holidays into the database.

/** Festivals of the chosen calendars, in calendar order. */
fun availableFestivals(): List<Festival> = GoogleHolidays.festivals

fun festivalById(id: String) = availableFestivals().firstOrNull { it.id == id }

fun festivalDate(f: Festival, year: Int): LocalDate? = GoogleHolidays.date(f.id, year)

/** Next date on or after [from] among the downloaded years. */
fun nextFestivalDate(f: Festival, from: LocalDate = today()): LocalDate? =
    (from.year..from.year + 1).firstNotNullOfOrNull { y -> festivalDate(f, y)?.takeIf { it >= from } }

/** Enabled festivals falling in the given month (1-12). */
fun festivalsInMonth(ids: List<String>, year: Int, month: Int): List<Pair<Festival, LocalDate>> =
    availableFestivals().filter { it.id in ids }.mapNotNull { f -> festivalDate(f, year)?.takeIf { it.monthValue == month }?.let { f to it } }
