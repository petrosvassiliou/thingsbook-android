# Thingsbook v3.1.1 (Build 114) — Intuitive Project Completion UX Across Android, ChromeOS & macOS

**Thingsbook `v3.1.1` (Build `114`)** brings **1-click Project Completion**, a visible **Soft Bento `✓ Complete` Header Action**, a **Smart Open-Task Guard**, and **5-Second Atomic Undo (`Ctrl+Z` / `⌘Z`)** across **Android & Googlebook OS**, **ChromeOS / Web PWA**, and **macOS** in all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`).

---

## ✨ What’s New in `v3.1.1`

### 1. Visible `✓ Complete` Header Button + Interactive Progress Badge
- **Interactive Left Progress / `[✓]` Badge:** Clicking the leading progress capsule/ring next to any project title now completes the project directly (with hand-cursor hover and localized `"Complete project"` / `"Projekt abschließen"` tooltip).
- **Top-Right Soft Bento `✓ Complete` Button:** Added a dedicated **`✓ Complete` (`✓ Abschließen`)** pill button right next to `✏️` Edit and `⋯` More on Googlebook/tablets, ChromeOS PWA, and macOS (and a compact `CheckCircleOutline` icon button on narrow `< 600dp` phones so titles never get crowded).
- **Emerald Ready-to-Complete Highlight:** Automatically highlights in **Logbook emerald green** as soon as `100%` of the project's tasks are done.

### 2. Smart Open-Task Guard & 5-Second Atomic Undo (`Ctrl+Z` / `⌘Z`)
- **Zero Open Tasks (`100%` Done or Empty Project):** Completes immediately in 1 click, transitions smoothly to **Logbook**, and shows a **5-second Undo toast** (`Ctrl+Z` / `⌘Z`) that restores the project to `ACTIVE` and navigates right back to it.
- **Remaining Open Tasks:** Displays a Soft Bento confirmation dialog with three clear options:
  - **Complete All & Finish** — marks remaining open tasks `COMPLETED` and finishes the project (with atomic Undo restoring both the project and those tasks).
  - **Complete Project Only** — finishes the project while keeping open tasks untouched (with Undo).
  - **Cancel** — keeps the project open.

---

## 📥 Download & Upgrade (`v3.1.1`, Build `114`)

- **📱 Android & Googlebook OS (`versionCode 114`, Universal Signed APK)**:
  - Download **`Thingsbook-v3.1.1-GTD-Full.apk`** below or use **Settings → App Update → Check for Updates** inside the app.
- **💻 ChromeOS & Web (Standalone Offline PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** — updates automatically in place.
- **🍎 macOS (`Build 114`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade via **Settings → Updates → Install & Relaunch**, download **`Thingsbook-macOS-v3.1.1-Universal.dmg`** below, or run:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
