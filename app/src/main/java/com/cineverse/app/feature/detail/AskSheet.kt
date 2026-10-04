package com.cineverse.app.feature.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
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
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, turns.lastOrNull()?.answer) {
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex)
    }
    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || turns.lastOrNull()?.answer == null && turns.isNotEmpty()) return
        haptics?.play(Haptic.Tap)
        onAsk(question)
        draft = ""
    }

    CvSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = GeminiColors[1], modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ask about $title", style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1)
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
            items(turns, key = { it.question + turns.indexOf(it) }) { turn ->
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
                        Text(
                            answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            modifier = Modifier
                                .graphicsLayer { alpha = arrive; translationY = (1f - arrive) * 10f }
                                .widthIn(max = 320.dp)
                                .geminiGlow(turn.byGemini, corner = 18.dp, width = 1.2.dp)
                                .clip(CvShape.Large)
                                .background(colors.text.copy(alpha = 0.05f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
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
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
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
