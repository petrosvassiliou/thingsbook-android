import React, { useRef, useState } from 'react';
import {
  AlertTriangle,
  Bell,
  Calendar,
  Check,
  Cloud,
  Database,
  Download,
  Eye,
  EyeOff,
  FileSpreadsheet,
  KeyRound,
  Lock,
  LogOut,
  RefreshCw,
  Share2,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  X
} from 'lucide-react';
import {
  APP_BUILD_CODE,
  APP_VERSION,
  type AppPreferences,
  type DeviceCalendarInfo,
  type SupportedLang,
  type ThemeMode,
  type ThingsbookStatistics
} from '../domain/types';
import type { BackupInspection } from '../data/backupCodec';
import {
  cloudSync,
  formatAuthErrorMessage,
  isPasswordRecoveryCandidateError,
  type CloudSyncSnapshot
} from '../data/cloudSync';
import { localDb, type DataSnapshot } from '../data/localDb';
import {
  applyPendingSwUpdate,
  checkForSwAndReleaseUpdates,
  type UpdateCheckResult
} from '../data/pwaUpdater';
import type { TranslationStrings } from '../i18n/translations';

// ==================== BackupCreateModal ====================

interface BackupCreateModalProps {
  prefs: AppPreferences;
  stats: ThingsbookStatistics;
  t: TranslationStrings;
  onToast: (msg: string) => void;
  onClose: () => void;
}

