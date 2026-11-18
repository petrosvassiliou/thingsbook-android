import test from 'node:test';
import assert from 'node:assert/strict';
import {
  APP_BUILD_CODE,
  APP_VERSION,
  filterActiveOrUpcomingCalendarEvents,
  localEpochDayFromMillis,
  type CalendarEventItem
} from '../src/domain/types.ts';
import {
  APP_BUILD_CODE as buildFromUtils,
  APP_VERSION as versionFromUtils,
  compareSemver as compareFromUtils,
  isEligibleReleasePayload as isEligibleFromUtils,
  isPrereleaseVersion,
  selectLatestEligibleRelease as selectFromUtils
} from '../src/data/versionUtils.ts';
import {
  compareSemver,
  isEligibleReleasePayload,
  selectLatestEligibleRelease
} from '../src/data/pwaUpdater.ts';
import { resolveMagicPlusDrop } from '../src/domain/magicPlusDrop.ts';
import {
  buildLocalizedSeedBundle,
  isActiveSampleTask,
  isPurgeableSampleTask
} from '../src/data/seedData.ts';
import {
  shouldAutoFocusTitleOnExpand,
  shouldShowQuickThisEveningPill
} from '../src/domain/v2Utils.ts';

test('v2.1.0 release build metadata constants are promoted to 2.1.0 (Build 73, non-prerelease)', () => {
  assert.equal(APP_VERSION, '2.1.0');
  assert.equal(APP_BUILD_CODE, 73);
  assert.equal(versionFromUtils, '2.1.0');
  assert.equal(buildFromUtils, 73);
  assert.equal(isPrereleaseVersion(APP_VERSION), false);
  assert.equal(isPrereleaseVersion('1.8.1'), false);
  assert.equal(isPrereleaseVersion('2.0.0'), false);
  assert.equal(isPrereleaseVersion('2.0.0-beta.26'), true);
  assert.equal(compareSemver('2.1.0', '2.0.1'), 1);
  assert.equal(compareSemver('2.0.1', '2.0.0'), 1);
  assert.equal(compareSemver('2.0.0', '2.0.0-beta.26'), 1);
  assert.equal(compareSemver, compareFromUtils);
  assert.equal(isEligibleReleasePayload, isEligibleFromUtils);
  assert.equal(selectLatestEligibleRelease, selectFromUtils);
});

test('compareSemver orders numeric pre-release identifiers (beta.1 < beta.2 < beta.3 < beta.10 < beta.11 < 2.0.0) and +build metadata accurately', () => {
  const ordered = [
    '1.7.9',
    '1.8.0-alpha.1',
    '1.8.0-beta.1',
    '1.8.0-rc.1',
    '1.8.0',
    '1.8.0+1',
    '1.8.0+2',
    '1.8.1-beta.1',
    '1.8.1',
    '1.8.1+1',
    '1.8.2-beta.1',
    '1.8.2',
    '2.0.0-alpha.1',
    '2.0.0-beta.1',
    '2.0.0-beta.2',
    '2.0.0-beta.3',
    '2.0.0-beta.10',
    '2.0.0-beta.11',
    '2.0.0-beta.99',
    '2.0.0-rc.1',
    '2.0.0-rc.2',
    '2.0.0',
    '2.0.0+1'
  ];

  for (let i = 0; i < ordered.length; i++) {
    assert.equal(compareSemver(ordered[i], ordered[i]), 0, `${ordered[i]} == ${ordered[i]}`);
    for (let j = i + 1; j < ordered.length; j++) {
      assert.equal(
        compareSemver(ordered[i], ordered[j]),
        -1,
        `Expected ${ordered[i]} < ${ordered[j]}`
      );
      assert.equal(
        compareSemver(ordered[j], ordered[i]),
        1,
        `Expected ${ordered[j]} > ${ordered[i]}`
      );
    }
  }

  // Explicit pre-release numeric & upgrade checks
  assert.equal(compareSemver('2.0.0-beta.2', '2.0.0-beta.1'), 1);
  assert.equal(compareSemver('2.0.0-beta.10', '2.0.0-beta.2'), 1);
  assert.equal(compareSemver('2.0.0', '2.0.0-beta.99'), 1);
  assert.equal(compareSemver('2.0.0-beta.1', '1.8.1'), 1);
  assert.equal(compareSemver('v2.0.0-beta.1', '2.0.0-beta.1'), 0);
});

