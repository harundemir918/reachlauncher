package com.baklavapp.reachlauncher.core.system.wallpaper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.WallpaperColorSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home-screen wallpaper's primary colour, for wallpaper mode's accent (spec §6.9).
 *
 * **What is read.** `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` — the handful of
 * colours the *system* already extracted from the wallpaper (it's what Material You uses).
 * Reach never reads the wallpaper bitmap itself: that would need `READ_EXTERNAL_STORAGE` /
 * `MANAGE_EXTERNAL_STORAGE` on recent Android versions, and it's far more than we need. The
 * colours API needs no permission at all.
 *
 * **Updates.** `addOnColorsChangedListener` fires when the user picks a new wallpaper (or a
 * live wallpaper changes its colours). We wrap it in a `callbackFlow`, emit the current
 * colour first, and unregister in `awaitClose` when nobody collects any more — so the
 * listener exists only while wallpaper mode is on and Home is visible.
 *
 * **Failure modes**, all reported as `null` ("unknown", Home keeps the palette's accent):
 * no wallpaper support (some TVs/automotive builds: `isWallpaperSupported == false`),
 * `getWallpaperColors` returning null (colours not computed yet), and a `SecurityException`
 * some OEM builds throw for third-party callers.
 *
 * Flutter analogy: a plugin `EventChannel` stream — `callbackFlow` is Kotlin's
 * `StreamController` whose `onListen`/`onCancel` register and unregister the platform
 * listener.
 */
@Singleton
class WallpaperManagerColorSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WallpaperColorSource {
        override val primaryColor: Flow<Int?> =
            callbackFlow {
                val manager = wallpaperManager()
                if (manager == null) {
                    trySend(null)
                    awaitClose { }
                    return@callbackFlow
                }
                // Current colour first, so Home has an accent before the wallpaper ever changes.
                trySend(readPrimary(manager))
                val listener =
                    WallpaperManager.OnColorsChangedListener { colors, which ->
                        if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                            // The callback's colours can be null (e.g. while a live wallpaper
                            // starts); re-read instead of forgetting the accent — off the main
                            // thread, since the listener runs there and the read is a binder call.
                            val primary = colors.primaryArgb()
                            if (primary != null) trySend(primary) else launch { send(readPrimary(manager)) }
                        }
                    }
                val registered =
                    runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }.isSuccess
                awaitClose { if (registered) runCatching { manager.removeOnColorsChangedListener(listener) } }
            }.distinctUntilChanged()
                // getWallpaperColors is a binder call that may compute colours on first use.
                .flowOn(ioDispatcher)

        private fun wallpaperManager(): WallpaperManager? =
            runCatching { WallpaperManager.getInstance(context) }
                .getOrNull()
                ?.takeIf { it.isWallpaperSupported }

        private fun readPrimary(manager: WallpaperManager): Int? =
            try {
                manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM).primaryArgb()
            } catch (_: SecurityException) {
                null
            } catch (_: RuntimeException) {
                // Some OEM builds throw IllegalStateException when the service isn't ready.
                null
            }

        private fun WallpaperColors?.primaryArgb(): Int? = this?.primaryColor?.toArgb()
    }
