package com.baklavapp.reachlauncher.core.system.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.UsageStatsSource
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** An app coming to the foreground (`ACTIVITY_RESUMED`), the input of [UsageBuckets]. */
data class ResumeEvent(
    val packageName: String,
    val timeMillis: Long,
)

/**
 * Turns the system's foreground events into "launches per local hour", the shape the
 * prediction engine blends in (D25). Pure, so the rules are JVM-testable:
 * - Consecutive resumes of the *same* app count once: moving between two screens of one app
 *   fires `ACTIVITY_RESUMED` for each, but it's one "open". Anything in between — including
 *   Reach itself — separates two launches.
 * - Reach's own resumes are never counted (Home is not an app the user "launches").
 * - The hour is the local hour in [zone] at the event's time, like Reach's own history (D27).
 */
object UsageBuckets {
    /** `hourOfDay -> package -> launches`; events must be in time order (as `queryEvents` returns them). */
    fun launchesByHour(
        events: List<ResumeEvent>,
        zone: ZoneId,
        ownPackage: String,
    ): Map<Int, Map<String, Int>> {
        val result = sortedMapOf<Int, MutableMap<String, Int>>()
        var previous: String? = null
        for (event in events) {
            if (event.packageName != previous && event.packageName != ownPackage) {
                val hour = Instant.ofEpochMilli(event.timeMillis).atZone(zone).hour
                val byPackage = result.getOrPut(hour) { mutableMapOf() }
                byPackage[event.packageName] = (byPackage[event.packageName] ?: 0) + 1
            }
            previous = event.packageName
        }
        return result
    }
}

/**
 * Optional usage data (spec §6.2) from `UsageStatsManager.queryEvents`.
 *
 * **The access.** `PACKAGE_USAGE_STATS` is a *special* permission: declaring it in the
 * manifest does nothing until the user switches Reach on under Settings › Usage access
 * (`AppLauncher.open(SystemScreen.USAGE_ACCESS)`). The real check is the app-op
 * `OPSTR_GET_USAGE_STATS`: `MODE_ALLOWED` = on; `MODE_DEFAULT` means "fall back to the
 * permission check" (some OEM settings screens set it). It's re-read on every Activity
 * resume ([AccessRechecks]).
 *
 * **What is read.** Only this user's (the personal profile's) foreground events of the last
 * `days` days — `queryEvents` never returns other profiles' data to us. Nothing is stored:
 * the prediction engine reads it once an hour and keeps the derived counts in memory.
 *
 * Flutter analogy: the native side of a `usage_stats` plugin call, plus the
 * "open settings, come back, re-check" dance you'd do with `permission_handler`.
 */
@Singleton
class UsageStatsManagerSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        rechecks: AccessRechecks,
        private val clock: Clock,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : UsageStatsSource {
        override val isAccessGranted: Flow<Boolean> =
            rechecks.ticks
                .map { isGranted() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        /** Whether usage access is on (see the class KDoc). */
        fun isGranted(): Boolean {
            val appOps = context.getSystemService<AppOpsManager>() ?: return false
            // checkOpNoThrow works on every level. (API 29 deprecated it for unsafeCheckOpNoThrow;
            // the current SDK reverses that and deprecates unsafeCheckOpNoThrow instead.)
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> {
                    true
                }

                AppOpsManager.MODE_DEFAULT -> {
                    context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
                }

                else -> {
                    false
                }
            }
        }

        override suspend fun launchesByHour(days: Int): Map<Int, Map<String, Int>> =
            withContext(ioDispatcher) {
                if (days <= 0 || !isGranted()) return@withContext emptyMap()
                val manager = context.getSystemService<UsageStatsManager>() ?: return@withContext emptyMap()
                val end = clock.millis()
                val begin = end - days * MILLIS_PER_DAY
                val resumes =
                    try {
                        // Null while the user is locked (direct boot); throws if access vanished.
                        manager.queryEvents(begin, end)?.resumes()
                    } catch (e: SecurityException) {
                        null
                    } catch (e: RuntimeException) {
                        // Some OEM builds throw from queryEvents/getNextEvent (e.g. a corrupt
                        // usage database). Usage data is an optional blend: just skip it.
                        null
                    } ?: return@withContext emptyMap()
                UsageBuckets.launchesByHour(resumes, clock.zone, context.packageName)
            }

        private fun UsageEvents.resumes(): List<ResumeEvent> {
            val resumed = resumedEventType()
            val event = UsageEvents.Event()
            return buildList {
                while (getNextEvent(event)) {
                    if (event.eventType == resumed) add(ResumeEvent(event.packageName, event.timeStamp))
                }
            }
        }

        /** `ACTIVITY_RESUMED` (API 29) is the renamed `MOVE_TO_FOREGROUND` (same value, 1). */
        private fun resumedEventType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }

        private companion object {
            const val MILLIS_PER_DAY = 86_400_000L
        }
    }
