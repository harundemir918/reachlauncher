package com.baklavapp.reachlauncher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The app's `Application` object; `@HiltAndroidApp` generates the root DI container.
 *
 * Flutter analogy: the place you'd call `configureDependencies()` (get_it/injectable)
 * before `runApp()`. Hilt generates that setup at compile time, so there is no code here.
 */
@HiltAndroidApp
class ReachApplication : Application()
