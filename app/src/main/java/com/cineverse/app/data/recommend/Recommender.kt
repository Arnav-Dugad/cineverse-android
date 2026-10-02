package com.cineverse.app.data.recommend

import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** A candidate, with where it came from and what it scored. */
data class Scored(
    val item: MediaItem,
    val sources: Set<String>,
    val keywordIds: List<Int> = emptyList(),
    val score: Double = 0.0,
)

/**
 * The recommender.
 *
 * Ported from the website's, including the constants, because those constants
 * are the product of real tuning and two surfaces that score the same person
 * differently is worse than either scoring them imperfectly.
 *
 * The shape: generate candidates from several TMDB queries shaped by the taste
 * profile, score each one once, drop everything already watched, then
 * **diversify** — because a row of twenty thrillers is a worse row than twelve
 * thrillers and eight other things, however well each one scored.
 *
 * One TMDB constraint runs through it: `/discover` results carry no cast or
 * crew. Actor and director affinity therefore enters as a QUERY parameter and a
 * per-source bonus, never as a per-candidate credits fetch, which would be
 * twenty extra requests for one row.
 */
class Recommender(private val tmdb: TmdbRepository) {

    companion object {
        /** How much being found by each query is worth on its own. */
        private val SOURCE_BONUS = mapOf(
            "watching" to 1.5, "rec" to 1.4, "keyword" to 1.3, "cast" to 1.2,
            "castmore" to 1.15, "director" to 1.1, "quality" to 0.7,
            "genre" to 0.6, "trending" to 0.4,
        )

        private const val MIN_VOTES = 50

        /**
         * Genres that DEFINE a title's audience rather than flavour it. Sharing
         * "Comedy" means little; being Animation or Documentary when you have
         * shown no interest in either almost never is a match. This is what
         * filled the website's "because you liked" rows with children's films.
         */
        private val DEFINING_GENRES = setOf(16, 99, 10751, 10762, 10770, 10764, 10763, 10767)

        /**
         * A percentage that actually separates the row.
         *
         * Scoring against the top candidate put every card between 97% and 99%,
         * because the scores inside one ranked window are all within a few
         * percent of each other — the badge said "99% match" on everything and
         * told the viewer nothing. Spreading the window's own range across
         * 72–99% keeps the ordering honest and makes first place visibly
         * different from twentieth.
         */
        fun matchBadge(score: Double, range: Pair<Double, Double>): Int {
            val (low, high) = range
            val span = high - low
            val ratio = if (span > 1e-6) ((score - low) / span) else 1.0
            return (72 + ratio.coerceIn(0.0, 1.0) * 27).toInt()
        }

        fun scoreRange(items: List<Scored>): Pair<Double, Double> {
            if (items.isEmpty()) return 0.0 to 1.0
            return items.minOf { it.score } to items.maxOf { it.score }
        }
    }

    /**
     * Everything the profile suggests, ranked and diversified.
     * A query that fails contributes nothing rather than failing the row.
     */
    suspend fun recommend(
        profile: TasteProfile,
        library: Library,
        adult: Boolean,
        limit: Int = 40,
    ): List<Scored> = coroutineScope {
        if (profile.isEmpty) return@coroutineScope emptyList()

        val page = { offset: Int -> 1 + ((profile.rotation + offset) % 3) }
        val today = java.time.LocalDate.now().toString()
        val movieGenres = TasteProfile.movieGenresFor(profile.topGenres.map { it.id }).take(3)
        val tvGenres = TasteProfile.tvGenresFor(profile.topGenres.map { it.id }).take(3)
        val keywords = profile.topKeywords.take(2)

        val calls = buildList {
            if (movieGenres.isNotEmpty()) {
                val or = movieGenres.joinToString("|")
                add(async {
                    tmdb.discover(MediaType.Movie, mapOf(
                        "with_genres" to or, "sort_by" to "popularity.desc",
                        "vote_count.gte" to "150", "release_date.lte" to today,
                    ), page(0)) to "genre"
                })
                add(async {
                    tmdb.discover(MediaType.Movie, mapOf(
                        "with_genres" to or, "sort_by" to "vote_average.desc",
                        "vote_count.gte" to "500", "release_date.lte" to today,
                    ), page(2)) to "quality"
                })
            }
            if (tvGenres.isNotEmpty()) {
                val or = tvGenres.joinToString("|")
                add(async {
                    tmdb.discover(MediaType.Tv, mapOf(
                        "with_genres" to or, "sort_by" to "popularity.desc",
                        "vote_count.gte" to "150", "first_air_date.lte" to today,
                    ), page(0)) to "genre"
                })
                add(async {
                    tmdb.discover(MediaType.Tv, mapOf(
                        "with_genres" to or, "sort_by" to "vote_average.desc",
                        "vote_count.gte" to "300", "first_air_date.lte" to today,
                    ), page(1)) to "quality"
                })
            }
            for (keyword in keywords) {
                add(async {
                    tmdb.discover(MediaType.Movie, mapOf(
                        "with_keywords" to keyword.id.toString(),
                        "sort_by" to "popularity.desc", "vote_count.gte" to "80",
                        "release_date.lte" to today,
                    ), page(2)) to "keyword"
                })
            }
            // What the titles you are actually watching are related to: TMDB's
            // own recommendations for your two strongest seeds.
            for (seed in profile.seeds.take(2)) {
                add(async {
                    runCatching {
                        tmdb.detail(seed.id, seed.type, "US").recommendations
                    }.getOrDefault(emptyList()) to if (seed.reason == SeedReason.Watching) "watching" else "rec"
                })
            }
            add(async { tmdb.trending("all", "week") to "trending" })
        }

        val bySource = calls.map { it.await() }
        val keywordBySource = keywords.map { it.id }

        // Merge duplicates: a title found by three different queries is a
        // stronger signal than one found by a single query, and `consensus`
        // below is what pays for that.
        val merged = LinkedHashMap<String, Scored>()
        for ((items, source) in bySource) {
            for (item in items) {
                val held = merged[item.key]
                merged[item.key] = Scored(
                    item = held?.item ?: item,
                    sources = (held?.sources.orEmpty()) + source,
                    keywordIds = if (source == "keyword") keywordBySource else held?.keywordIds.orEmpty(),
                )
            }
        }

        val norms = Norms(
            genre = max(1.0, profile.genreWeights.values.maxOfOrNull { abs(it) } ?: 1.0),
            keyword = max(1.0, profile.keywordWeights.values.maxOfOrNull { abs(it) } ?: 1.0),
            decade = max(1.0, profile.decadeWeights.values.maxOfOrNull { abs(it) } ?: 1.0),
            language = max(1.0, profile.languageWeights.values.maxOfOrNull { abs(it) } ?: 1.0),
        )

        val ranked = merged.values
            .asSequence()
            .filter { it.item.hasArt }
            .filter { it.item.voteCount >= MIN_VOTES }
            .filter { adult || !it.item.adult }
            // Never recommend something already finished. A SAVED title stays
            // eligible: a recommendation can still be useful after you put it on
            // the list, and hiding it makes the row look emptier than it is.
            .filterNot { library.isWatched(it.item.key) }
            .map { it.copy(score = score(it, profile, norms)) }
            .sortedByDescending { it.score }
            .toList()

        diversify(ranked, limit)
    }

