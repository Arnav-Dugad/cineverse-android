package com.cineverse.app.feature.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.clickableNoRipple
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * The microphone, through the phone's own speech recogniser: words appear
 * as they are heard, the orb moves with the voice, and the sentence goes to
 * search when the speaker stops.
 */
@Stable
class VoiceInput(private val context: Context) {

    enum class Phase { Idle, Starting, Listening, Error }

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** What has been heard so far. */
    var heard by mutableStateOf("")
        private set

    /** 0..1, how loud the voice is right now. */
    var level by mutableFloatStateOf(0f)
        private set

    var error by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onFinal: (String) -> Unit) {
        cancel()
        heard = ""
        error = ""
        level = 0f
        phase = Phase.Starting
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (created == null) {
            fail("Voice input isn't available on this phone")
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = Phase.Listening }
            override fun onBeginningOfSpeech() { phase = Phase.Listening }
            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2 (silence) to 10 (talking close to the phone).
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { level = 0f }
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { if (it.isNotBlank()) heard = it }
            }
            override fun onResults(results: Bundle?) {
                val text = first(results)?.takeIf { it.isNotBlank() } ?: heard
                finish()
                if (text.isNotBlank()) {
                    heard = text
                    onFinal(text)
                } else {
                    fail("Didn't catch that. Tap to try again.")
                }
            }
            override fun onError(code: Int) {
                // A recogniser that gives up with words in hand still heard
                // something worth searching for.
                val partial = heard
                finish()
                if (partial.isNotBlank() && code != SpeechRecognizer.ERROR_CLIENT) {
                    onFinal(partial)
                    return
                }
                fail(
                    when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap to try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No connection for voice. Check your internet."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "CineVerse needs the microphone for this."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."
                        SpeechRecognizer.ERROR_CLIENT -> return
                        else -> "Voice input stopped. Tap to try again."
                    }
                )
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        runCatching { created.startListening(intent()) }.onFailure { fail("Voice input isn't available on this phone") }
    }

    /** Stop listening and use what was heard. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** Stop and throw it away. */
    fun cancel() {
        runCatching { recognizer?.cancel() }
        finish()
        phase = Phase.Idle
    }

    private fun finish() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        level = 0f
        if (phase != Phase.Error) phase = Phase.Idle
    }

    private fun fail(message: String) {
        error = message
        phase = Phase.Error
    }

    fun reset() {
        error = ""
        if (phase == Phase.Error) phase = Phase.Idle
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask for anything to watch")
            // A pause between "funny films from the nineties" and "with Tom
            // Hanks" is a breath, not the end of the sentence.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
    }
}

@Composable
fun rememberVoiceInput(): VoiceInput {
    val context = LocalContext.current
    val voice = remember { VoiceInput(context.applicationContext) }
    DisposableEffect(voice) { onDispose { voice.cancel() } }
    return voice
}

private val Examples = listOf(
    "“Funny 90s films with Tom Hanks”",
    "“Something like Interstellar but shorter”",
    "“Korean thrillers on Netflix”",
    "“Mark the next Severance episode watched”",
    "“Add Dune Part Two to my list”",
    "“Best animated films from the 2010s”",
    "“Play the trailer for Oppenheimer”",
    "“Rate The Bear nine”",
    "“Cosy mysteries under two hours”",
    "“Take me to my stats”",
)

/**
 * Listening, full screen: an orb that breathes on its own and swells with
 * the voice, the words as they are recognised, and a few things to try.
 * Tapping the orb ends the sentence; the cross throws it away.
 */
