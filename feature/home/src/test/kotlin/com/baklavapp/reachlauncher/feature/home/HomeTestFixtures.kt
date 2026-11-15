package com.baklavapp.reachlauncher.feature.home

import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeHomeRoleSource
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeNotificationCountSource
import com.baklavapp.reachlauncher.core.data.fake.FakePredictionRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeSettingsRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeShortcutSource
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
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

/**
 * A ViewModel wired to fakes, plus the fakes so tests can drive and inspect them.
 *
 * @param catalogRepository / [historyRepository] replace the default fakes, e.g. with ones
 *   that throw, to test failure handling.
 * @param dispatcher where `uiState` is built. `Dispatchers.Main` is the test dispatcher in
 *   ViewModel tests (`Dispatchers.setMain`) and Robolectric's main looper in UI tests, so
 *   state updates stay synchronous/deterministic; production uses `Dispatchers.Default`.
 */
class HomeFixture(
    apps: List<CatalogApp> = emptyList(),
    catalogRepository: AppCatalogRepository? = null,
    historyRepository: LaunchHistoryRepository? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
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

    /**
     * Stands in for the application scope `LaunchApp` records history in. On `Dispatchers.Main`
     * (the unconfined test dispatcher in ViewModel tests, the main looper in UI tests), so a
     * recorded launch is visible right after the call.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val viewModel =
        HomeViewModel(
            catalogRepository = catalogRepository ?: catalog,
            predictionRepository = predictions,
            settingsRepository = settings,
            notificationCountSource = notifications,
            shortcutSource = shortcuts,
            launchApp = LaunchApp(launcher, shortcuts, historyRepository ?: history, clock, appScope),
            homeRoleSource = homeRole,
            defaultDispatcher = dispatcher,
        )

    /** The data state and the active surface together, as `HomeRoute` sees them. */
    val state: HomeUiState get() = viewModel.uiState.value
    val surface: HomeSurface get() = viewModel.surface.value
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

/** A catalog whose [apps] emits nothing until [release]: models Room not having answered yet. */
class SilentCatalog(
    private val real: FakeAppCatalogRepository,
) : AppCatalogRepository by real {
    private val released = MutableStateFlow(false)

    override val apps: Flow<List<CatalogApp>> = combine(released.filter { it }, real.apps) { _, apps -> apps }

    /** Lets the catalog emit. */
    fun release() {
        released.value = true
    }
}
