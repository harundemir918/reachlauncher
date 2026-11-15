package com.baklavapp.reachlauncher.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods Reach runs on its hot paths, so the release build ships
 * them as a Baseline Profile (docs/DECISIONS.md D68). ART then compiles that code ahead of
 * time at install, instead of interpreting it on the first launches — the main fix for
 * "the launcher is janky right after an update".
 *
 * The journeys are the ones a launcher must make instant: cold start to Home, opening an
 * intent's detail, Search (with typing) and All Apps. Elements are found through the same
 * content descriptions TalkBack reads ("Talk, 3 apps", "Type to launch"), so no test tags are
 * needed in production code.
 *
 * Run (device or emulator connected, API 33+ or a rooted API 28+):
 * `./gradlew :app:generateBaselineProfile`
 *
 * Flutter analogy: like `flutter drive` with an integration test whose only job is to warm up
 * the engine's code paths — except the output is a profile file checked into the app, not a
 * test report.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.descStartsWith("Talk")), TIMEOUT_MILLIS)

            openIntentDetail()
            openSearch()
            openAllApps()
        }

    private fun MacrobenchmarkScope.openIntentDetail() {
        device.findObject(By.descStartsWith("Talk"))?.click() ?: return
        device.wait(Until.hasObject(By.desc("Back")), TIMEOUT_MILLIS)
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openSearch() {
        device.findObject(By.desc("Type to launch"))?.click() ?: return
        device.waitForIdle()
        device.findObject(By.focused(true))?.text = "ma"
        device.waitForIdle()
        device.pressBack() // Keyboard.
        device.pressBack() // Search → Home.
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openAllApps() {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, SWIPE_STEPS)
        device.waitForIdle()
        // Scroll the word wall once so the lazy list's item composition is profiled too.
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, SWIPE_STEPS)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.baklavapp.reachlauncher"
        const val TIMEOUT_MILLIS = 5_000L
        const val SWIPE_STEPS = 20
    }
}
