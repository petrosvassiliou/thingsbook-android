# Thingsbook v3.0.0-beta.10 (Build 101) — Tactile Zero-Shift Sidebar Hover Highlights

> **Pre-Release Notice:** `v3.0.0-beta.10` (Build `101`) is a **3.0 Beta pre-release** for **Android**, **ChromeOS PWA**, and **macOS 14+**. Official Stable `Latest` (`v2.5.0` on `main`) remains untouched for stable-channel users.

## What's New in Beta 10

### Calm, Zero-Shift Sidebar Hover Highlights Across All Platforms
- **macOS (`SidebarView.swift`):** Added smooth, zero-shift `.onHover` visual highlights (elevated card tint + subtle sky-blue border glow) to the 2×2 Smart View Bento tiles (`Inbox`, `Today`, `Upcoming`, `Anytime`), `Someday` & `Review` mini-cards, `+ New Project ▾` menu button, Perspective / Area / Project rows, and bottom utility dock buttons (`Logbook`, `Trash`, `Areas`, `Categories`).
- **ChromeOS PWA (`index.css`):** Refined `.bento-tile:hover` and `.bento-mini-card:hover` so white Bento tiles softly illuminate with a sky-blue border glow and subtle elevation instead of dimming to gray, with zero size or border-width shift.
- **Android Chromebooks & Trackpads (`ThingsSmartViewScreen.kt`):** Added pointer `hoverable` highlights on the Smart View Bento tiles, `Someday` & `Review` mini-cards, and `+ New Project ▾` footer button with fixed border widths and zero layout movement.
