import { GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth';
import type { CalendarEventItem, DeviceCalendarInfo } from '../domain/types';
import { getGoogleCalendarAuthInstance } from './firebaseClient';

const GCAL_TOKENS_STORAGE_KEY = 'thingsbook_gcal_tokens_v1';
const ICS_CALENDARS_STORAGE_KEY = 'thingsbook_ics_calendars_v1';
const DAY_MS = 86_400_000;
const DEFAULT_CALENDAR_COLOR = '#1A73E8';

interface StoredGcalAccount {
  email: string;
  accessToken: string;
  expiresAt: number;
}

interface StoredIcsEvent {
  uid: string;
  title: string;
  startMillis: number;
  endMillis: number;
  isAllDay: boolean;
  location: string | null;
  url: string | null;
}

interface StoredIcsCalendar {
  id: string;
  displayName: string;
  accountName: string;
  colorCss: string;
  events: StoredIcsEvent[];
}

interface GoogleCalendarListEntry {
  id?: string;
  summary?: string;
  summaryOverride?: string;
  backgroundColor?: string;
  primary?: boolean;
  selected?: boolean;
  hidden?: boolean;
  deleted?: boolean;
}

interface GoogleCalendarEventDto {
  id?: string;
  status?: string;
  summary?: string;
  location?: string;
  htmlLink?: string;
  start?: {
    date?: string;
    dateTime?: string;
  };
  end?: {
    date?: string;
    dateTime?: string;
  };
  attendees?: Array<{
    self?: boolean;
    responseStatus?: string;
  }>;
}

// ==================== Epoch Day & Time Helpers (Exact Android Parity) ====================

export function localEpochDayFromMs(ms: number): number {
  const d = new Date(ms);
  return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY_MS);
}

export function utcEpochDayFromMs(ms: number): number {
  return Math.floor(ms / DAY_MS);
}

export function localMidnightFromEpochDay(epochDay: number): number {
  const utc = new Date(epochDay * DAY_MS);
  return new Date(
    utc.getUTCFullYear(),
    utc.getUTCMonth(),
    utc.getUTCDate(),
    0,
    0,
    0,
    0
  ).getTime();
}

export function localEndExclusiveFromEpochDay(epochDay: number): number {
  const utc = new Date(epochDay * DAY_MS);
  return new Date(
    utc.getUTCFullYear(),
    utc.getUTCMonth(),
    utc.getUTCDate() + 1,
    0,
    0,
    0,
    0
  ).getTime();
}

/**
 * Resolves inclusive [startEpochDay, endEpochDay] for a calendar event instance.
 *
 * Exact parity with Android `DeviceCalendarRepository.resolveEventDateRange`:
 * - All-day events are stored as UTC midnight boundaries [start, endExclusive), so a single-day
 *   all-day event has endMillis = startMillis + 86_400_000 UTC and must NOT shift across timezones
 *   or bleed into the following day.
 * - Timed events use local timezone dates; an event ending at exact local 00:00 midnight
 *   (inclusiveEndMillis = endMillis - 1) stays on the previous day unless it crosses past midnight.
 */
export function resolveEventDateRange(
  beginMillis: number,
  endMillis: number,
  isAllDay: boolean
): [number, number] {
  if (isAllDay) {
    const startDate = utcEpochDayFromMs(beginMillis);
    const effectiveEndMillis = endMillis > beginMillis ? endMillis : beginMillis + DAY_MS;
    const endExclusiveDate = utcEpochDayFromMs(effectiveEndMillis);
    const inclusiveEnd = Math.max(startDate, endExclusiveDate - 1);
    return [startDate, inclusiveEnd];
  } else {
    const startDate = localEpochDayFromMs(beginMillis);
    const inclusiveEndMillis = endMillis > beginMillis ? endMillis - 1 : beginMillis;
    const endDate = localEpochDayFromMs(inclusiveEndMillis);
    const inclusiveEnd = Math.max(startDate, endDate);
    return [startDate, inclusiveEnd];
  }
}

/**
 * Groups events by epochDay within [startEpochDay, endEpochDay], deduplicating multi-instance
 * overlaps per day and sorting all-day events first, then startMillis ascending, then title.
 */
