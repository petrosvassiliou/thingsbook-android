import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDown,
  ArrowLeftRight,
  ArrowUp,
  ArrowUpDown,
  CalendarDays,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  Cloud,
  Compass,
  Copy,
  Edit3,
  Flag,
  Folder,
  FolderInput,
  Heading as HeadingIcon,
  History,
  Inbox,
  LayoutGrid,
  List,
  Maximize2,
  Menu,
  Mic,
  Milestone,
  Minimize2,
  Moon,
  MoreHorizontal,
  PanelLeftClose,
  PanelRight,
  Pin,
  PinOff,
  Plus,
  Search,
  Settings,
  SlidersHorizontal,
  Sparkles,
  Sun,
  Trash2,
  X
} from 'lucide-react';
import {
  APP_VERSION,
  addDaysLocal,
  computeMidpointSortOrder,
  computeUpcomingTargetDate,
  daysBetweenToday,
  endOfDayEpoch,
  resolveEffectiveShowPerspectives,
  startOfDayEpoch,
  type ActiveNavigation,
  type AppPreferences,
  type CalendarEventItem,
  type DeviceCalendarInfo,
  type EffortLevel,
  type FocusTimerUiState,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectViewMode,
  type QuickShareDraft,
  type SavedPerspective,
  type SmartViewType,
  type SupportedLang,
  type TagRecord,
  type TagSortMode,
  type TaskRecord,
  type ThemeMode,
  type ViewSortMode
} from './domain/types';
import type { ParsedSmartSyntax } from './domain/smartSyntaxParser';
import {
  buildAreaPortfolioRoadmapLayout,
  buildProjectRoadmapLayout,
  extractDraggedTaskIds,
  hasDraggedTaskMime,
  normalizeAreaViewMode,
  normalizeProjectViewMode,
  orderTaskIdsByVisualOrder
} from './domain/v30Roadmap';
import {
  buildDayWorkloadSummary,
  computeMultiDayWorkloadForecast,
  computeTagUsageCounts,
  computeTodayCapacitySummary,
  detectStalledProjects,
  evaluateProjectStalledStatus,
  filterTasksForPerspective,
  formatStandaloneDuration,
  normalizeWorkdayBounds,
  resolveTopBarLayoutTier,
  sortTasksForView
} from './domain/v2Utils';
import {
  clearLastCalendarError,
  connectGoogleCalendarPopup,
  filterActiveOrUpcomingCalendarEvents,
  getLastCalendarError,
  hasCalendarAccess,
  importIcsCalendarText,
  localEpochDayFromMs,
  queryAvailableCalendars,
  queryEventsForDateRange,
  removeImportedIcsCalendar
} from './data/calendarService';
import { cloudSync, type CloudSyncSnapshot } from './data/cloudSync';
import {
  generateLogbookReflection,
  parseBrainDumpToTasks,
  type LogbookReviewResponse
} from './data/geminiService';
import {
  computeStatistics,
  loadPreferences,
  localDb,
  savePreferences,
  type DataSnapshot
} from './data/localDb';
import {
  applyPendingSwUpdate,
  checkForSwAndReleaseUpdates,
  type UpdateCheckResult
} from './data/pwaUpdater';
import { isPrereleaseVersion } from './data/versionUtils';
import { checkDueRemindersAndDigest } from './data/reminderRuntime';
import { TRANSLATIONS, resolveLanguage } from './i18n/translations';
import { AttachmentsStrip } from './components/AttachmentStrip';
import { CalendarEventsBlock } from './components/CalendarEventsBlock';
import { InProgressIcon } from './components/InProgressIcon';
import {
  AreaManagerModal,
  CreateOrEditProjectModal,
  MoveTaskModal,
  NotificationSettingsModal,
  PerspectiveModal,
  QuickCreateAreaModal,
  TagManagerModal,
  TaskDependencyModal,
  TaskTagPickerModal
} from './components/EntityModals';
import { GeminiModal, type GeminiModalTab } from './components/GeminiModal';
import { InlineQuickFind } from './components/InlineQuickFind';
import { MagicPlusFab } from './components/MagicPlusFab';
import type { MagicPlusResolvedDrop } from './domain/magicPlusDrop';
import { extractDetectedLinksFromNotes } from './domain/powerUtils';
import {
  DetectedNotesLinksRow,
  FormattedMarkdownText
} from './components/MarkdownNotesField';
import {
  ActionableReminderBanner,
  AppLockOverlay,
  AppLockPinSetupModal,
  EmptyTrashConfirmDialog,
  FocusTimerBanner,
  QuickShareModal,
  TrashViewList,
  UndoSnackbarBanner,
  ZenFocusModal
} from './components/PowerFeaturesModals';
import {
  AreaPortfolioRoadmapWorkspace,
  ProjectBoardWorkspace,
  ProjectRoadmapWorkspace
} from './components/ProjectBoardAndRoadmap';
import { ProjectProgressRing } from './components/ProjectProgressRing';
import { AuthSyncModal, SettingsModal } from './components/SettingsAndAuthModals';
import {
  Sidebar,
  SmartViewNavigationStrip,
  type SidebarDropTarget
} from './components/Sidebar';
import { TaskItem } from './components/TaskItem';
import { ThingsTooltipHost } from './components/ThingsTooltipHost';
import {
  TemplatesAndImportModal,
  type TemplatesModalTab
} from './components/TemplatesAndImportModal';
import { TodayTimelineView } from './components/TodayTimelineView';
import {
  WeeklyReviewModal,
  type WeeklyReviewStep
} from './components/WeeklyReviewModal';
import { DeadlineModal, RepeatModal, WhenModal } from './components/WhenAndRepeatModals';
import { WorkspaceInspectorPanel } from './components/WorkspaceInspectorPanel';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function parseSharedDraft(
  rawTitle: string | null,
  rawText: string | null,
  rawUrl: string | null
): QuickShareDraft | null {
  const cleanTitle = (rawTitle ?? '').trim();
  const cleanText = (rawText ?? '').trim();
  const cleanUrl = (rawUrl ?? '').trim();
  if (!cleanTitle && !cleanText && !cleanUrl) return null;

  let initialTitle = cleanTitle;
  const noteParts: string[] = [];

  if (!initialTitle && cleanText) {
    const lines = cleanText.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (lines.length === 1 && /^https?:\/\//i.test(lines[0])) {
      initialTitle = lines[0].replace(/^https?:\/\/(www\.)?/i, '').slice(0, 80);
      noteParts.push(lines[0]);
    } else if (lines.length > 0) {
      initialTitle = lines[0].slice(0, 120);
      if (lines.length > 1) noteParts.push(lines.slice(1).join('\n'));
    }
  } else if (cleanText && cleanText !== initialTitle) {
    noteParts.push(cleanText);
  }

  if (cleanUrl && !noteParts.some((p) => p.includes(cleanUrl))) {
    noteParts.push(cleanUrl);
    if (!initialTitle) {
      initialTitle = cleanUrl.replace(/^https?:\/\/(www\.)?/i, '').slice(0, 80);
    }
  }

  return {
    initialTitle,
    initialNotes: noteParts.join('\n\n')
  };
}

const INSPECTOR_OPEN_STORAGE_KEY = 'thingsbook_inspector_open_v1';

function loadInitialInspectorOpen(): boolean {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return true;
    const raw = window.localStorage.getItem(INSPECTOR_OPEN_STORAGE_KEY);
    return raw === 'false' ? false : true;
  } catch {
    return true;
  }
}

