package com.cineverse.app.feature.year

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.WatchedItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Your year, ported from the website's js/your-year.js and js/films-year.js.
 *
 * Two kinds of thing make a year: every VIEWING of a film (a rewatch counts
 * again, in the month it happened) and every series FINISHED (counted once, in
 * the month you finished it). The hours add the films' runtimes per viewing to
 * the whole runs of the series finished that year, and say so — it is the
 * honest reading of "how much did I watch", not the only one.
 *
 * Everything here is pure: a library and the show progress in, a summary out.
 */

@Immutable
data class YearFilm(
    val key: String,
    val id: Int,
    val title: String,
    val poster: String,
    /** Viewings inside the year: one per film. */
    val plays: Int,
    val first: Long,
    val dates: List<Long>,
    val minutes: Int,
    val rating: Int,
    val genres: List<Int>,
)

@Immutable
data class YearShow(
    val id: Int,
    val title: String,
    val poster: String,
    val finishedAt: Long,
    val episodes: Int,
    val minutes: Int,
    val startedAt: Long,
)

@Immutable
data class YearSummary(
    val year: Int,
    val films: List<YearFilm>,
    val shows: List<YearShow>,
    val filmMonths: List<Int>,
    val seriesMonths: List<Int>,
) {
    val filmCount: Int get() = films.size
    val seriesCount: Int get() = shows.size
    val titles: Int get() = filmCount + seriesCount
    val viewings: Int get() = films.sumOf { it.plays }
    val episodes: Int get() = shows.sumOf { it.episodes }
    val minutes: Int get() = films.sumOf { it.minutes } + shows.sumOf { it.minutes }
    val byMonth: List<Int> get() = filmMonths.zip(seriesMonths) { a, b -> a + b }

    /** Every month that ties for the most, so a tie is never silently broken. */
    val busiest: List<Int>
        get() {
            val top = byMonth.maxOrNull() ?: 0
            return if (top <= 0) emptyList() else byMonth.indices.filter { byMonth[it] == top }
        }
}

/** One figure compared with the year before. */
@Immutable
data class Delta(val diff: Int, val label: String) {
    val up: Boolean get() = diff > 0
    val down: Boolean get() = diff < 0
}

@Immutable
data class Moment(
    val label: String,
    val note: String,
    val id: Int,
    val type: MediaType,
    val title: String,
    val poster: String,
)

