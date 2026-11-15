package com.baklavapp.reachlauncher.core.system.calendar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.CalendarSource
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import com.baklavapp.reachlauncher.core.system.util.broadcasts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of `CalendarContract.Instances` (an occurrence of an event — a weekly meeting has
 * one instance per week), reduced to what [NextEventPicker] needs.
 *
 * @property beginMillis for all-day events this is *UTC midnight* of the event's date
 *   (Calendar Provider convention), not local midnight.
 * @property declined the user answered "No" (`SELF_ATTENDEE_STATUS == ATTENDEE_STATUS_DECLINED`).
 * @property endMillis end of the occurrence (exclusive); for all-day events *UTC midnight* of
 *   the day after the last day, so a 3-day trip spans three UTC dates. Null = a single day.
 */
data class InstanceRow(
    val title: String?,
    val beginMillis: Long,
    val isAllDay: Boolean,
    val declined: Boolean,
    val endMillis: Long? = null,
)

/**
 * Picks Home's context-line event (spec §6.3 item 3, §7.6): "what's next", today or
 * tomorrow. Pure, so the rules are JVM-testable:
 * 1. Untitled and declined events are skipped.
 * 2. Timed events count only if they haven't started yet; all-day events count for *every*
 *    (UTC-dated) day they cover — day 2 of a 3-day trip still shows it.
 * 3. Order: today's next timed event → today's all-day event → tomorrow's first timed event →
 *    tomorrow's all-day event. A meeting at 15:00 is more useful than "Birthday" all day,
 *    but today's birthday beats tomorrow's meeting.
 */
object NextEventPicker {
    /** The event to show at [nowMillis] in [zone], or null. */
    fun pick(
        rows: List<InstanceRow>,
        nowMillis: Long,
        zone: ZoneId,
    ): CalendarEvent? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val tomorrow = today.plusDays(1)
        val usable = rows.filter { !it.title.isNullOrBlank() && !it.declined }
        val timed = usable.filter { !it.isAllDay && it.beginMillis >= nowMillis }.sortedBy { it.beginMillis }
        val allDay = usable.filter { it.isAllDay }.sortedBy { it.beginMillis }

        fun LocalDate.timed() = timed.firstOrNull { Instant.ofEpochMilli(it.beginMillis).atZone(zone).toLocalDate() == this }

        fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

        fun LocalDate.allDay() =
            allDay.firstOrNull { row ->
                val first = row.beginMillis.utcDate()
                val endExclusive = row.endMillis?.utcDate()?.takeIf { it > first } ?: first.plusDays(1)
                this >= first && this < endExclusive
            }
        val chosen = today.timed() ?: today.allDay() ?: tomorrow.timed() ?: tomorrow.allDay() ?: return null
        return CalendarEvent(title = chosen.title.orEmpty().trim(), beginEpochMillis = chosen.beginMillis, isAllDay = chosen.isAllDay)
    }
}

/**
 * The next calendar event, from the Calendar Provider (`CalendarContract.Instances`).
 *
 * **Permission.** `READ_CALENDAR` is a runtime permission; Home asks for it from an optional,
 * dismissible card. Without it nothing is queried and [nextEvent] is null. The grant is
 * re-read on every Activity resume ([AccessRechecks]) — the permission dialog itself pauses
 * and resumes the Activity, as does a trip to Settings.
 *
 * **When it re-reads** (only while collected, i.e. while Home is on screen):
 * - on collection and on every resume;
 * - `ACTION_PROVIDER_CHANGED` for the calendar authority — the provider's "something
 *   changed" broadcast (sync, an edit in the calendar app);
 * - `ACTION_TIME_TICK` (every minute, so an event that just started gives way to the next
 *   one), time/date/zone changes.
 *
 * One query covers local today and tomorrow; only visible calendars are read; the query
 * returns occurrences, so recurring events just work. Nothing is stored.
 *
 * Flutter analogy: the native half of a `device_calendar`-style plugin: a `ContentResolver`
 * query (like a SQL `SELECT` against another app's database) re-run when a platform
 * broadcast says the data changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CalendarProviderSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val rechecks: AccessRechecks,
        private val clock: Clock,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CalendarSource {
        override val isPermissionGranted: Flow<Boolean> =
            rechecks.ticks
                .map { isGranted() }
                .distinctUntilChanged()

        override val nextEvent: Flow<CalendarEvent?> =
            isPermissionGranted
                .flatMapLatest { granted ->
                    if (!granted) {
                        flowOf(null)
                    } else {
                        merge(
                            rechecks.ticks.map { },
                            context.broadcasts(TIME_ACTIONS).map { },
                            providerChanges(),
                        ).conflate().map { query() }
                    }
                }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        private fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        /**
         * `ACTION_PROVIDER_CHANGED` for `content://com.android.calendar`. Registered *exported*
         * on purpose: the broadcast comes from the Calendar Provider's own app, not from the
         * system UID, so a not-exported receiver would never get it. Another app faking it can
         * only make us re-run one cheap query.
         */
        private fun providerChanges(): Flow<Unit> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(Unit)
                        }
                    }
                val filter =
                    IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
                        addDataScheme("content")
                        addDataAuthority(CalendarContract.AUTHORITY, null)
                    }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                awaitClose { context.unregisterReceiver(receiver) }
            }

        private fun query(): CalendarEvent? {
            val now = clock.millis()
            val zone = clock.zone
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val begin = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end =
                today
                    .plusDays(2)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val uri =
                CalendarContract.Instances.CONTENT_URI
                    .buildUpon()
                    .also {
                        ContentUris.appendId(it, begin)
                        ContentUris.appendId(it, end)
                    }.build()
            val rows =
                try {
                    context.contentResolver
                        .query(
                            uri,
                            PROJECTION,
                            "${CalendarContract.Instances.VISIBLE} = 1",
                            null,
                            "${CalendarContract.Instances.BEGIN} ASC",
                        )?.use { it.rows() }
                } catch (e: SecurityException) {
                    null // Revoked mid-flight.
                } catch (e: IllegalArgumentException) {
                    null // No calendar provider on this device.
                }
            return NextEventPicker.pick(rows.orEmpty(), now, zone)
        }

        private fun Cursor.rows(): List<InstanceRow> =
            buildList {
                while (moveToNext()) {
                    add(
                        InstanceRow(
                            title = getString(0),
                            beginMillis = getLong(1),
                            isAllDay = getInt(2) != 0,
                            declined = getInt(3) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                            endMillis = if (isNull(4)) null else getLong(4),
                        ),
                    )
                }
            }

        private companion object {
            val PROJECTION =
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                    CalendarContract.Instances.END,
                )
            val TIME_ACTIONS =
                listOf(
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_DATE_CHANGED,
                )
        }
    }
