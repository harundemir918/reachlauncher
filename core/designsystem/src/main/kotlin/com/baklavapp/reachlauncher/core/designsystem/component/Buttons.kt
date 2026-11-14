package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme

/*
 * Buttons — pill buttons, intent chips, round icon buttons, the back header.
 *
 * Flutter analogy: your own `FilledButton` / `OutlinedButton` / `ChoiceChip` / `IconButton`
 * equivalents, each a StatelessWidget taking an `onPressed` callback. We don't use
 * Material's buttons because Reach's shapes, sizes and colours differ; we reuse only
 * Material's ripple and `minimumInteractiveComponentSize()`.
 *
 * `Modifier.minimumInteractiveComponentSize()` makes a visually smaller control still
 * occupy (and accept touches in) at least 48×48dp — like wrapping a widget in a
 * `SizedBox(48, 48)` with `HitTestBehavior.opaque`, but without changing its look.
 */

/** Fill style of a [PillButton]. */
enum class PillButtonStyle {
    /** Accent fill, onAccent text: primary actions ("Done"). */
    Filled,

    /** 1.5dp accentInk outline and text on bg: app-shortcut pills ("Message Ayşe · WHATSAPP"). */
    Outlined,
}

/**
 * A pill-shaped text button, at least 48dp tall.
 *
 * ```kotlin
 * PillButton("Done", onClick = onClose)                                         // filled
 * PillButton("Message Ayşe", onClick = …, style = PillButtonStyle.Outlined,
 *            secondaryLabel = "WhatsApp")                                       // shortcut pill
 * ```
 *
 * @param secondaryLabel optional second line in mono caps (the shortcut's source app).
 *   Pass natural case; it is upper-cased for display and read naturally by TalkBack.
 * @param contentDescription overrides the spoken text; defaults to "[text], [secondaryLabel]".
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillButtonStyle = PillButtonStyle.Filled,
    secondaryLabel: String? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachComponentDimens.pillCorner)
    val contentColor = if (style == PillButtonStyle.Filled) colors.onAccent else colors.accentInk
    val decorated =
        when (style) {
            PillButtonStyle.Filled -> modifier.clip(shape).background(colors.accent)
            PillButtonStyle.Outlined -> modifier.clip(shape).border(ReachComponentDimens.outline, colors.accentInk, shape)
        }
    Column(
        modifier =
            decorated
                .reachClickable(
                    onClick = onClick,
                    description = contentDescription ?: listOfNotNull(text, secondaryLabel).joinToString(", "),
                    enabled = enabled,
                ).heightIn(min = ReachDimens.minTouchTarget)
                .padding(
                    horizontal = ReachComponentDimens.pillHorizontalPadding,
                    vertical = ReachComponentDimens.pillVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(ReachComponentDimens.pillLineGap, Alignment.CenterVertically),
    ) {
        Text(text = text, color = contentColor, style = ReachTheme.typography.body.copy(fontWeight = FontWeight.SemiBold))
        if (secondaryLabel != null) {
            MonoLabel(text = secondaryLabel, color = contentColor, style = ReachTheme.typography.labelSmall)
        }
    }
}

/**
 * One chip of the intent switcher at the bottom of intent detail (spec §6.4). The
 * [selected] chip (the current intent) is filled with accent.
 *
 * Semantics: a selectable tab, so TalkBack says "Listen, selected, tab 2 of 6" when the
 * chips sit in a container marked `selectableGroup()` — put that on your scrolling Row.
 */
@Composable
fun IntentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachComponentDimens.pillCorner)
    val background = if (selected) colors.accent else Color.Transparent
    val border = if (selected) colors.accent else colors.line2
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(background)
                .border(ReachComponentDimens.hairline, border, shape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = null,
                    indication = ripple(),
                ).heightIn(min = ReachDimens.minTouchTarget)
                .padding(horizontal = ReachComponentDimens.chipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccent else colors.fg,
            style = ReachTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Look of an [IconCircleButton]. */
enum class IconCircleButtonStyle {
    /** Accent circle, onAccent glyph: play/pause. */
    Filled,

    /** line2 hairline ring, fg glyph: back, close. */
    Outlined,

    /** No background, fg glyph: previous/next track. */
    Plain,
}

/**
 * A round, icon-only button. Icon-only means TalkBack has nothing to read, so
 * [contentDescription] is required ("Back", "Pause Daily Mix 1").
 *
 * @param size visual diameter, 44–48dp in the mockups. Whatever you pass, the touch target
 *   stays ≥ 48dp ([minimumInteractiveComponentSize]).
 * @param iconSize glyph size; defaults per [style] to the mockup sizes.
 */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconCircleButtonStyle = IconCircleButtonStyle.Filled,
    size: Dp = ReachDimens.minTouchTarget,
    iconSize: Dp = defaultGlyphSize(style),
    enabled: Boolean = true,
) {
    val colors = ReachTheme.colors
    val tint = if (style == IconCircleButtonStyle.Filled) colors.onAccent else colors.fg
    val decorated =
        modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .let {
                when (style) {
                    IconCircleButtonStyle.Filled -> it.background(colors.accent)
                    IconCircleButtonStyle.Outlined -> it.border(ReachComponentDimens.hairline, colors.line2, CircleShape)
                    IconCircleButtonStyle.Plain -> it
                }
            }
    Box(
        modifier = decorated.reachClickable(onClick = onClick, description = contentDescription, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private fun defaultGlyphSize(style: IconCircleButtonStyle): Dp =
    when (style) {
        IconCircleButtonStyle.Filled -> ReachComponentDimens.iconFilledGlyph
        IconCircleButtonStyle.Outlined -> ReachComponentDimens.iconOutlinedGlyph
        IconCircleButtonStyle.Plain -> ReachComponentDimens.iconPlainGlyph
    }

/** The outlined round back button of intent detail, labelled "Back" for TalkBack. */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.ds_back),
) {
    IconCircleButton(
        icon = ReachIcons.Back,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        style = IconCircleButtonStyle.Outlined,
    )
}

/**
 * A screen header row: [BackButton] + a mono [label] ("INTENT · 5 APPS"). The label wraps
 * at large font sizes instead of clipping.
 */
@Composable
fun BackHeader(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.backHeaderGap),
    ) {
        BackButton(onClick = onBack)
        MonoLabel(text = label, modifier = Modifier.weight(1f, fill = false))
    }
}

/** [BackHeader] with the intent-detail label "INTENT · N APPS" (localised, pluralised). */
@Composable
fun IntentDetailHeader(
    appCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHeader(
        label = pluralStringResource(R.plurals.ds_intent_header, appCount, appCount),
        onBack = onBack,
        modifier = modifier,
    )
}

@Preview(name = "Buttons")
@Composable
private fun ButtonsPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) {
        IntentDetailHeader(appCount = 5, onBack = {})
        Row(horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.nowTileSpacing)) {
            PillButton("Message Ayşe", onClick = {}, style = PillButtonStyle.Outlined, secondaryLabel = "WhatsApp")
            PillButton("Done", onClick = {})
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.badgeVerticalPadding),
        ) {
            listOf("Talk", "Listen", "Go", "Make", "Play", "Tools").forEach {
                IntentChip(label = it, selected = it == "Listen", onClick = {})
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(ReachIcons.Close, contentDescription = "Close", onClick = {}, style = IconCircleButtonStyle.Outlined)
            IconCircleButton(ReachIcons.Play, contentDescription = "Play", onClick = {})
            IconCircleButton(ReachIcons.SkipNext, contentDescription = "Next", onClick = {}, style = IconCircleButtonStyle.Plain)
        }
    }
}
