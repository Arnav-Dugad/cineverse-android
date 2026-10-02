package com.cineverse.app.feature.list

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ListSegment(val label: String) {
    Watchlist("Watchlist"), Watching("Watching"), Watched("Watched")
}

/**
 * The orders a LIBRARY offers.
 *
 * "Best match" means recently added here rather than a relevance rank, because
 * there is no query to be relevant to — the default order of your own list is
 * the order you built it in.
 */
val ListSorts = listOf(
    SortOrder.Relevance,
    SortOrder.Title,
    SortOrder.Newest,
    SortOrder.Oldest,
    SortOrder.Rating,
    SortOrder.Imdb,
)

@Immutable
data class MyListState(
    val segment: ListSegment = ListSegment.Watchlist,
    val filter: MediaFilter = MediaFilter(),
    /** Everything in this segment, before the filter. */
    val all: List<MediaItem> = emptyList(),
    /** What the user actually sees. */
    val items: List<MediaItem> = emptyList(),
    val genres: List<Genre> = emptyList(),
    /** Which custom list is selected, or empty for all of them. */
    val listId: String = "",
) {
    val filteredOut: Boolean get() = all.isNotEmpty() && items.isEmpty()
}

class MyListViewModel(private val app: AppContainer) : ViewModel() {

    private val segment = MutableStateFlow(ListSegment.Watchlist)
    private val filter = MutableStateFlow(MediaFilter())
    private val listId = MutableStateFlow("")
    private val genres = MutableStateFlow<List<Genre>>(emptyList())

    init {
        viewModelScope.launch {
            genres.value = (app.tmdb.genres(MediaType.Movie) + app.tmdb.genres(MediaType.Tv))
                .distinctBy { it.id }
                .sortedBy { it.name }
        }
    }

    val library: StateFlow<Library> = app.library.library

    val signedIn: StateFlow<Boolean> = app.auth.user
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.auth.signedIn)

    val state: StateFlow<MyListState> =
        combine(
            segment, filter, listId, genres,
            app.library.library, app.episodes.progress,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val seg = values[0] as ListSegment
            @Suppress("UNCHECKED_CAST")
            val active = values[1] as MediaFilter
            val list = values[2] as String
            @Suppress("UNCHECKED_CAST")
            val genreList = values[3] as List<Genre>
            val lib = values[4] as Library
            @Suppress("UNCHECKED_CAST")
            val shows = values[5] as Map<Int, com.cineverse.app.data.model.ShowProgress>

            val all = when (seg) {
                ListSegment.Watchlist -> lib.saved.values
                    .filterNot { lib.isWatched(it.key) }
                    // A custom list narrows the watchlist rather than replacing
                    // it, so the segment still means the same thing either way.
                    .filter { list.isBlank() || it.lists.contains(list) }
                    .sortedByDescending { it.addedAt }
                    .map { it.asItem() }

                ListSegment.Watching -> shows.values
                    .filter { it.watchedCount > 0 && !it.complete && !it.dropped }
                    .sortedByDescending { it.log.lastOrNull()?.stamp ?: it.updatedAt }
                    .map {
                        MediaItem(
                            id = it.tmdbId,
                            type = MediaType.Tv,
                            title = it.title,
                            posterPath = it.poster.ifBlank { null },
                            backdropPath = it.backdrop.ifBlank { null },
                        )
                    }

                ListSegment.Watched -> lib.watched.values
                    .sortedByDescending { it.watchedAt }
                    .map { it.asItem() }
            }

            // "Watching" arrives in the order that matters -- most recently
            // ticked -- so the default order leaves it exactly as it is.
            val shown = active.apply(all, isWatched = { lib.isWatched(it.key) }, imdbOf = ::imdbOf)
            MyListState(
                segment = seg,
                filter = active,
                all = all,
                items = shown,
                genres = genreList,
                listId = list,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListState())

    /**
     * A title whose score has not arrived sorts last rather than pretending to
     * be a zero -- the same honesty rule the website sort follows.
     */
    private fun imdbOf(item: MediaItem): Double {
        val imdbId = runCatching {
            app.tmdb.cachedDetail(item.id, item.type, app.settings.settings.value.region)?.imdbId
        }.getOrNull().orEmpty()
        if (imdbId.isBlank()) return -1.0
        return app.scores.cached(imdbId, item.type)?.imdb ?: -1.0
    }

    fun select(value: ListSegment) { segment.value = value }

    fun setFilter(value: MediaFilter) { filter.value = value }

    fun selectList(id: String) { listId.value = id }
}
