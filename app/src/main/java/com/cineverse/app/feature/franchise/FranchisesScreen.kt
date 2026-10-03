package com.cineverse.app.feature.franchise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.tabular
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CountUpText
import com.cineverse.app.core.ui.CvButton
import com.cineverse.app.core.ui.CvChip
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.CvScreenBar
import com.cineverse.app.core.ui.GrowBar
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.riseIn
import com.cineverse.app.core.ui.shimmer
import com.cineverse.app.data.franchise.CollectionInfo
import com.cineverse.app.data.franchise.CollectionPart
import com.cineverse.app.data.franchise.CollectionProgress
import com.cineverse.app.data.franchise.Franchises
import com.cineverse.app.data.franchise.PartState
import com.cineverse.app.data.franchise.formatMinutes
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.feature.list.EmptyState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/**
 * Every film series in your history, in one place — the website's /franchises.
 *
 * Completion was spread across a meter here and a block there, and none of them
 * answered the question somebody opens a tracker for: what have I got left, and
 * how long would it take? This is that screen. Each series opens into its whole
 * running order with what you have seen ticked, what a finish would cost in
 * hours, and which entries you SKIPPED rather than simply not reached yet.
 */
@Composable
fun FranchisesScreen(
    viewModel: FranchisesViewModel,
    onOpen: (MediaItem) -> Unit,
    onCollection: (Int) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val shown = remember { mutableSetOf<Any>() }

    Column(modifier.fillMaxSize().background(colors.ink)) {
        CvScreenBar("Franchises", onBack)

        when {
            !state.signedIn -> EmptyState(
                title = "Sign in to track franchises",
                body = "Completion is worked out from the films you have marked watched.",
                action = "Sign in" to onSignIn,
            )

            state.rows == null -> Column(Modifier.padding(ScreenPadding)) {
                repeat(5) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(CvShape.Large)
                            .shimmer()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            state.rows.isNullOrEmpty() -> EmptyState(
                title = "No film series yet",
                body = "Watch two films from the same collection — two Duns, two Toy Storys — and the series appears here with how far through it you are.",
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = BottomBarSpace),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "hero") {
                    FranchiseHero(state, onChoose = {
                        viewModel.chooseNext()?.let { part ->
                            haptics?.play(Haptic.Celebrate)
                            onOpen(part.asItem())
                        }
                    })
                }

                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(FranchiseFilter.entries.toList(), key = { it.name }) { filter ->
                            CvChip(
                                label = filter.label,
                                active = state.filter == filter,
                                count = state.counts[filter] ?: 0,
                                onClick = { viewModel.setFilter(filter) },
                            )
                        }
                    }
                }

                val visible = state.visible
                if (visible.isEmpty()) {
                    item(key = "none") {
                        Text(
                            state.filter.empty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text3,
                            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 30.dp),
                        )
                    }
                }

                items(visible.size, key = { visible[it].info.id }) { index ->
                    val row = visible[index]
                    FranchiseCard(
                        row = row,
                        expanded = row.info.id in state.expanded,
                        onToggle = {
                            haptics?.play(Haptic.Select)
                            viewModel.toggle(row.info.id)
                        },
                        onOpen = onOpen,
                        onCollection = { onCollection(row.info.id) },
                        modifier = Modifier
                            .padding(horizontal = ScreenPadding)
                            .riseIn(row.info.id, index, shown),
                    )
                }
            }
        }
    }
}

@Composable
private fun FranchiseHero(state: FranchisesState, onChoose: () -> Unit) {
    val colors = CvTheme.colors
    val rows = state.rows.orEmpty()
    val art = rows.firstNotNullOfOrNull { it.info.backdrop }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(CvShape.XLarge)
    ) {
        if (art != null) {
            CvImage(
                Img.backdrop(art), null,
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.35f },
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(colors.ink.copy(alpha = 0.2f), colors.ink.copy(alpha = 0.92f))))
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(CvShape.XLarge, strength = 0.6f)
                .padding(20.dp)
        ) {
            Text("EVERY FILM SERIES IN YOUR HISTORY", style = KickerStyle, color = colors.text3)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroFigure(rows.size.toLong(), if (rows.size == 1) "series" else "series")
                HeroFigure(state.seenParts.toLong(), "of ${state.trackedParts} seen")
                HeroFigure(rows.count { it.progress.complete }.toLong(), "complete")
            }
            if (state.minutesLeft > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "About ${formatMinutes(state.minutesLeft)} to finish everything in progress",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text2,
                )
            }
            if (rows.any { !it.progress.complete && it.progress.nextUp != null }) {
                Spacer(Modifier.height(16.dp))
                CvButton("Choose my next film", onChoose, icon = Icons.Rounded.Casino)
            }
        }
    }
}

