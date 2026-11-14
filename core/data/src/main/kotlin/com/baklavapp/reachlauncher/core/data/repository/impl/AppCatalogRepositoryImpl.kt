package com.baklavapp.reachlauncher.core.data.repository.impl

import com.baklavapp.reachlauncher.core.data.db.AppAssignmentDao
import com.baklavapp.reachlauncher.core.data.db.AppCacheDao
import com.baklavapp.reachlauncher.core.data.db.CachedAppEntity
import com.baklavapp.reachlauncher.core.data.db.LaunchEventDao
import com.baklavapp.reachlauncher.core.data.db.cachedAppEntity
import com.baklavapp.reachlauncher.core.data.db.key
import com.baklavapp.reachlauncher.core.data.db.toCachedApp
import com.baklavapp.reachlauncher.core.data.db.toUserAssignment
import com.baklavapp.reachlauncher.core.data.di.ApplicationScope
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.domain.CatalogAssembler
import com.baklavapp.reachlauncher.core.data.domain.IntentCategorizer
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.source.DefaultAppsSource
import com.baklavapp.reachlauncher.core.data.source.InstalledAppsSource
import com.baklavapp.reachlauncher.core.data.source.ProfileSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.ReachIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app catalog: Room cache first, kept in sync with the system in the background.
 *
 * How the "instant Home" promise is met:
 * 1. [apps] is built from Room tables only. Room emits the cached rows as soon as it is
 *    collected — no `LauncherApps` query sits in front of the first frame.
 * 2. Independently, a coroutine in the process-wide [appScope] collects
 *    [InstalledAppsSource.installedApps] and writes the *difference* into the cache.
 *    Room notices the table change and [apps] re-emits — that is the "fresh data follows
 *    as a second emission". Unchanged data writes nothing, so nothing re-emits.
 *
 * Privacy (D13): every emission is filtered against the live [ProfileSource.profiles]
 * (see [CatalogAssembler]), so locked private-space apps never appear, even from cache.
 *
 * Flutter analogy: a repository class behind your BLoCs that exposes a `Stream` combining
 * a drift `watch()` query with a platform-plugin stream (rxdart `combineLatest`), plus a
 * long-lived `StreamSubscription` started in the constructor that writes plugin events
 * into the database.
 */
@Singleton
class AppCatalogRepositoryImpl
    @Inject
    constructor(
        private val cacheDao: AppCacheDao,
        private val assignmentDao: AppAssignmentDao,
        launchEventDao: LaunchEventDao,
        private val installedAppsSource: InstalledAppsSource,
        profileSource: ProfileSource,
        private val defaultAppsSource: DefaultAppsSource,
        @param:ApplicationScope private val appScope: CoroutineScope,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AppCatalogRepository {
        /** Serialises syncs, so a [refresh] and a live system update never interleave their diffs. */
        private val syncMutex = Mutex()

        /** The last error the background sync swallowed; exposed for debugging and tests. */
        @Volatile
        var lastSyncError: Throwable? = null
            private set

        init {
            // Lives as long as the process (Hilt singleton + application scope). A failing
            // system query (e.g. a dead binder during a system update) must not crash the
            // launcher: the catalog keeps serving the cache and the source is re-subscribed
            // with a capped back-off.
            appScope.launch(ioDispatcher) {
                installedAppsSource.installedApps
                    .retryWhen { cause, attempt ->
                        lastSyncError = cause
                        delay(minOf(MAX_RETRY_DELAY_MS, RETRY_BASE_DELAY_MS shl attempt.coerceAtMost(MAX_SHIFT).toInt()))
                        true
                    }.collect { safeSync(it) }
            }
        }

        override val apps: Flow<List<CatalogApp>> =
            combine(
                cacheDao.observeAll(),
                assignmentDao.observeAll(),
                launchEventDao.observeCounts(),
                profileSource.profiles,
            ) { cache, assignments, counts, profiles ->
                CatalogAssembler.assemble(
                    cached = cache.map { it.toCachedApp() },
                    assignments = assignments.associate { it.key() to it.toUserAssignment() },
                    launchCounts = counts.associate { it.key() to it.count },
                    profiles = profiles,
                    // Read per emission: the user may change the device language while Reach runs.
                    locale = Locale.getDefault(),
                )
            }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override suspend fun refresh() {
            withContext(ioDispatcher) { safeSync(installedAppsSource.installedApps.first()) }
        }

        override suspend fun moveToIntent(
            key: AppKey,
            intent: ReachIntent,
        ) = withContext(ioDispatcher) {
            assignmentDao.setIntent(key.packageName, key.activityName, key.userSerial, intent.storageKey)
        }

        override suspend fun setHidden(
            key: AppKey,
            hidden: Boolean,
        ) = withContext(ioDispatcher) {
            assignmentDao.setHidden(key.packageName, key.activityName, key.userSerial, hidden)
        }

        private suspend fun safeSync(installed: List<InstalledApp>) {
            try {
                sync(installed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // Room throws this when the database can't be opened/written (e.g. disk full).
                lastSyncError = e
            } catch (e: SecurityException) {
                // Role lookups can throw on unusual OEM builds; skip this sync, keep the cache.
                lastSyncError = e
            }
        }

        private companion object {
            const val RETRY_BASE_DELAY_MS = 1_000L
            const val MAX_RETRY_DELAY_MS = 30_000L
            const val MAX_SHIFT = 5L
        }

        /**
         * Writes the difference between [installed] and the cache: upserts new or changed
         * rows (label, category, profile, auto intent) and deletes apps no longer installed.
         * User assignments are deliberately kept for removed apps, so a reinstall restores
         * the user's choice.
         */
        private suspend fun sync(installed: List<InstalledApp>) =
            syncMutex.withLock {
                val talkPackages = defaultAppsSource.talkRolePackages()
                val fresh: List<CachedAppEntity> =
                    installed
                        .distinctBy { it.key }
                        .map { app -> cachedAppEntity(app, IntentCategorizer.categorize(app.category, app.key.packageName, talkPackages)) }
                val current = cacheDao.getAll()
                val currentSet = current.toSet()
                val freshKeys = fresh.mapTo(HashSet()) { it.key() }
                val upserts = fresh.filterNot { it in currentSet }
                val deletes = current.filterNot { it.key() in freshKeys }
                if (upserts.isNotEmpty() || deletes.isNotEmpty()) cacheDao.applyDiff(upserts, deletes)
            }
    }
