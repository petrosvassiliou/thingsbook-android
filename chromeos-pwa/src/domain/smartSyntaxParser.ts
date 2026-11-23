import {
  normalizeDiacritics,
  parseNaturalDate,
  type ParsedNaturalDate
} from './naturalDateParser.ts';
import type {
  AreaRecord,
  ProjectRecord,
  ScheduleState,
  SupportedLang,
  TagRecord
} from './types.ts';
import { parseCustomDurationMinutes } from './v2Utils.ts';

export interface SmartSyntaxParserOptions {
  tags: readonly TagRecord[];
  projects: readonly ProjectRecord[];
  areas: readonly AreaRecord[];
  lang?: SupportedLang;
  reference?: Date;
}

export interface ParsedSmartSyntax {
  cleanTitle: string;
  scheduleState: ScheduleState | null;
  whenDate: number | null;
  whenLabel: string | null;
  reminderTime: number | null;
  reminderTimeEpochMillis: number | null;
  deadline: number | null;
  deadlineLabel: string | null;
  durationMinutes: number | null;
  matchedTagIds: string[];
  matchedTags: TagRecord[];
  matchedProject: ProjectRecord | null;
  matchedArea: AreaRecord | null;
  isMilestone: boolean;
  hasAnyMatch: boolean;
}

const MILESTONE_KEYWORDS = new Set([
  'milestone',
  'meilenstein',
  'jalon',
  'traguardo',
  'hito'
]);

function normalizeLookupText(input: string): string {
  return input.trim().replace(/\s+/g, ' ').toLowerCase();
}

function resolveSmartNaturalDate(
  rawToken: string,
  isQuoted: boolean,
  lang: SupportedLang,
  reference: Date
): ParsedNaturalDate | null {
  const trimmed = rawToken.trim();
  if (!trimmed) return null;

  const direct = parseNaturalDate(trimmed, lang, reference);
  if (direct) return direct;

  if (!isQuoted && trimmed.includes('-')) {
    const spaced = trimmed.replace(/-/g, ' ').replace(/\s+/g, ' ').trim();
    if (spaced) {
      return parseNaturalDate(spaced, lang, reference);
    }
  }

  return null;
}

function matchTagToken(
  rawToken: string,
  isQuoted: boolean,
  activeTags: readonly TagRecord[]
): TagRecord | null {
  const trimmed = rawToken.trim();
  if (!trimmed) return null;
  const exactNorm = normalizeLookupText(trimmed);
  const spacedNorm = normalizeLookupText(isQuoted ? trimmed : trimmed.replace(/-/g, ' '));

  for (const tg of activeTags) {
    const titleNorm = normalizeLookupText(tg.title);
    if (titleNorm === exactNorm || titleNorm === spacedNorm) {
      return tg;
    }
  }

  const foldedExact = normalizeDiacritics(exactNorm);
  const foldedSpaced = normalizeDiacritics(spacedNorm);
  for (const tg of activeTags) {
    const foldedTitle = normalizeDiacritics(normalizeLookupText(tg.title));
    if (foldedTitle === foldedExact || foldedTitle === foldedSpaced) {
      return tg;
    }
  }

  return null;
}

function matchEntityByTitleOrPrefix<T extends { title: string }>(
  rawToken: string,
  isQuoted: boolean,
  entities: readonly T[]
): T | null {
  const trimmed = rawToken.trim();
  if (!trimmed) return null;
  const exactNorm = normalizeLookupText(trimmed);
  const spacedNorm = normalizeLookupText(isQuoted ? trimmed : trimmed.replace(/-/g, ' '));

  // 1. Exact case-insensitive title match
  for (const item of entities) {
    if (normalizeLookupText(item.title) === exactNorm) {
      return item;
    }
  }

  // 2. Hyphen-to-space normalized exact title match
  if (spacedNorm !== exactNorm) {
    for (const item of entities) {
      if (normalizeLookupText(item.title) === spacedNorm) {
        return item;
      }
    }
  }

  // 3. Diacritic-insensitive exact match
  const foldedExact = normalizeDiacritics(exactNorm);
  const foldedSpaced = normalizeDiacritics(spacedNorm);
  for (const item of entities) {
    const foldedTitle = normalizeDiacritics(normalizeLookupText(item.title));
    if (foldedTitle === foldedExact || foldedTitle === foldedSpaced) {
      return item;
    }
  }

  // 4. Unambiguous prefix >= 3 characters
  if (spacedNorm.length >= 3) {
    const prefixMatches = entities.filter((item) => {
      const titleNorm = normalizeLookupText(item.title);
      const foldedTitle = normalizeDiacritics(titleNorm);
      return (
        titleNorm.startsWith(exactNorm) ||
        titleNorm.startsWith(spacedNorm) ||
        foldedTitle.startsWith(foldedExact) ||
        foldedTitle.startsWith(foldedSpaced)
      );
    });
    if (prefixMatches.length === 1) {
      return prefixMatches[0];
    }
  }

  return null;
}

