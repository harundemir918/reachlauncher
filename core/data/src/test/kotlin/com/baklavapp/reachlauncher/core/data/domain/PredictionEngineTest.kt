package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PredictionEngineTest {
    // Wednesday (ISO 3), 09:00. Day offsets below stay on weekdays unless noted.
    private val now = Moment(epochMillis = 100 * DAY, hourOfDay = 9, dayOfWeek = 3)

    private fun app(
        name: String,
        hidden: Boolean = false,
        paused: Boolean = false,
        profile: ProfileType = ProfileType.PERSONAL,
    ) = CatalogApp(
        key = AppKey("com.example.$name", "com.example.$name.Main", if (profile == ProfileType.PERSONAL) 0 else 10),
        label = name,
        profile = profile,
        intent = ReachIntent.TOOLS,
        isUserAssigned = false,
        isHidden = hidden,
        launchCount = 0,
        isProfilePaused = paused,
    )

    private fun launches(
        app: CatalogApp,
        hour: Int,
        count: Int = 3,
        daysAgo: Int = 1,
        dayOfWeek: Int = 2,
    ) = List(count) { i ->
        LaunchEvent(app.key, now.epochMillis - daysAgo * DAY - i * 60_000L, hour, dayOfWeek)
    }

    private fun rank(
        apps: List<CatalogApp>,
        events: List<LaunchEvent>,
        unread: Map<PackageUser, Int> = emptyMap(),
        usage: Map<Int, Map<String, Int>> = emptyMap(),
        at: Moment = now,
    ) = PredictionEngine.rank(apps, events, unread, usage, at)

    @Test
    fun `empty history gives no predictions`() {
        assertThat(rank(listOf(app("a"), app("b")), emptyList())).isEmpty()
    }

    @Test
    fun `unread alone does not create a prediction`() {
        val a = app("a")
        assertThat(rank(listOf(a), emptyList(), unread = mapOf(a.key.packageUser to 5))).isEmpty()
    }

    @Test
    fun `launches in the current hour window beat launches at other hours`() {
        val morning = app("morning")
        val evening = app("evening")
        val adjacent = app("adjacent")
        val result =
            rank(
                listOf(evening, morning, adjacent),
                launches(morning, hour = 9) + launches(evening, hour = 20, count = 5) + launches(adjacent, hour = 10),
            )
        assertThat(result.map { it.app }).containsExactly(morning, adjacent, evening).inOrder()
        assertThat(result[0].reason).isEqualTo(PredictionReason.UsuallyAroundNow)
        assertThat(result[2].reason).isEqualTo(PredictionReason.Recent)
    }

    @Test
    fun `hour window wraps around midnight`() {
        val lateNight = app("late")
        val noon = app("noon")
        val atMidnight = now.copy(hourOfDay = 0)
        val result = rank(listOf(noon, lateNight), launches(lateNight, hour = 23) + launches(noon, hour = 12), at = atMidnight)
        assertThat(result.first().app).isEqualTo(lateNight)
        assertThat(result.first().reason).isEqualTo(PredictionReason.UsuallyAroundNow)
        assertThat(PredictionEngine.circularHourDistance(23, 0)).isEqualTo(1)
        assertThat(PredictionEngine.circularHourDistance(1, 22)).isEqualTo(3)
    }

    @Test
    fun `weekday habits count less on weekends and vice versa`() {
        val work = app("work")
        val leisure = app("leisure")
        val events =
            launches(work, hour = 9, dayOfWeek = 2) + // Tuesday
                launches(leisure, hour = 9, dayOfWeek = 6) // Saturday

        val onWednesday = rank(listOf(work, leisure), events, at = now)
        assertThat(onWednesday.first().app).isEqualTo(work)

        val onSunday = rank(listOf(work, leisure), events, at = now.copy(dayOfWeek = 7))
        assertThat(onSunday.first().app).isEqualTo(leisure)
    }

    @Test
    fun `recent launches outrank older ones with the same pattern`() {
        val fresh = app("fresh")
        val stale = app("stale")
        val result = rank(listOf(stale, fresh), launches(fresh, hour = 9, daysAgo = 1) + launches(stale, hour = 9, daysAgo = 28))
        assertThat(result.map { it.app }).containsExactly(fresh, stale).inOrder()
        // 28 days = two half-lives: a quarter of the weight, give or take a day.
        assertThat(result[1].score / result[0].score).isWithin(0.03).of(0.25 / 0.5.pow(1.0 / 14))
    }

    @Test
    fun `events beyond the horizon are ignored`() {
        val ancient = app("ancient")
        assertThat(rank(listOf(ancient), launches(ancient, hour = 9, daysAgo = 61))).isEmpty()
    }

    @Test
    fun `unread boosts and labels but does not dominate`() {
        val habit = app("habit")
        val noisy = app("noisy")
        val tied = app("tied")
        val events =
            launches(habit, hour = 9, count = 6) +
                launches(noisy, hour = 9, count = 1) +
                launches(tied, hour = 9, count = 1)
        val result = rank(listOf(habit, noisy, tied), events, unread = mapOf(noisy.key.packageUser to 2, tied.key.packageUser to 0))

        assertThat(result.map { it.app }).containsExactly(habit, noisy, tied).inOrder()
        assertThat(result[1].reason).isEqualTo(PredictionReason.Unread(2))
        assertThat(result[1].score).isGreaterThan(result[2].score)
    }

    @Test
    fun `unread counts belong to one profile`() {
        val personal = app("mail")
        val work = app("mail", profile = ProfileType.WORK)
        val events = launches(personal, hour = 9) + launches(work, hour = 9)
        val result = rank(listOf(personal, work), events, unread = mapOf(work.key.packageUser to 1))
        assertThat(result.first { it.app == work }.reason).isEqualTo(PredictionReason.Unread(1))
        assertThat(result.first { it.app == personal }.reason).isEqualTo(PredictionReason.UsuallyAroundNow)
    }

    @Test
    fun `hidden and paused apps are never predicted`() {
        val hidden = app("hidden", hidden = true)
        val paused = app("paused", paused = true, profile = ProfileType.WORK)
        val visible = app("visible")
        val events = launches(hidden, 9) + launches(paused, 9) + launches(visible, 9, count = 1)
        assertThat(rank(listOf(hidden, paused, visible), events).map { it.app }).containsExactly(visible)
    }

    @Test
    fun `a single launch in the window is recent, not a habit`() {
        val once = app("once")
        assertThat(rank(listOf(once), launches(once, hour = 9, count = 1)).single().reason).isEqualTo(PredictionReason.Recent)
    }

    @Test
    fun `usage stats blend in for personal apps only`() {
        val personal = app("music")
        val work = app("music", profile = ProfileType.WORK)
        val usage = mapOf(9 to mapOf("com.example.music" to 20))
        val result = rank(listOf(personal, work), emptyList(), usage = usage)
        assertThat(result.map { it.app }).containsExactly(personal)
    }

    @Test
    fun `limit truncates the ranking`() {
        val apps = List(5) { app("app$it") }
        val events = apps.flatMap { launches(it, hour = 9) }
        assertThat(PredictionEngine.rank(apps, events, emptyMap(), emptyMap(), now, limit = 3)).hasSize(3)
        assertThat(PredictionEngine.rank(apps, events, emptyMap(), emptyMap(), now, limit = 0)).isEmpty()
    }

    private fun Double.pow(x: Double) = Math.pow(this, x)

    private companion object {
        const val DAY = 86_400_000L
    }
}
