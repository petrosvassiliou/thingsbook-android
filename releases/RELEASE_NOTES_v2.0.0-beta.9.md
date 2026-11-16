# Thingsbook v2.0.0-beta.9 (Build 53) — Pre-Release

## Highlights

### 1. macOS — Restored & Unified Top Toolbar Search Box
- **Deterministic Single-Item Toolbar Layout**: Resolved an `NSToolbar` layout issue on macOS 14/15 where an unbounded `ToolbarItem { Spacer() }` caused AppKit to drop the left-hand Search Box (`ToolbarItem(placement: .navigation)`). Both the **Quick Find (`⌘F`) Search Box** on the left and the **spacious, divider-grouped action bar** on the right now live inside a single unified `HStack` with explicit finite geometry (`MacToolbarLayoutMetrics`), ensuring the Search Box is always prominently visible across all window widths (`880pt` up to fullscreen/5K).

### 2. Cross-Platform Search Filters (Android, ChromeOS PWA & macOS)
- **Interactive Quick Find Filter Strip** across Android, ChromeOS PWA, and macOS (localized in **EN, DE, FR, IT, ES**), working seamlessly both alongside text queries and for 1-click filter-only browsing:
  - **Hide completed** — Instantly hide completed and canceled To-Dos and Projects.
  - **Date filters** — Filter by **Any date**, **Today**, **Overdue**, **Next 7 days**, **This month**, or **No date**.
  - **With deadline** — Show only items with an explicit deadline.
  - **With checklist** — Show only To-Dos containing checklist items.
  - **Tag filter** — Filter Quick Find results by any `#Tag`.
  - **Type scope** — Narrow results to **All types**, **To-Dos only**, or **Projects only**.
  - **1-click Reset** — Clear all active search filters immediately (or press `Esc` progressively).

## Channel Isolation & Compatibility
- Stable **`v1.8.1`** users remain untouched unless **Receive Beta Updates** is enabled in Settings.
- Full bidirectional sync and `.tbbackup` compatibility between `v1.8.1` and `v2.0.0-beta.9`.
