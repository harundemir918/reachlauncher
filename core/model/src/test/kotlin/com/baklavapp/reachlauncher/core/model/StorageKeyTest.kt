package com.baklavapp.reachlauncher.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Persisted keys must round-trip, and unknown keys must fall back safely. */
class StorageKeyTest {
    @Test
    fun `every enum round trips through its storage key`() {
        ReachIntent.entries.forEach { assertThat(ReachIntent.fromStorageKey(it.storageKey)).isEqualTo(it) }
        PaletteId.entries.forEach { assertThat(PaletteId.fromStorageKey(it.storageKey)).isEqualTo(it) }
        ThemeMode.entries.forEach { assertThat(ThemeMode.fromStorageKey(it.storageKey)).isEqualTo(it) }
        ThumbSide.entries.forEach { assertThat(ThumbSide.fromStorageKey(it.storageKey)).isEqualTo(it) }
    }

    @Test
    fun `an unknown intent key is not silently turned into a user choice`() {
        assertThat(ReachIntent.fromStorageKey("future_intent")).isNull()
    }

    @Test
    fun `unknown settings keys fall back to defaults`() {
        assertThat(PaletteId.fromStorageKey(null)).isEqualTo(PaletteId.LIME)
        assertThat(PaletteId.fromStorageKey("removed_palette")).isEqualTo(PaletteId.LIME)
        assertThat(ThemeMode.fromStorageKey("x")).isEqualTo(ThemeMode.AUTO)
        assertThat(ThumbSide.fromStorageKey("x")).isEqualTo(ThumbSide.LEFT)
    }
}
