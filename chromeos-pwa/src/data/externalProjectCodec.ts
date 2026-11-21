import {
  startOfDayEpoch,
  toIsoDateInputValue,
  type ChecklistItemRecord,
  type EffortLevel,
  type HeadingRecord,
  type ProjectRecord,
  type TagRecord,
  type TaskRecord
} from '../domain/types.ts';
import { detectCsvDelimiter, escapeCsvCell, parseCsvRows } from './csvCodec.ts';

export type ExternalExportFormat =
  | 'asana-csv'
  | 'asana-json'
  | 'planner-xlsx'
  | 'planner-csv';

export interface ExternalImportedChecklistItem {
  title: string;
  isCompleted: boolean;
}

export interface ExternalImportedTask {
  title: string;
  notes: string;
  status: 'OPEN' | 'COMPLETED';
  completedAt: number | null;
  whenDate: number | null;
  deadline: number | null;
  isPinned: boolean;
  isMilestone?: boolean;
  effortLevel: EffortLevel | null;
  tagNames: string[];
  checklistItems: ExternalImportedChecklistItem[];
  assigneeId: string | null;
  workspaceId?: string | null;
}

export interface ExternalImportedPhase {
  title: string;
  startDate: number | null;
  endDate: number | null;
  tasks: ExternalImportedTask[];
}

export interface ExternalImportedProject {
  title: string;
  notes: string;
  ungroupedTasks: ExternalImportedTask[];
  phases: ExternalImportedPhase[];
  tagNames: string[];
  totalTaskCount: number;
  workspaceId?: string | null;
}

export interface ExternalExportProjectInput {
  project: ProjectRecord;
  headings: HeadingRecord[];
  tasks: TaskRecord[];
  checklistItems: ChecklistItemRecord[];
  tags: TagRecord[];
}

export interface ZipEntryWriteSpec {
  name: string;
  data: Uint8Array;
  method?: 0 | 8;
  useDataDescriptor?: boolean;
}

const utf8Encoder = new TextEncoder();
const utf8Decoder = new TextDecoder('utf-8');

const EXCEL_EPOCH_UTC_MS = Date.UTC(1899, 11, 30);
const DAY_MS = 86_400_000;

// ==================== Date & Text Helpers ====================

function buildValidLocalMidnight(year: number, month1Based: number, day: number): number | null {
  if (year < 1900 || year > 9999 || month1Based < 1 || month1Based > 12 || day < 1 || day > 31) {
    return null;
  }
  const d = new Date(year, month1Based - 1, day, 0, 0, 0, 0);
  if (
    d.getFullYear() !== year ||
    d.getMonth() !== month1Based - 1 ||
    d.getDate() !== day
  ) {
    return null;
  }
  return d.getTime();
}

export function excelSerialToLocalEpoch(serial: number): number | null {
  if (!Number.isFinite(serial)) return null;
  const wholeDays = Math.floor(serial);
  if (wholeDays < 1 || wholeDays > 2_958_465) return null;
  const utc = new Date(EXCEL_EPOCH_UTC_MS + wholeDays * DAY_MS);
  return buildValidLocalMidnight(
    utc.getUTCFullYear(),
    utc.getUTCMonth() + 1,
    utc.getUTCDate()
  );
}

export function localEpochToExcelSerial(epochMillis: number): number {
  const d = new Date(epochMillis);
  const utcMidnight = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  return Math.round((utcMidnight - EXCEL_EPOCH_UTC_MS) / DAY_MS);
}

export function parseExternalLocalDate(raw: unknown): number | null {
  if (raw == null) return null;
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) return null;
    if (raw > 100_000_000_000) {
      return startOfDayEpoch(new Date(raw));
    }
    return excelSerialToLocalEpoch(raw);
  }
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;

  // ISO YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss...
  const isoMatch = trimmed.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s].*)?$/);
  if (isoMatch) {
    return buildValidLocalMidnight(
      Number(isoMatch[1]),
      Number(isoMatch[2]),
      Number(isoMatch[3])
    );
  }

  // Dot-separated DD.MM.YYYY
  const dotMatch = trimmed.match(/^(\d{1,2})\.(\d{1,2})\.(\d{2,4})(?:\s+.*)?$/);
  if (dotMatch) {
    const p1 = Number(dotMatch[1]);
    const p2 = Number(dotMatch[2]);
    let year = Number(dotMatch[3]);
    if (year < 100) year += 2000;
    if (p2 > 12 && p1 >= 1 && p1 <= 12) {
      return buildValidLocalMidnight(year, p1, p2);
    }
    return buildValidLocalMidnight(year, p2, p1);
  }

  // Slash-separated MM/DD/YYYY or DD/MM/YYYY
  const slashMatch = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2,4})(?:\s+.*)?$/);
  if (slashMatch) {
    const p1 = Number(slashMatch[1]);
    const p2 = Number(slashMatch[2]);
    let year = Number(slashMatch[3]);
    if (year < 100) year += 2000;
    // If first part > 12, it is DD/MM/YYYY; otherwise standard US Planner/Excel MM/DD/YYYY
    if (p1 > 12 && p2 >= 1 && p2 <= 12) {
      return buildValidLocalMidnight(year, p2, p1);
    }
    return buildValidLocalMidnight(year, p1, p2);
  }

  // Pure numeric string: Excel serial date (e.g. "46154" or "46154.0") or epoch millis
  if (/^\d+(?:\.\d+)?$/.test(trimmed)) {
    const num = Number(trimmed);
    if (Number.isFinite(num)) {
      if (num > 100_000_000_000) {
        return startOfDayEpoch(new Date(num));
      }
      return excelSerialToLocalEpoch(num);
    }
  }

  const parsedMs = Date.parse(trimmed);
  if (Number.isFinite(parsedMs)) {
    return startOfDayEpoch(new Date(parsedMs));
  }
  return null;
}

function parseExternalTimestamp(raw: unknown): number | null {
  if (raw == null) return null;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    if (raw > 100_000_000_000) return Math.trunc(raw);
    return excelSerialToLocalEpoch(raw);
  }
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;

  if (trimmed.includes('T') || trimmed.includes(':')) {
    const parsed = Date.parse(trimmed);
    if (Number.isFinite(parsed)) return parsed;
  }
  return parseExternalLocalDate(trimmed);
}

function normalizeEffort(raw: unknown): EffortLevel | null {
  if (raw === 'QUICK_WIN' || raw === 'MEDIUM' || raw === 'DEEP_FOCUS') {
    return raw;
  }
  return null;
}

function deduplicateStrings(items: readonly string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const raw of items) {
    const trimmed = raw.trim();
    if (!trimmed) continue;
    const key = trimmed.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      result.push(trimmed);
    }
  }
  return result;
}

function splitTagNames(raw: string): string[] {
  if (!raw || !raw.trim()) return [];
  return deduplicateStrings(raw.split(/[,;]/g));
}

function splitPlannerChecklistField(raw: string): string[] {
  if (!raw || !raw.trim()) return [];
  const out: string[] = [];
  let buf = '';
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === '\\' && i + 1 < raw.length && (raw[i + 1] === ';' || raw[i + 1] === '\\')) {
      buf += raw[i + 1];
      i++;
      continue;
    }
    if (ch === ';' || ch === '\n' || ch === '\r') {
      if (ch === '\r' && raw[i + 1] === '\n') i++;
      const trimmed = buf.trim();
      if (trimmed) out.push(trimmed);
      buf = '';
    } else {
      buf += ch;
    }
  }
  const tail = buf.trim();
  if (tail) out.push(tail);
  return out;
}

function detectHeaderDelimiter(rawCsv: string): ',' | ';' {
  const cleaned = rawCsv.replace(/^\uFEFF/, '');
  let inQuotes = false;
  let commas = 0;
  let semis = 0;
  for (let i = 0; i < cleaned.length; i++) {
    const ch = cleaned[i];
    if (inQuotes) {
      if (ch === '"') {
        if (cleaned[i + 1] === '"') i++;
        else inQuotes = false;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      commas++;
    } else if (ch === ';') {
      semis++;
    } else if (ch === '\r' || ch === '\n') {
      if (commas > 0 || semis > 0) {
        return semis > commas ? ';' : ',';
      }
    }
  }
  if (commas > 0 || semis > 0) {
    return semis > commas ? ';' : ',';
  }
  return detectCsvDelimiter(cleaned);
}

function normalizeHeaderKey(header: string): string {
  return header
    .replace(/^\uFEFF/, '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ');
}

function findColumnIndex(headers: readonly string[], aliases: readonly string[]): number {
  const aliasSet = new Set(aliases.map((a) => a.toLowerCase()));
  for (let i = 0; i < headers.length; i++) {
    if (aliasSet.has(headers[i])) return i;
  }
  return -1;
}

function isUngroupedSectionName(raw: string): boolean {
  const norm = raw.trim().toLowerCase();
  return (
    norm.length === 0 ||
    norm === '(no section)' ||
    norm === 'no section' ||
    norm === 'untitled section' ||
    norm === 'unbenannter abschnitt' ||
    norm === 'kein abschnitt' ||
    norm === '(kein abschnitt)' ||
    norm === 'section sans titre' ||
    norm === 'aucune section' ||
    norm === 'sezione senza titolo' ||
    norm === 'nessuna sezione' ||
    norm === 'sección sin título' ||
    norm === 'seccion sin titulo' ||
    norm === 'sin sección'
  );
}

function sanitizeFileSlug(title: string): string {
  const slug = title
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u00c0-\u024f_-]+/gi, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return slug || 'thingsbook-project';
}

function stripExtensionFromFileName(fileNameHint?: string): string {
  if (!fileNameHint) return '';
  const base = fileNameHint.split(/[/\\]/).pop() ?? '';
  return base.replace(/\.(xlsx|csv|json)$/i, '').trim();
}