test('isEligibleReleasePayload with includeBeta=false strictly isolates stable 1.8.1 from 2.0-beta/rc/alpha, drafts, and non-v tags', () => {
  // Stable 1.8.1 without opt-in (default includeBeta === false)
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: true },
      '1.8.1',
      false
    ),
    false
  );
  // Reject 2.0 pre-release tags even if GitHub API marks prerelease: false
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-alpha.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-rc.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'V2.0.0-beta.2+45', draft: false, prerelease: false },
      '1.8.1+44'
    ),
    false
  );

  // Reject macOS / non-v tags & drafts
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'macos-build-12', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: true, prerelease: false }, '1.8.1'),
    false
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: false, prerelease: true }, '1.8.1'),
    false
  );
  assert.equal(isEligibleReleasePayload(null, '1.8.1'), false);
  assert.equal(isEligibleReleasePayload({}, '1.8.1'), false);

  // Accept genuine stable releases (including 2.0.0 final!)
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: false, prerelease: false }, '1.8.1'),
    true
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'V2.0.0', draft: false, prerelease: false }, '1.8.1'),
    true
  );
});

test('isEligibleReleasePayload and selectLatestEligibleRelease support includeBeta=true for 2.0 beta channel & opt-in upgrades', () => {
  // Running 2.0.0-beta.1 defaults includeBeta to true automatically
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.2', draft: false, prerelease: true },
      '2.0.0-beta.1'
    ),
    true
  );
  // Stable 1.8.1 user opting into "Include 2.0 Beta Updates" (includeBeta = true)
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: true },
      '1.8.1',
      true
    ),
    true
  );
  // Still strictly rejects drafts and macos-build-* even when includeBeta === true!
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.3', draft: true, prerelease: true },
      '2.0.0-beta.1',
      true
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'macos-build-45', draft: false, prerelease: true },
      '2.0.0-beta.1',
      true
    ),
    false
  );

  const releasesFeed = [
    { tag_name: 'v2.0.0-beta.11', draft: true, prerelease: true },
    { tag_name: 'macos-build-99', draft: false, prerelease: true },
    { tag_name: 'v2.0.0-beta.2', draft: false, prerelease: true },
    { tag_name: 'v2.0.0-beta.10', draft: false, prerelease: true },
    { tag_name: 'v1.8.1', draft: false, prerelease: false }
  ];

  // Stable 1.8.1 without beta opt-in picks v1.8.1 (ignores beta.2 & beta.10)
  const pickedStable = selectLatestEligibleRelease(releasesFeed, '1.8.1', false);
  assert.equal(pickedStable?.tag_name, 'v1.8.1');

  // Stable 1.8.1 WITH beta opt-in picks v2.0.0-beta.10 (numeric 10 > 2, skips draft beta.11)
  const pickedOptIn = selectLatestEligibleRelease(releasesFeed, '1.8.1', true);
  assert.equal(pickedOptIn?.tag_name, 'v2.0.0-beta.10');

  // Once final v2.0.0 ships, both beta and stable channels upgrade to v2.0.0!
  const feedWithFinal = [
    { tag_name: 'v2.0.0', draft: false, prerelease: false },
    ...releasesFeed
  ];
  assert.equal(selectLatestEligibleRelease(feedWithFinal, '2.0.0-beta.10', true)?.tag_name, 'v2.0.0');
  assert.equal(selectLatestEligibleRelease(feedWithFinal, '1.8.1', false)?.tag_name, 'v2.0.0');
});

