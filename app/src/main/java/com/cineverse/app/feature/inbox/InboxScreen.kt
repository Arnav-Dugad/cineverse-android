package com.cineverse.app.feature.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvScreenBar
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.riseIn
import com.cineverse.app.data.inbox.InboxEvent
import com.cineverse.app.data.inbox.InboxKind
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.feature.list.EmptyState

/**
 * Everything worth knowing, in one place - the website's bell. Unread items
 * carry a dot and a brighter face; swipe one away and it stays gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    app: AppContainer,
    onOpen: (MediaItem) -> Unit,
    onYear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val events by app.inbox.events.collectAsStateWithLifecycle()
    val read by app.inbox.readIds.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    var filter by rememberSaveable { mutableStateOf("All") }
    val shown = remember { mutableSetOf<Any>() }
    val filters = listOf("All", "Episodes", "Releases", "Recaps")
    val visible = events.filter { filter == "All" || it.kind.label == filter }

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar("Inbox", onBack, trailing = {
            if (events.any { it.id !in read }) {
                Text(
                    "Mark all read",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text2,
                    modifier = Modifier
                        .clip(CvShape.Pill)
                        .clickableNoRipple { haptics?.play(Haptic.Tap); app.inbox.markAllRead() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        })
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters, key = { it }) { label ->
                CvChip(
                    label,
                    filter == label,
                    { filter = label },
                    count = events.count { (label == "All" || it.kind.label == label) && it.id !in read }.takeIf { it > 0 },
                )
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                title = "Nothing new",
                body = "New episodes, returning seasons, films on your list and your monthly recap arrive here.",
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 6.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible.size, key = { visible[it].id }) { index ->
                val event = visible[index]
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            haptics?.play(Haptic.Drop)
                            app.inbox.dismiss(event.id)
                        }
                        true
                    },
                )
                SwipeToDismissBox(
                    state = dismiss,
                    modifier = Modifier
                        .padding(horizontal = ScreenPadding)
                        .riseIn(event.id, index, shown),
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CvShape.Large)
                                .background(Palette.Red2.copy(alpha = 0.18f))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = Palette.Red2)
                        }
                    },
                ) {
                    InboxRow(event, unread = event.id !in read) {
                        haptics?.play(Haptic.Tap)
                        app.inbox.markRead(event.id)
                        when {
                            event.item != null -> onOpen(event.item)
                            event.kind == InboxKind.Recap -> onYear()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(event: InboxEvent, unread: Boolean, onClick: () -> Unit) {
    val colors = CvTheme.colors
    val face by animateColorAsState(
        if (unread) colors.text.copy(alpha = 0.08f) else colors.text.copy(alpha = 0.03f),
        label = "inboxFace",
    )
    val (icon, tint) = iconFor(event.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CvShape.Large)
            .background(colors.ink)
            .background(face)
            .clickableNoRipple(onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 74.dp, height = 50.dp)
                .clip(CvShape.Small)
                .background(
                    // No artwork (the monthly recap): its own colour, not a grey hole.
                    if (event.art == null) androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.15f)))
                    else androidx.compose.ui.graphics.SolidColor(colors.surface2)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (event.art != null) {
                CvImage(Img.still(event.art), null, Modifier.fillMaxSize())
            } else {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CvShape.Circle)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (unread) colors.text else colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(event.body, style = MaterialTheme.typography.labelMedium, color = colors.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (event.upcoming) "Coming up" else whenLabel(event.at),
                style = MaterialTheme.typography.labelSmall,
                color = if (event.upcoming) colors.cyan else colors.text3.copy(alpha = 0.8f),
            )
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).clip(CvShape.Circle).background(Palette.Red2))
        }
    }
}

private fun iconFor(kind: InboxKind): Pair<ImageVector, Color> = when (kind) {
    InboxKind.Episode -> Icons.Rounded.LiveTv to Palette.Cyan
    InboxKind.Season -> Icons.Rounded.Replay to Palette.Purple
    InboxKind.Release -> Icons.Rounded.Movie to Palette.Red
    InboxKind.Finished -> Icons.Rounded.EmojiEvents to Palette.Gold2
    InboxKind.Recap -> Icons.Rounded.CalendarMonth to Palette.Green
}

private fun whenLabel(at: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    }
}

/** The bell in the top bar, with how many are waiting. */
@Composable
fun InboxBell(count: Int, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pop by androidx.compose.animation.core.animateFloatAsState(
        if (count > 0) 1f else 0f,
        com.cineverse.app.core.design.Motion.lively(),
        label = "bell",
    )
    Box(
        modifier
            .size(42.dp)
            .clip(CvShape.Circle)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.NotificationsNone, if (count > 0) "Inbox, $count new" else "Inbox", tint = color, modifier = Modifier.size(24.dp))
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 5.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .clip(CvShape.Pill)
                    .background(Palette.Red2)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 9) "9+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}
