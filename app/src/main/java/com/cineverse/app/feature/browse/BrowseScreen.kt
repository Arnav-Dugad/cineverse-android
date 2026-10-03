package com.cineverse.app.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.dealIn
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.nav.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "See all": a whole catalogue behind a rail, made to be explored.
 *
 * Every rail - Popular, Now Playing, Upcoming, a genre, a mood - is translated
 * into the discover query that means the same thing, so all of them can be
 * re-sorted, narrowed to a genre and stripped of what you have seen, and shown
 * as a poster grid or a list with the story. The header takes the leading
 * title's artwork, and the count says how big the catalogue really is.
 */
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    title: String,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val shown = if (state.hideWatched) state.items.filterNot { library.isWatched(it.key) } else state.items
    val atEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(atEnd, state.items.size, state.hideWatched) { if (atEnd) viewModel.loadMore() }
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 600 } }
    val far by remember { derivedStateOf { grid.firstVisibleItemIndex > 12 } }
    // Flipped one frame AFTER the items arrive, so the first screenful deals
    // itself in rather than appearing already in place.
    var dealt by remember(state.revealed) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state.revealed) { if (state.revealed) dealt = true }

    Box(modifier.fillMaxSize().background(colors.ink)) {
        LazyVerticalGrid(
            state = grid,
            columns = if (state.list) GridCells.Fixed(1) else posterGridCells(),
            contentPadding = PaddingValues(
                start = ScreenPadding, end = ScreenPadding, bottom = 120.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (state.list) 12.dp else 18.dp),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                BrowseHero(title, state, shown.firstOrNull() ?: state.items.firstOrNull())
            }
            item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                Controls(state, viewModel)
            }
            if (state.loading && state.items.isEmpty()) {
                items(9) { if (state.list) ListSkeleton() else PosterSkeleton(width = posterCellWidth()) }
            }
            itemsIndexed(shown, key = { _, item -> item.key }) { index, item ->
                if (state.list) {
                    ListRow(item, library.isWatched(item.key), library.isSaved(item.key), onOpen)
                } else {
                    PosterCard(
                        item = item,
                        onOpen = onOpen,
                        width = posterCellWidth(),
                        watched = library.isWatched(item.key),
                        saved = library.isSaved(item.key),
                        rating = library.ratingOf(item.key),
                        modifier = Modifier.dealIn(index % 12, dealt),
                    )
                }
            }
            if (state.loading && state.items.isNotEmpty()) {
                items(3) { if (state.list) ListSkeleton() else PosterSkeleton(width = posterCellWidth()) }
            }
            if (!state.loading && state.items.isNotEmpty() && state.page >= state.totalPages) {
                item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "That is everything - ${"%,d".format(shown.size)} titles.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            if (!state.loading && shown.isEmpty() && state.items.isNotEmpty()) {
                item(key = "allseen", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "You have seen everything loaded so far. Scroll for more, or show watched titles again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text3,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }

        // A compact bar slides down once the header has gone, so the page is
        // never without its name or a way back.
        AnimatedVisibility(
            visible = scrolled,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.ink.copy(alpha = 0.96f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(state.sort.label, style = MaterialTheme.typography.labelMedium, color = colors.text3)
                Spacer(Modifier.width(12.dp))
            }
        }

        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(42.dp)
                .clip(CvShape.Circle)
                .background(colors.ink.copy(alpha = 0.6f))
                .clickableNoRipple { haptics?.play(Haptic.Tap); onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
        }

        // Back to the top, once you are deep enough to want it.
        AnimatedVisibility(
            visible = far,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .glass(CvShape.Circle, strength = 1.3f)
                    .clickableNoRipple {
                        haptics?.play(Haptic.Tap)
                        scope.launch { grid.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Back to the top", tint = colors.text)
            }
        }
    }
}

@Composable
private fun BrowseHero(title: String, state: BrowseState, lead: MediaItem?) {
    val colors = CvTheme.colors
    Box(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 0.dp)) {
        if (lead != null) {
            CvImage(
                Img.backdrop(lead.backdropPath ?: lead.posterPath), null,
                Modifier
                    .fillMaxSize()
                    .clip(CvShape.XLarge)
                    .graphicsLayer { alpha = 0.55f },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(CvShape.XLarge)
                .background(Brush.verticalGradient(listOf(colors.ink.copy(alpha = 0.15f), colors.ink.copy(alpha = 0.6f), colors.ink)))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp)) {
            Text(
                if (state.type == MediaType.Tv) "SERIES" else "FILMS",
                style = KickerStyle,
                color = colors.text3,
            )
            Text(title, style = MaterialTheme.typography.headlineLarge, color = colors.text, maxLines = 2)
            if (state.total > 0) {
                Text(
                    "${com.cineverse.app.feature.catalog.countLabel(state.total)} titles · ${state.sort.order}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                )
            }
        }
    }
}

@Composable
private fun Controls(state: BrowseState, viewModel: BrowseViewModel) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BrowseSort.entries.toList(), key = { it.name }) { sort ->
                    CvChip(sort.label, state.sort == sort, { viewModel.setSort(sort) })
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .glass(CvShape.Circle)
                    .clickableNoRipple { haptics?.play(Haptic.Select); viewModel.toggleLayout() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.list) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                    if (state.list) "Show as a grid" else "Show as a list",
                    tint = colors.text,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "unseen") {
                CvChip("Hide watched", state.hideWatched, { viewModel.toggleHideWatched() })
            }
            item(key = "allgenres") { CvChip("All genres", state.genre == 0, { viewModel.setGenre(0) }) }
            items(state.genres, key = { it.id }) { genre ->
                CvChip(genre.name, state.genre == genre.id, { viewModel.setGenre(genre.id) })
            }
        }
    }
}

