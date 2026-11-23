import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import { parseSmartTaskSyntax } from '../src/domain/smartSyntaxParser.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  normalizeBackupPayloadToEntities
} from '../src/data/backupCodec.ts';
import {
  taskFromFirestoreMap,
  taskToFirestoreMap
} from '../src/data/syncUtils.ts';
import { ThingsLocalDb } from '../src/data/localDb.ts';
import {
  addDaysLocal,
  startOfDayEpoch,
  type AreaRecord,
  type ProjectRecord,
  type TagRecord
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

const SAMPLE_TAGS: TagRecord[] = [
  {
    id: 'tag-work',
    title: 'Work',
    parentTagId: null,
    shortcut: null,
    sortOrder: 100,
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  },
  {
    id: 'tag-deep',
    title: 'Deep Work',
    parentTagId: null,
    shortcut: null,
    sortOrder: 200,
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  },
  {
    id: 'tag-deleted',
    title: 'ArchivedTag',
    parentTagId: null,
    shortcut: null,
    sortOrder: 300,
    createdAt: 1,
    updatedAt: 1,
    isDeleted: true,
    isDirty: false
  }
];

const SAMPLE_AREAS: AreaRecord[] = [
  {
    id: 'area-personal',
    title: 'Personal Growth',
    sortOrder: 100,
    tagIds: [],
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  },
  {
    id: 'area-finance',
    title: 'Finance',
    sortOrder: 200,
    tagIds: [],
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  }
];

const SAMPLE_PROJECTS: ProjectRecord[] = [
  {
    id: 'proj-launch',
    areaId: 'area-personal',
    title: 'Q4 Product Launch',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'TODAY',
    whenDate: null,
    deadline: null,
    completedAt: null,
    sortOrder: 100,
    tagIds: [],
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  },
  {
    id: 'proj-done',
    areaId: null,
    title: 'Completed Legacy',
    notes: '',
    status: 'COMPLETED',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: 10,
    sortOrder: 200,
    tagIds: [],
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  }
];

test('parseSmartTaskSyntax parses @when, !deadline, ~duration, #tag, /Project, /Area, !milestone across EN/DE/FR/IT/ES', () => {
  // Reference Monday 2026-11-23
  const ref = new Date(2026, 10, 23, 10, 0, 0, 0);
  const refStart = startOfDayEpoch(ref);
  const tmrEpoch = addDaysLocal(refStart, 1);
  const friEpoch = addDaysLocal(refStart, 4); // Mon(1) -> Fri(5) = +4d

  // 1. English full combo
  const en = parseSmartTaskSyntax(
    'Draft release announcement @tomorrow !fri ~45m #deep-work /Q4-Product-Launch !milestone',
    {
      tags: SAMPLE_TAGS,
      projects: SAMPLE_PROJECTS,
      areas: SAMPLE_AREAS,
      lang: 'en',
      reference: ref
    }
  );
  assert.equal(en.hasAnyMatch, true);
  assert.equal(en.cleanTitle, 'Draft release announcement');
  assert.equal(en.scheduleState, 'UPCOMING');
  assert.equal(en.whenDate, tmrEpoch);
  assert.ok(en.whenLabel && en.whenLabel.length > 0);
  assert.equal(en.deadline, friEpoch);
  assert.ok(en.deadlineLabel && en.deadlineLabel.length > 0);
  assert.equal(en.durationMinutes, 45);
  assert.deepEqual(en.matchedTagIds, ['tag-deep']);
  assert.equal(en.matchedProject?.id, 'proj-launch');
  assert.equal(en.matchedArea, null);
  assert.equal(en.isMilestone, true);

  // 2. German (@heute-abend, !freitag, ~1.5h, standalone ◆, unambiguous project prefix >= 3 chars)
  const de = parseSmartTaskSyntax('Steuerunterlagen prüfen @heute-abend !freitag ~1.5h /Q4-Pro ◆', {
    tags: SAMPLE_TAGS,
    projects: SAMPLE_PROJECTS,
    areas: SAMPLE_AREAS,
    lang: 'de',
    reference: ref
  });
  assert.equal(de.hasAnyMatch, true);
  assert.equal(de.cleanTitle, 'Steuerunterlagen prüfen');
  assert.equal(de.scheduleState, 'THIS_EVENING');
  assert.equal(de.whenDate, refStart);
  assert.equal(de.deadline, friEpoch);
  assert.equal(de.durationMinutes, 90);
  assert.equal(de.matchedProject?.id, 'proj-launch');
  assert.equal(de.isMilestone, true);

  // 3. French (@demain, !vendredi, ~30m, /Area fallback)
  const fr = parseSmartTaskSyntax('Réviser budget @demain !vendredi ~30m /Finance #work', {
    tags: SAMPLE_TAGS,
    projects: SAMPLE_PROJECTS,
    areas: SAMPLE_AREAS,
    lang: 'fr',
    reference: ref
  });
  assert.equal(fr.hasAnyMatch, true);
  assert.equal(fr.cleanTitle, 'Réviser budget');
  assert.equal(fr.scheduleState, 'UPCOMING');
  assert.equal(fr.whenDate, tmrEpoch);
  assert.equal(fr.deadline, friEpoch);
  assert.equal(fr.durationMinutes, 30);
  assert.equal(fr.matchedProject, null);
  assert.equal(fr.matchedArea?.id, 'area-finance');
  assert.deepEqual(fr.matchedTagIds, ['tag-work']);

  // 4. Italian (@stasera, !venerdi, quoted #"Deep Work" and /"Personal Growth")
  const it = parseSmartTaskSyntax(
    'Preparare presentazione @stasera !venerdi ~2h #"Deep Work" /"Personal Growth"',
    {
      tags: SAMPLE_TAGS,
      projects: SAMPLE_PROJECTS,
      areas: SAMPLE_AREAS,
      lang: 'it',
      reference: ref
    }
  );
  assert.equal(it.hasAnyMatch, true);
  assert.equal(it.cleanTitle, 'Preparare presentazione');
  assert.equal(it.scheduleState, 'THIS_EVENING');
  assert.equal(it.whenDate, refStart);
  assert.equal(it.deadline, friEpoch);
  assert.equal(it.durationMinutes, 120);
  assert.deepEqual(it.matchedTagIds, ['tag-deep']);
  assert.equal(it.matchedArea?.id, 'area-personal');

  // 5. Spanish (@mañana, !viernes, @2026-12-01 ISO date)
  const es = parseSmartTaskSyntax('Enviar propuesta @mañana !viernes ~15m', {
    tags: SAMPLE_TAGS,
    projects: SAMPLE_PROJECTS,
    areas: SAMPLE_AREAS,
    lang: 'es',
    reference: ref
  });
  assert.equal(es.hasAnyMatch, true);
  assert.equal(es.cleanTitle, 'Enviar propuesta');
  assert.equal(es.scheduleState, 'UPCOMING');
  assert.equal(es.whenDate, tmrEpoch);
  assert.equal(es.deadline, friEpoch);
  assert.equal(es.durationMinutes, 15);
});

test('parseSmartTaskSyntax preserves URLs, ratios, emails, unmatched tokens, and empty-title fallback', () => {
  const ref = new Date(2026, 10, 23, 10, 0, 0, 0);

  // URLs, fractions, and emails must NEVER be stripped or matched
  const urlCase = parseSmartTaskSyntax(
    'Review https://example.com/path/work#deep-work 3/4 scale mailto:dev@tomorrow.io',
    {
      tags: SAMPLE_TAGS,
      projects: SAMPLE_PROJECTS,
      areas: SAMPLE_AREAS,
      lang: 'en',
      reference: ref
    }
  );
  assert.equal(urlCase.hasAnyMatch, false);
  assert.equal(
    urlCase.cleanTitle,
    'Review https://example.com/path/work#deep-work 3/4 scale mailto:dev@tomorrow.io'
  );

  // Deleted tags and completed projects are ignored; unknown tokens remain untouched
  const unmatched = parseSmartTaskSyntax(
    'Investigate issue #ArchivedTag /Completed-Legacy @notadate',
    {
      tags: SAMPLE_TAGS,
      projects: SAMPLE_PROJECTS,
      areas: SAMPLE_AREAS,
      lang: 'en',
      reference: ref
    }
  );
  assert.equal(unmatched.hasAnyMatch, false);
  assert.equal(
    unmatched.cleanTitle,
    'Investigate issue #ArchivedTag /Completed-Legacy @notadate'
  );

  // Empty-title fallback: when all tokens match and stripping would leave cleanTitle empty, preserve rawTitle.trim()
  const onlyTokens = parseSmartTaskSyntax('  @tomorrow ~25m  ', {
    tags: SAMPLE_TAGS,
    projects: SAMPLE_PROJECTS,
    areas: SAMPLE_AREAS,
    lang: 'en',
    reference: ref
  });
  assert.equal(onlyTokens.hasAnyMatch, true);
  assert.equal(onlyTokens.scheduleState, 'UPCOMING');
  assert.equal(onlyTokens.durationMinutes, 25);
  assert.equal(onlyTokens.cleanTitle, '@tomorrow ~25m');
});

test('localDb actualMinutes persistence, clamping, addTaskActualMinutes, recurrence reset, duplicate reset, and purge reset', async () => {
  const { opener, stores } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const today = startOfDayEpoch();

  // 1. Persist & clamp actualMinutes (1..48_000; <= 0 -> null; undefined -> preserve)
  const task = await db.upsertTask({
    title: 'Deep Focus Implementation',
    scheduleState: 'TODAY',
    whenDate: today,
    durationMinutes: 60,
    actualMinutes: 20,
    recurrenceRule: {
      mode: 'FIXED_SCHEDULE',
      interval: 1,
      unit: 'DAYS',
      daysOfWeek: []
    }
  });
  assert.equal(task.actualMinutes, 20);

  await db.addTaskActualMinutes(task.id, 15);
  let snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === task.id)?.actualMinutes, 35);

  // Preserved when actualMinutes is undefined
  await db.upsertTask({ id: task.id, notes: 'Updated notes' });
  snap = db.getSnapshot();
  assert.equal(snap.tasks.find((t) => t.id === task.id)?.actualMinutes, 35);

  // Clamped at 48_000
  await db.upsertTask({ id: task.id, actualMinutes: 99_999 });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === task.id)?.actualMinutes, 48_000);
  await db.upsertTask({ id: task.id, actualMinutes: 42 });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === task.id)?.actualMinutes, 42);

  // 2. batchDuplicateTasks resets actualMinutes to null on copies
  const [dupId] = await db.batchDuplicateTasks([task.id], 'Copy');
  const dupTask = db.getSnapshot().tasks.find((t) => t.id === dupId);
  assert.ok(dupTask);
  assert.equal(dupTask.actualMinutes, null);

  // 3. Completing recurring task preserves actualMinutes on completed parent and resets actualMinutes: null on spawned occurrence
  await db.upsertTask({ id: task.id, status: 'COMPLETED' });
  snap = db.getSnapshot();
  const completedParent = snap.tasks.find((t) => t.id === task.id);
  assert.equal(completedParent?.status, 'COMPLETED');
  assert.equal(completedParent?.actualMinutes, 42);

  const spawnedNext = snap.tasks.find(
    (t) => t.id !== task.id && t.id !== dupId && !t.isDeleted && t.status === 'OPEN'
  );
  assert.ok(spawnedNext, 'Expected next recurring occurrence to spawn');
  assert.equal(spawnedNext.actualMinutes, null);

  // 4. permanentlyDeleteTask & emptyTrash reset actualMinutes: null on tombstones
  await db.upsertTask({ id: dupId, actualMinutes: 18 });
  await db.softDeleteTasks([dupId]);
  await db.permanentlyDeleteTask(dupId);
  const rawPurgedDup = stores.get('tasks')?.get(dupId) as { actualMinutes?: number | null } | undefined;
  assert.equal(rawPurgedDup?.actualMinutes, null);

  const trashCandidate = await db.upsertTask({
    title: 'Temp task',
    actualMinutes: 12
  });
  await db.softDeleteTasks([trashCandidate.id]);
  await db.emptyTrash();
  const rawEmptied = stores.get('tasks')?.get(trashCandidate.id) as
    | { actualMinutes?: number | null }
    | undefined;
  assert.equal(rawEmptied?.actualMinutes, null);

  // 5. permanentlyDeleteProject resets actualMinutes: null on purged deleted project tasks
  const proj = await db.upsertProject({ title: 'Temp Project' });
  const projTask = await db.upsertTask({
    title: 'Project Task with actual time',
    projectId: proj.id,
    actualMinutes: 27
  });
  await db.deleteProject(proj.id, true);
  await db.permanentlyDeleteProject(proj.id);
  const rawPurgedProjTask = stores.get('tasks')?.get(projTask.id) as
    | { actualMinutes?: number | null }
    | undefined;
  assert.equal(rawPurgedProjTask?.actualMinutes, null);
});

