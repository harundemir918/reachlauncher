package com.baklavapp.reachlauncher.core.data.source

import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.SystemScreen
import kotlinx.coroutines.flow.Flow

/*
 * System-source contracts: every Android system integration Reach uses, each behind a
 * small interface. Implementations live in `:core:system`; features never import that
 * module, so they can be tested with the fakes in `…core.data.fake`.
 *
 * No Android types appear here (no Intent, View, Context): ViewModel tests stay plain
 * JVM tests. Every source must work when its permission is denied: it reports "not
 * available" (empty value, `false`, `null`) instead of throwing.
 *
 * Flutter analogy: like the abstract classes you'd put in front of a plugin
 * (`url_launcher`, `device_apps`…) so BLoCs can be tested with mocks — except there is
 * no MethodChannel here; the implementations call Android APIs directly.
 */

/** Launchable activities across all user profiles (`LauncherApps`). */
interface InstalledAppsSource {
    /**
     * Current installed apps; re-emits on install, removal, update, and profile changes
     * (`LauncherApps.Callback`, work profile paused/resumed, private space lock/unlock).
     * Apps of a paused work profile are included; apps of a locked private space are not.
     * Reach's own launcher entry is never included.
     */
    val installedApps: Flow<List<InstalledApp>>
}

/** User profiles (personal, work, private space) and their quiet-mode state. */
interface ProfileSource {
    /** All profiles visible to the launcher; re-emits on pause/resume and lock/unlock. */
    val profiles: Flow<List<ProfileState>>

    /**
     * Pauses/resumes a work profile or locks/unlocks the private space
     * (`UserManager.requestQuietModeEnabled`). Unlocking may show system authentication.
     * @return false if the request was refused.
     */
    fun setQuietMode(
        userSerial: Long,
        enabled: Boolean,
    ): Boolean
}

/**
 * Starts apps, system screens and hand-offs to other apps.
 *
 * Implementation note: launch animations (`ActivityOptions.makeClipRevealAnimation`) and
 * the role request dialog (`startActivityForResult`) need the current Activity and its
 * View. The `:core:system` implementation is a singleton that tracks the resumed Activity
 * through a weak reference (registered in `ReachApplication.onCreate`), so ViewModels can call it
 * without holding any Android object. Without a resumed Activity it launches without
 * animation.
 */
interface AppLauncher {
    /**
     * Launches [key] with an animation that grows from [origin] when given. If the app's
     * work profile is paused (checked live), the system is asked to resume it instead and
     * nothing launches yet.
     * @return what actually happened; only [LaunchResult.LAUNCHED] counts as a launch.
     */
    fun launchApp(
        key: AppKey,
        origin: LaunchOrigin?,
    ): LaunchResult

    /** Opens the system "App info" screen for [key]. */
    fun openAppInfo(key: AppKey)

    /** Asks the system to uninstall [key]'s package (the system shows its own confirm). */
    fun requestUninstall(key: AppKey)

    /** Opens a system screen (permission settings, home role dialog). False if unavailable. */
    fun open(screen: SystemScreen): Boolean

    /** Hands off to another app (web search, dialer, SMS, contact card). False if nothing handles it. */
    fun perform(action: ExternalAction): Boolean
}

/** Default dialer/SMS apps (Telecom/Telephony APIs, D23), which auto-assignment puts into Talk. */
interface DefaultAppsSource {
    /** Package names currently holding the dialer or SMS role. */
    suspend fun talkRolePackages(): Set<String>
}

/**
 * Whether Reach is the default home app. To ask to become it, use
 * `AppLauncher.open(SystemScreen.HOME_ROLE)`.
 */
interface HomeRoleSource {
    /** Current state; re-emits after [recheck]. */
    val isDefaultHome: Flow<Boolean>

    /** Re-reads the role state. Call on every resume (e.g. after returning from settings). */
    fun recheck()
}

/**
 * Unread notification counts (`NotificationListenerService`). The user grants access in
 * system settings: `AppLauncher.open(SystemScreen.NOTIFICATION_ACCESS)`.
 */
interface NotificationCountSource {
    /** Whether the user enabled notification access for Reach. */
    val isAccessGranted: Flow<Boolean>

