export const APP_VERSION = '3.0.0-beta.13';
export const APP_BUILD_CODE = 104;
export const MAX_AREAS = 8;

export type ScheduleState =
  | 'INBOX'
  | 'TODAY'
  | 'THIS_EVENING'
  | 'UPCOMING'
  | 'ANYTIME'
  | 'SOMEDAY';

export type TaskStatus = 'OPEN' | 'COMPLETED' | 'CANCELED';

export type ProjectStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELED';

export type RecurrenceMode = 'FIXED_SCHEDULE' | 'AFTER_COMPLETION';

export type RecurrenceUnit = 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS';

export type EffortLevel = 'QUICK_WIN' | 'MEDIUM' | 'DEEP_FOCUS';

export type TagSortMode = 'alpha' | 'most_used' | 'manual';

export type ViewSortMode = 'manual' | 'deadline' | 'duration' | 'effort' | 'alpha';

export type ProjectViewMode = 'list' | 'board' | 'roadmap';

export interface SavedPerspective {
  id: string;
  title: string;
  categoryIds: string[];
  effortLevel: EffortLevel | null;
  maxDurationMinutes: number | null;
  dueWindow: 'ANY' | 'TODAY' | 'NEXT_7_DAYS' | 'OVERDUE';
  areaId: string | null;
  actionableOnly: boolean;
  sortOrder: number;
}

export interface RecurrenceRule {
  mode: RecurrenceMode;
  interval: number;
  unit: RecurrenceUnit;
  daysOfWeek: number[]; // ISO-8601: 1 (Mon) .. 7 (Sun)
  dayOfMonth?: number | null; // 1..31 or -1 for last day
  endDateEpochMillis?: number | null;
}

