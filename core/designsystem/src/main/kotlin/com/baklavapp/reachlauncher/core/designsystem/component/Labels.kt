package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.toLabelCase

/*
 * MonoLabel and AccentBadge — the small JetBrains Mono texts all over Reach ("NOW",
 * "INTENT · 5 APPS", "3 NEW").
 *
 * Flutter analogy: a `Text` with a fixed `TextStyle` from your theme, plus `toUpperCase()`
 * — wrapped in a StatelessWidget so nobody forgets the locale-aware upper-casing.
 */

/**
 * An UPPERCASE mono label. Pass natural-case text (from `strings.xml`); it is upper-cased
 * with the current locale, so Turkish "iş" shows as "İŞ" (a plain `uppercase()` gives "IŞ").
 *
 * TalkBack reads the *natural-case* [text]: screen readers sometimes spell out all-caps
 * words letter by letter.
 *
 * @param color defaults to `muted`, the colour of nearly every label in the mockups.
 * @param style `label` (11sp); use `labelSmall` (10sp) for tags, `count` (12sp) for counts.
 * @param maxLines unlimited by default so labels reflow at 200% font size instead of clipping.
 */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ReachTheme.colors.muted,
    style: TextStyle = ReachTheme.typography.label,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val locale = currentLocale()
    val shown = remember(text, locale) { text.toLabelCase(locale) }
    Text(
        text = shown,
        modifier = modifier.semantics { contentDescription = text },
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/**
 * A small accent pill with mono text: "3 NEW" on intent words, an unread count on app rows.
 *
 * Purely visual — the row that shows it puts the meaning into its own description
 * ("Talk, 3 new notifications"), so screen readers never hear "3 NEW" out of context.
 * Text is upper-cased with the current locale.
 */
@Composable
fun AccentBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    Box(
        modifier =
            modifier
                .background(colors.accent, RoundedCornerShape(ReachComponentDimens.pillCorner))
                .padding(
                    horizontal = ReachComponentDimens.badgeHorizontalPadding,
                    vertical = ReachComponentDimens.badgeVerticalPadding,
                ),
    ) {
        MonoLabel(text = text, color = colors.onAccent, maxLines = 1)
    }
}

@Preview(name = "Labels and badges")
@Composable
private fun LabelsPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) {
        MonoLabel("now · learns your routine")
        MonoLabel("iş · istanbul", style = ReachTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.intentWordTrailingGap)) {
            AccentBadge("3 new")
            AccentBadge("2")
            AccentBadge("99+")
        }
    }
}
