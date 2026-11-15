package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.util.rememberHapticTick
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ReachIntent

/*
 * Press, slide, release — Reach's signature gesture (spec §6.5).
 *
 * What the user does: hold an intent word (~300 ms), its apps fan upward beside the thumb;
 * slide onto one (it lights up, with a haptic tick) and lift the finger to open it. Lifting
 * anywhere else, or sliding back onto the word, cancels.
 *
 * ─── Flutter analogy ───────────────────────────────────────────────────────────────────
 *
 * - `Modifier.pointerInput { awaitEachGesture { … } }` ≈ a `RawGestureDetector` with your own
 *   `GestureRecognizer` subclass. Instead of overriding `addPointer` / `handleEvent` /
 *   `acceptGesture` callbacks, Compose lets you write the recognizer as *one sequential
 *   coroutine*: "wait for a down, then wait for 300 ms or a move, then follow the finger".
 *   `awaitEachGesture` restarts that coroutine for every new gesture (finger down → all
 *   fingers up), like a recognizer being reset after it resolves.
 * - `awaitFirstDown(requireUnconsumed = false)` ≈ `addAllowedPointer` / `onPointerDown`.
 *   `requireUnconsumed = false` because the word's own click handler (a child, see below)
 *   has already marked the down as "used" for its pressed state; we still want to see it.
 * - **Consuming** a `PointerInputChange` (`change.consume()`) ≈ **winning the gesture
 *   arena**. Flutter's arena is a referee: recognizers compete, one declares victory
 *   (`resolve(GestureDisposition.accepted)`) and the others are rejected. Compose has no
 *   referee — every detector sees every event — but by convention a detector *ignores
 *   events someone else consumed*. The scrollable, the click and our gesture all follow that
 *   convention, so consuming is how you say "this one is mine".
 * - The haptic tick ([rememberHapticTick]) ≈ `HapticFeedback.selectionClick()`.
 *
 * ─── The three passes (why we read some events in `Initial`) ───────────────────────────
 *
 * Each pointer event travels the UI tree three times (≈ Flutter's hit-test path, but
 * walked three ways):
 *
 * 1. `PointerEventPass.Initial` — from the root **down** to the touched leaf. Parents see
 *    the event *before* their children: the place to intercept.
 * 2. `PointerEventPass.Main` — from the leaf back **up** to the root. The normal pass: the
 *    innermost handler (e.g. a button) reacts first, then its ancestors (e.g. a scroll).
 * 3. `PointerEventPass.Final` — down again, to learn what everyone else decided.
 *
 * In a modifier chain, a modifier written *earlier* is the *parent* of the ones after it.
 * `IntentWordRow` applies our `wordModifier` before its own `reachClickable`, so for the
 * word's pointer events the tree looks like:
 *
 * ```
 * verticalScroll (Home's scroll, + the nested-scroll swipe detection, D29)
 *   └ … └ pressSlideRelease (this file)      ← parent
 *           └ reachClickable (tap → intent detail) ← child
 * ```
 *
 * Before the fan opens we only *watch*, in `Main` (after the click has seen the event,
 * before the scroll has). Once the fan is open we read each event in `Initial` and consume
 * it there, i.e. before both the click (it runs in `Main`, after us in `Initial`) and the
 * scroll (it reacts in `Main`, after its children). Both then see consumed changes and
 * stand down: the click doesn't fire when the finger lifts, the scroll doesn't drag, so no
 * nested-scroll leftovers reach the pull-down / swipe-up detector.
 *
 * ─── Step by step ──────────────────────────────────────────────────────────────────────
 *
 * 1. **Down.** `awaitFirstDown(requireUnconsumed = false)` on the word.
 * 2. **Hold.** Inside `withTimeoutOrNull(hold)` we watch the pointer. If it lifts, moves
 *    beyond the touch slop (`viewConfiguration.touchSlop`, ≈ `kTouchSlop`), is consumed by
 *    someone else, or a second finger lands, we stop and **do nothing**: we never consumed
 *    anything, so the tap (opens intent detail — the accessible path) or the scroll wins as
 *    if we weren't there. Only when the timeout fires with the finger still in place does the
 *    gesture arm.
 * 3. **Open.** We ask the ViewModel to open the fan for this intent (`onFanOpen`; it refuses
 *    for an intent with no apps — then we bow out again). We remember the word's window
 *    bounds (the cancel zone) and the finger's window position (where the fan is drawn).
 * 4. **Track.** From now on every event is read in `Initial` and **every change is
 *    consumed**. Each move is converted to window coordinates and hit-tested against the
 *    pills' window bounds (recorded by the fan's layout, see `FanOverlay`). The word itself
 *    is a cancel zone: sliding back onto it clears the highlight. A changed highlight is sent
 *    to the ViewModel and, when it lands on a pill, plays one haptic tick.
 * 5. **Release.** On a real up: highlighted pill → `onFanRelease(origin = that pill's
 *    bounds)` launches it through `LaunchApp`; no highlight → the same call cancels.
 * 6. **Cancel.** The system can take the pointer away (a system gesture, a dialog, the
 *    window losing focus). Compose then delivers a synthetic "all pointers up" event whose
 *    changes are **already consumed** — that's how we tell it from a real lift, which reaches
 *    us unconsumed in `Initial`. A cancel never launches. If the coroutine itself is cancelled
 *    (Home leaves the composition mid-gesture), a `finally` closes the fan.
 *
 * ─── Timing: why 300 ms and not `viewConfiguration.longPressTimeoutMillis` ─────────────
 *
 * The platform long-press timeout is 400 ms (500 ms before Android 12). The spec asks for
 * ~300 ms: the fan should feel instant, and 300 ms is still well above a tap (≈ 100 ms), so
 * taps keep opening intent detail. But the system timeout is also the accessibility
 * "Touch & hold delay" (Short / Medium 1 s / Long 1.5 s): someone who picked a longer delay
 * needs more time to lift a tap, and a 300 ms fan would hijack their taps. So: 300 ms while
 * the system timeout is a platform default (≤ 500 ms), otherwise the user's own longer delay
 * ([fanHoldMillis]). The clock's long-press (D30) follows the same "never shorter than the
 * user asked" rule.
 *
 * ─── Edges ─────────────────────────────────────────────────────────────────────────────
 *
 * The gesture always *starts* on an intent word, which sits inside Home's 24dp gutter and
 * the safe-drawing insets, so a down never lands in the system back-gesture zone
 * (`WindowInsets.systemGestures`), and once a gesture has started inside the app the system
 * doesn't turn it into Back. The fan pills themselves are kept out of the
 * `safeDrawing ∪ systemGestures` insets (see `FanOverlay`), so neither the pills nor the
 * finger that follows them ever sit on the edge strip.
 *
 * ─── Accessibility ─────────────────────────────────────────────────────────────────────
 *
 * The gesture is an enhancement: a tap always opens intent detail, which is what TalkBack
 * users use. TalkBack's double-tap-and-hold *does* deliver a held touch to the word, so it
 * opens the fan — but lifting without sliding lands on no pill, which cancels and removes
 * the overlay; the user is never stuck. The overlay has no semantics nodes while closed.
 */

