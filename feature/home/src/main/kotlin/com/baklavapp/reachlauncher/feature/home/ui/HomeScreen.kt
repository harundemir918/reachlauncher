package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.InlineMediaControl
import com.baklavapp.reachlauncher.core.designsystem.component.IntentRowTrailing
import com.baklavapp.reachlauncher.core.designsystem.component.IntentWordRow
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.NowTile
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.component.SearchPill
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.intentWord
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.home.HomeGesturesUi
import com.baklavapp.reachlauncher.feature.home.HomeSystemUi
import com.baklavapp.reachlauncher.feature.home.IntentSummary
import com.baklavapp.reachlauncher.feature.home.NowItem
import com.baklavapp.reachlauncher.feature.home.R

/*
 * The Home surface (spec §6.3): date, clock, context line (next calendar event), the one
 * onboarding card if any, NOW row, flexible space, six intent words in the thumb zone (Listen
 * with its inline player while something plays), the "Type to launch" pill.
 *
 * Flutter analogy: a stateless widget built from the BLoC state — every value comes in as a
 * parameter and every interaction goes out as a callback (≈ `context.read<HomeBloc>().add`).
 * The layout is a `SingleChildScrollView` whose child has `minHeight` = viewport height
 * (≈ `ConstrainedBox(constraints: BoxConstraints(minHeight: …))`) and
 * `MainAxisAlignment.spaceBetween`: on a normal screen the words sit at the bottom near the
 * thumb with a flexible gap above; at 200% font everything simply scrolls instead of clipping.
 */

private val TopPadding = 12.dp
private val BottomPadding = 20.dp
private val DateClockGap = 6.dp
private val NowTopGap = 28.dp
private val CardTopGap = 20.dp
private val NowLabelGap = 10.dp
private val PillTopGap = 18.dp
private const val NOW_ROW_SLOTS = 3

/**
 * Stateless Home content.
 *
 * @param catalogLoaded false until the catalog's first emission: the intent rows then show
 *   no count (rather than a wrong "0") and the NOW block stays hidden.
 * @param onLaunch launches an app; the [LaunchOrigin] is the tapped tile's window bounds.
 * @param scrollToTopRequests incremented each time Home is pressed while already on Home;
 *   every new value scrolls Home back to the top.
 * @param wordModifier per-intent modifier for the intent word's tappable area — the hook for
 *   M2's press-slide-release gesture (passed through to `IntentWordRow(wordModifier = …)`).
 * @param heldIntent the intent whose press-slide-release fan is open, or null. While set, that
 *   word turns `accentInk`, the other words `dim` (D18), counts are hidden, and the search
 *   pill is replaced by the hint "RELEASE TO OPEN · SLIDE OFF TO CANCEL".
 * @param system media, next event and onboarding card (M3); the default shows none of them.
 * @param mediaCallbacks the Listen row's play/pause.
 * @param onboardingCallbacks the onboarding card's buttons.
 * @param gestureCallbacks double-tap to lock / two-finger shade, and their TalkBack actions
 *   (enabled per [HomeSystemUi.gestures]).
 */
@Composable
internal fun HomeScreen(
    intents: List<IntentSummary>,
    now: List<NowItem>,
    catalogLoaded: Boolean,
    thumbSide: ThumbSide,
    onIntentSelected: (ReachIntent) -> Unit,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onSearch: () -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequests: Int = 0,
    wordModifier: (ReachIntent) -> Modifier = { Modifier },
    heldIntent: ReachIntent? = null,
    system: HomeSystemUi = HomeSystemUi(),
    mediaCallbacks: MediaCallbacks = MediaCallbacks.None,
    onboardingCallbacks: OnboardingCallbacks = OnboardingCallbacks.None,
    gestureCallbacks: ScreenGestureCallbacks = ScreenGestureCallbacks.None,
) {
    val swipe = rememberVerticalSwipeConnection(onSwipeUp = onAllApps, onSwipeDown = onSearch)
    val onLock = rememberUpdatedState(gestureCallbacks.onLock)
    val onOpenNotifications = rememberUpdatedState(gestureCallbacks.onOpenNotifications)
    val twoFingerDistancePx = rememberTwoFingerDistancePx()
    val reading by rememberWallClock()
    val scroll = rememberScrollState()
    val reducedMotion = rememberReducedMotion()
    // ≈ a `didUpdateWidget` that reacts to a new request number. 0 = no request yet; a fresh
    // Home (e.g. after leaving intent detail) starts at the top anyway.
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) {
            if (reducedMotion) scroll.scrollTo(0) else scroll.animateScrollTo(0)
        }
    }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .screenActionGestures(
                    doubleTapEnabled = system.gestures.doubleTapToLock,
                    // Not while a press-slide-release fan is open: a second finger (a palm, a
                    // grip) must not steal the fan's pointer stream.
                    twoFingerEnabled = system.gestures.twoFingerSwipe && heldIntent == null,
                    twoFingerDistancePx = twoFingerDistancePx,
                    onDoubleTap = onLock,
                    onTwoFingerSwipeDown = onOpenNotifications,
                ).nestedScroll(swipe)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier =
                Modifier
                    // No overscroll stretch: pulling past the edge means "open Search", not "bounce".
                    .verticalScroll(scroll, overscrollEffect = null)
                    .heightIn(min = maxHeight)
                    .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, top = TopPadding, bottom = BottomPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Column(verticalArrangement = Arrangement.spacedBy(DateClockGap)) {
                    HomeDateLine(reading)
                    HomeClock(reading, onOpenAppearance = onAppearance)
                    // Context line (spec §6.3 item 3): hidden without an event or calendar permission.
                    system.nextEvent?.let { HomeContextLine(it, reading) }
                }
                system.onboarding?.let { card ->
                    // key(card): the next card is a fresh composable (fresh permission launcher).
                    key(card) { OnboardingCardView(card, onboardingCallbacks, Modifier.padding(top = CardTopGap)) }
                }
                if (catalogLoaded && now.isNotEmpty()) {
                    NowRow(now, onLaunch, Modifier.padding(top = NowTopGap))
                }
            }
            Column {
                IntentWords(intents, catalogLoaded, thumbSide, onIntentSelected, wordModifier, heldIntent, system.media, mediaCallbacks)
                val pillActions = homePillActions(system.gestures, onAllApps, gestureCallbacks)
                if (heldIntent != null) {
                    FanHint(Modifier.padding(top = PillTopGap))
                } else {
                    SearchPill(
                        onClick = onSearch,
                        modifier =
                            Modifier
                                .padding(top = PillTopGap)
                                // Home's gestures without a visible tap alternative (spec §6.10):
                                // swipe-up, and — when they can work — double-tap to lock and the
                                // two-finger swipe, as TalkBack actions on the pill.
                                .semantics { customActions = pillActions },
                    )
                }
            }
        }
    }
}

