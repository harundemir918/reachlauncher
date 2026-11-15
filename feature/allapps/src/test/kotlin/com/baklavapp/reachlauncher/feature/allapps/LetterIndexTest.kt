package com.baklavapp.reachlauncher.feature.allapps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.Collator
import java.util.Locale

/**
 * [LetterIndex] buckets. Robolectric because `android.icu` is part of the Android
 * framework, not the JVM; Robolectric runs the real framework classes with real ICU data.
 */
@RunWith(RobolectricTestRunner::class)
class LetterIndexTest {
    private val names = listOf("Çalar", "Şarkı", "İnstagram", "ılık", "Zil", "Ödeme", "7-Zip", "Ελληνικά", "WhatsApp", "Instagram")

    /** Groups [names] the way the ViewModel does: Collator-sorted first, then bucketed. */
    private fun grouped(locale: Locale): Map<String, List<String>> {
        val collator = Collator.getInstance(locale)
        return LetterIndex(locale).group(names.sortedWith(collator)) { it }.toMap()
    }

    @Test
    fun turkishHasItsOwnLetters() {
        val groups = grouped(Locale.forLanguageTag("tr"))
        assertThat(groups["Ç"]).containsExactly("Çalar")
        assertThat(groups["Ş"]).containsExactly("Şarkı")
        assertThat(groups["İ"]).containsExactly("İnstagram")
        // Dotless ı is the lower case of I in Turkish.
        assertThat(groups["I"]).containsExactly("ılık", "Instagram").inOrder()
        assertThat(groups["Ö"]).containsExactly("Ödeme")
        assertThat(groups["Z"]).containsExactly("Zil")
        // Latin letters outside the Turkish alphabet still get a bucket.
        assertThat(groups["W"]).containsExactly("WhatsApp")
        assertThat(groups[LetterIndex.OTHER]).containsExactly("7-Zip", "Ελληνικά")
    }

    @Test
    fun turkishLetterOrderPutsEachLetterAfterItsBase() {
        val letters = LetterIndex(Locale.forLanguageTag("tr")).letters
        assertThat(letters).containsAtLeast("C", "Ç", "I", "İ", "O", "Ö", "S", "Ş", "U", "Ü", "W").inOrder()
        assertThat(letters.last()).isEqualTo(LetterIndex.OTHER)
    }

    @Test
    fun englishFoldsAccents() {
        val groups = grouped(Locale.ENGLISH)
        assertThat(groups["C"]).containsExactly("Çalar")
        assertThat(groups["S"]).containsExactly("Şarkı")
        assertThat(groups["I"]).containsExactly("ılık", "Instagram", "İnstagram")
        assertThat(groups["O"]).containsExactly("Ödeme")
        assertThat(groups[LetterIndex.OTHER]).containsExactly("7-Zip", "Ελληνικά")
        assertThat(groups.keys).doesNotContain("Ç")
    }

    @Test
    fun groupsFollowLetterOrderAndKeepInputOrder() {
        val index = LetterIndex(Locale.ENGLISH)
        val result = index.group(listOf("Zil", "7-Zip", "Abc", "Apple")) { it }
        assertThat(result.map { it.first }).containsExactly("A", "Z", LetterIndex.OTHER).inOrder()
        assertThat(result.first().second).containsExactly("Abc", "Apple").inOrder()
    }

    @Test
    fun englishRailIsAToZPlusOther() {
        val letters = LetterIndex(Locale.ENGLISH).letters
        assertThat(letters).isEqualTo(('A'..'Z').map { it.toString() } + LetterIndex.OTHER)
    }
}
