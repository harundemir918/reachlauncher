package com.baklavapp.reachlauncher.core.data.repository.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.db.AppAssignmentEntity
import com.baklavapp.reachlauncher.core.data.db.ReachDatabase
import com.baklavapp.reachlauncher.core.data.db.cachedAppEntity
import com.baklavapp.reachlauncher.core.data.fake.FakeDefaultAppsSource
import com.baklavapp.reachlauncher.core.data.fake.FakeProfileSource
import com.baklavapp.reachlauncher.core.data.source.InstalledAppsSource
import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.util.Locale

/**
 * Robolectric + in-memory Room: the real SQL and change notifications, with fake system
 * sources. Room runs its queries on its own background threads, so tests wait for
 * emissions (Turbine / `first { }`) rather than advancing virtual time.
 */
@RunWith(RobolectricTestRunner::class)
class AppCatalogRepositoryImplTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ReachDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** Emits nothing until a test sets a value: models "the system hasn't answered yet". */
    private class ControlledInstalledApps : InstalledAppsSource {
        val state = MutableStateFlow<List<InstalledApp>?>(null)
        override val installedApps: Flow<List<InstalledApp>> = state.filterNotNull()
    }

    private val installed = ControlledInstalledApps()
    private val profiles =
        FakeProfileSource(
            listOf(
                ProfileState(0, ProfileType.PERSONAL, isQuietModeEnabled = false),
                ProfileState(10, ProfileType.WORK, isQuietModeEnabled = false),
                ProfileState(11, ProfileType.PRIVATE, isQuietModeEnabled = true),
            ),
        )
    private val roles = FakeDefaultAppsSource()

    private fun TestScope.repository() =
        AppCatalogRepositoryImpl(
            cacheDao = db.appCacheDao(),
            assignmentDao = db.appAssignmentDao(),
            launchEventDao = db.launchEventDao(),
            installedAppsSource = installed,
            profileSource = profiles,
            defaultAppsSource = roles,
            appScope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private fun installedApp(
        label: String,
        category: AppCategory = AppCategory.UNDEFINED,
        serial: Long = 0,
        profile: ProfileType = ProfileType.PERSONAL,
        pkg: String = "com.example.${label.lowercase(Locale.ROOT)}",
    ) = InstalledApp(AppKey(pkg, "$pkg.Main", serial), label, category, profile)

    private suspend fun Flow<List<CatalogApp>>.awaitFirst(predicate: (List<CatalogApp>) -> Boolean) = realTime { first(predicate) }

    /**
     * Waits in *wall-clock* time: inside `runTest`, a plain `withTimeout` uses virtual time,
     * which jumps ahead instantly while Room works on its own threads.
     */
    private suspend fun <T> realTime(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }

    @After
    fun tearDown() {
        db.close()
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `cache is emitted before the system answers, then fresh data follows`() =
        runTest {
            val cached = installedApp("Cached")
            db.appCacheDao().upsert(listOf(cachedAppEntity(cached, ReachIntent.TOOLS)))
            val repo = repository()

            repo.apps.test {
                assertThat(awaitItem().map { it.label }).containsExactly("Cached")
                installed.state.value = listOf(installedApp("Fresh", AppCategory.MAPS))
                val fresh = awaitItem()
                assertThat(fresh.map { it.label }).containsExactly("Fresh")
                assertThat(fresh.single().intent).isEqualTo(ReachIntent.GO)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removed apps are deleted from the cache`() =
        runTest {
            val repo = repository()
            installed.state.value = listOf(installedApp("Alpha"), installedApp("Beta"))
            repo.apps.awaitFirst { it.size == 2 }
            installed.state.value = listOf(installedApp("Alpha"))
            repo.apps.awaitFirst { it.size == 1 }
            assertThat(db.appCacheDao().getAll().map { it.label }).containsExactly("Alpha")
        }

    @Test
    fun `user assignment beats auto-assignment, even after a category change`() =
        runTest {
            val repo = repository()
            val chat = installedApp("Chat", AppCategory.SOCIAL)
            installed.state.value = listOf(chat)
            assertThat(
                repo.apps
                    .awaitFirst { it.isNotEmpty() }
                    .single()
                    .intent,
            ).isEqualTo(ReachIntent.TALK)

            repo.moveToIntent(chat.key, ReachIntent.PLAY)
            val moved = repo.apps.awaitFirst { it.single().isUserAssigned }.single()
            assertThat(moved.intent).isEqualTo(ReachIntent.PLAY)

            installed.state.value = listOf(chat.copy(category = AppCategory.PRODUCTIVITY))
            realTime { db.appCacheDao().observeAll().first { it.single().categoryKey == "productivity" } }
            assertThat(
                repo.apps
                    .first()
                    .single()
                    .intent,
            ).isEqualTo(ReachIntent.PLAY)
        }

    @Test
    fun `unknown stored intent key falls back to auto-assignment`() =
        runTest {
            val repo = repository()
            val music = installedApp("Music", AppCategory.AUDIO)
            installed.state.value = listOf(music)
            db.appAssignmentDao().upsert(
                AppAssignmentEntity(music.key.packageName, music.key.activityName, 0, intentKey = "teleport", hidden = false),
            )
            val app = repo.apps.awaitFirst { it.isNotEmpty() }.single()
            assertThat(app.intent).isEqualTo(ReachIntent.LISTEN)
            assertThat(app.isUserAssigned).isFalse()
        }

    @Test
    fun `hidden flag is kept independently of the intent choice`() =
        runTest {
            val repo = repository()
            val game = installedApp("Game", AppCategory.GAME)
            installed.state.value = listOf(game)
            repo.apps.awaitFirst { it.isNotEmpty() }

            repo.setHidden(game.key, true)
            assertThat(
                repo.apps
                    .awaitFirst { it.single().isHidden }
                    .single()
                    .intent,
            ).isEqualTo(ReachIntent.PLAY)

            repo.moveToIntent(game.key, ReachIntent.TOOLS)
            val app = repo.apps.awaitFirst { it.single().isUserAssigned }.single()
            assertThat(app.isHidden).isTrue()

            repo.setHidden(game.key, false)
            val shown = repo.apps.awaitFirst { !it.single().isHidden }.single()
            assertThat(shown.intent).isEqualTo(ReachIntent.TOOLS)
        }

    @Test
    fun `locked private space apps are never emitted, even from the cache`() =
        runTest {
            val secret = installedApp("Secret", serial = 11, profile = ProfileType.PRIVATE)
            val visible = installedApp("Visible")
            db.appCacheDao().upsert(listOf(cachedAppEntity(secret, ReachIntent.TOOLS), cachedAppEntity(visible, ReachIntent.TOOLS)))
            val repo = repository()

            repo.apps.test {
                assertThat(awaitItem().map { it.label }).containsExactly("Visible")

                profiles.setQuietMode(11, enabled = false)
                assertThat(awaitItem().map { it.label }).containsExactly("Secret", "Visible").inOrder()

                profiles.setQuietMode(11, enabled = true)
                assertThat(awaitItem().map { it.label }).containsExactly("Visible")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `private apps of an unknown profile are treated as locked`() =
        runTest {
            val secret = installedApp("Secret", serial = 12, profile = ProfileType.PRIVATE)
            db.appCacheDao().upsert(listOf(cachedAppEntity(secret, ReachIntent.TOOLS)))
            assertThat(repository().apps.first()).isEmpty()
        }

    @Test
    fun `apps of a paused work profile are flagged, not dropped`() =
        runTest {
            profiles.setQuietMode(10, enabled = true)
            val repo = repository()
            installed.state.value = listOf(installedApp("Mail", serial = 10, profile = ProfileType.WORK), installedApp("Mail"))
            val apps = repo.apps.awaitFirst { it.size == 2 }
            assertThat(apps.single { it.profile == ProfileType.WORK }.isProfilePaused).isTrue()
            assertThat(apps.single { it.profile == ProfileType.PERSONAL }.isProfilePaused).isFalse()
        }

    @Test
    fun `labels sort with the Turkish collator`() =
        runTest {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val repo = repository()
            installed.state.value = listOf("Zil", "İnstagram", "ılık", "çalar", "Ayar").map { installedApp(it) }
            val labels = repo.apps.awaitFirst { it.size == 5 }.map { it.label }
            // Turkish alphabet: a b c ç … h ı i … z; İ is the capital of dotted i.
            assertThat(labels).containsExactly("Ayar", "çalar", "ılık", "İnstagram", "Zil").inOrder()
        }

    @Test
    fun `default dialer goes to Talk, and refresh picks up a role change`() =
        runTest {
            val repo = repository()
            val dialer = installedApp("Phone", AppCategory.UNDEFINED, pkg = "com.example.phone")
            installed.state.value = listOf(dialer)
            assertThat(
                repo.apps
                    .awaitFirst { it.isNotEmpty() }
                    .single()
                    .intent,
            ).isEqualTo(ReachIntent.TOOLS)

            roles.packages = setOf("com.example.phone")
            repo.refresh()
            assertThat(
                repo.apps
                    .awaitFirst { it.single().intent == ReachIntent.TALK }
                    .single()
                    .isUserAssigned,
            ).isFalse()
        }

    @Test
    fun `launch counts are merged per app`() =
        runTest {
            val repo = repository()
            val app = installedApp("Notes")
            installed.state.value = listOf(app)
            repo.apps.awaitFirst { it.isNotEmpty() }
            val history =
                LaunchHistoryRepositoryImpl(db.launchEventDao(), Clock.systemUTC(), StandardTestDispatcher(testScheduler))
            history.recordLaunch(app.key, 1_000)
            history.recordLaunch(app.key, 2_000)
            assertThat(repo.apps.awaitFirst { it.single().launchCount == 2 }).hasSize(1)
        }
}
