package com.cineverse.app.data.franchise

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import java.time.LocalDate

/**
 * Franchise completion, ported from the website's js/franchise.js.
 *
 * "Part of the Alien Collection" used to be a link and nothing more. It never
 * said how much of it you had actually seen, which is the only thing a tracker
 * should be able to answer instantly.
 *
 * Completion is measured against RELEASED parts only, for the same reason the
 * episode tracker caps at the last aired episode: a collection with an announced
 * sequel is not 80% complete, it is complete, with more coming. Counting a film
 * nobody can watch yet against you produces a number that can never reach 100.
 */

/** One film in a TMDB collection. */
@Immutable
data class CollectionPart(
    val id: Int,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val releaseDate: String = "",
    val vote: Double = 0.0,
    val voteCount: Int = 0,
    val overview: String = "",
) {
    val key: String get() = "movie_$id"
    val year: String get() = releaseDate.take(4)

    fun asItem() = MediaItem(
        id = id, type = MediaType.Movie, title = title, posterPath = poster,
        backdropPath = backdrop, overview = overview, voteAverage = vote,
        voteCount = voteCount, releaseDate = releaseDate,
    )
}

/** A TMDB collection, as the franchise screens need it. */
@Immutable
data class CollectionInfo(
    val id: Int,
    val name: String,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val parts: List<CollectionPart> = emptyList(),
)

enum class PartState { Released, Upcoming, Unknown }

/** Where the viewer stands in one collection. */
@Immutable
data class CollectionProgress(
    val total: Int,
    val released: Int,
    val upcoming: Int,
    val unknown: Int,
    val seen: Int,
    /** Released parts not yet watched, in release order. */
    val unseen: List<CollectionPart>,
    val seenIds: Set<Int>,
) {
    /** The earliest released part not yet watched: "carry on from here". */
    val nextUp: CollectionPart? get() = unseen.firstOrNull()
    val fraction: Float get() = if (released > 0) seen.toFloat() / released else 0f
    val percent: Int get() = (fraction * 100).toInt()
    val complete: Boolean get() = released > 0 && seen == released

    /** "3 of 6 seen" / "Complete" — one honest phrase for a progress meter. */
    val label: String
        get() = when {
            released == 0 -> ""
            complete -> if (upcoming > 0) "Complete so far" else "Complete"
            else -> "$seen of $released seen"
        }
}

/** A collection represented in the watch history, before TMDB is asked. */
@Immutable
data class WatchedCollection(
    val id: Int,
    val name: String,
    val poster: String,
    val seen: Int,
)

object Franchises {

    fun stateOf(part: CollectionPart, today: LocalDate, watched: Set<Int>): PartState {
        val day = part.releaseDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // A film the viewer has demonstrably seen was released, whatever TMDB's
        // date field says — an undated row must never count against them.
        if (day == null) return if (part.id in watched) PartState.Released else PartState.Unknown
        return if (!day.isAfter(today)) PartState.Released else PartState.Upcoming
    }

    /** Release order, undated last, ties broken by id so the order is stable. */
    fun releaseOrdered(parts: List<CollectionPart>): List<CollectionPart> =
        parts.distinctBy { it.id }
            .sortedWith(compareBy<CollectionPart> { it.releaseDate.ifBlank { "9999" } }.thenBy { it.id })

    fun progress(
        parts: List<CollectionPart>,
        watched: Set<Int>,
        today: LocalDate = LocalDate.now(),
    ): CollectionProgress {
        val list = releaseOrdered(parts.filter { it.id > 0 })
        val states = list.associate { it.id to stateOf(it, today, watched) }
        val released = list.filter { states[it.id] == PartState.Released }
        val seenIds = released.map { it.id }.filter { it in watched }.toSet()
        return CollectionProgress(
            total = list.size,
            released = released.size,
            upcoming = states.values.count { it == PartState.Upcoming },
            unknown = states.values.count { it == PartState.Unknown },
            seen = seenIds.size,
            unseen = released.filterNot { it.id in seenIds },
            seenIds = seenIds,
        )
    }

    /**
     * Entries left unseen that sit BEFORE something already watched — films
     * skipped rather than simply not reached yet. "You have three left" and
     * "you skipped the third one" are different problems.
     */
    fun gaps(ordered: List<CollectionPart>, seenIds: Set<Int>): List<CollectionPart> {
        val lastSeen = ordered.indexOfLast { it.id in seenIds }
        if (lastSeen < 0) return emptyList()
        return ordered.take(lastSeen).filterNot { it.id in seenIds }
    }

    /**
     * Roughly how long a finish would take.
     *
     * Runtime is not on a TMDB collection payload, so it can only be estimated
     * from the average length of the entries the viewer HAS seen — the most
     * relevant sample there is. Zero when there is nothing to average, and the
     * screens never show it without saying it is approximate.
     */
    fun remainingMinutes(progress: CollectionProgress, runtimes: Map<Int, Int>): Int {
        if (progress.unseen.isEmpty()) return 0
        val sample = progress.seenIds.mapNotNull { runtimes[it] }.filter { it in 1..399 }
        if (sample.isEmpty()) return 0
        return (sample.average() * progress.unseen.size).toInt()
    }

    /**
     * Every collection the watch history touches, from the `collectionId`
     * stamped on watched documents. Biggest commitments first.
     */
    fun watchedCollections(library: Library): List<WatchedCollection> =
        library.watched.values
            .filter { it.type == MediaType.Movie && it.collectionId > 0 }
            .groupBy { it.collectionId }
            .map { (id, films) ->
                WatchedCollection(
                    id = id,
                    name = films.firstNotNullOfOrNull { it.collectionName.ifBlank { null } }.orEmpty(),
                    poster = films.firstNotNullOfOrNull { it.collectionPoster.ifBlank { null } }.orEmpty(),
                    seen = films.map { it.tmdbId }.distinct().size,
                )
            }
            .sortedWith(compareByDescending<WatchedCollection> { it.seen }.thenBy { it.name })

    /** The ids of every film marked watched, which is what completion is measured with. */
    fun watchedFilmIds(library: Library): Set<Int> =
        library.watched.values.filter { it.type == MediaType.Movie }.map { it.tmdbId }.toSet()

    /** Minutes per watched film, for the finish estimate. */
    fun runtimes(library: Library): Map<Int, Int> =
        library.watched.values
            .filter { it.type == MediaType.Movie && it.runtime > 0 }
            .associate { it.tmdbId to it.runtime }
}

/** "2h 14m", "45m", "3h". Empty for nothing. */
fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}h ${rest}m"
        hours > 0 -> "${hours}h"
        else -> "${rest}m"
    }
}
