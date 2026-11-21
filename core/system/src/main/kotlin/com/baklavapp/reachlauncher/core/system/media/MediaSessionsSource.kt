package com.baklavapp.reachlauncher.core.system.media

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.source.MediaSessionSource
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.system.ActivityTracker
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
 * **Opening the app** sends the session's own `sessionActivity` (the screen the app wants
 * a tap on its player to open, typically "now playing"), else starts the package's launch
 * intent. That PendingIntent belongs to the media app, so since Android 14 the sender (Reach,
 * in the foreground) must explicitly lend it the right to start an activity, or the system
 * silently blocks it.
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
        private val tracker: ActivityTracker,
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

                // Every entry point below runs on the main Handler, where an uncaught exception
                // crashes Home. A session can die, or access can be revoked, between any two
                // binder calls (SecurityException / IllegalStateException): show nothing instead.
                fun guarded(block: () -> Unit) {
                    try {
                        block()
                    } catch (e: SecurityException) {
                        trySend(null)
                    } catch (e: IllegalStateException) {
                        trySend(null)
                    }
                }

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
                                override fun onPlaybackStateChanged(state: PlaybackState?) = guarded(::publish)

                                override fun onMetadataChanged(metadata: MediaMetadata?) = guarded(::publish)

                                override fun onSessionDestroyed() {
                                    tracked.removeAll { it.first === controller }
                                    guarded(::publish)
                                }
                            }
                        controller.registerCallback(callback, handler)
                        tracked += controller to callback
                    }
                    publish()
                }

                val listener =
                    MediaSessionManager.OnActiveSessionsChangedListener { controllers -> guarded { track(controllers.orEmpty()) } }
                // Access may have been revoked between the check and these calls: nothing to show.
                guarded {
                    manager.addOnActiveSessionsChangedListener(listener, access.listenerComponent, handler)
                    track(manager.getActiveSessions(access.listenerComponent))
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

        override fun openApp(origin: LaunchOrigin?): Boolean {
            val controller = shown ?: return false
            val activity = tracker.resumedActivity
            val options = launchOptions(activity, origin)
            val session =
                try {
                    controller.sessionActivity
                } catch (e: IllegalStateException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            if (session != null) {
                try {
                    session.send(activity ?: context, 0, null, null, null, null, options)
                    return true
                } catch (e: PendingIntent.CanceledException) {
                    // The app cancelled it: fall back to its launch screen.
                }
            }
            val launch = context.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return false
            return try {
                if (activity != null) {
                    activity.startActivity(launch, options)
                } else {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                }
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        /**
         * The clip-reveal animation from [origin] (as `SystemAppLauncher` does), plus, on
         * Android 14+, the opt-in that lets the media app's PendingIntent start its activity.
         */
        private fun launchOptions(
            activity: Activity?,
            origin: LaunchOrigin?,
        ): Bundle {
            val decorView = activity?.window?.decorView
            val options =
                if (decorView != null && origin != null) {
                    ActivityOptions.makeClipRevealAnimation(decorView, origin.left, origin.top, origin.width, origin.height)
                } else {
                    ActivityOptions.makeBasic()
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            return options.toBundle()
        }

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
