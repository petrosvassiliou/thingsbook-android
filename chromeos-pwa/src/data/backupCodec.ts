import type {
  AreaRecord,
  ChecklistItemRecord,
  HeadingRecord,
  ProjectRecord,
  ProjectStatus,
  RecurrenceRule,
  ScheduleState,
  TagRecord,
  TaskRecord,
  TaskStatus
} from '../domain/types';

export const BACKUP_MAGIC = 'THINGSBOOK_BACKUP_V1';
export const FORMAT_VERSION = 1;
const KDF_ALGORITHM = 'PBKDF2WithHmacSHA256';
const CIPHER_TRANSFORMATION = 'AES/GCM/NoPadding';
const KDF_ITERATIONS = 120_000;
const SALT_BYTES = 16;
const IV_BYTES = 12;
const GCM_TAG_BITS = 128;
const MAX_FILE_BYTES = 30 * 1024 * 1024;
const MAX_DECOMPRESSED_BYTES = 50 * 1024 * 1024;

export interface ThingsbookBackupEnvelope {
  magic: string;
  formatVersion: number;
  appVersionName: string;
  appVersionCode: number;
  createdAt: number;
  encrypted: boolean;
  kdf: string | null;
  kdfIterations: number | null;
  saltBase64: string | null;
  cipher: string | null;
  ivBase64: string | null;
  gcmTagBits: number | null;
  payloadGzipBase64: string | null;
  ciphertextBase64: string | null;
}

export interface BackupPreferencesDto {
  sidebarVisible?: boolean | null;
  dailyDigestEnabled?: boolean | null;
  dailyDigestHour?: number | null;
  dailyDigestMinute?: number | null;
  languageTag?: string | null;
  calendarEventsEnabled?: boolean | null;
  selectedCalendarIds?: string[] | null;
}

export interface ThingsbookBackupPayload {
  schemaVersion: number;
  createdAt: number;
  appVersionName: string;
  areas: Array<{
    id: string;
    title: string;
    sortOrder: number;
    tagIdsJson: string;
    createdAt: number;
    updatedAt: number;
  }>;
  projects: Array<{
    id: string;
    areaId: string | null;
    title: string;
    notes: string;
    status: string;
    scheduleState: string;
    whenDate: number | null;
    deadline: number | null;
    completedAt: number | null;
    sortOrder: number;
    tagIdsJson: string;
    createdAt: number;
    updatedAt: number;
  }>;
  headings: Array<{
    id: string;
    projectId: string;
    title: string;
    sortOrder: number;
    isArchived: boolean;
    createdAt: number;
    updatedAt: number;
  }>;
  tasks: Array<{
    id: string;
    title: string;
    notes: string;
    status: string;
    scheduleState: string;
    whenDate: number | null;
    deadline: number | null;
    reminderTime: number | null;
    completedAt: number | null;
    projectId: string | null;
    headingId: string | null;
    areaId: string | null;
    recurrenceRuleJson: string | null;
    sortOrder: number;
    tagIdsJson: string;
    createdAt: number;
    updatedAt: number;
  }>;
  checklistItems: Array<{
    id: string;
    taskId: string;
    title: string;
    isCompleted: boolean;
    sortOrder: number;
    createdAt: number;
    updatedAt: number;
  }>;
  tags: Array<{
    id: string;
    title: string;
    parentTagId: string | null;
    shortcut: string | null;
    sortOrder: number;
    createdAt: number;
    updatedAt: number;
  }>;
  taskTagRefs: Array<{ taskId: string; tagId: string }>;
  projectTagRefs: Array<{ projectId: string; tagId: string }>;
  preferences: BackupPreferencesDto;
}

export interface BackupSummary {
  createdAt: number;
  appVersionName: string;
  encrypted: boolean;
  areasCount: number;
  projectsCount: number;
  headingsCount: number;
  tasksCount: number;
  checklistItemsCount: number;
  tagsCount: number;
}

export type BackupInspection =
  | { kind: 'encrypted'; createdAt: number; appVersionName: string }
  | { kind: 'plain'; createdAt: number; appVersionName: string; summary: BackupSummary };

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

function base64ToBytes(b64: string): Uint8Array {
  const cleaned = b64.replace(/\s+/g, '');
  const binary = atob(cleaned);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
}

async function gzipCompress(data: Uint8Array): Promise<Uint8Array> {
  const cs = new CompressionStream('gzip');
  const writer = cs.writable.getWriter();
  await writer.write(new Uint8Array(data));
  await writer.close();
  const reader = cs.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    total += value.byteLength;
  }
  const result = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    result.set(c, offset);
    offset += c.byteLength;
  }
  return result;
}

