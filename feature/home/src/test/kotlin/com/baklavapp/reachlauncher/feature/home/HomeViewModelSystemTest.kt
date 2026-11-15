package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.SystemScreen
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
 * M3 behaviour of [HomeViewModel]: media, the calendar context line and the onboarding cards,
 * driven through the fakes (plain JVM, like [HomeViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSystemTest {
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
    fun `media state flows through and commands reach the session`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            assertThat(f.system.media).isNull()

            f.media.state.value = dailyMix
            assertThat(f.system.media?.title).isEqualTo("Daily Mix 1")

            f.viewModel.onPlayPause()
            f.viewModel.onSkipNext()
            f.viewModel.onSkipPrevious()
            assertThat(f.media.commands).containsExactly("playPause", "next", "previous").inOrder()
            assertThat(f.system.media?.isPlaying).isFalse() // The fake toggles on playPause.

            f.media.state.value = null
            assertThat(f.system.media).isNull()
        }

    @Test
    fun `calendar line is hidden without permission and shown with it`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.calendar.state.value = CalendarEvent("Standup", beginEpochMillis = 1_000, isAllDay = false)
            assertThat(f.system.nextEvent).isNull()
            f.calendar.granted.value = true
            assertThat(f.system.nextEvent?.title).isEqualTo("Standup")
        }

    @Test
    fun `onboarding offers one card at a time in priority order`() =
        runTest {
            val f = HomeFixture(sampleApps, onboarding = true)
            subscribe(f)
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.DEFAULT_HOME)
            f.homeRole.state.value = true
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.NOTIFICATION_ACCESS)
            f.notifications.granted.value = true
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.CALENDAR)
            f.calendar.granted.value = true
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.USAGE_ACCESS)
            f.usage.granted.value = true
            assertThat(f.system.onboarding).isNull()
        }

    @Test
    fun `not now persists and the next card follows`() =
        runTest {
            val f = HomeFixture(sampleApps, onboarding = true)
            subscribe(f)
            f.viewModel.onOnboardingDismissed(OnboardingCard.DEFAULT_HOME)
            assertThat(f.settings.state.value.dismissedTips).containsExactly("default_home")
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.NOTIFICATION_ACCESS)

            // Dismissed cards stay away even when their condition still holds; a granted
            // access hides its card without any dismissal.
            f.notifications.granted.value = true
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.CALENDAR)
        }

    @Test
    fun `primary actions open the matching system screens`() =
        runTest {
            val f = HomeFixture(sampleApps, onboarding = true)
            subscribe(f)
            f.viewModel.onOnboardingAction(OnboardingCard.DEFAULT_HOME)
            f.viewModel.onOnboardingAction(OnboardingCard.NOTIFICATION_ACCESS)
            f.viewModel.onOnboardingAction(OnboardingCard.USAGE_ACCESS)
            f.viewModel.onOnboardingAction(OnboardingCard.CALENDAR) // Runtime permission: the UI asks.
            assertThat(f.launcher.openedScreens)
                .containsExactly(SystemScreen.HOME_ROLE, SystemScreen.NOTIFICATION_ACCESS, SystemScreen.USAGE_ACCESS)
                .inOrder()
            assertThat(f.settings.state.value.dismissedTips).isEmpty()
        }

    @Test
    fun `a denied calendar permission dismisses its card for good`() =
        runTest {
            val f = HomeFixture(sampleApps, onboarding = true)
            subscribe(f)
            f.homeRole.state.value = true
            f.notifications.granted.value = true
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.CALENDAR)
            f.viewModel.onCalendarPermissionResult(granted = true)
            assertThat(f.settings.state.value.dismissedTips).isEmpty()
            f.viewModel.onCalendarPermissionResult(granted = false)
            assertThat(f.system.onboarding).isEqualTo(OnboardingCard.USAGE_ACCESS)
        }

    @Test
    fun `progress is extrapolated while playing and clamped`() {
        assertThat(dailyMix.positionAt(nowElapsedMillis = 11_000)).isEqualTo(70_000)
        assertThat(dailyMix.positionAt(nowElapsedMillis = 1_000_000)).isEqualTo(180_000)
        assertThat(dailyMix.copy(isPlaying = false).positionAt(nowElapsedMillis = 11_000)).isEqualTo(60_000)
        assertThat(dailyMix.copy(playbackSpeed = 2f).positionAt(nowElapsedMillis = 11_000)).isEqualTo(80_000)
        assertThat(dailyMix.copy(durationMillis = 0).progressAt(5_000)).isNull()
        assertThat(formatMediaTime(187_000)).isEqualTo("3:07")
        assertThat(formatMediaTime(3_723_000)).isEqualTo("1:02:03")
    }
}