// ==================== 1. Asana CSV Import & Export ====================

export const ASANA_CSV_HEADERS = [
  'Task ID',
  'Created At',
  'Completed At',
  'Last Modified',
  'Name',
  'Section/Column',
  'Assignee',
  'Start Date',
  'Due Date',
  'Tags',
  'Notes',
  'Projects',
  'Parent task'
] as const;

export function importAsanaCsv(
  rawCsv: string,
  fallbackTitle?: string
): ExternalImportedProject {
  const cleaned = rawCsv.replace(/^\uFEFF/, '');
  const delimiter = detectHeaderDelimiter(cleaned);
  const rows = parseCsvRows(cleaned, delimiter);
  const defaultTitle = fallbackTitle?.trim() || 'Imported Asana Project';

  if (rows.length === 0) {
    return {
      title: defaultTitle,
      notes: '',
      ungroupedTasks: [],
      phases: [],
      tagNames: [],
      totalTaskCount: 0
    };
  }

  const headers = rows[0].map(normalizeHeaderKey);
  const idxTaskId = findColumnIndex(headers, [
    'task id', 'id', 'gid', 'aufgaben-id', 'id de la tâche', 'id de la tache', 'id attività', 'id attivita', 'id de tarea'
  ]);
  const idxCompletedAt = findColumnIndex(headers, [
    'completed at', 'completed date', 'abgeschlossen am', 'erledigt am', 'terminé le', 'termine le', 'completato il', 'completada el', 'finalizado el'
  ]);
  const idxName = findColumnIndex(headers, [
    'name', 'task name', 'title', 'aufgabe', 'aufgabenname', 'nom', 'nom de la tâche', 'nome', 'nombre', 'nombre de la tarea'
  ]);
  const idxSection = findColumnIndex(headers, [
    'section/column', 'section', 'column', 'abschnitt/spalte', 'abschnitt', 'spalte',
    'section/colonne', 'colonne', 'sezione/colonna', 'sezione', 'colonna', 'sección/columna', 'seccion/columna', 'sección', 'columna'
  ]);
  const idxAssignee = findColumnIndex(headers, [
    'assignee', 'assignee email', 'assigned to', 'verantwortlich', 'zugewiesen an',
    'responsable', 'assegnatario', 'incaricato', 'responsable de la tarea', 'asignado a'
  ]);
  const idxStartDate = findColumnIndex(headers, [
    'start date', 'start_on', 'startdatum', 'date de début', 'date de debut', 'data di inizio', 'fecha de inicio'
  ]);
  const idxDueDate = findColumnIndex(headers, [
    'due date', 'due_on', 'deadline', 'fälligkeitsdatum', 'faelligkeitsdatum',
    'date d’échéance', "date d'échéance", "date d'echeance", 'data di scadenza', 'fecha de entrega', 'fecha de vencimiento'
  ]);
  const idxTags = findColumnIndex(headers, [
    'tags', 'categories', 'labels', 'kategorien', 'étiquettes', 'etiquettes', 'etichette', 'etiquetas'
  ]);
  const idxNotes = findColumnIndex(headers, [
    'notes', 'description', 'notizen', 'beschreibung', 'note', 'notas', 'descripción', 'descripcion'
  ]);
  const idxProjects = findColumnIndex(headers, [
    'projects', 'project', 'projekte', 'projekt', 'projets', 'projet', 'progetti', 'progetto', 'proyectos', 'proyecto'
  ]);
  const idxParent = findColumnIndex(headers, [
    'parent task', 'parent', 'parent id', 'übergeordnete aufgabe', 'uebergeordnete aufgabe',
    'tâche parente', 'tache parente', 'attività principale', 'attivita principale', 'tarea principal', 'tarea superior'
  ]);

  const cellAt = (row: readonly string[], idx: number): string =>
    idx >= 0 && idx < row.length ? row[idx].trim() : '';

  interface PendingTopTask {
    rawId: string;
    sectionName: string;
    task: ExternalImportedTask;
  }

  interface PendingSubtask {
    parentRef: string;
    sectionName: string;
    title: string;
    isCompleted: boolean;
    fallbackTask: ExternalImportedTask;
  }

  let detectedProjectTitle = '';
  const topTasks: PendingTopTask[] = [];
  const subtasks: PendingSubtask[] = [];
  const byTaskId = new Map<string, ExternalImportedTask>();
  const byTaskIdLower = new Map<string, ExternalImportedTask>();
  const byName = new Map<string, ExternalImportedTask>();
  const byNameLower = new Map<string, ExternalImportedTask>();
  const orderedPhaseNames: string[] = [];
  const seenPhaseKeys = new Set<string>();

  const registerPhaseName = (sec: string) => {
    if (isUngroupedSectionName(sec)) return;
    const key = sec.toLowerCase();
    if (!seenPhaseKeys.has(key)) {
      seenPhaseKeys.add(key);
      orderedPhaseNames.push(sec);
    }
  };

  // Pass 1: Separate top-level tasks and subtasks so row order never matters
  for (let r = 1; r < rows.length; r++) {
    const row = rows[r];
    const title = idxName >= 0 ? cellAt(row, idxName) : (row[0]?.trim() ?? '');
    if (!title) continue;

    const rawProjects = cellAt(row, idxProjects);
    if (!detectedProjectTitle && rawProjects) {
      const firstProj = rawProjects.split(/[,;]/)[0]?.trim();
      if (firstProj) detectedProjectTitle = firstProj;
    }

    const rawId = cellAt(row, idxTaskId);
    const completedAtStr = cellAt(row, idxCompletedAt);
    const sectionRaw = cellAt(row, idxSection);
    const sectionName = isUngroupedSectionName(sectionRaw) ? '' : sectionRaw;
    const assigneeRaw = cellAt(row, idxAssignee);
    const whenDate = parseExternalLocalDate(cellAt(row, idxStartDate));
    const deadline = parseExternalLocalDate(cellAt(row, idxDueDate));
    const tagNames = splitTagNames(cellAt(row, idxTags));
    const notes = idxNotes >= 0 && idxNotes < row.length ? row[idxNotes].trim() : '';
    const parentRef = cellAt(row, idxParent);

    const isCompleted = completedAtStr.length > 0;
    const completedAt = isCompleted
      ? (parseExternalTimestamp(completedAtStr) ?? Date.now())
      : null;

    const taskObj: ExternalImportedTask = {
      title,
      notes,
      status: isCompleted ? 'COMPLETED' : 'OPEN',
      completedAt,
      whenDate,
      deadline,
      isPinned: false,
      effortLevel: null,
      tagNames,
      checklistItems: [],
      assigneeId: assigneeRaw || null
    };

    if (parentRef.length > 0) {
      subtasks.push({
        parentRef,
        sectionName,
        title,
        isCompleted,
        fallbackTask: taskObj
      });
    } else {
      registerPhaseName(sectionName);
      topTasks.push({ rawId, sectionName, task: taskObj });
      if (rawId) {
        if (!byTaskId.has(rawId)) byTaskId.set(rawId, taskObj);
        if (!byTaskIdLower.has(rawId.toLowerCase())) {
          byTaskIdLower.set(rawId.toLowerCase(), taskObj);
        }
      }
      if (!byName.has(title)) byName.set(title, taskObj);
      if (!byNameLower.has(title.toLowerCase())) {
        byNameLower.set(title.toLowerCase(), taskObj);
      }
    }
  }

  // Pass 2: Attach subtasks onto matching parent task (by Task ID or Name)
  for (const sub of subtasks) {
    const parent =
      byTaskId.get(sub.parentRef) ??
      byTaskIdLower.get(sub.parentRef.toLowerCase()) ??
      byName.get(sub.parentRef) ??
      byNameLower.get(sub.parentRef.toLowerCase());

    if (parent) {
      parent.checklistItems.push({
        title: sub.title,
        isCompleted: sub.isCompleted
      });
    } else {
      registerPhaseName(sub.sectionName);
      topTasks.push({
        rawId: '',
        sectionName: sub.sectionName,
        task: sub.fallbackTask
      });
    }
  }

  const ungroupedTasks: ExternalImportedTask[] = [];
  const phaseMap = new Map<string, ExternalImportedPhase>();
  for (const phaseTitle of orderedPhaseNames) {
    phaseMap.set(phaseTitle.toLowerCase(), {
      title: phaseTitle,
      startDate: null,
      endDate: null,
      tasks: []
    });
  }

  for (const entry of topTasks) {
    if (!entry.sectionName) {
      ungroupedTasks.push(entry.task);
    } else {
      const phase = phaseMap.get(entry.sectionName.toLowerCase());
      if (phase) {
        phase.tasks.push(entry.task);
      } else {
        ungroupedTasks.push(entry.task);
      }
    }
  }

  const phases = Array.from(phaseMap.values());
  const totalTaskCount =
    ungroupedTasks.length + phases.reduce((sum, p) => sum + p.tasks.length, 0);

  return {
    title: detectedProjectTitle || defaultTitle,
    notes: '',
    ungroupedTasks,
    phases,
    tagNames: [],
    totalTaskCount
  };
}

