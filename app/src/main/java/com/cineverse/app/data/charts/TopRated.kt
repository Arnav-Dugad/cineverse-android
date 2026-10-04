package com.cineverse.app.data.charts

import android.content.Context
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.tmdb.TmdbRepository

/**
 * A Top 250 rank for the title page. IMDb's own Top 250 is not published
 * anywhere an app can read it (Wikidata does not record it), so this is
 * TMDB's equivalent - its top-rated films and series, the first 250 of each -
 * labelled as TMDB's. Fetched at most once a week.
 */
class TopRated(context: Context, private val tmdb: TmdbRepository) {

    private val prefs = context.getSharedPreferences("top250", Context.MODE_PRIVATE)

    /** 1..250, or null when it is not in the list. */
    suspend fun rank(id: Int, type: MediaType): Int? {
        val ids = ids(type)
        return ids.indexOf(id).takeIf { it >= 0 }?.plus(1)
    }

    private suspend fun ids(type: MediaType): List<Int> {
        val key = type.wire
        val at = prefs.getLong("$key:at", 0L)
        val held = prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (held.size >= 200 && System.currentTimeMillis() - at < 7 * 86_400_000L) return held
        val fresh = runCatching {
            (1..13).flatMap { page ->
                if (type == MediaType.Movie) tmdb.movies("top_rated", page) else tmdb.series("top_rated", page)
            }.map { it.id }.distinct().take(250)
        }.getOrDefault(emptyList())
        if (fresh.size >= 200) {
            prefs.edit().putString(key, fresh.joinToString(",")).putLong("$key:at", System.currentTimeMillis()).apply()
            return fresh
        }
        return held
    }
}
