## Thingsbook `v1.8.0` (Build `42`) — Android, ChromeOS & macOS

**Instant Capture, Native OS Surfaces, Rich File/Photo/PDF Attachments & Location Chips — 100% Seamless In-Place Upgrade from `v1.7.0`.**

---

### 🔄 Seamless Upgrade from `v1.7.0` (`Build 41 → 42`)

- **Android (`Thingsbook-v1.8.0-GTD-Full.apk`):**
  - Signed with the identical X.509 release certificate (`versionCode 41 → 42`) and automatic Room `MIGRATION_2_3` (`attachments` table + 4 indices).
  - Existing `v1.7.0` users receive `v1.8.0` automatically via the in-app updater or can install the APK directly over `v1.7.0` with zero data loss.
- **macOS (`Thingsbook-macOS-v1.8.0-Universal.dmg` — Apple Silicon `arm64` + Intel `x86_64`, macOS 14+):**
  - Automatic additive SQLite3 schema migration + backward-compatible `AppPreferences` & `.tbbackup` v1 decoding.
  - **1-Command Upgrade / Install (No Gatekeeper Prompts):**
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
    ```
    *(Automatically closes a running `v1.7.0` instance if open, replaces `/Applications/Thingsbook.app`, and preserves all local tasks, attachments, Keychain tokens, and preferences.)*
- **ChromeOS (`Thingsbook-ChromeOS-PWA-v1.8.0.zip` / `https://thingsbook-prod.web.app`):**
  - Automatic additive IndexedDB upgrade (`DB_VERSION 1 → 2`) + Workbox `autoUpdate` Service Worker preserving all offline tasks, preferences, and Cloud Sync state.

---

### ✨ What’s New in `v1.8.0` Across All 3 Platforms

1. **🎙️ Voice-to-Task Dictation & 1-Tap "✨ Smart Parse with Gemini"**
   - **Android:** Dictate tasks via `RecognizerIntent` from the top bar, Quick Capture sheet, `4×1` / `4×2` Home Screen Widgets, or `VOICE_CAPTURE` app shortcut.
   - **macOS:** Live speech recognition (`SFSpeechRecognizer` + `AVAudioEngine`) from the toolbar mic button, Quick Entry (`⌃⇧⌘V`), Magic Plus (`+`), Dock Menu, `thingsbook://voice`, or inline on any expanded task card.
   - **ChromeOS:** Live Web Speech dictation from the header, Quick Capture modal, or Shelf shortcut (`/?action=voice-capture`).
   - **1-Tap Gemini Smart Parse:** Speak an unstructured multi-task brain dump and split it into scheduled GTD tasks with dates, tags, and checklists in one click.

2. **⚡ Native OS Capture Surfaces & Project Mode**
   - **Android:** System **Quick Settings Tile (`TileService`)**, new **`4×1` Quick Capture Bar Widget**, and a **🎯 Project** tab in the **`4×2` GTD List Widget**.
   - **macOS:** **`MenuBarExtra` Status Bar Popover** now features a **Today / 🎯 Project** segmented switcher with persistent project selection, quick task completion, and instant Quick / Voice Capture.
   - **ChromeOS:** Standalone Launcher / Shelf shortcuts for *New To-Do*, *Voice Capture*, *Today*, *Inbox*, *Upcoming*, and *Quick Find*.

3. **📎 Rich Task & Project Attachments (Images, PDFs & Files)**
   - Attach photos, PDFs, or files (`max 5 MB`, with automatic JPEG compression) to any **To-Do** or **Project** — including Finder / ChromeOS file **drag-and-drop** onto expanded task cards and macOS `⇧⌘A`.
   - Collapsed task rows display a **`📎 N` count badge**; expanded tasks and project headers display an interactive horizontal thumbnail strip.
   - Built-in **zoomable Image & native PDF viewer** (`PdfRenderer` on Android, `PDFKit` on macOS, inline modal on ChromeOS) + OS Share / Finder Reveal.
   - Included inside **AES-256-GCM `.tbbackup`** archives and synced across Android, ChromeOS & macOS via **Firebase Firestore**.

4. **📍 Smart Location & Maps Chips in Notes**
   - Automatically detects `📍` markers, `Location:` / `Ort:` / `Adresse:` lines, GPS coordinates, and EU/US street addresses in task and project notes, rendering one-tap **Google Maps** (Android/ChromeOS) and **Apple Maps** (macOS) chips alongside Web, Email, and Phone chips.

5. **🌍 Full 5-Language Parity (`EN`, `DE`, `FR`, `IT`, `ES`)**
   - `427` verified localization keys across all 5 languages with zero missing keys.

---

### 🇩🇪 Kurzübersicht (`v1.8.0`, Build `42`)

- **100 % nahtloses Upgrade von `v1.7.0`** auf **Android**, **macOS** und **ChromeOS** ohne Datenverlust (identisches Android-Signaturzertifikat + Room-Migration `2 → 3`, additive SQLite-Migration auf macOS, additive IndexedDB-Migration `1 → 2` auf ChromeOS).
- **Neu in `v1.8.0`:**
  - **🎙️ Spracheingabe (Voice-to-Task) & „✨ Mit Gemini aufteilen“** auf Android, macOS (`⌃⇧⌘V`) und ChromeOS.
  - **⚡ Neue System-Oberflächen:** Android-Schnelleinstellungs-Kachel (`TileService`), `4×1` Schnellzugriffs-Widget, **🎯 Projekt-Modus** im Android-Hauptwidget sowie in der macOS-Menüleisten-App (`MenuBarExtra`).
  - **📎 Datei-, Foto- & PDF-Anhänge** an Aufgaben & Projekten inkl. Drag-&-Drop (macOS/ChromeOS), automatischer Bildkomprimierung, `📎`-Badge, integriertem Bild- & PDF-Viewer (`PdfRenderer` / `PDFKit`), `.tbbackup`-Verschlüsselung und plattformübergreifendem Firestore Cloud-Sync.
  - **📍 Orts- & Karten-Chips** in Notizen für Google Maps (Android/ChromeOS) und Apple Maps (macOS).
