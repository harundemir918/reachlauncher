package com.baklavapp.reachlauncher.core.system.shortcuts

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import androidx.test.core.app.ApplicationProvider
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [LauncherAppsShortcutSource] against Robolectric's `ShadowLauncherApps` (personal profile),
 * plus the pure [profileShortcuts] rules for what the shadow can't model (private space,
 * locked profiles).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LauncherAppsShortcutSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val launcherApps = app.getSystemService(LauncherApps::class.java)

    private fun TestScope.source() =
        LauncherAppsShortcutSource(app, Profiles(app), ActivityTracker(AccessRechecks()), UnconfinedTestDispatcher(testScheduler))

    private fun shortcutInfo(
        id: String,
        label: String,
        pkg: String = app.packageName,
    ): ShortcutInfo =
        ShortcutInfo
            .Builder(app, id)
            .setShortLabel(label)
            .setActivity(ComponentName(pkg, "$pkg.Main"))
            .setIntent(Intent(Intent.ACTION_VIEW))
            .build()

    @Test
    fun `without host permission there are no shortcuts and launch fails`() =
        runTest {
            shadowOf(launcherApps).setHasShortcutHostPermission(false)
            shadowOf(launcherApps).addDynamicShortcut(shortcutInfo("compose", "New message"))
            val source = source()

            assertThat(source.hasHostPermission.first()).isFalse()
            assertThat(source.shortcuts.first()).isEmpty()
            val shortcut = AppShortcut("compose", PackageUser(app.packageName, 0), null, "New message", "Reach")
            assertThat(source.launch(shortcut, origin = null)).isFalse()
        }

    @Test
    fun `with host permission dynamic shortcuts are mapped`() =
        runTest {
            shadowOf(launcherApps).setHasShortcutHostPermission(true)
            shadowOf(launcherApps).addDynamicShortcut(shortcutInfo("compose", "New message"))
            val source = source()

            assertThat(source.hasHostPermission.first()).isTrue()
            val shortcut = source.shortcuts.first().single()
            assertThat(shortcut.id).isEqualTo("compose")
            assertThat(shortcut.label).isEqualTo("New message")
            assertThat(shortcut.app).isEqualTo(PackageUser(app.packageName, 0))
            assertThat(shortcut.activityName).isEqualTo("${app.packageName}.Main")
            assertThat(shortcut.appLabel).isNotEmpty()
        }

    @Test
    fun `a locked private space contributes nothing and isn't read`() {
        var reads = 0
        val result =
            profileShortcuts(ProfileType.PRIVATE, isQuietMode = { true }, serial = 11, appLabel = { it }) {
                reads++
                listOf(entry("secret"))
            }
        assertThat(result).isEmpty()
        assertThat(reads).isEqualTo(0)
    }

    @Test
    fun `a locked or inaccessible profile yields nothing instead of crashing`() {
        assertThat(profileShortcuts(ProfileType.WORK, { true }, 10, { it }) { throw IllegalStateException("user locked") }).isEmpty()
        assertThat(profileShortcuts(ProfileType.WORK, { false }, 10, { it }) { throw SecurityException("no permission") }).isEmpty()
        assertThat(profileShortcuts(ProfileType.WORK, { false }, -1, { it }) { listOf(entry("a")) }).isEmpty()
    }

    @Test
    fun `disabled and unlabelled shortcuts are dropped, long label is the fallback, labels asked once per package`() {
        val asked = mutableListOf<String>()
        val result =
            profileShortcuts(ProfileType.WORK, { false }, serial = 10, appLabel = {
                asked += it
                "Mail"
            }) {
                listOf(
                    entry("a", short = "Compose"),
                    entry("b", short = null, long = "Open inbox"),
                    entry("c", short = "Gone", enabled = false),
                    entry("d", short = " ", long = null),
                )
            }
        assertThat(result.map { it.label }).containsExactly("Compose", "Open inbox").inOrder()
        assertThat(result.first().app).isEqualTo(PackageUser("com.mail", 10))
        assertThat(result.first().appLabel).isEqualTo("Mail")
        assertThat(asked).containsExactly("com.mail")
    }

    private fun entry(
        id: String,
        short: String? = id,
        long: String? = null,
        enabled: Boolean = true,
    ) = ShortcutEntry(id, "com.mail", "com.mail.Main", short, long, enabled)
}
