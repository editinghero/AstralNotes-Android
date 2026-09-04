import type { Note } from './types';
import {
  deriveKey,
  generateSalt,
  bufferToBase64,
  base64ToBuffer,
  encryptNotePayload,
  decryptNotePayload
} from './crypto';
import { vaultManager } from './vault';
import { getLocalNotes } from './db';
import { syncEngine } from './sync';

export interface BackupData {
  version: number;
  app: string;
  exportedAt: number;
  notes: any[];
  vaultNotes?: any[];
}

export interface BackupInspection {
  regularCount: number;
  vaultCount: number;
}

export interface ImportResult {
  regularImported: number;
  vaultImported: number;
}

export function inspectBackup(jsonString: string): BackupInspection {
  const data = JSON.parse(jsonString) as BackupData;
  const regularCount = Array.isArray(data.notes) ? data.notes.length : 0;
  const vaultCount = Array.isArray(data.vaultNotes) ? data.vaultNotes.length : 0;
  return { regularCount, vaultCount };
}

export async function exportLibrary(exportVaultPassword?: string): Promise<number> {
  const allNotes: Note[] = await getLocalNotes();
  const regularNotes = allNotes.filter((n: Note) => !n.isLocked && !n.isDeleted);
  const vaultNotes = allNotes.filter((n: Note) => n.isLocked && !n.isDeleted);

  const exportedVaultNotes: any[] = [];

  if (vaultNotes.length > 0) {
    if (!exportVaultPassword || !exportVaultPassword.trim()) {
      throw new Error('Vault password is required to export locked notes.');
    }

    const currentKey = vaultManager.getVaultKey();
    if (!currentKey) {
      throw new Error('Please unlock your vault first before exporting.');
    }

    const salt = generateSalt(16);
    const exportKey = await deriveKey(exportVaultPassword.trim(), salt);
    const saltB64 = bufferToBase64(salt);

    for (const vNote of vaultNotes) {
      let title = vNote.title;
      let content = vNote.content;
      let tags = vNote.tags;
      let imageUrls = vNote.imageUrls;

      if (vNote.encryptedData && vNote.iv) {
        try {
          const dec = await decryptNotePayload(vNote.encryptedData, vNote.iv, currentKey);
          title = dec.title;
          content = dec.content;
          tags = dec.tags;
          imageUrls = dec.imageUrls;
        } catch {
          if (vNote.title && !vNote.title.includes('[Locked Note]')) {
            title = vNote.title;
            content = vNote.content;
            tags = vNote.tags;
            imageUrls = vNote.imageUrls;
          } else {
            throw new Error(`Failed to decrypt vault note '${vNote.title}'.`);
          }
        }
      }

      const reEnc = await encryptNotePayload(
        { title, content, tags, imageUrls },
        exportKey
      );

      exportedVaultNotes.push({
        id: vNote.id,
        colorHex: vNote.colorHex,
        isPinned: vNote.isPinned,
        isArchived: vNote.isArchived,
        isLocked: true,
        isTrash: vNote.isTrash,
        createdAt: vNote.createdAt,
        updatedAt: vNote.updatedAt,
        encryptedData: reEnc.encryptedData,
        iv: reEnc.iv,
        salt: saltB64
      });
    }
  }

  const backupData: BackupData = {
    version: 1,
    app: 'AstralNotes',
    exportedAt: Date.now(),
    notes: regularNotes.map((n: Note) => ({
      id: n.id,
      title: n.title,
      content: n.content,
      colorHex: n.colorHex,
      isPinned: n.isPinned,
      isArchived: n.isArchived,
      isLocked: false,
      isTrash: n.isTrash,
      tags: n.tags,
      imageUrls: n.imageUrls,
      reminderTime: n.reminderTime,
      createdAt: n.createdAt,
      updatedAt: n.updatedAt
    })),
    vaultNotes: exportedVaultNotes
  };

  const jsonBlob = new Blob([JSON.stringify(backupData, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(jsonBlob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `astral_backup_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '_')}.json`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);

  return regularNotes.length + exportedVaultNotes.length;
}

export async function importLibrary(
  jsonString: string,
  importedVaultPassword?: string
): Promise<ImportResult> {
  const data = JSON.parse(jsonString) as BackupData;
  const regularArray = Array.isArray(data.notes) ? data.notes : [];
  const vaultArray = Array.isArray(data.vaultNotes) ? data.vaultNotes : [];

  let regularImported = 0;
  let vaultImported = 0;

  for (const obj of regularArray) {
    const note: Note = {
      id: obj.id || crypto.randomUUID(),
      title: obj.title || '',
      content: obj.content || '',
      colorHex: obj.colorHex || '#DEFAULT',
      isPinned: Boolean(obj.isPinned),
      isArchived: Boolean(obj.isArchived),
      isLocked: false,
      isTrash: Boolean(obj.isTrash),
      tags: Array.isArray(obj.tags) ? obj.tags : [],
      imageUrls: Array.isArray(obj.imageUrls) ? obj.imageUrls : [],
      reminderTime: obj.reminderTime || null,
      createdAt: obj.createdAt || Date.now(),
      updatedAt: Date.now(),
      revision: 1,
      deviceId: 'web-backup',
      isDeleted: false,
      isSynced: false
    };

    await syncEngine.uploadNote(note);
    regularImported++;
  }

  if (vaultArray.length > 0) {
    if (!importedVaultPassword || !importedVaultPassword.trim()) {
      throw new Error('Please enter the vault password of the imported file to unlock its notes.');
    }

    if (!vaultManager.isUnlocked()) {
      throw new Error('Please unlock your private vault before importing locked vault notes.');
    }

    const currentVaultKey = vaultManager.getVaultKey();

    for (const vObj of vaultArray) {
      if (!vObj.encryptedData || !vObj.iv || !vObj.salt) {
        continue;
      }

      const saltBytes = base64ToBuffer(vObj.salt);
      const importedKey = await deriveKey(importedVaultPassword.trim(), saltBytes);

      let decrypted: { title: string; content: string; tags: string[]; imageUrls: string[] };
      try {
        decrypted = await decryptNotePayload(vObj.encryptedData, vObj.iv, importedKey);
      } catch {
        throw new Error('Incorrect vault password for the imported file.');
      }

      let finalEncData = vObj.encryptedData;
      let finalIv = vObj.iv;

      if (currentVaultKey) {
        const reEnc = await encryptNotePayload(decrypted, currentVaultKey);
        finalEncData = reEnc.encryptedData;
        finalIv = reEnc.iv;
      }

      const note: Note = {
        id: vObj.id || crypto.randomUUID(),
        title: decrypted.title,
        content: decrypted.content,
        colorHex: vObj.colorHex || '#DEFAULT',
        isPinned: Boolean(vObj.isPinned),
        isArchived: Boolean(vObj.isArchived),
        isLocked: true,
        isTrash: Boolean(vObj.isTrash),
        tags: decrypted.tags,
        imageUrls: decrypted.imageUrls,
        createdAt: vObj.createdAt || Date.now(),
        updatedAt: Date.now(),
        revision: 1,
        deviceId: 'web-backup',
        isDeleted: false,
        encryptedData: finalEncData,
        iv: finalIv,
        isSynced: false
      };

      await syncEngine.uploadNote(note);
      vaultImported++;
    }
  }

  return { regularImported, vaultImported };
}
