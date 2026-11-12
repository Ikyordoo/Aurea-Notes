package com.aurea.notes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    fun observeNotesByLastModified(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, created_at DESC")
    fun observeNotesByCreatedDate(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, title COLLATE NOCASE ASC, updated_at DESC")
    fun observeNotesByTitle(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("UPDATE notes SET is_pinned = :isPinned WHERE id = :id")
    suspend fun setNotePinned(id: String, isPinned: Boolean)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)
}