/** The spec's hold time before the fan opens (§6.5): instant-feeling, but longer than a tap. */
internal const val FAN_HOLD_MILLIS = 300L

/**
 * The longest *platform default* long-press timeout (500 ms before Android 12, 400 ms since).
 * A larger system value means the user chose a longer "Touch & hold delay".
 */
private const val PLATFORM_DEFAULT_LONG_PRESS_MAX_MILLIS = 500L

/**
 * How long a finger must rest on an intent word before the fan opens: [FAN_HOLD_MILLIS],
 * unless the user set a longer system "Touch & hold delay" — then theirs (see the file
 * header, "Timing").
 */
internal fun fanHoldMillis(systemLongPressMillis: Long): Long =
    if (systemLongPressMillis > PLATFORM_DEFAULT_LONG_PRESS_MAX_MILLIS) systemLongPressMillis else FAN_HOLD_MILLIS

/** Where the open fan is anchored, in window pixels. */
internal data class FanAnchor(
    /** The held word's bounds: the fan grows upward from its top; sliding back onto it cancels. */
    val wordBounds: Rect,
    /** Where the finger was when the fan opened: the pills are staggered around its x. */
    val press: Offset,
)

/**
 * The UI half of press-slide-release: geometry and pointer bookkeeping. The ViewModel half
 * (which apps, which is highlighted, what release does) is `HomeViewModel.fan` / `onFan*`.
 *
 * Flutter analogy: the state object of a `StatefulWidget` that both the custom recognizer
 * and the overlay painter read — the anchor is real Compose state (the overlay recomposes
 * when it changes); the pill bounds and the word coordinates are plain fields, because
 * they change on every layout and only the gesture code reads them.
 *
 * Create it with [rememberPressSlideReleaseState]; attach it with [pressSlideRelease].
 */
