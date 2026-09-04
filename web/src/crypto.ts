export interface EncryptedContainer {
  encryptedData: string;
  iv: string;
}

export interface DecryptedNotePayload {
  title: string;
  content: string;
  tags: string[];
  imageUrls: string[];
}

export function bufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

export function base64ToBuffer(base64: string): Uint8Array {
  const sanitized = base64
    .replace(/\s+/g, '')
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  const padded = sanitized.padEnd(sanitized.length + ((4 - (sanitized.length % 4)) % 4), '=');
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function generateSalt(length = 16): Uint8Array {
  const salt = new Uint8Array(length);
  crypto.getRandomValues(salt);
  return salt;
}

export async function deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(passphrase),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as BufferSource,
      iterations: 100000,
      hash: 'SHA-256'
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function encryptNotePayload(
  payload: DecryptedNotePayload,
  key: CryptoKey
): Promise<EncryptedContainer> {
  const enc = new TextEncoder();
  const jsonStr = JSON.stringify({
    title: payload.title,
    content: payload.content,
    tags: payload.tags,
    imageUrls: payload.imageUrls
  });
  const dataBytes = enc.encode(jsonStr);

  const iv = new Uint8Array(12);
  crypto.getRandomValues(iv);

  const ciphertextBuffer = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: iv,
      tagLength: 128
    },
    key,
    dataBytes
  );

  return {
    encryptedData: bufferToBase64(ciphertextBuffer),
    iv: bufferToBase64(iv)
  };
}

export async function decryptNotePayload(
  encryptedDataB64: string,
  ivB64: string,
  key: CryptoKey
): Promise<DecryptedNotePayload> {
  const ciphertextBytes = base64ToBuffer(encryptedDataB64);
  const ivBytes = base64ToBuffer(ivB64);

  const decryptedBuffer = await crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: ivBytes as BufferSource,
      tagLength: 128
    },
    key,
    ciphertextBytes as BufferSource
  );

  const dec = new TextDecoder();
  const jsonStr = dec.decode(decryptedBuffer);
  const parsed = JSON.parse(jsonStr) as Partial<DecryptedNotePayload>;

  return {
    title: typeof parsed.title === 'string' ? parsed.title : '',
    content: typeof parsed.content === 'string' ? parsed.content : '',
    tags: Array.isArray(parsed.tags) ? parsed.tags.filter((t): t is string => typeof t === 'string') : [],
    imageUrls: Array.isArray(parsed.imageUrls) ? parsed.imageUrls.filter((u): u is string => typeof u === 'string') : []
  };
}
