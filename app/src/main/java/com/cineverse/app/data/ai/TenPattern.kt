package com.cineverse.app.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.MediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What your favourites share: a headline, the pattern, and the threads through them. */
@Immutable
@Serializable
data class Pattern(val headline: String, val pattern: String, val threads: List<Thread>) {
    @Immutable
    @Serializable
    data class Thread(val name: String, val keys: List<String>)
}

/**
 * "Why did I like it?": Gemini reads every title you gave a 10 (topped up
 * with your 9s when there are few 10s) - genres, directors, cast, decades,
 * themes - and names what they have in common: a headline, a short
 * explanation, and three threads, each with the titles that carry it. Kept
 * until your top scores change.
 */
class TenPattern(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("ten_pattern", Context.MODE_PRIVATE)

    /** The titles it is about: 10s first, then 9s, at most thirty. */
    fun favourites(): List<Pair<String, Int>> {
        val ratings = app.library.library.value.ratings
        val tens = ratings.filterValues { it >= 10 }.keys.toList()
        val nines = ratings.filterValues { it == 9 }.keys.toList()
        return (tens.map { it to 10 } + (if (tens.size < 8) nines.map { it to 9 } else emptyList())).take(30)
    }

    private fun signature(list: List<Pair<String, Int>>) = list.sortedBy { it.first }.joinToString(",") { "${it.first}=${it.second}" }.hashCode().toString()

    fun cached(): Pattern? {
        val list = favourites()
        val raw = prefs.getString("pattern", null) ?: return null
        if (raw.substringBefore('\n') != signature(list)) return null
        return runCatching { Http.json.decodeFromString<Pattern>(raw.substringAfter('\n')) }.getOrNull()
    }

    suspend fun explain(): Pattern? {
        cached()?.let { return it }
        val list = favourites()
        if (list.size < 3) return null
        val library = app.library.library.value
        val lines = list.mapNotNull { (key, score) ->
            val watched = library.watched[key]
            val saved = library.saved[key]
            val title = watched?.title ?: saved?.title ?: return@mapNotNull null
            buildString {
                append("$key | $title")
                (watched?.year ?: saved?.year)?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                append(" | $score/10")
                val genres = (watched?.genres ?: saved?.genres).orEmpty().mapNotNull { GenreNames[it] }
                if (genres.isNotEmpty()) append(" | ${genres.joinToString()}")
                watched?.director?.takeIf { it.isNotBlank() }?.let { append(" | dir. $it") }
                watched?.cast?.take(3)?.takeIf { it.isNotEmpty() }?.let { append(" | ${it.joinToString { c -> c.name }}") }
            }
        }
        val prompt = buildString {
            appendLine("These are the titles one viewer rated highest (key | title | score | genres | director | cast):")
            lines.forEach { appendLine(it) }
            appendLine()
            appendLine("What do they have in common? Look past the obvious genre labels: tone, craft, themes, the kind of character,")
            appendLine("the feeling they leave. Be specific and perceptive, not flattering.")
            appendLine("Reply with JSON only: {\"headline\": string, \"pattern\": string, \"threads\": [{\"name\": string, \"keys\": [string]}]}")
            appendLine("headline: at most 8 words, to the viewer (\"You love outsiders who outwit the system\").")
            appendLine("pattern: 2 or 3 sentences, under 70 words, to the viewer as \"you\", naming a few of the titles.")
            appendLine("threads: exactly 3, each a 2-5 word name and the keys (copied exactly from the list) of 2-5 titles that share it.")
        }
        val raw = app.gemini.json(prompt, timeoutMs = 20_000) ?: return null
        val pattern = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val headline = obj["headline"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim()?.trim('"').orEmpty()
            val text = obj["pattern"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim().orEmpty()
            val known = list.map { it.first }.toSet()
            val threads = (obj["threads"] as? JsonArray).orEmpty().mapNotNull { element ->
                val thread = element.jsonObject
                val name = thread["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val keys = (thread["keys"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it in known }
                if (keys.isEmpty()) null else Pattern.Thread(name, keys)
            }.take(3)
            if (headline.isBlank() || text.isBlank()) return null
            Pattern(headline, text, threads)
        }.getOrNull() ?: return null
        prefs.edit().putString("pattern", signature(list) + "\n" + Http.json.encodeToString(pattern)).apply()
        return pattern
    }

    /** A favourite's poster, for the threads. */
    fun item(key: String): MediaItem? {
        val library = app.library.library.value
        return library.watched[key]?.asItem() ?: library.saved[key]?.asItem()
    }
}
