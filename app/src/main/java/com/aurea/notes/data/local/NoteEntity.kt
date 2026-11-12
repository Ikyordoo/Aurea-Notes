package com.aurea.notes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a user-created Note.
 * Includes [contentFormat] ("PLAIN_TEXT" by default) for rich-text editor architecture readiness,
 * and [isPinned] (defaults to false) for pinning important notes to the top of lists.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "content_format")
    val contentFormat: String = FORMAT_PLAIN_TEXT,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false
) {
    companion object {
        const val FORMAT_PLAIN_TEXT = "PLAIN_TEXT"
        const val FORMAT_RICH_TEXT = "RICH_TEXT"
    }
}
