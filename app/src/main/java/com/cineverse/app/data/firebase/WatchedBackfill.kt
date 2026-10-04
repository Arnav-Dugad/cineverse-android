package com.cineverse.app.data.firebase

import android.content.Context
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Filling in what older watched titles never recorded, as the website's
 * watched-meta.js does: runtime, director, the first-billed cast, genres and
 * themes for films; episode length and cast for series. Everything that
 * reads your history - hours watched, favourite actors and directors, the
 * persona Gemini is given - is only as good as these fields, and titles
 * marked years ago, or from a list import, often have none of them.
 *
 * Quietly, in the background, a few titles at a time, well after start-up:
 * only empty fields are written (a merge, never an overwrite), and a title
 * that could not be filled is not asked about again for two weeks.
 */
class WatchedBackfill(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("watched_backfill", Context.MODE_PRIVATE)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        app.scope.launch {
            runCatching {
                val library = app.library.library.first { it.loaded }
                if (app.auth.uid.value == null) return@runCatching
                delay(25_000)
                run(library)
            }
        }
    }

    private suspend fun run(library: com.cineverse.app.data.firebase.Library) {
        val now = System.currentTimeMillis()
        val region = app.settings.settings.value.region
        val wanting = library.watched.values.filter { item ->
            val incomplete = if (item.type == MediaType.Movie) {
                item.runtime <= 0 || item.director.isBlank() || item.cast.isEmpty() || item.genres.isEmpty() || item.keywords.isEmpty()
            } else {
                item.episodeRuntime <= 0 || item.cast.isEmpty() || item.genres.isEmpty()
            }
            incomplete && now - prefs.getLong(item.key, 0L) > 14 * 86_400_000L
        }.sortedByDescending { it.lastPlay }.take(60)

        val gate = Semaphore(2)
        coroutineScope {
            wanting.map { item ->
                async {
                    gate.withPermit {
                        val detail = runCatching { app.tmdb.detail(item.tmdbId, item.type, region) }.getOrNull()
                        if (detail != null) {
                            val fields = buildMap<String, Any?> {
                                if (item.runtime <= 0 && detail.runtime > 0) put("runtime", detail.runtime)
                                if (item.episodeRuntime <= 0 && detail.episodeRuntime > 0) put("episodeRuntime", detail.episodeRuntime)
                                if (item.genres.isEmpty() && detail.genres.isNotEmpty()) put("genres", detail.genres.map { it.id })
                                if (item.keywords.isEmpty() && detail.keywords.isNotEmpty()) put("keywords", detail.keywords.take(20).map { it.id })
                                detail.director?.let { director ->
                                    if (item.director.isBlank()) {
                                        put("director", director.name)
                                        put("directorId", director.id)
                                        put("directorProfile", director.profilePath.orEmpty())
                                    }
                                }
                                if (item.cast.isEmpty() && detail.cast.isNotEmpty()) {
                                    put("cast", detail.cast.take(8).map { person ->
                                        mapOf("id" to person.id, "name" to person.name, "profile" to person.profilePath.orEmpty())
                                    })
                                }
                                if (item.episodeCount <= 0 && detail.numberOfEpisodes > 0) put("episodeCount", detail.numberOfEpisodes)
                            }
                            if (fields.isNotEmpty()) runCatching { app.library.fillWatched(item.key, fields) }
                            // A show in progress learns its episode length too,
                            // so its hours stop being counted at 42 minutes.
                            if (detail.isSeries && detail.episodeRuntime > 0) {
                                val show = app.episodes.progress.value[detail.id]
                                if (show != null && show.episodeRuntime <= 0) runCatching { app.episodes.refreshMeta(detail) }
                            }
                        }
                        prefs.edit().putLong(item.key, System.currentTimeMillis()).apply()
                        delay(400)
                    }
                }
            }.awaitAll()
        }
    }
}
