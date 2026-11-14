package com.baklavapp.reachlauncher

import android.app.Application
import com.baklavapp.reachlauncher.core.system.ActivityTracker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The app's `Application` object; `@HiltAndroidApp` generates the root DI container.
 *
 * It also registers [ActivityTracker], which lets the singleton app launcher reach the
 * resumed Activity for launch animations and the home-role dialog (docs/DECISIONS.md D11).
 *
 * Flutter analogy: the place you'd call `configureDependencies()` (get_it/injectable)
 * before `runApp()`. Hilt generates the container at compile time; `@Inject lateinit var`
 * fields are filled in during `super.onCreate()`.
 */
@HiltAndroidApp
class ReachApplication : Application() {
    @Inject
    lateinit var activityTracker: ActivityTracker

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above here.
        registerActivityLifecycleCallbacks(activityTracker)
    }
}
