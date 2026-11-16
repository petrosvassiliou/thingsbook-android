import {
  EmailAuthProvider,
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  linkWithCredential,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updatePassword,
  type Auth,
  type Unsubscribe as AuthUnsubscribe
} from 'firebase/auth';
import {
  collection,
  doc,
  getDocs,
  onSnapshot,
  query,
  where,
  writeBatch,
  type Unsubscribe as FirestoreUnsubscribe
} from 'firebase/firestore';
import type {
  AreaRecord,
  AttachmentRecord,
  ChecklistItemRecord,
  CloudSyncStatus,
  HeadingRecord,
  ProjectRecord,
  ProjectTemplate,
  TagRecord,
  TaskRecord
} from '../domain/types';
import {
  normalizeProjectStatus,
  normalizeScheduleState,
  normalizeTaskStatus,
  safeParseRecurrence
} from './backupCodec';
import { hasStoredGoogleCalendarAccountForEmail } from './calendarService';
import {
  getFirebaseAuthInstance,
  getFirestoreInstance,
  getGoogleCalendarAuthInstance
} from './firebaseClient';
import { loadPreferences, localDb, savePreferences } from './localDb';
import {
  ATTACHMENT_BACKFILL_STORAGE_PREFIX,
  ATTACHMENT_BATCH_CHUNK_SIZE,
  FAST_SYNC_COALESCE_MS,
  METADATA_BATCH_CHUNK_SIZE,
  PROJECTS_REVIEW_BACKFILL_STORAGE_PREFIX,
  attachmentToFirestoreMap,
  buildNtfyRelayOrWakePayload,
  chunkItems,
  clampRemoteCursorTimestamp,
  computeEffectivePullCursor,
  formatUidWakeChannelFromHex,
  formatUidWakeSeed
} from './syncUtils';

function extractAuthErrorCode(err: unknown): string {
  const code = (err as { code?: unknown })?.code;
  if (typeof code === 'string' && code.length > 0) return code;
  const msg = err instanceof Error ? err.message : String(err);
  const match = /\((auth\/[a-z0-9-]+)\)/i.exec(msg);
  return match ? match[1].toLowerCase() : '';
}

function isInvalidPasswordOrUnlinkedError(err: unknown): boolean {
  const code = extractAuthErrorCode(err);
  return (
    code === 'auth/invalid-credential' ||
    code === 'auth/invalid-login-credentials' ||
    code === 'auth/wrong-password'
  );
}

export function isPasswordRecoveryCandidateError(err: unknown): boolean {
  if (isInvalidPasswordOrUnlinkedError(err)) return true;
  const code = extractAuthErrorCode(err);
  return code === 'auth/popup-blocked' || code === 'auth/popup-closed-by-user';
}

export function formatAuthErrorMessage(err: unknown): string {
  const code = extractAuthErrorCode(err);
  if (
    code === 'auth/invalid-credential' ||
    code === 'auth/invalid-login-credentials' ||
    code === 'auth/wrong-password'
  ) {
    return 'Incorrect password, or Google Sign-In / Google Calendar unlinked password login for this email. Click "Verify with Google & Restore Password" below to sign in and re-link your password.';
  }
  if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') {
    return 'Google verification window was closed before completing. Click "Verify with Google & Restore Password" to try again.';
  }
  if (code === 'auth/popup-blocked') {
    return 'Popup window was blocked by the browser. Click "Verify with Google & Restore Password" below (or allow popups for this site).';
  }
  if (code === 'auth/too-many-requests') {
    return 'Too many sign-in attempts. Please wait a moment or sign in with Google.';
  }
  if (code === 'auth/network-request-failed') {
    return 'Network connection error. Please check your internet connection and try again.';
  }
  return err instanceof Error ? err.message : 'Authentication failed';
}

export interface CloudSyncSnapshot {
  status: CloudSyncStatus;
  userEmailOrId: string | null;
  userId: string | null;
  lastSyncedAt: number;
  errorMessage: string | null;
}

interface PersistedSyncMeta {
  userId: string;
  email: string;
  channelTopic: string;
  deviceId: string;
}

const SYNC_META_STORAGE_KEY = 'thingsbook_chromeos_sync_meta_v1';
const LAST_SYNC_PREFIX = 'thingsbook_last_sync_ts_';

async function sha256Hex(input: string): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

function finiteNumber(v: unknown, fallback = 0): number {
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function nullableNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function nullableString(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t.length > 0 ? t : null;
}

function stringList(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x): x is string => typeof x === 'string' && x.trim().length > 0);
}

// ==================== Exact Android Firestore DTO Mappers ====================

function tagToFirestoreMap(tag: TagRecord): Record<string, unknown> {
  return {
    id: tag.id,
    title: tag.title,
    parentTagId: tag.parentTagId ?? null,
    shortcut: tag.shortcut ?? null,
    sortOrder: finiteNumber(tag.sortOrder),
    createdAt: Math.trunc(finiteNumber(tag.createdAt)),
    updatedAt: Math.trunc(finiteNumber(tag.updatedAt)),
    isDeleted: Boolean(tag.isDeleted)
  };
}

function tagFromFirestoreMap(map: Record<string, unknown>): TagRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  return {
    id,
    title: typeof map.title === 'string' ? map.title : '',
    parentTagId: nullableString(map.parentTagId),
    shortcut: nullableString(map.shortcut),
    sortOrder: finiteNumber(map.sortOrder),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
  };
}

function areaToFirestoreMap(area: AreaRecord): Record<string, unknown> {
  return {
    id: area.id,
    title: area.title,
    sortOrder: finiteNumber(area.sortOrder),
    tagIds: area.tagIds || [],
    createdAt: Math.trunc(finiteNumber(area.createdAt)),
    updatedAt: Math.trunc(finiteNumber(area.updatedAt)),
    isDeleted: Boolean(area.isDeleted)
  };
}

function areaFromFirestoreMap(map: Record<string, unknown>): AreaRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  return {
    id,
    title: typeof map.title === 'string' ? map.title : '',
    sortOrder: finiteNumber(map.sortOrder),
    tagIds: stringList(map.tagIds),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
  };
}

function projectToFirestoreMap(project: ProjectRecord): Record<string, unknown> {
  const map: Record<string, unknown> = {
    id: project.id,
    areaId: project.areaId ?? null,
    title: project.title,
    notes: project.notes || '',
    status: project.status,
    scheduleState: project.scheduleState,
    whenDate: project.whenDate != null ? Math.trunc(project.whenDate) : null,
    deadline: project.deadline != null ? Math.trunc(project.deadline) : null,
    completedAt: project.completedAt != null ? Math.trunc(project.completedAt) : null,
    lastReviewedAt: project.lastReviewedAt != null ? Math.trunc(project.lastReviewedAt) : null,
    sortOrder: finiteNumber(project.sortOrder),
    tagIds: project.tagIds || [],
    createdAt: Math.trunc(finiteNumber(project.createdAt)),
    updatedAt: Math.trunc(finiteNumber(project.updatedAt)),
    isDeleted: Boolean(project.isDeleted)
  };
  if (project.purgedFromTrash) {
    map.purgedFromTrash = true;
  }
  return map;
}

