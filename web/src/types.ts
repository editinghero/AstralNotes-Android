export type SyncStatus = 'SYNCED' | 'SYNCING' | 'OFFLINE_PENDING' | 'ERROR';

export type DrawerDestination = 'NOTES' | 'PINNED' | 'VAULT' | 'ARCHIVE' | 'TRASH';

export interface Note {
  id: string;
  title: string;
  content: string;
  colorHex: string;
  isPinned: boolean;
  isArchived: boolean;
  isLocked: boolean;
  isTrash: boolean;
  tags: string[];
  imageUrls: string[];
  reminderTime?: number | null;
  createdAt: number;
  updatedAt: number;
  revision: number;
  deviceId: string;
  isDeleted: boolean;
  isSynced: boolean;
  isEncrypted?: boolean;
  encryptedData?: string;
  iv?: string;
}

export interface EncryptedNotePayload {
  id: string;
  revision: number;
  deviceId: string;
  colorHex: string;
  isPinned: boolean;
  isArchived: boolean;
  isLocked: boolean;
  isTrash: boolean;
  isDeleted: boolean;
  reminderTime: number | null;
  createdAt: number;
  updatedAt: number;
  isEncrypted: boolean;
  encryptedData?: string;
  iv?: string;
  title?: string;
  content?: string;
  tags?: string[];
  imageUrls?: string[];
}

export interface ShareRecord {
  shareId: string;
  ownerUid: string;
  noteId: string;
  title: string;
  salt: string;
  iv: string;
  encryptedPayload: string;
  createdAt: number;
  expiresAt: number | null;
}
