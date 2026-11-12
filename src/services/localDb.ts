export type ContentFormat = 'PLAIN_TEXT' | 'RICH_TEXT';
export type NoteSortOrder = 'LAST_MODIFIED' | 'CREATED_DATE' | 'TITLE_ASC';
export type SearchScope = 'ALL' | 'NOTES' | 'JOURNAL';

export interface NoteRecord {
  id: string;
  title: string;
  content: string;
  contentFormat: ContentFormat;
  createdAt: number;
  updatedAt: number;
  isPinned: boolean;
}

export interface JournalEntryRecord {
  id: string;
  title: string;
  content: string;
  createdAt: number;
  updatedAt: number;
  isPinned: boolean;
}

const DB_NAME = 'aurea_notes_journal_db';
const DB_VERSION = 1;
const NOTES_STORE = 'notes';
const JOURNAL_STORE = 'journal_entries';

const LS_NOTES_KEY = 'aurea_fallback_notes_v1';
const LS_JOURNAL_KEY = 'aurea_fallback_journal_v1';

function normalizeNoteRecord(raw: Partial<NoteRecord> & { id: string }): NoteRecord {
  return {
    id: raw.id,
    title: raw.title ?? '',
    content: raw.content ?? '',
    contentFormat: raw.contentFormat ?? 'PLAIN_TEXT',
    createdAt: typeof raw.createdAt === 'number' ? raw.createdAt : Date.now(),
    updatedAt: typeof raw.updatedAt === 'number' ? raw.updatedAt : Date.now(),
    isPinned: Boolean(raw.isPinned),
  };
}

function normalizeJournalRecord(
  raw: Partial<JournalEntryRecord> & { id: string }
): JournalEntryRecord {
  return {
    id: raw.id,
    title: raw.title ?? '',
    content: raw.content ?? '',
    createdAt: typeof raw.createdAt === 'number' ? raw.createdAt : Date.now(),
    updatedAt: typeof raw.updatedAt === 'number' ? raw.updatedAt : Date.now(),
    isPinned: Boolean(raw.isPinned),
  };
}

export function sortNotesList(
  notes: NoteRecord[],
  sortOrder: NoteSortOrder = 'LAST_MODIFIED'
): NoteRecord[] {
  return [...notes].sort((a, b) => {
    if (a.isPinned !== b.isPinned) {
      return a.isPinned ? -1 : 1;
    }
    if (sortOrder === 'CREATED_DATE') {
      return b.createdAt - a.createdAt;
    }
    if (sortOrder === 'TITLE_ASC') {
      const cmp = a.title.localeCompare(b.title, undefined, {
        sensitivity: 'base',
      });
      return cmp !== 0 ? cmp : b.updatedAt - a.updatedAt;
    }
    return b.updatedAt - a.updatedAt;
  });
}

export function sortJournalEntriesList(
  entries: JournalEntryRecord[]
): JournalEntryRecord[] {
  return [...entries].sort((a, b) => {
    if (a.isPinned !== b.isPinned) {
      return a.isPinned ? -1 : 1;
    }
    return b.createdAt - a.createdAt;
  });
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB is not available in this environment.'));
      return;
    }

    const request = window.indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(NOTES_STORE)) {
        const notesStore = db.createObjectStore(NOTES_STORE, { keyPath: 'id' });
        notesStore.createIndex('updatedAt', 'updatedAt', { unique: false });
      }
      if (!db.objectStoreNames.contains(JOURNAL_STORE)) {
        const journalStore = db.createObjectStore(JOURNAL_STORE, {
          keyPath: 'id',
        });
        journalStore.createIndex('createdAt', 'createdAt', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () =>
      reject(request.error ?? new Error('Failed to open IndexedDB.'));
  });
}

