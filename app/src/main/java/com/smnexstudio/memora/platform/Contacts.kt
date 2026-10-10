package com.smnexstudio.memora.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.core.content.ContextCompat
import com.smnexstudio.memora.data.EventType

data class LabeledValue(val value: String, val label: String)

data class ContactMatch(val id: String, val name: String, val phones: List<LabeledValue>, val email: LabeledValue?)

data class ContactDate(
    val key: String, // contactId:type
    val name: String,
    val type: EventType,
    val month: Int,
    val day: Int,
    val year: Int?,
    val phone: String?,
    val email: String?,
)

object ContactsRepo {
    fun hasAccess(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun names(context: Context): Map<String, String> = buildMap {
        context.contentResolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                put(c.getString(0), name)
            }
        }
    }

    private fun phones(context: Context): Map<String, List<LabeledValue>> {
        val out = linkedMapOf<String, MutableList<LabeledValue>>()
        context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.IS_PRIMARY),
            null, null, "${Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
                val list = out.getOrPut(c.getString(0)) { mutableListOf() }
                if (list.none { it.value.filter(Char::isDigit) == number.filter(Char::isDigit) }) list += LabeledValue(number, label)
            }
        }
        return out
    }

    private fun emails(context: Context): Map<String, LabeledValue> = buildMap {
        context.contentResolver.query(
            Email.CONTENT_URI, arrayOf(Email.CONTACT_ID, Email.ADDRESS, Email.TYPE, Email.LABEL),
            null, null, "${Email.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val address = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                if (!containsKey(id)) put(id, LabeledValue(address, Email.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()))
            }
        }
    }

    // Reading the whole address book is slow on big contact lists; reuse it briefly.
    private var cache: Pair<Long, List<ContactMatch>>? = null

    /** Every contact with a phone number or email, for matching while typing. */
    fun reachable(context: Context): List<ContactMatch> {
        if (!hasAccess(context)) return emptyList()
        cache?.let { (at, list) -> if (System.currentTimeMillis() - at < 120_000) return list }
        val n = names(context); val p = phones(context); val e = emails(context)
        val list = n.mapNotNull { (id, name) ->
            val ph = p[id].orEmpty(); val em = e[id]
            if (ph.isEmpty() && em == null) null else ContactMatch(id, name, ph, em)
        }
        cache = System.currentTimeMillis() to list
        return list
    }

    /**
     * Best contacts for what's typed: by phone digits once 3+ are entered,
     * otherwise by name (each person of a couple is matched on their own).
     * Returns each match with the number that fits.
     */
    fun match(all: List<ContactMatch>, name: String, phone: String, limit: Int = 4): List<Pair<ContactMatch, LabeledValue?>> {
        val typed = phone.filter(Char::isDigit)
        if (typed.length >= 3) {
            return all.asSequence()
                .mapNotNull { c -> c.phones.firstOrNull { it.value.filter(Char::isDigit).contains(typed) }?.let { c to it } }
                .take(limit).toList()
        }
        val q = name.trim().lowercase()
        if (q.length < 2) return emptyList()
        val parts = q.split(Regex("""\s*&\s*|\s+and\s+""")).filter { it.length >= 2 }
        fun score(n: String): Int {
            val lower = n.lowercase()
            return when {
                lower == q -> 0
                lower.startsWith(q) -> 1
                lower.split(Regex("\\s+")).any { w -> parts.any { w.startsWith(it) } } -> 2
                parts.any { lower.contains(it) } -> 3
                else -> -1
            }
        }
        return all.map { it to score(it.name) }.filter { it.second >= 0 }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .take(limit).map { (c, _) -> c to c.phones.firstOrNull() }
    }

    /** Birthdays and anniversaries saved in the phone's contacts. */
    fun dates(context: Context): List<ContactDate> {
        if (!hasAccess(context)) return emptyList()
        val n = names(context); val p = phones(context); val e = emails(context)
        val out = mutableListOf<ContactDate>()
        context.contentResolver.query(
            Data.CONTENT_URI, arrayOf(Event.CONTACT_ID, Event.START_DATE, Event.TYPE),
            "${Data.MIMETYPE} = ? AND ${Event.TYPE} IN (?, ?)",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString(), Event.TYPE_ANNIVERSARY.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = n[id] ?: continue
                val type = if (c.getInt(2) == Event.TYPE_ANNIVERSARY) EventType.ANNIVERSARY else EventType.BIRTHDAY
                val (year, month, day) = parseContactDate(c.getString(1) ?: continue) ?: continue
                val key = "$id:${type.id}"
                if (out.any { it.key == key }) continue
                out += ContactDate(key, name, type, month, day, year, p[id]?.firstOrNull()?.value, e[id]?.value)
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Contact dates come as "1990-05-14", "--05-14" (no year) or similar. */
    private fun parseContactDate(s: String): Triple<Int?, Int, Int>? {
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            return Triple(y.takeIf { it > 1900 }, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""^--(\d{1,2})-(\d{1,2})""").find(s)?.let { m -> return Triple(null, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return null
    }
}