function prepareOrderedProjectContent(input: ExternalExportProjectInput) {
  const activeHeadings = input.headings
    .filter((h) => h.projectId === input.project.id && !h.isDeleted && !h.isArchived)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

  const headingById = new Map(activeHeadings.map((h) => [h.id, h]));

  const validTasks = input.tasks
    .filter(
      (t) =>
        t.projectId === input.project.id &&
        !t.isDeleted &&
        (t.status === 'OPEN' || t.status === 'COMPLETED')
    )
    .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

  const checklistByTaskId = new Map<string, ChecklistItemRecord[]>();
  const sortedChecklists = input.checklistItems
    .filter((c) => !c.isDeleted)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt - b.createdAt);

  for (const c of sortedChecklists) {
    const arr = checklistByTaskId.get(c.taskId) ?? [];
    arr.push(c);
    checklistByTaskId.set(c.taskId, arr);
  }

  const tagById = new Map<string, TagRecord>();
  for (const tg of input.tags) {
    if (!tg.isDeleted) tagById.set(tg.id, tg);
  }

  const ungroupedTasks: TaskRecord[] = [];
  const tasksByHeadingId = new Map<string, TaskRecord[]>();
  for (const h of activeHeadings) {
    tasksByHeadingId.set(h.id, []);
  }

  for (const tk of validTasks) {
    if (tk.headingId && headingById.has(tk.headingId)) {
      tasksByHeadingId.get(tk.headingId)!.push(tk);
    } else {
      ungroupedTasks.push(tk);
    }
  }

  const orderedGroups: Array<{ heading: HeadingRecord | null; tasks: TaskRecord[] }> = [];
  if (ungroupedTasks.length > 0) {
    orderedGroups.push({ heading: null, tasks: ungroupedTasks });
  }
  for (const h of activeHeadings) {
    orderedGroups.push({ heading: h, tasks: tasksByHeadingId.get(h.id) ?? [] });
  }

  return {
    activeHeadings,
    orderedGroups,
    checklistByTaskId,
    tagById
  };
}

export function exportProjectToAsanaCsv(input: ExternalExportProjectInput): string {
  const { orderedGroups, checklistByTaskId, tagById } = prepareOrderedProjectContent(input);
  const lines: string[] = [ASANA_CSV_HEADERS.map(escapeCsvCell).join(',')];

  for (const group of orderedGroups) {
    const sectionName = group.heading?.title ?? '';
    for (const tk of group.tasks) {
      const tagNames = tk.tagIds
        .map((id) => tagById.get(id)?.title)
        .filter((v): v is string => Boolean(v))
        .join(', ');

      const createdIso = new Date(tk.createdAt).toISOString();
      const completedIso =
        tk.status === 'COMPLETED'
          ? new Date(tk.completedAt ?? tk.updatedAt).toISOString()
          : '';
      const updatedIso = new Date(tk.updatedAt).toISOString();

      lines.push(
        [
          tk.id,
          createdIso,
          completedIso,
          updatedIso,
          tk.title,
          sectionName,
          tk.assigneeId ?? '',
          toIsoDateInputValue(tk.whenDate),
          toIsoDateInputValue(tk.deadline),
          tagNames,
          tk.notes,
          input.project.title,
          ''
        ]
          .map(escapeCsvCell)
          .join(',')
      );

      const checklists = checklistByTaskId.get(tk.id) ?? [];
      for (const chk of checklists) {
        lines.push(
          [
            chk.id,
            new Date(chk.createdAt).toISOString(),
            chk.isCompleted ? new Date(chk.updatedAt).toISOString() : '',
            new Date(chk.updatedAt).toISOString(),
            chk.title,
            sectionName,
            '',
            '',
            '',
            '',
            '',
            input.project.title,
            tk.title
          ]
            .map(escapeCsvCell)
            .join(',')
        );
      }
    }
  }

  return `\uFEFF${lines.join('\r\n')}\r\n`;
}

// ==================== 2. Asana JSON Import & Export ====================

function extractObjectName(val: unknown): string {
  if (typeof val === 'string') return val.trim();
  if (val && typeof val === 'object') {
    const obj = val as Record<string, unknown>;
    if (typeof obj.email === 'string' && obj.email.trim()) return obj.email.trim();
    if (typeof obj.name === 'string' && obj.name.trim()) return obj.name.trim();
    if (typeof obj.title === 'string' && obj.title.trim()) return obj.title.trim();
    if (typeof obj.gid === 'string' && obj.gid.trim()) return obj.gid.trim();
    if (typeof obj.id === 'string' && obj.id.trim()) return obj.id.trim();
  }
  return '';
}

function extractWorkspaceString(val: unknown): string | null {
  if (typeof val === 'string' && val.trim()) return val.trim();
  if (val && typeof val === 'object') {
    const obj = val as Record<string, unknown>;
    if (typeof obj.gid === 'string' && obj.gid.trim()) return obj.gid.trim();
    if (typeof obj.id === 'string' && obj.id.trim()) return obj.id.trim();
    if (typeof obj.name === 'string' && obj.name.trim()) return obj.name.trim();
  }
  return null;
}

function extractJsonTagNames(rawTags: unknown): string[] {
  if (!Array.isArray(rawTags)) return [];
  const names: string[] = [];
  for (const item of rawTags) {
    if (typeof item === 'string') {
      names.push(item);
    } else if (item && typeof item === 'object') {
      const n = extractObjectName(item);
      if (n) names.push(n);
    }
  }
  return deduplicateStrings(names);
}

function parseAsanaJsonTaskObject(raw: Record<string, unknown>): {
  gid: string;
  parentRef: string;
  sectionHint: string;
  projectHint: string;
  task: ExternalImportedTask;
} | null {
  const title =
    (typeof raw.name === 'string' ? raw.name.trim() : '') ||
    (typeof raw.title === 'string' ? raw.title.trim() : '');
  if (!title) return null;

  const gid =
    (typeof raw.gid === 'string' ? raw.gid.trim() : '') ||
    (typeof raw.id === 'string' ? raw.id.trim() : '');

  const notes =
    typeof raw.notes === 'string'
      ? raw.notes
      : typeof raw.description === 'string'
        ? raw.description
        : '';

  const isCompleted =
    raw.completed === true ||
    raw.status === 'COMPLETED' ||
    (typeof raw.completed_at === 'string' && raw.completed_at.trim().length > 0);

  const completedAt = isCompleted
    ? (parseExternalTimestamp(raw.completed_at ?? raw.completedAt) ?? Date.now())
    : null;

  const whenDate = parseExternalLocalDate(
    raw.start_on ?? raw.start_at ?? raw.startDate ?? raw.whenDate
  );
  const deadline = parseExternalLocalDate(
    raw.due_on ?? raw.due_at ?? raw.dueDate ?? raw.deadline
  );

  const isMilestone =
    raw.resource_subtype === 'milestone' ||
    raw.is_milestone === true ||
    raw.isMilestone === true;

  let isPinned = Boolean(raw.is_pinned ?? raw.isPinned ?? raw.liked);
  let effortLevel = normalizeEffort(raw.effort_level ?? raw.effortLevel);
  if (typeof raw.priority === 'string') {
    const prio = raw.priority.trim().toLowerCase();
    if (prio === 'high' || prio === 'urgent') isPinned = true;
    else if (prio === 'important' && !effortLevel) effortLevel = 'DEEP_FOCUS';
    else if (prio === 'low' && !effortLevel) effortLevel = 'QUICK_WIN';
  }

  const tagNames = extractJsonTagNames(raw.tags);
  const assigneeStr = extractObjectName(raw.assignee);
  const workspaceId =
    extractWorkspaceString(raw.workspace) ??
    extractWorkspaceString(raw.workspaceId) ??
    extractWorkspaceString(raw.workspace_id);

  const checklistItems: ExternalImportedChecklistItem[] = [];
  if (Array.isArray(raw.subtasks)) {
    for (const sub of raw.subtasks) {
      if (!sub || typeof sub !== 'object') continue;
      const subObj = sub as Record<string, unknown>;
      const subTitle =
        (typeof subObj.name === 'string' ? subObj.name.trim() : '') ||
        (typeof subObj.title === 'string' ? subObj.title.trim() : '');
      if (!subTitle) continue;
      const subCompleted =
        subObj.completed === true ||
        subObj.isCompleted === true ||
        (typeof subObj.completed_at === 'string' && subObj.completed_at.trim().length > 0);
      checklistItems.push({ title: subTitle, isCompleted: subCompleted });
    }
  } else if (Array.isArray(raw.checklistItems)) {
    for (const sub of raw.checklistItems) {
      if (typeof sub === 'string' && sub.trim()) {
        checklistItems.push({ title: sub.trim(), isCompleted: false });
      } else if (sub && typeof sub === 'object') {
        const subObj = sub as Record<string, unknown>;
        const subTitle = extractObjectName(subObj);
        if (subTitle) {
          checklistItems.push({
            title: subTitle,
            isCompleted: Boolean(subObj.isCompleted ?? subObj.completed)
          });
        }
      }
    }
  }

  let sectionHint = '';
  let projectHint = '';

  if (Array.isArray(raw.memberships)) {
    for (const mem of raw.memberships) {
      if (!mem || typeof mem !== 'object') continue;
      const memObj = mem as Record<string, unknown>;
      if (!projectHint && memObj.project) {
        projectHint = extractObjectName(memObj.project);
      }
      if (!sectionHint && memObj.section) {
        const secName = extractObjectName(memObj.section);
        if (!isUngroupedSectionName(secName)) {
          sectionHint = secName;
        }
      }
    }
  }

  if (!sectionHint && raw.section) {
    const secName = extractObjectName(raw.section);
    if (!isUngroupedSectionName(secName)) {
      sectionHint = secName;
    }
  }
  if (!sectionHint && typeof raw.section_gid === 'string') {
    sectionHint = raw.section_gid.trim();
  }

  const parentRef = extractObjectName(raw.parent);

  return {
    gid,
    parentRef,
    sectionHint,
    projectHint,
    task: {
      title,
      notes,
      status: isCompleted ? 'COMPLETED' : 'OPEN',
      completedAt,
      whenDate,
      deadline,
      isPinned,
      isMilestone,
      effortLevel,
      tagNames,
      checklistItems,
      assigneeId: assigneeStr || null,
      workspaceId
    }
  };
}

