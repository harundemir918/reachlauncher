package com.baklavapp.reachlauncher.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for case- and diacritic-insensitive matching, with Turkish rules.
 *
 * Why not just `lowercase()`: in Turkish, `I` lowercases to `ı` (dotless) and `İ` to `i`,
 * while in English `I` lowercases to `i`. A user typing "i" must find "İzmir", "Instagram"
 * and "Kırmızı" alike, and "s" must find "Şarkı". So we collapse every i-variant to `i`
 * and strip combining marks from Latin letters (ş→s, ç→c, ğ→g, ö→o, ü→u) after Unicode
 * decomposition. A few Latin letters that don't decompose are mapped by hand (ß, ø, æ, ł, đ).
 *
 * Marks on non-Latin scripts (Devanagari vowel signs, Hebrew points…) are kept: there
 * they are part of the letter, not an accent.
 *
 * This is for *matching* only. Display text keeps its original form, and alphabetical
 * *sorting* uses `java.text.Collator` for the current locale instead. Folding can change
 * the string's length (ß → ss), so match highlighting must not map folded indices back
 * onto the original text one-to-one.
 *
 * Flutter analogy: what you'd do with `package:diacritic`'s `removeDiacritics()` plus a
 * Turkish-aware `toLowerCase()`, in one function.
 */
object TextFolding {
    private val latinCombiningMarks = Regex("(?<=\\p{IsLatin})\\p{Mn}+")

    private val nonDecomposing = mapOf('ß' to "ss", 'ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ł' to "l", 'đ' to "d")

    /** Returns [text] folded for matching: lower case, no Latin diacritics, i-variants unified. */
    fun fold(text: String): String {
        val mapped =
            buildString(text.length) {
                for (c in text) {
                    when (c) {
                        'İ', 'I', 'ı' -> {
                            append('i')
                        }

                        else -> {
                            val lower = c.lowercaseChar()
                            append(nonDecomposing[lower] ?: lower.toString())
                        }
                    }
                }
            }
        val decomposed = Normalizer.normalize(mapped, Normalizer.Form.NFD)
        return latinCombiningMarks.replace(decomposed, "").lowercase(Locale.ROOT)
    }
}
