package com.baklavapp.reachlauncher.core.designsystem.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class MonogramsTest {
    private val en = Locale.ENGLISH
    private val tr = Locale.forLanguageTag("tr-TR")

    @Test
    fun `first letters of up to two words`() {
        assertThat(monogramFor("YouTube Music", en)).isEqualTo("YM")
        assertThat(monogramFor("Spotify", en)).isEqualTo("S")
        assertThat(monogramFor("Google Play Store", en)).isEqualTo("GP")
        assertThat(monogramFor("  messages  ", en)).isEqualTo("M")
    }

    @Test
    fun `punctuation splits words, digits count as letters`() {
        assertThat(monogramFor("7-Zip", en)).isEqualTo("7Z")
        assertThat(monogramFor("Wi-Fi Analyzer", en)).isEqualTo("WF")
        assertThat(monogramFor("(beta) notes", en)).isEqualTo("BN")
    }

    @Test
    fun `apostrophes stay inside a word`() {
        assertThat(monogramFor("Ayşe's Notes", en)).isEqualTo("AN")
        assertThat(monogramFor("Ayşe’s Notes", en)).isEqualTo("AN")
    }

    @Test
    fun `emoji and symbols are skipped`() {
        assertThat(monogramFor("🎵 Music", en)).isEqualTo("M")
        assertThat(monogramFor("Radio 📻 FM", en)).isEqualTo("RF")
    }

    @Test
    fun `labels without letters fall back`() {
        assertThat(monogramFor("", en)).isEqualTo(MONOGRAM_FALLBACK)
        assertThat(monogramFor("   ", en)).isEqualTo(MONOGRAM_FALLBACK)
        assertThat(monogramFor("!!! ...", en)).isEqualTo(MONOGRAM_FALLBACK)
        assertThat(monogramFor("🎵🎶", en)).isEqualTo(MONOGRAM_FALLBACK)
    }

    @Test
    fun `turkish upper-casing rules apply under the turkish locale`() {
        assertThat(monogramFor("ılık şarkı", tr)).isEqualTo("IŞ")
        assertThat(monogramFor("iyi günler", tr)).isEqualTo("İG")
        // Under English, dotted i upper-cases to a plain I.
        assertThat(monogramFor("iyi günler", en)).isEqualTo("IG")
    }

    @Test
    fun `decomposed accents are normalised before upper-casing`() {
        // "e" + COMBINING ACUTE ACCENT → "É".
        assertThat(monogramFor("école maps", en)).isEqualTo("ÉM")
    }

    @Test
    fun `non-latin scripts take their first character`() {
        assertThat(monogramFor("微信", en)).isEqualTo("微")
        assertThat(monogramFor("Яндекс Карты", en)).isEqualTo("ЯК")
    }

    @Test
    fun `label case follows the locale`() {
        assertThat("iş".toLabelCase(tr)).isEqualTo("İŞ")
        assertThat("work".toLabelCase(en)).isEqualTo("WORK")
    }

    @Test
    fun `badge counts are capped at 99+`() {
        assertThat(formatBadgeCount(0)).isEmpty()
        assertThat(formatBadgeCount(-3)).isEmpty()
        assertThat(formatBadgeCount(7)).isEqualTo("7")
        assertThat(formatBadgeCount(99)).isEqualTo("99")
        assertThat(formatBadgeCount(100)).isEqualTo("99+")
    }
}
