package com.cineverse.app.data.recap

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.ShowProgress
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Season recaps, series finales and viewing patterns, ported from the
 * website's js/season-recap.js, js/series-finale.js, js/pacing.js and the
 * viewing log in js/episodes.js. Pure functions over a show's progress
 * document; the numbers match the website's for the same account.
 */

/** One row of the log, with whether it was a real sitting or bookkeeping. */
@Immutable
data class Viewing(val season: Int, val episode: Int, val at: Long, val viewing: Boolean)

@Immutable
data class Pattern(
    /** "on weeknights", "late at night", "on weekend mornings". */
    val phrase: String,
    val sittings: Int,
    val days: Int,
)

@Immutable
data class TopEpisode(
    val season: Int,
    val number: Int,
    val name: String,
    val rating: Double,
    val votes: Int,
    val still: String?,
)

@Immutable
data class SeasonRecap(
    val season: Int,
    val episodes: Int,
    val viewingEpisodes: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val spanDays: Int,
    /** Episodes a day across the span, 0 with fewer than two viewings. */
    val pace: Double,
    val bingeDays: Int,
    val longestSitting: Int,
    val minutes: Int,
    val pattern: Pattern?,
    val top: TopEpisode?,
    /** Everything was swept in with "mark season" - there is no viewing to describe. */
    val marked: Boolean,
)

@Immutable
data class SeriesRecap(
    val seasons: List<SeasonRecap>,
    val episodes: Int,
    val minutes: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val spanDays: Int,
    val pace: Double,
    val bingeDays: Int,
    val longestSitting: Int,
    val fastest: SeasonRecap?,
    val top: TopEpisode?,
    val pattern: Pattern?,
    val marked: Boolean,
) {
    val seasonCount: Int get() = seasons.size
}

object Recaps {

    private const val DAY = 86_400_000L
    private const val BINGE = 3
    private const val SITTING_MINUTES = 360
    private const val SITTING_EPISODES = 6
    private const val EVENING_MINUTES = 180
    private const val EVENING_EPISODES = 3
    private const val MIN_SITTINGS = 5
    private const val MIN_DAYS = 3

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun day(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /**
     * The log, each row marked as a real viewing or not.
     *
     * A single tick is always a viewing. A bulk sweep is one only when the
     * batch is small enough to have been watched in one sitting - three
     * episodes or three hours for the very first batch (which is usually "I
     * had already seen these"), six or six hours afterwards.
     */
    fun viewingLog(show: ShowProgress): List<Viewing> {
        val rows = show.log.filter { it.stamp > 0 }
        if (rows.isEmpty()) return emptyList()
        val batches = rows.filter { it.bulk }.groupingBy { it.stamp }.eachCount()
        val firstAt = rows.minOf { it.stamp }
        val runtime = show.episodeRuntime
        return rows.map { row ->
            if (!row.bulk) return@map Viewing(row.season, row.episode, row.stamp, true)
            val size = batches[row.stamp] ?: 1
            val first = row.stamp == firstAt
            val limitMinutes = if (first) EVENING_MINUTES else SITTING_MINUTES
            val limitEpisodes = if (first) EVENING_EPISODES else SITTING_EPISODES
            val sitting = if (runtime > 0) size * runtime <= limitMinutes else size <= limitEpisodes
            Viewing(row.season, row.episode, row.stamp, sitting)
        }
    }

    private fun partOfDay(hour: Int) = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "late"
    }

    private val Phrases = mapOf(
        "weekdays" to mapOf("evening" to "on weeknights", "late" to "late on weeknights", "morning" to "on weekday mornings", "afternoon" to "on weekday afternoons", "" to "on weekdays"),
        "weekends" to mapOf("evening" to "on weekend evenings", "late" to "late on weekends", "morning" to "on weekend mornings", "afternoon" to "on weekend afternoons", "" to "on weekends"),
        "week" to mapOf("evening" to "in the evenings", "late" to "late at night", "morning" to "in the mornings", "afternoon" to "in the afternoons", "" to ""),
    )

