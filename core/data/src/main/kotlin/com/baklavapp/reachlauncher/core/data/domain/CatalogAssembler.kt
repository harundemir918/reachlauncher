package com.baklavapp.reachlauncher.core.data.domain

import com.baklavapp.reachlauncher.core.model.AppKey
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.InstalledApp
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ReachIntent
import java.text.Collator
import java.util.Locale

/** A cached app plus the intent auto-assignment gave it when it was cached. */
data class CachedApp(
    val app: InstalledApp,
    val autoIntent: ReachIntent,
)

/**
 * The user's own choices for one app.
 *
 * @property intent "Move to…" target, or null when the user never moved the app (or the
 *   stored key is unknown to this build, docs/DECISIONS.md D15).
 */
data class UserAssignment(
    val intent: ReachIntent?,
    val hidden: Boolean,
)

/**
 * Merges the cached system data with the user's choices, launch counts and live profile
 * state into the list the UI shows. Pure, so the privacy and sorting rules are testable
 * without Room.
 *
 * Flutter analogy: the `combineLatest` mapper you'd write in a repository with rxdart,
 * pulled out into its own function.
 */
object CatalogAssembler {
    /**
     * Rules, in order:
     * 1. **Privacy (D13):** apps of a private space are dropped unless that profile is
     *    currently known *and* unlocked. "Unknown" counts as locked: on Android 15+ a
     *    launcher without `ACCESS_HIDDEN_PROFILES` doesn't even see the private profile,
     *    and stale cache rows from an unlocked session must never flash onto Home.
     * 2. Effective intent = user choice ?: auto-assignment ("a user's choice always beats
     *    auto-assignment", §6.1).
     * 3. Apps of a quiet-mode (paused) work profile are kept but flagged.
     * 4. Sorted by label with a [Collator] for [locale] (Turkish İ/ı, ç, ş… sort as a
     *    Turkish reader expects), ties broken by package/activity/profile so the order is
     *    stable between emissions.
     */
    fun assemble(
        cached: List<CachedApp>,
        assignments: Map<AppKey, UserAssignment>,
        launchCounts: Map<AppKey, Int>,
        profiles: List<ProfileState>,
        locale: Locale,
    ): List<CatalogApp> {
        val profilesBySerial = profiles.associateBy { it.userSerial }
        val collator = Collator.getInstance(locale)
        return cached
            .mapNotNull { (app, autoIntent) ->
                val profile = profilesBySerial[app.key.userSerial]
                val isPrivate = app.profile == ProfileType.PRIVATE || profile?.type == ProfileType.PRIVATE
                if (isPrivate && (profile == null || profile.isQuietModeEnabled)) return@mapNotNull null

                val assignment = assignments[app.key]
                val userIntent = assignment?.intent
                CatalogApp(
                    key = app.key,
                    label = app.label,
                    profile = app.profile,
                    intent = userIntent ?: autoIntent,
                    isUserAssigned = userIntent != null,
                    isHidden = assignment?.hidden ?: false,
                    launchCount = launchCounts[app.key] ?: 0,
                    isProfilePaused = profile?.isQuietModeEnabled ?: false,
                )
            }.sortedWith(
                Comparator<CatalogApp> { a, b -> collator.compare(a.label, b.label) }
                    .thenBy { it.key.packageName }
                    .thenBy { it.key.activityName }
                    .thenBy { it.key.userSerial },
            )
    }
}
