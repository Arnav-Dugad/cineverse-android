package com.cineverse.app.data.model

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.sign

/**
 * Every episode of a show as one grid.
 *
 * Ported from the website's `js/season-heatmap.js`, including the things it is
 * careful about, because those are what make the panel trustworthy rather than
 * decorative:
 *
 *  - An unrated episode is HATCHED, never coloured as low. Colouring "no data"
 *    as "bad" is the single easiest way to lie with a heatmap.
 *  - An unaired episode is outlined rather than filled.
 *  - Specials (season 0) are left out everywhere — they are not part of a run,
 *    and counting them makes every progress figure wrong.
 *  - Averages, "best" and "standouts" use only episodes that have votes, and
 *    "best" needs at least five, so one person's 10/10 cannot crown an episode.
 *  - Every square carries its numbers in its label, so colour is never the only
 *    signal.
 */
@Immutable
data class HeatCell(
    val season: Int,
    val episode: Int,
    val name: String,
    val rating: Double,
    val votes: Int,
    /** 0–6 by rating, or -1 when there is no community rating at all. */
    val band: Int,
    val aired: Boolean,
    val watched: Boolean,
    val watchedAt: Long,
    val stillPath: String?,
    val airDate: String,
    val runtime: Int,
    /** Difference from this season's own average, or null when it cannot be known. */
    val delta: Double?,
    /** 0–6, 3 meaning "about average"; -1 when [delta] is null. */
    val deltaBand: Int,
    /** Where this episode falls in the order you watched them, or -1. */
    val order: Int,
) {
    val rated: Boolean get() = band >= 0
    val label: String
        get() = buildString {
            append("Season $season episode $episode, $name, ")
            append(
                when {
                    rated -> "rated ${"%.1f".format(rating)} from $votes vote${if (votes == 1) "" else "s"}"
                    aired -> "no rating yet"
                    else -> "not aired yet"
                }
            )
            if (delta != null) {
                append(
                    if (deltaBand == 3) ", about the season average"
                    else ", ${"%.1f".format(abs(delta))} ${if (delta > 0) "above" else "below"} the season average"
                )
            }
            if (watched) append(", watched")
        }
}

@Immutable
data class HeatRow(
    val season: Int,
    val name: String,
    /** The season's average, over rated episodes only. 0 when none are rated. */
    val mean: Double,
    val ratedCount: Int,
    val cells: List<HeatCell>,
)

