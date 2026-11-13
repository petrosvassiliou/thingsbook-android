import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Archive,
  ArrowDown,
  ArrowUp,
  Bell,
  BookOpenCheck,
  CalendarDays,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ChevronUp,
  Cloud,
  Edit3,
  Flag,
  FolderInput,
  Globe,
  Heading as HeadingIcon,
  Inbox,
  Layers,
  Menu,
  Moon,
  Package,
  PanelLeftClose,
  Plus,
  Search,
  Settings,
  Sparkles,
  Star,
  Tag,
  Trash2,
  X
} from 'lucide-react';
import {
  addDaysLocal,
  computeMidpointSortOrder,
  computeUpcomingTargetDate,
  daysBetweenToday,
  endOfDayEpoch,
  resolveWhenPreset,
  startOfDayEpoch,
  type ActiveNavigation,
  type AppPreferences,
  type CalendarEventItem,
  type DeviceCalendarInfo,
  type ProjectRecord,
  type SmartViewType,
  type SupportedLang,
  type TagRecord,
  type TaskRecord
} from './domain/types';
import {
  clearLastCalendarError,
  connectGoogleCalendarPopup,
  getLastCalendarError,
  hasCalendarAccess,
  importIcsCalendarText,
  localEpochDayFromMs,
  queryAvailableCalendars,
  queryEventsForDateRange,
  removeImportedIcsCalendar
} from './data/calendarService';
import { cloudSync, type CloudSyncSnapshot } from './data/cloudSync';
import { generateLogbookReflection, type LogbookReviewResponse } from './data/geminiService';
import {
  computeStatistics,
  loadPreferences,
  localDb,
  savePreferences,
  type DataSnapshot
} from './data/localDb';
import { checkForSwAndReleaseUpdates } from './data/pwaUpdater';
import { checkDueRemindersAndDigest } from './data/reminderRuntime';
import { TRANSLATIONS, resolveLanguage } from './i18n/translations';
import { CalendarEventsBlock } from './components/CalendarEventsBlock';
import {
  AreaManagerModal,
  CreateOrEditProjectModal,
  MoveTaskModal,
  NotificationSettingsModal,
  QuickCreateAreaModal,
  TagManagerModal,
  TaskTagPickerModal
} from './components/EntityModals';
import { GeminiModal } from './components/GeminiModal';
import { InlineQuickFind } from './components/InlineQuickFind';
import { MagicPlusFab } from './components/MagicPlusFab';
import { ProjectProgressRing } from './components/ProjectProgressRing';
import { AuthSyncModal, SettingsModal } from './components/SettingsAndAuthModals';
import { Sidebar, SmartViewNavigationStrip } from './components/Sidebar';
import { TaskItem } from './components/TaskItem';
import { DeadlineModal, RepeatModal, WhenModal } from './components/WhenAndRepeatModals';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export const App: React.FC = () => {
  const [prefs, setPrefs] = useState<AppPreferences>(() => loadPreferences());
  const [snapshot, setSnapshot] = useState<DataSnapshot>(() => localDb.getSnapshot());
  const [syncState, setSyncState] = useState<CloudSyncSnapshot>(() => cloudSync.getState());

  const [isWideScreen, setIsWideScreen] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 700 : true
  );
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  const [activeNav, setActiveNav] = useState<ActiveNavigation>({
    kind: 'smart',
    view: 'TODAY'
  });
  const [selectedFilterTagId, setSelectedFilterTagId] = useState<string | null>(null);
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);
  const [expandedTaskId, setExpandedTaskId] = useState<string | null>(null);

  // Collapsible Project Headings & Inline Editing
  const [collapsedHeadingIds, setCollapsedHeadingIds] = useState<Set<string>>(new Set());
  const [editingHeadingId, setEditingHeadingId] = useState<string | null>(null);
  const [editingHeadingTitle, setEditingHeadingTitle] = useState('');
  const [dragOverHeadingId, setDragOverHeadingId] = useState<string | null>(null);

  // Area inline title editing & Project area dropdown
  const [editingAreaHeader, setEditingAreaHeader] = useState(false);
  const [editingAreaTitle, setEditingAreaTitle] = useState('');
  const [projectAreaDropdownOpen, setProjectAreaDropdownOpen] = useState(false);

  // Topbar Popovers, Magic Plus & Inline Quick Find
  const [langDropdownOpen, setLangDropdownOpen] = useState(false);
  const [quickFindOpen, setQuickFindOpen] = useState(false);
  const [magicFabOpen, setMagicFabOpen] = useState(false);

  // Modals (store entity IDs so modals always read live snapshot records and never overwrite titles)
  const [whenModalTaskId, setWhenModalTaskId] = useState<string | null>(null);
  const [deadlineModalTarget, setDeadlineModalTarget] = useState<
    { kind: 'task'; taskId: string } | { kind: 'project'; projectId: string } | null
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
  const [settingsModalOpen, setSettingsModalOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);

  // Logbook AI Reflection
  const [reflection, setReflection] = useState<LogbookReviewResponse | null>(null);
  const [loadingReflection, setLoadingReflection] = useState(false);
  const [reflectionError, setReflectionError] = useState<string | null>(null);
  const workspaceScrollRef = useRef<HTMLDivElement | null>(null);

  // ChromeOS Install Prompt & Toast
  const [deferredInstallPrompt, setDeferredInstallPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isStandalonePwa, setIsStandalonePwa] = useState<boolean>(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(display-mode: standalone)').matches
      : false
  );
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const lang: SupportedLang = useMemo(() => resolveLanguage(prefs.language), [prefs.language]);
  const t = TRANSLATIONS[lang];

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
  }, []);

  useEffect(() => {
    if (!toastMsg) return;
    const timer = window.setTimeout(() => setToastMsg(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toastMsg]);

  const updatePrefs = useCallback((patch: Partial<AppPreferences>) => {
    setPrefs((prev) => {
      const next = { ...prev, ...patch };
      savePreferences(next);
      return next;
    });
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

  // 700px Responsive Breakpoint Listener
  useEffect(() => {
    const onResize = () => setIsWideScreen(window.innerWidth >= 700);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // Initialize DB, Cloud Sync, Launcher Shortcuts, Auto Update Check, and PWA Install Listener
  useEffect(() => {
    const unsubDb = localDb.subscribe(setSnapshot);
    const unsubSync = cloudSync.subscribe(setSyncState);

    void localDb.initialize(prefs.language).then(async (initialSnap) => {
      void cloudSync.initializeSession();

      // Handle ChromeOS Launcher Manifest Shortcuts (?action=new-todo | ?action=quick-find | ?view=...)
      const params = new URLSearchParams(window.location.search);
      const viewParam = params.get('view')?.toUpperCase();
      const actionParam = params.get('action')?.toLowerCase();
      const validViews: SmartViewType[] = [
        'INBOX',
        'TODAY',
        'UPCOMING',
        'ANYTIME',
        'SOMEDAY',
        'LOGBOOK'
      ];

      if (viewParam && (validViews as string[]).includes(viewParam)) {
        setActiveNav({ kind: 'smart', view: viewParam as SmartViewType });
      }
      if (actionParam === 'quick-find') {
        setQuickFindOpen(true);
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
      if (viewParam || actionParam) {
        window.history.replaceState({}, '', window.location.pathname);
      }
    });

    if (prefs.autoCheckUpdatesEnabled) {
      void checkForSwAndReleaseUpdates();
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
      checkDueRemindersAndDigest(snapshot.tasks, prefs, (taskId) => {
        setSelectedTaskIds([]);
        setExpandedTaskId(taskId);
      });
    };
    tick();
    const intervalId = window.setInterval(tick, 30_000);
    return () => window.clearInterval(intervalId);
  }, [snapshot.tasks, prefs]);

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

  // ==================== Exact Android v1.6.19 Smart View Queries ====================
  const openTasks = useMemo(
    () => snapshot.tasks.filter((tk) => !tk.isDeleted && tk.status === 'OPEN'),
    [snapshot.tasks]
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
      someday: somedayTasks.length
    }),
    [inboxTasks, todayDaytimeTasks, todayEveningTasks, upcomingTasks, anytimeTasks, somedayTasks]
  );

  const statistics = useMemo(() => computeStatistics(snapshot, endOfToday), [snapshot, endOfToday]);

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
    const onFocusRefresh = () => setCalendarRefreshTick((n) => n + 1);
    const onVisibilityRefresh = () => {
      if (document.visibilityState === 'visible') {
        setCalendarRefreshTick((n) => n + 1);
      }
    };
    window.addEventListener('focus', onFocusRefresh);
    document.addEventListener('visibilitychange', onVisibilityRefresh);
    return () => {
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

      setTodayCalendarEvents(eventsByDay.get(todayEpochDay) ?? []);
      const upcomingMap = new Map<number, CalendarEventItem[]>();
      for (const [day, list] of eventsByDay.entries()) {
        if (day >= todayEpochDay + 1 && list.length > 0) {
          upcomingMap.set(day, list);
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
      }
    }
    if (activeNav.kind === 'area') {
      return openTasks.filter(
        (tk) => tk.areaId === activeNav.areaId && tk.projectId == null
      );
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
    openTasks
  ]);

  // Auto-prune completed/canceled/deleted/moved task IDs out of multi-selection (ThingsbookViewModel.kt v1.6.17+)
  useEffect(() => {
    if (selectedTaskIds.length === 0) return;
    const visibleIds = new Set(currentViewBaseTasks.map((tk) => tk.id));
    setSelectedTaskIds((prev) => {
      const next = prev.filter((id) => visibleIds.has(id));
      return next.length === prev.length ? prev : next;
    });
  }, [currentViewBaseTasks, selectedTaskIds.length]);

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

  // Preselected Area ID for New Project (current Area, or current Project's parent Area — v1.6.18 parity)
  const defaultAreaIdForNewProject = useMemo((): string | null => {
    if (activeNav.kind === 'area') return activeNav.areaId;
    if (activeNav.kind === 'project') {
      return allNonDeletedProjectsMap.get(activeNav.projectId)?.areaId ?? null;
    }
    return null;
  }, [activeNav, allNonDeletedProjectsMap]);

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
      const isLogbookRedirect =
        activeNav.kind === 'smart' && activeNav.view === 'LOGBOOK';

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
          // INBOX and LOGBOOK both create an INBOX to-do
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

      const created = await localDb.upsertTask({
        title: '',
        scheduleState,
        whenDate,
        projectId,
        headingId,
        areaId,
        tagIds: !isLogbookRedirect && selectedFilterTagId ? [selectedFilterTagId] : [],
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
      selectedFilterTagId
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

  // Global Keyboard Shortcuts + Ordered Escape Stack (Android v1.6.18 MainActivity + ThingsSmartViewScreen parity)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const inInput =
        target != null &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable);

      if (e.key === 'Escape') {
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
        if (langDropdownOpen) return setLangDropdownOpen(false);
        if (projectAreaDropdownOpen) return setProjectAreaDropdownOpen(false);
        if (quickFindOpen) return setQuickFindOpen(false);
        if (expandedTaskId) return setExpandedTaskId(null);
        if (selectedTaskIds.length > 0) return setSelectedTaskIds([]);
        if (mobileDrawerOpen) return setMobileDrawerOpen(false);
        return;
      }

      const lower = e.key.toLowerCase();

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
          setGeminiModalOpen(true);
          return;
        }
        if (
          lower === 'a' &&
          !e.shiftKey &&
          !inInput &&
          !expandedTaskId &&
          !projectModalState.open &&
          !moveModalTaskIds
        ) {
          const allIds = filterByTag(currentViewBaseTasks).map((x) => x.id);
          if (allIds.length > 0) {
            e.preventDefault();
            setSelectedTaskIds(allIds);
          }
          return;
        }
        if (!inInput && ['1', '2', '3', '4', '5', '6'].includes(lower)) {
          e.preventDefault();
          const views: SmartViewType[] = [
            'INBOX',
            'TODAY',
            'UPCOMING',
            'ANYTIME',
            'SOMEDAY',
            'LOGBOOK'
          ];
          setActiveNav({ kind: 'smart', view: views[Number(lower) - 1] });
          return;
        }

        // Ctrl+<TagShortcut> toggles tag on expanded or selected tasks!
        const targetIds = expandedTaskId
          ? [expandedTaskId]
          : selectedTaskIds;
        if (targetIds.length > 0 && lower.length === 1) {
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
    langDropdownOpen,
    projectAreaDropdownOpen,
    quickFindOpen,
    expandedTaskId,
    selectedTaskIds,
    mobileDrawerOpen,
    createNewTodoInCurrentContext,
    defaultAreaIdForNewProject,
    filterByTag,
    currentViewBaseTasks,
    snapshot.tags,
    snapshot.tasks
  ]);

  const handleSelectNav = (nav: ActiveNavigation, highlightTaskId?: string) => {
    setActiveNav(nav);
    setMobileDrawerOpen(false);
    setEditingAreaHeader(false);
    setProjectAreaDropdownOpen(false);
    setSelectedTaskIds([]);
    if (highlightTaskId) {
      setExpandedTaskId(highlightTaskId);
    } else {
      setExpandedTaskId(null);
    }
  };

  // Drag-and-drop task onto Sidebar target
  const handleDropTaskOnSidebarTarget = async (
    taskId: string,
    target:
      | { kind: 'smart'; view: SmartViewType }
      | { kind: 'area'; areaId: string }
      | { kind: 'project'; projectId: string }
  ) => {
    const tk = snapshot.tasks.find((x) => x.id === taskId);
    if (!tk) return;

    if (target.kind === 'area') {
      await localDb.upsertTask({
        id: tk.id,
        areaId: target.areaId,
        projectId: null,
        headingId: null
      });
    } else if (target.kind === 'project') {
      await localDb.upsertTask({
        id: tk.id,
        projectId: target.projectId,
        headingId: null,
        areaId: null
      });
    } else {
      if (target.view === 'LOGBOOK') {
        await localDb.upsertTask({ id: tk.id, status: 'COMPLETED' });
      } else if (target.view === 'INBOX') {
        await localDb.upsertTask({
          id: tk.id,
          scheduleState: 'INBOX',
          whenDate: null,
          projectId: null,
          headingId: null,
          areaId: null
        });
      } else {
        const resolved = resolveWhenPreset(target.view, '');
        await localDb.upsertTask({
          id: tk.id,
          scheduleState: resolved.scheduleState,
          whenDate: resolved.whenDate
        });
      }
    }
  };

  // Drag-and-drop reorder between two tasks (including cross-heading in Project view)
  const handleDropReorderTask = async (
    draggedId: string,
    targetId: string,
    listContext: TaskRecord[]
  ) => {
    const dragged = snapshot.tasks.find((x) => x.id === draggedId);
    const target = snapshot.tasks.find((x) => x.id === targetId);
    if (!dragged || !target) return;

    const idx = listContext.findIndex((x) => x.id === targetId);
    const prevOrder = idx > 0 ? listContext[idx - 1].sortOrder : null;
    const nextOrder = target.sortOrder;
    const newOrder = computeMidpointSortOrder(prevOrder, nextOrder);

    await localDb.upsertTask({
      id: dragged.id,
      projectId: target.projectId,
      headingId: target.headingId,
      areaId: target.areaId,
      sortOrder: newOrder
    });
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
    showContextSubtitle: boolean
  ) => (
    <TaskItem
      key={tk.id}
      task={tk}
      isExpanded={expandedTaskId === tk.id}
      isSelected={selectedTaskIds.includes(tk.id)}
      isMultiSelectMode={selectedTaskIds.length > 0}
      showContextSubtitle={showContextSubtitle}
      showScheduleBadgeInCollapsed={activeNav.kind === 'project' || activeNav.kind === 'area'}
      quickFindOpen={quickFindOpen}
      currentDayMarker={endOfToday}
      areasMap={areasMap}
      projectsMap={allNonDeletedProjectsMap}
      tagsMap={tagsMap}
      checklistItems={snapshot.checklistItems}
      lang={lang}
      geminiApiKey={prefs.geminiApiKey}
      t={t}
      onSelect={(multiToggle) => {
        if (multiToggle) {
          setSelectedTaskIds((prev) =>
            prev.includes(tk.id) ? prev.filter((id) => id !== tk.id) : [...prev, tk.id]
          );
        } else {
          setSelectedTaskIds([tk.id]);
        }
      }}
      onExpand={() => {
        preferBottomForExpandedTaskRef.current = false;
        setSelectedTaskIds([]);
        setExpandedTaskId(tk.id);
      }}
      onCollapse={() => setExpandedTaskId(null)}
      onRequestEnsureVisible={handleRequestEnsureTaskVisible}
      onOpenWhenModal={(taskId) => setWhenModalTaskId(taskId)}
      onOpenDeadlineModal={(taskId) => setDeadlineModalTarget({ kind: 'task', taskId })}
      onOpenRepeatModal={(taskId) => setRepeatModalTaskId(taskId)}
      onOpenMoveModal={(taskId) => setMoveModalTaskIds([taskId])}
      onOpenTagsModal={(taskId) => setTagPickerTaskId(taskId)}
      onDropReorder={(draggedId, targetId) =>
        void handleDropReorderTask(draggedId, targetId, listContext)
      }
      onToast={showToast}
    />
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
      }
    }
    if (activeNav.kind === 'area') {
      return areasMap.get(activeNav.areaId)?.title || t.entityArea;
    }
    return allNonDeletedProjectsMap.get(activeNav.projectId)?.title || t.entityProject;
  }, [activeNav, areasMap, allNonDeletedProjectsMap, t]);

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

  // Group Upcoming tasks by computeUpcomingTargetDate (re-evaluated across 00:00 midnight via endOfToday)
  const upcomingGroups = useMemo(() => {
    void endOfToday;
    const filtered = filterByTag(upcomingTasks);
    const map = new Map<number, TaskRecord[]>();
    for (const tk of filtered) {
      const targetDay = computeUpcomingTargetDate(tk);
      const list = map.get(targetDay) || [];
      list.push(tk);
      map.set(targetDay, list);
    }
    return Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
  }, [upcomingTasks, filterByTag, endOfToday]);

  const effectiveSidebarVisible = isWideScreen ? prefs.sidebarVisible : mobileDrawerOpen;

  return (
    <div
      className="app-shell"
      onClick={() => {
        if (
          expandedTaskId &&
          !whenModalTaskId &&
          !deadlineModalTarget &&
          !repeatModalTaskId &&
          !moveModalTaskIds &&
          !tagPickerTaskId
        ) {
          setExpandedTaskId(null);
        } else if (selectedTaskIds.length > 0 && !expandedTaskId) {
          setSelectedTaskIds([]);
        }
      }}
    >
      {/* Left Sidebar / Mobile Drawer */}
      <Sidebar
        visible={effectiveSidebarVisible}
        activeNav={activeNav}
        areas={snapshot.areas}
        projects={snapshot.projects}
        tasks={snapshot.tasks}
        counts={sidebarCounts}
        canInstallPwa={Boolean(deferredInstallPrompt)}
        isStandalonePwa={isStandalonePwa}
        t={t}
        onSelectNav={(nav) => handleSelectNav(nav)}
        onCloseMobile={() => setMobileDrawerOpen(false)}
        onCreateProjectInArea={(areaId) =>
          setProjectModalState({ open: true, project: null, defaultAreaId: areaId })
        }
        onOpenManageAreas={() => setAreaManagerOpen(true)}
        onDropTaskOnTarget={(taskId, target) => void handleDropTaskOnSidebarTarget(taskId, target)}
        onTriggerInstallPwa={() => void handleTriggerInstallPwa()}
      />

      {/* Right Workspace */}
      <main className="workspace">
        {/* TopThingsbookBar */}
        <header className="workspace-topbar" onClick={(e) => e.stopPropagation()}>
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
              title={effectiveSidebarVisible ? t.sidebarHide : t.sidebarShow}
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
              title={`${t.quickFindTitle} (Ctrl+F)`}
              onClick={() => setQuickFindOpen((v) => !v)}
            >
              <Search size={14} />
              <span className="topbar-label-hide-xs">{t.quickFindTitle}</span>
              <kbd className="topbar-label-hide-sm">Ctrl+F</kbd>
            </button>
          </div>

          <div className="topbar-right">
            {/* Gemini Hub Pill */}
            <button
              type="button"
              className="topbar-pill gemini-pill"
              onClick={() => setGeminiModalOpen(true)}
              title="Ctrl+G"
            >
              <Sparkles size={14} />
              <span className="topbar-label-hide-sm">Gemini</span>
            </button>

            {/* Areas Icon */}
            <button
              type="button"
              className="icon-btn"
              title={t.areasManageTitle}
              onClick={() => setAreaManagerOpen(true)}
            >
              <Package size={17} />
            </button>

            {/* Tags Icon */}
            <button
              type="button"
              className="icon-btn"
              title={t.tagsManageTitle}
              onClick={() => setTagManagerOpen(true)}
            >
              <Tag size={17} />
            </button>

            {/* Notifications Icon (blue when dailyDigestEnabled) */}
            <button
              type="button"
              className={`icon-btn ${prefs.dailyDigestEnabled ? 'active' : ''}`}
              title={t.notificationsTitle}
              onClick={() => setNotificationsModalOpen(true)}
            >
              <Bell size={17} />
            </button>

            {/* Language Dropdown */}
            <div className="dropdown-anchor">
              <button
                type="button"
                className="icon-btn"
                title={t.settingsLanguage}
                onClick={() => setLangDropdownOpen((v) => !v)}
              >
                <Globe size={17} />
              </button>

              {langDropdownOpen && (
                <>
                  <div
                    style={{ position: 'fixed', inset: 0, zIndex: 70 }}
                    onClick={() => setLangDropdownOpen(false)}
                  />
                  <div className="dropdown-popover">
                    {(
                      [
                        ['system', t.settingsSystemDefault],
                        ['en', '🇬🇧 English'],
                        ['de', '🇩🇪 Deutsch'],
                        ['it', '🇮🇹 Italiano'],
                        ['fr', '🇫🇷 Français'],
                        ['es', '🇪🇸 Español']
                      ] as Array<[SupportedLang | 'system', string]>
                    ).map(([code, label]) => (
                      <button
                        key={code}
                        type="button"
                        className="dropdown-item"
                        onClick={() => {
                          updatePrefs({ language: code });
                          setLangDropdownOpen(false);
                        }}
                      >
                        <span>{label}</span>
                        {prefs.language === code && <Check size={14} />}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>

            {/* Cloud Sync Icon */}
            <button
              type="button"
              className="icon-btn sync-icon-btn"
              title={
                syncState.status === 'SYNCED'
                  ? t.syncStatusSynced
                  : syncState.status === 'SYNCING'
                    ? t.syncStatusSyncing
                    : t.syncStatusOffline
              }
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

            {/* Pinned Far-Right Settings Icon */}
            <button
              type="button"
              className="icon-btn"
              title={t.settingsTitle}
              onClick={() => setSettingsModalOpen(true)}
            >
              <Settings size={17.5} />
            </button>
          </div>
        </header>

        {/* Horizontal SmartViewNavigationStrip when >= 700px and Sidebar Collapsed */}
        {isWideScreen && !prefs.sidebarVisible && (
          <SmartViewNavigationStrip
            activeNav={activeNav}
            areas={snapshot.areas}
            projects={snapshot.projects}
            tasks={snapshot.tasks}
            counts={sidebarCounts}
            t={t}
            isSoftKeyboardOpen={isSoftKeyboardOpen}
            onSelectNav={(nav) => handleSelectNav(nav)}
            onCreateProject={() =>
              setProjectModalState({
                open: true,
                project: null,
                defaultAreaId: defaultAreaIdForNewProject
              })
            }
            onOpenManageAreas={() => setAreaManagerOpen(true)}
          />
        )}

        {/* Inline Quick Find Section directly under Top Bar */}
        {quickFindOpen && (
          <InlineQuickFind
            areas={snapshot.areas}
            projects={snapshot.projects}
            tasks={snapshot.tasks}
            checklistItems={snapshot.checklistItems}
            tags={snapshot.tags}
            lang={lang}
            t={t}
            onNavigate={(nav, highlightId) => handleSelectNav(nav, highlightId)}
            onClose={() => setQuickFindOpen(false)}
          />
        )}

        {/* Main Scrollable View Content */}
        <div
          ref={workspaceScrollRef}
          className="workspace-scroll"
          style={
            isSoftKeyboardOpen
              ? { paddingBottom: `${softKeyboardInsetPx + 28}px` }
              : undefined
          }
        >
          <div className="workspace-container">
            {/* Welcome Setup Banner (only when sample tasks exist & not dismissed & not signed in) */}
            {snapshot.hasSampleTasks &&
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
                      onClick={() => void localDb.purgeSampleDataLocally(false)}
                    >
                      <Trash2 size={14} />
                      <span>{t.setupBannerDeleteSamplesBtn}</span>
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      onClick={() => updatePrefs({ setupBannerDismissed: true })}
                    >
                      <X size={15} />
                    </button>
                  </div>
                </div>
              )}

            {/* ==================== SMART VIEWS ==================== */}
            {activeNav.kind === 'smart' && (
              <>
                <div className="view-header">
                  <div className="view-title-row">
                    <div className="view-title-left">
                      {activeNav.view === 'INBOX' && (
                        <Inbox size={26} style={{ color: 'var(--things-blue)' }} />
                      )}
                      {activeNav.view === 'TODAY' && (
                        <Star
                          size={26}
                          fill="var(--today-star)"
                          style={{ color: 'var(--today-star)' }}
                        />
                      )}
                      {activeNav.view === 'UPCOMING' && (
                        <CalendarDays size={26} style={{ color: 'var(--upcoming-coral)' }} />
                      )}
                      {activeNav.view === 'ANYTIME' && (
                        <Layers size={26} style={{ color: 'var(--anytime-teal)' }} />
                      )}
                      {activeNav.view === 'SOMEDAY' && (
                        <Archive size={26} style={{ color: 'var(--someday-amber)' }} />
                      )}
                      {activeNav.view === 'LOGBOOK' && (
                        <BookOpenCheck size={26} style={{ color: 'var(--logbook-green)' }} />
                      )}
                      <h1 className="view-title">{currentViewTitle}</h1>
                    </div>
                  </div>
                </div>

                {/* View-Scoped Tag Filter Strip (Auto-hidden when empty) */}
                {usedTagsInCurrentView.length > 0 && (
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
                        <Tag size={11} />
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

                {/* TODAY View (Daytime + 🌙 This Evening) */}
                {activeNav.view === 'TODAY' && (() => {
                  const dayFiltered = filterByTag(todayDaytimeTasks);
                  const eveFiltered = filterByTag(todayEveningTasks);
                  if (dayFiltered.length === 0 && eveFiltered.length === 0) {
                    return <div className="empty-state">{t.emptyToday}</div>;
                  }
                  return (
                    <>
                      {prefs.calendarEventsEnabled && todayCalendarEvents.length > 0 && (
                        <CalendarEventsBlock events={todayCalendarEvents} t={t} />
                      )}
                      {dayFiltered.length > 0 && (
                        <div className="group-section">
                          <div className="task-list">
                            {dayFiltered.map((tk) => renderTaskRow(tk, dayFiltered, true))}
                          </div>
                        </div>
                      )}
                      {eveFiltered.length > 0 && (
                        <div className="group-section">
                          <div className="group-header">
                            <div className="group-header-title evening">
                              <Moon size={15} />
                              <span>{t.viewThisEvening}</span>
                            </div>
                          </div>
                          <div className="task-list">
                            {eveFiltered.map((tk) => renderTaskRow(tk, eveFiltered, true))}
                          </div>
                        </div>
                      )}
                    </>
                  );
                })()}

                {/* UPCOMING View (Day-by-Day Groups — v1.6.22: only dates with tasks display calendar events) */}
                {activeNav.view === 'UPCOMING' && (
                  <>
                    {upcomingGroups.length === 0 ? (
                      <div className="empty-state">{t.emptyView}</div>
                    ) : (
                      upcomingGroups.map(([dayEpoch, groupTasks]) => {
                        const hdr = formatUpcomingHeader(dayEpoch);
                        const dayEvents = prefs.calendarEventsEnabled
                          ? upcomingCalendarEventsByEpochDay.get(
                              localEpochDayFromMs(dayEpoch)
                            ) ?? []
                          : [];
                        return (
                          <div key={dayEpoch} className="group-section">
                            <div className="upcoming-day-header">
                              <span className="upcoming-day-num">{hdr.numPart}</span>
                              <span className="upcoming-day-sub">{hdr.subPart}</span>
                            </div>
                            {groupTasks.length > 0 && dayEvents.length > 0 && (
                              <CalendarEventsBlock events={dayEvents} t={t} compactTop />
                            )}
                            <div className="task-list">
                              {groupTasks.map((tk) => renderTaskRow(tk, groupTasks, true))}
                            </div>
                          </div>
                        );
                      })
                    )}
                  </>
                )}

                {/* INBOX / ANYTIME / SOMEDAY / LOGBOOK */}
                {activeNav.view !== 'TODAY' && activeNav.view !== 'UPCOMING' && (() => {
                  const list = filterByTag(currentViewBaseTasks);
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
              const areaDirectTasks = filterByTag(currentViewBaseTasks);

              return (
                <>
                  <div className="view-header" onClick={(e) => e.stopPropagation()}>
                    <div className="view-title-row">
                      <div className="view-title-left">
                        <Package size={26} style={{ color: 'var(--anytime-teal)', flexShrink: 0 }} />
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
                          <h1 className="view-title">{area.title}</h1>
                        )}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={areaIdx <= 0}
                          onClick={() => localDb.moveAreaOrder(area.id, 'up')}
                        >
                          <ArrowUp size={16} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={areaIdx < 0 || areaIdx >= activeAreas.length - 1}
                          onClick={() => localDb.moveAreaOrder(area.id, 'down')}
                        >
                          <ArrowDown size={16} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
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
                  </div>

                  {/* View Tag Strip */}
                  {usedTagsInCurrentView.length > 0 && (
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
                          <Tag size={11} />
                          <span>{tg.title}</span>
                        </button>
                      ))}
                    </div>
                  )}

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
                          const pDone = pTasks.filter((tk) => tk.status === 'COMPLETED').length;
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
                                  total={pTasks.length}
                                  size={20}
                                />
                                <div style={{ minWidth: 0 }}>
                                  <div style={{ fontWeight: 650, fontSize: 14.3 }}>
                                    {proj.title}
                                  </div>
                                  <div style={{ fontSize: 11.8, color: 'var(--text-muted)' }}>
                                    {pDone}/{pTasks.length} • {t.entityTodos}
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
                                  setDeadlineModalTarget({ kind: 'project', projectId: proj.id })
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
                                onClick={() =>
                                  localDb.moveProjectOrder(proj.id, 'down', area.id)
                                }
                              >
                                <ArrowDown size={14} />
                              </button>
                              <button
                                type="button"
                                className="icon-btn"
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
              );
            })()}

            {/* ==================== PROJECT DETAIL VIEW ==================== */}
            {activeNav.kind === 'project' && (() => {
              const project = allNonDeletedProjectsMap.get(activeNav.projectId);
              if (!project) {
                return <div className="empty-state">{t.emptyView}</div>;
              }
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

              const projHeadings = snapshot.headings.filter(
                (h) => !h.isDeleted && !h.isArchived && h.projectId === project.id
              );
              const validHeadingIdSet = new Set(projHeadings.map((h) => h.id));

              const visibleOpenProjTasks = filterByTag(currentViewBaseTasks);
              const ungroupedTasks = visibleOpenProjTasks.filter(
                (tk) => !tk.headingId || !validHeadingIdSet.has(tk.headingId)
              );

              const dlDiff =
                project.deadline != null ? daysBetweenToday(project.deadline) : null;
              const projArea = project.areaId ? areasMap.get(project.areaId) : undefined;

              return (
                <>
                  {/* Project Header */}
                  <div className="view-header" onClick={(e) => e.stopPropagation()}>
                    {/* Row 1: ProgressRing(26) + Title + Up/Down + Edit + Complete + Delete */}
                    <div className="view-title-row">
                      <div className="view-title-left">
                        <ProjectProgressRing
                          completed={doneProjTasks}
                          total={allProjTasks.length}
                          size={26}
                          isProjectCompleted={project.status === 'COMPLETED'}
                        />
                        <h1 className="view-title">{project.title}</h1>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={projIdx <= 0}
                          onClick={() =>
                            localDb.moveProjectOrder(project.id, 'up', project.areaId)
                          }
                        >
                          <ArrowUp size={15.5} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={
                            projIdx < 0 || projIdx >= siblingProjects.length - 1
                          }
                          onClick={() =>
                            localDb.moveProjectOrder(project.id, 'down', project.areaId)
                          }
                        >
                          <ArrowDown size={15.5} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          title={t.actionEdit}
                          onClick={() =>
                            setProjectModalState({ open: true, project })
                          }
                        >
                          <Edit3 size={15.5} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          style={{ color: 'var(--logbook-green)' }}
                          title={t.actionComplete}
                          onClick={async () => {
                            await localDb.upsertProject({
                              id: project.id,
                              status: 'COMPLETED'
                            });
                            handleSelectNav({ kind: 'smart', view: 'LOGBOOK' });
                          }}
                        >
                          <CheckCircle2 size={16.5} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          style={{ color: 'var(--deadline-red)' }}
                          title={t.actionDelete}
                          onClick={async () => {
                            await localDb.deleteProject(project.id);
                            handleSelectNav({ kind: 'smart', view: 'TODAY' });
                          }}
                        >
                          <Trash2 size={15.5} />
                        </button>
                      </div>
                    </div>

                    {/* Row 2: Project Notes */}
                    {project.notes.trim() && (
                      <p className="project-notes-display">{project.notes.trim()}</p>
                    )}

                    {/* Row 3: Action Pills (Target Date, Assign Area Dropdown, + To-Do, + Heading) */}
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
                          onClick={() => setProjectAreaDropdownOpen((v) => !v)}
                        >
                          <Package size={12.5} />
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
                        onClick={() => void createNewTodoInCurrentContext(null)}
                      >
                        <Plus size={12.5} />
                        <span>{t.entityTodo}</span>
                      </button>

                      <button
                        type="button"
                        className="chip-btn"
                        onClick={() => void createNewHeadingInActiveProject()}
                      >
                        <HeadingIcon size={12.5} />
                        <span>+ {t.entityHeading}</span>
                      </button>
                    </div>
                  </div>

                  {/* View Tag Strip */}
                  {usedTagsInCurrentView.length > 0 && (
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
                          <Tag size={11} />
                          <span>{tg.title}</span>
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Ungrouped Tasks at Top */}
                  {ungroupedTasks.length > 0 && (
                    <div className="group-section">
                      <div className="task-list">
                        {ungroupedTasks.map((tk) =>
                          renderTaskRow(tk, ungroupedTasks, false)
                        )}
                      </div>
                    </div>
                  )}

                  {/* Collapsible Project Headings with 4 Trailing Buttons & Drop Target */}
                  {projHeadings.map((hd) => {
                    const hdTasks = visibleOpenProjTasks.filter(
                      (tk) => tk.headingId === hd.id
                    );
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
                      <div key={hd.id} className="group-section">
                        <div
                          className={`project-heading-header ${
                            dragOverHeadingId === hd.id ? 'drag-over' : ''
                          }`}
                          onClick={(e) => e.stopPropagation()}
                          onDragOver={(e) => {
                            if (
                              e.dataTransfer.types.includes(
                                'application/x-thingsbook-task'
                              )
                            ) {
                              e.preventDefault();
                              setDragOverHeadingId(hd.id);
                            }
                          }}
                          onDragLeave={() => {
                            if (dragOverHeadingId === hd.id) setDragOverHeadingId(null);
                          }}
                          onDrop={async (e) => {
                            e.preventDefault();
                            setDragOverHeadingId(null);
                            const draggedId = e.dataTransfer.getData(
                              'application/x-thingsbook-task'
                            );
                            const tk = snapshot.tasks.find((x) => x.id === draggedId);
                            if (tk) {
                              await localDb.upsertTask({
                                id: tk.id,
                                projectId: project.id,
                                headingId: hd.id,
                                areaId: null
                              });
                              // Auto-expand collapsed heading on drop
                              if (isCollapsed) {
                                setCollapsedHeadingIds((prev) => {
                                  const next = new Set(prev);
                                  next.delete(hd.id);
                                  return next;
                                });
                              }
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
                              <span>{hd.title || t.entityHeading}</span>
                              {isCollapsed && hdTasks.length > 0 && (
                                <span className="meta-badge">{hdTasks.length}</span>
                              )}
                            </button>
                          )}

                          {/* Exact 4 Trailing Icon Buttons: +, Rename, Delete, Expand/Collapse */}
                          <div className="project-heading-actions">
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
              );
            })()}
          </div>
        </div>

        {/* Floating Multi-Selection Bar (ThingsMultiSelectionBar) */}
        {selectedTaskIds.length > 0 && !expandedTaskId && (() => {
          const allVisibleIds = filterByTag(currentViewBaseTasks).map((x) => x.id);
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
                  onClick={() => setSelectedTaskIds(allVisibleIds)}
                >
                  {t.multiSelectAll}
                </button>
              )}

              <button
                type="button"
                className="chip-btn"
                onClick={async () => {
                  await localDb.batchUpdateTasks(selectedTaskIds, {
                    scheduleState: 'TODAY',
                    whenDate: startOfDayEpoch()
                  });
                  setSelectedTaskIds([]);
                }}
              >
                <Star size={12} fill="var(--today-star)" style={{ color: 'var(--today-star)' }} />
                <span>{t.viewToday}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
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
                onClick={() => setMoveModalTaskIds(selectedTaskIds)}
              >
                <FolderInput size={12} />
                <span>{t.actionMove}</span>
              </button>

              <button
                type="button"
                className="chip-btn"
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

        {/* Context-Aware Magic Plus FAB */}
        <MagicPlusFab
          open={magicFabOpen}
          activeNav={activeNav}
          currentViewTitle={currentViewTitle}
          activeAreasCount={activeAreas.length}
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
          onNewArea={() => setQuickCreateAreaOpen(true)}
        />
      </main>

      {/* ==================== MODALS ==================== */}
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
        if (deadlineModalTarget.kind === 'project' && !liveProject) return null;
        if (deadlineModalTarget.kind === 'task' && !liveTask) return null;

        return (
          <DeadlineModal
            titleLabel={
              deadlineModalTarget.kind === 'project' ? t.targetDate : t.actionDeadline
            }
            currentDeadline={
              deadlineModalTarget.kind === 'project'
                ? liveProject!.deadline
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
          onClose={() => setProjectModalState({ open: false })}
        />
      )}

      {tagManagerOpen && (
        <TagManagerModal
          tags={snapshot.tags}
          t={t}
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
          areas={snapshot.areas}
          defaultAreaId={defaultAreaIdForNewProject}
          lang={lang}
          geminiApiKey={prefs.geminiApiKey}
          t={t}
          onNavigateProject={(projectId) => handleSelectNav({ kind: 'project', projectId })}
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
          onToggleCalendarEventsEnabled={handleToggleCalendarEventsEnabled}
          onRequestCalendarAccess={handleRequestCalendarAccess}
          onImportIcsFile={handleImportIcsFile}
          onRemoveImportedCalendar={handleRemoveImportedCalendar}
          onToggleSelectedCalendarId={handleToggleSelectedCalendarId}
          onSelectAllCalendars={handleSelectAllCalendars}
          onRefreshCalendars={handleRefreshCalendars}
          onOpenAuthModal={() => setAuthModalOpen(true)}
          onOpenNotificationsModal={() => setNotificationsModalOpen(true)}
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

      {toastMsg && <div className="toast-banner">{toastMsg}</div>}
    </div>
  );
};

export default App;
