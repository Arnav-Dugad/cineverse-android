package com.cineverse.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * Pull down to refresh, CineVerse-shaped.
 *
 * Written rather than taken from Material because the stock indicator is a
 * circle on a white chip, which on a page of edge-to-edge artwork looks like a
 * dialog that escaped. This one is a thin arc in the app own ink, it fills as
 * the finger travels, and it spins while the work runs.
 *
 * Two details that make it feel right rather than merely work:
 *
 *  - The pull is RUBBER-BANDED. Past the trigger the arc keeps moving but at a
 *    fraction of the finger speed, so there is a clear physical sense of having
 *    reached the end of the gesture rather than a value that silently clamps.
 *  - The trigger fires a Detent the moment it is crossed, on the way down, not
 *    on release. That is what tells a thumb it can let go, and it is the whole
 *    difference between a gesture you learn once and one you guess at each time.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val triggerPx = with(density) { 86.dp.toPx() }

    var pull by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    // While the work runs the arc parks at the trigger rather than snapping to
    // zero: a spinner that jumps back to the top edge reads as the pull having
    // been rejected.
    val resting by animateFloatAsState(
        targetValue = if (refreshing) triggerPx else 0f,
        animationSpec = Motion.landing(),
        label = "rest",
    )
    val travel = if (pull > 0f) pull else resting

    val connection = remember(enabled, refreshing, triggerPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up while the arc is out consumes the gesture, so
                // the list does not start moving before the arc has retracted.
                if (!enabled || refreshing || available.y >= 0f || pull <= 0f) return Offset.Zero
                val used = (-pull).coerceAtLeast(available.y)
                pull = (pull + used).coerceAtLeast(0f)
                return Offset(0f, used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled || refreshing || available.y <= 0f) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Rubber band: each pixel past the trigger is worth less than
                // the one before it, so the arc decelerates into its limit.
                val resistance = if (pull < triggerPx) 0.55f
                else 0.55f * (1f - (pull / (triggerPx * 2.4f)).coerceAtMost(0.92f)).pow(1.2f)
                pull += available.y * resistance
                if (!armed && pull >= triggerPx) {
                    armed = true
                    haptics?.play(Haptic.Detent)
                } else if (armed && pull < triggerPx) {
                    armed = false
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || refreshing) return Velocity.Zero
                val fire = pull >= triggerPx
                if (fire) {
                    haptics?.play(Haptic.Success)
                    scope.launch { onRefresh() }
                }
                pull = 0f
                armed = false
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        Box(
            Modifier
                .fillMaxSize()
                // The page follows the finger, at a third of the arc travel. Any
                // more and the content starts fighting the sticky headers above
                // it; any less and the pull feels like it is happening to
                // something else.
                .graphicsLayer { translationY = travel * 0.34f },
            content = content,
        )
        RefreshArc(
            travel = travel,
            trigger = triggerPx,
            spinning = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun RefreshArc(
    travel: Float,
    trigger: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    if (travel <= 0.5f && !spinning) return
    val progress = (travel / trigger).coerceIn(0f, 1.3f)

    val spin by animateFloatAsState(
        targetValue = if (spinning) 1f else 0f,
        animationSpec = Motion.fade(),
        label = "spinFade",
    )
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rotation",
    )

    Box(
        modifier
            .offset { androidx.compose.ui.unit.IntOffset(0, (travel * 0.62f).toInt()) }
            .size(30.dp)
            .graphicsLayer {
                // Before the trigger the arc grows and fades in with the pull;
                // past it, it holds, because a thing that keeps growing has no
                // threshold to read.
                val scale = 0.55f + 0.45f * progress.coerceAtMost(1f)
                scaleX = scale
                scaleY = scale
                alpha = (progress * 1.6f).coerceIn(0f, 1f)
                rotationZ = if (spinning) rotation else progress * 240f
            }
            .drawBehind {
                val stroke = 2.6.dp.toPx()
                val inset = stroke / 2f
                val sweep = if (spinning) 70f + 40f * spin else (progress.coerceAtMost(1f) * 300f)
                // The track, so the arc has something to be a fraction of.
                drawArc(
                    color = colors.text.copy(alpha = 0.16f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = colors.text,
                    startAngle = -90f,
                    sweepAngle = abs(sweep).coerceAtLeast(8f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
    )
}
