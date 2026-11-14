package com.baklavapp.reachlauncher.feature.home

import androidx.compose.runtime.Immutable
import com.baklavapp.reachlauncher.core.model.ReachIntent

/*
 * HomeSurface — which "screen" the one Home surface currently shows (docs/PLAN.md §3).
 *
 * Flutter analogy — why there is no `Navigator`: in Flutter you'd push routes
 * (`Navigator.push(IntentDetailPage())`) and let the route stack decide what Back does.
 * A launcher is different: the Home button must jump back to Home from *anywhere*, Back
 * must step back exactly one level, and nothing may ever be "under" Home. That is a tiny,
 * closed state machine, so we model it as one sealed value in the ViewModel (≈ a BLoC
 * state enum) and let Compose switch on it (≈ a `switch` inside one `BlocBuilder`). No
 * route stack, no back-stack bookkeeping, and every transition is unit-testable.
 *
 * Why the surface isn't saved (no `SavedStateHandle`, no `rememberSaveable`): in Flutter
 * you'd use `RestorationMixin` to bring a screen back after the OS kills the app. Android's
 * equivalent is `rememberSaveable` / `SavedStateHandle`. Reach deliberately opts out: the
 * manifest marks Home `stateNotNeeded`, and after a process death the right screen to show
 * is always Home itself, so the surface lives in a plain `MutableStateFlow`. (It still
 * survives rotation and dark-mode changes, because the ViewModel does.)
 */

/**
 * The surfaces of Reach's single Home screen. Exactly one is active at a time; the
 * transitions live in [HomeViewModel] (`onIntentSelected`, `onBack`, `onHomePressed`…).
 *
 * A `sealed interface` (≈ a Dart `sealed class`) lets `when` check that every surface is
 * handled, so adding one later is a compile error everywhere it matters.
 */
@Immutable
sealed interface HomeSurface {
    /** The Home screen itself: clock, Now row, intent words, search pill. */
    data object Home : HomeSurface

    /** The app list of one [intent] (spec §6.4). Chips switch between intents in place. */
    data class IntentDetail(
        val intent: ReachIntent,
    ) : HomeSurface

    /** Type-to-launch search (real screen: `:feature:search`, M2). */
    data object Search : HomeSurface

    /** The A–Z list of every app (real screen: `:feature:allapps`, M2). */
    data object AllApps : HomeSurface

    /** The Appearance sheet, drawn *over* Home (real sheet: `:feature:appearance`, M4). */
    data object Appearance : HomeSurface
}
