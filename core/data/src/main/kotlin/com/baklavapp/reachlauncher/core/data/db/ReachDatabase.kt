package com.baklavapp.reachlauncher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Reach's single SQLite database: app cache, user assignments and launch history.
 *
 * `exportSchema = true` writes `core/data/schemas/<class>/<version>.json` at build time.
 * The file is committed: when version 2 arrives, its migration is tested against the
 * exact v1 schema users have on their phones.
 *
 * Flutter analogy: drift's `@DriftDatabase(tables: [...])` class with its `schemaVersion`,
 * including drift's "schema dumps" for migration tests.
 */
@Database(
    entities = [CachedAppEntity::class, AppAssignmentEntity::class, LaunchEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReachDatabase : RoomDatabase() {
    /** The instant-start app cache. */
    abstract fun appCacheDao(): AppCacheDao

    /** User "Move to…" / "Hide" choices. */
    abstract fun appAssignmentDao(): AppAssignmentDao

    /** Reach's launch history. */
    abstract fun launchEventDao(): LaunchEventDao

    companion object {
        /** File name in the app's private database directory. */
        const val NAME = "reach.db"
    }
}