@Stable
internal class PressSlideReleaseState(
    private val onOpen: State<(ReachIntent) -> Boolean>,
    private val onHighlight: State<(Int?) -> Unit>,
    private val onRelease: State<(LaunchOrigin?) -> Unit>,
    private val onCancel: State<() -> Unit>,
    private val tick: State<() -> Unit>,
    private val pillGapPx: Float,
    private val hitSlopPx: Float,
) {
    /** The open fan's anchor, or null while closed. Compose state: the overlay reads it. */
    var anchor: FanAnchor? by mutableStateOf(null)
        private set

    /** Each word's latest layout coordinates (to map pointer positions to the window). */
    internal val wordCoordinates: MutableMap<ReachIntent, LayoutCoordinates> = mutableMapOf()

    /** The fan overlay's top-left in window pixels; maps window positions into the overlay. */
    internal var overlayOrigin: Offset = Offset.Zero

    private var pills: List<Rect> = emptyList()
    private var highlighted: Int? = null

    /** Opens the fan for [intent]; false (and nothing changes) if the ViewModel refuses. */
    fun open(
        intent: ReachIntent,
        wordBounds: Rect,
        press: Offset,
    ): Boolean {
        if (!onOpen.value(intent)) return false
        pills = emptyList()
        highlighted = null
        anchor = FanAnchor(wordBounds, press)
        return true
    }

    /**
     * Records the placed pills' window bounds, nearest first. Called by the fan's layout on
     * every placement; pills that didn't fit above the finger are simply not in the list.
     */
    fun recordPills(bounds: List<Rect>) {
        pills = bounds
    }

    /** The finger moved to [windowPosition]: update the highlight, tick on a new pill. */
    fun moveTo(windowPosition: Offset) {
        val index = hitTest(windowPosition)
        if (index == highlighted) return
        highlighted = index
        onHighlight.value(index)
        if (index != null) tick.value()
    }

    /** A real lift: launch the highlighted pill (from its bounds) or cancel. Closes the fan. */
    fun release() {
        if (anchor == null) return
        val origin = highlighted?.let(pills::getOrNull)?.toLaunchOrigin()
        close()
        onRelease.value(origin)
    }

    /** The gesture was cancelled: close without launching. Safe to call when already closed. */
    fun cancel() {
        if (anchor == null) return
        close()
        onCancel.value()
    }

    /**
     * Which pill [windowPosition] is over, or null. Forgiving on purpose — a thumb sliding up
     * drifts sideways: each pill owns a horizontal band (its height plus half the gap to its
     * neighbours) across the whole fan's width plus [hitSlopPx] on both sides. The held word
     * is always "no pill" (the cancel zone), and so is anything outside the fan.
     */
    internal fun hitTest(windowPosition: Offset): Int? {
        val anchor = anchor ?: return null
        if (pills.isEmpty() || anchor.wordBounds.contains(windowPosition)) return null
        val left = pills.minOf { it.left } - hitSlopPx
        val right = pills.maxOf { it.right } + hitSlopPx
        if (windowPosition.x !in left..right) return null
        val halfGap = pillGapPx / 2
        return pills.indexOfFirst { windowPosition.y in (it.top - halfGap)..(it.bottom + halfGap) }.takeIf { it >= 0 }
    }

    private fun close() {
        anchor = null
        pills = emptyList()
        highlighted = null
    }
}

/** Gap between fan pills; also used by the hit-test's bands. */
internal val FanPillGap = 10.dp

/** Extra horizontal reach of the hit-test beyond the fan's pills (thumbs drift sideways). */
private val FanHitSlop = 24.dp

