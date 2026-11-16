package com.baklavapp.reachlauncher

import android.app.Activity
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.baklavapp.reachlauncher.core.designsystem.theme.Palettes
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * What the *window* (not Compose) must look like: its background colour, and whether it
 * shows the wallpaper behind it.
 *
 * Flutter analogy: the native `LaunchTheme`/`NormalTheme` + `FlutterActivity`'s
 * `BackgroundMode.transparent` that you'd normally configure in `styles.xml` and the
 * Android embedding — here we switch them at run time.
 */
data class WindowAppearance(
    val isDark: Boolean,
    val palette: PaletteId,
    val showWallpaper: Boolean,
) {
    companion object {
        /** Resolves Auto against the Activity's current `uiMode`, before Compose exists. */
        fun resolve(
            themeMode: ThemeMode,
            palette: PaletteId,
            showWallpaper: Boolean,
            configuration: Configuration,
        ): WindowAppearance {
            val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark =
                when (themeMode) {
                    ThemeMode.AUTO -> systemDark
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                }
            return WindowAppearance(isDark, palette, showWallpaper)
        }
    }
}

/**
 * Applies a [WindowAppearance] to an Activity's window, live — wallpaper mode is switched
 * on and off without recreating the Activity (docs/DECISIONS.md D62).
 *
 * **Why a runtime flag instead of a second theme.** `windowShowWallpaper` in a theme can
 * only be applied by recreating the Activity, and Reach never recreates Home (that flashes
 * an empty frame and drops the surface state; see `configChanges` in the manifest). The
 * theme attribute is just a shortcut for `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER`,
 * which a window may add or clear at any time. Together with a transparent window
 * background, the system wallpaper shows through; Compose then paints the 82/86 % scrim.
 *
 * **Blur (Android 12+).** `Window.setBackgroundBlurRadius` blurs whatever is behind the
 * window — here, the wallpaper. Cross-window blur can be off (battery saver, the developer
 * option, some GPUs, tunnelled video), so we listen with
 * `WindowManager.addCrossWindowBlurEnabledListener` and set the radius only while it's
 * enabled; otherwise the scrim alone keeps text readable (spec §6.9).
 *
 * **Opaque mode.** The window background is the palette's bg (not the static Lime colour
 * from `themes.xml`), so the window never shows a different colour than the first Compose
 * frame — for example after a forced Light theme on a dark system.
 */
class ReachWindow(
    private val activity: Activity,
) {
    private var current: WindowAppearance? = null
    private var blurEnabled = false
    private val blurListener = Consumer<Boolean> { enabled -> onBlurEnabledChanged(enabled) }

    /** Call from `onCreate`, after `super.onCreate` and before `setContent`. */
    fun start() {
        // The window's surface is created opaque or translucent once, from the window format
        // at creation. An opaque start (wallpaper mode off) made a later live switch to
        // wallpaper mode show nothing on a real device (Android 13): the flag
        // was set and Reach was the wallpaper target, but the surface stayed opaque. So the
        // surface is always translucent; the opaque palette background makes it look the
        // same, at the cost of the compositor blending one full-screen layer (D72).
        activity.window.setFormat(PixelFormat.TRANSLUCENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerBlurListener()
    }

    /** Call from `onDestroy`. */
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) unregisterBlurListener()
    }

    /** Applies [appearance]; cheap and idempotent, so call it whenever it may have changed. */
    fun apply(appearance: WindowAppearance) {
        if (appearance == current) return
        current = appearance
        val window = activity.window
        if (appearance.showWallpaper) {
            window.setBackgroundDrawable(
                android.graphics.Color.TRANSPARENT
                    .toDrawable(),
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            val bg = Palettes.colors(appearance.palette, appearance.isDark).bg
            window.setBackgroundDrawable(bg.toArgb().toDrawable())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyBlur()
    }

    private fun onBlurEnabledChanged(enabled: Boolean) {
        blurEnabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyBlur()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlur() {
        val wanted = current?.showWallpaper == true && blurEnabled
        val radius = if (wanted) (BLUR_RADIUS_DP * activity.resources.displayMetrics.density).roundToInt() else 0
        activity.window.setBackgroundBlurRadius(radius)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerBlurListener() {
        // Called immediately with the current state, then on every change.
        activity.windowManager.addCrossWindowBlurEnabledListener(activity.mainExecutor, blurListener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterBlurListener() {
        activity.windowManager.removeCrossWindowBlurEnabledListener(blurListener)
    }

    private companion object {
        /** Strong enough to turn a photo into colour fields behind the 82–86 % scrim. */
        const val BLUR_RADIUS_DP = 40
    }
}
