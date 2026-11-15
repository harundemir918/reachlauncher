package com.baklavapp.reachlauncher.feature.allapps.ui

import com.baklavapp.reachlauncher.feature.allapps.RailLetter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rail's y → letter mapping (plain JVM). */
class RailLetterAtTest {
    // A, B (empty), C, D (empty), E — five slots of 20px on a 100px rail.
    private val letters =
        listOf(
            RailLetter("A", 0),
            RailLetter("B", null),
            RailLetter("C", 1),
            RailLetter("D", null),
            RailLetter("E", 2),
        )

    @Test
    fun mapsSlotsToLetters() {
        assertThat(railLetterAt(letters, 5f, 100f)?.letter).isEqualTo("A")
        assertThat(railLetterAt(letters, 45f, 100f)?.letter).isEqualTo("C")
        assertThat(railLetterAt(letters, 99f, 100f)?.letter).isEqualTo("E")
    }

    @Test
    fun emptyLettersResolveToTheNearestLetterWithApps() {
        // B is between A and C: a tie, the earlier one wins.
        assertThat(railLetterAt(letters, 30f, 100f)?.letter).isEqualTo("A")
        assertThat(railLetterAt(letters, 70f, 100f)?.letter).isEqualTo("C")
    }

    @Test
    fun positionsOutsideTheRailClamp() {
        assertThat(railLetterAt(letters, -50f, 100f)?.letter).isEqualTo("A")
        assertThat(railLetterAt(letters, 500f, 100f)?.letter).isEqualTo("E")
    }

    @Test
    fun noLetterWithAppsMeansNoTarget() {
        assertThat(railLetterAt(listOf(RailLetter("A", null)), 1f, 10f)).isNull()
        assertThat(railLetterAt(emptyList(), 1f, 10f)).isNull()
        assertThat(railLetterAt(letters, 1f, 0f)).isNull()
    }
}
