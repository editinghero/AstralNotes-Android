import { doc, getDoc, setDoc, deleteDoc } from 'firebase/firestore';
import { db, auth } from './firebase';
import type { Note, ShareRecord } from './types';
import { deriveKey, encryptNotePayload, decryptNotePayload, generateSalt, bufferToBase64, base64ToBuffer } from './crypto';

export async function createPasswordProtectedShare(
  note: Note,
  password: string,
  expiryHours: number | null = null
): Promise<{ shareId: string; shareUrl: string }> {
  const user = auth.currentUser;
  if (!user) {
    throw new Error('User must be signed in to generate a share link.');
  }

  const salt = generateSalt(16);
  const shareKey = await deriveKey(password, salt);

  const encrypted = await encryptNotePayload(
    {
      title: note.title,
      content: note.content,
      tags: note.tags,
      imageUrls: note.imageUrls
    },
    shareKey
  );

  const shareId = crypto.randomUUID();
  const createdAt = Date.now();
  const expiresAt = expiryHours ? createdAt + expiryHours * 3600 * 1000 : null;

  const record: ShareRecord = {
    shareId,
    ownerUid: user.uid,
    noteId: note.id,
    title: note.title || 'Protected Note',
    salt: bufferToBase64(salt),
    iv: encrypted.iv,
    encryptedPayload: encrypted.encryptedData,
    createdAt,
    expiresAt
  };

  const shareDocRef = doc(db, 'shares', shareId);
  await setDoc(shareDocRef, record);

  const base = `${window.location.origin}${window.location.pathname}`;
  const shareUrl = `${base}#/share/${shareId}`;

  return { shareId, shareUrl };
}

export async function unlockSharedNote(
  shareId: string,
  password: string
): Promise<{ title: string; content: string; tags: string[]; imageUrls: string[]; createdAt: number }> {
  const shareDocRef = doc(db, 'shares', shareId);
  const snapshot = await getDoc(shareDocRef);

  if (!snapshot.exists()) {
    throw new Error('Share link not found or has been revoked.');
  }

  const data = snapshot.data() as ShareRecord;

  if (data.expiresAt && Date.now() > data.expiresAt) {
    throw new Error('This share link has expired.');
  }

  const salt = base64ToBuffer(data.salt);
  const shareKey = await deriveKey(password, salt);

  try {
    const decrypted = await decryptNotePayload(data.encryptedPayload, data.iv, shareKey);
    return {
      title: decrypted.title,
      content: decrypted.content,
      tags: decrypted.tags,
      imageUrls: decrypted.imageUrls,
      createdAt: data.createdAt
    };
  } catch {
    throw new Error('Incorrect password. Please try again.');
  }
}

export async function revokeShare(shareId: string): Promise<void> {
  const shareDocRef = doc(db, 'shares', shareId);
  await deleteDoc(shareDocRef);
}
