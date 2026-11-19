# Thingsbook `v2.1.1` (Build `74`) — Weekly Review Interactivity & Visual Feedback Fix

**Thingsbook `v2.1.1`** (Build `74`) is a cross-platform maintenance release across **Android**, **ChromeOS PWA**, and **macOS** resolving the **Weekly Review (`Wochenrückblick`)** interaction & feedback issues reported in Step 2 (*Today & Deadlines* / *Heute & Fristen*) and across all 4 review steps.

---

## 🛠️ What’s Fixed in `v2.1.1`

### 1. Guided Weekly Review — Responsive Quick Actions & Immediate Visual State
- **Live Schedule Badges & Active Selected Chips in Step 2 (*Today & Deadlines*):**
  - Every task in Step 2 now displays its live schedule state (`⭐ Today`, `🌙 This Evening`, `🌅/📅 Tomorrow`, `📅 <Short Date>`, `📚 Anytime`, `📦 Someday`) alongside its project name and `🚩` deadline pill.
  - Scheduling chips (`Today` / *Heute*, `Tomorrow` / *Morgen*, `+7d` / *+7 T.*) highlight immediately with an active `✓` checkmark state when selected.
- **Automatic Deadline Advancement When Deferring (`Tomorrow` / `+7d`):**
  - Deferring an overdue or due-soon task via `Tomorrow` (*Morgen*) or `+7d` (*+7 T.*) now atomically advances an earlier `deadline` to match the deferred target date so tasks never end up scheduled *after* their own deadline.
- **Inline Action Confirmation Pill Inside the Modal:**
  - Every quick action across Steps 1–4 (*Today*, *This Evening*, *Tomorrow*, *+7d*, *Someday*, *Anytime*, *Complete*, *Delete*, *Mark Reviewed*) displays an immediate inline confirmation pill (`✓ <Task> → <Action>`) directly inside the Weekly Review modal.
- **Clickable Task & Project Rows Across Steps 1–4:**
  - Clicking any task title or project row inside the Weekly Review modal opens and expands that task or project directly in the main workspace.
- **macOS Hit-Testing & Synchronous Snapshot Refresh:**
  - Added full-capsule `.contentShape(...)` hit targets on all SwiftUI `.buttonStyle(.plain)` Weekly Review pills and synchronous `reloadSnapshot()` in `ThingsbookStore.updateTask` so clicks anywhere inside a chip respond on the very first frame.

---

## 📦 Downloads & Upgrade Compatibility

- **Android (`v2.1.1`, `versionCode = 74`):** Seamless in-place APK upgrade from `v2.1.0` (`73`), `v2.0.x`, `v1.8.x`, and `v1.7.0` with zero data loss.
- **ChromeOS / Web PWA (`v2.1.1`):** Live at **[https://thingsbook-prod.web.app](https://thingsbook-prod.web.app)** (updates automatically via Service Worker).
- **macOS (`v2.1.1`, Build `74`, Universal `arm64 + x86_64`):** Update in one click via **Settings (`⌘,`) → Updates → Install & Relaunch**, via `Thingsbook-macOS-v2.1.1-Universal.dmg`, or via `curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash`.
