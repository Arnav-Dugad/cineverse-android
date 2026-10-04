package com.cineverse.app.feature.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.badges.BadgeContext
import com.cineverse.app.data.badges.Badges
import com.cineverse.app.data.badges.Difficulty
import com.cineverse.app.data.badges.Tier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class BadgeRow(val badge: com.cineverse.app.data.badges.Badge, val value: Int) {
    val earned: Boolean get() = value >= badge.goal
    val progress: Float get() = (value.toFloat() / badge.goal).coerceIn(0f, 1f)
}

@Immutable
data class ChallengeRow(val challenge: com.cineverse.app.data.badges.Challenge, val value: Int) {
    val done: Boolean get() = value >= challenge.goal
    val progress: Float get() = (value.toFloat() / challenge.goal).coerceIn(0f, 1f)
}

class BadgesViewModel(app: AppContainer) : ViewModel() {
    val state: StateFlow<Pair<List<BadgeRow>, List<ChallengeRow>>> = app.library.library.map { lib ->
        val context = BadgeContext.of(lib)
        Badges.ALL.map { BadgeRow(it, it.value(context)) } to
            Badges.CHALLENGES.map { ChallengeRow(it, it.value(context)) }
                // In progress first, then done; easier first within each - the website's order.
                .sortedWith(compareBy<ChallengeRow> { it.done }.thenBy { it.challenge.difficulty.ordinal })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BadgeRow>() to emptyList())
}

/** The website's badge gallery and lifetime challenges, side by side with it, unlock for unlock. */
@Composable
fun BadgesScreen(viewModel: BadgesViewModel, onBack: () -> Unit) {
    val colors = CvTheme.colors
    val (badges, challenges) = viewModel.state.collectAsStateWithLifecycle().value
    val earned = badges.count { it.earned }
    val done = challenges.count { it.done }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(colors.ink).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CvShape.Circle).clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
                    }
                    Text("Badges", style = MaterialTheme.typography.titleLarge, color = colors.text)
                }
                Text(
                    "$earned of ${badges.size} badges · $done of ${challenges.size} challenges",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        itemsIndexed(badges, key = { _, row -> row.badge.id }) { index, row -> BadgeTile(row, index) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "LIFETIME CHALLENGES",
                style = KickerStyle,
                color = colors.text3,
                modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
            )
        }
        itemsIndexed(challenges, key = { _, row -> row.challenge.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, row ->
            ChallengeCard(row, index)
        }
    }
}

