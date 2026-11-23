import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { APP_BUILD_CODE, APP_VERSION } from '../src/domain/types.ts';
import { TRANSLATIONS } from '../src/i18n/translations.ts';

const SRC_DIR = path.resolve(import.meta.dirname, '../src');

test('v3.0.1 build constants and localized UX strings exist across all 5 languages', () => {
  assert.ok(APP_VERSION.startsWith('3.'));
  assert.ok(APP_BUILD_CODE >= 112);

  for (const lang of ['en', 'de', 'fr', 'it', 'es'] as const) {
    const dict = TRANSLATIONS[lang];
    assert.ok(dict.timelineFullscreen.trim().length > 0, `${lang}.timelineFullscreen missing`);
    assert.ok(
      dict.timelineExitFullscreen.trim().length > 0,
      `${lang}.timelineExitFullscreen missing`
    );
    assert.ok(dict.quickEditAction.trim().length > 0, `${lang}.quickEditAction missing`);
    assert.ok(dict.openDetailsAction.trim().length > 0, `${lang}.openDetailsAction missing`);
  }
});

test('v3.0.0-beta.5 Issue 1 + Polish: sticky non-clipped Soft Bento modal footer and flash-free 160ms softBentoModalEnter animation', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components/TaskItem.tsx'), 'utf8');

  // Strict cross-suite CSS invariant
  assert.ok(!css.includes('container-type:'), 'index.css must never contain container-type:');

  // Modal backdrop viewport padding + 160ms cubic-bezier(0.2, 0, 0, 1) entrance
  const backdropBlock = css.match(/\.modal-backdrop\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(backdropBlock.includes('padding: 16px'));
  assert.ok(backdropBlock.includes('box-sizing: border-box'));

  const taskBackdropBlock =
    css.match(/\[data-testid="task-modal-backdrop"\]\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(taskBackdropBlock.includes('padding: 16px'));
  assert.ok(taskBackdropBlock.includes('160ms cubic-bezier(0.2, 0, 0, 1)'));

  // Keyframe scale(0.97) -> scale(1)
  assert.ok(css.includes('@keyframes softBentoModalEnter'));
  assert.ok(css.includes('scale(0.97)'));
  assert.ok(css.includes('animation: softBentoModalEnter 160ms cubic-bezier(0.2, 0, 0, 1)'));

  // Modal card flex column + min(88vh, calc(100dvh - 32px)) + overflow: hidden
  const cardBlock =
    css.match(/\.task-card-expanded\.task-bento-modal-card\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(cardBlock.includes('max-height: 88vh'));
  assert.ok(cardBlock.includes('max-height: min(88vh, calc(100dvh - 32px))'));
  assert.ok(cardBlock.includes('display: flex'));
  assert.ok(cardBlock.includes('flex-direction: column'));
  assert.ok(cardBlock.includes('overflow: hidden'));

  // Sticky header, scrollable body, and non-clipped unified footer
  const headerBlock = css.match(/\.task-bento-modal-header\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(headerBlock.includes('flex-shrink: 0'));

  const bodyBlock = css.match(/\.task-bento-modal-body\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(bodyBlock.includes('flex: 1 1 auto'));
  assert.ok(bodyBlock.includes('min-height: 0'));
  assert.ok(bodyBlock.includes('overflow-y: auto'));

  const footerBlock =
    css.match(/\.task-bento-modal-card\s*>\s*\.task-card-unified-footer\s*\{([^}]*)\}/)?.[1] ??
    '';
  assert.ok(footerBlock.includes('flex-shrink: 0'));
  assert.ok(footerBlock.includes('padding: 12px 18px 14px'));
  assert.ok(footerBlock.includes('flex-wrap: wrap'));
  assert.ok(footerBlock.includes('gap: 8px'));

  // Footer hosts Focus Timer, More menu, and Done button together
  const footerIdx = taskItemSrc.indexOf('data-testid="task-unified-footer"');
  const collapsedSectionIdx = taskItemSrc.indexOf(
    '// ==================== Collapsed Row View ===================='
  );
  assert.ok(footerIdx !== -1 && collapsedSectionIdx > footerIdx);
  const footerJsxSlice = taskItemSrc.slice(footerIdx, collapsedSectionIdx);
  assert.ok(footerJsxSlice.includes('data-testid="task-start-focus-btn"'));
  assert.ok(footerJsxSlice.includes('data-testid="task-more-menu-btn"'));
  assert.ok(footerJsxSlice.includes('data-testid="task-modal-done-btn"'));
});

test('v3.0.0-beta.5 Issue 2 + v3.0.1 streamlined context menu: 1-click interactive badges + inline quick-rename (✏️ / F2) + streamlined context menus', () => {
  const taskItemSrc = fs.readFileSync(path.join(SRC_DIR, 'components/TaskItem.tsx'), 'utf8');
  const boardRoadmapSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/ProjectBoardAndRoadmap.tsx'),
    'utf8'
  );
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');

  // Collapsed row interactive badge testids & popovers
  const requiredQuickEditIds = [
    'isInlineQuickEditing',
    'task-row-quick-edit-btn',
    'task-ctx-edit-btn',
    'task-ctx-milestone-btn',
    'task-row-inline-title-input',
    'task-row-inline-save-btn',
    'task-row-quick-strip',
    'task-quick-strip-when',
    'task-quick-strip-deadline',
    'task-quick-strip-effort',
    'task-quick-strip-tags',
    'task-quick-strip-open-details',
    'task-row-when-badge',
    'task-row-deadline-badge',
    'task-row-repeat-badge',
    'task-row-reminder-badge',
    'task-row-waiting-badge',
    'task-row-timeblock-pill',
    'task-row-timeblock-popover',
    'task-row-duration-pill',
    'task-row-duration-popover',
    'task-row-effort-pill',
    'task-row-effort-popover'
  ];
  for (const id of requiredQuickEditIds) {
    assert.ok(taskItemSrc.includes(id), `TaskItem.tsx missing inline quick-edit identifier: ${id}`);
  }

  // v3.0.1 Streamlined Task Context Menu removes crowded secondary items and inline chips
  assert.ok(
    !taskItemSrc.includes('task-ctx-quick-edit-btn'),
    'TaskItem.tsx context menu must omit Quick Edit (kept on row pencil button & F2)'
  );
  assert.ok(
    !taskItemSrc.includes('context-menu-inline-chips'),
    'TaskItem.tsx context menu must omit inline duration/effort chip rows'
  );
  const ctxMenuIdx = taskItemSrc.indexOf('{/* Right-Click TaskContextDropdownMenu');
  assert.ok(ctxMenuIdx !== -1);
  const ctxMenuSlice = taskItemSrc.slice(ctxMenuIdx);
  assert.ok(ctxMenuSlice.includes('data-testid="task-ctx-edit-btn"'));
  assert.ok(ctxMenuSlice.includes('data-testid="task-ctx-milestone-btn"'));
  assert.ok(!ctxMenuSlice.includes('onOpenRepeatModal'));
  assert.ok(!ctxMenuSlice.includes('onOpenDependenciesModal'));
  assert.ok(!ctxMenuSlice.includes('runMagicBreakdown'));
  assert.ok(!ctxMenuSlice.includes('onStartFocusTimer'));

  // Kanban Board card context menu matches streamlined v3.0.1 menu
  const cardCtxIdx = boardRoadmapSrc.indexOf('{cardCtxMenu &&');
  const roadmapSectionIdx = boardRoadmapSrc.indexOf('// ==================== 2. Roadmap Gantt View');
  assert.ok(cardCtxIdx !== -1 && roadmapSectionIdx > cardCtxIdx);
  const cardCtxSlice = boardRoadmapSrc.slice(cardCtxIdx, roadmapSectionIdx);
  assert.ok(cardCtxSlice.includes('data-testid="task-ctx-edit-btn"'));
  assert.ok(cardCtxSlice.includes('data-testid="task-ctx-milestone-btn"'));
  assert.ok(cardCtxSlice.includes('t.openDetailsAction'));
  assert.ok(cardCtxSlice.includes('t.actionCancelTodo'));
  assert.ok(!cardCtxSlice.includes('onOpenRepeatModal'));
  assert.ok(!cardCtxSlice.includes('onOpenDependenciesModal'));
  assert.ok(!cardCtxSlice.includes('onStartFocusTimer'));

  // Effort cycling order: null -> QUICK_WIN -> MEDIUM -> DEEP_FOCUS -> null
  assert.ok(taskItemSrc.includes("if (!cur) return 'QUICK_WIN';"));
  assert.ok(taskItemSrc.includes("if (cur === 'QUICK_WIN') return 'MEDIUM';"));
  assert.ok(taskItemSrc.includes("if (cur === 'MEDIUM') return 'DEEP_FOCUS';"));

  // F2 keyboard shortcut in App.tsx triggers inline quick edit on single selected task
  assert.ok(appSrc.includes("e.key === 'F2'"));
  assert.ok(appSrc.includes('setInlineQuickEditRequest'));
  assert.ok(appSrc.includes('quickEditRequestNonce='));
});

test('v3.0.0-beta.5 Issue 3: calm single-row topbar preserving data-topbar-tier while Sidebar & Settings retain 1-click access', () => {
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');
  const sidebarSrc = fs.readFileSync(path.join(SRC_DIR, 'components/Sidebar.tsx'), 'utf8');

  // Critical topbar invariant
  assert.ok(
    appSrc.includes('data-topbar-tier={resolveTopBarLayoutTier(workspaceWidthPx)}'),
    'App.tsx must preserve data-topbar-tier={resolveTopBarLayoutTier(workspaceWidthPx)}'
  );

  // Extract only the <header className="workspace-topbar" ...>...</header> block
  const topbarStart = appSrc.indexOf('<header\n            className="workspace-topbar"');
  const topbarEnd = appSrc.indexOf('</header>', topbarStart);
  assert.ok(topbarStart !== -1 && topbarEnd > topbarStart, 'workspace-topbar header must exist');
  const topbarMarkup = appSrc.slice(topbarStart, topbarEnd);

  // Kept controls: Sidebar toggle, Quick Find, Voice Capture, Gemini, Cloud Sync, Settings
  assert.ok(topbarMarkup.includes('topbarTooltipQuickFind'));
  assert.ok(topbarMarkup.includes('topbar-voice-capture-btn'));
  assert.ok(topbarMarkup.includes('gemini-pill'));
  assert.ok(topbarMarkup.includes('sync-icon-btn'));
  assert.ok(topbarMarkup.includes('topbarTooltipSettings'));

  // Removed redundant topbar clutter
  assert.ok(!topbarMarkup.includes('topbar-weekly-review-btn'));
  assert.ok(!topbarMarkup.includes('topbar-templates-btn'));
  assert.ok(!topbarMarkup.includes('topbarTooltipManageAreas'));
  assert.ok(!topbarMarkup.includes('topbarTooltipManageTags'));
  assert.ok(!topbarMarkup.includes('topbarTooltipNotifications'));
  assert.ok(!topbarMarkup.includes('topbarTooltipLanguage'));

  // 1-click access preserved in Sidebar
  assert.ok(sidebarSrc.includes('sidebar-weekly-review-btn'));
  assert.ok(sidebarSrc.includes('sidebar-templates-btn'));
  assert.ok(sidebarSrc.includes('sidebar-manage-areas-btn'));
  assert.ok(sidebarSrc.includes('sidebar-manage-categories-btn'));
});

test('v3.0.0-beta.5 Issue 4: Smart View titles never break mid-word, Project header gives full-width title + consolidated menu, Sidebar wraps project names to 2 lines', () => {
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');
  const sidebarSrc = fs.readFileSync(path.join(SRC_DIR, 'components/Sidebar.tsx'), 'utf8');
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');

  // Smart View title non-breaking CSS
  const smartTitleCss = css.match(/\.view-title\.smart-view-title\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(smartTitleCss.includes('white-space: nowrap'));

  const smartRowCss =
    css.match(/\.view-title-row\.smart-view-title-row\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(smartRowCss.includes('flex-wrap: wrap'));

  // Project & Area title clean word-boundary wrapping (never break-all)
  const projTitleCss =
    css.match(/\.view-title\.project-view-title,\s*\.view-title\.area-view-title\s*\{([^}]*)\}/)?.[1] ??
    '';
  assert.ok(projTitleCss.includes('word-break: normal'));
  assert.ok(projTitleCss.includes('overflow-wrap: break-word'));
  assert.ok(!projTitleCss.includes('break-all'));

  // Project header structure: title={project.title}, Edit + More menu, Row 2 controls, no duplicate Export Roadmap in header
  assert.ok(appSrc.includes('className="view-title project-view-title"'));
  assert.ok(appSrc.includes('data-testid="project-edit-btn"'));
  assert.ok(appSrc.includes('data-testid="project-header-more-btn"'));
  assert.ok(appSrc.includes('data-testid="project-header-more-menu"'));
  assert.ok(!appSrc.includes('data-testid="project-export-roadmap-btn"'));

  // Sidebar 2-line project labels with title tooltip
  assert.ok(sidebarSrc.includes('project-nav-item'));
  assert.ok(sidebarSrc.includes('title={proj.title}'));
  const sidebarProjLabelCss =
    css.match(/\.nav-item\.project-nav-item\s+\.nav-item-label\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(sidebarProjLabelCss.includes('-webkit-line-clamp: 2'));
  assert.ok(sidebarProjLabelCss.includes('white-space: normal'));
  assert.ok(sidebarProjLabelCss.includes('overflow-wrap: break-word'));
});

test('v3.0.0-beta.5 Issue 5: Full Screen option for Today Daily Timeline and Project Roadmap with Escape & nav auto-reset', () => {
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');
  const roadmapSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/ProjectBoardAndRoadmap.tsx'),
    'utf8'
  );

  // App.tsx state, auto-reset, Escape handling, and Today Timeline fullscreen bar/button
  assert.ok(appSrc.includes('const [isTimelineFullScreen, setIsTimelineFullScreen] = useState(false)'));
  assert.ok(appSrc.includes('isTodayTimelineFullScreen'));
  assert.ok(appSrc.includes('isRoadmapFullScreen'));
  assert.ok(appSrc.includes('data-testid="today-timeline-fullscreen-btn"'));
  assert.ok(appSrc.includes('data-testid="today-timeline-fullscreen-bar"'));
  assert.ok(appSrc.includes('visible={effectiveSidebarVisible && !isAnyTimelineFullScreen}'));

  // ProjectRoadmapWorkspace full screen toggle + project title in toolbar
  assert.ok(roadmapSrc.includes('data-testid="roadmap-fullscreen-btn"'));
  assert.ok(roadmapSrc.includes('data-testid="roadmap-fullscreen-project-title"'));
  assert.ok(roadmapSrc.includes('t.timelineExitFullscreen'));
  assert.ok(roadmapSrc.includes('t.timelineFullscreen'));
});
