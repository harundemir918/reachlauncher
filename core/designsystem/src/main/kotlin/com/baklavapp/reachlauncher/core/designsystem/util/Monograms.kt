package com.baklavapp.reachlauncher.core.designsystem.util

import java.text.Normalizer
import java.util.Locale

/*
 * Pure text helpers behind monograms and mono labels. No Compose, no Android: they run in
 * plain JVM unit tests (≈ a Dart file of top-level functions you'd test with `package:test`).
 */

/** Shown when a label has no letters or digits at all (empty, emoji-only, punctuation-only). */
const val MONOGRAM_FALLBACK = "?"

/**
 * The letters inside an app's monogram circle (spec §5.4): the first letter of each of the
 * first two *words* of [label], upper-cased with [locale].
 *
 * Why a custom tokenizer instead of `split(" ")`: app names are messy. A "word" here is a
 * run of letters/digits (apostrophes and combining accents stay inside the word), so
 * - "YouTube Music" → "YM", "Spotify" → "S" (camelCase is *one* word: names, not initials);
 * - "7-Zip" → "7Z", "Wi-Fi Analyzer" → "WF" (hyphens and other punctuation split words);
 * - "Ayşe's Notes" → "AN" (the apostrophe doesn't start a new word);
 * - "🎵 Music" → "M" (emoji and symbols are skipped);
 * - "", "!!!", "🎵" → [MONOGRAM_FALLBACK].
 *
 * Upper-casing uses the *locale's* rules, so under Turkish "ılık şarkı" → "IŞ" and
 * "iyi" → "İ" (a plain `uppercase()` would turn "i" into "I"). Defaults to the device locale,
 * which is also the per-app language when one is set.
 */
fun monogramFor(
    label: String,
    locale: Locale = Locale.getDefault(),
): String {
    // NFC first, so "e" + combining acute becomes one "é" code point we can upper-case.
    val text = Normalizer.normalize(label, Normalizer.Form.NFC)
    val initials = StringBuilder()
    var inWord = false
    var words = 0
    var i = 0
    while (i < text.length && words < MAX_MONOGRAM_LETTERS) {
        val cp = text.codePointAt(i)
        when {
            Character.isLetterOrDigit(cp) -> {
                if (!inWord) {
                    initials.appendCodePoint(cp)
                    words++
                }
                inWord = true
            }

            // Keep "Ayşe's" and combining marks inside the current word.
            inWord && (cp in WORD_JOINERS || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()) -> {
                // Still inside the same word: no new initial.
            }

            else -> {
                inWord = false
            }
        }
        i += Character.charCount(cp)
    }
    return if (initials.isEmpty()) MONOGRAM_FALLBACK else initials.toString().uppercase(locale)
}

/**
 * Upper-cases a mono label ("work" → "WORK") with [locale]'s rules — Turkish "iş" → "İŞ".
 *
 * Why not in the strings: translators write natural case, and Compose has no CSS
 * `text-transform`. Labels are upper-cased at display time instead (see `MonoLabel`).
 */
fun String.toLabelCase(locale: Locale = Locale.getDefault()): String = uppercase(locale)

/**
 * Text for a numeric badge: the count itself, capped at "99+" so a badge never grows
 * wider than a short word. Returns an empty string for counts ≤ 0 (callers hide the badge).
 */
fun formatBadgeCount(count: Int): String =
    when {
        count <= 0 -> ""
        count > MAX_BADGE_COUNT -> "$MAX_BADGE_COUNT+"
        else -> count.toString()
    }

private const val MAX_MONOGRAM_LETTERS = 2
private const val MAX_BADGE_COUNT = 99

/** ASCII apostrophe and the typographic right single quote (U+2019). */
private val WORD_JOINERS = setOf('\''.code, '’'.code)
