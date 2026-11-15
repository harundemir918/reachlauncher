package com.baklavapp.reachlauncher.feature.allapps

import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeProfileSource
import com.baklavapp.reachlauncher.core.data.fake.FakeSettingsRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeShortcutSource
import com.baklavapp.reachlauncher.core.data.source.AppLauncher
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Builds a [CatalogApp] with sensible defaults for tests. */
fun app(
    label: String,
    intent: ReachIntent = ReachIntent.TOOLS,
    profile: ProfileType = ProfileType.PERSONAL,
    hidden: Boolean = false,
    paused: Boolean = false,
): CatalogApp {
    val pkg = "pkg." + label.lowercase().filter { it.isLetterOrDigit() }
    return CatalogApp(
        key = AppKey(pkg, "$pkg.Main", 0),
        label = label,
        profile = profile,
        intent = intent,
        isUserAssigned = false,
        isHidden = hidden,
        launchCount = 0,
        isProfilePaused = paused,
    )
}

/**
 * [FakeAppLauncher] records launches but not App info / Uninstall requests; this wrapper
 * records those too (the shared fake lives in `:core:data`, which this module must not change).
 */
class RecordingAppLauncher(
    val fake: FakeAppLauncher = FakeAppLauncher(),
) : AppLauncher by fake {
    val appInfo = mutableListOf<AppKey>()
    val uninstalled = mutableListOf<AppKey>()

    override fun openAppInfo(key: AppKey) {
        appInfo += key
    }

    override fun requestUninstall(key: AppKey) {
        uninstalled += key
    }
}

/**
 * An [AllAppsViewModel] wired to fakes, plus the fakes.
 *
 * @param appScope stands in for the application scope `LaunchApp` records history in.
 * @param dispatcher where `uiState` is built; Main (the test dispatcher / Robolectric's
 *   main looper) keeps tests deterministic.
 */
class AllAppsFixture(
    apps: List<CatalogApp> = emptyList(),
    appScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    val catalog = FakeAppCatalogRepository(apps)
    val settings = FakeSettingsRepository()
    val history = FakeLaunchHistoryRepository()
    val launcher = RecordingAppLauncher()
    val profiles = FakeProfileSource()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-08T09:41:00Z"), ZoneOffset.UTC)

    val viewModel =
        AllAppsViewModel(
            catalogRepository = catalog,
            settingsRepository = settings,
            launchApp = LaunchApp(launcher, FakeShortcutSource(), history, clock, appScope),
            appLauncher = launcher,
            profileSource = profiles,
            defaultDispatcher = dispatcher,
        )
}

/** A small catalog spanning several letters. */
val sampleApps: List<CatalogApp> =
    listOf(
        app("Maps"),
        app("Messages"),
        app("Settings"),
        app("Signal"),
        app("Slack", profile = ProfileType.WORK, paused = true),
        app("Spotify"),
        app("WhatsApp"),
        app("YouTube"),
        app("Chess", hidden = true),
    )
