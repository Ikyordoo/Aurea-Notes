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

    @Query(
        """
        SELECT * FROM notes
        WHERE TRIM(category) = ''
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeUncategorizedNotesByLastModified(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE TRIM(category) = ''
        ORDER BY is_pinned DESC, created_at DESC
        """
    )
    fun observeUncategorizedNotesByCreatedDate(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE TRIM(category) = ''
        ORDER BY is_pinned DESC, title COLLATE NOCASE ASC, updated_at DESC
        """
    )
    fun observeUncategorizedNotesByTitle(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeNotesByCategoryAndLastModified(category: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))
        ORDER BY is_pinned DESC, created_at DESC
        """
    )
    fun observeNotesByCategoryAndCreatedDate(category: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))
        ORDER BY is_pinned DESC, title COLLATE NOCASE ASC, updated_at DESC
        """
    )
    fun observeNotesByCategoryAndTitle(category: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT DISTINCT TRIM(category)
        FROM notes
        WHERE TRIM(category) != ''
        ORDER BY category COLLATE NOCASE ASC
        """
    )
    fun observeDistinctCategories(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("UPDATE notes SET is_pinned = :isPinned WHERE id = :id")
    suspend fun setNotePinned(id: String, isPinned: Boolean)

    @Query("UPDATE notes SET category = :category WHERE id = :id")
    suspend fun setNoteCategory(id: String, category: String)

    @Query(
        """
        UPDATE notes
        SET category = :newCategory
        WHERE LOWER(TRIM(category)) = LOWER(TRIM(:oldCategory))
        """
    )
    suspend fun renameCategoryInNotes(oldCategory: String, newCategory: String)

    @Query(
        """
        UPDATE notes
        SET category = ''
        WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))
        """
    )
    suspend fun clearCategoryFromNotes(category: String)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)
}
