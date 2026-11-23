import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import {
  buildAreaPortfolioRoadmapLayout,
  computeCascadeDeltaPreviewMap,
  computeSmartDependencyCascade,
  generateRoadmapPdfBytes,
  normalizeAreaViewMode,
  toExportablePortfolioLayout
} from '../src/domain/v30Roadmap.ts';
import {
  computeMultiDayWorkloadForecast,
  computeTodayCapacitySummary
} from '../src/domain/v2Utils.ts';
import {
  buildDeterministicDayTimeblockPlan,
  generateDailyTimeblockPlan,
  generateProjectBlueprint,
  sanitizeProposedTimeblocks
} from '../src/data/geminiService.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  normalizeBackupPayloadToEntities
} from '../src/data/backupCodec.ts';
import {
  DEFAULT_PREFS,
  ThingsLocalDb,
  type UndoEventPayload
} from '../src/data/localDb.ts';
import {
  addDaysLocal,
  endOfDayEpoch,
  localEpochDayFromMillis,
  startOfDayEpoch,
  type AreaRecord,
  type CalendarEventItem,
  type HeadingRecord,
  type ProjectRecord,
  type SupportedLang,
  type TaskRecord
} from '../src/domain/types.ts';

type StoreMap = Map<string, unknown>;

function createInMemoryIdb(): {
  opener: () => Promise<IDBPDatabase>;
  stores: Map<string, StoreMap>;
} {
  const stores = new Map<string, StoreMap>([
    ['areas', new Map()],
    ['projects', new Map()],
    ['headings', new Map()],
    ['tasks', new Map()],
    ['checklist_items', new Map()],
    ['tags', new Map()],
    ['attachments', new Map()],
    ['meta', new Map([['initial_seeded', true]])],
    ['projectTemplates', new Map()]
  ]);

  const getStoreMap = (name: string): StoreMap => {
    let m = stores.get(name);
    if (!m) {
      m = new Map();
      stores.set(name, m);
    }
    return m;
  };

  const makeObjectStore = (name: string) => {
    const map = getStoreMap(name);
    return {
      async get(key: string) {
        const val = map.get(key);
        return val === undefined ? undefined : structuredClone(val);
      },
      async getAll() {
        return Array.from(map.values()).map((v) => structuredClone(v));
      },
      async put(value: unknown, explicitKey?: string) {
        const k =
          explicitKey ??
          (typeof value === 'object' && value !== null && 'id' in value
            ? String((value as { id: string }).id)
            : '');
        map.set(k, structuredClone(value));
        return k;
      },
      async delete(key: string) {
        map.delete(key);
      },
      async clear() {
        map.clear();
      },
      async count() {
        return map.size;
      }
    };
  };

  const dbFake = {
    async get(storeName: string, key: string) {
      return makeObjectStore(storeName).get(key);
    },
    async getAll(storeName: string) {
      return makeObjectStore(storeName).getAll();
    },
    async put(storeName: string, value: unknown, key?: string) {
      return makeObjectStore(storeName).put(value, key);
    },
    async count(storeName: string) {
      return makeObjectStore(storeName).count();
    },
    transaction(storeNames: string | string[]) {
      const allowed = new Set(Array.isArray(storeNames) ? storeNames : [storeNames]);
      return {
        store: !Array.isArray(storeNames) ? makeObjectStore(storeNames) : undefined,
        objectStore(name: string) {
          assert.ok(allowed.has(name), `Store "${name}" not included in transaction`);
          return makeObjectStore(name);
        },
        done: Promise.resolve()
      };
    }
  } as unknown as IDBPDatabase;

  return {
    opener: async () => dbFake,
    stores
  };
}

