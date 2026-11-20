# Thingsbook v3.0.0-beta.3 (Beta Pre-Release · Build 94)

## Highlights — Roadmap Scroll-Safe Hold-to-Drag Interaction

### 1. Explicit Press-and-Hold Required Before Rescheduling Roadmap Bars
- **Zero Accidental Task or Phase Shifts While Scrolling (`Android`, `ChromeOS PWA`, `macOS`):**
  - **Android (`ProjectWorkspaceViews.kt`):** Replaced immediate `detectHorizontalDragGestures` with `detectDragGesturesAfterLongPress` + `graphicsLayer` visual lift and haptic confirmation, allowing vertical, horizontal, and diagonal touch/mouse scrolling right over Task & Phase bars without ever moving their dates.
  - **ChromeOS PWA (`ProjectBoardAndRoadmap.tsx`, `v30Roadmap.ts`, `index.css`):** Restored native `touch-action: pan-x pan-y` on unarmed Task & Phase bars and added a stationary press-and-hold guard (`260 ms` mouse / `320 ms` touch/pen, cancelling immediately on scroll or `> 6 px` movement before arming).
  - **macOS (`ProjectBoardAndRoadmapView.swift`):** Replaced immediate `DragGesture(minimumDistance: 4)` with `LongPressGesture(minimumDuration: 0.28, maximumDistance: 8)` sequenced before `DragGesture` in the stationary `roadmapTimelineGrid` coordinate space, preventing trackpad/mouse scrolling or three-finger swipes over bars from shifting dates unless explicitly held and dragged.

### 2. Includes All Milestone 4 (`v3.0.0-beta.2`) Capabilities
- 100% offline, zero-dependency **Asana CSV & JSON** and **Microsoft Planner `.xlsx` & CSV** project import + export across Android, ChromeOS PWA, and macOS in `EN`, `DE`, `FR`, `IT`, and `ES`.
