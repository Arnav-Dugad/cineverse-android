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
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvScreenBar
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.update.UpdateState

/**
 * Everything you can change.
 *
 * Split out of the account page, because they are two different errands. One is
 * "who am I and what have I watched", opened occasionally; the other is "make
 * the app behave differently", opened rarely and read carefully. Putting
 * thirty switches under a profile card meant scrolling past your own face to
 * reach a toggle, and meant the profile page was never glanceable.
 */
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar(title = "Settings", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
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

        item(key = "density") {
            ChoiceRow(
                title = "Poster size",
                options = com.cineverse.app.data.prefs.GridDensity.entries.map { it.label },
                selected = settings.gridDensity.label,
                onSelect = { label ->
                    com.cineverse.app.data.prefs.GridDensity.entries
                        .firstOrNull { it.label == label }
                        ?.let(viewModel::setGridDensity)
                },
            )
        }

        item(key = "captions") {
            SwitchRow(
                title = "Titles under posters",
                checked = settings.posterCaptions,
                onChange = viewModel::setPosterCaptions,
            )
        }

        item(key = "posterMeta") {
            SwitchRow(
                title = "Year and type under posters",
                detail = "Adds \"2020 · Series\" beneath the name.",
                checked = settings.posterMeta,
                onChange = viewModel::setPosterMeta,
                enabled = settings.posterCaptions,
            )
        }

        item(key = "corner") {
            ChoiceRow(
                title = "Poster corners",
                options = com.cineverse.app.core.ui.PosterCorner.entries.map { it.label },
                selected = runCatching {
                    com.cineverse.app.core.ui.PosterCorner.valueOf(settings.posterCorner).label
                }.getOrDefault(com.cineverse.app.core.ui.PosterCorner.Rounded.label),
                onSelect = { label ->
                    com.cineverse.app.core.ui.PosterCorner.entries
                        .firstOrNull { it.label == label }
                        ?.let { viewModel.setPosterCorner(it.name) }
                },
            )
        }

        item(key = "titleLogos") {
            SwitchRow(
                title = "Title artwork",
                checked = settings.titleLogos,
                onChange = viewModel::setTitleLogos,
            )
        }

        item(key = "match") {
            SwitchRow(
                title = "Match percentage",
                checked = settings.posterMatch,
                onChange = viewModel::setPosterMatch,
            )
        }

        item(key = "showRatings") {
            SwitchRow(
                title = "Ratings on posters",
                checked = settings.showRatings,
                onChange = viewModel::setShowRatings,
            )
        }

        item(key = "showWatched") {
            SwitchRow(
                title = "Mark what you have seen",
                checked = settings.showWatched,
                onChange = viewModel::setShowWatched,
            )
        }

        item(key = "startTab") {
            ChoiceRow(
                title = "Open on",
                options = listOf("Home", "Discover", "My List", "Stats"),
                selected = when (settings.startTab) {
                    "discover" -> "Discover"
                    "list" -> "My List"
                    "stats" -> "Stats"
                    else -> "Home"
                },
                onSelect = { label ->
                    viewModel.setStartTab(
                        when (label) {
                            "Discover" -> "discover"
                            "My List" -> "list"
                            "Stats" -> "stats"
                            else -> "home"
                        }
                    )
                },
            )
        }

        item(key = "watching") { GroupHeading("Watching") }

        item(key = "spoilers") {
            SwitchRow(
                title = "Hide episode spoilers",
                checked = settings.spoilerShield,
                onChange = viewModel::setSpoilerShield,
            )
        }

        item(key = "autoplay") {
            SwitchRow(
                title = "Autoplay trailers",
                checked = settings.autoplay,
                onChange = viewModel::setAutoplay,
            )
        }

        item(key = "wifi") {
            SwitchRow(
                title = "Only on Wi-Fi",
                checked = settings.autoplayOnWifiOnly,
                onChange = viewModel::setAutoplayWifi,
                enabled = settings.autoplay,
            )
        }

        item(key = "heroAuto") {
            SwitchRow(
                title = "Hero moves by itself",
                checked = settings.heroAutoAdvance,
                onChange = viewModel::setHeroAutoAdvance,
            )
        }

        item(key = "heroSeconds") {
            StepperRow(
                title = "Seconds per slide",
                value = settings.heroSeconds,
                range = 4..30,
                step = 1,
                enabled = settings.heroAutoAdvance,
                onChange = viewModel::setHeroSeconds,
            )
        }

        item(key = "countdowns") {
            SwitchRow(
                title = "Countdown to the next episode",
                checked = settings.countdowns,
                onChange = viewModel::setCountdowns,
            )
        }

        item(key = "episodeSwipe") {
            SwitchRow(
                title = "Swipe an episode to catch up",
                checked = settings.episodeSwipe,
                onChange = viewModel::setEpisodeSwipe,
            )
        }

        item(key = "confetti") {
            SwitchRow(
                title = "Celebrate a perfect ten",
                checked = settings.confetti,
                onChange = viewModel::setConfetti,
            )
        }

        item(key = "content") { GroupHeading("Content") }

        item(key = "region") {
            ChoiceRow(
                title = "Region",
                options = REGIONS.map { it.second },
                selected = REGIONS.firstOrNull { it.first == settings.region }?.second
                    ?: REGIONS.first().second,
                onSelect = { label ->
                    REGIONS.firstOrNull { it.second == label }?.let { viewModel.setRegion(it.first) }
                },
            )
        }

        item(key = "mature") {
            SwitchRow(
                title = "Include adult titles",
                checked = settings.mature,
                onChange = viewModel::setMature,
            )
        }

        item(key = "matureBlur") {
            SwitchRow(
                title = "Blur adult artwork",
                detail = "Covers artwork until tapped",
                checked = settings.matureBlur,
                onChange = viewModel::setMatureBlur,
                enabled = settings.mature,
            )
        }

        item(key = "notify") { GroupHeading("Notify") }

        item(key = "episodes") {
            SwitchRow(
                title = "New episodes",
                checked = settings.notifyNewEpisodes,
                onChange = viewModel::setNotifyEpisodes,
            )
        }

        item(key = "releases") {
            SwitchRow(
                title = "Releases",
                checked = settings.notifyReleases,
                onChange = viewModel::setNotifyReleases,
            )
        }

        // Asked FOR, not asked at launch.
        //
        // A permission prompt in the first five seconds of an app, before it has
        // shown anything worth being notified about, is the prompt people deny
        // by reflex and never revisit. This one sits under the two toggles it
        // belongs to, so it is requested by someone who has just said they want
        // the thing it enables.
        item(key = "notifyPermission") {
            val granted = com.cineverse.app.notify.Notifications.canPost(context)
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { allowed ->
                if (allowed) haptics?.play(Haptic.Success) else haptics?.play(Haptic.Warning)
            }
            if (!granted && (settings.notifyNewEpisodes || settings.notifyReleases)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 10.dp)
                        .clip(CvShape.Medium)
                        .background(Palette.Red2.copy(alpha = 0.12f))
                        .clickableNoRipple {
                            haptics?.play(Haptic.Tap)
                            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Allow notifications",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                        )
                        Text(
                            "Not granted yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.text3,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        null,
                        tint = Palette.Red2,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        item(key = "checkNow") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple {
                        haptics?.play(Haptic.Tap)
                        com.cineverse.app.notify.WatchWorker.runNow(context)
                        viewModel.say("Checking for new episodes and releases…")
                    }
                    .padding(horizontal = ScreenPadding, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    null,
                    tint = colors.text2,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Check now",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                    )
                    Text(
                        "Once a day, on Wi-Fi",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                }
            }
        }

        }
    }
}

