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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme

/**
 * Icons that move.
 *
 * The equivalent of an animated SVG, drawn as paths on a Canvas rather than
 * shipped as vector assets, for one reason that matters: an
 * `AnimatedVectorDrawable` can only play a fixed timeline between two baked
 * states, while these are driven by a spring from whatever value they are at
 * now. Tapping a half-drawn tick carries on from half-drawn instead of
 * snapping back to the start, which is the difference between an animation and
 * a transition.
 *
 * Every one of them:
 *
 *  - is a STROKE of the same weight at the same size, so they sit together in
 *    a row without one looking heavier than the rest;
 *  - animates on its own state, never on a clock, so a screen full of them is
 *    still when nothing is happening;
 *  - collapses to its end state instantly under reduced motion.
 */
private const val VIEWPORT = 24f

@Composable
private fun iconProgress(target: Boolean, stiffness: Float = Spring.StiffnessMediumLow): Float {
    val reduced = CvTheme.reducedMotion
    val value by animateFloatAsState(
        targetValue = if (target) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = stiffness),
        label = "icon",
    )
    return if (reduced) (if (target) 1f else 0f) else value
}

private fun DrawScope.strokeWidthFor(size: Dp) = (size.toPx() / VIEWPORT) * 2.1f

/** Draws [path] from nothing to whole as [progress] runs 0 to 1. */
private fun DrawScope.drawTrace(path: Path, progress: Float, color: Color, width: Float) {
    if (progress <= 0.001f) return
    val measure = PathMeasure()
    measure.setPath(path, false)
    val drawn = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)
    drawPath(
        drawn,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * The tick.
 *
 * It DRAWS ITSELF, left stroke then right, which is the single most satisfying
 * micro-animation in the app and the one the user sees most: every episode,
 * every title marked watched. Un-ticking erases it the same way in reverse,
 * because a tick that vanishes has been cancelled and a tick that un-draws has
 * been undone.
 */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(checked, Spring.StiffnessMedium)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val path = Path().apply {
            moveTo(5f * unit, 12.5f * unit)
            lineTo(10f * unit, 17.5f * unit)
            lineTo(19f * unit, 6.5f * unit)
        }
        val stroke = strokeWidthFor(size)
        // A GHOST of the finished shape underneath, so the control is visible
        // when it is off. Without it an un-ticked button is an empty circle,
        // which on the peek sheet read as a rendering fault rather than as a
        // thing to press. It fades out as the real stroke draws over it.
        if (progress < 0.999f) {
            drawPath(
                path,
                color = color.copy(alpha = 0.26f * (1f - progress)),
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        drawTrace(path, progress, color, stroke)
    }
}

/**
 * Plus to bookmark.
 *
 * Not a swap: the plus ROTATES as the bookmark fills in beneath it, so the two
 * are visibly the same control in two states rather than two icons sharing a
 * slot.
 */
@Composable
fun AnimatedBookmark(
    saved: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(saved)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val width = strokeWidthFor(size)

        // The plus shrinks out as it turns a quarter.
        if (progress < 0.999f) {
            rotate(degrees = progress * 90f) {
                scale(1f - progress) {
                    drawLine(
                        color.copy(alpha = 1f - progress),
                        Offset(12f * unit, 5f * unit),
                        Offset(12f * unit, 19f * unit),
                        width,
                        StrokeCap.Round,
                    )
                    drawLine(
                        color.copy(alpha = 1f - progress),
                        Offset(5f * unit, 12f * unit),
                        Offset(19f * unit, 12f * unit),
                        width,
                        StrokeCap.Round,
                    )
                }
            }
        }

        if (progress > 0.001f) {
            scale(progress) {
                val bookmark = Path().apply {
                    moveTo(6.5f * unit, 4f * unit)
                    lineTo(17.5f * unit, 4f * unit)
                    lineTo(17.5f * unit, 20f * unit)
                    lineTo(12f * unit, 15.5f * unit)
                    lineTo(6.5f * unit, 20f * unit)
                    close()
                }
                drawPath(bookmark, color.copy(alpha = progress))
            }
        }
    }
}

/**
 * The star, which FILLS rather than swaps.
 *
 * Outline at rest, solid when rated, and the transition is the fill arriving
 * from the middle outward with a half-turn on it. A star that simply becomes a
 * different star reads as a redraw.
 */
