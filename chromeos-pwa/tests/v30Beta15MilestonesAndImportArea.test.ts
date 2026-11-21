import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { IDBPDatabase } from 'idb';
import {
  APP_VERSION,
  addDaysLocal,
  startOfDayEpoch,
  type HeadingRecord,
  type ProjectRecord,
  type SupportedLang,
  type TaskRecord
} from '../src/domain/types.ts';
import { TRANSLATIONS } from '../src/i18n/translations.ts';
import {
  DEFAULT_PREFS,
  ThingsLocalDb,
  normalizeTask
} from '../src/data/localDb.ts';
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
import {
  exportProjectToAsanaJson,
  importAsanaJson
} from '../src/data/externalProjectCodec.ts';
import {
  buildProjectTemplateBlueprint,
  parseProjectOutlineInput,
  parseTemplateBlueprintJson
} from '../src/domain/v2Utils.ts';
import {
  buildProjectRoadmapLayout,
  exportProjectRoadmapPdf
} from '../src/domain/v30Roadmap.ts';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.resolve(__dirname, '..', 'src');

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
    scheduledStartMinutes: null,
    durationMinutes: null,
    completedAt: null,
    projectId: null,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 1,
    tagIds: [],
    effortLevel: null,
    isPinned: false,
    isMilestone: false,
    blockedByTaskIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false,
    ...overrides
  };
}

test('Milestone & External Import Area translations are defined across all 5 languages (en, de, fr, it, es)', () => {
  const langs: SupportedLang[] = ['en', 'de', 'fr', 'it', 'es'];
  for (const lang of langs) {
    const tr = TRANSLATIONS[lang];
    assert.ok(tr.milestoneLabel.trim().length > 0, `Missing milestoneLabel in ${lang}`);
    assert.ok(tr.markAsMilestone.trim().length > 0, `Missing markAsMilestone in ${lang}`);
    assert.ok(tr.removeMilestone.trim().length > 0, `Missing removeMilestone in ${lang}`);
    assert.ok(tr.externalImportTargetArea.trim().length > 0, `Missing externalImportTargetArea in ${lang}`);
    assert.ok(tr.areasNone.trim().length > 0, `Missing areasNone in ${lang}`);
  }
  assert.equal(TRANSLATIONS.en.milestoneLabel, 'Milestone');
  assert.equal(TRANSLATIONS.de.milestoneLabel, 'Meilenstein');
  assert.equal(TRANSLATIONS.fr.milestoneLabel, 'Jalon');
  assert.equal(TRANSLATIONS.it.milestoneLabel, 'Traguardo');
  assert.equal(TRANSLATIONS.es.milestoneLabel, 'Hito');
  assert.ok(APP_VERSION.startsWith('3.0.'));
});

test('localDb persists isMilestone across normalizeTask, upsertTask, batchDuplicateTasks, blueprint instantiation, and Asana import', async () => {
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const normalized = normalizeTask({
    ...makeTask({ id: 'legacy-no-flag' }),
    isMilestone: undefined
  });
  assert.equal(normalized.isMilestone, false);

  const area = await db.upsertArea({ title: 'Engineering' });
  const areaId = typeof area === 'string' ? area : area.id;
  const proj = await db.upsertProject({ title: 'Release 3.0', areaId });
  const projId = typeof proj === 'string' ? proj : proj.id;
  const msTask = await db.upsertTask({
    title: 'Go-Live Signoff',
    projectId: projId,
    isMilestone: true
  });
  const msTaskId = typeof msTask === 'string' ? msTask : msTask.id;

  let saved = db.getSnapshot().tasks.find((t) => t.id === msTaskId);
  assert.equal(saved?.isMilestone, true);

  // Partial update without isMilestone must preserve existing true milestone state
  await db.upsertTask({ id: msTaskId, notes: 'Security & QA signoff complete' });
  saved = db.getSnapshot().tasks.find((t) => t.id === msTaskId);
  assert.equal(saved?.isMilestone, true);

  // Duplicating task preserves isMilestone
  const dupIds = await db.batchDuplicateTasks([msTaskId]);
  assert.equal(dupIds.length, 1);
  const dupTask = db.getSnapshot().tasks.find((t) => t.id === dupIds[0]);
  assert.equal(dupTask?.isMilestone, true);

  // Blueprint instantiation preserves isMilestone
  const outline = parseProjectOutlineInput(
    `# Launch Plan\n## Phase 1\n- [ ] Architecture Review @milestone\n- [ ] Build API @45m`,
    'Untitled'
  );
  const bpProjId = await db.instantiateProjectFromBlueprint({
    title: outline.title,
    notes: outline.notes,
    areaId,
    blueprint: outline.blueprint
  });
  const bpTasks = db.getSnapshot().tasks.filter((t) => t.projectId === bpProjId);
  const archTask = bpTasks.find((t) => t.title === 'Architecture Review');
  const apiTask = bpTasks.find((t) => t.title === 'Build API');
  assert.equal(archTask?.isMilestone, true);
  assert.equal(apiTask?.isMilestone, false);

  // External Asana JSON import into preselected Area preserves isMilestone & areaId
  const asanaParsed = importAsanaJson(
    JSON.stringify({
      project: { name: 'Asana Launch' },
      tasks: [
        { gid: 'a1', name: 'Production Cutover', resource_subtype: 'milestone', due_on: '2026-12-01' },
        { gid: 'a2', name: 'Smoke Test Suite', resource_subtype: 'default_task', due_on: '2026-11-28' }
      ]
    })
  );
  const extRes = await db.importExternalProject(asanaParsed, areaId);
  const extProj = db.getSnapshot().projects.find((p) => p.id === extRes.projectId);
  assert.equal(extProj?.areaId, areaId);
  const extTasks = db.getSnapshot().tasks.filter((t) => t.projectId === extRes.projectId);
  assert.equal(extTasks.find((t) => t.title === 'Production Cutover')?.isMilestone, true);
  assert.equal(extTasks.find((t) => t.title === 'Smoke Test Suite')?.isMilestone, false);
});

