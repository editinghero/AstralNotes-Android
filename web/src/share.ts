import { doc, getDoc, setDoc, deleteDoc, collection, query, where, getDocs } from 'firebase/firestore';
import { db, auth } from './firebase';
import type { Note, ShareRecord } from './types';
import { deriveKey, encryptNotePayload, decryptNotePayload, generateSalt, bufferToBase64, base64ToBuffer } from './crypto';
import { renderMarkdown } from './markdown';

const PUBLIC_SALT_STR = 'cHVibGljX2FzdHJhbF9zYWx0X3Yx';

export async function createShare(
  note: Note,
  password?: string,
  expiresAtTimestamp: number | null = null
): Promise<{ shareId: string; shareUrl: string }> {
  const user = auth.currentUser;
  if (!user) {
    throw new Error('User must be signed in to generate a share link.');
  }

  const isPasswordProtected = Boolean(password && password.trim().length > 0);
  let shareKey: CryptoKey;
  let salt: Uint8Array;

  if (isPasswordProtected) {
    salt = generateSalt(16);
    shareKey = await deriveKey(password!.trim(), salt);
  } else {
    salt = base64ToBuffer(PUBLIC_SALT_STR);
    shareKey = await deriveKey('astral_public_share_token_open', salt);
  }

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

  const record: ShareRecord = {
    shareId,
    ownerUid: user.uid,
    noteId: note.id,
    title: note.isLocked ? 'Private Note' : (note.title || 'Untitled Note'),
    salt: bufferToBase64(salt),
    iv: encrypted.iv,
    encryptedPayload: encrypted.encryptedData,
    createdAt,
    expiresAt: expiresAtTimestamp,
    isPasswordProtected
  };

  const shareDocRef = doc(db, 'shares', shareId);
  await setDoc(shareDocRef, record);

  const base = `${window.location.origin}${window.location.pathname}`;
  const shareUrl = `${base}#/share/${shareId}`;

  return { shareId, shareUrl };
}

export async function getSharedNoteMeta(shareId: string): Promise<{
  shareId: string;
  title: string;
  isPasswordProtected: boolean;
  expiresAt: number | null;
  isExpired: boolean;
  isDeleted: boolean;
}> {
  const shareDocRef = doc(db, 'shares', shareId);
  const snapshot = await getDoc(shareDocRef);

  if (!snapshot.exists()) {
    throw new Error('Share link not found or has been revoked.');
  }

  const data = snapshot.data() as ShareRecord;
  const isExpired = Boolean(data.expiresAt && Date.now() > data.expiresAt);

  let isDeleted = false;
  try {
    const origNoteRef = doc(db, 'user', data.ownerUid, 'notes', data.noteId);
    const origSnap = await getDoc(origNoteRef);
    if (!origSnap.exists() || Boolean(origSnap.data()?.isDeleted)) {
      isDeleted = true;
    }
  } catch {}

  return {
    shareId: data.shareId,
    title: data.title,
    isPasswordProtected: Boolean(data.isPasswordProtected),
    expiresAt: data.expiresAt ?? null,
    isExpired,
    isDeleted
  };
}

