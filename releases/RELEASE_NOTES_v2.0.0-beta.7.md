# Thingsbook v2.0.0-beta.7 (Build 51) — Pre-Release Notes

> **Beta Channel Isolation**: `v2.0.0-beta.7` is published as a GitHub **Pre-Release** (`prerelease: true`) and deployed to the isolated Firebase Hosting `beta` preview channel (`https://thingsbook-prod--beta-63kq14fo.web.app`). Stable `v1.8.1` users on `/releases/latest` and `https://thingsbook-prod.web.app` remain completely unaffected unless they opt into **Include Beta Updates** in Settings.

## What's Fixed & Improved in `v2.0.0-beta.7`

### Cross-Device Project Review Sync (Android, ChromeOS / Web PWA & macOS)
- **Weekly Review Completion Syncs Across Devices**: Completing **Weekly Review** on **Android** or **ChromeOS** now stamps `lastReviewedAt` on all active projects and pushes immediately to Cloud Sync (matching **macOS**), so opening Thingsbook on another device no longer prompts to review those projects again.
- **Immediate Push & One-Time Review Backfill**: Marking an individual project reviewed, moving a project to **Someday**, adding a next action from Weekly Review, or completing Weekly Review now pushes immediately without debounce lag across **macOS**, **ChromeOS**, and **Android**, and runs a one-time `projects` backfill on startup so previously reviewed projects reconcile automatically across devices.
- **Monotonic `lastReviewedAt` Merge Protection**: Cloud Sync (`Firestore` + `ntfy.sh` delta relay + realtime listeners) now merges `lastReviewedAt` monotonically (`max(local, remote)`) across all 3 platforms so concurrent non-review edits or device clock skew never clear or regress a synced project review.
- **Unified 14-Day Review Detection & Creation Defaults**: Newly created projects on **macOS** and **ChromeOS** now initialize `lastReviewedAt` at creation time (matching **Android**), and all 3 platforms evaluate the 14-day review threshold identically (`lastReviewedAt` when present, falling back to `max(updatedAt, createdAt)` for legacy `v1.8` projects) so Android no longer falsely flags freshly created cross-device projects with `null` `lastReviewedAt`.

## Downloads & Preview Links
- **Android (`APK`)**: `Thingsbook-v2.0.0-beta.7-universal-release.apk` (Play Protect friendly, signed with persistent release key)
- **macOS (`DMG` — Apple Silicon & Intel Universal, macOS 14+)**: `Thingsbook-macOS-v2.0.0-beta.7.dmg` (also available directly via **Settings → Updates → Install & Relaunch** on `v2.0.0-beta.5+`)
- **ChromeOS / Web PWA (`beta` Preview Channel)**: `https://thingsbook-prod--beta-63kq14fo.web.app`
