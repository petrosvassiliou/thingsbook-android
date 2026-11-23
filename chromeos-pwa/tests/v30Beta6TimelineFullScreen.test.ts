import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { APP_BUILD_CODE, APP_VERSION } from '../src/domain/types.ts';

const SRC_DIR = path.resolve(import.meta.dirname, '../src');

test('v3.0.1+: ChromeOS PWA keeps window orientation untouched (no orientation lock)', () => {
  assert.ok(APP_VERSION.startsWith('3.'));
  assert.ok(APP_BUILD_CODE >= 112);

  const filesToCheck = [
    'App.tsx',
    'components/TodayTimelineView.tsx',
    'components/ProjectBoardAndRoadmap.tsx'
  ];
  for (const rel of filesToCheck) {
    const src = fs.readFileSync(path.join(SRC_DIR, rel), 'utf8');
    assert.ok(
      !src.includes('orientation.lock'),
      `${rel} must not lock window/screen orientation on ChromeOS PWA`
    );
  }
});

test('v3.0.0-beta.6 App.tsx: pure Timeline-only Full Screen clears state, hides Magic Plus FAB & banners, blocks non-Escape shortcuts, and removes List|Timeline switcher from fullscreen bar', () => {
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');

  // 1. Entering Full Screen clears expandedTaskId, selectedTaskIds, magicFabOpen, and popovers
  const enterIdx = appSrc.indexOf('const enterTimelineFullScreen = useCallback(');
  assert.ok(enterIdx !== -1, 'enterTimelineFullScreen callback must exist in App.tsx');
  const enterSlice = appSrc.slice(enterIdx, enterIdx + 420);
  assert.ok(enterSlice.includes('setExpandedTaskId(null)'));
  assert.ok(enterSlice.includes('setSelectedTaskIds([])'));
  assert.ok(enterSlice.includes('setMagicFabOpen(false)'));
  assert.ok(enterSlice.includes('setIsTimelineFullScreen(true)'));

  // 2. Magic Plus FAB and floating multi-select bar are hidden whenever isAnyTimelineFullScreen is true
  const fabGuardIdx = appSrc.indexOf('{!isAnyTimelineFullScreen && (\n          <MagicPlusFab');
  assert.ok(
    fabGuardIdx !== -1,
    'MagicPlusFab must be hidden when isTodayTimelineFullScreen || isRoadmapFullScreen is true'
  );
  assert.ok(
    appSrc.includes('{!isAnyTimelineFullScreen && selectedTaskIds.length > 0 && !expandedTaskId'),
    'Multi-selection bar must be hidden in Timeline/Roadmap Full Screen'
  );
  assert.ok(
    appSrc.includes('{!isAnyTimelineFullScreen && focusTimer && ('),
    'FocusTimerBanner must be hidden in Timeline/Roadmap Full Screen'
  );

  // 3. Global keydown handler allows Escape to exit Full Screen, then returns early before all other shortcuts
  const escapeExitIdx = appSrc.indexOf('if (isTimelineFullScreen) return setIsTimelineFullScreen(false);');
  const earlyReturnIdx = appSrc.indexOf('if (isTodayTimelineFullScreen || isRoadmapFullScreen) {');
  const enterOpenShortcutIdx = appSrc.indexOf("(e.key === 'Enter' || e.key === ' ')");
  const ctrlNewTodoIdx = appSrc.indexOf('void createNewTodoInCurrentContext(null);');
  const ctrlQuickFindIdx = appSrc.indexOf("if (lower === 'f' || lower === 'k')");

  assert.ok(escapeExitIdx !== -1, 'Escape must exit isTimelineFullScreen');
  assert.ok(
    earlyReturnIdx > escapeExitIdx,
    'Non-Escape shortcut guard must appear immediately after Escape handling'
  );
  assert.ok(
    earlyReturnIdx < enterOpenShortcutIdx &&
      earlyReturnIdx < ctrlNewTodoIdx &&
      earlyReturnIdx < ctrlQuickFindIdx,
    'Non-Escape shortcut guard must return early before Enter/Space, Ctrl/Cmd+N, and Ctrl/Cmd+F'
  );

  // 4. today-timeline-fullscreen-bar renders ONLY Today title + capacity summary on left and Exit Full Screen on right (no List|Timeline toggle)
  const fsBarStart = appSrc.indexOf('data-testid="today-timeline-fullscreen-bar"');
  const normalHeaderStart = appSrc.indexOf('<div className="view-header"', fsBarStart);
  assert.ok(fsBarStart !== -1 && normalHeaderStart > fsBarStart);
  const fsBarSlice = appSrc.slice(fsBarStart, normalHeaderStart);

  assert.ok(fsBarSlice.includes('{t.viewToday}'));
  assert.ok(fsBarSlice.includes('data-testid="today-capacity-pill"'));
  assert.ok(fsBarSlice.includes('data-testid="today-timeline-fullscreen-btn"'));
  assert.ok(fsBarSlice.includes('{t.timelineExitFullscreen}'));
  assert.ok(
    !fsBarSlice.includes('today-mode-toggle'),
    'today-timeline-fullscreen-bar must not render List | Timeline segmented toggle'
  );
  assert.ok(
    !fsBarSlice.includes('today-mode-list-btn'),
    'today-timeline-fullscreen-bar must not render List mode button'
  );

  // 5. TodayTimelineView receives isFullScreen={isTodayTimelineFullScreen}
  const todayTimelineCallIdx = appSrc.indexOf('<TodayTimelineView');
  const todayTimelineCallSlice = appSrc.slice(todayTimelineCallIdx, todayTimelineCallIdx + 550);
  assert.ok(
    todayTimelineCallSlice.includes('isFullScreen={isTodayTimelineFullScreen}'),
    'App.tsx must pass isFullScreen={isTodayTimelineFullScreen} to TodayTimelineView'
  );
  assert.ok(
    todayTimelineCallSlice.includes('if (isTodayTimelineFullScreen) return;'),
    'App.tsx must guard TodayTimelineView onExpandTask when in Full Screen'
  );
});