@Composable
private fun HeroFigure(value: Long, label: String) {
    val colors = CvTheme.colors
    Column {
        CountUpText(value, style = MaterialTheme.typography.headlineMedium, color = colors.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FranchiseCard(
    row: FranchiseRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val progress = row.progress
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.lively(), label = "chev")
    Box(modifier.fillMaxWidth().clip(CvShape.Large)) {
        // The series' own backdrop, faint, behind the card: every row is a
        // different franchise and should look like one at a glance.
        row.info.backdrop?.let { art ->
            CvImage(
                Img.backdrop(art), null,
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (expanded) 0.22f else 0.12f },
            )
        }
        Column(Modifier.fillMaxWidth().glass(CvShape.Large, strength = 0.8f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple(onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(54.dp)
                        .height(81.dp)
                        .clip(CvShape.Small)
                ) {
                    CvImage(Img.poster(row.info.poster ?: row.ordered.firstNotNullOfOrNull { it.poster }), row.info.name, Modifier.matchParentSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.info.name.removeSuffix(" Collection"),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    GrowBar(
                        progress.fraction,
                        color = if (progress.complete) colors.gold else com.cineverse.app.core.design.Palette.Red2,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        metaLine(row),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${progress.percent}%",
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = if (progress.complete) colors.gold else colors.text,
                    )
                    Icon(
                        Icons.Rounded.ExpandMore, if (expanded) "Collapse" else "Expand",
                        tint = colors.text3,
                        modifier = Modifier.graphicsLayer { rotationZ = turn },
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(Motion.size()) + fadeIn(),
                exit = shrinkVertically(Motion.size()) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                    RunningOrder(row.ordered, progress, row.gaps.map { it.id }.toSet(), onOpen)
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        progress.nextUp?.let { next ->
                            CvButton("Carry on with ${next.title}", { onOpen(next.asItem()) })
                        }
                        CvButton("Open the collection", onCollection, primary = false)
                    }
                }
            }
        }
    }
}

private fun metaLine(row: FranchiseRow): String = buildList {
    add(row.progress.label)
    if (row.progress.upcoming > 0) add("${row.progress.upcoming} still to come")
    if (row.gaps.isNotEmpty()) add("${row.gaps.size} skipped")
    if (row.minutesLeft > 0) add("about ${formatMinutes(row.minutesLeft)} left")
}.joinToString(" · ")

/**
 * The whole series in order, as a timeline: a numbered stop per film, filled
 * where you have been. Shared with the collection screen.
 */
@Composable
fun RunningOrder(
    ordered: List<CollectionPart>,
    progress: CollectionProgress,
    gapIds: Set<Int>,
    onOpen: (MediaItem) -> Unit,
) {
    val colors = CvTheme.colors
    val today = remember { LocalDate.now() }
    Column {
        ordered.forEachIndexed { index, part ->
            val seen = part.id in progress.seenIds
            val state = Franchises.stateOf(part, today, progress.seenIds)
            val skipped = part.id in gapIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CvShape.Medium)
                    .clickableNoRipple { onOpen(part.asItem()) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CvShape.Circle)
                        .background(
                            when {
                                seen -> colors.green
                                skipped -> colors.gold.copy(alpha = 0.22f)
                                else -> colors.text.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        seen -> Icon(Icons.Rounded.Check, "Watched", tint = Color.White, modifier = Modifier.size(16.dp))
                        state == PartState.Upcoming -> Icon(Icons.Rounded.HourglassTop, "Not out yet", tint = colors.text3, modifier = Modifier.size(14.dp))
                        else -> Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = if (skipped) colors.gold else colors.text2,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .width(34.dp)
                        .height(51.dp)
                        .clip(CvShape.Tiny)
                ) {
                    CvImage(Img.tiny(part.poster), part.title, Modifier.matchParentSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        part.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (seen) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (seen) colors.text2 else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${part.year.ifBlank { "Undated" }} · " + when {
                            state == PartState.Upcoming -> "Not out yet"
                            state == PartState.Unknown -> "Release date unknown"
                            seen -> "Watched"
                            skipped -> "Skipped"
                            else -> "Not seen"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (skipped) colors.gold else colors.text3,
                    )
                }
            }
        }
    }
}

// ---------- state ----------

enum class FranchiseFilter(val label: String, val empty: String) {
    Progress("In progress", "Nothing in progress — every series you started, you finished."),
    Near("Almost there", "Nothing is within two films of completion yet."),
    Gaps("With gaps", "No gaps. Everything you have started, you have watched in order."),
    Complete("Complete", "No series finished yet — the first one is usually closer than it looks."),
    All("Everything", "No film series in your history yet."),
}

data class FranchiseRow(
    val info: CollectionInfo,
    val progress: CollectionProgress,
    val ordered: List<CollectionPart>,
    val gaps: List<CollectionPart>,
    val minutesLeft: Int,
)

data class FranchisesState(
    val signedIn: Boolean = true,
    /** Null while the collections are being resolved. */
    val rows: List<FranchiseRow>? = null,
    val filter: FranchiseFilter = FranchiseFilter.Progress,
    val expanded: Set<Int> = emptySet(),
) {
    fun rowsFor(filter: FranchiseFilter): List<FranchiseRow> {
        val all = rows.orEmpty()
        val open = all.filter { !it.progress.complete }
        return when (filter) {
            FranchiseFilter.Progress -> open
            FranchiseFilter.Near -> open.filter { it.progress.unseen.size <= 2 }
            FranchiseFilter.Gaps -> open.filter { it.gaps.isNotEmpty() }
            FranchiseFilter.Complete -> all.filter { it.progress.complete }
            FranchiseFilter.All -> all
        }.sortedWith(
            // Closest to done first, then fewest films left — the ones actually finishable.
            compareByDescending<FranchiseRow> { it.progress.fraction }
                .thenBy { it.progress.unseen.size }
                .thenBy { it.info.name }
        )
    }

    val visible: List<FranchiseRow> get() = rowsFor(filter)
    val counts: Map<FranchiseFilter, Int> get() = FranchiseFilter.entries.associateWith { rowsFor(it).size }
    val seenParts: Int get() = rows.orEmpty().sumOf { it.progress.seen }
    val trackedParts: Int get() = rows.orEmpty().sumOf { it.progress.released }
    val minutesLeft: Int get() = rows.orEmpty().filter { !it.progress.complete }.sumOf { it.minutesLeft }
}

class FranchisesViewModel(private val app: AppContainer) : ViewModel() {

    private val rows = MutableStateFlow<List<FranchiseRow>?>(null)
    private val filter = MutableStateFlow(FranchiseFilter.Progress)
    private val expanded = MutableStateFlow<Set<Int>>(emptySet())

    val state: StateFlow<FranchisesState> = combine(
        rows, filter, expanded, app.auth.uid,
    ) { rows, filter, expanded, uid ->
        FranchisesState(signedIn = uid != null, rows = rows, filter = filter, expanded = expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FranchisesState())

    init {
        viewModelScope.launch {
            app.library.library
                .filter { it.loaded }
                // Only what completion depends on: a rating changing must not
                // re-resolve every collection.
                .map { library ->
                    Triple(
                        Franchises.watchedCollections(library).map { it.id },
                        Franchises.watchedFilmIds(library),
                        Franchises.runtimes(library),
                    )
                }
                .distinctUntilChanged()
                .collectLatest { (ids, watched, runtimes) ->
                    rows.value = resolve(ids, watched, runtimes)
                }
        }
    }

    private suspend fun resolve(ids: List<Int>, watched: Set<Int>, runtimes: Map<Int, Int>): List<FranchiseRow> {
        val gate = Semaphore(5)
        val infos = coroutineScope {
            ids.map { id -> async { gate.withPermit { app.tmdb.collectionInfo(id) } } }.awaitAll()
        }
        return infos.filterNotNull().mapNotNull { info ->
            val progress = Franchises.progress(info.parts, watched)
            // A "collection" of one released film is not a franchise.
            if (progress.released < 2) return@mapNotNull null
            val ordered = Franchises.releaseOrdered(info.parts)
            FranchiseRow(
                info = info,
                progress = progress,
                ordered = ordered,
                gaps = Franchises.gaps(ordered, progress.seenIds),
                minutesLeft = Franchises.remainingMinutes(progress, runtimes),
            )
        }
    }

    fun setFilter(value: FranchiseFilter) { filter.value = value }

    fun toggle(id: Int) = expanded.update { if (id in it) it - id else it + id }

    /** One unfinished series at random, and the film it is waiting on. */
    fun chooseNext(): CollectionPart? =
        rows.value.orEmpty()
            .filter { !it.progress.complete }
            .mapNotNull { it.progress.nextUp }
            .randomOrNull()
}
