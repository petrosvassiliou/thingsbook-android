import type { AttachmentRecord } from '../domain/types.ts';

export const MAX_ATTACHMENT_INLINE_BYTES = 700_000;
export const ATTACHMENT_BATCH_CHUNK_SIZE = 8;
export const METADATA_BATCH_CHUNK_SIZE = 450;
export const CLOCK_SKEW_OVERLAP_MS = 120_000;
export const MAX_FUTURE_CURSOR_DRIFT_MS = 60_000;
export const MAX_NTFY_PAYLOAD_BYTES = 3_800;
export const ATTACHMENT_BACKFILL_STORAGE_PREFIX = 'thingsbook_att_backfill_v1_';

function finiteNumber(v: unknown, fallback = 0): number {
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : fallback;
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
    (input.attachments?.length ?? 0) > 0;
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

  const json = JSON.stringify(payloadObj);
  const byteLength = new TextEncoder().encode(json).byteLength;
  if (byteLength >= maxBytes) {
    return null;
  }
  return json;
}
