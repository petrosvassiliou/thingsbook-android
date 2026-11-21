import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import {
  addDaysLocal,
  startOfDayEpoch,
  type HeadingRecord,
  type ProjectRecord,
  type TaskRecord
} from '../src/domain/types.ts';
import {
  DRAG_MIME_MULTI_TASKS,
  DRAG_MIME_SINGLE_TASK,
  ROADMAP_MOUSE_DRAG_ACTIVATE_PX,
  ROADMAP_MOUSE_HOLD_MS,
  ROADMAP_TOUCH_HOLD_MS,
  ROADMAP_TOUCH_HOLD_SLOP_PX,
  addOneMonthLocal,
  buildProjectRoadmapLayout,
  extractDraggedTaskIds,
  generateRoadmapPdfBytes,
  isRoadmapPointerActivelyPressed,
  localDaysBetween,
  normalizeProjectViewMode,
  resolveRoadmapHoldDurationMs,
  shiftPhaseDatesByDays,
  shiftTaskDatesByDays,
  shouldActivateMouseRoadmapDrag,
  shouldCancelUnarmedRoadmapDrag,
  wrapTextLines
} from '../src/domain/v30Roadmap.ts';
import {
  DEFAULT_PREFS,
  ThingsLocalDb,
  type UndoEventPayload
} from '../src/data/localDb.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  normalizeBackupPayloadToEntities
} from '../src/data/backupCodec.ts';
import {
  headingFromFirestoreMap,
  headingToFirestoreMap,
  projectFromFirestoreMap,
  projectToFirestoreMap
} from '../src/data/syncUtils.ts';

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

function makeProject(overrides: Partial<ProjectRecord> & { id: string }): ProjectRecord {
  const now = 1_760_000_000_000;
  return {
    areaId: null,
    title: 'Q4 Launch',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false,
    ...overrides
  };
}

function makeHeading(
  overrides: Partial<HeadingRecord> & { id: string; projectId: string }
): HeadingRecord {
  const now = 1_760_000_000_000;
  return {
    title: overrides.id,
    startDate: null,
    endDate: null,
    sortOrder: 1000,
    isArchived: false,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false,
    ...overrides
  };
}

function makeTask(overrides: Partial<TaskRecord> & { id: string }): TaskRecord {
  const now = 1_760_000_000_000;
  return {
    title: overrides.id,
    notes: '',
    status: 'OPEN',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    projectId: null,
    headingId: null,
    areaId: null,
    tagIds: [],
    sortOrder: 1000,
    recurrenceRule: null,
    completedAt: null,
    scheduledStartMinutes: null,
    durationMinutes: null,
    isPinned: false,
    effortLevel: null,
    blockedByTaskIds: [],
    workspaceId: null,
    assigneeId: null,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false,
    ...overrides
  };
}

test('normalizeProjectViewMode resolves list, board, roadmap, and legacy gantt alias cleanly', () => {
  assert.equal(normalizeProjectViewMode(null), 'list');
  assert.equal(normalizeProjectViewMode(undefined), 'list');
  assert.equal(normalizeProjectViewMode(''), 'list');
  assert.equal(normalizeProjectViewMode('unknown'), 'list');
  assert.equal(normalizeProjectViewMode('list'), 'list');
  assert.equal(normalizeProjectViewMode('BOARD'), 'board');
  assert.equal(normalizeProjectViewMode('  roadmap '), 'roadmap');
  assert.equal(normalizeProjectViewMode('gantt'), 'roadmap');
  assert.equal(normalizeProjectViewMode('GANTT'), 'roadmap');
});

