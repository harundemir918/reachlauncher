package com.baklavapp.reachlauncher.feature.allapps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.rememberHapticTick
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.allapps.R
import com.baklavapp.reachlauncher.feature.allapps.RailLetter
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The A–Z letter rail (spec §6.7): scrub with the thumb to jump through the word wall.
 *
 * Flutter analogy: a `GestureDetector(onVerticalDragDown:, onVerticalDragUpdate:,
 * onVerticalDragEnd:)` around a `Column` of letters, where each update maps the finger's
 * `localPosition.dy` to a letter and calls `ScrollController.jumpTo` / `animateTo`. The
 * bubble is a `Positioned` child of a `Stack` that follows the finger.
 */

/** Width of the rail's touch strip. Letters are centred in it. */
internal val RailWidth = 32.dp

private val BubbleSize = 72.dp
private val BubbleGap = 12.dp
private val BubbleCorner = 36.dp
private val BubblePointCorner = 8.dp
private val BubbleGlyph = 36.dp
private val SkippedDotSize = 3.dp

/** Test tag of the bubble's letter, so UI tests can read which letter is under the finger. */
internal const val RAIL_BUBBLE_TAG = "allapps:rail-bubble"

/** Test tag of the rail's touch strip. */
internal const val RAIL_TAG = "allapps:rail"

/**
 * The letter rail: a vertical strip of mono letters on the thumb side.
 *
 * How it works, step by step:
 * 1. **Layout.** The strip is a `Column` whose [letters] each get an equal slot
 *    (`weight(1f)`), so slot *i* spans `[i·h/n, (i+1)·h/n)` of the rail height *h*. That
 *    makes the y → letter mapping a division, independent of how big the letters are drawn.
 * 2. **Large fonts.** At 200 % font the letters may be taller than their slot. A text
 *    measurer tells how tall one letter is; if it doesn't fit, only every *k*-th letter is
 *    drawn (k = ⌈letter height / slot height⌉) and the others become small dots. All letters
 *    stay reachable by scrubbing — the bubble names the one under the finger.
 * 3. **Touch.** `pointerInput` + `awaitEachGesture` runs once per gesture: wait for the
 *    first finger down (and consume it so nothing behind the rail reacts), then loop over
 *    pointer events until that finger lifts. Every position goes through [railLetterAt].
 * 4. **Skip empty letters.** [railLetterAt] picks the nearest letter that has apps, so
 *    scrubbing over a letter without apps keeps the bubble meaningful instead of jumping to
 *    nothing. Letters without apps are drawn dimmed.
 * 5. **Feedback.** When the resolved letter changes: a haptic tick ([rememberHapticTick]),
 *    the accent bubble moves to the finger and shows the letter, and [onLetterChange] tells
 *    the screen, which scrolls the word wall (animated unless "Remove animations" is on) and
 *    highlights the group. On lift (or cancel) the bubble disappears and `onLetterChange(null)`.
 * 6. **Back gesture.** The screen places the rail inside `WindowInsets.systemGestures`, so it
 *    never starts inside the system back-swipe zone; `systemGestureExclusion()` additionally
 *    claims the strip for devices whose real zone is wider than the reported inset.
 * 7. **Accessibility.** The rail is an enhancement: TalkBack users get it as *one* control
 *    ("Letter index") with a "Jump to S" custom action per letter that has apps, instead of
 *    ~30 tiny targets. The word wall's letter labels are headings too, so heading
 *    navigation also works.
 *
 * @param letters the index letters (with [RailLetter.groupIndex] null for empty ones).
 * @param thumbSide the physical edge the rail is on; the bubble appears on the other side.
 * @param activeLetter the letter currently under the finger (owned by the screen).
 * @param onLetterChange called with the new letter while scrubbing, and null when the finger lifts.
 * @param onJump TalkBack's "Jump to X" action.
 */
@Composable
internal fun LetterRail(
    letters: List<RailLetter>,
    thumbSide: ThumbSide,
    activeLetter: RailLetter?,
    onLetterChange: (RailLetter?) -> Unit,
    onJump: (RailLetter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val tick = rememberHapticTick()
    // pointerInput(Unit) is started once; these keep its long-lived coroutine reading the
    // *current* values instead of the ones from the first composition (≈ reading
    // `widget.x` in a State instead of capturing it in initState).
    val currentLetters by rememberUpdatedState(letters)
    val currentOnChange by rememberUpdatedState(onLetterChange)
    val currentTick by rememberUpdatedState(tick)
    var fingerY by remember { mutableFloatStateOf(0f) }
    var lastLetter by remember { mutableStateOf<RailLetter?>(null) }

    val railLabel = stringResource(R.string.allapps_rail)
    val jumpActions =
        letters.filter { it.hasApps }.map { letter ->
            CustomAccessibilityAction(stringResource(R.string.allapps_rail_jump, letter.letter)) {
                onJump(letter)
                true
            }
        }

    Box(modifier = modifier.width(RailWidth).fillMaxHeight()) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .testTag(RAIL_TAG)
                    .systemGestureExclusion()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            fun update(y: Float) {
                                fingerY = y
                                val letter = railLetterAt(currentLetters, y, size.height.toFloat())
                                if (letter != null && letter != lastLetter) {
                                    lastLetter = letter
                                    currentTick()
                                    currentOnChange(letter)
                                }
                            }
                            try {
                                update(down.position.y)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    update(change.position.y)
                                }
                            } finally {
                                // Also on cancellation (e.g. the rail leaves the composition).
                                lastLetter = null
                                currentOnChange(null)
                            }
                        }
                    }.clearAndSetSemantics {
                        contentDescription = railLabel
                        customActions = jumpActions
                    },
        ) {
            RailLetters(letters, activeLetter, slotHeightPx = constraints.maxHeight.toFloat() / letters.size)
        }
        if (activeLetter != null) {
            Bubble(activeLetter.letter, thumbSide, fingerY)
        }
    }
}

