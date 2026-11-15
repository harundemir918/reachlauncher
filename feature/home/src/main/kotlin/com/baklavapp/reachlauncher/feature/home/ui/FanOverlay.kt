package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baklavapp.reachlauncher.core.designsystem.component.Monogram
import com.baklavapp.reachlauncher.core.designsystem.component.MonogramStyle
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.home.FanState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The press-slide-release fan: app pills stacked upward from the held word, beside the
 * thumb (spec §6.5). The gesture logic is in PressSlideRelease.kt; this file only draws.
 *
 * Flutter analogy: a `Stack` overlay with a `CustomMultiChildLayout` — the delegate measures
 * the pills, then positions them from the finger upward (≈ `layoutChild` + `positionChild`).
 * Compose's `Layout { measurables, constraints -> … }` is that delegate, inline.
 *
 * Layout rules:
 * - pill 0 (the most-used app) sits just above the held word, nearest the finger; each next
 *   pill sits higher and [FanStagger] further away from the thumb's screen edge — the arc a
 *   thumb pivoting from the bottom corner naturally follows. Mirrored for `ThumbSide.RIGHT`.
 * - Pills are kept inside `safeDrawing ∪ systemGestures` (+ a small margin), so none sits on
 *   the status bar, a cutout or the back-gesture edge strips.
 * - Only as many pills as fit between the word and the top inset are placed (at 200% font the
 *   pills grow taller and fewer fit). Unplaced pills aren't drawn, hit-tested or in semantics.
 * - Entrance: staggered fade + small rise, skipped with "Remove animations". No exit
 *   animation: the overlay (and its semantics) is gone the moment the fan closes.
 */

private val FanPillMinHeight = 52.dp
private val FanPillMinWidth = 168.dp
private val FanPillMaxWidth = 300.dp
private val FanPillCorner = 26.dp
private val FanPillPadding = 14.dp
private val FanPillContentGap = 10.dp
private val FanMonogramSize = 30.dp
private val FanWordGap = 8.dp
private val FanStagger = 16.dp
private val FanLeadIn = 48.dp
private val FanEdgeMargin = 8.dp
private val FanHalo = 6.dp
private val FanRise = 12.dp
private const val HALO_ALPHA = 0.32f
private const val STAGGER_MILLIS = 30L
private const val ENTER_MILLIS = 160

/** Test tag of every fan pill (tests find pills by it; intent chips are `selected` too). */
internal const val FAN_PILL_TEST_TAG = "fan-pill"

/**
 * Draws the open [fan] anchored at [state]'s anchor, and reports the placed pills' window
 * bounds back to [state] for hit-testing. Put it over Home, filling the window. Draws (and
 * exposes to accessibility) nothing while the fan is closed.
 */
