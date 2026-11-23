import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Share2,
  X,
  Inbox,
  Sun,
  Moon,
  Compass,
  Cloud,
  Folder,
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
  Clock,
  Mic,
  MicOff,
  Sparkles,
  CalendarDays,
  Flag,
  Hourglass,
  Diamond,
  LayoutGrid,
  Maximize2,
  Minimize2,
  Coffee,
  CheckSquare,
  Circle,
  Plus
} from 'lucide-react';
import type {
  AreaRecord,
  ChecklistItemRecord,
  FocusTimerUiState,
  ProjectRecord,
  QuickShareDraft,
  ScheduleState,
  SupportedLang,
  TagRecord,
  TaskRecord
} from '../domain/types';
import type { TranslationStrings } from '../i18n/translations';
import {
  DetectedNotesLinksRow,
  FormattedMarkdownText,
  MarkdownNotesField
} from './MarkdownNotesField';
import {
  extractDetectedLinksFromNotes,
  registerPlatformWebAuthnCredential,
  sha256Hex,
  verifyPlatformWebAuthnCredential
} from '../domain/powerUtils';
import { parseSmartTaskSyntax } from '../domain/smartSyntaxParser';
import { formatStandaloneDuration } from '../domain/v2Utils';
import { localDb } from '../data/localDb';

