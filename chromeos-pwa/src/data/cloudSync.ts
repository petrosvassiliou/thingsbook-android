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
  ProjectTemplate,
  TagRecord
} from '../domain/types';
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
  COLLECTION_CURSOR_STORAGE_PREFIX,
  ENTITIES_RECONCILE_V2_21_STORAGE_PREFIX,
  ENTITIES_RECONCILE_V2_22_STORAGE_PREFIX,
  FAST_SYNC_COALESCE_MS,
  METADATA_BATCH_CHUNK_SIZE,
  PROJECTS_REVIEW_BACKFILL_STORAGE_PREFIX,
  SYNC_COLLECTION_NAMES,
  attachmentToFirestoreMap,
  buildLegacyCompatibleNtfyChunks,
  buildNtfyRelayOrWakePayload,
  chunkItems,
  clampRemoteCursorTimestamp,
  computeEffectivePullCursor,
  computeNextCollectionCursor,
  detectMissingReferencedEntityContainers,
  formatInstantChannelSeed,
  formatLegacySyncChannelFromHex,
  formatOAuthChannelSeed,
  formatUidWakeChannelFromHex,
  formatUidWakeSeed,
  headingFromFirestoreMap,
  headingToFirestoreMap,
  normalizeFirestoreDocWithId,
  projectFromFirestoreMap,
  projectToFirestoreMap,
  taskFromFirestoreMap,
  taskToFirestoreMap,
  type RemoteHeadingWithHints,
  type RemoteProjectWithHints,
  type RemoteTaskWithHints,
  type SyncCollectionName
} from './syncUtils';

