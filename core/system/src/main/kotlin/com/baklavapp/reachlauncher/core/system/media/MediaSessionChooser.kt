package com.baklavapp.reachlauncher.core.system.media

import android.media.session.PlaybackState
import com.baklavapp.reachlauncher.core.model.MediaState

/**
 * One active media session, reduced to plain values read from its `MediaController`
 * (`getPlaybackState()`, `getMetadata()`), so choosing and mapping is JVM-testable.
 *
 * @property state a `PlaybackState.STATE_*` constant; `STATE_NONE` when the session has none.
 * @property positionMillis `PlaybackState.getPosition()`; `PLAYBACK_POSITION_UNKNOWN` (-1) allowed.
 * @property updatedAtMillis `PlaybackState.getLastPositionUpdateTime()`, elapsed-realtime clock.
 * @property durationMillis `METADATA_KEY_DURATION`; 0 or negative when unknown (live radio).
 */
data class SessionSnapshot(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val state: Int,
    val positionMillis: Long,
    val updatedAtMillis: Long,
    val speed: Float,
    val durationMillis: Long,
)

/**
 * Which session Home shows, and how it becomes a [MediaState] (spec §6.3, §7.2).
 *
 * Rules, in order:
 * 1. Only sessions with a title and a "live" state are candidates: playing (or about to —
 *    buffering, connecting, seeking, skipping) or paused. Stopped, errored and state-less
 *    sessions are left out, so a music app that merely *has* a session doesn't keep an empty
 *    player on the Listen row.
 * 2. Playing beats paused: if a podcast is paused and music plays, the music is shown.
 * 3. Ties keep the system's order — `getActiveSessions` lists sessions by priority, the most
 *   recently active first.
 */
object MediaSessionChooser {
    private val playingStates =
        setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )

    /** Whether [state] counts as playing: the button shows "Pause" and progress advances. */
    fun isPlaying(state: Int): Boolean = state in playingStates

    /** Index into [sessions] of the session to show, or null when none qualifies. */
    fun choose(sessions: List<SessionSnapshot>): Int? {
        val candidates =
            sessions.withIndex().filter { (_, s) ->
                !s.title.isNullOrBlank() && (isPlaying(s.state) || s.state == PlaybackState.STATE_PAUSED)
            }
        return (candidates.firstOrNull { isPlaying(it.value.state) } ?: candidates.firstOrNull())?.index
    }

    /** Maps the chosen [session] to the contract type; [sourceLabel] is its app's name. */
    fun toMediaState(
        session: SessionSnapshot,
        sourceLabel: String,
    ): MediaState =
        MediaState(
            title = session.title.orEmpty(),
            artist = session.artist?.takeIf { it.isNotBlank() },
            sourcePackage = session.packageName,
            sourceLabel = sourceLabel,
            isPlaying = isPlaying(session.state),
            positionMillis = session.positionMillis.coerceAtLeast(0),
            durationMillis = session.durationMillis.coerceAtLeast(0),
            positionUpdatedAtMillis = session.updatedAtMillis,
            playbackSpeed = session.speed,
        )
}
