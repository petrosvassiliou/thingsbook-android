import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import { parseSmartTaskSyntax } from '../src/domain/smartSyntaxParser.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  normalizeBackupPayloadToEntities,
  safeParseSavedPerspectives
} from '../src/data/backupCodec.ts';
import {
  planRecurringDeduplication,
  taskFromFirestoreMap,
  taskToFirestoreMap
} from '../src/data/syncUtils.ts';
import { ThingsLocalDb, normalizeTask } from '../src/data/localDb.ts';
import {
  DEFAULT_QUICK_FIND_FILTER,
  STALLED_PROJECT_THRESHOLD_MS,
  allocateTaskWorkloadMinutesByEpochDay,
  buildDailyTimeline,
  computeDayCapacityBreakdown,
  computeMultiDayWorkloadForecast,
  detectStalledProjects,
  evaluateProjectStalledStatus,
  filterTasksForPerspective,
  hasActiveQuickFindFilters,
  matchesQuickFindProject,
  matchesQuickFindTask,
  sortTasksForView
} from '../src/domain/v2Utils.ts';
import {
  buildAreaPortfolioRoadmapLayout,
  buildProjectRoadmapLayout
} from '../src/domain/v30Roadmap.ts';
import { TRANSLATIONS } from '../src/i18n/translations.ts';
import {
  addDaysLocal,
  localEpochDayFromMillis,
  startOfDayEpoch,
  type AreaRecord,
  type HeadingRecord,
  type ProjectRecord,
  type SavedPerspective,
  type TagRecord,
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
  const now = 1_760_000_000_000;
  return {
    id: overrides.id,
    title: overrides.title,
    notes: overrides.notes ?? '',
    status: overrides.status ?? 'OPEN',
    scheduleState: overrides.scheduleState ?? 'ANYTIME',
    whenDate: overrides.whenDate ?? null,
    deadline: overrides.deadline ?? null,
    reminderTime: overrides.reminderTime ?? null,
    scheduledStartMinutes: overrides.scheduledStartMinutes ?? null,
    durationMinutes: overrides.durationMinutes ?? null,
    actualMinutes: overrides.actualMinutes ?? null,
    isPinned: overrides.isPinned ?? false,
    isMilestone: overrides.isMilestone ?? false,
    isInProgress: overrides.isInProgress ?? false,
    effortLevel: overrides.effortLevel ?? null,
    blockedByTaskIds: overrides.blockedByTaskIds ?? [],
    workspaceId: overrides.workspaceId ?? null,
    assigneeId: overrides.assigneeId ?? null,
    completedAt: overrides.completedAt ?? null,
    projectId: overrides.projectId ?? null,
    headingId: overrides.headingId ?? null,
    areaId: overrides.areaId ?? null,
    recurrenceRule: overrides.recurrenceRule ?? null,
    sortOrder: overrides.sortOrder ?? 1000,
    tagIds: overrides.tagIds ?? [],
    createdAt: overrides.createdAt ?? now,
    updatedAt: overrides.updatedAt ?? now,
    isDeleted: overrides.isDeleted ?? false,
    isDirty: overrides.isDirty ?? false,
    purgedFromTrash: overrides.purgedFromTrash ?? false
  };
}

test('v3.3 translations expose all 6 In Progress strings across EN, DE, FR, IT, ES', () => {
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const tr = TRANSLATIONS[lang];
    assert.ok(tr.v33InProgressBadge.trim().length > 0);
    assert.ok(tr.v33MarkInProgress.trim().length > 0);
    assert.ok(tr.v33ClearInProgress.trim().length > 0);
    assert.ok(tr.v33FilterInProgressOnly.trim().length > 0);
    assert.ok(tr.v33CmdToggleInProgress.trim().length > 0);
    assert.ok(tr.v33ProjectInProgressCount(3).includes('3'));
  }
  assert.equal(TRANSLATIONS.en.v33ProjectInProgressCount(2), '2 in progress');
  assert.equal(TRANSLATIONS.de.v33ProjectInProgressCount(2), '2 in Arbeit');
  assert.equal(TRANSLATIONS.fr.v33ProjectInProgressCount(2), '2 en cours');
  assert.equal(TRANSLATIONS.it.v33ProjectInProgressCount(2), '2 in corso');
  assert.equal(TRANSLATIONS.es.v33ProjectInProgressCount(2), '2 en curso');
});

