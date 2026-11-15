package com.aurea.notes.ui.viewmodels

import com.aurea.notes.data.local.AureaDatabase
import com.aurea.notes.data.local.JournalEntryEntity
import com.aurea.notes.data.local.NoteEntity
import com.aurea.notes.data.repository.CategoryOperationResult
import com.aurea.notes.data.repository.JournalRepository
import com.aurea.notes.data.repository.NoteCategoryFilter
import com.aurea.notes.data.repository.NoteSortOrder
import com.aurea.notes.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
import org.junit.Assert.assertNull
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
    fun databaseMigration_andEntityDefaults_preserveExistingRecordsWithDefaults() {
        assertEquals(1, AureaDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AureaDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AureaDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AureaDatabase.MIGRATION_2_3.endVersion)

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
        assertEquals("", defaultNote.category)
        assertFalse(defaultEntry.isPinned)
        assertEquals("", defaultEntry.mood)
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
    fun categoryManagement_createsValidatesRenamesAndDeletesCategoriesWithoutDeletingNotes() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        val viewModel = NotesViewModel(fakeRepo, clock = { 10_000L })
        val collectNotesJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notes.collect()
        }
        val collectCategoriesJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.categories.collect()
        }
        advanceUntilIdle()

        // 1. Reject empty/whitespace category names
        viewModel.openCategoryManager()
        viewModel.onNewCategoryInputChange("   ")
        viewModel.createCategory()
        advanceUntilIdle()
        assertEquals(
            CategoryOperationResult.InvalidName.Reason.EMPTY,
            viewModel.categoryManagerState.value.validationError
        )
        assertTrue(viewModel.categories.value.isEmpty())

        // 2. Reject reserved filter names
        viewModel.onNewCategoryInputChange("Uncategorized")
        viewModel.createCategory()
        advanceUntilIdle()
        assertEquals(
            CategoryOperationResult.InvalidName.Reason.RESERVED,
            viewModel.categoryManagerState.value.validationError
        )

        // 3. Reject overly long category names (> 32 chars)
        viewModel.onNewCategoryInputChange("A".repeat(33))
        viewModel.createCategory()
        advanceUntilIdle()
        assertEquals(
            CategoryOperationResult.InvalidName.Reason.TOO_LONG,
            viewModel.categoryManagerState.value.validationError
        )

        // 4. Create valid category
        viewModel.onNewCategoryInputChange("  Architecture  ")
        viewModel.createCategory()
        advanceUntilIdle()
        assertNull(viewModel.categoryManagerState.value.validationError)
        assertEquals("", viewModel.categoryManagerState.value.newCategoryInput)
        assertEquals(listOf("Architecture"), viewModel.categories.value)

        // 5. Reject duplicate category name (case-insensitive + trimmed)
        viewModel.onNewCategoryInputChange("  architecture ")
        viewModel.createCategory()
        advanceUntilIdle()
        assertEquals(
            CategoryOperationResult.InvalidName.Reason.DUPLICATE,
            viewModel.categoryManagerState.value.validationError
        )
        assertEquals(listOf("Architecture"), viewModel.categories.value)

        // Seed a note assigned to "Architecture"
        val note = NoteEntity(
            id = "note-arch-1",
            title = "Vaulted Ceilings",
            content = "Romanesque stone rib geometry.",
            createdAt = 2_000L,
            updatedAt = 4_000L,
            isPinned = true,
            category = "Architecture"
        )
        fakeRepo.saveNote(note)
        viewModel.setCategoryFilter(NoteCategoryFilter.Category("Architecture"))
        advanceUntilIdle()
        assertEquals(1, viewModel.notes.value.size)

        // 6. Rename category and verify affected notes & active filter update consistently
        viewModel.startRenamingCategory("Architecture")
        viewModel.onRenameInputChange("Classical Studies")
        viewModel.renameCategory()
        advanceUntilIdle()

        assertEquals(listOf("Classical Studies"), viewModel.categories.value)
        val renamedNote = fakeRepo.getNoteById("note-arch-1")!!
        assertEquals("Classical Studies", renamedNote.category)
        assertEquals("Vaulted Ceilings", renamedNote.title)
        assertEquals("Romanesque stone rib geometry.", renamedNote.content)
        assertTrue(renamedNote.isPinned)
        assertEquals(2_000L, renamedNote.createdAt)
        assertEquals(4_000L, renamedNote.updatedAt)
        assertEquals(
            NoteCategoryFilter.Category("Classical Studies"),
            viewModel.categoryFilter.value
        )

        // 7. Delete category: confirm notes are preserved and become Uncategorized (category = "")
        viewModel.requestDeleteCategory("Classical Studies")
        assertEquals(
            "Classical Studies",
            viewModel.categoryManagerState.value.categoryPendingDeletion
        )
        viewModel.confirmDeleteCategory()
        advanceUntilIdle()

        assertTrue(viewModel.categories.value.isEmpty())
        assertEquals(NoteCategoryFilter.All, viewModel.categoryFilter.value)
        val preservedNote = fakeRepo.getNoteById("note-arch-1")!!
        assertEquals("", preservedNote.category)
        assertEquals("Vaulted Ceilings", preservedNote.title)
        assertEquals("Romanesque stone rib geometry.", preservedNote.content)
        assertTrue(preservedNote.isPinned)
        assertEquals(2_000L, preservedNote.createdAt)
        assertEquals(4_000L, preservedNote.updatedAt)

        // Verify note now appears under Uncategorized filter
        viewModel.setCategoryFilter(NoteCategoryFilter.Uncategorized)
        advanceUntilIdle()
        assertEquals(listOf("note-arch-1"), viewModel.notes.value.map { it.id })

        collectNotesJob.cancel()
        collectCategoriesJob.cancel()
    }

    @Test
    fun noteEditor_createsWithAndWithoutCategory_changesAndClearsCategory_andTracksUnsavedChanges() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        var currentTime = 10_000L
        val viewModel = NotesViewModel(fakeRepo, clock = { currentTime })
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notes.collect()
        }

        // 1. Create a note without a category (optional)
        viewModel.startNewNote()
        viewModel.onTitleChange("Quick Fragment")
        viewModel.onContentChange("No category required to save.")
        viewModel.saveNote()
        advanceUntilIdle()

        val uncategorizedNote = fakeRepo.currentItems().single()
        assertEquals("", uncategorizedNote.category)

        // 2. Create a note with a category
        currentTime += 5_000L
        viewModel.startNewNote()
        viewModel.onTitleChange("Botanical Sketch")
        viewModel.onContentChange("Pressed olive leaves.")
        viewModel.onCategoryChange("Fieldwork")
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)
        viewModel.saveNote()
        advanceUntilIdle()

        val categorizedNote = fakeRepo.currentItems().first { it.title == "Botanical Sketch" }
        assertEquals("Fieldwork", categorizedNote.category)

        // 3. Edit an existing note's category and verify unsaved changes detection
        currentTime += 5_000L
        viewModel.openNote(categorizedNote)
        assertFalse(viewModel.editorState.value.hasUnsavedChanges)
        viewModel.onCategoryChange("Herbarium")
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)
        viewModel.saveNote()
        advanceUntilIdle()

        val updatedNote = fakeRepo.getNoteById(categorizedNote.id)!!
        assertEquals("Herbarium", updatedNote.category)

        // 4. Clear a note's category back to Uncategorized
        currentTime += 5_000L
        viewModel.openNote(updatedNote)
        viewModel.clearEditorCategory()
        assertEquals("", viewModel.editorState.value.category)
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)
        viewModel.saveNote()
        advanceUntilIdle()

        val clearedNote = fakeRepo.getNoteById(categorizedNote.id)!!
        assertEquals("", clearedNote.category)

        collectJob.cancel()
    }

    @Test
    fun notesFiltering_combinesAllUncategorizedAndCategoryFilters_withSortingAndPinning() = runTest(testDispatcher) {
        val fakeRepo = FakeNotesRepository()
        val noteUncat = NoteEntity(
            id = "uncat-1",
            title = "Loose Leaf",
            content = "Uncategorized note.",
            createdAt = 1_000L,
            updatedAt = 4_000L,
            isPinned = false,
            category = ""
        )
        val noteEssaysAlpha = NoteEntity(
            id = "essays-alpha",
            title = "Alpha Essay",
            content = "First essay.",
            createdAt = 3_000L,
            updatedAt = 2_000L,
            isPinned = false,
            category = "Essays"
        )
        val noteEssaysBeta = NoteEntity(
            id = "essays-beta",
            title = "Beta Essay",
            content = "Second essay.",
            createdAt = 2_000L,
            updatedAt = 3_000L,
            isPinned = false,
            category = "Essays"
        )
        val noteEssaysZetaPinned = NoteEntity(
            id = "essays-zeta-pinned",
            title = "Zeta Pinned Essay",
            content = "Pinned essay stays on top.",
            createdAt = 500L,
            updatedAt = 500L,
            isPinned = true,
            category = "Essays"
        )
        fakeRepo.saveNote(noteUncat)
        fakeRepo.saveNote(noteEssaysAlpha)
        fakeRepo.saveNote(noteEssaysBeta)
        fakeRepo.saveNote(noteEssaysZetaPinned)

        val viewModel = NotesViewModel(fakeRepo, clock = { 9_000L })
        val collectNotesJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notes.collect()
        }
        val collectAllJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allNotes.collect()
        }
        advanceUntilIdle()

        // 1. All notes filter: returns all 4 notes (pinned first, then by updatedAt DESC)
        assertEquals(NoteCategoryFilter.All, viewModel.categoryFilter.value)
        assertEquals(
            listOf("essays-zeta-pinned", "uncat-1", "essays-beta", "essays-alpha"),
            viewModel.notes.value.map { it.id }
        )

        // 2. Uncategorized filter: returns only uncategorized notes while allNotes retains all 4
        viewModel.setCategoryFilter(NoteCategoryFilter.Uncategorized)
        advanceUntilIdle()
        assertEquals(listOf("uncat-1"), viewModel.notes.value.map { it.id })
        assertEquals(4, viewModel.allNotes.value.size)

        // 3. Specific category filter ("Essays") + Last Modified sort + Pinned first
        viewModel.setCategoryFilter(NoteCategoryFilter.Category("Essays"))
        viewModel.setSortOrder(NoteSortOrder.LAST_MODIFIED)
        advanceUntilIdle()
        assertEquals(
            listOf("essays-zeta-pinned", "essays-beta", "essays-alpha"),
            viewModel.notes.value.map { it.id }
        )

        // 4. Specific category filter ("Essays") + Created Date sort + Pinned first
        viewModel.setSortOrder(NoteSortOrder.CREATED_DATE)
        advanceUntilIdle()
        assertEquals(
            listOf("essays-zeta-pinned", "essays-alpha", "essays-beta"),
            viewModel.notes.value.map { it.id }
        )

        // 5. Specific category filter ("Essays") + Title A–Z sort + Pinned first
        viewModel.setSortOrder(NoteSortOrder.TITLE_ASC)
        advanceUntilIdle()
        assertEquals(
            listOf("essays-zeta-pinned", "essays-alpha", "essays-beta"),
            viewModel.notes.value.map { it.id }
        )

        collectNotesJob.cancel()
        collectAllJob.cancel()
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
    fun searchViewModel_filtersByAllNotesJournalScopes_categoryFilter_andClearsQuery() = runTest(testDispatcher) {
        val fakeNotesRepo = FakeNotesRepository()
        val fakeJournalRepo = FakeJournalRepository()

        fakeNotesRepo.saveNote(
            NoteEntity(
                id = "n-arch",
                title = "Courtyard Proportions",
                content = "Travertine stone study.",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                isPinned = false,
                category = "Reference"
            )
        )
        fakeNotesRepo.saveNote(
            NoteEntity(
                id = "n-uncat",
                title = "Travertine Quarry Notes",
                content = "Unclassified field measurements.",
                createdAt = 1_500L,
                updatedAt = 2_500L,
                isPinned = false,
                category = ""
            )
        )
        fakeJournalRepo.saveEntry(
            JournalEntryEntity(
                id = "j-arch",
                title = "Morning Courtyard",
                content = "Quiet light on the travertine wall.",
                createdAt = 3_000L,
                updatedAt = 3_000L,
                isPinned = true,
                mood = "Calm"
            )
        )

        val searchViewModel = SearchViewModel(fakeNotesRepo, fakeJournalRepo)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            searchViewModel.uiState.collect()
        }

        searchViewModel.onQueryChange("travertine")
        advanceTimeBy(200L)
        advanceUntilIdle()

        // Search scope ALL returns both matching Notes and Journal entries
        assertEquals(SearchScope.ALL, searchViewModel.uiState.value.scope)
        assertEquals(2, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(1, searchViewModel.uiState.value.matchingJournalEntries.size)
        assertEquals(3, searchViewModel.uiState.value.totalResultsCount)

        // Combining Search with an active category filter ("Reference") filters matching notes
        searchViewModel.onCategoryFilterChange(NoteCategoryFilter.Category("Reference"))
        advanceUntilIdle()
        assertEquals(listOf("n-arch"), searchViewModel.uiState.value.matchingNotes.map { it.id })

        // Combining Search with Uncategorized filter returns only uncategorized matching notes
        searchViewModel.onCategoryFilterChange(NoteCategoryFilter.Uncategorized)
        advanceUntilIdle()
        assertEquals(listOf("n-uncat"), searchViewModel.uiState.value.matchingNotes.map { it.id })

        // Reset category filter to All
        searchViewModel.onCategoryFilterChange(NoteCategoryFilter.All)
        advanceUntilIdle()

        // Search scope NOTES returns only matching Notes
        searchViewModel.onScopeChange(SearchScope.NOTES)
        advanceUntilIdle()
        assertEquals(SearchScope.NOTES, searchViewModel.uiState.value.scope)
        assertEquals(2, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(0, searchViewModel.uiState.value.matchingJournalEntries.size)

        // Search scope JOURNAL returns only matching Journal entries
        searchViewModel.onScopeChange(SearchScope.JOURNAL)
        advanceUntilIdle()
        assertEquals(SearchScope.JOURNAL, searchViewModel.uiState.value.scope)
        assertEquals(0, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(1, searchViewModel.uiState.value.matchingJournalEntries.size)

        // Verify category and mood search matching in ALL scope
        searchViewModel.onScopeChange(SearchScope.ALL)
        searchViewModel.onQueryChange("Reference")
        advanceTimeBy(200L)
        advanceUntilIdle()
        assertEquals(listOf("n-arch"), searchViewModel.uiState.value.matchingNotes.map { it.id })
        assertEquals(0, searchViewModel.uiState.value.matchingJournalEntries.size)

        searchViewModel.onQueryChange("Calm")
        advanceTimeBy(200L)
        advanceUntilIdle()
        assertEquals(0, searchViewModel.uiState.value.matchingNotes.size)
        assertEquals(1, searchViewModel.uiState.value.matchingJournalEntries.size)

        // Clearing a search query resets query and results while preserving scope
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

        // Unsaved-changes navigation interception when draft has title, content, or pin change
        viewModel.onTitleChange("Draft Essay")
        viewModel.onContentChange("Unfinished thoughts before switching tabs.")
        viewModel.toggleEditorPinned()
        assertTrue(viewModel.editorState.value.hasUnsavedChanges)
        assertTrue(viewModel.editorState.value.isPinned)

        // Navigation cancellation while retaining the draft
        viewModel.requestCancelEditor()
        assertTrue(viewModel.editorState.value.showDiscardConfirmation)
        viewModel.dismissDiscardConfirmation()
        assertFalse(viewModel.editorState.value.showDiscardConfirmation)
        assertTrue(viewModel.editorState.value.isOpen)
        assertEquals("Draft Essay", viewModel.editorState.value.title)
        assertEquals("Unfinished thoughts before switching tabs.", viewModel.editorState.value.content)

        // Confirmed navigation after discarding changes closes editor and clears dirty state
        viewModel.confirmDiscardChanges()
        assertFalse(viewModel.editorState.value.isOpen)
        assertFalse(viewModel.editorState.value.hasUnsavedChanges)
    }
}

private class FakeNotesRepository : NotesRepository {
    private val state = MutableStateFlow<List<NoteEntity>>(emptyList())
    private val storedCategories = MutableStateFlow<List<String>>(emptyList())

    fun currentItems(): List<NoteEntity> = state.value

    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        observeNotes(NoteSortOrder.LAST_MODIFIED, NoteCategoryFilter.All)

    override fun observeNotes(sortOrder: NoteSortOrder): Flow<List<NoteEntity>> =
        observeNotes(sortOrder, NoteCategoryFilter.All)

    override fun observeNotes(
        sortOrder: NoteSortOrder,
        category: String
    ): Flow<List<NoteEntity>> {
        val trimmed = category.trim()
        val filter = if (trimmed.isEmpty()) {
            NoteCategoryFilter.All
        } else {
            NoteCategoryFilter.Category(trimmed)
        }
        return observeNotes(sortOrder, filter)
    }

    override fun observeNotes(
        sortOrder: NoteSortOrder,
        filter: NoteCategoryFilter
    ): Flow<List<NoteEntity>> =
        state.map { list ->
            val filtered = when (filter) {
                NoteCategoryFilter.All -> list
                NoteCategoryFilter.Uncategorized -> list.filter { it.category.trim().isEmpty() }
                is NoteCategoryFilter.Category ->
                    list.filter { it.category.trim().equals(filter.name.trim(), ignoreCase = true) }
            }
            when (sortOrder) {
                NoteSortOrder.LAST_MODIFIED -> filtered.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
                )
                NoteSortOrder.CREATED_DATE -> filtered.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.createdAt }
                )
                NoteSortOrder.TITLE_ASC -> filtered.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }
                        .thenBy { it.title.lowercase() }
                        .thenByDescending { it.updatedAt }
                )
            }
        }

    override fun observeCategories(): Flow<List<String>> =
        combine(storedCategories, state) { saved, notes ->
            NotesRepository.mergeDistinctCategories(saved, notes.map { it.category })
        }

    override fun searchNotes(query: String): Flow<List<NoteEntity>> =
        state.map { list ->
            list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }.sortedWith(
                compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
            )
        }

    override suspend fun getNoteById(id: String): NoteEntity? =
        state.value.find { it.id == id }

    override suspend fun saveNote(note: NoteEntity) {
        val cleanCategory = note.category.trim()
        val normalized = note.copy(category = cleanCategory)
        if (cleanCategory.isNotEmpty()) {
            storedCategories.value = NotesRepository.mergeDistinctCategories(
                storedCategories.value,
                listOf(cleanCategory)
            )
        }
        state.value = state.value.filterNot { it.id == normalized.id } + normalized
    }

    override suspend fun setNotePinned(id: String, isPinned: Boolean) {
        state.value = state.value.map { note ->
            if (note.id == id) note.copy(isPinned = isPinned) else note
        }
    }

    override suspend fun setNoteCategory(id: String, category: String) {
        val clean = category.trim()
        if (clean.isNotEmpty()) {
            storedCategories.value = NotesRepository.mergeDistinctCategories(
                storedCategories.value,
                listOf(clean)
            )
        }
        state.value = state.value.map { note ->
            if (note.id == id) note.copy(category = clean) else note
        }
    }

    override suspend fun createCategory(name: String): CategoryOperationResult {
        val existing = NotesRepository.mergeDistinctCategories(
            storedCategories.value,
            state.value.map { it.category }
        )
        val validation = NotesRepository.validateCategoryName(
            candidate = name,
            existingCategories = existing
        )
        if (validation is CategoryOperationResult.InvalidName) {
            return validation
        }
        storedCategories.value = NotesRepository.mergeDistinctCategories(
            storedCategories.value,
            listOf(name.trim())
        )
        return CategoryOperationResult.Success
    }

    override suspend fun renameCategory(
        oldName: String,
        newName: String
    ): CategoryOperationResult {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        val existing = NotesRepository.mergeDistinctCategories(
            storedCategories.value,
            state.value.map { it.category }
        )
        val validation = NotesRepository.validateCategoryName(
            candidate = trimmedNew,
            existingCategories = existing,
            ignoringCurrentName = trimmedOld
        )
        if (validation is CategoryOperationResult.InvalidName) {
            return validation
        }
        storedCategories.value = NotesRepository.mergeDistinctCategories(
            storedCategories.value.filterNot { it.equals(trimmedOld, ignoreCase = true) },
            listOf(trimmedNew)
        )
        state.value = state.value.map { note ->
            if (note.category.trim().equals(trimmedOld, ignoreCase = true)) {
                note.copy(category = trimmedNew)
            } else {
                note
            }
        }
        return CategoryOperationResult.Success
    }

    override suspend fun deleteCategory(name: String) {
        val trimmed = name.trim()
        storedCategories.value = storedCategories.value.filterNot {
            it.equals(trimmed, ignoreCase = true)
        }
        state.value = state.value.map { note ->
            if (note.category.trim().equals(trimmed, ignoreCase = true)) {
                note.copy(category = "")
            } else {
                note
            }
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
                    it.content.contains(query, ignoreCase = true) ||
                    it.mood.contains(query, ignoreCase = true)
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

    override suspend fun setEntryMood(id: String, mood: String) {
        state.value = state.value.map { entry ->
            if (entry.id == id) entry.copy(mood = mood.trim()) else entry
        }
    }

    override suspend fun deleteEntryById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
