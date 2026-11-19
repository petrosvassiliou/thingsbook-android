# Thingsbook v3.0.0-alpha.8 (Pre-Release · Build 82)

> **Channel:** Pre-Release (`alpha-3.0`) — stable `2.x` users stay on `v2.1.2` unless **Receive Beta Updates** is enabled in Settings.

## Highlights

### Fixed-Size Category Assignment & Management Dialogs
- **Zero Window Resizing When Searching Categories:** Locked the scrollable category results viewports in **Assign Categories** (*Kategorien zuweisen*) and **Manage Categories** (*Kategorien verwalten*) across **Android**, **ChromeOS PWA**, and **macOS** to a constant viewport height so filtering from many results down to 2, 1, or 0 results (or creating/deleting categories) updates only the list contents inside the dialog — never shrinking, growing, or shifting the dialog/popover window on screen.
