package com.baklavapp.reachlauncher.feature.allapps

import android.icu.text.AlphabeticIndex
import java.util.Locale

/*
 * Locale-aware first-letter buckets for the All Apps word wall and its letter rail.
 *
 * Flutter analogy: what packages like `azlistview` or `alphabet_scroll_view` do by hand
 * (take `name[0].toUpperCase()`, strip accents with a lookup table, throw the rest into
 * "#") — except Android ships the real thing: ICU's `AlphabeticIndex`, the same class the
 * system Contacts app uses for its fast-scroll letters. It knows, per locale, which letters
 * an alphabet has and which characters fold into them:
 * - English: "Ödeme" files under O, "Évian" under E (accents fold);
 * - Turkish: Ç, I, İ, Ö, Ş, Ü are letters of their own ("ılık" → I, "İnstagram" → İ);
 * - digits, symbols and other scripts ("7-Zip", "Ελληνικά" in an English index) fall into
 *   the index's underflow/overflow buckets, which Reach shows as one "#" group at the end.
 */

/**
 * The letters of one locale's alphabetic index and a way to file a name under one of them.
 *
 * Why ICU and not `label.first().uppercase()`: a plain first character puts "Ödeme" in its
 * own "Ö" group in English (where users expect it under O), and can't tell that Turkish has
 * both I and İ. `android.icu.text.AlphabeticIndex` (API 24+) encodes these rules per locale.
 *
 * Latin A–Z labels are always added (`addLabels(Locale.ENGLISH)`): most app names are Latin
 * even in a Turkish, Greek or Japanese UI, so "WhatsApp" gets a W bucket in Turkish (whose
 * alphabet has no W) and doesn't vanish into "#".
 *
 * Building the index costs a few milliseconds; build one per locale off the main thread
 * (the ViewModel does). The built `ImmutableIndex` is thread-safe.
 *
 * Within a bucket, names keep the order of the input list — the catalog is already sorted
 * with the locale's `Collator` (spec §3), so [group] never re-sorts.
 */
class LetterIndex(
    locale: Locale,
) {
    private val index: AlphabeticIndex.ImmutableIndex<Any> =
        AlphabeticIndex<Any>(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()

    /**
     * The rail's letters in alphabet order, ending with [OTHER] ("#"). Includes letters that
     * have no apps; the rail shows those dimmed and skips them while scrubbing.
     */
    val letters: List<String> =
        buildList {
            for (bucket in index) {
                if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) add(bucket.label)
            }
            add(OTHER)
        }

    /** The letter [name] is filed under: one of [letters]. */
    fun letterFor(name: String): String {
        val bucket = index.getBucket(index.getBucketIndex(name.trim()))
        return if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) bucket.label else OTHER
    }

    /**
     * Groups [items] by the letter of [label], in [letters] order; empty letters are left
     * out. Items keep their input order inside a group.
     */
    fun <T> group(
        items: List<T>,
        label: (T) -> String,
    ): List<Pair<String, List<T>>> {
        val byLetter = items.groupBy { letterFor(label(it)) }
        return letters.mapNotNull { letter -> byLetter[letter]?.let { letter to it } }
    }

    companion object {
        /** The one bucket for digits, symbols and names in scripts the index doesn't cover. */
        const val OTHER = "#"
    }
}
