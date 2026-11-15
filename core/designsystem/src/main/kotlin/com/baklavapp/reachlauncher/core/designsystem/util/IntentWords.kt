package com.baklavapp.reachlauncher.core.designsystem.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.model.ReachIntent

/*
 * The six intent words ("Talk"/"Konuş" …), shared by Home, intent detail and All Apps' menu.
 * They used to be duplicated as `home_intent_*` and `allapps_intent_*`; one copy here means a
 * translation fix lands everywhere at once (M5 cleanup, D70).
 *
 * Flutter analogy: a getter on your generated `AppLocalizations` that every feature reads,
 * instead of each feature keeping its own ARB entries for the same word.
 */

/** String resource of [ReachIntent]'s word. */
@StringRes
fun ReachIntent.wordRes(): Int =
    when (this) {
        ReachIntent.TALK -> R.string.ds_intent_talk
        ReachIntent.LISTEN -> R.string.ds_intent_listen
        ReachIntent.GO -> R.string.ds_intent_go
        ReachIntent.MAKE -> R.string.ds_intent_make
        ReachIntent.PLAY -> R.string.ds_intent_play
        ReachIntent.TOOLS -> R.string.ds_intent_tools
    }

/** The localised word of [intent]. */
@Composable
@ReadOnlyComposable
fun intentWord(intent: ReachIntent): String = stringResource(intent.wordRes())
