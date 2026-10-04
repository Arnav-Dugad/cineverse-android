package com.cineverse.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme

/** Gemini's own colours, blue through violet to rose, and back to blue. */
val GeminiColors = listOf(
    Color(0xFF4285F4), Color(0xFF9B72CB), Color(0xFFD96570), Color(0xFF9B72CB), Color(0xFF4285F4),
)

/**
 * The mark of something Gemini wrote: a border that slowly turns through
 * Gemini's colours, with a soft bloom outside it. Only ever on words a model
 * produced, so a reader always knows which sentences came from where.
 *
 * Still under reduced motion: the colours stay, the turning stops.
 */
fun Modifier.geminiGlow(
    on: Boolean = true,
    corner: Dp = 20.dp,
    width: Dp = 1.5.dp,
    /** While Gemini is still writing: the bloom swells and breathes. */
    pulse: Boolean = false,
): Modifier = if (!on) this else composed {
    val reduced = CvTheme.reducedMotion
    val loop = rememberInfiniteTransition(label = "geminiGlow")
    val angle by loop.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "geminiAngle",
    )
    val breath by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "geminiBreath",
    )
    // Eases in and out of the pulse rather than snapping when writing ends.
    val pulsing by androidx.compose.animation.core.animateFloatAsState(if (pulse && !reduced) 1f else 0f, tween(500), label = "geminiPulse")
    val turn = if (reduced) 45f else angle
    drawWithContent {
        drawContent()
        val radius = CornerRadius(corner.toPx())
        val stroke = width.toPx()
        // The colour stops turn, so the colours travel round the edge.
        val brush = rotatedSweep(turn, size.width, size.height)
        // The bloom: the same line, wider and fainter, twice, outside it.
        val swell = 1f + pulsing * (0.6f + 1.4f * breath)
        for ((grow0, alpha0) in listOf(6f to 0.10f, 3f to 0.18f)) {
            val grow = grow0 * swell
            val alpha = (alpha0 * (1f + pulsing * breath * 1.2f)).coerceAtMost(0.5f)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(-grow / 2, -grow / 2),
                size = androidx.compose.ui.geometry.Size(size.width + grow, size.height + grow),
                cornerRadius = CornerRadius(radius.x + grow / 2),
                style = Stroke(stroke + grow),
                alpha = alpha,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(stroke),
        )
    }
}

/**
 * A sweep gradient whose start is turned by [degrees]. Compose's sweep always
 * starts at three o'clock, so the turn is made by rotating the colour stops.
 */
private fun rotatedSweep(degrees: Float, width: Float, height: Float): Brush {
    val shift = ((degrees % 360f) / 360f)
    val stops = GeminiColors.mapIndexed { index, color ->
        val at = (index.toFloat() / (GeminiColors.size - 1) + shift) % 1f
        at to color
    }.sortedBy { it.first }
    // Close the loop at both ends so there is no seam where 1 wraps to 0.
    val first = stops.first()
    val last = stops.last()
    val edge = lerpColor(last.second, first.second, (1f - last.first) / ((1f - last.first) + first.first).coerceAtLeast(0.0001f))
    val full = listOf(0f to edge) + stops + listOf(1f to edge)
    return Brush.sweepGradient(*full.toTypedArray(), center = Offset(width / 2, height / 2))
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** Gemini's colours across a small mark: the sparkle on an Ask button. */
val geminiBrushStatic: Brush = Brush.linearGradient(GeminiColors.take(3))
