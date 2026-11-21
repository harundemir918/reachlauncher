package com.baklavapp.reachlauncher.feature.search

import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.TextFolding
import com.baklavapp.reachlauncher.core.model.ThemeMode
import kotlin.math.ln
import kotlin.math.min

/**
 * Everything search ranks over, as one immutable snapshot.
 *
 * @property apps the full catalog (hidden apps included — the ranker drops them).
 * @property contacts contacts the provider returned for (roughly) the current query.
 * @property predictions the empty-query suggestions, best first.
 */
data class SearchCorpus(
    val apps: List<CatalogApp> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val predictions: List<Prediction> = emptyList(),
)

/**
 * Turns a query and a [SearchCorpus] into the result rows — pure Kotlin, so every rule is
 * unit-tested on the JVM without Android (`SearchRankerTest`).
 *
 * **Matching** compares [TextFolding]-folded text (case, accents and Turkish dotted/dotless
 * i ignored: "ins" finds "İnstagram", "sar" finds "Şarkı"), with every run of non-letters
 * collapsed to one space so "paper ink" finds "Paper & Ink". A label matches as:
 * - [MatchTier.LABEL_PREFIX] — the label starts with the query ("spo" → Spotify);
 * - [MatchTier.WORD_PREFIX] — another word starts with it ("maps" → Google Maps);
 * - [MatchTier.SUBSTRING] — it appears anywhere ("tify" → Spotify).
 *
 * **Scoring.** A word-prefix match always beats a substring match (spec §6.6). Launch
 * frequency adds a boost of at most [FREQUENCY_WEIGHT] (logarithmic, saturating at
 * [FREQUENCY_SATURATION] launches) — smaller than the gap between the two tiers, so it
 * reorders matches of the same quality and lets a much-used app whose *second* word
 * matches overtake a never-used app whose first word does, but never lifts a substring
 * match over a word-prefix one. Ties go to apps, then contacts and commands, then
 * the catalog's (alphabetical) order.
 *
 * **Sources and limits:** at most [MAX_RESULTS] rows, with at most [MAX_CONTACTS] contacts
 * so apps are never crowded out, and then always the "Search the web" row last. Hidden apps
 * never appear. App shortcuts are not searched (docs/DECISIONS.md D76). Commands need at least
 * [MIN_COMMAND_LENGTH] typed letters and match word prefixes only, so one stray letter
 * doesn't fill the list with "Switch to …" rows.
 *
 * **Empty query:** the first [SUGGESTION_COUNT] predictions, no web row.
 *
 * Commands match English *and* Turkish keywords whatever the UI language ([CommandKeywords]):
 * a bilingual user can type either, and keywords don't depend on Android resources.
 */
object SearchRanker {
    /** Rows before the web fallback. */
    const val MAX_RESULTS = 6

    /** Empty-query suggestions (spec §6.6). */
    const val SUGGESTION_COUNT = 4

    /** Commands appear from this many typed letters/digits on (spec: palettes need ≥ 2). */
    const val MIN_COMMAND_LENGTH = 2

    /** At most this many contact rows. */
    const val MAX_CONTACTS = 3

    /** Largest frequency boost; below the 1.0 gap between word-prefix and substring tiers. */
    const val FREQUENCY_WEIGHT = 0.5

    /** Launch count at which the boost reaches [FREQUENCY_WEIGHT]. */
    const val FREQUENCY_SATURATION = 50

    /** How well a label matches a query. Order matters: later entries are better. */
    enum class MatchTier(
        internal val score: Double,
    ) {
        NONE(0.0),
        SUBSTRING(1.0),
        WORD_PREFIX(2.0),
        LABEL_PREFIX(2.25),
    }

    /**
     * Folds [text] for matching: [TextFolding] plus collapsing every run of characters that
     * aren't letters or digits into a single space (trimmed).
     */
    fun normalize(text: String): String =
        TextFolding
            .fold(text)
            .replace(NON_WORD, " ")
            .trim()

    /** How [label] matches the already-[normalize]d [query]. */
    fun matchTier(
        label: String,
        query: String,
    ): MatchTier {
        if (query.isEmpty()) return MatchTier.NONE
        val text = normalize(label)
        return when {
            text.startsWith(query) -> MatchTier.LABEL_PREFIX
            text.contains(" $query") -> MatchTier.WORD_PREFIX
            text.contains(query) -> MatchTier.SUBSTRING
            else -> MatchTier.NONE
        }
    }

    /** The frequency boost for [launchCount] launches (0 … [FREQUENCY_WEIGHT]). */
    fun frequencyBoost(launchCount: Int): Double {
        if (launchCount <= 0) return 0.0
        return FREQUENCY_WEIGHT * min(1.0, ln(1.0 + launchCount) / ln(1.0 + FREQUENCY_SATURATION))
    }

