package com.baklavapp.reachlauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.feature.home.HomeTheme
import com.baklavapp.reachlauncher.feature.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The single Activity, registered as the HOME screen in the manifest.
 *
 * Every screen (Home, intent detail, search, All Apps, Appearance) is a *state* of one
 * Compose tree driven by [HomeViewModel], not a separate Activity or route (spec §3).
 *
 * Flutter analogy: `MainActivity` is the Android host that `FlutterActivity` normally
 * hides from you; `setContent { … }` is our `runApp(…)`. `by viewModels()` ≈ creating the
 * BLoC in a `BlocProvider` at the root: the same instance survives configuration changes
 * and is shared with every composable that receives it. `@Inject lateinit var` is Hilt's
 * field injection — the Activity is created by Android, not by us, so it can't take
 * constructor parameters (≈ `getIt<SettingsRepository>()` in `initState`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val reachWindow = ReachWindow(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; composables apply insets themselves.
        enableEdgeToEdge()
        reachWindow.start()

        // First-frame theme (docs/DECISIONS.md D63): settings live in DataStore, which only
        // answers asynchronously, so the very first Compose frame would otherwise use the
        // defaults (Auto, Lime, opaque) and flash for one frame for anyone who chose
        // otherwise. Reading the snapshot synchronously here is a single small file read
        // that Home needs immediately anyway (it warms the same DataStore instance), and
        // onCreate runs only when the process starts — the Activity is never recreated.
        val initial = readSettingsSnapshot()
        val initialTheme = HomeTheme(initial.themeMode, initial.palette, initial.showWallpaper)
        val initialWindow = WindowAppearance.resolve(initial.themeMode, initial.palette, initial.showWallpaper, resources.configuration)
        reachWindow.apply(initialWindow)
        applySystemBarStyle(initialWindow.isDark)

        setContent {
            ReachApp(
                viewModel = viewModel,
                initialTheme = initialTheme,
                onWindowAppearance = ::applyWindowAppearance,
            )
        }
    }

    override fun onDestroy() {
        reachWindow.stop()
        super.onDestroy()
    }

    /**
     * Android lifecycle quirk: the manifest declares `launchMode="singleTask"`, so pressing
     * the Home button while Reach exists does *not* create a new Activity — Android delivers
     * the HOME intent here instead (then calls onResume). That is our "Home pressed" signal:
     * return to the Home surface from wherever the user was and close the keyboard (spec §4).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onHomePressed()
    }

    /**
     * Blocks for the persisted settings, at most [SNAPSHOT_TIMEOUT_MILLIS]. A slow or broken
     * disk must never keep Home from appearing: on timeout we fall back to the defaults and
     * accept the one-frame flash. (`runBlocking` on the main thread is normally a smell; here
     * it is bounded, happens once per process, and replaces a visible glitch.)
     */
    private fun readSettingsSnapshot(): AppearanceSettings =
        try {
            runBlocking {
                withTimeoutOrNull(SNAPSHOT_TIMEOUT_MILLIS) { settingsRepository.settings.first() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A launcher that crashes in onCreate crash-loops the Home screen. Any unexpected
            // failure here just means "defaults for the first frame"; the live flow follows.
            null
        } ?: AppearanceSettings()

    private fun applyWindowAppearance(appearance: WindowAppearance) {
        reachWindow.apply(appearance)
        applySystemBarStyle(appearance.isDark)
    }

    /**
     * Re-styles the system bars for the resolved Reach theme. `enableEdgeToEdge()` alone
     * follows the *system* dark mode, which leaves dark icons on a dark background when the
     * user forces Dark (or light-on-light when forcing Light). Calling it again is supported.
     */
    private fun applySystemBarStyle(isDark: Boolean) {
        val style =
            if (isDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private companion object {
        const val SNAPSHOT_TIMEOUT_MILLIS = 250L
    }
}
