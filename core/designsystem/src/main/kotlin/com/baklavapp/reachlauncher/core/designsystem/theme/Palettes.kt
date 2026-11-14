package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.baklavapp.reachlauncher.core.model.PaletteId

/*
 * Flutter analogy: the `const Color(0xFF…)` constants you'd keep in an `AppColors` class,
 * grouped per palette like a set of `ColorScheme`s. Compose's `Color(0xFF101114)` takes
 * the same ARGB hex literal as Flutter's `Color(0xFF101114)`.
 */

/**
 * The six hand-picked colours of one palette in one theme (spec §5.2). Everything else
 * ([ReachColors]) is derived from these.
 *
 * @property accent the accent used as a *fill* (pills, highlights, the play button).
 * @property accentInk the accent used as *text or outline* on [bg]. On light themes it is
 *   often a darker shade of [accent], because a pale accent isn't readable as text.
 * @property onAccent text and icons drawn on top of an [accent] fill.
 */
data class PaletteColors(
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val accentInk: Color,
    val onAccent: Color,
)

/**
 * The palette table from the spec. Looked up by [PaletteId] at runtime: settings store
 * only the id, so changing a value here updates every existing user on the next release.
 */
object Palettes {
    /** Returns the colours for [id] in the dark or light theme. */
    fun colors(
        id: PaletteId,
        dark: Boolean,
    ): PaletteColors = if (dark) darkPalettes.getValue(id) else lightPalettes.getValue(id)

    private val darkPalettes: Map<PaletteId, PaletteColors> =
        mapOf(
            PaletteId.LIME to palette(0xFF101114, 0xFFECEAE3, 0xFFA3A5AC, 0xFFC8F25A, 0xFFC8F25A, 0xFF101114),
            PaletteId.PAPER_INK to palette(0xFF0C0C0C, 0xFFF2F0EA, 0xFFA6A39C, 0xFFF2F0EA, 0xFFF2F0EA, 0xFF0C0C0C),
            PaletteId.NIGHT_TIDE to palette(0xFF0B1220, 0xFFE6EDF7, 0xFF93A1B8, 0xFF7FD4FF, 0xFF7FD4FF, 0xFF0B1220),
            PaletteId.TERRACOTTA to palette(0xFF17120F, 0xFFF1E8DF, 0xFFB3A396, 0xFFFF8A5C, 0xFFFF8A5C, 0xFF17120F),
            PaletteId.FOREST to palette(0xFF0F1512, 0xFFE4EDE6, 0xFF9DB0A3, 0xFF8FE3A8, 0xFF8FE3A8, 0xFF0F1512),
            PaletteId.DUSK to palette(0xFF120F18, 0xFFECE7F5, 0xFFA79FB8, 0xFFC6A8FF, 0xFFC6A8FF, 0xFF120F18),
            PaletteId.HIGH_CONTRAST to palette(0xFF000000, 0xFFFFFFFF, 0xFFCCCCCC, 0xFFFFE14D, 0xFFFFE14D, 0xFF000000),
        )

    private val lightPalettes: Map<PaletteId, PaletteColors> =
        mapOf(
            PaletteId.LIME to palette(0xFFF4F2EC, 0xFF141414, 0xFF5E6066, 0xFFC8F25A, 0xFF3E6A00, 0xFF141414),
            PaletteId.PAPER_INK to palette(0xFFF7F5F0, 0xFF111111, 0xFF5F5D58, 0xFF111111, 0xFF111111, 0xFFF7F5F0),
            PaletteId.NIGHT_TIDE to palette(0xFFEEF3F8, 0xFF0B1220, 0xFF4E5C72, 0xFF7FD4FF, 0xFF005C8A, 0xFF0B1220),
            PaletteId.TERRACOTTA to palette(0xFFF6EFE7, 0xFF1C1511, 0xFF6B5A4D, 0xFFFF8A5C, 0xFF9A3B12, 0xFF1C1511),
            PaletteId.FOREST to palette(0xFFEFF4EF, 0xFF0F1512, 0xFF4F6356, 0xFF8FE3A8, 0xFF1F6B3A, 0xFF0F1512),
            PaletteId.DUSK to palette(0xFFF3F0F8, 0xFF15111C, 0xFF5E5670, 0xFFC6A8FF, 0xFF5B3AA8, 0xFF15111C),
            PaletteId.HIGH_CONTRAST to palette(0xFFFFFFFF, 0xFF000000, 0xFF3D3D3D, 0xFFFFE14D, 0xFF000000, 0xFF000000),
        )

    private fun palette(
        bg: Long,
        fg: Long,
        muted: Long,
        accent: Long,
        accentInk: Long,
        onAccent: Long,
    ) = PaletteColors(Color(bg), Color(fg), Color(muted), Color(accent), Color(accentInk), Color(onAccent))
}