/**
 * TalkBack's actions on the search pill: "All apps" always; "Lock screen" and "Open
 * notifications" only while they would work (a TalkBack action that only shows a hint would
 * be noise). With TalkBack on, a double-tap means "activate", so these actions are the only
 * way TalkBack users reach the screen actions.
 */
@Composable
private fun homePillActions(
    gestures: HomeGesturesUi,
    onAllApps: () -> Unit,
    callbacks: ScreenGestureCallbacks,
): List<CustomAccessibilityAction> {
    val allApps = stringResource(R.string.home_all_apps_action)
    val lock = stringResource(R.string.home_lock_action)
    val notifications = stringResource(R.string.home_notifications_action)
    return buildList {
        add(CustomAccessibilityAction(allApps) { onAllApps().let { true } })
        if (gestures.canLock) add(CustomAccessibilityAction(lock) { callbacks.onLock().let { true } })
        if (gestures.canOpenShade) add(CustomAccessibilityAction(notifications) { callbacks.onOpenNotifications().let { true } })
    }
}

/**
 * The hint shown in the search pill's place while the fan is open (spec §6.5). Same outline
 * and minimum height as the pill, so nothing below the words moves.
 */
@Composable
private fun FanHint(modifier: Modifier = Modifier) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachDimens.searchPillHeight / 2)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .border(ReachComponentDimens.hairline, colors.line, shape)
                .heightIn(min = ReachDimens.searchPillHeight)
                .padding(horizontal = ReachComponentDimens.searchPillHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(text = stringResource(R.string.home_fan_hint), textAlign = TextAlign.Center)
    }
}

@Composable
private fun NowRow(
    now: List<NowItem>,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NowLabelGap)) {
        MonoLabel(text = stringResource(R.string.home_now_label), modifier = Modifier.semantics { heading() })
        // IntrinsicSize.Min + fillMaxHeight: all tiles as tall as the tallest one when a
        // label wraps at large font sizes (≈ IntrinsicHeight + CrossAxisAlignment.stretch).
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.nowTileSpacing),
        ) {
            now.take(NOW_ROW_SLOTS).forEach { item ->
                // key(): the remembered origin follows its app when predictions reorder.
                key(item.app.key) {
                    val origin = rememberLaunchOrigin()
                    NowTile(
                        appLabel = item.app.label,
                        reasonLabel = reasonLabel(item.reason),
                        onClick = { onLaunch(item.app, origin.toLaunchOrigin()) },
                        modifier = Modifier.weight(1f).fillMaxHeight().trackLaunchOrigin(origin),
                    )
                }
            }
            // Fewer than three predictions: keep the tiles one third wide.
            repeat(NOW_ROW_SLOTS - now.size.coerceAtMost(NOW_ROW_SLOTS)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun IntentWords(
    intents: List<IntentSummary>,
    catalogLoaded: Boolean,
    thumbSide: ThumbSide,
    onIntentSelected: (ReachIntent) -> Unit,
    wordModifier: (ReachIntent) -> Modifier,
    heldIntent: ReachIntent?,
    media: MediaState?,
    mediaCallbacks: MediaCallbacks,
) {
    val colors = ReachTheme.colors
    val openLabel = stringResource(R.string.home_intent_open_action)
    Column(verticalArrangement = Arrangement.spacedBy(ReachComponentDimens.intentRowSpacing)) {
        intents.forEach { summary ->
            IntentWordRow(
                word = intentWord(summary.intent),
                trailing =
                    if (catalogLoaded && heldIntent == null) {
                        IntentRowTrailing.of(appCount = summary.appCount, unreadCount = summary.unreadCount)
                    } else {
                        // Not "0" before the cached catalog arrives; nothing while the fan is open.
                        IntentRowTrailing.None
                    },
                onClick = { onIntentSelected(summary.intent) },
                thumbSide = thumbSide,
                onClickLabel = openLabel,
                wordModifier = wordModifier(summary.intent),
                wordColor =
                    when (heldIntent) {
                        null -> Color.Unspecified
                        summary.intent -> colors.accentInk
                        else -> colors.dim
                    },
                // Listen's inline player (spec §6.3 item 6): only while a session is active,
                // and not while the fan is open (the words are the only thing that matters then).
                extraContent =
                    if (media != null && summary.intent == ReachIntent.LISTEN && heldIntent == null) {
                        {
                            InlineMediaControl(
                                trackTitle = media.title,
                                appLabel = media.sourceLabel,
                                isPlaying = media.isPlaying,
                                onTogglePlay = mediaCallbacks.onPlayPause,
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}