function makeTask(overrides: Partial<TaskRecord> & { id: string; title: string }): TaskRecord {
  const now = 1_700_000_000_000;
  return {
    id: overrides.id,
    title: overrides.title,
    notes: overrides.notes ?? '',
    status: overrides.status ?? 'OPEN',
    scheduleState: overrides.scheduleState ?? 'UPCOMING',
    whenDate: overrides.whenDate ?? null,
    deadline: overrides.deadline ?? null,
    reminderTime: overrides.reminderTime ?? null,
    scheduledStartMinutes: overrides.scheduledStartMinutes ?? null,
    durationMinutes: overrides.durationMinutes ?? 60,
    isPinned: overrides.isPinned ?? false,
    isMilestone: overrides.isMilestone ?? false,
    effortLevel: overrides.effortLevel ?? null,
    blockedByTaskIds: overrides.blockedByTaskIds ?? [],
    completedAt: overrides.completedAt ?? null,
    projectId: overrides.projectId ?? 'proj-1',
    headingId: overrides.headingId ?? null,
    areaId: overrides.areaId ?? null,
    recurrenceRule: overrides.recurrenceRule ?? null,
    sortOrder: overrides.sortOrder ?? 100,
    tagIds: overrides.tagIds ?? [],
    createdAt: overrides.createdAt ?? now,
    updatedAt: overrides.updatedAt ?? now,
    isDeleted: overrides.isDeleted ?? false,
    isDirty: overrides.isDirty ?? false
  };
}

function makeProject(
  overrides: Partial<ProjectRecord> & { id: string; title: string }
): ProjectRecord {
  const now = 1_700_000_000_000;
  return {
    id: overrides.id,
    areaId: overrides.areaId ?? 'area-1',
    title: overrides.title,
    notes: overrides.notes ?? '',
    status: overrides.status ?? 'ACTIVE',
    scheduleState: overrides.scheduleState ?? 'ANYTIME',
    whenDate: overrides.whenDate ?? null,
    deadline: overrides.deadline ?? null,
    completedAt: overrides.completedAt ?? null,
    lastReviewedAt: overrides.lastReviewedAt ?? now,
    sortOrder: overrides.sortOrder ?? 100,
    tagIds: overrides.tagIds ?? [],
    viewMode: overrides.viewMode ?? 'roadmap',
    createdAt: overrides.createdAt ?? now,
    updatedAt: overrides.updatedAt ?? now,
    isDeleted: overrides.isDeleted ?? false,
    isDirty: overrides.isDirty ?? false
  };
}

function makeHeading(
  overrides: Partial<HeadingRecord> & { id: string; projectId: string; title: string }
): HeadingRecord {
  const now = 1_700_000_000_000;
  return {
    id: overrides.id,
    projectId: overrides.projectId,
    title: overrides.title,
    startDate: overrides.startDate ?? null,
    endDate: overrides.endDate ?? null,
    sortOrder: overrides.sortOrder ?? 1000,
    isArchived: overrides.isArchived ?? false,
    createdAt: overrides.createdAt ?? now,
    updatedAt: overrides.updatedAt ?? now,
    isDeleted: overrides.isDeleted ?? false,
    isDirty: overrides.isDirty ?? false
  };
}

// ==================== 1. Smart Push-on-Conflict Dependency Cascade + Gantt Undo ====================