    /** The rows for [query] over [corpus]; see the object KDoc for every rule. */
    fun rank(
        query: String,
        corpus: SearchCorpus,
    ): List<SearchResult> {
        val q = normalize(query)
        if (q.isEmpty()) return suggestions(corpus)

        val visibleApps = corpus.apps.filterNot { it.isHidden }
        val candidates = mutableListOf<Candidate>()
        visibleApps.forEach { app ->
            score(app.label, q, app.launchCount)?.let { candidates += Candidate(SearchResult.AppItem(app), it, KIND_APP) }
        }
        corpus.contacts
            .distinctBy { it.id }
            .mapNotNull { contact ->
                score(contact.displayName, q, 0)?.let { Candidate(SearchResult.ContactItem(contact), it, KIND_CONTACT) }
            }.sortedByDescending { it.score }
            .take(MAX_CONTACTS)
            .let(candidates::addAll)
        if (q.count { it != ' ' } >= MIN_COMMAND_LENGTH) {
            CommandKeywords.all.forEach { (command, keywords) ->
                val tier = keywords.maxOf { matchTier(it, q) }
                if (tier >= MatchTier.WORD_PREFIX) candidates += Candidate(SearchResult.CommandItem(command), tier.score, KIND_COMMAND)
            }
        }
        // sortedWith is stable: equal scores and kinds keep the catalog's alphabetical order.
        val ranked =
            candidates
                .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.kind })
                .take(MAX_RESULTS)
                .map { it.result }
        return ranked + SearchResult.WebSearch(query.trim())
    }

    private fun suggestions(corpus: SearchCorpus): List<SearchResult> =
        corpus.predictions
            .map { it.app }
            .filterNot { it.isHidden }
            .distinctBy { it.key }
            .take(SUGGESTION_COUNT)
            .map(SearchResult::AppItem)

    private fun score(
        label: String,
        query: String,
        launchCount: Int,
    ): Double? {
        val tier = matchTier(label, query)
        return if (tier == MatchTier.NONE) null else tier.score + frequencyBoost(launchCount)
    }

    private class Candidate(
        val result: SearchResult,
        val score: Double,
        val kind: Int,
    )

    private const val KIND_APP = 0
    private const val KIND_CONTACT = 1
    private const val KIND_COMMAND = 2

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}

/**
 * The words each command answers to, in English and Turkish. Matched by word prefix, so
 * "dark" and "koyu" both find "Switch to dark theme", and "tema" finds both theme commands
 * and Appearance settings.
 *
 * Kept in code, not in `strings.xml`, on purpose: resources only expose the *current*
 * language, but both languages should work at once. The display text of every command is
 * still a localised string resource (and the palette names come from `PaletteNames.kt` in
 * `:core:designsystem`); keep the palette keywords in sync with those names.
 */
object CommandKeywords {
    /** Keywords per palette: its English and Turkish display names. */
    val palettes: Map<PaletteId, List<String>> =
        mapOf(
            PaletteId.LIME to listOf("Lime", "Limon"),
            PaletteId.PAPER_INK to listOf("Paper & Ink", "Kâğıt ve Mürekkep"),
            PaletteId.NIGHT_TIDE to listOf("Night Tide", "Gece Gelgiti"),
            PaletteId.TERRACOTTA to listOf("Terracotta", "Terakota"),
            PaletteId.FOREST to listOf("Forest", "Orman"),
            PaletteId.DUSK to listOf("Dusk", "Alacakaranlık"),
            PaletteId.HIGH_CONTRAST to listOf("High Contrast", "Yüksek Kontrast"),
        )

    /** "Switch to dark theme". */
    val dark: List<String> =
        listOf("dark theme", "dark mode", "night mode", "koyu tema", "karanlık tema", "karanlık mod", "gece modu")

    /** "Switch to light theme". */
    val light: List<String> =
        listOf("light theme", "light mode", "day mode", "açık tema", "aydınlık tema", "gündüz modu")

    /** "Appearance settings" (spec: "appearance", "theme", "palette"). */
    val appearance: List<String> =
        listOf("appearance", "theme", "palette", "colours", "colors", "görünüm", "tema", "palet", "renkler")

    /** Every command with its keywords, in display order for equally good matches. */
    val all: List<Pair<SearchCommand, List<String>>> =
        palettes.map { (id, words) -> SearchCommand.SwitchPalette(id) to words } +
            listOf(
                SearchCommand.SwitchTheme(ThemeMode.DARK) to dark,
                SearchCommand.SwitchTheme(ThemeMode.LIGHT) to light,
                SearchCommand.OpenAppearance to appearance,
            )
}
