package com.cineverse.app.data.firebase

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.MovieProgress
import com.cineverse.app.data.model.SavedItem
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.model.UserList
import com.cineverse.app.data.model.WatchedItem
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/**
 * Everything the user owns: the list, what they have watched, what they rated,
 * their custom lists, and where they got to in a film.
 *
 * Reads are LIVE. Every collection is a Firestore snapshot listener rather than
 * a fetch, so a tick on the laptop lands on the phone in the same second without
 * the app asking. Writes are optimistic and offline-first: Firestore applies
 * them locally, the listener fires immediately with the new value, and the
 * server copy catches up whenever there is a network. Nothing in the UI ever
 * waits on a round trip.
 */
@Immutable
data class Library(
    val saved: Map<String, SavedItem> = emptyMap(),
    val watched: Map<String, WatchedItem> = emptyMap(),
    val ratings: Map<String, Int> = emptyMap(),
    val lists: List<UserList> = emptyList(),
    val movieProgress: Map<Int, MovieProgress> = emptyMap(),
    val loaded: Boolean = false,
) {
    fun isSaved(key: String) = saved.containsKey(key)
    fun isWatched(key: String) = watched.containsKey(key)
    fun ratingOf(key: String) = ratings[key] ?: 0
}

class LibraryRepository(
    private val store: FirebaseFirestore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private fun user(uid: String) = store.collection("users").document(uid)

    @OptIn(ExperimentalCoroutinesApi::class)
    val library: StateFlow<Library> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(Library(loaded = true))
            else combine(
                user(uid).collection("watchlist").snapshots(),
                user(uid).collection("watched").snapshots(),
                user(uid).collection("ratings").snapshots(),
                user(uid).collection("lists").snapshots(),
                user(uid).collection("movieProgress").snapshots(),
            ) { saved, watched, ratings, lists, progress ->
                Library(
                    saved = saved.mapNotNull { it.toSaved() }.associateBy { it.key },
                    watched = watched.mapNotNull { it.toWatched() }.associateBy { it.key },
                    ratings = ratings.mapNotNull { doc ->
                        // The website's field is `score`. `value` is read as a
                        // fallback only because an early build of this app wrote
                        // that name; nothing writes it any more.
                        val score = (doc.get("score") as? Number)?.toInt()
                            ?: (doc.get("value") as? Number)?.toInt()
                            ?: return@mapNotNull null
                        doc.id to score
                    }.toMap(),
                    lists = lists.map { it.toList() }.sortedBy { it.createdAt },
                    // Tombstones are dropped here rather than at every call
                    // site, so nothing downstream can forget to check.
                    movieProgress = progress.mapNotNull { it.toMovieProgress() }
                        .filterNot { it.deleted }
                        .associateBy { it.tmdbId },
                    loaded = true,
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Library())

    // ---------- writes ----------

    /**
     * Save or unsave. The document id is `"${type}_${id}"`, the same key the
     * website uses, so the two never create a duplicate of the same title.
     */
    suspend fun toggleSaved(item: MediaItem, detail: TitleDetail? = null): Boolean {
        val uid = auth.uid.value ?: return false
        val ref = user(uid).collection("watchlist").document(item.key)
        return if (library.value.isSaved(item.key)) {
            ref.delete().await(); false
        } else {
            ref.set(savedPayload(item, detail)).await(); true
        }
    }

    private fun savedPayload(item: MediaItem, detail: TitleDetail?) = mapOf(
        "tmdbId" to item.id,
        "type" to item.type.wire,
        "title" to item.title,
        "poster" to item.posterPath.orEmpty(),
        "rating" to item.voteAverage,
        "year" to item.year,
        "genres" to (detail?.genres?.map { it.id } ?: item.genreIds),
        "keywords" to (detail?.keywords?.map { it.id } ?: emptyList()),
        "runtime" to (detail?.runtime ?: 0),
        "language" to item.originalLanguage,
        "country" to (detail?.countries?.firstOrNull().orEmpty()),
        "releaseDate" to item.releaseDate,
        "voteCount" to item.voteCount,
        "lists" to listOf("watchlist"),
        "added" to FieldValue.serverTimestamp(),
    )

    /**
     * Mark watched, or unmark. Enriched from the title page when the caller has
     * it, so the Watched grid and every statistic can be drawn without a second
     * TMDB request — the same reason the website enriches on write.
     */
    suspend fun toggleWatched(item: MediaItem, detail: TitleDetail? = null): Boolean {
        val uid = auth.uid.value ?: return false
        val ref = user(uid).collection("watched").document(item.key)
        return if (library.value.isWatched(item.key)) {
            ref.delete().await(); false
        } else {
            ref.set(watchedPayload(item, detail)).await(); true
        }
    }

    private fun watchedPayload(item: MediaItem, detail: TitleDetail?): Map<String, Any?> {
        val saved = library.value.saved[item.key]
        return mapOf(
            "tmdbId" to item.id,
            "type" to item.type.wire,
            "title" to item.title,
            "poster" to item.posterPath.orEmpty().ifBlank { saved?.poster.orEmpty() },
            "year" to item.year.ifBlank { saved?.year.orEmpty() },
            "genres" to (detail?.genres?.map { it.id } ?: saved?.genres ?: item.genreIds),
            "keywords" to (detail?.keywords?.map { it.id } ?: saved?.keywords ?: emptyList()),
            "runtime" to (detail?.runtime ?: saved?.runtime ?: 0),
            "episodeRuntime" to (detail?.episodeRuntime ?: 0),
            "episodeCount" to (detail?.numberOfEpisodes ?: 0),
            "language" to item.originalLanguage.ifBlank { saved?.language.orEmpty() },
            "country" to (detail?.countries?.firstOrNull() ?: saved?.country).orEmpty(),
            "releaseDate" to item.releaseDate.ifBlank { saved?.releaseDate.orEmpty() },
            "tmdbRating" to item.voteAverage,
            "voteCount" to item.voteCount,
            "director" to (detail?.director?.name.orEmpty()),
            "directorId" to (detail?.director?.id ?: 0),
            "collectionId" to (detail?.collectionId ?: 0),
            "collectionName" to (detail?.collectionName.orEmpty()),
            "collectionPoster" to (detail?.collectionPoster.orEmpty()),
            "watchedAt" to FieldValue.serverTimestamp(),
        )
    }

    /**
     * 1–10, or 0 to clear.
     *
     * The document is `{ score, tmdbId, type, title, updated }` — the website's
     * exact shape, down to the field NAME. An earlier build of this app wrote
     * `value` instead, which meant a score given on the phone was invisible on
     * the laptop and the other way round: two rating systems over one library.
     */
    suspend fun setRating(key: String, value: Int, title: String = "") {
        val uid = auth.uid.value ?: return
        val ref = user(uid).collection("ratings").document(key)
        if (value <= 0) { ref.delete().await(); return }
        val type = key.substringBefore('_')
        val id = key.substringAfterLast('_').toIntOrNull() ?: 0
        ref.set(
            mapOf(
                "score" to value.coerceIn(1, 10),
                "tmdbId" to id,
                "type" to type,
                "title" to title.ifBlank {
                    library.value.saved[key]?.title ?: library.value.watched[key]?.title.orEmpty()
                },
                "updated" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ---------- custom lists ----------

    suspend fun createList(name: String, icon: String = ""): String? {
        val uid = auth.uid.value ?: return null
        val id = "l" + System.currentTimeMillis().toString(36)
        user(uid).collection("lists").document(id).set(
            mapOf("name" to name.take(60), "icon" to icon, "createdAt" to System.currentTimeMillis())
        ).await()
        return id
    }

    suspend fun renameList(id: String, name: String) {
        val uid = auth.uid.value ?: return
        user(uid).collection("lists").document(id)
            .set(mapOf("name" to name.take(60)), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun deleteList(id: String) {
        val uid = auth.uid.value ?: return
        // A list going away must not take its titles with it: every saved item
        // that named it loses the name and falls back to the watchlist, which is
        // what the website does and what a viewer expects.
        val affected = library.value.saved.values.filter { it.lists.contains(id) }
        val batch = store.batch()
        batch.delete(user(uid).collection("lists").document(id))
        for (item in affected) {
            val remaining = (item.lists - id).ifEmpty { listOf("watchlist") }
            batch.update(user(uid).collection("watchlist").document(item.key), "lists", remaining)
        }
        batch.commit().await()
    }

    /**
     * Write or clear a list's PIN lock, in the website's exact shape, so a PIN
     * set on either opens the list on both.
     */
    suspend fun saveListLock(id: String, salt: String?, hash: String?): Boolean {
        val uid = auth.uid.value ?: return false
        val value: Any = if (salt != null && hash != null) mapOf(
            "v" to 1,
            "algo" to "PBKDF2-SHA256",
            "iterations" to com.cineverse.app.data.lock.ListLocks.ITERATIONS,
            "salt" to salt,
            "hash" to hash,
            "updatedAt" to System.currentTimeMillis(),
        ) else FieldValue.delete()
        return runCatching {
            user(uid).collection("lists").document(id)
                .set(mapOf("lock" to value), com.google.firebase.firestore.SetOptions.merge()).await()
        }.isSuccess
    }

    suspend fun setLists(key: String, lists: List<String>) {
        val uid = auth.uid.value ?: return
        val clean = lists.distinct().ifEmpty { listOf("watchlist") }
        user(uid).collection("watchlist").document(key)
            .set(mapOf("lists" to clean), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    // ---------- films, mid-play ----------

    /**
     * Where you got to in a film, in MINUTES, written as the website stores it.
     *
     * The document id is `movie_{id}`, the figures are in seconds, and
     * `deleted` is cleared explicitly — a film you removed and then started
     * again has a tombstone sitting on it, and merging a position into that
     * without clearing the flag writes a row that neither client will show.
     */
    suspend fun setMovieProgress(id: Int, minutes: Int, runtimeMinutes: Int, detail: TitleDetail? = null) {
        val uid = auth.uid.value ?: return
        val ref = user(uid).collection("movieProgress").document("movie_$id")
        val now = System.currentTimeMillis()
        val held = library.value.movieProgress[id]
        ref.set(
            mapOf(
                "tmdbId" to id,
                "position" to minutes.coerceAtLeast(0) * 60,
                "runtime" to runtimeMinutes.coerceAtLeast(0) * 60,
                "title" to (detail?.title ?: held?.title.orEmpty()),
                "poster" to (detail?.posterPath ?: held?.poster.orEmpty()),
                "backdrop" to (detail?.backdropPath ?: held?.backdrop.orEmpty()),
                "startedAt" to (held?.startedAt?.takeIf { it > 0 } ?: now),
                "updatedAt" to now,
                "deleted" to false,
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    /**
     * Take a film out of Continue Watching.
     *
     * A TOMBSTONE, not a delete, because that is what the website writes and
     * what its reconciliation expects: deleting the document outright lets an
     * older offline copy on another device resurrect the row on next sign-in.
     */
    /**
     * Another viewing of something already watched.
     *
     * Exactly the website's write: `plays`, `playDates` capped at the newest
     * forty, and `lastPlayedAt`. There is no such thing as a rewatch of
     * something unwatched, so this refuses rather than inventing a first play —
     * the caller should mark it watched instead, which is a different action
     * with different consequences for episodes and ratings.
     */
    suspend fun logRewatch(key: String, at: Long = System.currentTimeMillis()): Int {
        val uid = auth.uid.value ?: return 0
        val held = library.value.watched[key] ?: return 0
        val plays = held.plays.coerceAtLeast(1) + 1
        val dates = (held.playDates + at).sorted().takeLast(40)
        user(uid).collection("watched").document(key).set(
            mapOf(
                "plays" to plays,
                "playDates" to dates,
                "lastPlayedAt" to dates.last(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        return plays
    }

    suspend fun clearMovieProgress(id: Int) {
        val uid = auth.uid.value ?: return
        user(uid).collection("movieProgress").document("movie_$id").set(
            mapOf(
                "tmdbId" to id,
                "deleted" to true,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }
}

// ---------- reading documents ----------

private fun DocumentSnapshot.num(field: String): Double =
    (get(field) as? Number)?.toDouble() ?: 0.0

private fun DocumentSnapshot.int(field: String): Int = num(field).toInt()

private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()

private fun DocumentSnapshot.ints(field: String): List<Int> =
    (get(field) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()

private fun DocumentSnapshot.strings(field: String): List<String> =
    (get(field) as? List<*>)?.mapNotNull { it as? String }.orEmpty()

/**
 * A timestamp written with `serverTimestamp()` is null in the local echo of the
 * write until the server answers. Falling back to "now" rather than epoch 0 is
 * the difference between a title you just marked sorting first and sorting as
 * though you watched it in 1970 — a bug the website had and fixed.
 */
private fun DocumentSnapshot.millis(field: String): Long = when (val raw = get(field)) {
    is com.google.firebase.Timestamp -> raw.toDate().time
    is Number -> raw.toLong()
    else -> if (metadata.hasPendingWrites()) System.currentTimeMillis() else 0L
}

internal fun DocumentSnapshot.toSaved(): SavedItem? {
    val id = int("tmdbId").takeIf { it > 0 } ?: this.id.substringAfterLast('_').toIntOrNull() ?: return null
    return SavedItem(
        tmdbId = id,
        type = MediaType.of(str("type").ifBlank { this.id.substringBefore('_') }),
        title = str("title"),
        poster = str("poster"),
        rating = num("rating"),
        year = str("year"),
        genres = ints("genres"),
        keywords = ints("keywords"),
        runtime = int("runtime"),
        language = str("language"),
        country = str("country"),
        releaseDate = str("releaseDate"),
        voteCount = int("voteCount"),
        lists = strings("lists").ifEmpty { listOf("watchlist") },
        addedAt = millis("added"),
    )
}

internal fun DocumentSnapshot.toWatched(): WatchedItem? {
    val id = int("tmdbId").takeIf { it > 0 } ?: this.id.substringAfterLast('_').toIntOrNull() ?: return null
    return WatchedItem(
        tmdbId = id,
        type = MediaType.of(str("type").ifBlank { this.id.substringBefore('_') }),
        title = str("title"),
        poster = str("poster"),
        year = str("year"),
        genres = ints("genres"),
        keywords = ints("keywords"),
        runtime = int("runtime"),
        episodeRuntime = int("episodeRuntime"),
        episodeCount = int("episodeCount"),
        language = str("language"),
        country = str("country"),
        releaseDate = str("releaseDate"),
        tmdbRating = num("tmdbRating"),
        voteCount = int("voteCount"),
        director = str("director"),
        directorId = int("directorId"),
        collectionId = int("collectionId"),
        collectionName = str("collectionName"),
        collectionPoster = str("collectionPoster"),
        watchedAt = millis("watchedAt"),
        // A document with no `plays` predates the field and means ONE viewing.
        // Reading it as zero would make every title in a long-standing library
        // look unwatched to the rewatch panel.
        plays = int("plays").coerceAtLeast(1),
        playDates = (get("playDates") as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Number)?.toLong() }
            .filter { it > 0 }
            .sorted(),
        lastPlayedAt = millis("lastPlayedAt"),
    )
}

internal fun DocumentSnapshot.toList(): UserList = UserList(
    id = id,
    name = str("name").ifBlank { "Untitled list" },
    icon = str("icon"),
    createdAt = millis("createdAt"),
    locked = getBoolean("locked") == true,
    // The lock is an OBJECT on the website's documents. This used to read a
    // boolean that nothing writes, so a list locked on the laptop showed every
    // title on the phone.
    lockSalt = ((get("lock") as? Map<*, *>)?.get("salt") as? String).orEmpty(),
    lockHash = ((get("lock") as? Map<*, *>)?.get("hash") as? String).orEmpty(),
)

internal fun DocumentSnapshot.toMovieProgress(): MovieProgress? {
    // The document id is `movie_640`, so the id has to come from the field and
    // the suffix is only a fallback.
    val tmdbId = int("tmdbId").takeIf { it > 0 }
        ?: id.substringAfterLast('_').toIntOrNull()
        ?: return null
    return MovieProgress(
        tmdbId = tmdbId,
        seconds = int("position"),
        runtimeSeconds = int("runtime"),
        // The row carries its own artwork and name, so Continue Watching can
        // draw a film that is not in the watchlist and never was.
        title = str("title"),
        poster = str("poster"),
        backdrop = str("backdrop"),
        startedAt = millis("startedAt"),
        updatedAt = millis("updatedAt"),
        deleted = getBoolean("deleted") == true,
    )
}

/**
 * A collection as a flow of its documents. `MetadataChanges.INCLUDE` matters:
 * without it an optimistic local write does not emit until the server confirms,
 * which is exactly the lag the app is built to avoid.
 */
internal fun CollectionReference.snapshots(): Flow<List<DocumentSnapshot>> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            // A permission error means signed out mid-flight; an empty library is
            // the honest answer, and the auth flow above will swap this listener.
            trySend(emptyList())
            return@addSnapshotListener
        }
        trySend(snapshot?.documents.orEmpty())
    }
    awaitClose { registration.remove() }
}
