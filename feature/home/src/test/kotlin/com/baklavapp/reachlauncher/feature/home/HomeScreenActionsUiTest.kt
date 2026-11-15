package com.baklavapp.reachlauncher.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's screen-action gestures and their TalkBack actions (spec §6.3, §6.10, §7.4; D66), plus
 * intent detail at 200 % font. Robolectric Compose tests, like [HomeRouteTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenActionsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        fixture: HomeFixture,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ReachTheme {
                    HomeRoute(
                        fixture.viewModel,
                        searchContent = { Text("search surface") },
                        allAppsContent = { Text("all apps surface") },
                        appearanceContent = { Text("appearance sheet") },
                    )
                }
            }
        }
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action '$label'") { node ->
            node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
        }

    /** Empty space between the clock and the intent words (no Now row in these fixtures). */
    private fun emptySpot(
        width: Int,
        height: Int,
    ) = Offset(width * 0.75f, height * 0.40f)

    @Test
    fun twoFingerSwipeDownOpensTheShadeAndNotSearch() {
        val fixture = HomeFixture(sampleApps)
        fixture.screenActions.enabled.value = true
        setContent(fixture)

        composeRule.onRoot().performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.3f))
            down(1, Offset(width * 0.6f, height * 0.3f))
            repeat(12) {
                updatePointerBy(0, Offset(0f, 30f))
                updatePointerBy(1, Offset(0f, 30f))
                move()
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertThat(fixture.screenActions.shadeCount).isEqualTo(1)
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
    }

    @Test
    fun doubleTapOnEmptySpaceLocksOnlyWhenSwitchedOn() {
        val fixture = HomeFixture(sampleApps)
        fixture.screenActions.enabled.value = true
        setContent(fixture)

        composeRule.onRoot().performTouchInput { doubleClick(emptySpot(width, height)) }
        composeRule.waitForIdle()
        assertThat(fixture.screenActions.lockCount).isEqualTo(0) // Setting off: not even listened for.

        runBlocking { fixture.settings.setDoubleTapToLock(true) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { doubleClick(emptySpot(width, height)) }
        composeRule.waitForIdle()
        assertThat(fixture.screenActions.lockCount).isEqualTo(1)
        assertThat(fixture.surface).isEqualTo(HomeSurface.Home)
    }

    @Test
    fun doubleTapWithoutTheServiceShowsTheHint() {
        val fixture = HomeFixture(sampleApps)
        runBlocking { fixture.settings.setDoubleTapToLock(true) }
        setContent(fixture)

        composeRule.onRoot().performTouchInput { doubleClick(emptySpot(width, height)) }
        composeRule.onNodeWithText("accessibility service", substring = true).assertIsDisplayed()
        assertThat(fixture.screenActions.lockCount).isEqualTo(0)
    }

    @Test
    fun screenActionsAreTalkBackActionsOnlyWhileTheyWork() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture)
        composeRule.onNode(hasCustomAction("All apps")).assertIsDisplayed()
        composeRule.onNode(hasCustomAction("Lock screen")).assertDoesNotExist()

        fixture.screenActions.enabled.value = true
        composeRule.waitForIdle()
        val pill = composeRule.onNode(hasCustomAction("Lock screen"))
        pill.assertIsDisplayed()
        val actions = pill.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("All apps", "Lock screen", "Open notifications").inOrder()

        composeRule.runOnIdle { actions.first { it.label == "Lock screen" }.action() }
        assertThat(fixture.screenActions.lockCount).isEqualTo(1)
    }

    @Test
    fun intentDetailReflowsAtFontScale200() {
        val fixture = HomeFixture(sampleApps)
        setContent(fixture, fontScale = 2f)
        composeRule.onNode(hasContentDescription("Talk, ", substring = true)).performScrollTo().performClick()

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        listOf("WhatsApp", "Messages", "Phone").forEach {
            composeRule.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Tools").performScrollTo().assertIsDisplayed()
    }
}