@Composable
private fun ListRow(item: MediaItem, watched: Boolean, saved: Boolean, onOpen: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .glass(CvShape.Large, strength = 0.6f)
            .clickableNoRipple { onOpen(item) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(76.dp).height(114.dp).clip(CvShape.Medium).background(colors.surface2)) {
            CvImage(Img.poster(item.posterPath), item.title, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.voteAverage > 0) {
                    Icon(Icons.Rounded.Star, null, tint = Palette.Gold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("%.1f".format(java.util.Locale.US, item.voteAverage), style = MaterialTheme.typography.labelMedium.tabular(), color = colors.text2)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    listOfNotNull(item.year.takeIf { it.isNotBlank() }, item.genreIds.take(2).mapNotNull { GenreNames[it] }.joinToString(" · ").takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.overview, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (watched || saved) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (watched) "Watched" else "On your list",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (watched) colors.green else Palette.Red2,
                )
            }
        }
    }
}

@Composable
private fun ListSkeleton() {
    Box(Modifier.fillMaxWidth().height(134.dp).clip(CvShape.Large).shimmer())
}

// ---------- state ----------

enum class BrowseSort(val label: String, val order: String) {
    Popular("Popular", "most popular first"),
    Rated("Top rated", "best rated first"),
    Newest("Newest", "newest first"),
    Oldest("Oldest", "oldest first"),
    Votes("Most voted", "most voted first"),
}

data class BrowseState(
    val type: MediaType = MediaType.Movie,
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val sort: BrowseSort = BrowseSort.Popular,
    val genre: Int = 0,
    val genres: List<Genre> = emptyList(),
    val hideWatched: Boolean = false,
    val list: Boolean = false,
    /** The first page has arrived, so the grid deals itself in once. */
    val revealed: Boolean = false,
)

class BrowseViewModel(
    private val app: AppContainer,
    private val route: Route.Browse,
) : ViewModel() {

    private val type = MediaType.of(route.type)

    private val _state = MutableStateFlow(
        BrowseState(
            type = type,
            genre = route.genre,
            sort = when {
                route.sort.startsWith("vote_average") || route.list == "top_rated" -> BrowseSort.Rated
                route.list == "upcoming" -> BrowseSort.Oldest
                else -> BrowseSort.Popular
            },
        )
    )
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    private var job: Job? = null

    init {
        viewModelScope.launch { _state.update { it.copy(genres = app.tmdb.genres(type)) } }
        loadMore()
    }

    /**
     * The discover query that means the same as the rail's list, so every
     * catalogue can be sorted and filtered the same way.
     */
    private fun params(page: Int): Map<String, String> {
        val held = _state.value
        val today = LocalDate.now()
        val tv = type == MediaType.Tv
        val date = if (tv) "first_air_date" else "primary_release_date"
        return buildMap {
            put("page", page.toString())
            when (route.list) {
                "now_playing" -> {
                    put("$date.gte", today.minusWeeks(6).toString())
                    put("$date.lte", today.toString())
                    put("with_release_type", "2|3")
                    app.settings.settings.value.region.takeIf { it.isNotBlank() }?.let { put("region", it) }
                }
                "upcoming" -> {
                    put("$date.gte", today.plusDays(1).toString())
                    put("$date.lte", today.plusMonths(6).toString())
                }
                "airing_today" -> {
                    put("air_date.gte", today.toString()); put("air_date.lte", today.toString())
                }
                "on_the_air" -> {
                    put("air_date.gte", today.toString()); put("air_date.lte", today.plusDays(7).toString())
                }
            }
            if (held.genre > 0) put("with_genres", held.genre.toString())
            put(
                "sort_by",
                when (held.sort) {
                    BrowseSort.Popular -> "popularity.desc"
                    BrowseSort.Rated -> "vote_average.desc"
                    BrowseSort.Newest -> "$date.desc"
                    BrowseSort.Oldest -> "$date.asc"
                    BrowseSort.Votes -> "vote_count.desc"
                },
            )
            // A "top rated" with no floor is whichever film four people loved;
            // newest-first with no floor is a wall of unreleased placeholders.
            when (held.sort) {
                BrowseSort.Rated -> put("vote_count.gte", if (tv) "200" else "500")
                BrowseSort.Newest -> {
                    if (route.list != "upcoming") put("$date.lte", today.toString())
                    put("vote_count.gte", "20")
                }
                else -> put("vote_count.gte", if (route.list == "upcoming") "0" else "50")
            }
        }
    }

    fun loadMore() {
        val held = _state.value
        if (held.loading || (held.page > 0 && held.page >= held.totalPages)) return
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            val next = held.page + 1
            val (items, pages, total) = app.tmdb.discoverCounted(type, params(next))
            _state.update { s ->
                s.copy(
                    items = (s.items + items.filter { it.hasArt }).distinctBy { it.key },
                    page = next,
                    totalPages = pages,
                    total = if (next == 1) total else s.total,
                    loading = false,
                    revealed = true,
                )
            }
        }
    }

    private fun reload() {
        job?.cancel()
        _state.update { it.copy(items = emptyList(), page = 0, totalPages = 1, total = 0, loading = false, revealed = false) }
        loadMore()
    }

    fun setSort(sort: BrowseSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun setGenre(genre: Int) {
        if (genre == _state.value.genre) return
        _state.update { it.copy(genre = genre) }
        reload()
    }

    fun toggleHideWatched() = _state.update { it.copy(hideWatched = !it.hideWatched) }

    fun toggleLayout() = _state.update { it.copy(list = !it.list) }
}
