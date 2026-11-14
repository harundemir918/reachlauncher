package com.baklavapp.reachlauncher

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.feature.home.HomeRoute
import com.baklavapp.reachlauncher.feature.home.HomeViewModel

/**
 * The app's root composable: applies the user's theme and hosts the Home surface.
 *
 * Flutter analogy: the `MaterialApp(theme: …, home: …)` you'd pass to `runApp`, wrapped in a
 * `BlocBuilder` on the settings so palette and theme changes re-theme everything live.
 *
 * Android lifecycle: [LifecycleResumeEffect] runs on every `ON_RESUME` — first start, and
 * every time the user comes back to Home from an app or from system settings — which is
 * when the catalog and the default-home role must be re-read (`HomeViewModel.onResume`).
 * Compose has no `didChangeAppLifecycleState`; this effect is its lifecycle-aware equivalent.
 *
 * @param onDarkSystemBars called with the *resolved* dark/light theme (after applying a
 *   forced Dark/Light choice), so the Activity can pick status/navigation bar icon colours
 *   that stay visible.
 */
@Composable
fun ReachApp(
    viewModel: HomeViewModel,
    onDarkSystemBars: (isDark: Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val isDark =
        when (state.themeMode) {
            ThemeMode.AUTO -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val currentOnDarkSystemBars by rememberUpdatedState(onDarkSystemBars)
    LaunchedEffect(isDark) { currentOnDarkSystemBars(isDark) }

    // Wallpaper accent (spec §6.9) is passed here from M4 on.
    ReachTheme(palette = state.palette, themeMode = state.themeMode) {
        HomeRoute(viewModel)
    }
}
