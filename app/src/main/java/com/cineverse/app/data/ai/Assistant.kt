package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.time.LocalDate

/** What doing something came to, in words a person can read or hear. */
@Immutable
data class Outcome(
    val ok: Boolean,
    val message: String,
    val item: MediaItem? = null,
    val trailer: Video? = null,
)

/**
 * The hands behind voice search, the search box's natural language and
 * Gemini's AppFunctions: find the title a person meant, then do the thing to
 * it through the same repositories every button uses.
 */
class Assistant(private val app: AppContainer) {

    // ---------- understanding ----------

    /**
     * A sentence, understood. The fixed commands are matched first, because
     * "add Dune to my list" needs no model; then Gemini, when it is there;
     * then the rule-based parser.
     */
    suspend fun understand(text: String, natural: Boolean): Pair<Ask, String?> {
        Understanding.command(text)?.let { return it to null }
        if (natural) {
            val viewer = runCatching { app.persona.brief() }.getOrDefault("")
            app.gemini.json(Understanding.prompt(text, viewer = viewer))?.let { raw ->
                Understanding.fromJson(text, raw)?.let { return it }
            }
        }
        val query = Understanding.discover(text)
        return if (natural && !query.isEmpty) Ask.Discover(text, query) to null
        else Ask.Search(text, text) to null
    }

    // ---------- finding the title meant ----------

    /**
     * The title somebody named. Your own library is looked at first - "mark
     * Severance watched" means the Severance you are watching - and TMDB
     * after, weighing how closely the name matches against how well known
     * the title is.
     */
    suspend fun resolve(title: String, type: MediaType? = null): MediaItem? {
        val wanted = normal(title)
        if (wanted.isBlank()) return null
        val library = app.library.library.value
        val shows = app.episodes.progress.value
        val mine = buildList {
            library.saved.values.forEach { add(it.asItem()) }
            library.watched.values.forEach { add(it.asItem()) }
            shows.values.forEach { add(MediaItem(it.tmdbId, MediaType.Tv, it.title, posterPath = it.poster.ifBlank { null })) }
        }.distinctBy { it.key }.filter { type == null || it.type == type }
        mine.map { it to match(wanted, normal(it.title)) }
            .filter { it.second >= 0.92 }
            .maxByOrNull { it.second }
            ?.let { return it.first }

        val found = if (type != null) app.tmdb.searchKind(type, title)
        else app.tmdb.searchPage(title).items
        val best = found.filter { it.hasArt || it.voteCount > 0 }
            .map { it to match(wanted, normal(it.title)) * 3 + kotlin.math.log10(it.voteCount + 10.0) * 0.25 }
            .maxByOrNull { it.second }
            ?.takeIf { match(wanted, normal(it.first.title)) >= 0.5 }
            ?.first
        // A loose match in the library beats nothing at all.
        return best ?: mine.map { it to match(wanted, normal(it.title)) }.filter { it.second >= 0.6 }.maxByOrNull { it.second }?.first
    }

    private fun detail(item: MediaItem): suspend () -> TitleDetail? = {
        runCatching { app.tmdb.detail(item.id, item.type, app.settings.settings.value.region) }.getOrNull()
    }

    // ---------- doing ----------

    private fun signedIn() = app.auth.uid.value != null

    /**
     * The library as the server has it. Called before acting from outside the
     * app, where the process may have started a moment ago and the listeners
     * have not delivered their first snapshot.
     */
    suspend fun ready() {
        if (!signedIn()) return
        kotlinx.coroutines.withTimeoutOrNull(8_000) {
            app.library.library.first { it.loaded }
        }
    }

    private val needSignIn = Outcome(false, "Sign in to CineVerse first, so this can be saved to your account.")

    suspend fun add(title: String): Outcome {
        if (!signedIn()) return needSignIn
        val item = resolve(title) ?: return notFound(title)
        if (app.library.library.value.isSaved(item.key)) return Outcome(true, "${item.title} is already on your list", item)
        return runCatching { app.library.toggleSaved(item, detail(item)()) }
            .fold({ Outcome(true, "Added ${item.title} to your list", item) }, { failed(item) })
    }

    suspend fun remove(title: String): Outcome {
        if (!signedIn()) return needSignIn
        val item = resolve(title) ?: return notFound(title)
        if (!app.library.library.value.isSaved(item.key)) return Outcome(true, "${item.title} isn't on your list", item)
        return runCatching { app.library.toggleSaved(item) }
            .fold({ Outcome(true, "Removed ${item.title} from your list", item) }, { failed(item) })
    }

    suspend fun watched(title: String): Outcome {
        if (!signedIn()) return needSignIn
        val item = resolve(title) ?: return notFound(title)
        if (app.library.library.value.isWatched(item.key)) return Outcome(true, "${item.title} is already marked watched", item)
        return runCatching { app.library.toggleWatched(item, detail(item)()) }
            .fold({ Outcome(true, "Marked ${item.title} watched", item) }, { failed(item) })
    }

