package com.cineverse.app.core.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.data.ai.Spot
import com.cineverse.app.data.model.MediaItem

/**
 * Gemini's spotlight on a person or a studio: the morphing loader while it
 * reads their work, then a short take written for you - typed out - and
 * "Start with" posters, three of theirs you have not seen.
 */
@Composable
fun SpotlightCard(spot: Spot?, loading: Boolean, onOpen: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    if (spot == null && !loading) return
    val colors = CvTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .geminiGlow(corner = 24.dp, width = 1.3.dp, pulse = loading)
            .clip(CvShape.XLarge)
            .background(Brush.linearGradient(listOf(Palette.Purple.copy(alpha = 0.12f), colors.text.copy(alpha = 0.03f))))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) GeminiLoader(size = 18.dp)
            else Icon(Icons.Rounded.AutoAwesome, null, tint = GeminiColors[1], modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "GEMINI IS READING THEIR WORK" else "FOR YOU, BY GEMINI", style = KickerStyle, color = colors.text3)
        }
        if (spot != null) {
            Spacer(Modifier.height(10.dp))
            var writing by remember(spot) { mutableStateOf(true) }
            LaunchedEffect(spot) { writing = false }
            TypewriterText(spot.take, writing = writing, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            if (spot.start.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("START WITH", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(spot.start, key = { _, it -> it.key }) { index, item ->
                        val shown = rememberArrival(1f, delayMillis = 300 + index * 120, durationMillis = 480)
                        Box(Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 18f }) {
                            PosterCard(item = item, onOpen = onOpen, width = 100.dp)
                        }
                    }
                }
            }
        }
    }
}