@Composable
private fun BadgeTile(row: BadgeRow, index: Int) {
    val colors = CvTheme.colors
    val dark = colors.ink.luminance() < 0.5f
    val tone = tierColor(row.badge.tier, dark)
    val arrive = rememberArrival(1f, (index * 35).coerceAtMost(500), 520)
    val bar = rememberArrival(row.progress, 250 + (index * 35).coerceAtMost(500), 900)
    Column(
        Modifier
            .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 18f; scaleX = 0.92f + 0.08f * arrive; scaleY = scaleX }
            .clip(CvShape.Large)
            .background(if (row.earned) tone.copy(alpha = 0.10f) else colors.text.copy(alpha = 0.03f))
            .border(1.dp, if (row.earned) tone.copy(alpha = 0.45f) else colors.hairline, CvShape.Large)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CvShape.Circle)
                .background(
                    if (row.earned) Brush.linearGradient(listOf(tone, tone.copy(alpha = 0.55f), tone))
                    else Brush.linearGradient(listOf(colors.text.copy(alpha = 0.08f), colors.text.copy(alpha = 0.04f)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (row.earned) {
                // The sheen: a band of light across an earned medal.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), start = Offset(0f, 0f), end = Offset(160f, 160f))),
                )
            }
            BadgeGlyph(row.badge.icon, if (row.earned) Color(0xFF0B0B10) else colors.text3, 26.dp)
            if (!row.earned) {
                Icon(
                    Icons.Rounded.Lock, null, tint = colors.text3,
                    modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CvShape.Circle).background(colors.ink).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            row.badge.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (row.earned) colors.text else colors.text2,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            row.badge.description,
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        if (row.earned) {
            Text(row.badge.tier.name.uppercase(), style = KickerStyle, color = tone)
        } else {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CvShape.Pill).background(colors.text.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(bar).height(4.dp).clip(CvShape.Pill).background(tone))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${"%,d".format(row.value)}${row.badge.unit} / ${"%,d".format(row.badge.goal)}${row.badge.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
        }
    }
}

@Composable
private fun ChallengeCard(row: ChallengeRow, index: Int) {
    val colors = CvTheme.colors
    val accent = difficultyColor(row.challenge.difficulty)
    val fill = rememberArrival(row.progress, 200 + index * 60, 1100)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(colors.text.copy(alpha = 0.04f))
            .border(1.dp, colors.hairline, CvShape.Large)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(accent.copy(alpha = 0.16f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * fill, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (row.done) Icon(Icons.Rounded.Check, "Complete", tint = accent, modifier = Modifier.size(26.dp))
            else Text("${(fill * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = colors.text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeGlyph(row.challenge.icon, colors.text2, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(row.challenge.name, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(
                    row.challenge.difficulty.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.clip(CvShape.Pill).background(accent.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            Text(row.challenge.description, style = MaterialTheme.typography.labelMedium, color = colors.text3)
            Text(
                if (row.done) "Complete" else "${"%,d".format(row.challenge.goal - row.value)}${row.challenge.unit} to go",
                style = MaterialTheme.typography.labelMedium,
                color = if (row.done) accent else colors.text2,
            )
        }
    }
}

/** The website's tier colours: bronze, silver, gold, and platinum's cool white. */
fun tierColor(tier: Tier, dark: Boolean): Color = when (tier) {
    Tier.Bronze -> if (dark) Color(0xFFD08B52) else Color(0xFFA15C24)
    Tier.Silver -> if (dark) Color(0xFFCBD5E1) else Color(0xFF64748B)
    Tier.Gold -> if (dark) Palette.Gold else Color(0xFFB7791F)
    Tier.Platinum -> if (dark) Color(0xFFA5F3FC) else Color(0xFF0E7490)
}

private fun difficultyColor(difficulty: Difficulty): Color = when (difficulty) {
    Difficulty.Easy -> Palette.Green2
    Difficulty.Medium -> Palette.Cyan2
    Difficulty.Hard -> Palette.Gold
    Difficulty.Insane -> Palette.Red2
    Difficulty.Legendary -> Palette.Purple2
}

private fun glyphFor(name: String): ImageVector = when (name) {
    "clapper" -> Icons.Rounded.Movie
    "popcorn" -> Icons.Rounded.LocalMovies
    "film" -> Icons.Rounded.Theaters
    "medal" -> Icons.Rounded.MilitaryTech
    "columns" -> Icons.Rounded.ViewColumn
    "hourglass" -> Icons.Rounded.HourglassBottom
    "stopwatch" -> Icons.Rounded.Timer
    "runner" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "star" -> Icons.Rounded.Star
    "eye" -> Icons.Rounded.Visibility
    "trophy" -> Icons.Rounded.EmojiEvents
    "scales" -> Icons.Rounded.Balance
    "perfect" -> Icons.Rounded.AutoAwesome
    "compass" -> Icons.Rounded.Explore
    "globe" -> Icons.Rounded.Public
    "cassette" -> Icons.Rounded.Album
    "camera" -> Icons.Rounded.Videocam
    "starBurst" -> Icons.Rounded.Stars
    "clipboard" -> Icons.AutoMirrored.Rounded.Assignment
    else -> Icons.Rounded.EmojiEvents
}

@Composable
fun BadgeGlyph(name: String, tint: Color, size: Dp) {
    Icon(glyphFor(name), null, tint = tint, modifier = Modifier.size(size))
}
