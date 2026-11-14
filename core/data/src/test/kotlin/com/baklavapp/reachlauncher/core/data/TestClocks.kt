@file:OptIn(ExperimentalCoroutinesApi::class)

package com.baklavapp.reachlauncher.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A clock driven by the coroutine test scheduler's virtual time, so `delay`-based tickers
 * and the wall clock move together when a test calls `advanceTimeBy`.
 */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val startMillis: Long,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = SchedulerClock(scheduler, startMillis, zone)

    override fun instant(): Instant = Instant.ofEpochMilli(startMillis + scheduler.currentTime)
}
