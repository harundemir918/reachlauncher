package com.baklavapp.reachlauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.baklavapp.reachlauncher.feature.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity, registered as the HOME screen in the manifest.
 *
 * Every screen (Home, intent detail, search, All Apps, Appearance) is a *state* of one
 * Compose tree driven by [HomeViewModel], not a separate Activity or route (spec §3).
 *
 * Flutter analogy: `MainActivity` is the Android host that `FlutterActivity` normally
 * hides from you; `setContent { … }` is our `runApp(…)`. `by viewModels()` ≈ creating the
 * BLoC in a `BlocProvider` at the root: the same instance survives configuration changes
 * and is shared with every composable that receives it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; composables apply insets themselves.
        enableEdgeToEdge()
        setContent {
            ReachApp(viewModel = viewModel, onDarkSystemBars = ::applySystemBarStyle)
        }
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
}
