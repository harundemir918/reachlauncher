package com.baklavapp.reachlauncher.feature.search

import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The pure ranking rules of [SearchRanker]. Plain JVM tests: no Android, no coroutines.
 *
 * Flutter analogy: plain `test()` cases for a pure Dart function.
 */
class SearchRankerTest {
    private fun rank(
        query: String,
        corpus: SearchCorpus,
    ) = SearchRanker.rank(query, corpus)

    private fun labels(results: List<SearchResult>): List<String> =
        results.map {
            when (it) {
                is SearchResult.AppItem -> it.app.label
                is SearchResult.ContactItem -> it.contact.displayName
                is SearchResult.CommandItem -> it.command.toString()
                is SearchResult.WebSearch -> "web:${it.query}"
            }
        }

    private fun commands(results: List<SearchResult>) = results.filterIsInstance<SearchResult.CommandItem>().map { it.command }

    @Test
    fun `word prefix beats substring`() {
        val corpus = SearchCorpus(apps = listOf(app("Gmail"), app("Google Maps"), app("Amazon")))
        // "ma": "Google Maps" (word prefix) before "Gmail" and "Amazon" (substring).
        val result = labels(rank("ma", corpus))
        assertThat(result.first()).isEqualTo("Google Maps")
        assertThat(result).containsAtLeast("Gmail", "Amazon")
    }

    @Test
    fun `label prefix beats a later word prefix when usage is equal`() {
        val corpus = SearchCorpus(apps = listOf(app("Google Maps"), app("Maps Lite")))
        assertThat(labels(rank("maps", corpus)).take(2)).containsExactly("Maps Lite", "Google Maps").inOrder()
    }

    @Test
    fun `launch frequency reorders matches of the same quality`() {
        val corpus = SearchCorpus(apps = listOf(app("Signal"), app("Spotify", launchCount = 30), app("Slack", launchCount = 3)))
        assertThat(labels(rank("s", corpus)).take(3)).containsExactly("Spotify", "Slack", "Signal").inOrder()
    }

    @Test
    fun `frequency never lifts a substring match over a word prefix`() {
        val corpus = SearchCorpus(apps = listOf(app("Notes"), app("Piano", launchCount = 10_000)))
        // "no" is a prefix of Notes and only a substring of Piano.
        assertThat(labels(rank("no", corpus)).take(2)).containsExactly("Notes", "Piano").inOrder()
        assertThat(SearchRanker.frequencyBoost(10_000)).isLessThan(1.0)
    }

    @Test
    fun `Turkish folding matches dotted capital I and s-cedilla`() {
        val corpus = SearchCorpus(apps = listOf(app("İnstagram"), app("Şarkı Bul"), app("Kırmızı")))
        assertThat(labels(rank("ins", corpus)).first()).isEqualTo("İnstagram")
        assertThat(labels(rank("sar", corpus)).first()).isEqualTo("Şarkı Bul")
        assertThat(labels(rank("KIR", corpus)).first()).isEqualTo("Kırmızı")
        assertThat(labels(rank("bul", corpus)).first()).isEqualTo("Şarkı Bul")
    }

    @Test
    fun `punctuation is ignored when matching`() {
        val corpus = SearchCorpus(apps = listOf(app("Paper & Ink Notes")))
        assertThat(labels(rank("paper ink", corpus)).first()).isEqualTo("Paper & Ink Notes")
    }

    @Test
    fun `palette command needs at least two letters`() {
        assertThat(commands(rank("f", SearchCorpus()))).isEmpty()
        assertThat(commands(rank("fo", SearchCorpus()))).containsExactly(SearchCommand.SwitchPalette(PaletteId.FOREST))
        // Turkish palette names work too, whatever the UI language.
        assertThat(commands(rank("orm", SearchCorpus()))).containsExactly(SearchCommand.SwitchPalette(PaletteId.FOREST))
        assertThat(commands(rank("gece", SearchCorpus()))).contains(SearchCommand.SwitchPalette(PaletteId.NIGHT_TIDE))
        // A later word of the name: "Paper & Ink".
        assertThat(commands(rank("ink", SearchCorpus()))).containsExactly(SearchCommand.SwitchPalette(PaletteId.PAPER_INK))
    }

    @Test
    fun `commands match word prefixes only, not substrings`() {
        // "ark" is inside "dark" but no command word starts with it.
        assertThat(commands(rank("ark", SearchCorpus()))).isEmpty()
    }

