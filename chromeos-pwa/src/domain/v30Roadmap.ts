import {
  addDaysLocal,
  isoDayOfWeek,
  startOfDayEpoch,
  toIsoDateInputValue,
  type AreaRecord,
  type AreaViewMode,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectViewMode,
  type ScheduleState,
  type SupportedLang,
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
  isMilestone: boolean;
}

export type RoadmapPlottedTask = PlottedRoadmapTask;

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
  inProgressCount: number;
  totalCount: number;
}

export type RoadmapLane = RoadmapPhaseLane;

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
  lang?: SupportedLang;
  ungroupedTitle?: string;
  todayLabel?: string;
  unscheduledLabel?: string;
  subtitle?: string;
  phaseTodoHeader?: string;
  pageLabel?: string;
}

export type RoadmapPdfOrientation = 'landscape' | 'portrait';

export interface RoadmapPdfExportOptions {
  orientation?: RoadmapPdfOrientation;
  selectedLaneIds?: readonly string[];
  startEpoch?: number | null;
  endEpoch?: number | null;
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

/**
 * DST-safe calendar +1 month helper clamped to target month end
 * (e.g. Jan 31 -> Feb 28/29, Dec 18 -> Jan 18).
 */
export function addOneMonthLocal(epochMillis: number): number {
  const d = new Date(epochMillis);
  const year = d.getFullYear();
  const targetMonth = d.getMonth() + 1;
  const lastDayOfTargetMonth = new Date(year, targetMonth + 1, 0, 0, 0, 0, 0).getDate();
  const clampedDay = Math.min(d.getDate(), lastDayOfTargetMonth);
  return new Date(year, targetMonth, clampedDay, 0, 0, 0, 0).getTime();
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
  if (task.isMilestone) {
    if (task.deadline != null || task.whenDate != null) {
      const targetMs = task.deadline ?? task.whenDate!;
      const targetDay = startOfDayEpoch(new Date(targetMs));
      return {
        task,
        headingId: normalizedHeadingId,
        startEpoch: targetDay,
        endEpoch: targetDay
      };
    }
    return null;
  }

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
      const aInProg = a.status === 'OPEN' && Boolean(a.isInProgress);
      const bInProg = b.status === 'OPEN' && Boolean(b.isInProgress);
      if (bInProg !== aInProg) {
        return aInProg ? -1 : 1;
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
    inProgressCount: number;
    totalCount: number;
  }

  const pendingLanes: PendingLane[] = [];
  const unscheduledPhases: HeadingRecord[] = [];
  let minScheduled: number | null = null;
  let maxTaskOrPhaseEndEpoch: number | null = null;
  let lastTaskEndEpoch: number | null = null;
  let maxPhaseEndEpoch: number | null = null;
  let plottedTaskCount = 0;

  const trackTaskBounds = (startEp: number, endEp: number) => {
    plottedTaskCount += 1;
    minScheduled = minScheduled == null ? startEp : Math.min(minScheduled, startEp);
    maxTaskOrPhaseEndEpoch =
      maxTaskOrPhaseEndEpoch == null ? endEp : Math.max(maxTaskOrPhaseEndEpoch, endEp);
    lastTaskEndEpoch =
      lastTaskEndEpoch == null ? endEp : Math.max(lastTaskEndEpoch, endEp);
  };

  const trackPhaseBounds = (startEp: number | null, endEp: number | null) => {
    if (startEp != null) {
      minScheduled = minScheduled == null ? startEp : Math.min(minScheduled, startEp);
    }
    if (endEp != null) {
      maxTaskOrPhaseEndEpoch =
        maxTaskOrPhaseEndEpoch == null ? endEp : Math.max(maxTaskOrPhaseEndEpoch, endEp);
      maxPhaseEndEpoch =
        maxPhaseEndEpoch == null ? endEp : Math.max(maxPhaseEndEpoch, endEp);
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
        trackTaskBounds(resolved.startEpoch, resolved.endEpoch);
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
      inProgressCount: ungroupedAll.filter((t) => t.status === 'OPEN' && Boolean(t.isInProgress)).length,
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
        trackTaskBounds(resolved.startEpoch, resolved.endEpoch);
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

    trackPhaseBounds(phaseStart, phaseEnd);

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
      inProgressCount: laneTasks.filter((t) => t.status === 'OPEN' && Boolean(t.isInProgress)).length,
      totalCount: laneTasks.length
    });
  }

  // Note: project.deadline deliberately does NOT stretch windowEndEpoch;
  // horizon is capped strictly to <= 1 month after the last task due/end date.
  const defaultStart = addDaysLocal(todayDay, -3);
  const earliestAllowed = addDaysLocal(todayDay, -90);
  const windowStartEpoch =
    minScheduled != null && minScheduled < defaultStart
      ? Math.max(earliestAllowed, addDaysLocal(minScheduled, -1))
      : defaultStart;

  const minWindowEnd = addDaysLocal(windowStartEpoch, MIN_ROADMAP_WINDOW_DAYS - 1);
  const maxWindowEnd = addDaysLocal(windowStartEpoch, MAX_ROADMAP_WINDOW_DAYS - 1);

  let resolvedWindowEndEpoch: number;
  if (plottedTaskCount > 0 && lastTaskEndEpoch != null) {
    const oneMonthCapEpoch = addOneMonthLocal(lastTaskEndEpoch);
    const uncappedDesiredEnd = Math.max(
      minWindowEnd,
      addDaysLocal(maxTaskOrPhaseEndEpoch ?? lastTaskEndEpoch, 7)
    );
    if (oneMonthCapEpoch >= windowStartEpoch) {
      resolvedWindowEndEpoch = Math.max(
        lastTaskEndEpoch,
        Math.min(uncappedDesiredEnd, oneMonthCapEpoch)
      );
    } else {
      resolvedWindowEndEpoch = minWindowEnd;
    }
  } else {
    const oneMonthFromToday = addOneMonthLocal(todayDay);
    const cappedPhaseEnd = Math.min(
      addDaysLocal(maxPhaseEndEpoch ?? todayDay, 7),
      oneMonthFromToday
    );
    resolvedWindowEndEpoch =
      oneMonthFromToday >= minWindowEnd
        ? Math.min(Math.max(minWindowEnd, cappedPhaseEnd), oneMonthFromToday)
        : minWindowEnd;
  }

