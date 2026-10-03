package com.cineverse.app.feature.stats

import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloat
import com.cineverse.app.core.ui.clickableNoRipple
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.design.Motion
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.CountUpString
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.data.prefs.StatsSection
import com.cineverse.app.core.ui.SectionHeader
import com.cineverse.app.feature.list.EmptyState

/**
 * Stats, as a vertical story rather than a wall.
 *
 * The website's stats page is the most information-dense thing in CineVerse and
 * the user said it had become confusing. On a phone there is no room to be
 * confusing, which is a useful constraint: six figures, then the last twelve
 * weeks of viewing, then what you actually watch. One idea per screenful.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onYear: () -> Unit = {},
    onCollection: (Int) -> Unit = {},
    onFranchises: () -> Unit = {},
    onPerson: (Int) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folded by viewModel.sections.collectAsStateWithLifecycle()
    val castHours by viewModel.castHours.collectAsStateWithLifecycle()
    val colors = CvTheme.colors

    if (state.loaded && !state.signedIn) {
        EmptyState(
            title = "Sign in to see your numbers",
            body = "Hours watched, streaks, the shows you are in the middle of — all of it comes from what you track.",
            action = "Sign in" to onSignIn,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp, bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                Text("YOUR VIEWING", style = KickerStyle, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                Text(
                    hoursLine(state.totalMinutes),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.text,
                )
                Text(
                    "across everything you have tracked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text3,
                )
            }
        }

        item(key = "year") {
            YearCard(onYear, Modifier.padding(horizontal = ScreenPadding))
        }

        item(key = "figures") {
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                state.figures.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (figure in pair) {
                            FigureCard(figure, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.diary.any { it.count > 0 }) {
            item(key = "diary") {
                Column {
                    SectionHeader("The last twelve weeks")
                    Spacer(Modifier.height(12.dp))
                    DiaryGrid(state.diary, Modifier.padding(horizontal = ScreenPadding))
                }
            }
        }

        if (state.months.any { it.second > 0 }) {
            item(key = "months") {
                Column {
                    SectionHeader("By month")
                    Spacer(Modifier.height(16.dp))
                    MonthBars(state.months, Modifier.padding(horizontal = ScreenPadding))
                }
            }
        }

        if (state.genres.isNotEmpty()) {
            item(key = "genres") {
                Column {
                    SectionHeader("What you watch")
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = ScreenPadding)) {
                        for (slice in state.genres) GenreBar(slice)
                    }
                }
            }
        }

        // The deep panels, in the website's order and folding the same way.
        //
        // Collapsed state lives on `users/{uid}.statsSections`, exactly where
        // the website keeps it, so a page folded on the laptop opens folded
        // here. Default open, so an account that has never touched it sees no
        // change at all.
        val deep = state.deep
        val fold: (String) -> Unit = viewModel::toggleSection

        if (state.pattern.isNotBlank()) item(key = "pattern") {
            PatternCard(state.pattern, Modifier.padding(horizontal = ScreenPadding))
        }

        castHours?.takeIf { it.people.isNotEmpty() }?.let { cast ->
            item(key = "cast") {
                CollapsiblePanel(
                    id = "castHours",
                    kicker = "Who you watch",
                    title = "Time with the cast",
                    collapsed = "castHours" in folded,
                    onToggle = fold,
                    help = "Worked out from the episodes you have ticked on your twenty most-watched " +
                        "live-action shows; voice casts of animation are left out. TMDB does not say " +
                        "which episodes each regular is in, so these are estimates.",
                ) { CastHoursBody(cast, onPerson) }
            }
        }

        if (deep.tv.tracked > 0) item(key = "tv") {
            CollapsiblePanel(
                id = StatsSection.TV,
                kicker = "Episode intelligence",
                title = "TV tracker",
                collapsed = StatsSection.TV in folded,
                onToggle = fold,
                help = "Counted from the episodes you have ticked, against what " +
                    "has actually AIRED rather than a season's eventual length. " +
                    "A show still running does not look half abandoned.",
            ) { TvPanelBody(deep.tv) }
        }

        if (deep.rating.any) item(key = "critic") {
            CollapsiblePanel(
                id = StatsSection.CRITIC,
                kicker = "Your critical voice",
                title = "Rating intelligence",
                collapsed = StatsSection.CRITIC in folded,
                onToggle = fold,
                help = "Your own 1-10 scores, never TMDB's. The distribution is " +
                    "drawn against your commonest score rather than against ten, " +
                    "or a library where nothing is rated 1 would show nine empty bars.",
            ) {
                RatingPanelBody(deep.rating)
                if (deep.library.watched > 0) {
                    Spacer(Modifier.height(20.dp))
                    LibraryPanelBody(deep.library)
                }
            }
        }

        if (deep.taste.decades.isNotEmpty()) item(key = "taste") {
            CollapsiblePanel(
                id = StatsSection.TASTE,
                kicker = "Across everything you keep",
                title = "Taste map",
                collapsed = StatsSection.TASTE in folded,
                onToggle = fold,
                help = "Decades, languages and countries across everything marked " +
                    "watched. Shares are of the titles shown, not of your whole library.",
            ) { TastePanelBody(deep.taste) }
        }

        if (deep.rewatches.isNotEmpty()) item(key = "rewatch") {
            CollapsiblePanel(
                id = StatsSection.REWATCH,
                kicker = "What you go back to",
                title = "Rewatches",
                collapsed = StatsSection.REWATCH in folded,
                onToggle = fold,
                help = "Only shows with a season played more than once. The hours " +
                    "are the EXTRA viewings; the first time through is already in " +
                    "your total.",
            ) { RewatchPanelBody(deep.rewatches) }
        }

        if (deep.shifts.size >= 2) item(key = "evolution") {
            CollapsiblePanel(
                id = StatsSection.EVOLUTION,
                kicker = "Month by month",
                title = "Taste changes",
                collapsed = StatsSection.EVOLUTION in folded,
                onToggle = fold,
                help = "Your leading genre and language in each of your six most " +
                    "recent active months. Months with nothing in them are skipped.",
            ) { ShiftPanelBody(deep.shifts) }
        }

        if (deep.directors.isNotEmpty()) item(key = "directors") {
            CollapsiblePanel(
                id = StatsSection.DIRECTORS,
                kicker = "Who you watch",
                title = "Directors you return to",
                collapsed = StatsSection.DIRECTORS in folded,
                onToggle = fold,
                help = "Directors with at least two films in your watched list, " +
                    "from the credits stored when you marked each one.",
            ) { DirectorPanelBody(deep.directors) }
        }

        if (deep.franchises.isNotEmpty()) item(key = "franchises") {
            CollapsiblePanel(
                id = StatsSection.FRANCHISES,
                kicker = "Series you follow",
                title = "Franchises",
                collapsed = StatsSection.FRANCHISES in folded,
                onToggle = fold,
                help = "Collections with two or more films watched. TMDB decides " +
                    "what counts as a collection.",
            ) {
                Column {
                    FranchisePanelBody(deep.franchises) { onCollection(it.id) }
                    Spacer(Modifier.height(14.dp))
                    com.cineverse.app.core.ui.CvButton(
                        "Every franchise, and what is left",
                        onFranchises,
                        primary = false,
                    )
                }
            }
        }

        if (deep.health.isNotEmpty()) item(key = "health") {
            CollapsiblePanel(
                id = StatsSection.HEALTH,
                kicker = "Quality control",
                title = "Collection health",
                collapsed = StatsSection.HEALTH in folded,
                onToggle = fold,
                help = "What is missing from the documents your library is made of. " +
                    "The bars show what is PRESENT, because 92% complete is the " +
                    "same fact told the useful way round.",
            ) { HealthPanelBody(deep.health) }
        }

        if (deep.trophies.isNotEmpty()) item(key = "achievements") {
            CollapsiblePanel(
                id = StatsSection.ACHIEVEMENTS,
                kicker = "Milestones",
                title = "${deep.trophies.count { it.earned }} of ${deep.trophies.size} earned",
                collapsed = StatsSection.ACHIEVEMENTS in folded,
                onToggle = fold,
                help = "Every one is derived from your account rather than awarded " +
                    "for opening the app, and each shows how far away it is even " +
                    "before it is earned.",
            ) { TrophyPanelBody(deep.trophies) }
        }
    }
}

private fun hoursLine(minutes: Int): String {
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 2 -> "$days days"
        hours >= 1 -> "$hours hours"
        else -> "$minutes minutes"
    }
}

@Composable
private fun FigureCard(figure: Figure, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Column(
        modifier
            .glass(CvShape.XLarge)
            .padding(16.dp)
    ) {
        Text(
            figure.label.uppercase(),
            style = KickerStyle,
            color = colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        CountUpString(figure.value, style = MaterialTheme.typography.headlineMedium, color = colors.text)
        if (figure.detail.isNotBlank()) {
            Text(
                figure.detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
                maxLines = 1,
            )
        }
    }
}

/**
 * Twelve weeks of days, GitHub-style — the densest honest way to show "when do
 * I actually watch things" in the width of a phone.
 */
