package com.cineverse.app.feature.topten

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.AnimatedBookmark
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.RankNumeral
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.core.ui.rememberPosterAccent
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Top 10, as a page of its own: the number one as a feature, then nine
 * places down the chart, each with its numeral cut out beside it. Films or
 * series, today or this week, and the page takes its glow from whatever is at
 * the top.
 */
@Composable
fun TopTenScreen(
    viewModel: TopTenViewModel,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val leader = state.items.firstOrNull()
    val accent by rememberPosterAccent(leader?.posterPath, enabled = true)
    val glow by animateColorAsState(accent ?: Palette.Red, tween(900), label = "topGlow")

    Box(
        modifier
            .fillMaxSize()
            .background(colors.ink)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.12f),
                        radius = size.width * 1.2f,
                    )
                )
            }
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = BottomBarSpace)) {
            item(key = "head") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 52.dp)
                        .padding(horizontal = ScreenPadding)
                ) {
                    Text("THE CHART", style = KickerStyle, color = colors.text3)
                    Text(
                        "Top 10 ${if (state.type == MediaType.Movie) "films" else "series"}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.text,
                    )
                    Text(
                        if (state.window == "day") "Most watched on TMDB today" else "Most watched on TMDB this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text3,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CvChip("Films", state.type == MediaType.Movie, { viewModel.setType(MediaType.Movie) })
                        CvChip("Series", state.type == MediaType.Tv, { viewModel.setType(MediaType.Tv) })
                        Spacer(Modifier.width(6.dp))
                        CvChip("Today", state.window == "day", { viewModel.setWindow("day") })
                        CvChip("Week", state.window == "week", { viewModel.setWindow("week") })
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            item(key = "chart") {
                AnimatedContent(
                    targetState = state.items to state.loading,
                    contentKey = { (items, loading) -> items.firstOrNull()?.key + loading },
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInVertically(tween(420)) { it / 14 }) togetherWith fadeOut(tween(160))
                    },
                    label = "chart",
                ) { (items, loading) ->
                    if (loading || items.isEmpty()) {
                        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(CvShape.XLarge).shimmer())
                            repeat(4) { Box(Modifier.fillMaxWidth().height(130.dp).clip(CvShape.Large).shimmer()) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Leader(items.first(), library.isSaved(items.first().key), onOpen) { viewModel.toggleSaved(it) }
                            items.drop(1).forEachIndexed { index, item ->
                                ChartRow(
                                    rank = index + 2,
                                    item = item,
                                    saved = library.isSaved(item.key),
                                    watched = library.isWatched(item.key),
                                    index = index,
                                    onOpen = onOpen,
                                    onSave = { viewModel.toggleSaved(item) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(42.dp)
                .clip(CvShape.Circle)
                .background(colors.ink.copy(alpha = 0.6f))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
        }
    }
}

/** Number one: the whole width, its artwork, and the biggest numeral on the page. */
@Composable
private fun Leader(item: MediaItem, saved: Boolean, onOpen: (MediaItem) -> Unit, onSave: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val arrive = rememberArrival(1f, 80, 700)
    Box(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .aspectRatio(16f / 11f)
            .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 40f }
            .clip(CvShape.XLarge)
            .clickableNoRipple { onOpen(item) }
    ) {
        CvImage(Img.backdrop(item.backdropPath ?: item.posterPath), item.title, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color(0x3306060B),
                        1f to Color(0xF206060B),
                    )
                )
        )
        RankNumeral(1, height = 150.dp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 2.dp))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 100.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text("NUMBER ONE", style = KickerStyle, color = Palette.Gold)
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(item, Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CvButton("Open", { onOpen(item) }, icon = Icons.Rounded.PlayArrow)
                Box(
                    Modifier
                        .size(46.dp)
                        .glass(CvShape.Circle)
                        .clickableNoRipple { haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave(item) },
                    contentAlignment = Alignment.Center,
                ) { AnimatedBookmark(saved, Color.White) }
            }
        }
    }
}

@Composable
private fun ChartRow(
    rank: Int,
    item: MediaItem,
    saved: Boolean,
    watched: Boolean,
    index: Int,
    onOpen: (MediaItem) -> Unit,
    onSave: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    // Each place slides in from the left a beat after the one above it, so the
    // chart reads as being counted down rather than printed.
    val arrive = rememberArrival(1f, 160 + index * 70, 620)
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .graphicsLayer { alpha = arrive; translationX = (1f - arrive) * -60f }
            .glass(CvShape.Large, strength = 0.7f)
            .clickableNoRipple { onOpen(item) }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The numeral has its own room, and the poster only just touches its
        // right edge - a numeral tucked behind the poster read as a smudge.
        Box(Modifier.width(if (rank >= 10) 162.dp else 124.dp).height(126.dp)) {
            RankNumeral(rank, height = 92.dp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(72.dp)
                    .height(108.dp)
                    .clip(CvShape.Medium)
                    .background(colors.surface2)
            ) {
                CvImage(Img.poster(item.posterPath), item.title, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(item, colors.text3)
            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.overview,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (watched) {
                Text("Watched", style = MaterialTheme.typography.labelSmall, color = colors.green)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CvShape.Circle)
                .clickableNoRipple { haptics?.play(if (saved) Haptic.Untick else Haptic.Tick); onSave() },
            contentAlignment = Alignment.Center,
        ) { AnimatedBookmark(saved, if (saved) Palette.Red2 else colors.text2) }
    }
}

@Composable
private fun MetaLine(item: MediaItem, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.voteAverage > 0) {
            Icon(Icons.Rounded.Star, null, tint = Palette.Gold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text("%.1f".format(java.util.Locale.US, item.voteAverage), style = MaterialTheme.typography.labelMedium.tabular(), color = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            listOfNotNull(
                item.year.takeIf { it.isNotBlank() },
                item.genreIds.take(2).mapNotNull { GenreNames[it] }.joinToString(" · ").takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- state ----------

data class TopTenState(
    val type: MediaType = MediaType.Movie,
    val window: String = "week",
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
)

class TopTenViewModel(private val app: AppContainer, type: MediaType) : ViewModel() {

    private val _state = MutableStateFlow(TopTenState(type = type))
    val state: StateFlow<TopTenState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    private var job: Job? = null

    init { load() }

    private fun load() {
        job?.cancel()
        val held = _state.value
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            // The same feed as the Top 10 rails, with talk shows and news left
            // out exactly as Home leaves them out.
            val items = app.tmdb.trending(held.type.wire, held.window)
                .filterNot { it.type == MediaType.Tv && it.genreIds.any { g -> g == 10767 || g == 10763 } }
                .take(10)
            _state.update { it.copy(items = items, loading = false) }
        }
    }

    fun setType(type: MediaType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type) }
        load()
    }

    fun setWindow(window: String) {
        if (window == _state.value.window) return
        _state.update { it.copy(window = window) }
        load()
    }

    fun toggleSaved(item: MediaItem) = viewModelScope.launch { app.library.toggleSaved(item) }
}
