package com.cineverse.app.data.tmdb

import com.cineverse.app.data.model.Person

import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.PersonDetail
import com.cineverse.app.data.model.TitleDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the app knows about films and series.
 *
 * Two rules run through it:
 *
 * 1. **A failed rail is an empty rail, never a failed screen.** Home asks for
 *    fourteen things at once; one of them 404ing must not take the page down.
 *    Catalogue reads return empty lists on failure and let the screen decide.
 * 2. **A detail read is allowed to throw**, because there the failure IS the
 *    screen — there is nothing else to show, and the retry belongs to the user.
 */
class TmdbRepository(
    private val api: TmdbApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val detailCache = LruCache<String, TitleDetail>(24)
    private val seasonCache = LruCache<String, List<Episode>>(80)
    private val imdbIds = LruCache<String, String>(300)
    private val collectionCache = LruCache<Int, com.cineverse.app.data.franchise.CollectionInfo>(160)
    private val moneyCache = LruCache<Int, com.cineverse.app.data.boxoffice.FilmMoney>(600)
    private val directorCache = LruCache<Int, List<Triple<Int, String, String?>>>(400)
    private val briefCache = LruCache<Int, Pair<Long, com.cineverse.app.data.airing.TvBrief>>(200)
    private var movieGenres: List<Genre> = emptyList()
    private var tvGenres: List<Genre> = emptyList()
    private val genreLock = Mutex()

    private suspend fun <T> quiet(block: suspend () -> List<T>): List<T> =
        runCatching { withContext(io) { block() } }.getOrDefault(emptyList())

    // ---------- catalogue ----------

    suspend fun trending(media: String = "all", window: String = "week", page: Int = 1) =
        quiet { api.trending(media, window, page).results.toItems() }.also { items ->
            // Every sighting of a weekly chart is written down for the Top 10 history.
            if (window == "week" && page == 1 && (media == "movie" || media == "tv")) {
                runCatching { onWeeklyChart?.invoke(MediaType.of(media), items) }
            }
        }

    /** Told whenever this week's film or series chart is fetched. */
    var onWeeklyChart: ((MediaType, List<com.cineverse.app.data.model.MediaItem>) -> Unit)? = null

    suspend fun movies(list: String, page: Int = 1, region: String? = null) =
        quiet { api.movieList(list, page, region).results.toItems(MediaType.Movie) }

    suspend fun series(list: String, page: Int = 1) =
        quiet { api.tvList(list, page).results.toItems(MediaType.Tv) }

    suspend fun discover(type: MediaType, params: Map<String, String>, page: Int = 1): List<MediaItem> =
        quiet {
            val query = params + mapOf("page" to page.toString())
            if (type == MediaType.Movie) api.discoverMovies(query).results.toItems(MediaType.Movie)
            else api.discoverTv(query).results.toItems(MediaType.Tv)
        }

    /** Discover, with the page count, for a grid that loads more as it scrolls. */
    suspend fun discoverPage(
        type: MediaType,
        params: Map<String, String>,
        page: Int = 1,
    ): Pair<List<MediaItem>, Int> = runCatching {
        withContext(io) {
            val query = params + mapOf("page" to page.toString())
            val result = if (type == MediaType.Movie) api.discoverMovies(query) else api.discoverTv(query)
            result.results.toItems(type) to result.totalPages
        }
    }.getOrDefault(emptyList<MediaItem>() to 1)

    suspend fun search(query: String, page: Int = 1, adult: Boolean = false): Pair<List<MediaItem>, Int> {
        if (query.isBlank()) return emptyList<MediaItem>() to 1
        return runCatching {
            withContext(io) {
                val result = api.searchMulti(query, page, adult)
                result.results.toItems() to result.totalPages
            }
        }.getOrDefault(emptyList<MediaItem>() to 1)
    }

    /**
     * A search page with the people kept.
     *
     * The plain [search] drops every person TMDB returns, which is right for a
     * grid of posters and wrong for a search box: someone typing "nolan" is
     * almost always after Christopher Nolan, and got four documentaries with
     * the word in their titles instead. So the people come back as their own
     * list, and when the strongest match on the page IS a person, the titles
     * they are known for lead the results.
     *
     * Within a page, a title with almost no votes sorts after established ones
     * (stably, so TMDB's relevance order survives inside each group): a
     * student short with eleven votes should not outrank the film you meant.
     */
    suspend fun searchPage(query: String, page: Int = 1, adult: Boolean = false): SearchPage {
        if (query.isBlank()) return SearchPage()
        return runCatching {
            withContext(io) {
                val result = api.searchMulti(query, page, adult)
                val people = result.results.filter { it.mediaType == "person" && it.id > 0 }
                val media = result.results.filter { it.mediaType != "person" }
                val lead = people.maxByOrNull { it.popularity }
                val leadsPage = page == 1 && lead != null &&
                    lead.popularity >= (media.maxOfOrNull { it.popularity } ?: 0.0)
                val known = if (leadsPage) lead!!.knownFor.toItems().filter { it.hasArt } else emptyList()
                val knownKeys = known.map { it.key }.toSet()
                SearchPage(
                    items = (known + media.toItems())
                        .distinctBy { it.key }
                        .sortedBy { if (it.voteCount >= 25 || it.key in knownKeys) 0 else 1 },
                    people = if (page == 1) people.map { dto ->
                        Person(
                            id = dto.id,
                            name = dto.name.orEmpty(),
                            profilePath = dto.profilePath,
                            job = dto.knownForDepartment,
                        )
                    }.filter { it.name.isNotBlank() } else emptyList(),
                    totalPages = result.totalPages,
                )
            }
        }.getOrDefault(SearchPage())
    }

    /** Series whose name matches, for grouping television families. */
    suspend fun searchTv(query: String): List<MediaItem> =
        if (query.isBlank()) emptyList()
        else quiet { api.search("tv", query).results.toItems(MediaType.Tv) }

    /** Films or series whose title matches, best first; for resolving a title somebody said. */
    suspend fun searchKind(type: MediaType, query: String): List<MediaItem> =
        if (query.isBlank()) emptyList()
        else quiet { api.search(type.wire, query).results.toItems(type) }

    /** The best-known person by this name, or null. */
    suspend fun findPerson(name: String): Int? = runCatching {
        withContext(io) { api.search("person", name).results.filter { it.id > 0 }.maxByOrNull { it.popularity }?.id }
    }.getOrNull()

    /**
     * The streaming services in a region, in TMDB's own order of prominence -
     * the website's provider catalogue. Films and series are merged, since a
     * service is the same service for both.
     */
    suspend fun streamingServices(region: String): List<ProviderDto> = runCatching {
        withContext(io) {
            (api.watchProviders("movie", region).results + api.watchProviders("tv", region).results)
                // Rent-and-buy shops are not services: their catalogue on a
                // subscription filter is empty.
                .filter { it.providerId > 0 && it.logoPath != null && it.providerId !in Stores }
                .groupBy { it.providerId }
                .map { (_, same) -> same.minBy { it.displayPriority } }
                .sortedWith(compareBy<ProviderDto> { it.displayPriority }.thenBy { it.providerName })
        }
    }.getOrDefault(emptyList())

    /** Apple TV Store, Google Play Movies, Amazon Video, Microsoft Store, YouTube, Fandango at Home, Rakuten. */
    private val Stores = setOf(2, 3, 7, 10, 35, 68, 192)

    /** A TMDB keyword id for a theme like "time travel", or null. */
    suspend fun findKeyword(word: String): Int? = runCatching {
        withContext(io) {
            val all = api.searchKeyword(word).results
            (all.firstOrNull { it.name.equals(word, true) } ?: all.firstOrNull())?.id
        }
    }.getOrNull()

    /** The typeahead: fewer results, no paging, and failure is simply silence. */
    suspend fun suggest(query: String, adult: Boolean = false): List<MediaItem> =
        if (query.isBlank()) emptyList()
        else quiet { api.searchMulti(query, 1, adult).results.toItems().take(8) }

    // ---------- one title ----------

    /** Where a series' episode length comes from when TMDB has none (TVmaze). */
    var runtimeFallback: (suspend (TitleDetail) -> Int)? = null

    /** Told about every title whose details load. */
    var onDetail: ((TitleDetail) -> Unit)? = null

    suspend fun detail(id: Int, type: MediaType, region: String, refresh: Boolean = false): TitleDetail {
        val key = "${type.wire}_${id}_$region"
        if (!refresh) detailCache[key]?.let { return it }
        return withContext(io) {
            val mapped = if (type == MediaType.Movie) api.movie(id).toDetail(region)
            else api.tv(id).toDetail(region)
            val detail = if (mapped.isSeries && mapped.episodeRuntime <= 0) {
                val minutes = runCatching { runtimeFallback?.invoke(mapped) }.getOrNull() ?: 0
                if (minutes > 0) mapped.copy(episodeRuntime = minutes) else mapped
            } else mapped
            detailCache.put(key, detail)
            runCatching { onDetail?.invoke(detail) }
            if (detail.imdbId.isNotBlank()) imdbIds.put("${type.wire}_$id", detail.imdbId)
            detail
        }
    }

    /** The cached copy, for a screen that wants to paint before it fetches. */
    fun cachedDetail(id: Int, type: MediaType, region: String): TitleDetail? =
        detailCache["${type.wire}_${id}_$region"]

    suspend fun season(showId: Int, season: Int): List<Episode> {
        val key = "${showId}_$season"
        seasonCache[key]?.let { return it }
        return quiet {
            api.season(showId, season).episodes
                .filter { it.episodeNumber > 0 }
                .map { it.toEpisode() }
        }.also { if (it.isNotEmpty()) seasonCache.put(key, it) }
    }

    /**
     * Every season at once, four requests in flight. This is what the heatmap and
     * the progress figures need, and doing it serially on a twelve-season show is
     * the difference between a panel that opens and one that hangs.
     */
    suspend fun allSeasons(showId: Int, seasons: List<Int>): Map<Int, List<Episode>> = coroutineScope {
        val wanted = seasons.filter { it > 0 }.distinct().sorted()
        val chunks = wanted.chunked(4)
        buildMap {
            for (chunk in chunks) {
                val fetched = chunk.map { number -> async { number to season(showId, number) } }
                fetched.forEach { job ->
                    val (number, episodes) = job.await()
                    if (episodes.isNotEmpty()) put(number, episodes)
                }
            }
        }
    }

    suspend fun person(id: Int): PersonDetail = withContext(io) { api.person(id).toDetail() }

    suspend fun collection(id: Int): Pair<String, List<MediaItem>> = runCatching {
        withContext(io) {
            val dto = api.collection(id)
            dto.name to dto.parts.toItems(MediaType.Movie)
                .sortedBy { it.releaseDate.ifBlank { "9999" } }
        }
    }.getOrDefault("" to emptyList())

    /**
     * A collection with its own artwork and every part, for the franchise
     * screens. Kept for the session: a collection's shape changes when a sequel
     * is announced, not between two screens.
     */
    suspend fun collectionInfo(id: Int): com.cineverse.app.data.franchise.CollectionInfo? {
        if (id <= 0) return null
        collectionCache[id]?.let { return it }
        return runCatching {
            withContext(io) {
                val dto = api.collection(id)
                com.cineverse.app.data.franchise.CollectionInfo(
                    id = dto.id.takeIf { it > 0 } ?: id,
                    name = dto.name,
                    overview = dto.overview.orEmpty(),
                    poster = dto.posterPath,
                    backdrop = dto.backdropPath,
                    parts = dto.parts.filter { it.id > 0 }.distinctBy { it.id }.map { part ->
                        com.cineverse.app.data.franchise.CollectionPart(
                            id = part.id,
                            title = (part.title ?: part.name).orEmpty(),
                            poster = part.posterPath,
                            backdrop = part.backdropPath,
                            releaseDate = part.releaseDate.orEmpty(),
                            vote = part.voteAverage,
                            voteCount = part.voteCount,
                            overview = part.overview.orEmpty(),
                        )
                    },
                )
            }
        }.getOrNull()?.also { collectionCache.put(id, it) }
    }

    /**
     * A series with nothing appended: its status, its seasons' premiere dates
     * and its next episode. Up Next and Returning read a few dozen of these at
     * a time, and the full title payload is fifty times the size.
     */
    suspend fun tvBrief(id: Int): com.cineverse.app.data.airing.TvBrief? {
        if (id <= 0) return null
        briefCache[id]?.let { (at, brief) ->
            if (System.currentTimeMillis() - at < 30 * 60_000L) return brief
        }
        return runCatching {
            withContext(io) {
                val dto = api.tv(id, append = "")
                com.cineverse.app.data.airing.TvBrief(
                    id = dto.id.takeIf { it > 0 } ?: id,
                    name = dto.name,
                    originalName = dto.originalName.orEmpty(),
                    poster = dto.posterPath,
                    backdrop = dto.backdropPath,
                    status = dto.status.orEmpty(),
                    firstAirDate = dto.firstAirDate.orEmpty(),
                    seasonDates = dto.seasons.filter { it.seasonNumber > 0 }
                        .associate { it.seasonNumber to it.airDate.orEmpty() },
                    next = dto.nextEpisode?.let { next ->
                        com.cineverse.app.data.airing.NextEpisode(
                            season = next.seasonNumber,
                            episode = next.episodeNumber,
                            name = next.name,
                            airDate = next.airDate.orEmpty(),
                            still = next.stillPath,
                            type = next.episodeType.orEmpty(),
                        )
                    },
                )
            }
        }.getOrNull()?.also { briefCache.put(id, System.currentTimeMillis() to it) }
    }

    /**
     * One film's money and the few facts a box-office row shows. A bare detail
     * request with nothing appended: the chart reads two hundred of these, and
     * the full title payload is a hundred times the size.
     */
    suspend fun filmMoney(id: Int): com.cineverse.app.data.boxoffice.FilmMoney? {
        moneyCache[id]?.let { return it }
        return runCatching {
            withContext(io) {
                val dto = api.movie(id, append = "")
                com.cineverse.app.data.boxoffice.FilmMoney(
                    id = dto.id,
                    title = dto.title,
                    poster = dto.posterPath,
                    backdrop = dto.backdropPath,
                    releaseDate = dto.releaseDate.orEmpty(),
                    revenue = dto.revenue.coerceAtLeast(0),
                    budget = dto.budget.coerceAtLeast(0),
                    runtime = dto.runtime ?: 0,
                    vote = dto.voteAverage,
                    voteCount = dto.voteCount,
                    language = dto.originalLanguage.orEmpty(),
                    countries = dto.countries.map { it.code.uppercase() }.filter { it.isNotBlank() },
                    collectionId = dto.collection?.id ?: 0,
                    collectionName = dto.collection?.name.orEmpty(),
                    collectionPoster = dto.collection?.posterPath,
                    collectionBackdrop = dto.collection?.backdropPath,
                )
            }
        }.getOrNull()?.also { moneyCache.put(id, it) }
    }

    /** A film's directors: (id, name, profile). Empty on failure. */
    suspend fun directorsOf(id: Int): List<Triple<Int, String, String?>> {
        directorCache[id]?.let { return it }
        return runCatching {
            withContext(io) {
                api.movieCredits(id).crew.filter { it.job == "Director" && it.id > 0 }
                    .distinctBy { it.id }
                    .map { Triple(it.id, it.name, it.profilePath) }
            }
        }.getOrNull()?.also { directorCache.put(id, it) }.orEmpty()
    }

    /** A series' whole cast with how many episodes each was in. */
    suspend fun tvCast(id: Int): List<AggregateCastDto> = quiet { api.tvAggregateCredits(id).cast }

    /** A studio or network's own card: name, mark, where it is. */
    suspend fun studio(id: Int, network: Boolean): StudioDto? = runCatching {
        withContext(io) { if (network) api.network(id) else api.company(id) }
    }.getOrNull()

    /** One discover page with its totals, for a catalogue screen that counts. */
    suspend fun discoverCounted(type: MediaType, params: Map<String, String>): Triple<List<MediaItem>, Int, Int> =
        runCatching {
            withContext(io) {
                val page = api.discoverPage(type.wire, params)
                Triple(page.results.toItems(type), page.totalPages.coerceAtMost(500), page.totalResults)
            }
        }.getOrDefault(Triple(emptyList(), 1, 0))

    /**
     * A title's IMDb id, which the outside-scores service needs and a saved
     * title does not carry. Permanent once known — it never changes.
     */
    suspend fun imdbId(id: Int, type: MediaType): String {
        val key = "${type.wire}_$id"
        imdbIds[key]?.let { return it }
        return runCatching {
            withContext(io) { api.externalIds(type.wire, id).imdbId.orEmpty() }
        }.getOrDefault("").also { if (it.isNotBlank()) imdbIds.put(key, it) }
    }

    // ---------- genres ----------

    suspend fun genres(type: MediaType): List<Genre> = genreLock.withLock {
        val held = if (type == MediaType.Movie) movieGenres else tvGenres
        if (held.isNotEmpty()) return held
        val fetched = quiet { api.genres(type.wire).genres.map { Genre(it.id, it.name) } }
        if (type == MediaType.Movie) movieGenres = fetched else tvGenres = fetched
        fetched
    }

    /** Both lists merged, for a card that only has ids and no idea which it is. */
    suspend fun genreNames(): Map<Int, String> =
        (genres(MediaType.Movie) + genres(MediaType.Tv)).associate { it.id to it.name }
}

/** One page of a search: titles, the people it found, and how far it goes. */
data class SearchPage(
    val items: List<MediaItem> = emptyList(),
    val people: List<Person> = emptyList(),
    val totalPages: Int = 1,
)

/** A tiny, allocation-free LRU. Room would be a database for something that is a map. */
class LruCache<K : Any, V : Any>(private val max: Int) {
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }

    @Synchronized operator fun get(key: K): V? = entries[key]

    @Synchronized fun put(key: K, value: V) { entries[key] = value }

    @Synchronized fun clear() = entries.clear()
}
