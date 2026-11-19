import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import { ThingsLocalDb, DEFAULT_PREFS } from '../src/data/localDb.ts';
import {
  computeTagUsageCounts,
  sortTagsForMode,
  sortTasksForView
} from '../src/domain/v2Utils.ts';
import {
  taskFromFirestoreMap,
  taskToFirestoreMap
} from '../src/data/syncUtils.ts';
import {
  normalizeTagSortMode,
  normalizeViewSortMode
} from '../src/data/backupCodec.ts';
import type {
  AreaRecord,
  ChecklistItemRecord,
  ProjectRecord,
  ProjectTemplate,
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

function makeTask(overrides: Partial<TaskRecord> & { id: string; title: string }): TaskRecord {
  return {
    notes: '',
    status: 'OPEN',
    scheduleState: 'TODAY',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    scheduledStartMinutes: null,
    durationMinutes: null,
    isPinned: false,
    completedAt: null,
    projectId: null,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: 100,
    updatedAt: 100,
    isDeleted: false,
    isDirty: false,
    ...overrides
  };
}

test('sortTasksForView always places pinned tasks first and sorts accurately across manual, deadline, duration, and alpha modes', () => {
  const tasks: TaskRecord[] = [
    makeTask({
      id: 't-zeta',
      title: 'Zeta report',
      sortOrder: 100,
      createdAt: 20,
      deadline: 5_000,
      durationMinutes: 60,
      isPinned: false
    }),
    makeTask({
      id: 't-alpha',
      title: 'alpha call',
      sortOrder: 300,
      createdAt: 10,
      deadline: null,
      durationMinutes: 15,
      isPinned: false
    }),
    makeTask({
      id: 't-pin-b',
      title: 'Pinned Beta',
      sortOrder: 500,
      createdAt: 30,
      deadline: null,
      durationMinutes: 0,
      isPinned: true
    }),
    makeTask({
      id: 't-pin-a',
      title: 'Pinned Alpha',
      sortOrder: 400,
      createdAt: 40,
      deadline: 2_000,
      durationMinutes: 30,
      isPinned: true
    }),
    makeTask({
      id: 't-mid',
      title: 'Mid review',
      sortOrder: 200,
      createdAt: 5,
      deadline: 1_000,
      durationMinutes: null,
      isPinned: false
    })
  ];

  assert.deepEqual(
    sortTasksForView(tasks, 'manual').map((t) => t.id),
    ['t-pin-a', 't-pin-b', 't-zeta', 't-mid', 't-alpha']
  );

  assert.deepEqual(
    sortTasksForView(tasks, 'deadline').map((t) => t.id),
    ['t-pin-a', 't-pin-b', 't-mid', 't-zeta', 't-alpha']
  );

  assert.deepEqual(
    sortTasksForView(tasks, 'duration').map((t) => t.id),
    ['t-pin-a', 't-pin-b', 't-alpha', 't-zeta', 't-mid']
  );

  assert.deepEqual(
    sortTasksForView(tasks, 'alpha').map((t) => t.id),
    ['t-pin-a', 't-pin-b', 't-alpha', 't-mid', 't-zeta']
  );
});

test('sortTagsForMode and computeTagUsageCounts sort alpha, most_used, and manual accurately while ignoring soft-deleted entities', () => {
  const tags: TagRecord[] = [
    {
      id: 'tg-1',
      title: 'Work',
      parentTagId: null,
      shortcut: 'W',
      sortOrder: 3000,
      createdAt: 1,
      updatedAt: 1,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tg-2',
      title: 'errands',
      parentTagId: null,
      shortcut: null,
      sortOrder: 1000,
      createdAt: 2,
      updatedAt: 2,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tg-3',
      title: 'Deep Focus',
      parentTagId: null,
      shortcut: 'D',
      sortOrder: 2000,
      createdAt: 3,
      updatedAt: 3,
      isDeleted: false,
      isDirty: false
    }
  ];

  const counts = computeTagUsageCounts({
    tasks: [
      makeTask({ id: 't1', title: 'A', tagIds: ['tg-1', 'tg-3', 'tg-1'] }),
      makeTask({ id: 't2', title: 'B', tagIds: ['tg-1'], isDeleted: true }),
      makeTask({ id: 't3', title: 'C', tagIds: ['tg-3'] })
    ],
    projects: [
      {
        id: 'p1',
        areaId: null,
        title: 'Proj',
        notes: '',
        status: 'ACTIVE',
        scheduleState: 'ANYTIME',
        whenDate: null,
        deadline: null,
        completedAt: null,
        sortOrder: 100,
        tagIds: ['tg-3'],
        createdAt: 1,
        updatedAt: 1,
        isDeleted: false,
        isDirty: false
      }
    ],
    areas: [
      {
        id: 'a1',
        title: 'Area',
        sortOrder: 100,
        tagIds: ['tg-1'],
        createdAt: 1,
        updatedAt: 1,
        isDeleted: false,
        isDirty: false
      }
    ],
    projectTemplates: []
  });

  assert.equal(counts.get('tg-3'), 3);
  assert.equal(counts.get('tg-1'), 2);
  assert.equal(counts.get('tg-2') ?? 0, 0);

  assert.deepEqual(
    sortTagsForMode(tags, 'alpha', counts).map((t) => t.id),
    ['tg-3', 'tg-2', 'tg-1']
  );
  assert.deepEqual(
    sortTagsForMode(tags, 'most_used', counts).map((t) => t.id),
    ['tg-3', 'tg-1', 'tg-2']
  );
  assert.deepEqual(
    sortTagsForMode(tags, 'manual', counts).map((t) => t.id),
    ['tg-2', 'tg-3', 'tg-1']
  );
});

test('upsertTag, mergeTags, and moveTagOrder handle sortOrder spacing, shortcut clearing, case-insensitive rename auto-merge, and full entity remapping', async () => {
  const { opener, stores } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);

  const tagA = await db.upsertTag({ title: 'Urgent', shortcut: 'U' });
  const tagB = await db.upsertTag({ title: 'Waiting', shortcut: 'W' });
  const tagC = await db.upsertTag({ title: 'Home', parentTagId: tagA.id });

  assert.equal(tagA.sortOrder, 1000);
  assert.equal(tagB.sortOrder, 2000);
  assert.equal(tagC.sortOrder, 3000);

  // Clear shortcut explicitly via null
  const clearedB = await db.upsertTag({ id: tagB.id, title: 'Waiting', shortcut: null });
  assert.equal(clearedB.shortcut, null);

  // Manual reorder swaps sortOrder cleanly
  await db.moveTagOrder(tagC.id, 'up');
  let activeTags = db
    .getSnapshot()
    .tags.filter((t) => !t.isDeleted)
    .sort((a, b) => a.sortOrder - b.sortOrder);
  assert.deepEqual(
    activeTags.map((t) => t.id),
    [tagA.id, tagC.id, tagB.id]
  );

  // Seed entities referencing tagA and tagB to verify deduplicated atomic merge
  const area = await db.upsertArea({ title: 'Ops', tagIds: [tagA.id] });
  const proj = await db.upsertProject({ title: 'Q4', tagIds: [tagA.id, tagB.id] });
  const task = await db.upsertTask({ title: 'Follow up', tagIds: [tagA.id] });
  const tpl = await db.upsertProjectTemplate({
    title: 'Launch Tpl',
    tagIds: [tagA.id],
    blueprintJson: '{}'
  });

  const mergedOk = await db.mergeTags(tagA.id, tagB.id);
  assert.equal(mergedOk, true);

  const snap = db.getSnapshot();
  const rawSourceTag = (await stores.get('tags')!.get(tagA.id)) as TagRecord;
  assert.equal(rawSourceTag.isDeleted, true);
  assert.equal(rawSourceTag.isDirty, true);

  assert.deepEqual(
    snap.areas.find((a: AreaRecord) => a.id === area.id)?.tagIds,
    [tagB.id]
  );
  assert.deepEqual(
    snap.projects.find((p: ProjectRecord) => p.id === proj.id)?.tagIds,
    [tagB.id]
  );
  assert.deepEqual(
    snap.tasks.find((t: TaskRecord) => t.id === task.id)?.tagIds,
    [tagB.id]
  );
  assert.deepEqual(
    snap.projectTemplates.find((pt: ProjectTemplate) => pt.id === tpl.id)?.tagIds,
    [tagB.id]
  );
  // Child tagC had parentTagId === tagA.id -> re-parented to tagB.id
  assert.equal(
    snap.tags.find((tg) => tg.id === tagC.id)?.parentTagId,
    tagB.id
  );

  // Renaming tagC to 'waiting' (case-insensitive collision with active tagB) auto-merges into tagB!
  const taskWithC = await db.upsertTask({ title: 'Clean desk', tagIds: [tagC.id] });
  const renameMerged = await db.upsertTag({ id: tagC.id, title: 'waiting' });
  assert.equal(renameMerged.id, tagB.id);
  assert.deepEqual(
    db.getSnapshot().tasks.find((t) => t.id === taskWithC.id)?.tagIds,
    [tagB.id]
  );
  activeTags = db.getSnapshot().tags.filter((t) => !t.isDeleted);
  assert.deepEqual(
    activeTags.map((t) => t.id),
    [tagB.id]
  );
});

