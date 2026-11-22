import test from 'node:test';
import assert from 'node:assert/strict';
import type { IDBPDatabase } from 'idb';
import {
  DEFAULT_INITIAL_DELAY_MS,
  DEFAULT_WARM_HANDOFF_WINDOW_MS,
  TooltipTimingMachine,
  computeTooltipPosition,
  isTimelineMouseTooltipAllowed,
  isTopBarIconTooltipAllowed
} from '../src/domain/tooltipTiming.ts';
import { ThingsLocalDb } from '../src/data/localDb.ts';
import { importAsanaJson } from '../src/data/externalProjectCodec.ts';
import type { HeadingRecord, ProjectRecord, TaskRecord } from '../src/domain/types.ts';

test('TooltipTimingMachine enforces 160ms cold delay and 0ms warm handoff across adjacent controls', () => {
  const machine = new TooltipTimingMachine();
  assert.equal(machine.initialDelayMs, DEFAULT_INITIAL_DELAY_MS);
  assert.equal(machine.warmHandoffWindowMs, DEFAULT_WARM_HANDOFF_WINDOW_MS);

  // 1. Cold hover schedules 160ms delay
  const enter1 = machine.pointerEnter('btn-quick-find', 'Schnellsuche (Ctrl+F)', 1000);
  assert.equal(enter1.delayMs, 160);
  assert.equal(enter1.shouldShowImmediately, false);
  assert.equal(machine.isVisible, false);

  // Stale timer for a different anchor is ignored
  assert.equal(machine.commitPendingShow('btn-other'), false);
  assert.equal(machine.commitPendingShow('btn-quick-find'), true);
  assert.equal(machine.isVisible, true);
  assert.equal(machine.activeText, 'Schnellsuche (Ctrl+F)');

  // 2. Direct sweep to adjacent button while visible -> 0ms instant handoff (no stale tooltip!)
  const enter2 = machine.pointerEnter('btn-weekly-review', 'Wochenrückblick (Ctrl+Shift+R)', 1250);
  assert.equal(enter2.delayMs, 0);
  assert.equal(enter2.shouldShowImmediately, true);
  assert.equal(machine.activeAnchorId, 'btn-weekly-review');
  assert.equal(machine.activeText, 'Wochenrückblick (Ctrl+Shift+R)');

  // Out-of-order pointerLeave from previous button never clobbers the active button
  assert.equal(machine.pointerLeave('btn-quick-find', 1255), false);
  assert.equal(machine.isVisible, true);
  assert.equal(machine.activeAnchorId, 'btn-weekly-review');

  // 3. Leaving button starts 450ms warm handoff window
  assert.equal(machine.pointerLeave('btn-weekly-review', 1400), true);
  assert.equal(machine.isVisible, false);

  // Entering next button within 450ms (300ms elapsed) -> 0ms instant handoff
  const enter3 = machine.pointerEnter('btn-gemini', 'Gemini AI Hub (Ctrl+G)', 1700);
  assert.equal(enter3.delayMs, 0);
  assert.equal(enter3.shouldShowImmediately, true);
  assert.equal(machine.isVisible, true);

  // 4. Leaving and waiting > 450ms resets to 160ms cold delay
  assert.equal(machine.pointerLeave('btn-gemini', 2000), true);
  const enterColdAgain = machine.pointerEnter('btn-settings', 'Einstellungen', 2600);
  assert.equal(enterColdAgain.delayMs, 160);
  assert.equal(enterColdAgain.shouldShowImmediately, false);
});

