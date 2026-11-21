package com.baklavapp.reachlauncher.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.di.DefaultDispatcher
import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.data.source.ContactsSource
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SearchViewModel — "type to launch": query in, ranked rows out, plus the actions behind
 * every row.
 *
 * Flutter analogy: a search Cubit.
 * - [onQueryChange] ≈ `cubit.queryChanged(text)`; [uiState] ≈ the Cubit's state stream
 *   (`StateFlow` always has a current value, like `Cubit.state`).
 * - Contacts are looked up with a short **debounce** — the same idea as rxdart's
 *   `debounceTime(150.ms)` + `switchMap`: the contacts provider is another process, so we
 *   wait until typing pauses and cancel a lookup that's overtaken (`mapLatest`). Apps
 *   and commands are ranked on every keystroke: they're already in memory.
 * - [events] ≈ a `BlocListener`'s one-off effects (close search, open Appearance, a
 *   "couldn't open" message), sent through a `Channel` so each is delivered exactly once.
 *
 * Ranking itself is the pure [SearchRanker]; this class only gathers its inputs (`combine`
 * ≈ `Rx.combineLatest`) on [defaultDispatcher] and runs actions. Every app
 * launch goes through the shared [LaunchApp] use case, which records history only on a real
 * launch (D32, D34).
 *
 * Lifetime: `hiltViewModel()` in a single-Activity app without Navigation scopes this
 * ViewModel to the Activity, so it outlives one opening of Search. [onOpened] and
 * [onClosed] reset the per-opening state (query, expanded contact).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        catalogRepository: AppCatalogRepository,
        predictionRepository: PredictionRepository,
        private val contactsSource: ContactsSource,
        private val settingsRepository: SettingsRepository,
        private val appLauncher: AppLauncher,
        private val launchApp: LaunchApp,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val expandedContactId = MutableStateFlow<Long?>(null)
        private val contactsGranted = MutableStateFlow(contactsSource.isPermissionGranted())

        // Process-wide "the user declined": the prompt is never shown again this session
        // (spec §7: explain, never nag). A permanently denied permission ends up here too,
        // because the system then answers the request with "denied" at once.
        private val contactsPromptDeclined = MutableStateFlow(false)

        private val eventChannel = Channel<SearchEvent>(Channel.BUFFERED)

        /** One-off effects; collect once while the UI is at least STARTED. */
        val events: Flow<SearchEvent> = eventChannel.receiveAsFlow()

        /**
         * Contacts for the current query: debounced, latest-wins, and never queried without
         * permission. Starts with an empty list so the first results don't wait for it.
         */
        private val contacts: Flow<List<Contact>> =
            combine(query, contactsGranted) { q, granted -> ContactLookup(q.trim(), granted) }
                .distinctUntilChanged()
                .debounce { if (it.shouldQuery) CONTACT_DEBOUNCE_MILLIS else 0L }
                .mapLatest { lookup -> if (lookup.shouldQuery) searchContacts(lookup.text) else emptyList() }
                .onStart { emit(emptyList()) }

        // The last inputs the ranker saw, so Enter can rank the *current* query at once even
        // if the combine below hasn't caught up with the latest keystroke yet.
        @Volatile
        private var lastCorpus = SearchCorpus()

        private val corpus: Flow<SearchCorpus> =
            combine(
                catalogRepository.apps,
                contacts,
                predictionRepository.predictions(SearchRanker.SUGGESTION_COUNT),
            ) { apps, contacts, predictions ->
                SearchCorpus(apps, contacts, predictions)
            }.onEach { lastCorpus = it }

        /** The rows to show; ranks on [defaultDispatcher], never on the main thread. */
        val uiState: StateFlow<SearchUiState> =
            combine(query, corpus, expandedContactId, contactsGranted, contactsPromptDeclined) {
                q,
                corpus,
                expanded,
                granted,
                declined,
                ->
                SearchUiState(
                    query = q,
                    results = SearchRanker.rank(q, corpus),
                    expandedContactId = expanded,
                    showContactsPrompt = !granted && !declined && SearchRanker.normalize(q).length >= PROMPT_MIN_LENGTH,
                )
            }.flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

        /**
         * Search was opened. Starts from [initialQuery] — empty on a fresh opening, the
         * restored text after the Activity was recreated (rotation, dark-mode switch) — and
         * re-checks contacts access.
         */
        fun onOpened(initialQuery: String = "") {
            reset()
            query.value = initialQuery
            onResume()
        }

        /** Re-checks contacts access, which the user may have granted in system settings. */
        fun onResume() {
            contactsGranted.value = contactsSource.isPermissionGranted()
        }

        /** Search left the screen (closed, Home pressed, or Back): forget this opening's query. */
        fun onClosed() = reset()

        private fun reset() {
            query.value = ""
            expandedContactId.value = null
        }

        /** The text field changed. Closes an open contact chooser. */
        fun onQueryChange(text: String) {
            query.value = text
            expandedContactId.value = null
        }

        /**
         * Enter / the keyboard's action key: activates the top row, exactly as tapping it.
         * Ranks the current query directly when [uiState] is a keystroke behind.
         */
        fun onSubmit(origin: LaunchOrigin?) {
            val current = query.value
            val state = uiState.value
            val results = if (state.query == current) state.results else SearchRanker.rank(current, lastCorpus)
            results.firstOrNull()?.let { onResultClick(it, origin) }
        }

        /** A row was tapped (or chosen with Enter). */
        fun onResultClick(
            result: SearchResult,
            origin: LaunchOrigin?,
        ) {
            when (result) {
                is SearchResult.AppItem -> {
                    onLaunchResult(launchApp.app(result.app.key, origin), result.app.label)
                }

                is SearchResult.ContactItem -> {
                    val id = result.contact.id
                    expandedContactId.value = if (expandedContactId.value == id) null else id
                }

                is SearchResult.CommandItem -> {
                    run(result.command)
                }

                is SearchResult.WebSearch -> {
                    hand(ExternalAction.WebSearch(result.query))
                }
            }
        }

        /** One of the contact chooser's pills. Call/Message need a number; the card always works. */
        fun onContactAction(
            contact: Contact,
            action: ContactAction,
        ) {
            val phone = contact.phoneNumber
            val external =
                when (action) {
                    ContactAction.CALL -> phone?.let(ExternalAction::Dial)
                    ContactAction.MESSAGE -> phone?.let(ExternalAction::Message)
                    ContactAction.VIEW -> ExternalAction.ViewContact(contact.id, contact.lookupKey)
                } ?: return
            hand(external)
        }

        /**
         * The answer to the `READ_CONTACTS` request (or a re-check). A denial hides the prompt
         * for the rest of the session; a grant makes contacts appear for the current query.
         */
        fun onContactsPermissionResult(granted: Boolean) {
            contactsGranted.value = granted || contactsSource.isPermissionGranted()
            if (!contactsGranted.value) contactsPromptDeclined.value = true
        }

        private fun onLaunchResult(
            result: LaunchResult,
            label: String,
        ) {
            when (result) {
                LaunchResult.LAUNCHED -> eventChannel.trySend(SearchEvent.Close)

                // A paused work profile is being resumed: stay, the user taps again (D32).
                LaunchResult.RESUMING_PROFILE -> Unit

                LaunchResult.FAILED -> eventChannel.trySend(SearchEvent.LaunchFailed(label))
            }
        }

        private fun run(command: SearchCommand) {
            when (command) {
                is SearchCommand.SwitchPalette -> {
                    applySetting { settingsRepository.setPalette(command.palette) }
                }

                is SearchCommand.SwitchTheme -> {
                    applySetting { settingsRepository.setThemeMode(command.mode) }
                }

                SearchCommand.OpenAppearance -> {
                    eventChannel.trySend(SearchEvent.OpenAppearance)
                }
            }
        }

        /** Applies a setting, then closes search so the user sees Home in its new look. */
        private fun applySetting(block: suspend () -> Unit) {
            viewModelScope.launch {
                bestEffort(Unit) { block() }
                eventChannel.trySend(SearchEvent.Close)
            }
        }

        private fun hand(action: ExternalAction) {
            eventChannel.trySend(if (appLauncher.perform(action)) SearchEvent.Close else SearchEvent.NoHandler)
        }

        private suspend fun searchContacts(text: String): List<Contact> =
            bestEffort(emptyList()) { contactsSource.search(text, CONTACT_FETCH_LIMIT) }

        /** The contacts query's inputs; [shouldQuery] decides whether the provider is asked at all. */
        private data class ContactLookup(
            val text: String,
            val granted: Boolean,
        ) {
            val shouldQuery: Boolean get() = granted && text.isNotEmpty()
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** Long enough to skip intermediate keystrokes, short enough to feel instant. */
            const val CONTACT_DEBOUNCE_MILLIS = 150L

            /** More than the ranker shows, so its own folding-based ranking has a choice. */
            const val CONTACT_FETCH_LIMIT = 12

            /** The contacts prompt appears from two letters on (one letter is too early to ask). */
            const val PROMPT_MIN_LENGTH = 2

            /**
             * Runs [block], returning [fallback] on any failure except cancellation: a broken
             * database or provider must cost one result, never crash the launcher (D36).
             */
            suspend fun <T> bestEffort(
                fallback: T,
                block: suspend () -> T,
            ): T =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallback
                }
        }
    }
