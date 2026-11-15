package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.home.FanState
import com.baklavapp.reachlauncher.feature.home.HomeSystemUi
import com.baklavapp.reachlauncher.feature.home.HomeUiState

/*
 * Home plus its press-slide-release fan: wires the ViewModel's fan state and callbacks to the
 * gesture (attached through `HomeScreen(wordModifier = …)`) and draws the overlay above Home.
 *
 * Flutter analogy: a `Stack(children: [HomeScreen(...), FanOverlay(...)])` whose two layers
 * share one state object — here [PressSlideReleaseState].
 */

/** The ViewModel's fan entry points (`HomeViewModel.onFan*`), bundled to keep signatures short. */
@Immutable
internal data class FanCallbacks(
    val onOpen: (ReachIntent) -> Boolean,
    val onHighlight: (Int?) -> Unit,
    val onRelease: (LaunchOrigin?) -> Unit,
    val onCancel: () -> Unit,
)

/**
 * [HomeScreen] with press-slide-release on its intent words and the fan overlay on top.
 * If Home leaves the composition while a fan is open, the fan is cancelled.
 */
@Composable
internal fun HomeWithFan(
    state: HomeUiState,
    fan: FanState?,
    fanCallbacks: FanCallbacks,
    onIntentSelected: (ReachIntent) -> Unit,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onSearch: () -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    scrollToTopRequests: Int,
    modifier: Modifier = Modifier,
    system: HomeSystemUi = HomeSystemUi(),
    mediaCallbacks: MediaCallbacks = MediaCallbacks.None,
    onboardingCallbacks: OnboardingCallbacks = OnboardingCallbacks.None,
    gestureCallbacks: ScreenGestureCallbacks = ScreenGestureCallbacks.None,
) {
    val gesture =
        rememberPressSlideReleaseState(
            onOpen = fanCallbacks.onOpen,
            onHighlight = fanCallbacks.onHighlight,
            onRelease = fanCallbacks.onRelease,
            onCancel = fanCallbacks.onCancel,
        )
    val cancel = rememberUpdatedState(fanCallbacks.onCancel)
    DisposableEffect(gesture) { onDispose { if (gesture.anchor != null) cancel.value() } }
    // The fan is "open" for the UI only while both halves agree: the ViewModel may close it
    // under a still-held finger (Home pressed); the gesture then just runs out harmlessly.
    val openFan = fan?.takeIf { gesture.anchor != null }
    Box(modifier = modifier.fillMaxSize()) {
        HomeScreen(
            intents = state.intents,
            now = state.now,
            catalogLoaded = state.catalogLoaded,
            thumbSide = state.thumbSide,
            onIntentSelected = onIntentSelected,
            onLaunch = onLaunch,
            onSearch = onSearch,
            onAllApps = onAllApps,
            onAppearance = onAppearance,
            scrollToTopRequests = scrollToTopRequests,
            wordModifier = { intent -> Modifier.pressSlideRelease(intent, gesture) },
            heldIntent = openFan?.intent,
            system = system,
            mediaCallbacks = mediaCallbacks,
            onboardingCallbacks = onboardingCallbacks,
            gestureCallbacks = gestureCallbacks,
        )
        FanOverlay(fan = openFan, state = gesture, thumbSide = state.thumbSide, modifier = Modifier.fillMaxSize())
    }
}
