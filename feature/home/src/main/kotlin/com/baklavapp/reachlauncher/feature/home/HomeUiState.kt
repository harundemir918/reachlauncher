package com.baklavapp.reachlauncher.feature.home

import androidx.compose.runtime.Immutable
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide

/*
 * Home's UI state and one-off events.
 *
 * Flutter analogy: `HomeUiState` is the BLoC's state class (think Freezed/equatable): one
 * immutable snapshot of everything Home draws. `HomeEvent` is what you'd react to in a
 * `BlocListener` — things that happen *once* (hide the keyboard, show a message) and must
 * not replay when the UI re-subscribes, so they don't belong in the state.
 *
 * Why `@Immutable`: it promises Compose that these objects never change after creation, so
 * a composable receiving the same instance can skip recomposition. The lists inside are
 * read-only `List`s that the ViewModel always replaces wholesale, never mutates.
 */

/**
 * Everything Home's surfaces render, except *which* surface is active (that is
 * `HomeViewModel.surface`, kept separate so it is always current — see the ViewModel KDoc).
 * The default value is a valid first frame (all six intents, no counts yet, no predictions):
 * Home never shows a spinner.
 *
 * @property catalogLoaded false until the first catalog emission. Until then the intent
 *   rows show no trailing count (not a misleading "0") and the NOW block stays hidden.
 * @property intents all six intents, always in [ReachIntent] order, even with 0 apps.
 * @property now up to three Now-row predictions; empty hides the whole NOW block.
 * @property details the intent-detail content of every intent, precomputed so switching
 *   intents (word tap, chip) never waits for a recomputation; read it with [detailFor].
 */
@Immutable
data class HomeUiState(
    val catalogLoaded: Boolean = false,
    val thumbSide: ThumbSide = ThumbSide.LEFT,
    val intents: List<IntentSummary> = ReachIntent.entries.map { IntentSummary(it, appCount = 0, unreadCount = 0) },
    val now: List<NowItem> = emptyList(),
    val details: Map<ReachIntent, IntentDetailUi> = emptyMap(),
) {
    /** Detail content for [intent]; empty (not null) before the catalog has loaded. */
    fun detailFor(intent: ReachIntent): IntentDetailUi = details[intent] ?: IntentDetailUi(intent, emptyList(), emptyList())

    /**
     * The apps the press-slide-release fan shows for [intent]: the same order as intent
     * detail (most-launched first, then alphabetical), capped at [FanState.MAX_APPS].
     */
    fun fanAppsFor(intent: ReachIntent): List<CatalogApp> =
        detailFor(intent)
            .apps
            .asSequence()
            .take(FanState.MAX_APPS)
            .map { it.app }
            .toList()
}

/**
 * The open press-slide-release fan (spec §6.5).
 *
 * @property intent the held intent word.
 * @property apps the fanned apps; index 0 is the pill nearest the finger (the most used).
 *   The UI may show only the first few when fewer fit above the finger (200% font, a word
 *   high on the screen); it never highlights a pill it didn't place.
 * @property highlighted index into [apps] of the pill under the finger, or null when the
 *   finger is over no pill or back on the word (releasing then cancels).
 */
@Immutable
data class FanState(
    val intent: ReachIntent,
    val apps: List<CatalogApp>,
    val highlighted: Int?,
) {
    /** The app a release would launch, or null when a release would cancel. */
    val highlightedApp: CatalogApp? get() = highlighted?.let(apps::getOrNull)

    companion object {
        /**
         * Most pills a fan ever shows. Six 52dp pills plus gaps take ≈ 370dp — about what
         * sits above the lowest intent word on a typical phone — and more choices than that
         * stop being a quick "slide and release" anyway (the detail list has the rest).
         */
        const val MAX_APPS = 6
    }
}

/**
 * The theme choice the root `ReachTheme` and the window need — a small, separate state so
 * the theme root only recomposes when these values change.
 *
 * @property showWallpaper wallpaper mode (spec §6.9): the window shows the wallpaper and Home
 *   paints the scrim instead of its opaque bg.
 * @property wallpaperColor the wallpaper's primary colour as ARGB, only while
 *   [showWallpaper] is on and the system knows it; the accent is derived from it.
 */
@Immutable
data class HomeTheme(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val palette: PaletteId = PaletteId.DEFAULT,
    val showWallpaper: Boolean = false,
    val wallpaperColor: Int? = null,
)

/**
 * One intent word on Home: how many visible apps it holds and how many unread
 * notifications those apps have (the "3 NEW" badge).
 */
@Immutable
data class IntentSummary(
    val intent: ReachIntent,
    val appCount: Int,
    val unreadCount: Int,
)

/** One Now-row tile: the predicted app and why it was predicted. */
@Immutable
data class NowItem(
    val app: CatalogApp,
    val reason: PredictionReason,
)

/** An app row in intent detail, with its own unread count (per package *and* profile). */
@Immutable
data class AppItem(
    val app: CatalogApp,
    val unreadCount: Int,
)

/**
 * An app-shortcut pill in intent detail.
 *
 * @property appKey the catalog app the shortcut was found for; a shortcut launch is
 *   recorded against it so predictions learn from shortcut use too.
 */