const SPEECH_BCP47_BY_LANG: Record<SupportedLang, string> = {
  en: 'en-US',
  de: 'de-DE',
  fr: 'fr-FR',
  it: 'it-IT',
  es: 'es-ES'
};

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionLike {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((ev: SpeechRecognitionEventLike) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}

function getSpeechRecognitionCtor(): (new () => SpeechRecognitionLike) | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

// ==================== 1. Quick Share / Save to Thingsbook Modal ====================

export interface SmartParseDestination {
  projectId: string | null;
  areaId: string | null;
  scheduleState: ScheduleState;
}

export interface QuickShareSavePayload {
  title: string;
  notes: string;
  scheduleState: ScheduleState;
  whenDate?: number | null;
  reminderTime?: number | null;
  deadline?: number | null;
  durationMinutes?: number | null;
  tagIds?: string[];
  isMilestone?: boolean;
  projectId: string | null;
  areaId: string | null;
}

export const QuickShareModal: React.FC<{
  draft: QuickShareDraft;
  projects: ProjectRecord[];
  areas: AreaRecord[];
  tags?: TagRecord[];
  lang?: SupportedLang;
  t: TranslationStrings;
  onClose: () => void;
  onToast?: (msg: string) => void;
  onSave: (payload: QuickShareSavePayload) => void;
  onSmartParseWithGemini?: (
    rawText: string,
    destination: SmartParseDestination
  ) => Promise<void>;
}> = ({
  draft,
  projects,
  areas,
  tags = [],
  lang = 'en',
  t,
  onClose,
  onToast,
  onSave,
  onSmartParseWithGemini
}) => {
  const [title, setTitle] = useState(draft.initialTitle);
  const [notes, setNotes] = useState(draft.initialNotes);
  const [scheduleState, setScheduleState] = useState<ScheduleState>('INBOX');
  const [projectId, setProjectId] = useState<string | null>(null);
  const [areaId, setAreaId] = useState<string | null>(null);
  const [isListening, setIsListening] = useState(false);
  const [isSmartParsing, setIsSmartParsing] = useState(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const titleRef = useRef(title);
  titleRef.current = title;

  const toggleVoiceDictation = () => {
    if (isListening && recognitionRef.current) {
      recognitionRef.current.stop();
      setIsListening(false);
      return;
    }
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor) return;
    try {
      const rec = new Ctor();
      rec.continuous = false;
      rec.interimResults = false;
      rec.lang = SPEECH_BCP47_BY_LANG[lang] || 'en-US';
      rec.onresult = (ev) => {
        const transcript = Array.from(ev.results)
          .map((r) => r[0]?.transcript || '')
          .join(' ')
          .trim();
        if (!transcript) return;
        // Compute outside state updaters so React 19 StrictMode never double-appends to notes
        if (!titleRef.current.trim()) {
          titleRef.current = transcript;
          setTitle(transcript);
        } else {
          setNotes((n) => (n.trim() ? `${n}\n${transcript}` : transcript));
        }
      };
      rec.onerror = () => setIsListening(false);
      rec.onend = () => setIsListening(false);
      recognitionRef.current = rec;
      setIsListening(true);
      rec.start();
    } catch {
      setIsListening(false);
    }
  };

  useEffect(() => {
    if (draft.autoLaunchVoice) {
      const id = window.setTimeout(() => toggleVoiceDictation(), 120);
      return () => {
        window.clearTimeout(id);
        recognitionRef.current?.stop();
      };
    }
    return () => {
      recognitionRef.current?.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
      icon: <Sun size={14} style={{ color: 'var(--today-star)' }} />
    },
    {
      state: 'THIS_EVENING',
      label: t.viewThisEvening,
      icon: <Moon size={14} style={{ color: 'var(--evening-moon)' }} />
    },
    {
      state: 'ANYTIME',
      label: t.viewAnytime,
      icon: <Compass size={14} style={{ color: 'var(--anytime-teal)' }} />
    },
    {
      state: 'SOMEDAY',
      label: t.viewSomeday,
      icon: <Cloud size={14} style={{ color: 'var(--someday-amber)' }} />
    }
  ];

  const submittedRef = useRef(false);

  const parsedSmart = useMemo(
    () =>
      parseSmartTaskSyntax(title, {
        tags,
        projects,
        areas,
        lang
      }),
    [title, tags, projects, areas, lang]
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (submittedRef.current) return;
    submittedRef.current = true;
    const rawTrimmed = title.trim();
    const resolvedTitle = parsedSmart.hasAnyMatch
      ? parsedSmart.cleanTitle || rawTrimmed || t.entityNewTodo
      : rawTrimmed || t.entityNewTodo;
    const resolvedProjectId = parsedSmart.matchedProject
      ? parsedSmart.matchedProject.id
      : projectId;
    const resolvedAreaId = parsedSmart.matchedProject
      ? parsedSmart.matchedProject.areaId
      : parsedSmart.matchedArea
        ? parsedSmart.matchedArea.id
        : areaId;
    const resolvedSchedule = parsedSmart.scheduleState ?? scheduleState;

    onSave({
      title: resolvedTitle,
      notes: notes.trim(),
      scheduleState: resolvedSchedule,
      whenDate: parsedSmart.scheduleState != null ? parsedSmart.whenDate : undefined,
      reminderTime: parsedSmart.reminderTime ?? null,
      deadline: parsedSmart.deadline ?? null,
      durationMinutes: parsedSmart.durationMinutes ?? null,
      tagIds: parsedSmart.matchedTagIds,
      isMilestone: parsedSmart.isMilestone ? true : undefined,
      projectId: resolvedProjectId,
      areaId: resolvedAreaId
    });
  };

  const combinedText = `${title.trim()}${notes.trim() ? `\n${notes.trim()}` : ''}`.trim();

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
              <span>
                {draft.initialTitle || draft.initialNotes
                  ? t.quickShareTitle
                  : t.topbarTooltipQuickEntry}
              </span>
            </h3>
            <div style={{ fontSize: 12.2, color: 'var(--text-secondary)', marginTop: 2 }}>
              {isListening ? `🎙️ ${t.voiceListeningHint}` : t.quickShareSubtitle}
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              type="button"
              className={`icon-btn ${isListening ? 'active' : ''}`}
              title={t.voiceDictationLabel}
              onClick={toggleVoiceDictation}
              style={
                isListening
                  ? { color: 'var(--deadline-red)', background: 'rgba(229, 57, 53, 0.12)' }
                  : undefined
              }
            >
              {isListening ? <MicOff size={17} /> : <Mic size={17} />}
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.actionCancel}
              onClick={onClose}
              aria-label={t.actionCancel}
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="text"
                className="text-field"
                style={{ flex: 1 }}
                placeholder={isListening ? t.voiceListeningHint : t.placeholderTodoTitle}
                value={title}
                autoFocus
                onChange={(e) => {
                  titleRef.current = e.target.value;
                  setTitle(e.target.value);
                  if (parseError) setParseError(null);
                }}
              />
              {onSmartParseWithGemini && combinedText.length >= 6 && (
                <button
                  type="button"
                  className="chip-btn gemini"
                  disabled={isSmartParsing}
                  title={t.voiceSmartParseBtn}
                  onClick={async () => {
                    if (isSmartParsing) return;
                    setIsSmartParsing(true);
                    setParseError(null);
                    try {
                      await onSmartParseWithGemini(combinedText, {
                        projectId,
                        areaId,
                        scheduleState
                      });
                    } catch (err) {
                      const msg = err instanceof Error ? err.message : t.geminiErrorFallback;
                      setParseError(msg);
                      onToast?.(`⚠️ ${msg}`);
                    } finally {
                      setIsSmartParsing(false);
                    }
                  }}
                  style={{ flexShrink: 0 }}
                >
                  <Sparkles size={13} />
                  <span>{isSmartParsing ? t.geminiGenerating : t.voiceSmartParseBtn}</span>
                </button>
              )}
            </div>

            {parsedSmart.hasAnyMatch && (
              <div
                className="v32-smart-syntax-strip"
                data-testid="quick-share-smart-syntax-preview"
              >
                <span className="v32-smart-syntax-label">{t.v32SmartSyntaxDetected}</span>
                <div className="v32-smart-syntax-pills">
                  {parsedSmart.scheduleState != null && (
                    <span className="meta-badge today">
                      <CalendarDays size={10.5} />
                      <span>{parsedSmart.whenLabel || parsedSmart.scheduleState}</span>
                    </span>
                  )}
                  {parsedSmart.deadline != null && (
                    <span className="meta-badge urgent">
                      <Flag size={10.5} />
                      <span>{parsedSmart.deadlineLabel}</span>
                    </span>
                  )}
                  {parsedSmart.durationMinutes != null && (
                    <span className="meta-badge duration-badge">
                      <Hourglass size={10} />
                      <span>{formatStandaloneDuration(parsedSmart.durationMinutes)}</span>
                    </span>
                  )}
                  {parsedSmart.isMilestone && (
                    <span className="meta-badge milestone-badge">
                      <Diamond size={10} />
                      <span>{t.milestoneLabel}</span>
                    </span>
                  )}
                  {parsedSmart.matchedProject && (
                    <span className="meta-badge">
                      <Folder size={10.5} />
                      <span>{parsedSmart.matchedProject.title}</span>
                    </span>
                  )}
                  {!parsedSmart.matchedProject && parsedSmart.matchedArea && (
                    <span className="meta-badge">
                      <Folder size={10.5} />
                      <span>{parsedSmart.matchedArea.title}</span>
                    </span>
                  )}
                  {parsedSmart.matchedTags.map((tg) => (
                    <span key={tg.id} className="meta-badge">
                      <LayoutGrid size={9.5} />
                      <span>#{tg.title}</span>
                    </span>
                  ))}
                </div>
              </div>
            )}

            {parseError && (
              <div style={{ fontSize: 12.2, color: 'var(--deadline-red)' }}>{parseError}</div>
            )}

            <div className="quick-share-notes-box">
              <MarkdownNotesField
                value={notes}
                t={t}
                onChange={(next) => {
                  setNotes(next);
                  if (parseError) setParseError(null);
                }}
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
                      <Folder size={12.5} style={{ color: 'var(--things-blue)' }} />
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
  onOpenZen?: () => void;
}> = ({
  timer,
  t,
  onTogglePause,
  onAddFiveMinutes,
  onCompleteTask,
  onStop,
  onSelectTask,
  onOpenZen
}) => {
  const mins = Math.floor(timer.remainingSeconds / 60);
  const secs = timer.remainingSeconds % 60;
  const formattedTime = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  const isFinished = timer.remainingSeconds <= 0;
  const isBreak = Boolean(timer.isBreakMode);
  const progressPct =
    timer.totalSeconds > 0
      ? Math.min(
          100,
          Math.max(0, ((timer.totalSeconds - timer.remainingSeconds) / timer.totalSeconds) * 100)
        )
      : 100;

  return (
    <div
      className={`focus-timer-banner ${isFinished ? 'finished' : ''} ${isBreak ? 'break-mode' : ''}`}
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
            {isBreak ? <Coffee size={15} /> : <Timer size={15} />}
            <span className="focus-timer-clock">{formattedTime}</span>
          </span>
          <span className="focus-timer-info">
            <span className="focus-timer-label">
              {isFinished
                ? t.focusTimerFinishedTitle
                : isBreak
                  ? t.v32ZenBreakActive
                  : t.focusTimerActiveLabel}
            </span>
            <span className="focus-timer-task-title">{timer.taskTitle || t.entityNewTodo}</span>
          </span>
        </button>

        <div className="focus-timer-actions">
          {onOpenZen && (
            <button
              type="button"
              className="chip-btn"
              data-testid="focus-banner-zen-btn"
              onClick={onOpenZen}
              title={t.v32ZenExpandTooltip}
              aria-label={t.v32ZenExpandTooltip}
            >
              <Maximize2 size={12.5} />
              <span>{t.v32ZenFocusTitle}</span>
            </button>
          )}
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

// ==================== 2b. Full-Window Soft Bento Zen Focus Modal (v3.2) ====================

export const ZenFocusModal: React.FC<{
  timer: FocusTimerUiState;
  task: TaskRecord | null;
  checklistItems: ChecklistItemRecord[];
  t: TranslationStrings;
  onTogglePause: () => void;
  onAddFiveMinutes: () => void;
  onStartBreak: () => void;
  onBackToFocus: () => void;
  onCompleteTask: () => void;
  onMinimize: () => void;
  onStop: () => void;
}> = ({
  timer,
  task,
  checklistItems,
  t,
  onTogglePause,
  onAddFiveMinutes,
  onStartBreak,
  onBackToFocus,
  onCompleteTask,
  onMinimize,
  onStop
}) => {
  const [newChecklistTitle, setNewChecklistTitle] = useState('');

  const mins = Math.floor(timer.remainingSeconds / 60);
  const secs = timer.remainingSeconds % 60;
  const formattedTime = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  const isFinished = timer.remainingSeconds <= 0;
  const isBreak = Boolean(timer.isBreakMode);

  const progressFraction =
    timer.totalSeconds > 0
      ? Math.min(
          1,
          Math.max(0, (timer.totalSeconds - timer.remainingSeconds) / timer.totalSeconds)
        )
      : 1;
  const progressPct = Math.round(progressFraction * 100);

  const radius = 84;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference * (1 - progressFraction);

  const activeChecklists = useMemo(
    () =>
      checklistItems
        .filter((c) => !c.isDeleted && c.taskId === timer.taskId)
        .sort((a, b) => a.sortOrder - b.sortOrder),
    [checklistItems, timer.taskId]
  );
  const doneChecklistCount = activeChecklists.filter((c) => c.isCompleted).length;

  const savedActual =
    task?.actualMinutes != null && Number.isFinite(task.actualMinutes) && task.actualMinutes > 0
      ? Math.trunc(task.actualMinutes)
      : 0;
  const liveSessionMins = !isBreak ? Math.floor((timer.sessionFocusedSeconds ?? 0) / 60) : 0;
  const totalFocusedMins = savedActual + liveSessionMins;
  const estimatedMins =
    task?.durationMinutes != null &&
    Number.isFinite(task.durationMinutes) &&
    task.durationMinutes > 0
      ? Math.trunc(task.durationMinutes)
      : null;

  const focusedBadgeText =
    estimatedMins != null
      ? t.v32ActualVsEstimated(
          formatStandaloneDuration(totalFocusedMins),
          formatStandaloneDuration(estimatedMins)
        )
      : totalFocusedMins > 0
        ? t.v32ActualFocusedShort(formatStandaloneDuration(totalFocusedMins))
        : null;

  const commitAddChecklist = async () => {
    const clean = newChecklistTitle.trim();
    if (!clean) return;
    setNewChecklistTitle('');
    await localDb.addChecklistItems(timer.taskId, [clean]);
  };

  const notesText = task?.notes?.trim() ?? '';

  return (
    <div
      className="modal-backdrop v32-zen-focus-backdrop"
      data-testid="zen-focus-modal"
      onClick={onMinimize}
    >
      <div
        className={`v32-zen-focus-card ${isBreak ? 'break-mode' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top Bar */}
        <div className="v32-zen-header">
          <span className={`v32-zen-mode-pill ${isBreak ? 'break' : ''}`}>
            {isBreak ? <Coffee size={14} /> : <Timer size={14} />}
            <span>{isBreak ? t.v32ZenBreakActive : t.v32ZenFocusTitle}</span>
          </span>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button
              type="button"
              className="chip-btn"
              data-testid="zen-minimize-btn"
              title={t.v32ZenMinimize}
              onClick={onMinimize}
            >
              <Minimize2 size={13} />
              <span>{t.v32ZenMinimize}</span>
            </button>
            <button
              type="button"
              className="icon-btn"
              title={t.focusTimerStop}
              aria-label={t.focusTimerStop}
              onClick={onStop}
            >
              <X size={17} />
            </button>
          </div>
        </div>

        {/* Center Ring + Clock + Task Title */}
        <div className="v32-zen-hero">
          <div className="v32-zen-ring-wrap">
            <svg className="v32-zen-ring-svg" viewBox="0 0 192 192" aria-hidden="true">
              <circle
                className="v32-zen-ring-track"
                cx="96"
                cy="96"
                r={radius}
                fill="none"
                strokeWidth="8"
              />
              <circle
                className={`v32-zen-ring-progress ${isBreak ? 'break' : ''}`}
                cx="96"
                cy="96"
                r={radius}
                fill="none"
                strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                transform="rotate(-90 96 96)"
              />
            </svg>
            <div className="v32-zen-clock-center">
              <div className="v32-zen-clock" data-testid="zen-focus-clock">
                {formattedTime}
              </div>
              <div className="v32-zen-clock-sub">
                {isFinished
                  ? t.focusTimerFinishedTitle
                  : timer.isRunning
                    ? isBreak
                      ? t.v32ZenShortBreak
                      : t.focusTimerActiveLabel
                    : t.focusTimerPause}
              </div>
            </div>
          </div>

          <div className="v32-zen-progress-bar" aria-hidden="true">
            <div
              className={`v32-zen-progress-fill ${isBreak ? 'break' : ''}`}
              style={{ width: `${progressPct}%` }}
            />
          </div>

          <h2 className="v32-zen-task-title" data-testid="zen-focus-task-title">
            {task?.title || timer.taskTitle || t.entityNewTodo}
          </h2>

          {focusedBadgeText && (
            <span
              className="today-capacity-pill"
              data-testid="zen-focus-actual-badge"
            >
              <Timer size={12.5} />
              <span>{focusedBadgeText}</span>
            </span>
          )}
        </div>

        {/* Notes + Live Interactive Checklist Scroll Area */}
        <div className="v32-zen-scroll-content">
          {notesText && (
            <div className="v32-zen-bento-box" data-testid="zen-focus-notes">
              <div className="v32-zen-notes-text">
                <FormattedMarkdownText text={notesText} />
              </div>
              <DetectedNotesLinksRow links={extractDetectedLinksFromNotes(notesText)} />
            </div>
          )}

          <div className="v32-zen-bento-box" data-testid="zen-focus-checklist">
            <div className="v32-zen-section-head">
              <CheckSquare size={13.5} style={{ color: 'var(--things-blue)' }} />
              <span>
                {t.actionChecklist} ({doneChecklistCount}/{activeChecklists.length})
              </span>
            </div>

            {activeChecklists.length > 0 && (
              <div className="v32-zen-checklist-items">
                {activeChecklists.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`v32-zen-checklist-row ${item.isCompleted ? 'done' : ''}`}
                    data-testid={`zen-checklist-item-${item.id}`}
                    onClick={() =>
                      void localDb.updateChecklistItem(item.id, {
                        isCompleted: !item.isCompleted
                      })
                    }
                  >
                    <span className={`checklist-circle ${item.isCompleted ? 'done' : ''}`}>
                      {item.isCompleted && <Check size={10} strokeWidth={3} />}
                    </span>
                    <span className="v32-zen-checklist-label">{item.title}</span>
                  </button>
                ))}
              </div>
            )}

            <div className="checklist-add-row checklist-add-boxed" style={{ marginTop: 8 }}>
              <Circle size={13} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
              <input
                type="text"
                className="checklist-input"
                data-testid="zen-checklist-add-input"
                value={newChecklistTitle}
                placeholder={t.placeholderAddChecklist}
                onChange={(e) => setNewChecklistTitle(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && newChecklistTitle.trim()) {
                    e.preventDefault();
                    void commitAddChecklist();
                  }
                }}
              />
              {newChecklistTitle.trim() && (
                <button
                  type="button"
                  className="chip-btn active"
                  data-testid="zen-checklist-add-btn"
                  title={t.actionChecklist}
                  onClick={() => void commitAddChecklist()}
                >
                  <Plus size={12} />
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Footer Controls */}
        <div className="v32-zen-controls">
          {!isFinished && (
            <button
              type="button"
              className="btn-secondary"
              data-testid="zen-pause-resume-btn"
              onClick={onTogglePause}
            >
              {timer.isRunning ? <Pause size={14} /> : <Play size={14} />}
              <span>{timer.isRunning ? t.focusTimerPause : t.focusTimerResume}</span>
            </button>
          )}

          <button
            type="button"
            className="btn-secondary"
            data-testid="zen-plus-5m-btn"
            onClick={onAddFiveMinutes}
          >
            <span>{t.focusTimerPlus5m}</span>
          </button>

          {isBreak ? (
            <button
              type="button"
              className="btn-secondary"
              data-testid="zen-back-to-focus-btn"
              onClick={onBackToFocus}
            >
              <Timer size={14} />
              <span>{t.v32ZenBackToFocus}</span>
            </button>
          ) : (
            <button
              type="button"
              className="btn-secondary"
              data-testid="zen-short-break-btn"
              onClick={onStartBreak}
            >
              <Coffee size={14} />
              <span>{t.v32ZenShortBreak}</span>
            </button>
          )}

          <button
            type="button"
            className="btn-primary"
            data-testid="zen-complete-btn"
            onClick={onCompleteTask}
          >
            <Check size={14} />
            <span>{t.actionComplete}</span>
          </button>
        </div>
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
          title={t.actionClose}
          aria-label={t.actionClose}
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
          <div
            key={`task-${task.id}`}
            className="trash-item-card"
            draggable
            onDragStart={(e) => {
              e.dataTransfer.setData('application/x-thingsbook-task', task.id);
              e.dataTransfer.setData('application/x-thingsbook-tasks', JSON.stringify([task.id]));
              e.dataTransfer.effectAllowed = 'move';
            }}
          >
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
          <button type="button" className="icon-btn" title={t.actionCancel} onClick={onCancel}>
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
          <button type="button" className="icon-btn" title={t.actionCancel} onClick={onCancel}>
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
          title={t.actionClose}
          aria-label={t.actionClose}
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
