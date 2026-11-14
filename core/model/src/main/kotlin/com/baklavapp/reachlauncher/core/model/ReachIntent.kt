package com.baklavapp.reachlauncher.core.model

/**
 * The six fixed "intents" that replace the icon grid on Home.
 *
 * Every launchable app belongs to exactly one intent: first by auto-assignment from its
 * Play category ([AppCategory]), later by the user's own choice ("Move to…"). The set is
 * deliberately closed — Home is designed around six words in the thumb zone.
 *
 * Flutter analogy: a plain Dart `enum`. Kotlin enums can also carry properties and
 * methods, like Dart's enhanced enums.
 *
 * @property storageKey stable key persisted in Room. Never rename it; renaming the
 *   enum constant itself is safe because we never persist [name].
 */
enum class ReachIntent(
    val storageKey: String,
) {
    TALK("talk"),
    LISTEN("listen"),
    GO("go"),
    MAKE("make"),
    PLAY("play"),
    TOOLS("tools"),
    ;

    companion object {
        /**
         * Finds the intent for a persisted [key], or null for an unknown key (e.g. written
         * by a newer build). Callers treat null as "no user assignment", so the app falls
         * back to auto-assignment instead of silently landing in Tools as a user choice.
         */
        fun fromStorageKey(key: String): ReachIntent? = entries.firstOrNull { it.storageKey == key }
    }
}
