package com.cineverse.app.feature.profile

import androidx.compose.material.icons.rounded.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.setValue
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
            // Room to scroll the last group clear of a pinned navigation bar.
            contentPadding = PaddingValues(bottom = BottomBarSpace),
        ) {
        item(key = "group_appearance") {
            SettingsGroup("Appearance") {
            ChoiceRow(
                title = "Theme",
                options = ThemeChoice.entries.map { it.name },
                selected = settings.theme.name,
                onSelect = { viewModel.setTheme(ThemeChoice.valueOf(it)) },
            )
                Divider()
            SwitchRow(
                title = "Match my wallpaper",
                checked = settings.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )
                Divider()
            ChoiceRow(
                title = "Motion",
                options = MotionChoice.entries.map { it.name },
                selected = settings.motion.name,
                onSelect = { viewModel.setMotion(MotionChoice.valueOf(it)) },
            )
                Divider()
            SwitchRow(
                title = "Haptics",
                checked = settings.haptics,
                onChange = viewModel::setHaptics,
            )
                Divider()
            SwitchRow(
                title = "Moving lights",
                detail = "The glow follows your touch and leans as you scroll",
                checked = settings.movingLights,
                onChange = viewModel::setMovingLights,
            )
            }
        }

        item(key = "group_posters") {
            SettingsGroup("Posters") {
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
                Divider()
            SwitchRow(
                title = "Titles under posters",
                checked = settings.posterCaptions,
                onChange = viewModel::setPosterCaptions,
            )
                Divider()
            SwitchRow(
                title = "Year and type under posters",
                checked = settings.posterMeta,
                onChange = viewModel::setPosterMeta,
                enabled = settings.posterCaptions,
            )
                Divider()
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
                Divider()
            SwitchRow(
                title = "Hide titles on title pages",
                detail = "No logo and no name over the page",
                checked = settings.hideTitle,
                onChange = viewModel::setHideTitle,
            )
                Divider()
            SwitchRow(
                title = "Title logos",
                detail = "When titles show: the title's own logo, not plain type",
                checked = settings.titleLogos,
                onChange = viewModel::setTitleLogos,
                enabled = !settings.hideTitle,
            )
                Divider()
            SwitchRow(
                title = "Colour from the poster",
                checked = settings.titleColour,
                onChange = viewModel::setTitleColour,
            )
                Divider()
            SwitchRow(
                title = "Match percentage",
                checked = settings.posterMatch,
                onChange = viewModel::setPosterMatch,
            )
                Divider()
            SwitchRow(
                title = "Ratings on posters",
                checked = settings.showRatings,
                onChange = viewModel::setShowRatings,
            )
                Divider()
            SwitchRow(
                title = "Mark what you have seen",
                checked = settings.showWatched,
                onChange = viewModel::setShowWatched,
            )
            }
        }

        item(key = "group_start") {
            SettingsGroup("Start") {
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
                Divider()
            SwitchRow(
                title = "Keep the navigation bar pinned",
                detail = "Always on screen, on title pages too",
                checked = settings.pinNavBar,
                onChange = viewModel::setPinNavBar,
            )
            }
        }


        item(key = "group_watching") {
            SettingsGroup("Watching") {
            SwitchRow(
                title = "Voice search answers aloud",
                detail = "After you speak, it says what it found or did",
                checked = settings.spokenAnswers,
                onChange = viewModel::setSpokenAnswers,
            )
                Divider()
            SwitchRow(
                title = "Hide episode spoilers",
                checked = settings.spoilerShield,
                onChange = viewModel::setSpoilerShield,
            )
                Divider()
            SwitchRow(
                title = "Autoplay trailers",
                checked = settings.autoplay,
                onChange = viewModel::setAutoplay,
            )
                Divider()
            SwitchRow(
                title = "Only on Wi-Fi",
                checked = settings.autoplayOnWifiOnly,
                onChange = viewModel::setAutoplayWifi,
                enabled = settings.autoplay,
            )
                Divider()
            SwitchRow(
                title = "Hero moves by itself",
                checked = settings.heroAutoAdvance,
                onChange = viewModel::setHeroAutoAdvance,
            )
                Divider()
            StepperRow(
                title = "Seconds per slide",
                value = settings.heroSeconds,
                range = 4..30,
                step = 1,
                enabled = settings.heroAutoAdvance,
                onChange = viewModel::setHeroSeconds,
            )
                Divider()
            SwitchRow(
                title = "Countdown to the next episode",
                checked = settings.countdowns,
                onChange = viewModel::setCountdowns,
            )
                Divider()
            SwitchRow(
                title = "Swipe an episode to catch up",
                checked = settings.episodeSwipe,
                onChange = viewModel::setEpisodeSwipe,
            )
                Divider()
            SwitchRow(
                title = "Celebrate a perfect ten",
                checked = settings.confetti,
                onChange = viewModel::setConfetti,
            )
                Divider()
            SwitchRow(
                title = "Shake to pick",
                checked = settings.shakeToPick,
                onChange = viewModel::setShakeToPick,
            )
            }
        }

        item(key = "group_content") {
            SettingsGroup("Content") {
            // Twelve countries do not fit a segmented control - they wrapped a
            // letter at a time ("Indi / a"). A row that names the current one
            // and opens the full list is the shape this choice needs.
            RegionRow(
                selected = settings.region,
                onSelect = viewModel::setRegion,
            )
                Divider()
            SwitchRow(
                title = "Include adult titles",
                checked = settings.mature,
                onChange = viewModel::setMature,
            )
                Divider()
            SwitchRow(
                title = "Blur adult artwork",
                detail = "Covers artwork until tapped",
                checked = settings.matureBlur,
                onChange = viewModel::setMatureBlur,
                enabled = settings.mature,
            )
            }
        }

        item(key = "group_gemini") {
            SettingsGroup("Gemini") {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var status by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        if (viewModel.geminiConfirmed) "Connected" else "Tap to check whether Gemini answers"
                    )
                }
                var checking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                SwitchRow(
                    title = "Gemini features",
                    detail = if (settings.geminiOn) "Answers, picks and explanations across the app" else "Off: nothing of Gemini is shown anywhere",
                    checked = settings.geminiOn,
                    onChange = viewModel::setGeminiOn,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = settings.geminiOn,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                ) {
                Column {
                Divider()
                SwitchRow(
                    title = "Glow round the screen",
                    detail = "While Gemini is working",
                    checked = settings.geminiEdgeGlow,
                    onChange = viewModel::setGeminiEdgeGlow,
                )
                Divider()
                NotifyAction(
                    icon = Icons.Rounded.AutoAwesome,
                    title = if (checking) "Checking Gemini…" else "Gemini status",
                    detail = status,
                ) {
                    if (checking) return@NotifyAction
                    haptics?.play(Haptic.Tap)
                    checking = true
                    scope.launch {
                        val (ok, text) = viewModel.checkGemini()
                        status = text
                        checking = false
                        haptics?.play(if (ok) Haptic.Success else Haptic.Warning)
                    }
                }
                }
                }
            }
        }

        item(key = "group_notify") {
            SettingsGroup("Notify") {
            SwitchRow(
                title = "New episodes",
                detail = "Shows you are watching, morning and evening",
                checked = settings.notifyNewEpisodes,
                onChange = viewModel::setNotifyEpisodes,
            )
                Divider()
            SwitchRow(
                title = "The minute it airs",
                detail = "Where the broadcaster publishes a time",
                checked = settings.notifyAiring,
                onChange = viewModel::setNotifyAiring,
                enabled = settings.notifyNewEpisodes,
            )
                Divider()
            SwitchRow(
                title = "New seasons",
                detail = "A show you have watched comes back",
                checked = settings.notifySeasons,
                onChange = viewModel::setNotifySeasons,
            )
                Divider()
            SwitchRow(
                title = "From your list",
                detail = "A film or series you saved is out",
                checked = settings.notifyReleases,
                onChange = viewModel::setNotifyReleases,
            )
                Divider()
            SwitchRow(
                title = "Your month",
                detail = "Last month in films and series, on the first",
                checked = settings.notifyRecap,
                onChange = viewModel::setNotifyRecap,
            )
                Divider()
            SwitchRow(
                title = "App updates",
                detail = "When a new version is ready",
                checked = settings.notifyUpdates,
                onChange = viewModel::setNotifyUpdates,
            )
                Divider()
            val granted = com.cineverse.app.notify.Notifications.canPost(context)
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { allowed ->
                if (allowed) haptics?.play(Haptic.Success) else haptics?.play(Haptic.Warning)
            }
            if (!granted) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                Divider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple {
                        haptics?.play(Haptic.Tap)
                        com.cineverse.app.notify.WatchWorker.runNow(context)
                        viewModel.say("Checking for new episodes and releases…")
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                        "It also runs by itself, morning and evening",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                    )
                }
            }
                Divider()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            NotifyAction(
                icon = Icons.Rounded.NotificationsActive,
                title = "Send a test notification",
                detail = if (granted) "See how one looks on this phone" else "Allow notifications first",
            ) {
                haptics?.play(Haptic.Tap)
                if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else scope.launch { com.cineverse.app.notify.Notifications.postTest(context) }
            }
                Divider()
            NotifyAction(
                icon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                title = "Sound and style",
                detail = "Each kind has its own channel in Android's settings",
            ) {
                haptics?.play(Haptic.Tap)
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            }
        }