/**
 * Parses inline GTD smart-capture tokens (`@when`, `!deadline` / `!milestone` / `◆`,
 * `~duration`, `#tag`, `/project` or `/area`) from a task title.
 *
 * Tokens must be preceded by start-of-string or whitespace so URLs (`https://a.com/b`),
 * ratios (`3/4`), and email addresses (`user@example.com`) are never touched.
 * Only tokens that resolve validly are stripped; if stripping would leave `cleanTitle`
 * empty, `rawTitle.trim()` is preserved.
 */
export function parseSmartTaskSyntax(
  rawTitle: string,
  opts: SmartSyntaxParserOptions
): ParsedSmartSyntax {
  const source = String(rawTitle ?? '');
  const trimmedSource = source.trim();
  const lang: SupportedLang = opts.lang ?? 'en';
  const reference: Date = opts.reference ?? new Date();

  const activeTags = (opts.tags ?? []).filter((tg) => !tg.isDeleted);
  const activeProjects = (opts.projects ?? []).filter(
    (p) => !p.isDeleted && p.status === 'ACTIVE'
  );
  const activeAreas = (opts.areas ?? []).filter((a) => !a.isDeleted);

  let scheduleState: ScheduleState | null = null;
  let whenDate: number | null = null;
  let whenLabel: string | null = null;
  let reminderTime: number | null = null;
  let deadline: number | null = null;
  let deadlineLabel: string | null = null;
  let durationMinutes: number | null = null;
  const matchedTagIds: string[] = [];
  const matchedTags: TagRecord[] = [];
  let matchedProject: ProjectRecord | null = null;
  let matchedArea: AreaRecord | null = null;
  let isMilestone = false;
  let hasAnyMatch = false;

  if (!trimmedSource) {
    return {
      cleanTitle: '',
      scheduleState: null,
      whenDate: null,
      whenLabel: null,
      reminderTime: null,
      reminderTimeEpochMillis: null,
      deadline: null,
      deadlineLabel: null,
      durationMinutes: null,
      matchedTagIds: [],
      matchedTags: [],
      matchedProject: null,
      matchedArea: null,
      isMilestone: false,
      hasAnyMatch: false
    };
  }

  const strippedRanges: Array<[number, number]> = [];
  const len = source.length;
  let i = 0;

  while (i < len) {
    const isAtBoundary = i === 0 || /\s/.test(source[i - 1]);
    if (!isAtBoundary) {
      i++;
      continue;
    }

    const ch = source[i];

    // Standalone ◆ milestone symbol
    if (ch === '◆' && (i + 1 === len || /\s/.test(source[i + 1]))) {
      isMilestone = true;
      hasAnyMatch = true;
      strippedRanges.push([i, i + 1]);
      i += 1;
      continue;
    }

    if (ch !== '@' && ch !== '!' && ch !== '~' && ch !== '#' && ch !== '/') {
      i++;
      continue;
    }

    if (i + 1 >= len || /\s/.test(source[i + 1])) {
      i++;
      continue;
    }

    let rawToken = '';
    let isQuoted = false;
    let tokenEnd = i + 1;

    const nextCh = source[i + 1];
    if (nextCh === '"' || nextCh === '“') {
      const closingChar = nextCh === '“' ? '”' : '"';
      const closeIdx = source.indexOf(closingChar, i + 2);
      if (closeIdx > i + 1 && (closeIdx + 1 === len || /\s/.test(source[closeIdx + 1]))) {
        rawToken = source.slice(i + 2, closeIdx);
        isQuoted = true;
        tokenEnd = closeIdx + 1;
      } else {
        // Unclosed quote: fall back to reading until whitespace
        let end = i + 1;
        while (end < len && !/\s/.test(source[end])) end++;
        i = end;
        continue;
      }
    } else {
      let end = i + 1;
      while (end < len && !/\s/.test(source[end])) end++;
      rawToken = source.slice(i + 1, end);
      isQuoted = false;
      tokenEnd = end;
    }

    // Helper to attempt matching with optional trailing [,;] peel on unquoted tokens
    const candidates: Array<{ token: string; endPos: number }> = [
      { token: rawToken, endPos: tokenEnd }
    ];
    if (!isQuoted && rawToken.length > 1 && /[,;]$/.test(rawToken)) {
      candidates.push({
        token: rawToken.slice(0, -1),
        endPos: tokenEnd - 1
      });
    }

    let consumedEnd: number | null = null;

    for (const cand of candidates) {
      if (!cand.token.trim()) continue;

      if (ch === '@') {
        const parsedWhen = resolveSmartNaturalDate(cand.token, isQuoted, lang, reference);
        if (parsedWhen) {
          scheduleState = parsedWhen.scheduleState;
          whenDate = parsedWhen.whenDateEpochMillis;
          whenLabel = parsedWhen.formattedPreview;
          reminderTime = parsedWhen.reminderTimeEpochMillis;
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
      } else if (ch === '!') {
        const lowerToken = normalizeDiacritics(cand.token.trim().toLowerCase());
        if (MILESTONE_KEYWORDS.has(lowerToken)) {
          isMilestone = true;
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
        const parsedDl = resolveSmartNaturalDate(cand.token, isQuoted, lang, reference);
        if (parsedDl && parsedDl.whenDateEpochMillis != null) {
          deadline = parsedDl.whenDateEpochMillis;
          deadlineLabel = parsedDl.formattedPreview;
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
      } else if (ch === '~') {
        let mins = parseCustomDurationMinutes(cand.token);
        if (mins == null && !isQuoted && cand.token.includes('-')) {
          mins = parseCustomDurationMinutes(cand.token.replace(/-/g, ' '));
        }
        if (mins != null && mins > 0) {
          durationMinutes = mins;
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
      } else if (ch === '#') {
        const tg = matchTagToken(cand.token, isQuoted, activeTags);
        if (tg) {
          if (!matchedTagIds.includes(tg.id)) {
            matchedTagIds.push(tg.id);
            matchedTags.push(tg);
          }
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
      } else if (ch === '/') {
        const proj = matchEntityByTitleOrPrefix(cand.token, isQuoted, activeProjects);
        if (proj) {
          matchedProject = proj;
          matchedArea = null;
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
        const area = matchEntityByTitleOrPrefix(cand.token, isQuoted, activeAreas);
        if (area) {
          if (!matchedProject) {
            matchedArea = area;
          }
          hasAnyMatch = true;
          consumedEnd = cand.endPos;
          break;
        }
      }
    }

    if (consumedEnd != null) {
      strippedRanges.push([i, consumedEnd]);
      i = tokenEnd;
    } else {
      i = tokenEnd;
    }
  }

  let cleanTitle = trimmedSource;
  if (strippedRanges.length > 0) {
    let out = '';
    let cursor = 0;
    for (const [start, end] of strippedRanges) {
      out += source.slice(cursor, start);
      cursor = end;
    }
    out += source.slice(cursor);
    const collapsed = out.replace(/\s+/g, ' ').trim();
    cleanTitle = collapsed.length > 0 ? collapsed : trimmedSource;
  }

  return {
    cleanTitle,
    scheduleState,
    whenDate,
    whenLabel,
    reminderTime,
    reminderTimeEpochMillis: reminderTime,
    deadline,
    deadlineLabel,
    durationMinutes,
    matchedTagIds,
    matchedTags,
    matchedProject,
    matchedArea,
    isMilestone,
    hasAnyMatch
  };
}