    /**
     * When someone watches, from their sitting times - or null without a clear
     * pattern. Five sittings over three days at least; a sitting after
     * midnight belongs to the night before.
     */
    fun pattern(stamps: Collection<Long>): Pattern? {
        val sittings = stamps.filter { it > 0 }.distinct()
        if (sittings.size < MIN_SITTINGS) return null
        val days = HashSet<LocalDate>()
        var weekend = 0
        val parts = mutableMapOf("morning" to 0, "afternoon" to 0, "evening" to 0, "late" to 0)
        for (at in sittings) {
            val moment = Instant.ofEpochMilli(at).atZone(zone)
            val night = if (moment.hour < 5) moment.minusHours(5) else moment
            days += night.toLocalDate()
            if (night.dayOfWeek == DayOfWeek.SATURDAY || night.dayOfWeek == DayOfWeek.SUNDAY) weekend++
            val part = partOfDay(moment.hour)
            parts[part] = (parts[part] ?: 0) + 1
        }
        if (days.size < MIN_DAYS) return null
        val share = weekend.toDouble() / sittings.size
        val whenKey = when {
            share >= 0.6 -> "weekends"
            share <= 0.2 -> "weekdays"
            else -> "week"
        }
        val (topPart, topCount) = parts.entries.maxByOrNull { it.value }!!.toPair()
        val time = if (topCount.toDouble() / sittings.size >= 0.5) topPart else ""
        val phrase = Phrases.getValue(whenKey).getValue(time)
        if (phrase.isBlank()) return null
        return Pattern(phrase, sittings.size, days.size)
    }

    fun sittings(show: ShowProgress): List<Long> =
        viewingLog(show).filter { it.viewing }.map { it.at }

    /**
     * "You watch Severance on weeknights, The Bear on weekends" - shows watched
     * in the last ninety days, most sittings first, one show per pattern.
     */
    fun insight(shows: Collection<ShowProgress>, now: Long = System.currentTimeMillis(), limit: Int = 3): String {
        val since = now - 90 * DAY
        val ranked = shows
            .filter { !it.dropped && it.title.isNotBlank() }
            .mapNotNull { show ->
                val stamps = sittings(show)
                val recent = stamps.count { it >= since }
                val profile = pattern(stamps) ?: return@mapNotNull null
                if (recent == 0) null else Triple(show, profile, recent to stamps.size)
            }
            .sortedWith(compareByDescending<Triple<ShowProgress, Pattern, Pair<Int, Int>>> { it.third.first }
                .thenByDescending { it.third.second })
        val picked = mutableListOf<Pair<String, String>>()
        val phrases = HashSet<String>()
        for ((show, profile, _) in ranked) {
            if (!phrases.add(profile.phrase)) continue
            picked += show.title to profile.phrase
            if (picked.size == limit) break
        }
        if (picked.isEmpty()) return ""
        return "You watch " + picked.joinToString(", ") { (title, phrase) -> "$title $phrase" }
    }

    /** "2 episodes a day", "4 episodes a week", "1 episode a month". */
    fun paceLabel(pace: Double): String {
        fun unit(count: Number, span: String): String {
            val shown = if (count is Double && count % 1.0 != 0.0) "%.1f".format(Locale.US, count) else count.toInt().toString()
            return "$shown episode${if (shown == "1") "" else "s"} a $span"
        }
        return when {
            pace >= 0.95 -> unit((pace * 10).roundToInt() / 10.0, "day")
            pace * 7 >= 0.95 -> unit((pace * 7).roundToInt(), "week")
            else -> unit(maxOf(1, (pace * 30).roundToInt()), "month")
        }
    }

    private fun top(episodes: List<Episode>, watched: Set<Int>, season: Int): TopEpisode? {
        val rated = episodes.filter { it.voteAverage > 0 && it.number in watched }
        val trusted = rated.filter { it.voteCount >= 3 }
        val pick = (trusted.ifEmpty { rated })
            .sortedWith(compareByDescending<Episode> { it.voteAverage }.thenByDescending { it.voteCount })
            .firstOrNull() ?: return null
        return TopEpisode(season, pick.number, pick.name, (pick.voteAverage * 10).roundToInt() / 10.0, pick.voteCount, pick.stillPath)
    }

    private fun span(viewings: List<Viewing>): Int {
        if (viewings.isEmpty()) return 0
        val first = day(viewings.minOf { it.at })
        val last = day(viewings.maxOf { it.at })
        return ChronoUnit.DAYS.between(first, last).toInt() + 1
    }

