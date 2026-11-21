import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import {
  addDaysLocal,
  startOfDayEpoch,
  toIsoDateInputValue,
  type AreaRecord,
  type TagRecord
} from '../src/domain/types.ts';
import { ThingsLocalDb } from '../src/data/localDb.ts';
import {
  columnIndexToLetters,
  excelSerialToLocalEpoch,
  exportProjectToAsanaCsv,
  exportProjectToAsanaJson,
  exportProjectToPlannerCsv,
  exportProjectToPlannerXlsx,
  importAsanaCsv,
  importAsanaJson,
  importExternalProjectAuto,
  importPlannerCsv,
  importPlannerXlsx,
  localEpochToExcelSerial,
  parseCellRefColumnIndex,
  parseExternalLocalDate,
  writeZipArchive
} from '../src/data/externalProjectCodec.ts';

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

test('Excel serial dates and multi-locale date strings parse cleanly to local midnight', () => {
  // Serial 46154 from 1899-12-30 -> 2026-05-12
  const epoch46154 = excelSerialToLocalEpoch(46154);
  assert.notEqual(epoch46154, null);
  assert.equal(toIsoDateInputValue(epoch46154), '2026-05-12');
  assert.equal(localEpochToExcelSerial(epoch46154!), 46154);

  assert.equal(toIsoDateInputValue(parseExternalLocalDate('46154')), '2026-05-12');
  assert.equal(toIsoDateInputValue(parseExternalLocalDate('2026-05-12')), '2026-05-12');
  assert.equal(toIsoDateInputValue(parseExternalLocalDate('05/12/2026')), '2026-05-12');
  assert.equal(toIsoDateInputValue(parseExternalLocalDate('12.05.2026')), '2026-05-12');
  assert.equal(toIsoDateInputValue(parseExternalLocalDate('25/05/2026')), '2026-05-25');

  // Column coordinate helpers A1..AZ999
  assert.equal(columnIndexToLetters(0), 'A');
  assert.equal(columnIndexToLetters(25), 'Z');
  assert.equal(columnIndexToLetters(26), 'AA');
  assert.equal(columnIndexToLetters(51), 'AZ');
  assert.equal(parseCellRefColumnIndex('A1'), 0);
  assert.equal(parseCellRefColumnIndex('P42'), 15);
  assert.equal(parseCellRefColumnIndex('AZ999'), 51);
});

test('Asana CSV import resolves out-of-order subtasks (by ID & Name), sections, dates, tags, and round-trips', () => {
  const rawAsanaCsv = [
    'Task ID,Created At,Completed At,Last Modified,Name,Section/Column,Assignee,Start Date,Due Date,Tags,Notes,Projects,Parent task',
    // Subtask row BEFORE parent row, matched by Parent task Name
    'sub-early,2026-05-01,,2026-05-01,Verify mobile contrast,Design,, , ,,,Website 3.0,Design Hero Section',
    // Subtask row BEFORE parent row, matched by Parent task ID
    'sub-by-id,2026-05-01,2026-05-02T10:00:00.000Z,2026-05-02,Export Figma tokens,Design,, , ,,,Website 3.0,t-100',
    // Ungrouped top-level task
    't-001,2026-05-01,,2026-05-01,Kickoff alignment,(no section),pm@company.com,2026-05-03,2026-05-05,"Strategy, Q2",Align stakeholders,Website 3.0,',
    // Parent task in Design section
    't-100,2026-05-01,,2026-05-02,Design Hero Section,Design,designer@company.com,2026-05-06,2026-05-15,"UI, Brand","Responsive grid\nand dark mode",Website 3.0,',
    // Completed task in Engineering section
    't-200,2026-05-01,2026-05-10T18:30:00.000Z,2026-05-10,Configure CI pipeline,Engineering,dev@company.com,2026-05-04,2026-05-10,DevOps,GitHub Actions check,Website 3.0,'
  ].join('\r\n');

  const imported = importAsanaCsv(rawAsanaCsv);
  assert.equal(imported.title, 'Website 3.0');
  assert.equal(imported.totalTaskCount, 3);
  assert.equal(imported.ungroupedTasks.length, 1);
  assert.equal(imported.ungroupedTasks[0].title, 'Kickoff alignment');
  assert.equal(imported.ungroupedTasks[0].assigneeId, 'pm@company.com');
  assert.deepEqual(imported.ungroupedTasks[0].tagNames, ['Strategy', 'Q2']);
  assert.equal(toIsoDateInputValue(imported.ungroupedTasks[0].whenDate), '2026-05-03');
  assert.equal(toIsoDateInputValue(imported.ungroupedTasks[0].deadline), '2026-05-05');

  assert.equal(imported.phases.length, 2);
  assert.equal(imported.phases[0].title, 'Design');
  assert.equal(imported.phases[0].tasks.length, 1);
  const designTask = imported.phases[0].tasks[0];
  assert.equal(designTask.title, 'Design Hero Section');
  assert.equal(designTask.notes, 'Responsive grid\nand dark mode');
  assert.equal(designTask.assigneeId, 'designer@company.com');
  assert.deepEqual(designTask.checklistItems, [
    { title: 'Verify mobile contrast', isCompleted: false },
    { title: 'Export Figma tokens', isCompleted: true }
  ]);

  assert.equal(imported.phases[1].title, 'Engineering');
  assert.equal(imported.phases[1].tasks[0].status, 'COMPLETED');
  assert.notEqual(imported.phases[1].tasks[0].completedAt, null);
});

