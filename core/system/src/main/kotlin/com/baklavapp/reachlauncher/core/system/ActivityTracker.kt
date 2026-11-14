package com.baklavapp.reachlauncher.core.system

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which Activity is currently resumed, so singletons like `SystemAppLauncher`
 * can use it for things that need an Activity — the clip-reveal launch animation (needs a
 * View) and the home-role dialog (needs `startActivityForResult`) — without ViewModels
 * ever holding an Android object (docs/DECISIONS.md D11).
 *
 * **Wiring (done once, in `:app`):**
 * ```kotlin
 * @HiltAndroidApp
 * class ReachApplication : Application() {
 *     @Inject lateinit var activityTracker: ActivityTracker
 *     override fun onCreate() {
 *         super.onCreate() // Hilt injects the fields here
 *         registerActivityLifecycleCallbacks(activityTracker)
 *     }
 * }
 * ```
 *
 * Why a [WeakReference]: this object lives as long as the process, an Activity doesn't.
 * A strong reference would leak the whole Activity (and its view tree) after it is
 * destroyed. We also drop the reference on pause, because an animation or dialog started
 * from a paused Activity would play behind whatever is in front.
 *
 * All lifecycle callbacks run on the main thread; [resumedActivity] is meant to be read
 * there too (ViewModel event handlers run on the main thread).
 *
 * Flutter analogy: like a `NavigatorObserver` that keeps the current route's
 * `BuildContext` around — except Android hands us the Activity itself.
 */
@Singleton
class ActivityTracker
    @Inject
    constructor() : Application.ActivityLifecycleCallbacks {
        @Volatile
        private var resumed: WeakReference<Activity>? = null

        /** The resumed Activity, or null when none is resumed (or it is finishing). */
        val resumedActivity: Activity?
            get() = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

        override fun onActivityResumed(activity: Activity) {
            resumed = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumed?.get() === activity) resumed = null
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (resumed?.get() === activity) resumed = null
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit
    }
