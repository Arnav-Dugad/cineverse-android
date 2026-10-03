package com.cineverse.app.feature.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.Confetti
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.clickableNoRipple

/** Index = score - 1. The website words, so a 7 means the same thing in both. */
private val LABELS = listOf(
    "Unwatchable", "Awful", "Bad", "Weak", "Meh",
    "Decent", "Good", "Great", "Superb", "Masterpiece",
)

/**
 * Ten stars, scrubbable.
 *
 * On a phone the interesting part is not the stars, it is the gesture: a finger
 * lands anywhere on the row and slides, and the score follows with a detent tick
 * at every star. That is quicker and far more accurate than aiming at a 26dp
 * target, and it is the behaviour the website grew a touch handler for.
 *
 * Nothing is committed until Save. A preset that arrived from somewhere fuzzy —
 * a long-press, a voice command — must be confirmed before it is written,
 * because a rating is an opinion and the app must never guess one.
 */
@Composable
fun RatingSheet(
    title: String,
    current: Int,
    onSave: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    celebrate: Boolean = true,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var score by remember(current) { mutableIntStateOf(current.coerceIn(0, 10)) }
    var rowWidth by remember { mutableIntStateOf(0) }
    // Fired on SAVE, not on reaching ten while scrubbing: sliding across the
    // row passes through ten on the way to nowhere, and a burst for a score you
    // did not commit to is a burst that means nothing.
    var celebrate by remember { mutableStateOf(false) }

    fun pick(next: Int) {
        val clamped = next.coerceIn(1, 10)
        if (clamped != score) {
            score = clamped
            haptics?.play(if (clamped == 10) Haptic.Celebrate else Haptic.Detent)
        }
    }

    /** Which star an x in pixels lands on, 1..10. */
    fun scoreAt(x: Float): Int {
        if (rowWidth <= 0) return score
        return ((x / rowWidth) * 10f).toInt().coerceIn(0, 9) + 1
    }

    CvSheet(onDismiss = onDismiss) {
        Text("RATE THIS", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            maxLines = 2,
        )

        Spacer(Modifier.height(22.dp))

        // The score, big, because it is the thing being chosen. It slides up as
        // it rises and down as it falls, so the direction of the change reads
        // even when the number itself is only one digit different.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = score,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "score",
            ) { value ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (value == 0) "-" else value.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (value == 0) colors.text3 else colors.gold,
                    )
                    Text(
                        "/10",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text3,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            if (score == 0) "Slide to pick a score" else LABELS[score - 1],
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onSizeChanged { rowWidth = it.width }
                // Tap and drag are separate detectors on the same row: a tap
                // picks the star under the finger, a drag follows it. Doing it
                // on the ROW rather than on each star is what makes the gesture
                // continuous — there are no gaps between targets to fall into.
                .pointerInput(rowWidth) {
                    detectTapGestures { offset -> pick(scoreAt(offset.x)) }
                }
                .pointerInput(rowWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> pick(scoreAt(offset.x)) },
                    ) { change, _ -> pick(scoreAt(change.position.x)) }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 1..10) {
                val on = index <= score
                val scale by animateFloatAsState(
                    targetValue = if (index == score) 1.18f else if (on) 1f else 0.86f,
                    animationSpec = Motion.lively(),
                    label = "star",
                )
                Box(
                    Modifier.weight(1f).height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "$index of 10 — ${LABELS[index - 1]}",
                        tint = if (on) colors.gold else colors.text3.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(27.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current > 0) {
                Box(
                    Modifier
                        .height(50.dp)
                        .glass(CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Untick); onClear(); onDismiss() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                }
            }
            Button(
                onClick = {
                    haptics?.play(if (score == 10) Haptic.Celebrate else Haptic.Success)
                    onSave(score)
                    if (score == 10 && celebrate) {
                        // The sheet stays up for the burst, then closes itself.
                        // Dismissing immediately would throw the confetti away
                        // with the surface it was drawn on.
                        celebrate = true
                    } else {
                        onDismiss()
                    }
                },
                enabled = score > 0,
                shape = CvShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.gold,
                    contentColor = Color.Black,
                    disabledContainerColor = colors.glass,
                    disabledContentColor = colors.text3,
                ),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text(
                    if (current > 0 && score == current) "Saved" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (celebrate) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1_250)
            onDismiss()
        }
        // Over everything, consuming nothing.
        Box(Modifier.fillMaxSize()) {
            Confetti(play = true)
        }
    }
}
