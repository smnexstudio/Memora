package com.smnexstudio.memora.logic

import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

/** How many days back a missed day can still get a belated card. */
const val BELATED_DAYS = 2

/**
 * Where every event falls, seen from [today]: worked out once for each set of events and each
 * day, and shared by the screens, the widget and the reminders so they all agree on what's due.
 */
class Agenda private constructor(val events: List<MemoraEvent>, val today: LocalDate) {
    /** Each event with its next date (today counts), soonest first, then by name. */
    val upcoming: List<Pair<MemoraEvent, LocalDate>> =
        events.map { it to nextDate(it, today) }.sortedWith(compareBy({ it.second }, { it.first.name }))

    private val nextById: Map<Long, LocalDate> = upcoming.associate { it.first.id to it.second }

    /** Events from the last [BELATED_DAYS] days nobody sent or skipped, newest first. */
    val missed: List<Pair<MemoraEvent, LocalDate>> = events
        .map { it to previousDate(it, today) }
        .filter { (e, d) -> daysUntil(d, today) >= -BELATED_DAYS && (e.lastSentYear ?: 0) < d.year }
        .sortedByDescending { it.second }

    private val months = ConcurrentHashMap<YearMonth, Map<LocalDate, List<MemoraEvent>>>()

    fun next(e: MemoraEvent): LocalDate = nextById[e.id] ?: nextDate(e, today)

    /** The days of [month] that have events, each with its events by name. Worked out once per month. */
    fun inMonth(month: YearMonth): Map<LocalDate, List<MemoraEvent>> = months.getOrPut(month) {
        val days = sortedMapOf<LocalDate, MutableList<MemoraEvent>>()
        for (e in events) for (d in occurrencesInMonth(e, month)) days.getOrPut(d) { mutableListOf() } += e
        days.mapValues { (_, list) -> list.sortedBy { it.name } }
    }

    fun on(date: LocalDate): List<MemoraEvent> = inMonth(YearMonth.from(date))[date].orEmpty()

    companion object {
        @Volatile
        private var last: Agenda? = null

        /** The agenda for [events] as of [today], reusing the last one when neither has changed. */
        fun of(events: List<MemoraEvent>, today: LocalDate = today()): Agenda =
            last?.takeIf { it.events === events && it.today == today } ?: Agenda(events, today).also { last = it }

        fun current(): Agenda = of(Repo.events.value)
    }
}
