# Thingsbook `v2.0.0-beta.23` (Build 67) — macOS Settings Tab Bar Redesign & Cross-Platform Sync Parity

## Highlights
- **macOS Settings Icon + Label Toolbar Strip (`SettingsWindowView.swift`)**:
  - Replaced SwiftUI's nested `TabView` (`NSTabView` segmented pill, which stripped SF Symbols and truncated localized labels to `Al... Cl... K... M... B... U...` across a bezel line on macOS) with a native Things-style **macOS Settings Icon + Label Toolbar** (`SettingsToolbarTabButton` + `MacSettingsTabLayoutMetrics`).
  - Displays crisp hierarchical SF Symbols (`gearshape`, `icloud`, `calendar`, `bell.badge`, `externaldrive.badge.timemachine`, `arrow.triangle.2.circlepath.circle`) above full, untruncated localized labels across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`) in a `760×560` content-sized Settings window and sheet.
- **Includes All `v2.0.0-beta.22` Instant Cross-Generation Sync Fixes**:
  - Strict pull-before-push & offline commit restamping, dual `tb_uid_*` + legacy `tb_sync_*` `ntfy` channels, `v1.8` timeblock & rollover resurrection guards, `Cialis` standalone/Area (`Privat`) recurring deduplication, deterministic same-title Tag remapping, and per-collection cursor + `reconcile_v2_22` missing-project recovery.
