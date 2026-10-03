package com.cineverse.app.feature.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.lock.ListLocks
import com.cineverse.app.data.model.UserList
import kotlinx.coroutines.launch

enum class PinMode(val title: String, val hint: String) {
    Unlock("Enter the PIN", "Opens this list until CineVerse is closed."),
    Set("Set a PIN", "4 to 8 digits. You will need it each time CineVerse starts."),
    Change("Change the PIN", "Enter the current PIN first, then choose a new one."),
    Remove("Remove the PIN", "Enter the current PIN to open this list for good."),
}

private enum class PinStep(val label: String) { Current("Current PIN"), First("New PIN"), Confirm("Confirm the new PIN"), Unlock("PIN") }

/**
 * The PIN pad, ported from the website's list-lock.js: dots that fill as you
 * type, a gate at the fourth, a shake and a buzz for a wrong PIN, and the same
 * plain-English note that it is a privacy screen and not encryption.
 */
@Composable
fun PinSheet(
    list: UserList,
    mode: PinMode,
    /** Checks a PIN against the stored lock. */
    verify: suspend (String) -> Boolean,
    /** Applies the outcome: unlock, save a new PIN, or remove it. */
    onDone: suspend (pin: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var step by remember {
        mutableStateOf(
            when (mode) {
                PinMode.Set -> PinStep.First
                PinMode.Unlock -> PinStep.Unlock
                else -> PinStep.Current
            }
        )
    }
    var value by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var shakes by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakes) {
        if (shakes == 0) return@LaunchedEffect
        for (x in listOf(-18f, 16f, -12f, 9f, -5f, 0f)) shake.animateTo(x, tween(45))
    }

    fun fail(message: String) {
        error = message
        value = ""
        shakes++
        haptics?.play(Haptic.Warning)
    }

    fun submit() {
        if (busy || !ListLocks.isValidPin(value)) return
        busy = true
        val entered = value
        scope.launch {
            try {
                when (step) {
                    PinStep.Unlock, PinStep.Current -> {
                        if (!verify(entered)) { fail("That PIN is not right."); return@launch }
                        if (mode == PinMode.Change) {
                            step = PinStep.First; value = ""; error = null
                        } else if (onDone(entered)) {
                            haptics?.play(Haptic.Success); onDismiss()
                        } else fail("Could not save. Check your connection.")
                    }
                    PinStep.First -> { first = entered; value = ""; error = null; step = PinStep.Confirm }
                    PinStep.Confirm -> {
                        if (entered != first) { step = PinStep.First; first = ""; fail("Those did not match. Start again."); return@launch }
                        if (onDone(entered)) { haptics?.play(Haptic.Success); onDismiss() }
                        else fail("Could not save. Check your connection.")
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    CvSheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (mode == PinMode.Unlock) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null,
                tint = Palette.Red2, modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (mode == PinMode.Unlock) "LOCKED LIST" else "PRIVATE LIST"),
                style = KickerStyle, color = colors.text3,
            )
            Text(mode.title, style = MaterialTheme.typography.titleLarge, color = colors.text)
            Text("${list.name} · ${step.label}", style = MaterialTheme.typography.labelMedium, color = colors.text3)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.graphicsLayer { translationX = shake.value },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (index in 0 until ListLocks.MAX_LENGTH) {
                    val on = index < value.length
                    val fill by animateColorAsState(
                        if (on) colors.text else Color.Transparent, tween(120), label = "dot",
                    )
                    val grow by animateFloatAsState(if (on) 1f else 0.8f, label = "dotSize")
                    Box(
                        Modifier
                            .size(14.dp)
                            .graphicsLayer { scaleX = grow; scaleY = grow }
                            .clip(CvShape.Circle)
                            .background(fill)
                            .border(
                                1.5.dp,
                                if (index == ListLocks.MIN_LENGTH - 1 && !on) colors.text2 else colors.text3,
                                CvShape.Circle,
                            )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                error ?: mode.hint,
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) Palette.Red2 else colors.text3,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
            Spacer(Modifier.height(14.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "back")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (key in row) {
                        Box(
                            Modifier
                                .size(width = 84.dp, height = 58.dp)
                                .clip(CvShape.Large)
                                .background(if (key.length == 1) colors.text.copy(alpha = 0.07f) else Color.Transparent)
                                .clickableNoRipple {
                                    error = null
                                    when (key) {
                                        "clear" -> value = ""
                                        "back" -> value = value.dropLast(1)
                                        else -> if (value.length < ListLocks.MAX_LENGTH) {
                                            value += key
                                            haptics?.play(Haptic.Tap)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "back" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = colors.text2)
                                "clear" -> Text("Clear", style = MaterialTheme.typography.labelLarge, color = colors.text2)
                                else -> Text(key, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(4.dp))
            CvButton(
                when {
                    busy -> "Checking…"
                    step == PinStep.Confirm -> "Save PIN"
                    step == PinStep.Unlock -> "Unlock"
                    else -> "Continue"
                },
                ::submit,
                enabled = !busy && ListLocks.isValidPin(value),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "A PIN hides this list on screen. It is a privacy screen, not encryption: the titles stay in your own account.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What a long press on a list offers. */
@Composable
fun LockOptionsSheet(
    list: UserList,
    unlocked: Boolean,
    onPick: (PinMode?) -> Unit,
    onLockNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    CvSheet(onDismiss) {
        Text(list.name, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(
            if (list.hasPin) "Protected by a PIN" else "Anyone holding your phone can see this list",
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
        )
        Spacer(Modifier.height(16.dp))
        if (!list.hasPin) {
            CvButton("Set a PIN", { onPick(PinMode.Set) }, icon = Icons.Rounded.Lock, modifier = Modifier.fillMaxWidth())
        } else {
            if (unlocked) {
                CvButton("Lock it now", onLockNow, icon = Icons.Rounded.Lock, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            CvButton("Change the PIN", { onPick(PinMode.Change) }, primary = false, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            CvButton("Remove the PIN", { onPick(PinMode.Remove) }, primary = false, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** In place of the grid while the list is locked: nothing about it is drawn. */
@Composable
fun LockedPanel(list: UserList, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Box(modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CvShape.Circle)
                    .background(Palette.Red2.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Palette.Red2, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("${list.name} is locked", style = MaterialTheme.typography.titleLarge, color = colors.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter the PIN to show its titles. It locks again when CineVerse closes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            CvButton("Enter PIN", onUnlock, icon = Icons.Rounded.LockOpen)
            Spacer(Modifier.width(1.dp))
        }
    }
}
