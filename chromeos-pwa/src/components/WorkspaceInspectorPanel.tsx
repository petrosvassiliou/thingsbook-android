import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  BarChart3,
  Bell,
  Calendar,
  Camera,
  Check,
  CheckCircle2,
  CheckSquare,
  Circle,
  Clock,
  Cloud,
  Copy,
  Diamond,
  FileText,
  Flag,
  Folder,
  Hourglass,
  LayoutGrid,
  Link2,
  Maximize2,
  Mic,
  Moon,
  MoreHorizontal,
  PanelRightClose,
  Paperclip,
  Pin,
  PinOff,
  Plus,
  Repeat,
  RotateCcw,
  Sparkles,
  Sun,
  Timer,
  Trash2,
  X,
  XCircle,
  Zap
} from 'lucide-react';
import {
  daysBetweenToday,
  formatRecurrenceShort,
  type AreaRecord,
  type AttachmentRecord,
  type CalendarEventItem,
  type ChecklistItemRecord,
  type EffortLevel,
  type HeadingRecord,
  type ProjectRecord,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus
} from '../domain/types';
import {
  DEFAULT_TIMEBLOCK_DURATION_MINUTES,
  DEFAULT_WORKDAY_END_MINUTES,
  DEFAULT_WORKDAY_START_MINUTES,
  STANDALONE_DURATION_PRESETS,
  TIMEBLOCK_DURATION_PRESETS,
  TIMEBLOCK_QUICK_START_MINUTES,
  buildDailyTimeline,
  computeMultiDayWorkloadForecast,
  computeTodayCapacitySummary,
  formatMinutesAsClock,
  formatStandaloneDuration,
  formatTimeblockRange,
  isTaskActivelyBlocked,
  normalizeWorkdayBounds,
  parseClockToMinutes,
  parseCustomDurationMinutes,
  type DayWorkloadSummary
} from '../domain/v2Utils';
import {
  parseSmartTaskSyntax,
  type ParsedSmartSyntax
} from '../domain/smartSyntaxParser';
import { generateMagicChecklist } from '../data/geminiService';
import { processAndAddAttachmentFile } from '../data/attachmentHelpers';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';
import { MarkdownNotesField } from './MarkdownNotesField';
import { AttachmentsStrip } from './AttachmentStrip';
import { InProgressIcon } from './InProgressIcon';
import { extractDraggedTaskIds, hasDraggedTaskMime } from '../domain/v30Roadmap';

const EMPTY_OPEN_ID_SET: ReadonlySet<string> = new Set();
const EMPTY_TASKS_MAP: ReadonlyMap<string, TaskRecord> = new Map();
const EMPTY_HEADINGS: readonly HeadingRecord[] = [];
const EMPTY_ATTACHMENTS: readonly AttachmentRecord[] = [];
const NOOP_TOAST = (_msg: string) => {};

interface InspectorChecklistRowProps {
  item: ChecklistItemRecord;
  idx: number;
  totalCount: number;
  taskId: string;
  t: TranslationStrings;
  registerRef: (el: HTMLInputElement | null) => void;
  onFocusAddBottom: () => void;
  onFocusNewSubtask: (newId: string) => void;
  onSwapWith: (dir: 'up' | 'down') => void;
}

const InspectorChecklistRow: React.FC<InspectorChecklistRowProps> = ({
  item,
  idx,
  totalCount,
  taskId,
  t,
  registerRef,
  onFocusAddBottom,
  onFocusNewSubtask,
  onSwapWith
}) => {
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
        ref={registerRef}
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
        onKeyDown={async (e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            const inputEl = e.currentTarget;
            const val = subTitle;
            if (!val.trim()) {
              await localDb.deleteChecklistItem(item.id);
              onFocusAddBottom();
              return;
            }
            const cursor = inputEl.selectionStart ?? val.length;
            if (cursor < val.length) {
              const leftPart = val.slice(0, cursor).trimEnd();
              const rightPart = val.slice(cursor).trimStart();
              setSubTitle(leftPart);
              const newId = await localDb.insertChecklistItemAfter(
                taskId,
                item.id,
                rightPart,
                leftPart
              );
              onFocusNewSubtask(newId);
            } else {
              const newId = await localDb.insertChecklistItemAfter(taskId, item.id, '', val);
              onFocusNewSubtask(newId);
            }
          }
        }}
      />

      <div className="inspector-checklist-row-actions">
        <button
          type="button"
          className="icon-btn inspector-checklist-del-btn"
          disabled={idx === 0}
          title={t.actionMove}
          aria-label={t.actionMove}
          onClick={() => onSwapWith('up')}
        >
          <ArrowUp size={12} />
        </button>
        <button
          type="button"
          className="icon-btn inspector-checklist-del-btn"
          disabled={idx === totalCount - 1}
          title={t.actionMove}
          aria-label={t.actionMove}
          onClick={() => onSwapWith('down')}
        >
          <ArrowDown size={12} />
        </button>
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
    </div>
  );
};

export interface SevenDayWorkloadCardProps {
  forecast: readonly DayWorkloadSummary[];
  lang: SupportedLang;
  t: TranslationStrings;
  compact?: boolean;
  selectedTaskIds?: readonly string[];
  orderedVisibleTaskIds?: readonly string[];
  onJumpToWorkloadDay?: (day: DayWorkloadSummary, isToday: boolean) => void;
  onDropTasksOnWorkloadDay?: (
    taskIds: string[],
    day: DayWorkloadSummary,
    isToday: boolean
  ) => void;
}

