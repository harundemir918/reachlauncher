package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachColors
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.PaletteId

/*
 * Preview plumbing shared by the component files.
 *
 * Flutter analogy: there is no direct equivalent — think of `@Preview` as a widgetbook /
 * golden-test entry that Android Studio renders live without running the app. A
 * `PreviewParameterProvider` feeds each preview several values; here `true`/`false` gives
 * every component a dark and a light rendering from one function.
 */

/** Feeds `isDark = true, false` into a preview: one dark and one light rendering. */
internal class DarkLightProvider : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(true, false)
}

/** Lime palette in the requested theme on a bg-coloured, gutter-padded column. */
@Composable
internal fun PreviewSurface(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReachTheme(colors = ReachColors.of(PaletteId.LIME, isDark = isDark)) {
        Column(
            modifier = Modifier.background(ReachTheme.colors.bg).padding(ReachDimens.gutter),
            verticalArrangement = Arrangement.spacedBy(ReachComponentDimens.nowTileSpacing),
            content = content,
        )
    }
}
