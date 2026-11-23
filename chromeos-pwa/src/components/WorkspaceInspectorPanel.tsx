import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
   BarChart3,
  Calendar,
  CalendarDays,
  Check,
  CheckSquare,
  Circle,
  Clock,
  Cloud,
  Diamond,
  FileText,
  Flag,
  Folder,
  Hourglass,
  LayoutGrid,
  Maximize2,
  Moon,
  PanelRightClose,
  Pin,
  PinOff,
  Plus,
  Sun,
  Timer,
  X,
  Zap
} from 'lucide-react';
import {
  addDaysLocal,
  daysBetweenToday,
  type AreaRecord,
  type CalendarEventItem,
  type ChecklistItemRecord,
  type EffortLevel,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  DEFAULT_WORKDAY_END_MINUTES,
  DEFAULT_WORKDAY_START_MINUTES,
  STANDALONE_DURATION_PRESETS,
  TIMEBLOCK_QUICK_START_MINUTES,
  buildDailyTimeline,
  computeMultiDayWorkloadForecast,
  computeTodayCapacitySummary,
  formatMinutesAsClock,
  formatStandaloneDuration,
  formatTimeblockRange,
  normalizeWorkdayBounds,
  type DayWorkloadSummary
} from '../domain/v2Utils';
import { extractDetectedLinksFromNotes } from '../domain/powerUtils';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';
import { DetectedNotesLinksRow } from './MarkdownNotesField';

interface InspectorChecklistRowProps {
  item: ChecklistItemRecord;
  t: TranslationStrings;
}

const InspectorChecklistRow: React.FC<InspectorChecklistRowProps> = ({ item, t }) => {
  const [subTitle, setSubTitle] = useState(item.title);
  const [prevTitle, setPrevTitle] = useState(item.title);
  const [isFocused, setIsFocused] = useState(false);

  if (prevTitle !== item.title) {
    setPrevTitle(item.title);
    if (!isFocused) {
      setSubTitle(item.title);
    }
  }

  return (
    <div className="inspector-checklist-row">
      <button
        type="button"
        className={`checklist-circle ${item.isCompleted ? 'done' : ''}`}
        title={item.isCompleted ? t.actionReopenTodo : t.actionComplete}
        aria-label={item.isCompleted ? t.actionReopenTodo : t.actionComplete}
        onClick={() =>
          void localDb.updateChecklistItem(item.id, { isCompleted: !item.isCompleted })
        }
      >
        {item.isCompleted && <Check size={10} strokeWidth={3} />}
      </button>

      <input
        type="text"
        className={`checklist-input ${item.isCompleted ? 'done' : ''}`}
        value={subTitle}
        placeholder={t.placeholderAddChecklist}
        onFocus={() => setIsFocused(true)}
        onBlur={() => {
          setIsFocused(false);
          if (subTitle !== item.title) {
            void localDb.updateChecklistItem(item.id, { title: subTitle });
          }
        }}
        onChange={(e) => {
          const next = e.target.value;
          setSubTitle(next);
          void localDb.updateChecklistItem(item.id, { title: next });
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            e.currentTarget.blur();
          }
        }}
      />

      <button
        type="button"
        className="icon-btn inspector-checklist-del-btn"
        title={t.actionDelete}
        aria-label={t.actionDelete}
        onClick={() => void localDb.deleteChecklistItem(item.id)}
      >
        <X size={13} />
      </button>
    </div>
  );
};

function nextCycledEffort(cur: EffortLevel | null | undefined): EffortLevel | null {
  if (!cur) return 'QUICK_WIN';
  if (cur === 'QUICK_WIN') return 'MEDIUM';
  if (cur === 'MEDIUM') return 'DEEP_FOCUS';
  return null;
}

export interface SevenDayWorkloadCardProps {
  forecast: readonly DayWorkloadSummary[];
  lang: SupportedLang;
  t: TranslationStrings;
  compact?: boolean;
}