@Composable
private fun DiaryGrid(days: List<DiaryDay>, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val busiest = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Column(modifier) {
        // Twelve columns of seven, filled down then across, so each column is a
        // week and the grid reads left-to-right like a calendar.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.chunked(7).forEach { week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    week.forEach { day ->
                        val level = if (day.count == 0) 0f else
                            (0.25f + 0.75f * (day.count.toFloat() / busiest)).coerceIn(0.25f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(CvShape.Tiny)
                                .background(
                                    if (day.count == 0) colors.glassStrong
                                    else colors.green.copy(alpha = level)
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.text3)
            Spacer(Modifier.width(6.dp))
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(11.dp)
                        .clip(CvShape.Tiny)
                        .background(colors.green.copy(alpha = level))
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = colors.text3)
        }
    }
}

@Composable
private fun MonthBars(months: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    val peak = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for ((label, count) in months) {
            // The bars grow when the section scrolls in — the website's count-up,
            // which is the one piece of decoration that is also information: you
            // can see the shape arrive.
            val grown by animateFloatAsState(
                targetValue = count.toFloat() / peak,
                animationSpec = Motion.gentle(),
                label = "bar",
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (count > 0) {
                    // A bar is narrow; "2335" wrapped onto two lines under it.
                    Text(
                        if (count >= 1000) "%.1fk".format(java.util.Locale.US, count / 1000f) else "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((96 * (if (CvTheme.reducedMotion) count.toFloat() / peak else grown))
                            .coerceAtLeast(if (count > 0) 4f else 2f).dp)
                        .clip(CvShape.Tiny)
                        .background(if (count > 0) Palette.Red2 else colors.glassStrong)
                )
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.text3)
            }
        }
    }
}

