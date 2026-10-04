package com.cineverse.app.feature.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.feature.sheets.FilterDropdowns

/**
 * Everything you own, in three segments: what you mean to watch, what you are in
 * the middle of, and what you have finished.
 *
 * The website spreads these over My List, Continue Watching and Watched. On a
 * phone they are one tab with a segmented control, because they are the same
 * question asked at three points in time.
 */
@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onOpen: (MediaItem) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    shakeToPick: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    var pinTarget by remember { mutableStateOf<Pair<com.cineverse.app.data.model.UserList, PinMode>?>(null) }
    var lockOptions by remember { mutableStateOf<com.cineverse.app.data.model.UserList?>(null) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    var arrangingLists by remember { mutableStateOf(false) }
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current

    if (!signedIn) {
        EmptyState(
            title = "Sign in to keep a list",
            body = "Your list, what you have watched and every episode you tick sync with CineVerse on the web.",
            action = "Sign in" to onSignIn,
            modifier = modifier,
        )
        return
    }

    // What Pick for me chooses from: the list as you are looking at it, filters
    // and all, minus anything already watched.
    // A locked list draws NOTHING of itself - not its titles, not its count,
    // not a pick from it - until the PIN is entered.
    val selectedList = library.lists.firstOrNull { it.id == state.listId }
    val gridStates = remember { mutableMapOf<ListSegment, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val lockedNow = state.segment == ListSegment.Watchlist &&
        selectedList?.hasPin == true && selectedList.id !in unlocked
    val pickable = if (state.segment == ListSegment.Watchlist && !lockedNow) {
        state.items.filterNot { library.isWatched(it.key) }
    } else emptyList()
    com.cineverse.app.core.ui.OnShake(shakeToPick && pickable.size >= 3 && !picking) {
        haptics?.play(Haptic.Celebrate)
        picking = true
    }
    lockOptions?.let { list ->
        LockOptionsSheet(
            list = list,
            unlocked = list.id in unlocked,
            onPick = { mode -> lockOptions = null; if (mode != null) pinTarget = list to mode },
            onLockNow = { viewModel.relock(list); lockOptions = null },
            onDismiss = { lockOptions = null },
        )
    }
    pinTarget?.let { (list, mode) ->
        PinSheet(
            list = list,
            mode = mode,
            verify = { pin -> viewModel.verifyPin(list, pin) },
            onDone = { pin ->
                when (mode) {
                    PinMode.Unlock -> { viewModel.unlock(list); true }
                    PinMode.Set, PinMode.Change -> viewModel.setPin(list, pin)
                    PinMode.Remove -> viewModel.removePin(list)
                }
            },
            onDismiss = { pinTarget = null },
        )
    }

    if (arrangingLists) {
        ArrangeListsSheet(
            lists = library.lists,
            onSave = viewModel::reorderLists,
            onDismiss = { arrangingLists = false },
        )
    }

    if (picking && pickable.isNotEmpty()) {
        com.cineverse.app.feature.pick.PickSheet(
            picks = pickable,
            label = library.lists.firstOrNull { it.id == state.listId }?.name ?: "Your list",
            onOpen = onOpen,
            onDismiss = { picking = false },
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .glass(CvShape.Pill)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (segment in ListSegment.entries) {
                    val active = segment == state.segment
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CvShape.Pill)
                            .background(if (active) colors.text.copy(alpha = 0.14f) else Color.Transparent)
                            .clickableNoRipple {
                                if (!active) haptics?.play(Haptic.Select)
                                viewModel.select(segment)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            segment.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) colors.text else colors.text3,
                        )
                    }
                }
            }
            // Posters or rows.
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(42.dp)
                    .glass(CvShape.Circle)
                    .clickableNoRipple { haptics?.play(Haptic.Tap); viewModel.setRows(!state.rows) },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.Crossfade(state.rows, label = "viewMode") { rows ->
                    androidx.compose.material3.Icon(
                        if (rows) androidx.compose.material.icons.Icons.Rounded.GridView else androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ViewList,
                        if (rows) "Show posters" else "Show rows",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Pick for me: only where there is a list to pick from.
            androidx.compose.animation.AnimatedVisibility(
                visible = pickable.size >= 3,
                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(42.dp)
                        .glass(CvShape.Circle)
                        .clickableNoRipple {
                            haptics?.play(Haptic.Select)
                            picking = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.Casino,
                        "Pick for me",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // A list's showcase already gives its numbers; the summary is for the
        // tabs that have none.
        if (!lockedNow && state.summary.titles > 0 && state.showcase == null) {
            ListSummaryCard(state.summary, state.segment, Modifier.padding(horizontal = ScreenPadding).padding(bottom = 4.dp))
        }

        FilterDropdowns(
            filter = state.filter,
            genres = state.genres,
            onChange = viewModel::setFilter,
            count = when {
                lockedNow -> "Locked"
                state.filter.isDefault && state.mood == null -> "${state.all.size} title${if (state.all.size == 1) "" else "s"}"
                else -> "${state.items.size} of ${state.all.size}"
            },
            showType = state.segment != ListSegment.Watching,
            showHideWatched = state.segment == ListSegment.Watchlist,
            sortActive = state.sort != ListSort.Recent,
            onReset = { viewModel.setFilter(state.filter.clear()); viewModel.setSort(ListSort.Recent); viewModel.setMood(null) },
            sort = {
                com.cineverse.app.core.ui.CvDropdown(
                    "Sort",
                    ListSort.entries.map { it to it.labelFor(state.segment) },
                    state.sort,
                ) { viewModel.setSort(it) }
            },
        )

        // The custom lists, as a row of chips above the grid. Only on the
        // watchlist, where membership means something, and only when there is
        // more than nothing to choose between.
        if (state.segment == ListSegment.Watchlist) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListChip(
                    "Watchlist",
                    state.listId.isBlank() || state.listId == "watchlist",
                    count = library.saved.values.count { it.lists.ifEmpty { listOf("watchlist") }.contains("watchlist") },
                ) {
                    haptics?.play(Haptic.Select); viewModel.selectList("watchlist")
                }
                // The website keeps the watchlist itself as a list too; it has
                // its chip already.
                for (list in library.lists.filter { it.id != "watchlist" }) {
                    ListChip(
                        count = if (list.hasPin && list.id !in unlocked) null else library.saved.values.count { it.lists.contains(list.id) },
                        label = list.name,
                        active = state.listId == list.id,
                        lock = when {
                            !list.hasPin -> null
                            list.id in unlocked -> false
                            else -> true
                        },
                        // Hold a list to set, change or remove its PIN.
                        onLongPress = { haptics?.play(Haptic.Peek); lockOptions = list },
                    ) {
                        haptics?.play(Haptic.Select); viewModel.selectList(list.id)
                    }
                }
                if (library.lists.size >= 2) {
                    ListChip("Arrange", false) { haptics?.play(Haptic.Tap); arrangingLists = true }
                }
            }
        }

        // Gemini's moods, to filter by how you feel.
        if (state.segment == ListSegment.Watchlist && state.moods.isNotEmpty() && !lockedNow) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
                    "Moods by Gemini",
                    tint = com.cineverse.app.core.ui.GeminiColors[1],
                    modifier = Modifier.size(16.dp),
                )
                for ((mood, count) in state.moods) {
                    val on = state.mood == mood
                    val fill by androidx.compose.animation.animateColorAsState(
                        if (on) com.cineverse.app.core.ui.GeminiColors[1].copy(alpha = 0.28f) else colors.text.copy(alpha = 0.05f),
                        label = "mood",
                    )
                    Row(
                        Modifier
                            .clip(CvShape.Pill)
                            .background(fill)
                            .border(1.dp, if (on) com.cineverse.app.core.ui.GeminiColors[1] else colors.hairline, CvShape.Pill)
                            .clickableNoRipple { haptics?.play(Haptic.Select); viewModel.setMood(if (on) null else mood) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${mood.emoji} ${mood.label}", style = MaterialTheme.typography.labelMedium, color = if (on) colors.text else colors.text2)
                        Spacer(Modifier.size(5.dp))
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = colors.text3)
                    }
                }
            }
        }

        val items = state.items
        if (lockedNow && selectedList != null) {
            LockedPanel(selectedList, onUnlock = { pinTarget = selectedList to PinMode.Unlock })
        } else if (state.filteredOut) {
            EmptyState(
                title = "No titles match your filters",
                body = "Loosen one of them, or reset them all.",
                action = "Clear filters" to { viewModel.setFilter(state.filter.clear()); viewModel.setMood(null) },
            )
        } else if (items.isEmpty()) {
            EmptyState(
                title = when (state.segment) {
                    ListSegment.Watchlist -> "Nothing saved yet"
                    ListSegment.Watching -> "Nothing on the go"
                    ListSegment.Watched -> "Nothing watched yet"
                },
                body = when (state.segment) {
                    ListSegment.Watchlist -> "Tap the + on any title and it will be waiting here."
                    ListSegment.Watching -> "Tick an episode and the show turns up here with the next one ready."
                    ListSegment.Watched -> "Mark something watched and it is counted here, and in your stats."
                },
            )
        } else {
            LazyVerticalGrid(
                // Each of the three keeps its own place: Watched should not
                // open half way down because the watchlist was scrolled.
                state = gridStates.getOrPut(state.segment) { androidx.compose.foundation.lazy.grid.LazyGridState() },
                columns = if (state.rows) androidx.compose.foundation.lazy.grid.GridCells.Fixed(1) else posterGridCells(),
                contentPadding = PaddingValues(
                    start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (state.rows) 8.dp else 18.dp),
            ) {
                state.showcase?.takeIf { !lockedNow }?.let { show ->
                    item(key = "showcase_${show.list.id}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        ListShowcaseCard(show, onShuffle = { viewModel.shuffleCover(show.list.id) })
                    }
                }
                // Watched, newest first: grouped under the month it was watched.
                val months = state.segment == ListSegment.Watched && state.sort == ListSort.Recent
                var lastMonth = ""
                val zone = java.time.ZoneId.systemDefault()
                for (item in items) {
                    if (months) {
                        val at = library.watched[item.key]?.lastPlay ?: 0L
                        val month = if (at > 0) java.time.Instant.ofEpochMilli(at).atZone(zone)
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault())) else "Some time ago"
                        if (month != lastMonth) {
                            lastMonth = month
                            item(key = "m_$month", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(month.uppercase(), style = com.cineverse.app.core.design.KickerStyle, color = colors.text3, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    item(key = item.key) {
                        val place = state.places[item.key]
                        if (state.rows) {
                            ListRowCard(
                                item = item,
                                place = place,
                                rating = library.ratingOf(item.key),
                                watched = state.segment != ListSegment.Watching && library.isWatched(item.key),
                                onOpen = onOpen,
                            )
                        } else Column {
                            PosterCard(
                                item = item,
                                onOpen = onOpen,
                                width = posterCellWidth(),
                                // On "Watching" every show is in progress by definition;
                                // a green tick there said "finished", which it is not.
                                watched = state.segment != ListSegment.Watching && library.isWatched(item.key),
                                saved = library.isSaved(item.key),
                                rating = library.ratingOf(item.key),
                                showCaption = place == null,
                            )
                            if (place != null) ProgressCaption(item.title, place, posterCellWidth())
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    val colors = CvTheme.colors
    Box(modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text3,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .clip(CvShape.Pill)
                        .background(com.cineverse.app.core.design.Palette.Red)
                        .clickableNoRipple(action.second)
                        .padding(horizontal = 26.dp, vertical = 13.dp)
                ) {
                    Text(
                        action.first,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** One custom list, as a chip. */
/**
 * One custom list, as a chip. [lock] is null for a list with no PIN, true
 * while it is locked and false once it has been opened this session.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ListChip(
    label: String,
    active: Boolean,
    lock: Boolean? = null,
    count: Int? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = CvTheme.colors
    Row(
        Modifier
            .height(34.dp)
            .clip(CvShape.Pill)
            .background(if (active) colors.text else colors.glass)
            .border(1.dp, if (active) Color.Transparent else colors.hairline, CvShape.Pill)
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onLongClick = onLongPress,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lock != null) {
            androidx.compose.material3.Icon(
                if (lock) androidx.compose.material.icons.Icons.Rounded.Lock
                else androidx.compose.material.icons.Icons.Rounded.LockOpen,
                if (lock) "Locked" else "Unlocked",
                tint = if (active) colors.ink else colors.text3,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.ink else colors.text2,
            maxLines = 1,
        )
        if (count != null) {
            Spacer(Modifier.size(6.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = if (active) colors.ink.copy(alpha = 0.6f) else colors.text3)
        }
    }
}

/**
 * The top of the page: how many titles, films against series, and how long
 * it would take to watch what is left - the hours rolling up when they change
 * - beside a fan of the latest posters.
 */
@Composable
private fun ListSummaryCard(summary: ListSummary, segment: ListSegment, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val hours = summary.minutesLeft / 60
    val shownHours by androidx.compose.animation.core.animateIntAsState(hours, androidx.compose.animation.core.tween(900), label = "hoursLeft")
    Row(
        modifier
            .fillMaxWidth()
            .glass(CvShape.XLarge, strength = 0.6f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when (segment) {
                    ListSegment.Watchlist -> "TO WATCH"
                    ListSegment.Watching -> "IN PROGRESS"
                    ListSegment.Watched -> "WATCHED"
                },
                style = com.cineverse.app.core.design.KickerStyle,
                color = colors.text3,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${summary.titles}", style = MaterialTheme.typography.displaySmall, color = colors.text)
                Spacer(Modifier.size(6.dp))
                Text(if (summary.titles == 1) "title" else "titles", style = MaterialTheme.typography.bodyMedium, color = colors.text3, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                buildString {
                    append("${summary.films} films \u00b7 ${summary.series} series")
                    if (segment != ListSegment.Watched && hours > 0) append(" \u00b7 ${if (shownHours >= 48) "${shownHours / 24} days" else "${shownHours}h"} to watch")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.text2,
            )
        }
        // The fan: three posters, splayed.
        Box(Modifier.size(width = 104.dp, height = 84.dp), contentAlignment = Alignment.CenterEnd) {
            summary.posters.reversed().forEachIndexed { index, path ->
                val spread = summary.posters.size - 1 - index
                val shown = com.cineverse.app.core.ui.rememberArrival(1f, delayMillis = 120 * spread, durationMillis = 520)
                Box(
                    Modifier
                        .graphicsLayer {
                            rotationZ = (spread - 1) * 9f * shown
                            translationX = -(spread * 22).dp.toPx() * shown
                            alpha = shown
                        }
                        .size(width = 54.dp, height = 80.dp)
                        .clip(CvShape.Small)
                        .border(1.dp, colors.hairline, CvShape.Small),
                ) {
                    com.cineverse.app.core.ui.CvImage(com.cineverse.app.core.ui.Img.poster(path), null, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Under a show in progress: its name, the next episode and a thin bar of how far through. */
@Composable
private fun ProgressCaption(title: String, place: ShowPlace, width: androidx.compose.ui.unit.Dp) {
    val colors = CvTheme.colors
    val fill = com.cineverse.app.core.ui.rememberArrival(place.fraction, delayMillis = 150, durationMillis = 800)
    Column(Modifier.width(width).padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CvShape.Pill).background(colors.text.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(3.dp).clip(CvShape.Pill).background(com.cineverse.app.core.design.Palette.Red2))
        }
        Spacer(Modifier.height(5.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(place.next, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1)
    }
}

/** The compact view: one title per row. */
@Composable
private fun ListRowCard(item: MediaItem, place: ShowPlace?, rating: Int, watched: Boolean, onOpen: (MediaItem) -> Unit) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(colors.text.copy(alpha = 0.035f))
            .clickableNoRipple { haptics?.play(Haptic.Tap); onOpen(item) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 52.dp, height = 78.dp).clip(CvShape.Small).background(colors.surface2)) {
            com.cineverse.app.core.ui.CvImage(com.cineverse.app.core.ui.Img.poster(item.posterPath), item.title, Modifier.fillMaxSize())
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    item.year.takeIf { it.isNotBlank() },
                    if (item.type == com.cineverse.app.data.model.MediaType.Tv) "Series" else "Film",
                    item.voteAverage.takeIf { it > 0 }?.let { "\u2605 %.1f".format(it) },
                ).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
            if (place != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CvShape.Pill).background(colors.text.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(place.fraction.coerceIn(0f, 1f)).height(3.dp).clip(CvShape.Pill).background(com.cineverse.app.core.design.Palette.Red2))
                }
                Text(place.next, style = MaterialTheme.typography.labelSmall, color = colors.text3, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (rating > 0) {
            Text("$rating", style = MaterialTheme.typography.titleMedium, color = colors.gold, modifier = Modifier.padding(horizontal = 8.dp))
        } else if (watched) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.CheckCircle, "Watched", tint = colors.green, modifier = Modifier.padding(horizontal = 8.dp).size(20.dp))
        }
    }
}
