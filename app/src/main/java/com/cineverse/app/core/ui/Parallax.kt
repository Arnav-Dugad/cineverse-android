package com.cineverse.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.cineverse.app.core.design.CvTheme
import kotlin.math.absoluteValue

/**
 * A card that knows where it is in its row.
 *
 * Posters in a rail are usually identical no matter where they sit, which means
 * a row of them is a flat strip. Giving each card a touch of depth based on its
 * distance from the centre of the viewport makes the row read as something you
 * are looking ALONG rather than at: the card under your thumb is the one in
 * focus, and the ones running off the edge recede.
 *
 * Deliberately tiny. 4% of scale and 12% of brightness at the extremes; any
 * more and it becomes a carousel effect, which is a different and much louder
 * idea. The point is that you notice the row feels good, not that you notice
 * the effect.
 *
 * Costs one layer per card and nothing per frame beyond a multiply, because it
 * reads the position in the layout pass rather than recomposing on scroll.
 */
fun Modifier.railDepth(): Modifier = composed {
    if (CvTheme.reducedMotion) return@composed this
    val centreOffset = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableFloatStateOf(0f)
    }
    this
        .onGloballyPositioned { coordinates ->
            val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
            val width = parent.size.width.toFloat()
            if (width <= 0f) return@onGloballyPositioned
            val cardCentre = coordinates.positionInParent().x + coordinates.size.width / 2f
            // -1 at the left edge, 0 in the middle, +1 at the right.
            centreOffset.floatValue =
                ((cardCentre - width / 2f) / (width / 2f)).coerceIn(-1.4f, 1.4f)
        }
        .graphicsLayer {
            val distance = centreOffset.floatValue.absoluteValue.coerceAtMost(1f)
            val shrink = 1f - 0.04f * distance
            scaleX = shrink
            scaleY = shrink
            alpha = 1f - 0.12f * distance
        }
}

/**
 * How far a row has travelled up the page, 0 to 1.
 *
 * Used to drift a backdrop against the content in front of it. Reads the list's
 * own layout info rather than tracking offsets by hand, so it stays correct
 * through a fling, a jump to top, and a configuration change.
 */
@Composable
fun rememberScrollFraction(listState: LazyListState, over: Int = 2): Float {
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    val span = (info.viewportEndOffset - info.viewportStartOffset).toFloat() * over
    if (span <= 0f) return 0f
    val travelled = first.index * first.size.toFloat() - first.offset
    return (travelled / span).coerceIn(0f, 1f)
}
