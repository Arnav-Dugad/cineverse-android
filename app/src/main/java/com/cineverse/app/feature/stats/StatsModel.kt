package com.cineverse.app.feature.stats

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.WatchedItem
import java.util.Calendar
import java.util.Locale

/**
 * The deep panels.
 *
 * Every figure here is computed from documents the app already has in memory —
 * the watchlist, the watched list, the ratings, the episode log. Not one of them
 * costs a request, which is why the whole page can appear at once rather than
 * filling in panel by panel the way the website's does.
 *
 * Where the website needs TMDB to answer something (a director's full
 * filmography, a franchise's missing entries, provider freshness) the panel is
 * left out rather than approximated. A statistic that is nearly right is worse
 * than one that is absent, because you cannot tell by looking which kind you
 * are reading.
 */

@Immutable
data class RatingIntel(
    val average: Double = 0.0,
    val total: Int = 0,
    val coverage: Int = 0,
    val generous: Int = 0,
    val personality: String = "",
    /** Score 1..10 -> how many. */
    val distribution: List<Int> = List(10) { 0 },
) {
    val any: Boolean get() = total > 0
}

@Immutable
data class LibraryIntel(
    val completion: Int = 0,
    val watched: Int = 0,
    val saved: Int = 0,
    val facts: List<Triple<String, String, String>> = emptyList(),
)

@Immutable
data class TvIntel(
    val tracked: Int = 0,
    val finished: Int = 0,
    val inFlight: Int = 0,
    val dropped: Int = 0,
    val episodes: Int = 0,
    val completion: Int = 0,
    val biggestDay: Pair<Long, Int>? = null,
    val closest: List<ShowClose> = emptyList(),
)

@Immutable
data class ShowClose(val id: Int, val title: String, val poster: String, val left: Int, val fraction: Float)

@Immutable
data class Slice(val name: String, val count: Int, val share: Float)

@Immutable
data class TasteMap(
    val decades: List<Slice> = emptyList(),
    val languages: List<Slice> = emptyList(),
    val countries: List<Slice> = emptyList(),
)

@Immutable
data class Rewatch(val id: Int, val title: String, val poster: String, val plays: Int, val minutes: Int)

@Immutable
data class TasteShift(val month: String, val genre: String, val language: String)

@Immutable
data class HealthLine(val label: String, val missing: Int, val total: Int) {
    val share: Float get() = if (total > 0) missing.toFloat() / total else 0f
}

@Immutable
data class PersonCount(val id: Int, val name: String, val count: Int)

@Immutable
data class Franchise(
    val id: Int,
    val name: String,
    val poster: String,
    val watched: Int,
)

@Immutable
data class Trophy(
    val name: String,
    val detail: String,
    val progress: Float,
    val earned: Boolean,
)

@Immutable
data class DeepStats(
    val rating: RatingIntel = RatingIntel(),
    val library: LibraryIntel = LibraryIntel(),
    val tv: TvIntel = TvIntel(),
    val taste: TasteMap = TasteMap(),
    val rewatches: List<Rewatch> = emptyList(),
    val shifts: List<TasteShift> = emptyList(),
    val health: List<HealthLine> = emptyList(),
    val directors: List<PersonCount> = emptyList(),
    val franchises: List<Franchise> = emptyList(),
    val trophies: List<Trophy> = emptyList(),
)

// ---------- the computations ----------

internal fun buildDeepStats(
    lib: Library,
    shows: Map<Int, ShowProgress>,
    totalMinutes: Int,
    streak: Int,
    longestStreak: Int,
): DeepStats {
    val watched = lib.watched.values.toList()
    val films = watched.filter { it.type == MediaType.Movie }

    return DeepStats(
        rating = ratingIntel(lib, watched.size),
        library = libraryIntel(lib, watched),
        tv = tvIntel(shows),
        taste = tasteMap(watched),
        rewatches = rewatches(shows),
        shifts = tasteShifts(watched),
        health = health(watched),
        directors = directors(watched),
        franchises = franchises(films),
        trophies = trophies(lib, shows, totalMinutes, streak, longestStreak),
    )
}