  const windowEndEpoch = Math.min(resolvedWindowEndEpoch, maxWindowEnd);
  const totalDays = Math.max(1, localDaysBetween(windowStartEpoch, windowEndEpoch) + 1);

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
        isActivelyBlocked: activelyBlocked,
        isMilestone: Boolean(sp.task.isMilestone)
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
      inProgressCount: pl.inProgressCount,
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

// ==================== v3.1 Smart Push-on-Conflict Dependency Cascade & Area Portfolio Layout ====================

export interface CascadedTaskShift {
  task: TaskRecord;
  deltaDays: number;
  whenDate: number | null;
  deadline: number | null;
  scheduleState: ScheduleState;
  isAnchor: boolean;
}

/**
 * Smart Push-on-Conflict Auto-Cascading Dependencies (v3.1):
 * Shifts `anchorTask` by `deltaDays`, then runs a cycle-safe BFS over open downstream tasks
 * (`t.status === 'OPEN' && !t.isDeleted`) whose `blockedByTaskIds` reference an upstream shifted task.
 * Only pushes a downstream scheduled task forward when `blockerNewEndDay >= depEffectiveStartDay`
 * by `pushNeeded = (blockerNewEndDay + 1) - depEffectiveStartDay`, preserving existing slack and
 * never pulling downstream tasks backward.
 */
export function computeSmartDependencyCascade(
  anchorTask: TaskRecord,
  deltaDays: number,
  projectTasks: readonly TaskRecord[],
  todayEpoch: number = startOfDayEpoch()
): CascadedTaskShift[] {
  const step = Math.trunc(deltaDays);
  if (!Number.isFinite(step) || step === 0) return [];

  const normToday = normalizeLocalDayEpoch(todayEpoch);

  const taskById = new Map<string, TaskRecord>();
  taskById.set(anchorTask.id, anchorTask);
  for (const t of projectTasks) {
    if (!t.isDeleted && t.status !== 'CANCELED') {
      taskById.set(t.id, t);
    }
  }

  const dependentsByBlocker = new Map<string, TaskRecord[]>();
  for (const t of taskById.values()) {
    if (t.isDeleted || t.status !== 'OPEN') continue;
    for (const rawBlockerId of t.blockedByTaskIds ?? []) {
      const clean = String(rawBlockerId || '').trim();
      if (clean && clean !== t.id) {
        let list = dependentsByBlocker.get(clean);
        if (!list) {
          list = [];
          dependentsByBlocker.set(clean, list);
        }
        list.push(t);
      }
    }
  }

  const baseSpanByTaskId = new Map<string, { startDay: number; endDay: number }>();
  for (const t of taskById.values()) {
    const rawSpan = resolveRawTaskSpan(t, null);
    if (rawSpan) {
      baseSpanByTaskId.set(t.id, {
        startDay: localDaysBetween(normToday, rawSpan.startEpoch),
        endDay: localDaysBetween(normToday, rawSpan.endEpoch)
      });
    }
  }

  if (!baseSpanByTaskId.has(anchorTask.id)) {
    const durDays = anchorTask.isMilestone
      ? 1
      : Math.max(1, Math.ceil((anchorTask.durationMinutes ?? 30) / MINUTES_PER_WORKDAY));
    baseSpanByTaskId.set(anchorTask.id, {
      startDay: 0,
      endDay: durDays - 1
    });
  }

  const appliedDeltaByTaskId = new Map<string, number>();
  appliedDeltaByTaskId.set(anchorTask.id, step);

  const queue: string[] = [anchorTask.id];
  let guard = 0;
  const maxIterations = Math.max(16, taskById.size * 4);

  while (queue.length > 0 && guard < maxIterations) {
    guard++;
    const currentId = queue.shift()!;
    const curBase = baseSpanByTaskId.get(currentId);
    if (!curBase) continue;
    const curDelta = appliedDeltaByTaskId.get(currentId) ?? 0;
    const blockerNewEndDay = curBase.endDay + curDelta;

    const dependents = dependentsByBlocker.get(currentId) ?? [];
    for (const dep of dependents) {
      if (dep.id === anchorTask.id) continue;
      const depBase = baseSpanByTaskId.get(dep.id);
      if (!depBase) continue;
      const existingDepDelta = appliedDeltaByTaskId.get(dep.id) ?? 0;
      const depEffectiveStartDay = depBase.startDay + existingDepDelta;
      if (blockerNewEndDay >= depEffectiveStartDay) {
        const pushNeeded = blockerNewEndDay + 1 - depEffectiveStartDay;
        const nextDepDelta = existingDepDelta + pushNeeded;
        if (nextDepDelta > existingDepDelta) {
          appliedDeltaByTaskId.set(dep.id, nextDepDelta);
          queue.push(dep.id);
        }
      }
    }
  }

  const results: CascadedTaskShift[] = [];
  for (const [taskId, taskDelta] of appliedDeltaByTaskId.entries()) {
    if (taskDelta === 0) continue;
    const targetTask = taskById.get(taskId);
    if (!targetTask) continue;
    const shifted = shiftTaskDatesByDays(targetTask, taskDelta, normToday);
    results.push({
      task: targetTask,
      deltaDays: taskDelta,
      whenDate: shifted.whenDate,
      deadline: shifted.deadline,
      scheduleState: shifted.scheduleState,
      isAnchor: taskId === anchorTask.id
    });
  }

  return results;
}

export function computeCascadeDeltaPreviewMap(
  anchorTask: TaskRecord,
  deltaDays: number,
  projectTasks: readonly TaskRecord[],
  todayEpoch: number = startOfDayEpoch()
): Map<string, number> {
  const map = new Map<string, number>();
  const shifts = computeSmartDependencyCascade(anchorTask, deltaDays, projectTasks, todayEpoch);
  for (const item of shifts) {
    map.set(item.task.id, item.deltaDays);
  }
  return map;
}

export function normalizeAreaViewMode(mode?: string | null): AreaViewMode {
  if (typeof mode === 'string' && mode.trim().toLowerCase() === 'roadmap') {
    return 'roadmap';
  }
  return 'list';
}

export interface AreaPortfolioProjectLane {
  project: ProjectRecord;
  projectLayout: ProjectRoadmapLayout;
  summaryStartDay: number;
  summaryEndDay: number;
  summaryStartDayIndex: number;
  summaryEndDayIndex: number;
  spanDays: number;
  milestoneBars: PlottedRoadmapTask[];
  completedCount: number;
  inProgressCount: number;
  totalCount: number;
}

export interface AreaPortfolioRoadmapLayout {
  area: Pick<AreaRecord, 'id' | 'title'>;
  areaId: string;
  todayEpoch: number;
  todayDayIndex: number;
  windowStartEpoch: number;
  windowEndEpoch: number;
  totalDays: number;
  days: RoadmapDayCell[];
  projectLanes: AreaPortfolioProjectLane[];
  unscheduledProjects: ProjectRecord[];
}

export function buildAreaPortfolioRoadmapLayout(
  area: Pick<AreaRecord, 'id' | 'title'>,
  areaProjects: readonly ProjectRecord[],
  allHeadings: readonly HeadingRecord[],
  allOpenTasks: readonly TaskRecord[],
  todayEpoch: number = startOfDayEpoch(),
  ungroupedTitle: string = 'General'
): AreaPortfolioRoadmapLayout {
  const todayDay = normalizeLocalDayEpoch(todayEpoch);

  const activeProjects = areaProjects
    .filter((p) => !p.isDeleted && p.status === 'ACTIVE' && (!area.id || p.areaId === area.id))
    .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt || a.id.localeCompare(b.id));

  interface RawProjectCandidate {
    project: ProjectRecord;
    rawSubLayout: ProjectRoadmapLayout;
    summaryStartEpoch: number;
    summaryEndEpoch: number;
    completedCount: number;
    inProgressCount: number;
    totalCount: number;
  }

  const rawScheduled: RawProjectCandidate[] = [];
  const unscheduledProjects: ProjectRecord[] = [];

  for (const proj of activeProjects) {
    const projHeadings = allHeadings.filter(
      (h) => !h.isDeleted && !h.isArchived && h.projectId === proj.id
    );
    const projTasks = allOpenTasks.filter(
      (t) => !t.isDeleted && t.status !== 'CANCELED' && t.projectId === proj.id
    );
    const subLayout = buildProjectRoadmapLayout(
      proj,
      projHeadings,
      projTasks,
      todayDay,
      ungroupedTitle
    );

    const candidateStarts: number[] = [];
    const candidateEnds: number[] = [];

    for (const lane of subLayout.lanes) {
      if (lane.startEpoch != null && lane.endEpoch != null) {
        candidateStarts.push(lane.startEpoch);
        candidateEnds.push(lane.endEpoch);
      }
    }
    for (const pt of subLayout.plottedTasks) {
      candidateStarts.push(pt.startEpoch);
      candidateEnds.push(pt.endEpoch);
    }
    if (proj.whenDate != null) {
      candidateStarts.push(normalizeLocalDayEpoch(proj.whenDate));
    }
    if (proj.deadline != null) {
      candidateEnds.push(normalizeLocalDayEpoch(proj.deadline));
    }

    if (candidateStarts.length === 0 && candidateEnds.length === 0) {
      unscheduledProjects.push(proj);
    } else {
      const combined = [...candidateStarts, ...candidateEnds];
      const rawMin = combined.reduce((m, v) => Math.min(m, v), Infinity);
      const rawMax = combined.reduce((m, v) => Math.max(m, v), -Infinity);
      const summaryStartEpoch = Math.min(rawMin, rawMax);
      const summaryEndEpoch = Math.max(summaryStartEpoch, rawMax);
      rawScheduled.push({
        project: proj,
        rawSubLayout: subLayout,
        summaryStartEpoch,
        summaryEndEpoch,
        completedCount: projTasks.filter((t) => t.status === 'COMPLETED').length,
        inProgressCount: projTasks.filter((t) => t.status === 'OPEN' && Boolean(t.isInProgress)).length,
        totalCount: projTasks.length
      });
    }
  }