test('TooltipTimingMachine updates active text in 0ms on state or language change and resets on click dismiss', () => {
  const machine = new TooltipTimingMachine();
  machine.pointerEnter('btn-sync', 'Synchronisiere...', 500);
  machine.commitPendingShow('btn-sync');
  assert.equal(machine.isVisible, true);

  // Live state update while hovered (Syncing -> Synced)
  const updated = machine.updateActiveText('btn-sync', 'Synchronisiert', 580);
  assert.equal(updated.changed, true);
  assert.equal(updated.isVisible, true);
  assert.equal(updated.activeText, 'Synchronisiert');

  // Updates for non-active anchors are ignored
  const ignored = machine.updateActiveText('btn-other', 'Other', 590);
  assert.equal(ignored.changed, false);
  assert.equal(machine.activeText, 'Synchronisiert');

  // Empty text dismisses cleanly
  const cleared = machine.updateActiveText('btn-sync', '   ', 600);
  assert.equal(cleared.isVisible, false);
  assert.equal(machine.activeAnchorId, null);

  // Click / scroll dismissAll(preserveWarmWindow = false) clears warm window immediately
  machine.pointerEnter('btn-voice', 'Spracheingabe', 650); // still warm from 600ms
  assert.equal(machine.isVisible, true);
  machine.dismissAll(700, false);
  assert.equal(machine.isVisible, false);
  const afterClick = machine.pointerEnter('btn-voice', 'Spracheingabe', 750);
  assert.equal(afterClick.delayMs, 160);
  assert.equal(afterClick.shouldShowImmediately, false);
});

test('computeTooltipPosition centers above anchor, flips below near top edge, and clamps inside viewport margins', () => {
  // Standard placement centered above anchor
  const above = computeTooltipPosition(
    { left: 400, top: 120, width: 40, height: 32 },
    140,
    28,
    1280,
    800
  );
  assert.equal(above.placement, 'above');
  assert.equal(above.left, 350);
  assert.equal(above.top, 84);

  // Topbar button near y=8 flips cleanly below the button so it is never clipped
  const flippedBelow = computeTooltipPosition(
    { left: 600, top: 10, width: 34, height: 34 },
    180,
    28,
    1280,
    800
  );
  assert.equal(flippedBelow.placement, 'below');
  assert.equal(flippedBelow.top, 52);

  // Far-left and far-right buttons clamp horizontally to 8px viewport margins
  const clampedLeft = computeTooltipPosition(
    { left: 4, top: 100, width: 28, height: 28 },
    200,
    28,
    1280,
    800
  );
  assert.equal(clampedLeft.left, 8);

  const clampedRight = computeTooltipPosition(
    { left: 1244, top: 100, width: 28, height: 28 },
    200,
    28,
    1280,
    800
  );
  assert.equal(clampedRight.left, 1072);
});

test('isTopBarIconTooltipAllowed allows strictly top-bar icon buttons and blocks labeled pills and all non-topbar elements', () => {
  // Pure icon button inside .workspace-topbar -> allowed
  assert.equal(
    isTopBarIconTooltipAllowed({
      isInsideTopBar: true,
      tagName: 'BUTTON',
      isIconButtonClass: true,
      isResponsiveTopBarControl: false,
      hasVisibleTextLabel: false
    }),
    true
  );

  // Responsive topbar pill when label is visible (desktop / Chromebook) -> blocked
  assert.equal(
    isTopBarIconTooltipAllowed({
      isInsideTopBar: true,
      tagName: 'BUTTON',
      isIconButtonClass: false,
      isResponsiveTopBarControl: true,
      hasVisibleTextLabel: true
    }),
    false
  );

  // Responsive topbar pill when collapsed to icon-only on narrow screen -> allowed
  assert.equal(
    isTopBarIconTooltipAllowed({
      isInsideTopBar: true,
      tagName: 'BUTTON',
      isIconButtonClass: false,
      isResponsiveTopBarControl: true,
      hasVisibleTextLabel: false
    }),
    true
  );

  // Non-button inside topbar (e.g. stalled count badge span) -> blocked
  assert.equal(
    isTopBarIconTooltipAllowed({
      isInsideTopBar: true,
      tagName: 'SPAN',
      isIconButtonClass: false,
      isResponsiveTopBarControl: false,
      hasVisibleTextLabel: false
    }),
    false
  );

  // Icon button outside .workspace-topbar (sidebar, task card, modal, Magic Plus FAB) -> blocked
  assert.equal(
    isTopBarIconTooltipAllowed({
      isInsideTopBar: false,
      tagName: 'BUTTON',
      isIconButtonClass: true,
      isResponsiveTopBarControl: false,
      hasVisibleTextLabel: false
    }),
    false
  );
});

