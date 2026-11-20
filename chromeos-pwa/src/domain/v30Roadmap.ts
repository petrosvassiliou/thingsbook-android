import {
  addDaysLocal,
  isoDayOfWeek,
  startOfDayEpoch,
  toIsoDateInputValue,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectViewMode,
  type ScheduleState,
  type TaskRecord
} from './types.ts';

export const DRAG_MIME_SINGLE_TASK = 'application/x-thingsbook-task';
export const DRAG_MIME_MULTI_TASKS = 'application/x-thingsbook-tasks';

export const MIN_ROADMAP_WINDOW_DAYS = 21;
export const MAX_ROADMAP_WINDOW_DAYS = 180;
export const MINUTES_PER_WORKDAY = 480;

export interface RoadmapDayCell {
  dayIndex: number;
  epoch: number;
  isoDate: string;
  dayOfMonth: number;
  monthIndex: number;
  year: number;
  weekdayIso: number;
  isToday: boolean;
  isWeekend: boolean;
}

export interface PlottedRoadmapTask {
  task: TaskRecord;
  headingId: string | null;
  startEpoch: number;
  endEpoch: number;
  startDayIndex: number;
  endDayIndex: number;
  spanDays: number;
  isClippedStart: boolean;
  isClippedEnd: boolean;
  isActivelyBlocked: boolean;
}

export interface RoadmapPhaseLane {
  heading: HeadingRecord | null;
  laneId: string;
  title: string;
  startEpoch: number | null;
  endEpoch: number | null;
  startDayIndex: number | null;
  endDayIndex: number | null;
  spanDays: number;
  isInferred: boolean;
  plottedTasks: PlottedRoadmapTask[];
  unscheduledTasks: TaskRecord[];
  completedCount: number;
  totalCount: number;
}

export interface RoadmapDependencyEdge {
  fromTaskId: string;
  toTaskId: string;
  isActivelyBlocking: boolean;
}

export interface ProjectRoadmapLayout {
  projectId: string;
  todayEpoch: number;
  todayDayIndex: number;
  windowStartEpoch: number;
  windowEndEpoch: number;
  totalDays: number;
  days: RoadmapDayCell[];
  lanes: RoadmapPhaseLane[];
  plottedTasks: PlottedRoadmapTask[];
  unscheduledTasks: TaskRecord[];
  unscheduledPhases: HeadingRecord[];
  dependencyEdges: RoadmapDependencyEdge[];
}

export interface RoadmapExportLabels {
  ungroupedTitle?: string;
  todayLabel?: string;
  unscheduledLabel?: string;
  subtitle?: string;
}

export function normalizeProjectViewMode(mode?: string | null): ProjectViewMode {
  if (typeof mode !== 'string') return 'list';
  const low = mode.trim().toLowerCase();
  if (low === 'board') return 'board';
  if (low === 'roadmap' || low === 'gantt') return 'roadmap';
  return 'list';
}

export function normalizeLocalDayEpoch(epochMillis: number): number {
  return startOfDayEpoch(new Date(epochMillis));
}

export function localDaysBetween(startEpoch: number, endEpoch: number): number {
  const s = new Date(startEpoch);
  const e = new Date(endEpoch);
  const utcStart = Date.UTC(s.getFullYear(), s.getMonth(), s.getDate());
  const utcEnd = Date.UTC(e.getFullYear(), e.getMonth(), e.getDate());
  return Math.round((utcEnd - utcStart) / 86_400_000);
}

interface RawResolvedTaskSpan {
  task: TaskRecord;
  headingId: string | null;
  startEpoch: number;
  endEpoch: number;
}

function resolveRawTaskSpan(
  task: TaskRecord,
  normalizedHeadingId: string | null
): RawResolvedTaskSpan | null {
  const durationDays = Math.max(1, Math.ceil((task.durationMinutes ?? 30) / MINUTES_PER_WORKDAY));

  if (task.whenDate != null && task.deadline != null) {
    const startDay = normalizeLocalDayEpoch(task.whenDate);
    const endDay = Math.max(startDay, normalizeLocalDayEpoch(task.deadline));
    return {
      task,
      headingId: normalizedHeadingId,
      startEpoch: startDay,
      endEpoch: endDay
    };
  }

  if (task.whenDate != null && task.deadline == null) {
    const startDay = normalizeLocalDayEpoch(task.whenDate);
    const endDay = addDaysLocal(startDay, durationDays - 1);
    return {
      task,
      headingId: normalizedHeadingId,
      startEpoch: startDay,
      endEpoch: endDay
    };
  }

  if (task.whenDate == null && task.deadline != null) {
    const endDay = normalizeLocalDayEpoch(task.deadline);
    const startDay = addDaysLocal(endDay, -(durationDays - 1));
    return {
      task,
      headingId: normalizedHeadingId,
      startEpoch: startDay,
      endEpoch: endDay
    };
  }

  return null;
}