test('v3.1 Smart Cascade pushes conflicting downstream tasks forward while preserving slack & never pulling backward', () => {
  const today = startOfDayEpoch(new Date(2026, 4, 10)); // May 10, 2026
  const taskA = makeTask({
    id: 't-a',
    title: 'Design Spec',
    whenDate: today, // Day 0 (1 day duration)
    durationMinutes: 240
  });
  const taskB = makeTask({
    id: 't-b',
    title: 'Implement Core',
    whenDate: addDaysLocal(today, 2), // Day 2..3 (2 days duration = 960m), 1 day slack after Day 0
    durationMinutes: 960,
    blockedByTaskIds: ['t-a']
  });
  const taskC = makeTask({
    id: 't-c',
    title: 'Launch Milestone',
    whenDate: addDaysLocal(today, 4), // Day 4
    deadline: addDaysLocal(today, 4),
    isMilestone: true,
    durationMinutes: 30,
    blockedByTaskIds: ['t-b']
  });
  const completedDownstream = makeTask({
    id: 't-done',
    title: 'Already Done',
    status: 'COMPLETED',
    whenDate: addDaysLocal(today, 1),
    blockedByTaskIds: ['t-a']
  });

  const allTasks = [taskA, taskB, taskC, completedDownstream];

  // Case 1: Shift Task A by +1 day (Day 0 -> Day 1). Task B starts on Day 2 (blockerNewEndDay=1 < 2) -> slack absorbs shift!
  const slackShifts = computeSmartDependencyCascade(taskA, 1, allTasks, today);
  assert.equal(slackShifts.length, 1);
  assert.equal(slackShifts[0].task.id, 't-a');
  assert.equal(slackShifts[0].deltaDays, 1);
  assert.equal(slackShifts[0].isAnchor, true);

  // Case 2: Shift Task A by +3 days (Day 0 -> Day 3).
  // blockerNewEndDay = 3 >= taskB.startDay(2) -> pushNeeded = (3 + 1) - 2 = +2 days (Task B -> Day 4..5).
  // Task B new end = 3 + 2 = Day 5 >= taskC.startDay(4) -> pushNeeded = (5 + 1) - 4 = +2 days (Task C -> Day 6).
  // Completed task is untouched!
  const cascadeShifts = computeSmartDependencyCascade(taskA, 3, allTasks, today);
  const byId = new Map(cascadeShifts.map((s) => [s.task.id, s]));
  assert.equal(cascadeShifts.length, 3);
  assert.equal(byId.get('t-a')?.deltaDays, 3);
  assert.equal(byId.get('t-b')?.deltaDays, 2);
  assert.equal(byId.get('t-b')?.whenDate, addDaysLocal(today, 4));
  assert.equal(byId.get('t-c')?.deltaDays, 2);
  assert.equal(byId.get('t-c')?.whenDate, addDaysLocal(today, 6));
  assert.equal(byId.get('t-c')?.deadline, addDaysLocal(today, 6));
  assert.equal(byId.has('t-done'), false);

  const previewMap = computeCascadeDeltaPreviewMap(taskA, 3, allTasks, today);
  assert.equal(previewMap.get('t-a'), 3);
  assert.equal(previewMap.get('t-b'), 2);
  assert.equal(previewMap.get('t-c'), 2);

  // Case 3: Negative shift (-2 days) moves only anchor backward; never pulls downstream tasks backward
  const backShifts = computeSmartDependencyCascade(taskB, -2, allTasks, today);
  assert.equal(backShifts.length, 1);
  assert.equal(backShifts[0].task.id, 't-b');
  assert.equal(backShifts[0].deltaDays, -2);
});

test('v3.1 Smart Cascade handles cyclic blockedByTaskIds safely without infinite loop', () => {
  const today = startOfDayEpoch(new Date(2026, 4, 10));
  const t1 = makeTask({
    id: 'cyc-1',
    title: 'Node 1',
    whenDate: today,
    blockedByTaskIds: ['cyc-2']
  });
  const t2 = makeTask({
    id: 'cyc-2',
    title: 'Node 2',
    whenDate: today,
    blockedByTaskIds: ['cyc-1']
  });

  const shifts = computeSmartDependencyCascade(t1, 2, [t1, t2], today);
  assert.equal(shifts.length, 2);
  const byId = new Map(shifts.map((s) => [s.task.id, s.deltaDays]));
  assert.equal(byId.get('cyc-1'), 2);
  assert.equal(byId.get('cyc-2'), 3);
});