/**
 * A row that goes somewhere.
 *
 * Three near-identical copies of this existed with slightly different padding,
 * which is how a settings list ends up looking hand-assembled.
 */
@Composable
internal fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    detail: String? = null,
    tint: Color? = null,
) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = ScreenPadding, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: colors.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint ?: colors.text,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            null,
            tint = colors.text3,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
internal fun CountTile(label: String, value: Int, modifier: Modifier = Modifier) {
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

/**
 * A number with two ends.
 *
 * A slider would be wrong here: the useful range is small, the values are whole
 * seconds, and a thumb that has to land on "7" among twenty-six positions is a
 * thumb that lands on 6 or 8. Two buttons and the figure between them is both
 * exact and larger than any slider thumb.
 */
@Composable
internal fun StepperRow(
    title: String,
    detail: String? = null,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 12.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .clip(CvShape.Pill)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.Pill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("\u2212", enabled && value > range.first) {
                haptics?.play(Haptic.Detent)
                onChange((value - step).coerceIn(range))
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = colors.text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(34.dp),
            )
            StepButton("+", enabled && value < range.last) {
                haptics?.play(Haptic.Detent)
                onChange((value + step).coerceIn(range))
            }
        }
    }
}

@Composable
internal fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = CvTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CvShape.Pill)
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) colors.text else colors.text3.copy(alpha = 0.4f),
        )
    }
}

/**
 * The regions worth offering.
 *
 * Not every ISO country: a list of two hundred is a list nobody scrolls. These
 * are the ones with meaningfully different streaming catalogues, with India
 * first because that is where this app is used.
 */
private val REGIONS = listOf(
    "IN" to "India",
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "DE" to "Germany",
    "FR" to "France",
    "ES" to "Spain",
    "IT" to "Italy",
    "BR" to "Brazil",
    "JP" to "Japan",
    "KR" to "South Korea",
)

@Composable
internal fun GroupHeading(text: String) {
    Text(
        text.uppercase(),
        style = KickerStyle,
        color = CvTheme.colors.text3,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp),
    )
}

@Composable
internal fun SwitchRow(
    title: String,
    /**
     * Optional, and usually absent.
     *
     * A sentence under every switch turns a list of choices into a wall of
     * prose, and a title that needs explaining is usually a title that needs
     * rewriting. Only the few that are genuinely ambiguous keep one.
     */
    detail: String? = null,
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
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
            }
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
internal fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    detail: String? = null,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
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
