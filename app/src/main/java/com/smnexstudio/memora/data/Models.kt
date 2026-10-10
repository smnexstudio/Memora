package com.smnexstudio.memora.data

enum class EventType(val id: String, val label: String) {
    BIRTHDAY("birthday", "Birthday"),
    ANNIVERSARY("anniversary", "Anniversary"),
    OTHER("other", "Other");

    companion object {
        fun from(id: String?): EventType? = entries.firstOrNull { it.id == id }
    }
}

enum class Channel(val id: String, val label: String) {
    WHATSAPP("whatsapp", "WhatsApp"),
    SMS("sms", "SMS"),
    EMAIL("email", "Email");

    companion object {
        fun from(id: String?): Channel? = entries.firstOrNull { it.id == id }
    }
}

data class MemoraEvent(
    val id: Long,
    val type: EventType,
    val name: String,          // person / couple
    val title: String?,        // event name, used for OTHER
    val month: Int,            // 1-12
    val day: Int,              // 1-31
    val year: Int?,            // birth / wedding year, optional
    val phone: String?,
    val email: String?,
    val message: String,       // may contain {name}, {first}, {age}, {ordinal}
    val offsets: List<Int>,    // early reminders in days, 1 or 2 values
    val notes: String?,        // gift ideas, what was given before
    val contactId: String?,    // phone contact it was imported from
    val lastSentYear: Int?,
    val lunar: Boolean = false, // repeats on the same Hindu lunar date; needs [year]
    val sendVia: String? = null, // Channel id to skip "How should it reach…?", or null to ask
)

enum class NoteKind(val id: String, val label: String) {
    IDEA("idea", "Gift idea"),
    GIFT("gift", "Gave"),
    NOTE("note", "Note");

    companion object {
        fun from(id: String?): NoteKind = entries.firstOrNull { it.id == id } ?: NOTE
    }
}

/** A dated note about the person: a gift idea, a gift given in [year], or anything else. */
data class PersonNote(
    val id: Long,
    val eventId: Long,
    val kind: NoteKind,
    val text: String,
    val year: Int?,
    val createdAt: Long,
)

/** How a year's wish was handled; "skipped" means the user chose not to send. */
data class HistoryEntry(
    val id: Long,
    val eventId: Long,
    val year: Int,
    val channel: String, // a Channel id or "skipped"
    val message: String?,
    val sentAt: Long,
) {
    val channelLabel: String get() = Channel.from(channel)?.label ?: "Skipped"
}

/** One festival on one date, as downloaded from Google Calendar. */
data class FestivalRow(
    val id: String,
    val name: String,
    val date: java.time.LocalDate,
    val approximate: Boolean,
)

/** Early reminder choices, in days before the event. The user picks 1 or 2. */
val OFFSET_OPTIONS = listOf(7 to "1 week", 3 to "3 days", 2 to "2 days", 1 to "1 day")
