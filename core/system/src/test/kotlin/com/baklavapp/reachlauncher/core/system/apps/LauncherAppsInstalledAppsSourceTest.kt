package com.baklavapp.reachlauncher.core.system.apps

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * [LauncherAppsInstalledAppsSource] against Robolectric's `ShadowLauncherApps` (personal
 * profile), plus the pure [profileApps] rules for what the shadow can't model: Robolectric
 * has no private space (that needs `LauncherApps.getLauncherUserInfo` to report
 * `USER_TYPE_PROFILE_PRIVATE`) and no way to make `getActivityList` throw.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LauncherAppsInstalledAppsSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val me: UserHandle = Process.myUserHandle()

    private fun kotlinx.coroutines.test.TestScope.source() =
        LauncherAppsInstalledAppsSource(app, Profiles(app), UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `reads launcher activities and excludes Reach itself`() =
        runTest {
            shadowOf(launcherApps).addActivity(me, activity("com.example.notes", ApplicationInfo.CATEGORY_PRODUCTIVITY))
            shadowOf(launcherApps).addActivity(me, activity(app.packageName))

            val apps = source().installedApps.first()

            // (Labels come from PackageManager, which Robolectric resolves to the package name.)
            assertThat(apps.map { it.key.packageName }).containsExactly("com.example.notes")
            val notes = apps.single()
            assertThat(notes.key.userSerial).isEqualTo(0)
            assertThat(notes.profile).isEqualTo(ProfileType.PERSONAL)
            assertThat(notes.category).isEqualTo(AppCategory.PRODUCTIVITY)
        }

    @Test
    fun `a package callback re-reads, and cancelling unregisters the callback`() =
        runTest {
            shadowOf(launcherApps).addActivity(me, activity("com.example.notes"))
            val source = source()

            source.installedApps.test {
                assertThat(awaitItem().map { it.key.packageName }).containsExactly("com.example.notes")
                assertThat(registeredCallbacks()).isEqualTo(1)

                shadowOf(launcherApps).addActivity(me, activity("com.example.maps"))
                shadowOf(launcherApps).notifyPackageAdded("com.example.maps")
                shadowOf(Looper.getMainLooper()).idle() // Callbacks arrive on the main looper.
                assertThat(awaitItem().map { it.key.packageName }).containsExactly("com.example.notes", "com.example.maps")
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(registeredCallbacks()).isEqualTo(0)
        }

    @Test
    fun `collecting twice registers twice and both unregister`() =
        runTest {
            val source = source()
            val a = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.installedApps.collect {} }
            val b = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.installedApps.collect {} }
            assertThat(registeredCallbacks()).isEqualTo(2)
            a.cancel()
            b.cancel()
            assertThat(registeredCallbacks()).isEqualTo(0)
        }

    @Test
    fun `a locked private space contributes nothing and isn't even read`() {
        var reads = 0
        val apps =
            profileApps(ProfileType.PRIVATE, isQuietMode = { true }, serial = 11, ownPackage = OWN) {
                reads++
                listOf(entry("com.secret"))
            }
        assertThat(apps).isEmpty()
        assertThat(reads).isEqualTo(0)
    }

    @Test
    fun `an unlocked private space and a paused work profile contribute their apps`() {
        val private =
            profileApps(ProfileType.PRIVATE, isQuietMode = { false }, serial = 11, ownPackage = OWN) { listOf(entry("com.secret")) }
        assertThat(private.single().profile).isEqualTo(ProfileType.PRIVATE)
        // Paused work apps are kept (shown dimmed); quiet mode isn't even asked for work.
        val work =
            profileApps(
                ProfileType.WORK,
                isQuietMode = { error("not needed") },
                serial = 10,
                ownPackage = OWN,
            ) { listOf(entry("com.mail")) }
        assertThat(work.single().key.userSerial).isEqualTo(10)
    }

    @Test
    fun `a SecurityException while reading yields nothing instead of crashing`() {
        val apps =
            profileApps(
                ProfileType.WORK,
                isQuietMode = { false },
                serial = 10,
                ownPackage = OWN,
            ) { throw SecurityException("profile locked") }
        assertThat(apps).isEmpty()
    }

    @Test
    fun `a removed profile and Reach's own entry are skipped`() {
        assertThat(profileApps(ProfileType.WORK, { false }, serial = -1, ownPackage = OWN) { listOf(entry("com.mail")) }).isEmpty()
        val apps = profileApps(ProfileType.PERSONAL, { false }, serial = 0, ownPackage = OWN) { listOf(entry(OWN), entry("com.mail")) }
        assertThat(apps.map { it.key.packageName }).containsExactly("com.mail")
    }

    private fun entry(pkg: String) = LauncherEntry(pkg, "$pkg.Main", pkg.substringAfterLast('.'), ApplicationInfo.CATEGORY_UNDEFINED)

    /** Callbacks the shadow currently holds (`LauncherApps.registerCallback` minus unregister). */
    private fun registeredCallbacks(): Int = ReflectionHelpers.getStaticField<List<*>>(ShadowLauncherApps::class.java, "callbacks").size

    /**
     * Builds a `LauncherActivityInfo`. Its constructors are hidden; on API 35 it wraps a
     * `LauncherActivityInfoInternal`, which reflection can create inside Robolectric.
     */
    private fun activity(
        pkg: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ): LauncherActivityInfo {
        val appInfo =
            ApplicationInfo().apply {
                packageName = pkg
                this.category = category
            }
        val activityInfo =
            ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.Main"
                applicationInfo = appInfo
            }
        val incrementalClass = Class.forName("android.content.pm.IncrementalStatesInfo")
        val incremental =
            ReflectionHelpers.callConstructor(
                incrementalClass,
                ClassParameter(Boolean::class.javaPrimitiveType, false),
                ClassParameter(Float::class.javaPrimitiveType, 1f),
                ClassParameter(Long::class.javaPrimitiveType, 0L),
            )
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internal =
            ReflectionHelpers.callConstructor(
                internalClass,
                ClassParameter(ActivityInfo::class.java, activityInfo),
                ClassParameter(incrementalClass, incremental),
                ClassParameter(UserHandle::class.java, me),
            )
        return ReflectionHelpers.callConstructor(
            LauncherActivityInfo::class.java,
            ClassParameter(Context::class.java, app),
            ClassParameter(internalClass, internal),
        )
    }

    private companion object {
        const val OWN = "com.baklavapp.reachlauncher"
    }
}