export function buildProjectRoadmapLayout(
  project: Pick<ProjectRecord, 'id' | 'title' | 'deadline'>,
  headings: readonly HeadingRecord[],
  tasks: readonly TaskRecord[],
  todayEpoch: number = startOfDayEpoch(),
  ungroupedTitle: string = 'General'
): ProjectRoadmapLayout {
  const todayDay = normalizeLocalDayEpoch(todayEpoch);

  const activeHeadings = headings
    .filter((h) => !h.isDeleted && !h.isArchived && h.projectId === project.id)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);
  const validHeadingIds = new Set(activeHeadings.map((h) => h.id));

  const projectTasks = tasks
    .filter((t) => !t.isDeleted && t.status !== 'CANCELED' && t.projectId === project.id)
    .sort((a, b) => {
      if (Boolean(b.isPinned) !== Boolean(a.isPinned)) {
        return a.isPinned ? -1 : 1;
      }
      return a.sortOrder - b.sortOrder || a.createdAt - b.createdAt;
    });

  const openTaskIdSet = new Set(
    projectTasks.filter((t) => t.status === 'OPEN').map((t) => t.id)
  );

  const rawTasksByLane = new Map<string, TaskRecord[]>();
  const ensureLaneTasks = (laneId: string): TaskRecord[] => {
    let arr = rawTasksByLane.get(laneId);
    if (!arr) {
      arr = [];
      rawTasksByLane.set(laneId, arr);
    }
    return arr;
  };

  for (const tk of projectTasks) {
    const laneHeadingId =
      tk.headingId && validHeadingIds.has(tk.headingId) ? tk.headingId : null;
    ensureLaneTasks(laneHeadingId ?? '__ungrouped__').push(tk);
  }

  interface PendingLane {
    heading: HeadingRecord | null;
    laneId: string;
    title: string;
    startEpoch: number | null;
    endEpoch: number | null;
    isInferred: boolean;
    rawSpans: RawResolvedTaskSpan[];
    unscheduledTasks: TaskRecord[];
    completedCount: number;
    totalCount: number;
  }

  const pendingLanes: PendingLane[] = [];
  const unscheduledPhases: HeadingRecord[] = [];
  let minScheduled: number | null = null;
  let maxScheduled: number | null = null;

  const trackWindowBounds = (startEp: number | null, endEp: number | null) => {
    if (startEp != null) {
      minScheduled = minScheduled == null ? startEp : Math.min(minScheduled, startEp);
    }
    if (endEp != null) {
      maxScheduled = maxScheduled == null ? endEp : Math.max(maxScheduled, endEp);
    }
  };

  const ungroupedAll = rawTasksByLane.get('__ungrouped__') ?? [];
  if (ungroupedAll.length > 0 || activeHeadings.length === 0) {
    const rawSpans: RawResolvedTaskSpan[] = [];
    const unscheduled: TaskRecord[] = [];
    for (const tk of ungroupedAll) {
      const resolved = resolveRawTaskSpan(tk, null);
      if (resolved) {
        rawSpans.push(resolved);
        trackWindowBounds(resolved.startEpoch, resolved.endEpoch);
      } else {
        unscheduled.push(tk);
      }
    }
    pendingLanes.push({
      heading: null,
      laneId: '__ungrouped__',
      title: ungroupedTitle,
      startEpoch: null,
      endEpoch: null,
      isInferred: false,
      rawSpans,
      unscheduledTasks: unscheduled,
      completedCount: ungroupedAll.filter((t) => t.status === 'COMPLETED').length,
      totalCount: ungroupedAll.length
    });
  }

  for (const hd of activeHeadings) {
    const laneTasks = rawTasksByLane.get(hd.id) ?? [];
    const rawSpans: RawResolvedTaskSpan[] = [];
    const unscheduled: TaskRecord[] = [];

    for (const tk of laneTasks) {
      const resolved = resolveRawTaskSpan(tk, hd.id);
      if (resolved) {
        rawSpans.push(resolved);
        trackWindowBounds(resolved.startEpoch, resolved.endEpoch);
      } else {
        unscheduled.push(tk);
      }
    }

    const openChildSpans = rawSpans.filter((sp) => sp.task.status === 'OPEN');
    const childMinStart =
      openChildSpans.length > 0
        ? openChildSpans.reduce((m, s) => Math.min(m, s.startEpoch), Infinity)
        : null;
    const childMaxEnd =
      openChildSpans.length > 0
        ? openChildSpans.reduce((m, s) => Math.max(m, s.endEpoch), -Infinity)
        : null;

    let phaseStart: number | null = null;
    let phaseEnd: number | null = null;
    let isInferred = false;

    if (hd.startDate != null && hd.endDate != null) {
      const s = normalizeLocalDayEpoch(hd.startDate);
      const e = normalizeLocalDayEpoch(hd.endDate);
      phaseStart = Math.min(s, e);
      phaseEnd = Math.max(s, e);
    } else if (hd.startDate != null) {
      const s = normalizeLocalDayEpoch(hd.startDate);
      phaseStart = s;
      phaseEnd = childMaxEnd != null && childMaxEnd > s ? childMaxEnd : s;
    } else if (hd.endDate != null) {
      const e = normalizeLocalDayEpoch(hd.endDate);
      phaseStart = childMinStart != null && childMinStart < e ? childMinStart : e;
      phaseEnd = e;
    } else if (childMinStart != null && childMaxEnd != null) {
      phaseStart = childMinStart;
      phaseEnd = childMaxEnd;
      isInferred = true;
    } else {
      unscheduledPhases.push(hd);
    }

    trackWindowBounds(phaseStart, phaseEnd);

    pendingLanes.push({
      heading: hd,
      laneId: hd.id,
      title: hd.title || 'Phase',
      startEpoch: phaseStart,
      endEpoch: phaseEnd,
      isInferred,
      rawSpans,
      unscheduledTasks: unscheduled,
      completedCount: laneTasks.filter((t) => t.status === 'COMPLETED').length,
      totalCount: laneTasks.length
    });
  }

  if (project.deadline != null) {
    const projDeadlineDay = normalizeLocalDayEpoch(project.deadline);
    maxScheduled = maxScheduled == null ? projDeadlineDay : Math.max(maxScheduled, projDeadlineDay);
  }

  const defaultStart = addDaysLocal(todayDay, -3);
  const earliestAllowed = addDaysLocal(todayDay, -90);
  const windowStartEpoch =
    minScheduled != null && minScheduled < defaultStart
      ? Math.max(earliestAllowed, addDaysLocal(minScheduled, -1))
      : defaultStart;

  const minWindowEnd = addDaysLocal(windowStartEpoch, MIN_ROADMAP_WINDOW_DAYS - 1);
  const desiredEnd =
    maxScheduled != null ? addDaysLocal(maxScheduled, 7) : minWindowEnd;
  const unclampedEnd = Math.max(minWindowEnd, desiredEnd, addDaysLocal(todayDay, 14));
  const maxWindowEnd = addDaysLocal(windowStartEpoch, MAX_ROADMAP_WINDOW_DAYS - 1);
  const windowEndEpoch = Math.min(unclampedEnd, maxWindowEnd);

  const totalDays = Math.max(
    MIN_ROADMAP_WINDOW_DAYS,
    localDaysBetween(windowStartEpoch, windowEndEpoch) + 1
  );

  const days: RoadmapDayCell[] = [];
  for (let i = 0; i < totalDays; i++) {
    const ep = addDaysLocal(windowStartEpoch, i);
    const d = new Date(ep);
    const dow = isoDayOfWeek(d);
    days.push({
      dayIndex: i,
      epoch: ep,
      isoDate: toIsoDateInputValue(ep),
      dayOfMonth: d.getDate(),
      monthIndex: d.getMonth(),
      year: d.getFullYear(),
      weekdayIso: dow,
      isToday: ep === todayDay,
      isWeekend: dow === 6 || dow === 7
    });
  }

  const clampDayIndex = (idx: number): number =>
    Math.max(0, Math.min(totalDays - 1, idx));

  const todayRawIndex = localDaysBetween(windowStartEpoch, todayDay);
  const todayDayIndex = clampDayIndex(todayRawIndex);

  const allPlottedTasks: PlottedRoadmapTask[] = [];
  const allUnscheduledTasks: TaskRecord[] = [];
  const plottedById = new Map<string, PlottedRoadmapTask>();

  const lanes: RoadmapPhaseLane[] = pendingLanes.map((pl) => {
    let laneStartIdx: number | null = null;
    let laneEndIdx: number | null = null;
    let laneSpanDays = 0;

    if (pl.startEpoch != null && pl.endEpoch != null) {
      const rawS = localDaysBetween(windowStartEpoch, pl.startEpoch);
      const rawE = localDaysBetween(windowStartEpoch, pl.endEpoch);
      if (rawE >= 0 && rawS < totalDays) {
        laneStartIdx = clampDayIndex(rawS);
        laneEndIdx = Math.max(laneStartIdx, clampDayIndex(rawE));
        laneSpanDays = laneEndIdx - laneStartIdx + 1;
      }
    }

    const plottedTasks: PlottedRoadmapTask[] = pl.rawSpans.map((sp) => {
      const rawS = localDaysBetween(windowStartEpoch, sp.startEpoch);
      const rawE = localDaysBetween(windowStartEpoch, sp.endEpoch);
      const startDayIndex = clampDayIndex(rawS);
      const endDayIndex = Math.max(startDayIndex, clampDayIndex(rawE));
      const activelyBlocked = (sp.task.blockedByTaskIds ?? []).some(
        (bid) => bid !== sp.task.id && openTaskIdSet.has(bid)
      );
      const item: PlottedRoadmapTask = {
        task: sp.task,
        headingId: sp.headingId,
        startEpoch: sp.startEpoch,
        endEpoch: sp.endEpoch,
        startDayIndex,
        endDayIndex,
        spanDays: endDayIndex - startDayIndex + 1,
        isClippedStart: rawS < 0,
        isClippedEnd: rawE >= totalDays,
        isActivelyBlocked: activelyBlocked
      };
      allPlottedTasks.push(item);
      plottedById.set(sp.task.id, item);
      return item;
    });

    for (const ut of pl.unscheduledTasks) {
      allUnscheduledTasks.push(ut);
    }

    return {
      heading: pl.heading,
      laneId: pl.laneId,
      title: pl.title,
      startEpoch: pl.startEpoch,
      endEpoch: pl.endEpoch,
      startDayIndex: laneStartIdx,
      endDayIndex: laneEndIdx,
      spanDays: laneSpanDays,
      isInferred: pl.isInferred,
      plottedTasks,
      unscheduledTasks: pl.unscheduledTasks,
      completedCount: pl.completedCount,
      totalCount: pl.totalCount
    };
  });

  const dependencyEdges: RoadmapDependencyEdge[] = [];
  for (const target of allPlottedTasks) {
    const seenBlockers = new Set<string>();
    for (const blockerId of target.task.blockedByTaskIds ?? []) {
      if (!blockerId || blockerId === target.task.id || seenBlockers.has(blockerId)) {
        continue;
      }
      seenBlockers.add(blockerId);
      const blockerPlotted = plottedById.get(blockerId);
      if (blockerPlotted) {
        dependencyEdges.push({
          fromTaskId: blockerId,
          toTaskId: target.task.id,
          isActivelyBlocking: blockerPlotted.task.status === 'OPEN'
        });
      }
    }
  }

  return {
    projectId: project.id,
    todayEpoch: todayDay,
    todayDayIndex,
    windowStartEpoch,
    windowEndEpoch,
    totalDays,
    days,
    lanes,
    plottedTasks: allPlottedTasks,
    unscheduledTasks: allUnscheduledTasks,
    unscheduledPhases,
    dependencyEdges
  };
}

