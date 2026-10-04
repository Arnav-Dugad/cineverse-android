package com.cineverse.app.feature.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.AnimatedBookmark
import com.cineverse.app.core.ui.AnimatedCheck
import com.cineverse.app.core.ui.AnimatedStar
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores

/**
 * A look at a title without leaving the row you are in.
 *
 * The website previews a card on hover; a phone has no hover, so it gets a long
 * press — the gesture that already means "tell me more about this" everywhere
 * else on the platform.
 *
 * The point of a peek is that it is CHEAPER than opening the page, so it says
 * only the things that decide whether you want the title: the artwork, the
 * scores, four lines of what it is about, and the three actions that are the
 * reason most people open a title page at all. Cast, episodes and providers
 * stay behind "Open", because a peek that shows everything is just a page with
 * a worse layout.
 *
 * It opens with whatever is already cached and fills in when the detail
 * arrives, so the sheet is never waiting on a request before it can be useful.
 */
@Composable
fun PeekSheet(
    item: MediaItem,
    detail: TitleDetail?,
    scores: Scores,
    saved: Boolean,
    watched: Boolean,
    rating: Int,
    trailerKey: String? = null,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onWatched: () -> Unit,
    onRate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    CvSheet(onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CvShape.Large)
        ) {
            CvImage(
                Img.backdrop(detail?.backdropPath ?: item.backdropPath ?: item.posterPath),
                item.title,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // The trailer, over the artwork once it is genuinely playing: the
            // still paints first and the video fades in on top, never a black
            // frame while it loads.
            if (trailerKey != null) {
                var playing by androidx.compose.runtime.remember(trailerKey) { androidx.compose.runtime.mutableStateOf(false) }
                val shown by androidx.compose.animation.core.animateFloatAsState(
                    if (playing) 1f else 0f,
                    androidx.compose.animation.core.tween(700),
                    label = "peekTrailer",
                )
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
                    com.cineverse.app.feature.trailer.YouTubePlayer(
                        videoKey = trailerKey,
                        modifier = Modifier.fillMaxSize(),
                        muted = true,
                        showControls = false,
                        loop = true,
                        ambient = true,
                        onPlaying = { playing = true },
                    )
                }
            }
            // The artwork carries the name, so the sheet does not spend a line
            // on it. The ramp is what makes the type legible over anything.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color(0xE606060B),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    item.year.takeIf { it.isNotBlank() },
                    detail?.certificate?.takeIf { it.isNotBlank() },
                    runtimeOf(detail),
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xCCFFFFFF),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (scores.any || item.voteAverage > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.voteAverage > 0) {
                    Box(
                        Modifier
                            .glass(CvShape.Pill, raised = false)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            String.format("★ %.1f", item.voteAverage),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.gold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                ScoreRow(scores)
            }
            Spacer(Modifier.height(14.dp))
        }

        val overview = detail?.overview.orEmpty()
        if (overview.isNotBlank()) {
            Text(
                overview,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text2,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CvShape.Pill)
                    .background(Brush.horizontalGradient(listOf(Palette.Red, Palette.Red2)))
                    .clickableNoRipple { haptics?.play(Haptic.Tap); onOpen() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.OpenInFull,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text("Open", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }

            PeekCircle(
                active = saved,
                description = if (saved) "In your list" else "Save",
                onClick = { haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave() },
            ) { tint -> AnimatedBookmark(saved, tint, size = 20.dp) }

            PeekCircle(
                active = watched,
                activeTint = colors.green,
                description = if (watched) "Watched" else "Mark watched",
                onClick = { haptics?.play(if (watched) Haptic.Untick else Haptic.Tick); onWatched() },
            ) { tint -> AnimatedCheck(watched, tint, size = 20.dp) }

            PeekCircle(
                active = rating > 0,
                activeTint = colors.gold,
                label = rating.takeIf { it > 0 }?.toString(),
                description = "Rate",
                onClick = { haptics?.play(Haptic.Tap); onRate() },
            ) { tint -> AnimatedStar(rating > 0, tint, size = 20.dp) }
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PeekCircle(
    active: Boolean,
    description: String,
    onClick: () -> Unit,
    activeTint: Color = Color.Unspecified,
    label: String? = null,
    icon: @Composable (Color) -> Unit,
) {
    val colors = CvTheme.colors
    val tint = when {
        !active -> colors.text
        activeTint != Color.Unspecified -> activeTint
        else -> Palette.Red2
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = Motion.snappy(),
        label = "peekAction",
    )
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.15f) else colors.glass)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        } else {
            icon(tint)
        }
    }
}

private fun runtimeOf(detail: TitleDetail?): String? = when {
    detail == null -> null
    detail.isSeries && detail.numberOfSeasons > 0 ->
        "${detail.numberOfSeasons} season${if (detail.numberOfSeasons == 1) "" else "s"}"
    detail.runtime > 0 -> "${detail.runtime / 60}h ${detail.runtime % 60}m"
    else -> null
}