// Asked FOR, not asked at launch.
        //
        // A permission prompt in the first five seconds of an app, before it has
        // shown anything worth being notified about, is the prompt people deny
        // by reflex and never revisit. This one sits under the two toggles it
        // belongs to, so it is requested by someone who has just said they want
        // the thing it enables.

        }
    }
}

/**
 * A row that goes somewhere.
 *
 * Three near-identical copies of this existed with slightly different padding,
 * which is how a settings list ends up looking hand-assembled.
 */
/**
 * One group of settings, as an inset card.
 *
 * Free-floating rows on flat ink gave the page no rhythm at all: thirty
 * switches with nothing to group them, every gap the same size, so the eye had
 * nothing to hold on to. A card per group makes each one a thing you can take
 * in at a glance, and the hairlines between rows do the separating that
 * whitespace was failing to do — which is also how the only settings app
 * anyone finds pleasant has always worked.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = CvTheme.colors
    Column(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 22.dp)) {
        Text(
            title.uppercase(),
            style = KickerStyle,
            color = colors.text3,
            modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
        )
        Column(Modifier.glass(CvShape.XLarge), content = content)
    }
}

/** The hairline between two rows, inset so it does not touch the card edge. */
@Composable
internal fun Divider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(CvTheme.colors.text.copy(alpha = 0.055f))
    )
}

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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            .glass(CvShape.Large)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.cineverse.app.core.ui.OdometerText(value, MaterialTheme.typography.titleLarge, colors.text)
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
            .padding(horizontal = 16.dp, vertical = 13.dp)
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
                .glass(CvShape.Pill),
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
    "SA" to "Saudi Arabia",
    "AE" to "United Arab Emirates",
    "QA" to "Qatar",
    "BH" to "Bahrain",
    "KW" to "Kuwait",
    "OM" to "Oman",
    "LK" to "Sri Lanka",
    "NP" to "Nepal",
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