export function importAsanaJson(
  rawJson: string,
  fallbackTitle?: string
): ExternalImportedProject {
  const cleaned = rawJson.replace(/^\uFEFF/, '').trim();
  const parsed = JSON.parse(cleaned) as unknown;
  const defaultTitle = fallbackTitle?.trim() || 'Imported Asana Project';

  let projectTitle = '';
  let projectNotes = '';
  let projectTagNames: string[] = [];
  let projectWorkspaceId: string | null = null;

  const phaseList: ExternalImportedPhase[] = [];
  const phaseByKey = new Map<string, ExternalImportedPhase>();
  const sectionGidToPhase = new Map<string, ExternalImportedPhase>();

  const ensurePhase = (
    title: string,
    startDate: number | null = null,
    endDate: number | null = null,
    gid?: string
  ): ExternalImportedPhase | null => {
    const trimmed = title.trim();
    if (isUngroupedSectionName(trimmed)) return null;
    const key = trimmed.toLowerCase();
    let existing = phaseByKey.get(key);
    if (!existing) {
      existing = {
        title: trimmed,
        startDate,
        endDate,
        tasks: []
      };
      phaseByKey.set(key, existing);
      phaseList.push(existing);
    } else {
      if (existing.startDate == null && startDate != null) existing.startDate = startDate;
      if (existing.endDate == null && endDate != null) existing.endDate = endDate;
    }
    if (gid) sectionGidToPhase.set(gid, existing);
    return existing;
  };

  let rawTaskArray: unknown[] = [];

  if (Array.isArray(parsed)) {
    rawTaskArray = parsed;
  } else if (parsed && typeof parsed === 'object') {
    const root = parsed as Record<string, unknown>;

    if (typeof root.project === 'string' && root.project.trim()) {
      projectTitle = root.project.trim();
    } else if (root.project && typeof root.project === 'object') {
      const projObj = root.project as Record<string, unknown>;
      projectTitle =
        (typeof projObj.name === 'string' ? projObj.name.trim() : '') ||
        (typeof projObj.title === 'string' ? projObj.title.trim() : '');
      projectNotes =
        typeof projObj.notes === 'string'
          ? projObj.notes
          : typeof projObj.description === 'string'
            ? projObj.description
            : '';
      projectTagNames = extractJsonTagNames(projObj.tags);
      projectWorkspaceId =
        extractWorkspaceString(projObj.workspace) ??
        extractWorkspaceString(projObj.workspaceId) ??
        extractWorkspaceString(projObj.workspace_id);
    } else {
      if (typeof root.name === 'string' && root.name.trim()) {
        projectTitle = root.name.trim();
      } else if (typeof root.title === 'string' && root.title.trim()) {
        projectTitle = root.title.trim();
      }
      if (typeof root.notes === 'string') {
        projectNotes = root.notes;
      }
      projectWorkspaceId =
        extractWorkspaceString(root.workspace) ??
        extractWorkspaceString(root.workspaceId) ??
        extractWorkspaceString(root.workspace_id);
    }

    const hasTopLevelTasks = Array.isArray(root.data) || Array.isArray(root.tasks);

    if (Array.isArray(root.sections)) {
      for (const sec of root.sections) {
        if (typeof sec === 'string') {
          ensurePhase(sec);
        } else if (sec && typeof sec === 'object') {
          const secObj = sec as Record<string, unknown>;
          const secTitle =
            (typeof secObj.name === 'string' ? secObj.name.trim() : '') ||
            (typeof secObj.title === 'string' ? secObj.title.trim() : '');
          const secGid =
            (typeof secObj.gid === 'string' ? secObj.gid.trim() : '') ||
            (typeof secObj.id === 'string' ? secObj.id.trim() : '');
          const secStart = parseExternalLocalDate(secObj.start_on ?? secObj.startDate);
          const secEnd = parseExternalLocalDate(secObj.due_on ?? secObj.endDate);
          const phase = ensurePhase(secTitle, secStart, secEnd, secGid || undefined);

          if (!hasTopLevelTasks && Array.isArray(secObj.tasks)) {
            for (const nestedTask of secObj.tasks) {
              if (!nestedTask || typeof nestedTask !== 'object') continue;
              const parsedNested = parseAsanaJsonTaskObject(
                nestedTask as Record<string, unknown>
              );
              if (parsedNested) {
                if (phase) phase.tasks.push(parsedNested.task);
              }
            }
          }
        }
      }
    }

    if (Array.isArray(root.data)) {
      rawTaskArray = root.data;
    } else if (Array.isArray(root.tasks)) {
      rawTaskArray = root.tasks;
    }
  }

  const ungroupedTasks: ExternalImportedTask[] = [];
  const topTaskByGid = new Map<string, ExternalImportedTask>();
  const topTaskByTitle = new Map<string, ExternalImportedTask>();
  const deferredSubtasks: Array<{
    parentRef: string;
    sectionHint: string;
    task: ExternalImportedTask;
  }> = [];

  for (const item of rawTaskArray) {
    if (!item || typeof item !== 'object') continue;
    const parsedItem = parseAsanaJsonTaskObject(item as Record<string, unknown>);
    if (!parsedItem) continue;

    if (!projectTitle && parsedItem.projectHint) {
      projectTitle = parsedItem.projectHint;
    }
    if (!projectWorkspaceId && parsedItem.task.workspaceId) {
      projectWorkspaceId = parsedItem.task.workspaceId;
    }

    if (parsedItem.parentRef) {
      deferredSubtasks.push({
        parentRef: parsedItem.parentRef,
        sectionHint: parsedItem.sectionHint,
        task: parsedItem.task
      });
      continue;
    }

    if (parsedItem.gid) topTaskByGid.set(parsedItem.gid, parsedItem.task);
    if (!topTaskByTitle.has(parsedItem.task.title.toLowerCase())) {
      topTaskByTitle.set(parsedItem.task.title.toLowerCase(), parsedItem.task);
    }

    if (parsedItem.sectionHint) {
      const targetPhase =
        sectionGidToPhase.get(parsedItem.sectionHint) ??
        ensurePhase(parsedItem.sectionHint);
      if (targetPhase) {
        targetPhase.tasks.push(parsedItem.task);
      } else {
        ungroupedTasks.push(parsedItem.task);
      }
    } else {
      ungroupedTasks.push(parsedItem.task);
    }
  }

  for (const sub of deferredSubtasks) {
    const parent =
      topTaskByGid.get(sub.parentRef) ??
      topTaskByTitle.get(sub.parentRef.toLowerCase());
    if (parent) {
      parent.checklistItems.push({
        title: sub.task.title,
        isCompleted: sub.task.status === 'COMPLETED'
      });
    } else if (sub.sectionHint) {
      const targetPhase =
        sectionGidToPhase.get(sub.sectionHint) ?? ensurePhase(sub.sectionHint);
      if (targetPhase) targetPhase.tasks.push(sub.task);
      else ungroupedTasks.push(sub.task);
    } else {
      ungroupedTasks.push(sub.task);
    }
  }

  const totalTaskCount =
    ungroupedTasks.length + phaseList.reduce((sum, p) => sum + p.tasks.length, 0);

  return {
    title: projectTitle || defaultTitle,
    notes: projectNotes,
    ungroupedTasks,
    phases: phaseList,
    tagNames: projectTagNames,
    totalTaskCount,
    workspaceId: projectWorkspaceId
  };
}

export function exportProjectToAsanaJson(input: ExternalExportProjectInput): string {
  const { activeHeadings, orderedGroups, checklistByTaskId, tagById } =
    prepareOrderedProjectContent(input);

  const projectTags = input.project.tagIds
    .map((id) => tagById.get(id))
    .filter((tg): tg is TagRecord => Boolean(tg))
    .map((tg) => ({ gid: tg.id, name: tg.title }));

  const sections = activeHeadings.map((h) => ({
    gid: h.id,
    name: h.title,
    start_on: h.startDate != null ? toIsoDateInputValue(h.startDate) : null,
    due_on: h.endDate != null ? toIsoDateInputValue(h.endDate) : null
  }));

  const data = [];
  for (const group of orderedGroups) {
    const sectionRef = group.heading
      ? {
          gid: group.heading.id,
          name: group.heading.title
        }
      : null;

    for (const tk of group.tasks) {
      const taskTags = tk.tagIds
        .map((id) => tagById.get(id))
        .filter((tg): tg is TagRecord => Boolean(tg))
        .map((tg) => ({ gid: tg.id, name: tg.title }));

      const subtasks = (checklistByTaskId.get(tk.id) ?? []).map((chk) => ({
        gid: chk.id,
        name: chk.title,
        completed: chk.isCompleted
      }));

      data.push({
        gid: tk.id,
        resource_subtype: tk.isMilestone ? 'milestone' : 'default_task',
        name: tk.title,
        notes: tk.notes,
        completed: tk.status === 'COMPLETED',
        completed_at:
          tk.status === 'COMPLETED'
            ? new Date(tk.completedAt ?? tk.updatedAt).toISOString()
            : null,
        start_on: tk.whenDate != null ? toIsoDateInputValue(tk.whenDate) : null,
        due_on: tk.deadline != null ? toIsoDateInputValue(tk.deadline) : null,
        is_pinned: Boolean(tk.isPinned),
        is_milestone: Boolean(tk.isMilestone),
        effort_level: tk.effortLevel ?? null,
        assignee: tk.assigneeId ? { name: tk.assigneeId } : null,
        workspace: tk.workspaceId
          ? { gid: tk.workspaceId, name: tk.workspaceId }
          : input.project.workspaceId
            ? { gid: input.project.workspaceId, name: input.project.workspaceId }
            : null,
        tags: taskTags,
        memberships: [
          {
            project: {
              gid: input.project.id,
              name: input.project.title
            },
            section: sectionRef
          }
        ],
        subtasks
      });
    }
  }

  const payload = {
    project: {
      gid: input.project.id,
      name: input.project.title,
      notes: input.project.notes,
      workspace: input.project.workspaceId
        ? { gid: input.project.workspaceId, name: input.project.workspaceId }
        : null,
      tags: projectTags
    },
    sections,
    data
  };

  return `${JSON.stringify(payload, null, 2)}\n`;
}

