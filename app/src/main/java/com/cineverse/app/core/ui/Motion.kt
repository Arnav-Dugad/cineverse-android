package com.cineverse.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Motion
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A number that counts up when it first comes into view.
 *
 * The website's `observeCountUps`, and the one piece of decoration that is also
 * information: a figure arriving at 1,284 rather than simply being 1,284 makes
 * you read it. It runs ONCE — a number that re-counts every time you scroll past
 * is a number you stop trusting.
 */
@Composable
fun CountUpText(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = CvTheme.colors.text,
    format: (Long) -> String = { "%,d".format(it) },
    durationMillis: Int = 900,
) {
    var seen by remember(value) { mutableStateOf(false) }
    val reduced = CvTheme.reducedMotion
    val progress by animateFloatAsState(
        targetValue = if (seen || reduced) 1f else 0f,
        animationSpec = tween(durationMillis, easing = Motion.EaseOut),
        label = "count",
    )
    Text(
        format(if (reduced) value else (value * progress).roundToLong()),
        style = style,
        color = color,
        modifier = modifier.onGloballyPositioned { coordinates ->
            // Any part of it on screen is enough: waiting for the whole figure
            // means a tall number never starts.
            if (!seen && coordinates.size.height > 0) seen = true
        },
    )
}

/** The same, for a figure with a decimal — a rating, an average. */
@Composable
fun CountUpDecimal(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = CvTheme.colors.text,
    decimals: Int = 1,
    durationMillis: Int = 900,
) {
    var seen by remember(value) { mutableStateOf(false) }
    val reduced = CvTheme.reducedMotion
    val progress by animateFloatAsState(
        targetValue = if (seen || reduced) 1f else 0f,
        animationSpec = tween(durationMillis, easing = Motion.EaseOut),
        label = "countDecimal",
    )
    Text(
        "%.${decimals}f".format(if (reduced) value else value * progress),
        style = style,
        color = color,
        modifier = modifier.onGloballyPositioned { if (!seen && it.size.height > 0) seen = true },
    )
}

/**
 * The same, for a figure already formatted as text.
 *
 * Statistics arrive as strings — "1,284", "12h 30m", "7.4" — because the
 * formatting is part of what they mean and the view model is the right place for
 * it. This animates the FIRST run of digits it finds and leaves everything
 * around it alone, so "12h 30m" counts its hours up and keeps its minutes, and a
 * figure with no digits in it at all simply renders.
 */
@Composable
fun CountUpString(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = CvTheme.colors.text,
    durationMillis: Int = 900,
) {
    val match = remember(value) { Regex("""\d[\d,]*(\.\d+)?""").find(value) }
    if (match == null) {
        Text(value, style = style, color = color, modifier = modifier)
        return
    }
    val digits = match.value
    val decimals = digits.substringAfter('.', "").length
    val grouped = digits.contains(',')
    val number = digits.replace(",", "").toDoubleOrNull()
    if (number == null) {
        Text(value, style = style, color = color, modifier = modifier)
        return
    }

    var seen by remember(value) { mutableStateOf(false) }
    val reduced = CvTheme.reducedMotion
    val progress by animateFloatAsState(
        targetValue = if (seen || reduced) 1f else 0f,
        animationSpec = tween(durationMillis, easing = Motion.EaseOut),
        label = "countString",
    )
    val shown = if (reduced) number else number * progress
    val rendered = when {
        decimals > 0 -> "%.${decimals}f".format(shown)
        grouped -> "%,d".format(shown.roundToLong())
        else -> shown.roundToLong().toString()
    }
    Text(
        value.replaceRange(match.range, rendered),
        style = style,
        color = color,
        modifier = modifier.onGloballyPositioned { if (!seen && it.size.height > 0) seen = true },
    )
}

/**
 * The hand of cards.
 *
 * A rail's first paint deals itself out: each card rises, fades and un-rotates
 * into place a beat after the one before it. The website describes it as the row
 * fanning out like a hand of cards, and the rotation is what makes it that
 * rather than a plain stagger.
 *
 * Only the first screenful is staggered. A card ten places along would otherwise
 * wait half a second to appear when you flick straight to it, which is a rail
 * that feels broken rather than one that feels considered.
 */
@Composable
fun Modifier.dealIn(index: Int, revealed: Boolean): Modifier {
    val reduced = CvTheme.reducedMotion
    val progress by animateFloatAsState(
        targetValue = if (revealed || reduced) 1f else 0f,
        animationSpec = tween(
            durationMillis = 520,
            delayMillis = if (reduced) 0 else (index.coerceAtMost(6) * 45),
            easing = Motion.EaseOut,
        ),
        label = "deal",
    )
    if (reduced) return this
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 34f
        // Each card starts a little further round than the last, so the row
        // opens from the left like a hand rather than rising as a block.
        rotationZ = (1f - progress) * (2.5f + index.coerceAtMost(6) * 0.6f)
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 1f)
    }
}
