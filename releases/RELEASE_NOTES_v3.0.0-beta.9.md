# Thingsbook v3.0.0-beta.9 (Build 100) — Fixed-Size Section Frames & Zero-Jank Task Hover

> **Pre-Release Notice:** `v3.0.0-beta.9` (Build `100`) is a **3.0 Beta pre-release** for **Android**, **ChromeOS PWA**, and **macOS 14+**. Official Stable `Latest` (`v2.5.0` on `main`) remains untouched for stable-channel users.

## What's New in Beta 9

### Fixed-Size Section Frames When Hovering Over Tasks
- **Zero Height or Width Resize on Hover (`macOS`):** Hovering over task rows or Kanban Board cards no longer conditionally mounts/unmounts trailing quick-action buttons (`Quick Edit`, `When`, `Open Details`). Instead, the trailing quick-action cluster stays permanently in the layout and toggles purely via `opacity` and `allowsHitTesting`, with a fixed `minHeight: 22` row baseline and width-locked section containers (`groupedByContainerStack`, `projectUnheadedSection`, `projectHeadingSection`, `BoardTaskCard`).
- **Clean Vertical Badge Alignment:** Trailing badges (such as `Deadline` and `Timeblock` pills) stay completely stationary when moving the cursor across tasks, and outer section frames never expand or contract horizontally or vertically.