test('.tbbackup roundtrip and syncUtils omittedActualMinutesField preservation', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const created = await db.upsertTask({
    id: 'sync-actual-task-1',
    title: 'Write v3.2 Architecture Doc',
    scheduleState: 'TODAY',
    whenDate: startOfDayEpoch(),
    durationMinutes: 45,
    actualMinutes: 38
  });

  // 1. .tbbackup encode -> decode -> normalize roundtrip
  const snap = db.getSnapshot();
  const payload = buildBackupPayloadFromEntities({
    areas: snap.areas,
    projects: snap.projects,
    headings: snap.headings,
    tasks: snap.tasks,
    checklistItems: snap.checklistItems,
    tags: snap.tags
  });
  assert.equal(
    payload.tasks.find((t) => t.id === created.id)?.actualMinutes,
    38
  );

  const encodedBytes = await encodeThingsBackup(payload, null);
  const decodedPayload = await decodeThingsBackup(encodedBytes, null);
  const normalized = normalizeBackupPayloadToEntities(decodedPayload, false);
  const restoredTask = normalized.tasks.find((t) => t.id === created.id);
  assert.ok(restoredTask);
  assert.equal(restoredTask.actualMinutes, 38);
  assert.equal(restoredTask.durationMinutes, 45);

  // 2. syncUtils taskToFirestoreMap / taskFromFirestoreMap + omittedActualMinutesField preservation
  const fullRemoteMap = taskToFirestoreMap(created);
  assert.equal(fullRemoteMap.actualMinutes, 38);
  const parsedWithField = taskFromFirestoreMap(fullRemoteMap);
  assert.ok(parsedWithField);
  assert.equal(parsedWithField.actualMinutes, 38);
  assert.equal(parsedWithField.omittedActualMinutesField, undefined);

  // Simulate older v3.1 client pushing an update without `actualMinutes` key
  const legacyMap: Record<string, unknown> = { ...fullRemoteMap };
  delete legacyMap.actualMinutes;
  legacyMap.title = 'Write v3.2 Architecture Doc (Edited on v3.1 peer)';
  legacyMap.updatedAt = created.updatedAt + 5_000;

  const parsedLegacyDelta = taskFromFirestoreMap(legacyMap);
  assert.ok(parsedLegacyDelta);
  assert.equal(parsedLegacyDelta.omittedActualMinutesField, true);

  await db.applyRemoteBatch({
    tasks: [parsedLegacyDelta]
  });

  const afterLegacyMerge = db.getSnapshot().tasks.find((t) => t.id === created.id);
  assert.ok(afterLegacyMerge);
  assert.equal(afterLegacyMerge.title, 'Write v3.2 Architecture Doc (Edited on v3.1 peer)');
  assert.equal(
    afterLegacyMerge.actualMinutes,
    38,
    'Local actualMinutes must be preserved when older peer omits actualMinutes'
  );

  // Simulate v3.2 peer explicitly resetting actualMinutes: null
  const explicitResetMap: Record<string, unknown> = {
    ...legacyMap,
    actualMinutes: null,
    updatedAt: created.updatedAt + 10_000
  };
  const parsedResetDelta = taskFromFirestoreMap(explicitResetMap);
  assert.ok(parsedResetDelta);
  assert.equal(parsedResetDelta.omittedActualMinutesField, undefined);

  await db.applyRemoteBatch({
    tasks: [parsedResetDelta]
  });
  assert.equal(
    db.getSnapshot().tasks.find((t) => t.id === created.id)?.actualMinutes,
    null
  );
});
