package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Behaviour of the wallpaper accent beyond the contrast rules (spec §6.9, D6). */
class WallpaperAccentTest {
    @Test
    fun `a readable wallpaper colour is used as-is`() {
        val base = Palettes.colors(PaletteId.PAPER_INK, dark = true)
        val sun = Color(0xFFFFB65C)
        val result = WallpaperAccent.apply(base, sun)
        assertThat(result.accent).isEqualTo(sun)
        assertThat(result.accentInk).isEqualTo(sun)
        assertThat(result.onAccent).isEqualTo(base.bg)
    }

    @Test
    fun `a dark mid grey is nudged so text on it is readable (D6)`() {
        val base = Palettes.colors(PaletteId.PAPER_INK, dark = true)
        val grey = Color(0xFF757575)
        // Precondition: neither on-colour reaches 4.5:1 on the raw grey.
        assertThat(ColorMath.contrastRatio(base.bg, grey)).isLessThan(ColorMath.MIN_TEXT_CONTRAST)
        assertThat(ColorMath.contrastRatio(base.fg, grey)).isLessThan(ColorMath.MIN_TEXT_CONTRAST)

        val result = WallpaperAccent.apply(base, grey)
        assertThat(result.accent).isNotEqualTo(grey)
        assertThat(ColorMath.contrastRatio(result.onAccent, result.accent)).isAtLeast(ColorMath.MIN_TEXT_CONTRAST)
    }

    @Test
    fun `hard colours - accentInk readable on bg and onAccent is the stronger of bg and fg`() {
        val hard =
            mapOf(
                "pure white" to Color.White,
                "pure yellow" to Color(0xFFFFFF00),
                "mid grey" to Color(0xFF808080),
                "near black" to Color(0xFF0A0A0A),
            )
        PaletteId.entries.forEach { id ->
            listOf(true, false).forEach { dark ->
                val base = Palettes.colors(id, dark)
                hard.forEach { (name, raw) ->
                    val where = "$id dark=$dark $name"
                    val result = WallpaperAccent.apply(base, raw)
                    assertWithMessage("$where accentInk/bg")
                        .that(ColorMath.contrastRatio(result.accentInk, base.bg))
                        .isAtLeast(ColorMath.MIN_TEXT_CONTRAST)
                    assertWithMessage("$where onAccent/accent")
                        .that(ColorMath.contrastRatio(result.onAccent, result.accent))
                        .isAtLeast(ColorMath.MIN_TEXT_CONTRAST)
                    // Spec §6.9: onAccent = whichever of bg/fg contrasts more with the accent.
                    val other = if (result.onAccent == base.bg) base.fg else base.bg
                    assertWithMessage("$where onAccent is the stronger choice")
                        .that(ColorMath.contrastRatio(result.onAccent, raw))
                        .isAtLeast(ColorMath.contrastRatio(other, raw))
                }
            }
        }
    }

    @Test
    fun `wallpaper mode switches the root to the scrim and full-screen surfaces to transparent`() {
        val opaque = ReachColors.of(PaletteId.PAPER_INK, isDark = true)
        assertThat(opaque.root).isEqualTo(opaque.bg)
        assertThat(opaque.backdrop).isEqualTo(opaque.bg)

        val dark = ReachColors.from(Palettes.colors(PaletteId.PAPER_INK, true), isDark = true, wallpaperMode = true)
        assertThat(dark.root).isEqualTo(dark.bg.copy(alpha = ReachColors.SCRIM_ALPHA_DARK))
        assertThat(dark.backdrop).isEqualTo(Color.Transparent)
        val light = ReachColors.from(Palettes.colors(PaletteId.PAPER_INK, false), isDark = false, wallpaperMode = true)
        assertThat(light.root.alpha).isWithin(0.01f).of(ReachColors.SCRIM_ALPHA_LIGHT)
    }

    @Test
    fun `in wallpaper mode text tokens stay readable on the scrim over the worst wallpaper (D65)`() {
        val hard = listOf(null, Color.White, Color(0xFFFFFF00), Color(0xFF808080), Color(0xFF0A0A0A))
        PaletteId.entries.forEach { id ->
            listOf(true, false).forEach { dark ->
                hard.forEach { raw ->
                    val base = Palettes.colors(id, dark).let { if (raw == null) it else WallpaperAccent.apply(it, raw) }
                    val colors = ReachColors.from(base, dark, wallpaperMode = true)
                    // Both extremes behind the scrim, composited by hand (not via the helper under test).
                    val alpha = if (dark) ReachColors.SCRIM_ALPHA_DARK else ReachColors.SCRIM_ALPHA_LIGHT
                    listOf(Color.White, Color.Black).forEach { wallpaper ->
                        val onScreen = ColorMath.mix(wallpaper, colors.bg, alpha)
                        mapOf(
                            "fg" to colors.fg,
                            "muted" to colors.muted,
                            "accentInk" to colors.accentInk,
                            "soft" to colors.soft,
                            "groupDim" to colors.groupDim,
                        ).forEach { (token, color) ->
                            assertWithMessage("$id dark=$dark accent=$raw $token over $wallpaper")
                                .that(ColorMath.contrastRatio(color, onScreen))
                                .isAtLeast(ColorMath.MIN_TEXT_CONTRAST)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `wallpaper alpha is ignored`() {
        val base = Palettes.colors(PaletteId.LIME, dark = true)
        val translucent = Color(0x80FFB65C)
        assertThat(WallpaperAccent.apply(base, translucent).accent.alpha).isEqualTo(1f)
    }
}