test('v3.3 parseSmartTaskSyntax parses ◐, !wip, and all 5-language In Progress tokens (unquoted & quoted)', () => {
  const emptyEntities = { tags: [] as TagRecord[], projects: [] as ProjectRecord[], areas: [] as AreaRecord[] };
  const tokens = [
    '!wip',
    '!inprogress',
    '!in-progress',
    '!doing',
    '!inarbeit',
    '!in-arbeit',
    '!encours',
    '!en-cours',
    '!incorso',
    '!in-corso',
    '!encurso',
    '!en-curso',
    '!"in progress"',
    '!"in arbeit"',
    '!"en cours"',
    '!"in corso"',
    '!"en curso"',
    '◐'
  ];

  for (const tok of tokens) {
    const res = parseSmartTaskSyntax(`Draft architecture spec ${tok}`, emptyEntities);
    assert.equal(res.isInProgress, true, `Expected ${tok} to set isInProgress=true`);
    assert.equal(res.hasAnyMatch, true, `Expected ${tok} to set hasAnyMatch=true`);
    assert.equal(res.cleanTitle, 'Draft architecture spec', `Clean title mismatch for ${tok}`);
  }

  // Combined with milestone + duration
  const combo = parseSmartTaskSyntax('Ship v3.3 release !milestone !wip ~45m', emptyEntities);
  assert.equal(combo.cleanTitle, 'Ship v3.3 release');
  assert.equal(combo.isMilestone, true);
  assert.equal(combo.isInProgress, true);
  assert.equal(combo.durationMinutes, 45);
});