export const SevenDayWorkloadCard: React.FC<SevenDayWorkloadCardProps> = ({
  forecast,
  lang,
  t,
  compact = false,
  selectedTaskIds,
  orderedVisibleTaskIds,
  onJumpToWorkloadDay,
  onDropTasksOnWorkloadDay
}) => {
  const [dragOverEpochDay, setDragOverEpochDay] = useState<number | null>(null);

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
          const isToday = idx === 0;
          const isDragOver = dragOverEpochDay === day.epochDay;
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
              data-testid={`workload-day-col-${idx}`}
              role="button"
              tabIndex={0}
              className={`v31-workload-day-col ${isToday ? 'today' : ''} ${
                day.isOverloaded ? 'overloaded' : ''
              } ${isDragOver ? 'drag-over' : ''}`}
              title={tip}
              aria-label={tip}
              onClick={() => onJumpToWorkloadDay?.(day, isToday)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onJumpToWorkloadDay?.(day, isToday);
                }
              }}
              onDragOver={(e) => {
                if (hasDraggedTaskMime(e.dataTransfer)) {
                  e.preventDefault();
                  e.dataTransfer.dropEffect = 'move';
                  if (dragOverEpochDay !== day.epochDay) {
                    setDragOverEpochDay(day.epochDay);
                  }
                }
              }}
              onDragLeave={(e) => {
                if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
                  setDragOverEpochDay((cur) => (cur === day.epochDay ? null : cur));
                }
              }}
              onDrop={(e) => {
                e.preventDefault();
                setDragOverEpochDay(null);
                const ids = extractDraggedTaskIds(
                  e.dataTransfer,
                  [...(selectedTaskIds ?? [])],
                  [...(orderedVisibleTaskIds ?? [])]
                );
                if (ids.length > 0) {
                  onDropTasksOnWorkloadDay?.(ids, day, isToday);
                }
              }}
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
  headings?: readonly HeadingRecord[];
  attachments?: readonly AttachmentRecord[];
  openTaskIdSet?: ReadonlySet<string>;
  tasksByIdMap?: ReadonlyMap<string, TaskRecord>;
  selectedTaskIds?: readonly string[];
  orderedVisibleTaskIds?: readonly string[];
  areasMap: Map<string, AreaRecord>;
  projectsMap: Map<string, ProjectRecord>;
  tagsMap: Map<string, TagRecord>;
  lang: SupportedLang;
  geminiApiKey?: string;
  t: TranslationStrings;
  onToast?: (msg: string) => void;
  onJumpToWorkloadDay?: (day: DayWorkloadSummary, isToday: boolean) => void;
  onDropTasksOnWorkloadDay?: (
    taskIds: string[],
    day: DayWorkloadSummary,
    isToday: boolean
  ) => void;
  onSelectTaskFromInspector: (task: TaskRecord) => void;
  onOpenTaskEditor: (taskId: string) => void;
  onClearSelection: () => void;
  onCollapsePanel: () => void;
  onOpenWhenModal: (taskId: string) => void;
  onOpenDeadlineModal: (taskId: string) => void;
  onOpenRepeatModal?: (taskId: string) => void;
  onOpenMoveModal: (taskId: string) => void;
  onOpenTagsModal: (taskId: string) => void;
  onOpenDependenciesModal?: (taskId: string) => void;
  onDuplicateTask?: (taskId: string) => void | Promise<void>;
  onStartFocusTimer: (
    taskId: string,
    taskTitle: string,
    durationMinutes?: number | null
  ) => void;
}

