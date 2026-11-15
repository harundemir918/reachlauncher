package com.baklavapp.reachlauncher.feature.allapps

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.allapps.ui.AllAppsScreen
import com.baklavapp.reachlauncher.feature.allapps.ui.RAIL_BUBBLE_TAG
import com.baklavapp.reachlauncher.feature.allapps.ui.RAIL_TAG
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Compose UI tests of All Apps on the JVM (Robolectric).
 *
 * Flutter analogy: `testWidgets` + `WidgetTester`. `performTouchInput { down(); moveTo() }`
 * ≈ `tester.startGesture()` + `gesture.moveTo()`. Nodes are found through the semantics
 * tree TalkBack reads, so these double as accessibility checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AllAppsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun tearDown() {
        appScope.cancel()
    }

    // --- Route with the real ViewModel and fakes -------------------------------------------

    private fun setRoute(
        fixture: AllAppsFixture,
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            ReachTheme { AllAppsRoute(viewModel = fixture.viewModel, onClose = onClose, onOpenSearch = {}) }
        }
    }

    @Test
    fun headingCountAndNamesAreShown() {
        setRoute(AllAppsFixture(sampleApps, appScope))
        composeRule.onNodeWithText("Everything").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("8 apps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Maps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Chess, hidden").assertExists()
    }

    @Test
    fun namesHaveDescriptionsAndLongPressActions() {
        setRoute(AllAppsFixture(sampleApps, appScope))
        val slack = composeRule.onNodeWithContentDescription("Slack, work profile, paused")
        slack.assertIsDisplayed()
        val labels = slack.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertThat(labels).containsExactly("App options", "App info", "Move to intent…", "Hide", "Uninstall").inOrder()
        slack.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    }

    @Test
    fun launchingClosesAllApps() {
        val fixture = AllAppsFixture(sampleApps, appScope)
        var closed = false
        setRoute(fixture, onClose = { closed = true })
        composeRule.onNodeWithContentDescription("Maps").performClick()
        composeRule.waitForIdle()
        assertThat(
            fixture.launcher.fake.launched
                .map { it.packageName },
        ).containsExactly("pkg.maps")
        assertThat(closed).isTrue()
    }

    @Test
    fun longPressMenuHidesAndUnhides() {
        val fixture = AllAppsFixture(sampleApps, appScope)
        setRoute(fixture)
        composeRule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Hide").performClick()
        composeRule.waitForIdle()
        assertThat(
            fixture.catalog.state.value
                .first { it.label == "Maps" }
                .isHidden,
        ).isTrue()

        val hidden = composeRule.onNodeWithContentDescription("Maps, hidden")
        invokeCustomAction(hidden, "Unhide")
        assertThat(
            fixture.catalog.state.value
                .first { it.label == "Maps" }
                .isHidden,
        ).isFalse()
    }

    @Test
    fun moveToIntentShowsChipsAndMoves() {
        val fixture = AllAppsFixture(sampleApps, appScope)
        setRoute(fixture)
        invokeCustomAction(composeRule.onNodeWithContentDescription("Maps"), "Move to intent…")
        // Current intent (Tools) is the selected chip.
        composeRule.onNodeWithText("Tools").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithText("Go").performClick()
        composeRule.waitForIdle()
        val maps =
            fixture.catalog.state.value
                .first { it.label == "Maps" }
        assertThat(maps.intent).isEqualTo(ReachIntent.GO)
        assertThat(maps.isUserAssigned).isTrue()
    }

    @Test
    fun menuItemsReachAppInfoAndUninstallAndBackCloses() {
        val fixture = AllAppsFixture(sampleApps, appScope)
        setRoute(fixture)
        composeRule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("App info").performClick()
        assertThat(fixture.launcher.appInfo.map { it.packageName }).containsExactly("pkg.maps")

        composeRule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Uninstall").assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Uninstall").assertDoesNotExist()
        assertThat(composeRule.activity.isFinishing).isFalse()
        assertThat(fixture.launcher.uninstalled).isEmpty()
    }

    // --- Stateless screen: the letter rail --------------------------------------------------

    /** One app per letter A–Z (three names each), so the wall is long enough to scroll. */
    private val alphabetApps: List<CatalogApp> =
        ('A'..'Z').flatMap { c -> listOf("${c}lpha", "${c}ravo", "${c}harlie").map { app(it) } }

    private fun setScreen(
        thumbSide: ThumbSide,
        listState: LazyListState,
        fontScale: Float? = null,
    ): AllAppsUiState {
        val state = buildUiState(alphabetApps, LetterIndex(Locale.ENGLISH), thumbSide)
        composeRule.setContent {
            WithFontScale(fontScale) {
                ReachTheme {
                    AllAppsScreen(
                        state = state,
                        onLaunch = { _, _ -> },
                        onAppInfo = {},
                        onMoveToIntent = { _, _ -> },
                        onSetHidden = { _, _ -> },
                        onUninstall = {},
                        onOpenSearch = {},
                        listState = listState,
                    )
                }
            }
        }
        return state
    }

    /** Scrubs from A to [letter] on a rail of 27 letters (A–Z, #) and checks bubble + scroll. */
    private fun scrubTo(
        letter: Char,
        state: AllAppsUiState,
        listState: LazyListState,
    ) {
        val slot = letter - 'A'
        val rail = composeRule.onNodeWithTag(RAIL_TAG)
        rail.performTouchInput {
            val slotHeight = height.toFloat() / state.rail.size
            down(Offset(centerX, slotHeight / 2))
            moveTo(Offset(centerX, slotHeight * (slot / 2 + 0.5f)))
            moveTo(Offset(centerX, slotHeight * (slot + 0.5f)))
        }
        composeRule.onNodeWithTag(RAIL_BUBBLE_TAG, useUnmergedTree = true).assertTextEquals(letter.toString())
        rail.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RAIL_BUBBLE_TAG, useUnmergedTree = true).assertDoesNotExist()
        val group = state.groups.indexOfFirst { it.letter == letter.toString() }
        // Item 0 is the heading.
        assertThat(listState.firstVisibleItemIndex).isEqualTo(1 + group)
    }

    @Test
    fun scrubbingTheRailJumpsToTheGroupAndShowsTheBubble() {
        val listState = LazyListState()
        val state = setScreen(ThumbSide.RIGHT, listState)
        scrubTo('M', state, listState)
    }

    @Test
    fun railWorksOnTheLeftAndAtFontScale200() {
        val listState = LazyListState()
        val state = setScreen(ThumbSide.LEFT, listState, fontScale = 2f)
        scrubTo('J', state, listState)
        composeRule.onNodeWithContentDescription("Jharlie").assertIsDisplayed()
    }

    @Test
    fun railOffersJumpActionsForTalkBack() {
        val listState = LazyListState()
        val state = setScreen(ThumbSide.RIGHT, listState)
        val rail = composeRule.onNodeWithContentDescription("Letter index")
        val labels = rail.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        // Only letters with apps ("#" has none here).
        assertThat(labels).hasSize(26)
        invokeCustomAction(rail, "Jump to K")
        assertThat(listState.firstVisibleItemIndex).isEqualTo(1 + state.groups.indexOfFirst { it.letter == "K" })
    }

    /** Runs a TalkBack custom action the way the actions menu does. */
    private fun invokeCustomAction(
        node: SemanticsNodeInteraction,
        label: String,
    ) {
        val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        composeRule.runOnIdle { action.action() }
        composeRule.waitForIdle()
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
