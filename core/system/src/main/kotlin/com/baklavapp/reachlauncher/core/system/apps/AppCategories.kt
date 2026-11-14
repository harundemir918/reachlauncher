package com.baklavapp.reachlauncher.core.system.apps

import android.content.pm.ApplicationInfo
import com.baklavapp.reachlauncher.core.model.AppCategory

/**
 * Maps `ApplicationInfo.category` (API 26+) to the Android-free [AppCategory] mirror.
 * Unknown values — including categories added in future Android versions — become
 * [AppCategory.UNDEFINED], which auto-assigns to Tools.
 */
internal fun appCategoryOf(category: Int): AppCategory =
    when (category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.IMAGE
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        CATEGORY_ACCESSIBILITY -> AppCategory.ACCESSIBILITY
        else -> AppCategory.UNDEFINED
    }

/**
 * `ApplicationInfo.CATEGORY_ACCESSIBILITY` was added in API 31; the constant's value is
 * fixed, and older Android versions simply never report it. Spelled out so the mapping
 * needs no version check.
 */
private const val CATEGORY_ACCESSIBILITY = 8