test('Encrypted .tbbackup round-trip and Firestore syncUtils preserve isMilestone (including legacy peers omitting isMilestone)', async () => {
  const tMilestone = makeTask({ id: 't-ms', title: 'GA Release', isMilestone: true });
  const tRegular = makeTask({ id: 't-reg', title: 'Write Docs', isMilestone: false });

  // 1. .tbbackup round-trip
  const payload = buildBackupPayloadFromEntities({
    areas: [],
    projects: [],
    headings: [],
    tasks: [tMilestone, tRegular],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: [],
    prefs: DEFAULT_PREFS
  });
  const encryptedBytes = await encodeThingsBackup(payload, 'milestone-pass-2026');
  const decodedPayload = await decodeThingsBackup(encryptedBytes, 'milestone-pass-2026');
  const entities = normalizeBackupPayloadToEntities(decodedPayload);
  assert.equal(entities.tasks.find((t) => t.id === 't-ms')?.isMilestone, true);
  assert.equal(entities.tasks.find((t) => t.id === 't-reg')?.isMilestone, false);

  // 2. Firestore mapper serialization + legacy peer fallback
  const fsMap = taskToFirestoreMap(tMilestone);
  assert.equal(fsMap.isMilestone, true);

  const roundTrip = taskFromFirestoreMap('t-ms', fsMap);
  assert.equal(roundTrip.isMilestone, true);

  // Older peer omits isMilestone completely -> existingLocal.isMilestone === true MUST NOT be wiped!
  const legacyRemoteMap = { ...fsMap };
  delete legacyRemoteMap.isMilestone;
  legacyRemoteMap.updatedAt = Date.now() + 60_000;
  legacyRemoteMap.title = 'GA Release (Updated Title)';

  const fromLegacyWithLocal = taskFromFirestoreMap('t-ms', legacyRemoteMap, tMilestone);
  assert.equal(fromLegacyWithLocal.isMilestone, true);
  assert.equal(fromLegacyWithLocal.omittedMilestoneField, true);

  // Verify applyRemoteDeltas in ThingsLocalDb also preserves local milestone when omittedMilestoneField is true
  const { opener } = createInMemoryIdb();
  const db = new ThingsLocalDb(opener);
  await db.initialize('en');
  await db.upsertTask({ id: 't-ms', title: 'GA Release', isMilestone: true });

  const parsedWithoutLocalRef = taskFromFirestoreMap('t-ms', legacyRemoteMap);
  await db.applyRemoteDeltas({
    areas: [],
    projects: [],
    headings: [],
    tasks: [parsedWithoutLocalRef],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: []
  });
  const afterDelta = db.getSnapshot().tasks.find((t) => t.id === 't-ms');
  assert.equal(afterDelta?.title, 'GA Release (Updated Title)');
  assert.equal(afterDelta?.isMilestone, true);
});

