import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

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