export interface TagRecord {
  id: string;
  title: string;
  parentTagId: string | null;
  shortcut: string | null;
  sortOrder: number;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export interface AreaRecord {
  id: string;
  title: string;
  sortOrder: number;
  tagIds: string[];
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export interface DetachedProjectTaskSnapshot {
  id: string;
  projectId: string | null;
  headingId: string | null;
  areaId: string | null;
}

export interface ProjectRecord {
  id: string;
  areaId: string | null;
  title: string;
  notes: string;
  status: ProjectStatus;
  scheduleState: ScheduleState;
  whenDate: number | null;
  deadline: number | null;
  completedAt: number | null;
  lastReviewedAt?: number | null;
  sortOrder: number;
  tagIds: string[];
  workspaceId?: string | null;
  viewMode?: ProjectViewMode | null;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
  purgedFromTrash?: boolean;
  detachedTaskSnapshots?: DetachedProjectTaskSnapshot[];
  deletedTaskIds?: string[];
}

export type Project = ProjectRecord;

export interface HeadingRecord {
  id: string;
  projectId: string;
  title: string;
  startDate?: number | null;
  endDate?: number | null;
  sortOrder: number;
  isArchived: boolean;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export interface TaskRecord {
  id: string;
  title: string;
  notes: string;
  status: TaskStatus;
  scheduleState: ScheduleState;
  whenDate: number | null;
  deadline: number | null;
  reminderTime: number | null;
  scheduledStartMinutes?: number | null;
  durationMinutes?: number | null;
  isPinned?: boolean;
  effortLevel?: EffortLevel | null;
  blockedByTaskIds?: string[];
  workspaceId?: string | null;
  assigneeId?: string | null;
  completedAt: number | null;
  projectId: string | null;
  headingId: string | null;
  areaId: string | null;
  recurrenceRule: RecurrenceRule | null;
  sortOrder: number;
  tagIds: string[];
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
  purgedFromTrash?: boolean;
}

export type TaskItem = TaskRecord;

export interface ProjectTemplateTaskBlueprint {
  title: string;
  notes?: string;
  checklistItems?: string[];
  whenDaysOffset?: number | null;
  deadlineDaysOffset?: number | null;
  scheduleState?: ScheduleState;
  scheduledStartMinutes?: number | null;
  durationMinutes?: number | null;
}

export interface ProjectTemplateHeadingBlueprint {
  title: string;
  endDateDayOffset?: number | null;
  tasks: ProjectTemplateTaskBlueprint[];
}

export interface ProjectTemplateBlueprint {
  version?: number;
  notes?: string;
  tasks?: ProjectTemplateTaskBlueprint[];
  headings: ProjectTemplateHeadingBlueprint[];
}

export interface ProjectTemplate {
  id: string;
  title: string;
  notes: string;
  defaultAreaId: string | null;
  tagIds: string[];
  blueprintJson: string;
  sortOrder: number;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export type ProjectTemplateRecord = ProjectTemplate;

export interface ChecklistItemRecord {
  id: string;
  taskId: string;
  title: string;
  isCompleted: boolean;
  sortOrder: number;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export interface AttachmentRecord {
  id: string;
  taskId: string | null;
  projectId: string | null;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  dataBase64: string;
  localRelativePath?: string;
  sortOrder: number;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
  isDirty: boolean;
}

export type SmartViewType =
  | 'INBOX'
  | 'TODAY'
  | 'UPCOMING'
  | 'ANYTIME'
  | 'SOMEDAY'
  | 'LOGBOOK'
  | 'TRASH';

export type ActiveNavigation =
  | { kind: 'smart'; view: SmartViewType }
  | { kind: 'area'; areaId: string }
  | { kind: 'project'; projectId: string }
  | { kind: 'perspective'; perspectiveId: string };

export type NavSelection = ActiveNavigation;

export type CloudSyncStatus = 'OFFLINE' | 'SYNCING' | 'SYNCED';

export type SupportedLang = 'en' | 'de' | 'fr' | 'it' | 'es';

export type ThemeMode = 'system' | 'light' | 'dark';

export interface AppPreferences {
  language: SupportedLang | 'system';
  themeMode: ThemeMode;
  sidebarVisible: boolean;
  localOnlyDbMode: boolean;
  setupBannerDismissed: boolean;
  dailyDigestEnabled: boolean;
  dailyDigestHour: number;
  dailyDigestMinute: number;
  autoCheckUpdatesEnabled: boolean;
  includeBetaUpdates?: boolean;
  lastWeeklyReviewAt?: number;
  geminiApiKey: string;
  calendarEventsEnabled: boolean;
  selectedCalendarIds: string[];
  calendarIdsInitialized: boolean;
  groupTodayByProjectOrArea: boolean;
  biometricAppLockEnabled: boolean;
  appLockPinHash: string;
  appLockWebAuthnCredId: string;
  tagSortMode?: TagSortMode;
  viewSortMode?: ViewSortMode;
  savedPerspectives?: SavedPerspective[];
}

export type AppSettings = AppPreferences;

export interface QuickShareDraft {
  initialTitle: string;
  initialNotes: string;
  autoLaunchVoice?: boolean;
}

export interface FocusTimerUiState {
  taskId: string;
  taskTitle: string;
  totalSeconds: number;
  remainingSeconds: number;
  isRunning: boolean;
}

export interface DeviceCalendarInfo {
  id: string;
  displayName: string;
  accountName: string;
  colorCss: string;
  isPrimary: boolean;
  source: 'gcal' | 'ics';
}

export interface CalendarEventItem {
  eventId: string;
  calendarId: string;
  calendarName: string;
  calendarColorCss: string;
  title: string;
  startMillis: number;
  endMillis: number;
  isAllDay: boolean;
  location: string | null;
  htmlLink: string | null;
}

export interface ThingsbookStatistics {
  totalRecordsCount: number;
  openTasksCount: number;
  todayTasksCount: number;
  upcomingTasksCount: number;
  anytimeTasksCount: number;
  inboxTasksCount: number;
  somedayTasksCount: number;
  recurringTasksCount: number;
  completedTasksCount: number;
  canceledTasksCount: number;
  deletedTasksCount: number;
  activeProjectsCount: number;
  areasCount: number;
  tagsCount: number;
}

export function startOfDayEpoch(date: Date = new Date()): number {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0);
  return d.getTime();
}

export function endOfDayEpoch(date: Date = new Date()): number {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1, 0, 0, 0, 0);
  return d.getTime() - 1;
}

export function addDaysLocal(baseEpochMillis: number, days: number): number {
  const d = new Date(baseEpochMillis);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + days, 0, 0, 0, 0).getTime();
}

export function daysBetweenToday(targetEpochMillis: number): number {
  const today = new Date();
  const t0 = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  const d = new Date(targetEpochMillis);
  const t1 = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  return Math.round((t1 - t0) / 86_400_000);
}

export function toIsoDateInputValue(epochMillis: number | null): string {
  if (epochMillis == null) return '';
  const d = new Date(epochMillis);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function fromIsoDateInputValue(val: string): number | null {
  if (!val) return null;
  const parts = val.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
  return new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0, 0).getTime();
}

export function computeMidpointSortOrder(prev?: number | null, next?: number | null): number {
  const p = prev != null && Number.isFinite(prev) ? prev : null;
  const n = next != null && Number.isFinite(next) ? next : null;
  if (p == null && n == null) return 1000.0;
  if (p == null) return n! - 500.0;
  if (n == null) return p + 1000.0;
  if (Math.abs(n - p) < 1e-4) return p + 0.001;
  return (p + n) / 2.0;
}

export function isoDayOfWeek(d: Date): number {
  const js = d.getDay(); // 0 = Sun .. 6 = Sat
  return js === 0 ? 7 : js;
}

/**
 * Exact port of Android's WhenScheduleResolver.resolvePresetSelection
 */
export function resolveWhenPreset(
  preset: ScheduleState,
  reminderClock: string,
  now: Date = new Date()
): {
  scheduleState: ScheduleState;
  whenDate: number | null;
  reminderTime: number | null;
} {
  const todayEpoch = startOfDayEpoch(now);
  const tomorrowEpoch = addDaysLocal(todayEpoch, 1);

  const buildRem = (dayEpoch: number | null): number | null => {
    if (!reminderClock || dayEpoch == null) return null;
    const [hh, mm] = reminderClock.split(':').map(Number);
    if (Number.isNaN(hh) || Number.isNaN(mm)) return null;
    const d = new Date(dayEpoch);
    return new Date(
      d.getFullYear(),
      d.getMonth(),
      d.getDate(),
      Math.max(0, Math.min(23, hh)),
      Math.max(0, Math.min(59, mm)),
      0,
      0
    ).getTime();
  };

  switch (preset) {
    case 'TODAY':
      return {
        scheduleState: 'TODAY',
        whenDate: todayEpoch,
        reminderTime: buildRem(todayEpoch)
      };
    case 'THIS_EVENING':
      return {
        scheduleState: 'THIS_EVENING',
        whenDate: todayEpoch,
        reminderTime: buildRem(todayEpoch)
      };
    case 'UPCOMING':
      return {
        scheduleState: 'UPCOMING',
        whenDate: tomorrowEpoch,
        reminderTime: buildRem(tomorrowEpoch)
      };
    case 'ANYTIME':
      return { scheduleState: 'ANYTIME', whenDate: null, reminderTime: null };
    case 'SOMEDAY':
      return { scheduleState: 'SOMEDAY', whenDate: null, reminderTime: null };
    case 'INBOX':
      return { scheduleState: 'INBOX', whenDate: null, reminderTime: null };
  }
}

export function calculateNextRecurrenceDate(
  rule: RecurrenceRule,
  completedTaskWhenDate: number | null,
  nowMillis: number = Date.now()
): number | null {
  const interval = Math.max(1, Math.min(3650, Math.floor(rule.interval || 1)));
  const nowDate = new Date(nowMillis);
  const todayMidnight = new Date(
    nowDate.getFullYear(),
    nowDate.getMonth(),
    nowDate.getDate(),
    0,
    0,
    0,
    0
  );
  const baseEpoch =
    rule.mode === 'AFTER_COMPLETION' ? nowMillis : (completedTaskWhenDate ?? nowMillis);
  const base = new Date(baseEpoch);
  const initialBaseMidnight = new Date(
    base.getFullYear(),
    base.getMonth(),
    base.getDate(),
    0,
    0,
    0,
    0
  );

  const validDays = Array.from(
    new Set((rule.daysOfWeek || []).filter((d) => d >= 1 && d <= 7))
  ).sort((a, b) => a - b);

  const stepFrom = (anchorMidnight: Date): Date => {
    if (rule.unit === 'DAYS') {
      return new Date(
        anchorMidnight.getFullYear(),
        anchorMidnight.getMonth(),
        anchorMidnight.getDate() + interval
      );
    }
    if (rule.unit === 'WEEKS') {
      if (rule.mode === 'FIXED_SCHEDULE' && validDays.length > 0) {
        const currentDow = isoDayOfWeek(anchorMidnight);
        const nextSameWeek = validDays.find((d) => d > currentDow);
        if (nextSameWeek != null) {
          return new Date(
            anchorMidnight.getFullYear(),
            anchorMidnight.getMonth(),
            anchorMidnight.getDate() + (nextSameWeek - currentDow)
          );
        }
        const firstDow = validDays[0];
        const mondayOfCurrent = new Date(
          anchorMidnight.getFullYear(),
          anchorMidnight.getMonth(),
          anchorMidnight.getDate() - (currentDow - 1)
        );
        return new Date(
          mondayOfCurrent.getFullYear(),
          mondayOfCurrent.getMonth(),
          mondayOfCurrent.getDate() + interval * 7 + (firstDow - 1)
        );
      }
      return new Date(
        anchorMidnight.getFullYear(),
        anchorMidnight.getMonth(),
        anchorMidnight.getDate() + interval * 7
      );
    }
    if (rule.unit === 'MONTHS') {
      const targetMonthDate = new Date(
        anchorMidnight.getFullYear(),
        anchorMidnight.getMonth() + interval,
        1
      );
      const lastDayOfTarget = new Date(
        targetMonthDate.getFullYear(),
        targetMonthDate.getMonth() + 1,
        0
      ).getDate();
      let dom = anchorMidnight.getDate();
      if (rule.dayOfMonth === -1) {
        dom = lastDayOfTarget;
      } else if (rule.dayOfMonth != null && rule.dayOfMonth >= 1 && rule.dayOfMonth <= 31) {
        dom = Math.min(rule.dayOfMonth, lastDayOfTarget);
      } else {
        dom = Math.min(dom, lastDayOfTarget);
      }
      return new Date(targetMonthDate.getFullYear(), targetMonthDate.getMonth(), dom);
    }
    return new Date(
      anchorMidnight.getFullYear() + interval,
      anchorMidnight.getMonth(),
      anchorMidnight.getDate()
    );
  };

  let nextDate = stepFrom(initialBaseMidnight);
  if (rule.mode === 'FIXED_SCHEDULE' && nextDate.getTime() <= todayMidnight.getTime()) {
    let guard = 0;
    while (nextDate.getTime() <= todayMidnight.getTime() && guard < 500) {
      const stepped = stepFrom(nextDate);
      if (stepped.getTime() <= nextDate.getTime()) break;
      nextDate = stepped;
      guard++;
    }
    if (nextDate.getTime() <= todayMidnight.getTime()) {
      nextDate = stepFrom(todayMidnight);
    }
  }

  const nextMillis = nextDate.getTime();
  if (rule.endDateEpochMillis != null && nextMillis > rule.endDateEpochMillis) {
    return null;
  }
  return nextMillis;
}

/**
 * Exact port of computeUpcomingTargetDate in ThingsSmartViewScreen.kt (v1.6.5+)
 */
export function computeUpcomingTargetDate(
  task: TaskRecord,
  nowMillis: number = Date.now()
): number {
  const todayMidnight = startOfDayEpoch(new Date(nowMillis));
  const whenMidnight = task.whenDate != null ? startOfDayEpoch(new Date(task.whenDate)) : null;
  const deadlineMidnight =
    task.deadline != null ? startOfDayEpoch(new Date(task.deadline)) : null;

  if (task.scheduleState === 'TODAY' || task.scheduleState === 'THIS_EVENING') {
    if (whenMidnight != null && whenMidnight > todayMidnight) return whenMidnight;
    return todayMidnight;
  }
  if (whenMidnight != null && whenMidnight >= todayMidnight) {
    return whenMidnight;
  }
  if (task.recurrenceRule != null) {
    const baseEpoch =
      whenMidnight != null && whenMidnight > todayMidnight ? whenMidnight : todayMidnight;
    const nextOcc = calculateNextRecurrenceDate(task.recurrenceRule, baseEpoch, baseEpoch);
    if (nextOcc != null) return startOfDayEpoch(new Date(nextOcc));
  }
  if (deadlineMidnight != null && deadlineMidnight > todayMidnight) {
    return deadlineMidnight;
  }
  const futureWhen = whenMidnight != null && whenMidnight >= todayMidnight ? whenMidnight : null;
  const futureDeadline =
    deadlineMidnight != null && deadlineMidnight >= todayMidnight ? deadlineMidnight : null;
  return futureWhen ?? futureDeadline ?? addDaysLocal(todayMidnight, 1);
}

export function formatRecurrenceShort(rule: RecurrenceRule, lang: SupportedLang): string {
  const n = Math.max(1, Math.min(999, Math.floor(rule.interval || 1)));
  const weekdayShort: Record<SupportedLang, string[]> = {
    en: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    de: ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'],
    fr: ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'],
    it: ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'],
    es: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']
  };
  const lastDayWord: Record<SupportedLang, string> = {
    en: 'Last day',
    de: 'Letzter Tag',
    fr: 'Dernier jour',
    it: 'Ultimo giorno',
    es: 'Último día'
  };

  const names = weekdayShort[lang] || weekdayShort.en;
  const days =
    rule.mode === 'FIXED_SCHEDULE' && rule.unit === 'WEEKS'
      ? Array.from(new Set((rule.daysOfWeek || []).filter((d) => d >= 1 && d <= 7)))
          .sort((a, b) => a - b)
          .map((d) => names[d - 1])
          .join(',')
      : '';
  const daysPart = days ? ` (${days})` : '';

  let domPart = '';
  if (rule.mode === 'FIXED_SCHEDULE' && rule.unit === 'MONTHS' && rule.dayOfMonth != null) {
    if (rule.dayOfMonth === -1) {
      domPart = ` (${lastDayWord[lang] || lastDayWord.en})`;
    } else if (rule.dayOfMonth >= 1 && rule.dayOfMonth <= 31) {
      domPart = ` (${rule.dayOfMonth}.)`;
    }
  }

  const labels: Record<SupportedLang, Record<RecurrenceUnit, [string, string]>> = {
    en: {
      DAYS: ['Daily', `Every ${n} d.`],
      WEEKS: [`Weekly${daysPart}`, `Every ${n} wk.${daysPart}`],
      MONTHS: [`Monthly${domPart}`, `Every ${n} mo.${domPart}`],
      YEARS: ['Yearly', `Every ${n} yr.`]
    },
    de: {
      DAYS: ['Täglich', `Alle ${n} Tg.`],
      WEEKS: [`Wöchentlich${daysPart}`, `Alle ${n} Wo.${daysPart}`],
      MONTHS: [`Monatlich${domPart}`, `Alle ${n} Mon.${domPart}`],
      YEARS: ['Jährlich', `Alle ${n} J.`]
    },
    fr: {
      DAYS: ['Quotidien', `Tous les ${n} j.`],
      WEEKS: [`Hebdo.${daysPart}`, `Toutes les ${n} sem.${daysPart}`],
      MONTHS: [`Mensuel${domPart}`, `Tous les ${n} mois${domPart}`],
      YEARS: ['Annuel', `Tous les ${n} ans`]
    },
    it: {
      DAYS: ['Ogni giorno', `Ogni ${n} gg.`],
      WEEKS: [`Ogni sett.${daysPart}`, `Ogni ${n} sett.${daysPart}`],
      MONTHS: [`Ogni mese${domPart}`, `Ogni ${n} mesi${domPart}`],
      YEARS: ['Ogni anno', `Ogni ${n} anni`]
    },
    es: {
      DAYS: ['Diario', `Cada ${n} d.`],
      WEEKS: [`Semanal${daysPart}`, `Cada ${n} sem.${daysPart}`],
      MONTHS: [`Mensual${domPart}`, `Cada ${n} mes.${domPart}`],
      YEARS: ['Anual', `Cada ${n} a.`]
    }
  };

  const base = (labels[lang] || labels.en)[rule.unit][n === 1 ? 0 : 1];
  if (rule.mode === 'AFTER_COMPLETION') {
    const suffix: Record<SupportedLang, string> = {
      en: ' after compl.',
      de: ' n. Erledigung',
      fr: ' après achèv.',
      it: ' dopo compl.',
      es: ' tras compl.'
    };
    return base + (suffix[lang] || suffix.en);
  }
  return base;
}

const CALENDAR_DAY_MS = 86_400_000;

export function localEpochDayFromMillis(ms: number): number {
  const d = new Date(ms);
  return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / CALENDAR_DAY_MS);
}

export function resolveCalendarEventEpochDayRange(
  beginMillis: number,
  endMillis: number,
  isAllDay: boolean
): [number, number] {
  if (isAllDay) {
    const startDate = Math.floor(beginMillis / CALENDAR_DAY_MS);
    const effectiveEndMillis =
      endMillis > beginMillis ? endMillis : beginMillis + CALENDAR_DAY_MS;
    const endExclusiveDate = Math.floor(effectiveEndMillis / CALENDAR_DAY_MS);
    const inclusiveEnd = Math.max(startDate, endExclusiveDate - 1);
    return [startDate, inclusiveEnd];
  }
  const startDate = localEpochDayFromMillis(beginMillis);
  const inclusiveEndMillis = endMillis > beginMillis ? endMillis - 1 : beginMillis;
  const endDate = localEpochDayFromMillis(inclusiveEndMillis);
  return [startDate, Math.max(startDate, endDate)];
}

export function isCalendarEventActiveOrUpcoming(
  ev: CalendarEventItem,
  forEpochDay: number,
  todayEpochDay: number,
  nowMillis: number,
  upcomingOnly: boolean = false
): boolean {
  if (upcomingOnly) {
    if (forEpochDay <= todayEpochDay) return false;
  } else {
    if (forEpochDay < todayEpochDay) return false;
  }
  const [evStartDay, evEndDay] = resolveCalendarEventEpochDayRange(
    ev.startMillis,
    ev.endMillis,
    ev.isAllDay
  );
  if (forEpochDay < evStartDay || forEpochDay > evEndDay) {
    return false;
  }
  if (evEndDay < todayEpochDay) {
    return false;
  }
  if (!ev.isAllDay && ev.endMillis <= nowMillis) {
    return false;
  }
  return true;
}

export function filterActiveOrUpcomingCalendarEvents(
  events: readonly CalendarEventItem[],
  forEpochDay: number,
  todayEpochDay: number,
  nowMillis: number,
  upcomingOnly: boolean = false
): CalendarEventItem[] {
  if (events.length === 0) return [];
  return events.filter((ev) =>
    isCalendarEventActiveOrUpcoming(ev, forEpochDay, todayEpochDay, nowMillis, upcomingOnly)
  );
}

