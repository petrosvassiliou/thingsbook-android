import type { EffortLevel, ScheduleState, SupportedLang } from '../domain/types.ts';
import { getFirebaseAppInstance } from './firebaseClient.ts';

export interface BrainDumpTaskDto {
  title: string;
  notes: string;
  scheduleState: ScheduleState;
  whenDaysOffset: number | null;
  deadlineDaysOffset: number | null;
  checklist: string[];
}

export interface RoadmapTaskBlueprintDto {
  key: string;
  title: string;
  notes: string;
  dayOffset: number;
  durationMinutes: number;
  effortLevel: EffortLevel | null;
  isMilestone: boolean;
  dependsOnKeys: string[];
  checklist: string[];
}

export interface ProjectBlueprintHeadingDto {
  title: string;
  startDayOffset?: number;
  endDayOffset?: number;
  tasks: string[];
  richTasks?: RoadmapTaskBlueprintDto[];
}

export interface ProjectBlueprintResponse {
  projectTitle: string;
  projectNotes: string;
  headings: ProjectBlueprintHeadingDto[];
}

export interface DayTimeblockCandidate {
  taskId: string;
  title: string;
  durationMinutes: number;
  effortLevel?: EffortLevel | null;
  isPinned?: boolean;
  hasTodayDeadline?: boolean;
}

export interface DayTimeblockItemDto {
  taskId: string;
  startMinutes: number;
  durationMinutes: number;
  reason: string;
}

export interface DayTimeblockPlanResponse {
  summary: string;
  blocks: DayTimeblockItemDto[];
}

export interface LogbookReviewResponse {
  summaryParagraph: string;
  suggestedFocusTasks: string[];
}

function languageDisplayName(lang: SupportedLang): string {
  switch (lang) {
    case 'de':
      return 'German (Deutsch)';
    case 'it':
      return 'Italian (Italiano)';
    case 'fr':
      return 'French (Français)';
    case 'es':
      return 'Spanish (Español)';
    default:
      return 'English';
  }
}

function extractJsonObject(raw: string): string {
  const start = raw.indexOf('{');
  const end = raw.lastIndexOf('}');
  return start >= 0 && end > start ? raw.slice(start, end + 1) : raw;
}

async function callGeminiDirectRest(apiKey: string, prompt: string): Promise<string | null> {
  const res = await fetch(
    'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=UTF-8',
        'x-goog-api-key': apiKey
      },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          responseMimeType: 'application/json',
          temperature: 0.35
        }
      })
    }
  );
  if (!res.ok) {
    if (res.status === 400 || res.status === 401 || res.status === 403 || res.status === 429) {
      throw new Error(
        res.status === 429
          ? 'Gemini API rate limit reached (HTTP 429).'
          : `Gemini API key rejected (HTTP ${res.status}).`
      );
    }
    return null;
  }
  const data = await res.json();
  const text = data?.candidates?.[0]?.content?.parts?.find(
    (p: { text?: string }) => typeof p?.text === 'string' && p.text.trim().length > 0
  )?.text;
  return typeof text === 'string' ? text : null;
}

async function callFirebaseVertexAi(prompt: string): Promise<string | null> {
  try {
    const app = getFirebaseAppInstance();
    const firebaseAi = await import('firebase/ai');
    const ai = firebaseAi.getAI(app, { backend: new firebaseAi.GoogleAIBackend() });
    const model = firebaseAi.getGenerativeModel(ai, {
      model: 'gemini-2.5-flash',
      generationConfig: {
        temperature: 0.35,
        responseMimeType: 'application/json'
      }
    });
    const result = await model.generateContent(prompt);
    return result.response.text() || null;
  } catch {
    return null;
  }
}

async function callGeminiJson(prompt: string, customApiKey: string): Promise<string | null> {
  const trimmedKey = customApiKey.trim();
  if (trimmedKey) {
    const direct = await callGeminiDirectRest(trimmedKey, prompt);
    if (direct) return direct;
  }
  return callFirebaseVertexAi(prompt);
}

