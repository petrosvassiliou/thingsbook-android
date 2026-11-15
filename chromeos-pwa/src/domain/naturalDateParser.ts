import type { ScheduleState, SupportedLang } from './types';

export interface ParsedNaturalDate {
  scheduleState: ScheduleState;
  whenDateEpochMillis: number | null;
  reminderTimeEpochMillis: number | null;
  reminderClock: string | null;
  formattedPreview: string;
  matchedExpression: string;
}

function extractTrailingReminderTime(
  normalizedInput: string
): { datePart: string; hour: number; minute: number; clock: string } | null {
  // Matches trailing time expressions across EN/DE/FR/IT/ES:
  // e.g. "tomorrow at 9:30", "tomorrow 2:15 pm", "morgen um 14:00", "morgen um 9 uhr",
  // "demain a 18h30", "domani alle 08:15", "manana a las 15:00", "today @ 10:00"
  const re =
    /^(.*?)\s+(?:@\s*|at\s+|um\s+|a\s+las\s+|alle\s+|a\s+)?(\d{1,2})(?:[:h](\d{2}))?\s*(am|pm|uhr)?$/;
  const m = normalizedInput.match(re);
  if (!m) return null;

  const datePart = m[1].trim();
  if (!datePart) return null;

  const hasExplicitConnector =
    /\s+(?:@|at|um|a\s+las|alle|a)\s+\d{1,2}(?:[:h]\d{2})?\s*(?:am|pm|uhr)?$/.test(
      normalizedInput
    );
  const hasColonOrH = m[3] != null;
  const hasMeridiemOrUhr = Boolean(m[4]);
  // Avoid misinterpreting "in 3 days" or bare numbers unless time syntax is unambiguous
  if (!hasColonOrH && !hasMeridiemOrUhr && !hasExplicitConnector) {
    return null;
  }

  let hour = Number(m[2]);
  const minute = m[3] != null ? Number(m[3]) : 0;
  const suffix = m[4] || '';

  if (!Number.isFinite(hour) || !Number.isFinite(minute) || minute < 0 || minute > 59) {
    return null;
  }

  if (suffix === 'pm') {
    if (hour < 1 || hour > 12) return null;
    if (hour < 12) hour += 12;
  } else if (suffix === 'am') {
    if (hour < 1 || hour > 12) return null;
    if (hour === 12) hour = 0;
  } else if (hour < 0 || hour > 23) {
    return null;
  }

  const clock = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
  return { datePart, hour, minute, clock };
}

function attachReminderToParsedDate(
  base: Omit<ParsedNaturalDate, 'reminderTimeEpochMillis' | 'reminderClock'>,
  extracted: { hour: number; minute: number; clock: string } | null,
  refMidnight: Date,
  originalInput: string
): ParsedNaturalDate {
  if (!extracted) {
    return {
      ...base,
      reminderTimeEpochMillis: null,
      reminderClock: null,
      matchedExpression: originalInput
    };
  }
  const baseEpoch = base.whenDateEpochMillis ?? refMidnight.getTime();
  const baseDay = new Date(baseEpoch);
  const remEpoch = new Date(
    baseDay.getFullYear(),
    baseDay.getMonth(),
    baseDay.getDate(),
    extracted.hour,
    extracted.minute,
    0,
    0
  ).getTime();

  return {
    ...base,
    whenDateEpochMillis:
      base.whenDateEpochMillis ??
      (base.scheduleState === 'ANYTIME' || base.scheduleState === 'SOMEDAY' ? baseEpoch : null),
    reminderTimeEpochMillis: remEpoch,
    reminderClock: extracted.clock,
    formattedPreview: `${base.formattedPreview} · 🔔 ${extracted.clock}`,
    matchedExpression: originalInput
  };
}

const WEEKDAY_ALIASES: Record<string, number> = {
  // English (ISO 1=Mon..7=Sun)
  monday: 1, mon: 1,
  tuesday: 2, tue: 2,
  wednesday: 3, wed: 3,
  thursday: 4, thu: 4,
  friday: 5, fri: 5,
  saturday: 6, sat: 6,
  sunday: 7, sun: 7,
  // German
  montag: 1,
  dienstag: 2,
  mittwoch: 3,
  donnerstag: 4,
  freitag: 5,
  samstag: 6, sonnabend: 6,
  sonntag: 7,
  // Italian
  lunedi: 1,
  martedi: 2,
  mercoledi: 3,
  giovedi: 4,
  venerdi: 5,
  sabato: 6,
  domenica: 7,
  // French
  lundi: 1,
  mardi: 2,
  mercredi: 3,
  jeudi: 4,
  vendredi: 5,
  samedi: 6,
  dimanche: 7,
  // Spanish
  lunes: 1,
  martes: 2,
  miercoles: 3,
  jueves: 4,
  viernes: 5,
  sabado: 6,
  domingo: 7
};

