package com.aurea.notes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class JournalEditorUiState(
    val isOpen: Boolean = false,
    val isEditingExisting: Boolean = false,
    val entryId: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val originalTitle: String = "",
    val originalContent: String = "",
    val originalIsPinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val showValidationError: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val showDeleteConfirmation: Boolean = false
) {
    val hasUnsavedChanges: Boolean
        get() = isOpen && (
            title.trim() != originalTitle.trim() ||
                content.trim() != originalContent.trim() ||
                isPinned != originalIsPinned
            )
}

class JournalViewModel(
    private val repository: JournalRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = repository
        .observeAllEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(JournalEditorUiState())
    val editorState: StateFlow<JournalEditorUiState> = _editorState.asStateFlow()

    fun toggleEntryPinned(entry: JournalEntryEntity) {
        viewModelScope.launch {
            repository.setEntryPinned(entry.id, !entry.isPinned)
        }
    }

    fun startNewEntry() {
        val now = clock()
        _editorState.value = JournalEditorUiState(
            isOpen = true,
            isEditingExisting = false,
            entryId = UUID.randomUUID().toString(),
            title = "",
            content = "",
            isPinned = false,
            originalTitle = "",
            originalContent = "",
            originalIsPinned = false,
            createdAt = now,
            updatedAt = now
        )
    }

    fun openEntry(entry: JournalEntryEntity) {
        _editorState.value = JournalEditorUiState(
            isOpen = true,
            isEditingExisting = true,
            entryId = entry.id,
            title = entry.title,
            content = entry.content,
            isPinned = entry.isPinned,
            originalTitle = entry.title,
            originalContent = entry.content,
            originalIsPinned = entry.isPinned,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt
        )
    }

    fun onTitleChange(newTitle: String) {
        _editorState.update {
            it.copy(title = newTitle)
        }
    }

    fun onContentChange(newContent: String) {
        _editorState.update {
            it.copy(
                content = newContent,
                showValidationError = false
            )
        }
    }

    fun toggleEditorPinned() {
        _editorState.update {
            it.copy(isPinned = !it.isPinned)
        }
    }

    fun saveEntry(onSaved: () -> Unit = {}) {
        val state = _editorState.value
        val trimmedTitle = state.title.trim()
        val trimmedContent = state.content.trim()

        if (trimmedContent.isEmpty()) {
            _editorState.update { it.copy(showValidationError = true) }
            return
        }

        val now = clock()
        val entryToSave = JournalEntryEntity(
            id = state.entryId.ifEmpty { UUID.randomUUID().toString() },
            title = trimmedTitle,
            content = trimmedContent,
            createdAt = if (state.createdAt > 0L) state.createdAt else now,
            updatedAt = now,
            isPinned = state.isPinned
        )

        viewModelScope.launch {
            repository.saveEntry(entryToSave)
            _editorState.value = JournalEditorUiState()
            onSaved()
        }
    }

    fun requestCancelEditor() {
        val state = _editorState.value
        if (state.hasUnsavedChanges) {
            _editorState.update { it.copy(showDiscardConfirmation = true) }
        } else {
            _editorState.value = JournalEditorUiState()
        }
    }

    fun dismissDiscardConfirmation() {
        _editorState.update { it.copy(showDiscardConfirmation = false) }
    }

    fun confirmDiscardChanges() {
        _editorState.value = JournalEditorUiState()
    }

    fun requestDeleteEntry() {
        val state = _editorState.value
        if (state.isEditingExisting && state.entryId.isNotEmpty()) {
            _editorState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun dismissDeleteConfirmation() {
        _editorState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteEntry(onDeleted: () -> Unit = {}) {
        val state = _editorState.value
        if (!state.isEditingExisting || state.entryId.isEmpty()) return

        viewModelScope.launch {
            repository.deleteEntryById(state.entryId)
            _editorState.value = JournalEditorUiState()
            onDeleted()
        }
    }

    companion object {
        fun provideFactory(repository: JournalRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return JournalViewModel(repository) as T
                }
            }
    }
}
