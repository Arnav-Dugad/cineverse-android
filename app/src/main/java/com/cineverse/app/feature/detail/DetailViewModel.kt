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
    /** "Catch me up" on a whole finished season, before the next. */
    val catchUp: PreviouslyState = PreviouslyState.Idle,
)

sealed interface PreviouslyState {
    data object Idle : PreviouslyState
    data object Loading : PreviouslyState
    data object Empty : PreviouslyState
    data class Ready(val previously: com.cineverse.app.data.recap.Previously) : PreviouslyState
}

/** The trivia card: three facts, once asked for. */
sealed interface TriviaState {
    data object Idle : TriviaState
    data object Loading : TriviaState
    data object Failed : TriviaState
    data class Ready(val facts: List<String>) : TriviaState
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

    private val _chat = MutableStateFlow(app.chatStore.load(key))

    /** The conversation in the "Ask about it" sheet, kept on the device across visits. */
    val chat: StateFlow<List<com.cineverse.app.data.ai.ChatTurn>> = _chat.asStateFlow()

    /** The film being watched now, for the Live Update pill. */
    val watchingNow: StateFlow<com.cineverse.app.notify.Watching?> = app.watchingNow.current

    private val _pitch = MutableStateFlow<com.cineverse.app.data.ai.Pitch?>(null)

    /** Gemini on how this sits with your taste; null until (and unless) it answers. */
    val pitch: StateFlow<com.cineverse.app.data.ai.Pitch?> = _pitch.asStateFlow()
    private var pitchAsked = false

    // All of these above `init` too, for the same reason as the collection:
    // a cached title loads during construction, and a load that reaches a
    // flow not yet built is a NullPointerException (it was, on the phone).
    private val _seasonScores = MutableStateFlow(com.cineverse.app.data.scores.SeasonScores())

    /** The selected season's episode scores: IMDb's with an OMDb key, TVmaze's without. */
    val seasonScores: StateFlow<com.cineverse.app.data.scores.SeasonScores> = _seasonScores.asStateFlow()

    private val _wiki = MutableStateFlow(com.cineverse.app.data.wiki.WikiFacts())

    /** "Based on" and box office, from Wikidata. */
    val wiki: StateFlow<com.cineverse.app.data.wiki.WikiFacts> = _wiki.asStateFlow()

    private val _places = MutableStateFlow<List<com.cineverse.app.data.places.Place>>(emptyList())

    /** Filming locations, from Wikidata. */
    val places: StateFlow<List<com.cineverse.app.data.places.Place>> = _places.asStateFlow()

    private val _nextStill = MutableStateFlow<String?>(null)

    /** TVmaze's still for the next episode, when TMDB has none yet. */
    val nextStill: StateFlow<String?> = _nextStill.asStateFlow()

    private val _castHours = MutableStateFlow<Map<Int, com.cineverse.app.data.cast.ActorHours>>(emptyMap())

    /** Hours you have spent with each of the cast, for the About tab. */
    val castHours: StateFlow<Map<Int, com.cineverse.app.data.cast.ActorHours>> = _castHours.asStateFlow()
    private var castHoursLoaded = false

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
                if (_state.value.tab == DetailTab.About) loadCastHours()
                detail.nextEpisode?.let { loadExactAir(detail, it) }
                loadPitch(detail)
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
        _nextStill.value = app.airing.times.cachedImage(detail.id, next.season, next.number)
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

    fun selectTab(tab: DetailTab) {
        _state.value = _state.value.copy(tab = tab)
        if (tab == DetailTab.About) loadCastHours()
    }


    /**
     * Each cast member's credits, a few at a time, only once About is shown.
     * Twelve people, cached for a week by the HTTP layer after the first look.
     */
    fun loadCastHours() {
        if (castHoursLoaded) return
        val detail = _state.value.detail ?: return
        castHoursLoaded = true
        viewModelScope.launch {
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            detail.cast.take(12).forEach { person ->
                launch {
                    gate.acquire()
                    try {
                        val full = runCatching { app.tmdb.person(person.id) }.getOrNull() ?: return@launch
                        val hours = com.cineverse.app.data.cast.ActorHours.of(
                            full, app.library.library.value, app.episodes.progress.value,
                        )
                        if (hours.minutes > 0) _castHours.value = _castHours.value + (person.id to hours)
                    } finally {
                        gate.release()
                    }
                }
            }
        }
    }

    fun spoilerLine(): com.cineverse.app.data.ai.SpoilerLine? {
        val detail = _state.value.detail ?: return null
        return com.cineverse.app.data.ai.TitleChat.spoilerLine(
            detail,
            app.episodes.progress.value[detail.id],
            app.library.library.value.isWatched(detail.key),
        )
    }

