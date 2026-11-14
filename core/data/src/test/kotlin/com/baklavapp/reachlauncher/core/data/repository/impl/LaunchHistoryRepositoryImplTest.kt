package com.baklavapp.reachlauncher.core.data.repository.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baklavapp.reachlauncher.core.data.db.ReachDatabase
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class LaunchHistoryRepositoryImplTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ReachDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val chat = AppKey("com.example.chat", "com.example.chat.Main", 0)
    private val workChat = chat.copy(userSerial = 10)

    private fun TestScope.repository(zone: ZoneId = istanbul) =
        LaunchHistoryRepositoryImpl(db.launchEventDao(), Clock.fixed(Instant.EPOCH, zone), StandardTestDispatcher(testScheduler))

    @After
    fun tearDown() = db.close()

    @Test
    fun `hour and weekday are captured in the local zone at record time`() =
        runTest {
            // Saturday 2026-11-14 23:30 in Istanbul = 20:30 UTC.
            val millis = ZonedDateTime.of(2026, 11, 14, 23, 30, 0, 0, istanbul).toInstant().toEpochMilli()
            repository().recordLaunch(chat, millis)

            // A later read with a different device zone doesn't rewrite history.
            val events = repository(zone = ZoneId.of("America/New_York")).eventsSince(0).first()
            assertThat(events).containsExactly(LaunchEvent(chat, millis, hourOfDay = 23, dayOfWeek = 6))
            assertThat(events.single().isWeekend).isTrue()
        }

    @Test
    fun `eventsSince filters and orders oldest first`() =
        runTest {
            val repo = repository()
            repo.recordLaunch(chat, 3_000)
            repo.recordLaunch(chat, 1_000)
            repo.recordLaunch(chat, 2_000)
            assertThat(repo.eventsSince(2_000).first().map { it.epochMillis }).containsExactly(2_000L, 3_000L).inOrder()
        }

    @Test
    fun `launch counts are per activity and profile`() =
        runTest {
            val repo = repository()
            repo.recordLaunch(chat, 1)
            repo.recordLaunch(chat, 2)
            repo.recordLaunch(workChat, 3)
            assertThat(repo.launchCounts.first()).containsExactly(chat, 2, workChat, 1)
        }
}
