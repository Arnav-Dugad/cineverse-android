package com.cineverse.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Palette

/**
 * A chart position, drawn rather than typed: a heavy, tightly-set numeral
 * outlined in a stroke that fades from bright at the top to nothing at the
 * foot, over a faint fill. It reads as a cut-out in the page rather than a
 * label printed on it, and the top three carry a thread of gold in the stroke.
 */
@Composable
fun RankNumeral(rank: Int, height: Dp, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val size = with(LocalDensity.current) { (height * 1.08f).toSp() }
    val top = if (rank <= 3) Palette.Gold else colors.text
    val base = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.09).em,
        lineHeight = size,
    )
    Box(modifier.height(height), contentAlignment = Alignment.BottomStart) {
        // The fill: barely there, so the outline does the drawing.
        Text(
            "$rank",
            style = base.copy(
                brush = Brush.verticalGradient(listOf(top.copy(alpha = 0.16f), Color.Transparent)),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            "$rank",
            style = base.copy(
                brush = Brush.verticalGradient(
                    0f to top.copy(alpha = 0.95f),
                    0.65f to top.copy(alpha = 0.45f),
                    1f to top.copy(alpha = 0.06f),
                ),
                drawStyle = Stroke(width = with(LocalDensity.current) { 2.2.dp.toPx() }, join = StrokeJoin.Round),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One place in a Top 10 rail: the numeral, with the poster laid over its
 * right-hand side. "10" is wider than "1", so the overlap is measured per rank
 * and every poster sits the same distance from the numeral it belongs to.
 */
@Composable
fun TopTenCard(
    rank: Int,
    cardWidth: Dp,
    poster: @Composable (Modifier) -> Unit,
) {
    val cardHeight = cardWidth * 1.5f
    val numeral = if (rank >= 10) cardWidth * 0.92f else cardWidth * 0.56f
    val overlap = cardWidth * 0.18f
    Box(Modifier.width(numeral + cardWidth - overlap)) {
        RankNumeral(
            rank,
            height = cardHeight * 0.86f,
            modifier = Modifier.align(Alignment.TopStart).padding(top = cardHeight * 0.2f),
        )
        poster(Modifier.align(Alignment.TopStart).padding(start = numeral - overlap))
    }
}
