package com.cineverse.app.feature.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import com.cineverse.app.core.net.rememberUnmetered
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.CvDropdown
import com.cineverse.app.core.ui.dealIn
import com.cineverse.app.core.ui.bleed
import androidx.compose.foundation.lazy.grid.items
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.feature.home.Hero
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Films, or Series: the website's /movies and /tv pages. A hero carousel of
 * what is trending in that half of the catalogue, then the website's row of
 * filters as dropdowns - genre, year, sort, rating, language and runtime (or
 * status and format for television) - over a grid that pages as you scroll.
 */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onOpen: (MediaItem) -> Unit,
    onPeek: (MediaItem) -> Unit,
    contentPadding: PaddingValues,
    onScrolledPastHero: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val onWifi by rememberUnmetered()
    val grid = rememberLazyGridState()
    val colors = CvTheme.colors

    val atEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(atEnd, state.items.size) { if (atEnd) viewModel.loadMore() }
    HeroScrollReport(grid, onScrolledPastHero)
    var dealt by remember(state.generation) { mutableStateOf(false) }
    LaunchedEffect(state.generation, state.items.isNotEmpty()) { if (state.items.isNotEmpty()) dealt = true }

    LazyVerticalGrid(
        state = grid,
        columns = posterGridCells(),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ScreenPadding, end = ScreenPadding,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            // The hero runs edge to edge, outside the grid's side padding.
            Box(Modifier.bleed(ScreenPadding)) {
            Hero(
                items = state.hero,
                loading = state.hero.isEmpty(),
                logos = state.logos,
                trailers = state.trailers,
                autoplay = settings.autoplay && (!settings.autoplayOnWifiOnly || onWifi),
                autoAdvance = settings.heroAutoAdvance,
                holdMillis = settings.heroSeconds * 1_000L,
                isSaved = { library.isSaved(it.key) },
                onOpen = onOpen,
                onSave = viewModel::toggleSaved,
                onNeedLogo = viewModel::ensureLogo,
            )
            }
        }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(state, viewModel)
        }
        itemsIndexed(state.items, key = { _, it -> it.key }) { index, item ->
            PosterCard(
                item = item,
                onOpen = onOpen,
                width = posterCellWidth(),
                watched = library.isWatched(item.key),
                saved = library.isSaved(item.key),
                rating = library.ratingOf(item.key),
                onLongPress = onPeek,
                modifier = Modifier.dealIn(index % 12, dealt),
            )
        }
        if (state.loading) items(6) { PosterSkeleton(width = posterCellWidth()) }
        if (!state.loading && state.items.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matches", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text("Try clearing one or two filters.", style = MaterialTheme.typography.bodyMedium, color = colors.text3)
                }
            }
        }
    }
}

/** Tells the top bar when the hero has slid up behind it. */
@Composable
private fun HeroScrollReport(grid: LazyGridState, onScrolled: (Boolean) -> Unit) {
    // Glass before the hero's buttons slide under the bar, not after the hero has gone.
    val px = with(LocalDensity.current) { 350.dp.toPx() }
    val latest by rememberUpdatedState(onScrolled)
    LaunchedEffect(grid) {
        snapshotFlow {
            val first = grid.layoutInfo.visibleItemsInfo.firstOrNull()
            when {
                first == null -> false
                first.index > 0 -> true
                else -> first.offset.y + first.size.height < px
            }
        }.distinctUntilChanged().collect { latest(it) }
    }
}

