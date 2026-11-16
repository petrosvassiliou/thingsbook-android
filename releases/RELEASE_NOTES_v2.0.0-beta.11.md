# Thingsbook v2.0.0-beta.11 (Build 55) — Pre-Release

## Highlights

### 1. Seamless In-Place Upgrade Over `v1.8` Final Releases (`v1.8.0` / `v1.8.0+1` / `v1.8.1`)
- **Android (`versionCode = 55`, Signature-Verified)**:
  - Enforced release APK Signature Scheme v2 certificate SHA-256 verification and `versionCode > 44` checks in CI so `Thingsbook-v2.0.0-beta.11-GTD-Full.apk` always installs cleanly over any `1.8.x` final build without `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or data loss.
  - Hardened Room `MIGRATION_3_4` with idempotent column existence guards (`SupportSQLiteDatabase.hasColumn`) so upgrading from any `1.8.x` SQLite schema preserves all tasks, projects, areas, tags, checklists, attachments, preferences, and Cloud Sync sessions.
  - Expanded GitHub Releases pagination (`per_page=30`) with semantic descending release selection and raw `beta-2.0` fallback in `AppUpdateManager`.
- **macOS (`CFBundleVersion = 55`, Dedicated Beta Installer)**:
  - Upgraded `macos/install-beta.sh` and `macos/install.sh` (`--beta` / `THINGSBOOK_BETA=1`) with atomic backup/rollback and automatic `sudo` privilege escalation when replacing `/Applications/Thingsbook.app` installed by `v1.8.1`.
- **ChromeOS / Web PWA (`APP_BUILD_CODE = 55`)**:
  - Preserves existing IndexedDB (`thingsbook-db`) stores, attachments, preferences, and Firebase Cloud Sync credentials seamlessly across Service Worker upgrades from `v1.8.x` to `v2.0.0-beta.11`.

### 2. Instantaneous Cross-Platform Tooltip Engine & Full UI Coverage (macOS, Android & ChromeOS PWA)
- **Eliminated Stale / Lagging Tooltips Across All Platforms**:
  - **160ms Cold Hover Entry**: Prevents accidental tooltip flicker during casual cursor movement.
  - **0ms Warm Handoff (450ms Cooldown Window)**: Moving the pointer across adjacent toolbar, sidebar, or task card buttons transitions the tooltip in **0ms** with zero stale text lag (fixing the delayed `NSToolTipManager` / native browser tooltip lag when sweeping across topbar icons such as `Schnellsuche (⌘F)`).
  - **0ms Live State & Language Updates**: Changing control state while hovered (e.g., `Pause` ↔ `Resume`, `Syncing...` → `Synced`, `Show Sidebar` ↔ `Hide Sidebar`, or switching app language across **EN, DE, FR, IT, ES**) updates the active tooltip text and anchor coordinates immediately.
  - **Nested Control Precision & Instant Dismissal**: Inner buttons inside tooltipped cards resolve to the innermost control and restore the outer tooltip in `0ms` on exit, while clicks, scrolling, key presses, and window blur dismiss active tooltips immediately.
  - **380ms Passive Touch Long-Press Preview (Android & ChromeOS)**: Stationary touch long-press previews localized button labels and shortcuts without intercepting taps, drags, or scrolling.
- **Full Localized Tooltip Coverage (`EN`, `DE`, `FR`, `IT`, `ES`)**:
  - **macOS (`ThingsMacTooltipController` + `.thingsTooltip(...)`)**: Replaced all delayed `.help(...)` modifiers across all 12 macOS views and toolbars (112 tooltipped controls) with a non-activating, click-through `NSPanel` tooltip engine.
  - **Android (`ThingsTooltipHostBox` + `ThingsHoverTooltip`)**: Added GPU overlay tooltip hosts across the main workspace and all modal `Dialog` windows, covering sidebar views (`Ctrl+1`..`Ctrl+7`), topbar actions, Today List/Timeline switcher, collapsed task badges, expanded task chips, attachment strips/previews, Focus Timer controls, and modal close/action buttons.
  - **ChromeOS PWA (`ThingsTooltipHost` + `TooltipTimingMachine`)**: Automatically upgrades native `title` attributes into `data-tooltip` to eliminate sticky native browser tooltips, with full localized tooltips and keyboard shortcuts across the Sidebar, Smart View Strip, Topbar, Task Rows, Expanded Task Cards, Project/Area Headers, Multi-Select Bar, and Modals.

## Channel Isolation & Compatibility
- Stable **`v1.8.1`** users remain untouched unless **Receive Beta Updates** is enabled in Settings.
- Full bidirectional sync and `.tbbackup` compatibility between `v1.8.1` and `v2.0.0-beta.11`.
