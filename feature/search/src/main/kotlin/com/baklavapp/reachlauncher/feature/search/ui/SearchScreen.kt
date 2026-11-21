package com.baklavapp.reachlauncher.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baklavapp.reachlauncher.core.designsystem.component.IconCircleButton
import com.baklavapp.reachlauncher.core.designsystem.component.IconCircleButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.Monogram
import com.baklavapp.reachlauncher.core.designsystem.component.MonogramStyle
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.PillButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.component.reachClickable
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.theme.paletteDisplayName
import com.baklavapp.reachlauncher.core.designsystem.util.rememberReducedMotion
import com.baklavapp.reachlauncher.core.model.Contact
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ProfileType
import com.baklavapp.reachlauncher.core.model.ThemeMode
import com.baklavapp.reachlauncher.feature.search.ContactAction
import com.baklavapp.reachlauncher.feature.search.R
import com.baklavapp.reachlauncher.feature.search.SearchCommand
import com.baklavapp.reachlauncher.feature.search.SearchResult
import com.baklavapp.reachlauncher.feature.search.SearchUiState
import kotlin.math.roundToInt
import com.baklavapp.reachlauncher.core.designsystem.R as DsR

/*
 * The stateless search screen — draws a SearchUiState and reports taps; SearchRoute wires
 * it to SearchViewModel. Tests and previews drive it directly.
 *
 * Layout, bottom-up (the thumb zone is at the bottom, spec §6.6): the keyboard; the input
 * with its "APP, CONTACT OR ACTION" label right above it; the results right above the
 * input, the top one accent-filled with an "ENTER ↵" hint. Everything sits *above* the
 * keyboard because the whole column is padded by `WindowInsets.safeDrawing`, which includes
 * the IME inset. The results are a LazyColumn aligned to its bottom, so with few results
 * they hug the input, and at 200% font they scroll instead of being clipped.
 *
 * Flutter analogy:
 * - `BasicTextField` ≈ `EditableText`: a bare text input we style ourselves (no Material
 *   underline/label chrome), with `KeyboardOptions` ≈ `textInputAction` / `keyboardType`
 *   and `KeyboardActions(onGo = …)` ≈ `onSubmitted`.
 * - `FocusRequester` ≈ a `FocusNode`; `requestFocus()` is the same call (done in
 *   SearchRoute, together with showing the keyboard).
 * - `windowInsetsPadding(WindowInsets.safeDrawing)` ≈ `Scaffold(resizeToAvoidBottomInset:
 *   true)` + `SafeArea` in one.
 */

private val ScreenTopPadding = 12.dp
private val ResultGap = 6.dp
private val ResultCorner = 18.dp
private val ResultMinHeight = 56.dp
private val ResultHorizontalPadding = 14.dp
private val ResultVerticalPadding = 8.dp
private val ResultContentGap = 14.dp
private val ResultMonogramSize = 36.dp
private val ChooserPadding = 8.dp
private val InputTopGap = 20.dp
private val InputLabelGap = 6.dp
private val InputUnderline = 2.dp
private val InputVerticalPadding = 8.dp
private val SectionLabelBottom = 2.dp