export function groupEventsByEpochDayInRange(
  rawEvents: CalendarEventItem[],
  startEpochDay: number,
  endEpochDay: number
): Map<number, CalendarEventItem[]> {
  if (endEpochDay < startEpochDay || rawEvents.length === 0) {
    return new Map();
  }

  const rawByDay = new Map<number, CalendarEventItem[]>();
  const seenKeysPerDay = new Map<number, Set<string>>();

  for (const ev of rawEvents) {
    const [evStartDay, evEndDay] = resolveEventDateRange(
      ev.startMillis,
      ev.endMillis,
      ev.isAllDay
    );
    const clampedStart = Math.max(evStartDay, startEpochDay);
    const clampedEnd = Math.min(evEndDay, endEpochDay);
    if (clampedEnd < clampedStart) continue;

    for (let day = clampedStart; day <= clampedEnd; day++) {
      let seen = seenKeysPerDay.get(day);
      if (!seen) {
        seen = new Set<string>();
        seenKeysPerDay.set(day, seen);
      }
      const dedupeKey = `${ev.calendarId}_${ev.eventId}_${ev.startMillis}`;
      if (seen.has(dedupeKey)) continue;
      seen.add(dedupeKey);

      const list = rawByDay.get(day);
      if (list) {
        list.push(ev);
      } else {
        rawByDay.set(day, [ev]);
      }
    }
  }

  const sortedResult = new Map<number, CalendarEventItem[]>();
  const sortedDays = Array.from(rawByDay.keys()).sort((a, b) => a - b);
  for (const day of sortedDays) {
    const list = rawByDay.get(day)!;
    list.sort((a, b) => {
      if (a.isAllDay !== b.isAllDay) {
        return a.isAllDay ? -1 : 1;
      }
      if (a.startMillis !== b.startMillis) {
        return a.startMillis - b.startMillis;
      }
      return a.title.localeCompare(b.title, undefined, { sensitivity: 'base' });
    });
    sortedResult.set(day, list);
  }

  return sortedResult;
}

export function formatCalendarEventTimeRange(startMillis: number, endMillis: number): string {
  const pad2 = (n: number) => String(n).padStart(2, '0');
  const s = new Date(startMillis);
  const startStr = `${pad2(s.getHours())}:${pad2(s.getMinutes())}`;
  if (endMillis > startMillis) {
    const e = new Date(endMillis);
    const endStr = `${pad2(e.getHours())}:${pad2(e.getMinutes())}`;
    return `${startStr}–${endStr}`;
  }
  return startStr;
}

/**
 * Opens a tapped calendar event in Google Calendar (Android v1.6.23 parity).
 */
export function openCalendarEventInGoogleCalendar(event: CalendarEventItem): void {
  let targetUrl = '';
  if (event.htmlLink && /^https?:\/\//i.test(event.htmlLink)) {
    targetUrl = event.htmlLink;
  } else {
    const d = new Date(event.startMillis);
    const year = event.isAllDay ? d.getUTCFullYear() : d.getFullYear();
    const month = (event.isAllDay ? d.getUTCMonth() : d.getMonth()) + 1;
    const day = event.isAllDay ? d.getUTCDate() : d.getDate();
    targetUrl = `https://calendar.google.com/calendar/r/day/${year}/${month}/${day}`;
  }
  if (typeof window !== 'undefined') {
    window.open(targetUrl, '_blank', 'noopener,noreferrer');
  }
}

// ==================== Storage Helpers ====================

function loadStoredGcalAccounts(): StoredGcalAccount[] {
  try {
    const raw = localStorage.getItem(GCAL_TOKENS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (x): x is StoredGcalAccount =>
        typeof x?.email === 'string' &&
        typeof x?.accessToken === 'string' &&
        typeof x?.expiresAt === 'number'
    );
  } catch {
    return [];
  }
}

function saveStoredGcalAccounts(accounts: StoredGcalAccount[]): void {
  try {
    localStorage.setItem(GCAL_TOKENS_STORAGE_KEY, JSON.stringify(accounts));
  } catch {
    // ignore storage quota errors
  }
}

function loadStoredIcsCalendars(): StoredIcsCalendar[] {
  try {
    const raw = localStorage.getItem(ICS_CALENDARS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (c): c is StoredIcsCalendar =>
        typeof c?.id === 'string' &&
        typeof c?.displayName === 'string' &&
        Array.isArray(c?.events)
    );
  } catch {
    return [];
  }
}

