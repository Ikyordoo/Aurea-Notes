package com.aurea.notes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.NoteSortOrder
import com.aurea.notes.data.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class NoteEditorUiState(
    val isOpen: Boolean = false,
    val isEditingExisting: Boolean = false,
    val noteId: String = "",
    val title: String = "",
    val content: String = "",
    val contentFormat: String = NoteEntity.FORMAT_PLAIN_TEXT,
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

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NotesRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(NoteSortOrder.LAST_MODIFIED)
    val sortOrder: StateFlow<NoteSortOrder> = _sortOrder.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = _sortOrder
        .flatMapLatest { order ->
            repository.observeNotes(order)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(NoteEditorUiState())
    val editorState: StateFlow<NoteEditorUiState> = _editorState.asStateFlow()

    fun setSortOrder(order: NoteSortOrder) {
        _sortOrder.value = order
    }

    fun toggleNotePinned(note: NoteEntity) {
        viewModelScope.launch {
            repository.setNotePinned(note.id, !note.isPinned)
        }
    }

    fun startNewNote() {
        val now = clock()
        _editorState.value = NoteEditorUiState(
            isOpen = true,
            isEditingExisting = false,
            noteId = UUID.randomUUID().toString(),
            title = "",
            content = "",
            contentFormat = NoteEntity.FORMAT_PLAIN_TEXT,
            isPinned = false,
            originalTitle = "",
            originalContent = "",
            originalIsPinned = false,
            createdAt = now,
            updatedAt = now
        )
    }

    fun openNote(note: NoteEntity) {
        _editorState.value = NoteEditorUiState(
            isOpen = true,
            isEditingExisting = true,
            noteId = note.id,
            title = note.title,
            content = note.content,
            contentFormat = note.contentFormat,
            isPinned = note.isPinned,
            originalTitle = note.title,
            originalContent = note.content,
            originalIsPinned = note.isPinned,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
        )
    }

    fun onTitleChange(newTitle: String) {
        _editorState.update {
            it.copy(
                title = newTitle,
                showValidationError = false
            )
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

    fun saveNote(onSaved: () -> Unit = {}) {
        val state = _editorState.value
        val trimmedTitle = state.title.trim()
        val trimmedContent = state.content.trim()

        if (trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
            _editorState.update { it.copy(showValidationError = true) }
            return
        }

        val now = clock()
        val noteToSave = NoteEntity(
            id = state.noteId.ifEmpty { UUID.randomUUID().toString() },
            title = trimmedTitle,
            content = trimmedContent,
            contentFormat = state.contentFormat,
            createdAt = if (state.isEditingExisting && state.createdAt > 0L) state.createdAt else now,
            updatedAt = now,
            isPinned = state.isPinned
        )

        viewModelScope.launch {
            repository.saveNote(noteToSave)
            _editorState.value = NoteEditorUiState()
            onSaved()
        }
    }

    fun requestCancelEditor() {
        val state = _editorState.value
        if (state.hasUnsavedChanges) {
            _editorState.update { it.copy(showDiscardConfirmation = true) }
        } else {
            _editorState.value = NoteEditorUiState()
        }
    }

    fun dismissDiscardConfirmation() {
        _editorState.update { it.copy(showDiscardConfirmation = false) }
    }

    fun confirmDiscardChanges() {
        _editorState.value = NoteEditorUiState()
    }

    fun requestDeleteNote() {
        val state = _editorState.value
        if (state.isEditingExisting && state.noteId.isNotEmpty()) {
            _editorState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun dismissDeleteConfirmation() {
        _editorState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteNote(onDeleted: () -> Unit = {}) {
        val state = _editorState.value
        if (!state.isEditingExisting || state.noteId.isEmpty()) return

        viewModelScope.launch {
            repository.deleteNoteById(state.noteId)
            _editorState.value = NoteEditorUiState()
            onDeleted()
        }
    }

    companion object {
        fun provideFactory(repository: NotesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NotesViewModel(repository) as T
                }
            }
    }
}