    /**
     * Unlocked once it is all watched: a film marked watched, or a series
     * finished. Before then the ending stays where it belongs.
     */
    fun endingUnlocked(): Boolean {
        val detail = _state.value.detail ?: return false
        return if (detail.isSeries) app.episodes.progress.value[detail.id]?.complete == true || endingSeason() != null
        else app.library.library.value.isWatched(detail.key)
    }

    /**
     * A series still running that you are caught up on, to the end of its
     * latest season with no newer one out: that season, whose ending can be
     * explained (and whose trivia is safe) without waiting for the finale.
     */
    fun endingSeason(): Int? {
        val detail = _state.value.detail?.takeIf { it.isSeries } ?: return null
        val show = app.episodes.progress.value[detail.id] ?: return null
        if (show.complete || show.watchedCount == 0 || show.nextUp() != null) return null
        val season = show.seasons.filterValues { it.isNotEmpty() }.keys.filter { it > 0 }.maxOrNull() ?: return null
        val total = show.structure[season] ?: return null
        return season.takeIf { total > 0 && show.watchedIn(it) >= total }
    }

    fun explainEnding() {
        if (!endingUnlocked()) return
        // Already explained (or being explained): the sheet shows it again.
        if (_chat.value.any { it.question == ENDING && (it.byGemini || it.answer == null) }) return
        ask(ENDING, ending = true)
    }

    private var asking: kotlinx.coroutines.Job? = null

    private val _trivia = MutableStateFlow<TriviaState>(
        app.trivia.cached(key)?.let { TriviaState.Ready(it) } ?: TriviaState.Idle
    )

    /** "Did you know": locked until watched, then three facts on a tap. */
    val trivia: StateFlow<TriviaState> = _trivia.asStateFlow()

    fun loadTrivia() {
        val detail = _state.value.detail ?: return
        if (_trivia.value is TriviaState.Loading || _trivia.value is TriviaState.Ready || !endingUnlocked()) return
        _trivia.value = TriviaState.Loading
        viewModelScope.launch {
            val facts = runCatching { app.trivia.facts(detail) }.getOrNull()
            _trivia.value = facts?.let { TriviaState.Ready(it) } ?: TriviaState.Failed
        }
    }

    fun ask(question: String, ending: Boolean = false) {
        val detail = _state.value.detail ?: return
        val line = spoilerLine() ?: return
        if (asking?.isActive == true) return
        val history = _chat.value
        _chat.value = history + com.cineverse.app.data.ai.ChatTurn(question)
        asking = viewModelScope.launch {
            val viewer = viewerBrief(detail)
            runCatching {
                app.titleChat.answer(detail, line, history, question, ending, viewer, if (ending) endingSeason() else null).collect { turn ->
                    _chat.value = _chat.value.dropLast(1) + turn
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _chat.value = _chat.value.dropLast(1) + com.cineverse.app.data.ai.ChatTurn(question, "Something went wrong. Try again in a moment.")
            }
            app.chatStore.save(key, _chat.value)
        }
    }

    suspend fun resolveMentions(answer: String): com.cineverse.app.data.ai.Mentioned =
        com.cineverse.app.data.ai.Mentions.resolve(app, answer)

    /** An answer read aloud when "Voice search answers aloud" is on; [done] either way. */
    fun speak(text: String, done: () -> Unit) {
        if (app.settings.settings.value.spokenAnswers) app.speaker.say(text, done) else done()
    }

    fun stopSpeaking() = app.speaker.stop()

    /** Start this title's conversation over. */
    fun clearChat() {
        asking?.cancel()
        _chat.value = emptyList()
        app.chatStore.clear(key)
    }

    /** The viewer's brief plus where they stand with this very title. */
    private suspend fun viewerBrief(detail: TitleDetail): String {
        val brief = runCatching { app.persona.brief() }.getOrDefault("")
        val library = app.library.library.value
        val show = app.episodes.progress.value[detail.id]
        val here = buildList {
            library.ratings[detail.key]?.let { add("they rated it $it/10") }
            if (library.isWatched(detail.key)) add("they have watched it")
            show?.takeIf { it.watchedCount > 0 }?.let { add("they have seen ${it.watchedCount} of ${it.totalEpisodes} episodes") }
            if (library.saved.containsKey(detail.key) && !library.isWatched(detail.key)) add("it is on their watchlist")
        }
        return buildString {
            append(brief)
            if (here.isNotEmpty()) append("\n- With this title: ${here.joinToString(", ")}.")
        }.trim()
    }

    /** Start the Live Update from where you are in the film, or stop it, keeping your place. */
    fun toggleWatchingNow() {
        val detail = _state.value.detail ?: return
        val current = app.watchingNow.current.value
        if (current?.id == detail.id) {
            val was = app.watchingNow.stop()
            val at = was?.elapsed() ?: 0
            if (at > 0) viewModelScope.launch { runCatching { app.library.setMovieProgress(detail.id, at, detail.runtime, detail) } }
        } else {
            val from = app.library.library.value.movieProgress[detail.id]?.minutes ?: 0
            app.watchingNow.start(detail.id, detail.title, detail.runtime, from, detail.posterPath.orEmpty())
        }
    }

    /** Once per visit, and only for something you have not seen. */
    private fun loadPitch(detail: TitleDetail) {
        if (pitchAsked) return
        val library = app.library.library.value
        if (!library.loaded || library.isWatched(detail.key) || app.episodes.progress.value[detail.id]?.watchedCount?.let { it > 0 } == true) return
        pitchAsked = true
        viewModelScope.launch {
            _pitch.value = runCatching { app.forYou.pitch(detail, library) }.getOrNull()
        }
    }

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
        loadSeasonScores(season)
    }


