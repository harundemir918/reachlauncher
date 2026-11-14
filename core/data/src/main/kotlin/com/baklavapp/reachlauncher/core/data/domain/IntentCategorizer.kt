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
        return when (category) {
            AppCategory.SOCIAL -> ReachIntent.TALK
            AppCategory.AUDIO -> ReachIntent.LISTEN
            AppCategory.MAPS -> ReachIntent.GO
            AppCategory.IMAGE, AppCategory.PRODUCTIVITY -> ReachIntent.MAKE
            AppCategory.GAME, AppCategory.VIDEO, AppCategory.NEWS -> ReachIntent.PLAY
            AppCategory.UNDEFINED, AppCategory.ACCESSIBILITY -> ReachIntent.TOOLS
        }
    }
}