test('v3.1 localDb rescheduleRoadmapTaskWithCascade & rescheduleRoadmapHeading support atomic Undo restoration', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const undoEvents: UndoEventPayload[] = [];
  const unsub = db.subscribeUndo((ev) => undoEvents.push(ev));

  const today = startOfDayEpoch();
  const proj = await db.upsertProject({
    title: 'Roadmap Undo Project',
    viewMode: 'roadmap'
  });
  const heading = await db.upsertHeading({
    projectId: proj.id,
    title: 'Phase 1',
    startDate: today,
    endDate: addDaysLocal(today, 3)
  });

  const blocker = await db.upsertTask({
    title: 'Blocker Task',
    projectId: proj.id,
    headingId: heading.id,
    scheduleState: 'TODAY',
    whenDate: today,
    durationMinutes: 120
  });
  const dependent = await db.upsertTask({
    title: 'Dependent Milestone',
    projectId: proj.id,
    headingId: heading.id,
    scheduleState: 'UPCOMING',
    whenDate: addDaysLocal(today, 1),
    deadline: addDaysLocal(today, 1),
    isMilestone: true,
    blockedByTaskIds: [blocker.id]
  });

  // Shift blocker +3 days -> pushes dependent from Day 1 to Day 4 (+3 days)
  const cascadeRes = await db.rescheduleRoadmapTaskWithCascade(blocker, 3, today);
  assert.equal(cascadeRes.shiftedCount, 2);
  assert.equal(cascadeRes.cascadedCount, 1);
  assert.equal(undoEvents.at(-1)?.kind, 'roadmap_cascade');
  assert.equal(undoEvents.at(-1)?.count, 1);

  let snap = db.getSnapshot();
  assert.equal(
    snap.tasks.find((t) => t.id === blocker.id)?.whenDate,
    addDaysLocal(today, 3)
  );
  assert.equal(
    snap.tasks.find((t) => t.id === dependent.id)?.whenDate,
    addDaysLocal(today, 4)
  );
  assert.equal(
    snap.tasks.find((t) => t.id === dependent.id)?.deadline,
    addDaysLocal(today, 4)
  );

  // Undo restores both blocker and cascaded dependent dates!
  const undoneCascade = await db.performUndo();
  assert.equal(undoneCascade, true);
  snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === blocker.id)?.whenDate, today);
  assert.equal(
    snap.tasks.find((t) => t.id === dependent.id)?.whenDate,
    addDaysLocal(today, 1)
  );
  assert.equal(
    snap.tasks.find((t) => t.id === dependent.id)?.deadline,
    addDaysLocal(today, 1)
  );

  // Heading reschedule + Undo
  const shiftedHeadingOk = await db.rescheduleRoadmapHeading(
    heading.id,
    addDaysLocal(today, 5),
    addDaysLocal(today, 8),
    heading.title
  );
  assert.equal(shiftedHeadingOk, true);
  assert.equal(undoEvents.at(-1)?.kind, 'roadmap_rescheduled');
  snap = db.getSnapshot();
  assert.equal(
    snap.headings.find((h) => h.id === heading.id)?.startDate,
    addDaysLocal(today, 5)
  );
  assert.equal(
    snap.headings.find((h) => h.id === heading.id)?.endDate,
    addDaysLocal(today, 8)
  );

  const undoneHeading = await db.performUndo();
  assert.equal(undoneHeading, true);
  snap = db.getSnapshot();
  assert.equal(snap.headings.find((h) => h.id === heading.id)?.startDate, today);
  assert.equal(
    snap.headings.find((h) => h.id === heading.id)?.endDate,
    addDaysLocal(today, 3)
  );

  unsub();
});

// ==================== 2. Area Portfolio Timeline ====================

test('v3.1 buildAreaPortfolioRoadmapLayout separates scheduled project lanes, milestone bars, and unscheduled projects', () => {
  const today = startOfDayEpoch(new Date(2026, 4, 10));
  const area: Pick<AreaRecord, 'id' | 'title'> = {
    id: 'area-product',
    title: 'Product Engineering'
  };

  const projScheduled = makeProject({
    id: 'p-sched',
    areaId: area.id,
    title: 'v3.1 Release',
    sortOrder: 10
  });
  const projUnscheduled = makeProject({
    id: 'p-unsched',
    areaId: area.id,
    title: 'Backlog Ideas',
    sortOrder: 20,
    whenDate: null,
    deadline: null
  });
  const projOtherArea = makeProject({
    id: 'p-other',
    areaId: 'area-other',
    title: 'Other Area Project',
    whenDate: today
  });

  const headings: HeadingRecord[] = [
    makeHeading({
      id: 'h-1',
      projectId: projScheduled.id,
      title: 'Implementation',
      startDate: today,
      endDate: addDaysLocal(today, 4)
    })
  ];

  const tasks: TaskRecord[] = [
    makeTask({
      id: 't-1',
      projectId: projScheduled.id,
      headingId: 'h-1',
      title: 'Build Area Portfolio',
      whenDate: today,
      durationMinutes: 480
    }),
    makeTask({
      id: 't-ms',
      projectId: projScheduled.id,
      headingId: 'h-1',
      title: 'GA Ship Milestone',
      whenDate: addDaysLocal(today, 5),
      deadline: addDaysLocal(today, 5),
      isMilestone: true,
      durationMinutes: 30
    }),
    makeTask({
      id: 't-done',
      projectId: projScheduled.id,
      headingId: 'h-1',
      title: 'Spec Sign-Off',
      status: 'COMPLETED',
      whenDate: today
    }),
    makeTask({
      id: 't-unsched',
      projectId: projUnscheduled.id,
      title: 'Someday research',
      scheduleState: 'ANYTIME',
      whenDate: null,
      deadline: null
    })
  ];

  const portfolio = buildAreaPortfolioRoadmapLayout(
    area,
    [projScheduled, projUnscheduled, projOtherArea],
    headings,
    tasks,
    today
  );

  assert.equal(portfolio.areaId, area.id);
  assert.equal(portfolio.projectLanes.length, 1);
  assert.equal(portfolio.unscheduledProjects.length, 1);
  assert.equal(portfolio.unscheduledProjects[0].id, projUnscheduled.id);

  const lane = portfolio.projectLanes[0];
  assert.equal(lane.project.id, projScheduled.id);
  assert.equal(lane.summaryStartDay, today);
  assert.equal(lane.summaryEndDay, addDaysLocal(today, 5));
  assert.equal(lane.summaryStartDayIndex, portfolio.todayDayIndex);
  assert.equal(lane.summaryEndDayIndex, portfolio.todayDayIndex + 5);
  assert.equal(lane.spanDays, 6);
  assert.equal(lane.completedCount, 1);
  assert.equal(lane.totalCount, 3);
  assert.equal(lane.milestoneBars.length, 1);
  assert.equal(lane.milestoneBars[0].task.id, 't-ms');
  assert.equal(
    portfolio.days[lane.milestoneBars[0].startDayIndex].epoch,
    addDaysLocal(today, 5)
  );

  assert.equal(normalizeAreaViewMode('roadmap'), 'roadmap');
  assert.equal(normalizeAreaViewMode('list'), 'list');
  assert.equal(normalizeAreaViewMode(undefined), 'list');

  // Verify Area Portfolio PDF export adapter (collapsed milestones & expanded tasks)
  const exportCollapsed = toExportablePortfolioLayout(portfolio, new Set());
  assert.equal(exportCollapsed.lanes.length, 1);
  assert.equal(exportCollapsed.plottedTasks.length, 1);
  assert.equal(exportCollapsed.plottedTasks[0].isMilestone, true);
  const pdfBytes = generateRoadmapPdfBytes(exportCollapsed, area.title);
  const pdfHeader = new TextDecoder().decode(pdfBytes.slice(0, 8));
  assert.ok(pdfHeader.startsWith('%PDF-'));
});

