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
