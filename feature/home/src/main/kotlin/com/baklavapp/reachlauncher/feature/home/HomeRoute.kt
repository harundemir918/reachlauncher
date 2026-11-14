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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.baklavapp.reachlauncher.feature.home.ui.AllAppsPlaceholder
import com.baklavapp.reachlauncher.feature.home.ui.AppearancePlaceholderSheet
import com.baklavapp.reachlauncher.feature.home.ui.HomeScreen
import com.baklavapp.reachlauncher.feature.home.ui.IntentDetailScreen
import com.baklavapp.reachlauncher.feature.home.ui.SearchPlaceholder
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
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var failedLabel by remember { mutableStateOf<String?>(null) }
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

                    is HomeEvent.LaunchFailed -> {
                        failedLabel = event.label
                    }
                }
            }
        }
    }
    LaunchedEffect(failedLabel) {
        if (failedLabel != null) {
            delay(MESSAGE_DURATION_MILLIS)
            failedLabel = null
        }
    }
    BackHandler(enabled = true) { viewModel.onBack() }

    Box(modifier = modifier.fillMaxSize().background(ReachTheme.colors.bg)) {
        HomeSurfaces(state, viewModel)
        failedLabel?.let { LaunchFailedMessage(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun HomeSurfaces(
    state: HomeUiState,
    viewModel: HomeViewModel,
) {
    val reducedMotion = rememberReducedMotion()
    // targetState is the whole UI state, so an exiting surface keeps rendering the snapshot
    // it was showing (e.g. the detail list) while it fades out. contentKey decides when a
    // transition runs: switching intents via the chips updates detail in place, and the
    // Appearance sheet opens *over* Home, so both share Home's key.
    AnimatedContent(
        targetState = state,
        contentKey = { it.surface.contentKey() },
        transitionSpec = { surfaceTransition(reducedMotion) },
        label = "surface",
    ) { shown ->
        when (val surface = shown.surface) {
            HomeSurface.Home, HomeSurface.Appearance -> {
                HomeScreen(
                    intents = shown.intents,
                    now = shown.now,
                    thumbSide = shown.thumbSide,
                    onIntentSelected = viewModel::onIntentSelected,
                    onLaunch = viewModel::launch,
                    onSearch = viewModel::onSearchRequested,
                    onAllApps = viewModel::onAllAppsRequested,
                    onAppearance = viewModel::onAppearanceRequested,
                )
            }

            is HomeSurface.IntentDetail -> {
                // detail is non-null whenever the surface is IntentDetail (built together).
                val detail = shown.detail ?: IntentDetailUi(surface.intent, emptyList(), emptyList())
                IntentDetailScreen(
                    detail = detail,
                    onBack = viewModel::onBack,
                    onLaunch = viewModel::launch,
                    onLaunchShortcut = viewModel::launchShortcut,
                    onIntentSelected = viewModel::onIntentSelected,
                )
            }

            HomeSurface.Search -> {
                SearchPlaceholder(onClose = viewModel::onBack)
            }

            HomeSurface.AllApps -> {
                AllAppsPlaceholder(onClose = viewModel::onBack)
            }
        }
    }
    AnimatedVisibility(
        visible = state.surface == HomeSurface.Appearance,
        enter = if (reducedMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
        exit = if (reducedMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
        label = "appearance",
    ) {
        AppearancePlaceholderSheet(onClose = viewModel::onBack)
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
 */
private fun AnimatedContentTransitionScope<HomeUiState>.surfaceTransition(reducedMotion: Boolean): ContentTransform {
    val enterFade = fadeIn(tween(ENTER_MILLIS))
    val exitFade = fadeOut(tween(EXIT_MILLIS))
    if (reducedMotion) return enterFade togetherWith exitFade
    val fromAbove = targetState.surface == HomeSurface.Search
    val slide = slideInVertically(tween(ENTER_MILLIS)) { height -> if (fromAbove) -height / SLIDE_FRACTION else height / SLIDE_FRACTION }
    return (enterFade + slide) togetherWith exitFade
}

/**
 * Reach-styled inline message ("Couldn't open Maps"), announced politely by TalkBack.
 * Not a Material Snackbar: Reach never shows stock Material chrome (spec §3).
 */
@Composable
private fun LaunchFailedMessage(
    label: String,
    modifier: Modifier = Modifier,
) {
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
        Text(text = stringResource(R.string.home_launch_failed, label), style = ReachTheme.typography.body, color = colors.fg)
    }
}
