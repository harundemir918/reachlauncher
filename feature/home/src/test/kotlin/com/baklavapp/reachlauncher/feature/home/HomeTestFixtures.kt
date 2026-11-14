package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeHomeRoleSource
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeNotificationCountSource
import com.baklavapp.reachlauncher.core.data.fake.FakePredictionRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeSettingsRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeShortcutSource
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Builds a [CatalogApp] with sensible defaults for tests. */
fun app(
    label: String,
    intent: ReachIntent,
    launchCount: Int = 0,
    userSerial: Long = 0,
    profile: ProfileType = ProfileType.PERSONAL,
    hidden: Boolean = false,
    paused: Boolean = false,
    packageName: String = "pkg.${label.lowercase().replace(' ', '.')}",
    activityName: String = "$packageName.Main",
): CatalogApp =
    CatalogApp(
        key = AppKey(packageName, activityName, userSerial),
        label = label,
        profile = profile,
        intent = intent,
        isUserAssigned = false,
        isHidden = hidden,
        launchCount = launchCount,
        isProfilePaused = paused,
    )

/** A ViewModel wired to fakes, plus the fakes so tests can drive and inspect them. */
class HomeFixture(
    apps: List<CatalogApp> = emptyList(),
) {
    val catalog = FakeAppCatalogRepository(apps)
    val history = FakeLaunchHistoryRepository()
    val predictions = FakePredictionRepository()
    val settings = FakeSettingsRepository()
    val notifications = FakeNotificationCountSource()
    val shortcuts = FakeShortcutSource()
    val launcher = FakeAppLauncher()
    val homeRole = FakeHomeRoleSource()
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-08T09:41:00Z"), ZoneOffset.UTC)

    val viewModel =
        HomeViewModel(
            catalogRepository = catalog,
            historyRepository = history,
            predictionRepository = predictions,
            settingsRepository = settings,
            notificationCountSource = notifications,
            shortcutSource = shortcuts,
            appLauncher = launcher,
            homeRoleSource = homeRole,
            clock = clock,
        )
}

/** A small catalog spanning several intents, used by the UI tests. */
val sampleApps: List<CatalogApp> =
    listOf(
        app("WhatsApp", ReachIntent.TALK, launchCount = 5),
        app("Messages", ReachIntent.TALK, launchCount = 2),
        app("Phone", ReachIntent.TALK),
        app("Spotify", ReachIntent.LISTEN),
        app("Maps", ReachIntent.GO),
        app("Notes", ReachIntent.MAKE),
        app("Chess", ReachIntent.PLAY),
        app("Settings", ReachIntent.TOOLS),
    )
