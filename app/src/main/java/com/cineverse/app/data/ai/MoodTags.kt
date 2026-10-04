package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.GenreNames
import com.cineverse.app.data.model.SavedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Moods for your watchlist, from Gemini: each saved title gets one to three
 * of a fixed set - cosy, tense, epic, funny, dark, heartfelt, mind-bending,
 * light - so My List can be filtered by how you feel rather than by genre.
 * Titles are tagged thirty at a time and the tags kept on the device; a title
 * is only ever asked about once. Hidden titles (adult, or in a locked list)
 * are never sent.
 */
class MoodTags(private val app: AppContainer) {

    enum class Mood(val label: String, val emoji: String) {
        Cosy("Cosy", "☕"), Tense("Tense", "⚡"), Epic("Epic", "⛰️"), Funny("Funny", "😂"),
        Dark("Dark", "🌑"), Heartfelt("Heartfelt", "💛"), Mind("Mind-bending", "🌀"), Light("Light", "☀️"),
    }

    private val prefs = app.context.getSharedPreferences("mood_tags", Context.MODE_PRIVATE)
    private val _tags = MutableStateFlow(load())

    /** Title key to its moods. */
    val tags: StateFlow<Map<String, Set<Mood>>> = _tags

    private fun load(): Map<String, Set<Mood>> = prefs.all.mapNotNull { (key, value) ->
        val moods = (value as? String)?.split(',')?.mapNotNull { name -> Mood.entries.firstOrNull { it.name == name } }?.toSet()
        moods?.let { key to it }
    }.toMap()

    @Volatile private var running = false

    /** Tag whatever on the watchlist has no moods yet. */
    suspend fun tagMissing(items: Collection<SavedItem>) {
        if (running || !app.settings.settings.value.geminiOn) return
        running = true
        try {
            val todo = items.filter { it.key !in _tags.value && !app.privacy.hidden(it.key) }.sortedByDescending { it.addedAt }.take(60)
            for (batch in todo.chunked(30)) {
                val lines = batch.joinToString("\n") { item ->
                    val genres = item.genres.mapNotNull { GenreNames[it] }.joinToString()
                    "${item.key} | ${item.title} (${item.year}) | $genres"
                }
                val prompt = buildString {
                    appendLine("Tag each title with 1 to 3 moods from exactly this set: cosy, tense, epic, funny, dark, heartfelt, mind-bending, light.")
                    appendLine("Base it on what the title is actually like to watch, not just its genre labels.")
                    appendLine(lines)
                    appendLine("Reply with JSON only: {\"tags\": [{\"key\": string, \"moods\": [string]}]} with every key copied exactly.")
                }
                val raw = app.gemini.json(prompt, timeoutMs = 25_000) ?: return
                val parsed = runCatching {
                    val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw)!!).jsonObject
                    (obj["tags"] as? JsonArray).orEmpty().mapNotNull { element ->
                        val row = element.jsonObject
                        val key = row["key"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                        val moods = (row["moods"] as? JsonArray).orEmpty().mapNotNull { m ->
                            when (m.jsonPrimitive.contentOrNull?.lowercase()?.trim()) {
                                "cosy", "cozy" -> Mood.Cosy
                                "tense" -> Mood.Tense
                                "epic" -> Mood.Epic
                                "funny" -> Mood.Funny
                                "dark" -> Mood.Dark
                                "heartfelt" -> Mood.Heartfelt
                                "mind-bending", "mind bending" -> Mood.Mind
                                "light" -> Mood.Light
                                else -> null
                            }
                        }.toSet()
                        if (moods.isEmpty() || batch.none { it.key == key }) null else key to moods
                    }.toMap()
                }.getOrNull() ?: continue
                // A title Gemini answered for but could not place is kept with
                // no moods, so it is not asked about again on every refresh.
                val found = batch.associate { it.key to (parsed[it.key] ?: emptySet()) }
                val editor = prefs.edit()
                found.forEach { (key, moods) -> editor.putString(key, moods.joinToString(",") { it.name }) }
                editor.apply()
                _tags.value = _tags.value + found
            }
        } finally {
            running = false
        }
    }
}
