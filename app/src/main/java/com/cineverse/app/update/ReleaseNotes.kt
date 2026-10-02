package com.cineverse.app.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvSheet
import com.cineverse.app.core.ui.clickableNoRipple

/**
 * What changed, the first time you open the app after it updated itself.
 *
 * This is the piece sideloading usually leaves out. The installer prompt is the
 * last thing the user saw; without this they have a new build and no idea what
 * is in it. It appears exactly once per version, it is dismissible with one tap,
 * and it never blocks anything — the app is already behind it and usable.
 */
@Composable
fun WhatsNewSheet(
    release: Release,
    onHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    // A single celebratory beat on arrival. Any more than that and a changelog
    // starts behaving like a reward screen.
    LaunchedEffect(release.versionCode) { haptics?.play(Haptic.Celebrate) }

    CvSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.Red.copy(alpha = 0.35f), colors.purple.copy(alpha = 0.3f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("JUST UPDATED", style = KickerStyle, color = Palette.Red2)
                Spacer(Modifier.height(3.dp))
                Text(
                    "CineVerse ${release.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        NotesBody(release.notes.ifBlank { "No notes were published with this release." })

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .height(50.dp)
                    .clip(CvShape.Pill)
                    .background(colors.glass)
                    .border(1.dp, colors.hairline, CvShape.Pill)
                    .clickableNoRipple { haptics?.play(Haptic.Tap); onHistory() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("All versions", style = MaterialTheme.typography.labelLarge, color = colors.text2)
            }
            Button(
                onClick = { haptics?.play(Haptic.Tap); onDismiss() },
                shape = CvShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.text,
                    contentColor = colors.ink,
                ),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("Got it", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Every release, newest first, each one expandable.
 *
 * Collapsed by default except the newest: thirty expanded changelogs is a wall,
 * and the reason someone opens this is almost always the top one or a specific
 * version they remember.
 */
@Composable
fun VersionHistorySheet(
    releases: List<Release>,
    currentCode: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    CvSheet(onDismiss = onDismiss) {
        Text("RELEASE NOTES", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        Text(
            "Version history",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
        )
        Spacer(Modifier.height(16.dp))

        when {
            loading && releases.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.text3, strokeWidth = 2.dp)
            }

            releases.isEmpty() -> Text(
                "Could not reach GitHub for the release list. It will be here next time there is a connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text3,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            else -> LazyColumn(
                Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(releases, key = { _, release -> release.versionCode }) { index, release ->
                    HistoryRow(
                        release = release,
                        installed = release.versionCode == currentCode,
                        startExpanded = index == 0,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HistoryRow(release: Release, installed: Boolean, startExpanded: Boolean) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var expanded by remember { mutableStateOf(startExpanded) }
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.lively(),
        label = "chevron",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Medium)
            .background(if (installed) colors.text.copy(alpha = 0.07f) else colors.glass)
            .border(
                1.dp,
                if (installed) colors.text.copy(alpha = 0.22f) else colors.hairline,
                CvShape.Medium,
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { haptics?.play(Haptic.Tap); expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        release.versionName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                    )
                    if (installed) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(CvShape.Pill)
                                .background(colors.green.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.green,
                            )
                        }
                    }
                }
                if (release.publishedAt.isNotBlank()) {
                    Text(
                        release.publishedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                    )
                }
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.text3,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.size()) + fadeIn(),
            exit = shrinkVertically(Motion.size()) + fadeOut(),
        ) {
            Text(
                release.notes.ifBlank { "No notes were published with this release." },
                style = MaterialTheme.typography.bodySmall,
                color = colors.text2,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

/** A scrolling panel of notes, capped so a long changelog cannot eat the sheet. */
@Composable
private fun NotesBody(notes: String) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(CvShape.Large)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.Large)
            .padding(14.dp)
    ) {
        Text(
            notes,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}
