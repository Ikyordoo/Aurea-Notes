/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  Bookmark,
  BookOpen,
  FileText,
  Home,
  Moon,
  Pencil,
  Plus,
  Search,
  Sun,
  Tag,
  Trash2,
  X,
} from 'lucide-react';
import {
  CategoryValidationError,
  ContentFormat,
  createNoteCategoryRecord,
  deleteJournalEntryRecord,
  deleteNoteCategoryRecord,
  deleteNoteRecord,
  filterAndSortNotes,
  formatEditorialTimestamp,
  getAllJournalEntries,
  getAllNotes,
  getStoredCategoryNames,
  JournalEntryRecord,
  mergeAvailableCategories,
  NoteCategoryFilter,
  NoteRecord,
  NoteSortOrder,
  renameNoteCategoryRecord,
  saveJournalEntryRecord,
  saveNoteRecord,
  SearchScope,
  setJournalEntryPinnedRecord,
  setNotePinnedRecord,
  sortNotesList,
} from './services/localDb';

type Destination = 'home' | 'notes' | 'journal' | 'search';

interface NoteEditorState {
  isOpen: boolean;
  isEditingExisting: boolean;
  id: string;
  title: string;
  content: string;
  contentFormat: ContentFormat;
  isPinned: boolean;
  category: string;
  originalTitle: string;
  originalContent: string;
  originalIsPinned: boolean;
  originalCategory: string;
  createdAt: number;
  updatedAt: number;
  showValidationError: boolean;
}

interface JournalEditorState {
  isOpen: boolean;
  isEditingExisting: boolean;
  id: string;
  title: string;
  content: string;
  isPinned: boolean;
  originalTitle: string;
  originalContent: string;
  originalIsPinned: boolean;
  createdAt: number;
  updatedAt: number;
  showValidationError: boolean;
}

interface CategoryManagerState {
  isOpen: boolean;
  newCategoryInput: string;
  renamingCategory: string | null;
  renameInput: string;
  validationError: CategoryValidationError | null;
  categoryPendingDeletion: string | null;
}

type HomeHighlight =
  | {
      kind: 'note';
      idKey: string;
      isPinned: boolean;
      sortTimestamp: number;
      note: NoteRecord;
    }
  | {
      kind: 'journal';
      idKey: string;
      isPinned: boolean;
      sortTimestamp: number;
      entry: JournalEntryRecord;
    };

const CLOSED_NOTE_EDITOR: NoteEditorState = {
  isOpen: false,
  isEditingExisting: false,
  id: '',
  title: '',
  content: '',
  contentFormat: 'PLAIN_TEXT',
  isPinned: false,
  category: '',
  originalTitle: '',
  originalContent: '',
  originalIsPinned: false,
  originalCategory: '',
  createdAt: 0,
  updatedAt: 0,
  showValidationError: false,
};

const CLOSED_JOURNAL_EDITOR: JournalEditorState = {
  isOpen: false,
  isEditingExisting: false,
  id: '',
  title: '',
  content: '',
  isPinned: false,
  originalTitle: '',
  originalContent: '',
  originalIsPinned: false,
  createdAt: 0,
  updatedAt: 0,
  showValidationError: false,
};

const CLOSED_CATEGORY_MANAGER: CategoryManagerState = {
  isOpen: false,
  newCategoryInput: '',
  renamingCategory: null,
  renameInput: '',
  validationError: null,
  categoryPendingDeletion: null,
};

function createUniqueId(prefix: string): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function getCategoryValidationErrorMessage(
  error: CategoryValidationError | null
): string | null {
  if (!error) return null;
  switch (error) {
    case 'EMPTY':
      return 'Please enter a category name.';
    case 'TOO_LONG':
      return 'Category names must be 32 characters or fewer.';
    case 'DUPLICATE':
      return 'A category with this name already exists.';
    case 'RESERVED':
      return 'That name is reserved for built-in note filters.';
  }
}

