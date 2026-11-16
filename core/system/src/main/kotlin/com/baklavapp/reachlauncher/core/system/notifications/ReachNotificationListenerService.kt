package com.baklavapp.reachlauncher.core.system.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reach's notification listener: the one thing that makes "3 NEW" badges (spec §7.1) and the
 * media controls (spec §7.2, `MediaSessionManager.getActiveSessions` accepts only an enabled
 * listener's `ComponentName`) possible.
 *
 * Android background for Flutter developers: a `Service` is a component without UI that the
 * *system* creates. A notification listener is a bound service the system connects to as soon
 * as the user turns on "Notification access" for Reach in Settings, and keeps bound while it
 * stays on — whether or not a Reach screen is open. There is no runtime dialog; the user can
 * only grant it in Settings (`AppLauncher.open(SystemScreen.NOTIFICATION_ACCESS)`).
 *
 * What it does: nothing but forward. On connect it hands the full list of active
 * notifications to [NotificationListenerHub]; afterwards every post/update/removal. It never
 * reads notification *content* (titles, text) — only package, user and two flags — and never
 * stores anything.
 *
 * Declared in `core/system/src/main/AndroidManifest.xml`: `exported="true"` (the system binds
 * it from outside our process) protected by `BIND_NOTIFICATION_LISTENER_SERVICE` (only the
 * system holds it), plus the listener intent filter.
 *
 * `@AndroidEntryPoint` lets Hilt inject fields into a component Android creates itself.
 *
 * Flutter analogy: the native background half of a notification-listener plugin — the part
 * that runs even with no Flutter view attached and pushes events to a stream.
 */
@AndroidEntryPoint
class ReachNotificationListenerService : NotificationListenerService() {
    @Inject
    lateinit var hub: NotificationListenerHub

    @Inject
    lateinit var profiles: Profiles

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active =
            try {
                activeNotifications.orEmpty().map(::entryOf)
            } catch (e: SecurityException) {
                // Raced with a revocation: the system will call onListenerDisconnected next.
                emptyList()
            }
        hub.onConnected(active)
    }

    override fun onListenerDisconnected() {
        hub.onDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { hub.onPosted(entryOf(it)) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { hub.onRemoved(it.key) }
    }

    private fun entryOf(sbn: StatusBarNotification): NotificationEntry {
        val flags = sbn.notification?.flags ?: 0
        return NotificationEntry(
            key = sbn.key,
            app = PackageUser(sbn.packageName, profiles.serialOf(sbn.user)),
            isOngoing =
                sbn.isOngoing ||
                    (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
                    isPlayerNotification(sbn.notification),
            isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        )
    }
}

/**
 * A media player's notification (it carries a media session, or is filed as transport
 * controls). It counts like an ongoing one: it is "something playing", never "something
 * unread". Found on a real device: a paused YouTube Music player is no longer ongoing and
 * showed up as "1 NEW" on Listen and "1 unread" in the Now row.
 */
internal fun isPlayerNotification(notification: Notification?): Boolean =
    notification != null &&
        (
            notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                notification.category == Notification.CATEGORY_TRANSPORT
        )
