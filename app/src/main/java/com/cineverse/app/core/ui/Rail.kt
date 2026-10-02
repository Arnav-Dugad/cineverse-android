package com.cineverse.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Motion
import com.cineverse.app.data.model.MediaItem

/** The gutter every screen's content lines up to. */
val ScreenPadding = 18.dp

/**
 * How much room the bottom bar needs under a scrolling list.
 *
 * 44dp of fade, 80dp of bar and the gesture inset under that. The first guess
 * was 120dp, which left the last row's captions tucked behind the labels — a
 * list that cannot be scrolled to its own end is a list with a bug in it.
 */
val BottomBarSpace = 156.dp

/**
 * A rail's heading.
 *
 * A kicker above it when there is something to say about where the row came from
 * ("Because you watched Severance"), and a "See all" only when there is more
 * behind it than the rail can hold — an affordance that leads nowhere is worse
 * than none.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    count: Int? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val colors = CvTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (kicker != null) {
                Text(
                    kicker.uppercase(),
                    style = KickerStyle,
                    color = colors.text3,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
                if (count != null && count > 0) {
                    Text(
                        "  $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                }
            }
        }
        if (onSeeAll != null) {
            Row(
                Modifier
                    .clip(com.cineverse.app.core.design.CvShape.Pill)
                    .clickableNoRipple { onSeeAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("See all", style = MaterialTheme.typography.labelMedium, color = colors.text2)
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * A horizontal row of posters.
 *
 * The one piece of motion here is the entrance: on a rail's first appearance the
 * cards rise and fade in 40 ms apart, left to right, which reads as the row
 * dealing itself out. It happens once — scrolling back to a rail you have
 * already seen must not replay it, because then it is decoration rather than an
 * arrival.
 */
@Composable
fun PosterRail(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    kicker: String? = null,
    onSeeAll: (() -> Unit)? = null,
    cardWidth: Dp = 132.dp,
    showCaption: Boolean = true,
    isWatched: (MediaItem) -> Boolean = { false },
    isSaved: (MediaItem) -> Boolean = { false },
    ratingOf: (MediaItem) -> Int = { 0 },
    matchOf: (MediaItem) -> Int = { 0 },
    onLongPress: ((MediaItem) -> Unit)? = null,
    loading: Boolean = false,
) {
    if (!loading && items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SectionHeader(title, kicker = kicker, onSeeAll = onSeeAll)
            Spacer(Modifier.height(12.dp))
        }
        if (loading) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(6) { PosterSkeleton(width = cardWidth, showCaption = showCaption) }
            }
        } else {
            var revealed by remember(items.firstOrNull()?.key) { mutableStateOf(false) }
            LaunchedEffect(items.firstOrNull()?.key) { revealed = true }
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                    val reduced = CvTheme.reducedMotion
                    val appear by animateFloatAsState(
                        targetValue = if (revealed || reduced) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = Motion.Slow,
                            // Only the first screenful is staggered: a card ten
                            // places along would otherwise wait half a second to
                            // appear when you flick straight to it.
                            delayMillis = (index.coerceAtMost(5) * Motion.RailStaggerMs).toInt(),
                            easing = Motion.EaseOut,
                        ),
                        label = "appear",
                    )
                    PosterCard(
                        item = item,
                        onOpen = onOpen,
                        width = cardWidth,
                        showCaption = showCaption,
                        watched = isWatched(item),
                        saved = isSaved(item),
                        rating = ratingOf(item),
                        matchPercent = matchOf(item),
                        onLongPress = onLongPress,
                        modifier = Modifier.graphicsLayer {
                            alpha = appear
                            translationY = (1f - appear) * 26f
                        },
                    )
                }
            }
        }
    }
}

/** A tap with no ripple — used where the whole surface already responds. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
