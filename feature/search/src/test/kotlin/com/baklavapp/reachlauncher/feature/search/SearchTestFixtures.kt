package com.baklavapp.reachlauncher.feature.search

import com.baklavapp.reachlauncher.core.data.domain.LaunchApp
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeAppLauncher
import com.baklavapp.reachlauncher.core.data.fake.FakeContactsSource
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.fake.FakePredictionRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeSettingsRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.Contact
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
    launchCount: Int = 0,
    hidden: Boolean = false,
    paused: Boolean = false,
    profile: ProfileType = ProfileType.PERSONAL,
    userSerial: Long = 0,
    packageName: String = "pkg.${label.lowercase().replace(' ', '.')}",
): CatalogApp =
    CatalogApp(
        key = AppKey(packageName, "$packageName.Main", userSerial),
        label = label,
        profile = profile,
        intent = ReachIntent.TOOLS,
        isUserAssigned = false,
        isHidden = hidden,
        launchCount = launchCount,
        isProfilePaused = paused,
    )

/** A contact with a phone number by default. */
fun contact(
    name: String,
    id: Long = name.hashCode().toLong(),
    phone: String? = "+90 555 000 00 00",
): Contact = Contact(id = id, lookupKey = "lookup-$id", displayName = name, phoneNumber = phone)

/**
 * A [SearchViewModel] wired to fakes, plus the fakes. [dispatcher] is where ranking runs;
 * `Dispatchers.Main` is the test dispatcher in ViewModel tests and Robolectric's main
 * looper in UI tests, so results update deterministically.
 */
class SearchFixture(
    apps: List<CatalogApp> = emptyList(),
    appScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    val catalog = FakeAppCatalogRepository(apps)
    val predictions = FakePredictionRepository()
    val contacts = FakeContactsSource()
    val settings = FakeSettingsRepository()
    val launcher = FakeAppLauncher()
    val history = FakeLaunchHistoryRepository()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-14T09:41:00Z"), ZoneOffset.UTC)

    val viewModel =
        SearchViewModel(
            catalogRepository = catalog,
            predictionRepository = predictions,
            contactsSource = contacts,
            settingsRepository = settings,
            appLauncher = launcher,
            launchApp = LaunchApp(launcher, history, clock, appScope),
            defaultDispatcher = dispatcher,
        )

    val state: SearchUiState get() = viewModel.uiState.value
}
