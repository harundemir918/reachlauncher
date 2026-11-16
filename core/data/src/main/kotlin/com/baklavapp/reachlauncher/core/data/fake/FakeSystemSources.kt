package com.baklavapp.reachlauncher.core.data.fake

import com.baklavapp.reachlauncher.core.data.source.AppLanguageSource
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.data.source.CalendarSource
import com.baklavapp.reachlauncher.core.data.source.ContactsSource
import com.baklavapp.reachlauncher.core.data.source.DefaultAppsSource
import com.baklavapp.reachlauncher.core.data.source.HomeRoleSource
import com.baklavapp.reachlauncher.core.data.source.InstalledAppsSource
import com.baklavapp.reachlauncher.core.data.source.MediaSessionSource
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.data.source.ProfileSource
import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import com.baklavapp.reachlauncher.core.data.source.ShortcutSource
import com.baklavapp.reachlauncher.core.data.source.UsageStatsSource
import com.baklavapp.reachlauncher.core.data.source.WallpaperColorSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.AppLanguage
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.SystemScreen
import com.baklavapp.reachlauncher.core.model.TextFolding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/*
 * In-memory fakes of the system-source contracts. Defaults model "every optional
 * permission denied", which is the state Reach must always handle gracefully. Fakes
 * gate their data on their permission flag exactly as the interfaces promise.
 *
 * Flutter analogy: fake platform plugins — like registering a mock MethodChannel
 * handler in a widget test, but as plain classes with observable state.
 */

/** Fake installed apps; set [state] to simulate installs and removals. */
class FakeInstalledAppsSource(
    initial: List<InstalledApp> = emptyList(),
) : InstalledAppsSource {
    /** Current installed apps. */
    val state = MutableStateFlow(initial)
    override val installedApps: Flow<List<InstalledApp>> = state
}

/** Fake profiles: just the personal profile by default. */
class FakeProfileSource(
    initial: List<ProfileState> = listOf(ProfileState(userSerial = 0, type = ProfileType.PERSONAL, isQuietModeEnabled = false)),
) : ProfileSource {
    /** Current profiles; [setQuietMode] updates it. */
    val state = MutableStateFlow(initial)
    override val profiles: Flow<List<ProfileState>> = state

    override fun setQuietMode(
        userSerial: Long,
        enabled: Boolean,
    ): Boolean {
        state.update { list -> list.map { if (it.userSerial == userSerial) it.copy(isQuietModeEnabled = enabled) else it } }
        return true
    }
}

/** Records every launch, screen and hand-off request so tests can assert on them. */
class FakeAppLauncher : AppLauncher {
    /** Apps passed to [launchApp], in order. */
    val launched = mutableListOf<AppKey>()

    /** Screens passed to [open], in order. */
    val openedScreens = mutableListOf<SystemScreen>()

    /** Actions passed to [perform], in order. */
    val performed = mutableListOf<ExternalAction>()

    /** What [launchApp] returns; set FAILED to simulate a removed app, RESUMING_PROFILE for a paused profile. */
    var result: LaunchResult = LaunchResult.LAUNCHED

    override fun launchApp(
        key: AppKey,
        origin: LaunchOrigin?,
    ): LaunchResult {
        launched += key
        return result
    }

    /** Apps passed to [openAppInfo], in order. */
    val appInfoOpened = mutableListOf<AppKey>()

    /** Apps passed to [requestUninstall], in order. */
    val uninstallRequested = mutableListOf<AppKey>()

    override fun openAppInfo(key: AppKey) {
        appInfoOpened += key
    }

    override fun requestUninstall(key: AppKey) {
        uninstallRequested += key
    }

    override fun open(screen: SystemScreen): Boolean {
        openedScreens += screen
        return true
    }

    override fun perform(action: ExternalAction): Boolean {
        performed += action
        return true
    }
}

/** Fake dialer/SMS role holders. */
class FakeDefaultAppsSource(
    /** Packages returned by [talkRolePackages]. */
    var packages: Set<String> = emptySet(),
) : DefaultAppsSource {
    override suspend fun talkRolePackages(): Set<String> = packages
}

/** Fake home role; Reach is *not* default home by default. */
class FakeHomeRoleSource(
    isDefault: Boolean = false,
) : HomeRoleSource {
    /** Whether Reach is default home. */
    val state = MutableStateFlow(isDefault)
    override val isDefaultHome: Flow<Boolean> = state

    /** How many times [recheck] was called. */
    var recheckCount = 0
        private set

    override fun recheck() {
        recheckCount++
    }
}

/** Fake notification counts; [counts] are only visible while [granted] is true. */
class FakeNotificationCountSource : NotificationCountSource {
    /** Whether notification access is granted. */
    val granted = MutableStateFlow(false)

    /** Counts the listener would report if access were granted. */
    val counts = MutableStateFlow<Map<PackageUser, Int>>(emptyMap())
    override val isAccessGranted: Flow<Boolean> = granted
    override val countsByApp: Flow<Map<PackageUser, Int>> = combine(granted, counts) { g, c -> if (g) c else emptyMap() }
}

