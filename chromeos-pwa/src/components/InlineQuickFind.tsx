import React, { useMemo, useState } from 'react';
import {
  Bell,
  CalendarDays,
  CheckCircle2,
  CheckSquare,
  ClipboardList,
  Cloud,
  Columns3,
  Compass,
  Diamond,
  EyeOff,
  Flag,
  Folder,
  GanttChart,
  History,
  Hourglass,
  Inbox,
  LayoutGrid,
  List,
  Moon,
  Plus,
  Repeat,
  RotateCcw,
  Search,
  Settings,
  Sparkles,
  Sun,
  SunMoon,
  Timer,
  Trash2,
  X
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type ActiveNavigation,
  type AreaRecord,
  type AreaViewMode,
  type ChecklistItemRecord,
  type FocusTimerUiState,
  type ProjectRecord,
  type ProjectViewMode,
  type SmartViewType,
  type SupportedLang,
  type TagRecord,
  type TaskRecord
} from '../domain/types';
import {
  parseSmartTaskSyntax,
  type ParsedSmartSyntax
} from '../domain/smartSyntaxParser';
import {
  DEFAULT_QUICK_FIND_FILTER,
  formatStandaloneDuration,
  hasActiveQuickFindFilters,
  matchesQuickFindProject,
  matchesQuickFindTask,
  shouldShowSmartViewsAndAreasInQuickFind,
  type QuickFindDateFilter,
  type QuickFindEntityScope,
  type QuickFindFilter
} from '../domain/v2Utils';
import type { TranslationStrings } from '../i18n/translations';
import { ProjectProgressRing } from './ProjectProgressRing';

export interface CommandPaletteActionItem {
  id: string;
  label: string;
  keywords: string;
  icon: React.ReactNode;
  shortcut?: string;
  onRun: () => void;
}

interface InlineQuickFindProps {
  areas: AreaRecord[];
  projects: ProjectRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
  activeProject?: ProjectRecord | null;
  activeArea?: AreaRecord | null;
  selectedTask?: TaskRecord | null;
  focusTimer?: FocusTimerUiState | null;
  lang: SupportedLang;
  t: TranslationStrings;
  onNavigate: (nav: ActiveNavigation, highlightTaskId?: string) => void;
  onCreateSmartTask?: (parsed: ParsedSmartSyntax) => void;
  onToggleSelectedTaskInProgress?: () => void;
  onPlanMyDay?: () => void;
  onOpenAiArchitect?: () => void;
  onOpenWeeklyReview?: () => void;
  onCompleteActiveProject?: () => void;
  onSwitchProjectViewMode?: (mode: ProjectViewMode) => void;
  onSwitchAreaViewMode?: (mode: AreaViewMode) => void;
  onOpenZenFocus?: () => void;
  onOpenTemplates?: () => void;
  onCycleTheme?: () => void;
  onOpenSettings?: () => void;
  onClose: () => void;
}

