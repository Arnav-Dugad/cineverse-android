package com.cineverse.app.feature.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaFilter
import com.cineverse.app.data.model.SortOrder
import com.cineverse.app.data.model.TypeFilter

/**
 * Narrowing a list down.
 *
 * Applied LIVE, as each choice is made, with the result count on the button. The
 * alternative — collect six choices, press Apply, discover you filtered
 * everything away — makes you do the whole thing twice. Here the button reads
 * "Show 42" and goes grey at zero before you have committed to anything.
 */
@Composable
fun FilterSheet(
    filter: MediaFilter,
    genres: List<Genre>,
    resultCount: Int,
    showHideWatched: Boolean,
    sorts: List<SortOrder>,
    onChange: (MediaFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    fun set(next: MediaFilter) {
        haptics?.play(Haptic.Select)
        onChange(next)
    }

    CvSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NARROW IT DOWN", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(5.dp))
                Text("Filters", style = MaterialTheme.typography.titleLarge, color = colors.text)
            }
            AnimatedVisibility(
                visible = !filter.isDefault,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(CvShape.Pill)
                        .background(colors.glass)
                        .clickableNoRipple { haptics?.play(Haptic.Untick); onChange(filter.clear()) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium, color = colors.text2)
                }
            }
        }

        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Spacer(Modifier.height(18.dp))

            Group("Type") {
                for (value in TypeFilter.entries) {
                    Chip(value.label, filter.type == value) { set(filter.copy(type = value)) }
                }
            }

            Group("Sort by") {
                for (value in sorts) {
                    Chip(value.label, filter.sort == value) { set(filter.copy(sort = value)) }
                }
            }

            Group("Minimum rating") {
                Chip("Any", filter.minRating == 0) { set(filter.copy(minRating = 0)) }
                for (value in listOf(6, 7, 8, 9)) {
                    Chip("$value+", filter.minRating == value) { set(filter.copy(minRating = value)) }
                }
            }

            if (genres.isNotEmpty()) {
                Group("Genre") {
                    Chip("Any", filter.genreId == 0) { set(filter.copy(genreId = 0)) }
                    for (genre in genres) {
                        Chip(genre.name, filter.genreId == genre.id) {
                            set(filter.copy(genreId = genre.id))
                        }
                    }
                }
            }

            Group("Decade") {
                Chip("Any", filter.decade == 0) { set(filter.copy(decade = 0)) }
                for (value in MediaFilter.decades()) {
                    Chip("${value}s", filter.decade == value) { set(filter.copy(decade = value)) }
                }
            }

            if (showHideWatched) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CvShape.Medium)
                        .background(colors.glass)
                        .border(1.dp, colors.hairline, CvShape.Medium)
                        .clickableNoRipple { set(filter.copy(hideWatched = !filter.hideWatched)) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Hide what you have watched",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CvShape.Tiny)
                            .background(if (filter.hideWatched) colors.text else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (filter.hideWatched) colors.text else colors.text3.copy(alpha = 0.5f),
                                CvShape.Tiny,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filter.hideWatched) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                tint = colors.ink,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Button(
            onClick = { haptics?.play(Haptic.Tap); onDismiss() },
            enabled = resultCount > 0,
            shape = CvShape.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.text,
                contentColor = colors.ink,
                disabledContainerColor = colors.glass,
                disabledContentColor = colors.text3,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                when (resultCount) {
                    0 -> "Nothing matches"
                    1 -> "Show 1 title"
                    else -> "Show $resultCount titles"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = CvTheme.colors
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title.uppercase(), style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.98f,
        animationSpec = Motion.lively(),
        label = "chip",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(38.dp)
            .clip(CvShape.Pill)
            .background(if (active) colors.text else colors.glass)
            .border(1.dp, if (active) Color.Transparent else colors.hairline, CvShape.Pill)
            .clickableNoRipple(onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.ink else colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The bar above a list: a Filters button that carries its own count.
 *
 * The count is the whole point. A filter that is on and invisible is the
 * commonest way a list ends up looking broken — you come back an hour later,
 * half your library is missing, and nothing on screen says why.
 */
@Composable
fun FilterBar(
    filter: MediaFilter,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val on = !filter.isDefault
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .height(38.dp)
                .clip(CvShape.Pill)
                .background(if (on) Palette.Red2.copy(alpha = 0.16f) else colors.glass)
                .border(
                    1.dp,
                    if (on) Palette.Red2.copy(alpha = 0.5f) else colors.hairline,
                    CvShape.Pill,
                )
                .clickableNoRipple { haptics?.play(Haptic.Tap); onOpen() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = null,
                tint = if (on) Palette.Red2 else colors.text2,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Filters",
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Palette.Red2 else colors.text2,
            )
            if (on) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(Palette.Red2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        filter.activeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        if (trailing != null) trailing()
    }
}
