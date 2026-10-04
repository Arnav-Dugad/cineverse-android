package com.cineverse.app.data.ai

import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Gemini on a person or a studio, for this viewer: a take, and where to start. */
@Immutable
data class Spot(val take: String, val start: List<MediaItem>)

/**
 * The spotlight on person and studio pages: two or three sentences about
 * them written for this viewer - what of theirs you have loved, what their
 * work has in common, what you would make of it - and three titles of
 * theirs you have not seen to start with, chosen from their own list (so a
 * pick is always really theirs). Remembered for the session.
 */
class Spotlight(private val app: AppContainer) {

    private val cache = android.util.LruCache<String, Spot>(40)

    suspend fun person(id: Int, name: String, knownFor: String, work: List<MediaItem>): Spot? =
        spot("person:$id", "the ${knownFor.lowercase().ifBlank { "film-maker" }} $name", work)

    suspend fun studio(id: Int, name: String, network: Boolean, work: List<MediaItem>): Spot? =
        spot("studio:$id", "the ${if (network) "TV network" else "studio"} $name", work)

    private suspend fun spot(key: String, who: String, work: List<MediaItem>): Spot? {
        cache.get(key)?.let { return it }
        if (work.size < 3) return null
        val library = app.privacy.library()
        val shows = app.privacy.shows()
        fun seen(item: MediaItem) = library.isWatched(item.key) || (item.type == MediaType.Tv && (shows[item.id]?.watchedCount ?: 0) > 0)
        val list = work.filterNot { it.adult || app.privacy.isAdult(it.key) }.distinctBy { it.key }.sortedByDescending { it.voteCount }.take(45)
        val viewer = runCatching { app.persona.brief() }.getOrDefault("")
        val prompt = buildString {
            if (viewer.isNotBlank()) { appendLine(viewer); appendLine() }
            appendLine("About $who, for this viewer. Their work, most known first:")
            list.forEach { item ->
                val rated = library.ratings[item.key]
                append("- ${item.title} (${item.year})")
                when {
                    rated != null -> append(" [viewer rated $rated/10]")
                    seen(item) -> append(" [viewer has seen]")
                }
                appendLine()
            }
            appendLine()
            appendLine("Reply with JSON only: {\"take\": string, \"start\": [string, string, string]}")
            appendLine("take: 2 or 3 sentences, under 60 words, to the viewer as \"you\": what defines this work and how it sits")
            appendLine("with their taste - mention what of it they have seen or loved, honestly including a mismatch. No greeting, never their name.")
            appendLine("start: exactly three titles copied exactly from the list above that the viewer has NOT seen, best first for them.")
        }
        val raw = app.gemini.json(prompt, timeoutMs = 18_000) ?: return null
        val spot = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val take = obj["take"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim().orEmpty()
            val names = (obj["start"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.lowercase() }
            val start = names.mapNotNull { wanted ->
                list.firstOrNull { it.title.lowercase() == wanted && !seen(it) }
                    ?: list.firstOrNull { it.title.lowercase().startsWith(wanted.take(12)) && !seen(it) }
            }.distinctBy { it.key }
            if (take.length < 20) return null
            Spot(take, start)
        }.getOrNull() ?: return null
        cache.put(key, spot)
        return spot
    }
}
