#!/usr/bin/env bash
# Frictionless 1-Command Installer & Updater for Thingsbook on macOS (Sequoia / Tahoe / Sonoma)
# Downloads the latest Universal (Apple Silicon + Intel) DMG directly via curl (avoiding browser
# quarantine popups), installs Thingsbook.app into /Applications, clears quarantine attributes,
# and launches Thingsbook.
set -euo pipefail

APP_NAME="Thingsbook"
INSTALL_DIR="/Applications"
LATEST_RELEASE_API="https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases/latest"
RELEASES_API="https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=15"

resolve_stable_dmg_url() {
  local json_payload="${1:-}"
  [ -z "$json_payload" ] && return 0

  if command -v osascript >/dev/null 2>&1; then
    JSON_PAYLOAD="$json_payload" osascript -l JavaScript -e '
      ObjC.import("Foundation");
      const env = $.NSProcessInfo.processInfo.environment;
      const rawObj = env.objectForKey("JSON_PAYLOAD");
      const raw = rawObj ? ObjC.unwrap(rawObj) : "";
      if (!raw) { ""; } else {
        let parsed;
        try { parsed = JSON.parse(raw); } catch (e) { parsed = null; }
        const list = Array.isArray(parsed) ? parsed : (parsed && typeof parsed === "object" ? [parsed] : []);
        let found = "";
        for (const r of list) {
          if (!r || r.draft === true || r.prerelease === true) continue;
          const tag = String(r.tag_name || "").trim();
          if (!/^v\d+\.\d+\.\d+(\+\d+)?$/i.test(tag)) continue;
          const assets = Array.isArray(r.assets) ? r.assets : [];
          const dmg = assets.find(a => a && typeof a.name === "string" && a.name.toLowerCase().endsWith(".dmg") && a.browser_download_url);
          if (dmg) { found = String(dmg.browser_download_url).trim(); break; }
        }
        found;
      }
    ' 2>/dev/null || true
  elif command -v python3 >/dev/null 2>&1; then
    JSON_PAYLOAD="$json_payload" python3 -c '
import json, os, re, sys
raw = os.environ.get("JSON_PAYLOAD", "")
try:
    parsed = json.loads(raw)
except Exception:
    sys.exit(0)
items = parsed if isinstance(parsed, list) else ([parsed] if isinstance(parsed, dict) else [])
tag_re = re.compile(r"^v\d+\.\d+\.\d+(\+\d+)?$", re.IGNORECASE)
for r in items:
    if not isinstance(r, dict) or r.get("draft") is True or r.get("prerelease") is True:
        continue
    tag = str(r.get("tag_name") or "").strip()
    if not tag_re.match(tag):
        continue
    for a in (r.get("assets") or []):
        if isinstance(a, dict) and str(a.get("name") or "").lower().endswith(".dmg") and a.get("browser_download_url"):
            print(str(a["browser_download_url"]).strip())
            sys.exit(0)
' 2>/dev/null || true
  fi
}

if [ -n "${THINGSBOOK_DMG_URL:-}" ]; then
  DMG_URL="$THINGSBOOK_DMG_URL"
else
  echo "🔍 Locating latest stable $APP_NAME Universal macOS release..."
  LATEST_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" "$LATEST_RELEASE_API" 2>/dev/null || true)"
  DMG_URL="$(resolve_stable_dmg_url "$LATEST_JSON")"
  if [ -z "$DMG_URL" ]; then
    RELEASES_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" "$RELEASES_API" 2>/dev/null || true)"
    DMG_URL="$(resolve_stable_dmg_url "$RELEASES_JSON")"
  fi
fi

if [ -z "$DMG_URL" ]; then
  echo "❌ Error: Could not locate a published stable macOS .dmg asset on GitHub Releases." >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
DMG_PATH="$TMP_DIR/Thingsbook-macOS-Universal.dmg"
MOUNT_POINT="$TMP_DIR/mount"

cleanup() {
  if [ -d "$MOUNT_POINT" ]; then
    hdiutil detach "$MOUNT_POINT" -quiet 2>/dev/null || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "⬇️  Downloading $DMG_URL ..."
curl -fL --progress-bar "$DMG_URL" -o "$DMG_PATH"

echo "💿 Mounting disk image..."
mkdir -p "$MOUNT_POINT"
hdiutil attach "$DMG_PATH" -mountpoint "$MOUNT_POINT" -nobrowse -quiet

if [ ! -d "$MOUNT_POINT/$APP_NAME.app" ]; then
  echo "❌ Error: $APP_NAME.app not found inside disk image." >&2
  exit 1
fi

if pgrep -x "Thingsbook" >/dev/null 2>&1 || pgrep -x "ThingsbookMac" >/dev/null 2>&1; then
  echo "🔄 Closing running $APP_NAME instance..."
  osascript -e 'tell application "Thingsbook" to quit' 2>/dev/null || killall Thingsbook 2>/dev/null || killall ThingsbookMac 2>/dev/null || true
  sleep 1
fi

echo "📦 Installing $APP_NAME.app to $INSTALL_DIR..."
rm -rf "$INSTALL_DIR/$APP_NAME.app"
ditto "$MOUNT_POINT/$APP_NAME.app" "$INSTALL_DIR/$APP_NAME.app"
xattr -cr "$INSTALL_DIR/$APP_NAME.app" 2>/dev/null || true

echo "✅ Installed $INSTALL_DIR/$APP_NAME.app — launching now!"
open "$INSTALL_DIR/$APP_NAME.app"
