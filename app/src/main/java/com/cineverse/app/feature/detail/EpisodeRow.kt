package com.cineverse.app.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.Episode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One episode.
 *
 * This is the most-used row in the app, so everything about it is sized for a
 * thumb rather than a cursor: the row is 92dp tall, the tick is a 48dp target at
 * the RIGHT edge where a thumb naturally falls on a large phone, and the whole
 * row is swipeable.
 *
 *  - **Tap the tick** to mark it watched. The circle fills with a spring and the
 *    check draws itself along its own path, which is the website's `tick-draw`
 *    and is already the right animation.
 *  - **Swipe right** to mark everything up to and including this episode. The
 *    row tints green and a count follows your thumb, so you can see what you are
 *    about to do before you commit, and let go early to cancel.
 *  - **Swipe left** to un-tick.
 *
 * Both swipes are rubber-banded past their trigger point so the gesture always
 * feels like it has an end, and both are reversible mid-drag.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    watched: Boolean,
    isNext: Boolean,
    watchedAt: Long,
    spoilerShield: Boolean,
    onToggle: () -> Unit,
    onMarkUpTo: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    val triggerPx = with(density) { 86.dp.toPx() }
    var armed = remember { false }

    Box(modifier.fillMaxWidth()) {
        // What the swipe is going to do, revealed underneath.
        val progress = (abs(offset.value) / triggerPx).coerceIn(0f, 1f)
        if (offset.value != 0f) {
            val forward = offset.value > 0
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CvShape.Large)
                    .background(
                        (if (forward) colors.green else Palette.Red2).copy(alpha = 0.16f * progress)
                    ),
                contentAlignment = if (forward) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    Modifier.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (forward) Icons.Rounded.DoneAll else Icons.Rounded.Undo,
                        contentDescription = null,
                        tint = if (forward) colors.green else Palette.Red2,
                        modifier = Modifier.size(20.dp).scale(0.6f + progress * 0.4f),
                    )
                    Text(
                        if (forward) "Mark up to E${episode.number}" else "Un-tick",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (forward) colors.green else Palette.Red2,
                    )
                }
            }
        }

        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(CvShape.Large)
                .background(if (isNext) colors.glass else Color.Transparent)
                .clickableNoRipple(onOpen)
                .pointerInput(episode.id, watched) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            scope.launch {
                                offset.animateTo(0f, Motion.snappy())
                            }
                            when {
                                value >= triggerPx -> { haptics?.play(Haptic.Success); onMarkUpTo() }
                                value <= -triggerPx && watched -> { haptics?.play(Haptic.Untick); onToggle() }
                            }
                            armed = false
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f, Motion.snappy()) }
                        },
                    ) { _, delta ->
                        scope.launch {
                            val next = offset.value + delta
                            // Past the trigger the row resists, so the gesture
                            // always has a floor you can feel.
                            val damped = if (abs(next) > triggerPx) {
                                val over = abs(next) - triggerPx
                                (triggerPx + over * 0.35f) * (if (next < 0) -1f else 1f)
                            } else next
                            offset.snapTo(damped.coerceIn(-triggerPx * 1.6f, triggerPx * 1.6f))
                            val nowArmed = abs(offset.value) >= triggerPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                haptics?.play(if (nowArmed) Haptic.Land else Haptic.Detent)
                            }
                        }
                    }
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(124.dp)
                    .height(70.dp)
                    .clip(CvShape.Medium)
                    .background(colors.surface2)
            ) {
                // A still from an episode you have not seen gives it away, so
                // the spoiler shield blurs it until it is watched. An episode
                // that has not aired has nothing to spoil.
                val hide = spoilerShield && !watched && episode.hasAired
                CvImage(
                    Img.still(episode.stillPath),
                    episode.name,
                    Modifier
                        .fillMaxSize()
                        .then(if (hide) Modifier.blur(14.dp) else Modifier),
                )
                if (!episode.hasAired) {
                    // Not "unwatched" — UNAIRED. The difference matters: an
                    // episode that does not exist yet was being drawn exactly
                    // like one you had skipped, so a show you were completely
                    // caught up on looked like it had four outstanding.
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(CvShape.Pill)
                            .background(Color(0xB306060B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "UPCOMING",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cyan,
                        )
                    }
                } else if (isNext) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(CvShape.Pill)
                            .background(Palette.Red2)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                if (episode.rated) {
                    Text(
                        String.format("★ %.1f", episode.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(CvShape.Pill)
                            .background(Color(0xB306060B))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    buildString {
                        append("E").append(episode.number)
                        // A placeholder row has no name worth printing, and
                        // "Episode 10" twice over reads as a rendering fault.
                        if (episode.name.isNotBlank() && !episode.isPlaceholder) {
                            append("  ").append(episode.name)
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        !episode.hasAired -> colors.text3
                        watched -> colors.text2
                        else -> colors.text
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        if (!episode.hasAired) {
                            append(
                                if (episode.airDate.isBlank()) "Date not announced"
                                else "Airs ${episode.airDate}"
                            )
                            return@buildString
                        }
                        if (episode.runtime > 0) append("${episode.runtime}m")
                        if (episode.airDate.isNotBlank()) {
                            if (isNotEmpty()) append("  ·  ")
                            append(episode.airDate)
                        }
                        if (watchedAt > 0) {
                            if (isNotEmpty()) append("  ·  ")
                            append("Watched ${relativeDay(watchedAt)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 1,
                )
                if (episode.hasAired && (!spoilerShield || watched)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        episode.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // No tick on an unaired episode. Offering one invites a mark that
            // the website would immediately disagree with, and there is nothing
            // honest to record about an episode nobody can have watched.
            if (episode.hasAired) {
                TickButton(watched = watched, onToggle = onToggle)
            } else {
                Spacer(Modifier.width(46.dp))
            }
        }
    }
}

/**
 * The tick.
 *
 * 48dp of target around a 28dp circle, because the thing a user does most should
 * be the easiest thing in the app to hit. The check draws itself along its path
 * rather than fading in — a line being written, not an icon appearing.
 */
@Composable
fun TickButton(
    watched: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val reduced = CvTheme.reducedMotion
    val fill by animateFloatAsState(
        targetValue = if (watched) 1f else 0f,
        animationSpec = if (reduced) Motion.fade(Motion.Instant) else Motion.lively(),
        label = "fill",
    )
    val draw by animateFloatAsState(
        targetValue = if (watched) 1f else 0f,
        animationSpec = if (reduced) Motion.fade(Motion.Instant)
        else androidx.compose.animation.core.tween(360, delayMillis = 60, easing = Motion.EaseOut),
        label = "draw",
    )

    Box(
        modifier
            .size(48.dp)
            .clickableNoRipple {
                haptics?.play(if (watched) Haptic.Untick else Haptic.Tick)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.green.copy(alpha = fill))
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    drawCircle(
                        color = if (watched) Color.Transparent else colors.text3,
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(width = stroke),
                    )
                }
        ) {
            if (draw > 0.01f) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val start = Offset(w * 0.24f, w * 0.52f)
                    val mid = Offset(w * 0.42f, w * 0.70f)
                    val end = Offset(w * 0.77f, w * 0.33f)
                    // Two segments, drawn in order, so the check is written
                    // down-then-up the way a hand would.
                    val firstLength = 0.42f
                    val tint = Color(0xFF04241A)
                    if (draw <= firstLength) {
                        val t = draw / firstLength
                        drawLine(tint, start, lerp(start, mid, t), 3.dp.toPx(), StrokeCap.Round)
                    } else {
                        drawLine(tint, start, mid, 3.dp.toPx(), StrokeCap.Round)
                        val t = ((draw - firstLength) / (1f - firstLength)).coerceIn(0f, 1f)
                        drawLine(tint, mid, lerp(mid, end, t), 3.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** "today", "yesterday", or a date — the way a person would say it. */
fun relativeDay(millis: Long): String {
    if (millis <= 0) return ""
    val days = ((System.currentTimeMillis() - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7} week${if (days / 7 == 1) "" else "s"} ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
