# Thingsbook `v2.0.0-beta.5` (Build 49) — Pre-Release Notes

> **Beta Channel Notice**: `v2.0.0-beta.5` is a **2.0 Pre-Release** (`prerelease: true`). Stable `v1.8.1` users remain on `v1.8.1` unless they opt into Beta Updates, and in-place upgrades from `v1.8.x` and `v2.0.0-beta.1..4` to `v2.0.0-beta.5` preserve all local data, attachments, preferences, and Cloud Sync sessions.

## What's New in `v2.0.0-beta.5` (Build 49)

### 1. macOS In-App Automatic Updates (No Manual `.dmg` Download Needed)
- **Silent Background Staging**: When **Automatic Update Check** is enabled, Thingsbook for macOS checks GitHub Releases on launch and periodically in the background, silently streams the signed update archive, invisibly mounts and verifies `Thingsbook.app` (`CFBundleIdentifier`, executable binary, and `codesign --verify --deep --strict`), clears Gatekeeper quarantine attributes, detaches the temporary volume, and deletes the temporary archive in the background.
- **1-Click Install & Automatic Relaunch**: Clicking **Install & Relaunch** / **Update Now** in either the top workspace update banner or **Settings -> Updates** (or **Thingsbook -> Search for Updates…** in the macOS menu bar) atomically swaps `Thingsbook.app` in place (with automatic rollback protection and macOS administrator elevation only if the parent directory requires it) and relaunches Thingsbook within ~1 second — without ever opening a browser or downloading a `.dmg` file manually.

### 2. Pixel-Exact macOS Top Search / Toolbar Frame Alignment
- Eliminated the `3pt` (`6px` @2x Retina) horizontal overhang where AppKit's default `NSTitlebarView` background and bottom hairline extended into the sidebar column to the left of the `NSSplitView` vertical divider.
- The top toolbar background and `1pt` horizontal separator now start flush at `0.0px` offset from the vertical sidebar divider, while scrolled canvas content clips cleanly beneath the separator and all native toolbar items, popovers, and window drag regions remain 100% responsive.

### 3. Cross-Platform Parity (`v2.0.0-beta.5`, Build 49)
- Synchronized version/build metadata (`2.0.0-beta.5` / `49`) and full 5-language localization parity (`EN`, `DE`, `FR`, `IT`, `ES` — `484` verified keys) across **Android**, **ChromeOS / Web PWA**, and **macOS Universal (`arm64` + `x86_64`)**.
