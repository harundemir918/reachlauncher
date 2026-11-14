package com.baklavapp.reachlauncher.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.PackageUser
import com.baklavapp.reachlauncher.core.model.Prediction
import com.baklavapp.reachlauncher.core.model.PredictionReason
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
    // An Android compose rule (a real ComponentActivity) so tests can drive the Activity's
    // OnBackPressedDispatcher — the path system Back takes — and check it didn't finish.
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
        assertThat(fixture.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))

        composeRule.onNodeWithContentDescription("Back").performClick()
        word("Tools").assertIsDisplayed()
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
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
        assertThat(fixture.surface).isEqualTo(HomeSurface.Search)

        fixture.viewModel.onBack()
        composeRule.onRoot().performTouchInput { swipeUp() }
        assertThat(fixture.surface).isEqualTo(HomeSurface.AllApps)
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
        assertThat(fixture.surface).isEqualTo(HomeSurface.Appearance)
        composeRule.onNodeWithText("Theme, palette and thumb side arrive in M4.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Done").performClick()
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
    }

    @Test
    fun searchPillOffersAllAppsAction() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        invokeCustomAction(composeRule.onNode(hasCustomAction("All apps")), "All apps")
        assertThat(fixture.surface).isEqualTo(HomeSurface.AllApps)
    }

    @Test
    fun chipSwitchesIntent() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Talk").performClick()
        composeRule.onNodeWithText("Listen").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Spotify").assertIsDisplayed()
        assertThat(fixture.surface)
            .isEqualTo(HomeSurface.IntentDetail(ReachIntent.LISTEN))
    }

    @Test
    fun intentWordsSurviveFontScale200() {
        setContent(HomeFixture(sampleApps), fontScale = 2f)
        words.forEach { word(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Type to launch").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun systemBackFromDetailReturnsHome() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Talk").performClick()
        assertThat(fixture.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))

        pressSystemBack()

        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
        word("Tools").assertIsDisplayed()
    }

    @Test
    fun systemBackOnHomeIsConsumedAndStaysHome() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()

        pressSystemBack()

        // Without HomeRoute's BackHandler, the dispatcher's fallback finishes the Activity.
        assertThat(composeRule.activity.isFinishing).isFalse()
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
        word("Talk").assertIsDisplayed()
    }

    @Test
    fun beforeTheCatalogLoadsRowsShowNoCountAndNowIsHidden() {
        val catalog = SilentCatalog(FakeAppCatalogRepository(sampleApps))
        val fixture = HomeFixture(catalogRepository = catalog)
        fixture.predictions.state.value = sampleApps.take(3).map { Prediction(it, PredictionReason.Recent, score = 1.0) }
        setContent(fixture)

        // Just the word — no "0 apps" while the cache is on its way.
        composeRule.onNodeWithContentDescription("Talk").assertIsDisplayed()
        composeRule.onNodeWithText("NOW", ignoreCase = true).assertDoesNotExist()

        catalog.release()
        composeRule.onNodeWithContentDescription("Talk, 3 apps").assertIsDisplayed()
        composeRule.onNodeWithText("NOW", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun tapAndLongPressOnTheClockDontSwipe() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        val clock = composeRule.onNode(hasCustomAction("Appearance"))

        clock.performClick()
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)

        clock.performTouchInput { longClick(durationMillis = LONG_PRESS_MILLIS) }
        assertThat(fixture.surface).isEqualTo(HomeSurface.Appearance)
    }

    @Test
    fun tapOnAnIntentWordOnlyOpensItsDetail() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Go").performClick()
        assertThat(fixture.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.GO))
    }

    @Test
    fun swipesStillWorkAtFontScale200() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture, fontScale = 2f)

        // Home scrolls at 200 %: from the bottom, a swipe up is the drag the scroll can't use.
        composeRule.onNodeWithContentDescription("Type to launch").performScrollTo()
        composeRule.onRoot().performTouchInput { swipeUp() }
        assertThat(fixture.surface).isEqualTo(HomeSurface.AllApps)

        // Back on a fresh Home (scrolled to the top): pull down opens Search.
        fixture.viewModel.onBack()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeDown() }
        assertThat(fixture.surface).isEqualTo(HomeSurface.Search)
    }

    @Test
    @Config(qualifiers = "w320dp-h400dp-xxhdpi") // A short phone, so 200 % Home really scrolls.
    fun homePressedOnHomeScrollsBackToTheTop() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture, fontScale = 2f)
        composeRule.onNodeWithContentDescription("Type to launch").performScrollTo()
        assertThat(homeScrollOffset()).isGreaterThan(0f)

        composeRule.runOnIdle { fixture.viewModel.onHomePressed() }
        composeRule.waitForIdle()

        assertThat(homeScrollOffset()).isEqualTo(0f)
    }

    @Test
    fun aSecondFailureRestartsTheMessageTimer() {
        val fixture = HomeFixture(sampleApps)
        fixture.launcher.result = LaunchResult.FAILED
        val maps = sampleApps.first { it.label == "Maps" }
        setContent(fixture)
        composeRule.mainClock.autoAdvance = false
        val message = composeRule.onNodeWithText("Couldn't open Maps")

        composeRule.runOnIdle { fixture.viewModel.launch(maps, null) }
        composeRule.mainClock.advanceTimeBy(2_000)
        message.assertIsDisplayed()

        composeRule.runOnIdle { fixture.viewModel.launch(maps, null) }
        composeRule.mainClock.advanceTimeBy(2_000)
        // 4 s after the first failure, 2 s after the second: still shown.
        message.assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_500)
        message.assertDoesNotExist()
    }

    /** Current scroll offset of Home's vertical scroll, read from its semantics. */
    private fun homeScrollOffset(): Float =
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
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

/** Comfortably longer than the clock's long-press timeout (max(450 ms, system timeout)). */
private const val LONG_PRESS_MILLIS = 1_000L

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
