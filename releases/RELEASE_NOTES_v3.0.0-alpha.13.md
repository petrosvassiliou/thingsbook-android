# Thingsbook v3.0.0-alpha.13 (Pre-Release · Build 90)

## Highlights

### 1. Always-Visible Full Project Phase Titles
- **Zero Phase Title Truncation:** Project Phase titles now wrap cleanly and remain 100% visible across **List**, **Board (Kanban)** column headers, **Roadmap (Gantt)** left label lanes, **Roadmap timeline Phase bars** (including narrow single-/few-day phases), and **PDF/PNG exports** across Android, ChromeOS PWA, and macOS.

### 2. Smart Roadmap Horizon Cap (`≤ 1 Month` Past Last Task Due Date)
- **Focused Timeline Range:** The Project Roadmap timeline now caps automatically at **up to 1 calendar month after the latest task due/end date** (never stretching months ahead due to distant project target dates or far-future phase bounds), clamping extended Phase bars cleanly to the visible horizon.

### 3. Interactive PDF Export Configuration Dialog
- **Customizable Roadmap PDF Export:** Choosing **Export PDF** now opens a Soft Bento **Export Roadmap as PDF** dialog across Android, ChromeOS PWA, and macOS with:
  - **Project Phases Selection:** Choose specific phases (or `Select All`) to include in the exported report.
  - **Timeline Range:** Pick `Full Project`, `From Today`, `Next 30 Days`, or a `Custom Range` (`Start Date` → `End Date`).
  - **Page Orientation (`Landscape` & `Portrait`):** Export properly proportioned A4 **Landscape** (`842 × 595 pt`) or **Portrait** (`595 × 842 pt`) vector PDFs with adaptive timeline headers and clean multi-page row pagination.
