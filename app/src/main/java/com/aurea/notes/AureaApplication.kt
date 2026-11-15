package com.aurea.notes

import android.app.Application
import com.aurea.notes.data.local.AureaDatabase
import com.aurea.notes.data.repository.JournalRepository
import com.aurea.notes.data.repository.NotesRepository
import com.aurea.notes.data.repository.OfflineJournalRepository
import com.aurea.notes.data.repository.OfflineNotesRepository
import com.aurea.notes.data.repository.SharedPreferencesNoteCategoryStore

class AureaApplication : Application() {
    val database: AureaDatabase by lazy {
        AureaDatabase.getInstance(this)
    }

    val notesRepository: NotesRepository by lazy {
        OfflineNotesRepository(
            noteDao = database.noteDao(),
            categoryStore = SharedPreferencesNoteCategoryStore(this)
        )
    }

    val journalRepository: JournalRepository by lazy {
        OfflineJournalRepository(database.journalEntryDao())
    }
}