function saveStoredIcsCalendars(calendars: StoredIcsCalendar[]): void {
  try {
    localStorage.setItem(ICS_CALENDARS_STORAGE_KEY, JSON.stringify(calendars));
  } catch {
    // ignore storage quota errors
  }
}

function sanitizeColorCss(color: string | undefined | null): string {
  if (!color) return DEFAULT_CALENDAR_COLOR;
  const trimmed = color.trim();
  if (/^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(trimmed)) {
    return trimmed.slice(0, 7);
  }
  return DEFAULT_CALENDAR_COLOR;
}

// ==================== Google Calendar OAuth & REST API v3 ====================

export function hasCalendarAccess(): boolean {
  const now = Date.now();
  const validGcal = loadStoredGcalAccounts().some(
    (a) => a.accessToken.length > 0 && a.expiresAt > now
  );
  if (validGcal) return true;
  return loadStoredIcsCalendars().length > 0;
}

export function hasValidGoogleCalendarToken(): boolean {
  const now = Date.now();
  return loadStoredGcalAccounts().some((a) => a.accessToken.length > 0 && a.expiresAt > now);
}

export async function connectGoogleCalendarPopup(): Promise<string> {
  const auth = getGoogleCalendarAuthInstance();
  const provider = new GoogleAuthProvider();
  provider.addScope('https://www.googleapis.com/auth/calendar.readonly');
  provider.setCustomParameters({ prompt: 'select_account' });

  const result = await signInWithPopup(auth, provider);
  const credential = GoogleAuthProvider.credentialFromResult(result);
  const accessToken = credential?.accessToken;
  if (!accessToken) {
    throw new Error('Google Calendar access token was not granted.');
  }
  const email = (result.user.email || result.user.displayName || 'Google Account').trim();
  const expiresAt = Date.now() + 55 * 60 * 1000;

  const existing = loadStoredGcalAccounts().filter(
    (a) => a.email.toLowerCase() !== email.toLowerCase()
  );
  existing.push({ email, accessToken, expiresAt });
  saveStoredGcalAccounts(existing);
  return email;
}

export async function disconnectGoogleCalendars(): Promise<void> {
  saveStoredGcalAccounts([]);
  try {
    await signOut(getGoogleCalendarAuthInstance());
  } catch {
    // ignore sign-out errors
  }
}

function markGcalAccountExpired(email: string): void {
  const updated = loadStoredGcalAccounts().map((a) =>
    a.email.toLowerCase() === email.toLowerCase() ? { ...a, expiresAt: 0 } : a
  );
  saveStoredGcalAccounts(updated);
}

function parseYyyyMmDdUtcMs(dateStr: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr.trim());
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  return Date.UTC(year, month - 1, day, 0, 0, 0, 0);
}

// ==================== RFC 5545 (.ics) Parser & Persistence ====================

function unfoldIcsLines(icsText: string): string[] {
  const unfolded = icsText.replace(/\r?\n[ \t]/g, '');
  return unfolded.split(/\r?\n/);
}

function unescapeIcsText(val: string): string {
  return val
    .replace(/\\n/gi, '\n')
    .replace(/\\,/g, ',')
    .replace(/\\;/g, ';')
    .replace(/\\\\/g, '\\')
    .trim();
}

