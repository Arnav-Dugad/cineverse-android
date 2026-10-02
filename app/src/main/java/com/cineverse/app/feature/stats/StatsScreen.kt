package com.cineverse.app.feature.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.feature.list.EmptyState

/**
 * Stats, as a vertical story rather than a wall.
 *
 * The website's stats page is the most information-dense thing in CineVerse and
 * the user said it had become confusing. On a phone there is no room to be
 * confusing, which is a useful constraint: six figures, then the last twelve
 * weeks of viewing, then what you actually watch. One idea per screenful.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CvTheme.colors

    if (state.loaded && !state.signedIn) {
        EmptyState(
            title = "Sign in to see your numbers",
            body = "Hours watched, streaks, the shows you are in the middle of — all of it comes from what you track.",
            action = "Sign in" to onSignIn,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp, bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                Text("YOUR VIEWING", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                Text(
                    hoursLine(state.totalMinutes),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.text,
                )
                Text(
                    "across everything you have tracked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text3,
                )
            }
        }

        item(key = "figures") {
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                state.figures.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (figure in pair) {
                            FigureCard(figure, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.diary.any { it.count > 0 }) {
            item(key = "diary") {
                Column {
                    SectionHeader("The last twelve weeks")
                    Spacer(Modifier.height(12.dp))
                    DiaryGrid(state.diary, Modifier.padding(horizontal = ScreenPadding))
                }
            }
        }

        if (state.months.any { it.second > 0 }) {
            item(key = "months") {
                Column {
                    SectionHeader("By month")
                    Spacer(Modifier.height(16.dp))
                    MonthBars(state.months, Modifier.padding(horizontal = ScreenPadding))
                }
            }
        }

        if (state.genres.isNotEmpty()) {
            item(key = "genres") {
                Column {
                    SectionHeader("What you watch")
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = ScreenPadding)) {
                        for (slice in state.genres) GenreBar(slice)
                    }
                }
            }
        }
    }
}

private fun hoursLine(minutes: Int): String {
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 2 -> "$days days"
        hours >= 1 -> "$hours hours"
        else -> "$minutes minutes"
    }
}

@Composable
private fun FigureCard(figure: Figure, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(
        modifier
            .clip(CvShape.XLarge)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp)
    ) {
        Text(
            figure.label.uppercase(),
            style = KickerStyle,
            color = colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(figure.value, style = MaterialTheme.typography.headlineMedium, color = colors.text)
        if (figure.detail.isNotBlank()) {
            Text(
                figure.detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
            )
        }
    }
}

/**
 * Twelve weeks of days, GitHub-style — the densest honest way to show "when do
 * I actually watch things" in the width of a phone.
 */
@Composable
private fun DiaryGrid(days: List<DiaryDay>, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val busiest = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Column(modifier) {
        // Twelve columns of seven, filled down then across, so each column is a
        // week and the grid reads left-to-right like a calendar.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.chunked(7).forEach { week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    week.forEach { day ->
                        val level = if (day.count == 0) 0f else
                            (0.25f + 0.75f * (day.count.toFloat() / busiest)).coerceIn(0.25f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(CvShape.Tiny)
                                .background(
                                    if (day.count == 0) colors.glassStrong
                                    else colors.green.copy(alpha = level)
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.text3)
            Spacer(Modifier.width(6.dp))
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(11.dp)
                        .clip(CvShape.Tiny)
                        .background(colors.green.copy(alpha = level))
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}

@Composable
private fun MonthBars(months: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val peak = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for ((label, count) in months) {
            // The bars grow when the section scrolls in — the website's count-up,
            // which is the one piece of decoration that is also information: you
            // can see the shape arrive.
            val grown by animateFloatAsState(
                targetValue = count.toFloat() / peak,
                animationSpec = Motion.gentle(),
                label = "bar",
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (count > 0) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((96 * (if (CvTheme.reducedMotion) count.toFloat() / peak else grown))
                            .coerceAtLeast(if (count > 0) 4f else 2f).dp)
                        .clip(CvShape.Tiny)
                        .background(if (count > 0) Palette.Red2 else colors.glassStrong)
                )
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
            }
        }
    }
}

@Composable
private fun GenreBar(slice: GenreSlice) {
    val colors = CvTheme.colors
    val grown by animateFloatAsState(
        targetValue = slice.share,
        animationSpec = Motion.gentle(),
        label = "genre",
    )
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                slice.name,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${slice.count}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CvShape.Pill)
                .background(colors.glassStrong)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (CvTheme.reducedMotion) slice.share else grown)
                    .height(6.dp)
                    .clip(CvShape.Pill)
                    .background(Palette.Red2)
            )
        }
    }
}
