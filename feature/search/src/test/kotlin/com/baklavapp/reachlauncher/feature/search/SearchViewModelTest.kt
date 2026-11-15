package com.baklavapp.reachlauncher.feature.search

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.ExternalAction
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [SearchViewModel] with fakes: what Enter, taps and commands do, and when contacts are asked.
 *
 * Flutter analogy: `bloc_test` — build with fakes, act, expect states/effects. Turbine's
 * `events.test { awaitItem() }` ≈ `expect: [...]`; `Dispatchers.setMain` swaps Android's
 * main thread for a test dispatcher because `viewModelScope` runs on Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.fixture(vararg apps: com.baklavapp.reachlauncher.core.model.CatalogApp): SearchFixture {
        val f = SearchFixture(apps.toList(), appScope = backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.viewModel.uiState.collect {} }
        return f
    }

    @Test
    fun `Enter launches the top result through LaunchApp, records it and closes`() =
        runTest {
            val spotify = app("Spotify")
            val f = fixture(spotify, app("Signal"))
            f.viewModel.events.test {
                f.viewModel.onQueryChange("spo")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
            }
            assertThat(f.launcher.launched).containsExactly(spotify.key)
            runCurrent() // LaunchApp records in the application scope.
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(spotify.key)
        }

    @Test
    fun `a paused work profile is resumed and search stays open`() =
        runTest {
            val mail = app("Mail", paused = true)
            val f = fixture(mail)
            f.launcher.result = LaunchResult.RESUMING_PROFILE
            f.viewModel.events.test {
                f.viewModel.onQueryChange("mail")
                f.viewModel.onSubmit(origin = null)
                expectNoEvents()
            }
            assertThat(f.history.events.value).isEmpty()
        }

    @Test
    fun `a failed launch reports the label and records nothing`() =
        runTest {
            val f = fixture(app("Maps"))
            f.launcher.result = LaunchResult.FAILED
            f.viewModel.events.test {
                f.viewModel.onQueryChange("maps")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.LaunchFailed("Maps"))
            }
            assertThat(f.history.events.value).isEmpty()
        }

    @Test
    fun `a shortcut launch is recorded against its app`() =
        runTest {
            val whatsapp = app("WhatsApp")
            val f = fixture(whatsapp)
            val message = shortcut("Message Ayşe", whatsapp)
            f.shortcuts.granted.value = true
            f.shortcuts.available.value = listOf(message)
            f.viewModel.events.test {
                f.viewModel.onQueryChange("message ay")
                val row = f.state.results.first()
                assertThat(row).isInstanceOf(SearchResult.ShortcutItem::class.java)
                f.viewModel.onResultClick(row, origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
            }
            assertThat(f.shortcuts.launched).containsExactly(message)
            runCurrent()
            assertThat(
                f.history.events.value
                    .map { it.key },
            ).containsExactly(whatsapp.key)
        }

    @Test
    fun `palette and theme commands update settings and close`() =
        runTest {
            val f = fixture()
            f.viewModel.events.test {
                f.viewModel.onQueryChange("forest")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
                f.viewModel.onQueryChange("koyu")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
            }
            assertThat(f.settings.state.value.palette).isEqualTo(PaletteId.FOREST)
            assertThat(f.settings.state.value.themeMode).isEqualTo(ThemeMode.DARK)
        }

    @Test
    fun `appearance command asks to open the sheet without changing settings`() =
        runTest {
            val f = fixture()
            val before = f.settings.state.value
            f.viewModel.events.test {
                f.viewModel.onQueryChange("görünüm")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.OpenAppearance)
            }
            assertThat(f.settings.state.value).isEqualTo(before)
        }

    @Test
    fun `web fallback hands off the query`() =
        runTest {
            val f = fixture()
            f.viewModel.events.test {
                f.viewModel.onQueryChange("weather istanbul")
                f.viewModel.onSubmit(origin = null)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
            }
            assertThat(f.launcher.performed).containsExactly(ExternalAction.WebSearch("weather istanbul"))
        }

    @Test
    fun `contacts are never queried without permission and the prompt shows from two letters`() =
        runTest {
            val f = fixture()
            f.contacts.contacts = listOf(contact("Ayşe Yılmaz"))
            f.viewModel.onQueryChange("a")
            advanceTimeBy(500)
            assertThat(f.state.showContactsPrompt).isFalse()
            f.viewModel.onQueryChange("ay")
            advanceTimeBy(500)
            assertThat(f.state.results.filterIsInstance<SearchResult.ContactItem>()).isEmpty()
            assertThat(f.state.showContactsPrompt).isTrue()

            // Declined: the prompt goes away and stays away.
            f.viewModel.onContactsPermissionResult(granted = false)
            assertThat(f.state.showContactsPrompt).isFalse()
            f.viewModel.onQueryChange("ayş")
            assertThat(f.state.showContactsPrompt).isFalse()
        }

    @Test
    fun `granted contacts appear after the debounce and open a chooser`() =
        runTest {
            val f = fixture()
            val ayse = contact("Ayşe Yılmaz", phone = "+905550000000")
            f.contacts.contacts = listOf(ayse)
            f.viewModel.onQueryChange("ayse")
            advanceTimeBy(500)
            assertThat(f.state.results.filterIsInstance<SearchResult.ContactItem>()).isEmpty()

            f.contacts.granted = true
            f.viewModel.onContactsPermissionResult(granted = true)
            advanceTimeBy(500)
            val row = f.state.results.first()
            assertThat(row).isEqualTo(SearchResult.ContactItem(ayse))
            assertThat(f.state.showContactsPrompt).isFalse()

            // Enter on a contact opens its chooser instead of acting right away.
            f.viewModel.onSubmit(origin = null)
            assertThat(f.state.expandedContactId).isEqualTo(ayse.id)
            f.viewModel.events.test {
                f.viewModel.onContactAction(ayse, ContactAction.MESSAGE)
                assertThat(awaitItem()).isEqualTo(SearchEvent.Close)
            }
            assertThat(f.launcher.performed).containsExactly(ExternalAction.Message("+905550000000"))
        }

    @Test
    fun `opening search starts empty with suggestions`() =
        runTest {
            val f = fixture(app("Spotify"))
            f.viewModel.onQueryChange("spo")
            f.viewModel.onClosed()
            f.viewModel.onOpened()
            assertThat(f.state.query).isEmpty()
            assertThat(f.state.isSuggestions).isTrue()
        }
}