export {
  headingFromFirestoreMap,
  headingToFirestoreMap,
  projectFromFirestoreMap,
  projectToFirestoreMap,
  taskFromFirestoreMap,
  taskToFirestoreMap,
  type RemoteHeadingWithHints,
  type RemoteProjectWithHints,
  type RemoteTaskWithHints
};

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
const LAST_SIGNED_IN_UID_KEY = 'thingsbook_last_signed_in_uid_v1';
const LAST_SYNC_PREFIX = 'thingsbook_last_sync_ts_';
const LAST_WALL_SYNC_PREFIX = 'thingsbook_last_wall_sync_ts_';

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
  private fallbackPollInterval: number | null = null;
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
  }

  private getCollectionCursor(userId: string, colName: SyncCollectionName): number {
    const raw = localStorage.getItem(`${COLLECTION_CURSOR_STORAGE_PREFIX}${userId}_${colName}`);
    if (raw !== null) {
      const parsed = Number(raw);
      if (!Number.isFinite(parsed) || parsed <= 0) return 0;
      return clampRemoteCursorTimestamp(parsed, Date.now());
    }
    return this.getLastSyncTs(userId);
  }

  private setCollectionCursor(userId: string, colName: SyncCollectionName, ts: number) {
    const clamped = ts > 0 ? clampRemoteCursorTimestamp(ts, Date.now()) : 0;
    localStorage.setItem(
      `${COLLECTION_CURSOR_STORAGE_PREFIX}${userId}_${colName}`,
      String(Math.trunc(clamped))
    );
  }

  private resetAllCollectionCursors(userId: string) {
    for (const colName of SYNC_COLLECTION_NAMES) {
      this.setCollectionCursor(userId, colName, 0);
    }
    this.setLastSyncTs(userId, 0);
    localStorage.removeItem(`${LAST_WALL_SYNC_PREFIX}${userId}`);
    this.emit({ lastSyncedAt: 0 });
  }

  private getLastWallSyncDisplay(userId: string): number {
    const raw = localStorage.getItem(`${LAST_WALL_SYNC_PREFIX}${userId}`);
    const parsed = raw ? Number(raw) : 0;
    if (Number.isFinite(parsed) && parsed > 0) {
      return Math.trunc(parsed);
    }
    return this.getLastSyncTs(userId);
  }

  private recordWallSyncDisplay(userId: string, nowMs: number = Date.now()) {
    const ts = Math.trunc(nowMs);
    localStorage.setItem(`${LAST_WALL_SYNC_PREFIX}${userId}`, String(ts));
    this.emit({ lastSyncedAt: ts });
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
              ? formatLegacySyncChannelFromHex(await sha256Hex(formatInstantChannelSeed(lowerEmail)))
              : formatLegacySyncChannelFromHex(await sha256Hex(formatOAuthChannelSeed(user.uid)));

        localStorage.setItem(LAST_SIGNED_IN_UID_KEY, user.uid);
        this.savePersistedMeta({
          userId: user.uid,
          email: lowerEmail || user.uid,
          channelTopic,
          deviceId: this.getOrCreateDeviceId()
        });

        this.emit({
          userId: user.uid,
          userEmailOrId: user.email || user.uid,
          lastSyncedAt: this.getLastWallSyncDisplay(user.uid),
          errorMessage: null
        });

        if (!this.isLocalOnly()) {
          await this.syncBidirectionalDeltas();
          if (!this.isLocalOnly() && this.state.userId === user.uid) {
            this.attachRealtimeListeners(user.uid);
          }
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
        const uid = this.state.userId;
        if (!this.isLocalOnly() && uid) {
          // Pull remote deltas FIRST before restamping & pushing offline edits
          void this.syncBidirectionalDeltas(false).finally(() => {
            if (!this.isLocalOnly() && this.state.userId === uid && navigator.onLine) {
              this.attachRealtimeListeners(uid);
            }
          });
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
      const uid = this.state.userId;
      const forceFull = this.getLastSyncTs(uid) === 0;
      void this.syncBidirectionalDeltas(forceFull).finally(() => {
        if (!this.isLocalOnly() && this.state.userId === uid) {
          this.attachRealtimeListeners(uid);
        }
      });
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

  private async prepareLocalDataBeforeLogin(uid: string, mergeLocalTasks: boolean) {
    const previousSignedInUid = localStorage.getItem(LAST_SIGNED_IN_UID_KEY);
    if (previousSignedInUid && previousSignedInUid !== uid) {
      // Never leak cached records from a previously signed-in cloud account into a different UID.
      await localDb.purgeAllLocalDataBeforeAccountPull();
      return;
    }
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
    await this.prepareLocalDataBeforeLogin(uid, mergeLocalTasks);
    localStorage.setItem(LAST_SIGNED_IN_UID_KEY, uid);
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
    this.resetAllCollectionCursors(uid);
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

  private async deriveLegacySyncTopics(
    userId: string,
    emailOrId: string | null | undefined,
    persistedChannelTopic: string | null | undefined
  ): Promise<string[]> {
    const topics = new Set<string>();
    const persisted = (persistedChannelTopic || '').trim();
    if (persisted.startsWith('tb_sync_')) {
      topics.add(persisted);
    }
    const oauthHex = await sha256Hex(formatOAuthChannelSeed(userId));
    topics.add(formatLegacySyncChannelFromHex(oauthHex));
    const lowerEmail = (emailOrId || '').trim().toLowerCase();
    if (lowerEmail.includes('@') && !lowerEmail.endsWith('@thingsbook.app')) {
      const instantHex = await sha256Hex(formatInstantChannelSeed(lowerEmail));
      topics.add(formatLegacySyncChannelFromHex(instantHex));
    }
    return Array.from(topics);
  }

  private async runFastPushLoop(): Promise<void> {
    if (this.isLocalOnly() || !navigator.onLine) return;
    if (this.isSyncingPromise) {
      this.pendingFastPush = true;
      return;
    }
    if (this.isPushingPromise) {
      this.pendingFastPush = true;
      return this.isPushingPromise;
    }

    this.isPushingPromise = (async () => {
      try {
        while (true) {
          this.pendingFastPush = false;
          if (this.isSyncingPromise) {
            this.pendingFastPush = true;
            break;
          }
          const auth = getFirebaseAuthInstance();
          const userId = auth.currentUser?.uid || this.state.userId;
          if (!userId || this.isLocalOnly() || !navigator.onLine) break;

          // If any dirty records were edited offline (>30s ago), pull remote deltas FIRST
          // before restamping and committing them so LWW never overwrites newer remote edits.
          if (await localDb.hasStaleOfflineDirtyEntities()) {
            void this.syncBidirectionalDeltas(false);
            break;
          }

          const relayPushMaps = await this.pushDirtyRecordsToFirestore(userId, false);
          if (relayPushMaps) {
            this.recordWallSyncDisplay(userId, Date.now());
            this.emit({ status: 'SYNCED', errorMessage: null });
            const meta = this.loadPersistedMeta();
            const localDeviceId = meta?.deviceId || this.getOrCreateDeviceId();
            const legacyTopics = await this.deriveLegacySyncTopics(
              userId,
              auth.currentUser?.email || meta?.email || this.state.userEmailOrId,
              meta?.channelTopic
            );
            void this.broadcastNtfyWakeAndDeltas(
              userId,
              legacyTopics,
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
        if (this.isPushingPromise) {
          await this.isPushingPromise.catch(() => undefined);
        }
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
        if (this.pendingFastPush && !this.isLocalOnly() && navigator.onLine) {
          this.pendingFastPush = false;
          this.triggerFastPush(0);
        }
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
      const prevLowWatermark = forceFullPull ? 0 : this.getLastSyncTs(userId);
      const meta = this.loadPersistedMeta();
      const localDeviceId = meta?.deviceId || this.getOrCreateDeviceId();
      const legacyTopics = await this.deriveLegacySyncTopics(
        userId,
        auth.currentUser?.email || meta?.email || this.state.userEmailOrId,
        meta?.channelTopic
      );

      // 1. Pull remote deltas FIRST so LWW compares true edit timestamps before restamping
      //    any surviving offline dirty records, and so fresh logins merge cloud Areas/Tags/Tasks cleanly.
      await this.pullDeltaRecordsFromFirestore(userId, forceFullPull);

      if (forceFullPull && legacyTopics.length > 0) {
        await Promise.all(
          legacyTopics.map((topic) =>
            this.pollNtfyHistoryInBackground(userId, topic, localDeviceId, prevLowWatermark)
          )
        );
      }

      // 2. Push surviving dirty records (restamping any stale >30s offline edits to pushNow
      //    so online v1.8 and v2.x peers never skip them behind their 120s cursor window).
      const relayPushMaps = await this.pushDirtyRecordsToFirestore(userId, true);
      if (relayPushMaps) {
        void this.broadcastNtfyWakeAndDeltas(
          userId,
          legacyTopics,
          localDeviceId,
          relayPushMaps
        );
      }

      this.recordWallSyncDisplay(userId, Date.now());
      this.emit({ status: 'SYNCED', errorMessage: null });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      this.emit({ status: 'OFFLINE', errorMessage: msg });
    }
  }

  private async pushDirtyRecordsToFirestore(
    userId: string,
    restampStaleOffline = false
  ): Promise<{
    tags: Record<string, unknown>[];
    areas: Record<string, unknown>[];
    projects: Record<string, unknown>[];
    headings: Record<string, unknown>[];
    tasks: Record<string, unknown>[];
    checklistItems: Record<string, unknown>[];
    attachments: Record<string, unknown>[];
    projectTemplates: Record<string, unknown>[];
  } | null> {
    const pushNow = Date.now();
    const dirty = await localDb.getDirtyEntities(
      restampStaleOffline ? { restampStaleOfflineAt: pushNow } : undefined
    );
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

    const toSpec = <T extends { id: string; updatedAt: number }>(arr: T[]) => {
      const updatedAtById: Record<string, number> = {};
      let maxUpdatedAt = 0;
      for (const x of arr) {
        updatedAtById[x.id] = x.updatedAt;
        if (x.updatedAt > maxUpdatedAt) maxUpdatedAt = x.updatedAt;
      }
      return { ids: arr.map((x) => x.id), maxUpdatedAt, updatedAtById };
    };

    await localDb.markSynced({
      tags: toSpec(dirty.tags),
      areas: toSpec(dirty.areas),
      projects: toSpec(dirty.projects),
      headings: toSpec(dirty.headings),
      tasks: toSpec(dirty.tasks),
      checklistItems: toSpec(dirty.checklistItems),
      attachments: toSpec(dirty.attachments),
      projectTemplates: toSpec(dirty.projectTemplates)
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
    forceFullPull: boolean
  ): Promise<void> {
    const db = getFirestoreInstance();
    const backfillKey = `${ATTACHMENT_BACKFILL_STORAGE_PREFIX}${userId}`;
    const needsAttachmentBackfill = localStorage.getItem(backfillKey) !== '1';
    const projReviewBackfillKey = `${PROJECTS_REVIEW_BACKFILL_STORAGE_PREFIX}${userId}`;
    const needsProjectsReviewBackfill = localStorage.getItem(projReviewBackfillKey) !== '1';
    const reconcileV221Key = `${ENTITIES_RECONCILE_V2_21_STORAGE_PREFIX}${userId}`;
    const reconcileV222Key = `${ENTITIES_RECONCILE_V2_22_STORAGE_PREFIX}${userId}`;
    const needsReconcileV222 = localStorage.getItem(reconcileV222Key) !== '1';
    const orphanCheck = detectMissingReferencedEntityContainers(localDb.getSnapshot());

    const prevCursors: Record<SyncCollectionName, number> = {
      tags: forceFullPull || needsReconcileV222 ? 0 : this.getCollectionCursor(userId, 'tags'),
      areas:
        forceFullPull ||
        needsReconcileV222 ||
        orphanCheck.hasOrphanAreaRef ||
        orphanCheck.hasOrphanProjectRef
          ? 0
          : this.getCollectionCursor(userId, 'areas'),
      projects:
        forceFullPull ||
        needsReconcileV222 ||
        needsProjectsReviewBackfill ||
        orphanCheck.hasOrphanProjectRef
          ? 0
          : this.getCollectionCursor(userId, 'projects'),
      headings:
        forceFullPull ||
        needsReconcileV222 ||
        orphanCheck.hasOrphanHeadingRef ||
        orphanCheck.hasOrphanProjectRef
          ? 0
          : this.getCollectionCursor(userId, 'headings'),
      tasks: forceFullPull || needsReconcileV222 ? 0 : this.getCollectionCursor(userId, 'tasks'),
      checklist_items:
        forceFullPull || needsReconcileV222
          ? 0
          : this.getCollectionCursor(userId, 'checklist_items'),
      attachments:
        forceFullPull || needsAttachmentBackfill || needsReconcileV222
          ? 0
          : this.getCollectionCursor(userId, 'attachments'),
      project_templates:
        forceFullPull || needsReconcileV222
          ? 0
          : this.getCollectionCursor(userId, 'project_templates')
    };

    const fetchCollection = async (
      colName: SyncCollectionName
    ): Promise<Record<string, unknown>[]> => {
      const colSince = computeEffectivePullCursor(prevCursors[colName]);
      const colRef = collection(db, 'users', userId, colName);
      const q = colSince > 0 ? query(colRef, where('updatedAt', '>', colSince)) : colRef;
      const snap = await getDocs(q);
      return snap.docs.map((d) => normalizeFirestoreDocWithId(d.id, d.data()));
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
      fetchCollection('tags'),
      fetchCollection('areas'),
      fetchCollection('projects'),
      fetchCollection('headings'),
      fetchCollection('tasks'),
      fetchCollection('checklist_items'),
      fetchCollection('attachments'),
      fetchCollection('project_templates').catch(() => null)
    ]);

    const tags = rawTags.map(tagFromFirestoreMap).filter((x): x is TagRecord => x !== null);
    const areas = rawAreas.map(areaFromFirestoreMap).filter((x): x is AreaRecord => x !== null);
    const projects = rawProjects
      .map((m) => projectFromFirestoreMap(m))
      .filter((x): x is RemoteProjectWithHints => x !== null);
    const headings = rawHeadings
      .map((m) => headingFromFirestoreMap(m))
      .filter((x): x is RemoteHeadingWithHints => x !== null);
    const tasks = rawTasks
      .map((m) => taskFromFirestoreMap(m))
      .filter((x): x is RemoteTaskWithHints => x !== null);
    const checklistItems = rawChecklists
      .map(checklistFromFirestoreMap)
      .filter((x): x is ChecklistItemRecord => x !== null);
    const attachments = rawAttachments
      .map(attachmentFromFirestoreMap)
      .filter((x): x is AttachmentRecord => x !== null);
    const projectTemplates = (rawTemplates ?? [])
      .map(projectTemplateFromFirestoreMap)
      .filter((x): x is ProjectTemplate => x !== null);

    const hasAnyRecords =
      tags.length > 0 ||
      areas.length > 0 ||
      projects.length > 0 ||
      headings.length > 0 ||
      tasks.length > 0 ||
      checklistItems.length > 0 ||
      attachments.length > 0 ||
      projectTemplates.length > 0;

    if (hasAnyRecords) {
      await localDb.applyRemoteDeltas({
        tags,
        areas,
        projects,
        headings,
        tasks,
        checklistItems,
        attachments,
        projectTemplates
      });
    }

    const nowMs = Date.now();
    const maxOf = <T extends { updatedAt: number }>(arr: readonly T[]): number =>
      arr.reduce((m, x) => (x.updatedAt > m ? x.updatedAt : m), 0);

    this.setCollectionCursor(
      userId,
      'tags',
      computeNextCollectionCursor(prevCursors.tags, maxOf(tags), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'areas',
      computeNextCollectionCursor(prevCursors.areas, maxOf(areas), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'projects',
      computeNextCollectionCursor(prevCursors.projects, maxOf(projects), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'headings',
      computeNextCollectionCursor(prevCursors.headings, maxOf(headings), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'tasks',
      computeNextCollectionCursor(prevCursors.tasks, maxOf(tasks), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'checklist_items',
      computeNextCollectionCursor(prevCursors.checklist_items, maxOf(checklistItems), nowMs)
    );
    this.setCollectionCursor(
      userId,
      'attachments',
      computeNextCollectionCursor(prevCursors.attachments, maxOf(attachments), nowMs)
    );
    if (rawTemplates !== null) {
      this.setCollectionCursor(
        userId,
        'project_templates',
        computeNextCollectionCursor(prevCursors.project_templates, maxOf(projectTemplates), nowMs)
      );
    }

    if (needsAttachmentBackfill) {
      localStorage.setItem(backfillKey, '1');
    }
    if (needsProjectsReviewBackfill) {
      localStorage.setItem(projReviewBackfillKey, '1');
    }
    if (needsReconcileV222 && rawTemplates !== null) {
      localStorage.setItem(reconcileV221Key, '1');
      localStorage.setItem(reconcileV222Key, '1');
    }

    const lowWatermark = Math.min(
      ...SYNC_COLLECTION_NAMES.map((col) => this.getCollectionCursor(userId, col))
    );
    this.setLastSyncTs(userId, lowWatermark);
  }

  private scheduleRealtimeReconnect(userId: string) {
    if (typeof window === 'undefined' || this.isLocalOnly()) return;
    if (this.realtimeReconnectTimer != null) return;
    this.realtimeReconnectTimer = window.setTimeout(() => {
      this.realtimeReconnectTimer = null;
      if (!this.isLocalOnly() && this.state.userId === userId && navigator.onLine) {
        void this.syncBidirectionalDeltas(false).finally(() => {
          if (!this.isLocalOnly() && this.state.userId === userId && navigator.onLine) {
            this.attachRealtimeListeners(userId);
          }
        });
      }
    }, 1200);
  }

  private attachRealtimeListeners(userId: string) {
    this.detachRealtimeListeners();
    if (this.isLocalOnly()) return;

    const db = getFirestoreInstance();
    const localDeviceId = this.getOrCreateDeviceId();

    for (const colName of SYNC_COLLECTION_NAMES) {
      const since = computeEffectivePullCursor(this.getCollectionCursor(userId, colName));
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

          const docs = changed.map((c) => normalizeFirestoreDocWithId(c.doc.id, c.doc.data()));

          if (colName === 'tags') {
            await localDb.applyRemoteDeltas({
              tags: docs.map(tagFromFirestoreMap).filter((x): x is TagRecord => x !== null)
            });
          } else if (colName === 'areas') {
            await localDb.applyRemoteDeltas({
              areas: docs.map(areaFromFirestoreMap).filter((x): x is AreaRecord => x !== null)
            });
          } else if (colName === 'projects') {
            await localDb.applyRemoteDeltas({
              projects: docs
                .map((m) => projectFromFirestoreMap(m))
                .filter((x): x is RemoteProjectWithHints => x !== null)
            });
          } else if (colName === 'headings') {
            await localDb.applyRemoteDeltas({
              headings: docs
                .map((m) => headingFromFirestoreMap(m))
                .filter((x): x is RemoteHeadingWithHints => x !== null)
            });
          } else if (colName === 'tasks') {
            await localDb.applyRemoteDeltas({
              tasks: docs
                .map((m) => taskFromFirestoreMap(m))
                .filter((x): x is RemoteTaskWithHints => x !== null)
            });
          } else if (colName === 'checklist_items') {
            await localDb.applyRemoteDeltas({
              checklistItems: docs
                .map(checklistFromFirestoreMap)
                .filter((x): x is ChecklistItemRecord => x !== null)
            });
          } else if (colName === 'attachments') {
            await localDb.applyRemoteDeltas({
              attachments: docs
                .map(attachmentFromFirestoreMap)
                .filter((x): x is AttachmentRecord => x !== null)
            });
          } else if (colName === 'project_templates') {
            await localDb.applyRemoteDeltas({
              projectTemplates: docs
                .map(projectTemplateFromFirestoreMap)
                .filter((x): x is ProjectTemplate => x !== null)
            });
          }

          // Never advance pull cursors from a single-collection listener callback;
          // only update the wall-clock UI display timestamp.
          this.recordWallSyncDisplay(userId, Date.now());
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

    if (typeof window !== 'undefined' && this.fallbackPollInterval == null) {
      this.fallbackPollInterval = window.setInterval(() => {
        if (
          !this.isLocalOnly() &&
          this.state.userId === userId &&
          navigator.onLine &&
          typeof document !== 'undefined' &&
          document.visibilityState === 'visible'
        ) {
          this.triggerIncrementalPull(0);
        }
      }, 20_000);
    }
  }

  private async attachLiveWakeEventSource(userId: string, localDeviceId: string) {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') return;
    if (this.isLocalOnly() || this.state.userId !== userId) return;

    const meta = this.loadPersistedMeta();
    const auth = getFirebaseAuthInstance();
    const [uidWakeTopic, legacyTopics] = await Promise.all([
      this.deriveUidWakeTopic(userId),
      this.deriveLegacySyncTopics(
        userId,
        auth.currentUser?.email || meta?.email || this.state.userEmailOrId,
        meta?.channelTopic
      )
    ]);
    if (this.isLocalOnly() || this.state.userId !== userId) return;

    if (this.liveEventSource) {
      try {
        this.liveEventSource.close();
      } catch {
        // ignore
      }
      this.liveEventSource = null;
    }

    const allStreamTopics = Array.from(new Set([uidWakeTopic, ...legacyTopics]));
    const streamPath = allStreamTopics.map(encodeURIComponent).join(',');

    try {
      const es = new EventSource(`https://ntfy.sh/${streamPath}/sse`);
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
        // Never advance Firestore pull cursors from a partial ntfy SSE batch;
        // update wall-clock UI display and trigger a reconciled Firestore pull.
        this.recordWallSyncDisplay(userId, Date.now());
        this.emit({ status: 'SYNCED', errorMessage: null });
        this.triggerIncrementalPull(40);
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
            .map((m: Record<string, unknown>) => projectFromFirestoreMap(m))
            .filter((x: RemoteProjectWithHints | null): x is RemoteProjectWithHints => x !== null)
        : undefined,
      headings: Array.isArray(batch.headings)
        ? batch.headings
            .map((m: Record<string, unknown>) => headingFromFirestoreMap(m))
            .filter((x: RemoteHeadingWithHints | null): x is RemoteHeadingWithHints => x !== null)
        : undefined,
      tasks: Array.isArray(batch.tasks)
        ? batch.tasks
            .map((m: Record<string, unknown>) => taskFromFirestoreMap(m))
            .filter((x: RemoteTaskWithHints | null): x is RemoteTaskWithHints => x !== null)
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
    if (this.fallbackPollInterval != null && typeof window !== 'undefined') {
      window.clearInterval(this.fallbackPollInterval);
      this.fallbackPollInterval = null;
    }
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
    legacyTopics: readonly string[],
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
    const batchTimestamp = Date.now();
    const relayInput = {
      userId,
      deviceId: localDeviceId,
      batchTimestamp,
      tags: pushedDirtyMaps.tags,
      areas: pushedDirtyMaps.areas,
      projects: pushedDirtyMaps.projects,
      headings: pushedDirtyMaps.headings,
      tasks: pushedDirtyMaps.tasks,
      checklistItems: pushedDirtyMaps.checklistItems,
      attachments: pushedDirtyMaps.attachments,
      projectTemplates: pushedDirtyMaps.projectTemplates
    };

    const uidPayload = buildNtfyRelayOrWakePayload(relayInput);
    const legacyChunks = buildLegacyCompatibleNtfyChunks(relayInput);
    const uidWakeTopic = await this.deriveUidWakeTopic(userId);

    const postRequests: Promise<unknown>[] = [];
    if (uidPayload !== null) {
      postRequests.push(
        fetch(`https://ntfy.sh/${encodeURIComponent(uidWakeTopic)}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: uidPayload,
          signal: AbortSignal.timeout(2500)
        }).catch(() => undefined)
      );
    }

    for (const topic of legacyTopics) {
      const cleanTopic = topic.trim();
      if (!cleanTopic || cleanTopic === uidWakeTopic) continue;
      for (const chunkBody of legacyChunks) {
        postRequests.push(
          fetch(`https://ntfy.sh/${encodeURIComponent(cleanTopic)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: chunkBody,
            signal: AbortSignal.timeout(2500)
          }).catch(() => undefined)
        );
      }
    }

    if (postRequests.length > 0) {
      await Promise.all(postRequests);
    }
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
