import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ATTACHMENT_BATCH_CHUNK_SIZE,
  CLOCK_SKEW_OVERLAP_MS,
  MAX_ATTACHMENT_INLINE_BYTES,
  MAX_FUTURE_CURSOR_DRIFT_MS,
  MAX_NTFY_PAYLOAD_BYTES,
  attachmentToFirestoreMap,
  buildBoundedNtfyRelayPayload,
  chunkItems,
  clampRemoteCursorTimestamp,
  computeEffectivePullCursor
} from '../src/data/syncUtils.ts';
import {
  detectCsvDelimiter,
  escapeCsvSemicolonItem,
  escapeCsvTagItem,
  parseCsvTaskImport,
  parseFlexibleDate,
  serializeEntitiesToCsv,
  splitEscapedCsvList
} from '../src/data/csvCodec.ts';
import type {
  AreaRecord,
  AttachmentRecord,
  ChecklistItemRecord,
  ProjectRecord,
  TagRecord,
  TaskRecord
} from '../src/domain/types.ts';

test('syncUtils enforces 120s clock-skew overlap and +60s future-timestamp cursor clamping', () => {
  assert.equal(CLOCK_SKEW_OVERLAP_MS, 120_000);
  assert.equal(MAX_FUTURE_CURSOR_DRIFT_MS, 60_000);

  // Full pull or initial pull stays 0
  assert.equal(computeEffectivePullCursor(0, false), 0);
  assert.equal(computeEffectivePullCursor(500_000, true), 0);
  // 120s subtraction clamps cleanly at 0
  assert.equal(computeEffectivePullCursor(90_000, false), 0);
  assert.equal(computeEffectivePullCursor(300_000, false), 180_000);

  // Future-timestamp clamping prevents fast peer clock from poisoning lastSyncAt
  const localNow = 1_763_000_000_000;
  assert.equal(clampRemoteCursorTimestamp(localNow - 5_000, localNow), localNow - 5_000);
  assert.equal(clampRemoteCursorTimestamp(localNow + 30_000, localNow), localNow + 30_000);
  assert.equal(
    clampRemoteCursorTimestamp(localNow + 86_400_000, localNow),
    localNow + 60_000
  );
});

test('syncUtils chunks attachments into 8-op batches and omits dataBase64 non-destructively when >700 KB', () => {
  assert.equal(ATTACHMENT_BATCH_CHUNK_SIZE, 8);
  assert.equal(MAX_ATTACHMENT_INLINE_BYTES, 700_000);

  const twentyItems = Array.from({ length: 20 }, (_, idx) => `att-${idx}`);
  const batches = chunkItems(twentyItems, ATTACHMENT_BATCH_CHUNK_SIZE);
  assert.equal(batches.length, 3);
  assert.equal(batches[0].length, 8);
  assert.equal(batches[1].length, 8);
  assert.equal(batches[2].length, 4);

  const smallData = Buffer.alloc(1024, 0x42).toString('base64');
  const smallAtt: AttachmentRecord = {
    id: 'att-small',
    taskId: 'task-1',
    projectId: null,
    fileName: 'receipt.jpg',
    mimeType: 'image/jpeg',
    sizeBytes: 1024,
    localRelativePath: '/local/receipt.jpg',
    dataBase64: smallData,
    sortOrder: 1000,
    createdAt: 100,
    updatedAt: 200,
    isDeleted: false,
    isDirty: true
  };
  const smallMap = attachmentToFirestoreMap(smallAtt);
  assert.equal(smallMap.localRelativePath, '');
  assert.equal(smallMap.dataBase64, smallData);

  // Oversized >700 KB non-deleted attachment MUST omit dataBase64 instead of overwriting remote with ''
  const largeAtt: AttachmentRecord = {
    ...smallAtt,
    id: 'att-large',
    sizeBytes: 850_000,
    dataBase64: Buffer.alloc(710_000, 0x61).toString('base64')
  };
  const largeMap = attachmentToFirestoreMap(largeAtt);
  assert.equal(largeMap.localRelativePath, '');
  assert.equal('dataBase64' in largeMap, false);

  // Metadata-only update where local dataBase64 is empty while sizeBytes > 0 MUST also omit dataBase64
  const metadataOnlyAtt: AttachmentRecord = {
    ...smallAtt,
    id: 'att-meta-only',
    sizeBytes: 42_000,
    dataBase64: ''
  };
  const metaOnlyMap = attachmentToFirestoreMap(metadataOnlyAtt);
  assert.equal('dataBase64' in metaOnlyMap, false);

  // Soft-deleted attachment MUST explicitly clear dataBase64: ''
  const deletedAtt: AttachmentRecord = {
    ...largeAtt,
    isDeleted: true,
    dataBase64: ''
  };
  const deletedMap = attachmentToFirestoreMap(deletedAtt);
  assert.equal(deletedMap.dataBase64, '');
  assert.equal(deletedMap.localRelativePath, '');
});

