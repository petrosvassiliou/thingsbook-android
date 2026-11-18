# Thingsbook v2.0.0 (Build 71) — Official Final Release

**Thingsbook 2.0 (`v2.0.0`, Build `71`)** is now officially available for **Android**, **ChromeOS (Standalone PWA)**, and **macOS (Universal Apple Silicon + Intel)**. This milestone promotes the complete `2.0` feature suite and cross-platform quality-assurance hardening out of beta into General Availability — delivering **100% seamless, zero-data-loss in-place upgrades** from `v1.7.x`, `v1.8.x`, and `v2.0.0-beta.1`–`v2.0.0-beta.26`.

---

## ✨ What’s New in Thingsbook 2.0

### 1. 🧭 Guided 4-Step GTD Weekly Review & Cross-Device Review Sync (`Ctrl+Shift+R` / `⇧⌘R`)
- **Interactive 4-Step Review Wizard**:
  1. **Clear Inbox**: Rapidly triage unprocessed Inbox items (*Today*, *This Evening*, *Tomorrow*, *Someday*, *Complete*, or *Trash*).
  2. **Today & Overdue Deadlines**: Catch overdue commitments and reschedule or complete due-soon items in one tap.
  3. **Projects Attention Audit**: Automatically highlights open projects that lack an open next-action task or haven't been reviewed in **14+ days** — with inline **"Add next action…"**, **"Mark Reviewed ✓"** (`lastReviewedAt`), and **"Move to Someday"**, synchronized monotonically across Android, ChromeOS, and macOS.
  4. **Someday & Weekly Wrap-Up**: Activate Someday ideas, review 7-day completion momentum, and log your Weekly Review.

### 2. 📋 Reusable Project Templates & Markdown / JSON Outline Importer
- **Save Any Project as a Synced Template**: Turn any project (with headings, open tasks, checklists, tags, durations, and relative schedule offsets) into a reusable `ProjectTemplate` synced across all devices via Firestore and included in encrypted `.tbbackup` archives.
- **Localized Starter GTD Gallery**: Built-in templates (*Trip & Packing*, *Product Launch*, *Client Onboarding*, *Weekly Sprint*) fully localized across **English, German, French, Italian, and Spanish**.
- **1-Click Markdown & JSON Outline Importer**: Paste `# Project`, `## Heading`, `- [ ] Task`, and indented `- [ ] Subtask` outlines (or JSON blueprints) to instantiate structured GTD projects immediately.

### 3. ⏱️ Interactive Timeblocking, Daily Timeline & Quiet Subordinate Calendar Appointments
- **Task Timeblocks (`scheduledStartMinutes` & `durationMinutes`)**: Assign start times and durations (`15m`–`2h`) to tasks with automatic Focus Timer duration matching.
- **`📋 List` ↔ `🗓️ Timeline` Toggle in Today**: Chronological daily timeline interleaving **Calendar Events** and **Timeblocked Tasks** with overlap detection, `+15m` quick nudges, and an **Unscheduled Today** tray.
- **Compact, Subordinate Calendar Appointments**: Past appointments automatically clear from *Today* and *Planned/Upcoming*, and remaining events render inside a quiet, low-contrast grouped surface so visual focus stays squarely on your tasks across Android, ChromeOS, and macOS.

### 4. ⚡ Instant Cross-Platform Cloud Sync (`v1.8` ↔ `v2.0`) & Zero-Duplicate Recurring Tasks
- **Pull-Before-Push Delta Sync & Fast `ntfy` UID Wake Relay**: Sub-second multi-device wake notifications (`SHA-256` UID topic + chunked legacy compatibility), per-collection Firestore cursors, self-healing parent project/area hydration, and 120s clock-skew protection.
- **Deterministic Recurring Task Deduplication**: Cross-platform deterministic UUID generation, non-poisoning midnight rollover, and automatic deduplication across Android, ChromeOS, and macOS.

### 5. 🖥️ Native Desktop & Mobile Polish Across All Three Platforms
- **Project Deletion Choice + Full Undo / Trash Restore**: Choose between cascade-deleting a project's tasks or detaching & keeping them in the parent Area, with full 5-second **Undo** and Trash restoration.
- **Refined Task Card Editing & Today-Only `🌙 This Evening` Quick Pill**: Opening an existing task card no longer auto-selects all title/notes text, and tasks in *Today* include a 1-click **This Evening + Close** action pill.
- **macOS In-App Auto-Updater & Native Icon+Label Settings Toolbar**: Zero-DMG in-app update installation (`Settings → Updates → Install & Relaunch`), native AppKit/SwiftUI Settings toolbar, centered instant warm-handoff top-bar tooltips, and draggable Magic Plus (`+`) drop targets.
- **100% 5-Language Localization Parity (`EN`, `DE`, `FR`, `IT`, `ES`)**: Verified automatically on every build across Android, ChromeOS, and macOS.

---

## 📥 Download & Upgrade (`v1.7.x` / `v1.8.x` / `v2.0.0-beta.x` → `v2.0.0`)

- **📱 Android (`versionCode 71`, Universal Signed APK)**:
  - Download **`Thingsbook-v2.0.0-GTD-Full.apk`** below (or use **Settings → App Update → Check for Updates** inside the app) for an instant in-place upgrade with zero data loss.
- **💻 ChromeOS & Web (Standalone PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** — installed Chromebook Launcher apps update automatically in place via Service Worker.
- **🍎 macOS (`Build 71`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade directly inside Thingsbook (**Settings → Updates → Install & Relaunch**), download **`Thingsbook-macOS-v2.0.0-Universal.dmg`** below, or run the 1-command terminal installer:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
