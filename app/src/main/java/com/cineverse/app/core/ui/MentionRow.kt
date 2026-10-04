package com.cineverse.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.data.ai.Mentioned
import com.cineverse.app.data.model.MediaItem

/**
 * Everything a Gemini answer named, under it: the films and series as
 * posters, then the people as faces, each one tap from its page. They arrive
 * one after another, a quick stagger, once the answer is finished.
 */
@Composable
fun MentionRow(
    mentioned: Mentioned,
    onOpen: (MediaItem) -> Unit,
    onPerson: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
) {
    if (mentioned.isEmpty) return
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        itemsIndexed(mentioned.titles, key = { _, it -> "t_" + it.second.key }) { index, (_, item) ->
            val shown = rememberArrival(1f, delayMillis = index * 60, durationMillis = 460)
            Box(Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 16f }) {
                PosterCard(item = item, onOpen = onOpen, width = 92.dp)
            }
        }
        itemsIndexed(mentioned.people, key = { _, it -> "p_${it.second.id}" }) { index, (_, person) ->
            val shown = rememberArrival(1f, delayMillis = (mentioned.titles.size + index) * 60, durationMillis = 460)
            Column(
                Modifier
                    .width(78.dp)
                    .graphicsLayer { alpha = shown; translationY = (1f - shown) * 16f }
                    .clip(com.cineverse.app.core.design.CvShape.Medium)
                    .clickableNoRipple { haptics?.play(Haptic.Tap); onPerson(person.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(66.dp)
                        .border(1.5.dp, geminiBrushStatic, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(colors.surface2),
                ) {
                    CvImage(Img.profile(person.profilePath), person.name, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    person.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
