package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import java.util.Locale

/*
 * The one click + accessibility recipe every Reach control uses.
 *
 * Flutter analogy: `InkWell(onTap:, onLongPress:)` wrapped in a `Semantics(label:,
 * button: true, customSemanticsActions: …)` with `excludeSemantics: true` — in one modifier.
 *
 * Compose specifics worth knowing:
 * - `combinedClickable` is `clickable` plus long-press/double-tap. It makes the node a
 *   focusable, merged accessibility node with a click action and the given `Role`
 *   (TalkBack then says "button"), and shows the theme's ripple.
 * - `clearAndSetSemantics { … }` *replaces* everything the children would say with one
 *   description, so TalkBack reads "Talk, 3 new notifications" instead of "Talk", "3 NEW"
 *   piecemeal. It must come *after* (inside) the clickable in the chain: Compose collects a
 *   node's semantics from the innermost modifier outwards, and the clear wipes only what
 *   is inside it, so the click action and role set by the clickable survive.
 * - `CustomAccessibilityAction` adds an entry to TalkBack's actions menu. Long-press
 *   features are exposed both as the long-click action ("double-tap and hold to …") and as
 *   a named custom action, because many TalkBack users browse the menu instead (spec §6.10).
 */

/**
 * Makes this element a Reach control: click (and optional long-click) handling with a
 * ripple, a semantic [role], and — when [description] is set — one spoken description that
 * replaces the children's own text.
 *
 * @param description what TalkBack reads for the whole control, including badges
 *   ("WhatsApp, 2 new notifications"). Null keeps the children's merged text instead.
 * @param onClickLabel the verb TalkBack appends: "double-tap to <label>". Null = "activate".
 * @param onLongClick long-press handler; also offered as a custom action named
 *   [onLongClickLabel] when that label is given (always give one).
 * @param accessibilityActions extra custom actions, e.g. "Move to…" and "Hide" as direct
 *   entries so TalkBack users needn't open the long-press menu.
 * @param interactionSource pass one to observe pressed state (e.g. for a pressed style)
 *   or to share it with a gesture detector; null lets Compose create it lazily.
 */
fun Modifier.reachClickable(
    onClick: () -> Unit,
    role: Role? = Role.Button,
    description: String? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
): Modifier {
    val longClickAction =
        if (onLongClick != null && onLongClickLabel != null) {
            listOf(
                CustomAccessibilityAction(onLongClickLabel) {
                    onLongClick()
                    true
                },
            )
        } else {
            emptyList()
        }
    val actions = longClickAction + accessibilityActions
    val clickable =
        combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    return when {
        description != null -> {
            clickable.clearAndSetSemantics {
                contentDescription = description
                if (actions.isNotEmpty()) customActions = actions
            }
        }

        actions.isNotEmpty() -> {
            clickable.semantics { customActions = actions }
        }

        else -> {
            clickable
        }
    }
}

/**
 * The locale to upper-case labels and monograms with. Read from `LocalLocale` (observable
 * state), not `Locale.getDefault()`, so a language change recomposes — Compose's lint
 * flags the latter inside composables (`NonObservableLocale`).
 */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalLocale.current.platformLocale
