package com.baklavapp.reachlauncher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests of the Home surface, running on the JVM with Robolectric.
 *
 * Flutter analogy: `testWidgets` + `WidgetTester`. `composeRule.setContent` ≈ `pumpWidget`,
 * `onNodeWithText("Talk").performClick()` ≈ `tester.tap(find.text('Talk'))`, and Compose
 * waits for idle (≈ `pumpAndSettle`) before every assertion. Nodes are found through the
 * *semantics* tree — the same tree TalkBack reads — so these tests double as accessibility
 * checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val words = listOf("Talk", "Listen", "Go", "Make", "Play", "Tools")

    private fun setContent(
        fixture: HomeFixture,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithFontScale(fontScale) {
                ReachTheme { HomeRoute(fixture.viewModel) }
            }
        }
    }

    @Test
    fun homeShowsSixIntentWords() {
        setContent(HomeFixture(sampleApps))
        words.forEach { word(it).assertIsDisplayed() }
    }

    @Test
    fun tappingTalkOpensDetailAndBackReturnsHome() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)

        word("Talk").performClick()
        composeRule.onNodeWithContentDescription("WhatsApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Messages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Phone").assertIsDisplayed()
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))

        composeRule.onNodeWithContentDescription("Back").performClick()
        word("Tools").assertIsDisplayed()
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.Home)
    }

    @Test
    fun launchingFromDetailReturnsHome() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Go").performClick()
        composeRule.onNodeWithContentDescription("Maps").performClick()
        assertThat(fixture.launcher.launched.map { it.packageName }).containsExactly("pkg.maps")
        word("Talk").assertIsDisplayed()
    }

    @Test
    fun tappingThePillOpensSearchPlaceholder() {
        setContent(HomeFixture(sampleApps))
        composeRule.onNodeWithContentDescription("Type to launch").performClick()
        composeRule.onNodeWithText("Type to launch arrives in M2.").assertIsDisplayed()
    }

    @Test
    fun pullDownOpensSearchAndSwipeUpOpensAllApps() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        composeRule.onRoot().performTouchInput { swipeDown() }
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.Search)

        fixture.viewModel.onBack()
        composeRule.onRoot().performTouchInput { swipeUp() }
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.AllApps)
        composeRule.onNodeWithText("The A–Z list arrives in M2.").assertIsDisplayed()
    }

    @Test
    fun intentRowDescriptionIncludesTheBadge() {
        val fixture = HomeFixture(sampleApps)
        fixture.notifications.granted.value = true
        fixture.notifications.counts.value = mapOf(PackageUser("pkg.whatsapp", 0) to 3)
        setContent(fixture)
        composeRule.onNodeWithContentDescription("Talk, 3 new notifications").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Listen, 1 app").assertIsDisplayed()
    }

    @Test
    fun clockExposesAppearanceCustomAction() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        val clock = composeRule.onNode(hasCustomAction("Appearance"))
        clock.assertIsDisplayed()
        invokeCustomAction(clock, "Appearance")
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.Appearance)
        composeRule.onNodeWithText("Theme, palette and thumb side arrive in M4.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Done").performClick()
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.Home)
    }

    @Test
    fun searchPillOffersAllAppsAction() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        invokeCustomAction(composeRule.onNode(hasCustomAction("All apps")), "All apps")
        assertThat(fixture.viewModel.uiState.value.surface).isEqualTo(HomeSurface.AllApps)
    }

    @Test
    fun chipSwitchesIntent() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Talk").performClick()
        composeRule.onNodeWithText("Listen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Spotify").assertIsDisplayed()
        assertThat(fixture.viewModel.uiState.value.surface)
            .isEqualTo(HomeSurface.IntentDetail(ReachIntent.LISTEN))
    }

    @Test
    fun intentWordsSurviveFontScale200() {
        setContent(HomeFixture(sampleApps), fontScale = 2f)
        words.forEach { word(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Type to launch").performScrollTo().assertIsDisplayed()
    }

    /** An intent word on Home: its one merged description starts with the word ("Talk, 3 apps"). */
    private fun word(text: String) = composeRule.onNode(hasContentDescription("$text, ", substring = true))

    /** Runs a TalkBack custom action the way the actions menu does. */
    private fun invokeCustomAction(
        node: SemanticsNodeInteraction,
        label: String,
    ) {
        val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        composeRule.runOnIdle { action.action() }
        composeRule.waitForIdle()
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action '$label'") { node ->
            node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
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
