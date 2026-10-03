package com.cineverse.app.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.Img
import com.cineverse.app.data.model.ContinueRow
import kotlin.math.roundToInt

/**
 * Arrange Continue Watching, the website's drag-to-reorder for a phone: hold
 * a row by its handle and drag. The rows it passes slide out of the way and a
 * detent clicks under the thumb at each one, and the order is kept on your
 * account, so the website shows it too.
 */
@Composable
fun ArrangeSheet(
    rows: List<ContinueRow>,
    onSave: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val order = remember { mutableStateListOf<ContinueRow>().apply { addAll(rows) } }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var changed by remember { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { 76.dp.toPx() }

    fun finish() {
        if (changed) onSave(order.map { it.item.key })
        onDismiss()
    }

    CvSheet(onDismiss = ::finish) {
        Text("Arrange Continue watching", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(
            "Hold a handle and drag. New episodes and countdowns stay at the front, and the order follows you to the website.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(order, key = { _, row -> row.item.key }) { index, row ->
                val lifted = dragging == row.item.key
                val lift by animateFloatAsState(if (lifted) 1f else 0f, label = "arrangeLift")
                Row(
                    Modifier
                        .then(if (lifted) Modifier else Modifier.animateItem())
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else 0f
                            val grow = 1f + 0.03f * lift
                            scaleX = grow
                            scaleY = grow
                            shadowElevation = 16f * lift
                            shape = CvShape.Large
                            clip = true
                        }
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(CvShape.Large)
                        .background(if (lifted) colors.surface2 else colors.text.copy(alpha = 0.04f))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text3,
                        modifier = Modifier.width(22.dp),
                    )
                    Box(Modifier.width(88.dp).height(50.dp).clip(CvShape.Small).background(colors.surface2)) {
                        CvImage(
                            Img.still(row.stillPath ?: row.item.backdropPath ?: row.item.posterPath),
                            row.item.title,
                            Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.item.title, style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .pointerInput(row.item.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = row.item.key
                                        offset = 0f
                                        haptics?.play(Haptic.Peek)
                                    },
                                    onDragEnd = { dragging = null; offset = 0f; haptics?.play(Haptic.Drop) },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val from = order.indexOfFirst { it.item.key == row.item.key }
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    val steps = (offset / rowPx).roundToInt()
                                    val to = (from + steps).coerceIn(0, order.lastIndex)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CvButton("Done", ::finish, modifier = Modifier.weight(1f))
            CvButton("Automatic order", { onReset(); onDismiss() }, primary = false)
        }
        Spacer(Modifier.height(8.dp))
    }
}
