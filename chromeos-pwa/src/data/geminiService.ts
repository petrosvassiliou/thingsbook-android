import type { ScheduleState, SupportedLang } from '../domain/types';
import { getFirebaseAppInstance } from './firebaseClient';

export interface BrainDumpTaskDto {
  title: string;
  notes: string;
  scheduleState: ScheduleState;
  whenDaysOffset: number | null;
  deadlineDaysOffset: number | null;
  checklist: string[];
}

export interface ProjectBlueprintResponse {
  projectTitle: string;
  projectNotes: string;
  headings: Array<{ title: string; tasks: string[] }>;
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

export async function generateProjectBlueprint(
  goalPrompt: string,
  lang: SupportedLang,
  customApiKey = ''
): Promise<ProjectBlueprintResponse> {
  const safeGoal = goalPrompt.trim().slice(0, 1500);
  const langName = languageDisplayName(lang);
  const prompt = `You are the Thingsbook Project Architect.
Respond strictly in ${langName} (Locale: ${lang}).
Create a structured Things 3 GTD project for the goal: "${safeGoal}".
Include a clear projectTitle, brief inspiring projectNotes, and 3 logical headings, each with 3 concrete tasks.
Return ONLY valid JSON matching:
{
  "projectTitle": "...",
  "projectNotes": "...",
  "headings": [
    {"title": "...", "tasks": ["...", "...", "..."]}
  ]
}`;

  try {
    const raw = await callGeminiJson(prompt, customApiKey);
    if (raw) {
      const parsed = JSON.parse(extractJsonObject(raw));
      if (parsed?.projectTitle && Array.isArray(parsed?.headings) && parsed.headings.length > 0) {
        return {
          projectTitle: String(parsed.projectTitle).trim(),
          projectNotes: typeof parsed.projectNotes === 'string' ? parsed.projectNotes : '',
          headings: parsed.headings.map((h: { title?: string; tasks?: unknown[] }) => ({
            title: String(h.title || '').trim(),
            tasks: Array.isArray(h.tasks)
              ? h.tasks.map((t) => String(t).trim()).filter(Boolean)
              : []
          }))
        };
      }
    }
  } catch (err) {
    if (customApiKey.trim()) throw err;
  }

  return offlineLocalizedProjectBlueprint(safeGoal, lang);
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

function offlineLocalizedProjectBlueprint(goal: string, lang: SupportedLang): ProjectBlueprintResponse {
  const cleanGoal = goal || 'Thingsbook Project';
  if (lang === 'de') {
    return {
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
  }
  return {
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
}

function offlineLocalizedLogbookReflection(count: number, lang: SupportedLang): LogbookReviewResponse {
  if (lang === 'de') {
    return {
      summaryParagraph: `Du hast ${count} Aufgaben ruhig und fokussiert abgeschlossen. Dein Thingsbook-Workflow zeigt klare Struktur zwischen Projekten und täglichen Prioritäten.`,
      suggestedFocusTasks: [
        'Wichtigste offene Aufgabe in „Heute“ als Erstes abschließen',
        'Eingang (Inbox) in 2 Minuten auf null bringen',
        'Einen entspannten Punkt für „Heute Abend“ einplanen'
      ]
    };
  }
  return {
    summaryParagraph: `You have calmly completed ${count} To-Dos in your Logbook. Your momentum across core projects and daily focus blocks is steady and well-balanced.`,
    suggestedFocusTasks: [
      'Complete your #1 high-leverage To-Do in Today',
      'Process any remaining Inbox items to zero',
      'Set aside one calm reflection item for This Evening'
    ]
  };
}
