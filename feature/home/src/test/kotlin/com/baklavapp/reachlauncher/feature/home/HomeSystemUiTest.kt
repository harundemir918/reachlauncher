package com.baklavapp.reachlauncher.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI tests of the M3 pieces: Listen's player, the Now Playing card, onboarding cards. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeSystemUiTest {
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
                    HomeRoute(fixture.viewModel, searchContent = { Text("search") }, allAppsContent = { Text("all apps") })
                }
            }
        }
    }

    @Test
    fun listenRowShowsTheInlinePlayerWhilePlaying() {
        val fixture = HomeFixture(sampleApps)
        fixture.media.state.value = dailyMix
        setContent(fixture)

        composeRule.onNodeWithText("PLAYING · SPOTIFY", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause Daily Mix 1").performClick()
        assertThat(fixture.media.commands).containsExactly("playPause")
        composeRule.onNodeWithContentDescription("Play Daily Mix 1").assertIsDisplayed()
    }

    @Test
    fun listenRowHasNoPlayerWithoutASession() {
        setContent(HomeFixture(sampleApps))
        composeRule.onNodeWithContentDescription("Listen, 1 app").assertIsDisplayed()
        composeRule.onNodeWithText("PLAYING · SPOTIFY", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun nowPlayingCardButtonsCallThrough() {
        val fixture = HomeFixture(sampleApps)
        fixture.media.state.value = dailyMix
        setContent(fixture)
        fixture.viewModel.onIntentSelected(ReachIntent.LISTEN)

        composeRule.onNodeWithText("NOW PLAYING · SPOTIFY", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous track").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithContentDescription("Pause Daily Mix 1").performClick()
        assertThat(fixture.media.commands).containsExactly("previous", "next", "playPause").inOrder()
    }

    @Test
    fun nowPlayingCardIsOnlyOnListen() {
        val fixture = HomeFixture(sampleApps)
        fixture.media.state.value = dailyMix
        setContent(fixture)
        fixture.viewModel.onIntentSelected(ReachIntent.TALK)
        composeRule.onNodeWithText("NOW PLAYING · SPOTIFY", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun onboardingCardNotNowHidesItAndShowsTheNext() {
        val fixture = HomeFixture(sampleApps, onboarding = true)
        setContent(fixture)

        composeRule.onNodeWithText("Make Reach your home screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Set as home").performClick()
        assertThat(fixture.launcher.openedScreens).hasSize(1)

        composeRule.onNodeWithContentDescription("Not now, hide “Make Reach your home screen”").performScrollTo().performClick()
        composeRule.onNodeWithText("Make Reach your home screen").assertDoesNotExist()
        composeRule.onNodeWithText("Unread counts and music controls").assertIsDisplayed()
        assertThat(fixture.settings.state.value.dismissedTips).containsExactly("default_home")
    }

    @Test
    fun cardsReflowAt200PercentFont() {
        val fixture = HomeFixture(sampleApps, onboarding = true)
        fixture.media.state.value = dailyMix
        setContent(fixture, fontScale = 2f)
        composeRule.onNodeWithContentDescription("Not now, hide “Make Reach your home screen”").performScrollTo().assertIsDisplayed()
        fixture.viewModel.onIntentSelected(ReachIntent.LISTEN)
        composeRule.onNodeWithContentDescription("Next track").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noCardWhenEverythingIsGranted() {
        val fixture = HomeFixture(sampleApps, onboarding = true)
        fixture.homeRole.state.value = true
        fixture.notifications.granted.value = true
        fixture.calendar.granted.value = true
        fixture.usage.granted.value = true
        setContent(fixture)
        composeRule.onNodeWithText("Not now").assertDoesNotExist()
    }
}
