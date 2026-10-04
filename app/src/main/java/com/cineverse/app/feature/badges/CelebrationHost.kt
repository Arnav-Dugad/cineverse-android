package com.cineverse.app.feature.badges

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.Confetti
import com.cineverse.app.core.ui.OdometerText
import com.cineverse.app.core.ui.StreakFlame
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.badges.Celebration
import kotlinx.coroutines.delay

/**
 * The moment something is earned: a card that drops in at the top of the
 * screen, wherever you are - the website's milestone toast. A streak gets the
 * flame in a filling ring and the days rolling up; a badge gets its medal
 * and a burst of confetti. It goes by itself after nine seconds, and one tap
 * opens the Diary or the badges.
 */
@Composable
fun CelebrationHost(app: AppContainer, onDiary: () -> Unit, onBadges: () -> Unit) {
    var current by remember { mutableStateOf<Celebration?>(null) }
    var visible by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) {
        app.celebrations.events.collect { event ->
            current = event
            visible = true
            haptics?.play(Haptic.Celebrate)
            delay(9_000)
            visible = false
        }
    }
    val event = current ?: return
    Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
        Box {
            AnimatedVisibility(
                visible,
                enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                Card(event, onOpen = {
                    visible = false
                    if (event is Celebration.StreakMilestone) onDiary() else onBadges()
                }, onClose = { visible = false })
            }
            if (visible && event is Celebration.BadgeUnlocked) {
                Confetti(play = true, modifier = Modifier.matchParentSize(), pieces = if (event.badges.size > 1) 140 else 90)
            }
        }
    }
}

@Composable
private fun Card(event: Celebration, onOpen: () -> Unit, onClose: () -> Unit) {
    val colors = CvTheme.colors
    val fill = rememberArrival(1f, 160, 900)
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(colors.surface1)
            .border(1.dp, colors.hairline, CvShape.Large)
            .clickableNoRipple(onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = when (event) {
            is Celebration.StreakMilestone -> androidx.compose.ui.graphics.Color(0xFFFF7A1A)
            is Celebration.BadgeUnlocked -> tierColor(event.badges.first().tier, dark = true)
        }
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(58.dp)) {
                val stroke = 4.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(tone.copy(alpha = 0.2f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(tone, -90f, 360f * fill, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            when (event) {
                is Celebration.StreakMilestone -> StreakFlame(lit = true, size = 28.dp)
                is Celebration.BadgeUnlocked -> BadgeGlyph(event.badges.first().icon, tone, 26.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (event is Celebration.StreakMilestone) "STREAK MILESTONE" else "BADGE UNLOCKED",
                style = com.cineverse.app.core.design.KickerStyle,
                color = tone,
            )
            when (event) {
                is Celebration.StreakMilestone -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OdometerText(event.days, MaterialTheme.typography.titleMedium, colors.text)
                    Text(" days in a row with something watched", style = MaterialTheme.typography.titleSmall, color = colors.text)
                }
                is Celebration.BadgeUnlocked -> Text(
                    if (event.badges.size == 1) event.badges.first().name else "${event.badges.size} new badges",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
            }
        }
        Icon(
            Icons.Rounded.Close, "Dismiss",
            tint = colors.text3,
            modifier = Modifier.size(36.dp).clip(CvShape.Circle).clickableNoRipple(onClose).padding(8.dp),
        )
    }
}
