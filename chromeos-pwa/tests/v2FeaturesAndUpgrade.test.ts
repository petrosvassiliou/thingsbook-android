import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildBackupPayloadFromEntities,
  decodeThingsBackup,
  encodeThingsBackup,
  inspectThingsBackup,
  normalizeBackupPayloadToEntities,
  type ThingsbookBackupPayload
} from '../src/data/backupCodec.ts';
import {
  mergeMonotonicReviewTimestamp,
  resolveMergedHeadingEndDate
} from '../src/data/syncUtils.ts';
import {
  DEFAULT_QUICK_FIND_FILTER,
  STARTER_GTD_TEMPLATES,
  buildDailyTimeline,
  buildProjectTemplateBlueprint,
  computeClampedContextMenuPosition,
  detectStalledProjects,
  evaluateProjectStalledStatus,
  formatMinutesAsClock,
  formatTimeblockRange,
  getStarterGtdTemplates,
  hasActiveQuickFindFilters,
  matchesQuickFindProject,
  matchesQuickFindTask,
  parseClockToMinutes,
  parseProjectOutlineInput,
  parseTemplateBlueprintJson,
  shouldShowSmartViewsAndAreasInQuickFind
} from '../src/domain/v2Utils.ts';
import { TRANSLATIONS } from '../src/i18n/translations.ts';
import type {
  AreaRecord,
  CalendarEventItem,
  ChecklistItemRecord,
  HeadingRecord,
  ProjectRecord,
  ProjectTemplate,
  TagRecord,
  TaskRecord
} from '../src/domain/types.ts';