private fun ratingIntel(lib: Library, watchedCount: Int): RatingIntel {
    val scores = lib.ratings.values.filter { it in 1..10 }
    if (scores.isEmpty()) return RatingIntel()
    val average = scores.sum().toDouble() / scores.size
    val generous = scores.count { it >= 8 }
    val distribution = List(10) { index -> scores.count { it == index + 1 } }
    return RatingIntel(
        average = average,
        total = scores.size,
        coverage = if (watchedCount > 0) (scores.size * 100 / watchedCount).coerceAtMost(100) else 0,
        generous = generous,
        // The website calls this a critic style, and the labels are its labels,
        // so the same library describes the same person on both.
        personality = when {
            average >= 8.5 -> "Enthusiast"
            average >= 7.5 -> "Appreciative"
            average >= 6.5 -> "Balanced"
            average >= 5.5 -> "Discerning"
            else -> "Hard to please"
        },
        distribution = distribution,
    )
}

private fun libraryIntel(lib: Library, watched: List<WatchedItem>): LibraryIntel {
    val known = lib.saved.keys + lib.watched.keys
    val completion = if (known.isEmpty()) 0 else (watched.size * 100 / known.size)

    val dated = watched.filter { it.year.length >= 4 }
    val oldest = dated.minByOrNull { it.year }
    val newest = dated.maxByOrNull { it.year }
    // Films only. A series' runtime field holds the whole run on some
    // documents, which made Doraemon "the longest" at a thousand hours.
    val longest = watched.filter { it.type == MediaType.Movie && it.runtime in 1..999 }.maxByOrNull { it.runtime }
    val topLanguage = watched.mapNotNull { it.language.takeIf { l -> l.isNotBlank() } }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }
    val topCountry = watched.mapNotNull { it.country.takeIf { c -> c.isNotBlank() } }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }

    val facts = buildList {
        oldest?.let { add(Triple("Oldest", it.title, it.year)) }
        newest?.let { add(Triple("Newest", it.title, it.year)) }
        longest?.let { add(Triple("Longest film", it.title, com.cineverse.app.data.franchise.formatMinutes(it.runtime))) }
        topLanguage?.let {
            add(Triple("Language", languageName(it.key), "${it.value} titles"))
        }
        topCountry?.let { add(Triple("Country", countryName(it.key), "${it.value} titles")) }
        val unrated = watched.count { lib.ratingOf(it.key) == 0 }
        add(Triple("Unrated", unrated.toString(), "of ${watched.size} watched"))
    }

    return LibraryIntel(
        completion = completion,
        watched = watched.size,
        saved = lib.saved.size,
        facts = facts,
    )
}

private fun tvIntel(shows: Map<Int, ShowProgress>): TvIntel {
    val tracked = shows.values.filter { it.watchedCount > 0 }
    if (tracked.isEmpty()) return TvIntel()

    val finished = tracked.count { it.complete }
    val dropped = tracked.count { it.dropped }
    val inFlight = tracked.count { !it.complete && !it.dropped }
    val episodes = tracked.sumOf { it.watchedCount }
    val totalKnown = tracked.sumOf { it.totalEpisodes }

    // The biggest sitting: the day the most episodes were ticked. Bulk marks are
    // included because a season you swept through in one evening really was one
    // evening, and excluding them would make a real binge invisible.
    //
    // Unless the day could not physically have happened. An import, or "mark
    // the whole show" on a long anime, files hundreds of episodes under one
    // date; 1,886 episodes is not a sitting. A day whose marks add up to more
    // than twenty-four hours of runtime is bookkeeping, and only its single
    // ticks are counted.
    val byDay = tracked
        .flatMap { show ->
            val minutes = if (show.episodeRuntime > 0) show.episodeRuntime else 42
            show.log.map { row -> row to minutes }
        }
        .groupBy { (row, _) -> startOfDayLocal(row.stamp) }
        .mapValues { (_, rows) ->
            val runtime = rows.sumOf { (_, minutes) -> minutes }
            if (runtime <= 24 * 60) rows.size else rows.count { (row, _) -> !row.bulk }
        }
        .filterValues { it > 0 }
    val biggest = byDay.maxByOrNull { it.value }?.let { it.key to it.value }

    // Closest to finishing, because that is the question this panel exists for.
    val closest = tracked
        .filter { !it.complete && !it.dropped && it.totalEpisodes > 0 }
        .map { show ->
            ShowClose(
                id = show.tmdbId,
                title = show.title,
                poster = show.poster,
                left = show.airedRemaining,
                fraction = (show.watchedCount.toFloat() / show.totalEpisodes).coerceIn(0f, 1f),
            )
        }
        // Caught up is not "close to finishing": there is nothing to watch yet.
        .filter { it.left > 0 }
        .sortedWith(compareBy({ it.left }, { -it.fraction }))
        .take(5)

    return TvIntel(
        tracked = tracked.size,
        finished = finished,
        inFlight = inFlight,
        dropped = dropped,
        episodes = episodes,
        completion = if (totalKnown > 0) (episodes * 100 / totalKnown) else 0,
        biggestDay = biggest,
        closest = closest,
    )
}

