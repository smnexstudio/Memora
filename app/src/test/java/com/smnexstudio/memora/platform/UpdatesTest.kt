package com.smnexstudio.memora.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun newerVersions() {
        assertTrue(Updates.isNewer("1.1.0", "1.0.0"))
        assertTrue(Updates.isNewer("1.0.10", "1.0.9"))
        assertTrue(Updates.isNewer("2.0.0", "1.9.9"))
        assertFalse(Updates.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updates.isNewer("1.0.0", "1.1.0"))
        // A local build named "1.0.0-dev" is still 1.0.0.
        assertFalse(Updates.isNewer("1.0.0", "1.0.0-dev"))
        assertFalse(Updates.isNewer("1.1", "1.0.0"))
        assertFalse(Updates.isNewer("v1.1.0", "1.0.0"))
    }

    @Test
    fun versionParts() {
        assertEquals(listOf(1, 2, 3), Updates.parts("1.2.3"))
        assertNull(Updates.parts("1.2"))
        assertNull(Updates.parts("1.2.x"))
        assertNull(Updates.parts("1.-2.3"))
    }

    @Test
    fun notesFromChangelog() {
        val body = """
            The second release.

            ### Sending wishes
            - Six new **card** designs, see [the README](https://example.com).
            * A warning when a wish is too long for the card
            -
            ### Fixes
            - `Skip` now refreshes the widget
        """.trimIndent()
        assertEquals(
            listOf("Six new card designs, see the README.", "A warning when a wish is too long for the card", "Skip now refreshes the widget"),
            Updates.notes(body),
        )
    }

    @Test
    fun longNotesAreCut() {
        val notes = Updates.notes((1..10).joinToString("\n") { "- Change $it" })
        assertEquals(6, notes.size)
        assertEquals("Change 5", notes[4])
        assertEquals("And 5 more changes", notes[5])
    }
}
