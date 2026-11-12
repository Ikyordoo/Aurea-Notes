package com.aurea.notes.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurea.notes.R
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.ui.components.formatEditorialTimestamp
import com.aurea.notes.ui.theme.EspressoDisabled
import com.aurea.notes.ui.viewmodels.SearchScope
import com.aurea.notes.ui.viewmodels.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenNote: (NoteEntity) -> Unit = {},
    onOpenJournalEntry: (JournalEntryEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val disabledForeground = if (isDark) colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else EspressoDisabled

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = stringResource(id = R.string.search_title),
            style = MaterialTheme.typography.displayMedium,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(id = R.string.search_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(id = R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(id = R.string.cd_search_icon),
                    tint = colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(id = R.string.cd_clear_search),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surface,
                unfocusedContainerColor = colorScheme.surface,
                disabledContainerColor = colorScheme.surfaceVariant,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                disabledBorderColor = colorScheme.outline,
                cursorColor = colorScheme.primary,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                disabledTextColor = disabledForeground,
                disabledPlaceholderColor = disabledForeground,
                disabledLeadingIconColor = disabledForeground
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        SearchScopeSelector(
            selectedScope = uiState.scope,
            onSelectScope = viewModel::onScopeChange
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 1.dp, color = colorScheme.outline)
        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.hasActiveQuery) {
            Text(
                text = stringResource(id = R.string.search_empty_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary
            )
        } else if (uiState.totalResultsCount == 0) {
            Text(
                text = stringResource(id = R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.secondary
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items = uiState.matchingNotes, key = { "note-${it.id}" }) { note ->
                    val metaPrefix = buildString {
                        if (note.isPinned) {
                            append(stringResource(id = R.string.label_pinned))
                            append(" · ")
                        }
                        append(stringResource(id = R.string.notes_title))
                        append(" · ")
                        append(formatEditorialTimestamp(note.updatedAt))
                    }
                    Card(
                        onClick = { onOpenNote(note) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = metaPrefix,
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                items(items = uiState.matchingJournalEntries, key = { "journal-${it.id}" }) { entry ->
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
                    Card(
                        onClick = { onOpenJournalEntry(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = metaPrefix,
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
            }
        }
    }
}

@Composable
private fun SearchScopeSelector(
    selectedScope: SearchScope,
    onSelectScope: (SearchScope) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scopes = listOf(
        SearchScope.ALL to R.string.search_scope_all,
        SearchScope.NOTES to R.string.search_scope_notes,
        SearchScope.JOURNAL to R.string.search_scope_journal
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
            scopes.forEach { (scope, labelRes) ->
                val isSelected = selectedScope == scope
                TextButton(
                    onClick = { onSelectScope(scope) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) colorScheme.surfaceVariant else colorScheme.surface,
                        contentColor = if (isSelected) colorScheme.onSurface else colorScheme.secondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
