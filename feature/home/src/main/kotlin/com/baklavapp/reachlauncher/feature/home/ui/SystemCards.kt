package com.baklavapp.reachlauncher.feature.home.ui

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.PillButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.component.PlayPauseButton
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.component.SkipNextButton
import com.baklavapp.reachlauncher.core.designsystem.component.SkipPreviousButton
import com.baklavapp.reachlauncher.core.designsystem.component.reachClickable
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.core.model.CalendarEvent
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.MediaState
import com.baklavapp.reachlauncher.feature.home.OnboardingCard
import com.baklavapp.reachlauncher.feature.home.R
import com.baklavapp.reachlauncher.feature.home.formatMediaTime
import com.baklavapp.reachlauncher.feature.home.progressAt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.baklavapp.reachlauncher.core.designsystem.R as DsR

/*
 * The M3 "system" pieces of Home and Listen detail: the calendar context line, the
 * onboarding/permission card and the Now Playing card (spec §6.3, §6.4, §7).
 *
 * Flutter analogy: stateless widgets fed from the ViewModel's `system` state, with one local
 * exception each — the Now Playing card keeps a "now" ticker (≈ a `Ticker` in a
 * `StatefulWidget`) and the calendar card owns its permission launcher (≈ awaiting
 * `Permission.calendar.request()` from `permission_handler`).
 */

private val CardCorner = 18.dp
private val CardPadding = 16.dp
private val CardGap = 8.dp
private val ButtonGap = 8.dp
private val ProgressHeight = 4.dp
private const val TICK_MILLIS = 1_000L

/**
 * The media callbacks (`HomeViewModel.onPlayPause` …), bundled for short signatures.
 * [onOpen] opens the playing app from the tapped player's bounds.
 */
@Immutable
internal data class MediaCallbacks(
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpen: (LaunchOrigin?) -> Unit,
) {
    companion object {
        /** No-op callbacks, for previews and screens without media. */
        val None = MediaCallbacks({}, {}, {}, {})
    }
}

/** The onboarding-card callbacks (`HomeViewModel.onOnboarding…`). */
@Immutable
internal data class OnboardingCallbacks(
    val onAction: (OnboardingCard) -> Unit,
    val onDismiss: (OnboardingCard) -> Unit,
    val onCalendarPermissionResult: (Boolean) -> Unit,
) {
    companion object {
        /** No-op callbacks, for previews and tests that don't care. */
        val None = OnboardingCallbacks({}, {}, {})
    }
}

/** Lines the calendar context line may take before it is ellipsized. */
private const val CONTEXT_LINE_MAX_LINES = 2

/**
 * Home's context line (spec §6.3 item 3): "10:30 · Standup", "Tomorrow 09:00 · Dentist",
 * "Today · Birthday". Muted, 15sp-ish body text; wraps at large font sizes.
 *
 * @param reading the wall clock Home already ticks for its clock, so "today" and the time
 *   format (12/24h) follow the same source.
 */
@Composable
internal fun HomeContextLine(
    event: CalendarEvent,
    reading: ClockReading,
    modifier: Modifier = Modifier,
) {
    val zone = reading.now.zone
    val today = reading.now.toLocalDate()
    // All-day events are dated in UTC by the Calendar Provider; timed ones in local time.
    val eventDate =
        if (event.isAllDay) {
            Instant.ofEpochMilli(event.beginEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            Instant.ofEpochMilli(event.beginEpochMillis).atZone(zone).toLocalDate()
        }
    val isToday = eventDate == today
    val time =
        remember(event, reading.is24Hour, zone) {
            val pattern = if (reading.is24Hour) "HH:mm" else "h:mm a"
            DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(event.beginEpochMillis).atZone(zone))
        }
    val text =
        when {
            event.isAllDay && isToday -> stringResource(R.string.home_event_today_all_day, event.title)
            event.isAllDay -> stringResource(R.string.home_event_tomorrow_all_day, event.title)
            isToday -> stringResource(R.string.home_event_today_timed, time, event.title)
            else -> stringResource(R.string.home_event_tomorrow_timed, time, event.title)
        }
    val description = stringResource(R.string.home_event_description, text)
    Text(
        text = text,
        style = ReachTheme.typography.body,
        color = ReachTheme.colors.muted,
        // A long event title wrapped to five lines on a real device and pushed the words down;
        // two lines are plenty for "13:00 · title" (TalkBack still reads all of it).
        maxLines = CONTEXT_LINE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * One compact onboarding/permission card (spec §4, §7): title, one line of why, the primary
 * action and "Not now". Not a dialog: it sits in Home's flow, and Home works fully around it.
 *
 * The calendar card asks for `READ_CALENDAR` itself, because a runtime permission needs an
 * `ActivityResultLauncher` registered by the UI (D11); the others hand off to the ViewModel,
 * which opens the right system screen.
 */
@Composable
internal fun OnboardingCardView(
    card: OnboardingCard,
    callbacks: OnboardingCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(CardCorner)
    val calendarPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callbacks.onCalendarPermissionResult(granted)
        }
    val texts = card.texts()
    val title = stringResource(texts.title)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(ReachComponentDimens.hairline, colors.line2, shape)
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        Text(
            text = title,
            style = ReachTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.fg,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(texts.why), style = ReachTheme.typography.caption, color = colors.muted)
        // FlowRow: at 200% font the two buttons stack instead of clipping.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap), verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
            PillButton(
                text = stringResource(texts.action),
                onClick = {
                    if (card == OnboardingCard.CALENDAR) {
                        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    } else {
                        callbacks.onAction(card)
                    }
                },
            )
            PillButton(
                text = stringResource(R.string.home_card_not_now),
                onClick = { callbacks.onDismiss(card) },
                style = PillButtonStyle.Outlined,
                contentDescription = stringResource(R.string.home_card_not_now_description, title),
            )
        }
    }
}

