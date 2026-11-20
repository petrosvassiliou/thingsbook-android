# Thingsbook v3.0.0-beta.6 (Beta Pre-Release · Build 97)

## Highlights — Landscape-Only Full Screen on Android Phones & Pure Distraction-Free Full-Screen Timeline Mode

### 1. Landscape-Only Full-Screen Timeline on Android Phones
- **Android Phones (`smallestScreenWidthDp < 600`):** Entering **Full Screen (`Vollbild`)** on either **Project Roadmap Timeline** or **Today Daily Timeline (`Zeitplan`)** now automatically rotates the phone into **Landscape** (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) so the timeline has full horizontal breathing room, and smoothly restores normal orientation (`SCREEN_ORIENTATION_UNSPECIFIED`) upon exiting Full Screen.
- **Googlebooks / Chromebooks, Tablets, ChromeOS PWA & macOS:** Window orientation behavior remains untouched as expected.

### 2. Pure Timeline-Only Full Screen (All Non-Timeline Buttons & Panels Disabled/Hidden)
- In **Full Screen (`Vollbild`)** mode across Android, ChromeOS PWA, and macOS:
  - **Applies strictly to the Timeline canvas itself:** Hides the `Unscheduled` (`Ohne Datum` / `Unscheduled Today`) task trays, floating `+` Magic Plus FAB, `Export Roadmap` menu, view-mode switchers (`List | Timeline` / `List | Board | Roadmap`), and version badges so 100% of the screen is dedicated to the Timeline.
  - **Read/Navigate-Only Focus:** Disables task/phase editing controls (`-1d/+1d` date-shift arrows, checkboxes, drag-to-reschedule, timeblock/auto-schedule editors, task modal popups, and global creation/search shortcuts) while in Full Screen, keeping only Timeline scrolling/panning, `Jump to Today`, `- 1x/2x +` Zoom, and **`Exit Full Screen` (`Vollbild beenden` / `Esc` / Back)** active.
