package com.baklavapp.reachlauncher.core.system.shortcuts

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.ShortcutSource
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import com.baklavapp.reachlauncher.core.system.launch.SystemAppLauncher
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import com.baklavapp.reachlauncher.core.system.util.broadcasts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App shortcuts ("New message", "Navigate home"…) of every visible profile, via
 * `LauncherApps.getShortcuts`.
 *
 * **The permission.** Only the *default home app* may read other apps' shortcuts:
 * `LauncherApps.hasShortcutHostPermission()` is true exactly while Reach holds the home
 * role. There is no runtime dialog for it; the user grants it by choosing Reach as the
 * home app. Without it every call throws `SecurityException`, so we check first and
 * report "no shortcuts" instead.
 *
 * **What is read.** `FLAG_MATCH_DYNAMIC | FLAG_MATCH_MANIFEST | FLAG_MATCH_PINNED`: the
 * shortcuts an app declares in its manifest, the ones it publishes at run time (e.g. recent
 * chats) and the ones the user pinned *to Reach*. Disabled shortcuts (e.g. a pinned chat
 * that was deleted) are left out because they can't be started.
 *
 * **When it re-reads** — the same "poke, then re-read everything" pattern as
 * `LauncherAppsInstalledAppsSource`: on collection start (a cheap re-check of the
 * permission, which catches "Reach just became default home"), on
 * `LauncherApps.Callback.onShortcutsChanged`, on package callbacks and on profile
 * broadcasts (the permission and the readable profiles may change with them).
 *
 * **Privacy.** A locked private space contributes nothing, like in the app source. A paused
 * work profile throws `IllegalStateException` ("user locked or not running"), which yields
 * an empty list for that profile.
 *
 * Flutter analogy: a plugin's `EventChannel` stream (callbackFlow) plus a `startShortcut`
 * method-channel call — but calling the Android APIs directly.
 */
@Singleton
class LauncherAppsShortcutSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val profiles: Profiles,
        private val tracker: ActivityTracker,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ShortcutSource {
        private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())

        /** One read: the permission and, when held, the shortcuts. */
        private data class Snapshot(
            val granted: Boolean,
            val shortcuts: List<AppShortcut>,
        )

        private val snapshots: Flow<Snapshot> =
            callbackFlow {
                val pokes = Channel<Unit>(Channel.CONFLATED)
                val callback = PokingCallback { pokes.trySend(Unit) }
                launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
                val profileJob = launch { context.broadcasts(Profiles.profileChangeActions()).collect { pokes.trySend(Unit) } }
                pokes.trySend(Unit) // Initial read, which also re-checks the permission.
                launch(ioDispatcher) { pokes.receiveAsFlow().collect { send(read()) } }
                awaitClose {
                    profileJob.cancel()
                    launcherApps.unregisterCallback(callback)
                }
            }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override val hasHostPermission: Flow<Boolean> = snapshots.map { it.granted }.distinctUntilChanged()

        override val shortcuts: Flow<List<AppShortcut>> = snapshots.map { it.shortcuts }.distinctUntilChanged()

        private fun hasPermission(): Boolean =
            try {
                launcherApps.hasShortcutHostPermission()
            } catch (e: SecurityException) {
                false
            } catch (e: IllegalStateException) {
                false
            }

        private fun read(): Snapshot {
            if (!hasPermission()) return Snapshot(granted = false, shortcuts = emptyList())
            val all =
                profiles.visibleProfiles().flatMap { user ->
                    profileShortcuts(
                        type = profiles.typeOf(user),
                        isQuietMode = { profiles.isQuietMode(user) },
                        serial = profiles.serialOf(user),
                        appLabel = { pkg -> appLabel(pkg, user) },
                    ) { queryShortcuts(user) }
                }
            return Snapshot(granted = true, shortcuts = all)
        }

        private fun queryShortcuts(user: UserHandle): List<ShortcutEntry> {
            val query =
                LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            return launcherApps.getShortcuts(query, user).orEmpty().map(::entryOf)
        }

        /** Source app label for "MESSAGE AYŞE · WHATSAPP"; the package name if it can't be read. */
        private fun appLabel(
            packageName: String,
            user: UserHandle,
        ): String =
            try {
                launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(context.packageManager).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            } catch (e: SecurityException) {
                packageName
            }

        /**
         * Starts [shortcut] with the clip-reveal animation from [origin], like
         * `SystemAppLauncher.launchApp` (same `ActivityTracker`, same coordinate rules).
         * Every documented failure — the shortcut is gone or disabled
         * (`ActivityNotFoundException`), its profile is locked or not running
         * (`IllegalStateException`), the permission is missing (`SecurityException`) — returns
         * false instead of crashing Home.
         */
        override fun launch(
            shortcut: AppShortcut,
            origin: LaunchOrigin?,
        ): Boolean {
            if (!hasPermission()) return false
            val user = profiles.userFor(shortcut.app.userSerial) ?: return false
            val (sourceBounds, options) = animationFor(origin)
            return try {
                launcherApps.startShortcut(shortcut.app.packageName, shortcut.id, sourceBounds, options, user)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: IllegalStateException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        /**
         * Same recipe as `SystemAppLauncher`'s (private) helper: the clip-reveal rectangle is
         * relative to the decor view (= window pixels, what [LaunchOrigin] holds) and
         * `sourceBounds` is converted to screen pixels with the shared, tested
         * [SystemAppLauncher.sourceBoundsOnScreen].
         */
        private fun animationFor(origin: LaunchOrigin?): Pair<Rect?, Bundle?> {
            val activity = tracker.resumedActivity
            if (activity == null || origin == null) return null to null
            val decorView = activity.window?.decorView ?: return null to null
            val options =
                ActivityOptions
                    .makeClipRevealAnimation(decorView, origin.left, origin.top, origin.width, origin.height)
                    .toBundle()
            val onScreen = IntArray(2)
            decorView.getLocationOnScreen(onScreen)
            val bounds = SystemAppLauncher.sourceBoundsOnScreen(origin, windowLeftOnScreen = onScreen[0], windowTopOnScreen = onScreen[1])
            return bounds to options
        }

        /** Shortcut changes, and every package change that may affect them, trigger a full re-read. */
        private class PokingCallback(
            private val poke: () -> Unit,
        ) : LauncherApps.Callback() {
            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: List<ShortcutInfo>,
                user: UserHandle,
            ) = poke()

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = poke()

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = poke()

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = poke()

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = poke()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = poke()
        }

        private companion object {
            fun entryOf(info: ShortcutInfo): ShortcutEntry =
                ShortcutEntry(
                    id = info.id,
                    packageName = info.`package`,
                    activityName = info.activity?.className,
                    shortLabel = info.shortLabel?.toString(),
                    longLabel = info.longLabel?.toString(),
                    isEnabled = info.isEnabled,
                )
        }
    }

