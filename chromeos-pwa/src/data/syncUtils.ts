import type {
  AreaRecord,
  AttachmentRecord,
  HeadingRecord,
  ProjectRecord,
  RecurrenceRule,
  TaskRecord
} from '../domain/types.ts';

export const MAX_ATTACHMENT_INLINE_BYTES = 700_000;
export const ATTACHMENT_BATCH_CHUNK_SIZE = 8;
export const METADATA_BATCH_CHUNK_SIZE = 450;
export const CLOCK_SKEW_OVERLAP_MS = 120_000;
export const MAX_FUTURE_CURSOR_DRIFT_MS = 60_000;
export const MAX_NTFY_PAYLOAD_BYTES = 3_800;
export const FAST_SYNC_COALESCE_MS = 50;
export const ATTACHMENT_BACKFILL_STORAGE_PREFIX = 'thingsbook_att_backfill_v1_';
export const PROJECTS_REVIEW_BACKFILL_STORAGE_PREFIX = 'thingsbook_proj_review_backfill_v1_';
export const ENTITIES_RECONCILE_V2_21_STORAGE_PREFIX = 'thingsbook_v2_reconcile_b21_';
export const ENTITIES_RECONCILE_V2_22_STORAGE_PREFIX = 'thingsbook_v2_reconcile_b22_';
export const COLLECTION_CURSOR_STORAGE_PREFIX = 'thingsbook_col_sync_ts_v2_';
export const OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS = 30_000;

export const SYNC_COLLECTION_NAMES = [
  'tags',
  'areas',
  'projects',
  'headings',
  'tasks',
  'checklist_items',
  'attachments',
  'project_templates'
] as const;

export type SyncCollectionName = (typeof SYNC_COLLECTION_NAMES)[number];

export function formatUidWakeSeed(userId: string): string {
  return `thingsbook-uid-v2:${userId.trim()}`;
}

export function formatUidWakeChannelFromHex(sha256HexDigest: string): string {
  return `tb_uid_${sha256HexDigest.trim().toLowerCase().slice(0, 28)}`;
}

export function formatOAuthChannelSeed(userId: string): string {
  return `thingsbook-oauth:${userId.trim()}`;
}

export function formatInstantChannelSeed(email: string): string {
  return `thingsbook-instant:${email.trim().toLowerCase()}`;
}

export function formatLegacySyncChannelFromHex(sha256HexDigest: string): string {
  return `tb_sync_${sha256HexDigest.trim().toLowerCase().slice(0, 28)}`;
}

