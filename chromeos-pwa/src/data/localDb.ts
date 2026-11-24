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
  type EffortLevel,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectStatus,
  type ProjectTemplate,
  type ProjectTemplateBlueprint,
  type ProjectTemplateTaskBlueprint,
  type ProjectViewMode,
  type RecurrenceRule,
  type ScheduleState,
  type SupportedLang,
  type TagRecord,
  type TaskRecord,
  type TaskStatus,
  type ThingsbookStatistics
} from '../domain/types.ts';
import {
  DEFAULT_WORKDAY_END_MINUTES,
  DEFAULT_WORKDAY_START_MINUTES,
  buildProjectTemplateBlueprint,
  normalizeWorkdayBounds,
  wouldCreateDependencyCycle
} from '../domain/v2Utils.ts';
import {
  computeSmartDependencyCascade,
  normalizeAreaViewMode
} from '../domain/v30Roadmap.ts';
import type {
  ProjectBlueprintResponse,
  RoadmapTaskBlueprintDto
} from './geminiService.ts';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  inspectThingsBackup,
  normalizeBackupPayloadToEntities,
  normalizeEffortLevel,
  normalizeProjectViewMode,
  normalizeTagSortMode,
  normalizeViewSortMode,
  safeParseBlockedByTaskIds,
  safeParseSavedPerspectives,
  summarizeBackupPayload,
  type BackupInspection,
  type BackupPreferencesDto,
  type BackupSummary
} from './backupCodec.ts';
import { parseCsvTaskImport, serializeEntitiesToCsv } from './csvCodec.ts';
import {
  exportExternalProjectBytes,
  isUngroupedSectionName,
  type ExternalExportFormat,
  type ExternalImportedProject,
  type ExternalImportedTask
} from './externalProjectCodec.ts';
import {
  OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS,
  buildRecurringSeriesKey,
  canonicalizeRecurrenceRuleKey,
  clampRemoteCursorTimestamp,
  deriveRecurringChecklistItemId,
  deriveRecurringNextTaskId,
  formatLocalYyyyMmDd,
  mergeMonotonicReviewTimestamp,
  normalizeRecurringTitleKey,
  planRecurringDeduplication,
  resolveMergedHeadingEndDate,
  resolveMergedHeadingStartDate,
  type RemoteHeadingWithHints,
  type RemoteProjectWithHints,
  type RemoteTaskWithHints
} from './syncUtils.ts';
import {
  SAMPLE_AREA_IDS,
  SAMPLE_CHECKLIST_IDS,
  SAMPLE_HEADING_IDS,
  SAMPLE_PROJECT_IDS,
  SAMPLE_TAG_IDS,
  SAMPLE_TASK_IDS,
  buildLocalizedSeedBundle,
  isActiveSampleTask,
  isPurgeableSampleTask
} from './seedData.ts';
import { resolveLanguage } from '../i18n/translations.ts';

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

export const DEFAULT_PREFS: AppPreferences = {
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
  appLockWebAuthnCredId: '',
  tagSortMode: 'alpha',
  viewSortMode: 'manual',
  savedPerspectives: [],
  areaViewModes: {},
  workdayStartMinutes: DEFAULT_WORKDAY_START_MINUTES,
  workdayEndMinutes: DEFAULT_WORKDAY_END_MINUTES
};

function safeParseAreaViewModes(
  raw: unknown
): Record<string, 'list' | 'roadmap'> {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {};
  const out: Record<string, 'list' | 'roadmap'> = {};
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
    const cleanKey = k.trim();
    if (!cleanKey || typeof v !== 'string') continue;
    out[cleanKey] = normalizeAreaViewMode(v);
  }
  return out;
}

export function loadPreferences(): AppPreferences {
  try {
    const raw = localStorage.getItem(PREFS_STORAGE_KEY);
    if (!raw) {
      return {
        ...DEFAULT_PREFS,
        savedPerspectives: [],
        areaViewModes: {}
      };
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const workday = normalizeWorkdayBounds(
      typeof parsed?.workdayStartMinutes === 'number' ? parsed.workdayStartMinutes : undefined,
      typeof parsed?.workdayEndMinutes === 'number' ? parsed.workdayEndMinutes : undefined
    );
    return {
      ...DEFAULT_PREFS,
      ...(parsed as Partial<AppPreferences>),
      showPerspectives:
        typeof parsed?.showPerspectives === 'boolean' ? parsed.showPerspectives : undefined,
      tagSortMode: normalizeTagSortMode(parsed?.tagSortMode),
      viewSortMode: normalizeViewSortMode(parsed?.viewSortMode),
      savedPerspectives: safeParseSavedPerspectives(parsed?.savedPerspectives),
      areaViewModes: safeParseAreaViewModes(parsed?.areaViewModes),
      workdayStartMinutes: workday.startMinutes,
      workdayEndMinutes: workday.endMinutes
    };
  } catch {
    return {
      ...DEFAULT_PREFS,
      savedPerspectives: [],
      areaViewModes: {}
    };
  }
}

export function savePreferences(prefs: AppPreferences): void {
  localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(prefs));
}

export function updatePreferences(patch: Partial<AppPreferences>): AppPreferences {
  const next: AppPreferences = { ...loadPreferences(), ...patch };
  savePreferences(next);
  return next;
}

export type UndoEventKind =
  | 'task_completed'
  | 'task_deleted'
  | 'project_completed'
  | 'project_deleted'
  | 'tasks_updated'
  | 'roadmap_rescheduled'
  | 'roadmap_cascade'
  | 'day_plan_applied';

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

export function normalizeTask(raw: TaskRecord): TaskRecord {
  return {
    ...raw,
    isPinned: Boolean(raw.isPinned),
    isMilestone: Boolean(raw.isMilestone),
    isInProgress: raw.status === 'OPEN' && !raw.isDeleted && Boolean(raw.isInProgress)
  };
}

export class ThingsLocalDb {
  private dbPromise: Promise<IDBPDatabase> | null = null;
  private readonly customDbOpener?: () => Promise<IDBPDatabase>;
  private listeners = new Set<Listener>();
  private undoListeners = new Set<UndoListener>();
  private lastUndoSnapshot: InternalUndoSnapshot | null = null;
  private syncTriggerCallback: ((immediate?: boolean) => void) | null = null;
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

  constructor(customDbOpener?: () => Promise<IDBPDatabase>) {
    this.customDbOpener = customDbOpener;
  }

  private enqueueWrite<T>(fn: () => Promise<T>): Promise<T> {
    const next = this.writeChain.then(fn, fn);
    this.writeChain = next.then(
      () => undefined,
      () => undefined
    );
    return next;
  }

  updatePreferences(patch: Partial<AppPreferences>): AppPreferences {
    return updatePreferences(patch);
  }

  setSyncTrigger(cb: (immediate?: boolean) => void) {
    this.syncTriggerCallback = cb;
  }

  private triggerSync(immediate = false) {
    if (this.syncTriggerCallback) {
      this.syncTriggerCallback(immediate);
    }
  }

