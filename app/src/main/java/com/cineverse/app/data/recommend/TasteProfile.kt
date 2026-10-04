package com.cineverse.app.data.recommend

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import kotlin.math.exp
import kotlin.math.floor

/** One signal the profile learned, strongest first. */
@Immutable
data class Signal(val id: Int, val name: String, val weight: Double)

/** A title the profile can point at and say "because of this one". */
@Immutable
data class Seed(
    val id: Int,
    val type: MediaType,
    val title: String,
    val poster: String,
    val genres: List<Int>,
    val reason: SeedReason,
    val score: Double,
)

@Immutable
data class PersonSignal(val id: Int, val name: String, val profile: String, val weight: Double)

enum class SeedReason { Rated, Watching, Watched, Saved }

/**
 * What the app believes you like.
 *
 * Ported from the website's `buildTasteProfile`, including the weights it
 * arrived at, because those weights are the product of a lot of tuning against
 * a real library and inventing new ones here would only make the two surfaces
 * disagree about the same person.
 *
 * The shape of it: every title you have touched contributes weight to genres,
 * themes, decades and languages, scaled by how strong the signal is and how
 * recent it is. A watchlist entry is an explicit "I want this" and counts for
 * more than a view; a rating pushes a title's weight up or down around the
 * neutral 5; a thing watched last week counts for more than the same thing
 * watched two years ago.
 */
