package com.cineverse.app.data.ai

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.TitleDetail
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Why a title might suit you, and how well. */
@Immutable
data class Pitch(
    /** 0..100. */
    val fit: Int,
    val why: String,
)

/**
 * "Why you'll like it": Gemini reads what you have loved and what you have
 * not, and says in two sentences how this title sits with that - honestly,
 * including when it probably is not for you.
 *
 * Only for a title you have not seen, only with enough of a history to say
 * something real, and remembered for the session so a title is asked about
 * once.
 */
class ForYou(private val gemini: Gemini) {

    private val cache = LruCache<String, Pitch>(80)

    suspend fun pitch(detail: TitleDetail, library: Library): Pitch? {
        val key = "${detail.key}:${library.ratings.size}:${library.watched.size}"
        cache.get(key)?.let { return it }
        val taste = taste(library, exclude = detail.key) ?: return null
        val prompt = """
            You are a friend who knows films and TV well, talking to someone about a title they are looking at.
            What they have loved: ${taste.loved.joinToString("; ")}.
            ${if (taste.disliked.isNotEmpty()) "What they did not enjoy: ${taste.disliked.joinToString("; ")}." else ""}
            The title: ${detail.title} (${detail.releaseDate.take(4)}), ${if (detail.type.wire == "tv") "a series" else "a film"}.
            Genres: ${detail.genres.joinToString { it.name }}.
            ${detail.tagline.takeIf { it.isNotBlank() }?.let { "Tagline: $it" } ?: ""}
            Synopsis: ${detail.overview.take(700)}

            Reply with JSON only: {"fit": integer 0-100 for how well it suits their taste, "why": string}.
            "why" is two short sentences, under 42 words in all, speaking to them as "you". Name one or two of THEIR titles
            to explain the connection. If it is a poor fit, say honestly what might not work for them. No spoilers beyond
            the synopsis, no plot summary, no preamble, no quotation marks around titles.
        """.trimIndent()
        val raw = gemini.json(prompt) ?: return null
        val pitch = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val why = obj["why"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val fit = obj["fit"]?.jsonPrimitive?.intOrNull ?: return null
            if (why.length < 12) return null
            Pitch(fit.coerceIn(0, 100), why)
        }.getOrNull() ?: return null
        cache.put(key, pitch)
        return pitch
    }

    private class Taste(val loved: List<String>, val disliked: List<String>)

    /** At least three titles with an opinion attached, or nothing to go on. */
    private fun taste(library: Library, exclude: String): Taste? {
        fun titleOf(key: String) = library.watched[key]?.title ?: library.saved[key]?.title
        val rated = library.ratings.filterKeys { it != exclude }
        val loved = rated.filterValues { it >= 8 }.entries.sortedByDescending { it.value }
            .mapNotNull { (key, score) -> titleOf(key)?.let { "$it ($score/10)" } }
            .take(14)
            .ifEmpty {
                // No ratings: what they watched most recently stands in.
                library.watched.values.filter { it.key != exclude }.sortedByDescending { it.lastPlay }
                    .map { it.title }.take(14)
            }
        val disliked = rated.filterValues { it in 1..4 }.entries.sortedBy { it.value }
            .mapNotNull { (key, score) -> titleOf(key)?.let { "$it ($score/10)" } }
            .take(6)
        return if (loved.size >= 3) Taste(loved, disliked) else null
    }
}
