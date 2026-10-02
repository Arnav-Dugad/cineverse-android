package com.cineverse.app.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.nav.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A grid of one thing: a catalogue list, a genre, a mood.
 *
 * It pages as you reach the end rather than offering a "load more" button —
 * a button at the bottom of an infinite catalogue is a decision the user should
 * never have to make.
 */
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    title: String,
    onOpen: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val gridState = rememberLazyGridState()

    val atEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - 8
        }
    }
    LaunchedEffect(atEnd, state.items.size) { if (atEnd) viewModel.loadMore() }

    Column(modifier.fillMaxSize().background(colors.ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CvShape.Circle).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
            }
            Spacer(Modifier.size(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(118.dp),
            contentPadding = PaddingValues(
                start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = 120.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(state.items, key = { it.key }) { item ->
                PosterCard(
                    item = item,
                    onOpen = onOpen,
                    width = 118.dp,
                    watched = library.isWatched(item.key),
                    saved = library.isSaved(item.key),
                    rating = library.ratingOf(item.key),
                )
            }
            if (state.loading) {
                items(6) { PosterSkeleton(width = 118.dp) }
            }
        }
    }
}

data class BrowseState(
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
)

class BrowseViewModel(
    private val app: AppContainer,
    private val route: Route.Browse,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    init { loadMore() }

    fun loadMore() {
        val current = _state.value
        if (current.loading || (current.page > 0 && current.page >= current.totalPages)) return
        _state.value = current.copy(loading = true)
        viewModelScope.launch {
            val page = current.page + 1
            val type = MediaType.of(route.type)
            val (items, total) = when {
                route.list.isNotBlank() -> {
                    val fetched = if (type == MediaType.Movie)
                        app.tmdb.movies(route.list, page, app.settings.settings.value.region)
                    else app.tmdb.series(route.list, page)
                    // A curated list does not report its page count the way
                    // discover does, so keep paging while it keeps answering.
                    fetched to (if (fetched.isEmpty()) page else page + 1)
                }
                else -> app.tmdb.discoverPage(
                    type,
                    buildMap {
                        put("sort_by", route.sort)
                        if (route.genre > 0) put("with_genres", route.genre.toString())
                        put("vote_count.gte", "150")
                    },
                    page,
                )
            }
            _state.value = BrowseState(
                items = (_state.value.items + items).distinctBy { it.key },
                page = page,
                totalPages = total,
                loading = false,
            )
        }
    }
}
