import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { TRANSLATIONS } from '../src/i18n/translations.ts';
import type { SupportedLang } from '../src/domain/types.ts';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.resolve(__dirname, '..', 'src');

test('v3.0.0-beta.17 Inspector localization strings are defined across all 5 languages (en, de, fr, it, es)', () => {
  const langs: SupportedLang[] = ['en', 'de', 'fr', 'it', 'es'];
  for (const lang of langs) {
    const tr = TRANSLATIONS[lang];
    assert.ok(tr.inspectorTitle.trim().length > 0, `Missing inspectorTitle in ${lang}`);
    assert.ok(
      tr.inspectorOverviewTitle.trim().length > 0,
      `Missing inspectorOverviewTitle in ${lang}`
    );
    assert.ok(
      tr.inspectorUpcomingDeadlines.trim().length > 0,
      `Missing inspectorUpcomingDeadlines in ${lang}`
    );
    assert.ok(
      tr.inspectorToggleTooltip.trim().length > 0,
      `Missing inspectorToggleTooltip in ${lang}`
    );
  }

  assert.equal(TRANSLATIONS.en.inspectorTitle, 'Inspector');
  assert.equal(TRANSLATIONS.de.inspectorTitle, 'Inspektor');
  assert.equal(TRANSLATIONS.fr.inspectorTitle, 'Inspecteur');
  assert.equal(TRANSLATIONS.it.inspectorTitle, 'Ispettore');
  assert.equal(TRANSLATIONS.es.inspectorTitle, 'Inspector');

  assert.equal(TRANSLATIONS.en.inspectorOverviewTitle, 'Overview');
  assert.equal(TRANSLATIONS.de.inspectorOverviewTitle, 'Übersicht');
  assert.equal(TRANSLATIONS.fr.inspectorOverviewTitle, 'Aperçu');
  assert.equal(TRANSLATIONS.it.inspectorOverviewTitle, 'Panoramica');
  assert.equal(TRANSLATIONS.es.inspectorOverviewTitle, 'Resumen');

  assert.equal(
    TRANSLATIONS.en.inspectorUpcomingDeadlines,
    'Upcoming Deadlines & Milestones'
  );
  assert.equal(
    TRANSLATIONS.de.inspectorUpcomingDeadlines,
    'Anstehende Fristen & Meilensteine'
  );
  assert.equal(
    TRANSLATIONS.fr.inspectorUpcomingDeadlines,
    'Échéances & jalons à venir'
  );
  assert.equal(
    TRANSLATIONS.it.inspectorUpcomingDeadlines,
    'Scadenze e traguardi imminenti'
  );
  assert.equal(
    TRANSLATIONS.es.inspectorUpcomingDeadlines,
    'Próximas fechas límite e hitos'
  );

  assert.equal(TRANSLATIONS.en.inspectorToggleTooltip, 'Toggle Inspector Panel');
  assert.equal(
    TRANSLATIONS.de.inspectorToggleTooltip,
    'Inspektor-Seitenleiste ein-/ausblenden'
  );
  assert.equal(
    TRANSLATIONS.fr.inspectorToggleTooltip,
    'Afficher/masquer le panneau Inspecteur'
  );
  assert.equal(
    TRANSLATIONS.it.inspectorToggleTooltip,
    'Mostra/nascondi pannello Ispettore'
  );
  assert.equal(
    TRANSLATIONS.es.inspectorToggleTooltip,
    'Mostrar/ocultar panel Inspector'
  );
});

