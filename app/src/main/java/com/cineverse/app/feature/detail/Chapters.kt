package com.cineverse.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple

/** A place on a title page the jump bar can take you to. */
data class Chapter(val label: String, val icon: ImageVector, val tab: DetailTab, val key: String)

/**
 * The title page's chapters: one row of chips under the tabs - Episodes,
 * Cast, Where to watch, Box office, Map - that switch to the right tab and
 * glide down to the part named. Only the parts this title has are offered.
 */
@Composable
fun ChapterBar(chapters: List<Chapter>, onJump: (Chapter) -> Unit, modifier: Modifier = Modifier) {
    if (chapters.size < 2) return
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = ScreenPadding, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (chapter in chapters) {
            Row(
                Modifier
                    .clip(CvShape.Pill)
                    .background(colors.ink.copy(alpha = 0.85f))
                    .border(1.dp, colors.hairline, CvShape.Pill)
                    .clickableNoRipple { haptics?.play(Haptic.Select); onJump(chapter) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(chapter.icon, null, tint = colors.text2, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(chapter.label, style = MaterialTheme.typography.labelMedium, color = colors.text2)
            }
        }
    }
}

/**
 * Scroll a lazy list until the item with [key] is in view, then glide it to
 * just under [topInset]. A lazy list knows only the keys it has laid out, so
 * it steps through the list a few items at a time until the key turns up.
 */
suspend fun LazyListState.scrollToKey(key: Any, topInset: Int) {
    fun found() = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    found()?.let { animateScrollBy(it.offset.toFloat() - topInset); return }
    val total = layoutInfo.totalItemsCount
    var index = firstVisibleItemIndex
    while (index < total) {
        index += 3
        scrollToItem(index.coerceAtMost(total - 1))
        found()?.let { item ->
            scrollToItem(item.index)
            animateScrollBy(-topInset.toFloat())
            return
        }
    }
    // Not further down: from the top.
    index = 0
    while (index < total) {
        scrollToItem(index)
        found()?.let { item ->
            scrollToItem(item.index)
            animateScrollBy(-topInset.toFloat())
            return
        }
        index += 3
    }
}

