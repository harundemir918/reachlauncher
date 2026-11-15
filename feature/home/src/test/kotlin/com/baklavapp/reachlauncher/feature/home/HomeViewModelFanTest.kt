package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.ReachIntent
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

/** The ViewModel half of press-slide-release: what the fan shows and what releasing does. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFanTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe(fixture: HomeFixture) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.uiState.collect {} }
    }

    private fun HomeFixture.fanLabels() =
        viewModel.fan.value
            ?.apps
            ?.map { it.label }

    @Test
    fun `fan shows the intent's apps, most launched nearest the finger`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            assertThat(f.viewModel.onFanOpen(ReachIntent.TALK)).isTrue()
            assertThat(f.fanLabels()).containsExactly("WhatsApp", "Messages", "Phone").inOrder()
            assertThat(
                f.viewModel.fan.value
                    ?.highlighted,
            ).isNull()
        }

    @Test
    fun `fan is capped and skips hidden apps`() =
        runTest {
            val many =
                (1..9).map { app("Tool $it", ReachIntent.TOOLS, launchCount = it) } + app("Secret", ReachIntent.TOOLS, 99, hidden = true)
            val f = HomeFixture(many)
            subscribe(f)
            f.viewModel.onFanOpen(ReachIntent.TOOLS)
            assertThat(f.fanLabels()).hasSize(FanState.MAX_APPS)
            assertThat(f.fanLabels()?.first()).isEqualTo("Tool 9")
            assertThat(f.fanLabels()).doesNotContain("Secret")
        }

    @Test
    fun `an intent without apps opens no fan`() =
        runTest {
            val f = HomeFixture(sampleApps.filter { it.intent != ReachIntent.PLAY })
            subscribe(f)
            assertThat(f.viewModel.onFanOpen(ReachIntent.PLAY)).isFalse()
            assertThat(f.viewModel.fan.value).isNull()
        }

    @Test
    fun `releasing on a highlighted pill launches it, records it and returns home`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(1)
            assertThat(
                f.viewModel.fan.value
                    ?.highlightedApp
                    ?.label,
            ).isEqualTo("Messages")

            f.viewModel.onFanRelease(LaunchOrigin(1, 2, 3, 4))

            val messages = sampleApps.first { it.label == "Messages" }
            assertThat(f.launcher.launched).containsExactly(messages.key)
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(messages.key)
            assertThat(f.viewModel.fan.value).isNull()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `a paused profile resumes without history and the fan closes`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.launcher.result = LaunchResult.RESUMING_PROFILE
            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(0)
            f.viewModel.onFanRelease(null)
            assertThat(f.launcher.launched).hasSize(1)
            assertThat(f.history.events.value).isEmpty()
            assertThat(f.viewModel.fan.value).isNull()
        }

    @Test
    fun `release without a highlight, cancel and back launch nothing`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)

            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(0)
            f.viewModel.onFanHighlight(null) // slid back onto the word
            f.viewModel.onFanRelease(null)

            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(2)
            f.viewModel.onFanCancel()

            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(1)
            f.viewModel.onBack()

            assertThat(f.launcher.launched).isEmpty()
            assertThat(f.history.events.value).isEmpty()
            assertThat(f.viewModel.fan.value).isNull()
        }

    @Test
    fun `an out-of-range highlight counts as no pill`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.viewModel.onFanOpen(ReachIntent.TALK)
            f.viewModel.onFanHighlight(7)
            assertThat(
                f.viewModel.fan.value
                    ?.highlighted,
            ).isNull()
        }
}
