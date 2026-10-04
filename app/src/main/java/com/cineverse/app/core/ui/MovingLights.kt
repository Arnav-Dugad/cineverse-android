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
fun Modifier.movingLights(enabled: Boolean, mesmerise: Boolean = false): Modifier = if (!enabled && !mesmerise) this else composed {
    // The mesmerising background: four soft auroras drifting on slow
    // Lissajous paths that never quite repeat (their periods share no
    // factor), breathing in and out. Read only in the draw phase.
    val aurora = rememberInfiniteTransition(label = "aurora")
    val t1 by aurora.animateFloat(0f, 1f, infiniteRepeatable(tween(47_000, easing = LinearEasing)), label = "a1")
    val t2 by aurora.animateFloat(0f, 1f, infiniteRepeatable(tween(61_000, easing = LinearEasing)), label = "a2")
    val t3 by aurora.animateFloat(0f, 1f, infiniteRepeatable(tween(73_000, easing = LinearEasing)), label = "a3")
    val breath by aurora.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000), RepeatMode.Reverse), label = "aBreath")
    val reduced = CvTheme.reducedMotion
    val dark = CvTheme.colors.isDark
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0.5f) }
    val y = remember { Animatable(0.35f) }
    val lean = remember { Animatable(0f) }
    var settle: Job? = remember { null }
    var unlean: Job? = remember { null }
    val drift = spring<Float>(dampingRatio = 1f, stiffness = 18f)
    // A third, smaller light that follows the finger closely.
    val fx = remember { Animatable(0.5f) }
    val fy = remember { Animatable(0.5f) }
    val glow = remember { Animatable(0f) }
    val follow = spring<Float>(dampingRatio = 0.9f, stiffness = 120f)

    val scroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (reduced || consumed.y == 0f) return Offset.Zero
                unlean?.cancel()
                unlean = scope.launch {
                    lean.snapTo((lean.value + consumed.y * 0.004f).coerceIn(-1f, 1f))
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
                            scope.launch { fx.animateTo(tx, follow) }
                            scope.launch { fy.animateTo(ty, follow) }
                            if (event.type == PointerEventType.Press) scope.launch { glow.animateTo(1f, spring(stiffness = 300f)) }
                        }
                        PointerEventType.Release -> {
                            scope.launch { glow.animateTo(0f, androidx.compose.animation.core.tween(1400)) }
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
            val strength = if (dark) 1f else 0.6f
            if (mesmerise) {
                val tau = (2 * Math.PI).toFloat()
                fun s(t: Float, k: Float = 1f) = kotlin.math.sin(t * tau * k)
                fun c(t: Float, k: Float = 1f) = kotlin.math.cos(t * tau * k)
                val still = reduced
                val auroras = listOf(
                    Triple(Offset(w * (0.5f + 0.42f * s(t1)), h * (0.3f + 0.22f * c(t2, 2f))), Palette.Red, 1.05f),
                    Triple(Offset(w * (0.5f + 0.45f * c(t2)), h * (0.62f + 0.25f * s(t3))), Palette.Purple, 1.15f),
                    Triple(Offset(w * (0.5f + 0.4f * s(t3, 2f)), h * (0.5f + 0.35f * c(t1))), Color(0xFF2E6BFF), 0.95f),
                    Triple(Offset(w * (0.5f + 0.38f * c(t1, 2f)), h * (0.18f + 0.3f * s(t2))), Color(0xFF14B8A6), 0.85f),
                )
                for ((index, aurora) in auroras.withIndex()) {
                    val (centre, colour, size) = aurora
                    val at = if (still) Offset(w * (0.2f + 0.2f * index), h * (0.25f + 0.17f * index)) else centre
                    val radius = w * size * (0.9f + 0.18f * breath)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(colour.copy(alpha = 0.11f * strength), colour.copy(alpha = 0.04f * strength), Color.Transparent),
                            center = at, radius = radius,
                        ),
                        radius = radius, center = at, blendMode = if (dark) BlendMode.Screen else BlendMode.SrcOver,
                    )
                }
            }
            if (!enabled) return@drawWithContent
            val blend = if (dark) BlendMode.Screen else BlendMode.SrcOver
            val red = Offset(w * (0.18f + dx * 0.8f), h * (0.16f + dy * 0.6f - tilt * 0.14f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Red.copy(alpha = 0.17f * strength), Color.Transparent),
                    center = red, radius = w * 0.95f,
                ),
                radius = w * 0.95f, center = red, blendMode = blend,
            )
            val purple = Offset(w * (0.86f + dx * 0.55f), h * (0.74f + dy * 0.45f + tilt * 0.12f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Palette.Purple.copy(alpha = 0.15f * strength), Color.Transparent),
                    center = purple, radius = w * 0.85f,
                ),
                radius = w * 0.85f, center = purple, blendMode = blend,
            )
            // Under the finger while it is down, fading after it lifts.
            if (glow.value > 0.01f) {
                val touch = Offset(w * fx.value, h * fy.value)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Palette.Red2.copy(alpha = 0.16f * glow.value * strength), Color.Transparent),
                        center = touch, radius = w * 0.45f,
                    ),
                    radius = w * 0.45f, center = touch, blendMode = blend,
                )
            }
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
