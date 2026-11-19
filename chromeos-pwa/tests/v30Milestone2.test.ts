import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import {
  computeTagUsageCounts,
  computeTodayCapacitySummary,
  filterTasksForPerspective,
  isTaskActivelyBlocked,
  sortTagsForMode,
  sortTasksForView,
  wouldCreateDependencyCycle
} from '../src/domain/v2Utils.ts';
import {
  DEFAULT_PREFS,
  ThingsLocalDb
} from '../src/data/localDb.ts';
import {
  headingFromFirestoreMap,
  headingToFirestoreMap,
  projectFromFirestoreMap,
  projectToFirestoreMap,
  taskFromFirestoreMap,
  taskToFirestoreMap
} from '../src/data/syncUtils.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  normalizeBackupPayloadToEntities
} from '../src/data/backupCodec.ts';
import type {
  AppPreferences,
  CalendarEventItem,
  ProjectRecord,
  SavedPerspective,
  TagRecord,
  TaskRecord
} from '../src/domain/types.ts';

type StoreMap = Map<string, unknown>;

function createInMemoryIdb(existingStores?: Map<string, StoreMap>): {
  opener: () => Promise<IDBPDatabase>;
  stores: Map<string, StoreMap>;
} {
  const stores =
    existingStores ??
    new Map<string, StoreMap>([
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

function makeTask(overrides: Partial<TaskRecord> & { id: string }): TaskRecord {
  const now = 1_760_000_000_000;
  return {
    title: overrides.id,
    notes: '',
    status: 'OPEN',
    scheduleState: 'TODAY',
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

test('wouldCreateDependencyCycle detects self-cycles, 2-node cycles, and multi-hop transitive cycles while allowing DAGs', () => {
  const graph = new Map<string, readonly string[]>([
    ['task-b', ['task-c']],
    ['task-c', ['task-d']],
    ['task-d', []]
  ]);

  assert.equal(wouldCreateDependencyCycle('task-a', 'task-a', graph), true);
  assert.equal(wouldCreateDependencyCycle('task-b', 'task-c', graph), false);
  assert.equal(wouldCreateDependencyCycle('task-d', 'task-b', graph), true);
  assert.equal(wouldCreateDependencyCycle('task-a', 'task-b', graph), false);
  assert.equal(wouldCreateDependencyCycle('task-a', 'task-d', graph), false);

  assert.equal(
    wouldCreateDependencyCycle('x', 'y', { y: ['z'], z: ['x'] }),
    true
  );
});

test('isTaskActivelyBlocked returns true only when at least one distinct blocker ID is currently open', () => {
  const openSet = new Set(['blocker-open']);
  assert.equal(
    isTaskActivelyBlocked(
      makeTask({ id: 't1', blockedByTaskIds: ['blocker-done', 'blocker-open'] }),
      openSet
    ),
    true
  );
  assert.equal(
    isTaskActivelyBlocked(
      makeTask({ id: 't2', blockedByTaskIds: ['blocker-done'] }),
      openSet
    ),
    false
  );
  assert.equal(
    isTaskActivelyBlocked(
      makeTask({ id: 'blocker-open', blockedByTaskIds: ['blocker-open'] }),
      openSet
    ),
    false
  );
});

test('sortTasksForView keeps pinned tasks first across manual, deadline, duration, effort, and alpha sort modes', () => {
  const tasks: TaskRecord[] = [
    makeTask({
      id: 'c',
      title: 'Charlie',
      sortOrder: 1000,
      deadline: 3000,
      durationMinutes: 120,
      effortLevel: 'DEEP_FOCUS'
    }),
    makeTask({
      id: 'a',
      title: 'Alpha',
      sortOrder: 2000,
      deadline: 1000,
      durationMinutes: 15,
      effortLevel: 'QUICK_WIN'
    }),
    makeTask({
      id: 'b-pinned',
      title: 'Bravo Pinned',
      sortOrder: 3000,
      deadline: null,
      durationMinutes: null,
      effortLevel: null,
      isPinned: true
    }),
    makeTask({
      id: 'd',
      title: 'Delta',
      sortOrder: 1500,
      deadline: 2000,
      durationMinutes: 45,
      effortLevel: 'MEDIUM'
    })
  ];

  assert.deepEqual(
    sortTasksForView(tasks, 'manual').map((t) => t.id),
    ['b-pinned', 'c', 'd', 'a']
  );
  assert.deepEqual(
    sortTasksForView(tasks, 'deadline').map((t) => t.id),
    ['b-pinned', 'a', 'd', 'c']
  );
  assert.deepEqual(
    sortTasksForView(tasks, 'duration').map((t) => t.id),
    ['b-pinned', 'a', 'd', 'c']
  );
  assert.deepEqual(
    sortTasksForView(tasks, 'effort').map((t) => t.id),
    ['b-pinned', 'a', 'd', 'c']
  );
  assert.deepEqual(
    sortTasksForView(tasks, 'alpha').map((t) => t.id),
    ['b-pinned', 'a', 'c', 'd']
  );
});

test('computeTagUsageCounts and sortTagsForMode support alpha, most_used, and manual ordering', () => {
  const tags: TagRecord[] = [
    {
      id: 'tg-z',
      title: 'Zeta',
      parentTagId: null,
      shortcut: null,
      sortOrder: 2000,
      createdAt: 1,
      updatedAt: 1,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tg-a',
      title: 'Alpha',
      parentTagId: null,
      shortcut: null,
      sortOrder: 3000,
      createdAt: 1,
      updatedAt: 1,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tg-m',
      title: 'Middle',
      parentTagId: null,
      shortcut: null,
      sortOrder: 1000,
      createdAt: 1,
      updatedAt: 1,
      isDeleted: false,
      isDirty: false
    }
  ];

  const tasks: TaskRecord[] = [
    makeTask({ id: 't1', tagIds: ['tg-z', 'tg-m'] }),
    makeTask({ id: 't2', tagIds: ['tg-z'] }),
    makeTask({ id: 't3', tagIds: ['tg-a'], isDeleted: true })
  ];
  const projects: ProjectRecord[] = [
    {
      id: 'p1',
      title: 'P1',
      notes: '',
      status: 'ACTIVE',
      scheduleState: 'ANYTIME',
      whenDate: null,
      areaId: null,
      deadline: null,
      tagIds: ['tg-z'],
      sortOrder: 1000,
      completedAt: null,
      createdAt: 1,
      updatedAt: 1,
      isDeleted: false,
      isDirty: false
    }
  ];

  const counts = computeTagUsageCounts(tasks, projects);
  assert.equal(counts.get('tg-z'), 3);
  assert.equal(counts.get('tg-m'), 1);
  assert.equal(counts.get('tg-a') ?? 0, 0);

  assert.deepEqual(
    sortTagsForMode(tags, 'alpha', counts).map((t) => t.id),
    ['tg-a', 'tg-m', 'tg-z']
  );
  assert.deepEqual(
    sortTagsForMode(tags, 'most_used', counts).map((t) => t.id),
    ['tg-z', 'tg-m', 'tg-a']
  );
  assert.deepEqual(
    sortTagsForMode(tags, 'manual', counts).map((t) => t.id),
    ['tg-m', 'tg-z', 'tg-a']
  );
});

test('filterTasksForPerspective evaluates categories, effort, maxDuration, dueWindow, inherited project areaId, and actionableOnly', () => {
  const dayMs = 86_400_000;
  const todayStart = 1_760_054_400_000;
  const projectsMap = new Map<string, ProjectRecord>([
    [
      'proj-work',
      {
        id: 'proj-work',
        title: 'Launch',
        notes: '',
        status: 'ACTIVE',
        scheduleState: 'ANYTIME',
        whenDate: null,
        areaId: 'area-work',
        deadline: null,
        tagIds: [],
        sortOrder: 1000,
        completedAt: null,
        createdAt: todayStart,
        updatedAt: todayStart,
        isDeleted: false,
        isDirty: false
      }
    ]
  ]);

  const tasks: TaskRecord[] = [
    makeTask({
      id: 'match-direct',
      areaId: 'area-work',
      tagIds: ['cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 15,
      scheduleState: 'TODAY',
      whenDate: todayStart
    }),
    makeTask({
      id: 'match-via-project',
      projectId: 'proj-work',
      tagIds: ['cat-deep', 'cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 30,
      scheduleState: 'UPCOMING',
      whenDate: todayStart + 3 * dayMs
    }),
    makeTask({
      id: 'blocked-task',
      areaId: 'area-work',
      tagIds: ['cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 10,
      scheduleState: 'TODAY',
      blockedByTaskIds: ['match-direct']
    }),
    makeTask({
      id: 'someday-task',
      areaId: 'area-work',
      tagIds: ['cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 15,
      scheduleState: 'SOMEDAY'
    }),
    makeTask({
      id: 'overdue-task',
      areaId: 'area-work',
      tagIds: ['cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 20,
      scheduleState: 'ANYTIME',
      deadline: todayStart - dayMs
    }),
    makeTask({
      id: 'too-long-task',
      areaId: 'area-work',
      tagIds: ['cat-errand'],
      effortLevel: 'QUICK_WIN',
      durationMinutes: 60,
      scheduleState: 'TODAY'
    })
  ];

  const openSet = new Set(tasks.map((t) => t.id));

  const next7Persp: SavedPerspective = {
    id: 'persp-1',
    title: 'Work Quick Wins',
    categoryIds: ['cat-errand'],
    effortLevel: 'QUICK_WIN',
    maxDurationMinutes: 30,
    dueWindow: 'NEXT_7_DAYS',
    areaId: 'area-work',
    actionableOnly: true,
    sortOrder: 1000
  };

  assert.deepEqual(
    filterTasksForPerspective(tasks, next7Persp, openSet, todayStart, projectsMap).map(
      (t) => t.id
    ),
    ['match-direct', 'match-via-project', 'overdue-task']
  );

  const overdueOnly: SavedPerspective = {
    ...next7Persp,
    dueWindow: 'OVERDUE'
  };
  assert.deepEqual(
    filterTasksForPerspective(tasks, overdueOnly, openSet, todayStart, projectsMap).map(
      (t) => t.id
    ),
    ['overdue-task']
  );

  const includeAll: SavedPerspective = {
    ...next7Persp,
    dueWindow: 'ANY',
    actionableOnly: false
  };
  assert.deepEqual(
    filterTasksForPerspective(tasks, includeAll, openSet, todayStart, projectsMap).map(
      (t) => t.id
    ),
    ['match-direct', 'match-via-project', 'blocked-task', 'someday-task', 'overdue-task']
  );
});

test('computeTodayCapacitySummary sums standalone + timeblocked durations and subtracts overlapping calendar meetings from 8h capacity', () => {
  const dayStart = 1_760_054_400_000;
  const dayEnd = dayStart + 86_400_000 - 1;

  const tasks: TaskRecord[] = [
    makeTask({ id: 'standalone', durationMinutes: 45, scheduledStartMinutes: null }),
    makeTask({ id: 'timeblocked', durationMinutes: 60, scheduledStartMinutes: 9 * 60 }),
    makeTask({ id: 'unestimated', durationMinutes: null }),
    makeTask({ id: 'completed', status: 'COMPLETED', durationMinutes: 90 })
  ];

  const events: CalendarEventItem[] = [
    {
      eventId: 'ev-1',
      calendarId: 'primary',
      calendarName: 'Work',
      calendarColorCss: '#248BFD',
      title: 'Sync',
      startMillis: dayStart + 10 * 3_600_000,
      endMillis: dayStart + 11.5 * 3_600_000, // 90m
      isAllDay: false,
      location: null,
      htmlLink: null
    },
    {
      eventId: 'ev-all-day',
      calendarId: 'primary',
      calendarName: 'Work',
      calendarColorCss: '#248BFD',
      title: 'Holiday Banner',
      startMillis: dayStart,
      endMillis: dayEnd,
      isAllDay: true,
      location: null,
      htmlLink: null
    }
  ];

  const summary = computeTodayCapacitySummary(tasks, events, dayStart, dayEnd);
  assert.equal(summary.plannedMinutes, 105);
  assert.equal(summary.freeMinutes, 285);
});

test('ThingsLocalDb mergeTags, moveTagOrder, batchDuplicateTasks, dependency cycle guard, and recurring reset work end-to-end', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const tagErrand = await db.upsertTag({ title: 'Errand', shortcut: 'e' });
  const tagHome = await db.upsertTag({ title: 'Home', shortcut: 'h' });
  assert.equal(tagErrand.sortOrder, 1000);
  assert.equal(tagHome.sortOrder, 2000);

  // Clear shortcut explicitly with null
  const clearedShortcut = await db.upsertTag({ id: tagErrand.id, shortcut: null });
  assert.equal(clearedShortcut.shortcut, null);

  // Reorder Home above Errand
  await db.moveTagOrder(tagHome.id, 'up');
  const reorderedTags = db.getSnapshot().tags.filter((tg) => !tg.isDeleted);
  assert.equal(
    reorderedTags.find((tg) => tg.id === tagHome.id)!.sortOrder <
      reorderedTags.find((tg) => tg.id === tagErrand.id)!.sortOrder,
    true
  );

  // Create tasks with checklist, pin, effort, and dependencies
  const t1 = await db.upsertTask({
    title: 'First',
    tagIds: [tagErrand.id],
    isPinned: true,
    effortLevel: 'QUICK_WIN',
    durationMinutes: 15,
    sortOrder: 1000
  });
  await db.addChecklistItems(t1.id, ['Step A', 'Step B']);

  const t2 = await db.upsertTask({
    title: 'Second',
    tagIds: [tagErrand.id, tagHome.id],
    effortLevel: 'DEEP_FOCUS',
    blockedByTaskIds: [t1.id],
    sortOrder: 2000
  });

  // Attempting to make t1 depend on t2 would create a cycle and must be stripped!
  const t1CycleAttempt = await db.upsertTask({
    id: t1.id,
    blockedByTaskIds: [t2.id]
  });
  assert.deepEqual(t1CycleAttempt.blockedByTaskIds, []);

  // Merge Errand into Home
  const mergedOk = await db.mergeTags(tagErrand.id, tagHome.id);
  assert.equal(mergedOk, true);
  const snapAfterMerge = db.getSnapshot();
  assert.equal(
    snapAfterMerge.tags.find((tg) => tg.id === tagErrand.id)?.isDeleted,
    true
  );
  assert.deepEqual(
    snapAfterMerge.tasks.find((tk) => tk.id === t1.id)?.tagIds,
    [tagHome.id]
  );
  assert.deepEqual(
    snapAfterMerge.tasks.find((tk) => tk.id === t2.id)?.tagIds,
    [tagHome.id]
  );

  // Batch duplicate [t1, t2] preserves order, checklist items, isPinned, and effortLevel
  const dupIds = await db.batchDuplicateTasks([t1.id, t2.id], 'Copy');
  assert.equal(dupIds.length, 2);
  const snapAfterDup = db.getSnapshot();
  const dup1 = snapAfterDup.tasks.find((tk) => tk.id === dupIds[0])!;
  const dup2 = snapAfterDup.tasks.find((tk) => tk.id === dupIds[1])!;
  assert.equal(dup1.title, 'First (Copy)');
  assert.equal(dup1.isPinned, true);
  assert.equal(dup1.effortLevel, 'QUICK_WIN');
  assert.equal(dup2.title, 'Second (Copy)');
  assert.equal(dup2.effortLevel, 'DEEP_FOCUS');
  assert.equal(dup1.sortOrder < dup2.sortOrder, true);
  assert.equal(
    snapAfterDup.checklistItems.filter((ci) => ci.taskId === dup1.id && !ci.isDeleted)
      .length,
    2
  );

  // Completing a pinned + blocked recurring task spawns next occurrence with isPinned=false & blockedByTaskIds=[]
  const recurring = await db.upsertTask({
    title: 'Weekly Sync',
    scheduleState: 'TODAY',
    whenDate: 1_760_054_400_000,
    isPinned: true,
    effortLevel: 'MEDIUM',
    blockedByTaskIds: [t1.id],
    recurrenceRule: {
      mode: 'FIXED_SCHEDULE',
      interval: 1,
      unit: 'WEEKS',
      daysOfWeek: []
    }
  });
  await db.upsertTask({ id: recurring.id, status: 'COMPLETED' });
  const spawned = db
    .getSnapshot()
    .tasks.find(
      (tk) =>
        !tk.isDeleted && tk.status === 'OPEN' && tk.title === 'Weekly Sync' && tk.id !== recurring.id
    )!;
  assert.ok(spawned);
  assert.equal(spawned.isPinned, false);
  assert.deepEqual(spawned.blockedByTaskIds, []);
  assert.equal(spawned.effortLevel, 'MEDIUM');
});

test('Firestore mappers and applyRemoteDeltas preserve local v3.0 fields when older v2.1/v2.5 peers omit them', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const proj = await db.upsertProject({
    id: 'proj-1',
    title: 'Roadmap Project',
    workspaceId: 'ws-team-1',
    viewMode: 'board'
  });
  const heading = await db.upsertHeading({
    id: 'hd-1',
    projectId: proj.id,
    title: 'Phase 1',
    startDate: 1_760_000_000_000,
    endDate: 1_760_500_000_000
  });
  const blocker = await db.upsertTask({
    id: 'tk-blocker',
    title: 'Blocker'
  });
  const localTask = await db.upsertTask({
    id: 'tk-1',
    title: 'Local v3 Task',
    projectId: proj.id,
    headingId: heading.id,
    isPinned: true,
    effortLevel: 'DEEP_FOCUS',
    blockedByTaskIds: [blocker.id],
    workspaceId: 'ws-team-1',
    assigneeId: 'user-42',
    durationMinutes: 90
  });

  // Simulate older v2.1 peer pushing newer updatedAt WITHOUT v2.5/v3.0 keys
  const newerTs = Date.now() + 60_000;
  const legacyRemoteTask = taskFromFirestoreMap('tk-1', {
    title: 'Edited on Legacy Peer',
    notes: 'Updated notes',
    status: 'OPEN',
    scheduleState: 'TODAY',
    projectId: proj.id,
    headingId: heading.id,
    tagIds: [],
    sortOrder: localTask.sortOrder,
    durationMinutes: 90,
    createdAt: localTask.createdAt,
    updatedAt: newerTs,
    isDeleted: false
  });
  assert.equal(legacyRemoteTask.omittedPinnedField, true);
  assert.equal(legacyRemoteTask.omittedV30TaskFields, true);

  const legacyRemoteHeading = headingFromFirestoreMap('hd-1', {
    projectId: proj.id,
    title: 'Phase 1 Renamed',
    sortOrder: heading.sortOrder,
    isArchived: false,
    createdAt: heading.createdAt,
    updatedAt: newerTs,
    isDeleted: false
  });
  assert.equal(legacyRemoteHeading.omittedStartDate, true);
  assert.equal(legacyRemoteHeading.omittedEndDate, true);

  const legacyRemoteProject = projectFromFirestoreMap('proj-1', {
    title: 'Roadmap Project Renamed',
    notes: '',
    status: 'ACTIVE',
    tagIds: [],
    sortOrder: proj.sortOrder,
    createdAt: proj.createdAt,
    updatedAt: newerTs,
    isDeleted: false
  });
  assert.equal(legacyRemoteProject.omittedV30ProjectFields, true);

  await db.applyRemoteDeltas({
    areas: [],
    projects: [legacyRemoteProject],
    headings: [legacyRemoteHeading],
    tasks: [legacyRemoteTask],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: []
  });

  const snap = db.getSnapshot();
  const mergedTask = snap.tasks.find((t) => t.id === 'tk-1')!;
  assert.equal(mergedTask.title, 'Edited on Legacy Peer');
  assert.equal(mergedTask.isPinned, true);
  assert.equal(mergedTask.effortLevel, 'DEEP_FOCUS');
  assert.deepEqual(mergedTask.blockedByTaskIds, ['tk-blocker']);
  assert.equal(mergedTask.workspaceId, 'ws-team-1');
  assert.equal(mergedTask.assigneeId, 'user-42');

  const mergedHeading = snap.headings.find((h) => h.id === 'hd-1')!;
  assert.equal(mergedHeading.title, 'Phase 1 Renamed');
  assert.equal(mergedHeading.startDate, 1_760_000_000_000);
  assert.equal(mergedHeading.endDate, 1_760_500_000_000);

  const mergedProject = snap.projects.find((p) => p.id === 'proj-1')!;
  assert.equal(mergedProject.title, 'Roadmap Project Renamed');
  assert.equal(mergedProject.workspaceId, 'ws-team-1');
  assert.equal(mergedProject.viewMode, 'board');

  const taskMap = taskToFirestoreMap(mergedTask);
  assert.equal(taskMap.isPinned, true);
  assert.equal(taskMap.effortLevel, 'DEEP_FOCUS');
  assert.deepEqual(taskMap.blockedByTaskIds, ['tk-blocker']);
  assert.equal(headingToFirestoreMap(mergedHeading).startDate, 1_760_000_000_000);
  assert.equal(projectToFirestoreMap(mergedProject).viewMode, 'board');
});

test('Encrypted .tbbackup roundtrip preserves all v2.5 + v3.0 task, heading, project, and preference fields', async () => {
  const now = 1_760_100_000_000;
  const savedPerspectives: SavedPerspective[] = [
    {
      id: 'persp-focus',
      title: 'Morning Quick Wins',
      categoryIds: ['tag-1'],
      effortLevel: 'QUICK_WIN',
      maxDurationMinutes: 30,
      dueWindow: 'TODAY',
      areaId: 'area-1',
      actionableOnly: true,
      sortOrder: 1000
    }
  ];

  const prefs: AppPreferences = {
    ...DEFAULT_PREFS,
    tagSortMode: 'most_used',
    viewSortMode: 'effort',
    savedPerspectives
  };

  const payload = buildBackupPayloadFromEntities({
    areas: [
      {
        id: 'area-1',
        title: 'Work',
        sortOrder: 1000,
        tagIds: [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      }
    ],
    projects: [
      {
        id: 'proj-1',
        title: 'v3 Launch',
        notes: '',
        status: 'ACTIVE',
        scheduleState: 'ANYTIME',
        whenDate: null,
        areaId: 'area-1',
        deadline: null,
        tagIds: [],
        sortOrder: 1000,
        completedAt: null,
        workspaceId: 'ws-alpha',
        viewMode: 'roadmap',
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      }
    ],
    headings: [
      {
        id: 'hd-1',
        projectId: 'proj-1',
        title: 'Milestone 2',
        sortOrder: 1000,
        isArchived: false,
        startDate: now,
        endDate: now + 86_400_000 * 7,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      }
    ],
    tasks: [
      makeTask({ id: 'blocker-1', title: 'Spec Signoff' }),
      makeTask({
        id: 'task-v3',
        title: 'Ship Perspectives',
        projectId: 'proj-1',
        headingId: 'hd-1',
        durationMinutes: 45,
        isPinned: true,
        effortLevel: 'DEEP_FOCUS',
        blockedByTaskIds: ['blocker-1'],
        workspaceId: 'ws-alpha',
        assigneeId: 'member-7'
      })
    ],
    checklistItems: [],
    tags: [
      {
        id: 'tag-1',
        title: 'Deep',
        parentTagId: null,
        shortcut: 'd',
        sortOrder: 1000,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      }
    ],
    attachments: [],
    projectTemplates: [],
    preferences: {
      tagSortMode: prefs.tagSortMode,
      viewSortMode: prefs.viewSortMode,
      savedPerspectivesJson: JSON.stringify(prefs.savedPerspectives),
      savedPerspectives: prefs.savedPerspectives
    },
    appVersionName: '3.0.0-alpha.11'
  });

  const encryptedBytes = await encodeThingsBackup(payload, 'correct-horse-battery-staple');
  const decryptedPayload = await decodeThingsBackup(
    encryptedBytes,
    'correct-horse-battery-staple'
  );
  const restored = normalizeBackupPayloadToEntities(decryptedPayload);

  const restoredTask = restored.tasks.find((t) => t.id === 'task-v3')!;
  assert.equal(restoredTask.isPinned, true);
  assert.equal(restoredTask.effortLevel, 'DEEP_FOCUS');
  assert.deepEqual(restoredTask.blockedByTaskIds, ['blocker-1']);
  assert.equal(restoredTask.durationMinutes, 45);
  assert.equal(restoredTask.workspaceId, 'ws-alpha');
  assert.equal(restoredTask.assigneeId, 'member-7');

  const restoredHeading = restored.headings.find((h) => h.id === 'hd-1')!;
  assert.equal(restoredHeading.startDate, now);
  assert.equal(restoredHeading.endDate, now + 86_400_000 * 7);

  const restoredProject = restored.projects.find((p) => p.id === 'proj-1')!;
  assert.equal(restoredProject.workspaceId, 'ws-alpha');
  assert.equal(restoredProject.viewMode, 'roadmap');

  assert.equal(restored.preferences.tagSortMode, 'most_used');
  assert.equal(restored.preferences.viewSortMode, 'effort');
  assert.deepEqual(restored.preferences.savedPerspectives, savedPerspectives);
});
