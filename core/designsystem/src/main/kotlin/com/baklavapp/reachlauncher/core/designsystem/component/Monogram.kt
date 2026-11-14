package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.monogramFor

/*
 * Monogram — Reach's stand-in for an app icon (spec §5.4): one or two letters in a ringed circle.
 *
 * Flutter analogy: a `StatelessWidget` like `CircleAvatar(child: Text("YM"))` with a
 * `BoxDecoration(shape: circle, border: …)`. In Compose the decoration is a `Modifier`
 * chain (`size` → `border`), read outer to inner like nested `Container`s.
 */

/** Which colours a [Monogram] uses; pick the one that matches what it sits on. */
enum class MonogramStyle {
    /** `ring` outline, `fg` letters: on bg or surface (app rows, fan rows). */
    Default,

    /** `fg` outline and letters: stronger, for search results on surface. */
    Strong,

    /** `onAccent` outline and letters: on an accent fill (highlighted fan row, top search result). */
    OnAccent,
}

/**
 * An app's monogram: the first letters of up to two words of [label] ("YouTube Music" → "YM",
 * see [monogramFor]) in JetBrains Mono inside a circle with a 1.5dp outline.
 *
 * Decorative for accessibility: it clears its own semantics because every row that shows a
 * monogram also shows (and speaks) the app name.
 *
 * @param size circle diameter. 42dp on rows (the spec); the mockups use 36dp in search and
 *   28–30dp in the gesture fan — pass a smaller [textStyle] there (e.g. `fontSize = 11.sp`).
 * @param icon a real app icon. Always null today (Reach shows names, not icons); it exists
 *   so a future "show real icons" setting can drop icons in without changing call sites.
 *   When set, the icon fills the circle and the letters and ring are not drawn.
 * @param dimmed muted letters, for apps of a paused work profile.
 */
@Composable
fun Monogram(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = ReachDimens.monogramSize,
    style: MonogramStyle = MonogramStyle.Default,
    textStyle: TextStyle = ReachTheme.typography.monogram,
    icon: Painter? = null,
    dimmed: Boolean = false,
) {
    val colors = ReachTheme.colors
    val locale = currentLocale()
    val letters = remember(label, locale) { monogramFor(label, locale) }
    val ringColor: Color
    val textColor: Color
    when (style) {
        MonogramStyle.Default -> {
            ringColor = colors.ring
            textColor = if (dimmed) colors.muted else colors.fg
        }

        MonogramStyle.Strong -> {
            ringColor = colors.fg
            textColor = if (dimmed) colors.muted else colors.fg
        }

        MonogramStyle.OnAccent -> {
            ringColor = colors.onAccent
            textColor = colors.onAccent
        }
    }
    val base = modifier.size(size).clearAndSetSemantics {}
    if (icon != null) {
        Image(
            painter = icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base.clip(CircleShape),
        )
    } else {
        Box(
            modifier = base.border(ReachDimens.monogramRing, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = letters, style = textStyle, color = textColor, maxLines = 1, softWrap = false)
        }
    }
}

@Preview(name = "Monogram")
@Composable
private fun MonogramPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) {
        Row(horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.appRowGap)) {
            Monogram("YouTube Music")
            Monogram("Spotify")
            Monogram("7-Zip", style = MonogramStyle.Strong, size = 36.dp)
            Monogram("Paused App", dimmed = true)
            Monogram("", size = 30.dp)
        }
    }
}