test('v2.0.0-beta.20 topbarTooltip* labels are concise, localized across all 5 languages, and free of IndexedDB/Room jargon or shortcut clutter', async () => {
  const { TRANSLATIONS } = await import('../src/i18n/translations.ts');
  const keys = [
    'topbarTooltipHideSidebar',
    'topbarTooltipShowSidebar',
    'topbarTooltipQuickFind',
    'topbarTooltipWeeklyReview',
    'topbarTooltipTemplates',
    'topbarTooltipQuickEntry',
    'topbarTooltipVoiceInput',
    'topbarTooltipAreas',
    'topbarTooltipTags',
    'topbarTooltipNotifications',
    'topbarTooltipLanguage',
    'topbarTooltipCloudSync',
    'topbarTooltipSettings'
  ] as const;

  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const table = TRANSLATIONS[lang];
    for (const key of keys) {
      const value = table[key];
      assert.equal(typeof value, 'string');
      assert.ok(value.trim().length > 0, `Empty ${key} for ${lang}`);
      assert.equal(value.includes('('), false, `Shortcut clutter in ${key} for ${lang}: ${value}`);
      assert.equal(
        /indexeddb|room db|save to thingsbook/i.test(value),
        false,
        `Misleading label in ${key} for ${lang}: ${value}`
      );
    }
  }

  assert.equal(TRANSLATIONS.en.topbarTooltipQuickEntry, 'Quick Entry');
  assert.equal(TRANSLATIONS.de.topbarTooltipQuickEntry, 'Schnelleingabe');
  assert.equal(TRANSLATIONS.en.topbarTooltipCloudSync, 'Cloud Sync');
  assert.equal(TRANSLATIONS.de.topbarTooltipCloudSync, 'Cloud-Sync');
  assert.equal(TRANSLATIONS.en.topbarTooltipTemplates, 'Templates & Import');
  assert.equal(TRANSLATIONS.en.topbarTooltipAreas, 'Areas');
  assert.equal(TRANSLATIONS.en.topbarTooltipTags, 'Categories');
  assert.equal(TRANSLATIONS.de.topbarTooltipTags, 'Kategorien');
  assert.equal(TRANSLATIONS.en.sidebarReviewShort, 'Review');
  assert.equal(TRANSLATIONS.de.sidebarReviewShort, 'Rückblick');

  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    assert.ok(TRANSLATIONS[lang].sidebarReviewShort.trim().length > 0);
    assert.ok(TRANSLATIONS[lang].tagsSearchOrCreatePlaceholder.trim().length > 0);
    assert.ok(TRANSLATIONS[lang].tagsFilterPlaceholder.trim().length > 0);
    assert.ok(TRANSLATIONS[lang].tagsNoFilterMatches.trim().length > 0);
    assert.ok(TRANSLATIONS[lang].tagsCreateFromSearch('Focus').includes('Focus'));
    assert.ok(!/\b[23]\.0\b/.test(TRANSLATIONS[lang].settingsIncludeBetaTitle));
    assert.ok(!/\b[23]\.0\b/.test(TRANSLATIONS[lang].settingsIncludeBetaDesc));
  }
});

test('v3.0.0-alpha.4 sidebar Bento and Micro-Bento cards use 2-row layout and never truncate labels with ellipsis', async () => {
  const fs = await import('node:fs/promises');
  const cssUrl = new URL('../src/index.css', import.meta.url);
  const css = await fs.readFile(cssUrl, 'utf8');

  const bentoLabelBlock = css.match(/\.bento-tile-label\s*\{([^}]*)\}/)?.[1] ?? '';
  const miniCardBlock = css.match(/\.bento-mini-card\s*\{([^}]*)\}/)?.[1] ?? '';
  const miniLabelBlock = css.match(/\.bento-mini-label\s*\{([^}]*)\}/)?.[1] ?? '';

  assert.ok(bentoLabelBlock.length > 0, 'Missing .bento-tile-label rule');
  assert.ok(!bentoLabelBlock.includes('ellipsis'), '.bento-tile-label must not truncate with ellipsis');
  assert.ok(!bentoLabelBlock.includes('nowrap'), '.bento-tile-label must allow full-width wrapping');

  assert.ok(miniCardBlock.includes('flex-direction: column'), '.bento-mini-card must use 2-row column layout');
  assert.ok(miniLabelBlock.length > 0, 'Missing .bento-mini-label rule');
  assert.ok(!miniLabelBlock.includes('ellipsis'), '.bento-mini-label must not truncate with ellipsis');
  assert.ok(!miniLabelBlock.includes('nowrap'), '.bento-mini-label must allow full-width wrapping');

  const reviewTabBlock = css.match(/\.v2-review-step-tab\s*\{([^}]*)\}/)?.[1] ?? '';
  const reviewLabelBlock = css.match(/\.v2-review-step-label\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(reviewTabBlock.includes('flex-direction: column'), '.v2-review-step-tab must use 2-row column layout');
  assert.ok(reviewLabelBlock.length > 0, 'Missing .v2-review-step-label rule');
  assert.ok(!reviewLabelBlock.includes('ellipsis'), '.v2-review-step-label must never truncate with ellipsis');
  assert.ok(!reviewLabelBlock.includes('nowrap'), '.v2-review-step-label must allow full-width wrapping');
});

