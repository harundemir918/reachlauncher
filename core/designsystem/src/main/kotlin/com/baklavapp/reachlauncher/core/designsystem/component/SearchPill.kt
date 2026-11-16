package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.toLabelCase

/*
 * SearchPill — the "Type to launch" pill at the bottom of Home (spec §6.3).
 *
 * Flutter analogy: an `OutlinedButton.icon` shaped as a `StadiumBorder`. It doesn't take
 * text input itself; tapping opens the Search screen (as does pulling down on Home).
 */

/**
 * The outlined 56dp "Type to launch" pill with a search icon and the mono hint
 * "OR PULL DOWN". It fills the available width.
 *
 * TalkBack: "Type to launch, button, double-tap to search apps, contacts and actions".
 * The hint is shown only when label and hint fit side by side on one line: at 200% font
 * "Type to launch" was squeezed to one letter per line, and in Spanish "Escribe para
 * abrir" wrapped beside "O DESLIZA ABAJO" on a 360dp phone. Otherwise the label has the
 * whole pill (pulling down still works; it's only the hint that goes).
 *
 * @param showHint false hides "OR PULL DOWN" (e.g. in All Apps, where pulling down doesn't search).
 */
@Composable
fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
) {
    val label = stringResource(R.string.ds_search_pill_label)
    val shape = RoundedCornerShape(ReachDimens.searchPillHeight / 2)
    val hint = stringResource(R.string.ds_search_pill_hint)
    val labelStyle = ReachTheme.typography.button
    val hintStyle = ReachTheme.typography.label
    val measurer = rememberTextMeasurer()
    val locale = currentLocale()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fitsHint =
            showHint &&
                with(density) {
                    val room =
                        maxWidth.toPx() -
                            2 * ReachComponentDimens.searchPillHorizontalPadding.toPx() -
                            ReachComponentDimens.searchPillIconSize.toPx() -
                            2 * ReachComponentDimens.searchPillGap.toPx()
                    val labelWidth = measurer.measure(label, labelStyle, maxLines = 1).size.width
                    val hintWidth = measurer.measure(hint.toLabelCase(locale), hintStyle, maxLines = 1).size.width
                    labelWidth + hintWidth <= room
                }
        SearchPillRow(label, if (fitsHint) hint else null, shape, onClick)
    }
}

@Composable
private fun SearchPillRow(
    label: String,
    hint: String?,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val colors = ReachTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(ReachComponentDimens.hairline, colors.line2, shape)
                .reachClickable(
                    onClick = onClick,
                    description = label,
                    onClickLabel = stringResource(R.string.ds_search_pill_action),
                ).heightIn(min = ReachDimens.searchPillHeight)
                .padding(horizontal = ReachComponentDimens.searchPillHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.searchPillGap),
    ) {
        Icon(
            imageVector = ReachIcons.Search,
            contentDescription = null,
            tint = colors.fg,
            modifier = Modifier.size(ReachComponentDimens.searchPillIconSize),
        )
        Text(text = label, style = ReachTheme.typography.button, color = colors.fg, modifier = Modifier.weight(1f))
        hint?.let { MonoLabel(text = it, maxLines = 1) }
    }
}

@Preview(name = "Search pill", widthDp = 390)
@Composable
private fun SearchPillPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { SearchPill(onClick = {}) }
}

@Preview(name = "Search pill · 200% font", widthDp = 390, fontScale = 2f)
@Composable
private fun SearchPillLargeFontPreview() {
    PreviewSurface(isDark = true) { SearchPill(onClick = {}) }
}