    fun season(show: ShowProgress, season: Int, episodes: List<Episode>): SeasonRecap {
        val watched = show.seasons[season].orEmpty().toSet()
        val rows = viewingLog(show).filter { it.season == season }
        val viewing = rows.filter { it.viewing }
        val perDay = viewing.groupingBy { day(it.at) }.eachCount()
        val spanDays = span(viewing)
        val byNumber = episodes.associateBy { it.number }
        return SeasonRecap(
            season = season,
            episodes = watched.size,
            viewingEpisodes = viewing.size,
            startedAt = rows.minOfOrNull { it.at } ?: 0,
            finishedAt = rows.maxOfOrNull { it.at } ?: 0,
            spanDays = spanDays,
            pace = if (viewing.size >= 2) viewing.size.toDouble() / maxOf(1, spanDays) else 0.0,
            bingeDays = perDay.values.count { it >= BINGE },
            longestSitting = perDay.values.maxOrNull() ?: 0,
            minutes = watched.sumOf { number ->
                byNumber[number]?.runtime?.takeIf { it > 0 } ?: show.episodeRuntime
            },
            pattern = pattern(viewing.map { it.at }),
            top = top(episodes, watched, season),
            marked = viewing.isEmpty(),
        )
    }

    fun series(show: ShowProgress, seasons: Map<Int, List<Episode>>): SeriesRecap {
        val numbers = show.seasons.filter { (season, list) -> season > 0 && list.isNotEmpty() }.keys.sorted()
        val recaps = numbers.map { season(show, it, seasons[it].orEmpty()) }
        val rows = viewingLog(show).filter { it.season > 0 }
        val viewing = rows.filter { it.viewing }
        val perDay = viewing.groupingBy { day(it.at) }.eachCount()
        val spanDays = span(viewing)
        val paced = recaps.filter { !it.marked && it.pace > 0 && it.viewingEpisodes >= 2 }
        val fastest = if (paced.size >= 2) {
            paced.sortedWith(
                compareByDescending<SeasonRecap> { it.pace }.thenBy { it.spanDays }.thenBy { it.season }
            ).first()
        } else null
        val tops = recaps.mapNotNull { it.top }
        val trusted = tops.filter { it.votes >= 3 }
        return SeriesRecap(
            seasons = recaps,
            episodes = recaps.sumOf { it.episodes },
            minutes = recaps.sumOf { it.minutes },
            startedAt = rows.minOfOrNull { it.at } ?: 0,
            finishedAt = rows.maxOfOrNull { it.at } ?: 0,
            spanDays = spanDays,
            pace = if (viewing.size >= 2) viewing.size.toDouble() / maxOf(1, spanDays) else 0.0,
            bingeDays = perDay.values.count { it >= BINGE },
            longestSitting = perDay.values.maxOrNull() ?: 0,
            fastest = fastest,
            top = trusted.ifEmpty { tops }
                .sortedWith(compareByDescending<TopEpisode> { it.rating }.thenByDescending { it.votes }.thenBy { it.season })
                .firstOrNull(),
            pattern = pattern(viewing.map { it.at }),
            marked = viewing.isEmpty(),
        )
    }

    /** "3 Aug – 12 Sept 2026", or one date when it all happened in a day. */
    fun dates(start: Long, end: Long): String {
        if (start <= 0) return ""
        val a = day(start)
        val b = day(end)
        val long = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        if (a == b) return b.format(long)
        return "${if (a.year == b.year) a.format(short) else a.format(long)} – ${b.format(long)}"
    }

    /** The figures a recap shows, in order, empty ones left out. */
    fun seasonFigures(recap: SeasonRecap): List<Pair<String, String>> = buildList {
        add("Episodes" to recap.episodes.toString())
        if (!recap.marked) {
            add("Days" to recap.spanDays.toString())
            if (recap.pace > 0) add("Pace" to paceLabel(recap.pace).replace(Regex(" episodes?"), ""))
            add("Binge days" to recap.bingeDays.toString())
            add("Longest sitting" to "${recap.longestSitting} ep${if (recap.longestSitting == 1) "" else "s"}")
        }
        if (recap.minutes > 0) add("Watch time" to com.cineverse.app.data.franchise.formatMinutes(recap.minutes))
    }.take(6)

    fun seriesFigures(recap: SeriesRecap): List<Pair<String, String>> = buildList {
        add("Seasons" to recap.seasonCount.toString())
        add("Episodes" to recap.episodes.toString())
        if (!recap.marked) {
            add("Days" to recap.spanDays.toString())
            if (recap.pace > 0) add("Overall pace" to paceLabel(recap.pace).replace(Regex(" episodes?"), ""))
            add("Binge days" to recap.bingeDays.toString())
        }
        if (recap.minutes > 0) add("Watch time" to com.cineverse.app.data.franchise.formatMinutes(recap.minutes))
        if (recap.marked && recap.top != null) add("Best episode" to "%.1f".format(Locale.US, recap.top.rating))
    }.take(6)
}
