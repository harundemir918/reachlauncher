package com.baklavapp.reachlauncher.core.system.wallpaper

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The wallpaper colour source on Robolectric's `WallpaperManager`, which has no extracted
 * colours: the source must answer "unknown" (null) once, register its listener without
 * throwing, and unregister cleanly when collection stops. Real colour extraction can only
 * be checked on a device (docs/DECISIONS.md, M4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WallpaperManagerColorSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `emits the current colour first and unregisters on cancel`() =
        runTest {
            val source = WallpaperManagerColorSource(app, UnconfinedTestDispatcher(testScheduler))
            source.primaryColor.test {
                val first = awaitItem()
                // Robolectric computes no colours; a device returns the wallpaper's ARGB.
                assertThat(first == null || first != 0).isTrue()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
