package com.baklavapp.reachlauncher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Colour maths behind the derived tokens, wallpaper accents and contrast tests.
 *
 * Kept as plain functions on Compose [Color] so they run in JVM unit tests without a
 * device. Flutter analogy: `Color.lerp` and `Color.computeLuminance()` — but see [mix]
 * for why we don't use Compose's own `lerp`.
 */
object ColorMath {
    /** The WCAG AA threshold for normal-size text. Every text/background pair must reach it. */
    const val MIN_TEXT_CONTRAST = 4.5

    /**
     * Linear blend of the **sRGB channel values**: `a + (b - a) * t` per channel, like
     * CSS `color-mix(in srgb, …)`, which the design mockups use.
     *
     * Why not Compose's `lerp(a, b, t)`: it interpolates in Oklab, which gives slightly
     * different (perceptual) results, and the tokens are specified as an sRGB mix.
     * Alpha is taken from [a]. [t] = 0 returns [a], 1 returns [b].
     */
    fun mix(
        a: Color,
        b: Color,
        t: Float,
    ): Color {
        require(t in 0f..1f) { "t must be in 0..1, was $t" }
        return Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = a.alpha,
        )
    }

    /** WCAG 2.x relative luminance (0 = black, 1 = white) of an opaque sRGB colour. */
    fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    /** WCAG contrast ratio between two opaque colours, from 1.0 (same) to 21.0 (black/white). */
    fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Whichever of pure white or pure black contrasts more with [background].
     *
     * Why not "luminance < 0.5 → white": the WCAG crossover is at luminance ≈ 0.18, not
     * 0.5. A background of luminance 0.3 looks mid-grey but black text on it reaches ~7:1
     * while white only reaches ~3:1.
     */
    fun extremeFor(background: Color): Color =
        if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) Color.White else Color.Black

    /**
     * Moves [color] step by step towards [extremeFor] [background] until it reaches
     * [minContrast] against [background]. Returns [color] unchanged if it already passes.
     * Used to derive a readable `accentInk` from any wallpaper colour.
     *
     * Always terminates with a passing colour, because the extreme for any background
     * reaches at least √21 ≈ 4.58:1 against it.
     */
    fun ensureContrast(
        color: Color,
        background: Color,
        minContrast: Double = MIN_TEXT_CONTRAST,
    ): Color {
        if (contrastRatio(color, background) >= minContrast) return color
        return pushTowards(color, extremeFor(background)) { contrastRatio(it, background) >= minContrast }
    }

    /**
     * Mixes [color] towards [target] in small steps until [done] holds; [target] itself
     * is the last resort. The small step keeps the result as close to the input as possible.
     */
    internal fun pushTowards(
        color: Color,
        target: Color,
        done: (Color) -> Boolean,
    ): Color {
        var step = 1
        while (step <= STEPS) {
            val candidate = mix(color, target, step.toFloat() / STEPS)
            if (done(candidate)) return candidate
            step++
        }
        return target
    }

    private const val STEPS = 100
}
