package com.smnexstudio.memora.logic

import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Hindu lunar dates (amanta months, as used in Karnataka and most of the south and west),
 * so an event can repeat on the same tithi each year instead of the same Gregorian date.
 *
 * The tithi is the one running at 6 AM local time (a stand-in for sunrise). Sun and moon
 * positions use the low-precision series from Meeus, "Astronomical Algorithms" (ch. 25, 47),
 * accurate to well under an hour of tithi timing, so a date can very rarely differ by a
 * day from a printed panchang.
 */
object Lunar {
    val MONTHS = listOf(
        "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
        "Ashvija", "Kartika", "Margashira", "Pushya", "Magha", "Phalguna",
    )
    private val TITHIS = listOf(
        "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami",
        "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi",
    )

    /** [month] 0 = Chaitra; [tithi] 1–15 bright half (Shukla), 16–30 dark half (Krishna). */
    data class Day(val month: Int, val tithi: Int, val adhika: Boolean)

    /** "Bhadrapada Shukla Chaturthi" */
    fun describe(d: Day): String {
        val paksha = if (d.tithi <= 15) "Shukla" else "Krishna"
        val name = when (d.tithi) {
            15 -> "Purnima"
            30 -> "Amavasya"
            else -> TITHIS[(d.tithi - 1) % 15]
        }
        return "${if (d.adhika) "Adhika " else ""}${MONTHS[d.month]} ${if (d.tithi == 15 || d.tithi == 30) "" else "$paksha "}$name"
    }

    private val days = ConcurrentHashMap<LocalDate, Day>()

    fun dayOf(date: LocalDate): Day = days.getOrPut(date) {
        val jd = julianDay(date)
        val e = elongation(jd)
        val prev = newMoon(jd - e / MOON_RATE)
        val next = newMoon(prev + 29.53)
        val rashi = rashi(prev)
        Day((rashi + 1) % 12, tithiAt(jd), rashi == rashi(next))
    }

    /**
     * The date near [year]'s Gregorian anniversary of [original] with the same lunar month and tithi.
     * If that tithi is skipped that year (kshaya), the next day's tithi is used. Null if not found.
     */
    fun anniversary(original: LocalDate, year: Int): LocalDate? {
        val want = dayOf(original)
        val center = dateInYear(year, original.monthValue, original.dayOfMonth)
        val window = (-35L..35L).map { center.plusDays(it) }
        // Tithi first: it's cheap, and the month only needs working out for the few days that match.
        fun pick(tithi: Int, month: Int) = window
            .filter { tithiAt(julianDay(it)) == tithi }
            .filter { dayOf(it).let { d -> d.month == month && !d.adhika } }
            .minByOrNull { abs(it.toEpochDay() - center.toEpochDay()) }
        return pick(want.tithi, want.month)
            ?: if (want.tithi < 30) pick(want.tithi + 1, want.month) else pick(1, (want.month + 1) % 12)
    }

    // ---- Astronomy ----

    private const val MOON_RATE = 12.1907 // mean degrees per day the moon gains on the sun

    private fun julianDay(date: LocalDate): Double {
        val at = date.atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return at / 86_400_000.0 + 2440587.5
    }

    private fun tithiAt(jd: Double) = floor(elongation(jd) / 12).toInt() + 1

    private fun norm(x: Double) = ((x % 360) + 360) % 360
    private fun sinDeg(x: Double) = sin(Math.toRadians(x))

    private fun sunLongitude(jd: Double): Double {
        val t = (jd - 2451545) / 36525
        val l0 = 280.46646 + 36000.76983 * t
        val m = 357.52911 + 35999.05029 * t
        val c = (1.914602 - 0.004817 * t) * sinDeg(m) + 0.019993 * sinDeg(2 * m) + 0.000289 * sinDeg(3 * m)
        return norm(l0 + c)
    }

    // Main periodic terms of the moon's longitude: coefficient (1e-6 degrees), D, M, M', F.
    private val MOON_TERMS = arrayOf(
        intArrayOf(6288774, 0, 0, 1, 0), intArrayOf(1274027, 2, 0, -1, 0), intArrayOf(658314, 2, 0, 0, 0),
        intArrayOf(213618, 0, 0, 2, 0), intArrayOf(-185116, 0, 1, 0, 0), intArrayOf(-114332, 0, 0, 0, 2),
        intArrayOf(58793, 2, 0, -2, 0), intArrayOf(57066, 2, -1, -1, 0), intArrayOf(53322, 2, 0, 1, 0),
        intArrayOf(45758, 2, -1, 0, 0), intArrayOf(-40923, 0, 1, -1, 0), intArrayOf(-34720, 1, 0, 0, 0),
        intArrayOf(-30383, 0, 1, 1, 0), intArrayOf(15327, 2, 0, 0, -2), intArrayOf(-12528, 0, 0, 1, 2),
        intArrayOf(10980, 0, 0, 1, -2), intArrayOf(10675, 4, 0, -1, 0), intArrayOf(10034, 0, 0, 3, 0),
        intArrayOf(8548, 4, 0, -2, 0), intArrayOf(-7888, 2, 1, -1, 0), intArrayOf(-6766, 2, 1, 0, 0),
        intArrayOf(-5163, 1, 0, -1, 0), intArrayOf(4987, 1, 1, 0, 0), intArrayOf(4036, 2, -1, 1, 0),
        intArrayOf(3994, 2, 0, 2, 0), intArrayOf(3861, 4, 0, 0, 0), intArrayOf(3665, 2, 0, -3, 0),
        intArrayOf(-2689, 0, 1, -2, 0), intArrayOf(-2602, 2, 0, -1, 2), intArrayOf(2390, 2, -1, -2, 0),
        intArrayOf(-2348, 1, 0, 1, 0), intArrayOf(2236, 2, -2, 0, 0), intArrayOf(-2120, 0, 1, 2, 0),
        intArrayOf(-2069, 0, 2, 0, 0),
    )

    private fun moonLongitude(jd: Double): Double {
        val t = (jd - 2451545) / 36525
        val l = 218.3164477 + 481267.88123421 * t
        val d = 297.8501921 + 445267.1114034 * t
        val m = 357.5291092 + 35999.0502909 * t
        val mp = 134.9633964 + 477198.8675055 * t
        val f = 93.2720950 + 483202.0175233 * t
        val e = 1 - 0.002516 * t
        var sum = 0.0
        for ((coef, kd, km, kmp, kf) in MOON_TERMS) {
            val ecc = when (abs(km)) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sum += coef * ecc * sinDeg(kd * d + km * m + kmp * mp + kf * f)
        }
        sum += 3958 * sinDeg(119.75 + 131.849 * t) + 1962 * sinDeg(l - f) + 318 * sinDeg(53.09 + 479264.290 * t)
        return norm(l + sum / 1_000_000)
    }

    private fun elongation(jd: Double) = norm(moonLongitude(jd) - sunLongitude(jd))

    /** The new moon closest to [guess]. */
    private fun newMoon(guess: Double): Double {
        var jd = guess
        repeat(6) {
            var e = elongation(jd)
            if (e > 180) e -= 360
            jd -= e / MOON_RATE
        }
        return jd
    }

    /** Sidereal sign of the sun (0 = Mesha), using Lahiri's ayanamsa. */
    private fun rashi(jd: Double): Int {
        val ayanamsa = 23.853 + (jd - 2451545) / 365.25 * 0.013969
        return floor(norm(sunLongitude(jd) - ayanamsa) / 30).toInt()
    }
}
