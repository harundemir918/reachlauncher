package com.baklavapp.reachlauncher.feature.allapps.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.feature.allapps.R
import kotlin.math.roundToInt

/*
 * Small UI helpers: intent words and launch origins.
 *
 * These mirror `:feature:home`'s internal helpers of the same name. Feature modules don't
 * depend on each other (Home depends on All Apps, not the reverse), so the few lines are
 * repeated here rather than moved into `:core:designsystem` by this agent.
 *
 * Flutter analogy for the launch origin: reading `context.findRenderObject()` +
 * `localToGlobal(Offset.zero)` in an `onTap` to know where the tapped widget is.
 */

@StringRes
private fun ReachIntent.wordRes(): Int =
    when (this) {
        ReachIntent.TALK -> R.string.allapps_intent_talk
        ReachIntent.LISTEN -> R.string.allapps_intent_listen
        ReachIntent.GO -> R.string.allapps_intent_go
        ReachIntent.MAKE -> R.string.allapps_intent_make
        ReachIntent.PLAY -> R.string.allapps_intent_play
        ReachIntent.TOOLS -> R.string.allapps_intent_tools
    }

/** The localised word of [intent] ("Talk"/"Konuş"). */
@Composable
@ReadOnlyComposable
internal fun intentWord(intent: ReachIntent): String = stringResource(intent.wordRes())

/**
 * Remembers where one tappable name sits in the window, for the launch animation.
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
