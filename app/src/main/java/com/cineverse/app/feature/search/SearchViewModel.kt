package com.cineverse.app.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaFilter
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.SortOrder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class SearchState(
    val query: String = "",
    /** Everything TMDB returned, before the filter. */
    val results: List<MediaItem> = emptyList(),
    /** What the user actually sees. */
    val shown: List<MediaItem> = emptyList(),
    val history: List<String> = emptyList(),
    val loading: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
    val filter: MediaFilter = MediaFilter(),
    val genres: List<Genre> = emptyList(),
) {
    val hasMore: Boolean get() = page < totalPages && results.isNotEmpty()

    /** Filtered everything away, with more pages still to come. */
    val filteredOut: Boolean get() = results.isNotEmpty() && shown.isEmpty()
}

/** The orders a SEARCH offers. Relevance leads, because TMDB already ranked it. */
val SearchSorts = listOf(
    SortOrder.Relevance,
    SortOrder.Imdb,
    SortOrder.Rating,
    SortOrder.Votes,
    SortOrder.Newest,
    SortOrder.Oldest,
    SortOrder.Title,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    private val history = ArrayDeque<String>()

    init {
        // 280 ms: long enough that typing "severance" is one request rather than
        // nine, short enough that it still feels like it is keeping up.
        _state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(280)
            .filter { it.length >= 2 }
            .onEach { query -> search(query, page = 1) }
            .launchIn(viewModelScope)

        // Both genre lists, merged: a multi-search returns films and series in
        // one list, so a genre filter that only knew one of them would silently
        // drop half the matches.
        viewModelScope.launch {
            val merged = (app.tmdb.genres(MediaType.Movie) + app.tmdb.genres(MediaType.Tv))
                .distinctBy { it.id }
                .sortedBy { it.name }
            _state.value = _state.value.copy(genres = merged)
        }
    }

    fun setFilter(value: MediaFilter) {
        _state.value = _state.value.copy(filter = value).withFilter()
    }

    /**
     * Re-apply the filter to whatever is held.
     *
     * Kept as one function rather than inlined at each call site because the
     * filter has to be re-run on three separate occasions — a new search, a
     * further page, a changed filter — and a list that is filtered on two of
     * them is worse than one that is filtered on none.
     */
    private fun SearchState.withFilter(): SearchState = copy(
        shown = filter.apply(
            results,
            isWatched = { app.library.library.value.isWatched(it.key) },
            imdbOf = ::imdbOf,
        )
    )

    /** A title whose score has not arrived sorts last rather than as a zero. */
    private fun imdbOf(item: MediaItem): Double {
        val imdbId = app.tmdb.cachedDetail(
            item.id, item.type, app.settings.settings.value.region,
        )?.imdbId.orEmpty()
        if (imdbId.isBlank()) return -1.0
        return app.scores.cached(imdbId, item.type)?.imdb ?: -1.0
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        if (value.isBlank()) {
            _state.value = _state.value.copy(
                results = emptyList(), shown = emptyList(), page = 1, totalPages = 1,
            )
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        remember(query)
        search(query, page = 1)
    }

    private fun remember(query: String) {
        history.remove(query)
        history.addFirst(query)
        while (history.size > 8) history.removeLast()
        _state.value = _state.value.copy(history = history.toList())
    }

    private fun search(query: String, page: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val (items, total) = app.tmdb.search(query, page, app.settings.settings.value.adult)
        val existing = if (page == 1) emptyList() else _state.value.results
        _state.value = _state.value.copy(
            results = (existing + items).distinctBy { it.key },
            loading = false,
            page = page,
            totalPages = total,
        ).withFilter()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        search(current.query.trim(), current.page + 1)
    }
}