// ==================== 3. Microsoft Planner CSV & Row Mapper ====================

export const PLANNER_COLUMNS = [
  'Task ID',
  'Task Name',
  'Bucket Name',
  'Progress',
  'Priority',
  'Assigned To',
  'Created By',
  'Created Date',
  'Start Date',
  'Due Date',
  'Late',
  'Completed Date',
  'Description',
  'Completed Checklist Items',
  'Checklist Items',
  'Labels'
] as const;

function isPlannerProgressCompleted(rawProgress: string, rawCompletedDate: string): boolean {
  const norm = rawProgress.trim().toLowerCase();
  if (
    norm === 'completed' ||
    norm === 'complete' ||
    norm === '100%' ||
    norm === '100' ||
    norm === '1' ||
    norm === 'done' ||
    norm === 'erledigt' ||
    norm === 'abgeschlossen' ||
    norm === 'terminé' ||
    norm === 'termine' ||
    norm === 'completato' ||
    norm === 'completado' ||
    norm === 'completada'
  ) {
    return true;
  }
  if (!norm && rawCompletedDate.trim().length > 0) {
    return true;
  }
  return false;
}

function mapPlannerPriority(rawPriority: string): {
  isPinned: boolean;
  effortLevel: EffortLevel | null;
} {
  const norm = rawPriority.trim().toLowerCase();
  if (norm === 'urgent' || norm === 'dringend' || norm === 'urgente' || norm === '1') {
    return { isPinned: true, effortLevel: null };
  }
  if (
    norm === 'important' ||
    norm === 'wichtig' ||
    norm === 'importante' ||
    norm === 'high' ||
    norm === '3'
  ) {
    return { isPinned: false, effortLevel: 'DEEP_FOCUS' };
  }
  if (
    norm === 'low' ||
    norm === 'niedrig' ||
    norm === 'faible' ||
    norm === 'basse' ||
    norm === 'bassa' ||
    norm === 'baja' ||
    norm === '9'
  ) {
    return { isPinned: false, effortLevel: 'QUICK_WIN' };
  }
  return { isPinned: false, effortLevel: null };
}

function formatPlannerPriority(task: TaskRecord): string {
  if (task.isPinned) return 'Urgent';
  if (task.effortLevel === 'DEEP_FOCUS') return 'Important';
  if (task.effortLevel === 'QUICK_WIN') return 'Low';
  return 'Medium';
}

function parsePlannerChecklists(
  rawChecklist: string,
  rawCompletedChecklist: string
): ExternalImportedChecklistItem[] {
  const allTitles = splitPlannerChecklistField(rawChecklist);
  const completedTrimmed = rawCompletedChecklist.trim();

  if (allTitles.length === 0 && !completedTrimmed) return [];

  // Format 1: "N/M" ratio (e.g. "2/3")
  const ratioMatch = completedTrimmed.match(/^(\d+)\s*\/\s*(\d+)$/);
  if (ratioMatch) {
    const completedCount = Math.max(0, Number(ratioMatch[1]));
    return allTitles.map((title, idx) => ({
      title,
      isCompleted: idx < completedCount
    }));
  }

  // Format 2: Pure numeric count when no item literally equals that number
  if (
    /^\d+$/.test(completedTrimmed) &&
    allTitles.length > 0 &&
    !allTitles.some((t) => t === completedTrimmed)
  ) {
    const completedCount = Math.max(0, Number(completedTrimmed));
    return allTitles.map((title, idx) => ({
      title,
      isCompleted: idx < completedCount
    }));
  }

  // Format 3: Semicolon/newline-separated completed item titles
  const completedTitles = splitPlannerChecklistField(completedTrimmed);
  const remainingCounts = new Map<string, number>();
  for (const ct of completedTitles) {
    const key = ct.toLowerCase();
    remainingCounts.set(key, (remainingCounts.get(key) ?? 0) + 1);
  }

  const result: ExternalImportedChecklistItem[] = [];
  for (const title of allTitles) {
    const key = title.toLowerCase();
    const count = remainingCounts.get(key) ?? 0;
    if (count > 0) {
      result.push({ title, isCompleted: true });
      remainingCounts.set(key, count - 1);
    } else {
      result.push({ title, isCompleted: false });
    }
  }

  // If completed items were listed exclusively in Completed Checklist Items
  for (const ct of completedTitles) {
    const key = ct.toLowerCase();
    const rem = remainingCounts.get(key) ?? 0;
    if (rem > 0) {
      result.push({ title: ct, isCompleted: true });
      remainingCounts.set(key, rem - 1);
    }
  }

  return result;
}

function importPlannerTabularRows(
  rows: readonly string[][],
  fallbackTitle?: string,
  defaultNotes = ''
): ExternalImportedProject {
  const defaultTitle = fallbackTitle?.trim() || 'Imported Planner Project';
  if (rows.length === 0) {
    return {
      title: defaultTitle,
      notes: defaultNotes,
      ungroupedTasks: [],
      phases: [],
      tagNames: [],
      totalTaskCount: 0
    };
  }

  // Locate header row (supports row 0 or Planner Excel preamble rows before table header)
  let headerRowIdx = 0;
  let preambleTitle = '';
  const searchLimit = Math.min(rows.length, 15);
  for (let i = 0; i < searchLimit; i++) {
    const normalized = rows[i].map(normalizeHeaderKey);
    const firstKey = normalized[0] ?? '';
    if (
      (firstKey === 'plan name' || firstKey === 'planname') &&
      rows[i][1]?.trim()
    ) {
      preambleTitle = rows[i][1].trim();
    }
    const hasTaskCol =
      findColumnIndex(normalized, [
        'task name',
        'aufgabenname',
        'nom de la tâche',
        'nome attività',
        'nombre de la tarea'
      ]) >= 0;
    const hasPlannerCol =
      findColumnIndex(normalized, [
        'bucket name',
        'bucket',
        'progress',
        'checklist items',
        'labels'
      ]) >= 0;
    if (hasTaskCol || hasPlannerCol) {
      headerRowIdx = i;
      break;
    }
  }

  const headers = rows[headerRowIdx].map(normalizeHeaderKey);
  const idxTaskName = findColumnIndex(headers, [
    'task name',
    'aufgabenname',
    'nom de la tâche',
    'nome attività',
    'nombre de la tarea',
    'name',
    'title'
  ]);
  const idxBucket = findColumnIndex(headers, [
    'bucket name',
    'bucketname',
    'bucket',
    'compartiment',
    'nom du compartiment',
    'deposito',
    'section/column',
    'section'
  ]);
  const idxProgress = findColumnIndex(headers, [
    'progress',
    'fortschritt',
    'progression',
    'avanzamento',
    'progreso',
    'status'
  ]);
  const idxPriority = findColumnIndex(headers, [
    'priority',
    'priorität',
    'prioritaet',
    'priorité',
    'priorità',
    'priorita',
    'prioridad'
  ]);
  const idxAssignedTo = findColumnIndex(headers, [
    'assigned to',
    'zugewiesen an',
    'attribué à',
    'assegnato a',
    'asignado a',
    'assignee'
  ]);
  const idxStartDate = findColumnIndex(headers, [
    'start date',
    'startdatum',
    'date de début',
    'data di inizio',
    'fecha de inicio'
  ]);
  const idxDueDate = findColumnIndex(headers, [
    'due date',
    'fälligkeitsdatum',
    'date d’échéance',
    "date d'échéance",
    'data di scadenza',
    'fecha de vencimiento',
    'deadline'
  ]);
  const idxCompletedDate = findColumnIndex(headers, [
    'completed date',
    'abschlussdatum',
    'erledigt am',
    'completed at'
  ]);
  const idxDescription = findColumnIndex(headers, [
    'description',
    'beschreibung',
    'notes',
    'descrizione',
    'descripción'
  ]);
  const idxCompletedChecklist = findColumnIndex(headers, [
    'completed checklist items',
    'abgeschlossene checklistenelemente',
    'éléments de liste de contrôle terminés',
    'elementi elenco di controllo completati',
    'elementos de lista de comprobación completados'
  ]);
  const idxChecklist = findColumnIndex(headers, [
    'checklist items',
    'checklistenelemente',
    'éléments de liste de contrôle',
    'elementi elenco di controllo',
    'elementos de lista de comprobación'
  ]);
  const idxLabels = findColumnIndex(headers, [
    'labels',
    'bezeichnungen',
    'étiquettes',
    'etichette',
    'etiquetas',
    'tags',
    'categories'
  ]);

  const cellAt = (row: readonly string[], idx: number): string =>
    idx >= 0 && idx < row.length ? row[idx].trim() : '';

  const ungroupedTasks: ExternalImportedTask[] = [];
  const phases: ExternalImportedPhase[] = [];
  const phaseByKey = new Map<string, ExternalImportedPhase>();

  for (let r = headerRowIdx + 1; r < rows.length; r++) {
    const row = rows[r];
    const title = idxTaskName >= 0 ? cellAt(row, idxTaskName) : (row[1]?.trim() ?? row[0]?.trim() ?? '');
    if (!title) continue;

    const bucketName = cellAt(row, idxBucket);
    const progressRaw = cellAt(row, idxProgress);
    const completedDateRaw = cellAt(row, idxCompletedDate);
    const isCompleted = isPlannerProgressCompleted(progressRaw, completedDateRaw);
    const completedAt = isCompleted
      ? (parseExternalTimestamp(completedDateRaw) ?? Date.now())
      : null;

    const { isPinned, effortLevel } = mapPlannerPriority(cellAt(row, idxPriority));
    const assigneeRaw = cellAt(row, idxAssignedTo);
    const whenDate = parseExternalLocalDate(cellAt(row, idxStartDate));
    const deadline = parseExternalLocalDate(cellAt(row, idxDueDate));
    const notes = idxDescription >= 0 && idxDescription < row.length ? row[idxDescription].trim() : '';
    const checklistItems = parsePlannerChecklists(
      cellAt(row, idxChecklist),
      cellAt(row, idxCompletedChecklist)
    );
    const tagNames = splitTagNames(cellAt(row, idxLabels));

    const task: ExternalImportedTask = {
      title,
      notes,
      status: isCompleted ? 'COMPLETED' : 'OPEN',
      completedAt,
      whenDate,
      deadline,
      isPinned,
      effortLevel,
      tagNames,
      checklistItems,
      assigneeId: assigneeRaw || null
    };

    if (!bucketName) {
      ungroupedTasks.push(task);
    } else {
      const key = bucketName.toLowerCase();
      let phase = phaseByKey.get(key);
      if (!phase) {
        phase = {
          title: bucketName,
          startDate: null,
          endDate: null,
          tasks: []
        };
        phaseByKey.set(key, phase);
        phases.push(phase);
      }
      phase.tasks.push(task);
    }
  }

  const totalTaskCount =
    ungroupedTasks.length + phases.reduce((sum, p) => sum + p.tasks.length, 0);

  return {
    title: preambleTitle || defaultTitle,
    notes: defaultNotes,
    ungroupedTasks,
    phases,
    tagNames: [],
    totalTaskCount
  };
}

