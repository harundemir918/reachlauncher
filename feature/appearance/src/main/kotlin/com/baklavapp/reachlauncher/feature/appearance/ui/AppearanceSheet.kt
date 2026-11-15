package com.baklavapp.reachlauncher.feature.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.PillButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.Palettes
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.theme.paletteDisplayName
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.appearance.AppearanceUiState
import com.baklavapp.reachlauncher.feature.appearance.R

/*
 * The Appearance sheet (spec §6.8): a bottom sheet over Home with a dim scrim, so Home —
 * re-theming live behind it — stays visible above.
 *
 * Flutter analogy: the body of a `showModalBottomSheet`, but drawn inside Home's own tree
 * (a `Stack` with a scrim and a card) because Reach has no navigator (D28). The controls are
 * hand-built rather than Material's `SegmentedButton`/`ChoiceChip`, so they look like Reach,
 * but they carry the *same semantics* Material would give them:
 * - segments: `selectable(role = RadioButton)` inside a `selectableGroup()` — TalkBack says
 *   "Dark, selected, 2 of 3" (≈ `Semantics(inMutuallyExclusiveGroup: true, checked: …)`);
 * - the wallpaper row: `toggleable(role = Switch)` on the whole row (≈ `SwitchListTile`), so
 *   the label and the switch are one 48dp+ target and one TalkBack stop.
 *
 * The whole card scrolls, so at 200 % font nothing is clipped (spec §6.10).
 */

private val SheetPadding = 24.dp
private val SheetTopMargin = 48.dp
private val SectionGap = 22.dp
private val LabelGap = 10.dp
private val SwatchWidth = 84.dp
private val SwatchMinHeight = 96.dp
private val SwatchCorner = 18.dp
private val SwatchPadding = 10.dp
private val SwatchDot = 14.dp
private val SwatchDotGap = 6.dp
private val SwatchSpacing = 10.dp
private val SelectedBorder = 2.dp
private val SegmentPadding = 4.dp
private val SegmentTextPadding = 8.dp
private val SwitchGap = 16.dp
private const val SCRIM_ALPHA = 0.6f

/**
 * What the sheet's controls do. Bundled so the composables don't take five lambdas each;
 * `@Immutable` so Compose can skip recomposing children when the instance is unchanged.
 */
@Immutable
internal class AppearanceCallbacks(
    val onThemeMode: (ThemeMode) -> Unit,
    val onPalette: (PaletteId) -> Unit,
    val onShowWallpaper: (Boolean) -> Unit,
    val onThumbSide: (ThumbSide) -> Unit,
    val onDoubleTapToLock: (Boolean) -> Unit = {},
    val onOpenAccessibilitySettings: () -> Unit = {},
)

/**
 * Stateless Appearance sheet for [state]. Tapping the dim area above it calls [onClose],
 * like a modal sheet; the "Done" pill and system Back (handled by Home) do too.
 *
 * Accessibility: the card is a pane titled "Appearance", announced when it opens; the scrim
 * is not a control (Done and Back are the accessible ways out).
 */
@Composable
internal fun AppearanceSheet(
    state: AppearanceUiState,
    callbacks: AppearanceCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val title = stringResource(R.string.appearance_title)
    val shape = RoundedCornerShape(topStart = ReachDimens.sheetCorner, topEnd = ReachDimens.sheetCorner)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = SCRIM_ALPHA))
                // Plain tap detector, not clickable: the scrim isn't a control for TalkBack.
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = SheetTopMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(ReachComponentDimens.hairline, colors.line2, shape)
                    .background(colors.bg, shape)
                    // Swallow taps on the sheet itself so they don't reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title }
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            Header(title, onClose)
            Section(
                label =
                    stringResource(
                        if (state.themeMode == ThemeMode.AUTO) R.string.appearance_theme_follows else R.string.appearance_theme,
                    ),
            ) {
                Segmented(
                    options = ThemeMode.entries,
                    selected = state.themeMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = callbacks.onThemeMode,
                )
            }
            Section(
                label =
                    stringResource(
                        if (state.showWallpaper) R.string.appearance_palette_wallpaper else R.string.appearance_palette,
                    ),
            ) {
                PaletteRow(selected = state.palette, onSelect = callbacks.onPalette)
            }
            WallpaperSwitch(checked = state.showWallpaper, onChange = callbacks.onShowWallpaper)
            Section(label = stringResource(R.string.appearance_thumb_side)) {
                Segmented(
                    options = ThumbSide.entries,
                    selected = state.thumbSide,
                    label = { stringResource(it.labelRes()) },
                    onSelect = callbacks.onThumbSide,
                )
            }
            GesturesSection(state, callbacks)
        }
    }
}

/**
 * Home's screen-action gestures (spec §7.4, D66): the opt-in switch for double-tap to lock and,
 * while Reach's accessibility service is off, one button to the system's Accessibility
 * settings. This is the *only* place Reach offers the service — no Home card, no dialog —
 * because it is a convenience few users need, and asking for an accessibility service
 * unprompted would read as a red flag.
 */