private data class CardTexts(
    val title: Int,
    val why: Int,
    val action: Int,
)

private fun OnboardingCard.texts(): CardTexts =
    when (this) {
        OnboardingCard.DEFAULT_HOME -> {
            CardTexts(R.string.home_card_home_title, R.string.home_card_home_why, R.string.home_card_home_action)
        }

        OnboardingCard.NOTIFICATION_ACCESS -> {
            CardTexts(R.string.home_card_notifications_title, R.string.home_card_notifications_why, R.string.home_card_notifications_action)
        }

        OnboardingCard.CALENDAR -> {
            CardTexts(R.string.home_card_calendar_title, R.string.home_card_calendar_why, R.string.home_card_calendar_action)
        }

        OnboardingCard.USAGE_ACCESS -> {
            CardTexts(R.string.home_card_usage_title, R.string.home_card_usage_why, R.string.home_card_usage_action)
        }
    }

/**
 * Listen's Now Playing card (spec §6.4): source app, title (and artist), a progress bar and
 * previous / play-pause / next. Tapping the source and title opens the playing app
 * ([MediaCallbacks.onOpen]); the buttons keep their own jobs.
 *
 * Progress is extrapolated from the session's last report ([progressAt]) and re-evaluated
 * once a second — only while playing *and* while the screen is at least STARTED
 * (`repeatOnLifecycle`), so a card behind another app costs nothing. With "Remove
 * animations" on, the bar jumps each second instead of gliding (spec §6.10).
 */
@Composable
internal fun NowPlayingCard(
    media: MediaState,
    callbacks: MediaCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(CardCorner)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(media, lifecycle) {
        now = SystemClock.elapsedRealtime()
        if (!media.isPlaying) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(TICK_MILLIS)
            }
        }
    }
    val origin = rememberLaunchOrigin()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .trackLaunchOrigin(origin)
                .background(colors.surface, shape)
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        // Source and title are one tap target (and one TalkBack stop) that opens the app.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .reachClickable(
                        onClick = { callbacks.onOpen(origin.toLaunchOrigin()) },
                        onClickLabel = stringResource(DsR.string.ds_media_open, media.sourceLabel),
                    ),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            MonoLabel(
                // "Paused · YouTube Music" while paused, like the Home row (it said "now playing").
                text = stringResource(if (media.isPlaying) R.string.home_now_playing else R.string.home_now_paused, media.sourceLabel),
            )
            Column {
                Text(text = media.title, style = ReachTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = colors.fg)
                media.artist?.let { Text(text = it, style = ReachTheme.typography.caption, color = colors.muted) }
            }
        }
        media.progressAt(now)?.let { fraction ->
            val position = formatMediaTime((fraction * media.durationMillis).toLong())
            val description = stringResource(R.string.home_media_progress, position, formatMediaTime(media.durationMillis))
            ProgressBar(fraction, description)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipPreviousButton(onClick = callbacks.onPrevious)
            PlayPauseButton(isPlaying = media.isPlaying, onToggle = callbacks.onPlayPause, trackTitle = media.title)
            SkipNextButton(onClick = callbacks.onNext)
        }
    }
}

/** A thin accent progress bar with a TalkBack value ("1:23 of 3:45"). */
@Composable
private fun ProgressBar(
    fraction: Float,
    description: String,
) {
    val reducedMotion = rememberReducedMotion()
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else tween(TICK_MILLIS.toInt(), easing = LinearEasing),
        label = "progress",
    )
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ProgressHeight / 2)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ProgressHeight)
                .background(colors.line2, shape)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                    stateDescription = description
                },
    ) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(colors.accentInk, shape))
    }
}
