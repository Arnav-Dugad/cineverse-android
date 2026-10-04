package com.cineverse.app.data.ai

import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.ShowProgress

/**
 * One line for a new-episode alert, from Gemini: a hook written from the
 * last episode you actually watched - "After that cliffhanger at Lumon, Mark
 * finally gets answers" - rather than the bare "S2 E5 is out". Only from what
 * you have seen, and nothing from the new episode past its name.
 */
class EpisodeHook(private val app: AppContainer) {

    suspend fun line(show: ShowProgress, new: Episode): String? {
        if (app.privacy.hidden(show.key)) return null
        // The episode you saw last, by the log; failing that, the one before the new one.
        val last = show.log.filter { !it.bulk }.maxByOrNull { it.stamp }
            ?: show.log.maxByOrNull { it.stamp }
        val (season, number) = when {
            last != null -> last.season to last.episode
            new.number > 1 -> new.season to new.number - 1
            else -> return null
        }
        val previous = runCatching { app.tmdb.season(show.tmdbId, season) }.getOrNull()
            ?.firstOrNull { it.number == number }
            ?.takeIf { it.overview.isNotBlank() }
            ?: return null
        val prompt = """
            Write the body of a phone notification telling a fan that a new episode of "${show.title}" is out.
            One sentence, at most 16 words, that hooks them by recalling where the story was left at the end of the
            episode they watched last (below). Speak to them as "you" or about the characters. Spoil nothing from the new
            episode beyond its title. No quotation marks, no emoji, no hashtags.

            Their last episode, S${previous.season} E${previous.number} "${previous.name}": ${previous.overview.take(600)}
            The new episode: S${new.season} E${new.number}${new.name.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""}
        """.trimIndent()
        return app.gemini.text(prompt, timeoutMs = 15_000)
            ?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?.trim()?.trim('"', '“', '”')
            ?.takeIf { it.length in 12..160 }
    }
}
