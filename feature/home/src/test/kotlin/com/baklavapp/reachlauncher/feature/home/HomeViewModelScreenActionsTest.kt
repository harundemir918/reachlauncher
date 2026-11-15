package com.baklavapp.reachlauncher.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * M5 screen actions of [HomeViewModel] (spec §7.4, D66): lock and notification shade through
 * the optional accessibility service, the hint without it, and the gesture state the UI uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelScreenActionsTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe(fixture: HomeFixture) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.system.collect {} }
    }

    @Test
    fun `lock without the service shows a hint and does nothing else`() =
        runTest {
            val f = HomeFixture(sampleApps)
            f.viewModel.events.test {
                f.viewModel.onLockScreenRequested()
                assertThat(awaitItem()).isEqualTo(HomeEvent.ScreenActionUnavailable(ScreenAction.LOCK))
            }
            assertThat(f.screenActions.lockCount).isEqualTo(0)
            assertThat(f.launcher.openedScreens).isEmpty() // Never jumps to Settings uninvited.
        }

    @Test
    fun `lock with the service locks and sends no hint`() =
        runTest {
            val f = HomeFixture(sampleApps)
            f.screenActions.enabled.value = true
            f.viewModel.events.test {
                f.viewModel.onLockScreenRequested()
                expectNoEvents()
            }
            assertThat(f.screenActions.lockCount).isEqualTo(1)
        }

    @Test
    fun `notifications use the hidden-api fallback only when the flag allows it`() =
        runTest {
            val f = HomeFixture(sampleApps)
            f.screenActions.hiddenApiWorks = true
            f.viewModel.events.test {
                f.viewModel.onNotificationsRequested()
                assertThat(awaitItem()).isEqualTo(HomeEvent.ScreenActionUnavailable(ScreenAction.NOTIFICATIONS))
                assertThat(f.screenActions.lastAllowHiddenApi).isFalse()

                f.settings.setHiddenApiShadeFallback(true)
                f.viewModel.onNotificationsRequested()
                expectNoEvents()
            }
            assertThat(f.screenActions.lastAllowHiddenApi).isTrue()
            assertThat(f.screenActions.shadeCount).isEqualTo(1)
        }

    @Test
    fun `gesture state follows the setting and the service`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            assertThat(f.system.gestures).isEqualTo(HomeGesturesUi())

            f.settings.setDoubleTapToLock(true)
            assertThat(f.system.gestures).isEqualTo(HomeGesturesUi(doubleTapToLock = true))

            f.screenActions.enabled.value = true
            assertThat(
                f.system.gestures,
            ).isEqualTo(HomeGesturesUi(doubleTapToLock = true, canLock = true, canOpenShade = true, twoFingerSwipe = true))

            f.screenActions.enabled.value = false
            f.settings.setHiddenApiShadeFallback(true)
            assertThat(f.system.gestures).isEqualTo(HomeGesturesUi(doubleTapToLock = true, canOpenShade = false, twoFingerSwipe = true))
        }
}
