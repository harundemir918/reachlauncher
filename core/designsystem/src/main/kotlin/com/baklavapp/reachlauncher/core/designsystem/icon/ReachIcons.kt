package com.baklavapp.reachlauncher.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * ReachIcons — the handful of glyphs Reach draws, hand-built from the design mockups' SVGs.
 *
 * Flutter analogy: a tiny custom `IconData` set — but instead of a font, each icon is an
 * `ImageVector` (Compose's in-code VectorDrawable, ≈ an SVG path you build with a
 * `CustomPainter`). We don't use the `material-icons` artifact: it isn't in the catalog,
 * and the mockups' strokes (2dp, round caps, 24-unit viewport) differ from Material's.
 *
 * The colour baked into the paths (black) is only a mask: always draw these through
 * `Icon(imageVector, contentDescription, tint = ReachTheme.colors.…)`, which tints
 * strokes and fills alike. Each vector is built once, lazily, and cached.
 *
 * Stroke icons (Search, chevrons, arrows, Close) are outlines; transport icons (Play,
 * Pause, Skip*) are filled shapes, exactly as in the mockups.
 */
object ReachIcons {
    /** Magnifier: the "Type to launch" pill. */
    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            // circle cx=11 cy=11 r=7, drawn as two half arcs.
            moveTo(4f, 11f)
            arcToRelative(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 14f, dy1 = 0f)
            arcToRelative(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -14f, dy1 = 0f)
            close()
            moveTo(16.5f, 16.5f)
            lineTo(21f, 21f)
        }
    }

    /** Left chevron: the back button in intent detail (mockup `polyline 15 6 9 12 15 18`). */
    val Back: ImageVector by lazy {
        strokeIcon("Back") {
            moveTo(15f, 6f)
            lineTo(9f, 12f)
            lineTo(15f, 18f)
        }
    }

    /** Right chevron: "more" affordances and rows that open a sub-screen. */
    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }

    /** Arrow with a shaft: the trailing "launch" arrow on app rows (mockup intent detail). */
    val ArrowRight: ImageVector by lazy {
        strokeIcon("ArrowRight") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
    }

    /** +: the "add a favourite" circle in intent detail. */
    val Add: ImageVector by lazy {
        strokeIcon("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    /** Left arrow with a shaft: "Move left" in the favourites editor. */
    val ArrowLeft: ImageVector by lazy {
        strokeIcon("ArrowLeft") {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    /** ×: closes Search. */
    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    /** Filled triangle (mockup `polygon 7 4 20 12 7 20`). */
    val Play: ImageVector by lazy {
        fillIcon("Play") {
            moveTo(7f, 4f)
            lineTo(20f, 12f)
            lineTo(7f, 20f)
            close()
        }
    }

    /** Two filled bars with 1-unit rounded corners. */
    val Pause: ImageVector by lazy {
        fillIcon("Pause") {
            roundedRect(x = 6f, y = 4f, width = 4f, height = 16f, radius = 1f)
            roundedRect(x = 14f, y = 4f, width = 4f, height = 16f, radius = 1f)
        }
    }

    /** Bar + left-pointing triangle: previous track. */
    val SkipPrevious: ImageVector by lazy {
        fillIcon("SkipPrevious") {
            moveTo(18f, 5f)
            lineTo(8f, 12f)
            lineTo(18f, 19f)
            close()
            rect(x = 5f, y = 5f, width = 2.5f, height = 14f)
        }
    }

    /** Right-pointing triangle + bar: next track. */
    val SkipNext: ImageVector by lazy {
        fillIcon("SkipNext") {
            moveTo(6f, 5f)
            lineTo(16f, 12f)
            lineTo(6f, 19f)
            close()
            rect(x = 16.5f, y = 5f, width = 2.5f, height = 14f)
        }
    }
}

/** Mockup icons live in a 24×24 viewport. */
private const val VIEWPORT = 24f

/** The mockups' stroke width, in viewport units (= 2dp at the default 24dp size). */
private const val STROKE_WIDTH = 2f

private fun builder(name: String) =
    ImageVector.Builder(
        name = "Reach.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    )

private fun strokeIcon(
    name: String,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector =
    builder(name)
        .path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        ).build()

private fun fillIcon(
    name: String,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector = builder(name).path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder).build()

private fun PathBuilder.rect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    moveTo(x, y)
    horizontalLineToRelative(width)
    verticalLineToRelative(height)
    horizontalLineToRelative(-width)
    close()
}

private fun PathBuilder.roundedRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    moveTo(x + radius, y)
    lineTo(x + width - radius, y)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x + width, y1 = y + radius)
    lineTo(x + width, y + height - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x + width - radius, y1 = y + height)
    lineTo(x + radius, y + height)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x, y1 = y + height - radius)
    lineTo(x, y + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x + radius, y1 = y)
    close()
}
