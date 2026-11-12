package com.aurea.notes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurea.notes.AureaApplication
import com.aurea.notes.ui.components.DiscardChangesDialog
import com.aurea.notes.ui.navigation.AureaDestination
import com.aurea.notes.ui.screens.HomeScreen
import com.aurea.notes.ui.screens.JournalScreen
import com.aurea.notes.ui.screens.NotesScreen
import com.aurea.notes.ui.screens.SearchScreen
import com.aurea.notes.ui.viewmodels.JournalViewModel
import com.aurea.notes.ui.viewmodels.NotesViewModel
import com.aurea.notes.ui.viewmodels.SearchViewModel

@Composable
fun AureaApp() {
    val context = LocalContext.current
    val application = context.applicationContext as AureaApplication

    val notesViewModel: NotesViewModel = viewModel(
        factory = NotesViewModel.provideFactory(application.notesRepository)
    )
    val journalViewModel: JournalViewModel = viewModel(
        factory = JournalViewModel.provideFactory(application.journalRepository)
    )
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.provideFactory(
            notesRepository = application.notesRepository,
            journalRepository = application.journalRepository
        )
    )

    val notes by notesViewModel.notes.collectAsStateWithLifecycle()
    val journalEntries by journalViewModel.entries.collectAsStateWithLifecycle()
    val notesEditorState by notesViewModel.editorState.collectAsStateWithLifecycle()
    val journalEditorState by journalViewModel.editorState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AureaDestination.HOME.route
    val colorScheme = MaterialTheme.colorScheme

    var pendingDestination by remember { mutableStateOf<AureaDestination?>(null) }

    val executeNavigation: (AureaDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val requestNavigateToDestination: (AureaDestination) -> Unit = { destination ->
        if (destination.route == currentRoute) {
            if (currentRoute == AureaDestination.NOTES.route && notesEditorState.isOpen) {
                notesViewModel.requestCancelEditor()
            } else if (currentRoute == AureaDestination.JOURNAL.route && journalEditorState.isOpen) {
                journalViewModel.requestCancelEditor()
            }
        } else {
            val hasActiveUnsavedNotes =
                currentRoute == AureaDestination.NOTES.route && notesEditorState.hasUnsavedChanges
            val hasActiveUnsavedJournal =
                currentRoute == AureaDestination.JOURNAL.route && journalEditorState.hasUnsavedChanges

            if (hasActiveUnsavedNotes || hasActiveUnsavedJournal) {
                pendingDestination = destination
            } else {
                if (notesEditorState.isOpen) {
                    notesViewModel.confirmDiscardChanges()
                }
                if (journalEditorState.isOpen) {
                    journalViewModel.confirmDiscardChanges()
                }
                executeNavigation(destination)
            }
        }
    }

    if (pendingDestination != null) {
        DiscardChangesDialog(
            onKeepEditing = {
                pendingDestination = null
            },
            onDiscard = {
                val target = pendingDestination
                pendingDestination = null
                notesViewModel.confirmDiscardChanges()
                journalViewModel.confirmDiscardChanges()
                if (target != null) {
                    executeNavigation(target)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colorScheme.outline
                )
                NavigationBar(
                    containerColor = colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    AureaDestination.entries.forEach { destination ->
                        val isSelected = currentRoute == destination.route
                        val labelText = stringResource(id = destination.labelRes)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { requestNavigateToDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = labelText
                                )
                            },
                            label = {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.primary,
                                selectedTextColor = colorScheme.onSurface,
                                indicatorColor = colorScheme.surfaceVariant,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AureaDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AureaDestination.HOME.route) {
                HomeScreen(
                    notes = notes,
                    journalEntries = journalEntries,
                    onOpenNotes = { requestNavigateToDestination(AureaDestination.NOTES) },
                    onOpenJournal = { requestNavigateToDestination(AureaDestination.JOURNAL) },
                    onSelectNote = { note ->
                        notesViewModel.openNote(note)
                        executeNavigation(AureaDestination.NOTES)
                    },
                    onSelectJournalEntry = { entry ->
                        journalViewModel.openEntry(entry)
                        executeNavigation(AureaDestination.JOURNAL)
                    }
                )
            }
            composable(AureaDestination.NOTES.route) {
                NotesScreen(viewModel = notesViewModel)
            }
            composable(AureaDestination.JOURNAL.route) {
                JournalScreen(viewModel = journalViewModel)
            }
            composable(AureaDestination.SEARCH.route) {
                SearchScreen(
                    viewModel = searchViewModel,
                    onOpenNote = { note ->
                        notesViewModel.openNote(note)
                        executeNavigation(AureaDestination.NOTES)
                    },
                    onOpenJournalEntry = { entry ->
                        journalViewModel.openEntry(entry)
                        executeNavigation(AureaDestination.JOURNAL)
                    }
                )
            }
        }
    }
}