function projectFromFirestoreMap(map: Record<string, unknown>): ProjectRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  return {
    id,
    areaId: nullableString(map.areaId),
    title: typeof map.title === 'string' ? map.title : '',
    notes: typeof map.notes === 'string' ? map.notes : '',
    status: normalizeProjectStatus(typeof map.status === 'string' ? map.status : 'ACTIVE'),
    scheduleState: normalizeScheduleState(
      typeof map.scheduleState === 'string' ? map.scheduleState : 'ANYTIME',
      'ANYTIME'
    ),
    whenDate: nullableNumber(map.whenDate),
    deadline: nullableNumber(map.deadline),
    completedAt: nullableNumber(map.completedAt),
    lastReviewedAt: nullableNumber(map.lastReviewedAt),
    sortOrder: finiteNumber(map.sortOrder),
    tagIds: stringList(map.tagIds),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false,
    purgedFromTrash: Boolean(map.purgedFromTrash)
  };
}

function headingToFirestoreMap(heading: HeadingRecord): Record<string, unknown> {
  return {
    id: heading.id,
    projectId: heading.projectId,
    title: heading.title,
    sortOrder: finiteNumber(heading.sortOrder),
    isArchived: Boolean(heading.isArchived),
    createdAt: Math.trunc(finiteNumber(heading.createdAt)),
    updatedAt: Math.trunc(finiteNumber(heading.updatedAt)),
    isDeleted: Boolean(heading.isDeleted)
  };
}

function headingFromFirestoreMap(map: Record<string, unknown>): HeadingRecord | null {
  const id = nullableString(map.id);
  const projectId = nullableString(map.projectId);
  if (!id || !projectId) return null;
  return {
    id,
    projectId,
    title: typeof map.title === 'string' ? map.title : '',
    sortOrder: finiteNumber(map.sortOrder),
    isArchived: Boolean(map.isArchived),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
  };
}

function taskToFirestoreMap(task: TaskRecord): Record<string, unknown> {
  const map: Record<string, unknown> = {
    id: task.id,
    title: task.title,
    notes: task.notes || '',
    status: task.status,
    scheduleState: task.scheduleState,
    whenDate: task.whenDate != null ? Math.trunc(task.whenDate) : null,
    deadline: task.deadline != null ? Math.trunc(task.deadline) : null,
    reminderTime: task.reminderTime != null ? Math.trunc(task.reminderTime) : null,
    scheduledStartMinutes:
      task.scheduledStartMinutes != null ? Math.trunc(task.scheduledStartMinutes) : null,
    durationMinutes:
      task.durationMinutes != null ? Math.trunc(task.durationMinutes) : null,
    completedAt: task.completedAt != null ? Math.trunc(task.completedAt) : null,
    projectId: task.projectId ?? null,
    headingId: task.headingId ?? null,
    areaId: task.areaId ?? null,
    recurrenceRule: task.recurrenceRule ? JSON.stringify(task.recurrenceRule) : null,
    sortOrder: finiteNumber(task.sortOrder),
    tagIds: task.tagIds || [],
    createdAt: Math.trunc(finiteNumber(task.createdAt)),
    updatedAt: Math.trunc(finiteNumber(task.updatedAt)),
    isDeleted: Boolean(task.isDeleted)
  };
  if (task.purgedFromTrash) {
    map.purgedFromTrash = true;
  }
  return map;
}

function taskFromFirestoreMap(map: Record<string, unknown>): TaskRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  const title = typeof map.title === 'string' ? map.title : '';
  const notes = typeof map.notes === 'string' ? map.notes : '';
  const isDeleted = Boolean(map.isDeleted);
  return {
    id,
    title,
    notes,
    status: normalizeTaskStatus(typeof map.status === 'string' ? map.status : 'OPEN'),
    scheduleState: normalizeScheduleState(
      typeof map.scheduleState === 'string' ? map.scheduleState : 'INBOX',
      'INBOX'
    ),
    whenDate: nullableNumber(map.whenDate),
    deadline: nullableNumber(map.deadline),
    reminderTime: nullableNumber(map.reminderTime),
    scheduledStartMinutes: nullableNumber(map.scheduledStartMinutes),
    durationMinutes: nullableNumber(map.durationMinutes),
    completedAt: nullableNumber(map.completedAt),
    projectId: nullableString(map.projectId),
    headingId: nullableString(map.headingId),
    areaId: nullableString(map.areaId),
    recurrenceRule: safeParseRecurrence(nullableString(map.recurrenceRule)),
    sortOrder: finiteNumber(map.sortOrder),
    tagIds: stringList(map.tagIds),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted,
    isDirty: false,
    purgedFromTrash: Boolean(map.purgedFromTrash || (isDeleted && !title && !notes))
  };
}

function checklistToFirestoreMap(item: ChecklistItemRecord): Record<string, unknown> {
  return {
    id: item.id,
    taskId: item.taskId,
    title: item.title,
    isCompleted: Boolean(item.isCompleted),
    sortOrder: finiteNumber(item.sortOrder),
    createdAt: Math.trunc(finiteNumber(item.createdAt)),
    updatedAt: Math.trunc(finiteNumber(item.updatedAt)),
    isDeleted: Boolean(item.isDeleted)
  };
}

function checklistFromFirestoreMap(map: Record<string, unknown>): ChecklistItemRecord | null {
  const id = nullableString(map.id);
  const taskId = nullableString(map.taskId);
  if (!id || !taskId) return null;
  return {
    id,
    taskId,
    title: typeof map.title === 'string' ? map.title : '',
    isCompleted: Boolean(map.isCompleted),
    sortOrder: finiteNumber(map.sortOrder),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
  };
}

function attachmentFromFirestoreMap(map: Record<string, unknown>): AttachmentRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  const taskId = nullableString(map.taskId);
  const projectId = nullableString(map.projectId);
  const isDeleted = Boolean(map.isDeleted);
  if (!isDeleted && !taskId && !projectId) return null;
  return {
    id,
    taskId,
    projectId,
    fileName: typeof map.fileName === 'string' ? map.fileName : 'attachment',
    mimeType: typeof map.mimeType === 'string' ? map.mimeType : 'application/octet-stream',
    sizeBytes: Math.trunc(finiteNumber(map.sizeBytes)),
    dataBase64: typeof map.dataBase64 === 'string' ? map.dataBase64 : '',
    sortOrder: finiteNumber(map.sortOrder),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted,
    isDirty: false
  };
}

function projectTemplateToFirestoreMap(tpl: ProjectTemplate): Record<string, unknown> {
  return {
    id: tpl.id,
    title: tpl.title,
    notes: tpl.notes || '',
    defaultAreaId: tpl.defaultAreaId ?? null,
    tagIds: tpl.tagIds || [],
    blueprintJson: tpl.blueprintJson || '{}',
    sortOrder: finiteNumber(tpl.sortOrder),
    createdAt: Math.trunc(finiteNumber(tpl.createdAt)),
    updatedAt: Math.trunc(finiteNumber(tpl.updatedAt)),
    isDeleted: Boolean(tpl.isDeleted)
  };
}

function projectTemplateFromFirestoreMap(map: Record<string, unknown>): ProjectTemplate | null {
  const id = nullableString(map.id);
  if (!id) return null;
  return {
    id,
    title: typeof map.title === 'string' ? map.title : '',
    notes: typeof map.notes === 'string' ? map.notes : '',
    defaultAreaId: nullableString(map.defaultAreaId),
    tagIds: stringList(map.tagIds),
    blueprintJson: typeof map.blueprintJson === 'string' ? map.blueprintJson : '{}',
    sortOrder: finiteNumber(map.sortOrder),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
  };
}

// ==================== Cloud Sync Engine ====================

type SyncListener = (snapshot: CloudSyncSnapshot) => void;

