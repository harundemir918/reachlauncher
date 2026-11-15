package com.baklavapp.reachlauncher.core.system.unavailable

import com.baklavapp.reachlauncher.core.data.source.CalendarSource
import com.baklavapp.reachlauncher.core.data.source.MediaSessionSource
import com.baklavapp.reachlauncher.core.data.source.NotificationCountSource
import com.baklavapp.reachlauncher.core.data.source.ScreenActionsSource
import com.baklavapp.reachlauncher.core.data.source.UsageStatsSource
import com.baklavapp.reachlauncher.core.data.source.WallpaperColorSource
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.core.model.PackageUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/*
 * Placeholder sources for integrations of later milestones. Each reports exactly what the
 * real one reports when its permission is denied, so the app already behaves correctly
 * (every feature degrades gracefully) and the Hilt graph is complete. Replace the binding
 * in SystemModule when the real implementation lands.
 *
 * Flutter analogy: registering a no-op implementation in get_it until the real plugin
 * integration exists.
 */

/** Notification access off. Replaced in M3 by a `NotificationListenerService`-backed source. */
class UnavailableNotificationCountSource
    @Inject
    constructor() : NotificationCountSource {
        override val isAccessGranted: Flow<Boolean> = flowOf(false)
        override val countsByApp: Flow<Map<PackageUser, Int>> = flowOf(emptyMap())
    }

/** Nothing playing. Replaced in M3 by a `MediaSessionManager`-backed source. */
class UnavailableMediaSessionSource
    @Inject
    constructor() : MediaSessionSource {
        override val mediaState: Flow<MediaState?> = flowOf(null)

        override fun playPause() = Unit

        override fun skipToNext() = Unit

        override fun skipToPrevious() = Unit
    }

/** Usage access off. Replaced in M3 by a `UsageStatsManager`-backed source. */
class UnavailableUsageStatsSource
    @Inject
    constructor() : UsageStatsSource {
        override val isAccessGranted: Flow<Boolean> = flowOf(false)

        override suspend fun launchesByHour(days: Int): Map<Int, Map<String, Int>> = emptyMap()
    }

/** No calendar permission. Replaced in M3 by a `CalendarContract`-backed source. */
class UnavailableCalendarSource
    @Inject
    constructor() : CalendarSource {
        override val nextEvent: Flow<CalendarEvent?> = flowOf(null)
    }

/** Wallpaper colour unknown. Replaced in M4 (wallpaper mode) by a `WallpaperManager` source. */
class UnavailableWallpaperColorSource
    @Inject
    constructor() : WallpaperColorSource {
        override val primaryColor: Flow<Int?> = flowOf(null)
    }

/** Accessibility service off. Replaced in M5 by the opt-in accessibility service. */
class UnavailableScreenActionsSource
    @Inject
    constructor() : ScreenActionsSource {
        override val isServiceEnabled: Flow<Boolean> = flowOf(false)

        override fun lockScreen(): Boolean = false

        override fun openNotificationShade(allowHiddenApi: Boolean): Boolean = false
    }
