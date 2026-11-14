package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ProfileType
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/**
 * "Now" in the terms the prediction engine cares about: wall-clock time plus the local
 * hour and ISO weekday, captured once so a whole ranking pass uses one consistent moment.
 */
data class Moment(
    val epochMillis: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
) {
    /** ISO weekend (Saturday = 6, Sunday = 7), matching [LaunchEvent.isWeekend]. */
    val isWeekend: Boolean get() = dayOfWeek >= 6
}

/**
 * Scores apps for the Home "Now" row and empty-query search (spec §6.2).
 *
 * Every launch Reach performed contributes to its app's score:
 *
 * ```
 * eventWeight = recency × (hourWindow × dayType + BASE_WEIGHT)
 * ```
 *
 * - **recency** = 0.5^(ageDays / [HALF_LIFE_DAYS]). Habits drift (new job, holidays), so
 *   a launch two weeks ago counts half as much as one today. 14 days is long enough
 *   that a weekly pattern (e.g. Sunday-only apps) survives two missed weeks, and short
 *   enough that a changed routine takes over within about a month.
 * - **hourWindow**: 1 for the same hour, [ADJACENT_HOUR_WEIGHT] for ±1 hour (circular,
 *   so 23:xx and 00:xx are neighbours), 0 otherwise. This is the "usually around now" part.
 * - **dayType**: 1 when the launch happened on the same kind of day (weekday vs weekend)
 *   as now, [OTHER_DAY_TYPE_WEIGHT] otherwise. Weekend mornings look nothing like workday
 *   mornings, but a strong weekday habit is still a weak hint on a Saturday.
 * - **BASE_WEIGHT**: a small hour-independent share, so frequently and recently used
 *   apps still appear (as "RECENT") before a time pattern has formed.
 *
 * Optional signals:
 * - **Usage access** (`UsageStatsManager`) adds `USAGE_WEIGHT × ln(1 + launches in the
 *   hour window)`. The log keeps a heavy system-wide user (hundreds of foreground events)
 *   from drowning Reach's own, more intentional history. Usage stats only cover the
 *   personal profile, so the blend only applies to personal apps.
 * - **Unread notifications** multiply the score by `1 + UNREAD_BOOST_PER × min(n, 4)`
 *   (at most ×1.6). A multiplier boosts apps the user already uses without letting a
 *   noisy, never-opened app take over the row: unread "boosts but does not dominate".
 *
 * Apps with no history signal at all are not predicted (an empty history gives an empty
 * list; Home then shows its own empty state).
 *
 * Flutter analogy: a pure Dart class with no Flutter imports, the kind you'd test with
 * plain `package:test`; `PredictionRepositoryImpl` is the BLoC-facing repository that
 * feeds it streams.
 */
object PredictionEngine {
    /** Recency half-life; see the class KDoc for why 14 days. */
    const val HALF_LIFE_DAYS = 14.0

    /** Events older than this are ignored (weight would be ≤ 0.05 anyway). */
    const val HORIZON_DAYS = 60L

    /** Weight of a launch one hour before/after the current hour. */
    const val ADJACENT_HOUR_WEIGHT = 0.5

    /** Weight of a launch on the other day type (weekday vs weekend). */
    const val OTHER_DAY_TYPE_WEIGHT = 0.25

    /** Hour-independent share of every launch ("recently used"). */
    const val BASE_WEIGHT = 0.1

    /** Scale of the optional usage-stats blend (log-damped). */
    const val USAGE_WEIGHT = 0.3

    /** Score multiplier added per unread notification, capped at [UNREAD_CAP]. */
    const val UNREAD_BOOST_PER = 0.15

    /** Unread notifications beyond this add no further boost. */
    const val UNREAD_CAP = 4

    /**
     * "USUALLY AROUND NOW" needs the hour window to carry at least this share of the
     * history score, from at least [MIN_WINDOW_LAUNCHES] launches on the same day type —
     * a single launch an hour ago is "recent", not a habit.
     */
    const val AROUND_NOW_SHARE = 0.5

    /** See [AROUND_NOW_SHARE]. */
    const val MIN_WINDOW_LAUNCHES = 2

    private const val MILLIS_PER_DAY = 86_400_000.0

