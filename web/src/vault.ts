import { doc, getDoc, setDoc } from 'firebase/firestore';
import { db, auth } from './firebase';
import {
  deriveKey,
  generateSalt,
  bufferToBase64,
  base64ToBuffer
} from './crypto';
import { getMetaValue, setMetaValue } from './db';

const VERIFY_TOKEN = 'VAULT_VERIFY_V1';

export interface VaultMetaConfig {
  version: number;
  kdfSalt: string;
  kdfIterations: number;
  wrappedVmk: string;
  wrappedVmkIv: string;
  verifier: string;
  verifierIv: string;
  updatedAt: number;
}

type VaultListener = (isUnlocked: boolean) => void;

class VaultManager {
  private inMemoryVaultKey: CryptoKey | null = null;
  private isVaultUnlocked = false;
  private listeners: VaultListener[] = [];

  public isUnlocked(): boolean {
    return this.isVaultUnlocked;
  }

  public getVaultKey(): CryptoKey | null {
    return this.inMemoryVaultKey;
  }

  public onVaultStateChange(listener: VaultListener): () => void {
    this.listeners.push(listener);
    listener(this.isVaultUnlocked);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  private notifyListeners(): void {
    this.listeners.forEach(l => l(this.isVaultUnlocked));
  }

  public async checkVaultExists(): Promise<boolean> {
    const user = auth.currentUser;
    if (user) {
      try {
        const docRef = doc(db, 'user', user.uid, 'vault_meta', 'config');
        const snapshot = await getDoc(docRef);
        if (snapshot.exists()) {
          const data = snapshot.data() as Partial<VaultMetaConfig>;
          if (data.wrappedVmk && data.kdfSalt) {
            await setMetaValue(`has_vault_${user.uid}`, 'true');
            return true;
          }
        }
      } catch (err) {
        console.warn('Could not check remote vault config:', err);
      }
      const localFlag = await getMetaValue(`has_vault_${user.uid}`);
      return localFlag === 'true';
    }
    const offlineFlag = await getMetaValue('has_vault_offline');
    return offlineFlag === 'true';
  }

  public async setupNewVault(password: string): Promise<void> {
    const salt = generateSalt(16);
    const kek = await deriveKey(password, salt);

    const vmkRaw = new Uint8Array(32);
    crypto.getRandomValues(vmkRaw);

    const vmk = await crypto.subtle.importKey(
      'raw',
      vmkRaw,
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );

    const vmkIv = new Uint8Array(12);
    crypto.getRandomValues(vmkIv);
    const wrappedVmkBuffer = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: vmkIv, tagLength: 128 },
      kek,
      vmkRaw
    );

    const enc = new TextEncoder();
    const verifierIv = new Uint8Array(12);
    crypto.getRandomValues(verifierIv);
    const verifierBuffer = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: verifierIv, tagLength: 128 },
      vmk,
      enc.encode(VERIFY_TOKEN)
    );

    const config: VaultMetaConfig = {
      version: 1,
      kdfSalt: bufferToBase64(salt),
      kdfIterations: 100000,
      wrappedVmk: bufferToBase64(wrappedVmkBuffer),
      wrappedVmkIv: bufferToBase64(vmkIv),
      verifier: bufferToBase64(verifierBuffer),
      verifierIv: bufferToBase64(verifierIv),
      updatedAt: Date.now()
    };

    const user = auth.currentUser;
    if (user) {
      const docRef = doc(db, 'user', user.uid, 'vault_meta', 'config');
      await setDoc(docRef, config);
      await setMetaValue(`has_vault_${user.uid}`, 'true');
    } else {
      await setMetaValue('vault_config_offline', JSON.stringify(config));
      await setMetaValue('has_vault_offline', 'true');
    }

    this.inMemoryVaultKey = vmk;
    this.isVaultUnlocked = true;
    this.notifyListeners();
  }

  public async unlockVault(password: string): Promise<boolean> {
    let config: VaultMetaConfig | null = null;
    const user = auth.currentUser;

    if (user) {
      try {
        const docRef = doc(db, 'user', user.uid, 'vault_meta', 'config');
        const snapshot = await getDoc(docRef);
        if (snapshot.exists()) {
          config = snapshot.data() as VaultMetaConfig;
        }
      } catch (e) {
        console.warn('Failed to fetch remote vault config:', e);
      }
    }

    if (!config) {
      const offlineJson = await getMetaValue(user ? `vault_config_${user.uid}` : 'vault_config_offline');
      if (offlineJson) {
        config = JSON.parse(offlineJson) as VaultMetaConfig;
      }
    }

    if (!config || !config.wrappedVmk || !config.kdfSalt) {
      throw new Error('No existing vault found. Please create one.');
    }

    const salt = base64ToBuffer(config.kdfSalt);
    const kek = await deriveKey(password, salt);

    const wrappedVmkBytes = base64ToBuffer(config.wrappedVmk);
    const wrappedVmkIvBytes = base64ToBuffer(config.wrappedVmkIv);

    try {
      const unwrappedVmkBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: wrappedVmkIvBytes as BufferSource, tagLength: 128 },
        kek,
        wrappedVmkBytes as BufferSource
      );

      const vmk = await crypto.subtle.importKey(
        'raw',
        unwrappedVmkBuffer,
        { name: 'AES-GCM', length: 256 },
        false,
        ['encrypt', 'decrypt']
      );

      const verifierBytes = base64ToBuffer(config.verifier);
      const verifierIvBytes = base64ToBuffer(config.verifierIv);
      const decryptedVerifierBuffer = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: verifierIvBytes as BufferSource, tagLength: 128 },
        vmk,
        verifierBytes as BufferSource
      );

      const dec = new TextDecoder();
      const token = dec.decode(decryptedVerifierBuffer);

      if (token === VERIFY_TOKEN) {
        this.inMemoryVaultKey = vmk;
        this.isVaultUnlocked = true;
        this.notifyListeners();
        return true;
      } else {
        throw new Error('Incorrect vault password.');
      }
    } catch {
      throw new Error('Incorrect vault password. Please try again.');
    }
  }

  public lockVault(): void {
    this.inMemoryVaultKey = null;
    this.isVaultUnlocked = false;
    this.notifyListeners();
  }
}

export const vaultManager = new VaultManager();
