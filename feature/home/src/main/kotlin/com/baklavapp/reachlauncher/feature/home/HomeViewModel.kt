package com.baklavapp.reachlauncher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baklavapp.reachlauncher.core.data.di.DefaultDispatcher
import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.data.source.CalendarSource
import com.baklavapp.reachlauncher.core.data.source.HomeRoleSource
import com.baklavapp.reachlauncher.core.data.source.MediaSessionSource
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import com.baklavapp.reachlauncher.core.data.source.ShortcutSource
import com.baklavapp.reachlauncher.core.data.source.UsageStatsSource
import com.baklavapp.reachlauncher.core.data.source.WallpaperColorSource
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.SystemScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel — the state machine and data hub of Reach's one Home surface.
 *
 * Flutter analogy: this is your BLoC/Cubit.
 * - [uiState] (`StateFlow<HomeUiState>`) ≈ the BLoC's state stream. A `StateFlow` always
 *   has a current value, like a `BehaviorSubject` / `Cubit.state`. In Compose,
 *   `collectAsStateWithLifecycle()` ≈ `BlocBuilder`.
 * - [surface] is a second, tiny state stream: *which* screen is showing. It is kept out of
 *   [uiState] on purpose (see below).
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
 * Data state is built reactively: `combine` merges the repository flows (≈ `Rx.combineLatest`)
 * into one [HomeUiState] whenever any input changes — on [defaultDispatcher], so grouping
 * and sorting never run on the main thread — and `stateIn(WhileSubscribed(5 s))` keeps the
 * upstream alive for 5 s after the UI stops collecting: long enough to survive a quick app
 * switch without re-querying, short enough to release work when Home sits in the background.
 *
 * Why [surface] is separate: after those 5 s the `combine` is stopped, and a `stateIn`
 * value only changes when the *whole* combine re-emits. If the surface were one of its
 * inputs, pressing Home while Reach is in the background would update the surface, but the
 * first frame after resuming would still show the old intent detail or search. A plain
 * `MutableStateFlow` has no upstream to restart, so its value is always current. For the
 * same reason the intent details of *all six* intents are precomputed in [uiState]: the
 * screen never waits for a recombine to switch intents.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val catalogRepository: AppCatalogRepository,
        predictionRepository: PredictionRepository,
        private val settingsRepository: SettingsRepository,
        notificationCountSource: NotificationCountSource,
        shortcutSource: ShortcutSource,
        private val launchApp: LaunchApp,
        private val homeRoleSource: HomeRoleSource,
        private val mediaSessionSource: MediaSessionSource,
        calendarSource: CalendarSource,
        usageStatsSource: UsageStatsSource,
        private val appLauncher: AppLauncher,
        private val wallpaperColorSource: WallpaperColorSource,
        private val screenActionsSource: ScreenActionsSource,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _surface = MutableStateFlow<HomeSurface>(HomeSurface.Home)

        /**
         * The active surface. Always current, even while nobody collects (see the class
         * KDoc): `HomeRoute` reads it directly so Home is on the very first resumed frame.
         */
        val surface: StateFlow<HomeSurface> = _surface.asStateFlow()

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
         * The single source of truth for Home's *data*. Its initial value renders immediately
         * (six intents without counts, [HomeUiState.catalogLoaded] = false); the cached
         * catalog follows within milliseconds.
         */
        val uiState: StateFlow<HomeUiState> =
            combine(
                settingsRepository.settings,
                catalog,
                predictionRepository.predictions(NOW_ROW_SIZE),
            ) { settings, catalog, predictions ->
                buildUiState(settings, catalog, predictions)
            }.flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

        /**
         * Just the theme choice, for the root `ReachTheme` and the window. Separate and
         * `distinctUntilChanged` so a catalog or prediction update doesn't recompose the
         * theme root.
         *
         * Starts as `null` = "not read yet": the Activity then uses the snapshot it read
         * synchronously before the first frame (docs/DECISIONS.md D63), so a user who forced
         * Light never sees a dark first frame.
         *
         * The wallpaper colour is only collected while wallpaper mode is on (`flatMapLatest`
         * switches to it and cancels it again), so the `WallpaperManager` listener isn't
         * registered for users who never enable the mode.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val theme: StateFlow<HomeTheme?> =
            settingsRepository.settings
                .map { HomeTheme(themeMode = it.themeMode, palette = it.palette, showWallpaper = it.showWallpaper) }
                .distinctUntilChanged()
                .flatMapLatest { base ->
                    if (base.showWallpaper) {
                        wallpaperColorSource.primaryColor.map { base.copy(wallpaperColor = it) }
                    } else {
                        flowOf(base)
                    }
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private val onboarding: Flow<OnboardingCard?> =
            combine(
                homeRoleSource.isDefaultHome,
                notificationCountSource.isAccessGranted,
                calendarSource.isPermissionGranted,
                usageStatsSource.isAccessGranted,
                settingsRepository.settings.map { it.dismissedTips }.distinctUntilChanged(),
                OnboardingCard::next,
            )

        /**
         * Media, the next calendar event and the onboarding card ([HomeSystemUi]). Starts empty
         * (nothing playing, no line, no card), so nothing flashes before the sources answer;
         * the onboarding card waits for *all* its inputs, so a granted access never shows a
         * card for a frame.
         *
         * Permissions granted in Settings are noticed on resume: `:core:system` re-reads every
         * access-gated source whenever a Reach Activity resumes (docs/DECISIONS.md), so these
         * flows re-emit without the ViewModel doing anything.
         */
        private val gestures: Flow<HomeGesturesUi> =
            combine(
                settingsRepository.settings.map { it.doubleTapToLock to it.hiddenApiShadeFallback }.distinctUntilChanged(),
                screenActionsSource.isServiceEnabled,
            ) { (doubleTap, hiddenApiFallback), serviceOn ->
                HomeGesturesUi(
                    doubleTapToLock = doubleTap,
                    canLock = serviceOn,
                    canOpenShade = serviceOn,
                    twoFingerSwipe = serviceOn || hiddenApiFallback,
                )
            }

        val system: StateFlow<HomeSystemUi> =
            combine(mediaSessionSource.mediaState, calendarSource.nextEvent, onboarding, gestures, ::HomeSystemUi)
                .distinctUntilChanged()
                .flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeSystemUi())

        /** Listen row / Now Playing card: toggle play/pause on the shown session. */
        fun onPlayPause() = mediaSessionSource.playPause()

        /** Now Playing card: next track. */
        fun onSkipNext() = mediaSessionSource.skipToNext()

        /** Now Playing card: previous track. */
        fun onSkipPrevious() = mediaSessionSource.skipToPrevious()

        /**
         * The primary button of an onboarding [card]. Special accesses and the home role are
         * granted in system screens, so this only opens them; the card disappears by itself
         * when the user comes back with access on. If the system screen doesn't exist on this
         * device, the card is dismissed — a button that does nothing would be a nag.
         * [OnboardingCard.CALENDAR] is a runtime permission: the UI asks with its
         * `ActivityResultLauncher` and reports through [onCalendarPermissionResult].
         */
        fun onOnboardingAction(card: OnboardingCard) {
            val screen =
                when (card) {
                    OnboardingCard.DEFAULT_HOME -> SystemScreen.HOME_ROLE
                    OnboardingCard.NOTIFICATION_ACCESS -> SystemScreen.NOTIFICATION_ACCESS
                    OnboardingCard.USAGE_ACCESS -> SystemScreen.USAGE_ACCESS
                    OnboardingCard.CALENDAR -> return
                }
            if (!appLauncher.open(screen)) onOnboardingDismissed(card)
        }

        /** "Not now": the card never comes back (persisted — Reach never nags, spec §7). */
        fun onOnboardingDismissed(card: OnboardingCard) {
            viewModelScope.launch { bestEffort { settingsRepository.dismissTip(card.storageKey) } }
        }

        /**
         * The calendar permission dialog answered. A grant is picked up by the calendar source
         * on resume; a denial (or "don't ask again", which answers `false` without a dialog)
         * dismisses the card for good, so it is never offered again.
         */
        fun onCalendarPermissionResult(granted: Boolean) {
            if (!granted) onOnboardingDismissed(OnboardingCard.CALENDAR)
        }

        // ---- Screen actions (spec §7.4, D66) ----------------------------------------------
        //
        // Both go through the optional accessibility service. Without it nothing destructive
        // happens: Home shows a short hint that points to the Appearance sheet, where the user
        // can turn the service on (Reach never opens Settings uninvited, never uses
        // device-admin lockNow()).

        /**
         * Double-tap on empty Home (only detected while "Double-tap to lock" is on) or the
         * "Lock screen" TalkBack action.
         */
        fun onLockScreenRequested() {
            if (!screenActionsSource.lockScreen()) {
                eventChannel.trySend(HomeEvent.ScreenActionUnavailable(ScreenAction.LOCK))
            }
        }

        /**
         * Two-finger swipe down on Home, or the "Open notifications" TalkBack action. Tries the
         * service, then — only if the user's flag allows it — the hidden-API fallback.
         */
        fun onNotificationsRequested() {
            viewModelScope.launch {
                val allowHiddenApi =
                    try {
                        settingsRepository.settings.first().hiddenApiShadeFallback
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                if (!screenActionsSource.openNotificationShade(allowHiddenApi)) {
                    eventChannel.trySend(HomeEvent.ScreenActionUnavailable(ScreenAction.NOTIFICATIONS))
                }
            }
        }

        /** Tap on an intent word, or on a chip in intent detail: show that intent's apps. */
        fun onIntentSelected(intent: ReachIntent) {
            _surface.value = HomeSurface.IntentDetail(intent)
        }

        /** Tap on the search pill or pull-down on Home. */
        fun onSearchRequested() {
            _surface.value = HomeSurface.Search
        }

        /** Swipe up on Home. */
        fun onAllAppsRequested() {
            _surface.value = HomeSurface.AllApps
        }

        /** Long-press on the clock (or its TalkBack action). */
        fun onAppearanceRequested() {
            _surface.value = HomeSurface.Appearance
        }

        /**
         * System Back: every sub-surface steps back to Home; on Home itself Back does
         * nothing (a launcher has nothing behind it — spec §4).
         */
        fun onBack() {
            _fan.value = null
            _surface.value = HomeSurface.Home
        }

        /**
         * The Home button was pressed (`MainActivity.onNewIntent`): return to Home from any
         * surface and close the keyboard, as every launcher does (spec §4). Pressed while
         * already on Home, it also scrolls Home back to the top (Home scrolls at large font
         * sizes), like a second tap on a selected tab.
         */
        fun onHomePressed() {
            val wasHome = _surface.value == HomeSurface.Home
            _fan.value = null
            _surface.value = HomeSurface.Home
            eventChannel.trySend(HomeEvent.HideKeyboard)
            if (wasHome) eventChannel.trySend(HomeEvent.ScrollToTop)
        }

        /**
         * Call on every `ON_RESUME`: the user may have installed apps or changed the default
         * home app while Reach was in the background, and package broadcasts are not always
         * delivered to a stopped app. Both calls are cheap and safe to repeat. A failing
         * refresh (e.g. a full disk) keeps the cached catalog instead of crashing Home.
         */
        fun onResume() {
            homeRoleSource.recheck()
            viewModelScope.launch { bestEffort { catalogRepository.refresh() } }
        }

        /**
         * Reach's language changed (Appearance › Language) while it stayed on screen: re-read the
         * catalog so app names follow it ("Clock" → "Reloj"), as they would after a resume.
         */
        fun onLanguageChanged() {
            viewModelScope.launch { bestEffort { catalogRepository.refresh() } }
        }

        /**
         * Launches [app], animating from [origin] (the tapped element in window pixels),
         * through the shared [LaunchApp] use case — the one place that records launches, so
         * Home, Search, All Apps and the fan count them the same way.
         *
         * The decision follows only the launcher's [LaunchResult], which is computed from
         * *live* profile state (docs/DECISIONS.md D32, D34) — never from the catalog's
         * possibly stale `isProfilePaused` flag:
         * - [LaunchResult.LAUNCHED]: [LaunchApp] records it (the primary prediction signal,
         *   spec §6.2, in the application scope) and the surface returns to Home, so Home —
         *   not the old detail list — greets the user when they come back.
         * - [LaunchResult.RESUMING_PROFILE]: the system was asked to resume a paused work
         *   profile; nothing is recorded and the user stays where they are to tap again.
         * - [LaunchResult.FAILED]: a short message is shown and the surface stays.
         */
        fun launch(
            app: CatalogApp,
            origin: LaunchOrigin?,
        ) {
            onLaunchResult(launchApp.app(app.key, origin), app.label)
        }

        // ---- Intent detail's app menu (spec §6.1, §6.4) -----------------------------------
        //
        // The same four actions as All Apps' menu, through the same repository and launcher
        // calls. The catalog re-emits after a move or hide, so the detail list (precomputed
        // for all six intents in uiState) updates by itself: a moved app leaves this intent
        // and appears in its new one, a hidden app leaves every intent and the Now row.

        /** Menu "App info": the system's App info screen. */
        fun openAppInfo(app: CatalogApp) {
            appLauncher.openAppInfo(app.key)
        }

        /** Menu "Move to intent…": the user's choice, which always beats auto-assignment. */
        fun moveToIntent(
            app: CatalogApp,
            intent: ReachIntent,
        ) {
            if (intent == app.intent) return
            viewModelScope.launch { bestEffort { catalogRepository.moveToIntent(app.key, intent) } }
        }

        /** Menu "Hide". Undone from All Apps' "Hidden" group, the one place hidden apps are listed. */
        fun hideApp(app: CatalogApp) {
            viewModelScope.launch { bestEffort { catalogRepository.setHidden(app.key, hidden = true) } }
        }

        /** Menu "Uninstall": the system shows its own confirmation (D26). */
        fun uninstall(app: CatalogApp) {
            appLauncher.requestUninstall(app.key)
        }

        /** Launches an app-shortcut pill; recorded against the app it belongs to. */
        fun launchShortcut(
            item: ShortcutItem,
            origin: LaunchOrigin?,
        ) {
            onLaunchResult(launchApp.shortcut(item.shortcut, origin, recordAs = item.appKey), item.shortcut.label)
        }

        private fun onLaunchResult(
            result: LaunchResult,
            label: String,
        ) {
            when (result) {
                LaunchResult.LAUNCHED -> {
                    _fan.value = null
                    _surface.value = HomeSurface.Home
                }

                LaunchResult.RESUMING_PROFILE -> {
                    // Stay where the user is; they tap again once the profile is running (D32, D34).
                }

                LaunchResult.FAILED -> {
                    eventChannel.trySend(HomeEvent.LaunchFailed(label))
                }
            }
        }

        // ---- Press, slide, release (spec §6.5) ------------------------------------------
        //
        // The ViewModel owns *what* the fan shows (which apps, in which order, which one is
        // highlighted) and what releasing does (launch through LaunchApp). The pointer
        // tracking, hit-testing and pill geometry live in the UI (ui/PressSlideRelease.kt),
        // because they are about pixels, not about apps.

        private val _fan = MutableStateFlow<FanState?>(null)

        /**
         * The open press-slide-release fan, or null when it is closed. A plain `StateFlow`
         * like [surface] (no upstream to restart), because it changes while a finger is down
         * and must never lag behind.
         */
        val fan: StateFlow<FanState?> = _fan.asStateFlow()

        /**
         * The user has held [intent]'s word long enough: open its fan with the intent's
         * visible apps, most-launched first (the nearest pill to the finger), capped at
         * [FanState.MAX_APPS]. The UI shows fewer when fewer fit above the finger.
         *
         * @return false — and nothing opens — when the intent has no apps: the gesture then
         *   stays out of the way and the press behaves like a normal tap/long-press.
         */
        fun onFanOpen(intent: ReachIntent): Boolean {
            val apps = uiState.value.fanAppsFor(intent)
            if (apps.isEmpty()) return false
            _fan.value = FanState(intent = intent, apps = apps, highlighted = null)
            return true
        }

        /**
         * The finger is over pill [index] (0 = nearest the finger), or over no pill / back on
         * the word (null). Out-of-range indices count as "no pill".
         */
        fun onFanHighlight(index: Int?) {
            _fan.update { fan -> fan?.copy(highlighted = index?.takeIf { it in fan.apps.indices }) }
        }

        /**
         * The finger lifted. Over a pill → launch that app from [origin] (the pill's window
         * bounds) with the same outcome handling as a tap ([launch]); anywhere else →
         * cancel. The fan closes either way.
         */
        fun onFanRelease(origin: LaunchOrigin?) {
            val app = _fan.value?.highlightedApp
            _fan.value = null
            if (app != null) launch(app, origin)
        }

        /** The gesture was cancelled (system took the pointer, Home left the screen): close the fan. */
        fun onFanCancel() {
            _fan.value = null
        }

        private companion object {
            const val NOW_ROW_SIZE = 3
            const val MAX_SHORTCUTS = 4
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /**
             * Runs [block] and drops any failure except cancellation. `viewModelScope` has no
             * exception handler, so an exception escaping a `launch` there would kill the
             * process — for a launcher that means a crash loop on the home screen.
             * `CancellationException` must still propagate: it is how coroutines stop.
             */
            suspend fun bestEffort(block: suspend () -> Unit) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing to show: the cache/launch already happened; the next resume retries.
                }
            }

            fun buildUiState(
                settings: AppearanceSettings,
                catalog: CatalogSnapshot,
                predictions: List<Prediction>,
            ): HomeUiState {
                // Private-space apps stay in All Apps' "Private space" section and Search only,
                // never in Home's intents (Android's guidance for launchers; D71).
                val visible = catalog.apps.filterNot { it.isHidden || it.profile == ProfileType.PRIVATE }
                val byIntent = visible.groupBy { it.intent }
                val intents =
                    ReachIntent.entries.map { intent ->
                        val apps = byIntent[intent].orEmpty()
                        IntentSummary(intent, appCount = apps.size, unreadCount = unreadFor(apps, catalog.unread))
                    }
                return HomeUiState(
                    catalogLoaded = true,
                    thumbSide = settings.thumbSide,
                    intents = intents,
                    now = predictions.take(NOW_ROW_SIZE).map { NowItem(it.app, it.reason) },
                    details = ReachIntent.entries.associateWith { detailFor(it, byIntent[it].orEmpty(), catalog) },
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
