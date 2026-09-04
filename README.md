# Astral Notes

AstralNotes is an offline-first, high-performance note-taking and knowledge management system available as a native Android app or responsive web progressive web application (PWA). It combines Material 3 Expressive aesthetics, client-side zero-knowledge AES-256-GCM Vault encryption, user-owned Google Gemini AI API Key, and real-time bidirectional cloud synchronization. 

## Screenshot & Live Links

- **Android App:** [Download APK from GitHub Releases](https://github.com/editinghero/AstralNotes-Android/releases/)
- **Web App:** [Open AstralNotes Web](https://astralnotesweb.pages.dev/)
- **Visual Preview:** [Instagram Showcase](https://www.instagram.com/p/DcVwyKWkj9i/?img_index=4)

## Features

- **Material 3 Design:** Fluid adaptive layouts, custom pastel note tints, contrast-optimized themes, and responsive mobile and desktop viewports.
- **Full Markdown Engine:** Live interactive checklists, code blocks with syntax styling and one-tap copy, headers, text formatting, and automatic list continuation.
- **Client-Side Zero-Knowledge Vault:** Confidential notes are encrypted on-device using AES-256-GCM and PBKDF2. The Master Vault Key remains in local memory and is never uploaded in plaintext.
- **User-Provided Gemini AI Intelligence:** 100% user-owned API keys. Instant note summarization, checklist generation, executive action items, and knowledge base conversations across selectable models.
- **Encrypted Ephemeral Sharing:** Generate read-only web links with optional password protection and time-based auto-expiration (1 hour, 1 day, 7 days, or custom). Private notes mask their title automatically.
- **Offline-First Persistence:** Native Android SQLite persistence via Room and web persistence via IndexedDB ensure full access without internet connectivity.
- **Bidirectional Cloud Sync:** Real-time synchronization across Android and Web powered by Cloud Firestore and Google Authentication.
- **Universal Cross-Platform Backup:** Interchangeable JSON backup format that seamlessly exports and imports note libraries between Android and Web, including encrypted vault packaging.
- **Home Screen Quick Note Widget:** Capture thoughts, checklists, images, and trigger AI assistance directly from the Android home screen.

## Getting Started

### 1. Install or Access AstralNotes

[Download APK from GitHub Releases](https://github.com/editinghero/AstralNotes-Android/releases/)

or Can Use The Web App Without Installation-

- **Android:** Download the latest signed APK from [GitHub Releases](https://github.com/editinghero/AstralNotes-Android/releases) and install on Android 8.0 (API 24) or newer.
- **Web:** Navigate to [astralnotesweb.pages.dev](https://astralnotesweb.pages.dev/) in any modern desktop or mobile browser.

### 2. Configure Your Gemini AI Key (Optional)

AstralNotes requires no bundled server keys. You retain full control with your own personal Google AI key:

1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Open AstralNotes and navigate to **Settings > Google Gemini Intelligence** (or tap the Sparkle icon on any note).
3. Paste your key into **Custom Gemini API Key**.
4. Select your preferred model (such as `gemini-3.7-flash` or `gemini-3.5-flash`) and adjust thinking preferences.
5. Tap **Save Gemini Config**.

> **Note:** Your API key is stored strictly on your local device and is never sent to any intermediary server.

### 3. Set Up Your Private Vault & Cloud Sync

**Offline-First Mode (Default):**
- Use AstralNotes immediately without an account. All notes persist in your local database.

**Cloud Sync & Multi-Device Parity:**
1. In Settings, select **Cloud Sync & Google Sign-In**.
2. Authenticate with your Google account.
3. Your notes will sync bidirectionally in real time with Cloud Firestore.

**Private Vault Protection:**
1. Navigate to **Vault** from the navigation drawer.
2. Set up your master vault password.
3. On Android, enable biometric authentication (fingerprint or face unlock) for instant access.
4. Move sensitive notes to the vault. Notes are encrypted with AES-256-GCM before syncing to the cloud.

### 4. Quick Tips

- **Interactive Checklists:** Type `- [ ] ` to start a checklist item. Pressing Enter continues the list automatically.
- **Code Blocks:** Wrap text in triple backticks with a language specifier for styled code cards with copy functionality.
- **Private Note Sharing:** When sharing locked notes, titles are masked as "Private Note" on public reader links and tabs.
- **Empty Trash:** Purge deleted items permanently from both local storage and cloud database using the Empty Trash button.

## Install as App

AstralNotes can be installed directly as a standalone Progressive Web Application on desktop and mobile:

**On Desktop (Chrome, Edge, Brave):**
1. Visit [astralnotesweb.pages.dev](https://astralnotesweb.pages.dev/).
2. Click the install icon in the address bar or select **Install AstralNotes** from browser settings.

**On Mobile (Safari iOS / Chrome Android):**
1. Visit [astralnotesweb.pages.dev](https://astralnotesweb.pages.dev/).
2. Tap **Share** (iOS) or browser menu (Android) and choose **Add to Home Screen**.

## Security & Privacy

- **Zero-Knowledge Architecture:** Vault notes are encrypted client-side with AES-256-GCM and a PBKDF2-derived key. Neither cloud operators nor database administrators can view your encrypted content.
- **Local Isolation:** Local databases (Room SQLite on Android, IndexedDB on Web) store data within sandboxed app boundaries.
- **Granular Cloud Access:** Firestore security rules isolate every document to `users/{userId}/notes/{noteId}`, allowing read/write operations exclusively for authenticated owners.
- **Zero Third-Party Trackers:** No analytics trackers, ad networks, or telemetry SDKs are embedded in the applications.

## Use Cases

- **Developer Knowledge Base:** Manage technical documentation, syntax-formatted snippets, and release tasks.
- **Confidential Journal & Credentials:** Store sensitive credentials, recovery phrases, and private logs behind client-side encryption.
- **Lecture & Meeting Summaries:** Record notes and use Gemini AI to generate structured takeaways and action items.
- **Cross-Platform Daily Tasks:** Coordinate personal checklists and to-dos seamlessly between your Android phone and desktop workstation.

## Need Help?

**Google Sign-In fails or shows developer error?**
- Verify that Google Sign-In is enabled in Firebase Authentication and that your Android application SHA-1 certificate fingerprint is registered in Project Settings.

**Gemini AI returns an authentication error?**
- Ensure your Google AI Studio API key is entered accurately in **Settings > Google Gemini Intelligence** and has active quota.

**Restoring vault notes from backup requires a password?**
- When restoring a backup containing locked notes, enter the vault password configured when that backup file was exported.

---

## For Developers

Want to run your own instance or contribute?

### Tech Stack

- **Android App:** Jetpack Compose (Material 3 Expressive), Kotlin 2.2, AndroidX Room 2.7, Coroutines, StateFlow, Credential Manager, Coil
- **Web App:** TypeScript, Vite, Vanilla CSS design system, Web Crypto API (SubtleCrypto), IndexedDB
- **Cloud & Auth:** Google Cloud Firestore, Firebase Authentication
- **AI Engine:** Google Gemini REST API v1beta
- **Hosting:** Cloudflare Pages (Web), GitHub Releases (Android APK)

### Quick Setup

**Android Native:**
```bash
git clone https://github.com/editinghero/AstralNotes-Android.git
cd AstralNotes-Android
./gradlew assembleRelease
```

**Web Application:**
```bash
cd web
pnpm install
pnpm dev
```

### Firebase Setup

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.astralquarks.notes` and register your SHA-1 fingerprint.
3. Download `google-services.json` and place it in the `app/` folder.
4. For the web application, add a Web App in Firebase Console and configure `web/src/firebase.ts` with your project identifiers.
5. Enable **Authentication > Google** as a sign-in provider.
6. Enable **Cloud Firestore** and deploy the security rules described in [`SETUP.md`](SETUP.md).

### Universal Backup & Restore Format

Backup files use a standardized JSON schema supported across Android and Web:

```json
{
  "version": 1,
  "app": "AstralNotes",
  "exportedAt": 1788500000000,
  "notes": [
    {
      "id": "uuid",
      "title": "Sample Note",
      "content": "Markdown content...",
      "tags": ["work"],
      "colorHex": "#DEFAULT"
    }
  ],
  "vaultNotes": [
    {
      "id": "uuid",
      "encryptedData": "base64...",
      "iv": "base64...",
      "salt": "base64...",
      "isLocked": true
    }
  ]
}
```

### Project Structure

```text
app/src/main/java/com/astralquarks/notes/
  ai/                  Gemini AI API client and action prompt handlers
  auth/                Firebase Authentication and Firestore sync observer
  db/                  Room Database, DAO queries, and JSON converters
  markdown/            Markdown parser, renderer, and formatting helpers
  model/               Data models (Note, NoteColor, AiChatMessage)
  repository/          Offline-first repository managing Room and Cloud sync
  security/            BiometricPrompt, CryptoEngine, and VaultSecurityManager
  ui/
    components/        Toolbar, bottom dock, markdown editor, AI sheet, cards
    screens/           Home, Edit, Vault, Archive, Trash, Settings
    theme/             Material 3 Expressive theme, typography, color palettes
  util/                LibraryBackupManager, MultiFormatExporter
  viewmodel/           Reactive StateFlow UI viewmodels
web/
  src/
    backup.ts          Universal JSON library backup and restore manager
    crypto.ts          SubtleCrypto AES-256-GCM and PBKDF2 encryption engine
    db.ts              IndexedDB client database implementation
    main.ts            AstralNotesApp controller, router, and UI state
    share.ts           Encrypted note sharing, expiration, and reader logic
    sync.ts            Real-time Firestore bidirectional sync engine
    vault.ts           VaultManager master key derivation and session state
```

---

**Built with ❤️ for productive thinkers and developers**

[Report an Issue](https://github.com/editinghero/AstralNotes-Android/issues) • [Request a Feature](https://github.com/editinghero/AstralNotes-Android/issues/new)
