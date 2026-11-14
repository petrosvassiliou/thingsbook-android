#!/usr/bin/env bash
# Frictionless 1-Command Installer & Updater for Thingsbook on macOS (Sequoia / Tahoe / Sonoma)
# Downloads the latest Universal (Apple Silicon + Intel) DMG directly via curl (avoiding browser
# quarantine popups), installs Thingsbook.app into /Applications, clears quarantine attributes,
# and launches Thingsbook.
set -euo pipefail

APP_NAME="Thingsbook"
INSTALL_DIR="/Applications"
DMG_URL="${THINGSBOOK_DMG_URL:-https://github.com/petrosvassiliou/thingsbook-android/releases/download/macos-latest/Thingsbook-macOS-Universal.dmg}"

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

echo "⬇️  Downloading latest $APP_NAME for macOS (Universal arm64 + x86_64)..."
curl -fL --progress-bar "$DMG_URL" -o "$DMG_PATH"

echo "💿 Mounting disk image..."
mkdir -p "$MOUNT_POINT"
hdiutil attach "$DMG_PATH" -mountpoint "$MOUNT_POINT" -nobrowse -quiet

if [ ! -d "$MOUNT_POINT/$APP_NAME.app" ]; then
  echo "❌ Error: $APP_NAME.app not found inside disk image." >&2
  exit 1
fi

if pgrep -x "ThingsbookMac" >/dev/null 2>&1; then
  echo "🔄 Closing running $APP_NAME instance..."
  osascript -e 'tell application "Thingsbook" to quit' 2>/dev/null || killall ThingsbookMac 2>/dev/null || true
  sleep 1
fi

echo "📦 Installing $APP_NAME.app to $INSTALL_DIR..."
rm -rf "$INSTALL_DIR/$APP_NAME.app"
ditto "$MOUNT_POINT/$APP_NAME.app" "$INSTALL_DIR/$APP_NAME.app"
xattr -cr "$INSTALL_DIR/$APP_NAME.app" 2>/dev/null || true

echo "✅ Installed $INSTALL_DIR/$APP_NAME.app — launching now!"
open "$INSTALL_DIR/$APP_NAME.app"
