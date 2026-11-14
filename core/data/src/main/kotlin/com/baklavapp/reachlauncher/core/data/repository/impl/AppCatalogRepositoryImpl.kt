package com.baklavapp.reachlauncher.core.data.repository.impl

import android.database.sqlite.SQLiteException
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
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
 *    collected — no `LauncherApps` query sits in front of the first frame. The live
 *    profile list is combined in too, but it *starts* as an empty list, so the first
 *    emission never waits for `UserManager` either (see [apps]).
 * 2. Independently, a coroutine in the process-wide [appScope] collects
 *    [InstalledAppsSource.installedApps] and writes the *difference* into the cache.
 *    Room notices the table change and [apps] re-emits — that is the "fresh data follows
 *    as a second emission". Unchanged data writes nothing, so nothing re-emits.
 *
 * Privacy (D13): every emission is filtered against the profile state known so far (see
 * [CatalogAssembler]), so locked private-space apps never appear, even from cache.
 *
 * Robustness: a launcher must survive a full disk or a corrupt database. Sync failures
 * (`SQLiteException` and friends) are logged and swallowed, a failing observed query is
 * retried with back-off, and the cache keeps serving whatever it last had.
 *
 * Flutter analogy: a repository class behind your BLoCs that exposes a `Stream` combining
 * a drift `watch()` query with a platform-plugin stream (rxdart `combineLatest`), shared
 * with `shareReplay(1)`, plus a long-lived `StreamSubscription` started in the constructor
 * that writes plugin events into the database.
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

        /**
         * The locale labels are sorted for. Reach handles locale changes itself (no Activity
         * restart, D8), so nothing else would re-sort: [refresh] — called on every resume,
         * which follows any trip to the language settings — updates it.
         */
        private val sortLocale = MutableStateFlow(Locale.getDefault())

        /** The last error the background sync or the observed query swallowed; for debugging and tests. */
        @Volatile
        var lastSyncError: Throwable? = null
            private set

        init {
            // Lives as long as the process (Hilt singleton + application scope). A failing
            // system query (e.g. a dead binder during a system update) must not crash the
            // launcher: the catalog keeps serving the cache and the source is re-subscribed
            // with a capped back-off. Database errors while writing are handled in safeSync.
            appScope.launch(ioDispatcher) {
                installedAppsSource.installedApps
                    .retryWhen { cause, attempt ->
                        record(cause)
                        delay(backoffMillis(attempt))
                        true
                    }.collect { safeSync(it) }
            }
        }

        /**
         * Room tables + profile state + sort locale → the catalog.
         *
         * Cache-first: `ProfileSource.profiles` reads `UserManager`/`LauncherApps` before its
         * first emission, so it starts here as an empty list (`onStart`). The first emission
         * therefore depends on Room alone. With no profile data yet, the privacy filter fails
         * closed: private-space apps are hidden, personal and work apps are shown and not
         * marked paused (a stale "paused" flag would be worse — the launcher decides from live
         * state anyway, D34). The real profile list follows within milliseconds.
         *
         * Shared: Home and the prediction pipeline both collect [apps]; `shareIn` runs one
         * combine for both. `replayExpirationMillis = 0` drops the replayed list once nobody
         * listens, so a list assembled while the private space was unlocked can never be
         * replayed after it was locked in the meantime.
         */
        override val apps: Flow<List<CatalogApp>> =
            combine(
                cacheDao.observeAll(),
                assignmentDao.observeAll(),
                launchEventDao.observeCounts(),
                profileSource.profiles.onStart { emit(emptyList()) },
                sortLocale,
            ) { cache, assignments, counts, profiles, locale ->
                CatalogAssembler.assemble(
                    cached = cache.map { it.toCachedApp() },
                    assignments = assignments.associate { it.key() to it.toUserAssignment() },
                    launchCounts = counts.associate { it.key() to it.count },
                    profiles = profiles,
                    locale = locale,
                )
            }.distinctUntilChanged()
                .retryWhen { cause, attempt ->
                    // A database that can't be read right now (disk full, file locked or
                    // corrupt): keep the last list on screen and try again later. Anything
                    // else is a bug and should surface.
                    if (cause !is SQLiteException) return@retryWhen false
                    record(cause)
                    delay(backoffMillis(attempt))
                    true
                }.flowOn(ioDispatcher)
                .shareIn(
                    appScope,
                    SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS, replayExpirationMillis = 0),
                    replay = 1,
                )

        /**
         * One-shot re-read. Implementation note: the contract only offers the
         * [InstalledAppsSource.installedApps] stream, so this subscribes it and takes the
         * first (initial) read — registering and unregistering the system callback once per
         * resume. Cheap enough; a dedicated one-shot read would need a contract change.
         */
        override suspend fun refresh() {
            sortLocale.value = Locale.getDefault()
            withContext(ioDispatcher) {
                val installed =
                    try {
                        installedAppsSource.installedApps.first()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RuntimeException) {
                        // Dead binder, refused profile… keep the cache; the next resume retries.
                        record(e)
                        null
                    }
                if (installed != null) safeSync(installed)
            }
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
            } catch (e: SQLiteException) {
                // SQLiteFullException (disk full), SQLiteCantOpenDatabaseException,
                // SQLiteDatabaseLockedException, corrupt file…: skip this sync, keep the cache.
                record(e)
            } catch (e: IllegalStateException) {
                // Room's own checks, e.g. a database that was closed or needs a migration.
                record(e)
            } catch (e: SecurityException) {
                // Role lookups can throw on unusual OEM builds; skip this sync, keep the cache.
                record(e)
            }
        }

        private fun record(error: Throwable) {
            lastSyncError = error
            Log.w(TAG, "App catalog sync failed; serving the cached catalog", error)
        }

        private companion object {
            const val TAG = "ReachCatalog"
            const val RETRY_BASE_DELAY_MS = 1_000L
            const val MAX_RETRY_DELAY_MS = 30_000L
            const val MAX_SHIFT = 5L
            const val SHARE_STOP_TIMEOUT_MS = 5_000L

            /** 1 s, 2 s, 4 s … capped at 30 s. */
            fun backoffMillis(attempt: Long): Long =
                minOf(
                    MAX_RETRY_DELAY_MS,
                    RETRY_BASE_DELAY_MS shl attempt.coerceAtMost(MAX_SHIFT).toInt(),
                )
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
