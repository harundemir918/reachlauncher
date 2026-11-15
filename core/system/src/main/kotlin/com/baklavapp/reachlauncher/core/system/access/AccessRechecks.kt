package com.baklavapp.reachlauncher.core.system.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A process-wide "the user may have changed a permission" signal.
 *
 * **Why it exists.** Notification access, usage access and the calendar permission are all
 * granted *outside* Reach — in system Settings or in the system permission dialog — and
 * Android sends no broadcast when that happens. What we do know: to grant anything the user
 * leaves Reach's Activity and comes back, so the Activity resumes. [ActivityTracker] (already
 * registered for every Activity, D11) calls [recheck] on every resume, and each access-gated
 * source maps [ticks] to a fresh read of its permission:
 *
 * ```kotlin
 * override val isAccessGranted = rechecks.ticks.map { readAccess() }.distinctUntilChanged()
 * ```
 *
 * A `StateFlow` (not an event stream) so a source that starts collecting later still reads
 * once immediately, and so a burst of resumes collapses into one re-read. Keeping this inside
 * `:core:system` means no contract had to grow a `recheck()` method (unlike
 * `HomeRoleSource.recheck()` from M1, which predates it), and no screen can forget to call it.
 *
 * Flutter analogy: a `ValueNotifier<int>` bumped from `didChangeAppLifecycleState(resumed)`
 * that permission-dependent providers listen to.
 */
@Singleton
class AccessRechecks
    @Inject
    constructor() {
        private val counter = MutableStateFlow(0L)

        /** Increments on every [recheck]; collectors re-read their permission on each value. */
        val ticks: StateFlow<Long> = counter.asStateFlow()

        /** Asks every access-gated source to re-read. Cheap; call as often as you like. */
        fun recheck() = counter.update { it + 1 }
    }
