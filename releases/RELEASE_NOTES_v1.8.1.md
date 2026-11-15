# Thingsbook v1.8.1 (Build 44) — Quality Assurance, Cross-Platform Sync Hardening & 2.0-Beta Channel Isolation

**Thingsbook v1.8.1** completes a full codebase-wide Quality Assurance (QS) audit and automated test verification across **Android**, **ChromeOS (Web/PWA)**, and **macOS (Universal Apple Silicon + Intel)**, ensuring rock-solid stability and strict channel isolation ahead of the upcoming `2.0-beta` preview track.

All platforms upgrade seamlessly in place from `v1.7.0`, `v1.8.0`, and `v1.8.0+1`.

---

## Highlights & Fixes Across All Platforms

### 1. Strict `2.0-beta` Channel Isolation & SemVer `+build` Support
- **Stable Channel Protection**: Android (`AppUpdateManager`), ChromeOS (`pwaUpdater`), macOS (`AppUpdateService`), and `macos/install.sh` now strictly ignore `draft`, `prerelease`, and pre-release tags (`-beta`, `-alpha`, `-rc`) when running on a stable release, ensuring upcoming `v2.0.0-beta.*` builds never overwrite or prompt stable `1.8.1` installations.
- **Unified CI/CD & Hosting Isolation**: GitHub Actions runs automated unit test suites across all 3 platforms before packaging releases, publishes `-beta` tags exclusively with `--prerelease` (preserving stable `--latest`), and deploys `beta*` web branches to isolated Firebase Hosting preview channels while keeping `https://thingsbook-prod.web.app` strictly on stable `1.8.x`.

### 2. Cross-Platform Cloud Sync, Google OAuth & Attachment Reliability
- **Unified Google OAuth Across Android, ChromeOS & macOS**: Android's "Sign in with Google" now authenticates via real Firebase `OAuthProvider("google.com")`, signing into the exact same Firebase `uid` and Firestore workspace as ChromeOS and macOS.
- **Attachment Sync Hardening (`700 KB` Inline Cap & 8-Doc Chunking)**: Unified inline attachment sync threshold across all 3 platforms, chunked Firestore attachment uploads into 8-document batches to stay safely below Firestore's 10 MiB request limit, prevented metadata updates from overwriting remote `dataBase64` payloads, added canonical path-traversal guards, and added automatic one-time `v1.7 -> v1.8.1` cloud attachment backfill.
- **120-Second Clock-Skew Overlap & Future-Timestamp Clamping**: Eliminated missed real-time updates between devices with slight clock drift and prevented fast peer clocks from advancing `lastSyncTimestamp` into the future.
- **Privacy Hardening**: Removed legacy unencrypted LAN UDP broadcast fallback on Android and added delta message cursor tracking for secondary relay sync.

### 3. Undo, Trash, Database & Input Integrity
- **Recurring Task & Project Undo Parity**: Undoing completion of a recurring task now cleanly soft-deletes the spawned next occurrence and its checklist items across all platforms; undoing or restoring a deleted project restores its detached tasks and headings.
- **Compact Syncable Tombstones**: Emptying Trash or permanently deleting items writes compact syncable tombstones so deletions propagate to peers and never resurrect on full cloud pulls.
- **macOS Native Fixes**: Moved SQLite `PRAGMA foreign_keys = OFF` outside transactions during out-of-order cloud sync & `.tbbackup` restores, added deleted projects to Trash, prevented `⌘⌫` from deleting tasks while editing text fields, gated the Menu Bar popover behind Biometric App Lock, fixed stale struct captures in task/checklist editors, and localized date headers across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`).
- **ChromeOS & Android Fixes**: Protected active Markdown note typing against live-query cursor resets, guarded `Ctrl+<key>` tag shortcuts inside text inputs, improved PDF `Blob` previews & EXIF-aware OOM-safe camera compression, hardened Biometric App Lock against modal/shortcut bypasses, and fixed Safe Mode local-only recovery.

---

## Installation & Upgrade

- **Android (`v1.8.1`, `versionCode 44`)**: Download `Thingsbook-v1.8.1-GTD-Full.apk` (or use in-app Update in **Settings -> App Update**) to install directly over `v1.7.0` or `v1.8.0`.
- **ChromeOS / Web (`v1.8.1`)**: Live at **https://thingsbook-prod.web.app** (existing installed PWAs update automatically via Service Worker).
- **macOS (`v1.8.1`, Universal `arm64` + `x86_64`, Build `44`)**: Download `Thingsbook-macOS-v1.8.1-Universal.dmg` or upgrade in one command without Gatekeeper prompts:
  ```bash
  curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
  ```
