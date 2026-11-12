package com.aurea.notes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY is_pinned DESC, created_at DESC")
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): JournalEntryEntity?

    @Query(
        """
        SELECT * FROM journal_entries
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
        ORDER BY is_pinned DESC, created_at DESC
        """
    )
    fun searchEntries(query: String): Flow<List<JournalEntryEntity>>

    @Upsert
    suspend fun upsertEntry(entry: JournalEntryEntity)

    @Query("UPDATE journal_entries SET is_pinned = :isPinned WHERE id = :id")
    suspend fun setEntryPinned(id: String, isPinned: Boolean)

    @Delete
    suspend fun deleteEntry(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)
}