const BackupCreateModal: React.FC<BackupCreateModalProps> = ({
  prefs,
  stats,
  t,
  onToast,
  onClose
}) => {
  const [encrypt, setEncrypt] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [busy, setBusy] = useState(false);

  const canSubmit =
    !busy && (!encrypt || (password.length >= 4 && password === confirmPassword));

  const generateBackupFile = async (): Promise<{ file: File; filename: string } | null> => {
    setBusy(true);
    try {
      const { bytes } = await localDb.createBackupBytes(encrypt ? password : null, prefs);
      const d = new Date();
      const stamp = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate()
      ).padStart(2, '0')}`;
      const filename = `thingsbook-backup-${stamp}.tbbackup`;
      const copy = new Uint8Array(bytes.byteLength);
      copy.set(bytes);
      const file = new File([copy.buffer], filename, { type: 'application/octet-stream' });
      return { file, filename };
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Backup failed');
      return null;
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    const res = await generateBackupFile();
    if (!res) return;
    const url = URL.createObjectURL(res.file);
    const a = document.createElement('a');
    a.href = url;
    a.download = res.filename;
    a.click();
    URL.revokeObjectURL(url);
    onToast(`✓ ${res.filename}`);
    onClose();
  };

  const handleShare = async () => {
    const res = await generateBackupFile();
    if (!res) return;
    if (navigator.share && navigator.canShare?.({ files: [res.file] })) {
      try {
        await navigator.share({ title: res.filename, files: [res.file] });
        onClose();
        return;
      } catch {
        // fallback to download
      }
    }
    const url = URL.createObjectURL(res.file);
    const a = document.createElement('a');
    a.href = url;
    a.download = res.filename;
    a.click();
    URL.revokeObjectURL(url);
    onToast(`✓ ${res.filename}`);
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ShieldCheck size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.backupCreateBtn}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        {/* Summary Card */}
        <div
          style={{
            padding: '10px 12px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--bg-subtle)',
            border: '1px solid var(--border-subtle)',
            fontSize: 12.6,
            marginBottom: 14
          }}
        >
          <strong>{stats.totalRecordsCount}</strong> {t.entityTodos} •{' '}
          <strong>{stats.activeProjectsCount}</strong> {t.entityProjects} •{' '}
          <strong>{stats.areasCount}</strong> {t.entityAreas} •{' '}
          <strong>{stats.tagsCount}</strong> {t.entityTags}
        </div>

        {/* AES-256-GCM Password Switch */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 12
          }}
        >
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 560, fontSize: 13.3 }}>
            <Lock size={14} style={{ color: 'var(--things-blue)' }} />
            <span>{t.backupPasswordProtect}</span>
          </span>
          <button
            type="button"
            className={`toggle-switch ${encrypt ? 'on' : ''}`}
            onClick={() => setEncrypt((v) => !v)}
          >
            <span className="toggle-switch-knob" />
          </button>
        </div>

        {encrypt && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 14 }}>
            <div style={{ position: 'relative' }}>
              <input
                type={showPw ? 'text' : 'password'}
                className="text-field"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t.backupPasswordLabel}
              />
              <button
                type="button"
                className="icon-btn"
                style={{ position: 'absolute', right: 4, top: 3 }}
                onClick={() => setShowPw((v) => !v)}
              >
                {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
            <input
              type={showPw ? 'text' : 'password'}
              className="text-field"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder={t.backupPasswordConfirm}
            />
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t.actionCancel}
          </button>
          <button
            type="button"
            className="btn-secondary"
            disabled={!canSubmit}
            onClick={handleShare}
          >
            <Share2 size={14} />
            <span>{t.backupShareBtn}</span>
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={!canSubmit}
            onClick={handleSave}
          >
            <Download size={14} />
            <span>{t.backupSaveToFile}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== BackupRestoreModal ====================

interface BackupRestoreModalProps {
  rawBytes: Uint8Array;
  fileName: string;
  inspection: BackupInspection;
  isCloudSignedIn: boolean;
  t: TranslationStrings;
  onRestoredPrefs: (patch: Partial<AppPreferences>) => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

const BackupRestoreModal: React.FC<BackupRestoreModalProps> = ({
  rawBytes,
  fileName,
  inspection,
  isCloudSignedIn,
  t,
  onRestoredPrefs,
  onToast,
  onClose
}) => {
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [syncAfter, setSyncAfter] = useState(isCloudSignedIn);
  const [busy, setBusy] = useState(false);
  const [errMsg, setErrMsg] = useState<string | null>(null);

  const isEncrypted = inspection.kind === 'encrypted';

  const handleRestore = async () => {
    if (isEncrypted && !password) return;
    setBusy(true);
    setErrMsg(null);
    try {
      const result = await localDb.restoreBackupFromBytes(
        rawBytes,
        isEncrypted ? password : null,
        syncAfter
      );

      // Apply restored preferences (exact Android v1.6.21 parity)
      if (result.preferences) {
        const p = result.preferences;
        const nextLang = p.languageTag?.trim().slice(0, 2).toLowerCase();
        const validLang =
          nextLang === 'en' ||
          nextLang === 'de' ||
          nextLang === 'fr' ||
          nextLang === 'it' ||
          nextLang === 'es'
            ? (nextLang as SupportedLang)
            : 'system';
        const restoredPatch: Partial<AppPreferences> = {
          sidebarVisible: p.sidebarVisible ?? undefined,
          dailyDigestEnabled: p.dailyDigestEnabled ?? undefined,
          dailyDigestHour: p.dailyDigestHour ?? undefined,
          dailyDigestMinute: p.dailyDigestMinute ?? undefined,
          language: validLang
        };
        if (p.calendarEventsEnabled != null) {
          restoredPatch.calendarEventsEnabled = Boolean(p.calendarEventsEnabled);
        }
        if (Array.isArray(p.selectedCalendarIds)) {
          restoredPatch.selectedCalendarIds = p.selectedCalendarIds.filter(
            (id): id is string => typeof id === 'string' && id.trim().length > 0
          );
          restoredPatch.calendarIdsInitialized = true;
        }
        onRestoredPrefs(restoredPatch);
      }

      onToast(`✓ Restored ${result.summary.tasksCount} ${t.entityTodos}`);
      onClose();
    } catch (err) {
      setErrMsg(err instanceof Error ? err.message : 'Invalid password or backup file');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Upload size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.backupRestoreBtn}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{fileName}</div>

        {inspection.kind === 'plain' && (
          <div
            style={{
              padding: '10px 12px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              fontSize: 12.5,
              marginBottom: 12
            }}
          >
            <div>
              <strong>{inspection.summary.tasksCount}</strong> {t.entityTodos} •{' '}
              <strong>{inspection.summary.projectsCount}</strong> {t.entityProjects} •{' '}
              <strong>{inspection.summary.areasCount}</strong> {t.entityAreas} •{' '}
              <strong>{inspection.summary.tagsCount}</strong> {t.entityTags}
            </div>
            <div style={{ color: 'var(--text-muted)', marginTop: 3 }}>
              v{inspection.summary.appVersionName} •{' '}
              {new Date(inspection.summary.createdAt).toLocaleString()}
            </div>
          </div>
        )}

        {isEncrypted && (
          <div style={{ position: 'relative', marginBottom: 12 }}>
            <input
              type={showPw ? 'text' : 'password'}
              className="text-field"
              autoFocus
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t.backupPasswordLabel}
            />
            <button
              type="button"
              className="icon-btn"
              style={{ position: 'absolute', right: 4, top: 3 }}
              onClick={() => setShowPw((v) => !v)}
            >
              {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        )}

        {isCloudSignedIn && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 12,
              fontSize: 12.8
            }}
          >
            <span>{t.backupSyncAfterRestore}</span>
            <button
              type="button"
              className={`toggle-switch ${syncAfter ? 'on' : ''}`}
              onClick={() => setSyncAfter((v) => !v)}
            >
              <span className="toggle-switch-knob" />
            </button>
          </div>
        )}

        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 8,
            padding: '10px 12px',
            borderRadius: 'var(--radius-sm)',
            background: 'var(--deadline-red-bg)',
            color: 'var(--deadline-red)',
            fontSize: 12.3,
            marginBottom: 14
          }}
        >
          <AlertTriangle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
          <span>{t.backupOverwriteWarning}</span>
        </div>

        {errMsg && (
          <div style={{ color: 'var(--deadline-red)', fontSize: 12.5, marginBottom: 10 }}>
            {errMsg}
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t.actionCancel}
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy || (isEncrypted && !password)}
            onClick={handleRestore}
          >
            {t.backupRestoreConfirm}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== Main SettingsModal ====================

interface SettingsModalProps {
  prefs: AppPreferences;
  snapshot: DataSnapshot;
  stats: ThingsbookStatistics;
  syncState: CloudSyncSnapshot;
  hasCalendarPermission: boolean;
  availableCalendars: DeviceCalendarInfo[];
  calendarError: string | null;
  t: TranslationStrings;
  onUpdatePrefs: (patch: Partial<AppPreferences>) => void;
  onToggleCalendarEventsEnabled: (enabled: boolean) => void;
  onRequestCalendarAccess: () => Promise<void>;
  onImportIcsFile: (icsText: string, fileName: string) => Promise<void>;
  onRemoveImportedCalendar: (calendarId: string) => void;
  onToggleSelectedCalendarId: (calendarId: string) => void;
  onSelectAllCalendars: (selectAll: boolean) => void;
  onRefreshCalendars: () => Promise<void>;
  onOpenAuthModal: () => void;
  onOpenNotificationsModal: () => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  prefs,
  snapshot,
  stats,
  syncState,
  hasCalendarPermission,
  availableCalendars,
  calendarError,
  t,
  onUpdatePrefs,
  onToggleCalendarEventsEnabled,
  onRequestCalendarAccess,
  onImportIcsFile,
  onRemoveImportedCalendar,
  onToggleSelectedCalendarId,
  onSelectAllCalendars,
  onRefreshCalendars,
  onOpenAuthModal,
  onOpenNotificationsModal,
  onToast,
  onClose
}) => {
  const [backupCreateOpen, setBackupCreateOpen] = useState(false);
  const [restoreTarget, setRestoreTarget] = useState<{
    bytes: Uint8Array;
    name: string;
    inspection: BackupInspection;
  } | null>(null);
  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const [updateInfo, setUpdateInfo] = useState<UpdateCheckResult | null>(null);
  const [connectingGcal, setConnectingGcal] = useState(false);

  const backupInputRef = useRef<HTMLInputElement | null>(null);
  const csvInputRef = useRef<HTMLInputElement | null>(null);
  const icsInputRef = useRef<HTMLInputElement | null>(null);

  const handlePickIcsFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const text = await file.text();
      await onImportIcsFile(text, file.name);
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Invalid .ics calendar file');
    }
  };

  const handlePickBackupFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const buf = await file.arrayBuffer();
      const bytes = new Uint8Array(buf);
      const inspection = await localDb.inspectBackupFile(bytes);
      setRestoreTarget({ bytes, name: file.name, inspection });
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Invalid .tbbackup file');
    }
  };

  const handleExportCsv = (shareMode: boolean) => {
    const csv = localDb.exportCsv();
    const d = new Date();
    const stamp = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`;
    const filename = `thingsbook-export-${stamp}.csv`;
    const file = new File(['\uFEFF' + csv], filename, { type: 'text/csv;charset=utf-8' });

    if (shareMode && navigator.share && navigator.canShare?.({ files: [file] })) {
      void navigator.share({ title: filename, files: [file] });
      return;
    }

    const url = URL.createObjectURL(file);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    onToast(`✓ ${filename}`);
  };

  const handleImportCsvFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const text = await file.text();
    const count = await localDb.importCsv(text);
    onToast(`✓ Imported ${count} ${t.entityTodos}`);
  };

  const handleSearchUpdates = async () => {
    setCheckingUpdate(true);
    try {
      const res = await checkForSwAndReleaseUpdates(APP_VERSION);
      setUpdateInfo(res);
    } finally {
      setCheckingUpdate(false);
    }
  };

  const langOptions: Array<{ value: SupportedLang | 'system'; label: string }> = [
    { value: 'system', label: t.settingsSystemDefault },
    { value: 'en', label: '🇬🇧 English' },
    { value: 'de', label: '🇩🇪 Deutsch' },
    { value: 'it', label: '🇮🇹 Italiano' },
    { value: 'fr', label: '🇫🇷 Français' },
    { value: 'es', label: '🇪🇸 Español' }
  ];

  const themeOptions: Array<{ value: ThemeMode; label: string }> = [
    { value: 'system', label: t.settingsThemeSystem },
    { value: 'light', label: t.settingsThemeLight },
    { value: 'dark', label: t.settingsThemeDark }
  ];

  return (
    <>
      <div className="modal-backdrop" onClick={onClose}>
        <div className="modal-panel wide" onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">
            <div>
              <h3 className="modal-title">{t.settingsTitle}</h3>
              <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
                {t.settingsSubtitle}
              </div>
            </div>
            <button type="button" className="icon-btn" onClick={onClose}>
              <X size={18} />
            </button>
          </div>

          {/* 1. Database & Cloud Sync Mode Cards */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 7 }}>
              {t.settingsDbSyncSection}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <button
                type="button"
                className={`btn-secondary ${prefs.localOnlyDbMode ? 'active' : ''}`}
                style={{ flexDirection: 'column', alignItems: 'flex-start', padding: '10px 12px', textAlign: 'left' }}
                onClick={() => {
                  onUpdatePrefs({ localOnlyDbMode: true });
                  cloudSync.onDatabaseModeChanged(true);
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
                  <Database size={15} />
                  <span>{t.settingsLocalDbTitle}</span>
                </span>
                <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
                  {t.settingsLocalDbDesc}
                </span>
              </button>

              <button
                type="button"
                className={`btn-secondary ${!prefs.localOnlyDbMode ? 'active' : ''}`}
                style={{ flexDirection: 'column', alignItems: 'flex-start', padding: '10px 12px', textAlign: 'left' }}
                onClick={() => {
                  onUpdatePrefs({ localOnlyDbMode: false });
                  cloudSync.onDatabaseModeChanged(false);
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
                  <Cloud size={15} />
                  <span>{t.settingsCloudSyncTitle}</span>
                </span>
                <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
                  {t.settingsCloudSyncDesc}
                </span>
              </button>
            </div>
          </div>

          {/* 2. Account Card (Full Email + Sync Now + Sign Out) */}
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              marginBottom: 16
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 8
              }}
            >
              <div>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', fontWeight: 600 }}>
                  {t.settingsAccountStatus}
                </div>
                <div style={{ fontWeight: 650, fontSize: 13.8, wordBreak: 'break-all' }}>
                  {syncState.userEmailOrId || t.settingsNotSignedIn}
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                {syncState.userId ? (
                  <>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => void cloudSync.syncBidirectionalDeltas(true)}
                    >
                      <RefreshCw size={13.5} />
                      <span>{t.syncNow}</span>
                    </button>
                    <button
                      type="button"
                      className="btn-danger"
                      onClick={() => void cloudSync.signOutCloud()}
                    >
                      <LogOut size={13.5} />
                      <span>{t.authSignOut}</span>
                    </button>
                  </>
                ) : (
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => {
                      onClose();
                      onOpenAuthModal();
                    }}
                  >
                    <Cloud size={14} />
                    <span>{t.settingsOpenAccountSync}</span>
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* 3. Language & Theme Pills + Notifications Shortcut */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 12, marginBottom: 16 }}>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 6 }}>
                {t.settingsLanguage}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {langOptions.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={`chip-btn ${prefs.language === opt.value ? 'active' : ''}`}
                    onClick={() => onUpdatePrefs({ language: opt.value })}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
              <div>
                <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 6 }}>
                  {t.settingsTheme}
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  {themeOptions.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      className={`chip-btn ${prefs.themeMode === opt.value ? 'active' : ''}`}
                      onClick={() => onUpdatePrefs({ themeMode: opt.value })}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  onClose();
                  onOpenNotificationsModal();
                }}
              >
                <Bell size={14} />
                <span>{t.notificationsTitle}</span>
              </button>
            </div>
          </div>

          {/* 3b. Google Calendar & Calendar Events Section (Android v1.6.21–v1.6.23 Parity) */}
          <div
            data-testid="settings-calendar-section"
            style={{
              padding: '12px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              marginBottom: 16
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12.5,
                fontWeight: 700,
                marginBottom: 8
              }}
            >
              <Calendar size={15} style={{ color: 'var(--things-blue)' }} />
              <span>{t.settingsCalendarSection}</span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12
              }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13.3, fontWeight: 600 }}>
                  {t.settingsCalendarEnableTitle}
                </div>
                <div style={{ fontSize: 11.8, color: 'var(--text-secondary)', marginTop: 2 }}>
                  {t.settingsCalendarEnableDesc}
                </div>
              </div>
              <button
                type="button"
                data-testid="settings-calendar-toggle"
                aria-label={t.settingsCalendarEnableTitle}
                className={`toggle-switch ${prefs.calendarEventsEnabled ? 'on' : ''}`}
                onClick={() => onToggleCalendarEventsEnabled(!prefs.calendarEventsEnabled)}
              >
                <span className="toggle-switch-knob" />
              </button>
            </div>

            {prefs.calendarEventsEnabled && (() => {
              const selectedSet = new Set(prefs.selectedCalendarIds);
              const selectedCount = availableCalendars.filter((c) => selectedSet.has(c.id)).length;

              return (
                <div
                  style={{
                    marginTop: 11,
                    paddingTop: 11,
                    borderTop: '1px solid var(--border-subtle)'
                  }}
                >
                  <input
                    ref={icsInputRef}
                    data-testid="settings-calendar-ics-input"
                    type="file"
                    accept=".ics,text/calendar"
                    style={{ display: 'none' }}
                    onChange={handlePickIcsFile}
                  />

                  {calendarError && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 8,
                        padding: '8px 10px',
                        borderRadius: 'var(--radius-xs)',
                        background: 'var(--deadline-red-bg)',
                        color: 'var(--deadline-red)',
                        fontSize: 11.8,
                        marginBottom: 9
                      }}
                    >
                      <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
                      <span>{calendarError}</span>
                    </div>
                  )}

                  {!hasCalendarPermission ? (
                    <div>
                      <div
                        style={{
                          fontSize: 12,
                          color: 'var(--text-secondary)',
                          marginBottom: 9
                        }}
                      >
                        {t.settingsCalendarPermissionDenied}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          className="btn-primary"
                          disabled={connectingGcal}
                          onClick={async () => {
                            setConnectingGcal(true);
                            try {
                              await onRequestCalendarAccess();
                            } finally {
                              setConnectingGcal(false);
                            }
                          }}
                        >
                          <Calendar size={14} />
                          <span>{t.settingsCalendarGrantPermissionBtn}</span>
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => icsInputRef.current?.click()}
                        >
                          <Upload size={14} />
                          <span>{t.settingsCalendarImportIcsBtn}</span>
                        </button>
                      </div>
                    </div>
                  ) : availableCalendars.length === 0 ? (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        flexWrap: 'wrap',
                        gap: 8
                      }}
                    >
                      <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                        {t.settingsCalendarNoneFound}
                      </span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() => void onRefreshCalendars()}
                        >
                          <RefreshCw size={12.5} />
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          disabled={connectingGcal}
                          onClick={async () => {
                            setConnectingGcal(true);
                            try {
                              await onRequestCalendarAccess();
                            } finally {
                              setConnectingGcal(false);
                            }
                          }}
                        >
                          <Calendar size={12.5} />
                          <span>Google</span>
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() => icsInputRef.current?.click()}
                        >
                          <Upload size={12.5} />
                          <span>{t.settingsCalendarImportIcsBtn}</span>
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          flexWrap: 'wrap',
                          gap: 8,
                          marginBottom: 8
                        }}
                      >
                        <span
                          style={{
                            fontSize: 12,
                            fontWeight: 600,
                            color: 'var(--text-secondary)'
                          }}
                        >
                          {t.settingsCalendarSelectHint(selectedCount, availableCalendars.length)}
                        </span>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexWrap: 'wrap' }}>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => onSelectAllCalendars(true)}
                          >
                            {t.settingsCalendarSelectAll}
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => onSelectAllCalendars(false)}
                          >
                            {t.settingsCalendarSelectNone}
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            title="Refresh"
                            onClick={() => void onRefreshCalendars()}
                          >
                            <RefreshCw size={12.5} />
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            disabled={connectingGcal}
                            onClick={async () => {
                              setConnectingGcal(true);
                              try {
                                await onRequestCalendarAccess();
                              } finally {
                                setConnectingGcal(false);
                              }
                            }}
                          >
                            <Calendar size={12} />
                            <span>+ Google</span>
                          </button>
                          <button
                            type="button"
                            className="chip-btn"
                            onClick={() => icsInputRef.current?.click()}
                          >
                            <Upload size={12} />
                            <span>{t.settingsCalendarImportIcsBtn}</span>
                          </button>
                        </div>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                        {availableCalendars.map((cal) => {
                          const isChecked = selectedSet.has(cal.id);
                          return (
                            <div
                              key={cal.id}
                              style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                            >
                              <button
                                type="button"
                                className={`settings-calendar-row ${isChecked ? 'selected' : ''}`}
                                onClick={() => onToggleSelectedCalendarId(cal.id)}
                              >
                                <span
                                  className="settings-calendar-dot"
                                  style={{ backgroundColor: cal.colorCss }}
                                />
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div
                                    style={{
                                      fontSize: 13,
                                      fontWeight: isChecked ? 650 : 500,
                                      overflow: 'hidden',
                                      textOverflow: 'ellipsis',
                                      whiteSpace: 'nowrap'
                                    }}
                                  >
                                    {cal.displayName}
                                  </div>
                                  {cal.accountName && (
                                    <div
                                      style={{
                                        fontSize: 11,
                                        color: 'var(--text-muted)',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap'
                                      }}
                                    >
                                      {cal.accountName}
                                    </div>
                                  )}
                                </div>
                                <span
                                  style={{
                                    width: 18,
                                    height: 18,
                                    borderRadius: 5,
                                    border: `1.5px solid ${
                                      isChecked ? 'var(--things-blue)' : 'var(--checkbox-border)'
                                    }`,
                                    background: isChecked ? 'var(--things-blue)' : 'transparent',
                                    color: '#fff',
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    flexShrink: 0
                                  }}
                                >
                                  {isChecked && <Check size={12} strokeWidth={3} />}
                                </span>
                              </button>

                              {cal.source === 'ics' && (
                                <button
                                  type="button"
                                  className="icon-btn"
                                  title={t.actionDelete}
                                  style={{ color: 'var(--deadline-red)', flexShrink: 0 }}
                                  onClick={() => onRemoveImportedCalendar(cal.id)}
                                >
                                  <Trash2 size={14} />
                                </button>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              );
            })()}
          </div>

          {/* 4. Sample Tasks Cleanup Card (when hasSampleTasks) */}
          {snapshot.hasSampleTasks && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 10,
                padding: '10px 12px',
                borderRadius: 'var(--radius-sm)',
                background: 'var(--bg-subtle)',
                border: '1px solid var(--border-subtle)',
                marginBottom: 16
              }}
            >
              <span style={{ fontSize: 12.8 }}>{t.setupBannerDeleteSamplesBtn}</span>
              <button
                type="button"
                className="btn-danger"
                onClick={async () => {
                  await localDb.purgeSampleDataLocally(false);
                  onToast('✓ Sample tasks removed');
                }}
              >
                <Trash2 size={13.5} />
                <span>{t.setupBannerDeleteSamplesBtn}</span>
              </button>
            </div>
          )}

          {/* 5. App Updates Section (v1.6.16) */}
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              marginBottom: 16
            }}
          >
            <div style={{ fontSize: 12.5, fontWeight: 700, marginBottom: 8 }}>
              {t.settingsUpdatesSection}
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 10,
                marginBottom: 10
              }}
            >
              <span style={{ fontSize: 13 }}>{t.settingsAutoUpdateLabel}</span>
              <button
                type="button"
                className={`toggle-switch ${prefs.autoCheckUpdatesEnabled ? 'on' : ''}`}
                onClick={() =>
                  onUpdatePrefs({ autoCheckUpdatesEnabled: !prefs.autoCheckUpdatesEnabled })
                }
              >
                <span className="toggle-switch-knob" />
              </button>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn-secondary"
                disabled={checkingUpdate}
                onClick={handleSearchUpdates}
              >
                <RefreshCw size={13.5} />
                <span>
                  {checkingUpdate ? t.settingsCheckingUpdates : t.settingsSearchUpdatesBtn}
                </span>
              </button>

              {updateInfo && !updateInfo.hasUpdate && (
                <span style={{ fontSize: 12.5, color: 'var(--logbook-green)', fontWeight: 600 }}>
                  ✓ {t.settingsUpToDate(APP_VERSION)}
                </span>
              )}

              {updateInfo && updateInfo.hasUpdate && (
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => void applyPendingSwUpdate()}
                >
                  {t.settingsApplyUpdateBtn} (v{updateInfo.latestVersion})
                </button>
              )}
            </div>
          </div>

          {/* 6. 14-Field Statistics (4 KPI Cards + 10 Detail Rows) */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 12.5, fontWeight: 700 }}>{t.settingsStatsSection}</div>
            <div className="stats-kpi-grid">
              <div className="stat-tile">
                <div className="stat-tile-value" style={{ color: 'var(--things-blue)' }}>
                  {stats.openTasksCount}
                </div>
                <div className="stat-tile-label">{t.statOpen}</div>
              </div>
              <div className="stat-tile">
                <div className="stat-tile-value" style={{ color: 'var(--logbook-green)' }}>
                  {stats.completedTasksCount}
                </div>
                <div className="stat-tile-label">{t.statCompleted}</div>
              </div>
              <div className="stat-tile">
                <div className="stat-tile-value">{stats.canceledTasksCount}</div>
                <div className="stat-tile-label">{t.statCanceled}</div>
              </div>
              <div className="stat-tile">
                <div className="stat-tile-value" style={{ color: 'var(--text-muted)' }}>
                  {stats.deletedTasksCount}
                </div>
                <div className="stat-tile-label">{t.statDeleted}</div>
              </div>
            </div>

            <div className="stats-detail-list">
              <div className="stats-detail-row">
                <span>{t.statTodayActive}</span>
                <strong>{stats.todayTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statUpcoming}</span>
                <strong>{stats.upcomingTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statAnytime}</span>
                <strong>{stats.anytimeTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statInbox}</span>
                <strong>{stats.inboxTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statSomeday}</span>
                <strong>{stats.somedayTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statRecurring}</span>
                <strong>{stats.recurringTasksCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statActiveProjects}</span>
                <strong>{stats.activeProjectsCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statAreas}</span>
                <strong>{stats.areasCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statSavedTags}</span>
                <strong>{stats.tagsCount}</strong>
              </div>
              <div className="stats-detail-row">
                <span>{t.statTotalRecords}</span>
                <strong>{stats.totalRecordsCount}</strong>
              </div>
            </div>
          </div>

          {/* 7. Backup & Restore (.tbbackup) */}
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              marginBottom: 14
            }}
          >
            <div style={{ fontSize: 12.8, fontWeight: 700, marginBottom: 4 }}>
              {t.backupSectionTitle}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 10 }}>
              {t.backupSectionDesc}
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn-primary"
                onClick={() => setBackupCreateOpen(true)}
              >
                <Download size={14} />
                <span>{t.backupCreateBtn}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => backupInputRef.current?.click()}
              >
                <Upload size={14} />
                <span>{t.backupRestoreBtn}</span>
              </button>
              <input
                ref={backupInputRef}
                type="file"
                accept=".tbbackup,application/octet-stream"
                style={{ display: 'none' }}
                onChange={handlePickBackupFile}
              />
            </div>
          </div>

          {/* 8. CSV Import & Export */}
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)',
              marginBottom: 14
            }}
          >
            <div style={{ fontSize: 12.8, fontWeight: 700, marginBottom: 4 }}>
              {t.csvSectionTitle}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 10 }}>
              {t.csvSectionDesc}
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => handleExportCsv(false)}
              >
                <FileSpreadsheet size={14} />
                <span>{t.csvExportBtn}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => handleExportCsv(true)}
              >
                <Share2 size={14} />
                <span>{t.csvShareBtn}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => csvInputRef.current?.click()}
              >
                <Upload size={14} />
                <span>{t.csvImportBtn}</span>
              </button>
              <input
                ref={csvInputRef}
                type="file"
                accept=".csv,text/csv"
                style={{ display: 'none' }}
                onChange={handleImportCsvFile}
              />
            </div>
          </div>

          {/* 9. Gemini API Key */}
          <div style={{ marginBottom: 14 }}>
            <label
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 12.2,
                fontWeight: 600,
                color: 'var(--text-secondary)',
                marginBottom: 5
              }}
            >
              <KeyRound size={13} style={{ color: 'var(--gemini-violet)' }} />
              <span>{t.geminiApiKeyLabel}</span>
            </label>
            <input
              type="password"
              className="text-field"
              value={prefs.geminiApiKey}
              onChange={(e) => onUpdatePrefs({ geminiApiKey: e.target.value })}
              placeholder={t.geminiApiKeyPlaceholder}
            />
          </div>

          {/* Version Footer */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingTop: 10,
              borderTop: '1px solid var(--border-subtle)',
              fontSize: 11.8,
              color: 'var(--text-muted)'
            }}
          >
            <span>
              Thingsbook v{APP_VERSION} (Build {APP_BUILD_CODE})
            </span>
            <button type="button" className="btn-primary" onClick={onClose}>
              {t.actionDone}
            </button>
          </div>
        </div>
      </div>

      {backupCreateOpen && (
        <BackupCreateModal
          prefs={prefs}
          stats={stats}
          t={t}
          onToast={onToast}
          onClose={() => setBackupCreateOpen(false)}
        />
      )}

      {restoreTarget && (
        <BackupRestoreModal
          rawBytes={restoreTarget.bytes}
          fileName={restoreTarget.name}
          inspection={restoreTarget.inspection}
          isCloudSignedIn={Boolean(syncState.userId)}
          t={t}
          onRestoredPrefs={onUpdatePrefs}
          onToast={onToast}
          onClose={() => setRestoreTarget(null)}
        />
      )}
    </>
  );
};