// ==================== 3. Configurable Workday Hours + 7-Day Workload Forecast ====================

test('v3.1 computeTodayCapacitySummary clips calendar meetings to custom workday bounds while preserving default 09:00-17:00 compatibility', () => {
  const dayStart = startOfDayEpoch(new Date(2026, 4, 10));
  const dayEnd = endOfDayEpoch(new Date(2026, 4, 10));

  const tasks: TaskRecord[] = [
    makeTask({
      id: 'cap-1',
      title: 'Deep Focus Block',
      scheduleState: 'TODAY',
      whenDate: dayStart,
      durationMinutes: 90
    })
  ];

  const events: CalendarEventItem[] = [
    // Early 08:00 - 09:30 meeting (only 09:00-09:30 = 30m overlaps default 09:00-17:00; 0m overlaps 10:00-16:00!)
    {
      eventId: 'ev-early',
      calendarId: 'cal-work',
      calendarName: 'Work',
      calendarColorCss: '#3898EC',
      title: 'Early Standup',
      startMillis: dayStart + 8 * 60 * 60_000,
      endMillis: dayStart + 9.5 * 60 * 60_000,
      isAllDay: false,
      location: null,
      htmlLink: null
    },
    // Midday 11:00 - 12:00 meeting (60m inside both)
    {
      eventId: 'ev-mid',
      calendarId: 'cal-work',
      calendarName: 'Work',
      calendarColorCss: '#3898EC',
      title: 'Design Sync',
      startMillis: dayStart + 11 * 60 * 60_000,
      endMillis: dayStart + 12 * 60 * 60_000,
      isAllDay: false,
      location: null,
      htmlLink: null
    },
    // Evening 18:00 - 19:00 dinner (outside standard workday)
    {
      eventId: 'ev-late',
      calendarId: 'cal-work',
      calendarName: 'Work',
      calendarColorCss: '#3898EC',
      title: 'Team Dinner',
      startMillis: dayStart + 18 * 60 * 60_000,
      endMillis: dayStart + 19 * 60 * 60_000,
      isAllDay: false,
      location: null,
      htmlLink: null
    }
  ];

  // Default 4-arg call (09:00..17:00 = 480m total, 30m + 60m = 90m busy, 90m planned -> 300m free)
  const defaultCap = computeTodayCapacitySummary(tasks, events, dayStart, dayEnd);
  assert.equal(defaultCap.plannedMinutes, 90);
  assert.equal(defaultCap.freeMinutes, 300);

  // Custom 10:00..16:00 workday (600..960 = 360m total, only 60m midday meeting inside bounds, 90m planned -> 210m free)
  const customCap = computeTodayCapacitySummary(tasks, events, dayStart, dayEnd, 600, 960);
  assert.equal(customCap.plannedMinutes, 90);
  assert.equal(customCap.freeMinutes, 210);
});

