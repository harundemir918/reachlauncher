package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.AppRow
import com.baklavapp.reachlauncher.core.designsystem.component.IntentChip
import com.baklavapp.reachlauncher.core.designsystem.component.IntentDetailHeader
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.PillButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.home.AppItem
import com.baklavapp.reachlauncher.feature.home.IntentDetailUi
import com.baklavapp.reachlauncher.feature.home.R
import com.baklavapp.reachlauncher.feature.home.ShortcutItem

/*
 * Intent detail (spec §6.4): back header, accent dot + intent word, app shortcuts, Listen's
 * Now Playing card, the app list and the intent chips.
 *
 * Flutter analogy: same layout trick as Home — a `SingleChildScrollView` with a min-height
 * child and `spaceBetween`, so the app list and chips sit at the bottom (in thumb reach)
 * when they fit and scroll when they don't. Intents hold tens of apps, not thousands, so a
 * plain `Column` is fine here; a `LazyColumn` (≈ `ListView.builder`) would only pay off
 * for long lists like All Apps.
 */

private val TopPadding = 12.dp
private val BottomPadding = 20.dp
private val TitleTopGap = 16.dp
private val TitleDotGap = 14.dp
private val AccentDotSize = 14.dp
private val SectionTopGap = 20.dp
private val SectionLabelGap = 10.dp
private val ShortcutSpacing = 8.dp
private val ChipsTopGap = 16.dp
private val ChipSpacing = 6.dp
private val EmptyPadding = 14.dp

/**
 * Stateless intent-detail content for [detail].
 *
 * @param media the active media session; shown as the Now Playing card on Listen only.
 */
@Composable
internal fun IntentDetailScreen(
    detail: IntentDetailUi,
    onBack: () -> Unit,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onLaunchShortcut: (ShortcutItem, LaunchOrigin?) -> Unit,
    onIntentSelected: (ReachIntent) -> Unit,
    modifier: Modifier = Modifier,
    media: MediaState? = null,
    mediaCallbacks: MediaCallbacks = MediaCallbacks.None,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, top = TopPadding, bottom = BottomPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                IntentDetailHeader(appCount = detail.apps.size, onBack = onBack)
                DetailTitle(detail.intent, Modifier.padding(top = TitleTopGap))
                if (detail.shortcuts.isNotEmpty()) {
                    Shortcuts(detail.shortcuts, onLaunchShortcut, Modifier.padding(top = SectionTopGap))
                }
                if (detail.intent == ReachIntent.LISTEN && media != null) {
                    NowPlayingCard(media, mediaCallbacks, Modifier.padding(top = SectionTopGap))
                }
            }
            Column(modifier = Modifier.padding(top = SectionTopGap)) {
                AppList(detail.apps, onLaunch)
                IntentChips(detail.intent, onIntentSelected, Modifier.padding(top = ChipsTopGap))
            }
        }
    }
}

@Composable
private fun DetailTitle(
    intent: ReachIntent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TitleDotGap),
    ) {
        Box(Modifier.size(AccentDotSize).background(ReachTheme.colors.accentInk, CircleShape))
        Text(
            text = intentWord(intent),
            style = ReachTheme.typography.intentTitle,
            color = ReachTheme.colors.fg,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Shortcuts(
    shortcuts: List<ShortcutItem>,
    onLaunchShortcut: (ShortcutItem, LaunchOrigin?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SectionLabelGap)) {
        MonoLabel(text = stringResource(R.string.home_detail_shortcuts), modifier = Modifier.semantics { heading() })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShortcutSpacing),
            verticalArrangement = Arrangement.spacedBy(ShortcutSpacing),
        ) {
            shortcuts.forEach { item ->
                key(item.shortcut.app, item.shortcut.id) {
                    val origin = rememberLaunchOrigin()
                    PillButton(
                        text = item.shortcut.label,
                        onClick = { onLaunchShortcut(item, origin.toLaunchOrigin()) },
                        style = PillButtonStyle.Outlined,
                        secondaryLabel = item.shortcut.appLabel,
                        modifier = Modifier.trackLaunchOrigin(origin),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppItem>,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
) {
    if (apps.isEmpty()) {
        Text(
            text = stringResource(R.string.home_detail_empty),
            style = ReachTheme.typography.body,
            color = ReachTheme.colors.muted,
            modifier = Modifier.padding(vertical = EmptyPadding),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        apps.forEach { item ->
            key(item.app.key) {
                val origin = rememberLaunchOrigin()
                AppRow(
                    label = item.app.label,
                    onClick = { onLaunch(item.app, origin.toLaunchOrigin()) },
                    badgeCount = item.unreadCount,
                    profile = item.app.profile,
                    dimmed = item.app.isProfilePaused,
                    // TODO(M4): long-press menu "Move to… / Hide". No onLongClick until then,
                    //  so TalkBack doesn't offer an action that does nothing.
                    modifier = Modifier.trackLaunchOrigin(origin),
                )
            }
        }
    }
}

@Composable
private fun IntentChips(
    current: ReachIntent,
    onIntentSelected: (ReachIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // selectableGroup(): TalkBack announces "tab 2 of 6" for the chips inside.
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        ReachIntent.entries.forEach { intent ->
            IntentChip(
                label = intentWord(intent),
                selected = intent == current,
                onClick = { if (intent != current) onIntentSelected(intent) },
            )
        }
    }
}
