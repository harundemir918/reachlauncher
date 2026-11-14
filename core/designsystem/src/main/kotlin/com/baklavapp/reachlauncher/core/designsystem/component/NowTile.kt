package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme

/*
 * NowTile — one of the three prediction tiles in Home's "NOW" row (spec §6.3).
 *
 * Flutter analogy: a `Card` + `InkWell` StatelessWidget with two `Text`s in a `Column`.
 * Give each tile `Modifier.weight(1f)` inside a `Row` for the mockup's three equal columns
 * (≈ `Expanded` in a Flutter `Row`).
 */

/**
 * A prediction tile: surface background, 18dp corners, hairline `line` outline, a mono
 * [reasonLabel] ("usually around now", "2 from Ayşe") above the [appLabel]. Tap launches.
 *
 * Text wraps (never clips) at large font sizes; the tile grows taller, so give the Now row
 * room to grow. TalkBack reads "WhatsApp, 2 from Ayşe".
 *
 * @param reasonLabel natural case; displayed upper-cased with the current locale.
 */
@Composable
fun NowTile(
    appLabel: String,
    reasonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachDimens.tileCorner)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(colors.surface)
                .border(ReachComponentDimens.hairline, colors.line, shape)
                .reachClickable(onClick = onClick, description = "$appLabel, $reasonLabel")
                .heightIn(min = ReachComponentDimens.nowTileMinHeight)
                .padding(ReachComponentDimens.nowTilePadding),
        verticalArrangement = Arrangement.spacedBy(ReachComponentDimens.nowTileGap),
    ) {
        MonoLabel(text = reasonLabel)
        Text(text = appLabel, style = ReachTheme.typography.appName, color = colors.fg)
    }
}

@Preview(name = "Now tiles", widthDp = 390)
@Composable
private fun NowTilePreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) {
        MonoLabel("now · learns your routine")
        Row(horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.nowTileSpacing)) {
            NowTile("Calendar", "standup 10:00", onClick = {}, modifier = Modifier.weight(1f))
            NowTile("WhatsApp", "2 from Ayşe", onClick = {}, modifier = Modifier.weight(1f))
            NowTile("Maps", "commute", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
