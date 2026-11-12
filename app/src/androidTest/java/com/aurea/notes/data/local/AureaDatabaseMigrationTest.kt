package com.aurea.notes.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Genuine SQLite migration test verifying [AureaDatabase.MIGRATION_1_2].
 *
 * Constructs a real version 1 SQLite database using the exact Stage 2.1 schema,
 * seeds existing user notes and journal entries, executes the production
 * [AureaDatabase.MIGRATION_1_2] migration object, and verifies both row preservation
 * and version 2 table schema metadata via `PRAGMA table_info`.
 */
@RunWith(AndroidJUnit4::class)
class AureaDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var testDbName: String
    private var openHelper: SupportSQLiteOpenHelper? = null

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
            openHelper?.close()
        } finally {
            openHelper = null
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migrate1To2_preservesExistingNotesAndJournalEntries_andAddsNotNullIsPinnedWithDefaultZero() {
        val db = createVersion1Database()

        // Confirm version 1 schema does not yet contain is_pinned on either table
        val v1NotesColumns = readTableColumns(db, "notes")
        val v1JournalColumns = readTableColumns(db, "journal_entries")
        assertFalse(v1NotesColumns.containsKey("is_pinned"))
        assertFalse(v1JournalColumns.containsKey("is_pinned"))
        assertEquals(6, v1NotesColumns.size)
        assertEquals(5, v1JournalColumns.size)

        // 1. Insert an existing version 1 Note with distinct values
        val noteId = "note-v1-architectural-study"
        val noteTitle = "Limestone & Timber Proportions"
        val noteContent = "Notes on load-bearing masonry and natural light diffusion across the courtyard."
        val noteFormat = NoteEntity.FORMAT_PLAIN_TEXT
        val noteCreatedAt = 1_700_010_000_000L
        val noteUpdatedAt = 1_700_020_500_000L

        db.execSQL(
            """
            INSERT INTO `notes` (`id`, `title`, `content`, `content_format`, `created_at`, `updated_at`)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(noteId, noteTitle, noteContent, noteFormat, noteCreatedAt, noteUpdatedAt)
        )

        // 2. Insert existing version 1 Journal entries (one untitled/empty title, one titled)
        val untitledEntryId = "journal-v1-untitled-dawn"
        val untitledEntryTitle = ""
        val untitledEntryContent = "Quiet morning mist over the valley before sunrise."
        val untitledEntryCreatedAt = 1_700_030_000_000L
        val untitledEntryUpdatedAt = 1_700_030_900_000L

        db.execSQL(
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

        db.execSQL(
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

        // 3. Execute the actual production Room Migration (1 -> 2)
        AureaDatabase.MIGRATION_1_2.migrate(db)

        // 4. Verify resulting version 2 schema on `notes` via PRAGMA table_info
        val v2NotesColumns = readTableColumns(db, "notes")
        assertEquals(
            setOf("id", "title", "content", "content_format", "created_at", "updated_at", "is_pinned"),
            v2NotesColumns.keys
        )
        assertColumnSchema(
            column = v2NotesColumns["id"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = true
        )
        assertColumnSchema(
            column = v2NotesColumns["title"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2NotesColumns["content"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2NotesColumns["content_format"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2NotesColumns["created_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2NotesColumns["updated_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2NotesColumns["is_pinned"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = "0",
            expectedPrimaryKey = false
        )

        // 5. Verify resulting version 2 schema on `journal_entries` via PRAGMA table_info
        val v2JournalColumns = readTableColumns(db, "journal_entries")
        assertEquals(
            setOf("id", "title", "content", "created_at", "updated_at", "is_pinned"),
            v2JournalColumns.keys
        )
        assertColumnSchema(
            column = v2JournalColumns["id"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = true
        )
        assertColumnSchema(
            column = v2JournalColumns["title"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2JournalColumns["content"],
            expectedType = "TEXT",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2JournalColumns["created_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2JournalColumns["updated_at"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = null,
            expectedPrimaryKey = false
        )
        assertColumnSchema(
            column = v2JournalColumns["is_pinned"],
            expectedType = "INTEGER",
            expectedNotNull = true,
            expectedDefault = "0",
            expectedPrimaryKey = false
        )

        // 6. Verify existing Note survived intact with is_pinned = 0
        db.query(
            "SELECT `id`, `title`, `content`, `content_format`, `created_at`, `updated_at`, `is_pinned` FROM `notes` WHERE `id` = ?",
            arrayOf<Any>(noteId)
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(noteId, cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(noteTitle, cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(noteContent, cursor.getString(cursor.getColumnIndexOrThrow("content")))
            assertEquals(noteFormat, cursor.getString(cursor.getColumnIndexOrThrow("content_format")))
            assertEquals(noteCreatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
            assertEquals(noteUpdatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))

            val isPinnedIndex = cursor.getColumnIndexOrThrow("is_pinned")
            assertFalse(cursor.isNull(isPinnedIndex))
            assertEquals(0, cursor.getInt(isPinnedIndex))
        }

        // 7. Verify existing Journal entries survived intact with is_pinned = 0
        db.query(
            "SELECT `id`, `title`, `content`, `created_at`, `updated_at`, `is_pinned` FROM `journal_entries` ORDER BY `created_at` ASC"
        ).use { cursor ->
            assertEquals(2, cursor.count)

            // First entry: untitled journal entry
            assertTrue(cursor.moveToFirst())
            assertEquals(untitledEntryId, cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(untitledEntryTitle, cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(untitledEntryContent, cursor.getString(cursor.getColumnIndexOrThrow("content")))
            assertEquals(untitledEntryCreatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
            assertEquals(untitledEntryUpdatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
            val untitledPinnedIdx = cursor.getColumnIndexOrThrow("is_pinned")
            assertFalse(cursor.isNull(untitledPinnedIdx))
            assertEquals(0, cursor.getInt(untitledPinnedIdx))

            // Second entry: titled journal entry
            assertTrue(cursor.moveToNext())
            assertEquals(titledEntryId, cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(titledEntryTitle, cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(titledEntryContent, cursor.getString(cursor.getColumnIndexOrThrow("content")))
            assertEquals(titledEntryCreatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
            assertEquals(titledEntryUpdatedAt, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
            val titledPinnedIdx = cursor.getColumnIndexOrThrow("is_pinned")
            assertFalse(cursor.isNull(titledPinnedIdx))
            assertEquals(0, cursor.getInt(titledPinnedIdx))
        }
    }

    private fun createVersion1Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Exact Stage 2.1 Room version 1 schema for `notes`
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

                // Exact Stage 2.1 Room version 1 schema for `journal_entries`
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
                // Handled explicitly in test via AureaDatabase.MIGRATION_1_2.migrate(db)
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