function parseIcsDateTimeValue(
  rawVal: string,
  paramsPart: string
): { ms: number; isAllDay: boolean } | null {
  const val = rawVal.trim();
  const upperParams = paramsPart.toUpperCase();

  // Pure date: YYYYMMDD
  const dateOnlyMatch = /^(\d{4})(\d{2})(\d{2})$/.exec(val);
  if (dateOnlyMatch || upperParams.includes('VALUE=DATE')) {
    const m = /^(\d{4})(\d{2})(\d{2})/.exec(val);
    if (!m) return null;
    const ms = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 0, 0, 0, 0);
    return { ms, isAllDay: true };
  }

  // Date-time: YYYYMMDDTHHMMSS(Z)?
  const dtMatch = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z)?$/i.exec(val);
  if (dtMatch) {
    const year = Number(dtMatch[1]);
    const month = Number(dtMatch[2]);
    const day = Number(dtMatch[3]);
    const hour = Number(dtMatch[4]);
    const minute = Number(dtMatch[5]);
    const second = Number(dtMatch[6]);
    const isUtc = Boolean(dtMatch[7]);

    if (isUtc) {
      return {
        ms: Date.UTC(year, month - 1, day, hour, minute, second, 0),
        isAllDay: false
      };
    }

    // Check TZID parameter if provided
    const tzMatch = /TZID=([^;:]+)/i.exec(paramsPart);
    if (tzMatch) {
      const tzId = tzMatch[1].replace(/^"|"$/g, '').trim();
      try {
        const approxUtc = Date.UTC(year, month - 1, day, hour, minute, second, 0);
        const fmt = new Intl.DateTimeFormat('en-US', {
          timeZone: tzId,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hourCycle: 'h23'
        });
        const parts = fmt.formatToParts(new Date(approxUtc));
        const getNum = (t: string) => Number(parts.find((p) => p.type === t)?.value ?? '0');
        const tzAsUtc = Date.UTC(
          getNum('year'),
          getNum('month') - 1,
          getNum('day'),
          getNum('hour'),
          getNum('minute'),
          getNum('second'),
          0
        );
        const offsetMs = tzAsUtc - approxUtc;
        return { ms: approxUtc - offsetMs, isAllDay: false };
      } catch {
        // Fallback to local time if TZID is non-standard (e.g. Windows timezone name)
      }
    }

    return {
      ms: new Date(year, month - 1, day, hour, minute, second, 0).getTime(),
      isAllDay: false
    };
  }

  const parsed = Date.parse(val);
  if (Number.isFinite(parsed)) {
    return { ms: parsed, isAllDay: false };
  }
  return null;
}

export function importIcsCalendarText(
  icsText: string,
  fallbackName = 'Imported Calendar'
): DeviceCalendarInfo {
  const lines = unfoldIcsLines(icsText);
  let calName = '';
  let calColor = DEFAULT_CALENDAR_COLOR;
  const events: StoredIcsEvent[] = [];

  let inEvent = false;
  let uid = '';
  let summary = '';
  let location: string | null = null;
  let url: string | null = null;
  let status = '';
  let declinedSelf = false;
  let dtStart: { ms: number; isAllDay: boolean } | null = null;
  let dtEnd: { ms: number; isAllDay: boolean } | null = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    if (line.toUpperCase() === 'BEGIN:VEVENT') {
      inEvent = true;
      uid = '';
      summary = '';
      location = null;
      url = null;
      status = '';
      declinedSelf = false;
      dtStart = null;
      dtEnd = null;
      continue;
    }

    if (line.toUpperCase() === 'END:VEVENT') {
      if (inEvent && dtStart && status.toUpperCase() !== 'CANCELLED' && !declinedSelf) {
        const startMillis = dtStart.ms;
        const isAllDay = dtStart.isAllDay;
        let endMillis: number;
        if (dtEnd && dtEnd.ms > startMillis) {
          endMillis = dtEnd.ms;
        } else {
          endMillis = isAllDay ? startMillis + DAY_MS : startMillis + 3_600_000;
        }
        events.push({
          uid: uid || `ics_ev_${events.length + 1}_${startMillis}`,
          title: summary,
          startMillis,
          endMillis,
          isAllDay,
          location,
          url
        });
      }
      inEvent = false;
      continue;
    }

    const colonIdx = line.indexOf(':');
    if (colonIdx <= 0) continue;
    const left = line.slice(0, colonIdx);
    const value = line.slice(colonIdx + 1);
    const semiIdx = left.indexOf(';');
    const propName = (semiIdx >= 0 ? left.slice(0, semiIdx) : left).toUpperCase();
    const paramsPart = semiIdx >= 0 ? left.slice(semiIdx + 1) : '';

    if (!inEvent) {
      if (propName === 'X-WR-CALNAME' && !calName) {
        calName = unescapeIcsText(value);
      } else if ((propName === 'X-APPLE-CALENDAR-COLOR' || propName === 'COLOR') && value.trim()) {
        calColor = sanitizeColorCss(value);
      }
      continue;
    }

    switch (propName) {
      case 'UID':
        uid = unescapeIcsText(value);
        break;
      case 'SUMMARY':
        summary = unescapeIcsText(value);
        break;
      case 'LOCATION':
        location = unescapeIcsText(value) || null;
        break;
      case 'URL':
        url = unescapeIcsText(value) || null;
        break;
      case 'STATUS':
        status = value.trim();
        break;
      case 'ATTENDEE':
        if (
          /PARTSTAT=DECLINED/i.test(paramsPart) &&
          (/X-NUM-GUESTS/i.test(paramsPart) || /ROLE=REQ-PARTICIPANT/i.test(paramsPart))
        ) {
          // Only mark declined when explicit self-decline marker is present
          if (/SELF=TRUE/i.test(paramsPart)) {
            declinedSelf = true;
          }
        }
        break;
      case 'DTSTART':
        dtStart = parseIcsDateTimeValue(value, paramsPart);
        break;
      case 'DTEND':
        dtEnd = parseIcsDateTimeValue(value, paramsPart);
        break;
    }
  }

  const finalName = (calName || fallbackName.replace(/\.ics$/i, '').trim() || 'Calendar').trim();
  const calId = `ics:${finalName.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${Date.now().toString(36)}`;

  const existing = loadStoredIcsCalendars().filter(
    (c) => c.displayName.toLowerCase() !== finalName.toLowerCase()
  );
  const record: StoredIcsCalendar = {
    id: calId,
    displayName: finalName,
    accountName: '.ics Calendar',
    colorCss: calColor,
    events
  };
  existing.push(record);
  saveStoredIcsCalendars(existing);

  return {
    id: record.id,
    displayName: record.displayName,
    accountName: record.accountName,
    colorCss: record.colorCss,
    isPrimary: existing.length === 1,
    source: 'ics'
  };
}

