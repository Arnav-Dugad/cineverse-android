package com.cineverse.app.feature.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.places.Place
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * "Where it was filmed".
 *
 * The places on a map framed to fit them all, each a pin that drops in one
 * after another. Pins too close to tell apart at this zoom gather into one
 * with a count. Tap a pin, or a place's name below, and that pin bounces
 * again, a callout names it, and the map flies in to the place itself - with
 * "Open in Maps" on the callout and a globe button to go back out. Tiles are
 * OpenStreetMap's, asked for with the app's name as their policy requires,
 * and turned dark on the dark theme by inverting their lightness.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmingMap(places: List<Place>, modifier: Modifier = Modifier) {
    if (places.isEmpty()) return
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = colors.isDark
    var selected by remember(places) { mutableStateOf<Int?>(null) }
    var focused by remember(places) { mutableStateOf(false) }
    val drops = remember(places) { places.map { Animatable(0f) } }
    LaunchedEffect(places) {
        drops.forEachIndexed { index, drop ->
            launch {
                kotlinx.coroutines.delay(300L + 90L * index.coerceAtMost(12))
                drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
            }
        }
    }
    fun select(index: Int, zoom: Boolean) {
        haptics?.play(Haptic.Select)
        selected = index
        if (zoom) focused = true
        // The pin bounces once more: up, then down with a wobble.
        scope.launch {
            drops[index].snapTo(0.25f)
            drops[index].animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 380f))
        }
    }
    fun openInMaps(place: Place) {
        val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    Column(modifier.padding(top = 20.dp)) {
        SectionHeader("Where it was filmed", count = places.size)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            Modifier
                .padding(horizontal = ScreenPadding)
                .fillMaxWidth()
                .height(260.dp)
                .shadow(10.dp, CvShape.XLarge, clip = false)
                .clip(CvShape.XLarge)
                .background(if (dark) Color(0xFF0B0B10) else Color(0xFFEDEDED))
                .border(1.dp, colors.hairline, CvShape.XLarge),
        ) {
            val density = LocalDensity.current
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val tile = with(density) { 170.dp.toPx() }
            val focus = selected?.takeIf { focused }?.let { places[it] }
            val frame = remember(places, width, height, focus) {
                if (focus != null) frameAround(focus, width, height, tile) else frameFor(places, width, height, tile)
            }
            val night = remember(dark) {
                if (!dark) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix(
                        floatArrayOf(
                            -0.62f, -0.1f, -0.1f, 0f, 210f,
                            -0.1f, -0.62f, -0.1f, 0f, 210f,
                            -0.1f, -0.1f, -0.55f, 0f, 215f,
                            0f, 0f, 0f, 1f, 0f,
                        )
                    )
                )
            }
            val tileDp = with(density) { tile.toDp() }

            // The tiles, crossfading when the map flies to a place and back.
            Crossfade(frame, animationSpec = tween(520), label = "mapFrame") { f ->
                Box(Modifier.fillMaxSize()) {
                    val count = 2.0.pow(f.zoom).toInt()
                    val firstX = floor(f.left / tile).toInt()
                    val lastX = floor((f.left + width) / tile).toInt()
                    val firstY = floor(f.top / tile).toInt().coerceAtLeast(0)
                    val lastY = floor((f.top + height) / tile).toInt().coerceAtMost(count - 1)
                    for (tx in firstX..lastX) for (ty in firstY..lastY) {
                        val wrapped = ((tx % count) + count) % count
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(context)
                                .data("https://tile.openstreetmap.org/${f.zoom}/$wrapped/$ty.png")
                                .httpHeaders(
                                    coil3.network.NetworkHeaders.Builder()
                                        .set("User-Agent", "CineVerse/1.0 (https://cineverse.pages.dev)")
                                        .build()
                                )
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = night,
                            modifier = Modifier
                                .offset { IntOffset((tx * tile - f.left).toInt(), (ty * tile - f.top).toInt()) }
                                .size(tileDp),
                        )
                    }
                }
            }
            // A soft vignette, so the map sits in its frame rather than ending at it.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to (if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.35f)),
                    )
                )
            )

            // Pins - clustered where they would overlap - drawn on top.
            val measurer = rememberTextMeasurer()
            val clusterPx = with(density) { 22.dp.toPx() }
            val points = places.map { place ->
                Offset((lonToX(place.lon, frame.zoom, tile) - frame.left).toFloat(), (latToY(place.lat, frame.zoom, tile) - frame.top).toFloat())
            }
            val clusters = remember(points) {
                val groups = mutableListOf<MutableList<Int>>()
                points.forEachIndexed { i, p ->
                    val near = groups.firstOrNull { g -> hypot(points[g.first()].x - p.x, points[g.first()].y - p.y) < clusterPx }
                    if (near != null) near += i else groups += mutableListOf(i)
                }
                groups
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(clusters) {
                        detectTapGestures { tap ->
                            val hit = clusters.minByOrNull { g -> hypot(points[g.first()].x - tap.x, points[g.first()].y - tap.y) }
                                ?.takeIf { g -> hypot(points[g.first()].x - tap.x, points[g.first()].y - tap.y) < 34.dp.toPx() }
                            if (hit != null) select(hit.first(), zoom = hit.size > 1 || focused)
                            else if (selected != null) selected = null
                        }
                    },
            ) {
                for (group in clusters) {
                    val lead = group.first()
                    val drop = group.maxOf { drops[it].value }
                    if (drop <= 0f) continue
                    val lift = (1f - drop) * -18.dp.toPx()
                    val point = points[lead] + Offset(0f, lift)
                    val on = selected != null && selected in group
                    val radius = (if (group.size > 1) 11.dp else if (places[lead].broad) 8.dp else 6.dp).toPx() * (if (on) 1.25f else 1f)
                    drawCircle(
                        Brush.radialGradient(listOf(Palette.Red.copy(alpha = 0.5f * drop), Color.Transparent), center = point, radius = radius * 3.4f),
                        radius = radius * 3.4f, center = point,
                    )
                    drawCircle(Color.White.copy(alpha = drop), radius * 0.78f, point)
                    drawCircle((if (on) Palette.Gold else Palette.Red2).copy(alpha = drop), radius * 0.56f, point)
                    if (group.size > 1) {
                        val label = "${group.size}"
                        val laid = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontSize = 10.sp))
                        drawText(
                            measurer, label,
                            point - Offset(laid.size.width / 2f, laid.size.height / 2f),
                            style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White),
                        )
                    }
                }
            }

            // The callout over the chosen pin.
            selected?.let { index ->
                val point = points[index]
                val place = places[index]
                Box(
                    Modifier
                        .offset { IntOffset((point.x - 90.dp.toPx()).toInt().coerceIn(8, (width - 180.dp.toPx() - 8).toInt().coerceAtLeast(8)), (point.y - 74.dp.toPx()).toInt().coerceAtLeast(8)) }
                        .widthIn(max = 180.dp),
                ) {
                    Column(
                        Modifier
                            .shadow(8.dp, CvShape.Medium, clip = false)
                            .clip(CvShape.Medium)
                            .background(colors.surface2)
                            .border(1.dp, colors.hairline, CvShape.Medium)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(place.name, style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickableNoRipple { haptics?.play(Haptic.Tap); openInMaps(place) }) {
                            Icon(Icons.Rounded.Map, null, tint = colors.cyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open in Maps", style = MaterialTheme.typography.labelSmall, color = colors.cyan)
                        }
                    }
                }
            }

            // Back out to every place.
            if (focused) {
                val shown = com.cineverse.app.core.ui.rememberArrival(1f, durationMillis = 320)
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .androidxAlpha(shown)
                        .clip(CvShape.Pill)
                        .background(colors.ink.copy(alpha = 0.8f))
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Tap); focused = false; selected = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Public, null, tint = colors.text, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("All places", style = MaterialTheme.typography.labelMedium, color = colors.text)
                }
            }
            Text(
                "© OpenStreetMap contributors",
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
            places.take(16).forEachIndexed { index, place ->
                val on = selected == index
                Text(
                    place.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.ink else colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .background(if (on) Palette.Gold else Color.Transparent)
                        .border(1.dp, if (on) Color.Transparent else colors.hairline, CvShape.Pill)
                        .clickableNoRipple { select(index, zoom = true) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun Modifier.androidxAlpha(value: Float) =
    this.graphicsLayer { alpha = value; scaleX = 0.85f + 0.15f * value; scaleY = scaleX }

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

/** Close in on one place: a city at street-ish zoom, a region wider. */
private fun frameAround(place: Place, width: Float, height: Float, tile: Float): Frame {
    val zoom = if (place.broad) 5 else 12
    return Frame(zoom, lonToX(place.lon, zoom, tile) - width / 2, latToY(place.lat, zoom, tile) - height / 2)
}
