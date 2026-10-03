package com.cineverse.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette

/**
 * The handful of controls the franchise, box-office and year screens share.
 *
 * Each existed two or three times over as a private copy inside one screen, and
 * three copies of a chip are three chips that will be a pixel apart by the next
 * release. New screens use these; the old private copies are left alone until
 * they next change, because a refactor that moves every screenshot at once
 * hides the one that actually broke.
 */

/** A filter chip, optionally with a count. The active one inverts. */
@Composable
fun CvChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val fill by animateColorAsState(
        if (active) colors.text else colors.glass,
        animationSpec = tween(Motion.Quick),
        label = "chip",
    )
    val ink by animateColorAsState(
        if (active) colors.ink else colors.text2,
        animationSpec = tween(Motion.Quick),
        label = "chipInk",
    )
    Row(
        modifier
            .height(34.dp)
            .clip(CvShape.Pill)
            .background(fill)
            .border(1.dp, if (active) Color.Transparent else colors.hairline, CvShape.Pill)
            .clickableNoRipple {
                if (!active) haptics?.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.6f),
            )
        }
    }
}

/** A pill button: red and filled for the one thing to do, glass for the rest. */
@Composable
fun CvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .height(46.dp)
            .then(
                if (primary) Modifier.clip(CvShape.Pill).background(
                    if (enabled) Palette.Red else Palette.Red.copy(alpha = 0.4f)
                )
                else Modifier.glass(CvShape.Pill, strength = 1.1f)
            )
            .clickableNoRipple {
                if (!enabled) return@clickableNoRipple
                haptics?.play(Haptic.Tap)
                onClick()
            }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (primary) Color.White else colors.text
        if (icon != null) {
            Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = tint.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
        )
    }
}

/**
 * A value that travels from zero to [target] the first time it is shown, and
 * springs to any later value from wherever it is. The shared engine behind the
 * ring and the bars, so a figure that changes mid-animation never snaps.
 */
@Composable
fun rememberArrival(target: Float, delayMillis: Int = 0, durationMillis: Int = 900): Float {
    val reduced = CvTheme.reducedMotion
    val value = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) value.snapTo(target)
        else value.animateTo(target, tween(durationMillis, delayMillis, Motion.EaseOut))
    }
    return value.value
}

/**
 * Lets a full-width child (a rail) run past its padded parent to the screen's
 * edges, so a rail inside a padded grid scrolls edge to edge like every other
 * rail rather than inside a margin.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * A list row rising into place the first time it appears.
 *
 * [shown] is the screen's memory of which rows have already arrived, so a row
 * scrolled away and back does not perform its entrance again — an arrival that
 * replays is decoration, not information. Only the first screenful is
 * staggered; a row reached by scrolling arrives at once.
 */
@Composable
fun Modifier.riseIn(key: Any, index: Int, shown: MutableSet<Any>): Modifier {
    val first = remember(key) { shown.add(key) }
    if (!first || CvTheme.reducedMotion) return this
    val progress = rememberArrival(1f, if (index < 8) index * 55 else 0, 620)
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 46f
    }
}

/**
 * A progress ring that sweeps itself in. Gold when the thing is complete,
 * because a finished franchise is a small trophy and should look like one.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    color: Color = if (fraction >= 1f) CvTheme.colors.gold else Palette.Red2,
    delayMillis: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = CvTheme.colors
    val swept = rememberArrival(fraction.coerceIn(0f, 1f), delayMillis, 1_100)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val px = stroke.toPx()
                val inset = px / 2f
                val arcSize = Size(this.size.width - px, this.size.height - px)
                drawArc(
                    color = colors.text.copy(alpha = 0.1f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(px),
                )
                if (swept > 0f) drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                    startAngle = -90f, sweepAngle = 360f * swept, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(px, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A horizontal bar that grows to its value on first view. [delayMillis] lets a
 * list of them arrive one after another, which reads as a ranking being drawn
 * rather than a table being printed.
 */
@Composable
fun GrowBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Palette.Red2,
    height: Dp = 6.dp,
    delayMillis: Int = 0,
) {
    val colors = CvTheme.colors
    val grown = rememberArrival(fraction.coerceIn(0f, 1f), delayMillis)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CvShape.Pill)
            .background(colors.text.copy(alpha = 0.08f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(grown)
                .clip(CvShape.Pill)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
        )
    }
}
