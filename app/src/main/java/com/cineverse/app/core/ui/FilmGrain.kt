package com.cineverse.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.cineverse.app.core.design.CvTheme
import kotlin.random.Random

/**
 * Film grain, while a trailer loads: the still backdrop starts to flicker like
 * projected film, with a soft light running across it, and both give way as
 * the trailer fades up. It says "something is coming" without a spinner, and
 * without anything a spinner would cover.
 *
 * The grain is real noise - one small tile of random specks, re-dealt at 24
 * frames a second by moving where the tile starts, which is the rate film
 * runs at and the reason it reads as film rather than static. Nothing is
 * drawn under reduced motion.
 */
@Composable
fun FilmGrain(visible: Boolean, modifier: Modifier = Modifier) {
    if (CvTheme.reducedMotion) return
    // A trailer that never arrives must not leave the picture grainy for ever.
    var expired by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(visible) {
        expired = false
        if (visible) { kotlinx.coroutines.delay(8_000); expired = true }
    }
    val on = visible && !expired
    val strength by animateFloatAsState(if (on) 1f else 0f, tween(if (on) 500 else 700), label = "grain")
    if (strength <= 0.01f && !on) return
    val tile = remember { noiseTile() }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(on) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // 24 frames a second, whatever the display's own rate.
                if (now - last >= 41_666_666L) {
                    frame++
                    last = now
                }
            }
            if (!on && strength <= 0.01f) break
        }
    }
    val sweep by rememberInfiniteTransition(label = "grainSweep").animateFloat(
        -0.4f, 1.4f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sweepX",
    )
    Canvas(modifier.fillMaxSize()) {
        val random = Random(frame)
        val shift = Offset(random.nextFloat() * 160f, random.nextFloat() * 160f)
        translate(-shift.x, -shift.y) {
            drawRect(
                brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.width + 160f, size.height + 160f),
                alpha = 0.16f * strength,
            )
        }
        // The projector's light, crossing the frame.
        val x = size.width * sweep
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.07f * strength), Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height * 0.4f),
            ),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.translate(
    x: Float,
    y: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) = drawContext.transform.let { transform ->
    transform.translate(x, y)
    block()
    transform.translate(-x, -y)
}

/** A 160px tile of random light and dark specks, mostly transparent. */
private fun noiseTile(): ImageBitmap {
    val size = 160
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val roll = random.nextFloat()
        when {
            roll < 0.07f -> android.graphics.Color.argb(150 + random.nextInt(105), 255, 255, 255)
            roll < 0.14f -> android.graphics.Color.argb(120 + random.nextInt(100), 0, 0, 0)
            else -> 0
        }
    }
    return android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