@Composable
internal fun FanOverlay(
    fan: FanState?,
    state: PressSlideReleaseState,
    thumbSide: ThumbSide,
    modifier: Modifier = Modifier,
) {
    val anchor = state.anchor
    val insets = WindowInsets.safeDrawing.union(WindowInsets.systemGestures)
    val reducedMotion = rememberReducedMotion()
    val shown = fan?.takeIf { anchor != null }
    Layout(
        modifier = modifier.onGloballyPositioned { state.overlayOrigin = it.positionInWindow() },
        content = {
            shown?.apps?.forEachIndexed { index, app ->
                // Keyed by intent + app: re-opening restarts the entrance animation.
                key(shown.intent, app.key) {
                    FanPill(app = app, highlighted = shown.highlighted == index, index = index, reducedMotion = reducedMotion)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (anchor == null || measurables.isEmpty()) {
            state.recordPills(emptyList())
            return@Layout layout(width, height) {}
        }
        val origin = state.overlayOrigin
        val margin = FanEdgeMargin.roundToPx()
        val safeLeft = (insets.getLeft(this, layoutDirection) - origin.x).roundToInt().coerceAtLeast(0) + margin
        val safeRight = width - (insets.getRight(this, layoutDirection) + margin)
        val safeTop = (insets.getTop(this) - origin.y).roundToInt().coerceAtLeast(0) + margin
        val wordTop = (anchor.wordBounds.top - origin.y).roundToInt()
        val pressX = (anchor.press.x - origin.x).roundToInt()
        val available = max(0, safeRight - safeLeft)
        val maxPill = minOf(FanPillMaxWidth.roundToPx(), available)
        val pillWidth =
            measurables
                .maxOf { it.maxIntrinsicWidth(FanPillMinHeight.roundToPx()) }
                .coerceIn(minOf(FanPillMinWidth.roundToPx(), maxPill), maxPill)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(pillWidth)) }

        val gap = FanPillGap.roundToPx()
        val stagger = FanStagger.roundToPx()
        val leadIn = FanLeadIn.roundToPx()
        val positions = mutableListOf<Pair<Int, Int>>()
        var bottom = wordTop - FanWordGap.roundToPx()
        for ((index, placeable) in placeables.withIndex()) {
            val y = bottom - placeable.height
            if (y < safeTop) break // Doesn't fit above the finger: stop here (200% font, high word).
            val x =
                if (thumbSide == ThumbSide.LEFT) {
                    pressX - leadIn + index * stagger
                } else {
                    pressX + leadIn - index * stagger - placeable.width
                }
            positions += x.coerceIn(safeLeft, max(safeLeft, safeRight - placeable.width)) to y
            bottom = y - gap
        }
        state.recordPills(
            positions.mapIndexed { i, (x, y) ->
                Rect(Offset(x + origin.x, y + origin.y), Size(placeables[i].width.toFloat(), placeables[i].height.toFloat()))
            },
        )
        layout(width, height) {
            positions.forEachIndexed { i, (x, y) -> placeables[i].place(x, y) }
        }
    }
}

/**
 * One fan pill: monogram + app name. Highlighted = accent fill, `onAccent` content and a
 * translucent accent halo (no gradient, no shadow — spec §5.4).
 *
 * Semantics: one node, the app name, `selected` while highlighted. It exists only while the
 * fan is open; TalkBack users open apps from intent detail instead (spec §6.5).
 */
@Composable
private fun FanPill(
    app: CatalogApp,
    highlighted: Boolean,
    index: Int,
    reducedMotion: Boolean,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(FanPillCorner)
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            delay(index * STAGGER_MILLIS)
            progress.animateTo(1f, tween(ENTER_MILLIS))
        }
    }
    val halo = colors.accent.copy(alpha = HALO_ALPHA)
    Row(
        modifier =
            Modifier
                // graphicsLayer reads the animation in the draw phase: no recomposition per frame.
                .graphicsLayer {
                    alpha = progress.value
                    translationY = (1f - progress.value) * FanRise.toPx()
                }.drawBehind {
                    if (highlighted) {
                        val spread = FanHalo.toPx()
                        drawRoundRect(
                            color = halo,
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + 2 * spread, size.height + 2 * spread),
                            cornerRadius = CornerRadius(FanPillCorner.toPx() + spread),
                        )
                    }
                }.background(if (highlighted) colors.accent else colors.surface, shape)
                .border(ReachComponentDimens.hairline, if (highlighted) colors.accent else colors.line, shape)
                .clearAndSetSemantics {
                    contentDescription = app.label
                    selected = highlighted
                    testTag = FAN_PILL_TEST_TAG
                }.heightIn(min = FanPillMinHeight)
                .padding(horizontal = FanPillPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FanPillContentGap),
    ) {
        Monogram(
            label = app.label,
            size = FanMonogramSize,
            style = if (highlighted) MonogramStyle.OnAccent else MonogramStyle.Default,
            textStyle = ReachTheme.typography.monogram.copy(fontSize = 11.sp),
            dimmed = app.isProfilePaused && !highlighted,
        )
        Text(
            text = app.label,
            style = ReachTheme.typography.appName,
            color = if (highlighted) colors.onAccent else colors.fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
