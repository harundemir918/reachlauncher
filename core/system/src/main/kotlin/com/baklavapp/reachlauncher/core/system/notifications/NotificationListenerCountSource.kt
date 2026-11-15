package com.baklavapp.reachlauncher.core.system.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the user turned on notification access for Reach, shared by the unread counts and
 * the media source (both depend on the same listener).
 *
 * **The check.** `NotificationManager.isNotificationListenerAccessGranted(component)` (API 27+,
 * no permission needed) asks about exactly our listener component. It is re-read on every Activity resume
 * ([AccessRechecks] — the user grants access in Settings and comes back) and whenever the
 * listener connects or disconnects ([NotificationListenerHub.connected]).
 *
 * **Rebinding.** Normally the system binds the listener by itself once access is on. After
 * some events (an app update, the process being killed under memory pressure on some
 * devices) it may stay unbound although access is granted; then badges would silently
 * freeze. So whenever we see "granted but not connected" we ask for a rebind
 * (`NotificationListenerService.requestRebind`, API 24+), which is a no-op if already bound.
 *
 * Flutter analogy: a permission-status provider (`permission_handler`'s `status`) that is
 * re-read on `AppLifecycleState.resumed`.
 */
@Singleton
class NotificationAccess
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val rechecks: AccessRechecks,
        private val hub: NotificationListenerHub,
    ) {
        /** Reach's listener, the "token" `MediaSessionManager.getActiveSessions` asks for. */
        val listenerComponent: ComponentName = ComponentName(context, ReachNotificationListenerService::class.java)

        /** Current access, read now. */
        fun isGranted(): Boolean =
            context.getSystemService<NotificationManager>()?.isNotificationListenerAccessGranted(listenerComponent) ?: false

        /** Access state, re-read on every resume and listener (dis)connect. */
        val granted: Flow<Boolean> =
            combine(rechecks.ticks, hub.connected) { _, connected ->
                isGranted().also { granted -> if (granted && !connected) requestRebind() }
            }.distinctUntilChanged()

        private val lastRebindAt = AtomicLong(Long.MIN_VALUE / 2)

        /**
         * [granted] is cold, so every collector (counts, media, onboarding) runs this check on
         * each resume. A rebind is cheap but not free: at most one request per
         * [REBIND_INTERVAL_MILLIS], however many collectors notice the listener is unbound.
         */
        private fun requestRebind() {
            val now = SystemClock.elapsedRealtime()
            val last = lastRebindAt.get()
            if (now - last < REBIND_INTERVAL_MILLIS || !lastRebindAt.compareAndSet(last, now)) return
            try {
                NotificationListenerService.requestRebind(listenerComponent)
            } catch (e: SecurityException) {
                // Access was revoked between the check and the call; the next check says so.
            }
        }

        private companion object {
            const val REBIND_INTERVAL_MILLIS = 10_000L
        }
    }

/**
 * [NotificationCountSource] backed by [ReachNotificationListenerService] (via
 * [NotificationListenerHub]). Counting rules live in [NotificationCounter]: active,
 * non-ongoing, non-summary notifications of other apps, per package *and* profile
 * (`StatusBarNotification.user` → serial, D12/D31).
 *
 * With access off both flows report "nothing": `false` and an empty map — never stale counts
 * from an earlier session, because the hub is cleared on disconnect.
 */
@Singleton
class NotificationListenerCountSource
    @Inject
    constructor(
        access: NotificationAccess,
        hub: NotificationListenerHub,
    ) : NotificationCountSource {
        override val isAccessGranted: Flow<Boolean> = access.granted

        override val countsByApp: Flow<Map<PackageUser, Int>> =
            combine(access.granted, hub.counts) { granted, counts -> if (granted) counts else emptyMap() }
                .distinctUntilChanged()
    }