@Composable
fun VoiceOverlay(voice: VoiceInput, onRetry: () -> Unit, onClose: () -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val reduced = CvTheme.reducedMotion
    val listening = voice.phase == VoiceInput.Phase.Listening || voice.phase == VoiceInput.Phase.Starting
    val level by animateFloatAsState(voice.level, spring(dampingRatio = 0.55f, stiffness = 380f), label = "voiceLevel")
    val shown by animateFloatAsState(1f, tween(260), label = "voiceIn")

    LaunchedEffect(Unit) { haptics?.play(Haptic.Peek) }

    val loop = rememberInfiniteTransition(label = "voiceLoop")
    val breath by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "breath")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")

    var example by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(2800); example = (example + 1) % Examples.size }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown }
            .background(colors.ink.copy(alpha = 0.965f))
            .clickableNoRipple { }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Icon(
            Icons.Rounded.Close,
            "Close",
            tint = colors.text2,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(44.dp)
                .clip(CvShape.Circle)
                .clickableNoRipple { voice.cancel(); onClose() }
                .padding(10.dp),
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                when (voice.phase) {
                    VoiceInput.Phase.Error -> "Hmm"
                    VoiceInput.Phase.Starting -> "One moment"
                    else -> if (voice.heard.isBlank()) "Listening" else "Got it so far"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.text3,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(220.dp)
                    .clip(CvShape.Circle)
                    .clickableNoRipple {
                        if (listening) { haptics?.play(Haptic.Tap); voice.stop() }
                        else { voice.reset(); onRetry() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Orb(
                    level = if (listening) level else 0f,
                    breath = if (reduced) 0f else breath,
                    spin = if (reduced) 0f else spin,
                    active = listening,
                    error = voice.phase == VoiceInput.Phase.Error,
                )
                Icon(Icons.Rounded.Mic, if (listening) "Finish" else "Listen again", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(34.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.TopCenter) {
                when {
                    voice.phase == VoiceInput.Phase.Error -> Text(
                        voice.error,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text2,
                        textAlign = TextAlign.Center,
                    )
                    voice.heard.isNotBlank() -> Text(
                        voice.heard,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Try saying", style = MaterialTheme.typography.labelMedium, color = colors.text3)
                        Spacer(Modifier.height(8.dp))
                        AnimatedContent(
                            example,
                            transitionSpec = {
                                (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 3 }) togetherWith
                                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 3 })
                            },
                            label = "voiceExample",
                        ) { index ->
                            Text(
                                Examples[index],
                                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                                color = colors.text2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        Text(
            if (listening) "Tap the orb when you're done" else "Tap the orb to try again",
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
    }
}

/**
 * Three soft rings around a glowing core. At rest they breathe slowly; a
 * voice pushes them out, each a little later than the one inside it, so the
 * sound seems to ripple outwards.
 */
@Composable
private fun Orb(level: Float, breath: Float, spin: Float, active: Boolean, error: Boolean) {
    val warm = if (error) Palette.Gold2 else Palette.Red2
    val cool = if (error) Palette.Gold else Palette.Purple
    val core by animateFloatAsState(if (active) 1f else 0.82f, spring(stiffness = 300f), label = "orbCore")
    Canvas(Modifier.size(220.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension * 0.22f
        for (ring in 3 downTo 1) {
            val phase = ((breath + ring * 0.18f) % 1f)
            val wave = (sin(phase * 2 * PI).toFloat() + 1f) / 2f
            val reach = base * (1f + ring * 0.32f + wave * 0.06f + level * ring * 0.22f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(warm.copy(alpha = 0.16f + level * 0.10f), cool.copy(alpha = 0.03f), Color.Transparent),
                    center = center,
                    radius = reach * 1.15f,
                ),
                radius = reach * 1.15f,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f + 0.04f * (4 - ring) + level * 0.06f),
                radius = reach,
                center = center,
                style = Stroke(width = 1.2f),
            )
        }
        val radius = base * core * (1f + level * 0.16f)
        val angle = Math.toRadians(spin.toDouble())
        val shift = Offset((kotlin.math.cos(angle) * radius * 0.45f).toFloat(), (kotlin.math.sin(angle) * radius * 0.45f).toFloat())
        drawCircle(
            brush = Brush.radialGradient(listOf(warm, cool), center = center + shift, radius = radius * 1.6f),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = center - shift * 0.6f, radius = radius),
            radius = radius,
            center = center,
        )
    }
}