test('v2.0.0-beta.17 resolveMagicPlusDrop resolves To-Do slots, Evening/Upcoming context, and Project Heading left gutter', () => {
  const taskBoxes = [
    {
      taskId: 't1',
      sortOrder: 1000,
      headingId: null,
      evening: false,
      whenDate: null,
      top: 120,
      bottom: 164
    },
    {
      taskId: 't2',
      sortOrder: 2000,
      headingId: null,
      evening: false,
      whenDate: null,
      top: 170,
      bottom: 214
    },
    {
      taskId: 't3',
      sortOrder: 500,
      headingId: 'h1',
      evening: false,
      whenDate: null,
      top: 280,
      bottom: 324
    }
  ];
  const headingBoxes = [
    {
      headingId: 'h1',
      sortOrder: 1000,
      top: 236,
      bottom: 272,
      sectionBottom: 332
    },
    {
      headingId: 'h2',
      sortOrder: 2000,
      top: 348,
      bottom: 384,
      sectionBottom: 390
    }
  ];

  // Between t1 and t2 in center of list -> To-Do midpoint
  const midTodo = resolveMagicPlusDrop({
    clientX: 420,
    clientY: 168,
    isProjectView: true,
    containerLeft: 200,
    containerWidth: 640,
    containerTop: 80,
    taskBoxes,
    headingBoxes
  });
  assert.equal(midTodo.mode, 'todo');
  assert.equal(midTodo.prevTaskOrder, 1000);
  assert.equal(midTodo.nextTaskOrder, 2000);
  assert.equal(midTodo.headingId, null);

  // Inside empty heading h2 -> To-Do assigned to h2
  const emptyHeadingTodo = resolveMagicPlusDrop({
    clientX: 420,
    clientY: 376,
    isProjectView: true,
    containerLeft: 200,
    containerWidth: 640,
    containerTop: 80,
    taskBoxes,
    headingBoxes
  });
  assert.equal(emptyHeadingTodo.mode, 'todo');
  assert.equal(emptyHeadingTodo.headingId, 'h2');
  assert.equal(emptyHeadingTodo.prevTaskOrder, null);
  assert.equal(emptyHeadingTodo.nextTaskOrder, null);

  // Left gutter inside Project (clientX <= containerLeft + 88) -> Heading between h1 and h2
  const betweenHeadings = resolveMagicPlusDrop({
    clientX: 240,
    clientY: 340,
    isProjectView: true,
    containerLeft: 200,
    containerWidth: 640,
    containerTop: 80,
    taskBoxes,
    headingBoxes
  });
  assert.equal(betweenHeadings.mode, 'heading');
  assert.equal(betweenHeadings.prevHeadingOrder, 1000);
  assert.equal(betweenHeadings.nextHeadingOrder, 2000);

  // Left gutter outside Project -> still To-Do (never Heading)
  const nonProjectLeft = resolveMagicPlusDrop({
    clientX: 220,
    clientY: 130,
    isProjectView: false,
    containerLeft: 200,
    containerWidth: 640,
    containerTop: 80,
    taskBoxes: [
      {
        taskId: 'eve1',
        sortOrder: 3000,
        headingId: null,
        evening: true,
        whenDate: null,
        top: 120,
        bottom: 164
      }
    ],
    headingBoxes: []
  });
  assert.equal(nonProjectLeft.mode, 'todo');
  assert.equal(nonProjectLeft.evening, true);
  assert.equal(nonProjectLeft.prevTaskOrder, null);
  assert.equal(nonProjectLeft.nextTaskOrder, 3000);
});

test('v2.0.0-beta.19 filterActiveOrUpcomingCalendarEvents removes past timed and past-day appointments from Today and Planned views', () => {
  const nowLocal = new Date(2026, 2, 18, 17, 30, 0, 0).getTime(); // Local 17:30 on 2026-03-18
  const todayDay = localEpochDayFromMillis(nowLocal);
  const yesterdayDay = todayDay - 1;
  const tomorrowDay = todayDay + 1;

  const makeEvent = (
    eventId: string,
    title: string,
    startMillis: number,
    endMillis: number,
    isAllDay = false
  ): CalendarEventItem => ({
    eventId,
    calendarId: 'cal-1',
    calendarName: 'Work',
    calendarColorCss: '#2e7cf6',
    title,
    startMillis,
    endMillis,
    isAllDay,
    location: null,
    htmlLink: null
  });

  const yesterdayTimed = makeEvent(
    'ev-yesterday',
    'Yesterday Sync',
    new Date(2026, 2, 17, 10, 0).getTime(),
    new Date(2026, 2, 17, 11, 0).getTime()
  );
  const endedMorningToday = makeEvent(
    'ev-ended-today',
    'Morning Standup',
    new Date(2026, 2, 18, 9, 0).getTime(),
    new Date(2026, 2, 18, 9, 30).getTime()
  );
  const endedExactBoundary = makeEvent(
    'ev-ended-boundary',
    'Ended Right Now',
    new Date(2026, 2, 18, 16, 30).getTime(),
    nowLocal
  );
  const ongoingToday = makeEvent(
    'ev-ongoing-today',
    'Design Review',
    new Date(2026, 2, 18, 17, 0).getTime(),
    new Date(2026, 2, 18, 18, 0).getTime()
  );
  const futureEveningToday = makeEvent(
    'ev-future-today',
    'Dinner',
    new Date(2026, 2, 18, 19, 30).getTime(),
    new Date(2026, 2, 18, 21, 0).getTime()
  );
  // UTC midnight-to-midnight all-day event on 2026-03-18 (even after 16:00 PST, must stay visible all day)
  const utcAllDayToday = makeEvent(
    'ev-allday-today',
    'Company Offsite',
    Date.UTC(2026, 2, 18, 0, 0, 0),
    Date.UTC(2026, 2, 19, 0, 0, 0),
    true
  );
  const tomorrowMorning = makeEvent(
    'ev-tomorrow',
    'Tomorrow Planning',
    new Date(2026, 2, 19, 10, 0).getTime(),
    new Date(2026, 2, 19, 11, 0).getTime()
  );

  const allEvents = [
    yesterdayTimed,
    endedMorningToday,
    endedExactBoundary,
    ongoingToday,
    futureEveningToday,
    utcAllDayToday,
    tomorrowMorning
  ];

  // Today view (upcomingOnly = false): drops yesterday + ended today, keeps ongoing, future today, and today's all-day
  const todayVisible = filterActiveOrUpcomingCalendarEvents(
    allEvents,
    todayDay,
    todayDay,
    nowLocal,
    false
  );
  assert.deepEqual(
    todayVisible.map((e) => e.eventId),
    ['ev-ongoing-today', 'ev-future-today', 'ev-allday-today']
  );

  // Past day query always returns empty
  assert.deepEqual(
    filterActiveOrUpcomingCalendarEvents(allEvents, yesterdayDay, todayDay, nowLocal, false),
    []
  );

  // Planned / Upcoming view (upcomingOnly = true): strictly rejects yesterday & today, keeps tomorrow's future event
  assert.deepEqual(
    filterActiveOrUpcomingCalendarEvents(allEvents, yesterdayDay, todayDay, nowLocal, true),
    []
  );
  assert.deepEqual(
    filterActiveOrUpcomingCalendarEvents(allEvents, todayDay, todayDay, nowLocal, true),
    []
  );
  const tomorrowVisible = filterActiveOrUpcomingCalendarEvents(
    allEvents,
    tomorrowDay,
    todayDay,
    nowLocal,
    true
  );
  assert.deepEqual(
    tomorrowVisible.map((e) => e.eventId),
    ['ev-tomorrow']
  );
});

