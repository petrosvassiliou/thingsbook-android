#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${1:-thingsbook-prod}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==============================================================="
echo " Thingsbook for ChromeOS — Build & Firebase Hosting Deployment"
echo " Target Firebase Project: ${PROJECT_ID}"
echo "==============================================================="

echo "[1/3] Building ChromeOS Offline-First PWA bundle (chromeos-pwa/dist)..."
npm --prefix "${SCRIPT_DIR}/chromeos-pwa" install
npm --prefix "${SCRIPT_DIR}/chromeos-pwa" run build

echo "[2/3] Checking Firebase CLI authentication..."
if ! npx -y firebase-tools@latest projects:list >/dev/null 2>&1; then
  echo "Firebase CLI is not yet signed in on this machine. Starting login..."
  npx -y firebase-tools@latest login
fi

echo "[3/3] Deploying ChromeOS PWA to Firebase Hosting (https://${PROJECT_ID}.web.app)..."
npx -y firebase-tools@latest deploy --only hosting --project "${PROJECT_ID}"

echo ""
echo "✅ ChromeOS PWA deployed! Open https://${PROJECT_ID}.web.app in Chrome on your"
echo "   Chromebook and click 'Add to ChromeOS Launcher' (or the ⊕ icon in the address bar)"
echo "   to pin Thingsbook directly to your ChromeOS Start Menu & Shelf (no Linux required)."