    @Test
    fun `theme commands match English and Turkish keywords`() {
        val dark = SearchCommand.SwitchTheme(ThemeMode.DARK)
        val light = SearchCommand.SwitchTheme(ThemeMode.LIGHT)
        assertThat(commands(rank("dark", SearchCorpus()))).containsExactly(dark)
        assertThat(commands(rank("koyu", SearchCorpus()))).containsExactly(dark)
        assertThat(commands(rank("light", SearchCorpus()))).containsExactly(light)
        assertThat(commands(rank("açık", SearchCorpus()))).containsExactly(light)
        // Typed without the Turkish letters.
        assertThat(commands(rank("acik", SearchCorpus()))).containsExactly(light)
    }

    @Test
    fun `appearance command matches appearance, theme and palette in both languages`() {
        listOf("appearance", "theme", "palette", "görünüm", "gorunum", "tema", "palet").forEach { word ->
            assertThat(commands(rank(word, SearchCorpus()))).contains(SearchCommand.OpenAppearance)
        }
        // "theme"/"tema" also offer both theme switches.
        assertThat(commands(rank("tema", SearchCorpus())))
            .containsAtLeast(SearchCommand.SwitchTheme(ThemeMode.DARK), SearchCommand.SwitchTheme(ThemeMode.LIGHT))
    }

    @Test
    fun `apps come before commands on an equally good match`() {
        val corpus = SearchCorpus(apps = listOf(app("Forecast")))
        val result = rank("fo", corpus)
        assertThat(result[0]).isInstanceOf(SearchResult.AppItem::class.java)
        assertThat(result[1]).isEqualTo(SearchResult.CommandItem(SearchCommand.SwitchPalette(PaletteId.FOREST)))
    }

    @Test
    fun `web fallback is always last and carries the trimmed query`() {
        val corpus = SearchCorpus(apps = listOf(app("Spotify")))
        val withMatch = rank("  spo ", corpus)
        assertThat(withMatch.last()).isEqualTo(SearchResult.WebSearch("spo"))
        val noMatch = rank("zzz", corpus)
        assertThat(noMatch).containsExactly(SearchResult.WebSearch("zzz"))
    }

    @Test
    fun `at most six rows plus the web fallback`() {
        val corpus = SearchCorpus(apps = (1..20).map { app("App $it") })
        val result = rank("app", corpus)
        assertThat(result).hasSize(SearchRanker.MAX_RESULTS + 1)
        assertThat(result.last()).isInstanceOf(SearchResult.WebSearch::class.java)
    }

    @Test
    fun `hidden apps are excluded`() {
        val corpus = SearchCorpus(apps = listOf(app("Secret Chat", hidden = true), app("Signal")))
        val result = labels(rank("s", corpus))
        assertThat(result).doesNotContain("Secret Chat")
        assertThat(result).contains("Signal")
    }

    @Test
    fun `contacts are matched by folded name and capped`() {
        val corpus =
            SearchCorpus(
                contacts = listOf(contact("Ayşe Yılmaz"), contact("Ayla"), contact("Aydın"), contact("Ayten"), contact("Zeynep")),
            )
        val contacts = rank("ay", corpus).filterIsInstance<SearchResult.ContactItem>()
        assertThat(contacts).hasSize(SearchRanker.MAX_CONTACTS)
        // "yilmaz" (no Turkish letters) still finds "Ayşe Yılmaz"; a stale non-match is dropped.
        assertThat(labels(rank("yilmaz", corpus))).containsExactly("Ayşe Yılmaz", "web:yilmaz").inOrder()
    }

    @Test
    fun `empty query shows four predictions without hidden apps or web row`() {
        val predictions =
            listOf(app("A"), app("B", hidden = true), app("C"), app("D"), app("E"), app("F"))
                .map { Prediction(it, PredictionReason.Recent, 1.0) }
        val result = rank("   ", SearchCorpus(predictions = predictions))
        assertThat(labels(result)).containsExactly("A", "C", "D", "E").inOrder()
    }

    @Test
    fun `paused profile apps are kept and flagged`() {
        val corpus = SearchCorpus(apps = listOf(app("Mail", paused = true)))
        val item = rank("mail", corpus).first() as SearchResult.AppItem
        assertThat(item.app.isProfilePaused).isTrue()
    }
}
