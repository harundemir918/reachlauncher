package com.baklavapp.reachlauncher.core.system.launch

import android.app.Activity
import android.app.Application
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.SystemScreen
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SystemAppLauncherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tracker = ActivityTracker()
    private val launcher = SystemAppLauncher(app, tracker, Profiles(app))

    private fun nextStarted(): Intent = checkNotNull(shadowOf(app).nextStartedActivity) { "no activity started" }

    @Test
    fun `settings screens map to their system intents`() {
        val expected =
            mapOf(
                SystemScreen.NOTIFICATION_ACCESS to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                SystemScreen.USAGE_ACCESS to Settings.ACTION_USAGE_ACCESS_SETTINGS,
                SystemScreen.ACCESSIBILITY_SERVICE to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            )
        expected.forEach { (screen, action) ->
            assertThat(launcher.open(screen)).isTrue()
            val intent = nextStarted()
            assertThat(intent.action).isEqualTo(action)
            // No Activity is resumed, so the app context needs a new task.
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        }
    }

    @Test
    fun `web search carries the query`() {
        assertThat(launcher.perform(ExternalAction.WebSearch("hava durumu"))).isTrue()
        val intent = nextStarted()
        assertThat(intent.action).isEqualTo(Intent.ACTION_WEB_SEARCH)
        assertThat(intent.getStringExtra(SearchManager.QUERY)).isEqualTo("hava durumu")
    }

    @Test
    fun `dial, message and contact build the right URIs`() {
        launcher.perform(ExternalAction.Dial("+90 555 123 45 67"))
        nextStarted().let {
            assertThat(it.action).isEqualTo(Intent.ACTION_DIAL)
            assertThat(it.data).isEqualTo(Uri.fromParts("tel", "+90 555 123 45 67", null))
        }
        launcher.perform(ExternalAction.Message("5551234"))
        nextStarted().let {
            assertThat(it.action).isEqualTo(Intent.ACTION_SENDTO)
            assertThat(it.data?.scheme).isEqualTo("smsto")
            assertThat(it.data?.schemeSpecificPart).isEqualTo("5551234")
        }
        launcher.perform(ExternalAction.ViewContact(42, "abc"))
        nextStarted().let {
            assertThat(it.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(it.data).isEqualTo(ContactsContract.Contacts.getLookupUri(42, "abc"))
        }
    }

    @Test
    fun `nothing to handle a hand-off returns false`() {
        shadowOf(app).checkActivities(true)
        assertThat(launcher.perform(ExternalAction.WebSearch("x"))).isFalse()
    }

    @Test
    fun `with a resumed activity, intents start from it without a new task`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        tracker.onActivityResumed(activity)
        launcher.perform(ExternalAction.Dial("1"))
        val intent = checkNotNull(shadowOf(activity).nextStartedActivity)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)

        tracker.onActivityPaused(activity)
        assertThat(tracker.resumedActivity).isNull()
    }

    @Test
    fun `home role asks RoleManager from the resumed activity`() {
        val roleManager = app.getSystemService(RoleManager::class.java)
        @Suppress("DEPRECATION") // Robolectric's simplest way to mark a role available.
        shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_HOME)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        tracker.onActivityResumed(activity)

        assertThat(launcher.open(SystemScreen.HOME_ROLE)).isTrue()
        val forResult = checkNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertThat(forResult.requestCode).isEqualTo(SystemAppLauncher.REQUEST_HOME_ROLE)
    }

    @Test
    fun `home role falls back to Home settings without an activity`() {
        assertThat(launcher.open(SystemScreen.HOME_ROLE)).isTrue()
        assertThat(nextStarted().action).isEqualTo(Settings.ACTION_HOME_SETTINGS)
    }

    @Test
    @Config(sdk = [28])
    fun `home role falls back to Home settings on Android 9`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        tracker.onActivityResumed(activity)
        assertThat(launcher.open(SystemScreen.HOME_ROLE)).isTrue()
        assertThat(checkNotNull(shadowOf(activity).nextStartedActivity).action).isEqualTo(Settings.ACTION_HOME_SETTINGS)
    }

    @Test
    fun `uninstall targets the package in its profile`() {
        launcher.requestUninstall(AppKey("com.example.game", "com.example.game.Main", userSerial = 0))
        val intent = nextStarted()
        assertThat(intent.action).isEqualTo(Intent.ACTION_DELETE)
        assertThat(intent.data).isEqualTo(Uri.parse("package:com.example.game"))
        @Suppress("DEPRECATION") // Typed getParcelableExtra is API 33+.
        assertThat(intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)).isEqualTo(android.os.Process.myUserHandle())
    }

    @Test
    fun `launching an app of a removed profile fails`() {
        assertThat(launcher.launchApp(AppKey("com.example", "com.example.Main", userSerial = 999), origin = null)).isFalse()
    }

    @Test
    fun `launching an app of a paused work profile asks to resume it`() {
        val userManager = app.getSystemService(UserManager::class.java)
        val work = UserHandle.getUserHandleForUid(WORK_USER_ID * PER_USER_RANGE)
        shadowOf(userManager).addProfile(0, WORK_USER_ID, "Work", FLAG_MANAGED_PROFILE or FLAG_QUIET_MODE)
        shadowOf(userManager).setSerialNumberForUser(work, 10)
        assertThat(userManager.isQuietModeEnabled(work)).isTrue()

        assertThat(launcher.launchApp(AppKey("com.example", "com.example.Main", userSerial = 10), origin = null)).isTrue()
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    private companion object {
        const val WORK_USER_ID = 10
        const val PER_USER_RANGE = 100_000

        // android.content.pm.UserInfo flags (hidden API, stable values).
        const val FLAG_MANAGED_PROFILE = 0x20
        const val FLAG_QUIET_MODE = 0x80
    }
}
