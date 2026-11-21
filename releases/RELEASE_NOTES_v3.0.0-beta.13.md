# Thingsbook `v3.0.0-beta.13` (Build `104`) — Subtle Modal Hover, 1-Day & Custom Durations, Instant macOS Selection & Full-Presence App Icons

> **Pre-Release Notice:** `v3.0.0-beta.13` is an opt-in **3.0 Beta** pre-release (`isPrerelease: true`). Stable users on **`v2.5.0`** remain completely isolated on the Official `Latest` channel.

---

## Highlights

### 1. Subtle Zero-Shift Hover Illumination on Task Modal Pills & Controls
- **macOS (`TaskBentoModalView.swift`, `ThingsMacTheme.swift`):** Added `BentoMenuPill` and `BentoWrappingChip` with calm sky-blue hover illumination (`accentBlue` tint wash + crisp border highlight, zero layout shift) across `Effort`, `Duration`, `Waiting On`, `Project / Area`, `Phase`, `Categories`, `Magic Breakdown`, `Attachments`, `Pin`, `Voice Dictation`, and `Delete`.
- **ChromeOS PWA (`index.css`) & Android (`ThingsBadges.kt`):** Added matching subtle zero-shift hover borders and washes on Soft Bento property pills and clickable `ThingsTagPill` badges.

### 2. `1 Day` (`1d`) & Flexible Custom Durations Across All Platforms
- Added **`1d`** (`480` minutes = 1 standard `8h` workday, seamlessly matching Project Roadmap multi-day bar spans and Today Capacity budgeting) to `STANDALONE_DURATION_PRESETS` on macOS, ChromeOS PWA, and Android.
- Added **`Custom…` Duration Pickers & Multi-Locale Shorthand Parsing (`parseCustomDurationMinutes`)** supporting days, hours, minutes, and shorthand expressions (`45m`, `90`, `1:30`, `1.5h`, `1d`, `2,5d`, `1d 4h`, `3t`, `2j`, `1g`) across all 5 supported languages (`EN`, `DE`, `FR`, `IT`, `ES`).

### 3. Instantaneous (`0 ms`) Task Click Selection on macOS
- Replaced SwiftUI's `TapGesture(count: 2)` + `.onTapGesture` pairing (which delayed single-click selection by `NSEvent.doubleClickInterval` ~500–800 ms) with `.thingsInstantTaskTap(...)` across all task rows, Kanban cards, Today Timeline cards, and Project Roadmap rows/bars, plus lazy `@autoclosure` drag-payload generation and immediate non-animated selection state updates.

### 4. Full-Presence App Icon Sizing Across macOS, ChromeOS PWA & Android
- **macOS (`AppIconGlacierV4`):** Expanded the Dock squircle from `824×824` to `864×864` (`84.4%` of `1024×1024`, matching native macOS 26 Tahoe Dock icon height/width) and scaled the sculpted crystal open book by `1.20×` with automatic `CFBundleIconFile` cache invalidation.
- **ChromeOS PWA:** Expanded `pwa-192x192.png`, `pwa-512x512.png`, `apple-touch-icon.png`, and `favicon.svg` from `480×480` to `504×504` (`98.4%` canvas fill) and enlarged the `maskable-512x512.png` book emblem inside the `80%` safe circle.
- **Android:** Scaled and optically centered `ic_launcher_foreground.xml` by `1.18×` (`62dp` span inside the `72dp` adaptive safe zone).
