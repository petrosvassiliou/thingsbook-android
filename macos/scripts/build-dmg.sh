#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MACOS_DIR}/.." && pwd)"

VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["syncedAndroidVersionName"])' "${MACOS_DIR}/PARITY_MANIFEST.json")"
APP_NAME="Thingsbook"
BUILD_ROOT="${MACOS_DIR}/.build-release"
APP_BUNDLE="${BUILD_ROOT}/${APP_NAME}.app"
DMG_STAGING="${BUILD_ROOT}/dmg-staging"
DMG_OUTPUT="${MACOS_DIR}/dist/${APP_NAME}-macOS-v${VERSION}-Universal.dmg"

echo "==> [1/5] Verifying Android <-> macOS Parity..."
python3 "${SCRIPT_DIR}/verify-android-parity.py"

echo "==> [2/5] Building Universal macOS Binary (arm64 + x86_64)..."
cd "${MACOS_DIR}"
swift build -c release --arch arm64 --arch x86_64

BIN_PATH="$(swift build -c release --arch arm64 --arch x86_64 --show-bin-path)"
EXECUTABLE="${BIN_PATH}/${APP_NAME}"

echo "==> [3/5] Assembling ${APP_NAME}.app Bundle..."
rm -rf "${BUILD_ROOT}"
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"
mkdir -p "${MACOS_DIR}/dist"

cp "${EXECUTABLE}" "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"
chmod +x "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"
cp "${MACOS_DIR}/Resources/Info.plist" "${APP_BUNDLE}/Contents/Info.plist"
printf "APPL????" > "${APP_BUNDLE}/Contents/PkgInfo"

# Copy SwiftPM resource bundle(s) and direct resources into .app/Contents/Resources
find "${BIN_PATH}" -maxdepth 1 -name "*.bundle" -exec cp -R {} "${APP_BUNDLE}/Contents/Resources/" \;
cp "${MACOS_DIR}/Resources/Localization/strings.json" "${APP_BUNDLE}/Contents/Resources/strings.json"
cp "${MACOS_DIR}/Resources/Assets/MenuBarIconTemplate.png" "${APP_BUNDLE}/Contents/Resources/MenuBarIconTemplate.png"
cp "${MACOS_DIR}/Resources/Assets/MenuBarIconTemplate@2x.png" "${APP_BUNDLE}/Contents/Resources/MenuBarIconTemplate@2x.png"

# Compile Apple HIG AppIcon.icns via macOS iconutil
if command -v iconutil >/dev/null 2>&1 && [ -d "${MACOS_DIR}/Resources/Assets/AppIcon.iconset" ]; then
    iconutil -c icns "${MACOS_DIR}/Resources/Assets/AppIcon.iconset" -o "${APP_BUNDLE}/Contents/Resources/AppIcon.icns"
fi

# Create .lproj markers so macOS Finder & System Settings recognize all 5 localizations
for lang in en de fr it es; do
    mkdir -p "${APP_BUNDLE}/Contents/Resources/${lang}.lproj"
done

echo "==> [4/5] Ad-hoc Code Signing ${APP_NAME}.app..."
codesign --force --deep --entitlements "${MACOS_DIR}/Resources/Entitlements.plist" --sign - "${APP_BUNDLE}"

echo "==> [5/5] Packaging ${DMG_OUTPUT}..."
mkdir -p "${DMG_STAGING}"
cp -R "${APP_BUNDLE}" "${DMG_STAGING}/"
ln -s /Applications "${DMG_STAGING}/Applications"

rm -f "${DMG_OUTPUT}"
hdiutil create \
    -volname "${APP_NAME} v${VERSION}" \
    -srcfolder "${DMG_STAGING}" \
    -ov \
    -format UDZO \
    "${DMG_OUTPUT}"

echo "✅ Built Universal macOS App & DMG: ${DMG_OUTPUT}"
