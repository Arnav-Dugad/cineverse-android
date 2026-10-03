package com.cineverse.app.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.Confetti
import com.cineverse.app.core.ui.CountUpString
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.recap.Recaps
import com.cineverse.app.data.recap.SeasonRecap
import com.cineverse.app.data.recap.SeriesRecap
import com.cineverse.app.data.recap.TopEpisode

/**
 * Finishing a season, and finishing a show - the website's season recap and
 * series finale cards, as sheets.
 *
 * Every figure arrives: the tiles deal in one after another and their numbers
 * count up, the season bars of a finale rise in order, and a finale lands with
 * confetti. A recap of a season that was only ever swept in with "mark season"
 * says so rather than inventing a viewing that did not happen.
 */
@Composable
fun SeasonRecapSheet(
    title: String,
    backdrop: String?,
    recap: SeasonRecap,
    onDismiss: () -> Unit,
) {
    CvSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            RecapHeader(
                kicker = "SEASON RECAP",
                title = title,
                heading = "Season ${recap.season}",
                dates = Recaps.dates(recap.startedAt, recap.finishedAt),
                line = when {
                    recap.marked -> "Marked as watched"
                    recap.pattern != null -> "Watched ${recap.pattern.phrase}"
                    else -> "Finished in ${recap.spanDays} day${if (recap.spanDays == 1) "" else "s"}"
                },
                backdrop = backdrop,
            )
            Spacer(Modifier.height(18.dp))
            FigureGrid(Recaps.seasonFigures(recap))
            recap.top?.let {
                Spacer(Modifier.height(18.dp))
                TopEpisodeCard(it, delay = 700)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun SeriesFinaleSheet(
    title: String,
    backdrop: String?,
    recap: SeriesRecap,
    celebrate: Boolean,
    onDismiss: () -> Unit,
) {
    Box {
        CvSheet(onDismiss) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RecapHeader(
                    kicker = "SERIES COMPLETE",
                    title = title,
                    heading = "Your whole run",
                    dates = Recaps.dates(recap.startedAt, recap.finishedAt),
                    line = when {
                        recap.marked -> "Marked as watched"
                        recap.pattern != null -> "Watched ${recap.pattern.phrase}"
                        else -> "${recap.episodes} episodes over ${recap.spanDays} day${if (recap.spanDays == 1) "" else "s"}"
                    },
                    backdrop = backdrop,
                    trophy = true,
                )
                Spacer(Modifier.height(18.dp))
                FigureGrid(Recaps.seriesFigures(recap))
                if (recap.seasons.size >= 2) {
                    Spacer(Modifier.height(18.dp))
                    SeasonBars(recap)
                }
                recap.fastest?.let { fastest ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Fastest: Season ${fastest.season} · ${Recaps.paceLabel(fastest.pace)} over " +
                            "${fastest.spanDays} day${if (fastest.spanDays == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CvTheme.colors.gold,
                    )
                }
                recap.top?.let {
                    Spacer(Modifier.height(18.dp))
                    TopEpisodeCard(it, delay = 900)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (celebrate) Confetti(play = true)
    }
}

@Composable
private fun RecapHeader(
    kicker: String,
    title: String,
    heading: String,
    dates: String,
    line: String,
    backdrop: String?,
    trophy: Boolean = false,
) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.XLarge)
    ) {
        CvImage(
            Img.backdrop(backdrop), null,
            Modifier.matchParentSize().graphicsLayer { alpha = 0.45f },
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(Palette.Red.copy(alpha = 0.35f), colors.ink.copy(alpha = 0.85f), colors.ink.copy(alpha = 0.95f)),
                    )
                )
        )
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trophy) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = colors.gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(kicker, style = KickerStyle, color = if (trophy) colors.gold else Palette.Red2)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(heading, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            if (dates.isNotBlank()) {
                Text(dates, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            Text(line, style = MaterialTheme.typography.labelLarge, color = colors.gold)
        }
    }
}

/** The figures, three to a row, each dealing in after the one before. */
@Composable
private fun FigureGrid(figures: List<Pair<String, String>>) {
    val colors = CvTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        figures.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, (label, value) ->
                    val index = rowIndex * 3 + columnIndex
                    val arrive = rememberArrival(1f, 120 + index * 90, 560)
                    Column(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = arrive
                                translationY = (1f - arrive) * 28f
                                val grow = 0.92f + 0.08f * arrive
                                scaleX = grow
                                scaleY = grow
                            }
                            .glass(CvShape.Large, strength = 0.8f)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(label.uppercase(), style = KickerStyle, color = colors.text3, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        CountUpString(
                            value,
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            color = colors.text,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A bar per season, its height the episodes watched, rising in order. The
 * fastest season is gold - the one fact about a run people actually remember.
 */
@Composable
private fun SeasonBars(recap: SeriesRecap) {
    val colors = CvTheme.colors
    val shown = recap.seasons.takeLast(20)
    val top = (shown.maxOfOrNull { it.episodes } ?: 1).coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(CvShape.Large, strength = 0.6f)
            .padding(14.dp)
    ) {
        Text("SEASON BY SEASON", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            shown.forEachIndexed { index, season ->
                val grown = rememberArrival(season.episodes.toFloat() / top, 300 + index * 70, 700)
                val fastest = recap.fastest?.season == season.season
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(grown.coerceAtLeast(0.04f))
                            .clip(CvShape.Tiny)
                            .background(
                                if (fastest) Brush.verticalGradient(listOf(colors.gold, Palette.Gold2))
                                else Brush.verticalGradient(listOf(Palette.Red2, Palette.Red.copy(alpha = 0.55f)))
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "S${season.season}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fastest) colors.gold else colors.text3,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopEpisodeCard(top: TopEpisode, delay: Int) {
    val colors = CvTheme.colors
    val arrive = rememberArrival(1f, delay, 600)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 24f }
            .glass(CvShape.Large, strength = 0.8f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(118.dp).height(66.dp).clip(CvShape.Small).background(colors.surface2)) {
            CvImage(Img.still(top.still), top.name, Modifier.matchParentSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("BEST EPISODE YOU WATCHED", style = KickerStyle, color = colors.text3)
            Text(
                "S${top.season} E${top.number} · ${top.name}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = colors.gold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "%.1f".format(java.util.Locale.US, top.rating),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = colors.gold,
                )
            }
        }
    }
}
