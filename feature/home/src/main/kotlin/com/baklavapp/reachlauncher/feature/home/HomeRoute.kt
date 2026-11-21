package com.baklavapp.reachlauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.feature.allapps.AllAppsRoute
import com.baklavapp.reachlauncher.feature.appearance.AppearanceRoute
import com.baklavapp.reachlauncher.feature.home.ui.AppMenuCallbacks
import com.baklavapp.reachlauncher.feature.home.ui.FanCallbacks
import com.baklavapp.reachlauncher.feature.home.ui.FavoriteCallbacks
import com.baklavapp.reachlauncher.feature.home.ui.HomeWithFan
import com.baklavapp.reachlauncher.feature.home.ui.IntentDetailScreen
import com.baklavapp.reachlauncher.feature.home.ui.MediaCallbacks
import com.baklavapp.reachlauncher.feature.home.ui.OnboardingCallbacks
import com.baklavapp.reachlauncher.feature.home.ui.ScreenGestureCallbacks
import com.baklavapp.reachlauncher.feature.search.SearchRoute
import kotlinx.coroutines.delay

/*
 * HomeRoute — connects HomeViewModel to the stateless surface composables.
 *
 * Flutter analogy:
 * - `collectAsStateWithLifecycle()` ≈ `BlocBuilder`: recomposes when the state changes, and
 *   stops collecting while the app is in the background (lifecycle-aware, like a
 *   `StreamBuilder` that pauses).
 * - The `events` collector ≈ `BlocListener`: runs side effects (hide keyboard, show a
 *   message) once per event, outside of building UI.
 * - `BackHandler` ≈ `PopScope(canPop: false, onPopInvoked: …)` / `WillPopScope`: it
 *   intercepts system Back (button or gesture) while it's in the composition.
 * - `AnimatedContent` ≈ `AnimatedSwitcher`: cross-fades between surfaces.
 */

/**
 * What a sub-surface (Search, All Apps) may ask Home to do. Passed to the surface slots of
 * [HomeRoute] so the feature modules never see `HomeViewModel`.
 *
 * @property onClose back to Home — after a launch, or the surface's own close button.
 * @property onOpenAppearance Search's "Appearance settings" command.
 * @property onOpenSearch All Apps' "Type to launch" pill.
 */
@Immutable
class SurfaceCallbacks(
    val onClose: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onOpenSearch: () -> Unit,
)

private val MessageBottomPadding = 96.dp
private val MessagePadding = 14.dp
private const val MESSAGE_DURATION_MILLIS = 3_000L
private const val ENTER_MILLIS = 220
private const val EXIT_MILLIS = 150
private const val SLIDE_FRACTION = 12

/**
 * The whole Home surface: renders the active [HomeSurface] of [viewModel], handles Back,
 * and reacts to one-off [HomeEvent]s. Must be inside `ReachTheme`.
 *
 * Back is always consumed: in sub-surfaces it steps back one level, on Home it does
 * nothing — the default (finishing the Activity) would tear down the launcher (spec §4).
 *
 * @param searchContent the Search surface; defaults to `:feature:search`'s [SearchRoute].
 * @param allAppsContent the All Apps surface; defaults to `:feature:allapps`'s [AllAppsRoute].
 * @param appearanceContent the Appearance sheet drawn over Home; defaults to
 *   `:feature:appearance`'s [AppearanceRoute].
 *   All three are slots (≈ passing a `Widget` builder in Flutter) so tests can render Home
 *   without Hilt: the real routes create their ViewModels with `hiltViewModel()`.
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    searchContent: @Composable (callbacks: SurfaceCallbacks) -> Unit = { SearchRoute(it.onClose, it.onOpenAppearance) },
    allAppsContent: @Composable (callbacks: SurfaceCallbacks) -> Unit = { AllAppsRoute(it.onClose, it.onOpenSearch) },
    appearanceContent: @Composable (callbacks: SurfaceCallbacks) -> Unit = { AppearanceRoute(it.onClose) },
) {
    // Two separate subscriptions on purpose: the surface is always current (it has no
    // upstream to restart after WhileSubscribed's timeout), so the first frame after Home is
    // pressed in the background already shows Home, even while the data state catches up.
    val surface by viewModel.surface.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<InlineMessage?>(null) }
    var scrollToTopRequests by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    HomeEvent.HideKeyboard -> {
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                    }

                    HomeEvent.ScrollToTop -> {
                        scrollToTopRequests++
                    }

                    is HomeEvent.LaunchFailed -> {
                        message = InlineMessage.LaunchFailed(event.label)
                    }

                    is HomeEvent.ScreenActionUnavailable -> {
                        message = InlineMessage.Unavailable(event.action)
                    }
                }
            }
        }
    }
    // Keyed on the InlineMessage *instance*: a second failure for the same app is a new
    // object, so the 3 s timer restarts instead of hiding the new message early.
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DURATION_MILLIS)
            message = null
        }
    }
    BackHandler(enabled = true) { viewModel.onBack() }

    // root: the opaque bg, or in wallpaper mode the 82/86 % scrim over the wallpaper — painted
    // once here; every surface above paints `backdrop` (transparent in wallpaper mode).
    Box(modifier = modifier.fillMaxSize().background(ReachTheme.colors.root)) {
        HomeSurfaces(
            HomeFrame(surface, state),
            viewModel,
            scrollToTopRequests,
            SurfaceSlots(searchContent, allAppsContent, appearanceContent),
        )
        message?.let { InlineMessageView(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

/**
 * One shown inline message. Deliberately *not* data classes: two failures for the same label
 * must be unequal so `LaunchedEffect(message)` restarts its timer.
 */
