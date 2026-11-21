package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme

/*
 * Media controls — the Listen row's inline player (spec §6.3) and the transport buttons
 * of the Now Playing card (spec §6.4).
 *
 * Flutter analogy: small StatelessWidgets around `IconButton`, each taking the playback
 * state as a plain parameter and reporting taps through callbacks (state hoisting —
 * the same "lift state up" you'd do with a Cubit owning `isPlaying`).
 */

/**
 * The accent play/pause circle. Its label names the action *and* the track
 * ("Pause Daily Mix 1"), so TalkBack users know what will happen.
 *
 * @param trackTitle current track; null → just "Play" / "Pause".
 * @param size 44dp inline on Home, 48dp in the Now Playing card (touch target is ≥ 48dp either way).
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trackTitle: String? = null,
    size: Dp = ReachDimens.minTouchTarget,
) {
    val description =
        when {
            trackTitle == null && isPlaying -> stringResource(R.string.ds_pause)
            trackTitle == null -> stringResource(R.string.ds_play)
            isPlaying -> stringResource(R.string.ds_pause_track, trackTitle)
            else -> stringResource(R.string.ds_play_track, trackTitle)
        }
    IconCircleButton(
        icon = if (isPlaying) ReachIcons.Pause else ReachIcons.Play,
        contentDescription = description,
        onClick = onToggle,
        modifier = modifier,
        style = IconCircleButtonStyle.Filled,
        size = size,
    )
}

/** Plain "previous track" button for the Now Playing card. */
@Composable
fun SkipPreviousButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconCircleButton(
        icon = ReachIcons.SkipPrevious,
        contentDescription = stringResource(R.string.ds_previous_track),
        onClick = onClick,
        modifier = modifier,
        style = IconCircleButtonStyle.Plain,
    )
}

/** Plain "next track" button for the Now Playing card. */
@Composable
fun SkipNextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconCircleButton(
        icon = ReachIcons.SkipNext,
        contentDescription = stringResource(R.string.ds_next_track),
        onClick = onClick,
        modifier = modifier,
        style = IconCircleButtonStyle.Plain,
    )
}

/**
 * The Listen row's inline player: play/pause circle, track title and "PLAYING · <APP>".
 * Designed to be dropped into [IntentWordRow]'s `extraContent` slot; it is a separate
 * touch target from the word itself. Show it only while a media session is active.
 *
 * The title is single-line with an ellipsis (it is never the only place the title is
 * available: the button's label carries the full title).
 *
 * @param appLabel the media app's name, natural case ("Spotify"); shown upper-cased.
 * @param onOpen tap on the title: open the playing app. TalkBack hears "double-tap to open
 *   Spotify". Null = the title is not tappable.
 */
@Composable
fun InlineMediaControl(
    trackTitle: String,
    appLabel: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.ds_media_open, appLabel)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.inlineMediaGap),
    ) {
        PlayPauseButton(
            isPlaying = isPlaying,
            onToggle = onTogglePlay,
            trackTitle = trackTitle,
            size = ReachComponentDimens.inlinePlayButtonSize,
        )
        // One TalkBack stop for "Daily Mix 1, playing · Spotify" (a clickable merges its children).
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .then(
                        if (onOpen != null) {
                            // No rounded clip: the text starts at the edge and a clip cut its first letter.
                            Modifier
                                .reachClickable(onClick = onOpen, onClickLabel = openLabel)
                                .heightIn(min = ReachDimens.minTouchTarget)
                        } else {
                            Modifier.semantics(mergeDescendants = true) {}
                        },
                    ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = trackTitle,
                style = ReachTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = ReachTheme.colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MonoLabel(
                text = stringResource(if (isPlaying) R.string.ds_media_playing else R.string.ds_media_paused, appLabel),
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "Media controls")
@Composable
private fun MediaControlsPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) {
        InlineMediaControl(trackTitle = "Daily Mix 1", appLabel = "Spotify", isPlaying = true, onTogglePlay = {})
        InlineMediaControl(trackTitle = "Daily Mix 1", appLabel = "Spotify", isPlaying = false, onTogglePlay = {})
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkipPreviousButton(onClick = {})
            PlayPauseButton(isPlaying = true, onToggle = {}, trackTitle = "Daily Mix 1")
            SkipNextButton(onClick = {})
        }
    }
}
