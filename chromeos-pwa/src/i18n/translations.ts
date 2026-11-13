import type { SupportedLang } from '../domain/types';

export interface TranslationStrings {
  appName: string;
  viewInbox: string;
  viewToday: string;
  viewThisEvening: string;
  viewUpcoming: string;
  viewAnytime: string;
  viewSomeday: string;
  viewLogbook: string;
  entityArea: string;
  entityAreas: string;
  entityNewArea: string;
  entityProject: string;
  entityProjects: string;
  entityNewProject: string;
  entityHeading: string;
  entityNewHeading: string;
  entityTodo: string;
  entityTodos: string;
  entityNewTodo: string;
  entityTag: string;
  entityTags: string;
  placeholderTodoTitle: string;
  placeholderNotes: string;
  placeholderAddChecklist: string;
  actionWhen: string;
  actionDeadline: string;
  actionTags: string;
  actionChecklist: string;
  actionReminder: string;
  actionMove: string;
  actionRepeat: string;
  actionComplete: string;
  actionCancelTodo: string;
  actionReopenTodo: string;
  actionDuplicate: string;
  actionDelete: string;
  actionClear: string;
  actionDone: string;
  actionSave: string;
  actionCancel: string;
  actionEdit: string;
  actionRename: string;
  quickFindTitle: string;
  quickFindPlaceholder: string;
  quickFindNoResults: string;
  quickFindSmartViews: string;
  quickFindHits: (n: number) => string;
  deadlineToday: string;
  deadlineDaysLeft: (d: number) => string;
  deadlineDaysOverdue: (d: number) => string;
  dateTomorrow: string;
  dateNextWeek: string;
  dateInTwoWeeks: string;
  targetDate: string;
  setTargetDate: string;
  assignArea: string;
  noProjectNoArea: string;
  whenNaturalHint: string;
  syncTitle: string;
  syncStatusSynced: string;
  syncStatusSyncing: string;
  syncStatusOffline: string;
  syncNow: string;
  authEmailLabel: string;
  authPasswordLabel: string;
  authRegisterAccount: string;
  authLoginEmail: string;
  authSignInGoogle: string;
  authInstantCloud: string;
  authSignOut: string;
  authForgotPassword: string;
  authMultiDeviceHint: string;
  settingsTitle: string;
  settingsSubtitle: string;
  settingsLanguage: string;
  settingsSystemDefault: string;
  settingsTheme: string;
  settingsThemeSystem: string;
  settingsThemeLight: string;
  settingsThemeDark: string;
  settingsDbSyncSection: string;
  settingsLocalDbTitle: string;
  settingsLocalDbDesc: string;
  settingsCloudSyncTitle: string;
  settingsCloudSyncDesc: string;
  settingsAccountStatus: string;
  settingsNotSignedIn: string;
  settingsOpenAccountSync: string;
  settingsUpdatesSection: string;
  settingsAutoUpdateLabel: string;
  settingsSearchUpdatesBtn: string;
  settingsCheckingUpdates: string;
  settingsUpToDate: (v: string) => string;
  settingsUpdateAvailable: (v: string) => string;
  settingsApplyUpdateBtn: string;
  settingsStatsSection: string;
  statOpen: string;
  statCompleted: string;
  statCanceled: string;
  statDeleted: string;
  statTodayActive: string;
  statUpcoming: string;
  statAnytime: string;
  statInbox: string;
  statSomeday: string;
  statRecurring: string;
  statActiveProjects: string;
  statAreas: string;
  statSavedTags: string;
  statTotalRecords: string;
  backupSectionTitle: string;
  backupSectionDesc: string;
  backupCreateBtn: string;
  backupRestoreBtn: string;
  backupPasswordProtect: string;
  backupPasswordLabel: string;
  backupPasswordConfirm: string;
  backupSaveToFile: string;
  backupShareBtn: string;
  backupRestoreConfirm: string;
  backupOverwriteWarning: string;
  backupSyncAfterRestore: string;
  csvSectionTitle: string;
  csvSectionDesc: string;
  csvExportBtn: string;
  csvShareBtn: string;
  csvImportBtn: string;
  emptyInbox: string;
  emptyToday: string;
  emptyView: string;
  geminiHubTitle: string;
  geminiMagicBreakdown: string;
  geminiBrainDumpTitle: string;
  geminiBrainDumpPlaceholder: string;
  geminiBrainDumpAction: string;
  geminiArchitectTitle: string;
  geminiArchitectPlaceholder: string;
  geminiArchitectAction: string;
  geminiReflectionTitle: string;
  geminiReflectionAction: string;
  geminiGenerating: string;
  geminiApiKeyLabel: string;
  geminiApiKeyPlaceholder: string;
  setupBannerTitle: string;
  setupBannerDesc: string;
  setupBannerConnectBtn: string;
  setupBannerDeleteSamplesBtn: string;
  mergeDialogTitle: string;
  mergeDialogDesc: string;
  mergeKeepBothBtn: string;
  mergeAccountOnlyBtn: string;
  installChromeOsBtn: string;
  installChromeOsInstalled: string;
  installChromeOsHint: string;
  tagsManageTitle: string;
  tagsNewPlaceholder: string;
  tagsShortcutLabel: string;
  tagsAddBtn: string;
  tagsFilterAll: string;
  areasManageTitle: string;
  areaSuggestions: string[];
  repeatFixedSchedule: string;
  repeatAfterCompletion: string;
  repeatClearBtn: string;
  repeatEndsNever: string;
  repeatEndsOn: string;
  repeatLastDay: string;
  notificationsTitle: string;
  notificationsDailyTitle: string;
  notificationsTestBtn: string;
  shortcutCancelHint: string;
  magicTodoSubDefault: string;
  magicHeadingSub: string;
  magicProjectSub: string;
  magicAreaSub: string;
  ctxSelect: string;
  ctxDeselect: string;
  ctxWhenReminder: string;
  ctxMoveContainer: string;
  ctxAssignTags: string;
  multiSelectAll: string;
  multiSelectCount: (n: number) => string;
  multiSelectSelectAllTooltip: string;
  multiSelectClearTooltip: string;
  repeatDialogTitle: string;
  repeatModeFixedDesc: string;
  repeatModeAfterDesc: string;
  repeatWeekdaysLabel: string;
  repeatMonthDayLabel: string;
  repeatEndsLabel: string;
  sidebarShow: string;
  sidebarHide: string;
  settingsCalendarSection: string;
  settingsCalendarEnableTitle: string;
  settingsCalendarEnableDesc: string;
  settingsCalendarSelectHint: (selected: number, total: number) => string;
  settingsCalendarSelectAll: string;
  settingsCalendarSelectNone: string;
  settingsCalendarNoneFound: string;
  settingsCalendarPermissionDenied: string;
  settingsCalendarGrantPermissionBtn: string;
  settingsCalendarImportIcsBtn: string;
  calendarAllDay: string;
  calendarUntitledEvent: string;
}

