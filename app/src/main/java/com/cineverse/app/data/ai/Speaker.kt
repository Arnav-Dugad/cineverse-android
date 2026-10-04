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

    fun say(text: String) {
        if (text.isBlank()) return
        val current = engine
        if (current == null) {
            waiting = text
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.language = Locale.getDefault()
                    waiting?.let { speak(it) }
                }
                waiting = null
            }
            return
        }
        if (ready) speak(text) else waiting = text
    }

    private fun speak(text: String) {
        runCatching { engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cineverse-answer") }
    }

    fun stop() {
        runCatching { engine?.stop() }
    }
}
