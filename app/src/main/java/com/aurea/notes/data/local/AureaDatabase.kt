package com.aurea.notes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local Room database for Aurea Notes & Journal.
 * Uses explicit non-destructive migrations so user records are preserved safely across schema updates.
 */
@Database(
    entities = [
        NoteEntity::class,
        JournalEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AureaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        private const val DATABASE_NAME = "aurea_notes_journal.db"

        /**
         * Explicit non-destructive migration from version 1 to version 2.
         * Adds is_pinned column to both notes and journal_entries tables with default 0 (false).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE journal_entries ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: AureaDatabase? = null

        fun getInstance(context: Context): AureaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AureaDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