test('v3.0.0-beta.6 TodayTimelineView.tsx: hides Unscheduled tray & card action buttons and disables checkboxes & task modal clicks in Full Screen', () => {
  const timelineSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/TodayTimelineView.tsx'),
    'utf8'
  );

  // Prop acceptance and root fullscreen class
  assert.ok(timelineSrc.includes('isFullScreen?: boolean;'));
  assert.ok(timelineSrc.includes('isFullScreen = false'));
  assert.ok(
    timelineSrc.includes("className={`v2-timeline-root ${isFullScreen ? 'fullscreen' : ''}`}")
  );

  // Checkbox completion disabled in Full Screen
  assert.ok(timelineSrc.includes('disabled={isFullScreen}'));
  assert.ok(timelineSrc.includes('if (isFullScreen) return;'));

  // Clicking task card disabled in Full Screen
  assert.ok(
    timelineSrc.includes('onClick={isFullScreen ? undefined : () => onExpandTask(tk.id)}')
  );

  // Card action buttons (+15m, Focus Timer, Clear timeblock) hidden in Full Screen
  assert.ok(
    timelineSrc.includes('{!isFullScreen && (\n                    <div className="v2-timeline-card-actions">'),
    'TodayTimelineView must hide .v2-timeline-card-actions in Full Screen'
  );

  // Unscheduled Today Tray hidden in Full Screen
  assert.ok(
    timelineSrc.includes('{!isFullScreen && timeline.unscheduledTasks.length > 0 && ('),
    'TodayTimelineView must hide unscheduled-today-tray in Full Screen'
  );
});