function isSameCategoryFilter(
  a: NoteCategoryFilter,
  b: NoteCategoryFilter
): boolean {
  if (a.type !== b.type) return false;
  if (a.type === 'CATEGORY' && b.type === 'CATEGORY') {
    return a.name.trim().toLowerCase() === b.name.trim().toLowerCase();
  }
  return true;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<Destination>('home');
  const [rawNotes, setRawNotes] = useState<NoteRecord[]>([]);
  const [storedCategories, setStoredCategories] = useState<string[]>([]);
  const [journalEntries, setJournalEntries] = useState<JournalEntryRecord[]>([]);
  const [noteSortOrder, setNoteSortOrder] =
    useState<NoteSortOrder>('LAST_MODIFIED');
  const [noteCategoryFilter, setNoteCategoryFilter] =
    useState<NoteCategoryFilter>({ type: 'ALL' });
  const [isLoaded, setIsLoaded] = useState(false);

  const [noteEditor, setNoteEditor] =
    useState<NoteEditorState>(CLOSED_NOTE_EDITOR);
  const [journalEditor, setJournalEditor] =
    useState<JournalEditorState>(CLOSED_JOURNAL_EDITOR);
  const [categoryManager, setCategoryManager] =
    useState<CategoryManagerState>(CLOSED_CATEGORY_MANAGER);

  const [pendingCancelTarget, setPendingCancelTarget] = useState<
    | { type: 'close-note' }
    | { type: 'close-journal' }
    | { type: 'navigate'; destination: Destination }
    | null
  >(null);

  const [deleteTarget, setDeleteTarget] = useState<
    | { type: 'note'; id: string; title: string }
    | { type: 'journal'; id: string; title: string }
    | null
  >(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchScope, setSearchScope] = useState<SearchScope>('ALL');
  const [searchCategoryFilter, setSearchCategoryFilter] =
    useState<NoteCategoryFilter>({ type: 'ALL' });

  const [isDark, setIsDark] = useState<boolean>(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return false;
  });

  useEffect(() => {
    const root = document.documentElement;
    if (isDark) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [isDark]);

  const reloadRecords = useCallback(async () => {
    const [loadedNotes, loadedJournal] = await Promise.all([
      getAllNotes(),
      getAllJournalEntries(),
    ]);
    setRawNotes(loadedNotes);
    setStoredCategories(getStoredCategoryNames());
    setJournalEntries(loadedJournal);
    setIsLoaded(true);
  }, []);

  useEffect(() => {
    reloadRecords();
  }, [reloadRecords]);

  // Available categories merged from persistent category store and notes
  const categories = useMemo(
    () => mergeAvailableCategories(storedCategories, rawNotes),
    [storedCategories, rawNotes]
  );

  // Filtered and sorted notes (Pinned first, then selected NoteSortOrder within active category filter)
  const notes = useMemo(
    () => filterAndSortNotes(rawNotes, noteCategoryFilter, noteSortOrder),
    [rawNotes, noteCategoryFilter, noteSortOrder]
  );

  // Home Pinned & Recent (Up to 3 relevant user-created items across all notes & journal entries)
  const homeHighlights = useMemo<HomeHighlight[]>(() => {
    const items: HomeHighlight[] = [
      ...rawNotes.map(
        (note): HomeHighlight => ({
          kind: 'note',
          idKey: `note-${note.id}`,
          isPinned: note.isPinned,
          sortTimestamp: note.updatedAt,
          note,
        })
      ),
      ...journalEntries.map(
        (entry): HomeHighlight => ({
          kind: 'journal',
          idKey: `journal-${entry.id}`,
          isPinned: entry.isPinned,
          sortTimestamp: entry.createdAt,
          entry,
        })
      ),
    ];

    return items
      .sort((a, b) => {
        if (a.isPinned !== b.isPinned) {
          return a.isPinned ? -1 : 1;
        }
        return b.sortTimestamp - a.sortTimestamp;
      })
      .slice(0, 3);
  }, [rawNotes, journalEntries]);

  const noteHasUnsavedChanges =
    activeTab === 'notes' &&
    noteEditor.isOpen &&
    (noteEditor.title.trim() !== noteEditor.originalTitle.trim() ||
      noteEditor.content.trim() !== noteEditor.originalContent.trim() ||
      noteEditor.isPinned !== noteEditor.originalIsPinned ||
      noteEditor.category.trim() !== noteEditor.originalCategory.trim());

  const journalHasUnsavedChanges =
    activeTab === 'journal' &&
    journalEditor.isOpen &&
    (journalEditor.title.trim() !== journalEditor.originalTitle.trim() ||
      journalEditor.content.trim() !== journalEditor.originalContent.trim() ||
      journalEditor.isPinned !== journalEditor.originalIsPinned);

  const handleNavigate = (destination: Destination) => {
    if (destination === activeTab) {
      if (destination === 'notes' && noteEditor.isOpen) {
        requestCloseNoteEditor();
      } else if (destination === 'journal' && journalEditor.isOpen) {
        requestCloseJournalEditor();
      }
      return;
    }

    if (noteHasUnsavedChanges || journalHasUnsavedChanges) {
      setPendingCancelTarget({ type: 'navigate', destination });
      return;
    }

    setNoteEditor(CLOSED_NOTE_EDITOR);
    setJournalEditor(CLOSED_JOURNAL_EDITOR);
    setActiveTab(destination);
  };

  // Notes actions
  const startNewNote = () => {
    const now = Date.now();
    const defaultCategory =
      noteCategoryFilter.type === 'CATEGORY' ? noteCategoryFilter.name : '';
    setNoteEditor({
      isOpen: true,
      isEditingExisting: false,
      id: createUniqueId('note'),
      title: '',
      content: '',
      contentFormat: 'PLAIN_TEXT',
      isPinned: false,
      category: defaultCategory,
      originalTitle: '',
      originalContent: '',
      originalIsPinned: false,
      originalCategory: defaultCategory,
      createdAt: now,
      updatedAt: now,
      showValidationError: false,
    });
    setActiveTab('notes');
  };

  const openExistingNote = (note: NoteRecord) => {
    const cleanCategory = (note.category ?? '').trim();
    setNoteEditor({
      isOpen: true,
      isEditingExisting: true,
      id: note.id,
      title: note.title,
      content: note.content,
      contentFormat: note.contentFormat || 'PLAIN_TEXT',
      isPinned: Boolean(note.isPinned),
      category: cleanCategory,
      originalTitle: note.title,
      originalContent: note.content,
      originalIsPinned: Boolean(note.isPinned),
      originalCategory: cleanCategory,
      createdAt: note.createdAt,
      updatedAt: note.updatedAt,
      showValidationError: false,
    });
    setActiveTab('notes');
  };

  const handleToggleNoteCardPin = async (
    e: React.MouseEvent,
    note: NoteRecord
  ) => {
    e.stopPropagation();
    await setNotePinnedRecord(note.id, !note.isPinned);
    await reloadRecords();
  };

  const handleSaveNote = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedTitle = noteEditor.title.trim();
    const trimmedContent = noteEditor.content.trim();
    const trimmedCategory = noteEditor.category.trim();

    if (!trimmedTitle || !trimmedContent) {
      setNoteEditor((prev) => ({ ...prev, showValidationError: true }));
      return;
    }

    const now = Date.now();
    const record: NoteRecord = {
      id: noteEditor.id || createUniqueId('note'),
      title: trimmedTitle,
      content: trimmedContent,
      contentFormat: noteEditor.contentFormat,
      isPinned: noteEditor.isPinned,
      category: trimmedCategory,
      createdAt:
        noteEditor.isEditingExisting && noteEditor.createdAt > 0
          ? noteEditor.createdAt
          : now,
      updatedAt: now,
    };

    await saveNoteRecord(record);
    await reloadRecords();
    setNoteEditor(CLOSED_NOTE_EDITOR);
  };

  const requestCloseNoteEditor = () => {
    if (noteHasUnsavedChanges) {
      setPendingCancelTarget({ type: 'close-note' });
    } else {
      setNoteEditor(CLOSED_NOTE_EDITOR);
    }
  };

  // Category Management actions
  const openCategoryManager = () => {
    setCategoryManager({
      isOpen: true,
      newCategoryInput: '',
      renamingCategory: null,
      renameInput: '',
      validationError: null,
      categoryPendingDeletion: null,
    });
  };

  const closeCategoryManager = () => {
    setCategoryManager(CLOSED_CATEGORY_MANAGER);
  };

  const handleCreateCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    const result = await createNoteCategoryRecord(
      categoryManager.newCategoryInput,
      categories
    );
    if (!result.ok) {
      setCategoryManager((prev) => ({
        ...prev,
        validationError: result.error,
      }));
      return;
    }
    await reloadRecords();
    setCategoryManager((prev) => ({
      ...prev,
      newCategoryInput: '',
      validationError: null,
    }));
  };

  const startRenamingCategory = (categoryName: string) => {
    setCategoryManager((prev) => ({
      ...prev,
      renamingCategory: categoryName,
      renameInput: categoryName,
      validationError: null,
    }));
  };

  const cancelRenamingCategory = () => {
    setCategoryManager((prev) => ({
      ...prev,
      renamingCategory: null,
      renameInput: '',
      validationError: null,
    }));
  };

  const handleConfirmRenameCategory = async () => {
    const oldName = categoryManager.renamingCategory;
    if (!oldName) return;
    const result = await renameNoteCategoryRecord(
      oldName,
      categoryManager.renameInput,
      categories
    );
    if (!result.ok) {
      setCategoryManager((prev) => ({
        ...prev,
        validationError: result.error,
      }));
      return;
    }
    const newName = result.category;
    if (
      noteCategoryFilter.type === 'CATEGORY' &&
      noteCategoryFilter.name.toLowerCase() === oldName.toLowerCase()
    ) {
      setNoteCategoryFilter({ type: 'CATEGORY', name: newName });
    }
    if (
      searchCategoryFilter.type === 'CATEGORY' &&
      searchCategoryFilter.name.toLowerCase() === oldName.toLowerCase()
    ) {
      setSearchCategoryFilter({ type: 'CATEGORY', name: newName });
    }
    if (noteEditor.isOpen) {
      setNoteEditor((prev) => ({
        ...prev,
        category:
          prev.category.trim().toLowerCase() === oldName.toLowerCase()
            ? newName
            : prev.category,
        originalCategory:
          prev.originalCategory.trim().toLowerCase() === oldName.toLowerCase()
            ? newName
            : prev.originalCategory,
      }));
    }
    await reloadRecords();
    setCategoryManager((prev) => ({
      ...prev,
      renamingCategory: null,
      renameInput: '',
      validationError: null,
    }));
  };

  const requestDeleteCategory = (categoryName: string) => {
    setCategoryManager((prev) => ({
      ...prev,
      categoryPendingDeletion: categoryName,
      validationError: null,
    }));
  };

  const dismissDeleteCategoryConfirmation = () => {
    setCategoryManager((prev) => ({
      ...prev,
      categoryPendingDeletion: null,
    }));
  };

  const confirmDeleteCategory = async () => {
    const targetCategory = categoryManager.categoryPendingDeletion;
    if (!targetCategory) return;
    await deleteNoteCategoryRecord(targetCategory);

    if (
      noteCategoryFilter.type === 'CATEGORY' &&
      noteCategoryFilter.name.toLowerCase() === targetCategory.toLowerCase()
    ) {
      setNoteCategoryFilter({ type: 'ALL' });
    }
    if (
      searchCategoryFilter.type === 'CATEGORY' &&
      searchCategoryFilter.name.toLowerCase() === targetCategory.toLowerCase()
    ) {
      setSearchCategoryFilter({ type: 'ALL' });
    }
    if (noteEditor.isOpen) {
      setNoteEditor((prev) => ({
        ...prev,
        category:
          prev.category.trim().toLowerCase() === targetCategory.toLowerCase()
            ? ''
            : prev.category,
        originalCategory:
          prev.originalCategory.trim().toLowerCase() ===
          targetCategory.toLowerCase()
            ? ''
            : prev.originalCategory,
      }));
    }
    await reloadRecords();
    setCategoryManager((prev) => ({
      ...prev,
      categoryPendingDeletion: null,
      renamingCategory:
        prev.renamingCategory?.toLowerCase() === targetCategory.toLowerCase()
          ? null
          : prev.renamingCategory,
    }));
  };

  // Journal actions
  const startNewJournalEntry = () => {
    const now = Date.now();
    setJournalEditor({
      isOpen: true,
      isEditingExisting: false,
      id: createUniqueId('journal'),
      title: '',
      content: '',
      isPinned: false,
      originalTitle: '',
      originalContent: '',
      originalIsPinned: false,
      createdAt: now,
      updatedAt: now,
      showValidationError: false,
    });
    setActiveTab('journal');
  };

  const openExistingJournalEntry = (entry: JournalEntryRecord) => {
    setJournalEditor({
      isOpen: true,
      isEditingExisting: true,
      id: entry.id,
      title: entry.title,
      content: entry.content,
      isPinned: Boolean(entry.isPinned),
      originalTitle: entry.title,
      originalContent: entry.content,
      originalIsPinned: Boolean(entry.isPinned),
      createdAt: entry.createdAt,
      updatedAt: entry.updatedAt,
      showValidationError: false,
    });
    setActiveTab('journal');
  };

  const handleToggleJournalCardPin = async (
    e: React.MouseEvent,
    entry: JournalEntryRecord
  ) => {
    e.stopPropagation();
    await setJournalEntryPinnedRecord(entry.id, !entry.isPinned);
    await reloadRecords();
  };

  const handleSaveJournalEntry = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedTitle = journalEditor.title.trim();
    const trimmedContent = journalEditor.content.trim();

    if (!trimmedContent) {
      setJournalEditor((prev) => ({ ...prev, showValidationError: true }));
      return;
    }

    const now = Date.now();
    const record: JournalEntryRecord = {
      id: journalEditor.id || createUniqueId('journal'),
      title: trimmedTitle,
      content: trimmedContent,
      isPinned: journalEditor.isPinned,
      createdAt: journalEditor.createdAt > 0 ? journalEditor.createdAt : now,
      updatedAt: now,
    };

    await saveJournalEntryRecord(record);
    await reloadRecords();
    setJournalEditor(CLOSED_JOURNAL_EDITOR);
  };

  const requestCloseJournalEditor = () => {
    if (journalHasUnsavedChanges) {
      setPendingCancelTarget({ type: 'close-journal' });
    } else {
      setJournalEditor(CLOSED_JOURNAL_EDITOR);
    }
  };

  const confirmDiscardUnsavedChanges = () => {
    if (!pendingCancelTarget) return;
    if (pendingCancelTarget.type === 'close-note') {
      setNoteEditor(CLOSED_NOTE_EDITOR);
    } else if (pendingCancelTarget.type === 'close-journal') {
      setJournalEditor(CLOSED_JOURNAL_EDITOR);
    } else if (pendingCancelTarget.type === 'navigate') {
      setNoteEditor(CLOSED_NOTE_EDITOR);
      setJournalEditor(CLOSED_JOURNAL_EDITOR);
      setActiveTab(pendingCancelTarget.destination);
    }
    setPendingCancelTarget(null);
  };

  const confirmDeleteRecord = async () => {
    if (!deleteTarget) return;
    if (deleteTarget.type === 'note') {
      await deleteNoteRecord(deleteTarget.id);
      setNoteEditor(CLOSED_NOTE_EDITOR);
    } else {
      await deleteJournalEntryRecord(deleteTarget.id);
      setJournalEditor(CLOSED_JOURNAL_EDITOR);
    }
    setDeleteTarget(null);
    await reloadRecords();
  };

  // Scoped Search filtering (ALL / NOTES / JOURNAL) + Category matching + active Search category filter
  const trimmedSearchQuery = searchQuery.trim().toLowerCase();
  const matchingNotes = useMemo(() => {
    if (!trimmedSearchQuery || searchScope === 'JOURNAL') return [];
    const textMatches = rawNotes.filter((n) => {
      const cat = (n.category ?? '').toLowerCase();
      return (
        n.title.toLowerCase().includes(trimmedSearchQuery) ||
        n.content.toLowerCase().includes(trimmedSearchQuery) ||
        cat.includes(trimmedSearchQuery)
      );
    });
    return filterAndSortNotes(
      textMatches,
      searchCategoryFilter,
      'LAST_MODIFIED'
    );
  }, [rawNotes, trimmedSearchQuery, searchScope, searchCategoryFilter]);

  const matchingJournalEntries = useMemo(() => {
    if (!trimmedSearchQuery || searchScope === 'NOTES') return [];
    return journalEntries.filter(
      (e) =>
        e.title.toLowerCase().includes(trimmedSearchQuery) ||
        e.content.toLowerCase().includes(trimmedSearchQuery) ||
        (e.mood ?? '').toLowerCase().includes(trimmedSearchQuery)
    );
  }, [journalEntries, trimmedSearchQuery, searchScope]);

  const editorCategoriesList = useMemo(() => {
    const currentCat = noteEditor.category.trim();
    if (
      currentCat &&
      !categories.some((c) => c.toLowerCase() === currentCat.toLowerCase())
    ) {
      return [...categories, currentCat];
    }
    return categories;
  }, [categories, noteEditor.category]);

  const filterBarItems = useMemo<
    Array<{ key: string; label: string; filter: NoteCategoryFilter }>
  >(() => {
    return [
      { key: 'filter-all', label: 'All notes', filter: { type: 'ALL' } },
      {
        key: 'filter-uncategorized',
        label: 'Uncategorized',
        filter: { type: 'UNCATEGORIZED' },
      },
      ...categories.map((cat) => ({
        key: `filter-cat-${cat.toLowerCase()}`,
        label: cat,
        filter: { type: 'CATEGORY', name: cat } as NoteCategoryFilter,
      })),
    ];
  }, [categories]);

  const notesEmptyState = useMemo(() => {
    if (noteCategoryFilter.type === 'CATEGORY') {
      return {
        title: `No notes in “${noteCategoryFilter.name}”.`,
        description:
          'Assign this category to a note or switch back to All notes.',
      };
    }
    if (noteCategoryFilter.type === 'UNCATEGORIZED' && rawNotes.length > 0) {
      return {
        title: 'No uncategorized notes.',
        description: 'Every saved note currently belongs to a category.',
      };
    }
    return {
      title: 'Nothing here yet.',
      description:
        'Keep ideas, useful information and little things worth remembering.',
    };
  }, [noteCategoryFilter, rawNotes.length]);

  return (
    <div className="min-h-screen bg-[#EAE3D7] dark:bg-[#1C1916] text-[#29231E] dark:text-[#F3EFE6] flex flex-col pb-24 transition-colors duration-150">
      {/* Top Bar Contract: Zone 1 Brand Wordmark — Zone 2 Navigation Links — Zone 3 Theme Action */}
      <header className="sticky top-0 z-30 bg-[#EAE3D7] dark:bg-[#1C1916] border-b border-[#CFC3B2] dark:border-[#3E3730]">
        <div className="max-w-5xl mx-auto flex items-center justify-between gap-8 px-5 sm:px-8 h-14">
          {/* Zone 1: Single text element wordmark */}
          <button
            type="button"
            onClick={() => handleNavigate('home')}
            className="font-editorial text-2xl font-semibold tracking-tight text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
          >
            Aurea
          </button>

          {/* Zone 2: Concise single-line navigation links */}
          <nav
            aria-label="Primary Navigation"
            className="hidden md:flex items-center gap-7 text-sm font-medium text-[#5C5146] dark:text-[#B7AA9A]"
          >
            <button
              type="button"
              onClick={() => handleNavigate('home')}
              className={`whitespace-nowrap shrink-0 transition-colors cursor-pointer py-1 ${
                activeTab === 'home'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold underline underline-offset-8 decoration-[#66503F] dark:decoration-[#D8C6AE] decoration-2'
                  : 'hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
              }`}
            >
              Home
            </button>
            <button
              type="button"
              onClick={() => handleNavigate('notes')}
              className={`whitespace-nowrap shrink-0 transition-colors cursor-pointer py-1 ${
                activeTab === 'notes'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold underline underline-offset-8 decoration-[#66503F] dark:decoration-[#D8C6AE] decoration-2'
                  : 'hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
              }`}
            >
              Notes
            </button>
            <button
              type="button"
              onClick={() => handleNavigate('journal')}
              className={`whitespace-nowrap shrink-0 transition-colors cursor-pointer py-1 ${
                activeTab === 'journal'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold underline underline-offset-8 decoration-[#66503F] dark:decoration-[#D8C6AE] decoration-2'
                  : 'hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
              }`}
            >
              Journal
            </button>
            <button
              type="button"
              onClick={() => handleNavigate('search')}
              className={`whitespace-nowrap shrink-0 transition-colors cursor-pointer py-1 ${
                activeTab === 'search'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold underline underline-offset-8 decoration-[#66503F] dark:decoration-[#D8C6AE] decoration-2'
                  : 'hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
              }`}
            >
              Search
            </button>
          </nav>

          {/* Zone 3: Theme Switcher */}
          <div className="flex items-center shrink-0">
            <button
              type="button"
              onClick={() => setIsDark((prev) => !prev)}
              aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
              className="min-h-[40px] px-3.5 py-1.5 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] hover:bg-[#E3D9CA] dark:hover:bg-[#352F29] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer transition-colors focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
            >
              {isDark ? (
                <>
                  <Sun className="w-3.5 h-3.5 text-[#D8C6AE]" />
                  <span>Light</span>
                </>
              ) : (
                <>
                  <Moon className="w-3.5 h-3.5 text-[#66503F]" />
                  <span>Dark</span>
                </>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Container */}
      <main className="flex-1 w-full max-w-5xl mx-auto px-5 sm:px-8 pt-8 sm:pt-12 pb-16">
        {/* HOME DESTINATION */}
        {activeTab === 'home' && (
          <div className="space-y-8">
            <section className="border-b border-[#CFC3B2] dark:border-[#3E3730] pb-7">
              <h1 className="font-editorial text-5xl sm:text-6xl font-semibold tracking-tight text-[#29231E] dark:text-[#F3EFE6] leading-[1.05]">
                Aurea
              </h1>
              <p className="font-editorial italic font-medium text-2xl sm:text-3xl text-[#66503F] dark:text-[#D8C6AE] mt-2">
                Everything worth keeping.
              </p>
            </section>

            {/* Two Prominent Options: Notes & Journal */}
            <section aria-label="Primary Spaces">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5 items-stretch">
                {/* Prominent Option 1: Notes */}
                <button
                  type="button"
                  onClick={() => handleNavigate('notes')}
                  className="group text-left w-full rounded-[20px] bg-[#66503F] dark:bg-[#55483C] dark:border dark:border-[#3E3730] text-[#F2ECE2] dark:text-[#F3EFE6] p-6 sm:p-7 transition-transform duration-150 active:scale-[0.99] flex flex-col justify-between min-h-[220px] cursor-pointer focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#29231E] dark:focus-visible:outline-[#D8C6AE]"
                >
                  <div className="flex items-center justify-between w-full">
                    <span className="w-11 h-11 rounded-xl bg-[#29231E]/28 flex items-center justify-center text-[#F2ECE2] dark:text-[#F3EFE6]">
                      <FileText className="w-5 h-5" />
                    </span>
                    <ArrowRight className="w-5 h-5 text-[#F2ECE2] dark:text-[#F3EFE6] group-hover:translate-x-1 transition-transform" />
                  </div>

                  <div className="my-5">
                    <h2 className="font-editorial text-3xl sm:text-4xl font-medium text-[#F2ECE2] dark:text-[#F3EFE6]">
                      Notes
                    </h2>
                    <p className="text-[15px] text-[#E3D9CA] dark:text-[#EBE4D6] mt-2 leading-relaxed">
                      Quick thoughts, outlines, and structured observations.
                    </p>
                  </div>

                  <p className="text-xs font-medium text-[#E3D9CA] dark:text-[#EBE4D6] tracking-wide tabular-nums">
                    Drafts · Lists · Commonplace
                    {isLoaded && rawNotes.length > 0
                      ? ` · ${rawNotes.length} saved`
                      : ''}
                  </p>
                </button>

                {/* Prominent Option 2: Journal */}
                <button
                  type="button"
                  onClick={() => handleNavigate('journal')}
                  className="group text-left w-full rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE] text-[#29231E] dark:text-[#F3EFE6] p-6 sm:p-7 transition-all duration-150 active:scale-[0.99] flex flex-col justify-between min-h-[220px] cursor-pointer focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
                >
                  <div className="flex items-center justify-between w-full">
                    <span className="w-11 h-11 rounded-xl bg-[#E3D9CA] dark:bg-[#352F29] flex items-center justify-center text-[#66503F] dark:text-[#D8C6AE]">
                      <BookOpen className="w-5 h-5" />
                    </span>
                    <ArrowRight className="w-5 h-5 text-[#66503F] dark:text-[#D8C6AE] group-hover:translate-x-1 transition-transform" />
                  </div>

                  <div className="my-5">
                    <h2 className="font-editorial text-3xl sm:text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      Journal
                    </h2>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-2 leading-relaxed">
                      Unhurried daily reflections and chronological entries.
                    </p>
                  </div>

                  <p className="text-xs font-medium text-[#665A4E] dark:text-[#B7AA9A] tracking-wide tabular-nums">
                    Daily pages · Reflections · Archive
                    {isLoaded && journalEntries.length > 0
                      ? ` · ${journalEntries.length} saved`
                      : ''}
                  </p>
                </button>
              </div>
            </section>

            {/* Pinned & Recent Section — Hidden when no user-created records exist */}
            {isLoaded && homeHighlights.length > 0 && (
              <section
                aria-label="Pinned and Recent"
                className="pt-2 border-t border-[#CFC3B2] dark:border-[#3E3730]"
              >
                <h2 className="font-editorial text-2xl sm:text-3xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-4 mb-4">
                  Pinned &amp; Recent
                </h2>

                <div className="space-y-3">
                  {homeHighlights.map((item) => {
                    if (item.kind === 'note') {
                      const note = item.note;
                      const noteCategory = (note.category ?? '').trim();
                      return (
                        <article
                          key={item.idKey}
                          onClick={() => openExistingNote(note)}
                          className="rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE] p-5 transition-colors cursor-pointer"
                        >
                          <p className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] tabular-nums">
                            {note.isPinned ? 'Pinned · ' : ''}
                            Notes
                            {noteCategory ? ` · ${noteCategory}` : ''}
                            {' · '}
                            {formatEditorialTimestamp(note.updatedAt)}
                          </p>
                          <h3 className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-1 line-clamp-1">
                            {note.title}
                          </h3>
                          <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1 line-clamp-2 whitespace-pre-line">
                            {note.content}
                          </p>
                        </article>
                      );
                    }

                    const entry = item.entry;
                    const displayTitle =
                      entry.title.trim() || 'Untitled Reflection';
                    return (
                      <article
                        key={item.idKey}
                        onClick={() => openExistingJournalEntry(entry)}
                        className="rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE] p-5 transition-colors cursor-pointer"
                      >
                        <p className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] tabular-nums">
                          {entry.isPinned ? 'Pinned · ' : ''}
                          Journal · {formatEditorialTimestamp(entry.createdAt)}
                        </p>
                        <h3 className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-1 line-clamp-1">
                          {displayTitle}
                        </h3>
                        <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1 line-clamp-2 whitespace-pre-line">
                          {entry.content}
                        </p>
                      </article>
                    );
                  })}
                </div>
              </section>
            )}
          </div>
        )}

        {/* NOTES DESTINATION */}
        {activeTab === 'notes' && (
          <div>
            {noteEditor.isOpen ? (
              /* NOTE EDITOR */
              <form onSubmit={handleSaveNote} className="space-y-6">
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#CFC3B2] dark:border-[#3E3730] pb-5">
                  <div>
                    <button
                      type="button"
                      onClick={requestCloseNoteEditor}
                      className="inline-flex items-center gap-1.5 text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] mb-2 cursor-pointer"
                    >
                      <ArrowLeft className="w-3.5 h-3.5" />
                      <span>Back to Notes</span>
                    </button>
                    <h1 className="font-editorial text-3xl sm:text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      {noteEditor.isEditingExisting ? 'Edit Note' : 'New Note'}
                    </h1>
                    {noteEditor.isEditingExisting && noteEditor.updatedAt > 0 && (
                      <p className="text-xs text-[#665A4E] dark:text-[#B7AA9A] mt-1 tabular-nums">
                        Modified {formatEditorialTimestamp(noteEditor.updatedAt)}
                      </p>
                    )}
                  </div>

                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={() =>
                        setNoteEditor((prev) => ({
                          ...prev,
                          isPinned: !prev.isPinned,
                        }))
                      }
                      aria-label={noteEditor.isPinned ? 'Unpin note' : 'Pin note'}
                      className={`min-h-[44px] px-4 py-2 rounded-xl border text-xs font-medium flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer transition-colors ${
                        noteEditor.isPinned
                          ? 'border-[#66503F] dark:border-[#D8C6AE] bg-[#E3D9CA] dark:bg-[#352F29] text-[#29231E] dark:text-[#F3EFE6]'
                          : 'border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] text-[#66503F] dark:text-[#D8C6AE]'
                      }`}
                    >
                      <Bookmark
                        className={`w-4 h-4 ${
                          noteEditor.isPinned ? 'fill-current' : ''
                        }`}
                      />
                      <span>{noteEditor.isPinned ? 'Pinned' : 'Pin'}</span>
                    </button>

                    {noteEditor.isEditingExisting && (
                      <button
                        type="button"
                        onClick={() =>
                          setDeleteTarget({
                            type: 'note',
                            id: noteEditor.id,
                            title: noteEditor.title || 'this note',
                          })
                        }
                        className="min-h-[44px] px-4 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] hover:border-[#66503F] dark:hover:border-[#D8C6AE] text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                        <span>Delete</span>
                      </button>
                    )}
                  </div>
                </div>

                {noteEditor.showValidationError && (
                  <div
                    role="alert"
                    className="rounded-xl bg-[#E3D9CA] dark:bg-[#352F29] border border-[#66503F] dark:border-[#D8C6AE] px-4 py-3 text-sm text-[#29231E] dark:text-[#F3EFE6]"
                  >
                    Please enter a title and note content before saving.
                  </div>
                )}

                <div className="space-y-4">
                  <div>
                    <label
                      htmlFor="note-title-input"
                      className="block text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] mb-1.5"
                    >
                      Title
                    </label>
                    <input
                      id="note-title-input"
                      type="text"
                      value={noteEditor.title}
                      onChange={(e) =>
                        setNoteEditor((prev) => ({
                          ...prev,
                          title: e.target.value,
                          showValidationError: false,
                        }))
                      }
                      placeholder="Give this note a clear title…"
                      className="w-full min-h-[48px] px-4 py-3 rounded-[14px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none font-editorial text-xl text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A]"
                    />
                  </div>

                  {/* Optional Category Selector */}
                  <div>
                    <div className="flex items-center justify-between gap-2 mb-1.5">
                      <span className="text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A]">
                        Category (Optional)
                      </span>
                      <div className="flex items-center gap-3">
                        {noteEditor.category.trim().length > 0 && (
                          <button
                            type="button"
                            onClick={() =>
                              setNoteEditor((prev) => ({
                                ...prev,
                                category: '',
                              }))
                            }
                            className="text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] cursor-pointer"
                          >
                            Clear
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={openCategoryManager}
                          className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] hover:underline cursor-pointer"
                        >
                          Categories
                        </button>
                      </div>
                    </div>

                    <div
                      role="group"
                      aria-label="Note category selector"
                      className="flex flex-wrap items-center gap-2"
                    >
                      <button
                        type="button"
                        onClick={() =>
                          setNoteEditor((prev) => ({ ...prev, category: '' }))
                        }
                        className={`min-h-[36px] px-3 py-1.5 rounded-[10px] border text-xs font-medium transition-colors cursor-pointer ${
                          noteEditor.category.trim().length === 0
                            ? 'bg-[#66503F] dark:bg-[#D8C6AE] border-[#66503F] dark:border-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916]'
                            : 'bg-[#F2ECE2] dark:bg-[#26221E] border-[#CFC3B2] dark:border-[#3E3730] text-[#5C5146] dark:text-[#B7AA9A] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                        }`}
                      >
                        None (Uncategorized)
                      </button>

                      {editorCategoriesList.map((cat) => {
                        const isSelected =
                          noteEditor.category.trim().toLowerCase() ===
                          cat.toLowerCase();
                        return (
                          <button
                            key={`editor-cat-${cat.toLowerCase()}`}
                            type="button"
                            onClick={() =>
                              setNoteEditor((prev) => ({
                                ...prev,
                                category: cat,
                              }))
                            }
                            className={`min-h-[36px] px-3 py-1.5 rounded-[10px] border text-xs font-medium transition-colors cursor-pointer ${
                              isSelected
                                ? 'bg-[#66503F] dark:bg-[#D8C6AE] border-[#66503F] dark:border-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916]'
                                : 'bg-[#F2ECE2] dark:bg-[#26221E] border-[#CFC3B2] dark:border-[#3E3730] text-[#5C5146] dark:text-[#B7AA9A] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                            }`}
                          >
                            {cat}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div>
                    <label
                      htmlFor="note-content-input"
                      className="block text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] mb-1.5"
                    >
                      Note
                    </label>
                    <textarea
                      id="note-content-input"
                      rows={10}
                      value={noteEditor.content}
                      onChange={(e) =>
                        setNoteEditor((prev) => ({
                          ...prev,
                          content: e.target.value,
                          showValidationError: false,
                        }))
                      }
                      placeholder="Write your thoughts, outlines or observations…"
                      className="w-full p-4 rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none text-[15px] leading-relaxed text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A]"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <button
                    type="button"
                    onClick={requestCloseNoteEditor}
                    className="min-h-[44px] px-5 py-2.5 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="min-h-[44px] px-6 py-2.5 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium hover:opacity-95 whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    Save
                  </button>
                </div>
              </form>
            ) : (
              /* NOTES LIST VIEW */
              <div className="space-y-5 relative">
                <div className="border-b border-[#CFC3B2] dark:border-[#3E3730] pb-6 flex flex-wrap items-end justify-between gap-4">
                  <div>
                    <h1 className="font-editorial text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      Notes
                    </h1>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1.5">
                      Structured observations and fragments.
                    </p>
                  </div>

                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={openCategoryManager}
                      className="min-h-[44px] px-4 py-2.5 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] hover:border-[#66503F] dark:hover:border-[#D8C6AE] text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer transition-colors"
                    >
                      <Tag className="w-3.5 h-3.5" />
                      <span>Categories</span>
                    </button>

                    <button
                      type="button"
                      onClick={startNewNote}
                      className="min-h-[44px] px-5 py-2.5 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer hover:opacity-95 transition-opacity"
                    >
                      <Plus className="w-4 h-4" />
                      <span>New Note</span>
                    </button>
                  </div>
                </div>

                {/* Category Filter Bar */}
                <div
                  role="group"
                  aria-label="Filter notes by category"
                  className="flex flex-wrap items-center gap-2"
                >
                  {filterBarItems.map((item) => {
                    const isSelected = isSameCategoryFilter(
                      noteCategoryFilter,
                      item.filter
                    );
                    return (
                      <button
                        key={item.key}
                        type="button"
                        onClick={() => setNoteCategoryFilter(item.filter)}
                        className={`min-h-[36px] px-3.5 py-1.5 rounded-[10px] border text-xs font-medium transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                          isSelected
                            ? 'bg-[#66503F] dark:bg-[#D8C6AE] border-[#66503F] dark:border-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916]'
                            : 'bg-[#F2ECE2] dark:bg-[#26221E] border-[#CFC3B2] dark:border-[#3E3730] text-[#5C5146] dark:text-[#B7AA9A] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                        }`}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>

                {/* Notes Sorting Selector */}
                {rawNotes.length > 0 && (
                  <div className="flex items-center">
                    <div
                      role="group"
                      aria-label="Sort notes"
                      className="inline-flex items-center gap-1 p-1 rounded-xl bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730]"
                    >
                      {(
                        [
                          ['LAST_MODIFIED', 'Last Modified'],
                          ['CREATED_DATE', 'Created Date'],
                          ['TITLE_ASC', 'Title A–Z'],
                        ] as Array<[NoteSortOrder, string]>
                      ).map(([order, label]) => {
                        const isSelected = noteSortOrder === order;
                        return (
                          <button
                            key={order}
                            type="button"
                            onClick={() => setNoteSortOrder(order)}
                            className={`min-h-[36px] px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                              isSelected
                                ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#29231E] dark:text-[#F3EFE6]'
                                : 'text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
                            }`}
                          >
                            {label}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {notes.length === 0 ? (
                  <div className="rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] px-7 py-10">
                    <h2 className="font-editorial text-2xl sm:text-3xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      {notesEmptyState.title}
                    </h2>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-2.5 leading-relaxed max-w-lg">
                      {notesEmptyState.description}
                    </p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {notes.map((note) => {
                      const noteCategory = (note.category ?? '').trim();
                      return (
                        <article
                          key={note.id}
                          onClick={() => openExistingNote(note)}
                          className={`group rounded-[18px] bg-[#F2ECE2] dark:bg-[#26221E] border p-5 transition-colors cursor-pointer flex flex-col justify-between ${
                            note.isPinned
                              ? 'border-[#66503F] dark:border-[#D8C6AE]'
                              : 'border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                          }`}
                        >
                          <div>
                            <div className="flex items-start justify-between gap-3">
                              <h2 className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6] line-clamp-1">
                                {note.title}
                              </h2>

                              <button
                                type="button"
                                onClick={(e) => handleToggleNoteCardPin(e, note)}
                                aria-label={
                                  note.isPinned ? 'Unpin note' : 'Pin note'
                                }
                                className="min-h-[40px] min-w-[40px] -mt-1.5 -mr-1.5 rounded-xl flex items-center justify-center text-[#66503F] dark:text-[#D8C6AE] hover:bg-[#E3D9CA] dark:hover:bg-[#352F29] transition-colors shrink-0 cursor-pointer"
                              >
                                <Bookmark
                                  className={`w-4 h-4 ${
                                    note.isPinned ? 'fill-current' : 'opacity-70'
                                  }`}
                                />
                              </button>
                            </div>

                            {noteCategory && (
                              <span className="inline-block mt-1 px-2.5 py-0.5 rounded-lg bg-[#E3D9CA] dark:bg-[#352F29] border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#66503F] dark:text-[#D8C6AE]">
                                {noteCategory}
                              </span>
                            )}

                            <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-2 leading-relaxed line-clamp-2 whitespace-pre-line">
                              {note.content}
                            </p>
                          </div>

                          <p
                            className={`text-xs font-medium mt-4 pt-3 border-t border-[#CFC3B2]/60 dark:border-[#3E3730] tabular-nums ${
                              note.isPinned
                                ? 'text-[#66503F] dark:text-[#D8C6AE]'
                                : 'text-[#665A4E] dark:text-[#B7AA9A]'
                            }`}
                          >
                            {note.isPinned ? 'Pinned · ' : ''}
                            {noteCategory ? `${noteCategory} · ` : ''}
                            Modified {formatEditorialTimestamp(note.updatedAt)}
                          </p>
                        </article>
                      );
                    })}
                  </div>
                )}

                {/* Floating Action Button for mobile thumb reach */}
                <button
                  type="button"
                  onClick={startNewNote}
                  aria-label="New Note"
                  className="fixed bottom-20 right-6 z-30 min-h-[52px] px-5 py-3 rounded-2xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-semibold flex items-center gap-2 cursor-pointer active:scale-[0.98] transition-transform"
                >
                  <Plus className="w-4 h-4" />
                  <span>New Note</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* JOURNAL DESTINATION */}
        {activeTab === 'journal' && (
          <div>
            {journalEditor.isOpen ? (
              /* JOURNAL EDITOR */
              <form onSubmit={handleSaveJournalEntry} className="space-y-6">
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#CFC3B2] dark:border-[#3E3730] pb-5">
                  <div>
                    <button
                      type="button"
                      onClick={requestCloseJournalEditor}
                      className="inline-flex items-center gap-1.5 text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] mb-2 cursor-pointer"
                    >
                      <ArrowLeft className="w-3.5 h-3.5" />
                      <span>Back to Journal</span>
                    </button>
                    <h1 className="font-editorial text-3xl sm:text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      {journalEditor.isEditingExisting
                        ? 'Journal Entry'
                        : 'New Journal Entry'}
                    </h1>
                    <p className="text-xs text-[#665A4E] dark:text-[#B7AA9A] mt-1 tabular-nums">
                      Created {formatEditorialTimestamp(journalEditor.createdAt)}
                      {' · '}
                      Last modified{' '}
                      {formatEditorialTimestamp(journalEditor.updatedAt)}
                    </p>
                  </div>

                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={() =>
                        setJournalEditor((prev) => ({
                          ...prev,
                          isPinned: !prev.isPinned,
                        }))
                      }
                      aria-label={
                        journalEditor.isPinned
                          ? 'Unpin journal entry'
                          : 'Pin journal entry'
                      }
                      className={`min-h-[44px] px-4 py-2 rounded-xl border text-xs font-medium flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer transition-colors ${
                        journalEditor.isPinned
                          ? 'border-[#66503F] dark:border-[#D8C6AE] bg-[#E3D9CA] dark:bg-[#352F29] text-[#29231E] dark:text-[#F3EFE6]'
                          : 'border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] text-[#66503F] dark:text-[#D8C6AE]'
                      }`}
                    >
                      <Bookmark
                        className={`w-4 h-4 ${
                          journalEditor.isPinned ? 'fill-current' : ''
                        }`}
                      />
                      <span>{journalEditor.isPinned ? 'Pinned' : 'Pin'}</span>
                    </button>

                    {journalEditor.isEditingExisting && (
                      <button
                        type="button"
                        onClick={() =>
                          setDeleteTarget({
                            type: 'journal',
                            id: journalEditor.id,
                            title:
                              journalEditor.title.trim() ||
                              'Untitled Reflection',
                          })
                        }
                        className="min-h-[44px] px-4 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] hover:border-[#66503F] dark:hover:border-[#D8C6AE] text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                        <span>Delete</span>
                      </button>
                    )}
                  </div>
                </div>

                {journalEditor.showValidationError && (
                  <div
                    role="alert"
                    className="rounded-xl bg-[#E3D9CA] dark:bg-[#352F29] border border-[#66503F] dark:border-[#D8C6AE] px-4 py-3 text-sm text-[#29231E] dark:text-[#F3EFE6]"
                  >
                    Please write your journal entry before saving.
                  </div>
                )}

                <div className="space-y-4">
                  <div>
                    <label
                      htmlFor="journal-title-input"
                      className="block text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] mb-1.5"
                    >
                      Title (Optional)
                    </label>
                    <input
                      id="journal-title-input"
                      type="text"
                      value={journalEditor.title}
                      onChange={(e) =>
                        setJournalEditor((prev) => ({
                          ...prev,
                          title: e.target.value,
                        }))
                      }
                      placeholder="Optional title for this reflection…"
                      className="w-full min-h-[48px] px-4 py-3 rounded-[14px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none font-editorial text-xl text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A]"
                    />
                  </div>

                  <div>
                    <label
                      htmlFor="journal-content-input"
                      className="block text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] mb-1.5"
                    >
                      Entry
                    </label>
                    <textarea
                      id="journal-content-input"
                      rows={11}
                      value={journalEditor.content}
                      onChange={(e) =>
                        setJournalEditor((prev) => ({
                          ...prev,
                          content: e.target.value,
                          showValidationError: false,
                        }))
                      }
                      placeholder="Begin writing your reflection…"
                      className="w-full p-4 rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none text-[15px] leading-relaxed text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A]"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  <button
                    type="button"
                    onClick={requestCloseJournalEditor}
                    className="min-h-[44px] px-5 py-2.5 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] bg-[#F2ECE2] dark:bg-[#26221E] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="min-h-[44px] px-6 py-2.5 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium hover:opacity-95 whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    Save
                  </button>
                </div>
              </form>
            ) : (
              /* JOURNAL LIST VIEW (Pinned First, then Chronological Newest First) */
              <div className="space-y-8 relative">
                <div className="border-b border-[#CFC3B2] dark:border-[#3E3730] pb-6 flex flex-wrap items-end justify-between gap-4">
                  <div>
                    <h1 className="font-editorial text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      Journal
                    </h1>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1.5">
                      Unhurried pages for daily reflection.
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={startNewJournalEntry}
                    className="min-h-[44px] px-5 py-2.5 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium flex items-center gap-2 whitespace-nowrap shrink-0 cursor-pointer hover:opacity-95 transition-opacity"
                  >
                    <Plus className="w-4 h-4" />
                    <span>New Entry</span>
                  </button>
                </div>

                {journalEntries.length === 0 ? (
                  <div className="rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] px-7 py-10">
                    <h2 className="font-editorial text-2xl sm:text-3xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                      Your story starts here.
                    </h2>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-2.5 leading-relaxed max-w-lg">
                      Capture your thoughts, experiences and moments worth
                      keeping.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {journalEntries.map((entry) => (
                      <article
                        key={entry.id}
                        onClick={() => openExistingJournalEntry(entry)}
                        className={`rounded-[18px] bg-[#F2ECE2] dark:bg-[#26221E] border p-5 sm:p-6 transition-colors cursor-pointer ${
                          entry.isPinned
                            ? 'border-[#66503F] dark:border-[#D8C6AE]'
                            : 'border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                        }`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] tabular-nums">
                              {entry.isPinned ? 'Pinned · ' : ''}
                              Created {formatEditorialTimestamp(entry.createdAt)}
                            </p>
                            <h2 className="font-editorial text-2xl sm:text-3xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-1.5 line-clamp-1">
                              {entry.title.trim() || 'Untitled Reflection'}
                            </h2>
                          </div>

                          <button
                            type="button"
                            onClick={(e) =>
                              handleToggleJournalCardPin(e, entry)
                            }
                            aria-label={
                              entry.isPinned
                                ? 'Unpin journal entry'
                                : 'Pin journal entry'
                            }
                            className="min-h-[40px] min-w-[40px] -mt-1 -mr-1.5 rounded-xl flex items-center justify-center text-[#66503F] dark:text-[#D8C6AE] hover:bg-[#E3D9CA] dark:hover:bg-[#352F29] transition-colors shrink-0 cursor-pointer"
                          >
                            <Bookmark
                              className={`w-4 h-4 ${
                                entry.isPinned ? 'fill-current' : 'opacity-70'
                              }`}
                            />
                          </button>
                        </div>

                        <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-2 leading-relaxed line-clamp-2 whitespace-pre-line">
                          {entry.content}
                        </p>
                      </article>
                    ))}
                  </div>
                )}

                {/* Floating Action Button for mobile thumb reach */}
                <button
                  type="button"
                  onClick={startNewJournalEntry}
                  aria-label="New Journal Entry"
                  className="fixed bottom-20 right-6 z-30 min-h-[52px] px-5 py-3 rounded-2xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-semibold flex items-center gap-2 cursor-pointer active:scale-[0.98] transition-transform"
                >
                  <Plus className="w-4 h-4" />
                  <span>New Entry</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* SEARCH DESTINATION */}
        {activeTab === 'search' && (
          <div className="space-y-6">
            <div className="border-b border-[#CFC3B2] dark:border-[#3E3730] pb-6">
              <h1 className="font-editorial text-4xl font-medium text-[#29231E] dark:text-[#F3EFE6]">
                Search
              </h1>
              <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1.5">
                Find notes and journal entries.
              </p>

              <div className="mt-5 relative">
                <Search className="w-4 h-4 text-[#66503F] dark:text-[#D8C6AE] absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search titles, passages, categories, or dates..."
                  className="w-full min-h-[48px] pl-11 pr-11 py-3 rounded-[14px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none text-[15px] text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A] disabled:bg-[#E3D9CA] disabled:text-[#817565] disabled:placeholder:text-[#817565]"
                />
                {searchQuery.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setSearchQuery('')}
                    aria-label="Clear search query"
                    className="min-h-[40px] min-w-[40px] absolute right-1.5 top-1/2 -translate-y-1/2 rounded-lg flex items-center justify-center text-[#665A4E] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>

              {/* Search Scope Segmented Filter (All / Notes / Journal) */}
              <div className="mt-3.5">
                <div
                  role="group"
                  aria-label="Filter search by type"
                  className="inline-flex items-center gap-1 p-1 rounded-xl bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730]"
                >
                  {(
                    [
                      ['ALL', 'All'],
                      ['NOTES', 'Notes'],
                      ['JOURNAL', 'Journal'],
                    ] as Array<[SearchScope, string]>
                  ).map(([scope, label]) => {
                    const isSelected = searchScope === scope;
                    return (
                      <button
                        key={scope}
                        type="button"
                        onClick={() => setSearchScope(scope)}
                        className={`min-h-[36px] px-3.5 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                          isSelected
                            ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#29231E] dark:text-[#F3EFE6]'
                            : 'text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6]'
                        }`}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Search Note Category Filter (when searching All or Notes and categories exist) */}
              {searchScope !== 'JOURNAL' && categories.length > 0 && (
                <div
                  role="group"
                  aria-label="Filter search notes by category"
                  className="mt-3 flex flex-wrap items-center gap-2"
                >
                  {filterBarItems.map((item) => {
                    const isSelected = isSameCategoryFilter(
                      searchCategoryFilter,
                      item.filter
                    );
                    return (
                      <button
                        key={`search-${item.key}`}
                        type="button"
                        onClick={() => setSearchCategoryFilter(item.filter)}
                        className={`min-h-[34px] px-3 py-1 rounded-[10px] border text-xs font-medium transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                          isSelected
                            ? 'bg-[#66503F] dark:bg-[#D8C6AE] border-[#66503F] dark:border-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916]'
                            : 'bg-[#F2ECE2] dark:bg-[#26221E] border-[#CFC3B2] dark:border-[#3E3730] text-[#5C5146] dark:text-[#B7AA9A] hover:border-[#66503F] dark:hover:border-[#D8C6AE]'
                        }`}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {!trimmedSearchQuery ? (
              <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A]">
                Enter a word or phrase to search across your saved notes and
                journal entries.
              </p>
            ) : matchingNotes.length === 0 &&
              matchingJournalEntries.length === 0 ? (
              <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A]">
                No matching notes or journal entries found.
              </p>
            ) : (
              <div className="space-y-4">
                {matchingNotes.map((note) => {
                  const noteCategory = (note.category ?? '').trim();
                  return (
                    <article
                      key={`search-note-${note.id}`}
                      onClick={() => openExistingNote(note)}
                      className="rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE] p-5 transition-colors cursor-pointer"
                    >
                      <p className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] tabular-nums">
                        {note.isPinned ? 'Pinned · ' : ''}
                        Notes
                        {noteCategory ? ` · ${noteCategory}` : ''}
                        {' · '}
                        Modified {formatEditorialTimestamp(note.updatedAt)}
                      </p>
                      <h2 className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-1 line-clamp-1">
                        {note.title}
                      </h2>
                      <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1 line-clamp-2">
                        {note.content}
                      </p>
                    </article>
                  );
                })}

                {matchingJournalEntries.map((entry) => (
                  <article
                    key={`search-journal-${entry.id}`}
                    onClick={() => openExistingJournalEntry(entry)}
                    className="rounded-[16px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] hover:border-[#66503F] dark:hover:border-[#D8C6AE] p-5 transition-colors cursor-pointer"
                  >
                    <p className="text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] tabular-nums">
                      {entry.isPinned ? 'Pinned · ' : ''}
                      Journal · Created{' '}
                      {formatEditorialTimestamp(entry.createdAt)}
                    </p>
                    <h2 className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6] mt-1 line-clamp-1">
                      {entry.title.trim() || 'Untitled Reflection'}
                    </h2>
                    <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] mt-1 line-clamp-2">
                      {entry.content}
                    </p>
                  </article>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      {/* Category Management Dialog */}
      {categoryManager.isOpen && !categoryManager.categoryPendingDeletion && (
        <div
          className="fixed inset-0 z-50 bg-[#29231E]/50 flex items-center justify-center p-5"
          onClick={closeCategoryManager}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="category-manager-title"
            className="w-full max-w-lg rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] p-6 space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2
                  id="category-manager-title"
                  className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6]"
                >
                  Manage Categories
                </h2>
                <p className="text-sm text-[#5C5146] dark:text-[#B7AA9A] mt-1">
                  Create, rename, or remove categories for your notes library.
                </p>
              </div>
              <button
                type="button"
                onClick={closeCategoryManager}
                aria-label="Close category manager"
                className="min-h-[36px] min-w-[36px] rounded-xl flex items-center justify-center text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form
              onSubmit={handleCreateCategory}
              className="flex items-center gap-2.5"
            >
              <input
                type="text"
                value={categoryManager.newCategoryInput}
                onChange={(e) =>
                  setCategoryManager((prev) => ({
                    ...prev,
                    newCategoryInput: e.target.value,
                    validationError: null,
                  }))
                }
                placeholder="New category name…"
                className="flex-1 min-h-[44px] px-3.5 py-2 rounded-xl bg-[#EAE3D7] dark:bg-[#1C1916] border border-[#CFC3B2] dark:border-[#3E3730] focus:border-[#66503F] dark:focus:border-[#D8C6AE] focus:outline-none text-sm text-[#29231E] dark:text-[#F3EFE6] placeholder:text-[#665A4E] dark:placeholder:text-[#B7AA9A]"
              />
              <button
                type="submit"
                className="min-h-[44px] px-4 py-2 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer"
              >
                Add
              </button>
            </form>

            {categoryManager.validationError && (
              <div
                role="alert"
                className="rounded-xl bg-[#E3D9CA] dark:bg-[#352F29] border border-[#66503F] dark:border-[#D8C6AE] px-3.5 py-2.5 text-xs text-[#29231E] dark:text-[#F3EFE6]"
              >
                {getCategoryValidationErrorMessage(
                  categoryManager.validationError
                )}
              </div>
            )}

            <div className="border-t border-[#CFC3B2] dark:border-[#3E3730] pt-4">
              {categories.length === 0 ? (
                <p className="text-sm text-[#5C5146] dark:text-[#B7AA9A]">
                  No custom categories yet. Add one above to organize your notes.
                </p>
              ) : (
                <div className="max-h-64 overflow-y-auto space-y-2 pr-1">
                  {categories.map((cat) => {
                    const isRenaming =
                      categoryManager.renamingCategory?.toLowerCase() ===
                      cat.toLowerCase();
                    return (
                      <div
                        key={`manage-cat-${cat.toLowerCase()}`}
                        className="rounded-xl bg-[#EAE3D7] dark:bg-[#1C1916] border border-[#CFC3B2] dark:border-[#3E3730] px-3.5 py-2.5"
                      >
                        {isRenaming ? (
                          <div className="space-y-2.5">
                            <input
                              type="text"
                              value={categoryManager.renameInput}
                              onChange={(e) =>
                                setCategoryManager((prev) => ({
                                  ...prev,
                                  renameInput: e.target.value,
                                  validationError: null,
                                }))
                              }
                              aria-label="Rename category"
                              className="w-full min-h-[40px] px-3 py-1.5 rounded-lg bg-[#F2ECE2] dark:bg-[#26221E] border border-[#66503F] dark:border-[#D8C6AE] focus:outline-none text-sm text-[#29231E] dark:text-[#F3EFE6]"
                            />
                            <div className="flex items-center justify-end gap-2">
                              <button
                                type="button"
                                onClick={cancelRenamingCategory}
                                className="min-h-[34px] px-3 py-1 rounded-lg border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#5C5146] dark:text-[#B7AA9A] cursor-pointer"
                              >
                                Cancel
                              </button>
                              <button
                                type="button"
                                onClick={handleConfirmRenameCategory}
                                className="min-h-[34px] px-3.5 py-1 rounded-lg bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium cursor-pointer"
                              >
                                Save
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-sm font-medium text-[#29231E] dark:text-[#F3EFE6] truncate">
                              {cat}
                            </span>
                            <div className="flex items-center gap-1 shrink-0">
                              <button
                                type="button"
                                onClick={() => startRenamingCategory(cat)}
                                aria-label={`Rename ${cat}`}
                                className="min-h-[34px] min-w-[34px] rounded-lg flex items-center justify-center text-[#66503F] dark:text-[#D8C6AE] hover:bg-[#E3D9CA] dark:hover:bg-[#352F29] cursor-pointer"
                              >
                                <Pencil className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => requestDeleteCategory(cat)}
                                aria-label={`Delete ${cat}`}
                                className="min-h-[34px] min-w-[34px] rounded-lg flex items-center justify-center text-[#5C5146] dark:text-[#B7AA9A] hover:text-[#29231E] dark:hover:text-[#F3EFE6] hover:bg-[#E3D9CA] dark:hover:bg-[#352F29] cursor-pointer"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end pt-1">
              <button
                type="button"
                onClick={closeCategoryManager}
                className="min-h-[40px] px-5 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#66503F] dark:text-[#D8C6AE] cursor-pointer"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Category Confirmation Dialog */}
      {categoryManager.categoryPendingDeletion && (
        <div
          className="fixed inset-0 z-50 bg-[#29231E]/50 flex items-center justify-center p-5"
          onClick={dismissDeleteCategoryConfirmation}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-category-dialog-title"
            className="w-full max-w-md rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] p-6 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h2
              id="delete-category-dialog-title"
              className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6]"
            >
              Remove category “{categoryManager.categoryPendingDeletion}”?
            </h2>
            <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] leading-relaxed">
              Notes in “{categoryManager.categoryPendingDeletion}” will not be
              deleted. They will become Uncategorized and keep their content,
              timestamps, and pin state.
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={dismissDeleteCategoryConfirmation}
                className="min-h-[44px] px-4 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmDeleteCategory}
                className="min-h-[44px] px-5 py-2 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Unsaved Changes Confirmation Dialog */}
      {pendingCancelTarget && (
        <div
          className="fixed inset-0 z-50 bg-[#29231E]/50 flex items-center justify-center p-5"
          onClick={() => setPendingCancelTarget(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="discard-dialog-title"
            className="w-full max-w-md rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] p-6 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h2
              id="discard-dialog-title"
              className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6]"
            >
              Discard unsaved changes?
            </h2>
            <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] leading-relaxed">
              You have unsaved edits. If you leave now, your changes will be
              lost.
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setPendingCancelTarget(null)}
                className="min-h-[44px] px-4 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer"
              >
                Keep Editing
              </button>
              <button
                type="button"
                onClick={confirmDiscardUnsavedChanges}
                className="min-h-[44px] px-5 py-2 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer"
              >
                Discard Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Dialog */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 bg-[#29231E]/50 flex items-center justify-center p-5"
          onClick={() => setDeleteTarget(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-dialog-title"
            className="w-full max-w-md rounded-[20px] bg-[#F2ECE2] dark:bg-[#26221E] border border-[#CFC3B2] dark:border-[#3E3730] p-6 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h2
              id="delete-dialog-title"
              className="font-editorial text-2xl font-medium text-[#29231E] dark:text-[#F3EFE6]"
            >
              {deleteTarget.type === 'note'
                ? 'Delete this note?'
                : 'Delete this journal entry?'}
            </h2>
            <p className="text-[15px] text-[#5C5146] dark:text-[#B7AA9A] leading-relaxed">
              {deleteTarget.type === 'note'
                ? 'This note will be permanently removed from your device.'
                : 'This journal entry will be permanently removed from your device.'}
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="min-h-[44px] px-4 py-2 rounded-xl border border-[#CFC3B2] dark:border-[#3E3730] text-xs font-medium text-[#29231E] dark:text-[#F3EFE6] whitespace-nowrap shrink-0 cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmDeleteRecord}
                className="min-h-[44px] px-5 py-2 rounded-xl bg-[#66503F] dark:bg-[#D8C6AE] text-[#F2ECE2] dark:text-[#1C1916] text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Four Bottom Navigation Destinations: Home, Notes, Journal, Search */}
      <nav
        aria-label="Bottom Navigation"
        className="fixed bottom-0 left-0 right-0 z-40 flex flex-col bg-[#F2ECE2] dark:bg-[#26221E]"
      >
        <div className="w-full h-[1px] bg-[#CFC3B2] dark:bg-[#3E3730]" />
        <div className="max-w-lg w-full mx-auto grid grid-cols-4 items-center h-16 px-2">
          <button
            type="button"
            onClick={() => handleNavigate('home')}
            className="min-h-[48px] min-w-[48px] flex flex-col items-center justify-center gap-1 cursor-pointer group focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
          >
            <span
              className={`px-4 py-1 rounded-full transition-colors ${
                activeTab === 'home'
                  ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#66503F] dark:text-[#D8C6AE]'
                  : 'text-[#665A4E] dark:text-[#B7AA9A] group-hover:text-[#29231E] dark:group-hover:text-[#F3EFE6]'
              }`}
            >
              <Home className="w-5 h-5" />
            </span>
            <span
              className={`text-xs font-medium tracking-tight whitespace-nowrap shrink-0 ${
                activeTab === 'home'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold'
                  : 'text-[#665A4E] dark:text-[#B7AA9A]'
              }`}
            >
              Home
            </span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('notes')}
            className="min-h-[48px] min-w-[48px] flex flex-col items-center justify-center gap-1 cursor-pointer group focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
          >
            <span
              className={`px-4 py-1 rounded-full transition-colors ${
                activeTab === 'notes'
                  ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#66503F] dark:text-[#D8C6AE]'
                  : 'text-[#665A4E] dark:text-[#B7AA9A] group-hover:text-[#29231E] dark:group-hover:text-[#F3EFE6]'
              }`}
            >
              <FileText className="w-5 h-5" />
            </span>
            <span
              className={`text-xs font-medium tracking-tight whitespace-nowrap shrink-0 ${
                activeTab === 'notes'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold'
                  : 'text-[#665A4E] dark:text-[#B7AA9A]'
              }`}
            >
              Notes
            </span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('journal')}
            className="min-h-[48px] min-w-[48px] flex flex-col items-center justify-center gap-1 cursor-pointer group focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
          >
            <span
              className={`px-4 py-1 rounded-full transition-colors ${
                activeTab === 'journal'
                  ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#66503F] dark:text-[#D8C6AE]'
                  : 'text-[#665A4E] dark:text-[#B7AA9A] group-hover:text-[#29231E] dark:group-hover:text-[#F3EFE6]'
              }`}
            >
              <BookOpen className="w-5 h-5" />
            </span>
            <span
              className={`text-xs font-medium tracking-tight whitespace-nowrap shrink-0 ${
                activeTab === 'journal'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold'
                  : 'text-[#665A4E] dark:text-[#B7AA9A]'
              }`}
            >
              Journal
            </span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('search')}
            className="min-h-[48px] min-w-[48px] flex flex-col items-center justify-center gap-1 cursor-pointer group focus-visible:outline-2 focus-visible:outline-[#66503F] dark:focus-visible:outline-[#D8C6AE]"
          >
            <span
              className={`px-4 py-1 rounded-full transition-colors ${
                activeTab === 'search'
                  ? 'bg-[#E3D9CA] dark:bg-[#352F29] text-[#66503F] dark:text-[#D8C6AE]'
                  : 'text-[#665A4E] dark:text-[#B7AA9A] group-hover:text-[#29231E] dark:group-hover:text-[#F3EFE6]'
              }`}
            >
              <Search className="w-5 h-5" />
            </span>
            <span
              className={`text-xs font-medium tracking-tight whitespace-nowrap shrink-0 ${
                activeTab === 'search'
                  ? 'text-[#29231E] dark:text-[#F3EFE6] font-semibold'
                  : 'text-[#665A4E] dark:text-[#B7AA9A]'
              }`}
            >
              Search
            </span>
          </button>
        </div>
      </nav>
    </div>
  );
}
