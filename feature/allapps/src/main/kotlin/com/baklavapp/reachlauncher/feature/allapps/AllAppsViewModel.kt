package com.baklavapp.reachlauncher.feature.allapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.di.DefaultDispatcher
import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * AllAppsViewModel — the word wall's data and the long-press menu's actions.
 *
 * Flutter analogy: a Cubit. [uiState] ≈ its state stream (`collectAsStateWithLifecycle()`
 * ≈ `BlocBuilder`), [events] ≈ what a `BlocListener` reacts to, and the public functions ≈
 * Cubit methods. `combine` ≈ `Rx.combineLatest`.
 *
 * How state is built: the catalog, the letter index for the current locale and the thumb
 * side are combined into one [AllAppsUiState] on [defaultDispatcher] (grouping a few
 * hundred names with ICU is cheap, but never the main thread's job), then
 * `stateIn(WhileSubscribed(5 s))` keeps it alive across a quick app switch and releases the
 * work when All Apps has been gone for 5 s.
 *
 * Why the locale is pushed in by the route ([onLocaleChanged]) rather than read once: a
 * ViewModel survives configuration changes, including a language change, so a locale
 * captured at construction would keep a Turkish rail after switching to English.
 *
 * Launches go through the shared [LaunchApp] use case so All Apps counts launches exactly
 * like Home and Search (only real launches are recorded — D32, D34).
 */
@HiltViewModel
class AllAppsViewModel
    @Inject
    constructor(
        private val catalogRepository: AppCatalogRepository,
        settingsRepository: SettingsRepository,
        private val launchApp: LaunchApp,
        private val appLauncher: AppLauncher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val locale = MutableStateFlow(Locale.getDefault())

        // A Channel so an event sent while the UI isn't collecting is delivered once, later.
        private val eventChannel = Channel<AllAppsEvent>(Channel.BUFFERED)

        /** One-off effects ([AllAppsEvent]); collect once, while the UI is at least STARTED. */
        val events: Flow<AllAppsEvent> = eventChannel.receiveAsFlow()

        // Rebuilt only when the locale changes, not on every catalog emission.
        private val letterIndex: Flow<LetterIndex> = locale.map { LetterIndex(it) }

        private val thumbSide: Flow<ThumbSide> = settingsRepository.settings.map { it.thumbSide }.distinctUntilChanged()

        /** The single source of truth for the screen. Starts empty with [AllAppsUiState.loaded] = false. */
        val uiState: StateFlow<AllAppsUiState> =
            combine(catalogRepository.apps, letterIndex, thumbSide, ::buildUiState)
                .flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AllAppsUiState())

        /** The composition's locale; call whenever it changes (cheap when it didn't). */
        fun onLocaleChanged(newLocale: Locale) {
            locale.value = newLocale
        }

        /**
         * Tap on a name. Follows the launcher's live [LaunchResult]:
         * - LAUNCHED → [AllAppsEvent.Done], so the user finds Home (not All Apps) on return;
         * - RESUMING_PROFILE → stay: the system is resuming the work profile, tap again;
         * - FAILED → [AllAppsEvent.LaunchFailed] with the app's name.
         */
        fun launch(
            app: CatalogApp,
            origin: LaunchOrigin?,
        ) {
            when (launchApp.app(app.key, origin)) {
                LaunchResult.LAUNCHED -> eventChannel.trySend(AllAppsEvent.Done)
                LaunchResult.RESUMING_PROFILE -> Unit
                LaunchResult.FAILED -> eventChannel.trySend(AllAppsEvent.LaunchFailed(app.label))
            }
        }

        /** Menu "App info": the system's App info screen. */
        fun openAppInfo(app: CatalogApp) = appLauncher.openAppInfo(app.key)

        /** Menu "Move to intent…": the user's choice, which always beats auto-assignment (§6.1). */
        fun moveToIntent(
            app: CatalogApp,
            intent: ReachIntent,
        ) {
            viewModelScope.launch { bestEffort { catalogRepository.moveToIntent(app.key, intent) } }
        }

        /** Menu "Hide" / "Unhide". The catalog re-emits, moving the app between wall and Hidden group. */
        fun setHidden(
            app: CatalogApp,
            hidden: Boolean,
        ) {
            viewModelScope.launch { bestEffort { catalogRepository.setHidden(app.key, hidden) } }
        }

        /** Menu "Uninstall": the system shows its own confirmation (D26). */
        fun uninstall(app: CatalogApp) = appLauncher.requestUninstall(app.key)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /**
             * Runs [block] and drops any failure except cancellation: an exception escaping a
             * `viewModelScope` coroutine would crash the launcher (D36). A failed move/hide
             * simply leaves the wall as it was, which the user can see and retry.
             */
            suspend fun bestEffort(block: suspend () -> Unit) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Nothing to roll back: the catalog didn't change.
                }
            }
        }
    }

/**
 * Turns the catalog (Collator-sorted, hidden apps included) into the screen's state: hidden
 * apps move to their own group and out of the count, the rest are bucketed by [index].
 * Pure, so UI tests can build realistic states without a ViewModel.
 */
internal fun buildUiState(
    apps: List<CatalogApp>,
    index: LetterIndex,
    thumbSide: ThumbSide,
): AllAppsUiState {
    val (hidden, visible) = apps.partition { it.isHidden }
    val groups = index.group(visible) { it.label }.map { (letter, list) -> LetterGroup(letter, list) }
    val groupByLetter = groups.withIndex().associate { (i, group) -> group.letter to i }
    return AllAppsUiState(
        loaded = true,
        totalCount = visible.size,
        groups = groups,
        hidden = hidden,
        rail = index.letters.map { RailLetter(it, groupByLetter[it]) },
        thumbSide = thumbSide,
    )
}
