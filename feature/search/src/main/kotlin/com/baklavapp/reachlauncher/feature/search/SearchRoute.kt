package com.baklavapp.reachlauncher.feature.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.baklavapp.reachlauncher.feature.search.ui.SearchScreen
import kotlinx.coroutines.delay

/*
 * SearchRoute — the public entry point of `:feature:search`. Home shows it for its Search
 * surface and passes two callbacks; everything else is internal to this module.
 *
 * Flutter analogy:
 * - `hiltViewModel()` ≈ `BlocProvider(create: …)` + `context.read<SearchCubit>()`, with the
 *   dependencies injected by Hilt (≈ get_it/injectable).
 * - `collectAsStateWithLifecycle()` ≈ `BlocBuilder`; the `events` collector ≈ `BlocListener`.
 * - `rememberLauncherForActivityResult(RequestPermission())` ≈ permission_handler's
 *   `Permission.contacts.request()`: it shows the system dialog and calls back with the
 *   answer. It must be registered during composition (Android needs to re-deliver the
 *   result if the Activity is recreated while the dialog is up), and launched from a tap.
 * - `FocusRequester.requestFocus()` ≈ `FocusNode.requestFocus()`, and
 *   `LocalSoftwareKeyboardController.show()` ≈ `SystemChannels.textInput.invokeMethod('TextInput.show')`.
 *
 * System Back is *not* handled here: Home's `BackHandler` already closes Search, and a
 * second handler would compete with it.
 */

private const val MESSAGE_DURATION_MILLIS = 3_000L

/**
 * The search surface ("type to launch"): keyboard up on open, results right above it.
 *
 * @param onClose leave search (after a launch, a command, or the close button). Home
 *   should switch back to its Home surface.
 * @param onOpenAppearance the "Appearance settings" command was chosen: Home should show
 *   the Appearance sheet (which replaces Search).
 */
@Composable
fun SearchRoute(
    onClose: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The input's text lives here, in UI state: typing updates it synchronously and the
    // ViewModel only receives a copy, so a slow ranking pass can never make the cursor jump.
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<SearchMessage?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val contactsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onContactsPermissionResult(granted)
        }

    DisposableEffect(viewModel) {
        viewModel.onOpened(initialQuery = query)
        onDispose { viewModel.onClosed() }
    }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    // Contacts access may have been granted in system settings while Search was open.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    SearchEvent.Close -> {
                        keyboard?.hide()
                        onClose()
                    }

                    SearchEvent.OpenAppearance -> {
                        keyboard?.hide()
                        onOpenAppearance()
                    }

                    is SearchEvent.LaunchFailed -> {
                        message = SearchMessage.LaunchFailed(event.label)
                    }

                    SearchEvent.NoHandler -> {
                        message = SearchMessage.NoHandler()
                    }
                }
            }
        }
    }
    // Keyed on the message *instance*, so a repeated failure restarts the timer.
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DURATION_MILLIS)
            message = null
        }
    }

    SearchScreen(
        state = state,
        query = query,
        onQueryChange = {
            query = it
            message = null
            viewModel.onQueryChange(it)
        },
        onSubmit = viewModel::onSubmit,
        onResultClick = viewModel::onResultClick,
        onContactAction = viewModel::onContactAction,
        onIncludeContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
        onClose = {
            keyboard?.hide()
            onClose()
        },
        message =
            when (val shown = message) {
                is SearchMessage.LaunchFailed -> stringResource(R.string.search_launch_failed, shown.label)
                is SearchMessage.NoHandler -> stringResource(R.string.search_action_unavailable)
                null -> null
            },
        focusRequester = focusRequester,
        modifier = modifier,
    )
}

/**
 * One shown message. Plain classes (not data classes / objects) on purpose: two failures
 * in a row must be unequal so `LaunchedEffect(message)` restarts its timer.
 */
private sealed class SearchMessage {
    class LaunchFailed(
        val label: String,
    ) : SearchMessage()

    class NoHandler : SearchMessage()
}
