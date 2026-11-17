import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {
  COLLECTION_CURSOR_STORAGE_PREFIX,
  ENTITIES_RECONCILE_V2_21_STORAGE_PREFIX,
  ENTITIES_RECONCILE_V2_22_STORAGE_PREFIX,
  MAX_FUTURE_CURSOR_DRIFT_MS,
  MAX_NTFY_PAYLOAD_BYTES,
  OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS,
  SYNC_COLLECTION_NAMES,
  buildLegacyCompatibleNtfyChunks,
  computeEffectivePullCursor,
  computeNextCollectionCursor,
  detectMissingReferencedEntityContainers,
  formatInstantChannelSeed,
  formatLegacySyncChannelFromHex,
  formatOAuthChannelSeed,
  normalizeFirestoreDocWithId,
  planRecurringDeduplication,
  sha256HexWebCrypto
} from '../src/data/syncUtils.ts';

test('normalizeFirestoreDocWithId preserves doc.id when raw Firestore data has missing, null, blank, or non-string id', () => {
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-1', { title: 'Roadmap' }), {
    title: 'Roadmap',
    id: 'proj-doc-1'
  });
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-2', { id: '', title: 'Empty ID' }), {
    id: 'proj-doc-2',
    title: 'Empty ID'
  });
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-3', { id: '   ', title: 'Blank ID' }), {
    id: 'proj-doc-3',
    title: 'Blank ID'
  });
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-4', { id: null, title: 'Null ID' }), {
    id: 'proj-doc-4',
    title: 'Null ID'
  });
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-5', { id: 42, title: 'Numeric ID' }), {
    id: 'proj-doc-5',
    title: 'Numeric ID'
  });
  assert.deepEqual(
    normalizeFirestoreDocWithId('proj-doc-6', { id: ' explicit-id ', title: 'Trimmed ID' }),
    {
      id: 'explicit-id',
      title: 'Trimmed ID'
    }
  );
  assert.deepEqual(normalizeFirestoreDocWithId('proj-doc-7', null), {
    id: 'proj-doc-7'
  });
});

test('per-collection computeNextCollectionCursor prevents a newer task delta from advancing the projects pull cursor', () => {
  const now = 1_795_000_500_000;
  const morningSync = 1_795_000_000_000; // 09:00
  const missedProjectUpdatedAt = 1_795_000_180_000; // +3 min (created while device was asleep)
  const newerTaskUpdatedAt = 1_795_000_420_000; // +7 min (edited later on peer)

  // Advancing tasks cursor to +7 min leaves projects cursor untouched at morningSync
  const nextTasksCursor = computeNextCollectionCursor(morningSync, newerTaskUpdatedAt, now);
  assert.equal(nextTasksCursor, newerTaskUpdatedAt);

  // When projects has not yet pulled, its cursor remains morningSync, so its effective query floor catches missedProjectUpdatedAt
  const projectsEffectiveFloor = computeEffectivePullCursor(morningSync, false);
  assert.ok(
    missedProjectUpdatedAt > projectsEffectiveFloor,
    'Missed project must strictly exceed projects effective pull floor'
  );

  // Initial sync from 0 with an empty collection advances cursor to wall-clock now
  assert.equal(computeNextCollectionCursor(0, 0, now), now);

  // Empty incremental pull preserves prior non-zero cursor rather than jumping to wall-clock now
  assert.equal(computeNextCollectionCursor(morningSync, 0, now), morningSync);

  // Clamps clock-skewed future timestamps to now + MAX_FUTURE_CURSOR_DRIFT_MS
  const skewedFuture = now + 999_999_999;
  assert.equal(
    computeNextCollectionCursor(morningSync, skewedFuture, now),
    now + MAX_FUTURE_CURSOR_DRIFT_MS
  );
});