class ThingsCloudSyncService {
  private listeners = new Set<SyncListener>();
  private firestoreUnsubs: FirestoreUnsubscribe[] = [];
  private liveEventSource: EventSource | null = null;
  private liveEventSourceReconnectTimer: number | null = null;
  private realtimeReconnectTimer: number | null = null;
  private authUnsub: AuthUnsubscribe | null = null;
  private boundAuthInstance: Auth | null = null;
  private listenersRegistered = false;
  private debounceTimer: number | null = null;
  private fastPushTimer: number | null = null;
  private fastPullTimer: number | null = null;
  private isPushingPromise: Promise<void> | null = null;
  private pendingFastPush = false;
  private isSyncingPromise: Promise<void> | null = null;
  private pendingResyncRequested = false;
  private pendingForceFullPull = false;
  private lastHandledPulseAt = 0;
  private authFlowInProgress = false;
  private state: CloudSyncSnapshot = {
    status: 'OFFLINE',
    userEmailOrId: null,
    userId: null,
    lastSyncedAt: 0,
    errorMessage: null
  };

  constructor() {
    localDb.setSyncTrigger((immediate) => {
      this.triggerFastPush(immediate ? 0 : FAST_SYNC_COALESCE_MS);
    });
  }

  subscribe(listener: SyncListener): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getState(): CloudSyncSnapshot {
    return this.state;
  }

  private emit(patch: Partial<CloudSyncSnapshot>) {
    this.state = { ...this.state, ...patch };
    for (const l of this.listeners) {
      l(this.state);
    }
  }

  private getOrCreateDeviceId(): string {
    const existing = this.loadPersistedMeta()?.deviceId;
    if (existing) return existing;
    const key = 'thingsbook_chromeos_device_id';
    let dev = localStorage.getItem(key);
    if (!dev) {
      dev = crypto.randomUUID();
      localStorage.setItem(key, dev);
    }
    return dev;
  }

  private loadPersistedMeta(): PersistedSyncMeta | null {
    try {
      const raw = localStorage.getItem(SYNC_META_STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as PersistedSyncMeta;
    } catch {
      return null;
    }
  }

  private savePersistedMeta(meta: PersistedSyncMeta | null) {
    if (!meta) {
      localStorage.removeItem(SYNC_META_STORAGE_KEY);
    } else {
      localStorage.setItem(SYNC_META_STORAGE_KEY, JSON.stringify(meta));
    }
  }

  private getLastSyncTs(userId: string): number {
    const raw = localStorage.getItem(`${LAST_SYNC_PREFIX}${userId}`);
    const parsed = raw ? Number(raw) : 0;
    if (!Number.isFinite(parsed) || parsed <= 0) return 0;
    return clampRemoteCursorTimestamp(parsed, Date.now());
  }

  private setLastSyncTs(userId: string, ts: number) {
    const clamped = ts > 0 ? clampRemoteCursorTimestamp(ts, Date.now()) : 0;
    localStorage.setItem(`${LAST_SYNC_PREFIX}${userId}`, String(Math.trunc(clamped)));
    this.emit({ lastSyncedAt: Math.trunc(clamped) });
  }

  private isLocalOnly(): boolean {
    return loadPreferences().localOnlyDbMode;
  }

  async initializeSession(): Promise<void> {
    const auth = getFirebaseAuthInstance();
    if (this.authUnsub && this.boundAuthInstance === auth) return;
    if (this.authUnsub) {
      try {
        this.authUnsub();
      } catch {
        // ignore
      }
      this.authUnsub = null;
    }
    this.boundAuthInstance = auth;

    this.authUnsub = onAuthStateChanged(auth, async (user) => {
      if (user && !user.email?.endsWith('@thingsbook.app')) {
        if (this.authFlowInProgress) {
          this.emit({
            userId: user.uid,
            userEmailOrId: user.email || user.uid,
            errorMessage: null
          });
          return;
        }
        const persisted = this.loadPersistedMeta();
        const lowerEmail = (user.email || '').trim().toLowerCase();
        const channelTopic =
          persisted?.userId === user.uid && persisted.channelTopic
            ? persisted.channelTopic
            : lowerEmail
              ? `tb_sync_${(await sha256Hex(`thingsbook-instant:${lowerEmail}`)).slice(0, 28)}`
              : `tb_sync_${(await sha256Hex(user.uid)).slice(0, 28)}`;

        this.savePersistedMeta({
          userId: user.uid,
          email: lowerEmail || user.uid,
          channelTopic,
          deviceId: this.getOrCreateDeviceId()
        });

        this.emit({
          userId: user.uid,
          userEmailOrId: user.email || user.uid,
          lastSyncedAt: this.getLastSyncTs(user.uid),
          errorMessage: null
        });

        if (!this.isLocalOnly()) {
          this.attachRealtimeListeners(user.uid);
          await this.syncBidirectionalDeltas();
        } else {
          this.emit({ status: 'OFFLINE' });
        }
      } else {
        if (this.authFlowInProgress) return;
        this.detachRealtimeListeners();
        this.emit({
          status: 'OFFLINE',
          userId: null,
          userEmailOrId: null,
          errorMessage: null
        });
      }
    });

    if (!this.listenersRegistered && typeof window !== 'undefined') {
      this.listenersRegistered = true;
      window.addEventListener('online', () => {
        if (!this.isLocalOnly() && this.state.userId) {
          this.attachRealtimeListeners(this.state.userId);
          this.triggerFastPush(0);
          this.triggerBackgroundSync(40);
        }
      });
      window.addEventListener('offline', () => {
        this.emit({ status: 'OFFLINE' });
      });
      window.addEventListener('focus', () => {
        if (!this.isLocalOnly() && this.state.userId && navigator.onLine) {
          this.triggerIncrementalPull(0);
        }
      });
      if (typeof document !== 'undefined') {
        document.addEventListener('visibilitychange', () => {
          if (
            document.visibilityState === 'visible' &&
            !this.isLocalOnly() &&
            this.state.userId &&
            navigator.onLine
          ) {
            this.triggerFastPush(0);
            this.triggerIncrementalPull(0);
          }
        });
      }
    }
  }

  onDatabaseModeChanged(localOnly: boolean) {
    if (localOnly) {
      this.detachRealtimeListeners();
      this.emit({ status: 'OFFLINE' });
    } else if (this.state.userId) {
      const forceFull = this.getLastSyncTs(this.state.userId) === 0;
      this.attachRealtimeListeners(this.state.userId);
      void this.syncBidirectionalDeltas(forceFull);
    }
  }

  /**
   * Sub-100ms outbound Firestore push path decoupled from full 8-collection pulls and ntfy polling,
   * ensuring local edits propagate to peers immediately without 3-4s queueing delays.
   */
  triggerFastPush(delayMs = FAST_SYNC_COALESCE_MS) {
    if (this.isLocalOnly() || !this.state.userId) return;
    if (this.fastPushTimer != null) {
      window.clearTimeout(this.fastPushTimer);
      this.fastPushTimer = null;
    }
    if (delayMs <= 0) {
      void this.runFastPushLoop();
      return;
    }
    this.fastPushTimer = window.setTimeout(() => {
      this.fastPushTimer = null;
      void this.runFastPushLoop();
    }, delayMs);
  }

  triggerIncrementalPull(delayMs = 20) {
    if (this.isLocalOnly() || !this.state.userId) return;
    if (this.fastPullTimer != null) {
      window.clearTimeout(this.fastPullTimer);
      this.fastPullTimer = null;
    }
    if (delayMs <= 0) {
      void this.syncBidirectionalDeltas(false);
      return;
    }
    this.fastPullTimer = window.setTimeout(() => {
      this.fastPullTimer = null;
      void this.syncBidirectionalDeltas(false);
    }, delayMs);
  }

  triggerBackgroundSync(delayMs = FAST_SYNC_COALESCE_MS) {
    if (this.isLocalOnly() || !this.state.userId) return;
    if (this.debounceTimer != null) {
      window.clearTimeout(this.debounceTimer);
    }
    this.debounceTimer = window.setTimeout(() => {
      this.debounceTimer = null;
      void this.syncBidirectionalDeltas();
    }, delayMs);
  }

  // ==================== Authentication ====================

  private async prepareLocalDataBeforeLogin(mergeLocalTasks: boolean) {
    if (mergeLocalTasks) {
      await localDb.purgeSampleDataLocally(true);
    } else {
      await localDb.purgeAllLocalDataBeforeAccountPull();
    }
  }

  private async finalizeAuthenticatedSession(
    uid: string,
    emailOrId: string,
    channelTopic: string,
    mergeLocalTasks: boolean
  ): Promise<void> {
    await this.prepareLocalDataBeforeLogin(mergeLocalTasks);
    if (this.isLocalOnly()) {
      savePreferences({ ...loadPreferences(), localOnlyDbMode: false });
    }
    this.savePersistedMeta({
      userId: uid,
      email: emailOrId,
      channelTopic,
      deviceId: this.getOrCreateDeviceId()
    });
    this.emit({
      userId: uid,
      userEmailOrId: emailOrId,
      errorMessage: null
    });
    this.setLastSyncTs(uid, 0);
    await this.syncBidirectionalDeltas(true);
    this.attachRealtimeListeners(uid);
  }

  private async authenticateGoogleAndLinkPassword(
    auth: Auth,
    expectedEmail: string,
    password: string
  ): Promise<string> {
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({
      login_hint: expectedEmail,
      prompt: 'select_account'
    });
    const googleCred = await signInWithPopup(auth, provider);
    const signedInEmail = (googleCred.user.email || '').trim().toLowerCase();
    if (signedInEmail && signedInEmail !== expectedEmail) {
      await signOut(auth).catch(() => {});
      throw new Error(
        `Please select ${expectedEmail} in the Google sign-in window (signed in as ${signedInEmail}).`
      );
    }

    const hasPasswordProvider = googleCred.user.providerData.some(
      (p) => p.providerId === 'password'
    );
    if (!hasPasswordProvider) {
      try {
        await linkWithCredential(
          googleCred.user,
          EmailAuthProvider.credential(expectedEmail, password)
        );
      } catch (linkErr: unknown) {
        const linkCode = extractAuthErrorCode(linkErr);
        if (linkCode === 'auth/provider-already-linked') {
          await updatePassword(googleCred.user, password);
        } else {
          throw linkErr;
        }
      }
    } else {
      await updatePassword(googleCred.user, password);
    }

    return googleCred.user.uid;
  }

  async registerEmailAccount(
    rawEmail: string,
    password: string,
    mergeLocalTasks = true
  ): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || password.length < 6) {
      throw new Error('Please enter a valid email address and a password with at least 6 characters.');
    }

