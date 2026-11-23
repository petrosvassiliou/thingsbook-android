# Thingsbook v3.1.0 (Build 113) — Area Portfolio Roadmaps, Smart Dependency Cascading, 7-Day Workload & AI Day Planning

**Thingsbook `v3.1.0` (Build `113`)** brings multi-project **Area Portfolio Timelines** (with Vector PDF & High-DPI PNG export), **Smart Push-on-Conflict Dependency Cascading**, **Configurable Workday Hours + 7-Day Workload Forecasts**, and **v3-Aware Gemini Roadmap Architect + "Plan My Day" Auto-Timeblocking** in 100% parity across **Android & Googlebook OS**, **ChromeOS (Standalone PWA)**, and **macOS (Universal Apple Silicon + Intel)**.

---

## ✨ What’s New in `v3.1.0`

### 1. 📊 Area Portfolio Timeline with Collapsible Project Lanes & Vector PDF / PNG Export
- **`List` · `Portfolio Timeline` Switcher on Areas:** Switch any Area directly into an executive multi-project Gantt timeline (`F` / `⌃⌘F` Full-Screen Presentation Mode supported).
- **Collapsible Project Lanes with `◆` Milestones:**
  - **Collapsed by Default:** View each active project's summary date span, progress ring, `completed/total` count, quick `↗` open-project button, and **overlaid 45° Royal-Violet Milestone Diamonds (`◆`)** across all projects in the Area at a glance.
  - **Expand Anytime:** Expand individual projects (or use **Expand All / Collapse All**) to inspect and press-and-drag Phase and Task bars directly inside the Area timeline.
- **Multi-Page Vector PDF & High-DPI PNG Export:** Export any Area Portfolio Timeline as a multi-page **A4 Vector PDF** (choose which projects to include, date window, and Landscape/Portrait orientation) or a **High-DPI Retina PNG**.

### 2. 🔗 Smart Push-on-Conflict Auto-Cascading Dependencies + Full Gantt Undo
- **Conflict-Only Push Engine:** Dragging a prerequisite task forward on any Project or Area Gantt Timeline automatically shifts open downstream `Waiting On` (`blockedByTaskIds`) tasks forward **only when the blocker overlaps or passes their start date**—preserving existing slack when non-overlapping and never pulling downstream tasks backward.
- **Real-Time Cascade Preview & `Cmd+Z` / `Ctrl+Z` Undo:** Downstream task bars and Cubic-Bézier dependency curves preview cascaded day deltas live while dragging, with atomic multi-task Undo across Android, ChromeOS, and macOS.

### 3. ⚖️ Configurable Workday Hours & 7-Day Workload Forecast
- **Custom Workday Window in Settings:** Configure **Workday Start** and **Workday End** (default `09:00–17:00`, synced in `.tbbackup` archives) to match your real schedule.
- **7-Day Workload Strip & Overload Pills:** View a 7-day workload bar strip at the top of **Upcoming** and inside the **Wide-Screen Right-Side Inspector**, plus per-day capacity and overload indicators (`+Xh over` vs. `planned · free`) factoring in non-all-day calendar meetings.

### 4. 🧠 `v3`-Aware Gemini Roadmap Architect & "Plan My Day" Auto-Timeblocker
- **Gemini Roadmap Architect (`v3` Gantt Ready):** Generates complete projects with **dated Phases, `◆` Milestones, Effort (`Low` / `Medium` / `High`), Durations (`30m`–`8h`), Checklists, and `Waiting On` dependencies**, opening directly in **Timeline** view (with 5-language offline blueprint fallback).
- **"Plan My Day" Smart Auto-Timeblocker:** Accessible from **`✨ Plan My Day`** in the **Today** header or the Gemini AI Hub—proposes conflict-free 15-minute focus blocks around your calendar events and workday hours, with interactive preview checkboxes, `-15m` / `+15m` nudges, **`Apply to Today`**, and 1-click Undo.
- **5-Language Parity (`675` Keys):** Full `EN`, `DE`, `FR`, `IT`, and `ES` localization across all new `v3.1.0` controls and offline AI schedulers.

---

## 📥 Download & Upgrade (`v3.1.0`, Build `113`)

- **📱 Android & Googlebook OS (`versionCode 113`, Universal Signed APK)**:
  - Download **`Thingsbook-v3.1.0-GTD-Full.apk`** below or use **Settings → App Update → Check for Updates** inside the app for a zero-data-loss in-place upgrade.
- **💻 ChromeOS & Web (Standalone Offline-Capable PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** — installed Chromebook apps update automatically in place.
- **🍎 macOS (`Build 113`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade directly inside Thingsbook (**Settings → Updates → Install & Relaunch**), download **`Thingsbook-macOS-v3.1.0-Universal.dmg`** below, or run:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