test('detectMissingReferencedEntityContainers flags missing parent projects, areas, and headings while ignoring soft-deleted records', () => {
  // 1. Active task referencing a missing project triggers orphan project recovery
  const missingProj = detectMissingReferencedEntityContainers({
    areas: [{ id: 'area-1' }],
    projects: [{ id: 'proj-existing', areaId: 'area-1', isDeleted: false }],
    headings: [],
    tasks: [
      {
        id: 'task-1',
        projectId: 'proj-missing-on-googlebook',
        areaId: null,
        headingId: null,
        isDeleted: false
      }
    ]
  });
  assert.deepEqual(missingProj, {
    hasOrphanProjectRef: true,
    hasOrphanAreaRef: false,
    hasOrphanHeadingRef: false
  });

  // 2. Once proj-missing-on-googlebook is present in IndexedDB (even if soft-deleted), orphan check clears
  const healedOrSoftDeletedProj = detectMissingReferencedEntityContainers({
    areas: [{ id: 'area-1' }],
    projects: [
      { id: 'proj-existing', areaId: 'area-1', isDeleted: false },
      { id: 'proj-missing-on-googlebook', areaId: null, isDeleted: true }
    ],
    headings: [],
    tasks: [
      {
        id: 'task-1',
        projectId: 'proj-missing-on-googlebook',
        areaId: null,
        headingId: null,
        isDeleted: false
      }
    ]
  });
  assert.deepEqual(healedOrSoftDeletedProj, {
    hasOrphanProjectRef: false,
    hasOrphanAreaRef: false,
    hasOrphanHeadingRef: false
  });

  // 3. Soft-deleted tasks/headings/projects do not trigger false-positive full pulls
  const deletedChildOnly = detectMissingReferencedEntityContainers({
    areas: [],
    projects: [{ id: 'proj-del', areaId: 'area-gone', isDeleted: true }],
    headings: [{ id: 'head-del', projectId: 'proj-gone', isDeleted: true }],
    tasks: [
      {
        id: 'task-del',
        projectId: 'proj-gone',
        areaId: 'area-gone',
        headingId: 'head-gone',
        isDeleted: true
      }
    ]
  });
  assert.deepEqual(deletedChildOnly, {
    hasOrphanProjectRef: false,
    hasOrphanAreaRef: false,
    hasOrphanHeadingRef: false
  });

  // 4. Active heading referencing missing project, active project referencing missing area, and active task referencing missing heading
  const multiOrphan = detectMissingReferencedEntityContainers({
    areas: [],
    projects: [{ id: 'proj-1', areaId: 'area-missing', isDeleted: false }],
    headings: [{ id: 'head-1', projectId: 'proj-missing', isDeleted: false }],
    tasks: [
      {
        id: 'task-2',
        projectId: 'proj-1',
        areaId: null,
        headingId: 'head-missing',
        isDeleted: false
      }
    ]
  });
  assert.deepEqual(multiOrphan, {
    hasOrphanProjectRef: true,
    hasOrphanAreaRef: true,
    hasOrphanHeadingRef: true
  });
});

