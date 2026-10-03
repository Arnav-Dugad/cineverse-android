package com.cineverse.app.feature.pick

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.data.model.MediaItem
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Pick for me, ported from the website's js/pick-for-me.js.
 *
 * CineVerse decides for you: the posters flip past, slow down like a wheel
 * losing speed, and settle on one title with a thump. From there you can open
 * it or spin again. Each of the last few frames clicks under the thumb, so the
 * slowing is felt as well as seen.
 */
@Composable
fun PickSheet(
    picks: List<MediaItem>,
    label: String,
    onOpen: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (picks.isEmpty()) return
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val reduced = CvTheme.reducedMotion
    var spin by remember { mutableIntStateOf(0) }
    var showing by remember { mutableIntStateOf(0) }
    var winner by remember { mutableStateOf<MediaItem?>(null) }
    val flip = remember { Animatable(0f) }

    LaunchedEffect(spin) {
        winner = null
        val chosen = Random.nextInt(picks.size)
        val reel = if (reduced) listOf(chosen) else Spin.sequence(picks.size, chosen)
        reel.forEachIndexed { index, position ->
            showing = position
            if (!reduced) {
                // Each frame is a card turning in from edge-on.
                flip.snapTo(70f)
                flip.animateTo(0f, tween((Spin.frameDelay(index, reel.size) * 0.8f).toInt(), easing = Motion.EaseOut))
            }
            if (index > reel.size - 6) haptics?.play(Haptic.Detent)
            delay(if (reduced) 0 else (Spin.frameDelay(index, reel.size) * 0.2f).toLong())
        }
        winner = picks[chosen]
        haptics?.play(Haptic.Drop)
    }

    // The landing: a small overshoot and a gold glow, only once it has stopped.
    val landed by animateFloatAsState(
        if (winner != null) 1f else 0f,
        animationSpec = Motion.landing(),
        label = "landed",
    )

    CvSheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PICK FOR ME · ${label.uppercase()}", style = KickerStyle, color = colors.text3)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .width(190.dp)
                    .height(285.dp)
                    .drawBehind {
                        if (landed > 0f) drawCircle(
                            Brush.radialGradient(
                                listOf(colors.gold.copy(alpha = 0.45f * landed), Color.Transparent),
                                radius = size.maxDimension * 0.75f,
                            ),
                            radius = size.maxDimension * 0.75f,
                        )
                    }
                    .graphicsLayer {
                        rotationY = flip.value
                        cameraDistance = 14f * density
                        val pop = 1f + 0.06f * landed * (1f - landed) * 4f
                        scaleX = pop
                        scaleY = pop
                    }
                    .clip(CvShape.Large),
            ) {
                val item = picks[showing.coerceIn(0, picks.lastIndex)]
                CvImage(Img.poster(item.posterPath), item.title, Modifier.matchParentSize())
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = winner,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pickTitle",
            ) { chosen ->
                Text(
                    chosen?.let { it.title + if (it.year.isNotBlank()) " · ${it.year}" else "" } ?: "Choosing…",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (chosen != null) colors.text else colors.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CvButton(
                    "Open",
                    { winner?.let { onDismiss(); onOpen(it) } },
                    enabled = winner != null,
                    modifier = Modifier.width(130.dp),
                )
                CvButton(
                    "Spin again",
                    { spin++ },
                    primary = false,
                    icon = Icons.Rounded.Casino,
                    enabled = winner != null,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "From ${picks.size} titles",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
            Spacer(Modifier.size(6.dp))
        }
    }
}

/** The reel, pure, so its shape can be tested without a frame clock. */
object Spin {

    /** The indices to flash through, ending on the winner. */
    fun sequence(count: Int, winner: Int, steps: Int = 15): List<Int> {
        if (count < 1) return emptyList()
        val safe = ((winner % count) + count) % count
        return List(steps) { i -> (safe + count - ((steps - i) % count)) % count } + safe
    }

    /** How long each frame is held: quick at first, easing to slow. */
    fun frameDelay(index: Int, total: Int, from: Int = 45, to: Int = 250): Int {
        if (total <= 1) return to
        val t = index.toFloat() / (total - 1)
        return (from + (to - from) * (t * t)).toInt()
    }
}
