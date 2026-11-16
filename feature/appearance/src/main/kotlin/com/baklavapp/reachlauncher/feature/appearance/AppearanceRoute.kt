package com.baklavapp.reachlauncher.feature.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baklavapp.reachlauncher.feature.appearance.ui.AppearanceCallbacks
import com.baklavapp.reachlauncher.feature.appearance.ui.AppearanceSheet

/*
 * AppearanceRoute — the public entry point of `:feature:appearance`. Home shows it over its
 * Home surface (long-press on the clock, or Search's "Appearance settings" command) and
 * passes one callback; everything else is internal to this module.
 *
 * Flutter analogy: `hiltViewModel()` ≈ `BlocProvider(create: …)` + `context.read<…>()`, and
 * `collectAsStateWithLifecycle()` ≈ `BlocBuilder`.
 *
 * System Back is *not* handled here: Home's `BackHandler` already closes the sheet.
 */

/**
 * The Appearance sheet (spec §6.8): theme, palette, wallpaper mode, thumb side and the
 * screen-action gestures, applied live with no Save button.
 *
 * @param onClose the "Done" pill or a tap on the dimmed area: Home should go back to Home.
 */
@Composable
fun AppearanceRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AppearanceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks =
        remember(viewModel) {
            AppearanceCallbacks(
                onThemeMode = viewModel::onThemeModeSelected,
                onPalette = viewModel::onPaletteSelected,
                onShowWallpaper = viewModel::onShowWallpaperChanged,
                onThumbSide = viewModel::onThumbSideSelected,
                onDoubleTapToLock = viewModel::onDoubleTapToLockChanged,
                onOpenAccessibilitySettings = viewModel::onOpenAccessibilitySettings,
                onLanguage = viewModel::onLanguageSelected,
            )
        }
    AppearanceSheet(state = state, callbacks = callbacks, onClose = onClose, modifier = modifier)
}