test('buildProjectRoadmapLayout resolves Phase & Task date spans, multi-day durations, unscheduled items, and active vs completed dependency edges', () => {
  const today = startOfDayEpoch(new Date(2026, 9, 15)); // Oct 15, 2026 local midnight
  const project = makeProject({ id: 'proj-1', title: 'Thingsbook 3.0' });

  const phaseExplicit = makeHeading({
    id: 'hd-explicit',
    projectId: project.id,
    title: 'Design Phase',
    startDate: today,
    endDate: addDaysLocal(today, 4),
    sortOrder: 1000
  });
  const phaseInferred = makeHeading({
    id: 'hd-inferred',
    projectId: project.id,
    title: 'Engineering Phase',
    startDate: null,
    endDate: null,
    sortOrder: 2000
  });
  const phaseUnscheduled = makeHeading({
    id: 'hd-unscheduled',
    projectId: project.id,
    title: 'Backlog Phase',
    startDate: null,
    endDate: null,
    sortOrder: 3000
  });

  // 1. Completed blocker task (whenDate..deadline span)
  const completedBlocker = makeTask({
    id: 'tk-done-blocker',
    projectId: project.id,
    headingId: phaseExplicit.id,
    title: 'Approve Specs',
    status: 'COMPLETED',
    whenDate: today,
    deadline: addDaysLocal(today, 1),
    sortOrder: 100
  });

  // 2. Open blocker task with multi-day duration (600 mins -> ceil(600/480) = 2 days)
  const openBlocker = makeTask({
    id: 'tk-open-blocker',
    projectId: project.id,
    headingId: phaseExplicit.id,
    title: 'UI Prototype',
    status: 'OPEN',
    whenDate: addDaysLocal(today, 2),
    deadline: null,
    durationMinutes: 600,
    sortOrder: 200
  });

  // 3. Target task with deadline-only multi-day duration (1000 mins -> ceil(1000/480) = 3 days)
  //    blocked by BOTH completedBlocker and openBlocker
  const targetTask = makeTask({
    id: 'tk-target',
    projectId: project.id,
    headingId: phaseInferred.id,
    title: 'Implement Roadmap',
    status: 'OPEN',
    whenDate: null,
    deadline: addDaysLocal(today, 10),
    durationMinutes: 1000, // 3 days -> [today+8 .. today+10]
    blockedByTaskIds: [completedBlocker.id, openBlocker.id],
    sortOrder: 300
  });

  // 4. Unscheduled task (no whenDate and no deadline)
  const unscheduledTask = makeTask({
    id: 'tk-unscheduled',
    projectId: project.id,
    headingId: phaseUnscheduled.id,
    title: 'Polish Docs',
    whenDate: null,
    deadline: null,
    sortOrder: 400
  });

  const layout = buildProjectRoadmapLayout(
    project,
    [phaseExplicit, phaseInferred, phaseUnscheduled],
    [completedBlocker, openBlocker, targetTask, unscheduledTask],
    today
  );

  // Window starts at today - 3 days and spans at least 21 days (here today-3 .. today+17 => 21 days)
  assert.equal(layout.windowStartEpoch, addDaysLocal(today, -3));
  assert.ok(layout.totalDays >= 21);
  assert.equal(layout.days.length, layout.totalDays);

  // Phase 1: explicit [today .. today+4]
  const lane1 = layout.lanes.find((l) => l.heading?.id === phaseExplicit.id)!;
  assert.equal(lane1.isInferred, false);
  assert.equal(lane1.startEpoch, today);
  assert.equal(lane1.endEpoch, addDaysLocal(today, 4));
  assert.equal(lane1.spanDays, 5);

  // Phase 2: inferred from child open task [today+8 .. today+10]
  const lane2 = layout.lanes.find((l) => l.heading?.id === phaseInferred.id)!;
  assert.equal(lane2.isInferred, true);
  assert.equal(lane2.startEpoch, addDaysLocal(today, 8));
  assert.equal(lane2.endEpoch, addDaysLocal(today, 10));
  assert.equal(lane2.spanDays, 3);

  // Phase 3: unscheduled lane preserved with unscheduled child task
  const lane3 = layout.lanes.find((l) => l.heading?.id === phaseUnscheduled.id)!;
  assert.equal(lane3.startEpoch, null);
  assert.equal(lane3.endEpoch, null);
  assert.deepEqual(
    lane3.unscheduledTasks.map((t) => t.id),
    ['tk-unscheduled']
  );

  // Task spans
  const plottedOpenBlocker = layout.plottedTasks.find(
    (p) => p.task.id === openBlocker.id
  )!;
  assert.equal(plottedOpenBlocker.startEpoch, addDaysLocal(today, 2));
  assert.equal(plottedOpenBlocker.endEpoch, addDaysLocal(today, 3));
  assert.equal(plottedOpenBlocker.spanDays, 2);

  const plottedTarget = layout.plottedTasks.find((p) => p.task.id === targetTask.id)!;
  assert.equal(plottedTarget.startEpoch, addDaysLocal(today, 8));
  assert.equal(plottedTarget.endEpoch, addDaysLocal(today, 10));
  assert.equal(plottedTarget.spanDays, 3);
  assert.equal(plottedTarget.isActivelyBlocked, true);

  // Unscheduled partitioning
  assert.deepEqual(
    layout.unscheduledTasks.map((t) => t.id),
    ['tk-unscheduled']
  );
  assert.deepEqual(
    layout.unscheduledPhases.map((h) => h.id),
    ['hd-unscheduled']
  );

  // Dependency edges: completed blocker isActivelyBlocking=false, open blocker isActivelyBlocking=true
  assert.deepEqual(layout.dependencyEdges, [
    {
      fromTaskId: 'tk-done-blocker',
      toTaskId: 'tk-target',
      isActivelyBlocking: false
    },
    {
      fromTaskId: 'tk-open-blocker',
      toTaskId: 'tk-target',
      isActivelyBlocking: true
    }
  ]);
});

