import React, { useState } from 'react';
import {
  Share2,
  X,
  Inbox,
  Star,
  Moon,
  Layers,
  Archive,
  Folder,
  Package,
  Timer,
  Pause,
  Play,
  Check,
  RotateCcw,
  Trash2,
  Lock,
  Fingerprint,
  KeyRound,
  BellRing,
  Clock
} from 'lucide-react';
import type {
  AreaRecord,
  FocusTimerUiState,
  ProjectRecord,
  QuickShareDraft,
  ScheduleState,
  TaskRecord
} from '../domain/types';
import type { TranslationStrings } from '../i18n/translations';
import { MarkdownNotesField } from './MarkdownNotesField';
import {
  registerPlatformWebAuthnCredential,
  sha256Hex,
  verifyPlatformWebAuthnCredential
} from '../domain/powerUtils';

// ==================== 1. Quick Share / Save to Thingsbook Modal ====================

export const QuickShareModal: React.FC<{
  draft: QuickShareDraft;
  projects: ProjectRecord[];
  areas: AreaRecord[];
  t: TranslationStrings;
  onClose: () => void;
  onSave: (payload: {
    title: string;
    notes: string;
    scheduleState: ScheduleState;
    projectId: string | null;
    areaId: string | null;
  }) => void;
}> = ({ draft, projects, areas, t, onClose, onSave }) => {
  const [title, setTitle] = useState(draft.initialTitle);
  const [notes, setNotes] = useState(draft.initialNotes);
  const [scheduleState, setScheduleState] = useState<ScheduleState>('INBOX');
  const [projectId, setProjectId] = useState<string | null>(null);
  const [areaId, setAreaId] = useState<string | null>(null);

  const activeProjects = projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE');
  const activeAreas = areas.filter((a) => !a.isDeleted);

  const scheduleOptions: Array<{ state: ScheduleState; label: string; icon: React.ReactNode }> = [
    {
      state: 'INBOX',
      label: t.viewInbox,
      icon: <Inbox size={14} style={{ color: 'var(--things-blue)' }} />
    },
    {
      state: 'TODAY',
      label: t.viewToday,
      icon: <Star size={14} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
    },
    {
      state: 'THIS_EVENING',
      label: t.viewThisEvening,
      icon: <Moon size={14} style={{ color: 'var(--evening-moon)' }} />
    },
    {
      state: 'ANYTIME',
      label: t.viewAnytime,
      icon: <Layers size={14} style={{ color: 'var(--anytime-teal)' }} />
    },
    {
      state: 'SOMEDAY',
      label: t.viewSomeday,
      icon: <Archive size={14} style={{ color: 'var(--someday-amber)' }} />
    }
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const resolvedTitle = title.trim() || t.entityNewTodo;
    onSave({
      title: resolvedTitle,
      notes: notes.trim(),
      scheduleState,
      projectId,
      areaId
    });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel quick-share-modal"
        data-testid="quick-share-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Share2 size={18} style={{ color: 'var(--things-blue)' }} />
              <span>{t.quickShareTitle}</span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {t.quickShareSubtitle}
            </div>
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
            <input
              type="text"
              className="text-field"
              placeholder={t.placeholderTodoTitle}
              value={title}
              autoFocus
              onChange={(e) => setTitle(e.target.value)}
            />

            <div className="quick-share-notes-box">
              <MarkdownNotesField
                value={notes}
                onChange={setNotes}
                placeholder={t.placeholderNotes}
                rows={3}
                className="task-card-notes"
              />
            </div>

            <div>
              <div className="quick-share-section-label">{t.quickShareDestinationLabel}</div>
              <div className="quick-share-pills">
                {scheduleOptions.map((opt) => {
                  const active =
                    scheduleState === opt.state && projectId === null && areaId === null;
                  return (
                    <button
                      key={opt.state}
                      type="button"
                      className={`chip-btn ${active ? 'active' : ''}`}
                      onClick={() => {
                        setScheduleState(opt.state);
                        setProjectId(null);
                        setAreaId(null);
                      }}
                    >
                      {opt.icon}
                      <span>{opt.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {(activeProjects.length > 0 || activeAreas.length > 0) && (
              <div className="quick-share-pills">
                {activeProjects.map((proj) => {
                  const active = projectId === proj.id;
                  return (
                    <button
                      key={proj.id}
                      type="button"
                      className={`chip-btn ${active ? 'active' : ''}`}
                      onClick={() => {
                        setProjectId(proj.id);
                        setAreaId(proj.areaId);
                        if (scheduleState === 'INBOX') setScheduleState('ANYTIME');
                      }}
                    >
                      <Folder size={12.5} style={{ color: 'var(--things-blue)' }} />
                      <span>{proj.title}</span>
                    </button>
                  );
                })}
                {activeAreas.map((area) => {
                  const active = areaId === area.id && projectId === null;
                  return (
                    <button
                      key={area.id}
                      type="button"
                      className={`chip-btn ${active ? 'active' : ''}`}
                      onClick={() => {
                        setAreaId(area.id);
                        setProjectId(null);
                        if (scheduleState === 'INBOX') setScheduleState('ANYTIME');
                      }}
                    >
                      <Package size={12.5} style={{ color: 'var(--anytime-teal)' }} />
                      <span>{area.title}</span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn-secondary" onClick={onClose}>
              {t.actionCancel}
            </button>
            <button type="submit" className="btn-primary">
              {t.actionSave}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ==================== 2. Built-in 25-Minute Focus / Pomodoro Timer Banner ====================

export const FocusTimerBanner: React.FC<{
  timer: FocusTimerUiState;
  t: TranslationStrings;
  onTogglePause: () => void;
  onAddFiveMinutes: () => void;
  onCompleteTask: () => void;
  onStop: () => void;
  onSelectTask: (taskId: string) => void;
}> = ({
  timer,
  t,
  onTogglePause,
  onAddFiveMinutes,
  onCompleteTask,
  onStop,
  onSelectTask
}) => {
  const mins = Math.floor(timer.remainingSeconds / 60);
  const secs = timer.remainingSeconds % 60;
  const formattedTime = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  const isFinished = timer.remainingSeconds <= 0;
  const progressPct =
    timer.totalSeconds > 0
      ? Math.min(
          100,
          Math.max(0, ((timer.totalSeconds - timer.remainingSeconds) / timer.totalSeconds) * 100)
        )
      : 100;

  return (
    <div
      className={`focus-timer-banner ${isFinished ? 'finished' : ''}`}
      data-testid="focus-timer-banner"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="focus-timer-row">
        <button
          type="button"
          className="focus-timer-main"
          onClick={() => onSelectTask(timer.taskId)}
        >
          <span className="focus-timer-badge">
            <Timer size={15} />
            <span className="focus-timer-clock">{formattedTime}</span>
          </span>
          <span className="focus-timer-info">
            <span className="focus-timer-label">
              {isFinished ? t.focusTimerFinishedTitle : t.focusTimerActiveLabel}
            </span>
            <span className="focus-timer-task-title">{timer.taskTitle || t.entityNewTodo}</span>
          </span>
        </button>

        <div className="focus-timer-actions">
          {!isFinished && (
            <button
              type="button"
              className="chip-btn"
              onClick={onTogglePause}
              title={timer.isRunning ? t.focusTimerPause : t.focusTimerResume}
            >
              {timer.isRunning ? <Pause size={12.5} /> : <Play size={12.5} />}
              <span>{timer.isRunning ? t.focusTimerPause : t.focusTimerResume}</span>
            </button>
          )}
          <button
            type="button"
            className="chip-btn"
            onClick={onAddFiveMinutes}
            title={t.focusTimerPlus5m}
          >
            <span>{t.focusTimerPlus5m}</span>
          </button>
          <button
            type="button"
            className="chip-btn active"
            onClick={onCompleteTask}
            title={t.actionComplete}
          >
            <Check size={13} />
          </button>
          <button
            type="button"
            className="icon-btn"
            style={{ height: 26, minWidth: 26, padding: 0 }}
            onClick={onStop}
            title={t.focusTimerStop}
          >
            <X size={14} />
          </button>
        </div>
      </div>

      <div className="focus-timer-progress-track">
        <div className="focus-timer-progress-fill" style={{ width: `${progressPct}%` }} />
      </div>
    </div>
  );
};

// ==================== 3. Undo Snackbar Banner ====================

export const UndoSnackbarBanner: React.FC<{
  message: string;
  t: TranslationStrings;
  onUndo: () => void;
  onDismiss: () => void;
}> = ({ message, t, onUndo, onDismiss }) => {
  return (
    <div
      className="undo-snackbar"
      role="status"
      data-testid="undo-snackbar"
      onClick={(e) => e.stopPropagation()}
    >
      <span className="undo-snackbar-msg">{message}</span>
      <div className="undo-snackbar-actions">
        <button
          type="button"
          className="undo-snackbar-btn"
          data-testid="undo-snackbar-btn"
          onClick={onUndo}
        >
          <RotateCcw size={13} />
          <span>{t.actionUndo}</span>
        </button>
        <button
          type="button"
          className="undo-snackbar-close"
          onClick={onDismiss}
          aria-label="Dismiss"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  );
};

// ==================== 4. Trash View & Empty Trash Confirmation ====================

export const TrashViewList: React.FC<{
  deletedProjects: ProjectRecord[];
  deletedTasks: TaskRecord[];
  t: TranslationStrings;
  onRestoreProject: (projectId: string) => void;
  onDeleteProjectForever: (projectId: string) => void;
  onRestoreTask: (taskId: string) => void;
  onDeleteTaskForever: (taskId: string) => void;
}> = ({
  deletedProjects,
  deletedTasks,
  t,
  onRestoreProject,
  onDeleteProjectForever,
  onRestoreTask,
  onDeleteTaskForever
}) => {
  const combined = [
    ...deletedProjects.map((p) => ({ kind: 'project' as const, updatedAt: p.updatedAt, item: p })),
    ...deletedTasks.map((task) => ({ kind: 'task' as const, updatedAt: task.updatedAt, item: task }))
  ].sort((a, b) => b.updatedAt - a.updatedAt);

  if (combined.length === 0) {
    return (
      <div className="empty-state" data-testid="trash-empty-state">
        <div className="empty-state-icon">
          <Trash2 size={38} strokeWidth={1.5} />
        </div>
        <div>{t.trashEmptyState}</div>
      </div>
    );
  }

  return (
    <div className="trash-list" data-testid="trash-list" onClick={(e) => e.stopPropagation()}>
      {combined.map((entry) => {
        if (entry.kind === 'project') {
          const proj = entry.item;
          return (
            <div key={`proj-${proj.id}`} className="trash-item-card">
              <div className="trash-item-left">
                <Folder size={16} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
                <div style={{ minWidth: 0 }}>
                  <div className="trash-item-title">{proj.title || t.entityNewProject}</div>
                  <div className="trash-item-meta">{t.entityProject}</div>
                </div>
              </div>
              <div className="trash-item-actions">
                <button
                  type="button"
                  className="chip-btn"
                  onClick={() => onRestoreProject(proj.id)}
                >
                  <RotateCcw size={12.5} />
                  <span>{t.trashRestoreBtn}</span>
                </button>
                <button
                  type="button"
                  className="chip-btn urgent"
                  onClick={() => onDeleteProjectForever(proj.id)}
                >
                  <Trash2 size={12.5} />
                  <span>{t.trashDeleteForeverBtn}</span>
                </button>
              </div>
            </div>
          );
        }

        const task = entry.item;
        return (
          <div key={`task-${task.id}`} className="trash-item-card">
            <div className="trash-item-left">
              <span className="trash-task-circle" />
              <div style={{ minWidth: 0 }}>
                <div className="trash-item-title">{task.title || t.entityNewTodo}</div>
                {task.notes && (
                  <div className="trash-item-meta">
                    {task.notes.length > 80 ? `${task.notes.slice(0, 80)}…` : task.notes}
                  </div>
                )}
              </div>
            </div>
            <div className="trash-item-actions">
              <button
                type="button"
                className="chip-btn"
                onClick={() => onRestoreTask(task.id)}
              >
                <RotateCcw size={12.5} />
                <span>{t.trashRestoreBtn}</span>
              </button>
              <button
                type="button"
                className="chip-btn urgent"
                onClick={() => onDeleteTaskForever(task.id)}
              >
                <Trash2 size={12.5} />
                <span>{t.trashDeleteForeverBtn}</span>
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export const EmptyTrashConfirmDialog: React.FC<{
  count: number;
  t: TranslationStrings;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ count, t, onConfirm, onCancel }) => {
  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal-panel" style={{ maxWidth: 400 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">{t.trashEmptyBtn}</h3>
          <button type="button" className="icon-btn" onClick={onCancel}>
            <X size={18} />
          </button>
        </div>
        <p style={{ margin: '0 0 16px', fontSize: 13.5, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
          {t.trashDeleteForeverBtn} ({count})?
        </p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" className="btn-secondary" onClick={onCancel}>
            {t.actionCancel}
          </button>
          <button type="button" className="btn-danger" onClick={onConfirm}>
            {t.trashEmptyBtn}
          </button>
        </div>
      </div>
    </div>
  );
};

// ==================== 5. Biometric / PIN App Lock Overlay & Setup ====================

export const AppLockPinSetupModal: React.FC<{
  t: TranslationStrings;
  onCancel: () => void;
  onConfirm: (pinHash: string, webAuthnCredId: string) => void;
}> = ({ t, onCancel, onConfirm }) => {
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = pin.trim();
    if (trimmed.length < 4) return;
    setBusy(true);
    const hash = await sha256Hex(trimmed);
    const credId = await registerPlatformWebAuthnCredential();
    setBusy(false);
    onConfirm(hash, credId);
  };

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div
        className="modal-panel"
        style={{ maxWidth: 400 }}
        data-testid="app-lock-setup-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Lock size={17} style={{ color: 'var(--things-blue)' }} />
            <span>{t.appLockSetupPinTitle}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onCancel}>
            <X size={18} />
          </button>
        </div>
        <form onSubmit={handleSave}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
            <div style={{ fontSize: 12.8, color: 'var(--text-secondary)', lineHeight: 1.45 }}>
              {t.settingsBiometricLockDesc}
            </div>
            <input
              type="password"
              inputMode="numeric"
              maxLength={8}
              className="text-field"
              placeholder={t.appLockPinPlaceholder}
              value={pin}
              autoFocus
              onChange={(e) => setPin(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn-secondary" onClick={onCancel}>
              {t.actionCancel}
            </button>
            <button type="submit" className="btn-primary" disabled={pin.trim().length < 4 || busy}>
              {t.actionSave}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export const AppLockOverlay: React.FC<{
  pinHash: string;
  webAuthnCredId: string;
  t: TranslationStrings;
  onUnlocked: () => void;
}> = ({ pinHash, webAuthnCredId, t, onUnlocked }) => {
  const [pin, setPin] = useState('');
  const [error, setError] = useState(false);

  const handlePinSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pinHash) {
      onUnlocked();
      return;
    }
    const candidate = await sha256Hex(pin.trim());
    if (candidate === pinHash) {
      setError(false);
      setPin('');
      onUnlocked();
    } else {
      setError(true);
    }
  };

  const handleBiometricUnlock = async () => {
    if (!webAuthnCredId) return;
    const ok = await verifyPlatformWebAuthnCredential(webAuthnCredId);
    if (ok) {
      setError(false);
      onUnlocked();
    }
  };

  return (
    <div className="app-lock-overlay" data-testid="app-lock-overlay">
      <div className="app-lock-card">
        <div className="app-lock-icon-circle">
          <Lock size={28} style={{ color: 'var(--things-blue)' }} />
        </div>
        <h2 className="app-lock-title">{t.appLockTitle}</h2>
        <p className="app-lock-subtitle">{t.appLockSubtitle}</p>

        <form onSubmit={handlePinSubmit} className="app-lock-form">
          {pinHash && (
            <div className="app-lock-pin-row">
              <KeyRound size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
              <input
                type="password"
                inputMode="numeric"
                maxLength={8}
                className={`text-field ${error ? 'input-error' : ''}`}
                placeholder={t.appLockPinPlaceholder}
                value={pin}
                autoFocus
                onChange={(e) => {
                  setPin(e.target.value);
                  if (error) setError(false);
                }}
              />
            </div>
          )}

          <button
            type="submit"
            className="btn-primary"
            style={{ width: '100%', justifyContent: 'center' }}
          >
            {t.appLockUnlockBtn}
          </button>

          {webAuthnCredId && (
            <button
              type="button"
              className="btn-secondary"
              style={{ width: '100%', justifyContent: 'center' }}
              onClick={handleBiometricUnlock}
            >
              <Fingerprint size={16} />
              <span>{t.appLockUnlockBtn}</span>
            </button>
          )}
        </form>
      </div>
    </div>
  );
};

// ==================== 6. In-App Actionable Reminder Toast ====================

export const ActionableReminderBanner: React.FC<{
  reminders: Array<{ taskId: string; title: string }>;
  t: TranslationStrings;
  onComplete: (taskId: string) => void;
  onSnooze1h: (taskId: string) => void;
  onTomorrow: (taskId: string) => void;
  onDismiss: (taskId: string) => void;
}> = ({ reminders, t, onComplete, onSnooze1h, onTomorrow, onDismiss }) => {
  if (reminders.length === 0) return null;
  const active = reminders[0];
  return (
    <div
      className="actionable-reminder-banner"
      data-testid="actionable-reminder-banner"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="actionable-reminder-head">
        <BellRing size={15} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
        <span className="actionable-reminder-title">{active.title}</span>
        <button
          type="button"
          className="icon-btn"
          style={{ height: 24, minWidth: 24, padding: 0 }}
          onClick={() => onDismiss(active.taskId)}
        >
          <X size={14} />
        </button>
      </div>
      <div className="actionable-reminder-actions">
        <button
          type="button"
          className="chip-btn active"
          onClick={() => onComplete(active.taskId)}
        >
          <Check size={12.5} />
          <span>{t.actionComplete}</span>
        </button>
        <button
          type="button"
          className="chip-btn"
          onClick={() => onSnooze1h(active.taskId)}
        >
          <Clock size={12.5} />
          <span>{t.notificationSnooze1h}</span>
        </button>
        <button
          type="button"
          className="chip-btn"
          onClick={() => onTomorrow(active.taskId)}
        >
          <span>{t.dateTomorrow}</span>
        </button>
      </div>
    </div>
  );
};
