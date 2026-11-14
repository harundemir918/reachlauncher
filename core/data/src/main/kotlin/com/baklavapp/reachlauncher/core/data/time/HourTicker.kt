package com.baklavapp.reachlauncher.core.data.time

import com.baklavapp.reachlauncher.core.data.domain.Moment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Emits the current [Moment] now and again whenever the local hour (or day) changes.
 *
 * Not a busy loop: it sleeps until the next hour boundary computed from [clock]. The
 * sleep is capped at [maxSleepMillis] because coroutine `delay` runs on the *monotonic*
 * clock, which on Android stops while the phone is in deep sleep; without the cap, a
 * phone that slept through 9:00 would only notice at ~10:00. A wake-up that finds the
 * same hour emits nothing (`distinctUntilChanged`), so the cap costs almost nothing.
 * Time-zone and manual clock changes are picked up the same way.
 *
 * The flow is cold and only ticks while collected (e.g. while Home is visible).
 *
 * Flutter analogy: a `Stream.periodic` that re-schedules itself for the next full hour
 * instead of firing at a fixed interval.
 */
fun hourTicks(
    clock: Clock,
    maxSleepMillis: Long = DEFAULT_MAX_SLEEP_MILLIS,
): Flow<Moment> =
    flow {
        while (true) {
            val now = clock.instant().atZone(clock.zone)
            emit(Moment(now.toInstant().toEpochMilli(), now.hour, now.dayOfWeek.value))
            val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val untilNextHour = Duration.between(now, nextHour).toMillis()
            delay(untilNextHour.coerceIn(MIN_SLEEP_MILLIS, maxSleepMillis))
        }
    }.distinctUntilChanged { old, new -> old.hourOfDay == new.hourOfDay && old.dayOfWeek == new.dayOfWeek }

/** Five minutes: the worst-case lag after deep sleep, see [hourTicks]. */
const val DEFAULT_MAX_SLEEP_MILLIS = 5 * 60_000L

// Guards against a zero/negative delay right at a boundary (or a clock jumping back).
private const val MIN_SLEEP_MILLIS = 1_000L
