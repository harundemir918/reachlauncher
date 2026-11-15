package com.baklavapp.reachlauncher.core.model

/*
 * Appearance settings types.
 *
 * Flutter analogy: the values you'd keep in `shared_preferences` behind a settings
 * repository. Here they're persisted in DataStore by their `storageKey` strings, never by
 * enum `name` or ordinal, so renaming or reordering constants never breaks saved settings.
 */

/**
 * The seven palettes. Persisted **by [storageKey], never as colour values**, so a palette
 * tweak in a later release reaches existing users automatically.
 */
enum class PaletteId(
    val storageKey: String,
) {
    LIME("lime"),
    PAPER_INK("paper_ink"),
    NIGHT_TIDE("night_tide"),
    TERRACOTTA("terracotta"),
    FOREST("forest"),
    DUSK("dusk"),
    HIGH_CONTRAST("high_contrast"),
    ;

    companion object {
        /** The default palette for new installs. */
        val DEFAULT = LIME

        /** Unknown keys (e.g. a palette removed later) fall back to [DEFAULT]. */
        fun fromStorageKey(key: String?): PaletteId = entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

/** Theme selection in the Appearance sheet. [AUTO] follows Android's dark-mode setting. */
enum class ThemeMode(
    val storageKey: String,
) {
    AUTO("auto"),
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        /** Mode for a persisted [key]; missing or unknown keys fall back to [AUTO]. */
        fun fromStorageKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: AUTO
    }
}

/** Which side the intent words, the gesture fan and the letter rail hug. */
enum class ThumbSide(
    val storageKey: String,
) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        /** Side for a persisted [key]; missing or unknown keys fall back to [LEFT]. */
        fun fromStorageKey(key: String?): ThumbSide = entries.firstOrNull { it.storageKey == key } ?: LEFT
    }
}

/**
 * Everything the Appearance sheet controls, plus opt-in behaviour flags. Persisted in
 * DataStore; every change re-themes Home live through a `Flow<AppearanceSettings>`.
 *
 * @property showWallpaper wallpaper mode: blurred wallpaper behind a scrim, accent picked
 *   from the wallpaper.
 * @property doubleTapToLock requires the optional accessibility service.
 * @property hiddenApiShadeFallback best-effort, hidden-API way to open the notification
 *   shade when the accessibility service is off. Off by default (feature flag).
 * @property dismissedTips storage keys of the onboarding/permission cards the user answered
 *   with "Not now" (or a denied permission dialog). A dismissed card never comes back — Reach
 *   never nags (spec §7). Plain strings so a feature can add a card without a model change;
 *   unknown keys are simply ignored.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val palette: PaletteId = PaletteId.DEFAULT,
    val showWallpaper: Boolean = false,
    val thumbSide: ThumbSide = ThumbSide.LEFT,
    val doubleTapToLock: Boolean = false,
    val hiddenApiShadeFallback: Boolean = false,
    val dismissedTips: Set<String> = emptySet(),
)
