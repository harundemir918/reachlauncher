package com.baklavapp.reachlauncher.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities (tables). Column names are snake_case and fixed with @ColumnInfo, so
 * renaming a Kotlin property never silently changes the schema.
 *
 * Enums are stored by stable string keys (`ReachIntent.storageKey`, and the keys in
 * StorageKeys.kt), never by `name` or ordinal: renaming or reordering an enum constant
 * must not corrupt saved data.
 *
 * Flutter analogy: the table classes you'd declare for drift (`class CachedApps extends
 * Table`), or the CREATE TABLE strings of a sqflite `onCreate`. Room generates the SQL
 * and the row mapping at compile time (via KSP, like drift's build_runner).
 */

/**
 * The instant-start cache: one row per launchable activity per profile, as last seen
 * from `LauncherApps`. Home renders from this table before any system query runs.
 *
 * @property autoIntentKey the auto-assigned intent at the time of caching (role holders
 *   included), so the cache alone is enough to place every app.
 */
@Entity(
    tableName = "cached_apps",
    primaryKeys = ["package_name", "activity_name", "user_serial"],
)
data class CachedAppEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "category") val categoryKey: String,
    @ColumnInfo(name = "profile") val profileKey: String,
    @ColumnInfo(name = "auto_intent") val autoIntentKey: String,
)

/**
 * The user's own choices for an app. Kept in its own table (not columns on the cache)
 * because the cache is rewritten from the system, while these rows must survive app
 * updates, cache resets and even uninstall/reinstall.
 *
 * @property intentKey `ReachIntent.storageKey` chosen with "Move to…", or null for "no
 *   choice". Unknown keys are read as null too (docs/DECISIONS.md D15).
 */
@Entity(
    tableName = "app_assignments",
    primaryKeys = ["package_name", "activity_name", "user_serial"],
)
data class AppAssignmentEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "intent") val intentKey: String?,
    @ColumnInfo(name = "hidden") val hidden: Boolean,
)

/**
 * One launch performed by Reach. Hour and weekday are stored as captured in the local
 * time zone at launch time (see `LaunchEvent`), not recomputed later.
 */
@Entity(
    tableName = "launch_events",
    indices = [Index(value = ["epoch_millis"])],
)
data class LaunchEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "epoch_millis") val epochMillis: Long,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
)

/** Result row of the launch-count aggregate query (not a table). */
data class LaunchCountRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "launch_count") val count: Int,
)