export function shiftPhaseDatesByDays(
  heading: Pick<HeadingRecord, 'startDate' | 'endDate'>,
  deltaDays: number,
  fallbackSpan?: { startEpoch?: number | null; endEpoch?: number | null }
): { startDate: number | null; endDate: number | null } {
  const step = Math.trunc(deltaDays);
  const rawStart = heading.startDate ?? fallbackSpan?.startEpoch ?? null;
  const rawEnd = heading.endDate ?? fallbackSpan?.endEpoch ?? null;

  return {
    startDate:
      rawStart != null ? addDaysLocal(normalizeLocalDayEpoch(rawStart), step) : null,
    endDate:
      rawEnd != null ? addDaysLocal(normalizeLocalDayEpoch(rawEnd), step) : null
  };
}

export function shiftTaskDatesByDays(
  task: Pick<TaskRecord, 'whenDate' | 'deadline' | 'scheduleState'>,
  deltaDays: number,
  todayEpoch: number = startOfDayEpoch()
): {
  whenDate: number | null;
  deadline: number | null;
  scheduleState: ScheduleState;
} {
  const step = Math.trunc(deltaDays);
  const normToday = normalizeLocalDayEpoch(todayEpoch);

  const hasWhen = task.whenDate != null;
  const hasDeadline = task.deadline != null;

  let newWhen: number | null = hasWhen
    ? addDaysLocal(normalizeLocalDayEpoch(task.whenDate!), step)
    : null;
  const newDeadline: number | null = hasDeadline
    ? addDaysLocal(normalizeLocalDayEpoch(task.deadline!), step)
    : null;

  if (!hasWhen && !hasDeadline) {
    newWhen = addDaysLocal(normToday, step);
  }

  let nextSchedule: ScheduleState = task.scheduleState;
  if (newWhen != null) {
    if (newWhen <= normToday) {
      nextSchedule =
        newWhen === normToday && task.scheduleState === 'THIS_EVENING'
          ? 'THIS_EVENING'
          : 'TODAY';
    } else {
      nextSchedule = 'UPCOMING';
    }
  }

  return {
    whenDate: newWhen,
    deadline: newDeadline,
    scheduleState: nextSchedule
  };
}

// ==================== Multi-Task Drag & Drop Helpers ====================

export interface MinimalDataTransferReader {
  types?: readonly string[] | DOMStringList;
  getData: (format: string) => string;
}

