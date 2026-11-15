import {
  startOfDayEpoch,
  type CalendarEventItem,
  type ChecklistItemRecord,
  type HeadingRecord,
  type ProjectRecord,
  type ProjectTemplateBlueprint,
  type ProjectTemplateHeadingBlueprint,
  type ProjectTemplateTaskBlueprint,
  type ScheduleState,
  type SupportedLang,
  type TaskRecord
} from './types.ts';

export const STALLED_PROJECT_THRESHOLD_DAYS = 14;
export const STALLED_PROJECT_THRESHOLD_MS = STALLED_PROJECT_THRESHOLD_DAYS * 86_400_000;

export const DEFAULT_TIMEBLOCK_DURATION_MINUTES = 30;
export const TIMEBLOCK_DURATION_PRESETS = [15, 30, 45, 60, 90, 120] as const;
export const TIMEBLOCK_QUICK_START_MINUTES = [
  480, // 08:00
  540, // 09:00
  600, // 10:00
  660, // 11:00
  720, // 12:00
  780, // 13:00
  840, // 14:00
  900, // 15:00
  960, // 16:00
  1020, // 17:00
  1080 // 18:00
] as const;

export interface StalledProjectEvaluation {
  isStalled: boolean;
  reason: 'no_next_action' | 'inactive_14d' | null;
  openTasksCount: number;
  lastActivityAt: number;
  daysSinceActivity: number;
}

/**
 * Evaluates whether an open project is "Stalled" according to GTD Weekly Review rules:
 * - Only non-deleted, ACTIVE projects that are not parked in SOMEDAY can be stalled.
 * - Stalled if it has 0 open next actions OR hasn't been updated/reviewed in 14+ days.
 */
export function evaluateProjectStalledStatus(
  project: ProjectRecord,
  tasks: readonly TaskRecord[],
  nowMillis: number = Date.now()
): StalledProjectEvaluation {
  const projectTasks = tasks.filter((t) => !t.isDeleted && t.projectId === project.id);
  const openTasks = projectTasks.filter((t) => t.status === 'OPEN');

  const reviewAnchor =
    project.lastReviewedAt != null && project.lastReviewedAt > 0
      ? project.lastReviewedAt
      : Math.max(project.updatedAt || 0, project.createdAt || 0);

  const elapsedMs = Math.max(0, nowMillis - reviewAnchor);
  const daysSinceActivity = Math.floor(elapsedMs / 86_400_000);

  if (
    project.isDeleted ||
    project.status !== 'ACTIVE' ||
    project.scheduleState === 'SOMEDAY'
  ) {
    return {
      isStalled: false,
      reason: null,
      openTasksCount: openTasks.length,
      lastActivityAt: reviewAnchor,
      daysSinceActivity
    };
  }

  if (openTasks.length === 0) {
    return {
      isStalled: true,
      reason: 'no_next_action',
      openTasksCount: 0,
      lastActivityAt: reviewAnchor,
      daysSinceActivity
    };
  }

  if (reviewAnchor <= 0 || elapsedMs >= STALLED_PROJECT_THRESHOLD_MS) {
    return {
      isStalled: true,
      reason: 'inactive_14d',
      openTasksCount: openTasks.length,
      lastActivityAt: reviewAnchor,
      daysSinceActivity
    };
  }

  return {
    isStalled: false,
    reason: null,
    openTasksCount: openTasks.length,
    lastActivityAt: reviewAnchor,
    daysSinceActivity
  };
}

export function detectStalledProjects(
  projects: readonly ProjectRecord[],
  tasks: readonly TaskRecord[],
  nowMillis: number = Date.now()
): Array<{ project: ProjectRecord; status: StalledProjectEvaluation }> {
  const result: Array<{ project: ProjectRecord; status: StalledProjectEvaluation }> = [];
  for (const project of projects) {
    const status = evaluateProjectStalledStatus(project, tasks, nowMillis);
    if (status.isStalled) {
      result.push({ project, status });
    }
  }
  return result.sort((a, b) => a.project.sortOrder - b.project.sortOrder);
}

/**
 * Formats minutes from midnight (e.g. 540 -> "09:00") with 24-hour wraparound.
 */
