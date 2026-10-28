package com.smnexstudio.memora.ui

import android.content.Intent
import android.net.Uri
import androidx.navigation.navDeepLink
import androidx.test.core.app.ApplicationProvider
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.platform.LinkGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Typed routes, the memora:// links notifications and the widget use, and the guard on them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteTest {
    @Before fun freshDatabase() {
        Repo.resetForTests(ApplicationProvider.getApplicationContext())
        Repo.eraseAll()
    }

    @Test fun deepLinksKeepTheirAddresses() {
        // Notifications and the widget build memora://event/5 and memora://approve/5?channel=sms.
        assertEquals("memora://event/{id}", navDeepLink<Route.Event>(basePath = "memora://event").uriPattern)
        val approve = navDeepLink<Route.Approve>(basePath = "memora://approve").uriPattern!!
        assertEquals(true, approve.startsWith("memora://approve/{id}?"))
        assertEquals(true, "channel={channel}" in approve)
    }

    @Test fun routeHelpers() {
        assertEquals(Route.Edit(date = "2026-11-06"), Route.Edit.on(LocalDate.of(2026, 11, 6)))
        assertEquals(Route.Approve(5, year = 2026, belated = true), Route.Approve.late(5, LocalDate.of(2026, 10, 27)))
    }

    @Test fun linksFromOtherAppsAreDropped() {
        val outside = Intent(Intent.ACTION_VIEW, Uri.parse("memora://approve/5"))
        assertNull(LinkGuard.clean(outside).data)

        val own = LinkGuard.stamp(Intent(Intent.ACTION_VIEW, Uri.parse("memora://approve/5")))
        assertEquals("memora://approve/5", LinkGuard.clean(own).data.toString())
    }

    @Test fun keptLinksLoseNewTaskSoNavigationDoesntRestartWithoutTheToken() {
        // Notifications start Memora with NEW_TASK. Left on, Navigation relaunches the activity with
        // a copy of the link that no longer has the token, and that copy gets dropped.
        val own = LinkGuard.stamp(
            Intent(Intent.ACTION_VIEW, Uri.parse("memora://approve/5")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        val cleaned = LinkGuard.clean(own)
        assertEquals(0, cleaned.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, cleaned.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
