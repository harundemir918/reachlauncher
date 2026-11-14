package com.baklavapp.reachlauncher.core.system.launch

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import android.provider.ContactsContract
import android.provider.Settings
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.SystemScreen

/**
 * Turns Reach's domain requests into Android `Intent`s. Kept separate from
 * [SystemAppLauncher] so the exact intents are easy to read and to test.
 *
 * Android background for Flutter developers: an `Intent` is a message asking the system
 * "find an app/screen that can do this". `ACTION_DIAL` + a `tel:` URI is roughly what
 * `url_launcher` sends when you `launchUrl(Uri.parse('tel:…'))`.
 */
internal object SystemIntents {
    /**
     * Settings screens for each [SystemScreen] except [SystemScreen.HOME_ROLE], whose
     * role-dialog intent comes from `RoleManager` (see [SystemAppLauncher.open]); this
     * returns the fallback Home settings screen for it.
     */
    fun settingsScreen(screen: SystemScreen): Intent =
        when (screen) {
            // The listener list; the per-component detail screen (API 30) needs Reach's
            // listener service, which arrives in M3.
            SystemScreen.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

            SystemScreen.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

            SystemScreen.ACCESSIBILITY_SERVICE -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            SystemScreen.HOME_ROLE -> Intent(Settings.ACTION_HOME_SETTINGS)
        }

    /** Hand-off intents; none needs a permission (dialing pre-fills, it doesn't call). */
    fun external(action: ExternalAction): Intent =
        when (action) {
            is ExternalAction.WebSearch -> {
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query)
            }

            is ExternalAction.Dial -> {
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.phoneNumber, null))
            }

            is ExternalAction.Message -> {
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.phoneNumber, null))
            }

            is ExternalAction.ViewContact -> {
                Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(action.id, action.lookupKey))
            }
        }

    /**
     * The system uninstall confirmation for [packageName] in [user]'s profile.
     * `ACTION_DELETE` with a `package:` URI is what launchers use (the older
     * `ACTION_UNINSTALL_PACKAGE` is deprecated since API 29). `EXTRA_USER` targets the work
     * copy instead of the personal one. Requires `REQUEST_DELETE_PACKAGES` (normal
     * permission) for apps targeting API 28+.
     */
    fun uninstall(
        packageName: String,
        user: UserHandle,
    ): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
}