/** Draws the letters (or dots for skipped ones) in equal slots. */
@Composable
private fun RailLetters(
    letters: List<RailLetter>,
    activeLetter: RailLetter?,
    slotHeightPx: Float,
) {
    val colors = ReachTheme.colors
    val style = ReachTheme.typography.label
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Height of one drawn letter at the current font scale.
    val letterHeightPx =
        remember(style, density) {
            measurer
                .measure("W", style)
                .size.height
                .toFloat()
        }
    val stride = if (slotHeightPx <= 0f) 1 else ceil(letterHeightPx / slotHeightPx).toInt().coerceAtLeast(1)
    val dotFits = with(density) { slotHeightPx >= SkippedDotSize.toPx() * 2 }
    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth().clipToBounds()) {
        letters.forEachIndexed { i, letter ->
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val color =
                    when {
                        letter == activeLetter -> colors.accentInk

                        letter.hasApps -> colors.groupDim

                        // Not text-safe on purpose: an inactive, non-focusable letter (D18).
                        else -> colors.dim
                    }
                when {
                    i % stride == 0 -> {
                        Text(
                            text = letter.letter,
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                    }

                    dotFits -> {
                        Box(Modifier.size(SkippedDotSize).background(color, CircleShape))
                    }
                }
            }
        }
    }
}

/**
 * The 72dp accent bubble beside the finger, its pointed corner towards the rail (mockup).
 * The glyph is sized in dp, not sp: it is already ~3× body text, and scaling it with the
 * font would overflow the bubble at 200 %. TalkBack never sees it (the rail speaks instead).
 */
@Composable
private fun Bubble(
    letter: String,
    thumbSide: ThumbSide,
    fingerY: Float,
) {
    val colors = ReachTheme.colors
    val density = LocalDensity.current
    val shape =
        when (thumbSide) {
            // Rail on the right → bubble to its left, pointing bottom-right (as in the mockup).
            ThumbSide.RIGHT -> AbsoluteRoundedCornerShape(BubbleCorner, BubbleCorner, BubblePointCorner, BubbleCorner)

            ThumbSide.LEFT -> AbsoluteRoundedCornerShape(BubbleCorner, BubbleCorner, BubbleCorner, BubblePointCorner)
        }
    val glyphSize = with(density) { BubbleGlyph.toSp() }
    Box(
        modifier =
            Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .offset {
                    val x =
                        when (thumbSide) {
                            ThumbSide.RIGHT -> -(BubbleSize + BubbleGap).roundToPx()
                            ThumbSide.LEFT -> (RailWidth + BubbleGap).roundToPx()
                        }
                    // Bottom of the bubble (its point) at the finger, like a speech bubble;
                    // never above the rail's top, where it would slide under the status bar.
                    IntOffset(x, (fingerY - BubbleSize.toPx()).coerceAtLeast(0f).roundToInt())
                }.size(BubbleSize)
                .background(colors.accent, shape)
                .semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = ReachTheme.typography.intentWord.copy(fontSize = glyphSize, lineHeight = glyphSize),
            color = colors.onAccent,
            modifier = Modifier.testTag(RAIL_BUBBLE_TAG),
        )
    }
}

/**
 * Maps a finger position [y] on a rail of [height] to a letter: the slot under the finger,
 * or — when that letter has no apps — the nearest letter that has (the earlier one on a
 * tie). Positions above or below the rail clamp to its ends. Null when no letter has apps.
 *
 * Pure so it can be unit-tested without a screen.
 */
internal fun railLetterAt(
    letters: List<RailLetter>,
    y: Float,
    height: Float,
): RailLetter? {
    if (letters.isEmpty() || height <= 0f) return null
    val slot = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
    for (distance in 0..letters.size) {
        letters.getOrNull(slot - distance)?.takeIf { it.hasApps }?.let { return it }
        letters.getOrNull(slot + distance)?.takeIf { it.hasApps }?.let { return it }
    }
    return null
}
