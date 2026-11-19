import {
  endOfDayEpoch,
  startOfDayEpoch,
  type AreaRecord,
  type ChecklistItemRecord,
  type ProjectRecord,
  type ScheduleState,
  type TagRecord,
  type TaskRecord,
  type TaskStatus
} from '../domain/types.ts';
import { normalizeScheduleState, normalizeTaskStatus } from './backupCodec.ts';

export function escapeCsvCell(value: string): string {
  return /[",;\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

export function escapeCsvSemicolonItem(item: string): string {
  return item.replace(/\\/g, '\\\\').replace(/;/g, '\\;');
}

export function escapeCsvTagItem(item: string): string {
  return item
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\|/g, '\\|');
}

export function splitEscapedCsvList(
  raw: string,
  separators: readonly string[] = [';']
): string[] {
  const sepSet = new Set(separators);
  const out: string[] = [];
  let buf = '';

  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === '\\' && i + 1 < raw.length) {
      const next = raw[i + 1];
      if (next === '\\' || sepSet.has(next)) {
        buf += next;
        i++;
        continue;
      }
    }
    if (sepSet.has(ch)) {
      const trimmed = buf.trim();
      if (trimmed.length > 0) out.push(trimmed);
      buf = '';
    } else {
      buf += ch;
    }
  }

  const last = buf.trim();
  if (last.length > 0) out.push(last);
  return out;
}

export function detectCsvDelimiter(rawCsv: string): ',' | ';' {
  const cleaned = rawCsv.replace(/^\uFEFF/, '');
  let inQuotes = false;
  let rowCommas = 0;
  let rowSemis = 0;
  let rowHasContent = false;
  let totalCommas = 0;
  let totalSemis = 0;
  let rowsWithCommas = 0;
  let rowsWithSemis = 0;
  let sampledRows = 0;

  const flushRow = () => {
    if (!rowHasContent && rowCommas === 0 && rowSemis === 0) return;
    totalCommas += rowCommas;
    totalSemis += rowSemis;
    if (rowCommas > 0) rowsWithCommas++;
    if (rowSemis > 0) rowsWithSemis++;
    sampledRows++;
    rowCommas = 0;
    rowSemis = 0;
    rowHasContent = false;
  };

  for (let i = 0; i < cleaned.length; i++) {
    const ch = cleaned[i];
    if (inQuotes) {
      rowHasContent = true;
      if (ch === '"') {
        if (cleaned[i + 1] === '"') {
          i++;
        } else {
          inQuotes = false;
        }
      }
    } else if (ch === '"') {
      inQuotes = true;
      rowHasContent = true;
    } else if (ch === ',') {
      rowCommas++;
    } else if (ch === ';') {
      rowSemis++;
    } else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && cleaned[i + 1] === '\n') i++;
      flushRow();
      if (sampledRows >= 25) break;
    } else if (ch !== ' ' && ch !== '\t') {
      rowHasContent = true;
    }
  }

  if (sampledRows < 25) {
    flushRow();
  }

  if (rowsWithSemis > rowsWithCommas) return ';';
  if (rowsWithCommas > rowsWithSemis) return ',';
  return totalSemis > totalCommas ? ';' : ',';
}