test('v3.0.0-beta.6 ProjectRoadmapWorkspace: hides Export Roadmap & Unscheduled tray and disables all task/phase mutations in Full Screen while keeping scroll, Jump to Today, Zoom, and Exit Full Screen', () => {
  const roadmapFileSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/ProjectBoardAndRoadmap.tsx'),
    'utf8'
  );
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  const wsStart = roadmapFileSrc.indexOf('export const ProjectRoadmapWorkspace');
  assert.ok(wsStart !== -1, 'ProjectRoadmapWorkspace must exist');
  const roadmapSrc = roadmapFileSrc.slice(wsStart);

  // 1. Toolbar left keeps Jump to Today and Zoom toggle; toolbar right hides +To-Do, +Add Phase, and Export Roadmap when isFullScreen
  const toolbarStart = roadmapSrc.indexOf('<div className="roadmap-toolbar">');
  const canvasStart = roadmapSrc.indexOf('<div ref={scrollRef} className="roadmap-canvas-scroll">');
  assert.ok(toolbarStart !== -1 && canvasStart > toolbarStart);
  const toolbarSlice = roadmapSrc.slice(toolbarStart, canvasStart);

  assert.ok(toolbarSlice.includes('data-testid="roadmap-jump-today-btn"'));
  assert.ok(toolbarSlice.includes('className="v2-segmented-toggle roadmap-zoom-toggle"'));
  assert.ok(toolbarSlice.includes('data-testid="roadmap-fullscreen-btn"'));

  const rightToolbarIdx = toolbarSlice.indexOf('<div className="roadmap-toolbar-right">');
  const rightToolbarSlice = toolbarSlice.slice(rightToolbarIdx);
  const notFsBlockIdx = rightToolbarSlice.indexOf('{!isFullScreen && (');
  const exportBtnIdx = rightToolbarSlice.indexOf('data-testid="roadmap-export-btn"');
  const fsToggleIdx = rightToolbarSlice.indexOf('data-testid="roadmap-fullscreen-btn"');
  assert.ok(
    notFsBlockIdx !== -1 && exportBtnIdx > notFsBlockIdx && fsToggleIdx > exportBtnIdx,
    'Export Roadmap (+ To-Do / + Add Phase) must be gated inside {!isFullScreen && (...)} while Exit Full Screen remains visible'
  );

  // 2. Unscheduled section below Gantt timeline is hidden in Full Screen
  assert.ok(
    roadmapSrc.includes(
      '{!isFullScreen &&\n        (layout.unscheduledTasks.length > 0 || layout.unscheduledPhases.length > 0) && ('
    ),
    'roadmap-unscheduled-tray must be hidden when isFullScreen is true'
  );

  // 3. Task & phase mutation options hidden/disabled inside Gantt rows & bars
  assert.ok(
    roadmapSrc.includes('{!isFullScreen && (\n                              <span className="roadmap-bar-step-controls">'),
    'Phase bar -1d/+1d step controls must be hidden in Full Screen'
  );
  assert.ok(
    roadmapSrc.includes('{!isFullScreen && (\n                            <div className="roadmap-cell-quick-btns">'),
    'Lane & task cell quick buttons (-1d/+1d/edit/add) must be hidden in Full Screen'
  );
  assert.ok(roadmapSrc.includes('if (!barDrag || isFullScreen) return;'));
  assert.ok(roadmapSrc.includes('if (isFullScreen || !lane.heading) return;'));
  assert.ok(roadmapSrc.includes('draggable={!isFullScreen}'));
  assert.ok(roadmapSrc.includes('disabled={isFullScreen}'));

  // Count `if (isFullScreen) return;` guards across phase/task pointerdown, click, double-click, checkbox, and step handlers
  const fsGuardMatches = roadmapSrc.match(/if \(isFullScreen\) return;/g) ?? [];
  assert.ok(
    fsGuardMatches.length >= 8,
    `Expected at least 8 isFullScreen interaction guards in ProjectRoadmapWorkspace, found ${fsGuardMatches.length}`
  );

  // 4. CSS invariant: full viewport height for pure Gantt timeline and never container-type
  assert.ok(!css.includes('container-type:'), 'index.css must never contain container-type:');
  assert.ok(css.includes('.project-roadmap-workspace.fullscreen .roadmap-canvas-scroll'));
  assert.ok(css.includes('.today-timeline-fullscreen-body .v2-timeline-root'));
});
