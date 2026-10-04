package com.cineverse.app.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.scores.SeasonScores

/**
 * The season as a curve: each episode's score (IMDb's, or TVmaze's when
 * there is no OMDb key) as a gold line, and yours, where you have scored
 * episodes, as a red one on the same scale - where you and everyone else
 * part ways is the interesting bit. The lines draw themselves in from the
 * first episode.
 */
@Composable
fun SeasonCurve(
    season: Int,
    episodeCount: Int,
    scores: SeasonScores,
    progress: ShowProgress?,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val mine = (1..episodeCount).mapNotNull { number ->
        progress?.episodeRating(season, number)?.takeIf { it > 0 }?.let { number to it.toDouble() }
    }.toMap()
    if (scores.byEpisode.size < 3 && mine.size < 2) return
    val draw = remember(season, scores) { Animatable(0f) }
    LaunchedEffect(season, scores) { draw.animateTo(1f, tween(1100)) }
    val measurer = rememberTextMeasurer()
    val gold = Palette.Gold
    val red = Palette.Red2
    val all = scores.byEpisode.values + mine.values
    val low = ((all.minOrNull() ?: 5.0) - 0.4).coerceAtLeast(0.0)
    val high = ((all.maxOrNull() ?: 9.0) + 0.3).coerceAtMost(10.0)
    val count = maxOf(episodeCount, scores.byEpisode.keys.maxOrNull() ?: 0, mine.keys.maxOrNull() ?: 0)
    val best = scores.byEpisode.maxByOrNull { it.value }

    Column(
        modifier
            .padding(horizontal = ScreenPadding - 6.dp)
            .fillMaxWidth()
            .clip(CvShape.XLarge)
            .background(colors.text.copy(alpha = 0.035f))
            .border(1.dp, colors.hairline, CvShape.XLarge)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SEASON $season, EPISODE BY EPISODE", style = KickerStyle, color = colors.text3, modifier = Modifier.weight(1f))
            Legend(gold, scores.source.ifBlank { "Score" })
            if (mine.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Legend(red, "You")
            }
        }
        best?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Peak: episode ${it.key}, ${"%.1f".format(it.value)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text2,
            )
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val left = 26.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            val top = 6.dp.toPx()
            val width = size.width - left - 6.dp.toPx()
            fun x(number: Int) = left + width * (if (count <= 1) 0.5f else (number - 1f) / (count - 1f))
            fun y(score: Double) = (bottom - (bottom - top) * ((score - low) / (high - low))).toFloat()

            // Gridlines at whole scores.
            for (line in kotlin.math.ceil(low).toInt()..kotlin.math.floor(high).toInt()) {
                val at = y(line.toDouble())
                drawLine(colors.text.copy(alpha = 0.06f), Offset(left, at), Offset(size.width, at), 1.dp.toPx())
                drawText(measurer, "$line", Offset(0f, at - 7.sp.toPx()), style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.text3))
            }
            fun series(points: Map<Int, Double>, color: Color) {
                if (points.isEmpty()) return
                val sorted = points.entries.sortedBy { it.key }
                val path = Path()
                sorted.forEachIndexed { index, (number, score) ->
                    if (index == 0) path.moveTo(x(number), y(score)) else path.lineTo(x(number), y(score))
                }
                // Drawn in, from the first episode.
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * draw.value, partial, true)
                drawPath(partial, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                val shownTo = sorted.size * draw.value
                sorted.forEachIndexed { index, (number, score) ->
                    if (index <= shownTo) {
                        drawCircle(colors.ink, 4.4.dp.toPx(), Offset(x(number), y(score)))
                        drawCircle(color, 3.2.dp.toPx(), Offset(x(number), y(score)))
                    }
                }
            }
            series(scores.byEpisode, gold)
            series(mine, red)
            // Episode numbers along the foot, every one or every few.
            val step = if (count > 16) 4 else if (count > 8) 2 else 1
            for (number in 1..count step step) {
                val label = "$number"
                val laid = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontSize = 10.sp))
                drawText(measurer, label, Offset(x(number) - laid.size.width / 2f, bottom + 3.dp.toPx()), style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.text3))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Hold an episode to score it yourself.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CvTheme.colors.text2)
    }
}

/** Your score for one episode: ten numbers, tap one, tap it again to clear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeRateSheet(
    label: String,
    current: Int,
    published: Double?,
    source: String,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    CvSheet(onDismiss = onDismiss) {
        Text("Score this episode", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(
            label + (published?.let { "  ·  $source ${"%.1f".format(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text3,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (score in 1..10) {
                val on = score == current
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (on) Palette.Red2 else colors.text.copy(alpha = 0.07f))
                        .border(1.dp, if (on) Color.Transparent else colors.hairline, CircleShape)
                        .clickableNoRipple {
                            haptics?.play(if (on) Haptic.Untick else Haptic.Tick)
                            onRate(if (on) 0 else score)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$score", style = MaterialTheme.typography.titleMedium, color = if (on) Color.White else colors.text)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
