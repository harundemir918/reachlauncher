package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.AppActionsMenu
import com.baklavapp.reachlauncher.core.designsystem.component.AppMenuActions
import com.baklavapp.reachlauncher.core.designsystem.component.AppRow
import com.baklavapp.reachlauncher.core.designsystem.component.IntentChip
import com.baklavapp.reachlauncher.core.designsystem.component.IntentDetailHeader
import com.baklavapp.reachlauncher.core.designsystem.component.appMenuAccessibilityActions
import com.baklavapp.reachlauncher.core.designsystem.component.appMenuOpenLabel
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.intentWord
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.home.AppItem
import com.baklavapp.reachlauncher.feature.home.IntentDetailUi
import com.baklavapp.reachlauncher.feature.home.R

/*
 * Intent detail (spec §6.4): back header, accent dot + intent word, the favourites row
 * (docs/DECISIONS.md D76), Listen's Now Playing card, the app list and the intent chips.
 *
 * Flutter analogy: same layout trick as Home — a `SingleChildScrollView` with a min-height
 * child and `spaceBetween`, so the app list sits at the bottom (in thumb reach) when it fits
 * and scrolls when it doesn't. The intent chips are pinned below the scroll area
 * (≈ a `Column` of `Expanded(child: SingleChildScrollView(…))` and the chips). Intents hold tens of apps, not thousands, so a
 * plain `Column` is fine here; a `LazyColumn` (≈ `ListView.builder`) would only pay off
 * for long lists like All Apps.
 */

private val TopPadding = 12.dp
private val BottomPadding = 20.dp
private val TitleTopGap = 16.dp
private val TitleDotGap = 14.dp
private val AccentDotSize = 14.dp
private val SectionTopGap = 20.dp
private val ChipsTopGap = 16.dp
private val ChipSpacing = 6.dp
private val EmptyPadding = 14.dp

/**
 * What an app row's long-press menu can do (spec §6.1/§6.4: "Move to…", "Hide", plus App
 * info and Uninstall, as in All Apps). Passing none (null) offers no menu at all, so
 * TalkBack never announces an action that does nothing.
 *
 * Flutter analogy: a bundle of callbacks you'd pass down instead of a `BuildContext` lookup.
 */
@Immutable
internal class AppMenuCallbacks(
    val onAppInfo: (CatalogApp) -> Unit,
    val onMoveTo: (CatalogApp, ReachIntent) -> Unit,
    val onHide: (CatalogApp) -> Unit,
    val onUninstall: (CatalogApp) -> Unit,
)

/** The menu currently open in intent detail: for [app], optionally straight on the intent chips. */
private data class DetailMenu(
    val app: CatalogApp,
    val move: Boolean,
)

/**
 * Stateless intent-detail content for [detail] (the open menu is local UI state, like a
 * popup's "is open" flag in a StatefulWidget).
 *
 * @param media the active media session; shown as the Now Playing card on Listen only.
 * @param appMenu long-press menu actions for the app rows; null = no menu.
 * @param favoriteCallbacks edits this intent's favourites; null = the row is read-only.
 * @param pickableApps the apps the favourites editor offers.
 */