const RELATIVE_DAYS_RE =
  /^(?:in|tra|fra|dans|en)\s+(\d{1,3})\s*(?:days?|tag(?:en|e)?|giorn[oi]|jours?|dias?|d)$/;
const RELATIVE_WEEKS_RE =
  /^(?:in|tra|fra|dans|en)\s+(\d{1,2})\s*(?:weeks?|wochen?|settiman[ae]|semaines?|semanas?|w)$/;
const ISO_DATE_RE = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;
const EURO_DATE_RE = /^(\d{1,2})\.(\d{1,2})\.(?:(\d{4}|\d{2}))?$/;

export function normalizeDiacritics(input: string): string {
  return input
    .replace(/[\u2018\u2019\u02BC`]/g, "'")
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

function formatDatePreview(d: Date, lang: SupportedLang): string {
  try {
    return new Intl.DateTimeFormat(lang, {
      weekday: 'long',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(d);
  } catch {
    return d.toISOString().slice(0, 10);
  }
}

function isoDayOfWeek(d: Date): number {
  const js = d.getDay();
  return js === 0 ? 7 : js;
}

function parseCoreDatePart(
  normalized: string,
  trimmed: string,
  lang: SupportedLang,
  refMidnight: Date
): Omit<ParsedNaturalDate, 'reminderTimeEpochMillis' | 'reminderClock'> | null {
  // 1. This Evening
  if (
    [
      'this evening', 'tonight', 'evening',
      'heute abend', 'abend',
      'stasera', 'questa sera',
      'ce soir', 'soir',
      'esta noche', 'noche'
    ].includes(normalized)
  ) {
    return {
      scheduleState: 'THIS_EVENING',
      whenDateEpochMillis: refMidnight.getTime(),
      formattedPreview: `${formatDatePreview(refMidnight, lang)} · 🌙`,
      matchedExpression: trimmed
    };
  }

  // 2. Today
  if (['today', 'tod', 'heute', 'oggi', "aujourd'hui", 'aujourdhui', 'hoy'].includes(normalized)) {
    return {
      scheduleState: 'TODAY',
      whenDateEpochMillis: refMidnight.getTime(),
      formattedPreview: `${formatDatePreview(refMidnight, lang)} · ★`,
      matchedExpression: trimmed
    };
  }

  // 3. Tomorrow
  if (['tomorrow', 'tmr', 'tom', 'morgen', 'domani', 'demain', 'manana'].includes(normalized)) {
    const target = new Date(refMidnight.getFullYear(), refMidnight.getMonth(), refMidnight.getDate() + 1);
    return {
      scheduleState: 'UPCOMING',
      whenDateEpochMillis: target.getTime(),
      formattedPreview: formatDatePreview(target, lang),
      matchedExpression: trimmed
    };
  }

  // 4. Overmorrow (+2d)
  if (
    [
      'overmorrow',
      'ubermorgen', 'uebermorgen',
      'dopodomani',
      'apres-demain', 'apres demain',
      'pasado manana'
    ].includes(normalized)
  ) {
    const target = new Date(refMidnight.getFullYear(), refMidnight.getMonth(), refMidnight.getDate() + 2);
    return {
      scheduleState: 'UPCOMING',
      whenDateEpochMillis: target.getTime(),
      formattedPreview: formatDatePreview(target, lang),
      matchedExpression: trimmed
    };
  }

  // 5. Someday
  if (['someday', 'later', 'irgendwann', 'spater', 'prima o poi', 'un jour', 'algun dia'].includes(normalized)) {
    return {
      scheduleState: 'SOMEDAY',
      whenDateEpochMillis: null,
      formattedPreview: '📦 Someday',
      matchedExpression: trimmed
    };
  }

  // 6. Anytime
  if (
    [
      'anytime',
      'jederzeit',
      'in qualsiasi momento',
      'a tout moment',
      'en cualquier momento'
    ].includes(normalized)
  ) {
    return {
      scheduleState: 'ANYTIME',
      whenDateEpochMillis: null,
      formattedPreview: '⚡ Anytime',
      matchedExpression: trimmed
    };
  }

  // 7. Relative Days
  const relDays = normalized.match(RELATIVE_DAYS_RE);
  if (relDays) {
    const days = Number(relDays[1]);
    if (Number.isFinite(days)) {
      const target = new Date(refMidnight.getFullYear(), refMidnight.getMonth(), refMidnight.getDate() + days);
      return {
        scheduleState: days === 0 ? 'TODAY' : 'UPCOMING',
        whenDateEpochMillis: target.getTime(),
        formattedPreview: formatDatePreview(target, lang),
        matchedExpression: trimmed
      };
    }
  }

  // 8. Relative Weeks
  const relWeeks = normalized.match(RELATIVE_WEEKS_RE);
  if (relWeeks) {
    const weeks = Number(relWeeks[1]);
    if (Number.isFinite(weeks)) {
      const target = new Date(
        refMidnight.getFullYear(),
        refMidnight.getMonth(),
        refMidnight.getDate() + weeks * 7
      );
      return {
        scheduleState: weeks === 0 ? 'TODAY' : 'UPCOMING',
        whenDateEpochMillis: target.getTime(),
        formattedPreview: formatDatePreview(target, lang),
        matchedExpression: trimmed
      };
    }
  }

  // 9. Weekday / Next Weekday
  const cleanedWeekday = normalized
    .replace(
      /^(?:next|nachsten|nachster|nachste|naechsten|naechste|diesen|kommenden|am|prossimo|prossima|proximo|proxima|prochain|prochaine)\s+/,
      ''
    )
    .replace(/\s+(?:prossimo|prossima|prochain|prochaine|proximo|proxima)$/, '')
    .trim();

  const targetDow = WEEKDAY_ALIASES[cleanedWeekday];
  if (targetDow != null) {
    const currentDow = isoDayOfWeek(refMidnight);
    const delta = targetDow > currentDow ? targetDow - currentDow : 7 - (currentDow - targetDow);
    const target = new Date(
      refMidnight.getFullYear(),
      refMidnight.getMonth(),
      refMidnight.getDate() + delta
    );
    return {
      scheduleState: 'UPCOMING',
      whenDateEpochMillis: target.getTime(),
      formattedPreview: formatDatePreview(target, lang),
      matchedExpression: trimmed
    };
  }

  // 10. ISO YYYY-MM-DD
  const isoMatch = normalized.match(ISO_DATE_RE);
  if (isoMatch) {
    const y = Number(isoMatch[1]);
    const m = Number(isoMatch[2]);
    const d = Number(isoMatch[3]);
    if (y >= 1970 && y <= 2100 && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
      const candidate = new Date(y, m - 1, d, 0, 0, 0, 0);
      if (candidate.getMonth() === m - 1 && candidate.getDate() === d) {
        return {
          scheduleState: candidate.getTime() <= refMidnight.getTime() ? 'TODAY' : 'UPCOMING',
          whenDateEpochMillis: candidate.getTime(),
          formattedPreview: formatDatePreview(candidate, lang),
          matchedExpression: trimmed
        };
      }
    }
  }

  // 11. Euro DD.MM.YYYY
  const euroMatch = normalized.match(EURO_DATE_RE);
  if (euroMatch) {
    const d = Number(euroMatch[1]);
    const m = Number(euroMatch[2]);
    const rawY = euroMatch[3] || '';
    let y = refMidnight.getFullYear();
    if (rawY.length === 2) y = 2000 + Number(rawY);
    else if (rawY.length === 4) y = Number(rawY);
    if (y >= 1970 && y <= 2100 && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
      let candidate = new Date(y, m - 1, d, 0, 0, 0, 0);
      if (candidate.getMonth() === m - 1 && candidate.getDate() === d) {
        if (!rawY && candidate.getTime() < refMidnight.getTime()) {
          candidate = new Date(y + 1, m - 1, d, 0, 0, 0, 0);
        }
        return {
          scheduleState: candidate.getTime() <= refMidnight.getTime() ? 'TODAY' : 'UPCOMING',
          whenDateEpochMillis: candidate.getTime(),
          formattedPreview: formatDatePreview(candidate, lang),
          matchedExpression: trimmed
        };
      }
    }
  }

  return null;
}

export function parseNaturalDate(
  rawInput: string,
  lang: SupportedLang = 'en',
  reference: Date = new Date()
): ParsedNaturalDate | null {
  const trimmed = rawInput.trim();
  if (!trimmed) return null;

  const normalized = normalizeDiacritics(trimmed.toLowerCase());
  const refMidnight = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate(),
    0,
    0,
    0,
    0
  );

  // First try exact date match without reminder suffix
  const direct = parseCoreDatePart(normalized, trimmed, lang, refMidnight);
  if (direct) {
    return attachReminderToParsedDate(direct, null, refMidnight, trimmed);
  }

  // Next try stripping a trailing multilingual reminder time (e.g. "tomorrow at 9:30", "demain à 18h30")
  const extracted = extractTrailingReminderTime(normalized);
  if (extracted) {
    const core = parseCoreDatePart(extracted.datePart, trimmed, lang, refMidnight);
    if (core) {
      return attachReminderToParsedDate(core, extracted, refMidnight, trimmed);
    }
  }

  return null;
}
