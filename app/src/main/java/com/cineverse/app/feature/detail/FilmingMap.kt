package com.cineverse.app.feature.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.places.Place
import kotlin.math.PI
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * "Where it was filmed": the places on a small map, framed to fit them all,
 * each a pin that drops in one after another, and their names underneath -
 * a tap on a name opens it in your maps app. Map tiles are CARTO's, drawn
 * from OpenStreetMap, dark or light with the theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmingMap(places: List<Place>, modifier: Modifier = Modifier) {
    if (places.isEmpty()) return
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val dark = colors.isDark
    Column(modifier.padding(top = 20.dp)) {
        SectionHeader("Where it was filmed")
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            Modifier
                .padding(horizontal = ScreenPadding)
                .fillMaxWidth()
                .height(200.dp)
                .clip(CvShape.XLarge)
                .background(if (dark) Color(0xFF0E0E12) else Color(0xFFF2F2F2))
                .border(1.dp, colors.hairline, CvShape.XLarge),
        ) {
            val density = LocalDensity.current
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val tile = with(density) { 256.dp.toPx() }
            val frame = remember(places, width, height) { frameFor(places, width, height, tile) }

            // The tiles that cover the frame.
            val count = 2.0.pow(frame.zoom).toInt()
            val firstX = floor(frame.left / tile).toInt()
            val lastX = floor((frame.left + width) / tile).toInt()
            val firstY = floor(frame.top / tile).toInt().coerceAtLeast(0)
            val lastY = floor((frame.top + height) / tile).toInt().coerceAtMost(count - 1)
            val style = if (dark) "dark_all" else "light_all"
            val tileDp = with(density) { tile.toDp() }
            for (tx in firstX..lastX) for (ty in firstY..lastY) {
                val wrapped = ((tx % count) + count) % count
                AsyncImage(
                    model = "https://basemaps.cartocdn.com/$style/${frame.zoom}/$wrapped/$ty@2x.png",
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset((tx * tile - frame.left).toInt(), (ty * tile - frame.top).toInt()) }
                        .size(tileDp),
                )
            }

            // The pins, dropping in one by one.
            val drops = remember(places) { places.map { Animatable(0f) } }
            LaunchedEffect(places) {
                drops.forEachIndexed { index, drop ->
                    launch {
                        kotlinx.coroutines.delay(300L + 90L * index.coerceAtMost(12))
                        drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                places.forEachIndexed { index, place ->
                    val drop = drops[index].value
                    if (drop <= 0f) return@forEachIndexed
                    val at = Offset(
                        (lonToX(place.lon, frame.zoom, tile) - frame.left).toFloat(),
                        (latToY(place.lat, frame.zoom, tile) - frame.top).toFloat(),
                    )
                    val lift = (1f - drop) * -18.dp.toPx()
                    val point = at + Offset(0f, lift)
                    val radius = (if (place.broad) 9.dp else 6.dp).toPx()
                    drawCircle(
                        Brush.radialGradient(listOf(Palette.Red.copy(alpha = 0.45f * drop), Color.Transparent), center = point, radius = radius * 3.2f),
                        radius = radius * 3.2f, center = point,
                    )
                    drawCircle(Color.White.copy(alpha = drop), radius * 0.72f, point)
                    drawCircle(Palette.Red2.copy(alpha = drop), radius * 0.48f, point)
                }
            }
            Text(
                "© OpenStreetMap © CARTO",
                fontSize = 8.sp,
                color = colors.text3,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(colors.ink.copy(alpha = 0.55f), CvShape.Pill)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (place in places.take(14)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple {
                            haptics?.play(Haptic.Tap)
                            val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private data class Frame(val zoom: Int, val left: Double, val top: Double)

private fun lonToX(lon: Double, zoom: Int, tile: Float): Double = (lon + 180.0) / 360.0 * tile * 2.0.pow(zoom)

private fun latToY(lat: Double, zoom: Int, tile: Float): Double {
    val phi = lat.coerceIn(-85.0, 85.0) * PI / 180.0
    return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0 * tile * 2.0.pow(zoom)
}

/** The closest zoom that keeps every place inside the frame, with a margin. */
private fun frameFor(places: List<Place>, width: Float, height: Float, tile: Float): Frame {
    var zoom = 1
    for (z in 11 downTo 0) {
        val xs = places.map { lonToX(it.lon, z, tile) }
        val ys = places.map { latToY(it.lat, z, tile) }
        if ((xs.max() - xs.min()) <= width * 0.78 && (ys.max() - ys.min()) <= height * 0.66) { zoom = z; break }
    }
    // One place on its own is shown in its region, not on a street map.
    if (places.size == 1) zoom = 5
    val xs = places.map { lonToX(it.lon, zoom, tile) }
    val ys = places.map { latToY(it.lat, zoom, tile) }
    val cx = (xs.max() + xs.min()) / 2
    val cy = (ys.max() + ys.min()) / 2
    return Frame(zoom, cx - width / 2, cy - height / 2)
}
