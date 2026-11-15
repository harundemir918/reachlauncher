package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.BackHeader
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.feature.home.R

/*
 * Temporary Appearance sheet until `:feature:appearance` lands in M4 (Search and All Apps
 * were replaced by their real screens in M2). Same shape the real sheet will need (a
 * "close" callback), so swapping it in is a one-line change in `HomeRoute`.
 *
 * Flutter analogy: the `Placeholder()` widget, dressed in Reach's theme.
 */

private val SheetPadding = 24.dp
private val SheetGap = 18.dp

/**
 * Appearance placeholder: a bottom sheet over Home (real sheet: `:feature:appearance`, M4).
 * Tapping the dimmed area above it closes it, like a modal sheet.
 */
@Composable
internal fun AppearancePlaceholderSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(topStart = ReachTheme.dimens.sheetCorner, topEnd = ReachTheme.dimens.sheetCorner)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = SCRIM_ALPHA))
                // Plain tap detector, not clickable: the scrim isn't a control for TalkBack
                // (the sheet's Done button and system Back are the accessible ways out).
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(ReachComponentDimens.hairline, colors.line2, shape)
                    .background(colors.bg, shape)
                    // Swallow taps on the sheet itself so they don't reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SheetGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_appearance_title),
                    style = ReachTheme.typography.sheetTitle,
                    color = colors.fg,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PillButton(text = stringResource(R.string.home_done), onClick = onClose)
            }
            Text(text = stringResource(R.string.home_appearance_placeholder), style = ReachTheme.typography.body, color = colors.muted)
        }
    }
}

private const val SCRIM_ALPHA = 0.6f
