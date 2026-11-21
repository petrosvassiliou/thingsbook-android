#!/usr/bin/env node
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PWA_ROOT = path.resolve(__dirname, '..');
const CHROMIUM_BIN = process.env.CHROMIUM_BIN || '/usr/bin/chromium';
const ARTIFACT_DIR =
  process.env.SMOKE_ARTIFACT_DIR ||
  '/home/antigravity/.gemini/jetski/brain/v1/default/jetski_remote_agents_3p/42d8ced1-1c8a-4431-99c9-2959b6bdab6e';

const VIEWPORT_WIDTH = 1440;
const VIEWPORT_HEIGHT = 940;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(`SMOKE ASSERTION FAILED: ${message}`);
  }
}

async function getEphemeralPort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const addr = srv.address();
      const port = typeof addr === 'object' && addr ? addr.port : 0;
      srv.close(() => resolve(port));
    });
  });
}

async function waitForHttpReady(url, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;
  let lastErr = null;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch (err) {
      lastErr = err;
    }
    await sleep(150);
  }
  throw new Error(`Timed out waiting for ${url}: ${lastErr?.message || 'unreachable'}`);
}

class CdpSession {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl);
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.consoleErrors = [];
    this.pageExceptions = [];

    this.readyPromise = new Promise((resolve, reject) => {
      this.ws.addEventListener('open', () => resolve());
      this.ws.addEventListener('error', (ev) =>
        reject(new Error(`CDP WebSocket error: ${ev?.message || 'connection failed'}`))
      );
    });

    this.ws.addEventListener('message', (event) => {
      const raw = typeof event.data === 'string' ? event.data : event.data.toString();
      const msg = JSON.parse(raw);
      if (msg.id) {
        const entry = this.pending.get(msg.id);
        if (entry) {
          this.pending.delete(msg.id);
          if (msg.error) {
            entry.reject(new Error(`CDP ${entry.method} failed: ${JSON.stringify(msg.error)}`));
          } else {
            entry.resolve(msg.result);
          }
        }
        return;
      }
      if (msg.method) {
        const handlers = this.listeners.get(msg.method);
        if (handlers) {
          for (const fn of handlers) fn(msg.params);
        }
      }
    });
  }

  on(method, handler) {
    if (!this.listeners.has(method)) {
      this.listeners.set(method, []);
    }
    this.listeners.get(method).push(handler);
  }

  async send(method, params = {}) {
    await this.readyPromise;
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject, method });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async evalJs(expression) {
    const wrapped = `(async () => { ${expression} })()`;
    const res = await this.send('Runtime.evaluate', {
      expression: wrapped,
      awaitPromise: true,
      returnByValue: true
    });
    if (res.exceptionDetails) {
      const desc =
        res.exceptionDetails.exception?.description ||
        res.exceptionDetails.text ||
        JSON.stringify(res.exceptionDetails);
      throw new Error(`Browser eval exception: ${desc}`);
    }
    return res.result?.value;
  }

  async waitForSelector(selector, timeoutMs = 8_000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const found = await this.evalJs(`
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0 ? { x: r.left + r.width / 2, y: r.top + r.height / 2, width: r.width, height: r.height } : null;
      `);
      if (found) return found;
      await sleep(90);
    }
    throw new Error(`Timed out waiting for visible selector: ${selector}`);
  }

  async clickSelector(selector, timeoutMs = 8_000) {
    await this.waitForSelector(selector, timeoutMs);
    await this.evalJs(`
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) throw new Error('Missing selector on click: ' + ${JSON.stringify(selector)});
      el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    `);
    await sleep(40);
    const box = await this.waitForSelector(selector, 3_000);
    await this.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: Math.round(box.x),
      y: Math.round(box.y)
    });
    await this.send('Input.dispatchMouseEvent', {
      type: 'mousePressed',
      x: Math.round(box.x),
      y: Math.round(box.y),
      button: 'left',
      clickCount: 1
    });
    await this.send('Input.dispatchMouseEvent', {
      type: 'mouseReleased',
      x: Math.round(box.x),
      y: Math.round(box.y),
      button: 'left',
      clickCount: 1
    });
    await sleep(120);
  }

  async setReactInputValue(selector, value) {
    await this.waitForSelector(selector);
    await this.evalJs(`
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) throw new Error('Input not found: ' + ${JSON.stringify(selector)});
      el.focus();
      const proto = el instanceof HTMLTextAreaElement
        ? window.HTMLTextAreaElement.prototype
        : window.HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
      if (setter) {
        setter.call(el, ${JSON.stringify(value)});
      } else {
        el.value = ${JSON.stringify(value)};
      }
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    `);
    await sleep(90);
  }

  async pressKey(key, code, windowsVirtualKeyCode, modifiers = 0) {
    await this.send('Input.dispatchKeyEvent', {
      type: 'rawKeyDown',
      key,
      code,
      windowsVirtualKeyCode,
      nativeVirtualKeyCode: windowsVirtualKeyCode,
      modifiers
    });
    await this.send('Input.dispatchKeyEvent', {
      type: 'keyUp',
      key,
      code,
      windowsVirtualKeyCode,
      nativeVirtualKeyCode: windowsVirtualKeyCode,
      modifiers
    });
    await sleep(120);
  }

  async saveScreenshot(targetPath) {
    await this.evalJs(
      `new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))`
    );
    const shot = await this.send('Page.captureScreenshot', {
      format: 'png',
      fromSurface: true
    });
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(targetPath, Buffer.from(shot.data, 'base64'));
  }

  assertZeroErrors(stepLabel) {
    if (this.consoleErrors.length > 0) {
      throw new Error(
        `Console errors detected during [${stepLabel}]:\n${this.consoleErrors.join('\n')}`
      );
    }
    if (this.pageExceptions.length > 0) {
      throw new Error(
        `Uncaught runtime exceptions detected during [${stepLabel}]:\n${this.pageExceptions.join('\n')}`
      );
    }
  }

  close() {
    try {
      this.ws.close();
    } catch {
      // ignore
    }
  }
}

