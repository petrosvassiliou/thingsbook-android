import test from 'node:test';
import assert from 'node:assert/strict';
import {
  BACKUP_MAGIC,
  FORMAT_VERSION,
  MAX_DECOMPRESSED_BYTES,
  MAX_FILE_BYTES,
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  inspectThingsBackup
} from '../src/data/backupCodec.ts';
import type {
  AreaRecord,
  AttachmentRecord,
  ChecklistItemRecord,
  HeadingRecord,
  ProjectRecord,
  TagRecord,
  TaskRecord
} from '../src/domain/types.ts';

function createSampleDataset() {
  const now = 1_763_000_000_000;
  const areas: AreaRecord[] = [
    {
      id: 'area-1',
      title: 'Engineering',
      sortOrder: 1000,
      tagIds: ['tag-root'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'area-deleted',
      title: 'Deleted Area',
      sortOrder: 2000,
      tagIds: [],
      createdAt: now,
      updatedAt: now,
      isDeleted: true,
      isDirty: false
    }
  ];

  const projects: ProjectRecord[] = [
    {
      id: 'proj-1',
      areaId: 'area-1',
      title: 'ChromeOS v1.8.1 Launch',
      notes: 'Full parity release notes',
      status: 'ACTIVE',
      scheduleState: 'TODAY',
      whenDate: now,
      deadline: now + 86_400_000,
      completedAt: null,
      sortOrder: 1000,
      tagIds: ['tag-root', 'tag-child'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const headings: HeadingRecord[] = [
    {
      id: 'head-1',
      projectId: 'proj-1',
      title: 'QA Verification',
      sortOrder: 1000,
      isArchived: false,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const tasks: TaskRecord[] = [
    {
      id: 'task-1',
      title: 'Verify AES-256-GCM .tbbackup',
      notes: 'Include binary attachments and recurrence',
      status: 'OPEN',
      scheduleState: 'TODAY',
      whenDate: now,
      deadline: now + 86_400_000,
      reminderTime: now + 3_600_000,
      completedAt: null,
      projectId: 'proj-1',
      headingId: 'head-1',
      areaId: null,
      recurrenceRule: {
        mode: 'FIXED_SCHEDULE',
        interval: 2,
        unit: 'WEEKS',
        daysOfWeek: [1, 4],
        dayOfMonth: null,
        endDateEpochMillis: null
      },
      sortOrder: 1000,
      tagIds: ['tag-child'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const checklistItems: ChecklistItemRecord[] = [
    {
      id: 'chk-1',
      taskId: 'task-1',
      title: 'Test PBKDF2 120,000 iterations',
      isCompleted: true,
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const tags: TagRecord[] = [
    {
      id: 'tag-root',
      title: 'Release',
      parentTagId: null,
      shortcut: 'R',
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    },
    {
      id: 'tag-child',
      title: 'QA',
      parentTagId: 'tag-root',
      shortcut: 'Q',
      sortOrder: 2000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const binaryBytes = new Uint8Array(2048);
  for (let i = 0; i < binaryBytes.length; i++) {
    binaryBytes[i] = (i * 37) & 0xff;
  }
  const binaryB64 = Buffer.from(binaryBytes).toString('base64');

  const attachments: AttachmentRecord[] = [
    {
      id: 'att-1',
      taskId: 'task-1',
      projectId: null,
      fileName: 'spec.pdf',
      mimeType: 'application/pdf',
      sizeBytes: binaryBytes.byteLength,
      localRelativePath: '',
      dataBase64: binaryB64,
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  return {
    areas,
    projects,
    headings,
    tasks,
    checklistItems,
    tags,
    attachments,
    binaryB64
  };
}

test('unencrypted .tbbackup export, inspect, and decode roundtrips all entities and binary attachments', async () => {
  const ds = createSampleDataset();
  const payload = buildBackupPayloadFromEntities({
    areas: ds.areas,
    projects: ds.projects,
    headings: ds.headings,
    tasks: ds.tasks,
    checklistItems: ds.checklistItems,
    tags: ds.tags,
    attachments: ds.attachments,
    preferences: {
      sidebarVisible: true,
      dailyDigestEnabled: true,
      dailyDigestHour: 8,
      dailyDigestMinute: 30,
      languageTag: 'de',
      calendarEventsEnabled: false,
      selectedCalendarIds: ['primary'],
      groupTodayByProjectOrArea: true
    },
    appVersionName: '1.8.1-chromeos'
  });

  // Soft-deleted area should be excluded
  assert.equal(payload.areas.length, 1);

  const encodedBytes = await encodeThingsBackup(payload, null);
  const inspection = await inspectThingsBackup(encodedBytes);
  assert.equal(inspection.kind, 'plain');
  assert.equal(inspection.summary.areasCount, 1);
  assert.equal(inspection.summary.projectsCount, 1);
  assert.equal(inspection.summary.headingsCount, 1);
  assert.equal(inspection.summary.tasksCount, 1);
  assert.equal(inspection.summary.checklistItemsCount, 1);
  assert.equal(inspection.summary.tagsCount, 2);
  assert.equal(inspection.summary.attachmentsCount, 1);

  const decoded = await decodeThingsBackup(encodedBytes, null);
  assert.equal(decoded.areas[0].title, 'Engineering');
  assert.equal(decoded.projects[0].title, 'ChromeOS v1.8.1 Launch');
  assert.equal(decoded.headings[0].title, 'QA Verification');
  assert.equal(decoded.tasks[0].title, 'Verify AES-256-GCM .tbbackup');
  assert.deepEqual(JSON.parse(decoded.tasks[0].recurrenceRuleJson!), ds.tasks[0].recurrenceRule);
  assert.equal(decoded.checklistItems[0].isCompleted, true);
  assert.equal(decoded.tags[1].parentTagId, 'tag-root');
  assert.deepEqual(decoded.taskTagRefs, [{ taskId: 'task-1', tagId: 'tag-child' }]);
  assert.deepEqual(decoded.projectTagRefs, [
    { projectId: 'proj-1', tagId: 'tag-root' },
    { projectId: 'proj-1', tagId: 'tag-child' }
  ]);
  assert.equal(decoded.attachments?.length, 1);
  assert.equal(decoded.attachments?.[0].fileName, 'spec.pdf');
  assert.equal(decoded.attachments?.[0].dataBase64, ds.binaryB64);
  assert.equal(decoded.preferences.languageTag, 'de');
});

test('AES-256-GCM PBKDF2 encrypted .tbbackup roundtrips with right password and rejects wrong/missing password', async () => {
  const ds = createSampleDataset();
  const payload = buildBackupPayloadFromEntities({
    areas: ds.areas,
    projects: ds.projects,
    headings: ds.headings,
    tasks: ds.tasks,
    checklistItems: ds.checklistItems,
    tags: ds.tags,
    attachments: ds.attachments,
    preferences: { languageTag: 'fr' },
    appVersionName: '1.8.1-chromeos'
  });

  const encryptedBytes = await encodeThingsBackup(payload, 'CorrectHorseBatteryStaple!44');
  const inspection = await inspectThingsBackup(encryptedBytes);
  assert.equal(inspection.kind, 'encrypted');
  assert.equal(inspection.appVersionName, '1.8.1-chromeos');

  await assert.rejects(() => decodeThingsBackup(encryptedBytes, null), /PASSWORD_REQUIRED/);
  await assert.rejects(
    () => decodeThingsBackup(encryptedBytes, 'WrongPassword123'),
    /WRONG_PASSWORD/
  );

  const decoded = await decodeThingsBackup(encryptedBytes, 'CorrectHorseBatteryStaple!44');
  assert.equal(decoded.tasks[0].id, 'task-1');
  assert.equal(decoded.attachments?.[0].dataBase64, ds.binaryB64);
  assert.equal(decoded.preferences.languageTag, 'fr');
});

test('backupCodec enforces 80 MB compressed / 120 MB decompressed safety limits and validates headers', async () => {
  assert.equal(MAX_FILE_BYTES, 80 * 1024 * 1024);
  assert.equal(MAX_DECOMPRESSED_BYTES, 120 * 1024 * 1024);

  await assert.rejects(() => inspectThingsBackup(new Uint8Array(0)), /empty/i);

  // Fake view exceeding MAX_FILE_BYTES without allocating 80 MB of non-zero data
  const oversized = new Uint8Array(MAX_FILE_BYTES + 1);
  await assert.rejects(() => inspectThingsBackup(oversized), /80 MB/);

  const badMagic = new TextEncoder().encode(
    JSON.stringify({
      magic: 'NOT_THINGSBOOK',
      formatVersion: FORMAT_VERSION,
      encrypted: false
    })
  );
  await assert.rejects(() => inspectThingsBackup(badMagic), /Unknown backup magic/);

  const futureFormat = new TextEncoder().encode(
    JSON.stringify({
      magic: BACKUP_MAGIC,
      formatVersion: 99,
      encrypted: false
    })
  );
  await assert.rejects(() => inspectThingsBackup(futureFormat), /Unsupported backup format/);
});
