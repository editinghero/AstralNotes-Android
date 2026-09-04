# AstralNotes - Cloud Sync, Database & Vault Setup Guide

This guide provides instructions for setting up the local databases, Cloud Firestore synchronization, zero-knowledge vault metadata, and Google Gemini AI for **AstralNotes** on both Android (`com.astralquarks.notes`) and Web (`astralnotesweb`).

---

## 1. System Architecture

AstralNotes utilizes an **Offline-First Reactive Architecture** across platforms:

```text
+------------------------------------+        +------------------------------------+
|        Android Jetpack Compose     |        |          Web TypeScript PWA        |
|  (Compose UI, ViewModels, Coroutines) |     |     (Vite, IndexedDB, Web Crypto)  |
+-----------------+------------------+        +-----------------+------------------+
                  │                                             │
                  ▼                                             ▼
+------------------------------------+        +------------------------------------+
|        Local Room Database         |        |         Local IndexedDB            |
|       (SQLite / DAO / KSP)         |        |     ("astral_notes_db" store)      |
+-----------------+------------------+        +-----------------+------------------+
                  │                                             │
                  │ (AES-256-GCM Encrypted Notes & Vault Meta) │
                  ▼                                             ▼
+----------------------------------------------------------------------------------+
|                              Google Cloud Firestore                              |
|       - user/{uid}/notes/{noteId}         (Encrypted/Plaintext user notes)      |
|       - user/{uid}/vault_meta/config      (Salt, Wrapped VMK, Verifier Token)    |
|       - shares/{shareId}                  (Ephemeral encrypted shared links)     |
+----------------------------------------------------------------------------------+
```

---

## 2. Firebase Cloud Configuration

Both Android and Web share the same Firebase backend project for seamless cross-platform note synchronization.

### Step 1: Create Firebase Project
1. Navigate to the [Firebase Console](https://console.firebase.google.com/).
2. Create a new project or select an existing project.

### Step 2: Configure Android Application
1. In Project Settings, click **Add app** and select **Android**.
2. **Package Name:** `com.astralquarks.notes`
3. Generate and paste your development or release SHA-1 certificate fingerprint:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey
   ```
4. Download `google-services.json` and place it in the `app/` directory of the project.

> **Security Notice:** Do not commit `google-services.json` or personal API keys into public version control. It is already included in `.gitignore`.

### Step 3: Configure Web Application
1. In Project Settings, click **Add app** and select **Web**.
2. Register the web app and obtain the configuration object.
3. Configure `web/src/firebase.ts` with your project configuration:
   ```typescript
   export const firebaseConfig = {
     apiKey: "YOUR_API_KEY",
     authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
     projectId: "YOUR_PROJECT_ID",
     storageBucket: "YOUR_PROJECT_ID.firebasestorage.app",
     messagingSenderId: "YOUR_SENDER_ID",
     appId: "YOUR_APP_ID"
   };
   ```

### Step 4: Enable Authentication Providers
1. In the Firebase Console, go to **Build > Authentication > Sign-in method**.
2. Enable **Google** as a sign-in provider.
3. Add your support email and configure OAuth credentials matching your Google Cloud Console setup.

### Step 5: Enable Cloud Firestore & Deploy Security Rules
1. Navigate to **Build > Firestore Database** and click **Create Database**.
2. Go to the **Rules** tab, replace the contents with the following rules, and click **Publish**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User notes collection (read and write allowed only for the owning user)
    match /user/{userId}/notes/{noteId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Zero-knowledge vault metadata (salt, wrapped master key, verifier token)
    match /user/{userId}/vault_meta/{docId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Read-only ephemeral shared notes
    match /shares/{shareId} {
      // Anyone can read a shared note if it is unexpired
      allow read: if resource == null || 
        resource.data.expiresAt == null || 
        resource.data.expiresAt > request.time.toMillis();
        
      // Only the authenticated author can create, update, or revoke their share
      allow create: if request.auth != null && request.resource.data.ownerUid == request.auth.uid;
      allow update, delete: if request.auth != null && resource.data.ownerUid == request.auth.uid;
    }
  }
}
```

---

## 3. Zero-Knowledge Private Vault Architecture

AstralNotes features client-side zero-knowledge encryption for sensitive notes:

1. **Key Derivation (PBKDF2):** When you configure a vault password, a 256-bit Key Encryption Key (KEK) is derived using 100,000 PBKDF2 iterations and a cryptographic salt.
2. **Master Vault Key (VMK):** A random 256-bit AES-GCM Vault Master Key is generated. It encrypts and decrypts all vault notes.
3. **Key Wrapping:** The VMK is encrypted by the KEK and stored in Firestore under `user/{uid}/vault_meta/config`.
4. **Decryption in Memory:** When unlocking your vault, your password unwraps the VMK into volatile application memory. The plaintext password and VMK are never stored on disk or sent over the network.
5. **Sync Parity:** Locked notes synced to Firestore are encrypted before transmission (`isEncrypted: true`, `encryptedData`, `iv`).

---

## 4. Google Gemini AI Setup (User-Provided Keys)

AstralNotes operates with **zero server-side or bundled API keys**. Each user connects their own personal key:

1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Open AstralNotes on Android or Web.
3. Navigate to **Settings > Google Gemini Intelligence** (or tap the Sparkle icon on any note).
4. Paste your key into **Custom Gemini API Key** and select your preferred model (`gemini-3.7-flash`, `gemini-3.5-flash`, etc.).
5. Tap **Save Gemini Config**.

---

## 5. Library Backup & Restore Flow

1. **Exporting:**
   - Navigating to **Backup & Restore** generates a standardized JSON snapshot of all notes.
   - If locked notes are present, entering the vault password decrypts the notes and re-encrypts them with a salted backup key.
2. **Importing:**
   - Selecting an exported JSON file reconstructs notes with intact tags, colors, and timestamps.
   - If importing vault notes, entering the exported file's vault password unlocks them and re-encrypts them into your local vault session.
3. **Cross-Platform Compatibility:**
   - A backup generated on an Android phone can be restored directly into the web application, and vice versa.
