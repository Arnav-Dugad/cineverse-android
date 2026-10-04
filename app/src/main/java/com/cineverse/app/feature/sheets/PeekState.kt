package com.cineverse.app.feature.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cineverse.app.AppContainer
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores
import kotlinx.coroutines.launch

/**
 * The peek, hosted once per screen.
 *
 * Holding this at the screen rather than inside each card matters: a sheet
 * raised from inside a LazyRow item dies the moment that item scrolls out of
 * the composition, which on a rail is almost immediately.
 *
 * It paints from the CACHE first and fills in when the request lands, so a long
 * press on something you have already opened is instant and a long press on
 * something new still shows artwork, a title and a year within a frame.
 */
class PeekHost(private val app: AppContainer) {
    var item by mutableStateOf<MediaItem?>(null)
        private set
    var detail by mutableStateOf<TitleDetail?>(null)
        private set
    var scores by mutableStateOf(Scores.Empty)
        private set

    fun open(next: MediaItem) {
        item = next
        val region = app.settings.settings.value.region
        detail = app.tmdb.cachedDetail(next.id, next.type, region)
        scores = detail?.imdbId?.takeIf { it.isNotBlank() }
            ?.let { app.scores.cached(it, next.type) }
            ?: Scores.Empty
    }

    fun close() {
        item = null
        detail = null
        scores = Scores.Empty
    }

    suspend fun hydrate() {
        val current = item ?: return
        val region = app.settings.settings.value.region
        val full = runCatching { app.tmdb.detail(current.id, current.type, region) }.getOrNull()
            ?: return
        if (item?.key != current.key) return
        detail = full
        val imdbId = full.imdbId.ifBlank { return }
        val fetched = runCatching { app.scores.of(imdbId, current.type) }.getOrNull() ?: return
        if (item?.key == current.key) scores = fetched
    }
}

@Composable
fun rememberPeekHost(app: AppContainer): PeekHost = remember(app) { PeekHost(app) }

/**
 * Draws the peek when one is open, and nothing at all when it is not.
 *
 * The actions write through the same repositories the title page uses, so a
 * save made from a peek and a save made from a page are the same write.
 */
@Composable
fun PeekHostSheet(
    app: AppContainer,
    host: PeekHost,
    onOpen: (MediaItem) -> Unit,
    onRate: (MediaItem) -> Unit,
) {
    val item = host.item ?: return
    val library by app.library.library.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.key) { host.hydrate() }
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val onWifi by com.cineverse.app.core.net.rememberUnmetered()

    PeekSheet(
        item = item,
        detail = host.detail,
        scores = host.scores,
        saved = library.isSaved(item.key),
        watched = library.isWatched(item.key),
        rating = library.ratingOf(item.key),
        // The trailer plays in the peek, muted, as the website's hover preview
        // does - under the same rules as the hero's.
        trailerKey = host.detail?.trailer?.key?.takeIf { settings.autoplay && (!settings.autoplayOnWifiOnly || onWifi) },
        onOpen = { host.close(); onOpen(item) },
        onSave = { scope.launch { app.library.toggleSaved(item, host.detail) } },
        onWatched = {
            scope.launch {
                val nowWatched = app.library.toggleWatched(item, host.detail)
                val detail = host.detail
                if (nowWatched && detail != null && detail.isSeries) {
                    app.episodes.markShowWatched(detail)
                }
            }
        },
        onRate = { host.close(); onRate(item) },
        onDismiss = { host.close() },
    )
}