    /**
     * Ranks [apps] for [now], best first.
     *
     * @param apps the catalog; hidden apps and apps of a paused profile are never returned
     *   (locked private-space apps never reach this point, the catalog drops them).
     * @param events Reach's launch history; order doesn't matter.
     * @param unread unread notification counts by package-in-profile.
     * @param usageByHour optional `hourOfDay -> package -> launches` from usage access.
     * @param limit maximum number of results.
     */
    fun rank(
        apps: List<CatalogApp>,
        events: List<LaunchEvent>,
        unread: Map<PackageUser, Int>,
        usageByHour: Map<Int, Map<String, Int>>,
        now: Moment,
        limit: Int = Int.MAX_VALUE,
    ): List<Prediction> {
        if (limit <= 0) return emptyList()
        val horizonStart = now.epochMillis - HORIZON_DAYS * MILLIS_PER_DAY.toLong()
        val eventsByApp = events.filter { it.epochMillis >= horizonStart }.groupBy { it.key }

        return apps
            .asSequence()
            .filterNot { it.isHidden || it.isProfilePaused }
            .mapNotNull { app -> score(app, eventsByApp[app.key].orEmpty(), unread, usageByHour, now) }
            .sortedWith(compareByDescending<Prediction> { it.score }.thenBy { it.app.label })
            .take(limit)
            .toList()
    }

    private fun score(
        app: CatalogApp,
        events: List<LaunchEvent>,
        unread: Map<PackageUser, Int>,
        usageByHour: Map<Int, Map<String, Int>>,
        now: Moment,
    ): Prediction? {
        var windowScore = 0.0
        var baseScore = 0.0
        var windowLaunches = 0
        for (event in events) {
            val recency = recencyWeight(now.epochMillis - event.epochMillis)
            val hour = hourWindowWeight(event.hourOfDay, now.hourOfDay)
            val sameDayType = event.isWeekend == now.isWeekend
            windowScore += recency * hour * (if (sameDayType) 1.0 else OTHER_DAY_TYPE_WEIGHT)
            baseScore += recency * BASE_WEIGHT
            if (hour > 0.0 && sameDayType) windowLaunches++
        }
        val usageScore = if (app.profile == ProfileType.PERSONAL) usageWeight(app.key.packageName, usageByHour, now) else 0.0

        val historyScore = windowScore + baseScore + usageScore
        if (historyScore <= 0.0) return null

        val unreadCount = unread[app.key.packageUser] ?: 0
        val score = historyScore * (1.0 + UNREAD_BOOST_PER * min(unreadCount, UNREAD_CAP))
        val reason =
            when {
                unreadCount > 0 -> {
                    PredictionReason.Unread(unreadCount)
                }

                windowLaunches >= MIN_WINDOW_LAUNCHES && windowScore >= AROUND_NOW_SHARE * historyScore -> {
                    PredictionReason.UsuallyAroundNow
                }

                else -> {
                    PredictionReason.Recent
                }
            }
        return Prediction(app = app, reason = reason, score = score)
    }

    /** 0.5^(age / half-life); future timestamps (clock changes) count as "now". */
    internal fun recencyWeight(ageMillis: Long): Double {
        val ageDays = ageMillis.coerceAtLeast(0L) / MILLIS_PER_DAY
        return 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    /** 1 for the same hour, [ADJACENT_HOUR_WEIGHT] for ±1h on a 24-hour circle, else 0. */
    internal fun hourWindowWeight(
        eventHour: Int,
        nowHour: Int,
    ): Double =
        when (circularHourDistance(eventHour, nowHour)) {
            0 -> 1.0
            1 -> ADJACENT_HOUR_WEIGHT
            else -> 0.0
        }

    /** Distance between two hours on a clock face: 23 and 0 are 1 apart, not 23. */
    internal fun circularHourDistance(
        a: Int,
        b: Int,
    ): Int {
        val d = abs(a - b) % 24
        return min(d, 24 - d)
    }

    private fun usageWeight(
        packageName: String,
        usageByHour: Map<Int, Map<String, Int>>,
        now: Moment,
    ): Double {
        if (usageByHour.isEmpty()) return 0.0
        var launches = 0.0
        for ((hour, byPackage) in usageByHour) {
            val count = byPackage[packageName] ?: continue
            launches += count * hourWindowWeight(hour, now.hourOfDay)
        }
        return if (launches > 0.0) USAGE_WEIGHT * ln(1.0 + launches) else 0.0
    }
}
