# Thingsbook `v3.0.0-beta.12` (Build `103`) — Zero-Gap Task Modal Body Hugging

> **Pre-Release Notice:** `v3.0.0-beta.12` is an opt-in **3.0 Beta** pre-release (`isPrerelease: true`). Stable users on **`v2.5.0`** remain completely isolated on the Official `Latest` channel.

---

## Highlights

### 1. Eliminated Empty White Space Between Task Title & Bento Cards (macOS & ChromeOS PWA)
- **macOS (`TaskBentoModalView.swift`):** Replaced `ViewThatFits(in: .vertical)` + `.frame(maxHeight:)` (which caused SwiftUI to allocate the full `~528 pt` maximum height and vertically center shorter task bodies with `~83 pt` of blank white space above and below the Bento cards) with pixel-exact intrinsic measurement via `ModalBodyHeightPreferenceKey` + `.frame(height: effectiveBodyHeight)` + `.scrollDisabled(!needsVerticalScroll)` and `.fixedSize(horizontal: false, vertical: true)` on the outer modal `VStack`.
- **Crisp Header-to-Body Rhythm:** Tightened header, body, and empty-notes spacing on macOS and ChromeOS PWA so `Notes` / `Schedule` sit cleanly `12 pt` directly beneath the task title divider with zero wasted vertical whitespace, while still scrolling smoothly when a task exceeds the viewport cap.
- **Adaptive Markdown Editor Height:** Toggling `Edit Notes` on macOS now sizes the editor proportionally to the current note line count (`92–196 pt`) so switching between Markdown preview and editing feels seamless.
