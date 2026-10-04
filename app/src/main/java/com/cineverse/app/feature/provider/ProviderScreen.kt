package com.cineverse.app.feature.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvDropdown
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvTogglePill
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProviderSort(val label: String) { Popular("Most popular"), Rated("Best rated"), Newest("Newest"), Votes("Most voted") }

@Immutable
data class ProviderState(
    val name: String = "",
    val logo: String = "",
    val regionName: String = "",
    val type: MediaType = MediaType.Movie,
    val sort: ProviderSort = ProviderSort.Popular,
    val genre: Int = 0,
    val genres: List<Genre> = emptyList(),
    val hideWatched: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val pages: Int = 1,
    val loading: Boolean = false,
)

/**
 * Everything one streaming service has where you are - "Everything on
 * Netflix in India". The website's provider catalogue: the service's
 * subscription catalogue only (rentals and purchases never leak in), films
 * or series, with its sorts and genres, loading as you scroll.
 */
class ProviderViewModel(
    private val app: AppContainer,
    private val id: Int,
    name: String,
    logo: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ProviderState(name = name, logo = logo))
    val library = app.library.library
    val state: StateFlow<ProviderState> = _state.asStateFlow()
    private var generation = 0

    init {
        val region = app.settings.settings.value.region
        _state.update { it.copy(regionName = java.util.Locale("", region).getDisplayCountry(java.util.Locale.getDefault())) }
        if (name.isBlank() || logo.isBlank()) viewModelScope.launch {
            app.tmdb.streamingServices(region).firstOrNull { it.providerId == id }?.let { found ->
                _state.update { it.copy(name = it.name.ifBlank { found.providerName }, logo = it.logo.ifBlank { found.logoPath.orEmpty() }) }
            }
        }
        loadGenres()
        load(reset = true)
    }

    private fun loadGenres() = viewModelScope.launch {
        _state.update { it.copy(genres = app.tmdb.genres(it.type)) }
    }

    fun setType(type: MediaType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type, genre = 0) }
        loadGenres()
        load(reset = true)
    }

    fun setSort(sort: ProviderSort) { _state.update { it.copy(sort = sort) }; load(reset = true) }
    fun setGenre(genre: Int) { _state.update { it.copy(genre = genre) }; load(reset = true) }
    fun toggleHideWatched() = _state.update { it.copy(hideWatched = !it.hideWatched) }

    fun loadMore() {
        val held = _state.value
        if (held.loading || held.page >= held.pages) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val mine = ++generation
        val held = _state.value
        val page = if (reset) 1 else held.page + 1
        _state.update { it.copy(loading = true, items = if (reset) emptyList() else it.items) }
        viewModelScope.launch {
            val tv = held.type == MediaType.Tv
            val date = if (tv) "first_air_date" else "primary_release_date"
            val params = buildMap {
                put("with_watch_providers", id.toString())
                put("watch_region", app.settings.settings.value.region)
                put("with_watch_monetization_types", "flatrate")
                put("include_adult", "false")
                if (held.genre > 0) put("with_genres", held.genre.toString())
                when (held.sort) {
                    ProviderSort.Popular -> put("sort_by", "popularity.desc")
                    ProviderSort.Rated -> { put("sort_by", "vote_average.desc"); put("vote_count.gte", if (tv) "150" else "300") }
                    ProviderSort.Newest -> { put("sort_by", "$date.desc"); put("$date.lte", java.time.LocalDate.now().toString()) }
                    ProviderSort.Votes -> put("sort_by", "vote_count.desc")
                }
            }
            val (items, total, pages) = runCatching {
                if (page == 1) app.tmdb.discoverCounted(held.type, params).let { (list, totalPages, totalResults) -> Triple(list, totalResults, totalPages) }
                else app.tmdb.discoverPage(held.type, params, page).let { (list, totalPages) -> Triple(list, _state.value.total, totalPages) }
            }.getOrDefault(Triple(emptyList(), 0, 1))
            if (mine != generation) return@launch
            _state.update {
                it.copy(
                    items = (if (reset) items else it.items + items).filter { m -> m.hasArt }.distinctBy { m -> m.key },
                    total = total,
                    page = page,
                    pages = pages,
                    loading = false,
                )
            }
        }
    }
}

@Composable
fun ProviderScreen(
    viewModel: ProviderViewModel,
    onOpen: (MediaItem) -> Unit,
    onPeek: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val grid = rememberLazyGridState()
    val shown = if (state.hideWatched) state.items.filterNot { library.isWatched(it.key) } else state.items
    val atEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(atEnd, state.items.size) { if (atEnd) viewModel.loadMore() }

    LazyVerticalGrid(
        state = grid,
        columns = posterGridCells(),
        modifier = Modifier.fillMaxSize().background(colors.ink),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            val arrive = rememberArrival(1f, 0, 600)
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Box(Modifier.padding(vertical = 8.dp).size(44.dp).clip(CvShape.Circle).clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
                }
                Row(
                    Modifier.graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 16f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CvShape.Large)
                            .background(colors.surface2),
                    ) {
                        if (state.logo.isNotBlank()) CvImage(Img.provider(state.logo), state.name, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("EVERYTHING ON", style = KickerStyle, color = colors.text3)
                        Text(state.name.ifBlank { "This service" }, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                        Text(
                            buildString {
                                if (state.regionName.isNotBlank()) append("In ${state.regionName}")
                                if (state.total > 0) append(" · ${com.cineverse.app.feature.catalog.countLabel(state.total)} ${if (state.type == MediaType.Movie) "films" else "series"}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.text3,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CvChip("Films", state.type == MediaType.Movie, { viewModel.setType(MediaType.Movie) })
                    CvChip("Series", state.type == MediaType.Tv, { viewModel.setType(MediaType.Tv) })
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CvDropdown("Sort", ProviderSort.entries.map { it to it.label }, state.sort, active = true) { viewModel.setSort(it) }
                    CvDropdown("Genre", listOf(0 to "All genres") + state.genres.map { it.id to it.name }, state.genre) { viewModel.setGenre(it) }
                    CvTogglePill("Hide watched", state.hideWatched) { viewModel.toggleHideWatched() }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        itemsIndexed(shown, key = { _, item -> item.key }) { index, item ->
            val arrive = rememberArrival(1f, (index % 12) * 30, 420)
            Box(Modifier.graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 20f }) {
                PosterCard(
                    item = item,
                    onOpen = onOpen,
                    width = posterCellWidth(),
                    watched = library.isWatched(item.key),
                    saved = library.isSaved(item.key),
                    rating = library.ratingOf(item.key),
                    onLongPress = onPeek,
                )
            }
        }
        if (state.loading) {
            items(6) { PosterSkeleton(width = posterCellWidth()) }
        } else if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing here for that. Try another genre, or series instead of films.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 30.dp),
                )
            }
        }
    }
}
