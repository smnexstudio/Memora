package com.smnexstudio.memora.ui

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Every screen, with what it needs to open. Navigation builds its routes from these, so a
 * missing or misspelt argument is a compile error. Links from notifications and the widget
 * (memora://event/5, memora://approve/5?channel=sms) map onto them in MainActivity.
 */
@Serializable
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Calendar : Route
    @Serializable data object People : Route
    @Serializable data object Settings : Route
    @Serializable data object Search : Route

    /** A new event when [id] is 0, starting on [date] (yyyy-mm-dd) if given; otherwise editing event [id]. */
    @Serializable
    data class Edit(val id: Long = 0, val date: String? = null) : Route {
        companion object {
            fun on(date: LocalDate) = Edit(date = date.toString())
        }
    }

    @Serializable data class Event(val id: Long) : Route

    /**
     * Writing a card to event [id]. [year] is the occurrence it's for (0: the next one), with
     * [belated] for a day already past; [channel] opens straight on that way of sending.
     */
    @Serializable
    data class Approve(val id: Long, val channel: String? = null, val year: Int = 0, val belated: Boolean = false) : Route {
        companion object {
            fun late(id: Long, on: LocalDate) = Approve(id, year = on.year, belated = true)
        }
    }

    @Serializable data object Import : Route
    @Serializable data object Festivals : Route
    @Serializable data object Missed : Route
    @Serializable data object Checks : Route
    @Serializable data object Widget : Route
    @Serializable data object Update : Route
}
