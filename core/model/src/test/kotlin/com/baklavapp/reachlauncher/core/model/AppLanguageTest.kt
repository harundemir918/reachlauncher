package com.baklavapp.reachlauncher.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Reading the language back from what Android or the stored preference reports. */
class AppLanguageTest {
    @Test
    fun `a tag maps to its language whatever the region`() {
        assertThat(AppLanguage.fromTags("es")).isEqualTo(AppLanguage.SPANISH)
        assertThat(AppLanguage.fromTags("es-MX")).isEqualTo(AppLanguage.SPANISH)
        assertThat(AppLanguage.fromTags("tr_TR")).isEqualTo(AppLanguage.TURKISH)
        assertThat(AppLanguage.fromTags("EN-us")).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `only the first of several tags counts`() {
        assertThat(AppLanguage.fromTags("tr-TR,en-US")).isEqualTo(AppLanguage.TURKISH)
    }

    @Test
    fun `empty, missing or unknown tags follow the system`() {
        assertThat(AppLanguage.fromTags(null)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTags("")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTags("fr-FR")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun `every language's tag reads back as itself`() {
        AppLanguage.entries.forEach { assertThat(AppLanguage.fromTags(it.tag)).isEqualTo(it) }
    }
}
