# Thingsbook v3.0.0-alpha.11 (Pre-Release · Build 88)

## Highlights

### 1. Full Forward-Port of Thingsbook `v2.5.0` into `3.0`
- **Categories Power Suite:** Live search with constant-height viewports, **Category Sorting** (`A–Z`, `Most Used` with live usage counts, `Custom` manual reorder), and **Atomic Category Merge (`Merge Into…`)** across Tasks, Projects, Areas, and Project Templates.
- **Pin Priority Tasks to Top (`isPinned`):** Pin must-do tasks to the top of `Today`, `Anytime`, `Projects`, and `Perspectives` across Android, ChromeOS PWA, and macOS.
- **View & Project Sorting + Multi-Select Upgrades:** Header **Sort** menu (`Manual`, `By Deadline`, `By Duration`, `By Effort`, `Alphabetical A–Z`), `Ctrl/Cmd+D` multi-task & project duplication, Android touch long-press multi-select, and enriched batch actions.

### 2. Standalone Duration, Effort Levels & Today Capacity Summary
- **Standalone Estimated Duration:** Set task duration (`5m`, `15m`, `30m`, `1h`, `2h`, `4h`) on any task without requiring a specific clock start time.
- **Effort / Energy Badges:** Classify tasks as **Quick Win ⚡**, **Medium**, or **Deep Focus 🧠**.
- **Today Capacity Pill:** Soft Bento Glacier-Blue indicator in `Today` comparing planned task duration against free workday hours between calendar appointments.

### 3. Task Dependencies — "Waiting On" (`blockedByTaskIds`)
- Mark tasks as **Waiting On…** one or more open tasks with built-in DAG cycle prevention (impossible to create circular dependencies).
- Actively blocked tasks show a calm *"Waiting on…"* badge, dim gently in `Anytime` and `Project` lists, and automatically unlock the moment their blockers complete.

### 4. Saved Smart Filters / Custom Perspectives
- Create, edit, and pin **Custom Perspectives** directly to the Soft Bento Sidebar combining **Categories**, **Effort**, **Max Duration** (`≤ 15m`, `≤ 30m`, `≤ 60m`), **Due Window**, **Area**, and **Actionable Only (Hide Blocked)** — backed up and restored seamlessly in `.tbbackup`.
