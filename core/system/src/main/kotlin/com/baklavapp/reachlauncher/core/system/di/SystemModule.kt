package com.baklavapp.reachlauncher.core.system.di

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
import com.baklavapp.reachlauncher.core.system.apps.LauncherAppsInstalledAppsSource
import com.baklavapp.reachlauncher.core.system.calendar.CalendarProviderSource
import com.baklavapp.reachlauncher.core.system.contacts.ContactsProviderSource
import com.baklavapp.reachlauncher.core.system.language.SystemAppLanguageSource
import com.baklavapp.reachlauncher.core.system.launch.SystemAppLauncher
import com.baklavapp.reachlauncher.core.system.media.MediaSessionsSource
import com.baklavapp.reachlauncher.core.system.notifications.NotificationListenerCountSource
import com.baklavapp.reachlauncher.core.system.profile.UserManagerProfileSource
import com.baklavapp.reachlauncher.core.system.roles.RoleHomeRoleSource
import com.baklavapp.reachlauncher.core.system.roles.TelephonyDefaultAppsSource
import com.baklavapp.reachlauncher.core.system.screen.AccessibilityScreenActionsSource
import com.baklavapp.reachlauncher.core.system.shortcuts.LauncherAppsShortcutSource
import com.baklavapp.reachlauncher.core.system.usage.UsageStatsManagerSource
import com.baklavapp.reachlauncher.core.system.wallpaper.WallpaperManagerColorSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every system-source contract of `:core:data` to its `:core:system`
 * implementation, all as process-wide singletons.
 *
 * Flutter analogy: an injectable `@module` registering `@LazySingleton(as: Interface)`
 * implementations; features only ever ask for the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
    /** `LauncherApps`-backed app list. */
    @Binds
    @Singleton
    abstract fun bindInstalledAppsSource(impl: LauncherAppsInstalledAppsSource): InstalledAppsSource

    /** `UserManager`-backed profiles. */
    @Binds
    @Singleton
    abstract fun bindProfileSource(impl: UserManagerProfileSource): ProfileSource

    /** `LauncherApps.startMainActivity` and friends. */
    @Binds
    @Singleton
    abstract fun bindAppLauncher(impl: SystemAppLauncher): AppLauncher

    /** Default dialer/SMS via Telecom/Telephony. */
    @Binds
    @Singleton
    abstract fun bindDefaultAppsSource(impl: TelephonyDefaultAppsSource): DefaultAppsSource

    /** Home role state. */
    @Binds
    @Singleton
    abstract fun bindHomeRoleSource(impl: RoleHomeRoleSource): HomeRoleSource

    /** Unread counts from Reach's `NotificationListenerService`. */
    @Binds
    @Singleton
    abstract fun bindNotificationCountSource(impl: NotificationListenerCountSource): NotificationCountSource

    /** `MediaSessionManager` sessions, readable with notification access. */
    @Binds
    @Singleton
    abstract fun bindMediaSessionSource(impl: MediaSessionsSource): MediaSessionSource

    /** `LauncherApps.getShortcuts`, readable while Reach is the default home app. */
    @Binds
    @Singleton
    abstract fun bindShortcutSource(impl: LauncherAppsShortcutSource): ShortcutSource

    /** `UsageStatsManager` foreground events, with usage access. */
    @Binds
    @Singleton
    abstract fun bindUsageStatsSource(impl: UsageStatsManagerSource): UsageStatsSource

    /** Calendar Provider's next event, with `READ_CALENDAR`. */
    @Binds
    @Singleton
    abstract fun bindCalendarSource(impl: CalendarProviderSource): CalendarSource

    /** Contacts Provider name search, only with `READ_CONTACTS`. */
    @Binds
    @Singleton
    abstract fun bindContactsSource(impl: ContactsProviderSource): ContactsSource

    /** `WallpaperManager.getWallpaperColors`, for wallpaper mode's accent (no permission). */
    @Binds
    @Singleton
    abstract fun bindWallpaperColorSource(impl: WallpaperManagerColorSource): WallpaperColorSource

    /** Lock screen / notification shade through the optional accessibility service (M5). */
    @Binds
    @Singleton
    abstract fun bindScreenActionsSource(impl: AccessibilityScreenActionsSource): ScreenActionsSource

    /** Reach's display language: per-app locales on Android 13+, a stored choice before. */
    @Binds
    @Singleton
    abstract fun bindAppLanguageSource(impl: SystemAppLanguageSource): AppLanguageSource
}
