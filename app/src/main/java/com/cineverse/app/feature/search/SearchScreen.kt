package com.cineverse.app.feature.search

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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.cineverse.app.core.ui.CvImage
import com.cineverse.app.core.ui.Img
import com.cineverse.app.core.ui.PosterCard
import com.cineverse.app.core.ui.PosterSkeleton
import com.cineverse.app.core.ui.BottomBarSpace
import com.cineverse.app.core.ui.ScreenPadding
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors = CvTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }

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
                    .clip(CvShape.Pill)
                    .background(colors.glass)
                    .border(1.dp, colors.hairline, CvShape.Pill)
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
            }
        }

        when {
            state.query.isBlank() && state.history.isNotEmpty() -> {
                Column(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
                    Text("RECENT", style = KickerStyle, color = colors.text3)
                    Spacer(Modifier.height(10.dp))
                    for (entry in state.history) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(CvShape.Medium)
                                .clickableNoRipple { viewModel.onQueryChange(entry); viewModel.submit() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                null,
                                tint = colors.text3,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(entry, style = MaterialTheme.typography.bodyMedium, color = colors.text2)
                        }
                    }
                }
            }

            state.loading && state.results.isEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    contentPadding = PaddingValues(ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(9) { PosterSkeleton(width = 118.dp) }
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

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    contentPadding = PaddingValues(
                        start = ScreenPadding, end = ScreenPadding,
                        top = ScreenPadding, bottom = BottomBarSpace,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.results, key = { it.key }) { item ->
                        PosterCard(
                            item = item,
                            onOpen = onOpen,
                            width = 118.dp,
                            watched = library.isWatched(item.key),
                            saved = library.isSaved(item.key),
                            rating = library.ratingOf(item.key),
                        )
                    }
                    if (state.hasMore) {
                        item {
                            LaunchedEffect(state.results.size) { viewModel.loadMore() }
                            PosterSkeleton(width = 118.dp)
                        }
                    }
                }
            }
        }
    }
}
