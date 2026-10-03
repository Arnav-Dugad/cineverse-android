package com.cineverse.app.feature.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.Episode
import com.cineverse.app.data.model.HeatMode
import com.cineverse.app.data.model.Heatmap
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
    val allEpisodesOpen: Boolean = false,
    val showNumbers: Boolean = false,
    val heatMode: HeatMode = HeatMode.Rating,
    val heatmap: Heatmap? = null,
    val awards: com.cineverse.app.data.awards.Awards = com.cineverse.app.data.awards.Awards(),
    val loadingHeatmap: Boolean = false,
    val undo: UndoMark? = null,
    /** A season recap or the series finale, while one is open. */
    val recap: RecapView? = null,
    /** "Previously on", once asked for: null, loading, or the lines. */
    val previously: PreviouslyState = PreviouslyState.Idle,
)

sealed interface PreviouslyState {
    data object Idle : PreviouslyState
    data object Loading : PreviouslyState
    data object Empty : PreviouslyState
    data class Ready(val previously: com.cineverse.app.data.recap.Previously) : PreviouslyState
}

/** Which recap is up. */
sealed interface RecapView {
    data class Season(val recap: com.cineverse.app.data.recap.SeasonRecap) : RecapView
    data class Series(val recap: com.cineverse.app.data.recap.SeriesRecap) : RecapView
}

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
    val settings: StateFlow<com.cineverse.app.data.prefs.Settings> = app.settings.settings
    val progressFlow: StateFlow<Map<Int, ShowProgress>> = app.episodes.progress

    val progress: ShowProgress? get() = app.episodes.of(id)

    /** The library key for this title, known before the title page has loaded. */
    val key: String = "${type.wire}_$id"

    // Above `init` on purpose: a cached title makes load() finish during
    // construction, and it reaches loadCollection, which reads this.
    private val _collection = MutableStateFlow<com.cineverse.app.data.franchise.CollectionInfo?>(null)

    /** The collection a film belongs to, for the "part of" card and its meter. */
    val collection: StateFlow<com.cineverse.app.data.franchise.CollectionInfo?> = _collection.asStateFlow()

    private val _exactAir = MutableStateFlow<Long?>(null)

    /** The next episode's broadcast time to the minute, when TVmaze knows it. */
    val exactAir: StateFlow<Long?> = _exactAir.asStateFlow()

    init {
        if (type == MediaType.Tv) com.cineverse.app.core.shortcuts.HabitShortcuts.reportOpened(app.context, id)
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
                if (detail.collectionId > 0) loadCollection(detail.collectionId)
                detail.nextEpisode?.let { loadExactAir(detail, it) }
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

    private fun loadExactAir(detail: TitleDetail, next: com.cineverse.app.data.model.Episode) = viewModelScope.launch {
        if (next.airDate.isBlank()) return@launch
        val brief = com.cineverse.app.data.airing.TvBrief(
            id = detail.id,
            name = detail.title,
            originalName = detail.originalTitle,
            poster = detail.posterPath,
            backdrop = detail.backdropPath,
            status = "",
            firstAirDate = detail.releaseDate,
            seasonDates = emptyMap(),
            next = com.cineverse.app.data.airing.NextEpisode(
                season = next.season, episode = next.number, name = next.name,
                airDate = next.airDate, still = next.stillPath, type = "",
            ),
        )
        _exactAir.value = runCatching { app.airing.times.lookup(brief) }.getOrNull()
    }

    private fun loadCollection(id: Int) = viewModelScope.launch {
        if (_collection.value?.id == id) return@launch
        _collection.value = app.tmdb.collectionInfo(id)
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
            loadAwards(imdbId)
            return@launch
        }
        val scores = app.scores.of(imdbId, type)
        _state.value = _state.value.copy(scores = scores)
        loadAwards(imdbId)
    }

    /**
     * Trophies, from Wikidata, well off the critical path.
     *
     * TMDB has no awards feed, so this is a second service and a slow one. It
     * runs after the page is already on screen and simply fills a panel in when
     * it arrives; a title with nothing recorded shows no panel at all rather
     * than an empty one.
     */
    private fun loadAwards(imdbId: String) = viewModelScope.launch {
        val awards = app.awards.of(imdbId)
        if (awards.any) _state.value = _state.value.copy(awards = awards)
    }

    // ---------- the heatmap ----------

    fun toggleHeatmap() {
        val open = !_state.value.heatmapOpen
        _state.value = _state.value.copy(heatmapOpen = open)
        if (open) loadAllSeasons()
    }

    /**
     * Open the whole-run list.
     *
     * Shares the same fetch as the heatmap, so opening one and then the other
     * costs a single sweep of the show rather than two.
     */
    fun toggleAllEpisodes() {
        val open = !_state.value.allEpisodesOpen
        _state.value = _state.value.copy(allEpisodesOpen = open)
        if (open) loadAllSeasons()
    }

    fun toggleNumbers() {
        _state.value = _state.value.copy(showNumbers = !_state.value.showNumbers)
    }

    fun setHeatMode(mode: HeatMode) {
        _state.value = _state.value.copy(heatMode = mode)
    }

    /**
     * Every season at once, four requests in flight, then the model.
     *
     * Only fetched when the panel is opened: on a twelve-season show this is
     * twelve requests, and nobody who never opens the heatmap should pay for
     * them.
     */
    internal fun loadAllSeasons() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val wanted = detail.seasons.map { it.number }
        if (_state.value.heatmap != null && _state.value.allSeasons.keys.containsAll(wanted)) return@launch
        _state.value = _state.value.copy(loadingHeatmap = true)
        val all = _state.value.allSeasons + app.tmdb.allSeasons(id, wanted)
        _state.value = _state.value.copy(
            allSeasons = all,
            heatmap = Heatmap.build(all, app.episodes.of(id)),
            loadingHeatmap = false,
        )
    }

    /** Rebuild the grid's ticks after a mark, without refetching a thing. */
    private fun refreshHeatmap() {
        val held = _state.value
        if (held.heatmap == null || held.allSeasons.isEmpty()) return
        _state.value = held.copy(heatmap = Heatmap.build(held.allSeasons, app.episodes.of(id)))
    }

    // ---------- marks ----------

    fun toggleEpisode(season: Int, episode: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val before = app.episodes.of(id)
        app.episodes.toggleEpisode(detail, season, episode)
        refreshHeatmap()
        celebrate(before, season)
    }

    /**
     * Finishing a season raises its recap; finishing the whole show raises the
     * finale instead. Only on the transition - re-ticking an episode of a
     * season that was already complete does not throw the card at you again.
     */
    private fun celebrate(before: ShowProgress?, season: Int) {
        val after = app.episodes.of(id) ?: return
        fun seasonDone(show: ShowProgress?) =
            show != null && (show.structure[season] ?: 0) > 0 && show.watchedIn(season) >= (show.structure[season] ?: 0)
        if (seasonDone(before) || !seasonDone(after)) return
        if (after.complete && before?.complete != true) openSeriesRecap() else openSeasonRecap(season)
    }

    fun openSeasonRecap(season: Int) = viewModelScope.launch {
        val show = app.episodes.of(id) ?: return@launch
        val episodes = _state.value.allSeasons[season] ?: app.tmdb.season(id, season)
        _state.value = _state.value.copy(
            recap = RecapView.Season(com.cineverse.app.data.recap.Recaps.season(show, season, episodes)),
        )
    }

    fun openSeriesRecap() = viewModelScope.launch {
        val show = app.episodes.of(id) ?: return@launch
        val numbers = show.seasons.filterValues { it.isNotEmpty() }.keys.filter { it > 0 }
        val all = _state.value.allSeasons + app.tmdb.allSeasons(id, numbers - _state.value.allSeasons.keys)
        _state.value = _state.value.copy(
            allSeasons = all,
            recap = RecapView.Series(com.cineverse.app.data.recap.Recaps.series(show, all)),
        )
    }

    fun closeRecap() { _state.value = _state.value.copy(recap = null) }

    /**
     * The last three episodes you ticked before the next one, summarised.
     * Only ticked episodes are read, so it cannot spoil a thing.
     */
    fun loadPreviously() = viewModelScope.launch {
        if (_state.value.previously is PreviouslyState.Loading) return@launch
        val show = app.episodes.of(id) ?: return@launch
        val next = show.nextUp() ?: return@launch
        _state.value = _state.value.copy(previously = PreviouslyState.Loading)
        val watched = mutableListOf<com.cineverse.app.data.model.Episode>()
        for (season in show.structure.keys.filter { it in 1..next.first }.sortedDescending()) {
            val episodes = _state.value.allSeasons[season] ?: app.tmdb.season(id, season)
            watched += episodes
                .filter { show.isWatched(it.season, it.number) }
                .filter { season < next.first || it.number < next.second }
                .sortedByDescending { it.number }
            if (watched.size >= 3) break
        }
        val picked = watched.take(3).reversed()
        val result = app.previouslyOn.summarize(picked)
        _state.value = _state.value.copy(
            previously = result?.let { PreviouslyState.Ready(it) } ?: PreviouslyState.Empty,
        )
    }

    fun markUpTo(season: Int, episode: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val before = app.episodes.of(id)
        app.episodes.markUpTo(detail, season, episode)
        refreshHeatmap()
        celebrate(before, season)
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
        refreshHeatmap()
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
        app.library.setRating(detail.key, value, detail.title)
    }

    fun logRewatch() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val plays = app.library.logRewatch(detail.key)
        if (plays > 0) app.say("Logged — seen $plays times now")
    }

    fun setDropped(dropped: Boolean) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.episodes.setDropped(detail, dropped)
    }

    // ---------- lists ----------

    /**
     * Put a title in a list, or take it out.
     *
     * Naming a list for a title that is not saved yet SAVES it first. Otherwise
     * the membership would be written to a watchlist document that does not
     * exist and the choice would silently evaporate.
     */
    fun setInList(listId: String, member: Boolean) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        if (!app.library.library.value.isSaved(detail.key)) {
            app.library.toggleSaved(detail.asItem(), detail)
        }
        val held = app.library.library.value.saved[detail.key]?.lists ?: listOf("watchlist")
        val next = if (member) held + listId else held - listId
        app.library.setLists(detail.key, next)
    }

    fun createList(name: String) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        val id = app.library.createList(name) ?: return@launch
        // A list made from a title sheet is made FOR that title: it would be a
        // strange thing to name a list and then have to tick it as well.
        setInList(id, true)
    }

    fun renameList(id: String, name: String) = viewModelScope.launch {
        app.library.renameList(id, name)
    }

    fun deleteList(id: String) = viewModelScope.launch { app.library.deleteList(id) }

    // ---------- a film, mid-play ----------

    fun setMovieProgress(minutes: Int) = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.library.setMovieProgress(detail.id, minutes, detail.runtime, detail)
    }

    fun clearMovieProgress() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.library.clearMovieProgress(detail.id)
    }

    /** Reached the end: mark it watched and stop calling it in-progress. */
    fun finishMovie() = viewModelScope.launch {
        val detail = _state.value.detail ?: return@launch
        app.library.clearMovieProgress(detail.id)
        if (!app.library.library.value.isWatched(detail.key)) {
            app.library.toggleWatched(detail.asItem(), detail)
        }
    }
}
