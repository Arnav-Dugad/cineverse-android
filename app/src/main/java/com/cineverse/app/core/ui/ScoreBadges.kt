package com.cineverse.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.data.scores.Scores

/**
 * IMDb, the Tomatometer, the audience meter and Metacritic.
 *
 * Each is a quiet pill: the source's own mark in its own colour, then the number
 * in the page's ink. The colour belongs to the MARK, never to the whole chip —
 * four fully-coloured lozenges in a row is four things shouting, and the point
 * of the row is to be read at a glance as one line of numbers.
 *
 * They arrive after the page has painted and spring in one after another, which
 * is also the honest thing to do: they come from three different services and
 * some titles only have one.
 */
@Composable
fun ScoreRow(
    scores: Scores,
    modifier: Modifier = Modifier,
    onImdb: (() -> Unit)? = null,
) {
    if (!scores.any) return
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var index = 0
        if (scores.imdb > 0) {
            ScorePill(index++, "IMDb rating ${scores.imdb} out of 10", onImdb) {
                Box(
                    Modifier
                        .height(17.dp)
                        .clip(CvShape.Tiny)
                        .background(Palette.ImdbYellow)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "IMDb",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B0B0F),
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                }
                ScoreValue(String.format("%.1f", scores.imdb))
            }
        }
        if (scores.rt > 0) {
            val fresh = scores.rt >= 60
            ScorePill(
                index++,
                "Rotten Tomatoes critics ${scores.rt} percent positive",
            ) {
                TomatoMark(fresh)
                ScoreValue("${scores.rt}%")
            }
        }
        if (scores.rtAudience > 0) {
            ScorePill(index++, "Rotten Tomatoes audience ${scores.rtAudience} percent") {
                PopcornMark(scores.rtAudience >= 60)
                ScoreValue("${scores.rtAudience}%")
            }
        }
        if (scores.metacritic > 0) {
            val tone = when {
                scores.metacritic >= 61 -> Palette.MetaGood
                scores.metacritic >= 40 -> Palette.MetaMixed
                else -> Palette.MetaPoor
            }
            ScorePill(index, "Metacritic ${scores.metacritic} out of 100") {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CvShape.Tiny)
                        .background(tone)
                )
                ScoreValue("${scores.metacritic}")
            }
        }
    }
}

@Composable
private fun ScorePill(
    index: Int,
    description: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CvTheme.colors
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 420,
            delayMillis = index * 70,
            easing = Motion.EaseOut,
        ),
        label = "score",
    )
    Row(
        Modifier
            .scale(if (CvTheme.reducedMotion) 1f else 0.94f + appear * 0.06f)
            .height(30.dp)
            .clip(CvShape.Pill)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.Pill)
            .let { if (onClick != null) it.clickableNoRipple(onClick) else it }
            .padding(start = 7.dp, end = 11.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) { content() }
}

@Composable
private fun ScoreValue(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = CvTheme.colors.text,
    )
}

/** A tomato when it is fresh, a splat when it is not. */
@Composable
private fun TomatoMark(fresh: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        if (fresh) {
            drawPath(
                PathParser().parsePathString(
                    "M8.6 3.1c2.6 0 4.7 2 4.7 4.7 0 2.9-2.1 5.3-4.9 5.3S3.5 10.7 3.5 7.8c0-2.6 2.1-4.7 4.7-4.7z"
                ).toPath().scaled(s),
                Palette.TomatoFresh,
            )
            drawPath(
                PathParser().parsePathString("M8.9 3c.2-1.1 1-2 2-2.4-.1 1.1-.8 2.1-2 2.4z")
                    .toPath().scaled(s),
                Palette.TomatoLeaf,
            )
        } else {
            drawPath(
                PathParser().parsePathString(
                    "M8 2.4l1.5 1.3 1.9-.6-.2 2 1.7 1-1.3 1.5.6 1.9-2 .2-1 1.7-1.5-1.3-1.9.6.2-2-1.7-1 1.3-1.5-.6-1.9 2-.2z"
                ).toPath().scaled(s),
                Palette.TomatoRotten,
            )
        }
    }
}

/** The audience's half: a full tub, or a tipped-over one. */
@Composable
private fun PopcornMark(liked: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val body = if (liked) "M4.2 6h7.6l-.9 7.2a.8.8 0 0 1-.8.7H5.9a.8.8 0 0 1-.8-.7L4.2 6z"
        else "M3.1 7.5 10 5.1l2.4 6.8a.8.8 0 0 1-.5 1l-4 1.4a.8.8 0 0 1-1-.5L3.1 7.5z"
        val puff = if (liked) "M5.4 5.3a1.5 1.5 0 0 1 .3-2.5 1.6 1.6 0 0 1 2.3-1 1.6 1.6 0 0 1 2.4 1 1.5 1.5 0 0 1 .2 2.5H5.4z"
        else "M3.4 6.8 2 3.2l3.8 1.1-2.4 2.5z"
        val tone = if (liked) Palette.PopcornFull else Color(0xFF59A14F)
        val puffTone = if (liked) Color(0xFFFFE0A3) else Color(0xFF8FBF7A)
        drawPath(PathParser().parsePathString(body).toPath().scaled(s), tone)
        drawPath(PathParser().parsePathString(puff).toPath().scaled(s), puffTone)
    }
}

/** The marks are drawn in a 16-unit box; this puts them in the canvas's pixels. */
private fun Path.scaled(factor: Float): Path {
    val matrix = android.graphics.Matrix().apply { setScale(factor, factor) }
    val out = android.graphics.Path(this.asAndroidPath())
    out.transform(matrix)
    return out.asComposePath()
}

private fun Path.asAndroidPath(): android.graphics.Path =
    (this as androidx.compose.ui.graphics.AndroidPath).internalPath

private fun android.graphics.Path.asComposePath(): Path =
    androidx.compose.ui.graphics.AndroidPath(this)