test('Asana JSON import/export and Markdown Outline (@milestone, [milestone], ◆) round-trip isMilestone', () => {
  // Outline tokens in any order with @duration
  const parsed = parseProjectOutlineInput(
    [
      '# Milestone Outline',
      '- [ ] Kickoff Gate @milestone',
      '- [ ] Security Signoff [milestone] @30m',
      '- [ ] Launch Readiness @1h @milestone',
      '- [ ] ◆ Public Keynote',
      '- [ ] Regular Implementation Task @45m'
    ].join('\n'),
    'Fallback'
  );
  const top = parsed.blueprint.tasks ?? [];
  assert.equal(top.length, 5);
  assert.deepEqual(
    top.map((tk) => ({ title: tk.title, isMilestone: Boolean(tk.isMilestone), dur: tk.durationMinutes ?? null })),
    [
      { title: 'Kickoff Gate', isMilestone: true, dur: null },
      { title: 'Security Signoff', isMilestone: true, dur: 30 },
      { title: 'Launch Readiness', isMilestone: true, dur: 60 },
      { title: 'Public Keynote', isMilestone: true, dur: null },
      { title: 'Regular Implementation Task', isMilestone: false, dur: 45 }
    ]
  );

  // Template blueprint JSON round-trip
  const proj: ProjectRecord = {
    id: 'p1',
    areaId: null,
    title: 'Launch',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    lastReviewedAt: null,
    sortOrder: 1,
    tagIds: [],
    createdAt: 1,
    updatedAt: 1,
    isDeleted: false,
    isDirty: false
  };
  const bpJson = buildProjectTemplateBlueprint(
    proj,
    [],
    [
      makeTask({ id: 'tk1', projectId: 'p1', title: 'Alpha Gate', isMilestone: true }),
      makeTask({ id: 'tk2', projectId: 'p1', title: 'Coding', isMilestone: false })
    ],
    []
  );
  const reparsedBp = parseTemplateBlueprintJson(JSON.stringify(bpJson));
  assert.equal(reparsedBp.tasks?.[0]?.isMilestone, true);
  assert.equal(reparsedBp.tasks?.[1]?.isMilestone, undefined);

  // Asana JSON export -> import round-trip
  const jsonText = exportProjectToAsanaJson({
    project: proj,
    headings: [],
    tasks: [
      makeTask({ id: 'tk1', projectId: 'p1', title: 'Alpha Gate', isMilestone: true }),
      makeTask({ id: 'tk2', projectId: 'p1', title: 'Coding', isMilestone: false })
    ],
    checklistItems: [],
    tags: []
  });
  const rawJson = JSON.parse(jsonText) as {
    data?: Array<{ name: string; resource_subtype: string }>;
    tasks?: Array<{ name: string; resource_subtype: string }>;
  };
  const exportedTasks = rawJson.data ?? rawJson.tasks ?? [];
  assert.equal(exportedTasks[0].resource_subtype, 'milestone');
  assert.equal(exportedTasks[1].resource_subtype, 'default_task');

  const reimported = importAsanaJson(jsonText);
  assert.equal(reimported.ungroupedTasks[0].isMilestone, true);
  assert.equal(reimported.ungroupedTasks[1].isMilestone, false);
});

test('buildProjectRoadmapLayout anchors Milestones as 1-day checkpoints at target date and exportProjectRoadmapPdf draws royal-violet diamond', () => {
  const today = startOfDayEpoch(new Date(2026, 10, 20));
  const targetDeadline = addDaysLocal(today, 6);
  const proj: ProjectRecord = {
    id: 'p-road',
    areaId: null,
    title: 'Q4 Launch Roadmap',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'TODAY',
    whenDate: today,
    deadline: addDaysLocal(today, 10),
    completedAt: null,
    lastReviewedAt: null,
    sortOrder: 1,
    tagIds: [],
    viewMode: 'roadmap',
    createdAt: today,
    updatedAt: today,
    isDeleted: false,
    isDirty: false
  };
  const heading: HeadingRecord = {
    id: 'h1',
    projectId: 'p-road',
    title: 'Phase 1',
    startDate: today,
    endDate: addDaysLocal(today, 8),
    sortOrder: 1,
    createdAt: today,
    updatedAt: today,
    isDeleted: false,
    isDirty: false
  };

  // Even if whenDate is earlier and durationMinutes is 5d (2400m), a Milestone anchors strictly on its target day (deadline ?? whenDate) with spanDays === 1!
  const msTask = makeTask({
    id: 'ms-1',
    projectId: 'p-road',
    headingId: 'h1',
    title: 'Production Cutover',
    whenDate: today,
    deadline: targetDeadline,
    durationMinutes: 2400,
    isMilestone: true
  });
  const regTask = makeTask({
    id: 'reg-1',
    projectId: 'p-road',
    headingId: 'h1',
    title: 'Prepare Runbook',
    whenDate: today,
    deadline: addDaysLocal(today, 3),
    isMilestone: false
  });

  const layout = buildProjectRoadmapLayout(proj, [heading], [regTask, msTask], today);
  const plottedMs = layout.lanes[0].plottedTasks.find((p) => p.task.id === 'ms-1');
  assert.ok(plottedMs, 'Milestone must be plotted on Roadmap');
  assert.equal(plottedMs.isMilestone, true);
  assert.equal(plottedMs.spanDays, 1);
  assert.equal(plottedMs.startEpoch, targetDeadline);
  assert.equal(plottedMs.endEpoch, targetDeadline);
  assert.equal(plottedMs.startDayIndex, plottedMs.endDayIndex);

  const pdfBytes = exportProjectRoadmapPdf(layout, proj.title, {}, {
    orientation: 'landscape'
  });
  const pdfAscii = new TextDecoder('latin1').decode(pdfBytes);
  assert.ok(pdfAscii.startsWith('%PDF-1.4'), 'Valid PDF 1.4 header expected');
  // Royal violet #7C3AED -> fmtNum(124/255)=0.49, fmtNum(58/255)=0.23, fmtNum(237/255)=0.93 + closed 4-vertex diamond polygon (l h B)
  assert.ok(
    pdfAscii.includes('0.49 0.23 0.93 rg') && pdfAscii.includes('l h B'),
    'PDF stream must contain royal-violet #7C3AED fill and closed 45-degree vector diamond polygon'
  );
  assert.ok(
    pdfAscii.includes('Production Cutover'),
    'PDF stream must include Milestone callout label'
  );
});