@Composable
private fun FilterRow(state: CatalogState, viewModel: CatalogViewModel) {
    val colors = CvTheme.colors
    val tv = state.type == MediaType.Tv
    val years = remember { listOf(0 to "Any year") + (LocalDate.now().year + 1 downTo 1950).map { it to it.toString() } }
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (tv) "ALL SERIES" else "ALL FILMS",
                style = KickerStyle,
                color = colors.text3,
                modifier = Modifier.weight(1f),
            )
            if (!state.filters.isDefault) {
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .clickableNoRipple { viewModel.setFilters(CatalogFilters()) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val f = state.filters
        val summary = buildList {
            state.genres.firstOrNull { it.id == f.genre }?.let { add(it.name) }
            if (f.year > 0) add("${f.year}")
            if (f.rating > 0) add("${f.rating}+")
            state.services.firstOrNull { it.first == f.provider }?.let { add(it.second) }
            Languages.firstOrNull { it.first == f.language && f.language.isNotEmpty() }?.let { add(it.second) }
            Statuses.firstOrNull { it.first == f.status && f.status.isNotEmpty() }?.let { add(it.second) }
            Formats.firstOrNull { it.first == f.format && f.format.isNotEmpty() }?.let { add(it.second) }
            Runtimes.firstOrNull { it.first == f.runtime && f.runtime.isNotEmpty() }?.let { add(it.second) }
        }
        com.cineverse.app.core.ui.FoldedFilters(
            summary = summary,
            always = {
                CvDropdown("Sort", CatalogSort.entries.map { it to it.label }, f.sort) {
                    viewModel.setFilters(f.copy(sort = it))
                }
            },
        ) {
            CvDropdown("Genre", listOf(0 to "All genres") + state.genres.map { it.id to it.name }, f.genre) {
                viewModel.setFilters(f.copy(genre = it))
            }
            CvDropdown("Year", years, f.year) { viewModel.setFilters(f.copy(year = it)) }
            CvDropdown("Rating", listOf(0 to "Any rating", 6 to "6+", 7 to "7+", 8 to "8+", 9 to "9+"), f.rating) {
                viewModel.setFilters(f.copy(rating = it))
            }
            if (state.services.isNotEmpty()) {
                CvDropdown("Service", listOf(0 to "All services") + state.services, f.provider) {
                    viewModel.setFilters(f.copy(provider = it))
                }
            }
            CvDropdown("Language", Languages, f.language) { viewModel.setFilters(f.copy(language = it)) }
            if (tv) {
                CvDropdown("Status", Statuses, f.status) { viewModel.setFilters(f.copy(status = it)) }
                CvDropdown("Format", Formats, f.format) { viewModel.setFilters(f.copy(format = it)) }
            } else {
                CvDropdown("Runtime", Runtimes, f.runtime) { viewModel.setFilters(f.copy(runtime = it)) }
            }
        }
        if (state.total > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${countLabel(state.total)} ${if (tv) "series" else "films"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
    }
}

/**
 * TMDB stops counting at about twenty thousand, so a broad query and a narrow
 * one both came back as "20,001" - a number that looked exact and was not.
 */
fun countLabel(total: Int): String = if (total >= 20_000) "20,000+" else "%,d".format(total)

private val Statuses = listOf("" to "Any status", "0" to "Returning", "3" to "Ended", "2" to "In production")
private val Formats = listOf("" to "Any format", "4" to "Scripted", "2" to "Miniseries", "3" to "Reality", "0" to "Documentary", "6" to "Animation & video")
private val Runtimes = listOf("" to "Any length", "short" to "Under 90 min", "medium" to "90 to 150 min", "long" to "Over 150 min")

private val Languages = listOf(
    "" to "Any language", "en" to "English", "hi" to "Hindi", "ko" to "Korean", "ja" to "Japanese",
    "es" to "Spanish", "fr" to "French", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam",
    "de" to "German", "it" to "Italian", "zh" to "Chinese", "pt" to "Portuguese", "tr" to "Turkish",
)

// ---------- state ----------

enum class CatalogSort(val label: String) {
    Popular("Most popular"), Rated("Highest rated"), Newest("Newest"), Oldest("Oldest"), Votes("Most voted"), Revenue("Highest grossing"),
}

data class CatalogFilters(
    val genre: Int = 0,
    val year: Int = 0,
    val sort: CatalogSort = CatalogSort.Popular,
    val rating: Int = 0,
    val language: String = "",
    val runtime: String = "",
    val status: String = "",
    val format: String = "",
    /** A streaming service's id: only its subscription catalogue, as on the website. */
    val provider: Int = 0,
) {
    val isDefault: Boolean get() = this == CatalogFilters()
}

data class CatalogState(
    val type: MediaType,
    val hero: List<MediaItem> = emptyList(),
    val logos: Map<String, String> = emptyMap(),
    val trailers: Map<String, String> = emptyMap(),
    val filters: CatalogFilters = CatalogFilters(),
    val genres: List<Genre> = emptyList(),
    /** The streaming services in your region, for the Service dropdown. */
    val services: List<Pair<Int, String>> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    /** Bumped on every new query, so the grid deals itself in again. */
    val generation: Int = 0,
)

class CatalogViewModel(private val app: AppContainer, val type: MediaType) : ViewModel() {

    private val _state = MutableStateFlow(CatalogState(type = type))
    val state: StateFlow<CatalogState> = _state.asStateFlow()
    val library: StateFlow<Library> = app.library.library
    val settings = app.settings.settings

    private var job: Job? = null

    init {
        viewModelScope.launch {
            val hero = app.tmdb.trending(type.wire, "week").filter { it.backdropPath != null }
                .filterNot { it.type == MediaType.Tv && it.genreIds.any { g -> g == 10767 || g == 10763 } }
                .take(8)
            _state.update { it.copy(hero = hero) }
        }
        viewModelScope.launch { _state.update { it.copy(genres = app.tmdb.genres(type)) } }
        viewModelScope.launch {
            val services = app.tmdb.streamingServices(app.settings.settings.value.region)
                .take(30).map { it.providerId to it.providerName }
            _state.update { it.copy(services = services) }
        }
        loadMore()
    }

    fun ensureLogo(item: MediaItem) {
        val held = _state.value
        if (held.logos.containsKey(item.key)) return
        _state.update { it.copy(logos = it.logos + (item.key to "")) }
        viewModelScope.launch {
            val detail = runCatching { app.tmdb.detail(item.id, item.type, app.settings.settings.value.region) }.getOrNull()
                ?: return@launch
            _state.update { s ->
                s.copy(
                    logos = if (detail.logoPath.isNullOrBlank()) s.logos else s.logos + (item.key to detail.logoPath.orEmpty()),
                    trailers = detail.trailer?.key?.let { s.trailers + (item.key to it) } ?: s.trailers,
                )
            }
        }
    }

    fun toggleSaved(item: MediaItem) = viewModelScope.launch { app.library.toggleSaved(item) }

    fun setFilters(filters: CatalogFilters) {
        if (filters == _state.value.filters) return
        job?.cancel()
        _state.update {
            it.copy(filters = filters, items = emptyList(), page = 0, totalPages = 1, total = 0, loading = false, generation = it.generation + 1)
        }
        loadMore()
    }

    private fun params(page: Int): Map<String, String> {
        val f = _state.value.filters
        val tv = type == MediaType.Tv
        val date = if (tv) "first_air_date" else "primary_release_date"
        return buildMap {
            put("page", page.toString())
            put(
                "sort_by",
                when (f.sort) {
                    CatalogSort.Popular -> "popularity.desc"
                    CatalogSort.Rated -> "vote_average.desc"
                    CatalogSort.Newest -> "$date.desc"
                    CatalogSort.Oldest -> "$date.asc"
                    CatalogSort.Votes -> "vote_count.desc"
                    CatalogSort.Revenue -> if (tv) "popularity.desc" else "revenue.desc"
                },
            )
            if (f.genre > 0) put("with_genres", f.genre.toString())
            if (f.year > 0) {
                if (tv) put("first_air_date_year", f.year.toString()) else put("primary_release_year", f.year.toString())
            }
            if (f.rating > 0) put("vote_average.gte", f.rating.toString())
            if (f.language.isNotBlank()) put("with_original_language", f.language)
            when (f.runtime) {
                "short" -> put("with_runtime.lte", "89")
                "medium" -> { put("with_runtime.gte", "90"); put("with_runtime.lte", "150") }
                "long" -> put("with_runtime.gte", "151")
            }
            if (tv && f.status.isNotBlank()) put("with_status", f.status)
            if (tv && f.format.isNotBlank()) put("with_type", f.format)
            if (tv) put("without_genres", "10767,10763")
            if (f.provider > 0) {
                put("with_watch_providers", f.provider.toString())
                put("watch_region", app.settings.settings.value.region)
                put("with_watch_monetization_types", "flatrate")
            }
            // The website's floors: a rating or "highest rated" needs enough
            // votes to mean anything, and newest-first stops at today.
            when {
                f.sort == CatalogSort.Rated || f.rating > 0 -> put("vote_count.gte", if (tv) "150" else "300")
                f.sort == CatalogSort.Newest -> { put("$date.lte", LocalDate.now().toString()); put("vote_count.gte", "10") }
                else -> put("vote_count.gte", "20")
            }
        }
    }

    fun loadMore() {
        val held = _state.value
        if (held.loading || (held.page > 0 && held.page >= held.totalPages)) return
        _state.update { it.copy(loading = true) }
        val generation = held.generation
        job = viewModelScope.launch {
            val next = held.page + 1
            val (items, pages, total) = app.tmdb.discoverCounted(type, params(next))
            if (_state.value.generation != generation) return@launch
            _state.update { s ->
                s.copy(
                    items = (s.items + items.filter { it.hasArt }).distinctBy { it.key },
                    page = next,
                    totalPages = pages,
                    total = if (next == 1) total else s.total,
                    loading = false,
                )
            }
        }
    }
}
