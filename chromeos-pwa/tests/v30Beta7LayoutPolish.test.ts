import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import type { IDBPDatabase } from 'idb';
import {
  STANDALONE_DURATION_PRESETS,
  TIMEBLOCK_DURATION_PRESETS,
  buildDailyTimeline,
  formatStandaloneDuration,
  formatTimeblockRange,
  parseCustomDurationMinutes,
  parseProjectOutlineInput
} from '../src/domain/v2Utils.ts';
import { resolveEffectiveShowPerspectives } from '../src/domain/types.ts';
import {
  buildProjectRoadmapLayout,
  generateRoadmapPdfBytes
} from '../src/domain/v30Roadmap.ts';
import { DEFAULT_PREFS, ThingsLocalDb } from '../src/data/localDb.ts';
import { TRANSLATIONS } from '../src/i18n/translations.ts';

const SRC_DIR = path.resolve(import.meta.dirname, '../src');

test('v3.0.0-beta.7 Workspace container & empty state anchor cleanly to the left next to the Sidebar (no margin: 0 auto centering)', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const appTsx = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');

  assert.ok(!css.includes('container-type:'), 'index.css must never use container-type:');
  assert.ok(appTsx.includes('lang={lang}'), '.app-shell must set lang={lang} for clean hyphenation');

  const workspaceContainerBlock =
    css.match(/\.workspace-container\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(workspaceContainerBlock.length > 0, '.workspace-container rule must exist');
  assert.ok(workspaceContainerBlock.includes('margin: 0;'), '.workspace-container must use margin: 0;');
  assert.ok(
    !workspaceContainerBlock.includes('auto'),
    '.workspace-container must not center horizontally with margin: 0 auto'
  );

  const inlineQfBlock = css.match(/\.inline-qf-bar\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(inlineQfBlock.includes('margin: 0;'), '.inline-qf-bar must left-align');
  assert.ok(!inlineQfBlock.includes('auto'));

  const emptyStateBlock = css.match(/\.empty-state\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(emptyStateBlock.includes('text-align: left;'), '.empty-state must be left-aligned');
  assert.ok(emptyStateBlock.includes('max-width: 520px;'), '.empty-state must be bounded');
});

test('v3.0.0-beta.7 Sidebar footer uses a collision-free 2-row dock (+ New Project ▾ full-width row above spaced utility icons)', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const sidebarTsx = fs.readFileSync(path.join(SRC_DIR, 'components/Sidebar.tsx'), 'utf8');

  const footerBlock = css.match(/\.sidebar-footer\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(
    footerBlock.includes('flex-direction: column;'),
    '.sidebar-footer must stack + New Project row above utility icons row'
  );

  const footerBtnBlock = css.match(/\.sidebar-footer-btn\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(footerBtnBlock.includes('width: 100%;'));
  assert.ok(footerBtnBlock.includes('justify-content: space-between;'));

  const utilitiesBlock = css.match(/\.sidebar-footer-utilities\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(utilitiesBlock.includes('width: 100%;'));
  assert.ok(utilitiesBlock.includes('justify-content: space-between;'));

  assert.ok(sidebarTsx.includes('data-testid="sidebar-footer-dock"'));
  assert.ok(sidebarTsx.includes('data-testid="sidebar-new-project-btn"'));
  assert.ok(sidebarTsx.includes('className="sidebar-footer-btn-main"'));
});

test('v3.0.0-beta.8 Sidebar clearly separates Perspectives (Soft Bento card) from Areas and Unassigned Projects with unconditional divider & explicit section headers', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const sidebarTsx = fs.readFileSync(path.join(SRC_DIR, 'components/Sidebar.tsx'), 'utf8');

  assert.ok(!css.includes('container-type:'), 'index.css must never use container-type:');

  const perspCss = css.match(/\.sidebar-perspectives-section\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(
    perspCss.includes('background: var(--bg-card);'),
    '.sidebar-perspectives-section must be styled as a self-contained Soft Bento card'
  );
  assert.ok(perspCss.includes('border: 1px solid var(--border-subtle);'));
  assert.ok(perspCss.includes('border-radius: 12px;'));

  const perspIdx = sidebarTsx.indexOf('data-testid="sidebar-perspectives-section"');
  const divIdx = sidebarTsx.indexOf('data-testid="sidebar-perspectives-areas-divider"');
  const areasIdx = sidebarTsx.indexOf('data-testid="sidebar-areas-section"');
  const areasHeaderIdx = sidebarTsx.indexOf('data-testid="sidebar-areas-header"');

  assert.ok(perspIdx > 0, 'Perspectives Soft Bento section must exist');
  assert.ok(divIdx > perspIdx, 'Unconditional divider must follow Perspectives');
  assert.ok(areasIdx > divIdx, 'Areas section must follow the divider');
  assert.ok(areasHeaderIdx > areasIdx, 'Explicit Areas section header must precede Area rows');
  assert.ok(sidebarTsx.includes('{t.entityAreas}'), 'Areas section header must render localized t.entityAreas');
  assert.ok(
    sidebarTsx.includes('data-testid="sidebar-unassigned-projects-header"'),
    'Standalone unassigned projects must also render an explicit Projects section header'
  );
});

test('v3.0.0-beta.9 Hovering over task rows never changes row or section frame dimensions (opacity-only hover actions)', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  const quickEditHoverBlock =
    css.match(/\.task-row:hover \.task-row-quick-edit-btn[^{]*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(quickEditHoverBlock.includes('opacity: 1;'));
  assert.ok(!quickEditHoverBlock.includes('display:'));
  assert.ok(!quickEditHoverBlock.includes('width:'));
  assert.ok(!quickEditHoverBlock.includes('height:'));

  const openBtnHoverBlock =
    css.match(/\.task-row:hover \.task-row-open-btn[^{]*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(openBtnHoverBlock.includes('opacity: 1;'));
  assert.ok(!openBtnHoverBlock.includes('display:'));
  assert.ok(!openBtnHoverBlock.includes('width:'));
  assert.ok(!openBtnHoverBlock.includes('height:'));

  const taskRowHoverBlock = css.match(/\.task-row:hover\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(!taskRowHoverBlock.includes('padding'));
  assert.ok(!taskRowHoverBlock.includes('margin'));
  assert.ok(!taskRowHoverBlock.includes('border'));
});

test('v3.0.0-beta.10 Sidebar main Bento tiles & mini-cards provide calm sky-blue hover highlight with zero size shift', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  const bentoHover = css.match(/\.bento-tile:hover\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(bentoHover.includes('background: var(--bg-card-hover);'));
  assert.ok(bentoHover.includes('border-color: rgba(56, 152, 236, 0.36);'));
  assert.ok(!bentoHover.includes('padding'));
  assert.ok(!bentoHover.includes('border-width'));
  assert.ok(!bentoHover.includes('transform'));

  const miniHover = css.match(/\.bento-mini-card:hover\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(miniHover.includes('background: var(--bg-card-hover);'));
  assert.ok(miniHover.includes('border-color: rgba(56, 152, 236, 0.36);'));
  assert.ok(!miniHover.includes('padding'));
  assert.ok(!miniHover.includes('border-width'));
});

test('v3.0.0-beta.11 Balanced 3+3 Soft Bento Task Modal consolidates right-column property boxes into 3 logical cards and collapses empty Attachments into a slim 1-row bar', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components/TaskItem.tsx'), 'utf8');

  const propTileMatches = taskItemSrc.match(/className="task-bento-prop-tile"/g) ?? [];
  assert.equal(
    propTileMatches.length,
    3,
    'Right properties column must render 3 balanced Soft Bento property cards instead of 6'
  );
  assert.ok(taskItemSrc.includes("taskAttachments.length === 0 ? 'attachments-empty' : ''"));

  const attachmentsEmptyCss =
    css.match(/\.task-bento-card\.attachments-empty\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(attachmentsEmptyCss.includes('flex-direction: row;'));

  const inlineSubCss = css.match(/\.task-bento-inline-sub\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(inlineSubCss.includes('display: contents;'));
});

test('v3.0.0-beta.12 Task Modal eliminates vertical centering white gap between task title header and Bento body', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const macModalSwift = fs.readFileSync(
    path.resolve(SRC_DIR, '../../macos/Sources/ThingsbookMac/Views/TaskBentoModalView.swift'),
    'utf8'
  );

  assert.ok(macModalSwift.includes('ModalBodyHeightPreferenceKey'));
  assert.ok(macModalSwift.includes('.frame(height: effectiveBodyHeight)'));
  assert.ok(macModalSwift.includes('.scrollDisabled(!needsVerticalScroll)'));
  assert.ok(!macModalSwift.includes('ViewThatFits(in: .vertical)'));
  assert.ok(!macModalSwift.includes('.frame(maxHeight: resolvedMaxModalHeight)'));

  const modalBodyCss = css.match(/\.task-bento-modal-body\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(modalBodyCss.includes('padding: 12px 18px 14px;'));
});

test('v3.0.0-beta.13 subtle pill hover, 1-day & custom durations, 0ms macOS task click selection, and full-presence app icons', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components/TaskItem.tsx'), 'utf8');
  const repoRoot = path.resolve(SRC_DIR, '../..');

  // 1. Subtle zero-shift hover on Task Modal property pills
  const pillHoverCss =
    css.match(
      /\.task-bento-prop-pills\s+\.chip-btn:hover:not\(\.active\):not\(\.urgent\):not\(\.evening-quick\)\s*\{([^}]*)\}/
    )?.[1] ?? '';
  assert.ok(pillHoverCss.includes('border-color: rgba(56, 152, 236, 0.38);'));
  assert.ok(!pillHoverCss.includes('padding'));

  // 2. 4h (240m) & 8h (480m) presets + multi-day formatting + custom duration parser & input
  assert.ok(STANDALONE_DURATION_PRESETS.includes(240));
  assert.ok(STANDALONE_DURATION_PRESETS.includes(480));
  assert.ok(TIMEBLOCK_DURATION_PRESETS.includes(240));
  assert.ok(TIMEBLOCK_DURATION_PRESETS.includes(480));
  assert.equal(formatStandaloneDuration(240), '4h');
  assert.equal(formatStandaloneDuration(480), '8h');
  assert.equal(formatStandaloneDuration(720), '1d 4h');
  assert.equal(formatStandaloneDuration(960), '2d');
  assert.equal(parseCustomDurationMinutes('1d'), 480);
  assert.equal(parseCustomDurationMinutes('2,5d'), 1200);
  assert.equal(parseCustomDurationMinutes('1d 4h'), 720);
  assert.equal(parseCustomDurationMinutes('3t'), 1440);
  assert.equal(parseCustomDurationMinutes('2j'), 960);
  assert.equal(parseCustomDurationMinutes('1g'), 480);
  assert.equal(parseCustomDurationMinutes('1:30'), 90);
  assert.equal(parseCustomDurationMinutes('45'), 45);
  assert.equal(parseCustomDurationMinutes('invalid'), null);
  assert.ok(taskItemSrc.includes('data-testid="task-custom-duration-input"'));

  // 2b. Option 1: Hide Perspectives in Sidebar by default unless enabled in Settings or already used
  assert.equal(resolveEffectiveShowPerspectives(DEFAULT_PREFS), false);
  assert.equal(
    resolveEffectiveShowPerspectives({ showPerspectives: undefined, savedPerspectives: [] }),
    false
  );
  assert.equal(
    resolveEffectiveShowPerspectives({
      showPerspectives: undefined,
      savedPerspectives: [
        {
          id: 'p1',
          title: 'Sprint',
          categoryIds: [],
          effortLevel: null,
          maxDurationMinutes: 240,
          dueWindow: 'ANY',
          areaId: null,
          actionableOnly: true,
          sortOrder: 1000
        }
      ]
    }),
    true
  );
  assert.equal(
    resolveEffectiveShowPerspectives({ showPerspectives: true, savedPerspectives: [] }),
    true
  );
  assert.equal(
    resolveEffectiveShowPerspectives({
      showPerspectives: false,
      savedPerspectives: [
        {
          id: 'p1',
          title: 'Sprint',
          categoryIds: [],
          effortLevel: null,
          maxDurationMinutes: 480,
          dueWindow: 'ANY',
          areaId: null,
          actionableOnly: true,
          sortOrder: 1000
        }
      ]
    }),
    false
  );
  for (const code of ['en', 'de', 'fr', 'it', 'es'] as const) {
    assert.ok(
      TRANSLATIONS[code].settingsShowPerspectives.length > 0,
      `Missing settingsShowPerspectives for ${code}`
    );
    assert.ok(
      TRANSLATIONS[code].settingsShowPerspectivesDesc.length > 0,
      `Missing settingsShowPerspectivesDesc for ${code}`
    );
  }

  // 3. macOS 0ms instant task selection (zero TapGesture(count: 2) disambiguation delay)
  const macRowSwift = fs.readFileSync(
    path.join(repoRoot, 'macos/Sources/ThingsbookMac/Views/TaskRowCardView.swift'),
    'utf8'
  );
  const macBoardSwift = fs.readFileSync(
    path.join(repoRoot, 'macos/Sources/ThingsbookMac/Views/ProjectBoardAndRoadmapView.swift'),
    'utf8'
  );
  const macCanvasSwift = fs.readFileSync(
    path.join(repoRoot, 'macos/Sources/ThingsbookMac/Views/SmartViewCanvas.swift'),
    'utf8'
  );
  assert.ok(macRowSwift.includes('.thingsInstantTaskTap('));
  assert.ok(!macRowSwift.includes('TapGesture(count: 2)'));
  assert.ok(!macBoardSwift.includes('TapGesture(count: 2)'));
  assert.ok(!macCanvasSwift.includes('TapGesture(count: 2)'));

  // 4. Full-presence app icon geometry across macOS (864x864), ChromeOS PWA (504x504), and Android (scaleX 1.18)
  const macIconSvg = fs.readFileSync(
    path.join(repoRoot, 'macos/Resources/Assets/AppIcon.svg'),
    'utf8'
  );
  const pwaFaviconSvg = fs.readFileSync(
    path.join(repoRoot, 'chromeos-pwa/public/favicon.svg'),
    'utf8'
  );
  const androidFgXml = fs.readFileSync(
    path.join(repoRoot, 'app/src/main/res/drawable/ic_launcher_foreground.xml'),
    'utf8'
  );
  assert.ok(macIconSvg.includes('width="864" height="864"'));
  assert.ok(pwaFaviconSvg.includes('width="504" height="504"'));
  assert.ok(androidFgXml.includes('android:scaleX="1.18"'));
});

function createInMemoryIdb(): () => Promise<IDBPDatabase> {
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
  const getStoreMap = (name: string): StoreMap => {
    let m = stores.get(name);
    if (!m) {
      m = new Map();
      stores.set(name, m);
    }
    return m;
  };
  const makeObjectStore = (name: string) => {
    const map = getStoreMap(name);
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
        store: !Array.isArray(storeNames) ? makeObjectStore(storeNames) : undefined,
        objectStore(name: string) {
          assert.ok(allowed.has(name), `Store "${name}" not included in transaction`);
          return makeObjectStore(name);
        },
        done: Promise.resolve()
      };
    }
  } as unknown as IDBPDatabase;

  return async () => dbFake;
}

test('Soft Bento Task Modal aligns Left and Right column gray frames top & bottom via align-items: stretch and defines --shadow-xs/--shadow-sm', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  assert.ok(!css.includes('container-type:'), 'index.css must contain zero container-type rules');
  assert.ok(css.includes('--shadow-xs:'), ':root must define --shadow-xs');
  assert.ok(css.includes('--shadow-sm:'), ':root must define --shadow-sm');

  const bentoGridBlock = css.match(/\.task-bento-modal-grid\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(
    bentoGridBlock.includes('align-items: stretch;'),
    '.task-bento-modal-grid must stretch left and right columns to identical total height'
  );

  const propTileBlock = css.match(/\.task-bento-prop-tile\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(propTileBlock.includes('border-radius: 14px;'));
  assert.ok(propTileBlock.includes('padding: 12px 14px;'));
  assert.ok(propTileBlock.includes('flex: 1 0 auto;'));
  assert.ok(propTileBlock.includes('justify-content: flex-start;'));

  assert.ok(
    css.includes('.task-bento-main-col > [data-testid="task-bento-notes-card"]'),
    'Notes card must flex-fill when Right column exceeds Left column'
  );
  assert.ok(
    css.includes('.task-bento-main-col > [data-testid="task-bento-notes-card"] .md-rendered-body'),
    'Formatted Markdown preview must also flex-fill inside Notes card'
  );
});

test('Multi-day 2400m (5d) duration persists across localDb, .tbbackup round-trip, and non-destructive Timeblock clear', async () => {
  const db1 = new ThingsLocalDb(createInMemoryIdb());
  await db1.initialize('de');

  const created = await db1.upsertTask({
    title: 'Migrationskonzept',
    scheduleState: 'TODAY',
    whenDate: new Date(2026, 10, 21, 0, 0, 0, 0).getTime(),
    durationMinutes: 2400,
    scheduledStartMinutes: 9 * 60
  });
  assert.equal(created.durationMinutes, 2400);
  assert.equal(created.scheduledStartMinutes, 540);
  assert.equal(formatTimeblockRange(540, 2400), '09:00 · 5d');
  assert.equal(formatTimeblockRange(23 * 60 + 30, 60), '23:30–00:30 +1d · 1h');

  const timeline = buildDailyTimeline([created], [], new Date(2026, 10, 21, 0, 0, 0, 0).getTime());
  assert.equal(timeline.timedItems.length, 1);
  assert.equal(timeline.timedItems[0].durationMinutes, 2400);
  assert.equal(timeline.timedItems[0].endMinutes, 1440);
  assert.equal(timeline.timedItems[0].rangeLabel, '09:00 · 5d');

  // Non-destructive Timeblock clear preserves standalone durationMinutes (2400m = 5d)
  const afterClearTb = await db1.upsertTask({
    id: created.id,
    scheduledStartMinutes: null
  });
  assert.equal(afterClearTb.scheduledStartMinutes, null);
  assert.equal(afterClearTb.durationMinutes, 2400);

  // Verify TaskItem.tsx & TodayTimelineView.tsx never wipe durationMinutes when clearing Timeblock
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components/TaskItem.tsx'), 'utf8');
  const timelineSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/TodayTimelineView.tsx'),
    'utf8'
  );
  assert.ok(!taskItemSrc.includes('scheduledStartMinutes: null,\n                              durationMinutes: null'));
  assert.ok(!timelineSrc.includes('scheduledStartMinutes: null,\n                            durationMinutes: null'));

  // .tbbackup round-trip preserves 2400m (5d) without clamping to 720m
  const { bytes } = await db1.createBackupBytes(null, {
    language: 'de',
    themeMode: 'light',
    sidebarVisible: true,
    localOnlyDbMode: true,
    geminiApiKey: '',
    dailyDigestEnabled: false,
    dailyDigestHour: 8,
    dailyDigestMinute: 30,
    autoCheckUpdatesEnabled: true,
    includeBetaUpdates: true,
    calendarEventsEnabled: false,
    selectedCalendarIds: [],
    calendarIdsInitialized: false,
    groupTodayByProjectOrArea: true,
    biometricAppLockEnabled: false,
    appLockPinHash: '',
    webAuthnCredId: '',
    lastWeeklyReviewAt: 0,
    tagSortMode: 'alpha',
    viewSortMode: 'manual',
    savedPerspectives: [
      {
        id: 'persp-multi-day',
        title: 'Multi-Day Sprint',
        categoryIds: [],
        effortLevel: null,
        maxDurationMinutes: 2400,
        dueWindow: 'ANY',
        areaId: null,
        actionableOnly: true,
        sortOrder: 100
      }
    ]
  });

  const db2 = new ThingsLocalDb(createInMemoryIdb());
  await db2.initialize('en');
  const restored = await db2.restoreBackupFromBytes(bytes, null, false);
  const restoredTask = db2.getSnapshot().tasks.find((t) => t.id === created.id);
  assert.ok(restoredTask, 'Restored task must exist');
  assert.equal(restoredTask.durationMinutes, 2400, '.tbbackup restore must preserve 2400m (5d)');
  assert.equal(
    JSON.parse(restored.preferences?.savedPerspectivesJson || '[]')[0]?.maxDurationMinutes,
    2400
  );
});

test('parseProjectOutlineInput parses trailing @<duration> tokens and localization covers repeatClearBtn & Roadmap PDF headers', () => {
  const outline = `# Q4 Website Redesign
Modernize landing pages.

## Research & Architecture
- [ ] Audit analytics & conversion funnel @45m
  - [ ] Export top 20 landing pages
- [ ] Architecture workshop @1d
- [ ] Deep dive performance @1.5h`;

  const parsed = parseProjectOutlineInput(outline);
  assert.equal(parsed.title, 'Q4 Website Redesign');
  assert.equal(parsed.blueprint.headings.length, 1);
  const tasks = parsed.blueprint.headings[0].tasks;
  assert.equal(tasks.length, 3);
  assert.equal(tasks[0].title, 'Audit analytics & conversion funnel');
  assert.equal(tasks[0].durationMinutes, 45);
  assert.deepEqual(tasks[0].checklistItems, ['Export top 20 landing pages']);
  assert.equal(tasks[1].title, 'Architecture workshop');
  assert.equal(tasks[1].durationMinutes, 480);
  assert.equal(tasks[2].title, 'Deep dive performance');
  assert.equal(tasks[2].durationMinutes, 90);

  // Localization checks
  assert.equal(TRANSLATIONS.fr.repeatClearBtn, 'Désactivé');
  assert.equal(TRANSLATIONS.it.repeatClearBtn, 'Disattivo');
  assert.equal(TRANSLATIONS.en.actionFocusTimerMinutes(480), 'Start Focus Timer (8h)');
  assert.equal(TRANSLATIONS.de.actionFocusTimerMinutes(480), 'Fokus-Timer starten (8h)');
  assert.equal(TRANSLATIONS.en.actionFocusTimerMinutes(720), 'Start Focus Timer (1d 4h)');
  assert.equal(TRANSLATIONS.de.actionFocusTimerMinutes(25), 'Fokus-Timer starten (25m)');

  // Localized German Roadmap PDF header
  const today = new Date(2026, 2, 1, 0, 0, 0, 0).getTime(); // Mar 1 -> Mär in de
  const layout = buildProjectRoadmapLayout(
    { id: 'p-de', title: 'Migrationskonzept', deadline: null },
    [],
    [],
    today,
    'Allgemein'
  );
  const pdfBytes = generateRoadmapPdfBytes(layout, 'Migrationskonzept', {
    lang: 'de',
    ungroupedTitle: 'Allgemein'
  });
  const pdfAscii = new TextDecoder('ascii').decode(pdfBytes);
  assert.ok(pdfAscii.includes('Phase / Aufgabe'), 'German PDF must localize Phase / To-Do header');
  assert.ok(pdfAscii.includes('M\\344r'), 'German PDF must localize March as Mär (WinAnsi octal \\344)');
});