function buildSmartSyntaxPatch(parsed: ParsedSmartSyntax, baseTask: TaskRecord) {
  return {
    id: baseTask.id,
    title: parsed.cleanTitle,
    ...(parsed.scheduleState != null
      ? { scheduleState: parsed.scheduleState, whenDate: parsed.whenDate }
      : {}),
    ...(parsed.reminderTime != null ? { reminderTime: parsed.reminderTime } : {}),
    ...(parsed.deadline != null ? { deadline: parsed.deadline } : {}),
    ...(parsed.durationMinutes != null ? { durationMinutes: parsed.durationMinutes } : {}),
    ...(parsed.matchedTagIds.length > 0 ? { addTagIds: parsed.matchedTagIds } : {}),
    ...(parsed.matchedProject
      ? {
          projectId: parsed.matchedProject.id,
          headingId:
            baseTask.projectId === parsed.matchedProject.id ? baseTask.headingId : null,
          areaId: null
        }
      : parsed.matchedArea
        ? {
            projectId: null,
            headingId: null,
            areaId: parsed.matchedArea.id
          }
        : {}),
    ...(parsed.isMilestone ? { isMilestone: true } : {}),
    ...(parsed.isInProgress ? { isInProgress: true } : {})
  };
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
  headings = EMPTY_HEADINGS,
  attachments = EMPTY_ATTACHMENTS,
  openTaskIdSet = EMPTY_OPEN_ID_SET,
  tasksByIdMap = EMPTY_TASKS_MAP,
  selectedTaskIds,
  orderedVisibleTaskIds,
  areasMap,
  projectsMap,
  tagsMap,
  lang,
  geminiApiKey = '',
  t,
  onToast = NOOP_TOAST,
  onJumpToWorkloadDay,
  onDropTasksOnWorkloadDay,
  onSelectTaskFromInspector,
  onOpenTaskEditor,
  onClearSelection,
  onCollapsePanel,
  onOpenWhenModal,
  onOpenDeadlineModal,
  onOpenRepeatModal,
  onOpenMoveModal,
  onOpenTagsModal,
  onOpenDependenciesModal,
  onDuplicateTask,
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
  const [focusSubtaskId, setFocusSubtaskId] = useState<string | null>(null);
  const [loadingAi, setLoadingAi] = useState(false);
  const [showTimeblockPicker, setShowTimeblockPicker] = useState(false);
  const [showDurationPicker, setShowDurationPicker] = useState(false);
  const [showEffortPicker, setShowEffortPicker] = useState(false);
  const [customDurationDraft, setCustomDurationDraft] = useState('');
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const [statusMenuFlipUp, setStatusMenuFlipUp] = useState(false);
  const [moreMenuOpen, setMoreMenuOpen] = useState(false);
  const [fileDropOver, setFileDropOver] = useState(false);

  const subtaskRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const addChecklistInputRef = useRef<HTMLInputElement | null>(null);
  const attachmentFileInputRef = useRef<HTMLInputElement | null>(null);
  const cameraCaptureInputRef = useRef<HTMLInputElement | null>(null);

  if ((selectedTask?.id ?? null) !== prevTaskId) {
    setPrevTaskId(selectedTask?.id ?? null);
    setPrevServerTitle(selectedTask?.title ?? '');
    setPrevServerNotes(selectedTask?.notes ?? '');
    setTitleDraft(selectedTask?.title ?? '');
    setNotesDraft(selectedTask?.notes ?? '');
    setIsTitleFocused(false);
    setIsNotesFocused(false);
    setNewChecklistTitle('');
    setFocusSubtaskId(null);
    setLoadingAi(false);
    setShowTimeblockPicker(false);
    setShowDurationPicker(false);
    setShowEffortPicker(false);
    setCustomDurationDraft('');
    setStatusMenuOpen(false);
    setMoreMenuOpen(false);
    setFileDropOver(false);
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

  useEffect(() => {
    if (focusSubtaskId && subtaskRefs.current[focusSubtaskId]) {
      const el = subtaskRefs.current[focusSubtaskId]!;
      el.focus();
      el.setSelectionRange(0, 0);
      setFocusSubtaskId(null);
    }
  }, [focusSubtaskId, checklistItems]);

  const formatShortDate = (ms: number) =>
    new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' }).format(new Date(ms));

  const formatReminderClock = (ms: number) => {
    const d = new Date(ms);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const allTagsList = useMemo(() => Array.from(tagsMap.values()), [tagsMap]);
  const allProjectsList = useMemo(() => Array.from(projectsMap.values()), [projectsMap]);
  const allAreasList = useMemo(() => Array.from(areasMap.values()), [areasMap]);

  const inspectorParsedSmart = useMemo(
    () =>
      selectedTask
        ? parseSmartTaskSyntax(titleDraft, {
            tags: allTagsList,
            projects: allProjectsList,
            areas: allAreasList,
            lang
          })
        : null,
    [selectedTask, titleDraft, allTagsList, allProjectsList, allAreasList, lang]
  );

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

    const taskAttachments = attachments
      .filter((a) => !a.isDeleted && a.taskId === selectedTask.id)
      .sort((a, b) => a.createdAt - b.createdAt);

    const projectHeadings = selectedTask.projectId
      ? headings
          .filter(
            (h) => !h.isDeleted && !h.isArchived && h.projectId === selectedTask.projectId
          )
          .sort((a, b) => a.sortOrder - b.sortOrder)
      : [];
    const currentHeading = selectedTask.headingId
      ? headings.find((h) => !h.isDeleted && h.id === selectedTask.headingId)
      : undefined;

    const proj = selectedTask.projectId
      ? projectsMap.get(selectedTask.projectId)
      : undefined;
    const area =
      proj?.areaId != null
        ? areasMap.get(proj.areaId)
        : selectedTask.areaId
          ? areasMap.get(selectedTask.areaId)
          : undefined;
    const baseBreadcrumb =
      area && proj
        ? `${area.title} › ${proj.title}`
        : proj
          ? proj.title
          : area
            ? area.title
            : t.noProjectNoArea;
    const containerBreadcrumb =
      proj && currentHeading
        ? `${baseBreadcrumb} › ${currentHeading.title}`
        : baseBreadcrumb;

    const taskTags = selectedTask.tagIds
      .map((id) => tagsMap.get(id))
      .filter((tg): tg is TagRecord => tg != null);

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
    const currentTimeblockDuration =
      selectedTask.durationMinutes ?? DEFAULT_TIMEBLOCK_DURATION_MINUTES;

    const durationLabel =
      selectedTask.durationMinutes != null && selectedTask.durationMinutes > 0
        ? formatStandaloneDuration(selectedTask.durationMinutes)
        : t.durationLabel;

    const actualMinutesValue =
      selectedTask.actualMinutes != null &&
      Number.isFinite(selectedTask.actualMinutes) &&
      selectedTask.actualMinutes > 0
        ? Math.trunc(selectedTask.actualMinutes)
        : 0;
    const actualInspectorBadgeLabel =
      actualMinutesValue > 0
        ? selectedTask.durationMinutes != null && selectedTask.durationMinutes > 0
          ? t.v32ActualVsEstimated(
              formatStandaloneDuration(actualMinutesValue),
              formatStandaloneDuration(selectedTask.durationMinutes)
            )
          : t.v32ActualFocusedShort(formatStandaloneDuration(actualMinutesValue))
        : '';

    const effortLabel =
      selectedTask.effortLevel === 'QUICK_WIN'
        ? `⚡ ${t.effortQuickWin}`
        : selectedTask.effortLevel === 'MEDIUM'
          ? t.effortMedium
          : selectedTask.effortLevel === 'DEEP_FOCUS'
            ? `🧠 ${t.effortDeepFocus}`
            : t.effortLabel;

    const activeBlockerIds = (selectedTask.blockedByTaskIds || []).filter(
      (bid) => bid && bid !== selectedTask.id && openTaskIdSet.has(bid)
    );
    const isActivelyBlocked = isTaskActivelyBlocked(selectedTask, openTaskIdSet);
    const firstBlockerTitle =
      activeBlockerIds.length === 1
        ? (tasksByIdMap.get(activeBlockerIds[0])?.title?.trim() ?? '')
        : '';
    const blockedSummaryLabel = isActivelyBlocked
      ? activeBlockerIds.length === 1 && firstBlockerTitle
        ? t.waitingOnNamedBadge(firstBlockerTitle)
        : t.waitingOnBadge(activeBlockerIds.length)
      : '';
    const totalBlockerCount = (selectedTask.blockedByTaskIds || []).filter(
      (bid) => bid && bid !== selectedTask.id
    ).length;

    const focusMinutes =
      selectedTask.durationMinutes != null && selectedTask.durationMinutes > 0
        ? Math.min(720, selectedTask.durationMinutes)
        : 25;

    const flushDraftsIfNeeded = () => {
      const pendingSub = newChecklistTitle.trim();
      if (pendingSub) {
        setNewChecklistTitle('');
        void localDb.addChecklistItems(selectedTask.id, [pendingSub]);
      }
      const patch: { id: string; title?: string; notes?: string } = { id: selectedTask.id };
      let changed = false;
      const cleanTitle = titleDraft.trim();
      if (cleanTitle !== selectedTask.title) {
        patch.title = cleanTitle;
        changed = true;
      }
      if (notesDraft !== selectedTask.notes) {
        patch.notes = notesDraft;
        changed = true;
      }
      if (changed) {
        void localDb.upsertTask(patch);
      }
    };

    const commitStatus = async (nextStatus: TaskStatus) => {
      flushDraftsIfNeeded();
      await localDb.upsertTask({
        id: selectedTask.id,
        status: nextStatus
      });
    };

    const updateCheckboxMenuPlacement = (target: EventTarget | null) => {
      if (target instanceof HTMLElement) {
        const rect = target.getBoundingClientRect();
        setStatusMenuFlipUp(rect.bottom + 120 > window.innerHeight - 12);
      }
    };

    const triggerInspectorVoiceDictation = () => {
      const w = window as unknown as {
        SpeechRecognition?: new () => {
          lang: string;
          interimResults: boolean;
          onresult:
            | ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void)
            | null;
          start: () => void;
        };
        webkitSpeechRecognition?: new () => {
          lang: string;
          interimResults: boolean;
          onresult:
            | ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void)
            | null;
          start: () => void;
        };
      };
      const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
      if (!Ctor) {
        onToast(t.voiceDictationLabel);
        return;
      }
      try {
        const rec = new Ctor();
        rec.lang =
          lang === 'de'
            ? 'de-DE'
            : lang === 'fr'
              ? 'fr-FR'
              : lang === 'it'
                ? 'it-IT'
                : lang === 'es'
                  ? 'es-ES'
                  : 'en-US';
        rec.interimResults = false;
        rec.onresult = (ev) => {
          const spoken = ev.results?.[0]?.[0]?.transcript?.trim() ?? '';
          if (!spoken) return;
          if (!titleDraft.trim()) {
            setTitleDraft(spoken);
            void localDb.upsertTask({ id: selectedTask.id, title: spoken });
          } else {
            const merged = notesDraft.trim() ? `${notesDraft.trim()}\n${spoken}` : spoken;
            setNotesDraft(merged);
            void localDb.upsertTask({ id: selectedTask.id, notes: merged });
          }
        };
        onToast(t.voiceListeningHint);
        rec.start();
      } catch {
        onToast(t.voiceDictationLabel);
      }
    };

    const copyTaskAsMarkdown = async () => {
      flushDraftsIfNeeded();
      const statusBox =
        selectedTask.status === 'COMPLETED'
          ? '[x]'
          : selectedTask.status === 'CANCELED'
            ? '[-]'
            : '[ ]';
      const cleanTitle = (
        titleDraft.trim() ||
        selectedTask.title ||
        t.placeholderTodoTitle
      ).trim();
      const lines: string[] = [`- ${statusBox} ${cleanTitle}`];
      const cleanNotes = notesDraft.trim();
      if (cleanNotes) {
        for (const nl of cleanNotes.split(/\r?\n/)) {
          lines.push(`  ${nl}`);
        }
      }
      for (const item of activeChecklists) {
        lines.push(`  - [${item.isCompleted ? 'x' : ' '}] ${item.title}`);
      }
      const md = lines.join('\n');
      try {
        await navigator.clipboard.writeText(md);
        onToast(`✓ ${t.copiedMarkdownToast}`);
      } catch {
        onToast(t.actionCopyMarkdown);
      }
    };

    const runMagicBreakdown = async () => {
      if (loadingAi) return;
      flushDraftsIfNeeded();
      setLoadingAi(true);
      try {
        const steps = await generateMagicChecklist(
          titleDraft || selectedTask.title,
          notesDraft || selectedTask.notes,
          lang,
          geminiApiKey
        );
        await localDb.addChecklistItems(selectedTask.id, steps);
        onToast(`✨ +${steps.length} ${t.actionChecklist}`);
      } catch (err) {
        onToast(err instanceof Error ? err.message : 'Gemini error');
      } finally {
        setLoadingAi(false);
      }
    };

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
        style={
          fileDropOver
            ? { outline: '2px dashed var(--things-blue)', outlineOffset: -2 }
            : undefined
        }
        onClick={(e) => e.stopPropagation()}
        onDragOver={(e) => {
          if (e.dataTransfer.types.includes('Files')) {
            e.preventDefault();
            setFileDropOver(true);
          }
        }}
        onDragLeave={() => setFileDropOver(false)}
        onDrop={async (e) => {
          if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            e.preventDefault();
            setFileDropOver(false);
            const files = Array.from(e.dataTransfer.files);
            for (const f of files) {
              await processAndAddAttachmentFile(f, { taskId: selectedTask.id }, t, onToast);
            }
          }
        }}
      >
        <div className="inspector-header" data-testid="inspector-task-header">
          <span className="inspector-header-title">{t.inspectorTitle}</span>
          <div className="inspector-header-actions">
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-voice-dictation-btn"
              title={t.voiceDictationLabel}
              aria-label={t.voiceDictationLabel}
              onClick={triggerInspectorVoiceDictation}
            >
              <Mic size={14.5} />
            </button>

            <div style={{ position: 'relative', display: 'inline-flex' }}>
              <button
                type="button"
                className="icon-btn"
                data-testid="inspector-more-menu-btn"
                title={t.actionMoreOptions}
                aria-label={t.actionMoreOptions}
                onClick={(e) => {
                  e.stopPropagation();
                  setMoreMenuOpen((v) => !v);
                }}
              >
                <MoreHorizontal size={15.5} />
              </button>

              {moreMenuOpen && (
                <>
                  <div
                    style={{ position: 'fixed', inset: 0, zIndex: 85 }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setMoreMenuOpen(false);
                    }}
                  />
                  <div
                    className="dropdown-popover"
                    style={{
                      right: 0,
                      left: 'auto',
                      top: 'calc(100% + 6px)',
                      minWidth: 215,
                      zIndex: 95
                    }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setMoreMenuOpen(false);
                        attachmentFileInputRef.current?.click();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Paperclip size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.attachmentsAddPhotoBtn}</span>
                      </span>
                    </button>

                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setMoreMenuOpen(false);
                        cameraCaptureInputRef.current?.click();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Camera size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.actionTakePhoto}</span>
                      </span>
                    </button>

                    <button
                      type="button"
                      className="dropdown-item"
                      onClick={() => {
                        setMoreMenuOpen(false);
                        triggerInspectorVoiceDictation();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Mic size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.voiceDictationLabel}</span>
                      </span>
                    </button>

                    {onDuplicateTask && (
                      <button
                        type="button"
                        className="dropdown-item"
                        data-testid="inspector-duplicate-btn"
                        onClick={() => {
                          setMoreMenuOpen(false);
                          flushDraftsIfNeeded();
                          void onDuplicateTask(selectedTask.id);
                        }}
                      >
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <Copy size={14} style={{ color: 'var(--things-blue)' }} />
                          <span>{t.actionDuplicateTodo}</span>
                        </span>
                      </button>
                    )}

                    <button
                      type="button"
                      className="dropdown-item"
                      data-testid="inspector-copy-markdown-btn"
                      onClick={() => {
                        setMoreMenuOpen(false);
                        void copyTaskAsMarkdown();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <FileText size={14} style={{ color: 'var(--things-blue)' }} />
                        <span>{t.actionCopyMarkdown}</span>
                      </span>
                    </button>

                    {selectedTask.status !== 'CANCELED' && (
                      <button
                        type="button"
                        className="dropdown-item"
                        data-testid="inspector-cancel-todo-btn"
                        onClick={() => {
                          setMoreMenuOpen(false);
                          void commitStatus('CANCELED');
                        }}
                      >
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <XCircle size={14} style={{ color: 'var(--text-secondary)' }} />
                          <span>{t.actionCancelTodo}</span>
                        </span>
                      </button>
                    )}

                    {selectedTask.status !== 'OPEN' && (
                      <button
                        type="button"
                        className="dropdown-item"
                        data-testid="inspector-reopen-todo-btn"
                        onClick={() => {
                          setMoreMenuOpen(false);
                          void commitStatus('OPEN');
                        }}
                      >
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                          <RotateCcw size={14} style={{ color: 'var(--things-blue)' }} />
                          <span>{t.actionReopenTodo}</span>
                        </span>
                      </button>
                    )}

                    <div className="dropdown-divider" />

                    <button
                      type="button"
                      className="dropdown-item"
                      data-testid="inspector-delete-btn"
                      style={{ color: 'var(--deadline-red)' }}
                      onClick={() => {
                        setMoreMenuOpen(false);
                        void localDb.softDeleteTasks([selectedTask.id]);
                        onClearSelection();
                      }}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Trash2 size={14} style={{ color: 'var(--deadline-red)' }} />
                        <span>{t.actionDelete}</span>
                      </span>
                    </button>
                  </div>
                </>
              )}
            </div>

            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-open-modal-btn"
              title={t.openDetailsAction}
              aria-label={t.openDetailsAction}
              onClick={() => {
                flushDraftsIfNeeded();
                onOpenTaskEditor(selectedTask.id);
              }}
            >
              <Maximize2 size={15} />
            </button>
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-deselect-btn"
              title={t.ctxDeselect}
              aria-label={t.ctxDeselect}
              onClick={() => {
                flushDraftsIfNeeded();
                onClearSelection();
              }}
            >
              <X size={15.5} />
            </button>
            <button
              type="button"
              className="icon-btn"
              data-testid="inspector-collapse-btn"
              title={t.inspectorToggleTooltip}
              aria-label={t.inspectorToggleTooltip}
              onClick={() => {
                flushDraftsIfNeeded();
                onCollapsePanel();
              }}
            >
              <PanelRightClose size={16} />
            </button>
          </div>
        </div>

        <div className="inspector-scroll-body">
          {/* 1. Title, Smart-Syntax Strip & Flags Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-title-card"
          >
            <div className="inspector-title-row">
              <div className="checkbox-anchor" onDoubleClick={(e) => e.stopPropagation()}>
                <button
                  type="button"
                  className={`things-checkbox ${
                    selectedTask.isMilestone ? 'milestone' : ''
                  } ${
                    selectedTask.status === 'OPEN' && selectedTask.isInProgress
                      ? 'in-progress'
                      : ''
                  } ${isSomedayDashed ? 'someday-dashed' : ''} ${
                    isCompleted ? 'completed checked' : ''
                  } ${isCanceled ? 'canceled' : ''}`}
                  title={
                    selectedTask.status === 'OPEN'
                      ? `${t.actionComplete} · Shift+Click: ${
                          selectedTask.isInProgress
                            ? t.v33ClearInProgress
                            : t.v33MarkInProgress
                        } · ${t.shortcutCancelHint}`
                      : t.actionReopenTodo
                  }
                  aria-label={
                    selectedTask.status === 'OPEN' ? t.actionComplete : t.actionReopenTodo
                  }
                  onClick={(e) => {
                    e.stopPropagation();
                    if (e.altKey) {
                      updateCheckboxMenuPlacement(e.currentTarget);
                      setStatusMenuOpen((v) => !v);
                      return;
                    }
                    if (e.shiftKey && selectedTask.status === 'OPEN') {
                      flushDraftsIfNeeded();
                      void localDb.toggleTaskInProgress(selectedTask.id);
                      return;
                    }
                    void commitStatus(selectedTask.status === 'OPEN' ? 'COMPLETED' : 'OPEN');
                  }}
                  onContextMenu={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    updateCheckboxMenuPlacement(e.currentTarget);
                    setStatusMenuOpen(true);
                  }}
                >
                  {isCompleted ? (
                    <Check size={12.5} strokeWidth={3} />
                  ) : isCanceled ? (
                    <X size={12.5} strokeWidth={3} />
                  ) : null}
                </button>

                {statusMenuOpen && (
                  <>
                    <div
                      style={{ position: 'fixed', inset: 0, zIndex: 80 }}
                      onClick={(e) => {
                        e.stopPropagation();
                        setStatusMenuOpen(false);
                      }}
                    />
                    <div
                      className="dropdown-popover"
                      style={{
                        left: 0,
                        right: 'auto',
                        minWidth: 185,
                        zIndex: 90,
                        ...(statusMenuFlipUp
                          ? { top: 'auto', bottom: 'calc(100% + 6px)' }
                          : {})
                      }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      {selectedTask.status === 'OPEN' && (
                        <button
                          type="button"
                          className="dropdown-item"
                          data-testid="inspector-status-menu-in-progress-btn"
                          onClick={() => {
                            setStatusMenuOpen(false);
                            flushDraftsIfNeeded();
                            void localDb.toggleTaskInProgress(selectedTask.id);
                          }}
                        >
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <InProgressIcon
                              size={14}
                              style={{ color: 'var(--in-progress-amber-ring)' }}
                            />
                            <span>
                              {selectedTask.isInProgress
                                ? t.v33ClearInProgress
                                : t.v33MarkInProgress}
                            </span>
                          </span>
                        </button>
                      )}
                      {selectedTask.status !== 'COMPLETED' && (
                        <button
                          type="button"
                          className="dropdown-item"
                          onClick={() => {
                            setStatusMenuOpen(false);
                            void commitStatus('COMPLETED');
                          }}
                        >
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <CheckCircle2 size={14} style={{ color: 'var(--things-blue)' }} />
                            <span>{t.actionComplete}</span>
                          </span>
                        </button>
                      )}
                      {selectedTask.status !== 'CANCELED' && (
                        <button
                          type="button"
                          className="dropdown-item"
                          onClick={() => {
                            setStatusMenuOpen(false);
                            void commitStatus('CANCELED');
                          }}
                        >
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <XCircle size={14} style={{ color: 'var(--text-secondary)' }} />
                            <span>{t.actionCancelTodo}</span>
                          </span>
                        </button>
                      )}
                      {selectedTask.status !== 'OPEN' && (
                        <button
                          type="button"
                          className="dropdown-item"
                          onClick={() => {
                            setStatusMenuOpen(false);
                            void commitStatus('OPEN');
                          }}
                        >
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            <RotateCcw size={14} style={{ color: 'var(--things-blue)' }} />
                            <span>{t.actionReopenTodo}</span>
                          </span>
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>

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

            {inspectorParsedSmart?.hasAnyMatch && (
              <div
                className="v32-smart-syntax-strip compact"
                data-testid="inspector-smart-syntax-strip"
              >
                <span className="v32-smart-syntax-label">{t.v32SmartSyntaxDetected}</span>
                <div className="v32-smart-syntax-pills">
                  {inspectorParsedSmart.scheduleState != null && (
                    <span className="meta-badge today">
                      <Calendar size={10.5} />
                      <span>
                        {inspectorParsedSmart.whenLabel ||
                          inspectorParsedSmart.scheduleState}
                      </span>
                    </span>
                  )}
                  {inspectorParsedSmart.deadline != null && (
                    <span className="meta-badge urgent">
                      <Flag size={10.5} />
                      <span>{inspectorParsedSmart.deadlineLabel}</span>
                    </span>
                  )}
                  {inspectorParsedSmart.durationMinutes != null && (
                    <span className="meta-badge duration-badge">
                      <Hourglass size={10} />
                      <span>
                        {formatStandaloneDuration(inspectorParsedSmart.durationMinutes)}
                      </span>
                    </span>
                  )}
                  {inspectorParsedSmart.isMilestone && (
                    <span className="meta-badge milestone-badge">
                      <Diamond size={10} />
                      <span>{t.milestoneLabel}</span>
                    </span>
                  )}
                  {inspectorParsedSmart.isInProgress && (
                    <span className="meta-badge in-progress-badge">
                      <span>◐ {t.v33InProgressBadge}</span>
                    </span>
                  )}
                  {inspectorParsedSmart.matchedProject && (
                    <span className="meta-badge">
                      <Folder size={10.5} />
                      <span>{inspectorParsedSmart.matchedProject.title}</span>
                    </span>
                  )}
                  {!inspectorParsedSmart.matchedProject &&
                    inspectorParsedSmart.matchedArea && (
                      <span className="meta-badge">
                        <Folder size={10.5} />
                        <span>{inspectorParsedSmart.matchedArea.title}</span>
                      </span>
                    )}
                  {inspectorParsedSmart.matchedTags.map((tg) => (
                    <span key={tg.id} className="meta-badge">
                      <LayoutGrid size={9.5} />
                      <span>#{tg.title}</span>
                    </span>
                  ))}
                </div>
                <button
                  type="button"
                  className="chip-btn active v32-smart-apply-btn"
                  data-testid="inspector-smart-syntax-apply-btn"
                  onClick={() => {
                    setTitleDraft(inspectorParsedSmart.cleanTitle);
                    void localDb.upsertTask({
                      ...buildSmartSyntaxPatch(inspectorParsedSmart, selectedTask),
                      notes: notesDraft
                    });
                  }}
                >
                  <Check size={12} />
                  <span>{t.v32SmartSyntaxApply}</span>
                </button>
              </div>
            )}

            <div className="inspector-flags-bar">
              <button
                type="button"
                className={`chip-btn ${selectedTask.isPinned ? 'active' : 'ghost'}`}
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
                  selectedTask.isMilestone ? 'milestone-active' : 'ghost'
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

              {selectedTask.status === 'OPEN' && (
                <button
                  type="button"
                  className={`chip-btn ${
                    selectedTask.isInProgress ? 'in-progress-active' : 'ghost'
                  }`}
                  data-testid="inspector-in-progress-btn"
                  title={`${
                    selectedTask.isInProgress ? t.v33ClearInProgress : t.v33MarkInProgress
                  } (Ctrl+Shift+I)`}
                  aria-label={
                    selectedTask.isInProgress ? t.v33ClearInProgress : t.v33MarkInProgress
                  }
                  onClick={() => void localDb.toggleTaskInProgress(selectedTask.id)}
                >
                  <InProgressIcon
                    size={13.5}
                    style={
                      !selectedTask.isInProgress
                        ? { color: 'var(--in-progress-amber-ring)', opacity: 0.9 }
                        : undefined
                    }
                  />
                  <span>
                    {selectedTask.isInProgress ? t.v33InProgressBadge : t.v33MarkInProgress}
                  </span>
                </button>
              )}
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
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenWhenModal(selectedTask.id);
                }}
              >
                <Calendar size={13.5} />
              </button>
            </div>

            <div className="inspector-quick-when-grid">
              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'TODAY' ? 'active' : 'ghost'
                }`}
                data-testid="inspector-when-today"
                title={t.viewToday}
                onClick={() => {
                  flushDraftsIfNeeded();
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'TODAY',
                    whenDate: todayStartMs
                  });
                }}
              >
                <Sun size={12} style={{ color: 'var(--today-star)' }} />
                <span>{t.viewToday}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'THIS_EVENING' ? 'evening-quick' : 'ghost'
                }`}
                data-testid="inspector-when-evening"
                title={t.viewThisEvening}
                onClick={() => {
                  flushDraftsIfNeeded();
                  void localDb.upsertTask({
                    id: selectedTask.id,
                    scheduleState: 'THIS_EVENING',
                    whenDate: todayStartMs
                  });
                }}
              >
                <Moon size={12} />
                <span>{t.viewThisEvening}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduleState === 'UPCOMING' && selectedTask.whenDate != null
                    ? 'active'
                    : selectedTask.scheduleState === 'SOMEDAY'
                      ? 'someday-active'
                      : 'ghost'
                }`}
                data-testid="inspector-when-custom-date"
                title={t.ctxWhenReminder}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenWhenModal(selectedTask.id);
                }}
              >
                {selectedTask.scheduleState === 'SOMEDAY' ? (
                  <Cloud size={12.5} />
                ) : (
                  <Calendar size={12.5} />
                )}
                <span>
                  {selectedTask.scheduleState === 'UPCOMING' && selectedTask.whenDate != null
                    ? formatShortDate(selectedTask.whenDate)
                    : selectedTask.scheduleState === 'SOMEDAY'
                      ? t.viewSomeday
                      : t.repeatEndsOn}
                </span>
              </button>
            </div>

            <div className="inspector-prop-pills-wrap">
              {selectedTask.reminderTime != null && (
                <button
                  type="button"
                  className="chip-btn active"
                  data-testid="inspector-reminder-btn"
                  title={`${t.actionReminder}: ${formatReminderClock(selectedTask.reminderTime)}`}
                  onClick={() => {
                    flushDraftsIfNeeded();
                    onOpenWhenModal(selectedTask.id);
                  }}
                >
                  <Bell size={12} />
                  <span>{formatReminderClock(selectedTask.reminderTime)}</span>
                </button>
              )}

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.deadline != null
                    ? dlUrgent
                      ? 'urgent'
                      : 'active'
                    : 'ghost'
                }`}
                data-testid="inspector-deadline-btn"
                title={t.actionDeadline}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenDeadlineModal(selectedTask.id);
                }}
              >
                <Flag size={12} />
                <span>{dlLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.recurrenceRule != null ? 'active' : 'ghost'
                }`}
                data-testid="inspector-repeat-btn"
                title={t.actionRepeat}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenRepeatModal?.(selectedTask.id);
                }}
              >
                <Repeat size={12} />
                <span>
                  {selectedTask.recurrenceRule != null
                    ? formatRecurrenceShort(selectedTask.recurrenceRule, lang)
                    : t.actionRepeat}
                </span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.scheduledStartMinutes != null || showTimeblockPicker
                    ? 'active'
                    : 'ghost'
                }`}
                data-testid="inspector-timeblock-btn"
                title={t.actionTimeblock}
                onClick={() => {
                  setShowDurationPicker(false);
                  setShowEffortPicker(false);
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
                  actualMinutesValue > 0 ||
                  showDurationPicker
                    ? 'active'
                    : 'ghost'
                }`}
                data-testid="inspector-duration-btn"
                title={t.durationLabel}
                onClick={() => {
                  setShowTimeblockPicker(false);
                  setShowEffortPicker(false);
                  setShowDurationPicker((v) => !v);
                }}
              >
                <Hourglass size={12} />
                <span>{durationLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn ${
                  selectedTask.effortLevel != null || showEffortPicker ? 'active' : 'ghost'
                }`}
                data-testid="inspector-effort-btn"
                title={t.effortLabel}
                onClick={() => {
                  setShowTimeblockPicker(false);
                  setShowDurationPicker(false);
                  setShowEffortPicker((v) => !v);
                }}
              >
                <Zap size={12} />
                <span>{effortLabel}</span>
              </button>

              <button
                type="button"
                className={`chip-btn task-waiting-on-chip ${
                  isActivelyBlocked
                    ? 'urgent'
                    : totalBlockerCount > 0
                      ? 'active'
                      : 'ghost'
                }`}
                data-testid="task-waiting-on-chip"
                title={t.waitingOnLabel}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenDependenciesModal?.(selectedTask.id);
                }}
              >
                <Link2 size={12} />
                <span>
                  {blockedSummaryLabel
                    ? blockedSummaryLabel
                    : totalBlockerCount > 0
                      ? `${t.dependencyModalTitle} (${totalBlockerCount})`
                      : t.waitingOnLabel}
                </span>
              </button>
            </div>

            {actualMinutesValue > 0 && (
              <div
                className="inspector-actual-minutes-row"
                data-testid="inspector-actual-minutes-row"
              >
                <span
                  className="meta-badge duration-badge"
                  data-testid="inspector-actual-minutes-badge"
                >
                  <Timer size={11} />
                  <span>{actualInspectorBadgeLabel}</span>
                </span>
                <button
                  type="button"
                  className="chip-btn ghost"
                  data-testid="inspector-reset-actual-minutes-btn"
                  title={t.v32ResetActualTime}
                  onClick={() =>
                    void localDb.upsertTask({
                      id: selectedTask.id,
                      actualMinutes: null
                    })
                  }
                >
                  <RotateCcw size={11} />
                  <span>{t.v32ResetActualTime}</span>
                </button>
              </div>
            )}

            {showTimeblockPicker && (
              <div className="inspector-inline-picker" data-testid="inspector-timeblock-picker">
                <div className="inspector-inline-slots" style={{ marginBottom: 6 }}>
                  <span className="v2-timeblock-label">{t.timeblockStartTime}</span>
                  <input
                    type="time"
                    className="v2-inline-time-input"
                    aria-label={t.timeblockStartTime}
                    value={
                      selectedTask.scheduledStartMinutes != null
                        ? formatMinutesAsClock(selectedTask.scheduledStartMinutes)
                        : ''
                    }
                    onChange={(e) => {
                      const mins = parseClockToMinutes(e.target.value);
                      if (mins != null) {
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          scheduledStartMinutes: mins,
                          durationMinutes:
                            selectedTask.durationMinutes ??
                            DEFAULT_TIMEBLOCK_DURATION_MINUTES
                        });
                      }
                    }}
                  />
                </div>

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
                </div>

                <div className="inspector-inline-slots" style={{ marginTop: 8 }}>
                  <span className="v2-timeblock-label">{t.timeblockDuration}</span>
                  {TIMEBLOCK_DURATION_PRESETS.map((dur) => {
                    const label = formatStandaloneDuration(dur);
                    return (
                      <button
                        key={dur}
                        type="button"
                        className={`chip-btn ${
                          currentTimeblockDuration === dur ? 'active' : ''
                        }`}
                        title={`${t.timeblockDuration}: ${label}`}
                        onClick={() =>
                          void localDb.upsertTask({
                            id: selectedTask.id,
                            scheduledStartMinutes:
                              selectedTask.scheduledStartMinutes ?? 9 * 60,
                            durationMinutes: dur
                          })
                        }
                      >
                        {label}
                      </button>
                    );
                  })}
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
                      <X size={11.5} />
                      <span>{t.actionClear}</span>
                    </button>
                  )}
                </div>
              </div>
            )}

            {showDurationPicker && (
              <div className="inspector-inline-picker" data-testid="inspector-duration-picker">
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
                          durationMinutes:
                            selectedTask.durationMinutes === dur ? null : dur
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
                      <X size={11.5} />
                      <span>{t.actionClear}</span>
                    </button>
                  )}
                </div>

                <div className="v3-custom-duration-row compact">
                  <span className="v2-timeblock-label">{t.durationCustom}</span>
                  <input
                    type="text"
                    className="v3-custom-duration-input"
                    data-testid="inspector-custom-duration-input"
                    value={customDurationDraft}
                    placeholder={t.durationCustomPlaceholder}
                    onChange={(e) => setCustomDurationDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        e.stopPropagation();
                        const mins = parseCustomDurationMinutes(customDurationDraft);
                        if (mins != null) {
                          setCustomDurationDraft('');
                          void localDb.upsertTask({
                            id: selectedTask.id,
                            durationMinutes: mins
                          });
                        }
                      }
                    }}
                  />
                  <button
                    type="button"
                    className={`chip-btn ${
                      parseCustomDurationMinutes(customDurationDraft) != null
                        ? 'active'
                        : ''
                    }`}
                    data-testid="inspector-custom-duration-apply"
                    disabled={parseCustomDurationMinutes(customDurationDraft) == null}
                    onClick={() => {
                      const mins = parseCustomDurationMinutes(customDurationDraft);
                      if (mins != null) {
                        setCustomDurationDraft('');
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          durationMinutes: mins
                        });
                      }
                    }}
                  >
                    <Check size={11.5} />
                    <span>
                      {(() => {
                        const mins = parseCustomDurationMinutes(customDurationDraft);
                        return mins != null
                          ? formatStandaloneDuration(mins)
                          : t.actionDone;
                      })()}
                    </span>
                  </button>
                </div>
              </div>
            )}

            {showEffortPicker && (
              <div className="inspector-inline-picker" data-testid="inspector-effort-picker">
                <div className="inspector-inline-slots">
                  {(
                    [
                      [null, t.effortNone],
                      ['QUICK_WIN', `⚡ ${t.effortQuickWin}`],
                      ['MEDIUM', t.effortMedium],
                      ['DEEP_FOCUS', `🧠 ${t.effortDeepFocus}`]
                    ] as Array<[EffortLevel | null, string]>
                  ).map(([lvl, label]) => (
                    <button
                      key={lvl ?? 'none'}
                      type="button"
                      className={`chip-btn ${
                        (selectedTask.effortLevel ?? null) === lvl ? 'active' : ''
                      }`}
                      data-testid={`inspector-effort-option-${lvl ?? 'none'}`}
                      onClick={() =>
                        void localDb.upsertTask({
                          id: selectedTask.id,
                          effortLevel: lvl
                        })
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className="inspector-org-row">
              <button
                type="button"
                className={`chip-btn inspector-breadcrumb-btn ${
                  proj || area ? 'active' : 'ghost'
                }`}
                data-testid="inspector-container-btn"
                title={t.ctxMoveContainer}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenMoveModal(selectedTask.id);
                }}
              >
                <Folder size={12} />
                <span>{containerBreadcrumb}</span>
              </button>

              {selectedTask.projectId != null && projectHeadings.length > 0 && (
                <select
                  className="inspector-phase-select"
                  data-testid="inspector-heading-select"
                  aria-label={t.entityHeading}
                  title={t.entityHeading}
                  value={selectedTask.headingId ?? ''}
                  onChange={(e) => {
                    const nextHeadingId = e.target.value || null;
                    void localDb.upsertTask({
                      id: selectedTask.id,
                      headingId: nextHeadingId
                    });
                  }}
                >
                  <option value="">{t.projectUngroupedColumn}</option>
                  {projectHeadings.map((hd) => (
                    <option key={hd.id} value={hd.id}>
                      {hd.title}
                    </option>
                  ))}
                </select>
              )}
            </div>

            <div className="inspector-tags-row">
              {taskTags.map((tg) => (
                <button
                  key={tg.id}
                  type="button"
                  className="chip-btn active"
                  title={`${t.entityTag}: ${tg.title}`}
                  onClick={() => {
                    flushDraftsIfNeeded();
                    onOpenTagsModal(selectedTask.id);
                  }}
                >
                  <LayoutGrid size={11} />
                  <span>{tg.title}</span>
                </button>
              ))}
              <button
                type="button"
                className="chip-btn ghost"
                data-testid="inspector-tags-btn"
                title={t.ctxAssignTags}
                onClick={() => {
                  flushDraftsIfNeeded();
                  onOpenTagsModal(selectedTask.id);
                }}
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
                onClick={() => {
                  flushDraftsIfNeeded();
                  onStartFocusTimer(
                    selectedTask.id,
                    titleDraft || selectedTask.title,
                    selectedTask.durationMinutes
                  );
                }}
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
            <div className="inspector-card-header-between">
              <div className="inspector-card-label">
                <CheckSquare size={12.5} />
                <span>
                  {t.actionChecklist} ({doneChecklistsCount}/{activeChecklists.length})
                </span>
              </div>

              <button
                type="button"
                className="chip-btn gemini checklist-magic-inline-btn"
                data-testid="inspector-magic-breakdown-btn"
                disabled={loadingAi}
                title={t.geminiMagicBreakdown}
                onClick={() => void runMagicBreakdown()}
              >
                <Sparkles size={12} />
                <span>{loadingAi ? t.geminiGenerating : t.geminiMagicBreakdown}</span>
              </button>
            </div>

            {activeChecklists.length > 0 && (
              <div className="inspector-checklist-list">
                {activeChecklists.map((item, idx) => (
                  <InspectorChecklistRow
                    key={item.id}
                    item={item}
                    idx={idx}
                    totalCount={activeChecklists.length}
                    taskId={selectedTask.id}
                    t={t}
                    registerRef={(el) => {
                      subtaskRefs.current[item.id] = el;
                    }}
                    onFocusAddBottom={() => addChecklistInputRef.current?.focus()}
                    onFocusNewSubtask={(newId) => setFocusSubtaskId(newId)}
                    onSwapWith={(dir) => {
                      const other =
                        dir === 'up'
                          ? activeChecklists[idx - 1]
                          : activeChecklists[idx + 1];
                      if (other) {
                        void localDb.swapChecklistOrder(item.id, other.id);
                      }
                    }}
                  />
                ))}
              </div>
            )}

            <div className="checklist-add-row checklist-add-boxed inspector-checklist-add">
              <Circle
                size={13}
                style={{ color: 'var(--things-blue)', flexShrink: 0 }}
              />
              <input
                ref={addChecklistInputRef}
                type="text"
                className="checklist-input"
                data-testid="inspector-checklist-add-input"
                value={newChecklistTitle}
                placeholder={t.placeholderAddChecklist}
                onChange={(e) => setNewChecklistTitle(e.target.value)}
                onPaste={(e) => {
                  const pasted = e.clipboardData.getData('text');
                  if (pasted && /\r?\n/.test(pasted)) {
                    e.preventDefault();
                    const lines = pasted
                      .split(/\r?\n/)
                      .map((line) => line.replace(/^[-*•[\]xX\s]+/, '').trim())
                      .filter(Boolean);
                    if (lines.length > 0) {
                      setNewChecklistTitle('');
                      void localDb.addChecklistItems(selectedTask.id, lines);
                    }
                  }
                }}
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

            <MarkdownNotesField
              value={notesDraft}
              rows={3}
              t={t}
              onChange={(nextNotes) => {
                setNotesDraft(nextNotes);
                void localDb.upsertTask({ id: selectedTask.id, notes: nextNotes });
              }}
              onFocusChange={setIsNotesFocused}
              placeholder={t.placeholderNotes}
              className="inspector-notes-textarea"
            />
          </section>

          {/* 5. Attachments Soft Bento Card */}
          <section
            className="inspector-bento-card"
            data-testid="inspector-task-attachments-card"
          >
            <div className="inspector-card-header-between">
              <div className="inspector-card-label">
                <Paperclip size={12.5} />
                <span>
                  {t.attachmentsTitle}
                  {taskAttachments.length > 0 ? ` (${taskAttachments.length})` : ''}
                </span>
              </div>

              <button
                type="button"
                className="icon-btn inspector-mini-icon-btn"
                data-testid="inspector-camera-capture-btn"
                title={t.actionTakePhoto}
                aria-label={t.actionTakePhoto}
                onClick={() => cameraCaptureInputRef.current?.click()}
              >
                <Camera size={13} />
              </button>
            </div>

            <AttachmentsStrip
              taskId={selectedTask.id}
              attachments={taskAttachments}
              t={t}
              onToast={onToast}
              showAddButton
              fileInputRef={attachmentFileInputRef}
            />

            <input
              ref={cameraCaptureInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              style={{ display: 'none' }}
              onChange={async (e) => {
                const files = Array.from(e.target.files ?? []);
                e.target.value = '';
                for (const f of files) {
                  await processAndAddAttachmentFile(
                    f,
                    { taskId: selectedTask.id },
                    t,
                    onToast
                  );
                }
              }}
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
          selectedTaskIds={selectedTaskIds}
          orderedVisibleTaskIds={orderedVisibleTaskIds}
          onJumpToWorkloadDay={onJumpToWorkloadDay}
          onDropTasksOnWorkloadDay={onDropTasksOnWorkloadDay}
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
