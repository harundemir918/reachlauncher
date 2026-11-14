package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorMathTest {
    @Test
    fun `contrast of black and white is 21`() {
        assertThat(ColorMath.contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(ColorMath.contrastRatio(Color.White, Color.White)).isWithin(0.001).of(1.0)
    }

    @Test
    fun `known WCAG reference value`() {
        // #777777 on white is the classic "just fails AA" grey: 4.48:1.
        assertThat(ColorMath.contrastRatio(Color(0xFF777777), Color.White)).isWithin(0.01).of(4.48)
    }

    @Test
    fun `mix is a linear blend of sRGB channels`() {
        val mid = ColorMath.mix(Color.Black, Color.White, 0.5f)
        assertThat(mid.red).isWithin(0.01f).of(0.5f)
        assertThat(ColorMath.mix(Color.Red, Color.Blue, 0f)).isEqualTo(Color.Red)
        assertThat(ColorMath.mix(Color.Red, Color.Blue, 1f)).isEqualTo(Color.Blue)
    }

    @Test
    fun `derived tokens match the spec formulas`() {
        // Lime dark: surface = mix(#101114, #ECEAE3, 0.07) = #1F2022 (sRGB channels are
        // stored as 8-bit values, so each channel rounds to the nearest integer).
        val c = ReachColors.of(PaletteId.LIME, isDark = true)
        assertThat(toHex(c.surface)).isEqualTo("1F2022")
        // Light surface mixes towards white: mix(#F4F2EC, #FFFFFF, 0.75) = #FCFCFA.
        assertThat(toHex(ReachColors.of(PaletteId.LIME, isDark = false).surface)).isEqualTo("FCFCFA")
        // The other bg→fg mixes: line 12%, line2 20%, divider 9%, ring 35%, faint 45%.
        assertThat(toHex(c.line)).isEqualTo("2A2B2D")
        assertThat(toHex(c.line2)).isEqualTo("3C3C3D")
        assertThat(toHex(c.divider)).isEqualTo("242527")
        assertThat(toHex(c.ring)).isEqualTo("5D5D5C")
        assertThat(toHex(c.faint)).isEqualTo("737371")
        // M1 additions: soft 84%, groupDim 62%, dim 20% (mockup: #C9CBD1, #8E9098, #3A3C42).
        assertThat(toHex(c.soft)).isEqualTo("C9C7C2")
        assertThat(toHex(c.groupDim)).isEqualTo("989894")
        assertThat(toHex(c.dim)).isEqualTo("3C3C3D")
        assertThat(c.scrim.alpha).isWithin(0.01f).of(0.82f)
        assertThat(ReachColors.of(PaletteId.LIME, isDark = false).scrim.alpha).isWithin(0.01f).of(0.86f)
    }

    @Test
    fun `ensureContrast leaves passing colours alone and fixes failing ones`() {
        val bg = Color(0xFF101114)
        val lime = Color(0xFFC8F25A)
        assertThat(ColorMath.ensureContrast(lime, bg)).isEqualTo(lime)
        val fixed = ColorMath.ensureContrast(Color(0xFF303030), bg)
        assertThat(ColorMath.contrastRatio(fixed, bg)).isAtLeast(4.5)
    }

    @Test
    fun `extremeFor uses the real WCAG crossover, not luminance 0_5`() {
        // Luminance ≈ 0.3: looks mid-grey, but black text wins clearly (≈7:1 vs ≈3:1).
        val greyish = Color(0xFF959595)
        assertThat(ColorMath.relativeLuminance(greyish)).isWithin(0.02).of(0.3)
        assertThat(ColorMath.extremeFor(greyish)).isEqualTo(Color.Black)
        assertThat(ColorMath.extremeFor(Color(0xFF101114))).isEqualTo(Color.White)
    }

    @Test
    fun `ensureContrast succeeds on any background`() {
        val backgrounds = (0..255 step 15).map { v -> Color(v / 255f, v / 255f, v / 255f) }
        backgrounds.forEach { bg ->
            val fixed = ColorMath.ensureContrast(bg, bg)
            assertThat(ColorMath.contrastRatio(fixed, bg)).isAtLeast(ColorMath.MIN_TEXT_CONTRAST)
        }
    }

    private fun toHex(color: Color): String =
        listOf(color.red, color.green, color.blue)
            .joinToString("") { "%02X".format(Math.round(it * 255)) }
}