    suspend fun rate(title: String, score: Int): Outcome {
        if (!signedIn()) return needSignIn
        val item = resolve(title) ?: return notFound(title)
        val value = score.coerceIn(1, 10)
        return runCatching { app.library.setRating(item.key, value, item.title) }
            .fold({ Outcome(true, "Rated ${item.title} $value out of 10", item) }, { failed(item) })
    }

    suspend fun trailer(title: String): Outcome {
        val item = resolve(title) ?: return notFound(title)
        val video = detail(item)()?.trailer ?: return Outcome(false, "No trailer for ${item.title} yet", item)
        return Outcome(true, "Playing the ${item.title} trailer", item, video)
    }

    suspend fun open(title: String): Outcome {
        val item = resolve(title) ?: return notFound(title)
        return Outcome(true, "Opening ${item.title}", item)
    }

    /**
     * "Mark the next Severance episode watched": the first unwatched episode
     * that has aired, ticked. Refuses an episode that has not aired yet rather
     * than ticking the future.
     */
    suspend fun nextEpisode(show: String): Outcome {
        if (!signedIn()) return needSignIn
        val item = resolve(show, MediaType.Tv) ?: return notFound(show)
        val region = app.settings.settings.value.region
        val held = app.episodes.fetch(item.id)
        val detail = runCatching { app.tmdb.detail(item.id, MediaType.Tv, region) }.getOrNull()
            ?: return Outcome(false, "Couldn't reach TMDB for ${item.title}. Try again in a moment.", item)
        val next = when {
            held != null && held.structure.isNotEmpty() -> held.nextUp()
                ?: return Outcome(true, if (held.complete) "You've finished ${item.title}" else "You're caught up on ${item.title}", item)
            else -> {
                val first = detail.seasons.filter { it.number > 0 && it.episodeCount > 0 }.minByOrNull { it.number }
                    ?: return Outcome(false, "${item.title} has no episodes listed yet", item)
                val aired = first.airDate.isNotBlank() && runCatching { !LocalDate.parse(first.airDate).isAfter(LocalDate.now()) }.getOrDefault(false)
                if (!aired) return Outcome(false, "${item.title} hasn't started airing yet", item)
                first.number to 1
            }
        }
        val (season, episode) = next
        return runCatching { app.episodes.markEpisode(detail, season, episode) }
            .fold({ Outcome(true, "Marked ${item.title} S$season E$episode watched", item) }, { failed(item) })
    }

    private fun notFound(title: String) = Outcome(false, "Couldn't find “$title”")
    private fun failed(item: MediaItem) = Outcome(false, "Couldn't save that for ${item.title}. Check your connection.", item)

    // ---------- discovery ----------

    /**
     * A discover query run against TMDB. Names become ids here - people,
     * keywords, a streaming service - and "like X" starts from X's own
     * recommendations, filtered by whatever else was asked.
     */
    /**
     * "Who's that?": the people Gemini named, each found on TMDB with a face
     * and what they are known for. The best guess leads; a name TMDB cannot
     * find is dropped rather than shown as a blank.
     */
    suspend fun people(names: List<String>): List<com.cineverse.app.data.model.PersonDetail> = coroutineScope {
        names.distinct().take(3).map { name ->
            async {
                val id = app.tmdb.findPerson(name) ?: return@async null
                runCatching { app.tmdb.person(id) }.getOrNull()
            }
        }.mapNotNull { it.await() }.distinctBy { it.id }
    }

