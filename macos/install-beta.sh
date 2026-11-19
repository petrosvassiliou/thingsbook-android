#!/usr/bin/env bash
# Frictionless 1-Command Beta & Release Installer / Upgrader for Thingsbook on macOS (Sonoma / Sequoia / Tahoe)
# Seamlessly upgrades any existing Thingsbook 1.7.x / 1.8.x / 2.0-beta installation in-place (preserving all
# local SQLite data, attachments, and Cloud Sync sessions) or performs a clean install of the newest
# published stable or pre-release (e.g. v2.0.0-beta.1 -> v2.0.0) Universal macOS DMG.
set -euo pipefail

APP_NAME="Thingsbook"
INSTALL_DIR="/Applications"
RELEASES_API="https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=30"

resolve_beta_or_stable_dmg_url() {
  local json_payload="${1:-}"
  [ -z "$json_payload" ] && return 0

  if command -v osascript >/dev/null 2>&1; then
    JSON_PAYLOAD="$json_payload" osascript -l JavaScript -e '
      ObjC.import("Foundation");
      const env = $.NSProcessInfo.processInfo.environment;
      const rawObj = env.objectForKey("JSON_PAYLOAD");
      const raw = rawObj ? ObjC.unwrap(rawObj) : "";
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
        for (const r of list) {
          if (!r || r.draft === true) continue;
          const tag = String(r.tag_name || "").trim();
          if (!/^v\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(\+\d+)?$/i.test(tag)) continue;
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
    JSON_PAYLOAD="$json_payload" python3 -c '
import functools, json, os, re, sys
raw = os.environ.get("JSON_PAYLOAD", "")
try:
    parsed = json.loads(raw)
except Exception:
    sys.exit(0)
items = parsed if isinstance(parsed, list) else ([parsed] if isinstance(parsed, dict) else [])
tag_re = re.compile(r"^v\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(\+\d+)?$", re.IGNORECASE)

def cmp_semver(a_tag: str, b_tag: str) -> int:
    def split_ver(t: str):
        s = t.strip().lstrip("vV")
        main, build = (s.split("+", 1) + [""])[:2]
        core_s, pre = (main.split("-", 1) + [""])[:2]
        core = [int(x) if x.isdigit() else 0 for x in core_s.split(".")]
        while len(core) < 3:
            core.append(0)
        b_num = int(re.match(r"^\d+", build).group(0)) if re.match(r"^\d+", build) else 0
        return core, pre, b_num
    ac, ap, ab = split_ver(a_tag)
    bc, bp, bb = split_ver(b_tag)
    for av, bv in zip(ac, bc):
        if av != bv:
            return 1 if av > bv else -1
    if not ap and bp:
        return 1
    if ap and not bp:
        return -1
    if ap and bp and ap != bp:
        at, bt = ap.split("."), bp.split(".")
        for i in range(max(len(at), len(bt))):
            if i >= len(at):
                return -1
            if i >= len(bt):
                return 1
            if at[i] == bt[i]:
                continue
            an = int(at[i]) if at[i].isdigit() else None
            bn = int(bt[i]) if bt[i].isdigit() else None
            if an is not None and bn is not None:
                return 1 if an > bn else -1
            if an is not None:
                return -1
            if bn is not None:
                return 1
            return 1 if at[i].lower() > bt[i].lower() else -1
    if ab != bb:
        return 1 if ab > bb else -1
    return 0

best_tag = ""
best_url = ""
for r in items:
    if not isinstance(r, dict) or r.get("draft") is True:
        continue
    tag = str(r.get("tag_name") or "").strip()
    if not tag_re.match(tag):
        continue
    for a in (r.get("assets") or []):
        if isinstance(a, dict) and str(a.get("name") or "").lower().endswith(".dmg") and a.get("browser_download_url"):
            url = str(a["browser_download_url"]).strip()
            if url and (not best_url or cmp_semver(tag, best_tag) > 0):
                best_tag = tag
                best_url = url
            break
if best_url:
    print(best_url)
' 2>/dev/null || true
  fi
}

if [ -n "${THINGSBOOK_DMG_URL:-}" ]; then
  DMG_URL="$THINGSBOOK_DMG_URL"
else
  echo "🔍 Locating newest $APP_NAME Universal macOS release (including 2.0 Beta channel)..."
  RELEASES_JSON="$(curl -fsSL -H "Accept: application/vnd.github+json" -H "User-Agent: Thingsbook-Installer" "$RELEASES_API" 2>/dev/null || true)"
  DMG_URL="$(resolve_beta_or_stable_dmg_url "$RELEASES_JSON")"
  if [ -z "$DMG_URL" ]; then
    RAW_MANIFEST="$(curl -fsSL "https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/PARITY_MANIFEST.json" 2>/dev/null || true)"
    FALLBACK_VER="$(printf '%s' "$RAW_MANIFEST" | sed -n 's/.*"macosVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
    if [ -n "$FALLBACK_VER" ]; then
      DMG_URL="https://github.com/petrosvassiliou/thingsbook-android/releases/download/v${FALLBACK_VER#v}/Thingsbook-macOS-Universal.dmg"
    fi
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
  echo "🔄 Closing running $APP_NAME instance (preserving all local tasks & sync state)..."
  osascript -e 'tell application "Thingsbook" to quit' 2>/dev/null || killall Thingsbook 2>/dev/null || killall ThingsbookMac 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8; do
    if ! pgrep -x "Thingsbook" >/dev/null 2>&1 && ! pgrep -x "ThingsbookMac" >/dev/null 2>&1; then
      break
    fi
    sleep 0.5
  done
  killall Thingsbook 2>/dev/null || killall ThingsbookMac 2>/dev/null || true
fi

LSREGISTER="/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
TARGET_APP="$INSTALL_DIR/$APP_NAME.app"
echo "📦 Upgrading / installing $APP_NAME.app in $INSTALL_DIR..."
if [ -w "$INSTALL_DIR" ] && { [ ! -e "$TARGET_APP" ] || [ -w "$TARGET_APP" ]; }; then
  if [ -d "$TARGET_APP" ]; then
    mv "$TARGET_APP" "$BACKUP_APP" 2>/dev/null || rm -rf "$TARGET_APP"
  fi
  if ditto "$MOUNT_POINT/$APP_NAME.app" "$TARGET_APP"; then
    xattr -cr "$TARGET_APP" 2>/dev/null || true
    touch "$TARGET_APP" 2>/dev/null || true
    [ -x "$LSREGISTER" ] && "$LSREGISTER" -f "$TARGET_APP" 2>/dev/null || true
  else
    [ -d "$BACKUP_APP" ] && [ ! -d "$TARGET_APP" ] && mv "$BACKUP_APP" "$TARGET_APP" 2>/dev/null || true
    echo "❌ Error: Failed to copy $APP_NAME.app into $INSTALL_DIR." >&2
    exit 1
  fi
else
  echo "🔐 Administrator privileges required to replace $TARGET_APP..."
  if [ -d "$TARGET_APP" ]; then
    sudo mv "$TARGET_APP" "$BACKUP_APP" 2>/dev/null || sudo rm -rf "$TARGET_APP"
  fi
  if sudo ditto "$MOUNT_POINT/$APP_NAME.app" "$TARGET_APP"; then
    sudo xattr -cr "$TARGET_APP" 2>/dev/null || true
    sudo touch "$TARGET_APP" 2>/dev/null || true
    [ -x "$LSREGISTER" ] && sudo "$LSREGISTER" -f "$TARGET_APP" 2>/dev/null || true
  else
    [ -d "$BACKUP_APP" ] && [ ! -d "$TARGET_APP" ] && sudo mv "$BACKUP_APP" "$TARGET_APP" 2>/dev/null || true
    echo "❌ Error: Failed to copy $APP_NAME.app into $INSTALL_DIR." >&2
    exit 1
  fi
fi

echo "✅ Installed $TARGET_APP — launching now!"
open "$TARGET_APP"
