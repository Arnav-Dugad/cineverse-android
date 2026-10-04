package com.cineverse.app.feature.search

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cineverse.app.feature.sheets.FilterDropdowns
import com.cineverse.app.feature.sheets.SortDropdown
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.KickerStyle
import com.cineverse.app.core.ui.glass
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.posterCellWidth
import com.cineverse.app.core.ui.posterGridCells
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.model.MediaItem

/**
 * Search is a mode, not a place.
 *
 * It opens with the keyboard already up and the cursor in the field, because
 * every single time someone comes here they are about to type. Below it: what
 * they searched before, until the first character arrives.
 *
 * Results come in two waves — a suggestion list while typing, a grid once the
 * query settles — so the screen is never empty and never janks between them.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPerson: (Int) -> Unit = {},
    onTrailer: (key: String, title: String) -> Unit = { _, _ -> },
    onNavigate: (page: String) -> Unit = {},
    /** Opened from a "Search by voice" shortcut: start listening at once. */
    startListening: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val voice = rememberVoiceInput()
    var listening by remember { mutableStateOf(false) }
    fun listen() {
        keyboard?.hide()
        listening = true
        voice.start { heard ->
            // The overlay stays for the answer: the orb turns into it.
            viewModel.ask(heard, spoken = true)
        }
    }
    // Without the microphone permission, the system's own voice dialog still
    // works: it records in the recogniser's app, not this one.
    val systemVoice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { viewModel.ask(it, spoken = true) }
    }
    fun systemListen() {
        runCatching { systemVoice.launch(VoiceInput.intent()) }
    }
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) listen() else systemListen() }
    fun mic() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            !voice.available -> systemListen()
            granted -> listen()
            else -> askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        // Back from a result, the box still holds the question and its answer
        // is on screen; the keyboard would only cover it.
        if (startListening) mic() else if (state.query.isBlank()) focus.requestFocus()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.Open -> onOpen(event.item)
                is SearchEvent.Trailer -> onTrailer(event.key, event.title)
                is SearchEvent.Navigate -> onNavigate(event.page)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.ink)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CvShape.Circle).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .glass(CvShape.Pill)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = colors.text3, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(10.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        com.cineverse.app.core.design.Palette.Red2
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide(); viewModel.submit()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text(
                                "Films, series, people",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text3,
                            )
                        }
                        inner()
                    },
                )
                AnimatedVisibility(state.query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        "Clear",
                        tint = colors.text3,
                        modifier = Modifier
                            .size(19.dp)
                            .clickableNoRipple { viewModel.onQueryChange("") },
                    )
                }
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CvShape.Circle)
                        .clickableNoRipple(::mic),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.Mic,
                        "Search by voice",
                        tint = colors.text2,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        val ask = state.ask
        if (ask != null) {
            AskPane(
                ask = ask,
                library = library,
                onOpen = onOpen,
                onDismiss = viewModel::dismissAsk,
            )
        } else when {
            // An empty box is not an empty page: what you searched for before,
            // then what everyone is watching today.
            state.query.isBlank() -> {
                LazyVerticalGrid(
                    columns = posterGridCells(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = BottomBarSpace,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (state.history.isNotEmpty()) {
                        item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                            RecentSearches(
                                history = state.history,
                                onPick = { entry -> viewModel.onQueryChange(entry); viewModel.submit() },
                                onForget = viewModel::forget,
                                onClear = viewModel::clearHistory,
                            )
                        }
                    }
                    if (state.trending.isNotEmpty()) {
                        item(key = "trending_head", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "TRENDING TODAY",
                                style = KickerStyle,
                                color = colors.text3,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(state.trending, key = { "t_${it.key}" }) { item ->
                            PosterCard(
                                item = item,
                                onOpen = onOpen,
                                width = posterCellWidth(),
                                watched = library.isWatched(item.key),
                                saved = library.isSaved(item.key),
                                rating = library.ratingOf(item.key),
                            )
                        }
                    }
                }
            }

            state.loading && state.results.isEmpty() -> {
                LazyVerticalGrid(
                    columns = posterGridCells(),
                    contentPadding = PaddingValues(ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(9) { PosterSkeleton(width = posterCellWidth()) }
                }
            }

            state.results.isEmpty() && state.query.isNotBlank() && !state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nothing for “${state.query}”",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text2,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Check the spelling, or try a shorter query.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text3,
                        )
                    }
                }
            }

            else -> Column {
                FilterDropdowns(
                    filter = state.filter,
                    genres = state.genres,
                    onChange = viewModel::setFilter,
                    count = if (state.filter.isDefault) "${state.results.size} results" else "${state.shown.size} of ${state.results.size}",
                    showHideWatched = true,
                    sort = { SortDropdown(SearchSorts, state.filter, viewModel::setFilter) },
                )
                if (state.filteredOut) {
                    // A filter that hides everything has to say so, and has to
                    // offer the way out in the same breath. Showing an empty
                    // grid here is how a working search looks broken.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No results match your filters",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text2,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .glass(CvShape.Pill, raised = false)
                                    .clickableNoRipple {
                                        viewModel.setFilter(state.filter.clear())
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "Clear filters",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.text,
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = posterGridCells(),
                        contentPadding = PaddingValues(
                            start = ScreenPadding, end = ScreenPadding,
                            top = 4.dp, bottom = BottomBarSpace,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (state.people.isNotEmpty() && state.filter.isDefault) {
                            item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                                PeopleRow(state.people, onPerson)
                            }
                        }
                        items(state.shown, key = { it.key }) { item ->
                            PosterCard(
                                item = item,
                                onOpen = onOpen,
                                width = posterCellWidth(),
                                watched = library.isWatched(item.key),
                                saved = library.isSaved(item.key),
                                rating = library.ratingOf(item.key),
                            )
                        }
                        if (state.hasMore) {
                            item {
                                LaunchedEffect(state.results.size) { viewModel.loadMore() }
                                PosterSkeleton(width = posterCellWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    if (listening) {
        androidx.activity.compose.BackHandler { voice.cancel(); listening = false }
        VoiceOverlay(
            voice = voice,
            answer = state.ask,
            onRetry = ::listen,
            onClose = { listening = false },
        )
    }
}

@Composable
private fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = CvTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT", style = KickerStyle, color = colors.text3, modifier = Modifier.weight(1f))
            Text(
                "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = colors.text3,
                modifier = Modifier
                    .clip(CvShape.Pill)
                    .clickableNoRipple(onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        for (entry in history) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CvShape.Medium)
                    .clickableNoRipple { onPick(entry) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, null, tint = colors.text3, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(12.dp))
                Text(
                    entry,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text2,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Close, "Forget $entry",
                    tint = colors.text3,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CvShape.Circle)
                        .clickableNoRipple { onForget(entry) }
                        .padding(7.dp),
                )
            }
        }
    }
}

/**
 * The people a search found, as faces. Shown above the posters because a name
 * typed into a search box is usually a person, and the person is the answer.
 */
@Composable
private fun PeopleRow(people: List<com.cineverse.app.data.model.Person>, onPerson: (Int) -> Unit) {
    val colors = CvTheme.colors
    val haptics = com.cineverse.app.core.design.LocalHaptics.current
    Column {
        Text("PEOPLE", style = KickerStyle, color = colors.text3)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(people.size, key = { people[it].id }) { index ->
                val person = people[index]
                Column(
                    Modifier
                        .width(84.dp)
                        .clickableNoRipple {
                            haptics?.play(com.cineverse.app.core.design.Haptic.Tap)
                            onPerson(person.id)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CvShape.Circle)
                            .background(colors.surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (person.profilePath != null) {
                            com.cineverse.app.core.ui.CvImage(
                                com.cineverse.app.core.ui.Img.profile(person.profilePath),
                                person.name,
                                Modifier.matchParentSize(),
                            )
                        } else {
                            Text(
                                person.name.take(1),
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.text3,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    person.job?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.text3, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