test('shiftPhaseDatesByDays and shiftTaskDatesByDays perform DST-safe local day shifts and preserve THIS_EVENING', () => {
  const today = startOfDayEpoch(new Date(2026, 2, 28)); // Near spring DST boundary

  const hd = makeHeading({
    id: 'hd-1',
    projectId: 'p-1',
    startDate: today,
    endDate: addDaysLocal(today, 5)
  });
  const shiftedPhase = shiftPhaseDatesByDays(hd, 3);
  assert.equal(localDaysBetween(today, shiftedPhase.startDate!), 3);
  assert.equal(localDaysBetween(today, shiftedPhase.endDate!), 8);

  // Task shifting from tomorrow (-1 day -> lands on today)
  const upcomingEveningStyle = makeTask({
    id: 'tk-1',
    scheduleState: 'THIS_EVENING',
    whenDate: addDaysLocal(today, 1),
    deadline: addDaysLocal(today, 4)
  });
  const backToToday = shiftTaskDatesByDays(upcomingEveningStyle, -1, today);
  assert.equal(backToToday.whenDate, today);
  assert.equal(backToToday.deadline, addDaysLocal(today, 3));
  assert.equal(backToToday.scheduleState, 'THIS_EVENING');

  // Shifting Today task +2 days -> promotes scheduleState to UPCOMING
  const forwardTwo = shiftTaskDatesByDays(
    makeTask({ id: 'tk-2', scheduleState: 'TODAY', whenDate: today }),
    2,
    today
  );
  assert.equal(forwardTwo.whenDate, addDaysLocal(today, 2));
  assert.equal(forwardTwo.scheduleState, 'UPCOMING');

  // Shifting unscheduled task initializes whenDate = addDaysLocal(today, deltaDays)
  const fromUnscheduled = shiftTaskDatesByDays(
    makeTask({ id: 'tk-3', scheduleState: 'ANYTIME', whenDate: null, deadline: null }),
    4,
    today
  );
  assert.equal(fromUnscheduled.whenDate, addDaysLocal(today, 4));
  assert.equal(fromUnscheduled.deadline, null);
  assert.equal(fromUnscheduled.scheduleState, 'UPCOMING');
});

