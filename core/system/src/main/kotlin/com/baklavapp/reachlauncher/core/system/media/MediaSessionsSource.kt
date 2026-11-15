package com.baklavapp.reachlauncher.core.system.media

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.source.MediaSessionSource
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.system.notifications.NotificationAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The active media session for the Listen row and the Now Playing card (spec §7.2), via
 * `MediaSessionManager`.
 *
 * Android background for Flutter developers: every music/podcast/video app publishes a
 * *media session* — its "now playing" state plus a remote control. Other apps may read and
 * control sessions only with a *notification listener* that the user enabled, which is why
 * `getActiveSessions` takes our listener's `ComponentName` and throws `SecurityException`
 * without access. So this source reuses notification access — one grant lights up both
 * badges and media.
 *
 * **What it watches** (all callbacks on the main thread, which is also where the producer
 * runs, so the controller list needs no locking):
 * - `OnActiveSessionsChangedListener` — sessions appearing/disappearing.
 * - `MediaController.Callback` on each session — track (metadata) and play/pause/position
 *   (playback state) changes, and the session being destroyed.
 *
 * Each change re-reads every session into a [SessionSnapshot] and lets
 * [MediaSessionChooser] pick the most relevant one (playing first). The position is *not*
 * polled: a session only reports position on state changes, and the UI extrapolates from
 * [MediaState.positionUpdatedAtMillis] while playing.
 *
 * **Commands** go to the controller of the session currently shown, through
 * `MediaController.TransportControls`. Without a shown session (or access) they do nothing.
 *
 * Flutter analogy: the native side of an `audio_service`-style "remote control" plugin: an
 * `EventChannel` stream of now-playing state plus fire-and-forget method calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MediaSessionsSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val access: NotificationAccess,
    ) : MediaSessionSource {
        /** The controller of the session currently emitted; main thread writes, any thread reads. */
        @Volatile
        private var shown: MediaController? = null

        private val labels = ConcurrentHashMap<String, String>()

        override val mediaState: Flow<MediaState?> =
            access.granted
                .flatMapLatest { granted -> if (granted) sessions() else flowOf(null) }
                .distinctUntilChanged()

        private fun sessions(): Flow<MediaState?> =
            callbackFlow {
                val manager = context.getSystemService<MediaSessionManager>()
                if (manager == null) {
                    send(null)
                    awaitClose()
                    return@callbackFlow
                }
                val handler = Handler(Looper.getMainLooper())
                val tracked = mutableListOf<Pair<MediaController, MediaController.Callback>>()

                fun publish() {
                    val controllers = tracked.map { it.first }
                    val snapshots = controllers.map(::snapshotOf)
                    val index = MediaSessionChooser.choose(snapshots)
                    shown = index?.let(controllers::get)
                    trySend(index?.let { MediaSessionChooser.toMediaState(snapshots[it], labelOf(snapshots[it].packageName)) })
                }

                fun track(controllers: List<MediaController>) {
                    tracked.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
                    tracked.clear()
                    controllers.forEach { controller ->
                        val callback =
                            object : MediaController.Callback() {
                                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

                                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

                                override fun onSessionDestroyed() {
                                    tracked.removeAll { it.first === controller }
                                    publish()
                                }
                            }
                        controller.registerCallback(callback, handler)
                        tracked += controller to callback
                    }
                    publish()
                }

                val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> track(controllers.orEmpty()) }
                try {
                    manager.addOnActiveSessionsChangedListener(listener, access.listenerComponent, handler)
                    track(manager.getActiveSessions(access.listenerComponent))
                } catch (e: SecurityException) {
                    // Access was revoked between the check and the call: nothing to show.
                    trySend(null)
                }
                awaitClose {
                    manager.removeOnActiveSessionsChangedListener(listener)
                    tracked.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
                    tracked.clear()
                    shown = null
                }
            }.flowOn(Dispatchers.Main.immediate)

        private fun snapshotOf(controller: MediaController): SessionSnapshot {
            val state = controller.playbackState
            val metadata = controller.metadata
            return SessionSnapshot(
                packageName = controller.packageName,
                title =
                    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist =
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                state = state?.state ?: PlaybackState.STATE_NONE,
                positionMillis = state?.position ?: 0,
                updatedAtMillis = state?.lastPositionUpdateTime ?: 0,
                speed = state?.playbackSpeed ?: 0f,
                durationMillis = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            )
        }

        /** "Spotify" for "com.spotify.music"; the package name if the app isn't visible to us. */
        private fun labelOf(packageName: String): String =
            labels.getOrPut(packageName) {
                try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
            }

        override fun playPause() =
            command { controller ->
                if (MediaSessionChooser.isPlaying(controller.playbackState?.state ?: PlaybackState.STATE_NONE)) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }

        override fun skipToNext() = command { it.transportControls.skipToNext() }

        override fun skipToPrevious() = command { it.transportControls.skipToPrevious() }

        /** Runs [block] on the shown session; a session that died meanwhile is ignored. */
        private inline fun command(block: (MediaController) -> Unit) {
            val controller = shown ?: return
            try {
                block(controller)
            } catch (e: IllegalStateException) {
                // The session was released between the tap and the call.
            } catch (e: SecurityException) {
                // Access revoked: the next emission will hide the controls.
            }
        }
    }
