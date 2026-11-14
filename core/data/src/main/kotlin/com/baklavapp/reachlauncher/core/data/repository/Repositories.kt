package com.baklavapp.reachlauncher.core.data.repository

import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchEvent
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import kotlinx.coroutines.flow.Flow

/*
 * Repository contracts. Features depend on these interfaces only; implementations live in
 * this module (`…core.data.repository.impl`) and are bound with Hilt.
 *
 * Flutter analogy: the abstract repository classes of your domain layer in clean
 * architecture. `Flow<T>` plays the role of `Stream<T>`; `suspend fun` is a `Future`-
 * returning method you `await` (Kotlin awaits implicitly inside a coroutine).
 */

/**
 * The launchable apps Reach knows about, with their intent assignment and visibility.
 *
 * Performance contract: [apps] must emit the **cached** catalog (from Room) immediately,
 * before any system query — including the profile lookup — so Home renders instantly when
 * the Home button is pressed. Fresh data from the system follows as a second emission when
 * it differs.
 *
 * Privacy contract: apps of a **locked private space are never emitted**, not even from
 * the cache. Every emission (cached or fresh) is filtered against the `ProfileSource.profiles`
 * state known so far; before the first profile answer that state is "no profile known", so
 * private-space apps are hidden (fail closed) and nothing is marked paused yet. Apps of a
 * paused work profile are marked with [CatalogApp.isProfilePaused] — a display hint only;
 * launching decides from live state (`AppLauncher.launchApp`'s `LaunchResult`). Predictions
 * and search build on [apps], so they inherit this.
 */
interface AppCatalogRepository {
    /** All apps including hidden ones, sorted by label (locale-aware). Never completes. */
    val apps: Flow<List<CatalogApp>>

    /** Re-reads installed apps from the system and updates the cache. Safe to call often. */
    suspend fun refresh()

    /** "Move to…": the user's choice, which always beats auto-assignment. */
    suspend fun moveToIntent(
        key: AppKey,
        intent: ReachIntent,
    )

    /** Hides or un-hides an app everywhere except All Apps' long-press menu. */
    suspend fun setHidden(
        key: AppKey,
        hidden: Boolean,
    )
}

/**
 * Launches Reach performed itself. Needs no permission; primary input for predictions.
 */
interface LaunchHistoryRepository {
    /** Records a launch at [epochMillis]; hour and weekday are derived in local time. */
    suspend fun recordLaunch(
        key: AppKey,
        epochMillis: Long,
    )

    /** Launch count per app, for "sorted by launch frequency". */
    val launchCounts: Flow<Map<AppKey, Int>>

    /** Events newer than [sinceEpochMillis], oldest first. */
    fun eventsSince(sinceEpochMillis: Long): Flow<List<LaunchEvent>>
}

/**
 * Apps predicted for "right now": the Home "Now" row (3) and empty-query search (4).
 * Re-evaluates as time passes, history grows, or unread counts change.
 */
interface PredictionRepository {
    /** Up to [limit] predictions, best first, never including hidden or locked-profile apps. */
    fun predictions(limit: Int): Flow<List<Prediction>>
}

/**
 * Appearance and behaviour settings, persisted in DataStore. Every setter applies live:
 * [settings] re-emits and the theme follows, with no Save button.
 */
interface SettingsRepository {
    /** Current settings; emits defaults on first run and re-emits after every setter. */
    val settings: Flow<AppearanceSettings>

    /** THEME segmented control: Auto (follows Android) / Dark / Light. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** PALETTE swatches. Stored by [PaletteId.storageKey], never as colours. */
    suspend fun setPalette(palette: PaletteId)

    /** "Show wallpaper" switch (wallpaper mode, spec §6.9). */
    suspend fun setShowWallpaper(show: Boolean)

    /** "Thumb side" segmented control. */
    suspend fun setThumbSide(side: ThumbSide)

    /** Double-tap empty Home to lock; only effective with the accessibility service on. */
    suspend fun setDoubleTapToLock(enabled: Boolean)

    /** Feature flag for the best-effort hidden-API notification-shade fallback. */
    suspend fun setHiddenApiShadeFallback(enabled: Boolean)
}
