package com.cineverse.app.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.geminiGlow
import com.cineverse.app.core.ui.sharedPoster
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem

/**
 * A sentence, answered: what search understood, as chips you can read at a
 * glance, and then either the titles it found or what it did.
 */
@Composable
fun AskPane(
    ask: AskUi,
    library: Library,
    onOpen: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    onPerson: (Int) -> Unit = {},
) {
    AnimatedContent(
        ask,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "askPane",
    ) { current ->
        when (current) {
            is AskUi.Thinking -> Thinking(current.heard)
            is AskUi.Results -> Results(current, library, onOpen, onDismiss)
            is AskUi.Did -> Did(current, onOpen, onDismiss)
            is AskUi.Found -> Found(current, onPerson, onOpen, onDismiss)
            is AskUi.Answered -> Answered(current, onDismiss)
        }
    }
}

@Composable
private fun Thinking(heard: String) {
    val colors = CvTheme.colors
    val loop = rememberInfiniteTransition(label = "askThinking")
    val sweep by loop.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    LazyVerticalGrid(
        columns = posterGridCells(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CvShape.Large)
                    .background(colors.text.copy(alpha = 0.04f))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                Palette.Purple.copy(alpha = 0.16f),
                                androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            start = androidx.compose.ui.geometry.Offset(sweep * 1200f - 300f, 0f),
                            end = androidx.compose.ui.geometry.Offset(sweep * 1200f + 300f, 200f),
                        )
                    )
                    .border(1.dp, colors.text.copy(alpha = 0.06f), CvShape.Large)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Purple2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Understanding", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "“$heard”",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                    color = colors.text,
                )
            }
        }
        items(6) { PosterSkeleton(width = posterCellWidth()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Results(
    result: AskUi.Results,
    library: Library,
    onOpen: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val chips = result.understood.split(" · ").filter { it.isNotBlank() }
    LazyVerticalGrid(
        columns = posterGridCells(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "understood", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .geminiGlow(result.byGemini)
                    .clip(CvShape.Large)
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.Purple.copy(alpha = 0.14f), Palette.Red.copy(alpha = 0.07f), colors.text.copy(alpha = 0.03f))
                        )
                    )
                    .border(1.dp, colors.text.copy(alpha = 0.07f), CvShape.Large)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Purple2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (result.byGemini) "Understood with Gemini" else "Understood",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Search titles",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier
                            .clip(CvShape.Pill)
                            .clickableNoRipple(onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                result.reply?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.text)
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEachIndexed { index, chip ->
                            val shown = rememberArrival(1f, delayMillis = 60 * index, durationMillis = 420)
                            Text(
                                chip,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.text,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = shown
                                        translationY = (1f - shown) * 10f
                                    }
                                    .clip(CvShape.Pill)
                                    .background(colors.text.copy(alpha = 0.08f))
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${result.items.size} ${if (result.items.size == 1) "match" else "matches"} for “${result.heard}”",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (result.items.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing matched all of that", style = MaterialTheme.typography.titleMedium, color = colors.text2)
                    Spacer(Modifier.height(6.dp))
                    Text("Try fewer details, or search the titles instead.", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                    Spacer(Modifier.height(14.dp))
                    CvButton("Search titles", onDismiss, primary = false)
                }
            }
        }
        itemsIndexed(result.items, key = { _, item -> item.key }) { index, item ->
            val shown = rememberArrival(1f, delayMillis = (index * 35).coerceAtMost(420), durationMillis = 520)
            Box(Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 24f }) {
                PosterCard(
                    item = item,
                    onOpen = onOpen,
                    width = posterCellWidth(),
                    watched = library.isWatched(item.key),
                    saved = library.isSaved(item.key),
                    rating = library.ratingOf(item.key),
                )
            }
        }
    }
}

@Composable
private fun Did(result: AskUi.Did, onOpen: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val outcome = result.outcome
    LaunchedEffect(result) { haptics?.play(if (outcome.ok) Haptic.Success else Haptic.Warning) }
    Box(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .clip(CvShape.Large)
                .background(colors.text.copy(alpha = 0.04f))
                .border(1.dp, colors.text.copy(alpha = 0.07f), CvShape.Large)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val item = outcome.item
            if (item?.posterPath != null) {
                val shown = rememberArrival(1f, durationMillis = 520)
                Box(
                    Modifier
                        .graphicsLayer { scaleX = 0.9f + 0.1f * shown; scaleY = scaleX; alpha = shown }
                        .width(110.dp)
                        .height(165.dp)
                        // The same poster on the title page: it flies there.
                        .sharedPoster(item.key)
                        .clip(CvShape.Medium)
                        .background(colors.surface2)
                        .clickableNoRipple { onOpen(item) },
                ) {
                    CvImage(Img.poster(item.posterPath), item.title, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
            }
            AnimatedContent(outcome.ok, transitionSpec = { scaleIn(tween(320)) togetherWith fadeOut() }, label = "didIcon") { ok ->
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    null,
                    tint = if (ok) Palette.Green2 else Palette.Gold,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(outcome.message, style = MaterialTheme.typography.titleMedium, color = colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("“${result.heard}”", style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic), color = colors.text3)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item != null) CvButton("Open", { onOpen(item) })
                CvButton("Search titles", onDismiss, primary = false)
            }
        }
    }
}

