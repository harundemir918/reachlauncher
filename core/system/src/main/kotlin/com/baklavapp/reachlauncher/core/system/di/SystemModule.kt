package com.baklavapp.reachlauncher.core.system.di

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
import com.baklavapp.reachlauncher.core.system.launch.SystemAppLauncher
import com.baklavapp.reachlauncher.core.system.profile.UserManagerProfileSource
import com.baklavapp.reachlauncher.core.system.roles.RoleHomeRoleSource
import com.baklavapp.reachlauncher.core.system.roles.TelephonyDefaultAppsSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableCalendarSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableContactsSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableMediaSessionSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableNotificationCountSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableScreenActionsSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableShortcutSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableUsageStatsSource
import com.baklavapp.reachlauncher.core.system.unavailable.UnavailableWallpaperColorSource
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

    /** M3 placeholder. */
    @Binds
    @Singleton
    abstract fun bindNotificationCountSource(impl: UnavailableNotificationCountSource): NotificationCountSource

    /** M3 placeholder. */
    @Binds
    @Singleton
    abstract fun bindMediaSessionSource(impl: UnavailableMediaSessionSource): MediaSessionSource

    /** M2 placeholder. */
    @Binds
    @Singleton
    abstract fun bindShortcutSource(impl: UnavailableShortcutSource): ShortcutSource

    /** M4 placeholder. */
    @Binds
    @Singleton
    abstract fun bindUsageStatsSource(impl: UnavailableUsageStatsSource): UsageStatsSource

    /** M3 placeholder. */
    @Binds
    @Singleton
    abstract fun bindCalendarSource(impl: UnavailableCalendarSource): CalendarSource

    /** M2 placeholder. */
    @Binds
    @Singleton
    abstract fun bindContactsSource(impl: UnavailableContactsSource): ContactsSource

    /** M4 placeholder. */
    @Binds
    @Singleton
    abstract fun bindWallpaperColorSource(impl: UnavailableWallpaperColorSource): WallpaperColorSource

    /** M4 placeholder. */
    @Binds
    @Singleton
    abstract fun bindScreenActionsSource(impl: UnavailableScreenActionsSource): ScreenActionsSource
}
