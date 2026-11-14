package com.baklavapp.reachlauncher.core.system.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.InstalledAppsSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.ProfileType
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launchable activities of every visible profile, via `LauncherApps`.
 *
 * Why `LauncherApps` and not `PackageManager.queryIntentActivities`: it is the API made
 * for launchers. It covers work profiles (which `PackageManager` can't see across), comes
 * with change callbacks, and only needs the `<queries>` entry for `MAIN/LAUNCHER` that
 * `:app` declares — no `QUERY_ALL_PACKAGES`.
 *
 * How updates work: a `LauncherApps.Callback` (installs, removals, updates, apps on SD
 * cards becoming (un)available, suspension by parental controls/digital wellbeing) and
 * the profile broadcasts (work profile paused/resumed, private space locked/unlocked)
 * each just *poke* a conflated channel; the collector then re-reads the full list on the
 * IO dispatcher. A burst of callbacks (e.g. an app update fires remove+add) collapses
 * into one or two re-reads, and the result can't drift from the system's truth.
 *
 * Filtering promised by the contract: Reach's own entry is excluded, and so are apps of a
 * private space in quiet mode (locked). Paused work apps are included (shown dimmed).
 *
 * Future (M5): with `ACCESS_HIDDEN_PROFILES`, `LauncherApps.getProfiles()` starts
 * returning the private space; nothing here needs to change for that.
 *
 * Flutter analogy: a `StreamController` wrapping a platform callback (callbackFlow), like
 * a plugin's `EventChannel.receiveBroadcastStream()` — the subscription registers the
 * native listener and cancelling it unregisters.
 */
@Singleton
class LauncherAppsInstalledAppsSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val profiles: Profiles,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : InstalledAppsSource {
        private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())

        override val installedApps: Flow<List<InstalledApp>> =
            callbackFlow {
                val pokes = Channel<Unit>(Channel.CONFLATED)
                val callback = PokingCallback { pokes.trySend(Unit) }
                // Callbacks are delivered on the main looper; they only poke, so that's cheap.
                launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
                val profileJob = launch { context.broadcasts(Profiles.profileChangeActions()).collect { pokes.trySend(Unit) } }
                pokes.trySend(Unit) // Initial read.
                launch(ioDispatcher) { pokes.receiveAsFlow().collect { send(readAll()) } }
                awaitClose {
                    profileJob.cancel()
                    launcherApps.unregisterCallback(callback)
                }
            }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        private fun readAll(): List<InstalledApp> = profiles.visibleProfiles().flatMap(::readProfile)

        private fun readProfile(user: UserHandle): List<InstalledApp> =
            profileApps(
                type = profiles.typeOf(user),
                isQuietMode = { profiles.isQuietMode(user) },
                serial = profiles.serialOf(user),
                ownPackage = context.packageName,
            ) {
                launcherApps.getActivityList(null, user).map { info ->
                    LauncherEntry(
                        packageName = info.componentName.packageName,
                        className = info.componentName.className,
                        label = info.label.toString(),
                        category = info.applicationInfo.category,
                    )
                }
            }

        /** Every package/profile change just triggers a full re-read. */
        private class PokingCallback(
            private val poke: () -> Unit,
        ) : LauncherApps.Callback() {
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

            override fun onPackagesSuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) = poke()

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) = poke()
        }
    }

/** What Reach needs from one `LauncherActivityInfo`, as plain values (see [profileApps]). */
internal data class LauncherEntry(
    val packageName: String,
    val className: String,
    val label: String,
    /** `ApplicationInfo.category`. */
    val category: Int,
)

/**
 * The apps one profile contributes — the filtering rules of [LauncherAppsInstalledAppsSource],
 * pulled out as a pure function so they are testable without a system that has profiles:
 * - **Privacy:** a private space in quiet mode (locked) contributes nothing, not even
 *   labels, and [readActivities] isn't even called.
 * - A negative [serial] means the profile was removed while we were reading.
 * - A `SecurityException` from [readActivities] (the profile became inaccessible
 *   meanwhile) yields an empty list instead of failing the whole read.
 * - Reach's own launcher entry ([ownPackage]) is excluded.
 *
 * [isQuietMode] is a function so the system is only asked for private profiles.
 */
internal fun profileApps(
    type: ProfileType,
    isQuietMode: () -> Boolean,
    serial: Long,
    ownPackage: String,
    readActivities: () -> List<LauncherEntry>,
): List<InstalledApp> {
    if (type == ProfileType.PRIVATE && isQuietMode()) return emptyList()
    if (serial < 0) return emptyList()
    val entries =
        try {
            readActivities()
        } catch (e: SecurityException) {
            return emptyList()
        }
    return entries
        .filter { it.packageName != ownPackage }
        .map { entry ->
            InstalledApp(
                key = AppKey(entry.packageName, entry.className, serial),
                label = entry.label,
                category = appCategoryOf(entry.category),
                profile = type,
            )
        }
}
