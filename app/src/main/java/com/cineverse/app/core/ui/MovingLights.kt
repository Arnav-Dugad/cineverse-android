package com.cineverse.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Moving lights, from the website's stage: two soft glows, the brand's red
 * high on the left and purple low on the right, that drift toward where you
 * touch and lean the way you scroll, then settle back when you let go.
 *
 * Drawn over the page as a faint wash (screen-blended on the dark theme, so
 * it lifts rather than tints), never under it - the pages are opaque cards.
 * Touches are only watched, never taken, and the drawing happens in the draw
 * phase, so a moving light costs no recomposition. Reduced motion keeps the
 * lights where they rest.
 */
fun Modifier.movingLights(enabled: Boolean): Modifier = if (!enabled) this else composed {
    val reduced = CvTheme.reducedMotion
    val dark = CvTheme.colors.isDark
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0.5f) }
    val y = remember { Animatable(0.35f) }
    val lean = remember { Animatable(0f) }
    var settle: Job? = remember { null }
    var unlean: Job? = remember { null }
    val drift = spring<Float>(dampingRatio = 1f, stiffness = 18f)

    val scroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (reduced || consumed.y == 0f) return Offset.Zero
                unlean?.cancel()
                unlean = scope.launch {
                    lean.snapTo((lean.value + consumed.y * 0.0016f).coerceIn(-1f, 1f))
                    delay(90)
                    lean.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 40f))
                }
                return Offset.Zero
            }
        }
    }

    this
        .nestedScroll(scroll)
        .pointerInput(reduced) {
            if (reduced) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val point = event.changes.firstOrNull()?.position ?: continue
                    when (event.type) {
                        PointerEventType.Press, PointerEventType.Move -> {
                            settle?.cancel()
                            val tx = (point.x / size.width).coerceIn(0f, 1f)
                            val ty = (point.y / size.height).coerceIn(0f, 1f)
                            scope.launch { x.animateTo(tx, drift) }
                            scope.launch { y.animateTo(ty, drift) }
                        }
                        PointerEventType.Release -> {
                            settle = scope.launch {
                                delay(2_200)
                                launch { x.animateTo(0.5f, drift) }
                                launch { y.animateTo(0.35f, drift) }
                            }
                        }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            val dx = x.value - 0.5f
            val dy = y.value - 0.35f
            val tilt = lean.value
            val strength = if (dark) 1f else 0.55f
            val blend = if (dark) BlendMode.Screen else BlendMode.SrcOver
            val red = Offset(w * (0.18f + dx * 0.55f), h * (0.16f + dy * 0.4f - tilt * 0.06f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Red.copy(alpha = 0.085f * strength), Color.Transparent),
                    center = red, radius = w * 0.95f,
                ),
                radius = w * 0.95f, center = red, blendMode = blend,
            )
            val purple = Offset(w * (0.86f + dx * 0.35f), h * (0.74f + dy * 0.28f + tilt * 0.05f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Purple.copy(alpha = 0.08f * strength), Color.Transparent),
                    center = purple, radius = w * 0.85f,
                ),
                radius = w * 0.85f, center = purple, blendMode = blend,
            )
        }
}

/**
 * Gemini at work, round the whole screen: a thin line in Gemini's colours
 * that hugs the display's own rounded corners, turning slowly, with a soft
 * bloom inward that breathes. It fades in when a request goes out and away
 * when the last one comes back. Nothing is drawn, and nothing animates, when
 * Gemini is idle.
 */
@Composable
fun GeminiEdgeGlow(active: Boolean, modifier: Modifier = Modifier) {
    val reduced = CvTheme.reducedMotion
    val shown by animateFloatAsState(if (active) 1f else 0f, tween(if (active) 450 else 700), label = "edgeGlow")
    if (shown <= 0.001f) return
    val view = LocalView.current
    // The display's own corner, so the glow follows the glass, not a guess.
    val corner = remember(view) {
        view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
    }
    val loop = rememberInfiniteTransition(label = "edgeGlowLoop")
    val angle by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "edgeAngle")
    val breath by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "edgeBreath")
    Canvas(modifier.fillMaxSize()) {
        val radius = CornerRadius(corner ?: 48.dp.toPx())
        val brush = rotatedSweep(if (reduced) 45f else angle, size.width, size.height)
        val swell = if (reduced) 0.5f else breath
        // The bloom, widest and faintest first, then the line itself.
        for ((width, alpha) in listOf(26.dp to 0.10f, 14.dp to 0.18f, 6.dp to 0.35f)) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = radius,
                style = Stroke(width.toPx() * (0.85f + 0.3f * swell)),
                alpha = alpha * shown,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = radius,
            style = Stroke(3.dp.toPx()),
            alpha = 0.95f * shown,
        )
    }
}
