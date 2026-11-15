import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import { ThingsLocalDb, type UndoEventPayload } from '../src/data/localDb.ts';
import type {
  AreaRecord,
  AttachmentRecord,
  ChecklistItemRecord,
  HeadingRecord,
  ProjectRecord,
  TaskRecord
} from '../src/domain/types.ts';

type StoreMap = Map<string, unknown>;

/**
 * Lightweight in-memory IDBPDatabase mock compatible with ThingsLocalDb transactions & stores.
 * Passing the same `stores` map to a second `ThingsLocalDb` instance accurately simulates a browser page reload.
 */
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

async function seedProjectFixture(stores: Map<string, StoreMap>) {
  const now = 1_760_000_000_000;
  const area: AreaRecord = {
    id: 'area-work',
    title: 'Work',
    sortOrder: 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const project: ProjectRecord = {
    id: 'proj-alpha',
    areaId: area.id,
    title: 'Alpha Launch',
    notes: 'Launch notes',
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
    isDirty: false
  };
  const heading: HeadingRecord = {
    id: 'head-eng',
    projectId: project.id,
    title: 'Engineering',
    sortOrder: 1000,
    isArchived: false,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const openTask: TaskRecord = {
    id: 'task-open-1',
    title: 'Build API endpoint',
    notes: 'REST spec',
    status: 'OPEN',
    scheduleState: 'TODAY',
    whenDate: now,
    deadline: null,
    reminderTime: null,
    completedAt: null,
    projectId: project.id,
    headingId: heading.id,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const completedTask: TaskRecord = {
    id: 'task-done-2',
    title: 'Write design doc',
    notes: '',
    status: 'COMPLETED',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    completedAt: now - 5_000,
    projectId: project.id,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 2000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const unrelatedTask: TaskRecord = {
    id: 'task-standalone',
    title: 'Buy coffee beans',
    notes: '',
    status: 'OPEN',
    scheduleState: 'INBOX',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    completedAt: null,
    projectId: null,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 3000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const checklist: ChecklistItemRecord = {
    id: 'chk-1',
    taskId: openTask.id,
    title: 'Add unit tests',
    isCompleted: false,
    sortOrder: 100,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const projAttachment: AttachmentRecord = {
    id: 'att-proj-1',
    taskId: null,
    projectId: project.id,
    fileName: 'brief.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 1024,
    dataBase64: 'cHJvai1kYXRh',
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const taskAttachment: AttachmentRecord = {
    id: 'att-task-1',
    taskId: openTask.id,
    projectId: null,
    fileName: 'diagram.png',
    mimeType: 'image/png',
    sizeBytes: 2048,
    dataBase64: 'dGFzay1kYXRh',
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };

  stores.get('areas')!.set(area.id, area);
  stores.get('projects')!.set(project.id, project);
  stores.get('headings')!.set(heading.id, heading);
  stores.get('tasks')!.set(openTask.id, openTask);
  stores.get('tasks')!.set(completedTask.id, completedTask);
  stores.get('tasks')!.set(unrelatedTask.id, unrelatedTask);
  stores.get('checklist_items')!.set(checklist.id, checklist);
  stores.get('attachments')!.set(projAttachment.id, projAttachment);
  stores.get('attachments')!.set(taskAttachment.id, taskAttachment);
}

test('deleteProject(id, true) cascade-deletes OPEN & COMPLETED tasks, supports Undo, and restores cleanly across reload', async () => {
  const { opener, stores } = createInMemoryIdb();
  await seedProjectFixture(stores);

  const db = new ThingsLocalDb(opener);
  await db.refreshSnapshot();

  const undoEvents: UndoEventPayload[] = [];
  db.subscribeUndo((ev) => undoEvents.push(ev));

  // 1) Cascade delete (default true)
  await db.deleteProject('proj-alpha', true);
  let snap = db.getSnapshot();

  const deletedProj = snap.projects.find((p) => p.id === 'proj-alpha')!;
  assert.equal(deletedProj.isDeleted, true);
  assert.equal(deletedProj.purgedFromTrash, false);

  const tOpen = snap.tasks.find((t) => t.id === 'task-open-1')!;
  const tDone = snap.tasks.find((t) => t.id === 'task-done-2')!;
  const tStandalone = snap.tasks.find((t) => t.id === 'task-standalone')!;

  assert.equal(tOpen.isDeleted, true);
  assert.equal(tOpen.purgedFromTrash, false);
  assert.equal(tOpen.projectId, 'proj-alpha', 'projectId must remain intact on cascade-deleted task');
  assert.equal(tOpen.headingId, 'head-eng', 'headingId must remain intact on cascade-deleted task');

  assert.equal(tDone.isDeleted, true);
  assert.equal(tDone.projectId, 'proj-alpha');
  assert.equal(tStandalone.isDeleted, false, 'Unrelated task must not be touched');

  assert.equal(snap.headings.find((h) => h.id === 'head-eng')!.isDeleted, true);
  assert.equal(snap.checklistItems.find((c) => c.id === 'chk-1')!.isDeleted, true);
  assert.equal(snap.attachments.find((a) => a.id === 'att-proj-1')!.isDeleted, true);
  assert.equal(snap.attachments.find((a) => a.id === 'att-task-1')!.isDeleted, true);

  assert.equal(undoEvents.length, 1);
  assert.equal(undoEvents[0].kind, 'project_deleted');
  assert.equal(undoEvents[0].entityTitle, 'Alpha Launch');

  // 2) Undo restores project, headings, tasks, checklists, and attachment payloads
  const undone = await db.performUndo();
  assert.equal(undone, true);
  snap = db.getSnapshot();
  assert.equal(snap.projects.find((p) => p.id === 'proj-alpha')!.isDeleted, false);
  assert.equal(snap.headings.find((h) => h.id === 'head-eng')!.isDeleted, false);
  assert.equal(snap.tasks.find((t) => t.id === 'task-open-1')!.isDeleted, false);
  assert.equal(snap.tasks.find((t) => t.id === 'task-done-2')!.isDeleted, false);
  assert.equal(snap.checklistItems.find((c) => c.id === 'chk-1')!.isDeleted, false);
  assert.equal(snap.attachments.find((a) => a.id === 'att-task-1')!.isDeleted, false);
  assert.equal(snap.attachments.find((a) => a.id === 'att-task-1')!.dataBase64, 'dGFzay1kYXRh');

  // 3) Delete again with default parameter (true), then simulate browser reload via a fresh ThingsLocalDb instance
  await db.deleteProject('proj-alpha');
  const reloadedDb = new ThingsLocalDb(createInMemoryIdb(stores).opener);
  await reloadedDb.refreshSnapshot();

  await reloadedDb.restoreDeletedProject('proj-alpha');
  const restoredSnap = reloadedDb.getSnapshot();

  assert.equal(restoredSnap.projects.find((p) => p.id === 'proj-alpha')!.isDeleted, false);
  assert.equal(restoredSnap.headings.find((h) => h.id === 'head-eng')!.isDeleted, false);

  const restoredOpen = restoredSnap.tasks.find((t) => t.id === 'task-open-1')!;
  const restoredDone = restoredSnap.tasks.find((t) => t.id === 'task-done-2')!;
  assert.equal(restoredOpen.isDeleted, false);
  assert.equal(restoredOpen.projectId, 'proj-alpha');
  assert.equal(restoredOpen.headingId, 'head-eng');
  assert.equal(restoredDone.isDeleted, false);
  assert.equal(restoredDone.projectId, 'proj-alpha');
  assert.equal(restoredSnap.checklistItems.find((c) => c.id === 'chk-1')!.isDeleted, false);
});

test('restoreDeletedTask detaches task when parent project is still deleted, and permanentlyDeleteProject purges deleted child tasks', async () => {
  const { opener, stores } = createInMemoryIdb();
  await seedProjectFixture(stores);

  const db = new ThingsLocalDb(opener);
  await db.refreshSnapshot();

  await db.deleteProject('proj-alpha', true);

  // Restore a single task from Trash while its parent project remains in Trash
  await db.restoreDeletedTask('task-open-1');
  let snap = db.getSnapshot();

  const individuallyRestored = snap.tasks.find((t) => t.id === 'task-open-1')!;
  assert.equal(individuallyRestored.isDeleted, false);
  assert.equal(
    individuallyRestored.projectId,
    null,
    'Individually restored task must clear projectId when parent project is still deleted'
  );
  assert.equal(
    individuallyRestored.headingId,
    null,
    'Individually restored task must clear headingId when parent project is still deleted'
  );
  assert.equal(snap.checklistItems.find((c) => c.id === 'chk-1')!.isDeleted, false);

  // Remaining task-done-2 is still deleted under proj-alpha; permanentlyDeleteProject must purge it!
  await db.permanentlyDeleteProject('proj-alpha');
  snap = db.getSnapshot();

  const purgedProj = snap.projects.find((p) => p.id === 'proj-alpha')!;
  assert.equal(purgedProj.isDeleted, true);
  assert.equal(purgedProj.purgedFromTrash, true);
  assert.equal(purgedProj.title, '');

  const purgedChild = snap.tasks.find((t) => t.id === 'task-done-2')!;
  assert.equal(purgedChild.isDeleted, true);
  assert.equal(purgedChild.purgedFromTrash, true, 'Deleted child task must be purged from Trash');
  assert.equal(purgedChild.title, '');
  assert.equal(purgedChild.projectId, null);

  // Individually restored task remains active and untouched
  assert.equal(snap.tasks.find((t) => t.id === 'task-open-1')!.isDeleted, false);
});

test('restoreDeletedProject fallback restores synced/legacy deleted tasks (without deletedTaskIds) and validates headingId', async () => {
  const { opener, stores } = createInMemoryIdb();
  await seedProjectFixture(stores);

  // Simulate synced state where project & tasks are soft-deleted without local deletedTaskIds array,
  // and task-open-1 references a missing/deleted heading id
  const rawProj = stores.get('projects')!.get('proj-alpha') as ProjectRecord;
  stores.get('projects')!.set('proj-alpha', {
    ...rawProj,
    isDeleted: true,
    purgedFromTrash: false,
    deletedTaskIds: undefined,
    detachedTaskSnapshots: undefined
  });
  const rawTask = stores.get('tasks')!.get('task-open-1') as TaskRecord;
  stores.get('tasks')!.set('task-open-1', {
    ...rawTask,
    headingId: 'non-existent-heading',
    isDeleted: true,
    purgedFromTrash: false
  });

  const db = new ThingsLocalDb(opener);
  await db.refreshSnapshot();
  await db.restoreDeletedProject('proj-alpha');

  const snap = db.getSnapshot();
  const restored = snap.tasks.find((t) => t.id === 'task-open-1')!;
  assert.equal(restored.isDeleted, false);
  assert.equal(restored.projectId, 'proj-alpha');
  assert.equal(restored.headingId, null, 'Invalid headingId must be normalized to null');
});

test('deleteProject(id, false) keeps and detaches tasks to parent Area, and supports both Undo and restoreDeletedProject re-attachment', async () => {
  const { opener, stores } = createInMemoryIdb();
  await seedProjectFixture(stores);

  const db = new ThingsLocalDb(opener);
  await db.refreshSnapshot();

  // 1) Delete project ONLY, keeping tasks
  await db.deleteProject('proj-alpha', false);
  let snap = db.getSnapshot();

  assert.equal(snap.projects.find((p) => p.id === 'proj-alpha')!.isDeleted, true);
  const keptOpen = snap.tasks.find((t) => t.id === 'task-open-1')!;
  const keptDone = snap.tasks.find((t) => t.id === 'task-done-2')!;
  assert.equal(keptOpen.isDeleted, false);
  assert.equal(keptOpen.projectId, null);
  assert.equal(keptOpen.headingId, null);
  assert.equal(keptOpen.areaId, 'area-work', 'Kept task should inherit parent project areaId');
  assert.equal(keptDone.isDeleted, false);
  assert.equal(keptDone.projectId, null);

  // Task attachment must NOT be deleted when keeping tasks
  assert.equal(snap.attachments.find((a) => a.id === 'att-task-1')!.isDeleted, false);
  assert.equal(snap.attachments.find((a) => a.id === 'att-proj-1')!.isDeleted, true);

  // 2) Undo re-attaches tasks and restores project
  const undone = await db.performUndo();
  assert.equal(undone, true);
  snap = db.getSnapshot();
  assert.equal(snap.projects.find((p) => p.id === 'proj-alpha')!.isDeleted, false);
  assert.equal(snap.tasks.find((t) => t.id === 'task-open-1')!.projectId, 'proj-alpha');
  assert.equal(snap.tasks.find((t) => t.id === 'task-open-1')!.headingId, 'head-eng');
  assert.equal(snap.tasks.find((t) => t.id === 'task-open-1')!.areaId, null);

  // 3) Delete with keep-tasks again, then restoreDeletedProject from Trash
  await db.deleteProject('proj-alpha', false);
  await db.restoreDeletedProject('proj-alpha');
  snap = db.getSnapshot();

  assert.equal(snap.projects.find((p) => p.id === 'proj-alpha')!.isDeleted, false);
  const reattachedOpen = snap.tasks.find((t) => t.id === 'task-open-1')!;
  assert.equal(reattachedOpen.isDeleted, false);
  assert.equal(reattachedOpen.projectId, 'proj-alpha');
  assert.equal(reattachedOpen.headingId, 'head-eng');
  assert.equal(reattachedOpen.areaId, null);
});
