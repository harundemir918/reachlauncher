package com.baklavapp.reachlauncher.core.data.repository.impl

import com.baklavapp.reachlauncher.core.data.db.LaunchEventDao
import com.baklavapp.reachlauncher.core.data.db.LaunchEventEntity
import com.baklavapp.reachlauncher.core.data.db.key
import com.baklavapp.reachlauncher.core.data.db.toLaunchEvent
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reach's own launch history in Room. Recording our own launches needs no permission,
 * which is why it is the primary prediction source (spec §6.2).
 *
 * Hour and weekday are computed in [clock]'s zone *when the launch is recorded* and
 * stored, never recomputed: after a flight to another time zone, "usually around 8 am"
 * still means 8 am where the user was at the time.
 *
 * Flutter analogy: a repository over a drift/sqflite table — `insert` returns a `Future`,
 * the counts are a `watch()` stream.
 */
@Singleton
class LaunchHistoryRepositoryImpl
    @Inject
    constructor(
        private val dao: LaunchEventDao,
        private val clock: Clock,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : LaunchHistoryRepository {
        override suspend fun recordLaunch(
            key: AppKey,
            epochMillis: Long,
        ) {
            val local = Instant.ofEpochMilli(epochMillis).atZone(clock.zone)
            withContext(ioDispatcher) {
                dao.insert(
                    LaunchEventEntity(
                        packageName = key.packageName,
                        activityName = key.activityName,
                        userSerial = key.userSerial,
                        epochMillis = epochMillis,
                        hourOfDay = local.hour,
                        dayOfWeek = local.dayOfWeek.value,
                    ),
                )
            }
        }

        override val launchCounts: Flow<Map<AppKey, Int>> =
            dao
                .observeCounts()
                .map { rows -> rows.associate { it.key() to it.count } }
                .flowOn(ioDispatcher)

        override fun eventsSince(sinceEpochMillis: Long): Flow<List<LaunchEvent>> =
            dao
                .observeSince(sinceEpochMillis)
                .map { rows -> rows.map { it.toLaunchEvent() } }
                .flowOn(ioDispatcher)
    }
