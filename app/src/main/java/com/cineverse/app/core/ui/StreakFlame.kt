package com.cineverse.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import kotlin.math.sin

/**
 * The streak's flame: drawn, not a glyph, so it can live. Lit, it flickers -
 * the tip sways and the core breathes, on two clocks that never line up, so
 * it never settles into a visible loop. Out, it is a grey, still shape: a
 * streak that broke is not on fire.
 */
@Composable
fun StreakFlame(lit: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val reduced = CvTheme.reducedMotion
    val loop = rememberInfiniteTransition(label = "flame")
    val sway by loop.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sway")
    val breath by loop.animateFloat(0f, 6.283f, infiniteRepeatable(tween(870, easing = LinearEasing), RepeatMode.Restart), label = "breath")
    val dead = CvTheme.colors.text3.copy(alpha = 0.5f)
    val hollow = CvTheme.colors.ink.copy(alpha = 0.6f)
    Canvas(modifier.size(size)) {
        val motion = if (lit && !reduced) 1f else 0f
        val tipShift = sin(sway) * size.toPx() * 0.05f * motion
        val pulse = 1f + sin(breath) * 0.05f * motion
        if (lit) {
            // The glow behind it.
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FF7A1A), Color.Transparent)),
                radius = this.size.minDimension * 0.6f * pulse,
                center = Offset(this.size.width / 2, this.size.height * 0.62f),
            )
            flame(0.92f * pulse, tipShift, Brush.verticalGradient(listOf(Color(0xFFFFB020), Color(0xFFFF4D1A), Color(0xFFE5091A))))
            flame(0.52f * pulse, tipShift * 0.6f, Brush.verticalGradient(listOf(Color(0xFFFFF4B0), Color(0xFFFFC23D))), lift = 0.12f)
        } else {
            // Out: the same flame in grey, its inner tongue cut out darker,
            // so it still reads as a flame and not a drop.
            flame(0.92f, 0f, Brush.verticalGradient(listOf(dead, dead)))
            flame(0.5f, 0f, Brush.verticalGradient(listOf(hollow, hollow)), lift = 0.12f)
        }
    }
}

/** A flame, [scale] of the box, its tip pushed sideways by [tip] px. */
private fun DrawScope.flame(scale: Float, tip: Float, brush: Brush, lift: Float = 0f) {
    val w = size.width * scale
    val h = size.height * scale
    val cx = size.width / 2
    val bottom = size.height * (0.96f - lift)
    val top = bottom - h
    val path = Path().apply {
        moveTo(cx + tip, top)
        cubicTo(cx + w * 0.10f + tip, top + h * 0.25f, cx + w * 0.50f, top + h * 0.42f, cx + w * 0.44f, top + h * 0.70f)
        cubicTo(cx + w * 0.38f, bottom - h * 0.04f, cx + w * 0.12f, bottom, cx, bottom)
        cubicTo(cx - w * 0.12f, bottom, cx - w * 0.38f, bottom - h * 0.04f, cx - w * 0.44f, top + h * 0.70f)
        cubicTo(cx - w * 0.50f, top + h * 0.42f, cx - w * 0.04f + tip, top + h * 0.30f, cx + tip, top)
        close()
    }
    drawPath(path, brush)
}
