package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme

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
 * At 200% font size the texts wrap and the pill grows taller instead of clipping.
 *
 * @param showHint false hides "OR PULL DOWN" (e.g. in All Apps, where pulling down doesn't search).
 */
@Composable
fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
) {
    val colors = ReachTheme.colors
    val label = stringResource(R.string.ds_search_pill_label)
    val shape = RoundedCornerShape(ReachDimens.searchPillHeight / 2)
    Row(
        modifier =
            modifier
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
        if (showHint) MonoLabel(text = stringResource(R.string.ds_search_pill_hint))
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
