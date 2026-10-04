package com.cineverse.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics

/**
 * The bar on a pushed screen.
 *
 * One back target, one name, nothing else. Every screen that is not a tab wore
 * a slightly different arrangement of the same two things before this existed,
 * which is the kind of drift nobody can name and everybody feels.
 *
 * The title is left-aligned beside the arrow rather than centred. A centred
 * title has to be short enough to clear both sides, and the moment it is not it
 * either truncates in the middle of a word or shoves the arrow off balance.
 */
@Composable
fun CvScreenBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    /** 0..1: how far a large title below has collapsed into this bar. */
    titleAlpha: () -> Float = { 1f },
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.ink)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickableNoRipple { haptics?.play(Haptic.Tap); onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.text,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer {
                val shown = titleAlpha()
                alpha = shown
                translationY = (1f - shown) * 10.dp.toPx()
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * A large title at the top of a list - the M3 large app bar's shape - that
 * shrinks and fades as it scrolls up, while the same title fades into the
 * bar above. [collapse] reads how far that has gone, 0 open to 1 folded.
 */
@Composable
fun LargeTitle(title: String, state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = CvTheme.colors
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(top = 6.dp, bottom = 14.dp)
            .graphicsLayer {
                val c = state.collapse(80.dp.toPx())
                alpha = 1f - c
                val s = 1f - 0.12f * c
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
            },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = colors.text,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.text3)
        }
    }
}

/** How far the first item of a list has scrolled away, over [distance] pixels: 0..1. */
fun androidx.compose.foundation.lazy.LazyListState.collapse(distance: Float): Float =
    if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
