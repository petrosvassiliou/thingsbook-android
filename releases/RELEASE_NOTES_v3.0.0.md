# Thingsbook v3.0.0 (Build 111) — Official Final Release

**Thingsbook 3.0 (`v3.0.0`, Build `111`)** is now officially available in General Availability for **Android & Googlebook OS**, **ChromeOS (Standalone PWA)**, and **macOS (Universal Apple Silicon + Intel)**. This release delivers the complete `3.0` project planning, Soft Bento workspace, and cross-platform GTD suite with **100% seamless, zero-data-loss in-place upgrades** from `v1.8.x`, `v2.x`, and `v3.0.0-beta.x` (automatic Room `v7` / SQLite / IndexedDB schema migrations).

---

## ✨ Highlights in Thingsbook 3.0

### 1. 📊 Three-Mode Project Workspaces: List, Kanban Board & Interactive Gantt Timeline
- **Project List, Board & Timeline Modes (`📋 List` · `🗂️ Board` · `📊 Timeline`)**: Switch seamlessly inside any project across Android, ChromeOS, and macOS.
- **Kanban Board by Project Phases**: Drag and drop tasks across Phases, track per-phase completion rings, set Phase Start & End dates, and promote or clear ungrouped (`General`) tasks in one click.
- **Interactive Gantt Timeline / Roadmap**:
  - Multi-day task bars, Phase span bars, and **45° Royal-Violet Milestone Diamonds (`◆`)** with non-clipped floating callout pills.
  - **Press-and-Drag Rescheduling**: Hold and drag tasks, milestones, or entire phases horizontally to shift schedules cleanly without accidental hover drift.
  - **Mouse-Only Hover Tooltips**: Hovering with a mouse or trackpad cursor over tasks in Timeline view surfaces a discreet floating summary (title, dates, deadline, duration, effort, checklist progress, and blocked state) while staying completely silent on touchscreens.
  - **Full-Screen Presentation Mode & Vector PDF / High-DPI PNG Export**: Present roadmaps distraction-free (`F` / `⌃⌘F`) or export crisp multi-page **A4/Letter Vector PDFs** and **High-DPI PNGs** with customizable date ranges and phase filters.

### 2. 🍱 Redesigned Two-Column Soft Bento Task Modal & Wide-Screen Inspector
- **Soft Bento Task Editor Modal**: Symmetric two-column desktop/tablet layout pairing **Markdown Notes + Interactive Checklists** (with `✨` AI Magic Breakdown) on the left with **Schedule (`When`, `Deadline`, `Milestone ◆`, `Repeat`, `Timeblock`), Effort (`Low` / `Medium` / `High`), Duration (`5m`–`8h` + multi-day), Dependencies (`Waiting On`), Organization (`Area / Project / Phase`), Tags, and File Attachments** on the right.
- **Wide-Screen Right-Side Inspector / Context Panel**: Maximizing Thingsbook on Googlebook OS, ChromeOS, or macOS automatically unlocks a live Soft Bento Inspector showing selected task details & quick actions—or a live Today Focus / Project Progress / Smart View overview when no task is selected.

### 3. 🔄 Asana & Microsoft Planner Import/Export + Task Dependencies & Pinned Tasks
- **Round-Trip Asana JSON & Microsoft Planner / Todoist CSV Import & Export**: Import projects with Phases, Milestones (`resource_subtype: "milestone"`), Checklists, Tags, Notes, and Deadlines directly into a **pre-selected Target Area**, or export any project for external collaboration.
- **Task Dependencies (`Waiting On` / `Blocks`)**: Link prerequisite tasks with circular-dependency prevention, automatic `⏳` blocked indicators, and curved SVG dependency connectors on the Project Timeline.
- **Pinned Tasks, Effort Scale & Full-Day Durations**: Pin priority items to the top of views, classify cognitive/work Effort (`Low` / `Medium` / `High`), and estimate durations from `5m` up to `4h` (half-day), `8h` (full-day), and multi-day spans.

### 4. 🧠 Optional Custom Perspectives, BYOK Gemini AI Hub & Zero-Lock-In Sync
- **Clean Sidebar by Default + Optional Custom Perspectives**: Keep the classic Things-inspired sidebar clean out of the box, or enable saved multi-rule **Custom Perspectives** anytime in **Settings**.
- **Optional BYOK Google Gemini AI Hub**: Daily Briefing, Smart Triage, Natural-Language Task & Project Creation, and Checklist Magic Breakdown—powered strictly by your own API key when enabled.
- **Offline-First Storage, Real-Time Cloud Sync & Encrypted `.tbbackup`**: Instant local SQLite/Room/IndexedDB speed, optional Google Sign-In + Firestore delta sync across all devices, AES-256-GCM encrypted `.tbbackup` archives, and **100% 5-language parity (`EN`, `DE`, `FR`, `IT`, `ES` — 650 localized strings)**.

---

## 📥 Download & Upgrade (`v3.0.0`, Build `111`)

- **📱 Android & Googlebook OS (`versionCode 111`, Universal Signed APK)**:
  - Download **`Thingsbook-v3.0.0-GTD-Full.apk`** below (or use **Settings → App Update → Check for Updates** inside the app) for an instant in-place upgrade.
- **💻 ChromeOS & Web (Standalone Offline-Capable PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** — installed Chromebook apps update automatically in place.
- **🍎 macOS (`Build 111`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade directly inside Thingsbook (**Settings → Updates → Install & Relaunch**), download **`Thingsbook-macOS-v3.0.0-Universal.dmg`** below, or run the 1-command terminal installer:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
