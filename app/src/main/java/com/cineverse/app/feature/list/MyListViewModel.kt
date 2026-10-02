package com.cineverse.app.feature.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ListSegment(val label: String) {
    Watchlist("Watchlist"), Watching("Watching"), Watched("Watched")
}

enum class ListSort(val label: String) {
    Recent("Recently added"),
    Title("Title A–Z"),
    Year("Newest first"),
    Rating("Highest rated"),
    /** IMDb, from the shared score cache — the website's sort, on the phone. */
    Imdb("IMDb rating"),
}

@Immutable
data class MyListState(
    val segment: ListSegment = ListSegment.Watchlist,
    val sort: ListSort = ListSort.Recent,
    val items: List<MediaItem> = emptyList(),
)

class MyListViewModel(private val app: AppContainer) : ViewModel() {

    private val segment = MutableStateFlow(ListSegment.Watchlist)
    private val sort = MutableStateFlow(ListSort.Recent)

    val library: StateFlow<Library> = app.library.library

    val signedIn: StateFlow<Boolean> = app.auth.user
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.auth.signedIn)

    val state: StateFlow<MyListState> =
        combine(segment, sort, app.library.library, app.episodes.progress) { seg, order, lib, shows ->
            val items = when (seg) {
                ListSegment.Watchlist -> lib.saved.values
                    .filterNot { lib.isWatched(it.key) }
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

                ListSegment.Watched -> lib.watched.values.map { it.asItem() }
            }
            // "Watching" is already in the order that matters — most recently
            // ticked — so re-sorting it by title would throw away the one thing
            // the segment is for.
            val ordered = if (seg == ListSegment.Watching) items else items.sortedWith(comparator(order, lib))
            MyListState(seg, order, ordered)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListState())

    private fun comparator(order: ListSort, lib: Library): Comparator<MediaItem> = when (order) {
        ListSort.Title -> compareBy { it.title.lowercase() }
        ListSort.Year -> compareByDescending { it.releaseDate }
        ListSort.Rating -> compareByDescending { it.voteAverage }
        ListSort.Imdb -> compareByDescending { imdbOf(it) }
        ListSort.Recent -> compareByDescending {
            lib.saved[it.key]?.addedAt ?: lib.watched[it.key]?.watchedAt ?: 0L
        }
    }

    /**
     * A title whose score has not arrived sorts last rather than pretending to
     * be a zero — the same honesty rule the website's sort follows.
     */
    private fun imdbOf(item: MediaItem): Double {
        val imdbId = runCatching { app.tmdb.cachedDetail(item.id, item.type, app.settings.settings.value.region)?.imdbId }
            .getOrNull().orEmpty()
        if (imdbId.isBlank()) return -1.0
        return app.scores.cached(imdbId, item.type)?.imdb ?: -1.0
    }

    fun select(value: ListSegment) { segment.value = value }

    fun cycleSort() {
        val all = ListSort.entries
        sort.value = all[(all.indexOf(sort.value) + 1) % all.size]
    }
}
