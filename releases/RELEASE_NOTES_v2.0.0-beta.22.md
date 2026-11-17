# Thingsbook `v2.0.0-beta.22` (Build 66) — Cross-Platform Instant & Zero-Duplicate Sync Overhaul (`v1.8` ↔ `v2.x`)

> **Beta Pre-Release Channel**: Stable `v1.8.1` users remain completely isolated unless opting into **Include 2.0 Beta Updates**.

## Highlights

### 1. Instant (`<200 ms`), Robust Pull-Before-Push Sync Engine (`v1.8` ↔ `v2.x` & `v2.x` ↔ `v2.x`)
- **Strict Pull-Before-Push Ordering**: Every sync entrypoint across **Android**, **macOS**, and **ChromeOS PWA** pulls remote deltas first, reconciles stale offline edits (`>30 s`) so genuine offline edits win deterministically without clobbering fresher peer changes, and pushes dirty local entities last.
- **Per-Collection Cursors & Automatic `reconcile_v2_22` Self-Heal**: Replaces shared global sync watermarks with isolated per-collection cursors (`tags`, `areas`, `projects`, `headings`, `tasks`, `checklist_items`, `attachments`, `project_templates`) plus a one-time `since = 0` full reconciliation pass and automatic orphan-container (`projectId` / `areaId` / `headingId`) self-healing so projects created on `v1.8` or macOS never go missing on ChromeOS or Android.
- **Dual-Channel `ntfy` Instant Wake & Legacy `v1.8` Compatibility**: Subscribes simultaneously to `tb_uid_*` (`v2.x` instant wake) and legacy `tb_sync_*` (`v1.8` OAuth/email channels), never emits `wakeOnly: true` onto legacy `tb_sync_*` channels, splits oversized delta payloads cleanly below `3.8 KB`, and never advances Firestore pull cursors from partial `ntfy` or single-collection listener frames.

### 2. Zero-Duplicate Recurring Tasks, Tags & Areas
- **Recurring Series Area-Split Deduplication & Winner Enrichment**: Eliminates duplicate open recurring tasks (such as daily tasks split between `areaId == null` and a named Area like `Privat` across `v1.8` and `v2.x`), enriching the winning instance with any missing `areaId`, `headingId`, tags, notes, or timeblock metadata while silently purging duplicate losers without polluting Trash.
- **`v1.8` Startup Rollover Resurrection Guard & Timeblock Preservation**: Prevents `v1.8` midnight rollover from resurrecting already-completed recurring parent tasks when an `OPEN` next occurrence already exists, and preserves `v2.x` `scheduledStartMinutes` / `durationMinutes` timeblocks when `v1.8` peers edit a task without timeblock fields.
- **Deterministic Same-Title Tag & Area Reconciliation**: Resolves concurrent same-title Tags across devices using deterministic `(createdAt, id)` ordering, safely remapping `tagIds` across Tasks, Projects, Areas, and Project Templates while respecting SQLite unique constraints, and reuses case-insensitive active Areas before enforcing `MAX_AREAS`.
- **Cross-Platform Trash vs. Permanent Purge Parity**: Adds explicit `purgedFromTrash` tombstone metadata on `tasks` and `projects` alongside `updateMask` field paths so permanently purged items never reappear as blank rows in Trash on `v1.8` or `v2.x` peers.

### 3. ChromeOS PWA Channel Alignment & Magic Plus (`+`) Menu
- **Unified Magic Plus (`+`) Menu**: Strictly offers **New To-Do**, **New Heading** (inside Projects), and **New Project** across Android, macOS, and ChromeOS PWA, plus packaged `Thingsbook-ChromeOS-PWA-v2.0.0-beta.22.zip` release bundle and Firebase Hosting `beta` channel workflow support (`FIREBASE_SERVICE_ACCOUNT_THINGSBOOK_PROD` or `FIREBASE_TOKEN`).