export function hasDraggedTaskMime(
  dataTransfer: MinimalDataTransferReader | null | undefined
): boolean {
  if (!dataTransfer || !dataTransfer.types) return false;
  const typesList = Array.from(dataTransfer.types);
  return (
    typesList.includes(DRAG_MIME_MULTI_TASKS) ||
    typesList.includes(DRAG_MIME_SINGLE_TASK)
  );
}

export function orderTaskIdsByVisualOrder(
  ids: readonly string[],
  orderedVisibleTaskIds: readonly string[] = []
): string[] {
  const unique = Array.from(new Set(ids.map((x) => x.trim()).filter(Boolean)));
  if (unique.length <= 1 || orderedVisibleTaskIds.length === 0) {
    return unique;
  }
  const indexMap = new Map<string, number>();
  for (let i = 0; i < orderedVisibleTaskIds.length; i++) {
    indexMap.set(orderedVisibleTaskIds[i], i);
  }
  return [...unique].sort((a, b) => {
    const ia = indexMap.get(a);
    const ib = indexMap.get(b);
    if (ia !== undefined && ib !== undefined) return ia - ib;
    if (ia !== undefined) return -1;
    if (ib !== undefined) return 1;
    return 0;
  });
}

export function extractDraggedTaskIds(
  dataTransfer: MinimalDataTransferReader | null | undefined,
  selectedTaskIds: readonly string[] = [],
  orderedVisibleTaskIds: readonly string[] = []
): string[] {
  if (!dataTransfer) return [];

  const multiRaw = dataTransfer.getData(DRAG_MIME_MULTI_TASKS);
  if (multiRaw && multiRaw.trim()) {
    try {
      const parsed = JSON.parse(multiRaw);
      if (Array.isArray(parsed)) {
        const clean = parsed.filter(
          (item): item is string => typeof item === 'string' && item.trim().length > 0
        );
        if (clean.length > 0) {
          return orderTaskIdsByVisualOrder(clean, orderedVisibleTaskIds);
        }
      }
    } catch {
      // Fallback to single task MIME below
    }
  }

  const singleId = dataTransfer.getData(DRAG_MIME_SINGLE_TASK)?.trim();
  if (!singleId) return [];

  if (selectedTaskIds.length > 1 && selectedTaskIds.includes(singleId)) {
    return orderTaskIdsByVisualOrder(selectedTaskIds, orderedVisibleTaskIds);
  }

  return [singleId];
}

export function attachMultiTaskDragPreview(
  dataTransfer: DataTransfer,
  taskIds: readonly string[],
  countBadgeLabel: string
): void {
  if (taskIds.length === 0) return;
  dataTransfer.setData(DRAG_MIME_SINGLE_TASK, taskIds[0]);
  dataTransfer.setData(DRAG_MIME_MULTI_TASKS, JSON.stringify(taskIds));
  dataTransfer.effectAllowed = 'move';

  if (taskIds.length > 1 && typeof document !== 'undefined') {
    const badge = document.createElement('div');
    badge.className = 'thingsbook-drag-preview-badge';
    badge.textContent = countBadgeLabel;
    badge.style.position = 'fixed';
    badge.style.top = '-1000px';
    badge.style.left = '-1000px';
    badge.style.padding = '6px 12px';
    badge.style.borderRadius = '999px';
    badge.style.background = 'linear-gradient(90deg, #74C4FF, #3898EC)';
    badge.style.color = '#FFFFFF';
    badge.style.fontFamily = 'Inter, system-ui, -apple-system, sans-serif';
    badge.style.fontSize = '12.5px';
    badge.style.fontWeight = '700';
    badge.style.boxShadow = '0 6px 18px rgba(56, 152, 236, 0.35)';
    badge.style.pointerEvents = 'none';
    badge.style.zIndex = '99999';
    document.body.appendChild(badge);
    try {
      dataTransfer.setDragImage(badge, 18, 16);
    } catch {
      // Ignore if environment lacks setDragImage
    }
    const cleanup = () => {
      if (badge.parentNode) {
        badge.parentNode.removeChild(badge);
      }
    };
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(() => {
        setTimeout(cleanup, 0);
      });
    } else {
      setTimeout(cleanup, 0);
    }
  }
}

// ==================== Zero-Dependency Vector PDF 1.4 & PNG Exporter ====================

const UNICODE_PUNCTUATION_MAP: Record<string, string> = {
  '\u2013': '-',
  '\u2014': '-',
  '\u2018': "'",
  '\u2019': "'",
  '\u201C': '"',
  '\u201D': '"',
  '\u2026': '...',
  '\u2022': '*',
  '\u00A0': ' ',
  '\u2192': '->'
};

export function sanitizePdfText(raw: string, maxLen: number = 96): string {
  const normalized = (raw || '')
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/[\u2013\u2014\u2018\u2019\u201C\u201D\u2026\u2022\u00A0\u2192]/g, (ch) => {
      return UNICODE_PUNCTUATION_MAP[ch] ?? ' ';
    })
    .trim();

  const clipped =
    normalized.length > maxLen ? `${normalized.slice(0, Math.max(1, maxLen - 3))}...` : normalized;

  let out = '';
  for (let i = 0; i < clipped.length; i++) {
    const code = clipped.charCodeAt(i);
    // Surrogate pair or outside Latin-1/WinAnsi range -> skip or space
    if (code > 255) {
      if (code >= 0xd800 && code <= 0xdbff && i + 1 < clipped.length) {
        i++;
      }
      continue;
    }
    if (code < 32) continue;
    if (code === 0x5c) {
      out += '\\\\';
    } else if (code === 0x28) {
      out += '\\(';
    } else if (code === 0x29) {
      out += '\\)';
    } else if (code >= 127) {
      out += `\\${code.toString(8).padStart(3, '0')}`;
    } else {
      out += clipped[i];
    }
  }
  return out;
}

function fmtNum(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '');
}

const MONTH_SHORT_EN = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec'
];