export async function getAllNotes(
  sortOrder: NoteSortOrder = 'LAST_MODIFIED'
): Promise<NoteRecord[]> {
  try {
    const db = await openDatabase();
    return await new Promise<NoteRecord[]>((resolve, reject) => {
      const tx = db.transaction(NOTES_STORE, 'readonly');
      const store = tx.objectStore(NOTES_STORE);
      const req = store.getAll();
      req.onsuccess = () => {
        const rawItems = (req.result as Array<Partial<NoteRecord> & { id: string }>) || [];
        const normalized = rawItems.map(normalizeNoteRecord);
        resolve(sortNotesList(normalized, sortOrder));
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    const raw = localStorage.getItem(LS_NOTES_KEY);
    const parsed: Array<Partial<NoteRecord> & { id: string }> = raw
      ? JSON.parse(raw)
      : [];
    return sortNotesList(parsed.map(normalizeNoteRecord), sortOrder);
  }
}

export async function saveNoteRecord(note: NoteRecord): Promise<void> {
  const normalized = normalizeNoteRecord(note);
  try {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(NOTES_STORE, 'readwrite');
      const store = tx.objectStore(NOTES_STORE);
      store.put(normalized);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    const existing = await getAllNotes();
    const updated = [
      normalized,
      ...existing.filter((n) => n.id !== normalized.id),
    ];
    localStorage.setItem(LS_NOTES_KEY, JSON.stringify(updated));
  }
}

export async function setNotePinnedRecord(
  id: string,
  isPinned: boolean
): Promise<void> {
  const existing = await getAllNotes();
  const target = existing.find((n) => n.id === id);
  if (!target) return;
  await saveNoteRecord({
    ...target,
    isPinned,
  });
}

export async function deleteNoteRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(NOTES_STORE, 'readwrite');
      const store = tx.objectStore(NOTES_STORE);
      store.delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    const existing = await getAllNotes();
    const updated = existing.filter((n) => n.id !== id);
    localStorage.setItem(LS_NOTES_KEY, JSON.stringify(updated));
  }
}

export async function getAllJournalEntries(): Promise<JournalEntryRecord[]> {
  try {
    const db = await openDatabase();
    return await new Promise<JournalEntryRecord[]>((resolve, reject) => {
      const tx = db.transaction(JOURNAL_STORE, 'readonly');
      const store = tx.objectStore(JOURNAL_STORE);
      const req = store.getAll();
      req.onsuccess = () => {
        const rawItems =
          (req.result as Array<Partial<JournalEntryRecord> & { id: string }>) ||
          [];
        const normalized = rawItems.map(normalizeJournalRecord);
        resolve(sortJournalEntriesList(normalized));
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    const raw = localStorage.getItem(LS_JOURNAL_KEY);
    const parsed: Array<Partial<JournalEntryRecord> & { id: string }> = raw
      ? JSON.parse(raw)
      : [];
    return sortJournalEntriesList(parsed.map(normalizeJournalRecord));
  }
}

export async function saveJournalEntryRecord(
  entry: JournalEntryRecord
): Promise<void> {
  const normalized = normalizeJournalRecord(entry);
  try {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(JOURNAL_STORE, 'readwrite');
      const store = tx.objectStore(JOURNAL_STORE);
      store.put(normalized);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    const existing = await getAllJournalEntries();
    const updated = [
      normalized,
      ...existing.filter((e) => e.id !== normalized.id),
    ];
    localStorage.setItem(LS_JOURNAL_KEY, JSON.stringify(updated));
  }
}

export async function setJournalEntryPinnedRecord(
  id: string,
  isPinned: boolean
): Promise<void> {
  const existing = await getAllJournalEntries();
  const target = existing.find((e) => e.id === id);
  if (!target) return;
  await saveJournalEntryRecord({
    ...target,
    isPinned,
  });
}

export async function deleteJournalEntryRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(JOURNAL_STORE, 'readwrite');
      const store = tx.objectStore(JOURNAL_STORE);
      store.delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    const existing = await getAllJournalEntries();
    const updated = existing.filter((e) => e.id !== id);
    localStorage.setItem(LS_JOURNAL_KEY, JSON.stringify(updated));
  }
}

export function formatEditorialTimestamp(epochMillis: number): string {
  if (!epochMillis || epochMillis <= 0) return '';
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(epochMillis));
}