export function removeImportedIcsCalendar(calendarId: string): void {
  const next = loadStoredIcsCalendars().filter((c) => c.id !== calendarId);
  saveStoredIcsCalendars(next);
}

// ==================== Unified Calendar & Event Queries ====================

export async function queryAvailableCalendars(): Promise<DeviceCalendarInfo[]> {
  const results: DeviceCalendarInfo[] = [];
  const now = Date.now();
  const gcalAccounts = loadStoredGcalAccounts().filter(
    (a) => a.accessToken.length > 0 && a.expiresAt > now
  );

  for (const account of gcalAccounts) {
    try {
      const resp = await fetch(
        'https://www.googleapis.com/calendar/v3/users/me/calendarList?minAccessRole=reader&showHidden=false',
        {
          headers: {
            Authorization: `Bearer ${account.accessToken}`
          }
        }
      );
      if (resp.status === 401 || resp.status === 403) {
        markGcalAccountExpired(account.email);
        continue;
      }
      if (!resp.ok) continue;
      const data = (await resp.json()) as { items?: GoogleCalendarListEntry[] };
      for (const item of data.items ?? []) {
        if (!item.id || item.deleted || item.hidden) continue;
        const displayName = (item.summaryOverride || item.summary || item.id).trim();
        results.push({
          id: `gcal:${account.email}:${item.id}`,
          displayName,
          accountName: account.email,
          colorCss: sanitizeColorCss(item.backgroundColor),
          isPrimary: Boolean(item.primary),
          source: 'gcal'
        });
      }
    } catch {
      // Offline or network error: skip gracefully
    }
  }

  const icsCalendars = loadStoredIcsCalendars();
  icsCalendars.forEach((cal, idx) => {
    results.push({
      id: cal.id,
      displayName: cal.displayName,
      accountName: cal.accountName,
      colorCss: sanitizeColorCss(cal.colorCss),
      isPrimary: results.length === 0 && idx === 0,
      source: 'ics'
    });
  });

  results.sort((a, b) => {
    if (a.isPrimary !== b.isPrimary) return a.isPrimary ? -1 : 1;
    const accCmp = a.accountName.localeCompare(b.accountName, undefined, { sensitivity: 'base' });
    if (accCmp !== 0) return accCmp;
    return a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' });
  });

  return results;
}

