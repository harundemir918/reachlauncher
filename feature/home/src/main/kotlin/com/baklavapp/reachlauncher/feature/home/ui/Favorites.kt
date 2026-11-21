package com.baklavapp.reachlauncher.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baklavapp.reachlauncher.core.data.repository.FavoritesRepository
import com.baklavapp.reachlauncher.core.designsystem.component.IconCircleButton
import com.baklavapp.reachlauncher.core.designsystem.component.IconCircleButtonStyle
import com.baklavapp.reachlauncher.core.designsystem.component.MonoLabel
import com.baklavapp.reachlauncher.core.designsystem.component.Monogram
import com.baklavapp.reachlauncher.core.designsystem.component.MonogramStyle
import com.baklavapp.reachlauncher.core.designsystem.component.PillButton
import com.baklavapp.reachlauncher.core.designsystem.component.reachClickable
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.intentWord
import com.baklavapp.reachlauncher.core.model.CatalogApp
import com.baklavapp.reachlauncher.core.model.LaunchOrigin
import com.baklavapp.reachlauncher.core.model.ReachIntent
import com.baklavapp.reachlauncher.core.model.TextFolding
import com.baklavapp.reachlauncher.feature.home.AppItem
import com.baklavapp.reachlauncher.feature.home.R
import com.baklavapp.reachlauncher.core.designsystem.R as DsR

/*
 * Favourites: the round row at the top of intent detail (up to five apps the user picked for
 * this intent — "Phone", "Camera"), and the editor sheet that picks, orders and removes them.
 *
 * Flutter analogy: the row is a `Row` of five `Expanded` slots, each a `CircleAvatar` with a
 * caption; the editor is a bottom sheet drawn in the same screen (a `Stack` with a scrim, as
 * the app menu does — Reach has no navigator, D28) holding one `ListView.builder`.
 */

private val CircleSize = 52.dp
private val SlotGap = 6.dp
private val RowLabelGap = 6.dp
private val UnreadDotSize = 10.dp
private val EditorPadding = 20.dp
private val EditorGap = 4.dp
private val EditorMonogramSize = 36.dp
private val EditorRowGap = 12.dp
private val SearchUnderline = 2.dp
private val SearchVerticalPadding = 10.dp
private val SectionGap = 12.dp
private const val EDITOR_HEIGHT_FRACTION = 0.9f
private const val SCRIM_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.3f
private const val STACK_FONT_SCALE = 1.3f
private const val LABEL_SIZE_STEP = 0.5f

/** Smallest circle label, as it would look at 1× font (sp sizes are multiplied by the font scale). */
private const val MIN_LABEL_SP_AT_1X = 10f

/** What the favourites row and editor can change, already bound to the shown intent. */
@Immutable
internal class FavoriteCallbacks(
    val onAdd: (CatalogApp) -> Unit,
    val onRemove: (CatalogApp) -> Unit,
    val onMove: (CatalogApp, Int) -> Unit,
)

/**
 * The favourites row: five equal slots, the user's favourites first, then one "+" circle
 * while there is room. Tap launches; long-press (or "Edit") opens the editor. TalkBack also
 * gets "Remove from favourites" on each circle, so nothing needs the long-press.
 *
 * The slots are not clipped to a rounded shape: a name can fill a slot's width, and a clip
 * would cut its first and last letters.
 *
 * @param onEdit opens the editor; null = read-only (no "+", no "Edit").
 */
