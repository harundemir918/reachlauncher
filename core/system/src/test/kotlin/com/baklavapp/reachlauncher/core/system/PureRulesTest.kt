package com.baklavapp.reachlauncher.core.system

import android.media.session.PlaybackState
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.system.calendar.InstanceRow
import com.baklavapp.reachlauncher.core.system.calendar.NextEventPicker
import com.baklavapp.reachlauncher.core.system.media.MediaSessionChooser
import com.baklavapp.reachlauncher.core.system.media.SessionSnapshot
import com.baklavapp.reachlauncher.core.system.usage.ResumeEvent
import com.baklavapp.reachlauncher.core.system.usage.UsageBuckets
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Plain JVM tests of the decision rules extracted from the system sources: media session
 * choice and mapping, usage-event bucketing, next-event selection. (`PlaybackState.STATE_*`
 * are compile-time constants, so no Android runtime is needed.)
 */
class PureRulesTest {
    // ---- Media -------------------------------------------------------------------------

    private fun session(
        pkg: String,
        state: Int,
        title: String? = "Track of $pkg",
    ) = SessionSnapshot(
        packageName = pkg,
        title = title,
        artist = "Artist",
        state = state,
        positionMillis = 30_000,
        updatedAtMillis = 1_000,
        speed = 1f,
        durationMillis = 180_000,
    )

    @Test
    fun `a playing session beats an earlier paused one`() {
        val sessions = listOf(session("podcast", PlaybackState.STATE_PAUSED), session("music", PlaybackState.STATE_PLAYING))
        assertThat(MediaSessionChooser.choose(sessions)).isEqualTo(1)
    }

    @Test
    fun `ties keep the system order and buffering counts as playing`() {
        val sessions = listOf(session("a", PlaybackState.STATE_BUFFERING), session("b", PlaybackState.STATE_PLAYING))
        assertThat(MediaSessionChooser.choose(sessions)).isEqualTo(0)
    }

    @Test
    fun `stopped, state-less and untitled sessions are never shown`() {
        val sessions =
            listOf(
                session("a", PlaybackState.STATE_STOPPED),
                session("b", PlaybackState.STATE_NONE),
                session("c", PlaybackState.STATE_PLAYING, title = " "),
                session("d", PlaybackState.STATE_ERROR),
            )
        assertThat(MediaSessionChooser.choose(sessions)).isNull()
        assertThat(MediaSessionChooser.choose(emptyList())).isNull()
    }

    @Test
    fun `mapping keeps the label and clamps unknown position and duration`() {
        val state =
            MediaSessionChooser.toMediaState(
                session("com.spotify.music", PlaybackState.STATE_PAUSED).copy(positionMillis = -1, durationMillis = -1, artist = ""),
                sourceLabel = "Spotify",
            )
        assertThat(state.sourceLabel).isEqualTo("Spotify")
        assertThat(state.sourcePackage).isEqualTo("com.spotify.music")
        assertThat(state.isPlaying).isFalse()
        assertThat(state.positionMillis).isEqualTo(0)
        assertThat(state.durationMillis).isEqualTo(0)
        assertThat(state.artist).isNull()
        assertThat(state.positionUpdatedAtMillis).isEqualTo(1_000)
    }

    // ---- Usage stats -------------------------------------------------------------------

    private val istanbul: ZoneId = ZoneId.of("Europe/Istanbul")

    private fun at(
        hour: Int,
        minute: Int,
    ) = ZonedDateTime.of(2026, 11, 14, hour, minute, 0, 0, istanbul).toInstant().toEpochMilli()

    @Test
    fun `consecutive resumes of one app count once and buckets use local hours`() {
        val events =
            listOf(
                ResumeEvent("maps", at(8, 0)),
                ResumeEvent("maps", at(8, 1)), // Second screen of the same app.
                ResumeEvent("reach", at(8, 5)), // Home in between…
                ResumeEvent("maps", at(8, 6)), // …so this is a new launch.
                ResumeEvent("music", at(21, 30)),
            )
        val buckets = UsageBuckets.launchesByHour(events, istanbul, ownPackage = "reach")
        assertThat(buckets).containsExactly(8, mapOf("maps" to 2), 21, mapOf("music" to 1))
    }

