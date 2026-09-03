import {
  collection,
  doc,
  setDoc,
  deleteDoc,
  onSnapshot,
  type Unsubscribe,
  type DocumentData
} from 'firebase/firestore';
import { db, auth } from './firebase';
import type { Note, SyncStatus, EncryptedNotePayload } from './types';
import { deriveKey, encryptNotePayload, decryptNotePayload, base64ToBuffer, bufferToBase64, generateSalt } from './crypto';
import { saveLocalNote, saveLocalNotesBatch, deleteLocalNote, getMetaValue, setMetaValue } from './db';

type StatusListener = (status: SyncStatus) => void;
type NotesListener = (notes: Note[]) => void;

class SyncEngine {
  private syncStatus: SyncStatus = 'SYNCED';
  private statusListeners: StatusListener[] = [];
  private notesListeners: NotesListener[] = [];
  private unsubscribeFirestore: Unsubscribe | null = null;
  private currentKey: CryptoKey | null = null;

  public setStatus(status: SyncStatus): void {
    this.syncStatus = status;
    this.statusListeners.forEach(listener => listener(status));
  }

  public getStatus(): SyncStatus {
    return this.syncStatus;
  }

  public onStatusChange(listener: StatusListener): () => void {
    this.statusListeners.push(listener);
    listener(this.syncStatus);
    return () => {
      this.statusListeners = this.statusListeners.filter(l => l !== listener);
    };
  }

  public onNotesUpdate(listener: NotesListener): () => void {
    this.notesListeners.push(listener);
    return () => {
      this.notesListeners = this.notesListeners.filter(l => l !== listener);
    };
  }

  public async getOrCreateUserKey(passphrase?: string): Promise<CryptoKey> {
    if (this.currentKey && !passphrase) {
      return this.currentKey;
    }

    const user = auth.currentUser;
    const uid = user ? user.uid : 'offline_user';

    let saltStr = await getMetaValue(`salt_${uid}`);
    let salt: Uint8Array;

    if (saltStr) {
      salt = base64ToBuffer(saltStr);
    } else {
      salt = generateSalt(16);
      await setMetaValue(`salt_${uid}`, bufferToBase64(salt));
    }

    const effectivePass = passphrase || (await getMetaValue(`pass_${uid}`)) || uid;
    if (passphrase) {
      await setMetaValue(`pass_${uid}`, passphrase);
    }

    this.currentKey = await deriveKey(effectivePass, salt);
    return this.currentKey;
  }

  public async startRealtimeSync(onNotesReceived?: (notes: Note[]) => void): Promise<void> {
    this.stopRealtimeSync();

    const user = auth.currentUser;
    if (!user) {
      this.setStatus('OFFLINE_PENDING');
      return;
    }

    this.setStatus('SYNCING');
    const key = await this.getOrCreateUserKey();
    const notesCol = collection(db, 'user', user.uid, 'notes');

    this.unsubscribeFirestore = onSnapshot(
      notesCol,
      async (snapshot) => {
        const cloudNotes: Note[] = [];

        for (const docSnap of snapshot.docs) {
          const data = docSnap.data() as DocumentData;
          let title = typeof data.title === 'string' ? data.title : '';
          let content = typeof data.content === 'string' ? data.content : '';
          let tags = Array.isArray(data.tags) ? data.tags : [];
          let imageUrls = Array.isArray(data.imageUrls) ? data.imageUrls : [];

          if (data.isEncrypted && data.encryptedData && data.iv) {
            try {
              const decrypted = await decryptNotePayload(data.encryptedData, data.iv, key);
              title = decrypted.title;
              content = decrypted.content;
              tags = decrypted.tags;
              imageUrls = decrypted.imageUrls;
            } catch {
              title = '[Encrypted Note]';
              content = 'Encrypted with a different vault key.';
            }
          }

          cloudNotes.push({
            id: docSnap.id,
            title,
            content,
            colorHex: data.colorHex || '#DEFAULT',
            isPinned: Boolean(data.isPinned),
            isArchived: Boolean(data.isArchived),
            isLocked: Boolean(data.isLocked),
            isTrash: Boolean(data.isTrash),
            tags,
            imageUrls,
            reminderTime: typeof data.reminderTime === 'number' ? data.reminderTime : null,
            createdAt: typeof data.createdAt === 'number' ? data.createdAt : Date.now(),
            updatedAt: typeof data.updatedAt === 'number' ? data.updatedAt : Date.now(),
            revision: typeof data.revision === 'number' ? data.revision : 1,
            deviceId: typeof data.deviceId === 'string' ? data.deviceId : 'web',
            isDeleted: Boolean(data.isDeleted),
            isSynced: true
          });
        }

        const activeCloudNotes = cloudNotes.filter(n => !n.isDeleted);
        await saveLocalNotesBatch(activeCloudNotes);
        this.setStatus('SYNCED');

        if (onNotesReceived) {
          onNotesReceived(activeCloudNotes);
        }
        this.notesListeners.forEach(l => l(activeCloudNotes));
      },
      (error) => {
        console.error('Firestore V2 sync error:', error);
        this.setStatus('ERROR');
      }
    );
  }

