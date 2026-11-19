# Thingsbook v3.0.0-alpha.5 (Pre-Release · Build 78)

> **3.0 Alpha Channel (`alpha-3.0`)** — Isolated pre-release preview. Stable `2.x` (`v2.1.1`) users remain on the stable channel unless pre-release updates are explicitly enabled. Full two-way `v2.x ↔ v3.0` `.tbbackup`, CSV, and Firebase Cloud Sync compatibility is preserved.

## Highlights

### Zero-Truncation Weekly Review Screen Across Android, ChromeOS PWA & macOS
- **2-Row Weekly Review Step Cards (`1. Clear Inbox`, `2. Today & Deadlines`, `3. Require Attention`, `4. Someday & Wrap-Up`):** Upgraded the 4-step Weekly Review selector bar on macOS (`WeeklyReviewSheetView`) and ChromeOS PWA (`WeeklyReviewModal`) from single-line pills to **2-Row Step Cards** — placing each step's icon (`left`) and count badge (`right`) on the top row so step titles get **100% of the card width** underneath and never clip with `...` across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`).
- **Wider Modal Surfaces & Non-Truncating Action/Status Pills:** Expanded Weekly Review sheet/modal widths (`860pt` ideal on macOS, `840px` on ChromeOS PWA, `680dp` on Android), locked status pills and action chips (`fixedSize(horizontal: true)` on macOS), and removed `TextOverflow.Ellipsis` / `white-space: nowrap` truncation on project titles, feedback banners, and step navigation controls.