test('planRecurringDeduplication merges Cialis standalone/Privat area-split & v1.8 stripped timeblock duplicates while preserving distinct explicit areas', () => {
  const dailyRule = {
    mode: 'FIXED_SCHEDULE' as const,
    unit: 'DAY' as const,
    interval: 1,
    daysOfWeek: [] as number[],
    dayOfMonth: null,
    endDateEpochMillis: null
  };

  // Exact bug scenario: v1.8 or detached task has areaId=null + newer whenDate, while v2.x task has areaId='area-privat' + timeblock + notes
  const cialisPlan = planRecurringDeduplication([
    {
      id: 'cialis-detached-v18',
      title: 'Cialis',
      notes: '',
      status: 'OPEN',
      isDeleted: false,
      whenDate: 1_795_100_000_000,
      createdAt: 1_795_000_000_000,
      updatedAt: 1_795_100_500_000,
      projectId: null,
      headingId: null,
      areaId: null,
      recurrenceRule: dailyRule,
      tagIds: ['tag-gesundheit'],
      scheduledStartMinutes: null,
      durationMinutes: null,
      deadline: null,
      reminderTime: null
    },
    {
      id: 'cialis-privat-v2',
      title: '  cialis ',
      notes: 'Daily morning',
      status: 'OPEN',
      isDeleted: false,
      whenDate: 1_795_013_600_000,
      createdAt: 1_794_900_000_000,
      updatedAt: 1_795_100_200_000,
      projectId: null,
      headingId: null,
      areaId: 'area-privat',
      recurrenceRule: dailyRule,
      tagIds: ['tag-gesundheit', 'tag-routine'],
      scheduledStartMinutes: 480,
      durationMinutes: 15,
      deadline: null,
      reminderTime: 1_795_120_000_000
    }
  ]);

  assert.deepEqual(cialisPlan.loserIds, ['cialis-privat-v2']);
  assert.equal(cialisPlan.winnerEnrichments.length, 1);
  assert.deepEqual(cialisPlan.winnerEnrichments[0], {
    winnerId: 'cialis-detached-v18',
    areaId: 'area-privat',
    notes: 'Daily morning',
    tagIds: ['tag-gesundheit', 'tag-routine'],
    scheduledStartMinutes: 480,
    durationMinutes: 15,
    reminderTime: 1_795_120_000_000,
    minUpdatedAt: 1_795_100_500_001
  });

  // Two distinct explicit areas ('area-work' vs 'area-home') with same title must NOT merge across areas
  const distinctAreaPlan = planRecurringDeduplication([
    {
      id: 'standup-work',
      title: 'Check mail',
      notes: '',
      status: 'OPEN',
      isDeleted: false,
      whenDate: 1_795_100_000_000,
      createdAt: 1_795_000_000_000,
      projectId: null,
      headingId: null,
      areaId: 'area-work',
      recurrenceRule: dailyRule,
      tagIds: [],
      scheduledStartMinutes: null,
      durationMinutes: null
    },
    {
      id: 'standup-home',
      title: 'Check mail',
      notes: '',
      status: 'OPEN',
      isDeleted: false,
      whenDate: 1_795_100_000_000,
      createdAt: 1_795_000_100_000,
      projectId: null,
      headingId: null,
      areaId: 'area-home',
      recurrenceRule: dailyRule,
      tagIds: [],
      scheduledStartMinutes: null,
      durationMinutes: null
    }
  ]);
  assert.deepEqual(distinctAreaPlan.loserIds, []);
  assert.deepEqual(distinctAreaPlan.winnerEnrichments, []);
});

test('buildLegacyCompatibleNtfyChunks splits oversized batches and never emits wakeOnly on legacy tb_sync_* channels', async () => {
  const manyTasks = Array.from({ length: 24 }, (_, idx) => ({
    id: `task-bulk-${idx}`,
    title: `Synchronized Task #${idx} with detailed cross-platform description text`,
    notes: 'x'.repeat(180),
    status: 'OPEN',
    schedule: 'TODAY',
    updatedAt: 1_795_200_000_000 + idx
  }));

  const chunks = buildLegacyCompatibleNtfyChunks(
    {
      userId: 'uid-test-123',
      deviceId: 'dev-chromeos',
      batchTimestamp: 1_795_200_050_000,
      tasks: manyTasks
    },
    MAX_NTFY_PAYLOAD_BYTES,
    3
  );

  assert.ok(chunks.length >= 2 && chunks.length <= 3);
  for (const chunk of chunks) {
    assert.ok(new TextEncoder().encode(chunk).byteLength < MAX_NTFY_PAYLOAD_BYTES);
    const parsed = JSON.parse(chunk);
    assert.equal(parsed.wakeOnly, undefined, 'Legacy tb_sync_* chunks must never contain wakeOnly');
    assert.ok(Array.isArray(parsed.tasks) && parsed.tasks.length > 0);
  }

  const oauthHex = await sha256HexWebCrypto(formatOAuthChannelSeed('uid-test-123'));
  const instantHex = await sha256HexWebCrypto(formatInstantChannelSeed('User@Example.com '));
  assert.match(formatLegacySyncChannelFromHex(oauthHex), /^tb_sync_[0-9a-f]{28}$/);
  assert.match(formatLegacySyncChannelFromHex(instantHex), /^tb_sync_[0-9a-f]{28}$/);
});

