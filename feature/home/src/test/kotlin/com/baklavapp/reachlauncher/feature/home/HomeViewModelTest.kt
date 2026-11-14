package com.baklavapp.reachlauncher.feature.home

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.model.AppShortcut
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.PredictionReason
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HomeViewModel]: the surface state machine, how repository data becomes
 * [HomeUiState], and launching.
 *
 * Flutter analogy: `bloc_test` — build the BLoC with fakes, act, expect states. Turbine's
 * `test { awaitItem() }` plays the role of `expect: [...]`. `Dispatchers.setMain` swaps
 * Android's main thread for a test dispatcher, because `viewModelScope` runs on Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Keeps uiState subscribed (WhileSubscribed) for the test's duration. */
    private fun TestScope.subscribe(fixture: HomeFixture) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.uiState.collect {} }
    }

    @Test
    fun `initial state renders immediately with all six intents`() {
        val f = HomeFixture()
        val state = f.state
        assertThat(f.surface).isEqualTo(HomeSurface.Home)
        assertThat(state.intents.map { it.intent }).containsExactlyElementsIn(ReachIntent.entries).inOrder()
        assertThat(state.now).isEmpty()
        assertThat(state.details).isEmpty()
    }

    @Test
    fun `surface transitions and back`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            val vm = f.viewModel

            vm.onIntentSelected(ReachIntent.TALK)
            assertThat(f.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))
            vm.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)

            vm.onSearchRequested()
            assertThat(f.surface).isEqualTo(HomeSurface.Search)
            vm.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)

            vm.onAllAppsRequested()
            assertThat(f.surface).isEqualTo(HomeSurface.AllApps)
            vm.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)

            vm.onAppearanceRequested()
            assertThat(f.surface).isEqualTo(HomeSurface.Appearance)
            vm.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)

            // Back on Home does nothing.
            vm.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `chip switches intent in place and back still returns home`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.viewModel.onIntentSelected(ReachIntent.TALK)
            f.viewModel.onIntentSelected(ReachIntent.LISTEN)
            assertThat(f.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.LISTEN))
            assertThat(
                f.state
                    .detailFor(ReachIntent.LISTEN)
                    .apps
                    .map { it.app.label },
            ).containsExactly("Spotify")
            f.viewModel.onBack()
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `home pressed returns home from anywhere and hides the keyboard`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.viewModel.events.test {
                f.viewModel.onSearchRequested()
                f.viewModel.onHomePressed()
                assertThat(awaitItem()).isEqualTo(HomeEvent.HideKeyboard)
                assertThat(f.surface).isEqualTo(HomeSurface.Home)

                f.viewModel.onIntentSelected(ReachIntent.GO)
                f.viewModel.onHomePressed()
                assertThat(awaitItem()).isEqualTo(HomeEvent.HideKeyboard)
                assertThat(f.surface).isEqualTo(HomeSurface.Home)
                // Leaving a sub-surface doesn't ask to scroll: a fresh Home starts at the top.
                expectNoEvents()
            }
        }

    @Test
    fun `home pressed on Home also scrolls Home to the top`() =
        runTest {
            val f = HomeFixture(sampleApps)
            f.viewModel.events.test {
                f.viewModel.onHomePressed()
                assertThat(awaitItem()).isEqualTo(HomeEvent.HideKeyboard)
                assertThat(awaitItem()).isEqualTo(HomeEvent.ScrollToTop)
            }
        }

    @Test
    fun `home pressed while stopped shows Home on the first resumed frame`() =
        runTest {
            val f = HomeFixture(sampleApps)
            val ui = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.viewModel.uiState.collect {} }
            f.viewModel.onIntentSelected(ReachIntent.TALK)
            assertThat(f.state.catalogLoaded).isTrue()

            // Reach goes to the background: the UI stops collecting and, after
            // WhileSubscribed's 5 s, the uiState pipeline is stopped.
            ui.cancel()
            advanceTimeBy(6_000)
            f.viewModel.onHomePressed()

            // The first value a resubscribing HomeRoute reads is already Home.
            assertThat(f.viewModel.surface.first()).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `before the catalog arrives, the state says so`() =
        runTest {
            val catalog = FakeAppCatalogRepository(sampleApps)
            val silent = SilentCatalog(catalog)
            val f = HomeFixture(catalogRepository = silent)
            subscribe(f)
            assertThat(f.state.catalogLoaded).isFalse()
            assertThat(f.state.now).isEmpty()

            silent.release()
            assertThat(f.state.catalogLoaded).isTrue()
            assertThat(
                f.state.intents
                    .first { it.intent == ReachIntent.TALK }
                    .appCount,
            ).isEqualTo(3)
        }

    @Test
    fun `successful launch records history and returns home`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            val maps = sampleApps.first { it.label == "Maps" }
            f.viewModel.onIntentSelected(ReachIntent.GO)

            f.viewModel.launch(maps, LaunchOrigin(1, 2, 3, 4))

            assertThat(f.launcher.launched).containsExactly(maps.key)
            val events = f.history.events.value
            assertThat(events.map { it.key }).containsExactly(maps.key)
            assertThat(events.single().epochMillis).isEqualTo(f.clock.millis())
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `failed launch records nothing, stays, and reports the app`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.launcher.result = LaunchResult.FAILED
            val maps = sampleApps.first { it.label == "Maps" }
            f.viewModel.onIntentSelected(ReachIntent.GO)
            f.viewModel.events.test {
                f.viewModel.launch(maps, null)
                assertThat(awaitItem()).isEqualTo(HomeEvent.LaunchFailed("Maps"))
            }
            assertThat(f.history.events.value).isEmpty()
            assertThat(f.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.GO))
        }

    @Test
    fun `resuming a paused profile records nothing and stays`() =
        runTest {
            // The catalog still thinks the profile is running: only the live result counts.
            val outlook = app("Outlook", ReachIntent.TOOLS, userSerial = 10, profile = ProfileType.WORK, paused = false)
            val f = HomeFixture(listOf(outlook))
            subscribe(f)
            f.launcher.result = LaunchResult.RESUMING_PROFILE
            f.viewModel.onIntentSelected(ReachIntent.TOOLS)
            f.viewModel.events.test {
                f.viewModel.launch(outlook, null)
                expectNoEvents()
            }
            assertThat(f.launcher.launched).containsExactly(outlook.key)
            assertThat(f.history.events.value).isEmpty()
            assertThat(f.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TOOLS))
        }

    @Test
    fun `a stale paused flag doesn't block a real launch`() =
        runTest {
            // The profile was resumed a moment ago; the catalog hasn't caught up yet.
            val outlook = app("Outlook", ReachIntent.TOOLS, userSerial = 10, profile = ProfileType.WORK, paused = true)
            val f = HomeFixture(listOf(outlook))
            subscribe(f)
            f.viewModel.onIntentSelected(ReachIntent.TOOLS)
            f.viewModel.launch(outlook, null)
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(outlook.key)
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `unread counts are summed per intent and kept apart per profile`() =
        runTest {
            val personal = app("WhatsApp", ReachIntent.TALK, packageName = "com.whatsapp")
            val work = app("WhatsApp", ReachIntent.TALK, packageName = "com.whatsapp", userSerial = 10, profile = ProfileType.WORK)
            // Two launcher activities of one package share one notification count.
            val sms = app("Messages", ReachIntent.TALK, packageName = "com.sms", activityName = "com.sms.A")
            val sms2 = app("Messages Lite", ReachIntent.TALK, packageName = "com.sms", activityName = "com.sms.B")
            val spotify = app("Spotify", ReachIntent.LISTEN, packageName = "com.spotify")
            val f = HomeFixture(listOf(personal, work, sms, sms2, spotify))
            subscribe(f)
            f.notifications.granted.value = true
            f.notifications.counts.value =
                mapOf(
                    PackageUser("com.whatsapp", 0) to 2,
                    PackageUser("com.whatsapp", 10) to 3,
                    PackageUser("com.sms", 0) to 1,
                )

            val talk = f.state.intents.first { it.intent == ReachIntent.TALK }
            assertThat(talk.unreadCount).isEqualTo(6)
            assertThat(talk.appCount).isEqualTo(4)
            assertThat(
                f.state
                    .intents
                    .first { it.intent == ReachIntent.LISTEN }
                    .unreadCount,
            ).isEqualTo(0)

            f.viewModel.onIntentSelected(ReachIntent.TALK)
            val perApp =
                f.state
                    .detailFor(ReachIntent.TALK)
                    .apps
                    .associate { it.app.key to it.unreadCount }
            assertThat(perApp[personal.key]).isEqualTo(2)
            assertThat(perApp[work.key]).isEqualTo(3)
            assertThat(perApp[sms.key]).isEqualTo(1)
        }

    @Test
    fun `no notification access means no badges`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.notifications.counts.value = mapOf(PackageUser("pkg.whatsapp", 0) to 4)
            assertThat(f.state.intents.sumOf { it.unreadCount }).isEqualTo(0)
        }

    @Test
    fun `detail is sorted by launch frequency then alphabetically`() =
        runTest {
            val f =
                HomeFixture(
                    listOf(
                        app("Zoom", ReachIntent.TALK, launchCount = 1),
                        app("Signal", ReachIntent.TALK, launchCount = 7),
                        app("Ayşe", ReachIntent.TALK, launchCount = 1),
                        app("Mail", ReachIntent.TALK, launchCount = 0),
                        app("Discord", ReachIntent.TALK, launchCount = 7),
                    ),
                )
            subscribe(f)
            f.viewModel.onIntentSelected(ReachIntent.TALK)
            assertThat(
                f.state
                    .detailFor(ReachIntent.TALK)
                    .apps
                    .map { it.app.label },
            ).containsExactly("Discord", "Signal", "Ayşe", "Zoom", "Mail")
                .inOrder()
        }

    @Test
    fun `hidden apps are excluded from counts and detail, paused apps are flagged`() =
        runTest {
            val f =
                HomeFixture(
                    listOf(
                        app("Visible", ReachIntent.MAKE),
                        app("Secret", ReachIntent.MAKE, hidden = true),
                        app("Docs", ReachIntent.MAKE, userSerial = 10, profile = ProfileType.WORK, paused = true),
                    ),
                )
            subscribe(f)
            assertThat(
                f.state
                    .intents
                    .first { it.intent == ReachIntent.MAKE }
                    .appCount,
            ).isEqualTo(2)
            f.viewModel.onIntentSelected(ReachIntent.MAKE)
            val apps = f.state.detailFor(ReachIntent.MAKE).apps
            assertThat(apps.map { it.app.label }).containsExactly("Docs", "Visible").inOrder()
            assertThat(apps.first { it.app.label == "Docs" }.app.isProfilePaused).isTrue()
        }

    @Test
    fun `intents with no apps still show with count zero`() =
        runTest {
            val f = HomeFixture(listOf(app("Maps", ReachIntent.GO)))
            subscribe(f)
            assertThat(f.state.intents).hasSize(6)
            assertThat(
                f.state
                    .intents
                    .first { it.intent == ReachIntent.PLAY }
                    .appCount,
            ).isEqualTo(0)
        }

    @Test
    fun `settings flow through`() =
        runTest {
            val f = HomeFixture()
            subscribe(f)
            f.settings.setThumbSide(ThumbSide.RIGHT)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.viewModel.theme.collect {} }
            f.settings.setThemeMode(ThemeMode.LIGHT)
            f.settings.setPalette(PaletteId.DUSK)
            assertThat(f.state.thumbSide).isEqualTo(ThumbSide.RIGHT)
            assertThat(f.viewModel.theme.value).isEqualTo(HomeTheme(ThemeMode.LIGHT, PaletteId.DUSK))
        }

    @Test
    fun `now row is limited to three`() =
        runTest {
            val f = HomeFixture(sampleApps)
            subscribe(f)
            f.predictions.state.value = sampleApps.take(5).map { Prediction(it, PredictionReason.Recent, score = 1.0) }
            val now =
                f.viewModel.uiState
                    .first { it.now.isNotEmpty() }
                    .now
            assertThat(now.map { it.app.label }).containsExactly("WhatsApp", "Messages", "Phone").inOrder()
        }

    @Test
    fun `shortcuts appear only with host permission, max four, from the top apps`() =
        runTest {
            val top = app("Signal", ReachIntent.TALK, launchCount = 9, packageName = "org.signal")
            val second = app("WhatsApp", ReachIntent.TALK, launchCount = 3, packageName = "com.whatsapp")
            val f = HomeFixture(listOf(top, second))
            subscribe(f)

            fun shortcut(
                id: String,
                pkg: String,
            ) = AppShortcut(id, PackageUser(pkg, 0), null, "Shortcut $id", pkg)
            f.shortcuts.available.value =
                listOf(
                    shortcut("w1", "com.whatsapp"),
                    shortcut("w2", "com.whatsapp"),
                    shortcut("s1", "org.signal"),
                    shortcut("s2", "org.signal"),
                    shortcut("s3", "org.signal"),
                    shortcut("x", "com.other"),
                )
            f.viewModel.onIntentSelected(ReachIntent.TALK)
            assertThat(f.state.detailFor(ReachIntent.TALK).shortcuts).isEmpty()

            f.shortcuts.granted.value = true
            val ids =
                f.state
                    .detailFor(ReachIntent.TALK)
                    .shortcuts
                    .map { it.shortcut.id }
            assertThat(ids).containsExactly("s1", "s2", "s3", "w1").inOrder()

            val item =
                f.state
                    .detailFor(ReachIntent.TALK)
                    .shortcuts
                    .first()
            f.viewModel.launchShortcut(item, null)
            assertThat(f.shortcuts.launched).containsExactly(item.shortcut)
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(top.key)
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `launch failures surface the label, shortcut failures too`() =
        runTest {
            val f = HomeFixture(sampleApps)
            f.launcher.result = LaunchResult.FAILED
            f.viewModel.events.test {
                f.viewModel.launch(sampleApps.first(), null)
                assertThat(awaitItem()).isEqualTo(HomeEvent.LaunchFailed("WhatsApp"))
                val shortcut = AppShortcut("x", PackageUser("pkg.whatsapp", 0), null, "Call Ayşe", "WhatsApp")
                f.viewModel.launchShortcut(ShortcutItem(shortcut, sampleApps.first().key), null)
                assertThat(awaitItem()).isEqualTo(HomeEvent.LaunchFailed("Call Ayşe"))
            }
        }

    @Test
    fun `resume refreshes the catalog and rechecks the home role`() =
        runTest {
            val f = HomeFixture()
            f.viewModel.onResume()
            assertThat(f.catalog.refreshCount).isEqualTo(1)
            assertThat(f.homeRole.recheckCount).isEqualTo(1)
        }
}
