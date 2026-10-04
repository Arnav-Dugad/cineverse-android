package com.cineverse.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.geminiBrushStatic
import com.cineverse.app.core.ui.geminiGlow

/**
 * "Ask CineVerse", floating over Home: one tap and the keyboard is up, the
 * microphone half and it is already listening. Holding it asks Gemini to
 * pick something for tonight. It shrinks to its spark while you scroll down
 * the page and opens out again as you scroll back up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AskCineVerseFab(
    expanded: Boolean,
    onText: () -> Unit,
    onVoice: () -> Unit,
    onTonight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val reduced = CvTheme.reducedMotion

    // Arrives a beat after the page, with a little overshoot.
    val arrive = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            kotlinx.coroutines.delay(450)
            arrive.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
        }
    }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 700f), label = "askPress")

    Row(
        modifier
            .graphicsLayer {
                val s = (0.6f + 0.4f * arrive.value) * squeeze
                scaleX = s; scaleY = s
                alpha = arrive.value.coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
            }
            .geminiGlow(corner = 28.dp, width = 1.6.dp)
            .shadow(14.dp, CvShape.Pill, clip = false)
            .clip(CvShape.Pill)
            .background(colors.surface2)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .combinedClickable(
                    interactionSource = press,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Ask by typing",
                    onLongClickLabel = "Pick something for tonight",
                    onLongClick = { haptics?.play(Haptic.Peek); onTonight() },
                    onClick = { haptics?.play(Haptic.Tap); onText() },
                )
                .semantics { contentDescription = "Ask CineVerse" }
                .padding(start = 18.dp, end = if (expanded) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientSpark()
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    Text("Ask CineVerse", style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 1)
                }
            }
        }
        Box(Modifier.width(1.dp).height(24.dp).background(colors.hairline))
        Box(
            Modifier
                .fillMaxHeight()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Ask by voice",
                    onClick = { haptics?.play(Haptic.Tap); onVoice() },
                )
                .semantics { contentDescription = "Ask CineVerse by voice" }
                .padding(start = 12.dp, end = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, null, tint = colors.text, modifier = Modifier.size(22.dp))
        }
    }
}

/** The spark, painted in Gemini's gradient rather than a single tint. */
@Composable
private fun GradientSpark() {
    Icon(
        Icons.Rounded.AutoAwesome,
        null,
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(geminiBrushStatic, blendMode = BlendMode.SrcAtop)
                }
            },
    )
}