test('generateRoadmapPdfBytes emits valid standalone PDF 1.4 with %%EOF, streams, and escaped special characters', () => {
  const today = startOfDayEpoch(new Date(2026, 9, 15));
  const project = makeProject({
    id: 'proj-pdf',
    title: 'Alpha (v3.0) \\ Launch — Süd'
  });
  const hd = makeHeading({
    id: 'hd-pdf',
    projectId: project.id,
    title: 'Phase (1)',
    startDate: today,
    endDate: addDaysLocal(today, 3)
  });
  const t1 = makeTask({
    id: 't-1',
    projectId: project.id,
    headingId: hd.id,
    title: 'Spec (Draft)',
    whenDate: today
  });
  const t2 = makeTask({
    id: 't-2',
    projectId: project.id,
    headingId: hd.id,
    title: 'Ship \\ Release',
    whenDate: addDaysLocal(today, 2),
    blockedByTaskIds: [t1.id]
  });

  const layout = buildProjectRoadmapLayout(project, [hd], [t1, t2], today);
  const pdfBytes = generateRoadmapPdfBytes(layout, project.title, {
    ungroupedTitle: 'General',
    unscheduledLabel: 'Unscheduled'
  });

  assert.ok(pdfBytes instanceof Uint8Array);
  assert.ok(pdfBytes.byteLength > 600);

  const ascii = new TextDecoder('ascii').decode(pdfBytes);
  assert.ok(ascii.startsWith('%PDF-1.4\n'));
  assert.ok(ascii.trimEnd().endsWith('%%EOF'));
  assert.ok(ascii.includes('/Type /Catalog'));
  assert.ok(ascii.includes('/Type /Page'));
  assert.ok(ascii.includes('stream\n'));
  assert.ok(ascii.includes('\nendstream'));
  // Escaped parentheses and backslashes in title
  assert.ok(ascii.includes('Alpha \\(v3.0\\) \\\\ Launch'));
  assert.ok(ascii.includes('Spec \\(Draft\\)'));
});

test('extractDraggedTaskIds orders multi-task payloads and expands multi-selection in visual order', () => {
  const visualOrder = ['t-1', 't-2', 't-3', 't-4'];

  const multiPayload = extractDraggedTaskIds(
    {
      types: [DRAG_MIME_MULTI_TASKS, DRAG_MIME_SINGLE_TASK],
      getData: (fmt) =>
        fmt === DRAG_MIME_MULTI_TASKS ? JSON.stringify(['t-4', 't-2']) : 't-4'
    },
    [],
    visualOrder
  );
  assert.deepEqual(multiPayload, ['t-2', 't-4']);

  const singleExpandingSelection = extractDraggedTaskIds(
    {
      types: [DRAG_MIME_SINGLE_TASK],
      getData: (fmt) => (fmt === DRAG_MIME_SINGLE_TASK ? 't-3' : '')
    },
    ['t-4', 't-1', 't-3'],
    visualOrder
  );
  assert.deepEqual(singleExpandingSelection, ['t-1', 't-3', 't-4']);
});

