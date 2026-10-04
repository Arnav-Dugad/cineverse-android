package com.cineverse.app.agent

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.cineverse.app.CineVerseApp
import com.cineverse.app.data.ai.Outcome
import com.cineverse.app.data.ai.Understanding
import com.cineverse.app.widget.UpNextWidget
import com.cineverse.app.widget.updateAllSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What an action came to, for the assistant to say back. */
@AppFunctionSerializable
class ActionResult(
    /** Whether it was done. */
    val success: Boolean,
    /** One sentence to tell the user, e.g. "Marked Severance S2 E4 watched". */
    val message: String,
    /** The film or series it was done to, as CineVerse names it; empty when none was found. */
    val title: String,
)

/** A series the user is part-way through, and the episode that comes next. */
@AppFunctionSerializable
class NextEpisode(
    /** The series. */
    val show: String,
    /** The next unwatched episode that has aired, e.g. "S2 E4". */
    val episode: String,
    /** Episodes already out that the user has still to watch. */
    val episodesLeft: Int,
)

/** A film or series CineVerse suggests. */
@AppFunctionSerializable
class Suggestion(
    val title: String,
    /** "movie" or "tv". */
    val type: String,
    val year: String,
    /** TMDB's audience score, 0 to 10. */
    val rating: Double,
    val overview: String,
)

/**
 * CineVerse, for Gemini and other assistants on Android 16 and up: "mark the
 * next Severance episode watched", "add Dune to my CineVerse watchlist",
 * "what am I watching on CineVerse".
 *
 * Every function goes through the same [com.cineverse.app.data.ai.Assistant]
 * the in-app voice search uses, so a title is found the same way and a tick
 * lands in the same place - the user's account, shared with the website.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "CineVerseFunctionService",
    appFunctionXmlFileName = "cineverse_functions",
)
abstract class CineVerseFunctions : AppFunctionService() {

    private val container get() = (applicationContext as CineVerseApp).container

    /** "Gemini features" off in Settings covers the assistant reaching in, too. */
    private val allowed get() = container.settings.settings.value.geminiOn

    private suspend fun act(block: suspend () -> Outcome): ActionResult = withContext(Dispatchers.Default) {
        if (!allowed) return@withContext ActionResult(false, "Gemini features are turned off in CineVerse settings", "")
        container.assistant.ready()
        val outcome = runCatching { block() }.getOrElse { Outcome(false, "CineVerse couldn't do that just now") }
        if (outcome.ok) runCatching { UpNextWidget().updateAllSafe(applicationContext) }
        ActionResult(outcome.ok, outcome.message, outcome.item?.title.orEmpty())
    }

    /**
     * Marks the next unwatched episode of a TV series as watched in the user's CineVerse
     * account - the first episode, in order, that has aired and is not yet ticked. Use when the
     * user says they watched or finished the next or latest episode of a show.
     *
     * @param showName The name of the series, e.g. "Severance".
     * @return What was marked, or why nothing was.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun markNextEpisodeWatched(showName: String): ActionResult = act {
        container.assistant.nextEpisode(showName)
    }

    /**
     * Adds a film or series to the user's CineVerse watchlist.
     *
     * @param title The name of the film or series, e.g. "Dune: Part Two".
     * @return Whether it was added.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addToWatchlist(title: String): ActionResult = act {
        container.assistant.add(title)
    }

    /**
     * Removes a film or series from the user's CineVerse watchlist.
     *
     * @param title The name of the film or series.
     * @return Whether it was removed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun removeFromWatchlist(title: String): ActionResult = act {
        container.assistant.remove(title)
    }

    /**
     * Marks a whole film or series as watched in the user's CineVerse account. For a single
     * episode of a series, use markNextEpisodeWatched instead.
     *
     * @param title The name of the film or series.
     * @return Whether it was marked.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun markTitleWatched(title: String): ActionResult = act {
        container.assistant.watched(title)
    }

    /**
     * Gives a film or series the user's own rating in CineVerse.
     *
     * @param title The name of the film or series.
     * @param score The rating out of 10, a whole number from 1 to 10.
     * @return Whether the rating was saved.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun rateTitle(title: String, score: Int): ActionResult = act {
        container.assistant.rate(title, score)
    }

    /**
     * Lists the TV series the user is part-way through in CineVerse, most recently watched
     * first, each with the next episode that has aired. Use for "what am I watching" or
     * "what's next on my list".
     *
     * @return Up to ten series with their next episode.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getContinueWatching(): List<NextEpisode> = withContext(Dispatchers.Default) {
        if (!allowed) return@withContext emptyList()
        container.assistant.ready()
        container.episodes.progress.value.values
            .asSequence()
            .filter { !it.dropped && it.watchedCount > 0 }
            .mapNotNull { show -> show.nextUp()?.let { (s, e) -> show to "S$s E$e" } }
            .sortedByDescending { it.first.updatedAt }
            .take(10)
            .map { (show, label) -> NextEpisode(show.title, label, show.airedRemaining) }
            .toList()
    }

    /**
     * Finds films or series to watch from a description in plain words, e.g. "funny 90s films
     * with Tom Hanks", "Korean thrillers on Netflix" or "something like Interstellar".
     *
     * @param request What the user is in the mood for, in their own words.
     * @return Up to ten suggestions, best first.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun findSomethingToWatch(request: String): List<Suggestion> = withContext(Dispatchers.Default) {
        if (!allowed) return@withContext emptyList()
        val query = Understanding.discover(request)
        runCatching { container.assistant.discover(query) }.getOrDefault(emptyList())
            .take(10)
            .map { Suggestion(it.title, it.type.wire, it.year, it.voteAverage, it.overview.take(240)) }
    }
}
