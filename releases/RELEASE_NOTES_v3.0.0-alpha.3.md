# Thingsbook v3.0.0-alpha.3 (Pre-Release · Build 76)

> **3.0 Alpha Channel (`alpha-3.0`)** — Isolated pre-release preview. Stable `2.x` (`v2.1.1`) users remain on the stable channel unless pre-release updates are explicitly enabled. Full two-way `v2.x ↔ v3.0` `.tbbackup`, CSV, and Firebase Cloud Sync compatibility is preserved.

## Highlights

### 1. Decluttered Sidebar Layout (Option A) across Android, ChromeOS PWA & macOS
- **`2×2` Primary Bento Grid + `1×2` Slim Secondary Mini-Cards:** Kept the focused `2×2` Bento tiles (`Inbox`, `Today`, `Upcoming`, `Anytime`) and replaced the crowded 5-pill wrap strip beneath it with a clean **`1×2` Slim Mini-Card** row for **`Someday` (*Irgendwann*)** and **`Review` (*Rückblick*)** — aligned flush with the Bento columns above and styled with single-line labels and live item/review badges.
- **`+ New Project ▾` Footer Menu:** Relocated **`Templates & Import` (*Vorlagen & Import*)** into the bottom-left `+ New Project ▾` dropdown/popover menu so project creation and project templates live together in one logical place.
- **Quiet Bottom-Right Utility Dock:** Moved **`Logbook` (*Logbuch*)** and **`Trash` (*Papierkorb*)** (including drag-to-Trash support on ChromeOS) to compact, unobtrusive icon buttons in the sidebar footer alongside **`Areas X/8`**.

### 2. `Tags` Renamed to `Categories` (*Kategorien*) + Instant Search & 1-Click Creation
- **5-Language Terminology & Distinct Grid Iconography:** Renamed user-facing `Tag` / `Tags` labels to **`Category` / `Categories`** (`Kategorie` / `Kategorien`, `Catégorie` / `Catégories`, `Categoria` / `Categorie`, `Categoría` / `Categorías`) across English, German, French, Italian, and Spanish, paired with a clean `2×2` category grid icon (`LayoutGrid` / `square.grid.2x2` / `GridView`).
- **Instant Search + 1-Click Quick-Create When Assigning Categories:** Added a live search box to the Category Picker on Android, ChromeOS PWA, and macOS that filters categories by name or keyboard shortcut as you type — and offers a 1-click **`+ Create new category “…”`** action whenever no exact match exists.
- **Live Search Filter When Managing Categories:** Added an instant filter bar with live `filtered/total` counter and quick-clear button to the Category Manager across all 3 platforms.
- **100% `v2.x` Compatibility:** Underlying SQLite/Room/IndexedDB tables (`tags`, `task_tags`), `.tbbackup` archives (`tags`, `tagIds`), Firestore Cloud Sync collections, and CSV exports (`Tags`, plus accepting `Categories` / `Kategorien` on import) remain completely unchanged for seamless sync with `v2.1.1`.

### 3. Includes Weekly Review Interactive Navigation Fix (from `v2.1.1`)
- Clicking any stalled project, unreviewed project, or stale Someday task inside the **Weekly Review** assistant closes the modal and navigates directly to that project or expands & focuses the task in Someday across Android, ChromeOS PWA, and macOS.
