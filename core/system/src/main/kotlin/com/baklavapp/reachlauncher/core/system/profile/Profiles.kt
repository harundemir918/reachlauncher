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
         * On Android 15+ the private space is only included when Reach holds
         * `ACCESS_HIDDEN_PROFILES` *and* the home role. That permission is added in M5; until
         * then the private space is simply invisible to Reach, which is the safe default.
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

        /** Whether [user] is in quiet mode: a paused work profile or a locked private space. */
        fun isQuietMode(user: UserHandle): Boolean = userManager.isQuietModeEnabled(user)

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
         * (+ home role) and throws `SecurityException` without it.
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun privateLookup(user: UserHandle): PrivateLookup =
            try {
                val type = launcherApps.getLauncherUserInfo(user)?.userType
                if (type == UserManager.USER_TYPE_PROFILE_PRIVATE) PrivateLookup.PRIVATE else PrivateLookup.NOT_PRIVATE
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

    /** The system refused to tell us — only happens for hidden (private) profiles. */
    DENIED,
}

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
