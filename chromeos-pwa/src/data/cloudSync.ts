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
  ChecklistItemRecord,
  CloudSyncStatus,
  HeadingRecord,
  ProjectRecord,
  TagRecord,
  TaskRecord
} from '../domain/types';
import {
  normalizeProjectStatus,
  normalizeScheduleState,
  normalizeTaskStatus,
  safeParseRecurrence
} from './backupCodec';
import { getFirebaseAuthInstance, getFirestoreInstance } from './firebaseClient';
import { loadPreferences, localDb } from './localDb';

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
  return {
    id: project.id,
    areaId: project.areaId ?? null,
    title: project.title,
    notes: project.notes || '',
    status: project.status,
    scheduleState: project.scheduleState,
    whenDate: project.whenDate != null ? Math.trunc(project.whenDate) : null,
    deadline: project.deadline != null ? Math.trunc(project.deadline) : null,
    completedAt: project.completedAt != null ? Math.trunc(project.completedAt) : null,
    sortOrder: finiteNumber(project.sortOrder),
    tagIds: project.tagIds || [],
    createdAt: Math.trunc(finiteNumber(project.createdAt)),
    updatedAt: Math.trunc(finiteNumber(project.updatedAt)),
    isDeleted: Boolean(project.isDeleted)
  };
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
    sortOrder: finiteNumber(map.sortOrder),
    tagIds: stringList(map.tagIds),
    createdAt: Math.trunc(finiteNumber(map.createdAt)),
    updatedAt: Math.trunc(finiteNumber(map.updatedAt)),
    isDeleted: Boolean(map.isDeleted),
    isDirty: false
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
  return {
    id: task.id,
    title: task.title,
    notes: task.notes || '',
    status: task.status,
    scheduleState: task.scheduleState,
    whenDate: task.whenDate != null ? Math.trunc(task.whenDate) : null,
    deadline: task.deadline != null ? Math.trunc(task.deadline) : null,
    reminderTime: task.reminderTime != null ? Math.trunc(task.reminderTime) : null,
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
}