test('v3.3 localDb toggleTaskInProgress, auto-triggers (checklist & actualMinutes), manual clear override, and lifecycle resets', async () => {
  const { opener, stores } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  // 1. Create open task -> defaults to isInProgress: false
  const created = await db.upsertTask({
    title: 'Implement Option A',
    scheduleState: 'TODAY',
    whenDate: startOfDayEpoch()
  });
  assert.equal(created.isInProgress, false);

  // 2. Toggle ON and OFF via toggleTaskInProgress
  const toggledOn = await db.toggleTaskInProgress(created.id);
  assert.equal(toggledOn, true);
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === created.id)?.isInProgress, true);

  const toggledOff = await db.toggleTaskInProgress(created.id);
  assert.equal(toggledOff, false);
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === created.id)?.isInProgress, false);

  // 3. Auto-trigger #1: Checking a checklist item (false -> true) sets parent OPEN task isInProgress = true
  await db.addChecklistItems(created.id, ['Write unit tests', 'Run build']);
  const chkItems = db.getSnapshot().checklistItems.filter((c) => c.taskId === created.id);
  assert.equal(chkItems.length, 2);

  await db.updateChecklistItem(chkItems[0].id, { isCompleted: true });
  assert.equal(
    db.getSnapshot().tasks.find((t) => t.id === created.id)?.isInProgress,
    true,
    'Checking subtask should auto-set parent task isInProgress = true'
  );

  // Manual override: user explicitly clears In Progress while subtask stays checked
  await db.upsertTask({ id: created.id, isInProgress: false });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === created.id)?.isInProgress, false);

  // Unchecking subtask (true -> false) does NOT set isInProgress = true
  await db.updateChecklistItem(chkItems[0].id, { isCompleted: false });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === created.id)?.isInProgress, false);

  // 4. Auto-trigger #2: addTaskActualMinutes(delta > 0) sets OPEN task isInProgress = true
  await db.addTaskActualMinutes(created.id, 15);
  const afterFocus = db.getSnapshot().tasks.find((t) => t.id === created.id)!;
  assert.equal(afterFocus.actualMinutes, 15);
  assert.equal(afterFocus.isInProgress, true);

  // 5. Duplicating an in-progress task resets isInProgress = false on the copy
  const dupTaskId = await db.duplicateTask(created.id, 'Copy');
  assert.ok(dupTaskId);
  const dupTask = db.getSnapshot().tasks.find((t) => t.id === dupTaskId)!;
  assert.equal(dupTask.isInProgress, false);

  // 6. Completing or canceling resets isInProgress = false
  const completed = await db.upsertTask({ id: created.id, status: 'COMPLETED' });
  assert.equal(completed.isInProgress, false);
  assert.equal(await db.toggleTaskInProgress(created.id), false);

  await db.upsertTask({ id: dupTaskId!, isInProgress: true });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === dupTaskId)?.isInProgress, true);
  const canceled = await db.upsertTask({ id: dupTaskId!, status: 'CANCELED' });
  assert.equal(canceled.isInProgress, false);

  // 7. Recurring completion spawns next occurrence with isInProgress = false
  const recTask = await db.upsertTask({
    title: 'Weekly Sync Notes',
    scheduleState: 'TODAY',
    whenDate: startOfDayEpoch(),
    isInProgress: true,
    recurrenceRule: {
      mode: 'FIXED_SCHEDULE',
      interval: 1,
      unit: 'WEEKS',
      daysOfWeek: []
    }
  });
  assert.equal(recTask.isInProgress, true);
  await db.upsertTask({ id: recTask.id, status: 'COMPLETED' });
  const afterRecSnap = db.getSnapshot().tasks;
  const completedRec = afterRecSnap.find((t) => t.id === recTask.id)!;
  const spawnedRec = afterRecSnap.find(
    (t) => t.id !== recTask.id && t.title === 'Weekly Sync Notes' && t.status === 'OPEN'
  )!;
  assert.equal(completedRec.isInProgress, false);
  assert.ok(spawnedRec);
  assert.equal(spawnedRec.isInProgress, false);

  // 8. Project duplicate & completeProjectWithUndo(completeOpenTasks=true) reset isInProgress = false
  const proj = await db.upsertProject({ title: 'Q4 Launch' });
  const projTask = await db.upsertTask({
    title: 'Prepare keynote',
    projectId: proj.id,
    isInProgress: true
  });
  assert.equal(projTask.isInProgress, true);

  const clonedProjId = await db.duplicateProject(proj.id, 'Copy');
  assert.ok(clonedProjId);
  const clonedProjTasks = db
    .getSnapshot()
    .tasks.filter((t) => t.projectId === clonedProjId && !t.isDeleted);
  assert.equal(clonedProjTasks.length, 1);
  assert.equal(clonedProjTasks[0].isInProgress, false);

  await db.completeProjectWithUndo(proj.id, true);
  const completedProjTask = db.getSnapshot().tasks.find((t) => t.id === projTask.id)!;
  assert.equal(completedProjTask.status, 'COMPLETED');
  assert.equal(completedProjTask.isInProgress, false);

  // 9. Permanent delete & emptyTrash reset isInProgress = false on tombstones
  const trashCandidate1 = await db.upsertTask({ title: 'Temp 1', isInProgress: true });
  const trashCandidate2 = await db.upsertTask({ title: 'Temp 2', isInProgress: true });
  await db.softDeleteTasks([trashCandidate1.id, trashCandidate2.id]);
  await db.permanentlyDeleteTask(trashCandidate1.id);
  await db.emptyTrash();

  const rawTomb1 = (await stores.get('tasks')!.get(trashCandidate1.id)) as TaskRecord;
  const rawTomb2 = (await stores.get('tasks')!.get(trashCandidate2.id)) as TaskRecord;
  assert.equal(rawTomb1.isInProgress, false);
  assert.equal(rawTomb2.isInProgress, false);

  const projToPurge = await db.upsertProject({ title: 'Purgeable Project' });
  const projTaskToPurge = await db.upsertTask({
    title: 'Purgeable Task',
    projectId: projToPurge.id,
    isInProgress: true
  });
  await db.softDeleteTasks([projTaskToPurge.id]);
  await db.deleteProject(projToPurge.id);
  await db.permanentlyDeleteProject(projToPurge.id);
  const rawProjTombTask = (await stores.get('tasks')!.get(projTaskToPurge.id)) as TaskRecord;
  assert.equal(rawProjTombTask.isInProgress, false);

  assert.equal(
    normalizeTask({ ...trashCandidate1, status: 'COMPLETED', isInProgress: true }).isInProgress,
    false
  );
});