/**
 * Search, stateless.
 *
 * @param state ranked rows and flags from `SearchViewModel`.
 * @param query the text in the input. Owned by the caller (UI state), so typing never waits
 *   for ranking.
 * @param onSubmit Enter / the keyboard's Go key, with the top row's on-screen bounds.
 * @param onResultClick a row was tapped, with its bounds for the launch animation.
 * @param onIncludeContacts the "Include contacts" row was tapped (request the permission).
 * @param message a short "couldn't open" line, or null.
 * @param focusRequester attached to the input, so the caller can focus it on open.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (LaunchOrigin?) -> Unit,
    onResultClick: (SearchResult, LaunchOrigin?) -> Unit,
    onContactAction: (Contact, ContactAction) -> Unit,
    onIncludeContacts: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val topOrigin = remember { LaunchOriginHolder() }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // bg normally; transparent in wallpaper mode (Home's root paints the scrim).
                .background(ReachTheme.colors.backdrop)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter, top = ScreenTopPadding, bottom = ResultGap),
    ) {
        SearchHeader(onClose)
        ResultList(
            state = state,
            topOrigin = topOrigin,
            onResultClick = onResultClick,
            onContactAction = onContactAction,
            onIncludeContacts = onIncludeContacts,
            modifier = Modifier.weight(1f),
        )
        if (message != null) {
            Text(
                text = message,
                style = ReachTheme.typography.body,
                color = ReachTheme.colors.fg,
                modifier = Modifier.padding(top = ResultGap).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        SearchInput(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = { onSubmit(topOrigin.toLaunchOrigin()) },
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun SearchHeader(onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel(text = stringResource(R.string.search_title), modifier = Modifier.weight(1f))
        IconCircleButton(
            icon = ReachIcons.Close,
            contentDescription = stringResource(R.string.search_close),
            onClick = onClose,
            style = IconCircleButtonStyle.Outlined,
        )
    }
}

@Composable
private fun ResultList(
    state: SearchUiState,
    topOrigin: LaunchOriginHolder,
    onResultClick: (SearchResult, LaunchOrigin?) -> Unit,
    onContactAction: (Contact, ContactAction) -> Unit,
    onIncludeContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val resultCount = state.results.size
    val countText =
        if (state.isSuggestions) {
            stringResource(R.string.search_section_suggested)
        } else {
            pluralStringResource(R.plurals.search_results_count, resultCount, resultCount)
        }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        // Bottom-aligned: few results sit right above the input, in the thumb zone.
        verticalArrangement = Arrangement.spacedBy(ResultGap, Alignment.Bottom),
    ) {
        item(key = "label") {
            // The label doubles as a polite live region, so TalkBack users hear "3 results".
            MonoLabel(
                text =
                    if (state.isSuggestions) {
                        stringResource(
                            R.string.search_section_suggested,
                        )
                    } else {
                        stringResource(R.string.search_section_matches)
                    },
                modifier =
                    Modifier
                        .padding(bottom = SectionLabelBottom)
                        .semantics {
                            contentDescription = countText
                            liveRegion = LiveRegionMode.Polite
                        },
            )
        }
        if (state.results.isEmpty() && state.isSuggestions) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.search_empty_suggestions),
                    style = ReachTheme.typography.body,
                    color = ReachTheme.colors.muted,
                )
            }
        }
        itemsIndexedByKey(state.results) { index, result ->
            val isTop = index == 0
            val holder = remember { LaunchOriginHolder() }
            Column(modifier = if (reducedMotion) Modifier else Modifier.animateItem()) {
                ResultRow(
                    result = result,
                    isTop = isTop,
                    expanded = result is SearchResult.ContactItem && result.contact.id == state.expandedContactId,
                    onClick = { onResultClick(result, holder.toLaunchOrigin()) },
                    modifier =
                        Modifier.onGloballyPositioned {
                            val bounds = it.boundsInWindow()
                            holder.update(bounds)
                            if (isTop) topOrigin.update(bounds)
                        },
                )
                if (result is SearchResult.ContactItem && result.contact.id == state.expandedContactId) {
                    ContactChooser(result.contact, onContactAction)
                }
            }
        }
        if (state.showContactsPrompt) {
            item(key = "contacts-prompt") { ContactsPrompt(onIncludeContacts) }
        }
    }
}

/** `items` with each result's stable key, exposing the index (for the top-row highlight). */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedByKey(
    results: List<SearchResult>,
    content: @Composable LazyItemScope.(Int, SearchResult) -> Unit,
) {
    items(count = results.size, key = { results[it].key }) { index -> content(index, results[index]) }
}

/**
 * One result: monogram, label, a mono kind tag ("CONTACT"), and on the top row
 * the accent fill and "ENTER ↵". One semantic button whose description says what it is.
 */