test('buildBoundedNtfyRelayPayload strips inline attachment base64 and guards <3800 bytes', () => {
  assert.equal(MAX_NTFY_PAYLOAD_BYTES, 3_800);
  const smallPayload = buildBoundedNtfyRelayPayload({
    userId: 'user-1',
    deviceId: 'dev-1',
    batchTimestamp: 1_763_000_000_000,
    attachments: [
      {
        id: 'att-1',
        taskId: 't-1',
        projectId: null,
        fileName: 'doc.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 50_000,
        localRelativePath: '',
        dataBase64: Buffer.alloc(10_000, 0x55).toString('base64'),
        sortOrder: 1000,
        createdAt: 1,
        updatedAt: 2,
        isDeleted: false
      }
    ]
  });
  assert.ok(smallPayload);
  assert.ok(new TextEncoder().encode(smallPayload).byteLength < MAX_NTFY_PAYLOAD_BYTES);
  const parsed = JSON.parse(smallPayload);
  assert.equal('dataBase64' in parsed.attachments[0], false);

  // Huge task notes exceeding 3800 bytes must return null instead of pushing oversized relay payload
  const hugePayload = buildBoundedNtfyRelayPayload({
    userId: 'user-1',
    deviceId: 'dev-1',
    batchTimestamp: 1_763_000_000_000,
    tasks: [
      {
        id: 't-huge',
        title: 'Large Task',
        notes: 'x'.repeat(5000),
        status: 'OPEN',
        scheduleState: 'INBOX'
      }
    ]
  });
  assert.equal(hugePayload, null);
});