test('v3.0.0 isTimelineMouseTooltipAllowed strictly allows unpressed mouse hover and rejects touch, pen, pressed buttons, and active drags', () => {
  // Mouse with buttons === 0 and not dragging -> allowed
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'mouse',
      buttons: 0
    }),
    true
  );
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'mouse',
      buttons: 0,
      isDraggingTimelineBar: false
    }),
    true
  );

  // Missing attribute -> blocked
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: false,
      pointerType: 'mouse',
      buttons: 0
    }),
    false
  );

  // Touch, pen, and unknown pointer types -> strictly blocked
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'touch',
      buttons: 0
    }),
    false
  );
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'pen',
      buttons: 0
    }),
    false
  );
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: '',
      buttons: 0
    }),
    false
  );

  // Pressed mouse buttons -> strictly blocked
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'mouse',
      buttons: 1
    }),
    false
  );
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'mouse',
      buttons: 2
    }),
    false
  );

  // Active timeline bar drag -> strictly blocked
  assert.equal(
    isTimelineMouseTooltipAllowed({
      hasTimelineTipAttr: true,
      pointerType: 'mouse',
      buttons: 0,
      isDraggingTimelineBar: true
    }),
    false
  );
});

test('v3.0.0 promoteUngroupedTasksToHeading creates a top-of-project Heading and moves open ungrouped tasks atomically while Asana skips empty placeholder sections', async () => {
  type StoreMap = Map<string, unknown>;
  const stores = new Map<string, StoreMap>([
    ['areas', new Map()],
    ['projects', new Map()],
    ['headings', new Map()],
    ['tasks', new Map()],
    ['checklist_items', new Map()],
    ['tags', new Map()],
    ['attachments', new Map()],
    ['meta', new Map([['initial_seeded', true]])],
    ['projectTemplates', new Map()]
  ]);

  const makeObjectStore = (name: string) => {
    const map = stores.get(name)!;
    return {
      async get(key: string) {
        const val = map.get(key);
        return val === undefined ? undefined : structuredClone(val);
      },
      async getAll() {
        return Array.from(map.values()).map((v) => structuredClone(v));
      },
      async put(value: unknown, explicitKey?: string) {
        const k =
          explicitKey ??
          (typeof value === 'object' && value !== null && 'id' in value
            ? String((value as { id: string }).id)
            : '');
        map.set(k, structuredClone(value));
        return k;
      },
      async delete(key: string) {
        map.delete(key);
      },
      async clear() {
        map.clear();
      },
      async count() {
        return map.size;
      }
    };
  };

  const dbFake = {
    async get(storeName: string, key: string) {
      return makeObjectStore(storeName).get(key);
    },
    async getAll(storeName: string) {
      return makeObjectStore(storeName).getAll();
    },
    async put(storeName: string, value: unknown, key?: string) {
      return makeObjectStore(storeName).put(value, key);
    },
    async count(storeName: string) {
      return makeObjectStore(storeName).count();
    },
    transaction(storeNames: string | string[]) {
      const allowed = new Set(Array.isArray(storeNames) ? storeNames : [storeNames]);
      return {
        objectStore(name: string) {
          assert.ok(allowed.has(name), `Store "${name}" not included in transaction`);
          return makeObjectStore(name);
        },
        get store() {
          const single = Array.isArray(storeNames) ? storeNames[0] : storeNames;
          return makeObjectStore(single);
        },
        done: Promise.resolve()
      };
    }
  } as unknown as IDBPDatabase;

  const db = new ThingsLocalDb(async () => dbFake);
  const now = 1_765_000_000_000;

  const proj: ProjectRecord = {
    id: 'proj-general',
    areaId: null,
    title: 'Q4 Launch',
    notes: '',
    status: 'ACTIVE',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    completedAt: null,
    sortOrder: 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const existingPhase: HeadingRecord = {
    id: 'hd-existing',
    projectId: proj.id,
    title: 'Execution',
    startDate: null,
    endDate: null,
    sortOrder: 1000,
    isArchived: false,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    isDirty: false
  };
  const makeTask = (overrides: Partial<TaskRecord> & Pick<TaskRecord, 'id' | 'title'>): TaskRecord => ({
    id: overrides.id,
    title: overrides.title,
    notes: '',
    status: overrides.status ?? 'OPEN',
    scheduleState: 'ANYTIME',
    whenDate: null,
    deadline: null,
    reminderTime: null,
    completedAt: overrides.completedAt ?? null,
    projectId: overrides.projectId !== undefined ? overrides.projectId : proj.id,
    headingId: overrides.headingId !== undefined ? overrides.headingId : null,
    areaId: null,
    recurrenceRule: null,
    sortOrder: overrides.sortOrder ?? 1000,
    tagIds: [],
    createdAt: now,
    updatedAt: now,
    isDeleted: overrides.isDeleted ?? false,
    isDirty: false
  });

  stores.get('projects')!.set(proj.id, proj);
  stores.get('headings')!.set(existingPhase.id, existingPhase);
  stores.get('tasks')!.set('tk-open-ungrouped-1', makeTask({ id: 'tk-open-ungrouped-1', title: 'Draft brief' }));
  stores.get('tasks')!.set('tk-open-ungrouped-2', makeTask({ id: 'tk-open-ungrouped-2', title: 'Kickoff deck' }));
  stores.get('tasks')!.set(
    'tk-done-ungrouped',
    makeTask({ id: 'tk-done-ungrouped', title: 'Old done task', status: 'COMPLETED', completedAt: now })
  );
  stores.get('tasks')!.set(
    'tk-in-phase',
    makeTask({ id: 'tk-in-phase', title: 'Build prototype', headingId: existingPhase.id })
  );

  await db.initialize('en');

  // Empty title returns null and performs no mutation
  assert.equal(await db.promoteUngroupedTasksToHeading(proj.id, '   '), null);

  const promoted = await db.promoteUngroupedTasksToHeading(proj.id, 'Preparation');
  assert.ok(promoted != null);
  assert.equal(promoted.title, 'Preparation');
  assert.equal(promoted.projectId, proj.id);
  assert.ok(promoted.sortOrder < existingPhase.sortOrder, 'Promoted phase must sort before first existing heading');

  const updated1 = (await dbFake.get('tasks', 'tk-open-ungrouped-1')) as TaskRecord;
  const updated2 = (await dbFake.get('tasks', 'tk-open-ungrouped-2')) as TaskRecord;
  const doneTask = (await dbFake.get('tasks', 'tk-done-ungrouped')) as TaskRecord;
  const phaseTask = (await dbFake.get('tasks', 'tk-in-phase')) as TaskRecord;

  assert.equal(updated1.headingId, promoted.id);
  assert.equal(updated2.headingId, promoted.id);
  assert.equal(doneTask.headingId, null, 'Completed logbook tasks are untouched');
  assert.equal(phaseTask.headingId, existingPhase.id, 'Tasks in existing phases remain in their phase');

  // Verify Asana JSON import skips empty "Untitled section", "Unnamed section", "(no section)" sections
  const asanaImported = importAsanaJson(
    JSON.stringify({
      project: { name: 'Clean Import' },
      sections: [
        { gid: 's-empty-1', name: 'Untitled section' },
        { gid: 's-empty-2', name: 'Unnamed section' },
        { gid: 's-empty-3', name: '(no section)' },
        { gid: 's-real', name: 'Launch' }
      ],
      data: [
        { gid: 't-1', name: 'Ship GA', memberships: [{ section: { gid: 's-real', name: 'Launch' } }] }
      ]
    })
  );
  assert.equal(asanaImported.ungroupedTasks.length, 0);
  assert.deepEqual(
    asanaImported.phases.map((p) => p.title),
    ['Launch']
  );
});
