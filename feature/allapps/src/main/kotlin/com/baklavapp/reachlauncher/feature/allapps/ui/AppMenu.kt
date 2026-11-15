package com.baklavapp.reachlauncher.feature.allapps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.IntentChip
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.component.reachClickable
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.allapps.R

/*
 * The long-press menu of an app name (spec §6.7): App info, Move to intent…, Hide/Unhide,
 * Uninstall.
 *
 * Flutter analogy: a small `showModalBottomSheet` / `showMenu` — but drawn inside the same
 * screen (a `Stack` with a scrim and a card) instead of a new route, because Reach has no
 * navigator (D28). `BackHandler` ≈ `PopScope`: while the menu is open, Back closes it; Home's
 * own handler only runs when nothing deeper handles Back.
 */

private val MenuPadding = 20.dp
private val MenuGap = 6.dp
private val ItemPadding = 4.dp
private val ChipSpacing = 6.dp
private const val SCRIM_ALPHA = 0.6f

/**
 * The menu for [app], anchored at the bottom (in thumb reach) over a scrim. Tapping the
 * scrim or pressing Back dismisses it. "Move to intent…" swaps the items for the six
 * [IntentChip]s, the app's current intent selected.
 *
 * Accessibility: the card is a pane titled with the app's name (TalkBack announces it when
 * it opens); the scrim is not a control — Back and the "Close" item are the accessible ways
 * out. The screen behind hides its semantics while the menu is open.
 *
 * @param startInMove open directly on the intent chips (TalkBack's "Move to intent…" action).
 */
@Composable
internal fun AppMenu(
    app: CatalogApp,
    startInMove: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onMoveTo: (ReachIntent) -> Unit,
    onToggleHidden: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    var moving by remember(app.key, startInMove) { mutableStateOf(startInMove) }
    BackHandler(onBack = onDismiss)
    val shape = RoundedCornerShape(ReachDimens.sheetCorner)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(ReachTheme.dimens.gutter),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface, shape)
                    .border(ReachComponentDimens.hairline, colors.line2, shape)
                    // Swallow taps on the card so they don't reach the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = app.label }
                    // At 200 % font the menu can outgrow a short screen: scroll, don't clip.
                    .verticalScroll(rememberScrollState())
                    .padding(MenuPadding),
            verticalArrangement = Arrangement.spacedBy(MenuGap),
        ) {
            Text(
                text = app.label,
                style = ReachTheme.typography.title,
                color = colors.fg,
                modifier = Modifier.padding(horizontal = ItemPadding).semantics { heading() },
            )
            if (moving) {
                MonoLabel(text = stringResource(R.string.allapps_menu_move_label), modifier = Modifier.padding(horizontal = ItemPadding))
                // selectableGroup(): TalkBack announces "tab 2 of 6" for the chips inside.
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                    verticalArrangement = Arrangement.spacedBy(ChipSpacing),
                ) {
                    ReachIntent.entries.forEach { intent ->
                        IntentChip(
                            label = intentWord(intent),
                            selected = intent == app.intent,
                            onClick = { onMoveTo(intent) },
                        )
                    }
                }
            } else {
                MenuItem(stringResource(R.string.allapps_menu_app_info), onClick = onAppInfo)
                MenuItem(stringResource(R.string.allapps_menu_move)) { moving = true }
                MenuItem(
                    stringResource(if (app.isHidden) R.string.allapps_menu_unhide else R.string.allapps_menu_hide),
                    onClick = onToggleHidden,
                )
                MenuItem(stringResource(R.string.allapps_menu_uninstall), onClick = onUninstall)
            }
            MenuItem(stringResource(R.string.allapps_menu_close), muted = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReachComponentDimens.pillCorner))
                .reachClickable(onClick = onClick, description = text)
                .heightIn(min = ReachDimens.minTouchTarget)
                .padding(horizontal = ItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = ReachTheme.typography.button, color = if (muted) ReachTheme.colors.muted else ReachTheme.colors.fg)
    }
}