function buildPlannerTabularExportRows(input: ExternalExportProjectInput): string[][] {
  const { orderedGroups, checklistByTaskId, tagById } = prepareOrderedProjectContent(input);
  const todayEpoch = startOfDayEpoch();
  const rows: string[][] = [[...PLANNER_COLUMNS]];

  for (const group of orderedGroups) {
    const bucketName = group.heading?.title ?? '';
    for (const tk of group.tasks) {
      const checklists = checklistByTaskId.get(tk.id) ?? [];
      const allChecklistStr = checklists
        .map((c) => c.title.replace(/\\/g, '\\\\').replace(/;/g, '\\;'))
        .join('; ');
      const completedChecklistStr = checklists
        .filter((c) => c.isCompleted)
        .map((c) => c.title.replace(/\\/g, '\\\\').replace(/;/g, '\\;'))
        .join('; ');

      const labelsStr = tk.tagIds
        .map((id) => tagById.get(id)?.title)
        .filter((v): v is string => Boolean(v))
        .join('; ');

      const isLate =
        tk.status === 'OPEN' && tk.deadline != null && tk.deadline < todayEpoch;

      rows.push([
        tk.id,
        tk.title,
        bucketName,
        tk.status === 'COMPLETED' ? 'Completed' : 'Not started',
        formatPlannerPriority(tk),
        tk.assigneeId ?? '',
        '',
        toIsoDateInputValue(tk.createdAt),
        toIsoDateInputValue(tk.whenDate),
        toIsoDateInputValue(tk.deadline),
        isLate ? 'true' : 'false',
        tk.status === 'COMPLETED'
          ? toIsoDateInputValue(tk.completedAt ?? tk.updatedAt)
          : '',
        tk.notes,
        completedChecklistStr,
        allChecklistStr,
        labelsStr
      ]);
    }
  }

  return rows;
}

export function importPlannerCsv(
  rawCsv: string,
  fallbackTitle?: string
): ExternalImportedProject {
  const cleaned = rawCsv.replace(/^\uFEFF/, '');
  const delimiter = detectHeaderDelimiter(cleaned);
  const rows = parseCsvRows(cleaned, delimiter);
  return importPlannerTabularRows(rows, fallbackTitle);
}

export function exportProjectToPlannerCsv(input: ExternalExportProjectInput): string {
  const rows = buildPlannerTabularExportRows(input);
  const lines = rows.map((r) => r.map(escapeCsvCell).join(','));
  return `\uFEFF${lines.join('\r\n')}\r\n`;
}

// ==================== 4. Zero-Dependency ZIP & OOXML (.xlsx) Codec ====================

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

export function computeCrc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC32_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

async function runByteTransformStream(
  input: Uint8Array,
  stream: CompressionStream | DecompressionStream
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  const writeDone = writer
    .write(new Uint8Array(input))
    .then(() => writer.close());

  const reader = stream.readable.getReader();
  const chunks: Uint8Array[] = [];
  let totalLength = 0;

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    if (value) {
      chunks.push(value);
      totalLength += value.byteLength;
    }
  }
  await writeDone;

  const out = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return out;
}

async function deflateRawBytes(input: Uint8Array): Promise<Uint8Array> {
  if (input.byteLength === 0) return new Uint8Array(0);
  return runByteTransformStream(input, new CompressionStream('deflate-raw'));
}

async function inflateRawBytes(input: Uint8Array): Promise<Uint8Array> {
  if (input.byteLength === 0) return new Uint8Array(0);
  return runByteTransformStream(input, new DecompressionStream('deflate-raw'));
}

export async function readZipArchive(bytes: Uint8Array): Promise<Map<string, Uint8Array>> {
  if (bytes.byteLength < 22) {
    throw new Error('Invalid ZIP archive: file too small');
  }

  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

  // Locate End of Central Directory (0x06054b50), scanning backwards up to 65 KB comment
  const minEocdOffset = Math.max(0, bytes.byteLength - 65_557);
  let eocdOffset = -1;
  for (let pos = bytes.byteLength - 22; pos >= minEocdOffset; pos--) {
    if (view.getUint32(pos, true) === 0x06054b50) {
      eocdOffset = pos;
      break;
    }
  }
  if (eocdOffset < 0) {
    throw new Error('Invalid ZIP archive: End of Central Directory not found');
  }

  const totalEntries = view.getUint16(eocdOffset + 10, true);
  const centralDirSize = view.getUint32(eocdOffset + 12, true);
  const centralDirOffset = view.getUint32(eocdOffset + 16, true);
  const centralDirEnd = Math.min(bytes.byteLength, centralDirOffset + centralDirSize);

  const files = new Map<string, Uint8Array>();
  let ptr = centralDirOffset;

  for (let i = 0; i < totalEntries && ptr + 46 <= centralDirEnd; i++) {
    const cdSig = view.getUint32(ptr, true);
    if (cdSig !== 0x02014b50) {
      throw new Error('Invalid ZIP Central Directory entry signature');
    }

    const compressionMethod = view.getUint16(ptr + 10, true);
    const compressedSize = view.getUint32(ptr + 20, true);
    const fileNameLen = view.getUint16(ptr + 28, true);
    const extraFieldLen = view.getUint16(ptr + 30, true);
    const fileCommentLen = view.getUint16(ptr + 32, true);
    const localHeaderOffset = view.getUint32(ptr + 42, true);

    const rawName = utf8Decoder.decode(
      bytes.subarray(ptr + 46, ptr + 46 + fileNameLen)
    );
    const normalizedName = rawName.replace(/\\/g, '/').replace(/^\/+/, '');
    ptr += 46 + fileNameLen + extraFieldLen + fileCommentLen;

    if (normalizedName.endsWith('/')) continue;

    if (localHeaderOffset + 30 > bytes.byteLength) {
      throw new Error(`Invalid local header offset for ${normalizedName}`);
    }
    const lfhSig = view.getUint32(localHeaderOffset, true);
    if (lfhSig !== 0x04034b50) {
      throw new Error(`Invalid local file header signature for ${normalizedName}`);
    }

    const localNameLen = view.getUint16(localHeaderOffset + 26, true);
    const localExtraLen = view.getUint16(localHeaderOffset + 28, true);
    const dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen;
    const dataEnd = dataStart + compressedSize;
    if (dataEnd > bytes.byteLength) {
      throw new Error(`Truncated ZIP entry payload for ${normalizedName}`);
    }

    const compressedSlice = bytes.subarray(dataStart, dataEnd);
    if (compressionMethod === 0) {
      files.set(normalizedName, compressedSlice.slice());
    } else if (compressionMethod === 8) {
      const inflated = await inflateRawBytes(compressedSlice);
      files.set(normalizedName, inflated);
    } else {
      throw new Error(
        `Unsupported ZIP compression method ${compressionMethod} for ${normalizedName}`
      );
    }
  }

  return files;
}

