package com.cineverse.app.feature.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.ShowProgress
import com.cineverse.app.data.model.TitleDetail
import com.cineverse.app.data.scores.Scores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which of the three segments the page is showing. */
enum class DetailTab(val label: String) { Episodes("Episodes"), About("About"), More("More like this") }

@Immutable
data class DetailState(
    val detail: TitleDetail? = null,
    val scores: Scores = Scores.Empty,
    val loading: Boolean = true,
    val error: String? = null,
    val tab: DetailTab = DetailTab.About,
    val season: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val loadingEpisodes: Boolean = false,
    /** Season -> every episode, once the heatmap has asked for them. */
    val allSeasons: Map<Int, List<Episode>> = emptyMap(),
    val heatmapOpen: Boolean = false,
    val showNumbers: Boolean = false,
    val undo: UndoMark? = null,
)

/** What the snackbar offers to put back. */
@Immutable
data class UndoMark(val label: String, val season: Int, val episodes: List<Int>)

class DetailViewModel(
    private val app: AppContainer,
    private val id: Int,
    private val type: MediaType,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library
    val progressFlow: StateFlow<Map<Int, ShowProgress>> = app.episodes.progress

    val progress: ShowProgress? get() = app.episodes.of(id)

    init {
        // Paint from the cache first if we have been here before, so coming back
        // to a title is instant rather than a spinner over a page you just read.
        app.tmdb.cachedDetail(id, type, app.settings.settings.value.region)?.let { cached ->
            _state.value = _state.value.copy(
                detail = cached,
                loading = false,
                tab = if (cached.isSeries) DetailTab.Episodes else DetailTab.About,
            )
        }
        load()
    }

    fun load() = viewModelScope.launch {
        val region = app.settings.settings.value.region
        runCatching { app.tmdb.detail(id, type, region) }
            .onSuccess { detail ->
                val opening = _state.value.season.takeIf { it > 1 }
                    ?: firstUnfinishedSeason(detail)
                _state.value = _state.value.copy(
                    detail = detail,
                    loading = false,
                    error = null,
                    tab = if (_state.value.detail == null) {
                        if (detail.isSeries) DetailTab.Episodes else DetailTab.About
                    } else _state.value.tab,
                    season = opening,
                )
                if (detail.isSeries) loadSeason(opening)
                loadScores(detail)
            }
            .onFailure { error ->
                if (_state.value.detail == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.message?.takeIf { it.isNotBlank() }
                            ?: "Could not load this title.",
                    )
                }
            }
    }

    /**
     * Open on the season you are actually in, not season one. Someone four
     * seasons into a show does not want to scroll past three they finished.
     */
    private fun firstUnfinishedSeason(detail: TitleDetail): Int {
        if (!detail.isSeries) return 1
        val show = app.episodes.of(id) ?: return detail.seasons.firstOrNull()?.number ?: 1
        show.nextUp()?.let { return it.first }
        return detail.seasons.lastOrNull()?.number ?: 1
    }

    fun selectTab(tab: DetailTab) { _state.value = _state.value.copy(tab = tab) }

    fun selectSeason(season: Int) {
        if (season == _state.value.season && _state.value.episodes.isNotEmpty()) return
        _state.value = _state.value.copy(season = season, episodes = emptyList())
        loadSeason(season)
    }

    private fun loadSeason(season: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(loadingEpisodes = true)
        val episodes = app.tmdb.season(id, season)
        _state.value = _state.value.copy(
            episodes = episodes,
            loadingEpisodes = false,
            allSeasons = _state.value.allSeasons + (season to episodes),
        )
    }

    private fun loadScores(detail: TitleDetail) = viewModelScope.launch {
        val imdbId = detail.imdbId.ifBlank { app.tmdb.imdbId(id, type) }
        if (imdbId.isBlank()) return@launch
        app.scores.cached(imdbId, type)?.let {
            _state.value = _state.value.copy(scores = it)
            return@launch
        }
        val scores = app.scores.of(imdbId, type)
        _state.value = _state.value.copy(scores = scores)
    }

    // ---------- the heatmap ----------

    fun toggleHeatmap() {
        val open = !_state.value.heatmapOpen
        _state.value = _state.value.copy(heatmapOpen = open)
        if (open) loadAllSeasons()
    }

    fun toggleNumbers() {
        _state.value = _state.value.copy(showNumbers = !_state.value.showNumbers)
    }

    private fun loadAllSeasons() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val wanted = detail.seasons.map { it.number }
        if (_state.value.allSeasons.keys.containsAll(wanted)) return@launch
        val all = app.tmdb.allSeasons(id, wanted)
        _state.value = _state.value.copy(allSeasons = _state.value.allSeasons + all)
    }

    // ---------- marks ----------

    fun toggleEpisode(season: Int, episode: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.episodes.toggleEpisode(detail, season, episode)
    }

    fun markUpTo(season: Int, episode: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val before = app.episodes.of(id)
        app.episodes.markUpTo(detail, season, episode)
        val after = app.episodes.of(id)
        val added = (after?.seasons?.get(season).orEmpty() - before?.seasons?.get(season).orEmpty().toSet())
        if (added.isNotEmpty()) {
            _state.value = _state.value.copy(
                undo = UndoMark("${added.size} episode${if (added.size == 1) "" else "s"} marked", season, added)
            )
        }
    }

    fun setSeasonWatched(season: Int, watched: Boolean) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.episodes.setSeasonWatched(detail, season, watched)
    }

    fun undo() = viewModelScope.launch {
        val mark = _state.value.undo ?: return@launch
        val detail = _state.value.detail ?: return@launch
        for (episode in mark.episodes) app.episodes.toggleEpisode(detail, mark.season, episode)
        _state.value = _state.value.copy(undo = null)
    }

    fun clearUndo() { _state.value = _state.value.copy(undo = null) }

    // ---------- the action row ----------

    fun toggleSaved() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.library.toggleSaved(detail.asItem(), detail)
    }

    fun toggleWatched() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val nowWatched = app.library.toggleWatched(detail.asItem(), detail)
        // Marking a whole series watched ticks every episode too, so the two
        // never disagree — the same rule the website follows.
        if (detail.isSeries && nowWatched) app.episodes.markShowWatched(detail)
    }

    fun setRating(value: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.library.setRating(detail.key, value)
    }
}
