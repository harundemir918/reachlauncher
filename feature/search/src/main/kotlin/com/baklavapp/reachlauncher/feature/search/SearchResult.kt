package com.baklavapp.reachlauncher.feature.search

import androidx.compose.runtime.Immutable
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode

/*
 * What a search row can be. A sealed hierarchy, so `when (result)` in the ViewModel and the
 * screen must handle every kind — the compiler complains when a new kind is added and a
 * branch is missing.
 *
 * Flutter analogy: a Freezed union (`@freezed sealed class SearchResult` with
 * `.app(...)`, `.shortcut(...)`, …) matched with `switch` / `when`.
 */

/** One row in the search results. */
@Immutable
sealed interface SearchResult {
    /** Stable identity for the lazy list (keeps scroll position and animations right). */
    val key: String

    /** An installed app (also used for the four empty-query suggestions). */
    data class AppItem(
        val app: CatalogApp,
    ) : SearchResult {
        override val key: String get() = "app:${app.key.packageName}/${app.key.activityName}#${app.key.userSerial}"
    }

    /**
     * An app shortcut ("New message"). [appKey] is the catalog app it belongs to, so a
     * launch is recorded for that app and paused-profile state can be shown.
     */
    data class ShortcutItem(
        val shortcut: AppShortcut,
        val appKey: AppKey,
        val appLabel: String,
        val isProfilePaused: Boolean,
    ) : SearchResult {
        override val key: String get() = "shortcut:${shortcut.app.packageName}#${shortcut.app.userSerial}/${shortcut.id}"
    }

    /** A contact (only with `READ_CONTACTS`); tapping it offers call / message / card. */
    data class ContactItem(
        val contact: Contact,
    ) : SearchResult {
        override val key: String get() = "contact:${contact.id}"
    }

    /** A Reach command such as "Switch to Forest". */
    data class CommandItem(
        val command: SearchCommand,
    ) : SearchResult {
        override val key: String get() = "command:$command"
    }

    /** The always-last "Search the web for '<query>'" row. */
    data class WebSearch(
        val query: String,
    ) : SearchResult {
        override val key: String get() = "web"
    }
}

/** Commands search can run without leaving Reach (spec §6.6). */
@Immutable
sealed interface SearchCommand {
    /** "Switch to <Palette>". */
    data class SwitchPalette(
        val palette: PaletteId,
    ) : SearchCommand

    /** "Switch to dark/light theme". [mode] is [ThemeMode.DARK] or [ThemeMode.LIGHT]. */
    data class SwitchTheme(
        val mode: ThemeMode,
    ) : SearchCommand

    /** "Appearance settings": opens the Appearance sheet. */
    data object OpenAppearance : SearchCommand
}