@Composable
private fun GenreBar(slice: GenreSlice) {
    val colors = CvTheme.colors
    val grown by animateFloatAsState(
        targetValue = slice.share,
        animationSpec = Motion.gentle(),
        label = "genre",
    )
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                slice.name,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${slice.count}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CvShape.Pill)
                .background(colors.glassStrong)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (CvTheme.reducedMotion) slice.share else grown)
                    .height(6.dp)
                    .clip(CvShape.Pill)
                    .background(Palette.Red2)
            )
        }
    }
}

/**
 * The door to Your Year. A light sweeps across it every few seconds — the one
 * card on the page that leads somewhere rather than reporting something, and
 * the sweep is how it says so without a button.
 */
@Composable
private fun YearCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val year = java.time.LocalDate.now().year
    val reduced = CvTheme.reducedMotion
    val sweep by androidx.compose.animation.core.rememberInfiniteTransition(label = "yearSweep").animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(3_600, delayMillis = 1_400, easing = Motion.Standard),
        ),
        label = "sweep",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(CvShape.XLarge)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Palette.Red, Color(0xFF7C1D6F), Color(0xFF312E81)),
                )
            )
            .drawWithContent {
                drawContent()
                if (!reduced) {
                    val x = size.width * sweep
                    drawRect(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(x - 120f, 0f),
                            end = androidx.compose.ui.geometry.Offset(x + 120f, size.height),
                        )
                    )
                }
            }
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR YEAR", style = KickerStyle, color = Color.White.copy(alpha = 0.75f))
                Text(
                    "$year in films and series",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
            Text(
                "$year",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.22f),
            )
        }
    }
}

/** When you watch, in a sentence - the website's viewing patterns. */
@Composable
private fun PatternCard(text: String, modifier: Modifier = Modifier) {
    val colors = CvTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .glass(CvShape.XLarge, strength = 0.7f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.Schedule, null,
            tint = colors.cyan, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("WHEN YOU WATCH", style = KickerStyle, color = colors.text3)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
}

@Composable
private fun CastHoursBody(cast: com.cineverse.app.data.cast.CastHours, onPerson: (Int) -> Unit) {
    val colors = CvTheme.colors
    val top = (cast.people.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // A milestone crossed since you last looked gets its moment.
        cast.crossed.firstOrNull()?.let { (person, hours) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CvShape.Large)
                    .background(colors.gold.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.EmojiEvents, null,
                    tint = colors.gold, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "You've now watched $hours hours of ${person.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text,
                )
            }
        }
        cast.people.forEachIndexed { index, person ->
            Row(
                Modifier.fillMaxWidth().clip(CvShape.Medium).clickableNoRipple { onPerson(person.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CvShape.Circle).background(colors.surface2)) {
                    com.cineverse.app.core.ui.CvImage(
                        com.cineverse.app.core.ui.Img.profile(person.profile), person.name, Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            person.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "≈ ${person.hours}h",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index == 0) colors.gold else colors.text2,
                        )
                    }
                    Text(
                        person.shows.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    com.cineverse.app.core.ui.GrowBar(
                        person.minutes.toFloat() / top,
                        color = if (index == 0) colors.gold else colors.cyan,
                        height = 4.dp,
                        delayMillis = index * 60,
                    )
                }
            }
        }
    }
}
