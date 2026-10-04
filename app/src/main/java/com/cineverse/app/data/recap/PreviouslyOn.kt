package com.cineverse.app.data.recap

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.data.model.Episode
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A "previously on", and who wrote it. */
@Immutable
data class Previously(
    val lines: List<String>,
    /** True when Gemini Nano summarised it on the phone; false for the episode-guide fallback. */
    val onDevice: Boolean,
    /** True when Gemini in the cloud wrote it, where the phone has no Nano. */
    val cloud: Boolean = false,
)

/**
 * "Previously on…" before the next episode, summarised on the phone by Gemini
 * Nano where the phone has it.
 *
 * Spoiler-safe by construction: the only text it ever reads is the synopses of
 * episodes you have already ticked - the last three before the one you are
 * about to watch - so it cannot tell you anything you have not seen.
 *
 * Where Nano is not available (most phones today) it falls back to the opening
 * sentence of each of those synopses, and says so. Where Nano can be
 * downloaded, the download is started quietly for next time and this time gets
 * the fallback; nobody waits on a model download for a recap.
 */
class PreviouslyOn(private val context: Context, private val gemini: com.cineverse.app.data.ai.Gemini) {

    /** Episodes are given oldest first. */
    suspend fun summarize(episodes: List<Episode>): Previously? {
        val usable = episodes.filter { it.overview.isNotBlank() }
        if (usable.isEmpty()) return null
        val nano = runCatching { nano(usable) }.getOrNull()
        return nano ?: cloud(usable) ?: Previously(usable.map { "${it.label} · ${firstSentence(it.overview)}" }, onDevice = false)
    }

    private suspend fun nano(episodes: List<Episode>): Previously? = withContext(Dispatchers.IO) {
        val options = SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
        val summarizer = Summarization.getClient(options)
        try {
            when (summarizer.checkFeatureStatus().get()) {
                FeatureStatus.AVAILABLE -> {
                    val text = episodes.joinToString("\n\n") { "${it.label}, \"${it.name}\": ${it.overview}" }
                    val summary = summarizer.runInference(SummarizationRequest.builder(text).build()).get().summary
                    val lines = summary.lines()
                        .map { it.trim().trimStart('*', '-', '•', ' ') }
                        .filter { it.isNotBlank() }
                    lines.takeIf { it.isNotEmpty() }?.let { Previously(it, onDevice = true) }
                }
                FeatureStatus.DOWNLOADABLE -> {
                    // For next time. Fire and forget; the callback only logs.
                    summarizer.downloadFeature(object : DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) = Unit
                        override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
                        override fun onDownloadCompleted() = Unit
                        override fun onDownloadFailed(e: GenAiException) = Unit
                    })
                    null
                }
                else -> null
            }
        } finally {
            runCatching { summarizer.close() }
        }
    }

    /**
     * Gemini in the cloud, where the phone has no Nano. Given the same three
     * synopses and nothing else, and told not to guess beyond them, so it is
     * as spoiler-safe as the on-device summary.
     */
    private suspend fun cloud(episodes: List<Episode>): Previously? {
        val prompt = buildString {
            appendLine("Write a \"Previously on\" recap of these TV episodes for someone about to watch the next one.")
            appendLine("Exactly three short bullet lines, one per line, each under 22 words, starting with \"- \".")
            appendLine("Present tense, names of characters where given, the key turns only.")
            appendLine("Use ONLY what is written below. Never guess at, hint at or mention anything that happens later.")
            appendLine()
            episodes.forEach { appendLine("${it.label}, \"${it.name}\": ${it.overview}") }
        }
        val text = gemini.text(prompt) ?: return null
        val lines = text.lines()
            .map { it.trim().trimStart('*', '-', '•', ' ').trim() }
            .filter { it.length > 3 }
            .take(3)
        return lines.takeIf { it.size >= 2 }?.let { Previously(it, onDevice = false, cloud = true) }
    }

    /**
     * "Catch me up": a whole season you have finished, before the next one.
     * Gemini, where it is on, reads every episode's synopsis of that season and
     * nothing else - so it cannot say anything about the season to come. With
     * Gemini off it is the episode guide, one opening line per episode.
     */
    suspend fun season(showTitle: String, season: Int, episodes: List<Episode>): Previously? {
        val usable = episodes.filter { it.overview.isNotBlank() }.sortedBy { it.number }
        if (usable.isEmpty()) return null
        val prompt = buildString {
            appendLine("Recap season $season of \"$showTitle\" for someone about to start season ${season + 1}.")
            appendLine("Five or six short bullet lines, one per line, each starting with \"- \", each under 26 words.")
            appendLine("Cover the main arcs and where every major character ends the season, in order. Present tense.")
            appendLine("Use ONLY the synopses below. Never mention, guess at or hint at anything from later seasons.")
            appendLine()
            usable.forEach { appendLine("Episode ${it.number}, \"${it.name}\": ${it.overview}") }
        }
        gemini.text(prompt, timeoutMs = 25_000)?.let { text ->
            val lines = text.lines().map { it.trim().trimStart('*', '-', '•', ' ').trim() }.filter { it.length > 3 }.take(7)
            if (lines.size >= 3) return Previously(lines, onDevice = false, cloud = true)
        }
        return Previously(usable.map { "E${it.number} · ${firstSentence(it.overview)}" }, onDevice = false)
    }

    companion object {
        /** The first sentence, which in an episode synopsis is the setup and never the twist. */
        fun firstSentence(text: String): String {
            val trimmed = text.trim()
            // A full stop after "Sr", "Dr", "Mr" or an initial is not the end
            // of a sentence: "When George Sr. learns..." was cut at "Sr."
            val end = Regex("""[.!?](\s|$)""").findAll(trimmed).map { it.range.first }.firstOrNull { at ->
                val word = trimmed.substring(0, at).substringAfterLast(' ')
                trimmed[at] != '.' || (word.lowercase() !in Abbreviations && !(word.length == 1 && word[0].isUpperCase()))
            }
            return if (end != null && end < 220) trimmed.substring(0, end + 1) else trimmed.take(180).trimEnd() + "…"
        }

        private val Abbreviations = setOf("mr", "mrs", "ms", "dr", "sr", "jr", "st", "vs", "prof", "lt", "col", "gen", "sgt", "capt", "no", "mt")
    }
}
