package com.baklavapp.reachlauncher.core.model

/*
 * App identity and catalog types.
 *
 * Flutter analogy: plain immutable model classes, like Freezed/`equatable` data classes.
 * Kotlin `data class` gives you `==`, `hashCode`, `toString` and `copy(...)` for free.
 */

/**
 * Identifies one launchable activity of one app in one user profile.
 *
 * Why three parts: an app can expose several launcher activities, and the same package
 * can be installed in the personal profile, a work profile and the private space. Each
 * combination is a separate row on screen and a separate history entry.
 *
 * @property packageName e.g. `com.whatsapp`.
 * @property activityName fully qualified class name of the launcher activity.
 * @property userSerial `UserManager.getSerialNumberForUser(handle)` — unlike the
 *   `UserHandle` itself this number is stable across reboots, so it is safe to persist.
 */
data class AppKey(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
) {
    /** The package-in-profile this activity belongs to (what notifications are keyed by). */
    val packageUser: PackageUser get() = PackageUser(packageName, userSerial)
}

/**
 * A package in one user profile. Notifications belong to a package, not an
 * activity, but the personal and work copies of an app are still different apps.
 */
data class PackageUser(
    val packageName: String,
    val userSerial: Long,
)

/** Which user profile an app lives in. Shown as a small mono tag ("WORK") on rows. */
enum class ProfileType {
    PERSONAL,
    WORK,

    /** Android 15+ private space. Its apps are hidden while the space is locked. */
    PRIVATE,
}

/**
 * State of one user profile, so Home can show a paused work profile and offer to lock or
 * unlock the private space.
 *
 * @property isQuietModeEnabled "quiet mode" is Android's name for both a *paused* work
 *   profile and a *locked* private space. Paused work apps are shown dimmed (tapping
 *   offers to resume); apps of a locked private space are not shown at all.
 */
data class ProfileState(
    val userSerial: Long,
    val type: ProfileType,
    val isQuietModeEnabled: Boolean,
)

/**
 * Mirror of `ApplicationInfo.category`, kept free of Android so categorisation can be
 * unit-tested on the JVM. [UNDEFINED] covers `CATEGORY_UNDEFINED` and any future value.
 */
enum class AppCategory {
    UNDEFINED,
    GAME,
    AUDIO,
    VIDEO,
    IMAGE,
    SOCIAL,
    NEWS,
    MAPS,
    PRODUCTIVITY,
    ACCESSIBILITY,
}

/**
 * An app as the system reports it, before Reach adds its own knowledge (intent, hidden,
 * history). Produced by the system app source (`InstalledAppsSource` in `:core:data`)
 * and consumed by the catalog repository.
 */
data class InstalledApp(
    val key: AppKey,
    val label: String,
    val category: AppCategory,
    val profile: ProfileType,
)

/**
 * An app as Reach shows it: system data plus intent assignment, visibility and usage.
 *
 * @property intent the effective intent — the user's choice if they made one,
 *   otherwise the auto-assigned one.
 * @property isUserAssigned true when [intent] came from "Move to…"; such choices always
 *   win over auto-assignment, even after the app updates its category.
 * @property isHidden hidden apps are left out of Home, intent lists and search, but stay
 *   reachable from All Apps' long-press menu so they can be un-hidden.
 * @property isProfilePaused the app's work profile is paused (quiet mode). The app is
 *   shown dimmed; launching it asks the system to resume the profile first.
 * @property launchCount launches performed through Reach (used for sorting).
 */
data class CatalogApp(
    val key: AppKey,
    val label: String,
    val profile: ProfileType,
    val intent: ReachIntent,
    val isUserAssigned: Boolean,
    val isHidden: Boolean,
    val launchCount: Int,
    val isProfilePaused: Boolean = false,
)
