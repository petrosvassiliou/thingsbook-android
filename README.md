# Thingsbook — Native Android GTD Productivity App

Eine eigenständige, native Android-Produktivitäts-App (für **Android-Smartphones, Tablets & Foldables**), gestalterisch und funktional inspiriert vom GTD-Workflow (*Getting Things Done*) — entwickelt mit **100 % Kotlin**, **Jetpack Compose**, **Offline-First Room Database (SQLite FTS5 `unicode61`)**, **Bidirektionalem Firebase Firestore Delta-Sync**, **Multilingual Gemini AI** und **5 Sprachen (`DE`, `EN`, `FR`, `IT`, `ES`)**.

---

## ⚠️ Wichtiger Hinweis / Unabhängigkeits-Erklärung (Disclaimer)

> **DE:** Dieses Projekt ist eine komplett unabhängige Eigenentwicklung und steht in **keinerlei geschäftlicher, technischer oder rechtlicher Verbindung zur Cultured Code GmbH & Co. KG** (den Entwicklern von *Things* / *Things 3*). **Thingsbook funktioniert zu 100 % eigenständig** und besitzt **keinerlei Datenverbindung, Schnittstelle oder Kompatibilität zur offiziellen „Things Cloud“ oder sonstigen Servern von Cultured Code**. Alle Daten werden ausschließlich lokal auf dem jeweiligen Android-Gerät (Room/SQLite) sowie optional im eigenen Firebase-Firestore-Backend des Projekts gespeichert.
>
> **EN:** This project is an independent development and is **in no way affiliated with, endorsed, or sponsored by Cultured Code GmbH & Co. KG** (the creators of *Things* / *Things 3*). **Thingsbook operates 100% independently** and has **no data connection, integration, or compatibility with the official "Things Cloud" or any Cultured Code servers**. All data is stored strictly locally on the Android device (Room/SQLite) and optionally synced via the project's own independent Firebase Firestore backend.

---

## Direkt-Download (Installierbares Android-APK — Version 1.6.15)

- **[Thingsbook-v1.6.15-full.apk herunterladen (v1.6.15 — Hamburger-Slide-Over-Menü für Smartphones; Foldables & Googlebook unverändert)](https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.6.15-full.apk)**

---

## Kernfunktionen & Architektur

- **Adaptives UI für Android-Smartphones, Tablets & Foldables:**
  - **Android-Smartphones (`< 700dp`):** Einhand-optimiertes Layout mit horizontaler Smart-View-Leiste, Wischgeste nach unten für *Quick Find*, flüssiger 120-Hz-Inline-Karten-Expansion (`spring(dampingRatio = 0.8f, stiffness = 400f)`) und ziehbarem **„Magic Plus“ (`+`)** Floating Action Button für direktes Drag & Drop in die Liste.
  - **Große Displays / Android-Tablets & Foldables (`>= 700dp`):** 2-Spalten-Ansicht mit permanenter Seitenleiste (inkl. Live-Zählern & Projekt-Fortschrittsringen), zentrierter Arbeitsfläche (`max 840dp`) und Unterstützung für externe Tastaturen (`Strg+F` Quick Find, `Strg+N` Neue Aufgabe, `Strg+G` Gemini AI Hub, `Esc` Schließen).
- **Offline-First Room Database + SQLite FTS5 (`unicode61 remove_diacritics 2`) + Komplettes Backup & Restore:**
  - Komplette GTD-Hierarchie: `Areas` (Lebensbereiche), `Projects` (mit Live-Pie-Chart-Fortschrittsring), `Headings` (Überschriften innerhalb von Projekten), `Tasks` (mit `whenDate`, `deadline`, `recurrenceRule`, `reminderTime`, LexoRank `sortOrder` und Long-Press auf die Checkbox für *Abgebrochen* mit `X`-Icon), `ChecklistItems` und hierarchische `Tags`.
  - **Backup & Restore (`.tbbackup`):** Vollständige Sicherung sämtlicher Entitäten & Einstellungen in einer einzelnen Datei — wahlweise unverschlüsselt oder mit **AES-256-GCM & PBKDF2-HMAC-SHA256 (120.000 Iterationen)** passwortgeschützt, inklusive atomarem Komplett-Restore nach Passworteingabe.
- **Eigenständiger Bidirektionaler Cloud-Sync (`Firebase Firestore` + `Firebase Auth`):**
  - Multi-Device-Registrierung & Login direkt in der App (`users/{userId}/areas`, `projects`, `headings`, `tasks`, `checklist_items`, `tags`).
  - Kosteneffizienter Delta-Sync (`whereGreaterThan("updatedAt", lastSyncTimestamp)`), `WriteBatch`-Uploads (`isDirty = true`), *Last-Write-Wins*-Konfliktlösung und Soft-Deletes (`isDeleted = true`).
- **4 Mehrsprachige Gemini AI Features (`DE`, `EN`, `FR`, `IT`, `ES`):**
  1. **Magic Breakdown (`✨`):** Zerlegt komplexe Aufgaben per Klick in 4–7 konkrete Checklisten-Schritte.
  2. **Brain Dump -> GTD-Import:** Analysiert unstrukturierte Gedanken und erstellt automatisch Aufgaben mit Zeitplanung, Fristen, Tags und Checklisten.
  3. **AI Projekt-Architekt:** Entwirft aus einer Zielbeschreibung ein komplettes Projekt samt Phasen (`Headings`) und Aufgaben (`Tasks`).
  4. **Logbuch KI-Reflexion:** Fasst erledigte Aufgaben im Logbuch zusammen und schlägt nächste Fokus-Aufgaben für *Heute* vor.
- **Vollständige 5-Sprachen-Lokalisierung (`DE`, `EN`, `FR`, `IT`, `ES`):**
  - Keine hartcodierten UI-Strings, In-App-Sprachumschalter (`AppCompatDelegate.setApplicationLocales`) und ein 5-sprachiger *Natural Language Date Parser* im „Wann?“-Dialog.
