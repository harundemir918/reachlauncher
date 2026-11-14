package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.designsystem.theme.ColorMath.MIN_TEXT_CONTRAST
import com.baklavapp.reachlauncher.core.designsystem.theme.ColorMath.contrastRatio
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Guards the palette table (spec §5.4): every text/background pair must stay ≥ 4.5:1
 * (WCAG AA) for every palette in both themes. If you tweak a palette and this fails,
 * the tweak made some text unreadable.
 */
class PaletteContrastTest {
    private val combos = PaletteId.entries.flatMap { id -> listOf(true, false).map { dark -> id to dark } }

    @Test
    fun `every palette and theme meets the text contrast rules`() {
        val failures =
            combos.flatMap { (id, dark) ->
                val c = ReachColors.of(id, dark)
                listOf(
                    "muted/bg" to contrastRatio(c.muted, c.bg),
                    "muted/surface" to contrastRatio(c.muted, c.surface),
                    "accentInk/bg" to contrastRatio(c.accentInk, c.bg),
                    "onAccent/accent" to contrastRatio(c.onAccent, c.accent),
                ).filter { it.second < MIN_TEXT_CONTRAST }
                    .map { "$id ${if (dark) "dark" else "light"} ${it.first} = ${"%.2f".format(it.second)}" }
            }
        assertWithMessage("Pairs below $MIN_TEXT_CONTRAST:1:\n${failures.joinToString("\n")}").that(failures).isEmpty()
    }

    @Test
    fun `fg on bg is always comfortably readable`() {
        combos.forEach { (id, dark) ->
            val c = ReachColors.of(id, dark)
            assertWithMessage("$id dark=$dark fg/bg").that(contrastRatio(c.fg, c.bg)).isAtLeast(7.0)
        }
    }

    @Test
    fun `wallpaper accents meet the contrast rules for hard colours`() {
        val hardColours =
            mapOf(
                "pure white" to Color.White,
                "pure yellow" to Color(0xFFFFFF00),
                "mid grey" to Color(0xFF808080),
                // Darker than mid-grey: neither bg nor fg reaches 4.5:1 on it in any palette
                // except High Contrast, so this exercises the D6 fill nudge.
                "dark mid grey" to Color(0xFF757575),
                "near black" to Color(0xFF0A0A0A),
                "saturated blue" to Color(0xFF0000FF),
                "pure red" to Color(0xFFFF0000),
            )
        combos.forEach { (id, dark) ->
            val base = Palettes.colors(id, dark)
            hardColours.forEach { (name, wallpaper) ->
                val result = WallpaperAccent.apply(base, wallpaper)
                val where = "$id dark=$dark wallpaper=$name"
                assertWithMessage("$where accentInk/bg")
                    .that(contrastRatio(result.accentInk, result.bg))
                    .isAtLeast(MIN_TEXT_CONTRAST)
                assertWithMessage("$where onAccent/accent")
                    .that(contrastRatio(result.onAccent, result.accent))
                    .isAtLeast(MIN_TEXT_CONTRAST)
                assertWithMessage("$where onAccent is bg or fg")
                    .that(result.onAccent)
                    .isAnyOf(base.bg, base.fg)
                assertWithMessage("$where keeps base colours")
                    .that(Triple(result.bg, result.fg, result.muted))
                    .isEqualTo(Triple(base.bg, base.fg, base.muted))
            }
        }
    }
}
