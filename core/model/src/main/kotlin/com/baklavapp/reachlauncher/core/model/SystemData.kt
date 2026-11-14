package com.baklavapp.reachlauncher.core.model

/*
 * Data that comes from Android system services, expressed without Android types so
 * ViewModels can be tested on the plain JVM.
 *
 * Flutter analogy: the Dart-side model classes a plugin hands you after decoding a
 * MethodChannel result — except here the `:core:system` module builds them directly
 * from Android objects.
 */

/**
 * The active media session, as shown on the Listen row and the Now Playing card.
 *
 * @property sourcePackage package of the app that owns the session.
 * @property sourceLabel human label of that app ("Spotify"), shown as "PLAYING · SPOTIFY".
 * @property positionMillis position at [positionUpdatedAtMillis] (elapsed-realtime clock);
 *   the UI extrapolates while [isPlaying] instead of polling the session.
 */
data class MediaState(
    val title: String,
    val artist: String?,
    val sourcePackage: String,
    val sourceLabel: String,
    val isPlaying: Boolean,
    val positionMillis: Long,
    val durationMillis: Long,
    val positionUpdatedAtMillis: Long,
    val playbackSpeed: Float = 1f,
)

/**
 * A launcher shortcut (`ShortcutInfo`): manifest, dynamic or pinned.
 *
 * @property id the shortcut's id, unique within its package and user.
 * @property app the package-in-profile that publishes it.
 * @property activityName the activity the shortcut is attached to; null for some pinned
 *   shortcuts (`ShortcutInfo.getActivity()` may be null).
 * @property label short label, e.g. "Message Ayşe".
 * @property appLabel source app label, shown in mono caps beside the shortcut label.
 */
data class AppShortcut(
    val id: String,
    val app: PackageUser,
    val activityName: String?,
    val label: String,
    val appLabel: String,
)

/** A contact found by search. Only available when `READ_CONTACTS` is granted. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val phoneNumber: String?,
)

/** The next calendar event for Home's context line (only with `READ_CALENDAR`). */
data class CalendarEvent(
    val title: String,
    val beginEpochMillis: Long,
    val isAllDay: Boolean,
)

/**
 * Origin of a launch, in pixels relative to the Home window (Compose's
 * `LayoutCoordinates.boundsInWindow()`), so the launch animation can grow out of the
 * tapped element (clip-reveal / scale-up).
 */
data class LaunchOrigin(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Outcome of asking the system to launch an app. The launcher decides from *live* profile
 * state, so callers must react to this result rather than to a possibly stale
 * `CatalogApp.isProfilePaused` snapshot (docs/DECISIONS.md D32).
 */
enum class LaunchResult {
    /** The activity was started; record it in launch history and return Home. */
    LAUNCHED,

    /** The app's work profile was paused; the system was asked to resume it. Nothing launched yet. */
    RESUMING_PROFILE,

    /** Not launched: the activity is gone, the profile is locked, or the system refused. */
    FAILED,
}

/**
 * System screens Reach sends the user to, e.g. to grant an optional permission.
 * A domain type instead of `android.content.Intent`, so callers stay JVM-testable;
 * `:core:system` turns each into the right Intent.
 */
enum class SystemScreen {
    /** Notification access (for unread counts and media controls). */
    NOTIFICATION_ACCESS,

    /** Usage access (optional prediction blend). */
    USAGE_ACCESS,

    /** Accessibility settings, to enable Reach's optional service. */
    ACCESSIBILITY_SERVICE,

    /** The "become default home" role dialog, falling back to the Home settings screen. */
    HOME_ROLE,
}

/** Actions that hand off to another app. Built into Intents by `:core:system`. */
sealed interface ExternalAction {
    /** `ACTION_WEB_SEARCH` with [query] — search's last-resort result. */
    data class WebSearch(
        val query: String,
    ) : ExternalAction

    /** Opens the dialer with [phoneNumber] filled in (no `CALL_PHONE` permission needed). */
    data class Dial(
        val phoneNumber: String,
    ) : ExternalAction

    /** Opens the default SMS app addressed to [phoneNumber]. */
    data class Message(
        val phoneNumber: String,
    ) : ExternalAction

    /** Opens the contact card. */
    data class ViewContact(
        val id: Long,
        val lookupKey: String,
    ) : ExternalAction
}
