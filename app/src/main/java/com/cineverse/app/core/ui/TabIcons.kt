package com.cineverse.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme

/**
 * The tabs, drawn.
 *
 * The rest of the icon set is path-drawn and spring-driven; the navigation bar
 * was still Material glyphs, and beside the drawn set they read a shade heavier
 * and a shade colder — two icon languages in the one place every screen shows.
 *
 * All five share ONE transition, and the shared transition is the point: the
 * shape fills from the bottom up as it becomes selected, like level rising in a
 * glass. A tab bar where each icon has its own clever idea is a tab bar you
 * notice; one where all five do the same thing at the same speed is one that
 * feels built.
 *
 * Two of them earn a second gesture on top of it, because their subject gives
 * them one for free: the compass needle SETTLES into north as it fills, and the
 * chart bars GROW to full height. Neither is decoration — a compass that swings
 * and bars that rise are what those two objects actually do.
 *
 * Everything collapses to its end state under reduced motion.
 */
enum class TabGlyph { Home, Film, Tv, Discover, Search, List, Stats, Person }

private const val GRID = 24f

@Composable
fun TabIcon(
    glyph: TabGlyph,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val reduced = CvTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        // A touch softer than the action icons. A tab is pressed in passing, so
        // its icon should settle rather than snap to attention.
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "tab",
    )
    val fill = if (reduced) (if (selected) 1f else 0f) else animated

    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / GRID
        val stroke = unit * 1.9f
        when (glyph) {
            TabGlyph.Home -> {
                val path = housePath(unit)
                fillUp(path, fill, color)
                outline(path, color, stroke)
            }
            TabGlyph.Film -> {
                // A clapperboard: the stick is open while the tab is idle and
                // claps shut as it fills.
                val body = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(3.6f * unit, 10.2f * unit, 20.4f * unit, 19.6f * unit, 1.6f * unit, 1.6f * unit))
                }
                fillUp(body, fill, color)
                outline(body, color, stroke)
                rotate(degrees = -24f * (1f - fill), pivot = Offset(3.8f * unit, 8.6f * unit)) {
                    val stick = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(3.6f * unit, 5.4f * unit, 20.4f * unit, 8.6f * unit, 1.2f * unit, 1.2f * unit))
                    }
                    fillUp(stick, fill, color)
                    outline(stick, color, stroke)
                }
            }
            TabGlyph.Tv -> {
                val screen = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(3.0f * unit, 7.6f * unit, 21.0f * unit, 18.6f * unit, 2.2f * unit, 2.2f * unit))
                }
                fillUp(screen, fill, color)
                outline(screen, color, stroke)
                drawLine(color, Offset(8.6f * unit, 21.0f * unit), Offset(15.4f * unit, 21.0f * unit), stroke, StrokeCap.Round)
                // The antennas tilt out of true while idle and settle upright.
                rotate(degrees = 14f * (1f - fill), pivot = Offset(12f * unit, 7.4f * unit)) {
                    drawLine(color, Offset(12f * unit, 7.4f * unit), Offset(8.4f * unit, 3.0f * unit), stroke, StrokeCap.Round)
                    drawLine(color, Offset(12f * unit, 7.4f * unit), Offset(15.6f * unit, 3.0f * unit), stroke, StrokeCap.Round)
                }
            }
            TabGlyph.Search -> {
                // A lens and a handle; the handle grows out as it becomes yours.
                drawCircle(color, 6.6f * unit, Offset(10.6f * unit, 10.6f * unit), style = Stroke(stroke * 1.15f))
                val reach = 4.2f + 1.6f * fill
                drawLine(color, Offset(15.4f * unit, 15.4f * unit), Offset((15.4f + reach * 0.72f) * unit, (15.4f + reach * 0.72f) * unit), stroke * 1.3f, StrokeCap.Round)
            }
            TabGlyph.Discover -> {
                val ring = Path().apply {
                    addOval(Rect(Offset(12f * unit, 12f * unit), 8.7f * unit))
                }
                outline(ring, color, stroke)
                // The needle is 40 degrees out of true when the tab is not the
                // one you are on, and swings to north as it becomes it.
                rotate(degrees = -40f * (1f - fill), pivot = center) {
                    val needle = needlePath(unit)
                    fillUp(needle, fill, color)
                    outline(needle, color, stroke * 0.8f)
                }
            }
            TabGlyph.List -> {
                // The card behind, as one stroke. A second full bookmark would
                // make the icon a pile; a single line says there are more.
                drawLine(
                    color = color,
                    start = Offset(5.0f * unit, 7.6f * unit),
                    end = Offset(5.0f * unit, 16.8f * unit),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                val mark = bookmarkPath(unit)
                fillUp(mark, fill, color)
                outline(mark, color, stroke)
            }
            TabGlyph.Stats -> {
                // Three bars at a chart's proportions, each at 44% when the tab
                // is idle and full height when it is yours.
                val base = 19.4f * unit
                val tops = floatArrayOf(8.2f, 4.2f, 11.0f)
                val xs = floatArrayOf(6.2f, 12f, 17.8f)
                for (i in 0..2) {
                    val full = base - tops[i] * unit
                    val top = base - full * (0.58f + 0.42f * fill)
                    drawLine(
                        color = color,
                        start = Offset(xs[i] * unit, base),
                        end = Offset(xs[i] * unit, top),
                        strokeWidth = stroke * 1.35f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            TabGlyph.Person -> {
                val head = Path().apply {
                    addOval(Rect(Offset(12f * unit, 8.4f * unit), 3.9f * unit))
                }
                val shoulders = shouldersPath(unit)
                fillUp(head, fill, color)
                fillUp(shoulders, fill, color)
                outline(head, color, stroke)
                outline(shoulders, color, stroke)
            }
        }
    }
}

/** The shared transition: the shape fills from its foot upward. */
private fun DrawScope.fillUp(path: Path, progress: Float, color: Color) {
    if (progress <= 0.004f) return
    clipRect(top = size.height * (1f - progress)) {
        drawPath(path, color = color)
    }
}

private fun DrawScope.outline(path: Path, color: Color, width: Float) {
    drawPath(
        path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun housePath(unit: Float) = Path().apply {
    moveTo(3.5f * unit, 10.7f * unit)
    lineTo(10.9f * unit, 4.1f * unit)
    // The ridge is rounded rather than a point, which is what stops the roof
    // reading as sharper than every other corner in the set.
    quadraticTo(12f * unit, 3.2f * unit, 13.1f * unit, 4.1f * unit)
    lineTo(20.5f * unit, 10.7f * unit)
    lineTo(20.5f * unit, 19.4f * unit)
    lineTo(3.5f * unit, 19.4f * unit)
    close()
}

private fun needlePath(unit: Float) = Path().apply {
    moveTo(16.7f * unit, 7.3f * unit)
    lineTo(14.2f * unit, 14.2f * unit)
    lineTo(7.3f * unit, 16.7f * unit)
    lineTo(9.8f * unit, 9.8f * unit)
    close()
}

private fun bookmarkPath(unit: Float) = Path().apply {
    moveTo(8.7f * unit, 5.2f * unit)
    quadraticTo(8.7f * unit, 4.4f * unit, 9.5f * unit, 4.4f * unit)
    lineTo(15.4f * unit, 4.4f * unit)
    quadraticTo(16.2f * unit, 4.4f * unit, 16.2f * unit, 5.2f * unit)
    lineTo(16.2f * unit, 20.0f * unit)
    // A deep notch. At 24dp a shallow one disappears the moment the shape
    // fills, and a filled bookmark without a notch is a rectangle.
    lineTo(12.45f * unit, 16.0f * unit)
    lineTo(8.7f * unit, 20.0f * unit)
    close()
}

private fun shouldersPath(unit: Float) = Path().apply {
    moveTo(5.1f * unit, 20.2f * unit)
    cubicTo(
        5.1f * unit, 16.3f * unit,
        8.2f * unit, 14.4f * unit,
        12f * unit, 14.4f * unit,
    )
    cubicTo(
        15.8f * unit, 14.4f * unit,
        18.9f * unit, 16.3f * unit,
        18.9f * unit, 20.2f * unit,
    )
    close()
}
