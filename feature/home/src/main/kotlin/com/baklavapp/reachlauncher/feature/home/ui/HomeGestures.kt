package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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
 * the touch slop, so taps and the clock's long-press never reach this code. Press-slide-release
 * on the intent words (PressSlideRelease.kt) consumes every pointer change once its fan is
 * open, so the scrollable never drags and no leftovers reach this connection
 * (`PressSlideReleaseTest.verticalMovementInTheFanDoesNotTriggerHomeSwipes`).
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

/*
 * Screen-action gestures (spec §6.3, §7.4; docs/DECISIONS.md D66): double-tap on empty Home to
 * lock, two-finger swipe down on Home to open the notification shade. Both run through the
 * optional accessibility service; without it the ViewModel shows a hint instead.
 *
 * How they stay out of the way of Home's other gestures (docs/GESTURES.md):
 * - **Double-tap** is detected on Home's outermost box, *after* its children: a tap on a
 *   word, tile, pill or the clock is consumed by that control, so only taps on empty space
 *   reach the detector. It is only installed while "Double-tap to lock" is on, so normally a
 *   tap on empty space is never held back waiting for a second one.
 * - **Two fingers** are watched in the *Initial* pass (before the scroll and the words see
 *   the event). One finger is never touched, so pull-down → Search, swipe-up → All Apps,
 *   press-slide-release and the clock's long-press work exactly as before. As soon as a
 *   second finger is down, the gesture belongs to this detector: its moves are consumed, so
 *   the scroll cannot also turn them into "open Search".
 */

/** How far both fingers must travel down for the two-finger swipe. */
private val TwoFingerDistance = 48.dp

/** The ViewModel's screen-action entry points, bundled to keep signatures short. */
@Immutable
internal data class ScreenGestureCallbacks(
    val onLock: () -> Unit,
    val onOpenNotifications: () -> Unit,
) {
    companion object {
        /** No-op callbacks, for previews and tests that don't care. */
        val None = ScreenGestureCallbacks({}, {})
    }
}

/**
 * The two-finger rule, pure so it is unit-tested: at least two fingers, and each of the first
 * two moved down by [minDistancePx] or more, more down than sideways (within 45° of
 * vertical). [deltas] are the fingers' offsets from where they touched down.
 */
internal fun isTwoFingerSwipeDown(
    deltas: List<Offset>,
    minDistancePx: Float,
): Boolean = deltas.size >= 2 && deltas.take(2).all { it.y >= minDistancePx && abs(it.x) <= it.y }

/**
 * Attaches the screen-action gestures. [doubleTapEnabled] and [twoFingerEnabled] install or
 * remove each detector, so a gesture that can't do anything never holds back a touch. The callbacks are `State`s so the detectors,
 * which outlive recompositions, always call the latest lambdas.
 */
internal fun Modifier.screenActionGestures(
    doubleTapEnabled: Boolean,
    twoFingerEnabled: Boolean,
    twoFingerDistancePx: Float,
    onDoubleTap: State<() -> Unit>,
    onTwoFingerSwipeDown: State<() -> Unit>,
): Modifier {
    val twoFinger =
        if (twoFingerEnabled) {
            pointerInput(twoFingerDistancePx) { detectTwoFingerSwipeDown(twoFingerDistancePx) { onTwoFingerSwipeDown.value() } }
        } else {
            this
        }
    return if (doubleTapEnabled) {
        twoFinger.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap.value() }) }
    } else {
        twoFinger
    }
}

/** Pixels for [TwoFingerDistance] at the current density. */
@Composable
internal fun rememberTwoFingerDistancePx(): Float {
    val density = LocalDensity.current
    return remember(density) { with(density) { TwoFingerDistance.toPx() } }
}

private suspend fun PointerInputScope.detectTwoFingerSwipeDown(
    minDistancePx: Float,
    onSwipe: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val starts = HashMap<PointerId, Offset>()
        var fired = false
        // Once a second finger has touched, the whole gesture is ours until every finger is
        // up: the finger left behind after the other lifts must not reach the scroll and
        // turn the end of a shade swipe into "open Search".
        var claimed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                claimed = true
                pressed.forEach { starts.getOrPut(it.id) { it.position } }
                if (!fired && isTwoFingerSwipeDown(pressed.map { it.position - starts.getValue(it.id) }, minDistancePx)) {
                    fired = true
                    onSwipe()
                }
            }
            if (claimed) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
