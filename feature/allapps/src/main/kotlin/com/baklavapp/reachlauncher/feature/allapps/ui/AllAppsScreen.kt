package com.baklavapp.reachlauncher.feature.allapps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.AppActionsMenu
import com.baklavapp.reachlauncher.core.designsystem.component.AppMenuActions
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.component.SearchPill
import com.baklavapp.reachlauncher.core.designsystem.component.appMenuAccessibilityActions
import com.baklavapp.reachlauncher.core.designsystem.component.appMenuOpenLabel
import com.baklavapp.reachlauncher.core.designsystem.component.reachClickable
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachColors
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.allapps.AllAppsUiState
import com.baklavapp.reachlauncher.feature.allapps.LetterGroup
import com.baklavapp.reachlauncher.feature.allapps.R
import com.baklavapp.reachlauncher.feature.allapps.RailLetter
import kotlinx.coroutines.launch
import com.baklavapp.reachlauncher.core.designsystem.R as DsR

/*
 * All Apps (spec §6.7): "Everything" + count, the word wall, the letter rail and the
 * "Type to launch" pill.
 *
 * Flutter analogy:
 * - `LazyColumn` ≈ `ListView.builder`: only the visible groups are composed. Each group is
 *   one item (its letter label + names), so "jump to S" is "scroll to item n".
 * - `FlowRow` ≈ `Wrap`: names flow left to right and wrap onto new lines, which is also why
 *   200 % font needs no special layout — the wall simply gets more lines.
 * - `LazyListState.scrollToItem` ≈ `ScrollController.jumpTo` (and `animateScrollToItem` ≈
 *   `animateTo` / `Scrollable.ensureVisible`), driven by the rail.
 * - The menu overlay ≈ a `Stack` child shown conditionally, like an in-place modal sheet.
 */

private val TopPadding = 12.dp
private val BottomPadding = 16.dp
private val HeaderCountGap = 10.dp
private val SubtitleGap = 8.dp
private val HeaderBottomGap = 4.dp
private val GroupSpacing = 18.dp
private val LabelGap = 6.dp
private val NameSpacing = 10.dp
private val NameRowGap = 2.dp
private val NamePadding = 4.dp
private val NameCorner = 8.dp
private val RailContentGap = 8.dp
private val RailEdgeGap = 4.dp
private val RailVerticalPadding = 16.dp
private val PillVerticalPadding = 12.dp

/** Lazy-list index of the first letter group (index 0 is the heading). */
private const val FIRST_GROUP_ITEM = 1

/**
 * One "scroll to this group" request from the rail. Deliberately not a data class: two
 * requests for the same group are unequal, so a second scrub to S scrolls again even if the
 * user scrolled away in between.
 */
private class JumpRequest(
    val groupIndex: Int,
)

/** A pending long-press menu: for which app, and whether it opens on the intent chips. */
@Immutable
private data class MenuRequest(
    val app: CatalogApp,
    val move: Boolean,
)

/**
 * The stateless All Apps screen. Everything it shows comes from [state]; everything the
 * user does goes out through the callbacks — so tests and previews drive it without a
 * ViewModel. UI-only state (rail scrubbing, the open menu, scroll position) lives here.
 *
 * Edge-to-edge: pads itself for `safeDrawing` (status bar, cutout, navigation bar), and
 * keeps the letter rail out of the system back-gesture zone (`WindowInsets.systemGestures`).
 *
 * @param onLaunch tap on a name; the second argument is its window bounds for the launch animation.
 * @param onMoveToIntent the menu's intent chips; the user's choice beats auto-assignment.
 * @param onSetHidden the menu's Hide (true) / Unhide (false).
 * @param onOpenSearch the "Type to launch" pill.
 * @param listState the word wall's scroll state; hoisted so tests can read the position.
 */
