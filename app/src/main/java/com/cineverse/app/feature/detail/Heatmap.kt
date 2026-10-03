package com.cineverse.app.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.HeatCell
import com.cineverse.app.data.model.HeatMode
import com.cineverse.app.data.model.Heatmap

/**
 * The season heatmap.
 *
 * The website's best panel, and the one that answers "where does this show
 * peak?" and "how much of the good stuff have I seen?" without reading a single
 * number. Two colourings and a switch over either:
 *
 *  - **Rating**: TMDB's score in seven fixed bands, so a colour means the same
 *    thing on every show.
 *  - **Standouts**: each episode against its OWN season's average, so a strong
 *    episode in a weak season is as visible as one in a great season.
 *  - **Numbers**: the ratings printed in the squares, over either colouring.
 *    Not a third mode, because that would mean choosing between seeing the
 *    numbers and seeing the shape.
 */
@Composable
fun HeatmapPanel(
    heatmap: Heatmap?,
    loading: Boolean,
    expanded: Boolean,
    mode: HeatMode,
    numbers: Boolean,
    onToggle: () -> Unit,
    onMode: (HeatMode) -> Unit,
    onNumbers: () -> Unit,
    onOpenEpisode: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var selected by remember { mutableStateOf<HeatCell?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .glass(CvShape.XLarge)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { haptics?.play(Haptic.Select); onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CvShape.Medium).background(colors.glassStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GridView, null, tint = colors.text2, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text("Season heatmap", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    if (heatmap != null && !heatmap.isEmpty) {
                        "${heatmap.watched} of ${heatmap.aired} aired episodes seen"
                    } else "Every episode by rating, with the ones you've seen ticked",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val turn by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = Motion.snappy(),
                label = "chev",
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.text3,
                modifier = Modifier.rotate(turn),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.gentle()) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.snappy()) + fadeOut(Motion.fade(Motion.Quick)),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                when {
                    loading || heatmap == null -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Palette.Red2,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    heatmap.isEmpty -> Text(
                        "No episode ratings to show yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                    )

                    else -> {
                        Controls(mode, numbers, onMode, onNumbers)
                        Spacer(Modifier.height(14.dp))
                        Grid(
                            heatmap = heatmap,
                            mode = mode,
                            numbers = numbers,
                            selected = selected,
                            onSelect = { cell ->
                                // Touch: the first tap reads the episode out, the
                                // second opens it. A grid where one tap navigates
                                // is a grid you cannot explore.
                                if (selected?.season == cell.season && selected?.episode == cell.episode) {
                                    haptics?.play(Haptic.Tap)
                                    onOpenEpisode(cell.season, cell.episode)
                                } else {
                                    haptics?.play(Haptic.Detent)
                                    selected = cell
                                }
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        Readout(selected, heatmap)
                        Spacer(Modifier.height(14.dp))
                        Insights(heatmap, onOpenEpisode)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Ratings from TMDB; episodes with few votes can swing. " +
                                "Standouts compare each episode with its own season's average.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    mode: HeatMode,
    numbers: Boolean,
    onMode: (HeatMode) -> Unit,
    onNumbers: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CvShape.Pill)
                .background(colors.glassStrong)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (option in HeatMode.entries) {
                val active = option == mode
                Box(
                    Modifier
                        .clip(CvShape.Pill)
                        .background(if (active) colors.gold else Color.Transparent)
                        .clickableNoRipple { if (!active) { haptics?.play(Haptic.Select); onMode(option) } }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color(0xFF231A00) else colors.text3,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // The numbers are a switch over whichever colouring is showing.
        Row(
            Modifier
                .clip(CvShape.Pill)
                .background(if (numbers) colors.text.copy(alpha = 0.12f) else colors.glassStrong)
                .border(
                    1.dp,
                    if (numbers) colors.text.copy(alpha = 0.22f) else Color.Transparent,
                    CvShape.Pill,
                )
                .clickableNoRipple { haptics?.play(Haptic.Select); onNumbers() }
                .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(CvShape.Tiny)
                    .background(if (numbers) colors.gold else colors.glassStrong)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    "8.4",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (numbers) Color(0xFF231A00) else colors.text3,
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                "Numbers",
                style = MaterialTheme.typography.labelMedium,
                color = if (numbers) colors.text else colors.text3,
            )
        }
    }
}

@Composable
private fun Grid(
    heatmap: Heatmap,
    mode: HeatMode,
    numbers: Boolean,
    selected: HeatCell?,
    onSelect: (HeatCell) -> Unit,
) {
    val colors = CvTheme.colors
    // The squares grow for the numbers and shrink back; one animated size keeps
    // the whole grid on the compositor rather than re-laying it out per cell.
    val cellWidth by animateDpAsState(
        targetValue = if (numbers) 42.dp else if (heatmap.maxEpisodes > 24) 15.dp else 22.dp,
        animationSpec = Motion.dp(),
        label = "cellW",
    )
    val cellHeight by animateDpAsState(
        targetValue = if (numbers) 28.dp else if (heatmap.maxEpisodes > 24) 15.dp else 22.dp,
        animationSpec = Motion.dp(),
        label = "cellH",
    )

    // The first time a show's grid is opened the squares light up in the order
    // you watched them. It only runs once per open, because a decoration that
    // replays on every scroll is noise.
    var lit by remember(heatmap.rows.firstOrNull()?.season) { mutableStateOf(false) }
    LaunchedEffect(heatmap) { lit = true }

    // One scroll state shared by every row, so a twelve-episode season and a
    // ten-episode one stay aligned when you scroll sideways. The season label
    // and its average sit OUTSIDE the scroll: an average that slides off the
    // screen is an average you cannot read, which was the first version.
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in heatmap.rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S${row.season}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row == heatmap.strongest) colors.gold else colors.text3,
                    modifier = Modifier.width(26.dp),
                )
                Row(
                    Modifier.weight(1f).horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (cell in row.cells) {
                        Cell(
                            cell = cell,
                            mode = mode,
                            numbers = numbers,
                            width = cellWidth,
                            height = cellHeight,
                            isBest = heatmap.best === cell,
                            isSelected = selected?.season == cell.season && selected.episode == cell.episode,
                            lit = lit,
                            onClick = { onSelect(cell) },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (row.mean > 0) "%.1f".format(row.mean) else "–",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row == heatmap.strongest) colors.gold else colors.text2,
                    modifier = Modifier.width(30.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Legend(mode)
    }
}

@Composable
private fun Cell(
    cell: HeatCell,
    mode: HeatMode,
    numbers: Boolean,
    width: Dp,
    height: Dp,
    isBest: Boolean,
    isSelected: Boolean,
    lit: Boolean,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    val reduced = CvTheme.reducedMotion
    val fill = when {
        !cell.aired -> Color.Transparent
        !cell.rated -> Color.Transparent
        mode == HeatMode.Rating -> colors.heat[cell.band.coerceIn(0, colors.heat.lastIndex)]
        cell.deltaBand >= 0 -> DELTA_COLORS[cell.deltaBand.coerceIn(0, 6)]
        else -> colors.heatNone
    }
    // Lighting delay: in the order you watched them, capped so a 200-episode
    // show does not take half a minute to finish arriving.
    val delay = if (cell.order >= 0) minOf(cell.order * 26, 1800) else 1900
    val appear by animateFloatAsState(
        targetValue = if (lit || reduced) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 260,
            delayMillis = if (reduced) 0 else delay,
            easing = Motion.EaseOut,
        ),
        label = "lit",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.22f else 1f,
        animationSpec = Motion.lively(),
        label = "sel",
    )

    Box(
        Modifier
            .size(width = width, height = height)
            .alpha(if (reduced) 1f else appear)
            .scale(scale)
            .clip(CvShape.Tiny)
            .background(fill)
            .drawBehind {
                // An unrated episode is HATCHED and an unaired one is outlined:
                // colouring "no data" as "low" would be a lie the colour tells.
                if (cell.aired && !cell.rated) {
                    val step = 5.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(
                            colors.heatNone,
                            Offset(x, size.height),
                            Offset(x + size.height, 0f),
                            strokeWidth = 2f,
                        )
                        x += step
                    }
                } else if (!cell.aired) {
                    drawRect(
                        color = colors.heatNone,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, colors.text, CvShape.Tiny)
                    isBest -> Modifier.border(2.dp, colors.gold, CvShape.Tiny)
                    cell.watched && numbers -> Modifier.border(2.dp, Color.White.copy(alpha = 0.62f), CvShape.Tiny)
                    else -> Modifier
                }
            )
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (numbers) {
            Text(
                if (cell.rated) "%.1f".format(cell.rating) else "–",
                style = MaterialTheme.typography.labelSmall,
                color = if (cell.rated) Color(0xFF06120A) else colors.text3,
                maxLines = 1,
            )
        } else if (cell.watched) {
            // The tick draws itself along its own path.
            val draw by animateFloatAsState(
                targetValue = if (lit || reduced) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    delayMillis = if (reduced) 0 else delay + 120,
                    easing = Motion.EaseOut,
                ),
                label = "tick",
            )
            androidx.compose.foundation.Canvas(Modifier.size(width * 0.62f, height * 0.62f)) {
                val w = size.width
                val h = size.height
                val start = Offset(w * 0.12f, h * 0.52f)
                val mid = Offset(w * 0.40f, h * 0.82f)
                val end = Offset(w * 0.90f, h * 0.18f)
                val tint = Color(0xFF06120A)
                val stroke = (w * 0.16f).coerceAtLeast(2f)
                val first = 0.42f
                if (draw <= first) {
                    val t = (draw / first).coerceIn(0f, 1f)
                    drawLine(tint, start, lerp(start, mid, t), stroke, StrokeCap.Round)
                } else {
                    drawLine(tint, start, mid, stroke, StrokeCap.Round)
                    val t = ((draw - first) / (1f - first)).coerceIn(0f, 1f)
                    drawLine(tint, mid, lerp(mid, end, t), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Red through grey to green: below its season, about it, above it. */
private val DELTA_COLORS = listOf(
    Color(0xFF7F1D1D), Color(0xFFB45309), Color(0xFF8A6D3B),
    Color(0xFF4A4A58), Color(0xFF4D7C0F), Color(0xFF16A34A), Color(0xFF34D399),
)

@Composable
private fun Legend(mode: HeatMode) {
    val colors = CvTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (mode == HeatMode.Rating) "Under 6" else "Below",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val ramp = if (mode == HeatMode.Rating) colors.heat else DELTA_COLORS
            for (color in ramp) {
                Box(Modifier.size(width = 16.dp, height = 8.dp).clip(CvShape.Tiny).background(color))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (mode == HeatMode.Rating) "9+" else "Above",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}

@Composable
private fun Readout(cell: HeatCell?, heatmap: Heatmap) {
    val colors = CvTheme.colors
    if (cell == null) {
        Text(
            buildString {
                append("Tap a square to read its episode")
                heatmap.best?.let { append(". The peak is S${it.season} E${it.episode}, ${it.name}.") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.text3,
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(colors.glassStrong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(94.dp).height(54.dp).clip(CvShape.Small).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            CvImage(Img.still(cell.stillPath), cell.name, Modifier.fillMaxWidth().height(54.dp))
            Text(
                "S${cell.season} · E${cell.episode}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .clip(CvShape.Pill)
                    .background(Color(0xB306060B))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                cell.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    cell.airDate.takeIf { it.isNotBlank() },
                    cell.runtime.takeIf { it > 0 }?.let { "${it}m" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cell.rated) {
                    Text(
                        "★ %.1f".format(cell.rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.gold,
                    )
                    Text(
                        "  ${"%,d".format(cell.votes)} votes",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                } else {
                    Text(
                        if (cell.aired) "No rating yet" else "Not aired yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
            if (cell.delta != null) {
                Text(
                    if (cell.deltaBand == 3) "About its season average"
                    else "${if (cell.delta > 0) "+" else "−"}${"%.1f".format(kotlin.math.abs(cell.delta))} vs its season",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        cell.deltaBand == 3 -> colors.text3
                        cell.delta > 0 -> colors.green
                        else -> Palette.Red2
                    },
                )
            }
            if (cell.watched) {
                Text(
                    if (cell.watchedAt > 0) "Watched ${relativeDay(cell.watchedAt)}" else "Watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                )
            }
        }
    }
}

@Composable
private fun Insights(heatmap: Heatmap, onOpenEpisode: (Int, Int) -> Unit) {
    val colors = CvTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        heatmap.best?.let { best ->
            InsightCard(
                "Peak episode",
                "S${best.season} E${best.episode} · ${best.name}",
                "%.1f from %,d votes".format(best.rating, best.votes),
            )
        }
        if (heatmap.topCount > 0) {
            InsightCard(
                "The best of it",
                "${heatmap.topSeen} of the top ${heatmap.topCount}",
                "You've seen ${heatmap.watched} of ${heatmap.aired} aired episodes",
            )
        }
        heatmap.strongest?.takeIf { heatmap.rows.size > 1 }?.let { row ->
            InsightCard("Strongest season", row.name, "%.1f average".format(row.mean))
        }
        if (heatmap.gems.isNotEmpty()) {
            Text(
                "BEST YOU HAVEN'T SEEN",
                style = KickerStyle,
                color = colors.text3,
                modifier = Modifier.padding(top = 4.dp),
            )
            for (gem in heatmap.gems) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CvShape.Medium)
                        .background(colors.glassStrong)
                        .clickableNoRipple { onOpenEpisode(gem.season, gem.episode) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "S${gem.season} E${gem.episode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text2,
                    )
                    Text(
                        "  ${gem.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "★ %.1f".format(gem.rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(kicker: String, headline: String, detail: String) {
    val colors = CvTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Medium)
            .background(colors.glassStrong)
            .padding(12.dp)
    ) {
        Text(kicker.uppercase(), style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(4.dp))
        Text(
            headline,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.text3)
    }
}
