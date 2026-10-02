package com.cineverse.app.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScoreRow
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.sharedPoster
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.Person
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.ProviderKind
import com.cineverse.app.data.model.Brand
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores

/**
 * Poster, title, every score, and one row of actions.
 *
 * One filled button and four icon buttons: on a phone there is room for exactly
 * one primary action, and on a tracker that action is the trailer — everything
 * else is a toggle you will press a thousand times and should therefore be a
 * target, not a label.
 */
/** Three lines, then "Read more" — and only when the text actually overflows. */
@Composable
fun ExpandableText(text: String, collapsedLines: Int = 3) {
    val colors = CvTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize(Motion.size())) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflows = result.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickableNoRipple { expanded = !expanded },
            )
        }
    }
}

/**
 * Episodes / About / More like this.
 *
 * A segmented control rather than a tab row: three fixed options that fit on one
 * line. Tabs that scroll sideways hide their own options, which on a three-way
 * choice is a thing to avoid.
 *
 * The selection SLIDES. A highlight that teleports between segments tells you
 * which one is active; one that travels tells you which one you came from, and
 * on a control you hit dozens of times an evening that is the difference between
 * a widget and a place. The pill is one layer that moves, not three that
 * recolour — the same reason it can be animated on the compositor for free.
 */