test('batchDuplicateTasks preserves display order, clones checklists, preserves isPinned, and spawnNextRecurrence resets isPinned on future occurrence', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);

  const t1 = await db.upsertTask({
    title: 'First',
    scheduleState: 'TODAY',
    sortOrder: 1000,
    isPinned: true
  });
  await db.addChecklistItems(t1.id, ['Step 1', 'Step 2']);

  const t2 = await db.upsertTask({
    title: 'Second',
    scheduleState: 'TODAY',
    sortOrder: 2000,
    isPinned: false
  });
  const t3 = await db.upsertTask({
    title: 'Third',
    scheduleState: 'TODAY',
    sortOrder: 3000,
    isPinned: false
  });

  // Duplicate [t1, t2] in order; copies should sit cleanly between t1/t2 and t2/t3 without sortOrder collision
  const copyIds = await db.batchDuplicateTasks([t1.id, t2.id], 'Copy');
  assert.equal(copyIds.length, 2);

  const snap = db.getSnapshot();
  const c1 = snap.tasks.find((t) => t.id === copyIds[0])!;
  const c2 = snap.tasks.find((t) => t.id === copyIds[1])!;
  assert.equal(c1.title, 'First (Copy)');
  assert.equal(c1.isPinned, true);
  assert.ok(c1.sortOrder > t1.sortOrder && c1.sortOrder < t2.sortOrder);

  assert.equal(c2.title, 'Second (Copy)');
  assert.equal(c2.isPinned, false);
  assert.ok(c2.sortOrder > t2.sortOrder && c2.sortOrder < t3.sortOrder);

  const clonedChecklist = snap.checklistItems.filter(
    (chk: ChecklistItemRecord) => !chk.isDeleted && chk.taskId === c1.id
  );
  assert.deepEqual(
    clonedChecklist.map((c) => c.title),
    ['Step 1', 'Step 2']
  );

  // Completing a pinned recurring task spawns next occurrence with isPinned: false
  const recTask = await db.upsertTask({
    title: 'Daily Standup',
    scheduleState: 'TODAY',
    whenDate: 1_760_000_000_000,
    isPinned: true,
    recurrenceRule: {
      freq: 'DAILY',
      interval: 1,
      mode: 'FIXED'
    }
  });
  await db.upsertTask({ id: recTask.id, status: 'COMPLETED' });

  const afterComplete = db.getSnapshot().tasks.filter((t) => !t.isDeleted);
  const spawned = afterComplete.find(
    (t) => t.id !== recTask.id && t.title === 'Daily Standup' && t.status === 'OPEN'
  );
  assert.ok(spawned, 'Expected next recurring occurrence to spawn');
  assert.equal(spawned.isPinned, false);
});

