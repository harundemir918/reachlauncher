package com.baklavapp.reachlauncher.feature.appearance

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.source.AppLanguageSource
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.SystemScreen
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Appearance sheet shows: the four choices of spec §6.8 plus the opt-in for Home's
 * screen-action gestures (§7.4, D66).
 *
 * @property loaded false until DataStore answered once; the sheet still renders the
 *   defaults meanwhile (DataStore answers within a frame or two).
 * @property doubleTapToLock the "Double-tap to lock" switch.
 * @property accessibilityServiceOn Reach's accessibility service is on; while it is off the
 *   sheet offers a button to the system's Accessibility settings.
 * @property language Reach's display language ([AppLanguage.SYSTEM] = follow the phone).
 */
@Immutable
data class AppearanceUiState(
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val palette: PaletteId = PaletteId.DEFAULT,
    val showWallpaper: Boolean = false,
    val thumbSide: ThumbSide = ThumbSide.LEFT,
    val doubleTapToLock: Boolean = false,
    val accessibilityServiceOn: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

/**
 * AppearanceViewModel — reads and writes the appearance settings for the sheet.
 *
 * There is no Save button and no local draft: every `on…` call writes straight to
 * [SettingsRepository] (DataStore), and DataStore re-emits, which re-themes *everything*
 * live — the sheet itself, Home behind it, All Apps' rail side — because they all observe
 * the same settings flow. The sheet's own [uiState] comes from that flow too, so what it
 * shows is always what's persisted (one source of truth).
 *
 * Flutter analogy: a Cubit over a `shared_preferences` repository whose `Stream` drives
 * the whole app's `ThemeData` — `uiState` ≈ the Cubit's state stream, the `on…` methods ≈
 * Cubit methods that only call the repository and let the stream do the rest.
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        screenActionsSource: ScreenActionsSource,
        private val appLauncher: AppLauncher,
        private val appLanguageSource: AppLanguageSource,
    ) : ViewModel() {
        /**
         * The sheet's state, straight from the persisted settings and the service state (the
         * latter is re-read whenever Reach resumes, e.g. back from Accessibility settings).
         */
        val uiState: StateFlow<AppearanceUiState> =
            combine(
                settingsRepository.settings,
                screenActionsSource.isServiceEnabled,
                appLanguageSource.language,
            ) { settings, serviceOn, language ->
                AppearanceUiState(
                    loaded = true,
                    themeMode = settings.themeMode,
                    palette = settings.palette,
                    showWallpaper = settings.showWallpaper,
                    thumbSide = settings.thumbSide,
                    doubleTapToLock = settings.doubleTapToLock,
                    accessibilityServiceOn = serviceOn,
                    language = language,
                )
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppearanceUiState())

        /** THEME segmented control. */
        fun onThemeModeSelected(mode: ThemeMode) = write { setThemeMode(mode) }

        /** PALETTE swatch. */
        fun onPaletteSelected(palette: PaletteId) = write { setPalette(palette) }

        /** "Show wallpaper" switch. */
        fun onShowWallpaperChanged(show: Boolean) = write { setShowWallpaper(show) }

        /** Thumb side segmented control. */
        fun onThumbSideSelected(side: ThumbSide) = write { setThumbSide(side) }

        /**
         * "Double-tap to lock" switch. Only stores the choice: if the service is off, the sheet
         * shows the "Turn on in Accessibility settings" button next to it — Reach never jumps
         * to a system screen on its own (spec §7: never nag).
         */
        fun onDoubleTapToLockChanged(enabled: Boolean) = write { setDoubleTapToLock(enabled) }

        /**
         * LANGUAGE segmented control. Not a DataStore setting: on Android 13+ the system keeps
         * per-app languages itself (see `AppLanguageSource`), and the sheet re-renders in the
         * new language as soon as it is applied.
         */
        fun onLanguageSelected(language: AppLanguage) {
            appLanguageSource.setLanguage(language)
        }

        /** "Turn on" button: the system's Accessibility settings, where the user enables Reach's service. */
        fun onOpenAccessibilitySettings() {
            appLauncher.open(SystemScreen.ACCESSIBILITY_SERVICE)
        }

        /**
         * Writes in `viewModelScope` and drops failures (except cancellation): an exception
         * escaping a `viewModelScope` coroutine would crash the launcher (D36). A failed
         * write simply leaves the control where it was, because the UI mirrors the flow.
         */
        private fun write(block: suspend SettingsRepository.() -> Unit) {
            viewModelScope.launch {
                try {
                    settingsRepository.block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Nothing to roll back: the persisted value (and so the UI) didn't change.
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
