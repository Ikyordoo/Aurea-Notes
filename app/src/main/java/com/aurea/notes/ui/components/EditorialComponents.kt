package com.aurea.notes.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aurea.notes.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatEditorialTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val formatter = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

fun formatEditorialDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

@Composable
fun DiscardChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onKeepEditing,
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.secondary,
        title = {
            Text(
                text = stringResource(id = R.string.dialog_discard_title),
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Text(
                text = stringResource(id = R.string.dialog_discard_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(
                    text = stringResource(id = R.string.action_keep_editing),
                    color = colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(
                    text = stringResource(id = R.string.action_discard),
                    color = colorScheme.primary
                )
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.secondary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(id = R.string.action_cancel),
                    color = colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(
                    text = stringResource(id = R.string.action_delete),
                    color = colorScheme.primary
                )
            }
        }
    )
}