test('v3.3 .tbbackup roundtrip & Firestore sync preserve isInProgress and omittedInProgressField', async () => {
  const openWip = makeTask({
    id: 'task-wip-open',
    title: 'Active API refactor',
    status: 'OPEN',
    isInProgress: true
  });
  const doneTask = makeTask({
    id: 'task-done',
    title: 'Finished migration',
    status: 'COMPLETED',
    isInProgress: true, // should be sanitized to false on export & restore
    completedAt: 1_760_000_050_000
  });
  const savedPersp: SavedPerspective = {
    id: 'persp-wip',
    title: 'Active WIP',
    categoryIds: [],
    effortLevel: null,
    maxDurationMinutes: null,
    dueWindow: 'ANY',
    areaId: null,
    actionableOnly: true,
    inProgressOnly: true,
    sortOrder: 1000
  };

  const payload = buildBackupPayloadFromEntities({
    areas: [],
    projects: [],
    headings: [],
    tasks: [openWip, doneTask],
    checklistItems: [],
    tags: [],
    preferences: {
      savedPerspectivesJson: JSON.stringify([savedPersp]),
      savedPerspectives: [savedPersp]
    }
  });

  const encoded = await encodeThingsBackup(payload, null);
  const decoded = await decodeThingsBackup(encoded, null);
  const restored = normalizeBackupPayloadToEntities(decoded);

  const restoredOpen = restored.tasks.find((t) => t.id === 'task-wip-open')!;
  const restoredDone = restored.tasks.find((t) => t.id === 'task-done')!;
  assert.equal(restoredOpen.isInProgress, true);
  assert.equal(restoredDone.isInProgress, false);

  const parsedPersp = safeParseSavedPerspectives(decoded.preferences.savedPerspectivesJson);
  assert.equal(parsedPersp.length, 1);
  assert.equal(parsedPersp[0].inProgressOnly, true);

  // Firestore serialization & omittedInProgressField hint
  const fsMap = taskToFirestoreMap(openWip);
  assert.equal(fsMap.isInProgress, true);
  const fsDoneMap = taskToFirestoreMap(doneTask);
  assert.equal(fsDoneMap.isInProgress, false);

  const parsedWithField = taskFromFirestoreMap(fsMap)!;
  assert.equal(parsedWithField.isInProgress, true);
  assert.equal(parsedWithField.omittedInProgressField, undefined);

  const legacyMapWithoutInProgress = { ...fsMap };
  delete legacyMapWithoutInProgress.isInProgress;
  legacyMapWithoutInProgress.title = 'Edited from v3.2 client';
  legacyMapWithoutInProgress.updatedAt = openWip.updatedAt + 5_000;

  const parsedLegacy = taskFromFirestoreMap(legacyMapWithoutInProgress)!;
  assert.equal(parsedLegacy.isInProgress, false);
  assert.equal(parsedLegacy.omittedInProgressField, true);

  // Verify applyRemoteDeltas preserves local isInProgress when omittedInProgressField is true on OPEN task
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');
  await db.upsertTask({
    id: openWip.id,
    title: openWip.title,
    isInProgress: true
  });
  const localBefore = db.getSnapshot().tasks.find((t) => t.id === openWip.id)!;
  assert.equal(localBefore.isInProgress, true);

  const legacyRemoteHint = taskFromFirestoreMap({
    ...legacyMapWithoutInProgress,
    updatedAt: localBefore.updatedAt + 10_000
  })!;
  await db.applyRemoteDeltas({ tasks: [legacyRemoteHint] });

  const afterLegacyMerge = db.getSnapshot().tasks.find((t) => t.id === openWip.id)!;
  assert.equal(afterLegacyMerge.title, 'Edited from v3.2 client');
  assert.equal(
    afterLegacyMerge.isInProgress,
    true,
    'Local isInProgress must survive remote delta from older client omitting isInProgress'
  );

  // When older client completes the task, isInProgress resets to false
  const legacyCompletedHint = taskFromFirestoreMap({
    ...legacyMapWithoutInProgress,
    status: 'COMPLETED',
    completedAt: localBefore.updatedAt + 20_000,
    updatedAt: localBefore.updatedAt + 20_000
  })!;
  await db.applyRemoteDeltas({ tasks: [legacyCompletedHint] });
  assert.equal(db.getSnapshot().tasks.find((t) => t.id === openWip.id)?.isInProgress, false);

  // Recurring deduplication preserves isInProgress from loser onto winner
  const dedupPlan = planRecurringDeduplication([
    makeTask({
      id: 'rec-win',
      title: 'Daily Standup',
      whenDate: addDaysLocal(startOfDayEpoch(), 1),
      createdAt: 1000,
      isInProgress: false,
      recurrenceRule: { mode: 'FIXED_SCHEDULE', interval: 1, unit: 'DAYS', daysOfWeek: [] }
    }),
    makeTask({
      id: 'rec-lose',
      title: 'Daily Standup',
      whenDate: startOfDayEpoch(),
      createdAt: 2000,
      isInProgress: true,
      recurrenceRule: { mode: 'FIXED_SCHEDULE', interval: 1, unit: 'DAYS', daysOfWeek: [] }
    })
  ]);
  assert.deepEqual(dedupPlan.loserIds, ['rec-lose']);
  assert.equal(dedupPlan.winnerEnrichments.length, 1);
  assert.equal(dedupPlan.winnerEnrichments[0].isInProgress, true);
});