export function formatMinutesAsClock(totalMinutes: number): string {
  const normalized = ((Math.trunc(totalMinutes) % 1440) + 1440) % 1440;
  const hh = Math.floor(normalized / 60);
  const mm = normalized % 60;
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`;
}

/**
 * Parses "HH:mm" into minutes from midnight (0..1439), or null if invalid.
 */
export function parseClockToMinutes(clock: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(clock || '').trim());
  if (!m) return null;
  const hh = Number.parseInt(m[1], 10);
  const mm = Number.parseInt(m[2], 10);
  if (!Number.isFinite(hh) || !Number.isFinite(mm) || hh < 0 || hh > 23 || mm < 0 || mm > 59) {
    return null;
  }
  return hh * 60 + mm;
}

/**
 * Formats a task timeblock pill string, e.g. "09:00–09:45 · 45m".
 */
export function formatTimeblockRange(
  scheduledStartMinutes: number,
  durationMinutes?: number | null
): string {
  const dur =
    durationMinutes != null && Number.isFinite(durationMinutes) && durationMinutes > 0
      ? Math.max(5, Math.min(720, Math.trunc(durationMinutes)))
      : DEFAULT_TIMEBLOCK_DURATION_MINUTES;
  const startClock = formatMinutesAsClock(scheduledStartMinutes);
  const endClock = formatMinutesAsClock(scheduledStartMinutes + dur);
  return `${startClock}–${endClock} · ${dur}m`;
}

export interface DailyTimelineItem {
  id: string;
  kind: 'calendar' | 'task';
  title: string;
  startMinutes: number;
  endMinutes: number;
  durationMinutes: number;
  rangeLabel: string;
  hasConflict: boolean;
  conflictTitles: string[];
  task?: TaskRecord;
  calendarEvent?: CalendarEventItem;
}

export interface DailyTimelineModel {
  allDayEvents: CalendarEventItem[];
  timedItems: DailyTimelineItem[];
  unscheduledTasks: TaskRecord[];
}

/**
 * Builds a chronological daily timeline interleaving Calendar Events and Timeblocked Tasks,
 * detecting schedule overlap conflicts automatically.
 */
export function buildDailyTimeline(
  todayTasks: readonly TaskRecord[],
  calendarEvents: readonly CalendarEventItem[],
  dayStartMillis: number = startOfDayEpoch()
): DailyTimelineModel {
  const dayEndMillis = dayStartMillis + 86_400_000;
  const allDayEvents: CalendarEventItem[] = [];
  const rawTimed: DailyTimelineItem[] = [];
  const unscheduledTasks: TaskRecord[] = [];

  for (const ev of calendarEvents) {
    if (ev.isAllDay) {
      allDayEvents.push(ev);
      continue;
    }
    const clampedStart = Math.max(dayStartMillis, ev.startMillis);
    const clampedEnd = Math.min(dayEndMillis, Math.max(ev.startMillis + 60_000, ev.endMillis));
    if (clampedEnd <= dayStartMillis || clampedStart >= dayEndMillis) continue;
    const startD = new Date(clampedStart);
    const startMin = startD.getHours() * 60 + startD.getMinutes();
    const durMin = Math.max(5, Math.round((clampedEnd - clampedStart) / 60_000));
    const endMin = Math.min(1440, startMin + durMin);
    rawTimed.push({
      id: `cal:${ev.calendarId}:${ev.eventId}`,
      kind: 'calendar',
      title: ev.title,
      startMinutes: startMin,
      endMinutes: endMin,
      durationMinutes: durMin,
      rangeLabel: `${formatMinutesAsClock(startMin)}–${formatMinutesAsClock(endMin)} · ${durMin}m`,
      hasConflict: false,
      conflictTitles: [],
      calendarEvent: ev
    });
  }

  for (const task of todayTasks) {
    if (task.isDeleted || task.status !== 'OPEN') continue;
    if (task.scheduledStartMinutes == null || !Number.isFinite(task.scheduledStartMinutes)) {
      unscheduledTasks.push(task);
      continue;
    }
    const startMin = Math.max(0, Math.min(1439, Math.trunc(task.scheduledStartMinutes)));
    const durMin =
      task.durationMinutes != null && Number.isFinite(task.durationMinutes) && task.durationMinutes > 0
        ? Math.max(5, Math.min(720, Math.trunc(task.durationMinutes)))
        : DEFAULT_TIMEBLOCK_DURATION_MINUTES;
    const endMin = Math.min(1440, startMin + durMin);
    rawTimed.push({
      id: `task:${task.id}`,
      kind: 'task',
      title: task.title,
      startMinutes: startMin,
      endMinutes: endMin,
      durationMinutes: durMin,
      rangeLabel: formatTimeblockRange(startMin, durMin),
      hasConflict: false,
      conflictTitles: [],
      task
    });
  }

  rawTimed.sort((a, b) => {
    if (a.startMinutes !== b.startMinutes) return a.startMinutes - b.startMinutes;
    if (a.endMinutes !== b.endMinutes) return a.endMinutes - b.endMinutes;
    if (a.kind !== b.kind) return a.kind === 'calendar' ? -1 : 1;
    return a.title.localeCompare(b.title);
  });

  for (let i = 0; i < rawTimed.length; i++) {
    for (let j = i + 1; j < rawTimed.length; j++) {
      const a = rawTimed[i];
      const b = rawTimed[j];
      if (b.startMinutes >= a.endMinutes) break;
      if (a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes) {
        a.hasConflict = true;
        b.hasConflict = true;
        if (!a.conflictTitles.includes(b.title)) a.conflictTitles.push(b.title);
        if (!b.conflictTitles.includes(a.title)) b.conflictTitles.push(a.title);
      }
    }
  }

  unscheduledTasks.sort((a, b) => a.sortOrder - b.sortOrder);

  return {
    allDayEvents,
    timedItems: rawTimed,
    unscheduledTasks
  };
}

export interface StarterProjectTemplateDef {
  id: string;
  icon: string;
  titleKey: 'starterTplTripTitle' | 'starterTplLaunchTitle' | 'starterTplClientTitle' | 'starterTplSprintTitle';
  descKey: 'starterTplTripDesc' | 'starterTplLaunchDesc' | 'starterTplClientDesc' | 'starterTplSprintDesc';
  defaultTitle: string;
  defaultNotes: string;
  blueprint: ProjectTemplateBlueprint;
}

const STARTER_GTD_TEMPLATES_BY_LANG: Record<
  SupportedLang,
  readonly StarterProjectTemplateDef[]
> = {
  en: [
    {
      id: 'starter-trip-packing',
      icon: '🧳',
      titleKey: 'starterTplTripTitle',
      descKey: 'starterTplTripDesc',
      defaultTitle: 'Trip & Packing',
      defaultNotes:
        'End-to-end travel bookings, documents, packing checklist, and home departure prep.',
      blueprint: {
        version: 1,
        tasks: [
          {
            title: 'Confirm flight, train, and hotel reservations',
            whenDaysOffset: 0,
            durationMinutes: 30,
            checklistItems: ['Save offline boarding passes', 'Share itinerary with family']
          }
        ],
        headings: [
          {
            title: 'Documents & Essentials',
            tasks: [
              {
                title: 'Verify passport / ID validity & travel insurance',
                whenDaysOffset: 0,
                durationMinutes: 20,
                checklistItems: [
                  'Passport / ID card',
                  'Payment cards & local currency',
                  'Health / insurance card'
                ]
              }
            ]
          },
          {
            title: 'Packing List',
            tasks: [
              {
                title: 'Pack clothing & toiletries',
                whenDaysOffset: 1,
                durationMinutes: 45,
                checklistItems: [
                  'Weather-appropriate outfits',
                  'Shoes & jacket',
                  'Toiletries & medications'
                ]
              },
              {
                title: 'Pack electronics & chargers',
                whenDaysOffset: 1,
                durationMinutes: 15,
                checklistItems: [
                  'Phone + Laptop chargers',
                  'Power bank',
                  'Universal travel adapter',
                  'Headphones'
                ]
              }
            ]
          },
          {
            title: 'Before Leaving Home',
            tasks: [
              {
                title: 'Home departure walkthrough',
                whenDaysOffset: 2,
                durationMinutes: 20,
                checklistItems: [
                  'Empty trash & perishable food',
                  'Water plants',
                  'Lock windows & doors'
                ]
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-product-launch',
      icon: '🚀',
      titleKey: 'starterTplLaunchTitle',
      descKey: 'starterTplLaunchDesc',
      defaultTitle: 'Product Launch',
      defaultNotes:
        'Structured GTD launch playbook across positioning, QA sign-off, launch day, and follow-up.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Strategy & Positioning',
            tasks: [
              {
                title: 'Finalize launch messaging & release notes',
                whenDaysOffset: 0,
                durationMinutes: 45,
                checklistItems: [
                  'Headline value proposition',
                  'Changelog highlights',
                  'Screenshots & demo assets'
                ]
              }
            ]
          },
          {
            title: 'Build & QA Readiness',
            tasks: [
              {
                title: 'Complete pre-release QA & migration smoke tests',
                whenDaysOffset: 1,
                durationMinutes: 60,
                checklistItems: [
                  'Upgrade path verified',
                  'Cloud sync verified',
                  'Zero lint/build warnings'
                ]
              }
            ]
          },
          {
            title: 'Launch Day Execution',
            tasks: [
              {
                title: 'Publish release & announce across channels',
                whenDaysOffset: 2,
                scheduledStartMinutes: 540,
                durationMinutes: 45,
                checklistItems: [
                  'Tag release build',
                  'Update landing page / docs',
                  'Post announcement'
                ]
              }
            ]
          },
          {
            title: 'Post-Launch Follow-Up',
            tasks: [
              {
                title: 'Monitor telemetry & triage early user feedback',
                whenDaysOffset: 3,
                durationMinutes: 30
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-client-onboarding',
      icon: '🤝',
      titleKey: 'starterTplClientTitle',
      descKey: 'starterTplClientDesc',
      defaultTitle: 'Client Onboarding',
      defaultNotes:
        'Repeatable client kickoff workflow from signed agreement to first milestone delivery.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Kickoff & Agreements',
            tasks: [
              {
                title: 'Countersign agreement & send welcome packet',
                whenDaysOffset: 0,
                durationMinutes: 30,
                checklistItems: [
                  'Signed SOW / MSA archived',
                  'Billing profile configured',
                  'Welcome email sent'
                ]
              },
              {
                title: 'Schedule kickoff workshop',
                whenDaysOffset: 0,
                durationMinutes: 15
              }
            ]
          },
          {
            title: 'Access & Discovery',
            tasks: [
              {
                title: 'Provision shared workspace & repository access',
                whenDaysOffset: 1,
                durationMinutes: 30,
                checklistItems: [
                  'Shared folder & docs',
                  'Communication channel',
                  'Technical credentials'
                ]
              }
            ]
          },
          {
            title: 'First Milestone',
            tasks: [
              {
                title: 'Deliver 30-day roadmap & success metrics',
                whenDaysOffset: 3,
                deadlineDaysOffset: 7,
                durationMinutes: 60
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-weekly-sprint',
      icon: '⚡',
      titleKey: 'starterTplSprintTitle',
      descKey: 'starterTplSprintDesc',
      defaultTitle: 'Weekly Sprint',
      defaultNotes:
        'Focused 5-day execution sprint with clear top outcomes, deep work blocks, and Friday retro.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Sprint Planning',
            tasks: [
              {
                title: 'Define top 3 outcomes for the week',
                whenDaysOffset: 0,
                scheduledStartMinutes: 540,
                durationMinutes: 30,
                checklistItems: ['Outcome #1', 'Outcome #2', 'Outcome #3']
              }
            ]
          },
          {
            title: 'Deep Work Execution',
            tasks: [
              {
                title: 'Ship primary sprint deliverable',
                whenDaysOffset: 1,
                durationMinutes: 90
              },
              {
                title: 'Clear mid-week blockers & stakeholder check-in',
                whenDaysOffset: 2,
                durationMinutes: 30
              }
            ]
          },
          {
            title: 'Review & Retrospective',
            tasks: [
              {
                title: 'Demo shipped work & capture retro notes',
                whenDaysOffset: 4,
                durationMinutes: 30,
                checklistItems: ['What went well', 'What to improve next sprint']
              }
            ]
          }
        ]
      }
    }
  ],
  de: [
    {
      id: 'starter-trip-packing',
      icon: '🧳',
      titleKey: 'starterTplTripTitle',
      descKey: 'starterTplTripDesc',
      defaultTitle: 'Reise & Packliste',
      defaultNotes:
        'Komplette Reisevorbereitung mit Buchungen, Dokumenten, Packliste und Check vor der Abreise.',
      blueprint: {
        version: 1,
        tasks: [
          {
            title: 'Flug-, Bahn- und Hotelbuchungen bestätigen',
            whenDaysOffset: 0,
            durationMinutes: 30,
            checklistItems: ['Bordkarten offline speichern', 'Reiseplan mit der Familie teilen']
          }
        ],
        headings: [
          {
            title: 'Dokumente & Wichtiges',
            tasks: [
              {
                title: 'Reisepass / Ausweis & Reiseversicherung prüfen',
                whenDaysOffset: 0,
                durationMinutes: 20,
                checklistItems: [
                  'Reisepass / Personalausweis',
                  'Zahlungskarten & Landeswährung',
                  'Kranken- / Versicherungskarte'
                ]
              }
            ]
          },
          {
            title: 'Packliste',
            tasks: [
              {
                title: 'Kleidung & Kulturbeutel einpacken',
                whenDaysOffset: 1,
                durationMinutes: 45,
                checklistItems: [
                  'Wettergerechte Kleidung',
                  'Schuhe & Jacke',
                  'Pflegeartikel & Reiseapotheke'
                ]
              },
              {
                title: 'Elektronik & Ladegeräte einpacken',
                whenDaysOffset: 1,
                durationMinutes: 15,
                checklistItems: [
                  'Smartphone- & Laptop-Ladegeräte',
                  'Powerbank',
                  'Reiseadapter',
                  'Kopfhörer'
                ]
              }
            ]
          },
          {
            title: 'Vor der Abreise',
            tasks: [
              {
                title: 'Rundgang durch die Wohnung vor Abreise',
                whenDaysOffset: 2,
                durationMinutes: 20,
                checklistItems: [
                  'Müll & verderbliche Lebensmittel entsorgen',
                  'Pflanzen gießen',
                  'Fenster & Türen verschließen'
                ]
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-product-launch',
      icon: '🚀',
      titleKey: 'starterTplLaunchTitle',
      descKey: 'starterTplLaunchDesc',
      defaultTitle: 'Produkt-Launch',
      defaultNotes:
        'Strukturierter GTD-Launch-Plan von Positionierung und QA-Freigabe bis zum Launch-Tag und Follow-up.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Strategie & Positionierung',
            tasks: [
              {
                title: 'Launch-Botschaft & Release Notes finalisieren',
                whenDaysOffset: 0,
                durationMinutes: 45,
                checklistItems: [
                  'Kernnutzen & Wertversprechen',
                  'Changelog-Highlights',
                  'Screenshots & Demo-Material'
                ]
              }
            ]
          },
          {
            title: 'Build- & QA-Freigabe',
            tasks: [
              {
                title: 'Pre-Release-QA & Migrations-Tests abschließen',
                whenDaysOffset: 1,
                durationMinutes: 60,
                checklistItems: [
                  'Upgrade-Pfad geprüft',
                  'Cloud-Synchronisation geprüft',
                  'Null Lint-/Build-Warnungen'
                ]
              }
            ]
          },
          {
            title: 'Launch-Tag',
            tasks: [
              {
                title: 'Release veröffentlichen & auf allen Kanälen ankündigen',
                whenDaysOffset: 2,
                scheduledStartMinutes: 540,
                durationMinutes: 45,
                checklistItems: [
                  'Release-Build taggen',
                  'Landingpage / Dokumentation aktualisieren',
                  'Ankündigung veröffentlichen'
                ]
              }
            ]
          },
          {
            title: 'Nachbereitung',
            tasks: [
              {
                title: 'Telemetrie überwachen & erstes Nutzer-Feedback sichten',
                whenDaysOffset: 3,
                durationMinutes: 30
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-client-onboarding',
      icon: '🤝',
      titleKey: 'starterTplClientTitle',
      descKey: 'starterTplClientDesc',
      defaultTitle: 'Kunden-Onboarding',
      defaultNotes:
        'Wiederholbarer Onboarding-Workflow vom unterzeichneten Vertrag bis zum ersten Meilenstein.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Kickoff & Vereinbarungen',
            tasks: [
              {
                title: 'Vertrag gegenzeichnen & Willkommenspaket senden',
                whenDaysOffset: 0,
                durationMinutes: 30,
                checklistItems: [
                  'Unterzeichneten Vertrag archivieren',
                  'Abrechnungsprofil einrichten',
                  'Willkommens-E-Mail versenden'
                ]
              },
              {
                title: 'Kickoff-Workshop terminieren',
                whenDaysOffset: 0,
                durationMinutes: 15
              }
            ]
          },
          {
            title: 'Zugänge & Discovery',
            tasks: [
              {
                title: 'Gemeinsamen Workspace & Repository-Zugriff einrichten',
                whenDaysOffset: 1,
                durationMinutes: 30,
                checklistItems: [
                  'Geteilte Ordner & Dokumente',
                  'Kommunikationskanal',
                  'Technische Zugangsdaten'
                ]
              }
            ]
          },
          {
            title: 'Erster Meilenstein',
            tasks: [
              {
                title: '30-Tage-Roadmap & Erfolgskennzahlen liefern',
                whenDaysOffset: 3,
                deadlineDaysOffset: 7,
                durationMinutes: 60
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-weekly-sprint',
      icon: '⚡',
      titleKey: 'starterTplSprintTitle',
      descKey: 'starterTplSprintDesc',
      defaultTitle: 'Wochen-Sprint',
      defaultNotes:
        'Fokussierter 5-Tage-Sprint mit klaren Wochenzielen, Deep-Work-Blöcken und Freitags-Retrospektive.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Sprint-Planung',
            tasks: [
              {
                title: 'Top-3-Ergebnisse für die Woche festlegen',
                whenDaysOffset: 0,
                scheduledStartMinutes: 540,
                durationMinutes: 30,
                checklistItems: ['Ergebnis #1', 'Ergebnis #2', 'Ergebnis #3']
              }
            ]
          },
          {
            title: 'Deep-Work-Umsetzung',
            tasks: [
              {
                title: 'Wichtigstes Sprint-Ergebnis fertigstellen',
                whenDaysOffset: 1,
                durationMinutes: 90
              },
              {
                title: 'Blocker zur Wochenmitte lösen & Stakeholder-Update',
                whenDaysOffset: 2,
                durationMinutes: 30
              }
            ]
          },
          {
            title: 'Review & Retrospektive',
            tasks: [
              {
                title: 'Ergebnisse präsentieren & Retro-Notizen festhalten',
                whenDaysOffset: 4,
                durationMinutes: 30,
                checklistItems: ['Was gut lief', 'Was im nächsten Sprint verbessert wird']
              }
            ]
          }
        ]
      }
    }
  ],
  fr: [
    {
      id: 'starter-trip-packing',
      icon: '🧳',
      titleKey: 'starterTplTripTitle',
      descKey: 'starterTplTripDesc',
      defaultTitle: 'Voyage & Bagages',
      defaultNotes:
        'Préparation complète du voyage : réservations, documents, liste de bagages et départ de la maison.',
      blueprint: {
        version: 1,
        tasks: [
          {
            title: "Confirmer les réservations de vol, train et d'hôtel",
            whenDaysOffset: 0,
            durationMinutes: 30,
            checklistItems: [
              "Enregistrer les cartes d'embarquement hors ligne",
              "Partager l'itinéraire avec la famille"
            ]
          }
        ],
        headings: [
          {
            title: 'Documents & Essentiels',
            tasks: [
              {
                title: "Vérifier la validité du passeport / pièce d'identité et l'assurance voyage",
                whenDaysOffset: 0,
                durationMinutes: 20,
                checklistItems: [
                  "Passeport / Carte d'identité",
                  'Cartes bancaires & devise locale',
                  "Carte vitale / d'assurance"
                ]
              }
            ]
          },
          {
            title: 'Liste de bagages',
            tasks: [
              {
                title: 'Préparer les vêtements & la trousse de toilette',
                whenDaysOffset: 1,
                durationMinutes: 45,
                checklistItems: [
                  'Tenues adaptées à la météo',
                  'Chaussures & veste',
                  'Articles de toilette & médicaments'
                ]
              },
              {
                title: 'Préparer les appareils électroniques & chargeurs',
                whenDaysOffset: 1,
                durationMinutes: 15,
                checklistItems: [
                  'Chargeurs téléphone + ordinateur',
                  'Batterie externe',
                  'Adaptateur universel',
                  'Écouteurs / casque'
                ]
              }
            ]
          },
          {
            title: 'Avant de quitter la maison',
            tasks: [
              {
                title: 'Vérification du domicile avant le départ',
                whenDaysOffset: 2,
                durationMinutes: 20,
                checklistItems: [
                  'Vider les poubelles & aliments périssables',
                  'Arroser les plantes',
                  'Verrouiller fenêtres & portes'
                ]
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-product-launch',
      icon: '🚀',
      titleKey: 'starterTplLaunchTitle',
      descKey: 'starterTplLaunchDesc',
      defaultTitle: 'Lancement de produit',
      defaultNotes:
        'Plan de lancement GTD structuré : positionnement, validation QA, jour J et suivi post-lancement.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Stratégie & Positionnement',
            tasks: [
              {
                title: 'Finaliser le message de lancement & les notes de version',
                whenDaysOffset: 0,
                durationMinutes: 45,
                checklistItems: [
                  'Proposition de valeur principale',
                  'Points clés du journal des modifications',
                  "Captures d'écran & démos"
                ]
              }
            ]
          },
          {
            title: 'Préparation Build & QA',
            tasks: [
              {
                title: 'Terminer la recette QA & les tests de migration',
                whenDaysOffset: 1,
                durationMinutes: 60,
                checklistItems: [
                  'Parcours de mise à jour vérifié',
                  'Synchronisation cloud vérifiée',
                  'Zéro avertissement lint/build'
                ]
              }
            ]
          },
          {
            title: 'Exécution du jour J',
            tasks: [
              {
                title: 'Publier la version & annoncer sur tous les canaux',
                whenDaysOffset: 2,
                scheduledStartMinutes: 540,
                durationMinutes: 45,
                checklistItems: [
                  'Taguer la version publiée',
                  "Mettre à jour la page d'accueil / documentation",
                  "Publier l'annonce"
                ]
              }
            ]
          },
          {
            title: 'Suivi post-lancement',
            tasks: [
              {
                title: 'Surveiller la télémétrie & trier les premiers retours utilisateurs',
                whenDaysOffset: 3,
                durationMinutes: 30
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-client-onboarding',
      icon: '🤝',
      titleKey: 'starterTplClientTitle',
      descKey: 'starterTplClientDesc',
      defaultTitle: 'Intégration client',
      defaultNotes:
        "Flux d'intégration client reproductible, du contrat signé à la livraison du premier jalon.",
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Lancement & Accords',
            tasks: [
              {
                title: "Contresigner l'accord & envoyer le dossier de bienvenue",
                whenDaysOffset: 0,
                durationMinutes: 30,
                checklistItems: [
                  'Contrat signé archivé',
                  'Profil de facturation configuré',
                  'E-mail de bienvenue envoyé'
                ]
              },
              {
                title: "Planifier l'atelier de lancement",
                whenDaysOffset: 0,
                durationMinutes: 15
              }
            ]
          },
          {
            title: 'Accès & Découverte',
            tasks: [
              {
                title: "Configurer l'espace partagé & les accès techniques",
                whenDaysOffset: 1,
                durationMinutes: 30,
                checklistItems: [
                  'Dossier & documents partagés',
                  'Canal de communication',
                  'Identifiants techniques'
                ]
              }
            ]
          },
          {
            title: 'Premier jalon',
            tasks: [
              {
                title: 'Livrer la feuille de route à 30 jours & les indicateurs de succès',
                whenDaysOffset: 3,
                deadlineDaysOffset: 7,
                durationMinutes: 60
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-weekly-sprint',
      icon: '⚡',
      titleKey: 'starterTplSprintTitle',
      descKey: 'starterTplSprintDesc',
      defaultTitle: 'Sprint hebdomadaire',
      defaultNotes:
        "Sprint d'exécution ciblé de 5 jours avec objectifs prioritaires, travail profond et rétrospective du vendredi.",
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Planification du sprint',
            tasks: [
              {
                title: 'Définir les 3 résultats prioritaires de la semaine',
                whenDaysOffset: 0,
                scheduledStartMinutes: 540,
                durationMinutes: 30,
                checklistItems: ['Résultat n°1', 'Résultat n°2', 'Résultat n°3']
              }
            ]
          },
          {
            title: 'Travail profond',
            tasks: [
              {
                title: 'Livrer le livrable principal du sprint',
                whenDaysOffset: 1,
                durationMinutes: 90
              },
              {
                title: 'Lever les blocages de mi-semaine & point parties prenantes',
                whenDaysOffset: 2,
                durationMinutes: 30
              }
            ]
          },
          {
            title: 'Revue & Rétrospective',
            tasks: [
              {
                title: 'Présenter le travail livré & noter le bilan rétro',
                whenDaysOffset: 4,
                durationMinutes: 30,
                checklistItems: [
                  'Ce qui a bien fonctionné',
                  'Ce qu’il faut améliorer au prochain sprint'
                ]
              }
            ]
          }
        ]
      }
    }
  ],
  it: [
    {
      id: 'starter-trip-packing',
      icon: '🧳',
      titleKey: 'starterTplTripTitle',
      descKey: 'starterTplTripDesc',
      defaultTitle: 'Viaggio & Valigia',
      defaultNotes:
        'Organizzazione completa del viaggio: prenotazioni, documenti, lista bagagli e controlli prima di partire.',
      blueprint: {
        version: 1,
        tasks: [
          {
            title: 'Confermare prenotazioni di volo, treno e hotel',
            whenDaysOffset: 0,
            durationMinutes: 30,
            checklistItems: [
              "Salvare carte d'imbarco offline",
              "Condividere l'itinerario con la famiglia"
            ]
          }
        ],
        headings: [
          {
            title: 'Documenti & Essenziali',
            tasks: [
              {
                title: 'Verificare validità passaporto / documento e assicurazione viaggio',
                whenDaysOffset: 0,
                durationMinutes: 20,
                checklistItems: [
                  "Passaporto / Carta d'identità",
                  'Carte di pagamento & valuta locale',
                  'Tessera sanitaria / assicurativa'
                ]
              }
            ]
          },
          {
            title: 'Lista bagagli',
            tasks: [
              {
                title: 'Preparare abbigliamento & beauty case',
                whenDaysOffset: 1,
                durationMinutes: 45,
                checklistItems: [
                  'Abiti adatti al meteo',
                  'Scarpe & giacca',
                  'Articoli da toilette & medicinali'
                ]
              },
              {
                title: 'Preparare dispositivi elettronici & caricabatterie',
                whenDaysOffset: 1,
                durationMinutes: 15,
                checklistItems: [
                  'Caricabatterie telefono + laptop',
                  'Power bank',
                  'Adattatore universale da viaggio',
                  'Cuffie / auricolari'
                ]
              }
            ]
          },
          {
            title: 'Prima di uscire di casa',
            tasks: [
              {
                title: 'Controllo finale di casa prima della partenza',
                whenDaysOffset: 2,
                durationMinutes: 20,
                checklistItems: [
                  'Svuotare rifiuti & cibi deperibili',
                  'Annaffiare le piante',
                  'Chiudere finestre & porte'
                ]
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-product-launch',
      icon: '🚀',
      titleKey: 'starterTplLaunchTitle',
      descKey: 'starterTplLaunchDesc',
      defaultTitle: 'Lancio prodotto',
      defaultNotes:
        'Playbook GTD strutturato per il lancio: posizionamento, verifica QA, giorno del lancio e follow-up.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Strategia & Posizionamento',
            tasks: [
              {
                title: 'Finalizzare messaggio di lancio & note di rilascio',
                whenDaysOffset: 0,
                durationMinutes: 45,
                checklistItems: [
                  'Proposta di valore principale',
                  'Punti salienti del changelog',
                  'Screenshot & materiali demo'
                ]
              }
            ]
          },
          {
            title: 'Prontezza Build & QA',
            tasks: [
              {
                title: 'Completare test QA pre-rilascio & verifica migrazione',
                whenDaysOffset: 1,
                durationMinutes: 60,
                checklistItems: [
                  'Percorso di aggiornamento verificato',
                  'Sincronizzazione cloud verificata',
                  'Zero avvisi di lint/build'
                ]
              }
            ]
          },
          {
            title: 'Giorno del lancio',
            tasks: [
              {
                title: 'Pubblicare la release & annunciare sui canali',
                whenDaysOffset: 2,
                scheduledStartMinutes: 540,
                durationMinutes: 45,
                checklistItems: [
                  'Taggare la build di rilascio',
                  'Aggiornare landing page / documentazione',
                  "Pubblicare l'annuncio"
                ]
              }
            ]
          },
          {
            title: 'Follow-up post-lancio',
            tasks: [
              {
                title: 'Monitorare la telemetria & raccogliere i primi feedback',
                whenDaysOffset: 3,
                durationMinutes: 30
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-client-onboarding',
      icon: '🤝',
      titleKey: 'starterTplClientTitle',
      descKey: 'starterTplClientDesc',
      defaultTitle: 'Onboarding cliente',
      defaultNotes:
        'Flusso ripetibile di avvio cliente: dal contratto firmato alla consegna del primo traguardo.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Kickoff & Accordi',
            tasks: [
              {
                title: 'Controfirmare il contratto & inviare kit di benvenuto',
                whenDaysOffset: 0,
                durationMinutes: 30,
                checklistItems: [
                  'Contratto firmato archiviato',
                  'Profilo di fatturazione configurato',
                  'Email di benvenuto inviata'
                ]
              },
              {
                title: 'Pianificare il workshop di kickoff',
                whenDaysOffset: 0,
                durationMinutes: 15
              }
            ]
          },
          {
            title: 'Accessi & Discovery',
            tasks: [
              {
                title: 'Configurare spazio condiviso & accessi ai repository',
                whenDaysOffset: 1,
                durationMinutes: 30,
                checklistItems: [
                  'Cartella & documenti condivisi',
                  'Canale di comunicazione',
                  'Credenziali tecniche'
                ]
              }
            ]
          },
          {
            title: 'Primo traguardo',
            tasks: [
              {
                title: 'Consegnare roadmap a 30 giorni & metriche di successo',
                whenDaysOffset: 3,
                deadlineDaysOffset: 7,
                durationMinutes: 60
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-weekly-sprint',
      icon: '⚡',
      titleKey: 'starterTplSprintTitle',
      descKey: 'starterTplSprintDesc',
      defaultTitle: 'Sprint settimanale',
      defaultNotes:
        'Sprint focalizzato di 5 giorni con obiettivi chiave, blocchi di deep work e retrospettiva del venerdì.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Pianificazione sprint',
            tasks: [
              {
                title: 'Definire i 3 risultati principali della settimana',
                whenDaysOffset: 0,
                scheduledStartMinutes: 540,
                durationMinutes: 30,
                checklistItems: ['Risultato #1', 'Risultato #2', 'Risultato #3']
              }
            ]
          },
          {
            title: 'Esecuzione Deep Work',
            tasks: [
              {
                title: 'Completare il risultato principale dello sprint',
                whenDaysOffset: 1,
                durationMinutes: 90
              },
              {
                title: 'Rimuovere blocchi di metà settimana & allineamento stakeholder',
                whenDaysOffset: 2,
                durationMinutes: 30
              }
            ]
          },
          {
            title: 'Revisione & Retrospettiva',
            tasks: [
              {
                title: 'Presentare il lavoro completato & annotare la retrospettiva',
                whenDaysOffset: 4,
                durationMinutes: 30,
                checklistItems: [
                  'Cosa ha funzionato bene',
                  'Cosa migliorare nel prossimo sprint'
                ]
              }
            ]
          }
        ]
      }
    }
  ],
  es: [
    {
      id: 'starter-trip-packing',
      icon: '🧳',
      titleKey: 'starterTplTripTitle',
      descKey: 'starterTplTripDesc',
      defaultTitle: 'Viaje y Equipaje',
      defaultNotes:
        'Preparación integral del viaje: reservas, documentos, lista de equipaje y revisión antes de salir.',
      blueprint: {
        version: 1,
        tasks: [
          {
            title: 'Confirmar reservas de vuelo, tren y hotel',
            whenDaysOffset: 0,
            durationMinutes: 30,
            checklistItems: [
              'Guardar tarjetas de embarque sin conexión',
              'Compartir itinerario con la familia'
            ]
          }
        ],
        headings: [
          {
            title: 'Documentos e Imprescindibles',
            tasks: [
              {
                title: 'Verificar validez de pasaporte / DNI y seguro de viaje',
                whenDaysOffset: 0,
                durationMinutes: 20,
                checklistItems: [
                  'Pasaporte / Documento de identidad',
                  'Tarjetas bancarias y moneda local',
                  'Tarjeta sanitaria / de seguro'
                ]
              }
            ]
          },
          {
            title: 'Lista de equipaje',
            tasks: [
              {
                title: 'Empacar ropa y neceser',
                whenDaysOffset: 1,
                durationMinutes: 45,
                checklistItems: [
                  'Ropa adecuada al clima',
                  'Calzado y chaqueta',
                  'Artículos de aseo y medicación'
                ]
              },
              {
                title: 'Empacar dispositivos electrónicos y cargadores',
                whenDaysOffset: 1,
                durationMinutes: 15,
                checklistItems: [
                  'Cargadores de móvil y portátil',
                  'Batería externa',
                  'Adaptador universal de viaje',
                  'Auriculares'
                ]
              }
            ]
          },
          {
            title: 'Antes de salir de casa',
            tasks: [
              {
                title: 'Revisión final de casa antes de salir',
                whenDaysOffset: 2,
                durationMinutes: 20,
                checklistItems: [
                  'Sacar basura y alimentos perecederos',
                  'Regar las plantas',
                  'Cerrar ventanas y puertas con llave'
                ]
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-product-launch',
      icon: '🚀',
      titleKey: 'starterTplLaunchTitle',
      descKey: 'starterTplLaunchDesc',
      defaultTitle: 'Lanzamiento de producto',
      defaultNotes:
        'Guía GTD estructurada de lanzamiento: posicionamiento, validación QA, día de lanzamiento y seguimiento.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Estrategia y Posicionamiento',
            tasks: [
              {
                title: 'Definir mensaje de lanzamiento y notas de la versión',
                whenDaysOffset: 0,
                durationMinutes: 45,
                checklistItems: [
                  'Propuesta de valor principal',
                  'Novedades destacadas del registro de cambios',
                  'Capturas de pantalla y recursos demo'
                ]
              }
            ]
          },
          {
            title: 'Preparación de Build y QA',
            tasks: [
              {
                title: 'Completar pruebas QA previas al lanzamiento y migración',
                whenDaysOffset: 1,
                durationMinutes: 60,
                checklistItems: [
                  'Ruta de actualización verificada',
                  'Sincronización en la nube verificada',
                  'Cero advertencias de lint/compilación'
                ]
              }
            ]
          },
          {
            title: 'Ejecución del día de lanzamiento',
            tasks: [
              {
                title: 'Publicar versión y anunciar en todos los canales',
                whenDaysOffset: 2,
                scheduledStartMinutes: 540,
                durationMinutes: 45,
                checklistItems: [
                  'Etiquetar compilación de lanzamiento',
                  'Actualizar página de inicio / documentación',
                  'Publicar anuncio oficial'
                ]
              }
            ]
          },
          {
            title: 'Seguimiento poslanzamiento',
            tasks: [
              {
                title: 'Supervisar telemetría y clasificar primeros comentarios',
                whenDaysOffset: 3,
                durationMinutes: 30
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-client-onboarding',
      icon: '🤝',
      titleKey: 'starterTplClientTitle',
      descKey: 'starterTplClientDesc',
      defaultTitle: 'Incorporación de cliente',
      defaultNotes:
        'Flujo repetible de inicio con clientes desde el acuerdo firmado hasta el primer hito entregado.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Inicio y Acuerdos',
            tasks: [
              {
                title: 'Firmar acuerdo y enviar paquete de bienvenida',
                whenDaysOffset: 0,
                durationMinutes: 30,
                checklistItems: [
                  'Contrato firmado archivado',
                  'Perfil de facturación configurado',
                  'Correo de bienvenida enviado'
                ]
              },
              {
                title: 'Programar taller de inicio',
                whenDaysOffset: 0,
                durationMinutes: 15
              }
            ]
          },
          {
            title: 'Accesos y Descubrimiento',
            tasks: [
              {
                title: 'Configurar espacio compartido y acceso a repositorios',
                whenDaysOffset: 1,
                durationMinutes: 30,
                checklistItems: [
                  'Carpeta y documentos compartidos',
                  'Canal de comunicación',
                  'Credenciales técnicas'
                ]
              }
            ]
          },
          {
            title: 'Primer hito',
            tasks: [
              {
                title: 'Entregar hoja de ruta de 30 días y métricas de éxito',
                whenDaysOffset: 3,
                deadlineDaysOffset: 7,
                durationMinutes: 60
              }
            ]
          }
        ]
      }
    },
    {
      id: 'starter-weekly-sprint',
      icon: '⚡',
      titleKey: 'starterTplSprintTitle',
      descKey: 'starterTplSprintDesc',
      defaultTitle: 'Sprint semanal',
      defaultNotes:
        'Sprint enfocado de 5 días con resultados clave, bloques de trabajo profundo y retrospectiva del viernes.',
      blueprint: {
        version: 1,
        tasks: [],
        headings: [
          {
            title: 'Planificación del sprint',
            tasks: [
              {
                title: 'Definir los 3 resultados clave de la semana',
                whenDaysOffset: 0,
                scheduledStartMinutes: 540,
                durationMinutes: 30,
                checklistItems: ['Resultado #1', 'Resultado #2', 'Resultado #3']
              }
            ]
          },
          {
            title: 'Ejecución de trabajo profundo',
            tasks: [
              {
                title: 'Entregar el objetivo principal del sprint',
                whenDaysOffset: 1,
                durationMinutes: 90
              },
              {
                title: 'Resolver bloqueos de mitad de semana y revisión de avance',
                whenDaysOffset: 2,
                durationMinutes: 30
              }
            ]
          },
          {
            title: 'Revisión y Retrospectiva',
            tasks: [
              {
                title: 'Mostrar avances entregados y anotar retrospectiva',
                whenDaysOffset: 4,
                durationMinutes: 30,
                checklistItems: [
                  'Qué funcionó bien',
                  'Qué mejorar en el próximo sprint'
                ]
              }
            ]
          }
        ]
      }
    }
  ]
};

export function getStarterGtdTemplates(
  lang: SupportedLang = 'en'
): readonly StarterProjectTemplateDef[] {
  return STARTER_GTD_TEMPLATES_BY_LANG[lang] ?? STARTER_GTD_TEMPLATES_BY_LANG.en;
}

export const STARTER_GTD_TEMPLATES: readonly StarterProjectTemplateDef[] =
  STARTER_GTD_TEMPLATES_BY_LANG.en;

/**
 * Captures a live Project, its Headings, Open Tasks, and Checklists into a reusable ProjectTemplateBlueprint.
 */
export function buildProjectTemplateBlueprint(
  project: ProjectRecord,
  headings: readonly HeadingRecord[],
  tasks: readonly TaskRecord[],
  checklistItems: readonly ChecklistItemRecord[],
  referenceDayMillis: number = startOfDayEpoch()
): ProjectTemplateBlueprint {
  const refDay = startOfDayEpoch(new Date(referenceDayMillis));
  const checklistsByTask = new Map<string, ChecklistItemRecord[]>();
  for (const ci of checklistItems) {
    if (ci.isDeleted) continue;
    const arr = checklistsByTask.get(ci.taskId) ?? [];
    arr.push(ci);
    checklistsByTask.set(ci.taskId, arr);
  }

  const toOffset = (epochMillis: number | null): number | null => {
    if (epochMillis == null || !Number.isFinite(epochMillis)) return null;
    const targetDay = startOfDayEpoch(new Date(epochMillis));
    return Math.max(0, Math.round((targetDay - refDay) / 86_400_000));
  };

  const mapTask = (t: TaskRecord): ProjectTemplateTaskBlueprint => {
    const items = (checklistsByTask.get(t.id) ?? [])
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((ci) => ci.title)
      .filter((s) => s.trim().length > 0);

    const bp: ProjectTemplateTaskBlueprint = {
      title: t.title
    };
    if (t.notes.trim()) bp.notes = t.notes;
    if (items.length > 0) bp.checklistItems = items;
    const whenOff = toOffset(t.whenDate);
    if (whenOff != null) bp.whenDaysOffset = whenOff;
    const dlOff = toOffset(t.deadline);
    if (dlOff != null) bp.deadlineDaysOffset = dlOff;
    if (t.scheduleState && t.scheduleState !== 'ANYTIME') {
      bp.scheduleState = t.scheduleState;
    }
    if (t.scheduledStartMinutes != null) {
      bp.scheduledStartMinutes = t.scheduledStartMinutes;
    }
    if (t.durationMinutes != null) {
      bp.durationMinutes = t.durationMinutes;
    }
    return bp;
  };

  const activeHeadings = headings
    .filter((h) => !h.isDeleted && !h.isArchived && h.projectId === project.id)
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const validHeadingIds = new Set(activeHeadings.map((h) => h.id));

  const openProjectTasks = tasks
    .filter((t) => !t.isDeleted && t.status === 'OPEN' && t.projectId === project.id)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  const ungroupedTasks = openProjectTasks
    .filter((t) => !t.headingId || !validHeadingIds.has(t.headingId))
    .map(mapTask);

  const headingBlueprints: ProjectTemplateHeadingBlueprint[] = activeHeadings.map((h) => ({
    title: h.title,
    tasks: openProjectTasks.filter((t) => t.headingId === h.id).map(mapTask)
  }));

  return {
    version: 1,
    notes: project.notes || undefined,
    tasks: ungroupedTasks,
    headings: headingBlueprints
  };
}

function normalizeBlueprintTask(raw: unknown): ProjectTemplateTaskBlueprint | null {
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    return trimmed ? { title: trimmed } : null;
  }
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  const title = typeof r.title === 'string' ? r.title.trim() : '';
  if (!title) return null;

  const rawChecklist = Array.isArray(r.checklistItems)
    ? r.checklistItems
    : Array.isArray(r.checklist)
      ? r.checklist
      : Array.isArray(r.subtasks)
        ? r.subtasks
        : [];
  const checklistItems = rawChecklist
    .map((item) => {
      if (typeof item === 'string') return item.trim();
      if (item && typeof item === 'object' && typeof (item as Record<string, unknown>).title === 'string') {
        return String((item as Record<string, unknown>).title).trim();
      }
      return '';
    })
    .filter(Boolean);

  const whenOffsetRaw = r.whenDaysOffset ?? r.dayOffset;
  const whenDaysOffset =
    typeof whenOffsetRaw === 'number' && Number.isFinite(whenOffsetRaw)
      ? Math.trunc(whenOffsetRaw)
      : null;
  const deadlineDaysOffset =
    typeof r.deadlineDaysOffset === 'number' && Number.isFinite(r.deadlineDaysOffset)
      ? Math.trunc(r.deadlineDaysOffset)
      : null;
  const scheduledStartMinutes =
    typeof r.scheduledStartMinutes === 'number' && Number.isFinite(r.scheduledStartMinutes)
      ? Math.max(0, Math.min(1439, Math.trunc(r.scheduledStartMinutes)))
      : null;
  const durationMinutes =
    typeof r.durationMinutes === 'number' && Number.isFinite(r.durationMinutes) && r.durationMinutes > 0
      ? Math.max(5, Math.min(720, Math.trunc(r.durationMinutes)))
      : null;

  const validStates = new Set<ScheduleState>([
    'INBOX',
    'TODAY',
    'THIS_EVENING',
    'UPCOMING',
    'ANYTIME',
    'SOMEDAY'
  ]);
  const scheduleState =
    typeof r.scheduleState === 'string' && validStates.has(r.scheduleState as ScheduleState)
      ? (r.scheduleState as ScheduleState)
      : undefined;

  return {
    title,
    notes: typeof r.notes === 'string' && r.notes.trim() ? r.notes : undefined,
    checklistItems: checklistItems.length > 0 ? checklistItems : undefined,
    whenDaysOffset,
    deadlineDaysOffset,
    scheduleState,
    scheduledStartMinutes,
    durationMinutes
  };
}

/**
 * Safely parses stored `blueprintJson` from ChromeOS, Android, or macOS into `ProjectTemplateBlueprint`.
 */
export function parseTemplateBlueprintJson(blueprintJson: string): ProjectTemplateBlueprint {
  try {
    const parsed: unknown = JSON.parse(blueprintJson || '{}');
    if (!parsed || typeof parsed !== 'object') {
      return { version: 1, tasks: [], headings: [] };
    }
    const obj = parsed as Record<string, unknown>;
    const rawTopTasks = Array.isArray(obj.tasks)
      ? obj.tasks
      : Array.isArray(obj.unheadedTasks)
        ? obj.unheadedTasks
        : [];
    const tasks = rawTopTasks
      .map(normalizeBlueprintTask)
      .filter((t): t is ProjectTemplateTaskBlueprint => t !== null);

    const rawHeadings = Array.isArray(obj.headings) ? obj.headings : [];
    const headings: ProjectTemplateHeadingBlueprint[] = [];
    for (const h of rawHeadings) {
      if (!h || typeof h !== 'object') continue;
      const rec = h as Record<string, unknown>;
      const title = typeof rec.title === 'string' ? rec.title.trim() : '';
      if (!title) continue;
      const hTasks = (Array.isArray(rec.tasks) ? rec.tasks : [])
        .map(normalizeBlueprintTask)
        .filter((t): t is ProjectTemplateTaskBlueprint => t !== null);
      headings.push({ title, tasks: hTasks });
    }

    return {
      version: 1,
      notes: typeof obj.notes === 'string' ? obj.notes : undefined,
      tasks,
      headings
    };
  } catch {
    return { version: 1, tasks: [], headings: [] };
  }
}

export interface ParsedProjectOutline {
  title: string;
  notes: string;
  blueprint: ProjectTemplateBlueprint;
  stats: {
    headingsCount: number;
    tasksCount: number;
    subtasksCount: number;
  };
}

/**
 * Parses either a Markdown outline (`# Project`, `## Heading`, `- [ ] Task`, `  - [ ] Subtask`)
 * or a JSON outline into a structured project blueprint ready for 1-click creation.
 */
export function parseProjectOutlineInput(
  rawInput: string,
  fallbackTitle = 'Imported Project'
): ParsedProjectOutline {
  const trimmed = String(rawInput || '').trim();
  if (!trimmed) {
    return {
      title: fallbackTitle,
      notes: '',
      blueprint: { version: 1, tasks: [], headings: [] },
      stats: { headingsCount: 0, tasksCount: 0, subtasksCount: 0 }
    };
  }

  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const jsonVal: unknown = JSON.parse(trimmed);
      if (Array.isArray(jsonVal)) {
        const tasks = jsonVal
          .map(normalizeBlueprintTask)
          .filter((t): t is ProjectTemplateTaskBlueprint => t !== null);
        const subtasksCount = tasks.reduce((acc, t) => acc + (t.checklistItems?.length ?? 0), 0);
        return {
          title: fallbackTitle,
          notes: '',
          blueprint: { version: 1, tasks, headings: [] },
          stats: { headingsCount: 0, tasksCount: tasks.length, subtasksCount }
        };
      }
      if (jsonVal && typeof jsonVal === 'object') {
        const obj = jsonVal as Record<string, unknown>;
        const bpSource =
          obj.blueprint && typeof obj.blueprint === 'object'
            ? JSON.stringify(obj.blueprint)
            : trimmed;
        const bp = parseTemplateBlueprintJson(bpSource);
        const title =
          typeof obj.title === 'string' && obj.title.trim()
            ? obj.title.trim()
            : typeof obj.projectTitle === 'string' && obj.projectTitle.trim()
              ? obj.projectTitle.trim()
              : fallbackTitle;
        const notes =
          typeof obj.notes === 'string'
            ? obj.notes.trim()
            : bp.notes?.trim() ?? '';
        const topTasks = bp.tasks ?? [];
        const headingTasksCount = bp.headings.reduce((s, h) => s + h.tasks.length, 0);
        const subtasksCount =
          topTasks.reduce((s, t) => s + (t.checklistItems?.length ?? 0), 0) +
          bp.headings.reduce(
            (s, h) => s + h.tasks.reduce(( acc, t) => acc + (t.checklistItems?.length ?? 0), 0),
            0
          );
        return {
          title,
          notes,
          blueprint: bp,
          stats: {
            headingsCount: bp.headings.length,
            tasksCount: topTasks.length + headingTasksCount,
            subtasksCount
          }
        };
      }
    } catch {
      // Fall through to Markdown parser if invalid JSON
    }
  }

  const lines = trimmed.replace(/\r\n/g, '\n').split('\n');
  let projectTitle = '';
  const noteLines: string[] = [];
  const topTasks: ProjectTemplateTaskBlueprint[] = [];
  const headings: ProjectTemplateHeadingBlueprint[] = [];
  let currentHeading: ProjectTemplateHeadingBlueprint | null = null;
  let lastTask: ProjectTemplateTaskBlueprint | null = null;
  let seenStructure = false;

  for (const rawLine of lines) {
    if (!rawLine.trim()) continue;

    // # Project Title (H1)
    const h1Match = /^#\s+(.+)$/.exec(rawLine.trim());
    if (h1Match && !projectTitle && !seenStructure) {
      projectTitle = h1Match[1].trim();
      continue;
    }

    // ## Heading or ### Heading
    const h2Match = /^#{2,4}\s+(.+)$/.exec(rawLine.trim());
    if (h2Match) {
      seenStructure = true;
      currentHeading = {
        title: h2Match[1].trim(),
        tasks: []
      };
      headings.push(currentHeading);
      lastTask = null;
      continue;
    }

    // List item (task or indented subtask)
    const listMatch = /^([ \t]*)(?:[-*+]|\d+[.)])\s+(?:\[[ xX]\]\s+)?(.+)$/.exec(rawLine);
    if (listMatch) {
      seenStructure = true;
      const indentRaw = listMatch[1] || '';
      const indentSpaces = indentRaw.replace(/\t/g, '  ').length;
      const itemText = listMatch[2].trim();
      if (!itemText) continue;

      if (indentSpaces >= 2 && lastTask) {
        if (!lastTask.checklistItems) lastTask.checklistItems = [];
        lastTask.checklistItems.push(itemText);
      } else {
        const newTask: ProjectTemplateTaskBlueprint = { title: itemText };
        if (currentHeading) {
          currentHeading.tasks.push(newTask);
        } else {
          topTasks.push(newTask);
        }
        lastTask = newTask;
      }
      continue;
    }

    // Plain non-list line
    const cleanLine = rawLine.trim();
    if (!seenStructure && !projectTitle && lines.length > 1 && noteLines.length === 0) {
      // If user didn't use `# `, keep collecting notes unless there's no `#` anywhere
      noteLines.push(cleanLine);
    } else if (!seenStructure) {
      noteLines.push(cleanLine);
    } else if (lastTask) {
      lastTask.notes = lastTask.notes ? `${lastTask.notes}\n${cleanLine}` : cleanLine;
    }
  }

  if (!projectTitle) {
    if (noteLines.length > 0) {
      projectTitle = noteLines.shift()!;
    } else {
      projectTitle = fallbackTitle;
    }
  }

  const notes = noteLines.join('\n').trim();
  const blueprint: ProjectTemplateBlueprint = {
    version: 1,
    notes: notes || undefined,
    tasks: topTasks,
    headings
  };

  const headingTasksCount = headings.reduce((s, h) => s + h.tasks.length, 0);
  const subtasksCount =
    topTasks.reduce((s, t) => s + (t.checklistItems?.length ?? 0), 0) +
    headings.reduce(
      (s, h) => s + h.tasks.reduce((acc, t) => acc + (t.checklistItems?.length ?? 0), 0),
      0
    );

  return {
    title: projectTitle,
    notes,
    blueprint,
    stats: {
      headingsCount: headings.length,
      tasksCount: topTasks.length + headingTasksCount,
      subtasksCount
    }
  };
}
