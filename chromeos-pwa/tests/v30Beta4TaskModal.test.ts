import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  shouldAutoFocusTitleOnExpand,
  shouldShowQuickThisEveningPill
} from '../src/domain/v2Utils.ts';

const SRC_DIR = path.resolve(import.meta.dirname, '../src');

test('v3.0.0-beta.4 App.tsx hosts the Unified Soft Bento Task Modal across all views with collapsed list rows, Today Timeline modal support, sub-modal z-stacking, and Enter/Space shortcut', () => {
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');

  // 1. renderTaskRow strictly keeps list rows collapsed (isExpanded={false})
  const renderTaskRowIdx = appSrc.indexOf('const renderTaskRow =');
  assert.ok(renderTaskRowIdx !== -1, 'renderTaskRow must exist in App.tsx');
  const renderTaskRowSlice = appSrc.slice(renderTaskRowIdx, renderTaskRowIdx + 900);
  assert.ok(
    renderTaskRowSlice.includes('isExpanded={false}'),
    'renderTaskRow must pass isExpanded={false} so list rows never expand inline'
  );
  assert.ok(
    !renderTaskRowSlice.includes('isExpanded={expandedTaskId === tk.id}'),
    'renderTaskRow must not expand TaskItem inline in list views'
  );

  // 2. TodayTimelineView opens the modal directly without forcing setTodayViewMode('list')
  const timelineIdx = appSrc.indexOf('<TodayTimelineView');
  assert.ok(timelineIdx !== -1, '<TodayTimelineView> must exist in App.tsx');
  const timelineSlice = appSrc.slice(timelineIdx, timelineIdx + 600);
  assert.ok(
    timelineSlice.includes('setExpandedTaskId(taskId)'),
    'TodayTimelineView onExpandTask must open expandedTaskId'
  );
  assert.ok(
    !timelineSlice.includes("setTodayViewMode('list')"),
    'TodayTimelineView onExpandTask must not force setTodayViewMode("list")'
  );

  // 3. Unified Soft Bento Task Modal is NOT gated on activeProjectViewMode !== 'list'
  const modalsIdx = appSrc.indexOf('{/* ==================== MODALS ==================== */}');
  assert.ok(modalsIdx !== -1, 'MODALS section comment must exist in App.tsx');
  const modalHostSlice = appSrc.slice(modalsIdx, modalsIdx + 1800);
  assert.ok(
    modalHostSlice.includes('data-testid="task-modal-backdrop"'),
    'Task modal backdrop must render in global MODALS section'
  );
  assert.ok(
    !modalHostSlice.includes("activeProjectViewMode !== 'list'"),
    'Unified task modal must not be restricted to Board/Roadmap project modes'
  );
  assert.ok(
    modalHostSlice.includes("isTodayView={activeNav.kind === 'smart' && activeNav.view === 'TODAY'}"),
    'Unified task modal must forward isTodayView for Today quick-evening action'
  );

  // 4. Sub-modals render AFTER the Task Modal backdrop so they stack cleanly above it
  const taskBackdropPos = appSrc.indexOf('data-testid="task-modal-backdrop"');
  const whenModalPos = appSrc.indexOf('<WhenModal');
  const deadlineModalPos = appSrc.indexOf('<DeadlineModal');
  const repeatModalPos = appSrc.indexOf('<RepeatModal');
  const moveModalPos = appSrc.indexOf('<MoveTaskModal');
  const tagPickerModalPos = appSrc.indexOf('<TaskTagPickerModal');
  const depModalPos = appSrc.indexOf('<TaskDependencyModal');

  assert.ok(taskBackdropPos > 0);
  assert.ok(taskBackdropPos < whenModalPos, 'Task Modal must precede WhenModal');
  assert.ok(taskBackdropPos < deadlineModalPos, 'Task Modal must precede DeadlineModal');
  assert.ok(taskBackdropPos < repeatModalPos, 'Task Modal must precede RepeatModal');
  assert.ok(taskBackdropPos < moveModalPos, 'Task Modal must precede MoveTaskModal');
  assert.ok(taskBackdropPos < tagPickerModalPos, 'Task Modal must precede TaskTagPickerModal');
  assert.ok(taskBackdropPos < depModalPos, 'Task Modal must precede TaskDependencyModal');

  // 5. Global Enter / Space shortcut opens setExpandedTaskId(selectedTaskIds[0]) when 1 task is selected
  assert.ok(
    appSrc.includes('selectedTaskIds.length === 1') &&
      appSrc.includes("(e.key === 'Enter' || e.key === ' ')") &&
      appSrc.includes('setExpandedTaskId(selectedTaskIds[0])'),
    'App.tsx must open setExpandedTaskId(selectedTaskIds[0]) on Enter or Space when 1 task is selected'
  );
});

