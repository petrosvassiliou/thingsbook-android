# Thingsbook v3.0.0-alpha.4 (Pre-Release · Build 77)

> **3.0 Alpha Channel (`alpha-3.0`)** — Isolated pre-release preview. Stable `2.x` (`v2.1.1`) users remain on the stable channel unless pre-release updates are explicitly enabled. Full two-way `v2.x ↔ v3.0` `.tbbackup`, CSV, and Firebase Cloud Sync compatibility is preserved.

## Highlights

### 1. Compact 2-Row Micro-Bento Cards for `Someday` & `Review` (Zero Truncation Across All 5 Languages)
- **Full-Width Label Row on All 6 Sidebar Cards:** Upgraded the `1×2` `Someday` (*Irgendwann*) and `Review` (*Rückblick*) mini-cards to **Compact 2-Row Micro-Bento Tiles** across Android, ChromeOS PWA, and macOS — placing the icon (`left`) and count badge (`right`) on the top row so every card's label receives **100% of the column width** on the bottom row while remaining ~25% tighter vertically than the primary `2×2` Bento tiles above.
- **Universal Anti-Ellipsis Guarantee (`EN`, `DE`, `FR`, `IT`, `ES`):** Removed single-line `text-overflow: ellipsis` clipping on all Smart View Bento and Micro-Bento cards, paired with length-adaptive font scaling (`minimumScaleFactor` on macOS, responsive sizing + clean word-wrap fallback on ChromeOS PWA and Android) and wider desktop sidebar column minimums (`278px` on ChromeOS PWA, `min: 252pt / ideal: 274pt` on macOS) so names like *"Someday"*, *"Irgendwann"*, *"Rückblick"*, *"Boîte de réception"*, and *"En cualquier momento"* never truncate with `...`.
