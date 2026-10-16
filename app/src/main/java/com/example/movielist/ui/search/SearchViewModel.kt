package com.example.movielist.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.MovieSearchRepository
import com.example.movielist.domain.repository.UserRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab {
    MOVIES,
    PEOPLE,
}

/** Results of one tab for the current query. */
data class SearchResults<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.MOVIES,
    /** The query the results below belong to (the debounced one). */
    val searchedQuery: String = "",
    val movies: SearchResults<Movie> = SearchResults(),
    val people: SearchResults<UserSummary> = SearchResults(),
)

/**
 * Searches movies and people as the user types: 300 ms after the last keystroke, both tabs
 * are searched at once, and a newer query cancels the older search.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val movieSearch: MovieSearchRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiStateFlow: StateFlow<SearchUiState> = _uiState.asStateFlow()
    val uiState: SearchUiState
        get() = _uiState.value

    private val queries = MutableStateFlow("")
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            queries.debounce(DEBOUNCE_MILLIS).distinctUntilChanged().collectLatest { query -> search(query) }
        }
    }

    fun onQueryChange(text: String) {
        _uiState.update { it.copy(query = text.take(MAX_QUERY)) }
        queries.value = text.trim().take(MAX_QUERY)
    }

    fun selectTab(tab: SearchTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    /** Shows the next page of the current tab, if there is one. */
    fun loadMore() {
        val state = uiState
        val query = state.searchedQuery
        if (query.isEmpty() || moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            when (state.tab) {
                SearchTab.MOVIES -> if (state.movies.hasMore) loadMovies(query, state.movies.page + 1)
                SearchTab.PEOPLE -> if (state.people.hasMore) loadPeople(query, state.people.page + 1)
            }
        }
    }

    fun retry() {
        val query = uiState.searchedQuery
        if (query.isNotEmpty()) viewModelScope.launch { search(query) }
    }

    private suspend fun search(query: String) {
        moreJob?.cancel()
        _uiState.update {
            it.copy(
                searchedQuery = query,
                movies = SearchResults(isLoading = query.isNotEmpty()),
                people = SearchResults(isLoading = query.length >= MIN_PEOPLE_QUERY),
            )
        }
        if (query.isEmpty()) return
        coroutineScope {
            launch { loadMovies(query, page = 1) }
            if (query.length >= MIN_PEOPLE_QUERY) launch { loadPeople(query, page = 1) }
        }
    }

    private suspend fun loadMovies(query: String, page: Int) =
        load(page, { movieSearch.search(query, page) }) { transform ->
            _uiState.update { if (it.searchedQuery == query) it.copy(movies = transform(it.movies)) else it }
        }

    private suspend fun loadPeople(query: String, page: Int) =
        load(page, { users.search(query, page) }) { transform ->
            _uiState.update { if (it.searchedQuery == query) it.copy(people = transform(it.people)) else it }
        }

    /** Loads [page] and appends it; page 1 replaces. Errors are shown in place of the results. */
    private suspend fun <T> load(
        page: Int,
        fetch: suspend () -> SearchPage<T>,
        update: ((SearchResults<T>) -> SearchResults<T>) -> Unit,
    ) {
        update { it.copy(isLoading = true, error = null) }
        try {
            val result = fetch()
            update {
                it.copy(
                    items = if (page == 1) result.items else it.items + result.items,
                    page = page,
                    hasMore = result.hasMore,
                    isLoading = false,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            update { it.copy(isLoading = false, error = exception.toUserMessage()) }
        }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 300L
        /** The server needs at least two characters to search people. */
        const val MIN_PEOPLE_QUERY = 2
        private const val MAX_QUERY = 50
    }
}