/** What Reach needs from one `ShortcutInfo`, as plain values (see [profileShortcuts]). */
internal data class ShortcutEntry(
    val id: String,
    val packageName: String,
    val activityName: String?,
    val shortLabel: String?,
    val longLabel: String?,
    val isEnabled: Boolean,
)

/**
 * The shortcuts one profile contributes — the rules of [LauncherAppsShortcutSource] as a
 * pure function, so they are testable without a system that has profiles or shortcuts:
 * - **Privacy:** a locked private space contributes nothing and [readShortcuts] isn't called.
 * - A negative [serial] means the profile was removed while we were reading.
 * - `IllegalStateException` (profile locked / not running, e.g. a paused work profile) and
 *   `SecurityException` (permission lost meanwhile) yield an empty list.
 * - Disabled shortcuts are dropped (they can't be started); so are shortcuts without any
 *   label. The short label is preferred (it's what launchers show), the long one is the
 *   fallback.
 * - [appLabel] is asked once per package.
 */
internal fun profileShortcuts(
    type: ProfileType,
    isQuietMode: () -> Boolean,
    serial: Long,
    appLabel: (packageName: String) -> String,
    readShortcuts: () -> List<ShortcutEntry>,
): List<AppShortcut> {
    if (type == ProfileType.PRIVATE && isQuietMode()) return emptyList()
    if (serial < 0) return emptyList()
    val entries =
        try {
            readShortcuts()
        } catch (e: IllegalStateException) {
            return emptyList()
        } catch (e: SecurityException) {
            return emptyList()
        }
    val labels = mutableMapOf<String, String>()
    return entries.mapNotNull { entry ->
        val label = entry.shortLabel?.takeIf { it.isNotBlank() } ?: entry.longLabel?.takeIf { it.isNotBlank() }
        if (!entry.isEnabled || label == null) return@mapNotNull null
        AppShortcut(
            id = entry.id,
            app = PackageUser(entry.packageName, serial),
            activityName = entry.activityName,
            label = label,
            appLabel = labels.getOrPut(entry.packageName) { appLabel(entry.packageName) },
        )
    }
}