test('ThingsLocalDb multi-task cross-container moves preserve relative sortOrder, support Undo, updateHeadingDates, and persist viewMode across .tbbackup & Firestore sync', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const undoEvents: UndoEventPayload[] = [];
  db.subscribeUndo((ev) => undoEvents.push(ev));

  const area = (await db.upsertArea({ title: 'Product' }))!;
  const project = await db.upsertProject({
    title: 'Milestone 3',
    areaId: area.id,
    viewMode: 'board'
  });
  assert.equal(project.viewMode, 'board');

  await db.setProjectViewMode(project.id, 'roadmap');
  assert.equal(
    db.getSnapshot().projects.find((p) => p.id === project.id)?.viewMode,
    'roadmap'
  );

  const phaseA = await db.upsertHeading({
    projectId: project.id,
    title: 'Phase A',
    sortOrder: 1000
  });
  const phaseB = await db.upsertHeading({
    projectId: project.id,
    title: 'Phase B',
    sortOrder: 2000
  });

  const startMs = startOfDayEpoch(new Date(2026, 9, 20));
  const endMs = addDaysLocal(startMs, 7);
  await db.updateHeadingDates(phaseA.id, startMs, endMs);
  const updatedPhaseA = db.getSnapshot().headings.find((h) => h.id === phaseA.id)!;
  assert.equal(updatedPhaseA.startDate, startMs);
  assert.equal(updatedPhaseA.endDate, endMs);

  // Create 3 tasks in Inbox and batch-move them into Phase A in exact relative order [t3, t1, t2]
  const t1 = await db.upsertTask({ title: 'One', scheduleState: 'INBOX', sortOrder: 1000 });
  const t2 = await db.upsertTask({ title: 'Two', scheduleState: 'INBOX', sortOrder: 2000 });
  const t3 = await db.upsertTask({ title: 'Three', scheduleState: 'INBOX', sortOrder: 3000 });

  await db.batchMoveOrReorderTasksInContainer([t3.id, t1.id, t2.id], {
    projectId: project.id,
    headingId: phaseA.id,
    areaId: null
  });

  let snap = db.getSnapshot();
  const moved3 = snap.tasks.find((t) => t.id === t3.id)!;
  const moved1 = snap.tasks.find((t) => t.id === t1.id)!;
  const moved2 = snap.tasks.find((t) => t.id === t2.id)!;
  assert.equal(moved3.projectId, project.id);
  assert.equal(moved3.headingId, phaseA.id);
  assert.equal(moved3.areaId, null);
  assert.equal(moved3.scheduleState, 'ANYTIME'); // Inbox -> Project auto-transitions to ANYTIME
  assert.ok(moved3.sortOrder < moved1.sortOrder);
  assert.ok(moved1.sortOrder < moved2.sortOrder);
  assert.equal(undoEvents[undoEvents.length - 1]?.kind, 'tasks_updated');
  assert.equal(undoEvents[undoEvents.length - 1]?.count, 3);

  // Perform Undo -> restores all 3 tasks back to INBOX with original sortOrders!
  const undone = await db.performUndo();
  assert.equal(undone, true);
  snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.projectId, null);
  assert.equal(snap.tasks.find((t) => t.id === t1.id)?.scheduleState, 'INBOX');

  // Move [t1, t2] into Phase B between two anchor sortOrders (500 and 600)
  await db.batchMoveOrReorderTasksInContainer([t1.id, t2.id], {
    projectId: project.id,
    headingId: phaseB.id,
    areaId: null,
    afterSortOrder: 500,
    beforeSortOrder: 600
  });
  snap = db.getSnapshot();
  const b1 = snap.tasks.find((t) => t.id === t1.id)!;
  const b2 = snap.tasks.find((t) => t.id === t2.id)!;
  assert.ok(b1.sortOrder > 500 && b1.sortOrder < b2.sortOrder && b2.sortOrder < 600);

  // Encrypted .tbbackup roundtrip preserves viewMode='roadmap' and heading startDate/endDate
  const backupPayload = buildBackupPayloadFromEntities({
    areas: snap.areas,
    projects: snap.projects,
    headings: snap.headings,
    tasks: snap.tasks,
    checklistItems: snap.checklistItems,
    tags: snap.tags,
    attachments: snap.attachments,
    projectTemplates: snap.projectTemplates,
    preferences: {},
    appVersionName: '3.0.0-alpha.13'
  });
  const encryptedBytes = await encodeThingsBackup(backupPayload, 'milestone3-secret');
  const decodedPayload = await decodeThingsBackup(encryptedBytes, 'milestone3-secret');
  const normalized = normalizeBackupPayloadToEntities(decodedPayload, DEFAULT_PREFS);

  const restoredProj = normalized.projects.find((p) => p.id === project.id)!;
  assert.equal(restoredProj.viewMode, 'roadmap');
  const restoredHeading = normalized.headings.find((h) => h.id === phaseA.id)!;
  assert.equal(restoredHeading.startDate, startMs);
  assert.equal(restoredHeading.endDate, endMs);

  // Firestore DTO roundtrip (including legacy 'gantt' -> 'roadmap')
  const projMap = projectToFirestoreMap(restoredProj);
  assert.equal(projMap.viewMode, 'roadmap');
  const roundtripProj = projectFromFirestoreMap(project.id, {
    ...projMap,
    viewMode: 'gantt'
  });
  assert.equal(roundtripProj.viewMode, 'roadmap');

  const hdMap = headingToFirestoreMap(restoredHeading);
  assert.equal(hdMap.startDate, startMs);
  assert.equal(hdMap.endDate, endMs);
  const roundtripHd = headingFromFirestoreMap(phaseA.id, hdMap);
  assert.equal(roundtripHd.startDate, startMs);
  assert.equal(roundtripHd.endDate, endMs);

  // Also verify applyRemoteDeltas applies remote viewMode & heading dates cleanly
  await db.applyRemoteDeltas({
    areas: [],
    projects: [roundtripProj],
    headings: [roundtripHd],
    tasks: [],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: []
  });
  assert.equal(
    db.getSnapshot().projects.find((p) => p.id === project.id)?.viewMode,
    'roadmap'
  );
});

