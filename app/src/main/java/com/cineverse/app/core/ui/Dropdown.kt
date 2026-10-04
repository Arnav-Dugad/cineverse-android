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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Filters, folded: one "Filters" pill with a count of what is set and a short
 * summary of it beside - "Drama · 7+ · 1990s" - and the full row of choices
 * only when it is opened. A row of six empty dropdowns above every grid was
 * a lot to scroll past to reach the titles; folded, the grid starts a row
 * sooner and what is filtering it is still always written there. The one
 * control reached for every time (the sort) stays outside the fold.
 */
@Composable
fun FoldedFilters(
    summary: List<String>,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    always: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val count = summary.size
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "filtersChevron")
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(count) {
        if (count > 0) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    androidx.compose.foundation.layout.Column(modifier) {
        Row(
            Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = count > 0
            val fill by animateColorAsState(if (on) colors.text else colors.text.copy(alpha = 0.07f), label = "filtersFill")
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CvShape.Pill)
                    .background(fill)
                    .border(1.dp, if (on) Color.Transparent else colors.hairline, CvShape.Pill)
                    .clickableNoRipple {
                        haptics?.play(Haptic.Tap)
                        open = !open
                    }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Tune,
                    null,
                    tint = if (on) colors.ink else colors.text2,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    "  Filters",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.ink else colors.text,
                )
                if (on) {
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .androidx_graphics(pop.value)
                            .size(19.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(com.cineverse.app.core.design.Palette.Red2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null,
                    tint = if (on) colors.ink else colors.text2,
                    modifier = Modifier.size(20.dp).androidx_rotate(turn),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            always()
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            androidx.compose.animation.AnimatedContent(
                summary.joinToString("  \u00b7  "),
                transitionSpec = {
                    (androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 }) togetherWith
                        androidx.compose.animation.fadeOut()
                },
                label = "filtersSummary",
                modifier = Modifier.weight(1f),
            ) { line ->
                Text(
                    line.ifEmpty { "None set" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.isEmpty()) colors.text3 else colors.text2,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = horizontalPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

private fun Modifier.androidx_graphics(scale: Float) = this.graphicsLayer { scaleX = scale; scaleY = scale }

private fun Modifier.androidx_rotate(degrees: Float) = this.graphicsLayer { rotationZ = degrees }