/**
 * Remembers a [PressSlideReleaseState] wired to the ViewModel's fan callbacks and the
 * haptic tick. The callbacks are read through `rememberUpdatedState`, so the state object
 * survives recompositions while always calling the latest lambdas.
 */
@Composable
internal fun rememberPressSlideReleaseState(
    onOpen: (ReachIntent) -> Boolean,
    onHighlight: (Int?) -> Unit,
    onRelease: (LaunchOrigin?) -> Unit,
    onCancel: () -> Unit,
): PressSlideReleaseState {
    val open = rememberUpdatedState(onOpen)
    val highlight = rememberUpdatedState(onHighlight)
    val release = rememberUpdatedState(onRelease)
    val cancel = rememberUpdatedState(onCancel)
    val tick = rememberUpdatedState(rememberHapticTick())
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            PressSlideReleaseState(open, highlight, release, cancel, tick, FanPillGap.toPx(), FanHitSlop.toPx())
        }
    }
}

/**
 * Attaches press-slide-release to the word of [intent]. Pass it to
 * `HomeScreen(wordModifier = { intent -> Modifier.pressSlideRelease(intent, state) })`;
 * `IntentWordRow` applies it *outside* its click handling, which is what lets the
 * `Initial`-pass consumption below suppress the tap once the fan is open.
 */
internal fun Modifier.pressSlideRelease(
    intent: ReachIntent,
    state: PressSlideReleaseState,
): Modifier =
    onGloballyPositioned { state.wordCoordinates[intent] = it }
        .pointerInput(intent, state) {
            val hold = fanHoldMillis(viewConfiguration.longPressTimeoutMillis)
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                // 1. Down — even if the word's click already marked it as used (see header).
                val down = awaitFirstDown(requireUnconsumed = false)
                // 2. Hold: the block returns (non-null) as soon as the gesture belongs to
                //    someone else; null means the timeout fired with the finger still in place.
                val gaveUp = withTimeoutOrNull(hold) { awaitGiveUp(down, slop) }
                if (gaveUp != null) return@awaitEachGesture
                // 3. Open.
                val coordinates = state.wordCoordinates[intent]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val press = coordinates.localToWindow(currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: down.position)
                if (!state.open(intent, coordinates.boundsInWindow(), press)) return@awaitEachGesture
                // 4.–6. Track until release or cancel.
                trackFan(down.id, coordinates, state)
            }
        }

/**
 * Watches the held pointer (without consuming anything) and returns as soon as the press
 * is *not* a hold: lifted, moved beyond [slop], consumed by another detector (e.g. the
 * scroll), or joined by a second finger. Never returns on its own otherwise — the caller's
 * timeout ends it.
 */
private suspend fun AwaitPointerEventScope.awaitGiveUp(
    down: PointerInputChange,
    slop: Float,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        val otherFinger = event.changes.any { it.id != down.id && it.pressed }
        if (!change.pressed || change.isConsumed || otherFinger) return
        if ((change.position - down.position).getDistance() > slop) return
    }
}

/**
 * Follows [pointer] while the fan is open, consuming every change in the `Initial` pass so
 * nothing below or above us (the word's click, Home's scroll and swipes) reacts. Ends with
 * exactly one of [PressSlideReleaseState.release] or [PressSlideReleaseState.cancel].
 */
private suspend fun AwaitPointerEventScope.trackFan(
    pointer: PointerId,
    word: LayoutCoordinates,
    state: PressSlideReleaseState,
) {
    var finished = false
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointer }
            // Read before we consume: a system cancel arrives *already consumed* (header, step 6).
            val cancelledBySystem = change?.isConsumed == true
            event.changes.forEach { it.consume() }
            when {
                change == null || cancelledBySystem -> {
                    state.cancel()
                    finished = true
                    return
                }

                !change.pressed -> {
                    state.release()
                    finished = true
                    return
                }

                word.isAttached -> {
                    state.moveTo(word.localToWindow(change.position))
                }
            }
        }
    } finally {
        // The coroutine was cancelled mid-gesture (Home left the composition, keys changed).
        if (!finished) state.cancel()
    }
}
