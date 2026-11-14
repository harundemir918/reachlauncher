package com.baklavapp.reachlauncher.feature.home

import androidx.compose.runtime.Immutable
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.CatalogApp
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
 * Everything the Home surface renders. The default value is a valid first frame (all six
 * intents with zero apps, no predictions): Home never shows a spinner.
 *
 * @property surface which surface is active (see [HomeSurface]).
 * @property intents all six intents, always in [ReachIntent] order, even with 0 apps.
 * @property now up to three Now-row predictions; empty hides the whole NOW block.
 * @property detail data for [HomeSurface.IntentDetail]; null on every other surface.
 */
@Immutable
data class HomeUiState(
    val surface: HomeSurface = HomeSurface.Home,
    val thumbSide: ThumbSide = ThumbSide.LEFT,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val palette: PaletteId = PaletteId.DEFAULT,
    val intents: List<IntentSummary> = ReachIntent.entries.map { IntentSummary(it, appCount = 0, unreadCount = 0) },
    val now: List<NowItem> = emptyList(),
    val detail: IntentDetailUi? = null,
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

/** One-off effects for the UI (≈ what a `BlocListener` reacts to). */
sealed interface HomeEvent {
    /** Close the soft keyboard and drop focus (Home pressed while searching). */
    data object HideKeyboard : HomeEvent

    /** An app or shortcut couldn't be opened (uninstalled, disabled, locked profile). */
    data class LaunchFailed(
        val label: String,
    ) : HomeEvent
}
