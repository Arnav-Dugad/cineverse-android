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
import com.cineverse.app.feature.sheets.FilterBar
import com.cineverse.app.feature.sheets.FilterSheet

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
    var filters by remember { mutableStateOf(false) }
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

        FilterBar(
            filter = state.filter,
            onOpen = { filters = true },
            trailing = {
                Text(
                    when {
                        lockedNow -> "Locked"
                        state.filter.isDefault -> "${state.all.size} title${if (state.all.size == 1) "" else "s"}"
                        else -> "${state.items.size} of ${state.all.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text3,
                )
            },
        )

        // The custom lists, as a row of chips above the grid. Only on the
        // watchlist, where membership means something, and only when there is
        // more than nothing to choose between.
        if (state.segment == ListSegment.Watchlist && library.lists.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListChip("All", state.listId.isBlank()) {
                    haptics?.play(Haptic.Select); viewModel.selectList("")
                }
                for (list in library.lists) {
                    ListChip(
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
            }
        }

        val items = state.items
        if (lockedNow && selectedList != null) {
            LockedPanel(selectedList, onUnlock = { pinTarget = selectedList to PinMode.Unlock })
        } else if (state.filteredOut) {
            EmptyState(
                title = "No titles match your filters",
                body = "Loosen one of them, or reset them all.",
                action = "Clear filters" to { viewModel.setFilter(state.filter.clear()) },
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
                columns = posterGridCells(),
                contentPadding = PaddingValues(
                    start = ScreenPadding, end = ScreenPadding, bottom = BottomBarSpace,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    PosterCard(
                        item = item,
                        onOpen = onOpen,
                        width = posterCellWidth(),
                        // On "Watching" every show is in progress by definition;
                        // a green tick there said "finished", which it is not.
                        watched = state.segment != ListSegment.Watching && library.isWatched(item.key),
                        saved = library.isSaved(item.key),
                        rating = library.ratingOf(item.key),
                    )
                }
            }
        }
    }

    if (filters) {
        FilterSheet(
            filter = state.filter,
            genres = state.genres,
            resultCount = state.items.size,
            showHideWatched = state.segment == ListSegment.Watchlist,
            sorts = ListSorts,
            onChange = viewModel::setFilter,
            onDismiss = { filters = false },
        )
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
    }
}
