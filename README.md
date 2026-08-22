# AstralNotes - Android 17 Material Expressive Markdown Notes with Gemini AI

AstralNotes is a modern, high-performance note-taking and personal knowledge app built with Jetpack Compose, Material 3 Expressive design, local Room database persistence, Google Gemini AI intelligence, and optional Firebase Cloud Sync.

## Screenshot 
[Instagram](https://www.instagram.com/p/DcVwyKWkj9i/?img_index=4)
## Features

- **Material 3 Expressive Design** - Fluid adaptive layouts, custom pastel note tints, contrast-optimized dark and light modes, and Google Keep-style ease of use.
- **Full Markdown Editor** - Live interactive checklists, code blocks with 1-tap copy, headers, bold, italic, strikethrough, highlights, and automatic list continuations.
- **Biometric Private Vault** - Secure confidential and sensitive notes behind biometric fingerprint or custom alphanumeric PIN.
- **User-Provided Gemini AI Intelligence** - Instant note summarization, action item extraction, creative brainstorming, and interactive conversational chat across your entire knowledge base.
- **Offline-First Persistence** - All notes persist instantly in a local AndroidX Room SQLite database with zero internet connection required.
- **Cloud Sync with Firestore** - Real-time bidirectional note synchronization when signed in with Google.
- **Tags & Organization** - Flexible tag filters, pinned notes, archive storage, and trash recovery.
- **Multi-Format Export** - Export notes to Markdown (.md), Plain Text (.txt), HTML (.html), or JSON backup.
- **Home Screen Quick Note Widget** - Capture quick thoughts, checklists, images, and launch AI assistance directly from your Android home screen.

## Getting Started

### 1. Download and Install the App

1. Download the latest `AstralNotes-Release-APK` from the [Releases](https://github.com/editinghero/AstralNotes-Android/releases) or GitHub Actions build artifacts.
2. Install the `.apk` file on your Android device (Android 8.0 / API 24 or higher).
3. Open AstralNotes.

### 2. Configure Your Gemini AI Key

AstralNotes operates with 100% user-owned API keys. No developer or server-side API key is bundled or required.

1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. In AstralNotes, open the navigation drawer and tap **Settings** (or tap the Sparkle icon on any note).
3. Under **Google Gemini Intelligence**, paste your key into **Custom Gemini API Key**.
4. (Optional) Select your preferred model (such as `gemini-3.7-flash`, `gemini-3.5-flash`, or enable High Thinking Mode).
5. Tap **Save Gemini Config**.

> **Note:** Your API key is stored securely on your local device in private encrypted app storage and is never uploaded or shared.

### 3. Enable Cloud Sync (Optional)

**Offline-First Mode (Default):**
- You can use AstralNotes entirely offline without creating an account. All notes persist in your local SQLite Room database.

**Cloud Sync Mode:**
1. Open **Settings > Cloud Sync & Firebase Auth**.
2. Tap **Sign In with Google**.
3. Your notes will automatically sync bidirectionally with your private Cloud Firestore database across your devices.

### 4. Quick Tips

- **Checklists:** Type `- [ ] ` to start an interactive checklist. Tapping Enter on a checked or unchecked item automatically continues the list.
- **Code Blocks:** Wrap code in triple backticks with a language name (e.g. ```` ```kotlin ````) to get syntax-styled code cards with 1-tap copy.
- **Locking Notes:** Tap the 3-dot menu on any note and select **Lock Note** to move it into your Biometric Vault.
- **AI Chat:** Tap the Sparkle icon on any note to summarize, polish, or ask questions about that specific note or your entire library.

## Security & Privacy

- **Local Storage:** All notes are saved locally on your device in an isolated Room SQLite database.
- **Vault Protection:** Locked notes require your custom PIN or fingerprint biometric authentication to access and are hidden from regular note lists.
- **Private Cloud Data:** When Cloud Sync is active, Firestore security rules ensure that only your authenticated Google account can read and write to `users/{userId}/notes`.
- **Zero Third-Party Tracking:** No advertising SDKs, trackers, or hidden analytics.

## Use Cases

- **Developer Knowledge Base:** Store markdown documentation, code snippets, and checklists with syntax formatting.
- **Meeting & Lecture Notes:** Quickly capture ideas and use Gemini AI to generate executive summaries and extract action items.
- **Personal Journal & Vault:** Keep private journals, passphrases, and sensitive information protected behind biometric security.
- **Project Task Tracker:** Manage daily to-do lists, grocery lists, and project milestones with live interactive checkboxes.

## Need Help?

**Google Sign-In fails or shows developer error?**
- Verify that your Firebase project has Google provider enabled in Authentication and has your app's SHA-1 certificate fingerprint registered in Project Settings.

**Gemini AI returns an authentication error?**
- Check that you entered a valid API key from Google AI Studio in **Settings > Google Gemini Intelligence**.

---

## For Developers

Want to build from source or contribute?

### Tech Stack

- **UI Framework:** Jetpack Compose (Material 3 Expressive)
- **Language:** Kotlin 2.2
- **Architecture:** MVVM + Clean Architecture + Offline-First Coroutine Flows
- **Local Database:** AndroidX Room 2.7 (SQLite / KSP)
- **Cloud Backend:** Google Cloud Firestore + Firebase Authentication (Credential Manager)
- **AI Integration:** Google Gemini REST API v1beta
- **Image Loading:** Coil 2.7
- **Security:** AndroidX BiometricPrompt + Android Keystore
- **Widgets:** Android AppWidgetProvider + RemoteViews

### Quick Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/editinghero/AstralNotes-Android.git
   cd AstralNotes-Android
   ```
2. Open in **Android Studio Meerkat / Ladybug** or newer with JDK 21.
3. Sync Gradle and run on an Android device or emulator.

### Build Release APK

To compile and package the release APK:
```bash
./gradlew assembleRelease
```
The resulting signed APK will be located at:
```text
app/build/outputs/apk/release/app-release.apk
```

### Firebase Setup

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.astralquarks.notes`.
3. Add the project SHA-1 fingerprint
4. Download `google-services.json` and place it in the `app/` folder.
5. Enable **Authentication > Google Sign-In** and create a **Firestore Database** with the security rules in [`SETUP.md`](SETUP.md).

### Project Structure

```text
com.astralquarks.notes/
  ai/                  Gemini AI REST API integration, action parsers, and prompt engineering
  auth/                Firebase Authentication and Firestore sync manager
  db/                  Room Database, DAO queries, and JSON type converters
  markdown/            Markdown AST parser, live formatting toolbar, and renderer
  model/               Data models (Note, NoteColor, AiChatMessage)
  repository/          Offline-first data repository with Room and Cloud reconciliation
  security/            BiometricPrompt and custom PIN vault manager
  ui/
    components/        Bottom dock, note cards, markdown editor, AI sheet, dialogs
    screens/           Home, Edit, Vault, Archive, Trash, Tags, Settings, Dashboard
    theme/             Material 3 Expressive theme, typography, and pastel palettes
  util/                Multi-format exporter (MD, TXT, HTML, JSON) and Catbox uploader
  viewmodel/           StateFlow UI viewmodels and event handlers
  widget/              Home Screen App Widget provider
```

---

**Built with ❤️ for productive thinkers and developers**

[Report an Issue](https://github.com/editinghero/AstralNotes-Android/issues) • [Request a Feature](https://github.com/editinghero/AstralNotes-Android/issues/new)