test('v3.3 sorting, perspectives, Quick Find, Roadmap counts, and stalled-project suppression', () => {
  const regularEarly = makeTask({ id: 't-reg', title: 'B Regular', sortOrder: 100 });
  const wipLate = makeTask({
    id: 't-wip',
    title: 'C In Progress',
    sortOrder: 500,
    isInProgress: true
  });
  const pinnedVeryLate = makeTask({
    id: 't-pin',
    title: 'Z Pinned',
    sortOrder: 900,
    isPinned: true
  });
  const completedWipFlag = makeTask({
    id: 't-done-wip',
    title: 'A Completed',
    status: 'COMPLETED',
    sortOrder: 50,
    isInProgress: true
  });

  // 1. sortTasksForView ranks isPinned -> OPEN isInProgress -> others across sort modes
  for (const mode of ['manual', 'alpha', 'deadline', 'duration', 'effort'] as const) {
    const sorted = sortTasksForView(
      [regularEarly, wipLate, pinnedVeryLate, completedWipFlag],
      mode
    );
    assert.equal(sorted[0].id, 't-pin', `Pinned first in ${mode}`);
    assert.equal(sorted[1].id, 't-wip', `Open In Progress second in ${mode}`);
  }

  // 2. buildDailyTimeline unscheduledTasks ranks isPinned -> OPEN isInProgress -> sortOrder
  const timeline = buildDailyTimeline(
    [
      { ...regularEarly, scheduleState: 'TODAY' },
      { ...wipLate, scheduleState: 'TODAY' },
      { ...pinnedVeryLate, scheduleState: 'TODAY' }
    ],
    []
  );
  assert.deepEqual(
    timeline.unscheduledTasks.map((t) => t.id),
    ['t-pin', 't-wip', 't-reg']
  );

  // 3. filterTasksForPerspective with inProgressOnly: true
  const persp: SavedPerspective = {
    id: 'p1',
    title: 'WIP Focus',
    categoryIds: [],
    effortLevel: null,
    maxDurationMinutes: null,
    dueWindow: 'ANY',
    areaId: null,
    actionableOnly: false,
    inProgressOnly: true,
    sortOrder: 1000
  };
  const perspFiltered = filterTasksForPerspective(
    [regularEarly, wipLate, pinnedVeryLate, completedWipFlag],
    persp,
    new Set(['t-reg', 't-wip', 't-pin'])
  );
  assert.deepEqual(perspFiltered.map((t) => t.id), ['t-wip']);

  // 4. QuickFindFilter with inProgressOnly: true
  const qfFilter = { ...DEFAULT_QUICK_FIND_FILTER, inProgressOnly: true };
  assert.equal(hasActiveQuickFindFilters(qfFilter), true);
  assert.equal(matchesQuickFindTask(wipLate, qfFilter, false), true);
  assert.equal(matchesQuickFindTask(regularEarly, qfFilter, false), false);

  const nowMs = 1_760_500_000_000;
  const staleProject: ProjectRecord = {
    id: 'proj-stale',
    areaId: 'area-1',
    title: 'Long-Running Architecture Rewrite',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    lastReviewedAt: nowMs - STALLED_PROJECT_THRESHOLD_MS - 5 * 86_400_000,
    sortOrder: 100,
    tagIds: [],
    createdAt: nowMs - 30 * 86_400_000,
    updatedAt: nowMs - 25 * 86_400_000,
    isDeleted: false,
    isDirty: false
  };
  assert.equal(matchesQuickFindProject(staleProject, qfFilter, nowMs, false), false);
  assert.equal(matchesQuickFindProject(staleProject, qfFilter, nowMs, true), true);

  // 5. Stalled project suppression when >= 1 open task isInProgress
  const openIdleTask = makeTask({
    id: 'proj-t1',
    title: 'Draft RFC',
    projectId: staleProject.id,
    createdAt: staleProject.createdAt,
    updatedAt: staleProject.updatedAt,
    isInProgress: false
  });
  const stalledBefore = evaluateProjectStalledStatus(staleProject, [openIdleTask], nowMs);
  assert.equal(stalledBefore.isStalled, true);
  assert.equal(stalledBefore.reason, 'inactive_14d');
  assert.equal(detectStalledProjects([staleProject], [openIdleTask], nowMs).length, 1);

  const openActiveTask = { ...openIdleTask, isInProgress: true };
  const suppressed = evaluateProjectStalledStatus(staleProject, [openActiveTask], nowMs);
  assert.equal(suppressed.isStalled, false);
  assert.equal(suppressed.reason, null);
  assert.equal(detectStalledProjects([staleProject], [openActiveTask], nowMs).length, 0);

  // 6. Roadmap & Area Portfolio inProgressCount tracking
  const todayEp = startOfDayEpoch();
  const heading: HeadingRecord = {
    id: 'hd-1',
    projectId: staleProject.id,
    title: 'Phase 1',
    startDate: todayEp,
    endDate: addDaysLocal(todayEp, 5),
    sortOrder: 100,
    isArchived: false,
    createdAt: nowMs,
    updatedAt: nowMs,
    isDeleted: false,
    isDirty: false
  };
  const rmTasks: TaskRecord[] = [
    makeTask({
      id: 'rm-1',
      title: 'Done step',
      projectId: staleProject.id,
      headingId: heading.id,
      status: 'COMPLETED',
      whenDate: todayEp
    }),
    makeTask({
      id: 'rm-2',
      title: 'WIP step',
      projectId: staleProject.id,
      headingId: heading.id,
      status: 'OPEN',
      isInProgress: true,
      whenDate: addDaysLocal(todayEp, 1)
    }),
    makeTask({
      id: 'rm-3',
      title: 'Queued step',
      projectId: staleProject.id,
      headingId: heading.id,
      status: 'OPEN',
      isInProgress: false,
      whenDate: addDaysLocal(todayEp, 2)
    })
  ];
  const rmLayout = buildProjectRoadmapLayout(staleProject, [heading], rmTasks, todayEp);
  const phaseLane = rmLayout.lanes.find((l) => l.laneId === heading.id)!;
  assert.equal(phaseLane.completedCount, 1);
  assert.equal(phaseLane.inProgressCount, 1);
  assert.equal(phaseLane.totalCount, 3);

  const portfolio = buildAreaPortfolioRoadmapLayout(
    { id: 'area-1', title: 'Engineering' },
    [staleProject],
    [heading],
    rmTasks,
    todayEp
  );
  assert.equal(portfolio.projectLanes.length, 1);
  assert.equal(portfolio.projectLanes[0].completedCount, 1);
  assert.equal(portfolio.projectLanes[0].inProgressCount, 1);
  assert.equal(portfolio.projectLanes[0].totalCount, 3);
});

