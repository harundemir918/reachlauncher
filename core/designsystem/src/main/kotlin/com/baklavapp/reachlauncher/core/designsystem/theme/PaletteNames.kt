package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.model.PaletteId

/*
 * Localised palette names ("Lime", "Paper & Ink"…), shared by Search's "Switch to <Palette>"
 * command and the Appearance sheet's swatches (M4), so both always say the same thing.
 *
 * Why here and not in `:core:model`: `PaletteId` is pure Kotlin and has no access to
 * Android resources; display text belongs next to the theme that defines the palettes.
 *
 * Flutter analogy: an extension `String displayName(AppLocalizations l10n)` on your palette
 * enum, reading from the generated `l10n` class.
 */

/** String resource with the display name of this palette. */
@get:StringRes
val PaletteId.displayNameRes: Int
    get() =
        when (this) {
            PaletteId.LIME -> R.string.ds_palette_lime
            PaletteId.PAPER_INK -> R.string.ds_palette_paper_ink
            PaletteId.NIGHT_TIDE -> R.string.ds_palette_night_tide
            PaletteId.TERRACOTTA -> R.string.ds_palette_terracotta
            PaletteId.FOREST -> R.string.ds_palette_forest
            PaletteId.DUSK -> R.string.ds_palette_dusk
            PaletteId.HIGH_CONTRAST -> R.string.ds_palette_high_contrast
        }

/** The palette's name in the current language, e.g. "Night Tide" / "Gece Gelgiti". */
@Composable
@ReadOnlyComposable
fun paletteDisplayName(palette: PaletteId): String = stringResource(palette.displayNameRes)
