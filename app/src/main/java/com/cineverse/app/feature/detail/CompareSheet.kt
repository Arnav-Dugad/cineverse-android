package com.cineverse.app.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.GeminiColors
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.geminiGlow
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.ai.Comparison
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.TitleDetail

/** Where "Compare with..." has got to. */
sealed interface CompareState {
    data object Picking : CompareState
    data class Comparing(val other: MediaItem) : CompareState
    data class Done(val other: MediaItem, val result: Comparison) : CompareState
    data class Failed(val other: MediaItem) : CompareState
}

/** The entry on the title page: one line, in Gemini's light. */
@Composable
fun CompareCard(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .geminiGlow(corner = 18.dp, width = 1.2.dp)
            .clip(CvShape.Large)
            .background(colors.text.copy(alpha = 0.05f))
            .clickableNoRipple { haptics?.play(Haptic.Tap); onOpen() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CompareArrows, null, tint = GeminiColors[1], modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Compare with…", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text("Pick another title and Gemini sets them side by side", style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
    }
}

/**
 * The comparison: pick the other title (from search, or from what is like
 * this one), then the two posters face each other over a "vs", the six
 * rows arrive one by one, and the verdict - for you - lights up the poster
 * it picks.
 */
@Composable
fun CompareSheet(
    detail: TitleDetail,
    state: CompareState,
    search: suspend (String) -> List<MediaItem>,
    onPick: (MediaItem) -> Unit,
    onAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    CvSheet(onDismiss = onDismiss) {
        AnimatedContent(
            state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "compare",
        ) { current ->
            when (current) {
                CompareState.Picking -> Picker(detail, search, onPick)
                is CompareState.Comparing -> Column {
                    Faces(detail, current.other, pick = -2, thinking = true)
                    Spacer(Modifier.height(18.dp))
                    repeat(6) {
                        Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(34.dp).clip(CvShape.Medium).shimmer())
                    }
                    Spacer(Modifier.height(12.dp))
                }
                is CompareState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Faces(detail, current.other, pick = -2)
                    Spacer(Modifier.height(18.dp))
                    Text("Gemini didn't answer just now.", style = MaterialTheme.typography.bodyMedium, color = colors.text2)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Pick another",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text,
                        modifier = Modifier.clip(CvShape.Pill).border(1.dp, colors.hairline, CvShape.Pill).clickableNoRipple(onAgain).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                is CompareState.Done -> Result(detail, current.other, current.result, onAgain)
            }
        }
    }
}

@Composable
private fun Picker(detail: TitleDetail, search: suspend (String) -> List<MediaItem>, onPick: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { found = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        found = runCatching { search(query.trim()) }.getOrDefault(emptyList()).filter { it.key != detail.key && it.hasArt }.take(12)
    }
    val shown = if (query.trim().length >= 2) found else detail.recommendations.filter { it.hasArt }.take(12)
    Column {
        Text("Compare ${detail.title} with…", style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CvShape.Pill)
                .background(colors.text.copy(alpha = 0.06f))
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = colors.text3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(Palette.Red2),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search a film or series", style = MaterialTheme.typography.bodyLarge, color = colors.text3)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (query.isBlank() && shown.isNotEmpty()) {
            Text("OR ONE LIKE IT", style = KickerStyle, color = colors.text3, modifier = Modifier.padding(vertical = 6.dp))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(shown, key = { it.key }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CvShape.Medium)
                        .clickableNoRipple { haptics?.play(Haptic.Select); onPick(item) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CvImage(Img.poster(item.posterPath), item.title, Modifier.width(44.dp).height(66.dp).clip(CvShape.Small))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(item.year.takeIf { it.isNotBlank() }, if (item.type == com.cineverse.app.data.model.MediaType.Tv) "Series" else "Film").joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.text3,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The two posters, facing; the picked one glows and lifts. [pick] -2 means not decided yet. */
@Composable
private fun Faces(detail: TitleDetail, other: MediaItem, pick: Int, thinking: Boolean = false) {
    val colors = CvTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Poster(Img.poster(detail.posterPath), detail.title, picked = pick == 0)
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.text.copy(alpha = 0.08f))
                .border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (thinking) com.cineverse.app.core.ui.GeminiLoader(size = 30.dp)
            else Text("vs", style = MaterialTheme.typography.titleSmall, color = colors.text2)
        }
        Poster(Img.poster(other.posterPath), other.title, picked = pick == 1)
    }
}

@Composable
private fun Poster(url: String?, title: String, picked: Boolean) {
    val colors = CvTheme.colors
    val lift = androidx.compose.animation.core.animateFloatAsState(if (picked) 1f else 0f, label = "comparePick").value
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
        Box(
            Modifier
                .graphicsLayer { scaleX = 1f + 0.06f * lift; scaleY = scaleX; translationY = -8f * lift }
                .then(if (picked) Modifier.geminiGlow(corner = 14.dp, width = 2.dp) else Modifier)
                .width(104.dp)
                .height(156.dp)
                .clip(CvShape.Medium)
                .background(colors.surface2),
        ) {
            CvImage(url, title, Modifier.width(104.dp).height(156.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Result(detail: TitleDetail, other: MediaItem, result: Comparison, onAgain: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    LaunchedEffect(result) { haptics?.play(Haptic.Success) }
    Column {
        Faces(detail, other, pick = result.pick)
        Spacer(Modifier.height(16.dp))
        result.rows.forEachIndexed { index, row ->
            val shown = rememberArrival(1f, delayMillis = 120 + index * 110, durationMillis = 420)
            Column(Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 14f }.padding(vertical = 6.dp)) {
                Text(row.aspect.uppercase(), style = KickerStyle, color = colors.text3, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(row.first, style = MaterialTheme.typography.bodyMedium, color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(18.dp).background(colors.hairline))
                    Text(row.second, style = MaterialTheme.typography.bodyMedium, color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val verdict = rememberArrival(1f, delayMillis = 120 + result.rows.size * 110, durationMillis = 520)
        Column(
            Modifier
                .graphicsLayer { alpha = verdict; translationY = (1f - verdict) * 14f }
                .fillMaxWidth()
                .geminiGlow(corner = 18.dp, width = 1.2.dp)
                .clip(CvShape.Large)
                .background(colors.text.copy(alpha = 0.05f))
                .padding(14.dp),
        ) {
            Text("FOR YOU", style = KickerStyle, color = GeminiColors[1])
            Spacer(Modifier.height(4.dp))
            Text(result.verdict, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Compare with another",
            style = MaterialTheme.typography.labelLarge,
            color = colors.text2,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(CvShape.Pill)
                .border(1.dp, colors.hairline, CvShape.Pill)
                .clickableNoRipple(onAgain)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
    }
}
