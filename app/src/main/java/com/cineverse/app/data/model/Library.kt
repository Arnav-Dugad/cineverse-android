package com.cineverse.app.data.model

import androidx.compose.runtime.Immutable

/**
 * The user's own data, in the exact shape the website's Firestore documents
 * carry. These are not a parallel model: a field here is a field there, and
 * anything the app does not understand is preserved on write rather than
 * dropped, so a round trip through the phone never costs the website a value.
 */

/** `users/{uid}/watchlist/{type_id}` */
@Immutable
data class SavedItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val poster: String = "",
    val rating: Double = 0.0,
    val year: String = "",
    val genres: List<Int> = emptyList(),
    val keywords: List<Int> = emptyList(),
    val runtime: Int = 0,
    val language: String = "",
    val country: String = "",
    val releaseDate: String = "",
    val voteCount: Int = 0,
    val lists: List<String> = listOf("watchlist"),
    val addedAt: Long = 0L,
) {
    val key: String get() = "${type.wire}_$tmdbId"

    fun asItem() = MediaItem(
        id = tmdbId, type = type, title = title, posterPath = poster.ifBlank { null },
        voteAverage = rating, voteCount = voteCount, releaseDate = releaseDate.ifBlank { year },
        genreIds = genres, originalLanguage = language,
    )
}

/** `users/{uid}/watched/{type_id}` */
@Immutable
data class WatchedItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val poster: String = "",
    val year: String = "",
    val genres: List<Int> = emptyList(),
    val keywords: List<Int> = emptyList(),
    val runtime: Int = 0,
    val episodeRuntime: Int = 0,
    val episodeCount: Int = 0,
    val language: String = "",
    val country: String = "",
    val releaseDate: String = "",
    val tmdbRating: Double = 0.0,
    val voteCount: Int = 0,
    val director: String = "",
    val directorId: Int = 0,
    val collectionId: Int = 0,
    val collectionName: String = "",
    val collectionPoster: String = "",
    val watchedAt: Long = 0L,
) {
    val key: String get() = "${type.wire}_$tmdbId"

    fun asItem() = MediaItem(
        id = tmdbId, type = type, title = title, posterPath = poster.ifBlank { null },
        voteAverage = tmdbRating, voteCount = voteCount,
        releaseDate = releaseDate.ifBlank { year }, genreIds = genres, originalLanguage = language,
    )
}

/** `users/{uid}/lists/{listId}` */
@Immutable
data class UserList(
    val id: String,
    val name: String,
    val icon: String = "",
    val createdAt: Long = 0L,
    val locked: Boolean = false,
)

/**
 * One row of the watch log: an episode, when it was marked, and whether the
 * mark was a single deliberate tick or part of a bulk sweep.
 *
 * On the wire this is the STRING `"season.episode.stamp.bulk"`, never a nested
 * array — Firestore rejects an array of arrays, which on the website silently
 * stopped every show with history from syncing at all. The encoding boundary is
 * [ShowProgress.encodeLog]/[decodeLogRow] and nowhere else.
 */
@Immutable
data class LogRow(
    val season: Int,
    val episode: Int,
    val stamp: Long,
    val bulk: Boolean,
) {
    val episodeKey: Long get() = season * 100_000L + episode
    fun encode(): String = "$season.$episode.$stamp.${if (bulk) 1 else 0}"
}

