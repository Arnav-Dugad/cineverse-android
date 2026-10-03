package com.cineverse.app.feature.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.clickableNoRipple
import kotlin.math.roundToInt

/**
 * Where you stopped in a film.
 *
 * CineVerse does not play anything, so a film has no automatic progress the way
 * a series does — but people do pause a film at 50 minutes and come back three
 * days later, and the website records that. The phone version is a scrub bar
 * you drag with a thumb, with a detent every five minutes so landing on a round
 * number is easy and landing between two is still possible.
 *
 * The chapter ticks are not real chapters — TMDB does not have them. They are
 * quarter marks, and they are labelled as such, because a tick that looks like
 * data and is not is worse than no tick.
 */
@Composable
fun ProgressSheet(
    title: String,
    runtime: Int,
    current: Int,
    onSave: (Int) -> Unit,
    onFinish: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val total = runtime.coerceAtLeast(1)
    var minutes by remember(current) { mutableIntStateOf(current.coerceIn(0, total)) }
    var barWidth by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    fun seek(x: Float, detent: Boolean) {
        if (barWidth <= 0) return
        val raw = (x / barWidth).coerceIn(0f, 1f) * total
        // Snap to five minutes unless the film is short enough that five minutes
        // is a meaningful fraction of it, where snapping would be a blunt tool.
        val step = if (total >= 60) 5 else 1
        val next = ((raw / step).roundToInt() * step).coerceIn(0, total)
        if (next != minutes) {
            minutes = next
            if (detent) haptics?.play(if (next == 0 || next == total) Haptic.Edge else Haptic.Detent)
        }
    }

    val fraction = minutes.toFloat() / total
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (dragging) Motion.snappy() else Motion.landing(),
        label = "scrub",
    )

    CvSheet(onDismiss = onDismiss) {
        Text("WHERE YOU STOPPED", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 2)

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                clock(minutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Text(
                "  of ${clock(total)}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text3,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                minutes <= 0 -> "Not started"
                minutes >= total -> "Finished"
                else -> "${clock(total - minutes)} left — ${(fraction * 100).roundToInt()}% through"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
        )

        Spacer(Modifier.height(22.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onSizeChanged { barWidth = it.width }
                .pointerInput(barWidth) {
                    detectTapGestures { offset -> seek(offset.x, detent = true) }
                }
                .pointerInput(barWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragging = true; seek(offset.x, detent = true) },
                        onDragEnd = { dragging = false; haptics?.play(Haptic.Land) },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> seek(change.position.x, detent = true) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // The track.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.12f))
            )
            // The quarter marks, drawn on the track so they read as a scale.
            Row(Modifier.fillMaxWidth().height(10.dp)) {
                for (quarter in 1..3) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(colors.ink.copy(alpha = 0.55f))
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            // What has been watched.
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.001f))
                    .height(10.dp)
                    .clip(CvShape.Pill)
                    .background(
                        Brush.horizontalGradient(listOf(Palette.Red, Palette.Red2))
                    )
            )
            // The thumb. It grows while held, which is the only feedback a
            // finger that is covering the thumb can actually see.
            val thumbSize by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (dragging) 26.dp else 20.dp,
                animationSpec = Motion.lively(),
                label = "thumb",
            )
            val trackPx = barWidth - with(density) { thumbSize.toPx() }
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((trackPx * animated).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Palette.Red, CircleShape)
            )
        }

        Spacer(Modifier.height(18.dp))

        // Quick jumps, because dragging to "half way" is slower than tapping it.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in listOf(
                "Start" to 0,
                "¼" to total / 4,
                "Half" to total / 2,
                "¾" to total * 3 / 4,
            )) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(CvShape.Pill)
                        .background(colors.glass)
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Select); minutes = value },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.text2)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current > 0) {
                Box(
                    Modifier
                        .height(50.dp)
                        .clip(CvShape.Pill)
                        .background(colors.glass)
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Untick); onClear(); onDismiss() }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                }
            }
            Button(
                onClick = {
                    // Dragging to the end means finished, and finished means
                    // watched — not a film sitting at 100% in Continue Watching
                    // forever, which is the state the website had to special-case.
                    haptics?.play(Haptic.Success)
                    if (minutes >= total) onFinish() else onSave(minutes)
                    onDismiss()
                },
                shape = CvShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.text,
                    contentColor = colors.ink,
                ),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text(
                    if (minutes >= total) "Mark watched" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun clock(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
}
