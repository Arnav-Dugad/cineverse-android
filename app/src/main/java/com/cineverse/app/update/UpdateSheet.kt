package com.cineverse.app.update

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.clickableNoRipple

/**
 * The update sheet.
 *
 * Three things it must do, because this is the one piece of the app that touches
 * the installer and a bad experience here is the kind people stop opening an app
 * over:
 *
 *  1. Ask before downloading anything. 40 MB on mobile data without consent is
 *     a hostile act.
 *  2. Show real progress — megabytes and a percentage, not a spinner.
 *  3. Say what is actually happening at each stage, including verification,
 *     which is the step where a corrupt download is caught.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateState,
    currentVersion: String,
    onDownload: (Release) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onSkip: (Release) -> Unit,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = colors.surface1,
        contentColor = colors.text,
        shape = CvShape.Sheet,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CvShape.Pill)
                        .background(colors.text3.copy(alpha = 0.4f))
                )
            }
        },
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            when (state) {
                is UpdateState.Available -> Available(
                    release = state.release,
                    currentVersion = currentVersion,
                    onDownload = { haptics?.play(Haptic.Tap); onDownload(state.release) },
                    onSkip = { haptics?.play(Haptic.Tap); onSkip(state.release) },
                )

                is UpdateState.Downloading -> Downloading(state)

                is UpdateState.Verifying -> Stage(
                    icon = null,
                    title = "Checking the download",
                    body = "Making sure the file is exactly what the release says it is.",
                )

                is UpdateState.ReadyToInstall -> {
                    Stage(
                        icon = Icons.Rounded.CheckCircle,
                        tint = colors.green,
                        title = "Version ${state.release.versionName} is ready",
                        body = "Android will ask you to confirm the install.",
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Install") {
                        haptics?.play(Haptic.Success); onInstall(state.file)
                    }
                }

                is UpdateState.Failed -> {
                    Stage(
                        icon = Icons.Rounded.ErrorOutline,
                        tint = Palette.Red2,
                        title = "That did not work",
                        body = state.reason,
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Try again") { onRetry() }
                }

                UpdateState.Checking -> Stage(
                    icon = null,
                    title = "Looking for an update",
                    body = "Asking GitHub what the newest release is.",
                )

                UpdateState.UpToDate -> Stage(
                    icon = Icons.Rounded.CheckCircle,
                    tint = colors.green,
                    title = "You are up to date",
                    body = "CineVerse $currentVersion is the newest release.",
                )

                UpdateState.Idle -> Stage(
                    icon = Icons.Rounded.CloudDownload,
                    title = "CineVerse $currentVersion",
                    body = "Updates come from GitHub Releases. The app checks every few hours.",
                )
            }
            // Available already prints this release notes; the link is for the
            // other states, where someone checking for an update may well want
            // to know what the last few actually contained.
            if (state !is UpdateState.Available) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "See all release notes",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Tap); onHistory() }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Available(
    release: Release,
    currentVersion: String,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = CvTheme.colors
    Column {
        Text("UPDATE AVAILABLE", style = KickerStyle, color = Palette.Red2)
        Spacer(Modifier.height(8.dp))
        Text(
            "Version ${release.versionName}",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
        )
        Text(
            buildString {
                append("You have $currentVersion")
                if (release.sizeBytes > 0) append("  ·  ${megabytes(release.sizeBytes)}")
                if (release.publishedAt.isNotBlank()) append("  ·  ${release.publishedAt}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.text3,
        )
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .glass(CvShape.Large, raised = false)
                    .padding(14.dp)
            ) {
                Text(
                    release.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text2,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Download and install", onDownload)
        Spacer(Modifier.height(6.dp))
        // Skipping is permanent for that version and nothing else: the next
        // release will still be offered.
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CvShape.Pill)
                .clickableNoRipple(onSkip),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Skip this version",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
    }
}

@Composable
private fun Downloading(state: UpdateState.Downloading) {
    val colors = CvTheme.colors
    val fraction by animateFloatAsState(
        targetValue = state.fraction,
        animationSpec = Motion.fade(Motion.Quick),
        label = "progress",
    )
    Column {
        Text("DOWNLOADING", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(8.dp))
        Text(
            "Version ${state.release.versionName}",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
        )
        Spacer(Modifier.height(20.dp))
        FilmStrip(fraction)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${megabytes(state.bytes)} of ${megabytes(state.total)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text,
            )
        }
    }
}

/**
 * The download as a strip of film: twelve frames between two rows of sprocket
 * holes, each lighting up as its share of the megabytes arrives - the one
 * that is filling glows as far as it has got. A bar says "progress"; a strip
 * of film says "a new CineVerse is coming in".
 */
@Composable
private fun FilmStrip(fraction: Float) {
    val colors = CvTheme.colors
    val frames = 12
    val percent = (fraction * 100).toInt()
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                contentDescription = "Downloaded $percent percent"
            }
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        drawRoundRect(color = Color(0xFF111118), cornerRadius = radius)
        // Sprocket holes, top and bottom.
        val hole = androidx.compose.ui.geometry.Size(5.dp.toPx(), 4.dp.toPx())
        val holes = 24
        val gapX = size.width / holes
        for (row in listOf(3.dp.toPx(), size.height - 3.dp.toPx() - hole.height)) {
            for (i in 0 until holes) {
                drawRoundRect(
                    color = Color(0xFF2A2A36),
                    topLeft = androidx.compose.ui.geometry.Offset(i * gapX + (gapX - hole.width) / 2, row),
                    size = hole,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        // The frames.
        val pad = 3.dp.toPx()
        val top = 10.dp.toPx()
        val height = size.height - top * 2
        val frameWidth = (size.width - pad * (frames + 1)) / frames
        for (i in 0 until frames) {
            val left = pad + i * (frameWidth + pad)
            val lit = ((fraction * frames) - i).coerceIn(0f, 1f)
            drawRoundRect(
                color = Color(0xFF1C1C26),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(frameWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
            if (lit > 0f) {
                // Red through to gold along the strip, so the end of the
                // download looks like the end of the reel.
                val t = i / (frames - 1f)
                val tone = androidx.compose.ui.graphics.lerp(Palette.Red2, Palette.Gold, t)
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(tone.copy(alpha = lit), tone.copy(alpha = 0.55f * lit)),
                        startY = top, endY = top + height,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(frameWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun Stage(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    body: String,
    tint: Color = Color.Unspecified,
) {
    val colors = CvTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = if (tint == Color.Unspecified) colors.text2 else tint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
            } else {
                CircularProgressIndicator(
                    color = Palette.Red2,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(14.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.text3)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CvShape.Pill)
            .background(Palette.Red)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "—" else "%.1f MB".format(bytes / 1_048_576.0)
