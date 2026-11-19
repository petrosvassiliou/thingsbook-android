# Thingsbook v3.0.0-alpha.7 (Pre-Release · Build 81)

> **Channel:** Pre-Release (`alpha-3.0`) — stable `2.x` users stay on `v2.1.2` unless **Receive Beta Updates** is enabled in Settings.

## Highlights

### 1. Thingsbook 3.0 Home Screen & MenuBar Widgets
- **Android Home Screen Smart View & Project Widget:** Completely redesigned with the **Thingsbook 3.0 Pure Crisp White / Obsidian Slate + Sky Light Blue (`#3898EC` / `#E5F3FF`)** Soft Bento aesthetic:
  - Features the **3.0 Glacier Liquid Glass Book** brand mark alongside crisp 3.0 vector Smart View icons (Sun for Today, Inbox tray, Calendar for Upcoming, Compass for Anytime, Cloud for Someday, and Folder for Projects).
  - Replaces cramped, truncated `10sp` text tabs with a **6-slot Soft Bento Vector Icon Switcher Dock** so zero labels ever truncate in English, German, French, Italian, or Spanish at any widget width.
  - Task rows render as rounded Soft Bento cards with circular 3.0 checkboxes and Sky Light Blue capsule metadata pills, with automatic Light & Dark mode support.
- **Android 4×1 Quick Capture Bar Widget:** Updated to the Thingsbook 3.0 Bento design with the Glacier Book icon, Warm Sun Today pill, vector Quick Find & Voice Dictation squircles, and an expressive Sky Light Blue `+ New` action button.
- **macOS MenuBar Quick Entry Widget Popover:** Redesigned with the 3.0 Glacier Book header emblem, Sky Light Blue `☀ Today` badge pill, Soft Bento Today/Project switcher pills, and rounded Bento task cards.

### 2. Fixed macOS App Icon Showing Legacy v2.x Artwork
- Switched `CFBundleIconFile` to cache-busting `AppIconGlacierV3` (preserving `com.thingsbook.macos` so Keychain, SQLite, and in-app updates work seamlessly), bundled `AppIcon-1024.png` directly into `Contents/Resources`, enforced `NSApp.applicationIconImage` at launch, and added `touch` + LaunchServices `lsregister -f` cache invalidation on install/update so Finder, Dock, `⌘Tab`, Spotlight, Sidebar, MenuBar, and Settings → About immediately display the **new 3.0 Glacier Liquid Glass Book icon**.