export function parseCsvRows(csv: string, delimiter: string): string[][] {
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

function buildValidMidnightEpoch(year: number, month1Based: number, day: number): number | null {
  if (month1Based < 1 || month1Based > 12 || day < 1 || day > 31) return null;
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

export function parseFlexibleDate(raw: string): number | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;

  const iso = trimmed.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (iso) {
    return buildValidMidnightEpoch(Number(iso[1]), Number(iso[2]), Number(iso[3]));
  }

  const dotOrSlash = trimmed.match(/^(\d{1,2})[./](\d{1,2})[./](\d{2,4})$/);
  if (dotOrSlash) {
    const p1 = Number(dotOrSlash[1]);
    const p2 = Number(dotOrSlash[2]);
    let y = Number(dotOrSlash[3]);
    if (y < 100) y += 2000;

    // Default DD.MM.YYYY / DD/MM/YYYY, with MM/DD/YYYY fallback when second part > 12
    if (p2 > 12 && p1 >= 1 && p1 <= 12) {
      return buildValidMidnightEpoch(y, p1, p2);
    }
    return buildValidMidnightEpoch(y, p2, p1);
  }

  const num = Number(trimmed);
  if (Number.isFinite(num) && num > 86_400_000) {
    return startOfDayEpoch(new Date(num));
  }
  return null;
}

const CSV_HEADER_ALIASES = {
  title: [
    'title',
    'titel',
    'aufgabe',
    'task',
    'name',
    'todo',
    'to-do',
    'titre',
    'tâche',
    'tache',
    'nom',
    'titolo',
    'attività',
    'attivita',
    'nome',
    'título',
    'titulo',
    'tarea',
    'nombre'
  ],
  notes: [
    'notes',
    'notizen',
    'beschreibung',
    'description',
    'note',
    'remarques',
    'commentaires',
    'descrizione',
    'appunti',
    'notas',
    'descripción',
    'descripcion',
    'comentarios'
  ],
  status: ['status', 'statut', 'état', 'etat', 'stato', 'estado'],
  scheduleState: [
    'schedulestate',
    'schedule',
    'schedule state',
    'when',
    'ansicht',
    'view',
    'planung',
    'quand',
    'vue',
    'planification',
    'quando',
    'vista',
    'pianificazione',
    'stato pianificazione',
    'cuándo',
    'cuando',
    'programación',
    'programacion'
  ],
  whenDate: [
    'whendate',
    'when date',
    'startdatum',
    'date',
    'datum',
    'startdate',
    'start date',
    'date de début',
    'date de debut',
    'date début',
    'date debut',
    'data',
    'data inizio',
    'data di inizio',
    'fecha',
    'fecha de inicio',
    'fecha inicio'
  ],
  deadline: [
    'deadline',
    'fälligkeit',
    'faelligkeit',
    'due',
    'duedate',
    'due date',
    'zieldatum',
    'échéance',
    'echeance',
    'date limite',
    'scadenza',
    'fecha límite',
    'fecha limite',
    'vencimiento'
  ],
  area: ['area', 'bereich', 'domaine', 'zone', 'área'],
  project: ['project', 'projekt', 'projet', 'progetto', 'proyecto'],
  tags: [
    'tags',
    'tag',
    'categories',
    'category',
    'kategorien',
    'kategorie',
    'catégories',
    'catégorie',
    'categorie',
    'categoria',
    'categorías',
    'categoría',
    'schlagwörter',
    'schlagwoerter',
    'étiquettes',
    'etiquettes',
    'étiquette',
    'etiquette',
    'etichette',
    'etichetta',
    'etiquetas',
    'etiqueta'
  ],
  checklist: [
    'checklist',
    'subtasks',
    'unteraufgaben',
    'checkliste',
    'liste',
    'liste de contrôle',
    'liste de controle',
    'sous-tâches',
    'sous-taches',
    'elenco',
    'sottoattività',
    'sottoattivita',
    'lista',
    'lista de verificación',
    'lista de verificacion',
    'lista de control',
    'subtareas'
  ]
} as const;

export interface ParsedCsvTaskRow {
  title: string;
  notes: string;
  status: TaskStatus;
  scheduleState: ScheduleState;
  whenDate: number | null;
  deadline: number | null;
  areaName: string;
  projectName: string;
  tagNames: string[];
  checklistItems: Array<{ title: string; isCompleted: boolean }>;
}

export function serializeEntitiesToCsv(params: {
  tasks: readonly TaskRecord[];
  areas: readonly AreaRecord[];
  projects: readonly ProjectRecord[];
  tags: readonly TagRecord[];
  checklistItems: readonly ChecklistItemRecord[];
}): string {
  const areasById = new Map(
    params.areas.filter((a) => !a.isDeleted).map((a) => [a.id, a])
  );
  const projectsById = new Map(
    params.projects.filter((p) => !p.isDeleted).map((p) => [p.id, p])
  );
  const tagsById = new Map(
    params.tags.filter((tg) => !tg.isDeleted).map((tg) => [tg.id, tg])
  );

  const fmtDate = (ms: number | null) => {
    if (ms == null) return '';
    const d = new Date(ms);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  const lines = [
    'Title,Notes,Status,ScheduleState,WhenDate,Deadline,Area,Project,Tags,Checklist'
  ];

  for (const t of params.tasks.filter((x) => !x.isDeleted)) {
    const proj = t.projectId ? projectsById.get(t.projectId) : undefined;
    const areaId = proj?.areaId ?? t.areaId;
    const area = areaId ? areasById.get(areaId) : undefined;
    const tagStr = t.tagIds
      .map((id) => tagsById.get(id)?.title)
      .filter((title): title is string => Boolean(title))
      .map((title) => escapeCsvTagItem(title))
      .join('; ');
    const chkStr = params.checklistItems
      .filter((c) => c.taskId === t.id && !c.isDeleted)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(
        (c) =>
          `${c.isCompleted ? '[x] ' : '[ ] '}${escapeCsvSemicolonItem(c.title)}`
      )
      .join('; ');

    lines.push(
      [
        escapeCsvCell(t.title),
        escapeCsvCell(t.notes),
        escapeCsvCell(t.status),
        escapeCsvCell(t.scheduleState),
        escapeCsvCell(fmtDate(t.whenDate)),
        escapeCsvCell(fmtDate(t.deadline)),
        escapeCsvCell(area?.title || ''),
        escapeCsvCell(proj?.title || ''),
        escapeCsvCell(tagStr),
        escapeCsvCell(chkStr)
      ].join(',')
    );
  }

  return lines.join('\n');
}

export function parseCsvTaskImport(
  rawCsv: string,
  nowMillis: number = Date.now()
): ParsedCsvTaskRow[] {
  const cleaned = rawCsv.replace(/^\uFEFF/, '').trim();
  if (!cleaned) return [];

  const delimiter = detectCsvDelimiter(cleaned);
  const rows = parseCsvRows(cleaned, delimiter);
  if (rows.length === 0) return [];

  const header = rows[0].map((c) => c.trim().toLowerCase());
  const allKnownAliases = new Set<string>(
    Object.values(CSV_HEADER_ALIASES).flat()
  );
  const matchingKnownHeaders = header.filter((h) => allKnownAliases.has(h)).length;
  const hasHeader =
    header.some((h) => (CSV_HEADER_ALIASES.title as readonly string[]).includes(h)) ||
    matchingKnownHeaders >= 2;

  const dataRows = (hasHeader ? rows.slice(1) : rows).slice(0, 10000);
  if (dataRows.length === 0) return [];

  const colIdx = (names: readonly string[], fallback: number) => {
    if (!hasHeader) return fallback;
    return header.findIndex((h) => names.includes(h));
  };

  const rawTitleIdx = colIdx(CSV_HEADER_ALIASES.title, 0);
  const idxTitle = rawTitleIdx >= 0 ? rawTitleIdx : 0;
  const idxNotes = colIdx(CSV_HEADER_ALIASES.notes, 1);
  const idxStatus = colIdx(CSV_HEADER_ALIASES.status, 2);
  const idxSchedule = colIdx(CSV_HEADER_ALIASES.scheduleState, 3);
  const idxWhen = colIdx(CSV_HEADER_ALIASES.whenDate, 4);
  const idxDeadline = colIdx(CSV_HEADER_ALIASES.deadline, 5);
  const idxArea = colIdx(CSV_HEADER_ALIASES.area, 6);
  const idxProject = colIdx(CSV_HEADER_ALIASES.project, 7);
  const idxTags = colIdx(CSV_HEADER_ALIASES.tags, 8);
  const idxChecklist = colIdx(CSV_HEADER_ALIASES.checklist, 9);

  const endToday = endOfDayEpoch(new Date(nowMillis));
  const parsedRows: ParsedCsvTaskRow[] = [];

  for (const cols of dataRows) {
    const get = (i: number) => (i >= 0 && cols[i] ? cols[i].trim() : '');
    const title = get(idxTitle);
    if (!title) continue;

    const status = normalizeTaskStatus(get(idxStatus));
    const whenDate = parseFlexibleDate(get(idxWhen));
    const deadline = parseFlexibleDate(get(idxDeadline));
    const areaName = get(idxArea);
    const projectName = get(idxProject);
    let scheduleState = normalizeScheduleState(
      get(idxSchedule),
      whenDate != null ? (whenDate > endToday ? 'UPCOMING' : 'TODAY') : 'INBOX'
    );

    if (
      scheduleState === 'INBOX' &&
      whenDate == null &&
      (projectName.length > 0 || areaName.length > 0)
    ) {
      scheduleState = 'ANYTIME';
    }

    const rawTags = get(idxTags);
    const tagNames = rawTags ? splitEscapedCsvList(rawTags, [';', ',', '|']) : [];

    const rawChecklist = get(idxChecklist);
    const checklistItems: Array<{ title: string; isCompleted: boolean }> = [];
    if (rawChecklist) {
      const subParts = splitEscapedCsvList(rawChecklist, [';']);
      for (const rawSub of subParts) {
        const isCompleted = /^\[x\]/i.test(rawSub);
        const subTitle = rawSub.replace(/^\[[ xX]?\]\s*/, '').trim() || rawSub;
        checklistItems.push({ title: subTitle, isCompleted });
      }
    }

    parsedRows.push({
      title,
      notes: get(idxNotes),
      status,
      scheduleState,
      whenDate,
      deadline,
      areaName,
      projectName,
      tagNames,
      checklistItems
    });
  }

  return parsedRows;
}
