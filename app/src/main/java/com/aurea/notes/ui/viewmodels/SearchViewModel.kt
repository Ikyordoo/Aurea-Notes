package com.aurea.notes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.JournalRepository
import com.aurea.notes.data.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class SearchScope {
    ALL,
    NOTES,
    JOURNAL
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val matchingNotes: List<NoteEntity> = emptyList(),
    val matchingJournalEntries: List<JournalEntryEntity> = emptyList()
) {
    val hasActiveQuery: Boolean
        get() = query.trim().isNotEmpty()

    val totalResultsCount: Int
        get() = matchingNotes.size + matchingJournalEntries.size
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val notesRepository: NotesRepository,
    private val journalRepository: JournalRepository,
    debounceMillis: Long = 150L
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _scope = MutableStateFlow(SearchScope.ALL)
    val scope: StateFlow<SearchScope> = _scope.asStateFlow()

    private val debouncedTrimmedQuery = _query
        .map { it.trim() }
        .debounce { trimmed ->
            if (trimmed.isEmpty()) 0L else debounceMillis
        }

    private val searchResultsFlow = combine(
        debouncedTrimmedQuery,
        _scope
    ) { trimmedQuery, currentScope ->
        trimmedQuery to currentScope
    }.flatMapLatest { (trimmedQuery, currentScope) ->
        if (trimmedQuery.isEmpty()) {
            flowOf(emptyList<NoteEntity>() to emptyList<JournalEntryEntity>())
        } else {
            when (currentScope) {
                SearchScope.ALL -> combine(
                    notesRepository.searchNotes(trimmedQuery),
                    journalRepository.searchEntries(trimmedQuery)
                ) { notes, entries ->
                    notes to entries
                }
                SearchScope.NOTES -> notesRepository
                    .searchNotes(trimmedQuery)
                    .map { notes -> notes to emptyList() }
                SearchScope.JOURNAL -> journalRepository
                    .searchEntries(trimmedQuery)
                    .map { entries -> emptyList<NoteEntity>() to entries }
            }
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        _query,
        _scope,
        searchResultsFlow
    ) { rawQuery, currentScope, (notes, entries) ->
        SearchUiState(
            query = rawQuery,
            scope = currentScope,
            matchingNotes = notes,
            matchingJournalEntries = entries
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onScopeChange(newScope: SearchScope) {
        _scope.value = newScope
    }

    fun clearQuery() {
        _query.value = ""
    }

    companion object {
        fun provideFactory(
            notesRepository: NotesRepository,
            journalRepository: JournalRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SearchViewModel(notesRepository, journalRepository) as T
                }
            }
    }
}
