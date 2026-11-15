package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThemeMode
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
 * M4 personalisation on [HomeViewModel]: intent detail's app menu (Move to… / Hide / App
 * info / Uninstall) and the wallpaper-mode theme state.
 *
 * Flutter analogy: `bloc_test` with fakes — act through the public methods, then read the
 * state the UI would see.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPersonalisationTest {
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
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.theme.collect {} }
    }

    private fun HomeFixture.detailLabels(intent: ReachIntent) = state.detailFor(intent).apps.map { it.app.label }

    @Test
    fun `move from intent detail moves the app to its new intent`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.viewModel.onIntentSelected(ReachIntent.TALK)
            val messages =
                f.state
                    .detailFor(ReachIntent.TALK)
                    .apps
                    .first { it.app.label == "Messages" }
                    .app

            f.viewModel.moveToIntent(messages, ReachIntent.TOOLS)

            assertThat(f.detailLabels(ReachIntent.TALK)).doesNotContain("Messages")
            assertThat(f.detailLabels(ReachIntent.TOOLS)).contains("Messages")
            assertThat(
                f.catalog.state.value
                    .first { it.label == "Messages" }
                    .isUserAssigned,
            ).isTrue()
            // The user stays on the detail they were looking at.
            assertThat(f.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))
        }

    @Test
    fun `moving to the current intent is a no-op`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            val maps =
                f.state
                    .detailFor(ReachIntent.GO)
                    .apps
                    .single()
                    .app
            f.viewModel.moveToIntent(maps, ReachIntent.GO)
            assertThat(
                f.catalog.state.value
                    .first { it.label == "Maps" }
                    .isUserAssigned,
            ).isFalse()
        }

    @Test
    fun `hide from intent detail removes the app from intents and counts`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            val whatsapp =
                f.state
                    .detailFor(ReachIntent.TALK)
                    .apps
                    .first { it.app.label == "WhatsApp" }
                    .app

            f.viewModel.hideApp(whatsapp)

            assertThat(f.detailLabels(ReachIntent.TALK)).containsExactly("Messages", "Phone").inOrder()
            assertThat(
                f.state.intents
                    .first { it.intent == ReachIntent.TALK }
                    .appCount,
            ).isEqualTo(2)
            assertThat(
                f.catalog.state.value
                    .first { it.label == "WhatsApp" }
                    .isHidden,
            ).isTrue()
        }

    @Test
    fun `app info and uninstall go to the system`() {
        val f = HomeFixture(sampleApps)
        val notes = sampleApps.first { it.label == "Notes" }
        f.viewModel.openAppInfo(notes)
        f.viewModel.uninstall(notes)
        assertThat(f.launcher.appInfoOpened).containsExactly(notes.key)
        assertThat(f.launcher.uninstallRequested).containsExactly(notes.key)
    }

    @Test
    fun `theme is null until settings are read, then follows them`() =
        runTest {
            val f = HomeFixture()
            assertThat(f.viewModel.theme.value).isNull()
            subscribe(f)
            f.settings.setThemeMode(ThemeMode.DARK)
            assertThat(f.viewModel.theme.value).isEqualTo(HomeTheme(ThemeMode.DARK, PaletteId.DEFAULT))
        }

    @Test
    fun `wallpaper colour is only passed on in wallpaper mode and follows wallpaper changes`() =
        runTest {
            val f = HomeFixture()
            f.wallpaper.state.value = 0xFF336699.toInt()
            subscribe(f)
            assertThat(
                f.viewModel.theme.value
                    ?.wallpaperColor,
            ).isNull()

            f.settings.setShowWallpaper(true)
            assertThat(f.viewModel.theme.value).isEqualTo(HomeTheme(showWallpaper = true, wallpaperColor = 0xFF336699.toInt()))

            f.wallpaper.state.value = 0xFFFFFF00.toInt()
            assertThat(
                f.viewModel.theme.value
                    ?.wallpaperColor,
            ).isEqualTo(0xFFFFFF00.toInt())

            f.settings.setShowWallpaper(false)
            assertThat(f.viewModel.theme.value).isEqualTo(HomeTheme())
        }
}
