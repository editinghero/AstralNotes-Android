# AstralNotes - Release Notes & Migration Guide

## What is New in This Version

- **Zero-Knowledge Private Vault:** Your locked notes are now encrypted directly on your device using military-grade AES-256-GCM encryption. Your vault password never leaves your device, and notes are encrypted before syncing to the cloud.
- **Privacy-First Note Sharing:** When sharing a private note, its title is masked as "Private Note" across reader tabs and web links. Manage and revoke active links directly inside each note's share menu.
- **Redesigned Shared Reader:** A clean, responsive read-only web interface matching your current theme without heavy styles.
- **Empty Trash:** Easily purge all discarded notes in one tap from both your local database and the cloud.
- **Seamless Web & Android Sync:** Real-time note synchronization between your Android phone and the web app at [astralnotesweb.pages.dev](https://astralnotesweb.pages.dev/).

---

## Why Migrate (and Can You Keep Using the Old Version?)

- **Can I stay on the old version?**  
  Yes. If you prefer, your older version will continue working completely offline. Your existing notes stored on your device remain safe and accessible.
- **Why should you migrate to the new version?**  
  The new version introduces client-side zero-knowledge encryption and a modernized cloud synchronization format. Older versions cannot decrypt the new vault format or take advantage of encrypted web sharing and cross-device sync with the web app.

---

## How to Migrate to the Latest Version

### Method 1: Automatic Full Library Migration (Recommended)

1. **In your current app:**  
   Open **Settings > Library Backup & Restore**.
2. **Export your library:**  
   Tap **Export Backup**. If you have locked notes, enter your vault password when prompted, then save the `.json` backup file to your device or Google Drive.
3. **Install the new version:**  
   Download and install the latest APK from [GitHub Releases](https://github.com/editinghero/AstralNotes-Android/releases).
4. **Restore your library:**  
   Open the new app, go to **Settings > Library Backup & Restore**, tap **Import Backup**, and select your saved `.json` file. Enter your vault password to restore your locked notes.

---

### Method 2: Manual Note-by-Note Export

If you prefer to migrate specific notes individually:

1. Open a note in your current version.
2. Tap the top menu or Share button and select **Export as Markdown (.md)**.
3. Save the `.md` file to your storage. Repeat for any other important notes.
4. In the new version, tap the **Import** button or create a new note and paste your content directly.
