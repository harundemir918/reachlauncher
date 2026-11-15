package com.baklavapp.reachlauncher.core.system.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hub's lifecycle as the system drives it: connect (full list) → posts/updates/removals
 * → disconnect. Badges must follow every step and must never survive a disconnect (D54).
 */
@RunWith(RobolectricTestRunner::class)
class NotificationListenerHubTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val chat = PackageUser("com.example.chat", 0)
    private val workChat = PackageUser("com.example.chat", 10)

    private fun entry(
        key: String,
        app: PackageUser = chat,
        ongoing: Boolean = false,
    ) = NotificationEntry(key, app, isOngoing = ongoing, isGroupSummary = false)

    @Test
    fun `connect, post, update, remove and disconnect keep counts exact`() {
        val hub = NotificationListenerHub(app)
        assertThat(hub.connected.value).isFalse()
        assertThat(hub.counts.value).isEmpty()

        hub.onConnected(listOf(entry("a"), entry("b"), entry("w", app = workChat)))
        assertThat(hub.connected.value).isTrue()
        assertThat(hub.counts.value).containsExactly(chat, 2, workChat, 1)

        hub.onPosted(entry("a")) // an update of an existing notification: not a new one
        hub.onPosted(entry("c"))
        assertThat(hub.counts.value).containsExactly(chat, 3, workChat, 1)

        hub.onPosted(entry("b", ongoing = true)) // became ongoing (e.g. a call): no longer counts
        hub.onRemoved("w")
        assertThat(hub.counts.value).containsExactly(chat, 2)

        hub.onDisconnected()
        assertThat(hub.connected.value).isFalse()
        assertThat(hub.counts.value).isEmpty()
    }

    @Test
    fun `reconnecting replaces everything instead of adding to stale state`() {
        val hub = NotificationListenerHub(app)
        hub.onConnected(listOf(entry("a"), entry("b")))
        hub.onDisconnected()
        hub.onConnected(listOf(entry("z")))
        assertThat(hub.counts.value).containsExactly(chat, 1)
    }

    @Test
    fun `reach's own notifications never count`() {
        val hub = NotificationListenerHub(app)
        hub.onConnected(listOf(entry("own", app = PackageUser(app.packageName, 0)), entry("a")))
        assertThat(hub.counts.value).containsExactly(chat, 1)
    }
}
