#!/usr/bin/env python3
"""
Synchronizes all 5 language resource files (EN, DE, FR, IT, ES) directly from
the Android `app/src/main/res/values*/strings.xml` source of truth into
`macos/Resources/Localization/strings.json`, plus macOS-specific Apple HIG keys.
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID_RES = REPO_ROOT / "app" / "src" / "main" / "res"
OUT_JSON = REPO_ROOT / "macos" / "Resources" / "Localization" / "strings.json"
CORE_OUT_JSON = REPO_ROOT / "macos" / "Sources" / "ThingsbookCore" / "Resources" / "strings.json"

LANG_DIRS = {
    "en": ANDROID_RES / "values" / "strings.xml",
    "de": ANDROID_RES / "values-de" / "strings.xml",
    "fr": ANDROID_RES / "values-fr" / "strings.xml",
    "it": ANDROID_RES / "values-it" / "strings.xml",
    "es": ANDROID_RES / "values-es" / "strings.xml",
}

MACOS_EXTRA_STRINGS = {
    "en": {
        "macos_menubar_quick_entry": "Quick Entry…",
        "macos_menubar_open_app": "Open Thingsbook",
        "macos_menubar_today_header": "Today's Focus",
        "macos_menubar_quit": "Quit Thingsbook",
        "macos_touch_id_title": "Touch ID / System Password Lock",
        "macos_touch_id_desc": "Require Touch ID, Apple Watch, or your macOS password when opening Thingsbook (off by default).",
        "macos_app_lock_subtitle": "Authenticate with Touch ID, Apple Watch, or your macOS password to access your To-Dos.",
        "macos_calendar_section": "Apple Calendar & Google Calendar Events",
        "macos_calendar_enable_desc": "Display read-only events from selected macOS Calendar accounts (iCloud, Google, Exchange) in Today and Upcoming (off by default).",
        "macos_service_save_title": "Save to Thingsbook",
        "macos_quick_entry_shortcut_hint": "Tip: Use ⌘N for a new To-Do, ⇧⌘N for a new Project, or click the Thingsbook icon in your macOS Menu Bar anytime.",
        "macos_copy_suffix": "Copy",
        "macos_instant_sync_btn": "Instant Email Sync (Passwordless)",
        "macos_view_menu": "View",
        "macos_todo_menu": "To-Do",
        "macos_go_menu": "Go",
        "macos_intelligence_menu": "Intelligence",
        "macos_toggle_sidebar": "Toggle Sidebar",
        "macos_new_heading": "New Heading",
        "macos_new_area": "New Area…",
        "macos_log_completed": "Log Completed Now",
        "macos_complete_project": "Complete Project",
        "macos_delete_project": "Move Project to Trash",
    },
    "de": {
        "macos_menubar_quick_entry": "Schnelleingabe…",
        "macos_menubar_open_app": "Thingsbook öffnen",
        "macos_menubar_today_header": "Heute im Fokus",
        "macos_menubar_quit": "Thingsbook beenden",
        "macos_touch_id_title": "Touch ID / Systempasswort-Sperre",
        "macos_touch_id_desc": "Beim Öffnen von Thingsbook Touch ID, Apple Watch oder dein macOS-Passwort anfordern (standardmäßig deaktiviert).",
        "macos_app_lock_subtitle": "Bitte mit Touch ID, Apple Watch oder deinem macOS-Passwort entsperren.",
        "macos_calendar_section": "Apple Kalender & Google Kalender",
        "macos_calendar_enable_desc": "Termine aus ausgewählten macOS-Kalendern (iCloud, Google, Exchange) in „Heute“ und „Geplant“ einblenden (standardmäßig deaktiviert).",
        "macos_service_save_title": "In Thingsbook speichern",
        "macos_quick_entry_shortcut_hint": "Tipp: Nutze ⌘N für eine neue Aufgabe, ⇧⌘N für ein neues Projekt oder jederzeit das Thingsbook-Symbol in der macOS-Menüleiste.",
        "macos_copy_suffix": "Kopie",
        "macos_instant_sync_btn": "Sofort-Sync per E-Mail (ohne Passwort)",
        "macos_view_menu": "Darstellung",
        "macos_todo_menu": "Aufgabe",
        "macos_go_menu": "Gehe zu",
        "macos_intelligence_menu": "Intelligence",
        "macos_toggle_sidebar": "Seitenleiste ein-/ausblenden",
        "macos_new_heading": "Neue Überschrift",
        "macos_new_area": "Neuer Bereich…",
        "macos_log_completed": "Erledigte jetzt archivieren",
        "macos_complete_project": "Projekt abschließen",
        "macos_delete_project": "Projekt in den Papierkorb bewegen",
    },
    "fr": {
        "macos_menubar_quick_entry": "Saisie rapide…",
        "macos_menubar_open_app": "Ouvrir Thingsbook",
        "macos_menubar_today_header": "Priorités du jour",
        "macos_menubar_quit": "Quitter Thingsbook",
        "macos_touch_id_title": "Verrouillage Touch ID / Mot de passe",
        "macos_touch_id_desc": "Exiger Touch ID, Apple Watch ou votre mot de passe macOS à l'ouverture (désactivé par défaut).",
        "macos_app_lock_subtitle": "Authentifiez-vous avec Touch ID, Apple Watch ou votre mot de passe macOS pour accéder à vos tâches.",
        "macos_calendar_section": "Calendrier Apple & Google Agenda",
        "macos_calendar_enable_desc": "Afficher les événements des calendriers macOS sélectionnés (iCloud, Google, Exchange) dans Aujourd'hui et À venir (désactivé par défaut).",
        "macos_service_save_title": "Enregistrer dans Thingsbook",
        "macos_quick_entry_shortcut_hint": "Astuce : utilisez ⌘N pour une tâche, ⇧⌘N pour un projet ou l'icône Thingsbook dans la barre des menus macOS.",
        "macos_copy_suffix": "Copie",
        "macos_instant_sync_btn": "Sync instantanée par e-mail",
        "macos_view_menu": "Présentation",
        "macos_todo_menu": "Tâche",
        "macos_go_menu": "Aller",
        "macos_intelligence_menu": "Intelligence",
        "macos_toggle_sidebar": "Afficher/Masquer la barre latérale",
        "macos_new_heading": "Nouveau titre",
        "macos_new_area": "Nouveau domaine…",
        "macos_log_completed": "Archiver les tâches terminées",
        "macos_complete_project": "Terminer le projet",
        "macos_delete_project": "Déplacer le projet vers la corbeille",
    },
    "it": {
        "macos_menubar_quick_entry": "Inserimento rapido…",
        "macos_menubar_open_app": "Apri Thingsbook",
        "macos_menubar_today_header": "Focus di Oggi",
        "macos_menubar_quit": "Esci da Thingsbook",
        "macos_touch_id_title": "Blocco con Touch ID / Password",
        "macos_touch_id_desc": "Richiedi Touch ID, Apple Watch o la password di macOS all'apertura (disattivato di default).",
        "macos_app_lock_subtitle": "Autenticati con Touch ID, Apple Watch o la password di macOS per accedere alle tue attività.",
        "macos_calendar_section": "Calendario Apple & Google Calendar",
        "macos_calendar_enable_desc": "Mostra gli eventi dei calendari macOS selezionati (iCloud, Google, Exchange) in Oggi e Programmati (disattivato di default).",
        "macos_service_save_title": "Salva in Thingsbook",
        "macos_quick_entry_shortcut_hint": "Suggerimento: usa ⌘N per una nuova attività, ⇧⌘N per un progetto o l'icona Thingsbook nella barra dei menu.",
        "macos_copy_suffix": "Copia",
        "macos_instant_sync_btn": "Sync istantanea via e-mail",
        "macos_view_menu": "Vista",
        "macos_todo_menu": "Attività",
        "macos_go_menu": "Vai",
        "macos_intelligence_menu": "Intelligence",
        "macos_toggle_sidebar": "Mostra/Nascondi barra laterale",
        "macos_new_heading": "Nuova intestazione",
        "macos_new_area": "Nuova area…",
        "macos_log_completed": "Archivia completate ora",
        "macos_complete_project": "Completa progetto",
        "macos_delete_project": "Sposta progetto nel cestino",
    },
    "es": {
        "macos_menubar_quick_entry": "Entrada rápida…",
        "macos_menubar_open_app": "Abrir Thingsbook",
        "macos_menubar_today_header": "Enfoque de Hoy",
        "macos_menubar_quit": "Salir de Thingsbook",
        "macos_touch_id_title": "Bloqueo con Touch ID / Contraseña",
        "macos_touch_id_desc": "Requerir Touch ID, Apple Watch o tu contraseña de macOS al abrir Thingsbook (desactivado por defecto).",
        "macos_app_lock_subtitle": "Autentícate con Touch ID, Apple Watch o tu contraseña de macOS para acceder a tus tareas.",
        "macos_calendar_section": "Calendario de Apple y Google Calendar",
        "macos_calendar_enable_desc": "Mostrar eventos de los calendarios de macOS seleccionados (iCloud, Google, Exchange) en Hoy y Programado (desactivado por defecto).",
        "macos_service_save_title": "Guardar en Thingsbook",
        "macos_quick_entry_shortcut_hint": "Consejo: usa ⌘N para una tarea, ⇧⌘N para un proyecto o el icono de Thingsbook en la barra de menús.",
        "macos_copy_suffix": "Copia",
        "macos_instant_sync_btn": "Sincronización instantánea por correo",
        "macos_view_menu": "Visualización",
        "macos_todo_menu": "Tarea",
        "macos_go_menu": "Ir",
        "macos_intelligence_menu": "Intelligence",
        "macos_toggle_sidebar": "Mostrar/Ocultar barra lateral",
        "macos_new_heading": "Nuevo encabezado",
        "macos_new_area": "Nueva área…",
        "macos_log_completed": "Registrar completadas ahora",
        "macos_complete_project": "Completar proyecto",
        "macos_delete_project": "Mover proyecto a la papelera",
    },
}


def unescape_android_xml(raw: str) -> str:
    text = raw.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    # Convert Ctrl+ hints to macOS ⌘ symbol in shortcuts display while preserving meaning
    text = re.sub(r"\bCtrl\+A\b", "⌘A", text)
    text = re.sub(r"\bStrg\+A\b", "⌘A", text)
    return text


def parse_strings_xml(path: Path) -> dict[str, str]:
    tree = ET.parse(path)
    root = tree.getroot()
    out: dict[str, str] = {}
    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        if not name:
            continue
        raw_text = "".join(elem.itertext())
        out[name] = unescape_android_xml(raw_text)
    return out


def main() -> int:
    all_langs: dict[str, dict[str, str]] = {}
    for lang, xml_path in LANG_DIRS.items():
        if not xml_path.is_file():
            print(f"ERROR: Missing Android strings file: {xml_path}", file=sys.stderr)
            return 1
        parsed = parse_strings_xml(xml_path)
        parsed.update(MACOS_EXTRA_STRINGS[lang])
        all_langs[lang] = dict(sorted(parsed.items()))

    en_keys = set(all_langs["en"].keys())
    for lang, table in all_langs.items():
        diff = en_keys.symmetric_difference(table.keys())
        if diff:
            print(f"ERROR: Key mismatch in {lang}: {sorted(diff)}", file=sys.stderr)
            return 1

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    CORE_OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    formatted = json.dumps(all_langs, ensure_ascii=False, indent=2) + "\n"
    OUT_JSON.write_text(formatted, encoding="utf-8")
    CORE_OUT_JSON.write_text(formatted, encoding="utf-8")
    print(f"Synced {len(en_keys)} localization keys across {len(all_langs)} languages -> {OUT_JSON} & {CORE_OUT_JSON}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
