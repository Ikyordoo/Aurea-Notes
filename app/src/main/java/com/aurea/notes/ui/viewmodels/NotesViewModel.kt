package com.aurea.notes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.CategoryOperationResult
import com.aurea.notes.data.repository.NoteCategoryFilter
import com.aurea.notes.data.repository.NoteSortOrder
import com.aurea.notes.data.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val category: String = "",
    val isPinned: Boolean = false,
    val originalTitle: String = "",
    val originalContent: String = "",
    val originalCategory: String = "",
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
                category.trim() != originalCategory.trim() ||
                isPinned != originalIsPinned
            )
}

data class CategoryManagerUiState(
    val isOpen: Boolean = false,
    val newCategoryInput: String = "",
    val renamingCategory: String? = null,
    val renameInput: String = "",
    val deletingCategory: String? = null,
    val errorReason: CategoryOperationResult.InvalidName.Reason? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NotesRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(NoteSortOrder.LAST_MODIFIED)
    val sortOrder: StateFlow<NoteSortOrder> = _sortOrder.asStateFlow()

    private val _categoryFilter = MutableStateFlow<NoteCategoryFilter>(NoteCategoryFilter.All)
    val categoryFilter: StateFlow<NoteCategoryFilter> = _categoryFilter.asStateFlow()

    val categories: StateFlow<List<String>> = repository
        .observeCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val allNotes: StateFlow<List<NoteEntity>> = repository
        .observeAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val notes: StateFlow<List<NoteEntity>> = combine(
        _sortOrder,
        _categoryFilter
    ) { order, filter ->
        order to filter
    }
        .flatMapLatest { (order, filter) ->
            repository.observeNotes(order, filter)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(NoteEditorUiState())
    val editorState: StateFlow<NoteEditorUiState> = _editorState.asStateFlow()

    private val _categoryManagerState = MutableStateFlow(CategoryManagerUiState())
    val categoryManagerState: StateFlow<CategoryManagerUiState> = _categoryManagerState.asStateFlow()

    fun setSortOrder(order: NoteSortOrder) {
        _sortOrder.value = order
    }

    fun setCategoryFilter(filter: NoteCategoryFilter) {
        _categoryFilter.value = filter
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
            category = "",
            isPinned = false,
            originalTitle = "",
            originalContent = "",
            originalCategory = "",
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
            category = note.category,
            isPinned = note.isPinned,
            originalTitle = note.title,
            originalContent = note.content,
            originalCategory = note.category,
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

    fun onCategoryChange(newCategory: String) {
        _editorState.update {
            it.copy(category = newCategory.trim())
        }
    }

    fun clearEditorCategory() {
        _editorState.update {
            it.copy(category = "")
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
        val trimmedCategory = state.category.trim()

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
            isPinned = state.isPinned,
            category = trimmedCategory
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

    // Category Management Actions
    fun openCategoryManager() {
        _categoryManagerState.value = CategoryManagerUiState(isOpen = true)
    }

    fun closeCategoryManager() {
        _categoryManagerState.value = CategoryManagerUiState(isOpen = false)
    }

    fun onNewCategoryInputChange(input: String) {
        _categoryManagerState.update {
            it.copy(
                newCategoryInput = input,
                errorReason = null
            )
        }
    }

    fun createCategory(
        name: String = _categoryManagerState.value.newCategoryInput,
        onResult: (CategoryOperationResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.createCategory(name)
            when (result) {
                is CategoryOperationResult.Success -> {
                    _categoryManagerState.update {
                        it.copy(
                            newCategoryInput = "",
                            errorReason = null
                        )
                    }
                }
                is CategoryOperationResult.InvalidName -> {
                    _categoryManagerState.update {
                        it.copy(errorReason = result.reason)
                    }
                }
            }
            onResult(result)
        }
    }

    fun startRenamingCategory(category: String) {
        _categoryManagerState.update {
            it.copy(
                renamingCategory = category,
                renameInput = category,
                errorReason = null
            )
        }
    }

    fun onRenameInputChange(input: String) {
        _categoryManagerState.update {
            it.copy(
                renameInput = input,
                errorReason = null
            )
        }
    }

    fun cancelRenamingCategory() {
        _categoryManagerState.update {
            it.copy(
                renamingCategory = null,
                renameInput = "",
                errorReason = null
            )
        }
    }

    fun renameCategory(
        oldName: String = _categoryManagerState.value.renamingCategory.orEmpty(),
        newName: String = _categoryManagerState.value.renameInput,
        onResult: (CategoryOperationResult) -> Unit = {}
    ) {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        if (trimmedOld.isEmpty()) return

        viewModelScope.launch {
            val result = repository.renameCategory(trimmedOld, trimmedNew)
            when (result) {
                is CategoryOperationResult.Success -> {
                    val activeFilter = _categoryFilter.value
                    if (activeFilter is NoteCategoryFilter.Category &&
                        activeFilter.name.equals(trimmedOld, ignoreCase = true)
                    ) {
                        _categoryFilter.value = NoteCategoryFilter.Category(trimmedNew)
                    }
                    _editorState.update { state ->
                        val updatedCat = if (state.category.equals(trimmedOld, ignoreCase = true)) {
                            trimmedNew
                        } else {
                            state.category
                        }
                        val updatedOrigCat = if (state.originalCategory.equals(trimmedOld, ignoreCase = true)) {
                            trimmedNew
                        } else {
                            state.originalCategory
                        }
                        state.copy(
                            category = updatedCat,
                            originalCategory = updatedOrigCat
                        )
                    }
                    _categoryManagerState.update {
                        it.copy(
                            renamingCategory = null,
                            renameInput = "",
                            errorReason = null
                        )
                    }
                }
                is CategoryOperationResult.InvalidName -> {
                    _categoryManagerState.update {
                        it.copy(errorReason = result.reason)
                    }
                }
            }
            onResult(result)
        }
    }

    fun requestDeleteCategory(category: String) {
        _categoryManagerState.update {
            it.copy(
                deletingCategory = category,
                errorReason = null
            )
        }
    }

    fun dismissDeleteCategoryConfirmation() {
        _categoryManagerState.update {
            it.copy(deletingCategory = null)
        }
    }

    fun confirmDeleteCategory(
        category: String? = _categoryManagerState.value.deletingCategory,
        onDeleted: () -> Unit = {}
    ) {
        val target = category?.trim().orEmpty()
        if (target.isEmpty()) return

        viewModelScope.launch {
            repository.deleteCategory(target)
            val activeFilter = _categoryFilter.value
            if (activeFilter is NoteCategoryFilter.Category &&
                activeFilter.name.equals(target, ignoreCase = true)
            ) {
                _categoryFilter.value = NoteCategoryFilter.All
            }
            _editorState.update { state ->
                val updatedCat = if (state.category.equals(target, ignoreCase = true)) {
                    ""
                } else {
                    state.category
                }
                val updatedOrigCat = if (state.originalCategory.equals(target, ignoreCase = true)) {
                    ""
                } else {
                    state.originalCategory
                }
                state.copy(
                    category = updatedCat,
                    originalCategory = updatedOrigCat
                )
            }
            _categoryManagerState.update {
                it.copy(
                    deletingCategory = null,
                    renamingCategory = if (it.renamingCategory?.equals(target, ignoreCase = true) == true) {
                        null
                    } else {
                        it.renamingCategory
                    },
                    errorReason = null
                )
            }
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
