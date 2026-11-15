package com.baklavapp.reachlauncher.core.system.unavailable

import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/*
 * Placeholder sources for integrations of later milestones. Each reports exactly what the
 * real one reports when its permission is denied, so the app already behaves correctly
 * (every feature degrades gracefully) and the Hilt graph is complete. Replace the binding
 * in SystemModule when the real implementation lands.
 *
 * Flutter analogy: registering a no-op implementation in get_it until the real plugin
 * integration exists.
 */

/** Accessibility service off. Replaced in M5 by the opt-in accessibility service. */
class UnavailableScreenActionsSource
    @Inject
    constructor() : ScreenActionsSource {
        override val isServiceEnabled: Flow<Boolean> = flowOf(false)

        override fun lockScreen(): Boolean = false

        override fun openNotificationShade(allowHiddenApi: Boolean): Boolean = false
    }
