package com.aurea.notes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a user-created Journal Entry.
 * [title] is optional and may be blank; [createdAt] and [updatedAt] store epoch milliseconds;
 * [isPinned] defaults to false.
 */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false
)
