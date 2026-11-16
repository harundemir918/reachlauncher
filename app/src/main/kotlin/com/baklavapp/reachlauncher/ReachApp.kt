package com.baklavapp.reachlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.theme.resolveDark
import com.baklavapp.reachlauncher.feature.home.HomeRoute
import com.baklavapp.reachlauncher.feature.home.HomeTheme
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
 * @param initialTheme the settings snapshot `MainActivity` read synchronously before the
 *   first frame; used until the ViewModel's own theme state has answered (D63).
 * @param onWindowAppearance called with the *resolved* theme (after applying a forced
 *   Dark/Light choice) and wallpaper mode, so the Activity can style the window and the
 *   status/navigation bar icons to match.
 */
@Composable
fun ReachApp(
    viewModel: HomeViewModel,
    initialTheme: HomeTheme,
    onWindowAppearance: (WindowAppearance) -> Unit,
) {
    // Only the theme choice, not the whole Home state: a catalog or prediction update must
    // not recompose the theme root (≈ `BlocSelector` instead of `BlocBuilder`).
    val loaded by viewModel.theme.collectAsStateWithLifecycle()
    val theme = loaded ?: initialTheme
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // App names come from the system in Reach's language. Appearance › Language applies without
    // restarting the Activity (and without a pause/resume), so re-read them when it changes.
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()
    var lastLocaleTags by remember { mutableStateOf(localeTags) }
    LaunchedEffect(localeTags) {
        if (localeTags != lastLocaleTags) {
            lastLocaleTags = localeTags
            viewModel.onLanguageChanged()
        }
    }
    val isDark = resolveDark(theme.themeMode)
    val appearance = WindowAppearance(isDark, theme.palette, theme.showWallpaper)
    val currentOnWindowAppearance by rememberUpdatedState(onWindowAppearance)
    LaunchedEffect(appearance) { currentOnWindowAppearance(appearance) }

    ReachTheme(
        palette = theme.palette,
        themeMode = theme.themeMode,
        wallpaperAccent = theme.wallpaperColor?.takeIf { theme.showWallpaper }?.let { Color(it) },
        wallpaperMode = theme.showWallpaper,
    ) {
        HomeRoute(viewModel)
    }
}
