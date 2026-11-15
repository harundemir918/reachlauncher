package com.baklavapp.reachlauncher.core.system.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import com.baklavapp.reachlauncher.core.data.di.IoDispatcher
import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import com.baklavapp.reachlauncher.core.system.access.AccessRechecks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether screen actions are usable: the user has Reach's service switched on in Settings
 * ([listed]) *and* the system has actually connected it ([connected]).
 *
 * Both, because each alone can lie for a moment: right after the user switches the service
 * on, Settings already lists it but the system hasn't bound it yet (an action would fail);
 * right after switching it off, the unbind may still be in flight. Pure, so it is tested
 * on the JVM.
 */
internal fun serviceEnabledFlow(
    listed: Flow<Boolean>,
    connected: Flow<Boolean>,
): Flow<Boolean> = combine(listed, connected) { isListed, isConnected -> isListed && isConnected }.distinctUntilChanged()

/**
 * The real [ScreenActionsSource] (spec §7.4): lock screen and notification shade through
 * [ReachAccessibilityService], with a best-effort hidden-API fallback for the shade.
 *
 * **Enabled state.** Android sends no broadcast when the user toggles an accessibility
 * service, so — like notification and usage access (D53) — the Settings listing is re-read on
 * every Reach resume ([AccessRechecks]) and combined with the hub's live connection
 * ([serviceEnabledFlow]).
 *
 * **Shade without the service.** `StatusBarManager.expandNotificationsPanel()` is a hidden
 * (non-SDK) method; many launchers call it through reflection, with the normal
 * `EXPAND_STATUS_BAR` permission. Non-SDK access may be blocked on any release, so Reach
 * only tries it when the caller passes `allowHiddenApi` (the `hiddenApiShadeFallback`
 * setting, off by default) and reports `false` on any failure — the caller then shows a
 * hint instead (docs/DECISIONS.md).
 *
 * Flutter analogy: a plugin class whose `isEnabled` stream is re-polled from
 * `didChangeAppLifecycleState(resumed)` and whose methods return `Future<bool>`-like
 * results instead of throwing.
 */
@Singleton
class AccessibilityScreenActionsSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val hub: ScreenActionsHub,
        rechecks: AccessRechecks,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : ScreenActionsSource {
        private val ownService = ComponentName(context, ReachAccessibilityService::class.java)

        override val isServiceEnabled: Flow<Boolean> =
            serviceEnabledFlow(
                listed = rechecks.ticks.map { isListedAsEnabled() }.flowOn(ioDispatcher),
                connected = hub.isConnected,
            )

        override fun lockScreen(): Boolean = hub.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

        override fun openNotificationShade(allowHiddenApi: Boolean): Boolean =
            hub.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) || (allowHiddenApi && expandWithHiddenApi())

        /** `AccessibilityManager`'s list of enabled services — the documented way to ask. */
        private fun isListedAsEnabled(): Boolean {
            val manager = context.getSystemService<AccessibilityManager>() ?: return false
            return try {
                manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { info ->
                        val service = info.resolveInfo?.serviceInfo
                        service != null && ComponentName(service.packageName, service.name) == ownService
                    }
            } catch (e: RuntimeException) {
                false // Fail closed: "not enabled" only hides the gestures' TalkBack actions.
            }
        }

        /**
         * Best effort: `StatusBarManager.expandNotificationsPanel()` by reflection. Any failure —
         * method gone, non-SDK access blocked, permission refused — answers false.
         *
         * Lint: "statusbar" is `Context.STATUS_BAR_SERVICE`, public only from API 29+ in the SDK
         * stubs, hence the literal (WrongConstant); the reflective call is the point of this
         * fallback (PrivateApi/DiscouragedPrivateApi), kept behind a default-off flag.
         */
        @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi")
        private fun expandWithHiddenApi(): Boolean =
            try {
                val statusBar = context.getSystemService("statusbar") ?: return false
                statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
                true
            } catch (e: ReflectiveOperationException) {
                false
            } catch (e: RuntimeException) {
                false // SecurityException, or the hidden-API policy refusing the call.
            }
    }
