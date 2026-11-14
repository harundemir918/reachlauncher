package com.baklavapp.reachlauncher.core.data.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the device's *current* time zone.
 *
 * Why not `Clock.systemDefaultZone()`: that captures the zone once, when the clock is
 * created. Reach's clock is a process-wide singleton, and a launcher process can live
 * for weeks — including trips across time zones. Android updates the JVM default zone
 * when the user's zone changes, and this clock reads it on every call, so launch hours
 * and prediction windows always use the zone the user is in right now.
 *
 * Tests inject a fixed or test-scheduler-driven `Clock` instead.
 */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
