# Thingsbook v3.0.0-alpha.12 (Pre-Release · Build 89)

## Highlights

### 1. Full Cross-Container Drag & Drop (Single & Multi-Select)
- **Drag One or Many To-Dos Anywhere:** Select multiple to-dos (or grab any individual task) and drag them directly onto **Sidebar Smart Views** (`Inbox`, `Today`, `Planned`, `Anytime`, `Someday`, `Logbook`, `Trash`), **Areas**, **Projects**, and between **Project Phases** across **macOS**, **ChromeOS PWA**, and **Android tablet/split-screen** — with full 5-second Undo support and preserved selection order.
- **Collapsed-Sidebar & Section Drops:** Also supports dropping onto the horizontal Smart View navigation strip, Project `General` (ungrouped) sections, `Today · This Evening`, and `Planned` date groups.

### 2. Three-Mode Project Workspace: `List | Board | Roadmap (Gantt)`
- **Adaptive Full-Width Project Modes:** Switch any project seamlessly between **List**, **Board**, and **Roadmap** (`Project.viewMode` synced across Android, ChromeOS PWA, macOS, and `.tbbackup`).
- **Board View (Soft Bento Kanban):** Side-by-side Phase columns (`General` + Project Phases + `+ New Phase`), draggable Soft Bento task cards with Effort/Duration/Category/Dependency badges, cross-column multi-task drag-and-drop, and quick `+ New To-Do` per Phase.
- **Roadmap View (Calm Bento Gantt):**
  - **Glacier-Blue Phase Header Bars (`startDate` → `endDate`):** Set Phase Start and End dates across List, Board, and Roadmap views and visualize project phases along a clean horizontal timeline.
  - **Task Spans & Dependency Connectors:** Plots task schedules (`When`, `Duration`, and `Deadline`) with smooth cubic-Bézier dependency connector lines for `Waiting On` (`blockedByTaskIds`) relationships.
  - **Horizontal Drag-to-Reschedule & Step Controls:** Drag Phase or Task bars horizontally across day columns (or tap `-1 Day` / `+1 Day`) to shift schedules safely across calendar days, plus an **Unscheduled** tray for 1-click plotting.
  - **1-Click Export Roadmap (`PDF` & `PNG`):** Export the full unclipped project Roadmap as a crisp vector **PDF** or high-DPI **PNG** across Android, ChromeOS PWA, and macOS.