async function gzipDecompressBounded(compressed: Uint8Array): Promise<Uint8Array> {
  const ds = new DecompressionStream('gzip');
  const writer = ds.writable.getWriter();
  writer.write(new Uint8Array(compressed)).catch(() => {});
  writer.close().catch(() => {});
  const reader = ds.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_DECOMPRESSED_BYTES) {
      await reader.cancel();
      throw new Error('Backup exceeds 50 MB decompression safety limit.');
    }
    chunks.push(value);
  }
  const result = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    result.set(c, offset);
    offset += c.byteLength;
  }
  return result;
}

async function deriveAesGcmKey(
  password: string,
  salt: Uint8Array,
  iterations: number,
  usage: KeyUsage[]
): Promise<CryptoKey> {
  const pwBytes = new TextEncoder().encode(password);
  const baseKey = await crypto.subtle.importKey('raw', pwBytes, 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: new Uint8Array(salt),
      iterations,
      hash: 'SHA-256'
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    usage
  );
}

function buildAad(magic: string, version: number): Uint8Array {
  return new TextEncoder().encode(`${magic}|${version}`);
}

export function summarizeBackupPayload(
  payload: ThingsbookBackupPayload,
  encrypted: boolean
): BackupSummary {
  return {
    createdAt: payload.createdAt,
    appVersionName: payload.appVersionName,
    encrypted,
    areasCount: (payload.areas || []).length,
    projectsCount: (payload.projects || []).length,
    headingsCount: (payload.headings || []).length,
    tasksCount: (payload.tasks || []).length,
    checklistItemsCount: (payload.checklistItems || []).length,
    tagsCount: (payload.tags || []).length
  };
}

export function buildBackupPayloadFromEntities(params: {
  areas: AreaRecord[];
  projects: ProjectRecord[];
  headings: HeadingRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
  preferences: BackupPreferencesDto;
  appVersionName: string;
}): ThingsbookBackupPayload {
  const now = Date.now();
  const areas = params.areas.filter((a) => !a.isDeleted);
  const projects = params.projects.filter((p) => !p.isDeleted);
  const headings = params.headings.filter((h) => !h.isDeleted);
  const tasks = params.tasks.filter((t) => !t.isDeleted);
  const checklistItems = params.checklistItems.filter((c) => !c.isDeleted);
  const tags = params.tags.filter((tg) => !tg.isDeleted);
  const validTagIds = new Set(tags.map((tg) => tg.id));

  const taskTagRefs: Array<{ taskId: string; tagId: string }> = [];
  for (const t of tasks) {
    for (const tagId of t.tagIds || []) {
      if (validTagIds.has(tagId)) taskTagRefs.push({ taskId: t.id, tagId });
    }
  }

  const projectTagRefs: Array<{ projectId: string; tagId: string }> = [];
  for (const p of projects) {
    for (const tagId of p.tagIds || []) {
      if (validTagIds.has(tagId)) projectTagRefs.push({ projectId: p.id, tagId });
    }
  }

  return {
    schemaVersion: FORMAT_VERSION,
    createdAt: now,
    appVersionName: params.appVersionName,
    areas: areas.map((a) => ({
      id: a.id,
      title: a.title,
      sortOrder: Number.isFinite(a.sortOrder) ? a.sortOrder : 0,
      tagIdsJson: JSON.stringify(a.tagIds || []),
      createdAt: a.createdAt,
      updatedAt: a.updatedAt
    })),
    projects: projects.map((p) => ({
      id: p.id,
      areaId: p.areaId,
      title: p.title,
      notes: p.notes || '',
      status: p.status,
      scheduleState: p.scheduleState,
      whenDate: p.whenDate,
      deadline: p.deadline,
      completedAt: p.completedAt,
      sortOrder: Number.isFinite(p.sortOrder) ? p.sortOrder : 0,
      tagIdsJson: JSON.stringify(p.tagIds || []),
      createdAt: p.createdAt,
      updatedAt: p.updatedAt
    })),
    headings: headings.map((h) => ({
      id: h.id,
      projectId: h.projectId,
      title: h.title,
      sortOrder: Number.isFinite(h.sortOrder) ? h.sortOrder : 0,
      isArchived: Boolean(h.isArchived),
      createdAt: h.createdAt,
      updatedAt: h.updatedAt
    })),
    tasks: tasks.map((t) => ({
      id: t.id,
      title: t.title,
      notes: t.notes || '',
      status: t.status,
      scheduleState: t.scheduleState,
      whenDate: t.whenDate,
      deadline: t.deadline,
      reminderTime: t.reminderTime,
      completedAt: t.completedAt,
      projectId: t.projectId,
      headingId: t.headingId,
      areaId: t.areaId,
      recurrenceRuleJson: t.recurrenceRule ? JSON.stringify(t.recurrenceRule) : null,
      sortOrder: Number.isFinite(t.sortOrder) ? t.sortOrder : 0,
      tagIdsJson: JSON.stringify(t.tagIds || []),
      createdAt: t.createdAt,
      updatedAt: t.updatedAt
    })),
    checklistItems: checklistItems.map((c) => ({
      id: c.id,
      taskId: c.taskId,
      title: c.title,
      isCompleted: Boolean(c.isCompleted),
      sortOrder: Number.isFinite(c.sortOrder) ? c.sortOrder : 0,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt
    })),
    tags: tags.map((tg) => ({
      id: tg.id,
      title: tg.title,
      parentTagId: tg.parentTagId,
      shortcut: tg.shortcut,
      sortOrder: Number.isFinite(tg.sortOrder) ? tg.sortOrder : 0,
      createdAt: tg.createdAt,
      updatedAt: tg.updatedAt
    })),
    taskTagRefs,
    projectTagRefs,
    preferences: params.preferences
  };
}

