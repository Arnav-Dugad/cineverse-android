package com.cineverse.app.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.ai.Ask
import com.cineverse.app.data.ai.Outcome
import com.cineverse.app.data.ai.Understanding
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.GenreNames
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import com.cineverse.app.data.model.Genre
import com.cineverse.app.data.model.MediaFilter
import com.cineverse.app.data.model.MediaItem
import com.cineverse.app.data.model.MediaType
import com.cineverse.app.data.model.SortOrder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class SearchState(
    val query: String = "",
    /** Everything TMDB returned, before the filter. */
    val results: List<MediaItem> = emptyList(),
    /** What the user actually sees. */
    val shown: List<MediaItem> = emptyList(),
    val history: List<String> = emptyList(),
    /** People the search found - a row of faces above the posters. */
    val people: List<com.cineverse.app.data.model.Person> = emptyList(),
    /** What an empty search box offers instead of a blank page. */
    val trending: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
    val filter: MediaFilter = MediaFilter(),
    val genres: List<Genre> = emptyList(),
    /** A sentence being understood, or what came of it; null for a plain search. */
    val ask: AskUi? = null,
) {
    val hasMore: Boolean get() = page < totalPages && results.isNotEmpty()

    /** Filtered everything away, with more pages still to come. */
    val filteredOut: Boolean get() = results.isNotEmpty() && shown.isEmpty()
}

/** What became of a sentence typed or spoken into search. */
@Immutable
sealed interface AskUi {
    val heard: String

    data class Thinking(override val heard: String) : AskUi

    /** A discovery: what was understood, in words, and what it found. */
    data class Results(
        override val heard: String,
        val understood: String,
        val reply: String?,
        val items: List<MediaItem>,
        val byGemini: Boolean,
    ) : AskUi

    /** Something done: added, ticked, rated. */
    data class Did(override val heard: String, val outcome: Outcome) : AskUi
}

/** Things search asks the app to do: go somewhere, play something. */
sealed interface SearchEvent {
    data class Open(val item: MediaItem) : SearchEvent
    data class Trailer(val key: String, val title: String) : SearchEvent
    data class Navigate(val page: String) : SearchEvent
}

