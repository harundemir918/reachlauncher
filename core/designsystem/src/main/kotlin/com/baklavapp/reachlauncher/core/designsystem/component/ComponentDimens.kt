package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.ui.unit.dp

/**
 * Component-level sizes measured from the design mockups (paddings, gaps, outline widths).
 *
 * Why a second object next to `ReachDimens`: `ReachDimens` holds the *spec* sizes (touch
 * targets, monogram, row heights); these are the finer measurements each component needs.
 * Keeping them here, named, means no component hides a magic `12.dp`, and screens built
 * from these components (Home, intent detail) can reuse the same gaps to line up with them.
 *
 * Mockup sizes below 48dp for something tappable (the 44dp back/close/play buttons and
 * intent chips) are raised to `ReachDimens.minTouchTarget`: the spec's 48dp rule wins.
 *
 * Flutter analogy: the `const EdgeInsets`/`SizedBox` values you'd collect in an `AppSpacing`
 * class.
 */
object ReachComponentDimens {
    /** Hairline outlines: Now tiles, the search pill, outlined icon buttons, chips. */
    val hairline = 1.dp

    /** Emphasised outlines: monogram rings (= `ReachDimens.monogramRing`), outlined pill buttons. */
    val outline = 1.5.dp

    /** Corner radius of every pill-shaped control at its 48dp minimum height. */
    val pillCorner = 24.dp

    /** Gap between an intent word and its count/badge. */
    val intentWordTrailingGap = 10.dp

    /** Gap between the intent word button and extra content (the Listen row's player). */
    val intentRowExtraGap = 14.dp

    /** Vertical gap between stacked intent rows on Home. */
    val intentRowSpacing = 2.dp

    /** Vertical padding inside an intent word button. */
    val intentWordVerticalPadding = 2.dp

    /** Badge ("3 NEW", "2") inner padding. */
    val badgeHorizontalPadding = 8.dp
    val badgeVerticalPadding = 4.dp

    /** Now tiles: inner padding, gap between reason and name, minimum height, gap between tiles. */
    val nowTilePadding = 12.dp
    val nowTileGap = 6.dp
    val nowTileMinHeight = 72.dp
    val nowTileSpacing = 8.dp

    /** App rows: gap between monogram, name, badge and arrow; arrow size. */
    val appRowGap = 16.dp
    val appRowArrowSize = 18.dp

    /** Profile tag ("WORK") gap from the name. */
    val profileTagGap = 8.dp

    /** "Type to launch" pill: side padding, gap between icon/text/hint, icon size. */
    val searchPillHorizontalPadding = 20.dp
    val searchPillGap = 12.dp
    val searchPillIconSize = 20.dp

    /** Pill buttons and chips: side padding, vertical padding, gap between label lines. */
    val pillHorizontalPadding = 16.dp
    val pillVerticalPadding = 6.dp
    val pillLineGap = 2.dp
    val chipHorizontalPadding = 12.dp

    /** Round icon buttons: glyph size for filled (play/pause), outlined (back) and plain (skip). */
    val iconFilledGlyph = 16.dp
    val iconOutlinedGlyph = 20.dp
    val iconPlainGlyph = 18.dp

    /** Gap between the back button and the header label. */
    val backHeaderGap = 12.dp

    /** Gap between the inline play button and the track title on the Listen row. */
    val inlineMediaGap = 10.dp

    /**
     * Visual diameter of the Listen row's play button (mockup: 44dp). Its touch target is
     * still 48dp via `minimumInteractiveComponentSize()`.
     */
    val inlinePlayButtonSize = 44.dp
}