    const auth = getFirebaseAuthInstance();
    this.authFlowInProgress = true;
    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      let uid: string;
      try {
        const cred = await createUserWithEmailAndPassword(auth, email, password);
        uid = cred.user.uid;
      } catch (err: unknown) {
        const code = extractAuthErrorCode(err);
        const msg = err instanceof Error ? err.message : String(err);
        if (code === 'auth/email-already-in-use' || msg.includes('email-already-in-use')) {
          this.authFlowInProgress = false;
          return await this.signInEmailAccount(email, password, mergeLocalTasks);
        }
        throw err;
      }

      const pwVerifier = await sha256Hex(`thingsbook-pw-v1:${email}:${password}`);
      const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-chan-v1:${email}:${pwVerifier}`)).slice(0, 28)}`;
      await this.finalizeAuthenticatedSession(uid, email, channelTopic, mergeLocalTasks);
      return email;
    } catch (err: unknown) {
      const formatted = formatAuthErrorMessage(err);
      this.emit({ status: 'OFFLINE', errorMessage: formatted });
      throw err;
    } finally {
      this.authFlowInProgress = false;
    }
  }

  async signInEmailAccount(
    rawEmail: string,
    password: string,
    mergeLocalTasks = true
  ): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || password.length < 6) {
      throw new Error('Please enter your email address and password (min. 6 characters).');
    }

    const auth = getFirebaseAuthInstance();
    this.authFlowInProgress = true;
    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      let uid = '';
      try {
        const cred = await signInWithEmailAndPassword(auth, email, password);
        uid = cred.user.uid;
      } catch (signInErr: unknown) {
        if (!isInvalidPasswordOrUnlinkedError(signInErr)) {
          throw signInErr;
        }

        // Recovery Layer 1: Silent zero-popup re-link if this device holds a Google session
        // (e.g. from Google Calendar OAuth) whose password provider was unlinked by Firebase Server.
        const gcalAuth = getGoogleCalendarAuthInstance();
        await gcalAuth.authStateReady();
        const existingGoogleUser = [gcalAuth.currentUser, auth.currentUser].find(
          (u) => u && (u.email || '').trim().toLowerCase() === email
        );

        let googleUnlinkedDetected = hasStoredGoogleCalendarAccountForEmail(email);

        if (existingGoogleUser) {
          await existingGoogleUser.reload().catch(() => {});
          const hasPassword = existingGoogleUser.providerData.some(
            (p) => p.providerId === 'password'
          );
          if (!hasPassword) {
            googleUnlinkedDetected = true;
            try {
              await linkWithCredential(
                existingGoogleUser,
                EmailAuthProvider.credential(email, password)
              );
              const retryCred = await signInWithEmailAndPassword(auth, email, password);
              uid = retryCred.user.uid;
            } catch {
              // Fall through to Layer 2 (Google popup verification) if recent login is required
            }
          }
        }

        // Recovery Layer 2: Automatic Google verification popup + password re-link
        if (!uid && googleUnlinkedDetected) {
          uid = await this.authenticateGoogleAndLinkPassword(auth, email, password);
        }

        if (!uid) {
          throw signInErr;
        }
      }

      const pwVerifier = await sha256Hex(`thingsbook-pw-v1:${email}:${password}`);
      const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-chan-v1:${email}:${pwVerifier}`)).slice(0, 28)}`;
      await this.finalizeAuthenticatedSession(uid, email, channelTopic, mergeLocalTasks);
      return email;
    } catch (err: unknown) {
      const formatted = formatAuthErrorMessage(err);
      this.emit({ status: 'OFFLINE', errorMessage: formatted });
      throw err;
    } finally {
      this.authFlowInProgress = false;
    }
  }

  /**
   * Direct 1-click user-activated Google verification + password re-linking.
   * Opens the Google popup synchronously on the click gesture so browser popup blockers never fire,
   * signs in as `rawEmail`, and permanently attaches `password` alongside `google.com`.
   */
  async verifyGoogleAndLinkPassword(
    rawEmail: string,
    password: string,
    mergeLocalTasks = true
  ): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || password.length < 6) {
      throw new Error('Please enter your email address and password (min. 6 characters).');
    }

    const auth = getFirebaseAuthInstance();
    this.authFlowInProgress = true;
    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      const uid = await this.authenticateGoogleAndLinkPassword(auth, email, password);
      const pwVerifier = await sha256Hex(`thingsbook-pw-v1:${email}:${password}`);
      const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-chan-v1:${email}:${pwVerifier}`)).slice(0, 28)}`;
      await this.finalizeAuthenticatedSession(uid, email, channelTopic, mergeLocalTasks);
      return email;
    } catch (err: unknown) {
      const formatted = formatAuthErrorMessage(err);
      this.emit({ status: 'OFFLINE', errorMessage: formatted });
      throw err;
    } finally {
      this.authFlowInProgress = false;
    }
  }

  async signInWithGooglePopup(mergeLocalTasks = true): Promise<string> {
    const auth = getFirebaseAuthInstance();
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });

    this.authFlowInProgress = true;
    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      const cred = await signInWithPopup(auth, provider);
      const user = cred.user;
      const email = (user.email || '').trim().toLowerCase();
      const uid = user.uid;

      const channelTopic = email
        ? `tb_sync_${(await sha256Hex(`thingsbook-instant:${email}`)).slice(0, 28)}`
        : `tb_sync_${(await sha256Hex(uid)).slice(0, 28)}`;

      await this.finalizeAuthenticatedSession(uid, email || uid, channelTopic, mergeLocalTasks);
      return email || uid;
    } catch (err: unknown) {
      const formatted = formatAuthErrorMessage(err);
      this.emit({ status: 'OFFLINE', errorMessage: formatted });
      throw err;
    } finally {
      this.authFlowInProgress = false;
    }
  }

  async signInInstantEmail(rawEmail: string, mergeLocalTasks = true): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || !email.includes('.') || email.endsWith('@thingsbook.app')) {
      throw new Error('Please enter a valid email address for Instant Cloud Sync.');
    }

    const instantHash = await sha256Hex(`tb-instant-v2:${email}`);
    const derivedPassword = `TbCloud!${instantHash.slice(0, 14)}`;
    const auth = getFirebaseAuthInstance();

    this.authFlowInProgress = true;
    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      let uid: string;
      try {
        const cred = await signInWithEmailAndPassword(auth, email, derivedPassword);
        uid = cred.user.uid;
      } catch {
        try {
          const created = await createUserWithEmailAndPassword(auth, email, derivedPassword);
          uid = created.user.uid;
        } catch {
          throw new Error(
            `A custom password is already set for ${email} (or linked with Google). Enter your password above and click "Sign In with Email", or use "Continue with Google".`
          );
        }
      }

      const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-instant:${email}`)).slice(0, 28)}`;
      await this.finalizeAuthenticatedSession(uid, email, channelTopic, mergeLocalTasks);
      return email;
    } catch (err: unknown) {
      const formatted = formatAuthErrorMessage(err);
      this.emit({ status: 'OFFLINE', errorMessage: formatted });
      throw err;
    } finally {
      this.authFlowInProgress = false;
    }
  }

  async sendResetEmail(rawEmail: string): Promise<void> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@')) {
      throw new Error('Please enter your email address first.');
    }
    await sendPasswordResetEmail(getFirebaseAuthInstance(), email);
  }

  async signOutCloud(): Promise<void> {
    this.detachRealtimeListeners();
    this.savePersistedMeta(null);
    try {
      await signOut(getFirebaseAuthInstance());
    } catch {
      // ignore
    }
    this.emit({
      status: 'OFFLINE',
      userId: null,
      userEmailOrId: null,
      errorMessage: null
    });
  }

  // ==================== Fast Outbound Push & Bidirectional Delta Sync ====================

  private async deriveUidWakeTopic(userId: string): Promise<string> {
    const hex = await sha256Hex(formatUidWakeSeed(userId));
    return formatUidWakeChannelFromHex(hex);
  }

  private async runFastPushLoop(): Promise<void> {
    if (this.isLocalOnly() || !navigator.onLine) return;
    if (this.isPushingPromise) {
      this.pendingFastPush = true;
      return this.isPushingPromise;
    }

    this.isPushingPromise = (async () => {
      try {
        while (true) {
          this.pendingFastPush = false;
          const auth = getFirebaseAuthInstance();
          const userId = auth.currentUser?.uid || this.state.userId;
          if (!userId || this.isLocalOnly() || !navigator.onLine) break;

          const relayPushMaps = await this.pushDirtyRecordsToFirestore(userId);
          if (relayPushMaps) {
            this.emit({ status: 'SYNCED', errorMessage: null });
            const meta = this.loadPersistedMeta();
            const localDeviceId = meta?.deviceId || this.getOrCreateDeviceId();
            void this.broadcastNtfyWakeAndDeltas(
              userId,
              meta?.channelTopic ?? null,
              localDeviceId,
              relayPushMaps
            );
          }

          if (!this.pendingFastPush || this.isLocalOnly() || !navigator.onLine) {
            this.pendingFastPush = false;
            break;
          }
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        this.emit({ status: 'OFFLINE', errorMessage: msg });
      } finally {
        this.isPushingPromise = null;
      }
    })();

    return this.isPushingPromise;
  }

  async syncBidirectionalDeltas(forceFullPull = false): Promise<void> {
    void this.initializeSession();
    if (this.isLocalOnly()) {
      this.emit({ status: 'OFFLINE' });
      return;
    }
    if (!navigator.onLine) {
      this.emit({ status: 'OFFLINE' });
      return;
    }
    if (this.isSyncingPromise) {
      this.pendingResyncRequested = true;
      if (forceFullPull) {
        this.pendingForceFullPull = true;
      }
      return this.isSyncingPromise;
    }

    this.isSyncingPromise = (async () => {
      let nextForceFull = forceFullPull;
      try {
        while (true) {
          this.pendingResyncRequested = false;
          this.pendingForceFullPull = false;
          await this.runSyncInternal(nextForceFull);
          if (!this.pendingResyncRequested || this.isLocalOnly() || !navigator.onLine) {
            this.pendingResyncRequested = false;
            this.pendingForceFullPull = false;
            break;
          }
          nextForceFull = this.pendingForceFullPull;
        }
      } finally {
        this.isSyncingPromise = null;
      }
    })();

    return this.isSyncingPromise;
  }

  private async runSyncInternal(forceFullPull: boolean): Promise<void> {
    const auth = getFirebaseAuthInstance();
    const userId = auth.currentUser?.uid || this.state.userId;
    if (!userId) {
      this.emit({ status: 'OFFLINE' });
      return;
    }

    if (this.state.status !== 'SYNCED') {
      this.emit({ status: 'SYNCING', errorMessage: null });
    }

    try {
      const lastSync = forceFullPull ? 0 : this.getLastSyncTs(userId);

      const relayPushMaps = await this.pushDirtyRecordsToFirestore(userId);
      const rawPulledMax = await this.pullDeltaRecordsFromFirestore(userId, lastSync);
      const pulledMax = clampRemoteCursorTimestamp(rawPulledMax, Date.now());

      const meta = this.loadPersistedMeta();
      const localDeviceId = meta?.deviceId || this.getOrCreateDeviceId();
      if (relayPushMaps) {
        void this.broadcastNtfyWakeAndDeltas(
          userId,
          meta?.channelTopic ?? null,
          localDeviceId,
          relayPushMaps
        );
      }
      if (forceFullPull && meta?.channelTopic) {
        void this.pollNtfyHistoryInBackground(
          userId,
          meta.channelTopic,
          localDeviceId,
          lastSync
        );
      }

      const nextSyncTs =
        pulledMax > lastSync ? pulledMax : lastSync === 0 ? Date.now() : lastSync;
      this.setLastSyncTs(userId, nextSyncTs);
      this.emit({ status: 'SYNCED', errorMessage: null });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      this.emit({ status: 'OFFLINE', errorMessage: msg });
    }
  }

  private async pushDirtyRecordsToFirestore(userId: string): Promise<{
    tags: Record<string, unknown>[];
    areas: Record<string, unknown>[];
    projects: Record<string, unknown>[];
    headings: Record<string, unknown>[];
    tasks: Record<string, unknown>[];
    checklistItems: Record<string, unknown>[];
    attachments: Record<string, unknown>[];
    projectTemplates: Record<string, unknown>[];
  } | null> {
    const dirty = await localDb.getDirtyEntities();
    const metadataOps: Array<{ col: string; id: string; data: Record<string, unknown> }> = [];

    const tagMaps = dirty.tags.map(tagToFirestoreMap);
    const areaMaps = dirty.areas.map(areaToFirestoreMap);
    const projectMaps = dirty.projects.map(projectToFirestoreMap);
    const headingMaps = dirty.headings.map(headingToFirestoreMap);
    const taskMaps = dirty.tasks.map(taskToFirestoreMap);
    const checklistMaps = dirty.checklistItems.map(checklistToFirestoreMap);
    const attachmentMaps = dirty.attachments.map(attachmentToFirestoreMap);
    const projectTemplateMaps = dirty.projectTemplates.map(projectTemplateToFirestoreMap);

    for (let i = 0; i < dirty.tags.length; i++) {
      metadataOps.push({ col: 'tags', id: dirty.tags[i].id, data: tagMaps[i] });
    }
    for (let i = 0; i < dirty.areas.length; i++) {
      metadataOps.push({ col: 'areas', id: dirty.areas[i].id, data: areaMaps[i] });
    }
    for (let i = 0; i < dirty.projects.length; i++) {
      metadataOps.push({ col: 'projects', id: dirty.projects[i].id, data: projectMaps[i] });
    }
    for (let i = 0; i < dirty.headings.length; i++) {
      metadataOps.push({ col: 'headings', id: dirty.headings[i].id, data: headingMaps[i] });
    }
    for (let i = 0; i < dirty.tasks.length; i++) {
      metadataOps.push({ col: 'tasks', id: dirty.tasks[i].id, data: taskMaps[i] });
    }
    for (let i = 0; i < dirty.checklistItems.length; i++) {
      metadataOps.push({
        col: 'checklist_items',
        id: dirty.checklistItems[i].id,
        data: checklistMaps[i]
      });
    }
    for (let i = 0; i < dirty.projectTemplates.length; i++) {
      metadataOps.push({
        col: 'project_templates',
        id: dirty.projectTemplates[i].id,
        data: projectTemplateMaps[i]
      });
    }

    if (metadataOps.length === 0 && dirty.attachments.length === 0) return null;

    const db = getFirestoreInstance();
    const localDeviceId = this.getOrCreateDeviceId();
    const pulseAt = Date.now();
    const pulseRef = doc(db, 'users', userId, 'sync_meta', 'pulse');
    const pulseDoc = {
      pulseAt,
      senderDeviceId: localDeviceId
    };

    if (metadataOps.length > 0) {
      const slices = chunkItems(metadataOps, METADATA_BATCH_CHUNK_SIZE);
      for (let sIdx = 0; sIdx < slices.length; sIdx++) {
        const slice = slices[sIdx];
        const batch = writeBatch(db);
        for (const op of slice) {
          const ref = doc(db, 'users', userId, op.col, op.id);
          batch.set(ref, op.data, { merge: true });
        }
        if (sIdx === slices.length - 1 && dirty.attachments.length === 0) {
          batch.set(pulseRef, pulseDoc, { merge: true });
        }
        await batch.commit();
      }
    }

    if (dirty.attachments.length > 0) {
      const attachmentPairs = dirty.attachments.map((att, idx) => ({
        id: att.id,
        data: attachmentMaps[idx]
      }));
      const attChunks = chunkItems(attachmentPairs, ATTACHMENT_BATCH_CHUNK_SIZE);
      for (let aIdx = 0; aIdx < attChunks.length; aIdx++) {
        const attChunk = attChunks[aIdx];
        const batch = writeBatch(db);
        for (const item of attChunk) {
          const ref = doc(db, 'users', userId, 'attachments', item.id);
          batch.set(ref, item.data, { merge: true });
        }
        if (aIdx === attChunks.length - 1) {
          batch.set(pulseRef, pulseDoc, { merge: true });
        }
        await batch.commit();
      }
    }

    const maxOf = <T extends { updatedAt: number }>(arr: T[]) =>
      arr.reduce((m, x) => Math.max(m, x.updatedAt), 0);

    await localDb.markSynced({
      tags: { ids: dirty.tags.map((x) => x.id), maxUpdatedAt: maxOf(dirty.tags) },
      areas: { ids: dirty.areas.map((x) => x.id), maxUpdatedAt: maxOf(dirty.areas) },
      projects: { ids: dirty.projects.map((x) => x.id), maxUpdatedAt: maxOf(dirty.projects) },
      headings: { ids: dirty.headings.map((x) => x.id), maxUpdatedAt: maxOf(dirty.headings) },
      tasks: { ids: dirty.tasks.map((x) => x.id), maxUpdatedAt: maxOf(dirty.tasks) },
      checklistItems: {
        ids: dirty.checklistItems.map((x) => x.id),
        maxUpdatedAt: maxOf(dirty.checklistItems)
      },
      attachments: {
        ids: dirty.attachments.map((x) => x.id),
        maxUpdatedAt: maxOf(dirty.attachments)
      },
      projectTemplates: {
        ids: dirty.projectTemplates.map((x) => x.id),
        maxUpdatedAt: maxOf(dirty.projectTemplates)
      }
    });

    return {
      tags: tagMaps,
      areas: areaMaps,
      projects: projectMaps,
      headings: headingMaps,
      tasks: taskMaps,
      checklistItems: checklistMaps,
      attachments: attachmentMaps,
      projectTemplates: projectTemplateMaps
    };
  }

  private async pullDeltaRecordsFromFirestore(
    userId: string,
    sinceTimestamp: number
  ): Promise<number> {
    const db = getFirestoreInstance();
    const effectiveSince = computeEffectivePullCursor(sinceTimestamp);
    const backfillKey = `${ATTACHMENT_BACKFILL_STORAGE_PREFIX}${userId}`;
    const needsAttachmentBackfill = localStorage.getItem(backfillKey) !== '1';
    const projReviewBackfillKey = `${PROJECTS_REVIEW_BACKFILL_STORAGE_PREFIX}${userId}`;
    const needsProjectsReviewBackfill = localStorage.getItem(projReviewBackfillKey) !== '1';

    const fetchCollection = async (
      colName: string,
      colSince: number
    ): Promise<Record<string, unknown>[]> => {
      const colRef = collection(db, 'users', userId, colName);
      const q = colSince > 0 ? query(colRef, where('updatedAt', '>', colSince)) : colRef;
      const snap = await getDocs(q);
      return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    };

    const [
      rawTags,
      rawAreas,
      rawProjects,
      rawHeadings,
      rawTasks,
      rawChecklists,
      rawAttachments,
      rawTemplates
    ] = await Promise.all([
      fetchCollection('tags', effectiveSince),
      fetchCollection('areas', effectiveSince),
      fetchCollection('projects', needsProjectsReviewBackfill ? 0 : effectiveSince),
      fetchCollection('headings', effectiveSince),
      fetchCollection('tasks', effectiveSince),
      fetchCollection('checklist_items', effectiveSince),
      fetchCollection('attachments', needsAttachmentBackfill ? 0 : effectiveSince),
      fetchCollection('project_templates', effectiveSince)
    ]);

    const tags = rawTags.map(tagFromFirestoreMap).filter((x): x is TagRecord => x !== null);
    const areas = rawAreas.map(areaFromFirestoreMap).filter((x): x is AreaRecord => x !== null);
    const projects = rawProjects
      .map(projectFromFirestoreMap)
      .filter((x): x is ProjectRecord => x !== null);
    const headings = rawHeadings
      .map(headingFromFirestoreMap)
      .filter((x): x is HeadingRecord => x !== null);
    const tasks = rawTasks.map(taskFromFirestoreMap).filter((x): x is TaskRecord => x !== null);
    const checklistItems = rawChecklists
      .map(checklistFromFirestoreMap)
      .filter((x): x is ChecklistItemRecord => x !== null);
    const attachments = rawAttachments
      .map(attachmentFromFirestoreMap)
      .filter((x): x is AttachmentRecord => x !== null);
    const projectTemplates = rawTemplates
      .map(projectTemplateFromFirestoreMap)
      .filter((x): x is ProjectTemplate => x !== null);

    if (
      tags.length === 0 &&
      areas.length === 0 &&
      projects.length === 0 &&
      headings.length === 0 &&
      tasks.length === 0 &&
      checklistItems.length === 0 &&
      attachments.length === 0 &&
      projectTemplates.length === 0
    ) {
      if (needsAttachmentBackfill) {
        localStorage.setItem(backfillKey, '1');
      }
      if (needsProjectsReviewBackfill) {
        localStorage.setItem(projReviewBackfillKey, '1');
      }
      return sinceTimestamp;
    }

    const appliedMax = await localDb.applyRemoteDeltas({
      tags,
      areas,
      projects,
      headings,
      tasks,
      checklistItems,
      attachments,
      projectTemplates
    });

    if (needsAttachmentBackfill) {
      localStorage.setItem(backfillKey, '1');
    }
    if (needsProjectsReviewBackfill) {
      localStorage.setItem(projReviewBackfillKey, '1');
    }

    return clampRemoteCursorTimestamp(appliedMax, Date.now());
  }

  private scheduleRealtimeReconnect(userId: string) {
    if (typeof window === 'undefined' || this.isLocalOnly()) return;
    if (this.realtimeReconnectTimer != null) return;
    this.realtimeReconnectTimer = window.setTimeout(() => {
      this.realtimeReconnectTimer = null;
      if (!this.isLocalOnly() && this.state.userId === userId && navigator.onLine) {
        this.attachRealtimeListeners(userId);
        this.triggerIncrementalPull(0);
      }
    }, 1200);
  }

  private attachRealtimeListeners(userId: string) {
    this.detachRealtimeListeners();
    if (this.isLocalOnly()) return;

    const db = getFirestoreInstance();
    const localDeviceId = this.getOrCreateDeviceId();
    const collections = [
      'tags',
      'areas',
      'projects',
      'headings',
      'tasks',
      'checklist_items',
      'attachments',
      'project_templates'
    ] as const;

    for (const colName of collections) {
      const since = computeEffectivePullCursor(this.getLastSyncTs(userId));
      const colRef = collection(db, 'users', userId, colName);
      const q = since > 0 ? query(colRef, where('updatedAt', '>', since)) : colRef;

      const unsub = onSnapshot(
        q,
        async (snapshot) => {
          const changed = snapshot
            .docChanges()
            .filter(
              (c) =>
                !c.doc.metadata.hasPendingWrites &&
                (c.type === 'added' || c.type === 'modified')
            );
          if (changed.length === 0) return;

          const docs = changed.map((c) => ({ id: c.doc.id, ...c.doc.data() }));
          let maxTs = 0;

          if (colName === 'tags') {
            maxTs = await localDb.applyRemoteDeltas({
              tags: docs.map(tagFromFirestoreMap).filter((x): x is TagRecord => x !== null)
            });
          } else if (colName === 'areas') {
            maxTs = await localDb.applyRemoteDeltas({
              areas: docs.map(areaFromFirestoreMap).filter((x): x is AreaRecord => x !== null)
            });
          } else if (colName === 'projects') {
            maxTs = await localDb.applyRemoteDeltas({
              projects: docs.map(projectFromFirestoreMap).filter((x): x is ProjectRecord => x !== null)
            });
          } else if (colName === 'headings') {
            maxTs = await localDb.applyRemoteDeltas({
              headings: docs.map(headingFromFirestoreMap).filter((x): x is HeadingRecord => x !== null)
            });
          } else if (colName === 'tasks') {
            maxTs = await localDb.applyRemoteDeltas({
              tasks: docs.map(taskFromFirestoreMap).filter((x): x is TaskRecord => x !== null)
            });
          } else if (colName === 'checklist_items') {
            maxTs = await localDb.applyRemoteDeltas({
              checklistItems: docs
                .map(checklistFromFirestoreMap)
                .filter((x): x is ChecklistItemRecord => x !== null)
            });
          } else if (colName === 'attachments') {
            maxTs = await localDb.applyRemoteDeltas({
              attachments: docs
                .map(attachmentFromFirestoreMap)
                .filter((x): x is AttachmentRecord => x !== null)
            });
          } else if (colName === 'project_templates') {
            maxTs = await localDb.applyRemoteDeltas({
              projectTemplates: docs
                .map(projectTemplateFromFirestoreMap)
                .filter((x): x is ProjectTemplate => x !== null)
            });
          }

          const clampedMax = clampRemoteCursorTimestamp(maxTs, Date.now());
          if (clampedMax > this.getLastSyncTs(userId)) {
            this.setLastSyncTs(userId, clampedMax);
          }
          this.emit({ status: 'SYNCED', errorMessage: null });
        },
        () => {
          this.scheduleRealtimeReconnect(userId);
        }
      );
      this.firestoreUnsubs.push(unsub);
    }

    // Ultra-low-latency single-document pulse listener: fires in <80ms across all peer platforms
    // without query index or clock-skew filtering.
    const pulseRef = doc(db, 'users', userId, 'sync_meta', 'pulse');
    const pulseUnsub = onSnapshot(
      pulseRef,
      (snap) => {
        if (!snap.exists() || snap.metadata.hasPendingWrites) return;
        const data = snap.data();
        const sender = typeof data?.senderDeviceId === 'string' ? data.senderDeviceId : '';
        const pulseAt = finiteNumber(data?.pulseAt, 0);
        if (sender && sender !== localDeviceId && pulseAt > this.lastHandledPulseAt) {
          this.lastHandledPulseAt = pulseAt;
          this.triggerIncrementalPull(15);
        }
      },
      () => {
        this.scheduleRealtimeReconnect(userId);
      }
    );
    this.firestoreUnsubs.push(pulseUnsub);

    void this.attachLiveWakeEventSource(userId, localDeviceId);
  }

  private async attachLiveWakeEventSource(userId: string, localDeviceId: string) {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') return;
    if (this.isLocalOnly() || this.state.userId !== userId) return;

    const uidWakeTopic = await this.deriveUidWakeTopic(userId);
    if (this.isLocalOnly() || this.state.userId !== userId) return;

    if (this.liveEventSource) {
      try {
        this.liveEventSource.close();
      } catch {
        // ignore
      }
      this.liveEventSource = null;
    }

    try {
      const es = new EventSource(`https://ntfy.sh/${encodeURIComponent(uidWakeTopic)}/sse`);
      this.liveEventSource = es;
      es.onmessage = (ev) => {
        if (this.isLocalOnly() || this.state.userId !== userId) return;
        void this.handleIncomingNtfyEnvelope(ev.data, userId, localDeviceId);
      };
      es.onerror = () => {
        try {
          es.close();
        } catch {
          // ignore
        }
        if (this.liveEventSource === es) {
          this.liveEventSource = null;
        }
        if (
          this.liveEventSourceReconnectTimer == null &&
          !this.isLocalOnly() &&
          this.state.userId === userId
        ) {
          this.liveEventSourceReconnectTimer = window.setTimeout(() => {
            this.liveEventSourceReconnectTimer = null;
            void this.attachLiveWakeEventSource(userId, localDeviceId);
          }, 4000);
        }
      };
    } catch {
      // Ignore EventSource errors in restricted/offline environments
    }
  }

  private async handleIncomingNtfyEnvelope(
    rawEnvelopeOrBatch: string,
    userId: string,
    localDeviceId: string
  ): Promise<void> {
    try {
      const parsed = JSON.parse(rawEnvelopeOrBatch);
      const batch =
        typeof parsed?.message === 'string' ? JSON.parse(parsed.message) : parsed;
      if (!batch || batch.userId !== userId) return;
      if (batch.deviceId === localDeviceId) return;

      if (batch.wakeOnly === true) {
        this.triggerIncrementalPull(0);
        return;
      }

      const maxApplied = await this.applyParsedNtfyBatch(batch);
      if (maxApplied > 0) {
        const clamped = clampRemoteCursorTimestamp(maxApplied, Date.now());
        if (clamped > this.getLastSyncTs(userId)) {
          this.setLastSyncTs(userId, clamped);
        }
        this.emit({ status: 'SYNCED', errorMessage: null });
      }
    } catch {
      // Ignore malformed relay message
    }
  }

  private async applyParsedNtfyBatch(batch: Record<string, unknown>): Promise<number> {
    return await localDb.applyRemoteDeltas({
      tags: Array.isArray(batch.tags)
        ? batch.tags
            .map(tagFromFirestoreMap)
            .filter((x: TagRecord | null): x is TagRecord => x !== null)
        : undefined,
      areas: Array.isArray(batch.areas)
        ? batch.areas
            .map(areaFromFirestoreMap)
            .filter((x: AreaRecord | null): x is AreaRecord => x !== null)
        : undefined,
      projects: Array.isArray(batch.projects)
        ? batch.projects
            .map(projectFromFirestoreMap)
            .filter((x: ProjectRecord | null): x is ProjectRecord => x !== null)
        : undefined,
      headings: Array.isArray(batch.headings)
        ? batch.headings
            .map(headingFromFirestoreMap)
            .filter((x: HeadingRecord | null): x is HeadingRecord => x !== null)
        : undefined,
      tasks: Array.isArray(batch.tasks)
        ? batch.tasks
            .map(taskFromFirestoreMap)
            .filter((x: TaskRecord | null): x is TaskRecord => x !== null)
        : undefined,
      checklistItems: Array.isArray(batch.checklistItems)
        ? batch.checklistItems
            .map(checklistFromFirestoreMap)
            .filter((x: ChecklistItemRecord | null): x is ChecklistItemRecord => x !== null)
        : undefined,
      attachments: Array.isArray(batch.attachments)
        ? batch.attachments
            .map(attachmentFromFirestoreMap)
            .filter((x: AttachmentRecord | null): x is AttachmentRecord => x !== null)
        : undefined,
      projectTemplates: Array.isArray(batch.projectTemplates)
        ? batch.projectTemplates
            .map(projectTemplateFromFirestoreMap)
            .filter((x: ProjectTemplate | null): x is ProjectTemplate => x !== null)
        : undefined
    });
  }

  private detachRealtimeListeners() {
    if (this.realtimeReconnectTimer != null && typeof window !== 'undefined') {
      window.clearTimeout(this.realtimeReconnectTimer);
      this.realtimeReconnectTimer = null;
    }
    if (this.liveEventSourceReconnectTimer != null && typeof window !== 'undefined') {
      window.clearTimeout(this.liveEventSourceReconnectTimer);
      this.liveEventSourceReconnectTimer = null;
    }
    if (this.liveEventSource) {
      try {
        this.liveEventSource.close();
      } catch {
        // ignore
      }
      this.liveEventSource = null;
    }
    for (const u of this.firestoreUnsubs) {
      try {
        u();
      } catch {
        // ignore
      }
    }
    this.firestoreUnsubs = [];
  }

  private async broadcastNtfyWakeAndDeltas(
    userId: string,
    legacyChannelTopic: string | null,
    localDeviceId: string,
    pushedDirtyMaps: {
      tags: Record<string, unknown>[];
      areas: Record<string, unknown>[];
      projects: Record<string, unknown>[];
      headings: Record<string, unknown>[];
      tasks: Record<string, unknown>[];
      checklistItems: Record<string, unknown>[];
      attachments: Record<string, unknown>[];
      projectTemplates: Record<string, unknown>[];
    }
  ): Promise<void> {
    const payload = buildNtfyRelayOrWakePayload({
      userId,
      deviceId: localDeviceId,
      batchTimestamp: Date.now(),
      tags: pushedDirtyMaps.tags,
      areas: pushedDirtyMaps.areas,
      projects: pushedDirtyMaps.projects,
      headings: pushedDirtyMaps.headings,
      tasks: pushedDirtyMaps.tasks,
      checklistItems: pushedDirtyMaps.checklistItems,
      attachments: pushedDirtyMaps.attachments,
      projectTemplates: pushedDirtyMaps.projectTemplates
    });
    if (payload === null) return;

    const uidWakeTopic = await this.deriveUidWakeTopic(userId);
    const topics = new Set<string>([uidWakeTopic]);
    if (legacyChannelTopic && legacyChannelTopic.trim().length > 0) {
      topics.add(legacyChannelTopic.trim());
    }

    await Promise.all(
      Array.from(topics).map((topic) =>
        fetch(`https://ntfy.sh/${encodeURIComponent(topic)}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          signal: AbortSignal.timeout(2500)
        }).catch(() => undefined)
      )
    );
  }

  private async pollNtfyHistoryInBackground(
    userId: string,
    channelTopic: string,
    localDeviceId: string,
    sinceTimestamp: number
  ): Promise<void> {
    const sinceStorageKey = `thingsbook_ntfy_since_${channelTopic}`;
    const lastMsgId = localStorage.getItem(sinceStorageKey) || '24h';

    const res = await fetch(
      `https://ntfy.sh/${encodeURIComponent(channelTopic)}/json?poll=1&since=${encodeURIComponent(lastMsgId)}`,
      {
        method: 'GET',
        signal: AbortSignal.timeout(2500)
      }
    ).catch(() => null);
    if (!res || !res.ok) return;
    const text = await res.text();
    const lines = text
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean);
    let newestMsgId: string | null = null;

    for (const line of lines) {
      try {
        const env = JSON.parse(line);
        if (typeof env?.id === 'string' && env.id.trim().length > 0) {
          newestMsgId = env.id.trim();
        }
        if (env?.event !== 'message' || typeof env?.message !== 'string') continue;
        const batch = JSON.parse(env.message) as Record<string, unknown>;
        if (batch?.userId !== userId) continue;
        if (batch?.deviceId === localDeviceId) continue;
        if (batch?.wakeOnly === true) continue;
        if (
          sinceTimestamp > 0 &&
          typeof batch?.batchTimestamp === 'number' &&
          batch.batchTimestamp <= computeEffectivePullCursor(sinceTimestamp)
        ) {
          continue;
        }
        await this.applyParsedNtfyBatch(batch);
      } catch {
        // skip malformed relay message
      }
    }

    if (newestMsgId) {
      localStorage.setItem(sinceStorageKey, newestMsgId);
    }
  }
}

export const cloudSync = new ThingsCloudSyncService();