/** 🇮🇳 from "IN": two regional-indicator letters make a flag on every platform. */
private fun flagOf(code: String): String =
    code.uppercase().filter { it in 'A'..'Z' }.take(2)
        .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
        .joinToString("")

@Composable
private fun RegionRow(selected: String, onSelect: (String) -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val current = REGIONS.firstOrNull { it.first == selected } ?: REGIONS.first()
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple { haptics?.play(Haptic.Tap); open = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Region", style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(
                "Where to watch, and release dates",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
        Text(
            "${flagOf(current.first)}  ${current.second}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.text2,
        )
        Spacer(Modifier.size(6.dp))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos, null,
            tint = colors.text3, modifier = Modifier.size(14.dp),
        )
    }
    if (open) {
        com.cineverse.app.core.ui.CvSheet(onDismiss = { open = false }) {
            Text("Region", style = MaterialTheme.typography.titleLarge, color = colors.text)
            Spacer(Modifier.size(12.dp))
            // Twenty regions are taller than a phone: the list scrolls under
            // the heading rather than pushing the sheet off the screen.
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            for ((code, name) in REGIONS) {
                val active = code == current.first
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CvShape.Medium)
                        .background(if (active) colors.text.copy(alpha = 0.08f) else Color.Transparent)
                        .clickableNoRipple {
                            haptics?.play(Haptic.Select)
                            onSelect(code)
                            open = false
                        }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(flagOf(code), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(14.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) colors.text else colors.text2,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        Icon(
                            Icons.Rounded.Check, "Selected",
                            tint = com.cineverse.app.core.design.Palette.Red2,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glass(CvShape.Pill)
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

@Composable
private fun NotifyAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.text3)
        }
    }
}
