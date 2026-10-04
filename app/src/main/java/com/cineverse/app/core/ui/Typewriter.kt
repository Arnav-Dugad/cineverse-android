package com.cineverse.app.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.cineverse.app.core.design.CvTheme

/**
 * Gemini's words appearing as they arrive. The text grows as the answer
 * streams in, and this reveals it at a reading pace - about seventy
 * characters a second, faster when it falls behind a burst, so it never
 * lags far behind what has actually been written. A soft caret trails the
 * last letter while [writing]. Text that was already complete when it first
 * appeared (an earlier answer, scrolled back to) is shown whole, at once.
 */
@Composable
fun TypewriterText(
    text: String,
    writing: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduced = CvTheme.reducedMotion
    val complete = remember { !writing }
    var shown by remember { mutableIntStateOf(if (complete || reduced) text.length else 0) }
    val target by rememberUpdatedState(text.length)
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        var last = 0L
        var carry = 0f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val seconds = (now - last) / 1_000_000_000f
                    val behind = target - shown
                    // Seventy a second, up to four times that when far behind.
                    val rate = 70f * (1f + (behind / 120f).coerceIn(0f, 3f))
                    carry += rate * seconds
                    val step = carry.toInt()
                    if (step > 0) {
                        carry -= step
                        shown = (shown + step).coerceAtMost(target)
                    }
                }
                last = now
            }
        }
    }
    val visible = text.take(shown.coerceAtMost(text.length))
    val caret = writing || shown < text.length
    Text(
        buildAnnotatedString {
            append(visible)
            if (caret) withStyle(SpanStyle(color = color.copy(alpha = 0.55f))) { append(" ▍") }
        },
        style = style,
        color = color,
        modifier = modifier,
    )
}
