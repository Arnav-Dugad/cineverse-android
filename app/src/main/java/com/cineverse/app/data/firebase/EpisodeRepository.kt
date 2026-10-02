package com.cineverse.app.data.firebase

import com.cineverse.app.data.model.LogRow
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.TitleDetail
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/**
 * Per-episode tracking: `users/{uid}/progress/{tv_id}`, one document per show,
 * byte-for-byte the shape the website writes.
 *
 * Two things here are not negotiable, both learned the hard way on the website:
 *
 * 1. **The log is a list of STRINGS.** Firestore refuses an array of arrays, and
 *    when the website stored tuples every show with history silently stopped
 *    syncing — the write failed, retried, and failed forever. Encoding happens
 *    at this boundary and nowhere else.
 *
 * 2. **Every write is a TRANSACTION that merges with the server copy.** Two
 *    devices, or a device that was offline, must converge on the same document,
 *    and "last writer wins" loses real viewing history. [merge] is the website's
 *    rule ported exactly: a side with no opinion never overrules one that has
 *    one, a single tick always outranks a bulk sweep, and a tie prefers the
 *    removal — which is what makes the merge symmetric.
 */
class EpisodeRepository(
    private val store: FirebaseFirestore,
    private val auth: AuthRepository,
    scope: CoroutineScope,
) {
    private fun progressRef(uid: String, showId: Int) =
        store.collection("users").document(uid).collection("progress").document("tv_$showId")

    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<Map<Int, ShowProgress>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else store.collection("users").document(uid).collection("progress").snapshots()
        }
        .map { docs -> docs.mapNotNull { it.toProgress() }.associateBy { it.tmdbId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun of(showId: Int): ShowProgress? = progress.value[showId]

    // ---------- the tick ----------

    /**
     * Mark one episode watched, or un-mark it. The single most-used write in the
     * app, so it does the least possible work: one transaction, one document.
     */
    suspend fun toggleEpisode(show: TitleDetail, season: Int, episode: Int): Boolean {
        val current = of(show.id)
        val watched = current?.isWatched(season, episode) == true
        write(show) { entry ->
            val now = System.currentTimeMillis()
            if (watched) entry.without(season, listOf(episode))
            else entry.with(season, listOf(episode), now, bulk = false)
        }
        return !watched
    }

    /** Everything from the first episode up to and including this one. */
    suspend fun markUpTo(show: TitleDetail, season: Int, episode: Int) {
        write(show) { entry ->
            var next = entry
            val now = System.currentTimeMillis()
            for (s in entry.structure.keys.sorted()) {
                if (s > season) break
                // An earlier season is taken WHOLE; only the season being
                // marked up to is cut at an episode. Capping an earlier season
                // at its episode COUNT was right for 1..n numbering and wrong
                // for absolute: season 2 of One Piece is numbered 62..77, so a
                // cap of 16 matched none of it.
                val numbers = next.episodeNumbers(s)
                val wanted = if (s == season) numbers.filter { it <= episode } else numbers
                val episodes = wanted.filterNot { next.isWatched(s, it) }
                if (episodes.isNotEmpty()) next = next.with(s, episodes, now, bulk = true)
            }
            next
        }
    }

    suspend fun setSeasonWatched(show: TitleDetail, season: Int, watched: Boolean) {
        write(show) { entry ->
            val count = entry.structure[season] ?: 0
            if (count <= 0) entry
            else if (watched) entry.with(season, entry.episodeNumbers(season), System.currentTimeMillis(), bulk = true)
            else entry.without(season, entry.episodeNumbers(season))
        }
    }

    suspend fun markShowWatched(show: TitleDetail) {
        write(show) { entry ->
            var next = entry
            val now = System.currentTimeMillis()
            for (season in entry.structure.keys) {
                val episodes = next.episodeNumbers(season).filterNot { next.isWatched(season, it) }
                if (episodes.isNotEmpty()) next = next.with(season, episodes, now, bulk = true)
            }
            next
        }
    }

    suspend fun clearShow(show: TitleDetail) {
        val uid = auth.uid.value ?: return
        progressRef(uid, show.id).delete().await()
    }

    /** "I gave up on this." A decision, not an absence, so a merge can reason about it. */
    suspend fun setDropped(show: TitleDetail, dropped: Boolean) {
        write(show) { entry ->
            entry.copy(dropped = dropped, droppedAt = if (dropped) System.currentTimeMillis() else 0L)
        }
    }

    // ---------- the write path ----------

    /**
     * Read the server's copy, merge it with ours, apply the change, write it
     * back — all inside one transaction, so two phones ticking at once cannot
     * lose a mark between them.
     */
    private suspend fun write(show: TitleDetail, change: (ShowProgress) -> ShowProgress) {
        val uid = auth.uid.value ?: return
        val ref = progressRef(uid, show.id)
        val local = of(show.id) ?: ShowProgress(tmdbId = show.id)
        val withMeta = local.withMetadata(show)
        store.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val server = snapshot.toProgress()
            val base = if (server == null) withMeta else merge(server, withMeta)
            val next = change(base).copy(updatedAt = System.currentTimeMillis())
            transaction.set(ref, next.toDocument())
            null
        }.await()
    }

    private fun ShowProgress.withMetadata(show: TitleDetail): ShowProgress = copy(
        title = show.title,
        poster = show.posterPath.orEmpty(),
        backdrop = show.backdropPath.orEmpty(),
        episodeRuntime = show.episodeRuntime.takeIf { it > 0 } ?: episodeRuntime,
        status = show.status,
        structure = show.seasons.associate { it.number to it.episodeCount }.ifEmpty { structure },
        airedSeason = show.lastEpisode?.season ?: airedSeason,
        airedEpisode = show.lastEpisode?.number ?: airedEpisode,
        metaCheckedAt = System.currentTimeMillis(),
    )

    private fun ShowProgress.with(season: Int, episodes: List<Int>, now: Long, bulk: Boolean): ShowProgress {
        if (episodes.isEmpty()) return this
        val watched = ((seasons[season].orEmpty() + episodes).distinct()).sorted()
        // Watched always wins inside one document: an episode cannot be both
        // ticked and deliberately removed.
        val gone = removed[season].orEmpty().filterNot { episodes.contains(it) }
        val rows = log + episodes.map { LogRow(season, it, now, bulk) }
        return copy(
            seasons = seasons + (season to watched),
            removed = if (gone.isEmpty()) removed - season else removed + (season to gone),
            log = ShowProgress.cleanLog(rows),
        )
    }

    private fun ShowProgress.without(season: Int, episodes: List<Int>): ShowProgress {
        val watched = seasons[season].orEmpty().filterNot { episodes.contains(it) }
        val gone = (removed[season].orEmpty() + episodes).distinct().sorted()
        return copy(
            seasons = if (watched.isEmpty()) seasons - season else seasons + (season to watched),
            removed = removed + (season to gone),
            log = log.filterNot { it.season == season && episodes.contains(it.episode) },
        )
    }

    companion object {

        /**
         * Two copies of the same show into one. Ported from the website's
         * `mergeEntries`, including the tie-break that makes it symmetric:
         * merging A into B and B into A give the same document.
         */
        fun merge(server: ShowProgress, local: ShowProgress): ShowProgress {
            val tied = server.updatedAt == local.updatedAt
            val localWins = local.updatedAt >= server.updatedAt
            val newer = if (localWins) local else server
            val older = if (localWins) server else local

            val allSeasons = (server.seasons.keys + local.seasons.keys +
                server.removed.keys + local.removed.keys).toSortedSet()
            val seasons = mutableMapOf<Int, List<Int>>()
            val removed = mutableMapOf<Int, List<Int>>()
            for (season in allSeasons) {
                val newWatched = newer.seasons[season].orEmpty().toSet()
                val newGone = newer.removed[season].orEmpty().toSet()
                val oldWatched = older.seasons[season].orEmpty().toSet()
                val oldGone = older.removed[season].orEmpty().toSet()
                val watched = mutableListOf<Int>()
                val gone = mutableListOf<Int>()
                for (episode in (newWatched + newGone + oldWatched + oldGone)) {
                    val inNew = if (episode in newWatched) "w" else if (episode in newGone) "r" else ""
                    val inOld = if (episode in oldWatched) "w" else if (episode in oldGone) "r" else ""
                    // A side with no opinion never overrules one that has one.
                    // Both and agreeing: that is the answer. Both and differing:
                    // the newer document decides — unless they were written in
                    // the same millisecond, where removal wins, because an
                    // un-tick the viewer has to redo is a kinder mistake than an
                    // episode reappearing after they removed it.
                    val verdict = when {
                        inOld.isEmpty() -> inNew
                        inNew.isEmpty() -> inOld
                        inNew == inOld -> inNew
                        tied -> "r"
                        else -> inNew
                    }
                    if (verdict == "w") watched += episode else if (verdict == "r") gone += episode
                }
                if (watched.isNotEmpty()) seasons[season] = watched.sorted()
                if (gone.isNotEmpty()) removed[season] = gone.sorted()
            }

            val structure = (server.structure.keys + local.structure.keys).associateWith { season ->
                maxOf(server.structure[season] ?: 0, local.structure[season] ?: 0)
            }.filterValues { it > 0 }

            val plays = (server.seasonPlays.keys + local.seasonPlays.keys).associateWith { season ->
                maxOf(server.seasonPlays[season] ?: 0, local.seasonPlays[season] ?: 0)
            }.filterValues { it > 1 }

            // The earliest genuine finish, so a merge can never postpone one.
            fun milestone(pick: (ShowProgress) -> Long): Long {
                val values = listOfNotNull(
                    pick(server).takeIf { it > 0 },
                    pick(local).takeIf { it > 0 },
                )
                return values.minOrNull() ?: 0L
            }

            return newer.copy(
                tmdbId = newer.tmdbId.takeIf { it > 0 } ?: older.tmdbId,
                title = newer.title.ifBlank { older.title },
                poster = newer.poster.ifBlank { older.poster },
                backdrop = newer.backdrop.ifBlank { older.backdrop },
                episodeRuntime = newer.episodeRuntime.takeIf { it > 0 } ?: older.episodeRuntime,
                status = newer.status.ifBlank { older.status },
                numberingMode = if (server.numberingMode == "absolute" || local.numberingMode == "absolute")
                    "absolute" else "season",
                seasons = seasons,
                removed = removed,
                structure = structure,
                seasonPlays = plays,
                airedSeason = maxOf(server.airedSeason, local.airedSeason),
                airedEpisode = if (server.airedSeason >= local.airedSeason) server.airedEpisode else local.airedEpisode,
                log = ShowProgress.cleanLog(server.log + local.log),
                caughtUpAt = milestone { it.caughtUpAt },
                completedAt = milestone { it.completedAt },
                metaCheckedAt = maxOf(server.metaCheckedAt, local.metaCheckedAt),
                dropped = if (tied) server.dropped && local.dropped else newer.dropped,
                droppedAt = maxOf(server.droppedAt, local.droppedAt),
                episodeModelV = ShowProgress.MODEL_VERSION,
                extras = older.extras + newer.extras,
            )
        }
    }
}

