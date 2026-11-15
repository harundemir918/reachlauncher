@file:OptIn(ExperimentalCoroutinesApi::class)

package com.baklavapp.reachlauncher.core.data.repository.impl

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.SchedulerClock
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeNotificationCountSource
import com.baklavapp.reachlauncher.core.data.fake.FakeUsageStatsSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The repository's wiring: which changes trigger a re-rank. Scoring itself is in PredictionEngineTest. */
class PredictionRepositoryImplTest {
    private val catalog = FakeAppCatalogRepository()
    private val history = FakeLaunchHistoryRepository(ZoneOffset.UTC)
    private val notifications = FakeNotificationCountSource()
    private val usage = FakeUsageStatsSource()

    // Wednesday 2026-11-11 09:59 UTC.
    private val start = ZonedDateTime.of(2026, 11, 11, 9, 59, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun app(name: String) =
        CatalogApp(
            key = AppKey("com.example.$name", "com.example.$name.Main", 0),
            label = name,
            profile = ProfileType.PERSONAL,
            intent = ReachIntent.TOOLS,
            isUserAssigned = false,
            isHidden = false,
            launchCount = 0,
        )

    private fun TestScope.repository() =
        PredictionRepositoryImpl(
            catalog = catalog,
            history = history,
            notifications = notifications,
            usageStats = usage,
            clock = SchedulerClock(testScheduler, start, ZoneOffset.UTC),
            appScope = backgroundScope,
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    /** Records [count] launches at [hour]:15 on the previous [count] Wednesdays. */
    private suspend fun launch(
        app: CatalogApp,
        hour: Int,
        count: Int = 3,
    ) = repeat(count) { i ->
        val lastWednesday = ZonedDateTime.of(2026, 11, 4, hour, 15, 0, 0, ZoneOffset.UTC)
        history.recordLaunch(app.key, lastWednesday.minusWeeks(i.toLong()).toInstant().toEpochMilli())
    }

    @Test
    fun `empty history emits an empty list`() =
        runTest {
            catalog.state.value = listOf(app("a"))
            repository().predictions(3).test {
                assertThat(awaitItem()).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `re-ranks on history, unread, catalog and hour changes`() =
        runTest {
            val coffee = app("coffee")
            val news = app("news")
            catalog.state.value = listOf(coffee, news)
            val repo = repository()

            repo.predictions(3).test {
                assertThat(awaitItem()).isEmpty()

                // History: coffee at 9, news at 10.
                launch(coffee, hour = 9)
                launch(news, hour = 10)
                var ranked = expectMostRecentItem()
                assertThat(ranked.map { it.app.label }).containsExactly("coffee", "news").inOrder()

                // Unread notifications relabel news.
                notifications.granted.value = true
                notifications.counts.value = mapOf(news.key.packageUser to 2)
                ranked = expectMostRecentItem()
                assertThat(ranked.single { it.app == news }.reason).isEqualTo(PredictionReason.Unread(2))

                // The hour turns 10:00: news' hour now.
                notifications.counts.value = emptyMap()
                expectMostRecentItem()
                testScheduler.advanceTimeBy(61_000)
                ranked = expectMostRecentItem()
                assertThat(ranked.first().app.label).isEqualTo("news")
                assertThat(ranked.first().reason).isEqualTo(PredictionReason.UsuallyAroundNow)

                // Hiding news in the catalog removes it.
                catalog.state.update { list -> list.map { if (it == news) it.copy(isHidden = true) else it } }
                ranked = expectMostRecentItem()
                assertThat(ranked.map { it.app.label }).containsExactly("coffee")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `limit is applied per caller`() =
        runTest {
            val apps = List(5) { app("app$it") }
            catalog.state.value = apps
            apps.forEach { launch(it, hour = 9, count = 1) }
            val repo = repository()
            repo.predictions(3).test {
                assertThat(expectMostRecentItem()).hasSize(3)
                cancelAndIgnoreRemainingEvents()
            }
            repo.predictions(4).test {
                assertThat(expectMostRecentItem()).hasSize(4)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unlocked private-space apps are never predicted (D71)`() =
        runTest {
            val coffee = app("coffee")
            val vault = app("vault").copy(profile = ProfileType.PRIVATE)
            catalog.state.value = listOf(coffee, vault)
            launch(coffee, hour = 9, count = 1)
            launch(vault, hour = 9, count = 3)
            repository().predictions(3).test {
                assertThat(expectMostRecentItem().map { it.app }).containsExactly(coffee)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
