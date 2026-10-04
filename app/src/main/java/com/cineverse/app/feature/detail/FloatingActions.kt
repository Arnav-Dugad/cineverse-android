package com.cineverse.app.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.GeminiColors
import com.cineverse.app.core.ui.clickableNoRipple

/**
 * The title page's actions, floating: Material 3 Expressive's floating
 * toolbar. Once the row of buttons under the poster has scrolled away, the
 * same actions rise from the bottom in a pill - the trailer as its one
 * filled button, then save, watched, your score, Ask (with Gemini on) and
 * share - and sink away again when the page is back at the top.
 */
@Composable
fun FloatingTitleToolbar(
    visible: Boolean,
    accent: Color?,
    saved: Boolean,
    watched: Boolean,
    rating: Int,
    hasTrailer: Boolean,
    onTrailer: () -> Unit,
    onSave: () -> Unit,
    onWatched: () -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onAsk: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = 420f)) { it * 2 } +
            scaleIn(spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.8f) + fadeIn(),
        exit = slideOutVertically(spring(stiffness = 600f)) { it * 2 } + scaleOut(targetScale = 0.85f) + fadeOut(),
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
                .shadow(16.dp, CvShape.Pill, clip = false)
                .clip(CvShape.Pill)
                .background(colors.surface2)
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hasTrailer) {
                val fill = accent ?: Palette.Red2
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(CvShape.Pill)
                        .background(fill)
                        .clickableNoRipple { haptics?.play(Haptic.Tap); onTrailer() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trailer", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
            Action(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Saved" else "Save", if (saved) Palette.Red2 else colors.text) {
                haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave()
            }
            Action(Icons.Rounded.Check, if (watched) "Watched" else "Mark watched", if (watched) colors.green else colors.text) {
                haptics?.play(if (watched) Haptic.Untick else Haptic.Tick); onWatched()
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickableNoRipple { haptics?.play(Haptic.Tap); onRate() },
                contentAlignment = Alignment.Center,
            ) {
                if (rating > 0) {
                    Text("$rating", style = MaterialTheme.typography.titleMedium, color = colors.gold)
                } else {
                    Icon(Icons.Rounded.StarBorder, "Rate", tint = colors.text, modifier = Modifier.size(22.dp))
                }
            }
            if (onAsk != null) {
                Action(Icons.Rounded.AutoAwesome, "Ask about it", GeminiColors[1]) { haptics?.play(Haptic.Tap); onAsk() }
            }
            Action(Icons.Rounded.Share, "Share", colors.text) { haptics?.play(Haptic.Tap); onShare() }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
    }
}
