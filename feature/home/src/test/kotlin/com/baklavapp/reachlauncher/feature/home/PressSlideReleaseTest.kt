package com.baklavapp.reachlauncher.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.LaunchResult
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.home.ui.FAN_PILL_TEST_TAG
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Press, slide, release end to end: real pointer events through `HomeRoute`.
 *
 * Flutter analogy: `tester.startGesture(...)`, `tester.pump(Duration(milliseconds: 350))`,
 * `gesture.moveTo(...)`, `gesture.up()`. Compose's `performTouchInput { down(); … }` keeps
 * the gesture going across calls, so a test can press, look at the fan that opened, and
 * only then slide to a pill it found in the semantics tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PressSlideReleaseTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pill = hasTestTag(FAN_PILL_TEST_TAG)

    private fun setContent(
        fixture: HomeFixture,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ReachTheme { HomeRoute(fixture.viewModel) }
            }
        }
    }

    /** The word's button: "Talk, 3 apps" normally, just "Talk" while its fan is open. */
    private fun word(text: String) = composeRule.onNode(hasContentDescription(text, substring = true) and !pill)

    private fun fanPill(label: String) = composeRule.onNode(hasContentDescription(label) and pill)

    private fun placedPills() = composeRule.onAllNodes(pill).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    /** Presses the centre of [word] and holds past the threshold. */
    private fun holdWord(word: String) {
        word(word).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
        }
        // advanceEventTime only stamps the *next* event; with the finger resting there is no
        // next event yet, so move the test clock ourselves to let the hold timer fire.
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        composeRule.waitForIdle()
    }

    /** Continues the held gesture on [word]'s node: slide to [target]'s centre. */
    private fun slideTo(
        word: String,
        target: Offset,
    ) {
        val origin = word(word).fetchSemanticsNode().boundsInRoot.topLeft
        word(word).performTouchInput { moveInSteps(target - origin) }
        composeRule.waitForIdle()
    }

    private fun centreOf(label: String) = fanPill(label).fetchSemanticsNode().boundsInRoot.center

    private fun TouchInjectionScope.moveInSteps(target: Offset) {
        val start = checkNotNull(currentPosition()) { "no pointer down" }
        repeat(STEPS) { i -> moveTo(start + (target - start) * ((i + 1f) / STEPS)) }
    }

    private fun lift(word: String) {
        word(word).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun aShortPressIsStillATapAndOpensTheDetail() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Talk").performTouchInput {
            down(center)
            advanceEventTime(100)
            up()
        }
        composeRule.waitForIdle()
        assertThat(composeRule.onAllNodes(pill).fetchSemanticsNodes()).isEmpty()
        assertThat(fixture.surface).isEqualTo(HomeSurface.IntentDetail(ReachIntent.TALK))
    }

    @Test
    fun holdOpensTheFanWithTheHintAndSlidingHighlightsAndReleaseLaunches() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        holdWord("Talk")

        fanPill("WhatsApp").assertIsDisplayed().assertIsNotSelected()
        fanPill("Messages").assertIsDisplayed()
        composeRule.onNodeWithText("RELEASE TO OPEN · SLIDE OFF TO CANCEL").assertIsDisplayed()
        // The pill nearest the finger (lowest on screen) is the most used.
        assertThat(centreOf("WhatsApp").y).isGreaterThan(centreOf("Messages").y)

        slideTo("Talk", centreOf("Messages"))
        fanPill("Messages").assertIsSelected()
        fanPill("WhatsApp").assertIsNotSelected()

        lift("Talk")
        assertThat(fixture.launcher.launched.map { it.packageName }).containsExactly("pkg.messages")
        assertThat(
            fixture.history.events.value
                .map { it.key.packageName },
        ).containsExactly("pkg.messages")
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
        assertThat(composeRule.onAllNodes(pill).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun theFanStaysOpenWhileTheTwoFingerSwipeIsAvailable() {
        // Regression (found on a real device): with the accessibility service on, opening
        // the fan used to remove the two-finger detector from Home's modifier chain, which
        // cancelled the held word's gesture — the fan closed the instant it opened.
        val fixture = HomeFixture(sampleApps)
        fixture.screenActions.enabled.value = true
        setContent(fixture)
        composeRule.waitForIdle()
        holdWord("Talk")

        fanPill("WhatsApp").assertIsDisplayed()
        slideTo("Talk", centreOf("WhatsApp"))
        fanPill("WhatsApp").assertIsSelected()
        lift("Talk")
        assertThat(fixture.launcher.launched.map { it.packageName }).containsExactly("pkg.whatsapp")
    }

    @Test
    fun slidingBackOntoTheWordCancels() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        holdWord("Talk")
        val wordCentre = word("Talk").fetchSemanticsNode().boundsInRoot.center
        slideTo("Talk", centreOf("Messages"))
        slideTo("Talk", wordCentre)
        lift("Talk")

        assertThat(fixture.launcher.launched).isEmpty()
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home) // not the detail: the tap was suppressed
        assertThat(composeRule.onAllNodes(pill).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun verticalMovementInTheFanDoesNotTriggerHomeSwipes() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        holdWord("Talk")
        val wordBounds = word("Talk").fetchSemanticsNode().boundsInRoot
        // Far up (past the pills, a would-be swipe up), then far down (a would-be pull-down).
        slideTo("Talk", Offset(wordBounds.center.x, 40f))
        slideTo("Talk", Offset(wordBounds.center.x, wordBounds.bottom + 300f))
        lift("Talk")

        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
        assertThat(fixture.launcher.launched).isEmpty()
    }

    @Test
    fun aSystemCancelNeverLaunches() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        holdWord("Talk")
        slideTo("Talk", centreOf("Messages"))
        fanPill("Messages").assertIsSelected()
        word("Talk").performTouchInput { cancel() }
        composeRule.waitForIdle()

        assertThat(fixture.launcher.launched).isEmpty()
        assertThat(fixture.viewModel.fan.value).isNull()
        assertThat(composeRule.onAllNodes(pill).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun aTalkBackStyleHoldWithoutSlidingDoesNotTrapTheUser() {
        // TalkBack's double-tap-and-hold delivers a long still press on the focused word.
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        word("Talk").performTouchInput {
            down(center)
            advanceEventTime(1_500)
            up()
        }
        composeRule.waitForIdle()

        assertThat(fixture.launcher.launched).isEmpty()
        assertThat(composeRule.onAllNodes(pill).fetchSemanticsNodes()).isEmpty()
        // Home is intact and the accessible path still works.
        composeRule.onNode(hasContentDescription("Type to launch")).assertIsDisplayed()
        word("Talk, 3 apps").assertIsDisplayed()
    }

    @Test
    fun aFailedLaunchFromTheFanShowsTheMessage() {
        val fixture = HomeFixture(sampleApps)
        fixture.launcher.result = LaunchResult.FAILED
        setContent(fixture)
        holdWord("Talk")
        slideTo("Talk", centreOf("WhatsApp"))
        lift("Talk")
        composeRule.onNodeWithText("Couldn't open WhatsApp").assertIsDisplayed()
    }

    @Test
    fun rightThumbSideMirrorsTheFanAndStillLaunches() {
        val fixture = HomeFixture(sampleApps)
        runBlocking { fixture.settings.setThumbSide(ThumbSide.RIGHT) }
        setContent(fixture)
        holdWord("Talk")
        val wordBounds = word("Talk").fetchSemanticsNode().boundsInRoot
        val nearest = fanPill("WhatsApp").fetchSemanticsNode().boundsInRoot
        val farther = fanPill("Phone").fetchSemanticsNode().boundsInRoot
        // Mirrored: the column drifts left (away from the right edge) as it rises.
        assertThat(wordBounds.center.x).isGreaterThan(
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.width / 2,
        )
        assertThat(farther.right).isLessThan(nearest.right)

        slideTo("Talk", centreOf("Phone"))
        fanPill("Phone").assertIsSelected()
        lift("Talk")
        assertThat(fixture.launcher.launched.map { it.packageName }).containsExactly("pkg.phone")
    }

    @Test
    // A short phone: at 200 % Home scrolls and the space above the word is limited. (Robolectric's
    // text metrics don't grow the pills when names wrap, so the limit comes from the screen here.)
    @Config(qualifiers = "w360dp-h480dp-xxhdpi")
    fun at200PercentFontFewerPillsFitAndTheNearestStillLaunches() {
        // Long names wrap to two lines at 200 %, so each pill is much taller than 52dp.
        val tools =
            (1..6).map {
                app(
                    "Tool $it with quite a long name",
                    ReachIntent.TOOLS,
                    launchCount = 10 - it,
                    packageName = "pkg.tool.$it",
                )
            }
        val fixture = HomeFixture(tools)
        setContent(fixture, fontScale = 2f)
        word("Tools").performScrollTo()
        holdWord("Tools")

        val placed = placedPills()
        assertThat(placed.size).isAtLeast(1)
        assertThat(placed.size).isLessThan(FanState.MAX_APPS)
        // Every placed pill stays on screen, below the status-bar inset.
        placed.forEach { assertThat(it.boundsInRoot.top).isAtLeast(0f) }

        slideTo("Tools", centreOf("Tool 1 with quite a long name"))
        lift("Tools")
        assertThat(fixture.launcher.launched.map { it.packageName }).containsExactly("pkg.tool.1")
    }
}

/** Past the 300 ms threshold (Robolectric's system long-press timeout is a platform default). */
private const val HOLD_MILLIS = 350L
private const val STEPS = 8
