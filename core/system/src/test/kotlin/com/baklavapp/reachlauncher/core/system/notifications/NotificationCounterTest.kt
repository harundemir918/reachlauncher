package com.baklavapp.reachlauncher.core.system.notifications

import com.baklavapp.reachlauncher.core.model.PackageUser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Plain JVM test of the "N NEW" counting rules. */
class NotificationCounterTest {
    private val chat = PackageUser("com.chat", 0)
    private val workChat = PackageUser("com.chat", 10)
    private val counter = NotificationCounter(ownPackage = "com.baklavapp.reachlauncher")

    private fun entry(
        key: String,
        app: PackageUser = chat,
        ongoing: Boolean = false,
        summary: Boolean = false,
    ) = NotificationEntry(key, app, isOngoing = ongoing, isGroupSummary = summary)

    @Test
    fun `ongoing, group summaries and our own notifications are not counted`() {
        counter.replaceAll(
            listOf(
                entry("a"),
                entry("b"),
                entry("music", PackageUser("com.music", 0), ongoing = true),
                entry("bundle", summary = true),
                entry("own", PackageUser("com.baklavapp.reachlauncher", 0)),
            ),
        )
        assertThat(counter.snapshot()).containsExactly(chat, 2)
    }

    @Test
    fun `personal and work copies are counted separately`() {
        counter.replaceAll(listOf(entry("a"), entry("b", workChat), entry("c", workChat)))
        assertThat(counter.snapshot()).containsExactly(chat, 1, workChat, 2)
    }

    @Test
    fun `an update replaces by key and removal forgets it`() {
        counter.posted(entry("a"))
        counter.posted(entry("a")) // Same notification updated: still one.
        assertThat(counter.snapshot()).containsExactly(chat, 1)
        counter.posted(entry("a", ongoing = true)) // Became ongoing: no longer unread.
        assertThat(counter.snapshot()).isEmpty()
        counter.posted(entry("b"))
        counter.removed("b")
        counter.removed("unknown")
        assertThat(counter.snapshot()).isEmpty()
    }
}