@Composable
fun SegmentedTabs(
    tabs: List<DetailTab>,
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val slide by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = Motion.landing(),
        label = "segment",
    )
    // Swipe the control itself to move between segments. Put on the CONTROL and
    // not on the content below it: a horizontal drag anywhere in a vertically
    // scrolling page of rails would be a gesture fighting three other gestures,
    // and the one place a sideways swipe is unambiguous is the thing that
    // already looks like a row of options.
    var travel by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.ink)
            .padding(horizontal = ScreenPadding, vertical = 12.dp)
            .pointerInput(tabs, selected) {
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragCancel = { travel = 0f },
                    // One segment per gesture, settled on lift. Stepping as the
                    // finger moves means a single brisk swipe crosses the
                    // threshold several times and skips past the tab you wanted.
                    onDragEnd = {
                        val step = when {
                            travel <= -56f -> 1
                            travel >= 56f -> -1
                            else -> 0
                        }
                        travel = 0f
                        if (step != 0) {
                            val next = tabs.getOrNull(index + step)
                            if (next != null) {
                                haptics?.play(Haptic.Select)
                                onSelect(next)
                            } else {
                                haptics?.play(Haptic.Edge)
                            }
                        }
                    },
                ) { _, delta -> travel += delta }
            }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CvShape.Pill)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.Pill)
                .padding(3.dp),
        ) {
            val slot = maxWidth / tabs.size.coerceAtLeast(1)
            Box(
                Modifier
                    .offset(x = slot * slide)
                    .width(slot)
                    .fillMaxHeight()
                    .clip(CvShape.Pill)
                    .background(colors.text.copy(alpha = 0.14f))
            )
            Row(Modifier.fillMaxSize()) {
                for (tab in tabs) {
                    val active = tab == selected
                    // The label weight does not animate with the pill: text that
                    // changes weight mid-slide reflows, and a reflowing label
                    // under a moving highlight looks like a rendering fault.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CvShape.Pill)
                            .clickableNoRipple {
                                if (!active) haptics?.play(Haptic.Select)
                                onSelect(tab)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) colors.text else colors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Episodes ----------

fun LazyListScope.episodesSection(
    state: DetailState,
    progress: ShowProgress?,
    spoilerShield: Boolean,
    swipeToCatchUp: Boolean,
    onSeason: (Int) -> Unit,
    onToggle: (Int, Int) -> Unit,
    onMarkUpTo: (Int, Int) -> Unit,
    onSeasonWatched: (Int, Boolean) -> Unit,
    onHeatmapToggle: () -> Unit,
    onHeatMode: (com.cineverse.app.data.model.HeatMode) -> Unit,
    onNumbers: () -> Unit,
    onOpenEpisode: (Int, Int) -> Unit,
    onAllEpisodes: () -> Unit,
) {
    val detail = state.detail ?: return
    val next = progress?.nextUp()

    item(key = "heatmap") {
        HeatmapPanel(
            heatmap = state.heatmap,
            loading = state.loadingHeatmap,
            expanded = state.heatmapOpen,
            mode = state.heatMode,
            numbers = state.showNumbers,
            onToggle = onHeatmapToggle,
            onMode = onHeatMode,
            onNumbers = onNumbers,
            onOpenEpisode = onOpenEpisode,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }

    item(key = "allEpisodes") {
        AllEpisodesPanel(
            seasons = state.allSeasons,
            seasonCount = detail.seasons.size,
            progress = progress,
            expanded = state.allEpisodesOpen,
            loading = state.loadingHeatmap,
            spoilerShield = spoilerShield,
            onToggle = onAllEpisodes,
            onToggleEpisode = onToggle,
            onMarkUpTo = onMarkUpTo,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }

    item(key = "seasons") {
        SeasonChips(
            detail = detail,
            progress = progress,
            selected = state.season,
            onSelect = onSeason,
            onMarkSeason = onSeasonWatched,
        )
    }

    if (state.loadingEpisodes) {
        items(5, key = { "skel_$it" }) {
            Box(
                Modifier
                    .padding(horizontal = ScreenPadding, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(CvShape.Large)
                    .shimmer()
            )
        }
        return
    }

    items(state.episodes, key = { "ep_${it.season}_${it.number}" }) { episode ->
        EpisodeRow(
            episode = episode,
            watched = progress?.isWatched(episode.season, episode.number) == true,
            isNext = next?.first == episode.season && next.second == episode.number,
            watchedAt = progress?.watchedAt(episode.season, episode.number) ?: 0L,
            spoilerShield = spoilerShield,
            onToggle = { onToggle(episode.season, episode.number) },
            onMarkUpTo = if (swipeToCatchUp) {
                { onMarkUpTo(episode.season, episode.number) }
            } else null,
            onOpen = { onToggle(episode.season, episode.number) },
            modifier = Modifier.padding(horizontal = ScreenPadding - 4.dp),
        )
    }
}

/**
 * The season picker: a chip rail, not a dropdown — one tap instead of two, and
 * every season can show how far through it you are without being opened.
 */
/**
 * The seasons, as cards with their own artwork.
 *
 * A season poster is a real thing TMDB publishes and the app was ignoring it,
 * offering a row of identical grey pills instead. For a show with twelve
 * seasons that is twelve things that look the same, and picking the one you
 * want means reading every label. The artwork makes it a glance.
 *
 * Progress is drawn ON the card as a bar rather than written as "6/10": the
 * number is in the label underneath, and a bar answers "am I nearly done with
 * this one" without being read at all.
 *
 * Counted against what has AIRED, not against the season's eventual length, so
 * a season halfway through its run does not look half abandoned.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SeasonChips(
    detail: TitleDetail,
    progress: ShowProgress?,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMarkSeason: (Int, Boolean) -> Unit,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        items(detail.seasons, key = { it.number }) { season ->
            val active = season.number == selected
            val watched = progress?.watchedIn(season.number) ?: 0
            val aired = progress?.episodeNumbers(season.number)
                ?.count { progress.hasAired(season.number, it) }
                ?.takeIf { it > 0 }
                ?: season.episodeCount
            val complete = aired > 0 && watched >= aired
            val fraction = if (aired > 0) (watched.toFloat() / aired).coerceIn(0f, 1f) else 0f
            val lift by animateFloatAsState(
                targetValue = if (active) 1f else 0.96f,
                animationSpec = Motion.lively(),
                label = "season",
            )

            Column(
                Modifier
                    .width(104.dp)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .combinedClickable(
                        onClick = { haptics?.play(Haptic.Select); onSelect(season.number) },
                        onLongClick = {
                            haptics?.play(Haptic.Peek)
                            onMarkSeason(season.number, !complete)
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(CvShape.Medium)
                        .background(colors.surface2)
                        .border(
                            if (active) 2.dp else 1.dp,
                            if (active) colors.text.copy(alpha = 0.85f) else colors.hairline,
                            CvShape.Medium,
                        )
                ) {
                    CvImage(
                        Img.poster(season.posterPath ?: detail.posterPath),
                        season.name,
                        Modifier.fillMaxSize(),
                    )
                    // A season you have finished is dimmed and ticked, the same
                    // treatment a watched poster gets everywhere else.
                    if (complete) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(colors.ink.copy(alpha = 0.55f))
                        )
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = colors.green,
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                        )
                    }
                    if (fraction > 0f && !complete) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(colors.ink.copy(alpha = 0.6f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)
                                    .background(Palette.Red2)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    if (season.number == 0) "Specials" else "Season ${season.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.text else colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (aired <= 0) "Not aired" else "$watched / $aired",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (complete) colors.green else colors.text3,
                    maxLines = 1,
                )
            }
        }
    }
}

fun LazyListScope.aboutSection(
    detail: TitleDetail,
    onPerson: (Person) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    if (detail.cast.isNotEmpty()) {
        item(key = "cast") { CastRow(detail.cast, onPerson) }
    }
    if (detail.crew.isNotEmpty()) {
        item(key = "crew") { CrewRow(detail.crew, onPerson) }
    }
    if (detail.brands.isNotEmpty()) {
        item(key = "brands") { BrandStrip(detail.brands, Modifier.padding(top = 18.dp)) }
    }
    item(key = "facts") { Facts(detail) }
}

/**
 * The studios and networks behind a title.
 *
 * TMDB publishes a mark for most of them and the mapper was keeping only the
 * name. A row of names reads as a legal notice; the marks read as provenance,
 * which is what the information is for.
 *
 * The hard part is that TMDB holds these logos in BOTH polarities. Most are
 * dark ink drawn for a white page; a good number — Netflix, HBO, Apple — are
 * white, drawn for a dark one. A single treatment cannot serve both: tinting
 * everything to one ink turns the white ones into invisible rectangles on this
 * page, which is exactly what happened, and leaving them untinted hides the
 * dark ones instead.
 *
 * So each mark gets its own PLATE, and the plate is a very soft light wash
 * rather than a white chip. Dark logos read against it the way they were drawn
 * to; white logos keep their own shape and are readable because the wash is
 * lighter than the page but far darker than paper. No logo is recoloured, so
 * none of them is wrong.
 */
@Composable
fun BrandStrip(brands: List<Brand>, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    if (brands.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Made by")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(brands, key = { it.id }) { brand ->
                Box(
                    Modifier
                        .height(54.dp)
                        .widthIn(min = 76.dp, max = 132.dp)
                        .clip(CvShape.Medium)
                        .background(colors.text.copy(alpha = 0.10f))
                        .border(1.dp, colors.hairline, CvShape.Medium)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CvImage(
                        Img.logo(brand.logoPath),
                        brand.name,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        background = Color.Transparent,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            brands.joinToString("  \u00b7  ") { it.name },
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
    }
}

/**
 * Where to watch.
 *
 * Moved OUT of the About tab, where it was two taps and a scroll from the top of
 * the page. "Can I actually watch this?" is the question people open a title
 * page to answer, and it is the one thing on the page that is time-sensitive,
 * so it now sits directly under the actions and is visible whatever tab is
 * selected.
 *
 * Every logo is a BUTTON. On a phone that means the provider's own app opens on
 * a search for this title where it is installed, their site where it is not, and
 * TMDB's JustWatch page if the app has never heard of them — so a tap always
 * lands somewhere. A row of logos that cannot be tapped is a row of stickers.
 *
 * Grouped the way the decision is actually made: what your subscriptions already
 * cover, then free with adverts, then what costs money. Nobody scanning this row
 * wants to rent something they could stream.
 */
@Composable
fun WhereToWatch(detail: TitleDetail, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val context = androidx.compose.ui.platform.LocalContext.current
    if (detail.providers.isEmpty()) return

    // One pass, in the order a viewer cares about, de-duplicated: TMDB lists a
    // provider under both flatrate and ads often enough that a raw render shows
    // Netflix twice.
    val groups = remember(detail.providers) {
        val seen = mutableSetOf<Int>()
        listOf(
            "Included with your subscription" to ProviderKind.Stream,
            "Free" to ProviderKind.Free,
            "Free with adverts" to ProviderKind.Ads,
            "Rent" to ProviderKind.Rent,
            "Buy" to ProviderKind.Buy,
        ).mapNotNull { (label, kind) ->
            val matching = detail.providers.filter { it.kind == kind && seen.add(it.id) }
            if (matching.isEmpty()) null else label to matching
        }
    }
    if (groups.isEmpty()) return

    Column(modifier.padding(top = 20.dp)) {
        SectionHeader("Where to watch")
        Spacer(Modifier.height(10.dp))
        // `group`, not `items`: the destructured name would shadow LazyRow's
        // own `items` and the call below would not resolve.
        for ((label, group) in groups) {
            Text(
                label.uppercase(),
                style = KickerStyle,
                color = colors.text3,
                modifier = Modifier
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(group, key = { it.id }) { provider ->
                    ProviderTile(
                        provider = provider,
                        installed = remember(provider.name) {
                            com.cineverse.app.data.model.Providers.isInstalled(context, provider.name)
                        },
                        onClick = {
                            haptics?.play(Haptic.Tap)
                            com.cineverse.app.data.model.Providers.open(
                                context = context,
                                providerName = provider.name,
                                title = detail.title,
                                regionLink = detail.providerLink,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "Availability from JustWatch, for your region. Tap to open.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text3,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
    }
}

@Composable
private fun ProviderTile(
    provider: com.cineverse.app.data.model.WatchProvider,
    installed: Boolean,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = Motion.snappy(),
        label = "providerPress",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CvShape.Medium)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CvShape.Medium)
                    .border(1.dp, colors.hairline, CvShape.Medium)
            ) {
                CvImage(Img.provider(provider.logoPath), provider.name, Modifier.fillMaxSize())
            }
            // A dot, not a badge: it says the app is on this phone without
            // taking a second line to say it.
            if (installed) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(colors.ink)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colors.green)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            provider.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (installed) colors.text2 else colors.text3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CastRow(cast: List<Person>, onPerson: (Person) -> Unit) {
    val colors = CvTheme.colors
    Column(Modifier.padding(bottom = 18.dp)) {
        SectionHeader("Cast")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast.take(24), key = { it.id }) { person ->
                Column(
                    Modifier
                        .width(84.dp)
                        .clickableNoRipple { onPerson(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(colors.surface2)
                    ) {
                        CvImage(Img.profile(person.profilePath), person.name, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (!person.character.isNullOrBlank()) {
                        Text(
                            person.character,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Who made it, and what they did.
 *
 * Grouped by department and ordered the way a credit block is: the people who
 * decided what the thing would be, then the people who decided what it looked
 * and sounded like, then everyone else. TMDB returns crew in no useful order at
 * all — a key grip can arrive before the director — so an ungrouped list is
 * worse than none.
 *
 * One card per PERSON, not per credit. Somebody who wrote and directed appears
 * once with both jobs, because seeing the same face twice in a row reads as a
 * bug and loses the fact that it is the same person.
 */
@Composable
private fun CrewRow(crew: List<Person>, onPerson: (Person) -> Unit) {
    val colors = CvTheme.colors

    val ordered = remember(crew) {
        val rank = listOf(
            "Director", "Creator", "Writer", "Screenplay", "Story", "Novel", "Teleplay",
            "Producer", "Executive Producer", "Original Music Composer", "Composer",
            "Director of Photography", "Cinematography", "Editor", "Production Design",
            "Costume Design", "Casting",
        )
        crew.filter { !it.job.isNullOrBlank() }
            .groupBy { it.id }
            .map { (_, credits) ->
                credits.first() to credits.mapNotNull { it.job }.distinct()
            }
            .sortedBy { (_, jobs) ->
                jobs.minOfOrNull { job ->
                    rank.indexOfFirst { it.equals(job, true) }.takeIf { it >= 0 } ?: 99
                } ?: 99
            }
            .take(20)
    }
    if (ordered.isEmpty()) return

    Column(Modifier.padding(bottom = 18.dp)) {
        SectionHeader("Crew")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ordered, key = { it.first.id }) { (person, jobs) ->
                Column(
                    Modifier
                        .width(84.dp)
                        .clickableNoRipple { onPerson(person) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(colors.surface2)
                    ) {
                        CvImage(
                            Img.profile(person.profilePath),
                            person.name,
                            Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        jobs.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Facts(detail: TitleDetail) {
    val colors = CvTheme.colors
    val rows = buildList {
        detail.status.takeIf { it.isNotBlank() }?.let { add("Status" to it) }
        detail.director?.name?.let { add((if (detail.isSeries) "Creator" else "Director") to it) }
        detail.networks.firstOrNull()?.let { add("Network" to it) }
        detail.companies.firstOrNull()?.let { add("Studio" to it) }
        detail.countries.firstOrNull()?.let { add("Country" to it) }
        detail.spokenLanguages.firstOrNull()?.let { add("Language" to it) }
        if (detail.isSeries && detail.numberOfEpisodes > 0) {
            add("Episodes" to detail.numberOfEpisodes.toString())
        }
        if (detail.voteCount > 0) add("Votes" to "%,d".format(detail.voteCount))
        if (detail.budget > 0) add("Budget" to money(detail.budget))
        if (detail.revenue > 0) add("Box office" to money(detail.revenue))
    }
    if (rows.isEmpty()) return
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp)) {
        Text("DETAILS", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CvShape.XLarge)
                .background(colors.glass)
                .border(1.dp, colors.hairline, CvShape.XLarge)
        ) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text3,
                        modifier = Modifier.width(104.dp),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index < rows.lastIndex) {
                    Box(
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.hairline)
                    )
                }
            }
        }
    }
}

private fun money(value: Long): String = when {
    value >= 1_000_000_000 -> "$%.2fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "$%.1fM".format(value / 1_000_000.0)
    else -> "$%,d".format(value)
}
