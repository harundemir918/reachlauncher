package com.baklavapp.reachlauncher.feature.allapps

import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** The private-space section of All Apps (D67): own section, fail closed, lock/unlock. */
@RunWith(RobolectricTestRunner::class) // LetterIndex uses android.icu.
@OptIn(ExperimentalCoroutinesApi::class)
class AllAppsPrivateSpaceTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val personal = ProfileState(userSerial = 0, type = ProfileType.PERSONAL, isQuietModeEnabled = false)

    private fun privateProfile(locked: Boolean) =
        ProfileState(userSerial = PRIVATE_SERIAL, type = ProfileType.PRIVATE, isQuietModeEnabled = locked)

    private fun privateApp(
        label: String,
        hidden: Boolean = false,
    ): CatalogApp =
        app(label, profile = ProfileType.PRIVATE, hidden = hidden).let { it.copy(key = it.key.copy(userSerial = PRIVATE_SERIAL)) }

    private val apps = listOf(app("Maps"), privateApp("Vault"), app("Slack", profile = ProfileType.WORK))

    @Test
    fun `private apps get their own section and never join the letter groups`() {
        val state = buildUiState(apps, LetterIndex(Locale.ENGLISH), ThumbSide.LEFT, listOf(personal, privateProfile(locked = false)))
        assertThat(state.groups.flatMap { it.apps }.map { it.label }).containsExactly("Maps", "Slack")
        assertThat(state.totalCount).isEqualTo(2)
        assertThat(state.privateSpace).isEqualTo(PrivateSpaceUi(PRIVATE_SERIAL, isLocked = false, apps = listOf(apps[1])))
    }

    @Test
    fun `a locked or unknown private space lists no apps`() {
        val locked = buildUiState(apps, LetterIndex(Locale.ENGLISH), ThumbSide.LEFT, listOf(personal, privateProfile(locked = true)))
        assertThat(locked.privateSpace?.isLocked).isTrue()
        assertThat(locked.privateSpace?.apps).isEmpty()

        // No private profile known (yet): no section and the stray apps are dropped, not shown.
        val unknown = buildUiState(apps, LetterIndex(Locale.ENGLISH), ThumbSide.LEFT, listOf(personal))
        assertThat(unknown.privateSpace).isNull()
        assertThat(unknown.groups.flatMap { it.apps }.map { it.label }).doesNotContain("Vault")
    }

    @Test
    fun `a hidden private app stays reachable in the hidden group`() {
        val hiddenVault = privateApp("Vault", hidden = true)
        val state = buildUiState(listOf(hiddenVault), LetterIndex(Locale.ENGLISH), ThumbSide.LEFT, listOf(privateProfile(locked = false)))
        assertThat(state.hidden).containsExactly(hiddenVault)
        assertThat(state.privateSpace?.apps).isEmpty()
    }

    @Test
    fun `the button locks and unlocks through the profile source`() =
        runTest {
            val f = AllAppsFixture(apps, appScope = backgroundScope)
            f.profiles.state.value = listOf(personal, privateProfile(locked = true))
            f.viewModel.onLocaleChanged(Locale.ENGLISH)
            f.viewModel.uiState.test {
                assertThat(expectMostRecentItem().privateSpace?.isLocked).isTrue()
                f.viewModel.togglePrivateSpace()
                assertThat(expectMostRecentItem().privateSpace?.isLocked).isFalse()
                f.viewModel.togglePrivateSpace()
                assertThat(expectMostRecentItem().privateSpace?.isLocked).isTrue()
            }
        }

    private companion object {
        const val PRIVATE_SERIAL = 11L
    }
}
