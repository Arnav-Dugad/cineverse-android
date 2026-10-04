package com.cineverse.app.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.data.charts.ChartRun

/**
 * Where this title sat in the weekly Top 10, week by week: a line with #1 at
 * the top, dropping to the floor in weeks it was out, drawn in from the
 * left - and the headline numbers above it. Only the weeks the app has seen
 * the chart (it keeps its own record, as TMDB keeps no history).
 */
@Composable
fun ChartHistoryCard(run: ChartRun, since: String?, modifier: Modifier = Modifier) {
    if (!run.any) return
    val colors = CvTheme.colors
    val draw = remember(run) { Animatable(0f) }
    LaunchedEffect(run) { draw.animateTo(1f, tween(1000)) }
    val measurer = rememberTextMeasurer()
    val weeksIn = run.ranked.size
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
            Icon(Icons.Rounded.EmojiEvents, null, tint = colors.gold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("IN THE TOP 10", style = KickerStyle, color = colors.text3)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildList {
                if (run.current) run.now?.let { add("#$it this week") } ?: add("Out of the Top 10 this week")
                add("$weeksIn week${if (weeksIn == 1) "" else "s"} in")
                run.best?.let { add("peaked at #$it") }
            }.joinToString("  ·  "),
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
        )
        if (run.weeks.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val left = 24.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = size.height - 16.dp.toPx()
                val floor = bottom
                val width = size.width - left - 6.dp.toPx()
                val n = run.weeks.size
                fun x(i: Int) = left + width * (i.toFloat() / (n - 1).coerceAtLeast(1))
                fun y(rank: Int?) = if (rank == null) floor else top + (bottom - top - 10.dp.toPx()) * ((rank - 1) / 9f)
                for (rank in listOf(1, 5, 10)) {
                    val at = y(rank)
                    drawLine(colors.text.copy(alpha = 0.06f), Offset(left, at), Offset(size.width, at), 1.dp.toPx())
                    drawText(measurer, "#$rank", Offset(0f, at - 7.sp.toPx()), style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = colors.text3))
                }
                val path = Path()
                run.weeks.forEachIndexed { i, (_, rank) -> if (i == 0) path.moveTo(x(i), y(rank)) else path.lineTo(x(i), y(rank)) }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * draw.value, partial, true)
                drawPath(partial, Palette.Gold, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                run.weeks.forEachIndexed { i, (_, rank) ->
                    if (i <= (n - 1) * draw.value + 0.01f) {
                        drawCircle(colors.ink, 4.4.dp.toPx(), Offset(x(i), y(rank)))
                        drawCircle(if (rank == null) colors.text3 else Palette.Gold, 3.dp.toPx(), Offset(x(i), y(rank)))
                    }
                }
                val first = run.weeks.first().first.substringAfter("-")
                val last = run.weeks.last().first.substringAfter("-")
                drawText(measurer, first, Offset(left, bottom + 3.dp.toPx()), style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = colors.text3))
                val laid = measurer.measure(last, androidx.compose.ui.text.TextStyle(fontSize = 9.sp))
                drawText(measurer, last, Offset(size.width - laid.size.width, bottom + 3.dp.toPx()), style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = colors.text3))
            }
        }
        if (since != null) {
            Spacer(Modifier.height(6.dp))
            Text("CineVerse has kept the chart since $since", style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}
