package com.baklavapp.reachlauncher.core.data.fake

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The fakes are what every feature test builds on, so they must honour the contracts:
 * a user's "Move to…" marks the app as user-assigned, setters re-emit live, and
 * permission-gated data stays empty while the permission is off.
 */
class FakesTest {
    private val key = AppKey("com.example.chat", "com.example.chat.Main", userSerial = 0)

    private fun app(
        label: String,
        k: AppKey = key,
    ) = CatalogApp(
        key = k,
        label = label,
        profile = ProfileType.PERSONAL,
        intent = ReachIntent.TOOLS,
        isUserAssigned = false,
        isHidden = false,
        launchCount = 0,
    )

    @Test
    fun `moving an app marks it user-assigned`() =
        runTest {
            val repo = FakeAppCatalogRepository(listOf(app("Chat")))
            repo.apps.test {
                assertThat(awaitItem().single().intent).isEqualTo(ReachIntent.TOOLS)
                repo.moveToIntent(key, ReachIntent.TALK)
                val moved = awaitItem().single()
                assertThat(moved.intent).isEqualTo(ReachIntent.TALK)
                assertThat(moved.isUserAssigned).isTrue()
            }
        }

    @Test
    fun `catalog is sorted by label`() =
        runTest {
            val other = key.copy(packageName = "b")
            val repo = FakeAppCatalogRepository(listOf(app("Zoom", other), app("ayarlar")))
            repo.apps.test { assertThat(awaitItem().map { it.label }).containsExactly("ayarlar", "Zoom").inOrder() }
        }

    @Test
    fun `settings changes re-emit`() =
        runTest {
            val repo = FakeSettingsRepository()
            repo.settings.test {
                assertThat(awaitItem().thumbSide).isEqualTo(ThumbSide.LEFT)
                repo.setThumbSide(ThumbSide.RIGHT)
                assertThat(awaitItem().thumbSide).isEqualTo(ThumbSide.RIGHT)
            }
        }

    @Test
    fun `history derives hour and weekday and counts launches`() =
        runTest {
            val repo = FakeLaunchHistoryRepository()
            // Saturday 2026-11-14 08:30 UTC, then Friday 2026-11-13 21:00 UTC.
            val saturday = ZonedDateTime.of(2026, 11, 14, 8, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            val friday = ZonedDateTime.of(2026, 11, 13, 21, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            repo.recordLaunch(key, saturday)
            repo.recordLaunch(key, friday)

            repo.eventsSince(0).test {
                val events = awaitItem()
                assertThat(events.map { it.epochMillis }).containsExactly(friday, saturday).inOrder()
                assertThat(events.last().hourOfDay).isEqualTo(8)
                assertThat(events.last().isWeekend).isTrue()
                assertThat(events.first().dayOfWeek).isEqualTo(5)
            }
            repo.launchCounts.test { assertThat(awaitItem()).containsExactly(key, 2) }
        }

    @Test
    fun `notification counts stay empty until access is granted`() =
        runTest {
            val source = FakeNotificationCountSource()
            source.counts.value = mapOf(key.packageUser to 3)
            source.countsByApp.test {
                assertThat(awaitItem()).isEmpty()
                source.granted.value = true
                assertThat(awaitItem()).containsExactly(key.packageUser, 3)
            }
        }

    @Test
    fun `shortcuts are hidden and unlaunchable without host permission`() =
        runTest {
            val source = FakeShortcutSource()
            val shortcut = AppShortcut("s1", PackageUser("com.example.chat", 0), null, "Message Ayşe", "Chat")
            source.available.value = listOf(shortcut)
            assertThat(source.launch(shortcut, origin = null)).isFalse()
            source.shortcuts.test {
                assertThat(awaitItem()).isEmpty()
                source.granted.value = true
                assertThat(awaitItem()).containsExactly(shortcut)
            }
            assertThat(source.launch(shortcut, origin = null)).isTrue()
        }
}
