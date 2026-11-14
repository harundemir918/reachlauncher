package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.google.common.truth.Truth.assertThat
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
    fun `wallpaper alpha is ignored`() {
        val base = Palettes.colors(PaletteId.LIME, dark = true)
        val translucent = Color(0x80FFB65C)
        assertThat(WallpaperAccent.apply(base, translucent).accent.alpha).isEqualTo(1f)
    }
}