export function formatDeterministicUuidFromSha256Hex(sha256HexDigest: string): string {
  const h = sha256HexDigest.trim().toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

export function formatLocalYyyyMmDd(epochMillis: number): string {
  const d = new Date(epochMillis);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function formatRecurringNextTaskSeed(completedTaskId: string, nextWhenYyyyMmDd: string): string {
  return `thingsbook-rec-next-v1:${completedTaskId.trim()}:${nextWhenYyyyMmDd.trim()}`;
}

export function formatRecurringChecklistSeed(nextTaskId: string, sourceChecklistId: string): string {
  return `thingsbook-rec-chk-v1:${nextTaskId.trim()}:${sourceChecklistId.trim()}`;
}

export async function sha256HexWebCrypto(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function deriveRecurringNextTaskId(
  completedTaskId: string,
  nextWhenYyyyMmDd: string
): Promise<string> {
  const hex = await sha256HexWebCrypto(formatRecurringNextTaskSeed(completedTaskId, nextWhenYyyyMmDd));
  return formatDeterministicUuidFromSha256Hex(hex);
}

export async function deriveRecurringChecklistItemId(
  nextTaskId: string,
  sourceChecklistId: string
): Promise<string> {
  const hex = await sha256HexWebCrypto(formatRecurringChecklistSeed(nextTaskId, sourceChecklistId));
  return formatDeterministicUuidFromSha256Hex(hex);
}

export type RecurringDedupCandidate = Pick<
  TaskRecord,
  | 'id'
  | 'title'
  | 'notes'
  | 'status'
  | 'isDeleted'
  | 'whenDate'
  | 'createdAt'
  | 'projectId'
  | 'headingId'
  | 'areaId'
  | 'recurrenceRule'
  | 'tagIds'
  | 'scheduledStartMinutes'
  | 'durationMinutes'
> & {
  deadline?: number | null;
  reminderTime?: number | null;
  updatedAt?: number;
};

export interface RecurringWinnerEnrichment {
  winnerId: string;
  areaId?: string | null;
  headingId?: string | null;
  notes?: string;
  tagIds?: string[];
  scheduledStartMinutes?: number | null;
  durationMinutes?: number | null;
  deadline?: number | null;
  reminderTime?: number | null;
  minUpdatedAt: number;
}

export interface RecurringDeduplicationPlan {
  loserIds: string[];
  winnerEnrichments: RecurringWinnerEnrichment[];
}

export function normalizeRecurringTitleKey(title: string | null | undefined): string {
  return (title || '').trim().replace(/\s+/g, ' ').toLowerCase();
}

export function canonicalizeRecurrenceRuleKey(rule: RecurrenceRule | null | undefined): string | null {
  if (!rule) return null;
  const interval = Math.max(1, Math.min(3650, Math.trunc(rule.interval || 1)));
  const validDays = Array.from(
    new Set((rule.daysOfWeek || []).map((d) => Math.trunc(d)).filter((d) => d >= 1 && d <= 7))
  )
    .sort((a, b) => a - b)
    .join(',');
  const dom = rule.dayOfMonth != null ? String(Math.trunc(rule.dayOfMonth)) : '';
  const endMs = rule.endDateEpochMillis != null ? String(Math.trunc(rule.endDateEpochMillis)) : '';
  return `${rule.mode}|${rule.unit}|${interval}|${validDays}|${dom}|${endMs}`;
}

/**
 * Series identity key resilient to v1.8 <-> v2.x field differences (e.g. stripped timeblocks,
 * edited notes, or tag order differences). Tasks in the same project or area with the same
 * normalized title and recurrence rule share a single recurring series key.
 */
export function buildRecurringSeriesKey(
  task: Pick<
    RecurringDedupCandidate,
    | 'title'
    | 'notes'
    | 'status'
    | 'isDeleted'
    | 'projectId'
    | 'headingId'
    | 'areaId'
    | 'recurrenceRule'
    | 'tagIds'
    | 'scheduledStartMinutes'
    | 'durationMinutes'
  >
): string | null {
  if (task.isDeleted || task.status !== 'OPEN') return null;
  const normTitle = normalizeRecurringTitleKey(task.title);
  if (!normTitle) return null;
  const ruleKey = canonicalizeRecurrenceRuleKey(task.recurrenceRule);
  if (!ruleKey) return null;
  const proj = (task.projectId || '').trim();
  const area = (task.areaId || '').trim();
  const normContainer = proj ? `P:${proj}` : `A:${area}`;
  return `${normTitle}\u001F${normContainer}\u001F${ruleKey}`;
}

export function compareRecurringTaskPriority(
  a: Pick<RecurringDedupCandidate, 'id' | 'whenDate' | 'createdAt' | 'areaId' | 'scheduledStartMinutes'>,
  b: Pick<RecurringDedupCandidate, 'id' | 'whenDate' | 'createdAt' | 'areaId' | 'scheduledStartMinutes'>
): number {
  const aWhen = a.whenDate != null && Number.isFinite(a.whenDate) ? Math.trunc(a.whenDate) : -1;
  const bWhen = b.whenDate != null && Number.isFinite(b.whenDate) ? Math.trunc(b.whenDate) : -1;
  if (aWhen !== bWhen) {
    return aWhen > bWhen ? -1 : 1;
  }
  const aCreated =
    Number.isFinite(a.createdAt) && a.createdAt > 0 ? Math.trunc(a.createdAt) : Number.MAX_SAFE_INTEGER;
  const bCreated =
    Number.isFinite(b.createdAt) && b.createdAt > 0 ? Math.trunc(b.createdAt) : Number.MAX_SAFE_INTEGER;
  if (aCreated !== bCreated) {
    return aCreated < bCreated ? -1 : 1;
  }
  const aHasArea = Boolean((a.areaId || '').trim());
  const bHasArea = Boolean((b.areaId || '').trim());
  if (aHasArea !== bHasArea) {
    return aHasArea ? -1 : 1;
  }
  const aHasTb = a.scheduledStartMinutes != null;
  const bHasTb = b.scheduledStartMinutes != null;
  if (aHasTb !== bHasTb) {
    return aHasTb ? -1 : 1;
  }
  if (a.id === b.id) return 0;
  return a.id < b.id ? -1 : 1;
}

/**
 * Groups open recurring tasks across both exact containers AND standalone/Area splits where one
 * copy lost `areaId` (`areaId == null`) while a sibling in the same `(title, rule)` series has
 * `areaId != null` (e.g. v1.8 <-> v2.x sync or area detachment), returning both loser IDs to
 * tombstone and any richer metadata (`areaId`, `headingId`, `notes`, `tagIds`, timeblocks) to
 * preserve on the winning task.
 */
export function planRecurringDeduplication(
  tasks: readonly RecurringDedupCandidate[]
): RecurringDeduplicationPlan {
  const baseBuckets = new Map<string, RecurringDedupCandidate[]>();
  for (const t of tasks) {
    if (t.isDeleted || t.status !== 'OPEN') continue;
    const normTitle = normalizeRecurringTitleKey(t.title);
    if (!normTitle) continue;
    const ruleKey = canonicalizeRecurrenceRuleKey(t.recurrenceRule);
    if (!ruleKey) continue;
    const proj = (t.projectId || '').trim();
    const baseKey = `${normTitle}\u001F${ruleKey}\u001F${proj}`;
    const bucket = baseBuckets.get(baseKey);
    if (bucket) {
      bucket.push(t);
    } else {
      baseBuckets.set(baseKey, [t]);
    }
  }

  const finalGroups: RecurringDedupCandidate[][] = [];
  for (const bucket of baseBuckets.values()) {
    if (bucket.length < 2) continue;
    const proj = (bucket[0].projectId || '').trim();
    if (proj) {
      finalGroups.push(bucket);
      continue;
    }
    const distinctExplicitAreas = Array.from(
      new Set(bucket.map((t) => (t.areaId || '').trim()).filter(Boolean))
    );
    if (distinctExplicitAreas.length <= 1) {
      // Either all share the same explicit area, all have null area, or some have the single
      // explicit area (e.g. "Privat") while duplicates have areaId=null -> merge into one series!
      finalGroups.push(bucket);
    } else {
      // Multiple distinct explicit areas (e.g. "Work" vs "Home"): group per explicit area,
      // and deduplicate any null-area duplicates among themselves without cross-area merging.
      const byArea = new Map<string, RecurringDedupCandidate[]>();
      for (const t of bucket) {
        const areaKey = (t.areaId || '').trim();
        const sub = byArea.get(areaKey);
        if (sub) sub.push(t);
        else byArea.set(areaKey, [t]);
      }
      for (const sub of byArea.values()) {
        if (sub.length >= 2) finalGroups.push(sub);
      }
    }
  }

  const loserIds: string[] = [];
  const winnerEnrichments: RecurringWinnerEnrichment[] = [];

  for (const group of finalGroups) {
    if (group.length < 2) continue;
    const sorted = [...group].sort(compareRecurringTaskPriority);
    const winner = sorted[0];
    const losers = sorted.slice(1);
    for (const l of losers) {
      loserIds.push(l.id);
    }

    const patch: RecurringWinnerEnrichment = {
      winnerId: winner.id,
      minUpdatedAt: group.reduce((m, x) => Math.max(m, x.updatedAt ?? 0), 0) + 1
    };
    let changed = false;

    const winProj = (winner.projectId || '').trim();
    const winArea = (winner.areaId || '').trim();
    if (!winProj && !winArea) {
      const donorArea = losers.map((l) => (l.areaId || '').trim()).find(Boolean);
      if (donorArea) {
        patch.areaId = donorArea;
        changed = true;
      }
    }
    const winHead = (winner.headingId || '').trim();
    if (winProj && !winHead) {
      const donorHead = losers.map((l) => (l.headingId || '').trim()).find(Boolean);
      if (donorHead) {
        patch.headingId = donorHead;
        changed = true;
      }
    }
    if (!(winner.notes || '').trim()) {
      const donorNotes = losers.map((l) => (l.notes || '').trim()).find(Boolean);
      if (donorNotes) {
        patch.notes = donorNotes;
        changed = true;
      }
    }
    const winTags = Array.from(new Set((winner.tagIds || []).map((t) => t.trim()).filter(Boolean)));
    const unionTags = Array.from(
      new Set(
        group.flatMap((t) => (t.tagIds || []).map((id) => id.trim()).filter(Boolean))
      )
    );
    if (unionTags.length > winTags.length) {
      patch.tagIds = unionTags;
      changed = true;
    }
    if (winner.scheduledStartMinutes == null) {
      const donorTb = losers.find((l) => l.scheduledStartMinutes != null);
      if (donorTb && donorTb.scheduledStartMinutes != null) {
        patch.scheduledStartMinutes = donorTb.scheduledStartMinutes;
        patch.durationMinutes = winner.durationMinutes ?? donorTb.durationMinutes ?? null;
        changed = true;
      }
    }
    if (winner.deadline == null) {
      const donorDl = losers.find((l) => l.deadline != null)?.deadline;
      if (donorDl != null) {
        patch.deadline = donorDl;
        changed = true;
      }
    }
    if (winner.reminderTime == null) {
      const donorRem = losers.find((l) => l.reminderTime != null)?.reminderTime;
      if (donorRem != null) {
        patch.reminderTime = donorRem;
        changed = true;
      }
    }

    if (changed) {
      winnerEnrichments.push(patch);
    }
  }

  return { loserIds, winnerEnrichments };
}

export function selectRecurringDuplicateLoserIds(
  tasks: readonly RecurringDedupCandidate[]
): string[] {
  return planRecurringDeduplication(tasks).loserIds;
}

function finiteNumber(v: unknown, fallback = 0): number {
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export function mergeMonotonicReviewTimestamp(
  localLastReviewedAt: number | null | undefined,
  remoteLastReviewedAt: number | null | undefined
): number | null {
  const l =
    typeof localLastReviewedAt === 'number' && Number.isFinite(localLastReviewedAt) && localLastReviewedAt > 0
      ? Math.trunc(localLastReviewedAt)
      : null;
  const r =
    typeof remoteLastReviewedAt === 'number' && Number.isFinite(remoteLastReviewedAt) && remoteLastReviewedAt > 0
      ? Math.trunc(remoteLastReviewedAt)
      : null;
  if (l !== null && r !== null) return Math.max(l, r);
  return l ?? r;
}

export function computeEffectivePullCursor(
  sinceTimestamp: number,
  forceFullPull = false
): number {
  if (forceFullPull || !Number.isFinite(sinceTimestamp) || sinceTimestamp <= 0) return 0;
  return Math.max(0, Math.trunc(sinceTimestamp) - CLOCK_SKEW_OVERLAP_MS);
}

export function clampRemoteCursorTimestamp(
  remoteUpdatedAt: number,
  nowMillis: number = Date.now()
): number {
  if (!Number.isFinite(remoteUpdatedAt) || remoteUpdatedAt <= 0) return 0;
  return Math.trunc(Math.min(remoteUpdatedAt, nowMillis + MAX_FUTURE_CURSOR_DRIFT_MS));
}

export function computeNextCollectionCursor(
  prevCursor: number,
  collectionMaxUpdatedAt: number,
  nowMillis: number = Date.now()
): number {
  const safePrev =
    Number.isFinite(prevCursor) && prevCursor > 0
      ? clampRemoteCursorTimestamp(prevCursor, nowMillis)
      : 0;
  const clampedMax = clampRemoteCursorTimestamp(collectionMaxUpdatedAt, nowMillis);
  if (clampedMax > safePrev) return clampedMax;
  if (safePrev === 0) return Math.trunc(nowMillis);
  return safePrev;
}

/**
 * Ensures a Firestore document's path ID (`doc.id`) is never clobbered by a null, missing,
 * or whitespace-only `id` field inside `doc.data()` (exact parity with macOS `queryCollection`).
 */
export function normalizeFirestoreDocWithId(
  docId: string,
  rawData: Record<string, unknown> | undefined | null
): Record<string, unknown> {
  const base: Record<string, unknown> =
    rawData && typeof rawData === 'object' ? { ...rawData } : {};
  const fieldId = typeof base.id === 'string' ? base.id.trim() : '';
  const fallbackId = typeof docId === 'string' ? docId.trim() : '';
  base.id = fieldId.length > 0 ? fieldId : fallbackId;
  return base;
}

export interface OrphanContainerCheckResult {
  hasOrphanProjectRef: boolean;
  hasOrphanAreaRef: boolean;
  hasOrphanHeadingRef: boolean;
}

/**
 * Detects whether local IndexedDB holds active tasks, headings, or projects referencing a parent
 * Project, Area, or Heading ID that is completely absent from the local object store (e.g. skipped
 * by an interrupted sync), allowing `pullDeltaRecordsFromFirestore` to self-heal from `since = 0`.
 */
export function detectMissingReferencedEntityContainers(snapshot: {
  areas: readonly Pick<AreaRecord, 'id'>[];
  projects: readonly Pick<ProjectRecord, 'id' | 'areaId' | 'isDeleted'>[];
  headings: readonly Pick<HeadingRecord, 'id' | 'projectId' | 'isDeleted'>[];
  tasks: readonly Pick<TaskRecord, 'id' | 'projectId' | 'areaId' | 'headingId' | 'isDeleted'>[];
}): OrphanContainerCheckResult {
  const areaIds = new Set<string>();
  for (const a of snapshot.areas) {
    const id = a.id?.trim();
    if (id) areaIds.add(id);
  }
  const projectIds = new Set<string>();
  for (const p of snapshot.projects) {
    const id = p.id?.trim();
    if (id) projectIds.add(id);
  }
  const headingIds = new Set<string>();
  for (const h of snapshot.headings) {
    const id = h.id?.trim();
    if (id) headingIds.add(id);
  }

  let hasOrphanProjectRef = false;
  let hasOrphanAreaRef = false;
  let hasOrphanHeadingRef = false;

  for (const p of snapshot.projects) {
    if (p.isDeleted) continue;
    const aid = p.areaId?.trim();
    if (aid && !areaIds.has(aid)) {
      hasOrphanAreaRef = true;
    }
  }

  for (const h of snapshot.headings) {
    if (h.isDeleted) continue;
    const pid = h.projectId?.trim();
    if (pid && !projectIds.has(pid)) {
      hasOrphanProjectRef = true;
    }
  }

  for (const t of snapshot.tasks) {
    if (t.isDeleted) continue;
    const pid = t.projectId?.trim();
    if (pid && !projectIds.has(pid)) {
      hasOrphanProjectRef = true;
    }
    const aid = t.areaId?.trim();
    if (aid && !areaIds.has(aid)) {
      hasOrphanAreaRef = true;
    }
    const hid = t.headingId?.trim();
    if (hid && !headingIds.has(hid)) {
      hasOrphanHeadingRef = true;
    }
  }

  return {
    hasOrphanProjectRef,
    hasOrphanAreaRef,
    hasOrphanHeadingRef
  };
}


export function chunkItems<T>(items: readonly T[], chunkSize: number): T[][] {
  const step = Math.max(1, Math.trunc(chunkSize));
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += step) {
    out.push(items.slice(i, i + step));
  }
  return out;
}

export function estimateBase64DecodedBytes(b64: string): number {
  const cleaned = b64.replace(/\s+/g, '');
  if (!cleaned) return 0;
  let padding = 0;
  if (cleaned.endsWith('==')) padding = 2;
  else if (cleaned.endsWith('=')) padding = 1;
  return Math.max(0, Math.floor((cleaned.length * 3) / 4) - padding);
}

/**
 * Non-destructive Firestore mapper for attachments:
 * - Always writes `localRelativePath: ''`.
 * - Only writes `dataBase64: ''` when `att.isDeleted === true` (or a genuine 0-byte file).
 * - When `!att.isDeleted` and raw byte size > 700,000 (or `dataBase64` is empty while `sizeBytes > 0`),
 *   OMITS `dataBase64` so `{ merge: true }` never overwrites an existing remote `dataBase64` with `''`.
 */
export function attachmentToFirestoreMap(att: AttachmentRecord): Record<string, unknown> {
  const sizeBytes = Math.max(0, Math.trunc(finiteNumber(att.sizeBytes)));
  const rawBase64 = typeof att.dataBase64 === 'string' ? att.dataBase64 : '';
  const decodedBytes = rawBase64 ? estimateBase64DecodedBytes(rawBase64) : 0;
  const effectiveRawBytes = Math.max(sizeBytes, decodedBytes);

  const map: Record<string, unknown> = {
    id: att.id,
    taskId: att.taskId ?? null,
    projectId: att.projectId ?? null,
    fileName: att.fileName,
    mimeType: att.mimeType,
    sizeBytes,
    localRelativePath: '',
    sortOrder: finiteNumber(att.sortOrder),
    createdAt: Math.trunc(finiteNumber(att.createdAt)),
    updatedAt: Math.trunc(finiteNumber(att.updatedAt)),
    isDeleted: Boolean(att.isDeleted)
  };

  if (att.isDeleted) {
    map.dataBase64 = '';
  } else if (
    effectiveRawBytes <= MAX_ATTACHMENT_INLINE_BYTES &&
    !(rawBase64.length === 0 && sizeBytes > 0)
  ) {
    map.dataBase64 = rawBase64;
  }

  return map;
}

export interface NtfyRelayBatchInput {
  userId: string;
  deviceId: string;
  batchTimestamp: number;
  tags?: Record<string, unknown>[];
  areas?: Record<string, unknown>[];
  projects?: Record<string, unknown>[];
  headings?: Record<string, unknown>[];
  tasks?: Record<string, unknown>[];
  checklistItems?: Record<string, unknown>[];
  attachments?: Record<string, unknown>[];
  projectTemplates?: Record<string, unknown>[];
}

export function buildBoundedNtfyRelayPayload(
  input: NtfyRelayBatchInput,
  maxBytes: number = MAX_NTFY_PAYLOAD_BYTES
): string | null {
  const hasAny =
    (input.tags?.length ?? 0) > 0 ||
    (input.areas?.length ?? 0) > 0 ||
    (input.projects?.length ?? 0) > 0 ||
    (input.headings?.length ?? 0) > 0 ||
    (input.tasks?.length ?? 0) > 0 ||
    (input.checklistItems?.length ?? 0) > 0 ||
    (input.attachments?.length ?? 0) > 0 ||
    (input.projectTemplates?.length ?? 0) > 0;
  if (!hasAny) return null;

  const compactAttachments = input.attachments?.map((att) => {
    const copy = { ...att };
    if (!copy.isDeleted) {
      delete copy.dataBase64;
    }
    return copy;
  });

  const payloadObj: Record<string, unknown> = {
    userId: input.userId,
    deviceId: input.deviceId,
    batchTimestamp: Math.trunc(input.batchTimestamp)
  };
  if (input.tags?.length) payloadObj.tags = input.tags;
  if (input.areas?.length) payloadObj.areas = input.areas;
  if (input.projects?.length) payloadObj.projects = input.projects;
  if (input.headings?.length) payloadObj.headings = input.headings;
  if (input.tasks?.length) payloadObj.tasks = input.tasks;
  if (input.checklistItems?.length) payloadObj.checklistItems = input.checklistItems;
  if (compactAttachments?.length) payloadObj.attachments = compactAttachments;
  if (input.projectTemplates?.length) payloadObj.projectTemplates = input.projectTemplates;

  const json = JSON.stringify(payloadObj);
  const byteLength = new TextEncoder().encode(json).byteLength;
  if (byteLength >= maxBytes) {
    return null;
  }
  return json;
}

/**
 * Builds either the inline compact delta payload (when `< maxBytes`) or a tiny wake-only envelope
 * so peers subscribed via SSE / HTTP stream immediately trigger an incremental Firestore delta pull
 * even when notes or batch sizes exceed 3,800 bytes.
 */
export function buildNtfyRelayOrWakePayload(
  input: NtfyRelayBatchInput,
  maxBytes: number = MAX_NTFY_PAYLOAD_BYTES
): string | null {
  const inline = buildBoundedNtfyRelayPayload(input, maxBytes);
  if (inline !== null) return inline;

  const hasAny =
    (input.tags?.length ?? 0) > 0 ||
    (input.areas?.length ?? 0) > 0 ||
    (input.projects?.length ?? 0) > 0 ||
    (input.headings?.length ?? 0) > 0 ||
    (input.tasks?.length ?? 0) > 0 ||
    (input.checklistItems?.length ?? 0) > 0 ||
    (input.attachments?.length ?? 0) > 0 ||
    (input.projectTemplates?.length ?? 0) > 0;
  if (!hasAny) return null;

  const wakeJson = JSON.stringify({
    userId: input.userId,
    deviceId: input.deviceId,
    batchTimestamp: Math.trunc(input.batchTimestamp),
    wakeOnly: true
  });
  return new TextEncoder().encode(wakeJson).byteLength < maxBytes ? wakeJson : null;
}

/**
 * Builds up to `maxChunks` bounded inline payloads (`< maxBytes`) for legacy `tb_sync_*` channels
 * WITHOUT ever emitting a `wakeOnly: true` stub (since v1.8 Android ignores `wakeOnly` and would
 * advance its `lastSyncTimestamp` cursor without applying records).
 */
export function buildLegacyCompatibleNtfyChunks(
  input: NtfyRelayBatchInput,
  maxBytes: number = MAX_NTFY_PAYLOAD_BYTES,
  maxChunks = 3
): string[] {
  const single = buildBoundedNtfyRelayPayload(input, maxBytes);
  if (single !== null) return [single];

  const out: string[] = [];
  const appendFieldChunks = (
    field:
      | 'tags'
      | 'areas'
      | 'projects'
      | 'headings'
      | 'tasks'
      | 'checklistItems'
      | 'attachments'
      | 'projectTemplates',
    items: Record<string, unknown>[] | undefined
  ) => {
    if (!items || items.length === 0 || out.length >= maxChunks) return;
    const candidate = buildBoundedNtfyRelayPayload(
      {
        userId: input.userId,
        deviceId: input.deviceId,
        batchTimestamp: input.batchTimestamp,
        [field]: items
      },
      maxBytes
    );
    if (candidate !== null) {
      out.push(candidate);
    } else if (items.length > 1) {
      const mid = Math.floor(items.length / 2);
      appendFieldChunks(field, items.slice(0, mid));
      appendFieldChunks(field, items.slice(mid));
    }
  };

  appendFieldChunks('tags', input.tags);
  appendFieldChunks('areas', input.areas);
  appendFieldChunks('projects', input.projects);
  appendFieldChunks('headings', input.headings);
  appendFieldChunks('tasks', input.tasks);
  appendFieldChunks('checklistItems', input.checklistItems);
  appendFieldChunks('attachments', input.attachments);
  appendFieldChunks('projectTemplates', input.projectTemplates);
  return out.slice(0, maxChunks);
}

/**
 * Resolves the merged `endDate` for an incoming remote `headings` delta so `v2.x` clients
 * (which omit `endDate` from Firestore/ntfy payloads) preserve any existing local `endDate`
 * while `v3.0+` clients explicitly sending `endDate: null` or a timestamp apply cleanly.
 */
export function resolveMergedHeadingEndDate(
  local: Pick<HeadingRecord, 'endDate'> | undefined,
  remote: Pick<HeadingRecord, 'endDate' | 'isDeleted'> & { omittedEndDate?: boolean }
): number | null {
  if (remote.omittedEndDate && !remote.isDeleted && local !== undefined) {
    return local.endDate ?? null;
  }
  return remote.endDate ?? null;
}