  public stopRealtimeSync(): void {
    if (this.unsubscribeFirestore) {
      this.unsubscribeFirestore();
      this.unsubscribeFirestore = null;
    }
  }

  public async uploadNote(note: Note): Promise<void> {
    await saveLocalNote(note);

    const user = auth.currentUser;
    if (!user) {
      this.setStatus('OFFLINE_PENDING');
      return;
    }

    try {
      this.setStatus('SYNCING');
      let payload: EncryptedNotePayload;

      if (note.isLocked) {
        const key = await this.getOrCreateUserKey();
        const encrypted = await encryptNotePayload(
          {
            title: note.title,
            content: note.content,
            tags: note.tags,
            imageUrls: note.imageUrls
          },
          key
        );

        payload = {
          id: note.id,
          revision: note.revision + 1,
          deviceId: 'web',
          colorHex: note.colorHex,
          isPinned: note.isPinned,
          isArchived: note.isArchived,
          isLocked: true,
          isTrash: note.isTrash,
          isDeleted: note.isDeleted,
          reminderTime: note.reminderTime ?? null,
          createdAt: note.createdAt,
          updatedAt: Date.now(),
          isEncrypted: true,
          encryptedData: encrypted.encryptedData,
          iv: encrypted.iv
        };
      } else {
        payload = {
          id: note.id,
          revision: note.revision + 1,
          deviceId: 'web',
          colorHex: note.colorHex,
          isPinned: note.isPinned,
          isArchived: note.isArchived,
          isLocked: false,
          isTrash: note.isTrash,
          isDeleted: note.isDeleted,
          reminderTime: note.reminderTime ?? null,
          createdAt: note.createdAt,
          updatedAt: Date.now(),
          isEncrypted: false,
          title: note.title,
          content: note.content,
          tags: note.tags,
          imageUrls: note.imageUrls
        };
      }

      const docRef = doc(db, 'user', user.uid, 'notes', note.id);
      await setDoc(docRef, payload, { merge: true });
      this.setStatus('SYNCED');
    } catch (e) {
      console.error('Failed to upload note to Firestore V2:', e);
      this.setStatus('ERROR');
    }
  }

  public async deleteNote(noteId: string): Promise<void> {
    await deleteLocalNote(noteId);

    const user = auth.currentUser;
    if (!user) {
      return;
    }

    try {
      this.setStatus('SYNCING');
      const docRef = doc(db, 'user', user.uid, 'notes', noteId);
      await deleteDoc(docRef);
      this.setStatus('SYNCED');
    } catch (e) {
      console.error('Failed to delete note in Firestore V2:', e);
      this.setStatus('ERROR');
    }
  }
}

export const syncEngine = new SyncEngine();