    private data class Norms(
        val genre: Double, val keyword: Double, val decade: Double, val language: Double,
    )

    private fun score(candidate: Scored, profile: TasteProfile, norms: Norms): Double {
        val item = candidate.item

        val genre = item.genreIds.sumOf { profile.genreWeights[it] ?: 0.0 } / norms.genre
        val keyword = candidate.keywordIds.sumOf { profile.keywordWeights[it] ?: 0.0 } / norms.keyword * 0.9
        val source = candidate.sources.maxOfOrNull { SOURCE_BONUS[it] ?: 0.6 } ?: 0.6

        // Confidence rises logarithmically: 8.2 from 20,000 votes deserves more
        // trust than 8.2 from 60, without letting popularity run the row.
        val confidence = min(1.0, log10(max(10.0, item.voteCount.toDouble())) / 4.5)
        val quality = ((item.voteAverage - 5) / 5) * confidence * 0.75 +
            min(log10(1 + item.popularity) / 4, 0.22)

        val year = item.year.toIntOrNull() ?: 0
        val decade = if (year > 1900) {
            (profile.decadeWeights[(year / 10) * 10] ?: 0.0) / norms.decade * 0.4
        } else 0.0

        val language = if (item.originalLanguage.isNotBlank()) {
            (profile.languageWeights[item.originalLanguage] ?: 0.0) / norms.language * 0.28
        } else 0.0

        // Several queries agreeing is itself evidence.
        val consensus = max(0, candidate.sources.size - 1) * 0.22

        // A defining genre you have never engaged with is a strong "no".
        val penalty = item.genreIds.count {
            it in DEFINING_GENRES && (profile.genreWeights[it] ?: 0.0) <= 0.0
        } * 0.6

        // A deterministic jitter keyed to the title and the rotation: the same
        // launch always ranks the same way, and the next one differs. Without it
        // the rails are identical every single day.
        val hash = ((item.id * 2654435761L + profile.rotation * 1013904223L) and 0xFFFFFFFFL)
            .toDouble() / 4294967295.0
        val serendipity = (hash - 0.5) * 0.18

        return genre + keyword + source + quality + decade + language + consensus + serendipity - penalty
    }

    /**
     * Greedy diversification: at each step take the best remaining candidate
     * after subtracting a penalty for genres already used. A row of twenty
     * thrillers scores well and reads badly.
     */
    private fun diversify(ranked: List<Scored>, count: Int, lambda: Double = 0.35): List<Scored> {
        val pool = ranked.toMutableList()
        val out = mutableListOf<Scored>()
        val used = mutableMapOf<Int, Int>()
        while (out.size < count && pool.isNotEmpty()) {
            var bestIndex = 0
            var bestValue = Double.NEGATIVE_INFINITY
            for (index in pool.indices) {
                val genres = pool[index].item.genreIds
                val penalty = if (genres.isEmpty()) 0.0
                else genres.sumOf { (used[it] ?: 0).toDouble() } / genres.size
                val value = pool[index].score - lambda * penalty
                if (value > bestValue) { bestValue = value; bestIndex = index }
            }
            val pick = pool.removeAt(bestIndex)
            pick.item.genreIds.forEach { used[it] = (used[it] ?: 0) + 1 }
            out += pick
        }
        return out
    }

    /**
     * "Because you're watching X" — a title-specific row, which carries a much
     * stricter promise than a general taste row and so is filtered harder.
     */
    suspend fun becauseOf(seed: Seed, library: Library, limit: Int = 20): List<MediaItem> =
        runCatching { tmdb.detail(seed.id, seed.type, "US").recommendations }
            .getOrDefault(emptyList())
            .asSequence()
            .filter { it.hasArt && it.voteCount >= MIN_VOTES }
            .filterNot { library.isWatched(it.key) }
            // Share at least one genre with the seed: TMDB's recommendations
            // wander, and a row named after a show has to look like that show.
            .filter { candidate ->
                seed.genres.isEmpty() || candidate.genreIds.any { it in seed.genres }
            }
            .take(limit)
            .toList()
}