  private getDb(): Promise<IDBPDatabase> {
    if (!this.dbPromise) {
      if (this.customDbOpener) {
        this.dbPromise = this.customDbOpener();
      } else {
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

        // Initial day rollover on startup (local-only view promotion; preserves updatedAt/isDirty)
        await this.rolloverDueTasksUnlocked(db);
        const dedupedOnStartup = await this.deduplicateOpenRecurringTasksUnlocked(db);
        if (dedupedOnStartup > 0) {
          this.triggerSync();
        }
      }

      return this.refreshSnapshot();
    });
  }

  /**
   * Internal unlocked helper matching Android TaskDao.rolloverDueUpcomingTasksToToday
   * and TaskDao.rolloverYesterdayEveningTasksToToday.
   * Local rollover MUST NOT bump updatedAt or mark tasks isDirty, otherwise a device waking up
   * before pulling remote completions would stamp a newer updatedAt and resurrect completed tasks.
   */
  private async rolloverDueTasksUnlocked(db: IDBPDatabase): Promise<number> {
    const startOfToday = startOfDayEpoch();
    const endOfToday = endOfDayEpoch();
    const allTasks = (await db.getAll('tasks')) as TaskRecord[];
    const updates: TaskRecord[] = [];

    for (const t of allTasks) {
      if (t.isDeleted || t.status !== 'OPEN') continue;

      if (t.scheduleState === 'UPCOMING' && t.whenDate != null && t.whenDate <= endOfToday) {
        updates.push({
          ...t,
          scheduleState: 'TODAY'
        });
      } else if (
        t.scheduleState === 'THIS_EVENING' &&
        ((t.whenDate != null && t.whenDate < startOfToday) ||
          (t.whenDate == null && t.updatedAt < startOfToday))
      ) {
        updates.push({
          ...t,
          scheduleState: 'TODAY',
          whenDate: startOfToday
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

  private async silentlyPurgeTasksByIdsUnlocked(
    db: IDBPDatabase,
    taskIds: readonly string[],
    now = Date.now()
  ): Promise<number> {
    if (taskIds.length === 0) return 0;
    const idSet = new Set(taskIds);
    const allTasks = (await db.getAll('tasks')) as TaskRecord[];
    const allChecklists = (await db.getAll('checklist_items')) as ChecklistItemRecord[];
    const tx = db.transaction(['tasks', 'checklist_items'], 'readwrite');
    const taskStore = tx.objectStore('tasks');
    const chkStore = tx.objectStore('checklist_items');
    let purgedCount = 0;

    for (const t of allTasks) {
      if (!idSet.has(t.id) || t.isDeleted) continue;
      const tombstoneAt = Math.max(now, t.updatedAt + 1);
      await taskStore.put({
        ...t,
        title: '',
        notes: '',
        recurrenceRule: null,
        tagIds: [],
        scheduledStartMinutes: null,
        durationMinutes: null,
        actualMinutes: null,
        isPinned: false,
        isMilestone: false,
        isInProgress: false,
        effortLevel: null,
        blockedByTaskIds: [],
        workspaceId: null,
        assigneeId: null,
        isDeleted: true,
        purgedFromTrash: true,
        isDirty: true,
        updatedAt: tombstoneAt
      });
      purgedCount++;
    }

    for (const item of allChecklists) {
      if (!idSet.has(item.taskId) || item.isDeleted) continue;
      const tombstoneAt = Math.max(now, item.updatedAt + 1);
      await chkStore.put({
        ...item,
        isDeleted: true,
        isDirty: true,
        updatedAt: tombstoneAt
      });
    }

    await tx.done;
    return purgedCount;
  }

  private async deduplicateOpenRecurringTasksUnlocked(
    db: IDBPDatabase,
    now = Date.now()
  ): Promise<number> {
    const allTasks = (await db.getAll('tasks')) as TaskRecord[];
    const plan = planRecurringDeduplication(allTasks);
    if (plan.winnerEnrichments.length > 0) {
      const tx = db.transaction('tasks', 'readwrite');
      for (const enrich of plan.winnerEnrichments) {
        const winner = (await tx.store.get(enrich.winnerId)) as TaskRecord | undefined;
        if (!winner || winner.isDeleted || winner.status !== 'OPEN') continue;
        await tx.store.put({
          ...winner,
          areaId: enrich.areaId !== undefined ? enrich.areaId : winner.areaId,
          headingId: enrich.headingId !== undefined ? enrich.headingId : winner.headingId,
          notes: enrich.notes !== undefined ? enrich.notes : winner.notes,
          tagIds: enrich.tagIds !== undefined ? enrich.tagIds : winner.tagIds,
          scheduledStartMinutes:
            enrich.scheduledStartMinutes !== undefined
              ? enrich.scheduledStartMinutes
              : winner.scheduledStartMinutes,
          durationMinutes:
            enrich.durationMinutes !== undefined ? enrich.durationMinutes : winner.durationMinutes,
          deadline: enrich.deadline !== undefined ? enrich.deadline : winner.deadline,
          reminderTime: enrich.reminderTime !== undefined ? enrich.reminderTime : winner.reminderTime,
          isPinned: enrich.isPinned !== undefined ? enrich.isPinned : winner.isPinned,
          isMilestone:
            enrich.isMilestone !== undefined ? enrich.isMilestone : Boolean(winner.isMilestone),
          isInProgress:
            enrich.isInProgress !== undefined ? enrich.isInProgress : Boolean(winner.isInProgress),
          effortLevel:
            enrich.effortLevel !== undefined ? enrich.effortLevel : winner.effortLevel,
          updatedAt: Math.max(now, winner.updatedAt + 1, enrich.minUpdatedAt),
          isDirty: true
        });
      }
      await tx.done;
    }
    const purged =
      plan.loserIds.length > 0
        ? await this.silentlyPurgeTasksByIdsUnlocked(db, plan.loserIds, now)
        : 0;
    return purged + plan.winnerEnrichments.length;
  }

  /**
   * Re-evaluates local day boundaries (00:00 midnight, app resume/focus, or timezone change)
   * and promotes due UPCOMING tasks and yesterday's THIS_EVENING tasks to TODAY.
   */
  async rolloverDueTasksForNewDay(): Promise<boolean> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const promotedCount = await this.rolloverDueTasksUnlocked(db);
      const dedupedCount = await this.deduplicateOpenRecurringTasksUnlocked(db);
      if (promotedCount > 0 || dedupedCount > 0) {
        await this.refreshSnapshot();
      }
      if (dedupedCount > 0) {
        this.triggerSync();
      }
      return promotedCount > 0 || dedupedCount > 0;
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
          updatedAt: Math.max(now, proj.updatedAt + 1),
          isDirty: true
        });
      }
      for (const h of snap.headings) {
        await tx.objectStore('headings').put({
          ...h,
          updatedAt: Math.max(now, h.updatedAt + 1),
          isDirty: true
        });
      }
      for (const tk of snap.tasks) {
        await tx.objectStore('tasks').put({
          ...tk,
          purgedFromTrash: false,
          updatedAt: Math.max(now, tk.updatedAt + 1),
          isDirty: true
        });
      }
      for (const chk of snap.checklistItems) {
        await tx.objectStore('checklist_items').put({
          ...chk,
          updatedAt: Math.max(now, chk.updatedAt + 1),
          isDirty: true
        });
      }
      for (const att of snap.attachments) {
        await tx.objectStore('attachments').put({
          ...att,
          updatedAt: Math.max(now, att.updatedAt + 1),
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
    const [areas, projects, headings, rawTasks, checklistItems, tags, attachments, projectTemplates] =
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
    const tasks = rawTasks.map(normalizeTask);

    const hasSampleTasks = tasks.some((t) => isActiveSampleTask(t));

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
    actualMinutes?: number | null;
    isPinned?: boolean;
    isMilestone?: boolean;
    isInProgress?: boolean;
    effortLevel?: EffortLevel | null;
    blockedByTaskIds?: string[];
    workspaceId?: string | null;
    assigneeId?: string | null;
    projectId?: string | null;
    headingId?: string | null;
    areaId?: string | null;
    recurrenceRule?: RecurrenceRule | null;
    sortOrder?: number;
    tagIds?: string[];
    addTagIds?: string[];
    removeTagIds?: string[];
    suppressUndo?: boolean;
  }): Promise<TaskRecord> {
    const db = await this.getDb();
    const now = Date.now();
    const todayStart = startOfDayEpoch(new Date(now));
    const todayEnd = endOfDayEpoch(new Date(now));
    const id = params.id || crypto.randomUUID();

    const tx = db.transaction('tasks', 'readwrite');
    const existing = (await tx.store.get(id)) as TaskRecord | undefined;

    let resolvedBlockedBy: string[];
    if (params.blockedByTaskIds !== undefined) {
      const candidates = safeParseBlockedByTaskIds(params.blockedByTaskIds, id);
      if (candidates.length === 0) {
        resolvedBlockedBy = [];
      } else {
        const allTasksInStore = (await tx.store.getAll()) as TaskRecord[];
        const blockedByMap = new Map<string, readonly string[]>();
        const existingIdSet = new Set<string>();
        for (const t of allTasksInStore) {
          if (t.isDeleted) continue;
          existingIdSet.add(t.id);
          if (t.id !== id && Array.isArray(t.blockedByTaskIds) && t.blockedByTaskIds.length > 0) {
            blockedByMap.set(t.id, t.blockedByTaskIds);
          }
        }
        const clean: string[] = [];
        for (const bid of candidates) {
          if (existingIdSet.size > 0 && !existingIdSet.has(bid)) {
            // Allow referencing known non-deleted tasks; if store already has tasks and bid is deleted, skip deleted
            const maybeDeleted = allTasksInStore.find((t) => t.id === bid);
            if (maybeDeleted?.isDeleted) continue;
          }
          if (!wouldCreateDependencyCycle(id, bid, blockedByMap)) {
            clean.push(bid);
            blockedByMap.set(id, clean);
          }
        }
        resolvedBlockedBy = clean;
      }
    } else {
      resolvedBlockedBy = safeParseBlockedByTaskIds(existing?.blockedByTaskIds, id);
    }

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
        ? Math.max(1, Math.min(48_000, Math.trunc(rawDuration)))
        : null;

    const rawActual =
      params.actualMinutes !== undefined
        ? params.actualMinutes
        : (existing?.actualMinutes ?? null);
    const actualMinutes =
      rawActual != null && Number.isFinite(rawActual) && rawActual > 0
        ? Math.max(1, Math.min(48_000, Math.trunc(rawActual)))
        : null;

    let resolvedTagIds = Array.from(
      new Set(params.tagIds !== undefined ? params.tagIds : (existing?.tagIds ?? []))
    );
    if (params.addTagIds && params.addTagIds.length > 0) {
      for (const tid of params.addTagIds) {
        if (!resolvedTagIds.includes(tid)) resolvedTagIds.push(tid);
      }
    }
    if (params.removeTagIds && params.removeTagIds.length > 0) {
      const rem = new Set(params.removeTagIds);
      resolvedTagIds = resolvedTagIds.filter((tid) => !rem.has(tid));
    }

    const resolvedEffort =
      params.effortLevel !== undefined
        ? normalizeEffortLevel(params.effortLevel)
        : (existing?.effortLevel ?? null);
    const resolvedWorkspaceId =
      params.workspaceId !== undefined
        ? params.workspaceId?.trim() || null
        : (existing?.workspaceId ?? null);
    const resolvedAssigneeId =
      params.assigneeId !== undefined
        ? params.assigneeId?.trim() || null
        : (existing?.assigneeId ?? null);

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
      actualMinutes,
      isPinned: params.isPinned !== undefined ? Boolean(params.isPinned) : Boolean(existing?.isPinned),
      isMilestone:
        params.isMilestone !== undefined
          ? Boolean(params.isMilestone)
          : (existing?.isMilestone ?? false),
      isInProgress:
        status === 'OPEN'
          ? params.isInProgress !== undefined
            ? Boolean(params.isInProgress)
            : Boolean(existing?.isInProgress)
          : false,
      effortLevel: resolvedEffort,
      blockedByTaskIds: resolvedBlockedBy,
      workspaceId: resolvedWorkspaceId,
      assigneeId: resolvedAssigneeId,
      completedAt,
      projectId,
      headingId,
      areaId,
      recurrenceRule,
      sortOrder:
        params.sortOrder !== undefined && Number.isFinite(params.sortOrder)
          ? params.sortOrder
          : (existing?.sortOrder ?? 1000),
      tagIds: resolvedTagIds,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      isDeleted: false,
      isDirty: true,
      purgedFromTrash: false
    };

    await tx.store.put(record);
    await tx.done;

    // Spawn next recurrence instance if transitioning OPEN -> COMPLETED,
    // or clean up any already-spawned open sibling if reopening COMPLETED/CANCELED -> OPEN.
    let spawnedTaskId: string | null = null;
    if (
      (!existing || existing.status === 'OPEN') &&
      status === 'COMPLETED' &&
      record.recurrenceRule != null
    ) {
      spawnedTaskId = await this.spawnNextRecurrence(record, now);
    } else if (
      existing &&
      existing.status !== 'OPEN' &&
      status === 'OPEN' &&
      record.recurrenceRule != null
    ) {
      const seriesKey = buildRecurringSeriesKey(record);
      if (seriesKey) {
        const allTasks = (await db.getAll('tasks')) as TaskRecord[];
        const siblingIds = allTasks
          .filter((t) => t.id !== record.id && buildRecurringSeriesKey(t) === seriesKey)
          .map((t) => t.id);
        if (siblingIds.length > 0) {
          await this.silentlyPurgeTasksByIdsUnlocked(db, siblingIds, now);
        }
      }
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
    actualMinutes?: number | null;
    isPinned?: boolean;
    isMilestone?: boolean;
    isInProgress?: boolean;
    effortLevel?: EffortLevel | null;
    blockedByTaskIds?: string[];
    workspaceId?: string | null;
    assigneeId?: string | null;
    projectId?: string | null;
    headingId?: string | null;
    areaId?: string | null;
    recurrenceRule?: RecurrenceRule | null;
    sortOrder?: number;
    tagIds?: string[];
    addTagIds?: string[];
    removeTagIds?: string[];
  }): Promise<TaskRecord> {
    return this.enqueueWrite(async () => {
      const record = await this.upsertTaskUnlocked(params);
      await this.refreshSnapshot();
      this.triggerSync();
      return record;
    });
  }

  async toggleTaskInProgress(taskId: string): Promise<boolean> {
    if (!taskId) return false;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const existing = (await db.get('tasks', taskId)) as TaskRecord | undefined;
      if (!existing || existing.isDeleted || existing.status !== 'OPEN') {
        return false;
      }
      const next = !existing.isInProgress;
      await this.upsertTaskUnlocked({
        id: taskId,
        isInProgress: next,
        suppressUndo: true
      });
      await this.refreshSnapshot();
      this.triggerSync();
      return next;
    });
  }

  async addTaskActualMinutes(taskId: string, deltaMinutes: number): Promise<void> {
    const delta = Math.trunc(Number(deltaMinutes));
    if (!taskId || !Number.isFinite(delta) || delta <= 0) return;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('tasks', 'readwrite');
      const existing = (await tx.store.get(taskId)) as TaskRecord | undefined;
      if (!existing || existing.isDeleted) {
        await tx.done;
        return;
      }
      const prevActual =
        existing.actualMinutes != null &&
        Number.isFinite(existing.actualMinutes) &&
        existing.actualMinutes > 0
          ? Math.trunc(existing.actualMinutes)
          : 0;
      const nextActual = Math.max(1, Math.min(48_000, prevActual + delta));
      await tx.store.put({
        ...existing,
        actualMinutes: nextActual,
        isInProgress: existing.status === 'OPEN',
        updatedAt: Math.max(now, existing.updatedAt + 1),
        isDirty: true
      } satisfies TaskRecord);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
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
      actualMinutes?: number | null;
      isPinned?: boolean;
      isMilestone?: boolean;
      isInProgress?: boolean;
      effortLevel?: EffortLevel | null;
      blockedByTaskIds?: string[];
      workspaceId?: string | null;
      assigneeId?: string | null;
      projectId?: string | null;
      headingId?: string | null;
      areaId?: string | null;
      tagIds?: string[];
      addTagIds?: string[];
      removeTagIds?: string[];
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

  async batchMoveOrReorderTasksInContainer(
    taskIds: string[],
    target: {
      projectId?: string | null;
      headingId?: string | null;
      areaId?: string | null;
      beforeSortOrder?: number | null;
      afterSortOrder?: number | null;
      scheduleState?: ScheduleState;
      whenDate?: number | null;
      scheduledStartMinutes?: number | null;
    }
  ): Promise<void> {
    const uniqueIds = Array.from(new Set(taskIds.map((id) => id.trim()).filter(Boolean)));
    if (uniqueIds.length === 0) return;

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEnd = endOfDayEpoch(new Date(now));
      const tx = db.transaction(['tasks', 'checklist_items'], 'readwrite');
      const taskStore = tx.objectStore('tasks');
      const chkStore = tx.objectStore('checklist_items');

      const allTasks = (await taskStore.getAll()) as TaskRecord[];
      const taskById = new Map(allTasks.map((t) => [t.id, t]));
      const validTasks = uniqueIds
        .map((id) => taskById.get(id))
        .filter((t): t is TaskRecord => t !== undefined && !t.purgedFromTrash);

      if (validTasks.length === 0) {
        await tx.done;
        return;
      }

      const movedIdSet = new Set(validTasks.map((t) => t.id));
      const hasExplicitContainer =
        target.projectId !== undefined ||
        target.headingId !== undefined ||
        target.areaId !== undefined;

      const explicitProjectId = hasExplicitContainer ? (target.projectId || null) : undefined;
      const explicitHeadingId = hasExplicitContainer
        ? explicitProjectId
          ? (target.headingId || null)
          : null
        : undefined;
      const explicitAreaId = hasExplicitContainer
        ? explicitProjectId
          ? null
          : (target.areaId || null)
        : undefined;

      const before =
        target.beforeSortOrder != null && Number.isFinite(target.beforeSortOrder)
          ? target.beforeSortOrder
          : null;
      const after =
        target.afterSortOrder != null && Number.isFinite(target.afterSortOrder)
          ? target.afterSortOrder
          : null;

      const count = validTasks.length;
      let sortOrders: number[];
      if (before != null && after != null) {
        const low = Math.min(before, after);
        const high = Math.max(before, after);
        const span = high - low;
        const step = span > 1e-6 ? span / (count + 1) : 0.001;
        sortOrders = validTasks.map((_, idx) => low + step * (idx + 1));
      } else if (before != null) {
        sortOrders = validTasks.map((_, idx) => before - (count - idx) * 100);
      } else if (after != null) {
        sortOrders = validTasks.map((_, idx) => after + (idx + 1) * 100);
      } else {
        const refProjId = explicitProjectId !== undefined ? explicitProjectId : validTasks[0].projectId;
        const refHeadingId =
          explicitHeadingId !== undefined ? explicitHeadingId : validTasks[0].headingId;
        const refAreaId = explicitAreaId !== undefined ? explicitAreaId : validTasks[0].areaId;
        const siblings = allTasks.filter(
          (s) =>
            !s.isDeleted &&
            !movedIdSet.has(s.id) &&
            (s.projectId ?? null) === (refProjId ?? null) &&
            (s.headingId ?? null) === (refHeadingId ?? null) &&
            (s.areaId ?? null) === (refAreaId ?? null)
        );
        const maxOrder =
          siblings.length > 0
            ? siblings.reduce((m, s) => Math.max(m, s.sortOrder), -Infinity)
            : 900;
        const safeBase = Number.isFinite(maxOrder) ? maxOrder : 900;
        sortOrders = validTasks.map((_, idx) => safeBase + (idx + 1) * 100);
      }

      const priorTasks: TaskRecord[] = [];
      const priorChecklists: ChecklistItemRecord[] = [];
      const hadDeletedTask = validTasks.some((t) => t.isDeleted);
      const allChk = hadDeletedTask
        ? ((await chkStore.getAll()) as ChecklistItemRecord[])
        : [];

      for (let i = 0; i < validTasks.length; i++) {
        const existing = validTasks[i];
        priorTasks.push({ ...existing });

        const nextProjectId =
          explicitProjectId !== undefined ? explicitProjectId : existing.projectId;
        const nextHeadingId =
          explicitHeadingId !== undefined
            ? explicitHeadingId
            : nextProjectId
              ? existing.headingId
              : null;
        const nextAreaId =
          explicitAreaId !== undefined
            ? explicitAreaId
            : nextProjectId
              ? null
              : existing.areaId;

        let nextSchedule: ScheduleState =
          target.scheduleState !== undefined ? target.scheduleState : existing.scheduleState;
        let nextWhen: number | null =
          target.whenDate !== undefined
            ? target.whenDate
            : target.scheduleState !== undefined &&
                (target.scheduleState === 'INBOX' ||
                  target.scheduleState === 'ANYTIME' ||
                  target.scheduleState === 'SOMEDAY')
              ? null
              : existing.whenDate;

        if (nextSchedule === 'INBOX' && nextWhen != null) {
          nextSchedule = nextWhen <= todayEnd ? 'TODAY' : 'UPCOMING';
        }
        if (
          nextSchedule === 'INBOX' &&
          nextWhen == null &&
          existing.recurrenceRule == null &&
          (nextProjectId != null || nextAreaId != null)
        ) {
          nextSchedule = 'ANYTIME';
        }

        const nextScheduledStart =
          target.scheduledStartMinutes !== undefined
            ? target.scheduledStartMinutes
            : existing.scheduledStartMinutes ?? null;

        await taskStore.put({
          ...existing,
          projectId: nextProjectId,
          headingId: nextHeadingId,
          areaId: nextAreaId,
          scheduleState: nextSchedule,
          whenDate: nextWhen,
          scheduledStartMinutes: nextScheduledStart,
          sortOrder: sortOrders[i],
          isDeleted: false,
          purgedFromTrash: false,
          updatedAt: Math.max(now + i, existing.updatedAt + 1),
          isDirty: true
        } satisfies TaskRecord);

        if (existing.isDeleted) {
          for (const chk of allChk) {
            if (chk.taskId === existing.id && chk.isDeleted) {
              priorChecklists.push({ ...chk });
              await chkStore.put({
                ...chk,
                isDeleted: false,
                updatedAt: Math.max(now + i, chk.updatedAt + 1),
                isDirty: true
              });
            }
          }
        }
      }

      await tx.done;

      this.lastUndoSnapshot = {
        tasks: priorTasks,
        checklistItems: priorChecklists,
        projects: [],
        headings: [],
        attachments: [],
        spawnedTaskIds: []
      };
      this.emitUndoEvent({
        kind: 'tasks_updated',
        entityTitle: priorTasks[0].title,
        count: priorTasks.length
      });

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

    const newTaskId = await deriveRecurringNextTaskId(
      completedTask.id,
      formatLocalYyyyMmDd(nextWhen)
    );
    const existingById = (await db.get('tasks', newTaskId)) as TaskRecord | undefined;
    const nextUpdatedAt = existingById ? Math.max(now, existingById.updatedAt + 1) : now;
    const nextTask: TaskRecord = {
      ...completedTask,
      id: newTaskId,
      status: 'OPEN',
      completedAt: null,
      actualMinutes: null,
      scheduleState: nextSchedule,
      whenDate: nextWhen,
      deadline: nextDeadline,
      reminderTime: nextReminder,
      isPinned: false,
      isInProgress: false,
      blockedByTaskIds: [],
      sortOrder: completedTask.sortOrder + 0.01,
      createdAt: existingById?.createdAt ?? now,
      updatedAt: nextUpdatedAt,
      isDeleted: false,
      isDirty: true,
      purgedFromTrash: false
    };

    const allTasks = (await db.getAll('tasks')) as TaskRecord[];
    const otherOpenRecurring = allTasks.filter(
      (t) =>
        t.id !== completedTask.id &&
        t.id !== newTaskId &&
        !t.isDeleted &&
        t.status === 'OPEN' &&
        t.recurrenceRule != null
    );
    const dedupCheck = planRecurringDeduplication([nextTask, ...otherOpenRecurring]);
    const seriesKey = buildRecurringSeriesKey(nextTask);
    const openSiblings = otherOpenRecurring.filter(
      (t) =>
        (seriesKey !== null && buildRecurringSeriesKey(t) === seriesKey) ||
        dedupCheck.loserIds.includes(t.id) ||
        (dedupCheck.loserIds.includes(nextTask.id) &&
          normalizeRecurringTitleKey(t.title) === normalizeRecurringTitleKey(nextTask.title) &&
          canonicalizeRecurrenceRuleKey(t.recurrenceRule) ===
            canonicalizeRecurrenceRuleKey(nextTask.recurrenceRule))
    );

    if (openSiblings.length > 0) {
      const primary = openSiblings[0];
      if (openSiblings.length > 1) {
        await this.silentlyPurgeTasksByIdsUnlocked(
          db,
          openSiblings.slice(1).map((t) => t.id),
          now
        );
      }
      const nextArea = primary.projectId ? null : (primary.areaId || nextTask.areaId || null);
      const nextStartMin = primary.scheduledStartMinutes ?? nextTask.scheduledStartMinutes ?? null;
      const nextDurMin = primary.durationMinutes ?? nextTask.durationMinutes ?? null;
      const needsTaskUpdate =
        (primary.whenDate ?? 0) < nextWhen ||
        primary.areaId !== nextArea ||
        primary.scheduledStartMinutes !== nextStartMin ||
        primary.durationMinutes !== nextDurMin ||
        Boolean(primary.isInProgress);

      if (needsTaskUpdate) {
        await db.put('tasks', {
          ...primary,
          areaId: nextArea,
          whenDate: (primary.whenDate ?? 0) < nextWhen ? nextWhen : primary.whenDate,
          scheduleState: (primary.whenDate ?? 0) < nextWhen ? nextSchedule : primary.scheduleState,
          deadline: (primary.whenDate ?? 0) < nextWhen ? nextDeadline : primary.deadline,
          reminderTime:
            (primary.whenDate ?? 0) < nextWhen ? nextReminder : primary.reminderTime,
          scheduledStartMinutes: nextStartMin,
          durationMinutes: nextDurMin,
          actualMinutes: (primary.whenDate ?? 0) < nextWhen ? null : (primary.actualMinutes ?? null),
          isInProgress: false,
          updatedAt: Math.max(now, primary.updatedAt + 1),
          isDirty: true
        });
      }

      const allChk = (await db.getAll('checklist_items')) as ChecklistItemRecord[];
      for (const chk of allChk) {
        if (chk.taskId === primary.id && !chk.isDeleted && chk.isCompleted) {
          await db.put('checklist_items', {
            ...chk,
            isCompleted: false,
            updatedAt: Math.max(now, chk.updatedAt + 1),
            isDirty: true
          });
        }
      }
      return primary.id;
    }

    await db.put('tasks', nextTask);

    const allChecklists = (await db.getAll('checklist_items')) as ChecklistItemRecord[];
    const sourceItems = allChecklists.filter(
      (c) => c.taskId === completedTask.id && !c.isDeleted
    );
    for (const item of sourceItems) {
      const nextChecklistId = await deriveRecurringChecklistItemId(newTaskId, item.id);
      const existingChk = (await db.get('checklist_items', nextChecklistId)) as
        | ChecklistItemRecord
        | undefined;
      await db.put('checklist_items', {
        ...item,
        id: nextChecklistId,
        taskId: newTaskId,
        isCompleted: false,
        createdAt: existingChk?.createdAt ?? now,
        updatedAt: existingChk ? Math.max(now, existingChk.updatedAt + 1) : now,
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

      if (
        activeProj &&
        activeProj.isDeleted &&
        Array.isArray(activeProj.deletedTaskIds) &&
        activeProj.deletedTaskIds.includes(taskId)
      ) {
        await tx.objectStore('projects').put({
          ...activeProj,
          deletedTaskIds: activeProj.deletedTaskIds.filter((id) => id !== taskId)
        });
      }

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
      const tx = db.transaction(
        ['projects', 'headings', 'areas', 'tasks', 'checklist_items'],
        'readwrite'
      );
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

      const restoredTaskIds = new Set<string>();
      if (Array.isArray(existing.deletedTaskIds)) {
        for (const tid of existing.deletedTaskIds) {
          const curTask = (await tx.objectStore('tasks').get(tid)) as TaskRecord | undefined;
          if (
            curTask &&
            curTask.isDeleted &&
            !curTask.purgedFromTrash &&
            curTask.projectId === projectId
          ) {
            const restoredHeadingId =
              curTask.headingId && validHeadingIds.has(curTask.headingId)
                ? curTask.headingId
                : null;
            await tx.objectStore('tasks').put({
              ...curTask,
              projectId,
              headingId: restoredHeadingId,
              areaId: null,
              isDeleted: false,
              purgedFromTrash: false,
              updatedAt: now,
              isDirty: true
            });
            restoredTaskIds.add(curTask.id);
          }
        }
      } else if (!Array.isArray(existing.detachedTaskSnapshots)) {
        const allTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
        for (const curTask of allTasks) {
          if (
            curTask.projectId === projectId &&
            curTask.isDeleted &&
            !curTask.purgedFromTrash
          ) {
            const restoredHeadingId =
              curTask.headingId && validHeadingIds.has(curTask.headingId)
                ? curTask.headingId
                : null;
            await tx.objectStore('tasks').put({
              ...curTask,
              projectId,
              headingId: restoredHeadingId,
              areaId: null,
              isDeleted: false,
              purgedFromTrash: false,
              updatedAt: now,
              isDirty: true
            });
            restoredTaskIds.add(curTask.id);
          }
        }
      }

      if (restoredTaskIds.size > 0) {
        const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
        for (const chk of allChk) {
          if (restoredTaskIds.has(chk.taskId) && chk.isDeleted) {
            await tx.objectStore('checklist_items').put({
              ...chk,
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
        deletedTaskIds: undefined,
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
          actualMinutes: null,
          isInProgress: false,
          recurrenceRule: null,
          tagIds: [],
          projectId: null,
          headingId: null,
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
      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items', 'attachments'],
        'readwrite'
      );
      const purgedTaskIds = new Set<string>();
      const allTasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
      for (const t of allTasks) {
        if (t.projectId === projectId) {
          if (t.isDeleted) {
            purgedTaskIds.add(t.id);
            await tx.objectStore('tasks').put({
              ...t,
              title: '',
              notes: '',
              reminderTime: null,
              scheduledStartMinutes: null,
              durationMinutes: null,
              actualMinutes: null,
              isInProgress: false,
              recurrenceRule: null,
              tagIds: [],
              projectId: null,
              headingId: null,
              isDeleted: true,
              purgedFromTrash: true,
              updatedAt: now,
              isDirty: true
            });
          } else {
            await tx.objectStore('tasks').put({
              ...t,
              projectId: null,
              headingId: null,
              updatedAt: now,
              isDirty: true
            });
          }
        }
      }
      if (purgedTaskIds.size > 0) {
        const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
        for (const chk of allChk) {
          if (purgedTaskIds.has(chk.taskId)) {
            await tx.objectStore('checklist_items').put({
              ...chk,
              title: '',
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
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
        const matchesProject = att.projectId === projectId;
        const matchesTask = Boolean(att.taskId && purgedTaskIds.has(att.taskId));
        if (matchesProject || matchesTask) {
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
          deletedTaskIds: undefined,
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
            actualMinutes: null,
            isInProgress: false,
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
            lastReviewedAt: null,
            tagIds: [],
            detachedTaskSnapshots: undefined,
            deletedTaskIds: undefined,
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
    const ids = await this.batchDuplicateTasks([taskId], copySuffix);
    return ids[0] ?? null;
  }

  async batchDuplicateTasks(taskIds: string[], copySuffix = 'Copy'): Promise<string[]> {
    const uniqueIds = Array.from(new Set(taskIds.filter(Boolean)));
    if (uniqueIds.length === 0) return [];

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['tasks', 'checklist_items'], 'readwrite');
      const taskStore = tx.objectStore('tasks');
      const chkStore = tx.objectStore('checklist_items');

      const allTasks = (await taskStore.getAll()) as TaskRecord[];
      const taskById = new Map(allTasks.map((t) => [t.id, t]));
      const allChk = (await chkStore.getAll()) as ChecklistItemRecord[];

      const validSources = uniqueIds
        .map((id) => taskById.get(id))
        .filter((t): t is TaskRecord => Boolean(t && !t.isDeleted));
      if (validSources.length === 0) {
        await tx.done;
        return [];
      }

      validSources.sort((a, b) => {
        if (Boolean(b.isPinned) !== Boolean(a.isPinned)) {
          return a.isPinned ? -1 : 1;
        }
        return a.sortOrder - b.sortOrder || a.createdAt - b.createdAt;
      });

      const bucketKeyOf = (t: TaskRecord): string =>
        `${t.projectId ?? ''}\u001F${t.headingId ?? ''}\u001F${t.areaId ?? ''}\u001F${t.isPinned ? '1' : '0'}`;

      const sourcesByBucket = new Map<string, TaskRecord[]>();
      for (const src of validSources) {
        const bKey = bucketKeyOf(src);
        const list = sourcesByBucket.get(bKey);
        if (list) list.push(src);
        else sourcesByBucket.set(bKey, [src]);
      }

      const copySortOrderById = new Map<string, number>();
      for (const [bKey, group] of sourcesByBucket.entries()) {
        const lastSourceOrder = group.reduce((m, x) => Math.max(m, x.sortOrder), -Infinity);
        let nextCeiling: number | null = null;
        for (const candidate of allTasks) {
          if (candidate.isDeleted) continue;
          if (bucketKeyOf(candidate) !== bKey) continue;
          if (candidate.sortOrder > lastSourceOrder) {
            if (nextCeiling === null || candidate.sortOrder < nextCeiling) {
              nextCeiling = candidate.sortOrder;
            }
          }
        }
        const count = group.length;
        const rawStep =
          nextCeiling !== null ? (nextCeiling - lastSourceOrder) / (count + 1) : 10.0;
        const step = rawStep > 1e-7 ? rawStep : 0.0001;
        for (let i = 0; i < count; i++) {
          copySortOrderById.set(group[i].id, lastSourceOrder + step * (i + 1));
        }
      }

      const createdIds: string[] = [];
      for (let i = 0; i < validSources.length; i++) {
        const src = validSources[i];
        const newId = crypto.randomUUID();
        const trimmed = src.title.trim();
        const newTitle = trimmed ? `${trimmed} (${copySuffix})` : src.title;
        const copyTs = now + i;
        await taskStore.put({
          ...src,
          id: newId,
          title: newTitle,
          status: 'OPEN',
          completedAt: null,
          actualMinutes: null,
          reminderTime: src.reminderTime != null && src.reminderTime > now ? src.reminderTime : null,
          isPinned: Boolean(src.isPinned),
          isMilestone: Boolean(src.isMilestone),
          isInProgress: false,
          effortLevel: src.effortLevel ?? null,
          blockedByTaskIds: safeParseBlockedByTaskIds(src.blockedByTaskIds, newId),
          workspaceId: src.workspaceId ?? null,
          assigneeId: src.assigneeId ?? null,
          sortOrder: copySortOrderById.get(src.id) ?? src.sortOrder + (i + 1) * 10,
          createdAt: copyTs,
          updatedAt: copyTs,
          isDeleted: false,
          purgedFromTrash: false,
          isDirty: true
        } satisfies TaskRecord);

        const srcChecklists = allChk
          .filter((c) => c.taskId === src.id && !c.isDeleted)
          .sort((a, b) => a.sortOrder - b.sortOrder);
        for (const c of srcChecklists) {
          await chkStore.put({
            ...c,
            id: crypto.randomUUID(),
            taskId: newId,
            isCompleted: false,
            createdAt: copyTs,
            updatedAt: copyTs,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }

        createdIds.push(newId);
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return createdIds;
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
      const now = Date.now();
      const tx = db.transaction(['checklist_items', 'tasks'], 'readwrite');
      const chkStore = tx.objectStore('checklist_items');
      const existing = (await chkStore.get(itemId)) as ChecklistItemRecord | undefined;
      if (!existing) {
        await tx.done;
        return;
      }
      await chkStore.put({
        ...existing,
        ...patch,
        updatedAt: now,
        isDirty: true
      });
      if (!existing.isCompleted && patch.isCompleted === true) {
        const taskStore = tx.objectStore('tasks');
        const task = (await taskStore.get(existing.taskId)) as TaskRecord | undefined;
        if (task && !task.isDeleted && task.status === 'OPEN' && !task.isInProgress) {
          await taskStore.put({
            ...task,
            isInProgress: true,
            updatedAt: Math.max(now, task.updatedAt + 1),
            isDirty: true
          });
        }
      }
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
    workspaceId?: string | null;
    viewMode?: ProjectViewMode | null;
    sortOrder?: number;
    immediateSync?: boolean;
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
          ? params.lastReviewedAt !== null
            ? mergeMonotonicReviewTimestamp(existing?.lastReviewedAt, params.lastReviewedAt)
            : null
          : existing
            ? (existing.lastReviewedAt ?? null)
            : now;
      const nextUpdatedAt = existing
        ? Math.max(existing.updatedAt + 1, now, lastReviewedAt ?? 0)
        : now;
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
        workspaceId:
          params.workspaceId !== undefined
            ? params.workspaceId?.trim() || null
            : (existing?.workspaceId ?? null),
        viewMode:
          params.viewMode !== undefined
            ? normalizeProjectViewMode(params.viewMode)
            : (existing?.viewMode ?? null),
        createdAt: existing?.createdAt ?? now,
        updatedAt: nextUpdatedAt,
        isDeleted: false,
        isDirty: true
      };
      await tx.store.put(record);
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync(Boolean(params.immediateSync));
      return record;
    });
  }

  async setProjectViewMode(projectId: string, viewMode: ProjectViewMode): Promise<void> {
    await this.upsertProject({
      id: projectId,
      viewMode: normalizeProjectViewMode(viewMode) ?? 'list'
    });
  }

  async markProjectReviewed(projectId: string, reviewedAt: number = Date.now()): Promise<void> {
    await this.upsertProject({
      id: projectId,
      lastReviewedAt: reviewedAt,
      immediateSync: true
    });
  }

  async markAllActiveProjectsReviewed(reviewedAt: number = Date.now()): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const tx = db.transaction('projects', 'readwrite');
      const allProjects = (await tx.store.getAll()) as ProjectRecord[];
      let changed = false;
      for (const existing of allProjects) {
        if (existing.isDeleted || existing.status !== 'ACTIVE') continue;
        const nextReviewedAt =
          mergeMonotonicReviewTimestamp(existing.lastReviewedAt, reviewedAt) ?? reviewedAt;
        const nextUpdatedAt = Math.max(existing.updatedAt + 1, reviewedAt, nextReviewedAt);
        await tx.store.put({
          ...existing,
          lastReviewedAt: nextReviewedAt,
          updatedAt: nextUpdatedAt,
          isDirty: true
        });
        changed = true;
      }
      await tx.done;
      if (changed) {
        await this.refreshSnapshot();
        this.triggerSync(true);
      }
    });
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
          isMilestone: Boolean(bpTask.isMilestone),
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
        const phaseEndDate =
          h.endDateDayOffset != null && Number.isFinite(h.endDateDayOffset)
            ? addDaysLocal(todayEpoch, Math.max(0, Math.trunc(h.endDateDayOffset)))
            : null;
        await tx.objectStore('headings').put({
          id: headingId,
          projectId,
          title: h.title.trim() || `Phase ${hIdx + 1}`,
          endDate: phaseEndDate,
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
   * v3.1 AI Roadmap Architect: Instantiates a full v3 Gantt project (`viewMode: 'roadmap'`)
   * with dated headings, tasks (`whenDate`, milestone `deadline`, `durationMinutes`, `effortLevel`,
   * `isMilestone`, cycle-safe `blockedByTaskIds` resolved from `dependsOnKeys`), and checklist items.
   */
  async createV3RoadmapProjectWithBlueprint(
    blueprint: ProjectBlueprintResponse,
    areaId: string | null = null
  ): Promise<string> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEpoch = startOfDayEpoch(new Date(now));
      const projectId = crypto.randomUUID();
      const validAreaId = areaId && areaId.trim().length > 0 ? areaId.trim() : null;

      const dayOffsetMillis = (offsetDays: number): number =>
        addDaysLocal(todayEpoch, Math.max(0, Math.min(365, Math.trunc(offsetDays))));

      let maxEndOffset = 10;
      for (const h of blueprint.headings) {
        if (h.endDayOffset != null && Number.isFinite(h.endDayOffset)) {
          maxEndOffset = Math.max(maxEndOffset, Math.trunc(h.endDayOffset));
        } else if (Array.isArray(h.richTasks) && h.richTasks.length > 0) {
          for (const rt of h.richTasks) {
            maxEndOffset = Math.max(maxEndOffset, Math.trunc(rt.dayOffset || 0));
          }
        }
      }

      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items'],
        'readwrite'
      );

      await tx.objectStore('projects').put({
        id: projectId,
        areaId: validAreaId,
        title: blueprint.projectTitle.trim() || 'New Project',
        notes: blueprint.projectNotes || '',
        status: 'ACTIVE',
        scheduleState: 'TODAY',
        whenDate: todayEpoch,
        deadline: dayOffsetMillis(Math.max(1, maxEndOffset)),
        completedAt: null,
        lastReviewedAt: now,
        sortOrder: now,
        tagIds: [],
        viewMode: 'roadmap',
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies ProjectRecord);

      const taskIdByKey = new Map<string, string>();
      interface PendingBlueprintTask {
        id: string;
        headingId: string;
        hIdx: number;
        tIdx: number;
        dto: RoadmapTaskBlueprintDto;
      }
      const pendingTasks: PendingBlueprintTask[] = [];

      for (let hIdx = 0; hIdx < blueprint.headings.length; hIdx++) {
        const h = blueprint.headings[hIdx];
        const headingId = crypto.randomUUID();
        const richList: RoadmapTaskBlueprintDto[] =
          Array.isArray(h.richTasks) && h.richTasks.length > 0
            ? h.richTasks.filter((rt) => rt.title && rt.title.trim().length > 0)
            : (h.tasks || [])
                .map((t) => t.trim())
                .filter(Boolean)
                .map((tTitle, tIdx, arr) => {
                  const isMs =
                    hIdx === blueprint.headings.length - 1 && tIdx === arr.length - 1;
                  return {
                    key: `h${hIdx}_t${tIdx}`,
                    title: tTitle,
                    notes: '',
                    dayOffset: hIdx * 4 + tIdx,
                    durationMinutes: isMs ? 30 : 120,
                    effortLevel: isMs ? 'QUICK_WIN' : tIdx === 0 ? 'DEEP_FOCUS' : 'MEDIUM',
                    isMilestone: isMs,
                    dependsOnKeys: tIdx > 0 ? [`h${hIdx}_t${tIdx - 1}`] : [],
                    checklist: []
                  };
                });

        const minChildDay =
          richList.length > 0
            ? richList.reduce((m, r) => Math.min(m, r.dayOffset), Infinity)
            : hIdx * 4;
        const maxChildDay =
          richList.length > 0
            ? richList.reduce(
                (m, r) =>
                  Math.max(
                    m,
                    r.dayOffset + Math.max(0, Math.ceil((r.durationMinutes || 30) / 480) - 1)
                  ),
                -Infinity
              )
            : minChildDay + 3;

        const startOffset =
          h.startDayOffset != null && Number.isFinite(h.startDayOffset)
            ? Math.max(0, Math.trunc(h.startDayOffset))
            : minChildDay;
        const endOffset = Math.max(
          startOffset,
          h.endDayOffset != null && Number.isFinite(h.endDayOffset)
            ? Math.trunc(h.endDayOffset)
            : maxChildDay
        );

        await tx.objectStore('headings').put({
          id: headingId,
          projectId,
          title: h.title.trim() || `Phase ${hIdx + 1}`,
          startDate: dayOffsetMillis(startOffset),
          endDate: dayOffsetMillis(endOffset),
          sortOrder: (hIdx + 1) * 1000,
          isArchived: false,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        } satisfies HeadingRecord);

        for (let tIdx = 0; tIdx < richList.length; tIdx++) {
          const dto = richList[tIdx];
          const taskId = crypto.randomUUID();
          const cleanKey = (dto.key || '').trim();
          if (cleanKey) {
            taskIdByKey.set(cleanKey, taskId);
          }
          pendingTasks.push({
            id: taskId,
            headingId,
            hIdx,
            tIdx,
            dto
          });
        }
      }

      const blockedByMap = new Map<string, string[]>();
      for (let i = 0; i < pendingTasks.length; i++) {
        const pt = pendingTasks[i];
        const r = pt.dto;
        const cleanTitle = r.title.trim();
        if (!cleanTitle) continue;

        const safeDayOffset = Math.max(0, Math.min(365, Math.trunc(r.dayOffset || 0)));
        const itemMillis = dayOffsetMillis(safeDayOffset);
        const scheduleState: ScheduleState = safeDayOffset <= 0 ? 'TODAY' : 'UPCOMING';
        const isMs = Boolean(r.isMilestone);
        const safeDur = isMs
          ? 30
          : Math.max(5, Math.min(48_000, Math.trunc(r.durationMinutes || 60)));
        const normEffort = normalizeEffortLevel(r.effortLevel);

        const resolvedBlockers: string[] = [];
        for (const rawKey of r.dependsOnKeys ?? []) {
          const mappedId = taskIdByKey.get(String(rawKey || '').trim());
          if (!mappedId || mappedId === pt.id || resolvedBlockers.includes(mappedId)) continue;
          if (!wouldCreateDependencyCycle(pt.id, mappedId, blockedByMap)) {
            resolvedBlockers.push(mappedId);
            blockedByMap.set(pt.id, resolvedBlockers);
          }
        }
        blockedByMap.set(pt.id, resolvedBlockers);

        await tx.objectStore('tasks').put({
          id: pt.id,
          title: cleanTitle,
          notes: (r.notes || '').trim(),
          status: 'OPEN',
          scheduleState,
          whenDate: itemMillis,
          deadline: isMs ? itemMillis : null,
          reminderTime: null,
          durationMinutes: safeDur,
          isPinned: false,
          isMilestone: isMs,
          effortLevel: normEffort,
          blockedByTaskIds: resolvedBlockers,
          completedAt: null,
          projectId,
          headingId: pt.headingId,
          areaId: null,
          recurrenceRule: null,
          sortOrder: (pt.hIdx + 1) * 1000 + (pt.tIdx + 1) * 100,
          tagIds: [],
          createdAt: now,
          updatedAt: now + i,
          isDeleted: false,
          isDirty: true,
          purgedFromTrash: false
        } satisfies TaskRecord);

        const checklist = Array.isArray(r.checklist) ? r.checklist : [];
        for (let cIdx = 0; cIdx < checklist.length; cIdx++) {
          const cTitle = String(checklist[cIdx] || '').trim();
          if (!cTitle) continue;
          await tx.objectStore('checklist_items').put({
            id: crypto.randomUUID(),
            taskId: pt.id,
            title: cTitle,
            isCompleted: false,
            sortOrder: (cIdx + 1) * 100,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return projectId;
    });
  }

  /**
   * v3.1 Smart Push-on-Conflict Auto-Cascading Reschedule with atomic Undo snapshot.
   */
  async rescheduleRoadmapTaskWithCascade(
    task: TaskRecord,
    deltaDays: number,
    todayEpoch?: number
  ): Promise<{ shiftedCount: number; cascadedCount: number }> {
    const step = Math.trunc(deltaDays);
    if (!Number.isFinite(step) || step === 0) {
      return { shiftedCount: 0, cascadedCount: 0 };
    }

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const effectiveToday = todayEpoch ?? startOfDayEpoch(new Date(now));
      const tx = db.transaction('tasks', 'readwrite');
      const allTasks = (await tx.store.getAll()) as TaskRecord[];
      const liveAnchor = allTasks.find((t) => t.id === task.id && !t.isDeleted) ?? task;
      const scopeTasks = liveAnchor.projectId
        ? allTasks.filter((t) => !t.isDeleted && t.projectId === liveAnchor.projectId)
        : allTasks.filter((t) => !t.isDeleted);

      const shifts = computeSmartDependencyCascade(
        liveAnchor,
        step,
        scopeTasks,
        effectiveToday
      );

      if (shifts.length === 0) {
        await tx.done;
        return { shiftedCount: 0, cascadedCount: 0 };
      }

      const taskById = new Map(allTasks.map((t) => [t.id, t]));
      const priorTasks: TaskRecord[] = [];
      let idx = 0;
      for (const s of shifts) {
        const existing = taskById.get(s.task.id);
        if (!existing || existing.isDeleted) continue;
        priorTasks.push({ ...existing });
        await tx.store.put({
          ...existing,
          whenDate: s.whenDate,
          deadline: s.deadline,
          scheduleState: s.scheduleState,
          updatedAt: Math.max(now + idx, existing.updatedAt + 1),
          isDirty: true
        } satisfies TaskRecord);
        idx++;
      }

      await tx.done;

      const cascadedCount = shifts.filter((s) => !s.isAnchor).length;
      if (priorTasks.length > 0) {
        this.lastUndoSnapshot = {
          tasks: priorTasks,
          checklistItems: [],
          projects: [],
          headings: [],
          attachments: [],
          spawnedTaskIds: []
        };
        this.emitUndoEvent({
          kind: cascadedCount > 0 ? 'roadmap_cascade' : 'roadmap_rescheduled',
          entityTitle: liveAnchor.title,
          count: cascadedCount > 0 ? cascadedCount : 1
        });
      }

      await this.refreshSnapshot();
      this.triggerSync();
      return { shiftedCount: priorTasks.length, cascadedCount };
    });
  }

  /**
   * v3.1 Gantt Phase (Heading) reschedule with atomic Undo snapshot.
   */
  async rescheduleRoadmapHeading(
    headingId: string,
    nextStart: number | null,
    nextEnd: number | null,
    fallbackTitle: string = ''
  ): Promise<boolean> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(headingId)) as HeadingRecord | undefined;
      if (!existing || existing.isDeleted) {
        await tx.done;
        return false;
      }

      const clampedEnd =
        nextStart != null && nextEnd != null && nextEnd < nextStart ? nextStart : nextEnd;

      this.lastUndoSnapshot = {
        tasks: [],
        checklistItems: [],
        projects: [],
        headings: [{ ...existing }],
        attachments: [],
        spawnedTaskIds: []
      };

      await tx.store.put({
        ...existing,
        startDate: nextStart,
        endDate: clampedEnd,
        updatedAt: Math.max(now, existing.updatedAt + 1),
        isDirty: true
      } satisfies HeadingRecord);

      await tx.done;

      this.emitUndoEvent({
        kind: 'roadmap_rescheduled',
        entityTitle: existing.title || fallbackTitle,
        count: 1
      });

      await this.refreshSnapshot();
      this.triggerSync();
      return true;
    });
  }

  /**
   * v3.1 "Plan My Day" batch timeblock application with atomic Undo snapshot.
   */
  async applyBatchTaskTimeblocks(
    updates: ReadonlyArray<{
      taskId: string;
      scheduledStartMinutes: number | null;
      durationMinutes?: number | null;
    }>
  ): Promise<number> {
    if (updates.length === 0) return 0;

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEpoch = startOfDayEpoch(new Date(now));
      const tx = db.transaction('tasks', 'readwrite');
      const priorTasks: TaskRecord[] = [];

      for (let i = 0; i < updates.length; i++) {
        const u = updates[i];
        const existing = (await tx.store.get(u.taskId)) as TaskRecord | undefined;
        if (!existing || existing.isDeleted) continue;

        priorTasks.push({ ...existing });

        const cleanStart =
          u.scheduledStartMinutes != null && Number.isFinite(u.scheduledStartMinutes)
            ? Math.max(0, Math.min(1439, Math.trunc(u.scheduledStartMinutes)))
            : null;
        const cleanDur =
          u.durationMinutes !== undefined
            ? u.durationMinutes != null &&
              Number.isFinite(u.durationMinutes) &&
              u.durationMinutes > 0
              ? Math.max(5, Math.min(48_000, Math.trunc(u.durationMinutes)))
              : null
            : (existing.durationMinutes ?? null);

        const nextSchedule: ScheduleState =
          cleanStart != null &&
          (existing.scheduleState === 'INBOX' ||
            existing.scheduleState === 'ANYTIME' ||
            existing.scheduleState === 'SOMEDAY')
            ? 'TODAY'
            : existing.scheduleState;
        const nextWhen =
          cleanStart != null && existing.whenDate == null ? todayEpoch : existing.whenDate;

        await tx.store.put({
          ...existing,
          scheduleState: nextSchedule,
          whenDate: nextWhen,
          scheduledStartMinutes: cleanStart,
          durationMinutes: cleanDur,
          updatedAt: Math.max(now + i, existing.updatedAt + 1),
          isDirty: true
        } satisfies TaskRecord);
      }

      await tx.done;

      if (priorTasks.length > 0) {
        this.lastUndoSnapshot = {
          tasks: priorTasks,
          checklistItems: [],
          projects: [],
          headings: [],
          attachments: [],
          spawnedTaskIds: []
        };
        this.emitUndoEvent({
          kind: 'day_plan_applied',
          entityTitle: priorTasks[0].title,
          count: priorTasks.length
        });
      }

      await this.refreshSnapshot();
      this.triggerSync();
      return priorTasks.length;
    });
  }

  /**
   * Soft-deletes a project and its headings.
   * - When `deleteAssociatedTasks === true` (default): cascade soft-deletes all non-deleted tasks
   *   in the project (keeping `projectId` & `headingId` intact), their checklists, and attachments.
   * - When `deleteAssociatedTasks === false`: detaches non-deleted tasks (`projectId: null, headingId: null`,
   *   inheriting `proj.areaId`) and records `detachedTaskSnapshots` for Undo / Restore.
   */
  async deleteProject(projectId: string, deleteAssociatedTasks: boolean = true): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(
        ['projects', 'headings', 'tasks', 'checklist_items', 'attachments'],
        'readwrite'
      );
      const proj = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      const prevProjects: ProjectRecord[] = [];
      const prevHeadings: HeadingRecord[] = [];
      const prevTasks: TaskRecord[] = [];
      const prevChecklists: ChecklistItemRecord[] = [];
      const prevAttachments: AttachmentRecord[] = [];
      const detachedTaskSnapshots: DetachedProjectTaskSnapshot[] = [];
      const deletedTaskIds: string[] = [];

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
          if (deleteAssociatedTasks) {
            deletedTaskIds.push(t.id);
            await tx.objectStore('tasks').put({
              ...t,
              isDeleted: true,
              purgedFromTrash: false,
              updatedAt: now,
              isDirty: true
            });
          } else {
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
      }

      const deletedTaskIdSet = new Set(deletedTaskIds);
      if (deleteAssociatedTasks && deletedTaskIdSet.size > 0) {
        const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
        for (const chk of allChk) {
          if (deletedTaskIdSet.has(chk.taskId) && !chk.isDeleted) {
            prevChecklists.push({ ...chk });
            await tx.objectStore('checklist_items').put({
              ...chk,
              isDeleted: true,
              updatedAt: now,
              isDirty: true
            });
          }
        }
      }

      const allAtt = (await tx.objectStore('attachments').getAll()) as AttachmentRecord[];
      for (const att of allAtt) {
        const matchesProject = att.projectId === projectId;
        const matchesDeletedTask =
          deleteAssociatedTasks && Boolean(att.taskId && deletedTaskIdSet.has(att.taskId));
        if ((matchesProject || matchesDeletedTask) && !att.isDeleted) {
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
          detachedTaskSnapshots: deleteAssociatedTasks ? undefined : detachedTaskSnapshots,
          deletedTaskIds: deleteAssociatedTasks ? deletedTaskIds : undefined,
          updatedAt: now,
          isDirty: true
        });
      }

      await tx.done;

      if (prevProjects.length > 0) {
        this.lastUndoSnapshot = {
          tasks: prevTasks,
          checklistItems: prevChecklists,
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
   * Completes a Project with atomic Undo snapshot (optionally completing remaining OPEN non-deleted tasks).
   */
  async completeProjectWithUndo(
    projectId: string,
    completeOpenTasks: boolean = false
  ): Promise<boolean> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['projects', 'tasks'], 'readwrite');
      const proj = (await tx.objectStore('projects').get(projectId)) as ProjectRecord | undefined;
      if (!proj || proj.isDeleted) {
        await tx.done;
        return false;
      }

      const prevTasks: TaskRecord[] = [];
      if (completeOpenTasks) {
        const tasks = (await tx.objectStore('tasks').getAll()) as TaskRecord[];
        for (const t of tasks) {
          if (t.projectId === projectId && !t.isDeleted && t.status === 'OPEN') {
            prevTasks.push({ ...t });
            await tx.objectStore('tasks').put({
              ...t,
              status: 'COMPLETED',
              completedAt: now,
              isInProgress: false,
              updatedAt: Math.max(now, t.updatedAt + 1),
              isDirty: true
            } satisfies TaskRecord);
          }
        }
      }

      const prevProject: ProjectRecord = { ...proj };
      await tx.objectStore('projects').put({
        ...proj,
        status: 'COMPLETED',
        completedAt: now,
        updatedAt: Math.max(now, proj.updatedAt + 1),
        isDirty: true
      } satisfies ProjectRecord);

      await tx.done;

      this.lastUndoSnapshot = {
        tasks: prevTasks,
        checklistItems: [],
        projects: [prevProject],
        headings: [],
        attachments: [],
        spawnedTaskIds: []
      };
      this.emitUndoEvent({
        kind: 'project_completed',
        entityTitle: prevProject.title,
        count: 1 + prevTasks.length
      });

      await this.refreshSnapshot();
      this.triggerSync();
      return true;
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
            actualMinutes: null,
            reminderTime: null,
            isMilestone: Boolean(t.isMilestone),
            isInProgress: false,
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
    startDate?: number | null;
    endDate?: number | null;
    sortOrder?: number;
  }): Promise<HeadingRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const id = params.id || crypto.randomUUID();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(id)) as HeadingRecord | undefined;
      const resolvedStartDate =
        params.startDate !== undefined ? params.startDate : (existing?.startDate ?? null);
      const resolvedEndDate =
        params.endDate !== undefined ? params.endDate : (existing?.endDate ?? null);
      const record: HeadingRecord = {
        id,
        projectId: params.projectId,
        title: params.title,
        startDate: resolvedStartDate,
        endDate: resolvedEndDate,
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

  async updateHeadingStartDate(headingId: string, startDate: number | null): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(headingId)) as HeadingRecord | undefined;
      if (!existing || existing.isDeleted) {
        await tx.done;
        return;
      }
      await tx.store.put({
        ...existing,
        startDate,
        updatedAt: now,
        isDirty: true
      });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async updateHeadingEndDate(headingId: string, endDate: number | null): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(headingId)) as HeadingRecord | undefined;
      if (!existing || existing.isDeleted) {
        await tx.done;
        return;
      }
      await tx.store.put({
        ...existing,
        endDate,
        updatedAt: now,
        isDirty: true
      });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async updateHeadingDates(
    headingId: string,
    startDate: number | null,
    endDate: number | null
  ): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('headings', 'readwrite');
      const existing = (await tx.store.get(headingId)) as HeadingRecord | undefined;
      if (!existing || existing.isDeleted) {
        await tx.done;
        return;
      }
      await tx.store.put({
        ...existing,
        startDate,
        endDate,
        updatedAt: Math.max(now, existing.updatedAt + 1),
        isDirty: true
      });
      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
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

  /**
   * Atomically creates a real Phase (Heading) at the top of `projectId` (before the first existing heading)
   * and assigns all open ungrouped tasks in that project into the newly created Heading.
   */
  async promoteUngroupedTasksToHeading(
    projectId: string,
    title: string
  ): Promise<HeadingRecord | null> {
    const cleanTitle = title.trim();
    if (!projectId || !cleanTitle) return null;

    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction(['headings', 'tasks'], 'readwrite');
      const headingStore = tx.objectStore('headings');
      const taskStore = tx.objectStore('tasks');

      const allHeadings = (await headingStore.getAll()) as HeadingRecord[];
      const projectActiveHeadings = allHeadings.filter(
        (h) => h.projectId === projectId && !h.isDeleted && !h.isArchived
      );
      const validHeadingIds = new Set(projectActiveHeadings.map((h) => h.id));
      const minOrder =
        projectActiveHeadings.length > 0
          ? projectActiveHeadings.reduce((m, h) => Math.min(m, h.sortOrder), Infinity)
          : null;
      const sortOrder = computeMidpointSortOrder(
        null,
        minOrder != null && Number.isFinite(minOrder) ? minOrder : null
      );

      const newHeading: HeadingRecord = {
        id: crypto.randomUUID(),
        projectId,
        title: cleanTitle,
        startDate: null,
        endDate: null,
        sortOrder,
        isArchived: false,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      };
      await headingStore.put(newHeading);

      const allTasks = (await taskStore.getAll()) as TaskRecord[];
      for (const t of allTasks) {
        if (
          t.projectId === projectId &&
          !t.isDeleted &&
          t.status === 'OPEN' &&
          (t.headingId == null || !validHeadingIds.has(t.headingId))
        ) {
          await taskStore.put({
            ...t,
            headingId: newHeading.id,
            updatedAt: Math.max(now, t.updatedAt + 1),
            isDirty: true
          });
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
      return newHeading;
    });
  }

  async upsertArea(params: { id?: string; title: string; sortOrder?: number; tagIds?: string[] }): Promise<AreaRecord | null> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const trimmedTitle = params.title.trim();
      const tx = db.transaction('areas', 'readwrite');
      const allAreas = (await tx.store.getAll()) as AreaRecord[];
      const sameTitleActive = !params.id
        ? allAreas.find(
            (a) => !a.isDeleted && a.title.trim().toLowerCase() === trimmedTitle.toLowerCase()
          )
        : undefined;
      const id = params.id || sameTitleActive?.id || crypto.randomUUID();
      const existing =
        sameTitleActive ?? ((await tx.store.get(id)) as AreaRecord | undefined);
      if (!existing) {
        const activeCount = allAreas.filter((a) => !a.isDeleted).length;
        if (activeCount >= MAX_AREAS) {
          await tx.done;
          return null;
        }
      }
      const record: AreaRecord = {
        id,
        title: trimmedTitle,
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

  private async mergeTagsUnlocked(
    db: IDBPDatabase,
    sourceTagId: string,
    targetTagId: string,
    now = Date.now()
  ): Promise<TagRecord | null> {
    if (!sourceTagId || !targetTagId || sourceTagId === targetTagId) return null;
    const tx = db.transaction(
      ['tags', 'tasks', 'projects', 'areas', 'projectTemplates'],
      'readwrite'
    );
    const tagStore = tx.objectStore('tags');
    const source = (await tagStore.get(sourceTagId)) as TagRecord | undefined;
    const target = (await tagStore.get(targetTagId)) as TagRecord | undefined;
    if (!source || source.isDeleted || !target || target.isDeleted) {
      await tx.done;
      return null;
    }

    const remapTagList = (ids: string[] | undefined): { next: string[]; changed: boolean } => {
      const raw = ids || [];
      let changed = false;
      const out: string[] = [];
      for (const id of raw) {
        const mapped = id === sourceTagId ? targetTagId : id;
        if (mapped !== id) changed = true;
        if (!out.includes(mapped)) {
          out.push(mapped);
        } else {
          changed = true;
        }
      }
      return { next: out, changed };
    };

    for (const storeName of ['tasks', 'projects', 'areas', 'projectTemplates'] as const) {
      const store = tx.objectStore(storeName);
      const rows = (await store.getAll()) as Array<{
        id: string;
        tagIds?: string[];
        updatedAt: number;
        isDeleted: boolean;
      }>;
      for (const row of rows) {
        if (row.isDeleted) continue;
        const { next, changed } = remapTagList(row.tagIds);
        if (changed) {
          await store.put({
            ...row,
            tagIds: next,
            updatedAt: Math.max(now, row.updatedAt + 1),
            isDirty: true
          });
        }
      }
    }

    let finalTarget = target;
    const allTags = (await tagStore.getAll()) as TagRecord[];
    for (const tg of allTags) {
      if (tg.isDeleted || tg.id === sourceTagId) continue;
      if (tg.id === targetTagId && tg.parentTagId === sourceTagId) {
        finalTarget = {
          ...tg,
          parentTagId:
            source.parentTagId && source.parentTagId !== targetTagId ? source.parentTagId : null,
          updatedAt: Math.max(now, tg.updatedAt + 1),
          isDirty: true
        };
        await tagStore.put(finalTarget);
      } else if (tg.id !== targetTagId && tg.parentTagId === sourceTagId) {
        await tagStore.put({
          ...tg,
          parentTagId: targetTagId,
          updatedAt: Math.max(now, tg.updatedAt + 1),
          isDirty: true
        });
      }
    }

    await tagStore.put({
      ...source,
      parentTagId: null,
      shortcut: null,
      isDeleted: true,
      updatedAt: Math.max(now, source.updatedAt + 1),
      isDirty: true
    });

    await tx.done;

    if (typeof localStorage !== 'undefined') {
      try {
        const prefs = loadPreferences();
        if (prefs.savedPerspectives && prefs.savedPerspectives.length > 0) {
          let prefsChanged = false;
          const nextPerspectives = prefs.savedPerspectives.map((p) => {
            const { next, changed } = remapTagList(p.categoryIds);
            if (!changed) return p;
            prefsChanged = true;
            return { ...p, categoryIds: next };
          });
          if (prefsChanged) {
            savePreferences({ ...prefs, savedPerspectives: nextPerspectives });
          }
        }
      } catch {
        // ignore localStorage access issues
      }
    }

    return finalTarget;
  }

  async mergeTags(sourceTagId: string, targetTagId: string): Promise<boolean> {
    if (!sourceTagId || !targetTagId || sourceTagId === targetTagId) return false;
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const merged = await this.mergeTagsUnlocked(db, sourceTagId, targetTagId, Date.now());
      if (!merged) return false;
      await this.refreshSnapshot();
      this.triggerSync();
      return true;
    });
  }

  async moveTagOrder(tagId: string, direction: 'up' | 'down'): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const tx = db.transaction('tags', 'readwrite');
      const allTags = (await tx.store.getAll()) as TagRecord[];
      const active = allTags
        .filter((tg) => !tg.isDeleted)
        .sort(
          (a, b) =>
            a.sortOrder - b.sortOrder ||
            a.title.localeCompare(b.title, undefined, { sensitivity: 'base' }) ||
            a.id.localeCompare(b.id)
        );
      const idx = active.findIndex((tg) => tg.id === tagId);
      const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
      if (idx < 0 || swapIdx < 0 || swapIdx >= active.length) {
        await tx.done;
        return;
      }

      const a = active[idx];
      const b = active[swapIdx];
      const hasAnyDuplicateOrder =
        new Set(active.map((tg) => tg.sortOrder)).size < active.length;

      if (hasAnyDuplicateOrder || a.sortOrder === b.sortOrder) {
        const reordered = [...active];
        [reordered[idx], reordered[swapIdx]] = [reordered[swapIdx], reordered[idx]];
        for (let i = 0; i < reordered.length; i++) {
          const nextOrder = (i + 1) * 1000;
          const item = reordered[i];
          if (item.sortOrder !== nextOrder || item.id === a.id || item.id === b.id) {
            await tx.store.put({
              ...item,
              sortOrder: nextOrder,
              updatedAt: Math.max(now, item.updatedAt + 1),
              isDirty: true
            });
          }
        }
      } else {
        await tx.store.put({
          ...a,
          sortOrder: b.sortOrder,
          updatedAt: Math.max(now, a.updatedAt + 1),
          isDirty: true
        });
        await tx.store.put({
          ...b,
          sortOrder: a.sortOrder,
          updatedAt: Math.max(now, b.updatedAt + 1),
          isDirty: true
        });
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();
    });
  }

  async upsertTag(params: {
    id?: string;
    title?: string;
    shortcut?: string | null;
    parentTagId?: string | null;
    sortOrder?: number;
  }): Promise<TagRecord> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();

      if (params.id && params.title !== undefined) {
        const probeTrim = params.title.trim();
        const probeNorm = probeTrim.toLowerCase();
        if (probeNorm.length > 0) {
          const allTagsSnapshot = (await db.getAll('tags')) as TagRecord[];
          const collisionTarget = allTagsSnapshot.find(
            (tg) =>
              !tg.isDeleted &&
              tg.id !== params.id &&
              tg.title.trim().toLowerCase() === probeNorm
          );
          if (collisionTarget) {
            const merged = await this.mergeTagsUnlocked(db, params.id, collisionTarget.id, now);
            await this.refreshSnapshot();
            this.triggerSync();
            return merged ?? collisionTarget;
          }
        }
      }

      const tx = db.transaction('tags', 'readwrite');
      const allTags = (await tx.store.getAll()) as TagRecord[];
      const activeTags = allTags.filter((tg) => !tg.isDeleted);
      const existingById = params.id
        ? ((await tx.store.get(params.id)) as TagRecord | undefined)
        : undefined;
      const trimmedTitle =
        params.title !== undefined ? params.title.trim() : (existingById?.title ?? '');
      const normTitle = trimmedTitle.toLowerCase();
      const sameTitleActive =
        !params.id && normTitle.length > 0
          ? activeTags.find((tg) => tg.title.trim().toLowerCase() === normTitle)
          : undefined;
      const id = params.id || sameTitleActive?.id || crypto.randomUUID();
      const existing = sameTitleActive ?? existingById;
      const maxSortOrder = activeTags.reduce(
        (m, tg) => (Number.isFinite(tg.sortOrder) && tg.sortOrder > m ? tg.sortOrder : m),
        0
      );
      const resolvedShortcut =
        params.shortcut !== undefined
          ? params.shortcut != null && params.shortcut.trim().length > 0
            ? params.shortcut.trim().toUpperCase().slice(0, 1)
            : null
          : (existing?.shortcut ?? null);

      const record: TagRecord = {
        id,
        title: trimmedTitle,
        shortcut: resolvedShortcut,
        parentTagId:
          params.parentTagId !== undefined ? params.parentTagId : (existing?.parentTagId ?? null),
        sortOrder:
          params.sortOrder !== undefined && Number.isFinite(params.sortOrder)
            ? params.sortOrder
            : existing
              ? existing.sortOrder
              : maxSortOrder + 1000,
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

  async purgeSampleDataLocally(
    markRemainingDirtyForMerge = false,
    emitCloudTombstones = false
  ): Promise<void> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
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

      const preservedSampleTaskIds = new Set<string>();
      const purgedSampleTaskIds = new Set<string>();
      for (const id of SAMPLE_TASK_IDS) {
        const existing = (await tx.objectStore('tasks').get(id)) as TaskRecord | undefined;
        if (!existing) continue;
        if (!isPurgeableSampleTask(existing)) {
          preservedSampleTaskIds.add(id);
          continue;
        }
        purgedSampleTaskIds.add(id);
        if (emitCloudTombstones) {
          await tx.objectStore('tasks').put({
            ...existing,
            title: '',
            notes: '',
            reminderTime: null,
            scheduledStartMinutes: null,
            durationMinutes: null,
            isInProgress: false,
            recurrenceRule: null,
            tagIds: [],
            isDeleted: true,
            purgedFromTrash: true,
            updatedAt: Math.max(now, existing.updatedAt + 1),
            isDirty: true
          });
        } else {
          await tx.objectStore('tasks').delete(id);
        }
      }

      const allChk = (await tx.objectStore('checklist_items').getAll()) as ChecklistItemRecord[];
      for (const c of allChk) {
        const isSampleChk = SAMPLE_CHECKLIST_IDS.has(c.id) || purgedSampleTaskIds.has(c.taskId);
        if (!isSampleChk || preservedSampleTaskIds.has(c.taskId)) continue;
        if (emitCloudTombstones) {
          await tx.objectStore('checklist_items').put({
            ...c,
            title: '',
            isDeleted: true,
            purgedFromTrash: true,
            updatedAt: Math.max(now, c.updatedAt + 1),
            isDirty: true
          });
        } else {
          await tx.objectStore('checklist_items').delete(c.id);
        }
      }

      const remainingTasks = ((await tx.objectStore('tasks').getAll()) as TaskRecord[]).filter(
        (t) => !t.isDeleted
      );

      for (const hId of SAMPLE_HEADING_IDS) {
        if (!remainingTasks.some((t) => t.headingId === hId)) {
          const existingHdg = (await tx.objectStore('headings').get(hId)) as HeadingRecord | undefined;
          if (!existingHdg) continue;
          if (emitCloudTombstones) {
            await tx.objectStore('headings').put({
              ...existingHdg,
              title: '',
              isDeleted: true,
              purgedFromTrash: true,
              updatedAt: Math.max(now, existingHdg.updatedAt + 1),
              isDirty: true
            });
          } else {
            await tx.objectStore('headings').delete(hId);
          }
        }
      }
      for (const pId of SAMPLE_PROJECT_IDS) {
        if (!remainingTasks.some((t) => t.projectId === pId)) {
          const existingProj = (await tx.objectStore('projects').get(pId)) as ProjectRecord | undefined;
          if (!existingProj) continue;
          if (emitCloudTombstones) {
            await tx.objectStore('projects').put({
              ...existingProj,
              title: '',
              notes: '',
              tagIds: [],
              detachedTaskSnapshots: undefined,
              deletedTaskIds: undefined,
              isDeleted: true,
              purgedFromTrash: true,
              updatedAt: Math.max(now, existingProj.updatedAt + 1),
              isDirty: true
            });
          } else {
            await tx.objectStore('projects').delete(pId);
          }
        }
      }
      const remainingProjects = ((await tx.objectStore('projects').getAll()) as ProjectRecord[]).filter(
        (p) => !p.isDeleted
      );
      const remainingTemplates = (
        (await tx.objectStore('projectTemplates').getAll()) as ProjectTemplate[]
      ).filter((tpl) => !tpl.isDeleted);

      const deletedSampleTagIds = new Set<string>();
      for (const tagId of SAMPLE_TAG_IDS) {
        const usedByTask = remainingTasks.some((t) => t.tagIds.includes(tagId));
        const usedByProj = remainingProjects.some((p) => p.tagIds.includes(tagId));
        if (!usedByTask && !usedByProj) {
          const existingTag = (await tx.objectStore('tags').get(tagId)) as TagRecord | undefined;
          if (existingTag) {
            if (emitCloudTombstones) {
              await tx.objectStore('tags').put({
                ...existingTag,
                title: '',
                isDeleted: true,
                purgedFromTrash: true,
                updatedAt: Math.max(now, existingTag.updatedAt + 1),
                isDirty: true
              });
            } else {
              await tx.objectStore('tags').delete(tagId);
            }
          }
          deletedSampleTagIds.add(tagId);
        }
      }

      if (markRemainingDirtyForMerge) {
        // Delete untouched sample Areas before merging into a cloud account unless referenced
        // by a user task, project, or template, so cloud Areas are never polluted by sample data.
        for (const areaId of SAMPLE_AREA_IDS) {
          const usedByTask = remainingTasks.some((t) => t.areaId === areaId);
          const usedByProj = remainingProjects.some((p) => p.areaId === areaId);
          const usedByTpl = remainingTemplates.some((tpl) => tpl.defaultAreaId === areaId);
          if (!usedByTask && !usedByProj && !usedByTpl) {
            await tx.objectStore('areas').delete(areaId);
          }
        }

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
              const cleanedTags =
                Array.isArray((rec as { tagIds?: unknown }).tagIds) && deletedSampleTagIds.size > 0
                  ? ((rec as { tagIds: string[] }).tagIds || []).filter(
                      (tid) => !deletedSampleTagIds.has(tid)
                    )
                  : undefined;
              await tx.objectStore(sName).put({
                ...rec,
                ...(cleanedTags !== undefined ? { tagIds: cleanedTags } : {}),
                isDirty: true
              });
            }
          }
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      if (emitCloudTombstones) {
        this.triggerSync();
      }
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

  async hasStaleOfflineDirtyEntities(
    now = Date.now(),
    staleThresholdMs = OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS
  ): Promise<boolean> {
    const dirty = await this.getDirtyEntities();
    const cutoff = now - staleThresholdMs;
    return (
      dirty.areas.some((x) => x.updatedAt < cutoff) ||
      dirty.projects.some((x) => x.updatedAt < cutoff) ||
      dirty.headings.some((x) => x.updatedAt < cutoff) ||
      dirty.tasks.some((x) => x.updatedAt < cutoff) ||
      dirty.checklistItems.some((x) => x.updatedAt < cutoff) ||
      dirty.tags.some((x) => x.updatedAt < cutoff) ||
      dirty.attachments.some((x) => x.updatedAt < cutoff) ||
      dirty.projectTemplates.some((x) => x.updatedAt < cutoff)
    );
  }

  async getDirtyEntities(options?: { restampStaleOfflineAt?: number }) {
    if (options?.restampStaleOfflineAt != null) {
      const pushNow = Math.trunc(options.restampStaleOfflineAt);
      const cutoff = pushNow - OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS;
      await this.enqueueWrite(async () => {
        const db = await this.getDb();
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
        const tx = db.transaction(stores, 'readwrite');
        for (const sName of stores) {
          const store = tx.objectStore(sName);
          const all = (await store.getAll()) as Array<{
            id: string;
            updatedAt: number;
            isDirty: boolean;
          }>;
          for (const item of all) {
            if (item.isDirty && item.updatedAt < cutoff) {
              await store.put({ ...item, updatedAt: pushNow });
            }
          }
        }
        await tx.done;
      });
    }

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
    areas?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    projects?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    headings?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    tasks?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    checklistItems?: {
      ids: string[];
      maxUpdatedAt?: number;
      updatedAtById?: Record<string, number>;
    };
    tags?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    attachments?: { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> };
    projectTemplates?: {
      ids: string[];
      maxUpdatedAt?: number;
      updatedAtById?: Record<string, number>;
    };
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
      const map: Array<
        [
          string,
          | { ids: string[]; maxUpdatedAt?: number; updatedAtById?: Record<string, number> }
          | undefined
        ]
      > = [
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
        const fallbackMaxTs = spec.maxUpdatedAt ?? Number.MAX_SAFE_INTEGER;
        const store = tx.objectStore(storeName);
        for (const id of spec.ids) {
          const itemCeiling = spec.updatedAtById?.[id] ?? fallbackMaxTs;
          const cur = (await store.get(id)) as { updatedAt: number; isDirty: boolean } | undefined;
          if (cur && cur.isDirty && cur.updatedAt <= itemCeiling) {
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
    projects?: RemoteProjectWithHints[];
    headings?: RemoteHeadingWithHints[];
    tasks?: RemoteTaskWithHints[];
    checklistItems?: ChecklistItemRecord[];
    attachments?: AttachmentRecord[];
    projectTemplates?: ProjectTemplate[];
  }): Promise<number> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const applyNow = Date.now();
      let maxUpdatedAt = 0;
      let needsSyncPushBack = false;
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

      const nowCeiling = applyNow + 120_000;
      const shouldRemoteWin = (
        local: { updatedAt: number; isDirty: boolean } | undefined,
        remoteUpdatedAt: number
      ): boolean => {
        if (!local) return true;
        if (!Number.isFinite(local.updatedAt)) return true;
        if (!local.isDirty && local.updatedAt > nowCeiling) return true;
        return remoteUpdatedAt >= local.updatedAt;
      };

      const clampUpdatedAt = (ts: number): number => clampRemoteCursorTimestamp(ts, applyNow);

      const applyList = async <T extends { id: string; updatedAt: number; isDirty: boolean }>(
        storeName: string,
        items: T[] | undefined,
        mergeFn?: (local: T | undefined, remote: T) => T
      ) => {
        if (!items || items.length === 0) return;
        const store = tx.objectStore(storeName);
        for (const remote of items) {
          if (!remote.id) continue;
          const clampedTs = clampUpdatedAt(remote.updatedAt);
          const local = (await store.get(remote.id)) as T | undefined;
          if (shouldRemoteWin(local, remote.updatedAt)) {
            const merged = mergeFn ? mergeFn(local, remote) : remote;
            await store.put({ ...merged, updatedAt: clampedTs, isDirty: false });
          }
          if (clampedTs > maxUpdatedAt) {
            maxUpdatedAt = clampedTs;
          }
        }
      };

      await applyList('tags', batch.tags);
      await applyList('areas', batch.areas);

      if (batch.projects && batch.projects.length > 0) {
        const projStore = tx.objectStore('projects');
        for (const remoteWithHint of batch.projects) {
          if (!remoteWithHint.id) continue;
          const { omittedV30ProjectFields, ...remote } = remoteWithHint;
          const clampedTs = clampUpdatedAt(remote.updatedAt);
          const local = (await projStore.get(remote.id)) as ProjectRecord | undefined;
          const mergedLastReviewed: number | null = remote.isDeleted
            ? (remote.lastReviewedAt ?? null)
            : mergeMonotonicReviewTimestamp(local?.lastReviewedAt, remote.lastReviewedAt);
          if (shouldRemoteWin(local, remote.updatedAt)) {
            const keepLocalV30Proj =
              Boolean(omittedV30ProjectFields) && !remote.isDeleted && local !== undefined;
            await projStore.put({
              ...remote,
              updatedAt: clampedTs,
              lastReviewedAt: mergedLastReviewed,
              workspaceId: remote.isDeleted
                ? null
                : keepLocalV30Proj
                  ? (local?.workspaceId ?? null)
                  : (remote.workspaceId ?? null),
              viewMode: remote.isDeleted
                ? null
                : keepLocalV30Proj
                  ? (local?.viewMode ?? null)
                  : (remote.viewMode ?? null),
              purgedFromTrash: Boolean(
                remote.purgedFromTrash || (remote.isDeleted && local?.purgedFromTrash)
              ),
              detachedTaskSnapshots: remote.isDeleted ? local?.detachedTaskSnapshots : undefined,
              deletedTaskIds: remote.isDeleted ? local?.deletedTaskIds : undefined,
              isDirty: false
            });
          } else if (
            local &&
            !remote.isDeleted &&
            !local.isDeleted &&
            mergedLastReviewed !== null &&
            mergedLastReviewed > (local.lastReviewedAt ?? 0)
          ) {
            await projStore.put({
              ...local,
              lastReviewedAt: mergedLastReviewed
            });
          }
          if (clampedTs > maxUpdatedAt) {
            maxUpdatedAt = clampedTs;
          }
        }
      }

      if (batch.headings && batch.headings.length > 0) {
        const headingStore = tx.objectStore('headings');
        for (const remoteWithHint of batch.headings) {
          if (!remoteWithHint.id) continue;
          const {
            omittedStartDate: _omittedStartDate,
            omittedEndDate: _omittedEndDate,
            ...remote
          } = remoteWithHint;
          const clampedTs = clampUpdatedAt(remote.updatedAt);
          const local = (await headingStore.get(remote.id)) as HeadingRecord | undefined;
          if (shouldRemoteWin(local, remote.updatedAt)) {
            await headingStore.put({
              ...remote,
              startDate: resolveMergedHeadingStartDate(local, remoteWithHint),
              endDate: resolveMergedHeadingEndDate(local, remoteWithHint),
              updatedAt: clampedTs,
              isDirty: false
            });
          }
          if (clampedTs > maxUpdatedAt) {
            maxUpdatedAt = clampedTs;
          }
        }
      }

      if (batch.tasks && batch.tasks.length > 0) {
        const taskStore = tx.objectStore('tasks');
        const existingAllTasks = (await taskStore.getAll()) as TaskRecord[];
        const liveBlockedByMap = new Map<string, readonly string[]>();
        const openSeriesTaskIds = new Map<string, Set<string>>();
        const registerOpenSeries = (tk: Pick<TaskRecord, 'id' | 'title' | 'status' | 'isDeleted' | 'projectId' | 'recurrenceRule'>) => {
          if (tk.isDeleted || tk.status !== 'OPEN') return;
          const normTitle = normalizeRecurringTitleKey(tk.title);
          const ruleKey = canonicalizeRecurrenceRuleKey(tk.recurrenceRule);
          if (!normTitle || !ruleKey) return;
          const key = `${normTitle}\u001F${ruleKey}\u001F${(tk.projectId || '').trim()}`;
          const set = openSeriesTaskIds.get(key);
          if (set) set.add(tk.id);
          else openSeriesTaskIds.set(key, new Set([tk.id]));
        };
        for (const tk of existingAllTasks) {
          registerOpenSeries(tk);
          if (!tk.isDeleted && Array.isArray(tk.blockedByTaskIds) && tk.blockedByTaskIds.length > 0) {
            liveBlockedByMap.set(tk.id, tk.blockedByTaskIds);
          }
        }
        for (const tk of batch.tasks) registerOpenSeries(tk);

        for (const remoteWithHint of batch.tasks) {
          if (!remoteWithHint.id) continue;
          const {
            omittedTimeblockFields,
            omittedActualMinutesField,
            omittedPinnedField,
            omittedMilestoneField,
            omittedInProgressField,
            omittedV30TaskFields,
            omittedEffortField,
            omittedBlockedByField,
            omittedWorkspaceField,
            omittedAssigneeField,
            ...remote
          } = remoteWithHint;
          const clampedTs = clampUpdatedAt(remote.updatedAt);
          const local = (await taskStore.get(remote.id)) as TaskRecord | undefined;

          // Guard against v1.8 startup rollover resurrecting an already-COMPLETED recurring parent
          // when the next OPEN occurrence in the same series already exists.
          if (
            local &&
            !local.isDeleted &&
            local.status === 'COMPLETED' &&
            local.recurrenceRule != null &&
            !remote.isDeleted &&
            remote.status === 'OPEN'
          ) {
            const normTitle = normalizeRecurringTitleKey(local.title || remote.title);
            const ruleKey =
              canonicalizeRecurrenceRuleKey(local.recurrenceRule) ??
              canonicalizeRecurrenceRuleKey(remote.recurrenceRule);
            if (normTitle && ruleKey) {
              const sKey = `${normTitle}\u001F${ruleKey}\u001F${(local.projectId || remote.projectId || '').trim()}`;
              const openIds = openSeriesTaskIds.get(sKey);
              const hasOtherOpenOccurrence = openIds
                ? Array.from(openIds).some((oid) => oid !== remote.id)
                : false;
              if (hasOtherOpenOccurrence) {
                await taskStore.put({
                  ...local,
                  updatedAt: Math.max(applyNow, clampedTs + 1, local.updatedAt + 1),
                  isDirty: true
                });
                needsSyncPushBack = true;
                if (clampedTs > maxUpdatedAt) maxUpdatedAt = clampedTs;
                continue;
              }
            }
          }

          if (shouldRemoteWin(local, remote.updatedAt)) {
            const keepLocalTimeblock =
              Boolean(omittedTimeblockFields) &&
              !remote.isDeleted &&
              local !== undefined &&
              (remote.scheduleState === 'TODAY' ||
                remote.scheduleState === 'THIS_EVENING' ||
                remote.scheduleState === 'UPCOMING');
            const resolvedPinned = remote.isDeleted
              ? false
              : omittedPinnedField
                ? Boolean(local?.isPinned)
                : Boolean(remote.isPinned);
            const resolvedMilestone = remote.purgedFromTrash
              ? false
              : omittedMilestoneField
                ? (local?.isMilestone ?? false)
                : Boolean(remote.isMilestone);
            const resolvedInProgress =
              remote.isDeleted || remote.status !== 'OPEN'
                ? false
                : omittedInProgressField && local !== undefined
                  ? Boolean(local.isInProgress)
                  : Boolean(remote.isInProgress);

            const resolvedEffort = remote.isDeleted
              ? null
              : (omittedV30TaskFields || omittedEffortField) && local !== undefined
                ? (local.effortLevel ?? null)
                : normalizeEffortLevel(remote.effortLevel);

            let resolvedBlockedBy: string[] = [];
            if (!remote.isDeleted) {
              const rawCandidates =
                (omittedV30TaskFields || omittedBlockedByField) && local !== undefined
                  ? safeParseBlockedByTaskIds(local.blockedByTaskIds, remote.id)
                  : safeParseBlockedByTaskIds(remote.blockedByTaskIds, remote.id);
              liveBlockedByMap.delete(remote.id);
              for (const bid of rawCandidates) {
                if (!wouldCreateDependencyCycle(remote.id, bid, liveBlockedByMap)) {
                  resolvedBlockedBy.push(bid);
                  liveBlockedByMap.set(remote.id, resolvedBlockedBy);
                }
              }
              liveBlockedByMap.set(remote.id, resolvedBlockedBy);
            } else {
              liveBlockedByMap.delete(remote.id);
            }

            const resolvedWorkspaceId = remote.isDeleted
              ? null
              : (omittedV30TaskFields || omittedWorkspaceField) && local !== undefined
                ? (local.workspaceId ?? null)
                : (remote.workspaceId ?? null);

            const resolvedAssigneeId = remote.isDeleted
              ? null
              : (omittedV30TaskFields || omittedAssigneeField) && local !== undefined
                ? (local.assigneeId ?? null)
                : (remote.assigneeId ?? null);

            const resolvedActualMinutes = remote.isDeleted
              ? null
              : omittedActualMinutesField && local !== undefined
                ? (local.actualMinutes ?? null)
                : remote.actualMinutes != null &&
                    Number.isFinite(remote.actualMinutes) &&
                    remote.actualMinutes > 0
                  ? Math.max(1, Math.min(48_000, Math.trunc(remote.actualMinutes)))
                  : null;

            await taskStore.put({
              ...remote,
              updatedAt: clampedTs,
              scheduledStartMinutes: keepLocalTimeblock
                ? (local?.scheduledStartMinutes ?? null)
                : (remote.scheduledStartMinutes ?? null),
              durationMinutes: keepLocalTimeblock
                ? (local?.durationMinutes ?? null)
                : (remote.durationMinutes ?? null),
              actualMinutes: resolvedActualMinutes,
              isPinned: resolvedPinned,
              isMilestone: resolvedMilestone,
              isInProgress: resolvedInProgress,
              effortLevel: resolvedEffort,
              blockedByTaskIds: resolvedBlockedBy,
              workspaceId: resolvedWorkspaceId,
              assigneeId: resolvedAssigneeId,
              purgedFromTrash: Boolean(
                remote.purgedFromTrash || (remote.isDeleted && local?.purgedFromTrash)
              ),
              isDirty: false
            });
          }
          if (clampedTs > maxUpdatedAt) {
            maxUpdatedAt = clampedTs;
          }
        }
      }

      await applyList('checklist_items', batch.checklistItems);
      await applyList('attachments', batch.attachments, (local, remote) => {
        if (!remote.isDeleted && !remote.dataBase64 && local?.dataBase64) {
          return { ...remote, dataBase64: local.dataBase64 };
        }
        return remote;
      });
      await applyList('projectTemplates', batch.projectTemplates);

      // Reconcile same-title active Tags across devices with full reference remapping
      if ((batch.tags?.length ?? 0) > 0) {
        const tagStore = tx.objectStore('tags');
        const allTags = (await tagStore.getAll()) as TagRecord[];
        const byNormTitle = new Map<string, TagRecord[]>();
        for (const tg of allTags) {
          if (tg.isDeleted) continue;
          const norm = tg.title.trim().toLowerCase();
          if (!norm) continue;
          const list = byNormTitle.get(norm);
          if (list) list.push(tg);
          else byNormTitle.set(norm, [tg]);
        }

        const remapTagId = new Map<string, string>();
        for (const group of byNormTitle.values()) {
          if (group.length < 2) continue;
          group.sort((a, b) => {
            const ac = a.createdAt > 0 ? a.createdAt : Number.MAX_SAFE_INTEGER;
            const bc = b.createdAt > 0 ? b.createdAt : Number.MAX_SAFE_INTEGER;
            if (ac !== bc) return ac - bc;
            return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
          });
          const winner = group[0];
          for (let i = 1; i < group.length; i++) {
            const loser = group[i];
            remapTagId.set(loser.id, winner.id);
            await tagStore.put({
              ...loser,
              isDeleted: true,
              updatedAt: Math.max(applyNow, loser.updatedAt + 1),
              isDirty: true
            });
            needsSyncPushBack = true;
          }
        }

        if (remapTagId.size > 0) {
          const remapArray = (ids: string[] | undefined): { next: string[]; changed: boolean } => {
            const src = ids || [];
            let changed = false;
            const out: string[] = [];
            for (const rawId of src) {
              const mapped = remapTagId.get(rawId) ?? rawId;
              if (mapped !== rawId) changed = true;
              if (!out.includes(mapped)) out.push(mapped);
              else if (src.length !== out.length) changed = true;
            }
            return { next: out, changed };
          };

          for (const storeName of ['tasks', 'projects', 'areas', 'projectTemplates'] as const) {
            const st = tx.objectStore(storeName);
            const rows = (await st.getAll()) as Array<{
              id: string;
              tagIds?: string[];
              updatedAt: number;
              isDeleted: boolean;
            }>;
            for (const row of rows) {
              if (row.isDeleted) continue;
              const { next, changed } = remapArray(row.tagIds);
              if (changed) {
                await st.put({
                  ...row,
                  tagIds: next,
                  updatedAt: Math.max(applyNow, row.updatedAt + 1),
                  isDirty: true
                });
                needsSyncPushBack = true;
              }
            }
          }
        }
      }

      await tx.done;

      if ((batch.tasks?.length ?? 0) > 0) {
        await this.rolloverDueTasksUnlocked(db);
        const deduped = await this.deduplicateOpenRecurringTasksUnlocked(db);
        if (deduped > 0) {
          needsSyncPushBack = true;
        }
      }

      await this.refreshSnapshot();
      if (needsSyncPushBack) {
        this.triggerSync();
      }
      return maxUpdatedAt;
    });
  }

  async applyRemoteBatch(
    batch: Parameters<ThingsLocalDb['applyRemoteDeltas']>[0]
  ): Promise<number> {
    return this.applyRemoteDeltas(batch);
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
        lastWeeklyReviewAt: prefs.lastWeeklyReviewAt || 0,
        tagSortMode: prefs.tagSortMode ?? 'alpha',
        viewSortMode: prefs.viewSortMode ?? 'manual',
        ...(typeof prefs.showPerspectives === 'boolean'
          ? { showPerspectives: prefs.showPerspectives }
          : {}),
        savedPerspectivesJson: JSON.stringify(prefs.savedPerspectives ?? []),
        workdayStartMinutes: prefs.workdayStartMinutes ?? DEFAULT_WORKDAY_START_MINUTES,
        workdayEndMinutes: prefs.workdayEndMinutes ?? DEFAULT_WORKDAY_END_MINUTES,
        areaViewModes: prefs.areaViewModes ?? {}
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

  // ==================== Asana & Microsoft Planner Import / Export ====================

  async importExternalProject(
    parsed: ExternalImportedProject,
    areaId?: string | null,
    workspaceId?: string | null
  ): Promise<{ projectId: string; title: string; taskCount: number }> {
    return this.enqueueWrite(async () => {
      const db = await this.getDb();
      const now = Date.now();
      const todayEpoch = startOfDayEpoch(new Date(now));
      const todayEnd = endOfDayEpoch(new Date(now));

      const activeArea = areaId
        ? ((await db.get('areas', areaId)) as AreaRecord | undefined)
        : undefined;
      const validAreaId = activeArea && !activeArea.isDeleted ? activeArea.id : null;

      const resolvedProjectWorkspaceId =
        workspaceId?.trim() || parsed.workspaceId?.trim() || null;

      const tx = db.transaction(
        ['projects', 'headings', 'tags', 'tasks', 'checklist_items'],
        'readwrite'
      );

      const allTags = (await tx.objectStore('tags').getAll()) as TagRecord[];
      const tagMap = new Map<string, TagRecord>();
      let tagCounter = 0;
      for (const tg of allTags) {
        if (!tg.isDeleted) {
          tagMap.set(tg.title.trim().toLowerCase(), tg);
          tagCounter++;
        }
      }

      const resolveTagIds = async (names: readonly string[]): Promise<string[]> => {
        const ids: string[] = [];
        for (const rawName of names) {
          const trimmed = rawName.trim();
          if (!trimmed) continue;
          const lower = trimmed.toLowerCase();
          const existing = tagMap.get(lower);
          if (existing) {
            ids.push(existing.id);
          } else {
            tagCounter++;
            const created: TagRecord = {
              id: crypto.randomUUID(),
              title: trimmed,
              parentTagId: null,
              shortcut: null,
              sortOrder: tagCounter * 100,
              createdAt: now,
              updatedAt: now,
              isDeleted: false,
              isDirty: true
            };
            await tx.objectStore('tags').put(created);
            tagMap.set(lower, created);
            ids.push(created.id);
          }
        }
        return Array.from(new Set(ids));
      };

      const projectTitle = parsed.title.trim() || 'Imported Project';
      const projectId = crypto.randomUUID();
      const projectTagIds = await resolveTagIds(parsed.tagNames);

      await tx.objectStore('projects').put({
        id: projectId,
        areaId: validAreaId,
        title: projectTitle,
        notes: parsed.notes ?? '',
        status: 'ACTIVE',
        scheduleState: 'TODAY',
        whenDate: todayEpoch,
        deadline: null,
        completedAt: null,
        lastReviewedAt: now,
        sortOrder: now,
        tagIds: projectTagIds,
        workspaceId: resolvedProjectWorkspaceId,
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: true
      } satisfies ProjectRecord);

      let taskCount = 0;

      const persistImportedTask = async (
        extTask: ExternalImportedTask,
        headingId: string | null,
        sortOrder: number
      ) => {
        const cleanTitle = extTask.title.trim();
        if (!cleanTitle) return;

        const taskTagIds = await resolveTagIds(extTask.tagNames);
        const scheduleState: ScheduleState =
          extTask.whenDate != null
            ? extTask.whenDate <= todayEnd
              ? 'TODAY'
              : 'UPCOMING'
            : 'ANYTIME';

        const status: TaskStatus =
          extTask.status === 'COMPLETED' ? 'COMPLETED' : 'OPEN';
        const completedAt =
          status === 'COMPLETED' ? (extTask.completedAt ?? now) : null;

        const taskId = crypto.randomUUID();
        await tx.objectStore('tasks').put({
          id: taskId,
          title: cleanTitle,
          notes: extTask.notes ?? '',
          status,
          scheduleState,
          whenDate: extTask.whenDate,
          deadline: extTask.deadline,
          reminderTime: null,
          isPinned: Boolean(extTask.isPinned),
          isMilestone: Boolean(extTask.isMilestone),
          effortLevel: extTask.effortLevel ?? null,
          workspaceId: extTask.workspaceId?.trim() || resolvedProjectWorkspaceId,
          assigneeId: extTask.assigneeId?.trim() || null,
          completedAt,
          projectId,
          headingId,
          areaId: null,
          recurrenceRule: null,
          sortOrder,
          tagIds: taskTagIds,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true,
          purgedFromTrash: false
        } satisfies TaskRecord);

        for (let cIdx = 0; cIdx < extTask.checklistItems.length; cIdx++) {
          const chk = extTask.checklistItems[cIdx];
          const chkTitle = chk.title.trim();
          if (!chkTitle) continue;
          await tx.objectStore('checklist_items').put({
            id: crypto.randomUUID(),
            taskId,
            title: chkTitle,
            isCompleted: Boolean(chk.isCompleted),
            sortOrder: (cIdx + 1) * 100,
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            isDirty: true
          } satisfies ChecklistItemRecord);
        }

        taskCount++;
      };

      for (let tIdx = 0; tIdx < parsed.ungroupedTasks.length; tIdx++) {
        await persistImportedTask(parsed.ungroupedTasks[tIdx], null, (tIdx + 1) * 100);
      }

      let persistedHeadingIdx = 0;
      for (let hIdx = 0; hIdx < parsed.phases.length; hIdx++) {
        const phase = parsed.phases[hIdx];
        if (isUngroupedSectionName(phase.title) && phase.tasks.length === 0) {
          continue;
        }
        persistedHeadingIdx++;
        const headingId = crypto.randomUUID();
        await tx.objectStore('headings').put({
          id: headingId,
          projectId,
          title: phase.title.trim() || `Phase ${persistedHeadingIdx}`,
          startDate: phase.startDate ?? null,
          endDate: phase.endDate ?? null,
          sortOrder: persistedHeadingIdx * 1000,
          isArchived: false,
          createdAt: now,
          updatedAt: now,
          isDeleted: false,
          isDirty: true
        } satisfies HeadingRecord);

        for (let tIdx = 0; tIdx < phase.tasks.length; tIdx++) {
          await persistImportedTask(
            phase.tasks[tIdx],
            headingId,
            persistedHeadingIdx * 1000 + (tIdx + 1) * 100
          );
        }
      }

      await tx.done;
      await this.refreshSnapshot();
      this.triggerSync();

      return {
        projectId,
        title: projectTitle,
        taskCount
      };
    });
  }

  async exportProjectExternal(
    projectId: string,
    format: ExternalExportFormat
  ): Promise<{
    filename: string;
    mimeType: string;
    bytes: Uint8Array;
    projectTitle: string;
  }> {
    const db = await this.getDb();
    const project = (await db.get('projects', projectId)) as ProjectRecord | undefined;
    if (!project || project.isDeleted) {
      throw new Error('Project not found');
    }

    const [allHeadings, allTasks, allChecklists, allTags] = await Promise.all([
      db.getAll('headings') as Promise<HeadingRecord[]>,
      db.getAll('tasks') as Promise<TaskRecord[]>,
      db.getAll('checklist_items') as Promise<ChecklistItemRecord[]>,
      db.getAll('tags') as Promise<TagRecord[]>
    ]);

    const headings = allHeadings
      .filter((h) => h.projectId === projectId && !h.isDeleted && !h.isArchived)
      .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

    const tasks = allTasks
      .filter(
        (t) =>
          t.projectId === projectId &&
          !t.isDeleted &&
          (t.status === 'OPEN' || t.status === 'COMPLETED')
      )
      .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

    const taskIdSet = new Set(tasks.map((t) => t.id));
    const checklistItems = allChecklists
      .filter((c) => !c.isDeleted && taskIdSet.has(c.taskId))
      .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

    const tags = allTags.filter((tg) => !tg.isDeleted);

    return exportExternalProjectBytes(
      {
        project,
        headings,
        tasks,
        checklistItems,
        tags
      },
      format
    );
  }
}

export const localDb = new ThingsLocalDb();