test('v2.0.0-beta.24 sample task detection ignores Logbook completed items and preserves user-renamed tasks across all 5 languages', () => {
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const bundle = buildLocalizedSeedBundle(lang);
    assert.equal(bundle.tasks.length, 8);
    assert.equal(bundle.tasks.some((t) => isActiveSampleTask(t)), true);

    const logbookSample = bundle.tasks.find((t) => t.id === 'task-completed-008')!;
    assert.equal(logbookSample.status, 'COMPLETED');
    assert.equal(isActiveSampleTask(logbookSample), false);
    assert.equal(isPurgeableSampleTask(logbookSample), true);

    // When all 7 open sample tasks are completed or deleted and only Logbook sample task remains, hasSampleTasks is false
    const allCompleted = bundle.tasks.map((t) => ({ ...t, status: 'COMPLETED' as const }));
    assert.equal(allCompleted.some((t) => isActiveSampleTask(t)), false);

    // Renaming a sample task into a personal task removes it from both active sample detection and sample purge
    const inboxSample = bundle.tasks.find((t) => t.id === 'task-inbox-001')!;
    const renamedTask = { ...inboxSample, title: 'Steuererklärung für 2026 einreichen' };
    assert.equal(isActiveSampleTask(renamedTask), false);
    assert.equal(isPurgeableSampleTask(renamedTask), false);
  }
});

test('v2.0.0-beta.25 suppresses auto-selection on existing tasks and scopes 1-click This Evening pill strictly to Today view', () => {
  assert.equal(shouldAutoFocusTitleOnExpand(''), true);
  assert.equal(shouldAutoFocusTitleOnExpand('   '), true);
  assert.equal(shouldAutoFocusTitleOnExpand('Buy groceries'), false);

  assert.equal(
    shouldShowQuickThisEveningPill({
      isTodayView: true,
      status: 'OPEN',
      scheduleState: 'TODAY'
    }),
    true
  );
  assert.equal(
    shouldShowQuickThisEveningPill({
      isTodayView: true,
      status: 'OPEN',
      scheduleState: 'THIS_EVENING'
    }),
    false
  );
  assert.equal(
    shouldShowQuickThisEveningPill({
      isTodayView: false,
      status: 'OPEN',
      scheduleState: 'TODAY'
    }),
    false
  );
  assert.equal(
    shouldShowQuickThisEveningPill({
      isTodayView: true,
      status: 'COMPLETED',
      scheduleState: 'TODAY'
    }),
    false
  );
});

test('v2.1.0 Option B unified inline editor localizes actionMoreOptions & actionTakePhoto across all 5 languages', async () => {
  const { TRANSLATIONS } = await import('../src/i18n/translations.ts');
  const fs = await import('node:fs');
  const path = await import('node:path');
  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    assert.equal(typeof TRANSLATIONS[lang].actionMoreOptions, 'string');
    assert.ok(TRANSLATIONS[lang].actionMoreOptions.trim().length > 0);
    assert.equal(typeof TRANSLATIONS[lang].actionTakePhoto, 'string');
    assert.ok(TRANSLATIONS[lang].actionTakePhoto.trim().length > 0);
  }
  const taskItemSrc = fs.readFileSync(
    path.resolve(import.meta.dirname, '../src/components/TaskItem.tsx'),
    'utf8'
  );
  assert.ok(taskItemSrc.includes('task-card-unified-footer'));
  assert.ok(taskItemSrc.includes('checklist-add-boxed'));
  assert.ok(!taskItemSrc.includes('task-card-action-bar'));
});



