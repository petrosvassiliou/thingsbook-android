import { openDB, type IDBPDatabase } from 'idb';
import {
  MAX_AREAS,
  addDaysLocal,
  calculateNextRecurrenceDate,
  computeMidpointSortOrder,
  endOfDayEpoch,
  isoDayOfWeek,
  startOfDayEpoch,
  type AppPreferences,
  type AreaRecord,
  type ChecklistItemRecord,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectStatus,
  type RecurrenceRule,
  type ScheduleState,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus,
  type ThingsbookStatistics
} from '../domain/types';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  inspectThingsBackup,
  normalizeProjectStatus,
  normalizeScheduleState,
  normalizeTaskStatus,
  safeParseRecurrence,
  safeParseTagList,
  summarizeBackupPayload,
  type BackupInspection,
  type BackupPreferencesDto,
  type BackupSummary
} from './backupCodec';
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
const DB_VERSION = 1;
const PREFS_STORAGE_KEY = 'thingsbook_chromeos_prefs_v1';

export interface DataSnapshot {
  areas: AreaRecord[];
  projects: ProjectRecord[];
  headings: HeadingRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
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
    deletedTasksCount: snap.tasks.filter((t) => t.isDeleted).length,
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
  geminiApiKey: ''
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

type Listener = (snapshot: DataSnapshot) => void;

class ThingsLocalDb {
  private dbPromise: Promise<IDBPDatabase> | null = null;
  private listeners = new Set<Listener>();
  private syncTriggerCallback: (() => void) | null = null;
  private writeChain: Promise<unknown> = Promise.resolve();
  private cachedSnapshot: DataSnapshot = {
    areas: [],
    projects: [],
    headings: [],
    tasks: [],
    checklistItems: [],
    tags: [],
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
          if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta');
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

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    listener(this.cachedSnapshot);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getSnapshot(): DataSnapshot {
    return this.cachedSnapshot;
  }

  async refreshSnapshot(): Promise<DataSnapshot> {
    const db = await this.getDb();
    const [areas, projects, headings, tasks, checklistItems, tags] = await Promise.all([
      db.getAll('areas') as Promise<AreaRecord[]>,
      db.getAll('projects') as Promise<ProjectRecord[]>,
      db.getAll('headings') as Promise<HeadingRecord[]>,
      db.getAll('tasks') as Promise<TaskRecord[]>,
      db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>,
      db.getAll('tags') as Promise<TagRecord[]>
    ]);

    const hasSampleTasks = tasks.some((t) => !t.isDeleted && SAMPLE_TASK_IDS.has(t.id));

    this.cachedSnapshot = {
      areas: areas.sort((a, b) => a.sortOrder - b.sortOrder),
      projects: projects.sort((a, b) => a.sortOrder - b.sortOrder),
      headings: headings.sort((a, b) => a.sortOrder - b.sortOrder),
      tasks: tasks.sort((a, b) => a.sortOrder - b.sortOrder || b.createdAt - a.createdAt),
      checklistItems: checklistItems.sort((a, b) => a.sortOrder - b.sortOrder),
      tags: tags.sort((a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title)),
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
    projectId?: string | null;
    headingId?: string | null;
    areaId?: string | null;
    recurrenceRule?: RecurrenceRule | null;
    sortOrder?: number;
    tagIds?: string[];
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
      isDirty: true
    };

    await tx.store.put(record);
    await tx.done;

    // Spawn next recurrence instance if transitioning OPEN -> COMPLETED
    if (
      (!existing || existing.status === 'OPEN') &&
      status === 'COMPLETED' &&
      record.recurrenceRule != null
    ) {
      await this.spawnNextRecurrence(record, now);
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
      projectId?: string | null;
      headingId?: string | null;
      areaId?: string | null;
    }
  ): Promise<void> {
    if (taskIds.length === 0) return;
    return this.enqueueWrite(async () => {
      for (const id of taskIds) {
        await this.upsertTaskUnlocked({
          id,
          ...patch
        });
      }
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  private async spawnNextRecurrence(completedTask: TaskRecord, now: number): Promise<void> {
    if (!completedTask.recurrenceRule) return;
    const nextWhen = calculateNextRecurrenceDate(
      completedTask.recurrenceRule,
      completedTask.whenDate,
      now
    );
    if (nextWhen == null) return;

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
  }

  async softDeleteTasks(taskIds: string[]): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['tasks', 'checklist_items'], 'readwrite');
      for (const id of taskIds) {
        const existing = (await tx.objectStore('tasks').get(id)) as TaskRecord | undefined;
        if (existing) {
          await tx.objectStore('tasks').put({
            ...existing,
            isDeleted: true,
            updatedAt: now,
            isDirty: true
          });
        }
      }
      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      const idSet = new Set(taskIds);
      for (const chk of allChk) {
        if (idSet.has(chk.taskId) && !chk.isDeleted) {
          await tx.objectStore('checklist_items').put({
            ...chk,
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

  async duplicateTask(taskId: string): Promise<string | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const existing = (await db.get('tasks', taskId)) as TaskRecord | undefined;
      if (!existing) return null;
      const now = Date.now();
      const newId = crypto.randomUUID();
      await db.put('tasks', {
        ...existing,
        id: newId,
        status: 'OPEN',
        completedAt: null,
        sortOrder: existing.sortOrder + 0.5,
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
      const tx = db.transaction(['projects', 'headings', 'tasks'], 'readwrite');
      const proj = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      if (proj) {
        await tx.objectStore('projects').put({ ...proj, isDeleted: true, updatedAt: now, isDirty: true });
      }
      const headings = (await tx.objectStore('headings').getAll()) as HeadingRecord[];
      for (const h of headings) {
        if (h.projectId === projectId && !h.isDeleted) {
          await tx.objectStore('headings').put({ ...h, isDeleted: true, updatedAt: now, isDirty: true });
        }
      }
      const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of tasks) {
        if (t.projectId === projectId && !t.isDeleted) {
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
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
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
      const tx = db.transaction(['areas', 'projects', 'tasks'], 'readwrite');
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
      const tx = db.transaction(['tags', 'tasks', 'projects', 'areas'], 'readwrite');
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
        ['tasks', 'checklist_items', 'headings', 'projects', 'tags', 'areas'],
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
        const stores = ['areas', 'projects', 'headings', 'tasks', 'checklist_items', 'tags'] as const;
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
        ['areas', 'projects', 'headings', 'tasks', 'checklist_items', 'tags'],
        'readwrite'
      );
      await tx.objectStore('checklist_items').clear();
      await tx.objectStore('tasks').clear();
      await tx.objectStore('headings').clear();
      await tx.objectStore('projects').clear();
      await tx.objectStore('areas').clear();
      await tx.objectStore('tags').clear();
      await tx.done;
      await this.refreshSnapshot();
    });
  }

  // ==================== Cloud Sync Helpers ====================

  async getDirtyEntities() {
    const db = await this.getDb();
    const [areas, projects, headings, tasks, checklistItems, tags] = await Promise.all([
      db.getAll('areas') as Promise<AreaRecord[]>,
      db.getAll('projects') as Promise<ProjectRecord[]>,
      db.getAll('headings') as Promise<HeadingRecord[]>,
      db.getAll('tasks') as Promise<TaskRecord[]>,
      db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>,
      db.getAll('tags') as Promise<TagRecord[]>
    ]);
    return {
      areas: areas.filter((x) => x.isDirty),
      projects: projects.filter((x) => x.isDirty),
      headings: headings.filter((x) => x.isDirty),
      tasks: tasks.filter((x) => x.isDirty),
      checklistItems: checklistItems.filter((x) => x.isDirty),
      tags: tags.filter((x) => x.isDirty)
    };
  }

  async markSynced(idsByStore: {
    areas?: { ids: string[]; maxUpdatedAt?: number };
    projects?: { ids: string[]; maxUpdatedAt?: number };
    headings?: { ids: string[]; maxUpdatedAt?: number };
    tasks?: { ids: string[]; maxUpdatedAt?: number };
    checklistItems?: { ids: string[]; maxUpdatedAt?: number };
    tags?: { ids: string[]; maxUpdatedAt?: number };
  }): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction(
        ['areas', 'projects', 'headings', 'tasks', 'checklist_items', 'tags'],
        'readwrite'
      );
      const map: Array<[string, { ids: string[]; maxUpdatedAt?: number } | undefined]> = [
        ['areas', idsByStore.areas],
        ['projects', idsByStore.projects],
        ['headings', idsByStore.headings],
        ['tasks', idsByStore.tasks],
        ['checklist_items', idsByStore.checklistItems],
        ['tags', idsByStore.tags]
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
  }): Promise<number> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      let maxUpdatedAt = 0;
      const tx = db.transaction(
        ['tags', 'areas', 'projects', 'headings', 'tasks', 'checklist_items'],
        'readwrite'
      );

      const applyList = async <T extends { id: string; updatedAt: number; isDirty: boolean }>(
        storeName: string,
        items: T[] | undefined
      ) => {
        if (!items || items.length === 0) return;
        const store = tx.objectStore(storeName);
        for (const remote of items) {
          if (!remote.id) continue;
          const local = (await store.get(remote.id)) as T | undefined;
          if (!local || remote.updatedAt >= local.updatedAt) {
            await store.put({ ...remote, isDirty: false });
          }
          if (remote.updatedAt > maxUpdatedAt) {
            maxUpdatedAt = remote.updatedAt;
          }
        }
      };

      await applyList('tags', batch.tags);
      await applyList('areas', batch.areas);
      await applyList('projects', batch.projects);
      await applyList('headings', batch.headings);
      await applyList('tasks', batch.tasks);
      await applyList('checklist_items', batch.checklistItems);

      await tx.done;
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
      preferences: {
        sidebarVisible: prefs.sidebarVisible,
        dailyDigestEnabled: prefs.dailyDigestEnabled,
        dailyDigestHour: prefs.dailyDigestHour,
        dailyDigestMinute: prefs.dailyDigestMinute,
        languageTag: prefs.language === 'system' ? '' : prefs.language
      },
      appVersionName: '1.6.20-chromeos'
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

    const validTags = (payload.tags || []).filter((t) => t.id && t.title);
    const validTagIds = new Set(validTags.map((t) => t.id));
    const tags: TagRecord[] = validTags.map((tg) => ({
      id: tg.id,
      title: tg.title.trim(),
      parentTagId: tg.parentTagId && validTagIds.has(tg.parentTagId) && tg.parentTagId !== tg.id ? tg.parentTagId : null,
      shortcut: tg.shortcut?.trim() || null,
      sortOrder: Number.isFinite(tg.sortOrder) ? tg.sortOrder : 0,
      createdAt: tg.createdAt || now,
      updatedAt: tg.updatedAt || now,
      isDeleted: false,
      isDirty: syncAfterRestore
    }));

    const areas: AreaRecord[] = (payload.areas || [])
      .filter((a) => a.id && a.title)
      .slice(0, MAX_AREAS)
      .map((a) => ({
        id: a.id,
        title: a.title.trim(),
        sortOrder: Number.isFinite(a.sortOrder) ? a.sortOrder : 0,
        tagIds: safeParseTagList(a.tagIdsJson).filter((id) => validTagIds.has(id)),
        createdAt: a.createdAt || now,
        updatedAt: a.updatedAt || now,
        isDeleted: false,
        isDirty: syncAfterRestore
      }));
    const validAreaIds = new Set(areas.map((a) => a.id));

    const projRefMap = new Map<string, string[]>();
    for (const ref of payload.projectTagRefs || []) {
      if (validTagIds.has(ref.tagId)) {
        const list = projRefMap.get(ref.projectId) || [];
        list.push(ref.tagId);
        projRefMap.set(ref.projectId, list);
      }
    }

    const projects: ProjectRecord[] = (payload.projects || [])
      .filter((p) => p.id)
      .map((p) => {
        const mergedTags = Array.from(
          new Set([...safeParseTagList(p.tagIdsJson), ...(projRefMap.get(p.id) || [])])
        ).filter((id) => validTagIds.has(id));
        return {
          id: p.id,
          areaId: p.areaId && validAreaIds.has(p.areaId) ? p.areaId : null,
          title: p.title || '',
          notes: p.notes || '',
          status: normalizeProjectStatus(p.status),
          scheduleState: normalizeScheduleState(p.scheduleState, 'ANYTIME'),
          whenDate: p.whenDate ?? null,
          deadline: p.deadline ?? null,
          completedAt: p.completedAt ?? null,
          sortOrder: Number.isFinite(p.sortOrder) ? p.sortOrder : 0,
          tagIds: mergedTags,
          createdAt: p.createdAt || now,
          updatedAt: p.updatedAt || now,
          isDeleted: false,
          isDirty: syncAfterRestore
        };
      });
    const validProjectIds = new Set(projects.map((p) => p.id));

    const headings: HeadingRecord[] = (payload.headings || [])
      .filter((h) => h.id && validProjectIds.has(h.projectId))
      .map((h) => ({
        id: h.id,
        projectId: h.projectId,
        title: h.title || '',
        sortOrder: Number.isFinite(h.sortOrder) ? h.sortOrder : 0,
        isArchived: Boolean(h.isArchived),
        createdAt: h.createdAt || now,
        updatedAt: h.updatedAt || now,
        isDeleted: false,
        isDirty: syncAfterRestore
      }));
    const headingProjectMap = new Map(headings.map((h) => [h.id, h.projectId]));

    const taskRefMap = new Map<string, string[]>();
    for (const ref of payload.taskTagRefs || []) {
      if (validTagIds.has(ref.tagId)) {
        const list = taskRefMap.get(ref.taskId) || [];
        list.push(ref.tagId);
        taskRefMap.set(ref.taskId, list);
      }
    }

    const tasks: TaskRecord[] = (payload.tasks || [])
      .filter((t) => t.id)
      .map((t) => {
        const cleanProjId = t.projectId && validProjectIds.has(t.projectId) ? t.projectId : null;
        const cleanHeadId =
          cleanProjId && t.headingId && headingProjectMap.get(t.headingId) === cleanProjId
            ? t.headingId
            : null;
        const cleanAreaId = t.areaId && validAreaIds.has(t.areaId) ? t.areaId : null;
        const mergedTags = Array.from(
          new Set([...safeParseTagList(t.tagIdsJson), ...(taskRefMap.get(t.id) || [])])
        ).filter((id) => validTagIds.has(id));
        const parsedRecurrence = safeParseRecurrence(t.recurrenceRuleJson);
        const rawSchedule = normalizeScheduleState(t.scheduleState, 'INBOX');
        const normalizedSchedule: ScheduleState =
          rawSchedule === 'INBOX' &&
          t.whenDate == null &&
          parsedRecurrence == null &&
          (cleanProjId != null || cleanAreaId != null)
            ? 'ANYTIME'
            : rawSchedule;

        return {
          id: t.id,
          title: t.title || '',
          notes: t.notes || '',
          status: normalizeTaskStatus(t.status),
          scheduleState: normalizedSchedule,
          whenDate: t.whenDate ?? null,
          deadline: t.deadline ?? null,
          reminderTime: t.reminderTime ?? null,
          completedAt: t.completedAt ?? null,
          projectId: cleanProjId,
          headingId: cleanHeadId,
          areaId: cleanAreaId,
          recurrenceRule: parsedRecurrence,
          sortOrder: Number.isFinite(t.sortOrder) ? t.sortOrder : 0,
          tagIds: mergedTags,
          createdAt: t.createdAt || now,
          updatedAt: t.updatedAt || now,
          isDeleted: false,
          isDirty: syncAfterRestore
        };
      });
    const validTaskIds = new Set(tasks.map((t) => t.id));

    const checklistItems: ChecklistItemRecord[] = (payload.checklistItems || [])
      .filter((c) => c.id && validTaskIds.has(c.taskId))
      .map((c) => ({
        id: c.id,
        taskId: c.taskId,
        title: c.title || '',
        isCompleted: Boolean(c.isCompleted),
        sortOrder: Number.isFinite(c.sortOrder) ? c.sortOrder : 0,
        createdAt: c.createdAt || now,
        updatedAt: c.updatedAt || now,
        isDeleted: false,
        isDirty: syncAfterRestore
      }));

    const db = await this.getDb();
    const tx = db.transaction(
      ['areas', 'projects', 'headings', 'tasks', 'checklist_items', 'tags'],
      'readwrite'
    );
    await tx.objectStore('checklist_items').clear();
    await tx.objectStore('tasks').clear();
    await tx.objectStore('headings').clear();
    await tx.objectStore('projects').clear();
    await tx.objectStore('areas').clear();
    await tx.objectStore('tags').clear();

    for (const a of areas) await tx.objectStore('areas').put(a);
    for (const tg of tags) await tx.objectStore('tags').put(tg);
    for (const p of projects) await tx.objectStore('projects').put(p);
    for (const h of headings) await tx.objectStore('headings').put(h);
    for (const t of tasks) await tx.objectStore('tasks').put(t);
    for (const c of checklistItems) await tx.objectStore('checklist_items').put(c);
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
        tagsCount: tags.length
      },
      languageTag: payload.preferences?.languageTag,
      preferences: payload.preferences ?? null
    };
  }

  // ==================== CSV Export & Import ====================

  exportCsv(): string {
    const snap = this.cachedSnapshot;
    const areasById = new Map(snap.areas.filter((a) => !a.isDeleted).map((a) => [a.id, a]));
    const projectsById = new Map(snap.projects.filter((p) => !p.isDeleted).map((p) => [p.id, p]));
    const tagsById = new Map(snap.tags.filter((tg) => !tg.isDeleted).map((tg) => [tg.id, tg]));

    const esc = (v: string) =>
      /[",;\r\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
    const fmtDate = (ms: number | null) => {
      if (ms == null) return '';
      const d = new Date(ms);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    };

    const lines = ['Title,Notes,Status,ScheduleState,WhenDate,Deadline,Area,Project,Tags,Checklist'];
    for (const t of snap.tasks.filter((x) => !x.isDeleted)) {
      const proj = t.projectId ? projectsById.get(t.projectId) : undefined;
      const areaId = proj?.areaId ?? t.areaId;
      const area = areaId ? areasById.get(areaId) : undefined;
      const tagStr = t.tagIds
        .map((id) => tagsById.get(id)?.title)
        .filter(Boolean)
        .join('; ');
      const chkStr = snap.checklistItems
        .filter((c) => c.taskId === t.id && !c.isDeleted)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((c) => `${c.isCompleted ? '[x] ' : '[ ] '}${c.title}`)
        .join('; ');

      lines.push(
        [
          esc(t.title),
          esc(t.notes),
          esc(t.status),
          esc(t.scheduleState),
          esc(fmtDate(t.whenDate)),
          esc(fmtDate(t.deadline)),
          esc(area?.title || ''),
          esc(proj?.title || ''),
          esc(tagStr),
          esc(chkStr)
        ].join(',')
      );
    }
    return lines.join('\n');
  }

  async importCsv(rawCsv: string): Promise<number> {
    const cleaned = rawCsv.replace(/^\uFEFF/, '').trim();
    if (!cleaned) return 0;
    const firstLine = cleaned.split(/\r?\n/)[0] || '';
    const delimiter =
      (firstLine.match(/;/g) || []).length > (firstLine.match(/,/g) || []).length &&
      !firstLine.includes('Title,')
        ? ';'
        : ',';

    const rows = parseCsvRows(cleaned, delimiter);
    if (rows.length === 0) return 0;

    const header = rows[0].map((c) => c.trim().toLowerCase());
    const hasHeader = header.some((h) => ['title', 'titel', 'aufgabe', 'task', 'name'].includes(h));
    const dataRows = (hasHeader ? rows.slice(1) : rows).slice(0, 10000);
    if (dataRows.length === 0) return 0;

    const colIdx = (names: string[], fallback: number) => {
      if (!hasHeader) return fallback;
      const found = header.findIndex((h) => names.includes(h));
      return found;
    };

    const idxTitle = Math.max(0, colIdx(['title', 'titel', 'aufgabe', 'task', 'name'], 0));
    const idxNotes = colIdx(['notes', 'notizen', 'beschreibung', 'description'], 1);
    const idxStatus = colIdx(['status'], 2);
    const idxSchedule = colIdx(['schedulestate', 'schedule', 'when', 'ansicht', 'view'], 3);
    const idxWhen = colIdx(['whendate', 'startdatum', 'date', 'datum'], 4);
    const idxDeadline = colIdx(['deadline', 'fälligkeit', 'due', 'duedate', 'zieldatum'], 5);
    const idxArea = colIdx(['area', 'bereich'], 6);
    const idxProject = colIdx(['project', 'projekt'], 7);
    const idxTags = colIdx(['tags', 'tag'], 8);
    const idxChecklist = colIdx(['checklist', 'subtasks', 'unteraufgaben'], 9);

    const db = await this.getDb();
    const now = Date.now();
    const endToday = endOfDayEpoch(new Date(now));

    const existingAreas = ((await db.getAll('areas')) as AreaRecord[]).filter((a) => !a.isDeleted);
    const existingProjects = ((await db.getAll('projects')) as ProjectRecord[]).filter((p) => !p.isDeleted);
    const existingTags = ((await db.getAll('tags')) as TagRecord[]).filter((t) => !t.isDeleted);

    let imported = 0;
    const tx = db.transaction(['areas', 'projects', 'tags', 'tasks', 'checklist_items'], 'readwrite');

    for (let r = 0; r < dataRows.length; r++) {
      const cols = dataRows[r];
      const get = (i: number) => (i >= 0 && cols[i] ? cols[i].trim() : '');
      const title = get(idxTitle);
      if (!title) continue;

      const status = normalizeTaskStatus(get(idxStatus));
      const whenDate = parseFlexibleDate(get(idxWhen));
      const deadline = parseFlexibleDate(get(idxDeadline));
      let scheduleState = normalizeScheduleState(
        get(idxSchedule),
        whenDate != null ? (whenDate > endToday ? 'UPCOMING' : 'TODAY') : 'INBOX'
      );

      let areaId: string | null = null;
      const rawArea = get(idxArea);
      if (rawArea) {
        const match = existingAreas.find((a) => a.title.toLowerCase() === rawArea.toLowerCase());
        if (match) {
          areaId = match.id;
        } else if (existingAreas.length < MAX_AREAS) {
          const created: AreaRecord = {
            id: crypto.randomUUID(),
            title: rawArea,
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
      const rawProj = get(idxProject);
      if (rawProj) {
        const match = existingProjects.find((p) => p.title.toLowerCase() === rawProj.toLowerCase());
        if (match) {
          projectId = match.id;
        } else {
          const created: ProjectRecord = {
            id: crypto.randomUUID(),
            areaId,
            title: rawProj,
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
      const rawTags = get(idxTags);
      if (rawTags) {
        for (const tName of rawTags.split(/[;|]/).map((s) => s.trim()).filter(Boolean)) {
          const match = existingTags.find((tg) => tg.title.toLowerCase() === tName.toLowerCase());
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
      }

      if (scheduleState === 'INBOX' && whenDate == null && (projectId != null || areaId != null)) {
        scheduleState = 'ANYTIME';
      }

      const taskId = crypto.randomUUID();
      await tx.objectStore('tasks').put({
        id: taskId,
        title,
        notes: get(idxNotes),
        status,
        scheduleState,
        whenDate,
        deadline,
        reminderTime: null,
        completedAt: status === 'COMPLETED' || status === 'CANCELED' ? now : null,
        projectId,
        headingId: null,
        areaId: projectId ? null : areaId,
        recurrenceRule: null,
        sortOrder: computeMidpointSortOrder(r * 50, null),
        tagIds: Array.from(new Set(tagIds)),
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies TaskRecord);

      const rawChecklist = get(idxChecklist);
      if (rawChecklist) {
        const subParts = rawChecklist.split(';').map((s) => s.trim()).filter(Boolean);
        for (let sIdx = 0; sIdx < subParts.length; sIdx++) {
          const rawSub = subParts[sIdx];
          const done = /^\[x\]/i.test(rawSub);
          const subTitle = rawSub.replace(/^\[[ xX]?\]\s*/, '').trim() || rawSub;
          await tx.objectStore('checklist_items').put({
            id: crypto.randomUUID(),
            taskId,
            title: subTitle,
            isCompleted: done,
            sortOrder: (sIdx + 1) * 100,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }
      }
      imported++;
    }

    await tx.done;
    await this.refreshSnapshot();
    if (imported > 0) this.triggerSync();
    return imported;
  }
}

function parseCsvRows(csv: string, delimiter: string): string[][] {
  const result: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;
  for (let i = 0; i < csv.length; i++) {
    const ch = csv[i];
    if (inQuotes) {
      if (ch === '"') {
        if (csv[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
    } else if (ch === '"' && field.length === 0) {
      inQuotes = true;
    } else if (ch === delimiter) {
      row.push(field);
      field = '';
    } else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && csv[i + 1] === '\n') i++;
      row.push(field);
      field = '';
      if (row.some((c) => c.trim().length > 0)) result.push(row);
      row = [];
    } else {
      field += ch;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    if (row.some((c) => c.trim().length > 0)) result.push(row);
  }
  return result;
}

function parseFlexibleDate(raw: string): number | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  const iso = trimmed.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (iso) {
    return new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]), 0, 0, 0, 0).getTime();
  }
  const dot = trimmed.match(/^(\d{1,2})\.(\d{1,2})\.(\d{2,4})$/);
  if (dot) {
    let y = Number(dot[3]);
    if (y < 100) y += 2000;
    return new Date(y, Number(dot[2]) - 1, Number(dot[1]), 0, 0, 0, 0).getTime();
  }
  const num = Number(trimmed);
  if (Number.isFinite(num) && num > 86_400_000) {
    return startOfDayEpoch(new Date(num));
  }
  return null;
}

export const localDb = new ThingsLocalDb();
