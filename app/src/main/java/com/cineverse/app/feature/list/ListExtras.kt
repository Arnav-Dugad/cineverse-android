package com.cineverse.app.feature.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.model.UserList
import kotlin.math.roundToInt

/**
 * A list's showcase, the website's: four of its posters side by side as a
 * cover, its name over them, and the numbers that describe it underneath.
 * Shuffle cover deals the next four, and the choice is kept on this device.
 */
@Composable
fun ListShowcaseCard(show: ListShowcase, onShuffle: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val arrive = rememberArrival(1f, 0, 560)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 16f }
            .padding(bottom = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(CvShape.Large)
                .background(colors.surface2),
        ) {
            AnimatedContent(
                show.cover,
                transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(260)) },
                label = "listCover",
            ) { cover ->
                Row(Modifier.fillMaxSize()) {
                    for (index in 0 until 4) {
                        val path = cover.getOrNull(index)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(colors.surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (path != null) CvImage(Img.poster(path), null, Modifier.fillMaxSize())
                            // An empty slot, as the website draws one: a spark,
                            // not a grey hole.
                            else Icon(
                                Icons.Rounded.AutoAwesome,
                                null,
                                tint = colors.text3.copy(alpha = 0.35f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        )
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("CURATED COLLECTION", style = KickerStyle, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (show.list.hasPin) {
                        Icon(Icons.Rounded.Lock, "Locked list", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        show.list.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${show.count} title${if (show.count == 1) "" else "s"} · ${show.films} film${if (show.films == 1) "" else "s"} · ${show.series} series",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            if (show.canShuffle) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CvShape.Pill)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Select); onShuffle() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shuffle, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle cover", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Insight("Average rating", if (show.avgRating == "-") "-" else "${show.avgRating}/10", Modifier.weight(1f))
            Insight("Average runtime", show.avgRuntime, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Insight("Top genres", show.topGenres, Modifier.weight(1.4f))
            Insight("Years", show.years, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Insight(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(
        modifier
            .clip(CvShape.Medium)
            .background(colors.text.copy(alpha = 0.04f))
            .border(1.dp, colors.hairline, CvShape.Medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Your lists, in your order: hold a handle and drag. The order is the
 * website's `order` field, so the laptop shows the same.
 */
@Composable
fun ArrangeListsSheet(
    lists: List<UserList>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val order = remember { mutableStateListOf<UserList>().apply { addAll(lists) } }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var changed by remember { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }

    fun finish() {
        if (changed) onSave(order.map { it.id })
        onDismiss()
    }

    CvSheet(onDismiss = ::finish) {
        Text("Arrange your lists", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(
            "Hold a handle and drag. The order follows you to the website.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(order, key = { _, list -> list.id }) { index, list ->
                val lifted = dragging == list.id
                val lift by animateFloatAsState(if (lifted) 1f else 0f, label = "listLift")
                Row(
                    Modifier
                        .then(if (lifted) Modifier else Modifier.animateItem())
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else 0f
                            val grow = 1f + 0.03f * lift
                            scaleX = grow
                            scaleY = grow
                            shadowElevation = 14f * lift
                            shape = CvShape.Large
                            clip = true
                        }
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(CvShape.Large)
                        .background(if (lifted) colors.surface2 else colors.text.copy(alpha = 0.04f))
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.text3, modifier = Modifier.width(26.dp))
                    if (list.hasPin) {
                        Icon(Icons.Rounded.Lock, "Locked", tint = colors.text3, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        list.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(44.dp)
                            .pointerInput(list.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = list.id; offset = 0f; haptics?.play(Haptic.Peek) },
                                    onDragEnd = { dragging = null; offset = 0f; haptics?.play(Haptic.Drop) },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val from = order.indexOfFirst { it.id == list.id }
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    val to = (from + (offset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    if (to != from) {
                                        order.add(to, order.removeAt(from))
                                        offset -= (to - from) * rowPx
                                        changed = true
                                        haptics?.play(Haptic.Detent)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.DragHandle, "Hold and drag to move", tint = colors.text2)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CvButton("Done", ::finish, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}