export const SevenDayWorkloadCard: React.FC<SevenDayWorkloadCardProps> = ({
  forecast,
  lang,
  t,
  compact = false
}) => {
  if (forecast.length === 0) return null;

  const weekdayShortFmt = new Intl.DateTimeFormat(lang, { weekday: compact ? 'narrow' : 'short' });
  const dateFmt = new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' });
  const maxOverloadedMinutes = forecast.reduce(
    (m, d) => (d.isOverloaded ? Math.max(m, d.overloadedByMinutes) : m),
    0
  );

  return (
    <section
      className={compact ? 'inspector-bento-card' : 'v31-workload-bento-card'}
      data-testid={compact ? 'inspector-overview-workload-card' : 'upcoming-7day-workload-card'}
      onClick={(e) => e.stopPropagation()}
    >
      <div className="inspector-card-header-between">
        <span className="inspector-card-label">
          <BarChart3
            size={compact ? 12.5 : 14}
            style={{ color: 'var(--things-blue)', flexShrink: 0 }}
          />
          <span>{t.v31Workload7DayTitle}</span>
        </span>

        {maxOverloadedMinutes > 0 ? (
          <span
            className="v31-workload-status-pill overloaded"
            data-testid="workload-status-pill"
          >
            {compact
              ? t.v31WorkloadOverloadedShort(formatStandaloneDuration(maxOverloadedMinutes))
              : t.v31WorkloadOverloadedBy(formatStandaloneDuration(maxOverloadedMinutes))}
          </span>
        ) : (
          <span
            className="v31-workload-status-pill balanced"
            data-testid="workload-status-pill"
          >
            {t.v31WorkloadBalanced}
          </span>
        )}
      </div>

      <div className={`v31-workload-bars-grid ${compact ? 'compact' : ''}`}>
        {forecast.map((day, idx) => {
          const dObj = new Date(day.dayStartMillis);
          const totalUsed = day.plannedMinutes + day.busyCalendarMinutes;
          const cap = Math.max(60, day.totalCapacityMinutes);
          const fillPct = Math.min(100, Math.round((totalUsed / cap) * 100));
          const busyPct =
            totalUsed > 0
              ? Math.min(fillPct, Math.round((day.busyCalendarMinutes / cap) * 100))
              : 0;
          const plannedPct = Math.max(0, fillPct - busyPct);
          const topText = day.isOverloaded
            ? `+${formatStandaloneDuration(day.overloadedByMinutes)}`
            : totalUsed > 0
              ? formatStandaloneDuration(totalUsed)
              : '—';
          const tip = day.isOverloaded
            ? `${dateFmt.format(dObj)} · ${t.v31WorkloadOverloadedBy(
                formatStandaloneDuration(day.overloadedByMinutes)
              )}`
            : `${dateFmt.format(dObj)} · ${t.todayCapacitySummary(
                formatStandaloneDuration(day.plannedMinutes),
                formatStandaloneDuration(day.freeMinutes)
              )}`;

          return (
            <div
              key={day.epochDay}
              className={`v31-workload-day-col ${idx === 0 ? 'today' : ''} ${
                day.isOverloaded ? 'overloaded' : ''
              }`}
              title={tip}
            >
              <span className="v31-workload-day-top">{topText}</span>
              <div className="v31-workload-bar-track" aria-hidden="true">
                {busyPct > 0 && (
                  <div
                    className="v31-workload-bar-busy"
                    style={{ height: `${busyPct}%` }}
                  />
                )}
                {plannedPct > 0 && (
                  <div
                    className={`v31-workload-bar-planned ${
                      day.isOverloaded ? 'overloaded' : ''
                    }`}
                    style={{ height: `${plannedPct}%` }}
                  />
                )}
              </div>
              <span className="v31-workload-day-dow">{weekdayShortFmt.format(dObj)}</span>
              {!compact && (
                <span className="v31-workload-day-num">{dObj.getDate()}</span>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
};

export interface WorkspaceInspectorPanelProps {
  selectedTask: TaskRecord | null;
  todayTasks: TaskRecord[];
  todayCompletedCount: number;
  todayCalendarEvents: CalendarEventItem[];
  upcomingCalendarEventsByEpochDay?: ReadonlyMap<number, readonly CalendarEventItem[]>;
  workdayStartMinutes?: number;
  workdayEndMinutes?: number;
  todayStartMs: number;
  endOfTodayMs: number;
  allOpenTasks: TaskRecord[];
  visibleViewTasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  tagsMap: Map<string, TagRecord>;
  lang: SupportedLang;
  t: TranslationStrings;
  onSelectTaskFromInspector: (task: TaskRecord) => void;
  onOpenTaskEditor: (taskId: string) => void;
  onClearSelection: () => void;
  onCollapsePanel: () => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenDeadlineModal: (taskId: string) => void;
  onOpenMoveModal: (taskId: string) => void;
  onOpenTagsModal: (taskId: string) => void;
  onStartFocusTimer: (
    taskId: string,
    taskTitle: string,
    durationMinutes?: number | null
  ) => void;
}

export const WorkspaceInspectorPanel: React.FC<WorkspaceInspectorPanelProps> = ({
  selectedTask,
  todayTasks,
  todayCompletedCount,
  todayCalendarEvents,
  upcomingCalendarEventsByEpochDay,
  workdayStartMinutes = DEFAULT_WORKDAY_START_MINUTES,
  workdayEndMinutes = DEFAULT_WORKDAY_END_MINUTES,
  todayStartMs,
  endOfTodayMs,
  allOpenTasks,
  visibleViewTasks,
  checklistItems,
  areasMap,
  projectsMap,
  tagsMap,
  lang,
  t,
  onSelectTaskFromInspector,
  onOpenTaskEditor,
  onClearSelection,
  onCollapsePanel,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onStartFocusTimer
}) => {
  const [prevTaskId, setPrevTaskId] = useState<string | null>(selectedTask?.id ?? null);
  const [prevServerTitle, setPrevServerTitle] = useState<string>(selectedTask?.title ?? '');
  const [prevServerNotes, setPrevServerNotes] = useState<string>(selectedTask?.notes ?? '');
  const [titleDraft, setTitleDraft] = useState<string>(selectedTask?.title ?? '');
  const [notesDraft, setNotesDraft] = useState<string>(selectedTask?.notes ?? '');
  const [isTitleFocused, setIsTitleFocused] = useState(false);
  const [isNotesFocused, setIsNotesFocused] = useState(false);
  const [newChecklistTitle, setNewChecklistTitle] = useState('');
  const [showTimeblockPicker, setShowTimeblockPicker] = useState(false);
  const [showDurationPicker, setShowDurationPicker] = useState(false);

  const notesTextareaRef = useRef<HTMLTextAreaElement | null>(null);

  if ((selectedTask?.id ?? null) !== prevTaskId) {
    setPrevTaskId(selectedTask?.id ?? null);
    setPrevServerTitle(selectedTask?.title ?? '');
    setPrevServerNotes(selectedTask?.notes ?? '');
    setTitleDraft(selectedTask?.title ?? '');
    setNotesDraft(selectedTask?.notes ?? '');
    setIsTitleFocused(false);
    setIsNotesFocused(false);
    setNewChecklistTitle('');
    setShowTimeblockPicker(false);
    setShowDurationPicker(false);
  } else if (selectedTask) {
    if (prevServerTitle !== selectedTask.title) {
      setPrevServerTitle(selectedTask.title);
      if (!isTitleFocused) {
        setTitleDraft(selectedTask.title);
      }
    }
    if (prevServerNotes !== selectedTask.notes) {
      setPrevServerNotes(selectedTask.notes);
      if (!isNotesFocused) {
        setNotesDraft(selectedTask.notes);
      }
    }
  }

  // Auto-size notes textarea cleanly without scrollbars
  useEffect(() => {
    const el = notesTextareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.max(76, Math.min(320, el.scrollHeight))}px`;
  }, [notesDraft, selectedTask?.id]);

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const workdayBounds = useMemo(
    () => normalizeWorkdayBounds(workdayStartMinutes, workdayEndMinutes),
    [workdayStartMinutes, workdayEndMinutes]
  );

  // Mode B derived metrics
  const todayCapacity = useMemo(
    () =>
      computeTodayCapacitySummary(
        todayTasks,
        todayCalendarEvents,
        todayStartMs,
        endOfTodayMs,
        workdayBounds.startMinutes,
        workdayBounds.endMinutes
      ),
    [
      todayTasks,
      todayCalendarEvents,
      todayStartMs,
      endOfTodayMs,
      workdayBounds.startMinutes,
      workdayBounds.endMinutes
    ]
  );

  const sevenDayWorkload = useMemo(
    () =>
      computeMultiDayWorkloadForecast({
        todayTasks,
        allOpenTasks,
        todayCalendarEvents,
        upcomingCalendarEventsByEpochDay,
        todayStartMillis: todayStartMs,
        daysCount: 7,
        workdayStartMinutes: workdayBounds.startMinutes,
        workdayEndMinutes: workdayBounds.endMinutes
      }),
    [
      todayTasks,
      allOpenTasks,
      todayCalendarEvents,
      upcomingCalendarEventsByEpochDay,
      todayStartMs,
      workdayBounds.startMinutes,
      workdayBounds.endMinutes
    ]
  );

  const dailyTimeline = useMemo(
    () => buildDailyTimeline(todayTasks, todayCalendarEvents, todayStartMs),
    [todayTasks, todayCalendarEvents, todayStartMs]
  );

  const upcomingDeadlinesAndMilestones = useMemo(() => {
    const candidates = allOpenTasks.filter((tk) => {
      if (tk.isDeleted || tk.status !== 'OPEN') return false;
      if (tk.deadline != null) {
        return daysBetweenToday(tk.deadline) <= 14;
      }
      if (tk.isMilestone) {
        if (tk.whenDate != null) {
          return daysBetweenToday(tk.whenDate) <= 14;
        }
        return true;
      }
      return false;
    });

    return candidates
      .sort((a, b) => {
        const aTarget = a.deadline ?? a.whenDate ?? Number.MAX_SAFE_INTEGER;
        const bTarget = b.deadline ?? b.whenDate ?? Number.MAX_SAFE_INTEGER;
        if (aTarget !== bTarget) return aTarget - bTarget;
        if (Boolean(b.isMilestone) !== Boolean(a.isMilestone)) {
          return a.isMilestone ? -1 : 1;
        }
        return a.sortOrder - b.sortOrder;
      })
      .slice(0, 8);
  }, [allOpenTasks]);

  const pinnedInCurrentView = useMemo(
    () => visibleViewTasks.filter((tk) => !tk.isDeleted && Boolean(tk.isPinned)),
    [visibleViewTasks]
  );

  // ==================== MODE A: SELECTED TASK INSPECTOR ====================
  if (selectedTask) {
    const activeChecklists = checklistItems
      .filter((c) => !c.isDeleted && c.taskId === selectedTask.id)
      .sort((a, b) => a.sortOrder - b.sortOrder);
    const doneChecklistsCount = activeChecklists.filter((c) => c.isCompleted).length;

    const proj = selectedTask.projectId
      ? projectsMap.get(selectedTask.projectId)
      : undefined;
    const area =
      proj?.areaId != null
        ? areasMap.get(proj.areaId)
        : selectedTask.areaId
          ? areasMap.get(selectedTask.areaId)
          : undefined;
    const containerBreadcrumb =
      area && proj
        ? `${area.title} › ${proj.title}`
        : proj
          ? proj.title
          : area
            ? area.title
            : t.noProjectNoArea;

    const taskTags = selectedTask.tagIds
      .map((id) => tagsMap.get(id))
      .filter((tg): tg is TagRecord => tg != null);

    const tomorrowEpoch = addDaysLocal(todayStartMs, 1);
    const isTomorrowSelected =
      selectedTask.scheduleState === 'UPCOMING' &&
      selectedTask.whenDate != null &&
      daysBetweenToday(selectedTask.whenDate) === 1;

    const dlDiff =
      selectedTask.deadline != null ? daysBetweenToday(selectedTask.deadline) : null;
    const dlUrgent = dlDiff != null && dlDiff <= 2 && selectedTask.status === 'OPEN';
    const dlLabel =
      selectedTask.deadline != null && dlDiff != null
        ? `${formatShortDate(selectedTask.deadline)} · ${
            dlDiff === 0
              ? t.deadlineToday
              : dlDiff > 0
                ? t.deadlineDaysLeft(dlDiff)
                : t.deadlineDaysOverdue(Math.abs(dlDiff))
          }`
        : t.actionDeadline;

    const timeblockLabel =
      selectedTask.scheduledStartMinutes != null
        ? formatTimeblockRange(
            selectedTask.scheduledStartMinutes,
            selectedTask.durationMinutes
          )
        : t.actionTimeblock;

    const durationLabel =
      selectedTask.durationMinutes != null && selectedTask.durationMinutes > 0
        ? formatStandaloneDuration(selectedTask.durationMinutes)
        : t.durationLabel;

    const effortLabel =
      selectedTask.effortLevel === 'QUICK_WIN'
        ? `⚡ ${t.effortQuickWin}`
        : selectedTask.effortLevel === 'MEDIUM'
          ? t.effortMedium
          : selectedTask.effortLevel === 'DEEP_FOCUS'
            ? `🧠 ${t.effortDeepFocus}`
            : t.effortLabel;

    const focusMinutes =
      selectedTask.durationMinutes != null && selectedTask.durationMinutes > 0
        ? Math.min(720, selectedTask.durationMinutes)
        : 25;

    const commitChecklistAdd = async () => {
      const clean = newChecklistTitle.trim();
      if (!clean) return;
      setNewChecklistTitle('');
      await localDb.addChecklistItems(selectedTask.id, [clean]);
    };

    const isCompleted = selectedTask.status === 'COMPLETED';
    const isCanceled = selectedTask.status === 'CANCELED';
    const isSomedayDashed =
      selectedTask.status === 'OPEN' && selectedTask.scheduleState === 'SOMEDAY';

    return (
      <aside
        className="workspace-inspector-panel"
        data-testid="workspace-inspector-panel"
        data-inspector-mode="task"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="inspector-header" data-testid="inspector-task-header">
          <span className="inspector-header-title">{t.inspectorTitle}</span>
          <div className="inspector-header-actions">
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-open-modal-btn"
              title={t.openDetailsAction}
              aria-label={t.openDetailsAction}
              onClick={() => onOpenTaskEditor(selectedTask.id)}
            >
              <Maximize2 size={15} />
            </button>
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-deselect-btn"
              title={t.ctxDeselect}
              aria-label={t.ctxDeselect}
              onClick={onClearSelection}
            >
              <X size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-collapse-btn"
              title={t.inspectorToggleTooltip}
              aria-label={t.inspectorToggleTooltip}
              onClick={onCollapsePanel}
            >
              <PanelRightClose size={16} />
            </button>
          </div>
        </div>

        <div className="inspector-scroll-body">
          {/* 1. Title & Flags Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-title-card"
          >
            <div className="inspector-title-row">
              <button
                type="button"
                className={`things-checkbox ${
                  selectedTask.isMilestone ? 'milestone' : ''
                } ${isSomedayDashed ? 'someday-dashed' : ''} ${
                  isCompleted ? 'completed checked' : ''
                } ${isCanceled ? 'canceled' : ''}`}
                title={
                  selectedTask.status === 'OPEN' ? t.actionComplete : t.actionReopenTodo
                }
                aria-label={
                  selectedTask.status === 'OPEN' ? t.actionComplete : t.actionReopenTodo
                }
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    status: selectedTask.status === 'OPEN' ? 'COMPLETED' : 'OPEN'
                  })
                }
              >
                {isCompleted ? (
                  <Check size={12.5} strokeWidth={3} />
                ) : isCanceled ? (
                  <X size={12.5} strokeWidth={3} />
                ) : null}
              </button>

              <input
                type="text"
                className="inspector-title-input"
                data-testid="inspector-task-title-input"
                value={titleDraft}
                placeholder={t.placeholderTodoTitle}
                onFocus={() => setIsTitleFocused(true)}
                onBlur={() => {
                  setIsTitleFocused(false);
                  const clean = titleDraft.trim();
                  if (clean !== selectedTask.title) {
                    void localDb.upsertTask({ id: selectedTask.id, title: clean });
                  }
                }}
                onChange={(e) => {
                  const next = e.target.value;
                  setTitleDraft(next);
                  void localDb.upsertTask({ id: selectedTask.id, title: next });
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    e.currentTarget.blur();
                  }
                }}
              />
            </div>

            <div className="inspector-flags-bar">
              <button
                type="button"
                className={`chip-btn ${selectedTask.isPinned ? 'active' : ''}`}
                data-testid="inspector-pin-btn"
                title={selectedTask.isPinned ? t.unpinTask : t.pinTask}
                aria-label={selectedTask.isPinned ? t.unpinTask : t.pinTask}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    isPinned: !selectedTask.isPinned
                  })
                }
              >
                {selectedTask.isPinned ? <PinOff size={12} /> : <Pin size={12} />}
                <span>{selectedTask.isPinned ? t.pinnedBadge : t.pinTask}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.isMilestone ? 'milestone-active' : ''
                }`}
                data-testid="inspector-milestone-btn"
                title={
                  selectedTask.isMilestone ? t.removeMilestone : t.markAsMilestone
                }
                aria-label={
                  selectedTask.isMilestone ? t.removeMilestone : t.markAsMilestone
                }
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    isMilestone: !selectedTask.isMilestone
                  })
                }
              >
                <Diamond size={12} />
                <span>{t.milestoneLabel}</span>
              </button>
            </div>
          </section>

          {/* 2. Schedule & Properties Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-schedule-card"
          >
            <div className="inspector-card-header-between">
              <span className="inspector-card-label">{t.actionWhen}</span>
              <button
                type="button"
                className="icon-btn inspector-mini-icon-btn"
                title={t.ctxWhenReminder}
                aria-label={t.ctxWhenReminder}
                onClick={() => onOpenWhenModal(selectedTask.id)}
              >
                <Calendar size={13.5} />
              </button>
            </div>

            <div className="inspector-quick-when-grid">
              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'TODAY' ? 'active' : ''
                }`}
                data-testid="inspector-when-today"
                title={t.viewToday}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'TODAY',
                    whenDate: todayStartMs
                  })
                }
              >
                <Sun size={12} style={{ color: 'var(--today-star)' }} />
                <span>{t.viewToday}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'THIS_EVENING' ? 'evening-quick' : ''
                }`}
                data-testid="inspector-when-evening"
                title={t.viewThisEvening}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'THIS_EVENING',
                    whenDate: todayStartMs
                  })
                }
              >
                <Moon size={12} />
                <span>{t.viewThisEvening}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${isTomorrowSelected ? 'active' : ''}`}
                data-testid="inspector-when-tomorrow"
                title={t.dateTomorrow}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'UPCOMING',
                    whenDate: tomorrowEpoch
                  })
                }
              >
                <CalendarDays size={12} />
                <span>{t.dateTomorrow}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'SOMEDAY' ? 'active' : ''
                }`}
                data-testid="inspector-when-someday"
                title={t.viewSomeday}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'SOMEDAY',
                    whenDate: null,
                    scheduledStartMinutes: null
                  })
                }
              >
                <Cloud size={12} />
                <span>{t.viewSomeday}</span>
              </button>
            </div>

            <div className="inspector-prop-pills-wrap">
              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.deadline != null
                    ? dlUrgent
                      ? 'urgent'
                      : 'active'
                    : ''
                }`}
                data-testid="inspector-deadline-btn"
                title={t.actionDeadline}
                onClick={() => onOpenDeadlineModal(selectedTask.id)}
              >
                <Flag size={12} />
                <span>{dlLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduledStartMinutes != null || showTimeblockPicker
                    ? 'active'
                    : ''
                }`}
                data-testid="inspector-timeblock-btn"
                title={t.actionTimeblock}
                onClick={() => {
                  setShowDurationPicker(false);
                  setShowTimeblockPicker((v) => !v);
                }}
              >
                <Clock size={12} />
                <span>{timeblockLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  (selectedTask.durationMinutes != null &&
                    selectedTask.durationMinutes > 0) ||
                  showDurationPicker
                    ? 'active'
                    : ''
                }`}
                data-testid="inspector-duration-btn"
                title={t.durationLabel}
                onClick={() => {
                  setShowTimeblockPicker(false);
                  setShowDurationPicker((v) => !v);
                }}
              >
                <Hourglass size={12} />
                <span>{durationLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${selectedTask.effortLevel ? 'active' : ''}`}
                data-testid="inspector-effort-btn"
                title={t.effortLabel}
                onClick={() =>
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    effortLevel: nextCycledEffort(selectedTask.effortLevel)
                  })
                }
              >
                <Zap size={12} />
                <span>{effortLabel}</span>
              </button>
            </div>

            {showTimeblockPicker && (
              <div className="inspector-inline-picker">
                <div className="inspector-inline-slots">
                  {TIMEBLOCK_QUICK_START_MINUTES.map((mins) => (
                    <button
                      key={mins}
                      type="button"
                      className={`chip-btn ${
                        selectedTask.scheduledStartMinutes === mins ? 'active' : ''
                      }`}
                      title={`${t.timeblockStartTime}: ${formatMinutesAsClock(mins)}`}
                      onClick={() =>
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          scheduledStartMinutes: mins,
                          durationMinutes:
                            selectedTask.durationMinutes ??
                            DEFAULT_TIMEBLOCK_DURATION_MINUTES
                        })
                      }
                    >
                      {formatMinutesAsClock(mins)}
                    </button>
                  ))}
                  {selectedTask.scheduledStartMinutes != null && (
                    <button
                      type="button"
                      className="chip-btn urgent"
                      title={t.timeblockClear}
                      onClick={() =>
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          scheduledStartMinutes: null
                        })
                      }
                    >
                      {t.actionClear}
                    </button>
                  )}
                </div>
              </div>
            )}

            {showDurationPicker && (
              <div className="inspector-inline-picker">
                <div className="inspector-inline-slots">
                  {STANDALONE_DURATION_PRESETS.map((dur) => (
                    <button
                      key={dur}
                      type="button"
                      className={`chip-btn ${
                        selectedTask.durationMinutes === dur ? 'active' : ''
                      }`}
                      title={`${t.durationLabel}: ${formatStandaloneDuration(dur)}`}
                      onClick={() =>
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          durationMinutes: dur
                        })
                      }
                    >
                      {formatStandaloneDuration(dur)}
                    </button>
                  ))}
                  {selectedTask.durationMinutes != null && (
                    <button
                      type="button"
                      className="chip-btn urgent"
                      title={t.durationClear}
                      onClick={() =>
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          durationMinutes: null
                        })
                      }
                    >
                      {t.actionClear}
                    </button>
                  )}
                </div>
              </div>
            )}

            <div className="inspector-org-row">
              <button
                type="button"
                className={`chip-btn inspector-breadcrumb-btn ${
                  proj || area ? 'active' : ''
                }`}
                data-testid="inspector-container-btn"
                title={t.ctxMoveContainer}
                onClick={() => onOpenMoveModal(selectedTask.id)}
              >
                <Folder size={12} />
                <span>{containerBreadcrumb}</span>
              </button>
            </div>

            <div className="inspector-tags-row">
              {taskTags.map((tg) => (
                <button
                  key={tg.id}
                  type="button"
                  className="chip-btn active"
                  title={`${t.entityTag}: ${tg.title}`}
                  onClick={() => onOpenTagsModal(selectedTask.id)}
                >
                  <LayoutGrid size={11} />
                  <span>{tg.title}</span>
                </button>
              ))}
              <button
                type="button"
                className="chip-btn"
                data-testid="inspector-tags-btn"
                title={t.ctxAssignTags}
                onClick={() => onOpenTagsModal(selectedTask.id)}
              >
                <Plus size={11.5} />
                <span>{t.actionTags}</span>
              </button>
            </div>

            {selectedTask.status === 'OPEN' && (
              <button
                type="button"
                className="inspector-focus-timer-btn"
                data-testid="inspector-start-focus-btn"
                title={t.actionFocusTimerMinutes(focusMinutes)}
                onClick={() =>
                  onStartFocusTimer(
                    selectedTask.id,
                    selectedTask.title,
                    selectedTask.durationMinutes
                  )
                }
              >
                <Timer size={14} />
                <span>{t.actionFocusTimerMinutes(focusMinutes)}</span>
              </button>
            )}
          </section>

          {/* 3. Checklist Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-checklist-card"
          >
            <div className="inspector-card-label">
              <CheckSquare size={12.5} />
              <span>
                {t.actionChecklist} ({doneChecklistsCount}/{activeChecklists.length})
              </span>
            </div>

            {activeChecklists.length > 0 && (
              <div className="inspector-checklist-list">
                {activeChecklists.map((item) => (
                  <InspectorChecklistRow key={item.id} item={item} t={t} />
                ))}
              </div>
            )}

            <div className="checklist-add-row checklist-add-boxed inspector-checklist-add">
              <Circle
                size={13}
                style={{ color: 'var(--things-blue)', flexShrink: 0 }}
              />
              <input
                type="text"
                className="checklist-input"
                data-testid="inspector-checklist-add-input"
                value={newChecklistTitle}
                placeholder={t.placeholderAddChecklist}
                onChange={(e) => setNewChecklistTitle(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && newChecklistTitle.trim()) {
                    e.preventDefault();
                    void commitChecklistAdd();
                  }
                }}
              />
              {newChecklistTitle.trim() && (
                <button
                  type="button"
                  className="chip-btn active"
                  data-testid="inspector-checklist-add-btn"
                  title={t.actionChecklist}
                  aria-label={t.actionChecklist}
                  onClick={() => void commitChecklistAdd()}
                >
                  <Plus size={12} />
                </button>
              )}
            </div>
          </section>

          {/* 4. Notes Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-notes-card"
          >
            <div className="inspector-card-label">
              <FileText size={12.5} />
              <span>{t.placeholderNotes}</span>
            </div>

            <textarea
              ref={notesTextareaRef}
              className="inspector-notes-textarea"
              data-testid="inspector-task-notes-input"
              rows={3}
              value={notesDraft}
              placeholder={t.placeholderNotes}
              onFocus={() => setIsNotesFocused(true)}
              onBlur={() => {
                setIsNotesFocused(false);
                if (notesDraft !== selectedTask.notes) {
                  void localDb.upsertTask({ id: selectedTask.id, notes: notesDraft });
                }
              }}
              onChange={(e) => {
                const next = e.target.value;
                setNotesDraft(next);
                void localDb.upsertTask({ id: selectedTask.id, notes: next });
              }}
            />

            <DetectedNotesLinksRow
              links={extractDetectedLinksFromNotes(notesDraft)}
            />
          </section>
        </div>
      </aside>
    );
  }

  // ==================== MODE B: DAY & WORKSPACE OVERVIEW ====================
  const dailyCapacityMinutes = Math.max(
    60,
    workdayBounds.endMinutes - workdayBounds.startMinutes
  );
  const busyMinutes = Math.max(0, dailyCapacityMinutes - todayCapacity.freeMinutes);
  const capacityPct = Math.min(
    100,
    Math.round((busyMinutes / dailyCapacityMinutes) * 100)
  );
  const isOverCapacity =
    (sevenDayWorkload[0]?.isOverloaded ?? false) ||
    todayCapacity.plannedMinutes > dailyCapacityMinutes;

  return (
    <aside
      className="workspace-inspector-panel"
      data-testid="workspace-inspector-panel"
      data-inspector-mode="overview"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="inspector-header" data-testid="inspector-overview-header">
        <span className="inspector-header-title">{t.inspectorOverviewTitle}</span>
        <div className="inspector-header-actions">
          <button
            type="button"
            className="icon-btn"
            data-testid="inspector-collapse-btn"
            title={t.inspectorToggleTooltip}
            aria-label={t.inspectorToggleTooltip}
            onClick={onCollapsePanel}
          >
            <PanelRightClose size={16} />
          </button>
        </div>
      </div>

      <div className="inspector-scroll-body">
        {/* 1. Today Capacity & Progress Card */}
        <section
          className="inspector-bento-card"
          data-testid="inspector-overview-capacity-card"
        >
          <div className="inspector-card-header-between">
            <span className="inspector-card-label">
              <Sun size={13} style={{ color: 'var(--today-star)' }} />
              <span>{t.viewToday}</span>
            </span>
            <span className="inspector-counts-pill">
              {todayTasks.length} {t.statOpen} · {todayCompletedCount} {t.statCompleted}
            </span>
          </div>

          <div className="inspector-capacity-text">
            {t.todayCapacitySummary(
              formatStandaloneDuration(todayCapacity.plannedMinutes),
              formatStandaloneDuration(todayCapacity.freeMinutes)
            )}
          </div>

          <div className="inspector-capacity-track" aria-hidden="true">
            <div
              className={`inspector-capacity-fill ${isOverCapacity ? 'over' : ''}`}
              style={{ width: `${capacityPct}%` }}
            />
          </div>
        </section>

        {/* 1b. v3.1 Compact 7-Day Workload Forecast Bar Strip Card */}
        <SevenDayWorkloadCard
          forecast={sevenDayWorkload}
          lang={lang}
          t={t}
          compact
        />

        {/* 2. Today's Schedule Mini-Timeline Card */}
        <section
          className="inspector-bento-card"
          data-testid="inspector-overview-timeline-card"
        >
          <div className="inspector-card-label">
            <Clock size={12.5} />
            <span>{t.todayModeTimeline}</span>
          </div>

          {dailyTimeline.allDayEvents.length === 0 &&
          dailyTimeline.timedItems.length === 0 ? (
            <div className="inspector-empty-hint">{t.timelineEmptyScheduled}</div>
          ) : (
            <div className="inspector-mini-list">
              {dailyTimeline.allDayEvents.map((ev) => (
                <div
                  key={`allday-${ev.calendarId}-${ev.eventId}`}
                  className="inspector-mini-calendar-row"
                >
                  <span
                    className="inspector-cal-accent"
                    style={{ background: ev.calendarColorCss || 'var(--things-blue)' }}
                  />
                  <span className="inspector-mini-time">{t.calendarAllDay}</span>
                  <span className="inspector-mini-title">
                    {ev.title || t.calendarUntitledEvent}
                  </span>
                </div>
              ))}

              {dailyTimeline.timedItems.map((item) => {
                if (item.kind === 'calendar' && item.calendarEvent) {
                  return (
                    <div key={item.id} className="inspector-mini-calendar-row">
                      <span
                        className="inspector-cal-accent"
                        style={{
                          background:
                            item.calendarEvent.calendarColorCss || 'var(--things-blue)'
                        }}
                      />
                      <span className="inspector-mini-time">
                        {formatMinutesAsClock(item.startMinutes)}
                      </span>
                      <span className="inspector-mini-title">
                        {item.title || t.calendarUntitledEvent}
                      </span>
                    </div>
                  );
                }

                if (item.task) {
                  const tk = item.task;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      className={`inspector-mini-task-btn ${
                        tk.isMilestone ? 'milestone' : ''
                      }`}
                      title={tk.title || t.placeholderTodoTitle}
                      onClick={() => onSelectTaskFromInspector(tk)}
                      onDoubleClick={() => onOpenTaskEditor(tk.id)}
                    >
                      <span className="inspector-mini-time">
                        {formatMinutesAsClock(item.startMinutes)}
                      </span>
                      {tk.isMilestone && (
                        <span className="inspector-milestone-diamond-badge" aria-hidden="true">
                          ◆
                        </span>
                      )}
                      <span className="inspector-mini-title">
                        {tk.title || t.placeholderTodoTitle}
                      </span>
                    </button>
                  );
                }
                return null;
              })}
            </div>
          )}
        </section>

        {/* 3. Upcoming Deadlines & Milestones Card */}
        <section
          className="inspector-bento-card"
          data-testid="inspector-overview-deadlines-card"
        >
          <div className="inspector-card-label">
            <Flag size={12.5} style={{ color: 'var(--deadline-red)' }} />
            <span>{t.inspectorUpcomingDeadlines}</span>
          </div>

          {upcomingDeadlinesAndMilestones.length === 0 ? (
            <div className="inspector-empty-hint">{t.weeklyReviewNoUrgentDeadlines}</div>
          ) : (
            <div className="inspector-mini-list">
              {upcomingDeadlinesAndMilestones.map((tk) => {
                const targetMs = tk.deadline ?? tk.whenDate ?? null;
                const diff = targetMs != null ? daysBetweenToday(targetMs) : null;
                const isUrgent = diff != null && diff <= 2;
                const badgeText =
                  diff == null
                    ? t.milestoneLabel
                    : diff === 0
                      ? t.deadlineToday
                      : diff < 0
                        ? t.deadlineDaysOverdue(Math.abs(diff))
                        : formatShortDate(targetMs!);

                return (
                  <button
                    key={tk.id}
                    type="button"
                    className="inspector-mini-task-btn"
                    data-testid={`inspector-deadline-row-${tk.id}`}
                    title={tk.title || t.placeholderTodoTitle}
                    onClick={() => onSelectTaskFromInspector(tk)}
                    onDoubleClick={() => onOpenTaskEditor(tk.id)}
                  >
                    {tk.isMilestone && (
                      <span
                        className="inspector-milestone-diamond-badge"
                        data-testid="inspector-milestone-indicator"
                      >
                        ◆
                      </span>
                    )}
                    <span className="inspector-mini-title">
                      {tk.title || t.placeholderTodoTitle}
                    </span>
                    <span
                      className={`inspector-urgency-badge ${
                        isUrgent ? 'urgent' : tk.isMilestone ? 'milestone' : ''
                      }`}
                    >
                      {badgeText}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        {/* 4. Pinned Tasks Card (if current visible tasks include isPinned tasks) */}
        {pinnedInCurrentView.length > 0 && (
          <section
            className="inspector-bento-card"
            data-testid="inspector-overview-pinned-card"
          >
            <div className="inspector-card-label">
              <Pin size={12.5} style={{ color: 'var(--things-blue)' }} />
              <span>
                {t.pinnedBadge} ({pinnedInCurrentView.length})
              </span>
            </div>

            <div className="inspector-mini-list">
              {pinnedInCurrentView.map((tk) => (
                <button
                  key={tk.id}
                  type="button"
                  className="inspector-mini-task-btn"
                  data-testid={`inspector-pinned-row-${tk.id}`}
                  title={tk.title || t.placeholderTodoTitle}
                  onClick={() => onSelectTaskFromInspector(tk)}
                  onDoubleClick={() => onOpenTaskEditor(tk.id)}
                >
                  {tk.isMilestone ? (
                    <span className="inspector-milestone-diamond-badge">◆</span>
                  ) : (
                    <Pin
                      size={11.5}
                      style={{ color: 'var(--things-blue)', flexShrink: 0 }}
                    />
                  )}
                  <span className="inspector-mini-title">
                    {tk.title || t.placeholderTodoTitle}
                  </span>
                </button>
              ))}
            </div>
          </section>
        )}
      </div>
    </aside>
  );
};
