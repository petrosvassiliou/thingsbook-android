# Thingsbook v2.0.0-beta.8 (Pre-Release · Build 52)

> **Beta Channel Notice**: `v2.0.0-beta.8` is a **Pre-Release** on the `2.0` beta track. Stable `v1.8.1` users on Android, macOS, and `https://thingsbook-prod.web.app` remain completely unaffected until `v2.0.0` reaches General Availability.

## Highlights in `v2.0.0-beta.8`

### macOS Top Toolbar Layout, Breathing Room & Functional Grouping
- **Full-Width Balanced Toolbar Layout**: Added a native AppKit flexible space item between the leading **Quick Find (`⌘F`)** search pill and the trailing action controls so the toolbar cleanly spans the full detail pane instead of bunching all 12 controls against the left edge.
- **Generous Icon Spacing & Hover Targets**: Replaced cramped bare toolbar buttons with adaptive `MacToolbarIconButton` and `MacToolbarGeminiButton` controls (`34×30pt` hit areas with `11pt` inter-button spacing at the default `1140pt` window width, scaling up to `37×30pt` / `15pt` on wider displays and compacting smoothly down to the `880pt` minimum window width without `NSToolbar` `>>` overflow).
- **Clear Functional Grouping**: Organized the right-hand controls into three logical groups separated by subtle `1pt × 16pt` vertical dividers:
  1. **Intelligence & Planning**: `Gemini` (`⌘G`), `Weekly Review` (`⇧⌘R`, with live attention dot), and `Project Templates & Import`.
  2. **Capture & Organization**: `Quick Entry` (`⇧⌘N`), `Instant Voice Capture` (`⌃⇧⌘V`), `Manage Areas`, and `Manage Tags`.
  3. **Workspace & Preferences**: `Notifications`, `1-Click Language Switcher`, `Cloud Sync Status`, and `Settings` (`⌘,`).

## Cross-Platform Synchronization & Compatibility
- Synchronized version **`2.0.0-beta.8` (Build `52`)** across Android, ChromeOS PWA, and macOS (`Thingsbook.dmg` Universal Binary).
- Full `1.8.1 -> 2.0.0-beta.8` database, `.tbbackup`, and Firestore cloud sync backward compatibility preserved.