test('v3.0.0-alpha.13 buildProjectRoadmapLayout caps horizon strictly to <= 1 month after last task due/end date', () => {
  // Month-end DST-safe clamping checks
  assert.equal(
    addOneMonthLocal(startOfDayEpoch(new Date(2026, 0, 31))),
    startOfDayEpoch(new Date(2026, 1, 28))
  );
  assert.equal(
    addOneMonthLocal(startOfDayEpoch(new Date(2028, 0, 31))),
    startOfDayEpoch(new Date(2028, 1, 29))
  );
  assert.equal(
    addOneMonthLocal(startOfDayEpoch(new Date(2026, 11, 18))),
    startOfDayEpoch(new Date(2027, 0, 18))
  );

  // Spec scenario: Today = 2026-11-20, last task due date = 2026-12-18,
  // Phase 4 ends 2027-02-15, project.deadline = 2027-04-30 -> windowEndEpoch strictly 2027-01-18!
  const today = startOfDayEpoch(new Date(2026, 10, 20));
  const lastTaskDue = startOfDayEpoch(new Date(2026, 11, 18));
  const phase4End = startOfDayEpoch(new Date(2027, 1, 15));
  const farProjectDeadline = startOfDayEpoch(new Date(2027, 3, 30));

  const project = makeProject({
    id: 'proj-horizon-cap',
    title: 'Enterprise Rollout',
    deadline: farProjectDeadline
  });

  const phase1 = makeHeading({
    id: 'hd-1',
    projectId: project.id,
    title: 'Technische Umsetzung und Systemintegration',
    startDate: today,
    endDate: lastTaskDue,
    sortOrder: 1000
  });
  const phase4 = makeHeading({
    id: 'hd-4',
    projectId: project.id,
    title: 'Betriebsübergabe und Langzeitbegleitung',
    startDate: startOfDayEpoch(new Date(2026, 11, 10)),
    endDate: phase4End,
    sortOrder: 4000
  });

  const lastTask = makeTask({
    id: 'tk-last',
    projectId: project.id,
    headingId: phase1.id,
    title: 'Final Cutover Verification',
    whenDate: startOfDayEpoch(new Date(2026, 11, 15)),
    deadline: lastTaskDue
  });

  const layout = buildProjectRoadmapLayout(
    project,
    [phase1, phase4],
    [lastTask],
    today
  );

  const expectedCap = startOfDayEpoch(new Date(2027, 0, 18));
  assert.equal(layout.windowEndEpoch, expectedCap);
  assert.equal(layout.days[layout.days.length - 1].epoch, expectedCap);

  // Phase 4 extends beyond windowEndEpoch, so its lane indices are clamped strictly within [0 .. totalDays - 1]
  const lane4 = layout.lanes.find((l) => l.heading?.id === phase4.id)!;
  assert.ok(lane4.endDayIndex != null && lane4.endDayIndex === layout.totalDays - 1);

  // Zero plotted tasks scenario: caps at <= today + 1 month even when phase/project dates are months out
  const zeroTasksLayout = buildProjectRoadmapLayout(
    project,
    [phase4],
    [],
    today
  );
  assert.equal(
    zeroTasksLayout.windowEndEpoch,
    startOfDayEpoch(new Date(2026, 11, 20))
  );
});