  const minScheduled =
    rawScheduled.length > 0
      ? rawScheduled.reduce((m, p) => Math.min(m, p.summaryStartEpoch), Infinity)
      : null;
  const maxScheduled =
    rawScheduled.length > 0
      ? rawScheduled.reduce((m, p) => Math.max(m, p.summaryEndEpoch), -Infinity)
      : null;

  const defaultStart = addDaysLocal(todayDay, -3);
  const earliestAllowed = addDaysLocal(todayDay, -90);
  const windowStartEpoch =
    minScheduled != null && minScheduled < defaultStart
      ? Math.max(earliestAllowed, addDaysLocal(minScheduled, -1))
      : defaultStart;

  const minRequiredEnd = addDaysLocal(windowStartEpoch, MIN_ROADMAP_WINDOW_DAYS - 1);
  const maxWindowEnd = addDaysLocal(windowStartEpoch, MAX_ROADMAP_WINDOW_DAYS - 1);
  const twoMonthCap = addOneMonthLocal(addOneMonthLocal(maxScheduled ?? todayDay));
  const rawEnd =
    maxScheduled != null
      ? Math.max(minRequiredEnd, addDaysLocal(maxScheduled, 7))
      : minRequiredEnd;
  const windowEndEpoch = Math.min(
    maxWindowEnd,
    Math.min(rawEnd, Math.max(minRequiredEnd, twoMonthCap))
  );

  const totalDays = Math.max(1, localDaysBetween(windowStartEpoch, windowEndEpoch) + 1);
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

  const clampDayIndex = (idx: number): number => Math.max(0, Math.min(totalDays - 1, idx));
  const todayDayIndex = clampDayIndex(localDaysBetween(windowStartEpoch, todayDay));

  const projectLanes: AreaPortfolioProjectLane[] = rawScheduled.map((cand) => {
    const remappedPlottedTasks: PlottedRoadmapTask[] = [];
    const remappedLanes: RoadmapPhaseLane[] = cand.rawSubLayout.lanes.map((lane) => {
      let laneStartIdx: number | null = null;
      let laneEndIdx: number | null = null;
      let laneSpanDays = 0;
      if (lane.startEpoch != null && lane.endEpoch != null) {
        const rawS = localDaysBetween(windowStartEpoch, lane.startEpoch);
        const rawE = localDaysBetween(windowStartEpoch, lane.endEpoch);
        if (rawE >= 0 && rawS < totalDays) {
          laneStartIdx = clampDayIndex(rawS);
          laneEndIdx = Math.max(laneStartIdx, clampDayIndex(rawE));
          laneSpanDays = laneEndIdx - laneStartIdx + 1;
        }
      }

      const laneTasks = lane.plottedTasks.map((pt) => {
        const rawS = localDaysBetween(windowStartEpoch, pt.startEpoch);
        const rawE = localDaysBetween(windowStartEpoch, pt.endEpoch);
        const sIdx = clampDayIndex(rawS);
        const eIdx = Math.max(sIdx, clampDayIndex(rawE));
        const remapped: PlottedRoadmapTask = {
          ...pt,
          startDayIndex: sIdx,
          endDayIndex: eIdx,
          spanDays: eIdx - sIdx + 1,
          isClippedStart: rawS < 0,
          isClippedEnd: rawE >= totalDays
        };
        remappedPlottedTasks.push(remapped);
        return remapped;
      });

      return {
        ...lane,
        startDayIndex: laneStartIdx,
        endDayIndex: laneEndIdx,
        spanDays: laneSpanDays,
        plottedTasks: laneTasks
      };
    });

    const remappedLayout: ProjectRoadmapLayout = {
      ...cand.rawSubLayout,
      todayEpoch: todayDay,
      todayDayIndex,
      windowStartEpoch,
      windowEndEpoch,
      totalDays,
      days,
      lanes: remappedLanes,
      plottedTasks: remappedPlottedTasks
    };

    const rawProjS = localDaysBetween(windowStartEpoch, cand.summaryStartEpoch);
    const rawProjE = localDaysBetween(windowStartEpoch, cand.summaryEndEpoch);
    const summaryStartDayIndex = clampDayIndex(rawProjS);
    const summaryEndDayIndex = Math.max(summaryStartDayIndex, clampDayIndex(rawProjE));

    return {
      project: cand.project,
      projectLayout: remappedLayout,
      summaryStartDay: cand.summaryStartEpoch,
      summaryEndDay: cand.summaryEndEpoch,
      summaryStartDayIndex,
      summaryEndDayIndex,
      spanDays: summaryEndDayIndex - summaryStartDayIndex + 1,
      milestoneBars: remappedPlottedTasks.filter(
        (pt) => pt.isMilestone || Boolean(pt.task.isMilestone)
      ),
      completedCount: cand.completedCount,
      inProgressCount: cand.inProgressCount,
      totalCount: cand.totalCount
    };
  });

  return {
    area: { id: area.id, title: area.title },
    areaId: area.id,
    todayEpoch: todayDay,
    todayDayIndex,
    windowStartEpoch,
    windowEndEpoch,
    totalDays,
    days,
    projectLanes,
    unscheduledProjects
  };
}