@Composable
private fun GesturesSection(
    state: AppearanceUiState,
    callbacks: AppearanceCallbacks,
) {
    Section(label = stringResource(R.string.appearance_gestures)) {
        SwitchRow(
            title = stringResource(R.string.appearance_double_tap_lock),
            subtitle =
                stringResource(
                    if (state.accessibilityServiceOn) {
                        R.string.appearance_double_tap_lock_on
                    } else {
                        R.string.appearance_double_tap_lock_needs_service
                    },
                ),
            checked = state.doubleTapToLock,
            onChange = callbacks.onDoubleTapToLock,
        )
        if (!state.accessibilityServiceOn) {
            Text(
                text = stringResource(R.string.appearance_service_why),
                style = ReachTheme.typography.caption,
                color = ReachTheme.colors.muted,
            )
            PillButton(
                text = stringResource(R.string.appearance_service_turn_on),
                onClick = callbacks.onOpenAccessibilitySettings,
                style = PillButtonStyle.Outlined,
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = ReachTheme.typography.sheetTitle,
            color = ReachTheme.colors.fg,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        PillButton(text = stringResource(R.string.appearance_done), onClick = onClose)
    }
}

@Composable
private fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        MonoLabel(text = label, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * A segmented control: a pill-shaped track with one accent-filled segment. Each segment is
 * a radio button for TalkBack ("Light, not selected, 3 of 3").
 */
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = ReachTheme.colors
    val track = RoundedCornerShape(ReachComponentDimens.pillCorner)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(ReachComponentDimens.hairline, colors.line2, track)
                .padding(SegmentPadding)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentPadding),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val segment = RoundedCornerShape(ReachComponentDimens.pillCorner)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(segment)
                        .background(if (isSelected) colors.accent else colors.bg)
                        .selectable(
                            selected = isSelected,
                            onClick = { if (!isSelected) onSelect(option) },
                            role = Role.RadioButton,
                            interactionSource = null,
                            indication = ripple(),
                        ).heightIn(min = ReachDimens.minTouchTarget)
                        .padding(horizontal = SegmentTextPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) colors.onAccent else colors.fg,
                    style = ReachTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The seven palette swatches, each painted in *that* palette's real colours for the current
 * dark/light theme, so the user previews it before choosing. The selected one gets a 2dp
 * border in the *current* theme's `accentInk` (readable on the sheet's bg by construction).
 */
@Composable
private fun PaletteRow(
    selected: PaletteId,
    onSelect: (PaletteId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SwatchSpacing),
    ) {
        PaletteId.entries.forEach { id ->
            Swatch(id = id, selected = id == selected, onClick = { onSelect(id) })
        }
    }
}

@Composable
private fun Swatch(
    id: PaletteId,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ReachTheme.colors
    val swatch = Palettes.colors(id, colors.isDark)
    val shape = RoundedCornerShape(SwatchCorner)
    Column(
        modifier =
            Modifier
                .width(SwatchWidth)
                // Min height, not fixed: at 200 % font a two-line name grows the card.
                .heightIn(min = SwatchMinHeight)
                .clip(shape)
                .background(swatch.bg)
                .border(
                    width = if (selected) SelectedBorder else ReachComponentDimens.hairline,
                    color = if (selected) colors.accentInk else colors.line2,
                    shape = shape,
                ).selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                    interactionSource = null,
                    indication = ripple(),
                ).padding(SwatchPadding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SwatchDotGap)) {
            Box(Modifier.size(SwatchDot).background(swatch.accent, CircleShape))
            Box(Modifier.size(SwatchDot).background(swatch.fg, CircleShape))
        }
        Text(
            text = paletteDisplayName(id),
            color = swatch.fg,
            style = ReachTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = SwatchDotGap),
        )
    }
}

/** "Show wallpaper": the whole row is one switch (label, subtitle and the switch itself). */
@Composable
private fun WallpaperSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchRow(
        title = stringResource(R.string.appearance_wallpaper),
        subtitle = stringResource(R.string.appearance_wallpaper_subtitle),
        checked = checked,
        onChange = onChange,
    )
}

/** A row that is one switch for TalkBack and touch (≈ `SwitchListTile`): title, subtitle, switch. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = ReachTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReachComponentDimens.pillCorner))
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = ReachDimens.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SwitchGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ReachTheme.typography.title, color = colors.fg)
            Text(text = subtitle, style = ReachTheme.typography.caption, color = colors.muted)
        }
        // Visual only (onCheckedChange = null): the row above owns the toggle and semantics.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.line2,
                ),
        )
    }
}

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.AUTO -> R.string.appearance_theme_auto
        ThemeMode.DARK -> R.string.appearance_theme_dark
        ThemeMode.LIGHT -> R.string.appearance_theme_light
    }

private fun ThumbSide.labelRes(): Int =
    when (this) {
        ThumbSide.LEFT -> R.string.appearance_thumb_left
        ThumbSide.RIGHT -> R.string.appearance_thumb_right
    }

@Preview(name = "Appearance sheet", widthDp = 390, heightDp = 844)
@Composable
private fun AppearanceSheetPreview() {
    ReachTheme(palette = PaletteId.LIME) {
        AppearanceSheet(
            state = AppearanceUiState(loaded = true, showWallpaper = true),
            callbacks = AppearanceCallbacks({}, {}, {}, {}),
            onClose = {},
        )
    }
}
