package com.baklavapp.reachlauncher.core.system.profile

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.model.ProfileType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared knowledge about user profiles (personal, work, private space), used by the app
 * source, the profile source and the launcher.
 *
 * Android background for Flutter developers: one phone can run several *users*; a work
 * profile and the Android 15+ private space are "profile users" attached to the main
 * one. Each has a `UserHandle` (an in-memory token, not stable across reboots) and a
 * *serial number* (stable, safe to store). Reach persists serials and converts back to
 * handles when it calls the system.
 */
@Singleton
class Profiles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())
        private val userManager: UserManager = checkNotNull(context.getSystemService())

        /**
         * Profiles the launcher may show (`LauncherApps.getProfiles`, API 26+).
         *
         * On Android 15+ the private space is only included when Reach holds the normal
         * `ACCESS_HIDDEN_PROFILES` permission (declared in `:core:system`'s manifest) *and* the
         * home role (javadoc of `getProfiles`, verified against the API 36 sources, D67).
         * Without the role the private space is simply invisible to Reach — the safe default.
         */
        fun visibleProfiles(): List<UserHandle> =
            try {
                launcherApps.profiles
            } catch (e: SecurityException) {
                listOf(Process.myUserHandle())
            }

        /** Stable serial for [user]; -1 if the user no longer exists. */
        fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

        /** The handle for a persisted [serial], or null if that profile was removed. */
        fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

        /**
         * Whether [user] is in quiet mode: a paused work profile or a locked private space.
         * Fails closed: if the system refuses to answer (a hidden profile we lost access to),
         * the profile counts as quiet, so private-space apps are dropped and work apps dimmed.
         */
        fun isQuietMode(user: UserHandle): Boolean =
            try {
                userManager.isQuietModeEnabled(user)
            } catch (e: SecurityException) {
                true
            } catch (e: IllegalArgumentException) {
                true
            }

        /** Personal, work or private space; see [classifyProfile]. */
        fun typeOf(user: UserHandle): ProfileType =
            classifyProfile(
                isOwnUser = user == Process.myUserHandle(),
                privateLookup =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        privateLookup(user)
                    } else {
                        PrivateLookup.NOT_PRIVATE // No private space before Android 15.
                    },
            )

        /**
         * `LauncherApps.getLauncherUserInfo` (API 35) is the documented way for a launcher to
         * learn a profile's type. For a private profile it requires `ACCESS_HIDDEN_PROFILES`
         * (+ home role) and throws `SecurityException` without it — reported as
         * [PrivateLookup.DENIED], which [classifyProfile] treats as private (fail closed). A
         * null answer ("user is inaccessible") is *not* treated as private: see [privateLookupOf].
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun privateLookup(user: UserHandle): PrivateLookup =
            try {
                privateLookupOf(launcherApps.getLauncherUserInfo(user)?.userType)
            } catch (e: SecurityException) {
                PrivateLookup.DENIED
            }

        /**
         * Asks the system to pause/resume a work profile or lock/unlock the private space.
         * Allowed for the foreground default launcher (or holders of `MODIFY_QUIET_MODE`).
         * Turning quiet mode *off* may show the system's credential screen first; the call
         * then returns false and the profile is unlocked once the user authenticates.
         */
        fun requestQuietMode(
            user: UserHandle,
            enabled: Boolean,
        ): Boolean =
            try {
                userManager.requestQuietModeEnabled(enabled, user)
            } catch (e: SecurityException) {
                false
            } catch (e: IllegalArgumentException) {
                false // e.g. the user isn't a profile of ours (removed meanwhile).
            }

        companion object {
            /**
             * Broadcasts after which profile state must be re-read. The managed-profile ones
             * exist on every supported version; Android 12 (31) adds accessible/inaccessible,
             * 14 (34) generic added/removed, and 15 (35) generic available/unavailable,
             * which also cover the private space.
             */
            fun profileChangeActions(): List<String> =
                buildList {
                    add(Intent.ACTION_MANAGED_PROFILE_ADDED)
                    add(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                    add(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                    add(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                    add(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Intent.ACTION_PROFILE_ACCESSIBLE)
                        add(Intent.ACTION_PROFILE_INACCESSIBLE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        add(Intent.ACTION_PROFILE_ADDED)
                        add(Intent.ACTION_PROFILE_REMOVED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        add(Intent.ACTION_PROFILE_AVAILABLE)
                        add(Intent.ACTION_PROFILE_UNAVAILABLE)
                    }
                }
        }
    }

/** Result of asking the system whether a profile is the private space. */
enum class PrivateLookup {
    PRIVATE,
    NOT_PRIVATE,

    /** The system refused to tell us (`SecurityException`, only thrown for hidden profiles). */
    DENIED,
}

/**
 * Maps `LauncherUserInfo.getUserType()` to a [PrivateLookup].
 *
 * A null [userType] (no `LauncherUserInfo`) counts as "not private" on purpose (D67): the
 * profile came from `LauncherApps.getProfiles()`, which only lists a private space to a caller
 * that may also read its info, so a null there is a transient work-profile state (e.g. being
 * removed). Treating it as private would hide a paused work profile's apps and turn "tap to
 * resume" into a failure. The private space itself stays fail-closed through the
 * `SecurityException` path ([PrivateLookup.DENIED]) and the quiet-mode filter.
 */
fun privateLookupOf(userType: String?): PrivateLookup =
    if (userType == USER_TYPE_PROFILE_PRIVATE) PrivateLookup.PRIVATE else PrivateLookup.NOT_PRIVATE

/**
 * `UserManager.USER_TYPE_PROFILE_PRIVATE` (API 35) as a plain constant, so [privateLookupOf]
 * stays callable (and testable) below API 35.
 */
internal const val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

/**
 * Pure mapping from what the system told us to a [ProfileType].
 *
 * A [PrivateLookup.DENIED] lookup is treated as PRIVATE: the system only refuses for
 * hidden profiles, and calling a private space "work" would show its apps when locked.
 */
fun classifyProfile(
    isOwnUser: Boolean,
    privateLookup: PrivateLookup,
): ProfileType =
    when {
        isOwnUser -> ProfileType.PERSONAL
        privateLookup != PrivateLookup.NOT_PRIVATE -> ProfileType.PRIVATE
        else -> ProfileType.WORK
    }