export async function unlockSharedNote(
  shareId: string,
  password?: string
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

  try {
    const origNoteRef = doc(db, 'user', data.ownerUid, 'notes', data.noteId);
    const origSnap = await getDoc(origNoteRef);
    if (!origSnap.exists() || Boolean(origSnap.data()?.isDeleted)) {
      throw new Error('This shared note has been deleted by its author.');
    }
  } catch (e) {
    if ((e as Error).message.includes('deleted by its author')) {
      throw e;
    }
  }

  let shareKey: CryptoKey;
  if (data.isPasswordProtected) {
    if (!password || !password.trim()) {
      throw new Error('Please enter the share password to view this note.');
    }
    const salt = base64ToBuffer(data.salt);
    shareKey = await deriveKey(password.trim(), salt);
  } else {
    const salt = base64ToBuffer(data.salt);
    shareKey = await deriveKey('astral_public_share_token_open', salt);
  }

  try {
    const decrypted = await decryptNotePayload(data.encryptedPayload, data.iv, shareKey);
    const isPrivate = data.title === 'Private Note';
    return {
      title: isPrivate ? 'Private Note' : decrypted.title,
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

export async function deleteSharesForNote(noteId: string): Promise<void> {
  const user = auth.currentUser;
  if (!user) return;
  try {
    const q = query(
      collection(db, 'shares'),
      where('noteId', '==', noteId),
      where('ownerUid', '==', user.uid)
    );
    const snapshot = await getDocs(q);
    for (const d of snapshot.docs) {
      await deleteDoc(d.ref);
    }
  } catch (e) {
    console.warn('Could not cleanup shares for note:', e);
  }
}

export async function listUserShares(): Promise<ShareRecord[]> {
  const user = auth.currentUser;
  if (!user) return [];
  try {
    const q = query(
      collection(db, 'shares'),
      where('ownerUid', '==', user.uid)
    );
    const snapshot = await getDocs(q);
    return snapshot.docs.map(d => d.data() as ShareRecord).sort((a, b) => b.createdAt - a.createdAt);
  } catch (e) {
    console.error('Failed to list user shares:', e);
    return [];
  }
}

export async function listActiveSharesForNote(noteId: string): Promise<ShareRecord[]> {
  const shares = await listUserShares();
  const now = Date.now();
  return shares.filter(s => s.noteId === noteId && (!s.expiresAt || s.expiresAt > now));
}

export function exportAsMarkdown(note: Note): void {
  const title = note.title.trim() || 'Untitled Note';
  const blob = new Blob([note.content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${title.replace(/[/\\?%*:|"<>]/g, '_')}.md`;
  a.click();
  URL.revokeObjectURL(url);
}

export function exportAsHtml(note: Note): void {
  const title = note.title.trim() || 'Untitled Note';
  const rendered = renderMarkdown(note.content);
  const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>${title} - AstralNotes</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background: #181112;
      color: #fff3e0;
      line-height: 1.8;
      max-width: 800px;
      margin: 40px auto;
      padding: 0 20px;
    }
    h1, h2, h3 { color: #f0788a; font-family: 'Outfit', sans-serif; }
    pre { background: #22191a; padding: 16px; border-radius: 8px; overflow-x: auto; border: 1px solid rgba(240, 120, 138, 0.2); }
    code { font-family: 'JetBrains Mono', monospace; font-size: 0.9em; }
    blockquote { border-left: 4px solid #f0788a; margin: 0; padding-left: 16px; color: #d0c0b8; }
    table { width: 100%; border-collapse: collapse; margin: 20px 0; }
    th, td { border: 1px solid rgba(255, 255, 255, 0.1); padding: 10px 14px; text-align: left; }
    th { background: #22191a; color: #f0788a; }
    img { max-width: 100%; border-radius: 8px; }
  </style>
</head>
<body>
  <h1>${title}</h1>
  <hr style="border: none; border-top: 1px solid rgba(240,120,138,0.2); margin: 20px 0;" />
  <div class="content">${rendered}</div>
</body>
</html>`;

  const blob = new Blob([htmlContent], { type: 'text/html;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${title.replace(/[/\\?%*:|"<>]/g, '_')}.html`;
  a.click();
  URL.revokeObjectURL(url);
}

export function exportAsPdf(note: Note): void {
  const title = note.title.trim() || 'Untitled Note';
  const rendered = renderMarkdown(note.content);
  const printWindow = window.open('', '_blank');
  if (!printWindow) {
    alert('Please allow popups to export as PDF.');
    return;
  }

  printWindow.document.write(`<!DOCTYPE html>
<html>
<head>
  <title>${title}</title>
  <style>
    @media print {
      body { margin: 20mm; font-size: 12pt; color: #000; background: #fff; line-height: 1.6; }
      h1, h2, h3 { color: #000; page-break-after: avoid; }
      pre, blockquote { page-break-inside: avoid; }
    }
    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 40px; color: #222; }
    pre { background: #f5f5f5; padding: 12px; border-radius: 6px; border: 1px solid #ddd; }
    code { font-family: monospace; }
    table { width: 100%; border-collapse: collapse; margin: 16px 0; }
    th, td { border: 1px solid #ccc; padding: 8px 12px; }
    th { background: #eee; }
  </style>
</head>
<body>
  <h1>${title}</h1>
  <hr />
  <div>${rendered}</div>
  <script>
    window.onload = function() {
      window.print();
      setTimeout(function() { window.close(); }, 500);
    };
  </script>
</body>
</html>`);
  printWindow.document.close();
}