@Composable
private fun ResultRow(
    result: SearchResult,
    isTop: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val text = resultText(result)
    val dimmed = result.isDimmed()
    val topHint = stringResource(R.string.search_a11y_top_result)
    val description = listOfNotNull(text.spoken, if (isTop) topHint else null).joinToString(", ")
    val contentColor =
        if (isTop) {
            colors.onAccent
        } else if (dimmed) {
            colors.muted
        } else {
            colors.fg
        }
    val shape = RoundedCornerShape(ResultCorner)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ResultMinHeight)
                .background(if (isTop) colors.accent else colors.surface, shape)
                .reachClickable(
                    onClick = onClick,
                    description = description,
                    onClickLabel =
                        if (result is SearchResult.ContactItem &&
                            !expanded
                        ) {
                            stringResource(R.string.search_a11y_contact_options)
                        } else {
                            null
                        },
                ).padding(horizontal = ResultHorizontalPadding, vertical = ResultVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ResultContentGap),
    ) {
        Monogram(
            label = text.monogram,
            size = ResultMonogramSize,
            style = if (isTop) MonogramStyle.OnAccent else MonogramStyle.Strong,
            textStyle = ReachTheme.typography.count,
            dimmed = dimmed && !isTop,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text.label,
                style = ReachTheme.typography.searchResult,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            text.tag?.let {
                MonoLabel(
                    text = it,
                    color = if (isTop) colors.onAccent else colors.muted,
                    style = ReachTheme.typography.labelSmall,
                )
            }
        }
        // The visual "ENTER ↵" hint gives way at large font sizes: beside it the app name was
        // squeezed into "Instagr/am". TalkBack still hears it (it's in the description).
        if (isTop && LocalDensity.current.fontScale <= ENTER_HINT_MAX_FONT_SCALE) {
            MonoLabel(text = stringResource(R.string.search_enter_hint), color = colors.onAccent, maxLines = 1)
        }
    }
}

/** Above this font scale the top result drops its "ENTER" hint (see [ResultRow]). */
private const val ENTER_HINT_MAX_FONT_SCALE = 1.3f

/** Inline call / message / contact-card pills under an expanded contact. */
@Composable
private fun ContactChooser(
    contact: Contact,
    onContactAction: (Contact, ContactAction) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = ChooserPadding),
        horizontalArrangement = Arrangement.spacedBy(ChooserPadding),
        verticalArrangement = Arrangement.spacedBy(ChooserPadding),
    ) {
        if (contact.phoneNumber != null) {
            PillButton(
                text = stringResource(R.string.search_contact_call),
                onClick = { onContactAction(contact, ContactAction.CALL) },
                contentDescription = stringResource(R.string.search_a11y_contact_call, contact.displayName),
            )
            PillButton(
                text = stringResource(R.string.search_contact_message),
                onClick = { onContactAction(contact, ContactAction.MESSAGE) },
                style = PillButtonStyle.Outlined,
                contentDescription = stringResource(R.string.search_a11y_contact_message, contact.displayName),
            )
        }
        PillButton(
            text = stringResource(R.string.search_contact_view),
            onClick = { onContactAction(contact, ContactAction.VIEW) },
            style = PillButtonStyle.Outlined,
            contentDescription = stringResource(R.string.search_a11y_contact_view, contact.displayName),
        )
    }
}

/** The one, subtle permission row: what it's for in a line, and a pill to allow it. */
@Composable
private fun ContactsPrompt(onIncludeContacts: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = ResultGap), verticalArrangement = Arrangement.spacedBy(InputLabelGap)) {
        Text(
            text = stringResource(R.string.search_contacts_rationale),
            style = ReachTheme.typography.caption,
            color = ReachTheme.colors.muted,
        )
        PillButton(text = stringResource(R.string.search_contacts_prompt), onClick = onIncludeContacts, style = PillButtonStyle.Outlined)
    }
}

