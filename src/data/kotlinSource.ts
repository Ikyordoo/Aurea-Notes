import mainActivityRaw from '../../app/src/main/java/com/aurea/notes/MainActivity.kt?raw';
import aureaApplicationRaw from '../../app/src/main/java/com/aurea/notes/AureaApplication.kt?raw';
import aureaDatabaseRaw from '../../app/src/main/java/com/aurea/notes/data/local/AureaDatabase.kt?raw';
import noteEntityRaw from '../../app/src/main/java/com/aurea/notes/data/local/NoteEntity.kt?raw';
import journalEntryEntityRaw from '../../app/src/main/java/com/aurea/notes/data/local/JournalEntryEntity.kt?raw';
import noteDaoRaw from '../../app/src/main/java/com/aurea/notes/data/local/NoteDao.kt?raw';
import journalEntryDaoRaw from '../../app/src/main/java/com/aurea/notes/data/local/JournalEntryDao.kt?raw';
import notesRepositoryRaw from '../../app/src/main/java/com/aurea/notes/data/repository/NotesRepository.kt?raw';
import journalRepositoryRaw from '../../app/src/main/java/com/aurea/notes/data/repository/JournalRepository.kt?raw';
import notesViewModelRaw from '../../app/src/main/java/com/aurea/notes/ui/viewmodels/NotesViewModel.kt?raw';
import journalViewModelRaw from '../../app/src/main/java/com/aurea/notes/ui/viewmodels/JournalViewModel.kt?raw';
import searchViewModelRaw from '../../app/src/main/java/com/aurea/notes/ui/viewmodels/SearchViewModel.kt?raw';
import aureaAppRaw from '../../app/src/main/java/com/aurea/notes/ui/AureaApp.kt?raw';
import homeScreenRaw from '../../app/src/main/java/com/aurea/notes/ui/screens/HomeScreen.kt?raw';
import notesScreenRaw from '../../app/src/main/java/com/aurea/notes/ui/screens/NotesScreen.kt?raw';
import journalScreenRaw from '../../app/src/main/java/com/aurea/notes/ui/screens/JournalScreen.kt?raw';
import searchScreenRaw from '../../app/src/main/java/com/aurea/notes/ui/screens/SearchScreen.kt?raw';

export interface KotlinSourceFile {
  path: string;
  title: string;
  description: string;
  code: string;
}

export const KOTLIN_SOURCE_FILES: KotlinSourceFile[] = [
  {
    path: 'app/src/main/java/com/aurea/notes/data/local/AureaDatabase.kt',
    title: 'AureaDatabase.kt',
    description: 'Room v3 database with explicit non-destructive MIGRATION_1_2 and MIGRATION_2_3.',
    code: aureaDatabaseRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/local/NoteEntity.kt',
    title: 'NoteEntity.kt',
    description: 'Room entity for Notes with category, isPinned, and rich-text format readiness.',
    code: noteEntityRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/local/JournalEntryEntity.kt',
    title: 'JournalEntryEntity.kt',
    description: 'Room entity for Journal entries with optional title, mood, timestamps, and isPinned.',
    code: journalEntryEntityRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/local/NoteDao.kt',
    title: 'NoteDao.kt',
    description: 'Room DAO for Notes with pinned-first ordering, sort queries, uncategorized queries, and category rename/clear operations.',
    code: noteDaoRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/local/JournalEntryDao.kt',
    title: 'JournalEntryDao.kt',
    description: 'Room DAO for chronological pinned-first Journal entries and mood search.',
    code: journalEntryDaoRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/repository/NotesRepository.kt',
    title: 'NotesRepository.kt',
    description: 'Offline-first Notes repository with NoteSortOrder, NoteCategoryFilter, persistent category management, and pinning.',
    code: notesRepositoryRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/data/repository/JournalRepository.kt',
    title: 'JournalRepository.kt',
    description: 'Offline-first Journal repository with pinning and mood support.',
    code: journalRepositoryRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/viewmodels/NotesViewModel.kt',
    title: 'NotesViewModel.kt',
    description: 'MVVM state management for Notes list, category filtering, category CRUD, sorting, pinning, and editor.',
    code: notesViewModelRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/viewmodels/JournalViewModel.kt',
    title: 'JournalViewModel.kt',
    description: 'MVVM state management for Journal entries, pinning, and editor.',
    code: journalViewModelRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/viewmodels/SearchViewModel.kt',
    title: 'SearchViewModel.kt',
    description: 'Repository-backed debounced search with ALL/NOTES/JOURNAL scopes and NoteCategoryFilter.',
    code: searchViewModelRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/screens/NotesScreen.kt',
    title: 'NotesScreen.kt',
    description: 'Notes list, category filter bar, category management dialog, sort selector, pin controls, and Note editor.',
    code: notesScreenRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/screens/JournalScreen.kt',
    title: 'JournalScreen.kt',
    description: 'Chronological Journal list, pin controls, and Journal editor.',
    code: journalScreenRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/screens/SearchScreen.kt',
    title: 'SearchScreen.kt',
    description: 'Repository-backed search with scope filtering, category filtering, and clear action.',
    code: searchScreenRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/screens/HomeScreen.kt',
    title: 'HomeScreen.kt',
    description: 'Home screen with live counts and Pinned & Recent highlights.',
    code: homeScreenRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/ui/AureaApp.kt',
    title: 'AureaApp.kt',
    description: 'Root Compose Scaffold with centralized unsaved-changes navigation guard.',
    code: aureaAppRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/AureaApplication.kt',
    title: 'AureaApplication.kt',
    description: 'Application class initializing Room database and repositories.',
    code: aureaApplicationRaw,
  },
  {
    path: 'app/src/main/java/com/aurea/notes/MainActivity.kt',
    title: 'MainActivity.kt',
    description: 'Main Android Activity entry point.',
    code: mainActivityRaw,
  },
];