test('v3.0.0-beta.4 TaskItem.tsx exposes soft-bento-task-modal, 2-column Soft Bento cards/tiles, smart mouse vs touch row ergonomics, and preserves all v2.1 + v3.0 data-testids', () => {
  const taskItemSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/TaskItem.tsx'),
    'utf8'
  );

  // Critical v2.1 source invariants
  assert.ok(taskItemSrc.includes('task-card-unified-footer'));
  assert.ok(taskItemSrc.includes('checklist-add-boxed'));
  assert.ok(!taskItemSrc.includes('task-card-action-bar'));

  // Preserved v2Utils helpers
  assert.equal(shouldAutoFocusTitleOnExpand(''), true);
  assert.equal(shouldAutoFocusTitleOnExpand('Existing task'), false);
  assert.equal(
    shouldShowQuickThisEveningPill({
      isTodayView: true,
      status: 'OPEN',
      scheduleState: 'TODAY'
    }),
    true
  );

  // 2-Zone Soft Bento Modal structure & 6 right-column property tiles
  const requiredModalIdentifiers = [
    'soft-bento-task-modal',
    'task-bento-modal-header',
    'task-bento-modal-body',
    'task-bento-modal-grid',
    'task-bento-main-col',
    'task-bento-props-col',
    'task-bento-notes-card',
    'task-bento-checklist-card',
    'task-bento-attachments-card',
    'task-bento-tile-when',
    'task-bento-tile-deadline-repeat',
    'task-bento-tile-duration-effort',
    'task-bento-tile-waiting-on',
    'task-bento-tile-categories',
    'task-bento-tile-container'
  ];
  for (const id of requiredModalIdentifiers) {
    assert.ok(taskItemSrc.includes(id), `Missing Soft Bento modal identifier: ${id}`);
  }

  // Preserved v2.1 + v3.0 data-testids
  const preservedTestIds = [
    'task-pin-btn',
    'task-start-focus-btn',
    'task-modal-close-btn',
    'task-modal-done-btn',
    'task-quick-this-evening-chip',
    'task-timeblock-chip',
    'task-timeblock-panel',
    'task-effort-duration-chip',
    'task-effort-duration-panel',
    'task-duration-chip-${dur}',
    "task-effort-chip-${lvl ?? 'none'}",
    'task-waiting-on-chip',
    'task-more-menu-btn',
    'task-more-waiting-on-btn',
    'task-duplicate-btn',
    'task-unified-footer',
    'task-pin-indicator',
    'task-row-waiting-badge',
    'task-row-timeblock-pill',
    'task-row-duration-pill',
    'task-row-effort-pill',
    'task-row-open-modal-${task.id}'
  ];
  for (const tid of preservedTestIds) {
    assert.ok(taskItemSrc.includes(tid), `Missing required data-testid: ${tid}`);
  }

  // Smart Mouse vs Touch row ergonomics
  assert.ok(taskItemSrc.includes('lastPointerTypeRef'));
  assert.ok(taskItemSrc.includes("lastPointerTypeRef.current === 'touch'"));
  assert.ok(taskItemSrc.includes('onDoubleClick'));
  assert.ok(taskItemSrc.includes('task-row-open-btn'));
  assert.ok(taskItemSrc.includes('task-ctx-edit-btn'));
});

test('v3.0.0-beta.4 ProjectBoardAndRoadmap.tsx wires touch tap, double-click, and edit buttons to onOpenTaskEditor while guarding Roadmap hold-drag', () => {
  const boardRoadmapSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/ProjectBoardAndRoadmap.tsx'),
    'utf8'
  );

  assert.ok(boardRoadmapSrc.includes("lastBoardPointerTypeRef.current === 'touch'"));
  assert.ok(boardRoadmapSrc.includes("lastRoadmapPointerTypeRef.current === 'touch'"));
  assert.ok(boardRoadmapSrc.includes('suppressNextBarClickRef'));
  assert.ok(boardRoadmapSrc.includes('board-card-edit-btn'));
  assert.ok(boardRoadmapSrc.includes('roadmap-task-open-modal-${tk.id}'));
});

test('v3.0.0-beta.4 index.css defines responsive 820px Soft Bento Task Modal without container-type and preserves non-truncating property pills', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  assert.ok(!css.includes('container-type:'), 'index.css must never use container-type:');
  assert.ok(css.includes('.task-card-expanded.task-bento-modal-card'));
  assert.ok(css.includes('width: min(94vw, 820px)'));
  assert.ok(css.includes('max-width: 820px'));
  assert.ok(css.includes('max-height: 88vh'));
  assert.ok(css.includes('grid-template-columns: minmax(0, 1.45fr) minmax(250px, 1fr)'));
  assert.ok(css.includes('@media (max-width: 679px)'));

  const propPillsChipBlock =
    css.match(/\.task-bento-prop-pills\s+\.chip-btn\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(propPillsChipBlock.includes('white-space: normal'));
  assert.ok(!propPillsChipBlock.includes('ellipsis'));
  assert.ok(!propPillsChipBlock.includes('nowrap'));
});