export async function encodeThingsBackup(
  payload: ThingsbookBackupPayload,
  password: string | null,
  appVersionName = '1.6.23-chromeos',
  appVersionCode = 40
): Promise<Uint8Array> {
  const jsonBytes = new TextEncoder().encode(JSON.stringify(payload));
  const compressed = await gzipCompress(jsonBytes);
  const hasPassword = Boolean(password && password.length > 0);

  let envelope: ThingsbookBackupEnvelope;
  if (!hasPassword) {
    envelope = {
      magic: BACKUP_MAGIC,
      formatVersion: FORMAT_VERSION,
      appVersionName,
      appVersionCode,
      createdAt: payload.createdAt,
      encrypted: false,
      kdf: null,
      kdfIterations: null,
      saltBase64: null,
      cipher: null,
      ivBase64: null,
      gcmTagBits: null,
      payloadGzipBase64: bytesToBase64(compressed),
      ciphertextBase64: null
    };
  } else {
    const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
    const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
    const key = await deriveAesGcmKey(password!, salt, KDF_ITERATIONS, ['encrypt']);
    const aad = buildAad(BACKUP_MAGIC, FORMAT_VERSION);
    const cipherBuf = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv,
        additionalData: new Uint8Array(aad),
        tagLength: GCM_TAG_BITS
      },
      key,
      new Uint8Array(compressed)
    );
    envelope = {
      magic: BACKUP_MAGIC,
      formatVersion: FORMAT_VERSION,
      appVersionName,
      appVersionCode,
      createdAt: payload.createdAt,
      encrypted: true,
      kdf: KDF_ALGORITHM,
      kdfIterations: KDF_ITERATIONS,
      saltBase64: bytesToBase64(salt),
      cipher: CIPHER_TRANSFORMATION,
      ivBase64: bytesToBase64(iv),
      gcmTagBits: GCM_TAG_BITS,
      payloadGzipBase64: null,
      ciphertextBase64: bytesToBase64(new Uint8Array(cipherBuf))
    };
  }

  return new TextEncoder().encode(JSON.stringify(envelope, null, 2));
}

function parseEnvelope(rawBytes: Uint8Array): ThingsbookBackupEnvelope {
  if (!rawBytes || rawBytes.byteLength === 0) {
    throw new Error('The selected backup file is empty.');
  }
  if (rawBytes.byteLength > MAX_FILE_BYTES) {
    throw new Error('Backup file exceeds 30 MB limit.');
  }
  const text = new TextDecoder('utf-8').decode(rawBytes).replace(/^\uFEFF/, '').trim();
  let env: ThingsbookBackupEnvelope;
  try {
    env = JSON.parse(text) as ThingsbookBackupEnvelope;
  } catch {
    throw new Error('Invalid file format: Not a valid Thingsbook .tbbackup file.');
  }
  if (env.magic !== BACKUP_MAGIC) {
    throw new Error(`Unknown backup magic header (${String(env.magic)}).`);
  }
  if (env.formatVersion < 1 || env.formatVersion > FORMAT_VERSION) {
    throw new Error(`Unsupported backup format version v${env.formatVersion}.`);
  }
  return env;
}

