# Thingsbook v1.8.0+1 (macOS Build 43 / Android & ChromeOS v1.8.0)

### 🍎 macOS Fixes & Improvements (`v1.8.0+1`, Build 43)
- **Real-Time Bidirectional Cloud Sync**:
  - Added automatic 15-second background delta polling, immediate foreground sync on app activation (`NSApplication.didBecomeActiveNotification`), and full reconciliation on wake from sleep (`NSWorkspace.didWakeNotification`), launch, and **Sync Now** (`Jetzt synchronisieren`).
  - Separated the internal Firestore document cursor from the wall-clock `lastSyncedAt` display timestamp so **Last synced** (`Zuletzt synchronisiert`) always reflects the latest completed sync.
  - Added a 2-minute clock-skew overlap window on incremental Firestore pulls, bidirectional `ntfy.sh` relay push & pull, FTS5 Quick Find indexing on incoming remote tasks, and resilient coercion for mixed integer/floating-point `sortOrder` values.
  - Added live auto-save (`450ms` debounce + `onDisappear` flush) for expanded To-Do titles, Markdown notes, and checklist items so edits sync immediately even without collapsing the card.
  - Added 1-click **Continue with Google** (`Weiter mit Google`) browser sign-in via secure localhost OAuth loopback callback, plus visible sync error banners when signed in.
- **Direct 1-Click Language Switcher with Flag Icons**:
  - Replaced the nested `App-Sprache >` toolbar submenu with a direct 1-click language popover displaying language flag icons (`🌐`, `🇬🇧`, `🇩🇪`, `🇮🇹`, `🇫🇷`, `🇪🇸`), matching Android.
  - Updated **Settings → General → App Language** with 1-click selectable flag pills.

### ⚡ Upgrade / Install on macOS (from v1.7 or v1.8.0)
```bash
curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/install.sh | bash
```

### 📱 Android & 💻 ChromeOS
- **Android**: Includes `Thingsbook-v1.8.0-GTD-Full.apk` (fully upgradable from `v1.7.0`; existing `v1.8.0` Android devices remain up to date).
- **ChromeOS Live Web App**: https://thingsbook-prod.web.app
