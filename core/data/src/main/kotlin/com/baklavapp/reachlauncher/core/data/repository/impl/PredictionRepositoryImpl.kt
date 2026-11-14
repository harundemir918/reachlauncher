package com.baklavapp.reachlauncher.core.data.repository.impl

import com.baklavapp.reachlauncher.core.data.di.ApplicationScope
import com.baklavapp.reachlauncher.core.data.di.DefaultDispatcher
import com.baklavapp.reachlauncher.core.data.domain.PredictionEngine
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.data.source.UsageStatsSource
import com.baklavapp.reachlauncher.core.data.time.hourTicks
import com.baklavapp.reachlauncher.core.model.Prediction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds [PredictionEngine] with live data and re-ranks when anything relevant changes:
 * the catalog (installs, hides, profile pause), launch history, unread counts, usage
 * access, and the local hour ([hourTicks]).
 *
 * The full ranking is computed once and shared ([shareIn]) between the Home "Now" row
 * (limit 3) and empty-query search (limit 4); each caller just takes its prefix.
 * Sharing stops 5 s after the last collector leaves, so nothing ticks while Home is in
 * the background, but a quick screen switch doesn't restart the pipeline.
 *
 * Privacy: predictions are built on [AppCatalogRepository.apps], which already drops
 * locked private-space apps; the engine drops hidden and paused-profile apps.
 *
 * Flutter analogy: a repository exposing a broadcast `Stream` built with rxdart's
 * `switchMap` (≈ `flatMapLatest`) and `combineLatest`, shared with `shareReplay(1)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PredictionRepositoryImpl
    @Inject
    constructor(
        catalog: AppCatalogRepository,
        history: LaunchHistoryRepository,
        notifications: NotificationCountSource,
        usageStats: UsageStatsSource,
        clock: Clock,
        @ApplicationScope appScope: CoroutineScope,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : PredictionRepository {
        private val ranked: Flow<List<Prediction>> =
            hourTicks(clock)
                .flatMapLatest { now ->
                    val horizonStart = now.epochMillis - PredictionEngine.HORIZON_DAYS * MILLIS_PER_DAY
                    // Usage stats are re-read once per hour (and when access is granted or revoked).
                    val usage =
                        usageStats.isAccessGranted.distinctUntilChanged().mapLatest { granted ->
                            if (granted) usageStats.launchesByHour(USAGE_DAYS) else emptyMap()
                        }
                    combine(
                        catalog.apps,
                        history.eventsSince(horizonStart),
                        notifications.countsByApp,
                        usage,
                    ) { apps, events, unread, usageByHour ->
                        PredictionEngine.rank(apps, events, unread, usageByHour, now)
                    }
                }.distinctUntilChanged()
                .flowOn(defaultDispatcher)
                .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

        override fun predictions(limit: Int): Flow<List<Prediction>> = ranked.map { it.take(limit.coerceAtLeast(0)) }.distinctUntilChanged()

        private companion object {
            const val MILLIS_PER_DAY = 86_400_000L

            /** Two weeks of usage stats: matches the engine's recency half-life. */
            const val USAGE_DAYS = 14
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
