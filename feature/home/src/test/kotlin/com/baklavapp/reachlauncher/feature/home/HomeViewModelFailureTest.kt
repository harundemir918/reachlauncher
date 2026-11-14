package com.baklavapp.reachlauncher.feature.home

import android.database.sqlite.SQLiteFullException
import com.baklavapp.reachlauncher.core.data.fake.FakeAppCatalogRepository
import com.baklavapp.reachlauncher.core.data.fake.FakeLaunchHistoryRepository
import com.baklavapp.reachlauncher.core.data.repository.AppCatalogRepository
import com.baklavapp.reachlauncher.core.data.repository.LaunchHistoryRepository
import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A full disk must not crash the launcher. Runs under Robolectric only so the *real*
 * `SQLiteFullException` (an Android class) can be thrown. `runTest` fails the test on any
 * exception that escapes a coroutine — including ones in `viewModelScope` — so "no crash"
 * is checked by the test simply passing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelFailureTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FullDiskHistory(
        real: FakeLaunchHistoryRepository = FakeLaunchHistoryRepository(),
    ) : LaunchHistoryRepository by real {
        override suspend fun recordLaunch(
            key: AppKey,
            epochMillis: Long,
        ) = throw SQLiteFullException("database or disk is full")
    }

    private class FullDiskCatalog(
        real: FakeAppCatalogRepository,
    ) : AppCatalogRepository by real {
        override suspend fun refresh() = throw SQLiteFullException("database or disk is full")
    }

    @Test
    fun `a launch still returns Home when history can't be written`() =
        runTest {
            val f = HomeFixture(sampleApps, historyRepository = FullDiskHistory())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.viewModel.uiState.collect {} }
            f.viewModel.onIntentSelected(ReachIntent.GO)

            f.viewModel.launch(sampleApps.first { it.label == "Maps" }, null)

            assertThat(f.launcher.launched.map { it.packageName }).containsExactly("pkg.maps")
            assertThat(f.surface).isEqualTo(HomeSurface.Home)
        }

    @Test
    fun `a failing refresh on resume keeps serving the catalog`() =
        runTest {
            val f = HomeFixture(catalogRepository = FullDiskCatalog(FakeAppCatalogRepository(sampleApps)))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.viewModel.uiState.collect {} }

            f.viewModel.onResume()

            assertThat(f.homeRole.recheckCount).isEqualTo(1)
            assertThat(f.state.intents.sumOf { it.appCount }).isEqualTo(sampleApps.size)
        }
}
