package com.aurea.notes.data.repository

import android.content.Context
import com.aurea.notes.data.local.NoteDao
import com.aurea.notes.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONArray

enum class NoteSortOrder {
    LAST_MODIFIED,
    CREATED_DATE,
    TITLE_ASC
}

sealed interface NoteCategoryFilter {
    data object All : NoteCategoryFilter
    data object Uncategorized : NoteCategoryFilter
    data class Category(val name: String) : NoteCategoryFilter
}

sealed interface CategoryOperationResult {
    data object Success : CategoryOperationResult
    data class InvalidName(val reason: Reason) : CategoryOperationResult {
        enum class Reason {
            EMPTY,
            TOO_LONG,
            DUPLICATE,
            RESERVED
        }
    }
}

interface NoteCategoryStore {
    fun getSavedCategories(): List<String>
    fun saveCategories(categories: List<String>)
}

class InMemoryNoteCategoryStore(
    initialCategories: List<String> = emptyList()
) : NoteCategoryStore {
    private var categories: List<String> = initialCategories

    override fun getSavedCategories(): List<String> = categories

    override fun saveCategories(categories: List<String>) {
        this.categories = categories
    }
}

class SharedPreferencesNoteCategoryStore(
    context: Context
) : NoteCategoryStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getSavedCategories(): List<String> {
        val rawJson = prefs.getString(KEY_CATEGORIES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) {
                        add(item)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveCategories(categories: List<String>) {
        val array = JSONArray()
        categories.forEach { category ->
            val trimmed = category.trim()
            if (trimmed.isNotEmpty()) {
                array.put(trimmed)
            }
        }
        prefs.edit().putString(KEY_CATEGORIES_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "aurea_note_categories_prefs"
        private const val KEY_CATEGORIES_JSON = "saved_note_categories_v1"
    }
}

interface NotesRepository {
    fun observeAllNotes(): Flow<List<NoteEntity>>
    fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>>
    fun observeNotes(sortOrder: NoteSortOrder, category: String): Flow<List<NoteEntity>>
    fun observeNotes(
        sortOrder: NoteSortOrder,
        filter: NoteCategoryFilter
    ): Flow<List<NoteEntity>>
    fun observeCategories(): Flow<List<String>>
    fun searchNotes(query: String): Flow<List<NoteEntity>>
    suspend fun getNoteById(id: String): NoteEntity?
    suspend fun saveNote(note: NoteEntity)
    suspend fun setNotePinned(id: String, isPinned: Boolean)
    suspend fun setNoteCategory(id: String, category: String)
    suspend fun createCategory(name: String): CategoryOperationResult
    suspend fun renameCategory(oldName: String, newName: String): CategoryOperationResult
    suspend fun deleteCategory(name: String)
    suspend fun deleteNoteById(id: String)

    companion object {
        const val MAX_CATEGORY_NAME_LENGTH = 32
        private val RESERVED_NAMES = setOf("all", "all notes", "uncategorized", "none")

        fun validateCategoryName(
            candidate: String,
            existingCategories: List<String>,
            ignoringCategory: String? = null
        ): CategoryOperationResult {
            val trimmed = candidate.trim()
            if (trimmed.isEmpty()) {
                return CategoryOperationResult.InvalidName(
                    CategoryOperationResult.InvalidName.Reason.EMPTY
                )
            }
            if (trimmed.length > MAX_CATEGORY_NAME_LENGTH) {
                return CategoryOperationResult.InvalidName(
                    CategoryOperationResult.InvalidName.Reason.TOO_LONG
                )
            }
            if (RESERVED_NAMES.contains(trimmed.lowercase())) {
                return CategoryOperationResult.InvalidName(
                    CategoryOperationResult.InvalidName.Reason.RESERVED
                )
            }
            val isDuplicate = existingCategories.any { existing ->
                val matchesCandidate = existing.trim().equals(trimmed, ignoreCase = true)
                val isIgnoredSelf = ignoringCategory != null &&
                    existing.trim().equals(ignoringCategory.trim(), ignoreCase = true)
                matchesCandidate && !isIgnoredSelf
            }
            if (isDuplicate) {
                return CategoryOperationResult.InvalidName(
                    CategoryOperationResult.InvalidName.Reason.DUPLICATE
                )
            }
            return CategoryOperationResult.Success
        }

        fun mergeDistinctCategories(
            storedCategories: List<String>,
            noteCategories: List<String>
        ): List<String> {
            val merged = mutableListOf<String>()
            (storedCategories + noteCategories).forEach { raw ->
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty() &&
                    merged.none { it.equals(trimmed, ignoreCase = true) }
                ) {
                    merged.add(trimmed)
                }
            }
            return merged.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }
}

class OfflineNotesRepository(
    private val noteDao: NoteDao,
    private val categoryStore: NoteCategoryStore = InMemoryNoteCategoryStore()
) : NotesRepository {

    private val storedCategoriesFlow = MutableStateFlow(categoryStore.getSavedCategories())

    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        noteDao.observeAllNotes()

    override fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>> =
        observeNotes(sortOrder = sortOrder, filter = NoteCategoryFilter.All)

    override fun observeNotes(
        sortOrder: NoteSortOrder,
        category: String
    ): Flow<List<NoteEntity>> {
        val trimmedCategory = category.trim()
        val filter = if (trimmedCategory.isEmpty()) {
            NoteCategoryFilter.All
        } else {
            NoteCategoryFilter.Category(trimmedCategory)
        }
        return observeNotes(sortOrder = sortOrder, filter = filter)
    }

    override fun observeNotes(
        sortOrder: NoteSortOrder,
        filter: NoteCategoryFilter
    ): Flow<List<NoteEntity>> {
        return when (filter) {
            is NoteCategoryFilter.All -> when (sortOrder) {
                NoteSortOrder.LAST_MODIFIED -> noteDao.observeNotesByLastModified()
                NoteSortOrder.CREATED_DATE -> noteDao.observeNotesByCreatedDate()
                NoteSortOrder.TITLE_ASC -> noteDao.observeNotesByTitle()
            }
            is NoteCategoryFilter.Uncategorized -> when (sortOrder) {
                NoteSortOrder.LAST_MODIFIED -> noteDao.observeUncategorizedNotesByLastModified()
                NoteSortOrder.CREATED_DATE -> noteDao.observeUncategorizedNotesByCreatedDate()
                NoteSortOrder.TITLE_ASC -> noteDao.observeUncategorizedNotesByTitle()
            }
            is NoteCategoryFilter.Category -> {
                val target = filter.name.trim()
                if (target.isEmpty()) {
                    observeNotes(sortOrder, NoteCategoryFilter.All)
                } else {
                    when (sortOrder) {
                        NoteSortOrder.LAST_MODIFIED ->
                            noteDao.observeNotesByCategoryAndLastModified(target)
                        NoteSortOrder.CREATED_DATE ->
                            noteDao.observeNotesByCategoryAndCreatedDate(target)
                        NoteSortOrder.TITLE_ASC ->
                            noteDao.observeNotesByCategoryAndTitle(target)
                    }
                }
            }
        }
    }

    override fun observeCategories(): Flow<List<String>> =
        combine(
            storedCategoriesFlow,
            noteDao.observeDistinctCategories()
        ) { stored, fromNotes ->
            NotesRepository.mergeDistinctCategories(stored, fromNotes)
        }

    override fun searchNotes(query: String): Flow<List<NoteEntity>> =
        noteDao.searchNotes(query)

    override suspend fun getNoteById(id: String): NoteEntity? =
        noteDao.getNoteById(id)

    override suspend fun saveNote(note: NoteEntity) {
        val trimmedCategory = note.category.trim()
        noteDao.upsertNote(note.copy(category = trimmedCategory))
        if (trimmedCategory.isNotEmpty()) {
            val current = storedCategoriesFlow.value
            if (current.none { it.equals(trimmedCategory, ignoreCase = true) }) {
                val updated = NotesRepository.mergeDistinctCategories(
                    current + trimmedCategory,
                    emptyList()
                )
                categoryStore.saveCategories(updated)
                storedCategoriesFlow.value = updated
            }
        }
    }

    override suspend fun setNotePinned(id: String, isPinned: Boolean) {
        noteDao.setNotePinned(id, isPinned)
    }

    override suspend fun setNoteCategory(id: String, category: String) {
        val trimmedCategory = category.trim()
        noteDao.setNoteCategory(id, trimmedCategory)
        if (trimmedCategory.isNotEmpty()) {
            val current = storedCategoriesFlow.value
            if (current.none { it.equals(trimmedCategory, ignoreCase = true) }) {
                val updated = NotesRepository.mergeDistinctCategories(
                    current + trimmedCategory,
                    emptyList()
                )
                categoryStore.saveCategories(updated)
                storedCategoriesFlow.value = updated
            }
        }
    }

    override suspend fun createCategory(name: String): CategoryOperationResult {
        val current = observeCategories().first()
        val validation = NotesRepository.validateCategoryName(name, current)
        if (validation !is CategoryOperationResult.Success) {
            return validation
        }
        val trimmed = name.trim()
        val updated = NotesRepository.mergeDistinctCategories(
            current + trimmed,
            emptyList()
        )
        categoryStore.saveCategories(updated)
        storedCategoriesFlow.value = updated
        return CategoryOperationResult.Success
    }

    override suspend fun renameCategory(
        oldName: String,
        newName: String
    ): CategoryOperationResult {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        val current = observeCategories().first()
        val validation = NotesRepository.validateCategoryName(
            candidate = trimmedNew,
            existingCategories = current,
            ignoringCategory = trimmedOld
        )
        if (validation !is CategoryOperationResult.Success) {
            return validation
        }
        noteDao.renameCategoryInNotes(trimmedOld, trimmedNew)
        val updated = NotesRepository.mergeDistinctCategories(
            current.map { existing ->
                if (existing.equals(trimmedOld, ignoreCase = true)) trimmedNew else existing
            } + trimmedNew,
            emptyList()
        )
        categoryStore.saveCategories(updated)
        storedCategoriesFlow.value = updated
        return CategoryOperationResult.Success
    }

    override suspend fun deleteCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        noteDao.clearCategoryFromNotes(trimmed)
        val updated = storedCategoriesFlow.value.filterNot {
            it.equals(trimmed, ignoreCase = true)
        }
        categoryStore.saveCategories(updated)
        storedCategoriesFlow.value = updated
    }

    override suspend fun deleteNoteById(id: String) {
        noteDao.deleteNoteById(id)
    }
}
