package com.cineverse.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme

/**
 * The one glass recipe.
 *
 * Every panel, sheet and chip in the app was reaching for `colors.glass` plus a
 * hairline border and getting a flat grey rectangle. Real glass is not flat: it
 * is brighter where light would strike it and darker where it would not, and it
 * has a bright edge along the lit side only.
 *
 * Three layers, in order, and the order is the whole thing:
 *
 *  1. a FILL that is a very shallow diagonal gradient rather than one colour,
 *     so the surface has a direction;
 *  2. a SHEEN across the top-left corner, strongest at the corner and gone by
 *     a third of the way across, which is what reads as a highlight rather
 *     than as a lighter box;
 *  3. a HAIRLINE that is brighter at the top than the bottom, because an edge
 *     of uniform brightness is a stroke and an edge that fades is a bevel.
 *
 * All of it is a few draws with no blur. A real backdrop blur on Android means
 * `RenderEffect` and a layer readback on every frame, which on a scrolling page
 * of twenty panels costs more than it is worth — and against this app's very
 * dark page it would be nearly invisible anyway.
 */
fun Modifier.glass(
    shape: Shape = CvShape.XLarge,
    /** 0 is a whisper, 1 is a panel you could pick up. */
    strength: Float = 1f,
    /** The lit edge and sheen. Off for something sitting flat on the page. */
    raised: Boolean = true,
): Modifier = composed {
    val colors = CvTheme.colors
    val base = if (colors.isDark) Color.White else Color.Black
    val lift = strength.coerceIn(0f, 1.4f)

    this
        .clip(shape)
        .background(
            Brush.linearGradient(
                0f to base.copy(alpha = 0.085f * lift),
                0.55f to base.copy(alpha = 0.055f * lift),
                1f to base.copy(alpha = 0.035f * lift),
                start = Offset.Zero,
                end = Offset.Infinite,
            )
        )
        .then(
            if (!raised) Modifier else Modifier.background(
                Brush.linearGradient(
                    0f to base.copy(alpha = 0.10f * lift),
                    0.34f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(520f, 520f),
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to base.copy(alpha = if (raised) 0.16f * lift else 0.08f * lift),
                0.5f to base.copy(alpha = 0.07f * lift),
                1f to base.copy(alpha = 0.04f * lift),
            ),
            shape = shape,
        )
}

/**
 * The stage: a soft pool of the page's own accent behind something important.
 *
 * Used under a hero, a trophy, a perfect score - anywhere one element should
 * sit in a little light rather than on flat ink. It is a radial gradient that
 * reaches zero well inside its own bounds, so it never shows an edge; an
 * accent wash with a visible boundary is a coloured rectangle, which is the
 * opposite of the effect.
 *
 * It paints BEHIND its content and reads the real measured size, because a
 * gradient written against assumed pixels is correct on exactly one screen.
 */
fun Modifier.stage(
    tint: Color,
    alpha: Float = 0.22f,
    /** Where the light comes from, in fractions of the box. */
    origin: Offset = Offset(0.5f, 0.35f),
    /** How far the pool reaches, as a multiple of the longest side. */
    spread: Float = 0.92f,
): Modifier = this.drawWithCache {
    val brush = Brush.radialGradient(
        0f to tint.copy(alpha = alpha),
        0.55f to tint.copy(alpha = alpha * 0.34f),
        1f to Color.Transparent,
        center = Offset(size.width * origin.x, size.height * origin.y),
        radius = (size.maxDimension * spread).coerceAtLeast(1f),
    )
    onDrawBehind { drawRect(brush) }
}

/** Which side of a full-bleed pane is its lit edge. */
enum class PaneEdge { Top, Bottom }

/**
 * Glass for a bar that runs the full width of the screen.
 *
 * [glass] is wrong for this job and it is worth saying why: it clips to a shape
 * and strokes a border all the way round, and a bar pinned to the bottom of the
 * screen has three edges nobody can see and one that carries the whole effect.
 * Stroking all four puts a hairline down the left and right of the display.
 *
 * So this is the same material with the geometry a bar actually has: the page
 * colour underneath for legibility, the diagonal fill and the sheen over it,
 * and ONE hairline, on the edge that faces the content.
 */
fun Modifier.glassPane(
    edge: PaneEdge,
    base: Color,
    /** How much page colour sits under the glass. Legibility lives here. */
    opacity: Float = 0.92f,
    strength: Float = 1f,
): Modifier = composed {
    val colors = CvTheme.colors
    val light = if (colors.isDark) Color.White else Color.Black
    val lift = strength.coerceIn(0f, 1.4f)

    this.drawWithCache {
        val fill = Brush.linearGradient(
            0f to light.copy(alpha = 0.075f * lift),
            0.6f to light.copy(alpha = 0.045f * lift),
            1f to light.copy(alpha = 0.025f * lift),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        // The sheen runs along the bar rather than across it, because a bar is
        // a wide shallow box and a diagonal highlight in one corner of it just
        // looks like a stain.
        val sheen = Brush.horizontalGradient(
            0f to light.copy(alpha = 0.055f * lift),
            0.45f to Color.Transparent,
            1f to light.copy(alpha = 0.03f * lift),
        )
        val hairline = 1.dp.toPx()
        val y = if (edge == PaneEdge.Top) hairline / 2f else size.height - hairline / 2f
        onDrawBehind {
            drawRect(base.copy(alpha = opacity.coerceIn(0f, 1f)))
            drawRect(fill)
            drawRect(sheen)
            drawLine(
                brush = Brush.horizontalGradient(
                    0f to light.copy(alpha = 0.06f * lift),
                    0.5f to light.copy(alpha = 0.17f * lift),
                    1f to light.copy(alpha = 0.06f * lift),
                ),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = hairline,
            )
        }
    }
}
