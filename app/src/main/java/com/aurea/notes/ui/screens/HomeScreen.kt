package com.aurea.notes.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurea.notes.R
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.ui.components.formatEditorialTimestamp
import com.aurea.notes.ui.theme.DeepInk
import com.aurea.notes.ui.theme.EspressoAccent
import com.aurea.notes.ui.theme.WarmPaper
import com.aurea.notes.ui.theme.WarmParchment

private sealed interface HomeHighlightItem {
    val idKey: String
    val isPinned: Boolean
    val sortTimestamp: Long

    data class NoteHighlight(val note: NoteEntity) : HomeHighlightItem {
        override val idKey: String = "note-${note.id}"
        override val isPinned: Boolean = note.isPinned
        override val sortTimestamp: Long = note.updatedAt
    }

    data class JournalHighlight(val entry: JournalEntryEntity) : HomeHighlightItem {
        override val idKey: String = "journal-${entry.id}"
        override val isPinned: Boolean = entry.isPinned
        override val sortTimestamp: Long = entry.createdAt
    }
}

@Composable
fun HomeScreen(
    notes: List<NoteEntity> = emptyList(),
    journalEntries: List<JournalEntryEntity> = emptyList(),
    onOpenNotes: () -> Unit,
    onOpenJournal: () -> Unit,
    onSelectNote: (NoteEntity) -> Unit = {},
    onSelectJournalEntry: (JournalEntryEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Up to 3 relevant user-created items: pinned first, then most recent, no duplicates
    val highlights: List<HomeHighlightItem> = remember(notes, journalEntries) {
        val combined = buildList {
            notes.forEach { add(HomeHighlightItem.NoteHighlight(it)) }
            journalEntries.forEach { add(HomeHighlightItem.JournalHighlight(it)) }
        }
        combined
            .distinctBy { it.idKey }
            .sortedWith(
                compareByDescending<HomeHighlightItem> { it.isPinned }
                    .thenByDescending { it.sortTimestamp }
            )
            .take(3)
    }

    val notesMetaText = if (notes.isNotEmpty()) {
        stringResource(id = R.string.option_notes_meta_count, notes.size)
    } else {
        stringResource(id = R.string.option_notes_meta)
    }

    val journalMetaText = if (journalEntries.isNotEmpty()) {
        stringResource(id = R.string.option_journal_meta_count, journalEntries.size)
    } else {
        stringResource(id = R.string.option_journal_meta)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        // Editorial Masthead: Aurea Branding & Tagline
        Text(
            text = stringResource(id = R.string.brand_name),
            style = MaterialTheme.typography.displayLarge,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(id = R.string.tagline),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
        Spacer(modifier = Modifier.height(28.dp))

        // Two Prominent Options: Notes & Journal
        EditorialOptionCard(
            title = stringResource(id = R.string.option_notes),
            subtitle = stringResource(id = R.string.option_notes_subtitle),
            metaLine = notesMetaText,
            icon = Icons.Outlined.Description,
            isPrimary = true,
            onClick = onOpenNotes
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditorialOptionCard(
            title = stringResource(id = R.string.option_journal),
            subtitle = stringResource(id = R.string.option_journal_subtitle),
            metaLine = journalMetaText,
            icon = Icons.Outlined.Book,
            isPrimary = false,
            onClick = onOpenJournal
        )

        // Pinned & Recent Section — Shown only when user-created records exist
        if (highlights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(id = R.string.home_pinned_recent_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                highlights.forEach { item ->
                    when (item) {
                        is HomeHighlightItem.NoteHighlight -> {
                            val note = item.note
                            val metaPrefix = buildString {
                                if (note.isPinned) {
                                    append(stringResource(id = R.string.label_pinned))
                                    append(" · ")
                                }
                                append(stringResource(id = R.string.notes_title))
                                append(" · ")
                                append(formatEditorialTimestamp(note.updatedAt))
                            }
                            HomeHighlightCard(
                                metaLine = metaPrefix,
                                title = note.title,
                                preview = note.content,
                                onClick = { onSelectNote(note) }
                            )
                        }
                        is HomeHighlightItem.JournalHighlight -> {
                            val entry = item.entry
                            val displayTitle = entry.title.ifBlank {
                                stringResource(id = R.string.journal_untitled_fallback)
                            }
                            val metaPrefix = buildString {
                                if (entry.isPinned) {
                                    append(stringResource(id = R.string.label_pinned))
                                    append(" · ")
                                }
                                append(stringResource(id = R.string.journal_title))
                                append(" · ")
                                append(formatEditorialTimestamp(entry.createdAt))
                            }
                            HomeHighlightCard(
                                metaLine = metaPrefix,
                                title = displayTitle,
                                preview = entry.content,
                                onClick = { onSelectJournalEntry(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHighlightCard(
    metaLine: String,
    title: String,
    preview: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditorialOptionCard(
    title: String,
    subtitle: String,
    metaLine: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    val containerColor = if (isPrimary) {
        if (isDark) colorScheme.primaryContainer else EspressoAccent
    } else {
        colorScheme.surface
    }
    val titleColor = if (isPrimary) {
        if (isDark) colorScheme.onPrimaryContainer else WarmPaper
    } else {
        colorScheme.onSurface
    }
    val bodyColor = if (isPrimary) {
        WarmParchment
    } else {
        colorScheme.secondary
    }
    val metaColor = if (isPrimary) {
        WarmParchment
    } else {
        colorScheme.onSurfaceVariant
    }
    val borderStroke = if (isPrimary) {
        if (isDark) BorderStroke(1.dp, colorScheme.outline) else null
    } else {
        BorderStroke(1.dp, colorScheme.outline)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPrimary) DeepInk.copy(alpha = 0.28f) else colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPrimary) titleColor else colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(id = R.string.cd_open_option, title),
                    tint = if (isPrimary) titleColor else colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = titleColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelMedium,
                color = metaColor
            )
        }
    }
}
