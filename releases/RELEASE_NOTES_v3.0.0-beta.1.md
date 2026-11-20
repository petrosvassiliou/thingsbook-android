# Thingsbook v3.0.0-beta.1 (Beta Pre-Release · Build 92)

## Highlights

### 1. Three-Mode Project Workspace: `List | Board | Roadmap (Gantt)`
- **Board View (Soft Bento Kanban):** Side-by-side Phase columns (`General` + Project Phases + `+ New Phase`) with full untruncated Phase titles, progress rings, Phase Start/End date pills, rich Soft Bento task cards, cross-column multi-task drag-and-drop, and `+ New To-Do` per Phase.
- **Roadmap View (Calm Bento Gantt):**
  - **Glacier-Blue Phase Bars (`startDate` → `endDate`):** Full untruncated Phase titles across the pinned label column and timeline bars, capped cleanly at **up to 1 calendar month after the latest task due/end date**.
  - **Task Spans & Dependency Connectors:** Smooth cubic-Bézier dependency connector curves for `Waiting On` (`blockedByTaskIds`), horizontal drag-to-reschedule + `-1/+1 Day` controls, and an interactive `Unscheduled` tray.
  - **Interactive PDF Export Dialog & 1-Click PNG Export:** Choose specific **Project Phases**, **Timeline Range** (`Full Project`, `From Today`, `Next 30 Days`, or `Custom Range` with non-truncating flow pills), and **Page Orientation** (**Landscape** `842 × 595 pt` or **Portrait** `595 × 842 pt`) for multi-page A4 vector PDF export.

### 2. Full Cross-Container Drag & Drop (Single & Multi-Select)
- Drag single or multi-selected tasks across **macOS**, **ChromeOS PWA**, and **Android tablet/split-screen** onto **Sidebar Smart Views** (`Inbox`, `Today`, `Planned`, `Anytime`, `Someday`, `Logbook`, `Trash`), **Areas**, **Projects**, and **Project Phases**, preserving visual selection order with 5-second Undo.

### 3. Standalone Duration, Effort Levels, Dependencies & Smart Perspectives
- **Standalone Duration & Effort Badges** (`Quick Win ⚡`, `Medium`, `Deep Focus 🧠`) + **Today Capacity Summary**.
- **Task Dependencies (`Waiting On…`)** with DAG cycle prevention and automatic unlock upon blocker completion.
- **Custom Perspectives** pinned to the Soft Bento Sidebar, plus all `v2.5.0` Categories Power Suite, Pin to Top, View Sorting, and Duplication features.