test('v1.7 / v1.8.1 -> v2.0.0-beta.1 .tbbackup backward-compatible import applies safe null/[] defaults with zero data loss', async () => {
  // Construct an authentic legacy v1.8.1 payload WITHOUT projectTemplates, scheduledStartMinutes,
  // durationMinutes, lastReviewedAt, includeBetaUpdates, or lastWeeklyReviewAt
  const legacyV181Payload = {
    schemaVersion: 1,
    createdAt: 1_760_000_000_000,
    appVersionName: '1.8.1-chromeos',
    areas: [
      {
        id: 'area-work',
        title: 'Work',
        sortOrder: 1000,
        tagIdsJson: '["tag-deep"]',
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    projects: [
      {
        id: 'proj-q3',
        areaId: 'area-work',
        title: 'Legacy 1.8.1 Launch',
        notes: 'Keep 100% intact across 2.0 upgrade',
        status: 'ACTIVE',
        scheduleState: 'ANYTIME',
        whenDate: null,
        deadline: 1_765_000_000_000,
        completedAt: null,
        sortOrder: 1000,
        tagIdsJson: '["tag-deep"]',
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    headings: [
      {
        id: 'head-1',
        projectId: 'proj-q3',
        title: 'Engineering',
        sortOrder: 1000,
        isArchived: false,
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    tasks: [
      {
        id: 'task-legacy-1',
        title: 'Ship database migration',
        notes: 'Zero data loss required',
        status: 'OPEN',
        scheduleState: 'TODAY',
        whenDate: 1_760_000_000_000,
        deadline: null,
        reminderTime: null,
        completedAt: null,
        projectId: 'proj-q3',
        headingId: 'head-1',
        areaId: null,
        recurrenceRuleJson: null,
        sortOrder: 1000,
        tagIdsJson: '["tag-deep"]',
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    checklistItems: [
      {
        id: 'chk-1',
        taskId: 'task-legacy-1',
        title: 'Verify v1 and v2 stores',
        isCompleted: true,
        sortOrder: 1000,
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    tags: [
      {
        id: 'tag-deep',
        title: 'Deep Work',
        parentTagId: null,
        shortcut: 'w',
        sortOrder: 1000,
        createdAt: 1_750_000_000_000,
        updatedAt: 1_760_000_000_000
      }
    ],
    taskTagRefs: [{ taskId: 'task-legacy-1', tagId: 'tag-deep' }],
    projectTagRefs: [{ projectId: 'proj-q3', tagId: 'tag-deep' }],
    preferences: {
      sidebarVisible: true,
      dailyDigestEnabled: false,
      languageTag: 'en',
      groupTodayByProjectOrArea: true
    }
  } as unknown as ThingsbookBackupPayload;

  const archiveBytes = await encodeThingsBackup(legacyV181Payload, null, '1.8.1-chromeos', 44);
  const inspected = await inspectThingsBackup(archiveBytes);
  assert.equal(inspected.kind, 'plain');
  assert.equal(inspected.summary.projectsCount, 1);
  assert.equal(inspected.summary.tasksCount, 1);
  assert.equal(inspected.summary.projectTemplatesCount, 0);

  const decoded = await decodeThingsBackup(archiveBytes, null);
  const normalized = normalizeBackupPayloadToEntities(decoded, false, 1_770_000_000_000);

  assert.equal(normalized.areas.length, 1);
  assert.equal(normalized.projects.length, 1);
  assert.equal(normalized.projects[0].title, 'Legacy 1.8.1 Launch');
  assert.equal(normalized.projects[0].lastReviewedAt, null);

  assert.equal(normalized.tasks.length, 1);
  assert.equal(normalized.tasks[0].title, 'Ship database migration');
  assert.equal(normalized.tasks[0].scheduledStartMinutes, null);
  assert.equal(normalized.tasks[0].durationMinutes, null);

  assert.equal(normalized.checklistItems.length, 1);
  assert.deepEqual(normalized.projectTemplates, []);
  assert.equal(normalized.preferences.includeBetaUpdates, false);
  assert.equal(normalized.preferences.lastWeeklyReviewAt, 0);
});

test('v2.0.0-beta.1 .tbbackup encrypted & plain roundtrip preserves projectTemplates, scheduledStartMinutes, durationMinutes, and lastReviewedAt', async () => {
  const now = 1_775_000_000_000;
  const tags: TagRecord[] = [
    {
      id: 'tag-focus',
      title: 'Focus',
      parentTagId: null,
      shortcut: 'f',
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const areas: AreaRecord[] = [
    {
      id: 'area-prod',
      title: 'Product',
      sortOrder: 1000,
      tagIds: ['tag-focus'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const projects: ProjectRecord[] = [
    {
      id: 'proj-v2',
      areaId: 'area-prod',
      title: 'Thingsbook 2.0 Beta',
      notes: 'Cross-platform release',
      status: 'ACTIVE',
      scheduleState: 'ANYTIME',
      whenDate: null,
      deadline: null,
      completedAt: null,
      lastReviewedAt: 1_774_900_000_000,
      sortOrder: 1000,
      tagIds: ['tag-focus'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const headings: HeadingRecord[] = [];
  const tasks: TaskRecord[] = [
    {
      id: 'task-tb-1',
      title: 'Morning architecture review',
      notes: '',
      status: 'OPEN',
      scheduleState: 'TODAY',
      whenDate: now,
      deadline: null,
      reminderTime: null,
      scheduledStartMinutes: 540, // 09:00
      durationMinutes: 45,
      completedAt: null,
      projectId: 'proj-v2',
      headingId: null,
      areaId: null,
      recurrenceRule: null,
      sortOrder: 1000,
      tagIds: ['tag-focus'],
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];
  const checklistItems: ChecklistItemRecord[] = [];
  const projectTemplates: ProjectTemplate[] = [
    {
      id: 'tpl-custom-1',
      title: 'Release Checklist Template',
      notes: 'Reusable beta release flow',
      defaultAreaId: 'area-prod',
      tagIds: ['tag-focus'],
      blueprintJson: JSON.stringify({
        version: 1,
        tasks: [{ title: 'Tag release', whenDaysOffset: 0, durationMinutes: 30 }],
        headings: [{ title: 'QA', tasks: [{ title: 'Run test suite', checklistItems: ['Unit', 'Build'] }] }]
      }),
      sortOrder: 1000,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      isDirty: false
    }
  ];

  const payload = buildBackupPayloadFromEntities({
    areas,
    projects,
    headings,
    tasks,
    checklistItems,
    tags,
    projectTemplates,
    preferences: {
      includeBetaUpdates: true,
      lastWeeklyReviewAt: 1_774_950_000_000
    },
    appVersionName: '2.0.0-beta.1-chromeos'
  });

  // Test AES-256-GCM encrypted archive roundtrip
  const encryptedBytes = await encodeThingsBackup(
    payload,
    'BetaVault2026!',
    '2.0.0-beta.1-chromeos',
    45
  );
  const inspectedEnc = await inspectThingsBackup(encryptedBytes);
  assert.equal(inspectedEnc.kind, 'encrypted');

  const decoded = await decodeThingsBackup(encryptedBytes, 'BetaVault2026!');
  const restored = normalizeBackupPayloadToEntities(decoded, true, now);

  assert.equal(restored.projects[0].lastReviewedAt, 1_774_900_000_000);
  assert.equal(restored.tasks[0].scheduledStartMinutes, 540);
  assert.equal(restored.tasks[0].durationMinutes, 45);
  assert.equal(restored.projectTemplates.length, 1);
  assert.equal(restored.projectTemplates[0].id, 'tpl-custom-1');
  assert.equal(restored.projectTemplates[0].defaultAreaId, 'area-prod');
  assert.deepEqual(restored.projectTemplates[0].tagIds, ['tag-focus']);
  assert.equal(restored.preferences.includeBetaUpdates, true);
  assert.equal(restored.preferences.lastWeeklyReviewAt, 1_774_950_000_000);

  const parsedBp = parseTemplateBlueprintJson(restored.projectTemplates[0].blueprintJson);
  assert.equal(parsedBp.tasks.length, 1);
  assert.equal(parsedBp.tasks[0].durationMinutes, 30);
  assert.equal(parsedBp.headings.length, 1);
  assert.deepEqual(parsedBp.headings[0].tasks[0].checklistItems, ['Unit', 'Build']);
});

test('Markdown & JSON Outline Importer + Template Blueprint Builder parse # Project, ## Heading, - [ ] Task, and indented Subtasks', () => {
  const mdOutline = `# Autumn Product Launch
Coordinate design, engineering, and announcement blog post.

- [ ] Lock launch date with leadership

## Engineering & QA
- [ ] Finalize IndexedDB v3 upgrade
  - [ ] Verify v1 -> v3 migration
  - [ ] Verify v2 -> v3 migration
- [x] Configure beta auto-updater

## Marketing
1. Publish release notes
   - Capture Chromebook screenshots`;

  const parsedMd = parseProjectOutlineInput(mdOutline, 'Fallback');
  assert.equal(parsedMd.title, 'Autumn Product Launch');
  assert.equal(parsedMd.notes, 'Coordinate design, engineering, and announcement blog post.');
  assert.equal(parsedMd.stats.headingsCount, 2);
  assert.equal(parsedMd.stats.tasksCount, 4);
  assert.equal(parsedMd.stats.subtasksCount, 3);
  assert.equal(parsedMd.blueprint.tasks[0].title, 'Lock launch date with leadership');
  assert.equal(parsedMd.blueprint.headings[0].title, 'Engineering & QA');
  assert.deepEqual(parsedMd.blueprint.headings[0].tasks[0].checklistItems, [
    'Verify v1 -> v3 migration',
    'Verify v2 -> v3 migration'
  ]);
  assert.equal(parsedMd.blueprint.headings[1].title, 'Marketing');
  assert.deepEqual(parsedMd.blueprint.headings[1].tasks[0].checklistItems, [
    'Capture Chromebook screenshots'
  ]);

  // JSON outline parsing
  const jsonOutline = JSON.stringify({
    title: 'JSON Imported Sprint',
    notes: 'Structured JSON import',
    tasks: [{ title: 'Kickoff call', durationMinutes: 30, checklistItems: ['Agenda', 'Notes'] }],
    headings: [{ title: 'Execution', tasks: [{ title: 'Build timeline toggle', durationMinutes: 45 }] }]
  });
  const parsedJson = parseProjectOutlineInput(jsonOutline, 'Untitled');
  assert.equal(parsedJson.title, 'JSON Imported Sprint');
  assert.equal(parsedJson.stats.headingsCount, 1);
  assert.equal(parsedJson.stats.tasksCount, 2);
  assert.equal(parsedJson.stats.subtasksCount, 2);
  assert.equal(parsedJson.blueprint.headings[0].tasks[0].durationMinutes, 45);

  // Verify all 4 built-in Starter GTD Templates have valid structure
  assert.equal(STARTER_GTD_TEMPLATES.length, 4);
  for (const tpl of STARTER_GTD_TEMPLATES) {
    assert.ok(tpl.defaultTitle.length > 0);
    assert.ok(tpl.blueprint.headings.length > 0);
  }

  // Verify buildProjectTemplateBlueprint captures relative offsets and durations
  const anchorDay = new Date(2026, 10, 15, 0, 0, 0, 0).getTime();
  const nextDay = new Date(2026, 10, 16, 0, 0, 0, 0).getTime();
  const builtBlueprint = buildProjectTemplateBlueprint(
    {
      id: 'p1',
      areaId: null,
      title: 'Live Project',
      notes: 'Proj notes',
      status: 'ACTIVE',
      scheduleState: 'ANYTIME',
      whenDate: null,
      deadline: null,
      completedAt: null,
      lastReviewedAt: null,
      sortOrder: 1000,
      tagIds: [],
      createdAt: anchorDay,
      updatedAt: anchorDay,
      isDeleted: false,
      isDirty: false
    },
    [
      {
        id: 'h1',
        projectId: 'p1',
        title: 'Phase 1',
        sortOrder: 1000,
        isArchived: false,
        createdAt: anchorDay,
        updatedAt: anchorDay,
        isDeleted: false,
        isDirty: false
      }
    ],
    [
      {
        id: 't1',
        title: 'Day 2 Task',
        notes: 'Task note',
        status: 'OPEN',
        scheduleState: 'UPCOMING',
        whenDate: nextDay,
        deadline: null,
        reminderTime: null,
        scheduledStartMinutes: 600,
        durationMinutes: 60,
        completedAt: null,
        projectId: 'p1',
        headingId: 'h1',
        areaId: null,
        recurrenceRule: null,
        sortOrder: 1000,
        tagIds: [],
        createdAt: anchorDay,
        updatedAt: anchorDay,
        isDeleted: false,
        isDirty: false
      }
    ],
    [
      {
        id: 'c1',
        taskId: 't1',
        title: 'Sub-step A',
        isCompleted: false,
        sortOrder: 1000,
        createdAt: anchorDay,
        updatedAt: anchorDay,
        isDeleted: false,
        isDirty: false
      }
    ],
    anchorDay
  );
  const roundtrippedBp = parseTemplateBlueprintJson(JSON.stringify(builtBlueprint));
  assert.equal(roundtrippedBp.headings[0].tasks[0].whenDaysOffset, 1);
  assert.equal(roundtrippedBp.headings[0].tasks[0].durationMinutes, 60);
  assert.deepEqual(roundtrippedBp.headings[0].tasks[0].checklistItems, ['Sub-step A']);
});

test('Stalled Project Detector & Timeblocking Daily Timeline interleaving + conflict detection', () => {
  const dayMs = 86_400_000;
  const now = 1_780_000_000_000;

  const makeProject = (overrides: Partial<ProjectRecord>): ProjectRecord => ({
    id: 'p-default',
    areaId: null,
    title: 'Project',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    lastReviewedAt: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: now - 5 * dayMs,
    updatedAt: now - 2 * dayMs,
    isDeleted: false,
    isDirty: false,
    ...overrides
  });

  const makeTask = (overrides: Partial<TaskRecord>): TaskRecord => ({
    id: 't-default',
    title: 'Task',
    notes: '',
    status: 'OPEN',
    scheduleState: 'TODAY',
    whenDate: now,
    deadline: null,
    reminderTime: null,
    scheduledStartMinutes: null,
    durationMinutes: null,
    completedAt: null,
    projectId: null,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: now - 2 * dayMs,
    updatedAt: now - 2 * dayMs,
    isDeleted: false,
    isDirty: false,
    ...overrides
  });

  const projNoActions = makeProject({ id: 'p-empty', title: 'Empty Project', sortOrder: 100 });
  const projStale16d = makeProject({
    id: 'p-stale',
    title: 'Untouched 16d',
    createdAt: now - 20 * dayMs,
    updatedAt: now - 16 * dayMs,
    sortOrder: 200
  });
  const projReviewedYesterday = makeProject({
    id: 'p-reviewed',
    title: 'Reviewed Recently',
    createdAt: now - 30 * dayMs,
    updatedAt: now - 25 * dayMs,
    lastReviewedAt: now - 1 * dayMs,
    sortOrder: 300
  });
  const projSomeday = makeProject({
    id: 'p-someday',
    title: 'Parked Someday',
    scheduleState: 'SOMEDAY',
    sortOrder: 400
  });

  const tasksForStalled: TaskRecord[] = [
    makeTask({
      id: 'tk-stale',
      projectId: 'p-stale',
      updatedAt: now - 16 * dayMs,
      createdAt: now - 20 * dayMs
    }),
    makeTask({
      id: 'tk-reviewed',
      projectId: 'p-reviewed',
      updatedAt: now - 25 * dayMs,
      createdAt: now - 30 * dayMs
    })
  ];

  assert.equal(evaluateProjectStalledStatus(projNoActions, tasksForStalled, now).reason, 'no_next_action');
  assert.equal(evaluateProjectStalledStatus(projStale16d, tasksForStalled, now).reason, 'inactive_14d');
  assert.equal(evaluateProjectStalledStatus(projReviewedYesterday, tasksForStalled, now).isStalled, false);
  assert.equal(evaluateProjectStalledStatus(projSomeday, tasksForStalled, now).isStalled, false);

  // Brand-new empty project (< 24h) and 3/3 completed project (< 24h) must NOT trigger attention
  const projBrandNew = makeProject({
    id: 'p-brand-new',
    title: 'Brand New Empty',
    createdAt: now - 2 * 3_600_000,
    updatedAt: now - 2 * 3_600_000
  });
  const projAllDoneToday = makeProject({
    id: 'p-all-done-today',
    title: 'NexusTS',
    createdAt: now - 20 * dayMs,
    updatedAt: now - 20 * dayMs
  });
  const doneTodayTask = makeTask({
    id: 'tk-done-today',
    projectId: 'p-all-done-today',
    status: 'COMPLETED',
    createdAt: now - 5 * dayMs,
    updatedAt: now - 2 * 3_600_000,
    completedAt: now - 2 * 3_600_000
  });
  assert.equal(evaluateProjectStalledStatus(projBrandNew, [], now).isStalled, false);
  assert.equal(evaluateProjectStalledStatus(projAllDoneToday, [doneTodayTask], now).isStalled, false);
  assert.equal(
    evaluateProjectStalledStatus(projAllDoneToday, [doneTodayTask], now + 23 * 3_600_000).reason,
    'no_next_action'
  );

  const stalledList = detectStalledProjects(
    [projSomeday, projReviewedYesterday, projStale16d, projNoActions],
    tasksForStalled,
    now
  );
  assert.deepEqual(
    stalledList.map((x) => x.project.id),
    ['p-empty', 'p-stale']
  );

  // Timeblock formatting & clock utilities
  assert.equal(formatMinutesAsClock(540), '09:00');
  assert.equal(parseClockToMinutes('09:45'), 585);
  assert.equal(formatTimeblockRange(540, 45), '09:00–09:45 · 45m');

  // Chronological Daily Timeline + Overlap Conflict Detection
  const dayStart = new Date(2026, 10, 15, 0, 0, 0, 0).getTime();
  const calStandupStart = new Date(2026, 10, 15, 9, 30, 0, 0).getTime();
  const calStandupEnd = new Date(2026, 10, 15, 10, 0, 0, 0).getTime();

  const calEvents: CalendarEventItem[] = [
    {
      eventId: 'ev-standup',
      calendarId: 'cal-1',
      title: 'Team Standup',
      startMillis: calStandupStart,
      endMillis: calStandupEnd,
      isAllDay: false,
      colorHex: '#4285F4',
      location: 'Meet'
    }
  ];

  const todayTasks: TaskRecord[] = [
    makeTask({
      id: 'tb-deep',
      title: 'Write 2.0 Migration Spec',
      scheduledStartMinutes: 9 * 60, // 09:00–09:45 (overlaps 09:30–10:00 Team Standup!)
      durationMinutes: 45
    }),
    makeTask({
      id: 'tb-afternoon',
      title: 'Review Beta Feedback',
      scheduledStartMinutes: 14 * 60, // 14:00–14:30 (no overlap)
      durationMinutes: 30
    }),
    makeTask({
      id: 'tb-unscheduled',
      title: 'Reply to emails',
      scheduledStartMinutes: null,
      durationMinutes: null
    })
  ];

  const timeline = buildDailyTimeline(todayTasks, calEvents, dayStart);
  assert.equal(timeline.timedItems.length, 3);
  assert.equal(timeline.unscheduledTasks.length, 1);
  assert.equal(timeline.unscheduledTasks[0].id, 'tb-unscheduled');

  // Chronological order: 09:00 task -> 09:30 calendar -> 14:00 task
  assert.deepEqual(
    timeline.timedItems.map((i) => i.title),
    ['Write 2.0 Migration Spec', 'Team Standup', 'Review Beta Feedback']
  );
  assert.equal(timeline.timedItems[0].hasConflict, true);
  assert.deepEqual(timeline.timedItems[0].conflictTitles, ['Team Standup']);
  assert.equal(timeline.timedItems[1].hasConflict, true);
  assert.deepEqual(timeline.timedItems[1].conflictTitles, ['Write 2.0 Migration Spec']);
  assert.equal(timeline.timedItems[2].hasConflict, false);

  // Verify 5-language localization parity for v2.0 UI strings & Requires Attention / Review badges
  const forbiddenPattern = /stalled|unstall|stockend|stockt|bloqué|en pause|fermo|fermi|in stallo|estancad|⚠️/i;
  for (const locale of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const tr = TRANSLATIONS[locale];
    assert.ok(tr.weeklyReviewTitle.length > 0, `${locale} weeklyReviewTitle`);
    assert.ok(tr.templatesModalTitle.length > 0, `${locale} templatesModalTitle`);
    assert.ok(tr.todayModeTimeline.length > 0, `${locale} todayModeTimeline`);
    assert.ok(tr.settingsIncludeBetaTitle.length > 0, `${locale} settingsIncludeBetaTitle`);
    assert.ok(tr.projectStalledBadge.length > 0, `${locale} projectStalledBadge`);
    assert.ok(tr.projectRequiresReviewBadge.length > 0, `${locale} projectRequiresReviewBadge`);
    assert.notEqual(
      tr.projectStalledBadge,
      tr.projectRequiresReviewBadge,
      `${locale} no_next_action vs inactive_14d badges must be distinct`
    );

    for (const sampleStr of [
      tr.weeklyReviewSubtitle,
      tr.weeklyReviewStep3Title,
      tr.weeklyReviewStep3Desc,
      tr.weeklyReviewNoStalledProjects,
      tr.projectStalledBadge,
      tr.projectRequiresReviewBadge,
      tr.projectStalledNoActionHint,
      tr.projectStalledInactiveHint(16)
    ]) {
      assert.equal(
        forbiddenPattern.test(sampleStr),
        false,
        `Unexpected stalled/warning term in ${locale}: "${sampleStr}"`
      );
    }
  }

  assert.equal(TRANSLATIONS.en.projectStalledBadge, 'Requires attention');
  assert.equal(TRANSLATIONS.en.projectRequiresReviewBadge, 'Requires review');
  assert.equal(TRANSLATIONS.de.projectStalledBadge, 'Aufmerksamkeit nötig');
  assert.equal(TRANSLATIONS.de.projectRequiresReviewBadge, 'Überprüfung nötig');
  assert.equal(TRANSLATIONS.fr.projectStalledBadge, 'Attention requise');
  assert.equal(TRANSLATIONS.fr.projectRequiresReviewBadge, 'Révision requise');
  assert.equal(TRANSLATIONS.it.projectStalledBadge, 'Richiede attenzione');
  assert.equal(TRANSLATIONS.it.projectRequiresReviewBadge, 'Revisione richiesta');
  assert.equal(TRANSLATIONS.es.projectStalledBadge, 'Requiere atención');
  assert.equal(TRANSLATIONS.es.projectRequiresReviewBadge, 'Requiere revisión');
});

test('getStarterGtdTemplates localizes all 4 built-in templates (titles, notes, headings, tasks, and checklists) across en, de, fr, it, and es', () => {
  const enTemplates = getStarterGtdTemplates('en');
  assert.equal(STARTER_GTD_TEMPLATES, enTemplates);
  assert.equal(enTemplates.length, 4);

  const expectedLocalizedSamples = {
    en: {
      tripTitle: 'Trip & Packing',
      tripHeading: 'Documents & Essentials',
      tripTask: 'Confirm flight, train, and hotel reservations',
      tripChecklist: 'Save offline boarding passes',
      sprintTitle: 'Weekly Sprint',
      sprintChecklist: 'Outcome #1'
    },
    de: {
      tripTitle: 'Reise & Packliste',
      tripHeading: 'Dokumente & Wichtiges',
      tripTask: 'Flug-, Bahn- und Hotelbuchungen bestätigen',
      tripChecklist: 'Bordkarten offline speichern',
      sprintTitle: 'Wochen-Sprint',
      sprintChecklist: 'Ergebnis #1'
    },
    fr: {
      tripTitle: 'Voyage & Bagages',
      tripHeading: 'Documents & Essentiels',
      tripTask: "Confirmer les réservations de vol, train et d'hôtel",
      tripChecklist: "Enregistrer les cartes d'embarquement hors ligne",
      sprintTitle: 'Sprint hebdomadaire',
      sprintChecklist: 'Résultat n°1'
    },
    it: {
      tripTitle: 'Viaggio & Valigia',
      tripHeading: 'Documenti & Essenziali',
      tripTask: 'Confermare prenotazioni di volo, treno e hotel',
      tripChecklist: "Salvare carte d'imbarco offline",
      sprintTitle: 'Sprint settimanale',
      sprintChecklist: 'Risultato #1'
    },
    es: {
      tripTitle: 'Viaje y Equipaje',
      tripHeading: 'Documentos e Imprescindibles',
      tripTask: 'Confirmar reservas de vuelo, tren y hotel',
      tripChecklist: 'Guardar tarjetas de embarque sin conexión',
      sprintTitle: 'Sprint semanal',
      sprintChecklist: 'Resultado #1'
    }
  } as const;

  for (const locale of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const localized = getStarterGtdTemplates(locale);
    const exp = expectedLocalizedSamples[locale];
    assert.equal(localized.length, 4, `${locale} must expose all 4 starter templates`);

    const trip = localized.find((t) => t.id === 'starter-trip-packing')!;
    const sprint = localized.find((t) => t.id === 'starter-weekly-sprint')!;
    assert.equal(trip.defaultTitle, exp.tripTitle);
    assert.equal(trip.blueprint.headings[0].title, exp.tripHeading);
    assert.equal(trip.blueprint.tasks[0].title, exp.tripTask);
    assert.equal(trip.blueprint.tasks[0].checklistItems?.[0], exp.tripChecklist);
    assert.equal(sprint.defaultTitle, exp.sprintTitle);
    assert.equal(sprint.blueprint.headings[0].tasks[0].checklistItems?.[0], exp.sprintChecklist);

    // Structural parity with English templates (IDs, offsets, durations, task/checklist counts)
    for (let idx = 0; idx < enTemplates.length; idx++) {
      const baseTpl = enTemplates[idx];
      const locTpl = localized[idx];
      assert.equal(locTpl.id, baseTpl.id);
      assert.equal(locTpl.icon, baseTpl.icon);
      assert.ok(locTpl.defaultNotes.trim().length > 10, `${locale}:${locTpl.id} notes`);
      assert.equal(locTpl.blueprint.tasks.length, baseTpl.blueprint.tasks.length);
      assert.equal(locTpl.blueprint.headings.length, baseTpl.blueprint.headings.length);

      if (locale !== 'en') {
        assert.notEqual(
          locTpl.defaultNotes,
          baseTpl.defaultNotes,
          `${locale}:${locTpl.id} defaultNotes should be localized`
        );
      }

      for (let h = 0; h < baseTpl.blueprint.headings.length; h++) {
        const baseHd = baseTpl.blueprint.headings[h];
        const locHd = locTpl.blueprint.headings[h];
        assert.equal(locHd.tasks.length, baseHd.tasks.length);
        if (locale !== 'en') {
          assert.notEqual(
            locHd.title,
            baseHd.title,
            `${locale}:${locTpl.id} heading[${h}] should be localized`
          );
        }
        for (let tk = 0; tk < baseHd.tasks.length; tk++) {
          const baseTask = baseHd.tasks[tk];
          const locTask = locHd.tasks[tk];
          assert.equal(locTask.whenDaysOffset, baseTask.whenDaysOffset);
          assert.equal(locTask.deadlineDaysOffset, baseTask.deadlineDaysOffset);
          assert.equal(locTask.scheduledStartMinutes, baseTask.scheduledStartMinutes);
          assert.equal(locTask.durationMinutes, baseTask.durationMinutes);
          assert.equal(
            locTask.checklistItems?.length ?? 0,
            baseTask.checklistItems?.length ?? 0
          );
          if (locale !== 'en') {
            assert.notEqual(
              locTask.title,
              baseTask.title,
              `${locale}:${locTpl.id} heading[${h}].task[${tk}] should be localized`
            );
          }
        }
      }
    }
  }
});

test('v2.0.0-beta.7 cross-device project review sync unifies 14-day review anchor and merges lastReviewedAt monotonically', () => {
  const dayMs = 86_400_000;
  const now = 1_785_000_000_000;

  // 1. Editing a child task does NOT mask an unreviewed 16-day-old project (unified with Android & macOS)
  const unreviewedProj: ProjectRecord = {
    id: 'p-cross-sync',
    areaId: null,
    title: 'Cross-Device Project',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    lastReviewedAt: now - 16 * dayMs,
    sortOrder: 100,
    tagIds: [],
    createdAt: now - 30 * dayMs,
    updatedAt: now - 2 * dayMs,
    isDeleted: false,
    isDirty: false
  };
  const recentChildTask: TaskRecord = {
    id: 'tk-recent-child',
    title: 'Recently edited task',
    notes: '',
    status: 'OPEN',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    scheduledStartMinutes: null,
    durationMinutes: null,
    completedAt: null,
    projectId: unreviewedProj.id,
    headingId: null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: 100,
    tagIds: [],
    createdAt: now - 10 * dayMs,
    updatedAt: now - 1 * dayMs,
    isDeleted: false,
    isDirty: false
  };

  const beforeSyncEval = evaluateProjectStalledStatus(unreviewedProj, [recentChildTask], now);
  assert.equal(beforeSyncEval.isStalled, true);
  assert.equal(beforeSyncEval.reason, 'inactive_14d');

  // 2. Reviewing the project on another device syncs lastReviewedAt = now and clears the review prompt
  const mergedReview = mergeMonotonicReviewTimestamp(unreviewedProj.lastReviewedAt, now);
  assert.equal(mergedReview, now);
  assert.equal(mergeMonotonicReviewTimestamp(now, null), now);
  assert.equal(mergeMonotonicReviewTimestamp(now, now - 15 * dayMs), now);

  const syncedProjOnSecondDevice: ProjectRecord = {
    ...unreviewedProj,
    lastReviewedAt: mergedReview,
    updatedAt: now
  };
  const afterSyncEval = evaluateProjectStalledStatus(
    syncedProjOnSecondDevice,
    [recentChildTask],
    now + 5_000
  );
  assert.equal(afterSyncEval.isStalled, false);
  assert.equal(afterSyncEval.reason, null);
});

test('ChromeOS 12-item task context menu clamps cleanly inside viewport across top, bottom, right, and split-screen heights', () => {
  const menuWidth = 248;
  const menuHeightDe = 498; // Full 12-item context menu with wrapped German label ("Aufgabe auswählen (Mehrfachauswahl)")

  // 1. Top-area click: opens directly at cursor without shifting
  const topClick = computeClampedContextMenuPosition(420, 160, menuWidth, menuHeightDe, 1366, 768, 12);
  assert.deepEqual(topClick, { left: 420, top: 160 });
  assert.ok(topClick.top + menuHeightDe <= 768 - 12);

  // 2. Lower/middle-area click on a 768px ChromeOS display: shifts upward so Cancel & Delete remain 100% visible
  const lowerClick = computeClampedContextMenuPosition(460, 520, menuWidth, menuHeightDe, 1366, 768, 12);
  assert.equal(lowerClick.left, 460);
  assert.equal(lowerClick.top, 768 - menuHeightDe - 12);
  assert.equal(lowerClick.top + menuHeightDe, 756);

  // 3. Right-edge + bottom-edge click: clamps both X and Y with 12px margin
  const cornerClick = computeClampedContextMenuPosition(1340, 740, menuWidth, menuHeightDe, 1366, 768, 12);
  assert.equal(cornerClick.left, 1366 - menuWidth - 12);
  assert.equal(cornerClick.top, 768 - menuHeightDe - 12);

  // 4. Compact split-screen window shorter than natural menu height: pins cleanly to top margin (paired with CSS max-height scroll)
  const compactSplit = computeClampedContextMenuPosition(200, 310, menuWidth, 396, 680, 420, 12);
  assert.deepEqual(compactSplit, { left: 200, top: 12 });
});

test('Quick Find search filters accurately filter tasks, projects, dates, deadlines, checklists, tags, scope, and all 5 languages', () => {
  const refDate = new Date(2026, 10, 15, 12, 0, 0, 0).getTime(); // Nov 15, 2026 local noon
  const todayStart = new Date(2026, 10, 15, 0, 0, 0, 0).getTime();
  const dayMs = 86_400_000;

  assert.equal(hasActiveQuickFindFilters(DEFAULT_QUICK_FIND_FILTER), false);
  assert.equal(shouldShowSmartViewsAndAreasInQuickFind('Today', DEFAULT_QUICK_FIND_FILTER), true);
  assert.equal(
    shouldShowSmartViewsAndAreasInQuickFind('', { ...DEFAULT_QUICK_FIND_FILTER, hideCompleted: true }),
    false
  );
  assert.equal(
    shouldShowSmartViewsAndAreasInQuickFind('Tod', { ...DEFAULT_QUICK_FIND_FILTER, hideCompleted: true }),
    true
  );
  assert.equal(
    shouldShowSmartViewsAndAreasInQuickFind('Tod', { ...DEFAULT_QUICK_FIND_FILTER, scope: 'TASKS' }),
    false
  );

  const openTodayTask = {
    status: 'OPEN' as const,
    scheduleState: 'TODAY' as const,
    whenDate: todayStart,
    deadline: todayStart + 2 * dayMs,
    tagIds: ['tag-urgent'],
    isDeleted: false
  };
  const overdueTask = {
    status: 'OPEN' as const,
    scheduleState: 'UPCOMING' as const,
    whenDate: todayStart - 2 * dayMs,
    deadline: todayStart - dayMs,
    tagIds: ['tag-home'],
    isDeleted: false
  };
  const noDateTask = {
    status: 'OPEN' as const,
    scheduleState: 'SOMEDAY' as const,
    whenDate: null,
    deadline: null,
    tagIds: [] as string[],
    isDeleted: false
  };
  const completedTask = {
    status: 'COMPLETED' as const,
    scheduleState: 'ANYTIME' as const,
    whenDate: null,
    deadline: null,
    tagIds: ['tag-urgent'],
    isDeleted: false
  };

  const activeProject = {
    status: 'ACTIVE' as const,
    scheduleState: 'ANYTIME' as const,
    whenDate: null,
    deadline: todayStart + 4 * dayMs,
    tagIds: ['tag-urgent'],
    isDeleted: false
  };
  const completedProject = {
    status: 'COMPLETED' as const,
    scheduleState: 'ANYTIME' as const,
    whenDate: null,
    deadline: null,
    tagIds: [] as string[],
    isDeleted: false
  };

  // Hide completed
  const hideCompletedFilter = { ...DEFAULT_QUICK_FIND_FILTER, hideCompleted: true };
  assert.equal(matchesQuickFindTask(openTodayTask, hideCompletedFilter, true, refDate), true);
  assert.equal(matchesQuickFindTask(completedTask, hideCompletedFilter, false, refDate), false);
  assert.equal(matchesQuickFindProject(activeProject, hideCompletedFilter, refDate), true);
  assert.equal(matchesQuickFindProject(completedProject, hideCompletedFilter, refDate), false);

  // Date filters: TODAY, OVERDUE, NEXT_7_DAYS, THIS_MONTH, NO_DATE
  assert.equal(
    matchesQuickFindTask(openTodayTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'TODAY' }, true, refDate),
    true
  );
  assert.equal(
    matchesQuickFindTask(overdueTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'TODAY' }, false, refDate),
    false
  );
  assert.equal(
    matchesQuickFindTask(overdueTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'OVERDUE' }, false, refDate),
    true
  );
  assert.equal(
    matchesQuickFindProject(activeProject, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'NEXT_7_DAYS' }, refDate),
    true
  );
  assert.equal(
    matchesQuickFindTask(overdueTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'THIS_MONTH' }, false, refDate),
    true
  );
  assert.equal(
    matchesQuickFindTask(noDateTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'NO_DATE' }, false, refDate),
    true
  );
  assert.equal(
    matchesQuickFindTask(openTodayTask, { ...DEFAULT_QUICK_FIND_FILTER, dateFilter: 'NO_DATE' }, true, refDate),
    false
  );

  // Deadline, Checklist, Tag, and Scope filters
  assert.equal(
    matchesQuickFindTask(openTodayTask, { ...DEFAULT_QUICK_FIND_FILTER, hasChecklistOnly: true }, true, refDate),
    true
  );
  assert.equal(
    matchesQuickFindTask(openTodayTask, { ...DEFAULT_QUICK_FIND_FILTER, hasChecklistOnly: true }, false, refDate),
    false
  );
  assert.equal(
    matchesQuickFindProject(activeProject, { ...DEFAULT_QUICK_FIND_FILTER, hasChecklistOnly: true }, refDate),
    false
  );
  assert.equal(
    matchesQuickFindProject(
      activeProject,
      { ...DEFAULT_QUICK_FIND_FILTER, hasDeadlineOnly: true, tagId: 'tag-urgent', scope: 'PROJECTS' },
      refDate
    ),
    true
  );
  assert.equal(
    matchesQuickFindTask(
      openTodayTask,
      { ...DEFAULT_QUICK_FIND_FILTER, scope: 'PROJECTS' },
      true,
      refDate
    ),
    false
  );

  // Verify all 14 searchFilter* keys exist and are non-empty across EN, DE, FR, IT, ES
  const filterKeys = [
    'searchFilterHideCompleted',
    'searchFilterDateAll',
    'searchFilterDateToday',
    'searchFilterDateOverdue',
    'searchFilterDateNext7Days',
    'searchFilterDateThisMonth',
    'searchFilterDateNone',
    'searchFilterHasDeadline',
    'searchFilterHasChecklist',
    'searchFilterAllTags',
    'searchFilterScopeAll',
    'searchFilterScopeTasks',
    'searchFilterScopeProjects',
    'searchFilterReset'
  ] as const;
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    for (const key of filterKeys) {
      assert.ok(TRANSLATIONS[lang][key].trim().length > 0, `Missing ${key} in ${lang}`);
    }
  }
});

test('v3.0.0-alpha.2 Project Phases endDate preserves 100% v2.x backup, sync, and template compatibility', async () => {
  const refDay = new Date(2026, 10, 19, 0, 0, 0, 0).getTime();
  const phaseEndMillis = new Date(2026, 10, 26, 0, 0, 0, 0).getTime();

  const project: ProjectRecord = {
    id: 'proj-alpha2',
    areaId: null,
    title: 'Alpha 2 Rollout',
    notes: 'Phase milestone verification',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    lastReviewedAt: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: refDay,
    updatedAt: refDay,
    isDeleted: false,
    isDirty: false
  };

  // 1. Legacy v2.x .tbbackup headings without endDate normalize cleanly to endDate: null
  const legacyV2Payload = {
    schemaVersion: 1,
    createdAt: refDay,
    appVersionName: '2.1.0-chromeos',
    projects: [
      {
        id: 'proj-alpha2',
        areaId: null,
        title: 'Alpha 2 Rollout',
        notes: 'Phase milestone verification',
        status: 'ACTIVE',
        scheduleState: 'ANYTIME',
        whenDate: null,
        deadline: null,
        completedAt: null,
        sortOrder: 1000,
        tagIdsJson: '[]',
        createdAt: refDay,
        updatedAt: refDay
      }
    ],
    headings: [
      {
        id: 'phase-v2-legacy',
        projectId: 'proj-alpha2',
        title: 'Legacy Heading',
        sortOrder: 1000,
        isArchived: false,
        createdAt: refDay,
        updatedAt: refDay
      }
    ]
  } as unknown as ThingsbookBackupPayload;
  const legacyBytes = await encodeThingsBackup(legacyV2Payload, null, '2.1.0-chromeos', 73);
  const decodedLegacyPayload = await decodeThingsBackup(legacyBytes, null);
  const decodedLegacy = normalizeBackupPayloadToEntities(decodedLegacyPayload, false, refDay);
  assert.equal(decodedLegacy.headings.length, 1);
  assert.equal(decodedLegacy.headings[0].id, 'phase-v2-legacy');
  assert.equal(decodedLegacy.headings[0].endDate, null);

  // 2. v3.0 .tbbackup headings keep wire key `headings` and roundtrip `endDate` losslessly
  const v3Heading: HeadingRecord = {
    id: 'phase-v3-1',
    projectId: 'proj-alpha2',
    title: 'Design Freeze',
    endDate: phaseEndMillis,
    sortOrder: 1000,
    isArchived: false,
    createdAt: refDay,
    updatedAt: refDay,
    isDeleted: false,
    isDirty: false
  };
  const v3Payload = buildBackupPayloadFromEntities({
    appVersionName: '3.0.0-alpha.2-chromeos',
    areas: [],
    projects: [project],
    headings: [v3Heading],
    tasks: [],
    checklistItems: [],
    tags: [],
    attachments: [],
    projectTemplates: []
  });
  const rawV3Obj = v3Payload as unknown as Record<string, unknown>;
  assert.ok(Array.isArray(rawV3Obj.headings), 'Backup wire format must retain `headings` key for v2.x compatibility');
  assert.equal('phases' in rawV3Obj, false);
  const v3Bytes = await encodeThingsBackup(v3Payload, null, '3.0.0-alpha.2-chromeos', 75);
  const decodedV3Payload = await decodeThingsBackup(v3Bytes, null);
  const roundtrippedV3 = normalizeBackupPayloadToEntities(decodedV3Payload, false, refDay);
  assert.equal(roundtrippedV3.headings[0].endDate, phaseEndMillis);

  // 3. Cloud sync delta guard preserves local endDate when a v2.x client omits `endDate`
  assert.equal(
    resolveMergedHeadingEndDate(
      { endDate: phaseEndMillis },
      { endDate: null, isDeleted: false, omittedEndDate: true }
    ),
    phaseEndMillis
  );
  // Explicit v3.0 clear (`omittedEndDate: false` / undefined with `endDate: null`) clears `endDate`
  assert.equal(
    resolveMergedHeadingEndDate(
      { endDate: phaseEndMillis },
      { endDate: null, isDeleted: false, omittedEndDate: false }
    ),
    null
  );
  // Explicit v3.0 update applies new timestamp
  const updatedEndMillis = phaseEndMillis + 86_400_000 * 3;
  assert.equal(
    resolveMergedHeadingEndDate(
      { endDate: phaseEndMillis },
      { endDate: updatedEndMillis, isDeleted: false, omittedEndDate: false }
    ),
    updatedEndMillis
  );
  // Deleted remote heading does not resurrect stale endDate
  assert.equal(
    resolveMergedHeadingEndDate(
      { endDate: phaseEndMillis },
      { endDate: null, isDeleted: true, omittedEndDate: true }
    ),
    null
  );

  // 4. Project template blueprints capture `endDateDayOffset`, serialize under `headings`, and accept `phases` alias
  const blueprint = buildProjectTemplateBlueprint(project, [v3Heading], [], [], refDay);
  assert.equal(blueprint.headings.length, 1);
  assert.equal(blueprint.headings[0].title, 'Design Freeze');
  assert.equal(blueprint.headings[0].endDateDayOffset, 7);

  const parsedFromPhasesAlias = parseTemplateBlueprintJson(
    JSON.stringify({
      version: 1,
      phases: [
        {
          title: 'QA Sign-off',
          endDateDayOffset: 14,
          tasks: [{ title: 'Verify v2.x sync roundtrip' }]
        }
      ]
    })
  );
  assert.equal(parsedFromPhasesAlias.headings.length, 1);
  assert.equal(parsedFromPhasesAlias.headings[0].title, 'QA Sign-off');
  assert.equal(parsedFromPhasesAlias.headings[0].endDateDayOffset, 14);
  assert.equal(parsedFromPhasesAlias.headings[0].tasks[0].title, 'Verify v2.x sync roundtrip');

  // 5. Classic GTD Smart View labels + Phase i18n strings exist across EN, DE, FR, IT, ES
  assert.equal(TRANSLATIONS.en.viewInbox, 'Inbox');
  assert.equal(TRANSLATIONS.en.viewToday, 'Today');
  assert.equal(TRANSLATIONS.en.viewUpcoming, 'Upcoming');
  assert.equal(TRANSLATIONS.en.viewAnytime, 'Anytime');
  assert.equal(TRANSLATIONS.en.viewSomeday, 'Someday');
  assert.equal(TRANSLATIONS.en.viewLogbook, 'Logbook');
  assert.equal(TRANSLATIONS.de.viewInbox, 'Eingang');
  assert.equal(TRANSLATIONS.de.viewToday, 'Heute');
  assert.equal(TRANSLATIONS.de.viewUpcoming, 'Geplant');
  assert.equal(TRANSLATIONS.de.viewAnytime, 'Jederzeit');
  assert.equal(TRANSLATIONS.de.viewSomeday, 'Irgendwann');

  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    assert.ok(TRANSLATIONS[lang].entityHeading.trim().length > 0, `Missing entityHeading in ${lang}`);
    assert.ok(TRANSLATIONS[lang].entityNewHeading.trim().length > 0, `Missing entityNewHeading in ${lang}`);
    assert.ok(TRANSLATIONS[lang].phaseSetEndDate.trim().length > 0, `Missing phaseSetEndDate in ${lang}`);
    assert.ok(TRANSLATIONS[lang].phaseClearEndDate.trim().length > 0, `Missing phaseClearEndDate in ${lang}`);
    assert.ok(TRANSLATIONS[lang].phaseEndsOn('2026-11-26').includes('2026-11-26'), `Invalid phaseEndsOn in ${lang}`);
    assert.ok(TRANSLATIONS[lang].phaseMilestoneBadge.trim().length > 0, `Missing phaseMilestoneBadge in ${lang}`);
  }
});



