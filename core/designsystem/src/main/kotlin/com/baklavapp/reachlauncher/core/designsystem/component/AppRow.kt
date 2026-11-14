package com.baklavapp.reachlauncher.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.baklavapp.reachlauncher.core.designsystem.R
import com.baklavapp.reachlauncher.core.designsystem.icon.ReachIcons
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachDimens
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import com.baklavapp.reachlauncher.core.designsystem.util.formatBadgeCount
import com.baklavapp.reachlauncher.core.model.ProfileType

/*
 * AppRow — one app in intent detail (spec §6.4): monogram, name, badge, arrow.
 *
 * Flutter analogy: a `ListTile(leading:, title:, trailing:, onTap:, onLongPress:)` you'd
 * build yourself. `combinedClickable` (inside [reachClickable]) is Compose's
 * `GestureDetector(onTap:, onLongPress:)`; the divider is painted with `drawBehind`
 * (≈ a `CustomPainter` behind the child) rather than a separate widget.
 */

/**
 * An app row, ≥ 58dp tall: [Monogram], name (Inter 22sp), optional profile tag ("WORK"),
 * per-app unread [badgeCount], and a faint trailing arrow. Draws a `divider` hairline below.
 *
 * Long-press opens the app's menu (Move to…, Hide). It is exposed to TalkBack both as the
 * long-click action and as a custom action named [onLongClickLabel]; pass direct actions
 * ("Move to…", "Hide") in [accessibilityActions] too, so the menu isn't the only route.
 *
 * TalkBack reads e.g. "WhatsApp, 2 new notifications, work profile, paused".
 *
 * @param profile shows a "WORK"/"PRIVATE" tag for non-personal profiles.
 * @param dimmed the app's profile is paused: name and letters turn `muted` (still ≥ 4.5:1,
 *   tapping is still allowed so Home can offer to resume the profile).
 * @param icon future real-icon support; see [Monogram].
 */
@Composable
fun AppRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    badgeCount: Int = 0,
    profile: ProfileType = ProfileType.PERSONAL,
    dimmed: Boolean = false,
    showArrow: Boolean = true,
    showDivider: Boolean = true,
    icon: Painter? = null,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = ReachTheme.colors
    val tag =
        when (profile) {
            ProfileType.PERSONAL -> null
            ProfileType.WORK -> stringResource(R.string.ds_profile_tag_work)
            ProfileType.PRIVATE -> stringResource(R.string.ds_profile_tag_private)
        }
    val description =
        listOfNotNull(
            label,
            if (badgeCount > 0) pluralStringResource(R.plurals.ds_new_notifications, badgeCount, badgeCount) else null,
            when (profile) {
                ProfileType.PERSONAL -> null
                ProfileType.WORK -> stringResource(R.string.ds_profile_work)
                ProfileType.PRIVATE -> stringResource(R.string.ds_profile_private)
            },
            if (dimmed) stringResource(R.string.ds_profile_paused) else null,
        ).joinToString(", ")
    val dividerColor = colors.divider
    val dividerWidth = ReachComponentDimens.hairline
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (showDivider) {
                        Modifier.drawBehind {
                            val y = size.height - dividerWidth.toPx() / 2
                            drawLine(dividerColor, Offset(0f, y), Offset(size.width, y), strokeWidth = dividerWidth.toPx())
                        }
                    } else {
                        Modifier
                    },
                ).reachClickable(
                    onClick = onClick,
                    description = description,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    accessibilityActions = accessibilityActions,
                ).heightIn(min = ReachDimens.appRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.appRowGap),
    ) {
        Monogram(label = label, icon = icon, dimmed = dimmed)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReachComponentDimens.profileTagGap),
        ) {
            Text(
                text = label,
                style = ReachTheme.typography.appRow,
                color = if (dimmed) colors.muted else colors.fg,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tag != null) MonoLabel(text = tag, style = ReachTheme.typography.labelSmall, maxLines = 1)
        }
        if (badgeCount > 0) AccentBadge(text = formatBadgeCount(badgeCount))
        if (showArrow) {
            Icon(
                imageVector = ReachIcons.ArrowRight,
                contentDescription = null,
                tint = colors.faint,
                modifier = Modifier.size(ReachComponentDimens.appRowArrowSize),
            )
        }
    }
}

@Composable
private fun AppRowsSample() {
    Column {
        AppRow("Messages", onClick = {}, badgeCount = 1, onLongClick = {}, onLongClickLabel = "Move to…")
        AppRow("WhatsApp", onClick = {}, badgeCount = 2)
        AppRow("YouTube Music", onClick = {})
        AppRow("Slack", onClick = {}, profile = ProfileType.WORK)
        AppRow("Outlook", onClick = {}, profile = ProfileType.WORK, dimmed = true)
        AppRow("Vault", onClick = {}, profile = ProfileType.PRIVATE, showDivider = false)
    }
}

@Preview(name = "App rows", widthDp = 390)
@Composable
private fun AppRowPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { AppRowsSample() }
}

@Preview(name = "App rows · 200% font", widthDp = 390, fontScale = 2f)
@Composable
private fun AppRowLargeFontPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { AppRowsSample() }
}
