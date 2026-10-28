package com.smnexstudio.memora.data

import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The database, backups (export, restore, what restore refuses), typed settings and erasing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepoTest {
    @Before fun freshDatabase() {
        Repo.resetForTests(ApplicationProvider.getApplicationContext())
        Repo.eraseAll()
    }

    private fun event(name: String, month: Int = 11, day: Int = 6) = MemoraEvent(
        id = 0, type = EventType.BIRTHDAY, name = name, title = null, month = month, day = day, year = 1996,
        phone = "+91 98450 12345", email = null, message = "Happy birthday {name}", offsets = listOf(3, 1),
        notes = null, contactId = "c1", lastSentYear = null,
    )

    private fun backup(events: JSONArray, settings: JSONArray = JSONArray()) =
        JSONObject().put("app", "memora").put("version", 1).put("events", events).put("settings", settings).toString()

    private fun setting(key: String, value: String) = JSONObject().put("key", key).put("value", value)

    @Test fun savesAndReadsFromMemory() {
        val id = Repo.saveEvent(event("Ananya Rao"))
        assertEquals("Ananya Rao", Repo.getEvent(id)?.name)
        assertEquals(1, Repo.events.value.size)
        assertEquals(setOf("c1"), Repo.importedContactIds())
    }

    @Test fun deletingAnEventDeletesItsHistoryAndNotes() {
        val id = Repo.saveEvent(event("Ananya Rao"))
        Repo.markSent(id, 2026, "whatsapp", "hi")
        Repo.addNote(id, NoteKind.IDEA, "Books", null)
        Repo.deleteEvent(id)
        assertNull(Repo.getEvent(id))
        assertTrue(Repo.listHistory(id).isEmpty())
        assertTrue(Repo.listNotes(id).isEmpty())
    }

    @Test fun refusesFilesThatArentMemoraBackups() {
        assertThrows(IllegalArgumentException::class.java) { Repo.importJson("not json") }
        assertThrows(IllegalArgumentException::class.java) { Repo.importJson("""{"app":"other","events":[]}""") }
    }

    @Test fun restoreSkipsBrokenEvents() {
        val good = JSONObject().put("id", 1).put("type", "birthday").put("name", "Dev").put("message", "").put("month", 11).put("day", 6)
        val badMonth = JSONObject(good.toString()).put("id", 2).put("month", 13)
        val noName = JSONObject(good.toString()).put("id", 3).put("name", "")
        val badType = JSONObject(good.toString()).put("id", 4).put("type", "wedding-of-the-year")
        assertEquals(1, Repo.importJson(backup(JSONArray().put(good).put(badMonth).put(noName).put(badType))))
        assertEquals(listOf("Dev"), Repo.events.value.map { it.name })
    }

    @Test fun restoreTakesOnlyAllowedSettingsWithValidValues() {
        val settings = JSONArray()
            .put(setting("reminderHour", "7"))
            .put(setting("reminderMinute", "75"))      // out of range
            .put(setting("themeStyle", "board"))
            .put(setting("appLock", "1"))              // stays on the phone it was set on
            .put(setting("appPinHash", "AAAA"))
            .put(setting("linkToken", "attacker"))
        Repo.importJson(backup(JSONArray(), settings))
        assertEquals(7, Prefs.reminderHour.get())
        assertEquals(0, Prefs.reminderMinute.get())
        assertEquals("board", Prefs.themeStyle.get())
        assertFalse(Prefs.appLock.get())
        assertEquals("", Prefs.appPinHash.get())
        assertEquals("", Prefs.linkToken.get())
    }

    @Test fun exportLeavesOutWhatStaysOnThePhone() {
        Prefs.appLock.set(true)
        Prefs.appPinHash.set("secret-hash")
        Prefs.updateLatest.set("{}")
        Prefs.reminderHour.set(8)
        val keys = JSONObject(Repo.exportJson()).getJSONArray("settings").let { a -> List(a.length()) { a.getJSONObject(it).getString("key") } }
        assertTrue("reminderHour" in keys)
        assertFalse("appLock" in keys)
        assertFalse("appPinHash" in keys)
        assertFalse("updateLatest" in keys)
    }

    @Test fun exportThenRestoreKeepsEverything() {
        val id = Repo.saveEvent(event("Ananya Rao"))
        Repo.markSent(id, 2025, "sms", "Happy birthday")
        Repo.addNote(id, NoteKind.IDEA, "Books", null)
        val json = Repo.exportJson()
        Repo.eraseAll()
        assertEquals(1, Repo.importJson(json))
        val e = Repo.events.value.single()
        assertEquals("Ananya Rao", e.name)
        assertNull(e.contactId) // contact ids differ between phones
        assertEquals(1, Repo.listHistory(e.id).size)
        assertEquals("Books", Repo.listNotes(e.id).single().text)
    }

    @Test fun eraseAllLeavesNothing() {
        val id = Repo.saveEvent(event("Ananya Rao"))
        Repo.addNote(id, NoteKind.IDEA, "Books", null)
        Prefs.signature.set("SM")
        Prefs.appPinHash.set("hash")
        Repo.eraseAll()
        assertTrue(Repo.events.value.isEmpty())
        assertTrue(Repo.settings.value.isEmpty())
        assertTrue(Repo.allNotes().isEmpty())
    }

    @Test fun typedSettingsFallBackToTheirDefault() {
        Repo.writeSetting("reminderHour", "99")
        Repo.writeSetting("alertStyle", "foghorn")
        Repo.writeSetting("festivals", "not json")
        assertEquals(9, Prefs.reminderHour.get())
        assertEquals(com.smnexstudio.memora.reminders.AlertStyle.NOTIFICATION, Prefs.alertStyle.get())
        assertEquals(emptyList<String>(), Prefs.festivals.get())
        Prefs.defaultOffsets.set(listOf(7, 1))
        assertEquals(listOf(7, 1), Prefs.defaultOffsets.get())
    }

    @Test fun oldFestivalIdsAreRenamed() {
        Repo.writeSetting("festivals", """["diwali","new-years-day"]""")
        assertEquals(listOf("diwali-deepavali", "new-years-day"), Prefs.festivals.get())
    }
}
