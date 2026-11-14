package com.baklavapp.reachlauncher.core.system.roles

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.DefaultAppsSource
import com.baklavapp.reachlauncher.core.data.source.HomeRoleSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default dialer and SMS packages, for putting them into Talk.
 *
 * Deviation from spec §6.1 ("via `RoleManager`"): `RoleManager.getRoleHolders` is a
 * system API (needs `MANAGE_ROLE_HOLDERS`); third-party apps can only ask about roles
 * *they* hold. The public, permission-free equivalents are
 * `TelecomManager.getDefaultDialerPackage()` and `Telephony.Sms.getDefaultSmsPackage()`,
 * which report the same role holders.
 */
@Singleton
class TelephonyDefaultAppsSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DefaultAppsSource {
        override suspend fun talkRolePackages(): Set<String> =
            withContext(ioDispatcher) {
                buildSet {
                    // Tablets without telephony may lack these services or throw; both are optional.
                    try {
                        context.getSystemService<TelecomManager>()?.defaultDialerPackage?.let(::add)
                    } catch (e: SecurityException) {
                        // Ignore: no dialer override.
                    }
                    try {
                        Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
                    } catch (e: SecurityException) {
                        // Ignore: no SMS override.
                    }
                }
            }
    }

/**
 * Whether Reach is the default home app.
 *
 * API 29+: `RoleManager.isRoleHeld(ROLE_HOME)` — asks about our own role, no permission.
 * API 28 has no roles: resolve which activity answers the HOME intent by default and
 * compare its package with ours. (No package-visibility rules exist on API 28, so this
 * lookup needs no `<queries>` entry.)
 *
 * There is no callback when the user changes the default home elsewhere, hence
 * [recheck] on every resume.
 */
@Singleton
class RoleHomeRoleSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : HomeRoleSource {
        private val rechecks = MutableStateFlow(0)

        override val isDefaultHome: Flow<Boolean> =
            rechecks
                .map { read() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun recheck() = rechecks.update { it + 1 }

        private fun read(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
            } else {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager
                    .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName == context.packageName
            }
    }