object YearModel {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun yearOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).year
    fun monthOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).monthValue - 1

    /** When a watched film was watched: once, on its latest date. */
    fun viewingDates(entry: WatchedItem): List<Long> =
        listOfNotNull(entry.lastPlay.takeIf { it > 0 })

    /**
     * When a series was finished: the completion stamp, the caught-up stamp,
     * or failing both the last episode ticked.
     */
    fun finishedAt(show: ShowProgress): Long =
        show.completedAt.takeIf { it > 0 }
            ?: show.caughtUpAt.takeIf { it > 0 }
            ?: show.log.maxOfOrNull { it.stamp }
            ?: show.updatedAt

    private fun finished(shows: Map<Int, ShowProgress>): List<ShowProgress> =
        shows.values.filter { it.complete && !it.dropped && finishedAt(it) > 0 }

    /** Every year with a film viewing or a finished series, newest first. */
    fun years(library: Library, shows: Map<Int, ShowProgress>): List<Int> {
        val films = library.watched.values
            .filter { it.type == MediaType.Movie }
            .flatMap { viewingDates(it) }
            .map { yearOf(it) }
        val series = finished(shows).map { yearOf(finishedAt(it)) }
        return (films + series).distinct().sortedDescending()
    }

    fun summary(library: Library, shows: Map<Int, ShowProgress>, year: Int): YearSummary {
        val filmMonths = MutableList(12) { 0 }
        val films = library.watched.values
            .filter { it.type == MediaType.Movie }
            .mapNotNull { entry ->
                val dates = viewingDates(entry).filter { yearOf(it) == year }
                if (dates.isEmpty()) return@mapNotNull null
                dates.forEach { filmMonths[monthOf(it)]++ }
                val runtime = entry.runtime.takeIf { it in 1..999 } ?: 0
                YearFilm(
                    key = entry.key, id = entry.tmdbId, title = entry.title.ifBlank { "Untitled" },
                    poster = entry.poster, plays = dates.size, first = dates.first(), dates = dates,
                    minutes = runtime * dates.size, rating = library.ratingOf(entry.key),
                    genres = entry.genres,
                )
            }
            .sortedWith(compareBy<YearFilm> { it.first }.thenBy { it.title })

        val seriesMonths = MutableList(12) { 0 }
        val series = finished(shows)
            .filter { yearOf(finishedAt(it)) == year }
            .map { show ->
                val at = finishedAt(show)
                seriesMonths[monthOf(at)]++
                YearShow(
                    id = show.tmdbId, title = show.title.ifBlank { "TV show" }, poster = show.poster,
                    finishedAt = at, episodes = show.watchedCount, minutes = show.minutesWatched,
                    startedAt = show.log.minOfOrNull { it.stamp } ?: 0L,
                )
            }
            .sortedBy { it.finishedAt }

        return YearSummary(year, films, series, filmMonths, seriesMonths)
    }

    /** How a year compares with the one before; null when there is no earlier data. */
    fun deltas(current: YearSummary, previous: YearSummary): Triple<Delta, Delta, Delta>? {
        if (previous.titles == 0) return null
        fun one(now: Int, before: Int, format: (Int) -> String): Delta {
            val diff = now - before
            val label = if (diff == 0) "same as ${previous.year}"
            else "${if (diff > 0) "+" else "−"}${format(kotlin.math.abs(diff))} vs ${previous.year}"
            return Delta(diff, label)
        }
        return Triple(
            one(current.filmCount, previous.filmCount) { it.toString() },
            one(current.seriesCount, previous.seriesCount) { it.toString() },
            one(current.minutes, previous.minutes) { "${it / 60}h" },
        )
    }

    /** The year's moments: first and latest watch, top rated, biggest run, most rewatched. */
    fun highlights(summary: YearSummary): List<Moment> {
        data class Dated(val at: Long, val id: Int, val type: MediaType, val title: String, val poster: String)
        val dated = (
            summary.films.flatMap { film ->
                film.dates.map { Dated(it, film.id, MediaType.Movie, film.title, film.poster) }
            } + summary.shows.map { Dated(it.finishedAt, it.id, MediaType.Tv, it.title, it.poster) }
            ).sortedBy { it.at }
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        fun when_(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(day)

        return buildList {
            dated.firstOrNull()?.let { add(Moment("First of the year", when_(it.at), it.id, it.type, it.title, it.poster)) }
            if (dated.size > 1) dated.last().let { add(Moment("Most recent", when_(it.at), it.id, it.type, it.title, it.poster)) }
            summary.films.filter { it.rating > 0 }
                .maxWithOrNull(compareBy<YearFilm> { it.rating }.thenBy { it.plays })
                ?.let { add(Moment("Top rated", "${it.rating}/10", it.id, MediaType.Movie, it.title, it.poster)) }
            summary.shows.maxByOrNull { it.episodes }
                ?.let { add(Moment("Biggest run", "${it.episodes} episodes", it.id, MediaType.Tv, it.title, it.poster)) }
            run {
                summary.shows.filter { it.startedAt > 0 }
                    .minByOrNull { it.finishedAt - it.startedAt }
                    ?.let { show ->
                        val days = ((show.finishedAt - show.startedAt) / 86_400_000L).toInt().coerceAtLeast(1)
                        add(Moment("Fastest finish", "$days day${if (days == 1) "" else "s"}", show.id, MediaType.Tv, show.title, show.poster))
                    }
            }
        }
    }

    /** The year's films by genre, most first, with each one's share of the leader. */
    fun topGenres(summary: YearSummary, limit: Int = 5): List<Triple<String, Int, Float>> {
        val counts = summary.films
            .flatMap { film -> film.genres.distinct().mapNotNull { GenreNames[it] } }
            .groupingBy { it }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
        val top = counts.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
        return counts.map { Triple(it.key, it.value, it.value.toFloat() / top) }
    }
}