/** The 40sp input with its mono label, underlined in accentInk like the mockup. */
@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = ReachTheme.colors
    val label = stringResource(R.string.search_input_label)
    val underline = colors.accentInk
    Column(modifier = Modifier.fillMaxWidth().padding(top = InputTopGap), verticalArrangement = Arrangement.spacedBy(InputLabelGap)) {
        MonoLabel(text = label)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = ReachTheme.typography.searchInput.copy(color = colors.fg),
            cursorBrush = SolidColor(colors.accentInk),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
            // Go on the soft keyboard and Enter on a hardware keyboard both land here.
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label }
                    .drawBehind {
                        val y = size.height - InputUnderline.toPx() / 2
                        drawLine(underline, Offset(0f, y), Offset(size.width, y), strokeWidth = InputUnderline.toPx())
                    },
            decorationBox = { inner ->
                Column(modifier = Modifier.padding(vertical = InputVerticalPadding)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_input_hint),
                            style = ReachTheme.typography.searchInput,
                            color = colors.faint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** Display parts of one row: the label, a mono tag, the spoken description and monogram text. */
private class ResultText(
    val label: String,
    val tag: String?,
    val spoken: String,
    val monogram: String,
)

@Composable
@ReadOnlyComposable
private fun resultText(result: SearchResult): ResultText =
    when (result) {
        is SearchResult.AppItem -> {
            val app = result.app
            val profile =
                when (app.profile) {
                    ProfileType.PERSONAL -> null
                    ProfileType.WORK -> stringResource(DsR.string.ds_profile_tag_work)
                    ProfileType.PRIVATE -> stringResource(DsR.string.ds_profile_tag_private)
                }
            val spokenProfile =
                when (app.profile) {
                    ProfileType.PERSONAL -> null
                    ProfileType.WORK -> stringResource(DsR.string.ds_profile_work)
                    ProfileType.PRIVATE -> stringResource(DsR.string.ds_profile_private)
                }
            val paused = if (app.isProfilePaused) stringResource(DsR.string.ds_profile_paused) else null
            ResultText(
                label = app.label,
                tag = listOfNotNull(profile, paused).joinToString(" · ").ifEmpty { null },
                spoken = listOfNotNull(app.label, spokenProfile, paused).joinToString(", "),
                monogram = app.label,
            )
        }

        is SearchResult.ContactItem -> {
            ResultText(
                label = result.contact.displayName,
                tag = stringResource(R.string.search_kind_contact),
                spoken = stringResource(R.string.search_a11y_contact, result.contact.displayName),
                monogram = result.contact.displayName,
            )
        }

        is SearchResult.CommandItem -> {
            val label = commandLabel(result.command)
            ResultText(label = label, tag = stringResource(R.string.search_kind_action), spoken = label, monogram = label)
        }

        is SearchResult.WebSearch -> {
            val label = stringResource(R.string.search_web, result.query)
            ResultText(label = label, tag = stringResource(R.string.search_kind_web), spoken = label, monogram = result.query)
        }
    }

@Composable
@ReadOnlyComposable
private fun commandLabel(command: SearchCommand): String =
    when (command) {
        is SearchCommand.SwitchPalette -> {
            stringResource(R.string.search_command_palette, paletteDisplayName(command.palette))
        }

        is SearchCommand.SwitchTheme -> {
            stringResource(
                if (command.mode ==
                    ThemeMode.LIGHT
                ) {
                    R.string.search_command_light
                } else {
                    R.string.search_command_dark
                },
            )
        }

        SearchCommand.OpenAppearance -> {
            stringResource(R.string.search_command_appearance)
        }
    }

private fun SearchResult.isDimmed(): Boolean =
    when (this) {
        is SearchResult.AppItem -> app.isProfilePaused
        else -> false
    }

/**
 * Where one row sits in the window, for the launch animation. Plain holder, not state:
 * it changes with every scroll frame and is only read on tap (same as Home's helper).
 */
@Stable
private class LaunchOriginHolder {
    private var bounds: Rect = Rect.Zero

    fun update(rect: Rect) {
        bounds = rect
    }

    fun toLaunchOrigin(): LaunchOrigin? =
        bounds.takeUnless { it.isEmpty }?.let {
            LaunchOrigin(it.left.roundToInt(), it.top.roundToInt(), it.width.roundToInt(), it.height.roundToInt())
        }
}