private sealed class InlineMessage {
    /** "Couldn't open Maps". */
    class LaunchFailed(
        val label: String,
    ) : InlineMessage()

    /** A screen-action gesture needs the accessibility service (D66). */
    class Unavailable(
        val action: ScreenAction,
    ) : InlineMessage()
}

/** What one frame of [HomeSurfaces] shows: the active surface plus the data snapshot. */
@Immutable
private data class HomeFrame(
    val surface: HomeSurface,
    val state: HomeUiState,
)

/** The feature surfaces [HomeRoute] hosts, bundled so [HomeSurfaces] takes one parameter. */
private class SurfaceSlots(
    val search: @Composable (SurfaceCallbacks) -> Unit,
    val allApps: @Composable (SurfaceCallbacks) -> Unit,
    val appearance: @Composable (SurfaceCallbacks) -> Unit,
)

@Composable
private fun HomeSurfaces(
    frame: HomeFrame,
    viewModel: HomeViewModel,
    scrollToTopRequests: Int,
    slots: SurfaceSlots,
) {
    val reducedMotion = rememberReducedMotion()
    val surfaceCallbacks =
        remember(viewModel) {
            SurfaceCallbacks(
                onClose = viewModel::onBack,
                onOpenAppearance = viewModel::onAppearanceRequested,
                onOpenSearch = viewModel::onSearchRequested,
            )
        }
    val fan by viewModel.fan.collectAsStateWithLifecycle()
    // Media / calendar / onboarding: collected here, not in HomeFrame, so a track change
    // never counts as a new frame for the surface transition.
    val system by viewModel.system.collectAsStateWithLifecycle()
    val mediaCallbacks =
        remember(
            viewModel,
        ) { MediaCallbacks(viewModel::onPlayPause, viewModel::onSkipNext, viewModel::onSkipPrevious, viewModel::onOpenMediaApp) }
    val onboardingCallbacks =
        remember(viewModel) {
            OnboardingCallbacks(viewModel::onOnboardingAction, viewModel::onOnboardingDismissed, viewModel::onCalendarPermissionResult)
        }
    val fanCallbacks =
        remember(viewModel) {
            FanCallbacks(viewModel::onFanOpen, viewModel::onFanHighlight, viewModel::onFanRelease, viewModel::onFanCancel)
        }
    val gestureCallbacks =
        remember(viewModel) { ScreenGestureCallbacks(viewModel::onLockScreenRequested, viewModel::onNotificationsRequested) }
    val appMenuCallbacks =
        remember(viewModel) {
            AppMenuCallbacks(viewModel::openAppInfo, viewModel::moveToIntent, viewModel::hideApp, viewModel::uninstall)
        }
    // targetState is the whole frame, so an exiting surface keeps rendering the snapshot it
    // was showing (e.g. the detail list) while it fades out. contentKey decides when a
    // transition runs: switching intents via the chips updates detail in place, and the
    // Appearance sheet opens *over* Home, so both share Home's key.
    val wallpaperMode = ReachTheme.colors.wallpaperMode
    val sheetOpen = frame.surface == HomeSurface.Appearance
    AnimatedContent(
        targetState = frame,
        // While the Appearance sheet is open, Home behind it is dimmed and out of reach:
        // hide it from TalkBack too, so swipe navigation stays inside the sheet (modal).
        modifier = if (sheetOpen) Modifier.clearAndSetSemantics { } else Modifier,
        contentKey = { it.surface.contentKey() },
        transitionSpec = { surfaceTransition(reducedMotion, wallpaperMode) },
        label = "surface",
    ) { shown ->
        val state = shown.state
        when (val surface = shown.surface) {
            HomeSurface.Home, HomeSurface.Appearance -> {
                // Home with press-slide-release (spec §6.5): the fan's state lives in the ViewModel.
                HomeWithFan(
                    state = state,
                    fan = fan,
                    fanCallbacks = fanCallbacks,
                    onIntentSelected = viewModel::onIntentSelected,
                    onLaunch = viewModel::launch,
                    onSearch = viewModel::onSearchRequested,
                    onAllApps = viewModel::onAllAppsRequested,
                    onAppearance = viewModel::onAppearanceRequested,
                    scrollToTopRequests = scrollToTopRequests,
                    system = system,
                    mediaCallbacks = mediaCallbacks,
                    onboardingCallbacks = onboardingCallbacks,
                    gestureCallbacks = gestureCallbacks,
                )
            }

            is HomeSurface.IntentDetail -> {
                IntentDetailScreen(
                    detail = state.detailFor(surface.intent),
                    onBack = viewModel::onBack,
                    onLaunch = viewModel::launch,
                    onIntentSelected = viewModel::onIntentSelected,
                    media = system.media,
                    mediaCallbacks = mediaCallbacks,
                    appMenu = appMenuCallbacks,
                    favoriteCallbacks =
                        remember(surface.intent) {
                            FavoriteCallbacks(
                                onAdd = { viewModel.addFavorite(surface.intent, it) },
                                onRemove = { viewModel.removeFavorite(surface.intent, it) },
                                onMove = { app, by -> viewModel.moveFavorite(surface.intent, app, by) },
                            )
                        },
                    pickableApps = state.pickableApps,
                )
            }

            HomeSurface.Search -> {
                slots.search(surfaceCallbacks)
            }

            HomeSurface.AllApps -> {
                slots.allApps(surfaceCallbacks)
            }
        }
    }
    // The sheet slides up over Home, which stays visible (and re-themes live) above it.
    AnimatedVisibility(
        visible = sheetOpen,
        enter = if (reducedMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
        exit = if (reducedMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
        label = "appearance",
    ) {
        slots.appearance(surfaceCallbacks)
    }
}

/** Surfaces that share a key don't animate between each other (see [HomeSurfaces]). */
private fun HomeSurface.contentKey(): String =
    when (this) {
        HomeSurface.Home, HomeSurface.Appearance -> "home"
        is HomeSurface.IntentDetail -> "detail"
        HomeSurface.Search -> "search"
        HomeSurface.AllApps -> "allapps"
    }

/**
 * A short fade with a slight slide in the gesture's direction (Search from above, All Apps
 * and detail from below). With "Remove animations" on, only a cross-fade remains: what
 * moves changes, not just how fast (spec §6.10).
 *
 * In wallpaper mode surfaces paint a transparent `backdrop`, so during a cross-fade the old
 * and new text would be drawn on top of each other with nothing opaque between them. There
 * the new surface fades in only after the old one has faded out (D65).
 */
private fun AnimatedContentTransitionScope<HomeFrame>.surfaceTransition(
    reducedMotion: Boolean,
    wallpaperMode: Boolean,
): ContentTransform {
    val delay = if (wallpaperMode) EXIT_MILLIS else 0
    val enterFade = fadeIn(tween(ENTER_MILLIS, delayMillis = delay))
    val exitFade = fadeOut(tween(EXIT_MILLIS))
    if (reducedMotion) return enterFade togetherWith exitFade
    val fromAbove = targetState.surface == HomeSurface.Search
    val slide =
        slideInVertically(tween(ENTER_MILLIS, delayMillis = delay)) { height ->
            if (fromAbove) {
                -height / SLIDE_FRACTION
            } else {
                height /
                    SLIDE_FRACTION
            }
        }
    return (enterFade + slide) togetherWith exitFade
}

/**
 * Reach-styled inline message ("Couldn't open Maps", or a screen-action hint), announced
 * politely by TalkBack. Not a Material Snackbar: Reach never shows stock Material chrome (spec §3).
 */
@Composable
private fun InlineMessageView(
    message: InlineMessage,
    modifier: Modifier = Modifier,
) {
    val text =
        when (message) {
            is InlineMessage.LaunchFailed -> {
                stringResource(R.string.home_launch_failed, message.label)
            }

            is InlineMessage.Unavailable -> {
                when (message.action) {
                    ScreenAction.LOCK -> stringResource(R.string.home_lock_needs_service)
                    ScreenAction.NOTIFICATIONS -> stringResource(R.string.home_shade_needs_service)
                }
            }
        }
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachComponentDimens.pillCorner)
    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, bottom = MessageBottomPadding)
                .background(colors.surface, shape)
                .border(ReachComponentDimens.hairline, colors.line2, shape)
                .padding(MessagePadding)
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text = text, style = ReachTheme.typography.body, color = colors.fg)
    }
}
