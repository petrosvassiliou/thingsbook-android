# Thingsbook v3.0.0-beta.2 (Beta Pre-Release · Build 93)

## Highlights — Milestone 4: Asana & Microsoft Planner Import / Export + Final 3.0 QA

### 1. Asana CSV & JSON Import + Export (100% Offline, Zero OAuth)
- **Seamless Project Migration (`Templates & Import -> Asana & Planner`):**
  - **Import from Asana (`.csv` & `.json`):** Maps Asana `Projects` to Thingsbook `Projects`, `Sections/Columns` to `Phases` (`Headings`), `Subtasks` (`Parent task` / `subtasks[]`) to `Checklists` (preserving completion state), `Start Date` / `Due Date` (`start_on` / `due_on`) to `When` / `Deadline`, `Completed At` / `completed` to `Logbook` completion status, `Notes` to Markdown `Notes`, `Assignee` to `assigneeId`, and `Tags` to Thingsbook `Categories` (case-insensitively reusing existing Categories or creating missing ones).
  - **Export Any Project to Asana CSV or Asana JSON:** 1-click export of any active Thingsbook project (including Phases, open & completed To-Dos, Subtask checklist rows, Dates, Notes, and Categories) into UTF-8 Asana CSV or Asana-compatible JSON.

### 2. Microsoft Planner `.xlsx` & CSV Import + Export (Zero External Dependencies)
- **Native OOXML `.xlsx` & CSV Engine Across Android, ChromeOS PWA, and macOS:**
  - **Import from Microsoft Planner (`.xlsx` & `.csv`):** Built-in zero-dependency OOXML spreadsheet reader supporting shared strings, inline strings, sparse column references, and Excel serial dates alongside ISO/localized dates. Maps `Bucket Name` to `Phases`, `Progress` (`Not started` / `In progress` / `Completed`) to `TaskStatus`, `Priority` (`Urgent` -> Pinned, `Important` -> `Deep Focus`, `Low` -> `Quick Win`), `Checklist Items` + `Completed Checklist Items` to `Checklists`, `Start Date` / `Due Date` to `When` / `Deadline`, and `Labels` to `Categories`.
  - **Export Any Project to Planner `.xlsx` or Planner CSV:** Generates standards-compliant `.xlsx` workbooks (compatible with Microsoft Planner, Excel, Apple Numbers, LibreOffice, and Google Sheets) as well as UTF-8 CSV exports.

### 3. Localization, Non-Truncating Soft Bento UI & Cross-Platform Parity
- **5-Language Full Parity (`EN`, `DE`, `FR`, `IT`, `ES`):** Every import/export tab, section, project picker, action button, and confirmation banner is localized across all 5 languages with non-truncating flow layouts.
- **Future-Proof Schema Hooks Verified:** Nullable `workspaceId` and `assigneeId` fields verified across Room/SQLite/IndexedDB, `.tbbackup` v1 archives, and Firestore Cloud Delta-Sync with full `v2.5.0` backward compatibility.