test('v3.1 computeMultiDayWorkloadForecast computes 7-day load fractions, free focus, and overload states', () => {
  const todayStart = startOfDayEpoch(new Date(2026, 4, 10));
  const day1Start = addDaysLocal(todayStart, 1);
  const day1EpochDay = localEpochDayFromMillis(day1Start);

  const todayTask = makeTask({
    id: 'w-today',
    title: 'Today Focus',
    scheduleState: 'TODAY',
    whenDate: todayStart,
    durationMinutes: 120
  });
  const day1HeavyTask = makeTask({
    id: 'w-day1',
    title: 'Heavy Architecture Day',
    scheduleState: 'UPCOMING',
    whenDate: day1Start,
    durationMinutes: 300
  });

  const upcomingCal = new Map<number, CalendarEventItem[]>([
    [
      day1EpochDay,
      [
        {
          eventId: 'cal-d1',
          calendarId: 'cal-work',
          calendarName: 'Work',
          calendarColorCss: '#3898EC',
          title: 'Workshop',
          startMillis: day1Start + 10 * 60 * 60_000,
          endMillis: day1Start + 13 * 60 * 60_000, // 180m
          isAllDay: false,
          location: null,
          htmlLink: null
        }
      ]
    ]
  ]);

  // Custom 6h workday (09:00 - 15:00 = 360m capacity)
  const forecast = computeMultiDayWorkloadForecast({
    todayTasks: [todayTask],
    allOpenTasks: [todayTask, day1HeavyTask],
    todayCalendarEvents: [],
    upcomingCalendarEventsByEpochDay: upcomingCal,
    todayStartMillis: todayStart,
    daysCount: 7,
    workdayStartMinutes: 540,
    workdayEndMinutes: 900
  });

  assert.equal(forecast.length, 7);

  // Day 0: 120m planned, 0m cal, 360m cap -> 240m free, not overloaded
  assert.equal(forecast[0].plannedMinutes, 120);
  assert.equal(forecast[0].freeMinutes, 240);
  assert.equal(forecast[0].totalCapacityMinutes, 360);
  assert.equal(forecast[0].isOverloaded, false);
  assert.equal(forecast[0].overloadedByMinutes, 0);

  // Day 1: 300m planned + 180m cal = 480m > 360m cap -> overloaded by 120m!
  assert.equal(forecast[1].plannedMinutes, 300);
  assert.equal(forecast[1].busyCalendarMinutes, 180);
  assert.equal(forecast[1].freeMinutes, 0);
  assert.equal(forecast[1].isOverloaded, true);
  assert.equal(forecast[1].overloadedByMinutes, 120);
  assert.ok(forecast[1].loadFraction > 1);
});

test('v3.1 .tbbackup codec roundtrips workdayStartMinutes, workdayEndMinutes, and areaViewModes', async () => {
  const payload = buildBackupPayloadFromEntities({
    areas: [],
    projects: [],
    headings: [],
    tasks: [],
    checklistItems: [],
    tags: [],
    projectTemplates: [],
    preferences: {
      ...DEFAULT_PREFS,
      workdayStartMinutes: 480,
      workdayEndMinutes: 990,
      areaViewModes: { 'area-1': 'roadmap', 'area-2': 'list' }
    }
  });

  const bytes = await encodeThingsBackup(payload, null);
  const decoded = await decodeThingsBackup(bytes, null);
  const restored = normalizeBackupPayloadToEntities(decoded);

  assert.equal(restored.preferences.workdayStartMinutes, 480);
  assert.equal(restored.preferences.workdayEndMinutes, 990);
  assert.deepEqual(restored.preferences.areaViewModes, {
    'area-1': 'roadmap',
    'area-2': 'list'
  });
});

