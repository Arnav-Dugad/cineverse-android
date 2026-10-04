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
class Gemini(private val context: Context) {

    private val lock = Mutex()
    private var model: String? = null
    private var unavailableUntil = 0L

    /** True once Gemini has answered at least once this session. */
    @Volatile var confirmed: Boolean = false
        private set

    /** Plain text, or null when Gemini cannot be reached. */
    suspend fun text(prompt: String, timeoutMs: Long = 12_000): String? = call(prompt, json = false, timeoutMs)

    /** A JSON object as text, or null. */
    suspend fun json(prompt: String, timeoutMs: Long = 12_000): String? = call(prompt, json = true, timeoutMs)

    private suspend fun call(prompt: String, json: Boolean, timeoutMs: Long): String? {
        if (System.currentTimeMillis() < unavailableUntil) return null
        val candidates = lock.withLock { model?.let { listOf(it) } ?: MODELS }
        for (name in candidates) {
            val outcome = withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val ai = FirebaseAI.getInstance(Firebase.init(context), GenerativeBackend.googleAI())
                        val generative = ai.generativeModel(
                            modelName = name,
                            generationConfig = generationConfig {
                                temperature = if (json) 0.2f else 0.7f
                                if (json) responseMimeType = "application/json"
                            },
                        )
                        generative.generateContent(prompt).text
                    }
                }
            } ?: return null
            outcome.onSuccess { text ->
                lock.withLock { model = name }
                confirmed = true
                return text?.trim()?.takeIf { it.isNotEmpty() }
            }
            val message = outcome.exceptionOrNull()?.message.orEmpty()
            // Gemini is not there at all - switched off for the project, a bad
            // key, no network: the same for every model, so stop and stay quiet
            // for a while. Anything else - a retired model, one the free tier
            // does not include, its quota spent - is that model's problem, and
            // the next one may well answer.
            if (Off.any { message.contains(it, ignoreCase = true) }) {
                unavailableUntil = System.currentTimeMillis() + 30 * 60_000L
                return null
            }
            // The kept model failing - its quota for the day, say - sends the
            // next call back through the whole list.
            lock.withLock { if (model == name) model = null }
        }
        unavailableUntil = System.currentTimeMillis() + 10 * 60_000L
        return null
    }

    companion object {
        /** Newest first; the first one that answers is kept. */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")

        /** Errors that mean Gemini itself is unreachable, not just one model. */
        private val Off = listOf(
            "SERVICE_DISABLED", "has not been used", "is disabled", "API key not valid",
            "API_KEY_INVALID", "Unable to resolve host", "UnknownHost", "failed to connect",
        )

        /** The JSON object inside a reply, tolerating a fenced code block around it. */
        fun extractJson(text: String): String? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            return if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }
    }
}
