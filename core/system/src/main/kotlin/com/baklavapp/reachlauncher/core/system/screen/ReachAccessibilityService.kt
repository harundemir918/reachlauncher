package com.baklavapp.reachlauncher.core.system.screen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reach's optional accessibility service (spec §7.4). It exists for exactly two things:
 * locking the screen (`GLOBAL_ACTION_LOCK_SCREEN`, double-tap on empty Home) and opening the
 * notification shade (`GLOBAL_ACTION_NOTIFICATIONS`, two-finger swipe down on Home). No other
 * public API lets a third-party launcher do either without device admin, and device-admin
 * `lockNow()` would force a PIN/password unlock instead of biometrics — so Reach never uses it.
 *
 * **What it does not do.** It reads nothing: the config (`res/xml/reach_accessibility_service.xml`)
 * subscribes to no event types, limits events to Reach's own package, and does not ask for
 * window content, gestures, key events or screenshots. [onAccessibilityEvent] is therefore
 * never called with anything meaningful and ignores whatever arrives.
 *
 * Android background for Flutter developers: an `AccessibilityService` is a `Service` the
 * *system* binds when the user switches it on in Settings › Accessibility (there is no runtime
 * dialog). While bound it may perform "global actions" (Back, Home, lock, shade…) on the
 * user's behalf. Declared `exported="true"` (the system binds it from outside our process)
 * and protected by `BIND_ACCESSIBILITY_SERVICE`, which only the system holds.
 *
 * Flutter analogy: the native, always-bound half of a plugin that the Dart side reaches
 * through a singleton ([ScreenActionsHub]) instead of a MethodChannel.
 */
@AndroidEntryPoint
class ReachAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var hub: ScreenActionsHub

    override fun onServiceConnected() {
        super.onServiceConnected()
        hub.onConnected { action -> performGlobalAction(action) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hub.onDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hub.onDisconnected()
        super.onDestroy()
    }

    /** Subscribed to no event types: nothing to do. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /** No feedback in progress that could be interrupted. */
    override fun onInterrupt() = Unit
}