@Immutable
data class TasteProfile(
    val genreWeights: Map<Int, Double> = emptyMap(),
    val keywordWeights: Map<Int, Double> = emptyMap(),
    val decadeWeights: Map<Int, Double> = emptyMap(),
    val languageWeights: Map<String, Double> = emptyMap(),
    val topGenres: List<Signal> = emptyList(),
    val topKeywords: List<Signal> = emptyList(),
    val seeds: List<Seed> = emptyList(),
    /** The actors and directors you watch most, rating-weighted, as the website ranks them. */
    val topActors: List<PersonSignal> = emptyList(),
    val topDirectors: List<PersonSignal> = emptyList(),
    /** Changes once per launch, so the rails are a different slice each time. */
    val rotation: Int = 0,
    val titlesSeen: Int = 0,
) {
    val isEmpty: Boolean get() = titlesSeen == 0

    /** The one title worth naming a rail after. */
    fun headlineSeed(): Seed? =
        seeds.firstOrNull { it.reason == SeedReason.Watching }
            ?: seeds.firstOrNull { it.reason == SeedReason.Rated && it.score >= 8 }
            ?: seeds.firstOrNull()

    companion object {

        /** A rating's pull, around a neutral 5: a 9 adds 4, a 2 subtracts 3. */
        private fun ratingWeight(rating: Int): Double = if (rating > 0) (rating - 5).toDouble() else 0.0

        /**
         * How much a signal decays with age. Never below 0.75: something you
         * watched three years ago still says something about you — it just says
         * less than last week does.
         */
        private fun recency(at: Long): Double {
            if (at <= 0) return 1.0
            val days = ((System.currentTimeMillis() - at) / 86_400_000.0).coerceAtLeast(0.0)
            return 0.75 + 0.65 * exp(-days / 365.0)
        }

        private fun decadeOf(year: Int) = (floor(year / 10.0) * 10).toInt()

        /**
         * A film genre's television counterpart. TMDB uses different ids for the
         * same idea across the two — Action is 28 for a film and 10759 for a
         * series — so a taste built mostly from films would never find a series
         * without this.
         */
        private val TV_GENRE_FOR = mapOf(
            28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765,
            10752 to 10768, 53 to 80, 27 to 9648,
        )

        private val MOVIE_GENRES = setOf(
            28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 53, 10752, 37,
        )
        private val TV_GENRES = setOf(
            10759, 16, 35, 80, 99, 18, 10751, 10762, 9648, 10765, 10768, 37,
        )

        fun tvGenresFor(genres: List<Int>): List<Int> =
            genres.mapNotNull { if (it in TV_GENRES) it else TV_GENRE_FOR[it] }.distinct()

        fun movieGenresFor(genres: List<Int>): List<Int> = genres.filter { it in MOVIE_GENRES }

        private fun people(weights: Map<Int, Double>, names: Map<Int, Pair<String, String>>) = weights.entries
            .filter { it.value >= 1.5 && names[it.key]?.first?.isNotBlank() == true }
            .sortedByDescending { it.value }
            .take(6)
            .map { PersonSignal(it.key, names.getValue(it.key).first, names.getValue(it.key).second, it.value) }

        fun build(
            library: Library,
            shows: Map<Int, ShowProgress>,
            rotation: Int,
        ): TasteProfile {
            val genres = mutableMapOf<Int, Double>()
            val keywords = mutableMapOf<Int, Double>()
            val decades = mutableMapOf<Int, Double>()
            val languages = mutableMapOf<String, Double>()
            val seeds = mutableListOf<Seed>()
            val actors = mutableMapOf<Int, Double>()
            val directors = mutableMapOf<Int, Double>()
            val names = mutableMapOf<Int, Pair<String, String>>()

            fun bump(map: MutableMap<Int, Double>, key: Int, w: Double) {
                if (key == 0) return
                map[key] = (map[key] ?: 0.0) + w
            }

            // The list: an explicit "I want to see this", and therefore the
            // strongest genre signal there is.
            for (item in library.saved.values) {
                val rating = library.ratingOf(item.key)
                val weight = (2.0 + ratingWeight(rating)) * recency(item.addedAt)
                item.genres.forEach { bump(genres, it, weight) }
                item.keywords.forEach { bump(keywords, it, weight * 0.72) }
                if (item.language.isNotBlank()) {
                    languages[item.language] = (languages[item.language] ?: 0.0) + weight * 0.35
                }
                if (rating >= 8) {
                    seeds += Seed(
                        item.tmdbId, item.type, item.title, item.poster,
                        item.genres, SeedReason.Rated, rating.toDouble(),
                    )
                }
            }

            // Watched: what you actually consumed. The decade and language of a
            // thing you finished says more than one you only saved.
            for (item in library.watched.values) {
                val rating = library.ratingOf(item.key)
                val weight = (1.5 + ratingWeight(rating)) * recency(item.watchedAt)
                item.genres.forEach { bump(genres, it, weight) }
                item.keywords.forEach { bump(keywords, it, weight * 0.72) }
                val year = item.releaseDate.take(4).toIntOrNull() ?: item.year.toIntOrNull()
                if (year != null && year > 1900) bump(decades, decadeOf(year), weight * 0.5)
                if (item.language.isNotBlank()) {
                    languages[item.language] = (languages[item.language] ?: 0.0) + weight * 0.35
                }
                if (rating >= 8) {
                    seeds += Seed(
                        item.tmdbId, item.type, item.title, item.poster,
                        item.genres, SeedReason.Rated, rating.toDouble(),
                    )
                }
                // People, exactly as the website weighs them: one for each
                // title, plus half a point for every point of rating above 5.
                val personWeight = 1 + 0.5 * ratingWeight(rating)
                if (item.directorId > 0) {
                    bump(directors, item.directorId, personWeight)
                    if (item.director.isNotBlank()) names[item.directorId] = item.director to item.directorProfile
                }
                for (person in item.cast.take(5)) {
                    bump(actors, person.id, personWeight)
                    names[person.id] = person.name to person.profile
                }
            }

            // A show you are IN THE MIDDLE OF is the loudest signal in the whole
            // profile — it is what you are watching tonight, not what you watched
            // once in 2021 — so it is weighted highest and named first.
            val watchingWindow = 45L * 86_400_000
            for (show in shows.values) {
                if (show.dropped || show.watchedCount == 0) continue
                val last = show.log.lastOrNull()?.stamp ?: show.updatedAt
                val active = last > 0 && System.currentTimeMillis() - last < watchingWindow
                val weight = (if (active) 3.0 else 1.6) * recency(last)
                // A progress document carries no genres, so the saved or watched
                // copy of the same show is where they come from.
                val meta = library.saved["tv_${show.tmdbId}"]?.genres
                    ?: library.watched["tv_${show.tmdbId}"]?.genres
                    ?: emptyList()
                meta.forEach { bump(genres, it, weight) }
                if (active) {
                    seeds += Seed(
                        show.tmdbId, MediaType.Tv, show.title, show.poster,
                        meta, SeedReason.Watching, 9.0 + show.watchedCount / 100.0,
                    )
                }
            }

            fun top(map: Map<Int, Double>, min: Double) = map.entries
                .filter { it.value >= min }
                .sortedByDescending { it.value }
                .map { Signal(it.key, GenreNames[it.key].orEmpty(), it.value) }

            return TasteProfile(
                genreWeights = genres,
                keywordWeights = keywords,
                decadeWeights = decades,
                languageWeights = languages,
                topGenres = top(genres, 1.5),
                topKeywords = keywords.entries
                    .filter { it.value >= 1.5 }
                    .sortedByDescending { it.value }
                    .map { Signal(it.key, "", it.value) },
                seeds = seeds.sortedByDescending { it.score },
                topActors = people(actors, names),
                topDirectors = people(directors, names),
                rotation = rotation,
                titlesSeen = library.saved.size + library.watched.size + shows.size,
            )
        }
    }
}
