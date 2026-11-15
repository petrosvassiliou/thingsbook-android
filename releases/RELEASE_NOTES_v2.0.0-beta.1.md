# Thingsbook v2.0.0-beta.1 (Build 45) — Public Beta Preview

**Thingsbook v2.0.0-beta.1** introduces three major GTD productivity pillars across **Android**, **ChromeOS (Web/PWA)**, and **macOS (Universal Apple Silicon + Intel)** — engineered for **100% in-place, zero-data-loss upgrades from `v1.7.0`, `v1.8.0`, and `v1.8.1`**, while keeping the stable **`v1.8.1`** production channel completely untouched until `2.0.0` reaches final release stability.

---

## ✨ What’s New in Thingsbook 2.0 Beta 1

### 1. 🧭 Guided 4-Step GTD Weekly Review & Stalled Project Detection (`Ctrl+Shift+R` / `⇧⌘R`)
- **Interactive 4-Step Review Wizard**:
  1. **Clear Inbox**: Rapidly triage unprocessed Inbox items (*Today*, *This Evening*, *Tomorrow*, *Someday*, *Complete*, or *Trash*).
  2. **Today & Overdue Deadlines**: Catch overdue commitments and reschedule or complete due-soon items in one tap.
  3. **Stalled & Unreviewed Projects Audit**: Automatically flags open projects that have **zero open next-action tasks** or haven't been reviewed in **14+ days** — complete with an inline **"Add next action…"** input, **"Mark Reviewed ✓"** (`lastReviewedAt`), and **"Move to Someday"**, plus **`⚠️ Stalled`** badges in Project views.
  4. **Someday & Weekly Wrap-Up**: Activate Someday ideas, review your 7-day completion momentum, and log your completed Weekly Review.

### 2. 📋 Reusable Project Templates & Markdown / Outline Importer
- **Save Any Project as a Reusable Template**: Turn any active project (with its headings, open tasks, checklists, tags, durations, and relative day offsets) into a synced `ProjectTemplate` from the Project menu.
- **Starter GTD Gallery + Synced Custom Templates**: Includes curated GTD starter templates (*Trip & Packing*, *Product Launch*, *Client Onboarding*, *Weekly Sprint*) alongside your own cloud-synced templates across Android, ChromeOS, and macOS.
- **1-Click Markdown & JSON Outline Importer**: Paste structured Markdown (`# Project`, `## Heading`, `- [ ] Task`, `  - [ ] Subtask`) or JSON outlines to generate a complete GTD project with headings and checklists instantly.

### 3. ⏱️ Interactive Timeblocking & Daily Timeline in Today
- **Task Timeblocks (`scheduledStartMinutes` & `durationMinutes`)**: Assign a start time (`08:00`, `09:30`, custom `HH:mm`) and duration (`15m`–`2h`) to any task, with automatic Focus Timer duration matching!
- **`📋 List` ↔ `🗓️ Timeline` Toggle in Today**: Switch Today into an interactive chronological daily timeline interleaving your **Calendar Events** and **Timeblocked Tasks**, complete with overlap conflict warnings, `+15m` quick nudges, and an **Unscheduled Today** tray.

---

## 🔄 Seamless Upgrade from `v1.8.x` (Without Affecting Stable Users)

- **Stable `v1.8.1` Remains Untouched by Default**: Automatic stable update checks, `macos/install.sh` on `main`, GitHub `/releases/latest`, and `https://thingsbook-prod.web.app` remain pinned to **`v1.8.1`**.
- **In-Place Upgrade from `v1.7.x` / `v1.8.x` -> `v2.0.0-beta.1`**:
  - **Android (`versionCode 45`)**: Signed with the identical release certificate and additive Room `MIGRATION_3_4`. Install `Thingsbook-v2.0.0-beta.1-GTD-Full.apk` directly on top of `v1.8.0` or `v1.8.1` (or enable **Include 2.0 Beta Updates** in Settings -> App Update) with zero data loss.
  - **macOS (`Build 45`, Universal `arm64 + x86_64`)**: Additive SQLite schema migration preserves all existing tasks, projects, attachments, Keychain Cloud Sync sessions, and preferences. Upgrade from `v1.8.x` in one terminal command:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/install-beta.sh | bash
    ```
  - **ChromeOS / Web**: Live on the isolated Firebase Hosting `beta` preview channel (additive IndexedDB `DB_VERSION 2 -> 3` migration + full backward-and-forward Firestore sync compatibility with `v1.8.1` devices).
