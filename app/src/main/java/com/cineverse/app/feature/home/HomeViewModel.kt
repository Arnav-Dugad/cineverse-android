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
    /** A Top 10: drawn with its position behind each card. */
    val numbered: Boolean = false,
    /** The logo of the title this rail was derived from, for the kicker. */
    val kickerLogo: String? = null,
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
    /** item key -> its YouTube trailer key, for the hero to play behind itself. */
    val heroTrailers: Map<String, String> = emptyMap(),
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
    val settings: StateFlow<com.cineverse.app.data.prefs.Settings> = app.settings.settings

    fun ensureHeroLogo(item: MediaItem) {
        val held = _state.value
        if (held.heroLogos.containsKey(item.key)) return
        // Claimed with an empty string first, so eight recompositions in the same
        // frame cannot start eight identical requests.
        _state.value = held.copy(heroLogos = held.heroLogos + (item.key to ""))
        viewModelScope.launch {
            // The same request carries both: the title page already appends
            // `images` and `videos`, so asking once gets the logo AND the
            // trailer. Two calls for two fields off one document would be a
            // waste of a request and of somebody's data.
            val detail = runCatching {
                app.tmdb.detail(item.id, item.type, app.settings.settings.value.region)
            }.getOrNull() ?: return@launch
            val path = detail.logoPath.orEmpty()
            val trailer = detail.trailer?.key.orEmpty()
            _state.value = _state.value.copy(
                heroLogos = if (path.isBlank()) _state.value.heroLogos
                else _state.value.heroLogos + (item.key to path),
                heroTrailers = if (trailer.isBlank()) _state.value.heroTrailers
                else _state.value.heroTrailers + (item.key to trailer),
            )
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    /**
     * Home, built from the website's own list of rails.
     *
     * The same eighteen sections in the same order, with the same TMDB paths and
     * the same discover parameters, so the two front pages agree about what is
     * worth showing and in what order. Where the app differs it differs on
     * purpose: Trending This Week is the HERO here rather than a rail at the
     * bottom, and Trending People is left out because a row of faces with no
     * artwork reads as a gap on a phone.
     *
     * Everything is fetched at once and a rail that fails is an empty rail,
     * never a failed screen, so one 404 cannot take Home down.
     */
    private fun load() = viewModelScope.launch {
        val region = app.settings.settings.value.region
        fun browse(title: String, type: String, list: String = "", genre: Int = 0, sort: String = "popularity.desc") =
            com.cineverse.app.nav.Route.Browse(title, type, list, genre, sort)

        val trending = async { app.tmdb.trending("all", "week") }
        val popMovies = async { app.tmdb.movies("popular", region = region) }
        val top10Movies = async { app.tmdb.trending("movie", "week") }
        val popTv = async { app.tmdb.series("popular") }
        val top10Tv = async { app.tmdb.trending("tv", "week") }
        val acclaimed = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "sort_by" to "vote_average.desc", "vote_count.gte" to "3000",
            ))
        }
        val nowPlaying = async { app.tmdb.movies("now_playing", region = region) }
        val gems = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "sort_by" to "vote_average.desc",
                "vote_average.gte" to "7.2",
                "vote_count.gte" to "200",
                "vote_count.lte" to "1500",
            ))
        }
        val upcoming = async { app.tmdb.movies("upcoming", region = region) }
        val horror = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "with_genres" to "27", "sort_by" to "popularity.desc", "vote_count.gte" to "150",
            ))
        }
        val comedy = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "with_genres" to "35", "sort_by" to "popularity.desc", "vote_count.gte" to "150",
            ))
        }
        val topRated = async { app.tmdb.movies("top_rated") }
        val animation = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "with_genres" to "16", "sort_by" to "popularity.desc", "vote_count.gte" to "200",
            ))
        }
        val airing = async { app.tmdb.series("airing_today") }
        val world = async {
            app.tmdb.discover(MediaType.Movie, mapOf(
                "with_original_language" to "ko",
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "100",
            ))
        }
        val topTv = async { app.tmdb.series("top_rated") }

        val heroes = trending.await().filter { it.backdropPath != null }.take(8)
        val rails = buildList {
            add(Rail("pop_movies", "Popular Movies", items = popMovies.await(),
                seeAll = browse("Popular Movies", "movie", "popular")))
            add(Rail("top10", "Top 10 Movies This Week", items = top10Movies.await().take(10),
                numbered = true))
            add(Rail("pop_tv", "Popular TV Shows", items = popTv.await(),
                seeAll = browse("Popular TV Shows", "tv", "popular")))
            add(Rail("top10_tv", "Top 10 Shows This Week", items = top10Tv.await().take(10),
                numbered = true))
            add(Rail("acclaimed", "Critically Acclaimed", items = acclaimed.await(),
                seeAll = browse("Critically Acclaimed", "movie", sort = "vote_average.desc")))
            add(Rail("now_playing", "Now Playing", items = nowPlaying.await(),
                seeAll = browse("Now Playing", "movie", "now_playing")))
            add(Rail("gems", "Hidden Gems", items = gems.await()))
            add(Rail("upcoming", "Upcoming Movies", items = upcoming.await(),
                seeAll = browse("Upcoming Movies", "movie", "upcoming")))
            add(Rail("horror", "Spine-Chilling Horror", items = horror.await(),
                seeAll = browse("Spine-Chilling Horror", "movie", genre = 27)))
            add(Rail("comedy", "Laugh Out Loud", items = comedy.await(),
                seeAll = browse("Laugh Out Loud", "movie", genre = 35)))
            add(Rail("top_rated", "Top Rated Movies", items = topRated.await(),
                seeAll = browse("Top Rated Movies", "movie", "top_rated")))
            add(Rail("animation", "Animated Favorites", items = animation.await(),
                seeAll = browse("Animated Favorites", "movie", genre = 16)))
            add(Rail("airing", "Airing Today", items = airing.await(),
                seeAll = browse("Airing Today", "tv", "airing_today")))
            add(Rail("world", "World Cinema", items = world.await()))
            add(Rail("top_tv", "Top Rated TV", items = topTv.await(),
                seeAll = browse("Top Rated TV", "tv", "top_rated")))
        }.filter { it.items.isNotEmpty() }

        _state.value = _state.value.copy(
            hero = heroes,
            rails = rails,
            loading = false,
            refreshing = false,
        )
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

        val region = app.settings.settings.value.region
        for (seed in profile.seeds.take(2)) {
            val related = app.recommender.becauseOf(seed, library)
            if (related.size >= 6) {
                // The seed's own title treatment, so the row reads
                // "Because you're watching [MODERN FAMILY]" rather than naming
                // it in the app's typeface. The logo is the thing people
                // recognise; the words above it are the explanation.
                val logo = runCatching {
                    app.tmdb.detail(seed.id, seed.type, region).logoPath
                }.getOrNull()
                extra += Rail(
                    id = "because_${seed.type.wire}_${seed.id}",
                    title = seed.title,
                    kicker = when (seed.reason) {
                        SeedReason.Watching -> "Because you're watching"
                        SeedReason.Rated -> "Because you rated this highly"
                        else -> "Because you watched"
                    },
                    items = related,
                    kickerLogo = logo,
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
                absolute = show.isAbsolute,
                progress = if (total > 0) show.watchedCount.toFloat() / total else 0f,
                lastAt = show.log.lastOrNull()?.stamp ?: show.updatedAt,
            )
        }

        for (progress in lib.movieProgress.values) {
            val key = "movie_${progress.tmdbId}"
            // Finished means finished. The previous version wrote this as
            // `saved ?: watched?.let { null }`, which returns `saved` whenever
            // the film is saved -- so a film that was BOTH on the watchlist and
            // already watched stayed in Continue Watching for ever. The website
            // filters on watched alone, and so does this.
            if (lib.isWatched(key)) continue

            // A position of zero is not an absence. The website records a film
            // the moment you say you have started it, before you have said
            // where you got to, and that film belongs in the row.
            val saved = lib.saved[key]
            val item = MediaItem(
                id = progress.tmdbId,
                type = MediaType.Movie,
                // The progress document carries its own title and artwork, so a
                // film that was never on the watchlist still draws correctly.
                title = progress.title.ifBlank { saved?.title.orEmpty() },
                posterPath = progress.poster.ifBlank { saved?.poster.orEmpty() }.ifBlank { null },
                backdropPath = progress.backdrop.ifBlank { null },
            )
            if (item.title.isBlank()) continue
            rows += ContinueRow(
                item = item,
                progress = progress.fraction,
                lastAt = progress.updatedAt.takeIf { it > 0 } ?: progress.startedAt,
                isMovie = true,
                minutesLeft = progress.minutesLeft,
            )
        }

        _state.value = _state.value.copy(
            continueWatching = rows.sortedByDescending { it.lastAt }.take(20)
        )
    }
}
