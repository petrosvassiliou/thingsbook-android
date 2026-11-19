# Thingsbook v2.1.3 (Stable · Build 83)

## Highlights

### Always-Visible Responsive Top Bar Across All Platforms
- **ChromeOS PWA:** Added live workspace-width responsiveness (`wide`, `medium`, `compact`, and `wrap` tiers via `ResizeObserver`) so pill labels (`Quick Find Ctrl+F`, `Weekly Review`, `Gemini`) progressively collapse into icon buttons with hover tooltips when the workspace narrows, and cleanly wrap onto two balanced rows below `460px` — preventing right-side controls (**Language, Cloud Sync, Update pill, Settings**) from ever clipping off-screen.
- **macOS:** Added an ultra-compact (`< 494pt`) toolbar tier in `MacToolbarLayoutMetrics` that collapses the `"Gemini"` label into an icon capsule and lowered the minimum window width to `836pt` so `NSToolbar` never hides controls behind a `»` overflow chevron at any window or sidebar width.
- **Android:** Upgraded `TopThingsbookBar` to measure its live container width via `BoxWithConstraints` — keeping all controls visible on a single row down to `430dp` and wrapping cleanly into two visible rows on narrow portrait phones (`< 430dp`) instead of hiding buttons in an invisible horizontal scroll strip.
