@file:OptIn(ExperimentalCoroutinesApi::class)

package com.baklavapp.reachlauncher.core.data.repository.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.baklavapp.reachlauncher.core.data.repository.FavoritesRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.ReachIntent
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
class FavoritesRepositoryImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(file: File = File(tmp.root, "settings.preferences_pb")) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    private fun key(
        name: String,
        serial: Long = 0,
    ) = AppKey("com.example.$name", "com.example.$name.Main\$Inner", serial)

    @Test
    fun `first run has no favourites`() =
        runTest {
            assertThat(FavoritesRepositoryImpl(dataStore()).favorites.first()).isEmpty()
        }

    @Test
    fun `favourites keep their order per intent and survive a new instance`() =
        runTest {
            val file = File(tmp.root, "f.preferences_pb")
            val store = dataStore(file)
            val repo = FavoritesRepositoryImpl(store)
            repo.update(ReachIntent.TALK) { listOf(key("phone"), key("chat", serial = 10)) }
            repo.update(ReachIntent.MAKE) { listOf(key("camera")) }
            val expected =
                mapOf(
                    ReachIntent.TALK to listOf(key("phone"), key("chat", serial = 10)),
                    ReachIntent.MAKE to listOf(key("camera")),
                )
            assertThat(repo.favorites.first()).isEqualTo(expected)
            assertThat(FavoritesRepositoryImpl(store).favorites.first()).isEqualTo(expected)
        }

    @Test
    fun `update de-duplicates, caps at five and removes an emptied intent`() =
        runTest {
            val repo = FavoritesRepositoryImpl(dataStore())
            repo.update(ReachIntent.TOOLS) { (1..7).map { key("a$it") } + key("a1") }
            assertThat(repo.favorites.first()[ReachIntent.TOOLS]).hasSize(FavoritesRepository.MAX_PER_INTENT)
            repo.update(ReachIntent.TOOLS) { emptyList() }
            assertThat(repo.favorites.first()).doesNotContainKey(ReachIntent.TOOLS)
        }

    @Test
    fun `unparseable lines are skipped`() =
        runTest {
            val store = dataStore()
            store.edit { it[FavoritesRepositoryImpl.keyFor(ReachIntent.GO)] = "garbage\ncom.maps/com.maps.Main#0\nx/y#notanumber" }
            assertThat(FavoritesRepositoryImpl(store).favorites.first()[ReachIntent.GO])
                .containsExactly(AppKey("com.maps", "com.maps.Main", 0))
        }
}
