# GDrive Sync – Android Setup Guide

A lightweight Android app that syncs a local folder to Google Drive automatically.  
Designed to keep an **Obsidian vault** in sync so AI agents can read / write shared memory.

---

## Architecture

```
MainActivity (Compose)
  └── SyncViewModel
        ├── SyncPreferences  (DataStore – persists config)
        └── DriveRepository  (Google Drive REST API v3)
              └── called by SyncWorker (WorkManager periodic job)
```

| Component | Role |
|-----------|------|
| `DriveRepository` | Walks local SAF tree, compares with Drive, uploads new/changed files |
| `SyncWorker` | Runs in background via WorkManager; shows foreground notification |
| `SyncPreferences` | Persists local URI, Drive folder ID, interval, flags |
| `BootReceiver` | Re-schedules sync after device reboot |

---

## Google Cloud Console – one-time setup

1. Go to <https://console.cloud.google.com/> and create a project (e.g. `gdrive-sync`).
2. **Enable** the **Google Drive API** for the project.
3. **Configure OAuth consent screen** → External → add your Gmail as a test user.
4. Create **OAuth 2.0 credentials** → **Android**:
   - Package name: `com.agentcouncil.gdrivesync`
   - SHA-1 fingerprint of your debug keystore:
     ```
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
       -storepass android -keypass android
     ```
5. No `google-services.json` is needed — this app uses `GoogleAccountCredential` directly.

---

## Build & Install

```bash
# Clone the repo
git clone https://github.com/sentry01/agentcouncil
cd agentcouncil/android-gdrive-sync

# Open in Android Studio, or build from CLI:
./gradlew assembleDebug

# Install on connected device / emulator
./gradlew installDebug
```

**Requirements**: Android 8.0+ (API 26), network connection for Drive sync.

---

## Usage

1. **Sign in** with your Google account.
2. **Choose local folder** – tap "Choose Folder" and grant the app access via the system file picker (SAF).  
   For Obsidian, pick the vault root (e.g. `/storage/emulated/0/Documents/MyVault`).
3. **Select Drive folder** – either pick an existing folder or type a name to create one  
   (e.g. `ObsidianVault`). Share this folder with your agents / collaborators.
4. **Configure settings**:
   - Toggle **Auto Sync** on to enable periodic background sync.
   - Adjust the **interval** (15 min … 6 h).
   - Enable **Sync subfolders** to mirror the full directory tree.
   - Enable **Delete orphans** if you want Drive to mirror deletions from the phone.
5. Tap the **Sync** FAB for an immediate manual sync.

---

## How sync works

```
Local SAF tree  ──── compare mod-time ──── Google Drive folder
     │                                            │
     │  new / updated  ──────────────────► upload │
     │                                            │
     │  (if deleteOrphans=true) ◄─── delete ──── orphan files
```

- Files are compared by **last-modified timestamp**.  
  Drive files are only overwritten when the local file is *newer*.
- Subfolders are replicated as Drive sub-folders recursively.
- The SAF URI is kept with a **persistent permission** so WorkManager can access  
  the folder even after the app is closed or the device reboots.

---

## Obsidian shared-memory pattern

```
Android phone (Obsidian)
  └── /Documents/AgentVault/   ← local vault
        └── GDriveSync (this app) → syncs every 15 min

Google Drive
  └── AgentVault/              ← shared folder
        ├── notes/
        ├── agent-logs/        ← agents write here
        └── memory/            ← structured agent memory files
```

Agents (Claude, GPT, Gemini) read & write markdown files in the Drive folder.  
The phone's Obsidian vault stays in sync automatically.

---

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Google Drive API calls |
| `READ_EXTERNAL_STORAGE` | Read local files (≤ Android 12) |
| `FOREGROUND_SERVICE` | Show progress notification during sync |
| `POST_NOTIFICATIONS` | Sync result notification |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule sync after reboot |