@Composable
fun AnimatedStar(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(filled)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val star = starPath(unit)
        drawPath(
            star,
            color = color.copy(alpha = 0.9f),
            style = Stroke(
                width = strokeWidthFor(size),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        if (progress > 0.001f) {
            rotate(degrees = (1f - progress) * 72f) {
                scale(progress) { drawPath(star, color = color) }
            }
        }
    }
}

private fun DrawScope.starPath(unit: Float): Path {
    val centre = Offset(12f * unit, 12.4f * unit)
    val outer = 8.6f * unit
    val inner = 3.9f * unit
    return Path().apply {
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = Math.toRadians((point * 36.0) - 90.0)
            val x = centre.x + (radius * Math.cos(angle)).toFloat()
            val y = centre.y + (radius * Math.sin(angle)).toFloat()
            if (point == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * A chevron that turns.
 *
 * Used by everything that folds. Drawn rather than glyphed so its stroke
 * matches the rest of this set exactly at any size — a Material chevron beside
 * these reads a shade lighter, which on a page of panels is visible.
 */
@Composable
fun AnimatedChevron(
    expanded: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(expanded)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        rotate(degrees = -90f + progress * 90f) {
            val path = Path().apply {
                moveTo(7f * unit, 10f * unit)
                lineTo(12f * unit, 15f * unit)
                lineTo(17f * unit, 10f * unit)
            }
            drawPath(
                path,
                color = color,
                style = Stroke(
                    width = strokeWidthFor(size),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * Play, becoming pause.
 *
 * The triangle's two halves slide apart into the two bars, which is the same
 * trick every video player uses because the shapes genuinely share an outline.
 */
@Composable
fun AnimatedPlayPause(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(playing)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val gap = 1.6f * unit * progress
        val barWidth = 3.2f * unit

        // Left half: a triangle edge at rest, a bar when playing.
        val leftTip = 12f * unit - (12f * unit - (7f * unit + barWidth)) * progress
        drawPath(
            Path().apply {
                moveTo(7f * unit - gap, 5f * unit)
                lineTo(leftTip - gap, 5f * unit + (0f * unit) * (1f - progress))
                lineTo(leftTip - gap, 19f * unit)
                lineTo(7f * unit - gap, 19f * unit)
                close()
            },
            color = color,
        )
        drawPath(
            Path().apply {
                val rightStart = 12f * unit + gap
                moveTo(rightStart, 5f * unit + (3.5f * unit) * (1f - progress))
                lineTo(rightStart + barWidth + (3.5f * unit) * (1f - progress), 12f * unit)
                lineTo(rightStart + barWidth, 19f * unit - (3.5f * unit) * (1f - progress))
                close()
            },
            color = color,
        )
    }
}

/**
 * The search glass, whose handle draws out of the circle.
 *
 * Only animates when the field actually opens, so the top bar is still at rest.
 */
@Composable
fun AnimatedSearch(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(active)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val width = strokeWidthFor(size)
        val radius = 6.2f * unit - 0.6f * unit * progress
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(10.5f * unit, 10.5f * unit),
            style = Stroke(width = width),
        )
        val handle = Path().apply {
            moveTo(15f * unit, 15f * unit)
            lineTo(19.5f * unit, 19.5f * unit)
        }
        drawTrace(handle, 0.55f + 0.45f * progress, color, width)
    }
}

/**
 * A heart that beats once when it fills. Used nowhere yet; here because the
 * set should be complete rather than grown one icon at a time by whoever needs
 * one next, which is how an icon set stops matching itself.
 */
@Composable
fun AnimatedHeart(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val progress = iconProgress(filled, Spring.StiffnessLow)
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / VIEWPORT
        val heart = Path().apply {
            moveTo(12f * unit, 20f * unit)
            cubicTo(4f * unit, 14.5f * unit, 3f * unit, 9f * unit, 6.2f * unit, 6.4f * unit)
            cubicTo(8.6f * unit, 4.4f * unit, 11f * unit, 5.6f * unit, 12f * unit, 7.6f * unit)
            cubicTo(13f * unit, 5.6f * unit, 15.4f * unit, 4.4f * unit, 17.8f * unit, 6.4f * unit)
            cubicTo(21f * unit, 9f * unit, 20f * unit, 14.5f * unit, 12f * unit, 20f * unit)
            close()
        }
        drawPath(
            heart,
            color = color,
            style = Stroke(width = strokeWidthFor(size), join = StrokeJoin.Round),
        )
        if (progress > 0.001f) {
            // A touch past 1 at the peak of the spring, which is the beat.
            scale(progress) { drawPath(heart, color = color) }
        }
    }
}
