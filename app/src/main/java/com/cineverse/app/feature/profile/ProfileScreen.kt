package com.cineverse.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.tabular
import androidx.compose.ui.graphics.graphicsLayer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.MotionChoice
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.ThemeChoice
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.update.UpdateState

/**
 * You: the account, everything the app can be told to do differently, and the
 * updater.
 *
 * It is one scroll rather than the website's eleven settings panels, because a
 * phone's settings are read once and then never again — anything that needs a
 * section heading and a search box has too many options.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSignIn: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = BottomBarSpace),
    ) {
        item(key = "account") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 10.dp)
                    .glass(CvShape.XLarge)
                    .clickableNoRipple { if (user == null) onSignIn() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Palette.Red, Palette.Purple))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!user?.photo.isNullOrBlank()) {
                        CvImage(user?.photo, user?.name, Modifier.fillMaxSize())
                    } else {
                        Text(
                            (user?.name?.firstOrNull() ?: '?').uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        user?.name?.takeIf { it.isNotBlank() } ?: "Not signed in",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        user?.email?.takeIf { it.isNotBlank() }
                            ?: "Sign in to sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (user == null) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        null,
                        tint = colors.text3,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        if (user != null) {
            item(key = "counts") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CountTile("Saved", library.saved.size, Modifier.weight(1f))
                    CountTile("Watched", library.watched.size, Modifier.weight(1f))
                    CountTile("Rated", library.ratings.size, Modifier.weight(1f))
                }
            }
        }

        item(key = "settings") {
            NavRow(
                icon = Icons.Rounded.Tune,
                title = "Settings",
                detail = "",
                onClick = { haptics?.play(Haptic.Tap); onOpenSettings() },
            )
        }

        item(key = "app") { GroupHeading("App") }

        item(key = "update") {
            NavRow(
                icon = Icons.Rounded.Download,
                title = "Check for updates",
                detail = when (val value = update) {
                    is UpdateState.Available -> "Version ${value.release.versionName} is ready"
                    is UpdateState.Downloading -> "Downloading ${(value.fraction * 100).toInt()}%"
                    UpdateState.UpToDate -> "Up to date"
                    else -> viewModel.currentVersion
                },
                tint = if (update is UpdateState.Available) Palette.Red2 else null,
                onClick = { haptics?.play(Haptic.Tap); onOpenUpdate() },
            )
        }

        item(key = "notes") {
            NavRow(
                icon = Icons.Rounded.Description,
                title = "Release notes",
                onClick = { haptics?.play(Haptic.Tap); onOpenReleaseNotes() },
            )
        }

        // Only when there is something to show. A permanent "Crash reports (0)"
        // row is an invitation to worry about a thing that has not happened.
        val crashes: List<com.cineverse.app.core.crash.CrashReport> = viewModel.crashes
        if (crashes.isNotEmpty()) {
            item(key = "crashes") {
                Column(Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp)) {
                    Text(
                        "Crash reports",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                    )
                    Text(
                        "${crashes.size} on this phone, none sent",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                    Spacer(Modifier.height(12.dp))
                    for (report in crashes.take(3).toList()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .glass(CvShape.Medium, raised = false)
                                .clickableNoRipple {
                                    haptics?.play(Haptic.Tap)
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(viewModel.crashIssueUrl(report)),
                                            )
                                        )
                                    }
                                }
                                .padding(13.dp)
                        ) {
                            Text(
                                report.summary,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.text2,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Report on GitHub",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Red2,
                            )
                        }
                    }
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickableNoRipple {
                                haptics?.play(Haptic.Untick)
                                viewModel.clearCrashes()
                            },
                    )
                }
            }
        }

        if (user != null) {
            item(key = "signout") {
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Red2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { haptics?.play(Haptic.Warning); viewModel.signOut() }
                        .padding(horizontal = ScreenPadding, vertical = 18.dp),
                )
            }
        }

        item(key = "about") {
            Column(Modifier.padding(horizontal = ScreenPadding, vertical = 24.dp)) {
                Text(
                    "CineVerse ${viewModel.currentVersion}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
                Text(
                    "TMDB \u00b7 IMDb \u00b7 Rotten Tomatoes \u00b7 Metacritic",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                )
                Text(
                    "Arnav Dugad",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

