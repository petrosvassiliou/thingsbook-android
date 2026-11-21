# Thingsbook `v3.0.0-beta.15` (Build `106`) — Task Milestones, Asana Target Area Preselection & Single-Task Selection Parity

> **Pre-Release Notice:** `v3.0.0-beta.15` is published on the **3.0 Beta Channel** (`isPrerelease: true`). Stable `v2.5.0` remains the official `Latest` release until final `v3.0.0` sign-off.

## Highlights Across Android, ChromeOS PWA & macOS

1. **Task Milestones (`◆` / `◇`) Across All Views — Especially Timeline Views:**
   - **Project Gantt Roadmap Timeline & Vector PDF/PNG Exports:** Milestones anchor as crisp 1-day point-in-time checkpoints rendered with a **45° rotated Royal-Violet Diamond (`◆`)** + floating non-clipped callout pill (`◆ Title`), dependency connectors, drag-to-shift, and vector diamonds in exported PDF & PNG roadmaps.
   - **Today Daily Timeline & Upcoming:** Milestone tasks render with a violet accent border, rotated **Diamond Checkbox (`◇` → `◆ ✓`)**, and `◆ Milestone` (`◆ Meilenstein`) badge.
   - **1-Click Milestone Toggle & Full Sync/Asana Parity:** Toggle any task as a Milestone from the Soft Bento modal header (diamond button next to Pin), the `When · Deadline` card (`◇ Milestone` chip), or right-click/context menus. Persisted across SQLite, IndexedDB, Room v7 (`MIGRATION_6_7`), Firestore Cloud Sync, `.tbbackup`, Project Templates, `@milestone` Markdown Outlines, and automatic **Asana JSON `resource_subtype: "milestone"`** import/export.
2. **Target Area Preselection for Asana & Microsoft Planner Import:**
   - Added a prominent **`Target Area for Import` (`Ziel-Bereich für Import`)** selector directly inside the Asana & Planner Import Soft Bento card across all 3 platforms—automatically pre-populated with the currently open Area or active Project's Area—plus a 1-click `"Asana & Planner"` shortcut on Area headers and Sidebar Area context menus.
3. **1:1 Single-Task Click Selection Across macOS, Googlebook OS / Android & ChromeOS PWA:**
   - Plain-clicking a task selects only that single task without triggering sticky multi-select mode or popping up the bottom multi-selection bar; clicking a second task cleanly switches selection to that task, while `Ctrl`/`Cmd`-Click, `Shift`-Click, `Ctrl+A`, Long-Press, or Context Menu `Select` enters multi-selection.
