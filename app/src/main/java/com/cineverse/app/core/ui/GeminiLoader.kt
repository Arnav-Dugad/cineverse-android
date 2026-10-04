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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.cineverse.app.core.design.CvTheme

/**
 * Gemini thinking: the Material 3 Expressive loader's idea, drawn here - one
 * soft shape that turns while it morphs from form to form (a soft burst, a
 * pentagon, a flower, a pill-round cookie), in Gemini's colours. Built on
 * AndroidX's graphics-shapes, the same morphing the M3 loader uses.
 */
@Composable
fun GeminiLoader(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val reduced = CvTheme.reducedMotion
    val shapes = remember {
        listOf(
            RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.78f, rounding = CornerRounding(0.18f)),
            RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.32f)),
            RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.62f, rounding = CornerRounding(0.28f)),
            RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.86f, rounding = CornerRounding(0.12f)),
        )
    }
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val loop = rememberInfiniteTransition(label = "geminiLoader")
    val phase by loop.animateFloat(0f, shapes.size.toFloat(), infiniteRepeatable(tween(650 * shapes.size, easing = LinearEasing)), label = "phase")
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "turn")
    val tint by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "tint")
    Canvas(modifier.size(size)) {
        val index = phase.toInt().coerceIn(0, morphs.lastIndex)
        // Each change eases in and out, with a brief hold on each shape.
        val raw = (phase - index).coerceIn(0f, 1f)
        val t = if (reduced) 0f else androidx.compose.animation.core.FastOutSlowInEasing.transform(raw)
        val path = morphs[index].toPath(t).asComposePath()
        val radius = this.size.minDimension / 2f
        val color = lerp(GeminiColors[0], GeminiColors[2], tint)
        rotate(if (reduced) 0f else turn) {
            translate(center.x, center.y) {
                scale(radius * 0.92f, radius * 0.92f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(path, color)
                }
            }
        }
    }
}
