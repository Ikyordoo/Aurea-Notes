package com.aurea.notes.data.repository

import com.aurea.notes.data.local.JournalEntryDao
import com.aurea.notes.data.local.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>
    fun searchEntries(query: String): Flow<List<JournalEntryEntity>>
    suspend fun getEntryById(id: String): JournalEntryEntity?
    suspend fun saveEntry(entry: JournalEntryEntity)
    suspend fun setEntryPinned(id: String, isPinned: Boolean)
    suspend fun setEntryMood(id: String, mood: String)
    suspend fun deleteEntryById(id: String)
}

class OfflineJournalRepository(
    private val journalEntryDao: JournalEntryDao
) : JournalRepository {
    override fun observeAllEntries(): Flow<List<JournalEntryEntity>> =
        journalEntryDao.observeAllEntries()

    override fun searchEntries(query: String): Flow<List<JournalEntryEntity>> =
        journalEntryDao.searchEntries(query)

    override suspend fun getEntryById(id: String): JournalEntryEntity? =
        journalEntryDao.getEntryById(id)

    override suspend fun saveEntry(entry: JournalEntryEntity) {
        journalEntryDao.upsertEntry(entry)
    }

    override suspend fun setEntryPinned(id: String, isPinned: Boolean) {
        journalEntryDao.setEntryPinned(id, isPinned)
    }

    override suspend fun setEntryMood(id: String, mood: String) {
        journalEntryDao.setEntryMood(id, mood.trim())
    }

    override suspend fun deleteEntryById(id: String) {
        journalEntryDao.deleteEntryById(id)
    }
}
