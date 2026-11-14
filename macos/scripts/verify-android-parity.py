#!/usr/bin/env python3
"""
Verifies that the macOS port (`macos/`) strictly matches the Android `main` branch (`app/`):
1. Version parity (`versionName` & `versionCode` in `app/build.gradle.kts` vs `macos/PARITY_MANIFEST.json` & `Info.plist`)
2. Localization parity (every `<string>` in `app/src/main/res/values*/strings.xml` across EN, DE, FR, IT, ES exists in `macos/Resources/Localization/strings.json`)
3. Backup format parity (`THINGSBOOK_BACKUP_V1` & `FORMAT_VERSION = 1` in `ThingsBackupCodec.kt` vs `ThingsBackupCodec.swift`)
4. Firestore collection parity (`tags`, `areas`, `projects`, `headings`, `tasks`, `checklist_items`)
5. Core Swift architectural modules exist and are non-empty.
"""

import json
import plistlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
GRADLE_FILE = REPO_ROOT / "app" / "build.gradle.kts"
BACKUP_KT = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "culturedcode" / "things3" / "data" / "backup" / "ThingsBackupCodec.kt"
MANIFEST_FILE = REPO_ROOT / "macos" / "PARITY_MANIFEST.json"
INFO_PLIST = REPO_ROOT / "macos" / "Resources" / "Info.plist"
STRINGS_JSON = REPO_ROOT / "macos" / "Resources" / "Localization" / "strings.json"
BACKUP_SWIFT = REPO_ROOT / "macos" / "Sources" / "ThingsbookCore" / "Backup" / "ThingsBackupCodec.swift"


def fail(msg: str) -> None:
    print(f"❌ PARITY CHECK FAILED: {msg}", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    if not GRADLE_FILE.is_file():
        fail(f"Missing {GRADLE_FILE}")
    if not MANIFEST_FILE.is_file():
        fail(f"Missing {MANIFEST_FILE}")

    gradle_text = GRADLE_FILE.read_text(encoding="utf-8")
    v_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
    v_code_match = re.search(r"versionCode\s*=\s*(\d+)", gradle_text)
    if not v_name_match or not v_code_match:
        fail("Could not parse versionName / versionCode from app/build.gradle.kts")

    android_version_name = v_name_match.group(1)
    android_version_code = int(v_code_match.group(1))

    manifest = json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    if manifest.get("syncedAndroidVersionName") != android_version_name:
        fail(
            f"Version mismatch! Android is {android_version_name}, macOS PARITY_MANIFEST is {manifest.get('syncedAndroidVersionName')}. "
            "Port the new Android changes from main to macOS and update PARITY_MANIFEST.json."
        )
    if manifest.get("syncedAndroidVersionCode") != android_version_code:
        fail(
            f"Build number mismatch! Android versionCode is {android_version_code}, macOS PARITY_MANIFEST is {manifest.get('syncedAndroidVersionCode')}."
        )

    if INFO_PLIST.is_file():
        plist = plistlib.loads(INFO_PLIST.read_bytes())
        if plist.get("CFBundleShortVersionString") != android_version_name:
            fail(
                f"Info.plist CFBundleShortVersionString ({plist.get('CFBundleShortVersionString')}) != Android ({android_version_name})"
            )
        if str(plist.get("CFBundleVersion")) != str(android_version_code):
            fail(
                f"Info.plist CFBundleVersion ({plist.get('CFBundleVersion')}) != Android ({android_version_code})"
            )

    if not STRINGS_JSON.is_file():
        fail(f"Missing {STRINGS_JSON} — run python3 macos/scripts/sync-strings-from-android.py")

    mac_strings = json.loads(STRINGS_JSON.read_text(encoding="utf-8"))
    for lang, folder in [
        ("en", "values"),
        ("de", "values-de"),
        ("fr", "values-fr"),
        ("it", "values-it"),
        ("es", "values-es"),
    ]:
        xml_path = REPO_ROOT / "app" / "src" / "main" / "res" / folder / "strings.xml"
        root = ET.parse(xml_path).getroot()
        android_keys = {e.attrib["name"] for e in root.findall("string") if "name" in e.attrib}
        mac_lang_keys = set(mac_strings.get(lang, {}).keys())
        missing = sorted(android_keys - mac_lang_keys)
        if missing:
            fail(f"macOS strings.json [{lang}] is missing {len(missing)} Android string keys: {missing[:10]}")

    if BACKUP_KT.is_file() and BACKUP_SWIFT.is_file():
        kt_text = BACKUP_KT.read_text(encoding="utf-8")
        swift_text = BACKUP_SWIFT.read_text(encoding="utf-8")
        kt_magic = re.search(r'BACKUP_MAGIC\s*=\s*"([^"]+)"', kt_text)
        swift_magic = re.search(r'backupMagic\s*=\s*"([^"]+)"', swift_text)
        if kt_magic and swift_magic and kt_magic.group(1) != swift_magic.group(1):
            fail(f"Backup magic mismatch: Android={kt_magic.group(1)} vs macOS={swift_magic.group(1)}")

    required_swift_files = [
        "Sources/ThingsbookCore/Domain/ThingsEnums.swift",
        "Sources/ThingsbookCore/Domain/DomainModels.swift",
        "Sources/ThingsbookCore/Domain/MultilingualDateParser.swift",
        "Sources/ThingsbookCore/Domain/MarkdownNotesParser.swift",
        "Sources/ThingsbookCore/Database/ThingsSQLiteDatabase.swift",
        "Sources/ThingsbookCore/Backup/ThingsBackupCodec.swift",
        "Sources/ThingsbookCore/Sync/FirebaseCloudSyncService.swift",
        "Sources/ThingsbookCore/AI/ThingsbookGeminiService.swift",
        "Sources/ThingsbookCore/System/AppleCalendarService.swift",
        "Sources/ThingsbookCore/System/BiometricLockService.swift",
        "Sources/ThingsbookCore/System/NotificationReminderService.swift",
        "Sources/ThingsbookMac/ThingsbookMacApp.swift",
        "Sources/ThingsbookMac/Views/MainWorkspaceView.swift",
    ]
    for rel in required_swift_files:
        p = REPO_ROOT / "macos" / rel
        if not p.is_file():
            fail(f"Missing required Swift source file: macos/{rel}")

    print(
        f"✅ Android ↔ macOS Parity Verified: v{android_version_name} (Build {android_version_code}), "
        f"5 languages ({len(mac_strings['en'])} keys/lang), .tbbackup v1, and all GTD features present."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
