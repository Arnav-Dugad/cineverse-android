package com.cineverse.app.data.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voice search, answering out loud: "Added Dune to your list", "Here are
 * twenty Korean thrillers on Netflix". Only ever after something was SPOKEN -
 * a typed search is answered on screen and in silence - and the phone's own
 * text-to-speech voice does the talking.
 *
 * The engine starts the first time it is needed, and a sentence asked for
 * before it is ready is said the moment it is.
 */
class Speaker(private val context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: String? = null

    private var after: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Say it, and call [onDone] on the main thread once it has been said (or could not be). */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        after = onDone
        val current = engine
        if (current == null) {
            waiting = text
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.language = Locale.getDefault()
                    engine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = finished()
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finished()
                        override fun onStop(utteranceId: String?, interrupted: Boolean) { if (interrupted) after = null }
                    })
                    waiting?.let { speak(it) }
                } else {
                    finished()
                }
                waiting = null
            }
            return
        }
        if (ready) speak(text) else waiting = text
    }

    private fun finished() {
        val done = after ?: return
        after = null
        main.post { done() }
    }

    private fun speak(text: String) {
        runCatching { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cineverse-answer") }
            .onFailure { finished() }
    }

    fun stop() {
        after = null
        runCatching { engine?.stop() }
    }
}
