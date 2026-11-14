package com.baklavapp.reachlauncher.core.data.db

import com.baklavapp.reachlauncher.core.data.domain.CachedApp
import com.baklavapp.reachlauncher.core.data.domain.IntentCategorizer
import com.baklavapp.reachlauncher.core.data.domain.UserAssignment
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.baklavapp.reachlauncher.core.model.ReachIntent

/*
 * Entity <-> domain mapping. Nothing above `:core:data` ever sees a Room entity.
 */

internal fun CachedAppEntity.key(): AppKey = AppKey(packageName, activityName, userSerial)

internal fun AppAssignmentEntity.key(): AppKey = AppKey(packageName, activityName, userSerial)

internal fun LaunchCountRow.key(): AppKey = AppKey(packageName, activityName, userSerial)

/** Builds a cache row for [app] auto-assigned to [autoIntent]. */
internal fun cachedAppEntity(
    app: InstalledApp,
    autoIntent: ReachIntent,
): CachedAppEntity =
    CachedAppEntity(
        packageName = app.key.packageName,
        activityName = app.key.activityName,
        userSerial = app.key.userSerial,
        label = app.label,
        categoryKey = app.category.storageKey,
        profileKey = app.profile.storageKey,
        autoIntentKey = autoIntent.storageKey,
    )

/**
 * Reads a cache row. An auto-intent key this build doesn't know is recomputed from the
 * category (without role overrides, which the next sync restores).
 */
internal fun CachedAppEntity.toCachedApp(): CachedApp {
    val category = appCategoryFromStorageKey(categoryKey)
    val app = InstalledApp(key = key(), label = label, category = category, profile = profileTypeFromStorageKey(profileKey))
    val auto = ReachIntent.fromStorageKey(autoIntentKey) ?: IntentCategorizer.categorize(category, packageName, emptySet())
    return CachedApp(app, auto)
}

/** Reads an assignment row; an unknown intent key means "no user choice" (D15). */
internal fun AppAssignmentEntity.toUserAssignment(): UserAssignment =
    UserAssignment(intent = intentKey?.let(ReachIntent::fromStorageKey), hidden = hidden)

internal fun LaunchEventEntity.toLaunchEvent(): LaunchEvent =
    LaunchEvent(
        key = AppKey(packageName, activityName, userSerial),
        epochMillis = epochMillis,
        hourOfDay = hourOfDay,
        dayOfWeek = dayOfWeek,
    )
