# Thingsbook v3.0.0-alpha.1 (Build 74) — 3.0 Alpha UI Overhaul

> **Pre-Release Alpha Channel (`alpha-3.0`)**
> Stable `v2.1.0` remains the default `--latest` release and live production ChromeOS web app (`https://thingsbook-prod.web.app`). `v3.0.0-alpha.1` is published as an opt-in pre-release across Android, ChromeOS PWA (`alpha-3-0` preview channel), and macOS.

## Highlights

### 1. Original, Copyright-Distinct 3.0 Visual Identity
- **Crisp Pure White & Neutral Charcoal Dominant Surfaces:** Pure White (`#FFFFFF` canvas & elevated cards, `#F4F5F8` sidebar) in Light Mode and Deep Neutral Charcoal (`#121417` canvas, `#171A1E` sidebar, `#1B1F25` cards) in Dark Mode.
- **Sky Light Blue Accent:** Fresh Sky Light Blue (`#3898EC` in Light Mode / `#6BC0FF` in Dark Mode) reserved strictly for active navigation capsules, focus rings, completed indicators, and primary actions.
- **Monochrome-to-Sky-Blue Smart View Icons:** Replaces rainbow sidebar/header icons with cohesive monochrome slate icons that illuminate in Sky Light Blue when active.
- **New App Icon ("Glacier Liquid Glass Book"):** Sculpted frosted-crystal open book over a luminous Glacier Sky Light Blue squircle across Android Adaptive Icons, ChromeOS PWA icons, and macOS Apple HIG `.icns` iconsets.

### 2. Platform-Native Expressive Design Languages
- **Android & Chromebook (`Material 3 Expressive`):**
  - Full-capsule active navigation pills (`RoundedCornerShape(50)`), `18dp` expanded inline task cards with subtle Sky Light Blue focus rings, and an `18dp` Material 3 Expressive squircle `+` FAB.
  - Morphing squircle-to-circle task checkboxes (`6.5dp` squircle when open → smooth Sky Light Blue circle on completion).
  - Open-track rounded-cap Sky Light Blue project progress arcs replacing pie-slice wedges.
- **ChromeOS Progressive Web App (`Material 3 Expressive`):**
  - Matching Crisp White / Neutral Charcoal CSS design tokens, capsule sidebar & top-strip pills, morphing `6.5px → 50%` checkboxes, open-track SVG project progress arcs, and `18px` squircle `+` FAB.
- **macOS (`Tahoe Liquid Glass`):**
  - Translucent `.ultraThinMaterial` Liquid Glass sidebar with subtle specular rim, capsule Sky Light Blue selection pills, spring-animated squircle-to-circle checkboxes, open-track project progress arcs, and a continuous-curvature Liquid Glass `+` button.

### 3. `Areas → Projects` Hierarchy & Unified Inline Task Editing
- **Projects Live Inside Areas:** Collapsible `Areas → Projects` disclosure sections across Android, ChromeOS PWA, and macOS, with `+ New Project` automatically defaulting to the active Area (or first Area) while preserving legacy unassigned 2.x projects cleanly below Areas.
- **Unified Inline Task Editor (from `v2.1.0`):** Retains Thingsbook's signature in-place task editing with the streamlined single-row action bar (`When`, `Time`, `Repeat`, `Project`, `Tags`, `Checklist`, `Deadline`, conditional `Attachment` count, and `⋯` More menu).

### 4. Package Identity & Seamless 2.x → 3.0 Upgrade Continuity
- **Android Package (`com.pv.thingsbook`):** Clean `com.pv.thingsbook` namespace and application ID wired to the `thingsbook-prod` Firebase Cloud Sync backend and AES-256-GCM `.tbbackup` v1 archive format for lossless migration from `2.x`.
- **macOS In-Place Upgrade:** Preserves `CFBundleIdentifier` (`com.thingsbook.macos`) and `~/Library/Application Support/Thingsbook/thingsbook.sqlite3` so `2.x` upgrades in-place to `3.0` with all local data, Keychain secrets, and macOS permissions intact.
