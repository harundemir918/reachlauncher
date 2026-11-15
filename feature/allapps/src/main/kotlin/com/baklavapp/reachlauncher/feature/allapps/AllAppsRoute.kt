package com.baklavapp.reachlauncher.feature.allapps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.baklavapp.reachlauncher.core.designsystem.component.ReachComponentDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.feature.allapps.ui.AllAppsScreen
import kotlinx.coroutines.delay

/*
 * AllAppsRoute — connects AllAppsViewModel to the stateless AllAppsScreen.
 *
 * Flutter analogy: a `BlocProvider` + `BlocBuilder` + `BlocListener` in one widget.
 * `hiltViewModel()` ≈ `context.read<AllAppsCubit>()` from get_it/injectable: Hilt builds the
 * ViewModel with its dependencies and keeps it for the Activity's lifetime.
 */

private val MessageBottomPadding = 96.dp
private val MessagePadding = 14.dp
private const val MESSAGE_DURATION_MILLIS = 3_000L

/**
 * The All Apps surface, ready to drop into Home's surface state machine.
 *
 * Back is *not* handled here (Home's `BackHandler` steps back to Home); only the long-press
 * menu claims Back while it is open.
 *
 * @param onClose called after an app really launched, so Home — not All Apps — greets the
 *   user when they return. Wire it to the same "back to Home" as system Back.
 * @param onOpenSearch the "Type to launch" pill: switch to the Search surface.
 */
@Composable
fun AllAppsRoute(
    onClose: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AllAppsRoute(viewModel = hiltViewModel(), onClose = onClose, onOpenSearch = onOpenSearch, modifier = modifier)
}

/** [AllAppsRoute] with an explicit [viewModel], for tests that build it with fakes. */
@Composable
internal fun AllAppsRoute(
    viewModel: AllAppsViewModel,
    onClose: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    var failure by remember { mutableStateOf<LaunchFailure?>(null) }

    LaunchedEffect(viewModel, locale) { viewModel.onLocaleChanged(locale) }
    // ≈ BlocListener: one-off effects, only while the UI is at least STARTED.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    AllAppsEvent.Done -> currentOnClose()
                    is AllAppsEvent.LaunchFailed -> failure = LaunchFailure(event.label)
                }
            }
        }
    }
    // Keyed on the instance: a second failure restarts the 3 s timer.
    LaunchedEffect(failure) {
        if (failure != null) {
            delay(MESSAGE_DURATION_MILLIS)
            failure = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AllAppsScreen(
            state = state,
            onLaunch = viewModel::launch,
            onAppInfo = viewModel::openAppInfo,
            onMoveToIntent = viewModel::moveToIntent,
            onSetHidden = viewModel::setHidden,
            onUninstall = viewModel::uninstall,
            onOpenSearch = onOpenSearch,
            onTogglePrivateSpace = viewModel::togglePrivateSpace,
        )
        failure?.let { LaunchFailedMessage(it.label, Modifier.align(Alignment.BottomCenter)) }
    }
}

/** One shown failure; not a data class, so two failures for one app are unequal. */
private class LaunchFailure(
    val label: String,
)

/** Reach-styled inline message ("Couldn't open Maps"), announced politely by TalkBack. */
@Composable
private fun LaunchFailedMessage(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val shape = RoundedCornerShape(ReachComponentDimens.pillCorner)
    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, bottom = MessageBottomPadding)
                .background(colors.surface, shape)
                .border(ReachComponentDimens.hairline, colors.line2, shape)
                .padding(MessagePadding)
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text = stringResource(R.string.allapps_launch_failed, label), style = ReachTheme.typography.body, color = colors.fg)
    }
}
