import { openDB, type IDBPDatabase } from 'idb';
import {
  APP_VERSION,
  MAX_AREAS,
  addDaysLocal,
  calculateNextRecurrenceDate,
  computeMidpointSortOrder,
  endOfDayEpoch,
  isoDayOfWeek,
  startOfDayEpoch,
  type AppPreferences,
  type AreaRecord,
  type AttachmentRecord,
  type ChecklistItemRecord,
  type DetachedProjectTaskSnapshot,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectStatus,
  type ProjectTemplate,
  type ProjectTemplateBlueprint,
  type ProjectTemplateTaskBlueprint,
  type RecurrenceRule,
  type ScheduleState,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus,
  type ThingsbookStatistics
} from '../domain/types';
import { buildProjectTemplateBlueprint } from '../domain/v2Utils';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  inspectThingsBackup,
  normalizeBackupPayloadToEntities,
  summarizeBackupPayload,
  type BackupInspection,
  type BackupPreferencesDto,
  type BackupSummary
} from './backupCodec';
import { parseCsvTaskImport, serializeEntitiesToCsv } from './csvCodec';
import {
  SAMPLE_CHECKLIST_IDS,
  SAMPLE_HEADING_IDS,
  SAMPLE_PROJECT_IDS,
  SAMPLE_TAG_IDS,
  SAMPLE_TASK_IDS,
  buildLocalizedSeedBundle
} from './seedData';
import { resolveLanguage } from '../i18n/translations';

const DB_NAME = 'thingsbook_chromeos_db';
const DB_VERSION = 3;
const PREFS_STORAGE_KEY = 'thingsbook_chromeos_prefs_v1';

export interface DataSnapshot {
  areas: AreaRecord[];
  projects: ProjectRecord[];
  headings: HeadingRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
  attachments: AttachmentRecord[];
  projectTemplates: ProjectTemplate[];
  hasSampleTasks: boolean;
}

export function computeStatistics(
  snap: DataSnapshot,
  todayEnd: number = endOfDayEpoch()
): ThingsbookStatistics {
  const activeProjIds = new Set(
    snap.projects.filter((p) => !p.isDeleted && p.status === 'ACTIVE').map((p) => p.id)
  );
  const nonDeletedTasks = snap.tasks.filter((t) => !t.isDeleted);
  const openTasks = nonDeletedTasks.filter((t) => t.status === 'OPEN');
  const openInActiveProj = openTasks.filter((t) => !t.projectId || activeProjIds.has(t.projectId));

  return {
    totalRecordsCount: snap.tasks.length,
    openTasksCount: openTasks.length,
    todayTasksCount: openInActiveProj.filter(
      (t) =>
        t.scheduleState === 'TODAY' ||
        t.scheduleState === 'THIS_EVENING' ||
        (t.whenDate != null && t.whenDate <= todayEnd) ||
        (t.deadline != null && t.deadline <= todayEnd)
    ).length,
    upcomingTasksCount: openInActiveProj.filter(
      (t) =>
        t.scheduleState !== 'TODAY' &&
        t.scheduleState !== 'THIS_EVENING' &&
        (t.whenDate == null || t.whenDate > todayEnd) &&
        (t.deadline == null || t.deadline > todayEnd) &&
        (t.scheduleState === 'UPCOMING' ||
          (t.whenDate != null && t.whenDate > todayEnd) ||
          (t.deadline != null && t.deadline > todayEnd) ||
          t.recurrenceRule != null)
    ).length,
    anytimeTasksCount: openInActiveProj.filter(
      (t) => t.scheduleState === 'ANYTIME' && t.whenDate == null && t.recurrenceRule == null
    ).length,
    inboxTasksCount: openTasks.filter(
      (t) =>
        t.scheduleState === 'INBOX' &&
        t.projectId == null &&
        t.areaId == null &&
        t.whenDate == null
    ).length,
    somedayTasksCount: openInActiveProj.filter((t) => t.scheduleState === 'SOMEDAY').length,
    recurringTasksCount: openTasks.filter((t) => t.recurrenceRule != null).length,
    completedTasksCount: nonDeletedTasks.filter((t) => t.status === 'COMPLETED').length,
    canceledTasksCount: nonDeletedTasks.filter((t) => t.status === 'CANCELED').length,
    deletedTasksCount: snap.tasks.filter((t) => t.isDeleted && !t.purgedFromTrash).length,
    activeProjectsCount: activeProjIds.size,
    areasCount: snap.areas.filter((a) => !a.isDeleted).length,
    tagsCount: snap.tags.filter((tg) => !tg.isDeleted).length
  };
}

const DEFAULT_PREFS: AppPreferences = {
  language: 'system',
  themeMode: 'system',
  sidebarVisible: true,
  localOnlyDbMode: false,
  setupBannerDismissed: false,
  dailyDigestEnabled: false,
  dailyDigestHour: 8,
  dailyDigestMinute: 30,
  autoCheckUpdatesEnabled: true,
  includeBetaUpdates: false,
  lastWeeklyReviewAt: 0,
  geminiApiKey: '',
  calendarEventsEnabled: false,
  selectedCalendarIds: [],
  calendarIdsInitialized: false,
  groupTodayByProjectOrArea: false,
  biometricAppLockEnabled: false,
  appLockPinHash: '',
  appLockWebAuthnCredId: ''
};

export function loadPreferences(): AppPreferences {
  try {
    const raw = localStorage.getItem(PREFS_STORAGE_KEY);
    if (!raw) return { ...DEFAULT_PREFS };
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_PREFS, ...parsed };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function savePreferences(prefs: AppPreferences): void {
  localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(prefs));
}

export type UndoEventKind = 'task_completed' | 'task_deleted' | 'project_deleted';

export interface UndoEventPayload {
  kind: UndoEventKind;
  entityTitle: string;
  count: number;
}

interface InternalUndoSnapshot {
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  projects: ProjectRecord[];
  headings: HeadingRecord[];
  attachments: AttachmentRecord[];
  spawnedTaskIds: string[];
}

type Listener = (snapshot: DataSnapshot) => void;
type UndoListener = (event: UndoEventPayload) => void;

class ThingsLocalDb {
  private dbPromise: Promise<IDBPDatabase> | null = null;
  private listeners = new Set<Listener>();
  private undoListeners = new Set<UndoListener>();
  private lastUndoSnapshot: InternalUndoSnapshot | null = null;
  private syncTriggerCallback: (() => void) | null = null;
  private writeChain: Promise<unknown> = Promise.resolve();
  private cachedSnapshot: DataSnapshot = {
    areas: [],
    projects: [],
    headings: [],
    tasks: [],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: [],
    hasSampleTasks: false
  };

  private enqueueWrite<T>(fn: () => Promise<T>): Promise<T> {
    const next = this.writeChain.then(fn, fn);
    this.writeChain = next.then(
      () => undefined,
      () => undefined
    );
    return next;
  }

  setSyncTrigger(cb: () => void) {
    this.syncTriggerCallback = cb;
  }

  private triggerSync() {
    if (this.syncTriggerCallback) {
      this.syncTriggerCallback();
    }
  }

