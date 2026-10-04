package com.cineverse.app.feature.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.TypewriterText
import com.cineverse.app.core.ui.GeminiColors
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.geminiGlow
import com.cineverse.app.core.ui.rememberArrival
import com.cineverse.app.data.ai.ChatTurn
import com.cineverse.app.data.ai.SpoilerLine

/**
 * "Ask about it": a conversation about this one title, that knows how far
 * you have got and will not spoil past it. The line at the top says where
 * that boundary is, so it is never a matter of trust.
 */
@Composable
fun AskTitleSheet(
    title: String,
    line: SpoilerLine,
    suggestions: List<String>,
    turns: List<ChatTurn>,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    /** Finds the titles and people an answer named. */
    resolve: suspend (String) -> com.cineverse.app.data.ai.Mentioned = { com.cineverse.app.data.ai.Mentioned() },
    onOpenTitle: (com.cineverse.app.data.model.MediaItem) -> Unit = {},
    onPerson: (Int) -> Unit = {},
    /** Reads an answer aloud (or not, by the setting) and then calls back. */
    speak: (String, () -> Unit) -> Unit = { _, done -> done() },
    stopSpeaking: () -> Unit = {},
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    // Follow the answer down as it is written, until you scroll yourself.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    LaunchedEffect(turns.size) {
        follow = true
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex, END)
        snapshotFlow { list.canScrollForward }.collect { more ->
            if (more && follow && turns.isNotEmpty()) list.scrollToItem(turns.lastIndex, END)
        }
    }
    val busy = turns.lastOrNull()?.let { it.answer == null || it.writing } == true
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // A spoken conversation: ask by voice, hear the answer, and the mic opens
    // again by itself for the next question. Typing, tapping the mic off or
    // saying nothing ends it.
    val voice = com.cineverse.app.feature.search.rememberVoiceInput()
    var conversation by remember { mutableStateOf(false) }
    val listening = voice.phase == com.cineverse.app.feature.search.VoiceInput.Phase.Listening ||
        voice.phase == com.cineverse.app.feature.search.VoiceInput.Phase.Starting

    fun send(text: String, spoken: Boolean = false) {
        val question = text.trim()
        if (question.isEmpty() || busy) return
        haptics?.play(Haptic.Tap)
        conversation = spoken
        onAsk(question)
        draft = ""
    }
    fun listen() {
        stopSpeaking()
        voice.start { heard -> if (heard.isNotBlank()) send(heard, spoken = true) else conversation = false }
    }
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) listen() }
    fun mic() {
        haptics?.play(Haptic.Tap)
        if (listening) {
            voice.stop()
            conversation = false
            return
        }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) listen() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val last = turns.lastOrNull()
    LaunchedEffect(last?.question, last?.writing, last?.answer != null) {
        val answer = last?.answer
        if (!conversation || answer == null || last.writing) return@LaunchedEffect
        speak(com.cineverse.app.data.ai.Mentions.plain(answer)) {
            if (conversation) scope.launch { kotlinx.coroutines.delay(350); if (conversation) listen() }
        }
    }
    LaunchedEffect(voice.phase) {
        if (voice.phase == com.cineverse.app.feature.search.VoiceInput.Phase.Error) conversation = false
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { stopSpeaking() } }

    CvSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = GeminiColors[1], modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Ask about $title",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (turns.isNotEmpty() && !busy) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "New chat",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Tap); onClear() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .clip(CvShape.Pill)
                .background(Palette.Green2.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Shield, null, tint = Palette.Green2, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(line.label, style = MaterialTheme.typography.labelMedium, color = Palette.Green2)
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            state = list,
            modifier = Modifier.heightIn(min = 120.dp, max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (turns.isEmpty()) {
                item {
                    Text(
                        "Ask anything: what's going on, who someone is, whether it's worth it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text3,
                    )
                }
            }
            itemsIndexed(turns, key = { index, turn -> "$index:${turn.question}" }) { _, turn ->
                Column(Modifier.fillMaxWidth()) {
                    // The question, on the right.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            turn.question,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(CvShape.Large)
                                .background(colors.text)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // The answer, on the left: Gemini's in Gemini's light.
                    val answer = turn.answer
                    if (answer == null) {
                        Thinking()
                    } else {
                        val arrive = rememberArrival(1f, 0, 420)
                        // Names in the answer are links; once it is finished,
                        // what it named is found and laid out under it.
                        val mentioned by androidx.compose.runtime.produceState(com.cineverse.app.data.ai.Mentioned(), answer, turn.writing) {
                            if (!turn.writing && (answer.contains("[[") || answer.contains("{{"))) value = resolve(answer)
                        }
                        fun open(mention: com.cineverse.app.data.ai.Mention) {
                            haptics?.play(Haptic.Tap)
                            scope.launch {
                                val found = mentioned.takeUnless { it.isEmpty } ?: resolve(answer)
                                if (mention.person) found.person(mention)?.let { onPerson(it.id) }
                                else found.title(mention)?.let(onOpenTitle)
                            }
                        }
                        val linked = remember(answer, colors.text) {
                            com.cineverse.app.data.ai.Mentions.annotated(answer, com.cineverse.app.core.ui.GeminiColors[0]) { open(it) }
                        }
                        Column {
                        TypewriterText(
                            linked,
                            writing = turn.writing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            modifier = Modifier
                                .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 10f }
                                .widthIn(max = 320.dp)
                                .geminiGlow(turn.byGemini, corner = 18.dp, width = 1.2.dp, pulse = turn.writing)
                                .clip(CvShape.Large)
                                .background(colors.text.copy(alpha = 0.05f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        if (!mentioned.isEmpty) {
                            Spacer(Modifier.height(10.dp))
                            com.cineverse.app.core.ui.MentionRow(mentioned, onOpen = onOpenTitle, onPerson = onPerson)
                        }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (suggestion in suggestions) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .border(1.dp, colors.hairline, CvShape.Pill)
                        .clickableNoRipple { send(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .clip(CvShape.Pill)
                .background(colors.text.copy(alpha = 0.06f))
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (listening) {
                // Listening: the words as they are heard, where the typing goes.
                Text(
                    voice.heard.ifBlank { "Listening\u2026" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (voice.heard.isBlank()) com.cineverse.app.core.ui.GeminiColors[1] else colors.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                )
            } else BasicTextField(
                value = draft,
                onValueChange = { draft = it; if (it.isNotEmpty()) conversation = false },
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(Palette.Red2),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(draft) }),
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Ask a question", style = MaterialTheme.typography.bodyLarge, color = colors.text3)
                    inner()
                },
            )
            // The mic: swells with your voice while it listens.
            val swell by androidx.compose.animation.core.animateFloatAsState(
                if (listening) 1f + voice.level * 0.35f else 1f, label = "askMicSwell",
            )
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { scaleX = swell; scaleY = swell }
                    .then(if (listening) Modifier.geminiGlow(corner = 20.dp, width = 1.5.dp, pulse = true) else Modifier)
                    .clip(CvShape.Circle)
                    .background(if (listening) colors.text.copy(alpha = 0.12f) else colors.glass)
                    .clickableNoRipple { mic() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Mic,
                    if (listening) "Stop listening" else "Ask by voice",
                    tint = if (listening) com.cineverse.app.core.ui.GeminiColors[1] else colors.text2,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CvShape.Circle)
                    .background(if (draft.isBlank()) colors.glass else colors.text)
                    .clickableNoRipple { send(draft) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    "Send",
                    tint = if (draft.isBlank()) colors.text3 else colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Far enough to land on the end of the last answer, however long it is. */
private const val END = 100_000

/** Three dots that breathe in Gemini's colours while an answer is written. */
@Composable
private fun Thinking() {
    val loop = rememberInfiniteTransition(label = "askThinking")
    Row(
        Modifier
            .clip(CvShape.Large)
            .background(CvTheme.colors.text.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            val phase by loop.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = index * 160, easing = LinearEasing), RepeatMode.Reverse),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = phase; scaleX = 0.7f + 0.3f * phase; scaleY = scaleX }
                    .clip(CvShape.Circle)
                    .background(GeminiColors[index]),
            )
        }
    }
}
