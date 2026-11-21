package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.baklavapp.reachlauncher.core.designsystem.R

/**
 * Reach's type roles (spec §5.1, sizes cross-checked against the design canvas).
 *
 * Three bundled variable fonts (OFL, licences in `assets/licenses/fonts`):
 * - **Bricolage Grotesque** for display words (clock, intents, titles);
 * - **Inter** for app names and body text;
 * - **JetBrains Mono** for small UPPERCASE labels, counts, hints and monograms.
 *
 * Labels are specified in upper case, but Compose has no `text-transform`; callers pass
 * upper-cased strings (use `uppercase(Locale.getDefault())` so Turkish "i" → "İ").
 *
 * Flutter analogy: a custom `TextTheme` — except these roles are named after what they
 * are in Reach (`clock`, `intentWord`) instead of Material's `displayLarge` etc.
 */
@Immutable
data class ReachTypography(
    /** Home clock: 112sp, 500, −5sp, line height 0.9. */
    val clock: TextStyle,
    /** Intent words on Home, "Everything" heading: 46sp, 700, −1.5sp. */
    val intentWord: TextStyle,
    /** Intent title on the detail screen: 60sp, 700, −2.5sp. */
    val intentTitle: TextStyle,
    /** Search input: 40sp, 700, −1sp. */
    val searchInput: TextStyle,
    /** Sheet titles ("Appearance"): 30sp, 700, −1sp. */
    val sheetTitle: TextStyle,
    /** App rows in intent detail: Inter 22sp, 500. */
    val appRow: TextStyle,
    /** All Apps word wall: Inter 21sp, 500. */
    val wordWall: TextStyle,
    /** Search results: Inter 19sp, 500. */
    val searchResult: TextStyle,
    /** Now Playing title: Inter 18sp, 600. */
    val title: TextStyle,
    /** Now tile app names, gesture fan names: Inter 16sp, 600. */
    val appName: TextStyle,
    /** Pill and button labels ("Type to launch"): Inter 16sp, 500. */
    val button: TextStyle,
    /** Context line, empty-state text: Inter 15sp, 400. */
    val body: TextStyle,
    /**
     * Subtitles and secondary lines: Inter 13sp, 400. Smaller than §5.1's 15–22sp body
     * range on purpose: the mockups use 13–14sp for switch subtitles and the All Apps
     * subtitle. Never use it for primary content.
     */
    val caption: TextStyle,
    /** Mono labels ("NOW", "FAVOURITES"): 11sp, 500, +1.5sp. */
    val label: TextStyle,
    /** Smaller mono tags ("WORK", "PLAYING · SPOTIFY"): 10sp, 500, +1.5sp. */
    val labelSmall: TextStyle,
    /** Mono counts and the date line: 12sp, 500, +1.5sp. */
    val count: TextStyle,
    /** Letters inside a 42dp monogram circle: mono 13sp, 500. */
    val monogram: TextStyle,
) {
    companion object {
        /** The default type scale. */
        val Default: ReachTypography by lazy { build() }

        private fun build(): ReachTypography {
            val mono =
                FontFamily(
                    Font(
                        resId = R.font.jetbrains_mono,
                        weight = FontWeight(500),
                        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
                    ),
                )
            return ReachTypography(
                clock = bricolage(112.sp, 500, (-5).sp, lineHeight = 0.9.em),
                intentWord = bricolage(46.sp, 700, (-1.5).sp, lineHeight = 1.em),
                intentTitle = bricolage(60.sp, 700, (-2.5).sp, lineHeight = 0.95.em),
                searchInput = bricolage(40.sp, 700, (-1).sp),
                sheetTitle = bricolage(30.sp, 700, (-1).sp),
                appRow = inter(22.sp, 500),
                wordWall = inter(21.sp, 500),
                searchResult = inter(19.sp, 500),
                title = inter(18.sp, 600),
                appName = inter(16.sp, 600),
                button = inter(16.sp, 500),
                body = inter(15.sp, 400),
                caption = inter(13.sp, 400),
                label = mono(mono, 11.sp, 1.5.sp),
                labelSmall = mono(mono, 10.sp, 1.5.sp),
                count = mono(mono, 12.sp, 1.5.sp),
                monogram = mono(mono, 13.sp, 0.sp),
            )
        }

        /**
         * Bricolage Grotesque has an optical-size axis (12–96). Browsers set it to the
         * font size automatically (`font-optical-sizing: auto`); Android doesn't, so each
         * role gets its own instance with `opsz` = its size, capped at the axis maximum.
         */
        private fun bricolage(
            size: TextUnit,
            weight: Int,
            tracking: TextUnit,
            lineHeight: TextUnit = TextUnit.Unspecified,
        ): TextStyle {
            val family =
                FontFamily(
                    Font(
                        resId = R.font.bricolage_grotesque,
                        weight = FontWeight(weight),
                        variationSettings =
                            FontVariation.Settings(
                                FontVariation.weight(weight),
                                FontVariation.Setting("opsz", size.value.coerceIn(12f, 96f)),
                            ),
                    ),
                )
            return TextStyle(
                fontFamily = family,
                fontWeight = FontWeight(weight),
                fontSize = size,
                // As a fraction of the font size (em), not sp: the clock auto-sizes down at
                // 200% font scale, and a fixed −10sp (−5sp × 2) made its digits overlap on a
                // real device. In em the tracking shrinks with the glyphs. Same at 100%.
                letterSpacing = (tracking.value / size.value).em,
                lineHeight = lineHeight,
                lineHeightStyle = tightLineHeight,
            )
        }

        /**
         * Inter also has an optical-size axis (14–32), set to the role's size for the same
         * reason as [bricolage]. The family holds weights 400/500/600 at that size, so a
         * caller can still `copy(fontWeight = …)` within the spec's 400–600 range.
         */
        private fun inter(
            size: TextUnit,
            weight: Int,
        ): TextStyle {
            val opsz = size.value.coerceIn(14f, 32f)
            val family =
                FontFamily(
                    intArrayOf(400, 500, 600).map { w ->
                        Font(
                            resId = R.font.inter,
                            weight = FontWeight(w),
                            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opsz)),
                        )
                    },
                )
            return TextStyle(fontFamily = family, fontWeight = FontWeight(weight), fontSize = size)
        }

        private fun mono(
            family: FontFamily,
            size: TextUnit,
            tracking: TextUnit,
        ) = TextStyle(fontFamily = family, fontWeight = FontWeight(500), fontSize = size, letterSpacing = tracking)

        /** Display type sets line height < 1; trim so it doesn't leave padding above/below. */
        private val tightLineHeight =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            )
    }
}
