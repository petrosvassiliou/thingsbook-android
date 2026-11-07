# Thingsbook (Things 3 Clone for Android & Chromebooks / ChromeOS)

A native Android & Chromebook (ChromeOS) productivity app inspired by **Things 3 (Cultured Code)** — built with **100% Kotlin**, **Jetpack Compose**, **Offline-First Room Database (SQLite FTS5 `unicode61`)**, **Bidirectional Firebase Firestore Delta-Sync (`thingsbook-prod`)**, **Multilingual Gemini AI**, and **5 Languages (`EN`, `DE`, `IT`, `FR`, `ES`)**.

## Highlights

- **Adaptive Multi-Form-Factor UI (Android Phones + Googlebooks / Chromebooks & Tablets):**
  - **Android Phones (`< 700dp`):** One-handed layout with horizontal Smart View pill strip, swipe-down Quick Find, Inline Card Expansion (`spring(dampingRatio = 0.8f, stiffness = 400f)`), and Draggable "Magic Plus" FAB.
  - **Googlebooks / Chromebooks & Tablets (`>= 700dp`):** 2-Pane Split View with a permanent Things 3 Sidebar (live badge counters & Project Progress Rings), centered reading canvas (`max 840dp`), freeform window resizing (`android:resizeableActivity="true"`), non-touchscreen compatibility (`android.hardware.touchscreen` not required), and Hardware Keyboard Shortcuts (`Ctrl+F` Quick Find, `Ctrl+N` New To-Do, `Ctrl+G` Gemini AI Hub, `Esc` Collapse/Close).
- **Offline-First Room Database + SQLite FTS5 (`unicode61 remove_diacritics 2`):**
  - Full GTD hierarchy: `Areas`, `Projects` (with live Pie-Chart Progress Ring), `Headings`, `Tasks` (with `whenDate`, `deadline`, `recurrenceRule`, `reminderTime`, LexoRank `sortOrder`), `ChecklistItems`, and hierarchical `Tags`.
- **Cost-Efficient Bidirectional Cloud Sync (`Firebase Firestore` + `Firebase Auth`):**
  - Multi-device user registration & sign-in (`users/{userId}/areas`, `projects`, `headings`, `tasks`, `checklist_items`, `tags`).
  - Delta-only queries (`whereGreaterThan("updatedAt", lastSyncTimestamp)`), `WriteBatch` uploads (`isDirty = true`), Last-Write-Wins conflict resolution, and Tombstones (`isDeleted = true`).
- **4 Multilingual Gemini AI Features (`EN`, `DE`, `IT`, `FR`, `ES`):**
  1. **Magic Breakdown (`✨`):** Turns any task into 4–7 actionable checklist steps.
  2. **Brain Dump -> GTD Import:** Parses unstructured thoughts into structured tasks with schedules, deadlines, tags, and checklist items.
  3. **AI Project Architect:** Generates complete projects with headings and tasks from a goal description.
  4. **Logbook AI Reflection:** Summarizes completed tasks and proposes 1-tap focus tasks for Today.
- **Full 5-Language Localization (`EN`, `DE`, `IT`, `FR`, `ES`):**
  - Zero hardcoded UI strings, Per-App Language Switcher (`AppCompatDelegate.setApplicationLocales`), and a 5-language Natural Language Date Parser.
