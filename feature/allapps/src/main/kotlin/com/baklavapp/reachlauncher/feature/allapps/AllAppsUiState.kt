package com.baklavapp.reachlauncher.feature.allapps

import androidx.compose.runtime.Immutable
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.ProfileState
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ThumbSide

/*
 * State and events of the All Apps surface.
 *
 * Flutter analogy: the immutable state class a Cubit emits (think Freezed), plus the
 * one-off "effects" a `BlocListener` reacts to. `@Immutable` promises Compose that these
 * never change after construction, so it can skip recomposing parts whose input is equal.
 */

/**
 * Everything the All Apps screen shows.
 *
 * @property loaded false until the catalog's first emission: the heading shows no count
 *   yet instead of a misleading "0 apps".
 * @property totalCount visible apps (hidden ones are not counted: they're not on the wall).
 * @property groups the word wall: one group per first letter, in alphabet order.
 * @property hidden hidden apps, shown as a final dimmed "Hidden" group so they can be
 *   launched and un-hidden (the only place they stay reachable — contract of
 *   `AppCatalogRepository.setHidden`). Empty → no group.
 * @property rail every letter of the locale's index (plus "#"), for the letter rail.
 * @property thumbSide the rail hugs this physical edge.
 * @property privateSpace the private-space section, or null when Reach can't see one
 *   (none set up, Android < 15, or Reach isn't the default home app).
 */
@Immutable
data class AllAppsUiState(
    val loaded: Boolean = false,
    val totalCount: Int = 0,
    val groups: List<LetterGroup> = emptyList(),
    val hidden: List<CatalogApp> = emptyList(),
    val rail: List<RailLetter> = emptyList(),
    val thumbSide: ThumbSide = ThumbSide.LEFT,
    val privateSpace: PrivateSpaceUi? = null,
)

/**
 * The private space's section in All Apps (D67).
 *
 * @property userSerial the profile, for `ProfileSource.setQuietMode`.
 * @property isLocked quiet mode is on: [apps] is then always empty (the catalog never emits
 *   a locked private space's apps; this state doesn't either, by construction).
 * @property apps the unlocked private space's visible apps, alphabetical.
 */
@Immutable
data class PrivateSpaceUi(
    val userSerial: Long,
    val isLocked: Boolean,
    val apps: List<CatalogApp>,
) {
    companion object {
        /**
         * The section for [profiles] and the catalog's private-space [apps], or null without a
         * private profile. Fails closed: apps are only listed while the profile is known *and*
         * unlocked.
         */
        fun of(
            profiles: List<ProfileState>,
            apps: List<CatalogApp>,
        ): PrivateSpaceUi? {
            val profile = profiles.firstOrNull { it.type == ProfileType.PRIVATE } ?: return null
            val locked = profile.isQuietModeEnabled
            return PrivateSpaceUi(
                userSerial = profile.userSerial,
                isLocked = locked,
                apps = if (locked) emptyList() else apps.filter { it.key.userSerial == profile.userSerial },
            )
        }
    }
}

/** One word-wall group: a letter ("S", or "#") and its apps in Collator order. */
@Immutable
data class LetterGroup(
    val letter: String,
    val apps: List<CatalogApp>,
)

/**
 * One letter on the rail.
 *
 * @property groupIndex index into [AllAppsUiState.groups], or null when no app starts with
 *   this letter: the rail draws it dimmed and scrubbing skips it.
 */
@Immutable
data class RailLetter(
    val letter: String,
    val groupIndex: Int?,
) {
    /** Whether jumping to this letter goes anywhere. */
    val hasApps: Boolean get() = groupIndex != null
}

/** One-off effects for the route (delivered exactly once). */
sealed interface AllAppsEvent {
    /** An app really launched: the route closes All Apps (back to Home for next time). */
    data object Done : AllAppsEvent

    /** Launching failed (e.g. the app was just uninstalled): show a short message. */
    data class LaunchFailed(
        val label: String,
    ) : AllAppsEvent
}
