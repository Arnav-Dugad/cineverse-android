package com.cineverse.app.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.ContinueRow
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** One titled row of posters. */
@Immutable
data class Rail(
    val id: String,
    val title: String,
    val kicker: String? = null,
    val items: List<MediaItem> = emptyList(),
    val seeAll: com.cineverse.app.nav.Route? = null,
)

@Immutable
data class HomeState(
    val hero: List<MediaItem> = emptyList(),
    val continueWatching: List<ContinueRow> = emptyList(),
    val rails: List<Rail> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
)

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library
    val progress: StateFlow<Map<Int, ShowProgress>> = app.episodes.progress

    init {
        load()
        // Continue Watching is derived, not fetched: it is a view over the
        // episode documents and the library, so it must rebuild the instant
        // either changes — which is what makes a tick on the detail page move
        // the row on Home before you have finished going back.
        combine(app.episodes.progress, app.library.library) { shows, lib -> shows to lib }
            .onEach { (shows, lib) -> rebuildContinue(shows, lib) }
            .launchIn(viewModelScope)

        app.online
            .onEach { online -> _state.value = _state.value.copy(offline = !online) }
            .launchIn(viewModelScope)
    }

    /** The hero's and every rail's save button. */
    fun toggleSaved(item: MediaItem) = viewModelScope.launch {
        app.library.toggleSaved(item)
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    private fun load() = viewModelScope.launch {
        val region = app.settings.settings.value.region

        // Everything at once. A rail that fails is an empty rail, never a
        // failed screen, so one 404 cannot take Home down.
        val trending = async { app.tmdb.trending("all", "week") }
        val trendingToday = async { app.tmdb.trending("all", "day") }
        val popularMovies = async { app.tmdb.movies("popular", region = region) }
        val topMovies = async { app.tmdb.movies("top_rated") }
        val nowPlaying = async { app.tmdb.movies("now_playing", region = region) }
        val upcoming = async { app.tmdb.movies("upcoming", region = region) }
        val popularTv = async { app.tmdb.series("popular") }
        val topTv = async { app.tmdb.series("top_rated") }
        val airingTv = async { app.tmdb.series("on_the_air") }

        val heroes = trending.await().filter { it.backdropPath != null }.take(8)
        val rails = buildList {
            add(Rail("trending_today", "Trending today", items = trendingToday.await()))
            add(Rail("popular_movies", "Popular films", items = popularMovies.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("Popular films", "movie", "popular")))
            add(Rail("airing", "On air now", kicker = "Television", items = airingTv.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("On air now", "tv", "on_the_air")))
            add(Rail("top_movies", "Highest rated films", items = topMovies.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("Highest rated films", "movie", "top_rated")))
            add(Rail("popular_tv", "Popular series", items = popularTv.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("Popular series", "tv", "popular")))
            add(Rail("in_cinemas", "In cinemas", kicker = "Near you", items = nowPlaying.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("In cinemas", "movie", "now_playing")))
            add(Rail("top_tv", "Highest rated series", items = topTv.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("Highest rated series", "tv", "top_rated")))
            add(Rail("upcoming", "Coming soon", items = upcoming.await(),
                seeAll = com.cineverse.app.nav.Route.Browse("Coming soon", "movie", "upcoming")))
        }.filter { it.items.isNotEmpty() }

        _state.value = _state.value.copy(
            hero = heroes,
            rails = rails,
            loading = false,
            refreshing = false,
        )

        // Taste-based rows come after the catalogue, because they need the
        // library to have arrived and they must never delay first paint.
        loadBecauseYouWatched()
    }

    /**
     * "Because you're watching Severance" — the website's best rail, and the
     * one that makes the app feel like it knows you. Built from the show you
     * are furthest into, not the most recently added, because that is the one
     * your taste is actually expressed by.
     */
    private fun loadBecauseYouWatched() = viewModelScope.launch {
        val shows = app.episodes.progress.value.values
            .filter { it.watchedCount >= 3 && !it.dropped }
            .sortedByDescending { it.log.lastOrNull()?.stamp ?: 0L }
            .take(2)
        if (shows.isEmpty()) return@launch
        val region = app.settings.settings.value.region
        val extra = shows.mapNotNull { show ->
            val similar = runCatching {
                app.tmdb.detail(show.tmdbId, MediaType.Tv, region).recommendations
            }.getOrDefault(emptyList())
            val unseen = similar.filterNot { app.library.library.value.isWatched(it.key) }
            if (unseen.isEmpty()) null else Rail(
                id = "because_${show.tmdbId}",
                title = show.title,
                kicker = "Because you're watching",
                items = unseen.take(20),
            )
        }
        if (extra.isEmpty()) return@launch
        // Second from the top: close enough to be seen, far enough that the
        // catalogue still leads.
        val existing = _state.value.rails.filterNot { it.id.startsWith("because_") }
        _state.value = _state.value.copy(
            rails = existing.take(1) + extra + existing.drop(1)
        )
    }

    /**
     * Continue Watching, in the order you last touched them.
     *
     * A show appears when it has been started and is not finished or dropped;
     * a film appears when it has a stored position. The next episode is the
     * lowest unwatched one that has aired — and a show you are caught up on
     * drops off the row entirely rather than sitting there with nothing to tap.
     */
    private fun rebuildContinue(shows: Map<Int, ShowProgress>, lib: Library) = viewModelScope.launch {
        val rows = mutableListOf<ContinueRow>()

        for (show in shows.values) {
            if (show.dropped || show.watchedCount == 0) continue
            val next = show.nextUp() ?: continue
            val (season, episode) = next
            val total = show.totalEpisodes
            rows += ContinueRow(
                item = MediaItem(
                    id = show.tmdbId,
                    type = MediaType.Tv,
                    title = show.title,
                    posterPath = show.poster.ifBlank { null },
                    backdropPath = show.backdrop.ifBlank { null },
                ),
                season = season,
                episode = episode,
                remaining = (total - show.watchedCount).coerceAtLeast(0),
                progress = if (total > 0) show.watchedCount.toFloat() / total else 0f,
                lastAt = show.log.lastOrNull()?.stamp ?: show.updatedAt,
            )
        }

        for (progress in lib.movieProgress.values) {
            if (progress.position <= 0) continue
            val saved = lib.saved["movie_${progress.tmdbId}"] ?: lib.watched["movie_${progress.tmdbId}"]?.let {
                null // already finished: it does not belong in Continue Watching
            }
            val item = saved?.asItem() ?: MediaItem(
                id = progress.tmdbId,
                type = MediaType.Movie,
                title = lib.saved["movie_${progress.tmdbId}"]?.title ?: "",
            )
            if (item.title.isBlank()) continue
            rows += ContinueRow(
                item = item,
                progress = progress.fraction,
                lastAt = progress.updatedAt,
                isMovie = true,
                minutesLeft = (progress.runtime - progress.position).coerceAtLeast(0),
            )
        }

        _state.value = _state.value.copy(
            continueWatching = rows.sortedByDescending { it.lastAt }.take(20)
        )
    }
}
