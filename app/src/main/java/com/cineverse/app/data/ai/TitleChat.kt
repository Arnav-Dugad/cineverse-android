package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.TitleDetail

/** One turn of a conversation about a title. */
@Immutable
data class ChatTurn(
    val question: String,
    /** Null while it is being answered. */
    val answer: String? = null,
    /** True when Gemini wrote it, false for an answer read off the title's own facts. */
    val byGemini: Boolean = false,
)

/** Where a viewer is in a title, which decides what may be said about it. */
@Immutable
data class SpoilerLine(
    /** "You're on S2 E4", "You've seen it", "You haven't started". */
    val label: String,
    /** The instruction Gemini is given. */
    val rule: String,
)

/**
 * "Ask about this title": Gemini, told exactly how far you are, so it can
 * talk about what you have seen and will not say a word about what you have
 * not. A film you have watched is open season; a series is open up to the
 * last episode you ticked and closed after it; anything unseen gets the
 * premise and nothing more.
 *
 * With Gemini off, the questions a title page can answer by itself - who
 * made it, who is in it, how long, where to watch - are answered from TMDB,
 * and anything else says plainly that it needs Gemini.
 */
class TitleChat(private val gemini: Gemini) {

    suspend fun answer(
        detail: TitleDetail,
        line: SpoilerLine,
        history: List<ChatTurn>,
        question: String,
        /** "Explain the ending": a fuller answer, only ever asked by someone who has seen it. */
        ending: Boolean = false,
    ): ChatTurn {
        val prompt = buildString {
            appendLine("You are CineVerse's film and TV expert, answering a fan's question about one title.")
            if (ending) {
                appendLine("The fan has watched all of it and asked you to explain the ending.")
                appendLine("In at most 190 words and three short paragraphs of plain prose: what actually happens at the end,")
                appendLine("what it means for the main characters and the story's themes, and any open question or popular reading.")
                appendLine("Be concrete and accurate; if you are not sure of a detail, say so rather than inventing it. No headings.")
            } else {
                appendLine("Answer in at most 90 words, warmly and specifically, in plain prose with no markdown headings.")
            }
            appendLine("SPOILER RULE (absolute): ${line.rule}")
            appendLine()
            appendLine("Title: ${detail.title} (${detail.releaseDate.take(4)}), ${if (detail.isSeries) "series" else "film"}")
            if (detail.genres.isNotEmpty()) appendLine("Genres: ${detail.genres.joinToString { it.name }}")
            if (detail.overview.isNotBlank()) appendLine("Premise: ${detail.overview}")
            detail.director?.let { appendLine("Director: ${it.name}") }
            if (detail.cast.isNotEmpty()) appendLine("Cast: ${detail.cast.take(8).joinToString { p -> listOfNotNull(p.name, p.character?.takeIf { c -> c.isNotBlank() }?.let { c -> "as $c" }).joinToString(" ") }}")
            if (detail.runtime > 0) appendLine("Runtime: ${detail.runtime} minutes")
            if (detail.isSeries) appendLine("Seasons: ${detail.numberOfSeasons}, episodes: ${detail.numberOfEpisodes}")
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("Conversation so far:")
                history.takeLast(6).forEach { turn ->
                    appendLine("Fan: ${turn.question}")
                    turn.answer?.let { appendLine("You: $it") }
                }
            }
            appendLine()
            appendLine("Fan: $question")
            appendLine("You:")
        }
        gemini.text(prompt, timeoutMs = if (ending) 30_000 else 20_000)?.let { return ChatTurn(question, it.trim(), byGemini = true) }
        if (ending) {
            // Without Gemini a series still has its finale's own synopsis; a
            // film's ending is written nowhere a title page can read.
            val finale = detail.lastEpisode?.takeIf { detail.isSeries && it.overview.isNotBlank() }
            return ChatTurn(
                question,
                finale?.let { "Gemini is off, so here is the finale as the episode guide tells it - S${it.season} E${it.number}, \"${it.name}\": ${it.overview}" }
                    ?: "Explaining an ending needs Gemini, which isn't working for CineVerse yet. Settings → Gemini status says why.",
            )
        }
        return ChatTurn(question, facts(detail, question) ?: OFF, byGemini = false)
    }

    companion object {
        fun spoilerLine(detail: TitleDetail, progress: ShowProgress?, watched: Boolean): SpoilerLine {
            if (!detail.isSeries) {
                return if (watched) SpoilerLine("You've seen it, so anything goes", "The user HAS seen this film. Spoilers about it are fine.")
                else SpoilerLine("Spoiler-free: you haven't seen it", "The user has NOT seen this film. Never reveal plot points beyond its premise, twists, deaths or the ending.")
            }
            val last = progress?.let { lastWatched(it) }
            return when {
                progress != null && progress.complete ->
                    SpoilerLine("You've finished it, so anything goes", "The user has finished this series. Spoilers about it are fine.")
                last != null -> SpoilerLine(
                    "Spoiler-safe up to S${last.first} E${last.second}",
                    "The user has watched this series up to and including season ${last.first} episode ${last.second}. " +
                        "You may discuss anything up to that point. NEVER reveal, hint at or confirm anything that happens after " +
                        "season ${last.first} episode ${last.second} - not deaths, twists, who survives, later seasons, or how it ends. " +
                        "If asked about later events, say you will not spoil it.",
                )
                else -> SpoilerLine("Spoiler-free: you haven't started", "The user has NOT started this series. Never reveal anything beyond its premise.")
            }
        }

        /** The last episode ticked, in airing order. */
        private fun lastWatched(progress: ShowProgress): Pair<Int, Int>? {
            val season = progress.seasons.filterValues { it.isNotEmpty() }.keys.filter { it > 0 }.maxOrNull() ?: return null
            val episode = progress.seasons[season]?.maxOrNull() ?: return null
            return season to episode
        }

        /** The questions a title page can answer by itself. */
        fun facts(detail: TitleDetail, question: String): String? {
            val q = question.lowercase()
            fun has(vararg words: String) = words.any { q.contains(it) }
            return when {
                has("direct", "who made", "creator", "created", "showrunner") -> {
                    val people = detail.crew.filter { it.job == "Director" || it.job == "Creator" }.map { it.name }.distinct()
                    if (people.isEmpty()) null else "${detail.title} is ${if (detail.isSeries) "created" else "directed"} by ${people.take(3).joinToString(", ")}."
                }
                has("cast", "star", "actor", "who plays", "who is in", "who's in") ->
                    detail.cast.take(6).joinToString("; ") { p -> p.name + (p.character?.takeIf { it.isNotBlank() }?.let { " as $it" } ?: "") }
                        .takeIf { it.isNotBlank() }?.let { "The leads: $it." }
                has("how long", "runtime", "length", "minutes", "hours") -> when {
                    detail.isSeries && detail.episodeRuntime > 0 ->
                        "Episodes run about ${detail.episodeRuntime} minutes, across ${detail.numberOfEpisodes} episodes in ${detail.numberOfSeasons} season${if (detail.numberOfSeasons == 1) "" else "s"}."
                    detail.runtime > 0 -> "It runs ${detail.runtime / 60}h ${detail.runtime % 60}m."
                    else -> null
                }
                has("where", "stream", "watch it", "netflix", "prime", "available") ->
                    detail.providers.map { it.name }.distinct().takeIf { it.isNotEmpty() }
                        ?.let { "In your region it's on ${it.take(5).joinToString(", ")}." }
                        ?: "No streaming service lists it in your region right now."
                has("season", "episodes", "how many") && detail.isSeries ->
                    "${detail.numberOfSeasons} season${if (detail.numberOfSeasons == 1) "" else "s"}, ${detail.numberOfEpisodes} episodes so far."
                has("next episode", "when does", "new episode", "come out", "release", "air") -> when {
                    detail.nextEpisode != null -> "The next episode, S${detail.nextEpisode.season} E${detail.nextEpisode.number}, airs ${detail.nextEpisode.airDate}."
                    detail.releaseDate.isNotBlank() -> "It came out on ${detail.releaseDate}."
                    else -> null
                }
                has("rating", "score", "good", "worth", "reviews") ->
                    if (detail.voteAverage > 0) "TMDB members give it ${"%.1f".format(detail.voteAverage)} out of 10, from ${"%,d".format(detail.voteCount)} votes." else null
                has("genre", "kind of", "type of") ->
                    detail.genres.takeIf { it.isNotEmpty() }?.let { "It's ${it.joinToString(", ") { g -> g.name.lowercase() }}." }
                has("similar", "like this", "recommend") ->
                    detail.recommendations.take(5).map { it.title }.takeIf { it.isNotEmpty() }?.let { "If you like it, try ${it.joinToString(", ")}." }
                else -> null
            }
        }

        const val OFF = "That one needs Gemini, which isn't switched on for CineVerse yet. I can tell you who made it, who's in it, how long it is, where to watch it and what's similar."

        /** Questions worth offering before anyone types, by where the viewer is. */
        fun suggestions(detail: TitleDetail, line: SpoilerLine): List<String> = buildList {
            if (detail.isSeries) {
                if (line.label.startsWith("Spoiler-safe")) {
                    add("Remind me what's happened so far")
                    add("Who's who again?")
                }
                add("Is it worth continuing?")
                add("Where can I watch it?")
            } else {
                add("What's it really about?")
                add("Who directed it?")
                add("How long is it?")
                add(if (line.label.startsWith("You've seen")) "Explain the ending" else "Is it worth watching?")
            }
            add("What's similar?")
        }
    }
}
