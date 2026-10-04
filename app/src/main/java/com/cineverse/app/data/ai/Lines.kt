package com.cineverse.app.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.TitleDetail

/**
 * Short pieces of Gemini writing for title pages, each kept per title (and
 * never for a hidden one):
 *
 *  - a hook in place of a flat TMDB synopsis: two sentences that make you
 *    want to press play, spoiler-free;
 *  - what critics made of one season, in a single line.
 */
class Lines(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("gemini_lines", Context.MODE_PRIVATE)

    private suspend fun cachedOr(key: String, keep: Boolean, write: suspend () -> String?): String? {
        if (keep) prefs.getString(key, null)?.let { return it }
        val text = write()?.lineSequence()?.joinToString(" ")?.trim()?.trim('"', '“', '”')?.takeIf { it.length > 15 } ?: return null
        if (keep) prefs.edit().putString(key, text).apply()
        return text
    }

    /** Everything kept for a title, once it is locked away. */
    fun forget(key: String, id: Int) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it == "hook:$key" || (key.startsWith("tv_") && it.startsWith("critics:$id:")) }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** The synopsis, rewritten as a hook. */
    suspend fun hook(detail: TitleDetail): String? {
        if (detail.overview.length < 40) return null
        return cachedOr("hook:${detail.key}", !app.privacy.hidden(detail.key)) {
            app.gemini.text(
                """
                Rewrite this ${if (detail.isSeries) "series" else "film"} synopsis as a hook: two short sentences, under 45 words,
                vivid and specific, that make someone want to press play. Spoil nothing beyond what the synopsis already says.
                Plain prose, no quotation marks, no title in the first words.
                Title: ${detail.title} (${detail.year})
                Synopsis: ${detail.overview}
                """.trimIndent(),
                timeoutMs = 15_000,
            )
        }
    }

    /** One line on how critics received a season. */
    suspend fun critics(detail: TitleDetail, season: Int, spoilerSafe: Boolean): String? =
        cachedOr("critics:${detail.id}:$season:${if (spoilerSafe) "s" else "o"}", !app.privacy.hidden(detail.key)) {
            app.gemini.text(
                """
                In one sentence of at most 25 words, sum up how critics received season $season of "${detail.title}"
                (${detail.year}): the consensus, and what was praised or criticised. Lead with the verdict itself - do not
                open with "Critics" or the show's name. If you do not know how it was received,
                reply exactly: unknown.
                ${if (spoilerSafe) "The reader has not finished this season: no plot points, deaths, twists or the ending." else ""}
                """.trimIndent(),
                timeoutMs = 15_000,
            )?.takeUnless { it.trim().lowercase().startsWith("unknown") }
        }
}

/** A title left half-watched, and Gemini's nudge about it. */
@Immutable
data class Unfinished(val item: MediaItem, val where: String, val line: String, val series: Boolean)

/**
 * "Finish or drop?": a series you stopped ticking more than a month ago (or
 * a film you paused part way, a fortnight ago), with a line from Gemini on
 * whether it is worth going back - spoiler-free, honest, from your own
 * taste. One at a time, the most recently abandoned first; "Not now" puts
 * that title away for a month.
 */
class HalfWatched(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("half_watched", Context.MODE_PRIVATE)

    fun snooze(key: String) = prefs.edit().putLong(key, System.currentTimeMillis()).apply()

    private fun snoozed(key: String) = System.currentTimeMillis() - prefs.getLong(key, 0L) < 30 * 86_400_000L

    suspend fun find(): Unfinished? {
        val library = app.privacy.library()
        val shows = app.privacy.shows()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val show = shows.values
            .filter { it.watchedCount > 0 && !it.complete && !it.dropped && it.nextUp() != null }
            .map { it to (it.log.maxOfOrNull { row -> row.stamp } ?: it.updatedAt) }
            .filter { (s, at) -> at > 0 && now - at > 30 * day && now - at < 2 * 365 * day && !snoozed(s.key) }
            .maxByOrNull { it.second }
        val film = library.movieProgress.values
            .filter { it.minutes > 0 && !library.isWatched("movie_${it.tmdbId}") && !snoozed("movie_${it.tmdbId}") && !it.deleted }
            .filter { it.updatedAt in 1 until now - 14 * day }
            .maxByOrNull { it.updatedAt }
        val pick = when {
            show != null && (film == null || show.second >= film.updatedAt) -> {
                val (s, at) = show
                val next = s.nextUp()!!
                val where = "${if (s.isAbsolute) "Episode ${next.second}" else "S${next.first} E${next.second}"} next · stopped ${(now - at) / day / 7} weeks ago"
                Triple(MediaItem(s.tmdbId, MediaType.Tv, s.title, posterPath = s.poster.ifBlank { null }, backdropPath = s.backdrop.ifBlank { null }), where, true)
            }
            film != null -> {
                val detail = runCatching { app.tmdb.detail(film.tmdbId, MediaType.Movie, app.settings.settings.value.region) }.getOrNull() ?: return null
                val where = "${film.minutes} of ${detail.runtime.takeIf { it > 0 } ?: film.runtimeMinutes} minutes in"
                Triple(detail.asItem(), where, false)
            }
            else -> return null
        }
        val (item, where, series) = pick
        if (app.privacy.hidden(item.key)) return null
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        val line = app.gemini.text(
            buildString {
                if (viewer.isNotBlank()) { appendLine(viewer); appendLine() }
                appendLine("The viewer stopped part way through \"${item.title}\" ($where). In one or two sentences, under 35 words,")
                appendLine("tell them honestly whether it is worth finishing for someone with their taste - what gets better, or that")
                appendLine("it is fine to let it go. Speak to them as \"you\". No spoilers past where they stopped. No greeting.")
            },
            timeoutMs = 15_000,
        ) ?: return null
        return Unfinished(item, where, line.trim(), series)
    }

    /** "Drop": a series is marked dropped; a film's place is cleared. */
    suspend fun drop(unfinished: Unfinished) {
        val item = unfinished.item
        if (unfinished.series) {
            val detail = runCatching { app.tmdb.detail(item.id, MediaType.Tv, app.settings.settings.value.region) }.getOrNull() ?: return
            app.episodes.setDropped(detail, true)
        } else {
            app.library.clearMovieProgress(item.id)
        }
    }
}
