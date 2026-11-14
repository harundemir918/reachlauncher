package com.baklavapp.reachlauncher.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * DAOs: the queries Room generates code for.
 *
 * A DAO function returning `Flow` is an *observed* query: Room re-runs it and emits a new
 * list whenever any table it reads changes (Room's "invalidation tracker"). `suspend`
 * functions are one-shot and run off the main thread.
 *
 * Flutter analogy: drift's `select(table).watch()` (→ Flow) vs `.get()` (→ suspend), or a
 * sqflite helper class whose methods return `Future`s and `Stream`s.
 *
 * Why abstract classes instead of interfaces: `@Transaction` methods with a body are the
 * simplest to declare on an abstract class.
 *
 * No SQLite `INSERT … ON CONFLICT DO UPDATE` in raw queries: that syntax needs SQLite 3.24
 * (Android 11), and minSdk is 28. Room's `@Upsert` works on every version.
 */

/** Access to the instant-start app cache. */
@Dao
abstract class AppCacheDao {
    /** Observed: the whole cache. Small (one row per launcher activity), so no paging. */
    @Query("SELECT * FROM cached_apps")
    abstract fun observeAll(): Flow<List<CachedAppEntity>>

    /** One-shot read used to diff against fresh system data. */
    @Query("SELECT * FROM cached_apps")
    abstract suspend fun getAll(): List<CachedAppEntity>

    /** Inserts or replaces rows by primary key. */
    @Upsert
    abstract suspend fun upsert(rows: List<CachedAppEntity>)

    /** Deletes rows by primary key. */
    @Delete
    abstract suspend fun delete(rows: List<CachedAppEntity>)

    /**
     * Applies a sync diff atomically, so observers never see a half-updated catalog
     * (e.g. an updated app briefly missing).
     */
    @Transaction
    open suspend fun applyDiff(
        upserts: List<CachedAppEntity>,
        deletes: List<CachedAppEntity>,
    ) {
        if (upserts.isNotEmpty()) upsert(upserts)
        if (deletes.isNotEmpty()) delete(deletes)
    }
}

/** Access to the user's "Move to…" and "Hide" choices. */
@Dao
abstract class AppAssignmentDao {
    /** Observed: every assignment. */
    @Query("SELECT * FROM app_assignments")
    abstract fun observeAll(): Flow<List<AppAssignmentEntity>>

    /** One-shot read of one app's assignment, or null if the user never touched it. */
    @Query(
        "SELECT * FROM app_assignments " +
            "WHERE package_name = :packageName AND activity_name = :activityName AND user_serial = :userSerial",
    )
    abstract suspend fun get(
        packageName: String,
        activityName: String,
        userSerial: Long,
    ): AppAssignmentEntity?

    /** Inserts or replaces a row by primary key. */
    @Upsert
    abstract suspend fun upsert(row: AppAssignmentEntity)

    /** Sets the user's intent choice, keeping the hidden flag (read-modify-write in one transaction). */
    @Transaction
    open suspend fun setIntent(
        packageName: String,
        activityName: String,
        userSerial: Long,
        intentKey: String?,
    ) {
        val current = get(packageName, activityName, userSerial)
        upsert(
            current?.copy(intentKey = intentKey)
                ?: AppAssignmentEntity(packageName, activityName, userSerial, intentKey = intentKey, hidden = false),
        )
    }

    /** Sets the hidden flag, keeping the intent choice. */
    @Transaction
    open suspend fun setHidden(
        packageName: String,
        activityName: String,
        userSerial: Long,
        hidden: Boolean,
    ) {
        val current = get(packageName, activityName, userSerial)
        upsert(
            current?.copy(hidden = hidden)
                ?: AppAssignmentEntity(packageName, activityName, userSerial, intentKey = null, hidden = hidden),
        )
    }
}

/** Access to Reach's own launch history. */
@Dao
abstract class LaunchEventDao {
    /** Appends one launch. */
    @Insert
    abstract suspend fun insert(event: LaunchEventEntity)

    /** Observed: launches per app, all time (for "sorted by launch frequency"). */
    @Query(
        "SELECT package_name, activity_name, user_serial, COUNT(*) AS launch_count FROM launch_events " +
            "GROUP BY package_name, activity_name, user_serial",
    )
    abstract fun observeCounts(): Flow<List<LaunchCountRow>>

    /** Observed: launches at or after [sinceEpochMillis], oldest first (uses the epoch index). */
    @Query("SELECT * FROM launch_events WHERE epoch_millis >= :sinceEpochMillis ORDER BY epoch_millis ASC, id ASC")
    abstract fun observeSince(sinceEpochMillis: Long): Flow<List<LaunchEventEntity>>
}
