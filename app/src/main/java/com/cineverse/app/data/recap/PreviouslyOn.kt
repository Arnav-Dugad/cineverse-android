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
class PreviouslyOn(private val context: Context) {

    /** Episodes are given oldest first. */
    suspend fun summarize(episodes: List<Episode>): Previously? {
        val usable = episodes.filter { it.overview.isNotBlank() }
        if (usable.isEmpty()) return null
        val nano = runCatching { nano(usable) }.getOrNull()
        return nano ?: Previously(usable.map { "${it.label} · ${firstSentence(it.overview)}" }, onDevice = false)
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

    companion object {
        /** The first sentence, which in an episode synopsis is the setup and never the twist. */
        fun firstSentence(text: String): String {
            val trimmed = text.trim()
            val end = Regex("""[.!?](\s|$)""").find(trimmed)?.range?.first
            return if (end != null && end < 220) trimmed.substring(0, end + 1) else trimmed.take(180).trimEnd() + "…"
        }
    }
}