export async function generateMagicChecklist(
  taskTitle: string,
  taskNotes: string,
  lang: SupportedLang,
  customApiKey = ''
): Promise<string[]> {
  const safeTitle = taskTitle.trim().slice(0, 500);
  const safeNotes = taskNotes.trim().slice(0, 2000);
  const langName = languageDisplayName(lang);
  const prompt = `You are the calm, precise GTD assistant inside Thingsbook (Things 3 style).
Respond strictly in ${langName} (Locale: ${lang}).
Break down the following To-Do into 4 to 5 concise, actionable checklist sub-steps.
To-Do Title: "${safeTitle}"
To-Do Notes: "${safeNotes}"
Return ONLY valid JSON matching: {"items": ["Step 1", "Step 2", "Step 3", "Step 4"]}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (Array.isArray(parsed?.items)) {
        const clean = parsed.items.map((x: unknown) => String(x).trim()).filter(Boolean);
        if (clean.length > 0) return clean;
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return offlineLocalizedBreakdown(safeTitle, lang);
}

export async function parseBrainDumpToTasks(
  rawBrainDump: string,
  lang: SupportedLang,
  customApiKey = ''
): Promise<BrainDumpTaskDto[]> {
  const safeDump = rawBrainDump.trim().slice(0, 4000);
  const langName = languageDisplayName(lang);
  const prompt = `You are the Thingsbook GTD Brain Dump parser.
Respond strictly in ${langName} (Locale: ${lang}).
Extract individual actionable To-Dos from the user's brain dump text:
"${safeDump}"
For each task, determine:
- title (concise action in ${langName})
- notes (optional context in ${langName})
- scheduleState: one of INBOX, TODAY, THIS_EVENING, UPCOMING, ANYTIME, SOMEDAY
- whenDaysOffset: integer days from today (0 for today/this evening, 1 for tomorrow, etc., or null)
- deadlineDaysOffset: integer days from today if a hard deadline is mentioned, or null
- checklist: 0 to 3 sub-steps in ${langName}
Return ONLY valid JSON matching:
{"tasks": [{"title": "...", "notes": "...", "scheduleState": "TODAY", "whenDaysOffset": 0, "deadlineDaysOffset": null, "checklist": []}]}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (Array.isArray(parsed?.tasks)) {
        const valid = parsed.tasks
          .filter((t: { title?: string }) => typeof t?.title === 'string' && t.title.trim())
          .map((t: Record<string, unknown>) => ({
            title: String(t.title).trim(),
            notes: typeof t.notes === 'string' ? t.notes : '',
            scheduleState: ['INBOX', 'TODAY', 'THIS_EVENING', 'UPCOMING', 'ANYTIME', 'SOMEDAY'].includes(
              String(t.scheduleState || '').toUpperCase()
            )
              ? (String(t.scheduleState).toUpperCase() as ScheduleState)
              : 'TODAY',
            whenDaysOffset:
              typeof t.whenDaysOffset === 'number' && Number.isFinite(t.whenDaysOffset)
                ? Math.max(-365, Math.min(3650, Math.round(t.whenDaysOffset)))
                : null,
            deadlineDaysOffset:
              typeof t.deadlineDaysOffset === 'number' && Number.isFinite(t.deadlineDaysOffset)
                ? Math.max(-365, Math.min(3650, Math.round(t.deadlineDaysOffset)))
                : null,
            checklist: Array.isArray(t.checklist)
              ? t.checklist.map((c: unknown) => String(c).trim()).filter(Boolean)
              : []
          }));
        if (valid.length > 0) return valid;
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return offlineLocalizedBrainDumpParser(safeDump);
}

function normalizeBlueprintEffort(raw: unknown, fallback: EffortLevel = 'MEDIUM'): EffortLevel {
  if (typeof raw === 'string') {
    const up = raw.trim().toUpperCase();
    if (up === 'QUICK_WIN' || up === 'MEDIUM' || up === 'DEEP_FOCUS') {
      return up;
    }
  }
  return fallback;
}

export async function generateProjectBlueprint(
  goalPrompt: string,
  lang: SupportedLang,
  customApiKey = ''
): Promise<ProjectBlueprintResponse> {
  const safeGoal = goalPrompt.trim().slice(0, 1500);
  const langName = languageDisplayName(lang);
  const prompt = `You are the Thingsbook v3.1 Roadmap Architect.
Respond strictly in ${langName} (Locale: ${lang}).
Create a structured GTD + Gantt project roadmap for the goal: "${safeGoal}".
Include:
- projectTitle (clear outcome title in ${langName})
- projectNotes (1–2 inspiring sentences in ${langName})
- 3 logical phases (\`headings\`) spanning a realistic 2 to 4 week schedule starting at dayOffset 0:
  - title (phase title in ${langName})
  - startDayOffset (integer >= 0)
  - endDayOffset (integer >= startDayOffset)
  - tasks: array of plain task titles (mirroring richTasks titles for compatibility)
  - richTasks: 3 to 4 structured items per phase, including at least 1 milestone (\`isMilestone: true\`) per project:
    - key: unique short id like "t1", "t2", ...
    - title: concise action or milestone name in ${langName}
    - notes: brief context in ${langName} (optional)
    - dayOffset: integer >= phase startDayOffset
    - durationMinutes: realistic estimate (30, 60, 120, 240, or 480; 30 for milestones)
    - effortLevel: one of "QUICK_WIN", "MEDIUM", "DEEP_FOCUS"
    - isMilestone: boolean (true for key phase deliverables ◆)
    - dependsOnKeys: array of upstream task \`key\` strings when strictly sequential (e.g. ["t1"])
    - checklist: 0 to 3 sub-steps in ${langName}
Return ONLY valid JSON matching:
{
  "projectTitle": "...",
  "projectNotes": "...",
  "headings": [
    {
      "title": "...",
      "startDayOffset": 0,
      "endDayOffset": 4,
      "tasks": ["..."],
      "richTasks": [
        {
          "key": "t1",
          "title": "...",
          "notes": "",
          "dayOffset": 0,
          "durationMinutes": 120,
          "effortLevel": "DEEP_FOCUS",
          "isMilestone": false,
          "dependsOnKeys": [],
          "checklist": ["...", "..."]
        }
      ]
    }
  ]
}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (parsed?.projectTitle && Array.isArray(parsed?.headings) && parsed.headings.length > 0) {
        const totalHeadings = parsed.headings.length;
        const headings: ProjectBlueprintHeadingDto[] = parsed.headings.map(
          (
            h: {
              title?: string;
              startDayOffset?: unknown;
              endDayOffset?: unknown;
              tasks?: unknown[];
              richTasks?: Array<Record<string, unknown>>;
            },
            hIdx: number
          ) => {
            const rawRich = Array.isArray(h.richTasks) ? h.richTasks : [];
            const cleanRich: RoadmapTaskBlueprintDto[] = rawRich
              .filter((rt) => typeof rt?.title === 'string' && String(rt.title).trim().length > 0)
              .map((rt, tIdx) => {
                const isMs = Boolean(rt.isMilestone);
                const rawDur =
                  typeof rt.durationMinutes === 'number' && Number.isFinite(rt.durationMinutes)
                    ? Math.max(15, Math.min(4800, Math.round(rt.durationMinutes)))
                    : isMs
                      ? 30
                      : 120;
                const dayOff =
                  typeof rt.dayOffset === 'number' && Number.isFinite(rt.dayOffset)
                    ? Math.max(0, Math.min(365, Math.round(rt.dayOffset)))
                    : hIdx * 4 + tIdx;
                return {
                  key:
                    typeof rt.key === 'string' && rt.key.trim().length > 0
                      ? rt.key.trim()
                      : `p${hIdx + 1}_t${tIdx + 1}`,
                  title: String(rt.title).trim(),
                  notes: typeof rt.notes === 'string' ? rt.notes.trim() : '',
                  dayOffset: dayOff,
                  durationMinutes: isMs ? 30 : rawDur,
                  effortLevel: normalizeBlueprintEffort(
                    rt.effortLevel,
                    isMs ? 'QUICK_WIN' : tIdx === 0 ? 'DEEP_FOCUS' : 'MEDIUM'
                  ),
                  isMilestone: isMs,
                  dependsOnKeys: Array.isArray(rt.dependsOnKeys)
                    ? rt.dependsOnKeys.map((k) => String(k).trim()).filter(Boolean)
                    : [],
                  checklist: Array.isArray(rt.checklist)
                    ? rt.checklist.map((c) => String(c).trim()).filter(Boolean)
                    : []
                };
              });

            const plainTasks = Array.isArray(h.tasks)
              ? h.tasks.map((t) => String(t).trim()).filter(Boolean)
              : [];
            const effectiveTasks =
              plainTasks.length > 0 ? plainTasks : cleanRich.map((rt) => rt.title);

            const effectiveRich: RoadmapTaskBlueprintDto[] =
              cleanRich.length > 0
                ? cleanRich
                : effectiveTasks.map((tTitle, tIdx) => {
                    const isMs =
                      hIdx === totalHeadings - 1 && tIdx === effectiveTasks.length - 1;
                    return {
                      key: `p${hIdx + 1}_t${tIdx + 1}`,
                      title: tTitle,
                      notes: '',
                      dayOffset: hIdx * 4 + tIdx,
                      durationMinutes: isMs ? 30 : 120,
                      effortLevel: isMs ? 'QUICK_WIN' : tIdx === 0 ? 'DEEP_FOCUS' : 'MEDIUM',
                      isMilestone: isMs,
                      dependsOnKeys: tIdx > 0 ? [`p${hIdx + 1}_t${tIdx}`] : [],
                      checklist: []
                    };
                  });

            const inferredStart =
              effectiveRich.length > 0
                ? effectiveRich.reduce((m, r) => Math.min(m, r.dayOffset), Infinity)
                : hIdx * 4;
            const inferredEnd =
              effectiveRich.length > 0
                ? effectiveRich.reduce(
                    (m, r) =>
                      Math.max(
                        m,
                        r.dayOffset + Math.max(0, Math.ceil(r.durationMinutes / 480) - 1)
                      ),
                    -Infinity
                  )
                : inferredStart + 3;

            const startDayOffset =
              typeof h.startDayOffset === 'number' && Number.isFinite(h.startDayOffset)
                ? Math.max(0, Math.round(h.startDayOffset))
                : inferredStart;
            const endDayOffset = Math.max(
              startDayOffset,
              typeof h.endDayOffset === 'number' && Number.isFinite(h.endDayOffset)
                ? Math.round(h.endDayOffset)
                : inferredEnd
            );

            return {
              title: String(h.title || '').trim(),
              startDayOffset,
              endDayOffset,
              tasks: effectiveTasks,
              richTasks: effectiveRich
            };
          }
        );

        return {
          projectTitle: String(parsed.projectTitle).trim(),
          projectNotes: typeof parsed.projectNotes === 'string' ? parsed.projectNotes : '',
          headings
        };
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return offlineLocalizedProjectBlueprint(safeGoal, lang);
}

function formatClock(minutesFromMidnight: number): string {
  const clamped = Math.max(0, Math.min(1439, Math.trunc(minutesFromMidnight)));
  const hh = String(Math.floor(clamped / 60)).padStart(2, '0');
  const mm = String(clamped % 60).padStart(2, '0');
  return `${hh}:${mm}`;
}

export function sanitizeProposedTimeblocks(
  proposed: readonly DayTimeblockItemDto[],
  busyIntervalsMinutes: ReadonlyArray<readonly [number, number]>,
  earliestStartMinutes: number
): DayTimeblockItemDto[] {
  if (proposed.length === 0) return [];
  const occupied: Array<[number, number]> = busyIntervalsMinutes
    .filter(([s, e]) => e > s)
    .map(([s, e]) => [s, e]);
  const result: DayTimeblockItemDto[] = [];
  const seenIds = new Set<string>();

  const sorted = [...proposed].sort((a, b) => a.startMinutes - b.startMinutes);
  for (const item of sorted) {
    if (!item.taskId || seenIds.has(item.taskId)) continue;
    seenIds.add(item.taskId);
    const clampedDur = Math.max(15, Math.min(480, Math.round(item.durationMinutes || 30)));
    const dur = Math.floor((clampedDur + 4) / 5) * 5;
    const rawStart = Math.max(0, Math.min(1425, Math.max(earliestStartMinutes, item.startMinutes)));
    let start = Math.floor((rawStart + 7) / 15) * 15;
    let guard = 0;
    while (guard < 96 && start + dur <= 24 * 60) {
      const end = start + dur;
      const conflict = occupied.find(([bs, be]) => start < be && bs < end);
      if (!conflict) break;
      start = Math.ceil(conflict[1] / 15) * 15;
      guard++;
    }
    if (start + dur <= 24 * 60) {
      occupied.push([start, start + dur]);
      occupied.sort((a, b) => a[0] - b[0]);
      result.push({
        taskId: item.taskId,
        startMinutes: start,
        durationMinutes: dur,
        reason: item.reason || ''
      });
    }
  }

  return result.sort((a, b) => a.startMinutes - b.startMinutes);
}

function localizedTimeblockReason(
  cand: DayTimeblockCandidate,
  withinWorkday: boolean,
  lang: SupportedLang
): string {
  const effort = cand.effortLevel ?? null;
  switch (lang) {
    case 'de':
      if (cand.isPinned || cand.hasTodayDeadline) return 'Top-Priorität im freien Fokusfenster';
      if (effort === 'DEEP_FOCUS') return 'Ungestörter Fokusblock';
      if (effort === 'QUICK_WIN') return 'Kompakter Quick-Win zwischen Terminen';
      return withinWorkday ? 'Passendes freies Arbeitszeitfenster' : 'Abendblock nach den Kernzeiten';
    case 'fr':
      if (cand.isPinned || cand.hasTodayDeadline) return 'Priorité placée sur le premier créneau libre';
      if (effort === 'DEEP_FOCUS') return 'Créneau de concentration profonde';
      if (effort === 'QUICK_WIN') return 'Action rapide entre deux rendez-vous';
      return withinWorkday ? 'Créneau libre dans vos horaires' : 'Créneau de fin de journée';
    case 'it':
      if (cand.isPinned || cand.hasTodayDeadline) return 'Priorità alta nel primo spazio libero';
      if (effort === 'DEEP_FOCUS') return 'Blocco di concentrazione profonda';
      if (effort === 'QUICK_WIN') return 'Attività rapida tra gli impegni';
      return withinWorkday ? "Finestra libera nell'orario di lavoro" : 'Blocco serale fuori orario';
    case 'es':
      if (cand.isPinned || cand.hasTodayDeadline) return 'Prioridad clave en el primer hueco libre';
      if (effort === 'DEEP_FOCUS') return 'Bloque de concentración profunda';
      if (effort === 'QUICK_WIN') return 'Tarea rápida entre reuniones';
      return withinWorkday ? 'Hueco libre dentro de tu jornada' : 'Bloque vespertino adicional';
    default:
      if (cand.isPinned || cand.hasTodayDeadline) return 'Priority slot in your earliest free window';
      if (effort === 'DEEP_FOCUS') return 'Uninterrupted deep-focus window';
      if (effort === 'QUICK_WIN') return 'Quick win fitted cleanly into free gap';
      return withinWorkday ? 'Balanced slot within workday hours' : 'Overflow slot after standard workday';
  }
}

export function buildDeterministicDayTimeblockPlan(
  candidates: readonly DayTimeblockCandidate[],
  busyIntervalsMinutes: ReadonlyArray<readonly [number, number]>,
  workdayStartMinutes: number = 540,
  workdayEndMinutes: number = 1020,
  earliestStartMinutes: number = workdayStartMinutes,
  lang: SupportedLang = 'en'
): DayTimeblockPlanResponse {
  if (candidates.length === 0) {
    return { summary: '', blocks: [] };
  }

  const effortRank = (eff: EffortLevel | null | undefined): number => {
    if (eff === 'DEEP_FOCUS') return 0;
    if (eff === 'MEDIUM') return 1;
    if (eff === 'QUICK_WIN') return 2;
    return 1;
  };

  const sortedCandidates = [...candidates].sort((a, b) => {
    if (Boolean(b.isPinned) !== Boolean(a.isPinned)) return a.isPinned ? -1 : 1;
    if (Boolean(b.hasTodayDeadline) !== Boolean(a.hasTodayDeadline)) {
      return a.hasTodayDeadline ? -1 : 1;
    }
    const er = effortRank(a.effortLevel) - effortRank(b.effortLevel);
    if (er !== 0) return er;
    return (b.durationMinutes || 30) - (a.durationMinutes || 30);
  });

  const occupied: Array<[number, number]> = busyIntervalsMinutes
    .filter(([s, e]) => e > s)
    .map(([s, e]) => [s, e] as [number, number])
    .sort((a, b) => a[0] - b[0]);

  const clampedEarliest = Math.max(0, Math.min(23 * 60, Math.trunc(earliestStartMinutes)));
  const cursorFloor = Math.ceil(clampedEarliest / 15) * 15;

  const blocks: DayTimeblockItemDto[] = [];
  for (const cand of sortedCandidates) {
    const rawDur =
      cand.durationMinutes != null && Number.isFinite(cand.durationMinutes) && cand.durationMinutes > 0
        ? Math.trunc(cand.durationMinutes)
        : 30;
    const dur = Math.max(15, Math.min(240, Math.ceil(rawDur / 5) * 5));
    let slotStart = cursorFloor;
    let placed = false;
    let guard = 0;

    while (guard < 96 && slotStart + dur <= 24 * 60) {
      const slotEnd = slotStart + dur;
      const conflict = occupied.find(([bs, be]) => slotStart < be && bs < slotEnd);
      if (!conflict) {
        placed = true;
        break;
      }
      slotStart = Math.ceil(conflict[1] / 15) * 15;
      guard++;
    }

    if (placed) {
      occupied.push([slotStart, slotStart + dur]);
      occupied.sort((a, b) => a[0] - b[0]);
      blocks.push({
        taskId: cand.taskId,
        startMinutes: slotStart,
        durationMinutes: dur,
        reason: localizedTimeblockReason(cand, slotStart < workdayEndMinutes, lang)
      });
    }
  }

  const sortedBlocks = blocks.sort((a, b) => a.startMinutes - b.startMinutes);
  let summary: string;
  switch (lang) {
    case 'de':
      summary = `${sortedBlocks.length} fokussierte Zeitblöcke konfliktfrei um deine Termine eingeplant.`;
      break;
    case 'fr':
      summary = `${sortedBlocks.length} créneaux planifiés sans conflit autour de votre agenda.`;
      break;
    case 'it':
      summary = `${sortedBlocks.length} blocchi orari pianificati senza conflitti attorno ai tuoi impegni.`;
      break;
    case 'es':
      summary = `${sortedBlocks.length} bloques de enfoque programados sin conflictos alrededor de tu agenda.`;
      break;
    default:
      summary = `Scheduled ${sortedBlocks.length} conflict-free focus blocks around your calendar.`;
      break;
  }

  return {
    summary,
    blocks: sortedBlocks
  };
}

export async function generateDailyTimeblockPlan(params: {
  candidates: readonly DayTimeblockCandidate[];
  busyIntervalsMinutes: ReadonlyArray<readonly [number, number]>;
  workdayStartMinutes?: number;
  workdayEndMinutes?: number;
  earliestStartMinutes?: number;
  lang: SupportedLang;
  customApiKey?: string;
}): Promise<DayTimeblockPlanResponse> {
  const {
    candidates,
    busyIntervalsMinutes,
    workdayStartMinutes = 540,
    workdayEndMinutes = 1020,
    lang,
    customApiKey = ''
  } = params;

  if (candidates.length === 0) {
    return { summary: '', blocks: [] };
  }

  const safeWorkStart = Math.max(0, Math.min(22 * 60, Math.trunc(workdayStartMinutes)));
  const safeWorkEnd = Math.max(safeWorkStart + 60, Math.min(24 * 60, Math.trunc(workdayEndMinutes)));
  const effectiveStart = Math.min(
    23 * 60,
    Math.max(safeWorkStart, Math.trunc(params.earliestStartMinutes ?? safeWorkStart))
  );

  const deterministicFallback = buildDeterministicDayTimeblockPlan(
    candidates,
    busyIntervalsMinutes,
    safeWorkStart,
    safeWorkEnd,
    effectiveStart,
    lang
  );

  const langName = languageDisplayName(lang);
  const busyDesc =
    busyIntervalsMinutes.length === 0
      ? 'none'
      : busyIntervalsMinutes.map(([s, e]) => `${formatClock(s)}-${formatClock(e)}`).join(', ');
  const tasksDesc = candidates
    .slice(0, 20)
    .map(
      (c) =>
        `- id="${c.taskId}", title="${c.title.slice(0, 120)}", durationMin=${c.durationMinutes}, effort=${c.effortLevel ?? 'MEDIUM'}, pinned=${Boolean(c.isPinned)}, dueToday=${Boolean(c.hasTodayDeadline)}`
    )
    .join('\n');

  const prompt = `You are the Thingsbook "Plan My Day" Focus Timeblocker.
Respond strictly in ${langName} (Locale: ${lang}).
Workday window: ${formatClock(effectiveStart)} (${effectiveStart}m) to ${formatClock(safeWorkEnd)} (${safeWorkEnd}m).
Busy calendar/locked intervals (DO NOT overlap): ${busyDesc}.
Open Today tasks to schedule:
${tasksDesc}
Rules:
1. Place tasks in 15-minute aligned startMinutes (0..1425) without overlapping busy intervals or each other.
2. Prioritize pinned and dueToday tasks first, DEEP_FOCUS in longer uninterrupted morning/early blocks, and QUICK_WIN in short gaps.
3. Keep durationMinutes realistic (15..240). Include a short 3-8 word \`reason\` in ${langName}.
Return ONLY valid JSON matching:
{"summary": "...", "blocks": [{"taskId": "...", "startMinutes": 540, "durationMinutes": 60, "reason": "..."}]}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (Array.isArray(parsed?.blocks)) {
        const validIds = new Set(candidates.map((c) => c.taskId));
        const rawProposed: DayTimeblockItemDto[] = parsed.blocks
          .filter(
            (b: Record<string, unknown>) =>
              typeof b?.taskId === 'string' && validIds.has(String(b.taskId))
          )
          .map((b: Record<string, unknown>) => ({
            taskId: String(b.taskId),
            startMinutes:
              typeof b.startMinutes === 'number' && Number.isFinite(b.startMinutes)
                ? Math.round(b.startMinutes)
                : effectiveStart,
            durationMinutes:
              typeof b.durationMinutes === 'number' && Number.isFinite(b.durationMinutes)
                ? Math.round(b.durationMinutes)
                : 30,
            reason: typeof b.reason === 'string' ? b.reason.trim() : ''
          }));
        const sanitized = sanitizeProposedTimeblocks(
          rawProposed,
          busyIntervalsMinutes,
          effectiveStart
        );
        if (sanitized.length > 0) {
          return {
            summary:
              typeof parsed.summary === 'string' && parsed.summary.trim()
                ? parsed.summary.trim()
                : deterministicFallback.summary,
            blocks: sanitized
          };
        }
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return deterministicFallback;
}

export async function generateLogbookReflection(
  completedTaskTitles: string[],
  lang: SupportedLang,
  customApiKey = ''
): Promise<LogbookReviewResponse> {
  const langName = languageDisplayName(lang);
  const joined = completedTaskTitles
    .slice(0, 25)
    .map((t) => t.slice(0, 160))
    .join('; ');
  const prompt = `You are the calm Thingsbook Logbook reflection assistant.
Respond strictly in ${langName} (Locale: ${lang}).
Completed tasks in Logbook: [${joined}].
Write a calm, encouraging 2-sentence reflection summary in ${langName} and suggest 3 next high-leverage focus tasks for Today.
Return ONLY valid JSON matching:
{"summaryParagraph": "...", "suggestedFocusTasks": ["...", "...", "..."]}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (typeof parsed?.summaryParagraph === 'string' && parsed.summaryParagraph.trim()) {
        return {
          summaryParagraph: parsed.summaryParagraph.trim(),
          suggestedFocusTasks: Array.isArray(parsed.suggestedFocusTasks)
            ? parsed.suggestedFocusTasks.map((t: unknown) => String(t).trim()).filter(Boolean)
            : []
        };
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return offlineLocalizedLogbookReflection(completedTaskTitles.length, lang);
}

function offlineLocalizedBreakdown(title: string, lang: SupportedLang): string[] {
  const clean = title || 'To-Do';
  switch (lang) {
    case 'de':
      return [
        `Ziel & Rahmenbedingungen für „${clean}“ klären`,
        'Wichtigste Unterlagen und Notizen zusammenstellen',
        'Ersten konkreten Umsetzungsschritt (15 Min. Fokus) starten',
        'Zwischenstand prüfen & offene Punkte abstimmen',
        'Abschluss dokumentieren und im Logbuch abhaken'
      ];
    case 'it':
      return [
        `Definire l'obiettivo principale per «${clean}»`,
        'Raccogliere documenti, link e note necessarie',
        'Completare il primo passo operativo (15 min di focus)',
        'Verificare i dettagli e confermare le scadenze',
        'Concludere e archiviare nel Registro'
      ];
    case 'fr':
      return [
        `Clarifier l'objectif principal pour « ${clean} »`,
        'Rassembler les documents et notes essentiels',
        'Lancer la première étape concrète (15 min de concentration)',
        "Vérifier l'avancement et ajuster les détails",
        'Finaliser et valider dans le Journal'
      ];
    case 'es':
      return [
        `Definir el objetivo clave para «${clean}»`,
        'Reunir notas, enlaces y materiales necesarios',
        'Completar el primer paso práctico (15 min de enfoque)',
        'Revisar avances y confirmar fechas límite',
        'Finalizar y registrar en el Registro'
      ];
    default:
      return [
        `Clarify desired outcome and scope for "${clean}"`,
        'Gather key notes, links, and required materials',
        'Execute the first 15-minute focused action step',
        'Review progress and align any dependencies',
        'Finalize details and mark complete in Logbook'
      ];
  }
}

function offlineLocalizedBrainDumpParser(raw: string): BrainDumpTaskDto[] {
  const segments = raw
    .split(/[,;\n]+|\b(?:und|and|et|e|y|sowie|plus)\b/i)
    .map((s) => s.trim())
    .filter((s) => s.length >= 3);

  return segments.map((seg) => {
    const lower = seg.toLowerCase();
    let scheduleState: ScheduleState = 'TODAY';
    let whenDaysOffset: number | null = 0;
    if (/heute abend|this evening|tonight|stasera|ce soir|esta noche/.test(lower)) {
      scheduleState = 'THIS_EVENING';
      whenDaysOffset = 0;
    } else if (/morgen|tomorrow|domani|demain|mañana|manana/.test(lower)) {
      scheduleState = 'UPCOMING';
      whenDaysOffset = 1;
    } else if (/irgendwann|someday|prima o poi|un jour|algún día|algun dia/.test(lower)) {
      scheduleState = 'SOMEDAY';
      whenDaysOffset = null;
    }
    const deadlineDaysOffset = /frist|bis |deadline|by |entro|avant|para el/.test(lower) ? 3 : null;
    return {
      title: seg.charAt(0).toUpperCase() + seg.slice(1),
      notes: '',
      scheduleState,
      whenDaysOffset,
      deadlineDaysOffset,
      checklist: []
    };
  });
}

function enrichOfflineBlueprintWithRoadmapMetadata(
  base: ProjectBlueprintResponse,
  lang: SupportedLang
): ProjectBlueprintResponse {
  const checklistSampleByLang: Record<SupportedLang, [string, string]> = {
    en: ['Clarify scope & key assumptions', 'Confirm deliverables & timeline'],
    de: ['Umfang & wichtigste Annahmen klären', 'Ergebnisse & Zeitplan abstimmen'],
    fr: ['Clarifier le périmètre et les hypothèses', 'Valider les livrables et le calendrier'],
    it: ['Chiarire ambito e ipotesi chiave', 'Confermare risultati e tempistiche'],
    es: ['Aclarar alcance y supuestos clave', 'Confirmar entregables y cronograma']
  };
  let runningDay = 0;
  let prevMilestoneKey: string | null = null;

  const headings: ProjectBlueprintHeadingDto[] = base.headings.map((h, hIdx) => {
    const phaseStart = runningDay;
    const richTasks: RoadmapTaskBlueprintDto[] = h.tasks.map((title, tIdx) => {
      const key = `p${hIdx + 1}_t${tIdx + 1}`;
      const isLastInPhase = tIdx === h.tasks.length - 1;
      const isMilestone =
        isLastInPhase && (hIdx === 0 || hIdx === base.headings.length - 1);
      const durationMinutes = isMilestone ? 30 : tIdx === 0 ? 240 : 120;
      const effortLevel: EffortLevel = isMilestone
        ? 'QUICK_WIN'
        : tIdx === 0
          ? 'DEEP_FOCUS'
          : 'MEDIUM';
      const dependsOnKeys: string[] = [];
      if (tIdx > 0) {
        dependsOnKeys.push(`p${hIdx + 1}_t${tIdx}`);
      } else if (prevMilestoneKey != null) {
        dependsOnKeys.push(prevMilestoneKey);
      }
      const itemDay = runningDay;
      runningDay += isMilestone ? 1 : 2;
      if (isMilestone) {
        prevMilestoneKey = key;
      }
      return {
        key,
        title,
        notes: '',
        dayOffset: itemDay,
        durationMinutes,
        effortLevel,
        isMilestone,
        dependsOnKeys,
        checklist: hIdx === 0 && tIdx === 0 ? [...checklistSampleByLang[lang]] : []
      };
    });
    const phaseEnd = Math.max(phaseStart, runningDay - 1);
    return {
      title: h.title,
      startDayOffset: phaseStart,
      endDayOffset: phaseEnd,
      tasks: [...h.tasks],
      richTasks
    };
  });

  return {
    projectTitle: base.projectTitle,
    projectNotes: base.projectNotes,
    headings
  };
}

function offlineLocalizedProjectBlueprint(goal: string, lang: SupportedLang): ProjectBlueprintResponse {
  const cleanGoal = goal || 'Thingsbook Project';
  let base: ProjectBlueprintResponse;
  switch (lang) {
    case 'de':
      base = {
        projectTitle: cleanGoal,
        projectNotes: 'Von Thingsbook Gemini strukturierter GTD-Projektplan.',
        headings: [
          {
            title: '1. Konzeption & Vorbereitung',
            tasks: [
              `Ziele und Erfolgskriterien für „${cleanGoal}“ festlegen`,
              'Zeitplan, Budget und Meilensteine skizzieren',
              'Wichtigste Ressourcen und Ansprechpartner sammeln'
            ]
          },
          {
            title: '2. Fokus-Umsetzung',
            tasks: [
              'Kernaufgaben mit höchster Hebelwirkung bearbeiten',
              'Ersten Prototyp oder Entwurf fertigstellen',
              'Zwischenergebnisse prüfen und feinschleifen'
            ]
          },
          {
            title: '3. Abschluss & Review',
            tasks: [
              'Finale Qualitätskontrolle durchführen',
              'Projekt live schalten / übergeben',
              'Learnings im Thingsbook Logbuch festhalten'
            ]
          }
        ]
      };
      break;
    case 'fr':
      base = {
        projectTitle: cleanGoal,
        projectNotes: 'Plan de projet GTD structuré calmement par Thingsbook Gemini.',
        headings: [
          {
            title: '1. Cadrage & Préparation',
            tasks: [
              `Définir des critères de réussite clairs pour « ${cleanGoal} »`,
              'Rassembler les ressources essentielles, le calendrier et les notes',
              'Planifier le premier jalon à fort impact'
            ]
          },
          {
            title: '2. Exécution Concentrée',
            tasks: [
              'Réaliser les livrables principaux lors de blocs de concentration',
              'Évaluer la progression à mi-parcours et affiner le périmètre',
              'Soigner les détails clés et les cas particuliers'
            ]
          },
          {
            title: '3. Revue Finale & Lancement',
            tasks: [
              'Vérifier la liste de contrôle qualité finale',
              'Livrer le résultat et valider son achèvement',
              'Archiver le projet terminé dans le Journal Thingsbook'
            ]
          }
        ]
      };
      break;
    case 'it':
      base = {
        projectTitle: cleanGoal,
        projectNotes: 'Piano di progetto GTD strutturato con calma da Thingsbook Gemini.',
        headings: [
          {
            title: '1. Analisi & Preparazione',
            tasks: [
              `Definire chiari criteri di successo per «${cleanGoal}»`,
              'Raccogliere risorse essenziali, tempistiche e note',
              'Pianificare il primo traguardo ad alto impatto'
            ]
          },
          {
            title: '2. Esecuzione Focalizzata',
            tasks: [
              'Completare le attività principali in blocchi di concentrazione',
              'Verificare i progressi intermedi e rifinire gli obiettivi',
              'Curare i dettagli principali e i casi limite'
            ]
          },
          {
            title: '3. Revisione Finale & Consegna',
            tasks: [
              'Eseguire la lista di controllo qualità finale',
              'Consegnare il risultato e celebrare il completamento',
              'Archiviare il progetto completato nel Registro Thingsbook'
            ]
          }
        ]
      };
      break;
    case 'es':
      base = {
        projectTitle: cleanGoal,
        projectNotes: 'Plan de proyecto GTD estructurado con calma por Thingsbook Gemini.',
        headings: [
          {
            title: '1. Definición & Preparación',
            tasks: [
              `Definir criterios claros de éxito para «${cleanGoal}»`,
              'Reunir recursos esenciales, cronograma y notas',
              'Programar el primer hito de alto impacto'
            ]
          },
          {
            title: '2. Ejecución Enfocada',
            tasks: [
              'Completar los entregables clave en bloques de enfoque',
              'Revisar el avance intermedio y ajustar el alcance',
              'Pulir detalles importantes y casos especiales'
            ]
          },
          {
            title: '3. Revisión Final & Cierre',
            tasks: [
              'Realizar la revisión final de calidad',
              'Entregar el resultado y celebrar la finalización',
              'Archivar el proyecto completado en el Registro de Thingsbook'
            ]
          }
        ]
      };
      break;
    default:
      base = {
        projectTitle: cleanGoal,
        projectNotes: 'Calm GTD project blueprint structured by Thingsbook Gemini.',
        headings: [
          {
            title: '1. Discovery & Preparation',
            tasks: [
              `Define clear success criteria for "${cleanGoal}"`,
              'Gather essential resources, timeline, and notes',
              'Schedule the first high-leverage milestone'
            ]
          },
          {
            title: '2. Deep Execution',
            tasks: [
              'Complete core deliverables during focus blocks',
              'Review mid-project progress and refine scope',
              'Polish key details and edge cases'
            ]
          },
          {
            title: '3. Final Review & Launch',
            tasks: [
              'Run final quality checklist',
              'Deliver outcome and celebrate completion',
              'Archive completed project in Thingsbook Logbook'
            ]
          }
        ]
      };
      break;
  }
  return enrichOfflineBlueprintWithRoadmapMetadata(base, lang);
}

function offlineLocalizedLogbookReflection(count: number, lang: SupportedLang): LogbookReviewResponse {
  switch (lang) {
    case 'de':
      return {
        summaryParagraph: `Du hast ${count} Aufgaben ruhig und fokussiert abgeschlossen. Dein Thingsbook-Workflow zeigt klare Struktur zwischen Projekten und täglichen Prioritäten.`,
        suggestedFocusTasks: [
          'Wichtigste offene Aufgabe in „Heute“ als Erstes abschließen',
          'Eingang (Inbox) in 2 Minuten auf null bringen',
          'Einen entspannten Punkt für „Heute Abend“ einplanen'
        ]
      };
    case 'fr':
      return {
        summaryParagraph: `Vous avez terminé ${count} tâches avec calme et régularité dans votre Journal. Votre rythme entre projets clés et priorités quotidiennes est équilibré.`,
        suggestedFocusTasks: [
          'Terminer votre tâche prioritaire n°1 dans Aujourd’hui',
          'Traiter les éléments restants de la Boîte de réception',
          'Prévoir un point serein pour Ce soir'
        ]
      };
    case 'it':
      return {
        summaryParagraph: `Hai completato con calma ${count} attività nel tuo Registro. Il tuo ritmo tra progetti principali e blocchi di concentrazione giornalieri è costante ed equilibrato.`,
        suggestedFocusTasks: [
          'Completare la tua attività prioritaria n. 1 in Oggi',
          'Portare a zero gli elementi rimasti in Entrata',
          'Pianificare un momento tranquillo per Stasera'
        ]
      };
    case 'es':
      return {
        summaryParagraph: `Has completado con calma ${count} tareas en tu Registro. Tu ritmo entre proyectos principales y bloques de enfoque diario es constante y equilibrado.`,
        suggestedFocusTasks: [
          'Completar tu tarea prioritaria n.º 1 en Hoy',
          'Procesar los elementos pendientes de Entrada hasta dejarla en cero',
          'Reservar una tarea tranquila para Esta noche'
        ]
      };
    default:
      return {
        summaryParagraph: `You have calmly completed ${count} To-Dos in your Logbook. Your momentum across core projects and daily focus blocks is steady and well-balanced.`,
        suggestedFocusTasks: [
          'Complete your #1 high-leverage To-Do in Today',
          'Process any remaining Inbox items to zero',
          'Set aside one calm reflection item for This Evening'
        ]
      };
  }
}
