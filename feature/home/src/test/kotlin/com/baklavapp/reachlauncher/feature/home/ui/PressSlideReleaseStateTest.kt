package com.baklavapp.reachlauncher.feature.home.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The UI half of press-slide-release without any UI: timing, hit-testing, release/cancel. */
class PressSlideReleaseStateTest {
    private val highlights = mutableListOf<Int?>()
    private val releases = mutableListOf<LaunchOrigin?>()
    private var cancels = 0
    private var ticks = 0

    private val state =
        PressSlideReleaseState(
            onOpen = mutableStateOf({ _: ReachIntent -> true }),
            onHighlight = mutableStateOf({ i: Int? -> highlights += i }),
            onRelease = mutableStateOf({ o: LaunchOrigin? -> releases += o }),
            onCancel = mutableStateOf({ cancels++ }),
            tick = mutableStateOf({ ticks++ }),
            pillGapPx = 10f,
            hitSlopPx = 20f,
        )

    // Word at y 500..552; pill 0 at 430..482, pill 1 at 368..420 (10px gap).
    private val word = Rect(24f, 500f, 200f, 552f)
    private val pill0 = Rect(60f, 430f, 260f, 482f)
    private val pill1 = Rect(76f, 368f, 276f, 420f)

    private fun openFan() {
        state.open(ReachIntent.TALK, word, Offset(100f, 520f))
        state.recordPills(listOf(pill0, pill1))
    }

    @Test
    fun `hold time is 300 ms unless the user chose a longer touch-and-hold delay`() {
        assertThat(fanHoldMillis(400)).isEqualTo(300)
        assertThat(fanHoldMillis(500)).isEqualTo(300)
        assertThat(fanHoldMillis(1_000)).isEqualTo(1_000)
        assertThat(fanHoldMillis(1_500)).isEqualTo(1_500)
    }

    @Test
    fun `hit test uses bands, the word cancels, outside the fan cancels`() {
        openFan()
        assertThat(state.hitTest(Offset(100f, 450f))).isEqualTo(0)
        assertThat(state.hitTest(Offset(100f, 427f))).isEqualTo(0) // in the gap, nearer pill 0
        assertThat(state.hitTest(Offset(290f, 390f))).isEqualTo(1) // drifted right, within slop
        assertThat(state.hitTest(Offset(100f, 520f))).isNull() // back on the word
        assertThat(state.hitTest(Offset(380f, 450f))).isNull() // far to the side
        assertThat(state.hitTest(Offset(100f, 100f))).isNull() // above the fan
    }

    @Test
    fun `each new pill ticks once and release launches from its bounds`() {
        openFan()
        state.moveTo(Offset(100f, 450f))
        state.moveTo(Offset(110f, 455f)) // same pill: no new highlight, no tick
        state.moveTo(Offset(100f, 390f))
        state.moveTo(Offset(100f, 520f)) // word: highlight cleared, no tick
        state.moveTo(Offset(100f, 390f))
        assertThat(highlights).containsExactly(0, 1, null, 1).inOrder()
        assertThat(ticks).isEqualTo(3)

        state.release()
        assertThat(releases).containsExactly(LaunchOrigin(76, 368, 200, 52))
        assertThat(state.anchor).isNull()
    }

    @Test
    fun `release on nothing reports no origin and cancel is idempotent`() {
        openFan()
        state.release()
        assertThat(releases).containsExactly(null)

        openFan()
        state.cancel()
        state.cancel()
        assertThat(cancels).isEqualTo(1)
        assertThat(state.anchor).isNull()
    }
}