test('v2.0.0-beta.22 cloudSync.ts and localDb.ts enforce pull-before-push, offline restamping, v1.8 rollover protection, and tag remapping', async () => {
  assert.equal(SYNC_COLLECTION_NAMES.length, 8);
  assert.ok(SYNC_COLLECTION_NAMES.includes('projects'));
  assert.equal(ENTITIES_RECONCILE_V2_21_STORAGE_PREFIX, 'thingsbook_v2_reconcile_b21_');
  assert.equal(ENTITIES_RECONCILE_V2_22_STORAGE_PREFIX, 'thingsbook_v2_reconcile_b22_');
  assert.equal(COLLECTION_CURSOR_STORAGE_PREFIX, 'thingsbook_col_sync_ts_v2_');
  assert.equal(OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS, 30_000);

  const cloudSyncPath = path.resolve(import.meta.dirname, '../src/data/cloudSync.ts');
  const cloudSyncSrc = await fs.readFile(cloudSyncPath, 'utf8');

  // Ensure initializeSession awaits syncBidirectionalDeltas BEFORE attachRealtimeListeners
  const initIdx = cloudSyncSrc.indexOf('async initializeSession()');
  const firstBidirectionalIdx = cloudSyncSrc.indexOf('await this.syncBidirectionalDeltas()', initIdx);
  const firstAttachIdx = cloudSyncSrc.indexOf('this.attachRealtimeListeners(user.uid)', initIdx);
  assert.ok(initIdx >= 0 && firstBidirectionalIdx > initIdx && firstAttachIdx > firstBidirectionalIdx);

  // Ensure runSyncInternal pulls remote deltas BEFORE pushing local dirty records
  const runSyncStart = cloudSyncSrc.indexOf('private async runSyncInternal(');
  const pullCallIdx = cloudSyncSrc.indexOf('await this.pullDeltaRecordsFromFirestore(', runSyncStart);
  const pushCallIdx = cloudSyncSrc.indexOf('await this.pushDirtyRecordsToFirestore(', runSyncStart);
  assert.ok(runSyncStart > 0 && pullCallIdx > runSyncStart && pushCallIdx > pullCallIdx);

  // Ensure attachRealtimeListeners and handleIncomingNtfyEnvelope never mutate collection or global pull cursors
  const attachSectionStart = cloudSyncSrc.indexOf('private attachRealtimeListeners(');
  const detachSectionStart = cloudSyncSrc.indexOf('private detachRealtimeListeners()');
  assert.ok(attachSectionStart > 0 && detachSectionStart > attachSectionStart);
  const realtimeSection = cloudSyncSrc.slice(attachSectionStart, detachSectionStart);
  assert.equal(
    realtimeSection.includes('this.setCollectionCursor('),
    false,
    'Realtime listeners and ntfy SSE must never advance per-collection pull cursors'
  );
  assert.equal(
    realtimeSection.includes('this.setLastSyncTs('),
    false,
    'Realtime listeners and ntfy SSE must never advance the shared low-watermark pull cursor'
  );

  // Ensure localDb.applyRemoteDeltas heals corrupted timestamps, protects completed recurring parents, and remaps duplicate tags
  const localDbPath = path.resolve(import.meta.dirname, '../src/data/localDb.ts');
  const localDbSrc = await fs.readFile(localDbPath, 'utf8');
  assert.ok(localDbSrc.includes('!Number.isFinite(local.updatedAt)'));
  assert.ok(localDbSrc.includes('!local.isDirty && local.updatedAt > nowCeiling'));
  assert.ok(localDbSrc.includes('omittedTimeblockFields'));
  assert.ok(localDbSrc.includes('remapTagId.size > 0'));
  assert.ok(localDbSrc.includes("local.status === 'COMPLETED'"));
});

