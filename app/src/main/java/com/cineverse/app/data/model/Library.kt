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

/** A person named on a watched title: enough to rank them and draw their face. */
@Immutable
data class CastRef(val id: Int, val name: String, val profile: String)

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
    /**
     * How many times this has been seen. The website writes `plays`; a document
     * without it predates the field and means ONE viewing, never zero.
     */
    val plays: Int = 1,
    /** The dates it was seen, oldest first. Shorter than [plays] on old rows. */
    val playDates: List<Long> = emptyList(),
    val lastPlayedAt: Long = 0L,
    /** The first billed actors, as the website backfills them: who you watched. */
    val cast: List<CastRef> = emptyList(),
    val directorProfile: String = "",
) {
    /** The most recent viewing we know of, falling back to the first. */
    val lastPlay: Long
        get() = lastPlayedAt.takeIf { it > 0 }
            ?: playDates.maxOrNull()
            ?: watchedAt

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
    /** Where it sits among your lists: the website's `order`, smallest first. */
    val order: Int = 0,
    val locked: Boolean = false,
    /**
     * The website's PIN lock, `lock: { salt, hash, ... }` on the list document:
     * PBKDF2-SHA256 over the PIN, 150,000 rounds. Both empty means no PIN.
     */
    val lockSalt: String = "",
    val lockHash: String = "",
) {
    val hasPin: Boolean get() = lockSalt.isNotBlank() && lockHash.isNotBlank()
}

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

    /**
     * The episode numbers a season actually uses.
     *
     * For most shows that is 1..count. For an ABSOLUTE-numbered show — One
     * Piece, Doraemon, most long-running anime — TMDB numbers episodes
     * continuously across seasons, so season 23 might run 1086..1110 and there
     * is no episode 1 in it at all.
     *
     * Ported from the website's `episodeNumbersFor`. Everything that walks a
     * season has to go through this: the five places in this app that counted
     * `1..count` instead were asking whether One Piece episode 1 was in season
     * 23, which it is not and never will be, so "next up" pointed at an episode
     * that does not exist.
     */
    fun episodeNumbers(season: Int): List<Int> {
        val count = structure[season] ?: return emptyList()
        if (!isAbsolute) return (1..count).toList()
        val before = structure.entries.filter { it.key < season }.sumOf { it.value }
        return (1..count).map { before + it }
    }

    /** True when TMDB numbers this show continuously across its seasons. */
    val isAbsolute: Boolean
        get() {
            if (numberingMode == "absolute") return true
            if (numberingMode == "season" && episodeModelV >= MODEL_VERSION) return false
            // The website's inference, for documents written before the mode was
            // recorded: an aired episode number larger than its own season is
            // only possible under continuous numbering.
            val airedSeasonSize = structure[airedSeason] ?: 0
            return airedSeasonSize > 0 && airedEpisode > airedSeasonSize
        }

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
            for (episode in episodeNumbers(season)) {
                if (isWatched(season, episode)) continue
                if (!hasAired(season, episode)) return null
                return season to episode
            }
        }
        return null
    }

    fun hasAired(season: Int, episode: Int): Boolean {
        if (airedSeason <= 0) return true
        // Under continuous numbering the episode number alone settles it, and
        // comparing seasons as well would call episode 1106 unaired simply
        // because TMDB files it under a later season than the one now airing.
        if (isAbsolute) return episode <= airedEpisode
        return season < airedSeason || (season == airedSeason && episode <= airedEpisode)
    }

    /**
     * Episodes still to watch that have actually AIRED.
     *
     * "2 left" on a show you are caught up on, where the second is next week's,
     * reads as a nag about something nobody can watch yet. The totals count
     * every episode TMDB has filed; this counts only the ones you could press
     * play on today.
     */
    val airedRemaining: Int
        get() = structure.keys.sumOf { season ->
            episodeNumbers(season).count { episode ->
                !isWatched(season, episode) && hasAired(season, episode)
            }
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

/**
 * `users/{uid}/movieProgress/movie_{id}` — where you stopped in a film.
 *
 * Two things about this document that cost a day to find:
 *
 *  - **[seconds], not minutes.** The website stores `position` and `runtime` in
 *    SECONDS (`runtime: Math.round(minutes * 60)`). An earlier build of this app
 *    read them as minutes, so a film 54 minutes in reported "5,217m left".
 *  - **A delete is a TOMBSTONE, not a deletion.** Removing a film from Continue
 *    Watching on the website writes `{ tmdbId, deleted: true, updatedAt }` over
 *    the document rather than deleting it, so that an offline delete cannot be
 *    resurrected by an older copy on another device. A reader that ignores the
 *    flag shows every film the user has ever removed. All five of the author's
 *    own documents were tombstones, and all five were on screen.
 */
@Immutable
data class MovieProgress(
    val tmdbId: Int,
    /** Seconds into the film. Zero is MEANINGFUL: it means started, not absent. */
    val seconds: Int = 0,
    /** The film's length in seconds, or 0 when it was never recorded. */
    val runtimeSeconds: Int = 0,
    val title: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** The website's tombstone. A row carrying this is not in Continue Watching. */
    val deleted: Boolean = false,
) {
    val minutes: Int get() = seconds / 60
    val runtimeMinutes: Int get() = runtimeSeconds / 60
    val minutesLeft: Int get() = ((runtimeSeconds - seconds).coerceAtLeast(0)) / 60

    val fraction: Float
        get() = if (runtimeSeconds > 0) (seconds.toFloat() / runtimeSeconds).coerceIn(0f, 1f) else 0f
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
    /** True when the show numbers its episodes continuously across seasons. */
    val absolute: Boolean = false,
) {
    /**
     * What to call the next episode.
     *
     * An absolute-numbered show must never read "S23 E1086" — nobody says that,
     * and the season number is an artefact of how TMDB files the show rather
     * than anything a viewer tracks. The website says "Episode 1086"; so does
     * this.
     */
    val label: String
        get() = when {
            isMovie -> "${minutesLeft}m left"
            absolute -> "EP $episode"
            else -> "S$season E$episode"
        }
}
