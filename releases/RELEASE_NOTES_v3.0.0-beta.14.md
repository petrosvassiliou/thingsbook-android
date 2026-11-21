# Thingsbook `v3.0.0-beta.14` (Build `105`) — Pre-GA QA, Soft Bento Frame Alignment, Googlebook Parity & Localized Asana Import

> **Pre-Release Notice:** `v3.0.0-beta.14` is published on the **3.0 Beta Channel** (`isPrerelease: true`). Stable `v2.5.0` remains the official `Latest` release until final `v3.0.0` sign-off.

## Highlights Across Android, ChromeOS PWA & macOS

1. **Flush Two-Column Soft Bento Gray-Frame Alignment (`0px` Delta):**
   - Left (`Notes`, `Checklist`, `Attachments`) and right (`When · Deadline`, `Duration · Effort`, `Project / Area · Categories`) Soft Bento gray frames now align 100% flush at both top and bottom across ChromeOS PWA (`align-items: stretch`), macOS (`AlignedBentoTwoColumnLayout`), and Android (`BentoAlignedTwoColumnRow`).
2. **Googlebook / Android Top-Bar & Task-Click Parity:**
   - **True Top-Right Toolbar Icons:** Lifted `TopThingsbookBar` onto the full-width workspace header so `🎙️ Voice`, `✨ Gemini`, `☁️ Cloud Sync`, and `⚙️ Settings` anchor cleanly at the top-right window edge on wide Googlebooks, Chromebooks, and tablets.
   - **Unified `0 ms` Click Selection:** Single-clicking a collapsed task row or Kanban card on Android now selects the task immediately (`0 ms`), while double-clicking (`<= 380ms`), pressing `Enter`/`Space`, or tapping the trailing **Open Details (`⤢`)** button opens the Soft Bento modal—matching ChromeOS PWA and macOS 1:1.
3. **Localized Asana & Microsoft Planner Import/Export (`EN`, `DE`, `FR`, `IT`, `ES`):**
   - Added localized Asana CSV column header & ungrouped-section aliases (`Aufgaben-ID`, `Abschnitt/Spalte`, `Fälligkeitsdatum`, `Startdatum`, `Notizen`, `Projekte`, `Übergeordnete Aufgabe`, `Abgeschlossen am`, plus FR/IT/ES equivalents) across all 3 platforms so CSVs exported directly from non-English Asana workspaces import sections, dates, tags, notes, and subtasks seamlessly alongside Asana JSON and Microsoft Planner `.xlsx`/`.csv`.
4. **Full-Codebase QA & Multi-Day Duration Persistence (`1m..48,000m`):**
   - Raised legacy `[5, 720]` / `[1, 1440]` duration caps to `48,000m` (`100` workdays) across SQLite, IndexedDB, Room, Firestore DTOs, `.tbbackup`, and Templates; preserved standalone durations when clearing Timeblocks; fixed macOS right-click `"Blocked by…"` and `"Duration → Custom…"` popover anchors; added `"Copy To-Do"` Markdown copy; wired `Ctrl+1..7` & `Ctrl+Shift+R` on Android; and verified all 5 locales via headless Chromium E2E smoke tests.