@Composable
internal fun FavoritesRow(
    favorites: List<AppItem>,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: ((CatalogApp) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty() && onEdit == null) return
    val max = FavoritesRepository.MAX_PER_INTENT
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowLabelGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(
                text = stringResource(R.string.home_favorites),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onEdit != null && favorites.isNotEmpty()) {
                val editLabel = stringResource(R.string.home_favorites_edit)
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(ReachDimens.minTouchTarget))
                            .reachClickable(onClick = onEdit, description = stringResource(R.string.home_favorites_edit_a11y))
                            .heightIn(min = ReachDimens.minTouchTarget)
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoLabel(text = editLabel, color = ReachTheme.colors.accentInk)
                }
            }
        }
        val addLabel = stringResource(R.string.home_favorites_add)
        val showAdd = onEdit != null && favorites.size < max
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val slotWidth = (maxWidth - SlotGap * (max - 1)) / max
            val labels = favorites.map { it.app.label } + if (showAdd) listOf(addLabel) else emptyList()
            val labelStyle = rememberSharedLabelStyle(labels, slotWidth)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SlotGap)) {
                for (slot in 0 until max) {
                    val slotModifier = Modifier.weight(1f)
                    when {
                        slot < favorites.size -> {
                            FavoriteCircle(favorites[slot], labelStyle, onLaunch, onEdit, onRemove, slotModifier)
                        }

                        slot == favorites.size && onEdit != null -> {
                            AddCircle(addLabel, labelStyle, onEdit, slotModifier)
                        }

                        else -> {
                            Spacer(slotModifier)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One text size for every name in the row: the largest (up to `caption`) at which all of
 * [labels] fit [slotWidth] on one line, but never below [MIN_LABEL_SP_AT_1X] on screen. Sizing
 * each name on its own made "WhatsApp" smaller than "X" and the names sat at uneven heights
 * (found on a real phone); a name still too wide at the floor ends with "…".
 */
@Composable
private fun rememberSharedLabelStyle(
    labels: List<String>,
    slotWidth: Dp,
): TextStyle {
    val base = ReachTheme.typography.caption
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, slotWidth, base, density) {
        val widthPx = with(density) { slotWidth.roundToPx() }
        // sp sizes are multiplied by the font scale: divide the floor so it's the same on screen.
        val floor = MIN_LABEL_SP_AT_1X / density.fontScale
        var size = base.fontSize.value

        fun fits(sp: Float) =
            labels.all { measurer.measure(it, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= widthPx }
        while (size > floor && !fits(size)) size -= LABEL_SIZE_STEP
        base.copy(fontSize = maxOf(size, floor).sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FavoriteCircle(
    item: AppItem,
    labelStyle: TextStyle,
    onLaunch: (CatalogApp, LaunchOrigin?) -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: ((CatalogApp) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val app = item.app
    val colors = ReachTheme.colors
    val origin = rememberLaunchOrigin()
    val description =
        listOfNotNull(
            app.label,
            if (item.unreadCount > 0) pluralStringResource(DsR.plurals.ds_new_notifications, item.unreadCount, item.unreadCount) else null,
            if (app.isProfilePaused) stringResource(DsR.string.ds_profile_paused) else null,
        ).joinToString(", ")
    val removeLabel = stringResource(R.string.home_favorites_remove)
    val actions =
        if (onRemove != null) {
            listOf(
                CustomAccessibilityAction(removeLabel) {
                    onRemove(app)
                    true
                },
            )
        } else {
            emptyList()
        }
    Column(
        modifier =
            modifier
                .reachClickable(
                    onClick = { onLaunch(app, origin.toLaunchOrigin()) },
                    description = description,
                    onLongClick = onEdit,
                    onLongClickLabel = if (onEdit != null) stringResource(R.string.home_favorites_edit_a11y) else null,
                    accessibilityActions = actions,
                ).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RowLabelGap),
    ) {
        Box(modifier = Modifier.trackLaunchOrigin(origin)) {
            Monogram(label = app.label, size = CircleSize, style = MonogramStyle.Strong, dimmed = app.isProfilePaused)
            if (item.unreadCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .size(UnreadDotSize)
                        .background(colors.accent, CircleShape)
                        .border(2.dp, colors.bg, CircleShape),
                )
            }
        }
        // The row picks one size that fits every name (rememberSharedLabelStyle); the full
        // name is in the circle's description for TalkBack either way.
        Text(
            text = app.label,
            style = labelStyle,
            color = if (app.isProfilePaused) colors.muted else colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun fontScale(): Float = LocalDensity.current.fontScale

@Composable
private fun AddCircle(
    label: String,
    labelStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    Column(
        modifier =
            modifier
                .reachClickable(onClick = onClick, description = stringResource(R.string.home_favorites_add_a11y))
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RowLabelGap),
    ) {
        Box(
            modifier = Modifier.size(CircleSize).border(ReachDimens.monogramRing, colors.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = ReachIcons.Add, contentDescription = null, tint = colors.accentInk, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = labelStyle,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The favourites editor for [intent]: the current favourites (move left/right, remove), a
 * search field and every pickable app (tap to add). Changes apply at once — "Done", Back
 * and a tap on the scrim only close it. Anchored at the bottom, in thumb reach.
 *
 * @param pickable every app that may be a favourite, alphabetical.
 */
@Composable
internal fun FavoritesEditor(
    intent: ReachIntent,
    favorites: List<AppItem>,
    pickable: List<CatalogApp>,
    callbacks: FavoriteCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val max = FavoritesRepository.MAX_PER_INTENT
    val full = favorites.size >= max
    var query by rememberSaveable(intent) { mutableStateOf("") }
    val folded = remember(query) { TextFolding.fold(query.trim()) }
    val pinnedKeys = remember(favorites) { favorites.mapTo(HashSet()) { it.app.key } }
    val candidates =
        remember(pickable, pinnedKeys, folded) {
            pickable.filter { it.key !in pinnedKeys && (folded.isEmpty() || TextFolding.fold(it.label).contains(folded)) }
        }
    val title = stringResource(R.string.home_favorites_editor_title, intentWord(intent))
    BackHandler(onBack = onClose)
    val shape = RoundedCornerShape(ReachDimens.sheetCorner)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(ReachTheme.dimens.gutter),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(EDITOR_HEIGHT_FRACTION)
                    .clip(shape)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.line2, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title }
                    .padding(EditorPadding),
        ) {
            // One lazy list for everything, so even at 200 % font the app list is reachable.
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EditorGap)) {
                item(key = "title") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = ReachTheme.typography.title,
                            color = colors.fg,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        MonoLabel(text = stringResource(R.string.home_favorites_count, favorites.size, max))
                    }
                }
                if (favorites.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.home_favorites_none),
                            style = ReachTheme.typography.body,
                            color = colors.muted,
                            modifier = Modifier.padding(vertical = SectionGap),
                        )
                    }
                }
                itemsIndexed(favorites, key = { _, item -> "fav:${item.app.key}" }) { index, item ->
                    PinnedRow(
                        app = item.app,
                        canMoveLeft = index > 0,
                        canMoveRight = index < favorites.lastIndex,
                        callbacks = callbacks,
                    )
                }
                if (full) {
                    item(key = "full") {
                        Text(
                            text = stringResource(R.string.home_favorites_full),
                            style = ReachTheme.typography.body,
                            color = colors.muted,
                            modifier = Modifier.padding(top = SectionGap),
                        )
                    }
                } else {
                    item(key = "search") {
                        SearchField(query, onQueryChange = { query = it }, modifier = Modifier.padding(top = SectionGap))
                    }
                    if (candidates.isEmpty()) {
                        item(key = "nomatch") {
                            Text(
                                text = stringResource(R.string.home_favorites_no_match),
                                style = ReachTheme.typography.body,
                                color = colors.muted,
                                modifier = Modifier.padding(vertical = SectionGap),
                            )
                        }
                    }
                    items(candidates, key = { "app:${it.key}" }) { app ->
                        CandidateRow(app, onAdd = { callbacks.onAdd(app) })
                    }
                }
            }
            PillButton(
                text = stringResource(R.string.home_favorites_done),
                onClick = onClose,
                modifier = Modifier.align(Alignment.End).padding(top = SectionGap),
            )
        }
    }
}

@Composable
private fun PinnedRow(
    app: CatalogApp,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    callbacks: FavoriteCallbacks,
) {
    // Above 1.3× font the three buttons leave the name too little room ("Messag/es"): they go
    // on their own line under it, as the Appearance sheet stacks its options.
    val stacked = fontScale() > STACK_FONT_SCALE
    val name: @Composable (Modifier) -> Unit = { nameModifier ->
        Row(
            modifier = nameModifier.heightIn(min = ReachDimens.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorRowGap),
        ) {
            Monogram(label = app.label, size = EditorMonogramSize, style = MonogramStyle.Strong, dimmed = app.isProfilePaused)
            Text(
                text = app.label,
                style = ReachTheme.typography.button,
                color = ReachTheme.colors.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val buttons: @Composable () -> Unit = { PinnedButtons(app, canMoveLeft, canMoveRight, callbacks) }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth()) {
            name(Modifier.fillMaxWidth())
            Row(modifier = Modifier.align(Alignment.End)) { buttons() }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            name(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun PinnedButtons(
    app: CatalogApp,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    callbacks: FavoriteCallbacks,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(EditorGap)) {
        IconCircleButton(
            icon = ReachIcons.ArrowLeft,
            contentDescription = stringResource(R.string.home_favorites_move_left, app.label),
            onClick = { callbacks.onMove(app, -1) },
            style = IconCircleButtonStyle.Plain,
            enabled = canMoveLeft,
            modifier = Modifier.alpha(if (canMoveLeft) 1f else DISABLED_ALPHA),
        )
        IconCircleButton(
            icon = ReachIcons.ArrowRight,
            contentDescription = stringResource(R.string.home_favorites_move_right, app.label),
            onClick = { callbacks.onMove(app, 1) },
            style = IconCircleButtonStyle.Plain,
            enabled = canMoveRight,
            modifier = Modifier.alpha(if (canMoveRight) 1f else DISABLED_ALPHA),
        )
        IconCircleButton(
            icon = ReachIcons.Close,
            contentDescription = stringResource(R.string.home_favorites_remove_named, app.label),
            onClick = { callbacks.onRemove(app) },
            style = IconCircleButtonStyle.Plain,
        )
    }
}

@Composable
private fun CandidateRow(
    app: CatalogApp,
    onAdd: () -> Unit,
) {
    val colors = ReachTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReachDimens.minTouchTarget / 2))
                .reachClickable(onClick = onAdd, description = stringResource(R.string.home_favorites_add_named, app.label))
                .heightIn(min = ReachDimens.minTouchTarget)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorRowGap),
    ) {
        Monogram(label = app.label, size = EditorMonogramSize, dimmed = app.isProfilePaused)
        Text(
            text = app.label,
            style = ReachTheme.typography.button,
            color = if (app.isProfilePaused) colors.muted else colors.fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(imageVector = ReachIcons.Add, contentDescription = null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReachTheme.colors
    val hint = stringResource(R.string.home_favorites_find)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = ReachTheme.typography.button.copy(color = colors.fg),
        cursorBrush = SolidColor(colors.accentInk),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = hint }
                .drawBehind {
                    val y = size.height - SearchUnderline.toPx() / 2
                    drawLine(colors.line2, Offset(0f, y), Offset(size.width, y), SearchUnderline.toPx())
                }.padding(vertical = SearchVerticalPadding),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text(text = hint, style = ReachTheme.typography.button, color = colors.muted)
                inner()
            }
        },
    )
}