/** `users/{uid}/progress/{tv_id}` — one document per show. */
@Immutable
data class ShowProgress(
    val tmdbId: Int,
    val title: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val episodeRuntime: Int = 0,
    val status: String = "",
    /** season number -> the episode numbers watched in it */
    val seasons: Map<Int, List<Int>> = emptyMap(),
    /** season number -> how many episodes that season has */
    val structure: Map<Int, Int> = emptyMap(),
    /** season number -> episodes deliberately un-ticked */
    val removed: Map<Int, List<Int>> = emptyMap(),
    val airedSeason: Int = 0,
    val airedEpisode: Int = 0,
    val numberingMode: String = "season",
    val log: List<LogRow> = emptyList(),
    val seasonPlays: Map<Int, Int> = emptyMap(),
    val caughtUpAt: Long = 0L,
    val completedAt: Long = 0L,
    val metaCheckedAt: Long = 0L,
    val dropped: Boolean = false,
    val droppedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val episodeModelV: Int = MODEL_VERSION,
    /** Fields this build did not recognise, carried through untouched. */
    val extras: Map<String, Any?> = emptyMap(),
) {
    val key: String get() = "tv_$tmdbId"

    /** How many episodes the show has, specials excluded. */
    val totalEpisodes: Int get() = structure.values.sum()

    val watchedCount: Int get() = seasons.values.sumOf { it.size }

    val complete: Boolean
        get() = totalEpisodes > 0 && watchedCount >= totalEpisodes

    fun isWatched(season: Int, episode: Int): Boolean =
        seasons[season]?.contains(episode) == true

    fun watchedIn(season: Int): Int = seasons[season]?.size ?: 0

    /** When an episode was marked, or 0 — what the diary and the heatmap read. */
    fun watchedAt(season: Int, episode: Int): Long =
        log.lastOrNull { it.season == season && it.episode == episode }?.stamp ?: 0L

    /**
     * The next episode to watch: the lowest unwatched one that has aired.
     * Nulls when the viewer is caught up, which is a different state from
     * "finished" and the UI says so differently.
     */
    fun nextUp(): Pair<Int, Int>? {
        val seasonNumbers = structure.keys.sorted()
        for (season in seasonNumbers) {
            val count = structure[season] ?: continue
            for (episode in 1..count) {
                if (isWatched(season, episode)) continue
                if (!hasAired(season, episode)) return null
                return season to episode
            }
        }
        return null
    }

    private fun hasAired(season: Int, episode: Int): Boolean {
        if (airedSeason <= 0) return true
        return season < airedSeason || (season == airedSeason && episode <= airedEpisode)
    }

    /** Minutes watched, for the stats page and the hours clubs. */
    val minutesWatched: Int
        get() = watchedCount * (if (episodeRuntime > 0) episodeRuntime else 42)

    companion object {
        const val MODEL_VERSION = 2
        const val LOG_CAP = 400

        fun decodeLogRow(raw: Any?): LogRow? {
            val parts = when (raw) {
                is String -> raw.split('.')
                is List<*> -> raw.map { it.toString() }
                else -> return null
            }
            if (parts.size < 3) return null
            val season = parts[0].toDoubleOrNull()?.toInt() ?: return null
            val episode = parts[1].toDoubleOrNull()?.toInt() ?: return null
            val stamp = parts[2].toDoubleOrNull()?.toLong() ?: return null
            // `parts[3]` is the string "0" for a single tick, and a non-empty
            // string is truthy in a language that is not Kotlin — the bug the
            // website had to fix, kept impossible here by comparing properly.
            val bulk = parts.getOrNull(3)?.let { it == "1" || it == "true" } ?: false
            if (season <= 0 || episode <= 0 || stamp <= 0) return null
            return LogRow(season, episode, stamp, bulk)
        }

        /**
         * One row per episode, newest deliberate mark winning.
         *
         * The rule, ported exactly from the website after it got this wrong:
         * a single tick always outranks a bulk sweep, so "Mark season" can never
         * rewrite the day you actually sat and watched an episode; between two
         * marks of the same kind, the later one wins, so an episode you un-ticked
         * and watched again today reads as today.
         */
        fun cleanLog(rows: List<LogRow>): List<LogRow> {
            val byEpisode = LinkedHashMap<Long, LogRow>()
            for (row in rows) {
                val held = byEpisode[row.episodeKey]
                val better = held == null ||
                    (!row.bulk && held.bulk) ||
                    (row.bulk == held.bulk && row.stamp > held.stamp)
                if (better) byEpisode[row.episodeKey] = row
            }
            return byEpisode.values.sortedBy { it.stamp }.takeLast(LOG_CAP)
        }

        fun encodeLog(rows: List<LogRow>): List<String> = rows.map { it.encode() }
    }
}

/** `users/{uid}/movieProgress/{id}` — where you stopped in a film. */
@Immutable
data class MovieProgress(
    val tmdbId: Int,
    val position: Int = 0,
    val runtime: Int = 0,
    val updatedAt: Long = 0L,
) {
    val fraction: Float get() = if (runtime > 0) (position.toFloat() / runtime).coerceIn(0f, 1f) else 0f
}

/** A row of Continue Watching: a show mid-run, or a film mid-play. */
@Immutable
data class ContinueRow(
    val item: MediaItem,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeName: String = "",
    val stillPath: String? = null,
    val remaining: Int = 0,
    val progress: Float = 0f,
    val lastAt: Long = 0L,
    val isMovie: Boolean = false,
    val minutesLeft: Int = 0,
) {
    val label: String
        get() = if (isMovie) "${minutesLeft}m left" else "S$season E$episode"
}
