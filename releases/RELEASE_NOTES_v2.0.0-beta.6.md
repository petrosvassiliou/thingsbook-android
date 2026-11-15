# Thingsbook `v2.0.0-beta.6` (Build 50) — Pre-Release Notes

> **Beta Channel Notice**: `v2.0.0-beta.6` is a **2.0 Pre-Release** (`prerelease: true`). Stable `v1.8.1` users remain on `v1.8.1` unless they opt into Beta Updates, and in-place upgrades from `v1.8.x` and `v2.0.0-beta.1..5` to `v2.0.0-beta.6` preserve all local data, attachments, preferences, and Cloud Sync sessions. On macOS `v2.0.0-beta.5`+, this update downloads and installs automatically inside the app with zero manual `.dmg` handling.

## What's New in `v2.0.0-beta.6` (Build 50)

### 1. Interactive Hover Animation on the Magic Plus (`+`) Button Across Android, macOS & ChromeOS
- **Android (`MagicPlusFab`)**: Hovering with a mouse or stylus over the bottom-right `+` Magic Plus button now smoothly animates scale (`1.06×`), subtle upward lift, elevated blue glow shadow, subtle inner white highlight ring, Things hover-blue color shift (`#1479EC` in light mode / `#58ADFF` in dark mode), and a pointing-hand cursor — signaling clearly that it is an interactive button waiting to be pressed, while keeping touch taps and drag-to-insert 100% responsive.
- **macOS (`MainWorkspaceView`)**: Added matching spring hover animation (`1.06×` scale, subtle upward lift, brighter gradient + glow shadow, inner highlight ring, and pointing-hand cursor), smooth `45°` `+` -> `×` icon rotation when opening/closing the popover, and hover row highlights inside the `+` popover menu.
- **ChromeOS / Web PWA**: Refined `.magic-fab-circle` hover lift/scale and preserved the `45°` open rotation while hovering (`rotate(45deg) scale(1.05)`).

### 2. Cross-Platform Parity (`v2.0.0-beta.6`, Build 50)
- Synchronized version/build metadata (`2.0.0-beta.6` / `50`) and full 5-language localization parity (`EN`, `DE`, `FR`, `IT`, `ES` — `484` verified keys) across **Android**, **ChromeOS / Web PWA**, and **macOS Universal (`arm64` + `x86_64`)**.
