@file:OptIn(ExperimentalCoroutinesApi::class)

package com.baklavapp.reachlauncher.core.data.time

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.SchedulerClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class HourTickerTest {
    @Test
    fun `emits now, then once per hour boundary`() =
        runTest {
            // Sunday 2026-11-15 23:58 UTC.
            val start = ZonedDateTime.of(2026, 11, 15, 23, 58, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            val clock = SchedulerClock(testScheduler, start, ZoneOffset.UTC)

            hourTicks(clock).test {
                val first = awaitItem()
                assertThat(first.hourOfDay).isEqualTo(23)
                assertThat(first.dayOfWeek).isEqualTo(7)

                // Nothing in the remaining two minutes of the hour.
                testScheduler.advanceTimeBy(119_000)
                expectNoEvents()

                testScheduler.advanceTimeBy(2_000)
                val second = awaitItem()
                assertThat(second.hourOfDay).isEqualTo(0)
                assertThat(second.dayOfWeek).isEqualTo(1) // Monday

                // The 5-minute safety wake-ups within the hour emit nothing.
                testScheduler.advanceTimeBy(30 * 60_000L)
                expectNoEvents()
                testScheduler.advanceTimeBy(30 * 60_000L)
                assertThat(awaitItem().hourOfDay).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
