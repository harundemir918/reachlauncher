package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode

/*
 * ReachTheme — provides Reach's colours, type and dimensions to every composable below it.
 *
 * Flutter analogy: `Theme(data: …, child: …)` + `Theme.of(context)`. In Compose the
 * "inherited widget" mechanism is a `CompositionLocal`: `CompositionLocalProvider`
 * puts a value into the tree (like an InheritedWidget), and `LocalX.current` reads the
 * nearest one (like `context.dependOnInheritedWidgetOfExactType`). Our tokens are the
 * equivalent of a `ThemeExtension`, read via `ReachTheme.colors` / `ReachTheme.typography`.
 */

/**
 * Holder for the current [ReachColors]. Static: palette changes recompose the whole tree,
 * which is what we want. No default: a composable outside [ReachTheme] fails loudly
 * instead of silently rendering in Lime dark.
 */
val LocalReachColors = staticCompositionLocalOf<ReachColors> { error("No ReachTheme: wrap content in ReachTheme { … }") }

/** Holder for the current [ReachTypography]. */
val LocalReachTypography = staticCompositionLocalOf<ReachTypography> { error("No ReachTheme: wrap content in ReachTheme { … }") }

/**
 * Applies a palette in the theme chosen by [themeMode].
 *
 * @param wallpaperAccent primary wallpaper colour in wallpaper mode, or null. When set,
 *   the palette's accent trio is replaced by one derived from it ([WallpaperAccent]).
 */
@Composable
fun ReachTheme(
    palette: PaletteId = PaletteId.DEFAULT,
    themeMode: ThemeMode = ThemeMode.AUTO,
    wallpaperAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.AUTO -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val colors =
        remember(palette, isDark, wallpaperAccent) {
            val base = Palettes.colors(palette, isDark)
            val withAccent = if (wallpaperAccent != null) WallpaperAccent.apply(base, wallpaperAccent) else base
            ReachColors.from(withAccent, isDark)
        }
    ReachTheme(colors = colors, content = content)
}

/** Lower-level overload taking a ready [ReachColors]; used by previews and tests. */
@Composable
fun ReachTheme(
    colors: ReachColors,
    typography: ReachTypography = ReachTypography.Default,
    content: @Composable () -> Unit,
) {
    // Material 3 is infrastructure only (text fields, ripples, sheets, switches). Every
    // colour role and type style is mapped onto Reach tokens, so nothing M3 draws on its
    // own falls back to the stock purple/Roboto look.
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    val selection =
        remember(colors) { TextSelectionColors(handleColor = colors.accentInk, backgroundColor = colors.accent.copy(alpha = 0.4f)) }

    MaterialTheme(colorScheme = materialColors, typography = materialTypography) {
        CompositionLocalProvider(
            LocalReachColors provides colors,
            LocalReachTypography provides typography,
            LocalContentColor provides colors.fg,
            // Default for any Text without an explicit style (≈ DefaultTextStyle in Flutter).
            LocalTextStyle provides typography.body,
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}

/**
 * Maps every Material 3 colour role onto Reach tokens. Reach has one accent, so primary,
 * secondary and tertiary all use it; containers use [ReachColors.surface]; there is no
 * red "error" colour in the design, so errors use the accent ink on bg.
 */
private fun ReachColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accentInk,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent,
        onSecondaryContainer = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent,
        onTertiaryContainer = onAccent,
        background = bg,
        onBackground = fg,
        surface = bg,
        onSurface = fg,
        surfaceVariant = surface,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        inverseSurface = fg,
        inverseOnSurface = bg,
        error = accentInk,
        onError = bg,
        errorContainer = surface,
        onErrorContainer = fg,
        outline = line2,
        outlineVariant = line,
        scrim = bg,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = line,
    )
}

/** Maps Material 3's 15 type styles onto the closest Reach roles. */
private fun ReachTypography.toMaterialTypography(): Typography =
    Typography(
        displayLarge = clock,
        displayMedium = intentTitle,
        displaySmall = intentWord,
        headlineLarge = searchInput,
        headlineMedium = sheetTitle,
        headlineSmall = sheetTitle,
        titleLarge = appRow,
        titleMedium = title,
        titleSmall = appName,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = count,
        labelSmall = label,
    )

/** Accessors for the current theme values, mirroring `MaterialTheme.colorScheme`. */
object ReachTheme {
    val colors: ReachColors
        @Composable @ReadOnlyComposable
        get() = LocalReachColors.current

    val typography: ReachTypography
        @Composable @ReadOnlyComposable
        get() = LocalReachTypography.current

    /** Fixed sizes ([ReachDimens]); exposed here so call sites read `ReachTheme.dimens.gutter`. */
    val dimens: ReachDimens get() = ReachDimens
}

/** Fixed sizes from the spec (§5.4, §6). Plain constants: they don't change per theme. */
object ReachDimens {
    /** Minimum touch target everywhere. */
    val minTouchTarget = 48.dp

    /** App monogram circle and its outline. */
    val monogramSize = 42.dp
    val monogramRing = 1.5.dp

    /** Bottom sheet top corners. */
    val sheetCorner = 28.dp

    /** Now tiles. */
    val tileCorner = 18.dp

    /** Intent word rows on Home (minimum). */
    val intentRowHeight = 52.dp

    /** App rows in intent detail (minimum). */
    val appRowHeight = 58.dp

    /** "Type to launch" pill height. */
    val searchPillHeight = 56.dp

    /** Horizontal screen gutter used by the mockups. */
    val gutter = 24.dp
}
