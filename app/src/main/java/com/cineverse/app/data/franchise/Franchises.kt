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

// ---------- television families ----------

/** A family of shows grouped by the franchise name in their titles. */
@Immutable
data class TvFamily(
    val key: String,
    val name: String,
    /** Every member TMDB's search found, plus any watched show it missed, oldest first. */
    val members: List<MediaItem>,
    val seenIds: Set<Int>,
    /** Members not watched and not dropped, oldest first. */
    val unseen: List<MediaItem>,
) {
    val found: Int get() = members.size
    val seen: Int get() = seenIds.size
    val fraction: Float get() = if (found > 0) seen.toFloat() / found else 0f
    val complete: Boolean get() = found > 0 && seen >= found
    val nextUp: MediaItem? get() = unseen.firstOrNull()
}

/**
 * TMDB has collections for film and nothing for television, so a Star Trek or
 * a Law & Order can only be grouped by NAME - ported from the website. It is
 * deliberately strict: a show joins a family only when its title declares the
 * franchise before a colon or a dash, or when its whole title IS a stem another
 * show declared. A looser rule would put "Love, Death & Robots" in a family with
 * "Love Island" and invent a franchise nobody is in.
 */
object TvFamilies {

    private val Split = Regex("""^(.{2,40}?)\s*[:–—]\s+\S""")
    private val Dash = Regex("""^(.{2,40}?)\s+-\s+\S""")

    /** The franchise a title declares, or "" when it declares none. */
    fun stem(title: String): String {
        val clean = title.trim()
        if (clean.isEmpty()) return ""
        val match = Split.find(clean) ?: Dash.find(clean) ?: return ""
        val stem = match.groupValues[1].trim()
        return if (stem.length >= 3 && stem != clean) stem else ""
    }

    /** Case and punctuation never split a family; "&" is spelled "and". */
    fun fold(value: String): String =
        value.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Watched shows grouped into named families of two or more. (stem, shows) */
    fun families(library: Library): List<Pair<String, List<com.cineverse.app.data.model.WatchedItem>>> {
        val shows = library.watched.values.filter { it.type == MediaType.Tv && it.title.isNotBlank() }
        val declared = HashMap<String, String>()
        for (show in shows) stem(show.title).takeIf { it.isNotEmpty() }?.let { declared[fold(it)] = it }
        if (declared.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<com.cineverse.app.data.model.WatchedItem>>()
        for (show in shows) {
            val key = fold(stem(show.title).ifEmpty { show.title })
            if (key !in declared) continue
            groups.getOrPut(key) { mutableListOf() } += show
        }
        return groups.filterValues { it.size >= 2 }
            .map { (key, members) -> declared.getValue(key) to members.toList() }
            .sortedWith(compareByDescending<Pair<String, List<com.cineverse.app.data.model.WatchedItem>>> { it.second.size }.thenBy { it.first })
    }

    /** Does a search result belong to the family, or merely mention it? */
    fun belongs(title: String, stem: String): Boolean {
        val f = fold(title)
        val s = fold(stem)
        if (f == s) return true
        val declared = stem(title)
        return declared.isNotEmpty() && fold(declared) == s
    }
}
