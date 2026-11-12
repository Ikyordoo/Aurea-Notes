package com.aurea.notes

import android.app.Application
import com.aurea.notes.data.local.AureaDatabase
import com.aurea.notes.data.repository.JournalRepository
import com.aurea.notes.data.repository.NotesRepository
import com.aurea.notes.data.repository.OfflineJournalRepository
import com.aurea.notes.data.repository.OfflineNotesRepository

class AureaApplication : Application() {
    val database: AureaDatabase by lazy {
        AureaDatabase.getInstance(this)
    }

    val notesRepository: NotesRepository by lazy {
        OfflineNotesRepository(database.noteDao())
    }

    val journalRepository: JournalRepository by lazy {
        OfflineJournalRepository(database.journalEntryDao())
    }
}