private fun tasteMap(watched: List<WatchedItem>): TasteMap {
    fun slices(values: List<String>, take: Int): List<Slice> {
        val counts = values.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(take)
        val total = counts.sumOf { it.value }.coerceAtLeast(1)
        return counts.map { Slice(it.key, it.value, it.value.toFloat() / total) }
    }

    return TasteMap(
        decades = slices(
            watched.mapNotNull { it.year.take(4).toIntOrNull() }.map { "${(it / 10) * 10}s" },
            6,
        ),
        languages = slices(watched.map { languageName(it.language) }, 6),
        // Named, not coded: "IN" is a two-letter puzzle, "India" is an answer.
        countries = slices(watched.map { countryName(it.country) }, 6),
    )
}

/**
 * What you keep going back to.
 *
 * `seasonPlays` counts how many times a season has been completed, so a value
 * above one is a genuine rewatch rather than a long first run. Shows with no
 * repeat at all are left out entirely: a "rewatches" panel listing things you
 * watched once is just the library again.
 */
private fun rewatches(shows: Map<Int, ShowProgress>): List<Rewatch> =
    shows.values.mapNotNull { show ->
        val extra = show.seasonPlays.values.sumOf { (it - 1).coerceAtLeast(0) }
        if (extra <= 0) return@mapNotNull null
        val perSeason = if (show.structure.isNotEmpty()) {
            show.totalEpisodes / show.structure.size.coerceAtLeast(1)
        } else show.watchedCount
        Rewatch(
            id = show.tmdbId,
            title = show.title,
            poster = show.poster,
            plays = show.seasonPlays.values.maxOrNull() ?: 1,
            minutes = extra * perSeason * (show.episodeRuntime.takeIf { it > 0 } ?: 42),
        )
    }.sortedByDescending { it.minutes }.take(6)

/**
 * The leading genre and language, month by month.
 *
 * Six months, newest last, and only months with something in them — printing
 * "no data" four times running tells you nothing except that you were busy.
 */
private fun tasteShifts(watched: List<WatchedItem>): List<TasteShift> {
    val byMonth = watched.filter { it.watchedAt > 0 }.groupBy { monthOf(it.watchedAt) }
    return byMonth.entries
        .sortedBy { it.key }
        .takeLast(6)
        .mapNotNull { (month, items) ->
            val genre = items.flatMap { it.genres }.mapNotNull { GenreNames[it] }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return@mapNotNull null
            val language = items.map { languageName(it.language) }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            TasteShift(monthLabel(month), genre, language)
        }
}

/** What the library is missing, as counts rather than a vague score. */
private fun health(watched: List<WatchedItem>): List<HealthLine> {
    if (watched.isEmpty()) return emptyList()
    val total = watched.size
    val films = watched.filter { it.type == MediaType.Movie }
    return listOfNotNull(
        HealthLine("Artwork", watched.count { it.poster.isBlank() }, total),
        HealthLine("Release date", watched.count { it.releaseDate.isBlank() && it.year.isBlank() }, total),
        HealthLine("Genres", watched.count { it.genres.isEmpty() }, total),
        HealthLine("Runtime", films.count { it.runtime <= 0 }, films.size).takeIf { films.isNotEmpty() },
        HealthLine("Director", films.count { it.director.isBlank() }, films.size).takeIf { films.isNotEmpty() },
        HealthLine("Language", watched.count { it.language.isBlank() }, total),
    ).filter { it.total > 0 }
}