test('UI components wire Target Area Preselector, Milestone controls, Roadmap diamond marker, and macOS 1:1 non-sticky single-click selection', () => {
  const modalSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components', 'TemplatesAndImportModal.tsx'),
    'utf8'
  );
  const sidebarSrc = fs.readFileSync(path.join(SRC_DIR, 'components', 'Sidebar.tsx'), 'utf8');
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components', 'TaskItem.tsx'), 'utf8');
  const roadmapSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components', 'ProjectBoardAndRoadmap.tsx'),
    'utf8'
  );
  const todayTlSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components', 'TodayTimelineView.tsx'),
    'utf8'
  );
  const cssSrc = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  // Feature 1: Target Area Preselection & In-Card Area Selector
  assert.ok(modalSrc.includes('data-testid="external-import-area-selector"'));
  assert.ok(modalSrc.includes('t.externalImportTargetArea'));
  assert.ok(modalSrc.includes('t.areasNone'));
  assert.ok(!modalSrc.includes('>\n                —\n              </button>'));
  assert.ok(sidebarSrc.includes('data-testid="sidebar-area-ctx-import-external"'));
  assert.ok(sidebarSrc.includes('data-testid="sidebar-import-external-btn"'));

  // Feature 2: Milestone UI Controls & Roadmap Diamond
  assert.ok(cssSrc.includes('--milestone-violet: #7c3aed;'));
  assert.ok(cssSrc.includes('--milestone-violet: #a78bfa;'));
  assert.ok(cssSrc.includes('.things-checkbox.milestone'));
  assert.ok(cssSrc.includes('.roadmap-milestone-diamond'));
  assert.ok(cssSrc.includes('.roadmap-milestone-label'));
  assert.ok(!cssSrc.includes('container-type:'));

  assert.ok(taskItemSrc.includes('data-testid="task-milestone-btn"'));
  assert.ok(taskItemSrc.includes('data-testid="task-milestone-chip"'));
  assert.ok(taskItemSrc.includes('data-testid="task-row-milestone-pill"'));
  assert.ok(taskItemSrc.includes('data-testid="task-ctx-milestone-btn"'));

  assert.ok(roadmapSrc.includes('data-milestone={isMilestoneTask ? \'true\' : undefined}'));
  assert.ok(roadmapSrc.includes('roadmap-milestone-diamond'));
  assert.ok(roadmapSrc.includes('roadmap-milestone-label'));
  assert.ok(todayTlSrc.includes('milestone-item'));

  // Selection Parity: isExplicitMultiSelect prevents sticky multi-select on plain single-click
  assert.ok(appSrc.includes('const [isExplicitMultiSelect, setIsExplicitMultiSelect] = useState(false);'));
  assert.ok(appSrc.includes('isMultiSelectMode={isExplicitMultiSelect}'));
  assert.ok(
    appSrc.includes(
      '{!isAnyTimelineFullScreen && selectedTaskIds.length > 0 && !expandedTaskId && (selectedTaskIds.length > 1 || isExplicitMultiSelect)'
    )
  );
});
