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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurea.notes.R
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.ui.components.DeleteConfirmationDialog
import com.aurea.notes.ui.components.DiscardChangesDialog
import com.aurea.notes.ui.components.formatEditorialTimestamp
import com.aurea.notes.ui.viewmodels.JournalEditorUiState
import com.aurea.notes.ui.viewmodels.JournalViewModel

@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    if (editorState.isOpen) {
        JournalEditorView(
            state = editorState,
            onTitleChange = viewModel::onTitleChange,
            onContentChange = viewModel::onContentChange,
            onTogglePinned = viewModel::toggleEditorPinned,
            onSave = { viewModel.saveEntry() },
            onCancel = viewModel::requestCancelEditor,
            onRequestDelete = viewModel::requestDeleteEntry,
            onDismissDiscard = viewModel::dismissDiscardConfirmation,
            onConfirmDiscard = viewModel::confirmDiscardChanges,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onConfirmDelete = { viewModel.confirmDeleteEntry() },
            modifier = modifier
        )
    } else {
        JournalListView(
            entries = entries,
            onCreateEntry = viewModel::startNewEntry,
            onOpenEntry = viewModel::openEntry,
            onTogglePin = viewModel::toggleEntryPinned,
            modifier = modifier
        )
    }
}

@Composable
private fun JournalListView(
    entries: List<JournalEntryEntity>,
    onCreateEntry: () -> Unit,
    onOpenEntry: (JournalEntryEntity) -> Unit,
    onTogglePin: (JournalEntryEntity) -> Unit,
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
                text = stringResource(id = R.string.journal_title),
                style = MaterialTheme.typography.displayMedium,
                color = colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.journal_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
            Spacer(modifier = Modifier.height(24.dp))

            if (entries.isEmpty()) {
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
                            text = stringResource(id = R.string.journal_empty_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.journal_empty_description),
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
                    items(items = entries, key = { it.id }) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            onClick = { onOpenEntry(entry) },
                            onTogglePin = { onTogglePin(entry) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateEntry,
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
                    text = stringResource(id = R.string.action_new_journal_entry),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayTitle = entry.title.ifBlank {
        stringResource(id = R.string.journal_untitled_fallback)
    }
    val createdText = stringResource(
        id = R.string.journal_created_at,
        formatEditorialTimestamp(entry.createdAt)
    )
    val metaLine = if (entry.isPinned) {
        stringResource(id = R.string.label_pinned) + " · " + createdText
    } else {
        createdText
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, if (entry.isPinned) colorScheme.primary else colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (entry.isPinned) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(
                            id = if (entry.isPinned) R.string.action_unpin else R.string.action_pin
                        ),
                        tint = if (entry.isPinned) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JournalEditorView(
    state: JournalEditorUiState,
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
            title = stringResource(id = R.string.dialog_delete_journal_title),
            message = stringResource(id = R.string.dialog_delete_journal_message),
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
                        R.string.editor_edit_journal_title
                    } else {
                        R.string.editor_new_journal_title
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(
                id = R.string.journal_created_at,
                formatEditorialTimestamp(state.createdAt)
            ) + " · " + stringResource(
                id = R.string.journal_modified_at,
                formatEditorialTimestamp(state.updatedAt)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant
        )

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
                    text = stringResource(id = R.string.journal_validation_error),
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
            label = { Text(text = stringResource(id = R.string.journal_title_label)) },
            placeholder = { Text(text = stringResource(id = R.string.journal_title_placeholder)) },
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
            label = { Text(text = stringResource(id = R.string.journal_content_label)) },
            placeholder = { Text(text = stringResource(id = R.string.journal_content_placeholder)) },
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
