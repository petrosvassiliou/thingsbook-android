# Thingsbook v2.0.0-beta.2 (Build 46) — Public Beta Preview

**Thingsbook v2.0.0-beta.2** refines the **Weekly Review** experience, icon system, and **5-language Project Templates** across **Android**, **ChromeOS (Web/PWA)**, and **macOS (Universal Apple Silicon + Intel)** — engineered for **100% in-place, zero-data-loss upgrades from `v1.7.0`, `v1.8.0`, `v1.8.1`, and `v2.0.0-beta.1`**, while keeping the stable **`v1.8.1`** production channel completely untouched.

---

## ✨ What’s New & Refined in `v2.0.0-beta.2`

### 1. Clearer GTD Review Terminology: `Requires Review` & `Requires Attention`
- Replaced the misleading *"Stalled"* label across all 5 languages (`EN`, `DE`, `FR`, `IT`, `ES`) with accurate GTD status badges:
  - **`Requires review`**: Shown calmly when an active project has open next actions and simply hasn't been reviewed in **14+ days**.
  - **`Requires attention`**: Shown when an active project has **zero open next-action tasks**, prompting you to add its next concrete action or move it to *Someday*.
  - Step 3 of Weekly Review is now **3. Require Attention** across Android, ChromeOS, and macOS.

### 2. Crisp Vector Checklist Icon for Weekly Review
- Replaced the red compass emoji (`🧭`) and octagonal/seal badge shapes with a clean, calm Things-Blue **Checklist** vector icon (`Icons.Outlined.Checklist` on Android, `ListChecks` on ChromeOS, `checklist.checked` / `checkmark.circle.fill` on macOS) across the Sidebar, Top Bar, Mobile Navigation Strip, and Weekly Review modal.

### 3. Full 5-Language Localization for Starter Project Templates
- All 4 built-in GTD Starter Project Templates (*Trip & Packing*, *Product Launch*, *Client Onboarding*, *Weekly Sprint*) now dynamically localize **every Project Title, Project Note, Section Heading, Task Title, and Checklist Subtask** to your selected app language (**English**, **Deutsch**, **Français**, **Italiano**, and **Español**) both in the Template Gallery preview and when instantiating a new project.

---

## 🔄 Seamless Upgrade from `v1.8.x` or `v2.0.0-beta.1`

- **Stable `v1.8.1` Remains Untouched by Default**: Default stable update checks, `macos/install.sh` on `main`, GitHub `/releases/latest`, and `https://thingsbook-prod.web.app` remain pinned to **`v1.8.1`**.
- **In-Place Upgrade (`Build 46`)**:
  - **Android (`versionCode 46`)**: Signed with the identical release certificate and additive Room `MIGRATION_3_4`. Install `Thingsbook-v2.0.0-beta.2-GTD-Full.apk` directly over `v1.8.x` or `v2.0.0-beta.1` (or tap **Check for Updates** in Settings with **Receive Beta Updates** enabled).
  - **macOS (`Build 46`, Universal `arm64 + x86_64`)**: Upgrade in place in one terminal command:
    ```bash
    curl -fsSL https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/install-beta.sh | bash
    ```
  - **ChromeOS / Web**: Live on the isolated Firebase Hosting `beta` preview channel (`https://thingsbook-prod--beta-63kq14fo.web.app`).