export async function queryEventsForDateRange(
  startEpochDay: number,
  endEpochDay: number,
  selectedCalendarIds: string[],
  knownCalendars?: DeviceCalendarInfo[]
): Promise<Map<number, CalendarEventItem[]>> {
  if (endEpochDay < startEpochDay || selectedCalendarIds.length === 0) {
    return new Map();
  }

  const selectedSet = new Set(selectedCalendarIds);
  const rangeStartMillis = localMidnightFromEpochDay(startEpochDay);
  const rangeEndExclusiveMillis = localEndExclusiveFromEpochDay(endEpochDay);
  // Pad query window by 1 day on each side so UTC all-day events in any timezone are captured
  const fetchMinIso = new Date(rangeStartMillis - DAY_MS).toISOString();
  const fetchMaxIso = new Date(rangeEndExclusiveMillis + DAY_MS).toISOString();

  const rawEvents: CalendarEventItem[] = [];
  const calMetaMap = new Map<string, DeviceCalendarInfo>();
  for (const c of knownCalendars ?? []) {
    calMetaMap.set(c.id, c);
  }

  // 1. Fetch Google Calendar REST API v3 events for selected `gcal:` calendars
  const now = Date.now();
  const validAccounts = new Map<string, string>();
  for (const acc of loadStoredGcalAccounts()) {
    if (acc.accessToken.length > 0 && acc.expiresAt > now) {
      validAccounts.set(acc.email.toLowerCase(), acc.accessToken);
    }
  }

  const gcalIds = selectedCalendarIds.filter((id) => id.startsWith('gcal:'));
  await Promise.all(
    gcalIds.map(async (compositeId) => {
      // Format: gcal:<accountEmail>:<rawCalendarId>
      const rest = compositeId.slice('gcal:'.length);
      const sepIdx = rest.indexOf(':');
      if (sepIdx <= 0) return;
      const accountEmail = rest.slice(0, sepIdx);
      const rawCalId = rest.slice(sepIdx + 1);
      const token = validAccounts.get(accountEmail.toLowerCase());
      if (!token) return;

      const meta = calMetaMap.get(compositeId);
      const calName = meta?.displayName || rawCalId;
      const calColor = meta?.colorCss || DEFAULT_CALENDAR_COLOR;

      try {
        const url =
          `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(rawCalId)}/events` +
          `?singleEvents=true&orderBy=startTime` +
          `&timeMin=${encodeURIComponent(fetchMinIso)}` +
          `&timeMax=${encodeURIComponent(fetchMaxIso)}` +
          `&maxResults=500`;
        const resp = await fetch(url, {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (resp.status === 401 || resp.status === 403) {
          markGcalAccountExpired(accountEmail);
          return;
        }
        if (!resp.ok) return;
        const data = (await resp.json()) as { items?: GoogleCalendarEventDto[] };
        for (const ev of data.items ?? []) {
          if (ev.status === 'cancelled') continue;
          const selfDeclined = ev.attendees?.some(
            (att) => att.self && att.responseStatus === 'declined'
          );
          if (selfDeclined) continue;

          let isAllDay = false;
          let startMillis: number | null = null;
          let endMillis: number | null = null;

          if (ev.start?.date) {
            isAllDay = true;
            startMillis = parseYyyyMmDdUtcMs(ev.start.date);
            endMillis = ev.end?.date
              ? parseYyyyMmDdUtcMs(ev.end.date)
              : startMillis != null
                ? startMillis + DAY_MS
                : null;
          } else if (ev.start?.dateTime) {
            const s = Date.parse(ev.start.dateTime);
            const e = ev.end?.dateTime ? Date.parse(ev.end.dateTime) : s;
            if (Number.isFinite(s)) {
              startMillis = s;
              endMillis = Number.isFinite(e) ? e : s;
            }
          }

          if (startMillis == null || endMillis == null) continue;

          rawEvents.push({
            eventId: ev.id || `${startMillis}_${ev.summary || ''}`,
            calendarId: compositeId,
            calendarName: calName,
            calendarColorCss: calColor,
            title: (ev.summary || '').trim(),
            startMillis,
            endMillis,
            isAllDay,
            location: ev.location?.trim() || null,
            htmlLink: ev.htmlLink || null
          });
        }
      } catch {
        // Ignore transient network errors
      }
    })
  );

  // 2. Include events from selected `.ics` calendars
  for (const cal of loadStoredIcsCalendars()) {
    if (!selectedSet.has(cal.id)) continue;
    for (const ev of cal.events) {
      rawEvents.push({
        eventId: ev.uid,
        calendarId: cal.id,
        calendarName: cal.displayName,
        calendarColorCss: sanitizeColorCss(cal.colorCss),
        title: ev.title.trim(),
        startMillis: ev.startMillis,
        endMillis: ev.endMillis,
        isAllDay: ev.isAllDay,
        location: ev.location,
        htmlLink: ev.url
      });
    }
  }

  return groupEventsByEpochDayInRange(rawEvents, startEpochDay, endEpochDay);
}
