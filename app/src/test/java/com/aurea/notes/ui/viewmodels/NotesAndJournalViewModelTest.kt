package com.aurea.notes.ui.viewmodels

import com.aurea.notes.data.local.AureaDatabase
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.JournalRepository
import com.aurea.notes.data.repository.NoteSortOrder
import com.aurea.notes.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesAndJournalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun databaseMigration_andEntityDefaults_preserveExistingRecordsWithIsPinnedFalse() {
        assertEquals(1, AureaDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AureaDatabase.MIGRATION_1_2.endVersion)

        val defaultNote = NoteEntity(
            id = "n-1",
            title = "Architectural Study",
            content = "Limestone and timber proportions.",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        val defaultEntry = JournalEntryEntity(
            id = "j-1",
            title = "Morning Observation",
            content = "Light across the courtyard.",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertFalse(defaultNote.isPinned)
        assertFalse(defaultEntry.isPinned)
    }

    @Test
    fun notesViewModel_createsEditsValidatesAndDeletesNote() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        var currentTime = 1_700_000_000_000L
        val viewModel = NotesViewModel(fakeRepo, clock = { currentTime })

        // Start new note and test validation on empty input
        viewModel.startNewNote()
        viewModel.saveNote()
        assertTrue(viewModel.editorState.value.showValidationError)

        // Enter title and content and save
        viewModel.onTitleChange("Field Observation")
        viewModel.onContentChange("Notes on limestone surfaces.")
        viewModel.saveNote()
        advanceUntilIdle()

        val savedNotes = fakeRepo.currentItems()
        assertEquals(1, savedNotes.size)
        assertEquals("Field Observation", savedNotes.first().title)

        // Open existing note, modify, and verify unsaved changes guard
        currentTime += 60_000L
        viewModel.openNote(savedNotes.first())
        viewModel.onContentChange("Updated notes on limestone surfaces.")
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)

        viewModel.requestCancelEditor()
        assertTrue(viewModel.editorState.value.showDiscardConfirmation)
        viewModel.dismissDiscardConfirmation()
        assertFalse(viewModel.editorState.value.showDiscardConfirmation)

        // Save updated note
        viewModel.saveNote()
        advanceUntilIdle()
        assertEquals("Updated notes on limestone surfaces.", fakeRepo.currentItems().first().content)

        // Delete with confirmation
        viewModel.openNote(fakeRepo.currentItems().first())
        viewModel.requestDeleteNote()
        assertTrue(viewModel.editorState.value.showDeleteConfirmation)
        viewModel.confirmDeleteNote()
        advanceUntilIdle()
        assertTrue(fakeRepo.currentItems().isEmpty())
    }

    @Test
    fun notesViewModel_pinsAndUnpinsNote_andOrdersPinnedFirst() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        val noteOlder = NoteEntity(
            id = "note-older",
            title = "Older Note",
            content = "Created earlier.",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            isPinned = false
        )
        val noteNewer = NoteEntity(
            id = "note-newer",
            title = "Newer Note",
            content = "Created later.",
            createdAt = 2_000L,
            updatedAt = 2_000L,
            isPinned = false
        )
        fakeRepo.saveNote(noteOlder)
        fakeRepo.saveNote(noteNewer)

        val viewModel = NotesViewModel(fakeRepo, clock = { 3_000L })
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notes.collect()
        }
        advanceUntilIdle()

        // 1. Pinning a Note: pin the older note so it rises above the newer unpinned note
        viewModel.toggleNotePinned(noteOlder)
        advanceUntilIdle()
        assertTrue(fakeRepo.getNoteById("note-older")!!.isPinned)
        assertEquals("note-older", viewModel.notes.value.first().id)

        // 2. Unpinning a Note: unpin the older note so newer note returns to top
        val pinnedOlder = fakeRepo.getNoteById("note-older")!!
        viewModel.toggleNotePinned(pinnedOlder)
        advanceUntilIdle()
        assertFalse(fakeRepo.getNoteById("note-older")!!.isPinned)
        assertEquals("note-newer", viewModel.notes.value.first().id)

        collectJob.cancel()
    }

    @Test
    fun notesViewModel_sortsByLastModifiedCreatedDateAndTitleAsc_withPinnedFirst() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        val noteAlpha = NoteEntity(
            id = "alpha",
            title = "Alpha Monograph",
            content = "Content A",
            createdAt = 3_000L,
            updatedAt = 1_000L,
            isPinned = false
        )
        val noteBeta = NoteEntity(
            id = "beta",
            title = "Beta Monograph",
            content = "Content B",
            createdAt = 1_000L,
            updatedAt = 3_000L,
            isPinned = false
        )
        val notePinnedZeta = NoteEntity(
            id = "zeta-pinned",
            title = "Zeta Pinned",
            content = "Pinned item always stays at top",
            createdAt = 500L,
            updatedAt = 500L,
            isPinned = true
        )
        fakeRepo.saveNote(noteAlpha)
        fakeRepo.saveNote(noteBeta)
        fakeRepo.saveNote(notePinnedZeta)

        val viewModel = NotesViewModel(fakeRepo, clock = { 5_000L })
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notes.collect()
        }
        advanceUntilIdle()

        // 4. Sorting Notes by modification date (default LAST_MODIFIED): pinned first, then beta (3000), then alpha (1000)
        assertEquals(NoteSortOrder.LAST_MODIFIED, viewModel.sortOrder.value)
        assertEquals(listOf("zeta-pinned", "beta", "alpha"), viewModel.notes.value.map { it.id })

        // 5. Sorting Notes by creation date (CREATED_DATE): pinned first, then alpha (3000), then beta (1000)
        viewModel.setSortOrder(NoteSortOrder.CREATED_DATE)
        advanceUntilIdle()
        assertEquals(NoteSortOrder.CREATED_DATE, viewModel.sortOrder.value)
        assertEquals(listOf("zeta-pinned", "alpha", "beta"), viewModel.notes.value.map { it.id })

        // 6. Sorting Notes alphabetically (TITLE_ASC): pinned first, then Alpha, then Beta
        viewModel.setSortOrder(NoteSortOrder.TITLE_ASC)
        advanceUntilIdle()
        assertEquals(NoteSortOrder.TITLE_ASC, viewModel.sortOrder.value)
        assertEquals(listOf("zeta-pinned", "alpha", "beta"), viewModel.notes.value.map { it.id })

        collectJob.cancel()
    }

    @Test
    fun journalViewModel_pinsEntry_andMaintainsPinnedFirstChronologicalOrder() = runTest(testDispatcher) {
        val fakeRepo = FakeJournalRepository()
        var currentTime = 1_700_000_000_000L
        val viewModel = JournalViewModel(fakeRepo, clock = { currentTime })
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.entries.collect()
        }

        // Create earlier entry without title
        viewModel.startNewEntry()
        viewModel.onContentChange("First reflection of the morning.")
        viewModel.saveEntry()
        advanceUntilIdle()

        // Create second entry later
        currentTime += 120_000L
        viewModel.startNewEntry()
        viewModel.onTitleChange("Evening Walk")
        viewModel.onContentChange("Second reflection at dusk.")
        viewModel.saveEntry()
        advanceUntilIdle()

        val initialEntries = viewModel.entries.value
        assertEquals(2, initialEntries.size)
        assertEquals("Evening Walk", initialEntries.first().title)

        // 3. Pinning a Journal entry: pin the older entry and verify it moves above the newer unpinned entry
        val olderEntry = initialEntries.last()
        viewModel.toggleEntryPinned(olderEntry)
        advanceUntilIdle()

        val reorderedEntries = viewModel.entries.value
        assertTrue(reorderedEntries.first().isPinned)
        assertEquals(olderEntry.id, reorderedEntries.first().id)

        collectJob.cancel()
    }

    @Test
    fun searchViewModel_filtersByAllNotesJournalScopes_andClearsQuery() = runTest(testDispatcher) {
        val fakeNotesRepo = FakeNotesRepository()
        val fakeJournalRepo = FakeJournalRepository()

        fakeNotesRepo.saveNote(
            NoteEntity(
                id = "n-arch",
                title = "Courtyard Proportions",
                content = "Travertine stone study.",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                isPinned = false
            )
        )
        fakeJournalRepo.saveEntry(
            JournalEntryEntity(
                id = "j-arch",
                title = "Morning Courtyard",
                content = "Quiet light on the travertine wall.",
                createdAt = 3_000L,
                updatedAt = 3_000L,
                isPinned = true
            )
        )

        val searchViewModel = SearchViewModel(fakeNotesRepo, fakeJournalRepo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            searchViewModel.uiState.collect()
        }

        searchViewModel.onQueryChange("travertine")
        advanceTimeBy(200L)
        advanceUntilIdle()

        // 7. Search scope ALL returns both matching Notes and Journal entries
        assertEquals(SearchScope.ALL, searchViewModel.uiState.value.scope)
        assertEquals(1, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(1, searchViewModel.uiState.value.matchingJournalEntries.size)
        assertEquals(2, searchViewModel.uiState.value.totalResultsCount)

        // 8. Search scope NOTES returns only matching Notes
        searchViewModel.onScopeChange(SearchScope.NOTES)
        advanceUntilIdle()
        assertEquals(SearchScope.NOTES, searchViewModel.uiState.value.scope)
        assertEquals(1, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(0, searchViewModel.uiState.value.matchingJournalEntries.size)

        // 9. Search scope JOURNAL returns only matching Journal entries
        searchViewModel.onScopeChange(SearchScope.JOURNAL)
        advanceUntilIdle()
        assertEquals(SearchScope.JOURNAL, searchViewModel.uiState.value.scope)
        assertEquals(0, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(1, searchViewModel.uiState.value.matchingJournalEntries.size)

        // 10. Clearing a search query resets query and results while preserving scope
        searchViewModel.clearQuery()
        advanceTimeBy(200L)
        advanceUntilIdle()
        assertEquals("", searchViewModel.uiState.value.query)
        assertFalse(searchViewModel.uiState.value.hasActiveQuery)
        assertEquals(0, searchViewModel.uiState.value.totalResultsCount)

        collectJob.cancel()
    }

    @Test
    fun unsavedChangesGuard_interceptsNavigation_allowsCancelRetention_andConfirmsDiscard() = runTest(testDispatcher) {
        val fakeNotesRepo = FakeNotesRepository()
        val viewModel = NotesViewModel(fakeNotesRepo, clock = { 1_000L })

        viewModel.startNewNote()
        assertFalse(viewModel.editorState.value.hasUnsavedChanges)

        // 11. Unsaved-changes navigation interception when draft has title, content, or pin change
        viewModel.onTitleChange("Draft Essay")
        viewModel.onContentChange("Unfinished thoughts before switching tabs.")
        viewModel.toggleEditorPinned()
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)
        assertTrue(viewModel.editorState.value.isPinned)

        // 13. Navigation cancellation while retaining the draft
        viewModel.requestCancelEditor()
        assertTrue(viewModel.editorState.value.showDiscardConfirmation)
        viewModel.dismissDiscardConfirmation()
        assertFalse(viewModel.editorState.value.showDiscardConfirmation)
        assertTrue(viewModel.editorState.value.isOpen)
        assertEquals("Draft Essay", viewModel.editorState.value.title)
        assertEquals("Unfinished thoughts before switching tabs.", viewModel.editorState.value.content)

        // 12. Confirmed navigation after discarding changes closes editor and clears dirty state
        viewModel.confirmDiscardChanges()
        assertFalse(viewModel.editorState.value.isOpen)
        assertFalse(viewModel.editorState.value.hasUnsavedChanges)
    }
}

private class FakeNotesRepository : NotesRepository {
    private val state = MutableStateFlow<List<NoteEntity>>(emptyList())

    fun currentItems(): List<NoteEntity> = state.value

    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        observeNotes(NoteSortOrder.LAST_MODIFIED)

    override fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>> =
        state.map { list ->
            when (sortOrder) {
                NoteSortOrder.LAST_MODIFIED -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
                )
                NoteSortOrder.CREATED_DATE -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.createdAt }
                )
                NoteSortOrder.TITLE_ASC -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }
                        .thenBy { it.title.lowercase() }
                        .thenByDescending { it.updatedAt }
                )
            }
        }

    override fun searchNotes(query: String): Flow<List<NoteEntity>> =
        state.map { list ->
            list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
            }.sortedWith(
                compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
            )
        }

    override suspend fun getNoteById(id: String): NoteEntity? =
        state.value.find { it.id == id }

    override suspend fun saveNote(note: NoteEntity) {
        state.value = state.value.filterNot { it.id == note.id } + note
    }

    override suspend fun setNotePinned(id: String, isPinned: Boolean) {
        state.value = state.value.map { note ->
            if (note.id == id) note.copy(isPinned = isPinned) else note
        }
    }

    override suspend fun deleteNoteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class FakeJournalRepository : JournalRepository {
    private val state = MutableStateFlow<List<JournalEntryEntity>>(emptyList())

    fun currentItems(): List<JournalEntryEntity> =
        state.value.sortedWith(
            compareByDescending<JournalEntryEntity> { it.isPinned }.thenByDescending { it.createdAt }
        )

    override fun observeAllEntries(): Flow<List<JournalEntryEntity>> =
        state.map { list ->
            list.sortedWith(
                compareByDescending<JournalEntryEntity> { it.isPinned }.thenByDescending { it.createdAt }
            )
        }

    override fun searchEntries(query: String): Flow<List<JournalEntryEntity>> =
        state.map { list ->
            list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
            }.sortedWith(
                compareByDescending<JournalEntryEntity> { it.isPinned }.thenByDescending { it.createdAt }
            )
        }

    override suspend fun getEntryById(id: String): JournalEntryEntity? =
        state.value.find { it.id == id }

    override suspend fun saveEntry(entry: JournalEntryEntity) {
        state.value = state.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun setEntryPinned(id: String, isPinned: Boolean) {
        state.value = state.value.map { entry ->
            if (entry.id == id) entry.copy(isPinned = isPinned) else entry
        }
    }

    override suspend fun deleteEntryById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
