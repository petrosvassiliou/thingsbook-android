# Thingsbook v2.0.0-beta.3 (Build 47) — Public Beta Preview

**Thingsbook v2.0.0-beta.3** adds an interactive **Delete Project & Associated To-Dos** prompt and full cascade delete/restore/undo support across **Android**, **ChromeOS (Web/PWA)**, and **macOS (Universal Apple Silicon + Intel)** — maintaining **100% in-place, zero-data-loss upgrades from `v1.7.0`, `v1.8.x`, `v2.0.0-beta.1`, and `v2.0.0-beta.2`**, while keeping the stable **`v1.8.1`** production channel completely untouched.

---

## ✨ What’s New & Fixed in `v2.0.0-beta.3`

### Interactive Project Deletion Prompt & Cascade Task Deletion (`EN`, `DE`, `FR`, `IT`, `ES`)
- **Smart Prompt When Deleting Non-Empty Projects**: Whenever you delete a project containing associated to-dos, Thingsbook now prompts you to choose:
  1. **`Delete Project & To-Dos`**: Moves the project, its section headings, **and all associated to-dos** to Trash (canceling any active task reminders so no tasks remain floating in *Today*, *Anytime*, *Upcoming*, *Someday*, or *Logbook*).
  2. **`Keep To-Dos (Delete Project Only)`**: Moves only the project and headings to Trash while detaching the to-dos cleanly without a project.
  3. **`Cancel`**: Aborts deletion safely.
- *(Empty projects with 0 associated to-dos move straight to Trash with an instant Undo banner and zero extra clicks.)*
- **Full Undo, Trash Restore & Tombstone Sync Parity**:
  - Clicking **Undo** or restoring the project from **Trash** restores the project, its headings, and all cascade-deleted to-dos (or re-attaches detached to-dos if *Keep To-Dos* was chosen).
  - Restoring an individual task from Trash while its parent project remains in Trash cleanly detaches it so it reappears in your Smart Views immediately.
  - Permanently deleting a project in Trash purges its cascade-deleted tasks with Cloud Sync tombstones so deleted items never resurrect across devices.

---

## 🔄 Seamless Upgrade (`Build 47`)

- **Stable `v1.8.1` Remains Untouched by Default**: Default stable update checks, `macos/install.sh` on `main`, GitHub `/releases/latest`, and `https://thingsbook-prod.web.app` remain pinned to **`v1.8.1`**.
- **Android (`versionCode 47`)**: Tap **Check for Updates** in **Settings → App Update** (with **Receive Beta Updates** enabled), or install `Thingsbook-v2.0.0-beta.3-GTD-Full.apk` directly over `v1.8.x` / `v2.0.0-beta.x`.
- **macOS (`Build 47`, Universal `arm64 + x86_64`)**: Tap **Check for Updates** in **Settings**, or run:
  ```bash
  curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/install-beta.sh | bash
  ```
- **ChromeOS / Web**: Live on the isolated Firebase Hosting `beta` preview channel (`https://thingsbook-prod--beta-63kq14fo.web.app`).