test('v3.0.0-alpha.13 generateRoadmapPdfBytes supports A4 portrait vs landscape, phase filtering, custom timeline range, full phase titles, and pagination', async () => {
  const today = startOfDayEpoch(new Date(2026, 10, 20));
  const project = makeProject({
    id: 'proj-pdf-v13',
    title: 'Q1 Platform Migration'
  });

  const phaseKeep = makeHeading({
    id: 'hd-keep',
    projectId: project.id,
    title: 'Technische Umsetzung Architektur und Infrastruktur Validierung',
    startDate: today,
    endDate: addDaysLocal(today, 1), // Narrow 2-day phase bar -> unclipped title beside bar!
    sortOrder: 1000
  });
  const phaseOmit = makeHeading({
    id: 'hd-omit',
    projectId: project.id,
    title: 'Excluded Secret Phase',
    startDate: today,
    endDate: addDaysLocal(today, 5),
    sortOrder: 2000
  });

  const inRangeTask = makeTask({
    id: 'tk-in-range',
    projectId: project.id,
    headingId: phaseKeep.id,
    title: 'Migrate Core Database',
    whenDate: addDaysLocal(today, 2),
    deadline: addDaysLocal(today, 4),
    sortOrder: 100
  });
  const outOfRangeTask = makeTask({
    id: 'tk-out-of-range',
    projectId: project.id,
    headingId: phaseKeep.id,
    title: 'Late December Audit',
    whenDate: addDaysLocal(today, 35),
    deadline: addDaysLocal(today, 37),
    sortOrder: 200
  });
  const omittedPhaseTask = makeTask({
    id: 'tk-omitted-phase',
    projectId: project.id,
    headingId: phaseOmit.id,
    title: 'Hidden Phase Task',
    whenDate: addDaysLocal(today, 1),
    sortOrder: 300
  });

  const layout = buildProjectRoadmapLayout(
    project,
    [phaseKeep, phaseOmit],
    [inRangeTask, outOfRangeTask, omittedPhaseTask],
    today
  );

  // 1. Landscape A4 (/MediaBox [0 0 842 595])
  const landscapeBytes = generateRoadmapPdfBytes(
    layout,
    project.title,
    { ungroupedTitle: 'General' },
    { orientation: 'landscape' }
  );
  const landscapeAscii = new TextDecoder('ascii').decode(landscapeBytes);
  assert.ok(landscapeAscii.includes('/MediaBox [0 0 842 595]'));

  // 2. Portrait A4 (/MediaBox [0 0 595 842]) + Phase filtering + Custom 45-day range
  //    Wait: custom 14-day range [today .. today+13] excludes outOfRangeTask (at today+35)
  const portraitFilteredBytes = generateRoadmapPdfBytes(
    layout,
    project.title,
    { ungroupedTitle: 'General' },
    {
      orientation: 'portrait',
      selectedLaneIds: [phaseKeep.id],
      startEpoch: today,
      endEpoch: addDaysLocal(today, 13)
    }
  );
  const portraitAscii = new TextDecoder('ascii').decode(portraitFilteredBytes);
  assert.ok(portraitAscii.includes('/MediaBox [0 0 595 842]'));
  assert.ok(portraitAscii.includes('Migrate Core Database'));
  assert.ok(!portraitAscii.includes('Excluded Secret Phase'));
  assert.ok(!portraitAscii.includes('Hidden Phase Task'));
  assert.ok(!portraitAscii.includes('Late December Audit'));
  // Full Phase title wrapped across lines & rendered without ellipsis on narrow bar
  const wrappedCheck = wrapTextLines(phaseKeep.title, 30);
  assert.ok(wrappedCheck.length >= 2);
  for (const part of wrappedCheck) {
    assert.ok(portraitAscii.includes(part));
  }
  assert.ok(portraitAscii.includes(phaseKeep.title));

  // 3. Multi-page pagination on Landscape when tasks exceed 1 A4 page + 45-day Portrait tick spacing
  const manyTasks: TaskRecord[] = [];
  for (let i = 0; i < 28; i++) {
    manyTasks.push(
      makeTask({
        id: `tk-bulk-${i}`,
        projectId: project.id,
        headingId: phaseKeep.id,
        title: `Deliverable Step ${i + 1}`,
        whenDate: addDaysLocal(today, i % 20),
        sortOrder: 1000 + i * 10
      })
    );
  }
  const largeLayout = buildProjectRoadmapLayout(project, [phaseKeep], manyTasks, today);
  const multiPageLandscape = new TextDecoder('ascii').decode(
    generateRoadmapPdfBytes(
      largeLayout,
      project.title,
      { ungroupedTitle: 'General' },
      {
        orientation: 'landscape',
        startEpoch: today,
        endEpoch: addDaysLocal(today, 44)
      }
    )
  );
  assert.ok(multiPageLandscape.includes('/Count 2'));
  assert.ok(multiPageLandscape.includes('Page 1 / 2'));
  assert.ok(multiPageLandscape.includes('Page 2 / 2'));

  // 4. Verify all 11 new PDF export i18n keys across all 5 languages
  const { TRANSLATIONS } = await import('../src/i18n/translations.ts');
  const pdfKeys = [
    'pdfExportDialogTitle',
    'pdfPhasesLabel',
    'pdfSelectAllPhases',
    'pdfTimelineLabel',
    'pdfTimelineAuto',
    'pdfTimelineFromToday',
    'pdfTimelineNext30d',
    'pdfTimelineCustom',
    'pdfOrientationLabel',
    'pdfOrientationLandscape',
    'pdfOrientationPortrait'
  ] as const;
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    for (const key of pdfKeys) {
      assert.equal(typeof TRANSLATIONS[lang][key], 'string');
      assert.ok(TRANSLATIONS[lang][key].trim().length > 0, `Missing ${lang}.${key}`);
    }
  }
});