private fun directors(watched: List<WatchedItem>): List<PersonCount> =
    watched.filter { it.directorId > 0 && it.director.isNotBlank() }
        .groupBy { it.directorId }
        .map { (id, items) -> PersonCount(id, items.first().director, items.size) }
        .filter { it.count >= 2 }
        .sortedByDescending { it.count }
        .take(8)

private fun franchises(films: List<WatchedItem>): List<Franchise> =
    films.filter { it.collectionId > 0 && it.collectionName.isNotBlank() }
        .groupBy { it.collectionId }
        .map { (id, items) ->
            Franchise(
                id = id,
                name = items.first().collectionName.removeSuffix(" Collection"),
                poster = items.firstNotNullOfOrNull { it.collectionPoster.takeIf { p -> p.isNotBlank() } }
                    ?: items.first().poster,
                watched = items.size,
            )
        }
        .filter { it.watched >= 2 }
        .sortedByDescending { it.watched }
        .take(8)

/**
 * Milestones.
 *
 * Every one is derived from the account rather than awarded for opening the app,
 * and every one shows its progress bar even before it is earned — a locked
 * trophy with no sense of how far away it is is just a grey box.
 */
private fun trophies(
    lib: Library,
    shows: Map<Int, ShowProgress>,
    totalMinutes: Int,
    streak: Int,
    longestStreak: Int,
): List<Trophy> {
    val watched = lib.watched.size
    val episodes = shows.values.sumOf { it.watchedCount }
    val finished = shows.values.count { it.complete }
    val rated = lib.ratings.size
    val perfect = lib.ratings.values.count { it == 10 }
    val hours = totalMinutes / 60
    val decades = lib.watched.values.mapNotNull { it.year.take(4).toIntOrNull() }
        .map { it / 10 }.distinct().size
    val languages = lib.watched.values.map { it.language }.filter { it.isNotBlank() }.distinct().size

    fun trophy(name: String, detail: String, value: Int, target: Int) = Trophy(
        name = name,
        detail = detail,
        progress = (value.toFloat() / target).coerceIn(0f, 1f),
        earned = value >= target,
    )

    return listOf(
        trophy("Century", "100 titles watched", watched, 100),
        trophy("Five hundred", "500 titles watched", watched, 500),
        trophy("Marathon", "1,000 episodes", episodes, 1_000),
        trophy("Completist", "25 shows finished", finished, 25),
        trophy("Critic", "250 titles rated", rated, 250),
        trophy("Masterpiece", "10 perfect tens", perfect, 10),
        trophy("A thousand hours", "1,000 hours watched", hours, 1_000),
        trophy("Time traveller", "Titles from 8 decades", decades, 8),
        trophy("Polyglot", "10 original languages", languages, 10),
        trophy("Habit", "A 30-day streak", maxOf(streak, longestStreak), 30),
    ).sortedWith(compareByDescending<Trophy> { it.earned }.thenByDescending { it.progress })
}

// ---------- small helpers ----------

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfDayLocal(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthOf(millis: Long): String = Calendar.getInstance().apply {
    timeInMillis = millis
}.let { "%04d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

private fun monthLabel(key: String): String {
    val year = key.substringBefore('-').toIntOrNull() ?: return key
    val month = key.substringAfter('-').toIntOrNull() ?: return key
    val calendar = Calendar.getInstance().apply { set(year, month - 1, 1) }
    return java.text.SimpleDateFormat("MMM yy", Locale.getDefault()).format(calendar.time)
}

/**
 * A two-letter code is not a language.
 *
 * The common ones are named outright and everything else falls back to the
 * platform's own name for the tag, which is correct far more often than a
 * hand-written list and is already localised.
 */
internal fun countryName(code: String): String {
    if (code.isBlank()) return ""
    return runCatching {
        Locale("", code).getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != code }
    }.getOrNull() ?: code.uppercase()
}

internal fun languageName(code: String): String {
    if (code.isBlank()) return ""
    return runCatching {
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
            .takeIf { it.isNotBlank() && it != code }
    }.getOrNull() ?: code.uppercase()
}