export const App: React.FC = () => {
  const [prefs, setPrefs] = useState<AppPreferences>(() => loadPreferences());
  const [snapshot, setSnapshot] = useState<DataSnapshot>(() => localDb.getSnapshot());
  const [syncState, setSyncState] = useState<CloudSyncSnapshot>(() => cloudSync.getState());

  const [isWideScreen, setIsWideScreen] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 700 : true
  );
  const [isInspectorViewport, setIsInspectorViewport] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 1200 : true
  );
  const [inspectorOpen, setInspectorOpen] = useState<boolean>(() =>
    loadInitialInspectorOpen()
  );
  const toggleInspectorOpen = useCallback(() => {
    setInspectorOpen((prev) => {
      const next = !prev;
      try {
        window.localStorage?.setItem(INSPECTOR_OPEN_STORAGE_KEY, String(next));
      } catch {
        // Ignore storage write errors in restricted contexts
      }
      return next;
    });
  }, []);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  const [activeNav, setActiveNav] = useState<ActiveNavigation>({
    kind: 'smart',
    view: 'TODAY'
  });
  const [selectedFilterTagId, setSelectedFilterTagId] = useState<string | null>(null);
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);
  const [isExplicitMultiSelect, setIsExplicitMultiSelect] = useState(false);
  const lastSelectedAnchorIdRef = useRef<string | null>(null);
  const [expandedTaskId, setExpandedTaskId] = useState<string | null>(null);

  // Collapsible Project Headings & Inline Editing
  const [collapsedHeadingIds, setCollapsedHeadingIds] = useState<Set<string>>(new Set());
  const [editingHeadingId, setEditingHeadingId] = useState<string | null>(null);
  const [editingHeadingTitle, setEditingHeadingTitle] = useState('');
  const cancelingHeadingEditRef = useRef(false);
  const [dragOverHeadingId, setDragOverHeadingId] = useState<string | null>(null);

  // Area inline title editing & Project area/menu dropdowns
  const [editingAreaHeader, setEditingAreaHeader] = useState(false);
  const [editingAreaTitle, setEditingAreaTitle] = useState('');
  const [projectAreaDropdownOpen, setProjectAreaDropdownOpen] = useState(false);
  const [projectHeaderMenuOpen, setProjectHeaderMenuOpen] = useState(false);

  // Magic Plus, Inline Quick Find & v3.0-beta.5 Full-Screen / Inline Quick Edit
  const [quickFindOpen, setQuickFindOpen] = useState(false);
  const [magicFabOpen, setMagicFabOpen] = useState(false);
  const [isTimelineFullScreen, setIsTimelineFullScreen] = useState(false);
  const [inlineQuickEditRequest, setInlineQuickEditRequest] = useState<{
    taskId: string;
    nonce: number;
  } | null>(null);

  // Modals (store entity IDs so modals always read live snapshot records and never overwrite titles)
  const [whenModalTaskId, setWhenModalTaskId] = useState<string | null>(null);
  const [deadlineModalTarget, setDeadlineModalTarget] = useState<
    | { kind: 'task'; taskId: string }
    | { kind: 'project'; projectId: string }
    | { kind: 'heading'; headingId: string }
    | { kind: 'heading-start'; headingId: string }
    | null
  >(null);
  const [repeatModalTaskId, setRepeatModalTaskId] = useState<string | null>(null);
  const [moveModalTaskIds, setMoveModalTaskIds] = useState<string[] | null>(null);
  const [tagPickerTaskId, setTagPickerTaskId] = useState<string | null>(null);
  const [areaManagerOpen, setAreaManagerOpen] = useState(false);
  const [quickCreateAreaOpen, setQuickCreateAreaOpen] = useState(false);
  const [projectModalState, setProjectModalState] = useState<{
    open: boolean;
    project?: ProjectRecord | null;
    defaultAreaId?: string | null;
  }>({ open: false });
  const [tagManagerOpen, setTagManagerOpen] = useState(false);
  const [notificationsModalOpen, setNotificationsModalOpen] = useState(false);
  const [geminiModalOpen, setGeminiModalOpen] = useState(false);
  const [geminiModalTab, setGeminiModalTab] = useState<GeminiModalTab>('braindump');
  const [settingsModalOpen, setSettingsModalOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [weeklyReviewModal, setWeeklyReviewModal] = useState<{
    open: boolean;
    step: WeeklyReviewStep;
  }>({ open: false, step: 1 });
  const [templatesModal, setTemplatesModal] = useState<{
    open: boolean;
    tab: TemplatesModalTab;
    areaId: string | null;
  }>({ open: false, tab: 'gallery', areaId: null });
  const [todayViewMode, setTodayViewMode] = useState<'list' | 'timeline'>('list');
  const [viewSortDropdownOpen, setViewSortDropdownOpen] = useState(false);
  const [batchTagPickerOpen, setBatchTagPickerOpen] = useState(false);
  const [dependencyModalTaskId, setDependencyModalTaskId] = useState<string | null>(null);
  const [perspectiveModalState, setPerspectiveModalState] = useState<{
    open: boolean;
    perspective: SavedPerspective | null;
  }>({ open: false, perspective: null });

  // Android v1.7.0 Power Features State
  const [quickShareDraft, setQuickShareDraft] = useState<QuickShareDraft | null>(null);
  const [focusTimer, setFocusTimer] = useState<FocusTimerUiState | null>(null);
  const [zenFocusOpen, setZenFocusOpen] = useState(false);
  const focusTimerRef = useRef<FocusTimerUiState | null>(null);
  focusTimerRef.current = focusTimer;
  const [undoBanner, setUndoBanner] = useState<{ id: number; message: string } | null>(null);
  const [emptyTrashConfirmOpen, setEmptyTrashConfirmOpen] = useState(false);
  const [pendingDeleteProject, setPendingDeleteProject] = useState<{
    project: ProjectRecord;
    associatedTasksCount: number;
  } | null>(null);
  const [pendingCompleteProject, setPendingCompleteProject] = useState<{
    project: ProjectRecord;
    openTasksCount: number;
  } | null>(null);
  const undoNavRestoreRef = useRef<{ kind: 'project'; projectId: string } | null>(null);
  const [appLockSetupOpen, setAppLockSetupOpen] = useState(false);
  const [isAppLocked, setIsAppLocked] = useState<boolean>(() => {
    const initial = loadPreferences();
    return Boolean(initial.biometricAppLockEnabled && initial.appLockPinHash);
  });
  const [inAppReminders, setInAppReminders] = useState<Array<{ taskId: string; title: string }>>(
    []
  );

  // Logbook AI Reflection
  const [reflection, setReflection] = useState<LogbookReviewResponse | null>(null);
  const [loadingReflection, setLoadingReflection] = useState(false);
  const [reflectionError, setReflectionError] = useState<string | null>(null);
  const workspaceScrollRef = useRef<HTMLDivElement | null>(null);
  const workspaceContainerRef = useRef<HTMLElement | null>(null);
  const [workspaceWidthPx, setWorkspaceWidthPx] = useState<number>(() => {
    if (typeof window === 'undefined') return 1024;
    const sidebarReserve = window.innerWidth >= 700 && prefs.sidebarVisible ? 278 : 0;
    return Math.max(280, window.innerWidth - sidebarReserve);
  });

  // ChromeOS Install Prompt, Auto-Update State & Toast
  const [deferredInstallPrompt, setDeferredInstallPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isStandalonePwa, setIsStandalonePwa] = useState<boolean>(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(display-mode: standalone)').matches
      : false
  );
  const [availableUpdate, setAvailableUpdate] = useState<UpdateCheckResult | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const lang: SupportedLang = useMemo(() => resolveLanguage(prefs.language), [prefs.language]);
  const t = TRANSLATIONS[lang];
  const tRef = useRef(t);
  tRef.current = t;

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
  }, []);

  useEffect(() => {
    if (!toastMsg) return;
    const timer = window.setTimeout(() => setToastMsg(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toastMsg]);

  useEffect(() => {
    if (!undoBanner) return;
    const timer = window.setTimeout(() => {
      setUndoBanner(null);
      localDb.clearUndoSnapshot();
    }, 5500);
    return () => window.clearTimeout(timer);
  }, [undoBanner]);

  const updatePrefs = useCallback((patch: Partial<AppPreferences>) => {
    setPrefs((prev) => {
      const next = { ...prev, ...patch };
      savePreferences(next);
      return next;
    });
  }, []);

  const handleActionableReminderAction = useCallback(async (action: string, taskId: string) => {
    setInAppReminders((prev) => prev.filter((r) => r.taskId !== taskId));
    if (action === 'complete') {
      await localDb.upsertTask({ id: taskId, status: 'COMPLETED' });
    } else if (action === 'snooze-1h') {
      await localDb.upsertTask({ id: taskId, reminderTime: Date.now() + 3_600_000 });
    } else if (action === 'tomorrow') {
      const tomorrowStart = addDaysLocal(startOfDayEpoch(), 1);
      const tomorrow9am = new Date(tomorrowStart);
      tomorrow9am.setHours(9, 0, 0, 0);
      await localDb.upsertTask({
        id: taskId,
        scheduleState: 'UPCOMING',
        whenDate: tomorrowStart,
        reminderTime: tomorrow9am.getTime()
      });
    } else {
      setSelectedTaskIds([]);
      setExpandedTaskId(taskId);
    }
  }, []);

  // Theme application
  useEffect(() => {
    const applyTheme = () => {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      const effectiveDark =
        prefs.themeMode === 'dark' || (prefs.themeMode === 'system' && prefersDark);
      document.documentElement.setAttribute('data-theme', effectiveDark ? 'dark' : 'light');
    };
    applyTheme();
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    mq.addEventListener('change', applyTheme);
    return () => mq.removeEventListener('change', applyTheme);
  }, [prefs.themeMode]);

  // 700px Responsive Breakpoint, 1200px Inspector Breakpoint & Live Workspace Width Listener
  useEffect(() => {
    const syncWidths = () => {
      setIsWideScreen(window.innerWidth >= 700);
      setIsInspectorViewport(window.innerWidth >= 1200);
      const el = workspaceContainerRef.current;
      if (el && el.clientWidth > 0) {
        setWorkspaceWidthPx(el.clientWidth);
      }
    };
    syncWidths();
    window.addEventListener('resize', syncWidths);
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined' && workspaceContainerRef.current) {
      observer = new ResizeObserver((entries) => {
        const w = entries[0]?.contentRect.width;
        if (typeof w === 'number' && w > 0) {
          setWorkspaceWidthPx(Math.round(w));
        }
      });
      observer.observe(workspaceContainerRef.current);
    }
    return () => {
      window.removeEventListener('resize', syncWidths);
      observer?.disconnect();
    };
  }, [prefs.sidebarVisible]);

  // Re-lock app after 60s in background when Biometric / PIN App Lock is enabled
  useEffect(() => {
    if (!prefs.biometricAppLockEnabled || !prefs.appLockPinHash) return;
    let hiddenSince: number | null = null;
    const onVis = () => {
      if (document.visibilityState === 'hidden') {
        hiddenSince = Date.now();
      } else if (
        document.visibilityState === 'visible' &&
        hiddenSince != null &&
        Date.now() - hiddenSince >= 60_000
      ) {
        hiddenSince = null;
        setIsAppLocked(true);
      }
    };
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
  }, [prefs.biometricAppLockEnabled, prefs.appLockPinHash]);

  const flushFocusMinutes = useCallback(
    (timer: FocusTimerUiState | null, roundUpPartial: boolean) => {
      if (!timer || timer.isBreakMode) return;
      const secs = timer.sessionFocusedSeconds ?? 0;
      if (secs <= 0) return;
      const earned =
        roundUpPartial && secs >= 30
          ? Math.max(1, Math.round(secs / 60))
          : Math.round(secs / 60);
      if (earned > 0) {
        void localDb.addTaskActualMinutes(timer.taskId, earned);
      }
    },
    []
  );

  // 25-Minute Focus / Pomodoro Timer Countdown + v3.2 actualMinutes Session Accumulation
  const isFocusTimerCountingDown = Boolean(
    focusTimer && focusTimer.isRunning && focusTimer.remainingSeconds > 0
  );
  useEffect(() => {
    if (!isFocusTimerCountingDown) return;
    const id = window.setInterval(() => {
      const cur = focusTimerRef.current;
      if (!cur || !cur.isRunning || cur.remainingSeconds <= 0) return;
      const nextFocused = cur.isBreakMode
        ? (cur.sessionFocusedSeconds ?? 0)
        : (cur.sessionFocusedSeconds ?? 0) + 1;
      if (cur.remainingSeconds <= 1) {
        if (!cur.isBreakMode) {
          flushFocusMinutes({ ...cur, sessionFocusedSeconds: nextFocused }, true);
        }
        showToast(
          cur.isBreakMode
            ? `☕ ${tRef.current.v32ZenBackToFocus}`
            : `🎉 ${tRef.current.focusTimerFinishedTitle}`
        );
        const finishedState: FocusTimerUiState = {
          ...cur,
          remainingSeconds: 0,
          isRunning: false,
          sessionFocusedSeconds: 0
        };
        focusTimerRef.current = finishedState;
        setFocusTimer(finishedState);
        return;
      }
      const nextState: FocusTimerUiState = {
        ...cur,
        remainingSeconds: cur.remainingSeconds - 1,
        sessionFocusedSeconds: nextFocused
      };
      focusTimerRef.current = nextState;
      setFocusTimer(nextState);
    }, 1000);
    return () => window.clearInterval(id);
  }, [
    isFocusTimerCountingDown,
    focusTimer?.taskId,
    focusTimer?.isBreakMode,
    flushFocusMinutes,
    showToast
  ]);

  // Keep Focus Timer title synced or flush & close if task was deleted/completed
  useEffect(() => {
    if (!focusTimer) {
      if (zenFocusOpen) setZenFocusOpen(false);
      return;
    }
    const liveTask = snapshot.tasks.find((tk) => tk.id === focusTimer.taskId);
    if (!liveTask || liveTask.isDeleted || liveTask.status !== 'OPEN') {
      if (liveTask && !liveTask.isDeleted && liveTask.status !== 'OPEN') {
        flushFocusMinutes(focusTimerRef.current, true);
      }
      focusTimerRef.current = null;
      setFocusTimer(null);
      setZenFocusOpen(false);
    } else if (liveTask.title !== focusTimer.taskTitle) {
      setFocusTimer((prev) => (prev ? { ...prev, taskTitle: liveTask.title } : null));
    }
  }, [snapshot.tasks, focusTimer, zenFocusOpen, flushFocusMinutes]);

  // Initialize DB, Cloud Sync, Undo, Launcher Shortcuts, Share Target, Reminder Actions, Auto Update Check, and PWA Install Listener
  useEffect(() => {
    const unsubDb = localDb.subscribe(setSnapshot);
    const unsubSync = cloudSync.subscribe(setSyncState);
    const unsubUndo = localDb.subscribeUndo((ev) => {
      if (ev.kind === 'tasks_updated') return;
      if (ev.kind !== 'project_completed') {
        undoNavRestoreRef.current = null;
      }
      const tr = tRef.current;
      const fallbackTitle = ev.entityTitle.trim() || tr.entityNewTodo;
      const msg =
        ev.kind === 'task_completed'
          ? tr.undoTaskCompleted(fallbackTitle)
          : ev.kind === 'project_completed'
            ? tr.v31ProjectCompletedUndo(ev.entityTitle.trim() || tr.entityNewProject)
            : ev.kind === 'project_deleted'
              ? tr.undoProjectDeleted(ev.entityTitle.trim() || tr.entityNewProject)
              : ev.kind === 'roadmap_cascade'
                ? tr.v31RoadmapCascadeUndo(ev.count)
                : ev.kind === 'roadmap_rescheduled'
                  ? tr.v31RoadmapRescheduledUndo
                  : ev.kind === 'day_plan_applied'
                    ? tr.v31GeminiPlanDayAppliedUndo(ev.count)
                    : tr.undoTaskDeleted(fallbackTitle);
      setUndoBanner({ id: Date.now(), message: msg });
    });

    const onSwMessage = (event: MessageEvent) => {
      const data = event.data as { type?: string; action?: string; taskId?: string } | null;
      if (data?.type === 'THINGSBOOK_REMINDER_ACTION' && data.taskId) {
        void handleActionableReminderAction(data.action || 'open', data.taskId);
      }
    };
    navigator.serviceWorker?.addEventListener('message', onSwMessage);

    void localDb.initialize(prefs.language).then(async (initialSnap) => {
      void cloudSync.initializeSession();

      // Handle ChromeOS Launcher Shortcuts, Web Share Target & Actionable Reminder Deep Links
      const params = new URLSearchParams(window.location.search);
      const viewParam = params.get('view')?.toUpperCase();
      const actionParam = params.get('action')?.toLowerCase();
      const reminderActionParam = params.get('reminder_action')?.toLowerCase();
      const reminderTaskIdParam = params.get('task_id');
      const shareTitle = params.get('share_title');
      const shareText = params.get('share_text');
      const shareUrl = params.get('share_url');

      const validViews: SmartViewType[] = [
        'INBOX',
        'TODAY',
        'UPCOMING',
        'ANYTIME',
        'SOMEDAY',
        'LOGBOOK',
        'TRASH'
      ];

      if (viewParam && (validViews as string[]).includes(viewParam)) {
        setActiveNav({ kind: 'smart', view: viewParam as SmartViewType });
      }

      if (reminderActionParam && reminderTaskIdParam) {
        await handleActionableReminderAction(reminderActionParam, reminderTaskIdParam);
      } else if (actionParam === 'quick-share' || shareTitle || shareText || shareUrl) {
        const draft = parseSharedDraft(shareTitle, shareText, shareUrl) ?? {
          initialTitle: '',
          initialNotes: ''
        };
        setQuickShareDraft(draft);
      } else if (actionParam === 'voice-capture') {
        setQuickShareDraft({
          initialTitle: '',
          initialNotes: '',
          autoLaunchVoice: true
        });
      } else if (actionParam === 'quick-find') {
        setQuickFindOpen(true);
      } else if (actionParam === 'weekly-review') {
        setWeeklyReviewModal({ open: true, step: 1 });
      } else if (actionParam === 'new-todo') {
        const inboxOpen = initialSnap.tasks.filter(
          (tk) =>
            !tk.isDeleted &&
            tk.status === 'OPEN' &&
            tk.scheduleState === 'INBOX' &&
            tk.projectId == null &&
            tk.areaId == null
        );
        const minOrder =
          inboxOpen.length > 0
            ? inboxOpen.reduce((m, tk) => Math.min(m, tk.sortOrder), Infinity)
            : null;
        const created = await localDb.upsertTask({
          title: '',
          scheduleState: 'INBOX',
          sortOrder: computeMidpointSortOrder(
            null,
            minOrder != null && Number.isFinite(minOrder) ? minOrder : null
          )
        });
        setActiveNav({ kind: 'smart', view: 'INBOX' });
        setSelectedTaskIds([]);
        setExpandedTaskId(created.id);
      }

      if (
        viewParam ||
        actionParam ||
        reminderActionParam ||
        shareTitle ||
        shareText ||
        shareUrl
      ) {
        window.history.replaceState({}, '', window.location.pathname);
      }
    });

    if (prefs.autoCheckUpdatesEnabled) {
      const includeBeta =
        Boolean(prefs.includeBetaUpdates) || isPrereleaseVersion(APP_VERSION);
      void checkForSwAndReleaseUpdates(APP_VERSION, includeBeta).then((res) => {
        if (res.hasUpdate) {
          setAvailableUpdate(res);
        }
      });
    }

    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredInstallPrompt(e as BeforeInstallPromptEvent);
    };
    const handleAppInstalled = () => {
      setIsStandalonePwa(true);
      setDeferredInstallPrompt(null);
    };
    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      unsubDb();
      unsubSync();
      unsubUndo();
      navigator.serviceWorker?.removeEventListener('message', onSwMessage);
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Reactive Day Boundary & Exact 00:00 Midnight Rollover Watcher (Android v1.6.19 parity)
  const [endOfToday, setEndOfToday] = useState<number>(() => endOfDayEpoch());

  useEffect(() => {
    let lastEndOfToday = endOfDayEpoch();
    let midnightTimerId: number | null = null;

    const scheduleExactMidnightCheck = () => {
      if (midnightTimerId != null) {
        window.clearTimeout(midnightTimerId);
      }
      const msUntilMidnight = Math.max(1_000, endOfDayEpoch() - Date.now() + 60);
      midnightTimerId = window.setTimeout(() => {
        checkDayBoundary();
        scheduleExactMidnightCheck();
      }, Math.min(msUntilMidnight, 2_147_483_647));
    };

    const checkDayBoundary = () => {
      const latestEndOfToday = endOfDayEpoch();
      if (latestEndOfToday !== lastEndOfToday) {
        lastEndOfToday = latestEndOfToday;
        setEndOfToday(latestEndOfToday);
        void localDb.rolloverDueTasksForNewDay();
        scheduleExactMidnightCheck();
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkDayBoundary();
      }
    };

    scheduleExactMidnightCheck();
    const intervalId = window.setInterval(checkDayBoundary, 15_000);
    window.addEventListener('focus', checkDayBoundary);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      if (midnightTimerId != null) window.clearTimeout(midnightTimerId);
      window.clearInterval(intervalId);
      window.removeEventListener('focus', checkDayBoundary);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  // Soft Keyboard & Expanded Bottom Task Visibility Engine (Android v1.6.20 parity)
  const [softKeyboardInsetPx, setSoftKeyboardInsetPx] = useState(0);
  const isSoftKeyboardOpen = softKeyboardInsetPx > 60;
  const [focusScrollRequestTick, setFocusScrollRequestTick] = useState(0);
  const preferBottomForExpandedTaskRef = useRef(false);

  const handleRequestEnsureTaskVisible = useCallback((preferBottom: boolean) => {
    preferBottomForExpandedTaskRef.current = preferBottom;
    setFocusScrollRequestTick((n) => n + 1);
  }, []);

  const ensureExpandedTaskVisible = useCallback((targetId: string, animate: boolean) => {
    const scroller = workspaceScrollRef.current;
    if (!scroller) return;
    const escapedId =
      typeof CSS !== 'undefined' && typeof CSS.escape === 'function'
        ? CSS.escape(targetId)
        : targetId.replace(/"/g, '\\"');
    const cardEl = scroller.querySelector<HTMLElement>(`[data-task-id="${escapedId}"]`);
    if (!cardEl) return;

    const topMarginPx = 12;
    const bottomMarginPx = 24;
    const scrollerRect = scroller.getBoundingClientRect();
    const vv = window.visualViewport;
    const visibleViewportBottom = vv ? vv.offsetTop + vv.height : window.innerHeight;

    const safeTop = scrollerRect.top + topMarginPx;
    const safeBottom = Math.max(
      safeTop + 1,
      Math.min(scrollerRect.bottom, visibleViewportBottom) - bottomMarginPx
    );

    const itemRect = cardEl.getBoundingClientRect();
    let scrollDelta = 0;

    if (itemRect.bottom > safeBottom) {
      const neededDown = itemRect.bottom - safeBottom;
      if (preferBottomForExpandedTaskRef.current) {
        scrollDelta = neededDown;
      } else {
        const maxWithoutHidingTitle = Math.max(0, itemRect.top - safeTop);
        scrollDelta = Math.min(neededDown, maxWithoutHidingTitle);
      }
    } else if (itemRect.top < safeTop) {
      scrollDelta = itemRect.top - safeTop;
    }

    if (Math.abs(scrollDelta) > 1.5) {
      scroller.scrollBy({
        top: scrollDelta,
        behavior: animate ? 'smooth' : 'auto'
      });
    }
  }, []);

  // Track ChromeOS / tablet virtual keyboard occlusion via window.visualViewport
  useEffect(() => {
    const vv = window.visualViewport;
    const syncVisualViewport = () => {
      const nextInset = vv
        ? Math.max(0, Math.round(window.innerHeight - (vv.height + vv.offsetTop)))
        : 0;
      setSoftKeyboardInsetPx((prev) => (prev === nextInset ? prev : nextInset));
    };
    syncVisualViewport();
    vv?.addEventListener('resize', syncVisualViewport);
    vv?.addEventListener('scroll', syncVisualViewport);
    window.addEventListener('resize', syncVisualViewport);
    return () => {
      vv?.removeEventListener('resize', syncVisualViewport);
      vv?.removeEventListener('scroll', syncVisualViewport);
      window.removeEventListener('resize', syncVisualViewport);
    };
  }, []);

  // Staggered smooth scroll when expanding a task or focusing title/notes/checklist (Android v1.6.20)
  useEffect(() => {
    if (!expandedTaskId) return;
    const t1 = window.setTimeout(() => ensureExpandedTaskVisible(expandedTaskId, true), 16);
    const t2 = window.setTimeout(() => ensureExpandedTaskVisible(expandedTaskId, true), 140);
    return () => {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
    };
  }, [expandedTaskId, focusScrollRequestTick, ensureExpandedTaskVisible]);

  // Keep expanded task visible when soft keyboard opens, viewport shrinks, or card height grows (subtasks/notes)
  useEffect(() => {
    if (!expandedTaskId) return;
    const scroller = workspaceScrollRef.current;
    if (!scroller) return;
    const escapedId =
      typeof CSS !== 'undefined' && typeof CSS.escape === 'function'
        ? CSS.escape(expandedTaskId)
        : expandedTaskId.replace(/"/g, '\\"');
    const cardEl = scroller.querySelector<HTMLElement>(`[data-task-id="${escapedId}"]`);

    let prevCardHeight = cardEl ? cardEl.getBoundingClientRect().height : 0;
    let prevScrollerHeight = scroller.clientHeight;

    const cardObserver =
      cardEl && typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver((entries) => {
            const h = entries[0]?.contentRect.height ?? 0;
            if (h > prevCardHeight && prevCardHeight > 0) {
              ensureExpandedTaskVisible(expandedTaskId, false);
            }
            if (h > 0) prevCardHeight = h;
          })
        : null;

    const scrollerObserver =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(() => {
            const curH = scroller.clientHeight;
            if (curH > 0 && curH < prevScrollerHeight) {
              ensureExpandedTaskVisible(expandedTaskId, false);
            }
            if (curH > 0) prevScrollerHeight = curH;
          })
        : null;

    if (cardEl && cardObserver) cardObserver.observe(cardEl);
    scrollerObserver?.observe(scroller);

    if (softKeyboardInsetPx > 0) {
      ensureExpandedTaskVisible(expandedTaskId, false);
    }

    return () => {
      cardObserver?.disconnect();
      scrollerObserver?.disconnect();
    };
  }, [expandedTaskId, softKeyboardInsetPx, ensureExpandedTaskVisible]);

  // Reminder & Daily Digest Ticker
  useEffect(() => {
    const tick = () => {
      checkDueRemindersAndDigest(
        snapshot.tasks,
        prefs,
        (taskId) => {
          setSelectedTaskIds([]);
          setExpandedTaskId(taskId);
        },
        (item) => {
          setInAppReminders((prev) =>
            prev.some((x) => x.taskId === item.taskId) ? prev : [...prev, item]
          );
        },
        {
          complete: t.actionComplete,
          snooze1h: t.notificationSnooze1h,
          tomorrow: t.dateTomorrow
        }
      );
    };
    tick();
    const intervalId = window.setInterval(tick, 30_000);
    return () => window.clearInterval(intervalId);
  }, [snapshot.tasks, prefs, t.actionComplete, t.notificationSnooze1h, t.dateTomorrow]);

  // Lookup Maps
  const activeAreas = useMemo(
    () => snapshot.areas.filter((a) => !a.isDeleted),
    [snapshot.areas]
  );
  const activeProjects = useMemo(
    () => snapshot.projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE'),
    [snapshot.projects]
  );
  const allNonDeletedProjectsMap = useMemo(
    () => new Map(snapshot.projects.filter((p) => !p.isDeleted).map((p) => [p.id, p])),
    [snapshot.projects]
  );
  const areasMap = useMemo(
    () => new Map(activeAreas.map((a) => [a.id, a])),
    [activeAreas]
  );
  const tagsMap = useMemo(
    () => new Map(snapshot.tags.filter((tg) => !tg.isDeleted).map((tg) => [tg.id, tg])),
    [snapshot.tags]
  );

  // ==================== Exact Android v1.7.0 Smart View Queries ====================
  const openTasks = useMemo(
    () => snapshot.tasks.filter((tk) => !tk.isDeleted && tk.status === 'OPEN'),
    [snapshot.tasks]
  );

  const deletedTasks = useMemo(
    () => snapshot.tasks.filter((tk) => tk.isDeleted && !tk.purgedFromTrash),
    [snapshot.tasks]
  );

  const deletedProjects = useMemo(
    () => snapshot.projects.filter((p) => p.isDeleted && !p.purgedFromTrash),
    [snapshot.projects]
  );

  // Exclude tasks belonging to COMPLETED, CANCELED, or soft-deleted projects from Smart Views (ThingsDaos.kt v1.6.17+)
  const activeProjectIds = useMemo(
    () => new Set(activeProjects.map((p) => p.id)),
    [activeProjects]
  );
  const openInActiveProj = useMemo(
    () => openTasks.filter((tk) => !tk.projectId || activeProjectIds.has(tk.projectId)),
    [openTasks, activeProjectIds]
  );

  const inboxTasks = useMemo(
    () =>
      openTasks.filter(
        (tk) => tk.scheduleState === 'INBOX' && tk.projectId == null && tk.areaId == null
      ),
    [openTasks]
  );

  const todayDaytimeTasks = useMemo(
    () =>
      openInActiveProj.filter(
        (tk) =>
          tk.scheduleState !== 'THIS_EVENING' &&
          (tk.scheduleState === 'TODAY' ||
            (tk.whenDate != null && tk.whenDate <= endOfToday) ||
            (tk.deadline != null && tk.deadline <= endOfToday))
      ),
    [openInActiveProj, endOfToday]
  );

  const todayEveningTasks = useMemo(() => {
    const dayIds = new Set(todayDaytimeTasks.map((x) => x.id));
    return openInActiveProj.filter(
      (tk) => tk.scheduleState === 'THIS_EVENING' && !dayIds.has(tk.id)
    );
  }, [openInActiveProj, todayDaytimeTasks]);

  // Exact v1.6.17+ UPCOMING: strictly excludes tasks already active in Today or This Evening!
  const upcomingTasks = useMemo(
    () =>
      openInActiveProj.filter(
        (tk) =>
          tk.scheduleState !== 'TODAY' &&
          tk.scheduleState !== 'THIS_EVENING' &&
          (tk.whenDate == null || tk.whenDate > endOfToday) &&
          (tk.deadline == null || tk.deadline > endOfToday) &&
          (tk.scheduleState === 'UPCOMING' ||
            (tk.whenDate != null && tk.whenDate > endOfToday) ||
            (tk.deadline != null && tk.deadline > endOfToday) ||
            tk.recurrenceRule != null)
      ),
    [openInActiveProj, endOfToday]
  );

  // Exact v1.6.16+ ANYTIME: strictly excludes dated & recurring tasks and non-active projects!
  const anytimeTasks = useMemo(
    () =>
      openInActiveProj.filter(
        (tk) =>
          tk.scheduleState === 'ANYTIME' &&
          tk.whenDate == null &&
          tk.recurrenceRule == null
      ),
    [openInActiveProj]
  );

  const somedayTasks = useMemo(
    () => openInActiveProj.filter((tk) => tk.scheduleState === 'SOMEDAY'),
    [openInActiveProj]
  );

  const logbookTasks = useMemo(
    () =>
      snapshot.tasks
        .filter(
          (tk) =>
            !tk.isDeleted && (tk.status === 'COMPLETED' || tk.status === 'CANCELED')
        )
        .sort(
          (a, b) =>
            (b.completedAt ?? b.updatedAt) - (a.completedAt ?? a.updatedAt)
        ),
    [snapshot.tasks]
  );

  const sidebarCounts = useMemo(
    () => ({
      inbox: inboxTasks.length,
      today: todayDaytimeTasks.length + todayEveningTasks.length,
      upcoming: upcomingTasks.length,
      anytime: anytimeTasks.length,
      someday: somedayTasks.length,
      trash: deletedTasks.length + deletedProjects.length
    }),
    [
      inboxTasks,
      todayDaytimeTasks,
      todayEveningTasks,
      upcomingTasks,
      anytimeTasks,
      somedayTasks,
      deletedTasks,
      deletedProjects
    ]
  );

  const statistics = useMemo(() => computeStatistics(snapshot, endOfToday), [snapshot, endOfToday]);
  const stalledProjects = useMemo(
    () => detectStalledProjects(snapshot.projects, snapshot.tasks),
    [snapshot.projects, snapshot.tasks]
  );

  // ==================== v2.5 + v3.0 Milestone 2 Derived State ====================
  const tagSortMode: TagSortMode = prefs.tagSortMode ?? 'alpha';
  const viewSortMode: ViewSortMode = prefs.viewSortMode ?? 'manual';
  const savedPerspectives = useMemo<SavedPerspective[]>(
    () =>
      [...(prefs.savedPerspectives ?? [])].sort(
        (a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title)
      ),
    [prefs.savedPerspectives]
  );
  const effectiveShowPerspectives = useMemo(
    () =>
      resolveEffectiveShowPerspectives({
        showPerspectives: prefs.showPerspectives,
        savedPerspectives
      }),
    [prefs.showPerspectives, savedPerspectives]
  );

  const tasksByIdMap = useMemo(
    () => new Map(snapshot.tasks.filter((tk) => !tk.isDeleted).map((tk) => [tk.id, tk])),
    [snapshot.tasks]
  );

  const openTaskIdSet = useMemo(
    () => new Set(openTasks.map((tk) => tk.id)),
    [openTasks]
  );

  const tagUsageCounts = useMemo(
    () =>
      computeTagUsageCounts({
        tasks: snapshot.tasks,
        projects: snapshot.projects,
        areas: snapshot.areas,
        projectTemplates: snapshot.projectTemplates
      }),
    [snapshot.tasks, snapshot.projects, snapshot.areas, snapshot.projectTemplates]
  );

  const todayStartMs = useMemo(() => startOfDayEpoch(new Date(endOfToday)), [endOfToday]);

  const logbookFocused7dMinutes = useMemo(() => {
    const cutoff = todayStartMs - 6 * 86_400_000;
    let sum = 0;
    for (const tk of logbookTasks) {
      if (tk.status !== 'COMPLETED') continue;
      const ts = tk.completedAt ?? tk.updatedAt;
      if (ts >= cutoff && tk.actualMinutes != null && tk.actualMinutes > 0) {
        sum += Math.trunc(tk.actualMinutes);
      }
    }
    return sum;
  }, [logbookTasks, todayStartMs]);

  const perspectiveTasksMap = useMemo(() => {
    const map = new Map<string, TaskRecord[]>();
    for (const persp of savedPerspectives) {
      map.set(
        persp.id,
        filterTasksForPerspective(
          openInActiveProj,
          persp,
          openTaskIdSet,
          todayStartMs,
          allNonDeletedProjectsMap
        )
      );
    }
    return map;
  }, [savedPerspectives, openInActiveProj, openTaskIdSet, todayStartMs, allNonDeletedProjectsMap]);

  const perspectiveCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const [id, list] of perspectiveTasksMap.entries()) {
      counts.set(id, list.length);
    }
    return counts;
  }, [perspectiveTasksMap]);

  useEffect(() => {
    if (
      activeNav.kind === 'perspective' &&
      (!effectiveShowPerspectives ||
        !savedPerspectives.some((p) => p.id === activeNav.perspectiveId))
    ) {
      setActiveNav({ kind: 'smart', view: 'TODAY' });
    }
  }, [activeNav, effectiveShowPerspectives, savedPerspectives]);

  // ==================== Calendar Integration State & Refresh (Android v1.6.21–v1.6.23 Parity) ====================
  const [hasCalendarPermission, setHasCalendarPermission] = useState<boolean>(() =>
    hasCalendarAccess()
  );
  const [availableCalendars, setAvailableCalendars] = useState<DeviceCalendarInfo[]>([]);
  const [calendarError, setCalendarError] = useState<string | null>(null);
  const [todayCalendarEvents, setTodayCalendarEvents] = useState<CalendarEventItem[]>([]);
  const [upcomingCalendarEventsByEpochDay, setUpcomingCalendarEventsByEpochDay] = useState<
    Map<number, CalendarEventItem[]>
  >(() => new Map());
  const [calendarRefreshTick, setCalendarRefreshTick] = useState(0);
  const [calendarNowMs, setCalendarNowMs] = useState<number>(() => Date.now());

  const activeTodayCalendarEvents = useMemo(() => {
    if (!prefs.calendarEventsEnabled || todayCalendarEvents.length === 0) {
      return [];
    }
    const todayEpochDay = localEpochDayFromMs(endOfToday);
    return filterActiveOrUpcomingCalendarEvents(
      todayCalendarEvents,
      todayEpochDay,
      todayEpochDay,
      calendarNowMs,
      false
    );
  }, [prefs.calendarEventsEnabled, todayCalendarEvents, endOfToday, calendarNowMs]);

  const workdayBounds = useMemo(
    () => normalizeWorkdayBounds(prefs.workdayStartMinutes, prefs.workdayEndMinutes),
    [prefs.workdayStartMinutes, prefs.workdayEndMinutes]
  );

  const todayCapacity = useMemo(
    () =>
      computeTodayCapacitySummary(
        [...todayDaytimeTasks, ...todayEveningTasks],
        activeTodayCalendarEvents,
        todayStartMs,
        endOfToday,
        workdayBounds.startMinutes,
        workdayBounds.endMinutes
      ),
    [
      todayDaytimeTasks,
      todayEveningTasks,
      activeTodayCalendarEvents,
      todayStartMs,
      endOfToday,
      workdayBounds.startMinutes,
      workdayBounds.endMinutes
    ]
  );

  const upcomingForecastDaysCount = useMemo(() => {
    const todayEpochDay = localEpochDayFromMs(endOfToday);
    let maxTargetEpochDay = todayEpochDay + 6;
    for (const tk of upcomingTasks) {
      const startDay = localEpochDayFromMs(computeUpcomingTargetDate(tk));
      const extraDays =
        tk.durationMinutes != null && tk.durationMinutes > 480
          ? Math.ceil(tk.durationMinutes / 480) - 1
          : 0;
      if (startDay + extraDays > maxTargetEpochDay) {
        maxTargetEpochDay = startDay + extraDays;
      }
    }
    for (const hd of snapshot.headings) {
      if (hd.isDeleted || hd.isArchived || hd.endDate == null) continue;
      const hdDay = localEpochDayFromMs(startOfDayEpoch(new Date(hd.endDate)));
      if (hdDay > maxTargetEpochDay) {
        maxTargetEpochDay = hdDay;
      }
    }
    return Math.max(7, Math.min(366, maxTargetEpochDay - todayEpochDay + 1));
  }, [upcomingTasks, snapshot.headings, endOfToday]);

  const upcomingWorkloadForecast = useMemo(
    () =>
      computeMultiDayWorkloadForecast({
        todayTasks: [...todayDaytimeTasks, ...todayEveningTasks],
        allOpenTasks: openInActiveProj,
        todayCalendarEvents: activeTodayCalendarEvents,
        upcomingCalendarEventsByEpochDay: prefs.calendarEventsEnabled
          ? upcomingCalendarEventsByEpochDay
          : new Map(),
        todayStartMillis: todayStartMs,
        daysCount: upcomingForecastDaysCount,
        workdayStartMinutes: workdayBounds.startMinutes,
        workdayEndMinutes: workdayBounds.endMinutes
      }),
    [
      todayDaytimeTasks,
      todayEveningTasks,
      openInActiveProj,
      activeTodayCalendarEvents,
      prefs.calendarEventsEnabled,
      upcomingCalendarEventsByEpochDay,
      todayStartMs,
      upcomingForecastDaysCount,
      workdayBounds.startMinutes,
      workdayBounds.endMinutes
    ]
  );

  const upcomingWorkloadByEpochDay = useMemo(
    () => new Map(upcomingWorkloadForecast.map((d) => [d.epochDay, d])),
    [upcomingWorkloadForecast]
  );

  const maxUpcomingHorizonEpochDay = useMemo(() => {
    const todayEpochDay = localEpochDayFromMs(endOfToday);
    let maxTaskDay = todayEpochDay + 1;
    for (const tk of upcomingTasks) {
      const targetDay = localEpochDayFromMs(computeUpcomingTargetDate(tk));
      if (targetDay > maxTaskDay) {
        maxTaskDay = targetDay;
      }
    }
    return Math.max(todayEpochDay + 30, Math.min(maxTaskDay, todayEpochDay + 180));
  }, [upcomingTasks, endOfToday]);

  useEffect(() => {
    if (!prefs.calendarEventsEnabled) return;
    const onFocusRefresh = () => {
      setCalendarNowMs(Date.now());
      setCalendarRefreshTick((n) => n + 1);
    };
    const onVisibilityRefresh = () => {
      if (document.visibilityState === 'visible') {
        setCalendarNowMs(Date.now());
        setCalendarRefreshTick((n) => n + 1);
      }
    };
    const nowInterval = window.setInterval(() => {
      setCalendarNowMs(Date.now());
    }, 30_000);
    window.addEventListener('focus', onFocusRefresh);
    document.addEventListener('visibilitychange', onVisibilityRefresh);
    return () => {
      window.clearInterval(nowInterval);
      window.removeEventListener('focus', onFocusRefresh);
      document.removeEventListener('visibilitychange', onVisibilityRefresh);
    };
  }, [prefs.calendarEventsEnabled]);

  useEffect(() => {
    let cancelled = false;

    const runCalendarSync = async () => {
      const permitted = hasCalendarAccess();
      if (cancelled) return;
      setHasCalendarPermission(permitted);

      if (!permitted) {
        setAvailableCalendars([]);
        setTodayCalendarEvents([]);
        setUpcomingCalendarEventsByEpochDay(new Map());
        return;
      }

      if (!prefs.calendarEventsEnabled && !settingsModalOpen) {
        setTodayCalendarEvents([]);
        setUpcomingCalendarEventsByEpochDay(new Map());
        return;
      }

      const calendars = await queryAvailableCalendars();
      if (cancelled) return;

      const stillPermitted = hasCalendarAccess();
      setHasCalendarPermission(stillPermitted);
      setAvailableCalendars(calendars);
      setCalendarError(getLastCalendarError());

      let effectiveSelectedIds = prefs.selectedCalendarIds;
      const availableIdSet = new Set(calendars.map((c) => c.id));
      const hasAnyMatchingSelected = effectiveSelectedIds.some((id) => availableIdSet.has(id));
      if (
        calendars.length > 0 &&
        (!prefs.calendarIdsInitialized ||
          (!hasAnyMatchingSelected && effectiveSelectedIds.length > 0))
      ) {
        const primaries = calendars.filter((c) => c.isPrimary).map((c) => c.id);
        effectiveSelectedIds =
          primaries.length > 0 ? primaries : calendars.map((c) => c.id);
        updatePrefs({
          selectedCalendarIds: effectiveSelectedIds,
          calendarIdsInitialized: true
        });
      }

      if (!prefs.calendarEventsEnabled || !stillPermitted || effectiveSelectedIds.length === 0) {
        setTodayCalendarEvents([]);
        setUpcomingCalendarEventsByEpochDay(new Map());
        return;
      }

      const todayEpochDay = localEpochDayFromMs(endOfToday);
      const eventsByDay = await queryEventsForDateRange(
        todayEpochDay,
        maxUpcomingHorizonEpochDay,
        effectiveSelectedIds,
        calendars
      );
      if (cancelled) return;

      const nowMs = Date.now();
      setCalendarNowMs(nowMs);
      setTodayCalendarEvents(
        filterActiveOrUpcomingCalendarEvents(
          eventsByDay.get(todayEpochDay) ?? [],
          todayEpochDay,
          todayEpochDay,
          nowMs,
          false
        )
      );
      const upcomingMap = new Map<number, CalendarEventItem[]>();
      for (const [day, list] of eventsByDay.entries()) {
        if (day >= todayEpochDay + 1 && list.length > 0) {
          const activeList = filterActiveOrUpcomingCalendarEvents(
            list,
            day,
            todayEpochDay,
            nowMs,
            true
          );
          if (activeList.length > 0) {
            upcomingMap.set(day, activeList);
          }
        }
      }
      setUpcomingCalendarEventsByEpochDay(upcomingMap);
    };

    void runCalendarSync();
    return () => {
      cancelled = true;
    };
  }, [
    prefs.calendarEventsEnabled,
    prefs.selectedCalendarIds,
    prefs.calendarIdsInitialized,
    settingsModalOpen,
    maxUpcomingHorizonEpochDay,
    endOfToday,
    calendarRefreshTick,
    updatePrefs
  ]);

  const handleToggleCalendarEventsEnabled = useCallback(
    (enabled: boolean) => {
      updatePrefs({ calendarEventsEnabled: enabled });
    },
    [updatePrefs]
  );

  const handleRequestCalendarAccess = useCallback(async () => {
    clearLastCalendarError();
    setCalendarError(null);
    try {
      const { email, calendars } = await connectGoogleCalendarPopup();
      setHasCalendarPermission(true);
      setAvailableCalendars(calendars);

      // Ensure newly connected primary calendar(s) are selected if no available calendar is selected yet
      setPrefs((prev) => {
        const availableSet = new Set(calendars.map((c) => c.id));
        const hasAnyMatchingSelected = prev.selectedCalendarIds.some((id) =>
          availableSet.has(id)
        );
        if (hasAnyMatchingSelected && prev.calendarIdsInitialized) {
          return prev;
        }
        const primaries = calendars.filter((c) => c.isPrimary).map((c) => c.id);
        const defaults = primaries.length > 0 ? primaries : calendars.map((c) => c.id);
        const next = {
          ...prev,
          selectedCalendarIds: defaults,
          calendarIdsInitialized: true
        };
        savePreferences(next);
        return next;
      });

      setCalendarRefreshTick((n) => n + 1);
      showToast(`✓ ${email} (${calendars.length})`);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      if (!msg.includes('popup-closed-by-user') && !msg.includes('cancelled-popup-request')) {
        setCalendarError(msg);
        showToast(msg);
      }
    }
  }, [showToast]);

  const handleImportIcsFile = useCallback(
    async (icsText: string, fileName: string) => {
      const imported = importIcsCalendarText(icsText, fileName);
      setPrefs((prev) => {
        const nextIds = prev.selectedCalendarIds.includes(imported.id)
          ? prev.selectedCalendarIds
          : [...prev.selectedCalendarIds, imported.id];
        const next = {
          ...prev,
          selectedCalendarIds: nextIds,
          calendarIdsInitialized: true
        };
        savePreferences(next);
        return next;
      });
      setHasCalendarPermission(true);
      setCalendarRefreshTick((n) => n + 1);
      showToast(`✓ ${imported.displayName}`);
    },
    [showToast]
  );

  const handleRemoveImportedCalendar = useCallback((calendarId: string) => {
    removeImportedIcsCalendar(calendarId);
    setPrefs((prev) => {
      const next = {
        ...prev,
        selectedCalendarIds: prev.selectedCalendarIds.filter((id) => id !== calendarId)
      };
      savePreferences(next);
      return next;
    });
    setHasCalendarPermission(hasCalendarAccess());
    setCalendarRefreshTick((n) => n + 1);
  }, []);

  const handleToggleSelectedCalendarId = useCallback((calendarId: string) => {
    setPrefs((prev) => {
      const exists = prev.selectedCalendarIds.includes(calendarId);
      const nextIds = exists
        ? prev.selectedCalendarIds.filter((id) => id !== calendarId)
        : [...prev.selectedCalendarIds, calendarId];
      const next = {
        ...prev,
        selectedCalendarIds: nextIds,
        calendarIdsInitialized: true
      };
      savePreferences(next);
      return next;
    });
  }, []);

  const handleSelectAllCalendars = useCallback(
    (selectAll: boolean) => {
      const nextIds = selectAll ? availableCalendars.map((c) => c.id) : [];
      updatePrefs({
        selectedCalendarIds: nextIds,
        calendarIdsInitialized: true
      });
    },
    [availableCalendars, updatePrefs]
  );

  const handleRefreshCalendars = useCallback(async () => {
    setCalendarRefreshTick((n) => n + 1);
  }, []);

  // Raw tasks visible in the current view BEFORE tag filtering (for ViewTagFilterUtils parity)
  const currentViewBaseTasks = useMemo((): TaskRecord[] => {
    if (activeNav.kind === 'smart') {
      switch (activeNav.view) {
        case 'INBOX':
          return inboxTasks;
        case 'TODAY':
          return [...todayDaytimeTasks, ...todayEveningTasks];
        case 'UPCOMING':
          return upcomingTasks;
        case 'ANYTIME':
          return anytimeTasks;
        case 'SOMEDAY':
          return somedayTasks;
        case 'LOGBOOK':
          return logbookTasks;
        case 'TRASH':
          return [];
      }
    }
    if (activeNav.kind === 'area') {
      return openTasks.filter(
        (tk) => tk.areaId === activeNav.areaId && tk.projectId == null
      );
    }
    if (activeNav.kind === 'perspective') {
      return perspectiveTasksMap.get(activeNav.perspectiveId) ?? [];
    }
    return openTasks.filter((tk) => tk.projectId === activeNav.projectId);
  }, [
    activeNav,
    inboxTasks,
    todayDaytimeTasks,
    todayEveningTasks,
    upcomingTasks,
    anytimeTasks,
    somedayTasks,
    logbookTasks,
    openTasks,
    perspectiveTasksMap
  ]);

  // Auto-prune completed/canceled/deleted/moved task IDs out of multi-selection (ThingsbookViewModel.kt v1.6.17+)
  useEffect(() => {
    if (selectedTaskIds.length === 0) {
      if (isExplicitMultiSelect) setIsExplicitMultiSelect(false);
      return;
    }
    const visibleIds = new Set(currentViewBaseTasks.map((tk) => tk.id));
    setSelectedTaskIds((prev) => {
      const next = prev.filter((id) => visibleIds.has(id));
      return next.length === prev.length ? prev : next;
    });
  }, [currentViewBaseTasks, selectedTaskIds.length, isExplicitMultiSelect]);

  // ViewTagFilterUtils: compute usedTags strictly from currentViewBaseTasks
  const usedTagsInCurrentView = useMemo((): TagRecord[] => {
    const usedIds = new Set<string>();
    for (const tk of currentViewBaseTasks) {
      for (const tid of tk.tagIds) {
        if (tagsMap.has(tid)) usedIds.add(tid);
      }
    }
    return Array.from(usedIds)
      .map((id) => tagsMap.get(id)!)
      .sort((a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title));
  }, [currentViewBaseTasks, tagsMap]);

  useEffect(() => {
    if (
      selectedFilterTagId &&
      !usedTagsInCurrentView.some((tg) => tg.id === selectedFilterTagId)
    ) {
      setSelectedFilterTagId(null);
    }
  }, [selectedFilterTagId, usedTagsInCurrentView]);

  const filterByTag = useCallback(
    (list: TaskRecord[]) => {
      if (!selectedFilterTagId) return list;
      return list.filter((tk) => tk.tagIds.includes(selectedFilterTagId));
    },
    [selectedFilterTagId]
  );

  const sortViewList = useCallback(
    (list: TaskRecord[]) => sortTasksForView(list, viewSortMode),
    [viewSortMode]
  );

  const isEffectiveDarkTheme = useMemo(() => {
    if (prefs.themeMode === 'dark') return true;
    if (prefs.themeMode === 'light') return false;
    return typeof window !== 'undefined'
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : false;
  }, [prefs.themeMode]);

  const activeAreaRecord = useMemo(
    () =>
      activeNav.kind === 'area' ? (areasMap.get(activeNav.areaId) ?? null) : null,
    [activeNav, areasMap]
  );

  const activeAreaViewMode = useMemo(
    () =>
      activeAreaRecord
        ? normalizeAreaViewMode(prefs.areaViewModes?.[activeAreaRecord.id])
        : 'list',
    [activeAreaRecord, prefs.areaViewModes]
  );

  const activeAreaRoadmapLayout = useMemo(() => {
    if (!activeAreaRecord || activeAreaViewMode !== 'roadmap') return null;
    return buildAreaPortfolioRoadmapLayout(
      activeAreaRecord,
      activeProjects,
      snapshot.headings,
      snapshot.tasks,
      todayStartMs,
      t.projectUngroupedColumn
    );
  }, [
    activeAreaRecord,
    activeAreaViewMode,
    activeProjects,
    snapshot.headings,
    snapshot.tasks,
    todayStartMs,
    t.projectUngroupedColumn
  ]);

  const activeProjectRecord = useMemo(
    () =>
      activeNav.kind === 'project'
        ? (allNonDeletedProjectsMap.get(activeNav.projectId) ?? null)
        : null,
    [activeNav, allNonDeletedProjectsMap]
  );

  const activeProjectViewMode: ProjectViewMode = useMemo(
    () =>
      activeProjectRecord
        ? normalizeProjectViewMode(activeProjectRecord.viewMode)
        : 'list',
    [activeProjectRecord]
  );

  const activeProjectRoadmapLayout = useMemo(() => {
    if (!activeProjectRecord || activeProjectViewMode !== 'roadmap') return null;
    return buildProjectRoadmapLayout(
      activeProjectRecord,
      snapshot.headings,
      filterByTag(currentViewBaseTasks),
      todayStartMs,
      t.projectUngroupedColumn
    );
  }, [
    activeProjectRecord,
    activeProjectViewMode,
    snapshot.headings,
    filterByTag,
    currentViewBaseTasks,
    todayStartMs,
    t.projectUngroupedColumn
  ]);

  const orderedVisibleTaskIds = useMemo((): string[] => {
    if (activeNav.kind === 'smart') {
      if (activeNav.view === 'TODAY') {
        return [
          ...sortViewList(filterByTag(todayDaytimeTasks)),
          ...sortViewList(filterByTag(todayEveningTasks))
        ].map((tk) => tk.id);
      }
      if (activeNav.view === 'UPCOMING') {
        const dayMap = new Map<number, TaskRecord[]>();
        for (const tk of filterByTag(upcomingTasks)) {
          const day = computeUpcomingTargetDate(tk);
          const arr = dayMap.get(day) ?? [];
          arr.push(tk);
          dayMap.set(day, arr);
        }
        return Array.from(dayMap.entries())
          .sort((a, b) => a[0] - b[0])
          .flatMap(([, list]) => sortViewList(list).map((tk) => tk.id));
      }
      if (activeNav.view === 'LOGBOOK') {
        return filterByTag(currentViewBaseTasks).map((tk) => tk.id);
      }
      if (activeNav.view === 'TRASH') {
        return [];
      }
      return sortViewList(filterByTag(currentViewBaseTasks)).map((tk) => tk.id);
    }
    if (activeNav.kind === 'project') {
      if (activeProjectViewMode === 'roadmap' && activeProjectRoadmapLayout) {
        return [
          ...activeProjectRoadmapLayout.plottedTasks.map((p) => p.task.id),
          ...activeProjectRoadmapLayout.unscheduledTasks.map((u) => u.id)
        ];
      }
      const projHeadings = snapshot.headings.filter(
        (h) => !h.isDeleted && !h.isArchived && h.projectId === activeNav.projectId
      );
      const validHeadingIdSet = new Set(projHeadings.map((h) => h.id));
      const visibleOpenProjTasks = filterByTag(currentViewBaseTasks);
      const ungrouped = sortViewList(
        visibleOpenProjTasks.filter((tk) => !tk.headingId || !validHeadingIdSet.has(tk.headingId))
      );
      const headed = projHeadings.flatMap((hd) =>
        sortViewList(visibleOpenProjTasks.filter((tk) => tk.headingId === hd.id))
      );
      return [...ungrouped, ...headed].map((tk) => tk.id);
    }
    return sortViewList(filterByTag(currentViewBaseTasks)).map((tk) => tk.id);
  }, [
    activeNav,
    activeProjectViewMode,
    activeProjectRoadmapLayout,
    sortViewList,
    filterByTag,
    todayDaytimeTasks,
    todayEveningTasks,
    upcomingTasks,
    currentViewBaseTasks,
    snapshot.headings
  ]);

  const isTodayTimelineFullScreen =
    isTimelineFullScreen &&
    activeNav.kind === 'smart' &&
    activeNav.view === 'TODAY' &&
    todayViewMode === 'timeline';
  const isRoadmapFullScreen =
    isTimelineFullScreen &&
    activeNav.kind === 'project' &&
    activeProjectViewMode === 'roadmap';
  const isAreaRoadmapFullScreen =
    isTimelineFullScreen &&
    activeNav.kind === 'area' &&
    activeAreaViewMode === 'roadmap';
  const isAnyTimelineFullScreen =
    isTodayTimelineFullScreen || isRoadmapFullScreen || isAreaRoadmapFullScreen;

  const enterTimelineFullScreen = useCallback(() => {
    setExpandedTaskId(null);
    setSelectedTaskIds([]);
    setMagicFabOpen(false);
    setQuickFindOpen(false);
    setViewSortDropdownOpen(false);
    setProjectHeaderMenuOpen(false);
    setProjectAreaDropdownOpen(false);
    setIsTimelineFullScreen(true);
  }, []);

  useEffect(() => {
    if (!isTimelineFullScreen) return;
    const inTodayTimeline =
      activeNav.kind === 'smart' &&
      activeNav.view === 'TODAY' &&
      todayViewMode === 'timeline';
    const inProjectRoadmap =
      activeNav.kind === 'project' && activeProjectViewMode === 'roadmap';
    const inAreaRoadmap =
      activeNav.kind === 'area' && activeAreaViewMode === 'roadmap';
    if (!inTodayTimeline && !inProjectRoadmap && !inAreaRoadmap) {
      setIsTimelineFullScreen(false);
    }
  }, [
    isTimelineFullScreen,
    activeNav,
    todayViewMode,
    activeProjectViewMode,
    activeAreaViewMode
  ]);

  // Always reset dragOverHeadingId on global dragend / drop so '__ungrouped__' never gets stuck visible
  useEffect(() => {
    const clearHeadingDragState = () => {
      setDragOverHeadingId(null);
    };
    window.addEventListener('dragend', clearHeadingDragState, true);
    window.addEventListener('drop', clearHeadingDragState, true);
    return () => {
      window.removeEventListener('dragend', clearHeadingDragState, true);
      window.removeEventListener('drop', clearHeadingDragState, true);
    };
  }, []);

  const handleSelectNav = useCallback(
    (nav: ActiveNavigation, highlightTaskId?: string) => {
      setActiveNav(nav);
      setMobileDrawerOpen(false);
      setEditingAreaHeader(false);
      setEditingHeadingId(null);
      setDragOverHeadingId(null);
      setProjectAreaDropdownOpen(false);
      setProjectHeaderMenuOpen(false);
      setViewSortDropdownOpen(false);
      setIsTimelineFullScreen(false);
      setSelectedTaskIds([]);
      if (highlightTaskId) {
        setExpandedTaskId(highlightTaskId);
      } else {
        setExpandedTaskId(null);
      }
    },
    []
  );

  // Android v1.7.0 + v2.0 + v3.2 Zen Focus & actualMinutes Timer Callbacks
  const tasksByIdRef = useRef(tasksByIdMap);
  tasksByIdRef.current = tasksByIdMap;

  const handleStartFocusTimer = useCallback(
    (taskId: string, taskTitle: string, durationMinutes?: number | null) => {
      if (focusTimerRef.current) {
        flushFocusMinutes(focusTimerRef.current, true);
      }
      if (taskId) {
        const live = tasksByIdRef.current.get(taskId);
        if (live && !live.isDeleted && live.status === 'OPEN' && !live.isInProgress) {
          void localDb.upsertTask({ id: taskId, isInProgress: true });
        }
      }
      const mins =
        durationMinutes != null && Number.isFinite(durationMinutes) && durationMinutes > 0
          ? Math.max(1, Math.min(720, Math.trunc(durationMinutes)))
          : 25;
      const secs = mins * 60;
      const nextTimer: FocusTimerUiState = {
        taskId,
        taskTitle,
        totalSeconds: secs,
        remainingSeconds: secs,
        isRunning: true,
        sessionFocusedSeconds: 0,
        isBreakMode: false
      };
      focusTimerRef.current = nextTimer;
      setFocusTimer(nextTimer);
    },
    [flushFocusMinutes]
  );

  const handleStopFocusTimer = useCallback(() => {
    flushFocusMinutes(focusTimerRef.current, true);
    focusTimerRef.current = null;
    setFocusTimer(null);
    setZenFocusOpen(false);
  }, [flushFocusMinutes]);

  const handleCompleteFocusTimerTask = useCallback(() => {
    const cur = focusTimerRef.current;
    if (!cur) return;
    flushFocusMinutes(cur, true);
    const tid = cur.taskId;
    focusTimerRef.current = null;
    setFocusTimer(null);
    setZenFocusOpen(false);
    void localDb.upsertTask({ id: tid, status: 'COMPLETED' });
  }, [flushFocusMinutes]);

  const handleStartFocusBreak = useCallback(() => {
    const cur = focusTimerRef.current;
    if (!cur) return;
    flushFocusMinutes(cur, true);
    const breakState: FocusTimerUiState = {
      taskId: cur.taskId,
      taskTitle: cur.taskTitle,
      totalSeconds: 300,
      remainingSeconds: 300,
      isRunning: true,
      sessionFocusedSeconds: 0,
      isBreakMode: true
    };
    focusTimerRef.current = breakState;
    setFocusTimer(breakState);
  }, [flushFocusMinutes]);

  const handleBackToFocusFromBreak = useCallback(() => {
    const cur = focusTimerRef.current;
    if (!cur) return;
    const liveTask = snapshot.tasks.find((tk) => tk.id === cur.taskId);
    const mins =
      liveTask?.durationMinutes != null &&
      Number.isFinite(liveTask.durationMinutes) &&
      liveTask.durationMinutes > 0
        ? Math.max(1, Math.min(720, Math.trunc(liveTask.durationMinutes)))
        : 25;
    const secs = mins * 60;
    const nextFocus: FocusTimerUiState = {
      taskId: cur.taskId,
      taskTitle: liveTask?.title ?? cur.taskTitle,
      totalSeconds: secs,
      remainingSeconds: secs,
      isRunning: true,
      sessionFocusedSeconds: 0,
      isBreakMode: false
    };
    focusTimerRef.current = nextFocus;
    setFocusTimer(nextFocus);
  }, [snapshot.tasks]);

  const handleCycleThemeMode = useCallback(() => {
    const nextTheme: ThemeMode =
      prefs.themeMode === 'system'
        ? 'light'
        : prefs.themeMode === 'light'
          ? 'dark'
          : 'system';
    updatePrefs({ themeMode: nextTheme });
    showToast(`✓ ${t.v32CmdToggleTheme}`);
  }, [prefs.themeMode, updatePrefs, showToast, t.v32CmdToggleTheme]);

  const handleCreateSmartTaskFromPalette = useCallback(
    async (parsed: ParsedSmartSyntax) => {
      const todayEpoch = startOfDayEpoch();
      const tomorrowEpoch = addDaysLocal(todayEpoch, 1);
      let projectId: string | null = null;
      let areaId: string | null = null;
      let scheduleState: TaskRecord['scheduleState'] = 'INBOX';
      let whenDate: number | null = parsed.whenDate;

      if (parsed.matchedProject) {
        projectId = parsed.matchedProject.id;
        areaId = null;
        scheduleState = parsed.scheduleState ?? 'ANYTIME';
      } else if (parsed.matchedArea) {
        projectId = null;
        areaId = parsed.matchedArea.id;
        scheduleState = parsed.scheduleState ?? 'ANYTIME';
      } else if (parsed.scheduleState != null) {
        scheduleState = parsed.scheduleState;
        if (activeNav.kind === 'project') {
          projectId = activeNav.projectId;
        } else if (activeNav.kind === 'area') {
          areaId = activeNav.areaId;
        }
      } else if (activeNav.kind === 'project') {
        projectId = activeNav.projectId;
        scheduleState = 'ANYTIME';
      } else if (activeNav.kind === 'area') {
        areaId = activeNav.areaId;
        scheduleState = 'ANYTIME';
      } else if (activeNav.kind === 'smart') {
        if (activeNav.view === 'TODAY') {
          scheduleState = 'TODAY';
          whenDate = todayEpoch;
        } else if (activeNav.view === 'UPCOMING') {
          scheduleState = 'UPCOMING';
          whenDate = tomorrowEpoch;
        } else if (activeNav.view === 'ANYTIME') {
          scheduleState = 'ANYTIME';
        } else if (activeNav.view === 'SOMEDAY') {
          scheduleState = 'SOMEDAY';
        } else {
          scheduleState = 'INBOX';
        }
      }

      const minOrder =
        openTasks.length > 0
          ? openTasks.reduce((m, tk) => Math.min(m, tk.sortOrder), Infinity)
          : null;
      const mergedTags = Array.from(
        new Set([
          ...parsed.matchedTagIds,
          ...(selectedFilterTagId ? [selectedFilterTagId] : [])
        ])
      );

      const created = await localDb.upsertTask({
        title: parsed.cleanTitle || t.entityNewTodo,
        scheduleState,
        whenDate,
        reminderTime: parsed.reminderTime,
        deadline: parsed.deadline,
        durationMinutes: parsed.durationMinutes,
        tagIds: mergedTags,
        isMilestone: parsed.isMilestone,
        isInProgress: parsed.isInProgress,
        projectId,
        areaId,
        sortOrder: computeMidpointSortOrder(
          null,
          minOrder != null && Number.isFinite(minOrder) ? minOrder : null
        )
      });
      showToast(`✓ ${created.title}`);
    },
    [activeNav, openTasks, selectedFilterTagId, showToast, t.entityNewTodo]
  );

  const handleDuplicateTask = useCallback(
    async (taskId: string) => {
      const newId = await localDb.duplicateTask(taskId, t.copySuffix);
      if (newId) {
        setSelectedTaskIds([]);
        setExpandedTaskId(newId);
      }
    },
    [t.copySuffix]
  );

  const handleBatchDuplicateSelected = useCallback(async () => {
    if (selectedTaskIds.length === 0) return;
    const selectedSet = new Set(selectedTaskIds);
    const ordered = orderedVisibleTaskIds.filter((id) => selectedSet.has(id));
    const targetIds = ordered.length > 0 ? ordered : [...selectedTaskIds];
    const createdIds = await localDb.batchDuplicateTasks(targetIds, t.copySuffix);
    if (createdIds.length > 0) {
      setSelectedTaskIds(createdIds);
      showToast(`✓ +${createdIds.length} ${t.entityTodos}`);
    }
  }, [selectedTaskIds, orderedVisibleTaskIds, t, showToast]);

  const handleDuplicateProject = useCallback(
    async (projectId: string) => {
      const newId = await localDb.duplicateProject(projectId, t.copySuffix);
      if (newId) {
        handleSelectNav({ kind: 'project', projectId: newId });
      }
    },
    [t.copySuffix, handleSelectNav]
  );

  const executeDeleteProject = useCallback(
    async (projectId: string, deleteAssociatedTasks: boolean) => {
      setPendingDeleteProject(null);
      await localDb.deleteProject(projectId, deleteAssociatedTasks);
      if (activeNav.kind === 'project' && activeNav.projectId === projectId) {
        handleSelectNav({ kind: 'smart', view: 'TODAY' });
      }
    },
    [activeNav, handleSelectNav]
  );

  const requestDeleteProject = useCallback(
    async (project: ProjectRecord) => {
      const associatedTasksCount = snapshot.tasks.filter(
        (tk) => !tk.isDeleted && tk.projectId === project.id
      ).length;
      if (associatedTasksCount > 0) {
        setPendingDeleteProject({ project, associatedTasksCount });
        return;
      }
      await executeDeleteProject(project.id, true);
    },
    [snapshot.tasks, executeDeleteProject]
  );

  const executeCompleteProject = useCallback(
    async (projectId: string, completeOpenTasks: boolean) => {
      setPendingCompleteProject(null);
      const wasViewingProject =
        activeNav.kind === 'project' && activeNav.projectId === projectId;
      undoNavRestoreRef.current = wasViewingProject
        ? { kind: 'project', projectId }
        : null;
      if (completeOpenTasks) {
        setExpandedTaskId(null);
        setSelectedTaskIds([]);
      }
      await localDb.completeProjectWithUndo(projectId, completeOpenTasks);
      if (wasViewingProject) {
        handleSelectNav({ kind: 'smart', view: 'LOGBOOK' });
      }
    },
    [activeNav, handleSelectNav]
  );

  const requestCompleteProject = useCallback(
    async (project: ProjectRecord) => {
      if (project.status === 'COMPLETED') {
        await localDb.upsertProject({ id: project.id, status: 'ACTIVE' });
        return;
      }
      const openTasksCount = snapshot.tasks.filter(
        (tk) => !tk.isDeleted && tk.projectId === project.id && tk.status === 'OPEN'
      ).length;
      if (openTasksCount > 0) {
        setPendingCompleteProject({ project, openTasksCount });
        return;
      }
      await executeCompleteProject(project.id, false);
    },
    [snapshot.tasks, executeCompleteProject]
  );

  const handleToggleBiometricAppLock = useCallback(
    (enabled: boolean) => {
      if (enabled) {
        setAppLockSetupOpen(true);
      } else {
        updatePrefs({
          biometricAppLockEnabled: false,
          appLockPinHash: '',
          appLockWebAuthnCredId: ''
        });
        setIsAppLocked(false);
      }
    },
    [updatePrefs]
  );

  // Preselected Area ID for New Project (current Area, Project's parent Area, or Perspective's Area)
  const defaultAreaIdForNewProject = useMemo((): string | null => {
    if (activeNav.kind === 'area') return activeNav.areaId;
    if (activeNav.kind === 'project') {
      return allNonDeletedProjectsMap.get(activeNav.projectId)?.areaId ?? null;
    }
    if (activeNav.kind === 'perspective') {
      return savedPerspectives.find((p) => p.id === activeNav.perspectiveId)?.areaId ?? null;
    }
    return null;
  }, [activeNav, allNonDeletedProjectsMap, savedPerspectives]);

  // Create New To-Do at Top of Current Context (ThingsbookViewModel.createTaskAtPosition dropIndex=0 — v1.6.18 parity)
  const createNewTodoInCurrentContext = useCallback(
    async (explicitHeadingId?: string | null) => {
      const todayEpoch = startOfDayEpoch();
      const tomorrowEpoch = addDaysLocal(todayEpoch, 1);
      let scheduleState: TaskRecord['scheduleState'] = 'INBOX';
      let whenDate: number | null = null;
      let projectId: string | null = null;
      let headingId: string | null = null;
      let areaId: string | null = null;
      let extraTagIds: string[] = [];
      let initialEffortLevel: EffortLevel | null = null;
      let initialInProgress = false;
      const isLogbookRedirect =
        activeNav.kind === 'smart' &&
        (activeNav.view === 'LOGBOOK' || activeNav.view === 'TRASH');

      if (activeNav.kind === 'smart') {
        if (activeNav.view === 'TODAY') {
          scheduleState = 'TODAY';
          whenDate = todayEpoch;
        } else if (activeNav.view === 'UPCOMING') {
          scheduleState = 'UPCOMING';
          // Match earliest visible Upcoming group date (>= tomorrow) so new item lands at top of Upcoming
          let earliestUpcoming = tomorrowEpoch;
          if (upcomingTasks.length > 0) {
            const minTarget = upcomingTasks.reduce(
              (m, tk) => Math.min(m, computeUpcomingTargetDate(tk)),
              Infinity
            );
            if (Number.isFinite(minTarget)) {
              earliestUpcoming = Math.max(tomorrowEpoch, minTarget);
            }
          }
          whenDate = earliestUpcoming;
        } else if (activeNav.view === 'ANYTIME') {
          scheduleState = 'ANYTIME';
        } else if (activeNav.view === 'SOMEDAY') {
          scheduleState = 'SOMEDAY';
        } else {
          // INBOX, LOGBOOK, and TRASH all create an INBOX to-do
          scheduleState = 'INBOX';
        }
      } else if (activeNav.kind === 'area') {
        areaId = activeNav.areaId;
        scheduleState = 'ANYTIME';
      } else if (activeNav.kind === 'project') {
        projectId = activeNav.projectId;
        // When triggered via Ctrl+N or + To-Do (no explicit heading), force ungrouped top of project
        headingId = explicitHeadingId ?? null;
        scheduleState = 'ANYTIME';
      } else if (activeNav.kind === 'perspective') {
        const persp = savedPerspectives.find((p) => p.id === activeNav.perspectiveId);
        if (persp) {
          areaId = persp.areaId;
          extraTagIds = persp.categoryIds;
          initialEffortLevel = persp.effortLevel;
          initialInProgress = Boolean(persp.inProgressOnly);
          if (
            persp.dueWindow === 'TODAY' ||
            persp.dueWindow === 'OVERDUE' ||
            persp.dueWindow === 'NEXT_7_DAYS'
          ) {
            scheduleState = 'TODAY';
            whenDate = todayEpoch;
          } else {
            scheduleState = 'ANYTIME';
          }
        }
      }

      // Compute top-of-list sortOrder (index 0) within the target container
      let referenceTasks: TaskRecord[];
      if (isLogbookRedirect) {
        referenceTasks = inboxTasks;
      } else if (activeNav.kind === 'project') {
        if (headingId) {
          referenceTasks = currentViewBaseTasks.filter((tk) => tk.headingId === headingId);
        } else {
          referenceTasks = currentViewBaseTasks.filter((tk) => !tk.headingId);
        }
      } else if (activeNav.kind === 'smart' && activeNav.view === 'TODAY') {
        referenceTasks = todayDaytimeTasks.length > 0 ? todayDaytimeTasks : currentViewBaseTasks;
      } else {
        referenceTasks = currentViewBaseTasks;
      }

      const firstSortOrder =
        referenceTasks.length > 0
          ? referenceTasks.reduce((min, tk) => Math.min(min, tk.sortOrder), Infinity)
          : null;
      const topSortOrder = computeMidpointSortOrder(
        null,
        firstSortOrder != null && Number.isFinite(firstSortOrder) ? firstSortOrder : null
      );

      const mergedTags = Array.from(
        new Set([
          ...extraTagIds,
          ...(!isLogbookRedirect && selectedFilterTagId ? [selectedFilterTagId] : [])
        ])
      );

      const created = await localDb.upsertTask({
        title: '',
        scheduleState,
        whenDate,
        projectId,
        headingId,
        areaId,
        tagIds: mergedTags,
        effortLevel: initialEffortLevel,
        ...(initialInProgress ? { isInProgress: true } : {}),
        sortOrder: topSortOrder
      });

      if (isLogbookRedirect) {
        setActiveNav({ kind: 'smart', view: 'INBOX' });
      }
      setSelectedTaskIds([]);
      setExpandedTaskId(created.id);
      if (!explicitHeadingId) {
        workspaceScrollRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
      }
    },
    [
      activeNav,
      currentViewBaseTasks,
      inboxTasks,
      todayDaytimeTasks,
      upcomingTasks,
      selectedFilterTagId,
      savedPerspectives
    ]
  );

  // Create New Heading inside Active Project (mounts in inline edit mode!)
  const createNewHeadingInActiveProject = useCallback(async () => {
    if (activeNav.kind !== 'project') return;
    const projHeadings = snapshot.headings.filter(
      (h) => !h.isDeleted && h.projectId === activeNav.projectId
    );
    const maxOrder = projHeadings.reduce((m, h) => Math.max(m, h.sortOrder), 0);
    const created = await localDb.upsertHeading({
      projectId: activeNav.projectId,
      title: '',
      sortOrder: maxOrder + 1000
    });
    setEditingHeadingId(created.id);
    setEditingHeadingTitle('');
  }, [activeNav, snapshot.headings]);

  const handleMagicPlusDropInsert = useCallback(
    async (drop: MagicPlusResolvedDrop) => {
      if (drop.mode === 'heading') {
        if (activeNav.kind !== 'project') return;
        const sortOrder = computeMidpointSortOrder(
          drop.prevHeadingOrder,
          drop.nextHeadingOrder
        );
        const created = await localDb.upsertHeading({
          projectId: activeNav.projectId,
          title: '',
          sortOrder
        });
        setEditingHeadingId(created.id);
        setEditingHeadingTitle('');
        return;
      }

      if (
        activeNav.kind === 'smart' &&
        (activeNav.view === 'LOGBOOK' || activeNav.view === 'TRASH')
      ) {
        return;
      }

      const todayEpoch = startOfDayEpoch();
      const tomorrowEpoch = addDaysLocal(todayEpoch, 1);
      let scheduleState: TaskRecord['scheduleState'] = 'INBOX';
      let whenDate: number | null = null;
      let projectId: string | null = null;
      let headingId: string | null = null;
      let areaId: string | null = null;
      let extraTagIds: string[] = [];
      let initialEffortLevel: EffortLevel | null = null;
      let initialInProgress = false;

      if (activeNav.kind === 'smart') {
        if (activeNav.view === 'TODAY') {
          scheduleState = drop.evening ? 'THIS_EVENING' : 'TODAY';
          whenDate = todayEpoch;
        } else if (activeNav.view === 'UPCOMING') {
          scheduleState = 'UPCOMING';
          let earliestUpcoming = tomorrowEpoch;
          if (upcomingTasks.length > 0) {
            const minTarget = upcomingTasks.reduce(
              (m, tk) => Math.min(m, computeUpcomingTargetDate(tk)),
              Infinity
            );
            if (Number.isFinite(minTarget)) {
              earliestUpcoming = Math.max(tomorrowEpoch, minTarget);
            }
          }
          whenDate = drop.whenDate ?? earliestUpcoming;
        } else if (activeNav.view === 'ANYTIME') {
          scheduleState = 'ANYTIME';
        } else if (activeNav.view === 'SOMEDAY') {
          scheduleState = 'SOMEDAY';
        } else {
          scheduleState = 'INBOX';
        }
      } else if (activeNav.kind === 'area') {
        areaId = activeNav.areaId;
        scheduleState = 'ANYTIME';
      } else if (activeNav.kind === 'project') {
        projectId = activeNav.projectId;
        headingId = drop.headingId;
        scheduleState = 'ANYTIME';
        if (headingId) {
          setCollapsedHeadingIds((prev) => {
            if (!prev.has(headingId!)) return prev;
            const next = new Set(prev);
            next.delete(headingId!);
            return next;
          });
        }
      } else if (activeNav.kind === 'perspective') {
        const persp = savedPerspectives.find((p) => p.id === activeNav.perspectiveId);
        if (persp) {
          areaId = persp.areaId;
          extraTagIds = persp.categoryIds;
          initialEffortLevel = persp.effortLevel;
          initialInProgress = Boolean(persp.inProgressOnly);
          if (
            persp.dueWindow === 'TODAY' ||
            persp.dueWindow === 'OVERDUE' ||
            persp.dueWindow === 'NEXT_7_DAYS'
          ) {
            scheduleState = 'TODAY';
            whenDate = todayEpoch;
          } else {
            scheduleState = 'ANYTIME';
          }
        }
      }

      const sortOrder = computeMidpointSortOrder(
        drop.prevTaskOrder,
        drop.nextTaskOrder
      );

      const created = await localDb.upsertTask({
        title: '',
        scheduleState,
        whenDate,
        projectId,
        headingId,
        areaId,
        tagIds: Array.from(
          new Set([...extraTagIds, ...(selectedFilterTagId ? [selectedFilterTagId] : [])])
        ),
        effortLevel: initialEffortLevel,
        ...(initialInProgress ? { isInProgress: true } : {}),
        sortOrder
      });

      setSelectedTaskIds([]);
      setExpandedTaskId(created.id);
    },
    [activeNav, upcomingTasks, selectedFilterTagId, savedPerspectives]
  );

  // Global Keyboard Shortcuts + Ordered Escape Stack (Android v1.7.0 MainActivity + ThingsSmartViewScreen parity)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (isAppLocked) return;

      const target = e.target as HTMLElement | null;
      const inInput =
        target != null &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable);

      if (e.key === 'Escape') {
        if (zenFocusOpen) return setZenFocusOpen(false);
        if (weeklyReviewModal.open) return setWeeklyReviewModal((prev) => ({ ...prev, open: false }));
        if (templatesModal.open) return setTemplatesModal((prev) => ({ ...prev, open: false }));
        if (quickShareDraft) return setQuickShareDraft(null);
        if (pendingDeleteProject) return setPendingDeleteProject(null);
        if (pendingCompleteProject) return setPendingCompleteProject(null);
        if (emptyTrashConfirmOpen) return setEmptyTrashConfirmOpen(false);
        if (appLockSetupOpen) return setAppLockSetupOpen(false);
        if (perspectiveModalState.open) {
          return setPerspectiveModalState({ open: false, perspective: null });
        }
        if (dependencyModalTaskId) return setDependencyModalTaskId(null);
        if (batchTagPickerOpen) return setBatchTagPickerOpen(false);
        if (whenModalTaskId) return setWhenModalTaskId(null);
        if (deadlineModalTarget) return setDeadlineModalTarget(null);
        if (repeatModalTaskId) return setRepeatModalTaskId(null);
        if (moveModalTaskIds) return setMoveModalTaskIds(null);
        if (tagPickerTaskId) return setTagPickerTaskId(null);
        if (areaManagerOpen) return setAreaManagerOpen(false);
        if (quickCreateAreaOpen) return setQuickCreateAreaOpen(false);
        if (projectModalState.open) return setProjectModalState({ open: false });
        if (tagManagerOpen) return setTagManagerOpen(false);
        if (notificationsModalOpen) return setNotificationsModalOpen(false);
        if (geminiModalOpen) return setGeminiModalOpen(false);
        if (authModalOpen) return setAuthModalOpen(false);
        if (settingsModalOpen) return setSettingsModalOpen(false);
        if (magicFabOpen) return setMagicFabOpen(false);
        if (projectHeaderMenuOpen) return setProjectHeaderMenuOpen(false);
        if (projectAreaDropdownOpen) return setProjectAreaDropdownOpen(false);
        if (viewSortDropdownOpen) return setViewSortDropdownOpen(false);
        if (quickFindOpen) return setQuickFindOpen(false);
        if (expandedTaskId) return setExpandedTaskId(null);
        if (isTimelineFullScreen) return setIsTimelineFullScreen(false);
        if (selectedTaskIds.length > 0) return setSelectedTaskIds([]);
        if (mobileDrawerOpen) return setMobileDrawerOpen(false);
        return;
      }

      // Pure Timeline-only Full Screen: allow F to exit, block all non-Escape global shortcuts
      if (
        !inInput &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        e.key.toLowerCase() === 'f' &&
        isTimelineFullScreen
      ) {
        e.preventDefault();
        setIsTimelineFullScreen(false);
        return;
      }
      if (isTodayTimelineFullScreen || isRoadmapFullScreen) {
        return;
      }
      if (isAreaRoadmapFullScreen) {
        return;
      }

      const inInteractiveControl =
        inInput || Boolean(target?.closest('button, a, [role="button"]'));
      const anyOtherModalOpen =
        zenFocusOpen ||
        weeklyReviewModal.open ||
        templatesModal.open ||
        Boolean(quickShareDraft) ||
        Boolean(pendingDeleteProject) ||
        Boolean(pendingCompleteProject) ||
        emptyTrashConfirmOpen ||
        appLockSetupOpen ||
        perspectiveModalState.open ||
        Boolean(dependencyModalTaskId) ||
        batchTagPickerOpen ||
        Boolean(whenModalTaskId) ||
        Boolean(deadlineModalTarget) ||
        Boolean(repeatModalTaskId) ||
        Boolean(moveModalTaskIds) ||
        Boolean(tagPickerTaskId) ||
        areaManagerOpen ||
        quickCreateAreaOpen ||
        projectModalState.open ||
        tagManagerOpen ||
        notificationsModalOpen ||
        geminiModalOpen ||
        authModalOpen ||
        settingsModalOpen ||
        quickFindOpen;

      if (
        !inInput &&
        !inInteractiveControl &&
        !expandedTaskId &&
        !anyOtherModalOpen &&
        selectedTaskIds.length === 1 &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        (e.key === 'Enter' || e.key === ' ')
      ) {
        e.preventDefault();
        setExpandedTaskId(selectedTaskIds[0]);
        return;
      }

      if (
        !inInput &&
        !expandedTaskId &&
        !anyOtherModalOpen &&
        selectedTaskIds.length === 1 &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        e.key === 'F2'
      ) {
        e.preventDefault();
        setInlineQuickEditRequest({
          taskId: selectedTaskIds[0],
          nonce: Date.now()
        });
        return;
      }

      const lower = e.key.toLowerCase();

      // F -> Toggle Full-Screen Timeline / Portfolio / Gantt mode when not typing in an input
      if (
        !inInput &&
        !inInteractiveControl &&
        !expandedTaskId &&
        !anyOtherModalOpen &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        lower === 'f'
      ) {
        const canEnterFs =
          (activeNav.kind === 'smart' &&
            activeNav.view === 'TODAY' &&
            todayViewMode === 'timeline') ||
          (activeNav.kind === 'project' && activeProjectViewMode === 'roadmap') ||
          (activeNav.kind === 'area' && activeAreaViewMode === 'roadmap');
        if (canEnterFs) {
          e.preventDefault();
          enterTimelineFullScreen();
          return;
        }
      }

      // Global Ctrl+Shift+R / Cmd+Shift+R -> Guided GTD Weekly Review (v2.0)
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && lower === 'r') {
        e.preventDefault();
        setMobileDrawerOpen(false);
        setMagicFabOpen(false);
        setWeeklyReviewModal({ open: true, step: 1 });
        return;
      }

      // Global Ctrl+Shift+N / Cmd+Shift+N / Alt+Shift+N -> New Project Modal (works everywhere, even when editing a task!)
      if ((e.ctrlKey || e.metaKey || (e.altKey && !e.ctrlKey)) && e.shiftKey && lower === 'n') {
        e.preventDefault();
        setMoveModalTaskIds(null);
        setMobileDrawerOpen(false);
        setMagicFabOpen(false);
        setProjectModalState({
          open: true,
          project: null,
          defaultAreaId: defaultAreaIdForNewProject
        });
        return;
      }

      // Global Ctrl+N / Cmd+N / Alt+N -> New To-Do at top of current context (works everywhere, even when editing!)
      if ((e.ctrlKey || e.metaKey || (e.altKey && !e.ctrlKey)) && !e.shiftKey && lower === 'n') {
        e.preventDefault();
        setProjectModalState({ open: false });
        setMoveModalTaskIds(null);
        setMobileDrawerOpen(false);
        setMagicFabOpen(false);
        void createNewTodoInCurrentContext(null);
        return;
      }

      if (e.ctrlKey || e.metaKey) {
        if (lower === 'f' || lower === 'k') {
          e.preventDefault();
          setQuickFindOpen((v) => !v);
          return;
        }
        if (lower === 'g') {
          e.preventDefault();
          setGeminiModalTab('braindump');
          setGeminiModalOpen(true);
          return;
        }
        // Ctrl+Z -> Undo last task/project completion or deletion (when not typing in a text field)
        if (lower === 'z' && !e.shiftKey && !inInput) {
          e.preventDefault();
          setUndoBanner(null);
          void localDb.performUndo().then((restored) => {
            if (restored && undoNavRestoreRef.current) {
              const targetNav = undoNavRestoreRef.current;
              undoNavRestoreRef.current = null;
              handleSelectNav(targetNav);
            }
          });
          return;
        }
        // Ctrl+D -> Duplicate expanded To-Do, selected To-Dos, or active Project
        if (lower === 'd' && !e.shiftKey) {
          if (expandedTaskId) {
            e.preventDefault();
            void handleDuplicateTask(expandedTaskId);
            return;
          }
          if (!inInput && selectedTaskIds.length > 0) {
            e.preventDefault();
            void handleBatchDuplicateSelected();
            return;
          }
          if (!inInput && activeNav.kind === 'project') {
            e.preventDefault();
            void handleDuplicateProject(activeNav.projectId);
            return;
          }
        }
        // Ctrl+S -> Open When Schedule modal for expanded To-Do
        if (lower === 's' && !e.shiftKey && expandedTaskId) {
          e.preventDefault();
          setWhenModalTaskId(expandedTaskId);
          return;
        }
        // Ctrl+Shift+D -> Open Deadline modal for expanded To-Do
        if (lower === 'd' && e.shiftKey && expandedTaskId) {
          e.preventDefault();
          setDeadlineModalTarget({ kind: 'task', taskId: expandedTaskId });
          return;
        }
        // Ctrl+Shift+M -> Move expanded or selected To-Dos
        if (lower === 'm' && e.shiftKey && (expandedTaskId || selectedTaskIds.length > 0)) {
          e.preventDefault();
          setMoveModalTaskIds(expandedTaskId ? [expandedTaskId] : selectedTaskIds);
          return;
        }
        // Ctrl+Backspace / Ctrl+Delete -> Move expanded or selected To-Dos to Trash
        if (e.key === 'Backspace' || e.key === 'Delete') {
          if (expandedTaskId) {
            e.preventDefault();
            const tid = expandedTaskId;
            setExpandedTaskId(null);
            void localDb.softDeleteTasks([tid]);
            return;
          }
          if (!inInput && selectedTaskIds.length > 0) {
            e.preventDefault();
            const ids = [...selectedTaskIds];
            setSelectedTaskIds([]);
            void localDb.softDeleteTasks(ids);
            return;
          }
        }
        if (
          lower === 'a' &&
          !e.shiftKey &&
          !inInput &&
          !expandedTaskId &&
          !projectModalState.open &&
          !moveModalTaskIds
        ) {
          if (orderedVisibleTaskIds.length > 0) {
            e.preventDefault();
            setIsExplicitMultiSelect(true);
            setSelectedTaskIds(orderedVisibleTaskIds);
          }
          return;
        }
        if (!inInput && ['1', '2', '3', '4', '5', '6', '7'].includes(lower)) {
          e.preventDefault();
          const views: SmartViewType[] = [
            'INBOX',
            'TODAY',
            'UPCOMING',
            'ANYTIME',
            'SOMEDAY',
            'LOGBOOK',
            'TRASH'
          ];
          handleSelectNav({ kind: 'smart', view: views[Number(lower) - 1] });
          return;
        }

        // Ctrl+<TagShortcut> toggles tag on expanded or selected tasks when not typing in an input!
        const targetIds = expandedTaskId
          ? [expandedTaskId]
          : selectedTaskIds;
        if (!inInput && targetIds.length > 0 && lower.length === 1) {
          const matchedTag = snapshot.tags.find(
            (tg) =>
              !tg.isDeleted &&
              tg.shortcut &&
              tg.shortcut.toLowerCase() === lower
          );
          if (matchedTag) {
            e.preventDefault();
            for (const tid of targetIds) {
              const tk = snapshot.tasks.find((x) => x.id === tid);
              if (!tk) continue;
              const nextTags = tk.tagIds.includes(matchedTag.id)
                ? tk.tagIds.filter((id) => id !== matchedTag.id)
                : [...tk.tagIds, matchedTag.id];
              void localDb.upsertTask({ id: tk.id, tagIds: nextTags });
            }
          }
        }
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [
    isAppLocked,
    zenFocusOpen,
    weeklyReviewModal.open,
    templatesModal.open,
    quickShareDraft,
    pendingDeleteProject,
    pendingCompleteProject,
    emptyTrashConfirmOpen,
    appLockSetupOpen,
    perspectiveModalState.open,
    dependencyModalTaskId,
    batchTagPickerOpen,
    whenModalTaskId,
    deadlineModalTarget,
    repeatModalTaskId,
    moveModalTaskIds,
    tagPickerTaskId,
    areaManagerOpen,
    quickCreateAreaOpen,
    projectModalState.open,
    tagManagerOpen,
    notificationsModalOpen,
    geminiModalOpen,
    authModalOpen,
    settingsModalOpen,
    magicFabOpen,
    projectHeaderMenuOpen,
    projectAreaDropdownOpen,
    viewSortDropdownOpen,
    quickFindOpen,
    expandedTaskId,
    isTimelineFullScreen,
    isTodayTimelineFullScreen,
    isRoadmapFullScreen,
    isAreaRoadmapFullScreen,
    todayViewMode,
    activeProjectViewMode,
    activeAreaViewMode,
    enterTimelineFullScreen,
    selectedTaskIds,
    mobileDrawerOpen,
    activeNav,
    handleSelectNav,
    createNewTodoInCurrentContext,
    handleDuplicateTask,
    handleBatchDuplicateSelected,
    handleDuplicateProject,
    defaultAreaIdForNewProject,
    orderedVisibleTaskIds,
    snapshot.tags,
    snapshot.tasks
  ]);

  const orderedSelectedDragIds = useMemo(
    () => orderTaskIdsByVisualOrder(selectedTaskIds, orderedVisibleTaskIds),
    [selectedTaskIds, orderedVisibleTaskIds]
  );

  const handleSelectTask = useCallback(
    (taskId: string, multiToggle: boolean, shiftRange: boolean = false) => {
      if (shiftRange && orderedVisibleTaskIds.length > 0) {
        const anchor =
          lastSelectedAnchorIdRef.current ??
          selectedTaskIds[selectedTaskIds.length - 1] ??
          taskId;
        const i1 = orderedVisibleTaskIds.indexOf(anchor);
        const i2 = orderedVisibleTaskIds.indexOf(taskId);
        if (i1 >= 0 && i2 >= 0) {
          const slice = orderedVisibleTaskIds.slice(
            Math.min(i1, i2),
            Math.max(i1, i2) + 1
          );
          const next = Array.from(new Set([...selectedTaskIds, ...slice]));
          if (next.length > 1) {
            setIsExplicitMultiSelect(true);
          }
          setSelectedTaskIds(next);
          return;
        }
      }
      lastSelectedAnchorIdRef.current = taskId;
      if (multiToggle) {
        setSelectedTaskIds((prev) => {
          const next = prev.includes(taskId)
            ? prev.filter((id) => id !== taskId)
            : [...prev, taskId];
          setIsExplicitMultiSelect(next.length > 0);
          return next;
        });
      } else {
        setIsExplicitMultiSelect(false);
        setSelectedTaskIds([taskId]);
      }
    },
    [orderedVisibleTaskIds, selectedTaskIds]
  );

  // Drag-and-drop 1 or N tasks onto Sidebar or Strip target
  const handleDropTasksOnSidebarTarget = async (
    rawTaskIds: string[],
    target: SidebarDropTarget
  ) => {
    const resolvedIds =
      rawTaskIds.length === 1 &&
      selectedTaskIds.length > 1 &&
      selectedTaskIds.includes(rawTaskIds[0])
        ? orderedSelectedDragIds
        : orderTaskIdsByVisualOrder(rawTaskIds, orderedVisibleTaskIds);

    const validRecords = resolvedIds
      .map((id) => snapshot.tasks.find((x) => x.id === id))
      .filter((x): x is TaskRecord => x !== undefined && !x.purgedFromTrash);

    if (validRecords.length === 0) return;
    const taskIds = validRecords.map((x) => x.id);

    // Restore from Trash first if dragged out of Trash onto a non-Trash target!
    const isTargetTrash = target.kind === 'smart' && target.view === 'TRASH';
    if (!isTargetTrash) {
      for (const rec of validRecords) {
        if (rec.isDeleted) {
          await localDb.restoreDeletedTask(rec.id);
        }
      }
    }

    if (target.kind === 'area') {
      await localDb.batchMoveOrReorderTasksInContainer(taskIds, {
        areaId: target.areaId,
        projectId: null,
        headingId: null
      });
      setSelectedTaskIds([]);
      if (taskIds.length > 1) showToast(t.dropMovedFeedback(taskIds.length));
      return;
    }

    if (target.kind === 'project') {
      await localDb.batchMoveOrReorderTasksInContainer(taskIds, {
        projectId: target.projectId,
        headingId: null,
        areaId: null
      });
      setSelectedTaskIds([]);
      if (taskIds.length > 1) showToast(t.dropMovedFeedback(taskIds.length));
      return;
    }

    const todayEpoch = startOfDayEpoch();
    const tomorrowEpoch = addDaysLocal(todayEpoch, 1);

    switch (target.view) {
      case 'TRASH':
        await localDb.softDeleteTasks(taskIds);
        setSelectedTaskIds([]);
        return;

      case 'LOGBOOK':
        await localDb.batchUpdateTasks(taskIds, { status: 'COMPLETED' });
        setSelectedTaskIds([]);
        return;

      case 'INBOX':
        await localDb.batchMoveOrReorderTasksInContainer(taskIds, {
          projectId: null,
          headingId: null,
          areaId: null,
          scheduleState: 'INBOX',
          whenDate: null,
          scheduledStartMinutes: null
        });
        break;

      case 'TODAY':
        await localDb.batchUpdateTasks(taskIds, {
          scheduleState: 'TODAY',
          whenDate: todayEpoch
        });
        break;

      case 'UPCOMING': {
        const keepFutureIds: string[] = [];
        const setTomorrowIds: string[] = [];
        for (const rec of validRecords) {
          if (
            rec.whenDate != null &&
            startOfDayEpoch(new Date(rec.whenDate)) > todayEpoch
          ) {
            keepFutureIds.push(rec.id);
          } else {
            setTomorrowIds.push(rec.id);
          }
        }
        if (keepFutureIds.length > 0) {
          await localDb.batchUpdateTasks(keepFutureIds, {
            scheduleState: 'UPCOMING'
          });
        }
        if (setTomorrowIds.length > 0) {
          await localDb.batchUpdateTasks(setTomorrowIds, {
            scheduleState: 'UPCOMING',
            whenDate: tomorrowEpoch
          });
        }
        break;
      }

      case 'ANYTIME':
        await localDb.batchUpdateTasks(taskIds, {
          scheduleState: 'ANYTIME',
          whenDate: null,
          scheduledStartMinutes: null
        });
        break;

      case 'SOMEDAY':
        await localDb.batchUpdateTasks(taskIds, {
          scheduleState: 'SOMEDAY',
          whenDate: null,
          scheduledStartMinutes: null
        });
        break;
    }

    setSelectedTaskIds([]);
    if (taskIds.length > 1) {
      showToast(t.dropMovedFeedback(taskIds.length));
    }
  };

  // Drag-and-drop reorder 1 or N tasks between tasks (including cross-heading, Today Evening, & Upcoming date groups)
  const handleDropReorderTasks = async (
    rawDraggedIds: string[],
    targetId: string,
    listContext: TaskRecord[],
    dropContext?: { evening?: boolean; upcomingDayEpoch?: number }
  ) => {
    const draggedIds =
      rawDraggedIds.length === 1 &&
      selectedTaskIds.length > 1 &&
      selectedTaskIds.includes(rawDraggedIds[0])
        ? orderedSelectedDragIds
        : orderTaskIdsByVisualOrder(rawDraggedIds, orderedVisibleTaskIds);

    const target = snapshot.tasks.find((x) => x.id === targetId);
    if (!target || draggedIds.length === 0) return;
    if (draggedIds.length === 1 && draggedIds[0] === targetId) return;

    const idx = listContext.findIndex((x) => x.id === targetId);
    const prevOrder = idx > 0 ? listContext[idx - 1].sortOrder : null;
    const nextOrder = target.sortOrder;

    const shouldAdoptContainer =
      activeNav.kind === 'project' || activeNav.kind === 'area';

    let nextSchedule: TaskRecord['scheduleState'] | undefined;
    let nextWhen: number | null | undefined;

    if (activeNav.kind === 'smart' && activeNav.view === 'TODAY') {
      if (dropContext?.evening || target.scheduleState === 'THIS_EVENING') {
        nextSchedule = 'THIS_EVENING';
        nextWhen = startOfDayEpoch();
      } else {
        const anyWasEvening = draggedIds.some(
          (id) => tasksByIdMap.get(id)?.scheduleState === 'THIS_EVENING'
        );
        if (anyWasEvening) {
          nextSchedule = 'TODAY';
          nextWhen = startOfDayEpoch();
        }
      }
    } else if (activeNav.kind === 'smart' && activeNav.view === 'UPCOMING') {
      nextSchedule = 'UPCOMING';
      nextWhen = dropContext?.upcomingDayEpoch ?? computeUpcomingTargetDate(target);
    }

    await localDb.batchMoveOrReorderTasksInContainer(draggedIds, {
      ...(shouldAdoptContainer
        ? {
            projectId: target.projectId,
            headingId: target.headingId,
            areaId: target.areaId
          }
        : {}),
      afterSortOrder: prevOrder,
      beforeSortOrder: nextOrder,
      ...(nextSchedule !== undefined ? { scheduleState: nextSchedule } : {}),
      ...(nextWhen !== undefined ? { whenDate: nextWhen } : {})
    });

    if (draggedIds.length > 1) {
      showToast(t.dropMovedFeedback(draggedIds.length));
    }
  };

  const handleTriggerInstallPwa = async () => {
    if (deferredInstallPrompt) {
      await deferredInstallPrompt.prompt();
      const choice = await deferredInstallPrompt.userChoice;
      if (choice.outcome === 'accepted') {
        setIsStandalonePwa(true);
      }
      setDeferredInstallPrompt(null);
    } else {
      showToast(t.installChromeOsHint);
    }
  };

  const renderTaskRow = (
    tk: TaskRecord,
    listContext: TaskRecord[],
    showContextSubtitle: boolean,
    dropContext?: { evening?: boolean; upcomingDayEpoch?: number }
  ) => (
    <TaskItem
      key={tk.id}
      task={tk}
      isExpanded={false}
      isTodayView={activeNav.kind === 'smart' && activeNav.view === 'TODAY'}
      isSelected={selectedTaskIds.includes(tk.id)}
      isMultiSelectMode={isExplicitMultiSelect}
      showContextSubtitle={showContextSubtitle}
      showScheduleBadgeInCollapsed={
        activeNav.kind === 'project' ||
        activeNav.kind === 'area' ||
        activeNav.kind === 'perspective'
      }
      quickFindOpen={quickFindOpen}
      currentDayMarker={endOfToday}
      areasMap={areasMap}
      projectsMap={allNonDeletedProjectsMap}
      tagsMap={tagsMap}
      checklistItems={snapshot.checklistItems}
      attachments={snapshot.attachments}
      openTaskIdSet={openTaskIdSet}
      tasksByIdMap={tasksByIdMap}
      lang={lang}
      geminiApiKey={prefs.geminiApiKey}
      t={t}
      dragTaskIds={orderedSelectedDragIds}
      quickEditRequestNonce={
        inlineQuickEditRequest?.taskId === tk.id ? inlineQuickEditRequest.nonce : 0
      }
      onSelect={(multiToggle, shiftRange) =>
        handleSelectTask(tk.id, multiToggle, Boolean(shiftRange))
      }
      onExpand={() => {
        preferBottomForExpandedTaskRef.current = false;
        setSelectedTaskIds([]);
        setExpandedTaskId(tk.id);
      }}
      onCollapse={() => setExpandedTaskId(null)}
      onUpdateTitleNotes={(nextTitle, nextNotes) =>
        void localDb.upsertTask({ id: tk.id, title: nextTitle, notes: nextNotes })
      }
      onUpdateEffortLevel={(nextEffort) =>
        void localDb.upsertTask({ id: tk.id, effortLevel: nextEffort })
      }
      onRequestEnsureVisible={handleRequestEnsureTaskVisible}
      onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
      onOpenDeadlineModal={(taskId) => setDeadlineModalTarget({ kind: 'task', taskId })}
      onOpenRepeatModal={(taskId) => setRepeatModalTaskId(taskId)}
      onOpenMoveModal={(taskId) => setMoveModalTaskIds([taskId])}
      onOpenTagsModal={(taskId) => setTagPickerTaskId(taskId)}
      onOpenDependenciesModal={(taskId) => setDependencyModalTaskId(taskId)}
      onStartFocusTimer={handleStartFocusTimer}
      onDuplicateTask={(taskId) => void handleDuplicateTask(taskId)}
      onDropReorder={(draggedId, targetId) =>
        void handleDropReorderTasks([draggedId], targetId, listContext, dropContext)
      }
      onDropReorderTasks={(draggedIds, targetId) =>
        void handleDropReorderTasks(draggedIds, targetId, listContext, dropContext)
      }
      onToast={showToast}
    />
  );

  const viewSortModeLabel = (mode: ViewSortMode): string => {
    switch (mode) {
      case 'deadline':
        return t.viewSortDeadline;
      case 'duration':
        return t.viewSortDuration;
      case 'effort':
        return t.viewSortEffort;
      case 'alpha':
        return t.viewSortAlpha;
      default:
        return t.viewSortManual;
    }
  };

  const renderViewSortDropdown = () => (
    <div className="dropdown-anchor" onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        className={`view-sort-pill ${viewSortMode !== 'manual' ? 'active' : ''}`}
        data-testid="view-sort-dropdown-btn"
        title={`${t.viewSortLabel}: ${viewSortModeLabel(viewSortMode)}`}
        onClick={() => setViewSortDropdownOpen((v) => !v)}
      >
        <ArrowUpDown size={13} />
        <span>{viewSortModeLabel(viewSortMode)}</span>
      </button>

      {viewSortDropdownOpen && (
        <>
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 70 }}
            onClick={() => setViewSortDropdownOpen(false)}
          />
          <div className="dropdown-popover" data-testid="view-sort-dropdown-menu">
            {(
              [
                ['manual', t.viewSortManual],
                ['deadline', t.viewSortDeadline],
                ['duration', t.viewSortDuration],
                ['effort', t.viewSortEffort],
                ['alpha', t.viewSortAlpha]
              ] as Array<[ViewSortMode, string]>
            ).map(([mode, label]) => (
              <button
                key={mode}
                type="button"
                className="dropdown-item"
                data-testid={`view-sort-option-${mode}`}
                onClick={() => {
                  updatePrefs({ viewSortMode: mode });
                  setViewSortDropdownOpen(false);
                }}
              >
                <span>{label}</span>
                {viewSortMode === mode && <Check size={14} />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );

  // Determine current view title for MagicPlusFab subtitle
  const currentViewTitle = useMemo(() => {
    if (activeNav.kind === 'smart') {
      switch (activeNav.view) {
        case 'INBOX':
          return t.viewInbox;
        case 'TODAY':
          return t.viewToday;
        case 'UPCOMING':
          return t.viewUpcoming;
        case 'ANYTIME':
          return t.viewAnytime;
        case 'SOMEDAY':
          return t.viewSomeday;
        case 'LOGBOOK':
          return t.viewLogbook;
        case 'TRASH':
          return t.viewTrash;
      }
    }
    if (activeNav.kind === 'area') {
      return areasMap.get(activeNav.areaId)?.title || t.entityArea;
    }
    if (activeNav.kind === 'perspective') {
      return (
        savedPerspectives.find((p) => p.id === activeNav.perspectiveId)?.title ||
        t.perspectivesSectionTitle
      );
    }
    return allNonDeletedProjectsMap.get(activeNav.projectId)?.title || t.entityProject;
  }, [activeNav, areasMap, allNonDeletedProjectsMap, savedPerspectives, t]);

  // Format Upcoming Day Group Header ("d. MMM" in de else "MMM d" + "Today • EEEE" / "Tomorrow • EEEE" / "EEEE")
  const formatUpcomingHeader = (dayEpoch: number) => {
    const d = new Date(dayEpoch);
    const numPart =
      lang === 'de'
        ? new Intl.DateTimeFormat('de', { day: 'numeric', month: 'short' }).format(d)
        : new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(d);
    const weekday = new Intl.DateTimeFormat(lang, { weekday: 'long' }).format(d);
    const diff = daysBetweenToday(dayEpoch);
    const subPart =
      diff === 0
        ? `${t.viewToday} • ${weekday}`
        : diff === 1
          ? `${t.dateTomorrow} • ${weekday}`
          : weekday;
    return { numPart, subPart };
  };

  // Group Upcoming tasks & active Phase End-Date Milestones by target date
  const upcomingGroups = useMemo(() => {
    const todayStart = startOfDayEpoch(new Date(endOfToday));
    const filtered = filterByTag(upcomingTasks);
    const map = new Map<
      number,
      {
        tasks: TaskRecord[];
        phases: Array<{
          heading: HeadingRecord;
          project: ProjectRecord;
          done: number;
          inProgress: number;
          total: number;
        }>;
      }
    >();

    const ensureBucket = (day: number) => {
      let entry = map.get(day);
      if (!entry) {
        entry = { tasks: [], phases: [] };
        map.set(day, entry);
      }
      return entry;
    };

    for (const tk of filtered) {
      const targetDay = computeUpcomingTargetDate(tk);
      ensureBucket(targetDay).tasks.push(tk);
    }

    const activeProjectsById = new Map(
      snapshot.projects
        .filter((p) => !p.isDeleted && p.status === 'ACTIVE')
        .map((p) => [p.id, p])
    );

    for (const hd of snapshot.headings) {
      if (hd.isDeleted || hd.isArchived || hd.endDate == null) continue;
      const proj = activeProjectsById.get(hd.projectId);
      if (!proj) continue;
      if (selectedFilterTagId && !proj.tagIds.includes(selectedFilterTagId)) continue;
      const phaseDay = startOfDayEpoch(new Date(hd.endDate));
      if (phaseDay < todayStart) continue;
      const phaseTasks = snapshot.tasks.filter(
        (tk) =>
          !tk.isDeleted &&
          tk.projectId === proj.id &&
          tk.headingId === hd.id &&
          tk.status !== 'CANCELED'
      );
      const done = phaseTasks.filter((tk) => tk.status === 'COMPLETED').length;
      const inProgress = phaseTasks.filter(
        (tk) => tk.status === 'OPEN' && Boolean(tk.isInProgress)
      ).length;
      ensureBucket(phaseDay).phases.push({
        heading: hd,
        project: proj,
        done,
        inProgress,
        total: phaseTasks.length
      });
    }

    return Array.from(map.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([dayEpoch, bucket]) => ({
        dayEpoch,
        tasks: bucket.tasks,
        phases: bucket.phases.sort((a, b) => a.heading.sortOrder - b.heading.sortOrder)
      }));
  }, [
    upcomingTasks,
    filterByTag,
    endOfToday,
    snapshot.headings,
    snapshot.projects,
    snapshot.tasks,
    selectedFilterTagId
  ]);

  const effectiveSidebarVisible = isWideScreen ? prefs.sidebarVisible : mobileDrawerOpen;
  const isWideProjectCanvas =
    (activeNav.kind === 'project' &&
      (activeProjectViewMode === 'board' || activeProjectViewMode === 'roadmap')) ||
    (activeNav.kind === 'area' && activeAreaViewMode === 'roadmap');
  const canShowInspector =
    isInspectorViewport && !isAnyTimelineFullScreen && !isWideProjectCanvas;
  const effectiveInspectorOpen = canShowInspector && inspectorOpen;
  const isMultiSelectBarVisible =
    !isAnyTimelineFullScreen &&
    selectedTaskIds.length > 0 &&
    !expandedTaskId &&
    (selectedTaskIds.length > 1 || isExplicitMultiSelect);

  const selectedInspectorTask = useMemo(() => {
    if (selectedTaskIds.length !== 1) return null;
    const tk = tasksByIdMap.get(selectedTaskIds[0]);
    return tk && !tk.isDeleted ? tk : null;
  }, [selectedTaskIds, tasksByIdMap]);

  const todayCompletedCount = useMemo(
    () =>
      snapshot.tasks.filter(
        (tk) =>
          !tk.isDeleted &&
          tk.status === 'COMPLETED' &&
          tk.completedAt != null &&
          tk.completedAt >= todayStartMs &&
          tk.completedAt <= endOfToday
      ).length,
    [snapshot.tasks, todayStartMs, endOfToday]
  );

  const visibleViewTasks = useMemo(
    () => filterByTag(currentViewBaseTasks),
    [filterByTag, currentViewBaseTasks]
  );

  const handleSelectTaskFromInspector = useCallback(
    (tk: TaskRecord) => {
      const inCurrentView = currentViewBaseTasks.some((x) => x.id === tk.id);
      if (!inCurrentView) {
        if (
          tk.projectId &&
          normalizeProjectViewMode(allNonDeletedProjectsMap.get(tk.projectId)?.viewMode) ===
            'list'
        ) {
          setActiveNav({ kind: 'project', projectId: tk.projectId });
        } else if (
          (tk.whenDate != null && tk.whenDate <= endOfToday) ||
          (tk.deadline != null && tk.deadline <= endOfToday) ||
          tk.scheduleState === 'TODAY' ||
          tk.scheduleState === 'THIS_EVENING'
        ) {
          setActiveNav({ kind: 'smart', view: 'TODAY' });
        } else if (
          (tk.whenDate != null && tk.whenDate > endOfToday) ||
          (tk.deadline != null && tk.deadline > endOfToday) ||
          tk.scheduleState === 'UPCOMING'
        ) {
          setActiveNav({ kind: 'smart', view: 'UPCOMING' });
        } else if (tk.areaId) {
          setActiveNav({ kind: 'area', areaId: tk.areaId });
        } else if (tk.scheduleState === 'SOMEDAY') {
          setActiveNav({ kind: 'smart', view: 'SOMEDAY' });
        } else if (tk.scheduleState === 'ANYTIME') {
          setActiveNav({ kind: 'smart', view: 'ANYTIME' });
        } else {
          setActiveNav({ kind: 'smart', view: 'INBOX' });
        }
        setSelectedFilterTagId(null);
      }
      lastSelectedAnchorIdRef.current = tk.id;
      setIsExplicitMultiSelect(false);
      setSelectedTaskIds([tk.id]);
    },
    [currentViewBaseTasks, allNonDeletedProjectsMap, endOfToday]
  );

  return (
    <div
      className="app-shell"
      lang={lang}
      onClick={() => {
        if (
          expandedTaskId &&
          !whenModalTaskId &&
          !deadlineModalTarget &&
          !repeatModalTaskId &&
          !moveModalTaskIds &&
          !tagPickerTaskId &&
          !tagManagerOpen &&
          !areaManagerOpen &&
          !quickCreateAreaOpen &&
          !batchTagPickerOpen &&
          !dependencyModalTaskId &&
          !perspectiveModalState.open
        ) {
          setExpandedTaskId(null);
        } else if (selectedTaskIds.length > 0 && !expandedTaskId && !batchTagPickerOpen) {
          setSelectedTaskIds([]);
        }
      }}
    >
      {/* Left Sidebar / Mobile Drawer */}
      <Sidebar
        visible={effectiveSidebarVisible && !isAnyTimelineFullScreen}
        activeNav={activeNav}
        areas={snapshot.areas}
        projects={snapshot.projects}
        tasks={snapshot.tasks}
        selectedTaskIds={selectedTaskIds}
        orderedVisibleTaskIds={orderedVisibleTaskIds}
        counts={sidebarCounts}
        showPerspectives={effectiveShowPerspectives}
        savedPerspectives={savedPerspectives}
        perspectiveCounts={perspectiveCounts}
        stalledProjectsCount={stalledProjects.length}
        canInstallPwa={Boolean(deferredInstallPrompt)}
        isStandalonePwa={isStandalonePwa}
        t={t}
        onSelectNav={(nav) => handleSelectNav(nav)}
        onCloseMobile={() => setMobileDrawerOpen(false)}
        onCreateProjectInArea={(areaId) =>
          setProjectModalState({ open: true, project: null, defaultAreaId: areaId })
        }
        onOpenTemplates={(areaId) =>
          setTemplatesModal({ open: true, tab: 'gallery', areaId })
        }
        onOpenExternalImport={(areaId) =>
          setTemplatesModal({ open: true, tab: 'external', areaId })
        }
        onOpenWeeklyReview={() => setWeeklyReviewModal({ open: true, step: 1 })}
        onOpenManageAreas={() => setAreaManagerOpen(true)}
        onOpenManageTags={() => setTagManagerOpen(true)}
        onCreatePerspective={() =>
          setPerspectiveModalState({ open: true, perspective: null })
        }
        onEditPerspective={(persp) =>
          setPerspectiveModalState({ open: true, perspective: persp })
        }
        onDropTasksOnTarget={(ids, target) => void handleDropTasksOnSidebarTarget(ids, target)}
        onTriggerInstallPwa={() => void handleTriggerInstallPwa()}
      />

      {/* Right Workspace */}
      <main
        ref={workspaceContainerRef}
        className={`workspace ${effectiveInspectorOpen ? 'inspector-split-open' : ''} ${
          isMultiSelectBarVisible ? 'multi-bar-visible' : ''
        }`}
      >
        {/* TopThingsbookBar (Calm Single-Row Bar; hidden when Timeline/Roadmap Full Screen is active) */}
        {!isAnyTimelineFullScreen && (
          <header
            className="workspace-topbar"
            data-topbar-tier={resolveTopBarLayoutTier(workspaceWidthPx)}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="topbar-left">
              <button
                type="button"
                className="icon-btn"
                onClick={() => {
                  if (isWideScreen) {
                    updatePrefs({ sidebarVisible: !prefs.sidebarVisible });
                  } else {
                    setMobileDrawerOpen((v) => !v);
                  }
                }}
                title={
                  effectiveSidebarVisible
                    ? t.topbarTooltipHideSidebar
                    : t.topbarTooltipShowSidebar
                }
              >
                {effectiveSidebarVisible && isWideScreen ? (
                  <PanelLeftClose size={18} />
                ) : (
                  <Menu size={18} />
                )}
              </button>

              {(!isWideScreen || !prefs.sidebarVisible) && (
                <button
                  type="button"
                  className="brand-title-group"
                  title={t.viewToday}
                  onClick={() => handleSelectNav({ kind: 'smart', view: 'TODAY' })}
                >
                  <span className="brand-app-icon">
                    <CheckSquare size={13.5} strokeWidth={2.6} />
                  </span>
                  <span className="brand-name topbar-label-hide-sm">{t.appName}</span>
                </button>
              )}

              <button
                type="button"
                className={`topbar-pill ${quickFindOpen ? 'active' : ''}`}
                title={t.topbarTooltipQuickFind}
                onClick={() => setQuickFindOpen((v) => !v)}
              >
                <Search size={14} />
                <span className="topbar-label-hide-xs">{t.quickFindTitle}</span>
                <kbd className="topbar-label-hide-sm">Ctrl+K</kbd>
              </button>
            </div>

            <div className="topbar-right">
              {/* Voice Capture Icon */}
              <button
                type="button"
                className="icon-btn"
                data-testid="topbar-voice-capture-btn"
                title={t.topbarTooltipVoiceInput}
                onClick={() =>
                  setQuickShareDraft({
                    initialTitle: '',
                    initialNotes: '',
                    autoLaunchVoice: true
                  })
                }
              >
                <Mic size={17} />
              </button>

              {/* Gemini Hub Pill */}
              <button
                type="button"
                className="topbar-pill gemini-pill"
                onClick={() => {
                  setGeminiModalTab('braindump');
                  setGeminiModalOpen(true);
                }}
                title="Gemini"
              >
                <Sparkles size={14} />
                <span className="topbar-label-hide-md">Gemini</span>
              </button>

              {/* Cloud Sync Icon */}
              <button
                type="button"
                className="icon-btn sync-icon-btn"
                title={t.topbarTooltipCloudSync}
                onClick={() => setAuthModalOpen(true)}
              >
                <Cloud
                  size={17}
                  style={{
                    color:
                      syncState.status === 'SYNCED'
                        ? 'var(--logbook-green)'
                        : syncState.status === 'SYNCING'
                          ? 'var(--things-blue)'
                          : undefined
                  }}
                />
                <span
                  className={`sync-status-dot ${
                    syncState.status === 'SYNCED'
                      ? 'synced'
                      : syncState.status === 'SYNCING'
                        ? 'syncing'
                        : ''
                  }`}
                />
              </button>

              {availableUpdate?.hasUpdate && (
                <button
                  type="button"
                  className="chip-btn active"
                  data-testid="topbar-update-available-btn"
                  title={`${t.settingsApplyUpdateBtn} (v${availableUpdate.latestVersion})`}
                  onClick={() => void applyPendingSwUpdate(availableUpdate)}
                >
                  <span>v{availableUpdate.latestVersion}</span>
                </button>
              )}

              {canShowInspector && (
                <button
                  type="button"
                  className={`icon-btn ${inspectorOpen ? 'active' : ''}`}
                  data-testid="topbar-inspector-toggle"
                  title={t.inspectorToggleTooltip}
                  aria-label={t.inspectorToggleTooltip}
                  onClick={toggleInspectorOpen}
                >
                  <PanelRight size={17.5} />
                </button>
              )}

              {/* Pinned Far-Right Settings Icon */}
              <button
                type="button"
                className="icon-btn"
                title={t.topbarTooltipSettings}
                onClick={() => setSettingsModalOpen(true)}
              >
                <Settings size={17.5} />
              </button>
            </div>
          </header>
        )}

        {/* Horizontal SmartViewNavigationStrip when >= 700px and Sidebar Collapsed */}
        {!isAnyTimelineFullScreen && isWideScreen && !prefs.sidebarVisible && (
          <SmartViewNavigationStrip
            activeNav={activeNav}
            areas={snapshot.areas}
            projects={snapshot.projects}
            tasks={snapshot.tasks}
            selectedTaskIds={selectedTaskIds}
            orderedVisibleTaskIds={orderedVisibleTaskIds}
            counts={sidebarCounts}
            t={t}
            isSoftKeyboardOpen={isSoftKeyboardOpen}
            onSelectNav={(nav) => handleSelectNav(nav)}
            onDropTasksOnTarget={(ids, target) =>
              void handleDropTasksOnSidebarTarget(ids, target)
            }
            onCreateProject={() =>
              setProjectModalState({
                open: true,
                project: null,
                defaultAreaId: defaultAreaIdForNewProject
              })
            }
            onOpenTemplates={() =>
              setTemplatesModal({
                open: true,
                tab: 'gallery',
                areaId: defaultAreaIdForNewProject
              })
            }
            onOpenWeeklyReview={() => setWeeklyReviewModal({ open: true, step: 1 })}
            onOpenManageAreas={() => setAreaManagerOpen(true)}
            onOpenManageTags={() => setTagManagerOpen(true)}
          />
        )}

        {/* Inline Quick Find & Unified Command Palette directly under Top Bar */}
        {!isAnyTimelineFullScreen && quickFindOpen && (
          <InlineQuickFind
            areas={snapshot.areas}
            projects={snapshot.projects}
            tasks={snapshot.tasks}
            checklistItems={snapshot.checklistItems}
            tags={snapshot.tags}
            activeProject={activeProjectRecord}
            activeArea={activeAreaRecord}
            selectedTask={
              expandedTaskId
                ? (tasksByIdMap.get(expandedTaskId) ?? null)
                : selectedInspectorTask
            }
            focusTimer={focusTimer}
            lang={lang}
            t={t}
            onNavigate={(nav, highlightId) => handleSelectNav(nav, highlightId)}
            onCreateSmartTask={(parsed) => void handleCreateSmartTaskFromPalette(parsed)}
            onToggleSelectedTaskInProgress={() => {
              const targetId = expandedTaskId ?? selectedInspectorTask?.id ?? null;
              if (targetId) {
                void localDb.toggleTaskInProgress(targetId);
              }
            }}
            onPlanMyDay={() => {
              setGeminiModalTab('planday');
              setGeminiModalOpen(true);
            }}
            onOpenAiArchitect={() => {
              setGeminiModalTab('braindump');
              setGeminiModalOpen(true);
            }}
            onOpenWeeklyReview={() => setWeeklyReviewModal({ open: true, step: 1 })}
            onCompleteActiveProject={
              activeProjectRecord
                ? () => void requestCompleteProject(activeProjectRecord)
                : undefined
            }
            onSwitchProjectViewMode={
              activeProjectRecord
                ? (mode) => {
                    if (mode !== 'roadmap') setIsTimelineFullScreen(false);
                    void localDb.setProjectViewMode(activeProjectRecord.id, mode);
                  }
                : undefined
            }
            onSwitchAreaViewMode={
              activeAreaRecord
                ? (mode) => {
                    if (mode !== 'roadmap') setIsTimelineFullScreen(false);
                    const nextModes: Record<string, 'list' | 'roadmap'> = {
                      ...(prefs.areaViewModes ?? {}),
                      [activeAreaRecord.id]: mode
                    };
                    const updated = localDb.updatePreferences({ areaViewModes: nextModes });
                    setPrefs(updated);
                  }
                : undefined
            }
            onOpenZenFocus={() => setZenFocusOpen(true)}
            onOpenTemplates={() =>
              setTemplatesModal({
                open: true,
                tab: 'gallery',
                areaId: defaultAreaIdForNewProject
              })
            }
            onCycleTheme={handleCycleThemeMode}
            onOpenSettings={() => setSettingsModalOpen(true)}
            onClose={() => setQuickFindOpen(false)}
          />
        )}

        {/* Main Scrollable View Content + Wide-Screen Soft Bento Inspector Split */}
        <div className="workspace-body-split">
        <div
          ref={workspaceScrollRef}
          className={`workspace-scroll ${
            isAnyTimelineFullScreen ? 'timeline-fullscreen-scroll' : ''
          }`}
          style={
            isSoftKeyboardOpen && !isAnyTimelineFullScreen
              ? { paddingBottom: `${softKeyboardInsetPx + 28}px` }
              : undefined
          }
        >
          <div
            className={`workspace-container ${
              isWideProjectCanvas ? 'wide-project-canvas' : ''
            } ${effectiveInspectorOpen ? 'inspector-open' : ''} ${
              isAnyTimelineFullScreen ? 'timeline-fullscreen-container' : ''
            }`}
          >
            {/* Welcome Setup Banner (only when sample tasks exist & not dismissed & not signed in) */}
            {!isAnyTimelineFullScreen &&
              snapshot.hasSampleTasks &&
              !prefs.setupBannerDismissed &&
              !syncState.userId && (
                <div className="setup-banner" onClick={(e) => e.stopPropagation()}>
                  <div className="setup-banner-text">
                    <h4>{t.setupBannerTitle}</h4>
                    <p>{t.setupBannerDesc}</p>
                  </div>
                  <div className="setup-banner-actions">
                    <button
                      type="button"
                      className="btn-primary"
                      onClick={() => setAuthModalOpen(true)}
                    >
                      <Cloud size={14} />
                      <span>{t.setupBannerConnectBtn}</span>
                    </button>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() =>
                        void localDb.purgeSampleDataLocally(
                          false,
                          Boolean(syncState.userId && !prefs.localOnlyDbMode)
                        )
                      }
                    >
                      <Trash2 size={14} />
                      <span>{t.setupBannerDeleteSamplesBtn}</span>
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      title={t.actionCancel}
                      onClick={() => updatePrefs({ setupBannerDismissed: true })}
                    >
                      <X size={15} />
                    </button>
                  </div>
                </div>
              )}

            {/* Built-in 25-Minute Focus / Pomodoro Timer Banner (Android v1.7.0; hidden in pure Timeline Full Screen) */}
            {!isAnyTimelineFullScreen && focusTimer && (
              <FocusTimerBanner
                timer={focusTimer}
                t={t}
                onTogglePause={() =>
                  setFocusTimer((prev) =>
                    prev ? { ...prev, isRunning: !prev.isRunning } : null
                  )
                }
                onAddFiveMinutes={() =>
                  setFocusTimer((prev) =>
                    prev
                      ? {
                          ...prev,
                          totalSeconds: prev.totalSeconds + 5 * 60,
                          remainingSeconds: prev.remainingSeconds + 5 * 60
                        }
                      : null
                  )
                }
                onCompleteTask={handleCompleteFocusTimerTask}
                onStop={handleStopFocusTimer}
                onOpenZen={() => setZenFocusOpen(true)}
                onSelectTask={(tid) => {
                  setSelectedTaskIds([]);
                  setExpandedTaskId(tid);
                }}
              />
            )}

            {/* ==================== SMART VIEWS ==================== */}
            {activeNav.kind === 'smart' && (
              <>
                {isTodayTimelineFullScreen ? (
                  <div
                    className="timeline-fullscreen-bar"
                    data-testid="today-timeline-fullscreen-bar"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="timeline-fullscreen-bar-left">
                      <Sun size={18} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
                      <span className="timeline-fullscreen-title">{t.viewToday}</span>
                      {todayCapacity.plannedMinutes > 0 && (
                        <span
                          className="today-capacity-pill"
                          data-testid="today-capacity-pill"
                        >
                          {t.todayCapacitySummary(
                            formatStandaloneDuration(todayCapacity.plannedMinutes),
                            formatStandaloneDuration(todayCapacity.freeMinutes)
                          )}
                        </span>
                      )}
                    </div>

                    <div className="timeline-fullscreen-bar-right">
                      <button
                        type="button"
                        className="chip-btn active"
                        data-testid="today-timeline-fullscreen-btn"
                        title={t.timelineExitFullscreen}
                        onClick={() => setIsTimelineFullScreen(false)}
                      >
                        <Minimize2 size={13} />
                        <span>{t.timelineExitFullscreen}</span>
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="view-header" onClick={(e) => e.stopPropagation()}>
                    <div className="view-title-row smart-view-title-row">
                      <div className="view-title-left smart-view-title-left">
                        {activeNav.view === 'INBOX' && (
                          <Inbox
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'TODAY' && (
                          <Sun
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'UPCOMING' && (
                          <CalendarDays
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'ANYTIME' && (
                          <Compass
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'SOMEDAY' && (
                          <Cloud
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'LOGBOOK' && (
                          <History
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        {activeNav.view === 'TRASH' && (
                          <Trash2
                            size={26}
                            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                          />
                        )}
                        <h1 className="view-title smart-view-title">{currentViewTitle}</h1>

                        {activeNav.view === 'TODAY' && todayCapacity.plannedMinutes > 0 && (
                          <span
                            className="today-capacity-pill"
                            data-testid="today-capacity-pill"
                          >
                            {t.todayCapacitySummary(
                              formatStandaloneDuration(todayCapacity.plannedMinutes),
                              formatStandaloneDuration(todayCapacity.freeMinutes)
                            )}
                          </span>
                        )}

                        {activeNav.view === 'LOGBOOK' && logbookFocused7dMinutes > 0 && (
                          <span
                            className="today-capacity-pill"
                            data-testid="logbook-focused-7d-pill"
                          >
                            {t.v32LogbookFocused7d(
                              formatStandaloneDuration(logbookFocused7dMinutes)
                            )}
                          </span>
                        )}
                      </div>

                      <div className="smart-view-header-controls">
                        {activeNav.view !== 'LOGBOOK' &&
                          activeNav.view !== 'TRASH' &&
                          renderViewSortDropdown()}

                        {activeNav.view === 'TODAY' && (
                          <>
                            <button
                              type="button"
                              className="chip-btn v31-plan-my-day-pill"
                              data-testid="today-plan-my-day-btn"
                              title={t.v31GeminiPlanDayDesc}
                              onClick={() => {
                                setGeminiModalTab('planday');
                                setGeminiModalOpen(true);
                              }}
                            >
                              <Sparkles
                                size={13}
                                style={{ color: 'var(--gemini-violet)', flexShrink: 0 }}
                              />
                              <span>{t.v31GeminiPlanDayTab}</span>
                            </button>

                            <div
                              className="v2-segmented-toggle"
                              data-testid="today-mode-toggle"
                            >
                              <button
                                type="button"
                                className={`v2-segmented-btn ${
                                  todayViewMode === 'list' ? 'active' : ''
                                }`}
                                data-testid="today-mode-list-btn"
                                title={t.todayModeList}
                                onClick={() => {
                                  setIsTimelineFullScreen(false);
                                  setTodayViewMode('list');
                                }}
                              >
                                <List size={13.5} />
                                <span>{t.todayModeList}</span>
                              </button>
                              <button
                                type="button"
                                className={`v2-segmented-btn ${
                                  todayViewMode === 'timeline' ? 'active' : ''
                                }`}
                                data-testid="today-mode-timeline-btn"
                                title={t.todayModeTimeline}
                                onClick={() => setTodayViewMode('timeline')}
                              >
                                <CalendarDays size={13.5} />
                                <span>{t.todayModeTimeline}</span>
                              </button>
                            </div>

                            {todayViewMode === 'timeline' && (
                              <button
                                type="button"
                                className="chip-btn"
                                data-testid="today-timeline-fullscreen-btn"
                                title={t.timelineFullscreen}
                                onClick={enterTimelineFullScreen}
                              >
                                <Maximize2 size={13} />
                                <span>{t.timelineFullscreen}</span>
                              </button>
                            )}
                          </>
                        )}

                        {activeNav.view === 'TRASH' &&
                          deletedProjects.length + deletedTasks.length > 0 && (
                            <button
                              type="button"
                              className="chip-btn urgent"
                              data-testid="empty-trash-btn"
                              title={t.trashEmptyBtn}
                              onClick={() => setEmptyTrashConfirmOpen(true)}
                            >
                              <Trash2 size={13} />
                              <span>{t.trashEmptyBtn}</span>
                            </button>
                          )}
                      </div>
                    </div>
                  </div>
                )}

                {/* View-Scoped Tag Filter Strip (Auto-hidden when empty or in Full Screen) */}
                {!isTodayTimelineFullScreen && usedTagsInCurrentView.length > 0 && (
                  <div className="tag-filter-bar" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className={`tag-pill ${selectedFilterTagId === null ? 'active' : ''}`}
                      title={t.tagsFilterAll}
                      onClick={() => setSelectedFilterTagId(null)}
                    >
                      {t.tagsFilterAll}
                    </button>
                    {usedTagsInCurrentView.map((tg) => (
                      <button
                        key={tg.id}
                        type="button"
                        className={`tag-pill ${selectedFilterTagId === tg.id ? 'active' : ''}`}
                        title={`${t.entityTag}: ${tg.title}`}
                        onClick={() =>
                          setSelectedFilterTagId((cur) => (cur === tg.id ? null : tg.id))
                        }
                      >
                        <LayoutGrid size={11} />
                        <span>{tg.title}</span>
                      </button>
                    ))}
                  </div>
                )}

                {/* LOGBOOK AI Reflection Card */}
                {activeNav.view === 'LOGBOOK' && (
                  <div className="reflection-card" onClick={(e) => e.stopPropagation()}>
                    <div className="reflection-header">
                      <div className="reflection-title">
                        <Sparkles size={16} />
                        <span>{t.geminiReflectionTitle}</span>
                      </div>
                      <button
                        type="button"
                        className="btn-primary"
                        style={{ background: 'var(--gemini-violet)', padding: '6px 12px' }}
                        disabled={loadingReflection}
                        onClick={async () => {
                          setLoadingReflection(true);
                          setReflectionError(null);
                          try {
                            const titles = logbookTasks
                              .filter((x) => x.status === 'COMPLETED')
                              .map((x) => x.title);
                            const res = await generateLogbookReflection(
                              titles,
                              lang,
                              prefs.geminiApiKey
                            );
                            setReflection(res);
                          } catch (err) {
                            setReflectionError(
                              err instanceof Error ? err.message : String(err)
                            );
                          } finally {
                            setLoadingReflection(false);
                          }
                        }}
                      >
                        <Sparkles size={13.5} />
                        <span>
                          {loadingReflection
                            ? t.geminiGenerating
                            : t.geminiReflectionAction}
                        </span>
                      </button>
                    </div>

                    {reflectionError && (
                      <p
                        style={{
                          margin: '8px 0 0',
                          fontSize: 12.6,
                          color: 'var(--deadline-red)'
                        }}
                      >
                        {reflectionError}
                      </p>
                    )}

                    {reflection && (
                      <div style={{ marginTop: 10 }}>
                        <p style={{ margin: '0 0 10px', fontSize: 13.6, lineHeight: 1.5 }}>
                          {reflection.summaryParagraph}
                        </p>
                        {reflection.suggestedFocusTasks.length > 0 && (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            {reflection.suggestedFocusTasks.map((sug, i) => (
                              <div
                                key={i}
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'space-between',
                                  gap: 8,
                                  padding: '6px 10px',
                                  borderRadius: 'var(--radius-xs)',
                                  background: 'var(--bg-card)'
                                }}
                              >
                                <span style={{ fontSize: 13 }}>{sug}</span>
                                <button
                                  type="button"
                                  className="chip-btn active"
                                  onClick={async () => {
                                    await localDb.upsertTask({
                                      title: sug,
                                      scheduleState: 'TODAY',
                                      whenDate: startOfDayEpoch()
                                    });
                                    showToast(`★ ${t.viewToday}: ${sug}`);
                                  }}
                                >
                                  + {t.viewToday}
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* TODAY View (Daytime + Optional Project/Area Grouping + 🌙 This Evening | 🗓️ Timeline) */}
                {activeNav.view === 'TODAY' && (() => {
                  const dayFiltered = sortViewList(filterByTag(todayDaytimeTasks));
                  const eveFiltered = sortViewList(filterByTag(todayEveningTasks));

                  if (todayViewMode === 'timeline') {
                    return (
                      <div
                        className={
                          isTodayTimelineFullScreen
                            ? 'today-timeline-fullscreen-body'
                            : undefined
                        }
                      >
                        <TodayTimelineView
                          todayTasks={[...dayFiltered, ...eveFiltered]}
                          calendarEvents={activeTodayCalendarEvents}
                          areasMap={areasMap}
                          projectsMap={allNonDeletedProjectsMap}
                          t={t}
                          isFullScreen={isTodayTimelineFullScreen}
                          onExpandTask={(taskId) => {
                            if (isTodayTimelineFullScreen) return;
                            setSelectedTaskIds([]);
                            setExpandedTaskId(taskId);
                          }}
                          onStartFocusTimer={handleStartFocusTimer}
                        />
                      </div>
                    );
                  }

                  if (dayFiltered.length === 0 && eveFiltered.length === 0) {
                    return <div className="empty-state">{t.emptyToday}</div>;
                  }

                  const renderGroupedDaytime = () => {
                    if (!prefs.groupTodayByProjectOrArea) {
                      return (
                        <div className="group-section">
                          <div className="task-list">
                            {dayFiltered.map((tk) => renderTaskRow(tk, dayFiltered, true))}
                          </div>
                        </div>
                      );
                    }

                    const renderedIds = new Set<string>();
                    const ungrouped = dayFiltered.filter(
                      (tk) => tk.projectId == null && tk.areaId == null
                    );
                    ungrouped.forEach((tk) => renderedIds.add(tk.id));

                    const projectSections = activeProjects
                      .map((proj) => {
                        const tasks = dayFiltered.filter((tk) => tk.projectId === proj.id);
                        tasks.forEach((tk) => renderedIds.add(tk.id));
                        return { proj, tasks };
                      })
                      .filter((sec) => sec.tasks.length > 0);

                    const areaSections = activeAreas
                      .map((area) => {
                        const tasks = dayFiltered.filter(
                          (tk) => tk.projectId == null && tk.areaId === area.id
                        );
                        tasks.forEach((tk) => renderedIds.add(tk.id));
                        return { area, tasks };
                      })
                      .filter((sec) => sec.tasks.length > 0);

                    const remaining = dayFiltered.filter((tk) => !renderedIds.has(tk.id));

                    return (
                      <>
                        {ungrouped.length > 0 && (
                          <div className="group-section">
                            <div className="task-list">
                              {ungrouped.map((tk) => renderTaskRow(tk, ungrouped, true))}
                            </div>
                          </div>
                        )}

                        {projectSections.map(({ proj, tasks }) => (
                          <div key={`today-proj-${proj.id}`} className="group-section">
                            <div className="group-header">
                              <button
                                type="button"
                                className="today-context-group-header"
                                data-testid={`today-group-project-${proj.id}`}
                                onClick={() =>
                                  handleSelectNav({ kind: 'project', projectId: proj.id })
                                }
                              >
                                <Folder size={14.5} style={{ color: 'var(--things-blue)' }} />
                                <span>{proj.title}</span>
                              </button>
                            </div>
                            <div className="task-list">
                              {tasks.map((tk) => renderTaskRow(tk, tasks, false))}
                            </div>
                          </div>
                        ))}

                        {areaSections.map(({ area, tasks }) => (
                          <div key={`today-area-${area.id}`} className="group-section">
                            <div className="group-header">
                              <button
                                type="button"
                                className="today-context-group-header"
                                data-testid={`today-group-area-${area.id}`}
                                onClick={() =>
                                  handleSelectNav({ kind: 'area', areaId: area.id })
                                }
                              >
                                <Folder size={14.5} style={{ color: 'var(--things-blue)' }} />
                                <span>{area.title}</span>
                              </button>
                            </div>
                            <div className="task-list">
                              {tasks.map((tk) => renderTaskRow(tk, tasks, false))}
                            </div>
                          </div>
                        ))}

                        {remaining.length > 0 && (
                          <div className="group-section">
                            <div className="task-list">
                              {remaining.map((tk) => renderTaskRow(tk, remaining, true))}
                            </div>
                          </div>
                        )}
                      </>
                    );
                  };

                  return (
                    <>
                      {prefs.calendarEventsEnabled && activeTodayCalendarEvents.length > 0 && (
                        <CalendarEventsBlock events={activeTodayCalendarEvents} t={t} />
                      )}
                      {dayFiltered.length > 0 && renderGroupedDaytime()}
                      {eveFiltered.length > 0 && (
                        <div className="group-section" data-magic-evening="true">
                          <div
                            className="group-header"
                            onDragOver={(e) => {
                              if (hasDraggedTaskMime(e.dataTransfer)) {
                                e.preventDefault();
                                e.dataTransfer.dropEffect = 'move';
                              }
                            }}
                            onDrop={async (e) => {
                              e.preventDefault();
                              const ids = extractDraggedTaskIds(
                                e.dataTransfer,
                                selectedTaskIds,
                                orderedVisibleTaskIds
                              );
                              if (ids.length === 0) return;
                              await localDb.batchMoveOrReorderTasksInContainer(ids, {
                                scheduleState: 'THIS_EVENING',
                                whenDate: startOfDayEpoch()
                              });
                              if (ids.length > 1) {
                                showToast(t.dropMovedFeedback(ids.length));
                              }
                            }}
                          >
                            <div className="group-header-title evening">
                              <Moon size={15} />
                              <span>{t.viewThisEvening}</span>
                            </div>
                          </div>
                          <div className="task-list">
                            {eveFiltered.map((tk) =>
                              renderTaskRow(tk, eveFiltered, true, { evening: true })
                            )}
                          </div>
                        </div>
                      )}
                    </>
                  );
                })()}

                {/* UPCOMING View (Day-by-Day Groups — v1.6.22+: only future dates with tasks display non-past calendar events) */}
                {activeNav.view === 'UPCOMING' && (
                  <>
                    {upcomingGroups.length === 0 ? (
                      <div className="empty-state">{t.emptyView}</div>
                    ) : (
                      upcomingGroups.map(({ dayEpoch, tasks: groupTasks, phases: groupPhases }) => {
                        const sortedGroupTasks = sortViewList(groupTasks);
                        const hdr = formatUpcomingHeader(dayEpoch);
                        const targetEpochDay = localEpochDayFromMs(dayEpoch);
                        const todayEpochDay = localEpochDayFromMs(endOfToday);
                        const dayEvents = prefs.calendarEventsEnabled
                          ? filterActiveOrUpcomingCalendarEvents(
                              upcomingCalendarEventsByEpochDay.get(targetEpochDay) ?? [],
                              targetEpochDay,
                              todayEpochDay,
                              calendarNowMs,
                              true
                            )
                          : [];
                        const dayWorkload =
                          upcomingWorkloadByEpochDay.get(targetEpochDay) ??
                          buildDayWorkloadSummary(
                            targetEpochDay,
                            dayEpoch,
                            addDaysLocal(dayEpoch, 1),
                            sortedGroupTasks,
                            dayEvents,
                            workdayBounds.startMinutes,
                            workdayBounds.endMinutes
                          );
                        return (
                          <div
                            key={dayEpoch}
                            className="group-section"
                            data-magic-when-date={dayEpoch}
                          >
                            <div
                              className="upcoming-day-header"
                              onDragOver={(e) => {
                                if (hasDraggedTaskMime(e.dataTransfer)) {
                                  e.preventDefault();
                                  e.dataTransfer.dropEffect = 'move';
                                }
                              }}
                              onDrop={async (e) => {
                                e.preventDefault();
                                const ids = extractDraggedTaskIds(
                                  e.dataTransfer,
                                  selectedTaskIds,
                                  orderedVisibleTaskIds
                                );
                                if (ids.length === 0) return;
                                await localDb.batchMoveOrReorderTasksInContainer(ids, {
                                  scheduleState: 'UPCOMING',
                                  whenDate: dayEpoch
                                });
                                if (ids.length > 1) {
                                  showToast(t.dropMovedFeedback(ids.length));
                                }
                              }}
                            >
                              <span className="upcoming-day-num">{hdr.numPart}</span>
                              <span className="upcoming-day-sub">{hdr.subPart}</span>
                              {dayWorkload.plannedMinutes > 0 && (
                                <span
                                  className={`upcoming-day-workload-pill ${
                                    dayWorkload.isOverloaded ? 'overloaded' : ''
                                  }`}
                                  data-testid={`upcoming-day-workload-pill-${targetEpochDay}`}
                                >
                                  {dayWorkload.isOverloaded
                                    ? t.v31WorkloadOverloadedShort(
                                        formatStandaloneDuration(
                                          dayWorkload.overloadedByMinutes
                                        )
                                      )
                                    : t.todayCapacitySummary(
                                        formatStandaloneDuration(dayWorkload.plannedMinutes),
                                        formatStandaloneDuration(dayWorkload.freeMinutes)
                                      )}
                                </span>
                              )}
                            </div>
                            {(sortedGroupTasks.length > 0 || groupPhases.length > 0) &&
                              dayEvents.length > 0 && (
                                <CalendarEventsBlock events={dayEvents} t={t} compactTop />
                              )}
                            {groupPhases.map((ph) => (
                              <button
                                key={`phase-ms-${ph.heading.id}`}
                                type="button"
                                className="phase-milestone-card"
                                onClick={() =>
                                  handleSelectNav({
                                    kind: 'project',
                                    projectId: ph.project.id
                                  })
                                }
                              >
                                <div className="phase-milestone-left">
                                  <Milestone
                                    size={15.5}
                                    style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                                  />
                                  <div className="phase-milestone-texts">
                                    <span className="phase-milestone-title">
                                      {ph.heading.title || t.entityHeading}
                                    </span>
                                    <span className="phase-milestone-sub">
                                      {t.phaseMilestoneBadge} · {ph.project.title}
                                    </span>
                                  </div>
                                </div>
                                <ProjectProgressRing
                                  completed={ph.done}
                                  inProgress={ph.inProgress}
                                  total={ph.total}
                                  size={16}
                                  showCount
                                />
                              </button>
                            ))}
                            {sortedGroupTasks.length > 0 && (
                              <div className="task-list">
                                {sortedGroupTasks.map((tk) =>
                                  renderTaskRow(tk, sortedGroupTasks, true, {
                                    upcomingDayEpoch: dayEpoch
                                  })
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })
                    )}
                  </>
                )}

                {/* TRASH View (Android v1.7.0) */}
                {activeNav.view === 'TRASH' && (
                  <TrashViewList
                    deletedProjects={deletedProjects}
                    deletedTasks={deletedTasks}
                    t={t}
                    onRestoreProject={(id) => void localDb.restoreDeletedProject(id)}
                    onDeleteProjectForever={(id) => void localDb.permanentlyDeleteProject(id)}
                    onRestoreTask={(id) => void localDb.restoreDeletedTask(id)}
                    onDeleteTaskForever={(id) => void localDb.permanentlyDeleteTask(id)}
                  />
                )}

                {/* INBOX / ANYTIME / SOMEDAY / LOGBOOK */}
                {activeNav.view !== 'TODAY' &&
                  activeNav.view !== 'UPCOMING' &&
                  activeNav.view !== 'TRASH' && (() => {
                    const rawFiltered = filterByTag(currentViewBaseTasks);
                    const list =
                      activeNav.view === 'LOGBOOK'
                        ? rawFiltered
                        : sortViewList(rawFiltered);
                    if (list.length === 0) {
                      return (
                        <div className="empty-state">
                          {activeNav.view === 'INBOX' ? t.emptyInbox : t.emptyView}
                        </div>
                      );
                    }
                    return (
                      <div className="task-list">
                        {list.map((tk) =>
                          renderTaskRow(tk, list, activeNav.view !== 'INBOX')
                        )}
                      </div>
                    );
                  })()}
              </>
            )}

            {/* ==================== AREA DETAIL VIEW ==================== */}
            {activeNav.kind === 'area' && (() => {
              const area = areasMap.get(activeNav.areaId);
              if (!area) {
                return <div className="empty-state">{t.emptyView}</div>;
              }
              const areaIdx = activeAreas.findIndex((a) => a.id === area.id);
              const areaProjects = activeProjects.filter((p) => p.areaId === area.id);
              const areaDirectTasks = sortViewList(filterByTag(currentViewBaseTasks));
              const currentAreaMode = normalizeAreaViewMode(prefs.areaViewModes?.[area.id]);

              const setAreaMode = (nextMode: 'list' | 'roadmap') => {
                if (nextMode !== 'roadmap') {
                  setIsTimelineFullScreen(false);
                }
                const nextModes: Record<string, 'list' | 'roadmap'> = {
                  ...(prefs.areaViewModes ?? {}),
                  [area.id]: nextMode
                };
                const updated = localDb.updatePreferences({ areaViewModes: nextModes });
                setPrefs(updated);
              };

              return (
                <>
                  {!isAreaRoadmapFullScreen && (
                    <div className="view-header" onClick={(e) => e.stopPropagation()}>
                      <div className="view-title-row">
                        <div className="view-title-left">
                          <Folder size={26} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
                          {editingAreaHeader ? (
                            <input
                              type="text"
                              className="view-title-input"
                              autoFocus
                              value={editingAreaTitle}
                              onChange={(e) => setEditingAreaTitle(e.target.value)}
                              onBlur={async () => {
                                if (editingAreaTitle.trim()) {
                                  await localDb.upsertArea({
                                    id: area.id,
                                    title: editingAreaTitle.trim()
                                  });
                                }
                                setEditingAreaHeader(false);
                              }}
                              onKeyDown={async (e) => {
                                if (e.key === 'Enter') {
                                  if (editingAreaTitle.trim()) {
                                    await localDb.upsertArea({
                                      id: area.id,
                                      title: editingAreaTitle.trim()
                                    });
                                  }
                                  setEditingAreaHeader(false);
                                } else if (e.key === 'Escape') {
                                  setEditingAreaHeader(false);
                                }
                              }}
                            />
                          ) : (
                            <h1 className="view-title area-view-title" title={area.title}>
                              {area.title}
                            </h1>
                          )}
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                          <div
                            className="v2-segmented-toggle"
                            data-testid="area-mode-toggle"
                          >
                            <button
                              type="button"
                              className={`v2-segmented-btn ${
                                currentAreaMode === 'list' ? 'active' : ''
                              }`}
                              data-testid="area-mode-list-btn"
                              onClick={() => setAreaMode('list')}
                            >
                              <List size={13.5} />
                              <span>{t.projectViewList}</span>
                            </button>
                            <button
                              type="button"
                              className={`v2-segmented-btn ${
                                currentAreaMode === 'roadmap' ? 'active' : ''
                              }`}
                              data-testid="area-mode-roadmap-btn"
                              onClick={() => setAreaMode('roadmap')}
                            >
                              <CalendarDays size={13.5} />
                              <span>{t.v31AreaPortfolioTimeline}</span>
                            </button>
                          </div>

                          {currentAreaMode === 'list' && renderViewSortDropdown()}
                          <button
                            type="button"
                            className="icon-btn"
                            title={t.actionMove}
                            aria-label={t.actionMove}
                            disabled={areaIdx <= 0}
                            onClick={() => localDb.moveAreaOrder(area.id, 'up')}
                          >
                            <ArrowUp size={16} />
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            title={t.actionMove}
                            aria-label={t.actionMove}
                            disabled={areaIdx < 0 || areaIdx >= activeAreas.length - 1}
                            onClick={() => localDb.moveAreaOrder(area.id, 'down')}
                          >
                            <ArrowDown size={16} />
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            title={t.actionRename}
                            aria-label={t.actionRename}
                            onClick={() => {
                              setEditingAreaTitle(area.title);
                              setEditingAreaHeader(true);
                            }}
                          >
                            <Edit3 size={16} />
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            title={t.actionDelete}
                            aria-label={t.actionDelete}
                            style={{ color: 'var(--deadline-red)' }}
                            onClick={async () => {
                              await localDb.deleteArea(area.id);
                              handleSelectNav({ kind: 'smart', view: 'TODAY' });
                            }}
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </div>

                      <div className="project-action-pills">
                        <button
                          type="button"
                          className="chip-btn"
                          data-testid="area-new-project-btn"
                          onClick={() =>
                            setProjectModalState({
                              open: true,
                              project: null,
                              defaultAreaId: area.id
                            })
                          }
                        >
                          <Plus size={12.5} />
                          <span>{t.entityNewProject}</span>
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          data-testid="area-external-import-btn"
                          style={{ whiteSpace: 'normal', height: 'auto' }}
                          title={t.importAsanaPlannerLabel}
                          onClick={() =>
                            setTemplatesModal({
                              open: true,
                              tab: 'external',
                              areaId: area.id
                            })
                          }
                        >
                          <ArrowLeftRight size={12.5} style={{ flexShrink: 0 }} />
                          <span>{t.externalImportExportTab}</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {/* View Tag Strip */}
                  {!isAreaRoadmapFullScreen &&
                    currentAreaMode === 'list' &&
                    usedTagsInCurrentView.length > 0 && (
                      <div className="tag-filter-bar" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          className={`tag-pill ${selectedFilterTagId === null ? 'active' : ''}`}
                          onClick={() => setSelectedFilterTagId(null)}
                        >
                          {t.tagsFilterAll}
                        </button>
                        {usedTagsInCurrentView.map((tg) => (
                          <button
                            key={tg.id}
                            type="button"
                            className={`tag-pill ${selectedFilterTagId === tg.id ? 'active' : ''}`}
                            onClick={() =>
                              setSelectedFilterTagId((cur) => (cur === tg.id ? null : tg.id))
                            }
                          >
                            <LayoutGrid size={11} />
                            <span>{tg.title}</span>
                          </button>
                        ))}
                      </div>
                    )}

                  {currentAreaMode === 'roadmap' && activeAreaRoadmapLayout ? (
                    <AreaPortfolioRoadmapWorkspace
                      area={area}
                      layout={activeAreaRoadmapLayout}
                      allTasks={snapshot.tasks}
                      selectedTaskIds={selectedTaskIds}
                      isMultiSelectMode={isExplicitMultiSelect}
                      lang={lang}
                      isDark={isEffectiveDarkTheme}
                      isFullScreen={isAreaRoadmapFullScreen}
                      onToggleFullScreen={() => {
                        if (isAreaRoadmapFullScreen) {
                          setIsTimelineFullScreen(false);
                        } else {
                          enterTimelineFullScreen();
                        }
                      }}
                      t={t}
                      onToast={showToast}
                      onOpenProject={(projectId) =>
                        handleSelectNav({ kind: 'project', projectId })
                      }
                      onSelectTask={handleSelectTask}
                      onOpenTaskEditor={(taskId) => {
                        if (isAreaRoadmapFullScreen) return;
                        setSelectedTaskIds([]);
                        setExpandedTaskId(taskId);
                      }}
                      onOpenProjectDeadlineModal={(projectId) =>
                        setDeadlineModalTarget({ kind: 'project', projectId })
                      }
                    />
                  ) : (
                    <>
                      {/* Projects Section: Shown ONLY when areaProjects.length > 0 (exact v1.6.16 rule) */}
                      {areaProjects.length > 0 && (
                        <div className="group-section" onClick={(e) => e.stopPropagation()}>
                          <div className="group-header">
                            <span className="group-header-title muted-nonbold">
                              {t.entityProjects} • {area.title} ({areaProjects.length})
                            </span>
                            <button
                              type="button"
                              className="chip-btn"
                              onClick={() =>
                                setProjectModalState({
                                  open: true,
                                  project: null,
                                  defaultAreaId: area.id
                                })
                              }
                            >
                              <Plus size={12} />
                              <span>{t.entityNewProject}</span>
                            </button>
                          </div>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            {areaProjects.map((proj, pIdx) => {
                              const pTasks = snapshot.tasks.filter(
                                (tk) =>
                                  !tk.isDeleted &&
                                  tk.projectId === proj.id &&
                                  tk.status !== 'CANCELED'
                              );
                              const pDone = pTasks.filter(
                                (tk) => tk.status === 'COMPLETED'
                              ).length;
                              const pInProgress = pTasks.filter(
                                (tk) => tk.status === 'OPEN' && Boolean(tk.isInProgress)
                              ).length;
                              const dlDiff =
                                proj.deadline != null ? daysBetweenToday(proj.deadline) : null;

                              return (
                                <div key={proj.id} className="area-project-row">
                                  <button
                                    type="button"
                                    style={{
                                      flex: 1,
                                      minWidth: 0,
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: 10,
                                      textAlign: 'left'
                                    }}
                                    onClick={() =>
                                      handleSelectNav({ kind: 'project', projectId: proj.id })
                                    }
                                  >
                                    <ProjectProgressRing
                                      completed={pDone}
                                      inProgress={pInProgress}
                                      total={pTasks.length}
                                      size={20}
                                    />
                                    <div style={{ minWidth: 0 }}>
                                      <div
                                        style={{
                                          fontWeight: 650,
                                          fontSize: 14.3,
                                          wordBreak: 'normal',
                                          overflowWrap: 'break-word'
                                        }}
                                        title={proj.title}
                                      >
                                        {proj.title}
                                      </div>
                                      <div style={{ fontSize: 11.8, color: 'var(--text-muted)' }}>
                                        {pDone}/{pTasks.length} • {t.entityTodos}
                                        {pInProgress > 0
                                          ? ` • ◐ ${t.v33ProjectInProgressCount(pInProgress)}`
                                          : ''}
                                      </div>
                                    </div>
                                  </button>

                                  {/* Target Date Badge or Pill */}
                                  <button
                                    type="button"
                                    className={`chip-btn ${
                                      dlDiff != null && dlDiff <= 2
                                        ? 'urgent'
                                        : proj.deadline != null
                                          ? 'active'
                                          : ''
                                    }`}
                                    onClick={() =>
                                      setDeadlineModalTarget({
                                        kind: 'project',
                                        projectId: proj.id
                                      })
                                    }
                                  >
                                    <Flag size={11.5} />
                                    <span>
                                      {proj.deadline != null && dlDiff != null
                                        ? dlDiff === 0
                                          ? t.deadlineToday
                                          : dlDiff > 0
                                            ? t.deadlineDaysLeft(dlDiff)
                                            : t.deadlineDaysOverdue(Math.abs(dlDiff))
                                        : t.targetDate}
                                    </span>
                                  </button>

                                  <button
                                    type="button"
                                    className="icon-btn"
                                    disabled={pIdx === 0}
                                    title={t.actionMove}
                                    onClick={() =>
                                      localDb.moveProjectOrder(proj.id, 'up', area.id)
                                    }
                                  >
                                    <ArrowUp size={14} />
                                  </button>
                                  <button
                                    type="button"
                                    className="icon-btn"
                                    disabled={pIdx === areaProjects.length - 1}
                                    title={t.actionMove}
                                    onClick={() =>
                                      localDb.moveProjectOrder(proj.id, 'down', area.id)
                                    }
                                  >
                                    <ArrowDown size={14} />
                                  </button>
                                  <button
                                    type="button"
                                    className="icon-btn"
                                    title={t.actionEdit}
                                    onClick={() =>
                                      setProjectModalState({ open: true, project: proj })
                                    }
                                  >
                                    <Edit3 size={14} />
                                  </button>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      )}

                      {/* Direct Area To-Dos */}
                      <div className="group-section">
                        {areaDirectTasks.length === 0 && areaProjects.length === 0 ? (
                          <div className="empty-state">{t.emptyView}</div>
                        ) : (
                          <div className="task-list">
                            {areaDirectTasks.map((tk) =>
                              renderTaskRow(tk, areaDirectTasks, false)
                            )}
                          </div>
                        )}
                      </div>
                    </>
                  )}
                </>
              );
            })()}

            {/* ==================== PROJECT DETAIL VIEW ==================== */}
            {activeNav.kind === 'project' && (() => {
              const project = allNonDeletedProjectsMap.get(activeNav.projectId);
              if (!project) {
                return <div className="empty-state">{t.emptyView}</div>;
              }
              const currentProjectMode = normalizeProjectViewMode(project.viewMode);
              const siblingProjects = activeProjects.filter(
                (p) => p.areaId === project.areaId
              );
              const projIdx = siblingProjects.findIndex((p) => p.id === project.id);

              const allProjTasks = snapshot.tasks.filter(
                (tk) =>
                  !tk.isDeleted &&
                  tk.projectId === project.id &&
                  tk.status !== 'CANCELED'
              );
              const doneProjTasks = allProjTasks.filter(
                (tk) => tk.status === 'COMPLETED'
              ).length;
              const inProgressProjTasks = allProjTasks.filter(
                (tk) => tk.status === 'OPEN' && Boolean(tk.isInProgress)
              ).length;

              const projHeadings = snapshot.headings.filter(
                (h) => !h.isDeleted && !h.isArchived && h.projectId === project.id
              );
              const validHeadingIdSet = new Set(projHeadings.map((h) => h.id));

              const visibleOpenProjTasks = filterByTag(currentViewBaseTasks);
              const ungroupedTasks = sortViewList(
                visibleOpenProjTasks.filter(
                  (tk) => !tk.headingId || !validHeadingIdSet.has(tk.headingId)
                )
              );

              const dlDiff =
                project.deadline != null ? daysBetweenToday(project.deadline) : null;
              const projArea = project.areaId ? areasMap.get(project.areaId) : undefined;
              const stalledStatus = evaluateProjectStalledStatus(project, snapshot.tasks);

              return (
                <>
                  {/* Project Header (Hidden when Roadmap Full Screen is active) */}
                  {!isRoadmapFullScreen && (
                    <div className="view-header" onClick={(e) => e.stopPropagation()}>
                      {/* Row 1: Generous Full-Width Project Title + Compact [✓ Complete], [✏️ Edit] & [⋯ Project Menu] */}
                      <div className="view-title-row project-header-title-row">
                        <div className="view-title-left project-title-left">
                          <ProjectProgressRing
                            completed={doneProjTasks}
                            inProgress={inProgressProjTasks}
                            total={allProjTasks.length}
                            size={26}
                            isProjectCompleted={project.status === 'COMPLETED'}
                          />
                          <h1
                            className="view-title project-view-title"
                            title={project.title}
                          >
                            {project.title}
                          </h1>
                          {project.status === 'ACTIVE' && inProgressProjTasks > 0 && (
                            <span
                              className="meta-badge in-progress-badge"
                              data-testid="project-header-in-progress-count"
                            >
                              <span>◐ {t.v33ProjectInProgressCount(inProgressProjTasks)}</span>
                            </span>
                          )}
                          {stalledStatus.isStalled && (
                            <button
                              type="button"
                              className="v2-stalled-pill"
                              data-testid="project-stalled-badge"
                              title={
                                stalledStatus.reason === 'no_next_action'
                                  ? t.projectStalledNoActionHint
                                  : t.projectStalledInactiveHint(stalledStatus.daysSinceActivity)
                              }
                              onClick={() => setWeeklyReviewModal({ open: true, step: 3 })}
                            >
                              <span>
                                {stalledStatus.reason === 'no_next_action'
                                  ? t.projectStalledBadge
                                  : t.projectRequiresReviewBadge}
                              </span>
                            </button>
                          )}
                        </div>

                        <div className="project-header-primary-actions">
                          {project.status !== 'COMPLETED' && (
                            <button
                              type="button"
                              className="chip-btn"
                              data-testid="project-complete-header-btn"
                              title={t.actionComplete}
                              style={
                                allProjTasks.length > 0 && doneProjTasks === allProjTasks.length
                                  ? {
                                      background:
                                        'color-mix(in srgb, var(--logbook-green) 14%, transparent)',
                                      borderColor:
                                        'color-mix(in srgb, var(--logbook-green) 36%, transparent)',
                                      color: 'var(--logbook-green)',
                                      fontWeight: 600
                                    }
                                  : undefined
                              }
                              onClick={() => void requestCompleteProject(project)}
                            >
                              <CheckCircle2
                                size={13.5}
                                style={{
                                  color:
                                    allProjTasks.length > 0 && doneProjTasks === allProjTasks.length
                                      ? 'var(--logbook-green)'
                                      : 'var(--things-blue)'
                                }}
                              />
                              <span>{t.v31ProjectCompleteBtn}</span>
                            </button>
                          )}

                          <button
                            type="button"
                            className="chip-btn"
                            data-testid="project-edit-btn"
                            title={t.actionEdit}
                            onClick={() => setProjectModalState({ open: true, project })}
                          >
                            <Edit3 size={13.5} />
                            <span>{t.actionEdit}</span>
                          </button>

                          <div className="dropdown-anchor">
                            <button
                              type="button"
                              className={`icon-btn ${projectHeaderMenuOpen ? 'active' : ''}`}
                              data-testid="project-header-more-btn"
                              title={t.actionMoreOptions}
                              aria-label={t.actionMoreOptions}
                              onClick={() => setProjectHeaderMenuOpen((v) => !v)}
                            >
                              <MoreHorizontal size={17} />
                            </button>

                            {projectHeaderMenuOpen && (
                              <>
                                <div
                                  style={{ position: 'fixed', inset: 0, zIndex: 70 }}
                                  onClick={() => setProjectHeaderMenuOpen(false)}
                                />
                                <div
                                  className="dropdown-popover"
                                  data-testid="project-header-more-menu"
                                  style={{ minWidth: 215 }}
                                >
                                  <button
                                    type="button"
                                    className="dropdown-item"
                                    disabled={projIdx <= 0}
                                    onClick={() => {
                                      setProjectHeaderMenuOpen(false);
                                      void localDb.moveProjectOrder(
                                        project.id,
                                        'up',
                                        project.areaId
                                      );
                                    }}
                                  >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                      <ArrowUp size={14} />
                                      <span>{t.actionMove} ↑</span>
                                    </span>
                                  </button>

                                  <button
                                    type="button"
                                    className="dropdown-item"
                                    disabled={
                                      projIdx < 0 || projIdx >= siblingProjects.length - 1
                                    }
                                    onClick={() => {
                                      setProjectHeaderMenuOpen(false);
                                      void localDb.moveProjectOrder(
                                        project.id,
                                        'down',
                                        project.areaId
                                      );
                                    }}
                                  >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                      <ArrowDown size={14} />
                                      <span>{t.actionMove} ↓</span>
                                    </span>
                                  </button>

                                  <button
                                    type="button"
                                    className="dropdown-item"
                                    data-testid="project-duplicate-btn"
                                    onClick={() => {
                                      setProjectHeaderMenuOpen(false);
                                      void handleDuplicateProject(project.id);
                                    }}
                                  >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                      <Copy size={14} style={{ color: 'var(--things-blue)' }} />
                                      <span>{t.actionDuplicateProject}</span>
                                    </span>
                                  </button>

                                  <button
                                    type="button"
                                    className="dropdown-item"
                                    data-testid="project-complete-btn"
                                    onClick={() => {
                                      setProjectHeaderMenuOpen(false);
                                      void requestCompleteProject(project);
                                    }}
                                  >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                      <CheckCircle2
                                        size={14}
                                        style={{ color: 'var(--logbook-green)' }}
                                      />
                                      <span>{t.actionComplete}</span>
                                    </span>
                                  </button>

                                  <div className="dropdown-divider" />

                                  <button
                                    type="button"
                                    className="dropdown-item"
                                    data-testid="project-delete-btn"
                                    style={{ color: 'var(--deadline-red)' }}
                                    onClick={() => {
                                      setProjectHeaderMenuOpen(false);
                                      void requestDeleteProject(project);
                                    }}
                                  >
                                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                      <Trash2
                                        size={14}
                                        style={{ color: 'var(--deadline-red)' }}
                                      />
                                      <span>{t.actionDelete}</span>
                                    </span>
                                  </button>
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Row 2: [List | Board | Roadmap] View Switcher + [Sort] */}
                      <div className="project-controls-bar">
                        <div
                          className="v2-segmented-toggle project-mode-switcher"
                          data-testid="project-view-mode-switcher"
                        >
                          <button
                            type="button"
                            className={`v2-segmented-btn ${
                              currentProjectMode === 'list' ? 'active' : ''
                            }`}
                            data-testid="project-view-mode-list"
                            title={t.projectViewList}
                            onClick={() => {
                              setIsTimelineFullScreen(false);
                              void localDb.upsertProject({
                                id: project.id,
                                viewMode: 'list'
                              });
                            }}
                          >
                            <List size={13.5} />
                            <span>{t.projectViewList}</span>
                          </button>
                          <button
                            type="button"
                            className={`v2-segmented-btn ${
                              currentProjectMode === 'board' ? 'active' : ''
                            }`}
                            data-testid="project-view-mode-board"
                            title={t.projectViewBoard}
                            onClick={() => {
                              setIsTimelineFullScreen(false);
                              void localDb.upsertProject({
                                id: project.id,
                                viewMode: 'board'
                              });
                            }}
                          >
                            <LayoutGrid size={13.5} />
                            <span>{t.projectViewBoard}</span>
                          </button>
                          <button
                            type="button"
                            className={`v2-segmented-btn ${
                              currentProjectMode === 'roadmap' ? 'active' : ''
                            }`}
                            data-testid="project-view-mode-roadmap"
                            title={t.projectViewRoadmap}
                            onClick={() =>
                              void localDb.upsertProject({
                                id: project.id,
                                viewMode: 'roadmap'
                              })
                            }
                          >
                            <CalendarDays size={13.5} />
                            <span>{t.projectViewRoadmap}</span>
                          </button>
                        </div>

                        {currentProjectMode !== 'roadmap' && renderViewSortDropdown()}
                      </div>

                      {/* Row 3: Project Notes (Rich Markdown + Detected Links — Android v1.7.0) */}
                      {project.notes.trim() && (
                        <div className="project-notes-display">
                          <FormattedMarkdownText text={project.notes.trim()} />
                          <DetectedNotesLinksRow
                            links={extractDetectedLinksFromNotes(project.notes)}
                          />
                        </div>
                      )}

                      {/* Row 3b: Project Attachments Strip (v1.8.0) */}
                      <AttachmentsStrip
                        projectId={project.id}
                        attachments={snapshot.attachments
                          .filter((a) => !a.isDeleted && a.projectId === project.id)
                          .sort((a, b) => a.createdAt - b.createdAt)}
                        t={t}
                        onToast={showToast}
                      />

                      {/* Row 4: Action Pills (Target Date, Assign Area Dropdown, + To-Do, + Heading, Templates, External) */}
                      <div className="project-action-pills">
                        <button
                          type="button"
                          className={`chip-btn ${
                            dlDiff != null && dlDiff <= 2
                              ? 'urgent'
                              : project.deadline != null
                                ? 'active'
                                : ''
                          }`}
                          title={t.targetDate}
                          onClick={() =>
                            setDeadlineModalTarget({ kind: 'project', projectId: project.id })
                          }
                        >
                          <Flag size={12.5} />
                          <span>
                            {project.deadline != null && dlDiff != null
                              ? `${t.targetDate}: ${
                                  dlDiff === 0
                                    ? t.deadlineToday
                                    : dlDiff > 0
                                      ? t.deadlineDaysLeft(dlDiff)
                                      : t.deadlineDaysOverdue(Math.abs(dlDiff))
                                }`
                              : `🏁 ${t.setTargetDate}`}
                          </span>
                        </button>

                        {/* Area Assignment Dropdown */}
                        <div className="dropdown-anchor">
                          <button
                            type="button"
                            className={`chip-btn ${projArea ? 'active' : ''}`}
                            title={t.assignArea}
                            onClick={() => setProjectAreaDropdownOpen((v) => !v)}
                          >
                            <Folder size={12.5} />
                            <span>
                              {projArea
                                ? `${t.entityArea}: ${projArea.title}`
                                : t.assignArea}
                            </span>
                          </button>

                          {projectAreaDropdownOpen && (
                            <>
                              <div
                                style={{ position: 'fixed', inset: 0, zIndex: 70 }}
                                onClick={() => setProjectAreaDropdownOpen(false)}
                              />
                              <div className="dropdown-popover" style={{ left: 0, right: 'auto' }}>
                                <button
                                  type="button"
                                  className="dropdown-item"
                                  onClick={async () => {
                                    await localDb.upsertProject({
                                      id: project.id,
                                      areaId: null
                                    });
                                    setProjectAreaDropdownOpen(false);
                                  }}
                                >
                                  <span>— {t.noProjectNoArea} —</span>
                                  {!project.areaId && <Check size={14} />}
                                </button>
                                {activeAreas.map((a) => (
                                  <button
                                    key={a.id}
                                    type="button"
                                    className="dropdown-item"
                                    onClick={async () => {
                                      await localDb.upsertProject({
                                        id: project.id,
                                        areaId: a.id
                                      });
                                      setProjectAreaDropdownOpen(false);
                                    }}
                                  >
                                    <span>📦 {a.title}</span>
                                    {project.areaId === a.id && <Check size={14} />}
                                  </button>
                                ))}
                              </div>
                            </>
                          )}
                        </div>

                        <button
                          type="button"
                          className="chip-btn"
                          title={`${t.entityNewTodo} (Ctrl+N)`}
                          onClick={() => void createNewTodoInCurrentContext(null)}
                        >
                          <Plus size={12.5} />
                          <span>{t.entityTodo}</span>
                        </button>

                        <button
                          type="button"
                          className="chip-btn"
                          title={t.entityNewHeading}
                          onClick={() => void createNewHeadingInActiveProject()}
                        >
                          <HeadingIcon size={12.5} />
                          <span>+ {t.entityHeading}</span>
                        </button>

                        <button
                          type="button"
                          className="chip-btn"
                          data-testid="project-save-template-btn"
                          style={{ whiteSpace: 'normal', height: 'auto' }}
                          title={t.projectSaveAsTemplateBtn}
                          onClick={async () => {
                            const tpl = await localDb.saveProjectAsTemplate(project.id);
                            if (tpl) {
                              showToast(`📋 ${t.projectSavedAsTemplateToast(tpl.title)}`);
                            }
                          }}
                        >
                          <ClipboardList size={12.5} style={{ flexShrink: 0 }} />
                          <span>{t.projectSaveAsTemplateBtn}</span>
                        </button>

                        <button
                          type="button"
                          className="chip-btn"
                          data-testid="project-external-import-export-btn"
                          style={{ whiteSpace: 'normal', height: 'auto' }}
                          title={t.importAsanaPlannerLabel}
                          onClick={() =>
                            setTemplatesModal({
                              open: true,
                              tab: 'external',
                              areaId: project.areaId
                            })
                          }
                        >
                          <FolderInput size={12.5} style={{ flexShrink: 0 }} />
                          <span>{t.externalImportExportTab}</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {/* View Tag Strip (Hidden in Roadmap Full Screen) */}
                  {!isRoadmapFullScreen && usedTagsInCurrentView.length > 0 && (
                    <div className="tag-filter-bar" onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        className={`tag-pill ${selectedFilterTagId === null ? 'active' : ''}`}
                        title={t.tagsFilterAll}
                        onClick={() => setSelectedFilterTagId(null)}
                      >
                        {t.tagsFilterAll}
                      </button>
                      {usedTagsInCurrentView.map((tg) => (
                        <button
                          key={tg.id}
                          type="button"
                          className={`tag-pill ${selectedFilterTagId === tg.id ? 'active' : ''}`}
                          title={`${t.entityTag}: ${tg.title}`}
                          onClick={() =>
                            setSelectedFilterTagId((cur) => (cur === tg.id ? null : tg.id))
                          }
                        >
                          <LayoutGrid size={11} />
                          <span>{tg.title}</span>
                        </button>
                      ))}
                    </div>
                  )}

                  {/* ==================== MODE 2: BOARD VIEW ==================== */}
                  {currentProjectMode === 'board' && (
                    <ProjectBoardWorkspace
                      project={project}
                      headings={projHeadings}
                      visibleTasks={visibleOpenProjTasks}
                      allProjectTasks={allProjTasks}
                      checklistItems={snapshot.checklistItems}
                      tagsMap={tagsMap}
                      openTaskIdSet={openTaskIdSet}
                      tasksByIdMap={tasksByIdMap}
                      selectedTaskIds={selectedTaskIds}
                      isMultiSelectMode={isExplicitMultiSelect}
                      orderedVisibleTaskIds={orderedVisibleTaskIds}
                      viewSortMode={viewSortMode}
                      lang={lang}
                      t={t}
                      onSelectTask={handleSelectTask}
                      onOpenTaskEditor={(taskId) => {
                        setSelectedTaskIds([]);
                        setExpandedTaskId(taskId);
                      }}
                      onCreateTodoInPhase={(headingId) =>
                        void createNewTodoInCurrentContext(headingId)
                      }
                      onCreatePhase={() => void createNewHeadingInActiveProject()}
                      onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
                      onOpenDeadlineModal={(taskId) =>
                        setDeadlineModalTarget({ kind: 'task', taskId })
                      }
                      onOpenRepeatModal={(taskId) => setRepeatModalTaskId(taskId)}
                      onOpenMoveModal={(taskId) => setMoveModalTaskIds([taskId])}
                      onOpenTagsModal={(taskId) => setTagPickerTaskId(taskId)}
                      onOpenDependenciesModal={(taskId) => setDependencyModalTaskId(taskId)}
                      onOpenHeadingStartModal={(headingId) =>
                        setDeadlineModalTarget({ kind: 'heading-start', headingId })
                      }
                      onOpenHeadingEndModal={(headingId) =>
                        setDeadlineModalTarget({ kind: 'heading', headingId })
                      }
                      onStartFocusTimer={handleStartFocusTimer}
                      onDuplicateTask={(taskId) => void handleDuplicateTask(taskId)}
                      onToast={showToast}
                    />
                  )}

                  {/* ==================== MODE 3: ROADMAP VIEW ==================== */}
                  {currentProjectMode === 'roadmap' && activeProjectRoadmapLayout && (
                    <ProjectRoadmapWorkspace
                      project={project}
                      projectTitle={project.title}
                      isFullScreen={isRoadmapFullScreen}
                      onToggleFullScreen={() => {
                        if (isRoadmapFullScreen) {
                          setIsTimelineFullScreen(false);
                        } else {
                          enterTimelineFullScreen();
                        }
                      }}
                      layout={activeProjectRoadmapLayout}
                      checklistItems={snapshot.checklistItems}
                      selectedTaskIds={selectedTaskIds}
                      isMultiSelectMode={isExplicitMultiSelect}
                      orderedVisibleTaskIds={orderedVisibleTaskIds}
                      lang={lang}
                      isDark={isEffectiveDarkTheme}
                      t={t}
                      onSelectTask={handleSelectTask}
                      onOpenTaskEditor={(taskId) => {
                        if (isRoadmapFullScreen) return;
                        setSelectedTaskIds([]);
                        setExpandedTaskId(taskId);
                      }}
                      onCreateTodoInPhase={(headingId) =>
                        void createNewTodoInCurrentContext(headingId)
                      }
                      onCreatePhase={() => void createNewHeadingInActiveProject()}
                      onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
                      onOpenHeadingStartModal={(headingId) =>
                        setDeadlineModalTarget({ kind: 'heading-start', headingId })
                      }
                      onOpenHeadingEndModal={(headingId) =>
                        setDeadlineModalTarget({ kind: 'heading', headingId })
                      }
                      onToast={showToast}
                    />
                  )}

                  {/* ==================== MODE 1: LIST VIEW ==================== */}
                  {currentProjectMode === 'list' && (
                    <>
                      {/* Ungrouped (General) Tasks at Top + Drop Target when Phases exist */}
                      {(ungroupedTasks.length > 0 ||
                        (projHeadings.length > 0 &&
                          (dragOverHeadingId === '__ungrouped__' ||
                            editingHeadingId === '__ungrouped__'))) && (
                        <div className="group-section">
                          {projHeadings.length > 0 &&
                            (ungroupedTasks.length > 0 ||
                              dragOverHeadingId === '__ungrouped__' ||
                              editingHeadingId === '__ungrouped__') && (() => {
                              const isEditingUngrouped =
                                editingHeadingId === '__ungrouped__';
                              const commitUngroupedPromote = async () => {
                                if (cancelingHeadingEditRef.current) {
                                  cancelingHeadingEditRef.current = false;
                                  return;
                                }
                                const clean = editingHeadingTitle.trim();
                                setEditingHeadingId(null);
                                if (clean) {
                                  await localDb.promoteUngroupedTasksToHeading(
                                    project.id,
                                    clean
                                  );
                                }
                              };

                              return (
                                <div
                                  className={`project-heading-header ungrouped-drop-header ${
                                    dragOverHeadingId === '__ungrouped__' ? 'drag-over' : ''
                                  }`}
                                  data-testid="project-ungrouped-drop-header"
                                  onClick={(e) => e.stopPropagation()}
                                  onDragOver={(e) => {
                                    if (hasDraggedTaskMime(e.dataTransfer)) {
                                      e.preventDefault();
                                      e.dataTransfer.dropEffect = 'move';
                                      setDragOverHeadingId('__ungrouped__');
                                    }
                                  }}
                                  onDragLeave={() => {
                                    if (dragOverHeadingId === '__ungrouped__') {
                                      setDragOverHeadingId(null);
                                    }
                                  }}
                                  onDrop={async (e) => {
                                    e.preventDefault();
                                    setDragOverHeadingId(null);
                                    const draggedIds = extractDraggedTaskIds(
                                      e.dataTransfer,
                                      selectedTaskIds,
                                      orderedVisibleTaskIds
                                    );
                                    if (draggedIds.length === 0) return;
                                    await localDb.batchMoveOrReorderTasksInContainer(
                                      draggedIds,
                                      {
                                        projectId: project.id,
                                        headingId: null,
                                        areaId: null
                                      }
                                    );
                                    if (draggedIds.length > 1) {
                                      showToast(t.dropMovedFeedback(draggedIds.length));
                                    }
                                  }}
                                >
                                  {isEditingUngrouped ? (
                                    <input
                                      type="text"
                                      className="text-field"
                                      style={{ padding: '4px 8px', flex: 1, fontWeight: 700 }}
                                      autoFocus
                                      data-testid="project-ungrouped-rename-input"
                                      value={editingHeadingTitle}
                                      placeholder={t.entityNewHeading}
                                      onChange={(e) =>
                                        setEditingHeadingTitle(e.target.value)
                                      }
                                      onBlur={() => void commitUngroupedPromote()}
                                      onKeyDown={(e) => {
                                        if (e.key === 'Enter') {
                                          e.preventDefault();
                                          void commitUngroupedPromote();
                                        } else if (e.key === 'Escape') {
                                          e.stopPropagation();
                                          cancelingHeadingEditRef.current = true;
                                          window.setTimeout(() => {
                                            cancelingHeadingEditRef.current = false;
                                          }, 0);
                                          setEditingHeadingId(null);
                                        }
                                      }}
                                    />
                                  ) : (
                                    <span
                                      className="project-heading-title-btn"
                                      style={{ cursor: 'default' }}
                                      onDoubleClick={() => {
                                        setEditingHeadingId('__ungrouped__');
                                        setEditingHeadingTitle(t.projectUngroupedColumn);
                                      }}
                                    >
                                      <span className="project-phase-accent general" />
                                      <span className="project-heading-title-text">
                                        {t.projectUngroupedColumn}
                                      </span>
                                    </span>
                                  )}

                                  <div className="project-heading-actions">
                                    <button
                                      type="button"
                                      className="icon-btn"
                                      style={{ height: 26, minWidth: 26, padding: 0 }}
                                      title={t.entityNewTodo}
                                      onClick={() => void createNewTodoInCurrentContext(null)}
                                    >
                                      <Plus size={14.5} />
                                    </button>
                                    {ungroupedTasks.length > 0 && (
                                      <>
                                        <button
                                          type="button"
                                          className="icon-btn"
                                          data-testid="project-ungrouped-rename-btn"
                                          style={{ height: 26, minWidth: 26, padding: 0 }}
                                          title={t.actionRename}
                                          onClick={() => {
                                            setEditingHeadingId('__ungrouped__');
                                            setEditingHeadingTitle(t.projectUngroupedColumn);
                                          }}
                                        >
                                          <Edit3 size={13.5} />
                                        </button>
                                        <button
                                          type="button"
                                          className="icon-btn"
                                          data-testid="project-ungrouped-delete-btn"
                                          style={{
                                            height: 26,
                                            minWidth: 26,
                                            padding: 0,
                                            color: 'var(--deadline-red)'
                                          }}
                                          title={t.actionDelete}
                                          onClick={async () => {
                                            const ids = ungroupedTasks.map((tk) => tk.id);
                                            if (ids.length === 0) return;
                                            await localDb.softDeleteTasks(ids);
                                            showToast(
                                              t.undoTaskDeleted(t.projectUngroupedColumn)
                                            );
                                          }}
                                        >
                                          <Trash2 size={13.5} />
                                        </button>
                                      </>
                                    )}
                                  </div>
                                </div>
                              );
                            })()}

                          {ungroupedTasks.length > 0 && (
                            <div className="task-list">
                              {ungroupedTasks.map((tk) =>
                                renderTaskRow(tk, ungroupedTasks, false)
                              )}
                            </div>
                          )}
                        </div>
                      )}

                      {/* Collapsible Project Phases with Progress, Start/End Date Capsules & Multi-Task Drop Target */}
                      {projHeadings.map((hd) => {
                        const hdTasks = sortViewList(
                          visibleOpenProjTasks.filter((tk) => tk.headingId === hd.id)
                        );
                        const allHdTasks = allProjTasks.filter((tk) => tk.headingId === hd.id);
                        const doneHdTasks = allHdTasks.filter(
                          (tk) => tk.status === 'COMPLETED'
                        ).length;
                        const inProgressHdTasks = allHdTasks.filter(
                          (tk) => tk.status === 'OPEN' && Boolean(tk.isInProgress)
                        ).length;
                        const hdEndDiff =
                          hd.endDate != null ? daysBetweenToday(hd.endDate) : null;
                        const isPhaseOverdue =
                          hdEndDiff != null &&
                          hdEndDiff < 0 &&
                          doneHdTasks < allHdTasks.length;
                        const formattedPhaseStart =
                          hd.startDate != null
                            ? new Intl.DateTimeFormat(lang, {
                                month: 'short',
                                day: 'numeric'
                              }).format(new Date(hd.startDate))
                            : null;
                        const formattedPhaseEnd =
                          hd.endDate != null
                            ? new Intl.DateTimeFormat(lang, {
                                month: 'short',
                                day: 'numeric'
                              }).format(new Date(hd.endDate))
                            : null;
                        const isCollapsed = collapsedHeadingIds.has(hd.id);
                        const isEditing = editingHeadingId === hd.id;

                        const toggleCollapse = () => {
                          setCollapsedHeadingIds((prev) => {
                            const next = new Set(prev);
                            if (next.has(hd.id)) next.delete(hd.id);
                            else next.add(hd.id);
                            return next;
                          });
                        };

                        const commitHeadingEdit = async () => {
                          if (cancelingHeadingEditRef.current) {
                            cancelingHeadingEditRef.current = false;
                            return;
                          }
                          const clean = editingHeadingTitle.trim();
                          if (!clean) {
                            if (!hd.title.trim()) {
                              await localDb.deleteHeading(hd.id);
                            }
                          } else {
                            await localDb.upsertHeading({
                              id: hd.id,
                              projectId: project.id,
                              title: clean
                            });
                          }
                          setEditingHeadingId(null);
                        };

                        return (
                          <div
                            key={hd.id}
                            className="group-section"
                            data-magic-heading-id={hd.id}
                          >
                            <div
                              className={`project-heading-header ${
                                dragOverHeadingId === hd.id ? 'drag-over' : ''
                              }`}
                              data-heading-id={hd.id}
                              onClick={(e) => e.stopPropagation()}
                              onDragOver={(e) => {
                                if (hasDraggedTaskMime(e.dataTransfer)) {
                                  e.preventDefault();
                                  e.dataTransfer.dropEffect = 'move';
                                  setDragOverHeadingId(hd.id);
                                }
                              }}
                              onDragLeave={() => {
                                if (dragOverHeadingId === hd.id) setDragOverHeadingId(null);
                              }}
                              onDrop={async (e) => {
                                e.preventDefault();
                                setDragOverHeadingId(null);
                                const draggedIds = extractDraggedTaskIds(
                                  e.dataTransfer,
                                  selectedTaskIds,
                                  orderedVisibleTaskIds
                                );
                                if (draggedIds.length === 0) return;
                                await localDb.batchMoveOrReorderTasksInContainer(draggedIds, {
                                  projectId: project.id,
                                  headingId: hd.id,
                                  areaId: null
                                });
                                if (isCollapsed) {
                                  setCollapsedHeadingIds((prev) => {
                                    const next = new Set(prev);
                                    next.delete(hd.id);
                                    return next;
                                  });
                                }
                                if (draggedIds.length > 1) {
                                  showToast(t.dropMovedFeedback(draggedIds.length));
                                }
                              }}
                            >
                              {isEditing ? (
                                <input
                                  type="text"
                                  className="text-field"
                                  style={{ padding: '4px 8px', flex: 1, fontWeight: 700 }}
                                  autoFocus
                                  value={editingHeadingTitle}
                                  placeholder={t.entityNewHeading}
                                  onChange={(e) => setEditingHeadingTitle(e.target.value)}
                                  onBlur={() => void commitHeadingEdit()}
                                  onKeyDown={(e) => {
                                    if (e.key === 'Enter') {
                                      e.preventDefault();
                                      void commitHeadingEdit();
                                    } else if (e.key === 'Escape') {
                                      e.stopPropagation();
                                      cancelingHeadingEditRef.current = true;
                                      window.setTimeout(() => {
                                        cancelingHeadingEditRef.current = false;
                                      }, 0);
                                      if (!hd.title.trim()) {
                                        void localDb.deleteHeading(hd.id);
                                      }
                                      setEditingHeadingId(null);
                                    }
                                  }}
                                />
                              ) : (
                                <button
                                  type="button"
                                  className="project-heading-title-btn"
                                  onClick={toggleCollapse}
                                >
                                  <span className="project-phase-accent" />
                                  <span className="project-heading-title-text">
                                    {hd.title || t.entityHeading}
                                  </span>
                                  {allHdTasks.length > 0 && (
                                    <ProjectProgressRing
                                      completed={doneHdTasks}
                                      inProgress={inProgressHdTasks}
                                      total={allHdTasks.length}
                                      size={14}
                                      showCount
                                    />
                                  )}
                                </button>
                              )}

                              {/* Phase Start Date & End Date Capsules + 4 Trailing Icon Buttons */}
                              <div className="project-heading-actions">
                                <span className="phase-date-chip-group">
                                  <button
                                    type="button"
                                    className={`phase-end-pill ${
                                      hd.startDate != null ? 'has-date' : ''
                                    }`}
                                    title={t.phaseSetStartDate}
                                    onClick={() =>
                                      setDeadlineModalTarget({
                                        kind: 'heading-start',
                                        headingId: hd.id
                                      })
                                    }
                                  >
                                    <CalendarDays size={12} />
                                    <span>
                                      {formattedPhaseStart != null
                                        ? t.phaseStartsOn(formattedPhaseStart)
                                        : `+ ${t.phaseSetStartDate}`}
                                    </span>
                                  </button>
                                  {hd.startDate != null && (
                                    <button
                                      type="button"
                                      className="phase-date-clear-btn"
                                      title={t.phaseClearStartDate}
                                      aria-label={t.phaseClearStartDate}
                                      onClick={() =>
                                        void localDb.updateHeadingStartDate(hd.id, null)
                                      }
                                    >
                                      <X size={10.5} />
                                    </button>
                                  )}
                                </span>

                                <span className="phase-date-chip-group">
                                  <button
                                    type="button"
                                    className={`phase-end-pill ${
                                      isPhaseOverdue
                                        ? 'overdue'
                                        : hd.endDate != null
                                          ? 'has-date'
                                          : ''
                                    }`}
                                    title={t.phaseSetEndDate}
                                    onClick={() =>
                                      setDeadlineModalTarget({
                                        kind: 'heading',
                                        headingId: hd.id
                                      })
                                    }
                                  >
                                    <CalendarDays size={12} />
                                    <span>
                                      {formattedPhaseEnd != null
                                        ? t.phaseEndsOn(formattedPhaseEnd)
                                        : `+ ${t.phaseSetEndDate}`}
                                    </span>
                                  </button>
                                  {hd.endDate != null && (
                                    <button
                                      type="button"
                                      className="phase-date-clear-btn"
                                      title={t.phaseClearEndDate}
                                      aria-label={t.phaseClearEndDate}
                                      onClick={() =>
                                        void localDb.updateHeadingEndDate(hd.id, null)
                                      }
                                    >
                                      <X size={10.5} />
                                    </button>
                                  )}
                                </span>

                                <button
                                  type="button"
                                  className="icon-btn"
                                  style={{ height: 26, minWidth: 26, padding: 0 }}
                                  title={t.entityNewTodo}
                                  onClick={() => {
                                    if (isCollapsed) {
                                      setCollapsedHeadingIds((prev) => {
                                        const next = new Set(prev);
                                        next.delete(hd.id);
                                        return next;
                                      });
                                    }
                                    void createNewTodoInCurrentContext(hd.id);
                                  }}
                                >
                                  <Plus size={14.5} />
                                </button>
                                <button
                                  type="button"
                                  className="icon-btn"
                                  style={{ height: 26, minWidth: 26, padding: 0 }}
                                  title={t.actionRename}
                                  onClick={() => {
                                    setEditingHeadingId(hd.id);
                                    setEditingHeadingTitle(hd.title);
                                  }}
                                >
                                  <Edit3 size={13.5} />
                                </button>
                                <button
                                  type="button"
                                  className="icon-btn"
                                  style={{
                                    height: 26,
                                    minWidth: 26,
                                    padding: 0,
                                    color: 'var(--deadline-red)'
                                  }}
                                  title={t.actionDelete}
                                  onClick={() => localDb.deleteHeading(hd.id)}
                                >
                                  <Trash2 size={13.5} />
                                </button>
                                <button
                                  type="button"
                                  className="icon-btn"
                                  style={{ height: 26, minWidth: 26, padding: 0 }}
                                  title={isCollapsed ? t.sidebarShow : t.sidebarHide}
                                  onClick={toggleCollapse}
                                >
                                  {isCollapsed ? (
                                    <ChevronDown size={15} />
                                  ) : (
                                    <ChevronUp size={15} />
                                  )}
                                </button>
                              </div>
                            </div>

                            {!isCollapsed && (
                              <div className="task-list">
                                {hdTasks.map((tk) => renderTaskRow(tk, hdTasks, false))}
                              </div>
                            )}
                          </div>
                        );
                      })}

                      {ungroupedTasks.length === 0 && projHeadings.length === 0 && (
                        <div className="empty-state">{t.emptyView}</div>
                      )}
                    </>
                  )}
                </>
              );
            })()}

            {/* ==================== CUSTOM PERSPECTIVE VIEW ==================== */}
            {activeNav.kind === 'perspective' && (() => {
              const persp = savedPerspectives.find(
                (p) => p.id === activeNav.perspectiveId
              );
              if (!persp) {
                return <div className="empty-state">{t.emptyView}</div>;
              }
              const perspTasks = sortViewList(filterByTag(currentViewBaseTasks));
              const perspArea = persp.areaId ? areasMap.get(persp.areaId) : undefined;
              const perspCategories = persp.categoryIds
                .map((cid) => tagsMap.get(cid))
                .filter((tg): tg is TagRecord => tg != null);

              return (
                <>
                  <div className="view-header" onClick={(e) => e.stopPropagation()}>
                    <div className="view-title-row">
                      <div className="view-title-left" style={{ flexWrap: 'wrap' }}>
                        <SlidersHorizontal
                          size={25}
                          style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                        />
                        <h1 className="view-title">{persp.title}</h1>
                        <span className="today-capacity-pill">
                          {perspTasks.length} {t.entityTodos}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        {renderViewSortDropdown()}
                        <button
                          type="button"
                          className="icon-btn"
                          data-testid="perspective-edit-header-btn"
                          title={t.perspectiveEditTitle}
                          onClick={() =>
                            setPerspectiveModalState({ open: true, perspective: persp })
                          }
                        >
                          <Edit3 size={15.5} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          data-testid="perspective-delete-header-btn"
                          style={{ color: 'var(--deadline-red)' }}
                          title={t.actionDelete}
                          onClick={() => {
                            const nextList = savedPerspectives.filter(
                              (p) => p.id !== persp.id
                            );
                            updatePrefs({ savedPerspectives: nextList });
                            handleSelectNav({ kind: 'smart', view: 'TODAY' });
                            showToast(`✓ ${t.actionDelete}`);
                          }}
                        >
                          <Trash2 size={15.5} />
                        </button>
                      </div>
                    </div>

                    {/* Active Perspective Filter Summary Chips */}
                    <div className="project-action-pills" style={{ marginTop: 8 }}>
                      {perspArea && (
                        <span className="meta-badge">
                          <Folder size={11} />
                          <span>{perspArea.title}</span>
                        </span>
                      )}
                      {persp.dueWindow !== 'ANY' && (
                        <span className="meta-badge">
                          <CalendarDays size={11} />
                          <span>
                            {persp.dueWindow === 'TODAY'
                              ? t.perspectiveDueToday
                              : persp.dueWindow === 'NEXT_7_DAYS'
                                ? t.perspectiveDueNext7Days
                                : t.perspectiveDueOverdue}
                          </span>
                        </span>
                      )}
                      {persp.effortLevel && (
                        <span className="meta-badge">
                          <span>
                            {persp.effortLevel === 'QUICK_WIN'
                              ? `⚡ ${t.effortQuickWin}`
                              : persp.effortLevel === 'MEDIUM'
                                ? t.effortMedium
                                : `🧠 ${t.effortDeepFocus}`}
                          </span>
                        </span>
                      )}
                      {persp.maxDurationMinutes != null && (
                        <span className="meta-badge">
                          <span>
                            ≤ {formatStandaloneDuration(persp.maxDurationMinutes)}
                          </span>
                        </span>
                      )}
                      {perspCategories.map((tg) => (
                        <span key={tg.id} className="meta-badge">
                          <LayoutGrid size={10.5} />
                          <span>{tg.title}</span>
                        </span>
                      ))}
                      {persp.inProgressOnly && (
                        <span className="meta-badge in-progress-badge">
                          <span>◐ {t.v33FilterInProgressOnly}</span>
                        </span>
                      )}
                      {persp.actionableOnly && (
                        <span className="meta-badge">
                          <span>✓ {t.perspectiveActionableOnlyLabel}</span>
                        </span>
                      )}
                    </div>
                  </div>

                  {/* View Tag Strip */}
                  {usedTagsInCurrentView.length > 0 && (
                    <div className="tag-filter-bar" onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        className={`tag-pill ${selectedFilterTagId === null ? 'active' : ''}`}
                        title={t.tagsFilterAll}
                        onClick={() => setSelectedFilterTagId(null)}
                      >
                        {t.tagsFilterAll}
                      </button>
                      {usedTagsInCurrentView.map((tg) => (
                        <button
                          key={tg.id}
                          type="button"
                          className={`tag-pill ${selectedFilterTagId === tg.id ? 'active' : ''}`}
                          title={`${t.entityTag}: ${tg.title}`}
                          onClick={() =>
                            setSelectedFilterTagId((cur) => (cur === tg.id ? null : tg.id))
                          }
                        >
                          <LayoutGrid size={11} />
                          <span>{tg.title}</span>
                        </button>
                      ))}
                    </div>
                  )}

                  <div className="group-section">
                    {perspTasks.length === 0 ? (
                      <div className="empty-state" data-testid="perspective-empty-state">
                        {t.perspectiveEmptyHint}
                      </div>
                    ) : (
                      <div className="task-list">
                        {perspTasks.map((tk) => renderTaskRow(tk, perspTasks, true))}
                      </div>
                    )}
                  </div>
                </>
              );
            })()}
          </div>
        </div>

        {effectiveInspectorOpen && (
          <WorkspaceInspectorPanel
            selectedTask={selectedInspectorTask}
            todayTasks={[...todayDaytimeTasks, ...todayEveningTasks]}
            todayCompletedCount={todayCompletedCount}
            todayCalendarEvents={activeTodayCalendarEvents}
            upcomingCalendarEventsByEpochDay={
              prefs.calendarEventsEnabled ? upcomingCalendarEventsByEpochDay : undefined
            }
            workdayStartMinutes={workdayBounds.startMinutes}
            workdayEndMinutes={workdayBounds.endMinutes}
            todayStartMs={todayStartMs}
            endOfTodayMs={endOfToday}
            allOpenTasks={openInActiveProj}
            visibleViewTasks={visibleViewTasks}
            checklistItems={snapshot.checklistItems}
            headings={snapshot.headings}
            attachments={snapshot.attachments}
            openTaskIdSet={openTaskIdSet}
            tasksByIdMap={tasksByIdMap}
            areasMap={areasMap}
            projectsMap={allNonDeletedProjectsMap}
            tagsMap={tagsMap}
            lang={lang}
            geminiApiKey={prefs.geminiApiKey}
            t={t}
            onToast={showToast}
            onSelectTaskFromInspector={handleSelectTaskFromInspector}
            onOpenTaskEditor={(taskId) => {
              setSelectedTaskIds([]);
              setExpandedTaskId(taskId);
            }}
            onClearSelection={() => setSelectedTaskIds([])}
            onCollapsePanel={toggleInspectorOpen}
            onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
            onOpenDeadlineModal={(taskId) =>
              setDeadlineModalTarget({ kind: 'task', taskId })
            }
            onOpenRepeatModal={(id) => setRepeatModalTaskId(id)}
            onOpenMoveModal={(taskId) => setMoveModalTaskIds([taskId])}
            onOpenTagsModal={(taskId) => setTagPickerTaskId(taskId)}
            onOpenDependenciesModal={(id) => setDependencyModalTaskId(id)}
            onDuplicateTask={handleDuplicateTask}
            onStartFocusTimer={handleStartFocusTimer}
          />
        )}
        </div>

        {/* Floating Multi-Selection Bar (ThingsMultiSelectionBar; hidden in Timeline/Roadmap Full Screen) */}
        {!isAnyTimelineFullScreen && selectedTaskIds.length > 0 && !expandedTaskId && (selectedTaskIds.length > 1 || isExplicitMultiSelect) && (() => {
          const allVisibleIds = orderedVisibleTaskIds;
          const allSelectedPinned =
            selectedTaskIds.length > 0 &&
            selectedTaskIds.every((id) => Boolean(tasksByIdMap.get(id)?.isPinned));
          const openSelectedIds = selectedTaskIds.filter(
            (id) => tasksByIdMap.get(id)?.status === 'OPEN'
          );
          const allSelectedInProgress =
            openSelectedIds.length > 0 &&
            openSelectedIds.every((id) => Boolean(tasksByIdMap.get(id)?.isInProgress));
          return (
            <div className="multi-select-bar" onClick={(e) => e.stopPropagation()}>
              <span className="multi-count-pill">
                {t.multiSelectCount(selectedTaskIds.length)}
              </span>

              {selectedTaskIds.length < allVisibleIds.length && (
                <button
                  type="button"
                  className="chip-btn"
                  title={t.multiSelectSelectAllTooltip}
                  onClick={() => {
                    setIsExplicitMultiSelect(true);
                    setSelectedTaskIds(allVisibleIds);
                  }}
                >
                  {t.multiSelectAll}
                </button>
              )}

              <button
                type="button"
                className="chip-btn"
                title={t.viewToday}
                onClick={async () => {
                  await localDb.batchUpdateTasks(selectedTaskIds, {
                    scheduleState: 'TODAY',
                    whenDate: startOfDayEpoch()
                  });
                  setSelectedTaskIds([]);
                }}
              >
                <Sun size={12} style={{ color: 'var(--today-star)' }} />
                <span>{t.viewToday}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
                title={t.dateTomorrow}
                onClick={async () => {
                  await localDb.batchUpdateTasks(selectedTaskIds, {
                    scheduleState: 'UPCOMING',
                    whenDate: addDaysLocal(startOfDayEpoch(), 1)
                  });
                  setSelectedTaskIds([]);
                }}
              >
                <CalendarDays size={12} />
                <span>{t.dateTomorrow}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
                data-testid="multi-select-someday-btn"
                title={t.batchSomeday}
                onClick={async () => {
                  await localDb.batchUpdateTasks(selectedTaskIds, {
                    scheduleState: 'SOMEDAY',
                    whenDate: null
                  });
                  setSelectedTaskIds([]);
                }}
              >
                <Cloud size={12} />
                <span>{t.batchSomeday}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
                data-testid="multi-select-categories-btn"
                title={t.batchTags}
                onClick={() => setBatchTagPickerOpen(true)}
              >
                <LayoutGrid size={12} />
                <span>{t.batchTags}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${allSelectedPinned ? 'active' : ''}`}
                data-testid="multi-select-pin-btn"
                title={allSelectedPinned ? t.unpinTask : t.batchPin}
                onClick={() => {
                  void localDb.batchUpdateTasks(selectedTaskIds, {
                    isPinned: !allSelectedPinned
                  });
                }}
              >
                {allSelectedPinned ? <PinOff size={12} /> : <Pin size={12} />}
                <span>{allSelectedPinned ? t.unpinTask : t.batchPin}</span>
              </button>

              {openSelectedIds.length > 0 && (
                <button
                  type="button"
                  className={`chip-btn ${allSelectedInProgress ? 'in-progress-active' : ''}`}
                  data-testid="multi-select-in-progress-btn"
                  title={allSelectedInProgress ? t.v33ClearInProgress : t.v33MarkInProgress}
                  onClick={() => {
                    void localDb.batchUpdateTasks(openSelectedIds, {
                      isInProgress: !allSelectedInProgress
                    });
                  }}
                >
                  <InProgressIcon size={13.5} />
                  <span>
                    {allSelectedInProgress ? t.v33ClearInProgress : t.v33MarkInProgress}
                  </span>
                </button>
              )}

              <button
                type="button"
                className="chip-btn"
                data-testid="multi-select-duplicate-btn"
                title={`${t.batchDuplicate} (Ctrl+D)`}
                onClick={() => void handleBatchDuplicateSelected()}
              >
                <Copy size={12} />
                <span>{t.batchDuplicate}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
                title={t.actionMove}
                onClick={() => setMoveModalTaskIds(selectedTaskIds)}
              >
                <FolderInput size={12} />
                <span>{t.actionMove}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
                title={t.actionComplete}
                onClick={async () => {
                  await localDb.batchUpdateTasks(selectedTaskIds, { status: 'COMPLETED' });
                  setSelectedTaskIds([]);
                }}
              >
                <Check size={12} />
                <span>{t.actionComplete}</span>
              </button>

              <button
                type="button"
                className="chip-btn urgent"
                title={t.actionDelete}
                onClick={async () => {
                  await localDb.softDeleteTasks(selectedTaskIds);
                  setSelectedTaskIds([]);
                }}
              >
                <Trash2 size={12} />
                <span>{t.actionDelete}</span>
              </button>

              <button
                type="button"
                className="icon-btn"
                style={{ height: 26, minWidth: 26, padding: 0 }}
                title={t.multiSelectClearTooltip}
                onClick={() => setSelectedTaskIds([])}
              >
                <X size={14} />
              </button>
            </div>
          );
        })()}

        {/* Context-Aware Magic Plus FAB (Hidden in Timeline / Roadmap Full Screen) */}
        {!isAnyTimelineFullScreen && (
          <MagicPlusFab
            open={magicFabOpen}
            activeNav={activeNav}
            currentViewTitle={currentViewTitle}
            tasks={snapshot.tasks}
            headings={snapshot.headings}
            scrollContainerRef={workspaceScrollRef}
            t={t}
            onToggleOpen={() => setMagicFabOpen((v) => !v)}
            onClose={() => setMagicFabOpen(false)}
            onNewTodo={() => void createNewTodoInCurrentContext(null)}
            onNewHeading={() => void createNewHeadingInActiveProject()}
            onNewProject={() =>
              setProjectModalState({
                open: true,
                project: null,
                defaultAreaId: defaultAreaIdForNewProject
              })
            }
            onDropInsert={(drop) => void handleMagicPlusDropInsert(drop)}
          />
        )}
      </main>

      {/* ==================== MODALS ==================== */}
      {expandedTaskId && (() => {
        const liveExpanded = snapshot.tasks.find(
          (x) => x.id === expandedTaskId && !x.isDeleted
        );
        if (!liveExpanded) return null;
        return (
          <div
            className="modal-backdrop task-bento-modal-backdrop"
            data-testid="task-modal-backdrop"
            onClick={(e) => {
              e.stopPropagation();
              setExpandedTaskId(null);
            }}
          >
            <div
              className="task-bento-modal-shell"
              onClick={(e) => e.stopPropagation()}
            >
              <TaskItem
                task={liveExpanded}
                isExpanded
                isTodayView={activeNav.kind === 'smart' && activeNav.view === 'TODAY'}
                isSelected={false}
                isMultiSelectMode={false}
                showContextSubtitle={false}
                showScheduleBadgeInCollapsed
                quickFindOpen={quickFindOpen}
                currentDayMarker={endOfToday}
                areasMap={areasMap}
                projectsMap={allNonDeletedProjectsMap}
                tagsMap={tagsMap}
                checklistItems={snapshot.checklistItems}
                attachments={snapshot.attachments}
                openTaskIdSet={openTaskIdSet}
                tasksByIdMap={tasksByIdMap}
                lang={lang}
                geminiApiKey={prefs.geminiApiKey}
                t={t}
                onSelect={(multiToggle, shiftRange) =>
                  handleSelectTask(liveExpanded.id, multiToggle, shiftRange)
                }
                onExpand={() => {}}
                onCollapse={() => setExpandedTaskId(null)}
                onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
                onOpenDeadlineModal={(taskId) =>
                  setDeadlineModalTarget({ kind: 'task', taskId })
                }
                onOpenRepeatModal={(taskId) => setRepeatModalTaskId(taskId)}
                onOpenMoveModal={(taskId) => setMoveModalTaskIds([taskId])}
                onOpenTagsModal={(taskId) => setTagPickerTaskId(taskId)}
                onOpenDependenciesModal={(taskId) => setDependencyModalTaskId(taskId)}
                onStartFocusTimer={handleStartFocusTimer}
                onDuplicateTask={(taskId) => void handleDuplicateTask(taskId)}
                onDropReorder={() => {}}
                onToast={showToast}
              />
            </div>
          </div>
        );
      })()}

      {whenModalTaskId && (() => {
        const liveWhenTask = snapshot.tasks.find((x) => x.id === whenModalTaskId);
        if (!liveWhenTask) return null;
        return (
          <WhenModal
            task={liveWhenTask}
            lang={lang}
            t={t}
            onApply={(patch) => {
              void localDb.upsertTask({
                id: liveWhenTask.id,
                ...patch
              });
            }}
            onOpenDeadline={() =>
              setDeadlineModalTarget({ kind: 'task', taskId: liveWhenTask.id })
            }
            onClose={() => setWhenModalTaskId(null)}
          />
        );
      })()}

      {deadlineModalTarget && (() => {
        const liveProject =
          deadlineModalTarget.kind === 'project'
            ? snapshot.projects.find((p) => p.id === deadlineModalTarget.projectId)
            : undefined;
        const liveTask =
          deadlineModalTarget.kind === 'task'
            ? snapshot.tasks.find((x) => x.id === deadlineModalTarget.taskId)
            : undefined;
        const liveHeading =
          deadlineModalTarget.kind === 'heading' ||
          deadlineModalTarget.kind === 'heading-start'
            ? snapshot.headings.find((h) => h.id === deadlineModalTarget.headingId)
            : undefined;
        if (deadlineModalTarget.kind === 'project' && !liveProject) return null;
        if (deadlineModalTarget.kind === 'task' && !liveTask) return null;
        if (
          (deadlineModalTarget.kind === 'heading' ||
            deadlineModalTarget.kind === 'heading-start') &&
          !liveHeading
        ) {
          return null;
        }

        return (
          <DeadlineModal
            titleLabel={
              deadlineModalTarget.kind === 'project'
                ? t.targetDate
                : deadlineModalTarget.kind === 'heading-start'
                  ? `${liveHeading!.title || t.entityHeading} — ${t.phaseSetStartDate}`
                  : deadlineModalTarget.kind === 'heading'
                    ? `${liveHeading!.title || t.entityHeading} — ${t.phaseSetEndDate}`
                    : t.actionDeadline
            }
            clearLabel={
              deadlineModalTarget.kind === 'heading-start'
                ? t.phaseClearStartDate
                : deadlineModalTarget.kind === 'heading'
                  ? t.phaseClearEndDate
                  : undefined
            }
            currentDeadline={
              deadlineModalTarget.kind === 'project'
                ? liveProject!.deadline
                : deadlineModalTarget.kind === 'heading-start'
                  ? (liveHeading!.startDate ?? null)
                  : deadlineModalTarget.kind === 'heading'
                    ? (liveHeading!.endDate ?? null)
                    : liveTask!.deadline
            }
            lang={lang}
            t={t}
            onApply={(dl) => {
              if (deadlineModalTarget.kind === 'project') {
                void localDb.upsertProject({
                  id: deadlineModalTarget.projectId,
                  deadline: dl
                });
              } else if (deadlineModalTarget.kind === 'heading-start') {
                void localDb.updateHeadingStartDate(deadlineModalTarget.headingId, dl);
              } else if (deadlineModalTarget.kind === 'heading') {
                void localDb.updateHeadingEndDate(deadlineModalTarget.headingId, dl);
              } else {
                void localDb.upsertTask({
                  id: deadlineModalTarget.taskId,
                  deadline: dl
                });
              }
            }}
            onClose={() => setDeadlineModalTarget(null)}
          />
        );
      })()}

      {repeatModalTaskId && (() => {
        const liveRepeatTask = snapshot.tasks.find((x) => x.id === repeatModalTaskId);
        if (!liveRepeatTask) return null;
        return (
          <RepeatModal
            task={liveRepeatTask}
            lang={lang}
            t={t}
            onApply={(rule) => {
              void localDb.upsertTask({
                id: liveRepeatTask.id,
                recurrenceRule: rule
              });
            }}
            onClose={() => setRepeatModalTaskId(null)}
          />
        );
      })()}

      {moveModalTaskIds && (
        <MoveTaskModal
          taskIds={moveModalTaskIds}
          areas={snapshot.areas}
          projects={snapshot.projects}
          headings={snapshot.headings}
          t={t}
          onMoved={() => setSelectedTaskIds([])}
          onClose={() => setMoveModalTaskIds(null)}
        />
      )}

      {tagPickerTaskId && (() => {
        const liveTask = snapshot.tasks.find((x) => x.id === tagPickerTaskId);
        if (!liveTask) return null;
        return (
          <TaskTagPickerModal
            selectedTagIds={liveTask.tagIds}
            tags={snapshot.tags}
            usageCounts={tagUsageCounts}
            tagSortMode={tagSortMode}
            t={t}
            onToggleTag={(tagId) => {
              const next = liveTask.tagIds.includes(tagId)
                ? liveTask.tagIds.filter((id) => id !== tagId)
                : [...liveTask.tagIds, tagId];
              void localDb.upsertTask({
                id: liveTask.id,
                tagIds: next
              });
            }}
            onOpenManageTags={() => setTagManagerOpen(true)}
            onClose={() => setTagPickerTaskId(null)}
          />
        );
      })()}

      {batchTagPickerOpen && selectedTaskIds.length > 0 && (() => {
        const selectedRecords = selectedTaskIds
          .map((id) => tasksByIdMap.get(id))
          .filter((tk): tk is TaskRecord => tk != null);
        if (selectedRecords.length === 0) return null;

        const countsByTag = new Map<string, number>();
        for (const tk of selectedRecords) {
          for (const tid of tk.tagIds) {
            countsByTag.set(tid, (countsByTag.get(tid) ?? 0) + 1);
          }
        }
        const commonTagIds: string[] = [];
        const indeterminateTagIds: string[] = [];
        for (const [tid, count] of countsByTag.entries()) {
          if (count === selectedRecords.length) {
            commonTagIds.push(tid);
          } else if (count > 0) {
            indeterminateTagIds.push(tid);
          }
        }

        return (
          <TaskTagPickerModal
            selectedTagIds={commonTagIds}
            indeterminateTagIds={indeterminateTagIds}
            tags={snapshot.tags}
            usageCounts={tagUsageCounts}
            tagSortMode={tagSortMode}
            t={t}
            onToggleTag={(tagId) => {
              if (commonTagIds.includes(tagId)) {
                void localDb.batchUpdateTasks(selectedTaskIds, {
                  removeTagIds: [tagId]
                });
              } else {
                void localDb.batchUpdateTasks(selectedTaskIds, {
                  addTagIds: [tagId]
                });
              }
            }}
            onOpenManageTags={() => setTagManagerOpen(true)}
            onClose={() => setBatchTagPickerOpen(false)}
          />
        );
      })()}

      {dependencyModalTaskId && (() => {
        const liveDepTask = snapshot.tasks.find(
          (x) => x.id === dependencyModalTaskId && !x.isDeleted
        );
        if (!liveDepTask) return null;
        return (
          <TaskDependencyModal
            task={liveDepTask}
            allTasks={snapshot.tasks}
            projectsMap={allNonDeletedProjectsMap}
            t={t}
            onToast={showToast}
            onClose={() => setDependencyModalTaskId(null)}
          />
        );
      })()}

      {perspectiveModalState.open && (
        <PerspectiveModal
          perspective={perspectiveModalState.perspective}
          nextSortOrder={
            savedPerspectives.reduce((m, p) => Math.max(m, p.sortOrder), 0) + 1000
          }
          tags={snapshot.tags}
          areas={snapshot.areas}
          t={t}
          onSave={(saved) => {
            const exists = savedPerspectives.some((p) => p.id === saved.id);
            const nextList = exists
              ? savedPerspectives.map((p) => (p.id === saved.id ? saved : p))
              : [...savedPerspectives, saved];
            updatePrefs({ savedPerspectives: nextList, showPerspectives: true });
            setPerspectiveModalState({ open: false, perspective: null });
            handleSelectNav({ kind: 'perspective', perspectiveId: saved.id });
            showToast(`✓ ${saved.title}`);
          }}
          onDelete={
            perspectiveModalState.perspective
              ? (perspId) => {
                  const nextList = savedPerspectives.filter((p) => p.id !== perspId);
                  updatePrefs({ savedPerspectives: nextList });
                  setPerspectiveModalState({ open: false, perspective: null });
                  if (
                    activeNav.kind === 'perspective' &&
                    activeNav.perspectiveId === perspId
                  ) {
                    handleSelectNav({ kind: 'smart', view: 'TODAY' });
                  }
                  showToast(`✓ ${t.actionDelete}`);
                }
              : undefined
          }
          onClose={() => setPerspectiveModalState({ open: false, perspective: null })}
        />
      )}

      {areaManagerOpen && (
        <AreaManagerModal
          areas={snapshot.areas}
          t={t}
          onOpenArea={(areaId) => handleSelectNav({ kind: 'area', areaId })}
          onClose={() => setAreaManagerOpen(false)}
        />
      )}

      {quickCreateAreaOpen && (
        <QuickCreateAreaModal
          areasCount={activeAreas.length}
          t={t}
          onCreated={(areaId) => handleSelectNav({ kind: 'area', areaId })}
          onClose={() => setQuickCreateAreaOpen(false)}
        />
      )}

      {projectModalState.open && (
        <CreateOrEditProjectModal
          project={projectModalState.project}
          defaultAreaId={projectModalState.defaultAreaId}
          areas={snapshot.areas}
          t={t}
          onSaved={(projectId) => handleSelectNav({ kind: 'project', projectId })}
          onOpenTemplates={(areaId) =>
            setTemplatesModal({ open: true, tab: 'gallery', areaId })
          }
          onClose={() => setProjectModalState({ open: false })}
        />
      )}

      {weeklyReviewModal.open && (
        <WeeklyReviewModal
          initialStep={weeklyReviewModal.step}
          projects={snapshot.projects}
          tasks={snapshot.tasks}
          areasMap={areasMap}
          projectsMap={allNonDeletedProjectsMap}
          lastWeeklyReviewAt={
            snapshot.projects.reduce<number>(
              (acc, p) => (!p.isDeleted && p.lastReviewedAt && p.lastReviewedAt > acc ? p.lastReviewedAt : acc),
              prefs.lastWeeklyReviewAt ?? 0
            ) || undefined
          }
          lang={lang}
          t={t}
          onOpenProject={(projectId) => {
            setWeeklyReviewModal((prev) => ({ ...prev, open: false }));
            handleSelectNav({ kind: 'project', projectId });
          }}
          onOpenTask={(task) => {
            setWeeklyReviewModal((prev) => ({ ...prev, open: false }));
            if (task.projectId) {
              handleSelectNav({ kind: 'project', projectId: task.projectId }, task.id);
            } else if (task.areaId) {
              handleSelectNav({ kind: 'area', areaId: task.areaId }, task.id);
            } else if (task.scheduleState === 'TODAY' || task.scheduleState === 'THIS_EVENING') {
              handleSelectNav({ kind: 'smart', view: 'TODAY' }, task.id);
            } else if (task.scheduleState === 'UPCOMING') {
              handleSelectNav({ kind: 'smart', view: 'UPCOMING' }, task.id);
            } else if (task.scheduleState === 'SOMEDAY') {
              handleSelectNav({ kind: 'smart', view: 'SOMEDAY' }, task.id);
            } else if (task.scheduleState === 'ANYTIME') {
              handleSelectNav({ kind: 'smart', view: 'ANYTIME' }, task.id);
            } else {
              handleSelectNav({ kind: 'smart', view: 'INBOX' }, task.id);
            }
          }}
          onCompleteReview={() => {
            const now = Date.now();
            updatePrefs({ lastWeeklyReviewAt: now });
            void localDb.markAllActiveProjectsReviewed(now);
            setWeeklyReviewModal({ open: false, step: 1 });
            showToast(`🎉 ${t.weeklyReviewCompletedToast}`);
          }}
          onClose={() => setWeeklyReviewModal((prev) => ({ ...prev, open: false }))}
        />
      )}

      {templatesModal.open && (
        <TemplatesAndImportModal
          initialTab={templatesModal.tab}
          defaultAreaId={templatesModal.areaId}
          activeProjectId={activeNav.kind === 'project' ? activeNav.projectId : null}
          areas={snapshot.areas}
          projects={snapshot.projects}
          savedTemplates={snapshot.projectTemplates}
          lang={lang}
          t={t}
          onCreatedProject={(projectId) => {
            setTemplatesModal((prev) => ({ ...prev, open: false }));
            handleSelectNav({ kind: 'project', projectId });
          }}
          onToast={showToast}
          onClose={() => setTemplatesModal((prev) => ({ ...prev, open: false }))}
        />
      )}

      {tagManagerOpen && (
        <TagManagerModal
          tags={snapshot.tags}
          usageCounts={tagUsageCounts}
          tagSortMode={tagSortMode}
          t={t}
          onChangeTagSortMode={(mode) => updatePrefs({ tagSortMode: mode })}
          onToast={showToast}
          onClose={() => setTagManagerOpen(false)}
        />
      )}

      {notificationsModalOpen && (
        <NotificationSettingsModal
          prefs={prefs}
          t={t}
          onUpdatePrefs={updatePrefs}
          onToast={showToast}
          onClose={() => setNotificationsModalOpen(false)}
        />
      )}

      {geminiModalOpen && (
        <GeminiModal
          initialTab={geminiModalTab}
          areas={snapshot.areas}
          defaultAreaId={defaultAreaIdForNewProject}
          todayOpenTasks={[...todayDaytimeTasks, ...todayEveningTasks]}
          todayCalendarEvents={activeTodayCalendarEvents}
          workdayStartMinutes={workdayBounds.startMinutes}
          workdayEndMinutes={workdayBounds.endMinutes}
          lang={lang}
          geminiApiKey={prefs.geminiApiKey}
          t={t}
          onNavigateProject={(projectId) => handleSelectNav({ kind: 'project', projectId })}
          onAppliedDayPlan={() => {
            setActiveNav({ kind: 'smart', view: 'TODAY' });
            setTodayViewMode('timeline');
          }}
          onToast={showToast}
          onClose={() => setGeminiModalOpen(false)}
        />
      )}

      {settingsModalOpen && (
        <SettingsModal
          prefs={prefs}
          snapshot={snapshot}
          stats={statistics}
          syncState={syncState}
          hasCalendarPermission={hasCalendarPermission}
          availableCalendars={availableCalendars}
          calendarError={calendarError}
          t={t}
          onUpdatePrefs={updatePrefs}
          onToggleBiometricAppLock={handleToggleBiometricAppLock}
          onToggleCalendarEventsEnabled={handleToggleCalendarEventsEnabled}
          onRequestCalendarAccess={handleRequestCalendarAccess}
          onImportIcsFile={handleImportIcsFile}
          onRemoveImportedCalendar={handleRemoveImportedCalendar}
          onToggleSelectedCalendarId={handleToggleSelectedCalendarId}
          onSelectAllCalendars={handleSelectAllCalendars}
          onRefreshCalendars={handleRefreshCalendars}
          onOpenAuthModal={() => setAuthModalOpen(true)}
          onOpenNotificationsModal={() => setNotificationsModalOpen(true)}
          initialUpdateInfo={availableUpdate}
          onUpdateInfoChange={setAvailableUpdate}
          onToast={showToast}
          onClose={() => setSettingsModalOpen(false)}
        />
      )}

      {authModalOpen && (
        <AuthSyncModal
          syncState={syncState}
          hasSampleTasks={snapshot.hasSampleTasks}
          t={t}
          onEnableCloudMode={() => {
            updatePrefs({ localOnlyDbMode: false });
            cloudSync.onDatabaseModeChanged(false);
          }}
          onToast={showToast}
          onClose={() => setAuthModalOpen(false)}
        />
      )}

      {zenFocusOpen && focusTimer && (
        <ZenFocusModal
          timer={focusTimer}
          task={snapshot.tasks.find((tk) => tk.id === focusTimer.taskId && !tk.isDeleted) ?? null}
          checklistItems={snapshot.checklistItems}
          t={t}
          onTogglePause={() =>
            setFocusTimer((prev) =>
              prev ? { ...prev, isRunning: !prev.isRunning } : null
            )
          }
          onAddFiveMinutes={() =>
            setFocusTimer((prev) =>
              prev
                ? {
                    ...prev,
                    totalSeconds: prev.totalSeconds + 5 * 60,
                    remainingSeconds: prev.remainingSeconds + 5 * 60
                  }
                : null
            )
          }
          onStartBreak={handleStartFocusBreak}
          onBackToFocus={handleBackToFocusFromBreak}
          onCompleteTask={handleCompleteFocusTimerTask}
          onMinimize={() => setZenFocusOpen(false)}
          onStop={handleStopFocusTimer}
        />
      )}

      {quickShareDraft && (
        <QuickShareModal
          draft={quickShareDraft}
          projects={snapshot.projects}
          areas={snapshot.areas}
          tags={snapshot.tags}
          lang={lang}
          t={t}
          onClose={() => setQuickShareDraft(null)}
          onToast={showToast}
          onSmartParseWithGemini={async (rawText, destination) => {
            const parsed = await parseBrainDumpToTasks(rawText, lang, prefs.geminiApiKey);
            if (parsed.length === 0) return;
            const todayStart = startOfDayEpoch();
            let lastCreatedId: string | undefined;
            for (const dto of parsed) {
              const hasExplicitContainer =
                destination.projectId != null || destination.areaId != null;
              const useDestSchedule =
                !hasExplicitContainer &&
                destination.scheduleState !== 'INBOX' &&
                dto.scheduleState === 'TODAY' &&
                dto.whenDaysOffset === 0;
              const resolvedSchedule = useDestSchedule
                ? destination.scheduleState
                : hasExplicitContainer && dto.scheduleState === 'INBOX'
                  ? 'ANYTIME'
                  : dto.scheduleState;
              const whenDate = useDestSchedule
                ? resolvedSchedule === 'TODAY' || resolvedSchedule === 'THIS_EVENING'
                  ? todayStart
                  : null
                : dto.whenDaysOffset != null
                  ? addDaysLocal(todayStart, dto.whenDaysOffset)
                  : null;
              const deadline =
                dto.deadlineDaysOffset != null
                  ? addDaysLocal(todayStart, dto.deadlineDaysOffset)
                  : null;
              const created = await localDb.upsertTask({
                title: dto.title,
                notes: dto.notes,
                scheduleState: resolvedSchedule,
                whenDate,
                deadline,
                projectId: destination.projectId,
                areaId: destination.projectId ? null : destination.areaId
              });
              lastCreatedId = created.id;
              if (dto.checklist.length > 0) {
                await localDb.addChecklistItems(created.id, dto.checklist);
              }
            }
            setQuickShareDraft(null);
            if (destination.projectId) {
              handleSelectNav({ kind: 'project', projectId: destination.projectId }, lastCreatedId);
            } else if (destination.areaId) {
              handleSelectNav({ kind: 'area', areaId: destination.areaId }, lastCreatedId);
            } else {
              handleSelectNav({ kind: 'smart', view: 'TODAY' });
            }
            showToast(`✨ +${parsed.length} ${t.entityTodos}`);
          }}
          onSave={async (payload) => {
            const minOrder =
              openTasks.length > 0
                ? openTasks.reduce((m, tk) => Math.min(m, tk.sortOrder), Infinity)
                : null;
            const whenDate =
              payload.whenDate !== undefined
                ? payload.whenDate
                : payload.scheduleState === 'TODAY' || payload.scheduleState === 'THIS_EVENING'
                  ? startOfDayEpoch()
                  : null;
            const created = await localDb.upsertTask({
              title: payload.title,
              notes: payload.notes,
              scheduleState: payload.scheduleState,
              whenDate,
              reminderTime: payload.reminderTime ?? null,
              deadline: payload.deadline ?? null,
              durationMinutes: payload.durationMinutes ?? null,
              tagIds: payload.tagIds ?? [],
              isMilestone: payload.isMilestone,
              isInProgress: payload.isInProgress,
              projectId: payload.projectId,
              areaId: payload.projectId ? null : payload.areaId,
              sortOrder: computeMidpointSortOrder(
                null,
                minOrder != null && Number.isFinite(minOrder) ? minOrder : null
              )
            });
            setQuickShareDraft(null);
            if (payload.projectId) {
              handleSelectNav({ kind: 'project', projectId: payload.projectId }, created.id);
            } else if (payload.areaId) {
              handleSelectNav({ kind: 'area', areaId: payload.areaId }, created.id);
            } else {
              const targetView: SmartViewType =
                payload.scheduleState === 'THIS_EVENING'
                  ? 'TODAY'
                  : payload.scheduleState === 'TODAY' ||
                      payload.scheduleState === 'UPCOMING' ||
                      payload.scheduleState === 'ANYTIME' ||
                      payload.scheduleState === 'SOMEDAY'
                    ? payload.scheduleState
                    : 'INBOX';
              handleSelectNav({ kind: 'smart', view: targetView }, created.id);
            }
            showToast(`✓ ${t.quickShareTitle}`);
          }}
        />
      )}

      {pendingCompleteProject && (
        <div
          className="modal-backdrop"
          onClick={() => setPendingCompleteProject(null)}
        >
          <div
            className="modal-panel"
            data-testid="complete-project-confirm-modal"
            style={{ maxWidth: 440 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <CheckCircle2 size={18} style={{ color: 'var(--logbook-green)' }} />
                <span>{t.v31ProjectCompleteConfirmTitle}</span>
              </h3>
              <button
                type="button"
                className="icon-btn"
                title={t.actionCancel}
                onClick={() => setPendingCompleteProject(null)}
              >
                <X size={18} />
              </button>
            </div>
            <p
              style={{
                margin: '0 0 18px',
                fontSize: 13.5,
                color: 'var(--text-secondary)',
                lineHeight: 1.5
              }}
            >
              {t.v31ProjectCompleteConfirmBody(
                pendingCompleteProject.project.title || t.entityNewProject,
                pendingCompleteProject.openTasksCount
              )}
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button
                type="button"
                className="btn-primary"
                data-testid="confirm-complete-project-with-tasks"
                style={{
                  width: '100%',
                  padding: '9px 14px',
                  background: 'var(--logbook-green)',
                  borderColor: 'var(--logbook-green)'
                }}
                onClick={() =>
                  void executeCompleteProject(pendingCompleteProject.project.id, true)
                }
              >
                <CheckCircle2 size={14} />
                <span>{t.v31ProjectCompleteAllTodos}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                data-testid="confirm-complete-project-as-is"
                style={{ width: '100%', padding: '8.5px 14px' }}
                onClick={() =>
                  void executeCompleteProject(pendingCompleteProject.project.id, false)
                }
              >
                <span>{t.v31ProjectCompleteAsIs}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                data-testid="cancel-complete-project"
                style={{ width: '100%', padding: '8.5px 14px' }}
                onClick={() => setPendingCompleteProject(null)}
              >
                <span>{t.actionCancel}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {pendingDeleteProject && (
        <div
          className="modal-backdrop"
          onClick={() => setPendingDeleteProject(null)}
        >
          <div
            className="modal-panel"
            data-testid="delete-project-confirm-modal"
            style={{ maxWidth: 440 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 className="modal-title">
                {t.projectDeletePromptTitle(
                  pendingDeleteProject.project.title || t.entityNewProject
                )}
              </h3>
              <button
                type="button"
                className="icon-btn"
                title={t.actionCancel}
                onClick={() => setPendingDeleteProject(null)}
              >
                <X size={18} />
              </button>
            </div>
            <p
              style={{
                margin: '0 0 18px',
                fontSize: 13.5,
                color: 'var(--text-secondary)',
                lineHeight: 1.5
              }}
            >
              {t.projectDeletePromptTasksBody(pendingDeleteProject.associatedTasksCount)}
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button
                type="button"
                className="btn-danger"
                data-testid="confirm-delete-project-with-tasks"
                style={{ width: '100%', padding: '9px 14px' }}
                onClick={() =>
                  void executeDeleteProject(pendingDeleteProject.project.id, true)
                }
              >
                <Trash2 size={14} />
                <span>{t.projectDeleteWithTasksBtn}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                data-testid="confirm-delete-project-keep-tasks"
                style={{ width: '100%', padding: '8.5px 14px' }}
                onClick={() =>
                  void executeDeleteProject(pendingDeleteProject.project.id, false)
                }
              >
                <span>{t.projectDeleteKeepTasksBtn}</span>
              </button>
              <button
                type="button"
                className="btn-secondary"
                data-testid="cancel-delete-project"
                style={{ width: '100%', padding: '8.5px 14px' }}
                onClick={() => setPendingDeleteProject(null)}
              >
                <span>{t.actionCancel}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {emptyTrashConfirmOpen && (
        <EmptyTrashConfirmDialog
          count={deletedProjects.length + deletedTasks.length}
          t={t}
          onCancel={() => setEmptyTrashConfirmOpen(false)}
          onConfirm={() => {
            setEmptyTrashConfirmOpen(false);
            void localDb.emptyTrash();
          }}
        />
      )}

      {appLockSetupOpen && (
        <AppLockPinSetupModal
          t={t}
          onCancel={() => setAppLockSetupOpen(false)}
          onConfirm={(pinHash, webAuthnCredId) => {
            updatePrefs({
              biometricAppLockEnabled: true,
              appLockPinHash: pinHash,
              appLockWebAuthnCredId: webAuthnCredId
            });
            setAppLockSetupOpen(false);
            showToast(`🔒 ${t.settingsBiometricLockTitle}`);
          }}
        />
      )}

      {!isAnyTimelineFullScreen && (
        <ActionableReminderBanner
          reminders={inAppReminders}
          t={t}
          onComplete={(taskId) => void handleActionableReminderAction('complete', taskId)}
          onSnooze1h={(taskId) => void handleActionableReminderAction('snooze-1h', taskId)}
          onTomorrow={(taskId) => void handleActionableReminderAction('tomorrow', taskId)}
          onDismiss={(taskId) =>
            setInAppReminders((prev) => prev.filter((r) => r.taskId !== taskId))
          }
        />
      )}

      {!isAnyTimelineFullScreen && undoBanner && (
        <UndoSnackbarBanner
          key={undoBanner.id}
          message={undoBanner.message}
          t={t}
          onUndo={() => {
            setUndoBanner(null);
            void localDb.performUndo().then((restored) => {
              if (restored && undoNavRestoreRef.current) {
                const targetNav = undoNavRestoreRef.current;
                undoNavRestoreRef.current = null;
                handleSelectNav(targetNav);
              }
            });
          }}
          onDismiss={() => {
            setUndoBanner(null);
            undoNavRestoreRef.current = null;
            localDb.clearUndoSnapshot();
          }}
        />
      )}

      {toastMsg && !undoBanner && <div className="toast-banner">{toastMsg}</div>}

      {isAppLocked && prefs.biometricAppLockEnabled && (
        <AppLockOverlay
          pinHash={prefs.appLockPinHash}
          webAuthnCredId={prefs.appLockWebAuthnCredId}
          t={t}
          onUnlocked={() => setIsAppLocked(false)}
        />
      )}

      <ThingsTooltipHost />
    </div>
  );
};

export default App;
