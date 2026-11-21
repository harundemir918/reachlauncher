package com.baklavapp.reachlauncher.core.system

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import com.baklavapp.reachlauncher.core.system.calendar.CalendarProviderSource
import com.baklavapp.reachlauncher.core.system.media.MediaSessionsSource
import com.baklavapp.reachlauncher.core.system.notifications.NotificationAccess
import com.baklavapp.reachlauncher.core.system.notifications.NotificationEntry
import com.baklavapp.reachlauncher.core.system.notifications.NotificationListenerCountSource
import com.baklavapp.reachlauncher.core.system.notifications.NotificationListenerHub
import com.baklavapp.reachlauncher.core.system.usage.UsageStatsManagerSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock

/**
 * The access-gated sources with access off (the default) report "nothing" without throwing,
 * and pick up a grant on the next recheck (what every Activity resume triggers).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccessGatedSourcesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val rechecks = AccessRechecks()
    private val hub = NotificationListenerHub(app)
    private val access = NotificationAccess(app, rechecks, hub)

    private fun enableListener() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(access.listenerComponent, true)
    }

    @Test
    fun `notification counts are empty without access and follow the hub once granted`() =
        runTest {
            val source = NotificationListenerCountSource(access, hub)
            val chat = PackageUser("com.chat", 0)
            hub.onConnected(listOf(NotificationEntry("a", chat, isOngoing = false, isGroupSummary = false)))
            assertThat(source.isAccessGranted.first()).isFalse()
            assertThat(source.countsByApp.first()).isEmpty()

            source.countsByApp.test {
                assertThat(awaitItem()).isEmpty()
                enableListener()
                rechecks.recheck()
                assertThat(awaitItem()).containsExactly(chat, 1)
                hub.onDisconnected()
                assertThat(awaitItem()).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `media state is null without notification access`() =
        runTest {
            val source = MediaSessionsSource(app, access, ActivityTracker(AccessRechecks()))
            assertThat(source.mediaState.first()).isNull()
            // Commands without a session are no-ops, not crashes.
            source.playPause()
            source.skipToNext()
            source.skipToPrevious()
            assertThat(source.openApp(origin = null)).isFalse()
        }

    @Test
    fun `media state stays null with access but no sessions`() =
        runTest {
            enableListener()
            val source = MediaSessionsSource(app, access, ActivityTracker(AccessRechecks()))
            assertThat(source.mediaState.first()).isNull()
        }

    @Test
    fun `usage stats are empty without access and granted after the app-op is allowed`() =
        runTest {
            val appOps = shadowOf(app.getSystemService(AppOpsManager::class.java))
            appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_IGNORED)
            val source = UsageStatsManagerSource(app, rechecks, Clock.systemUTC(), UnconfinedTestDispatcher(testScheduler))
            assertThat(source.launchesByHour(14)).isEmpty()
            source.isAccessGranted.test {
                assertThat(awaitItem()).isFalse()
                appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
                rechecks.recheck()
                assertThat(awaitItem()).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
            // Granted but no events recorded: still empty, no exception.
            assertThat(source.launchesByHour(14)).isEmpty()
        }

    @Test
    fun `calendar is null without permission and queries safely once granted`() =
        runTest {
            val source = CalendarProviderSource(app, rechecks, Clock.systemUTC(), UnconfinedTestDispatcher(testScheduler))
            assertThat(source.isPermissionGranted.first()).isFalse()
            assertThat(source.nextEvent.first()).isNull()
            shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
            rechecks.recheck()
            assertThat(source.isPermissionGranted.first()).isTrue()
            // Robolectric has no calendar provider: the query fails soft to "no event".
            assertThat(source.nextEvent.first()).isNull()
        }
}