/** The orders a SEARCH offers. Relevance leads, because TMDB already ranked it. */
val SearchSorts = listOf(
    SortOrder.Relevance,
    SortOrder.Imdb,
    SortOrder.Rating,
    SortOrder.Votes,
    SortOrder.Newest,
    SortOrder.Oldest,
    SortOrder.Title,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    // Kept on the device, the eight most recent. It lived only in memory, so
    // leaving Search and coming back found the list empty every time.
    private val prefs = app.context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)
    private val history = ArrayDeque(
        prefs.getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }.take(8)
    )

    init {
        _state.value = _state.value.copy(history = history.toList())
        viewModelScope.launch {
            _state.value = _state.value.copy(
                trending = app.tmdb.trending("all", "day").filter { it.hasArt }.take(18),
            )
        }
        // 280 ms: long enough that typing "severance" is one request rather than
        // nine, short enough that it still feels like it is keeping up.
        _state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(280)
            .filter { it.length >= 2 && _state.value.ask == null }
            .onEach { query -> search(query, page = 1) }
            .launchIn(viewModelScope)

        // Both genre lists, merged: a multi-search returns films and series in
        // one list, so a genre filter that only knew one of them would silently
        // drop half the matches.
        viewModelScope.launch {
            val merged = (app.tmdb.genres(MediaType.Movie) + app.tmdb.genres(MediaType.Tv))
                .distinctBy { it.id }
                .sortedBy { it.name }
            _state.value = _state.value.copy(genres = merged)
        }
    }

    fun setFilter(value: MediaFilter) {
        _state.value = _state.value.copy(filter = value).withFilter()
    }

    /**
     * Re-apply the filter to whatever is held.
     *
     * Kept as one function rather than inlined at each call site because the
     * filter has to be re-run on three separate occasions — a new search, a
     * further page, a changed filter — and a list that is filtered on two of
     * them is worse than one that is filtered on none.
     */
    private fun SearchState.withFilter(): SearchState = copy(
        shown = filter.apply(
            results,
            isWatched = { app.library.library.value.isWatched(it.key) },
            imdbOf = ::imdbOf,
        )
    )

    /** A title whose score has not arrived sorts last rather than as a zero. */
    private fun imdbOf(item: MediaItem): Double {
        val imdbId = app.tmdb.cachedDetail(
            item.id, item.type, app.settings.settings.value.region,
        )?.imdbId.orEmpty()
        if (imdbId.isBlank()) return -1.0
        return app.scores.cached(imdbId, item.type)?.imdb ?: -1.0
    }

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events: Flow<SearchEvent> = _events.receiveAsFlow()

    /**
     * A sentence, typed or spoken. A command is carried out ("add Dune to my
     * list"), a request becomes a discovery ("funny 90s films with Tom
     * Hanks"), and anything else is an ordinary search.
     */
    fun ask(text: String, spoken: Boolean) {
        val heard = text.trim()
        if (heard.length < 2) return
        remember(heard)
        val mine = ++generation
        _state.value = _state.value.copy(query = heard, ask = AskUi.Thinking(heard), loading = false)
        viewModelScope.launch {
            val natural = spoken || Understanding.looksNatural(heard)
            val (ask, reply) = runCatching { app.assistant.understand(heard, natural) }
                .getOrDefault(Ask.Search(heard, heard) to null)
            if (mine != generation) return@launch
            val assistant = app.assistant
            val ui: AskUi? = when (ask) {
                is Ask.Search -> null
                is Ask.Discover -> {
                    val items = runCatching { assistant.discover(ask.query) }.getOrDefault(emptyList())
                    AskUi.Results(
                        heard = heard,
                        understood = ask.query.describe { GenreNames[it] },
                        reply = reply,
                        items = items,
                        byGemini = reply != null,
                    )
                }
                is Ask.Navigate -> {
                    _events.send(SearchEvent.Navigate(ask.page))
                    AskUi.Did(heard, Outcome(true, "Going to ${pageName(ask.page)}"))
                }
                is Ask.Open -> assistant.open(ask.title).also { o -> o.item?.let { _events.send(SearchEvent.Open(it)) } }.let { AskUi.Did(heard, it) }
                is Ask.Trailer -> assistant.trailer(ask.title).also { o ->
                    val video = o.trailer
                    val item = o.item
                    if (video != null && item != null) _events.send(SearchEvent.Trailer(video.key, item.title))
                }.let { AskUi.Did(heard, it) }
                is Ask.Add -> AskUi.Did(heard, assistant.add(ask.title))
                is Ask.Remove -> AskUi.Did(heard, assistant.remove(ask.title))
                is Ask.Watched -> AskUi.Did(heard, assistant.watched(ask.title))
                is Ask.Rate -> AskUi.Did(heard, assistant.rate(ask.title, ask.score))
                is Ask.NextEpisode -> AskUi.Did(heard, assistant.nextEpisode(ask.show))
            }
            if (mine != generation) return@launch
            _state.value = _state.value.copy(ask = ui)
            if (ui == null) search((ask as Ask.Search).query, page = 1)
        }
    }

    private fun pageName(page: String) = when (page) {
        "list" -> "your list"; "stats" -> "your stats"; "box-office" -> "the box office"
        "year" -> "your year"; "top10" -> "the Top 10"; "tv" -> "TV shows"
        else -> page
    }

    /** Back to plain search, keeping the words. */
    fun dismissAsk() {
        generation++
        _state.value = _state.value.copy(ask = null)
        val query = _state.value.query.trim()
        if (query.length >= 2) search(query, page = 1)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, ask = null)
        if (value.isBlank()) {
            generation++
            _state.value = _state.value.copy(
                results = emptyList(), shown = emptyList(), people = emptyList(),
                page = 1, totalPages = 1, loading = false,
            )
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        // A sentence, or a command, is understood; a title is searched.
        if (Understanding.command(query) != null || Understanding.looksNatural(query)) {
            ask(query, spoken = false)
            return
        }
        remember(query)
        search(query, page = 1)
    }

    private fun remember(query: String) {
        history.remove(query)
        history.addFirst(query)
        while (history.size > 8) history.removeLast()
        persist()
    }

    fun forget(query: String) {
        history.remove(query)
        persist()
    }

    fun clearHistory() {
        history.clear()
        persist()
    }

    private fun persist() {
        prefs.edit().putString("history", history.joinToString("\n")).apply()
        _state.value = _state.value.copy(history = history.toList())
    }

    /** Bumped by every search, so only the newest one may paint. */
    private var generation = 0

    private fun search(query: String, page: Int) = viewModelScope.launch {
        val mine = ++generation
        _state.value = _state.value.copy(loading = true)
        val found = app.tmdb.searchPage(query, page, app.settings.settings.value.adult)
        // A late answer to a query the user has since changed is dropped, not
        // painted over what they are now typing. The newer search owns the
        // loading flag and will clear it.
        if (mine != generation) return@launch
        val existing = if (page == 1) emptyList() else _state.value.results
        _state.value = _state.value.copy(
            results = (existing + found.items).distinctBy { it.key },
            people = if (page == 1) found.people else _state.value.people,
            loading = false,
            page = page,
            totalPages = found.totalPages,
        ).withFilter()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        search(current.query.trim(), current.page + 1)
    }
}