test('v3.0.0-beta.17 Wide-screen Soft Bento Inspector layout & CSS invariants hold (no container-type, 356px panel, 100%/1380px workspace container, zero hover shift, collision-free FAB)', () => {
  const css = fs.readFileSync(path.join(SRC_DIR, 'index.css'), 'utf8');
  const appSrc = fs.readFileSync(path.join(SRC_DIR, 'App.tsx'), 'utf8');
  const inspectorSrc = fs.readFileSync(
    path.join(SRC_DIR, 'components/WorkspaceInspectorPanel.tsx'),
    'utf8'
  );

  // Strict global CSS invariant
  assert.ok(!css.includes('container-type:'), 'index.css must never contain container-type:');

  // 1. Inspector panel width 356px & vertical scroll
  const panelBlock = css.match(/\.workspace-inspector-panel\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(panelBlock.includes('width: 356px;'), 'Inspector panel must be 356px wide');
  assert.ok(panelBlock.includes('overflow-y: auto;'), 'Inspector panel must scroll vertically');

  // 2. Workspace split & zero wasted white space
  const scrollBlock = css.match(/\.workspace-scroll\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(scrollBlock.includes('flex: 1 1 auto;'));
  assert.ok(scrollBlock.includes('min-width: 0;'));

  const containerBlock = css.match(/\.workspace-container\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(
    containerBlock.includes('max-width: 1380px;'),
    'Collapsed inspector canvas must expand up to 1380px instead of narrow 960px'
  );
  assert.ok(containerBlock.includes('margin: 0;'));

  const containerOpenBlock =
    css.match(/\.workspace-container\.inspector-open\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(
    containerOpenBlock.includes('max-width: 100%;'),
    'Open inspector canvas must use max-width: 100% with zero wasted gap'
  );

  // 3. Magic Plus FAB avoids Inspector Panel (right: 380px) and lifts above .multi-select-bar
  const fabSplitBlock =
    css.match(/\.workspace\.inspector-split-open\s+\.magic-fab-circle\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(fabSplitBlock.includes('right: 380px;'));

  const fabMultiBlock =
    css.match(/\.workspace\.multi-bar-visible\s+\.magic-fab-circle\s*\{([^}]*)\}/)?.[1] ?? '';
  assert.ok(fabMultiBlock.includes('bottom: 78px;'));

  // 4. Zero hover dimension shift on inspector cards & rows
  for (const hoverPattern of [
    /\.inspector-bento-card:hover\s*\{([^}]*)\}/,
    /\.inspector-mini-task-btn:hover\s*\{([^}]*)\}/,
    /\.inspector-focus-timer-btn:hover\s*\{([^}]*)\}/
  ]) {
    const hoverBody = css.match(hoverPattern)?.[1] ?? '';
    assert.ok(hoverBody.length > 0, `Missing hover rule for ${hoverPattern}`);
    assert.ok(!hoverBody.includes('padding'), `Hover must not alter padding: ${hoverPattern}`);
    assert.ok(
      !hoverBody.includes('border-width'),
      `Hover must not alter border-width: ${hoverPattern}`
    );
    assert.ok(!hoverBody.includes('transform'), `Hover must not transform size: ${hoverPattern}`);
    assert.ok(!hoverBody.includes('width'), `Hover must not alter width: ${hoverPattern}`);
    assert.ok(!hoverBody.includes('height'), `Hover must not alter height: ${hoverPattern}`);
  }

  // 5. App.tsx persistence, 1200px breakpoint, topbar toggle & Mode A / Mode B testids
  assert.ok(appSrc.includes("'thingsbook_inspector_open_v1'"));
  assert.ok(appSrc.includes('window.innerWidth >= 1200'));
  assert.ok(appSrc.includes('data-testid="topbar-inspector-toggle"'));
  assert.ok(appSrc.includes('!isAnyTimelineFullScreen && !isWideProjectCanvas'));

  assert.ok(inspectorSrc.includes('data-testid="workspace-inspector-panel"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-open-modal-btn"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-milestone-btn"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-task-title-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-task-schedule-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-task-checklist-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-task-notes-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-overview-capacity-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-overview-timeline-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-overview-deadlines-card"'));
  assert.ok(inspectorSrc.includes('data-testid="inspector-overview-pinned-card"'));
});