  private getDb(): Promise<IDBPDatabase> {
    if (!this.dbPromise) {
      this.dbPromise = openDB(DB_NAME, DB_VERSION, {
        upgrade(db) {
          if (!db.objectStoreNames.contains('areas')) db.createObjectStore('areas', { keyPath: 'id' });
          if (!db.objectStoreNames.contains('projects')) db.createObjectStore('projects', { keyPath: 'id' });
          if (!db.objectStoreNames.contains('headings')) db.createObjectStore('headings', { keyPath: 'id' });
          if (!db.objectStoreNames.contains('tasks')) db.createObjectStore('tasks', { keyPath: 'id' });
          if (!db.objectStoreNames.contains('checklist_items')) {
            db.createObjectStore('checklist_items', { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains('tags')) db.createObjectStore('tags', { keyPath: 'id' });
          if (!db.objectStoreNames.contains('attachments')) {
            db.createObjectStore('attachments', { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta');
          if (!db.objectStoreNames.contains('projectTemplates')) {
            const tplStore = db.createObjectStore('projectTemplates', { keyPath: 'id' });
            tplStore.createIndex('updatedAt', 'updatedAt');
            tplStore.createIndex('isDirty', 'isDirty');
            tplStore.createIndex('sortOrder', 'sortOrder');
          }
        }
      });
    }
    return this.dbPromise;
  }

  async initialize(langPref: SupportedLang | 'system' = 'system'): Promise<DataSnapshot> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const seededFlag = await db.get('meta', 'initial_seeded');
      const existingTasksCount = await db.count('tasks');

      if (!seededFlag && existingTasksCount === 0) {
        const lang = resolveLanguage(langPref);
        const seed = buildLocalizedSeedBundle(lang);
        const tx = db.transaction(
          ['tags', 'areas', 'projects', 'headings', 'tasks', 'checklist_items', 'meta'],
          'readwrite'
        );
        for (const item of seed.tags) await tx.objectStore('tags').put(item);
        for (const item of seed.areas) await tx.objectStore('areas').put(item);
        for (const item of seed.projects) await tx.objectStore('projects').put(item);
        for (const item of seed.headings) await tx.objectStore('headings').put(item);
        for (const item of seed.tasks) await tx.objectStore('tasks').put(item);
        for (const item of seed.checklistItems) await tx.objectStore('checklist_items').put(item);
        await tx.objectStore('meta').put(true, 'initial_seeded');
        await tx.done;
      } else {
        // Startup self-heal (matching ensureDefaultWorkAndHomeAreas in Android ThingsRepository):
        // 1) Any OPEN task in ANYTIME that has whenDate != null or recurrenceRule != null is migrated to TODAY or UPCOMING
        // 2) Any OPEN task in INBOX that has whenDate != null is normalized to TODAY or UPCOMING
        const allTasks = (await db.getAll('tasks')) as TaskRecord[];
        const todayEnd = endOfDayEpoch();
        const staleTasks = allTasks.filter(
          (t) =>
            !t.isDeleted &&
            t.status === 'OPEN' &&
            ((t.scheduleState === 'ANYTIME' && (t.whenDate != null || t.recurrenceRule != null)) ||
              (t.scheduleState === 'INBOX' && t.whenDate != null))
        );
        if (staleTasks.length > 0) {
          const tx = db.transaction('tasks', 'readwrite');
          for (const t of staleTasks) {
            const resolvedWhen = t.whenDate ?? startOfDayEpoch();
            const nextState: ScheduleState = resolvedWhen <= todayEnd ? 'TODAY' : 'UPCOMING';
            await tx.store.put({
              ...t,
              scheduleState: nextState,
              whenDate: resolvedWhen
            });
          }
          await tx.done;
        }

        // Initial day rollover on startup (matching ThingsbookViewModel.init -> refreshCurrentDayAndRollover)
        const promotedOnStartup = await this.rolloverDueTasksUnlocked(db);
        if (promotedOnStartup > 0) {
          this.triggerSync();
        }
      }

      return this.refreshSnapshot();
    });
  }

  /**
   * Internal unlocked helper matching Android v1.6.19 TaskDao.rolloverDueUpcomingTasksToToday
   * and TaskDao.rolloverYesterdayEveningTasksToToday.
   */
  private async rolloverDueTasksUnlocked(db: IDBPDatabase): Promise<number> {
    const startOfToday = startOfDayEpoch();
    const endOfToday = endOfDayEpoch();
    const now = Date.now();
    const allTasks = (await db.getAll('tasks')) as TaskRecord[];
    const updates: TaskRecord[] = [];

    for (const t of allTasks) {
      if (t.isDeleted || t.status !== 'OPEN') continue;

      if (t.scheduleState === 'UPCOMING' && t.whenDate != null && t.whenDate <= endOfToday) {
        updates.push({
          ...t,
          scheduleState: 'TODAY',
          updatedAt: now,
          isDirty: true
        });
      } else if (
        t.scheduleState === 'THIS_EVENING' &&
        ((t.whenDate != null && t.whenDate < startOfToday) ||
          (t.whenDate == null && t.updatedAt < startOfToday))
      ) {
        updates.push({
          ...t,
          scheduleState: 'TODAY',
          whenDate: startOfToday,
          updatedAt: now,
          isDirty: true
        });
      }
    }

    if (updates.length > 0) {
      const tx = db.transaction('tasks', 'readwrite');
      for (const updated of updates) {
        await tx.store.put(updated);
      }
      await tx.done;
    }

    return updates.length;
  }

  /**
   * Re-evaluates local day boundaries (00:00 midnight, app resume/focus, or timezone change)
   * and promotes due UPCOMING tasks and yesterday's THIS_EVENING tasks to TODAY (Android v1.6.19 parity).
   */
  async rolloverDueTasksForNewDay(): Promise<boolean> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const promotedCount = await this.rolloverDueTasksUnlocked(db);
      if (promotedCount > 0) {
        await this.refreshSnapshot();
        this.triggerSync();
        return true;
      }
      return false;
    });
  }

  async performMidnightRollover(): Promise<boolean> {
    return this.rolloverDueTasksForNewDay();
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    listener(this.cachedSnapshot);
    return () => {
      this.listeners.delete(listener);
    };
  }

  subscribeUndo(listener: UndoListener): () => void {
    this.undoListeners.add(listener);
    return () => {
      this.undoListeners.delete(listener);
    };
  }

  clearUndoSnapshot(): void {
    this.lastUndoSnapshot = null;
  }

  private emitUndoEvent(payload: UndoEventPayload) {
    for (const listener of this.undoListeners) {
      listener(payload);
    }
  }

  async performUndo(): Promise<boolean> {
    const snap = this.lastUndoSnapshot;
    if (!snap) return false;
    this.lastUndoSnapshot = null;

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items', 'attachments'],
        'readwrite'
      );

      for (const proj of snap.projects) {
        await tx.objectStore('projects').put({
          ...proj,
          purgedFromTrash: false,
          updatedAt: now,
          isDirty: true
        });
      }
      for (const h of snap.headings) {
        await tx.objectStore('headings').put({
          ...h,
          updatedAt: now,
          isDirty: true
        });
      }
      for (const tk of snap.tasks) {
        await tx.objectStore('tasks').put({
          ...tk,
          purgedFromTrash: false,
          updatedAt: now,
          isDirty: true
        });
      }
      for (const chk of snap.checklistItems) {
        await tx.objectStore('checklist_items').put({
          ...chk,
          updatedAt: now,
          isDirty: true
        });
      }
      for (const att of snap.attachments) {
        await tx.objectStore('attachments').put({
          ...att,
          updatedAt: now,
          isDirty: true
        });
      }
      if (snap.spawnedTaskIds.length > 0) {
        const spawnedSet = new Set(snap.spawnedTaskIds);
        const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
        for (const chk of allChk) {
          if (spawnedSet.has(chk.taskId) && !chk.isDeleted) {
            await tx.objectStore('checklist_items').put({
              ...chk,
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
        for (const spawnedId of snap.spawnedTaskIds) {
          const spawned = (await tx.objectStore('tasks').get(spawnedId)) as TaskRecord | undefined;
          if (spawned && !spawned.isDeleted) {
            await tx.objectStore('tasks').put({
              ...spawned,
              isDeleted: true,
              purgedFromTrash: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return true;
    });
  }

  getSnapshot(): DataSnapshot {
    return this.cachedSnapshot;
  }

  async refreshSnapshot(): Promise<DataSnapshot> {
    const db = await this.getDb();
    const [areas, projects, headings, tasks, checklistItems, tags, attachments, projectTemplates] =
      await Promise.all([
        db.getAll('areas') as Promise<AreaRecord[]>,
        db.getAll('projects') as Promise<ProjectRecord[]>,
        db.getAll('headings') as Promise<HeadingRecord[]>,
        db.getAll('tasks') as Promise<TaskRecord[]>,
        db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>,
        db.getAll('tags') as Promise<TagRecord[]>,
        db.getAll('attachments') as Promise<AttachmentRecord[]>,
        db.getAll('projectTemplates') as Promise<ProjectTemplate[]>
      ]);

    const hasSampleTasks = tasks.some((t) => !t.isDeleted && SAMPLE_TASK_IDS.has(t.id));

    this.cachedSnapshot = {
      areas: areas.sort((a, b) => a.sortOrder - b.sortOrder),
      projects: projects.sort((a, b) => a.sortOrder - b.sortOrder),
      headings: headings.sort((a, b) => a.sortOrder - b.sortOrder),
      tasks: tasks.sort((a, b) => a.sortOrder - b.sortOrder || b.createdAt - a.createdAt),
      checklistItems: checklistItems.sort((a, b) => a.sortOrder - b.sortOrder),
      tags: tags.sort((a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title)),
      attachments: attachments.sort((a, b) => a.createdAt - b.createdAt),
      projectTemplates: projectTemplates.sort(
        (a, b) => a.sortOrder - b.sortOrder || b.updatedAt - a.updatedAt
      ),
      hasSampleTasks
    };

    for (const l of this.listeners) {
      l(this.cachedSnapshot);
    }
    return this.cachedSnapshot;
  }

  async getMeta<T>(key: string, fallback: T): Promise<T> {
    const db = await this.getDb();
    const val = await db.get('meta', key);
    return val !== undefined ? (val as T) : fallback;
  }

  async setMeta<T>(key: string, val: T): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      await db.put('meta', val, key);
    });
  }

  // ==================== CRUD: Tasks ====================

  private async upsertTaskUnlocked(params: {
    id?: string;
    title?: string;
    notes?: string;
    status?: TaskStatus;
    scheduleState?: ScheduleState;
    whenDate?: number | null;
    deadline?: number | null;
    reminderTime?: number | null;
    scheduledStartMinutes?: number | null;
    durationMinutes?: number | null;
    projectId?: string | null;
    headingId?: string | null;
    areaId?: string | null;
    recurrenceRule?: RecurrenceRule | null;
    sortOrder?: number;
    tagIds?: string[];
    suppressUndo?: boolean;
  }): Promise<TaskRecord> {
    const db = await this.getDb();
    const now = Date.now();
    const todayStart = startOfDayEpoch(new Date(now));
    const todayEnd = endOfDayEpoch(new Date(now));
    const id = params.id || crypto.randomUUID();

    const tx = db.transaction('tasks', 'readwrite');
    const existing = (await tx.store.get(id)) as TaskRecord | undefined;

    const status = params.status ?? existing?.status ?? 'OPEN';
    const projectId = (params.projectId !== undefined ? params.projectId : existing?.projectId) || null;
    const headingId = projectId
      ? (params.headingId !== undefined ? params.headingId : existing?.headingId) || null
      : null;
    // Container invariant: task inside a project inherits area from project (areaId = null on task row)
    const areaId = projectId
      ? null
      : (params.areaId !== undefined ? params.areaId : existing?.areaId) || null;

    const recurrenceRule =
      params.recurrenceRule !== undefined ? params.recurrenceRule : (existing?.recurrenceRule ?? null);

    let scheduleState = params.scheduleState ?? existing?.scheduleState ?? 'INBOX';
    let whenDate =
      params.whenDate !== undefined
        ? params.whenDate
        : params.scheduleState !== undefined &&
            (params.scheduleState === 'INBOX' ||
              params.scheduleState === 'ANYTIME' ||
              params.scheduleState === 'SOMEDAY')
          ? null
          : (existing?.whenDate ?? null);

    // Normalize INBOX with an explicit whenDate into TODAY or UPCOMING
    if (scheduleState === 'INBOX' && whenDate != null) {
      scheduleState = whenDate <= todayEnd ? 'TODAY' : 'UPCOMING';
    }

    // Moving an INBOX task (with no date or recurrence) into a Project or Area transitions it to ANYTIME
    if (
      scheduleState === 'INBOX' &&
      whenDate == null &&
      recurrenceRule == null &&
      (projectId != null || areaId != null)
    ) {
      scheduleState = 'ANYTIME';
    }

    // Assigning a RecurrenceRule to a task with whenDate == null (Android ThingsRepository.updateTaskRecurrence parity)
    if (recurrenceRule != null && whenDate == null) {
      let initialWhen = todayStart;
      if (
        recurrenceRule.mode === 'FIXED_SCHEDULE' &&
        recurrenceRule.unit === 'WEEKS' &&
        recurrenceRule.daysOfWeek.length > 0
      ) {
        const currentDow = isoDayOfWeek(new Date(now));
        const sortedDays = [...recurrenceRule.daysOfWeek].sort((a, b) => a - b);
        const nextDayInWeek = sortedDays.find((d) => d >= currentDow);
        const daysToAdd =
          nextDayInWeek !== undefined
            ? nextDayInWeek - currentDow
            : 7 - currentDow + sortedDays[0];
        initialWhen = addDaysLocal(todayStart, daysToAdd);
      }
      whenDate = initialWhen;
      if (scheduleState === 'INBOX' || scheduleState === 'ANYTIME') {
        scheduleState = initialWhen <= todayEnd ? 'TODAY' : 'UPCOMING';
      }
    }

    const completedAt =
      status === 'COMPLETED' || status === 'CANCELED'
        ? (existing?.completedAt ?? now)
        : null;

    const resolvedTitle =
      params.title !== undefined ? params.title : (existing?.title ?? '');

    const rawScheduledStart =
      params.scheduledStartMinutes !== undefined
        ? params.scheduledStartMinutes
        : (existing?.scheduledStartMinutes ?? null);
    const scheduledStartMinutes =
      rawScheduledStart != null && Number.isFinite(rawScheduledStart)
        ? Math.max(0, Math.min(1439, Math.trunc(rawScheduledStart)))
        : null;

    const rawDuration =
      params.durationMinutes !== undefined
        ? params.durationMinutes
        : (existing?.durationMinutes ?? null);
    const durationMinutes =
      rawDuration != null && Number.isFinite(rawDuration) && rawDuration > 0
        ? Math.max(5, Math.min(720, Math.trunc(rawDuration)))
        : null;

    const record: TaskRecord = {
      id,
      title: resolvedTitle,
      notes: params.notes !== undefined ? params.notes : (existing?.notes ?? ''),
      status,
      scheduleState,
      whenDate,
      deadline: params.deadline !== undefined ? params.deadline : (existing?.deadline ?? null),
      reminderTime:
        status === 'OPEN'
          ? (params.reminderTime !== undefined ? params.reminderTime : (existing?.reminderTime ?? null))
          : null,
      scheduledStartMinutes,
      durationMinutes,
      completedAt,
      projectId,
      headingId,
      areaId,
      recurrenceRule,
      sortOrder:
        params.sortOrder !== undefined && Number.isFinite(params.sortOrder)
          ? params.sortOrder
          : (existing?.sortOrder ?? 1000),
      tagIds: params.tagIds !== undefined ? params.tagIds : (existing?.tagIds ?? []),
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      isDeleted: false,
      isDirty: true,
      purgedFromTrash: false
    };

    await tx.store.put(record);
    await tx.done;

    // Spawn next recurrence instance if transitioning OPEN -> COMPLETED
    let spawnedTaskId: string | null = null;
    if (
      (!existing || existing.status === 'OPEN') &&
      status === 'COMPLETED' &&
      record.recurrenceRule != null
    ) {
      spawnedTaskId = await this.spawnNextRecurrence(record, now);
    }

    if (
      !params.suppressUndo &&
      existing &&
      !existing.isDeleted &&
      existing.status === 'OPEN' &&
      status === 'COMPLETED'
    ) {
      this.lastUndoSnapshot = {
        tasks: [{ ...existing }],
        checklistItems: [],
        projects: [],
        headings: [],
        attachments: [],
        spawnedTaskIds: spawnedTaskId ? [spawnedTaskId] : []
      };
      this.emitUndoEvent({
        kind: 'task_completed',
        entityTitle: record.title,
        count: 1
      });
    }

    return record;
  }

  async upsertTask(params: {
    id?: string;
    title?: string;
    notes?: string;
    status?: TaskStatus;
    scheduleState?: ScheduleState;
    whenDate?: number | null;
    deadline?: number | null;
    reminderTime?: number | null;
    scheduledStartMinutes?: number | null;
    durationMinutes?: number | null;
    projectId?: string | null;
    headingId?: string | null;
    areaId?: string | null;
    recurrenceRule?: RecurrenceRule | null;
    sortOrder?: number;
    tagIds?: string[];
  }): Promise<TaskRecord> {
    return this.enqueueWrite(async () => {
      const record = await this.upsertTaskUnlocked(params);
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async batchUpdateTasks(
    taskIds: string[],
    patch: {
      status?: TaskStatus;
      scheduleState?: ScheduleState;
      whenDate?: number | null;
      scheduledStartMinutes?: number | null;
      durationMinutes?: number | null;
      projectId?: string | null;
      headingId?: string | null;
      areaId?: string | null;
    }
  ): Promise<void> {
    if (taskIds.length === 0) return;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const priorOpenTasks: TaskRecord[] = [];
      if (patch.status === 'COMPLETED') {
        for (const id of taskIds) {
          const existing = (await db.get('tasks', id)) as TaskRecord | undefined;
          if (existing && !existing.isDeleted && existing.status === 'OPEN') {
            priorOpenTasks.push({ ...existing });
          }
        }
      }

      const prevExistingIds = new Set(this.cachedSnapshot.tasks.map((t) => t.id));
      for (const id of taskIds) {
        await this.upsertTaskUnlocked({
          id,
          ...patch,
          suppressUndo: true
        });
      }

      if (priorOpenTasks.length >= 1) {
        const afterAll = (await db.getAll('tasks')) as TaskRecord[];
        const spawnedIds = afterAll
          .filter((t) => !prevExistingIds.has(t.id) && !taskIds.includes(t.id))
          .map((t) => t.id);
        this.lastUndoSnapshot = {
          tasks: priorOpenTasks,
          checklistItems: [],
          projects: [],
          headings: [],
          attachments: [],
          spawnedTaskIds: spawnedIds
        };
        this.emitUndoEvent({
          kind: 'task_completed',
          entityTitle: priorOpenTasks[0].title,
          count: priorOpenTasks.length
        });
      }

      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  private async spawnNextRecurrence(completedTask: TaskRecord, now: number): Promise<string | null> {
    if (!completedTask.recurrenceRule) return null;
    const nextWhen = calculateNextRecurrenceDate(
      completedTask.recurrenceRule,
      completedTask.whenDate,
      now
    );
    if (nextWhen == null) return null;

    const db = await this.getDb();
    const todayEnd = endOfDayEpoch(new Date(now));
    const nextSchedule: ScheduleState =
      nextWhen <= todayEnd
        ? completedTask.scheduleState === 'THIS_EVENING'
          ? 'THIS_EVENING'
          : 'TODAY'
        : 'UPCOMING';

    let nextDeadline = completedTask.deadline;
    if (completedTask.deadline != null && completedTask.whenDate != null) {
      nextDeadline = completedTask.deadline + (nextWhen - completedTask.whenDate);
    }

    let nextReminder: number | null = null;
    if (completedTask.reminderTime != null) {
      const oldRem = new Date(completedTask.reminderTime);
      const nextDay = new Date(nextWhen);
      nextReminder = new Date(
        nextDay.getFullYear(),
        nextDay.getMonth(),
        nextDay.getDate(),
        oldRem.getHours(),
        oldRem.getMinutes(),
        0,
        0
      ).getTime();
    }

    const newTaskId = crypto.randomUUID();
    const nextTask: TaskRecord = {
      ...completedTask,
      id: newTaskId,
      status: 'OPEN',
      completedAt: null,
      scheduleState: nextSchedule,
      whenDate: nextWhen,
      deadline: nextDeadline,
      reminderTime: nextReminder,
      sortOrder: completedTask.sortOrder + 0.01,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: true
    };
    await db.put('tasks', nextTask);

    const allChecklists = (await db.getAll('checklist_items')) as ChecklistItemRecord[];
    const sourceItems = allChecklists.filter(
      (c) => c.taskId === completedTask.id && !c.isDeleted
    );
    for (const item of sourceItems) {
      await db.put('checklist_items', {
        ...item,
        id: crypto.randomUUID(),
        taskId: newTaskId,
        isCompleted: false,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      });
    }
    return newTaskId;
  }

  async softDeleteTasks(taskIds: string[]): Promise<void> {
    if (taskIds.length === 0) return;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['tasks', 'checklist_items', 'attachments'], 'readwrite');
      const priorTasks: TaskRecord[] = [];
      const priorChecklists: ChecklistItemRecord[] = [];
      const priorAttachments: AttachmentRecord[] = [];

      for (const id of taskIds) {
        const existing = (await tx.objectStore('tasks').get(id)) as TaskRecord | undefined;
        if (existing && !existing.isDeleted) {
          priorTasks.push({ ...existing });
          await tx.objectStore('tasks').put({
            ...existing,
            isDeleted: true,
            purgedFromTrash: false,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const idSet = new Set(taskIds);
      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const chk of allChk) {
        if (idSet.has(chk.taskId) && !chk.isDeleted) {
          priorChecklists.push({ ...chk });
          await tx.objectStore('checklist_items').put({
            ...chk,
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
      for (const att of allAtt) {
        if (att.taskId && idSet.has(att.taskId) && !att.isDeleted) {
          priorAttachments.push({ ...att });
          await tx.objectStore('attachments').put({
            ...att,
            dataBase64: '',
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      await tx.done;

      if (priorTasks.length > 0) {
        this.lastUndoSnapshot = {
          tasks: priorTasks,
          checklistItems: priorChecklists,
          projects: [],
          headings: [],
          attachments: priorAttachments,
          spawnedTaskIds: []
        };
        this.emitUndoEvent({
          kind: 'task_deleted',
          entityTitle: priorTasks[0].title,
          count: priorTasks.length
        });
      }

      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async restoreDeletedTask(taskId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(
        ['tasks', 'checklist_items', 'projects', 'areas', 'headings'],
        'readwrite'
      );
      const existing = (await tx.objectStore('tasks').get(taskId)) as TaskRecord | undefined;
      if (!existing || !existing.isDeleted) {
        await tx.done;
        return;
      }

      const activeProj = existing.projectId
        ? ((await tx.objectStore('projects').get(existing.projectId)) as ProjectRecord | undefined)
        : undefined;
      const validProjId = activeProj && !activeProj.isDeleted ? activeProj.id : null;

      const activeHeading =
        validProjId && existing.headingId
          ? ((await tx.objectStore('headings').get(existing.headingId)) as HeadingRecord | undefined)
          : undefined;
      const validHeadingId =
        activeHeading && !activeHeading.isDeleted && activeHeading.projectId === validProjId
          ? activeHeading.id
          : null;

      const activeArea = existing.areaId
        ? ((await tx.objectStore('areas').get(existing.areaId)) as AreaRecord | undefined)
        : undefined;
      const validAreaId = activeArea && !activeArea.isDeleted ? activeArea.id : null;

      await tx.objectStore('tasks').put({
        ...existing,
        projectId: validProjId,
        headingId: validHeadingId,
        areaId: validProjId ? null : validAreaId,
        isDeleted: false,
        purgedFromTrash: false,
        updatedAt: now,
        isDirty: true
      });

      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const chk of allChk) {
        if (chk.taskId === taskId && chk.isDeleted) {
          await tx.objectStore('checklist_items').put({
            ...chk,
            isDeleted: false,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async restoreDeletedProject(projectId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['projects', 'headings', 'areas', 'tasks'], 'readwrite');
      const existing = (await tx.objectStore('projects').get(projectId)) as
        | ProjectRecord
        | undefined;
      if (!existing || !existing.isDeleted) {
        await tx.done;
        return;
      }

      const activeArea = existing.areaId
        ? ((await tx.objectStore('areas').get(existing.areaId)) as AreaRecord | undefined)
        : undefined;
      const validAreaId = activeArea && !activeArea.isDeleted ? activeArea.id : null;

      const allHeadings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
      const validHeadingIds = new Set<string>();
      for (const h of allHeadings) {
        if (h.projectId === projectId) {
          validHeadingIds.add(h.id);
          if (h.isDeleted) {
            await tx.objectStore('headings').put({
              ...h,
              isDeleted: false,
              updatedAt: now,
              isDirty: true
            });
          }
        }
      }

      const snapshots = existing.detachedTaskSnapshots || [];
      for (const snapTask of snapshots) {
        const curTask = (await tx.objectStore('tasks').get(snapTask.id)) as TaskRecord | undefined;
        if (curTask && !curTask.isDeleted && curTask.projectId == null) {
          const restoredHeadingId =
            snapTask.headingId && validHeadingIds.has(snapTask.headingId)
              ? snapTask.headingId
              : null;
          await tx.objectStore('tasks').put({
            ...curTask,
            projectId,
            headingId: restoredHeadingId,
            areaId: null,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      await tx.objectStore('projects').put({
        ...existing,
        areaId: validAreaId,
        isDeleted: false,
        purgedFromTrash: false,
        detachedTaskSnapshots: undefined,
        updatedAt: now,
        isDirty: true
      });

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async permanentlyDeleteTask(taskId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['tasks', 'checklist_items', 'attachments'], 'readwrite');
      const existing = (await tx.objectStore('tasks').get(taskId)) as TaskRecord | undefined;
      if (existing) {
        await tx.objectStore('tasks').put({
          ...existing,
          title: '',
          notes: '',
          reminderTime: null,
          scheduledStartMinutes: null,
          durationMinutes: null,
          recurrenceRule: null,
          tagIds: [],
          isDeleted: true,
          purgedFromTrash: true,
          updatedAt: now,
          isDirty: true
        });
      }
      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const chk of allChk) {
        if (chk.taskId === taskId) {
          await tx.objectStore('checklist_items').put({
            ...chk,
            title: '',
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
      for (const att of allAtt) {
        if (att.taskId === taskId) {
          await tx.objectStore('attachments').put({
            ...att,
            fileName: '',
            dataBase64: '',
            sizeBytes: 0,
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async permanentlyDeleteProject(projectId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['projects', 'headings', 'tasks', 'attachments'], 'readwrite');
      const allTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of allTasks) {
        if (t.projectId === projectId) {
          await tx.objectStore('tasks').put({
            ...t,
            projectId: null,
            headingId: null,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const allHeadings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
      for (const h of allHeadings) {
        if (h.projectId === projectId) {
          await tx.objectStore('headings').put({
            ...h,
            title: '',
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
      for (const att of allAtt) {
        if (att.projectId === projectId) {
          await tx.objectStore('attachments').put({
            ...att,
            fileName: '',
            dataBase64: '',
            sizeBytes: 0,
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const proj = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      if (proj) {
        await tx.objectStore('projects').put({
          ...proj,
          title: '',
          notes: '',
          lastReviewedAt: null,
          tagIds: [],
          detachedTaskSnapshots: undefined,
          isDeleted: true,
          purgedFromTrash: true,
          updatedAt: now,
          isDirty: true
        });
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async emptyTrash(): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items', 'attachments'],
        'readwrite'
      );

      let anyPurged = false;
      const allTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      const trashTasks = allTasks.filter((t) => t.isDeleted && !t.purgedFromTrash);
      const deletedTaskIds = new Set(trashTasks.map((t) => t.id));

      const allProjects = (await tx.objectStore('projects').getAll()) as ProjectRecord[];
      const trashProjects = allProjects.filter((p) => p.isDeleted && !p.purgedFromTrash);
      const deletedProjIds = new Set(trashProjects.map((p) => p.id));

      if (deletedTaskIds.size > 0) {
        anyPurged = true;
        const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
        for (const chk of allChk) {
          if (deletedTaskIds.has(chk.taskId)) {
            await tx.objectStore('checklist_items').put({
              ...chk,
              title: '',
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
        for (const t of trashTasks) {
          await tx.objectStore('tasks').put({
            ...t,
            title: '',
            notes: '',
            reminderTime: null,
            scheduledStartMinutes: null,
            durationMinutes: null,
            recurrenceRule: null,
            tagIds: [],
            isDeleted: true,
            purgedFromTrash: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      if (deletedProjIds.size > 0) {
        anyPurged = true;
        const latestTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
        for (const t of latestTasks) {
          if (t.projectId && deletedProjIds.has(t.projectId)) {
            await tx.objectStore('tasks').put({
              ...t,
              projectId: null,
              headingId: null,
              updatedAt: now,
              isDirty: true
            });
          }
        }
        const allHeadings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
        for (const h of allHeadings) {
          if (deletedProjIds.has(h.projectId)) {
            await tx.objectStore('headings').put({
              ...h,
              title: '',
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
        for (const p of trashProjects) {
          await tx.objectStore('projects').put({
            ...p,
            title: '',
            notes: '',
            tagIds: [],
            detachedTaskSnapshots: undefined,
            isDeleted: true,
            purgedFromTrash: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      if (deletedTaskIds.size > 0 || deletedProjIds.size > 0) {
        const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
        for (const att of allAtt) {
          const matchesTask = Boolean(att.taskId && deletedTaskIds.has(att.taskId));
          const matchesProj = Boolean(att.projectId && deletedProjIds.has(att.projectId));
          if (matchesTask || matchesProj) {
            await tx.objectStore('attachments').put({
              ...att,
              fileName: '',
              dataBase64: '',
              sizeBytes: 0,
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      if (anyPurged) {
        this.triggerSync();
      }
    });
  }

  async duplicateTask(taskId: string, copySuffix = 'Copy'): Promise<string | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const existing = (await db.get('tasks', taskId)) as TaskRecord | undefined;
      if (!existing || existing.isDeleted) return null;
      const now = Date.now();
      const newId = crypto.randomUUID();
      const trimmed = existing.title.trim();
      const newTitle = trimmed ? `${trimmed} (${copySuffix})` : existing.title;
      await db.put('tasks', {
        ...existing,
        id: newId,
        title: newTitle,
        status: 'OPEN',
        completedAt: null,
        reminderTime:
          existing.reminderTime != null && existing.reminderTime > now
            ? existing.reminderTime
            : null,
        sortOrder: existing.sortOrder + 10.0,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      });
      const allChk = (await db.getAll('checklist_items')) as ChecklistItemRecord[];
      for (const c of allChk.filter((x) => x.taskId === taskId && !x.isDeleted)) {
        await db.put('checklist_items', {
          ...c,
          id: crypto.randomUUID(),
          taskId: newId,
          isCompleted: false,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        });
      }
      await this.refreshSnapshot();
      this.triggerSync();
      return newId;
    });
  }

  // ==================== CRUD: Checklist Items ====================

  async addChecklistItems(taskId: string, titles: string[]): Promise<void> {
    const clean = titles.map((t) => t.trim()).filter(Boolean);
    if (clean.length === 0) return;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('checklist_items', 'readwrite');
      const existing = ((await tx.store.getAll()) as ChecklistItemRecord[])
        .filter((c) => c.taskId === taskId && !c.isDeleted)
        .sort((a, b) => a.sortOrder - b.sortOrder);
      let baseOrder = existing.length > 0 ? existing[existing.length - 1].sortOrder : 0;

      for (const title of clean) {
        baseOrder += 100;
        await tx.store.put({
          id: crypto.randomUUID(),
          taskId,
          title,
          isCompleted: false,
          sortOrder: baseOrder,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        } satisfies ChecklistItemRecord);
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async insertChecklistItemAfter(
    taskId: string,
    afterItemId: string,
    newItemTitle: string,
    updatedCurrentTitle?: string
  ): Promise<string> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('checklist_items', 'readwrite');
      const items = ((await tx.store.getAll()) as ChecklistItemRecord[])
        .filter((c) => c.taskId === taskId && !c.isDeleted)
        .sort((a, b) => a.sortOrder - b.sortOrder);

      const idx = items.findIndex((c) => c.id === afterItemId);
      const prevOrder = idx >= 0 ? items[idx].sortOrder : (items[items.length - 1]?.sortOrder ?? 0);
      const nextOrder = idx >= 0 && idx + 1 < items.length ? items[idx + 1].sortOrder : null;
      const sortOrder = computeMidpointSortOrder(prevOrder, nextOrder);
      const newId = crypto.randomUUID();

      if (idx >= 0 && updatedCurrentTitle !== undefined) {
        await tx.store.put({
          ...items[idx],
          title: updatedCurrentTitle,
          updatedAt: now,
          isDirty: true
        });
      }
      await tx.store.put({
        id: newId,
        taskId,
        title: newItemTitle,
        isCompleted: false,
        sortOrder,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies ChecklistItemRecord);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return newId;
    });
  }

  async swapChecklistOrder(itemIdA: string, itemIdB: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('checklist_items', 'readwrite');
      const a = (await tx.store.get(itemIdA)) as ChecklistItemRecord | undefined;
      const b = (await tx.store.get(itemIdB)) as ChecklistItemRecord | undefined;
      if (!a || !b) {
        await tx.done;
        return;
      }
      const orderA = a.sortOrder;
      const orderB = b.sortOrder === orderA ? orderA + 1 : b.sortOrder;
      await tx.store.put({ ...a, sortOrder: orderB, updatedAt: now, isDirty: true });
      await tx.store.put({ ...b, sortOrder: orderA, updatedAt: now, isDirty: true });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async updateChecklistItem(
    itemId: string,
    patch: Partial<Pick<ChecklistItemRecord, 'title' | 'isCompleted' | 'sortOrder'>>
  ): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction('checklist_items', 'readwrite');
      const existing = (await tx.store.get(itemId)) as ChecklistItemRecord | undefined;
      if (!existing) {
        await tx.done;
        return;
      }
      await tx.store.put({
        ...existing,
        ...patch,
        updatedAt: Date.now(),
        isDirty: true
      });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async deleteChecklistItem(itemId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction('checklist_items', 'readwrite');
      const existing = (await tx.store.get(itemId)) as ChecklistItemRecord | undefined;
      if (!existing) {
        await tx.done;
        return;
      }
      await tx.store.put({
        ...existing,
        isDeleted: true,
        updatedAt: Date.now(),
        isDirty: true
      });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  // ==================== CRUD: Projects, Headings, Areas, Tags ====================

  async upsertProject(params: {
    id?: string;
    title?: string;
    notes?: string;
    areaId?: string | null;
    status?: ProjectStatus;
    scheduleState?: ScheduleState;
    whenDate?: number | null;
    deadline?: number | null;
    lastReviewedAt?: number | null;
    tagIds?: string[];
    sortOrder?: number;
  }): Promise<ProjectRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('projects', 'readwrite');
      const existing = (await tx.store.get(id)) as ProjectRecord | undefined;
      const status = params.status ?? existing?.status ?? 'ACTIVE';
      const lastReviewedAt =
        params.lastReviewedAt !== undefined
          ? params.lastReviewedAt
          : (existing?.lastReviewedAt ?? null);
      const record: ProjectRecord = {
        id,
        areaId: params.areaId !== undefined ? params.areaId : (existing?.areaId ?? null),
        title: params.title !== undefined ? params.title : (existing?.title ?? ''),
        notes: params.notes !== undefined ? params.notes : (existing?.notes ?? ''),
        status,
        scheduleState: params.scheduleState ?? existing?.scheduleState ?? 'ANYTIME',
        whenDate: params.whenDate !== undefined ? params.whenDate : (existing?.whenDate ?? null),
        deadline: params.deadline !== undefined ? params.deadline : (existing?.deadline ?? null),
        completedAt: status === 'COMPLETED' || status === 'CANCELED' ? (existing?.completedAt ?? now) : null,
        lastReviewedAt,
        sortOrder:
          params.sortOrder !== undefined && Number.isFinite(params.sortOrder)
            ? params.sortOrder
            : (existing?.sortOrder ?? now),
        tagIds: params.tagIds !== undefined ? params.tagIds : (existing?.tagIds ?? []),
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async markProjectReviewed(projectId: string, reviewedAt: number = Date.now()): Promise<void> {
    await this.upsertProject({ id: projectId, lastReviewedAt: reviewedAt });
  }

  async saveProjectAsTemplate(
    projectId: string,
    customTitle?: string
  ): Promise<ProjectTemplate | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const project = (await db.get('projects', projectId)) as ProjectRecord | undefined;
      if (!project || project.isDeleted) return null;

      const [headings, tasks, checklistItems] = await Promise.all([
        db.getAll('headings') as Promise<HeadingRecord[]>,
        db.getAll('tasks') as Promise<TaskRecord[]>,
        db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>
      ]);

      const now = Date.now();
      const baseDay =
        project.whenDate != null ? startOfDayEpoch(new Date(project.whenDate)) : startOfDayEpoch();
      const blueprint = buildProjectTemplateBlueprint(
        project,
        headings,
        tasks,
        checklistItems,
        baseDay
      );

      const template: ProjectTemplate = {
        id: crypto.randomUUID(),
        title: (customTitle?.trim() || project.title.trim()) || 'Project Template',
        notes: project.notes || '',
        defaultAreaId: project.areaId ?? null,
        tagIds: [...(project.tagIds || [])],
        blueprintJson: JSON.stringify(blueprint),
        sortOrder: now,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };

      const tx = db.transaction('projectTemplates', 'readwrite');
      await tx.store.put(template);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return template;
    });
  }

  async upsertProjectTemplate(params: {
    id?: string;
    title: string;
    notes?: string;
    defaultAreaId?: string | null;
    tagIds?: string[];
    blueprintJson: string;
    sortOrder?: number;
  }): Promise<ProjectTemplate> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('projectTemplates', 'readwrite');
      const existing = (await tx.store.get(id)) as ProjectTemplate | undefined;
      const record: ProjectTemplate = {
        id,
        title: params.title.trim() || 'Project Template',
        notes: params.notes !== undefined ? params.notes : (existing?.notes ?? ''),
        defaultAreaId:
          params.defaultAreaId !== undefined
            ? params.defaultAreaId
            : (existing?.defaultAreaId ?? null),
        tagIds: params.tagIds !== undefined ? params.tagIds : (existing?.tagIds ?? []),
        blueprintJson: params.blueprintJson || existing?.blueprintJson || '{}',
        sortOrder: params.sortOrder ?? existing?.sortOrder ?? now,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async deleteProjectTemplate(templateId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('projectTemplates', 'readwrite');
      const existing = (await tx.store.get(templateId)) as ProjectTemplate | undefined;
      if (existing && !existing.isDeleted) {
        await tx.store.put({
          ...existing,
          isDeleted: true,
          updatedAt: now,
          isDirty: true
        });
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async instantiateProjectFromBlueprint(params: {
    title: string;
    notes?: string;
    areaId?: string | null;
    tagIds?: string[];
    blueprint: ProjectTemplateBlueprint;
  }): Promise<string> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEpoch = startOfDayEpoch(new Date(now));
      const projectId = crypto.randomUUID();

      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items'],
        'readwrite'
      );

      await tx.objectStore('projects').put({
        id: projectId,
        areaId: params.areaId ?? null,
        title: params.title.trim() || 'New Project',
        notes: params.notes ?? params.blueprint.notes ?? '',
        status: 'ACTIVE',
        scheduleState: 'TODAY',
        whenDate: todayEpoch,
        deadline: null,
        completedAt: null,
        lastReviewedAt: now,
        sortOrder: now,
        tagIds: params.tagIds ?? [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies ProjectRecord);

      let createdAnyTask = false;
      const writeTask = async (
        bpTask: ProjectTemplateTaskBlueprint,
        headingId: string | null,
        sortOrder: number
      ) => {
        const title = bpTask.title.trim();
        if (!title) return;
        const isFirstTask = !createdAnyTask;
        createdAnyTask = true;

        let whenDate: number | null = null;
        let scheduleState: ScheduleState = bpTask.scheduleState ?? 'ANYTIME';

        if (bpTask.whenDaysOffset != null && Number.isFinite(bpTask.whenDaysOffset)) {
          const offset = Math.max(0, Math.trunc(bpTask.whenDaysOffset));
          whenDate = addDaysLocal(todayEpoch, offset);
          scheduleState = offset === 0 ? (scheduleState === 'THIS_EVENING' ? 'THIS_EVENING' : 'TODAY') : 'UPCOMING';
        } else if (scheduleState === 'TODAY' || scheduleState === 'THIS_EVENING') {
          whenDate = todayEpoch;
        } else if (isFirstTask && bpTask.scheduleState === undefined) {
          scheduleState = 'TODAY';
          whenDate = todayEpoch;
        }

        const deadline =
          bpTask.deadlineDaysOffset != null && Number.isFinite(bpTask.deadlineDaysOffset)
            ? addDaysLocal(todayEpoch, Math.max(0, Math.trunc(bpTask.deadlineDaysOffset)))
            : null;

        const taskId = crypto.randomUUID();
        await tx.objectStore('tasks').put({
          id: taskId,
          title,
          notes: bpTask.notes ?? '',
          status: 'OPEN',
          scheduleState,
          whenDate,
          deadline,
          reminderTime: null,
          scheduledStartMinutes: bpTask.scheduledStartMinutes ?? null,
          durationMinutes: bpTask.durationMinutes ?? null,
          completedAt: null,
          projectId,
          headingId,
          areaId: null,
          recurrenceRule: null,
          sortOrder,
          tagIds: [],
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true,
          purgedFromTrash: false
        } satisfies TaskRecord);

        const subtasks = bpTask.checklistItems ?? [];
        for (let cIdx = 0; cIdx < subtasks.length; cIdx++) {
          const subTitle = subtasks[cIdx].trim();
          if (!subTitle) continue;
          await tx.objectStore('checklist_items').put({
            id: crypto.randomUUID(),
            taskId,
            title: subTitle,
            isCompleted: false,
            sortOrder: (cIdx + 1) * 100,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }
      };

      const topTasks = params.blueprint.tasks ?? [];
      for (let tIdx = 0; tIdx < topTasks.length; tIdx++) {
        await writeTask(topTasks[tIdx], null, (tIdx + 1) * 100);
      }

      for (let hIdx = 0; hIdx < params.blueprint.headings.length; hIdx++) {
        const h = params.blueprint.headings[hIdx];
        const headingId = crypto.randomUUID();
        await tx.objectStore('headings').put({
          id: headingId,
          projectId,
          title: h.title.trim() || `Section ${hIdx + 1}`,
          sortOrder: (hIdx + 1) * 1000,
          isArchived: false,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        } satisfies HeadingRecord);

        for (let tIdx = 0; tIdx < h.tasks.length; tIdx++) {
          await writeTask(h.tasks[tIdx], headingId, (hIdx + 1) * 1000 + (tIdx + 1) * 100);
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return projectId;
    });
  }

  async moveProjectOrder(
    projectId: string,
    direction: 'up' | 'down',
    withinAreaId?: string | null
  ): Promise<void> {
    return this.enqueueWrite(async () => {
      const list = this.cachedSnapshot.projects.filter(
        (p) =>
          !p.isDeleted &&
          p.status === 'ACTIVE' &&
          (withinAreaId === undefined ? true : p.areaId === withinAreaId)
      );
      const idx = list.findIndex((p) => p.id === projectId);
      const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
      if (idx < 0 || swapIdx < 0 || swapIdx >= list.length) return;
      const a = list[idx];
      const b = list[swapIdx];
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('projects', 'readwrite');
      const orderA = a.sortOrder;
      const orderB = b.sortOrder === orderA ? (direction === 'up' ? orderA - 1 : orderA + 1) : b.sortOrder;
      await tx.store.put({ ...a, sortOrder: orderB, updatedAt: now, isDirty: true });
      await tx.store.put({ ...b, sortOrder: orderA, updatedAt: now, isDirty: true });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async createProjectWithBlueprint(params: {
    title: string;
    notes: string;
    areaId: string | null;
    headings: Array<{ title: string; tasks: string[] }>;
  }): Promise<string> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEpoch = startOfDayEpoch(new Date(now));
      const projectId = crypto.randomUUID();

      const tx = db.transaction(['projects', 'headings', 'tasks'], 'readwrite');
      await tx.objectStore('projects').put({
        id: projectId,
        areaId: params.areaId,
        title: params.title.trim() || 'New Project',
        notes: params.notes || '',
        status: 'ACTIVE',
        scheduleState: 'TODAY',
        whenDate: todayEpoch,
        deadline: null,
        completedAt: null,
        sortOrder: now,
        tagIds: [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies ProjectRecord);

      for (let hIdx = 0; hIdx < params.headings.length; hIdx++) {
        const h = params.headings[hIdx];
        const headingId = crypto.randomUUID();
        await tx.objectStore('headings').put({
          id: headingId,
          projectId,
          title: h.title.trim() || `Section ${hIdx + 1}`,
          sortOrder: (hIdx + 1) * 1000,
          isArchived: false,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        } satisfies HeadingRecord);

        for (let tIdx = 0; tIdx < h.tasks.length; tIdx++) {
          const taskTitle = h.tasks[tIdx].trim();
          if (!taskTitle) continue;
          const isFirst = hIdx === 0 && tIdx === 0;
          await tx.objectStore('tasks').put({
            id: crypto.randomUUID(),
            title: taskTitle,
            notes: '',
            status: 'OPEN',
            scheduleState: isFirst ? 'TODAY' : 'ANYTIME',
            whenDate: isFirst ? todayEpoch : null,
            deadline: null,
            reminderTime: null,
            completedAt: null,
            projectId,
            headingId,
            areaId: null,
            recurrenceRule: null,
            sortOrder: (hIdx + 1) * 1000 + (tIdx + 1) * 100,
            tagIds: [],
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies TaskRecord);
        }
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return projectId;
    });
  }

  /**
   * Matches Android TaskDao.clearProjectFromTasks: soft-deletes project & headings,
   * while clearing projectId & headingId on its tasks and preserving parent areaId.
   */
  async deleteProject(projectId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['projects', 'headings', 'tasks', 'attachments'], 'readwrite');
      const proj = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      const prevProjects: ProjectRecord[] = [];
      const prevHeadings: HeadingRecord[] = [];
      const prevTasks: TaskRecord[] = [];
      const prevAttachments: AttachmentRecord[] = [];
      const detachedTaskSnapshots: DetachedProjectTaskSnapshot[] = [];

      const headings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
      for (const h of headings) {
        if (h.projectId === projectId && !h.isDeleted) {
          prevHeadings.push({ ...h });
          await tx.objectStore('headings').put({
            ...h,
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of tasks) {
        if (t.projectId === projectId && !t.isDeleted) {
          prevTasks.push({ ...t });
          detachedTaskSnapshots.push({
            id: t.id,
            projectId: t.projectId,
            headingId: t.headingId,
            areaId: t.areaId
          });
          await tx.objectStore('tasks').put({
            ...t,
            projectId: null,
            headingId: null,
            areaId: proj?.areaId ?? t.areaId,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
      for (const att of allAtt) {
        if (att.projectId === projectId && !att.isDeleted) {
          prevAttachments.push({ ...att });
          await tx.objectStore('attachments').put({
            ...att,
            dataBase64: '',
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }

      if (proj && !proj.isDeleted) {
        prevProjects.push({ ...proj });
        await tx.objectStore('projects').put({
          ...proj,
          isDeleted: true,
          purgedFromTrash: false,
          detachedTaskSnapshots,
          updatedAt: now,
          isDirty: true
        });
      }

      await tx.done;

      if (prevProjects.length > 0) {
        this.lastUndoSnapshot = {
          tasks: prevTasks,
          checklistItems: [],
          projects: prevProjects,
          headings: prevHeadings,
          attachments: prevAttachments,
          spawnedTaskIds: []
        };
        this.emitUndoEvent({
          kind: 'project_deleted',
          entityTitle: prevProjects[0].title,
          count: 1
        });
      }

      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  /**
   * Duplicates a Project along with its non-deleted Headings, OPEN To-Dos, and their Checklist Items
   * (matching Android v1.7.0 ThingsRepository.duplicateProject).
   */
  async duplicateProject(projectId: string, copySuffix = 'Copy'): Promise<string | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['projects', 'headings', 'tasks', 'checklist_items'], 'readwrite');
      const source = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      if (!source || source.isDeleted) {
        await tx.done;
        return null;
      }

      const newProjectId = crypto.randomUUID();
      const baseTitle = source.title.trim();
      const newTitle = baseTitle ? `${baseTitle} (${copySuffix})` : copySuffix;

      const clonedProject: ProjectRecord = {
        ...source,
        id: newProjectId,
        title: newTitle,
        status: 'ACTIVE',
        completedAt: null,
        sortOrder: source.sortOrder + 1,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.objectStore('projects').put(clonedProject);

      const allHeadings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
      const headingIdMap = new Map<string, string>();
      for (const h of allHeadings) {
        if (h.projectId === projectId && !h.isDeleted) {
          const newHeadingId = crypto.randomUUID();
          headingIdMap.set(h.id, newHeadingId);
          await tx.objectStore('headings').put({
            ...h,
            id: newHeadingId,
            projectId: newProjectId,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies HeadingRecord);
        }
      }

      const allTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      const allChecklist = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const t of allTasks) {
        if (t.projectId === projectId && !t.isDeleted && t.status === 'OPEN') {
          const newTaskId = crypto.randomUUID();
          const mappedHeadingId = t.headingId ? (headingIdMap.get(t.headingId) ?? null) : null;
          await tx.objectStore('tasks').put({
            ...t,
            id: newTaskId,
            projectId: newProjectId,
            headingId: mappedHeadingId,
            status: 'OPEN',
            completedAt: null,
            reminderTime: null,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies TaskRecord);

          const taskChecklist = allChecklist
            .filter((ci) => ci.taskId === t.id && !ci.isDeleted)
            .sort((a, b) => a.sortOrder - b.sortOrder);
          for (const ci of taskChecklist) {
            await tx.objectStore('checklist_items').put({
              ...ci,
              id: crypto.randomUUID(),
              taskId: newTaskId,
              isCompleted: false,
              createdAt: now,
              updatedAt: now,
              isDeleted: false,
              isDirty: true
            } satisfies ChecklistItemRecord);
          }
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return newProjectId;
    });
  }

  async upsertHeading(params: {
    id?: string;
    projectId: string;
    title: string;
    sortOrder?: number;
  }): Promise<HeadingRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(id)) as HeadingRecord | undefined;
      const record: HeadingRecord = {
        id,
        projectId: params.projectId,
        title: params.title,
        sortOrder: params.sortOrder ?? existing?.sortOrder ?? now,
        isArchived: existing?.isArchived ?? false,
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async deleteHeading(headingId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['headings', 'tasks'], 'readwrite');
      const h = (await tx.objectStore('headings').get(headingId)) as HeadingRecord | undefined;
      if (h) {
        await tx.objectStore('headings').put({ ...h, isDeleted: true, updatedAt: now, isDirty: true });
      }
      const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of tasks) {
        if (t.headingId === headingId && !t.isDeleted) {
          await tx.objectStore('tasks').put({ ...t, headingId: null, updatedAt: now, isDirty: true });
        }
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async upsertArea(params: { id?: string; title: string; sortOrder?: number; tagIds?: string[] }): Promise<AreaRecord | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('areas', 'readwrite');
      const existing = (await tx.store.get(id)) as AreaRecord | undefined;
      if (!existing) {
        const activeCount = ((await tx.store.getAll()) as AreaRecord[]).filter((a) => !a.isDeleted).length;
        if (activeCount >= MAX_AREAS) {
          await tx.done;
          return null;
        }
      }
      const record: AreaRecord = {
        id,
        title: params.title.trim(),
        sortOrder: params.sortOrder ?? existing?.sortOrder ?? (now % 100000),
        tagIds: params.tagIds ?? existing?.tagIds ?? [],
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async moveAreaOrder(areaId: string, direction: 'up' | 'down'): Promise<void> {
    return this.enqueueWrite(async () => {
      const active = this.cachedSnapshot.areas.filter((a) => !a.isDeleted);
      const idx = active.findIndex((a) => a.id === areaId);
      const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
      if (idx < 0 || swapIdx < 0 || swapIdx >= active.length) return;
      const a = active[idx];
      const b = active[swapIdx];
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('areas', 'readwrite');
      const orderA = a.sortOrder;
      const orderB = b.sortOrder === orderA ? (direction === 'up' ? orderA - 1 : orderA + 1) : b.sortOrder;
      await tx.store.put({ ...a, sortOrder: orderB, updatedAt: now, isDirty: true });
      await tx.store.put({ ...b, sortOrder: orderA, updatedAt: now, isDirty: true });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async deleteArea(areaId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['areas', 'projects', 'tasks', 'projectTemplates'], 'readwrite');
      const area = (await tx.objectStore('areas').get(areaId)) as AreaRecord | undefined;
      if (area) {
        await tx.objectStore('areas').put({ ...area, isDeleted: true, updatedAt: now, isDirty: true });
      }
      const projects = (await tx.objectStore('projects').getAll()) as ProjectRecord[];
      for (const p of projects) {
        if (p.areaId === areaId && !p.isDeleted) {
          await tx.objectStore('projects').put({ ...p, areaId: null, updatedAt: now, isDirty: true });
        }
      }
      const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of tasks) {
        if (t.areaId === areaId && !t.isDeleted) {
          await tx.objectStore('tasks').put({ ...t, areaId: null, updatedAt: now, isDirty: true });
        }
      }
      const templates = (await tx.objectStore('projectTemplates').getAll()) as ProjectTemplate[];
      for (const tpl of templates) {
        if (tpl.defaultAreaId === areaId && !tpl.isDeleted) {
          await tx
            .objectStore('projectTemplates')
            .put({ ...tpl, defaultAreaId: null, updatedAt: now, isDirty: true });
        }
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async upsertTag(params: {
    id?: string;
    title: string;
    shortcut?: string | null;
    parentTagId?: string | null;
  }): Promise<TagRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('tags', 'readwrite');
      const existing = (await tx.store.get(id)) as TagRecord | undefined;
      const record: TagRecord = {
        id,
        title: params.title.trim(),
        shortcut: params.shortcut !== undefined ? params.shortcut : (existing?.shortcut ?? null),
        parentTagId: params.parentTagId !== undefined ? params.parentTagId : (existing?.parentTagId ?? null),
        sortOrder: existing?.sortOrder ?? (now % 100000),
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async deleteTag(tagId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(
        ['tags', 'tasks', 'projects', 'areas', 'projectTemplates'],
        'readwrite'
      );
      const tag = (await tx.objectStore('tags').get(tagId)) as TagRecord | undefined;
      if (tag) {
        await tx.objectStore('tags').put({ ...tag, isDeleted: true, updatedAt: now, isDirty: true });
      }
      const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of tasks) {
        if (!t.isDeleted && t.tagIds.includes(tagId)) {
          await tx.objectStore('tasks').put({
            ...t,
            tagIds: t.tagIds.filter((id) => id !== tagId),
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const projects = (await tx.objectStore('projects').getAll()) as ProjectRecord[];
      for (const p of projects) {
        if (!p.isDeleted && p.tagIds.includes(tagId)) {
          await tx.objectStore('projects').put({
            ...p,
            tagIds: p.tagIds.filter((id) => id !== tagId),
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const areas = (await tx.objectStore('areas').getAll()) as AreaRecord[];
      for (const a of areas) {
        if (!a.isDeleted && a.tagIds.includes(tagId)) {
          await tx.objectStore('areas').put({
            ...a,
            tagIds: a.tagIds.filter((id) => id !== tagId),
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const templates = (await tx.objectStore('projectTemplates').getAll()) as ProjectTemplate[];
      for (const tpl of templates) {
        if (!tpl.isDeleted && tpl.tagIds.includes(tagId)) {
          await tx.objectStore('projectTemplates').put({
            ...tpl,
            tagIds: tpl.tagIds.filter((id) => id !== tagId),
            updatedAt: now,
            isDirty: true
          });
        }
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async addAttachment(params: {
    taskId: string | null;
    projectId: string | null;
    fileName: string;
    mimeType: string;
    sizeBytes: number;
    dataBase64: string;
    sortOrder?: number;
  }): Promise<AttachmentRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const record: AttachmentRecord = {
        id: crypto.randomUUID(),
        taskId: params.taskId,
        projectId: params.projectId,
        fileName: params.fileName.trim() || 'attachment',
        mimeType: params.mimeType || 'application/octet-stream',
        sizeBytes: params.sizeBytes,
        dataBase64: params.dataBase64,
        sortOrder: params.sortOrder ?? now,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      const tx = db.transaction('attachments', 'readwrite');
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async deleteAttachment(attachmentId: string): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('attachments', 'readwrite');
      const existing = (await tx.store.get(attachmentId)) as AttachmentRecord | undefined;
      if (existing) {
        await tx.store.put({
          ...existing,
          dataBase64: '',
          isDeleted: true,
          updatedAt: now,
          isDirty: true
        });
      }
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  // ==================== Sample Data Cleanup & Cloud Merge ====================

  countNonSampleTasks(): number {
    return this.cachedSnapshot.tasks.filter(
      (t) => !t.isDeleted && !SAMPLE_TASK_IDS.has(t.id)
    ).length;
  }

  async purgeSampleDataLocally(markRemainingDirtyForMerge = false): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction(
        [
          'tasks',
          'checklist_items',
          'headings',
          'projects',
          'tags',
          'areas',
          'attachments',
          'projectTemplates'
        ],
        'readwrite'
      );

      for (const id of SAMPLE_TASK_IDS) {
        await tx.objectStore('tasks').delete(id);
      }
      for (const id of SAMPLE_CHECKLIST_IDS) {
        await tx.objectStore('checklist_items').delete(id);
      }
      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const c of allChk) {
        if (SAMPLE_TASK_IDS.has(c.taskId)) {
          await tx.objectStore('checklist_items').delete(c.id);
        }
      }

      const remainingTasks = ((await tx.objectStore('tasks').getAll()) as TaskRecord[]).filter(
        (t) => !t.isDeleted
      );

      for (const hId of SAMPLE_HEADING_IDS) {
        if (!remainingTasks.some((t) => t.headingId === hId)) {
          await tx.objectStore('headings').delete(hId);
        }
      }
      for (const pId of SAMPLE_PROJECT_IDS) {
        if (!remainingTasks.some((t) => t.projectId === pId)) {
          await tx.objectStore('projects').delete(pId);
        }
      }
      const remainingProjects = ((await tx.objectStore('projects').getAll()) as ProjectRecord[]).filter(
        (p) => !p.isDeleted
      );
      for (const tagId of SAMPLE_TAG_IDS) {
        const usedByTask = remainingTasks.some((t) => t.tagIds.includes(tagId));
        const usedByProj = remainingProjects.some((p) => p.tagIds.includes(tagId));
        if (!usedByTask && !usedByProj) {
          await tx.objectStore('tags').delete(tagId);
        }
      }

      if (markRemainingDirtyForMerge) {
        const stores = [
          'areas',
          'projects',
          'headings',
          'tasks',
          'checklist_items',
          'tags',
          'attachments',
          'projectTemplates'
        ] as const;
        for (const sName of stores) {
          const all = await tx.objectStore(sName).getAll();
          for (const rec of all) {
            if (!rec.isDeleted) {
              await tx.objectStore(sName).put({ ...rec, isDirty: true });
            }
          }
        }
      }

      await tx.done;
      await this.refreshSnapshot();
    });
  }

  async purgeAllLocalDataBeforeAccountPull(): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction(
        [
          'areas',
          'projects',
          'headings',
          'tasks',
          'checklist_items',
          'tags',
          'attachments',
          'projectTemplates'
        ],
        'readwrite'
      );
      await tx.objectStore('attachments').clear();
      await tx.objectStore('checklist_items').clear();
      await tx.objectStore('tasks').clear();
      await tx.objectStore('headings').clear();
      await tx.objectStore('projects').clear();
      await tx.objectStore('areas').clear();
      await tx.objectStore('tags').clear();
      await tx.objectStore('projectTemplates').clear();
      await tx.done;
      await this.refreshSnapshot();
    });
  }

  // ==================== Cloud Sync Helpers ====================

  async getDirtyEntities() {
    const db = await this.getDb();
    const [
      areas,
      projects,
      headings,
      tasks,
      checklistItems,
      tags,
      attachments,
      projectTemplates
    ] = await Promise.all([
      db.getAll('areas') as Promise<AreaRecord[]>,
      db.getAll('projects') as Promise<ProjectRecord[]>,
      db.getAll('headings') as Promise<HeadingRecord[]>,
      db.getAll('tasks') as Promise<TaskRecord[]>,
      db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>,
      db.getAll('tags') as Promise<TagRecord[]>,
      db.getAll('attachments') as Promise<AttachmentRecord[]>,
      db.getAll('projectTemplates') as Promise<ProjectTemplate[]>
    ]);
    return {
      areas: areas.filter((x) => x.isDirty),
      projects: projects.filter((x) => x.isDirty),
      headings: headings.filter((x) => x.isDirty),
      tasks: tasks.filter((x) => x.isDirty),
      checklistItems: checklistItems.filter((x) => x.isDirty),
      tags: tags.filter((x) => x.isDirty),
      attachments: attachments.filter((x) => x.isDirty),
      projectTemplates: projectTemplates.filter((x) => x.isDirty)
    };
  }

  async markSynced(idsByStore: {
    areas?: { ids: string[]; maxUpdatedAt?: number };
    projects?: { ids: string[]; maxUpdatedAt?: number };
    headings?: { ids: string[]; maxUpdatedAt?: number };
    tasks?: { ids: string[]; maxUpdatedAt?: number };
    checklistItems?: { ids: string[]; maxUpdatedAt?: number };
    tags?: { ids: string[]; maxUpdatedAt?: number };
    attachments?: { ids: string[]; maxUpdatedAt?: number };
    projectTemplates?: { ids: string[]; maxUpdatedAt?: number };
  }): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction(
        [
          'areas',
          'projects',
          'headings',
          'tasks',
          'checklist_items',
          'tags',
          'attachments',
          'projectTemplates'
        ],
        'readwrite'
      );
      const map: Array<[string, { ids: string[]; maxUpdatedAt?: number } | undefined]> = [
        ['areas', idsByStore.areas],
        ['projects', idsByStore.projects],
        ['headings', idsByStore.headings],
        ['tasks', idsByStore.tasks],
        ['checklist_items', idsByStore.checklistItems],
        ['tags', idsByStore.tags],
        ['attachments', idsByStore.attachments],
        ['projectTemplates', idsByStore.projectTemplates]
      ];
      for (const [storeName, spec] of map) {
        if (!spec || spec.ids.length === 0) continue;
        const maxTs = spec.maxUpdatedAt ?? Number.MAX_SAFE_INTEGER;
        const store = tx.objectStore(storeName);
        for (const id of spec.ids) {
          const cur = (await store.get(id)) as { updatedAt: number; isDirty: boolean } | undefined;
          if (cur && cur.isDirty && cur.updatedAt <= maxTs) {
            await store.put({ ...cur, isDirty: false });
          }
        }
      }
      await tx.done;
    });
  }

  async applyRemoteDeltas(batch: {
    tags?: TagRecord[];
    areas?: AreaRecord[];
    projects?: ProjectRecord[];
    headings?: HeadingRecord[];
    tasks?: TaskRecord[];
    checklistItems?: ChecklistItemRecord[];
    attachments?: AttachmentRecord[];
    projectTemplates?: ProjectTemplate[];
  }): Promise<number> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      let maxUpdatedAt = 0;
      const tx = db.transaction(
        [
          'tags',
          'areas',
          'projects',
          'headings',
          'tasks',
          'checklist_items',
          'attachments',
          'projectTemplates'
        ],
        'readwrite'
      );

      const applyList = async <T extends { id: string; updatedAt: number; isDirty: boolean }>(
        storeName: string,
        items: T[] | undefined,
        mergeFn?: (local: T | undefined, remote: T) => T
      ) => {
        if (!items || items.length === 0) return;
        const store = tx.objectStore(storeName);
        for (const remote of items) {
          if (!remote.id) continue;
          const local = (await store.get(remote.id)) as T | undefined;
          if (!local || remote.updatedAt >= local.updatedAt) {
            const merged = mergeFn ? mergeFn(local, remote) : remote;
            await store.put({ ...merged, isDirty: false });
          }
          if (remote.updatedAt > maxUpdatedAt) {
            maxUpdatedAt = remote.updatedAt;
          }
        }
      };

      await applyList('tags', batch.tags);
      await applyList('areas', batch.areas);
      await applyList('projects', batch.projects, (local, remote) => ({
        ...remote,
        purgedFromTrash: Boolean(
          remote.purgedFromTrash || (remote.isDeleted && local?.purgedFromTrash)
        ),
        detachedTaskSnapshots: remote.isDeleted ? local?.detachedTaskSnapshots : undefined
      }));
      await applyList('headings', batch.headings);
      await applyList('tasks', batch.tasks, (local, remote) => ({
        ...remote,
        purgedFromTrash: Boolean(
          remote.purgedFromTrash || (remote.isDeleted && local?.purgedFromTrash)
        )
      }));
      await applyList('checklist_items', batch.checklistItems);
      await applyList('attachments', batch.attachments, (local, remote) => {
        if (!remote.isDeleted && !remote.dataBase64 && local?.dataBase64) {
          return { ...remote, dataBase64: local.dataBase64 };
        }
        return remote;
      });
      await applyList('projectTemplates', batch.projectTemplates);

      await tx.done;

      if ((batch.tasks?.length ?? 0) > 0) {
        const promoted = await this.rolloverDueTasksUnlocked(db);
        if (promoted > 0) {
          this.triggerSync();
        }
      }

      await this.refreshSnapshot();
      return maxUpdatedAt;
    });
  }

  // ==================== Full Backup (.tbbackup) & Restore ====================

  async createBackupBytes(
    password: string | null,
    prefs: AppPreferences
  ): Promise<{ bytes: Uint8Array; summary: BackupSummary }> {
    const snap = await this.refreshSnapshot();
    const payload = buildBackupPayloadFromEntities({
      areas: snap.areas,
      projects: snap.projects,
      headings: snap.headings,
      tasks: snap.tasks,
      checklistItems: snap.checklistItems,
      tags: snap.tags,
      attachments: snap.attachments,
      projectTemplates: snap.projectTemplates,
      preferences: {
        sidebarVisible: prefs.sidebarVisible,
        dailyDigestEnabled: prefs.dailyDigestEnabled,
        dailyDigestHour: prefs.dailyDigestHour,
        dailyDigestMinute: prefs.dailyDigestMinute,
        languageTag: prefs.language === 'system' ? '' : prefs.language,
        calendarEventsEnabled: prefs.calendarEventsEnabled,
        selectedCalendarIds: prefs.selectedCalendarIds,
        groupTodayByProjectOrArea: prefs.groupTodayByProjectOrArea,
        includeBetaUpdates: Boolean(prefs.includeBetaUpdates),
        lastWeeklyReviewAt: prefs.lastWeeklyReviewAt || 0
      },
      appVersionName: `${APP_VERSION}-chromeos`
    });
    const bytes = await encodeThingsBackup(payload, password);
    return {
      bytes,
      summary: summarizeBackupPayload(payload, Boolean(password && password.length > 0))
    };
  }

  async inspectBackupFile(rawBytes: Uint8Array): Promise<BackupInspection> {
    return inspectThingsBackup(rawBytes);
  }

  async restoreBackupFromBytes(
    rawBytes: Uint8Array,
    password: string | null,
    syncAfterRestore: boolean
  ): Promise<{
    summary: BackupSummary;
    languageTag?: string | null;
    preferences?: BackupPreferencesDto | null;
  }> {
    const payload = await decodeThingsBackup(rawBytes, password);
    const now = Date.now();
    const normalized = normalizeBackupPayloadToEntities(payload, syncAfterRestore, now);
    const {
      tags,
      areas,
      projects,
      headings,
      tasks,
      checklistItems,
      attachments,
      projectTemplates
    } = normalized;

    const db = await this.getDb();
    const tx = db.transaction(
      [
        'areas',
        'projects',
        'headings',
        'tasks',
        'checklist_items',
        'tags',
        'attachments',
        'projectTemplates'
      ],
      'readwrite'
    );
    await tx.objectStore('attachments').clear();
    await tx.objectStore('checklist_items').clear();
    await tx.objectStore('tasks').clear();
    await tx.objectStore('headings').clear();
    await tx.objectStore('projects').clear();
    await tx.objectStore('areas').clear();
    await tx.objectStore('tags').clear();
    await tx.objectStore('projectTemplates').clear();

    for (const a of areas) await tx.objectStore('areas').put(a);
    for (const tg of tags) await tx.objectStore('tags').put(tg);
    for (const p of projects) await tx.objectStore('projects').put(p);
    for (const h of headings) await tx.objectStore('headings').put(h);
    for (const t of tasks) await tx.objectStore('tasks').put(t);
    for (const c of checklistItems) await tx.objectStore('checklist_items').put(c);
    for (const att of attachments) await tx.objectStore('attachments').put(att);
    for (const tpl of projectTemplates) await tx.objectStore('projectTemplates').put(tpl);
    await tx.done;

    await this.refreshSnapshot();
    if (syncAfterRestore) {
      this.triggerSync();
    }

    return {
      summary: {
        createdAt: payload.createdAt,
        appVersionName: payload.appVersionName,
        encrypted: Boolean(password && password.length > 0),
        areasCount: areas.length,
        projectsCount: projects.length,
        headingsCount: headings.length,
        tasksCount: tasks.length,
        checklistItemsCount: checklistItems.length,
        tagsCount: tags.length,
        attachmentsCount: attachments.length,
        projectTemplatesCount: projectTemplates.length
      },
      languageTag: payload.preferences?.languageTag,
      preferences: payload.preferences ?? null
    };
  }

  // ==================== CSV Export & Import ====================

  exportCsv(): string {
    const snap = this.cachedSnapshot;
    return serializeEntitiesToCsv({
      tasks: snap.tasks,
      areas: snap.areas,
      projects: snap.projects,
      tags: snap.tags,
      checklistItems: snap.checklistItems
    });
  }

  async importCsv(rawCsv: string): Promise<number> {
    return this.enqueueWrite(async () => {
      const now = Date.now();
      const parsedRows = parseCsvTaskImport(rawCsv, now);
      if (parsedRows.length === 0) return 0;

      const db = await this.getDb();
      const existingAreas = ((await db.getAll('areas')) as AreaRecord[]).filter(
        (a) => !a.isDeleted
      );
      const existingProjects = ((await db.getAll('projects')) as ProjectRecord[]).filter(
        (p) => !p.isDeleted
      );
      const existingTags = ((await db.getAll('tags')) as TagRecord[]).filter((t) => !t.isDeleted);

      let imported = 0;
      const tx = db.transaction(
        ['areas', 'projects', 'tags', 'tasks', 'checklist_items'],
        'readwrite'
      );

      for (let r = 0; r < parsedRows.length; r++) {
        const row = parsedRows[r];
        let areaId: string | null = null;
        if (row.areaName) {
          const match = existingAreas.find(
            (a) => a.title.toLowerCase() === row.areaName.toLowerCase()
          );
          if (match) {
            areaId = match.id;
          } else if (existingAreas.length < MAX_AREAS) {
            const created: AreaRecord = {
              id: crypto.randomUUID(),
              title: row.areaName,
              sortOrder: (existingAreas.length + 1) * 1000,
              tagIds: [],
              createdAt: now,
              updatedAt: now,
              isDeleted: false,
              isDirty: true
            };
            await tx.objectStore('areas').put(created);
            existingAreas.push(created);
            areaId = created.id;
          }
        }

        let projectId: string | null = null;
        if (row.projectName) {
          const match = existingProjects.find(
            (p) => p.title.toLowerCase() === row.projectName.toLowerCase()
          );
          if (match) {
            projectId = match.id;
          } else {
            const created: ProjectRecord = {
              id: crypto.randomUUID(),
              areaId,
              title: row.projectName,
              notes: '',
              status: 'ACTIVE',
              scheduleState: 'ANYTIME',
              whenDate: null,
              deadline: null,
              completedAt: null,
              sortOrder: (existingProjects.length + 1) * 1000,
              tagIds: [],
              createdAt: now,
              updatedAt: now,
              isDeleted: false,
              isDirty: true
            };
            await tx.objectStore('projects').put(created);
            existingProjects.push(created);
            projectId = created.id;
          }
        }

        const tagIds: string[] = [];
        for (const tName of row.tagNames) {
          const match = existingTags.find(
            (tg) => tg.title.toLowerCase() === tName.toLowerCase()
          );
          if (match) {
            tagIds.push(match.id);
          } else {
            const created: TagRecord = {
              id: crypto.randomUUID(),
              title: tName,
              parentTagId: null,
              shortcut: null,
              sortOrder: existingTags.length * 100 + 100,
              createdAt: now,
              updatedAt: now,
              isDeleted: false,
              isDirty: true
            };
            await tx.objectStore('tags').put(created);
            existingTags.push(created);
            tagIds.push(created.id);
          }
        }

        let scheduleState = row.scheduleState;
        if (
          scheduleState === 'INBOX' &&
          row.whenDate == null &&
          (projectId != null || areaId != null)
        ) {
          scheduleState = 'ANYTIME';
        }

        const taskId = crypto.randomUUID();
        await tx.objectStore('tasks').put({
          id: taskId,
          title: row.title,
          notes: row.notes,
          status: row.status,
          scheduleState,
          whenDate: row.whenDate,
          deadline: row.deadline,
          reminderTime: null,
          completedAt:
            row.status === 'COMPLETED' || row.status === 'CANCELED' ? now : null,
          projectId,
          headingId: null,
          areaId: projectId ? null : areaId,
          recurrenceRule: null,
          sortOrder: computeMidpointSortOrder(r * 50, null),
          tagIds: Array.from(new Set(tagIds)),
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true,
          purgedFromTrash: false
        } satisfies TaskRecord);

        for (let sIdx = 0; sIdx < row.checklistItems.length; sIdx++) {
          const item = row.checklistItems[sIdx];
          await tx.objectStore('checklist_items').put({
            id: crypto.randomUUID(),
            taskId,
            title: item.title,
            isCompleted: item.isCompleted,
            sortOrder: (sIdx + 1) * 100,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }

        imported++;
      }

      await tx.done;
      await this.refreshSnapshot();
      if (imported > 0) this.triggerSync();
      return imported;
    });
  }
}

export const localDb = new ThingsLocalDb();
