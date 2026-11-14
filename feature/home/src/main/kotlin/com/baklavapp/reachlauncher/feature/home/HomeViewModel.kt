package com.baklavapp.reachlauncher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.data.source.HomeRoleSource
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.data.source.ShortcutSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.ReachIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * HomeViewModel — the state machine and data hub of Reach's one Home surface.
 *
 * Flutter analogy: this is your BLoC/Cubit.
 * - [uiState] (`StateFlow<HomeUiState>`) ≈ the BLoC's state stream. A `StateFlow` always
 *   has a current value, like a `BehaviorSubject` / `Cubit.state`. In Compose,
 *   `collectAsStateWithLifecycle()` ≈ `BlocBuilder`.
 * - [events] ≈ what a `BlocListener` reacts to: one-off effects delivered exactly once.
 * - `onIntentSelected(…)`, `onBack()`… ≈ `bloc.add(IntentTapped(…))` / Cubit methods.
 * - `viewModelScope` ≈ the BLoC's lifetime: coroutines launched in it are cancelled when the
 *   ViewModel is cleared (`close()`).
 *
 * The ViewModel outlives configuration changes (rotation, dark mode), so nothing here is
 * reloaded when the Activity reconfigures. It holds no Android objects: every system
 * integration arrives through the `:core:data` interfaces, which is why
 * `HomeViewModelTest` runs on the plain JVM with fakes.
 *
 * State is built reactively: `combine` merges the repository flows (≈ `Rx.combineLatest`)
 * into one [HomeUiState] whenever any input changes, and `stateIn(WhileSubscribed(5 s))`
 * keeps the upstream alive for 5 s after the UI stops collecting — long enough to survive
 * a quick app switch without re-querying, short enough to release work when Home sits in
 * the background.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val catalogRepository: AppCatalogRepository,
        private val historyRepository: LaunchHistoryRepository,
        predictionRepository: PredictionRepository,
        settingsRepository: SettingsRepository,
        notificationCountSource: NotificationCountSource,
        private val shortcutSource: ShortcutSource,
        private val appLauncher: AppLauncher,
        private val homeRoleSource: HomeRoleSource,
        private val clock: Clock,
    ) : ViewModel() {
        private val surface = MutableStateFlow<HomeSurface>(HomeSurface.Home)

        // A Channel (not a SharedFlow) so an event sent while the UI isn't collecting (e.g.
        // HideKeyboard from onNewIntent before onResume) is buffered and delivered once.
        private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)

        /** One-off UI effects; collect them once, while the UI is at least STARTED. */
        val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

        private val shortcuts: Flow<List<AppShortcut>> =
            combine(shortcutSource.hasHostPermission, shortcutSource.shortcuts) { granted, list ->
                if (granted) list else emptyList()
            }

        private val catalog: Flow<CatalogSnapshot> =
            combine(catalogRepository.apps, notificationCountSource.countsByApp, shortcuts, ::CatalogSnapshot)

        /**
         * The single source of truth for Home. Its initial value renders immediately (six
         * intents with zero apps); the cached catalog follows within milliseconds.
         */
        val uiState: StateFlow<HomeUiState> =
            combine(
                surface,
                settingsRepository.settings,
                catalog,
                predictionRepository.predictions(NOW_ROW_SIZE),
            ) { surface, settings, catalog, predictions ->
                buildUiState(surface, settings, catalog, predictions)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

        /** Tap on an intent word, or on a chip in intent detail: show that intent's apps. */
        fun onIntentSelected(intent: ReachIntent) {
            surface.value = HomeSurface.IntentDetail(intent)
        }

        /** Tap on the search pill or pull-down on Home. */
        fun onSearchRequested() {
            surface.value = HomeSurface.Search
        }

        /** Swipe up on Home. */
        fun onAllAppsRequested() {
            surface.value = HomeSurface.AllApps
        }

        /** Long-press on the clock (or its TalkBack action). */
        fun onAppearanceRequested() {
            surface.value = HomeSurface.Appearance
        }

        /**
         * System Back: every sub-surface steps back to Home; on Home itself Back does
         * nothing (a launcher has nothing behind it — spec §4).
         */
        fun onBack() {
            surface.value = HomeSurface.Home
        }

        /**
         * The Home button was pressed (`MainActivity.onNewIntent`): return to Home from any
         * surface and close the keyboard, as every launcher does (spec §4).
         */
        fun onHomePressed() {
            surface.value = HomeSurface.Home
            eventChannel.trySend(HomeEvent.HideKeyboard)
        }

        /**
         * Call on every `ON_RESUME`: the user may have installed apps or changed the default
         * home app while Reach was in the background, and package broadcasts are not always
         * delivered to a stopped app. Both calls are cheap and safe to repeat.
         */
        fun onResume() {
            homeRoleSource.recheck()
            viewModelScope.launch { catalogRepository.refresh() }
        }

        /**
         * Launches [app], animating from [origin] (the tapped element in window pixels).
         *
         * On success the launch is recorded (the primary prediction signal, spec §6.2) and
         * the surface returns to Home, so Home — not the old detail list — greets the user
         * when they come back. On failure a short message is shown and the surface stays.
         *
         * A paused work profile is special (docs/DECISIONS.md D27): the launcher only asks
         * the system to resume the profile and does not start the app, so nothing is
         * recorded and the user stays where they are to tap again.
         */
        fun launch(
            app: CatalogApp,
            origin: LaunchOrigin?,
        ) {
            val launched = appLauncher.launchApp(app.key, origin)
            when {
                !launched -> eventChannel.trySend(HomeEvent.LaunchFailed(app.label))
                app.isProfilePaused -> Unit
                else -> onLaunched(app.key)
            }
        }

        /** Launches an app-shortcut pill; recorded against the app it belongs to. */
        fun launchShortcut(
            item: ShortcutItem,
            origin: LaunchOrigin?,
        ) {
            if (shortcutSource.launch(item.shortcut, origin)) {
                onLaunched(item.appKey)
            } else {
                eventChannel.trySend(HomeEvent.LaunchFailed(item.shortcut.label))
            }
        }

        private fun onLaunched(key: AppKey) {
            val now = clock.millis()
            viewModelScope.launch { historyRepository.recordLaunch(key, now) }
            surface.value = HomeSurface.Home
        }

        private companion object {
            const val NOW_ROW_SIZE = 3
            const val MAX_SHORTCUTS = 4
            const val STOP_TIMEOUT_MILLIS = 5_000L

            fun buildUiState(
                surface: HomeSurface,
                settings: AppearanceSettings,
                catalog: CatalogSnapshot,
                predictions: List<Prediction>,
            ): HomeUiState {
                val visible = catalog.apps.filterNot { it.isHidden }
                val byIntent = visible.groupBy { it.intent }
                val intents =
                    ReachIntent.entries.map { intent ->
                        val apps = byIntent[intent].orEmpty()
                        IntentSummary(intent, appCount = apps.size, unreadCount = unreadFor(apps, catalog.unread))
                    }
                val detail =
                    (surface as? HomeSurface.IntentDetail)?.let { detailFor(it.intent, byIntent[it.intent].orEmpty(), catalog) }
                return HomeUiState(
                    surface = surface,
                    thumbSide = settings.thumbSide,
                    themeMode = settings.themeMode,
                    palette = settings.palette,
                    intents = intents,
                    now = predictions.take(NOW_ROW_SIZE).map { NowItem(it.app, it.reason) },
                    detail = detail,
                )
            }

            /**
             * Sums unread counts over the *packages-in-profile* of [apps]. An app with two
             * launcher activities shares one notification count, so each [PackageUser] is
             * counted once; the personal and work copies of an app are counted separately.
             */
            fun unreadFor(
                apps: List<CatalogApp>,
                unread: Map<PackageUser, Int>,
            ): Int = apps.mapTo(mutableSetOf()) { it.key.packageUser }.sumOf { unread[it] ?: 0 }

            fun detailFor(
                intent: ReachIntent,
                apps: List<CatalogApp>,
                catalog: CatalogSnapshot,
            ): IntentDetailUi {
                // sortedByDescending is stable, so equal counts keep the catalog's
                // Collator (locale-aware alphabetical) order: frequency, then label.
                val sorted = apps.sortedByDescending { it.launchCount }
                val shortcutsByApp = catalog.shortcuts.groupBy { it.app }
                val shortcuts =
                    sorted
                        .distinctBy { it.key.packageUser }
                        .asSequence()
                        .flatMap { app -> shortcutsByApp[app.key.packageUser].orEmpty().map { ShortcutItem(it, app.key) } }
                        .take(MAX_SHORTCUTS)
                        .toList()
                return IntentDetailUi(
                    intent = intent,
                    apps = sorted.map { AppItem(it, catalog.unread[it.key.packageUser] ?: 0) },
                    shortcuts = shortcuts,
                )
            }
        }
    }

/** The catalog-side inputs of [HomeUiState], combined first to stay within `combine`'s typed arity. */
private data class CatalogSnapshot(
    val apps: List<CatalogApp>,
    val unread: Map<PackageUser, Int>,
    val shortcuts: List<AppShortcut>,
)
