package com.smnexstudio.memora.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPinTest {
    @Before fun freshDatabase() {
        Repo.resetForTests(ApplicationProvider.getApplicationContext())
        Repo.eraseAll()
    }

    @Test fun storesOnlyAHash() {
        AppPin.set("482913")
        assertTrue(AppPin.isSet())
        assertFalse(Repo.settings.value.values.any { it.contains("482913") })
        assertEquals(AppPin.Check.Ok, AppPin.check("482913"))
    }

    @Test fun rejectsBadPins() {
        assertFalse(AppPin.valid("123"))
        assertFalse(AppPin.valid("1234567"))
        assertFalse(AppPin.valid("12a4"))
        assertTrue(AppPin.valid("1234"))
    }

    @Test fun waitsAfterFiveWrongTries() {
        AppPin.set("482913")
        repeat(4) { i -> assertEquals(AppPin.Check.Wrong(4 - i), AppPin.check("000000")) }
        val fifth = AppPin.check("000000")
        assertTrue(fifth is AppPin.Check.Wait && fifth.seconds == 30L)
        // Even the right PIN has to wait now.
        assertTrue(AppPin.check("482913") is AppPin.Check.Wait)
    }

    @Test fun clearingRemovesIt() {
        AppPin.set("4829")
        AppPin.clear()
        assertFalse(AppPin.isSet())
    }
}
