package com.baklavapp.reachlauncher.core.data.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.baklavapp.reachlauncher.core.data.repository.SettingsRepository
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Appearance settings in Preferences DataStore.
 *
 * Enums are stored by their `storageKey` and read back with `fromStorageKey`, which falls
 * back to the default for missing or unknown keys — so a first run, a renamed constant or
 * a value written by a newer build all read as sensible defaults ([AppearanceSettings]).
 *
 * DataStore does its file I/O on its own background dispatcher and emits a new
 * `Preferences` snapshot after every `edit`, which is what makes every setter "apply
 * live" with no Save button.
 *
 * Flutter analogy: a settings repository over `shared_preferences` — except DataStore is
 * asynchronous and transactional by design, and `data` is a `Stream` you listen to
 * instead of re-reading after each write.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppearanceSettings> =
            dataStore.data
                // A read failure (corrupt or unreadable file) must not take Home down: fall
                // back to defaults. Other errors are bugs and are rethrown.
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { prefs -> prefs.toSettings() }
                .distinctUntilChanged()

        override suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME_MODE, mode.storageKey)

        override suspend fun setPalette(palette: PaletteId) = put(Keys.PALETTE, palette.storageKey)

        override suspend fun setShowWallpaper(show: Boolean) = put(Keys.SHOW_WALLPAPER, show)

        override suspend fun setThumbSide(side: ThumbSide) = put(Keys.THUMB_SIDE, side.storageKey)

        override suspend fun setDoubleTapToLock(enabled: Boolean) = put(Keys.DOUBLE_TAP_TO_LOCK, enabled)

        override suspend fun setHiddenApiShadeFallback(enabled: Boolean) = put(Keys.HIDDEN_API_SHADE_FALLBACK, enabled)

        // Read-modify-write inside one `edit` transaction, so two quick dismissals can't
        // overwrite each other.
        override suspend fun dismissTip(tipKey: String) {
            dataStore.edit { it[Keys.DISMISSED_TIPS] = it[Keys.DISMISSED_TIPS].orEmpty() + tipKey }
        }

        private suspend fun <T> put(
            key: Preferences.Key<T>,
            value: T,
        ) {
            dataStore.edit { it[key] = value }
        }

        private fun Preferences.toSettings(): AppearanceSettings {
            val defaults = AppearanceSettings()
            return AppearanceSettings(
                themeMode = ThemeMode.fromStorageKey(this[Keys.THEME_MODE]),
                palette = PaletteId.fromStorageKey(this[Keys.PALETTE]),
                showWallpaper = this[Keys.SHOW_WALLPAPER] ?: defaults.showWallpaper,
                thumbSide = ThumbSide.fromStorageKey(this[Keys.THUMB_SIDE]),
                doubleTapToLock = this[Keys.DOUBLE_TAP_TO_LOCK] ?: defaults.doubleTapToLock,
                hiddenApiShadeFallback = this[Keys.HIDDEN_API_SHADE_FALLBACK] ?: defaults.hiddenApiShadeFallback,
                dismissedTips = this[Keys.DISMISSED_TIPS] ?: defaults.dismissedTips,
            )
        }

        /** Preference keys. Never rename the strings: they are what's on disk. */
        private object Keys {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val PALETTE = stringPreferencesKey("palette")
            val SHOW_WALLPAPER = booleanPreferencesKey("show_wallpaper")
            val THUMB_SIDE = stringPreferencesKey("thumb_side")
            val DOUBLE_TAP_TO_LOCK = booleanPreferencesKey("double_tap_to_lock")
            val HIDDEN_API_SHADE_FALLBACK = booleanPreferencesKey("hidden_api_shade_fallback")
            val DISMISSED_TIPS = stringSetPreferencesKey("dismissed_tips")
        }
    }
