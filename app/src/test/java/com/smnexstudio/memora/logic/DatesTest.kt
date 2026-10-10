package com.smnexstudio.memora.logic

import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    private fun event(type: EventType, month: Int, day: Int, year: Int?) = MemoraEvent(
        id = 1, type = type, name = "Ananya Rao", title = null, month = month, day = day, year = year,
        phone = null, email = null, message = "", offsets = listOf(1), notes = null, contactId = null, lastSentYear = null,
    )

    @Test fun leapDayFallsOnTheTwentyEighthInOtherYears() {
        assertEquals(LocalDate.of(2027, 2, 28), dateInYear(2027, 2, 29))
        assertEquals(LocalDate.of(2028, 2, 29), dateInYear(2028, 2, 29))
    }

    @Test fun nextOccurrenceIsTodayOrLater() {
        val today = LocalDate.of(2026, 10, 9)
        assertEquals(today, nextOccurrence(10, 9, today))
        assertEquals(LocalDate.of(2027, 10, 8), nextOccurrence(10, 8, today))
        assertEquals(LocalDate.of(2026, 10, 12), nextOccurrence(10, 12, today))
    }

    @Test fun previousOccurrenceIsBeforeToday() {
        val today = LocalDate.of(2026, 10, 9)
        assertEquals(LocalDate.of(2026, 10, 8), previousOccurrence(10, 8, today))
        assertEquals(LocalDate.of(2025, 10, 9), previousOccurrence(10, 9, today))
    }

    @Test fun ordinals() {
        assertEquals("1st", ordinal(1)); assertEquals("2nd", ordinal(2)); assertEquals("3rd", ordinal(3))
        assertEquals("11th", ordinal(11)); assertEquals("12th", ordinal(12)); assertEquals("13th", ordinal(13))
        assertEquals("21st", ordinal(21)); assertEquals("22nd", ordinal(22)); assertEquals("27th", ordinal(27))
        assertEquals("101st", ordinal(101)); assertEquals("111th", ordinal(111))
    }

    @Test fun yearsAndMilestones() {
        val birthday = event(EventType.BIRTHDAY, 10, 9, 1999)
        assertEquals(27, yearsOn(birthday, LocalDate.of(2026, 10, 9)))
        assertEquals("turns 27", milestone(birthday, LocalDate.of(2026, 10, 9)))
        assertFalse(isMilestone(birthday, LocalDate.of(2026, 10, 9)))
        assertTrue(isMilestone(birthday, LocalDate.of(2024, 10, 9))) // 25th

        val anniversary = event(EventType.ANNIVERSARY, 10, 12, 2016)
        assertEquals("10th anniversary", milestone(anniversary, LocalDate.of(2026, 10, 12)))
        assertTrue(isMilestone(anniversary, LocalDate.of(2026, 10, 12)))

        assertNull(yearsOn(event(EventType.BIRTHDAY, 1, 2, null), LocalDate.of(2027, 1, 2)))
    }

    @Test fun fillsPlaceholders() {
        val on = LocalDate.of(2026, 10, 9)
        assertEquals(
            "Happy 27th birthday, Ananya! Love, from all of us to Ananya Rao (27).",
            fillMessage("Happy {ordinal} birthday, {first}! Love, from all of us to {name} ({age}).", "Ananya Rao", 1999, on),
        )
        // Without a year, age placeholders disappear and the spacing is tidied up.
        assertEquals("Happy birthday, Kavya!", fillMessage("Happy {ordinal} birthday, {first}!", "Kavya", null, on))
    }
}
