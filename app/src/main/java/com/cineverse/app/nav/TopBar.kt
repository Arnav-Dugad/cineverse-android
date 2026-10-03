package com.cineverse.app.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.AnimatedSearch
import com.cineverse.app.core.ui.PaneEdge
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.glassPane

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
    val interaction = remember { MutableInteractionSource() }

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
                ) else Modifier.glassPane(PaneEdge.Bottom, colors.ink, opacity = 0.975f)
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = ScreenPadding - 6.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CvMark(size = 22.dp)
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
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { haptics?.play(Haptic.Tap); onSearch() },
            contentAlignment = Alignment.Center,
        ) {
            // Pressing it pulls the handle out of the glass, which is the only
            // feedback a 42dp target with no ripple gets.
            val pressed by interaction.collectIsPressedAsState()
            AnimatedSearch(
                active = pressed,
                color = if (overArt) Color.White else colors.text,
                size = 23.dp,
            )
        }
    }
}

/**
 * The mark.
 *
 * It was a play character set in the body typeface inside a flat red circle,
 * which is to say it was whatever that font felt like drawing that day: off
 * centre, the wrong weight, and a different shape on a device with a different
 * font stack. A wordmark cannot be a text glyph.
 *
 * So it is drawn. A squircle in the brand gradient, a triangle with rounded
 * tips sitting slightly right of true centre - because an optically centred
 * triangle sits left of the geometric centre, which is the one thing everybody
 * gets wrong about play buttons - and a sheen across the top.
 */
@Composable
private fun CvMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val corner = androidx.compose.ui.geometry.CornerRadius(side * 0.3f)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Palette.Red2, Palette.Red),
                start = Offset.Zero,
                end = Offset(side, side),
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.26f),
                0.5f to Color.Transparent,
            ),
            cornerRadius = corner,
        )
        val play = Path().apply {
            moveTo(side * 0.40f, side * 0.29f)
            lineTo(side * 0.73f, side * 0.50f)
            lineTo(side * 0.40f, side * 0.71f)
            close()
        }
        drawPath(play, color = Color.White)
        // Stroked over the fill with a round join, which is how you round the
        // tips of a triangle without three more curves in the path.
        drawPath(
            play,
            color = Color.White,
            style = Stroke(
                width = side * 0.09f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
