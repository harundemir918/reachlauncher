package com.baklavapp.reachlauncher.core.system.notifications

import android.content.Context
import com.baklavapp.reachlauncher.core.model.PackageUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge between [ReachNotificationListenerService] — which the *system* creates and
 * binds whenever notification access is on, independent of any screen — and the sources
 * the UI collects ([NotificationListenerCountSource], and [connected] for media).
 *
 * The service pushes into it; the sources read its `StateFlow`s. It's a Hilt singleton,
 * injected into the service with `@AndroidEntryPoint`, so there is exactly one per process.
 * Callbacks arrive on the main thread, but the methods are synchronized anyway: they're
 * cheap, and it makes the hub safe whatever thread a future caller uses.
 *
 * Flutter analogy: the singleton `StreamController`s a plugin keeps on the native side,
 * which its background service writes to and its `EventChannel` reads from.
 */
@Singleton
class NotificationListenerHub
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val counter = NotificationCounter(ownPackage = context.packageName)
        private val _connected = MutableStateFlow(false)
        private val _counts = MutableStateFlow<Map<PackageUser, Int>>(emptyMap())

        /** Whether the system currently has our listener bound (access on *and* connected). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** Countable notifications per package-in-profile; empty while disconnected. */
        val counts: StateFlow<Map<PackageUser, Int>> = _counts.asStateFlow()

        /** The listener connected: [active] is the full current list. */
        @Synchronized
        fun onConnected(active: List<NotificationEntry>) {
            counter.replaceAll(active)
            _counts.value = counter.snapshot()
            _connected.value = true
        }

        /** A notification was posted or updated. */
        @Synchronized
        fun onPosted(entry: NotificationEntry) {
            counter.posted(entry)
            _counts.value = counter.snapshot()
        }

        /** A notification was removed. */
        @Synchronized
        fun onRemoved(key: String) {
            counter.removed(key)
            _counts.value = counter.snapshot()
        }

        /** The listener was unbound (access revoked, process trimmed): no stale badges. */
        @Synchronized
        fun onDisconnected() {
            counter.clear()
            _counts.value = emptyMap()
            _connected.value = false
        }
    }