test('v3.3.0-beta.2 Task Inspector Panel achieves 1:1 parity with Expanded Bento Modal', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const inspectorSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/components/WorkspaceInspectorPanel.tsx'),
    'utf8'
  );
  const appSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/App.tsx'),
    'utf8'
  );

  // Core parity integrations required in WorkspaceInspectorPanel.tsx
  assert.ok(inspectorSrc.includes('onOpenDependenciesModal'));
  assert.ok(inspectorSrc.includes('onOpenRepeatModal'));
  assert.ok(inspectorSrc.includes('AttachmentsStrip'));
  assert.ok(inspectorSrc.includes('MarkdownNotesField'));
  assert.ok(inspectorSrc.includes('generateMagicChecklist'));
  assert.ok(inspectorSrc.includes('parseSmartTaskSyntax'));

  // Additional 1:1 Inspector controls (Smart Syntax strip, Repeat, Waiting On, Timeblock durations, Custom Duration, Phase selector, Checklist reorder/split, Voice & Copy Markdown)
  assert.ok(inspectorSrc.includes('v32-smart-syntax-strip'));
  assert.ok(inspectorSrc.includes('task-waiting-on-chip'));
  assert.ok(inspectorSrc.includes('TIMEBLOCK_DURATION_PRESETS'));
  assert.ok(inspectorSrc.includes('parseCustomDurationMinutes'));
  assert.ok(inspectorSrc.includes('swapChecklistOrder'));
  assert.ok(inspectorSrc.includes('insertChecklistItemAfter'));
  assert.ok(inspectorSrc.includes('processAndAddAttachmentFile'));
  assert.ok(inspectorSrc.includes('SpeechRecognition'));
  assert.ok(inspectorSrc.includes('navigator.clipboard.writeText'));
  assert.ok(inspectorSrc.includes('inspector-heading-select'));
  assert.ok(inspectorSrc.includes('inspector-when-anytime'));

  // App.tsx call site passes full snapshot & modal callbacks to WorkspaceInspectorPanel
  const inspectorCallMatch = appSrc.match(/<WorkspaceInspectorPanel[\s\S]*?\/>/);
  assert.ok(inspectorCallMatch, 'Expected <WorkspaceInspectorPanel /> in App.tsx');
  const callBlock = inspectorCallMatch[0];
  assert.ok(callBlock.includes('headings={snapshot.headings}'));
  assert.ok(callBlock.includes('attachments={snapshot.attachments}'));
  assert.ok(callBlock.includes('openTaskIdSet={openTaskIdSet}'));
  assert.ok(callBlock.includes('tasksByIdMap={tasksByIdMap}'));
  assert.ok(callBlock.includes('geminiApiKey={prefs.geminiApiKey}'));
  assert.ok(callBlock.includes('onToast={showToast}'));
  assert.ok(callBlock.includes('onOpenRepeatModal='));
  assert.ok(callBlock.includes('onOpenDependenciesModal='));
  assert.ok(callBlock.includes('onDuplicateTask={handleDuplicateTask}'));

  // Localized Copy as Markdown across all 5 supported languages
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    assert.ok(TRANSLATIONS[lang].actionCopyMarkdown.trim().length > 0);
    assert.ok(TRANSLATIONS[lang].copiedMarkdownToast.trim().length > 0);
  }
});