function taskFromFirestoreMap(map: Record<string, unknown>): TaskRecord | null {
  const id = nullableString(map.id);
  if (!id) return null;
  return {
    id,
    title: typeof map.title === 'string' ? map.title : '',
    notes: typeof map.notes === 'string' ? map.notes : '',
    status: normalizeTaskStatus(typeof map.status === 'string' ? map.status : 'OPEN'),
    scheduleState: normalizeScheduleState(
      typeof map.scheduleState === 'string' ? map.scheduleState : 'INBOX',
      'INBOX'
    ),
    whenDate: nullableNumber(map.whenDate),
    deadline: nullableNumber(map.deadline),
    reminderTime: nullableNumber(map.reminderTime),
    completedAt: nullableNumber(map.completedAt),
    projectId: nullableString(map.projectId),
    headingId: nullableString(map.headingId),
    areaId: nullableString(map.areaId),
    recurrenceRule: safeParseRecurrence(nullableString(map.recurrenceRule)),
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

// ==================== Cloud Sync Engine ====================

type SyncListener = (snapshot: CloudSyncSnapshot) => void;

class ThingsCloudSyncService {
  private listeners = new Set<SyncListener>();
  private firestoreUnsubs: FirestoreUnsubscribe[] = [];
  private authUnsub: AuthUnsubscribe | null = null;
  private debounceTimer: number | null = null;
  private isSyncingPromise: Promise<void> | null = null;
  private state: CloudSyncSnapshot = {
    status: 'OFFLINE',
    userEmailOrId: null,
    userId: null,
    lastSyncedAt: 0,
    errorMessage: null
  };

  constructor() {
    localDb.setSyncTrigger(() => {
      this.triggerBackgroundSync(600);
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
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
  }

  private setLastSyncTs(userId: string, ts: number) {
    localStorage.setItem(`${LAST_SYNC_PREFIX}${userId}`, String(Math.trunc(ts)));
    this.emit({ lastSyncedAt: Math.trunc(ts) });
  }

  private isLocalOnly(): boolean {
    return loadPreferences().localOnlyDbMode;
  }

  async initializeSession(): Promise<void> {
    if (this.authUnsub) return;
    const auth = getFirebaseAuthInstance();
    this.authUnsub = onAuthStateChanged(auth, async (user) => {
      if (user && !user.email?.endsWith('@thingsbook.app')) {
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
          await this.syncBidirectionalDeltas();
          this.attachRealtimeListeners(user.uid);
        } else {
          this.emit({ status: 'OFFLINE' });
        }
      } else {
        this.detachRealtimeListeners();
        this.emit({
          status: 'OFFLINE',
          userId: null,
          userEmailOrId: null,
          errorMessage: null
        });
      }
    });

    window.addEventListener('online', () => {
      if (!this.isLocalOnly() && this.state.userId) {
        this.triggerBackgroundSync(250);
      }
    });
    window.addEventListener('offline', () => {
      this.emit({ status: 'OFFLINE' });
    });
  }

  onDatabaseModeChanged(localOnly: boolean) {
    if (localOnly) {
      this.detachRealtimeListeners();
      this.emit({ status: 'OFFLINE' });
    } else if (this.state.userId) {
      void this.syncBidirectionalDeltas();
      this.attachRealtimeListeners(this.state.userId);
    }
  }

  triggerBackgroundSync(delayMs = 500) {
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

  async registerEmailAccount(
    rawEmail: string,
    password: string,
    mergeLocalTasks = true
  ): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || password.length < 6) {
      throw new Error('Please enter a valid email address and a password with at least 6 characters.');
    }
    await this.prepareLocalDataBeforeLogin(mergeLocalTasks);
    this.emit({ status: 'SYNCING', errorMessage: null });

    const auth = getFirebaseAuthInstance();
    let uid: string;
    try {
      const cred = await createUserWithEmailAndPassword(auth, email, password);
      uid = cred.user.uid;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      if (msg.includes('email-already-in-use')) {
        const signInCred = await signInWithEmailAndPassword(auth, email, password);
        uid = signInCred.user.uid;
      } else {
        this.emit({ status: 'OFFLINE', errorMessage: msg });
        throw err;
      }
    }

    const pwVerifier = await sha256Hex(`thingsbook-pw-v1:${email}:${password}`);
    const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-chan-v1:${email}:${pwVerifier}`)).slice(0, 28)}`;

    this.savePersistedMeta({
      userId: uid,
      email,
      channelTopic,
      deviceId: this.getOrCreateDeviceId()
    });
    this.setLastSyncTs(uid, 0);
    await this.syncBidirectionalDeltas(true);
    this.attachRealtimeListeners(uid);
    return email;
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
    await this.prepareLocalDataBeforeLogin(mergeLocalTasks);
    this.emit({ status: 'SYNCING', errorMessage: null });

    const auth = getFirebaseAuthInstance();
    const cred = await signInWithEmailAndPassword(auth, email, password);
    const uid = cred.user.uid;

    const pwVerifier = await sha256Hex(`thingsbook-pw-v1:${email}:${password}`);
    const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-chan-v1:${email}:${pwVerifier}`)).slice(0, 28)}`;

    this.savePersistedMeta({
      userId: uid,
      email,
      channelTopic,
      deviceId: this.getOrCreateDeviceId()
    });
    this.setLastSyncTs(uid, 0);
    await this.syncBidirectionalDeltas(true);
    this.attachRealtimeListeners(uid);
    return email;
  }

  async signInWithGooglePopup(mergeLocalTasks = true): Promise<string> {
    await this.prepareLocalDataBeforeLogin(mergeLocalTasks);
    this.emit({ status: 'SYNCING', errorMessage: null });

    const auth = getFirebaseAuthInstance();
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    const cred = await signInWithPopup(auth, provider);
    const user = cred.user;
    const email = (user.email || '').trim().toLowerCase();
    const uid = user.uid;

    // Maintain compatibility with Android's Instant Cloud deterministic Email/Password sign-in
    if (email && !user.providerData.some((p) => p.providerId === 'password')) {
      try {
        const instantHash = await sha256Hex(`tb-instant-v2:${email}`);
        const derivedPassword = `TbCloud!${instantHash.slice(0, 14)}`;
        await linkWithCredential(user, EmailAuthProvider.credential(email, derivedPassword));
      } catch {
        // Safe to ignore if already linked or disabled
      }
    }

    const channelTopic = email
      ? `tb_sync_${(await sha256Hex(`thingsbook-instant:${email}`)).slice(0, 28)}`
      : `tb_sync_${(await sha256Hex(uid)).slice(0, 28)}`;

    this.savePersistedMeta({
      userId: uid,
      email: email || uid,
      channelTopic,
      deviceId: this.getOrCreateDeviceId()
    });
    this.setLastSyncTs(uid, 0);
    await this.syncBidirectionalDeltas(true);
    this.attachRealtimeListeners(uid);
    return email || uid;
  }

  async signInInstantEmail(rawEmail: string, mergeLocalTasks = true): Promise<string> {
    const email = rawEmail.trim().toLowerCase();
    if (!email.includes('@') || !email.includes('.') || email.endsWith('@thingsbook.app')) {
      throw new Error('Please enter a valid email address for Instant Cloud Sync.');
    }
    await this.prepareLocalDataBeforeLogin(mergeLocalTasks);
    this.emit({ status: 'SYNCING', errorMessage: null });

    const instantHash = await sha256Hex(`tb-instant-v2:${email}`);
    const derivedPassword = `TbCloud!${instantHash.slice(0, 14)}`;
    const auth = getFirebaseAuthInstance();

    let uid: string;
    try {
      const cred = await signInWithEmailAndPassword(auth, email, derivedPassword);
      uid = cred.user.uid;
    } catch {
      try {
        const created = await createUserWithEmailAndPassword(auth, email, derivedPassword);
        uid = created.user.uid;
      } catch {
        this.emit({ status: 'OFFLINE' });
        throw new Error(
          `A custom password is already set for ${email}. Please enter your password above and click "Sign In with Email" (or use Google Sign-In).`
        );
      }
    }

    const channelTopic = `tb_sync_${(await sha256Hex(`thingsbook-instant:${email}`)).slice(0, 28)}`;
    this.savePersistedMeta({
      userId: uid,
      email,
      channelTopic,
      deviceId: this.getOrCreateDeviceId()
    });
    this.setLastSyncTs(uid, 0);
    await this.syncBidirectionalDeltas(true);
    this.attachRealtimeListeners(uid);
    return email;
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

  // ==================== Bidirectional Delta Sync ====================

  async syncBidirectionalDeltas(forceFullPull = false): Promise<void> {
    if (this.isLocalOnly()) {
      this.emit({ status: 'OFFLINE' });
      return;
    }
    if (!navigator.onLine) {
      this.emit({ status: 'OFFLINE' });
      return;
    }
    if (this.isSyncingPromise) {
      return this.isSyncingPromise;
    }

    this.isSyncingPromise = this.runSyncInternal(forceFullPull).finally(() => {
      this.isSyncingPromise = null;
    });
    return this.isSyncingPromise;
  }

  private async runSyncInternal(forceFullPull: boolean): Promise<void> {
    const auth = getFirebaseAuthInstance();
    const userId = auth.currentUser?.uid || this.state.userId;
    if (!userId) {
      this.emit({ status: 'OFFLINE' });
      return;
    }

    this.emit({ status: 'SYNCING', errorMessage: null });

    try {
      const lastSync = forceFullPull ? 0 : this.getLastSyncTs(userId);

      // 1. Pull remote deltas first on initial full pull so cloud wins if newer, or push dirty first
      await this.pushDirtyRecordsToFirestore(userId);
      const pulledMax = await this.pullDeltaRecordsFromFirestore(userId, lastSync);

      // Optional secondary relay push/pull via ntfy topic if persisted
      const meta = this.loadPersistedMeta();
      if (meta?.channelTopic) {
        await this.syncSecondaryNtfyRelay(userId, meta.channelTopic, meta.deviceId, lastSync).catch(
          () => undefined
        );
      }

      const nextSyncTs = pulledMax > lastSync ? pulledMax : lastSync === 0 ? Date.now() : lastSync;
      this.setLastSyncTs(userId, nextSyncTs);
      this.emit({ status: 'SYNCED', errorMessage: null });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      this.emit({ status: 'OFFLINE', errorMessage: msg });
    }
  }

  private async pushDirtyRecordsToFirestore(userId: string): Promise<void> {
    const dirty = await localDb.getDirtyEntities();
    const ops: Array<{ col: string; id: string; data: Record<string, unknown> }> = [];

    for (const tag of dirty.tags) {
      ops.push({ col: 'tags', id: tag.id, data: tagToFirestoreMap(tag) });
    }
    for (const area of dirty.areas) {
      ops.push({ col: 'areas', id: area.id, data: areaToFirestoreMap(area) });
    }
    for (const proj of dirty.projects) {
      ops.push({ col: 'projects', id: proj.id, data: projectToFirestoreMap(proj) });
    }
    for (const head of dirty.headings) {
      ops.push({ col: 'headings', id: head.id, data: headingToFirestoreMap(head) });
    }
    for (const task of dirty.tasks) {
      ops.push({ col: 'tasks', id: task.id, data: taskToFirestoreMap(task) });
    }
    for (const chk of dirty.checklistItems) {
      ops.push({ col: 'checklist_items', id: chk.id, data: checklistToFirestoreMap(chk) });
    }

    if (ops.length === 0) return;

    const db = getFirestoreInstance();
    const CHUNK = 450;
    for (let i = 0; i < ops.length; i += CHUNK) {
      const slice = ops.slice(i, i + CHUNK);
      const batch = writeBatch(db);
      for (const op of slice) {
        const ref = doc(db, 'users', userId, op.col, op.id);
        batch.set(ref, op.data, { merge: true });
      }
      await batch.commit();
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
      }
    });
  }

  private async pullDeltaRecordsFromFirestore(userId: string, sinceTimestamp: number): Promise<number> {
    const db = getFirestoreInstance();
    const fetchCollection = async (colName: string): Promise<Record<string, unknown>[]> => {
      const colRef = collection(db, 'users', userId, colName);
      const q = sinceTimestamp > 0 ? query(colRef, where('updatedAt', '>', sinceTimestamp)) : colRef;
      const snap = await getDocs(q);
      return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    };

    const [rawTags, rawAreas, rawProjects, rawHeadings, rawTasks, rawChecklists] = await Promise.all([
      fetchCollection('tags'),
      fetchCollection('areas'),
      fetchCollection('projects'),
      fetchCollection('headings'),
      fetchCollection('tasks'),
      fetchCollection('checklist_items')
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

    if (
      tags.length === 0 &&
      areas.length === 0 &&
      projects.length === 0 &&
      headings.length === 0 &&
      tasks.length === 0 &&
      checklistItems.length === 0
    ) {
      return sinceTimestamp;
    }

    return localDb.applyRemoteDeltas({
      tags,
      areas,
      projects,
      headings,
      tasks,
      checklistItems
    });
  }

  private attachRealtimeListeners(userId: string) {
    this.detachRealtimeListeners();
    if (this.isLocalOnly()) return;

    const db = getFirestoreInstance();
    const collections = [
      'tags',
      'areas',
      'projects',
      'headings',
      'tasks',
      'checklist_items'
    ] as const;

    for (const colName of collections) {
      const since = Math.max(0, this.getLastSyncTs(userId) - 60_000);
      const colRef = collection(db, 'users', userId, colName);
      const q = since > 0 ? query(colRef, where('updatedAt', '>', since)) : colRef;

      const unsub = onSnapshot(
        q,
        async (snapshot) => {
          if (snapshot.metadata.hasPendingWrites) return;
          const changed = snapshot.docChanges().filter((c) => c.type === 'added' || c.type === 'modified');
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
          }

          if (maxTs > this.getLastSyncTs(userId)) {
            this.setLastSyncTs(userId, maxTs);
          }
          this.emit({ status: 'SYNCED' });
        },
        () => {
          // Silently fallback on transient listener disconnect
        }
      );
      this.firestoreUnsubs.push(unsub);
    }
  }

  private detachRealtimeListeners() {
    for (const u of this.firestoreUnsubs) {
      try {
        u();
      } catch {
        // ignore
      }
    }
    this.firestoreUnsubs = [];
  }

  private async syncSecondaryNtfyRelay(
    userId: string,
    channelTopic: string,
    localDeviceId: string,
    sinceTimestamp: number
  ): Promise<void> {
    const res = await fetch(`https://ntfy.sh/${encodeURIComponent(channelTopic)}/json?poll=1&since=24h`, {
      method: 'GET'
    });
    if (!res.ok) return;
    const text = await res.text();
    const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
    for (const line of lines) {
      try {
        const env = JSON.parse(line);
        if (env?.event !== 'message' || typeof env?.message !== 'string') continue;
        const batch = JSON.parse(env.message);
        if (batch?.userId !== userId) continue;
        if (batch?.deviceId === localDeviceId && sinceTimestamp > 0 && batch.batchTimestamp <= sinceTimestamp) {
          continue;
        }
        await localDb.applyRemoteDeltas({
          tags: Array.isArray(batch.tags)
            ? batch.tags.map(tagFromFirestoreMap).filter((x: TagRecord | null): x is TagRecord => x !== null)
            : undefined,
          areas: Array.isArray(batch.areas)
            ? batch.areas.map(areaFromFirestoreMap).filter((x: AreaRecord | null): x is AreaRecord => x !== null)
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
            ? batch.tasks.map(taskFromFirestoreMap).filter((x: TaskRecord | null): x is TaskRecord => x !== null)
            : undefined,
          checklistItems: Array.isArray(batch.checklistItems)
            ? batch.checklistItems
                .map(checklistFromFirestoreMap)
                .filter((x: ChecklistItemRecord | null): x is ChecklistItemRecord => x !== null)
            : undefined
        });
      } catch {
        // skip malformed relay message
      }
    }
  }
}

export const cloudSync = new ThingsCloudSyncService();
