package com.baklavapp.reachlauncher.core.system.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs one `AccessibilityService.GLOBAL_ACTION_*`. A function type rather than the
 * service itself, so the hub (and its tests) never hold or need a real service.
 */
fun interface GlobalActionPerformer {
    /** @return what `performGlobalAction` returned: false when the system refused. */
    fun perform(action: Int): Boolean
}

/**
 * The meeting point between [ReachAccessibilityService] (created and bound by the *system*)
 * and [AccessibilityScreenActionsSource] (created by Hilt for the ViewModels).
 *
 * **Why a hub.** Global actions (`performGlobalAction`) can only be called on the connected
 * service instance, and Android — not Reach — decides when that instance exists: it binds the
 * service when the user switches it on in Settings and unbinds it when they switch it off.
 * The service registers a [GlobalActionPerformer] here on connect and clears it on unbind,
 * so the source can act without ever holding the service, and the service never leaks
 * through a long-lived singleton after it is gone.
 *
 * Flutter analogy: the singleton `StreamController` a plugin's native background service
 * pushes into, which the Dart side listens to — here with a "connected" value instead of
 * events.
 */
@Singleton
class ScreenActionsHub
    @Inject
    constructor() {
        private val performer = MutableStateFlow<GlobalActionPerformer?>(null)
        private val connected = MutableStateFlow(false)

        /** True while the system keeps Reach's accessibility service connected. */
        val isConnected: StateFlow<Boolean> = connected.asStateFlow()

        /** Called by the service in `onServiceConnected`. */
        fun onConnected(actions: GlobalActionPerformer) {
            performer.value = actions
            connected.value = true
        }

        /** Called by the service when it is unbound or destroyed. */
        fun onDisconnected() {
            performer.value = null
            connected.value = false
        }

        /**
         * Performs [action] through the connected service.
         * @return false when the service isn't connected or the system refused.
         */
        fun perform(action: Int): Boolean =
            try {
                performer.value?.perform(action) ?: false
            } catch (e: RuntimeException) {
                // A service torn down mid-call (DeadObject surfaces as a RuntimeException):
                // report "not done" instead of crashing Home.
                false
            }
    }