// ---------- the Firestore boundary ----------

/** Everything this build writes. Fields it does not know are carried in `extras`. */
internal fun ShowProgress.toDocument(): Map<String, Any?> {
    val known = mapOf(
        "tmdbId" to tmdbId,
        "title" to title,
        "poster" to poster,
        "backdrop" to backdrop,
        "episodeRuntime" to episodeRuntime,
        "status" to status,
        "seasons" to seasons.mapKeys { it.key.toString() },
        "structure" to structure.mapKeys { it.key.toString() },
        "removed" to removed.mapKeys { it.key.toString() },
        "aired" to mapOf("season" to airedSeason, "episode" to airedEpisode),
        "numberingMode" to numberingMode,
        "episodeModelV" to ShowProgress.MODEL_VERSION,
        // Strings, never tuples — see the class comment.
        "log" to ShowProgress.encodeLog(log),
        "seasonPlays" to seasonPlays.mapKeys { it.key.toString() },
        "caughtUpAt" to caughtUpAt,
        "completedAt" to completedAt,
        "metaCheckedAt" to metaCheckedAt,
        "dropped" to dropped,
        "droppedAt" to droppedAt,
        "updatedAt" to updatedAt,
        "serverUpdatedAt" to FieldValue.serverTimestamp(),
    )
    // A field a newer build of the website wrote survives a round trip through
    // this one rather than being silently dropped.
    return extras + known
}

