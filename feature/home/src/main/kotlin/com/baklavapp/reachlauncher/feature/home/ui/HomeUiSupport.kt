package com.baklavapp.reachlauncher.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.home.R
import kotlin.math.roundToInt

/*
 * Small UI helpers shared by the Home surfaces: intent words, Now-row reasons and launch
 * origins.
 *
 * Flutter analogy for the launch origin: like reading `context.findRenderObject()` +
 * `localToGlobal(Offset.zero)` in an `onTap` to know where the tapped widget is. Compose
 * reports it through `onGloballyPositioned`, which we keep in a plain holder (not state)
 * because nothing should recompose when it changes.
 */

/** String resource of an intent's word ("Talk"/"Konuş"). */
@StringRes
internal fun ReachIntent.wordRes(): Int =
    when (this) {
        ReachIntent.TALK -> R.string.home_intent_talk
        ReachIntent.LISTEN -> R.string.home_intent_listen
        ReachIntent.GO -> R.string.home_intent_go
        ReachIntent.MAKE -> R.string.home_intent_make
        ReachIntent.PLAY -> R.string.home_intent_play
        ReachIntent.TOOLS -> R.string.home_intent_tools
    }

/** The localised word of [intent]. */
@Composable
@ReadOnlyComposable
internal fun intentWord(intent: ReachIntent): String = stringResource(intent.wordRes())

/** Natural-case Now-tile reason ("usually around now", "2 unread", "recent"). */
@Composable
@ReadOnlyComposable
internal fun reasonLabel(reason: PredictionReason): String =
    when (reason) {
        PredictionReason.UsuallyAroundNow -> stringResource(R.string.home_now_reason_usual)
        is PredictionReason.Unread -> pluralStringResource(R.plurals.home_now_reason_unread, reason.count, reason.count)
        PredictionReason.Recent -> stringResource(R.string.home_now_reason_recent)
    }

/**
 * Remembers where one tappable element sits in the window, for the launch animation.
 * Deliberately not Compose state: it changes on every scroll frame and nothing reads it
 * until the tap.
 */
@Stable
internal class LaunchOriginHolder {
    private var bounds: Rect = Rect.Zero

    /** Updates the bounds; called from [trackLaunchOrigin]. */
    fun update(rect: Rect) {
        bounds = rect
    }

    /** The bounds as a [LaunchOrigin] in window pixels, or null before the first layout. */
    fun toLaunchOrigin(): LaunchOrigin? =
        bounds.takeUnless { it.isEmpty }?.let {
            LaunchOrigin(it.left.roundToInt(), it.top.roundToInt(), it.width.roundToInt(), it.height.roundToInt())
        }
}

/** A [LaunchOriginHolder] that lives as long as the calling composable. */
@Composable
internal fun rememberLaunchOrigin(): LaunchOriginHolder = remember { LaunchOriginHolder() }

/** Records this element's window bounds into [holder] whenever it moves. */
internal fun Modifier.trackLaunchOrigin(holder: LaunchOriginHolder): Modifier = onGloballyPositioned { holder.update(it.boundsInWindow()) }