    private fun loadSeasonScores(season: Int) = viewModelScope.launch {
        _seasonScores.value = com.cineverse.app.data.scores.SeasonScores()
        val detail = _state.value.detail ?: return@launch
        val imdbId = detail.imdbId.ifBlank { runCatching { app.tmdb.imdbId(id, type) }.getOrDefault("") }
        val scores = runCatching { app.episodeScores.season(imdbId, detail.title, season) }.getOrNull() ?: return@launch
        if (_state.value.season == season) _seasonScores.value = scores
    }

    private val _compare = MutableStateFlow<CompareState?>(null)

    /** "Compare with...": null when the sheet is closed. */
    val compare: StateFlow<CompareState?> = _compare.asStateFlow()
    private var comparing: kotlinx.coroutines.Job? = null

    fun openCompare() { _compare.value = CompareState.Picking }
    fun closeCompare() { comparing?.cancel(); _compare.value = null }

    suspend fun searchTitles(query: String): List<com.cineverse.app.data.model.MediaItem> =
        app.tmdb.searchPage(query, 1, app.settings.settings.value.adult).items

    fun compareWith(other: com.cineverse.app.data.model.MediaItem) {
        val detail = _state.value.detail ?: return
        comparing?.cancel()
        _compare.value = CompareState.Comparing(other)
        comparing = viewModelScope.launch {
            val otherDetail = runCatching { app.tmdb.detail(other.id, other.type, app.settings.settings.value.region) }.getOrNull()
            val result = otherDetail?.let { runCatching { app.compare.of(detail, it) }.getOrNull() }
            if (_compare.value !is CompareState.Comparing) return@launch
            _compare.value = result?.let { CompareState.Done(other, it) } ?: CompareState.Failed(other)
        }
    }

    suspend fun adviseSkip(episode: com.cineverse.app.data.model.Episode): com.cineverse.app.data.ai.Skip? {
        val detail = _state.value.detail ?: return null
        return app.skipAdvice.of(detail.id, detail.title, episode)
    }

    /** Your score for an episode; 0 clears it. */
    fun rateEpisode(season: Int, episode: Int, score: Int) = viewModelScope.launch {
        runCatching { app.episodes.rateEpisode(id, season, episode, score) }
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
        // Where it was filmed, from the same source, after the trophies.
        _wiki.value = runCatching { app.wikiFacts.of(imdbId) }.getOrDefault(com.cineverse.app.data.wiki.WikiFacts())
        val places = runCatching { app.filmingLocations.of(imdbId) }.getOrDefault(emptyList())
        _places.value = places
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

    /**
     * The season to catch up on: the latest one you have finished whose next
     * season exists and you have not started. Null when there is none.
     */
    fun catchUpSeason(progress: com.cineverse.app.data.model.ShowProgress?): Int? {
        val detail = _state.value.detail ?: return null
        val show = progress ?: return null
        val numbers = detail.seasons.map { it.number }.filter { it > 0 }.toSet()
        return show.structure.keys.filter { it > 0 }.sortedDescending().firstOrNull { season ->
            val total = show.structure[season] ?: 0
            total > 0 && show.watchedIn(season) >= total && (season + 1) in numbers && show.watchedIn(season + 1) == 0
        }
    }

    fun loadCatchUp(season: Int) = viewModelScope.launch {
        if (_state.value.catchUp is PreviouslyState.Loading) return@launch
        val detail = _state.value.detail ?: return@launch
        _state.value = _state.value.copy(catchUp = PreviouslyState.Loading)
        val episodes = _state.value.allSeasons[season] ?: app.tmdb.season(id, season)
        val result = runCatching { app.previouslyOn.season(detail.title, season, episodes) }.getOrNull()
        _state.value = _state.value.copy(catchUp = result?.let { PreviouslyState.Ready(it) } ?: PreviouslyState.Empty)
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

/** The question the ending card asks. */
const val ENDING = "Explain the ending"