export async function writeZipArchive(
  entries: readonly ZipEntryWriteSpec[]
): Promise<Uint8Array> {
  const localChunks: Uint8Array[] = [];
  const centralChunks: Uint8Array[] = [];
  let localOffset = 0;

  for (const entry of entries) {
    const nameBytes = utf8Encoder.encode(entry.name);
    const uncompressedSize = entry.data.byteLength;
    const crc = computeCrc32(entry.data);
    const method: 0 | 8 = entry.method ?? 8;
    const compressedData =
      method === 8 && uncompressedSize > 0
        ? await deflateRawBytes(entry.data)
        : entry.data;
    const compressedSize = compressedData.byteLength;
    const useDataDesc = Boolean(entry.useDataDescriptor);

    // UTF-8 filename flag (0x0800), plus optional bit 3 (0x0008) data descriptor
    const flags = 0x0800 | (useDataDesc ? 0x0008 : 0);
    const dosTime = 0x0000;
    const dosDate = 0x0021; // 1980-01-01 valid MS-DOS date

    const localHeader = new Uint8Array(30 + nameBytes.byteLength);
    const lv = new DataView(localHeader.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(6, flags, true);
    lv.setUint16(8, method, true);
    lv.setUint16(10, dosTime, true);
    lv.setUint16(12, dosDate, true);
    lv.setUint32(14, useDataDesc ? 0 : crc, true);
    lv.setUint32(18, useDataDesc ? 0 : compressedSize, true);
    lv.setUint32(22, useDataDesc ? 0 : uncompressedSize, true);
    lv.setUint16(26, nameBytes.byteLength, true);
    lv.setUint16(28, 0, true);
    localHeader.set(nameBytes, 30);

    const entryLocalOffset = localOffset;
    localChunks.push(localHeader, compressedData);
    localOffset += localHeader.byteLength + compressedSize;

    if (useDataDesc) {
      const dd = new Uint8Array(16);
      const ddv = new DataView(dd.buffer);
      ddv.setUint32(0, 0x08074b50, true);
      ddv.setUint32(4, crc, true);
      ddv.setUint32(8, compressedSize, true);
      ddv.setUint32(12, uncompressedSize, true);
      localChunks.push(dd);
      localOffset += dd.byteLength;
    }

    const centralHeader = new Uint8Array(46 + nameBytes.byteLength);
    const cv = new DataView(centralHeader.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, flags, true);
    cv.setUint16(10, method, true);
    cv.setUint16(12, dosTime, true);
    cv.setUint16(14, dosDate, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, compressedSize, true);
    cv.setUint32(24, uncompressedSize, true);
    cv.setUint16(28, nameBytes.byteLength, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, entryLocalOffset, true);
    centralHeader.set(nameBytes, 46);
    centralChunks.push(centralHeader);
  }

  let centralDirSize = 0;
  for (const c of centralChunks) centralDirSize += c.byteLength;

  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(4, 0, true);
  ev.setUint16(6, 0, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, centralDirSize, true);
  ev.setUint32(16, localOffset, true);
  ev.setUint16(20, 0, true);

  const totalSize = localOffset + centralDirSize + eocd.byteLength;
  const output = new Uint8Array(totalSize);
  let pos = 0;
  for (const chunk of localChunks) {
    output.set(chunk, pos);
    pos += chunk.byteLength;
  }
  for (const chunk of centralChunks) {
    output.set(chunk, pos);
    pos += chunk.byteLength;
  }
  output.set(eocd, pos);
  return output;
}

function escapeXml(raw: string): string {
  // Strip invalid XML 1.0 control chars while preserving \t, \n, \r
  let cleaned = '';
  for (let i = 0; i < raw.length; i++) {
    const code = raw.charCodeAt(i);
    if (
      code === 0x09 ||
      code === 0x0a ||
      code === 0x0d ||
      (code >= 0x20 && code <= 0xd7ff) ||
      (code >= 0xe000 && code <= 0xfffd) ||
      code >= 0x10000
    ) {
      cleaned += raw[i];
    }
  }
  return cleaned
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function unescapeXml(raw: string): string {
  return raw
    .replace(/_x([0-9A-Fa-f]{4})_/g, (_, hex: string) =>
      String.fromCharCode(parseInt(hex, 16))
    )
    .replace(/&#x([0-9A-Fa-f]+);/g, (_, hex: string) =>
      String.fromCodePoint(parseInt(hex, 16))
    )
    .replace(/&#(\d+);/g, (_, dec: string) =>
      String.fromCodePoint(parseInt(dec, 10))
    )
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

export function columnIndexToLetters(colIdx: number): string {
  let n = colIdx + 1;
  let letters = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    letters = String.fromCharCode(65 + rem) + letters;
    n = Math.floor((n - 1) / 26);
  }
  return letters;
}

export function parseCellRefColumnIndex(ref: string): number | null {
  const match = ref.trim().match(/^([A-Za-z]+)\d+$/);
  if (!match) return null;
  const letters = match[1].toUpperCase();
  let col = 0;
  for (let i = 0; i < letters.length; i++) {
    col = col * 26 + (letters.charCodeAt(i) - 64);
  }
  return col - 1;
}

function extractXmlAttr(tagAttrs: string, attrName: string): string | null {
  const re = new RegExp(`\\b${attrName}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`);
  const m = tagAttrs.match(re);
  if (!m) return null;
  return unescapeXml(m[1] ?? m[2] ?? '');
}

function extractTextRunsFromXmlFragment(xmlFragment: string): string {
  // Remove phonetic runs <rPh>...</rPh> if present
  const withoutPhonetic = xmlFragment.replace(/<(?:\w+:)?rPh\b[\s\S]*?<\/(?:\w+:)?rPh>/g, '');
  let out = '';
  const tRegex = /<(?:\w+:)?t(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?t>/g;
  let m: RegExpExecArray | null;
  while ((m = tRegex.exec(withoutPhonetic)) !== null) {
    out += unescapeXml(m[1]);
  }
  return out;
}

function parseSharedStringsXml(xml: string): string[] {
  const strings: string[] = [];
  const siRegex = /<(?:\w+:)?si\b[^>]*>([\s\S]*?)<\/(?:\w+:)?si>/g;
  let m: RegExpExecArray | null;
  while ((m = siRegex.exec(xml)) !== null) {
    strings.push(extractTextRunsFromXmlFragment(m[1]));
  }
  return strings;
}

function parseWorksheetXml(xml: string, sharedStrings: readonly string[]): string[][] {
  const rows: string[][] = [];
  const rowRegex = /<(?:\w+:)?row\b[^>]*>([\s\S]*?)<\/(?:\w+:)?row>/g;
  let rowMatch: RegExpExecArray | null;

  while ((rowMatch = rowRegex.exec(xml)) !== null) {
    const rowInner = rowMatch[1];
    const rowCells: string[] = [];
    let nextCol = 0;

    const cellRegex = /<(?:\w+:)?c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/(?:\w+:)?c>)/g;
    let cellMatch: RegExpExecArray | null;

    while ((cellMatch = cellRegex.exec(rowInner)) !== null) {
      const attrs = cellMatch[1] ?? '';
      const inner = cellMatch[2] ?? '';
      const refAttr = extractXmlAttr(attrs, 'r');
      const colIdx = (refAttr ? parseCellRefColumnIndex(refAttr) : null) ?? nextCol;
      nextCol = colIdx + 1;

      while (rowCells.length <= colIdx) {
        rowCells.push('');
      }

      if (!inner) {
        rowCells[colIdx] = '';
        continue;
      }

      const cellType = extractXmlAttr(attrs, 't') ?? '';
      const vMatch = inner.match(/<(?:\w+:)?v(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?v>/);
      const rawVal = vMatch ? unescapeXml(vMatch[1].trim()) : '';

      let resolved = '';
      if (cellType === 's') {
        const idx = Number(rawVal);
        resolved = Number.isInteger(idx) && idx >= 0 ? (sharedStrings[idx] ?? '') : '';
      } else if (cellType === 'inlineStr') {
        const isMatch = inner.match(/<(?:\w+:)?is\b[^>]*>([\s\S]*?)<\/(?:\w+:)?is>/);
        resolved = extractTextRunsFromXmlFragment(isMatch ? isMatch[1] : inner);
      } else if (cellType === 'b') {
        resolved = rawVal === '1' || rawVal.toLowerCase() === 'true' ? 'true' : 'false';
      } else if (cellType === 'str' || cellType === 'e') {
        resolved = rawVal;
      } else {
        resolved = rawVal || extractTextRunsFromXmlFragment(inner);
      }

      rowCells[colIdx] = resolved;
    }

    if (rowCells.some((c) => c.trim().length > 0)) {
      rows.push(rowCells);
    }
  }

  return rows;
}

const GENERIC_SHEET_NAMES = new Set([
  'sheet1',
  'tasks',
  'worksheet',
  'tabelle1',
  'feuil1',
  'foglio1',
  'hoja1'
]);

function sanitizeExcelSheetName(title: string): string {
  const cleaned = title
    .replace(/[:\\/?*[\]]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^'+|'+$/g, '')
    .slice(0, 31)
    .trim();
  return cleaned || 'Tasks';
}

export async function importPlannerXlsx(
  bytes: Uint8Array,
  fallbackTitle?: string
): Promise<ExternalImportedProject> {
  const zipEntries = await readZipArchive(bytes);

  const sharedStringsBytes = zipEntries.get('xl/sharedStrings.xml');
  const sharedStrings = sharedStringsBytes
    ? parseSharedStringsXml(utf8Decoder.decode(sharedStringsBytes))
    : [];

  let coreTitle = '';
  let coreNotes = '';
  const coreBytes = zipEntries.get('docProps/core.xml');
  if (coreBytes) {
    const coreXml = utf8Decoder.decode(coreBytes);
    const titleMatch = coreXml.match(/<dc:title\b[^>]*>([\s\S]*?)<\/dc:title>/);
    if (titleMatch) coreTitle = unescapeXml(titleMatch[1]).trim();
    const descMatch = coreXml.match(/<dc:description\b[^>]*>([\s\S]*?)<\/dc:description>/);
    if (descMatch) coreNotes = unescapeXml(descMatch[1]);
  }

  let sheetName = '';
  let firstSheetRelId = '';
  const workbookBytes = zipEntries.get('xl/workbook.xml');
  if (workbookBytes) {
    const workbookXml = utf8Decoder.decode(workbookBytes);
    const sheetMatch = workbookXml.match(/<(?:\w+:)?sheet\b([^>]*?)\/?>/);
    if (sheetMatch) {
      sheetName = (extractXmlAttr(sheetMatch[1], 'name') ?? '').trim();
      firstSheetRelId =
        extractXmlAttr(sheetMatch[1], 'r:id') ??
        extractXmlAttr(sheetMatch[1], 'id') ??
        '';
    }
  }

  let worksheetPath = 'xl/worksheets/sheet1.xml';
  if (firstSheetRelId) {
    const relsBytes = zipEntries.get('xl/_rels/workbook.xml.rels');
    if (relsBytes) {
      const relsXml = utf8Decoder.decode(relsBytes);
      const relRegex = /<Relationship\b([^>]*?)\/?>/g;
      let rm: RegExpExecArray | null;
      while ((rm = relRegex.exec(relsXml)) !== null) {
        const id = extractXmlAttr(rm[1], 'Id');
        const target = extractXmlAttr(rm[1], 'Target');
        if (id === firstSheetRelId && target) {
          const cleanTarget = target.replace(/^\/+/, '');
          worksheetPath = cleanTarget.startsWith('xl/')
            ? cleanTarget
            : `xl/${cleanTarget}`;
          break;
        }
      }
    }
  }

  let sheetBytes = zipEntries.get(worksheetPath);
  if (!sheetBytes) {
    sheetBytes = zipEntries.get('xl/worksheets/sheet1.xml');
  }
  if (!sheetBytes) {
    for (const [entryPath, data] of zipEntries.entries()) {
      if (entryPath.startsWith('xl/worksheets/') && entryPath.endsWith('.xml')) {
        sheetBytes = data;
        break;
      }
    }
  }
  if (!sheetBytes) {
    throw new Error('Invalid .xlsx workbook: no worksheet found');
  }

  const rows = parseWorksheetXml(utf8Decoder.decode(sheetBytes), sharedStrings);
  const meaningfulSheetTitle =
    sheetName && !GENERIC_SHEET_NAMES.has(sheetName.toLowerCase()) ? sheetName : '';

  const resolvedTitle =
    coreTitle || meaningfulSheetTitle || fallbackTitle?.trim() || 'Imported Planner Project';

  return importPlannerTabularRows(rows, resolvedTitle, coreNotes);
}

export async function exportProjectToPlannerXlsx(
  input: ExternalExportProjectInput,
  options?: { compress?: boolean; useDataDescriptor?: boolean }
): Promise<Uint8Array> {
  const rows = buildPlannerTabularExportRows(input);
  const sharedStringsList: string[] = [];
  const sharedIndexMap = new Map<string, number>();
  let totalStringRefs = 0;

  const internString = (val: string): number => {
    totalStringRefs++;
    let idx = sharedIndexMap.get(val);
    if (idx === undefined) {
      idx = sharedStringsList.length;
      sharedStringsList.push(val);
      sharedIndexMap.set(val, idx);
    }
    return idx;
  };

  const xmlRows: string[] = [];
  for (let rIdx = 0; rIdx < rows.length; rIdx++) {
    const rowNumber = rIdx + 1;
    const row = rows[rIdx];
    const styleAttr = rIdx === 0 ? ' s="1"' : '';
    const cellsXml: string[] = [];

    for (let cIdx = 0; cIdx < row.length; cIdx++) {
      const val = row[cIdx];
      if (!val) continue;
      const cellRef = `${columnIndexToLetters(cIdx)}${rowNumber}`;
      const ssIdx = internString(val);
      cellsXml.push(`<c r="${cellRef}" t="s"${styleAttr}><v>${ssIdx}</v></c>`);
    }

    xmlRows.push(`<row r="${rowNumber}">${cellsXml.join('')}</row>`);
  }

  const sheetXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
    `<sheetData>${xmlRows.join('')}</sheetData>` +
    `</worksheet>`;

  const siItemsXml = sharedStringsList
    .map((str) => {
      const needsPreserve = /^\s|\s$|[\r\n]/.test(str);
      const preserveAttr = needsPreserve ? ' xml:space="preserve"' : '';
      return `<si><t${preserveAttr}>${escapeXml(str)}</t></si>`;
    })
    .join('');

  const sharedStringsXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${totalStringRefs}" uniqueCount="${sharedStringsList.length}">` +
    `${siItemsXml}` +
    `</sst>`;

  const safeSheetName = sanitizeExcelSheetName(input.project.title);
  const workbookXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
    `<sheets><sheet name="${escapeXml(safeSheetName)}" sheetId="1" r:id="rId1"/></sheets>` +
    `</workbook>`;

  const workbookRelsXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>` +
    `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>` +
    `<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>` +
    `</Relationships>`;

  const stylesXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
    `<fonts count="2">` +
    `<font><sz val="11"/><name val="Calibri"/></font>` +
    `<font><b/><sz val="11"/><name val="Calibri"/></font>` +
    `</fonts>` +
    `<fills count="2">` +
    `<fill><patternFill patternType="none"/></fill>` +
    `<fill><patternFill patternType="gray125"/></fill>` +
    `</fills>` +
    `<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>` +
    `<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>` +
    `<cellXfs count="2">` +
    `<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>` +
    `<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>` +
    `</cellXfs>` +
    `</styleSheet>`;

  const rootRelsXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>` +
    `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>` +
    `</Relationships>`;

  const coreXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">` +
    `<dc:title>${escapeXml(input.project.title)}</dc:title>` +
    `<dc:description>${escapeXml(input.project.notes)}</dc:description>` +
    `<dc:creator>Thingsbook</dc:creator>` +
    `</cp:coreProperties>`;

  const contentTypesXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
    `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
    `<Default Extension="xml" ContentType="application/xml"/>` +
    `<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>` +
    `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>` +
    `<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>` +
    `<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>` +
    `<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>` +
    `</Types>`;

  const method: 0 | 8 = options?.compress === false ? 0 : 8;
  const useDataDescriptor = Boolean(options?.useDataDescriptor);

  return writeZipArchive([
    {
      name: '[Content_Types].xml',
      data: utf8Encoder.encode(contentTypesXml),
      method,
      useDataDescriptor
    },
    {
      name: '_rels/.rels',
      data: utf8Encoder.encode(rootRelsXml),
      method,
      useDataDescriptor
    },
    {
      name: 'docProps/core.xml',
      data: utf8Encoder.encode(coreXml),
      method,
      useDataDescriptor
    },
    {
      name: 'xl/workbook.xml',
      data: utf8Encoder.encode(workbookXml),
      method,
      useDataDescriptor
    },
    {
      name: 'xl/_rels/workbook.xml.rels',
      data: utf8Encoder.encode(workbookRelsXml),
      method,
      useDataDescriptor
    },
    {
      name: 'xl/styles.xml',
      data: utf8Encoder.encode(stylesXml),
      method,
      useDataDescriptor
    },
    {
      name: 'xl/sharedStrings.xml',
      data: utf8Encoder.encode(sharedStringsXml),
      method,
      useDataDescriptor
    },
    {
      name: 'xl/worksheets/sheet1.xml',
      data: utf8Encoder.encode(sheetXml),
      method,
      useDataDescriptor
    }
  ]);
}

// ==================== 5. Auto-Detection & Unified Export ====================

export async function importExternalProjectAuto(
  bytes: Uint8Array,
  fileNameHint?: string,
  fallbackTitle?: string
): Promise<ExternalImportedProject> {
  const effectiveFallback =
    fallbackTitle?.trim() || stripExtensionFromFileName(fileNameHint) || undefined;

  // PK\x03\x04 ZIP magic bytes (.xlsx)
  if (
    bytes.byteLength >= 4 &&
    bytes[0] === 0x50 &&
    bytes[1] === 0x4b &&
    bytes[2] === 0x03 &&
    bytes[3] === 0x04
  ) {
    return importPlannerXlsx(bytes, effectiveFallback);
  }

  const text = utf8Decoder.decode(bytes).replace(/^\uFEFF/, '');
  const trimmed = text.trimStart();

  if (
    trimmed.startsWith('{') ||
    trimmed.startsWith('[') ||
    fileNameHint?.toLowerCase().endsWith('.json')
  ) {
    return importAsanaJson(text, effectiveFallback);
  }

  // Distinguish Microsoft Planner CSV vs Asana CSV via header columns
  const delimiter = detectHeaderDelimiter(text);
  const sampleRows = parseCsvRows(text.slice(0, 8192), delimiter);
  const firstRowHeaders = (sampleRows[0] ?? []).map(normalizeHeaderKey);
  const isPlanner =
    findColumnIndex(firstRowHeaders, [
      'bucket name',
      'bucketname',
      'bucket',
      'checklist items',
      'completed checklist items',
      'progress',
      'labels',
      'assigned to',
      'aufgabenname',
      'checklistenelemente'
    ]) >= 0 &&
    findColumnIndex(firstRowHeaders, ['section/column', 'parent task']) < 0;

  if (isPlanner) {
    return importPlannerCsv(text, effectiveFallback);
  }
  return importAsanaCsv(text, effectiveFallback);
}

export async function exportExternalProjectBytes(
  input: ExternalExportProjectInput,
  format: ExternalExportFormat
): Promise<{
  filename: string;
  mimeType: string;
  bytes: Uint8Array;
  projectTitle: string;
}> {
  const slug = sanitizeFileSlug(input.project.title);
  const projectTitle = input.project.title;

  switch (format) {
    case 'asana-csv': {
      const csv = exportProjectToAsanaCsv(input);
      return {
        filename: `${slug}-asana.csv`,
        mimeType: 'text/csv;charset=utf-8',
        bytes: utf8Encoder.encode(csv),
        projectTitle
      };
    }
    case 'asana-json': {
      const json = exportProjectToAsanaJson(input);
      return {
        filename: `${slug}-asana.json`,
        mimeType: 'application/json;charset=utf-8',
        bytes: utf8Encoder.encode(json),
        projectTitle
      };
    }
    case 'planner-xlsx': {
      const bytes = await exportProjectToPlannerXlsx(input);
      return {
        filename: `${slug}-planner.xlsx`,
        mimeType:
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        bytes,
        projectTitle
      };
    }
    case 'planner-csv': {
      const csv = exportProjectToPlannerCsv(input);
      return {
        filename: `${slug}-planner.csv`,
        mimeType: 'text/csv;charset=utf-8',
        bytes: utf8Encoder.encode(csv),
        projectTitle
      };
    }
  }
}