@Immutable
data class Heatmap(
    val rows: List<HeatRow> = emptyList(),
    val best: HeatCell? = null,
    val strongest: HeatRow? = null,
    /** Aired, unwatched, at or above the show's average — the ones worth catching. */
    val gems: List<HeatCell> = emptyList(),
    val topCount: Int = 0,
    val topSeen: Int = 0,
    val watched: Int = 0,
    val aired: Int = 0,
    val total: Int = 0,
    val lowest: Double = 0.0,
    val highest: Double = 0.0,
    val maxEpisodes: Int = 0,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        /** The seven fixed bands, so a colour means the same score on every show. */
        val RATING_BANDS = listOf(0.0 to "Under 6", 6.0 to "6", 7.0 to "7", 7.5 to "7.5", 8.0 to "8", 8.5 to "8.5", 9.0 to "9+")

        /** Distance from the season average, in rating points, for the standout bands. */
        private val DELTA_STEPS = listOf(0.2, 0.5, 1.0)

        private const val TRUSTED_VOTES = 5

        fun ratingBand(rating: Double, votes: Int): Int {
            if (votes <= 0 || rating <= 0) return -1
            var band = 0
            RATING_BANDS.forEachIndexed { index, (min, _) -> if (rating >= min) band = index }
            return band
        }

        fun deltaBand(delta: Double): Int {
            // A hair's tolerance, so a difference that is exactly a step
            // (8.5 − 8.0) is never pushed below it by floating point.
            val size = abs(delta) + 1e-9
            val level = DELTA_STEPS.count { size >= it }
            return 3 + sign(delta).toInt() * level
        }

        private fun round1(value: Double) = Math.round(value * 10) / 10.0

        fun build(
            seasons: Map<Int, List<Episode>>,
            progress: ShowProgress?,
            now: Long = System.currentTimeMillis(),
        ): Heatmap {
            val today = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            fun airedBy(date: String): Boolean {
                if (date.isBlank()) return false
                val at = runCatching {
                    java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull() ?: return false
                return at <= today
            }

            val rows = seasons.entries
                .filter { it.key > 0 && it.value.isNotEmpty() }
                .sortedBy { it.key }
                .map { (season, episodes) ->
                    val cells = episodes.map { episode ->
                        val rating = round1(episode.voteAverage)
                        val watched = progress?.isWatched(season, episode.number) == true
                        HeatCell(
                            season = season,
                            episode = episode.number,
                            name = episode.name,
                            rating = rating,
                            votes = episode.voteCount,
                            band = ratingBand(rating, episode.voteCount),
                            aired = airedBy(episode.airDate),
                            watched = watched,
                            watchedAt = if (watched) progress?.watchedAt(season, episode.number) ?: 0L else 0L,
                            stillPath = episode.stillPath,
                            airDate = episode.airDate,
                            runtime = episode.runtime,
                            delta = null,
                            deltaBand = -1,
                            order = -1,
                        )
                    }
                    val rated = cells.filter { it.rated }
                    val mean = if (rated.isEmpty()) 0.0 else round1(rated.sumOf { it.rating } / rated.size)
                    // The comparison uses the UNROUNDED average, so rounding can
                    // never flip an episode into the wrong standout band.
                    val exact = if (rated.isEmpty()) 0.0 else rated.sumOf { it.rating } / rated.size
                    val withDelta = if (rated.size >= 2) cells.map { cell ->
                        if (!cell.rated) cell else {
                            val delta = round1(cell.rating - exact)
                            cell.copy(delta = delta, deltaBand = deltaBand(cell.rating - exact))
                        }
                    } else cells
                    HeatRow(season, "Season $season", mean, rated.size, withDelta)
                }

            val all = rows.flatMap { it.cells }
            val ratedAll = all.filter { it.rated }
            val trusted = ratedAll.filter { it.votes >= TRUSTED_VOTES }
            val pool = trusted.ifEmpty { ratedAll }
            val byRating = compareByDescending<HeatCell> { it.rating }
                .thenByDescending { it.votes }.thenBy { it.season }.thenBy { it.episode }

            val best = pool.minWithOrNull(byRating)
            val strongest = rows.filter { it.ratedCount >= 3 }.maxByOrNull { it.mean }
            val showMean = if (pool.isEmpty()) 0.0 else pool.sumOf { it.rating } / pool.size
            // The show's best: its top tenth, at least three and at most ten.
            val topCount = minOf(10, maxOf(3, Math.round(pool.size / 10.0).toInt()))
            val top = pool.sortedWith(byRating).take(minOf(topCount, pool.size))
            val gems = pool.filter { it.aired && !it.watched && it.rating >= showMean }
                .sortedWith(byRating).take(3)

            // The order you watched them, for the first-light animation. An
            // episode with no stamp predates the log and therefore comes first.
            val order = all.filter { it.watched }
                .sortedWith(compareBy({ it.watchedAt }, { it.season }, { it.episode }))
                .mapIndexed { index, cell -> (cell.season to cell.episode) to index }
                .toMap()
            val ordered = rows.map { row ->
                row.copy(cells = row.cells.map { it.copy(order = order[it.season to it.episode] ?: -1) })
            }

            val scores = ratedAll.map { it.rating }
            return Heatmap(
                rows = ordered,
                best = best,
                strongest = strongest,
                gems = gems,
                topCount = top.size,
                topSeen = top.count { it.watched },
                watched = all.count { it.watched },
                aired = all.count { it.aired },
                total = all.size,
                lowest = scores.minOrNull() ?: 0.0,
                highest = scores.maxOrNull() ?: 0.0,
                maxEpisodes = rows.maxOfOrNull { it.cells.size } ?: 0,
            )
        }
    }
}

/** Which colouring the grid is showing. Numbers is a switch over either. */
enum class HeatMode(val label: String) { Rating("Rating"), Standouts("Standouts") }