export const InlineQuickFind: React.FC<InlineQuickFindProps> = ({
  areas,
  projects,
  tasks,
  checklistItems,
  tags,
  activeProject = null,
  activeArea = null,
  selectedTask = null,
  focusTimer = null,
  lang,
  t,
  onNavigate,
  onCreateSmartTask,
  onToggleSelectedTaskInProgress,
  onPlanMyDay,
  onOpenAiArchitect,
  onOpenWeeklyReview,
  onCompleteActiveProject,
  onSwitchProjectViewMode,
  onSwitchAreaViewMode,
  onOpenZenFocus,
  onOpenTemplates,
  onCycleTheme,
  onOpenSettings,
  onClose
}) => {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<QuickFindFilter>(DEFAULT_QUICK_FIND_FILTER);
  const [activeIndex, setActiveIndex] = useState(0);
  const [referenceNowMs] = useState(() => Date.now());

  const activeTags = useMemo(() => tags.filter((tg) => !tg.isDeleted), [tags]);
  const areasMap = useMemo(
    () => new Map(areas.filter((a) => !a.isDeleted).map((a) => [a.id, a])),
    [areas]
  );
  const projectsMap = useMemo(
    () => new Map(projects.filter((p) => !p.isDeleted).map((p) => [p.id, p])),
    [projects]
  );
  const tagsMap = useMemo(
    () => new Map(activeTags.map((tg) => [tg.id, tg])),
    [activeTags]
  );
  const taskHasChecklistSet = useMemo(() => {
    const s = new Set<string>();
    for (const chk of checklistItems) {
      if (!chk.isDeleted) s.add(chk.taskId);
    }
    return s;
  }, [checklistItems]);
  const projectHasInProgressSet = useMemo(() => {
    const s = new Set<string>();
    for (const tk of tasks) {
      if (!tk.isDeleted && tk.status === 'OPEN' && tk.isInProgress && tk.projectId) {
        s.add(tk.projectId);
      }
    }
    return s;
  }, [tasks]);

  const trimmedQuery = query.trim();
  const isCommandMode = query.trimStart().startsWith('>');
  const effectiveQuery = isCommandMode
    ? query.trimStart().slice(1).trim()
    : trimmedQuery;
  const q = effectiveQuery.toLowerCase();
  const hasFilters = hasActiveQuickFindFilters(filter);
  const showViewsAndAreas = shouldShowSmartViewsAndAreasInQuickFind(q, filter);

  const showCreateSmartRow =
    Boolean(onCreateSmartTask) && trimmedQuery.length > 0 && !isCommandMode;

  const parsedSmart = useMemo(
    () =>
      showCreateSmartRow
        ? parseSmartTaskSyntax(query, {
            tags,
            projects,
            areas,
            lang,
            reference: new Date(referenceNowMs)
          })
        : null,
    [showCreateSmartRow, query, tags, projects, areas, lang, referenceNowMs]
  );

  const allQuickActions = useMemo<CommandPaletteActionItem[]>(() => {
    const items: CommandPaletteActionItem[] = [];

    if (
      selectedTask &&
      !selectedTask.isDeleted &&
      selectedTask.status === 'OPEN' &&
      onToggleSelectedTaskInProgress
    ) {
      items.push({
        id: 'toggle-in-progress',
        label: t.v33CmdToggleInProgress,
        keywords: 'in progress wip doing active toggle mark clear arbeit encours incorso encurso',
        icon: (
          <span
            style={{
              color: 'var(--in-progress-amber-ring)',
              fontSize: 15,
              lineHeight: 1,
              width: 15,
              textAlign: 'center'
            }}
            aria-hidden="true"
          >
            ◐
          </span>
        ),
        shortcut: 'Ctrl+Shift+I',
        onRun: () => {
          onToggleSelectedTaskInProgress();
          onClose();
        }
      });
    }

    if (onPlanMyDay) {
      items.push({
        id: 'plan-my-day',
        label: t.v32CmdPlanMyDay,
        keywords: 'plan day schedule timeblock ai gemini today',
        icon: <Sparkles size={15} style={{ color: 'var(--gemini-violet)' }} />,
        onRun: () => {
          onPlanMyDay();
          onClose();
        }
      });
    }

    if (onOpenAiArchitect) {
      items.push({
        id: 'ai-architect',
        label: t.v32CmdAiArchitect,
        keywords: 'gemini ai hub architect brain dump roadmap',
        icon: <Sparkles size={15} style={{ color: 'var(--things-blue)' }} />,
        shortcut: 'Ctrl+G',
        onRun: () => {
          onOpenAiArchitect();
          onClose();
        }
      });
    }

    if (onOpenWeeklyReview) {
      items.push({
        id: 'weekly-review',
        label: t.v32CmdWeeklyReview,
        keywords: 'weekly review gtd stalled inbox',
        icon: <ClipboardList size={15} style={{ color: 'var(--things-blue)' }} />,
        shortcut: 'Ctrl+Shift+R',
        onRun: () => {
          onOpenWeeklyReview();
          onClose();
        }
      });
    }

    if (activeProject && activeProject.status === 'ACTIVE' && onCompleteActiveProject) {
      items.push({
        id: 'complete-project',
        label: `${t.actionComplete}: ${activeProject.title || t.entityProject}`,
        keywords: `complete project finish ${activeProject.title.toLowerCase()}`,
        icon: <CheckCircle2 size={15} style={{ color: 'var(--logbook-green)' }} />,
        onRun: () => {
          onCompleteActiveProject();
          onClose();
        }
      });
    }

    if (activeProject && onSwitchProjectViewMode) {
      items.push(
        {
          id: 'switch-view-list',
          label: t.v32CmdSwitchList,
          keywords: 'view list mode project',
          icon: <List size={15} style={{ color: 'var(--things-blue)' }} />,
          onRun: () => {
            onSwitchProjectViewMode('list');
            onClose();
          }
        },
        {
          id: 'switch-view-board',
          label: t.v32CmdSwitchBoard,
          keywords: 'view board kanban columns project',
          icon: <Columns3 size={15} style={{ color: 'var(--things-blue)' }} />,
          onRun: () => {
            onSwitchProjectViewMode('board');
            onClose();
          }
        },
        {
          id: 'switch-view-roadmap',
          label: t.v32CmdSwitchRoadmap,
          keywords: 'view timeline gantt roadmap project',
          icon: <GanttChart size={15} style={{ color: 'var(--things-blue)' }} />,
          onRun: () => {
            onSwitchProjectViewMode('roadmap');
            onClose();
          }
        }
      );
    } else if (activeArea && onSwitchAreaViewMode) {
      items.push(
        {
          id: 'switch-area-list',
          label: t.v32CmdSwitchList,
          keywords: 'view list mode area',
          icon: <List size={15} style={{ color: 'var(--things-blue)' }} />,
          onRun: () => {
            onSwitchAreaViewMode('list');
            onClose();
          }
        },
        {
          id: 'switch-area-roadmap',
          label: t.v32CmdSwitchRoadmap,
          keywords: 'view timeline portfolio gantt roadmap area',
          icon: <GanttChart size={15} style={{ color: 'var(--things-blue)' }} />,
          onRun: () => {
            onSwitchAreaViewMode('roadmap');
            onClose();
          }
        }
      );
    }

    if (focusTimer && onOpenZenFocus) {
      items.push({
        id: 'zen-focus',
        label: t.v32CmdZenFocus,
        keywords: 'zen focus timer pomodoro fullscreen',
        icon: <Timer size={15} style={{ color: 'var(--things-blue)' }} />,
        onRun: () => {
          onOpenZenFocus();
          onClose();
        }
      });
    }

    if (onOpenTemplates) {
      items.push({
        id: 'open-templates',
        label: t.v32CmdOpenTemplates,
        keywords: 'templates gallery blueprint import asana planner',
        icon: <LayoutGrid size={15} style={{ color: 'var(--things-blue)' }} />,
        onRun: () => {
          onOpenTemplates();
          onClose();
        }
      });
    }

    if (onCycleTheme) {
      items.push({
        id: 'cycle-theme',
        label: t.v32CmdToggleTheme,
        keywords: 'theme dark light system mode appearance',
        icon: <SunMoon size={15} style={{ color: 'var(--someday-amber)' }} />,
        onRun: () => {
          onCycleTheme();
          onClose();
        }
      });
    }

    if (onOpenSettings) {
      items.push({
        id: 'open-settings',
        label: t.v32CmdOpenSettings,
        keywords: 'settings preferences backup sync calendar workday',
        icon: <Settings size={15} style={{ color: 'var(--text-secondary)' }} />,
        onRun: () => {
          onOpenSettings();
          onClose();
        }
      });
    }

    return items;
  }, [
    t,
    activeProject,
    activeArea,
    selectedTask,
    focusTimer,
    onToggleSelectedTaskInProgress,
    onPlanMyDay,
    onOpenAiArchitect,
    onOpenWeeklyReview,
    onCompleteActiveProject,
    onSwitchProjectViewMode,
    onSwitchAreaViewMode,
    onOpenZenFocus,
    onOpenTemplates,
    onCycleTheme,
    onOpenSettings,
    onClose
  ]);

  const matchedActions = useMemo(() => {
    if (!isCommandMode && hasFilters) return [];
    if (!q) return allQuickActions;
    return allQuickActions.filter(
      (act) =>
        act.label.toLowerCase().includes(q) || act.keywords.toLowerCase().includes(q)
    );
  }, [allQuickActions, q, isCommandMode, hasFilters]);

  const smartViews: Array<{ view: SmartViewType; label: string; icon: React.ReactNode }> = useMemo(
    () => [
      { view: 'INBOX', label: t.viewInbox, icon: <Inbox size={15} style={{ color: 'var(--things-blue)' }} /> },
      { view: 'TODAY', label: t.viewToday, icon: <Sun size={15} style={{ color: 'var(--today-star)' }} /> },
      { view: 'UPCOMING', label: t.viewUpcoming, icon: <CalendarDays size={15} style={{ color: 'var(--upcoming-coral)' }} /> },
      { view: 'ANYTIME', label: t.viewAnytime, icon: <Compass size={15} style={{ color: 'var(--anytime-teal)' }} /> },
      { view: 'SOMEDAY', label: t.viewSomeday, icon: <Cloud size={15} style={{ color: 'var(--someday-amber)' }} /> },
      { view: 'LOGBOOK', label: t.viewLogbook, icon: <History size={15} style={{ color: 'var(--logbook-green)' }} /> },
      { view: 'TRASH', label: t.viewTrash, icon: <Trash2 size={15} style={{ color: 'var(--text-muted)' }} /> }
    ],
    [t]
  );

  const matchedViews = useMemo(() => {
    if (!showViewsAndAreas) return [];
    if (!q) return smartViews;
    return smartViews.filter((sv) => sv.label.toLowerCase().includes(q));
  }, [q, smartViews, showViewsAndAreas]);

  const matchedAreas = useMemo(() => {
    if (isCommandMode || !showViewsAndAreas) return [];
    const active = areas.filter((a) => !a.isDeleted);
    if (!q) return active;
    return active.filter((a) => a.title.toLowerCase().includes(q));
  }, [areas, q, showViewsAndAreas, isCommandMode]);

  const matchedProjects = useMemo(() => {
    if (isCommandMode) return [];
    const filtered = projects.filter((p) =>
      matchesQuickFindProject(p, filter, referenceNowMs, projectHasInProgressSet.has(p.id))
    );
    if (!q) return hasFilters ? filtered.slice(0, 25) : filtered.slice(0, 8);
    return filtered
      .filter(
        (p) =>
          p.title.toLowerCase().includes(q) ||
          p.notes.toLowerCase().includes(q) ||
          (p.areaId && (areasMap.get(p.areaId)?.title.toLowerCase().includes(q) ?? false))
      )
      .slice(0, 25);
  }, [
    projects,
    areasMap,
    q,
    filter,
    hasFilters,
    referenceNowMs,
    isCommandMode,
    projectHasInProgressSet
  ]);

  const matchedTasks = useMemo(() => {
    if (isCommandMode) return [];
    const filtered = tasks.filter((tk) =>
      matchesQuickFindTask(tk, filter, taskHasChecklistSet.has(tk.id), referenceNowMs)
    );
    if (!q) {
      if (hasFilters) return filtered.slice(0, 50);
      return filtered.filter((tk) => tk.status === 'OPEN').slice(0, 12);
    }
    return filtered
      .filter((tk) => {
        const tagMatch = tk.tagIds.some((id) => tagsMap.get(id)?.title.toLowerCase().includes(q));
        const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
        const area =
          proj?.areaId != null
            ? areasMap.get(proj.areaId)
            : tk.areaId
              ? areasMap.get(tk.areaId)
              : undefined;
        const chkMatch = checklistItems.some(
          (c) => !c.isDeleted && c.taskId === tk.id && c.title.toLowerCase().includes(q)
        );
        return (
          tk.title.toLowerCase().includes(q) ||
          tk.notes.toLowerCase().includes(q) ||
          tagMatch ||
          chkMatch ||
          (proj?.title.toLowerCase().includes(q) ?? false) ||
          (area?.title.toLowerCase().includes(q) ?? false)
        );
      })
      .slice(0, 50);
  }, [
    tasks,
    q,
    filter,
    hasFilters,
    taskHasChecklistSet,
    tagsMap,
    projectsMap,
    areasMap,
    checklistItems,
    referenceNowMs,
    isCommandMode
  ]);

  const resolveTaskDestination = (task: TaskRecord): ActiveNavigation => {
    if (task.projectId && projectsMap.has(task.projectId)) {
      return { kind: 'project', projectId: task.projectId };
    }
    if (task.status !== 'OPEN') {
      return { kind: 'smart', view: 'LOGBOOK' };
    }
    if (task.areaId && areasMap.has(task.areaId) && task.scheduleState === 'ANYTIME') {
      return { kind: 'area', areaId: task.areaId };
    }
    switch (task.scheduleState) {
      case 'INBOX':
        return { kind: 'smart', view: 'INBOX' };
      case 'TODAY':
      case 'THIS_EVENING':
        return { kind: 'smart', view: 'TODAY' };
      case 'UPCOMING':
        return { kind: 'smart', view: 'UPCOMING' };
      case 'SOMEDAY':
        return { kind: 'smart', view: 'SOMEDAY' };
      default:
        return task.areaId && areasMap.has(task.areaId)
          ? { kind: 'area', areaId: task.areaId }
          : { kind: 'smart', view: 'ANYTIME' };
    }
  };

  // Ordered keyboard-navigable entries across Create To-Do, Quick Actions, Views, Areas, Projects, Tasks
  const navigableCallbacks = useMemo<Array<() => void>>(() => {
    const list: Array<() => void> = [];
    if (showCreateSmartRow && parsedSmart && onCreateSmartTask) {
      list.push(() => {
        onCreateSmartTask(parsedSmart);
        onClose();
      });
    }
    for (const act of matchedActions) {
      list.push(act.onRun);
    }
    for (const sv of matchedViews) {
      list.push(() => {
        onNavigate({ kind: 'smart', view: sv.view });
        onClose();
      });
    }
    for (const area of matchedAreas) {
      list.push(() => {
        onNavigate({ kind: 'area', areaId: area.id });
        onClose();
      });
    }
    for (const proj of matchedProjects) {
      list.push(() => {
        onNavigate({ kind: 'project', projectId: proj.id });
        onClose();
      });
    }
    for (const tk of matchedTasks) {
      list.push(() => {
        onNavigate(resolveTaskDestination(tk), tk.id);
        onClose();
      });
    }
    return list;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    showCreateSmartRow,
    parsedSmart,
    onCreateSmartTask,
    matchedActions,
    matchedViews,
    matchedAreas,
    matchedProjects,
    matchedTasks,
    onNavigate,
    onClose
  ]);

  const safeActiveIndex =
    navigableCallbacks.length === 0
      ? -1
      : Math.min(Math.max(0, activeIndex), navigableCallbacks.length - 1);

  const totalHits =
    matchedActions.length +
    matchedViews.length +
    matchedAreas.length +
    matchedProjects.length +
    matchedTasks.length;

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const formatClock = (ms: number) => {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const renderDeadlineBadge = (deadlineMs: number) => {
    const diff = daysBetweenToday(deadlineMs);
    const label =
      diff === 0
        ? t.deadlineToday
        : diff > 0
          ? t.deadlineDaysLeft(diff)
          : t.deadlineDaysOverdue(Math.abs(diff));
    const urgent = diff <= 2;
    return (
      <span className={`meta-badge ${urgent ? 'urgent' : ''}`}>
        <Flag size={10.5} />
        <span>{label}</span>
      </span>
    );
  };

  let runningRowIdx = 0;

  return (
    <section
      className="inline-quickfind"
      data-testid="inline-command-palette"
      aria-label={t.quickFindTitle}
    >
      <div className="inline-qf-bar">
        <div className="inline-qf-input-wrap">
          <Search size={17} style={{ color: 'var(--things-blue)', flexShrink: 0 }} />
          <input
            type="text"
            className="inline-qf-input"
            data-testid="cmd-palette-input"
            autoFocus
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.stopPropagation();
                if (query) {
                  setQuery('');
                  setActiveIndex(0);
                } else if (hasFilters) {
                  setFilter(DEFAULT_QUICK_FIND_FILTER);
                  setActiveIndex(0);
                } else {
                  onClose();
                }
                return;
              }
              if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (navigableCallbacks.length > 0) {
                  setActiveIndex((prev) => (prev + 1) % navigableCallbacks.length);
                }
                return;
              }
              if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (navigableCallbacks.length > 0) {
                  setActiveIndex(
                    (prev) =>
                      (prev - 1 + navigableCallbacks.length) % navigableCallbacks.length
                  );
                }
                return;
              }
              if (e.key === 'Enter' && safeActiveIndex >= 0) {
                e.preventDefault();
                navigableCallbacks[safeActiveIndex]?.();
              }
            }}
            placeholder={t.v32CmdPalettePlaceholder}
          />
          <span style={{ fontSize: 11.5, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
            {t.quickFindHits(totalHits)}
          </span>
          {query && (
            <button
              type="button"
              className="icon-btn"
              style={{ height: 24, minWidth: 24, padding: 0 }}
              onClick={() => {
                setQuery('');
                setActiveIndex(0);
              }}
              title={t.actionClear}
            >
              <X size={14} />
            </button>
          )}
          <button
            type="button"
            className="btn-secondary"
            style={{ padding: '4px 9px', fontSize: 12 }}
            onClick={onClose}
          >
            {t.actionDone}
          </button>
        </div>

        {/* Search Filter Strip */}
        <div className="inline-qf-filters">
          <button
            type="button"
            className={`inline-qf-chip ${filter.hideCompleted ? 'active' : ''}`}
            onClick={() => {
              setFilter((f) => ({ ...f, hideCompleted: !f.hideCompleted }));
              setActiveIndex(0);
            }}
          >
            <EyeOff size={12.5} />
            <span>{t.searchFilterHideCompleted}</span>
          </button>

          <button
            type="button"
            data-testid="qf-filter-in-progress"
            className={`inline-qf-chip ${filter.inProgressOnly ? 'active' : ''}`}
            onClick={() => {
              setFilter((f) => ({ ...f, inProgressOnly: !f.inProgressOnly }));
              setActiveIndex(0);
            }}
          >
            <span aria-hidden="true">◐</span>
            <span>{t.v33FilterInProgressOnly}</span>
          </button>

          <select
            className={`inline-qf-select ${filter.dateFilter !== 'ALL' ? 'active' : ''}`}
            value={filter.dateFilter}
            aria-label={t.searchFilterDateAll}
            onChange={(e) => {
              setFilter((f) => ({ ...f, dateFilter: e.target.value as QuickFindDateFilter }));
              setActiveIndex(0);
            }}
          >
            <option value="ALL">📅 {t.searchFilterDateAll}</option>
            <option value="TODAY">⭐ {t.searchFilterDateToday}</option>
            <option value="OVERDUE">⚠️ {t.searchFilterDateOverdue}</option>
            <option value="NEXT_7_DAYS">🗓️ {t.searchFilterDateNext7Days}</option>
            <option value="THIS_MONTH">📆 {t.searchFilterDateThisMonth}</option>
            <option value="NO_DATE">⏳ {t.searchFilterDateNone}</option>
          </select>

          <button
            type="button"
            className={`inline-qf-chip ${filter.hasDeadlineOnly ? 'active' : ''}`}
            onClick={() => {
              setFilter((f) => ({ ...f, hasDeadlineOnly: !f.hasDeadlineOnly }));
              setActiveIndex(0);
            }}
          >
            <Flag size={12} />
            <span>{t.searchFilterHasDeadline}</span>
          </button>

          <button
            type="button"
            className={`inline-qf-chip ${filter.hasChecklistOnly ? 'active' : ''}`}
            onClick={() => {
              setFilter((f) => ({ ...f, hasChecklistOnly: !f.hasChecklistOnly }));
              setActiveIndex(0);
            }}
          >
            <CheckSquare size={12} />
            <span>{t.searchFilterHasChecklist}</span>
          </button>

          {activeTags.length > 0 && (
            <select
              className={`inline-qf-select ${filter.tagId !== null ? 'active' : ''}`}
              value={filter.tagId ?? ''}
              aria-label={t.searchFilterAllTags}
              onChange={(e) => {
                setFilter((f) => ({ ...f, tagId: e.target.value || null }));
                setActiveIndex(0);
              }}
            >
              <option value="">🏷️ {t.searchFilterAllTags}</option>
              {activeTags.map((tg) => (
                <option key={tg.id} value={tg.id}>
                  #{tg.title}
                </option>
              ))}
            </select>
          )}

          <select
            className={`inline-qf-select ${filter.scope !== 'ALL' ? 'active' : ''}`}
            value={filter.scope}
            aria-label={t.searchFilterScopeAll}
            onChange={(e) => {
              setFilter((f) => ({ ...f, scope: e.target.value as QuickFindEntityScope }));
              setActiveIndex(0);
            }}
          >
            <option value="ALL">{t.searchFilterScopeAll}</option>
            <option value="TASKS">{t.searchFilterScopeTasks}</option>
            <option value="PROJECTS">{t.searchFilterScopeProjects}</option>
          </select>

          {hasFilters && (
            <button
              type="button"
              className="inline-qf-chip reset"
              onClick={() => {
                setFilter(DEFAULT_QUICK_FIND_FILTER);
                setActiveIndex(0);
              }}
            >
              <RotateCcw size={11.5} />
              <span>{t.searchFilterReset}</span>
            </button>
          )}
        </div>

        <div className="inline-qf-results">
          {/* 0. Instant Smart-Syntax Task Creation Row */}
          {showCreateSmartRow && parsedSmart && onCreateSmartTask && (() => {
            const rowIdx = runningRowIdx++;
            const isActive = rowIdx === safeActiveIndex;
            return (
              <div>
                <button
                  type="button"
                  data-testid="cmd-palette-create-task"
                  className={`inline-qf-row v32-cmd-create-row ${isActive ? 'selected' : ''}`}
                  onMouseEnter={() => setActiveIndex(rowIdx)}
                  onClick={() => {
                    onCreateSmartTask(parsedSmart);
                    onClose();
                  }}
                >
                  <div className="inline-qf-row-top">
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 8,
                        fontWeight: 650,
                        color: 'var(--things-blue)'
                      }}
                    >
                      <Plus size={16} strokeWidth={2.4} />
                      <span>{t.v32CmdCreateTask(parsedSmart.cleanTitle)}</span>
                    </span>
                    <kbd className="v32-cmd-kbd">↵</kbd>
                  </div>

                  {parsedSmart.hasAnyMatch ? (
                    <div className="inline-qf-row-meta" data-testid="cmd-palette-smart-badges">
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
                      {parsedSmart.isInProgress && (
                        <span className="meta-badge in-progress-badge">
                          <span>◐ {t.v33InProgressBadge}</span>
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
                  ) : (
                    <div className="inline-qf-notes">{t.v32SmartSyntaxHint}</div>
                  )}
                </button>
              </div>
            );
          })()}

          {totalHits === 0 && !showCreateSmartRow && (
            <div style={{ padding: '22px 12px', textAlign: 'center', color: 'var(--text-muted)' }}>
              {t.quickFindNoResults}
            </div>
          )}

          {/* 0b. Quick Actions Section */}
          {matchedActions.length > 0 && (
            <div data-testid="cmd-palette-actions-section">
              <div className="inline-qf-section-label">{t.v32CmdSectionActions}</div>
              {matchedActions.map((act) => {
                const rowIdx = runningRowIdx++;
                const isActive = rowIdx === safeActiveIndex;
                return (
                  <button
                    key={act.id}
                    type="button"
                    data-testid={`cmd-action-${act.id}`}
                    className={`inline-qf-row ${isActive ? 'selected' : ''}`}
                    onMouseEnter={() => setActiveIndex(rowIdx)}
                    onClick={act.onRun}
                  >
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 600
                        }}
                      >
                        {act.icon}
                        <span>{act.label}</span>
                      </span>
                      {act.shortcut && <kbd className="v32-cmd-kbd">{act.shortcut}</kbd>}
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {/* 1. Smart Views */}
          {matchedViews.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.quickFindSmartViews}</div>
              {matchedViews.map((sv) => {
                const rowIdx = runningRowIdx++;
                const isActive = rowIdx === safeActiveIndex;
                return (
                  <button
                    key={sv.view}
                    type="button"
                    className={`inline-qf-row ${isActive ? 'selected' : ''}`}
                    onMouseEnter={() => setActiveIndex(rowIdx)}
                    onClick={() => {
                      onNavigate({ kind: 'smart', view: sv.view });
                      onClose();
                    }}
                  >
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 600
                        }}
                      >
                        {sv.icon}
                        <span>{sv.label}</span>
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {/* 2. Areas */}
          {matchedAreas.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityAreas}</div>
              {matchedAreas.map((area) => {
                const rowIdx = runningRowIdx++;
                const isActive = rowIdx === safeActiveIndex;
                return (
                  <button
                    key={area.id}
                    type="button"
                    className={`inline-qf-row ${isActive ? 'selected' : ''}`}
                    onMouseEnter={() => setActiveIndex(rowIdx)}
                    onClick={() => {
                      onNavigate({ kind: 'area', areaId: area.id });
                      onClose();
                    }}
                  >
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 600
                        }}
                      >
                        <Folder size={15} style={{ color: 'var(--things-blue)' }} />
                        <span>{area.title}</span>
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {/* 3. Projects */}
          {matchedProjects.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityProjects}</div>
              {matchedProjects.map((proj) => {
                const rowIdx = runningRowIdx++;
                const isActive = rowIdx === safeActiveIndex;
                const projTasks = tasks.filter(
                  (tk) => !tk.isDeleted && tk.projectId === proj.id && tk.status !== 'CANCELED'
                );
                const done = projTasks.filter((tk) => tk.status === 'COMPLETED').length;
                const inProg = projTasks.filter(
                  (tk) => tk.status === 'OPEN' && Boolean(tk.isInProgress)
                ).length;
                const area = proj.areaId ? areasMap.get(proj.areaId) : undefined;
                return (
                  <button
                    key={proj.id}
                    type="button"
                    className={`inline-qf-row ${isActive ? 'selected' : ''}`}
                    onMouseEnter={() => setActiveIndex(rowIdx)}
                    onClick={() => {
                      onNavigate({ kind: 'project', projectId: proj.id });
                      onClose();
                    }}
                  >
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                          fontWeight: 650
                        }}
                      >
                        <ProjectProgressRing
                          completed={done}
                          inProgress={inProg}
                          total={projTasks.length}
                          size={16}
                          isProjectCompleted={proj.status === 'COMPLETED'}
                        />
                        <span>{proj.title}</span>
                      </span>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        {inProg > 0 && proj.status === 'ACTIVE' && (
                          <span className="meta-badge in-progress-badge">
                            <span>◐ {t.v33ProjectInProgressCount(inProg)}</span>
                          </span>
                        )}
                        <span className="meta-badge">
                          <Folder size={10.5} />
                          <span>
                            {done}/{projTasks.length}
                          </span>
                        </span>
                        {proj.deadline != null && renderDeadlineBadge(proj.deadline)}
                      </span>
                    </div>
                    {(area || proj.notes.trim()) && (
                      <div className="inline-qf-notes">
                        {area ? `📦 ${area.title}` : ''}
                        {area && proj.notes.trim() ? ' • ' : ''}
                        {proj.notes.trim()}
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {/* 4. Tasks (3-row rich metadata) */}
          {matchedTasks.length > 0 && (
            <div>
              <div className="inline-qf-section-label">{t.entityTodos}</div>
              {matchedTasks.map((tk) => {
                const rowIdx = runningRowIdx++;
                const isActive = rowIdx === safeActiveIndex;
                const proj = tk.projectId ? projectsMap.get(tk.projectId) : undefined;
                const area =
                  proj?.areaId != null
                    ? areasMap.get(proj.areaId)
                    : tk.areaId
                      ? areasMap.get(tk.areaId)
                      : undefined;
                const containerText =
                  area && proj
                    ? `📦 ${area.title} › 📁 ${proj.title}`
                    : proj
                      ? `📁 ${proj.title}`
                      : area
                        ? `📦 ${area.title}`
                        : '';
                const chks = checklistItems.filter((c) => !c.isDeleted && c.taskId === tk.id);
                const chkDone = chks.filter((c) => c.isCompleted).length;
                const taskTags = tk.tagIds
                  .map((id) => tagsMap.get(id))
                  .filter((x): x is TagRecord => Boolean(x));

                const statusLabel =
                  tk.status === 'COMPLETED'
                    ? `✓ ${t.statCompleted}`
                    : tk.status === 'CANCELED'
                      ? `✕ ${t.statCanceled}`
                      : tk.scheduleState === 'THIS_EVENING'
                        ? t.viewThisEvening
                        : tk.scheduleState === 'TODAY'
                          ? t.viewToday
                          : tk.scheduleState === 'UPCOMING'
                            ? t.viewUpcoming
                            : tk.scheduleState === 'SOMEDAY'
                              ? t.viewSomeday
                              : tk.scheduleState === 'ANYTIME'
                                ? t.viewAnytime
                                : t.viewInbox;

                return (
                  <button
                    key={tk.id}
                    type="button"
                    className={`inline-qf-row ${isActive ? 'selected' : ''}`}
                    onMouseEnter={() => setActiveIndex(rowIdx)}
                    onClick={() => {
                      onNavigate(resolveTaskDestination(tk), tk.id);
                      onClose();
                    }}
                  >
                    {/* Row 1: Title + Status/Schedule Badge */}
                    <div className="inline-qf-row-top">
                      <span
                        style={{
                          fontWeight: 560,
                          textDecoration: tk.status !== 'OPEN' ? 'line-through' : undefined,
                          color: tk.status !== 'OPEN' ? 'var(--text-muted)' : 'var(--text-primary)'
                        }}
                      >
                        {tk.title || t.placeholderTodoTitle}
                      </span>
                      <span
                        className={`meta-badge ${
                          tk.scheduleState === 'TODAY'
                            ? 'today'
                            : tk.scheduleState === 'THIS_EVENING'
                              ? 'evening'
                              : ''
                        }`}
                      >
                        {tk.scheduleState === 'THIS_EVENING' && <Moon size={10.5} />}
                        <span>{statusLabel}</span>
                      </span>
                    </div>

                    {/* Row 2: Container, When, Reminder, Repeat, Deadline, Checklist, Tags */}
                    <div className="inline-qf-row-meta">
                      {tk.status === 'OPEN' && tk.isInProgress && (
                        <span
                          className="meta-badge in-progress-badge"
                          data-testid="qf-task-in-progress-pill"
                        >
                          <span>◐ {t.v33InProgressBadge}</span>
                        </span>
                      )}
                      {containerText && <span className="meta-badge">{containerText}</span>}
                      {tk.whenDate != null && (
                        <span className="meta-badge">
                          <CalendarDays size={10.5} />
                          <span>{formatShortDate(tk.whenDate)}</span>
                        </span>
                      )}
                      {tk.reminderTime != null && (
                        <span className="meta-badge">
                          <Bell size={10.5} />
                          <span>{formatClock(tk.reminderTime)}</span>
                        </span>
                      )}
                      {tk.recurrenceRule != null && (
                        <span className="meta-badge">
                          <Repeat size={10.5} />
                          <span>{formatRecurrenceShort(tk.recurrenceRule, lang)}</span>
                        </span>
                      )}
                      {tk.deadline != null && renderDeadlineBadge(tk.deadline)}
                      {chks.length > 0 && (
                        <span className="meta-badge">
                          <CheckSquare size={10.5} />
                          <span>
                            {chkDone}/{chks.length}
                          </span>
                        </span>
                      )}
                      {taskTags.map((tg) => (
                        <span key={tg.id} className="meta-badge">
                          <LayoutGrid size={9.5} />
                          <span>{tg.title}</span>
                        </span>
                      ))}
                    </div>

                    {/* Row 3: 2-line Notes Preview */}
                    {tk.notes.trim() && <div className="inline-qf-notes">{tk.notes.trim()}</div>}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  );
};