test('csvCodec escapes semicolons in checklist/tags, detects headerless delimiters, supports MM/DD/YYYY, and parses FR/IT/ES headers', () => {
  // Semicolon & comma escaping roundtrip
  const rawSteps = ['Step A; with semicolon', 'Step B \\ backslash', 'Step C'];
  const serializedSteps = rawSteps.map((s) => `[x] ${escapeCsvSemicolonItem(s)}`).join('; ');
  assert.deepEqual(
    splitEscapedCsvList(serializedSteps, [';']),
    rawSteps.map((s) => `[x] ${s}`)
  );

  const rawTags = ['Urgent; Q4', 'Client, VIP'];
  const serializedTags = rawTags.map(escapeCsvTagItem).join('; ');
  assert.deepEqual(splitEscapedCsvList(serializedTags, [';', ',']), rawTags);

  // Full entity CSV export -> import roundtrip
  const now = new Date(2026, 10, 18, 0, 0, 0, 0).getTime();
  const areas: AreaRecord[] = [
    {
      id: 'a-1',
      title: 'Work',
      sortOrder: 1000,
      tagIds: [],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const projects: ProjectRecord[] = [
    {
      id: 'p-1',
      areaId: 'a-1',
      title: 'v1.8.1 Ship',
      notes: '',
      status: 'ACTIVE',
      scheduleState: 'TODAY',
      whenDate: now,
      deadline: null,
      completedAt: null,
      sortOrder: 1000,
      tagIds: [],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const tags: TagRecord[] = [
    {
      id: 'tg-1',
      title: 'Semi;Tag',
      parentTagId: null,
      shortcut: null,
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tg-2',
      title: 'Comma,Tag',
      parentTagId: null,
      shortcut: null,
      sortOrder: 2000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const tasks: TaskRecord[] = [
    {
      id: 't-1',
      title: 'Export CSV cleanly',
      notes: 'Line 1\nLine 2, with comma',
      status: 'OPEN',
      scheduleState: 'TODAY',
      whenDate: now,
      deadline: new Date(2026, 11, 1, 0, 0, 0, 0).getTime(),
      reminderTime: null,
      completedAt: null,
      projectId: 'p-1',
      headingId: null,
      areaId: null,
      recurrenceRule: null,
      sortOrder: 1000,
      tagIds: ['tg-1', 'tg-2'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const checklistItems: ChecklistItemRecord[] = [
    {
      id: 'c-1',
      taskId: 't-1',
      title: 'First; part',
      isCompleted: true,
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'c-2',
      taskId: 't-1',
      title: 'Second; part',
      isCompleted: false,
      sortOrder: 2000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const csvOut = serializeEntitiesToCsv({
    areas,
    projects,
    tasks,
    checklistItems,
    tags
  });
  const roundtrip = parseCsvTaskImport(csvOut, now);
  assert.equal(roundtrip.length, 1);
  assert.equal(roundtrip[0].title, 'Export CSV cleanly');
  assert.equal(roundtrip[0].notes, 'Line 1\nLine 2, with comma');
  assert.equal(roundtrip[0].areaName, 'Work');
  assert.equal(roundtrip[0].projectName, 'v1.8.1 Ship');
  assert.deepEqual(roundtrip[0].tagNames, ['Semi;Tag', 'Comma,Tag']);
  assert.deepEqual(roundtrip[0].checklistItems, [
    { title: 'First; part', isCompleted: true },
    { title: 'Second; part', isCompleted: false }
  ]);

  // Headerless delimiter detection ignores semicolons inside quoted comma-delimited fields
  const headerlessComma = '"Buy milk; bread; eggs","Check fridge; pantry"\n"Call Bob","Today"';
  assert.equal(detectCsvDelimiter(headerlessComma), ',');
  const parsedHeaderlessComma = parseCsvTaskImport(headerlessComma, now);
  assert.equal(parsedHeaderlessComma.length, 2);
  assert.equal(parsedHeaderlessComma[0].title, 'Buy milk; bread; eggs');
  assert.equal(parsedHeaderlessComma[0].notes, 'Check fridge; pantry');

  const headerlessSemi = 'Aufgabe eins;Notiz mit, Komma\nAufgabe zwei;Zweite Notiz';
  assert.equal(detectCsvDelimiter(headerlessSemi), ';');
  const parsedHeaderlessSemi = parseCsvTaskImport(headerlessSemi, now);
  assert.equal(parsedHeaderlessSemi.length, 2);
  assert.equal(parsedHeaderlessSemi[0].notes, 'Notiz mit, Komma');

  // MM/DD/YYYY fallback when second part > 12 vs DD/MM/YYYY
  assert.equal(
    parseFlexibleDate('04/25/2026'),
    new Date(2026, 3, 25, 0, 0, 0, 0).getTime()
  );
  assert.equal(
    parseFlexibleDate('25/04/2026'),
    new Date(2026, 3, 25, 0, 0, 0, 0).getTime()
  );

  // FR, IT, and ES header aliases
  const frCsv =
    'Titre;Notes;Statut;Planification;Quand;Échéance;Domaine;Projet;Étiquettes;Sous-tâches\n' +
    'Préparer le rapport;Détails Q4;OPEN;TODAY;2026-11-18;05/20/2026;Direction;Bilan;Priorité;"[x] Étape 1\\; A; [ ] Étape 2"';
  const frParsed = parseCsvTaskImport(frCsv, now);
  assert.equal(frParsed.length, 1);
  assert.equal(frParsed[0].title, 'Préparer le rapport');
  assert.equal(frParsed[0].areaName, 'Direction');
  assert.equal(frParsed[0].projectName, 'Bilan');
  assert.equal(frParsed[0].deadline, new Date(2026, 4, 20, 0, 0, 0, 0).getTime());
  assert.deepEqual(frParsed[0].checklistItems, [
    { title: 'Étape 1; A', isCompleted: true },
    { title: 'Étape 2', isCompleted: false }
  ]);

  const itCsv =
    'Titolo,Note,Stato,Scadenza,Area,Progetto,Etichette,Sottoattività\n' +
    'Revisionare contratto,Note legali,COMPLETED,12/28/2026,Legale,Contratti 2026,Importante,"[x] Firma"';
  const itParsed = parseCsvTaskImport(itCsv, now);
  assert.equal(itParsed.length, 1);
  assert.equal(itParsed[0].status, 'COMPLETED');
  assert.equal(itParsed[0].areaName, 'Legale');
  assert.equal(itParsed[0].projectName, 'Contratti 2026');
  assert.equal(itParsed[0].deadline, new Date(2026, 11, 28, 0, 0, 0, 0).getTime());

  const esCsv =
    'Título,Notas,Estado,Fecha límite,Área,Proyecto,Etiquetas,Lista de verificación\n' +
    'Enviar presupuesto,Cliente Madrid,OPEN,07/19/2026,Ventas,Q3 Iberia,Urgente,"[ ] Revisar IVA"';
  const esParsed = parseCsvTaskImport(esCsv, now);
  assert.equal(esParsed.length, 1);
  assert.equal(esParsed[0].title, 'Enviar presupuesto');
  assert.equal(esParsed[0].areaName, 'Ventas');
  assert.equal(esParsed[0].projectName, 'Q3 Iberia');
  assert.deepEqual(esParsed[0].tagNames, ['Urgente']);
  assert.equal(esParsed[0].deadline, new Date(2026, 6, 19, 0, 0, 0, 0).getTime());
});