/** Fake media session; [commands] records transport calls. */
class FakeMediaSessionSource : MediaSessionSource {
    /** The active session, or null for "nothing playing". */
    val state = MutableStateFlow<MediaState?>(null)
    override val mediaState: Flow<MediaState?> = state

    /** Transport commands received, in order ("playPause", "next", "previous"). */
    val commands = mutableListOf<String>()

    override fun playPause() {
        commands += "playPause"
        state.update { it?.copy(isPlaying = !it.isPlaying) }
    }

    override fun skipToNext() {
        commands += "next"
    }

    override fun skipToPrevious() {
        commands += "previous"
    }
}

/** Fake shortcuts; [available] are only visible (and launchable) while [granted] is true. */
class FakeShortcutSource : ShortcutSource {
    /** Whether Reach holds the shortcut host permission (is default home). */
    val granted = MutableStateFlow(false)

    /** Shortcuts the system would report with permission. */
    val available = MutableStateFlow<List<AppShortcut>>(emptyList())

    /** Shortcuts passed to a successful [launch], in order. */
    val launched = mutableListOf<AppShortcut>()

    override val hasHostPermission: Flow<Boolean> = granted
    override val shortcuts: Flow<List<AppShortcut>> = combine(granted, available) { g, s -> if (g) s else emptyList() }

    override fun launch(
        shortcut: AppShortcut,
        origin: LaunchOrigin?,
    ): Boolean {
        if (!granted.value || shortcut !in available.value) return false
        launched += shortcut
        return true
    }
}

/** Fake usage stats; [data] is only returned while [granted] is true. */
class FakeUsageStatsSource : UsageStatsSource {
    /** Whether usage access is granted. */
    val granted = MutableStateFlow(false)

    /** `hourOfDay -> package -> count` returned with access. */
    var data: Map<Int, Map<String, Int>> = emptyMap()
    override val isAccessGranted: Flow<Boolean> = granted

    override suspend fun launchesByHour(days: Int): Map<Int, Map<String, Int>> = if (granted.value) data else emptyMap()
}

/** Fake calendar; [state] is only visible while [granted] is true (denied by default). */
class FakeCalendarSource : CalendarSource {
    /** Whether `READ_CALENDAR` is granted. */
    val granted = MutableStateFlow(false)

    /** The next event the provider would report with permission, or null. */
    val state = MutableStateFlow<CalendarEvent?>(null)
    override val isPermissionGranted: Flow<Boolean> = granted
    override val nextEvent: Flow<CalendarEvent?> = combine(granted, state) { g, e -> if (g) e else null }
}

/** Fake contacts; [contacts] are only searchable while [granted] is true. */
class FakeContactsSource(
    /** Whether `READ_CONTACTS` is granted. */
    var granted: Boolean = false,
    /** Contacts searchable with permission. */
    var contacts: List<Contact> = emptyList(),
) : ContactsSource {
    override fun isPermissionGranted(): Boolean = granted

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<Contact> {
        if (!granted) return emptyList()
        val folded = TextFolding.fold(query)
        return contacts.filter { TextFolding.fold(it.displayName).contains(folded) }.take(limit)
    }
}

/** Fake wallpaper colour; null (unknown) by default. */
class FakeWallpaperColorSource : WallpaperColorSource {
    /** Primary wallpaper colour as ARGB, or null. */
    val state = MutableStateFlow<Int?>(null)
    override val primaryColor: Flow<Int?> = state
}

/** Fake accessibility service; actions only succeed while [enabled] is true. */
class FakeScreenActionsSource : ScreenActionsSource {
    /** Whether the accessibility service is enabled. */
    val enabled = MutableStateFlow(false)
    override val isServiceEnabled: Flow<Boolean> = enabled

    /** Successful [lockScreen] calls. */
    var lockCount = 0
        private set

    /** Successful [openNotificationShade] calls. */
    var shadeCount = 0
        private set

    override fun lockScreen(): Boolean {
        if (enabled.value) lockCount++
        return enabled.value
    }

    /** Whether the hidden-API fallback would work on this "device" (only tried when allowed). */
    var hiddenApiWorks = false

    /** The `allowHiddenApi` argument of the last [openNotificationShade] call, or null. */
    var lastAllowHiddenApi: Boolean? = null
        private set

    override fun openNotificationShade(allowHiddenApi: Boolean): Boolean {
        lastAllowHiddenApi = allowHiddenApi
        val opened = enabled.value || (allowHiddenApi && hiddenApiWorks)
        if (opened) shadeCount++
        return opened
    }
}

/** [AppLanguageSource] backed by a [MutableStateFlow]; [setLanguage] just stores the choice. */
class FakeAppLanguageSource : AppLanguageSource {
    val current = MutableStateFlow(AppLanguage.SYSTEM)
    override val language: Flow<AppLanguage> = current

    override fun setLanguage(language: AppLanguage) {
        current.value = language
    }
}
