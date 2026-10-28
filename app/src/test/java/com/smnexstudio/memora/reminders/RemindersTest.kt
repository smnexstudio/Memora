package com.smnexstudio.memora.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smnexstudio.memora.data.EventType
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MILESTONE_OFFSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** People reminders share one alarm, so hundreds of people can't hit Android's limit of 500. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemindersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun freshDatabase() {
        Repo.resetForTests(context)
        Repo.eraseAll()
    }

    private fun event(id: Long, month: Int, day: Int, year: Int? = 1996, offsets: List<Int> = listOf(3, 1)) = MemoraEvent(
        id = id, type = EventType.BIRTHDAY, name = "Person $id", title = null, month = month, day = day, year = year,
        phone = null, email = null, message = "", offsets = offsets, notes = null, contactId = null, lastSentYear = null,
    )

    @Test fun planListsEachReminderAtTheReminderTime() {
        // Default reminder time is 9:00. Dev turns 30 on 6 Nov 2026, a milestone.
        val dev = event(1, 11, 6)
        val plan = Reminders.plan(listOf(dev), LocalDateTime.of(2026, 10, 1, 12, 0))
        assertEquals(
            listOf(
                Reminders.Due(LocalDateTime.of(2026, 10, 23, 9, 0), 1, MILESTONE_OFFSET),
                Reminders.Due(LocalDateTime.of(2026, 11, 3, 9, 0), 1, 3),
                Reminders.Due(LocalDateTime.of(2026, 11, 5, 9, 0), 1, 1),
                Reminders.Due(LocalDateTime.of(2026, 11, 6, 9, 0), 1, 0),
            ),
            plan,
        )
    }

    @Test fun dueIsWhatFellBetweenTheLastRunAndNow() {
        val events = listOf(event(1, 11, 6), event(2, 11, 3, year = null, offsets = listOf(1)))
        val due = Reminders.due(events, LocalDateTime.of(2026, 11, 3, 8, 59), LocalDateTime.of(2026, 11, 3, 9, 0, 5))
        assertEquals(setOf(1L to 3, 2L to 0), due.map { it.eventId to it.offset }.toSet())
        // The run after that starts from its own time, so nothing is sent twice.
        assertTrue(Reminders.due(events, LocalDateTime.of(2026, 11, 3, 9, 0, 5), LocalDateTime.of(2026, 11, 3, 9, 0, 6)).isEmpty())
    }

    @Test fun hundredsOfPeopleUseOneAlarm() {
        Repo.saveEvents((1..400).map { event(0, it % 12 + 1, it % 28 + 1) })
        Reminders.syncAll(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        // One shared people alarm plus the weekly digest; festivals are off.
        assertTrue("${alarms.size} alarms", alarms.size <= 3)
    }
}