    /**
     * A question, answered as it is written - personal where it helps, from
     * the viewer's own brief. Empty when Gemini is off or does not answer.
     */
    fun answer(question: String): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        val prompt = buildString {
            appendLine("You are CineVerse's film and TV expert, answering one particular fan inside the app.")
            appendLine("Answer in at most 120 words of warm, specific plain prose: no markdown, no headings, no lists of more than three items.")
            appendLine("Start with the answer itself: no greeting, never their name. At most one personal touch, and only when it")
            appendLine("genuinely illuminates the answer - a title they love that shares something real with it. Never force one.")
            appendLine("Never spoil anything they have not seen. If you are not sure of a fact, say so rather than inventing one.")
            if (viewer.isNotBlank()) {
                appendLine()
                appendLine(viewer)
            }
            appendLine()
            appendLine("Question: $question")
            appendLine("Answer:")
        }
        app.gemini.stream(prompt).collect { emit(it) }
    }

    suspend fun discover(query: DiscoverQuery): List<MediaItem> = coroutineScope {
        val region = app.settings.settings.value.region
        val peopleIds = query.people.map { async { app.tmdb.findPerson(it) } }
        val keywordIds = query.keywords.map { async { app.tmdb.findKeyword(it) } }
        val people = peopleIds.mapNotNull { it.await() }
        val keywords = keywordIds.mapNotNull { it.await() }
        val seed = query.likeTitle?.let { resolve(it, query.type) }

        val results = if (seed != null) {
            val base = detail(seed)()?.recommendations.orEmpty()
            filterLocally(base, query)
        } else {
            val types = when {
                query.type != null -> listOf(query.type)
                // A person named with no kind: films, where with_people works.
                people.isNotEmpty() -> listOf(MediaType.Movie)
                else -> listOf(MediaType.Movie, MediaType.Tv)
            }
            types.map { type ->
                async {
                    val params = params(query, type, people, keywords, region)
                    var page = app.tmdb.discover(type, params)
                    // Every genre at once can be too narrow ("funny horror
                    // western"); widen to any of them before giving up.
                    if (page.isEmpty() && query.genres.size > 1) {
                        page = app.tmdb.discover(type, params + ("with_genres" to query.genres.joinToString("|")))
                    }
                    if (page.isEmpty() && keywords.isNotEmpty()) {
                        page = app.tmdb.discover(type, params - "with_keywords")
                    }
                    page
                }
            }.flatMap { it.await() }.let { all ->
                if (types.size > 1) all.sortedByDescending { it.popularity } else all
            }
        }
        results.filter { it.hasArt }.distinctBy { it.key }
    }

    private fun params(
        query: DiscoverQuery,
        type: MediaType,
        people: List<Int>,
        keywords: List<Int>,
        region: String,
    ): Map<String, String> = buildMap {
        val movie = type == MediaType.Movie
        val dateField = if (movie) "primary_release_date" else "first_air_date"
        put("include_adult", "false")
        if (query.genres.isNotEmpty()) put("with_genres", query.genres.joinToString(","))
        if (query.withoutGenres.isNotEmpty()) put("without_genres", query.withoutGenres.joinToString(","))
        query.yearFrom?.let { put("$dateField.gte", "$it-01-01") }
        query.yearTo?.let { put("$dateField.lte", "$it-12-31") }
        query.minRating?.let { put("vote_average.gte", it.toString()) }
        query.maxRuntime?.let { put("with_runtime.lte", it.toString()) }
        query.minRuntime?.let { put("with_runtime.gte", it.toString()) }
        query.language?.let { put("with_original_language", it) }
        if (people.isNotEmpty() && movie) put("with_people", people.joinToString(","))
        if (keywords.isNotEmpty()) put("with_keywords", keywords.joinToString("|"))
        query.provider?.let { name ->
            Understanding.Providers[name]?.let {
                put("with_watch_providers", it.toString())
                put("watch_region", region)
                put("with_watch_monetization_types", "flatrate|free|ads")
            }
        }
        when (query.sort) {
            "vote_average.desc" -> {
                put("sort_by", "vote_average.desc")
                put("vote_count.gte", if (movie) "500" else "200")
            }
            "release" -> {
                put("sort_by", "$dateField.desc")
                put("$dateField.lte", LocalDate.now().toString())
                put("vote_count.gte", "20")
            }
            else -> {
                put("sort_by", "popularity.desc")
                // A floor, so "good" and plain popularity alike skip the
                // unrated filler that tops raw popularity.
                put("vote_count.gte", if (query.minRating != null) (if (movie) "300" else "120") else "40")
            }
        }
    }

    private fun filterLocally(items: List<MediaItem>, query: DiscoverQuery): List<MediaItem> = items.filter { item ->
        val year = item.year.toIntOrNull()
        (query.type == null || item.type == query.type) &&
            (query.genres.isEmpty() || item.genreIds.any { it in query.genres }) &&
            item.genreIds.none { it in query.withoutGenres } &&
            (query.yearFrom == null || (year != null && year >= query.yearFrom)) &&
            (query.yearTo == null || (year != null && year <= query.yearTo)) &&
            (query.minRating == null || item.voteAverage >= query.minRating - 0.3) &&
            (query.language == null || item.originalLanguage == query.language)
    }.ifEmpty { items }

    companion object {
        /** Lower case, no accents, no punctuation, no leading article. */
        fun normal(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("^(the|a|an) "), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** 1 for the same title, less for a prefix or a share of the words, 0 for nothing alike. */
        fun match(wanted: String, title: String): Double {
            if (wanted.isEmpty() || title.isEmpty()) return 0.0
            if (wanted == title) return 1.0
            if (title.startsWith("$wanted ") || title.startsWith(wanted) && wanted.length >= 4) return 0.9
            if (wanted.startsWith("$title ")) return 0.8
            val a = wanted.split(' ').toSet()
            val b = title.split(' ').toSet()
            val shared = (a intersect b).size.toDouble()
            return shared / (a union b).size
        }
    }
}
