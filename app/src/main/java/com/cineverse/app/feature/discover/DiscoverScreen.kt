package com.cineverse.app.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.PosterRail
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.PullToRefresh
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.nav.Route

/**
 * Discover absorbs five of the website's nav items — Movies, TV Shows,
 * Franchises, Box Office and Releases — because they are all the same verb, and
 * on a phone five tabs that each hold one grid is four tabs too many.
 *
 * The hub is: a row of destinations, a row of moods, Surprise me, and then the
 * curated rails underneath. Nothing here needs the library, so it is the one tab
 * that is fully useful before you have signed in.
 */
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onOpen: (MediaItem) -> Unit,
    onBrowse: (Route) -> Unit,
    onSurprise: () -> Unit,
    modifier: Modifier = Modifier,
    shakeToPick: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var shaken by remember { mutableIntStateOf(0) }

    // The website's shake: on this tab, shaking the phone is Surprise me.
    com.cineverse.app.core.ui.OnShake(shakeToPick) {
        haptics?.play(Haptic.Celebrate)
        shaken++
        onSurprise()
    }

    PullToRefresh(
        refreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "destinations") {
            Column {
                Text(
                    "BROWSE",
                    style = KickerStyle,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(destinations, key = { it.label }) { destination ->
                        DestinationCard(destination) {
                            haptics?.play(Haptic.Select)
                            onBrowse(destination.route)
                        }
                    }
                }
            }
            }

            item(key = "surprise") {
            SurpriseCard(shaken = shaken, shakeHint = shakeToPick) {
                haptics?.play(Haptic.Celebrate)
                onSurprise()
            }
            }

            item(key = "moods") {
            Column {
                SectionHeader("By mood")
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(Mood.entries.toList(), key = { it.name }) { mood ->
                        Box(
                            Modifier
                                .height(96.dp)
                                .width(150.dp)
                                .clip(CvShape.Large)
                                .background(
                                    Brush.linearGradient(listOf(mood.from, mood.to))
                                )
                                .clickableNoRipple {
                                    haptics?.play(Haptic.Select)
                                    onBrowse(mood.route)
                                }
                                .padding(13.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(
                                mood.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            }

            items(state.rails, key = { it.first }) { (id, rail) ->
            PosterRail(
                items = rail.items,
                title = rail.title,
                kicker = rail.kicker,
                onOpen = onOpen,
                onSeeAll = rail.seeAll?.let { route -> { onBrowse(route) } },
                isWatched = { library.isWatched(it.key) },
                isSaved = { library.isSaved(it.key) },
            )
            }
        }
    }
}

private data class Destination(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val route: Route,
)

private val destinations = listOf(
    Destination("Films", Icons.Rounded.Movie, Palette.Red2, Route.Browse("Films", "movie", "popular")),
    Destination("Series", Icons.Rounded.Tv, Palette.Cyan2, Route.Browse("Series", "tv", "popular")),
    Destination("In cinemas", Icons.Rounded.Theaters, Palette.Gold, Route.Browse("In cinemas", "movie", "now_playing")),
    Destination("Coming soon", Icons.Rounded.Upcoming, Palette.Purple2, Route.Browse("Coming soon", "movie", "upcoming")),
    Destination("Top rated", Icons.Rounded.LocalMovies, Palette.Green2, Route.Browse("Top rated", "movie", "top_rated")),
    Destination("Franchises", Icons.Rounded.CollectionsBookmark, Palette.Pink, Route.Franchises),
    Destination("Box office", Icons.Rounded.Paid, Palette.Gold2, Route.BoxOffice),
)

@Composable
private fun DestinationCard(destination: Destination, onClick: () -> Unit) {
    val colors = CvTheme.colors
    Column(
        Modifier
            .width(96.dp)
            .glass(CvShape.Large)
            .clickableNoRipple(onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CvShape.Medium)
                .background(destination.tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, null, tint = destination.tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The website's "Surprise me", which on a phone deserves to be a whole card. */
@Composable
private fun SurpriseCard(shaken: Int, shakeHint: Boolean, onClick: () -> Unit) {
    // A shake makes the card itself shake: a quick damped wobble, so the
    // gesture visibly landed on the thing it triggered.
    val wobble = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(shaken) {
        if (shaken == 0) return@LaunchedEffect
        for (angle in listOf(-5f, 4.5f, -3.5f, 2.5f, -1.5f, 0f)) {
            wobble.animateTo(angle, androidx.compose.animation.core.tween(60))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .graphicsLayer { rotationZ = wobble.value }
            .height(88.dp)
            .clip(CvShape.XLarge)
            .background(
                Brush.linearGradient(listOf(Palette.Red, Color(0xFF7C1D6F)))
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Casino, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Surprise me", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    if (shakeHint) "One title you have not seen, at random — or shake your phone"
                    else "One title, picked at random from everything you have not seen",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xCCFFFFFF),
                    maxLines = 2,
                )
            }
        }
    }
}

/** The website's mood chips, each a discover query with a colour of its own. */
enum class Mood(
    val label: String,
    val from: Color,
    val to: Color,
    val genre: Int,
    val type: MediaType = MediaType.Movie,
) {
    Laugh("Make me laugh", Color(0xFFF59E0B), Color(0xFFDC2626), 35),
    Cry("Break my heart", Color(0xFF3B82F6), Color(0xFF6D28D9), 18),
    Scare("Scare me", Color(0xFF111827), Color(0xFF7F1D1D), 27),
    Think("Make me think", Color(0xFF0E7490), Color(0xFF1E3A8A), 878),
    Thrill("Keep me up", Color(0xFF9A3412), Color(0xFF7C2D12), 53),
    Family("Watch together", Color(0xFF059669), Color(0xFF065F46), 10751);

    val route: Route
        get() = Route.Browse(label, type.wire, genre = genre, sort = "vote_average.desc")
}
