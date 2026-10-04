package com.cineverse.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.GeminiColors
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.geminiGlow
import com.cineverse.app.data.ai.Mood
import com.cineverse.app.data.ai.TimeBox
import com.cineverse.app.data.ai.TonightPick
import com.cineverse.app.data.model.MediaItem
import kotlinx.coroutines.launch

/** The way in, on Home: one line and a moon. */
@Composable
fun TonightBanner(onClick: () -> Unit) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(
                Brush.linearGradient(
                    listOf(GeminiColors[0].copy(alpha = 0.16f), GeminiColors[1].copy(alpha = 0.12f), GeminiColors[2].copy(alpha = 0.10f))
                )
            )
            .border(1.dp, colors.hairline, CvShape.Large)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Nightlight, null, tint = GeminiColors[1], modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("What should I watch tonight?", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("Pick a mood and the time you have", style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
        Icon(Icons.Rounded.AutoAwesome, null, tint = GeminiColors[2], modifier = Modifier.size(18.dp))
    }
}

/**
 * Tonight, in three taps: a mood, a length, a film. The pick is from your own
 * list. "Another" passes over the last one, and Start watching goes straight
 * to the Live Update.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TonightSheet(
    pick: suspend (Mood, TimeBox, Set<String>) -> TonightPick?,
    onOpen: (MediaItem) -> Unit,
    onStart: (TonightPick) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf<Mood?>(null) }
    var time by remember { mutableStateOf<TimeBox?>(null) }
    var result by remember { mutableStateOf<TonightPick?>(null) }
    var picking by remember { mutableStateOf(false) }
    var nothing by remember { mutableStateOf(false) }
    val passed = remember { mutableSetOf<String>() }

    fun go() {
        val m = mood ?: return
        val t = time ?: return
        picking = true
        nothing = false
        haptics?.play(Haptic.Select)
        scope.launch {
            val next = pick(m, t, passed.toSet())
            picking = false
            result = next
            nothing = next == null
            if (next != null) {
                passed += next.item.key
                haptics?.play(Haptic.Success)
            } else haptics?.play(Haptic.Warning)
        }
    }

    CvSheet(onDismiss = onDismiss) {
        Text("Tonight", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text("From your list, for how you feel and the time you have.", style = MaterialTheme.typography.labelMedium, color = colors.text3)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = when {
                picking -> 1
                result != null -> 2
                else -> 0
            },
            transitionSpec = { (fadeIn(tween(280)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
            label = "tonightStage",
        ) { stage ->
            when (stage) {
                1 -> Reel()
                2 -> result?.let { r ->
                    Picked(
                        r,
                        onOpen = { onOpen(r.item); onDismiss() },
                        onStart = { onStart(r); onDismiss() },
                        onAnother = ::go,
                    )
                }
                else -> Column {
                    Text("I'm in the mood for", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in Mood.entries) {
                            Choice(option.label, mood == option) { mood = option; haptics?.play(Haptic.Select) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("I have", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in TimeBox.entries) {
                            Choice(option.label, time == option) { time = option; haptics?.play(Haptic.Select) }
                        }
                    }
                    if (nothing) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nothing on your list fits that. Try a longer time or another mood.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text3,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    CvButton(
                        "Pick for me",
                        ::go,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.AutoAwesome,
                        enabled = mood != null && time != null,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = CvTheme.colors
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) colors.ink else colors.text,
        modifier = Modifier
            .clip(CvShape.Pill)
            .background(if (on) colors.text else colors.glass)
            .border(1.dp, if (on) Color.Transparent else colors.hairline, CvShape.Pill)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** While it chooses: a light running across Gemini's colours. */
@Composable
private fun Reel() {
    val loop = rememberInfiniteTransition(label = "tonightReel")
    val sweep by loop.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(CvShape.Large)
            .background(CvTheme.colors.text.copy(alpha = 0.04f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    (sweep - 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                    sweep.coerceIn(0f, 1f) to GeminiColors[1].copy(alpha = 0.22f),
                    (sweep + 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                    1f to Color.Transparent,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("Choosing…", style = MaterialTheme.typography.titleMedium, color = CvTheme.colors.text2)
    }
}

@Composable
private fun Picked(pick: TonightPick, onOpen: () -> Unit, onStart: () -> Unit, onAnother: () -> Unit) {
    val colors = CvTheme.colors
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(width = 110.dp, height = 165.dp)
                    .clip(CvShape.Medium)
                    .background(colors.surface2)
                    .clickableNoRipple(onOpen),
            ) {
                CvImage(Img.poster(pick.item.posterPath), pick.item.title, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("TONIGHT", style = com.cineverse.app.core.design.KickerStyle, color = GeminiColors[1])
                Spacer(Modifier.height(4.dp))
                Text(
                    pick.item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        pick.item.year.ifBlank { null },
                        pick.runtime.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    pick.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier
                        .geminiGlow(pick.byGemini, corner = 14.dp, width = 1.2.dp)
                        .clip(CvShape.Medium)
                        .background(colors.text.copy(alpha = 0.04f))
                        .padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pick.runtime > 0) CvButton("Start watching", onStart, modifier = Modifier.weight(1f))
            else CvButton("Open", onOpen, modifier = Modifier.weight(1f))
            CvButton("Another", onAnother, primary = false)
        }
    }
}
