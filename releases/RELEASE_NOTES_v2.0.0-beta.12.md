# Thingsbook v2.0.0-beta.12 (Build 56) — Pre-Release

## Highlights

### 1. Fixed Beta-to-Beta Update Discovery Across macOS, Android & ChromeOS
- **macOS Beta Channel Persistence Fix (`AppUpdateService.resolveEffectiveIncludeBeta`)**:
  - Fixed a bug where upgrading macOS Thingsbook from a `1.8.x` installation (which had saved `"includeBetaUpdates": false` in `UserDefaults` or restored a `1.8.x` `.tbbackup`) caused `2.0.0-beta.x` builds to query only stable releases and report *"You are up to date"* instead of finding newer `2.0.0-beta.*` releases.
  - Pre-release builds now always check the beta channel (`preferences.includeBetaUpdates || currentVersionIsPrerelease`), matching Android and ChromeOS PWA while automatically returning to the user's saved stable preference once promoted to `2.0.0` final.
- **GitHub API Cache Bypass & Rate-Limit Fallback (`PARITY_MANIFEST.json`)**:
  - Added `.reloadIgnoringLocalCacheData` / `Cache-Control: no-cache` across macOS, Android, and ChromeOS PWA update checks so local caches never serve stale `/releases` lists.
  - Added automatic fallback to `raw.githubusercontent.com/.../PARITY_MANIFEST.json` across macOS (`AppUpdateService`), ChromeOS PWA (`pwaUpdater`), and the macOS 1-command installers (`install-beta.sh` & `install.sh`) when GitHub's unauthenticated REST API (`60 req/hr`) returns HTTP `403` rate limits.
  - Surfaced genuine offline/network errors in macOS Settings instead of reporting *"You are up to date"*.
- **Semantic Release Sorting Across Double-Digit Betas (`beta.9` -> `beta.10` / `beta.11` / `beta.12`)**:
  - Carries forward all `v2.0.0-beta.11` improvements: descending SemVer sorting on GitHub's `/releases` payload (immune to GitHub's lexicographic `"v2.0.0-beta.9" > "v2.0.0-beta.10"` tag ordering), instant cross-platform floating tooltips (`160ms` cold / `0ms` warm handoff / `0ms` live updates / `380ms` touch preview), and verified `1.8` in-place upgrade compatibility.

## Updating from `v2.0.0-beta.9` (One-Time Step)
- **macOS (`v2.0.0-beta.9`)**: Open **Settings (`⌘,`) → General → App Updates**, toggle ON **Receive Beta Updates** (*Beta-Updates erhalten*), and click **Search for Updates** (*Nach Updates suchen*) — or run:
  ```bash
  curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/install-beta.sh | bash
  ```
- **Android (`v2.0.0-beta.9`)**: Download and install `Thingsbook-Full-GTD-Edition.apk` directly from this release over your existing app (all `v2.0.0-beta.11+` builds include semantic release sorting so future double-digit beta updates are detected automatically).