async function launchChromiumCdp(userDataDir) {
  const proc = spawn(
    CHROMIUM_BIN,
    [
      '--headless=new',
      '--no-sandbox',
      '--disable-gpu',
      '--disable-dev-shm-usage',
      '--no-first-run',
      '--no-default-browser-check',
      '--no-proxy-server',
      `--user-data-dir=${userDataDir}`,
      `--window-size=${VIEWPORT_WIDTH},${VIEWPORT_HEIGHT}`,
      '--remote-debugging-port=0',
      'about:blank'
    ],
    { stdio: ['ignore', 'pipe', 'pipe'] }
  );

  const browserWsUrl = await new Promise((resolve, reject) => {
    let stderrBuf = '';
    const timer = setTimeout(() => {
      reject(new Error(`Timed out waiting for Chromium DevTools WS URL. Stderr:\n${stderrBuf}`));
    }, 15_000);

    proc.stderr.on('data', (chunk) => {
      stderrBuf += chunk.toString();
      const match = /DevTools listening on (ws:\/\/[^\s]+)/.exec(stderrBuf);
      if (match) {
        clearTimeout(timer);
        resolve(match[1]);
      }
    });
    proc.on('exit', (code) => {
      clearTimeout(timer);
      reject(new Error(`Chromium exited prematurely with code ${code}. Stderr:\n${stderrBuf}`));
    });
  });

  const parsedUrl = new URL(browserWsUrl);
  const listRes = await fetch(`http://${parsedUrl.host}/json/list`);
  const targets = await listRes.json();
  const pageTarget = targets.find((t) => t.type === 'page') || targets[0];
  assert(pageTarget?.webSocketDebuggerUrl, 'Failed to locate page CDP target');

  return { proc, pageWsUrl: pageTarget.webSocketDebuggerUrl };
}

async function auditVisibleButtonsAndHoverStability(cdp, contextLabel) {
  const deadButtons = await cdp.evalJs(`
    const buttons = Array.from(document.querySelectorAll('button:not(:disabled)'));
    const bad = [];
    for (const btn of buttons) {
      const style = window.getComputedStyle(btn);
      if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) {
        continue;
      }
      const r = btn.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) continue;
      const accessibleName = (
        btn.innerText ||
        btn.getAttribute('aria-label') ||
        btn.getAttribute('title') ||
        ''
      ).trim();
      if (!accessibleName && !btn.classList.contains('things-checkbox')) {
        bad.push(btn.outerHTML.slice(0, 140));
      }
    }
    return bad;
  `);
  assert(
    Array.isArray(deadButtons) && deadButtons.length === 0,
    `Unlabeled/dead visible buttons in [${contextLabel}]: ${JSON.stringify(deadButtons)}`
  );
}

async function assertHoverZeroShift(cdp, selector, label) {
  // Move pointer to neutral top-left corner first
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 2, y: 2 });
  await sleep(60);
  const before = await cdp.evalJs(`
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, width: r.width, height: r.height };
  `);
  assert(before, `Element not found for hover check (${label}): ${selector}`);

  await cdp.send('Input.dispatchMouseEvent', {
    type: 'mouseMoved',
    x: Math.round(before.x),
    y: Math.round(before.y)
  });
  await sleep(80);
  const after = await cdp.evalJs(`
    const el = document.querySelector(${JSON.stringify(selector)});
    const r = el.getBoundingClientRect();
    return { width: r.width, height: r.height };
  `);
  const dw = Math.abs(after.width - before.width);
  const dh = Math.abs(after.height - before.height);
  assert(
    dw <= 0.5 && dh <= 0.5,
    `Hover size shift detected on ${label} (${selector}): before=${before.width}x${before.height}, after=${after.width}x${after.height}`
  );
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 2, y: 2 });
}

async function measureBentoAlignment(cdp, scenarioLabel) {
  await cdp.evalJs(
    `new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))`
  );
  const metrics = await cdp.evalJs(`
    const notesCard = document.querySelector('[data-testid="task-bento-notes-card"]');
    const attachCard = document.querySelector('[data-testid="task-bento-attachments-card"]');
    const whenTile = document.querySelector('[data-testid="task-bento-tile-when"]');
    const containerTile = document.querySelector('[data-testid="task-bento-tile-container"]');
    if (!notesCard || !attachCard || !whenTile || !containerTile) return null;
    const leftFirstRect = notesCard.getBoundingClientRect();
    const leftLastRect = attachCard.getBoundingClientRect();
    const rightFirstRect = whenTile.getBoundingClientRect();
    const rightLastRect = containerTile.getBoundingClientRect();
    return {
      leftFirstTop: leftFirstRect.top,
      rightFirstTop: rightFirstRect.top,
      leftLastBottom: leftLastRect.bottom,
      rightLastBottom: rightLastRect.bottom,
      topDiff: Math.abs(leftFirstRect.top - rightFirstRect.top),
      bottomDiff: Math.abs(leftLastRect.bottom - rightLastRect.bottom)
    };
  `);
  assert(metrics, `Missing Bento cards during alignment check [${scenarioLabel}]`);
  assert(
    metrics.topDiff <= 1,
    `[${scenarioLabel}] Bento top edge mismatch > 1px: left=${metrics.leftFirstTop}, right=${metrics.rightFirstTop} (diff=${metrics.topDiff}px)`
  );
  assert(
    metrics.bottomDiff <= 1,
    `[${scenarioLabel}] Bento bottom edge mismatch > 1px: left=${metrics.leftLastBottom}, right=${metrics.rightLastBottom} (diff=${metrics.bottomDiff}px)`
  );
  return metrics;
}

