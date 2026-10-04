package com.cineverse.app.data.ai

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.ShowProgress

/**
 * What Gemini is never told.
 *
 *  - Adult titles (TMDB's adult flag): never part of anything sent to
 *    Gemini, and no Gemini features on their pages at all.
 *  - Titles in a locked list (a PIN or a lock on any list they are in): left
 *    out of everything Gemini reads about you - your brief, your favourites,
 *    your diary, your watchlist. On such a title's own page you can still
 *    ask about it, have the ending explained or see its trivia, but nothing
 *    of that is kept: no saved conversation, no cached answers.
 *
 * Adult flags are only known once a title's details have been loaded, so
 * they are remembered here as they are seen.
 */
class GeminiPrivacy(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("gemini_privacy", Context.MODE_PRIVATE)
    @Volatile private var adult: Set<String> = prefs.getStringSet("adult", emptySet()).orEmpty().toSet()

    /** Called for every title whose details load: remembers the adult ones. */
    fun saw(key: String, isAdult: Boolean) {
        if (isAdult && key !in adult) {
            adult = adult + key
            prefs.edit().putStringSet("adult", adult).apply()
        }
    }

    fun isAdult(key: String): Boolean = key in adult

    /**
     * The library's titles, checked against TMDB's adult flag a batch at a
     * time: saved on the website, on another phone or before this version,
     * most were never opened here, so nothing had told the app. Each title
     * is checked once and remembered; after that this costs nothing.
     */
    suspend fun sweep(limit: Int = 120) {
        val library = app.library.library.value
        if (!library.loaded) return
        val done = prefs.getStringSet("checked", emptySet()).orEmpty().toMutableSet()
        val todo = (library.watched.keys + library.saved.keys + library.ratings.keys)
            .filter { it !in done && it !in adult }
            .take(limit)
        if (todo.isEmpty()) return
        val region = app.settings.settings.value.region
        for (group in todo.chunked(4)) {
            kotlinx.coroutines.coroutineScope {
                group.map { key ->
                    async {
                        val id = key.substringAfter('_').toIntOrNull() ?: return@async key
                        val type = com.cineverse.app.data.model.MediaType.of(key.substringBefore('_'))
                        // Loading the details is what records the flag (TmdbRepository.onDetail).
                        runCatching { app.tmdb.detail(id, type, region) }.getOrNull()?.let { key }
                    }
                }.awaitAll().filterNotNull().forEach { done += it }
            }
        }
        prefs.edit().putStringSet("checked", done).apply()
    }

    /** In any list that is locked. */
    fun isLocked(key: String, library: Library = app.library.library.value): Boolean {
        val locked = library.lists.filter { it.locked || it.hasPin }.map { it.id }.toSet()
        if (locked.isEmpty()) return false
        return library.saved[key]?.lists?.any { it in locked } == true
    }

    /** Never sent to Gemini as part of what it knows about you. */
    fun hidden(key: String, library: Library = app.library.library.value): Boolean = isAdult(key) || isLocked(key, library)

    /** The library with every hidden title (and the locked lists themselves) taken out. */
    fun library(library: Library = app.library.library.value): Library {
        val drop = (library.watched.keys + library.saved.keys + library.ratings.keys).filter { hidden(it, library) }.toSet()
        if (drop.isEmpty() && library.lists.none { it.locked || it.hasPin }) return library
        return library.copy(
            watched = library.watched - drop,
            saved = library.saved - drop,
            ratings = library.ratings - drop,
            lists = library.lists.filterNot { it.locked || it.hasPin },
        )
    }

    /** Shows in progress, minus the hidden ones. */
    fun shows(shows: Map<Int, ShowProgress> = app.episodes.progress.value, library: Library = app.library.library.value): Map<Int, ShowProgress> =
        shows.filterValues { !hidden(it.key, library) }
}
