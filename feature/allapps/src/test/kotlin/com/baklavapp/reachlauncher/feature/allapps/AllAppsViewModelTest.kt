package com.baklavapp.reachlauncher.feature.allapps

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * [AllAppsViewModel] with fakes.
 *
 * Flutter analogy: `bloc_test` — build with fakes, act, expect states. Robolectric only
 * because the grouping uses `android.icu` (framework code, not on the plain JVM).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AllAppsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.fixture(apps: List<CatalogApp> = sampleApps) =
        AllAppsFixture(apps, appScope = backgroundScope).also { it.viewModel.onLocaleChanged(Locale.ENGLISH) }

    @Test
    fun groupsAndCountLeaveHiddenAppsOut() =
        runTest {
            val f = fixture()
            f.viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertThat(state.loaded).isTrue()
                assertThat(state.totalCount).isEqualTo(8)
                assertThat(state.groups.map { it.letter }).containsExactly("M", "S", "W", "Y").inOrder()
                assertThat(state.groups[1].apps.map { it.label }).containsExactly("Settings", "Signal", "Slack", "Spotify").inOrder()
                assertThat(state.groups.flatMap { it.apps }.map { it.label }).doesNotContain("Chess")
                assertThat(state.hidden.map { it.label }).containsExactly("Chess")
            }
        }

    @Test
    fun railHasEveryLetterAndPointsAtGroups() =
        runTest {
            fixture().viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertThat(state.rail.map { it.letter }).isEqualTo(('A'..'Z').map { it.toString() } + "#")
                assertThat(state.rail.first { it.letter == "S" }.groupIndex).isEqualTo(1)
                assertThat(state.rail.first { it.letter == "A" }.hasApps).isFalse()
            }
        }

    @Test
    fun hiddenGroupOnlyWhenNeeded() =
        runTest {
            val f = fixture(sampleApps.filterNot { it.isHidden })
            f.viewModel.uiState.test {
                assertThat(expectMostRecentItem().hidden).isEmpty()
                val maps = sampleApps.first { it.label == "Maps" }
                f.viewModel.setHidden(maps, true)
                val state = awaitItem()
                assertThat(state.hidden.map { it.label }).containsExactly("Maps")
                assertThat(state.totalCount).isEqualTo(7)
                f.viewModel.setHidden(maps, false)
                assertThat(awaitItem().hidden).isEmpty()
            }
        }

    @Test
    fun moveToIntentIsTheUsersChoice() =
        runTest {
            val f = fixture()
            val maps = sampleApps.first { it.label == "Maps" }
            f.viewModel.moveToIntent(maps, ReachIntent.GO)
            val moved =
                f.catalog.state.value
                    .first { it.key == maps.key }
            assertThat(moved.intent).isEqualTo(ReachIntent.GO)
            assertThat(moved.isUserAssigned).isTrue()
        }

    @Test
    fun appInfoAndUninstallGoToTheLauncher() =
        runTest {
            val f = fixture()
            val slack = sampleApps.first { it.label == "Slack" }
            f.viewModel.openAppInfo(slack)
            f.viewModel.uninstall(slack)
            assertThat(f.launcher.appInfo).containsExactly(slack.key)
            assertThat(f.launcher.uninstalled).containsExactly(slack.key)
        }

    @Test
    fun launchedEmitsDoneAndIsRecorded() =
        runTest {
            val f = fixture()
            val maps = sampleApps.first { it.label == "Maps" }
            f.viewModel.events.test {
                f.viewModel.launch(maps, null)
                assertThat(awaitItem()).isEqualTo(AllAppsEvent.Done)
            }
            // LaunchApp records in the app scope (here backgroundScope): let it run.
            runCurrent()
            assertThat(f.launcher.fake.launched).containsExactly(maps.key)
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(maps.key)
        }

    @Test
    fun resumingProfileStaysAndFailureShowsAMessage() =
        runTest {
            val f = fixture()
            val slack = sampleApps.first { it.label == "Slack" }
            f.viewModel.events.test {
                f.launcher.fake.result = LaunchResult.RESUMING_PROFILE
                f.viewModel.launch(slack, null)
                expectNoEvents()

                f.launcher.fake.result = LaunchResult.FAILED
                f.viewModel.launch(slack, null)
                assertThat(awaitItem()).isEqualTo(AllAppsEvent.LaunchFailed("Slack"))
            }
            assertThat(f.history.events.value).isEmpty()
        }

    @Test
    fun thumbSideFlowsThrough() =
        runTest {
            val f = fixture()
            f.viewModel.uiState.test {
                assertThat(expectMostRecentItem().thumbSide).isEqualTo(ThumbSide.LEFT)
                f.settings.setThumbSide(ThumbSide.RIGHT)
                assertThat(awaitItem().thumbSide).isEqualTo(ThumbSide.RIGHT)
            }
        }

    @Test
    fun localeChangeRebuildsTheRail() =
        runTest {
            val f = fixture()
            f.viewModel.uiState.test {
                assertThat(expectMostRecentItem().rail.map { it.letter }).doesNotContain("Ş")
                f.viewModel.onLocaleChanged(Locale.forLanguageTag("tr"))
                assertThat(awaitItem().rail.map { it.letter }).contains("Ş")
            }
        }
}