test('v3.0.0-beta.3 Roadmap hold-to-drag guard requires stationary hold and cancels unarmed scroll/swipe motion', () => {
  assert.equal(resolveRoadmapHoldDurationMs('mouse'), ROADMAP_MOUSE_HOLD_MS);
  assert.equal(resolveRoadmapHoldDurationMs('touch'), ROADMAP_TOUCH_HOLD_MS);
  assert.equal(resolveRoadmapHoldDurationMs('pen'), ROADMAP_TOUCH_HOLD_MS);
  assert.ok(ROADMAP_TOUCH_HOLD_MS >= ROADMAP_MOUSE_HOLD_MS);
  assert.ok(ROADMAP_MOUSE_HOLD_MS >= 200);

  // Micro jitter (<= 6px) while holding finger/mouse stationary does NOT cancel hold
  assert.equal(shouldCancelUnarmedRoadmapDrag(100, 200, 103, 204), false);
  assert.equal(shouldCancelUnarmedRoadmapDrag(100, 200, 106, 200), false);

  // Horizontal, vertical, or diagonal scroll swipe (> 6px) before hold completes cancels immediately
  assert.equal(shouldCancelUnarmedRoadmapDrag(100, 200, 112, 200), true);
  assert.equal(shouldCancelUnarmedRoadmapDrag(100, 200, 100, 214), true);
  assert.equal(shouldCancelUnarmedRoadmapDrag(100, 200, 106, 206), true);
});

test('v3.0.0-beta.16 Roadmap timeline drag requires active mouse press or finger contact and ignores hover', () => {
  // Pure mouse/pen/touch hover (buttons === 0) is NEVER treated as actively pressed
  assert.equal(isRoadmapPointerActivelyPressed('mouse', 0), false);
  assert.equal(isRoadmapPointerActivelyPressed('mouse', 2), false); // right button only
  assert.equal(isRoadmapPointerActivelyPressed('pen', 0), false);
  assert.equal(isRoadmapPointerActivelyPressed('touch', 0), false);

  // Active primary mouse press or finger/stylus contact returns true
  assert.equal(isRoadmapPointerActivelyPressed('mouse', 1), true);
  assert.equal(isRoadmapPointerActivelyPressed('mouse', 3), true);
  assert.equal(isRoadmapPointerActivelyPressed('pen', 1), true);
  assert.equal(isRoadmapPointerActivelyPressed('touch', 1), true);

  // Hovering mouse motion (buttons === 0) NEVER activates drag regardless of distance
  assert.equal(shouldActivateMouseRoadmapDrag(100, 200, 180, 200, 0), false);
  // Tiny click jitter (< 4px) while pressed does NOT prematurely activate drag
  assert.equal(shouldActivateMouseRoadmapDrag(100, 200, 102, 201, 1), false);
  // Pressing primary mouse button and dragging >= 4px activates smooth timeline drag immediately
  assert.equal(
    shouldActivateMouseRoadmapDrag(100, 200, 100 + ROADMAP_MOUSE_DRAG_ACTIVATE_PX, 200, 1),
    true
  );

  // Touch hold jitter tolerance allows natural capacitive pad settle (<= 10px) while rejecting scroll swipes
  assert.equal(
    shouldCancelUnarmedRoadmapDrag(100, 200, 107, 205, ROADMAP_TOUCH_HOLD_SLOP_PX),
    false
  );
  assert.equal(
    shouldCancelUnarmedRoadmapDrag(100, 200, 122, 200, ROADMAP_TOUCH_HOLD_SLOP_PX),
    true
  );
});


