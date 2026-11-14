package com.baklavapp.reachlauncher.core.data.db

import com.baklavapp.reachlauncher.core.model.AppCategory
import com.baklavapp.reachlauncher.core.model.ProfileType

/*
 * Stable storage keys for the `:core:model` enums that don't carry their own
 * `storageKey`. Written out by hand (not derived from `name`) so renaming a constant can
 * never change what is stored. Never change an existing key; only add new ones.
 */

/** Key persisted for this category. */
internal val AppCategory.storageKey: String
    get() =
        when (this) {
            AppCategory.UNDEFINED -> "undefined"
            AppCategory.GAME -> "game"
            AppCategory.AUDIO -> "audio"
            AppCategory.VIDEO -> "video"
            AppCategory.IMAGE -> "image"
            AppCategory.SOCIAL -> "social"
            AppCategory.NEWS -> "news"
            AppCategory.MAPS -> "maps"
            AppCategory.PRODUCTIVITY -> "productivity"
            AppCategory.ACCESSIBILITY -> "accessibility"
        }

/** Category for a stored [key]; unknown keys (a newer build's category) read as UNDEFINED. */
internal fun appCategoryFromStorageKey(key: String): AppCategory =
    AppCategory.entries.firstOrNull { it.storageKey == key } ?: AppCategory.UNDEFINED

/** Key persisted for this profile type. */
internal val ProfileType.storageKey: String
    get() =
        when (this) {
            ProfileType.PERSONAL -> "personal"
            ProfileType.WORK -> "work"
            ProfileType.PRIVATE -> "private"
        }

/**
 * Profile type for a stored [key]. Unknown keys read as [ProfileType.PRIVATE]: the most
 * restrictive choice, so a row we can't interpret is hidden rather than leaked. The next
 * sync from the system rewrites the row anyway.
 */
internal fun profileTypeFromStorageKey(key: String): ProfileType =
    ProfileType.entries.firstOrNull { it.storageKey == key } ?: ProfileType.PRIVATE
