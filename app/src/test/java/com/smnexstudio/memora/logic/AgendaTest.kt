package com.smnexstudio.memora.logic

import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AgendaTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun event(id: Long, name: String, month: Int, day: Int, lastSent: Int? = null) = MemoraEvent(
        id = id, type = EventType.BIRTHDAY, name = name, title = null, month = month, day = day, year = 1990,
        phone = null, email = null, message = "", offsets = listOf(1), notes = null, contactId = null, lastSentYear = lastSent,
    )

    private val events = listOf(
        event(1, "Dev", 11, 6),
        event(2, "Ananya", 10, 28),
        event(3, "Divya", 10, 27),                 // yesterday, not sent: missed
        event(4, "Kavya", 10, 26, lastSent = 2026), // two days ago, sent: not missed
        event(5, "Arun", 10, 20),                  // too long ago to count as missed
        event(6, "Bala", 10, 28),
    )

    @Test fun upcomingIsSoonestFirstThenByName() {
        val a = Agenda.of(events, today)
        assertEquals(listOf("Ananya", "Bala", "Dev"), a.upcoming.take(3).map { it.first.name })
        assertEquals(today, a.upcoming.first().second)
        assertEquals(LocalDate.of(2027, 10, 20), a.next(events[4]))
    }

    @Test fun missedIsUnsentWithinTheBelatedWindow() {
        assertEquals(listOf("Divya"), Agenda.of(events, today).missed.map { it.first.name })
    }

    @Test fun daysOfAMonthAreGroupedAndSortedByName() {
        val a = Agenda.of(events, today)
        assertEquals(listOf("Ananya", "Bala"), a.on(today).map { it.name })
        assertTrue(a.on(LocalDate.of(2026, 10, 29)).isEmpty())
        assertEquals(setOf(20, 26, 27, 28), a.inMonth(YearMonth.of(2026, 10)).keys.map { it.dayOfMonth }.toSet())
    }

    @Test fun reusedUntilTheEventsOrTheDayChange() {
        val a = Agenda.of(events, today)
        assertSame(a, Agenda.of(events, today))
        assertTrue(a !== Agenda.of(events, today.plusDays(1)))
        assertTrue(a !== Agenda.of(events.toList(), today))
    }
}
