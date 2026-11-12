package com.aurea.notes.data.repository

import com.aurea.notes.data.local.NoteDao
import com.aurea.notes.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow

enum class NoteSortOrder {
    LAST_MODIFIED,
    CREATED_DATE,
    TITLE_ASC
}

interface NotesRepository {
    fun observeAllNotes(): Flow<List<NoteEntity>>
    fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>>
    fun searchNotes(query: String): Flow<List<NoteEntity>>
    suspend fun getNoteById(id: String): NoteEntity?
    suspend fun saveNote(note: NoteEntity)
    suspend fun setNotePinned(id: String, isPinned: Boolean)
    suspend fun deleteNoteById(id: String)
}

class OfflineNotesRepository(
    private val noteDao: NoteDao
) : NotesRepository {
    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        noteDao.observeAllNotes()

    override fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>> =
        when (sortOrder) {
            NoteSortOrder.LAST_MODIFIED -> noteDao.observeNotesByLastModified()
            NoteSortOrder.CREATED_DATE -> noteDao.observeNotesByCreatedDate()
            NoteSortOrder.TITLE_ASC -> noteDao.observeNotesByTitle()
        }

    override fun searchNotes(query: String): Flow<List<NoteEntity>> =
        noteDao.searchNotes(query)

    override suspend fun getNoteById(id: String): NoteEntity? =
        noteDao.getNoteById(id)

    override suspend fun saveNote(note: NoteEntity) {
        noteDao.upsertNote(note)
    }

    override suspend fun setNotePinned(id: String, isPinned: Boolean) {
        noteDao.setNotePinned(id, isPinned)
    }

    override suspend fun deleteNoteById(id: String) {
        noteDao.deleteNoteById(id)
    }
}
