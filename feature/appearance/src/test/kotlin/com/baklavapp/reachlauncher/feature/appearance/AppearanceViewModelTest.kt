package com.baklavapp.reachlauncher.feature.appearance

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLanguageSource
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeScreenActionsSource
import com.baklavapp.reachlauncher.core.data.fake.FakeSettingsRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.SystemScreen
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [AppearanceViewModel] with the in-memory settings fake: every control writes straight to
 * the repository and the state follows the repository (no draft, no Save).
 *
 * Flutter analogy: `bloc_test` — build with fakes, act, expect states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state mirrors the persisted settings`() =
        runTest {
            val repo =
                FakeSettingsRepository(
                    AppearanceSettings(ThemeMode.DARK, PaletteId.DUSK, showWallpaper = true, thumbSide = ThumbSide.RIGHT),
                )
            AppearanceViewModel(repo, FakeScreenActionsSource(), FakeAppLauncher(), FakeAppLanguageSource()).uiState.test {
                assertThat(expectMostRecentItem())
                    .isEqualTo(AppearanceUiState(true, ThemeMode.DARK, PaletteId.DUSK, showWallpaper = true, thumbSide = ThumbSide.RIGHT))
            }
        }

    @Test
    fun `every control writes through and the state follows live`() =
        runTest {
            val repo = FakeSettingsRepository()
            val vm = AppearanceViewModel(repo, FakeScreenActionsSource(), FakeAppLauncher(), FakeAppLanguageSource())
            vm.uiState.test {
                assertThat(expectMostRecentItem().themeMode).isEqualTo(ThemeMode.AUTO)

                vm.onThemeModeSelected(ThemeMode.LIGHT)
                assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.LIGHT)

                vm.onPaletteSelected(PaletteId.HIGH_CONTRAST)
                assertThat(awaitItem().palette).isEqualTo(PaletteId.HIGH_CONTRAST)

                vm.onShowWallpaperChanged(true)
                assertThat(awaitItem().showWallpaper).isTrue()

                vm.onThumbSideSelected(ThumbSide.RIGHT)
                assertThat(awaitItem().thumbSide).isEqualTo(ThumbSide.RIGHT)
            }
            assertThat(repo.state.value)
                .isEqualTo(AppearanceSettings(ThemeMode.LIGHT, PaletteId.HIGH_CONTRAST, showWallpaper = true, thumbSide = ThumbSide.RIGHT))
        }

    @Test
    fun `a failing write does not crash and leaves the state unchanged`() =
        runTest {
            val failing =
                object : SettingsRepository by FakeSettingsRepository() {
                    override suspend fun setPalette(palette: PaletteId): Unit = throw IllegalStateException("disk full")
                }
            val vm = AppearanceViewModel(failing, FakeScreenActionsSource(), FakeAppLauncher(), FakeAppLanguageSource())
            vm.uiState.test {
                assertThat(expectMostRecentItem().palette).isEqualTo(PaletteId.DEFAULT)
                vm.onPaletteSelected(PaletteId.FOREST)
                expectNoEvents()
            }
        }

    @Test
    fun `double-tap to lock is stored and the service button opens accessibility settings`() =
        runTest {
            val repo = FakeSettingsRepository()
            val service = FakeScreenActionsSource()
            val launcher = FakeAppLauncher()
            val vm = AppearanceViewModel(repo, service, launcher, FakeAppLanguageSource())
            vm.uiState.test {
                assertThat(expectMostRecentItem().accessibilityServiceOn).isFalse()

                vm.onDoubleTapToLockChanged(true)
                assertThat(awaitItem().doubleTapToLock).isTrue()
                // Storing the choice never jumps to Settings by itself.
                assertThat(launcher.openedScreens).isEmpty()

                vm.onOpenAccessibilitySettings()
                assertThat(launcher.openedScreens).containsExactly(SystemScreen.ACCESSIBILITY_SERVICE)

                service.enabled.value = true
                assertThat(awaitItem().accessibilityServiceOn).isTrue()
            }
            assertThat(repo.state.value.doubleTapToLock).isTrue()
        }

    @Test
    fun `choosing a language goes to the language source and back into the state`() =
        runTest {
            val language = FakeAppLanguageSource()
            val vm = AppearanceViewModel(FakeSettingsRepository(), FakeScreenActionsSource(), FakeAppLauncher(), language)
            vm.uiState.test {
                assertThat(expectMostRecentItem().language).isEqualTo(AppLanguage.SYSTEM)
                vm.onLanguageSelected(AppLanguage.SPANISH)
                assertThat(awaitItem().language).isEqualTo(AppLanguage.SPANISH)
                assertThat(language.current.value).isEqualTo(AppLanguage.SPANISH)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