    @Test
    fun `no events give no buckets`() {
        assertThat(UsageBuckets.launchesByHour(emptyList(), istanbul, "reach")).isEmpty()
    }

    // ---- Calendar ----------------------------------------------------------------------

    private val now = at(10, 0)
    private val utcMidnightToday = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val day = 86_400_000L

    @Test
    fun `today's next timed event wins over all-day and started events`() {
        val rows =
            listOf(
                InstanceRow("Started", at(9, 30), isAllDay = false, declined = false),
                InstanceRow("Birthday", utcMidnightToday, isAllDay = true, declined = false),
                InstanceRow("Lunch", at(12, 30), isAllDay = false, declined = false),
                InstanceRow("Standup", at(11, 0), isAllDay = false, declined = false),
            )
        assertThat(NextEventPicker.pick(rows, now, istanbul)).isEqualTo(CalendarEvent("Standup", at(11, 0), isAllDay = false))
    }

    @Test
    fun `today's all-day event beats tomorrow's meeting, declined and untitled are skipped`() {
        val rows =
            listOf(
                InstanceRow("Declined", at(15, 0), isAllDay = false, declined = true),
                InstanceRow("", at(16, 0), isAllDay = false, declined = false),
                InstanceRow("Tomorrow", at(9, 0) + day, isAllDay = false, declined = false),
                InstanceRow("Birthday", utcMidnightToday, isAllDay = true, declined = false),
            )
        assertThat(NextEventPicker.pick(rows, now, istanbul)?.title).isEqualTo("Birthday")
    }

    @Test
    fun `falls back to tomorrow, and nothing beyond tomorrow`() {
        val tomorrow = listOf(InstanceRow("Dentist", at(9, 0) + day, isAllDay = false, declined = false))
        assertThat(NextEventPicker.pick(tomorrow, now, istanbul)?.title).isEqualTo("Dentist")
        val later = listOf(InstanceRow("Later", at(9, 0) + 2 * day, isAllDay = false, declined = false))
        assertThat(NextEventPicker.pick(later, now, istanbul)).isNull()
    }

    @Test
    fun `a multi-day all-day event shows on every day it covers, not after`() {
        // A 3-day trip that started yesterday (UTC dates 13, 14, 15; end is exclusive: 16).
        val trip =
            listOf(
                InstanceRow(
                    "Trip",
                    beginMillis = utcMidnightToday - day,
                    isAllDay = true,
                    declined = false,
                    endMillis = utcMidnightToday + 2 * day,
                ),
            )
        assertThat(NextEventPicker.pick(trip, now, istanbul)?.title).isEqualTo("Trip")
        // Two days later the trip is over (today = 16th): nothing to show.
        assertThat(NextEventPicker.pick(trip, now + 2 * day, istanbul)).isNull()
    }

    @Test
    fun `all-day events use their UTC date even far from UTC`() {
        // Birthday on the 14th (stored as UTC midnight). In UTC-8 at 20:00 on the 13th, the
        // local "today" is the 13th, so the birthday is tomorrow's all-day event; in UTC+13 at
        // 08:00 on the 14th it is today's.
        val birthday = listOf(InstanceRow("Birthday", utcMidnightToday, isAllDay = true, declined = false))
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val evening13th = ZonedDateTime.of(2026, 11, 13, 20, 0, 0, 0, losAngeles).toInstant().toEpochMilli()
        assertThat(NextEventPicker.pick(birthday, evening13th, losAngeles)?.title).isEqualTo("Birthday")
        val tongatapu = ZoneId.of("Pacific/Tongatapu")
        val morning14th = ZonedDateTime.of(2026, 11, 14, 8, 0, 0, 0, tongatapu).toInstant().toEpochMilli()
        assertThat(NextEventPicker.pick(birthday, morning14th, tongatapu)?.title).isEqualTo("Birthday")
        // …and in UTC+13 on the 15th it is gone, although it is still the 14th in UTC.
        val morning15th = ZonedDateTime.of(2026, 11, 15, 8, 0, 0, 0, tongatapu).toInstant().toEpochMilli()
        assertThat(NextEventPicker.pick(birthday, morning15th, tongatapu)).isNull()
    }
}
