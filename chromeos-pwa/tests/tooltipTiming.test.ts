import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_INITIAL_DELAY_MS,
  DEFAULT_WARM_HANDOFF_WINDOW_MS,
  TooltipTimingMachine,
  computeTooltipPosition,
  isTopBarIconTooltipAllowed
} from '../src/domain/tooltipTiming.ts';

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
  }
});