// ==================== AuthSyncModal + 3-Choice SampleTasksAccountMergeDialog ====================

interface AuthSyncModalProps {
  syncState: CloudSyncSnapshot;
  hasSampleTasks: boolean;
  t: TranslationStrings;
  onEnableCloudMode: () => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

type PendingAuthAction =
  | 'register'
  | 'login'
  | 'google'
  | 'instant'
  | 'verify-google-password';

export const AuthSyncModal: React.FC<AuthSyncModalProps> = ({
  syncState,
  hasSampleTasks,
  t,
  onEnableCloudMode,
  onToast,
  onClose
}) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showGooglePasswordRecovery, setShowGooglePasswordRecovery] = useState(false);
  const [pendingMergeAction, setPendingMergeAction] = useState<PendingAuthAction | null>(null);
  const [chosenMergePref, setChosenMergePref] = useState<boolean | null>(null);

  const executeAuth = async (action: PendingAuthAction, mergeLocalTasks: boolean) => {
    setBusy(true);
    setError(null);
    onEnableCloudMode();
    try {
      let signedInAs = '';
      if (action === 'register') {
        signedInAs = await cloudSync.registerEmailAccount(email, password, mergeLocalTasks);
      } else if (action === 'login') {
        signedInAs = await cloudSync.signInEmailAccount(email, password, mergeLocalTasks);
      } else if (action === 'verify-google-password') {
        signedInAs = await cloudSync.verifyGoogleAndLinkPassword(
          email,
          password,
          mergeLocalTasks
        );
      } else if (action === 'google') {
        signedInAs = await cloudSync.signInWithGooglePopup(mergeLocalTasks);
      } else {
        signedInAs = await cloudSync.signInInstantEmail(email, mergeLocalTasks);
      }
      setShowGooglePasswordRecovery(false);
      onToast(`✓ ${signedInAs}`);
      onClose();
    } catch (err) {
      setError(formatAuthErrorMessage(err));
      if (
        (action === 'login' || action === 'register' || action === 'verify-google-password') &&
        isPasswordRecoveryCandidateError(err)
      ) {
        setShowGooglePasswordRecovery(true);
      }
    } finally {
      setBusy(false);
    }
  };

  const initiateAuth = (action: PendingAuthAction) => {
    if (hasSampleTasks && chosenMergePref === null) {
      setPendingMergeAction(action);
    } else {
      void executeAuth(action, chosenMergePref ?? true);
    }
  };

  // 3-Choice SampleTasksAccountMergeDialog
  if (pendingMergeAction) {
    return (
      <div className="modal-backdrop" onClick={() => setPendingMergeAction(null)}>
        <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">
            <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Sparkles size={18} style={{ color: 'var(--things-blue)' }} />
              <span>{t.mergeDialogTitle}</span>
            </h3>
            <button
              type="button"
              className="icon-btn"
              onClick={() => setPendingMergeAction(null)}
            >
              <X size={18} />
            </button>
          </div>

          <p style={{ fontSize: 13.3, color: 'var(--text-secondary)', marginTop: 0, marginBottom: 16 }}>
            {t.mergeDialogDesc}
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button
              type="button"
              className="btn-primary"
              disabled={busy}
              onClick={() => {
                const act = pendingMergeAction;
                setChosenMergePref(true);
                setPendingMergeAction(null);
                void executeAuth(act, true);
              }}
            >
              {t.mergeKeepBothBtn}
            </button>
            <button
              type="button"
              className="btn-secondary"
              disabled={busy}
              onClick={() => {
                const act = pendingMergeAction;
                setChosenMergePref(false);
                setPendingMergeAction(null);
                void executeAuth(act, false);
              }}
            >
              {t.mergeAccountOnlyBtn}
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setPendingMergeAction(null)}
            >
              {t.actionCancel}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Cloud size={18} style={{ color: 'var(--things-blue)' }} />
            <span>{t.syncTitle}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <p style={{ fontSize: 12.8, color: 'var(--text-secondary)', marginTop: 0, marginBottom: 14 }}>
          {t.authMultiDeviceHint}
        </p>

        {syncState.userId ? (
          <div
            style={{
              padding: '14px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-subtle)'
            }}
          >
            <div style={{ fontWeight: 650, marginBottom: 4 }}>{syncState.userEmailOrId}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 12 }}>
              {syncState.status === 'SYNCED'
                ? t.syncStatusSynced
                : syncState.status === 'SYNCING'
                  ? t.syncStatusSyncing
                  : t.syncStatusOffline}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                type="button"
                className="btn-primary"
                onClick={() => void cloudSync.syncBidirectionalDeltas(true)}
              >
                <RefreshCw size={14} />
                <span>{t.syncNow}</span>
              </button>
              <button
                type="button"
                className="btn-danger"
                onClick={() => void cloudSync.signOutCloud()}
              >
                <LogOut size={14} />
                <span>{t.authSignOut}</span>
              </button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button
              type="button"
              className="btn-primary"
              disabled={busy}
              onClick={() => initiateAuth('google')}
            >
              {t.authSignInGoogle}
            </button>

            <div className="context-menu-divider" />

            <input
              type="email"
              className="text-field"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (error) setError(null);
              }}
              placeholder={t.authEmailLabel}
            />
            <input
              type="password"
              className="text-field"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (error) setError(null);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !busy && email.trim() && password.length >= 6) {
                  e.preventDefault();
                  initiateAuth('login');
                }
              }}
              placeholder={t.authPasswordLabel}
            />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <button
                type="button"
                className="btn-primary"
                disabled={busy || !email.trim() || password.length < 6}
                onClick={() => initiateAuth('login')}
              >
                {t.authLoginEmail}
              </button>
              <button
                type="button"
                className="btn-secondary"
                disabled={busy || !email.trim() || password.length < 6}
                onClick={() => initiateAuth('register')}
              >
                {t.authRegisterAccount}
              </button>
            </div>

            {showGooglePasswordRecovery && email.trim() && password.length >= 6 && (
              <div
                data-testid="auth-google-password-recovery-box"
                style={{
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-subtle)',
                  border: '1px solid var(--things-blue)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8
                }}
              >
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                  {t.authGoogleRestoreHint}
                </div>
                <button
                  type="button"
                  data-testid="auth-verify-google-restore-btn"
                  className="btn-primary"
                  disabled={busy}
                  onClick={() => initiateAuth('verify-google-password')}
                >
                  <ShieldCheck size={14} />
                  <span>{t.authVerifyGoogleRestoreBtn}</span>
                </button>
              </div>
            )}

            <button
              type="button"
              className="btn-secondary"
              disabled={busy || !email.trim()}
              onClick={() => initiateAuth('instant')}
            >
              {t.authInstantCloud}
            </button>

            <button
              type="button"
              style={{ fontSize: 12, color: 'var(--things-blue)', textAlign: 'left' }}
              onClick={async () => {
                try {
                  await cloudSync.sendResetEmail(email);
                  onToast('✓ Password reset email sent');
                } catch (err) {
                  setError(formatAuthErrorMessage(err));
                }
              }}
            >
              {t.authForgotPassword}
            </button>

            {error && (
              <div
                data-testid="auth-error-message"
                style={{ fontSize: 12.5, color: 'var(--deadline-red)' }}
              >
                {error}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
