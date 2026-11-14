package com.baklavapp.reachlauncher.core.data.repository.impl

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.baklavapp.reachlauncher.core.data.db.AppAssignmentEntity
import com.baklavapp.reachlauncher.core.data.db.AppCacheDao
import com.baklavapp.reachlauncher.core.data.db.CachedAppEntity
import com.baklavapp.reachlauncher.core.data.db.ReachDatabase
import com.baklavapp.reachlauncher.core.data.db.cachedAppEntity
import com.baklavapp.reachlauncher.core.data.fake.FakeDefaultAppsSource
import com.baklavapp.reachlauncher.core.data.fake.FakeProfileSource
import com.baklavapp.reachlauncher.core.data.source.InstalledAppsSource
import com.baklavapp.reachlauncher.core.data.source.ProfileSource
import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger

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

    private fun TestScope.repository(
        cacheDao: AppCacheDao = db.appCacheDao(),
        profileSource: ProfileSource = profiles,
        installedAppsSource: InstalledAppsSource = installed,
    ) = AppCatalogRepositoryImpl(
        cacheDao = cacheDao,
        assignmentDao = db.appAssignmentDao(),
        launchEventDao = db.launchEventDao(),
        installedAppsSource = installedAppsSource,
        profileSource = profileSource,
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
            // The very first emission may predate the profile answer (cache-first); wait for it.
            val apps = repo.apps.awaitFirst { list -> list.size == 2 && list.any { it.isProfilePaused } }
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

    @Test
    fun `the first emission needs only Room, not the profile source`() =
        runTest {
            val work = installedApp("Mail", serial = 10, profile = ProfileType.WORK)
            val personal = installedApp("Notes")
            val secret = installedApp("Secret", serial = 11, profile = ProfileType.PRIVATE)
            db.appCacheDao().upsert(listOf(work, personal, secret).map { cachedAppEntity(it, ReachIntent.TOOLS) })
            val silentProfiles = SilentProfileSource()
            val repo = repository(profileSource = silentProfiles)

            repo.apps.test {
                // UserManager hasn't answered: private apps stay hidden (fail closed), personal
                // and work apps show, nobody is marked paused yet.
                val first = awaitItem()
                assertThat(first.map { it.label }).containsExactly("Mail", "Notes").inOrder()
                assertThat(first.none { it.isProfilePaused }).isTrue()

                silentProfiles.state.emit(
                    listOf(
                        ProfileState(0, ProfileType.PERSONAL, isQuietModeEnabled = false),
                        ProfileState(10, ProfileType.WORK, isQuietModeEnabled = true),
                        ProfileState(11, ProfileType.PRIVATE, isQuietModeEnabled = true),
                    ),
                )
                val known = awaitItem()
                assertThat(known.map { it.label }).containsExactly("Mail", "Notes").inOrder()
                assertThat(known.single { it.label == "Mail" }.isProfilePaused).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a full disk during sync keeps the cache serving, and the next sync recovers`() =
        runTest {
            db.appCacheDao().upsert(listOf(cachedAppEntity(installedApp("Cached"), ReachIntent.TOOLS)))
            val failing = FailingCacheDao(db.appCacheDao())
            val repo = repository(cacheDao = failing)

            repo.apps.test {
                assertThat(awaitItem().map { it.label }).containsExactly("Cached")
                installed.state.value = listOf(installedApp("Fresh"))
                realTime { while (repo.lastSyncError == null) delay(10) }
                assertThat(repo.lastSyncError).isInstanceOf(SQLiteFullException::class.java)
                // Still serving: nothing re-emitted, nothing crashed (runTest would fail on an
                // exception escaping the application scope).
                expectNoEvents()

                failing.failWrites = false
                installed.state.value = listOf(installedApp("Fresh"), installedApp("Fresher"))
                assertThat(awaitItem().map { it.label }).containsExactly("Fresh", "Fresher").inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a refresh on a full disk doesn't throw`() =
        runTest {
            val failing = FailingCacheDao(db.appCacheDao())
            val repo = repository(cacheDao = failing)
            installed.state.value = listOf(installedApp("Fresh"))
            repo.refresh()
            assertThat(repo.lastSyncError).isInstanceOf(SQLiteFullException::class.java)
        }

    @Test
    fun `a refresh whose system read fails doesn't throw`() =
        runTest {
            val broken =
                object : InstalledAppsSource {
                    override val installedApps: Flow<List<InstalledApp>> = flow { throw IllegalStateException("dead binder") }
                }
            val repo = repository(installedAppsSource = broken)
            repo.refresh()
            assertThat(repo.lastSyncError).isInstanceOf(IllegalStateException::class.java)
        }

    @Test
    fun `an observed query that can't open the database is retried`() =
        runTest {
            db.appCacheDao().upsert(listOf(cachedAppEntity(installedApp("Cached"), ReachIntent.TOOLS)))
            val flaky = FailingCacheDao(db.appCacheDao(), failFirstObserve = true)
            val repo = repository(cacheDao = flaky)
            assertThat(repo.apps.awaitFirst { it.isNotEmpty() }.map { it.label }).containsExactly("Cached")
            assertThat(repo.lastSyncError).isInstanceOf(SQLiteCantOpenDatabaseException::class.java)
        }

    @Test
    fun `refresh re-sorts after a language change`() =
        runTest {
            Locale.setDefault(Locale.US)
            val repo = repository()
            installed.state.value = listOf("cuz", "çay").map { installedApp(it) }
            repo.apps.test {
                // English collation: ç is an accented c, so "çay" < "cuz" (a < u).
                assertThat(nextMatching { it.size == 2 }.map { it.label }).containsExactly("çay", "cuz").inOrder()
                Locale.setDefault(Locale.forLanguageTag("tr"))
                repo.refresh()
                // Turkish: ç is its own letter after c.
                assertThat(nextMatching { it.first().label == "cuz" }.map { it.label }).containsExactly("cuz", "çay").inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Home and predictions share one catalog pipeline`() =
        runTest {
            val counting = CountingProfileSource(profiles)
            val repo = repository(profileSource = counting)
            installed.state.value = listOf(installedApp("Notes"))
            val a = backgroundScope.launch(Dispatchers.Default) { repo.apps.collect {} }
            val b = backgroundScope.launch(Dispatchers.Default) { repo.apps.collect {} }
            repo.apps.awaitFirst { it.isNotEmpty() }
            assertThat(counting.subscriptions.get()).isEqualTo(1)
            a.cancel()
            b.cancel()
        }

    /** Waits (wall clock) for the next emission matching [predicate] and returns it. */
    private suspend fun <T> ReceiveTurbine<T>.nextMatching(predicate: (T) -> Boolean): T =
        realTime {
            var item = awaitItem()
            while (!predicate(item)) item = awaitItem()
            item
        }

    /** A profile source that hasn't answered yet; tests emit into [state] by hand. */
    private class SilentProfileSource : ProfileSource {
        val state = MutableSharedFlow<List<ProfileState>>()
        override val profiles: Flow<List<ProfileState>> = state

        override fun setQuietMode(
            userSerial: Long,
            enabled: Boolean,
        ) = false
    }

    /** Counts how often [ProfileSource.profiles] is subscribed: once per catalog pipeline. */
    private class CountingProfileSource(
        private val real: ProfileSource,
    ) : ProfileSource by real {
        val subscriptions = AtomicInteger()
        override val profiles: Flow<List<ProfileState>> = real.profiles.onStart { subscriptions.incrementAndGet() }
    }

    /**
     * The real DAO, except that writes throw `SQLiteFullException` while [failWrites] is set
     * (a full disk), and optionally the first observed query throws
     * `SQLiteCantOpenDatabaseException`.
     */
    private class FailingCacheDao(
        private val real: AppCacheDao,
        failFirstObserve: Boolean = false,
    ) : AppCacheDao() {
        @Volatile
        var failWrites = !failFirstObserve
        private val observeFailures = AtomicInteger(if (failFirstObserve) 1 else 0)

        override fun observeAll(): Flow<List<CachedAppEntity>> =
            flow {
                if (observeFailures.getAndDecrement() > 0) throw SQLiteCantOpenDatabaseException("unable to open database file")
                emitAll(real.observeAll())
            }

        override suspend fun getAll(): List<CachedAppEntity> = real.getAll()

        override suspend fun upsert(rows: List<CachedAppEntity>) {
            if (failWrites) throw SQLiteFullException("database or disk is full")
            real.upsert(rows)
        }

        override suspend fun delete(rows: List<CachedAppEntity>) {
            if (failWrites) throw SQLiteFullException("database or disk is full")
            real.delete(rows)
        }
    }
}