export function generateRoadmapPdfBytes(
  layout: ProjectRoadmapLayout,
  projectTitle: string,
  labels: RoadmapExportLabels = {}
): Uint8Array {
  const marginX = 28;
  const marginY = 28;
  const labelColW = 210;
  const dayColW = 26;
  const headerBannerH = 44;
  const dayHeaderH = 30;
  const laneRowH = 26;
  const taskRowH = 24;
  const unscheduledFooterH = layout.unscheduledTasks.length > 0 ? 30 : 0;

  let bodyRowsCount = 0;
  for (const lane of layout.lanes) {
    bodyRowsCount += 1 + lane.plottedTasks.length;
  }
  if (bodyRowsCount === 0) bodyRowsCount = 1;

  const gridW = layout.totalDays * dayColW;
  const pageW = Math.max(842, marginX * 2 + labelColW + gridW);
  const gridBodyH = bodyRowsCount * laneRowH;
  const pageH = Math.max(
    595,
    marginY * 2 + headerBannerH + dayHeaderH + gridBodyH + unscheduledFooterH + 16
  );

  // Convert top-down Y coordinate (0 = top of page) to PDF Y coordinate (0 = bottom of page)
  const pdfY = (topY: number): number => pageH - topY;

  const ops: string[] = [];

  const fillRgb = (r: number, g: number, b: number) => {
    ops.push(`${fmtNum(r / 255)} ${fmtNum(g / 255)} ${fmtNum(b / 255)} rg`);
  };
  const strokeRgb = (r: number, g: number, b: number) => {
    ops.push(`${fmtNum(r / 255)} ${fmtNum(g / 255)} ${fmtNum(b / 255)} RG`);
  };
  const rectFill = (topLeftX: number, topLeftY: number, w: number, h: number) => {
    ops.push(`${fmtNum(topLeftX)} ${fmtNum(pdfY(topLeftY + h))} ${fmtNum(w)} ${fmtNum(h)} re f`);
  };
  const rectStroke = (topLeftX: number, topLeftY: number, w: number, h: number) => {
    ops.push(`${fmtNum(topLeftX)} ${fmtNum(pdfY(topLeftY + h))} ${fmtNum(w)} ${fmtNum(h)} re S`);
  };
  const rectFillStroke = (topLeftX: number, topLeftY: number, w: number, h: number) => {
    ops.push(`${fmtNum(topLeftX)} ${fmtNum(pdfY(topLeftY + h))} ${fmtNum(w)} ${fmtNum(h)} re B`);
  };
  const line = (x1: number, y1: number, x2: number, y2: number) => {
    ops.push(`${fmtNum(x1)} ${fmtNum(pdfY(y1))} m ${fmtNum(x2)} ${fmtNum(pdfY(y2))} l S`);
  };
  const drawText = (
    text: string,
    x: number,
    baselineTopY: number,
    font: 'F1' | 'F2' = 'F1',
    size: number = 9,
    maxChars: number = 64
  ) => {
    const safe = sanitizePdfText(text, maxChars);
    if (!safe) return;
    ops.push(`BT /${font} ${fmtNum(size)} Tf ${fmtNum(x)} ${fmtNum(pdfY(baselineTopY))} Td (${safe}) Tj ET`);
  };

  // 1. Crisp white canvas background
  fillRgb(255, 255, 255);
  rectFill(0, 0, pageW, pageH);

  // 2. Top Soft Bento Project Title Banner
  const titleText = projectTitle.trim() || 'Project Roadmap';
  fillRgb(23, 26, 30);
  drawText(titleText, marginX, marginY + 16, 'F2', 15, 80);

  const rangeStart = layout.days[0];
  const rangeEnd = layout.days[layout.days.length - 1];
  const rangeStr =
    labels.subtitle ||
    (rangeStart && rangeEnd
      ? `${MONTH_SHORT_EN[rangeStart.monthIndex]} ${rangeStart.dayOfMonth}, ${rangeStart.year} - ${MONTH_SHORT_EN[rangeEnd.monthIndex]} ${rangeEnd.dayOfMonth}, ${rangeEnd.year}`
      : '');
  fillRgb(91, 98, 110);
  drawText(`Thingsbook Roadmap  |  ${rangeStr}`, marginX, marginY + 32, 'F1', 9, 100);

  // 3. Timeline Table Outer Box
  const tableTop = marginY + headerBannerH;
  const timelineLeft = marginX + labelColW;
  const tableW = labelColW + gridW;

  // Header strip background
  fillRgb(244, 246, 250);
  rectFill(marginX, tableTop, tableW, dayHeaderH);

  fillRgb(91, 98, 110);
  drawText('Phase / To-Do', marginX + 10, tableTop + 19, 'F2', 8.5, 32);

  // 4. Weekend columns & vertical day grid lines + day headers
  const bodyTop = tableTop + dayHeaderH;
  // Compute exact row height dynamically from lanes + tasks
  let totalTableBodyH = 0;
  for (const lane of layout.lanes) {
    totalTableBodyH += laneRowH + lane.plottedTasks.length * taskRowH;
  }
  if (totalTableBodyH === 0) totalTableBodyH = laneRowH;

  ops.push('0.6 w');
  for (let i = 0; i < layout.days.length; i++) {
    const cell = layout.days[i];
    const colX = timelineLeft + i * dayColW;

    if (cell.isWeekend) {
      fillRgb(247, 249, 252);
      rectFill(colX, bodyTop, dayColW, totalTableBodyH);
    }

    strokeRgb(231, 235, 241);
    line(colX, tableTop, colX, bodyTop + totalTableBodyH);

    if (i === 0 || cell.dayOfMonth === 1) {
      fillRgb(56, 152, 236);
      drawText(MONTH_SHORT_EN[cell.monthIndex], colX + 3, tableTop + 10, 'F2', 7, 6);
    }

    if (cell.isToday) {
      fillRgb(56, 152, 236);
      rectFill(colX + 2, tableTop + 13, dayColW - 4, 13);
      fillRgb(255, 255, 255);
      drawText(String(cell.dayOfMonth), colX + 6, tableTop + 22.5, 'F2', 7.5, 4);
    } else {
      fillRgb(91, 98, 110);
      drawText(String(cell.dayOfMonth), colX + 6, tableTop + 22.5, 'F1', 7.5, 4);
    }
  }

  // Divider between label column and timeline
  strokeRgb(207, 214, 226);
  line(timelineLeft, tableTop, timelineLeft, bodyTop + totalTableBodyH);
  line(marginX, bodyTop, marginX + tableW, bodyTop);

  // 5. Lanes: Glacier-Blue Phase Bars & Soft Bento Task Bars
  const taskBarCoords = new Map<
    string,
    { xLeft: number; xRight: number; yMid: number }
  >();

  let cursorY = bodyTop;
  for (const lane of layout.lanes) {
    // Subtle lane header tint
    fillRgb(249, 251, 254);
    rectFill(marginX, cursorY, labelColW, laneRowH);

    // Glacier-Blue left accent pill in label cell
    fillRgb(56, 152, 236);
    rectFill(marginX + 8, cursorY + 6, 3.5, laneRowH - 12);

    fillRgb(23, 26, 30);
    const laneTitleLabel =
      lane.heading == null ? labels.ungroupedTitle || lane.title : lane.title;
    drawText(laneTitleLabel, marginX + 16, cursorY + 16.5, 'F2', 9, 30);

    // Phase Glacier-Blue Bar on timeline
    if (lane.startDayIndex != null && lane.endDayIndex != null) {
      const barX = timelineLeft + lane.startDayIndex * dayColW + 2;
      const barW = Math.max(10, lane.spanDays * dayColW - 4);
      const barY = cursorY + 5;
      const barH = laneRowH - 10;

      if (lane.isInferred) {
        fillRgb(116, 196, 255);
        strokeRgb(56, 152, 236);
        rectFillStroke(barX, barY, barW, barH);
        fillRgb(12, 75, 134);
      } else {
        // Crisp Glacier-Blue (#3898EC) with #74C4FF top highlight strip
        fillRgb(56, 152, 236);
        rectFill(barX, barY, barW, barH);
        fillRgb(116, 196, 255);
        rectFill(barX, barY, barW, 3);
        fillRgb(255, 255, 255);
      }
      if (barW >= 42) {
        drawText(laneTitleLabel, barX + 6, cursorY + 16, 'F2', 7.5, Math.floor(barW / 4.5));
      }
    }

    strokeRgb(231, 235, 241);
    line(marginX, cursorY + laneRowH, marginX + tableW, cursorY + laneRowH);
    cursorY += laneRowH;

    // Plotted Task Rows
    for (const pt of lane.plottedTasks) {
      const tk = pt.task;
      // Label cell
      fillRgb(91, 98, 110);
      const prefix = tk.status === 'COMPLETED' ? '[x] ' : '[ ] ';
      drawText(`${prefix}${tk.title || 'To-Do'}`, marginX + 16, cursorY + 15.5, 'F1', 8.2, 32);

      // Task Bar
      const barX = timelineLeft + pt.startDayIndex * dayColW + 3;
      const barW = Math.max(10, pt.spanDays * dayColW - 6);
      const barY = cursorY + 4;
      const barH = taskRowH - 8;

      if (tk.status === 'COMPLETED') {
        fillRgb(232, 248, 238);
        strokeRgb(46, 173, 91);
      } else if (pt.isActivelyBlocked) {
        fillRgb(255, 243, 232);
        strokeRgb(234, 88, 12);
      } else if (tk.scheduleState === 'TODAY' || tk.scheduleState === 'THIS_EVENING') {
        fillRgb(229, 243, 255);
        strokeRgb(56, 152, 236);
      } else {
        fillRgb(240, 247, 255);
        strokeRgb(116, 196, 255);
      }
      ops.push('0.9 w');
      rectFillStroke(barX, barY, barW, barH);
      ops.push('0.6 w');

      fillRgb(23, 26, 30);
      if (barW >= 48) {
        drawText(tk.title || 'To-Do', barX + 5, cursorY + 15, 'F1', 7.5, Math.floor(barW / 4.5));
      }

      taskBarCoords.set(tk.id, {
        xLeft: barX,
        xRight: barX + barW,
        yMid: cursorY + taskRowH / 2
      });

      strokeRgb(238, 241, 246);
      line(marginX, cursorY + taskRowH, marginX + tableW, cursorY + taskRowH);
      cursorY += taskRowH;
    }
  }

  // 6. Dependency Connector Cubic-Bezier Lines + Arrowheads
  for (const edge of layout.dependencyEdges) {
    const src = taskBarCoords.get(edge.fromTaskId);
    const dst = taskBarCoords.get(edge.toTaskId);
    if (!src || !dst) continue;

    if (edge.isActivelyBlocking) {
      strokeRgb(234, 88, 12);
      fillRgb(234, 88, 12);
    } else {
      strokeRgb(100, 116, 139);
      fillRgb(100, 116, 139);
    }
    ops.push('1.1 w');
    const x1 = src.xRight;
    const y1 = src.yMid;
    const x2 = dst.xLeft;
    const y2 = dst.yMid;
    const ctrlOffset = Math.max(14, Math.abs(x2 - x1) * 0.45);
    ops.push(
      `${fmtNum(x1)} ${fmtNum(pdfY(y1))} m ${fmtNum(x1 + ctrlOffset)} ${fmtNum(pdfY(y1))} ${fmtNum(x2 - ctrlOffset)} ${fmtNum(pdfY(y2))} ${fmtNum(x2)} ${fmtNum(pdfY(y2))} c S`
    );
    // Arrowhead pointing right into dst.xLeft
    const ah = 3.2;
    ops.push(
      `${fmtNum(x2)} ${fmtNum(pdfY(y2))} m ${fmtNum(x2 - 5)} ${fmtNum(pdfY(y2 - ah))} l ${fmtNum(x2 - 5)} ${fmtNum(pdfY(y2 + ah))} l h f`
    );
  }

  // 7. Today Vertical Accent Line
  const todayRaw = localDaysBetween(layout.windowStartEpoch, layout.todayEpoch);
  if (todayRaw >= 0 && todayRaw < layout.totalDays) {
    const todayLineX = timelineLeft + todayRaw * dayColW + dayColW / 2;
    ops.push('1.2 w');
    strokeRgb(56, 152, 236);
    line(todayLineX, bodyTop, todayLineX, bodyTop + totalTableBodyH);
  }

  // Outer border around table
  ops.push('0.8 w');
  strokeRgb(207, 214, 226);
  rectStroke(marginX, tableTop, tableW, dayHeaderH + totalTableBodyH);

  // 8. Unscheduled summary footer if present
  if (layout.unscheduledTasks.length > 0) {
    const footY = bodyTop + totalTableBodyH + 18;
    fillRgb(91, 98, 110);
    const unschLabel = labels.unscheduledLabel || 'Unscheduled';
    const sampleNames = layout.unscheduledTasks
      .slice(0, 5)
      .map((t) => t.title || 'To-Do')
      .join(', ');
    drawText(
      `${unschLabel} (${layout.unscheduledTasks.length}): ${sampleNames}`,
      marginX,
      footY,
      'F1',
      8.5,
      110
    );
  }

  const contentStream = ops.join('\n');

  const objects: string[] = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmtNum(pageW)} ${fmtNum(pageH)}] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n`,
    '4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n',
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n',
    `6 0 obj\n<< /Length ${contentStream.length} >>\nstream\n${contentStream}\nendstream\nendobj\n`
  ];

  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [0];
  for (const obj of objects) {
    offsets.push(pdf.length);
    pdf += obj;
  }

  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += '0000000000 65535 f \n';
  for (let i = 1; i < offsets.length; i++) {
    pdf += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;

  return new TextEncoder().encode(pdf);
}

export function renderRoadmapToPngDataUrl(
  layout: ProjectRoadmapLayout,
  projectTitle: string,
  labels: RoadmapExportLabels = {},
  isDark: boolean = false
): string {
  if (typeof document === 'undefined') {
    return 'data:image/png;base64,';
  }
  const canvas = document.createElement('canvas');
  const dpr = 2;
  const marginX = 28;
  const marginY = 28;
  const labelColW = 224;
  const dayColW = 32;
  const headerBannerH = 50;
  const dayHeaderH = 34;
  const laneRowH = 30;
  const taskRowH = 28;

  let totalBodyH = 0;
  for (const lane of layout.lanes) {
    totalBodyH += laneRowH + lane.plottedTasks.length * taskRowH;
  }
  if (totalBodyH === 0) totalBodyH = laneRowH;

  const extraFooter = layout.unscheduledTasks.length > 0 ? 32 : 0;
  const gridW = layout.totalDays * dayColW;
  const logicalW = Math.max(880, marginX * 2 + labelColW + gridW);
  const logicalH = marginY * 2 + headerBannerH + dayHeaderH + totalBodyH + extraFooter;

  canvas.width = Math.round(logicalW * dpr);
  canvas.height = Math.round(logicalH * dpr);

  const ctx = canvas.getContext('2d');
  if (!ctx) return 'data:image/png;base64,';

  ctx.scale(dpr, dpr);

  const bgCanvas = isDark ? '#121417' : '#FFFFFF';
  const bgHeader = isDark ? '#1B1F25' : '#F4F6FA';
  const bgWeekend = isDark ? '#171A1E' : '#F8FAFD';
  const borderSubtle = isDark ? '#272C35' : '#E7EBF1';
  const borderStrong = isDark ? '#39404D' : '#CFD6E2';
  const textPrimary = isDark ? '#F1F3F6' : '#171A1E';
  const textSecondary = isDark ? '#9BA3B0' : '#5B626E';

  ctx.fillStyle = bgCanvas;
  ctx.fillRect(0, 0, logicalW, logicalH);

  ctx.fillStyle = textPrimary;
  ctx.font = '700 17px Inter, system-ui, -apple-system, sans-serif';
  ctx.fillText(projectTitle.trim() || 'Project Roadmap', marginX, marginY + 20);

  const firstDay = layout.days[0];
  const lastDay = layout.days[layout.days.length - 1];
  const subtitle =
    labels.subtitle ||
    (firstDay && lastDay
      ? `${MONTH_SHORT_EN[firstDay.monthIndex]} ${firstDay.dayOfMonth}, ${firstDay.year} – ${MONTH_SHORT_EN[lastDay.monthIndex]} ${lastDay.dayOfMonth}, ${lastDay.year}`
      : '');
  ctx.fillStyle = textSecondary;
  ctx.font = '500 11.5px Inter, system-ui, -apple-system, sans-serif';
  ctx.fillText(`Thingsbook Roadmap · ${subtitle}`, marginX, marginY + 38);

  const tableTop = marginY + headerBannerH;
  const timelineLeft = marginX + labelColW;
  const tableW = labelColW + gridW;
  const bodyTop = tableTop + dayHeaderH;

  ctx.fillStyle = bgHeader;
  ctx.fillRect(marginX, tableTop, tableW, dayHeaderH);

  ctx.fillStyle = textSecondary;
  ctx.font = '600 11px Inter, system-ui, -apple-system, sans-serif';
  ctx.fillText('Phase / To-Do', marginX + 12, tableTop + 21);

  for (let i = 0; i < layout.days.length; i++) {
    const cell = layout.days[i];
    const colX = timelineLeft + i * dayColW;

    if (cell.isWeekend) {
      ctx.fillStyle = bgWeekend;
      ctx.fillRect(colX, bodyTop, dayColW, totalBodyH);
    }

    ctx.strokeStyle = borderSubtle;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(colX + 0.5, tableTop);
    ctx.lineTo(colX + 0.5, bodyTop + totalBodyH);
    ctx.stroke();

    if (i === 0 || cell.dayOfMonth === 1) {
      ctx.fillStyle = '#3898EC';
      ctx.font = '700 9.5px Inter, system-ui, sans-serif';
      ctx.fillText(MONTH_SHORT_EN[cell.monthIndex], colX + 4, tableTop + 12);
    }

    if (cell.isToday) {
      ctx.fillStyle = '#3898EC';
      ctx.beginPath();
      ctx.roundRect(colX + 4, tableTop + 15, dayColW - 8, 15, 6);
      ctx.fill();
      ctx.fillStyle = '#FFFFFF';
      ctx.font = '700 10px Inter, system-ui, sans-serif';
      ctx.fillText(String(cell.dayOfMonth), colX + 9, tableTop + 26);
    } else {
      ctx.fillStyle = textSecondary;
      ctx.font = '500 10px Inter, system-ui, sans-serif';
      ctx.fillText(String(cell.dayOfMonth), colX + 9, tableTop + 26);
    }
  }

  const barCoords = new Map<string, { xLeft: number; xRight: number; yMid: number }>();
  let cursorY = bodyTop;

  for (const lane of layout.lanes) {
    ctx.fillStyle = isDark ? 'rgba(255,255,255,0.025)' : '#FAFBFD';
    ctx.fillRect(marginX, cursorY, labelColW, laneRowH);

    ctx.fillStyle = '#3898EC';
    ctx.beginPath();
    ctx.roundRect(marginX + 10, cursorY + 7, 4, laneRowH - 14, 2);
    ctx.fill();

    const laneTitle =
      lane.heading == null ? labels.ungroupedTitle || lane.title : lane.title;
    ctx.fillStyle = textPrimary;
    ctx.font = '650 11.5px Inter, system-ui, sans-serif';
    ctx.fillText(laneTitle.slice(0, 28), marginX + 20, cursorY + 19);

    if (lane.startDayIndex != null && lane.endDayIndex != null) {
      const barX = timelineLeft + lane.startDayIndex * dayColW + 3;
      const barW = Math.max(14, lane.spanDays * dayColW - 6);
      const barY = cursorY + 5;
      const barH = laneRowH - 10;

      const grad = ctx.createLinearGradient(barX, barY, barX + barW, barY);
      grad.addColorStop(0, '#74C4FF');
      grad.addColorStop(1, '#3898EC');
      ctx.save();
      if (lane.isInferred) ctx.globalAlpha = 0.45;
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.roundRect(barX, barY, barW, barH, 6);
      ctx.fill();
      ctx.restore();

      if (barW >= 54) {
        ctx.fillStyle = lane.isInferred ? textPrimary : '#FFFFFF';
        ctx.font = '650 10px Inter, system-ui, sans-serif';
        ctx.fillText(laneTitle.slice(0, Math.floor(barW / 6.5)), barX + 8, cursorY + 18.5);
      }
    }

    ctx.strokeStyle = borderSubtle;
    ctx.beginPath();
    ctx.moveTo(marginX, cursorY + laneRowH + 0.5);
    ctx.lineTo(marginX + tableW, cursorY + laneRowH + 0.5);
    ctx.stroke();
    cursorY += laneRowH;

    for (const pt of lane.plottedTasks) {
      const tk = pt.task;
      ctx.fillStyle = textSecondary;
      ctx.font = '500 11px Inter, system-ui, sans-serif';
      ctx.fillText((tk.title || 'To-Do').slice(0, 30), marginX + 20, cursorY + 18);

      const barX = timelineLeft + pt.startDayIndex * dayColW + 4;
      const barW = Math.max(14, pt.spanDays * dayColW - 8);
      const barY = cursorY + 4;
      const barH = taskRowH - 8;

      ctx.fillStyle = pt.isActivelyBlocked
        ? isDark
          ? 'rgba(251, 146, 60, 0.22)'
          : '#FFF7ED'
        : isDark
          ? 'rgba(107, 192, 255, 0.20)'
          : '#E5F3FF';
      ctx.strokeStyle = pt.isActivelyBlocked ? '#EA580C' : '#3898EC';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.roundRect(barX, barY, barW, barH, 6);
      ctx.fill();
      ctx.stroke();

      if (barW >= 52) {
        ctx.fillStyle = textPrimary;
        ctx.font = '550 10px Inter, system-ui, sans-serif';
        ctx.fillText((tk.title || 'To-Do').slice(0, Math.floor(barW / 6)), barX + 7, cursorY + 17.5);
      }

      barCoords.set(tk.id, {
        xLeft: barX,
        xRight: barX + barW,
        yMid: cursorY + taskRowH / 2
      });

      ctx.strokeStyle = borderSubtle;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(marginX, cursorY + taskRowH + 0.5);
      ctx.lineTo(marginX + tableW, cursorY + taskRowH + 0.5);
      ctx.stroke();
      cursorY += taskRowH;
    }
  }

  for (const edge of layout.dependencyEdges) {
    const src = barCoords.get(edge.fromTaskId);
    const dst = barCoords.get(edge.toTaskId);
    if (!src || !dst) continue;
    const color = edge.isActivelyBlocking ? '#EA580C' : '#64748B';
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 1.5;
    const ctrl = Math.max(16, Math.abs(dst.xLeft - src.xRight) * 0.45);
    ctx.beginPath();
    ctx.moveTo(src.xRight, src.yMid);
    ctx.bezierCurveTo(
      src.xRight + ctrl,
      src.yMid,
      dst.xLeft - ctrl,
      dst.yMid,
      dst.xLeft,
      dst.yMid
    );
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(dst.xLeft, dst.yMid);
    ctx.lineTo(dst.xLeft - 5.5, dst.yMid - 3.5);
    ctx.lineTo(dst.xLeft - 5.5, dst.yMid + 3.5);
    ctx.closePath();
    ctx.fill();
  }

  const todayRaw = localDaysBetween(layout.windowStartEpoch, layout.todayEpoch);
  if (todayRaw >= 0 && todayRaw < layout.totalDays) {
    const tx = timelineLeft + todayRaw * dayColW + dayColW / 2;
    ctx.strokeStyle = '#3898EC';
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.moveTo(tx, bodyTop);
    ctx.lineTo(tx, bodyTop + totalBodyH);
    ctx.stroke();
  }

  ctx.strokeStyle = borderStrong;
  ctx.lineWidth = 1;
  ctx.strokeRect(marginX + 0.5, tableTop + 0.5, tableW - 1, dayHeaderH + totalBodyH - 1);
  ctx.beginPath();
  ctx.moveTo(timelineLeft + 0.5, tableTop);
  ctx.lineTo(timelineLeft + 0.5, bodyTop + totalBodyH);
  ctx.stroke();

  if (layout.unscheduledTasks.length > 0) {
    const unschLabel = labels.unscheduledLabel || 'Unscheduled';
    ctx.fillStyle = textSecondary;
    ctx.font = '500 11px Inter, system-ui, sans-serif';
    ctx.fillText(
      `${unschLabel}: ${layout.unscheduledTasks.length}`,
      marginX,
      bodyTop + totalBodyH + 20
    );
  }

  return canvas.toDataURL('image/png');
}

export function slugifyFilename(title: string): string {
  const slug = (title || 'project')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48);
  return slug || 'roadmap';
}

export function downloadBlob(
  bytesOrDataUrl: Uint8Array | string,
  filename: string,
  mimeType: string
): void {
  if (typeof document === 'undefined') return;

  if (typeof bytesOrDataUrl === 'string' && bytesOrDataUrl.startsWith('data:')) {
    const link = document.createElement('a');
    link.href = bytesOrDataUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    return;
  }

  const rawBytes =
    typeof bytesOrDataUrl === 'string'
      ? new TextEncoder().encode(bytesOrDataUrl)
      : bytesOrDataUrl;
  const blob = new Blob([new Uint8Array(rawBytes)], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}
