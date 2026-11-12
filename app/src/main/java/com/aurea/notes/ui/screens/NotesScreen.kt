package com.aurea.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurea.notes.R
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.NoteSortOrder
import com.aurea.notes.ui.components.DeleteConfirmationDialog
import com.aurea.notes.ui.components.DiscardChangesDialog
import com.aurea.notes.ui.components.formatEditorialTimestamp
import com.aurea.notes.ui.viewmodels.NoteEditorUiState
import com.aurea.notes.ui.viewmodels.NotesViewModel

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    if (editorState.isOpen) {
        NoteEditorView(
            state = editorState,
            onTitleChange = viewModel::onTitleChange,
            onContentChange = viewModel::onContentChange,
            onTogglePinned = viewModel::toggleEditorPinned,
            onSave = { viewModel.saveNote() },
            onCancel = viewModel::requestCancelEditor,
            onRequestDelete = viewModel::requestDeleteNote,
            onDismissDiscard = viewModel::dismissDiscardConfirmation,
            onConfirmDiscard = viewModel::confirmDiscardChanges,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onConfirmDelete = { viewModel.confirmDeleteNote() },
            modifier = modifier
        )
    } else {
        NotesListView(
            notes = notes,
            sortOrder = sortOrder,
            onSortOrderChange = viewModel::setSortOrder,
            onCreateNote = viewModel::startNewNote,
            onOpenNote = viewModel::openNote,
            onTogglePin = viewModel::toggleNotePinned,
            modifier = modifier
        )
    }
}

@Composable
private fun NotesListView(
    notes: List<NoteEntity>,
    sortOrder: NoteSortOrder,
    onSortOrderChange: (NoteSortOrder) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.notes_title),
                style = MaterialTheme.typography.displayMedium,
                color = colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.notes_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Sorting selector (shown when notes exist)
            if (notes.isNotEmpty()) {
                NotesSortBar(
                    selectedOrder = sortOrder,
                    onSelectOrder = onSortOrderChange
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (notes.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 40.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.notes_empty_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.notes_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.secondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(items = notes, key = { it.id }) { note ->
                        NoteListItemCard(
                            note = note,
                            onClick = { onOpenNote(note) },
                            onTogglePin = { onTogglePin(note) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.action_new_note),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}

@Composable
private fun NotesSortBar(
    selectedOrder: NoteSortOrder,
    onSelectOrder: (NoteSortOrder) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val options = listOf(
        NoteSortOrder.LAST_MODIFIED to R.string.sort_last_modified,
        NoteSortOrder.CREATED_DATE to R.string.sort_created_date,
        NoteSortOrder.TITLE_ASC to R.string.sort_title_asc
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (order, labelRes) ->
                val isSelected = selectedOrder == order
                TextButton(
                    onClick = { onSelectOrder(order) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                        contentColor = if (isSelected) colorScheme.onSurface else colorScheme.secondary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(id = labelRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteListItemCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val modifiedText = stringResource(
        id = R.string.note_last_modified,
        formatEditorialTimestamp(note.updatedAt)
    )
    val metaLine = if (note.isPinned) {
        stringResource(id = R.string.label_pinned) + " · " + modifiedText
    } else {
        modifiedText
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, if (note.isPinned) colorScheme.primary else colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (note.isPinned) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(
                            id = if (note.isPinned) R.string.action_unpin else R.string.action_pin
                        ),
                        tint = if (note.isPinned) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelMedium,
                color = if (note.isPinned) colorScheme.primary else colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteEditorView(
    state: NoteEditorUiState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    BackHandler(onBack = onCancel)

    if (state.showDiscardConfirmation) {
        DiscardChangesDialog(
            onKeepEditing = onDismissDiscard,
            onDiscard = onConfirmDiscard
        )
    }

    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            title = stringResource(id = R.string.dialog_delete_note_title),
            message = stringResource(id = R.string.dialog_delete_note_message),
            onCancel = onDismissDelete,
            onConfirmDelete = onConfirmDelete
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    id = if (state.isEditingExisting) {
                        R.string.editor_edit_note_title
                    } else {
                        R.string.editor_new_note_title
                    }
                ),
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTogglePinned,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.isPinned) colorScheme.primary else colorScheme.outline
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPinned) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            id = if (state.isPinned) R.string.label_pinned else R.string.action_pin
                        ),
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                if (state.isEditingExisting) {
                    OutlinedButton(
                        onClick = onRequestDelete,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colorScheme.primary
                        )
                        Text(
                            text = stringResource(id = R.string.action_delete),
                            color = colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }

        if (state.isEditingExisting && state.updatedAt > 0L) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    id = R.string.note_last_modified,
                    formatEditorialTimestamp(state.updatedAt)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
        Spacer(modifier = Modifier.height(18.dp))

        if (state.showValidationError) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, colorScheme.primary)
            ) {
                Text(
                    text = stringResource(id = R.string.note_validation_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(id = R.string.note_title_label)) },
            placeholder = { Text(text = stringResource(id = R.string.note_title_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surface,
                unfocusedContainerColor = colorScheme.surface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                cursorColor = colorScheme.primary,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = state.content,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text(text = stringResource(id = R.string.note_content_label)) },
            placeholder = { Text(text = stringResource(id = R.string.note_content_placeholder)) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surface,
                unfocusedContainerColor = colorScheme.surface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                cursorColor = colorScheme.primary,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    color = colorScheme.onSurface
                )
            }

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Text(text = stringResource(id = R.string.action_save))
            }
        }
    }
}
