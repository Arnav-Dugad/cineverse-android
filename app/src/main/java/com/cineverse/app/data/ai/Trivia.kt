package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.TitleDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Three things you might not know about a title, from Gemini: production,
 * casting, the making of it - never the plot. Kept on the device once
 * written, so a title's trivia is asked for once and is the same next time.
 */
class Trivia(context: Context, private val gemini: Gemini) {

    private val prefs = context.getSharedPreferences("trivia", Context.MODE_PRIVATE)

    /** A title's facts, once it is locked away. */
    fun forget(key: String) = prefs.edit().remove(key).apply()

    fun cached(key: String): List<String>? = prefs.getString(key, null)
        ?.split('\n')?.filter { it.isNotBlank() }?.takeIf { it.size == 3 }

    suspend fun facts(detail: TitleDetail, keep: Boolean = true, spoilerSafe: Boolean = true): List<String>? {
        if (keep) cached(detail.key)?.let { return it }
        val prompt = """
            Give three true, surprising, spoiler-free pieces of trivia about ${if (detail.isSeries) "the series" else "the film"}
            "${detail.title}" (${detail.releaseDate.take(4)})${detail.director?.let { ", directed by ${it.name}" } ?: ""}.
            Behind the scenes: production, casting, filming, music, influence, records. Nothing that reveals the plot past its premise.
            Only facts you are confident are accurate - well documented, not rumours. Each one sentence, at most 28 words, plain text.
            ${if (spoilerSafe) "The viewer has NOT finished it: nothing about any character's fate, twists, later seasons or the ending, even indirectly." else ""}
            Reply with JSON only: {"facts": [string, string, string]}.
        """.trimIndent()
        val raw = gemini.json(prompt) ?: return null
        val facts = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            (obj["facts"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.let(Gemini::plain)?.trim() }
                ?.map { it.replace('\n', ' ') }
                ?.filter { it.length > 12 }
        }.getOrNull()?.take(3) ?: return null
        if (facts.size < 3) return null
        if (keep) prefs.edit().putString(detail.key, facts.joinToString("\n")).apply()
        return facts
    }
}
