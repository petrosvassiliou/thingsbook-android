#!/usr/bin/env bash
# Frictionless 1-Command Installer & Updater for Thingsbook on macOS (Sequoia / Tahoe / Sonoma)
# Downloads the latest Universal (Apple Silicon + Intel) DMG directly via curl (avoiding browser
# quarantine popups), installs Thingsbook.app into /Applications, clears quarantine attributes,
# and launches Thingsbook.
set -euo pipefail

APP_NAME="Thingsbook"
INSTALL_DIR="/Applications"
LATEST_RELEASE_API="https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases/latest"
RELEASES_API="https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=30"
INCLUDE_BETA="${THINGSBOOK_BETA:-0}"
for arg in "$@"; do
  case "$arg" in
    --beta|-b) INCLUDE_BETA="1" ;;
  esac
done

resolve_dmg_url() {
  local json_payload="${1:-}"
  local allow_beta="${2:-0}"
  [ -z "$json_payload" ] && return 0

  if command -v osascript >/dev/null 2>&1; then
    JSON_PAYLOAD="$json_payload" ALLOW_BETA="$allow_beta" osascript -l JavaScript -e '
      ObjC.import("Foundation");
      const env = $.NSProcessInfo.processInfo.environment;
      const rawObj = env.objectForKey("JSON_PAYLOAD");
      const raw = rawObj ? ObjC.unwrap(rawObj) : "";
      const allowBetaObj = env.objectForKey("ALLOW_BETA");
      const allowBeta = allowBetaObj ? ObjC.unwrap(allowBetaObj) === "1" : false;
      function cmpSemver(aTag, bTag) {
        const clean = t => String(t || "").trim().replace(/^[vV]+/, "");
        const splitPlus = s => { const i = s.indexOf("+"); return i >= 0 ? [s.slice(0, i), s.slice(i + 1)] : [s, ""]; };
        const splitPre = s => { const i = s.indexOf("-"); return i >= 0 ? [s.slice(0, i), s.slice(i + 1)] : [s, ""]; };
        const [aMain, aBuildStr] = splitPlus(clean(aTag));
        const [bMain, bBuildStr] = splitPlus(clean(bTag));
        const [aCoreStr, aPre] = splitPre(aMain);
        const [bCoreStr, bPre] = splitPre(bMain);
        const aCore = aCoreStr.split(".").map(x => parseInt(x, 10) || 0);
        const bCore = bCoreStr.split(".").map(x => parseInt(x, 10) || 0);
        const len = Math.max(aCore.length, bCore.length, 3);
        for (let i = 0; i < len; i++) {
          const av = aCore[i] || 0, bv = bCore[i] || 0;
          if (av !== bv) return av > bv ? 1 : -1;
        }
        if (!aPre && bPre) return 1;
        if (aPre && !bPre) return -1;
        if (aPre && bPre && aPre !== bPre) {
          const at = aPre.split("."), bt = bPre.split(".");
          const m = Math.max(at.length, bt.length);
          for (let i = 0; i < m; i++) {
            if (i >= at.length) return -1;
            if (i >= bt.length) return 1;
            if (at[i] === bt[i]) continue;
            const an = /^\d+$/.test(at[i]) ? parseInt(at[i], 10) : null;
            const bn = /^\d+$/.test(bt[i]) ? parseInt(bt[i], 10) : null;
            if (an !== null && bn !== null) return an > bn ? 1 : -1;
            if (an !== null) return -1;
            if (bn !== null) return 1;
            return at[i].toLowerCase() > bt[i].toLowerCase() ? 1 : -1;
          }
        }
        const aBuild = parseInt(aBuildStr, 10) || 0;
        const bBuild = parseInt(bBuildStr, 10) || 0;
        if (aBuild !== bBuild) return aBuild > bBuild ? 1 : -1;
        return 0;
      }
      if (!raw) { ""; } else {
        let parsed;
        try { parsed = JSON.parse(raw); } catch (e) { parsed = null; }
        const list = Array.isArray(parsed) ? parsed : (parsed && typeof parsed === "object" ? [parsed] : []);
        let bestTag = "";
        let bestUrl = "";
        const tagRe = allowBeta ? /^v\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(\+\d+)?$/i : /^v\d+\.\d+\.\d+(\+\d+)?$/i;
        for (const r of list) {
          if (!r || r.draft === true) continue;
          if (!allowBeta && r.prerelease === true) continue;
          const tag = String(r.tag_name || "").trim();
          if (!tagRe.test(tag)) continue;
          const assets = Array.isArray(r.assets) ? r.assets : [];
          const dmg = assets.find(a => a && typeof a.name === "string" && a.name.toLowerCase().endsWith(".dmg") && a.browser_download_url);
          if (!dmg) continue;
          const url = String(dmg.browser_download_url).trim();
          if (!url) continue;
          if (!bestUrl || cmpSemver(tag, bestTag) > 0) {
            bestTag = tag;
            bestUrl = url;
          }
        }
        bestUrl;
      }
    ' 2>/dev/null || true
  elif command -v python3 >/dev/null 2>&1; then
    JSON_PAYLOAD="$json_payload" ALLOW_BETA="$allow_beta" python3 -c '
import json, os, re, sys
raw = os.environ.get("JSON_PAYLOAD", "")
allow_beta = os.environ.get("ALLOW_BETA") == "1"
try:
    parsed = json.loads(raw)
except Exception:
    sys.exit(0)
items = parsed if isinstance(parsed, list) else ([parsed] if isinstance(parsed, dict) else [])
tag_re = re.compile(r"^v\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(\+\d+)?$" if allow_beta else r"^v\d+\.\d+\.\d+(\+\d+)?$", re.IGNORECASE)
for r in items:
    if not isinstance(r, dict) or r.get("draft") is True:
        continue
    if not allow_beta and r.get("prerelease") is True:
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
elif [ "$INCLUDE_BETA" = "1" ]; then
  echo "🔍 Locating newest $APP_NAME Universal macOS release (including Beta channel)..."
  RELEASES_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" "$RELEASES_API" 2>/dev/null || true)"
  DMG_URL="$(resolve_dmg_url "$RELEASES_JSON" "1")"
else
  echo "🔍 Locating latest stable $APP_NAME Universal macOS release..."
  LATEST_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" "$LATEST_RELEASE_API" 2>/dev/null || true)"
  DMG_URL="$(resolve_dmg_url "$LATEST_JSON" "0")"
  if [ -z "$DMG_URL" ]; then
    RELEASES_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" "$RELEASES_API" 2>/dev/null || true)"
    DMG_URL="$(resolve_dmg_url "$RELEASES_JSON" "0")"
  fi
fi

if [ -z "$DMG_URL" ]; then
  echo "❌ Error: Could not locate a published macOS .dmg asset on GitHub Releases." >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
DMG_PATH="$TMP_DIR/Thingsbook-macOS-Universal.dmg"
MOUNT_POINT="$TMP_DIR/mount"
BACKUP_APP="$TMP_DIR/rollback-$APP_NAME.app"

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
  for _ in 1 2 3 4 5 6; do
    if ! pgrep -x "Thingsbook" >/dev/null 2>&1 && ! pgrep -x "ThingsbookMac" >/dev/null 2>&1; then
      break
    fi
    sleep 0.5
  done
  killall Thingsbook 2>/dev/null || killall ThingsbookMac 2>/dev/null || true
fi

TARGET_APP="$INSTALL_DIR/$APP_NAME.app"
echo "📦 Installing $APP_NAME.app to $INSTALL_DIR..."
if [ -w "$INSTALL_DIR" ] && { [ ! -e "$TARGET_APP" ] || [ -w "$TARGET_APP" ]; }; then
  [ -d "$TARGET_APP" ] && { mv "$TARGET_APP" "$BACKUP_APP" 2>/dev/null || rm -rf "$TARGET_APP"; }
  if ditto "$MOUNT_POINT/$APP_NAME.app" "$TARGET_APP"; then
    xattr -cr "$TARGET_APP" 2>/dev/null || true
  else
    [ -d "$BACKUP_APP" ] && [ ! -d "$TARGET_APP" ] && mv "$BACKUP_APP" "$TARGET_APP" 2>/dev/null || true
    exit 1
  fi
else
  echo "🔐 Administrator privileges required to replace $TARGET_APP..."
  [ -d "$TARGET_APP" ] && { sudo mv "$TARGET_APP" "$BACKUP_APP" 2>/dev/null || sudo rm -rf "$TARGET_APP"; }
  if sudo ditto "$MOUNT_POINT/$APP_NAME.app" "$TARGET_APP"; then
    sudo xattr -cr "$TARGET_APP" 2>/dev/null || true
  else
    [ -d "$BACKUP_APP" ] && [ ! -d "$TARGET_APP" ] && sudo mv "$BACKUP_APP" "$TARGET_APP" 2>/dev/null || true
    exit 1
  fi
fi

echo "✅ Installed $TARGET_APP — launching now!"
open "$TARGET_APP"
