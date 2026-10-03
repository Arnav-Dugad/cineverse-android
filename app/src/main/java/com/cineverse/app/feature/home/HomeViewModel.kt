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
import com.cineverse.app.widget.updateAllSafe
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
    /** Caught-up shows with an episode on the way, soonest first. */
    val upNext: List<com.cineverse.app.data.airing.UpNextItem> = emptyList(),
    /** Shows you finished whose next season premieres this month. */
    val returning: List<com.cineverse.app.data.airing.ReturningItem> = emptyList(),
)

/** TMDB's Talk and News genres: nightly programming, not shows you pick. */
private val NOT_SHOWS = setOf(10767, 10763)

/** Below this many posters a rail looks broken rather than curated. */
private const val MIN_RAIL = 8

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    /** What the taste profile was last built from, so it rebuilds only on change. */
    private var tasteSignature = ""

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library
    val progress: StateFlow<Map<Int, ShowProgress>> = app.episodes.progress

    val settings: StateFlow<com.cineverse.app.data.prefs.Settings> = app.settings.settings

    // Every property is declared ABOVE `init`, and that is load-bearing. The
    // collectors started in `init` run on Dispatchers.Main.immediate against
    // StateFlows that already hold a value, so they execute during
    // construction — and a property declared further down the class has not
    // been assigned yet. `snoozed` used to live beside rebuildContinue, and
    // reading it there threw a NullPointerException on a non-null Set.
    /** Keys snoozed this session. Deliberately not persisted — see [snooze]. */
    private var snoozed: Set<String> = emptySet()

    /** Which shows the airing rails were last built for, and when. */
    private var airingSignature = ""
    private var airingAt = 0L
    private var airingJob: kotlinx.coroutines.Job? = null
    /** The catalogue load; the airing rails wait for it, they never race it. */
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadJob = load()
        // Continue Watching is derived, not fetched: it is a view over the
        // episode documents and the library, so it must rebuild the instant
        // either changes — which is what makes a tick on the detail page move
        // the row on Home before you have finished going back.
        combine(app.episodes.progress, app.library.library, app.continueOrder.order) { shows, lib, _ -> shows to lib }
            .onEach { (shows, lib) ->
                rebuildContinue(shows, lib)
                if (lib.loaded) refreshAiring(shows, lib)
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

    /**
     * Up Next and Returning this month. Both cost a request per show, so they
     * are rebuilt only when the set of shows they read changes, or after half
     * an hour - not on every tick of an episode.
     */
    private fun refreshAiring(
        shows: Map<Int, ShowProgress>,
        lib: Library,
        force: Boolean = false,
    ) {
        val watchedShows = lib.watched.values.filter { it.type == MediaType.Tv }.map { it.tmdbId }.sorted()
        val caughtUp = com.cineverse.app.data.airing.Airing.candidates(shows).map { it.tmdbId }
        val signature = (caughtUp + listOf(-1) + watchedShows).joinToString(",")
        val stale = System.currentTimeMillis() - airingAt > 30 * 60_000L
        if (!force && signature == airingSignature && !stale) return
        airingSignature = signature
        airingAt = System.currentTimeMillis()
        airingJob?.cancel()
        airingJob = viewModelScope.launch {
            // Home's own rails first. Up Next and Returning are a request per
            // show, and started together with the catalogue they queued ahead
            // of it and left the hero and every rail on skeletons.
            loadJob?.join()
            val upNext = async { runCatching { app.airing.upNext(shows) }.getOrDefault(emptyList()) }
            val returning = async {
                runCatching { app.airing.returning(shows, watchedShows) }.getOrDefault(emptyList())
            }
            _state.value = _state.value.copy(upNext = upNext.await(), returning = returning.await())
            // The home-screen widget shows the same countdowns; keep it in step.
            com.cineverse.app.widget.UpNextWidget().updateAllSafe(app.context)
        }
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

    /**
     * Tick the next episode of a show straight from the rail.
     *
     * The same repository the title page writes through, so the merge rules,
     * the log encoding and the offline queue are identical. A film is marked
     * watched outright, since there is no next episode to advance to.
     */
    fun markNext(row: ContinueRow) = viewModelScope.launch {
        val region = app.settings.settings.value.region
        val detail = runCatching {
            app.tmdb.detail(row.item.id, row.item.type, region)
        }.getOrNull() ?: return@launch
        if (row.isMovie) {
            app.library.clearMovieProgress(row.item.id)
            if (!app.library.library.value.isWatched(row.item.key)) {
                app.library.toggleWatched(row.item, detail)
            }
            app.say("Marked watched")
        } else {
            app.episodes.toggleEpisode(detail, row.season, row.episode)
            app.say("${row.item.title} · ${row.label} watched")
        }
    }

    /**
     * Out of the row for now.
     *
     * Local and temporary by design: it is not a statement about the show, it
     * is "not tonight". It survives until the app is killed, and any new tick
     * on that show brings it back, which is the behaviour the word implies.
     * Writing it to Firestore would make a passing mood permanent and would
     * put a field on the account the website knows nothing about.
     */
    fun snooze(row: ContinueRow) {
        snoozed = snoozed + row.item.key
        rebuildContinue(app.episodes.progress.value, app.library.library.value)
        app.say("Snoozed · back when you tick something")
    }

    /**
     * Off the row entirely.
     *
     * For a show this is DROPPED, which is a real field the website reads, so
     * the two agree. For a film it is a tombstone on its progress document,
     * which is exactly what removing it on the website writes.
     */
    fun dismiss(row: ContinueRow) = viewModelScope.launch {
        if (row.isMovie) {
            app.library.clearMovieProgress(row.item.id)
        } else {
            val region = app.settings.settings.value.region
            val detail = runCatching {
                app.tmdb.detail(row.item.id, row.item.type, region)
            }.getOrNull() ?: return@launch
            app.episodes.setDropped(detail, true)
        }
        app.say("Removed from Continue watching")
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        loadJob = load()
        refreshAiring(app.episodes.progress.value, app.library.library.value, force = true)
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
                // A year out at least, or the rail is this month's releases
                // riding their opening-week scores (see Discover's twin).
                "primary_release_date.lte" to java.time.LocalDate.now().minusYears(1).toString(),
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
        // TMDB's own top-rated list lets in a film with a few hundred early
        // votes; two pages, kept to the established ones, is the actual canon.
        val topRated = async {
            (app.tmdb.movies("top_rated") + app.tmdb.movies("top_rated", page = 2))
                .filter { it.voteCount >= 2_000 }
        }
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
        val rawRails = buildList {
            add(Rail("pop_movies", "Popular Movies", items = popMovies.await(),
                seeAll = browse("Popular Movies", "movie", "popular")))
            add(Rail("top10", "Top 10 Movies This Week", items = top10Movies.await(),
                numbered = true, seeAll = com.cineverse.app.nav.Route.TopTen("movie")))
            add(Rail("pop_tv", "Popular TV Shows", items = popTv.await(),
                seeAll = browse("Popular TV Shows", "tv", "popular")))
            add(Rail("top10_tv", "Top 10 Shows This Week", items = top10Tv.await(),
                numbered = true, seeAll = com.cineverse.app.nav.Route.TopTen("tv")))
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
        }
        val rails = curate(rawRails)

        _state.value = _state.value.copy(
            hero = heroes,
            rails = rails,
            loading = false,
            refreshing = false,
        )
    }

    /**
     * The rails, made to read as one front page rather than fifteen queries.
     *
     *  - Talk shows and news are left out of the TV rails. They are popular in
     *    the sense of being on every night, and a Top 10 of chat shows is not a
     *    list of anything anyone would choose to watch.
     *  - A title appears ONCE. Batman turning up in Top Picks, Hidden Gems and
     *    Top Rated reads as a page that ran out of ideas. Rails keep their own
     *    order and each drops what an earlier rail already showed - except a
     *    ranked Top 10, which is a ranking and keeps every position.
     *  - A rail that dedupes down to almost nothing keeps its originals: four
     *    posters is not a rail.
     */
    private fun curate(rails: List<Rail>): List<Rail> {
        val shown = HashSet<String>()
        return rails.map { rail ->
            val clean = rail.items.filterNot { item ->
                item.type == MediaType.Tv && item.genreIds.any { it in NOT_SHOWS }
            }
            val fresh = if (rail.numbered) clean else clean.filterNot { it.key in shown }
            val kept = if (rail.numbered || fresh.size >= MIN_RAIL) fresh else clean
            shown += kept.take(if (rail.numbered) 10 else kept.size).map { it.key }
            rail.copy(items = if (rail.numbered) kept.take(10) else kept)
        }.filter { it.items.isNotEmpty() }
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
                    // With a logo: "More like [logo]" on one line. Without:
                    // the words alone.
                    title = if (logo != null) seed.title else "More like ${seed.title}",
                    kicker = if (logo != null) "More like" else null,
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
                remaining = show.airedRemaining,
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

        // The automatic order (most recently touched first), then the order
        // you arranged by hand on top of it - on this phone or on the website.
        val automatic = rows
            .filterNot { it.item.key in snoozed }
            .sortedByDescending { it.lastAt }
        _state.value = _state.value.copy(
            continueWatching = app.continueOrder.order.value.apply(automatic) { it.item.key }.take(20)
        )
    }

    /** Save the row in the order it was arranged. */
    fun arrange(keys: List<String>) = app.continueOrder.setOrder(keys)

    /** Forget the hand-made order. */
    fun resetArrangement() = app.continueOrder.reset()
}
