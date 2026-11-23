# Thingsbook v3.2.0 (Build 116) — Unified Ctrl+K / ⌘K Command Palette, Smart-Syntax Capture & Zen Focus Mode

**Thingsbook `v3.2.0` (Build `116`)** brings a **Unified `Ctrl+K` / `⌘K` Command Palette**, **Multilingual Inline Smart-Syntax Capture**, and **Full-Window Zen Focus Mode with `actualMinutes` Tracking** in 100% parity across **Android & Googlebook OS**, **ChromeOS / Web PWA**, and **macOS** across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`).

---

## ✨ What’s New in `v3.2.0`

### 1. ⚡ Unified `Ctrl+K` / `⌘K` Command Palette & Action Runner
- **Search + Actions in One Place:** Press **`Ctrl+K` / `⌘K`** (or `Ctrl+F` / `⌘F`) to jump to any Smart View, Area, Project, or To-Do—or execute contextual **Quick Actions** directly from the keyboard:
  - *Plan My Day (Auto-Timeblock)* · *Open Gemini AI Hub & Roadmap Architect* · *Start Weekly Review* · *Complete Current Project* · *Switch View (List / Board / Timeline)* · *Open Zen Focus Mode* · *Open Templates & Import/Export* · *Cycle Theme* · *Open Settings*
- **1-Keystroke To-Do Creation:** Typing any non-command query displays a live **`+ Create To-Do: “…”`** card with resolved Smart-Syntax badges so you can capture and schedule tasks without leaving the keyboard. *(On macOS, Complete Selected To-Do is cleanly mapped to `⌘.` so `⌘K` opens the Command Palette everywhere.)*

### 2. ✨ Multilingual Inline Smart-Syntax Capture (`EN`, `DE`, `FR`, `IT`, `ES`)
- **Expressive Single-Line Tokens:** Capture rich GTD attributes inline inside the Command Palette, Quick Entry / Quick Share, Inline Quick-Edit, and Expanded Task Modal (with a live non-destructive preview bar + **`Apply`** button):
  - **`@when`**: `@today`, `@this-evening`, `@tomorrow`, `@someday`, `@morgen`, `@demain`, `@domani`, `@mañana`, `@2026-12-01`
  - **`!deadline`**: `!fri`, `!fr`, `!ven`, `!vie`, `!2026-12-15`, `!30.11.2026`
  - **`~duration`**: `~15m`, `~45m`, `~1.5h`, `~2h`
  - **`#category`** & **`/Project` or `/Area`**: `#deep-work`, `/NexusTS`, `/"App Development"`
  - **`!milestone` / `◆`**: marks the to-do as a Milestone (`◆`)
- **Zero Prose Corruption:** Strictly preserves URLs (`https://...`), email addresses, ratios (`3/4`), and unmatched text.

### 3. 🧘 Zen Focus Mode & Actual-vs-Estimated Focus Time (`actualMinutes`)
- **Calm Full-Window Zen Focus Overlay:** Expand any active Focus Timer in 1 click into a distraction-free **Zen Focus** card featuring a large circular countdown, **`5m Break` / `Back to Focus`** toggle, task notes, and a **live interactive Checklist** (check off or `+` add subtasks right inside Zen Focus). Press `Esc` or **Minimize** anytime to collapse back to the banner without interrupting the timer.
- **Synced & Backed-Up `actualMinutes` Tracking:**
  - Automatically accumulates focused minutes (`actualMinutes`) across sessions—persisted in Room v8, IndexedDB, macOS SQLite, `.tbbackup` v1 archives, and Firestore/Ntfy real-time sync (preserving tracked time across legacy peers and resetting cleanly on recurring spawns and duplicates).
  - Shows **`Focused Xm / Ym`** (or **`Focused Xm`**) on task rows, modals, and the inspector (with 1-click reset), plus a **`Focused (7d): Xh Ym`** summary pill in **Logbook**.

---

## 📥 Download & Upgrade (`v3.2.0`, Build `116`)

- **📱 Android & Googlebook OS (`versionCode 116`, Universal Signed APK)**:
  - Download **`Thingsbook-v3.2.0-GTD-Full.apk`** below or use **Settings → App Update → Check for Updates** inside the app.
- **💻 ChromeOS & Web (Standalone Offline PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** — updates automatically in place.
- **🍎 macOS (`Build 116`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade via **Settings → Updates → Install & Relaunch**, download **`Thingsbook-macOS-v3.2.0-Universal.dmg`** below, or run:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
