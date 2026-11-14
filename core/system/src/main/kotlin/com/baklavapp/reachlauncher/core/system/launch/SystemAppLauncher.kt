package com.baklavapp.reachlauncher.core.system.launch

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.SystemScreen
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import com.baklavapp.reachlauncher.core.system.profile.Profiles
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts apps, settings screens and hand-offs.
 *
 * Launch animation: `ActivityOptions.makeClipRevealAnimation(view, x, y, w, h)` grows the
 * new app's window out of a rectangle given *relative to `view`*. [LaunchOrigin] is in
 * window pixels (Compose `boundsInWindow()`), and the decor view is the window's root
 * view, so the origin can be passed straight through. `sourceBounds` (a hint the target
 * app may read, e.g. for its own transition) must be in *screen* coordinates, so the
 * decor view's on-screen offset is added (it differs from 0 in split-screen / freeform).
 *
 * The Activity comes from [ActivityTracker] (D11). Without a resumed Activity — e.g. a
 * call from a background coroutine — apps launch without animation from the
 * application context, which then needs `FLAG_ACTIVITY_NEW_TASK`.
 *
 * Every method catches the exceptions the system documents (`ActivityNotFoundException`
 * for a vanished target, `SecurityException` for a disabled/unexported one) and reports
 * `false` instead of crashing Home.
 *
 * Flutter analogy: the implementation behind a `url_launcher`/`android_intent_plus`-style
 * service class — here calling the Android APIs directly, no MethodChannel.
 */
@Singleton
class SystemAppLauncher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tracker: ActivityTracker,
        private val profiles: Profiles,
    ) : AppLauncher {
        private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())

        override fun launchApp(
            key: AppKey,
            origin: LaunchOrigin?,
        ): Boolean {
            val user = profiles.userFor(key.userSerial) ?: return false
            if (profiles.isQuietMode(user)) {
                // A locked private space stays locked; unlocking is an explicit action on Home.
                if (profiles.typeOf(user) == ProfileType.PRIVATE) return false
                // Paused work profile: ask to resume. The system may show its own
                // confirmation/credential UI; the user taps the app again once it's resumed.
                profiles.requestQuietMode(user, enabled = false)
                return true
            }
            val component = ComponentName(key.packageName, key.activityName)
            val activity = tracker.resumedActivity
            val (sourceBounds, options) = animationFor(activity, origin)
            return try {
                launcherApps.startMainActivity(component, user, sourceBounds, options)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        private fun animationFor(
            activity: Activity?,
            origin: LaunchOrigin?,
        ): Pair<Rect?, Bundle?> {
            if (activity == null || origin == null) return null to null
            val decorView = activity.window?.decorView ?: return null to null
            val options =
                ActivityOptions
                    .makeClipRevealAnimation(decorView, origin.left, origin.top, origin.width, origin.height)
                    .toBundle()
            val onScreen = IntArray(2)
            decorView.getLocationOnScreen(onScreen)
            val left = origin.left + onScreen[0]
            val top = origin.top + onScreen[1]
            return Rect(left, top, left + origin.width, top + origin.height) to options
        }

        override fun openAppInfo(key: AppKey) {
            val user = profiles.userFor(key.userSerial) ?: return
            try {
                launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.activityName), user, null, null)
            } catch (e: ActivityNotFoundException) {
                // Nothing to show; the menu simply closes.
            } catch (e: SecurityException) {
                // Same.
            }
        }

        override fun requestUninstall(key: AppKey) {
            val user = profiles.userFor(key.userSerial) ?: return
            start(SystemIntents.uninstall(key.packageName, user))
        }

        override fun open(screen: SystemScreen): Boolean {
            if (screen == SystemScreen.HOME_ROLE && requestHomeRole()) return true
            return start(SystemIntents.settingsScreen(screen))
        }

        /**
         * The "Set Reach as default home?" dialog (API 29+). `RoleManager` documents its
         * intent for `startActivityForResult` — the dialog relies on a calling Activity, so
         * it needs the tracked one; the result itself isn't needed because
         * `HomeRoleSource.recheck()` runs on resume. Returns false to fall back to the Home
         * settings screen (API 28, no Activity, or role unavailable).
         */
        private fun requestHomeRole(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val activity = tracker.resumedActivity ?: return false
            val roleManager = context.getSystemService<RoleManager>() ?: return false
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return false
            return try {
                activity.startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }

        override fun perform(action: ExternalAction): Boolean = start(SystemIntents.external(action))

        /** Starts [intent] from the resumed Activity if any, else from the app context. */
        private fun start(intent: Intent): Boolean {
            val activity = tracker.resumedActivity
            return try {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        companion object {
            /** Request code for the home-role dialog; results are ignored (see [requestHomeRole]). */
            const val REQUEST_HOME_ROLE = 0x4E01
        }
    }
