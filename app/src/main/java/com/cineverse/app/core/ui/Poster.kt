package com.cineverse.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.Motion
import com.cineverse.app.data.model.MediaItem

/**
 * The card.
 *
 * One definition, drawn identically in a rail, a grid, a search result and a
 * person's filmography — so a poster always behaves the same way wherever it is
 * met. It presses in under the thumb, takes you to the title on a tap, and opens
 * the quick sheet on a long press.
 */
@Composable
fun PosterCard(
    item: MediaItem,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 132.dp,
    showCaption: Boolean = true,
    watched: Boolean = false,
    saved: Boolean = false,
    rating: Int = 0,
    showRating: Boolean = true,
    matchPercent: Int = 0,
    onLongPress: ((MediaItem) -> Unit)? = null,
) {
    val colors = CvTheme.colors
    val style = LocalPosterStyle.current
    val haptics = com.cineverse.app.core.design.LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A press scales the whole card, caption included, so it reads as one object
    // being pushed rather than a picture shrinking inside a frame.
    val scale by animateFloatAsState(
        targetValue = if (pressed && !CvTheme.reducedMotion) 0.955f else 1f,
        animationSpec = Motion.snappy(),
        label = "press",
    )

    Column(
        modifier
            .width(width)
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics?.play(Haptic.Tap)
                    onOpen(item)
                },
                onLongClick = onLongPress?.let {
                    {
                        haptics?.play(Haptic.Peek)
                        it(item)
                    }
                },
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .sharedPoster(item.key)
                .clip(style.corner.shape)
                .background(colors.surface2)
                .border(1.dp, colors.hairline, style.corner.shape)
        ) {
            // Revealed per card, and forgotten when the screen is. Persisting it
            // would mean a setting that silently stops applying.
            var revealed by remember(item.key) { mutableStateOf(false) }
            val covered = style.blurMature && item.adult && !revealed

            CvImage(
                Img.poster(item.posterPath),
                item.title,
                Modifier
                    .fillMaxSize()
                    .then(if (covered) Modifier.blur(26.dp) else Modifier),
            )

            if (covered) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.ink.copy(alpha = 0.45f))
                        .clickableNoRipple { revealed = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Tap to show",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = colors.text2,
                    )
                }
            }

            // A title with no artwork gets its name, not an empty grey box.
            if (!item.hasArt) {
                Text(
                    item.title,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }

            if (showRating && style.rating && item.voteAverage > 0) {
                RatingPill(
                    item.voteAverage,
                    Modifier.align(Alignment.TopEnd).padding(7.dp),
                )
            }

            if (matchPercent > 0 && style.match) {
                MatchPill(matchPercent, Modifier.align(Alignment.TopStart).padding(7.dp))
            }

            // Your own mark sits bottom-left, the site's rating top-right, so the
            // two numbers can never be mistaken for each other.
            if (rating > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .clip(CvShape.Pill)
                        .background(Brush.linearGradient(listOf(colors.gold, Color(0xFFF59E0B))))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "$rating",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3A2A05),
                    )
                }
            }

            // The tick springs in rather than appearing, so marking something
            // watched from a rail is visibly the same gesture as marking it on
            // the title page.
            if (watched && style.watchedMark) {
                val tickScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = Motion.landing(),
                    label = "tick",
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .scale(if (CvTheme.reducedMotion) 1f else tickScale)
                        .size(26.dp)
                        .clip(CvShape.Circle)
                        .background(colors.green),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedCheck(
                        checked = true,
                        color = Color(0xFF04241A),
                        size = 16.dp,
                    )
                }
            }

            if (saved && style.savedMark && !(watched && style.watchedMark)) {
                Icon(
                    Icons.Rounded.Bookmark,
                    contentDescription = "In your list",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .size(20.dp),
                )
            }
        }

        // Captions are a PREFERENCE now, and off by default on a page of
        // artwork: a poster already says what it is, and a name under every one
        // of them turns a wall of pictures into a list with pictures attached.
        if (showCaption && style.captions) {
            Text(
                item.title,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (style.meta) {
                val meta = listOfNotNull(
                    item.year.takeIf { it.isNotBlank() },
                    if (item.type == com.cineverse.app.data.model.MediaType.Tv) "Series" else "Film",
                ).joinToString(" · ")
                Text(
                    meta,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * How a poster is dressed.
 *
 * Every flag here was already a scattered boolean parameter that each screen
 * passed differently, so the same card looked one way on Home and another in
 * Search with no rule behind the difference. One object, provided once from
 * Settings, means a change in Settings reaches every grid and rail at once and
 * the default is written down in exactly one place.
 */
@androidx.compose.runtime.Immutable
data class PosterStyle(
    /** The name under the artwork. */
    val captions: Boolean = false,
    /** The year and whether it is a film, under the name. */
    val meta: Boolean = false,
    /** TMDB's score in the corner. */
    val rating: Boolean = true,
    /** A tick on something already watched. */
    val watchedMark: Boolean = true,
    /** The bookmark on something saved. */
    val savedMark: Boolean = true,
    /** The match percentage on a recommendation. */
    val match: Boolean = true,
    /** How round the corners are. */
    val corner: PosterCorner = PosterCorner.Rounded,
    /**
     * Cover adult artwork until it is tapped.
     *
     * Nothing is hidden and nothing is removed; the poster simply does not
     * appear uninvited on a screen somebody else can see. One tap reveals it,
     * for that card, for as long as the screen is up.
     */
    val blurMature: Boolean = true,
)

enum class PosterCorner(val label: String) {
    Square("Square"), Soft("Soft"), Rounded("Rounded"), Pill("Very round");

    val shape: androidx.compose.foundation.shape.RoundedCornerShape
        get() = when (this) {
            Square -> androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
            Soft -> CvShape.Small
            Rounded -> CvShape.Large
            Pill -> CvShape.XXLarge
        }
}

val LocalPosterStyle = androidx.compose.runtime.staticCompositionLocalOf { PosterStyle() }

/** TMDB's own score, in gold, as it is everywhere in CineVerse. */
@Composable
fun RatingPill(value: Double, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Row(
        modifier
            .clip(CvShape.Pill)
            .background(Color(0xBF06060B))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.Rounded.Star,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(11.dp),
        )
        Text(
            String.format("%.1f", value),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** How well a recommendation fits you, in the site's red. */
@Composable
fun MatchPill(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CvShape.Pill)
            .background(com.cineverse.app.core.design.Palette.Red)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            "$percent% match",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** A poster-shaped placeholder, for a rail that has not arrived. */
@Composable
fun PosterSkeleton(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 132.dp,
    showCaption: Boolean = true,
) {
    Column(modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CvShape.Large)
                .shimmer()
        )
        if (showCaption) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.85f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
    }
}

/** A thin bar under a Continue Watching card. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.height(3.dp).clip(CvShape.Pill),
        color = com.cineverse.app.core.design.Palette.Red2,
        trackColor = colors.glassStrong,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
