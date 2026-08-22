# AstralNotes - Database & Cloud Sync Setup Guide

This document provides complete instructions for setting up, configuring, and verifying both the **Local Room Database** and **Cloud Firestore Sync** for **AstralNotes** (`com.astralquarks.notes`).

---

## 1. Architecture Overview

AstralNotes is built on an **Offline-First Reactive MVVM Architecture**:

```
+-------------------------------------------------------------------------+
|                              Jetpack Compose UI                         |
|     (HomeScreen, NoteEditScreen, VaultScreen, ArchiveScreen, etc.)      |
+-------------------------------------------------------------------------+
                                    ▲
                                    │ StateFlow / Actions
                                    ▼
+-------------------------------------------------------------------------+
|                               NotesViewModel                            |
+-------------------------------------------------------------------------+
                                    ▲
                                    │ Flow<List<Note>> / Coroutine Calls
                                    ▼
+-------------------------------------------------------------------------+
|                               NoteRepository                            |
+---------------------+-----------------------------------+---------------+
                      │                                   │
                      ▼ (Primary Single Source of Truth)  ▼ (Background Cloud Sync)
+-------------------------------+               +-------------------------+
|       Local Room Database     |               |     Firebase Firestore  |
|       (SQLite / KSP / DAO)    |               |  (users/{uid}/notes/..) |
+-------------------------------+               +-------------------------+
```

---

## 2. Local Database Setup (Room SQLite)

The local database operates 100% offline without requiring any external accounts or internet connection.

### Components
- **Entity**: `com.astralquarks.notes.model.Note` (`@Entity(tableName = "notes")`)
- **DAO**: `com.astralquarks.notes.db.NoteDao` (Reactive `Flow<List<Note>>` queries, full-text search, trash management, pin and archive filters)
- **Converters**: `com.astralquarks.notes.db.Converters` (JSON serialization for tags and image URLs)
- **Database Instance**: `com.astralquarks.notes.db.AppDatabase` (`"expressive_notes_db"`)

### Verification & Testing
Room database operations execute on background dispatchers (`Dispatchers.IO`) with reactive `StateFlow` bindings.

---

## 3. Cloud Database Setup (Firebase Firestore & Google Sign-In)

When users sign in via Google Sign-In (using Android Jetpack `CredentialManager`), their notes automatically synchronize bidirectionally with Google Cloud Firestore.

### Step 1: Firebase Project Configuration
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create or select your Firebase project.
3. Add an **Android Application**:
   - **Package Name**: `com.astralquarks.notes`
   - Download the `google-services.json` file and place it in the `app/` directory.

### Step 2: Enable Firebase Authentication
1. In the Firebase Console, navigate to **Build > Authentication > Sign-in method**.
2. Enable **Google** as a Sign-in provider.
3. Configure the **Web Client ID** matching your Google Cloud Console OAuth 2.0 Client Credentials.

### Step 3: Enable Cloud Firestore
1. Navigate to **Build > Firestore Database**.
2. Click **Create Database** and select your preferred region.
3. Choose **Production Rules** or configure the security rules below.

### Step 4: Recommended Firestore Security Rules
Copy and paste the following rules into **Firestore Database > Rules**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // User-isolated notes collection
    match /users/{userId}/notes/{noteId} {
      // Allow read/write only if authenticated and accessing own user folder
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

## 4. Google Gemini AI Setup (User-Provided API Key)

The integrated **Gemini AI Assistant** provides automatic summarization, markdown restructuring, brainstorming, action item checklist extraction, and interactive note chat.

### Setting Up Your Gemini API Key:
AstralNotes requires **zero server-side or hardcoded API keys**. Each user provides their own key directly inside the app:
1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Open AstralNotes on your device.
3. Open **Settings > Google Gemini Intelligence** (or tap the ✨ Sparkle icon on any note > **Model & Keys**).
4. Paste your key into **Custom Gemini API Key** and tap **Save Gemini Config**.
5. You can also switch between models (`gemini-3.7-flash`, `gemini-3.5-flash`, `gemini-3.1-pro-preview`) or enable High Thinking Mode anytime.

---

## 5. Bidirectional Synchronization Flow

1. **Local Writes**: When a note is created, updated, pinned, archived, or colored, it is instantly persisted to the local SQLite database via Room.
2. **Cloud Push**: If the user is signed in, `NoteRepository` triggers an asynchronous background job to upload the note to `users/{userId}/notes/{noteId}` in Firestore with merge semantics.
3. **Cloud Pull / Real-time Listener**: When signed in, `AuthManager.observeFirestoreNotes()` listens for remote snapshot changes and synchronizes any updates into the local Room database automatically.
4. **Instant Reconnect Sync**: Upon signing in, a full bidirectional reconciliation is performed between local Room DB and Cloud Firestore.
5. **App Widgets**: Upon every local or cloud mutation, `QuickNoteWidgetProvider.updateAllWidgets()` refreshes the Android home screen widgets.

---
