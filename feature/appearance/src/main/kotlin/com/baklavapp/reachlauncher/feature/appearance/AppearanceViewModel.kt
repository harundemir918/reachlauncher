package com.baklavapp.reachlauncher.feature.appearance

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Appearance sheet shows: the four choices it controls (spec §6.8).
 *
 * @property loaded false until DataStore answered once; the sheet still renders the
 *   defaults meanwhile (DataStore answers within a frame or two).
 */
@Immutable
data class AppearanceUiState(
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val palette: PaletteId = PaletteId.DEFAULT,
    val showWallpaper: Boolean = false,
    val thumbSide: ThumbSide = ThumbSide.LEFT,
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
    ) : ViewModel() {
        /** The sheet's state, straight from the persisted settings. */
        val uiState: StateFlow<AppearanceUiState> =
            settingsRepository.settings
                .map { AppearanceUiState(true, it.themeMode, it.palette, it.showWallpaper, it.thumbSide) }
                .distinctUntilChanged()
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
