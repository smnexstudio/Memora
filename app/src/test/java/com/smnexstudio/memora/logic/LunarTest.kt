package com.smnexstudio.memora.logic

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

/**
 * Lunar dates are checked against festivals whose dates are well known, since a festival
 * falls on the same lunar month and tithi every year. Purnima (full moon) festivals are used
 * because their tithi is the one running at sunrise on the day, which is what Memora uses.
 */
class LunarTest {
    private lateinit var saved: TimeZone

    @Before fun indiaTime() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    @After fun restore() = TimeZone.setDefault(saved)

    @Test fun rakshaBandhanIsShravanaPurnima() {
        val d = Lunar.dayOf(LocalDate.of(2026, 8, 28))
        assertEquals("Shravana", Lunar.MONTHS[d.month])
        assertEquals(15, d.tithi)
    }

    @Test fun rakshaBandhanRepeatsOnTheLunarDate() {
        val original = LocalDate.of(2026, 8, 28)
        assertEquals(LocalDate.of(2027, 8, 17), Lunar.anniversary(original, 2027))
        assertEquals(LocalDate.of(2025, 8, 9), Lunar.anniversary(original, 2025))
        assertEquals(LocalDate.of(2024, 8, 19), Lunar.anniversary(original, 2024))
    }

    @Test fun holiRepeatsOnPhalgunaPurnima() {
        val original = LocalDate.of(2026, 3, 3)
        assertEquals("Phalguna", Lunar.MONTHS[Lunar.dayOf(original).month])
        assertEquals(LocalDate.of(2027, 3, 22), Lunar.anniversary(original, 2027))
        assertEquals(LocalDate.of(2025, 3, 14), Lunar.anniversary(original, 2025))
    }

    @Test fun describeNamesPakshaAndTithi() {
        assertEquals("Shravana Purnima", Lunar.describe(Lunar.Day(4, 15, adhika = false)))
        assertEquals("Bhadrapada Shukla Chaturthi", Lunar.describe(Lunar.Day(5, 4, adhika = false)))
        assertEquals("Ashvija Krishna Dashami", Lunar.describe(Lunar.Day(6, 25, adhika = false)))
        assertEquals("Adhika Jyeshtha Amavasya", Lunar.describe(Lunar.Day(2, 30, adhika = true)))
    }

    @Test fun anniversaryStaysNearTheCalendarDate() {
        // A lunar date drifts against the calendar, but never by more than about a month.
        val original = LocalDate.of(1990, 11, 5)
        for (year in 2024..2035) {
            val d = Lunar.anniversary(original, year)!!
            val drift = d.toEpochDay() - LocalDate.of(year, 11, 5).toEpochDay()
            assert(drift in -35..35) { "$year drifted $drift days" }
        }
    }
}
