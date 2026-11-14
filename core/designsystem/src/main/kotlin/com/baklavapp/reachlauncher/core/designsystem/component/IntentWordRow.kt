package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.formatBadgeCount
import com.baklavapp.reachlauncher.core.model.ThumbSide

/*
 * IntentWordRow — one of the six big words on Home ("Talk", "Listen", …), spec §6.3.
 *
 * Flutter analogy: a StatelessWidget taking `word`, a trailing enum-like value and
 * `onTap`, plus an optional `Widget? extra` child — Compose calls such a
 * `@Composable () -> Unit` parameter a *slot*. `modifier` / `wordModifier` ≈ the
 * Padding/GestureDetector wrappers you'd nest around the widget from outside.
 *
 * Layout: a `FlowRow` (≈ Flutter's `Wrap`) so that at 200% font size the Listen row's player
 * wraps under the word instead of being clipped off-screen.
 */

/** What an [IntentWordRow] shows after its word. */
@Immutable
sealed interface IntentRowTrailing {
    /** Muted mono app count ("5"). */
    data class AppCount(
        val count: Int,
    ) : IntentRowTrailing

    /** Accent badge "3 NEW": the intent's apps have unread notifications (spec §7.1). */
    data class NewBadge(
        val unreadCount: Int,
    ) : IntentRowTrailing

    /** Nothing after the word (e.g. while the gesture fan is open). */
    data object None : IntentRowTrailing

    companion object {
        /** The usual rule: a badge when anything is unread, the app count otherwise. */
        fun of(
            appCount: Int,
            unreadCount: Int,
        ): IntentRowTrailing = if (unreadCount > 0) NewBadge(unreadCount) else AppCount(appCount)
    }
}

/**
 * A Home intent word: 46sp Bricolage, ≥ 52dp tall, aligned to the user's [thumbSide],
 * followed by its [trailing] count or badge and an optional [extraContent].
 *
 * Tapping the word calls [onClick] (open intent detail). That tap is the accessible path for
 * everything; the press-slide-release fan is an enhancement attached by Home through
 * [wordModifier].
 *
 * Semantics: the word + trailing area is *one* button whose description includes the badge,
 * "Talk, 3 new notifications" or "Talk, 5 apps". [extraContent] keeps its own semantics
 * (e.g. the play button stays a separate, labelled button).
 *
 * ```kotlin
 * IntentWordRow(
 *     word = "Listen",
 *     trailing = IntentRowTrailing.of(appCount = 4, unreadCount = 0),
 *     onClick = { open(ReachIntent.LISTEN) },
 *     thumbSide = settings.thumbSide,
 *     wordModifier = Modifier.pointerInput(Unit) { /* press-slide-release */ },
 * ) {
 *     InlineMediaControl("Daily Mix 1", "Spotify", isPlaying, onTogglePlay)
 * }
 * ```
 *
 * @param modifier applied to the whole row (it fills the max width so it can align itself).
 * @param thumbSide LEFT/RIGHT is *physical*, not start/end: a right-thumb user wants the
 *   words on the right even in a right-to-left language, so absolute alignment is used.
 * @param wordModifier applied to the tappable word area only, *outside* its click handling.
 *   Attach the press-slide-release `pointerInput` here: being outside, your detector sees
 *   events first in the Initial pass and can consume them to suppress the tap once the fan opens.
 * @param wordColor override for gesture states: `ReachTheme.colors.dim` for inactive words
 *   while the fan is open, `accentInk` for the held word. Unspecified = `fg`.
 * @param onClickLabel TalkBack verb: "double-tap to <label>" (e.g. "open Talk apps").
 * @param accessibilityActions extra custom actions on the word button.
 * @param interactionSource observe/share the word's press state with your gesture code.
 * @param extraContent slot after the word (the Listen row's [InlineMediaControl]).
 */
@Composable
fun IntentWordRow(
    word: String,
    trailing: IntentRowTrailing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbSide: ThumbSide = ThumbSide.LEFT,
    wordModifier: Modifier = Modifier,
    wordColor: Color = Color.Unspecified,
    onClickLabel: String? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    interactionSource: MutableInteractionSource? = null,
    extraContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = ReachTheme.colors
    val description =
        when (trailing) {
            is IntentRowTrailing.AppCount -> {
                "$word, ${pluralStringResource(R.plurals.ds_app_count, trailing.count, trailing.count)}"
            }

            is IntentRowTrailing.NewBadge -> {
                "$word, ${pluralStringResource(R.plurals.ds_new_notifications, trailing.unreadCount, trailing.unreadCount)}"
            }

            IntentRowTrailing.None -> {
                word
            }
        }
    val horizontal = if (thumbSide == ThumbSide.LEFT) AbsoluteAlignment.Left else AbsoluteAlignment.Right
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.spacedBy(ReachComponentDimens.intentRowExtraGap, horizontal),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                wordModifier
                    .reachClickable(
                        onClick = onClick,
                        description = description,
                        onClickLabel = onClickLabel,
                        accessibilityActions = accessibilityActions,
                        interactionSource = interactionSource,
                    ).heightIn(min = ReachDimens.intentRowHeight)
                    .padding(vertical = ReachComponentDimens.intentWordVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.intentWordTrailingGap),
        ) {
            Text(
                text = word,
                style = ReachTheme.typography.intentWord,
                color = wordColor.takeOrElse { colors.fg },
                modifier = Modifier.alignByBaseline(),
            )
            when (trailing) {
                is IntentRowTrailing.AppCount -> {
                    MonoLabel(
                        text = trailing.count.toString(),
                        style = ReachTheme.typography.count,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                is IntentRowTrailing.NewBadge -> {
                    AccentBadge(text = stringResource(R.string.ds_badge_new, formatBadgeCount(trailing.unreadCount)))
                }

                IntentRowTrailing.None -> {
                    Unit
                }
            }
        }
        if (extraContent != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = extraContent)
        }
    }
}

@Composable
private fun IntentWordsSample(thumbSide: ThumbSide) {
    Column(verticalArrangement = Arrangement.spacedBy(ReachComponentDimens.intentRowSpacing)) {
        IntentWordRow("Talk", IntentRowTrailing.of(appCount = 5, unreadCount = 3), onClick = {}, thumbSide = thumbSide)
        IntentWordRow("Listen", IntentRowTrailing.of(appCount = 4, unreadCount = 0), onClick = {}, thumbSide = thumbSide) {
            InlineMediaControl(trackTitle = "Daily Mix 1", appLabel = "Spotify", isPlaying = true, onTogglePlay = {})
        }
        IntentWordRow("Go", IntentRowTrailing.AppCount(4), onClick = {}, thumbSide = thumbSide, wordColor = ReachTheme.colors.dim)
        IntentWordRow("Tools", IntentRowTrailing.AppCount(5), onClick = {}, thumbSide = thumbSide)
    }
}

@Preview(name = "Intent words · left", widthDp = 390)
@Composable
private fun IntentWordRowPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { IntentWordsSample(ThumbSide.LEFT) }
}

@Preview(name = "Intent words · right", widthDp = 390)
@Composable
private fun IntentWordRowRightPreview() {
    PreviewSurface(isDark = true) { IntentWordsSample(ThumbSide.RIGHT) }
}

@Preview(name = "Intent words · 200% font", widthDp = 390, fontScale = 2f)
@Composable
private fun IntentWordRowLargeFontPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { IntentWordsSample(ThumbSide.LEFT) }
}