test('Asana JSON supports both REST API { data: [...] } and structured { project, sections, tasks }', () => {
  const apiJson = JSON.stringify({
    data: [
      {
        gid: '9001',
        name: 'Draft launch blog',
        notes: 'Highlight offline PWA capabilities',
        completed: false,
        completed_at: null,
        start_on: '2026-06-01',
        due_on: '2026-06-10',
        assignee: { email: 'writer@acme.io', name: 'Alex Writer' },
        workspace: { gid: 'ws-acme-99', name: 'Acme Workspace' },
        tags: [{ name: 'Marketing' }, { name: 'Content' }],
        memberships: [
          {
            project: { gid: 'p1', name: 'Summer Launch' },
            section: { gid: 's1', name: 'Content & PR' }
          }
        ],
        subtasks: [
          { gid: 'sub1', name: 'Outline key features', completed: true },
          { gid: 'sub2', name: 'Proofread German copy', completed: false }
        ]
      },
      {
        gid: '9002',
        name: 'Approve press list',
        notes: '',
        completed: true,
        completed_at: '2026-05-29T12:00:00.000Z',
        start_on: null,
        due_on: '2026-05-30',
        assignee: null,
        tags: [],
        memberships: [{ project: { gid: 'p1', name: 'Summer Launch' }, section: null }],
        subtasks: []
      }
    ]
  });

  const parsedApi = importAsanaJson(apiJson);
  assert.equal(parsedApi.title, 'Summer Launch');
  assert.equal(parsedApi.workspaceId, 'ws-acme-99');
  assert.equal(parsedApi.totalTaskCount, 2);
  assert.equal(parsedApi.ungroupedTasks.length, 1);
  assert.equal(parsedApi.ungroupedTasks[0].title, 'Approve press list');
  assert.equal(parsedApi.ungroupedTasks[0].status, 'COMPLETED');
  assert.equal(parsedApi.phases.length, 1);
  assert.equal(parsedApi.phases[0].title, 'Content & PR');
  assert.equal(parsedApi.phases[0].tasks[0].assigneeId, 'writer@acme.io');
  assert.deepEqual(parsedApi.phases[0].tasks[0].tagNames, ['Marketing', 'Content']);
  assert.deepEqual(parsedApi.phases[0].tasks[0].checklistItems, [
    { title: 'Outline key features', isCompleted: true },
    { title: 'Proofread German copy', isCompleted: false }
  ]);

  const structuredJson = JSON.stringify({
    project: {
      name: 'Platform Migration',
      notes: 'Zero-downtime rollout',
      tags: ['Infra']
    },
    sections: [
      {
        id: 'sec-prep',
        name: 'Preparation',
        startDate: '2026-07-01',
        endDate: '2026-07-15',
        tasks: [
          {
            title: 'Snapshot database',
            completed: true,
            checklistItems: [{ title: 'Verify checksum', isCompleted: true }]
          }
        ]
      }
    ]
  });

  const parsedStructured = importAsanaJson(structuredJson);
  assert.equal(parsedStructured.title, 'Platform Migration');
  assert.equal(parsedStructured.notes, 'Zero-downtime rollout');
  assert.deepEqual(parsedStructured.tagNames, ['Infra']);
  assert.equal(parsedStructured.phases.length, 1);
  assert.equal(toIsoDateInputValue(parsedStructured.phases[0].startDate), '2026-07-01');
  assert.equal(toIsoDateInputValue(parsedStructured.phases[0].endDate), '2026-07-15');
  assert.equal(parsedStructured.phases[0].tasks[0].title, 'Snapshot database');
  assert.equal(parsedStructured.phases[0].tasks[0].status, 'COMPLETED');
  assert.equal(parsedStructured.phases[0].tasks[0].checklistItems.length, 1);
});