async function main() {
  const distIndex = path.join(PWA_ROOT, 'dist', 'index.html');
  assert(
    fs.existsSync(distIndex),
    `Missing ${distIndex} — run "npm --prefix chromeos-pwa run build" before smoke test`
  );
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true });

  const port = await getEphemeralPort();
  const viteBin = path.join(PWA_ROOT, 'node_modules', '.bin', 'vite');
  const previewProc = spawn(
    viteBin,
    ['preview', '--host', '127.0.0.1', '--port', String(port), '--strictPort'],
    { cwd: PWA_ROOT, stdio: ['ignore', 'pipe', 'pipe'] }
  );

  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'thingsbook-smoke-'));
  let chromiumProc = null;
  let cdp = null;

  try {
    const baseUrl = `http://127.0.0.1:${port}`;
    await waitForHttpReady(baseUrl);
    console.log(`[1/9] vite preview ready at ${baseUrl}`);

    const launched = await launchChromiumCdp(userDataDir);
    chromiumProc = launched.proc;
    cdp = new CdpSession(launched.pageWsUrl);

    cdp.on('Runtime.consoleAPICalled', (params) => {
      if (params.type === 'error') {
        const msg = (params.args || [])
          .map((a) => a.description || a.value || JSON.stringify(a))
          .join(' ');
        cdp.consoleErrors.push(msg);
      }
    });
    cdp.on('Runtime.exceptionThrown', (params) => {
      const details = params.exceptionDetails;
      const desc =
        details?.exception?.description || details?.text || JSON.stringify(details);
      cdp.pageExceptions.push(desc);
    });

    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');
    await cdp.send('DOM.enable');
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: VIEWPORT_WIDTH,
      height: VIEWPORT_HEIGHT,
      deviceScaleFactor: 2,
      mobile: false
    });

    await cdp.send('Page.navigate', { url: baseUrl });
    await cdp.waitForSelector('.sidebar .bento-tile');
    await sleep(350);
    cdp.assertZeroErrors('initial app boot');
    console.log('[2/9] App booted cleanly with 0 console errors & 0 runtime exceptions');

    // Hover stability check on Sidebar Bento tiles, Mini-Bento cards, Task rows, and Wide-Screen Inspector Panel
    await cdp.waitForSelector('[data-testid="workspace-inspector-panel"][data-inspector-mode="overview"]');
    const initialInspectorBox = await cdp.evalJs(`
      const panel = document.querySelector('[data-testid="workspace-inspector-panel"]');
      const r = panel?.getBoundingClientRect();
      return r ? { width: Math.round(r.width), mode: panel.getAttribute('data-inspector-mode') } : null;
    `);
    assert(
      initialInspectorBox && initialInspectorBox.width === 356 && initialInspectorBox.mode === 'overview',
      `Expected 356px WorkspaceInspectorPanel in overview mode at 1440px, got ${JSON.stringify(initialInspectorBox)}`
    );
    await assertHoverZeroShift(cdp, '.bento-focus-grid .bento-tile:nth-child(1)', 'Sidebar Bento Tile');
    await assertHoverZeroShift(cdp, '.bento-mini-grid .bento-mini-card:nth-child(1)', 'Sidebar Mini Card');
    await assertHoverZeroShift(cdp, '.task-row', 'Collapsed Task Row');
    await assertHoverZeroShift(cdp, '.inspector-bento-card', 'Inspector Soft Bento Card');
    await auditVisibleButtonsAndHoverStability(cdp, 'Today initial view + Overview Inspector');
    console.log('[3/9] Verified 0px hover size shift across Sidebar Bento tiles, Mini cards, Task rows, and 356px Overview Inspector');

    // Navigate all 7 Smart Views + Today Timeline + Full Screen
    const smartViewChecks = [
      { selector: '.bento-focus-grid .bento-tile:nth-child(1)', name: 'Inbox' },
      { selector: '.bento-focus-grid .bento-tile:nth-child(2)', name: 'Today' },
      { selector: '.bento-focus-grid .bento-tile:nth-child(3)', name: 'Upcoming' },
      { selector: '.bento-focus-grid .bento-tile:nth-child(4)', name: 'Anytime' },
      { selector: '.bento-mini-grid .bento-mini-card:nth-child(1)', name: 'Someday' },
      { selector: '.sidebar-footer-utilities button:nth-child(1)', name: 'Logbook' },
      { selector: '.sidebar-footer-utilities button:nth-child(2)', name: 'Trash' }
    ];

    for (const sv of smartViewChecks) {
      await cdp.clickSelector(sv.selector);
      await cdp.waitForSelector('.view-title');
      await auditVisibleButtonsAndHoverStability(cdp, `Smart View: ${sv.name}`);
      cdp.assertZeroErrors(`Smart View: ${sv.name}`);
    }

    // Today List -> Timeline -> Full Screen -> Exit Full Screen -> List
    await cdp.clickSelector('.bento-focus-grid .bento-tile:nth-child(2)');
    await cdp.clickSelector('[data-testid="today-mode-timeline-btn"]');
    await cdp.waitForSelector('[data-testid="today-timeline-view"]');
    await auditVisibleButtonsAndHoverStability(cdp, 'Today Timeline');
    await cdp.clickSelector('[data-testid="today-timeline-fullscreen-btn"]');
    await cdp.waitForSelector('[data-testid="today-timeline-fullscreen-bar"]');
    await cdp.clickSelector('[data-testid="today-timeline-fullscreen-btn"]');
    await cdp.clickSelector('[data-testid="today-mode-list-btn"]');
    cdp.assertZeroErrors('Today Timeline + Full Screen');

    // Create & verify Custom Perspective
    await cdp.clickSelector('[data-testid="sidebar-add-perspective-btn"]');
    await cdp.waitForSelector('[data-testid="perspective-modal"]');
    await cdp.setReactInputValue('[data-testid="perspective-modal"] input.text-field', 'Sprint Focus');
    await cdp.clickSelector('[data-testid="perspective-modal"] button[type="submit"]');
    await sleep(180);
    const perspTitle = await cdp.evalJs(
      `return document.querySelector('.view-title')?.textContent?.trim();`
    );
    assert(perspTitle === 'Sprint Focus', `Expected Custom Perspective title "Sprint Focus", got "${perspTitle}"`);
    await auditVisibleButtonsAndHoverStability(cdp, 'Custom Perspective');

    // Area Dashboard + Asana/Planner Target Area Preselector check
    await cdp.clickSelector('[data-testid="sidebar-areas-section"] .area-header-row button, [data-testid="sidebar-areas-section"] .nav-item');
    await cdp.waitForSelector('.area-view-title');
    await auditVisibleButtonsAndHoverStability(cdp, 'Area Dashboard');

    const activeAreaTitle = await cdp.evalJs(
      `return document.querySelector('.area-view-title')?.textContent?.trim() || '';`
    );
    assert(activeAreaTitle.length > 0, 'Expected non-empty active Area title');

    // Open Asana & Planner Import via Sidebar Area right-click context menu and verify preselection
    await cdp.evalJs(`
      const areaRow = document.querySelector('[data-testid="sidebar-areas-section"] .nav-item.area-row');
      if (!areaRow) throw new Error('Sidebar Area row not found');
      const r = areaRow.getBoundingClientRect();
      areaRow.dispatchEvent(new MouseEvent('contextmenu', {
        bubbles: true,
        cancelable: true,
        clientX: r.left + 24,
        clientY: r.top + 12
      }));
    `);
    await cdp.waitForSelector('[data-testid="sidebar-area-ctx-import-external"]');
    await cdp.clickSelector('[data-testid="sidebar-area-ctx-import-external"]');
    await cdp.waitForSelector('[data-testid="external-import-area-selector"]');
    await auditVisibleButtonsAndHoverStability(cdp, 'Asana/Planner Import Modal (Area Preselected)');

    const extAreaPreselect = await cdp.evalJs(`
      const sel = document.querySelector('[data-testid="external-import-area-selector"]');
      const activeChip = sel?.querySelector('.chip-btn.active')?.textContent?.trim() || '';
      const noneChip = sel?.querySelector('[data-testid="external-import-area-chip-none"]')?.textContent?.trim() || '';
      return { activeChip, noneChip };
    `);
    assert(
      extAreaPreselect.activeChip === activeAreaTitle,
      `Expected external import selector to preselect active Area "${activeAreaTitle}", got "${extAreaPreselect.activeChip}"`
    );
    assert(
      extAreaPreselect.noneChip === 'No Area',
      `Expected unassigned Area chip to read "No Area", got "${extAreaPreselect.noneChip}"`
    );
    await cdp.pressKey('Escape', 'Escape', 27);
    await sleep(140);

    cdp.assertZeroErrors('Area Dashboard + Asana/Planner Area Preselection');
    console.log('[4/9] Verified all 7 Smart Views, Today Timeline + Full Screen, Custom Perspective, Area Dashboard & Asana Area Preselector');

    // Templates & Sample Markdown Outline (@45m) check
    await cdp.clickSelector('[data-testid="sidebar-new-project-btn"]');
    await cdp.clickSelector('[data-testid="sidebar-templates-btn"]');
    await cdp.waitForSelector('[data-testid="templates-import-modal"]');
    await cdp.clickSelector('[data-testid="templates-tab-import"]');
    await cdp.waitForSelector('[data-testid="markdown-outline-input"]');
    // Click "Load Sample" chip button above textarea
    await cdp.clickSelector('[data-testid="templates-load-sample-btn"]');
    await sleep(160);
    await cdp.clickSelector('[data-testid="create-from-outline-btn"]');
    await cdp.waitForSelector('[data-testid="project-view-mode-switcher"]');
    await sleep(220);

    const outlineCheck = await cdp.evalJs(`
      const projTitle = document.querySelector('.project-view-title')?.textContent?.trim() || '';
      const rowTitles = Array.from(document.querySelectorAll('.task-row .task-row-title')).map((n) => n.textContent?.trim() || '');
      const durPills = Array.from(document.querySelectorAll('[data-testid="task-row-duration-pill"]')).map((n) => n.textContent?.trim() || '');
      return { projTitle, rowTitles, durPills };
    `);
    assert(
      outlineCheck.rowTitles.some((t) => t === 'Audit analytics & conversion funnel'),
      `Expected @45m suffix to be stripped from outline task title, got: ${JSON.stringify(outlineCheck.rowTitles)}`
    );
    assert(
      outlineCheck.durPills.some((p) => p.includes('45m')),
      `Expected @45m outline token to populate 45m duration pill, got: ${JSON.stringify(outlineCheck.durPills)}`
    );

    // Project List -> Kanban Board -> Gantt Roadmap + Full Screen + Screenshot
    await cdp.clickSelector('[data-testid="project-view-mode-board"]');
    await cdp.waitForSelector('[data-testid="project-board-workspace"]');
    await auditVisibleButtonsAndHoverStability(cdp, 'Project Kanban Board');

    await cdp.clickSelector('[data-testid="project-view-mode-roadmap"]');
    await cdp.waitForSelector('[data-testid="project-roadmap-workspace"]');
    await auditVisibleButtonsAndHoverStability(cdp, 'Project Gantt Roadmap');

    const roadmapShotPath = path.join(ARTIFACT_DIR, 'smoke_roadmap_3_0.png');
    await cdp.saveScreenshot(roadmapShotPath);

    await cdp.clickSelector('[data-testid="roadmap-fullscreen-btn"]');
    await cdp.waitForSelector('[data-testid="roadmap-fullscreen-project-title"]');
    await cdp.clickSelector('[data-testid="roadmap-fullscreen-btn"]');
    await cdp.clickSelector('[data-testid="project-view-mode-list"]');
    cdp.assertZeroErrors('Project List / Kanban / Roadmap + Full Screen + @45m Outline');
    console.log('[5/9] Verified Templates @45m outline import & Project List / Kanban / Roadmap + Full Screen');

    // Settings Modal + switching across all 5 languages (en, de, fr, it, es) -> settle on de
    await cdp.clickSelector('header .topbar-right button:last-child');
    await cdp.waitForSelector('[data-testid="settings-workflow-security-section"]');
    const expectedTodayByFlag = [
      { flag: '🇬🇧', code: 'en' },
      { flag: '🇫🇷', code: 'fr' },
      { flag: '🇮🇹', code: 'it' },
      { flag: '🇪🇸', code: 'es' },
      { flag: '🇩🇪', code: 'de' }
    ];
    for (const item of expectedTodayByFlag) {
      await cdp.evalJs(`
        const chips = Array.from(document.querySelectorAll('.modal-panel .chip-btn'));
        const target = chips.find((b) => (b.textContent || '').includes(${JSON.stringify(item.flag)}));
        if (!target) throw new Error('Language chip not found for ' + ${JSON.stringify(item.code)});
        target.click();
      `);
      await sleep(140);
      const htmlLang = await cdp.evalJs(
        `return document.querySelector('.app-shell')?.getAttribute('lang');`
      );
      assert(htmlLang === item.code, `Expected .app-shell[lang="${item.code}"], got "${htmlLang}"`);
      await auditVisibleButtonsAndHoverStability(cdp, `Settings (${item.code})`);
      cdp.assertZeroErrors(`Language switch: ${item.code}`);
    }
    // Close Settings modal
    await cdp.pressKey('Escape', 'Escape', 27);
    await sleep(160);
    // Dismiss setup banner if visible so screenshots look super clean
    await cdp.evalJs(`
      const bannerClose = document.querySelector('.setup-banner .icon-btn');
      if (bannerClose) bannerClose.click();
    `);
    await sleep(100);
    console.log('[6/9] Verified Settings modal & clean switching across all 5 languages (en, fr, it, es, de)');

    // Create Scenario A & Scenario B tasks directly in IndexedDB via UI/DB and open in German ('de')
    // Switch to Today ('Heute') first
    await cdp.clickSelector('.bento-focus-grid .bento-tile:nth-child(2)');
    await sleep(160);

    // Seed Scenario A ("Migrationskonzept" with 5 bullet lines, Today + Deadline + Project, 0 checklists, 0 attachments)
    // and Scenario B ("Kurzer Check" with empty notes, Today, 0 checklists, 0 attachments) in thingsbook_chromeos_db, then reload!
    const seededIds = await cdp.evalJs(`
      const now = Date.now();
      const todayD = new Date();
      const todayMidnight = new Date(todayD.getFullYear(), todayD.getMonth(), todayD.getDate(), 0, 0, 0, 0).getTime();
      const deadlineMs = todayMidnight + 14 * 86400000;

      const dbReq = indexedDB.open('thingsbook_chromeos_db');
      const db = await new Promise((res, rej) => {
        dbReq.onsuccess = () => res(dbReq.result);
        dbReq.onerror = () => rej(dbReq.error);
      });

      const projId = 'smoke-proj-infra';
      const taskDeId = 'smoke-task-migrationskonzept';
      const taskShortId = 'smoke-task-short-bento';

      const tx = db.transaction(['projects', 'tasks'], 'readwrite');
      tx.objectStore('projects').put({
        id: projId,
        areaId: null,
        title: 'IT-Infrastruktur 2026',
        notes: '',
        status: 'ACTIVE',
        scheduleState: 'TODAY',
        whenDate: todayMidnight,
        deadline: deadlineMs,
        completedAt: null,
        lastReviewedAt: now,
        sortOrder: 10,
        tagIds: [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      });

      tx.objectStore('tasks').put({
        id: taskDeId,
        title: 'Migrationskonzept',
        notes: [
          '• DFS Migration',
          '• Client Umstellung',
          '• Exchange Umstellung',
          '• M365 Umstellung',
          '• Citrix Apps Umstellung'
        ].join('\\n'),
        status: 'OPEN',
        scheduleState: 'TODAY',
        whenDate: todayMidnight,
        deadline: deadlineMs,
        reminderTime: null,
        scheduledStartMinutes: null,
        durationMinutes: null,
        completedAt: null,
        projectId: projId,
        headingId: null,
        areaId: null,
        recurrenceRule: null,
        sortOrder: 1,
        tagIds: [],
        effortLevel: null,
        isPinned: false,
        blockedByTaskIds: [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      });

      tx.objectStore('tasks').put({
        id: taskShortId,
        title: 'Kurzer Check',
        notes: '',
        status: 'OPEN',
        scheduleState: 'TODAY',
        whenDate: todayMidnight,
        deadline: deadlineMs,
        reminderTime: null,
        scheduledStartMinutes: 9 * 60,
        durationMinutes: 45,
        completedAt: null,
        projectId: projId,
        headingId: null,
        areaId: null,
        recurrenceRule: null,
        sortOrder: 2,
        tagIds: [],
        effortLevel: 'DEEP_FOCUS',
        isPinned: false,
        blockedByTaskIds: [],
        createdAt: now,
        updatedAt: now,
        isDeleted: false,
        isDirty: false
      });

      await new Promise((res, rej) => {
        tx.oncomplete = () => res(true);
        tx.onerror = () => rej(tx.error);
      });
      db.close();
      return { taskDeId, taskShortId };
    `);

    await cdp.send('Page.reload', { ignoreCache: false });
    await cdp.waitForSelector(`[data-testid="task-row-open-modal-${seededIds.taskDeId}"]`);
    await sleep(250);

    // Scenario A: Open "Migrationskonzept" Soft Bento Task Modal (Left > Right)
    await cdp.evalJs(`
      const btn = document.querySelector('[data-testid="task-row-open-modal-${seededIds.taskDeId}"]');
      btn.click();
    `);
    await cdp.waitForSelector('[data-testid="soft-bento-task-modal"]');
    // Blur title input so formatted bullet preview is active
    await cdp.evalJs(`if (document.activeElement instanceof HTMLElement) document.activeElement.blur();`);
    await sleep(180);

    const alignDe = await measureBentoAlignment(cdp, 'Scenario A: German Migrationskonzept (Left > Right)');
    const bentoDePath = path.join(ARTIFACT_DIR, 'smoke_bento_aligned_de.png');
    await cdp.saveScreenshot(bentoDePath);
    console.log(
      `[7/9] Scenario A Bento alignment PASS (topDiff=${alignDe.topDiff.toFixed(2)}px, bottomDiff=${alignDe.bottomDiff.toFixed(2)}px) -> ${bentoDePath}`
    );

    // Close Scenario A modal and open Scenario B ("Kurzer Check", empty notes where Right > Left!)
    await cdp.clickSelector('[data-testid="task-modal-done-btn"]');
    await sleep(180);

    await cdp.evalJs(`
      const btn = document.querySelector('[data-testid="task-row-open-modal-${seededIds.taskShortId}"]');
      btn.click();
    `);
    await cdp.waitForSelector('[data-testid="soft-bento-task-modal"]');
    // Expand Duration & Effort panel on Right Column so Right Column is substantially taller than Left Column!
    await cdp.clickSelector('[data-testid="task-effort-duration-chip"]');
    await cdp.waitForSelector('[data-testid="task-effort-duration-panel"]');
    await cdp.evalJs(`if (document.activeElement instanceof HTMLElement) document.activeElement.blur();`);
    await sleep(180);

    const alignShort = await measureBentoAlignment(cdp, 'Scenario B: Empty Notes + Tall Right Column (Right > Left)');
    const bentoShortPath = path.join(ARTIFACT_DIR, 'smoke_bento_aligned_short.png');
    await cdp.saveScreenshot(bentoShortPath);
    console.log(
      `[8/9] Scenario B Bento alignment PASS (topDiff=${alignShort.topDiff.toFixed(2)}px, bottomDiff=${alignShort.bottomDiff.toFixed(2)}px) -> ${bentoShortPath}`
    );

    // Exercise custom 5d (2400m) duration apply + reload persistence + non-destructive Timeblock set & clear + Checklist + Magic Breakdown + Pin + Quick Find (Ctrl+K)
    await cdp.setReactInputValue('[data-testid="task-custom-duration-input"]', '5d');
    await cdp.clickSelector('[data-testid="task-custom-duration-apply"]');
    await sleep(180);

    const chipAfter5d = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-effort-duration-chip"]')?.textContent?.trim() || '';
    `);
    assert(chipAfter5d.includes('5d'), `Expected Duration chip to include "5d", got "${chipAfter5d}"`);

    const focusLabelAfter5d = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-start-focus-btn"]')?.textContent?.trim() || '';
    `);
    assert(
      !focusLabelAfter5d.includes('2400m') && !focusLabelAfter5d.includes('1dm') && focusLabelAfter5d.includes('1d 4h'),
      `Expected clamped compact Focus Timer label "(1d 4h)" for 5d task, got "${focusLabelAfter5d}"`
    );

    await cdp.clickSelector('[data-testid="task-modal-done-btn"]');
    await sleep(180);

    // Reload and verify 5d (2400m) persisted in IndexedDB!
    await cdp.send('Page.reload', { ignoreCache: false });
    await cdp.waitForSelector(`[data-testid="task-row-open-modal-${seededIds.taskShortId}"]`);
    await sleep(220);

    await cdp.evalJs(`
      document.querySelector('[data-testid="task-row-open-modal-${seededIds.taskShortId}"]').click();
    `);
    await cdp.waitForSelector('[data-testid="soft-bento-task-modal"]');
    await sleep(150);

    const persisted5dChip = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-effort-duration-chip"]')?.textContent?.trim() || '';
    `);
    assert(
      persisted5dChip.includes('5d'),
      `Expected 5d duration to survive full page reload, got "${persisted5dChip}"`
    );

    // Timeblock set & non-destructive Clear Timeblock (verifying 5d survives!)
    await cdp.clickSelector('[data-testid="task-timeblock-chip"]');
    await cdp.waitForSelector('[data-testid="task-timeblock-panel"]');
    // Click 11:00 slot
    await cdp.evalJs(`
      const slots = Array.from(document.querySelectorAll('[data-testid="task-timeblock-panel"] .chip-btn'));
      const slot11 = slots.find((b) => (b.textContent || '').trim() === '11:00');
      if (!slot11) throw new Error('11:00 slot button not found');
      slot11.click();
    `);
    await sleep(160);
    const tbLabelWith5d = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-timeblock-chip"]')?.textContent?.trim() || '';
    `);
    assert(
      tbLabelWith5d.includes('11:00 · 5d'),
      `Expected Timeblock pill "11:00 · 5d", got "${tbLabelWith5d}"`
    );

    await cdp.clickSelector('[data-testid="task-timeblock-clear-btn"]');
    await sleep(160);
    const durAfterClearTb = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-effort-duration-chip"]')?.textContent?.trim() || '';
    `);
    assert(
      durAfterClearTb.includes('5d'),
      `Expected standalone 5d duration to survive Clear Timeblock, got "${durAfterClearTb}"`
    );
    const clearBtnGone = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-timeblock-clear-btn"]') === null;
    `);
    assert(clearBtnGone, 'Clear Timeblock button must hide once scheduledStartMinutes is null');

    // Checklist add + Magic Breakdown + Pin
    await cdp.setReactInputValue('[data-testid="task-bento-checklist-card"] .checklist-input', 'Erster Prüfpunkt');
    await cdp.pressKey('Enter', 'Enter', 13);
    await sleep(160);
    await cdp.clickSelector('.checklist-magic-inline-btn');
    await sleep(400);
    const checklistHeader = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-bento-checklist-card"] .checklist-header')?.textContent?.trim() || '';
    `);
    assert(
      !/\(0\/0\)/.test(checklistHeader),
      `Expected Checklist + Magic Breakdown to populate subtasks, got header "${checklistHeader}"`
    );

    // Toggle Milestone inside Soft Bento Task Modal + Pin
    await cdp.clickSelector('[data-testid="task-milestone-chip"]');
    await sleep(140);
    const msHeaderActive = await cdp.evalJs(`
      return document.querySelector('[data-testid="task-milestone-btn"]')?.classList.contains('milestone-active') === true;
    `);
    assert(msHeaderActive, 'Expected task-milestone-btn in modal header to reflect active milestone state');

    await cdp.clickSelector('[data-testid="task-pin-btn"]');
    await sleep(140);
    await cdp.clickSelector('[data-testid="task-modal-done-btn"]');
    await sleep(180);
    await cdp.waitForSelector('[data-testid="task-pin-indicator"]');
    await cdp.waitForSelector('[data-testid="task-row-milestone-pill"]');
    await assertHoverZeroShift(cdp, '.things-checkbox.milestone', 'Milestone Diamond Checkbox');

    // macOS 1:1 Selection Parity + Wide-Screen Selected Task Inspector E2E Check:
    // Before clicking a task, Inspector is in Overview mode (showing Pinned & Upcoming Deadlines/Milestones)
    const overviewBeforeClick = await cdp.evalJs(`
      const panel = document.querySelector('[data-testid="workspace-inspector-panel"]');
      const hasPinned = Boolean(document.querySelector('[data-testid="inspector-overview-pinned-card"]'));
      const hasDeadlines = Boolean(document.querySelector('[data-testid="inspector-overview-deadlines-card"]'));
      return {
        mode: panel?.getAttribute('data-inspector-mode') || null,
        hasPinned,
        hasDeadlines
      };
    `);
    assert(
      overviewBeforeClick.mode === 'overview' && overviewBeforeClick.hasPinned && overviewBeforeClick.hasDeadlines,
      `Expected Inspector in Overview mode with Pinned & Deadlines cards before task selection, got ${JSON.stringify(overviewBeforeClick)}`
    );

    // Plain-click Task 1 -> ONLY Task 1 has .selected, .multi-select-bar is NOT shown, and Inspector switches to Mode A ("Kurzer Check")!
    await cdp.clickSelector(`.task-row[data-task-id="${seededIds.taskShortId}"] .task-row-title`);
    const selAfterFirstClick = await cdp.evalJs(`
      const selected = Array.from(document.querySelectorAll('.task-row.selected')).map((el) => el.getAttribute('data-task-id'));
      const multiBar = document.querySelector('.multi-select-bar');
      const panel = document.querySelector('[data-testid="workspace-inspector-panel"]');
      const inspectorTitleVal = document.querySelector('[data-testid="inspector-task-title-input"]')?.value || '';
      const hasOpenModalBtn = Boolean(document.querySelector('[data-testid="inspector-open-modal-btn"]'));
      const hasMilestoneBtn = Boolean(document.querySelector('[data-testid="inspector-milestone-btn"]'));
      return {
        selected,
        hasMultiBar: Boolean(multiBar),
        inspectorMode: panel?.getAttribute('data-inspector-mode') || null,
        inspectorTitleVal,
        hasOpenModalBtn,
        hasMilestoneBtn
      };
    `);
    assert(
      selAfterFirstClick.selected.length === 1 && selAfterFirstClick.selected[0] === seededIds.taskShortId,
      `Expected plain-click on Task 1 to select ONLY Task 1, got ${JSON.stringify(selAfterFirstClick)}`
    );
    assert(
      !selAfterFirstClick.hasMultiBar,
      'Expected .multi-select-bar NOT to appear for a single plain-clicked task'
    );
    assert(
      selAfterFirstClick.inspectorMode === 'task' &&
        selAfterFirstClick.inspectorTitleVal === 'Kurzer Check' &&
        selAfterFirstClick.hasOpenModalBtn &&
        selAfterFirstClick.hasMilestoneBtn,
      `Expected Inspector to switch to Selected Task Inspector mode for "Kurzer Check", got ${JSON.stringify(selAfterFirstClick)}`
    );
    await auditVisibleButtonsAndHoverStability(cdp, 'Selected Task Inspector (Kurzer Check)');

    // Plain-click Task 2 -> Task 1 deselects, ONLY Task 2 has .selected, and Inspector updates live to "Migrationskonzept"!
    await cdp.clickSelector(`.task-row[data-task-id="${seededIds.taskDeId}"] .task-row-title`);
    const selAfterSecondClick = await cdp.evalJs(`
      const selected = Array.from(document.querySelectorAll('.task-row.selected')).map((el) => el.getAttribute('data-task-id'));
      const multiBar = document.querySelector('.multi-select-bar');
      const panel = document.querySelector('[data-testid="workspace-inspector-panel"]');
      const inspectorTitleVal = document.querySelector('[data-testid="inspector-task-title-input"]')?.value || '';
      return {
        selected,
        hasMultiBar: Boolean(multiBar),
        inspectorMode: panel?.getAttribute('data-inspector-mode') || null,
        inspectorTitleVal
      };
    `);
    assert(
      selAfterSecondClick.selected.length === 1 && selAfterSecondClick.selected[0] === seededIds.taskDeId,
      `Expected plain-click on Task 2 to deselect Task 1 and select ONLY Task 2, got ${JSON.stringify(selAfterSecondClick)}`
    );
    assert(
      !selAfterSecondClick.hasMultiBar,
      'Expected .multi-select-bar NOT to appear after plain-clicking Task 2'
    );
    assert(
      selAfterSecondClick.inspectorMode === 'task' &&
        selAfterSecondClick.inspectorTitleVal === 'Migrationskonzept',
      `Expected Inspector to switch to "Migrationskonzept" on plain-click, got ${JSON.stringify(selAfterSecondClick)}`
    );

    // Deselect from Inspector header -> returns cleanly to Overview mode
    await cdp.clickSelector('[data-testid="inspector-deselect-btn"]');
    await cdp.waitForSelector('[data-testid="workspace-inspector-panel"][data-inspector-mode="overview"]');

    // Quick Find via Ctrl+K (modifiers: 2 = Ctrl)
    await cdp.pressKey('k', 'KeyK', 75, 2);
    await cdp.waitForSelector('.inline-qf-bar input');
    await cdp.setReactInputValue('.inline-qf-bar input', 'Migrationskonzept');
    await sleep(160);
    const qfMatchFound = await cdp.evalJs(`
      return document.body.innerText.includes('Migrationskonzept');
    `);
    assert(qfMatchFound, 'Quick Find must locate "Migrationskonzept"');
    await cdp.pressKey('Escape', 'Escape', 27);
    await sleep(140);

    // Navigate to Project "IT-Infrastruktur 2026" -> Gantt Roadmap -> Verify Milestone Diamond & Callout Pill + Save smoke_milestone_roadmap.png!
    await cdp.evalJs(`
      const todayD = new Date();
      const todayMidnight = new Date(todayD.getFullYear(), todayD.getMonth(), todayD.getDate(), 0, 0, 0, 0).getTime();
      const dbReq = indexedDB.open('thingsbook_chromeos_db');
      const db = await new Promise((res, rej) => {
        dbReq.onsuccess = () => res(dbReq.result);
        dbReq.onerror = () => rej(dbReq.error);
      });
      await new Promise((res, rej) => {
        const tx = db.transaction(['headings', 'tasks'], 'readwrite');
        tx.oncomplete = () => res(true);
        tx.onerror = () => rej(tx.error);
        const hStore = tx.objectStore('headings');
        const tStore = tx.objectStore('tasks');
        hStore.put({
          id: 'smoke-heading-phase1',
          projectId: 'smoke-proj-infra',
          title: 'Phase 1 · Architektur & Go-Live',
          startDate: todayMidnight,
          endDate: todayMidnight + 8 * 86400000,
          sortOrder: 1,
          createdAt: Date.now(),
          updatedAt: Date.now(),
          isDeleted: false,
          isDirty: false
        });
        const r1 = tStore.get(${JSON.stringify(seededIds.taskDeId)});
        r1.onsuccess = () => {
          const t1 = r1.result;
          if (t1) {
            t1.headingId = 'smoke-heading-phase1';
            t1.whenDate = todayMidnight;
            t1.deadline = todayMidnight + 4 * 86400000;
            tStore.put(t1);
          }
        };
        const r2 = tStore.get(${JSON.stringify(seededIds.taskShortId)});
        r2.onsuccess = () => {
          const t2 = r2.result;
          if (t2) {
            t2.title = 'Go-Live Freigabe (M365 Cutover)';
            t2.headingId = 'smoke-heading-phase1';
            t2.whenDate = todayMidnight + 6 * 86400000;
            t2.deadline = todayMidnight + 6 * 86400000;
            t2.blockedByTaskIds = [${JSON.stringify(seededIds.taskDeId)}];
            tStore.put(t2);
          }
        };
      });
      db.close();
    `);
    await cdp.send('Page.reload', { ignoreCache: false });
    await cdp.waitForSelector('.sidebar .project-nav-item');
    await sleep(220);
    await cdp.evalJs(`
      const projBtns = Array.from(document.querySelectorAll('.sidebar .project-nav-item'));
      const target = projBtns.find((b) => (b.textContent || '').includes('IT-Infrastruktur 2026'));
      if (!target) throw new Error('Sidebar project IT-Infrastruktur 2026 not found');
      target.click();
    `);
    await cdp.waitForSelector('[data-testid="project-view-mode-roadmap"]');
    await cdp.clickSelector('[data-testid="project-view-mode-roadmap"]');
    await cdp.waitForSelector('[data-milestone="true"]');
    await sleep(220);

    const roadmapMilestoneInfo = await cdp.evalJs(`
      const bar = document.querySelector('[data-milestone="true"]');
      const diamond = bar?.querySelector('.roadmap-milestone-diamond');
      const label = bar?.querySelector('.roadmap-milestone-label');
      const dRect = diamond?.getBoundingClientRect();
      const lRect = label?.getBoundingClientRect();
      return {
        hasBar: Boolean(bar),
        hasDiamond: Boolean(diamond && dRect && dRect.width > 0 && dRect.height > 0),
        labelText: label?.textContent?.trim() || '',
        labelVisible: Boolean(lRect && lRect.width > 40 && lRect.right <= window.innerWidth)
      };
    `);
    assert(
      roadmapMilestoneInfo.hasBar && roadmapMilestoneInfo.hasDiamond,
      `Expected visible 45° Milestone diamond on Project Gantt Roadmap, got ${JSON.stringify(roadmapMilestoneInfo)}`
    );
    assert(
      roadmapMilestoneInfo.labelText.includes('◆ Go-Live Freigabe'),
      `Expected floating Milestone callout pill with "◆ Go-Live Freigabe", got "${roadmapMilestoneInfo.labelText}"`
    );
    await auditVisibleButtonsAndHoverStability(cdp, 'Project Gantt Roadmap (Milestone Diamond)');

    // Timeline Drag E2E Parity Check (Hover = no-op; Mouse press-and-drag = moves +2d; Touch hold-and-drag = moves -2d):
    const dayW = await cdp.evalJs(`
      return document.querySelector('.roadmap-day-cell')?.getBoundingClientRect()?.width || 40;
    `);
    const twoDaysPx = Math.round(2 * dayW);

    const getBarMetrics = async (selector) => {
      return await cdp.evalJs(`
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return {
          leftStylePx: parseFloat(el.style.left || '0'),
          rectLeft: r.left,
          cx: Math.round(r.left + Math.min(24, r.width / 2)),
          cy: Math.round(r.top + r.height / 2)
        };
      `);
    };

    const taskBarSel = `[data-testid="roadmap-task-bar-${seededIds.taskDeId}"]`;
    const beforeTaskBar = await getBarMetrics(taskBarSel);
    assert(beforeTaskBar, `Missing task bar ${taskBarSel} in Roadmap Timeline`);

    // 1. Pure mouse hover (buttons: 0) across task bar MUST NOT move it
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx,
      y: beforeTaskBar.cy,
      button: 'none',
      buttons: 0
    });
    await sleep(60);
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx + Math.round(3 * dayW),
      y: beforeTaskBar.cy,
      button: 'none',
      buttons: 0
    });
    await sleep(80);
    const afterHoverTaskBar = await getBarMetrics(taskBarSel);
    assert(
      Math.abs(afterHoverTaskBar.leftStylePx - beforeTaskBar.leftStylePx) < 0.5,
      `Hovering over Timeline task bar moved it! before=${beforeTaskBar.leftStylePx}, afterHover=${afterHoverTaskBar.leftStylePx}`
    );

    // 2. Mouse press-and-drag (+2 * dayW = +2 days) MUST move bar while pressed and persist on release
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx,
      y: beforeTaskBar.cy,
      button: 'none',
      buttons: 0
    });
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mousePressed',
      x: beforeTaskBar.cx,
      y: beforeTaskBar.cy,
      button: 'left',
      buttons: 1,
      clickCount: 1
    });
    await sleep(30); // Move immediately (< 260ms) like a normal mouse press-and-drag!
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx + 16,
      y: beforeTaskBar.cy,
      button: 'left',
      buttons: 1
    });
    await sleep(30);
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx + twoDaysPx,
      y: beforeTaskBar.cy,
      button: 'left',
      buttons: 1
    });
    await sleep(60);
    const midMouseDragBar = await getBarMetrics(taskBarSel);
    assert(
      Math.abs(midMouseDragBar.leftStylePx - (beforeTaskBar.leftStylePx + twoDaysPx)) <= 1,
      `Expected live mouse press-and-drag preview +${twoDaysPx}px (+2d), got before=${beforeTaskBar.leftStylePx}, mid=${midMouseDragBar.leftStylePx}`
    );

    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseReleased',
      x: beforeTaskBar.cx + twoDaysPx,
      y: beforeTaskBar.cy,
      button: 'left',
      buttons: 0,
      clickCount: 1
    });
    await sleep(160);

    // Post-release hover back (buttons: 0) MUST NOT pull the task bar back!
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseMoved',
      x: beforeTaskBar.cx - Math.round(dayW),
      y: beforeTaskBar.cy,
      button: 'none',
      buttons: 0
    });
    await sleep(80);
    const afterMouseReleaseBar = await getBarMetrics(taskBarSel);
    assert(
      Math.abs(afterMouseReleaseBar.leftStylePx - (beforeTaskBar.leftStylePx + twoDaysPx)) <= 1,
      `Expected persisted +${twoDaysPx}px (+2d) position after mouse release + hover, got ${afterMouseReleaseBar.leftStylePx}`
    );

    // 3. Touch finger press-and-hold (360ms) + drag (-2 * dayW = -2 days) on Milestone diamond bar MUST move cleanly without scroll/pointercancel
    const milestoneSel = '[data-milestone="true"]';
    const beforeMilestoneBar = await getBarMetrics(milestoneSel);
    assert(beforeMilestoneBar, 'Missing Milestone bar for touch hold-and-drag test');
    const diamondTouchPos = await cdp.evalJs(`
      const d = document.querySelector('[data-milestone="true"] .roadmap-milestone-diamond');
      const r = d.getBoundingClientRect();
      return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2) };
    `);

    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [{ x: diamondTouchPos.x, y: diamondTouchPos.y, radiusX: 6, radiusY: 6, id: 1 }]
    });
    await sleep(360); // Hold stationary > 320ms to arm touch drag
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: diamondTouchPos.x - Math.round(dayW), y: diamondTouchPos.y, radiusX: 6, radiusY: 6, id: 1 }]
    });
    await sleep(40);
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: diamondTouchPos.x - twoDaysPx, y: diamondTouchPos.y, radiusX: 6, radiusY: 6, id: 1 }]
    });
    await sleep(60);
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchEnd',
      touchPoints: []
    });
    await sleep(160);

    const afterTouchMilestoneBar = await getBarMetrics(milestoneSel);
    assert(
      Math.abs(afterTouchMilestoneBar.leftStylePx - (beforeMilestoneBar.leftStylePx - twoDaysPx)) <= 1,
      `Expected touch hold-and-drag on Milestone to shift -${twoDaysPx}px (-2d), got before=${beforeMilestoneBar.leftStylePx}, after=${afterTouchMilestoneBar.leftStylePx}`
    );

    const milestoneRoadmapShotPath = path.join(ARTIFACT_DIR, 'smoke_milestone_roadmap.png');
    await cdp.saveScreenshot(milestoneRoadmapShotPath);
    console.log(`Captured Milestone Gantt Roadmap screenshot -> ${milestoneRoadmapShotPath}`);

    cdp.assertZeroErrors('Milestone toggle + Selection Parity + Roadmap Hover/Mouse/Touch Drag + Quick Find');
    console.log(
      '[9/9] ALL E2E Headless Chromium UI Smoke Checks PASSED (0 console.error, 0 exceptions, <=1px Bento alignment, Timeline Mouse & Touch Drag + Hover Guard verified)!'
    );
  } finally {
    if (cdp) cdp.close();
    if (chromiumProc) {
      chromiumProc.kill('SIGKILL');
    }
    previewProc.kill('SIGTERM');
    try {
      fs.rmSync(userDataDir, { recursive: true, force: true });
    } catch {
      // ignore cleanup error
    }
  }
}

main().catch((err) => {
  console.error('\n❌ SMOKE TEST FAILED:', err?.stack || err);
  process.exit(1);
});
