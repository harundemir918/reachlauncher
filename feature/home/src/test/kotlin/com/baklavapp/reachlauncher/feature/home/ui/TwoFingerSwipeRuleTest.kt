package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure two-finger rule behind Home's "swipe down with two fingers → notifications". */
class TwoFingerSwipeRuleTest {
    private val min = 100f

    @Test
    fun `two fingers moving down far enough count`() {
        assertThat(isTwoFingerSwipeDown(listOf(Offset(0f, 120f), Offset(10f, 101f)), min)).isTrue()
    }

    @Test
    fun `one finger never counts`() {
        assertThat(isTwoFingerSwipeDown(listOf(Offset(0f, 500f)), min)).isFalse()
    }

    @Test
    fun `both fingers must travel the distance`() {
        assertThat(isTwoFingerSwipeDown(listOf(Offset(0f, 120f), Offset(0f, 60f)), min)).isFalse()
    }

    @Test
    fun `upward or sideways moves don't count`() {
        assertThat(isTwoFingerSwipeDown(listOf(Offset(0f, -150f), Offset(0f, -150f)), min)).isFalse()
        assertThat(isTwoFingerSwipeDown(listOf(Offset(200f, 120f), Offset(200f, 120f)), min)).isFalse()
    }
}
