package com.baklavapp.reachlauncher.feature.appearance

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.baklavapp.reachlauncher.feature.appearance.ui.AppearanceCallbacks
import com.baklavapp.reachlauncher.feature.appearance.ui.AppearanceSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests of the Appearance sheet on the JVM (Robolectric): the controls carry
 * real radio/switch semantics, change state live, and stay reachable at 200 % font.
 *
 * Flutter analogy: `testWidgets` with `find.bySemanticsLabel` — nodes are found through
 * the semantics tree TalkBack reads, so these double as accessibility checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppearanceSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var closed = 0

    /** Renders the sheet over in-memory state, the way the real route does over DataStore. */
    private fun setSheet(fontScale: Float = 1f): () -> AppearanceUiState {
        // Test-owned state: callbacks update it synchronously, the sheet recomposes from it.
        val holder = mutableStateOf(AppearanceUiState(loaded = true))
        composeRule.setContent {
            var state by remember { holder }
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ReachTheme(palette = state.palette, themeMode = state.themeMode) {
                    AppearanceSheet(
                        state = state,
                        callbacks =
                            AppearanceCallbacks(
                                onThemeMode = { state = state.copy(themeMode = it) },
                                onPalette = { state = state.copy(palette = it) },
                                onShowWallpaper = { state = state.copy(showWallpaper = it) },
                                onThumbSide = { state = state.copy(thumbSide = it) },
                            ),
                        onClose = { closed++ },
                    )
                }
            }
        }
        return { holder.value }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun themeSegmentsAreRadioButtonsAndAutoShowsFollowsAndroid() {
        val state = setSheet()
        composeRule.onNodeWithContentDescription("theme · follows Android").assertExists()
        composeRule.onNodeWithText("Auto").assert(hasRole(Role.RadioButton)).assertIsSelected()

        composeRule.onNodeWithText("Dark").performClick()

        assertThat(state().themeMode).isEqualTo(ThemeMode.DARK)
        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.onNodeWithContentDescription("theme").assertExists()
    }

    @Test
    fun paletteSwatchesAreSelectable() {
        val state = setSheet()
        composeRule
            .onNodeWithText("Night Tide")
            .performScrollTo()
            .assert(hasRole(Role.RadioButton))
            .performClick()
        assertThat(state().palette).isEqualTo(PaletteId.NIGHT_TIDE)
        composeRule.onNodeWithText("Night Tide").assertIsSelected()
    }

    @Test
    fun wallpaperRowIsOneSwitchAndRelabelsThePalette() {
        val state = setSheet()
        val row = composeRule.onNode(hasRole(Role.Switch) and hasText("Show wallpaper", substring = true))
        row.assertIsOff().performClick()
        assertThat(state().showWallpaper).isTrue()
        row.assertIsOn()
        composeRule.onNodeWithContentDescription("palette · accent from wallpaper").assertExists()
    }

    @Test
    fun thumbSideIsReachableAt200PercentFont() {
        val state = setSheet(fontScale = 2f)
        composeRule.onNodeWithText("Right").performScrollTo().performClick()
        assertThat(state().thumbSide).isEqualTo(ThumbSide.RIGHT)
    }

    @Test
    fun donePillCloses() {
        setSheet()
        composeRule.onNodeWithContentDescription("Done").performClick()
        assertThat(closed).isEqualTo(1)
    }
}