test('v3.3 allocateTaskWorkloadMinutesByEpochDay and multi-day workload forecast distribute multi-day tasks across consecutive days', () => {
  // Wednesday 2026-05-13 -> Thursday 2026-05-14 (offset 1) -> Friday 2026-05-15 (offset 2)
  const wedStart = startOfDayEpoch(new Date(2026, 4, 13));
  const thuStart = addDaysLocal(wedStart, 1);
  const friStart = addDaysLocal(wedStart, 2);
  const thuEpoch = localEpochDayFromMillis(thuStart);
  const friEpoch = localEpochDayFromMillis(friStart);

  // Direct pure helper checks
  assert.deepEqual(allocateTaskWorkloadMinutesByEpochDay(0, thuEpoch, null, 480), []);
  assert.deepEqual(allocateTaskWorkloadMinutesByEpochDay(960, thuEpoch, null, 480), [
    { epochDay: thuEpoch, minutes: 480 },
    { epochDay: friEpoch, minutes: 480 }
  ]);
  assert.deepEqual(allocateTaskWorkloadMinutesByEpochDay(960, thuEpoch, thuEpoch, 480), [
    { epochDay: thuEpoch, minutes: 960 }
  ]);
  assert.deepEqual(allocateTaskWorkloadMinutesByEpochDay(1440, thuEpoch, friEpoch, 480), [
    { epochDay: thuEpoch, minutes: 720 },
    { epochDay: friEpoch, minutes: 720 }
  ]);

  // 1. 2d (960m) + 15m on Thursday -> Do = 495m (+15m overload), Fr = 480m (0 overload)
  const task2d = makeTask({
    id: 'thu-2d',
    title: 'Deep 2-day sprint',
    scheduleState: 'UPCOMING',
    whenDate: thuStart,
    durationMinutes: 960
  });
  const task15m = makeTask({
    id: 'thu-15m',
    title: 'Quick check-in',
    scheduleState: 'UPCOMING',
    whenDate: thuStart,
    durationMinutes: 15
  });
  const forecastWith15m = computeMultiDayWorkloadForecast({
    todayTasks: [],
    allOpenTasks: [task2d, task15m],
    todayStartMillis: wedStart,
    daysCount: 7
  });
  assert.equal(forecastWith15m[1].epochDay, thuEpoch);
  assert.equal(forecastWith15m[1].plannedMinutes, 495);
  assert.equal(forecastWith15m[1].overloadedByMinutes, 15);
  assert.equal(forecastWith15m[1].isOverloaded, true);
  assert.equal(forecastWith15m[1].taskCount, 2);

  assert.equal(forecastWith15m[2].epochDay, friEpoch);
  assert.equal(forecastWith15m[2].plannedMinutes, 480);
  assert.equal(forecastWith15m[2].overloadedByMinutes, 0);
  assert.equal(forecastWith15m[2].isOverloaded, false);
  assert.equal(forecastWith15m[2].taskCount, 1);

  // 2. 2d (960m) alone on Thursday -> 480m + 480m, zero overload
  const forecastAlone = computeMultiDayWorkloadForecast({
    todayTasks: [],
    allOpenTasks: [task2d],
    todayStartMillis: wedStart,
    daysCount: 7
  });
  assert.equal(forecastAlone[1].plannedMinutes, 480);
  assert.equal(forecastAlone[1].overloadedByMinutes, 0);
  assert.equal(forecastAlone[1].isOverloaded, false);
  assert.equal(forecastAlone[2].plannedMinutes, 480);
  assert.equal(forecastAlone[2].overloadedByMinutes, 0);
  assert.equal(forecastAlone[2].isOverloaded, false);

  // Single-day computeDayCapacityBreakdown agrees with multi-day slice (480m when not due today, 960m when due today)
  const singleDayNormal = computeDayCapacityBreakdown(
    [task2d],
    [],
    thuStart,
    addDaysLocal(thuStart, 1)
  );
  assert.equal(singleDayNormal.plannedMinutes, 480);

  // 3. 2d (960m) due on Thursday -> 960m compressed onto Thursday
  const task2dDueThu = makeTask({
    id: 'thu-2d-due',
    title: 'Urgent 2-day deliverable',
    scheduleState: 'UPCOMING',
    whenDate: thuStart,
    deadline: thuStart,
    durationMinutes: 960
  });
  const forecastDueSameDay = computeMultiDayWorkloadForecast({
    todayTasks: [],
    allOpenTasks: [task2dDueThu],
    todayStartMillis: wedStart,
    daysCount: 7
  });
  assert.equal(forecastDueSameDay[1].plannedMinutes, 960);
  assert.equal(forecastDueSameDay[1].overloadedByMinutes, 480);
  assert.equal(forecastDueSameDay[2].plannedMinutes, 0);

  const singleDayDue = computeDayCapacityBreakdown(
    [task2dDueThu],
    [],
    thuStart,
    addDaysLocal(thuStart, 1)
  );
  assert.equal(singleDayDue.plannedMinutes, 960);

  // 4. 3d (1440m) due in 2 days (Thursday..Friday) -> 720m + 720m
  const task3dDueFri = makeTask({
    id: 'thu-3d-due-fri',
    title: '3-day release prep due Friday',
    scheduleState: 'UPCOMING',
    whenDate: thuStart,
    deadline: friStart,
    durationMinutes: 1440
  });
  const forecast3dIn2d = computeMultiDayWorkloadForecast({
    todayTasks: [],
    allOpenTasks: [task3dDueFri],
    todayStartMillis: wedStart,
    daysCount: 7
  });
  assert.equal(forecast3dIn2d[1].plannedMinutes, 720);
  assert.equal(forecast3dIn2d[1].overloadedByMinutes, 240);
  assert.equal(forecast3dIn2d[2].plannedMinutes, 720);
  assert.equal(forecast3dIn2d[2].overloadedByMinutes, 240);
  assert.equal(forecast3dIn2d[3].plannedMinutes, 0);

  // 5. Beyond 7 days (e.g. day +10 spilling onto day +11) for Upcoming headers
  const day10Start = addDaysLocal(wedStart, 10);
  const taskBeyond7d = makeTask({
    id: 'day10-2d',
    title: 'Next-month 2d workshop prep',
    scheduleState: 'UPCOMING',
    whenDate: day10Start,
    durationMinutes: 960
  });
  const longForecast = computeMultiDayWorkloadForecast({
    todayTasks: [],
    allOpenTasks: [taskBeyond7d],
    todayStartMillis: wedStart,
    daysCount: 15
  });
  assert.equal(longForecast.length, 15);
  assert.equal(longForecast[10].plannedMinutes, 480);
  assert.equal(longForecast[11].plannedMinutes, 480);
});

