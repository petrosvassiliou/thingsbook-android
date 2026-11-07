#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.culturedcode.things3"
PROJECT_ID="${1:-things3-android-sync}"

echo "========================================================"
echo " Things 3 Android – Automatisiertes Firebase Provisioning"
echo "========================================================"

echo "[1/5] Authentifizierung via Firebase CLI..."
npx firebase-tools login

echo "[2/5] Verknüpfe oder erstelle Firebase-Projekt: ${PROJECT_ID}..."
npx firebase-tools use "${PROJECT_ID}" --add || npx firebase-tools projects:create "${PROJECT_ID}"

echo "[3/5] Registriere Android-App (${PACKAGE_NAME})..."
npx firebase-tools apps:create android "Things 3 Android" --package-name="${PACKAGE_NAME}" --project="${PROJECT_ID}" || true

echo "[4/5] Lade google-services.json nach app/google-services.json herunter..."
npx firebase-tools apps:sdkconfig android -o app/google-services.json --project="${PROJECT_ID}"

echo "[5/5] Deploye Firestore Security Rules (firestore.rules) & Indizes (firestore.indexes.json)..."
npx firebase-tools deploy --only firestore --project="${PROJECT_ID}"

echo "✅ Firebase Provisioning & Deployment erfolgreich abgeschlossen!"