export const TRANSLATIONS: Record<SupportedLang, TranslationStrings> = {
  en: {
    appName: 'Thingsbook',
    viewInbox: 'Inbox',
    viewToday: 'Today',
    viewThisEvening: 'This Evening',
    viewUpcoming: 'Upcoming',
    viewAnytime: 'Anytime',
    viewSomeday: 'Someday',
    viewLogbook: 'Logbook',
    entityArea: 'Area',
    entityAreas: 'Areas',
    entityNewArea: 'New Area',
    entityProject: 'Project',
    entityProjects: 'Projects',
    entityNewProject: 'New Project',
    entityHeading: 'Heading',
    entityNewHeading: 'New Heading',
    entityTodo: 'To-Do',
    entityTodos: 'To-Dos',
    entityNewTodo: 'New To-Do',
    entityTag: 'Tag',
    entityTags: 'Tags',
    placeholderTodoTitle: 'New To-Do',
    placeholderNotes: 'Notes',
    placeholderAddChecklist: 'Add checklist item…',
    actionWhen: 'When',
    actionDeadline: 'Deadline',
    actionTags: 'Tags',
    actionChecklist: 'Checklist',
    actionReminder: 'Reminder',
    actionMove: 'Move',
    actionRepeat: 'Repeat',
    actionComplete: 'Complete',
    actionCancelTodo: 'Mark as Canceled',
    actionReopenTodo: 'Reopen To-Do',
    actionDuplicate: 'Duplicate',
    actionDelete: 'Delete',
    actionClear: 'Clear',
    actionDone: 'Done',
    actionSave: 'Save',
    actionCancel: 'Cancel',
    actionEdit: 'Edit',
    actionRename: 'Rename',
    quickFindTitle: 'Quick Find',
    quickFindPlaceholder: 'Quick Find — Search To-Dos, Projects, Areas, or Tags…',
    quickFindNoResults: 'No matching To-Dos, Projects, Areas, or Views',
    quickFindSmartViews: 'Views',
    quickFindHits: (n) => `${n} ${n === 1 ? 'result' : 'results'}`,
    deadlineToday: 'Today',
    deadlineDaysLeft: (d) => `${d}d left`,
    deadlineDaysOverdue: (d) => `${d}d overdue`,
    dateTomorrow: 'Tomorrow',
    dateNextWeek: 'Next Week (+7d)',
    dateInTwoWeeks: 'In 2 Weeks (+14d)',
    targetDate: 'Target Date',
    setTargetDate: '+ Set Target Date',
    assignArea: 'Assign Area',
    noProjectNoArea: 'No Project / No Area',
    whenNaturalHint: 'Type e.g. "tomorrow", "this evening", "next friday", "in 3 days"…',
    syncTitle: 'Thingsbook Cloud Sync',
    syncStatusSynced: 'Synced with Cloud',
    syncStatusSyncing: 'Syncing deltas…',
    syncStatusOffline: 'Offline-First (Local IndexedDB)',
    syncNow: 'Sync Now',
    authEmailLabel: 'Email Address',
    authPasswordLabel: 'Password (min. 6 chars)',
    authRegisterAccount: 'Create Account & Sync',
    authLoginEmail: 'Sign In with Email',
    authSignInGoogle: 'Continue with Google',
    authInstantCloud: 'Instant Email Sync (Passwordless)',
    authSignOut: 'Sign Out',
    authForgotPassword: 'Forgot password? Reset',
    authMultiDeviceHint:
      'Sign in with the same Email or Google account as on your Android device for live bidirectional Firestore Delta-Sync.',
    settingsTitle: 'Settings',
    settingsSubtitle: 'Database, Cloud Sync, Language, Updates, Statistics, Backup & CSV',
    settingsLanguage: 'App Language',
    settingsSystemDefault: 'System Default',
    settingsTheme: 'Appearance',
    settingsThemeSystem: 'System',
    settingsThemeLight: 'Light',
    settingsThemeDark: 'Dark',
    settingsDbSyncSection: 'Database & Synchronization',
    settingsLocalDbTitle: 'Local DB',
    settingsLocalDbDesc: '100% offline on this Chromebook (IndexedDB)',
    settingsCloudSyncTitle: 'Cloud Sync',
    settingsCloudSyncDesc: 'Local DB + real-time Firestore sync across Android & ChromeOS',
    settingsAccountStatus: 'Account & Cloud Status',
    settingsNotSignedIn: 'Not signed in',
    settingsOpenAccountSync: 'Open Account & Sync',
    settingsUpdatesSection: 'App Updates',
    settingsAutoUpdateLabel: 'Automatically check for app updates',
    settingsSearchUpdatesBtn: 'Search for Updates',
    settingsCheckingUpdates: 'Checking for updates…',
    settingsUpToDate: (v) => `Thingsbook v${v} is up to date.`,
    settingsUpdateAvailable: (v) => `Update v${v} is available!`,
    settingsApplyUpdateBtn: 'Install & Reload Now',
    settingsStatsSection: 'Statistics (To-Dos & Inventory)',
    statOpen: 'Open',
    statCompleted: 'Completed',
    statCanceled: 'Canceled',
    statDeleted: 'Deleted',
    statTodayActive: '★ Active Today',
    statUpcoming: '📅 Upcoming',
    statAnytime: '🗂️ Anytime',
    statInbox: '📥 Inbox',
    statSomeday: '📦 Someday',
    statRecurring: '🔁 Recurring',
    statActiveProjects: '📁 Active Projects',
    statAreas: '🗂️ Areas',
    statSavedTags: '🏷️ Saved Tags',
    statTotalRecords: '🗄️ Total Records',
    backupSectionTitle: 'Backup & Restore (.tbbackup)',
    backupSectionDesc:
      'Full backup of all To-Dos, Areas, Projects, Headings, Tags & Checklists — 100% compatible with Android Thingsbook and optional AES-256-GCM password encryption.',
    backupCreateBtn: 'Create Backup',
    backupRestoreBtn: 'Restore from File',
    backupPasswordProtect: 'Protect with AES-256-GCM password',
    backupPasswordLabel: 'Backup password (min. 4 chars)',
    backupPasswordConfirm: 'Confirm password',
    backupSaveToFile: 'Save to Device (.tbbackup)',
    backupShareBtn: 'Share Backup',
    backupRestoreConfirm: 'Restore Everything Now',
    backupOverwriteWarning:
      'Restoring replaces all current local To-Dos, Projects, Areas, Headings, and Tags on this device.',
    backupSyncAfterRestore: 'Push restored items to Cloud Sync immediately',
    csvSectionTitle: 'Import & Export (CSV)',
    csvSectionDesc:
      'Export all To-Dos (including status, area, project, date, deadline, tags & checklist) as CSV or import an existing CSV file.',
    csvExportBtn: 'Export CSV',
    csvShareBtn: 'Share CSV',
    csvImportBtn: 'Import CSV',
    emptyInbox: 'Your Inbox is empty.',
    emptyToday: 'No To-Dos scheduled for Today.',
    emptyView: 'No items in this view right now.',
    geminiHubTitle: 'Thingsbook Intelligence (Gemini)',
    geminiMagicBreakdown: 'Magic Breakdown',
    geminiBrainDumpTitle: 'Brain Dump → GTD Import',
    geminiBrainDumpPlaceholder:
      'Type or paste anything on your mind (e.g. "Call dentist tomorrow, finish Q4 deck by Friday, buy espresso beans this evening")…',
    geminiBrainDumpAction: 'Extract & Schedule To-Dos',
    geminiArchitectTitle: 'AI Project Architect',
    geminiArchitectPlaceholder:
      'Describe your project goal (e.g. "Relocate to Munich in autumn" or "Plan summer trip to Italy")…',
    geminiArchitectAction: 'Generate Project with Headings & To-Dos',
    geminiReflectionTitle: 'Thingsbook AI Reflection',
    geminiReflectionAction: 'Generate Review',
    geminiGenerating: 'Gemini is thinking calmly…',
    geminiApiKeyLabel: 'Optional Gemini API Key (overrides Firebase AI)',
    geminiApiKeyPlaceholder: 'Paste AIza… key or leave blank for Firebase AI / Offline GTD Engine',
    setupBannerTitle: 'Welcome to Thingsbook for ChromeOS',
    setupBannerDesc:
      'Connect your Thingsbook Cloud account to sync live with Android, or clear the sample tasks for a fresh local workspace.',
    setupBannerConnectBtn: 'Connect Cloud Account',
    setupBannerDeleteSamplesBtn: 'Delete Sample Tasks',
    mergeDialogTitle: 'Sample Tasks & Cloud Sync',
    mergeDialogDesc:
      'Pre-installed sample tasks will be removed automatically so they never pollute your cloud account. How should locally created tasks be handled?',
    mergeKeepBothBtn: 'Merge local tasks with Cloud account',
    mergeAccountOnlyBtn: 'Load Cloud account tasks only',
    installChromeOsBtn: 'Add to ChromeOS Launcher',
    installChromeOsInstalled: 'Installed in ChromeOS Start Menu',
    installChromeOsHint:
      'Click the Install icon (⊕) in Chrome’s address bar to pin Thingsbook to your ChromeOS Start Menu & Shelf — works 100% offline.',
    tagsManageTitle: 'Manage Tags',
    tagsNewPlaceholder: 'Tag name (e.g. Urgent, Home, Office)…',
    tagsShortcutLabel: 'Shortcut (Ctrl+…)',
    tagsAddBtn: 'Create Tag',
    tagsFilterAll: 'All',
    areasManageTitle: 'Manage Areas',
    areaSuggestions: ['Work', 'Home', 'Finance', 'Health', 'Family', 'Study', 'Sport', 'Hobby'],
    repeatFixedSchedule: 'Regularly',
    repeatAfterCompletion: 'After Completion',
    repeatClearBtn: 'Off',
    repeatEndsNever: 'Never',
    repeatEndsOn: 'Pick date…',
    repeatLastDay: 'Last day',
    notificationsTitle: 'Notifications & Reminders',
    notificationsDailyTitle: 'Daily Today Summary Notification',
    notificationsTestBtn: 'Send Test Notification',
    shortcutCancelHint: 'Tip: Right-Click or Alt+Click a checkbox to cancel or reopen a To-Do',
    magicTodoSubDefault: 'Create in current view',
    magicHeadingSub: 'Add a section heading inside this project',
    magicProjectSub: 'Multi-step goal with progress ring & headings',
    magicAreaSub: 'Group related projects & responsibilities',
    ctxSelect: 'Select To-Do (Multi-select)',
    ctxDeselect: 'Clear selection',
    ctxWhenReminder: 'When? (Date & Reminder)',
    ctxMoveContainer: 'Move (Area / Project)',
    ctxAssignTags: 'Assign Tags',
    multiSelectAll: 'All',
    multiSelectCount: (n) => `${n} selected`,
    multiSelectSelectAllTooltip: 'Select all in this view (Ctrl+A)',
    multiSelectClearTooltip: 'Clear selection (Esc)',
    repeatDialogTitle: 'Repeat (Things Mode)',
    repeatModeFixedDesc: 'Fixed calendar schedule (like "Regularly" in Things for Mac).',
    repeatModeAfterDesc: 'Next To-Do is created only after this one is completed.',
    repeatWeekdaysLabel: 'On these weekdays:',
    repeatMonthDayLabel: 'Day of month:',
    repeatEndsLabel: 'Ends:',
    sidebarShow: 'Show sidebar',
    sidebarHide: 'Hide sidebar',
    settingsCalendarSection: 'Google Calendar & Calendar Events',
    settingsCalendarEnableTitle: 'Show Calendar Events',
    settingsCalendarEnableDesc:
      'Display read-only events from selected Google & device calendars in Today and Upcoming (off by default).',
    settingsCalendarSelectHint: (sel, total) => `Select calendars to display (${sel}/${total}):`,
    settingsCalendarSelectAll: 'All',
    settingsCalendarSelectNone: 'None',
    settingsCalendarNoneFound: 'No Google or device calendars found on this device.',
    settingsCalendarPermissionDenied:
      'Calendar permission is required to list and display your calendars.',
    settingsCalendarGrantPermissionBtn: 'Grant Calendar Access',
    settingsCalendarImportIcsBtn: 'Import .ics',
    calendarAllDay: 'All-day',
    calendarUntitledEvent: '(No title)'
  },
  de: {
    appName: 'Thingsbook',
    viewInbox: 'Eingang',
    viewToday: 'Heute',
    viewThisEvening: 'Heute Abend',
    viewUpcoming: 'Geplant',
    viewAnytime: 'Jederzeit',
    viewSomeday: 'Irgendwann',
    viewLogbook: 'Logbuch',
    entityArea: 'Bereich',
    entityAreas: 'Bereiche',
    entityNewArea: 'Neuer Bereich',
    entityProject: 'Projekt',
    entityProjects: 'Projekte',
    entityNewProject: 'Neues Projekt',
    entityHeading: 'Überschrift',
    entityNewHeading: 'Neue Überschrift',
    entityTodo: 'Aufgabe',
    entityTodos: 'Aufgaben',
    entityNewTodo: 'Neue Aufgabe',
    entityTag: 'Tag',
    entityTags: 'Tags',
    placeholderTodoTitle: 'Neue Aufgabe',
    placeholderNotes: 'Notizen',
    placeholderAddChecklist: 'Checklistenpunkt hinzufügen…',
    actionWhen: 'Wann',
    actionDeadline: 'Frist',
    actionTags: 'Tags',
    actionChecklist: 'Checkliste',
    actionReminder: 'Erinnerung',
    actionMove: 'Bewegen',
    actionRepeat: 'Wiederholen',
    actionComplete: 'Erledigen',
    actionCancelTodo: 'Als abgebrochen markieren',
    actionReopenTodo: 'Wieder öffnen',
    actionDuplicate: 'Duplizieren',
    actionDelete: 'Löschen',
    actionClear: 'Leeren',
    actionDone: 'Fertig',
    actionSave: 'Speichern',
    actionCancel: 'Abbrechen',
    actionEdit: 'Bearbeiten',
    actionRename: 'Umbenennen',
    quickFindTitle: 'Schnellsuche',
    quickFindPlaceholder: 'Schnellsuche – Aufgaben, Projekte, Bereiche oder Tags suchen…',
    quickFindNoResults: 'Keine passenden Aufgaben, Projekte, Bereiche oder Ansichten',
    quickFindSmartViews: 'Ansichten',
    quickFindHits: (n) => `${n} ${n === 1 ? 'Treffer' : 'Treffer'}`,
    deadlineToday: 'Heute',
    deadlineDaysLeft: (d) => `Noch ${d} Tg.`,
    deadlineDaysOverdue: (d) => `${d} Tg. überfällig`,
    dateTomorrow: 'Morgen',
    dateNextWeek: 'Nächste Woche (+7T)',
    dateInTwoWeeks: 'In 2 Wochen (+14T)',
    targetDate: 'Zieldatum',
    setTargetDate: '+ Zieldatum setzen',
    assignArea: 'Bereich zuordnen',
    noProjectNoArea: 'Kein Projekt / Kein Bereich',
    whenNaturalHint: 'Tippe z. B. „morgen“, „heute abend“, „nächsten freitag“, „in 3 tagen“…',
    syncTitle: 'Thingsbook Cloud-Sync',
    syncStatusSynced: 'Mit Cloud synchronisiert',
    syncStatusSyncing: 'Synchronisiere Deltas…',
    syncStatusOffline: 'Offline-First (Lokale IndexedDB)',
    syncNow: 'Jetzt synchronisieren',
    authEmailLabel: 'E-Mail-Adresse',
    authPasswordLabel: 'Passwort (mind. 6 Zeichen)',
    authRegisterAccount: 'Account anlegen & synchronisieren',
    authLoginEmail: 'Mit E-Mail anmelden',
    authSignInGoogle: 'Weiter mit Google',
    authInstantCloud: 'Sofort-Sync per E-Mail (ohne Passwort)',
    authSignOut: 'Abmelden',
    authForgotPassword: 'Passwort vergessen? Zurücksetzen',
    authMultiDeviceHint:
      'Melde dich mit derselben E-Mail oder Google-ID wie auf deinem Android-Gerät an, um alle Aufgaben in Echtzeit per Firestore Delta-Sync abzugleichen.',
    settingsTitle: 'Einstellungen',
    settingsSubtitle: 'Datenbank, Cloud-Konto, Sprache, Updates, Statistik, Backup & CSV',
    settingsLanguage: 'App-Sprache',
    settingsSystemDefault: 'Systemstandard',
    settingsTheme: 'Erscheinungsbild',
    settingsThemeSystem: 'System',
    settingsThemeLight: 'Hell',
    settingsThemeDark: 'Dunkel',
    settingsDbSyncSection: 'Datenbank & Synchronisierung',
    settingsLocalDbTitle: 'Lokale DB',
    settingsLocalDbDesc: '100% Offline auf diesem Chromebook (IndexedDB)',
    settingsCloudSyncTitle: 'Cloud-Sync',
    settingsCloudSyncDesc: 'Lokale DB + Echtzeit-Sync zwischen Android & ChromeOS',
    settingsAccountStatus: 'Konto & Cloud-Status',
    settingsNotSignedIn: 'Nicht angemeldet',
    settingsOpenAccountSync: 'Konto & Sync öffnen',
    settingsUpdatesSection: 'App-Updates',
    settingsAutoUpdateLabel: 'Automatisch nach App-Updates suchen',
    settingsSearchUpdatesBtn: 'Nach Updates suchen',
    settingsCheckingUpdates: 'Suche nach Updates…',
    settingsUpToDate: (v) => `Thingsbook v${v} ist auf dem neuesten Stand.`,
    settingsUpdateAvailable: (v) => `Update v${v} ist verfügbar!`,
    settingsApplyUpdateBtn: 'Jetzt aktualisieren & neu laden',
    settingsStatsSection: 'Statistiken (Aufgaben & Bestand)',
    statOpen: 'Aktuell offen',
    statCompleted: 'Erledigt',
    statCanceled: 'Abgebrochen',
    statDeleted: 'Gelöscht',
    statTodayActive: '★ Heute aktiv',
    statUpcoming: '📅 Geplant',
    statAnytime: '🗂️ Jederzeit',
    statInbox: '📥 Eingang',
    statSomeday: '📦 Irgendwann',
    statRecurring: '🔁 Wiederkehrend',
    statActiveProjects: '📁 Aktive Projekte',
    statAreas: '🗂️ Bereiche',
    statSavedTags: '🏷️ Gespeicherte Tags',
    statTotalRecords: '🗄️ Einträge gesamt',
    backupSectionTitle: 'Backup & Restore (.tbbackup)',
    backupSectionDesc:
      'Sichert alle Aufgaben, Bereiche, Projekte, Überschriften, Tags & Checklisten in einer .tbbackup-Datei — 100 % kompatibel mit Android Thingsbook inkl. AES-256-GCM Passwortschutz.',
    backupCreateBtn: 'Backup erstellen',
    backupRestoreBtn: 'Restore aus Datei',
    backupPasswordProtect: 'Mit AES-256-GCM Passwort schützen',
    backupPasswordLabel: 'Backup-Passwort (mind. 4 Zeichen)',
    backupPasswordConfirm: 'Passwort wiederholen',
    backupSaveToFile: 'Auf Gerät speichern (.tbbackup)',
    backupShareBtn: 'Backup teilen',
    backupRestoreConfirm: 'Jetzt komplett wiederherstellen',
    backupOverwriteWarning:
      'Beim Wiederherstellen werden alle aktuellen lokalen Aufgaben, Projekte, Bereiche, Überschriften und Tags auf diesem Gerät ersetzt.',
    backupSyncAfterRestore: 'Wiederhergestellte Daten sofort mit Cloud-Sync abgleichen',
    csvSectionTitle: 'Import & Export (CSV-Datei)',
    csvSectionDesc:
      'Exportiere alle Aufgaben (inkl. Status, Bereich, Projekt, Datum, Frist, Tags & Checkliste) als CSV-Datei oder importiere bestehende CSV-Dateien.',
    csvExportBtn: 'CSV speichern',
    csvShareBtn: 'CSV teilen',
    csvImportBtn: 'CSV importieren',
    emptyInbox: 'Dein Eingang ist leer.',
    emptyToday: 'Heute stehen keine Aufgaben an.',
    emptyView: 'Aktuell keine Einträge in dieser Ansicht.',
    geminiHubTitle: 'Thingsbook Intelligence (Gemini)',
    geminiMagicBreakdown: 'Magische Teilaufgaben',
    geminiBrainDumpTitle: 'Brain Dump → GTD-Import',
    geminiBrainDumpPlaceholder:
      'Schreibe frei von der Leber weg (z. B. „Morgen Zahnarzt anrufen, bis Freitag Q4-Präsentation fertigstellen, heute Abend Espressobohnen kaufen“)…',
    geminiBrainDumpAction: 'Aufgaben erkennen & einplanen',
    geminiArchitectTitle: 'KI-Projekt-Architekt',
    geminiArchitectPlaceholder:
      'Beschreibe dein Ziel (z. B. „Umzug nach München im Herbst“ oder „Sommerurlaub in Italien planen“)…',
    geminiArchitectAction: 'Projekt mit Überschriften & Aufgaben erstellen',
    geminiReflectionTitle: 'Thingsbook KI-Rückblick',
    geminiReflectionAction: 'Rückblick erstellen',
    geminiGenerating: 'Gemini strukturiert deine Gedanken…',
    geminiApiKeyLabel: 'Optionaler Gemini API-Schlüssel (überschreibt Firebase AI)',
    geminiApiKeyPlaceholder: 'AIza… Schlüssel einfügen oder für Firebase AI leer lassen',
    setupBannerTitle: 'Willkommen bei Thingsbook für ChromeOS',
    setupBannerDesc:
      'Verbinde dein Cloud-Konto für den Live-Abgleich mit Android oder lösche die Beispielaufgaben für einen leeren Arbeitsbereich.',
    setupBannerConnectBtn: 'Cloud-Konto verbinden',
    setupBannerDeleteSamplesBtn: 'Beispielaufgaben löschen',
    mergeDialogTitle: 'Beispielaufgaben & Cloud-Sync',
    mergeDialogDesc:
      'Vorinstallierte Demo-Aufgaben werden automatisch entfernt, damit dein Cloud-Konto sauber bleibt. Wie soll mit lokal erstellten Aufgaben verfahren werden?',
    mergeKeepBothBtn: 'Lokale Aufgaben mit Cloud zusammenführen',
    mergeAccountOnlyBtn: 'Nur Aufgaben aus dem Cloud-Konto laden',
    installChromeOsBtn: 'Zum ChromeOS-Startmenü hinzufügen',
    installChromeOsInstalled: 'Im ChromeOS-Startmenü installiert',
    installChromeOsHint:
      'Klicke oben in der Chrome-Adressleiste auf das Installieren-Symbol (⊕), um Thingsbook im ChromeOS-Startmenü & auf der Ablage anzuheften — funktioniert zu 100 % offline.',
    tagsManageTitle: 'Tags verwalten',
    tagsNewPlaceholder: 'Tag-Name (z. B. Wichtig, Zuhause, Büro)…',
    tagsShortcutLabel: 'Kürzel (Strg+…)',
    tagsAddBtn: 'Tag anlegen',
    tagsFilterAll: 'Alle',
    areasManageTitle: 'Bereiche verwalten',
    areaSuggestions: ['Arbeit', 'Zuhause', 'Finanzen', 'Gesundheit', 'Familie', 'Studium', 'Sport', 'Hobby'],
    repeatFixedSchedule: 'Nach Zeitplan',
    repeatAfterCompletion: 'Nach Erledigung',
    repeatClearBtn: 'Aus',
    repeatEndsNever: 'Nie',
    repeatEndsOn: 'Datum wählen…',
    repeatLastDay: 'Letzter Tag',
    notificationsTitle: 'Benachrichtigungen & Erinnerungen',
    notificationsDailyTitle: 'Tägliche Übersicht für „Heute“',
    notificationsTestBtn: 'Test-Benachrichtigung senden',
    shortcutCancelHint: 'Tipp: Rechtsklick oder Alt+Klick auf eine Checkbox öffnet das Statusmenü',
    magicTodoSubDefault: 'In aktueller Ansicht erstellen',
    magicHeadingSub: 'Abschnitts-Überschrift im Projekt anlegen',
    magicProjectSub: 'Mehrstufiges Ziel mit Fortschrittsring & Abschnitten',
    magicAreaSub: 'Verwandte Projekte & Verantwortungsbereiche bündeln',
    ctxSelect: 'Aufgabe auswählen (Mehrfachauswahl)',
    ctxDeselect: 'Auswahl aufheben',
    ctxWhenReminder: 'Wann? (Datum & Erinnerung)',
    ctxMoveContainer: 'Bewegen (Bereich / Projekt)',
    ctxAssignTags: 'Tags zuweisen',
    multiSelectAll: 'Alle',
    multiSelectCount: (n) => `${n} ausgewählt`,
    multiSelectSelectAllTooltip: 'Alle Aufgaben in dieser Ansicht auswählen (Strg+A)',
    multiSelectClearTooltip: 'Auswahl aufheben (Esc)',
    repeatDialogTitle: 'Wiederholen (Things-Modus)',
    repeatModeFixedDesc: 'Fester Kalender-Rhythmus (wie „Regularly“ in Things for Mac).',
    repeatModeAfterDesc: 'Nächste Aufgabe wird erst erstellt, nachdem diese abgehakt wurde.',
    repeatWeekdaysLabel: 'An diesen Wochentagen:',
    repeatMonthDayLabel: 'Tag des Monats:',
    repeatEndsLabel: 'Endet:',
    sidebarShow: 'Seitenleiste einblenden',
    sidebarHide: 'Seitenleiste ausblenden',
    settingsCalendarSection: 'Google Kalender & Termine',
    settingsCalendarEnableTitle: 'Kalendertermine anzeigen',
    settingsCalendarEnableDesc:
      'Termine aus ausgewählten Google- und Gerätekalendern in „Heute“ und „Geplant“ einblenden (standardmäßig deaktiviert).',
    settingsCalendarSelectHint: (sel, total) =>
      `Kalender zur Anzeige auswählen (${sel}/${total}):`,
    settingsCalendarSelectAll: 'Alle',
    settingsCalendarSelectNone: 'Keine',
    settingsCalendarNoneFound: 'Keine Google- oder Gerätekalender auf diesem Gerät gefunden.',
    settingsCalendarPermissionDenied:
      'Kalenderberechtigung ist erforderlich, um Kalender aufzulisten und anzuzeigen.',
    settingsCalendarGrantPermissionBtn: 'Kalenderzugriff erlauben',
    settingsCalendarImportIcsBtn: '.ics importieren',
    calendarAllDay: 'Ganztägig',
    calendarUntitledEvent: '(Ohne Titel)'
  },
  fr: {
    appName: 'Thingsbook',
    viewInbox: 'Boîte de réception',
    viewToday: "Aujourd'hui",
    viewThisEvening: 'Ce soir',
    viewUpcoming: 'À venir',
    viewAnytime: 'À tout moment',
    viewSomeday: 'Un jour',
    viewLogbook: 'Journal',
    entityArea: 'Domaine',
    entityAreas: 'Domaines',
    entityNewArea: 'Nouveau domaine',
    entityProject: 'Projet',
    entityProjects: 'Projets',
    entityNewProject: 'Nouveau projet',
    entityHeading: 'Titre',
    entityNewHeading: 'Nouveau titre',
    entityTodo: 'Tâche',
    entityTodos: 'Tâches',
    entityNewTodo: 'Nouvelle tâche',
    entityTag: 'Tag',
    entityTags: 'Tags',
    placeholderTodoTitle: 'Nouvelle tâche',
    placeholderNotes: 'Notes',
    placeholderAddChecklist: 'Ajouter un élément à la liste…',
    actionWhen: 'Quand',
    actionDeadline: 'Échéance',
    actionTags: 'Tags',
    actionChecklist: 'Liste',
    actionReminder: 'Rappel',
    actionMove: 'Déplacer',
    actionRepeat: 'Répéter',
    actionComplete: 'Terminer',
    actionCancelTodo: 'Marquer comme annulée',
    actionReopenTodo: 'Rouvrir la tâche',
    actionDuplicate: 'Dupliquer',
    actionDelete: 'Supprimer',
    actionClear: 'Effacer',
    actionDone: 'OK',
    actionSave: 'Enregistrer',
    actionCancel: 'Annuler',
    actionEdit: 'Modifier',
    actionRename: 'Renommer',
    quickFindTitle: 'Recherche rapide',
    quickFindPlaceholder: 'Recherche rapide – Tâches, projets, domaines ou tags…',
    quickFindNoResults: 'Aucune tâche, projet ou domaine correspondant',
    quickFindSmartViews: 'Vues',
    quickFindHits: (n) => `${n} ${n === 1 ? 'résultat' : 'résultats'}`,
    deadlineToday: "Aujourd'hui",
    deadlineDaysLeft: (d) => `Reste ${d} j.`,
    deadlineDaysOverdue: (d) => `${d} j. de retard`,
    dateTomorrow: 'Demain',
    dateNextWeek: 'Semaine prochaine (+7j)',
    dateInTwoWeeks: 'Dans 2 semaines (+14j)',
    targetDate: 'Date cible',
    setTargetDate: '+ Définir date cible',
    assignArea: 'Assigner un domaine',
    noProjectNoArea: 'Aucun projet / Aucun domaine',
    whenNaturalHint: 'Tapez ex. « demain », « ce soir », « vendredi prochain »…',
    syncTitle: 'Synchronisation Thingsbook Cloud',
    syncStatusSynced: 'Synchronisé avec le Cloud',
    syncStatusSyncing: 'Synchronisation des deltas…',
    syncStatusOffline: 'Offline-First (IndexedDB locale)',
    syncNow: 'Synchroniser',
    authEmailLabel: 'Adresse e-mail',
    authPasswordLabel: 'Mot de passe (min. 6 caractères)',
    authRegisterAccount: 'Créer un compte et synchroniser',
    authLoginEmail: 'Se connecter par e-mail',
    authSignInGoogle: 'Continuer avec Google',
    authInstantCloud: 'Sync instantanée par e-mail',
    authSignOut: 'Se déconnecter',
    authForgotPassword: 'Mot de passe oublié ? Réinitialiser',
    authMultiDeviceHint:
      'Connectez-vous avec le même compte que sur Android pour synchroniser vos tâches en temps réel.',
    settingsTitle: 'Paramètres',
    settingsSubtitle: 'Base de données, Cloud, Langue, Mises à jour, Statistiques, Sauvegarde & CSV',
    settingsLanguage: "Langue de l'application",
    settingsSystemDefault: 'Par défaut du système',
    settingsTheme: 'Apparence',
    settingsThemeSystem: 'Système',
    settingsThemeLight: 'Clair',
    settingsThemeDark: 'Sombre',
    settingsDbSyncSection: 'Base de données et synchronisation',
    settingsLocalDbTitle: 'BD locale',
    settingsLocalDbDesc: '100% hors ligne sur ce Chromebook',
    settingsCloudSyncTitle: 'Cloud Sync',
    settingsCloudSyncDesc: 'BD locale + synchro temps réel Android & ChromeOS',
    settingsAccountStatus: 'Compte et statut Cloud',
    settingsNotSignedIn: 'Non connecté',
    settingsOpenAccountSync: 'Ouvrir Compte & Sync',
    settingsUpdatesSection: 'Mises à jour',
    settingsAutoUpdateLabel: 'Rechercher automatiquement les mises à jour',
    settingsSearchUpdatesBtn: 'Rechercher des mises à jour',
    settingsCheckingUpdates: 'Vérification…',
    settingsUpToDate: (v) => `Thingsbook v${v} est à jour.`,
    settingsUpdateAvailable: (v) => `La mise à jour v${v} est disponible !`,
    settingsApplyUpdateBtn: 'Installer et recharger',
    settingsStatsSection: 'Statistiques',
    statOpen: 'Ouvertes',
    statCompleted: 'Terminées',
    statCanceled: 'Annulées',
    statDeleted: 'Supprimées',
    statTodayActive: "★ Aujourd'hui",
    statUpcoming: '📅 À venir',
    statAnytime: '🗂️ À tout moment',
    statInbox: '📥 Boîte de réception',
    statSomeday: '📦 Un jour',
    statRecurring: '🔁 Récurrentes',
    statActiveProjects: '📁 Projets actifs',
    statAreas: '🗂️ Domaines',
    statSavedTags: '🏷️ Tags',
    statTotalRecords: '🗄️ Total entrées',
    backupSectionTitle: 'Sauvegarde et restauration (.tbbackup)',
    backupSectionDesc:
      'Sauvegarde complète compatible Android Thingsbook avec chiffrement AES-256-GCM optionnel.',
    backupCreateBtn: 'Créer une sauvegarde',
    backupRestoreBtn: 'Restaurer un fichier',
    backupPasswordProtect: 'Protéger par mot de passe AES-256-GCM',
    backupPasswordLabel: 'Mot de passe (min. 4 car.)',
    backupPasswordConfirm: 'Confirmer le mot de passe',
    backupSaveToFile: 'Enregistrer (.tbbackup)',
    backupShareBtn: 'Partager',
    backupRestoreConfirm: 'Tout restaurer maintenant',
    backupOverwriteWarning:
      'La restauration remplace toutes les tâches, projets, domaines et tags locaux actuels.',
    backupSyncAfterRestore: 'Synchroniser avec le Cloud après restauration',
    csvSectionTitle: 'Import & Export (CSV)',
    csvSectionDesc: 'Exportez ou importez vos tâches au format CSV.',
    csvExportBtn: 'Exporter CSV',
    csvShareBtn: 'Partager CSV',
    csvImportBtn: 'Importer CSV',
    emptyInbox: 'Votre boîte de réception est vide.',
    emptyToday: "Aucune tâche prévue pour aujourd'hui.",
    emptyView: 'Aucun élément dans cette vue.',
    geminiHubTitle: 'Thingsbook Intelligence (Gemini)',
    geminiMagicBreakdown: 'Découpage magique',
    geminiBrainDumpTitle: 'Brain Dump → Import GTD',
    geminiBrainDumpPlaceholder: 'Notez librement vos idées…',
    geminiBrainDumpAction: 'Extraire et planifier les tâches',
    geminiArchitectTitle: 'Architecte de projet IA',
    geminiArchitectPlaceholder: 'Décrivez votre objectif de projet…',
    geminiArchitectAction: 'Générer le projet avec titres et tâches',
    geminiReflectionTitle: 'Bilan IA du Journal',
    geminiReflectionAction: 'Générer le bilan',
    geminiGenerating: 'Gemini structure vos idées avec calme…',
    geminiApiKeyLabel: 'Clé API Gemini facultative',
    geminiApiKeyPlaceholder: 'Collez la clé AIza… ou laissez vide',
    setupBannerTitle: 'Bienvenue dans Thingsbook pour ChromeOS',
    setupBannerDesc:
      'Connectez votre compte Cloud pour synchroniser avec Android ou supprimez les exemples.',
    setupBannerConnectBtn: 'Connecter le compte Cloud',
    setupBannerDeleteSamplesBtn: "Supprimer les tâches d'exemple",
    mergeDialogTitle: 'Exemples et synchronisation Cloud',
    mergeDialogDesc:
      'Les tâches de démonstration seront supprimées. Que faire de vos tâches créées localement ?',
    mergeKeepBothBtn: 'Fusionner mes tâches locales avec le Cloud',
    mergeAccountOnlyBtn: 'Charger uniquement le compte Cloud',
    installChromeOsBtn: 'Ajouter au lanceur ChromeOS',
    installChromeOsInstalled: 'Installé dans le menu ChromeOS',
    installChromeOsHint:
      "Cliquez sur l'icône Installer (⊕) dans la barre d'adresse Chrome pour ajouter Thingsbook au menu Démarrer de ChromeOS.",
    tagsManageTitle: 'Gérer les Tags',
    tagsNewPlaceholder: 'Nom du nouveau tag…',
    tagsShortcutLabel: 'Raccourci (Ctrl+…)',
    tagsAddBtn: 'Créer le tag',
    tagsFilterAll: 'Tous',
    areasManageTitle: 'Gérer les domaines',
    areaSuggestions: ['Travail', 'Maison', 'Finances', 'Santé', 'Famille', 'Études', 'Sport', 'Loisirs'],
    repeatFixedSchedule: 'Régulièrement',
    repeatAfterCompletion: 'Après achèvement',
    repeatClearBtn: 'Off',
    repeatEndsNever: 'Jamais',
    repeatEndsOn: 'Choisir une date…',
    repeatLastDay: 'Dernier jour',
    notificationsTitle: 'Notifications et rappels',
    notificationsDailyTitle: 'Résumé quotidien des tâches actives',
    notificationsTestBtn: 'Notification test',
    shortcutCancelHint: 'Astuce : Clic droit ou Alt+Clic sur une case ouvre le menu de statut',
    magicTodoSubDefault: 'Créer dans la vue actuelle',
    magicHeadingSub: 'Ajouter une section dans ce projet',
    magicProjectSub: 'Objectif à étapes avec anneau de progression',
    magicAreaSub: 'Regrouper projets et responsabilités',
    ctxSelect: 'Sélectionner la tâche (Sélection multiple)',
    ctxDeselect: 'Effacer la sélection',
    ctxWhenReminder: 'Quand ? (Date et rappel)',
    ctxMoveContainer: 'Déplacer (Domaine / Projet)',
    ctxAssignTags: 'Attribuer des tags',
    multiSelectAll: 'Tout',
    multiSelectCount: (n) => `${n} sélectionnée(s)`,
    multiSelectSelectAllTooltip: 'Tout sélectionner dans cette vue (Ctrl+A)',
    multiSelectClearTooltip: 'Effacer la sélection (Échap)',
    repeatDialogTitle: 'Répéter (Mode Things)',
    repeatModeFixedDesc: 'Rythme fixe du calendrier (comme « Regularly » dans Things pour Mac).',
    repeatModeAfterDesc: 'La prochaine tâche est créée uniquement une fois celle-ci terminée.',
    repeatWeekdaysLabel: 'Ces jours de la semaine :',
    repeatMonthDayLabel: 'Jour du mois :',
    repeatEndsLabel: 'Fin :',
    sidebarShow: 'Afficher la barre latérale',
    sidebarHide: 'Masquer la barre latérale',
    settingsCalendarSection: 'Google Agenda & Événements',
    settingsCalendarEnableTitle: 'Afficher les événements du calendrier',
    settingsCalendarEnableDesc:
      "Afficher les événements des agendas Google et de l'appareil sélectionnés dans Aujourd'hui et Planifié (désactivé par défaut).",
    settingsCalendarSelectHint: (sel, total) =>
      `Sélectionner les agendas à afficher (${sel}/${total}) :`,
    settingsCalendarSelectAll: 'Tous',
    settingsCalendarSelectNone: 'Aucun',
    settingsCalendarNoneFound: 'Aucun agenda Google ou de l’appareil trouvé sur cet appareil.',
    settingsCalendarPermissionDenied:
      "L'autorisation d'accès au calendrier est requise pour afficher vos agendas.",
    settingsCalendarGrantPermissionBtn: "Autoriser l'accès au calendrier",
    settingsCalendarImportIcsBtn: 'Importer .ics',
    calendarAllDay: 'Toute la journée',
    calendarUntitledEvent: '(Sans titre)'
  },
  it: {
    appName: 'Thingsbook',
    viewInbox: 'Entrata',
    viewToday: 'Oggi',
    viewThisEvening: 'Stasera',
    viewUpcoming: 'Programmati',
    viewAnytime: 'In qualsiasi momento',
    viewSomeday: 'Prima o poi',
    viewLogbook: 'Registro',
    entityArea: 'Area',
    entityAreas: 'Aree',
    entityNewArea: 'Nuova area',
    entityProject: 'Progetto',
    entityProjects: 'Progetti',
    entityNewProject: 'Nuovo progetto',
    entityHeading: 'Intestazione',
    entityNewHeading: 'Nuova intestazione',
    entityTodo: 'Attività',
    entityTodos: 'Attività',
    entityNewTodo: 'Nuova attività',
    entityTag: 'Tag',
    entityTags: 'Tag',
    placeholderTodoTitle: 'Nuova attività',
    placeholderNotes: 'Note',
    placeholderAddChecklist: 'Aggiungi voce di controllo…',
    actionWhen: 'Quando',
    actionDeadline: 'Scadenza',
    actionTags: 'Tag',
    actionChecklist: 'Elenco',
    actionReminder: 'Promemoria',
    actionMove: 'Sposta',
    actionRepeat: 'Ripeti',
    actionComplete: 'Completa',
    actionCancelTodo: 'Segna come annullata',
    actionReopenTodo: 'Riapri attività',
    actionDuplicate: 'Duplica',
    actionDelete: 'Elimina',
    actionClear: 'Cancella',
    actionDone: 'Fine',
    actionSave: 'Salva',
    actionCancel: 'Annulla',
    actionEdit: 'Modifica',
    actionRename: 'Rinomina',
    quickFindTitle: 'Ricerca rapida',
    quickFindPlaceholder: 'Ricerca rapida – Attività, progetti, aree o tag…',
    quickFindNoResults: 'Nessuna attività, progetto o area corrispondente',
    quickFindSmartViews: 'Viste',
    quickFindHits: (n) => `${n} ${n === 1 ? 'risultato' : 'risultati'}`,
    deadlineToday: 'Oggi',
    deadlineDaysLeft: (d) => `${d} gg rimasti`,
    deadlineDaysOverdue: (d) => `${d} gg in ritardo`,
    dateTomorrow: 'Domani',
    dateNextWeek: 'Prossima settimana (+7g)',
    dateInTwoWeeks: 'Tra 2 settimane (+14g)',
    targetDate: 'Data obiettivo',
    setTargetDate: '+ Imposta data obiettivo',
    assignArea: 'Assegna area',
    noProjectNoArea: 'Nessun progetto / Nessuna area',
    whenNaturalHint: 'Digita ad es. "domani", "stasera", "venerdì prossimo"…',
    syncTitle: 'Sincronizzazione Thingsbook Cloud',
    syncStatusSynced: 'Sincronizzato con il Cloud',
    syncStatusSyncing: 'Sincronizzazione delta…',
    syncStatusOffline: 'Offline-First (IndexedDB locale)',
    syncNow: 'Sincronizza ora',
    authEmailLabel: 'Indirizzo e-mail',
    authPasswordLabel: 'Password (min. 6 caratteri)',
    authRegisterAccount: 'Crea account e sincronizza',
    authLoginEmail: 'Accedi con e-mail',
    authSignInGoogle: 'Continua con Google',
    authInstantCloud: 'Sync istantanea via e-mail',
    authSignOut: 'Esci',
    authForgotPassword: 'Password dimenticata? Reimposta',
    authMultiDeviceHint:
      'Accedi con lo stesso account usato su Android per sincronizzare in tempo reale.',
    settingsTitle: 'Impostazioni',
    settingsSubtitle: 'Database, Cloud, Lingua, Aggiornamenti, Statistiche, Backup & CSV',
    settingsLanguage: "Lingua dell'app",
    settingsSystemDefault: 'Predefinita di sistema',
    settingsTheme: 'Aspetto',
    settingsThemeSystem: 'Sistema',
    settingsThemeLight: 'Chiaro',
    settingsThemeDark: 'Scuro',
    settingsDbSyncSection: 'Database e sincronizzazione',
    settingsLocalDbTitle: 'DB locale',
    settingsLocalDbDesc: '100% offline su questo Chromebook',
    settingsCloudSyncTitle: 'Cloud Sync',
    settingsCloudSyncDesc: 'DB locale + sync in tempo reale Android & ChromeOS',
    settingsAccountStatus: 'Account e stato Cloud',
    settingsNotSignedIn: 'Non connesso',
    settingsOpenAccountSync: 'Apri Account e Sync',
    settingsUpdatesSection: 'Aggiornamenti App',
    settingsAutoUpdateLabel: 'Controlla automaticamente gli aggiornamenti',
    settingsSearchUpdatesBtn: 'Cerca aggiornamenti',
    settingsCheckingUpdates: 'Controllo in corso…',
    settingsUpToDate: (v) => `Thingsbook v${v} è aggiornato.`,
    settingsUpdateAvailable: (v) => `Aggiornamento v${v} disponibile!`,
    settingsApplyUpdateBtn: 'Installa e ricarica',
    settingsStatsSection: 'Statistiche',
    statOpen: 'Aperte',
    statCompleted: 'Completate',
    statCanceled: 'Annullate',
    statDeleted: 'Eliminate',
    statTodayActive: '★ Attive Oggi',
    statUpcoming: '📅 Programmati',
    statAnytime: '🗂️ In qualsiasi momento',
    statInbox: '📥 Entrata',
    statSomeday: '📦 Prima o poi',
    statRecurring: '🔁 Ricorrenti',
    statActiveProjects: '📁 Progetti attivi',
    statAreas: '🗂️ Aree',
    statSavedTags: '🏷️ Tag salvati',
    statTotalRecords: '🗄️ Record totali',
    backupSectionTitle: 'Backup e ripristino (.tbbackup)',
    backupSectionDesc:
      'Backup completo compatibile con Android Thingsbook con crittografia AES-256-GCM opzionale.',
    backupCreateBtn: 'Crea backup',
    backupRestoreBtn: 'Ripristina da file',
    backupPasswordProtect: 'Proteggi con password AES-256-GCM',
    backupPasswordLabel: 'Password backup (min. 4 car.)',
    backupPasswordConfirm: 'Conferma password',
    backupSaveToFile: 'Salva file (.tbbackup)',
    backupShareBtn: 'Condividi',
    backupRestoreConfirm: 'Ripristina tutto ora',
    backupOverwriteWarning:
      'Il ripristino sostituisce tutte le attività, i progetti, le aree e i tag locali correnti.',
    backupSyncAfterRestore: 'Sincronizza con il Cloud dopo il ripristino',
    csvSectionTitle: 'Importa ed esporta (CSV)',
    csvSectionDesc: 'Esporta o importa attività in formato CSV.',
    csvExportBtn: 'Esporta CSV',
    csvShareBtn: 'Condividi CSV',
    csvImportBtn: 'Importa CSV',
    emptyInbox: 'Entrata è vuota.',
    emptyToday: 'Nessuna attività programmata per Oggi.',
    emptyView: 'Nessun elemento in questa vista.',
    geminiHubTitle: 'Thingsbook Intelligence (Gemini)',
    geminiMagicBreakdown: 'Suddivisione magica',
    geminiBrainDumpTitle: 'Brain Dump → Importazione GTD',
    geminiBrainDumpPlaceholder: 'Scrivi liberamente i tuoi pensieri…',
    geminiBrainDumpAction: 'Estrai e pianifica attività',
    geminiArchitectTitle: 'Architetto di progetti IA',
    geminiArchitectPlaceholder: 'Descrivi il tuo obiettivo…',
    geminiArchitectAction: 'Genera progetto con intestazioni e attività',
    geminiReflectionTitle: 'Riflessione IA del Registro',
    geminiReflectionAction: 'Genera riepilogo',
    geminiGenerating: 'Gemini sta elaborando con calma…',
    geminiApiKeyLabel: 'Chiave API Gemini opzionale',
    geminiApiKeyPlaceholder: 'Incolla chiave AIza… o lascia vuoto',
    setupBannerTitle: 'Benvenuto in Thingsbook per ChromeOS',
    setupBannerDesc:
      'Collega il tuo account Cloud per sincronizzare con Android oppure elimina le attività di esempio.',
    setupBannerConnectBtn: 'Collega account Cloud',
    setupBannerDeleteSamplesBtn: 'Elimina attività di esempio',
    mergeDialogTitle: 'Attività di esempio e Cloud Sync',
    mergeDialogDesc:
      'Le attività demo verranno rimosse automaticamente. Come gestire eventuali attività create localmente?',
    mergeKeepBothBtn: 'Unisci le mie attività locali con il Cloud',
    mergeAccountOnlyBtn: 'Carica solo le attività del Cloud',
    installChromeOsBtn: 'Aggiungi al Launcher ChromeOS',
    installChromeOsInstalled: 'Installata nel menu ChromeOS',
    installChromeOsHint:
      "Clicca sull'icona Installa (⊕) nella barra degli indirizzi di Chrome per aggiungere Thingsbook al menu Start di ChromeOS.",
    tagsManageTitle: 'Gestione Tag',
    tagsNewPlaceholder: 'Nome nuovo tag…',
    tagsShortcutLabel: 'Scorciatoia (Ctrl+…)',
    tagsAddBtn: 'Crea Tag',
    tagsFilterAll: 'Tutti',
    areasManageTitle: 'Gestione Aree',
    areaSuggestions: ['Lavoro', 'Casa', 'Finanze', 'Salute', 'Famiglia', 'Studio', 'Sport', 'Hobby'],
    repeatFixedSchedule: 'Regolarmente',
    repeatAfterCompletion: 'Dopo il completamento',
    repeatClearBtn: 'Off',
    repeatEndsNever: 'Mai',
    repeatEndsOn: 'Scegli data…',
    repeatLastDay: 'Ultimo giorno',
    notificationsTitle: 'Notifiche e promemoria',
    notificationsDailyTitle: 'Riepilogo giornaliero Oggi',
    notificationsTestBtn: 'Invia notifica di prova',
    shortcutCancelHint: 'Suggerimento: Clic destro o Alt+Clic sulla casella apre il menu stato',
    magicTodoSubDefault: 'Crea nella vista attuale',
    magicHeadingSub: 'Aggiungi una sezione in questo progetto',
    magicProjectSub: 'Obiettivo strutturato con progresso e sezioni',
    magicAreaSub: 'Raggruppa progetti e responsabilità correlate',
    ctxSelect: 'Seleziona attività (Selezione multipla)',
    ctxDeselect: 'Annulla selezione',
    ctxWhenReminder: 'Quando? (Data e promemoria)',
    ctxMoveContainer: 'Sposta (Area / Progetto)',
    ctxAssignTags: 'Assegna tag',
    multiSelectAll: 'Tutti',
    multiSelectCount: (n) => `${n} selezionate`,
    multiSelectSelectAllTooltip: 'Seleziona tutto in questa vista (Ctrl+A)',
    multiSelectClearTooltip: 'Annulla selezione (Esc)',
    repeatDialogTitle: 'Ripeti (Modalità Things)',
    repeatModeFixedDesc: 'Pianificazione fissa del calendario (come "Regularly" in Things per Mac).',
    repeatModeAfterDesc: 'La prossima attività viene creata solo dopo il completamento di questa.',
    repeatWeekdaysLabel: 'In questi giorni della settimana:',
    repeatMonthDayLabel: 'Giorno del mese:',
    repeatEndsLabel: 'Termina:',
    sidebarShow: 'Mostra barra laterale',
    sidebarHide: 'Nascondi barra laterale',
    settingsCalendarSection: 'Google Calendar & Eventi',
    settingsCalendarEnableTitle: 'Mostra eventi del calendario',
    settingsCalendarEnableDesc:
      'Mostra gli eventi dei calendari Google e del dispositivo selezionati in Oggi e Pianificati (disattivato per impostazione predefinita).',
    settingsCalendarSelectHint: (sel, total) =>
      `Seleziona i calendari da mostrare (${sel}/${total}):`,
    settingsCalendarSelectAll: 'Tutti',
    settingsCalendarSelectNone: 'Nessuno',
    settingsCalendarNoneFound: 'Nessun calendario Google o del dispositivo trovato su questo dispositivo.',
    settingsCalendarPermissionDenied:
      "È necessaria l'autorizzazione al calendario per mostrare i tuoi calendari.",
    settingsCalendarGrantPermissionBtn: 'Consenti accesso al calendario',
    settingsCalendarImportIcsBtn: 'Importa .ics',
    calendarAllDay: 'Tutto il giorno',
    calendarUntitledEvent: '(Senza titolo)'
  },
  es: {
    appName: 'Thingsbook',
    viewInbox: 'Entrada',
    viewToday: 'Hoy',
    viewThisEvening: 'Esta noche',
    viewUpcoming: 'Programado',
    viewAnytime: 'En cualquier momento',
    viewSomeday: 'Algún día',
    viewLogbook: 'Registro',
    entityArea: 'Área',
    entityAreas: 'Áreas',
    entityNewArea: 'Nueva área',
    entityProject: 'Proyecto',
    entityProjects: 'Proyectos',
    entityNewProject: 'Nuevo proyecto',
    entityHeading: 'Encabezado',
    entityNewHeading: 'Nuevo encabezado',
    entityTodo: 'Tarea',
    entityTodos: 'Tareas',
    entityNewTodo: 'Nueva tarea',
    entityTag: 'Etiqueta',
    entityTags: 'Etiquetas',
    placeholderTodoTitle: 'Nueva tarea',
    placeholderNotes: 'Notas',
    placeholderAddChecklist: 'Añadir elemento a la lista…',
    actionWhen: 'Cuándo',
    actionDeadline: 'Fecha límite',
    actionTags: 'Etiquetas',
    actionChecklist: 'Lista',
    actionReminder: 'Recordatorio',
    actionMove: 'Mover',
    actionRepeat: 'Repetir',
    actionComplete: 'Completar',
    actionCancelTodo: 'Marcar como cancelada',
    actionReopenTodo: 'Reabrir tarea',
    actionDuplicate: 'Duplicar',
    actionDelete: 'Eliminar',
    actionClear: 'Borrar',
    actionDone: 'Listo',
    actionSave: 'Guardar',
    actionCancel: 'Cancelar',
    actionEdit: 'Editar',
    actionRename: 'Renombrar',
    quickFindTitle: 'Búsqueda rápida',
    quickFindPlaceholder: 'Búsqueda rápida – Tareas, proyectos, áreas o etiquetas…',
    quickFindNoResults: 'No hay tareas, proyectos ni áreas coincidentes',
    quickFindSmartViews: 'Vistas',
    quickFindHits: (n) => `${n} ${n === 1 ? 'resultado' : 'resultados'}`,
    deadlineToday: 'Hoy',
    deadlineDaysLeft: (d) => `Quedan ${d} d.`,
    deadlineDaysOverdue: (d) => `${d} d. de retraso`,
    dateTomorrow: 'Mañana',
    dateNextWeek: 'Próxima semana (+7d)',
    dateInTwoWeeks: 'En 2 semanas (+14d)',
    targetDate: 'Fecha objetivo',
    setTargetDate: '+ Fijar fecha objetivo',
    assignArea: 'Asignar área',
    noProjectNoArea: 'Sin proyecto / Sin área',
    whenNaturalHint: 'Escribe p. ej. «mañana», «esta noche», «próximo viernes»…',
    syncTitle: 'Sincronización Thingsbook Cloud',
    syncStatusSynced: 'Sincronizado con la nube',
    syncStatusSyncing: 'Sincronizando deltas…',
    syncStatusOffline: 'Offline-First (IndexedDB local)',
    syncNow: 'Sincronizar ahora',
    authEmailLabel: 'Correo electrónico',
    authPasswordLabel: 'Contraseña (mín. 6 caracteres)',
    authRegisterAccount: 'Crear cuenta y sincronizar',
    authLoginEmail: 'Iniciar sesión con correo',
    authSignInGoogle: 'Continuar con Google',
    authInstantCloud: 'Sync instantánea por correo',
    authSignOut: 'Cerrar sesión',
    authForgotPassword: '¿Olvidaste tu contraseña? Restablecer',
    authMultiDeviceHint:
      'Inicia sesión con la misma cuenta que en Android para sincronizar en tiempo real.',
    settingsTitle: 'Ajustes',
    settingsSubtitle: 'Base de datos, Nube, Idioma, Actualizaciones, Estadísticas, Copia y CSV',
    settingsLanguage: 'Idioma de la aplicación',
    settingsSystemDefault: 'Predeterminado del sistema',
    settingsTheme: 'Apariencia',
    settingsThemeSystem: 'Sistema',
    settingsThemeLight: 'Claro',
    settingsThemeDark: 'Oscuro',
    settingsDbSyncSection: 'Base de datos y sincronización',
    settingsLocalDbTitle: 'BD local',
    settingsLocalDbDesc: '100% sin conexión en este Chromebook',
    settingsCloudSyncTitle: 'Cloud Sync',
    settingsCloudSyncDesc: 'BD local + sincronización en tiempo real Android y ChromeOS',
    settingsAccountStatus: 'Cuenta y estado en la nube',
    settingsNotSignedIn: 'Sin iniciar sesión',
    settingsOpenAccountSync: 'Abrir Cuenta y Sync',
    settingsUpdatesSection: 'Actualizaciones',
    settingsAutoUpdateLabel: 'Buscar actualizaciones automáticamente',
    settingsSearchUpdatesBtn: 'Buscar actualizaciones',
    settingsCheckingUpdates: 'Buscando actualizaciones…',
    settingsUpToDate: (v) => `Thingsbook v${v} está actualizado.`,
    settingsUpdateAvailable: (v) => `¡Actualización v${v} disponible!`,
    settingsApplyUpdateBtn: 'Instalar y recargar',
    settingsStatsSection: 'Estadísticas',
    statOpen: 'Abiertas',
    statCompleted: 'Completadas',
    statCanceled: 'Canceladas',
    statDeleted: 'Eliminadas',
    statTodayActive: '★ Activas Hoy',
    statUpcoming: '📅 Programadas',
    statAnytime: '🗂️ En cualquier momento',
    statInbox: '📥 Entrada',
    statSomeday: '📦 Algún día',
    statRecurring: '🔁 Recurrentes',
    statActiveProjects: '📁 Proyectos activos',
    statAreas: '🗂️ Áreas',
    statSavedTags: '🏷️ Etiquetas',
    statTotalRecords: '🗄️ Registros totales',
    backupSectionTitle: 'Copia de seguridad y restauración (.tbbackup)',
    backupSectionDesc:
      'Copia completa compatible con Android Thingsbook con cifrado opcional AES-256-GCM.',
    backupCreateBtn: 'Crear copia',
    backupRestoreBtn: 'Restaurar archivo',
    backupPasswordProtect: 'Proteger con contraseña AES-256-GCM',
    backupPasswordLabel: 'Contraseña (mín. 4 car.)',
    backupPasswordConfirm: 'Confirmar contraseña',
    backupSaveToFile: 'Guardar archivo (.tbbackup)',
    backupShareBtn: 'Compartir',
    backupRestoreConfirm: 'Restaurar todo ahora',
    backupOverwriteWarning:
      'Al restaurar se reemplazarán todas las tareas, proyectos, áreas y etiquetas locales actuales.',
    backupSyncAfterRestore: 'Sincronizar con la nube tras restaurar',
    csvSectionTitle: 'Importar y exportar (CSV)',
    csvSectionDesc: 'Exporta o importa tus tareas en formato CSV.',
    csvExportBtn: 'Exportar CSV',
    csvShareBtn: 'Compartir CSV',
    csvImportBtn: 'Importar CSV',
    emptyInbox: 'Tu Entrada está vacía.',
    emptyToday: 'No hay tareas programadas para Hoy.',
    emptyView: 'No hay elementos en esta vista.',
    geminiHubTitle: 'Thingsbook Intelligence (Gemini)',
    geminiMagicBreakdown: 'Desglose mágico',
    geminiBrainDumpTitle: 'Brain Dump → Importación GTD',
    geminiBrainDumpPlaceholder: 'Escribe libremente tus ideas…',
    geminiBrainDumpAction: 'Extraer y programar tareas',
    geminiArchitectTitle: 'Arquitecto de proyectos IA',
    geminiArchitectPlaceholder: 'Describe tu objetivo…',
    geminiArchitectAction: 'Generar proyecto con encabezados y tareas',
    geminiReflectionTitle: 'Reflexión IA del Registro',
    geminiReflectionAction: 'Generar resumen',
    geminiGenerating: 'Gemini está estructurando tus ideas con calma…',
    geminiApiKeyLabel: 'Clave API de Gemini opcional',
    geminiApiKeyPlaceholder: 'Pega la clave AIza… o déjalo en blanco',
    setupBannerTitle: 'Bienvenido a Thingsbook para ChromeOS',
    setupBannerDesc:
      'Conecta tu cuenta en la nube para sincronizar con Android o elimina las tareas de ejemplo.',
    setupBannerConnectBtn: 'Conectar cuenta Cloud',
    setupBannerDeleteSamplesBtn: 'Eliminar tareas de ejemplo',
    mergeDialogTitle: 'Tareas de ejemplo y sincronización Cloud',
    mergeDialogDesc:
      'Las tareas de demostración se eliminarán automáticamente. ¿Cómo gestionar las tareas creadas localmente?',
    mergeKeepBothBtn: 'Combinar mis tareas locales con la nube',
    mergeAccountOnlyBtn: 'Cargar solo tareas de la nube',
    installChromeOsBtn: 'Añadir al menú de ChromeOS',
    installChromeOsInstalled: 'Instalada en el menú de ChromeOS',
    installChromeOsHint:
      'Haz clic en el icono Instalar (⊕) en la barra de direcciones de Chrome para añadir Thingsbook al menú Inicio de ChromeOS.',
    tagsManageTitle: 'Gestionar etiquetas',
    tagsNewPlaceholder: 'Nombre de nueva etiqueta…',
    tagsShortcutLabel: 'Atajo (Ctrl+…)',
    tagsAddBtn: 'Crear etiqueta',
    tagsFilterAll: 'Todas',
    areasManageTitle: 'Gestionar áreas',
    areaSuggestions: ['Trabajo', 'Casa', 'Finanzas', 'Salud', 'Familia', 'Estudios', 'Deporte', 'Hobby'],
    repeatFixedSchedule: 'Regularmente',
    repeatAfterCompletion: 'Al completar',
    repeatClearBtn: 'Desact.',
    repeatEndsNever: 'Nunca',
    repeatEndsOn: 'Elegir fecha…',
    repeatLastDay: 'Último día',
    notificationsTitle: 'Notificaciones y recordatorios',
    notificationsDailyTitle: 'Resumen diario de Hoy',
    notificationsTestBtn: 'Enviar notificación de prueba',
    shortcutCancelHint: 'Consejo: Clic derecho o Alt+Clic en la casilla abre el menú de estado',
    magicTodoSubDefault: 'Crear en la vista actual',
    magicHeadingSub: 'Añadir encabezado de sección al proyecto',
    magicProjectSub: 'Objetivo con anillo de progreso y secciones',
    magicAreaSub: 'Agrupar proyectos y responsabilidades',
    ctxSelect: 'Seleccionar tarea (Selección múltiple)',
    ctxDeselect: 'Quitar selección',
    ctxWhenReminder: '¿Cuándo? (Fecha y recordatorio)',
    ctxMoveContainer: 'Mover (Área / Proyecto)',
    ctxAssignTags: 'Asignar etiquetas',
    multiSelectAll: 'Todas',
    multiSelectCount: (n) => `${n} seleccionada(s)`,
    multiSelectSelectAllTooltip: 'Seleccionar todo en esta vista (Ctrl+A)',
    multiSelectClearTooltip: 'Quitar selección (Esc)',
    repeatDialogTitle: 'Repetir (Modo Things)',
    repeatModeFixedDesc: 'Calendario fijo (como «Regularly» en Things para Mac).',
    repeatModeAfterDesc: 'La siguiente tarea se crea solo después de completar esta.',
    repeatWeekdaysLabel: 'En estos días de la semana:',
    repeatMonthDayLabel: 'Día del mes:',
    repeatEndsLabel: 'Termina:',
    sidebarShow: 'Mostrar barra lateral',
    sidebarHide: 'Ocultar barra lateral',
    settingsCalendarSection: 'Google Calendar y Eventos',
    settingsCalendarEnableTitle: 'Mostrar eventos del calendario',
    settingsCalendarEnableDesc:
      'Mostrar eventos de los calendarios de Google y del dispositivo seleccionados en Hoy y Programado (desactivado por defecto).',
    settingsCalendarSelectHint: (sel, total) =>
      `Seleccionar calendarios para mostrar (${sel}/${total}):`,
    settingsCalendarSelectAll: 'Todos',
    settingsCalendarSelectNone: 'Ninguno',
    settingsCalendarNoneFound: 'No se encontraron calendarios de Google ni del dispositivo.',
    settingsCalendarPermissionDenied:
      'Se requiere permiso de calendario para listar y mostrar tus calendarios.',
    settingsCalendarGrantPermissionBtn: 'Permitir acceso al calendario',
    settingsCalendarImportIcsBtn: 'Importar .ics',
    calendarAllDay: 'Todo el día',
    calendarUntitledEvent: '(Sin título)'
  }
};

export function resolveLanguage(pref: SupportedLang | 'system'): SupportedLang {
  if (pref !== 'system') return pref;
  const nav = (typeof navigator !== 'undefined' ? navigator.language : 'en').slice(0, 2).toLowerCase();
  if (nav === 'de' || nav === 'fr' || nav === 'it' || nav === 'es') return nav;
  return 'en';
}
