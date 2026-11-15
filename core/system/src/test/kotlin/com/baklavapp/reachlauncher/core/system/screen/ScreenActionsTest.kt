package com.baklavapp.reachlauncher.core.system.screen

import android.accessibilityservice.AccessibilityService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The accessibility hub and the "enabled" rule of [AccessibilityScreenActionsSource] (D66). */
class ScreenActionsTest {
    @Test
    fun `nothing is performed while the service is not connected`() {
        val hub = ScreenActionsHub()
        assertThat(hub.isConnected.value).isFalse()
        assertThat(hub.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)).isFalse()
    }

    @Test
    fun `a connected service performs the action until it disconnects`() {
        val hub = ScreenActionsHub()
        val performed = mutableListOf<Int>()
        hub.onConnected {
            performed += it
            true
        }
        assertThat(hub.isConnected.value).isTrue()
        assertThat(hub.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)).isTrue()
        assertThat(performed).containsExactly(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

        hub.onDisconnected()
        assertThat(hub.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)).isFalse()
        assertThat(performed).hasSize(1)
    }

    @Test
    fun `a refused or crashing action reports false instead of throwing`() {
        val hub = ScreenActionsHub()
        hub.onConnected { false }
        assertThat(hub.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)).isFalse()
        hub.onConnected { throw IllegalStateException("service gone") }
        assertThat(hub.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)).isFalse()
    }

    @Test
    fun `enabled needs both the settings listing and a live connection`() =
        runTest {
            val listed = MutableStateFlow(false)
            val connected = MutableStateFlow(false)
            val enabled = serviceEnabledFlow(listed, connected)
            assertThat(enabled.first()).isFalse()
            listed.value = true
            assertThat(enabled.first()).isFalse() // Switched on, not bound yet.
            connected.value = true
            assertThat(enabled.first()).isTrue()
            listed.value = false
            assertThat(enabled.first()).isFalse() // Switched off, unbind still in flight.
        }
}