test('Microsoft Planner .xlsx OOXML reader handles Stored, Deflate-Raw, bit-3 Data Descriptors, inlineStr, sharedStrings, and Excel serial dates', async () => {
  const enc = new TextEncoder();

  // Hand-crafted worksheet exercising:
  // - t="s" (sharedStrings.xml)
  // - t="inlineStr" (<is><t>...</t></is>)
  // - numeric Excel serial dates (46154 -> 2026-05-12, 46162 -> 2026-05-20)
  // - sparse cell coordinates (skipping empty columns)
  // - "2/3" numeric ratio in Completed Checklist Items AND item-title matching
  // - Priority -> Urgent (pinned), Important (DEEP_FOCUS), Low (QUICK_WIN)
  const sharedStringsXml = `<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="12" uniqueCount="12">
  <si><t>Task ID</t></si>
  <si><t>Task Name</t></si>
  <si><t>Bucket Name</t></si>
  <si><t>Progress</t></si>
  <si><t>Priority</t></si>
  <si><t>Assigned To</t></si>
  <si><t>Start Date</t></si>
  <si><t>Due Date</t></si>
  <si><t>Description</t></si>
  <si><t>Completed Checklist Items</t></si>
  <si><t>Checklist Items</t></si>
  <si><t>Labels</t></si>
</sst>`;

  const sheetXml = `<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>0</v></c>
      <c r="B1" t="s"><v>1</v></c>
      <c r="C1" t="s"><v>2</v></c>
      <c r="D1" t="s"><v>3</v></c>
      <c r="E1" t="s"><v>4</v></c>
      <c r="F1" t="s"><v>5</v></c>
      <c r="I1" t="s"><v>6</v></c>
      <c r="J1" t="s"><v>7</v></c>
      <c r="M1" t="s"><v>8</v></c>
      <c r="N1" t="s"><v>9</v></c>
      <c r="O1" t="s"><v>10</v></c>
      <c r="P1" t="s"><v>11</v></c>
    </row>
    <row r="2">
      <c r="A2" t="inlineStr"><is><t>pl-1</t></is></c>
      <c r="B2" t="inlineStr"><is><t>Finalize Security Audit</t></is></c>
      <c r="C2" t="inlineStr"><is><t>Compliance</t></is></c>
      <c r="D2" t="inlineStr"><is><t>In progress</t></is></c>
      <c r="E2" t="inlineStr"><is><t>Urgent</t></is></c>
      <c r="F2" t="inlineStr"><is><t>sec@corp.com</t></is></c>
      <c r="I2"><v>46154</v></c>
      <c r="J2"><v>46162</v></c>
      <c r="M2" t="inlineStr"><is><t>SOC2 &amp; GDPR review</t></is></c>
      <c r="N2" t="inlineStr"><is><t>2/3</t></is></c>
      <c r="O2" t="inlineStr"><is><t>Check IAM; Rotate keys; Sign report</t></is></c>
      <c r="P2" t="inlineStr"><is><t>Security; Q2</t></is></c>
    </row>
    <row r="3">
      <c r="A3" t="inlineStr"><is><t>pl-2</t></is></c>
      <c r="B3" t="inlineStr"><is><t>Ship Release Notes</t></is></c>
      <c r="C3" t="inlineStr"><is><t>Launch</t></is></c>
      <c r="D3" t="inlineStr"><is><t>Erledigt</t></is></c>
      <c r="E3" t="inlineStr"><is><t>Important</t></is></c>
      <c r="I3" t="inlineStr"><is><t>15.05.2026</t></is></c>
      <c r="J3" t="inlineStr"><is><t>05/22/2026</t></is></c>
      <c r="N3" t="inlineStr"><is><t>Write EN; Translate DE</t></is></c>
      <c r="O3" t="inlineStr"><is><t>Write EN; Proofread FR; Translate DE</t></is></c>
      <c r="P3" t="inlineStr"><is><t>Docs, Release</t></is></c>
    </row>
    <row r="4">
      <c r="B4" t="inlineStr"><is><t>Update Footer Year</t></is></c>
      <c r="D4" t="inlineStr"><is><t>Not started</t></is></c>
      <c r="E4" t="inlineStr"><is><t>Low</t></is></c>
    </row>
  </sheetData>
</worksheet>`;

  const workbookXml = `<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Enterprise Rollout" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;

  // Build ZIP mixing Stored (method 0), Deflate-Raw (method 8), AND bit-3 Data Descriptor!
  const bit3ZipBytes = await writeZipArchive([
    {
      name: 'xl/workbook.xml',
      data: enc.encode(workbookXml),
      method: 0,
      useDataDescriptor: true
    },
    {
      name: 'xl/sharedStrings.xml',
      data: enc.encode(sharedStringsXml),
      method: 8,
      useDataDescriptor: true
    },
    {
      name: 'xl/worksheets/sheet1.xml',
      data: enc.encode(sheetXml),
      method: 8,
      useDataDescriptor: true
    }
  ]);

  const parsed = await importPlannerXlsx(bit3ZipBytes);
  assert.equal(parsed.title, 'Enterprise Rollout');
  assert.equal(parsed.totalTaskCount, 3);
  assert.equal(parsed.ungroupedTasks.length, 1);
  assert.equal(parsed.ungroupedTasks[0].title, 'Update Footer Year');
  assert.equal(parsed.ungroupedTasks[0].effortLevel, 'QUICK_WIN');
  assert.equal(parsed.ungroupedTasks[0].isPinned, false);

  assert.equal(parsed.phases.length, 2);
  assert.equal(parsed.phases[0].title, 'Compliance');
  const auditTask = parsed.phases[0].tasks[0];
  assert.equal(auditTask.title, 'Finalize Security Audit');
  assert.equal(auditTask.isPinned, true);
  assert.equal(auditTask.assigneeId, 'sec@corp.com');
  assert.equal(auditTask.notes, 'SOC2 & GDPR review');
  assert.equal(toIsoDateInputValue(auditTask.whenDate), '2026-05-12');
  assert.equal(toIsoDateInputValue(auditTask.deadline), '2026-05-20');
  assert.deepEqual(auditTask.tagNames, ['Security', 'Q2']);
  assert.deepEqual(auditTask.checklistItems, [
    { title: 'Check IAM', isCompleted: true },
    { title: 'Rotate keys', isCompleted: true },
    { title: 'Sign report', isCompleted: false }
  ]);

  assert.equal(parsed.phases[1].title, 'Launch');
  const notesTask = parsed.phases[1].tasks[0];
  assert.equal(notesTask.status, 'COMPLETED');
  assert.equal(notesTask.effortLevel, 'DEEP_FOCUS');
  assert.equal(toIsoDateInputValue(notesTask.whenDate), '2026-05-15');
  assert.equal(toIsoDateInputValue(notesTask.deadline), '2026-05-22');
  assert.deepEqual(notesTask.tagNames, ['Docs', 'Release']);
  assert.deepEqual(notesTask.checklistItems, [
    { title: 'Write EN', isCompleted: true },
    { title: 'Proofread FR', isCompleted: false },
    { title: 'Translate DE', isCompleted: true }
  ]);
});

test('Microsoft Planner CSV supports both comma and semicolon delimiters', async () => {
  const commaCsv = [
    'Task ID,Task Name,Bucket Name,Progress,Priority,Assigned To,Created By,Created Date,Start Date,Due Date,Late,Completed Date,Description,Completed Checklist Items,Checklist Items,Labels',
    'id-1,Design Mockups,UI Phase,Not started,Important,mia@studio.de,,,2026-08-01,2026-08-10,false,,Figma components,"Header","Header; Footer","UI; Mobile"',
    'id-2,Ship Beta,QA Phase,100%,Urgent,leo@studio.de,,,2026-08-11,2026-08-15,false,2026-08-14,All green,"","",Release'
  ].join('\r\n');

  const parsedComma = importPlannerCsv(commaCsv, 'Studio App');
  assert.equal(parsedComma.title, 'Studio App');
  assert.equal(parsedComma.phases.length, 2);
  assert.equal(parsedComma.phases[0].tasks[0].effortLevel, 'DEEP_FOCUS');
  assert.deepEqual(parsedComma.phases[0].tasks[0].checklistItems, [
    { title: 'Header', isCompleted: true },
    { title: 'Footer', isCompleted: false }
  ]);
  assert.equal(parsedComma.phases[1].tasks[0].status, 'COMPLETED');
  assert.equal(parsedComma.phases[1].tasks[0].isPinned, true);

  const semicolonCsv = [
    'Task ID;Task Name;Bucket Name;Progress;Priority;Assigned To;Start Date;Due Date;Description;Completed Checklist Items;Checklist Items;Labels',
    'id-9;Kundenbefragung;Forschung;Completed;Low;anna@firma.de;10.09.2026;20.09.2026;Notizen;"Fragebogen";"Fragebogen\nAuswertung";"UX, Kunden"'
  ].join('\r\n');

  const autoDetected = await importExternalProjectAuto(
    new TextEncoder().encode(semicolonCsv),
    'Kundenprojekt.csv'
  );
  assert.equal(autoDetected.title, 'Kundenprojekt');
  assert.equal(autoDetected.phases.length, 1);
  assert.equal(autoDetected.phases[0].title, 'Forschung');
  const t0 = autoDetected.phases[0].tasks[0];
  assert.equal(t0.status, 'COMPLETED');
  assert.equal(t0.effortLevel, 'QUICK_WIN');
  assert.equal(toIsoDateInputValue(t0.whenDate), '2026-09-10');
  assert.equal(toIsoDateInputValue(t0.deadline), '2026-09-20');
  assert.deepEqual(t0.checklistItems, [
    { title: 'Fragebogen', isCompleted: true },
    { title: 'Auswertung', isCompleted: false }
  ]);
  assert.deepEqual(t0.tagNames, ['UX', 'Kunden']);
});

test('End-to-end localDb.importExternalProject + localDb.exportProjectExternal across all 4 formats with category reuse & workspaceId/assigneeId preservation', async () => {
  const { opener, stores } = createInMemoryIdb();
  const now = Date.now();
  const todayEpoch = startOfDayEpoch(new Date(now));
  const futureEpoch = addDaysLocal(todayEpoch, 7);
  const futureIso = toIsoDateInputValue(futureEpoch);
  const todayIso = toIsoDateInputValue(todayEpoch);

  // Seed one active Area and one existing Category ("Design") to verify case-insensitive reuse
  const existingArea: AreaRecord = {
    id: 'area-work',
    title: 'Work',
    sortOrder: 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const existingDesignTag: TagRecord = {
    id: 'tag-existing-design',
    title: 'Design',
    parentTagId: null,
    shortcut: null,
    sortOrder: 100,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  stores.get('areas')!.set(existingArea.id, existingArea);
  stores.get('tags')!.set(existingDesignTag.id, existingDesignTag);

  const db = new ThingsLocalDb(opener);
  await db.initialize('en');

  const sourceJson = JSON.stringify({
    project: {
      name: 'Q4 Cross-Platform Sync',
      notes: 'Asana & Planner interoperability milestone',
      workspace: { gid: 'ws-enterprise-42' },
      tags: [{ name: 'design' }, { name: 'Roadmap' }]
    },
    sections: [
      {
        gid: 'sec-1',
        name: 'Architecture',
        start_on: todayIso,
        due_on: futureIso
      },
      {
        gid: 'sec-2',
        name: 'Verification',
        start_on: null,
        due_on: futureIso
      }
    ],
    data: [
      {
        gid: 'tk-ungrouped',
        name: 'Publish milestone brief',
        notes: 'Share with leadership',
        completed: false,
        start_on: todayIso,
        due_on: futureIso,
        is_pinned: true,
        effort_level: null,
        assignee: { name: 'lead@thingsbook.app' },
        workspace: { gid: 'ws-enterprise-42' },
        tags: [{ name: 'DESIGN' }],
        memberships: [{ project: { name: 'Q4 Cross-Platform Sync' }, section: null }],
        subtasks: [
          { name: 'Confirm KPIs', completed: true },
          { name: 'Send calendar invite', completed: false }
        ]
      },
      {
        gid: 'tk-arch',
        name: 'Build OOXML ZIP codec',
        notes: 'Zero dependencies',
        completed: false,
        start_on: futureIso,
        due_on: futureIso,
        is_pinned: false,
        effort_level: 'DEEP_FOCUS',
        assignee: { name: 'eng@thingsbook.app' },
        workspace: { gid: 'ws-enterprise-42' },
        tags: [{ name: 'Backend' }],
        memberships: [
          {
            project: { name: 'Q4 Cross-Platform Sync' },
            section: { gid: 'sec-1', name: 'Architecture' }
          }
        ],
        subtasks: []
      },
      {
        gid: 'tk-done',
        name: 'Baseline schema check',
        notes: '',
        completed: true,
        completed_at: '2026-05-01T09:00:00.000Z',
        start_on: null,
        due_on: null,
        is_pinned: false,
        effort_level: 'QUICK_WIN',
        assignee: { name: 'qa@thingsbook.app' },
        workspace: { gid: 'ws-enterprise-42' },
        tags: [],
        memberships: [
          {
            project: { name: 'Q4 Cross-Platform Sync' },
            section: { gid: 'sec-2', name: 'Verification' }
          }
        ],
        subtasks: [{ name: 'Run unit suite', completed: true }]
      }
    ]
  });

  const parsedInitial = await importExternalProjectAuto(
    new TextEncoder().encode(sourceJson),
    'q4-sync.json'
  );
  const importRes = await db.importExternalProject(parsedInitial, 'area-work');

  assert.equal(importRes.title, 'Q4 Cross-Platform Sync');
  assert.equal(importRes.taskCount, 3);

  const snap = db.getSnapshot();
  const createdProj = snap.projects.find((p) => p.id === importRes.projectId);
  assert.ok(createdProj);
  assert.equal(createdProj.areaId, 'area-work');
  assert.equal(createdProj.status, 'ACTIVE');
  assert.equal(createdProj.scheduleState, 'TODAY');
  assert.equal(createdProj.workspaceId, 'ws-enterprise-42');
  assert.equal(createdProj.isDirty, true);

  // Verify existing "Design" tag was reused (no duplicate "design"/"DESIGN" created)
  const activeTags = snap.tags.filter((t) => !t.isDeleted);
  assert.equal(activeTags.filter((t) => t.title.toLowerCase() === 'design').length, 1);
  assert.ok(createdProj.tagIds.includes('tag-existing-design'));
  assert.equal(activeTags.length, 3); // Design (reused) + Roadmap + Backend

  // Verify project-task invariant (areaId === null on all project tasks) + scheduleState calculation
  const projTasks = snap.tasks.filter((t) => t.projectId === createdProj.id);
  assert.equal(projTasks.length, 3);
  for (const tk of projTasks) {
    assert.equal(tk.areaId, null);
    assert.equal(tk.workspaceId, 'ws-enterprise-42');
    assert.equal(tk.isDirty, true);
  }

  const briefTask = projTasks.find((t) => t.title === 'Publish milestone brief')!;
  assert.equal(briefTask.scheduleState, 'TODAY');
  assert.equal(briefTask.assigneeId, 'lead@thingsbook.app');
  assert.equal(briefTask.isPinned, true);
  assert.deepEqual(briefTask.tagIds, ['tag-existing-design']);

  const codecTask = projTasks.find((t) => t.title === 'Build OOXML ZIP codec')!;
  assert.equal(codecTask.scheduleState, 'UPCOMING');
  assert.equal(codecTask.effortLevel, 'DEEP_FOCUS');
  assert.equal(codecTask.assigneeId, 'eng@thingsbook.app');

  const doneTask = projTasks.find((t) => t.title === 'Baseline schema check')!;
  assert.equal(doneTask.status, 'COMPLETED');
  assert.equal(doneTask.scheduleState, 'ANYTIME');
  assert.equal(doneTask.assigneeId, 'qa@thingsbook.app');

  // Round-trip Format 1: Asana JSON
  const exportedJson = await db.exportProjectExternal(createdProj.id, 'asana-json');
  assert.ok(exportedJson.filename.endsWith('-asana.json'));
  const reimportedFromJson = await importExternalProjectAuto(
    exportedJson.bytes,
    exportedJson.filename
  );
  assert.equal(reimportedFromJson.title, 'Q4 Cross-Platform Sync');
  assert.equal(reimportedFromJson.notes, 'Asana & Planner interoperability milestone');
  assert.equal(reimportedFromJson.workspaceId, 'ws-enterprise-42');
  assert.equal(reimportedFromJson.totalTaskCount, 3);
  assert.equal(reimportedFromJson.phases.length, 2);
  assert.equal(toIsoDateInputValue(reimportedFromJson.phases[0].startDate), todayIso);
  assert.equal(toIsoDateInputValue(reimportedFromJson.phases[0].endDate), futureIso);
  assert.equal(reimportedFromJson.ungroupedTasks[0].assigneeId, 'lead@thingsbook.app');
  assert.equal(reimportedFromJson.ungroupedTasks[0].isPinned, true);

  // Round-trip Format 2: Asana CSV
  const exportedAsanaCsv = await db.exportProjectExternal(createdProj.id, 'asana-csv');
  assert.ok(exportedAsanaCsv.filename.endsWith('-asana.csv'));
  assert.equal(exportedAsanaCsv.bytes[0], 0xef);
  assert.equal(exportedAsanaCsv.bytes[1], 0xbb);
  assert.equal(exportedAsanaCsv.bytes[2], 0xbf);
  const csvText = new TextDecoder('utf-8', { ignoreBOM: true }).decode(exportedAsanaCsv.bytes);
  assert.ok(csvText.startsWith('\uFEFF'));
  const reimportedFromAsanaCsv = await importExternalProjectAuto(
    exportedAsanaCsv.bytes,
    exportedAsanaCsv.filename
  );
  assert.equal(reimportedFromAsanaCsv.title, 'Q4 Cross-Platform Sync');
  assert.equal(reimportedFromAsanaCsv.totalTaskCount, 3);
  assert.deepEqual(reimportedFromAsanaCsv.ungroupedTasks[0].checklistItems, [
    { title: 'Confirm KPIs', isCompleted: true },
    { title: 'Send calendar invite', isCompleted: false }
  ]);
  assert.equal(reimportedFromAsanaCsv.phases[1].tasks[0].status, 'COMPLETED');

  // Round-trip Format 3: Microsoft Planner .xlsx (Compressed Deflate-Raw OOXML)
  const exportedXlsx = await db.exportProjectExternal(createdProj.id, 'planner-xlsx');
  assert.ok(exportedXlsx.filename.endsWith('-planner.xlsx'));
  assert.equal(exportedXlsx.bytes[0], 0x50); // 'P'
  assert.equal(exportedXlsx.bytes[1], 0x4b); // 'K'
  const reimportedFromXlsx = await importExternalProjectAuto(
    exportedXlsx.bytes,
    exportedXlsx.filename
  );
  assert.equal(reimportedFromXlsx.title, 'Q4 Cross-Platform Sync');
  assert.equal(reimportedFromXlsx.notes, 'Asana & Planner interoperability milestone');
  assert.equal(reimportedFromXlsx.totalTaskCount, 3);
  assert.equal(reimportedFromXlsx.ungroupedTasks[0].isPinned, true);
  assert.equal(reimportedFromXlsx.ungroupedTasks[0].assigneeId, 'lead@thingsbook.app');
  assert.deepEqual(reimportedFromXlsx.ungroupedTasks[0].checklistItems, [
    { title: 'Confirm KPIs', isCompleted: true },
    { title: 'Send calendar invite', isCompleted: false }
  ]);
  assert.equal(reimportedFromXlsx.phases[0].tasks[0].effortLevel, 'DEEP_FOCUS');
  assert.equal(reimportedFromXlsx.phases[1].tasks[0].status, 'COMPLETED');
  assert.equal(reimportedFromXlsx.phases[1].tasks[0].effortLevel, 'QUICK_WIN');

  // Also verify Stored (uncompressed) .xlsx export/import directly
  const storedXlsxBytes = await exportProjectToPlannerXlsx(
    {
      project: createdProj,
      headings: snap.headings.filter((h) => h.projectId === createdProj.id),
      tasks: projTasks,
      checklistItems: snap.checklistItems,
      tags: activeTags
    },
    { compress: false }
  );
  const reimportedStored = await importPlannerXlsx(storedXlsxBytes);
  assert.equal(reimportedStored.totalTaskCount, 3);

  // Also verify direct codec export helpers return non-empty strings
  assert.ok(
    exportProjectToAsanaCsv({
      project: createdProj,
      headings: snap.headings,
      tasks: projTasks,
      checklistItems: snap.checklistItems,
      tags: activeTags
    }).length > 50
  );
  assert.ok(
    exportProjectToAsanaJson({
      project: createdProj,
      headings: snap.headings,
      tasks: projTasks,
      checklistItems: snap.checklistItems,
      tags: activeTags
    }).length > 50
  );
  assert.ok(
    exportProjectToPlannerCsv({
      project: createdProj,
      headings: snap.headings,
      tasks: projTasks,
      checklistItems: snap.checklistItems,
      tags: activeTags
    }).length > 50
  );

  // Round-trip Format 4: Microsoft Planner CSV
  const exportedPlannerCsv = await db.exportProjectExternal(createdProj.id, 'planner-csv');
  assert.ok(exportedPlannerCsv.filename.endsWith('-planner.csv'));
  const reimportedFromPlannerCsv = await importExternalProjectAuto(
    exportedPlannerCsv.bytes,
    exportedPlannerCsv.filename,
    createdProj.title
  );
  assert.equal(reimportedFromPlannerCsv.title, 'Q4 Cross-Platform Sync');
  assert.equal(reimportedFromPlannerCsv.totalTaskCount, 3);
  assert.equal(reimportedFromPlannerCsv.phases[1].tasks[0].status, 'COMPLETED');
});

test('Localized German Asana CSV (Aufgaben-ID, Abschnitt/Spalte, Fälligkeitsdatum, Übergeordnete Aufgabe) imports sections, dates, notes, tags & subtasks', () => {
  const germanAsanaCsv = [
    '\uFEFFAufgaben-ID,Erstellt am,Abgeschlossen am,Zuletzt geändert,Name,Abschnitt/Spalte,Verantwortlich,Startdatum,Fälligkeitsdatum,Tags,Notizen,Projekte,Übergeordnete Aufgabe',
    '9001,2026-11-01,,,Migrationskonzept,Planung,petros@corp.de,2026-11-22,2026-11-27,"IT, Infrastruktur",DFS & Exchange Umstellung,Informationsverbund 2.0,',
    '9002,2026-11-02,2026-11-10,,DFS Migration,Planung,,,,,,Informationsverbund 2.0,Migrationskonzept',
    '9003,2026-11-02,,,Client Umstellung,Planung,,,,,,Informationsverbund 2.0,9001',
    '9004,2026-11-03,,,Kickoff-Protokoll,Unbenannter Abschnitt,,2026-11-20,,,,Informationsverbund 2.0,'
  ].join('\n');

  const parsed = importAsanaCsv(germanAsanaCsv);
  assert.equal(parsed.title, 'Informationsverbund 2.0');
  assert.equal(parsed.ungroupedTasks.length, 1);
  assert.equal(parsed.ungroupedTasks[0].title, 'Kickoff-Protokoll');
  assert.equal(parsed.phases.length, 1);
  assert.equal(parsed.phases[0].title, 'Planung');
  const mainTask = parsed.phases[0].tasks[0];
  assert.equal(mainTask.title, 'Migrationskonzept');
  assert.equal(mainTask.notes, 'DFS & Exchange Umstellung');
  assert.equal(toIsoDateInputValue(mainTask.whenDate), '2026-11-22');
  assert.equal(toIsoDateInputValue(mainTask.deadline), '2026-11-27');
  assert.deepEqual(mainTask.tagNames, ['IT', 'Infrastruktur']);
  assert.deepEqual(mainTask.checklistItems, [
    { title: 'DFS Migration', isCompleted: true },
    { title: 'Client Umstellung', isCompleted: false }
  ]);
});