// ==================== 4. v3-Aware Gemini Roadmap Architect + "Plan My Day" Auto-Timeblocker ====================

test('v3.1 generateProjectBlueprint enriches all 5 languages with phase spans, richTasks, milestones, dependencies & createV3RoadmapProjectWithBlueprint instantiates roadmap DAG', async () => {
  const langs: SupportedLang[] = ['en', 'de', 'fr', 'it', 'es'];

  for (const lang of langs) {
    const bp = await generateProjectBlueprint('Launch Thingsbook v3.1', lang, '');
    assert.ok(bp.headings.length >= 3, `Expected >= 3 phases for ${lang}`);
    for (const h of bp.headings) {
      assert.equal(typeof h.startDayOffset, 'number');
      assert.equal(typeof h.endDayOffset, 'number');
      assert.ok(h.tasks.length > 0, `Backward-compatible string tasks[] preserved for ${lang}`);
      assert.ok(Array.isArray(h.richTasks) && h.richTasks.length === h.tasks.length);
    }
    const allRich = bp.headings.flatMap((h) => h.richTasks ?? []);
    assert.ok(allRich.some((rt) => rt.isMilestone));
    assert.ok(allRich.some((rt) => rt.dependsOnKeys.length > 0));
    assert.ok(allRich.some((rt) => rt.checklist.length > 0));
  }

  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const area = await db.upsertArea({ title: 'AI Launches' });
  const enBlueprint = await generateProjectBlueprint('Ship Mobile & Web Parity', 'en', '');
  const projectId = await db.createV3RoadmapProjectWithBlueprint(enBlueprint, area.id);

  const snap = db.getSnapshot();
  const createdProj = snap.projects.find((p) => p.id === projectId);
  assert.ok(createdProj);
  assert.equal(createdProj.viewMode, 'roadmap');
  assert.equal(createdProj.areaId, area.id);
  assert.ok(createdProj.whenDate != null);
  assert.ok(createdProj.deadline != null);

  const projHeadings = snap.headings.filter((h) => h.projectId === projectId && !h.isDeleted);
  assert.equal(projHeadings.length, enBlueprint.headings.length);
  assert.ok(projHeadings.every((h) => h.startDate != null && h.endDate != null));

  const projTasks = snap.tasks.filter((t) => t.projectId === projectId && !t.isDeleted);
  assert.equal(
    projTasks.length,
    enBlueprint.headings.reduce((acc, h) => acc + (h.richTasks?.length ?? 0), 0)
  );
  // Container invariant: tasks inside a project have areaId = null
  assert.ok(projTasks.every((t) => t.areaId === null));
  // Milestones have deadline populated, dependencies resolved to real task UUIDs, checklists created
  const milestones = projTasks.filter((t) => t.isMilestone);
  assert.ok(milestones.length >= 2);
  assert.ok(milestones.every((m) => m.deadline != null));
  const taskIdSet = new Set(projTasks.map((t) => t.id));
  const tasksWithDeps = projTasks.filter((t) => (t.blockedByTaskIds?.length ?? 0) > 0);
  assert.ok(tasksWithDeps.length >= 4);
  for (const t of tasksWithDeps) {
    for (const bid of t.blockedByTaskIds ?? []) {
      assert.ok(taskIdSet.has(bid), `Resolved blocker ID ${bid} must belong to created project tasks`);
    }
  }
  const checklists = snap.checklistItems.filter(
    (c) => taskIdSet.has(c.taskId) && !c.isDeleted
  );
  assert.ok(checklists.length >= 2);
});

