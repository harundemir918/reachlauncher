package com.baklavapp.reachlauncher.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.baklavapp.reachlauncher.core.data.db.AppAssignmentDao
import com.baklavapp.reachlauncher.core.data.db.AppCacheDao
import com.baklavapp.reachlauncher.core.data.db.LaunchEventDao
import com.baklavapp.reachlauncher.core.data.db.ReachDatabase
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.FavoritesRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.repository.impl.AppCatalogRepositoryImpl
import com.baklavapp.reachlauncher.core.data.repository.impl.FavoritesRepositoryImpl
import com.baklavapp.reachlauncher.core.data.repository.impl.LaunchHistoryRepositoryImpl
import com.baklavapp.reachlauncher.core.data.repository.impl.PredictionRepositoryImpl
import com.baklavapp.reachlauncher.core.data.repository.impl.SettingsRepositoryImpl
import com.baklavapp.reachlauncher.core.data.time.SystemZoneClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

/*
 * Hilt modules of `:core:data`. `SingletonComponent` = one instance per process.
 *
 * Flutter analogy: an injectable `@module abstract class RegisterModule` — `@Binds` is
 * `@LazySingleton(as: AppCatalogRepository)` on the implementation, `@Provides` is a
 * module getter that builds a third-party object (the database, DataStore).
 */

/** Binds every repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /** The Room-backed catalog. */
    @Binds
    abstract fun bindAppCatalogRepository(impl: AppCatalogRepositoryImpl): AppCatalogRepository

    /** The Room-backed launch history. */
    @Binds
    abstract fun bindLaunchHistoryRepository(impl: LaunchHistoryRepositoryImpl): LaunchHistoryRepository

    /** Live predictions. */
    @Binds
    abstract fun bindPredictionRepository(impl: PredictionRepositoryImpl): PredictionRepository

    /** DataStore-backed settings. */
    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    /** DataStore-backed favourites (same file as the settings). */
    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository
}

/** Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * The one database instance (opening Room is expensive; several instances on one file
     * would also break its change notifications). The file is created lazily on first query,
     * on a background thread, so this doesn't slow down app start.
     *
     * `fallbackToDestructiveMigrationOnDowngrade`: installing an *older* debug build over a
     * newer one would otherwise crash on open ("downgrade not supported"); the cache and
     * history are wiped instead. Upgrades are not covered — every schema bump needs a real
     * `Migration` (docs/DECISIONS.md D37).
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ReachDatabase =
        Room
            .databaseBuilder(context, ReachDatabase::class.java, ReachDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    /** App cache DAO. */
    @Provides
    fun provideAppCacheDao(db: ReachDatabase): AppCacheDao = db.appCacheDao()

    /** Assignment DAO. */
    @Provides
    fun provideAppAssignmentDao(db: ReachDatabase): AppAssignmentDao = db.appAssignmentDao()

    /** Launch history DAO. */
    @Provides
    fun provideLaunchEventDao(db: ReachDatabase): LaunchEventDao = db.launchEventDao()
}

/** Preferences DataStore. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /**
     * Exactly one DataStore per file per process — DataStore throws if two instances are
     * active for the same file, hence `@Singleton`. A corrupt file is replaced by empty
     * preferences (= defaults) instead of crashing Home on every start.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
        )

    private const val SETTINGS_FILE = "settings"
}

/** Coroutine dispatchers, the application scope and the clock. */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    /** See [IoDispatcher]. */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** See [DefaultDispatcher]. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * See [ApplicationScope]. `SupervisorJob`: one failing child (e.g. a sync) must not
     * cancel its siblings (e.g. the DataStore).
     *
     * [loggingExceptionHandler] is the backstop: without a handler, an exception escaping a
     * coroutine in this scope goes to the thread's uncaught-exception handler and kills the
     * process — for the home screen, a crash loop (e.g. on a full disk). Code is still
     * expected to handle its own failures; this only turns a missed case into a log line.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher + loggingExceptionHandler)

    /** Logs instead of crashing; see [provideApplicationScope]. */
    internal val loggingExceptionHandler =
        CoroutineExceptionHandler { _, error ->
            Log.e("ReachAppScope", "Uncaught exception in the application scope", error)
        }

    /** Wall clock in the current zone; tests replace it. */
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemZoneClock()
}
