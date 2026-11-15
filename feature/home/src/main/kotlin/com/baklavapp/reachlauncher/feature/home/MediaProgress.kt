package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.model.MediaState
import java.util.Locale

/*
 * Progress maths for the Now Playing card, pure so it is unit-tested without a clock.
 *
 * Why extrapolate: a media session reports its position only when its state changes (play,
 * pause, seek, new track), together with *when* that was on the elapsed-realtime clock.
 * Between reports the player just plays on, so "position now" = reported position + time
 * since the report × playback speed. Polling the session every second would cost an IPC call
 * per tick for the same answer.
 *
 * Flutter analogy: like computing an `AnimationController`'s value from a start time instead
 * of asking the platform for every frame.
 */

/**
 * The position at [nowElapsedMillis] (`SystemClock.elapsedRealtime()`), clamped to
 * `0..durationMillis` when the duration is known. A paused session stays where it is.
 */
internal fun MediaState.positionAt(nowElapsedMillis: Long): Long {
    val advanced =
        if (isPlaying && positionUpdatedAtMillis > 0) {
            positionMillis + ((nowElapsedMillis - positionUpdatedAtMillis).coerceAtLeast(0) * playbackSpeed).toLong()
        } else {
            positionMillis
        }
    return if (durationMillis > 0) advanced.coerceIn(0, durationMillis) else advanced.coerceAtLeast(0)
}

/** Fraction played (0..1) at [nowElapsedMillis], or null when the duration is unknown (live streams). */
internal fun MediaState.progressAt(nowElapsedMillis: Long): Float? =
    if (durationMillis > 0) positionAt(nowElapsedMillis).toFloat() / durationMillis else null

/** "3:07" or "1:02:03" — the spoken/visible form of a position or duration. */
internal fun formatMediaTime(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    // Locale.ROOT: digits stay ASCII and lint's implicit-locale check is satisfied.
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