test('v3.1 deterministic Plan My Day auto-timeblocker schedules conflict-free 15m blocks across all 5 languages & applyBatchTaskTimeblocks supports Undo', async () => {
  const candidates = [
    {
      taskId: 't-quick',
      title: 'Reply to design review',
      durationMinutes: 30,
      effortLevel: 'QUICK_WIN' as const,
      isPinned: false,
      hasTodayDeadline: false
    },
    {
      taskId: 't-pinned',
      title: 'Finalize release notes',
      durationMinutes: 60,
      effortLevel: 'DEEP_FOCUS' as const,
      isPinned: true,
      hasTodayDeadline: true
    },
    {
      taskId: 't-med',
      title: 'Verify cloud sync backup',
      durationMinutes: 45,
      effortLevel: 'MEDIUM' as const,
      isPinned: false,
      hasTodayDeadline: false
    }
  ];

  // Busy meeting 09:00 - 10:00 (540..600) and 11:00 - 11:30 (660..690)
  const busy: Array<[number, number]> = [
    [540, 600],
    [660, 690]
  ];

  const langs: SupportedLang[] = ['en', 'de', 'fr', 'it', 'es'];
  for (const lang of langs) {
    const plan = await generateDailyTimeblockPlan({
      candidates,
      busyIntervalsMinutes: busy,
      workdayStartMinutes: 540,
      workdayEndMinutes: 1020,
      earliestStartMinutes: 540,
      lang,
      customApiKey: ''
    });

    assert.equal(plan.blocks.length, 3, `Expected all 3 tasks scheduled for ${lang}`);
    assert.ok(plan.summary.length > 0);

    // Pinned 60m task placed in 10:00-11:00 (600..660), avoiding 09:00-10:00 meeting!
    const pinnedBlock = plan.blocks.find((b) => b.taskId === 't-pinned')!;
    assert.equal(pinnedBlock.startMinutes, 600);
    assert.equal(pinnedBlock.durationMinutes, 60);

    // Verify 15m alignment and zero overlap with busy intervals or each other
    for (let i = 0; i < plan.blocks.length; i++) {
      const b = plan.blocks[i];
      assert.equal(b.startMinutes % 15, 0);
      const bEnd = b.startMinutes + b.durationMinutes;
      for (const [bs, be] of busy) {
        assert.equal(b.startMinutes < be && bs < bEnd, false, 'Must not overlap busy interval');
      }
      for (let j = i + 1; j < plan.blocks.length; j++) {
        const other = plan.blocks[j];
        const oEnd = other.startMinutes + other.durationMinutes;
        assert.equal(
          b.startMinutes < oEnd && other.startMinutes < bEnd,
          false,
          'Proposed blocks must not overlap each other'
        );
      }
    }
  }

  // Verify sanitizeProposedTimeblocks resolves overlapping raw LLM proposals cleanly
  const sanitized = sanitizeProposedTimeblocks(
    [
      { taskId: 'a', startMinutes: 545, durationMinutes: 45, reason: 'r1' },
      { taskId: 'b', startMinutes: 550, durationMinutes: 30, reason: 'r2' }
    ],
    [[540, 600]],
    540
  );
  assert.equal(sanitized.length, 2);
  assert.equal(sanitized[0].startMinutes, 600);
  assert.equal(sanitized[1].startMinutes, 645);

  // Also verify buildDeterministicDayTimeblockPlan directly with empty input
  const emptyPlan = buildDeterministicDayTimeblockPlan([], busy, 540, 1020, 540, 'en');
  assert.equal(emptyPlan.blocks.length, 0);

  // Verify localDb.applyBatchTaskTimeblocks + Undo restoration
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const undoEvents: UndoEventPayload[] = [];
  const unsub = db.subscribeUndo((ev) => undoEvents.push(ev));

  const t1 = await db.upsertTask({
    title: 'Morning Architecture',
    scheduleState: 'TODAY',
    scheduledStartMinutes: null,
    durationMinutes: 30
  });
  const t2 = await db.upsertTask({
    title: 'Afternoon QA',
    scheduleState: 'TODAY',
    scheduledStartMinutes: 840,
    durationMinutes: 45
  });

  const appliedCount = await db.applyBatchTaskTimeblocks([
    { taskId: t1.id, scheduledStartMinutes: 600, durationMinutes: 60 },
    { taskId: t2.id, scheduledStartMinutes: 720, durationMinutes: 45 }
  ]);
  assert.equal(appliedCount, 2);
  assert.equal(undoEvents.at(-1)?.kind, 'day_plan_applied');
  assert.equal(undoEvents.at(-1)?.count, 2);

  let snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.scheduledStartMinutes, 600);
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.durationMinutes, 60);
  assert.equal(snap.tasks.find((t) => t.id === t2.id)?.scheduledStartMinutes, 720);

  const undone = await db.performUndo();
  assert.equal(undone, true);
  snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.scheduledStartMinutes, null);
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.durationMinutes, 30);
  assert.equal(snap.tasks.find((t) => t.id === t2.id)?.scheduledStartMinutes, 840);
  assert.equal(snap.tasks.find((t) => t.id === t2.id)?.durationMinutes, 45);

  unsub();
});