@Composable
internal fun IntentDetailScreen(
    detail: IntentDetailUi,
    onBack: () -> Unit,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onIntentSelected: (ReachIntent) -> Unit,
    modifier: Modifier = Modifier,
    media: MediaState? = null,
    mediaCallbacks: MediaCallbacks = MediaCallbacks.None,
    appMenu: AppMenuCallbacks? = null,
    favoriteCallbacks: FavoriteCallbacks? = null,
    pickableApps: List<CatalogApp> = emptyList(),
) {
    // Keyed on the intent: switching intents via the chips closes a menu left open.
    var menu by remember(detail.intent) { mutableStateOf<DetailMenu?>(null) }
    var editingFavorites by remember(detail.intent) { mutableStateOf(false) }
    val overlayOpen = menu != null || editingFavorites
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // While the menu or editor is open, the screen behind is not reachable by TalkBack.
                    .then(if (overlayOpen) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, top = TopPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        IntentDetailHeader(appCount = detail.apps.size, onBack = onBack)
                        DetailTitle(detail.intent, Modifier.padding(top = TitleTopGap))
                        FavoritesRow(
                            favorites = detail.favorites,
                            onLaunch = onLaunch,
                            onEdit = if (favoriteCallbacks != null) ({ editingFavorites = true }) else null,
                            onRemove = favoriteCallbacks?.onRemove,
                            modifier = Modifier.padding(top = SectionTopGap),
                        )
                        if (detail.intent == ReachIntent.LISTEN && media != null) {
                            NowPlayingCard(media, mediaCallbacks, Modifier.padding(top = SectionTopGap))
                        }
                    }
                    AppList(
                        detail.apps,
                        onLaunch,
                        appMenu,
                        onOpenMenu = { app, move -> menu = DetailMenu(app, move) },
                        modifier = Modifier.padding(top = SectionTopGap),
                    )
                }
            }
            // Pinned below the scrolling list, in thumb reach however many apps the intent
            // holds (with 60 apps they used to sit at the very end of the scroll).
            IntentChips(
                detail.intent,
                onIntentSelected,
                Modifier.padding(
                    start = ReachTheme.dimens.gutter,
                    end = ReachTheme.dimens.gutter,
                    top = ChipsTopGap,
                    bottom = BottomPadding,
                ),
            )
        }
        val open = menu
        if (open != null && appMenu != null) {
            DetailAppMenu(open, appMenu, onClose = { menu = null })
        }
        if (editingFavorites && favoriteCallbacks != null) {
            FavoritesEditor(
                intent = detail.intent,
                favorites = detail.favorites,
                pickable = pickableApps,
                callbacks = favoriteCallbacks,
                onClose = { editingFavorites = false },
            )
        }
    }
}

@Composable
private fun DetailAppMenu(
    open: DetailMenu,
    callbacks: AppMenuCallbacks,
    onClose: () -> Unit,
) {
    val app = open.app
    AppActionsMenu(
        title = app.label,
        currentIntent = app.intent,
        // Intent detail lists visible apps only, so the item always reads "Hide".
        isHidden = app.isHidden,
        startInMove = open.move,
        intentLabel = { intentWord(it) },
        actions =
            AppMenuActions(
                onDismiss = onClose,
                onAppInfo = {
                    onClose()
                    callbacks.onAppInfo(app)
                },
                onMoveTo = { intent ->
                    onClose()
                    callbacks.onMoveTo(app, intent)
                },
                onToggleHidden = {
                    onClose()
                    callbacks.onHide(app)
                },
                onUninstall = {
                    onClose()
                    callbacks.onUninstall(app)
                },
            ),
    )
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

/**
 * The app rows. With [appMenu], a long-press opens the menu, and TalkBack gets both the
 * long-press action ("App options") and every menu item as a direct custom action (spec
 * §6.10: a tap alternative for each long-press feature).
 */
@Composable
private fun AppList(
    apps: List<AppItem>,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    appMenu: AppMenuCallbacks?,
    onOpenMenu: (CatalogApp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Text(
            text = stringResource(R.string.home_detail_empty),
            style = ReachTheme.typography.body,
            color = ReachTheme.colors.muted,
            modifier = modifier.padding(vertical = EmptyPadding),
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        apps.forEach { item ->
            key(item.app.key) {
                val app = item.app
                val origin = rememberLaunchOrigin()
                val actions =
                    if (appMenu != null) {
                        appMenuAccessibilityActions(
                            isHidden = app.isHidden,
                            onAppInfo = { appMenu.onAppInfo(app) },
                            onOpenMove = { onOpenMenu(app, true) },
                            onToggleHidden = { appMenu.onHide(app) },
                            onUninstall = { appMenu.onUninstall(app) },
                        )
                    } else {
                        emptyList()
                    }
                AppRow(
                    label = app.label,
                    onClick = { onLaunch(app, origin.toLaunchOrigin()) },
                    badgeCount = item.unreadCount,
                    profile = app.profile,
                    dimmed = app.isProfilePaused,
                    onLongClick = if (appMenu != null) ({ onOpenMenu(app, false) }) else null,
                    onLongClickLabel = if (appMenu != null) appMenuOpenLabel() else null,
                    accessibilityActions = actions,
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
    // On a 360dp phone the six chips are wider than the screen and the row scrolls; scroll the
    // current one into view, or "Tools" (the last) opens out of sight.
    val selectedChip = remember { BringIntoViewRequester() }
    LaunchedEffect(current) { selectedChip.bringIntoView() }
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
                modifier = if (intent == current) Modifier.bringIntoViewRequester(selectedChip) else Modifier,
            )
        }
    }
}
