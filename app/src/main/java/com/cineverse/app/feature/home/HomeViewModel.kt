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
import com.cineverse.app.data.recommend.Recommender
import com.cineverse.app.data.recommend.SeedReason
import com.cineverse.app.data.recommend.TasteProfile
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
    /** Per-title match percentage, for a personalised row. */
    val match: Map<String, Int> = emptyMap(),
)

@Immutable
data class HomeState(
    val hero: List<MediaItem> = emptyList(),
    val continueWatching: List<ContinueRow> = emptyList(),
    /**
     * Kept apart from [rails] on purpose. The two are produced by independent
     * coroutines — the catalogue from one fetch, these from the library
     * arriving — and holding them in one list meant whichever finished second
     * silently erased the other. Which it was depended on how warm the Firestore
     * cache was, so the personalised rows appeared on some launches and not
     * others, which is the worst kind of bug to chase.
     */
    val personal: List<Rail> = emptyList(),
    val rails: List<Rail> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** item key -> the title treatment path, once one has been found. */
    val heroLogos: Map<String, String> = emptyMap(),
    val offline: Boolean = false,
)

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    /** What the taste profile was last built from, so it rebuilds only on change. */
    private var tasteSignature = ""

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
            .onEach { (shows, lib) ->
                rebuildContinue(shows, lib)
                // Recommendations cannot be built until the library has actually
                // arrived — it is a Firestore listener, so at the moment `load()`
                // runs it is still empty and a profile built from it is empty too.
                // Rebuilt when the SHAPE of the taste changes (a title added,
                // watched or rated), not on every emission: a tick that moves one
                // episode must not re-fetch nine TMDB queries.
                if (lib.loaded) {
                    val signature = listOf(lib.saved.size, lib.watched.size, lib.ratings.size, shows.size).joinToString("|")
                    if (signature != tasteSignature) {
                        tasteSignature = signature
                        loadRecommendations()
                    }
                }
            }
            .launchIn(viewModelScope)

        app.online
            .onEach { online -> _state.value = _state.value.copy(offline = !online) }
            .launchIn(viewModelScope)
    }

    /** The hero's and every rail's save button. */
    fun toggleSaved(item: MediaItem) = viewModelScope.launch {
        app.library.toggleSaved(item)
    }

    /**
     * The hero title logo, fetched for one slide at a time.
     *
     * Every streaming app in the world shows a film own title treatment over its
     * artwork rather than setting the name in the app typeface, and it is the
     * single biggest reason their heroes look like posters and ours looked like a
     * caption. TMDB does not return logos with a trending list, so this asks for
     * the title page — but only for the slide now showing and the one after it,
     * which is two requests rather than eight, and both are cached by the time
     * the carousel comes back round.
     *
     * A title with no logo keeps the typeset name. That is the correct fallback:
     * plenty of titles have no treatment, and a blank space where the name was
     * is not a trade anyone would take.
     */
    fun ensureHeroLogo(item: MediaItem) {
        val held = _state.value
        if (held.heroLogos.containsKey(item.key)) return
        // Claimed with an empty string first, so eight recompositions in the same
        // frame cannot start eight identical requests.
        _state.value = held.copy(heroLogos = held.heroLogos + (item.key to ""))
        viewModelScope.launch {
            val path = runCatching {
                app.tmdb.detail(item.id, item.type, app.settings.settings.value.region).logoPath
            }.getOrNull().orEmpty()
            if (path.isNotBlank()) {
                _state.value = _state.value.copy(
                    heroLogos = _state.value.heroLogos + (item.key to path)
                )
            }
        }
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

        // Recommendations are driven by the library arriving, not by this
        // function finishing — see the combine above.
    }

    /**
     * The recommendations.
     *
     * Two kinds, and the difference matters:
     *
     *  - **Top picks**, scored across every signal in the taste profile and
     *    diversified, with a match percentage that actually separates the row.
     *  - **Because you're watching X**, which is a narrower promise and is
     *    filtered harder — a row named after a show has to look like that show.
     *
     * Both wait for the library to arrive and neither delays first paint: the
     * catalogue is already on screen by the time these land.
     */
    private fun loadRecommendations() = viewModelScope.launch {
        val library = app.library.library.value
        val shows = app.episodes.progress.value
        val profile = TasteProfile.build(library, shows, app.rotation)
        if (profile.isEmpty) return@launch

        val picks = app.recommender.recommend(
            profile = profile,
            library = library,
            adult = app.settings.settings.value.adult,
        )

        val extra = mutableListOf<Rail>()
        if (picks.isNotEmpty()) {
            val range = Recommender.scoreRange(picks)
            extra += Rail(
                id = "picks",
                title = "Top picks for you",
                kicker = "Chosen from ${profile.titlesSeen} titles you have tracked",
                items = picks.map { it.item },
                match = picks.associate { it.item.key to Recommender.matchBadge(it.score, range) },
            )
        }

        for (seed in profile.seeds.take(2)) {
            val related = app.recommender.becauseOf(seed, library)
            if (related.size >= 6) {
                extra += Rail(
                    id = "because_${seed.type.wire}_${seed.id}",
                    title = seed.title,
                    kicker = when (seed.reason) {
                        SeedReason.Watching -> "Because you're watching"
                        SeedReason.Rated -> "Because you rated this highly"
                        else -> "Because you watched"
                    },
                    items = related,
                )
            }
        }

        // Top picks leads; the catalogue follows. A personalised row below six
        // generic ones is a personalised row nobody sees.
        _state.value = _state.value.copy(personal = extra)
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
