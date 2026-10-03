package com.cineverse.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
 * Used under a hero, a trophy, a perfect score — anywhere one element should
 * sit in a little light rather than on flat ink. It is a radial gradient that
 * reaches zero well inside its own bounds, so it never shows an edge; an
 * accent wash with a visible boundary is a coloured rectangle, which is the
 * opposite of the effect.
 */
fun Modifier.stage(
    tint: Color,
    alpha: Float = 0.22f,
    /** Where the light comes from, in fractions of the box. */
    origin: Offset = Offset(0.5f, 0.35f),
): Modifier = composed {
    this.background(
        Brush.radialGradient(
            colors = listOf(tint.copy(alpha = alpha), Color.Transparent),
            center = Offset(origin.x, origin.y) * 1000f,
            radius = 900f,
        )
    )
}
