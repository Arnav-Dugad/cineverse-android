package com.cineverse.app.feature.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvScreenBar
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.tmdb.StudioDto
import com.cineverse.app.nav.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A studio's or a network's whole catalogue, ported from the website's
 * js/studio.js. It used to be one popularity-sorted grid; this exposes the
 * catalogue the way you actually explore one: by era, by rating, by genre, by
 * what made the most money - with the shape of its output across the decades.
 */
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    title: String,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val spot by viewModel.spot.collectAsStateWithLifecycle()
    val spotLoading by viewModel.spotLoading.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val grid = rememberLazyGridState()
    val atEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(atEnd, state.items.size) { if (atEnd) viewModel.loadMore() }

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar(state.studio?.name ?: title, onBack)
        LazyVerticalGrid(
            state = grid,
            columns = posterGridCells(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) { StudioHead(state.studio, viewModel.network) }
            if (spot != null || spotLoading) {
                item(key = "spot", span = { GridItemSpan(maxLineSpan) }) {
                    com.cineverse.app.core.ui.SpotlightCard(spot, spotLoading, onOpen)
                }
            }

            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!viewModel.network) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (type in MediaType.entries) {
                                val total = state.totals[type] ?: 0
                                CvChip(
                                    if (type == MediaType.Movie) "Films" else "Series",
                                    state.type == type,
                                    { viewModel.setType(type) },
                                    count = total.takeIf { it > 0 },
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.cineverse.app.core.ui.CvDropdown(
                            "Sort",
                            StudioSort.entries.filter { it != StudioSort.Revenue || state.type == MediaType.Movie }.map { it to it.label },
                            state.sort,
                            active = true,
                        ) { viewModel.setSort(it) }
                        com.cineverse.app.core.ui.CvDropdown("Era", Eras.map { it.first to it.second }, state.era) { viewModel.setEra(it) }
                        com.cineverse.app.core.ui.CvDropdown("Rating", listOf(0 to "Any rating", 6 to "6+", 7 to "7+", 8 to "8+"), state.rating) { viewModel.setRating(it) }
                        com.cineverse.app.core.ui.CvDropdown("Genre", listOf(0 to "All genres") + state.genres.map { it.id to it.name }, state.genre) { viewModel.setGenre(it) }
                    }
                }
            }

            if (state.decades.size >= 2) {
                item(key = "decades", span = { GridItemSpan(maxLineSpan) }) {
                    key(state.type, state.sort, state.era, state.rating, state.genre) {
                        DecadeProfile(state.decades, state.items.size)
                    }
                }
            }

            items(state.items, key = { it.key }) { item ->
                PosterCard(
                    item = item,
                    onOpen = onOpen,
                    width = posterCellWidth(),
                    watched = library.isWatched(item.key),
                    saved = library.isSaved(item.key),
                    rating = library.ratingOf(item.key),
                )
            }
            if (state.loading) items(6) { PosterSkeleton(width = posterCellWidth()) }
            if (!state.loading && state.items.isEmpty()) {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Nothing matches these filters. Try a wider era or a lower rating.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text3,
                        modifier = Modifier.padding(vertical = 30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioHead(studio: StudioDto?, network: Boolean) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .glass(CvShape.XLarge, strength = 0.7f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(110.dp)
                .height(64.dp)
                .clip(CvShape.Medium)
                .background(colors.text.copy(alpha = 0.10f))
                .border(1.dp, colors.hairline, CvShape.Medium)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (studio?.logoPath != null) {
                com.cineverse.app.core.ui.MarkOrName(studio.logoPath, studio.name, Modifier.fillMaxSize())
            } else {
                Text(
                    studio?.name?.take(2)?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text2,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (network) "NETWORK" else "STUDIO", style = KickerStyle, color = colors.text3)
            Text(
                studio?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val place = listOfNotNull(
                studio?.headquarters?.takeIf { it.isNotBlank() },
                studio?.originCountry?.takeIf { it.isNotBlank() }?.let { com.cineverse.app.feature.stats.countryName(it) },
            ).joinToString(" · ")
            if (place.isNotBlank()) {
                Text(place, style = MaterialTheme.typography.labelMedium, color = colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** How the loaded catalogue spreads across the decades, its peak in gold. */
@Composable
private fun DecadeProfile(decades: List<Pair<Int, Int>>, loaded: Int) {
    val colors = CvTheme.colors
    val top = (decades.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().glass(CvShape.Large, strength = 0.6f).padding(14.dp)) {
        Text("$loaded LOADED TITLES BY DECADE", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(86.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            decades.forEachIndexed { index, (decade, count) ->
                val grown = rememberArrival(count.toFloat() / top, index * 60, 700)
                val peak = count == top
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall.tabular(), color = if (peak) colors.gold else colors.text3)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(grown.coerceAtLeast(0.06f))
                            .clip(CvShape.Tiny)
                            .background(if (peak) colors.gold else Palette.Red2.copy(alpha = 0.75f))
                    )
                    Text("${decade % 100}s".padStart(3, '0'), style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1)
                }
            }
        }
    }
}

// ---------- state ----------

enum class StudioSort(val label: String, val param: String) {
    Popular("Most popular", "popularity.desc"),
    Rated("Highest rated", "vote_average.desc"),
    Discussed("Most discussed", "vote_count.desc"),
    Newest("Newest", "date.desc"),
    Oldest("Oldest", "date.asc"),
    Revenue("Highest grossing", "revenue.desc"),
}

private val Eras = listOf(0 to "Any era", 2020 to "2020s", 2010 to "2010s", 2000 to "2000s", 1990 to "1990s", 1980 to "1980s", 1970 to "1970s", -1 to "Before 1970")

data class StudioState(
    val studio: StudioDto? = null,
    val type: MediaType = MediaType.Movie,
    val sort: StudioSort = StudioSort.Popular,
    val era: Int = 0,
    val rating: Int = 0,
    val genre: Int = 0,
    val genres: List<Genre> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val totals: Map<MediaType, Int> = emptyMap(),
    val loading: Boolean = false,
) {
    val decades: List<Pair<Int, Int>>
        get() = items.mapNotNull { it.year.toIntOrNull()?.let { year -> year / 10 * 10 } }
            .groupingBy { it }.eachCount().toList().sortedBy { it.first }
}

class StudioViewModel(private val app: AppContainer, route: Route.Studio) : ViewModel() {

    private val id = route.id
    val network = route.network

    private val _state = MutableStateFlow(StudioState(type = if (route.network) MediaType.Tv else MediaType.Movie))
    val state: StateFlow<StudioState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    private var job: Job? = null

    private val _spot = MutableStateFlow<com.cineverse.app.data.ai.Spot?>(null)
    val spot: StateFlow<com.cineverse.app.data.ai.Spot?> = _spot.asStateFlow()
    private val _spotLoading = MutableStateFlow(false)
    val spotLoading: StateFlow<Boolean> = _spotLoading.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(studio = app.tmdb.studio(id, network)) } }
        reload()
        // Gemini's take on the studio, from its best-known work, once.
        viewModelScope.launch {
            if (!app.settings.settings.value.geminiOn) return@launch
            val work = runCatching {
                val type = if (network) MediaType.Tv else MediaType.Movie
                app.tmdb.discover(type, buildMap {
                    put("sort_by", "vote_count.desc")
                    if (network) put("with_networks", id.toString()) else put("with_companies", id.toString())
                })
            }.getOrDefault(emptyList()).filter { it.hasArt }
            if (work.size < 3) return@launch
            _spotLoading.value = true
            val name = _state.value.studio?.name ?: kotlinx.coroutines.withTimeoutOrNull(5_000) {
                _state.first { it.studio != null }.studio?.name
            }.orEmpty()
            _spot.value = runCatching { app.spotlight.studio(id, name, network, work) }.getOrNull()
            _spotLoading.value = false
        }
    }

    private fun params(page: Int): Map<String, String> {
        val held = _state.value
        val date = if (held.type == MediaType.Tv) "first_air_date" else "primary_release_date"
        return buildMap {
            put("page", page.toString())
            put(
                "sort_by",
                when {
                    held.sort == StudioSort.Newest -> "$date.desc"
                    held.sort == StudioSort.Oldest -> "$date.asc"
                    held.sort == StudioSort.Revenue && held.type == MediaType.Tv -> "popularity.desc"
                    else -> held.sort.param
                },
            )
            if (network && held.type == MediaType.Tv) put("with_networks", id.toString())
            else put("with_companies", id.toString())
            if (held.genre > 0) put("with_genres", held.genre.toString())
            when {
                held.era == -1 -> put("$date.lte", "1969-12-31")
                held.era > 0 -> { put("$date.gte", "${held.era}-01-01"); put("$date.lte", "${held.era + 9}-12-31") }
            }
            // A "highest rated" with no floor is whichever film four people loved.
            if (held.rating > 0) { put("vote_average.gte", held.rating.toString()); put("vote_count.gte", "50") }
            else if (held.sort == StudioSort.Rated) put("vote_count.gte", "100")
        }
    }

    private fun reload() {
        job?.cancel()
        _state.update { it.copy(items = emptyList(), page = 0, totalPages = 1, loading = false) }
        viewModelScope.launch {
            val genres = app.tmdb.genres(_state.value.type)
            _state.update { it.copy(genres = genres) }
        }
        loadMore()
    }

    fun loadMore() {
        val held = _state.value
        if (held.loading || (held.page > 0 && held.page >= held.totalPages)) return
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            val next = held.page + 1
            val (items, pages, total) = app.tmdb.discoverCounted(held.type, params(next))
            _state.update { s ->
                s.copy(
                    items = (s.items + items).distinctBy { it.key },
                    page = next,
                    totalPages = pages,
                    totals = s.totals + (s.type to total),
                    loading = false,
                )
            }
        }
    }

    fun setType(type: MediaType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type, genre = 0, sort = if (it.sort == StudioSort.Revenue) StudioSort.Popular else it.sort) }
        reload()
    }

    fun setSort(sort: StudioSort) { _state.update { it.copy(sort = sort) }; reload() }
    fun setEra(era: Int) { _state.update { it.copy(era = era) }; reload() }
    fun setRating(rating: Int) { _state.update { it.copy(rating = rating) }; reload() }
    fun setGenre(genre: Int) { _state.update { it.copy(genre = genre) }; reload() }
}
