package com.cineverse.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics

/**
 * The website's select boxes, as pills: the label until something is chosen,
 * then the choice itself, filled, so what is narrowing a grid is readable at
 * a glance. Every filter in the app is one of these, so filtering looks and
 * works the same on Movies, TV Shows, See all, Search and My List.
 *
 * [active] defaults to "anything but the first option"; a sort whose default
 * is not first in its list says so explicitly.
 */
@Composable
fun <T> CvDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    active: Boolean = selected != options.first().first,
    onSelect: (T) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var open by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (active) colors.text else colors.glass, label = "dropdownFill")
    val ink = if (active) colors.ink else colors.text2
    Box {
        Row(
            Modifier
                .height(36.dp)
                .clip(CvShape.Pill)
                .background(fill)
                .border(1.dp, if (active) Color.Transparent else colors.hairline, CvShape.Pill)
                .clickableNoRipple { haptics?.play(Haptic.Tap); open = true }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active) options.firstOrNull { it.first == selected }?.second ?: label else label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                maxLines = 1,
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = ink, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surface1,
            shape = CvShape.Large,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            for ((value, text) in options) {
                DropdownMenuItem(
                    text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.text) },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Rounded.Check, null, tint = colors.text) }
                    } else null,
                    onClick = {
                        haptics?.play(Haptic.Select)
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** An on/off pill in the same row as the dropdowns: "Hide watched". */
@Composable
fun CvTogglePill(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val fill by animateColorAsState(if (on) colors.text else colors.glass, label = "toggleFill")
    Row(
        Modifier
            .height(36.dp)
            .clip(CvShape.Pill)
            .background(fill)
            .border(1.dp, if (on) Color.Transparent else colors.hairline, CvShape.Pill)
            .clickableNoRipple { haptics?.play(if (on) Haptic.Untick else Haptic.Tick); onChange(!on) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, null, tint = colors.ink, modifier = Modifier.size(16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) colors.ink else colors.text2, maxLines = 1)
    }
}