/**
 * "Who's that?": the person Gemini thinks you mean, large, with the line that
 * says why, the things they are known for, and the other people it might
 * have been underneath.
 */
@Composable
private fun Found(
    result: AskUi.Found,
    onPerson: (Int) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val best = result.people.first()
    LaunchedEffect(result.heard) { haptics?.play(Haptic.Success) }
    // What they are actually known for: no talk shows, news or reality, no
    // playing themselves, no single guest episode - and the title the
    // description named comes first.
    val known = androidx.compose.runtime.remember(best.id, result.heard) {
        val heard = result.heard.lowercase()
        (best.asCast + best.asCrew)
            .filter { credit ->
                val item = credit.item
                item.hasArt &&
                    item.genreIds.none { it in setOf(10763, 10764, 10767) } &&
                    !Regex("""\b(self|himself|herself|themselves)\b""", RegexOption.IGNORE_CASE).containsMatchIn(credit.role) &&
                    (credit.episodeCount == 0 || credit.episodeCount >= 3)
            }
            .map { it.item }
            .distinctBy { it.key }
            .sortedWith(
                compareByDescending<MediaItem> { it.title.length > 2 && heard.contains(it.title.lowercase()) }
                    .thenByDescending { it.voteCount }
            )
            .take(10)
    }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "best") {
            val shown = rememberArrival(1f, durationMillis = 560)
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = shown; translationY = (1f - shown) * 16f }
                    .geminiGlow()
                    .clip(CvShape.Large)
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.Purple.copy(alpha = 0.14f), Palette.Red.copy(alpha = 0.07f), colors.text.copy(alpha = 0.03f))
                        )
                    )
                    .border(1.dp, colors.text.copy(alpha = 0.07f), CvShape.Large)
                    .clickableNoRipple { haptics?.play(Haptic.Tap); onPerson(best.id) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Purple2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Who's that?", style = MaterialTheme.typography.labelLarge, color = colors.text2, modifier = Modifier.weight(1f))
                    Text(
                        "Search titles",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier
                            .clip(CvShape.Pill)
                            .clickableNoRipple(onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The face lands with a small settle, ringed in Gemini's colours.
                    val face = rememberArrival(1f, delayMillis = 120, durationMillis = 620)
                    Box(
                        Modifier
                            .size(86.dp)
                            .graphicsLayer { scaleX = 0.82f + 0.18f * face; scaleY = scaleX; alpha = face }
                            .border(2.dp, com.cineverse.app.core.ui.geminiBrushStatic, androidx.compose.foundation.shape.CircleShape)
                            .padding(4.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(colors.surface2),
                    ) {
                        CvImage(Img.profile(best.profilePath), best.name, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(best.name, style = MaterialTheme.typography.headlineSmall, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (best.knownFor.isNotBlank()) {
                            Text(best.knownFor, style = MaterialTheme.typography.labelMedium, color = colors.text3)
                        }
                    }
                }
                result.why?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.text)
                }
                Spacer(Modifier.height(10.dp))
                Text("Open their page ›", style = MaterialTheme.typography.labelLarge, color = colors.text2)
            }
        }
        if (known.isNotEmpty()) {
            item(key = "known") {
                Column {
                    Text("KNOWN FOR", style = com.cineverse.app.core.design.KickerStyle, color = colors.text3)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItemsIndexed(known, key = { _, item -> item.key }) { index, item ->
                            val shown = rememberArrival(1f, delayMillis = 200 + index * 45, durationMillis = 480)
                            Box(Modifier.graphicsLayer { alpha = shown; translationX = (1f - shown) * 24f }) {
                                PosterCard(item = item, onOpen = onOpen, width = 104.dp)
                            }
                        }
                    }
                }
            }
        }
        if (result.people.size > 1) {
            item(key = "others") {
                Column {
                    Text("OR PERHAPS", style = com.cineverse.app.core.design.KickerStyle, color = colors.text3)
                    Spacer(Modifier.height(8.dp))
                    for (other in result.people.drop(1)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(CvShape.Medium)
                                .clickableNoRipple { haptics?.play(Haptic.Tap); onPerson(other.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.surface2)) {
                                CvImage(Img.profile(other.profilePath), other.name, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(other.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
                                if (other.knownFor.isNotBlank()) Text(other.knownFor, style = MaterialTheme.typography.labelSmall, color = colors.text3)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A question, answered word by word, in Gemini's light while it writes. */
@Composable
private fun Answered(result: AskUi.Answered, onDismiss: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    LaunchedEffect(result.writing) { if (!result.writing) haptics?.play(Haptic.Land) }
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = BottomBarSpace),
    ) {
        item(key = "answer") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .geminiGlow(pulse = result.writing)
                    .clip(CvShape.Large)
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.Purple.copy(alpha = 0.12f), colors.text.copy(alpha = 0.03f))
                        )
                    )
                    .border(1.dp, colors.text.copy(alpha = 0.07f), CvShape.Large)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Purple2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (result.writing) "Gemini is writing…" else "Answered by Gemini",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Search titles",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier
                            .clip(CvShape.Pill)
                            .clickableNoRipple(onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "“${result.heard}”",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                    color = colors.text2,
                )
                Spacer(Modifier.height(12.dp))
                com.cineverse.app.core.ui.TypewriterText(
                    result.text,
                    writing = result.writing,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                )
            }
        }
    }
}