private val KNOWN_FIELDS = setOf(
    "tmdbId", "title", "poster", "backdrop", "episodeRuntime", "status", "seasons",
    "structure", "removed", "aired", "numberingMode", "episodeModelV", "log",
    "seasonPlays", "caughtUpAt", "completedAt", "metaCheckedAt", "dropped",
    "droppedAt", "updatedAt", "serverUpdatedAt", "lastWatched", "legacy", "legacyBackfillAt",
)

internal fun DocumentSnapshot.toProgress(): ShowProgress? {
    if (!exists()) return null
    val data = data ?: return null
    val id = (data["tmdbId"] as? Number)?.toInt()
        ?: id.substringAfterLast('_').toIntOrNull()
        ?: return null

    fun intMap(field: String): Map<Int, Int> =
        (data[field] as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) ->
            val season = key.toString().toIntOrNull() ?: return@mapNotNull null
            val count = (value as? Number)?.toInt() ?: return@mapNotNull null
            if (season > 0 && count > 0) season to count else null
        }.toMap()

    fun listMap(field: String): Map<Int, List<Int>> =
        (data[field] as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) ->
            val season = key.toString().toIntOrNull() ?: return@mapNotNull null
            val episodes = (value as? List<*>).orEmpty()
                .mapNotNull { (it as? Number)?.toInt() }
                .filter { it > 0 }.distinct().sorted()
            if (season > 0 && episodes.isNotEmpty()) season to episodes else null
        }.toMap()

    val aired = data["aired"] as? Map<*, *>
    fun num(field: String): Long = (data[field] as? Number)?.toLong() ?: 0L

    return ShowProgress(
        tmdbId = id,
        title = data["title"] as? String ?: "",
        poster = data["poster"] as? String ?: "",
        backdrop = data["backdrop"] as? String ?: "",
        episodeRuntime = (data["episodeRuntime"] as? Number)?.toInt() ?: 0,
        status = data["status"] as? String ?: "",
        seasons = listMap("seasons"),
        structure = intMap("structure"),
        removed = listMap("removed"),
        airedSeason = (aired?.get("season") as? Number)?.toInt() ?: 0,
        airedEpisode = (aired?.get("episode") as? Number)?.toInt() ?: 0,
        numberingMode = (data["numberingMode"] as? String)?.takeIf { it == "absolute" } ?: "season",
        log = ShowProgress.cleanLog(
            (data["log"] as? List<*>).orEmpty().mapNotNull { ShowProgress.decodeLogRow(it) }
        ),
        seasonPlays = intMap("seasonPlays").filterValues { it > 1 },
        caughtUpAt = num("caughtUpAt"),
        completedAt = num("completedAt"),
        metaCheckedAt = num("metaCheckedAt"),
        dropped = data["dropped"] as? Boolean ?: false,
        droppedAt = num("droppedAt"),
        updatedAt = num("updatedAt"),
        episodeModelV = (data["episodeModelV"] as? Number)?.toInt() ?: 1,
        extras = data.filterKeys { it !in KNOWN_FIELDS },
    )
}
