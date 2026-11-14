package com.baklavapp.reachlauncher.core.designsystem.util

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode

/*
 * Motion and haptics helpers (spec §6.5 haptic ticks, §6.10 "respect remove animations").
 *
 * Flutter analogy: `MediaQuery.of(context).disableAnimations` and `HapticFeedback.selectionClick()`.
 * Compose has no built-in "disable animations" flag in the tree, so [rememberReducedMotion]
 * reads the system setting itself and keeps it up to date.
 */

/**
 * True while the system "Remove animations" setting is on (Settings → Accessibility →
 * Color and motion), which sets `Settings.Global.ANIMATOR_DURATION_SCALE` to 0. The
 * developer option "Animator duration scale → Animation off" sets the same value.
 *
 * Why you still need this although Compose animations already obey that scale (a 0 scale
 * makes them jump straight to their end value): *what* animates should change too. With
 * reduced motion, prefer showing the gesture fan at once instead of staggering it, a
 * crossfade instead of a slide, and no looping/decorative motion at all.
 *
 * Re-reads the value when the setting changes (a `ContentObserver`, unregistered when this
 * leaves the composition). Returns false in Android Studio previews.
 *
 * ```kotlin
 * val reducedMotion = rememberReducedMotion()
 * val stagger = if (reducedMotion) 0 else 30 // ms between fan rows
 * ```
 */
@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(isAnimationOff(resolver)) }
    // ≈ initState/dispose in a StatefulWidget: subscribe on enter, unsubscribe on leave.
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = isAnimationOff(resolver)
                }
            }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun isAnimationOff(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Returns a function that plays one light haptic "tick" — the feedback for "the highlighted
 * item changed" in the press-slide-release fan and the A–Z letter rail (spec §6.5, §6.7).
 *
 * Uses [HapticFeedbackType.SegmentTick], Compose's semantic type for stepping through
 * discrete segments; it maps to the platform's segment-tick constant where the device
 * supports it and falls back gracefully elsewhere. The device-wide "touch feedback"
 * setting is respected by the platform, so no extra check is needed here.
 *
 * ```kotlin
 * val tick = rememberHapticTick()
 * LaunchedEffect(highlightedIndex) { if (highlightedIndex != null) tick() }
 * ```
 */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
}
