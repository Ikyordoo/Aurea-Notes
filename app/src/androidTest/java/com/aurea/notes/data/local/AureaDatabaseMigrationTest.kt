package com.aurea.notes.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Genuine Room-level migration instrumentation tests verifying [AureaDatabase.MIGRATION_1_2]
 * and [AureaDatabase.MIGRATION_2_3].
 *
 * Each test:
 * 1. Constructs a real SQLite database file (`testDbName`) at version 1 or version 2 using
 *    [SupportSQLiteOpenHelper] and seeds representative Notes and Journal records.
 * 2. Closes the initial [SupportSQLiteOpenHelper] connection.
 * 3. Reopens the exact same database file through [Room.databaseBuilder] with
 *    [AureaDatabase.MIGRATION_1_2] and [AureaDatabase.MIGRATION_2_3] registered.
 * 4. Forces Room to execute its migration container path and KSP-generated
 *    `AureaDatabase_Impl.onValidateSchema` verification.
 * 5. Verifies both low-level `PRAGMA table_info` metadata and entity reads through
 *    Room's generated [NoteDao] and [JournalEntryDao].
 */
@RunWith(AndroidJUnit4::class)
class AureaDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var testDbName: String
    private var openHelper: SupportSQLiteOpenHelper? = null
    private var migratedRoomDb: AureaDatabase? = null

    private data class ColumnSchemaInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDbName = "aurea_migration_test_${UUID.randomUUID()}.db"
        context.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        try {
            closeInitialHelper()
            migratedRoomDb?.close()
        } finally {
            migratedRoomDb = null
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migrate1To2To3_preservesExistingRecords_andAddsIsPinnedCategoryAndMoodWithDefaults() = runBlocking {
        val initialDb = createVersion1Database()

        // Confirm version 1 schema does not yet contain is_pinned, category, or mood
        val v1NotesColumns = readTableColumns(initialDb, "notes")
        val v1JournalColumns = readTableColumns(initialDb, "journal_entries")
        assertFalse(v1NotesColumns.containsKey("is_pinned"))
        assertFalse(v1NotesColumns.containsKey("category"))
        assertFalse(v1JournalColumns.containsKey("is_pinned"))
        assertFalse(v1JournalColumns.containsKey("mood"))
        assertEquals(6, v1NotesColumns.size)
        assertEquals(5, v1JournalColumns.size)

        // 1. Seed an existing version 1 Note with distinct values
        val noteId = "note-v1-architectural-study"
        val noteTitle = "Limestone & Timber Proportions"
        val noteContent = "Notes on load-bearing masonry and natural light diffusion across the courtyard."
        val noteFormat = NoteEntity.FORMAT_PLAIN_TEXT
        val noteCreatedAt = 1_700_010_000_000L
        val noteUpdatedAt = 1_700_020_500_000L

        initialDb.execSQL(
            """
            INSERT INTO `notes` (`id`, `title`, `content`, `content_format`, `created_at`, `updated_at`)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(noteId, noteTitle, noteContent, noteFormat, noteCreatedAt, noteUpdatedAt)
        )

        // 2. Seed existing version 1 Journal entries (one untitled/empty title, one titled)
        val untitledEntryId = "journal-v1-untitled-dawn"
        val untitledEntryTitle = ""
        val untitledEntryContent = "Quiet morning mist over the valley before sunrise."
        val untitledEntryCreatedAt = 1_700_030_000_000L
        val untitledEntryUpdatedAt = 1_700_030_900_000L

        initialDb.execSQL(
            """
            INSERT INTO `journal_entries` (`id`, `title`, `content`, `created_at`, `updated_at`)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                untitledEntryId,
                untitledEntryTitle,
                untitledEntryContent,
                untitledEntryCreatedAt,
                untitledEntryUpdatedAt
            )
        )

        val titledEntryId = "journal-v1-evening-study"
        val titledEntryTitle = "Autumn Solstice Reflection"
        val titledEntryContent = "Completed the first phase of the studio archive."
        val titledEntryCreatedAt = 1_700_040_000_000L
        val titledEntryUpdatedAt = 1_700_045_000_000L

        initialDb.execSQL(
            """
            INSERT INTO `journal_entries` (`id`, `title`, `content`, `created_at`, `updated_at`)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                titledEntryId,
                titledEntryTitle,
                titledEntryContent,
                titledEntryCreatedAt,
                titledEntryUpdatedAt
            )
        )

        // 3. Close the original version 1 SQLite helper before opening the file through Room
        closeInitialHelper()

        // 4. Open the same physical database file through Room with registered migrations (1 -> 2 -> 3)
        val roomDb = openDatabaseThroughRoom()
        val migratedSqliteDb = roomDb.openHelper.writableDatabase
        assertEquals(3, migratedSqliteDb.version)

        // 5. Verify resulting version 3 schema metadata on both tables via PRAGMA table_info
        assertVersion3Schema(migratedSqliteDb)

        // 6. Verify migrated Note through Room's generated NoteDao
        val noteDao = roomDb.noteDao()
        val allNotes = noteDao.observeAllNotes().first()
        assertEquals(1, allNotes.size)

        val migratedNote = noteDao.getNoteById(noteId)
        assertNotNull(migratedNote)
        assertEquals(noteId, migratedNote!!.id)
        assertEquals(noteTitle, migratedNote.title)
        assertEquals(noteContent, migratedNote.content)
        assertEquals(noteFormat, migratedNote.contentFormat)
        assertEquals(noteCreatedAt, migratedNote.createdAt)
        assertEquals(noteUpdatedAt, migratedNote.updatedAt)
        assertFalse(migratedNote.isPinned)
        assertEquals("", migratedNote.category)

        // 7. Verify migrated Journal entries through Room's generated JournalEntryDao
        val journalDao = roomDb.journalEntryDao()
        val allEntries = journalDao.observeAllEntries().first()
        assertEquals(2, allEntries.size)
        // Newest created_at (titledEntryId) appears first when both are unpinned
        assertEquals(listOf(titledEntryId, untitledEntryId), allEntries.map { it.id })

        val migratedUntitled = journalDao.getEntryById(untitledEntryId)
        assertNotNull(migratedUntitled)
        assertEquals(untitledEntryId, migratedUntitled!!.id)
        assertEquals(untitledEntryTitle, migratedUntitled.title)
        assertEquals(untitledEntryContent, migratedUntitled.content)
        assertEquals(untitledEntryCreatedAt, migratedUntitled.createdAt)
        assertEquals(untitledEntryUpdatedAt, migratedUntitled.updatedAt)
        assertFalse(migratedUntitled.isPinned)
        assertEquals("", migratedUntitled.mood)

        val migratedTitled = journalDao.getEntryById(titledEntryId)
        assertNotNull(migratedTitled)
        assertEquals(titledEntryId, migratedTitled!!.id)
        assertEquals(titledEntryTitle, migratedTitled.title)
        assertEquals(titledEntryContent, migratedTitled.content)
        assertEquals(titledEntryCreatedAt, migratedTitled.createdAt)
        assertEquals(titledEntryUpdatedAt, migratedTitled.updatedAt)
        assertFalse(migratedTitled.isPinned)
        assertEquals("", migratedTitled.mood)
    }

    @Test
    fun migrate2To3_preservesPinnedAndUnpinnedRecords_andAddsCategoryAndMoodWithEmptyStringDefaults() = runBlocking {
        val initialDb = createVersion2Database()

        // Confirm version 2 schema contains is_pinned, but does not yet contain category or mood
        val v2NotesColumns = readTableColumns(initialDb, "notes")
        val v2JournalColumns = readTableColumns(initialDb, "journal_entries")
        assertTrue(v2NotesColumns.containsKey("is_pinned"))
        assertFalse(v2NotesColumns.containsKey("category"))
        assertTrue(v2JournalColumns.containsKey("is_pinned"))
        assertFalse(v2JournalColumns.containsKey("mood"))
        assertEquals(7, v2NotesColumns.size)
        assertEquals(6, v2JournalColumns.size)

        // 1. Seed two version 2 Notes (one pinned = 1, one unpinned = 0)
        val pinnedNoteId = "note-v2-pinned-strategy"
        val pinnedNoteTitle = "Annual Research Roadmap"
        val pinnedNoteContent = "Key milestones for archival indexing and typography studies."
        val pinnedNoteFormat = NoteEntity.FORMAT_PLAIN_TEXT
        val pinnedNoteCreatedAt = 1_700_100_000_000L
        val pinnedNoteUpdatedAt = 1_700_110_000_000L

        initialDb.execSQL(
            """
            INSERT INTO `notes` (`id`, `title`, `content`, `content_format`, `created_at`, `updated_at`, `is_pinned`)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                pinnedNoteId,
                pinnedNoteTitle,
                pinnedNoteContent,
                pinnedNoteFormat,
                pinnedNoteCreatedAt,
                pinnedNoteUpdatedAt,
                1
            )
        )

        val unpinnedNoteId = "note-v2-unpinned-reference"
        val unpinnedNoteTitle = "Botanical Illustrations Catalogue"
        val unpinnedNoteContent = "Collected plates from the 18th-century herbarium."
        val unpinnedNoteFormat = NoteEntity.FORMAT_PLAIN_TEXT
        val unpinnedNoteCreatedAt = 1_700_120_000_000L
        val unpinnedNoteUpdatedAt = 1_700_125_000_000L

        initialDb.execSQL(
            """
            INSERT INTO `notes` (`id`, `title`, `content`, `content_format`, `created_at`, `updated_at`, `is_pinned`)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                unpinnedNoteId,
                unpinnedNoteTitle,
                unpinnedNoteContent,
                unpinnedNoteFormat,
                unpinnedNoteCreatedAt,
                unpinnedNoteUpdatedAt,
                0
            )
        )

        // 2. Seed two version 2 Journal entries (one pinned = 1, one unpinned = 0)
        val pinnedEntryId = "journal-v2-pinned-milestone"
        val pinnedEntryTitle = "First Exhibition Opening"
        val pinnedEntryContent = "A quiet evening sharing the monograph collection."
        val pinnedEntryCreatedAt = 1_700_130_000_000L
        val pinnedEntryUpdatedAt = 1_700_135_000_000L

        initialDb.execSQL(
            """
            INSERT INTO `journal_entries` (`id`, `title`, `content`, `created_at`, `updated_at`, `is_pinned`)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                pinnedEntryId,
                pinnedEntryTitle,
                pinnedEntryContent,
                pinnedEntryCreatedAt,
                pinnedEntryUpdatedAt,
                1
            )
        )

        val unpinnedEntryId = "journal-v2-unpinned-walk"
        val unpinnedEntryTitle = ""
        val unpinnedEntryContent = "Late afternoon walk along the river."
        val unpinnedEntryCreatedAt = 1_700_140_000_000L
        val unpinnedEntryUpdatedAt = 1_700_140_000_000L

        initialDb.execSQL(
            """
            INSERT INTO `journal_entries` (`id`, `title`, `content`, `created_at`, `updated_at`, `is_pinned`)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                unpinnedEntryId,
                unpinnedEntryTitle,
                unpinnedEntryContent,
                unpinnedEntryCreatedAt,
                unpinnedEntryUpdatedAt,
                0
            )
        )

        // 3. Close the original version 2 SQLite helper before opening the file through Room
        closeInitialHelper()

        // 4. Open the same physical database file through Room with registered migrations (2 -> 3)
        val roomDb = openDatabaseThroughRoom()
        val migratedSqliteDb = roomDb.openHelper.writableDatabase
        assertEquals(3, migratedSqliteDb.version)

        // 5. Verify resulting version 3 schema metadata on both tables via PRAGMA table_info
        assertVersion3Schema(migratedSqliteDb)

        // 6. Verify Notes count, pinned-first ordering, fields, pinning state, and empty-string category default via NoteDao
        val noteDao = roomDb.noteDao()
        val observedNotes = noteDao.observeAllNotes().first()
        assertEquals(2, observedNotes.size)
        // Pinned note must appear first despite older updatedAt timestamp
        assertEquals(listOf(pinnedNoteId, unpinnedNoteId), observedNotes.map { it.id })

        val migratedPinnedNote = noteDao.getNoteById(pinnedNoteId)
        assertNotNull(migratedPinnedNote)
        assertEquals(pinnedNoteId, migratedPinnedNote!!.id)
        assertEquals(pinnedNoteTitle, migratedPinnedNote.title)
        assertEquals(pinnedNoteContent, migratedPinnedNote.content)
        assertEquals(pinnedNoteFormat, migratedPinnedNote.contentFormat)
        assertEquals(pinnedNoteCreatedAt, migratedPinnedNote.createdAt)
        assertEquals(pinnedNoteUpdatedAt, migratedPinnedNote.updatedAt)
        assertTrue(migratedPinnedNote.isPinned)
        assertEquals("", migratedPinnedNote.category)

        val migratedUnpinnedNote = noteDao.getNoteById(unpinnedNoteId)
        assertNotNull(migratedUnpinnedNote)
        assertEquals(unpinnedNoteId, migratedUnpinnedNote!!.id)
        assertEquals(unpinnedNoteTitle, migratedUnpinnedNote.title)
        assertEquals(unpinnedNoteContent, migratedUnpinnedNote.content)
        assertEquals(unpinnedNoteFormat, migratedUnpinnedNote.contentFormat)
        assertEquals(unpinnedNoteCreatedAt, migratedUnpinnedNote.createdAt)
        assertEquals(unpinnedNoteUpdatedAt, migratedUnpinnedNote.updatedAt)
        assertFalse(migratedUnpinnedNote.isPinned)
        assertEquals("", migratedUnpinnedNote.category)

        // 7. Verify Journal entries count, pinned-first ordering, fields, pinning state, and empty-string mood default via JournalEntryDao
        val journalDao = roomDb.journalEntryDao()
        val observedEntries = journalDao.observeAllEntries().first()
        assertEquals(2, observedEntries.size)
        // Pinned journal entry must appear first despite older createdAt timestamp
        assertEquals(listOf(pinnedEntryId, unpinnedEntryId), observedEntries.map { it.id })

        val migratedPinnedEntry = journalDao.getEntryById(pinnedEntryId)
        assertNotNull(migratedPinnedEntry)
        assertEquals(pinnedEntryId, migratedPinnedEntry!!.id)
        assertEquals(pinnedEntryTitle, migratedPinnedEntry.title)
        assertEquals(pinnedEntryContent, migratedPinnedEntry.content)
        assertEquals(pinnedEntryCreatedAt, migratedPinnedEntry.createdAt)
        assertEquals(pinnedEntryUpdatedAt, migratedPinnedEntry.updatedAt)
        assertTrue(migratedPinnedEntry.isPinned)
        assertEquals("", migratedPinnedEntry.mood)

        val migratedUnpinnedEntry = journalDao.getEntryById(unpinnedEntryId)
        assertNotNull(migratedUnpinnedEntry)
        assertEquals(unpinnedEntryId, migratedUnpinnedEntry!!.id)
        assertEquals(unpinnedEntryTitle, migratedUnpinnedEntry.title)
        assertEquals(unpinnedEntryContent, migratedUnpinnedEntry.content)
        assertEquals(unpinnedEntryCreatedAt, migratedUnpinnedEntry.createdAt)
        assertEquals(unpinnedEntryUpdatedAt, migratedUnpinnedEntry.updatedAt)
        assertFalse(migratedUnpinnedEntry.isPinned)
        assertEquals("", migratedUnpinnedEntry.mood)
    }

    private fun closeInitialHelper() {
        openHelper?.close()
        openHelper = null
    }

    private fun openDatabaseThroughRoom(): AureaDatabase {
        val db = Room.databaseBuilder(
            context,
            AureaDatabase::class.java,
            testDbName
        )
            .addMigrations(
                AureaDatabase.MIGRATION_1_2,
                AureaDatabase.MIGRATION_2_3
            )
            .allowMainThreadQueries()
            .build()
        migratedRoomDb = db
        return db
    }

    private fun assertVersion3Schema(db: SupportSQLiteDatabase) {
        val v3NotesColumns = readTableColumns(db, "notes")
        assertEquals(
            setOf(
                "id",
                "title",
                "content",
                "content_format",
                "created_at",
                "updated_at",
                "is_pinned",
                "category"
            ),
            v3NotesColumns.keys
        )
        assertColumnSchema(
            column = v3NotesColumns["id"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = true
        )
        assertColumnSchema(
            column = v3NotesColumns["title"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["content"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["content_format"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["created_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["updated_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["is_pinned"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = "0",
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3NotesColumns["category"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = "''",
            expectedPrimaryKey = false
        )

        val v3JournalColumns = readTableColumns(db, "journal_entries")
        assertEquals(
            setOf("id", "title", "content", "created_at", "updated_at", "is_pinned", "mood"),
            v3JournalColumns.keys
        )
        assertColumnSchema(
            column = v3JournalColumns["id"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = true
        )
        assertColumnSchema(
            column = v3JournalColumns["title"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3JournalColumns["content"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3JournalColumns["created_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3JournalColumns["updated_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3JournalColumns["is_pinned"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = "0",
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v3JournalColumns["mood"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = "''",
            expectedPrimaryKey = false
        )
    }

    private fun createVersion1Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `content_format` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                // Handled by Room.databaseBuilder when reopening testDbName
            }
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(callback)
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        openHelper = helper
        return helper.writableDatabase
    }

    private fun createVersion2Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `content_format` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `is_pinned` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `is_pinned` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                // Handled by Room.databaseBuilder when reopening testDbName
            }
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(callback)
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        openHelper = helper
        return helper.writableDatabase
    }

    private fun readTableColumns(
        db: SupportSQLiteDatabase,
        tableName: String
    ): Map<String, ColumnSchemaInfo> {
        val columns = mutableMapOf<String, ColumnSchemaInfo>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
            val defaultIdx = cursor.getColumnIndexOrThrow("dflt_value")
            val pkIdx = cursor.getColumnIndexOrThrow("pk")

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val type = cursor.getString(typeIdx)
                val notNull = cursor.getInt(notNullIdx) == 1
                val defaultValue = if (cursor.isNull(defaultIdx)) null else cursor.getString(defaultIdx)
                val isPrimaryKey = cursor.getInt(pkIdx) > 0

                columns[name] = ColumnSchemaInfo(
                    name = name,
                    type = type,
                    notNull = notNull,
                    defaultValue = defaultValue,
                    isPrimaryKey = isPrimaryKey
                )
            }
        }
        return columns
    }

    private fun assertColumnSchema(
        column: ColumnSchemaInfo?,
        expectedType: String,
        expectedNotNull: Boolean,
        expectedDefault: String?,
        expectedPrimaryKey: Boolean
    ) {
        assertNotNull("Expected column to exist in table schema", column)
        val actual = column!!
        assertTrue(
            "Expected SQLite type $expectedType for column ${actual.name}, but was ${actual.type}",
            actual.type.equals(expectedType, ignoreCase = true)
        )
        assertEquals(
            "Unexpected NOT NULL constraint for column ${actual.name}",
            expectedNotNull,
            actual.notNull
        )
        assertEquals(
            "Unexpected DEFAULT value for column ${actual.name}",
            expectedDefault,
            actual.defaultValue
        )
        assertEquals(
            "Unexpected PRIMARY KEY flag for column ${actual.name}",
            expectedPrimaryKey,
            actual.isPrimaryKey
        )
    }
}
