package com.cineverse.app.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.model.MediaItem
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
    val results: List<MediaItem> = emptyList(),
    val history: List<String> = emptyList(),
    val loading: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val hasMore: Boolean get() = page < totalPages && results.isNotEmpty()
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val library: StateFlow<Library> = app.library.library

    private val history = ArrayDeque<String>()

    init {
        // 280 ms: long enough that typing "severance" is one request rather than
        // nine, short enough that it still feels like it is keeping up.
        _state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(280)
            .filter { it.length >= 2 }
            .onEach { query -> search(query, page = 1) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        if (value.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), page = 1, totalPages = 1)
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        remember(query)
        search(query, page = 1)
    }

    private fun remember(query: String) {
        history.remove(query)
        history.addFirst(query)
        while (history.size > 8) history.removeLast()
        _state.value = _state.value.copy(history = history.toList())
    }

    private fun search(query: String, page: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val (items, total) = app.tmdb.search(query, page, app.settings.settings.value.adult)
        val existing = if (page == 1) emptyList() else _state.value.results
        _state.value = _state.value.copy(
            results = (existing + items).distinctBy { it.key },
            loading = false,
            page = page,
            totalPages = total,
        )
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        search(current.query.trim(), current.page + 1)
    }
}
