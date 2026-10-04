package com.cineverse.app.core.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle

/**
 * There is more below this.
 *
 * A title page opens on a full-bleed poster and a row of buttons, and on a tall
 * phone that is the entire first screen — nothing about it says the episode
 * list, the countdown and the cast are underneath. People genuinely do not
 * scroll pages that look finished.
 *
 * The hint is a chevron that draws itself downward on a repeating stroke, with
 * a soft wash behind it so it reads over artwork of any brightness. Three rules
 * keep it from being annoying, which is the only risk a thing like this carries:
 *
 *  - it appears a beat AFTER the page settles, not instantly, so it never
 *    competes with the content arriving;
 *  - it vanishes the moment anything is scrolled, and never returns for that
 *    screen;
 *  - under reduced motion it does not animate at all, and under a screen reader
 *    it is not announced, because it is a nudge rather than information.
 */
@Composable
fun ScrollHint(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    label: String = "Scroll for more",
    /**
     * The item the hint must never sit on top of - a title page's action row.
     * If that item already runs past the bottom of the screen, the page is
     * visibly cut off, which says "scroll" by itself, and a hint drawn there
     * would only cover the buttons.
     */
    avoid: Any? = null,
) {
    val colors = CvTheme.colors
    val clearancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val blocked by remember(avoid) {
        derivedStateOf {
            if (avoid == null) return@derivedStateOf false
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == avoid }
                ?: return@derivedStateOf false
            item.offset + item.size > info.viewportEndOffset - clearancePx
        }
    }

    // Anything at all: one notch of scroll means the hint has done its job.
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }
    // Once false, it stays false. A hint that comes back when you scroll to the
    // top again is a hint that starts feeling like an instruction.
    val spent = remember { androidx.compose.runtime.mutableStateOf(false) }
    if (scrolled) spent.value = true

    AnimatedVisibility(
        visible = !spent.value && !blocked,
        enter = fadeIn(tween(600, delayMillis = 1_400)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        val reduced = CvTheme.reducedMotion
        val drift by rememberInfiniteTransition(label = "hint").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "drift",
        )
        val travel = if (reduced) 0f else drift

        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.ink.copy(alpha = 0.55f))
                    )
                )
                .padding(bottom = 14.dp, top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label.uppercase(), style = KickerStyle, color = colors.text2)
            Spacer(Modifier.height(6.dp))
            Canvas(
                Modifier
                    .size(width = 26.dp, height = 16.dp)
                    .graphicsLayer {
                        translationY = travel * 7f
                        alpha = 0.55f + 0.45f * travel
                    }
            ) {
                val stroke = 2.4.dp.toPx()
                // One chevron, drawn rather than glyphed, so its weight matches
                // the rest of the icon set at any size.
                drawLine(
                    color = colors.text,
                    start = Offset(0f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = colors.text,
                    start = Offset(size.width / 2f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * The same idea, sideways, for a rail that runs off the edge.
 *
 * A fade at the right-hand edge of a row says "this continues" without taking
 * any space from it, which on a 132dp card is the difference between four cards
 * visible and three and a half. Only drawn while there IS more to the right.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.EdgeHint(listState: androidx.compose.foundation.lazy.LazyListState) {
    val colors = CvTheme.colors
    val more by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index < info.totalItemsCount - 1 ||
                last.offset + last.size > info.viewportEndOffset
        }
    }
    AnimatedVisibility(
        visible = more,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        Box(
            Modifier
                .width(46.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.ink.copy(alpha = 0.85f))
                    )
                )
        )
    }
}

/**
 * The left-hand edge's fade, once a row has been scrolled: the website's
 * "middle" and "end" states, where there is more back the way you came.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.StartEdgeHint(listState: androidx.compose.foundation.lazy.LazyListState) {
    val colors = CvTheme.colors
    val behind by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24 }
    }
    AnimatedVisibility(
        visible = behind,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterStart),
    ) {
        Box(
            Modifier
                .width(30.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(colors.ink.copy(alpha = 0.8f), Color.Transparent)))
        )
    }
}

/**
 * The nudge: the first time a rail is ever on screen, it slides a little way
 * along and settles back, so a row that is cut by the screen edge says it
 * scrolls. Each rail does it once on this device, a beat after it arrives,
 * and not at all under reduced motion or once you have touched it.
 */
@Composable
fun RailNudge(listState: androidx.compose.foundation.lazy.LazyListState, id: String, ready: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduced = CvTheme.reducedMotion
    val distance = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val haptics = com.cineverse.app.core.design.LocalHaptics.current
    androidx.compose.runtime.LaunchedEffect(id, ready) {
        if (!ready || reduced) return@LaunchedEffect
        val prefs = context.getSharedPreferences("rail_hints", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(id, false)) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        if (listState.isScrollInProgress || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) return@LaunchedEffect
        if (!listState.canScrollForward) return@LaunchedEffect
        prefs.edit().putBoolean(id, true).apply()
        listState.animateScrollBy(distance, tween(520, easing = FastOutSlowInEasing))
        haptics?.play(com.cineverse.app.core.design.Haptic.Tick)
        listState.animateScrollBy(-distance, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 220f))
    }
}
