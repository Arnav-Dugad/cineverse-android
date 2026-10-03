package com.cineverse.app.feature.franchise

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.GrowBar
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.franchise.CollectionInfo
import com.cineverse.app.data.franchise.Franchises

/**
 * "Part of the Alien Collection" on a film's page — a link that now says how
 * far through it you are, and shows the whole run as a strip with this film
 * outlined in it, so where it falls in the series is visible before you tap.
 */
@Composable
fun CollectionBanner(
    info: CollectionInfo,
    library: Library,
    currentId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val progress = Franchises.progress(info.parts, Franchises.watchedFilmIds(library))
    if (progress.total < 2) return
    val ordered = Franchises.releaseOrdered(info.parts)

    Box(modifier.fillMaxWidth().clip(CvShape.XLarge)) {
        info.backdrop?.let { art ->
            CvImage(Img.backdrop(art), null, Modifier.matchParentSize().graphicsLayer { alpha = 0.18f })
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(CvShape.XLarge, strength = 0.8f)
                .clickableNoRipple { haptics?.play(Haptic.Tap); onClick() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PART OF", style = KickerStyle, color = colors.text3)
                    Text(
                        info.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Open the collection", tint = colors.text3)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ordered.take(8).forEach { part ->
                    val seen = part.id in progress.seenIds
                    val here = part.id == currentId
                    Box(
                        Modifier
                            .width(30.dp)
                            .height(45.dp)
                            .clip(CvShape.Tiny)
                            .then(if (here) Modifier.border(2.dp, Palette.Red2, CvShape.Tiny) else Modifier)
                    ) {
                        CvImage(
                            Img.tiny(part.poster), part.title,
                            Modifier.matchParentSize().graphicsLayer { alpha = if (seen || here) 1f else 0.45f },
                        )
                        if (seen) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(13.dp)
                                    .clip(CvShape.Circle)
                                    .background(colors.green),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(9.dp))
                            }
                        }
                    }
                }
                if (ordered.size > 8) {
                    Text(
                        "+${ordered.size - 8}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GrowBar(progress.fraction, color = if (progress.complete) colors.gold else Palette.Red2, height = 5.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                buildList {
                    add(progress.label)
                    progress.nextUp?.takeIf { it.id != currentId }?.let { add("next: ${it.title}") }
                }.filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
