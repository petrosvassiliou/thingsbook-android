# Thingsbook v3.0.0-alpha.9 (Pre-Release · Build 84)

## Highlights

### Always-Visible Responsive Top Bar Across All Platforms
- **ChromeOS PWA:** Added live workspace-width responsiveness (`wide`, `medium`, `compact`, and `wrap` tiers via `ResizeObserver` on `.workspace`) so pill labels (`Quick Find Ctrl+F`, `Weekly Review`, `Gemini`) progressively collapse into icon buttons with hover tooltips as the workspace narrows, and cleanly wrap onto two balanced rows below `460px` — keeping **Language, Cloud Sync, Update pill, and Settings** 100% visible at every window and sidebar width without CSS layout containment side-effects.
- **macOS:** Added an ultra-compact (`< 494pt`) toolbar tier in `MacToolbarLayoutMetrics` that collapses the `"Gemini"` label into an icon capsule and adjusted minimum window width to `846pt` (`340pt` max Bento sidebar + `506pt` detail width) so `NSToolbar` never collapses controls behind a `»` overflow chevron.
- **Android:** Upgraded `TopThingsbookBar` to measure its live container width via `BoxWithConstraints` — keeping all controls visible on a single row down to `430dp` and wrapping cleanly into two visible rows on narrow portrait phones (`< 430dp`) instead of hiding icons inside an invisible horizontal scroll strip.