test('v3.3 InProgressIcon crisp SVG component is used in TaskItem, WorkspaceInspectorPanel, and App multi-select bar', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const iconSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/components/InProgressIcon.tsx'),
    'utf8'
  );
  const taskItemSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/components/TaskItem.tsx'),
    'utf8'
  );
  const inspectorSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/components/WorkspaceInspectorPanel.tsx'),
    'utf8'
  );
  const appSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/App.tsx'),
    'utf8'
  );

  assert.ok(iconSrc.includes('viewBox="0 0 16 16"'));
  assert.ok(iconSrc.includes('M 8 1.65 A 6.35 6.35 0 0 0 8 14.35 Z'));

  assert.ok(taskItemSrc.includes('<InProgressIcon'));
  assert.match(
    taskItemSrc,
    /data-testid="task-in-progress-btn"[\s\S]*?<InProgressIcon[\s\S]*?size=\{16\}/
  );
  assert.match(
    taskItemSrc,
    /data-testid="task-in-progress-chip"[\s\S]*?<InProgressIcon[\s\S]*?size=\{13\.5\}/
  );

  assert.ok(inspectorSrc.includes('<InProgressIcon'));
  assert.match(
    inspectorSrc,
    /data-testid="inspector-in-progress-btn"[\s\S]*?<InProgressIcon[\s\S]*?size=\{13\.5\}/
  );

  assert.ok(appSrc.includes('<InProgressIcon'));
  assert.match(
    appSrc,
    /data-testid="multi-select-in-progress-btn"[\s\S]*?<InProgressIcon size=\{13\.5\} \/>/
  );
  assert.ok(appSrc.includes('upcomingWorkloadForecast'));
  assert.ok(appSrc.includes('upcomingWorkloadByEpochDay'));
});
