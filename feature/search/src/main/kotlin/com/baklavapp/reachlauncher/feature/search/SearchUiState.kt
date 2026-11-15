package com.baklavapp.reachlauncher.feature.search

import androidx.compose.runtime.Immutable

/**
 * Everything the search screen draws, as one immutable snapshot.
 *
 * @property query the (raw) query these [results] were ranked for. The text field itself
 *   holds its own text in the UI so typing is never delayed by ranking (see `SearchRoute`).
 * @property results rows in display order; the first is the highlighted "ENTER ↵" row.
 *   With a blank query these are the suggestions.
 * @property expandedContactId the contact whose call / message / card pills are open.
 * @property showContactsPrompt show the one "Include contacts" row (permission not
 *   granted, not declined in this session, and at least two letters typed).
 *
 * Flutter analogy: the immutable state class a Cubit emits (`copyWith`/Freezed style).
 */
@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val expandedContactId: Long? = null,
    val showContactsPrompt: Boolean = false,
) {
    /** True while showing empty-query suggestions rather than matches. */
    val isSuggestions: Boolean get() = query.isBlank()
}

/**
 * One-off effects for `SearchRoute`, delivered exactly once.
 *
 * Flutter analogy: what a `BlocListener` reacts to (navigation, snack-style messages).
 */
sealed interface SearchEvent {
    /** Something was launched or applied: close search (the route calls `onClose`). */
    data object Close : SearchEvent

    /** "Appearance settings" was chosen (the route calls `onOpenAppearance`). */
    data object OpenAppearance : SearchEvent

    /** [label] couldn't be opened (uninstalled, disabled, refused by the system). */
    data class LaunchFailed(
        val label: String,
    ) : SearchEvent

    /** No installed app handles the hand-off (no browser for web search, no dialer…). */
    data object NoHandler : SearchEvent
}

/** The contact chooser's options (spec §6.6: "tap → call/message chooser"). */
enum class ContactAction {
    CALL,
    MESSAGE,
    VIEW,
}
