package com.cineverse.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple

/**
 * The wordmark and one action.
 *
 * Search lives here rather than in the bottom bar because search is a MODE, not
 * a place — the pattern IMDb, YouTube and Prime all settled on. On Home the bar
 * is transparent so the hero reaches the top of the screen; everywhere else it
 * sits on the page colour.
 */
@Composable
fun CvTopBar(tab: Tab, onSearch: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val overArt = tab == Tab.Home

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                // Over the hero the bar is a scrim rather than a surface, so the
                // artwork reaches the top of the screen. It has to be a STRONG
                // scrim: at 0.7 fading immediately, a poster's caption scrolling
                // underneath was legible straight through the wordmark. It holds
                // nearly full page colour behind the bar itself and only releases
                // below it.
                if (overArt) Modifier.background(
                    Brush.verticalGradient(
                        0f to colors.ink.copy(alpha = 0.95f),
                        0.62f to colors.ink.copy(alpha = 0.82f),
                        1f to Color.Transparent,
                    )
                ) else Modifier.background(colors.ink)
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = ScreenPadding - 6.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(Palette.Red),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "  ${if (overArt) "CineVerse" else tab.label}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W800),
                color = if (overArt) Palette.Red2 else colors.text,
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(42.dp)
                .clip(CircleShape)
                .clickableNoRipple { haptics?.play(Haptic.Tap); onSearch() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = "Search",
                tint = if (overArt) Color.White else colors.text,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}
