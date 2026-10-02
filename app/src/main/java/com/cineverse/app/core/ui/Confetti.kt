package com.cineverse.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A burst, for the one moment in the app that deserves one.
 *
 * The website fires this on a perfect ten, and it is the right and only place:
 * a rating of ten is rare, deliberate, and the single most enthusiastic thing
 * anyone does in CineVerse. Firing confetti on every save would make the app
 * exhausting and would devalue the one time it means something.
 *
 * Drawn on a Canvas rather than with a particle library, because it is ninety
 * rectangles under gravity for 1.6 seconds and that is genuinely all it is.
 * Every piece gets its own angle, speed, spin, size and drift, chosen once from
 * a seeded Random so the burst is different each time without being random
 * frame to frame.
 *
 * It is NOT interactive and must never block a tap: the whole thing is a
 * non-consuming overlay, so the sheet underneath keeps working while it falls.
 * And it respects reduced motion by simply not existing, which is the only
 * honest way to reduce an effect that is pure motion.
 */
@Composable
fun Confetti(
    play: Boolean,
    modifier: Modifier = Modifier,
    pieces: Int = 90,
    durationMillis: Int = 1_600,
) {
    if (CvTheme.reducedMotion) return
    val colors = CvTheme.colors

    val progress = remember { Animatable(0f) }
    val burst = remember(play) {
        if (!play) emptyList() else {
            val random = Random(System.currentTimeMillis())
            val palette = listOf(
                colors.gold,
                Palette.Red2,
                colors.cyan,
                colors.green,
                colors.purple,
                colors.pink,
            )
            List(pieces) {
                // Upward and outward, in a fan rather than a circle: a burst
                // that throws pieces downward looks like something broke.
                val angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 0.9f).toFloat()
                Piece(
                    angle = angle,
                    speed = 0.55f + random.nextFloat() * 0.75f,
                    spin = (random.nextFloat() - 0.5f) * 1400f,
                    width = 5f + random.nextFloat() * 7f,
                    height = 9f + random.nextFloat() * 12f,
                    drift = (random.nextFloat() - 0.5f) * 0.5f,
                    color = palette[random.nextInt(palette.size)],
                    delay = random.nextFloat() * 0.12f,
                )
            }
        }
    }

    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    }

    if (burst.isEmpty()) return

    Canvas(modifier.fillMaxSize()) {
        val originX = size.width / 2f
        val originY = size.height * 0.46f
        val reach = size.minDimension * 1.15f

        for (piece in burst) {
            val t = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue

            // Ballistic: constant outward velocity, gravity squared on top. The
            // squared term is what makes it fall rather than drift, and it is
            // the difference between confetti and a screensaver.
            val x = originX + cos(piece.angle) * piece.speed * reach * t +
                piece.drift * reach * t * t
            val y = originY + sin(piece.angle) * piece.speed * reach * t +
                1.9f * reach * t * t

            // Fading only at the very end, so the burst does not look like it
            // is dying from the moment it starts.
            val alpha = ((1f - t) / 0.28f).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue

            rotate(degrees = piece.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - piece.width / 2f, y - piece.height / 2f),
                    size = Size(piece.width, piece.height),
                )
            }
        }
    }
}

private data class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val drift: Float,
    val color: Color,
    val delay: Float,
)