export async function inspectThingsBackup(rawBytes: Uint8Array): Promise<BackupInspection> {
  const env = parseEnvelope(rawBytes);
  if (env.encrypted) {
    return {
      kind: 'encrypted',
      createdAt: env.createdAt || 0,
      appVersionName: env.appVersionName || ''
    };
  }
  const payload = await decodeThingsBackup(rawBytes, null);
  return {
    kind: 'plain',
    createdAt: env.createdAt || payload.createdAt || 0,
    appVersionName: env.appVersionName || payload.appVersionName || '',
    summary: summarizeBackupPayload(payload, false)
  };
}

export async function decodeThingsBackup(
  rawBytes: Uint8Array,
  password: string | null
): Promise<ThingsbookBackupPayload> {
  const env = parseEnvelope(rawBytes);
  let compressed: Uint8Array;

  if (!env.encrypted) {
    if (!env.payloadGzipBase64) {
      throw new Error('Unencrypted backup contains no payload.');
    }
    compressed = base64ToBytes(env.payloadGzipBase64);
  } else {
    if (!password) {
      throw new Error('PASSWORD_REQUIRED');
    }
    if (!env.saltBase64 || !env.ivBase64 || !env.ciphertextBase64) {
      throw new Error('Encrypted backup is missing salt, IV, or ciphertext.');
    }
    const salt = base64ToBytes(env.saltBase64);
    const iv = base64ToBytes(env.ivBase64);
    const ciphertext = base64ToBytes(env.ciphertextBase64);
    const iterations = Math.max(10_000, Math.min(1_000_000, env.kdfIterations ?? KDF_ITERATIONS));
    const tagLength = [96, 104, 112, 120, 128].includes(env.gcmTagBits ?? 128)
      ? (env.gcmTagBits ?? 128)
      : GCM_TAG_BITS;
    const key = await deriveAesGcmKey(password, salt, iterations, ['decrypt']);
    const aad = buildAad(env.magic, env.formatVersion);
    try {
      const plainBuf = await crypto.subtle.decrypt(
        {
          name: 'AES-GCM',
          iv: new Uint8Array(iv),
          additionalData: new Uint8Array(aad),
          tagLength
        },
        key,
        new Uint8Array(ciphertext)
      );
      compressed = new Uint8Array(plainBuf);
    } catch {
      throw new Error('WRONG_PASSWORD');
    }
  }

  const decompressed = await gzipDecompressBounded(compressed);
  const jsonStr = new TextDecoder('utf-8').decode(decompressed);
  return JSON.parse(jsonStr) as ThingsbookBackupPayload;
}

export function safeParseTagList(tagIdsJson: string | undefined | null): string[] {
  if (!tagIdsJson) return [];
  try {
    const arr = JSON.parse(tagIdsJson);
    return Array.isArray(arr) ? arr.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

export function safeParseRecurrence(raw: string | null | undefined): RecurrenceRule | null {
  if (!raw || !raw.trim()) return null;
  try {
    const obj = JSON.parse(raw);
    if (obj && typeof obj === 'object' && typeof obj.unit === 'string') {
      return {
        mode: obj.mode === 'AFTER_COMPLETION' ? 'AFTER_COMPLETION' : 'FIXED_SCHEDULE',
        interval: Math.max(1, Number(obj.interval) || 1),
        unit: ['DAYS', 'WEEKS', 'MONTHS', 'YEARS'].includes(obj.unit) ? obj.unit : 'DAYS',
        daysOfWeek: Array.isArray(obj.daysOfWeek)
          ? obj.daysOfWeek.map(Number).filter((d: number) => d >= 1 && d <= 7)
          : [],
        dayOfMonth: obj.dayOfMonth != null ? Number(obj.dayOfMonth) : null,
        endDateEpochMillis: obj.endDateEpochMillis != null ? Number(obj.endDateEpochMillis) : null
      };
    }
  } catch {
    // ignore invalid recurrence JSON
  }
  return null;
}

export function normalizeTaskStatus(val: string | undefined): TaskStatus {
  const up = (val || '').trim().toUpperCase();
  if (up === 'COMPLETED' || up === 'CANCELED') return up;
  return 'OPEN';
}

export function normalizeProjectStatus(val: string | undefined): ProjectStatus {
  const up = (val || '').trim().toUpperCase();
  if (up === 'COMPLETED' || up === 'CANCELED') return up;
  return 'ACTIVE';
}

export function normalizeScheduleState(val: string | undefined, fallback: ScheduleState = 'INBOX'): ScheduleState {
  const up = (val || '').trim().toUpperCase();
  if (
    up === 'INBOX' ||
    up === 'TODAY' ||
    up === 'THIS_EVENING' ||
    up === 'UPCOMING' ||
    up === 'ANYTIME' ||
    up === 'SOMEDAY'
  ) {
    return up;
  }
  return fallback;
}
