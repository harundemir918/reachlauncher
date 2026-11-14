package com.baklavapp.reachlauncher.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextFoldingTest {
    @Test
    fun `dotted and dotless i variants all fold to i`() {
        assertThat(TextFolding.fold("İzmir")).isEqualTo("izmir")
        assertThat(TextFolding.fold("Instagram")).isEqualTo("instagram")
        assertThat(TextFolding.fold("Kırmızı")).isEqualTo("kirmizi")
    }

    @Test
    fun `turkish diacritics are stripped`() {
        assertThat(TextFolding.fold("Şarkı Çalar Öğün Ümit")).isEqualTo("sarki calar ogun umit")
    }

    @Test
    fun `latin accents are stripped`() {
        assertThat(TextFolding.fold("Café Ñandú")).isEqualTo("cafe nandu")
    }

    @Test
    fun `latin letters without a decomposition are mapped`() {
        assertThat(TextFolding.fold("Straße Øl Æble Łódź Đak")).isEqualTo("strasse ol aeble lodz dak")
    }

    @Test
    fun `marks on non-latin scripts are kept`() {
        // Devanagari "हिंदी": its vowel signs are combining marks but part of the letters.
        val hindi = "हिंदी"
        assertThat(TextFolding.fold(hindi)).isEqualTo(java.text.Normalizer.normalize(hindi, java.text.Normalizer.Form.NFD))
    }
}
