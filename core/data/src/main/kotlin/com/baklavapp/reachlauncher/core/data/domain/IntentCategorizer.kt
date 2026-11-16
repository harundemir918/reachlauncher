package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.ReachIntent

/**
 * Auto-assigns an app to one of the six intents the first time Reach sees it (spec §6.1).
 *
 * Why it is a pure function of the category: `ApplicationInfo.category` is the only signal
 * every app declares without Reach needing extra permissions, and keeping this free of
 * Android types means the whole table is unit-tested on the JVM.
 *
 * The result is only the *automatic* intent. A user's "Move to…" choice is stored
 * separately and always wins (see `AppCatalogRepositoryImpl`).
 *
 * Flutter analogy: a top-level pure function you'd unit test with `package:test`, no
 * widgets or plugins involved.
 */
object IntentCategorizer {
    /**
     * @param category the app's category mirror ([AppCategory.UNDEFINED] when it declares none).
     * @param packageName the app's package, compared against [talkRolePackages].
     * @param talkRolePackages packages holding the default dialer or SMS role. Phone and
     *   Messages apps rarely declare `CATEGORY_SOCIAL`, but they are the most "Talk" apps
     *   on the device, so the role overrides the category.
     */
    fun categorize(
        category: AppCategory,
        packageName: String,
        talkRolePackages: Set<String>,
    ): ReachIntent {
        if (packageName in talkRolePackages) return ReachIntent.TALK
        KNOWN_APPS[packageName]?.let { return it }
        return when (category) {
            AppCategory.SOCIAL -> ReachIntent.TALK
            AppCategory.AUDIO -> ReachIntent.LISTEN
            AppCategory.MAPS -> ReachIntent.GO
            AppCategory.IMAGE, AppCategory.PRODUCTIVITY -> ReachIntent.MAKE
            AppCategory.GAME, AppCategory.VIDEO, AppCategory.NEWS -> ReachIntent.PLAY
            AppCategory.UNDEFINED, AppCategory.ACCESSIBILITY -> ReachIntent.TOOLS
        }
    }

    /**
     * Well-known apps whose declared category points to the wrong intent (D73). Found on a
     * real device: Chrome, Clock, Gmail and the Play Store declare `PRODUCTIVITY` (→ Make),
     * Samsung's Gallery declares nothing (→ Tools). A browser, a clock and a calculator are
     * tools; mail is talking; camera and gallery are making. Only the automatic intent is
     * affected — a user's "Move to…" still wins.
     */
    private val KNOWN_APPS: Map<String, ReachIntent> =
        buildMap {
            listOf(
                "com.google.android.gm",
                "com.samsung.android.email.provider",
                "com.microsoft.office.outlook",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.google.android.contacts",
                "com.samsung.android.app.contacts",
                "com.whatsapp",
                "org.telegram.messenger",
                "org.thoughtcrime.securesms",
            ).forEach { put(it, ReachIntent.TALK) }
            listOf(
                "com.google.android.apps.youtube.music",
                "com.spotify.music",
                "com.google.android.apps.podcasts",
            ).forEach { put(it, ReachIntent.LISTEN) }
            listOf(
                "com.google.android.apps.maps",
                "com.waze",
            ).forEach { put(it, ReachIntent.GO) }
            listOf(
                "com.google.android.GoogleCamera",
                "com.sec.android.app.camera",
                "com.android.camera2",
                "com.google.android.apps.photos",
                "com.sec.android.gallery3d",
                "com.google.android.keep",
                "com.samsung.android.app.notes",
                // Google Calendar declares PRODUCTIVITY (→ Make); Samsung's declares nothing.
                "com.samsung.android.calendar",
            ).forEach { put(it, ReachIntent.MAKE) }
            listOf(
                "com.google.android.youtube",
                "com.netflix.mediaclient",
            ).forEach { put(it, ReachIntent.PLAY) }
            listOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.microsoft.emmx",
                "com.opera.browser",
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.google.android.calculator",
                "com.sec.android.app.popupcalculator",
                "com.android.vending",
                "com.google.android.googlequicksearchbox",
                "com.android.settings",
                "com.google.android.apps.nbu.files",
                "com.sec.android.app.myfiles",
            ).forEach { put(it, ReachIntent.TOOLS) }
        }
}
