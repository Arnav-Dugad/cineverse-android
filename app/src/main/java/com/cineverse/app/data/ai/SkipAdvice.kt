package com.cineverse.app.data.ai

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.AppContainer
import com.cineverse.app.core.net.Http
import com.cineverse.app.data.model.Episode
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Whether an episode can be skipped, and why - without saying what happens in it. */
@Immutable
data class Skip(val verdict: Verdict, val why: String) {
    enum class Verdict(val label: String) { Skip("Safe to skip"), Optional("Optional"), Watch("Don't skip") }
}

/**
 * "Should I skip this?": filler, recap and clip-show episodes - the long
 * anime runs are full of them - told apart from the ones the story needs.
 * Spoiler-free by rule: the answer talks about the episode's role (a filler
 * arc, a breather, a turning point), never its events. Kept per episode.
 */
class SkipAdvice(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("skip_advice", Context.MODE_PRIVATE)

    /** Every episode's advice for a show, once it is locked away. */
    fun forget(showId: Int) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$showId:") }.forEach { editor.remove(it) }
        editor.apply()
    }

    suspend fun of(showId: Int, showTitle: String, episode: Episode, keep: Boolean = true): Skip? {
        val key = "$showId:${episode.season}:${episode.number}"
        if (keep) prefs.getString(key, null)?.let { raw ->
            val verdict = runCatching { Skip.Verdict.valueOf(raw.substringBefore('|')) }.getOrNull()
            if (verdict != null) return Skip(verdict, raw.substringAfter('|'))
        }
        val prompt = buildString {
            appendLine("A viewer is deciding whether to watch season ${episode.season} episode ${episode.number}")
            if (episode.name.isNotBlank()) appendLine("(\"${episode.name}\")")
            appendLine("of \"$showTitle\". Is it filler, a recap or clip show, or a stand-alone diversion they could skip without")
            appendLine("missing the story - or does the main story, or a major character, need it?")
            if (episode.overview.isNotBlank()) appendLine("For your eyes only (do not repeat any of it): ${episode.overview.take(500)}")
            appendLine("ABSOLUTE RULE: no spoilers. Describe only the episode's role (filler arc, breather, character piece, part of the")
            appendLine("main plot), never what happens in it, who appears or how anything turns out.")
            appendLine("If you are not sure, say \"optional\" and say why briefly.")
            appendLine("Reply with JSON only: {\"verdict\": \"skip\" | \"optional\" | \"watch\", \"why\": string under 20 words}")
        }
        val raw = app.gemini.json(prompt) ?: return null
        val skip = runCatching {
            val obj = Http.json.parseToJsonElement(Gemini.extractJson(raw) ?: return null).jsonObject
            val verdict = when (obj["verdict"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "skip" -> Skip.Verdict.Skip
                "watch" -> Skip.Verdict.Watch
                else -> Skip.Verdict.Optional
            }
            val why = obj["why"]?.jsonPrimitive?.contentOrNull?.let(Gemini::plain)?.trim().orEmpty()
            Skip(verdict, why)
        }.getOrNull() ?: return null
        if (keep) prefs.edit().putString(key, "${skip.verdict.name}|${skip.why}").apply()
        return skip
    }
}
