# Thingsbook v2.0.1 (Build 72) — Android "Planned" (Upcoming) Scroll Fix

**Thingsbook `v2.0.1` (Build `72`)** is a targeted maintenance release for the `2.0` stable series, resolving a scroll-anchoring issue in the **"Planned" (`Upcoming` / *"Geplant"*)** smart view on Android while preserving 100% seamless in-place upgrades across Android, ChromeOS, and macOS.

---

## 🐛 What’s Fixed in v2.0.1

- **📱 Android — "Planned" (`Upcoming` / *"Geplant"*) Opens at the Very Top**:
  - Fixed a Jetpack Compose `LazyColumn` scroll-anchoring bug where navigating to **"Planned"** (`SmartViewType.UPCOMING`) caused Compose's `LazyListScrollPosition` to cross-anchor the shared `trailing_magic_plus_gap` item from the previous view (or an initial empty frame) to the bottom of the Planned list — jumping straight to the last available task instead of the top.
  - Each navigation destination now initializes its own isolated `LazyListState` starting at index `0`, anchors index `0` with a dedicated per-view top scroll key, and scopes trailing Magic Plus drop-slot keys strictly per navigation target so **"Planned"** always opens at the top displaying the earliest/first available task.

---

## 📥 Download & Upgrade (`v2.0.0` → `v2.0.1`)

- **📱 Android (`versionCode 72`, Universal Signed APK)**:
  - Download **`Thingsbook-v2.0.1-GTD-Full.apk`** (or **`Thingsbook-Full-GTD-Edition.apk`**) below, or use **Settings → App Update → Check for Updates** inside the app for a seamless in-place upgrade.
- **💻 ChromeOS & Web (Standalone PWA)**:
  - Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)**.
- **🍎 macOS (`Build 72`, Universal Apple Silicon `arm64` + Intel `x86_64`, macOS 14+)**:
  - Upgrade directly inside Thingsbook (**Settings → Updates → Install & Relaunch**) or download **`Thingsbook-macOS-v2.0.1-Universal.dmg`** below.
