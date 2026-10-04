package com.cineverse.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.tabular
import kotlin.math.floor

/**
 * Digits that roll up like an odometer - the website's `odometerHTML`. Each
 * column spins through a full turn of 0 to 9 and stops on its digit, the
 * leftmost first, and a light tick lands under the thumb as each one settles.
 * A changed figure rolls on from where it was rather than starting again.
 */
@Composable
fun OdometerText(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val text = "%,d".format(value.coerceAtLeast(0))
    val digits = text.count { it.isDigit() }
    Row(modifier) {
        var seen = 0
        text.forEachIndexed { index, char ->
            if (!char.isDigit()) {
                Text(char.toString(), style = style.tabular(), color = color)
            } else {
                // Keyed from the right, so 99 becoming 100 adds a column on the
                // left instead of reshuffling every column it already had.
                val fromRight = digits - 1 - seen
                key(fromRight) {
                    OdometerDigit(char - '0', delayMillis = seen * 90, style = style, color = color)
                }
                seen++
            }
        }
    }
}

@Composable
private fun OdometerDigit(digit: Int, delayMillis: Int, style: TextStyle, color: Color) {
    val reduced = CvTheme.reducedMotion
    val haptics = LocalHaptics.current
    val turn = remember { Animatable(if (reduced) 10f + digit else 0f) }
    LaunchedEffect(digit, reduced) {
        val target = 10f + digit
        if (reduced) {
            turn.snapTo(target)
        } else if (turn.value != target) {
            turn.animateTo(target, tween(820, delayMillis, FastOutSlowInEasing))
            haptics?.play(Haptic.Tick)
        }
    }
    val tabular = style.tabular()
    val position = turn.value
    val base = floor(position).toInt()
    val fraction = position - base
    Box(Modifier.clipToBounds()) {
        // Sizes the column; every figure is the same width in tabular type.
        Text("0", style = tabular, color = Color.Transparent)
        Text(
            "${base % 10}",
            style = tabular,
            color = color,
            modifier = Modifier.graphicsLayer { translationY = -fraction * size.height },
        )
        Text(
            "${(base + 1) % 10}",
            style = tabular,
            color = color,
            modifier = Modifier.graphicsLayer { translationY = (1f - fraction) * size.height },
        )
    }
}
