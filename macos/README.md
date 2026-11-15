# Thingsbook for macOS (`macos/`)

**100% Native Swift + SwiftUI + AppKit GTD Desktop App for macOS 14+ (Sonoma, Sequoia & Tahoe)** — engineered in strict feature, version, localization, cloud-sync, and `.tbbackup` binary parity with Thingsbook for Android (`main`).

---

## Instant 1-Command Install / Update (No Gatekeeper Prompts)

Install or update `Thingsbook.app` into `/Applications` on any Apple Silicon or Intel Mac with one Terminal command:

```bash
curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/macOS/macos/install.sh | bash
```

*(Downloading via `curl` avoids browser quarantine flags so macOS Sequoia/Tahoe opens `Thingsbook.app` immediately without manual System Settings overrides.)*

---

## Highlights & Apple Human Interface Guidelines

- **Modern macOS Desktop Design (Sequoia / Tahoe Vibrancy)**
  - `NavigationSplitView` with `.ultraThinMaterial` translucent sidebar, unified split toolbar (Quick Find pill on left; Gemini pill, Quick Entry, Manage Areas, Manage Tags, Notifications, Language switcher, Cloud Sync status, Settings on right), crisp Things-style Light/Dark cards, and SF Symbols.
  - Live pie-chart `ProjectProgressRing` indicators, smooth spring-animated inline task card expansion, Magic Plus (`+`) contextual creation popover, and keyboard-first navigation (`⌘N`, `⇧⌘N`, `⌥⌘N`, `⌃⌘N`, `⌘F`, `⌘G`, `⌘K`, `⌘T`, `⌘E`, `⌘O`, `⌘D`, `⌥⌘F`, `⌘Z`, `⌘⌫`, `⌘1`–`⌘7`, `⌘,`).
- **System-Wide macOS Integration**
  - **`MenuBarExtra` Status Bar Item:** One-click Quick Capture & Today mini-list from anywhere on macOS.
  - **macOS Services ("Save to Thingsbook"):** Highlight text in Safari, Mail, or Notes and send straight into Thingsbook Quick Entry.
  - **URL Scheme (`thingsbook://`) & `.tbbackup` File Association:** Supports `thingsbook://add?title=...&notes=...`, `thingsbook://today`, `thingsbook://search`, and double-clicking `.tbbackup` archives in Finder.
  - **Apple Calendar (`EventKit`):** Read-only calendar events in *Today* and *Upcoming* strictly on dates with scheduled tasks; clicking opens Apple Calendar at that event.
  - **Touch ID App Lock (`LocalAuthentication`):** Optional biometric/device unlock overlay.
  - **Actionable macOS Notifications (`UNUserNotificationCenter`):** Task reminders with `Complete`, `+1h Snooze`, and `Tomorrow` actions, plus Daily Morning Digest, Test Notification, and 25-minute Focus Pomodoro timer (`+5m`, Pause/Resume, Complete).
- **100% Cross-Platform Compatibility with Android & ChromeOS**
  - **SQLite3 + WAL + FTS5 (`unicode61 remove_diacritics 2`):** Instant diacritic-insensitive search across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES` — 407 verified localization keys with zero missing keys).
  - **`.tbbackup` v1 Binary Compatibility:** GZIP + `PBKDF2WithHmacSHA256` (120,000 iterations) + `AES/GCM/NoPadding` (`THINGSBOOK_BACKUP_V1|1` AAD). Backups exported on Android restore directly on macOS and vice versa.
  - **Firebase Firestore v1 Bidirectional Delta Sync:** Syncs `users/{uid}/{tags,areas,projects,headings,tasks,checklist_items}` via lightweight native `URLSession` + macOS Keychain (supports Official Thingsbook Cloud, Instant Email Sync, custom Firebase projects, and strict Local-Only DB mode).
  - **Thingsbook Intelligence (`gemini-2.5-flash`):** Magic Checklist Breakdown, Brain Dump → GTD Import, AI Project Architect, and Logbook Reflection (with localized 5-language offline fallbacks).

---

## Building & Running Locally from Source

```bash
# 1. Verify parity & zero-missing-key localization audit against Android main branch
python3 macos/scripts/sync-strings-from-android.py
python3 macos/scripts/verify-android-parity.py

# 2. Run Swift unit & cross-platform backup vector tests
cd macos && swift test

# 3. Build Universal (Apple Silicon arm64 + Intel x86_64) Thingsbook.app & .dmg
./macos/scripts/build-dmg.sh
```

The universal installer disk image is output to `macos/dist/Thingsbook-macOS-v1.7.0-Universal.dmg`.

---

## Keeping `macOS` in Lockstep with `main` (Android-First Workflow)

Because all macOS files live cleanly inside `macos/` (plus `.github/workflows/build-macos.yml`), merging new Android releases from `main` is conflict-free:

```bash
git checkout macOS
git merge main
python3 macos/scripts/sync-strings-from-android.py
# Port any newly added Android features from main into macos/, bump macos/PARITY_MANIFEST.json & macos/Resources/Info.plist:
python3 macos/scripts/verify-android-parity.py
```

If `app/build.gradle.kts` (`versionName` / `versionCode`) or any of the 5 `strings.xml` files advance on `main` before `macos/` is updated, `verify-android-parity.py` fails the CI build in `.github/workflows/build-macos.yml` until parity is restored.
