package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/*
 * Home's vertical swipes: pull down → Search, swipe up → All Apps (spec §6.3).
 *
 * Flutter analogy: in Flutter you'd wrap Home in a `GestureDetector(onVerticalDragUpdate:,
 * onVerticalDragEnd:)` — Compose's equivalent is `Modifier.pointerInput { detectVerticalDragGestures(…) }`.
 * We use a different tool on purpose: Home *scrolls* at 200% font size (spec §6.10), and a
 * second vertical drag detector would fight the scroll. Instead we listen to the scroll's
 * **nested-scroll** leftovers (≈ Flutter's `NotificationListener<OverscrollNotification>`):
 * whatever drag distance the content could not consume — all of it when Home fits on screen,
 * the part past the top/bottom edge when it doesn't — counts towards the swipe.
 *
 * Why this keeps taps and long-press working: the scrollable only claims the pointer after
 * the touch slop, so taps and the clock's long-press never reach this code. For M2's
 * press-slide-release on the intent words (attached via `wordModifier`): a detector there
 * that consumes its move events once the fan opens should keep the scroll from dragging,
 * because the scrollable ignores already-consumed movement — verify this with a UI test
 * when the gesture lands.
 */

/** Drag distance past the scroll edge that opens Search / All Apps. */
private val SwipeDistance = 72.dp

/** A flick this fast (with a little travel) also counts, so short quick swipes work. */
private val FlingVelocity = 1_200.dp // per second
private val FlingMinDistance = 16.dp

/**
 * Turns unconsumed vertical drag into one swipe callback per gesture.
 *
 * Sign convention of nested scroll: a positive `y` delta is the finger moving **down**.
 */
internal class VerticalSwipeConnection(
    private val distancePx: Float,
    private val flingVelocityPx: Float,
    private val flingMinDistancePx: Float,
    private val onSwipeUp: State<() -> Unit>,
    private val onSwipeDown: State<() -> Unit>,
) : NestedScrollConnection {
    private var accumulated = 0f
    private var fired = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || fired) return Offset.Zero
        // The content itself scrolled: start measuring from the edge it just reached.
        if (consumed.y != 0f) accumulated = 0f
        accumulated += available.y
        when {
            accumulated >= distancePx -> fire(down = true)
            accumulated <= -distancePx -> fire(down = false)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Called once when the finger lifts: the end of this gesture.
        if (!fired) {
            when {
                available.y >= flingVelocityPx && accumulated >= flingMinDistancePx -> fire(down = true)
                available.y <= -flingVelocityPx && accumulated <= -flingMinDistancePx -> fire(down = false)
            }
        }
        accumulated = 0f
        fired = false
        return Velocity.Zero
    }

    private fun fire(down: Boolean) {
        fired = true
        if (down) onSwipeDown.value() else onSwipeUp.value()
    }
}

/** Remembers a [VerticalSwipeConnection]; attach it with `Modifier.nestedScroll` above the scroll. */
@Composable
internal fun rememberVerticalSwipeConnection(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): VerticalSwipeConnection {
    val density = LocalDensity.current
    // rememberUpdatedState: the connection lives across recompositions but must always call
    // the latest lambdas (≈ reading `widget.onX` in a State instead of capturing it once).
    val up = rememberUpdatedState(onSwipeUp)
    val down = rememberUpdatedState(onSwipeDown)
    return remember(density) {
        with(density) {
            VerticalSwipeConnection(SwipeDistance.toPx(), FlingVelocity.toPx(), FlingMinDistance.toPx(), up, down)
        }
    }
}