@Composable
fun AllAppsScreen(
    state: AllAppsUiState,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onAppInfo: (CatalogApp) -> Unit,
    onMoveToIntent: (CatalogApp, ReachIntent) -> Unit,
    onSetHidden: (CatalogApp, Boolean) -> Unit,
    onUninstall: (CatalogApp) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<RailLetter?>(null) }
    var jump by remember { mutableStateOf<JumpRequest?>(null) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }

    // Restarted on every new request, which cancels a still-running scroll to the previous
    // letter (≈ calling animateTo again on a ScrollController). Kept apart from activeLetter
    // so lifting the finger (activeLetter → null) doesn't cancel the last scroll.
    LaunchedEffect(jump) {
        val item = FIRST_GROUP_ITEM + (jump?.groupIndex ?: return@LaunchedEffect)
        if (reducedMotion) listState.scrollToItem(item) else listState.animateScrollToItem(item)
    }

    val nameCallbacks =
        NameCallbacks(
            onLaunch = onLaunch,
            onOpenMenu = { app, move -> menu = MenuRequest(app, move) },
            onAppInfo = onAppInfo,
            onSetHidden = onSetHidden,
            onUninstall = onUninstall,
        )
    val railOnLeft = state.thumbSide == ThumbSide.LEFT
    val gestureInsets = WindowInsets.systemGestures.exclude(WindowInsets.safeDrawing).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val railEdgeInset =
        (if (railOnLeft) gestureInsets.calculateLeftPadding(layoutDirection) else gestureInsets.calculateRightPadding(layoutDirection)) +
            RailEdgeGap
    val railSidePadding = railEdgeInset + RailWidth + RailContentGap
    val gutter = ReachTheme.dimens.gutter

    // backdrop: bg normally; transparent in wallpaper mode, where Home's root paints the scrim.
    Box(modifier = modifier.fillMaxSize().background(ReachTheme.colors.backdrop)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // While the menu is open, the wall behind it is not reachable by TalkBack.
                    .then(if (menu != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues.Absolute(
                            left = if (railOnLeft) railSidePadding else gutter,
                            right = if (railOnLeft) gutter else railSidePadding,
                            top = TopPadding,
                            bottom = BottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                ) {
                    item(key = "header") { Header(state) }
                    items(state.groups, key = { "group:${it.letter}" }) { group ->
                        WordWallGroup(
                            label = group.letter,
                            apps = group.apps,
                            focus = groupFocus(activeLetter, group),
                            hiddenGroup = false,
                            callbacks = nameCallbacks,
                        )
                    }
                    if (state.hidden.isNotEmpty()) {
                        item(key = "hidden") {
                            WordWallGroup(
                                label = stringResource(R.string.allapps_hidden_group),
                                apps = state.hidden,
                                focus = GroupFocus.Dimmed,
                                hiddenGroup = true,
                                callbacks = nameCallbacks,
                            )
                        }
                    }
                }
                LetterRail(
                    letters = state.rail,
                    thumbSide = state.thumbSide,
                    activeLetter = activeLetter,
                    onLetterChange = { letter ->
                        activeLetter = letter
                        letter?.groupIndex?.let { jump = JumpRequest(it) }
                    },
                    onJump = { letter ->
                        val group = letter.groupIndex ?: return@LetterRail
                        scope.launch { listState.scrollToItem(FIRST_GROUP_ITEM + group) }
                    },
                    modifier =
                        Modifier
                            .align(if (railOnLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                            .absolutePadding(
                                left = if (railOnLeft) railEdgeInset else 0.dp,
                                right = if (railOnLeft) 0.dp else railEdgeInset,
                                top = RailVerticalPadding,
                                bottom = RailVerticalPadding,
                            ),
                )
            }
            SearchPill(
                onClick = onOpenSearch,
                showHint = false,
                modifier = Modifier.padding(horizontal = gutter, vertical = PillVerticalPadding),
            )
        }
        menu?.let { request ->
            val app = request.app
            val close = { menu = null }
            AppActionsMenu(
                title = app.label,
                currentIntent = app.intent,
                isHidden = app.isHidden,
                startInMove = request.move,
                intentLabel = { intentWord(it) },
                actions =
                    AppMenuActions(
                        onDismiss = close,
                        onAppInfo = {
                            close()
                            onAppInfo(app)
                        },
                        onMoveTo = { intent ->
                            close()
                            if (intent != app.intent) onMoveToIntent(app, intent)
                        },
                        onToggleHidden = {
                            close()
                            onSetHidden(app, !app.isHidden)
                        },
                        onUninstall = {
                            close()
                            onUninstall(app)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun Header(state: AllAppsUiState) {
    Column(modifier = Modifier.padding(bottom = HeaderBottomGap)) {
        // FlowRow: at 200 % font the count wraps under the heading instead of squeezing it.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(HeaderCountGap)) {
            Text(
                text = stringResource(R.string.allapps_title),
                style = ReachTheme.typography.intentWord,
                color = ReachTheme.colors.fg,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            if (state.loaded) {
                MonoLabel(
                    text = pluralStringResource(R.plurals.allapps_count, state.totalCount, state.totalCount),
                    style = ReachTheme.typography.count,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Text(
            text = stringResource(R.string.allapps_subtitle),
            style = ReachTheme.typography.caption,
            color = ReachTheme.colors.muted,
            modifier = Modifier.padding(top = SubtitleGap),
        )
    }
}

/** How a group is drawn relative to the rail: normal, the scrubbed one, or dimmed. */
private enum class GroupFocus { Normal, Active, Dimmed }

private fun groupFocus(
    active: RailLetter?,
    group: LetterGroup,
): GroupFocus =
    when {
        active == null -> GroupFocus.Normal
        active.letter == group.letter -> GroupFocus.Active
        else -> GroupFocus.Dimmed
    }

/** What a name can do; bundled so the group composables don't take six lambdas each. */
private class NameCallbacks(
    val onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    val onOpenMenu: (CatalogApp, Boolean) -> Unit,
    val onAppInfo: (CatalogApp) -> Unit,
    val onSetHidden: (CatalogApp, Boolean) -> Unit,
    val onUninstall: (CatalogApp) -> Unit,
)

@Composable
private fun WordWallGroup(
    label: String,
    apps: List<CatalogApp>,
    focus: GroupFocus,
    hiddenGroup: Boolean,
    callbacks: NameCallbacks,
) {
    val colors = ReachTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        MonoLabel(
            text = label,
            color = if (focus == GroupFocus.Active) colors.accentInk else colors.muted,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NameSpacing),
            verticalArrangement = Arrangement.spacedBy(NameRowGap),
        ) {
            apps.forEach { app ->
                key(app.key) {
                    val color =
                        when {
                            focus == GroupFocus.Dimmed -> colors.groupDim
                            app.isProfilePaused -> colors.muted
                            else -> colors.fg
                        }
                    WordWallName(app, color, hiddenGroup, callbacks)
                }
            }
        }
    }
}

/**
 * One name on the wall: a ≥ 48dp-tall button. TalkBack reads e.g. "Slack, work profile,
 * paused" and offers the menu both as the long-press action ("App options") and as direct
 * custom actions, so nobody has to open the menu to hide or uninstall (spec §6.10).
 */
@Composable
private fun WordWallName(
    app: CatalogApp,
    color: Color,
    hiddenGroup: Boolean,
    callbacks: NameCallbacks,
) {
    val origin = rememberLaunchOrigin()
    val tag =
        when (app.profile) {
            ProfileType.PERSONAL -> null
            ProfileType.WORK -> stringResource(DsR.string.ds_profile_tag_work)
            ProfileType.PRIVATE -> stringResource(DsR.string.ds_profile_tag_private)
        }
    val description =
        listOfNotNull(
            app.label,
            when (app.profile) {
                ProfileType.PERSONAL -> null
                ProfileType.WORK -> stringResource(DsR.string.ds_profile_work)
                ProfileType.PRIVATE -> stringResource(DsR.string.ds_profile_private)
            },
            if (app.isProfilePaused) stringResource(DsR.string.ds_profile_paused) else null,
            if (hiddenGroup) stringResource(R.string.allapps_hidden) else null,
        ).joinToString(", ")
    val actions =
        appMenuAccessibilityActions(
            isHidden = app.isHidden,
            onAppInfo = { callbacks.onAppInfo(app) },
            onOpenMove = { callbacks.onOpenMenu(app, true) },
            onToggleHidden = { callbacks.onSetHidden(app, !app.isHidden) },
            onUninstall = { callbacks.onUninstall(app) },
        )
    Row(
        modifier =
            Modifier
                .trackLaunchOrigin(origin)
                .clip(RoundedCornerShape(NameCorner))
                .reachClickable(
                    onClick = { callbacks.onLaunch(app, origin.toLaunchOrigin()) },
                    description = description,
                    onLongClick = { callbacks.onOpenMenu(app, false) },
                    onLongClickLabel = appMenuOpenLabel(),
                    accessibilityActions = actions,
                ).heightIn(min = ReachDimens.minTouchTarget)
                .padding(horizontal = NamePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.profileTagGap),
    ) {
        Text(text = app.label, style = ReachTheme.typography.wordWall, color = color, modifier = Modifier.weight(1f, fill = false))
        if (tag != null) MonoLabel(text = tag, style = ReachTheme.typography.labelSmall, maxLines = 1)
    }
}

@Preview(name = "All apps", widthDp = 390, heightDp = 844)
@Composable
private fun AllAppsScreenPreview() {
    fun app(
        label: String,
        profile: ProfileType = ProfileType.PERSONAL,
        hidden: Boolean = false,
    ) = CatalogApp(AppKey("p.$label", "p.$label.Main", 0), label, profile, ReachIntent.TOOLS, false, hidden, 0)
    val groups =
        listOf(
            LetterGroup("M", listOf(app("Maps"), app("Messages"))),
            LetterGroup("S", listOf(app("Settings"), app("Signal"), app("Slack", ProfileType.WORK), app("Spotify"))),
            LetterGroup("W", listOf(app("Wallet"), app("WhatsApp"))),
        )
    val letters = ('A'..'Z').map { c -> RailLetter(c.toString(), groups.indexOfFirst { it.letter == c.toString() }.takeIf { it >= 0 }) }
    ReachTheme(colors = ReachColors.of(PaletteId.LIME, isDark = true)) {
        AllAppsScreen(
            state =
                AllAppsUiState(
                    loaded = true,
                    totalCount = 8,
                    groups = groups,
                    hidden = listOf(app("Chess", hidden = true)),
                    rail = letters + RailLetter("#", null),
                    thumbSide = ThumbSide.RIGHT,
                ),
            onLaunch = { _, _ -> },
            onAppInfo = {},
            onMoveToIntent = { _, _ -> },
            onSetHidden = { _, _ -> },
            onUninstall = {},
            onOpenSearch = {},
        )
    }
}
