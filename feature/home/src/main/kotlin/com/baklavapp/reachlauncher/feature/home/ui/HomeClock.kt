package com.baklavapp.reachlauncher.feature.home.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.feature.home.R
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The date line and the big clock at the top of Home (spec §6.3 items 1–2).
 *
 * Flutter analogy: `rememberWallClock()` is what you'd do with a `Timer.periodic` in a
 * StatefulWidget's `initState`/`dispose`; here a `DisposableEffect` registers an Android
 * broadcast receiver on enter and unregisters it on leave. The long-press is a
 * `GestureDetector(onLongPress:)` — Compose's `pointerInput { detectTapGestures(…) }`.
 *
 * Android quirks handled here:
 * - `ACTION_TIME_TICK` is the system's once-a-minute broadcast, sent exactly at the minute
 *   boundary, but only to receivers registered in code and only while the screen is on.
 *   That's precisely when a clock needs updating — no timer drift, no wake-ups at night.
 * - The 12/24-hour switch in system settings sends `ACTION_TIME_CHANGED` (`TIME_SET`);
 *   time-zone changes send `ACTION_TIMEZONE_CHANGED`. Both re-read the clock.
 * - After the screen was off, the first tick may be up to a minute away, so the time is
 *   also re-read on every resume.
 */

/** Spec §6.3: long-press the clock for ~450ms to open Appearance. */
private const val CLOCK_LONG_PRESS_MILLIS = 450L

/** Smallest size the clock may shrink to so "12:59" fits the width at 200% font scale. */
private val ClockMinFontSize = 40.sp

/** One reading of the wall clock plus the user's 12/24-hour preference. */
@Immutable
internal data class ClockReading(
    val now: ZonedDateTime,
    val is24Hour: Boolean,
)

private fun readClock(context: Context) = ClockReading(ZonedDateTime.now(), DateFormat.is24HourFormat(context))

/** The current time, updated on every minute boundary, time change and resume. */
@Composable
internal fun rememberWallClock(): State<ClockReading> {
    val context = LocalContext.current
    val reading = remember(context) { mutableStateOf(readClock(context)) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context,
                    intent: Intent,
                ) {
                    reading.value = readClock(context)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        // Protected system broadcasts still reach a NOT_EXPORTED receiver; no other app can send these.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(context) {
        reading.value = readClock(context)
        onPauseOrDispose { }
    }
    return reading
}

/** "SUN 08 NOV" / "PAZ 08 KAS": weekday, day, month in the current locale, mono and muted. */
@Composable
internal fun HomeDateLine(
    reading: ClockReading,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE dd MMM", locale) }
    MonoLabel(
        text = reading.now.format(formatter),
        style = ReachTheme.typography.count,
        modifier = modifier,
    )
}

/**
 * The 112sp clock. It's a real focusable element (keyboard Enter works too); a long-press
 * of ~450ms — or TalkBack's "Appearance" custom action — calls [onOpenAppearance].
 *
 * The clock shrinks to fit the width (auto-size) instead of wrapping "09:41" over two
 * lines at large font scales.
 */
@Composable
internal fun HomeClock(
    reading: ClockReading,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val shown = remember(reading.is24Hour, locale) { DateTimeFormatter.ofPattern(if (reading.is24Hour) "HH:mm" else "h:mm", locale) }
    val spoken = remember(reading.is24Hour, locale) { spokenFormatter(reading.is24Hour, locale) }
    val actionLabel = stringResource(R.string.home_clock_appearance_action)
    val haptics = LocalHapticFeedback.current
    val open by rememberUpdatedState(onOpenAppearance)
    val baseConfiguration = LocalViewConfiguration.current
    val configuration =
        remember(baseConfiguration) {
            // Never shorter than the user's own "touch & hold delay" accessibility setting.
            val timeout = maxOf(CLOCK_LONG_PRESS_MILLIS, baseConfiguration.longPressTimeoutMillis)
            object : ViewConfiguration by baseConfiguration {
                override val longPressTimeoutMillis: Long get() = timeout
            }
        }
    val style = ReachTheme.typography.clock.copy(color = ReachTheme.colors.fg)
    CompositionLocalProvider(LocalViewConfiguration provides configuration) {
        BasicText(
            text = reading.now.format(shown),
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = ClockMinFontSize, maxFontSize = style.fontSize),
            modifier =
                modifier
                    .clearAndSetSemantics {
                        contentDescription = reading.now.format(spoken)
                        onLongClick(label = actionLabel) {
                            open()
                            true
                        }
                        customActions =
                            listOf(
                                CustomAccessibilityAction(actionLabel) {
                                    open()
                                    true
                                },
                            )
                    }.onKeyEvent { event ->
                        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                        if (isEnter && event.type == KeyEventType.KeyUp) {
                            open()
                            true
                        } else {
                            false
                        }
                    }.focusable()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                open()
                            },
                        )
                    },
        )
    }
}

private fun spokenFormatter(
    is24Hour: Boolean,
    locale: Locale,
): DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
