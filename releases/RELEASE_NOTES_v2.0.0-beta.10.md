# Thingsbook v2.0.0-beta.10 (Build 54) — Pre-Release

## Highlights

### 1. macOS — Readable, Non-Truncated Action Buttons Across All Languages & Window Sizes
- **Responsive Two-Tier Expanded Task Footer (`ThingsFlowLayout` + `ThingsActionChipButton`)**:
  - Replaced the cramped single-row 11-control expanded task footer with a clean two-tier layout:
    - **Row 1 (Primary GTD Actions)**: `When`, `Repeat`, `Move`, `Tags`, `Deadline`, and `Timeblock` render as crisp, tactile rounded action chips that always display their full localized labels (e.g., *Wann*, *Wiederholen*, *Bewegen*, *Tags*, *Frist*, *Zeitblock* in German) without ever truncating into ellipses (`"Wa..."`, `"Wie..."`, `"Be..."`, `"Zeit..."`), gracefully wrapping onto a second line on narrow windows (`880pt`).
    - **Row 2 (Utility Controls + Done)**: Left-aligned `Attach`, `Focus Timer`, `Duplicate`, and `Delete` utility icon buttons (`ThingsUtilityIconButton`) paired with the right-aligned `Done` capsule button (`Esc`).
- **Full-Width Readability Audit Across macOS Views**:
  - Upgraded the Project header quick-action pills (`Target Date`, `Area`, `+ To-Do`, `+ Heading`, `Attach Photo or PDF`), Today List/Timeline segmented switcher, Quick Find filter pills, Today Timeline timeblock controls, Focus Timer banner, Multi-Select bar, Undo banner, and Attachment preview controls to preserve full label readability without ellipsis compression across **EN, DE, FR, IT, ES**.

### 2. macOS — Comprehensive Localized Mouse Hover Tooltips (`.help(...)`)
- **Contextual Hover Tooltips on Every Interactive Control**:
  - Added localized `.help(...)` tooltips directly on `Button` / `Menu` / `Picker` controls across the expanded task card, collapsed task badges (`Attachments`, `Timeblock`, `Reminder`, `Checklist`, `Recurrence`, `Deadline`), Smart Link chips, Checklist toggles, Project header actions, Today Timeline controls, Quick Find overlay & search filters, Sidebar `+ New Project` menu, Focus Timer & Multi-Select banners, Quick Entry AI Smart Parse, and Attachment Preview zoom/share controls.
  - Fixed AppKit hit-testing on icon buttons where `.help(...)` previously sat inside `label:` instead of on the outer `Button`, ensuring reliable native macOS tooltip display on mouse hover.

## Channel Isolation & Compatibility
- Stable **`v1.8.1`** users remain untouched unless **Receive Beta Updates** is enabled in Settings.
- Full bidirectional sync and `.tbbackup` compatibility between `v1.8.1` and `v2.0.0-beta.10`.
