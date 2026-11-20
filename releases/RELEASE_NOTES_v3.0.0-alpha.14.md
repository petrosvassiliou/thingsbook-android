# Thingsbook v3.0.0-alpha.14 (Pre-Release · Build 91)

## Highlights

### Zero Button Label Truncation Across PDF Export, Board & Roadmap
- **PDF Export Dialog (`Zeitraum` / `Timeline Range`, `Select All`, `Orientation`, and Footer Buttons):** Replaced fixed equal-width single-line clipping with natural-width flow pills (`ThingsFlowLayout` + `.fixedSize(horizontal: true)` on macOS, `flex-wrap` non-shrinking chips on ChromeOS PWA, and `FlowRow` on Android) inside a wider `580 pt` sheet so localized labels like *Gesamtes Projekt*, *Nächste 30 Tage*, *Benutzerdefiniert*, *À partir d'aujourd'hui*, and *Plage personnalisée* are always displayed in full.
- **Board & Roadmap Action Pills:** Ensured Phase Start/End date pills, Kanban `+ New To-Do` footer buttons, and Roadmap toolbar pills (`Jump to Today`, `-1 Day`, `+1 Day`) size naturally or flow-wrap without truncating labels across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`).
