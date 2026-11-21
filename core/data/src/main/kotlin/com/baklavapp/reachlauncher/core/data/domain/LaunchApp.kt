package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.data.di.ApplicationScope
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * Launches an app and records it in launch history — the one place every
 * screen (Home, intent detail, Search, All Apps, the press-slide-release fan) goes through,
 * so they all count launches the same way.
 *
 * Rules (docs/DECISIONS.md D32, D34, D36):
 * - only [LaunchResult.LAUNCHED] is recorded; a paused-profile resume or a failure isn't;
 * - recording runs in the application scope, not the caller's: the launched app is already
 *   in front and the ViewModel may be stopped, but the launch must still be counted;
 * - a recording failure (e.g. a full disk) is swallowed — it costs one prediction signal,
 *   never a crash.
 *
 * Flutter analogy: a use-case class from clean architecture (`LaunchApp(repository)` with a
 * `call()` method) that several BLoCs share instead of each re-implementing the logic.
 */
class LaunchApp
    @Inject
    constructor(
        private val appLauncher: AppLauncher,
        private val history: LaunchHistoryRepository,
        private val clock: Clock,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        /** Launches [key], animating from [origin] when given. Records the launch on success. */
        fun app(
            key: AppKey,
            origin: LaunchOrigin?,
        ): LaunchResult {
            val result = appLauncher.launchApp(key, origin)
            if (result == LaunchResult.LAUNCHED) record(key)
            return result
        }

        private fun record(key: AppKey) {
            val now = clock.millis()
            appScope.launch {
                try {
                    history.recordLaunch(key, now)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best effort: the app is already open; see the class KDoc.
                }
            }
        }
    }
