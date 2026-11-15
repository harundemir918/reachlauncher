package com.baklavapp.reachlauncher.core.data.fake

import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.repository.PredictionRepository
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/*
 * In-memory fakes of the repository contracts, for ViewModel tests and Compose previews
 * in feature modules. Each exposes its backing state so tests can push values in, and
 * each honours its interface's KDoc (sorting, filtering) so tests see realistic data.
 *
 * Flutter analogy: the hand-written fake repositories you'd pass to a BLoC in
 * `bloc_test`, instead of a mocktail mock.
 */

/**
 * Fake catalog. [state] is the raw list; [apps] emits it sorted by label with a
 * [Collator] for [locale], as the real repository does.
 */
class FakeAppCatalogRepository(
    initial: List<CatalogApp> = emptyList(),
    private val locale: Locale = Locale.ENGLISH,
) : AppCatalogRepository {
    /** Backing list; tests set it to simulate installs, removals and cache emissions. */
    val state = MutableStateFlow(initial)

    /** How many times [refresh] was called. */
    var refreshCount = 0
        private set

    override val apps: Flow<List<CatalogApp>> =
        state.map { list ->
            val collator = Collator.getInstance(locale)
            list.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun moveToIntent(
        key: AppKey,
        intent: ReachIntent,
    ) = state.update { list -> list.map { if (it.key == key) it.copy(intent = intent, isUserAssigned = true) else it } }

    override suspend fun setHidden(
        key: AppKey,
        hidden: Boolean,
    ) = state.update { list -> list.map { if (it.key == key) it.copy(isHidden = hidden) else it } }
}

/**
 * Fake history. Hour and weekday are derived from `epochMillis` in [zone] (UTC by
 * default, so tests are deterministic on any machine).
 */
class FakeLaunchHistoryRepository(
    private val zone: ZoneId = ZoneOffset.UTC,
) : LaunchHistoryRepository {
    /** Every recorded launch, in recording order. Tests may also set it directly. */
    val events = MutableStateFlow<List<LaunchEvent>>(emptyList())

    override suspend fun recordLaunch(
        key: AppKey,
        epochMillis: Long,
    ) {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        events.update { it + LaunchEvent(key, epochMillis, time.hour, time.dayOfWeek.value) }
    }

    override val launchCounts: Flow<Map<AppKey, Int>> =
        events.map { list -> list.groupingBy { it.key }.eachCount() }

    override fun eventsSince(sinceEpochMillis: Long): Flow<List<LaunchEvent>> =
        events.map { list -> list.filter { it.epochMillis >= sinceEpochMillis }.sortedBy { it.epochMillis } }
}

/**
 * Fake predictions: whatever is in [state], minus hidden and paused-profile apps,
 * truncated to the requested limit.
 */
class FakePredictionRepository(
    initial: List<Prediction> = emptyList(),
) : PredictionRepository {
    /** Predictions before filtering, best first. */
    val state = MutableStateFlow(initial)

    override fun predictions(limit: Int): Flow<List<Prediction>> =
        state.map { list -> list.filterNot { it.app.isHidden || it.app.isProfilePaused }.take(limit) }
}

/** Fake settings held in memory; every setter re-emits, like DataStore. */
class FakeSettingsRepository(
    initial: AppearanceSettings = AppearanceSettings(),
) : SettingsRepository {
    /** Current settings; tests may set it directly. */
    val state = MutableStateFlow(initial)

    override val settings: Flow<AppearanceSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setPalette(palette: PaletteId) = state.update { it.copy(palette = palette) }

    override suspend fun setShowWallpaper(show: Boolean) = state.update { it.copy(showWallpaper = show) }

    override suspend fun setThumbSide(side: ThumbSide) = state.update { it.copy(thumbSide = side) }

    override suspend fun setDoubleTapToLock(enabled: Boolean) = state.update { it.copy(doubleTapToLock = enabled) }

    override suspend fun setHiddenApiShadeFallback(enabled: Boolean) = state.update { it.copy(hiddenApiShadeFallback = enabled) }

    override suspend fun dismissTip(tipKey: String) = state.update { it.copy(dismissedTips = it.dismissedTips + tipKey) }
}