test('Firestore task mappers, applyRemoteDeltas omittedPinnedField, and .tbbackup roundtrip preserve isPinned, tagSortMode, and viewSortMode', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);

  const localPinned = await db.upsertTask({
    id: 'sync-task-1',
    title: 'Pinned Locally',
    scheduleState: 'TODAY',
    isPinned: true
  });

  const serialized = taskToFirestoreMap(localPinned);
  assert.equal(serialized.isPinned, true);

  const deletedSerialized = taskToFirestoreMap({ ...localPinned, isDeleted: true });
  assert.equal(deletedSerialized.isPinned, false);

  // Legacy remote payload without `isPinned` key preserves local `isPinned: true`
  const legacyMap: Record<string, unknown> = { ...serialized, updatedAt: localPinned.updatedAt + 5000 };
  delete legacyMap.isPinned;
  const parsedLegacy = taskFromFirestoreMap(legacyMap)!;
  assert.equal(parsedLegacy.omittedPinnedField, true);

  await db.applyRemoteDeltas({ tasks: [parsedLegacy] });
  assert.equal(
    db.getSnapshot().tasks.find((t) => t.id === 'sync-task-1')?.isPinned,
    true
  );

  // Explicit remote `isPinned: false` updates local task
  const explicitUnpin = taskFromFirestoreMap({
    ...serialized,
    isPinned: false,
    updatedAt: localPinned.updatedAt + 10_000
  })!;
  assert.equal(explicitUnpin.omittedPinnedField, undefined);
  await db.applyRemoteDeltas({ tasks: [explicitUnpin] });
  assert.equal(
    db.getSnapshot().tasks.find((t) => t.id === 'sync-task-1')?.isPinned,
    false
  );

  // Pin again and verify encrypted .tbbackup roundtrip of isPinned, tagSortMode, and viewSortMode
  await db.upsertTask({ id: 'sync-task-1', isPinned: true });
  const { bytes } = await db.createBackupBytes('secret-pass-25', {
    ...DEFAULT_PREFS,
    tagSortMode: 'most_used',
    viewSortMode: 'deadline'
  });

  const { opener: opener2 } = createInMemoryIdb();
  const db2 = new ThingsLocalDb(opener2);
  const restored = await db2.restoreBackupFromBytes(bytes, 'secret-pass-25', false);

  assert.equal(normalizeTagSortMode(restored.preferences?.tagSortMode), 'most_used');
  assert.equal(normalizeViewSortMode(restored.preferences?.viewSortMode), 'deadline');
  assert.equal(
    db2.getSnapshot().tasks.find((t) => t.id === 'sync-task-1')?.isPinned,
    true
  );
});
