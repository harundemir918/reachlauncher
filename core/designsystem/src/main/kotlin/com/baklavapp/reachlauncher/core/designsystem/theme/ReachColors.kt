package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.designsystem.theme.ColorMath.mix
import com.baklavapp.reachlauncher.core.model.PaletteId

/**
 * Every colour token a Reach screen may use: the palette's six base colours plus the
 * tokens derived from them (spec §5.3). Read it in composables via `ReachTheme.colors`.
 *
 * Derived tokens are *computed* from bg/fg rather than hand-picked, so a new palette
 * only needs six colours and its lines, rings and surfaces stay consistent.
 *
 * Flutter analogy: a `ThemeExtension<ReachColors>` — a typed bag of app-specific colours
 * you'd read with `Theme.of(context).extension<ReachColors>()!`.
 *
 * @property surface tiles and cards (Now tiles, the Appearance sheet).
 * @property line outlines (the type-to-launch pill); [line2] stronger outlines.
 * @property divider hairlines between rows.
 * @property ring the 1.5dp outline of app monograms.
 * @property faint decorative chevrons only — never for text (too low contrast).
 * @property scrim wallpaper-mode overlay: [bg] at 82% (dark) / 86% (light) opacity.
 * @property soft secondary body text that should read stronger than [muted] (the gesture
 *   tutorial steps): mix(bg, fg, 0.84). Text-safe (≥ 4.5:1 on bg and surface, tested).
 * @property groupDim text that is present but not in focus (All Apps groups other than the
 *   one under the letter rail, inactive rail letters): mix(bg, fg, 0.62). Text-safe (tested).
 * @property dim intent words that are *inactive* while the press-slide-release fan is open:
 *   mix(bg, fg, 0.20). **Not** text-safe (~1.5–1.9:1): allowed only for text of a
 *   temporarily inactive control, which WCAG 1.4.3 exempts. Never for readable content.
 */
@Immutable
data class ReachColors(
    val isDark: Boolean,
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val accentInk: Color,
    val onAccent: Color,
    val surface: Color,
    val line: Color,
    val line2: Color,
    val divider: Color,
    val ring: Color,
    val faint: Color,
    val scrim: Color,
    val soft: Color,
    val groupDim: Color,
    val dim: Color,
) {
    companion object {
        /** Opacity of the wallpaper-mode scrim (spec §6.9). */
        const val SCRIM_ALPHA_DARK = 0.82f
        const val SCRIM_ALPHA_LIGHT = 0.86f

        /** Derives the full token set from a palette's base colours. */
        fun from(
            base: PaletteColors,
            isDark: Boolean,
        ): ReachColors =
            ReachColors(
                isDark = isDark,
                bg = base.bg,
                fg = base.fg,
                muted = base.muted,
                accent = base.accent,
                accentInk = base.accentInk,
                onAccent = base.onAccent,
                surface = if (isDark) mix(base.bg, base.fg, 0.07f) else mix(base.bg, Color.White, 0.75f),
                line = mix(base.bg, base.fg, 0.12f),
                line2 = mix(base.bg, base.fg, 0.20f),
                divider = mix(base.bg, base.fg, 0.09f),
                ring = mix(base.bg, base.fg, 0.35f),
                faint = mix(base.bg, base.fg, 0.45f),
                scrim = base.bg.copy(alpha = if (isDark) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT),
                // Added in M1 for mockup roles that had no token; t picked so dark Lime
                // approximates the mockup values (#C9CBD1 / #8E9098 / #3A3C42). groupDim
                // is a little lighter than the mockup (0.62, not 0.57) so it stays ≥ 4.5:1
                // on bg in every palette (light Lime at 0.57 would be ~4.0:1).
                soft = mix(base.bg, base.fg, 0.84f),
                groupDim = mix(base.bg, base.fg, 0.62f),
                dim = mix(base.bg, base.fg, 0.20f),
            )

        /** Convenience: tokens for a stored [PaletteId] in the given theme. */
        fun of(
            id: PaletteId,
            isDark: Boolean,
        ): ReachColors = from(Palettes.colors(id, isDark), isDark)
    }
}

/**
 * Accent colours picked from the wallpaper (spec §6.9). The base palette keeps its
 * bg/fg/muted; only accent, accentInk and onAccent change.
 */
object WallpaperAccent {
    /**
     * Builds an accent set from the wallpaper's primary colour:
     * - `accent` = the wallpaper colour (made opaque);
     * - `accentInk` = accent darkened/lightened until it reaches 4.5:1 on bg;
     * - `onAccent` = whichever of bg/fg contrasts more with the accent.
     *
     * Deviation (docs/DECISIONS.md D6): for mid-luminance accents (greys around
     * `#6A`–`#7E`) neither bg nor fg reaches 4.5:1. In that case the accent fill itself
     * is nudged away from `onAccent` until text on it is readable.
     */
    fun apply(
        base: PaletteColors,
        wallpaperColor: Color,
    ): PaletteColors {
        val raw = wallpaperColor.copy(alpha = 1f)
        val onAccent = if (ColorMath.contrastRatio(base.bg, raw) >= ColorMath.contrastRatio(base.fg, raw)) base.bg else base.fg
        val accent =
            if (ColorMath.contrastRatio(onAccent, raw) >= ColorMath.MIN_TEXT_CONTRAST) {
                raw
            } else {
                ColorMath.pushTowards(raw, ColorMath.extremeFor(onAccent)) {
                    ColorMath.contrastRatio(onAccent, it) >= ColorMath.MIN_TEXT_CONTRAST
                }
            }
        val accentInk = ColorMath.ensureContrast(accent, base.bg)
        return base.copy(accent = accent, accentInk = accentInk, onAccent = onAccent)
    }
}
