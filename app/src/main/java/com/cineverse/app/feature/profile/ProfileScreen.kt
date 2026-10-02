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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.MotionChoice
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.design.ThemeChoice
import com.cineverse.app.core.ui.CvImage
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
    modifier: Modifier = Modifier,
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
    ) {
        item(key = "account") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 10.dp)
                    .clip(CvShape.XLarge)
                    .background(colors.glass)
                    .border(1.dp, colors.hairline, CvShape.XLarge)
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
                            ?: "Sign in to sync with CineVerse on the web",
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

        item(key = "look") { GroupHeading("Look") }

        item(key = "theme") {
            ChoiceRow(
                title = "Theme",
                options = ThemeChoice.entries.map { it.name },
                selected = settings.theme.name,
                onSelect = { viewModel.setTheme(ThemeChoice.valueOf(it)) },
            )
        }

        item(key = "dynamic") {
            SwitchRow(
                title = "Match my wallpaper",
                detail = "Take the accent colour from Android's theme instead of CineVerse red.",
                checked = settings.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )
        }

        item(key = "motion") {
            ChoiceRow(
                title = "Motion",
                options = MotionChoice.entries.map { it.name },
                selected = settings.motion.name,
                onSelect = { viewModel.setMotion(MotionChoice.valueOf(it)) },
            )
        }

        item(key = "haptics") {
            SwitchRow(
                title = "Haptics",
                detail = "Every tick, swipe and milestone has its own feel.",
                checked = settings.haptics,
                onChange = viewModel::setHaptics,
            )
        }

        item(key = "watching") { GroupHeading("Watching") }

        item(key = "spoilers") {
            SwitchRow(
                title = "Hide episode spoilers",
                detail = "Blur the still and summary of an episode until you have watched it.",
                checked = settings.spoilerShield,
                onChange = viewModel::setSpoilerShield,
            )
        }

        item(key = "autoplay") {
            SwitchRow(
                title = "Autoplay trailers",
                detail = "A title's trailer starts by itself, muted, after a moment.",
                checked = settings.autoplay,
                onChange = viewModel::setAutoplay,
            )
        }

        item(key = "wifi") {
            SwitchRow(
                title = "Only on Wi-Fi",
                detail = "Never autoplay on mobile data.",
                checked = settings.autoplayOnWifiOnly,
                onChange = viewModel::setAutoplayWifi,
                enabled = settings.autoplay,
            )
        }

        item(key = "notify") { GroupHeading("Notifications") }

        item(key = "episodes") {
            SwitchRow(
                title = "New episodes",
                detail = "Tell me when a show I am watching airs a new episode.",
                checked = settings.notifyNewEpisodes,
                onChange = viewModel::setNotifyEpisodes,
            )
        }

        item(key = "releases") {
            SwitchRow(
                title = "Releases",
                detail = "Tell me when something on my list comes out.",
                checked = settings.notifyReleases,
                onChange = viewModel::setNotifyReleases,
            )
        }

        item(key = "app") { GroupHeading("App") }

        item(key = "update") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple { haptics?.play(Haptic.Tap); onOpenUpdate() }
                    .padding(horizontal = ScreenPadding, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, null, tint = colors.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.bodyLarge, color = colors.text)
                    Text(
                        when (val value = update) {
                            is UpdateState.Available -> "Version ${value.release.versionName} is ready"
                            is UpdateState.Downloading -> "Downloading… ${(value.fraction * 100).toInt()}%"
                            UpdateState.UpToDate -> "You are on the newest version"
                            else -> "Version ${viewModel.currentVersion}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (update is UpdateState.Available) Palette.Red2 else colors.text3,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    null,
                    tint = colors.text3,
                    modifier = Modifier.size(15.dp),
                )
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
                    "Data from TMDB. Ratings from IMDb, Rotten Tomatoes and Metacritic.",
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

@Composable
private fun CountTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(
        modifier
            .clip(CvShape.Large)
            .background(colors.glass)
            .border(1.dp, colors.hairline, CvShape.Large)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text.uppercase(),
        style = KickerStyle,
        color = CvTheme.colors.text3,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple {
                if (enabled) { haptics?.play(Haptic.Select); onChange(!checked) }
            }
            .padding(horizontal = ScreenPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.text else colors.text3,
            )
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) { haptics?.play(Haptic.Select); onChange(it) } },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Red,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = colors.glassStrong,
                uncheckedBorderColor = colors.hairline,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CvShape.Pill)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (option in options) {
                val active = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(CvShape.Pill)
                        .background(if (active) colors.text.copy(alpha = 0.14f) else Color.Transparent)
                        .clickableNoRipple {
                            if (!active) haptics?.play(Haptic.Select)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) colors.text else colors.text3,
                    )
                }
            }
        }
    }
}