    /**
     * Active, non-ongoing notifications per package *and profile*. Empty when access is off.
     *
     * Deviation from spec §7.1 (`Map<packageName, Int>`): keyed by [PackageUser] so the
     * personal and work copies of an app get separate badges (docs/DECISIONS.md).
     */
    val countsByApp: Flow<Map<PackageUser, Int>>
}

/** Active media session (`MediaSessionManager`, reuses notification-listener access). */
interface MediaSessionSource {
    /** The most relevant active session, or null when nothing plays or access is off. */
    val mediaState: Flow<MediaState?>

    /** Toggles play/pause on the active session (`TransportControls`); no-op without one. */
    fun playPause()

    /** Skips to the next track; no-op without a session. */
    fun skipToNext()

    /** Skips to the previous track; no-op without a session. */
    fun skipToPrevious()

    /**
     * Opens the app that owns the shown session, animating from [origin] when given: the
     * screen the app registered for its session (usually its player), else the app's launch
     * screen.
     *
     * @return false when there is no session or neither could be started.
     */
    fun openApp(origin: LaunchOrigin?): Boolean
}

/**
 * Optional `UsageStatsManager` data blended into predictions. The user grants access in
 * system settings: `AppLauncher.open(SystemScreen.USAGE_ACCESS)`.
 */
interface UsageStatsSource {
    /** Whether usage access is granted; re-checked on resume. */
    val isAccessGranted: Flow<Boolean>

    /**
     * Foreground launches per package bucketed by local hour-of-day for the last [days]
     * days (`hourOfDay -> package -> count`). Empty when access is off.
     */
    suspend fun launchesByHour(days: Int): Map<Int, Map<String, Int>>
}

/** Next calendar event (`READ_CALENDAR`). */
interface CalendarSource {
    /**
     * Whether `READ_CALENDAR` is granted; re-checked whenever an Activity of Reach resumes
     * (the permission dialog and the system settings both send the user away and back).
     * Home uses it to decide whether to offer the optional calendar card.
     */
    val isPermissionGranted: Flow<Boolean>

    /** Next upcoming event today/tomorrow, or null (also when permission is denied). */
    val nextEvent: Flow<CalendarEvent?>
}

/** Contacts for search (`READ_CONTACTS`). */
interface ContactsSource {
    /** Whether `READ_CONTACTS` is granted; search skips contacts entirely when it isn't. */
    fun isPermissionGranted(): Boolean

    /** Contacts whose name matches [query]; empty when permission is denied. */
    suspend fun search(
        query: String,
        limit: Int,
    ): List<Contact>
}

/** Wallpaper colours (`WallpaperManager.getWallpaperColors(FLAG_SYSTEM)`). */
interface WallpaperColorSource {
    /** Primary colour as ARGB, re-emitting when the wallpaper changes; null if unknown. */
    val primaryColor: Flow<Int?>
}

/**
 * Global actions through the optional accessibility service. The user enables it in
 * system settings: `AppLauncher.open(SystemScreen.ACCESSIBILITY_SERVICE)`.
 */
interface ScreenActionsSource {
    /** Whether the user enabled Reach's accessibility service. */
    val isServiceEnabled: Flow<Boolean>

    /** `GLOBAL_ACTION_LOCK_SCREEN`; false when the service is off. */
    fun lockScreen(): Boolean

    /** `GLOBAL_ACTION_NOTIFICATIONS`, or the hidden-API fallback when [allowHiddenApi]. */
    fun openNotificationShade(allowHiddenApi: Boolean): Boolean
}

/**
 * Reach's own display language (Appearance › Language), separate from the phone's.
 * On Android 13+ this is the system's per-app language (also editable in Settings › Apps ›
 * Reach › Language), so [language] is re-read whenever Reach comes back to the foreground.
 */
interface AppLanguageSource {
    /** The chosen language; [AppLanguage.SYSTEM] when Reach follows the phone. */
    val language: Flow<AppLanguage>

    /** Switches Reach's language; the UI re-renders in it right away. */
    fun setLanguage(language: AppLanguage)
}
