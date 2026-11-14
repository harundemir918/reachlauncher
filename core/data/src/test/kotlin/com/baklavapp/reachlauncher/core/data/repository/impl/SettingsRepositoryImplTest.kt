@file:OptIn(ExperimentalCoroutinesApi::class)

package com.baklavapp.reachlauncher.core.data.repository.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.model.AppearanceSettings
import com.baklavapp.reachlauncher.core.model.PaletteId
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.core.model.ThumbSide
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Plain JVM test: DataStore's core doesn't need Android, only a file. */
class SettingsRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(file: File = File(tmp.root, "settings.preferences_pb")) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    @Test
    fun `first run emits defaults`() =
        runTest {
            assertThat(SettingsRepositoryImpl(dataStore()).settings.first()).isEqualTo(AppearanceSettings())
        }

    @Test
    fun `every setter re-emits and round-trips`() =
        runTest {
            val repo = SettingsRepositoryImpl(dataStore())
            repo.settings.test {
                assertThat(awaitItem()).isEqualTo(AppearanceSettings())
                repo.setThemeMode(ThemeMode.DARK)
                assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
                repo.setPalette(PaletteId.DUSK)
                assertThat(awaitItem().palette).isEqualTo(PaletteId.DUSK)
                repo.setShowWallpaper(true)
                assertThat(awaitItem().showWallpaper).isTrue()
                repo.setThumbSide(ThumbSide.RIGHT)
                assertThat(awaitItem().thumbSide).isEqualTo(ThumbSide.RIGHT)
                repo.setDoubleTapToLock(true)
                assertThat(awaitItem().doubleTapToLock).isTrue()
                repo.setHiddenApiShadeFallback(true)
                assertThat(awaitItem())
                    .isEqualTo(AppearanceSettings(ThemeMode.DARK, PaletteId.DUSK, true, ThumbSide.RIGHT, true, true))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `enums are stored by storage key`() =
        runTest {
            val store = dataStore()
            SettingsRepositoryImpl(store).setPalette(PaletteId.PAPER_INK)
            assertThat(store.data.first()[stringPreferencesKey("palette")]).isEqualTo("paper_ink")
        }

    @Test
    fun `unknown stored keys fall back to defaults`() =
        runTest {
            val store = dataStore()
            store.edit {
                it[stringPreferencesKey("palette")] = "neon"
                it[stringPreferencesKey("theme_mode")] = "sepia"
            }
            val settings = SettingsRepositoryImpl(store).settings.first()
            assertThat(settings.palette).isEqualTo(PaletteId.DEFAULT)
            assertThat(settings.themeMode).isEqualTo(ThemeMode.AUTO)
        }
}
