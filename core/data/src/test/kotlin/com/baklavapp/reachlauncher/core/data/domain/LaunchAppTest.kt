package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeShortcutSource
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle, StandardTestDispatcher
class LaunchAppTest {
    private val key = AppKey("com.example.chat", "com.example.chat.Main", userSerial = 0)
    private val clock = Clock.fixed(Instant.ofEpochMilli(42_000), ZoneOffset.UTC)

    private fun TestScope.subject(
        launcher: FakeAppLauncher = FakeAppLauncher(),
        shortcuts: FakeShortcutSource = FakeShortcutSource(),
        history: LaunchHistoryRepository = FakeLaunchHistoryRepository(),
        // The test scope itself stands in for the application scope: advanceUntilIdle() runs
        // the recording coroutines, and an exception escaping them would fail the test.
    ) = LaunchApp(launcher, shortcuts, history, clock, this)

    @Test
    fun `a launched app is recorded at the clock's time`() =
        runTest {
            val history = FakeLaunchHistoryRepository()
            val result = subject(history = history).app(key, origin = null)
            advanceUntilIdle()
            assertThat(result).isEqualTo(LaunchResult.LAUNCHED)
            assertThat(
                history.events.value
                    .single()
                    .epochMillis,
            ).isEqualTo(42_000)
        }

    @Test
    fun `resuming a profile or failing is not recorded`() =
        runTest {
            LaunchResult.entries.filter { it != LaunchResult.LAUNCHED }.forEach { outcome ->
                val history = FakeLaunchHistoryRepository()
                val launcher = FakeAppLauncher().apply { result = outcome }
                assertThat(subject(launcher = launcher, history = history).app(key, null)).isEqualTo(outcome)
                advanceUntilIdle()
                assertThat(history.events.value).isEmpty()
            }
        }

    @Test
    fun `a history failure never escapes`() =
        runTest(StandardTestDispatcher()) {
            val failing =
                object : LaunchHistoryRepository {
                    override suspend fun recordLaunch(
                        key: AppKey,
                        epochMillis: Long,
                    ) = throw IllegalStateException("disk full")

                    override val launchCounts: Flow<Map<AppKey, Int>> = emptyFlow()

                    override fun eventsSince(sinceEpochMillis: Long): Flow<List<LaunchEvent>> = emptyFlow()
                }
            assertThat(subject(history = failing).app(key, null)).isEqualTo(LaunchResult.LAUNCHED)
            advanceUntilIdle() // would rethrow here if the failure escaped the scope
        }

    @Test
    fun `shortcuts record against the given app and fail without permission`() =
        runTest {
            val shortcut = AppShortcut("s1", key.packageUser, null, "Message Ayşe", "Chat")
            val shortcuts = FakeShortcutSource().apply { available.value = listOf(shortcut) }
            val history = FakeLaunchHistoryRepository()
            val launch = subject(shortcuts = shortcuts, history = history)

            assertThat(launch.shortcut(shortcut, null, recordAs = key)).isEqualTo(LaunchResult.FAILED)
            shortcuts.granted.value = true
            assertThat(launch.shortcut(shortcut, null, recordAs = key)).isEqualTo(LaunchResult.LAUNCHED)
            advanceUntilIdle()
            assertThat(history.events.value.map { it.key }).containsExactly(key)
        }
}