@Immutable
data class ShortcutItem(
    val shortcut: AppShortcut,
    val appKey: AppKey,
)

/**
 * Intent detail content.
 *
 * @property apps visible apps of [intent], most-launched first, then alphabetical.
 * @property shortcuts up to four shortcuts of the intent's most-used apps; empty (section
 *   hidden) without the shortcut host permission.
 */
@Immutable
data class IntentDetailUi(
    val intent: ReachIntent,
    val apps: List<AppItem>,
    val shortcuts: List<ShortcutItem>,
)

/**
 * The live system extras around Home's data (spec §6.3, §6.4, §7): what's playing, the next
 * calendar event and the one onboarding card to show. A separate, small state from
 * [HomeUiState] because it changes often (every play/pause, every track) and has nothing to
 * do with the catalog: a track change must not regroup and re-sort every app.
 *
 * @property media the active media session, or null (nothing playing / no notification
 *   access): the Listen row's inline player and the Now Playing card are hidden.
 * @property nextEvent the context line under the clock; null hides it (no event, or no
 *   calendar permission).
 * @property onboarding the single card to show, or null for none (all granted, or dismissed).
 * @property gestures the screen-action gestures (double-tap to lock, two-finger shade).
 */
@Immutable
data class HomeSystemUi(
    val media: MediaState? = null,
    val nextEvent: CalendarEvent? = null,
    val onboarding: OnboardingCard? = null,
    val gestures: HomeGesturesUi = HomeGesturesUi(),
)

/**
 * What Home's screen-action gestures may do (spec §6.3, §7.4; docs/DECISIONS.md D66).
 *
 * @property doubleTapToLock the user switched on "Double-tap to lock" (Appearance sheet).
 *   Only then does Home listen for double-taps at all, so a single tap on empty space is
 *   never delayed while it waits for a possible second one.
 * @property canLock the accessibility service is on: locking will work. Adds the "Lock
 *   screen" TalkBack action.
 * @property canOpenShade the service is on: adds the "Open notifications" TalkBack action.
 *   (Not the hidden-API fallback: it can fail silently, and an action is only offered when
 *   it will work.)
 * @property twoFingerSwipe Home listens for the two-finger swipe: the service is on, or the
 *   best-effort hidden-API fallback is allowed. Otherwise two-finger touches pass through
 *   untouched (D71).
 */
@Immutable
data class HomeGesturesUi(
    val doubleTapToLock: Boolean = false,
    val canLock: Boolean = false,
    val canOpenShade: Boolean = false,
    val twoFingerSwipe: Boolean = false,
)

/** A screen action that can be unavailable (accessibility service off). */
enum class ScreenAction {
    /** Lock the screen (double-tap on empty Home). */
    LOCK,

    /** Open the notification shade (two-finger swipe down on Home). */
    NOTIFICATIONS,
}

/**
 * Onboarding and permission cards (spec §4, §7), in the order they are offered — one at a
 * time, never a modal, never again after "Not now" ([storageKey] is what
 * `SettingsRepository.dismissTip` persists; never rename it).
 */
enum class OnboardingCard(
    val storageKey: String,
) {
    /** Reach isn't the default home app: the Home button wouldn't come here (spec §4). */
    DEFAULT_HOME("default_home"),

    /** Notification access: "N NEW" badges and the media controls (spec §7.1, §7.2). */
    NOTIFICATION_ACCESS("notification_access"),

    /** Optional `READ_CALENDAR`: the next event under the clock (spec §7.6). */
    CALENDAR("calendar"),

    /** Optional usage access: better Now-row guesses (spec §6.2). */
    USAGE_ACCESS("usage_access"),
    ;

    companion object {
        /**
         * The card to show: the first in declaration order that is still needed and not
         * dismissed. The essentials (home role, notification access) come before the two
         * optional extras.
         */
        fun next(
            isDefaultHome: Boolean,
            hasNotificationAccess: Boolean,
            hasCalendar: Boolean,
            hasUsageAccess: Boolean,
            dismissed: Set<String>,
        ): OnboardingCard? {
            val needed =
                mapOf(
                    DEFAULT_HOME to !isDefaultHome,
                    NOTIFICATION_ACCESS to !hasNotificationAccess,
                    CALENDAR to !hasCalendar,
                    USAGE_ACCESS to !hasUsageAccess,
                )
            return entries.firstOrNull { needed.getValue(it) && it.storageKey !in dismissed }
        }
    }
}

/** One-off effects for the UI (≈ what a `BlocListener` reacts to). */
sealed interface HomeEvent {
    /** Close the soft keyboard and drop focus (Home pressed while searching). */
    data object HideKeyboard : HomeEvent

    /** Home pressed while already on Home: scroll Home back to the top. */
    data object ScrollToTop : HomeEvent

    /** An app or shortcut couldn't be opened (uninstalled, disabled, locked profile). */
    data class LaunchFailed(
        val label: String,
    ) : HomeEvent

    /**
     * A screen-action gesture couldn't run because the accessibility service is off: show a
     * short hint pointing to the Appearance sheet, where the user can turn it on (never a
     * dialog, and nothing destructive happens instead).
     */
    data class ScreenActionUnavailable(
        val action: ScreenAction,
    ) : HomeEvent
}
