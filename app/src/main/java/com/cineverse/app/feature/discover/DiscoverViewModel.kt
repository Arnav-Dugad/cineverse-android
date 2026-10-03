package com.cineverse.app.feature.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.feature.home.Rail
import com.cineverse.app.nav.Route
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class DiscoverState(
    val rails: List<Pair<String, Rail>> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
)

class DiscoverViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    init { load() }

    /**
     * Pull to refresh.
     *
     * Every rail here is a TMDB discover query, so the page genuinely can be
     * stale — this is not the decorative kind of refresh. The flag clears
     * because load() replaces the whole state object.
     */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    private fun load() = viewModelScope.launch {
        val region = app.settings.settings.value.region
        val thisYear = java.time.Year.now().value

        val acclaimed = async {
            app.tmdb.discover(
                MediaType.Movie,
                mapOf(
                    "sort_by" to "vote_average.desc",
                    "vote_count.gte" to "2000",
                    "primary_release_year" to thisYear.toString(),
                ),
            )
        }
        val hiddenGems = async {
            app.tmdb.discover(
                MediaType.Movie,
                mapOf(
                    "sort_by" to "vote_average.desc",
                    "vote_count.gte" to "300",
                    "vote_count.lte" to "2500",
                    // Out for a year at least. A film in its opening weeks has a
                    // few hundred votes from the people who queued for it, and
                    // those are always tens: that is hype, not a hidden gem.
                    "primary_release_date.lte" to java.time.LocalDate.now().minusYears(1).toString(),
                ),
            )
        }
        val animation = async {
            app.tmdb.discover(MediaType.Movie, mapOf("with_genres" to "16", "sort_by" to "popularity.desc"))
        }
        val docs = async {
            app.tmdb.discover(MediaType.Movie, mapOf("with_genres" to "99", "sort_by" to "popularity.desc"))
        }
        val indian = async {
            app.tmdb.discover(
                MediaType.Movie,
                mapOf("with_original_language" to "hi", "sort_by" to "popularity.desc"),
            )
        }
        val korean = async {
            app.tmdb.discover(
                MediaType.Tv,
                mapOf("with_original_language" to "ko", "sort_by" to "popularity.desc"),
            )
        }

        val rails = listOf(
            "acclaimed" to Rail("acclaimed", "The best of $thisYear", items = acclaimed.await()),
            "gems" to Rail("gems", "Hidden gems", items = hiddenGems.await()),
            "korean" to Rail("korean", "Korean series", items = korean.await()),
            "animation" to Rail("animation", "Animation", items = animation.await()),
            "indian" to Rail("indian", "Indian cinema", items = indian.await()),
            "docs" to Rail("docs", "Documentaries", items = docs.await()),
        ).filter { it.second.items.isNotEmpty() }

        _state.value = DiscoverState(rails = rails, loading = false)
    }

    /**
     * One title, at random, from what you have not seen. It pulls from a random
     * page of a broad, well-rated query so it is a genuine surprise rather than
     * the same famous film every time.
     */
    suspend fun surprise(): MediaItem? {
        val page = (1..12).random()
        val pool = app.tmdb.discover(
            MediaType.entries.random(),
            mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "500"),
            page = page,
        )
        val lib = app.library.library.value
        return pool.filterNot { lib.isWatched(it.key) }.randomOrNull() ?: pool.randomOrNull()
    }
}