export function toExportablePortfolioLayout(
  portfolio: AreaPortfolioRoadmapLayout,
  expandedProjectIds: ReadonlySet<string> = new Set()
): ProjectRoadmapLayout {
  const lanes: RoadmapPhaseLane[] = [];
  const plottedTasks: PlottedRoadmapTask[] = [];
  const dependencyEdges: RoadmapDependencyEdge[] = [];

  portfolio.projectLanes.forEach((pLane, idx) => {
    const synHeading: HeadingRecord = {
      id: pLane.project.id,
      projectId: `area_export_${portfolio.areaId}`,
      title: pLane.project.title,
      startDate: pLane.summaryStartDay,
      endDate: pLane.summaryEndDay,
      sortOrder: (idx + 1) * 100,
      isArchived: false,
      createdAt: pLane.project.createdAt,
      updatedAt: pLane.project.updatedAt,
      isDeleted: false,
      isDirty: false
    };
    const isExpanded = expandedProjectIds.has(pLane.project.id);
    const sourceBars = isExpanded ? pLane.projectLayout.plottedTasks : pLane.milestoneBars;
    const remappedBars: PlottedRoadmapTask[] = sourceBars.map((pt) => ({
      ...pt,
      headingId: synHeading.id
    }));
    plottedTasks.push(...remappedBars);
    if (isExpanded) {
      dependencyEdges.push(...pLane.projectLayout.dependencyEdges);
    }
    lanes.push({
      heading: synHeading,
      laneId: synHeading.id,
      title: synHeading.title,
      startEpoch: pLane.summaryStartDay,
      endEpoch: pLane.summaryEndDay,
      startDayIndex: pLane.summaryStartDayIndex,
      endDayIndex: pLane.summaryEndDayIndex,
      spanDays: pLane.spanDays,
      isInferred: false,
      plottedTasks: remappedBars,
      unscheduledTasks: [],
      completedCount: pLane.completedCount,
      inProgressCount: pLane.inProgressCount,
      totalCount: pLane.totalCount
    });
  });

  return {
    projectId: `area_export_${portfolio.areaId}`,
    todayEpoch: portfolio.todayEpoch,
    todayDayIndex: portfolio.todayDayIndex,
    windowStartEpoch: portfolio.windowStartEpoch,
    windowEndEpoch: portfolio.windowEndEpoch,
    totalDays: portfolio.totalDays,
    days: portfolio.days,
    lanes,
    plottedTasks,
    unscheduledTasks: [],
    unscheduledPhases: [],
    dependencyEdges
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
  '\u25C6': '<>',
  '\u00A0': ' ',
  '\u2192': '->'
};

export function sanitizePdfText(raw: string, maxLen: number = 96): string {
  const normalized = (raw || '')
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/[\u2013\u2014\u2018\u2019\u201C\u201D\u2026\u2022\u25C6\u00A0\u2192]/g, (ch) => {
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

const MONTH_SHORT_BY_LANG: Record<SupportedLang, readonly string[]> = {
  en: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  de: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
  fr: ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Juin', 'Juil', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'],
  it: ['Gen', 'Feb', 'Mar', 'Apr', 'Mag', 'Giu', 'Lug', 'Ago', 'Set', 'Ott', 'Nov', 'Dic'],
  es: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic']
};

const PHASE_TODO_HEADER_BY_LANG: Record<SupportedLang, string> = {
  en: 'Phase / To-Do',
  de: 'Phase / Aufgabe',
  fr: 'Phase / Tâche',
  it: 'Fase / Attività',
  es: 'Fase / Tarea'
};

const PAGE_LABEL_BY_LANG: Record<SupportedLang, string> = {
  en: 'Page',
  de: 'Seite',
  fr: 'Page',
  it: 'Pagina',
  es: 'Página'
};

export function wrapTextLines(raw: string, maxCharsPerLine: number): string[] {
  const clean = (raw || '').replace(/[\r\n\t]+/g, ' ').trim();
  if (!clean) return [''];
  const limit = Math.max(8, Math.floor(maxCharsPerLine));
  const words = clean.split(/\s+/);
  const lines: string[] = [];
  let current = '';

  for (const word of words) {
    if (word.length > limit) {
      if (current) {
        lines.push(current);
        current = '';
      }
      for (let idx = 0; idx < word.length; idx += limit) {
        const chunk = word.slice(idx, idx + limit);
        if (chunk.length === limit && idx + limit < word.length) {
          lines.push(chunk);
        } else {
          current = chunk;
        }
      }
      continue;
    }

    if (!current) {
      current = word;
    } else if (current.length + 1 + word.length <= limit) {
      current = `${current} ${word}`;
    } else {
      lines.push(current);
      current = word;
    }
  }

  if (current) {
    lines.push(current);
  }
  return lines.length > 0 ? lines : [''];
}

export function generateRoadmapPdfBytes(
  layout: ProjectRoadmapLayout,
  projectTitle: string,
  labels: RoadmapExportLabels = {},
  exportOptions: RoadmapPdfExportOptions = {}
): Uint8Array {
  const orientation: RoadmapPdfOrientation =
    exportOptions.orientation === 'portrait' ? 'portrait' : 'landscape';
  const pageW = orientation === 'portrait' ? 595 : 842;
  const pageH = orientation === 'portrait' ? 842 : 595;

  const marginX = 28;
  const marginY = 28;
  const contentW = pageW - marginX * 2;
  const labelColW = orientation === 'portrait' ? 176 : 206;
  const gridW = contentW - labelColW;
  const tableW = labelColW + gridW;
  const headerBannerH = 42;
  const dayHeaderH = 30;
  const taskRowH = 24;

  // Resolve custom or preset timeline bounds
  const rawStart =
    exportOptions.startEpoch != null
      ? normalizeLocalDayEpoch(exportOptions.startEpoch)
      : layout.windowStartEpoch;
  const rawEnd =
    exportOptions.endEpoch != null
      ? normalizeLocalDayEpoch(exportOptions.endEpoch)
      : layout.windowEndEpoch;
  const exportStartEpoch = Math.min(rawStart, rawEnd);
  const exportEndEpoch = Math.max(rawStart, rawEnd);
  const exportTotalDays = Math.max(
    1,
    localDaysBetween(exportStartEpoch, exportEndEpoch) + 1
  );
  const dayColW = gridW / exportTotalDays;

  const exportDays: RoadmapDayCell[] = [];
  let todayExportIndex = -1;
  for (let i = 0; i < exportTotalDays; i++) {
    const ep = addDaysLocal(exportStartEpoch, i);
    const d = new Date(ep);
    const dow = isoDayOfWeek(d);
    const isToday = ep === layout.todayEpoch;
    if (isToday) todayExportIndex = i;
    exportDays.push({
      dayIndex: i,
      epoch: ep,
      isoDate: toIsoDateInputValue(ep),
      dayOfMonth: d.getDate(),
      monthIndex: d.getMonth(),
      year: d.getFullYear(),
      weekdayIso: dow,
      isToday,
      isWeekend: dow === 6 || dow === 7
    });
  }

  // Filter lanes by selectedLaneIds
  const allowedLaneSet = exportOptions.selectedLaneIds
    ? new Set(exportOptions.selectedLaneIds)
    : null;
  const selectedLanes = allowedLaneSet
    ? layout.lanes.filter((l) => allowedLaneSet.has(l.laneId))
    : layout.lanes;

  const phaseWrapChars = orientation === 'portrait' ? 30 : 36;
  const taskMaxChars = orientation === 'portrait' ? 27 : 33;

  type PdfLaneRow = {
    kind: 'lane';
    laneId: string;
    isGeneral: boolean;
    isInferred: boolean;
    titleLabel: string;
    wrappedLines: string[];
    rowH: number;
    barStartIdx: number | null;
    barEndIdx: number | null;
    barSpanDays: number;
  };

  type PdfTaskRow = {
    kind: 'task';
    task: TaskRecord;
    isActivelyBlocked: boolean;
    isMilestone: boolean;
    rowH: number;
    barStartIdx: number;
    barEndIdx: number;
    barSpanDays: number;
  };

  type PdfBodyRow = PdfLaneRow | PdfTaskRow;

  const allBodyRows: PdfBodyRow[] = [];
  const exportedTaskIds = new Set<string>();
  const exportedUnscheduledTasks: TaskRecord[] = [];

  for (const lane of selectedLanes) {
    const laneTitleLabel =
      lane.heading == null ? labels.ungroupedTitle || lane.title : lane.title;
    const wrappedLines = wrapTextLines(laneTitleLabel, phaseWrapChars);
    const laneRowH = Math.max(28, 14 + wrappedLines.length * 11);

    let barStartIdx: number | null = null;
    let barEndIdx: number | null = null;
    let barSpanDays = 0;

    if (
      lane.heading != null &&
      lane.startEpoch != null &&
      lane.endEpoch != null &&
      lane.endEpoch >= exportStartEpoch &&
      lane.startEpoch <= exportEndEpoch
    ) {
      const clampedStart = Math.max(exportStartEpoch, lane.startEpoch);
      const clampedEnd = Math.min(exportEndEpoch, lane.endEpoch);
      barStartIdx = Math.max(
        0,
        Math.min(exportTotalDays - 1, localDaysBetween(exportStartEpoch, clampedStart))
      );
      barEndIdx = Math.max(
        barStartIdx,
        Math.min(exportTotalDays - 1, localDaysBetween(exportStartEpoch, clampedEnd))
      );
      barSpanDays = barEndIdx - barStartIdx + 1;
    }

    allBodyRows.push({
      kind: 'lane',
      laneId: lane.laneId,
      isGeneral: lane.heading == null,
      isInferred: lane.isInferred,
      titleLabel: laneTitleLabel,
      wrappedLines,
      rowH: laneRowH,
      barStartIdx,
      barEndIdx,
      barSpanDays
    });

    for (const pt of lane.plottedTasks) {
      if (pt.endEpoch < exportStartEpoch || pt.startEpoch > exportEndEpoch) {
        continue;
      }
      const clampedStart = Math.max(exportStartEpoch, pt.startEpoch);
      const clampedEnd = Math.min(exportEndEpoch, pt.endEpoch);
      const sIdx = Math.max(
        0,
        Math.min(exportTotalDays - 1, localDaysBetween(exportStartEpoch, clampedStart))
      );
      const eIdx = Math.max(
        sIdx,
        Math.min(exportTotalDays - 1, localDaysBetween(exportStartEpoch, clampedEnd))
      );
      exportedTaskIds.add(pt.task.id);
      allBodyRows.push({
        kind: 'task',
        task: pt.task,
        isActivelyBlocked: pt.isActivelyBlocked,
        isMilestone: Boolean(pt.isMilestone || pt.task.isMilestone),
        rowH: taskRowH,
        barStartIdx: sIdx,
        barEndIdx: eIdx,
        barSpanDays: eIdx - sIdx + 1
      });
    }

    for (const ut of lane.unscheduledTasks) {
      exportedUnscheduledTasks.push(ut);
    }
  }

  const filteredDependencyEdges = layout.dependencyEdges.filter(
    (edge) =>
      exportedTaskIds.has(edge.fromTaskId) && exportedTaskIds.has(edge.toTaskId)
  );

  const footerReserveH = exportedUnscheduledTasks.length > 0 ? 24 : 6;
  const maxPageBodyH = Math.max(
    120,
    pageH - marginY * 2 - headerBannerH - dayHeaderH - footerReserveH
  );

  // Paginate body rows vertically across A4 pages without orphaning lane headers
  const pageRowGroups: PdfBodyRow[][] = [];
  let currentGroup: PdfBodyRow[] = [];
  let currentGroupH = 0;

  for (let rIdx = 0; rIdx < allBodyRows.length; rIdx++) {
    const row = allBodyRows[rIdx];
    const nextRow = rIdx + 1 < allBodyRows.length ? allBodyRows[rIdx + 1] : null;
    const minNeededWithFirstChild =
      row.kind === 'lane' && nextRow?.kind === 'task'
        ? row.rowH + nextRow.rowH
        : row.rowH;

    if (
      currentGroup.length > 0 &&
      (currentGroupH + row.rowH > maxPageBodyH ||
        (row.kind === 'lane' && currentGroupH + minNeededWithFirstChild > maxPageBodyH))
    ) {
      pageRowGroups.push(currentGroup);
      currentGroup = [];
      currentGroupH = 0;
    }

    currentGroup.push(row);
    currentGroupH += row.rowH;
  }
  if (currentGroup.length > 0 || pageRowGroups.length === 0) {
    pageRowGroups.push(currentGroup);
  }

  const totalPages = pageRowGroups.length;

  // Adaptive tick spacing so day numbers never collide even on 45+ day Portrait exports
  const tickStep = Math.max(1, Math.ceil(17 / dayColW));
  const gridLineStep = dayColW >= 6 ? 1 : tickStep;

  const pdfY = (topY: number): number => pageH - topY;

  const resolvedLang: SupportedLang = labels.lang ?? 'en';
  const monthNames = MONTH_SHORT_BY_LANG[resolvedLang] ?? MONTH_SHORT_BY_LANG.en;
  const phaseTodoHeader =
    labels.phaseTodoHeader || PHASE_TODO_HEADER_BY_LANG[resolvedLang] || 'Phase / To-Do';
  const pageWord = labels.pageLabel || PAGE_LABEL_BY_LANG[resolvedLang] || 'Page';

  const rangeStart = exportDays[0];
  const rangeEnd = exportDays[exportDays.length - 1];
  const hasCustomRange =
    exportOptions.startEpoch != null || exportOptions.endEpoch != null;
  const computedRangeStr =
    rangeStart && rangeEnd
      ? `${monthNames[rangeStart.monthIndex]} ${rangeStart.dayOfMonth}, ${rangeStart.year} - ${monthNames[rangeEnd.monthIndex]} ${rangeEnd.dayOfMonth}, ${rangeEnd.year}`
      : '';
  const rangeStr =
    !hasCustomRange && labels.subtitle ? labels.subtitle : labels.subtitle && hasCustomRange ? labels.subtitle : computedRangeStr;

  const pageStreams: string[] = [];

  for (let pageIdx = 0; pageIdx < totalPages; pageIdx++) {
    const pageRows = pageRowGroups[pageIdx];
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
      maxChars: number = 96
    ) => {
      const safe = sanitizePdfText(text, maxChars);
      if (!safe) return;
      ops.push(
        `BT /${font} ${fmtNum(size)} Tf ${fmtNum(x)} ${fmtNum(pdfY(baselineTopY))} Td (${safe}) Tj ET`
      );
    };

    // 1. Crisp white A4 page background
    fillRgb(255, 255, 255);
    rectFill(0, 0, pageW, pageH);

    // 2. Top Soft Bento Project Title Banner
    const titleText = projectTitle.trim() || 'Project Roadmap';
    fillRgb(23, 26, 30);
    drawText(
      titleText,
      marginX,
      marginY + 15,
      'F2',
      14.5,
      orientation === 'portrait' ? 62 : 90
    );

    fillRgb(91, 98, 110);
    const pageSuffix = totalPages > 1 ? `  |  ${pageWord} ${pageIdx + 1} / ${totalPages}` : '';
    drawText(
      `Thingsbook Roadmap  |  ${rangeStr}${pageSuffix}`,
      marginX,
      marginY + 30,
      'F1',
      8.8,
      120
    );

    // 3. Timeline Table Header Strip
    const tableTop = marginY + headerBannerH;
    const timelineLeft = marginX + labelColW;
    const bodyTop = tableTop + dayHeaderH;

    let pageTableBodyH = 0;
    for (const r of pageRows) {
      pageTableBodyH += r.rowH;
    }
    if (pageTableBodyH === 0) pageTableBodyH = 28;

    fillRgb(244, 246, 250);
    rectFill(marginX, tableTop, tableW, dayHeaderH);

    fillRgb(91, 98, 110);
    drawText(phaseTodoHeader, marginX + 10, tableTop + 19, 'F2', 8.5, 32);

    // 4. Weekend columns, adaptive vertical day grid lines & non-colliding day/month headers
    ops.push('0.55 w');
    let lastMonthLabelX = -100;
    let lastDayLabelX = -100;

    for (let i = 0; i < exportDays.length; i++) {
      const cell = exportDays[i];
      const colX = timelineLeft + i * dayColW;

      if (cell.isWeekend) {
        fillRgb(247, 249, 252);
        rectFill(colX, bodyTop, dayColW, pageTableBodyH);
      }

      if (i % gridLineStep === 0 || cell.dayOfMonth === 1) {
        strokeRgb(231, 235, 241);
        line(colX, tableTop, colX, bodyTop + pageTableBodyH);
      }

      if ((i === 0 || cell.dayOfMonth === 1) && colX - lastMonthLabelX >= 24) {
        fillRgb(56, 152, 236);
        drawText(monthNames[cell.monthIndex], colX + 2, tableTop + 10, 'F2', 7, 8);
        lastMonthLabelX = colX;
      }

      if (cell.isToday) {
        const pillW = Math.max(13, Math.min(dayColW - 2, 20));
        const pillX = colX + (dayColW - pillW) / 2;
        fillRgb(56, 152, 236);
        rectFill(pillX, tableTop + 13, pillW, 13);
        fillRgb(255, 255, 255);
        const numStr = String(cell.dayOfMonth);
        const textOffset = numStr.length === 1 ? pillW / 2 - 2.1 : pillW / 2 - 4.2;
        drawText(numStr, pillX + Math.max(1.5, textOffset), tableTop + 22.2, 'F2', 7.2, 4);
        lastDayLabelX = colX;
      } else {
        const isTickCandidate =
          tickStep === 1 || i % tickStep === 0 || cell.dayOfMonth === 1;
        const nearToday =
          todayExportIndex >= 0 && Math.abs(colX - (timelineLeft + todayExportIndex * dayColW)) < 15;
        if (isTickCandidate && !nearToday && colX - lastDayLabelX >= 14.5) {
          fillRgb(91, 98, 110);
          const numStr = String(cell.dayOfMonth);
          const numX =
            dayColW >= 16
              ? colX + dayColW / 2 - (numStr.length === 1 ? 2.1 : 4.2)
              : colX + 1.5;
          drawText(numStr, numX, tableTop + 22.2, 'F1', 7.2, 4);
          lastDayLabelX = colX;
        }
      }
    }

    // Divider between label column and timeline
    strokeRgb(207, 214, 226);
    line(timelineLeft, tableTop, timelineLeft, bodyTop + pageTableBodyH);
    line(marginX, bodyTop, marginX + tableW, bodyTop);

    // 5. Render Page Rows (Wrapped Phase Titles + Unclipped Phase Bars + Task Bars)
    const taskBarCoords = new Map<
      string,
      { xLeft: number; xRight: number; yMid: number }
    >();

    let cursorY = bodyTop;
    for (const row of pageRows) {
      if (row.kind === 'lane') {
        fillRgb(249, 251, 254);
        rectFill(marginX, cursorY, labelColW, row.rowH);

        // Left accent pill
        if (row.isGeneral) {
          fillRgb(180, 189, 204);
        } else {
          fillRgb(56, 152, 236);
        }
        rectFill(marginX + 8, cursorY + 6, 3.5, row.rowH - 12);

        // Full multi-line Phase title (zero truncation!)
        fillRgb(23, 26, 30);
        const totalTextBlockH = row.wrappedLines.length * 11;
        const firstBaselineY =
          cursorY + (row.rowH - totalTextBlockH) / 2 + 8.5;
        for (let lIdx = 0; lIdx < row.wrappedLines.length; lIdx++) {
          const lineStr = row.wrappedLines[lIdx];
          drawText(
            lineStr,
            marginX + 16,
            firstBaselineY + lIdx * 11,
            'F2',
            8.6,
            Math.max(128, lineStr.length + 16)
          );
        }

        // Clamped Phase Bar on timeline + unclipped Phase title inside or beside narrow bar
        if (row.barStartIdx != null && row.barEndIdx != null) {
          const rawBarX = timelineLeft + row.barStartIdx * dayColW + 1.5;
          const maxAvailW = Math.max(4, timelineLeft + gridW - rawBarX - 1.5);
          const barW = Math.min(maxAvailW, Math.max(6, row.barSpanDays * dayColW - 3));
          const barH = 16;
          const barY = cursorY + (row.rowH - barH) / 2;

          if (row.isInferred) {
            fillRgb(116, 196, 255);
            strokeRgb(56, 152, 236);
            rectFillStroke(rawBarX, barY, barW, barH);
          } else {
            fillRgb(56, 152, 236);
            rectFill(rawBarX, barY, barW, barH);
            fillRgb(116, 196, 255);
            rectFill(rawBarX, barY, barW, 2.6);
          }

          const estTitleWidthPt = row.titleLabel.length * 4.4 + 12;
          const fullMaxChars = Math.max(160, row.titleLabel.length + 16);
          const textBaselineY = barY + 10.8;

          if (barW >= estTitleWidthPt) {
            if (row.isInferred) {
              fillRgb(12, 75, 134);
            } else {
              fillRgb(255, 255, 255);
            }
            drawText(row.titleLabel, rawBarX + 6, textBaselineY, 'F2', 7.4, fullMaxChars);
          } else {
            // Narrow Phase bar: render unclipped bold Sky-Blue label right next to (or left of) the bar!
            fillRgb(33, 126, 214);
            const spaceRightPt = timelineLeft + gridW - (rawBarX + barW + 5);
            const spaceLeftPt = rawBarX - timelineLeft - 5;
            if (spaceRightPt >= estTitleWidthPt || spaceRightPt >= spaceLeftPt) {
              drawText(
                row.titleLabel,
                rawBarX + barW + 5,
                textBaselineY,
                'F2',
                7.4,
                fullMaxChars
              );
            } else {
              const leftX = Math.max(
                timelineLeft + 3,
                rawBarX - (row.titleLabel.length * 4.35 + 5)
              );
              drawText(row.titleLabel, leftX, textBaselineY, 'F2', 7.4, fullMaxChars);
            }
          }
        }

        strokeRgb(231, 235, 241);
        line(marginX, cursorY + row.rowH, marginX + tableW, cursorY + row.rowH);
        cursorY += row.rowH;
      } else {
        const tk = row.task;
        if (row.isMilestone) {
          // Left column: royal-violet vector mini-diamond + bold milestone title
          const lcx = marginX + 19.5;
          const lcy = cursorY + row.rowH / 2;
          const lr = 3.2;
          fillRgb(124, 58, 237);
          ops.push(
            `${fmtNum(lcx)} ${fmtNum(pdfY(lcy - lr))} m ${fmtNum(lcx + lr)} ${fmtNum(pdfY(lcy))} l ${fmtNum(lcx)} ${fmtNum(pdfY(lcy + lr))} l ${fmtNum(lcx - lr)} ${fmtNum(pdfY(lcy))} l h f`
          );
          fillRgb(124, 58, 237);
          drawText(
            tk.title || 'Milestone',
            marginX + 26,
            cursorY + 15.5,
            'F2',
            8.1,
            taskMaxChars
          );

          // Timeline: true 45° vector diamond polygon in royal violet (#7C3AED) centered on milestone day
          const cx = timelineLeft + row.barStartIdx * dayColW + dayColW / 2;
          const cy = cursorY + row.rowH / 2;
          const r = 6.5;
          fillRgb(124, 58, 237);
          strokeRgb(91, 33, 182);
          ops.push('1.0 w');
          ops.push(
            `${fmtNum(cx)} ${fmtNum(pdfY(cy - r))} m ${fmtNum(cx + r)} ${fmtNum(pdfY(cy))} l ${fmtNum(cx)} ${fmtNum(pdfY(cy + r))} l ${fmtNum(cx - r)} ${fmtNum(pdfY(cy))} l h B`
          );
          const ir = 2.4;
          fillRgb(255, 255, 255);
          ops.push(
            `${fmtNum(cx)} ${fmtNum(pdfY(cy - ir))} m ${fmtNum(cx + ir)} ${fmtNum(pdfY(cy))} l ${fmtNum(cx)} ${fmtNum(pdfY(cy + ir))} l ${fmtNum(cx - ir)} ${fmtNum(pdfY(cy))} l h f`
          );
          ops.push('0.55 w');

          const msLabel = `◆ ${tk.title || 'Milestone'}`;
          const estMsW = msLabel.length * 4.5 + 10;
          const spaceRightPt = timelineLeft + gridW - (cx + r + 5);
          fillRgb(124, 58, 237);
          if (spaceRightPt >= estMsW || spaceRightPt >= cx - r - timelineLeft) {
            drawText(
              msLabel,
              cx + r + 5,
              cursorY + 15,
              'F2',
              7.6,
              Math.max(12, Math.floor(Math.max(48, spaceRightPt) / 4.3))
            );
          } else {
            const leftLabelX = Math.max(timelineLeft + 3, cx - r - estMsW);
            drawText(msLabel, leftLabelX, cursorY + 15, 'F2', 7.6, 64);
          }

          taskBarCoords.set(tk.id, {
            xLeft: cx - r,
            xRight: cx + r,
            yMid: cy
          });
        } else {
          fillRgb(91, 98, 110);
          const prefix = tk.status === 'COMPLETED' ? '[x] ' : '[ ] ';
          drawText(
            `${prefix}${tk.title || 'To-Do'}`,
            marginX + 16,
            cursorY + 15.5,
            'F1',
            8.1,
            taskMaxChars
          );

          const rawBarX = timelineLeft + row.barStartIdx * dayColW + 2;
          const maxAvailW = Math.max(4, timelineLeft + gridW - rawBarX - 2);
          const barW = Math.min(maxAvailW, Math.max(6, row.barSpanDays * dayColW - 4));
          const barY = cursorY + 4;
          const barH = row.rowH - 8;

          if (tk.status === 'COMPLETED') {
            fillRgb(232, 248, 238);
            strokeRgb(46, 173, 91);
          } else if (row.isActivelyBlocked) {
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
          rectFillStroke(rawBarX, barY, barW, barH);
          ops.push('0.55 w');

          fillRgb(23, 26, 30);
          if (barW >= 44) {
            drawText(
              tk.title || 'To-Do',
              rawBarX + 5,
              cursorY + 15,
              'F1',
              7.4,
              Math.max(6, Math.floor(barW / 4.4))
            );
          }

          taskBarCoords.set(tk.id, {
            xLeft: rawBarX,
            xRight: rawBarX + barW,
            yMid: cursorY + row.rowH / 2
          });
        }

        strokeRgb(238, 241, 246);
        line(marginX, cursorY + row.rowH, marginX + tableW, cursorY + row.rowH);
        cursorY += row.rowH;
      }
    }

    // 6. Dependency Connector Cubic-Bezier Lines + Arrowheads on this page
    for (const edge of filteredDependencyEdges) {
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
      const ctrlOffset = Math.max(12, Math.abs(x2 - x1) * 0.45);
      ops.push(
        `${fmtNum(x1)} ${fmtNum(pdfY(y1))} m ${fmtNum(x1 + ctrlOffset)} ${fmtNum(pdfY(y1))} ${fmtNum(x2 - ctrlOffset)} ${fmtNum(pdfY(y2))} ${fmtNum(x2)} ${fmtNum(pdfY(y2))} c S`
      );
      const ah = 3.0;
      ops.push(
        `${fmtNum(x2)} ${fmtNum(pdfY(y2))} m ${fmtNum(x2 - 4.8)} ${fmtNum(pdfY(y2 - ah))} l ${fmtNum(x2 - 4.8)} ${fmtNum(pdfY(y2 + ah))} l h f`
      );
    }

    // 7. Today Vertical Accent Line
    if (todayExportIndex >= 0 && todayExportIndex < exportTotalDays) {
      const todayLineX = timelineLeft + todayExportIndex * dayColW + dayColW / 2;
      ops.push('1.1 w');
      strokeRgb(56, 152, 236);
      line(todayLineX, bodyTop, todayLineX, bodyTop + pageTableBodyH);
    }

    // Outer border around table
    ops.push('0.8 w');
    strokeRgb(207, 214, 226);
    rectStroke(marginX, tableTop, tableW, dayHeaderH + pageTableBodyH);

    // 8. Unscheduled summary footer on final page
    if (pageIdx === totalPages - 1 && exportedUnscheduledTasks.length > 0) {
      const footY = bodyTop + pageTableBodyH + 16;
      fillRgb(91, 98, 110);
      const unschLabel = labels.unscheduledLabel || 'Unscheduled';
      const sampleNames = exportedUnscheduledTasks
        .slice(0, 5)
        .map((t) => t.title || 'To-Do')
        .join(', ');
      drawText(
        `${unschLabel} (${exportedUnscheduledTasks.length}): ${sampleNames}`,
        marginX,
        footY,
        'F1',
        8.2,
        orientation === 'portrait' ? 88 : 120
      );
    }

    pageStreams.push(ops.join('\n'));
  }

  // Assemble PDF 1.4 Catalog, Pages tree, shared Type1 Fonts, and per-page Page + Stream objects
  const pageRefs: string[] = [];
  for (let p = 0; p < pageStreams.length; p++) {
    pageRefs.push(`${5 + p * 2} 0 R`);
  }

  const objects: string[] = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    `2 0 obj\n<< /Type /Pages /Kids [${pageRefs.join(' ')}] /Count ${pageStreams.length} >>\nendobj\n`,
    '3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n',
    '4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n'
  ];

  for (let p = 0; p < pageStreams.length; p++) {
    const pageObjNum = 5 + p * 2;
    const contentObjNum = 6 + p * 2;
    const stream = pageStreams[p];
    objects.push(
      `${pageObjNum} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmtNum(pageW)} ${fmtNum(pageH)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentObjNum} 0 R >>\nendobj\n`
    );
    objects.push(
      `${contentObjNum} 0 obj\n<< /Length ${stream.length} >>\nstream\n${stream}\nendstream\nendobj\n`
    );
  }

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

export const exportProjectRoadmapPdf = generateRoadmapPdfBytes;

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
  const labelColW = 256;
  const dayColW = 32;
  const headerBannerH = 50;
  const dayHeaderH = 34;
  const taskRowH = 28;

  const laneSpecs = layout.lanes.map((lane) => {
    const laneTitle =
      lane.heading == null ? labels.ungroupedTitle || lane.title : lane.title;
    const wrappedLines = wrapTextLines(laneTitle, 34);
    const laneRowH = Math.max(34, 16 + wrappedLines.length * 14);
    return { lane, laneTitle, wrappedLines, laneRowH };
  });

  let totalBodyH = 0;
  for (const sp of laneSpecs) {
    totalBodyH += sp.laneRowH + sp.lane.plottedTasks.length * taskRowH;
  }
  if (totalBodyH === 0) totalBodyH = 34;

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
  const skyBlueLabel = isDark ? '#6BC0FF' : '#217ED6';

  ctx.fillStyle = bgCanvas;
  ctx.fillRect(0, 0, logicalW, logicalH);

  ctx.fillStyle = textPrimary;
  ctx.font = '700 17px Inter, system-ui, -apple-system, sans-serif';
  ctx.fillText(projectTitle.trim() || 'Project Roadmap', marginX, marginY + 20);

  const resolvedLang: SupportedLang = labels.lang ?? 'en';
  const monthNames = MONTH_SHORT_BY_LANG[resolvedLang] ?? MONTH_SHORT_BY_LANG.en;
  const phaseTodoHeader =
    labels.phaseTodoHeader || PHASE_TODO_HEADER_BY_LANG[resolvedLang] || 'Phase / To-Do';

  const firstDay = layout.days[0];
  const lastDay = layout.days[layout.days.length - 1];
  const subtitle =
    labels.subtitle ||
    (firstDay && lastDay
      ? `${monthNames[firstDay.monthIndex]} ${firstDay.dayOfMonth}, ${firstDay.year} – ${monthNames[lastDay.monthIndex]} ${lastDay.dayOfMonth}, ${lastDay.year}`
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
  ctx.fillText(phaseTodoHeader, marginX + 12, tableTop + 21);

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
      ctx.fillText(monthNames[cell.monthIndex], colX + 4, tableTop + 12);
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

  for (const { lane, laneTitle, wrappedLines, laneRowH } of laneSpecs) {
    ctx.fillStyle = isDark ? 'rgba(255,255,255,0.025)' : '#FAFBFD';
    ctx.fillRect(marginX, cursorY, labelColW, laneRowH);

    ctx.fillStyle = lane.heading == null ? borderStrong : '#3898EC';
    ctx.beginPath();
    ctx.roundRect(marginX + 10, cursorY + 7, 4, laneRowH - 14, 2);
    ctx.fill();

    // Full multi-line Phase title in left column (zero truncation!)
    ctx.fillStyle = textPrimary;
    ctx.font = '650 11.5px Inter, system-ui, sans-serif';
    const textBlockH = wrappedLines.length * 14;
    const firstLineY = cursorY + (laneRowH - textBlockH) / 2 + 11;
    for (let lIdx = 0; lIdx < wrappedLines.length; lIdx++) {
      ctx.fillText(wrappedLines[lIdx], marginX + 20, firstLineY + lIdx * 14);
    }

    if (
      lane.heading != null &&
      lane.startEpoch != null &&
      lane.endEpoch != null &&
      lane.endEpoch >= layout.windowStartEpoch &&
      lane.startEpoch <= layout.windowEndEpoch
    ) {
      const clampedStart = Math.max(layout.windowStartEpoch, lane.startEpoch);
      const clampedEnd = Math.min(layout.windowEndEpoch, lane.endEpoch);
      const sIdx = Math.max(
        0,
        Math.min(layout.totalDays - 1, localDaysBetween(layout.windowStartEpoch, clampedStart))
      );
      const eIdx = Math.max(
        sIdx,
        Math.min(layout.totalDays - 1, localDaysBetween(layout.windowStartEpoch, clampedEnd))
      );
      const spanDays = eIdx - sIdx + 1;

      const barX = timelineLeft + sIdx * dayColW + 3;
      const maxAvailW = Math.max(8, timelineLeft + gridW - barX - 3);
      const barW = Math.min(maxAvailW, Math.max(14, spanDays * dayColW - 6));
      const barH = 22;
      const barY = cursorY + (laneRowH - barH) / 2;

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

      ctx.font = '700 10.5px Inter, system-ui, sans-serif';
      const measuredTitleW = ctx.measureText(laneTitle).width;
      const titleBaselineY = barY + 14.5;

      if (barW >= measuredTitleW + 16) {
        ctx.fillStyle = lane.isInferred ? textPrimary : '#FFFFFF';
        ctx.fillText(laneTitle, barX + 8, titleBaselineY);
      } else {
        ctx.fillStyle = skyBlueLabel;
        const spaceRight = timelineLeft + gridW - (barX + barW + 7);
        if (spaceRight >= measuredTitleW + 4 || spaceRight >= barX - timelineLeft) {
          ctx.fillText(laneTitle, barX + barW + 7, titleBaselineY);
        } else {
          ctx.fillText(
            laneTitle,
            Math.max(timelineLeft + 4, barX - measuredTitleW - 7),
            titleBaselineY
          );
        }
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
      const isMs = Boolean(pt.isMilestone || tk.isMilestone);
      if (isMs) {
        const violetColor = isDark ? '#A78BFA' : '#7C3AED';
        ctx.fillStyle = violetColor;
        ctx.font = '700 11px Inter, system-ui, sans-serif';
        ctx.fillText(`◆ ${(tk.title || 'Milestone').slice(0, 32)}`, marginX + 20, cursorY + 18);

        const cx = timelineLeft + pt.startDayIndex * dayColW + dayColW / 2;
        const cy = cursorY + taskRowH / 2;
        const r = 8;

        ctx.fillStyle = '#7C3AED';
        ctx.strokeStyle = '#FFFFFF';
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.moveTo(cx, cy - r);
        ctx.lineTo(cx + r, cy);
        ctx.lineTo(cx, cy + r);
        ctx.lineTo(cx - r, cy);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();

        const msCallout = `◆ ${tk.title || 'Milestone'}`;
        ctx.font = '700 10.5px Inter, system-ui, sans-serif';
        ctx.fillStyle = violetColor;
        const measuredMsW = ctx.measureText(msCallout).width;
        const spaceRight = timelineLeft + gridW - (cx + r + 7);
        if (spaceRight >= measuredMsW + 4 || spaceRight >= cx - r - timelineLeft) {
          ctx.fillText(msCallout, cx + r + 7, cursorY + 17.5);
        } else {
          ctx.fillText(
            msCallout,
            Math.max(timelineLeft + 4, cx - r - measuredMsW - 7),
            cursorY + 17.5
          );
        }

        barCoords.set(tk.id, {
          xLeft: cx - r,
          xRight: cx + r,
          yMid: cy
        });
      } else {
        ctx.fillStyle = textSecondary;
        ctx.font = '500 11px Inter, system-ui, sans-serif';
        ctx.fillText((tk.title || 'To-Do').slice(0, 34), marginX + 20, cursorY + 18);

        const barX = timelineLeft + pt.startDayIndex * dayColW + 4;
        const barW = Math.min(
          Math.max(8, timelineLeft + gridW - barX - 4),
          Math.max(14, pt.spanDays * dayColW - 8)
        );
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
      }

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

export const exportProjectRoadmapPng = renderRoadmapToPngDataUrl;

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

// ==================== Hold-to-Drag Roadmap Interaction Guard ====================

export const ROADMAP_MOUSE_HOLD_MS = 260;
export const ROADMAP_TOUCH_HOLD_MS = 320;
export const ROADMAP_HOLD_MOVE_SLOP_PX = 6;
export const ROADMAP_MOUSE_DRAG_ACTIVATE_PX = 4;
export const ROADMAP_TOUCH_HOLD_SLOP_PX = 10;

export function resolveRoadmapHoldDurationMs(pointerType: string): number {
  return pointerType === 'mouse' ? ROADMAP_MOUSE_HOLD_MS : ROADMAP_TOUCH_HOLD_MS;
}

export function shouldCancelUnarmedRoadmapDrag(
  startClientX: number,
  startClientY: number,
  currentClientX: number,
  currentClientY: number,
  slopPx: number = ROADMAP_HOLD_MOVE_SLOP_PX
): boolean {
  return Math.hypot(currentClientX - startClientX, currentClientY - startClientY) > slopPx;
}

/**
 * Returns true strictly when the user is actively keeping the primary mouse button pressed
 * or maintaining finger/stylus contact on the screen. Hovering (buttons === 0) ALWAYS returns false.
 */
export function isRoadmapPointerActivelyPressed(
  pointerType: string,
  buttons: number | undefined
): boolean {
  if (pointerType === 'mouse' || pointerType === 'pen') {
    return typeof buttons === 'number' && (buttons & 1) === 1;
  }
  if (typeof buttons === 'number') {
    return buttons !== 0;
  }
  return true;
}

/**
 * Mouse press-and-drag activates as soon as the user moves >= 4px while keeping
 * the primary mouse button pressed (buttons & 1 === 1), while pure hover or tiny click jitter does not.
 */
export function shouldActivateMouseRoadmapDrag(
  startClientX: number,
  startClientY: number,
  currentClientX: number,
  currentClientY: number,
  buttons: number | undefined,
  activatePx: number = ROADMAP_MOUSE_DRAG_ACTIVATE_PX
): boolean {
  if (!isRoadmapPointerActivelyPressed('mouse', buttons)) return false;
  return Math.hypot(currentClientX - startClientX, currentClientY - startClientY) >= activatePx;
}


