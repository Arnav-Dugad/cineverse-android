package com.cineverse.app.feature.person

import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import com.cineverse.app.core.ui.glass
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.PersonDetail
import com.cineverse.app.feature.detail.ExpandableText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun PersonScreen(
    viewModel: PersonViewModel,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val person by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    var timeline by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Content scrolled under the clock gets a scrim, as every other page has.
    val scrolled by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40
        }
    }
    val scrim by androidx.compose.animation.core.animateFloatAsState(if (scrolled) 1f else 0f, label = "scrim")

    Box(modifier.fillMaxSize().background(colors.ink)) {
        if (person == null) {
            Box(Modifier.fillMaxSize().shimmer())
        } else {
            val detail = person!!
            // Everything they are in, newest first, deduplicated: an actor who
            // also directed a film should appear once, under the work, not twice.
            val credits = (detail.asCast + detail.asCrew)
                .distinctBy { it.item.key }
                .filter { it.item.posterPath != null }

            LazyVerticalGrid(
                state = gridState,
                columns = posterGridCells(),
                contentPadding = PaddingValues(
                    start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpanFull() }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 48.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(colors.surface2)
                        ) {
                            CvImage(Img.profile(detail.profilePath), detail.name, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            detail.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                        )
                        if (detail.knownFor.isNotBlank()) {
                            Text(
                                detail.knownFor,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.text3,
                            )
                        }
                        if (detail.biography.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            ExpandableText(detail.biography, collapsedLines = 4)
                        }
                        // How long you have spent watching them.
                        val hours = androidx.compose.runtime.remember(detail, library, shows) {
                            com.cineverse.app.data.cast.ActorHours.of(detail, library, shows)
                        }
                        if (hours.minutes > 0) {
                            Spacer(Modifier.height(22.dp))
                            TimeWithYou(detail.name, hours)
                        }
                        // How much of their work you have seen, and what is left.
                        val completion = androidx.compose.runtime.remember(detail, library.watched) {
                            Completion.of(
                                detail,
                                com.cineverse.app.data.franchise.Franchises.watchedFilmIds(library),
                            )
                        }
                        if (completion.total >= 2) {
                            Spacer(Modifier.height(22.dp))
                            CompletionPanel(
                                completion = completion,
                                onOpen = onOpen,
                                isSaved = { library.isSaved(it.key) },
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${credits.size} TITLES",
                                style = KickerStyle,
                                color = colors.text3,
                                modifier = Modifier.weight(1f),
                            )
                            com.cineverse.app.core.ui.CvChip("Timeline", timeline, { timeline = true })
                            Spacer(Modifier.width(6.dp))
                            com.cineverse.app.core.ui.CvChip("Grid", !timeline, { timeline = false })
                        }
                    }
                }
                if (timeline) {
                    val decades = Timeline.byDecade(credits)
                    decades.forEachIndexed { index, (decade, entries) ->
                        item(key = "decade_$decade", span = { GridItemSpanFull() }) {
                            DecadeBlock(
                                decade = decade,
                                credits = entries,
                                last = index == decades.lastIndex,
                                index = index,
                                isWatched = { library.isWatched(it.key) || (it.type == com.cineverse.app.data.model.MediaType.Tv && (shows[it.id]?.watchedCount ?: 0) > 0) },
                                isSaved = { library.isSaved(it.key) },
                                onOpen = onOpen,
                            )
                        }
                    }
                } else {
                    items(credits, key = { it.item.key + it.role }) { credit ->
                        PosterCard(
                            item = credit.item,
                            onOpen = onOpen,
                            width = posterCellWidth(),
                            watched = library.isWatched(credit.item.key),
                            saved = library.isSaved(credit.item.key),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = scrim }
                .background(colors.ink)
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(58.dp)
                .graphicsLayer { alpha = scrim }
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.ink, colors.ink.copy(alpha = 0f))))
        )
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.ink.copy(alpha = 0.6f))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
        }
    }
}

@Suppress("FunctionName")
private fun androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.GridItemSpanFull() =
    androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)

class PersonViewModel(private val app: AppContainer, private val id: Int) : ViewModel() {

    private val _state = MutableStateFlow<PersonDetail?>(null)
    val state: StateFlow<PersonDetail?> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library
    val shows = app.episodes.progress

    init {
        viewModelScope.launch {
            _state.value = runCatching { app.tmdb.person(id) }.getOrNull()
        }
    }
}

/** "≈ 34h with Adam Scott", and where that time came from. */
@Composable
private fun TimeWithYou(name: String, hours: com.cineverse.app.data.cast.ActorHours) {
    val colors = CvTheme.colors
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .glass(com.cineverse.app.core.design.CvShape.XLarge, strength = 0.7f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.gold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Schedule, null, tint = colors.gold)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("TIME WITH YOU", style = KickerStyle, color = colors.text3)
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
                Text("≈ ", style = MaterialTheme.typography.titleLarge, color = colors.gold)
                com.cineverse.app.core.ui.CountUpText(
                    (hours.minutes / 60).toLong().coerceAtLeast(if (hours.minutes > 0) 1 else 0),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.gold,
                    format = { "${it}h" },
                )
                Text(
                    "  with ${name.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Text(
                buildList {
                    if (hours.shows > 0) add("${hours.shows} show${if (hours.shows == 1) "" else "s"}")
                    if (hours.films > 0) add("${hours.films} film${if (hours.films == 1) "" else "s"}")
                }.joinToString(" and ") + (hours.top.firstOrNull()?.let { " · most from ${it.first}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 2,
            )
        }
    }
}
