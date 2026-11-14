package com.baklavapp.reachlauncher.core.system.roles

import android.app.Application
import android.app.role.RoleManager
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeRoleSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `recheck re-reads the home role`() =
        runTest {
            val source = RoleHomeRoleSource(app, UnconfinedTestDispatcher(testScheduler))
            source.isDefaultHome.test {
                assertThat(awaitItem()).isFalse()
                // Deprecated in Robolectric in favour of per-package holders; still the simplest
                // way to make isRoleHeld() true for the app under test.
                @Suppress("DEPRECATION")
                shadowOf(app.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_HOME)
                source.recheck()
                assertThat(awaitItem()).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
