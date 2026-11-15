package com.baklavapp.reachlauncher.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.feature.search.ui.SearchScreen
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests of the stateless [SearchScreen] on the JVM (Robolectric).
 *
 * Flutter analogy: `testWidgets` — `setContent` ≈ `pumpWidget`, `onNodeWithText(...)` ≈
 * `find.text(...)`. Nodes are found through the semantics tree TalkBack reads, so these
 * double as accessibility checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val spotify = app("Spotify")
    private val ayse = Contact(id = 7, lookupKey = "k7", displayName = "Ayşe Yılmaz", phoneNumber = "+905550000000")

    private val results =
        SearchUiState(
            query = "s",
            results =
                listOf(
                    SearchResult.AppItem(spotify),
                    SearchResult.AppItem(app("Signal", paused = true)),
                    SearchResult.ContactItem(ayse),
                    SearchResult.CommandItem(SearchCommand.SwitchPalette(PaletteId.NIGHT_TIDE)),
                    SearchResult.WebSearch("s"),
                ),
            showContactsPrompt = true,
        )

    private val clicked = mutableListOf<SearchResult>()
    private var submitted = 0
    private var contactsRequested = 0

    private fun setContent(
        state: SearchUiState,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithFontScale(fontScale) {
                ReachTheme {
                    SearchScreen(
                        state = state,
                        query = state.query,
                        onQueryChange = {},
                        onSubmit = { _: LaunchOrigin? -> submitted++ },
                        onResultClick = { result, _ -> clicked += result },
                        onContactAction = { _, _ -> },
                        onIncludeContacts = { contactsRequested++ },
                        onClose = {},
                    )
                }
            }
        }
    }

    @Test
    fun topResultShowsTheEnterHintAndOnlyOnce() {
        setContent(results)
        // The hint is drawn inside the row, whose own description replaces its children's
        // semantics (reachClickable), so it lives in the unmerged tree.
        composeRule.onNode(hasText("ENTER ↵"), true).assertExists()
        assertThat(composeRule.onAllNodes(hasText("ENTER ↵"), true).fetchSemanticsNodes()).hasSize(1)
        // The top row says so to TalkBack.
        composeRule.onNodeWithContentDescription("Spotify, top result, press Enter to open").assertHasClickAction()
    }

    @Test
    fun everyResultIsAButtonWithADescription() {
        setContent(results)
        listOf(
            "Spotify, top result, press Enter to open",
            "Signal, paused",
            "Ayşe Yılmaz, contact",
            "Switch to Night Tide",
            "Search the web for “s”",
        ).forEach { description ->
            composeRule
                .onNodeWithContentDescription(description)
                .performScrollTo()
                .assertHasClickAction()
        }
        composeRule.onNodeWithContentDescription("Close search").assertHasClickAction()
    }

    @Test
    fun tappingARowReportsIt() {
        setContent(results)
        composeRule.onNodeWithContentDescription("Switch to Night Tide").performScrollTo().performClick()
        assertThat(clicked).containsExactly(SearchResult.CommandItem(SearchCommand.SwitchPalette(PaletteId.NIGHT_TIDE)))
    }

    @Test
    fun expandedContactShowsCallMessageAndCard() {
        setContent(results.copy(expandedContactId = ayse.id))
        composeRule.onNodeWithContentDescription("Call Ayşe Yılmaz").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Message Ayşe Yılmaz").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open the contact card of Ayşe Yılmaz").performScrollTo().assertHasClickAction()
    }

    @Test
    fun contactsPromptRequestsPermission() {
        setContent(results)
        composeRule.onNodeWithContentDescription("Include contacts").performScrollTo().performClick()
        assertThat(contactsRequested).isEqualTo(1)
    }

    @Test
    fun emptyQueryShowsSuggestedLabel() {
        setContent(SearchUiState(results = listOf(SearchResult.AppItem(spotify))))
        composeRule.onNodeWithText("SUGGESTED").assertIsDisplayed()
        composeRule.onNodeWithText("APP, CONTACT OR ACTION").assertIsDisplayed()
    }

    @Test
    fun worksAtTwoHundredPercentFont() {
        setContent(results, fontScale = 2f)
        // Every row is still reachable (the list scrolls instead of clipping) and the input stays.
        composeRule.onNodeWithContentDescription("Spotify, top result, press Enter to open").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search the web for “s”").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Include contacts").performScrollTo().assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsDisplayed()
        composeRule
            .onNode(
                hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Close search")),
            ).assertIsDisplayed()
    }
}

@Composable
private fun WithFontScale(
    fontScale: Float?,
    content: @Composable () -> Unit,
) {
    if (fontScale == null) {
        content()
    } else {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
    }
}
