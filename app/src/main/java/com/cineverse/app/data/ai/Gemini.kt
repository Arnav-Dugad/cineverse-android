package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.data.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flowOn

/**
 * Gemini, through Firebase AI Logic in the same Firebase project as the rest
 * of the app.
 *
 * Every caller has a fallback, because Gemini may not be there: the project
 * has to have Firebase AI Logic switched on, the phone has to be online, and
 * a model can be retired. So this answers null rather than throwing, remembers
 * a refusal for half an hour instead of asking again on every keystroke, and
 * tries newer models first, settling on whichever one answers.
 */
class Gemini(
    private val context: Context,
    /** The Settings switch: false means no request is ever made. */
    private val enabled: () -> Boolean = { true },
) {

    private val lock = Mutex()
    private var model: String? = null
    private var unavailableUntil = 0L

    private val working = java.util.concurrent.atomic.AtomicInteger(0)
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True while any request is in flight: what the edge glow listens to. */
    val busy: kotlinx.coroutines.flow.StateFlow<Boolean> = _busy

    private fun begin() { _busy.value = working.incrementAndGet() > 0 }
    private fun end() { _busy.value = working.decrementAndGet() > 0 }

    /** True once Gemini has answered at least once this session. */
    @Volatile var confirmed: Boolean = false
        private set

    /** What the last attempt said, for the status row in Settings. */
    @Volatile var lastError: String = ""
        private set

    /**
     * Asked from Settings: forget any earlier refusal, try once, and say in
     * plain words whether Gemini is there - and if not, why.
     */
    suspend fun check(): Pair<Boolean, String> {
        unavailableUntil = 0L
        lock.withLock { model = null }
        val answer = text("Reply with the single word: ready", timeoutMs = 20_000)
        if (answer != null) return true to "Connected · ${lock.withLock { model }.orEmpty()}"
        val reason = lastError
        return false to when {
            reason.contains("Developer API is not enabled", true) || reason.contains("genai config not found", true) ->
                "Not switched on yet: Firebase console → AI Logic → Settings → Gemini Developer API → Enable"
            reason.contains("has not been used", true) || reason.contains("SERVICE_DISABLED", true) ->
                "Firebase AI Logic is switched off for this project"
            reason.contains("quota", true) || reason.contains("429") || reason.contains("RESOURCE_EXHAUSTED", true) ->
                "Today's free Gemini allowance is used up; it resets daily"
            reason.contains("resolve host", true) || reason.contains("failed to connect", true) -> "No connection"
            reason.isBlank() -> "No answer from Gemini"
            else -> reason.take(160)
        }
    }

    /** Plain text, or null when Gemini cannot be reached. */
    suspend fun text(prompt: String, timeoutMs: Long = 20_000): String? = call(prompt, json = false, timeoutMs)

    /** A JSON object as text, or null. */
    suspend fun json(prompt: String, timeoutMs: Long = 12_000): String? = call(prompt, json = true, timeoutMs)

    /**
     * A written answer as it is written: each emission is the whole answer so
     * far. Nothing is emitted when Gemini is off or unreachable, so a caller
     * that collects nothing knows to fall back. A model that fails before its
     * first words hands over to the next; one that fails part way stops there,
     * since words already on screen should not be replaced.
     */
    fun stream(prompt: String, timeoutMs: Long = 45_000): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        if (!enabled() || System.currentTimeMillis() < unavailableUntil) return@flow
        begin()
        try {
        for (name in SMART) {
            val sofar = StringBuilder()
            val outcome = runCatching {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    model(name, json = false).generateContentStream(prompt).collect { chunk ->
                        val piece = chunk.text ?: return@collect
                        sofar.append(piece)
                        emit(plain(sofar.toString()))
                    }
                }
            }
            if (outcome.isSuccess && sofar.isNotEmpty()) {
                lock.withLock { model = name }
                confirmed = true
                return@flow
            }
            if (sofar.isNotEmpty()) return@flow
            val error = outcome.exceptionOrNull()
            if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            val message = error?.let { it.message ?: it.javaClass.simpleName }.orEmpty()
            lastError = message
            android.util.Log.w("CineVerseGemini", "$name stream failed: $message", error)
            if (Off.any { message.contains(it, ignoreCase = true) }) {
                unavailableUntil = System.currentTimeMillis() + (if (message.contains("config not found", true)) 3 else 30) * 60_000L
                return@flow
            }
        }
        } finally {
            end()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * A picture and a question about it - a screenshot, a poster photo - as
     * JSON. Flash-Lite first, which reads images and answers in seconds.
     */
    suspend fun vision(image: android.graphics.Bitmap, prompt: String, timeoutMs: Long = 25_000): String? {
        if (!enabled() || System.currentTimeMillis() < unavailableUntil) return null
        begin()
        try {
            for (name in FAST) {
                val text = withTimeoutOrNull(timeoutMs) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            model(name, json = true).generateContent(
                                com.google.firebase.ai.type.content {
                                    image(image)
                                    text(prompt)
                                }
                            ).text
                        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
                    }
                }
                if (!text.isNullOrBlank()) {
                    confirmed = true
                    return text.trim()
                }
            }
            return null
        } finally {
            end()
        }
    }

    private fun model(name: String, json: Boolean) =
        FirebaseAI.getInstance(Firebase.init(context), GenerativeBackend.googleAI()).generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                temperature = if (json) 0.2f else 0.7f
                if (json) responseMimeType = "application/json"
            },
        )

    private suspend fun call(prompt: String, json: Boolean, timeoutMs: Long): String? {
        if (!enabled() || System.currentTimeMillis() < unavailableUntil) return callInner(prompt, json, timeoutMs)
        begin()
        return try { callInner(prompt, json, timeoutMs) } finally { end() }
    }

    private suspend fun callInner(prompt: String, json: Boolean, timeoutMs: Long): String? {
        if (!enabled()) return null
        if (System.currentTimeMillis() < unavailableUntil) {
            android.util.Log.i("CineVerseGemini", "skipped: unavailable for ${(unavailableUntil - System.currentTimeMillis()) / 1000}s more")
            return null
        }
        // Quick, structured jobs - reading a request, picking a film, a match
        // score - go to Flash-Lite, which answers in two or three seconds;
        // written answers go to Flash, which thinks first and writes better.
        // Each list is tried in order, and a model that fails or is too slow
        // hands over to the other.
        val candidates = if (json) FAST else SMART
        var failures = 0
        for (name in candidates) {
            val outcome = withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.IO) {
                    runCatching { model(name, json).generateContent(prompt).text }
                }
            } ?: run {
                android.util.Log.w("CineVerseGemini", "$name timed out after ${timeoutMs}ms")
                lastError = "Gemini took too long to answer"
                failures++
                null
            } ?: continue
            outcome.onSuccess { text ->
                lock.withLock { model = name }
                confirmed = true
                return text?.trim()?.let { if (json) it else plain(it) }?.takeIf { it.isNotEmpty() }
            }
            val message = outcome.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }.orEmpty()
            lastError = message
            android.util.Log.w("CineVerseGemini", "$name failed: $message", outcome.exceptionOrNull())
            // Gemini is not there at all - switched off for the project, a bad
            // key, no network: the same for every model, so stop and stay quiet
            // for a while. Anything else - a retired model, one the free tier
            // does not include, its quota spent - is that model's problem, and
            // the next one may well answer.
            if (Off.any { message.contains(it, ignoreCase = true) }) {
                // A setup that has only just been fixed reaches some servers
                // before others: "config not found" waits minutes, not half an hour.
                val pause = if (message.contains("config not found", true)) 3 else 30
                unavailableUntil = System.currentTimeMillis() + pause * 60_000L
                return null
            }
            failures++
        }
        // Every model was slow or refused, but Gemini is there: a short pause,
        // so a busy minute does not switch the features off for long.
        if (failures > 0) unavailableUntil = System.currentTimeMillis() + 2 * 60_000L
        return null
    }

    companion object {
        /** Newest first; the first one that answers is kept. */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
        private val SMART = MODELS
        private val FAST = MODELS.reversed()

        /** Errors that mean Gemini itself is unreachable, not just one model. */
        private val Off = listOf(
            "SERVICE_DISABLED", "has not been used", "is disabled", "is not enabled", "API key not valid", "genai config not found",
            "API_KEY_INVALID", "Unable to resolve host", "UnknownHost", "failed to connect",
        )

        /**
         * Gemini's prose with the markdown taken out - bold and italic marks,
         * headings, bullets made of asterisks - because the app sets its own type.
         */
        fun plain(text: String): String = text
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])"""), "$1")
            .replace(Regex("""(?m)^#{1,6}\s+"""), "")
            .replace(Regex("""(?m)^\s*\*\s+"""), "- ")
            .trim()

        /** The JSON object inside a reply, tolerating a fenced code block around it. */
        fun extractJson(text: String): String? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            return if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }
    }
}
