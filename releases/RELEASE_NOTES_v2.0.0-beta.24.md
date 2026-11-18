# Thingsbook v2.0.0-beta.24 (Build 68) — Pre-Release

> **Pre-Release (`beta-2.0` channel)** — Stable `v1.8.1` users on `main` remain completely unaffected unless explicitly opted into the Beta update channel.

## Highlights & Fixes

### Accurate "Sample Tasks Still Present" Detection & Cloud-Safe Sample Cleanup (macOS, ChromeOS PWA & Android)
- **Open Unmodified Demo Tasks Only**: Fixed `hasSampleTasks` across macOS, ChromeOS PWA, and Android so that completed or canceled Logbook entries (such as the pre-seeded completed Logbook item `task-completed-008`) no longer trigger a false *"Sample tasks still present"* / *"Beispielaufgaben noch vorhanden"* notice in Settings when no open example tasks remain.
- **Preserves User-Renamed Tasks**: Sample task detection and cleanup now verify localized preinstalled demo titles (`EN`, `DE`, `FR`, `IT`, `ES`). Any seeded task whose title was edited into a personal task is never flagged as a demo task or removed during cleanup.
- **Prevents Cloud Sync Resurrection**: Deleting sample tasks while signed into Cloud Sync now emits purged tombstones (`purgedFromTrash = true`, `isDeleted = true`) without cluttering Trash, ensuring previously synced demo tasks do not reappear after full cloud reconciliation.
